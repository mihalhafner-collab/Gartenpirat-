package at.hausgarten.service

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Native Compose entry point; platform consent stays separate from business writers. */
class MainActivity : ComponentActivity() {
    private lateinit var preferences: SharedPreferences
    private var store: PixelLocalStore? = null
    private var controller by mutableStateOf<NativeAppController?>(null)
    private var startupError by mutableStateOf("")
    private val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var planningNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var workLocation: WorkLocationController? = null
    private var locationWaiter: Pair<String, CancellableContinuation<JSONObject>>? = null
    private var photoWaiter: CancellableContinuation<Uri?>? = null
    private var speechResult: ((String) -> Unit)? = null
    private var pendingCaptureUri: Uri? = null
    private var pendingCaptureFile: File? = null
    private val photoResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val capturedUri = pendingCaptureUri
        val capturedFile = pendingCaptureFile
        val waiter = photoWaiter
        photoWaiter = null
        lifecycleScope.launch {
            val selected = if (capturedUri != null) {
                val valid = withContext(Dispatchers.IO) {
                    result.resultCode == RESULT_OK && capturedFile != null && capturedFile.isFile &&
                        capturedFile.length() > 0 && capturedFile.length() <= PhotoAttachmentStore.MAX_BYTES
                }
                clearPendingCapture(!valid)
                if (!valid && result.resultCode == RESULT_OK) toast("Das Kamerafoto ist leer oder größer als 20 MB.")
                if (valid) capturedUri else null
            } else if (result.resultCode == RESULT_OK) {
                val data = result.data
                val selectedUri = data?.data ?: data?.clipData?.let { if (it.itemCount > 0) it.getItemAt(0).uri else null }
                if (selectedUri != null && data != null && data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                    try { contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
                }
                selectedUri
            } else null
            if (waiter?.isActive == true) waiter.resume(selected)
        }
    }
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = speechResult
        speechResult = null
        if (result.resultCode == RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { callback?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (RevierApplication.recoveryRequired()) { startActivity(Intent(this, RestoreRecoveryActivity::class.java)); finish(); return }
        val cameraName = savedInstanceState?.getString("capture_name")
        if (cameraName != null && cameraName.matches(Regex("capture-[0-9a-f]{32}\\.jpg"))) {
            pendingCaptureFile = File(File(cacheDir, "photo-capture"), cameraName)
            pendingCaptureUri = Uri.Builder().scheme("content").authority(packageName + ".photocapture").appendPath(cameraName).build()
        }
        workLocation = WorkLocationController(this) { request, value ->
            val waiting = locationWaiter
            if (waiting?.first == request) {
                locationWaiter = null
                if (waiting.second.isActive) waiting.second.resume(value)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = controller
                if (current != null) current.back() else finish()
            }
        })
        window.nativeLightSystemBars()
        setContent {
            HausGartenTheme {
                val current = controller
                if (current != null) NativeApplication(current)
                else Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Haus & Garten", style = MaterialTheme.typography.headlineMedium)
                        if (startupError.isEmpty()) { CircularProgressIndicator(); Text("Lokaler Bestand wird geöffnet …") }
                        else { Text(startupError); TextButton(onClick = { startActivity(Intent(this@MainActivity, RestoreRecoveryActivity::class.java)) }) { Text("Wiederherstellungsprüfung öffnen") } }
                    }
                }
            }
        }
        lifecycleScope.launch {
            var openingStore: PixelLocalStore? = null
            try {
                val opened = withContext(Dispatchers.IO) {
                    RestoreStartupGuard.assertStoreMayOpen(this@MainActivity)
                    preferences = getSharedPreferences("haus_garten", MODE_PRIVATE)
                    PixelLocalStore(this@MainActivity).also { local ->
                        openingStore = local
                        if (!BuildConfig.UI_PREVIEW) {
                            try { if (PlanningAccount.connected(this@MainActivity) || PlanningAccount.prefs(this@MainActivity).getBoolean("weather_enabled", false)) PlanningJob.install(this@MainActivity) }
                            catch (_: RuntimeException) { android.util.Log.w("HausGartenPlanning", "BACKGROUND_SCHEDULING_FAILED") }
                        }
                    }
                }
                store = opened
                controller = NativeAppController(this@MainActivity, opened).also { it.restoreRoute(savedInstanceState?.getString("native_route")) }
                lifecycleScope.launch {
                    opened.nativeData().watchCommittedChanges().onStart { emit(0L) }
                        .retryWhen { failure, _ -> if (failure !is android.database.sqlite.SQLiteDatabaseLockedException) false else { delay(75); true } }
                        .catch { android.util.Log.w("HausGartenWork", "NOTICE_WATCH_FAILED") }
                        .collect { refreshWorkNotice() }
                }
                handleLaunch(intent, savedInstanceState == null)
                publishShortcuts()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeRuntime()
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { openingStore?.close() }
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { openingStore?.close() }
                startupError = "Der lokale Bestand benötigt eine Prüfung. Bestehende Daten bleiben erhalten; es wurde nichts automatisch übernommen."
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleLaunch(intent) }
    private fun handleLaunch(intent: Intent?, fresh: Boolean = true) {
        val current = controller ?: return
        val shortcut = intent?.getStringExtra(SHORTCUT)
        if (intent != null && shortcut != null) {
            intent.removeExtra(SHORTCUT)
            if (fresh) when (shortcut) {
                "start" -> current.navigate("timer")
                "photo" -> current.navigate("photos", mode = "import", args = JSONObject().put("_open_camera", true))
                "worklog" -> current.navigate("worklogs", mode = "new")
            }
            return
        }
        val work = intent?.getStringExtra(WorkNotice.EXTRA)
        if (intent != null && work != null) {
            intent.removeExtra(WorkNotice.EXTRA)
            // A recreated activity must not repeat a notification action.
            if (fresh) lifecycleScope.launch { workNoticeAction(current, work, intent.getStringExtra("session_id").orEmpty()) }
            return
        }
        if (intent?.getBooleanExtra("open_planning", false) != true) return
        val id = intent.getStringExtra("planning_appointment_id") ?: ""
        current.openSection("planning")
        current.navigate("appointments", id, if (id.isEmpty()) "list" else "detail", JSONObject().put("action", intent.getStringExtra("planning_action") ?: ""))
        intent.removeExtra("open_planning")
    }
    /** Notification actions run only when they still match the current session; repeats are ignored. */
    private suspend fun workNoticeAction(current: NativeAppController, action: String, id: String) {
        try {
            val session = withContext(Dispatchers.IO) { current.store.workSessions().current() }
            val state = session.optString("state")
            val same = id.isNotEmpty() && session.optString("id", session.optString("session_id")) == id
            val args = JSONObject().put("session_id", id).apply {
                for (field in listOf("customer_id", "order_id", "property_id", "visit_id")) session.optString(field).takeIf { it.isNotEmpty() }?.let { put(field, it) }
            }
            when {
                same && action == "photo" && state == "RUNNING" -> current.navigate("photos", mode = "import", args = args.put("_open_camera", true))
                same && (action == "stop_work" && state in setOf("RUNNING", "PAUSED") || action == "start_break" && state == "RUNNING" || action == "end_break" && state == "PAUSED") -> {
                    current.navigate("timer")
                    current.timerAction(action, id)
                    current.message(when (action) { "stop_work" -> "Zeit gestoppt."; "start_break" -> "Pause läuft."; else -> "Zeit läuft weiter." })
                }
                else -> current.navigate("timer")
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { current.navigate("timer"); current.message(failure.message ?: "Zeitvorgang nicht bestätigt. Bitte Zeitstand prüfen.") }
        refreshWorkNotice()
    }
    /** Long press on the app icon offers the three most frequent actions. */
    private fun publishShortcuts() {
        val manager = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        fun shortcut(id: String, label: String) = android.content.pm.ShortcutInfo.Builder(this, id).setShortLabel(label)
            .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.app_icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(SHORTCUT, id)).build()
        try { manager.dynamicShortcuts = listOf(shortcut("start", "Arbeit starten"), shortcut("photo", "Foto"), shortcut("worklog", "Arbeit nachtragen")) }
        catch (_: RuntimeException) { android.util.Log.w("HausGartenShortcuts", "PUBLISH_FAILED") }
    }
    private suspend fun refreshWorkNotice() {
        val local = store ?: return
        val session = try { withContext(Dispatchers.IO) { local.workSessions().current() } }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { return }
        if (session.optString("state") == "RUNNING" && !WorkNotice.allowed(this) && !preferences.getBoolean("work_notice_asked", false)) {
            preferences.edit().putBoolean("work_notice_asked", true).apply()
            try { requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), WORK_NOTICE_REQUEST) } catch (_: RuntimeException) { }
        }
        WorkNotice.update(this, session)
    }
    override fun onResume() {
        super.onResume()
        foreground = WeakReference(this)
        if (store != null) resumeRuntime()
    }
    private fun resumeRuntime() {
        refreshPlanningView()
        if (BuildConfig.UI_PREVIEW) return
        val connectivity = getSystemService(ConnectivityManager::class.java)
        if (connectivity != null && planningNetworkCallback == null) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                private var ready: Network? = null
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        if (network != ready) { ready = network; maintenance.launch { PlanningJob.schedule(this@MainActivity, 1000) } }
                    } else if (network == ready) ready = null
                }
                override fun onLost(network: Network) { if (network == ready) ready = null }
            }
            try { connectivity.registerDefaultNetworkCallback(callback); planningNetworkCallback = callback } catch (_: RuntimeException) { }
        }
        maintenance.launch {
            PlanningJob.schedule(this@MainActivity, 1500)
            val config = PixelTunnelConfig(this@MainActivity)
            if (config.enabled() && config.configured() && !PixelTunnelService.status(this@MainActivity).optBoolean("running")) {
                try { startForegroundService(Intent(this@MainActivity, PixelTunnelService::class.java)) }
                catch (_: RuntimeException) {
                    getSharedPreferences("pixel_tunnel_status", MODE_PRIVATE).edit().putString("state", "start_blocked")
                        .putString("detail", "Android hat den Wiederstart eingeschränkt. Bitte Verbindung in der App starten.").apply()
                }
            }
        }
    }
    override fun onPause() {
        if (foreground.get() === this) foreground.clear()
        val callback = planningNetworkCallback
        if (callback != null) try { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) } catch (_: RuntimeException) { }
        planningNetworkCallback = null
        super.onPause()
    }

    /** The picker and camera only select a draft source. Saving remains an explicit business action. */
    suspend fun pickPhoto(camera: Boolean): Uri? = withContext(Dispatchers.Main) {
        if (photoWaiter?.isActive == true) throw IOException("Eine Fotoauswahl läuft bereits.")
        if (camera) {
            clearPendingCapture(true)
            try {
                val target = withContext(Dispatchers.IO) { PhotoCaptureProvider.createTarget(this@MainActivity) }
                pendingCaptureFile = target
                pendingCaptureUri = withContext(Dispatchers.IO) { PhotoCaptureProvider.uri(this@MainActivity, target) }
            } catch (error: Exception) { failPhotoCapture("PREPARE_FAILED", "Die Fotodatei konnte nicht vorbereitet werden. Bitte freien Speicher und Geräteeinschränkungen prüfen.", error); return@withContext null }
        }
        suspendCancellableCoroutine { continuation ->
            photoWaiter = continuation
            try {
                if (camera) photoResult.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri)
                    .apply { clipData = ClipData.newRawUri("Kamerafoto", pendingCaptureUri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                else photoResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            } catch (error: Exception) {
                photoWaiter = null
                lifecycleScope.launch { if (camera) failPhotoCapture("LAUNCH_FAILED", if (error is ActivityNotFoundException) "Keine Kamera-App für Fotoaufnahmen verfügbar." else "Die Kamera konnte nicht gestartet werden. Bitte Geräteeinschränkungen prüfen.", error) else toast("Keine Fotoauswahl verfügbar.") }
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { if (photoWaiter === continuation) photoWaiter = null }
        }
    }
    private suspend fun failPhotoCapture(code: String, message: String, error: Exception) {
        logPhotoCaptureFailure(code, error)
        clearPendingCapture(true)
        toast(message)
    }
    private suspend fun clearPendingCapture(delete: Boolean) {
        val uri = pendingCaptureUri
        val file = pendingCaptureFile
        pendingCaptureUri = null; pendingCaptureFile = null
        if (uri != null) try { revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (error: RuntimeException) { logPhotoCaptureFailure("REVOKE_FAILED", error) }
        if (delete && file != null) withContext(Dispatchers.IO) {
            try { if (file.exists() && !file.delete()) android.util.Log.w("HausGartenCamera", "TEMP_DELETE_FAILED") }
            catch (error: SecurityException) { logPhotoCaptureFailure("TEMP_DELETE_DENIED", error) }
        }
    }
    suspend fun currentWorkLocation(): JSONObject = withContext(Dispatchers.Main) {
        locationWaiter?.second?.let { if (it.isActive) it.resume(JSONObject().put("status", "CANCELLED")) }
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { continuation ->
            locationWaiter = id to continuation
            workLocation?.request(id)
            continuation.invokeOnCancellation { runOnUiThread { if (locationWaiter?.first == id) { locationWaiter = null; workLocation?.cancel() } } }
        }
    }
    fun recognizeSpeech(result: (String) -> Unit) {
        speechResult = result
        try { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN.toLanguageTag()).putExtra(RecognizerIntent.EXTRA_PROMPT, "Arbeitsaufzeichnung sprechen")) }
        catch (_: ActivityNotFoundException) { speechResult = null; toast("Keine Spracheingabe eingerichtet.") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WORK_NOTICE_REQUEST) lifecycleScope.launch { refreshWorkNotice() } else workLocation?.permissionResult(requestCode)
    }
    fun openBackup() {
        if (BuildConfig.UI_PREVIEW) { toast("Verbindungen und Sicherungen bleiben in der Vorschau ausgeschaltet."); return }
        lifecycleScope.launch {
            try {
                controller?.flushDrafts()
                startActivity(Intent(this@MainActivity, FullBackupActivity::class.java))
            } catch (_: Exception) { toast("Formulare konnten noch nicht vollständig gespeichert werden. Bitte erneut prüfen.") }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("native_route", controller?.savedNavigation())
        outState.putString("capture_name", pendingCaptureFile?.name)
    }
    override fun onDestroy() {
        if (foreground.get() === this) foreground.clear()
        workLocation?.destroy()
        locationWaiter?.second?.cancel(); locationWaiter = null
        photoWaiter?.cancel(); photoWaiter = null
        speechResult = null
        val current = controller
        val local = store
        controller = null
        store = null
        super.onDestroy()
        CoroutineScope(Dispatchers.IO).launch {
            maintenance.coroutineContext[Job]?.cancelAndJoin()
            try { current?.close() }
            catch (_: Exception) { android.util.Log.w("HausGartenDrafts", "DRAFT_FLUSH_FAILED") }
            finally { local?.close() }
        }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    companion object {
        @Volatile private var foreground = WeakReference<MainActivity>(null)
        private const val WORK_NOTICE_REQUEST = 0x574b
        internal const val SHORTCUT = "shortcut"
        @JvmStatic fun refreshPlanningView() { refreshBusinessView() }
        @JvmStatic fun refreshBusinessView() {
            val activity = foreground.get() ?: return
            activity.maintenance.launch { activity.store?.notifyCommittedChange() }
        }
        private fun logPhotoCaptureFailure(code: String, error: Exception) {
            val cause = error.cause?.let { "; cause=" + it.javaClass.simpleName } ?: ""
            android.util.Log.e("HausGartenCamera", code + "; type=" + error.javaClass.simpleName + cause)
        }
    }
}
