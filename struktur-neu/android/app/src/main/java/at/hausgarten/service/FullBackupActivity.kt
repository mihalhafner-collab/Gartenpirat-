package at.hausgarten.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One selected plaintext ZIP save and one explicit restore; no app-managed cloud account. */
class FullBackupActivity : ComponentActivity() {
    companion object {
        private const val SAVE = 203
        private const val OPEN = 204
        // A selected operation finishes across Activity recreation; its request is retried explicitly.
        private val WORK = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private var status by mutableStateOf("Sicherungsstatus wird geprüft …")
    private var busy by mutableStateOf(true)
    private var ready by mutableStateOf(false)
    private var saveLabel by mutableStateOf("Sicherung speichern")
    private var showNewTarget by mutableStateOf(false)
    private var showRecoveryCode by mutableStateOf(false)
    private var showCheck by mutableStateOf(false)
    private var showActivate by mutableStateOf(false)
    private var showConfirmation by mutableStateOf(false)
    private var previousActivation by mutableStateOf("")
    // Deliberately absent from both SavedState and rememberSaveable.
    private var recoveryCode by mutableStateOf("")
    private var request = ""
    private var target = ""
    private var restoreRequest = ""
    private var restoreUri = ""
    private var restoreId = ""
    private var previewHash = ""
    private var legacyMedia = false
    private val initialized = CompletableDeferred<Unit>()
    private var resolvingDocument = false

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "Sicherung"
        if (state != null) {
            request = state.getString("request", "")
            target = state.getString("target", "")
            restoreRequest = state.getString("restore_request", "")
            restoreUri = state.getString("restore_uri", "")
        }
        showCheck = restoreUri.isNotEmpty()
        if (target.isNotEmpty()) {
            saveLabel = "Speichern erneut versuchen"
            status = "Der vorherige Speichervorgang ist noch nicht bestätigt. Mit „Speichern erneut versuchen“ prüfen und fortsetzen."
        }
        nativeContent("Sicherung", actions = {
            Button(enabled = ready && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                if (target.isEmpty()) chooseSave() else save()
            }) { Text(saveLabel) }
        }) {
            BackHandler(enabled = busy) { status = "Die Sicherung läuft noch. Bitte auf das Ergebnis warten." }
            Text("Speichert deinen vorhandenen Bestand als unverschlüsselte ZIP-Datei. Wähle in der Android-Dateiauswahl Drive oder einen lokalen Speicherort.")
            if (showNewTarget) OutlinedButton(enabled = ready && !busy, modifier = Modifier.fillMaxWidth(), onClick = { target = ""; chooseSave() }) { Text("Anderen Speicherort wählen") }
            Text(status)
            OutlinedButton(enabled = ready && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                recoveryCode = ""
                clearPreview()
                showRecoveryCode = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                chooseRestore()
            }) { Text("Sicherung wiederherstellen") }
            if (showRecoveryCode) OutlinedTextField(
                value = recoveryCode, onValueChange = { recoveryCode = it; clearPreview() },
                label = { Text("Schlüssel – nur für alte verschlüsselte Sicherungen") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()
            )
            if (showCheck) Button(enabled = ready && !busy, modifier = Modifier.fillMaxWidth(), onClick = { preview() }) { Text("Gewählte Sicherung prüfen") }
            if (showActivate) Button(enabled = ready && !busy, modifier = Modifier.fillMaxWidth(), onClick = { confirmRestore() }) { Text("Geprüfte Sicherung wiederherstellen") }
            TextButton(onClick = { if (busy) status = "Die Sicherung läuft noch. Bitte auf das Ergebnis warten." else finish() }) { Text("Zurück") }
            if (previousActivation.isNotEmpty()) OutlinedButton(enabled = ready && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                val id = UUID.randomUUID().toString()
                val code = recoveryCode
                val activation = previousActivation
                restoreRequest = id
                runTask { showPreview(BackupRestoreService(this@FullBackupActivity).previewPrevious(id, activation, code)) }
            }) { Text("Bestand vor letzter Wiederherstellung zurückholen") }
            if (showConfirmation) AlertDialog(
                onDismissRequest = { showConfirmation = false },
                title = { Text("Bestand wiederherstellen?") },
                text = { Text("Der aktuelle Bestand wird durch die geprüfte Sicherung ersetzt. Eine lokale Rückfallkopie bleibt erhalten." +
                    if (legacyMedia) " Die unvollständig belegte Fotoabdeckung dieser alten Sicherung wird ausdrücklich akzeptiert." else "") },
                dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("Abbrechen") } },
                confirmButton = { TextButton(onClick = { showConfirmation = false; activateRestore() }) { Text("Wiederherstellen") } }
            )
        }
        WORK.launch {
            if (!startupReady()) return@launch
            val initial = try {
                PixelBackupService.cancelRetiredJobs(this@FullBackupActivity)
                PixelLocalStore(this@FullBackupActivity).use { store ->
                    BackupStatusRules.message(PixelBackupService(this@FullBackupActivity, store).status(JSONObject().put("limit", 1)))
                }
            } catch (_: Exception) {
                "Sicherungsstatus konnte nicht gelesen werden. Es ist keine neue Sicherung bestätigt."
            }
            ui {
                if (status == "Sicherungsstatus wird geprüft …") status = initial
                ready = true
                busy = resolvingDocument
                initialized.complete(Unit)
            }
            try {
                val previous = BackupRestoreService(this@FullBackupActivity).previousPreservation()
                if (previous.optBoolean("available")) {
                    val activation = previous.getString("activation_request_id")
                    ui { previousActivation = activation }
                }
            } catch (_: Exception) {
                // A missing historical proof never blocks a new save.
            }
        }
    }

    /** Called only on IO; a protected or interrupted startup never exposes backup operations. */
    private fun startupReady(): Boolean = try {
        RevierApplication.awaitStartup(this)
        if (RevierApplication.recoveryRequired()) throw IllegalStateException("RESTORE_RESTART_REQUIRED")
        true
    } catch (_: Exception) {
        ui { startActivity(Intent(this, RestoreRecoveryActivity::class.java)); finish() }
        false
    }

    private fun chooseSave() {
        target = ""
        request = UUID.randomUUID().toString()
        val filename = "Haus-Garten-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip").putExtra(Intent.EXTRA_TITLE, filename)
        try {
            startActivityForResult(intent, SAVE)
        } catch (_: ActivityNotFoundException) {
            status = "Die Android-Dateiauswahl ist nicht verfügbar."
        }
    }

    private fun chooseRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(intent, OPEN)
        } catch (_: ActivityNotFoundException) {
            status = "Die Android-Dateiauswahl ist nicht verfügbar."
        }
    }

    private fun runTask(task: () -> Unit) {
        if (busy || !ready) return
        busy = true
        status = "Vorgang läuft …"
        WORK.launch {
            try {
                task()
            } catch (failure: Exception) {
                val safe = BackupScheduleRules.errorCode(failure)
                ui {
                    if (target.isNotEmpty()) showNewTarget = true
                    if (safe == "RECOVERY_CODE_INVALID") {
                        showRecoveryCode = true
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        status = "Diese Sicherung ist verschlüsselt. Den zugehörigen Schlüssel eingeben und erneut prüfen."
                    } else {
                        status = BackupStatusRules.failureMessage(safe)
                    }
                }
            } finally {
                ui { busy = false }
            }
        }
    }

    private fun ui(action: () -> Unit) {
        runOnUiThread { if (!isDestroyed && !isFinishing) action() }
    }

    private fun save() {
        val current = request
        val destination = target
        runTask {
            PixelLocalStore(this).use { store ->
                val result = PixelBackupService(this, store).save(current, Uri.parse(destination)) { message -> ui { status = message } }
                if (!result.optBoolean("verified")) throw IOException("BACKUP_LOCAL_READBACK_FAILED")
                ui {
                    status = "Sicherung gespeichert und vollständig zurückgelesen. Du findest die ZIP-Datei am gewählten Speicherort." +
                        if (result.optInt("warning_count") > 0) {
                            " Hinweise zum Ausgangsbestand: ${result.optInt("warning_count")}. Details: data/backup-notes.json in der ZIP-Datei."
                        } else ""
                    request = ""
                    target = ""
                    showNewTarget = false
                    saveLabel = "Neue Sicherung speichern"
                }
            }
        }
    }

    private fun preview() {
        if (restoreUri.isEmpty()) return
        if (restoreRequest.isEmpty()) restoreRequest = UUID.randomUUID().toString()
        val id = restoreRequest
        val uri = restoreUri
        val code = recoveryCode
        runTask { showPreview(BackupRestoreService(this).preview(id, Uri.parse(uri), code)) }
    }

    private fun clearPreview() {
        restoreRequest = ""
        restoreId = ""
        previewHash = ""
        showActivate = false
    }

    private fun showPreview(result: JSONObject) {
        val summary = result.optJSONObject("summary")
        val incomplete = summary != null && summary.optString("media_coverage") == "LEGACY_NOT_PROVEN"
        ui {
            restoreId = result.optString("restore_id")
            previewHash = result.optString("preview_hash")
            legacyMedia = incomplete
            status = "Sicherungsdatei geprüft. Wiederherstellen ersetzt den aktuellen Bestand. Vorher wird eine lokale Rückfallkopie erstellt." +
                (if (summary != null && summary.optInt("warning_count") > 0) {
                    " Hinweise zum Ausgangsbestand: ${summary.optInt("warning_count")}. Vorhandene Unstimmigkeiten werden mit wiederhergestellt."
                } else "") +
                if (legacyMedia) " Bei dieser alten Sicherung ist die Fotoabdeckung nicht vollständig belegt." else ""
            showActivate = true
        }
    }

    private fun confirmRestore() {
        if (restoreId.isNotEmpty() && previewHash.isNotEmpty()) showConfirmation = true
    }

    private fun activateRestore() {
        val id = restoreId
        val hash = previewHash
        val activationRequest = "activate_$restoreRequest"
        val acceptLegacyMedia = legacyMedia
        if (id.isEmpty() || hash.isEmpty()) return
        runTask {
            BackupRestoreService(this).requestActivation(activationRequest, id, hash, true, acceptLegacyMedia)
            ui { status = "Wiederherstellung vorgemerkt. App vollständig schließen und erneut öffnen. Der Austausch erfolgt beim Neustart." }
        }
    }

    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (result != RESULT_OK || code != SAVE && code != OPEN) return
        val uri = data?.data ?: return
        // Retain the selected identity immediately, including recreation while the provider grants access.
        if (code == SAVE) {
            target = uri.toString()
            saveLabel = "Speichern erneut versuchen"
        } else {
            restoreUri = uri.toString()
            clearPreview()
        }
        // Providers may perform disk work while granting access. Handle the result on IO.
        resolvingDocument = true
        busy = true
        WORK.launch {
            try {
                if (!startupReady()) return@launch
                initialized.await()
                PixelBackupService.requireDocument(uri)
                try {
                    val flags = if (code == SAVE) {
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    } else Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                    // This explicit operation may use its temporary grant.
                }
                ui {
                    resolvingDocument = false
                    busy = false
                    if (code == SAVE) {
                        save()
                    } else {
                        showCheck = true
                        status = "Sicherungsdatei gewählt. Mit „Gewählte Sicherung prüfen“ fortfahren."
                    }
                }
            } catch (_: Exception) {
                ui {
                    resolvingDocument = false
                    busy = false
                    status = "Die gewählte Datei ist nicht zugänglich. Bitte die Android-Dateiauswahl erneut öffnen. Es wurde keine Sicherung geschrieben."
                    request = ""
                    target = ""
                }
            }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("request", request)
        state.putString("target", target)
        state.putString("restore_request", restoreRequest)
        state.putString("restore_uri", restoreUri)
        // Never serialize an old recovery code or a prior preview/confirmation.
    }
}
