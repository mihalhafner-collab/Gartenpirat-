package at.hausgarten.service

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native credential setup; secrets stay outside saved state and business data. */
class PixelTunnelActivity : ComponentActivity() {
    private lateinit var config: PixelTunnelConfig
    private var status by mutableStateOf("Status wird geprüft …")
    private var battery by mutableStateOf("")
    private var diagnosis by mutableStateOf("")
    private var lastContact by mutableStateOf("Noch kein bestätigter Tunnelkontakt.")
    private var lastResponse by mutableStateOf("Noch keine beantwortete Anfrage bestätigt.")
    private var access by mutableStateOf("")
    private var statusTone by mutableStateOf(NativeStatusTone.Neutral)
    private var enabled by mutableStateOf(false)
    private var advanced by mutableStateOf(false)
    private var tunnelId by mutableStateOf("")
    private var organization by mutableStateOf("")
    private var runtimeKey by mutableStateOf("")
    private var write by mutableStateOf(false)
    private var configured by mutableStateOf(false)
    private var busy by mutableStateOf(true)
    private var requestedWrite: Boolean? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (requestedWrite == null) return@registerForActivityResult
            pendingNotificationResult = granted
            consumeNotificationResult()
        }
    private var pendingNotificationResult: Boolean? = null
    private var configurationReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.containsKey("pending_notification_write") == true) {
            requestedWrite = savedInstanceState.getBoolean("pending_notification_write")
            if (savedInstanceState.containsKey("pending_notification_result"))
                pendingNotificationResult =
                    savedInstanceState.getBoolean("pending_notification_result")
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            try {
                val initial =
                    withContext(Dispatchers.IO) {
                        RevierApplication.awaitStartup(this@PixelTunnelActivity)
                        config = PixelTunnelConfig(this@PixelTunnelActivity)
                        listOf(
                            config.id(),
                            config.organization(),
                            config.enabled().toString(),
                            config.writeEnabled().toString(),
                            config.configured().toString(),
                        )
                    }
                tunnelId = initial[0]
                organization = initial[1]
                enabled = initial[2].toBoolean()
                write = requestedWrite ?: initial[3].toBoolean()
                configured = initial[4].toBoolean()
                refreshStatus()
                configurationReady = true
                busy = false
                consumeNotificationResult()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = "Der lokale Bestand benötigt zuerst eine Wiederherstellungsprüfung."
                startActivity(Intent(this@PixelTunnelActivity, RestoreRecoveryActivity::class.java))
                finish()
            }
        }
        window.nativeLightSystemBars()
        setContent {
            HausGartenTheme {
                Scaffold(
                    modifier = Modifier.safeDrawingPadding().imePadding(),
                    bottomBar = {
                        NativeActionBar(
                            "Status erneut prüfen",
                            { lifecycleScope.launch { refreshStatus() } },
                            if (enabled) "Verbindung beenden" else "Zurück",
                            { if (enabled) stopConnection() else finish() },
                            enabled = !busy,
                            secondaryEnabled = !busy,
                        )
                    },
                ) { insets ->
                    Column(
                        Modifier.fillMaxSize()
                            .padding(insets)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("ChatGPT-Verbindung", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Eine private Verbindung zu diesem Pixel. Mit deiner Freigabe nutzt ChatGPT den aktuellen Funktionsumfang der App. Die lokale Arbeitserfassung benötigt diese Verbindung nicht."
                        )
                        NativeStatus(status, statusTone)
                        NativeInfoRow("Letzte beantwortete Anfrage", lastResponse)
                        NativeInfoRow("Letzter Tunnelkontakt", lastContact)
                        NativeInfoRow("Zugriff", access)
                        Text(
                            "Ein Tunnelkontakt bestätigt noch keine beantwortete Anfrage. Eine bestätigte Antwort gilt für ihren Zeitpunkt. Android und Netzwerk können die Verbindung später unterbrechen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            Text("Pixel-Verbindung eingeschaltet", Modifier.weight(1f))
                            Switch(
                                enabled,
                                { on ->
                                    if (on) {
                                        if (!configured) {
                                            advanced = true
                                            toast("Bitte die Verbindung einmalig einrichten.")
                                        } else startSavedConnection()
                                    } else stopConnection()
                                },
                                enabled = !busy,
                            )
                        }
                        Row {
                            Text("ChatGPT darf Daten ändern", Modifier.weight(1f))
                            Switch(write, ::changeWrite, enabled = !busy)
                        }
                        Text(
                            "Kunden, Arbeitszeiten, Angebote, Abrechnung und Fotos. Ausgeschaltet kann ChatGPT nur lesen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { advanced = !advanced }) {
                            Text(
                                if (advanced) "Einrichtung und Diagnose schließen"
                                else "Einrichtung und Diagnose"
                            )
                        }
                        if (advanced) {
                            HorizontalDivider()
                            Text(battery)
                            Text(diagnosis)
                            Text(
                                "Einmalig einrichten",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Die bestehende private Pixel-Verbindung verwenden. Ein eigener Runtime-Schlüssel wird verschlüsselt auf diesem Gerät gespeichert."
                            )
                            OutlinedTextField(
                                tunnelId,
                                { tunnelId = it },
                                label = { Text("Pixel-Tunnel-ID") },
                                placeholder = { Text("tunnel_…") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                organization,
                                { organization = it },
                                label = { Text("OpenAI-Organisation des Tunnels") },
                                placeholder = { Text("org-…") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Tunnel und Schlüssel müssen zur selben OpenAI-Organisation gehören. Die Angaben werden beim Übernehmen gespeichert. Ein gültiger Schlüssel aus einem anderen Konto funktioniert hier nicht."
                            )
                            OutlinedTextField(
                                runtimeKey,
                                { runtimeKey = it },
                                label = { Text("Runtime-Schlüssel") },
                                placeholder = {
                                    Text(
                                        if (configured) "Bereits gespeichert · leer lassen"
                                        else "Schlüssel hier einfügen"
                                    )
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        autoCorrectEnabled = false,
                                    ),
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = ::startConnection, enabled = !busy) {
                                Text("Einrichtung und Freigabe übernehmen")
                            }
                            TextButton(onClick = ::stopConnection, enabled = !busy) {
                                Text("Verbindung beenden")
                            }
                            Text("Hintergrundbetrieb", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Damit Android die Verbindung seltener unterbricht, Hintergrundnutzung zulassen und diese App vom extremen Energiesparen ausnehmen. Eine sichtbare Statusmeldung bleibt aktiv."
                            )
                            TextButton(
                                onClick = {
                                    open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null)
                                }
                            ) {
                                Text("Akku-Einstellungen öffnen")
                            }
                            TextButton(
                                onClick = {
                                    open(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:$packageName"),
                                    )
                                }
                            ) {
                                Text("App- und Hintergrunddaten-Einstellungen")
                            }
                            TextButton(
                                onClick = {
                                    open(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://platform.openai.com/settings/organization/tunnels"
                                        ),
                                    )
                                }
                            ) {
                                Text("OpenAI-Tunnel verwalten")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (configurationReady) lifecycleScope.launch { refreshStatus() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        requestedWrite?.let { outState.putBoolean("pending_notification_write", it) }
        pendingNotificationResult?.let { outState.putBoolean("pending_notification_result", it) }
    }

    private fun open(action: String, uri: Uri?) {
        try {
            startActivity(Intent(action).apply { if (uri != null) data = uri })
        } catch (_: ActivityNotFoundException) {
            toast("Diese Einstellung ist auf dem Gerät nicht verfügbar.")
        }
    }

    private fun startConnection() {
        if (busy) return
        busy = true
        val entered = runtimeKey.trim()
        val tunnel = tunnelId.trim()
        val org = organization.trim()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PixelTunnelConfig.validateId(tunnel)
                    PixelTunnelConfig.validateOrganization(org)
                    if (entered.isNotEmpty()) {
                        stopService(
                            Intent(this@PixelTunnelActivity, PixelTunnelService::class.java)
                        )
                        config.save(tunnel, org, entered)
                    } else if (!config.configured() || config.id() != tunnel)
                        throw IllegalArgumentException(
                            "Bitte für den neuen Tunnel den Runtime-Schlüssel eingeben."
                        )
                    else if (config.organization() != org) {
                        stopService(
                            Intent(this@PixelTunnelActivity, PixelTunnelService::class.java)
                        )
                        config.organization(org)
                    }
                }
                if (entered.isNotEmpty()) runtimeKey = ""
                configured = true
                requestConnection(write)
            } catch (error: Exception) {
                AlertDialog.Builder(this@PixelTunnelActivity)
                    .setTitle("Verbindung noch nicht eingerichtet")
                    .setMessage(
                        if (error is IllegalArgumentException) error.message
                        else
                            "Einstellungen konnten nicht sicher gespeichert werden. Bitte erneut versuchen."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } finally {
                busy = false
            }
        }
    }

    /**
     * Takes effect at once: a running connection restarts with the new access, otherwise only the
     * choice is saved.
     */
    private fun changeWrite(permitted: Boolean) {
        if (busy) return
        write = permitted
        busy = true
        lifecycleScope.launch {
            try {
                if (enabled && configured) requestConnection(permitted)
                else {
                    withContext(Dispatchers.IO) { config.writeEnabled(permitted) }
                    refreshStatus()
                }
            } finally {
                busy = false
            }
        }
    }

    private fun startSavedConnection() {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                requestConnection(withContext(Dispatchers.IO) { config.writeEnabled() })
            } finally {
                busy = false
            }
        }
    }

    private suspend fun requestConnection(permitted: Boolean) {
        pendingNotificationResult = null
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            requestedWrite = permitted
            try {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (failure: RuntimeException) {
                requestedWrite = null
                throw failure
            }
        } else {
            requestedWrite = null
            launchConnection(permitted)
        }
    }

    private suspend fun launchConnection(permitted: Boolean) {
        try {
            withContext(Dispatchers.IO) {
                if (config.writeEnabled() != permitted)
                    stopService(Intent(this@PixelTunnelActivity, PixelTunnelService::class.java))
                config.writeEnabled(permitted)
                config.enabled(true)
                startForegroundService(
                    Intent(this@PixelTunnelActivity, PixelTunnelService::class.java)
                )
            }
            refreshStatus()
        } catch (_: RuntimeException) {
            withContext(Dispatchers.IO) { config.enabled(false) }
            enabled = false
            toast("Android hat den Dienststart eingeschränkt. Bitte die Akku-Einstellungen prüfen.")
        }
    }

    private fun stopConnection() {
        if (busy) return
        requestedWrite = null
        pendingNotificationResult = null
        lifecycleScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    config.enabled(false)
                    stopService(Intent(this@PixelTunnelActivity, PixelTunnelService::class.java))
                }
                refreshStatus()
            } finally {
                busy = false
            }
        }
    }

    private fun consumeNotificationResult() {
        if (!configurationReady) return
        val permitted = requestedWrite ?: return
        val granted = pendingNotificationResult ?: return
        requestedWrite = null
        pendingNotificationResult = null
        if (!granted) {
            toast(
                "Bitte Benachrichtigungen freigeben, damit du die laufende Verbindung sehen und beenden kannst."
            )
            return
        }
        busy = true
        lifecycleScope.launch {
            try {
                launchConnection(permitted)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun refreshStatus() {
        try {
            val info =
                withContext(Dispatchers.IO) { PixelTunnelService.status(this@PixelTunnelActivity) }
            diagnosis = info.optString("detail")
            enabled = info.optBoolean("enabled")
            configured = info.optBoolean("configured")
            status = pixelConnectionLabel(info)
            statusTone = pixelConnectionTone(info)
            lastContact =
                pixelConnectionTime(
                    info.optLong("last_contact_at"),
                    "Noch kein bestätigter Tunnelkontakt.",
                )
            lastResponse =
                pixelConnectionTime(
                    info.optLong("last_response_at"),
                    "Noch keine beantwortete Anfrage bestätigt.",
                )
            access =
                (if (enabled) "Lesen lokal eingeschaltet."
                else "Verbindung und Zugriff ausgeschaltet.") +
                    "\nGespeicherte Schreibfreigabe: " +
                    (if (info.optBoolean("write_enabled")) "EIN." else "AUS.") +
                    " Keine Schreibprobe ausgeführt."
            battery =
                if (info.optBoolean("battery_exempt")) "Akku-Ausnahme ist aktiv."
                else
                    "Akku-Ausnahme noch nicht aktiv. Bei Energiesparen kann die Verbindung unterbrochen werden."
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = "Lokaler Verbindungsstatus derzeit nicht lesbar."
            statusTone = NativeStatusTone.Attention
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}

internal fun pixelConnectionLabel(info: org.json.JSONObject): String =
    when {
        info.optString("state") == "status_unavailable" -> "Lokaler Status nicht lesbar"
        info.optString("state") == "auth_error" -> "Verbindungsfreigabe prüfen"
        !info.optBoolean("configured") -> "Noch nicht eingerichtet"
        !info.optBoolean("enabled") -> "Pixel-Verbindung ausgeschaltet"
        !info.optBoolean("running") -> "Tunnel eingeschaltet · Pixel-Dienst läuft nicht"
        info.optBoolean("transport_confirmed") -> "Tunnel eingeschaltet · letzter Kontakt bestätigt"
        info.optString("state") in
            listOf(
                "offline",
                "reconnecting",
                "rate_limited",
                "local_review_required",
                "start_blocked",
            ) -> "Tunnel eingeschaltet · derzeit nicht bestätigt"
        else -> "Tunnel eingeschaltet · Verbindung ausstehend"
    }

internal fun pixelConnectionTone(info: org.json.JSONObject): NativeStatusTone =
    when {
        info.optString("state") in
            listOf("auth_error", "local_review_required", "start_blocked", "status_unavailable") ->
            NativeStatusTone.Attention
        !info.optBoolean("enabled") -> NativeStatusTone.Neutral
        info.optBoolean("transport_confirmed") -> NativeStatusTone.Confirmed
        else -> NativeStatusTone.Attention
    }

internal fun pixelConnectionTime(value: Long, empty: String): String =
    if (value <= 0) empty
    else
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
            .withZone(ZoneId.of("Europe/Vienna"))
            .format(Instant.ofEpochMilli(value))
