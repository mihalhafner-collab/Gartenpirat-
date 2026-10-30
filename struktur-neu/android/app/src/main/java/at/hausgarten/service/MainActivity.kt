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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import kotlin.coroutines.resume

/** Native Compose entry point; platform consent stays separate from business writers. */
class MainActivity : ComponentActivity(), McpSyncHost {
    private lateinit var preferences: SharedPreferences
    private var store: PixelLocalStore? = null
    private var controller by mutableStateOf<NativeAppController?>(null)
    private var startupError by mutableStateOf("")
    private val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncNetworkCallback: ConnectivityManager.NetworkCallback? = null
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
                if (current?.route?.section == "restore_review") finish()
                else if (current?.canGoBack() == true) current.back() else finish()
            }
        })
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
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
                    LocalAuthorityGuard.assertStoreMayOpen(this@MainActivity)
                    preferences = getSharedPreferences("haus_garten", MODE_PRIVATE)
                    PixelLocalStore(this@MainActivity).also { local ->
                        openingStore = local
                        if (!BuildConfig.UI_PREVIEW) {
                            try { DriveSyncJob.install(this@MainActivity) } catch (_: RuntimeException) { backgroundState(local, true, "BACKGROUND_SCHEDULING_FAILED") }
                            try { if (PlanningAccount.connected(this@MainActivity) || PlanningAccount.prefs(this@MainActivity).getBoolean("weather_enabled", false)) PlanningJob.install(this@MainActivity) }
                            catch (_: RuntimeException) { backgroundState(local, true, "BACKGROUND_SCHEDULING_FAILED") }
                        }
                        try { if (local.compactPurgedTestActions() > 0) local.notifyCommittedChange() }
                        catch (_: Exception) { runOnUiThread { toast("Testbereinigung benötigt Prüfung. Bestehende Daten bleiben erhalten.") } }
                    }
                }
                store = opened
                controller = NativeAppController(this@MainActivity, opened).also { it.restoreRoute(savedInstanceState?.getString("native_route")) }
                handleLaunch(intent)
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
    private fun handleLaunch(intent: Intent?) {
        val current = controller ?: return
        if (intent?.getBooleanExtra("open_planning", false) != true) return
        val id = intent.getStringExtra("planning_appointment_id") ?: ""
        current.navigate("appointments", id, if (id.isEmpty()) "list" else "detail", JSONObject().put("action", intent.getStringExtra("planning_action") ?: ""))
        intent.removeExtra("open_planning")
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
        if (connectivity != null && syncNetworkCallback == null) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                private var ready: Network? = null
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        if (network != ready) { ready = network; requestAutomaticSync(0); maintenance.launch { PlanningJob.schedule(this@MainActivity, 1000) } }
                    } else if (network == ready) ready = null
                }
                override fun onLost(network: Network) { if (network == ready) ready = null }
            }
            try { connectivity.registerDefaultNetworkCallback(callback); syncNetworkCallback = callback } catch (_: RuntimeException) { }
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
        requestAutomaticSync(0)
    }
    override fun onPause() {
        if (foreground.get() === this) foreground.clear()
        val callback = syncNetworkCallback
        if (callback != null) try { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) } catch (_: RuntimeException) { }
        syncNetworkCallback = null
        super.onPause()
    }
    override fun requestMcpSync() { requestMcpSync(this) }

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
        workLocation?.permissionResult(requestCode)
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
    private fun backgroundState(local: PixelLocalStore, pending: Boolean, code: String) {
        try { local.putOwnerValue("background_work_dispatch", JSONObject().put("formatVersion", 1).put("pending", pending).put("code", code).toString()) } catch (_: Exception) { }
        try { preferences.edit().putBoolean("background_work_pending", pending).putString("background_work_error_code", code).commit() } catch (_: RuntimeException) { }
    }
    private fun requestAutomaticSync(delayMs: Long) {
        if (BuildConfig.UI_PREVIEW) return
        val local = store ?: return
        maintenance.launch {
            BackgroundWorkDispatch.request(delayMs, object : BackgroundWorkDispatch.Host {
                override fun state(pending: Boolean, code: String) { backgroundState(local, pending, code) }
                override fun post(task: Runnable): Boolean { maintenance.launch { task.run() }; return true }
                override fun prepare() { if (LocalAuthorityGuard.local(this@MainActivity)) scheduleLocalMaintenance(local) }
                override fun localReady(): Boolean = false
                override fun local(delay: Long): Boolean = false
            })
        }
    }
    private fun scheduleLocalMaintenance(local: PixelLocalStore) {
        val generation = UUID.randomUUID().toString()
        try {
            local.putOwnerValue("local_media_cleanup", JSONObject().put("formatVersion", 1).put("pending", true).put("generation", generation).toString())
            val result = TrashMediaCleanup.runLocal(this, local)
            val database = local.writableDatabase
            database.beginTransaction()
            try {
                val marker = JSONObject(local.ownerValue("local_media_cleanup", "{}"))
                if (generation == marker.optString("generation")) local.putOwnerValue("local_media_cleanup", marker.put("pending", result.optInt("pending") > 0).put("code", result.optString("error_code")).toString())
                database.setTransactionSuccessful()
            } finally { database.endTransaction() }
        } catch (_: Exception) {
            try { local.putOwnerValue("local_media_cleanup", JSONObject().put("formatVersion", 1).put("pending", true).put("code", "LOCAL_MEDIA_CLEANUP_PENDING").toString()) } catch (_: Exception) { }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("native_route", controller?.savedRoute())
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

    class VerifiedInvoiceShare(@JvmField val invoice: JSONObject, @JvmField val bytes: ByteArray)
    companion object {
        @Volatile private var foreground = WeakReference<MainActivity>(null)
        private val BACKUP_SECRET_NAME = Pattern.compile("password|passwd|secret|credential|token|authorization|oauth|recovery.?code|api.?key", Pattern.CASE_INSENSITIVE)
        @JvmStatic fun refreshPlanningView() { refreshBusinessView() }
        @JvmStatic fun refreshBusinessView() {
            val activity = foreground.get() ?: return
            activity.maintenance.launch { activity.store?.notifyCommittedChange() }
        }
        @JvmStatic fun requestMcpSync(context: Context) {
            if (BuildConfig.UI_PREVIEW) return
            refreshBusinessView()
            foreground.get()?.requestAutomaticSync(0)
        }
        @JvmStatic @Throws(Exception::class)
        fun verifiedLocalInvoiceShare(context: Context, store: PixelLocalStore, supplied: JSONObject): VerifiedInvoiceShare {
            val all = PixelMcpReadTools.effective(store.getState())
            val invoice = JSONObject(PixelMcpWriteTools.find(all.getJSONArray("invoices"), supplied.getString("id")).toString())
            if (!IssuedInvoiceRules.isIssued(invoice)) throw IOException("Nur ausgestellte Originalbelege teilen.")
            TestDataRules.noDelivery(invoice, all)
            return VerifiedInvoiceShare(invoice, BusinessDocumentStore(context).read(invoice))
        }
        private fun logPhotoCaptureFailure(code: String, error: Exception) {
            val cause = error.cause?.let { "; cause=" + it.javaClass.simpleName } ?: ""
            android.util.Log.e("HausGartenCamera", code + "; type=" + error.javaClass.simpleName + cause)
        }
        @JvmStatic @Throws(Exception::class)
        fun sanitizeBackupCheckpoint(source: JSONObject): JSONObject {
            if (source.optInt("formatVersion") != 1 || source.optJSONObject("storage") == null) throw IOException("BACKUP_BROWSER_CAPTURE_REQUIRED")
            val value = JSONObject().put("formatVersion", 1).put("storage", JSONObject(source.getJSONObject("storage").toString()))
            val storage = value.getJSONObject("storage")
            val keys = storage.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                if (BACKUP_SECRET_NAME.matcher(name).find()) keys.remove()
                else if (storage.get(name) !is String) throw IOException("BACKUP_BROWSER_CAPTURE_REQUIRED")
            }
            val drafts = source.opt("drafts")
            if (drafts != null && drafts !== JSONObject.NULL) value.put("drafts", sanitizeBackupDraftValue(drafts))
            val mirror = "hausgarten.fullbackup.formdrafts.v1"
            if (storage.has(mirror)) try {
                val original = JSONObject(storage.getString(mirror))
                val sanitized = sanitizeBackupDraftValue(original)
                if (PixelMcpWriteTools.hash(original) != PixelMcpWriteTools.hash(sanitized)) storage.put(mirror, sanitized.toString())
            } catch (_: JSONException) { throw IOException("BACKUP_BROWSER_CAPTURE_REQUIRED") }
            if (source.has("editor_state")) {
                val state = source.optJSONObject("editor_state")
                val identity = state?.optJSONObject("context")
                if (state == null || state.length() != 4 || state.opt("formatVersion") !is Int || state.getInt("formatVersion") != 1 || state.opt("review_required") !is Boolean || state.opt("record_hash") !is String || !state.getString("record_hash").matches(Regex("[0-9a-f]{64}")) || identity == null || identity.length() != 2) throw IOException("BACKUP_BROWSER_CAPTURE_REQUIRED")
                for (name in arrayOf("installationId", "restoreEpoch")) if (identity.opt(name) !is String || !identity.getString(name).matches(Regex("[0-9a-f-]{36}"))) throw IOException("BACKUP_BROWSER_CAPTURE_REQUIRED")
                value.put("editor_state", JSONObject().put("formatVersion", 1).put("context", JSONObject(identity.toString())).put("review_required", state.getBoolean("review_required")).put("record_hash", state.getString("record_hash")))
            }
            return value
        }
        private fun sanitizeBackupDraftValue(value: Any?): Any? = when (value) {
            is JSONArray -> JSONArray().also { safe ->
                for (i in 0 until value.length()) {
                    val row = value.get(i)
                    if (row is JSONObject) {
                        val type = row.optString("type")
                        val name = row.optString("id") + "." + row.optString("name") + "." + row.optString("autocomplete")
                        if (type.equals("password", true) || type.equals("file", true) || BACKUP_SECRET_NAME.matcher(name).find()) continue
                    }
                    safe.put(sanitizeBackupDraftValue(row))
                }
            }
            is JSONObject -> JSONObject().also { safe ->
                val keys = value.keys()
                while (keys.hasNext()) { val name = keys.next(); if (!BACKUP_SECRET_NAME.matcher(name).find()) safe.put(name, sanitizeBackupDraftValue(value.get(name))) }
            }
            else -> value
        }
    }
}
