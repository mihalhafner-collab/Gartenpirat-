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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native credential setup; secrets stay outside saved state and business data. */
class PixelTunnelActivity : ComponentActivity() {
    private lateinit var config: PixelTunnelConfig
    private var status by mutableStateOf("Status wird geprüft …")
    private var battery by mutableStateOf("")
    private var diagnosis by mutableStateOf("")
    private var enabled by mutableStateOf(false)
    private var advanced by mutableStateOf(false)
    private var tunnelId by mutableStateOf("")
    private var organization by mutableStateOf("")
    private var runtimeKey by mutableStateOf("")
    private var write by mutableStateOf(false)
    private var configured by mutableStateOf(false)
    private var busy by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
            write = initial[3].toBoolean()
            configured = initial[4].toBoolean()
            busy = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                status = "Der lokale Bestand benötigt zuerst eine Wiederherstellungsprüfung."
                startActivity(Intent(this@PixelTunnelActivity, RestoreRecoveryActivity::class.java))
                finish()
            }
        }
        nativeContent("ChatGPT auf dem Pixel") {
            Text(
                "Mit deiner Freigabe kann ChatGPT die fachlichen Funktionen dieser App bedienen. Ausschalten beendet die Verbindung; deine Daten bleiben erhalten."
            )
            Text(status)
            Row {
                Text("ChatGPT-Steuerung · EIN / AUS", Modifier.weight(1f))
                Switch(
                    enabled,
                    { on ->
                        if (on) {
                            if (!configured) {
                                advanced = true
                                toast("Bitte die Verbindung einmalig einrichten.")
                            } else startConnection()
                        } else stopConnection()
                    },
                    enabled = !busy,
                )
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text("Erweitert / Diagnose · Einrichtung und Rechte")
            }
            if (advanced) {
                HorizontalDivider()
                Text(battery)
                Text(diagnosis)
                Text("Einmalig einrichten", style = MaterialTheme.typography.titleMedium)
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
                    "Tunnel und Schlüssel müssen zur selben OpenAI-Organisation gehören. Die vorbelegte Angabe wird erst mit Verbindung starten gespeichert. Ein gültiger Schlüssel aus einem anderen Konto funktioniert hier nicht."
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
                Row {
                    Text(
                        "ChatGPT darf fachliche Eigentümerfunktionen lesen und schreiben",
                        Modifier.weight(1f),
                    )
                    Switch(write, { write = it }, enabled = !busy)
                }
                Text(
                    "Freigabe für Kunden, Aufträge, Arbeitszeiten, Fotos, Entwürfe, Konfliktprüfung, Archiv und Sicherungen. Originalbelege bleiben unveränderlich; Abschluss und Korrekturen benötigen den geschützten lokalen Nummernkreis. Kontozugänge und Android-Rechte werden hier nicht erweitert. Änderungen werden lokal gespeichert. Die Freigabe gilt nach Verbindung starten."
                )
                Button(onClick = ::startConnection, enabled = !busy) { Text("Verbindung starten") }
                TextButton(onClick = ::stopConnection, enabled = !busy) {
                    Text("Verbindung beenden")
                }
                Text("Hintergrundbetrieb", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Damit Android die Verbindung seltener unterbricht, Hintergrundnutzung zulassen und diese App vom extremen Energiesparen ausnehmen. Eine sichtbare Statusmeldung bleibt aktiv."
                )
                TextButton(
                    onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null) }
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
                            Uri.parse("https://platform.openai.com/settings/organization/tunnels"),
                        )
                    }
                ) {
                    Text("OpenAI-Tunnel verwalten")
                }
            }
            TextButton(onClick = ::finish) { Text("Zurück zur Haus & Garten App") }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    if (::config.isInitialized) refreshStatus()
                    delay(2000)
                }
            }
        }
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
                if (
                    Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 281)
                } else launchConnection()
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

    private suspend fun launchConnection() {
        val permitted = write
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

    @Deprecated("Platform permission callback retained for the existing request code")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 281) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                lifecycleScope.launch { launchConnection() }
            else
                toast(
                    "Bitte Benachrichtigungen freigeben, damit du die laufende Verbindung sehen und beenden kannst."
                )
        }
    }

    private suspend fun refreshStatus() {
        try {
            val (info, active) =
                withContext(Dispatchers.IO) {
                    PixelTunnelService.status(this@PixelTunnelActivity) to config.enabled()
                }
            diagnosis = info.optString("detail")
            enabled = active
            status =
                if (info.optString("state") == "auth_error")
                    "Bitte die ChatGPT-Verbindung erneut freigeben."
                else if (active) {
                    if (info.optString("state") == "ready") "ChatGPT ist verbunden."
                    else "ChatGPT ist eingeschaltet. Verbindung wird geprüft."
                } else "ChatGPT ist ausgeschaltet."
            battery =
                if (info.optBoolean("battery_exempt")) "Akku-Ausnahme ist aktiv."
                else
                    "Akku-Ausnahme noch nicht aktiv. Bei Energiesparen kann die Verbindung unterbrochen werden."
        } catch (_: Exception) {}
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
