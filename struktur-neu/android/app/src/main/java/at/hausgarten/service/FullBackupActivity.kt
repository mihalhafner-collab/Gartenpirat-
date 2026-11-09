package at.hausgarten.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One selected local ZIP save and one explicit restore; no app-managed cloud account. */
class FullBackupActivity : ComponentActivity() {
    companion object {
        private const val SAVE = 203
        private const val OPEN = 204
        // A selected operation finishes across Activity recreation; its identity is retained.
        private val WORK = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private var status by mutableStateOf("Sicherungsstatus wird geprüft …")
    private var statusLabel by mutableStateOf("Status wird geprüft")
    private var statusTone by mutableStateOf(NativeStatusTone.Running)
    private var busy by mutableStateOf(true)
    private var ready by mutableStateOf(false)
    private var showNewTarget by mutableStateOf(false)
    private var showCheck by mutableStateOf(false)
    private var showActivate by mutableStateOf(false)
    private var showConfirmation by mutableStateOf(false)
    private var showWarnings by mutableStateOf(false)
    private var saveFinished by mutableStateOf(false)
    private var restorePending by mutableStateOf(false)
    private var selectedName by mutableStateOf("")
    private var lastVerified by mutableStateOf("")
    private var warningCount by mutableIntStateOf(0)
    private val saveDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            documentChosen(SAVE, it.resultCode, it.data)
        }
    private val openDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            documentChosen(OPEN, it.resultCode, it.data)
        }
    private var warnings by mutableStateOf(JSONArray())
    private var backupCounts by mutableStateOf<JSONObject?>(null)
    private var currentCounts by mutableStateOf<JSONObject?>(null)
    private var capturedAt by mutableStateOf("")
    private var request = ""
    private var target = ""
    private var restoreRequest = ""
    private var restoreUri = ""
    private var restoreId = ""
    private var previewHash = ""
    private val initialized = CompletableDeferred<Unit>()
    private var resolvingDocument = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Sicherung"
        if (savedInstanceState != null) {
            request = savedInstanceState.getString("request", "")
            target = savedInstanceState.getString("target", "")
            restoreRequest = savedInstanceState.getString("restore_request", "")
            restoreUri = savedInstanceState.getString("restore_uri", "")
            restoreId = savedInstanceState.getString("restore_id", "")
            selectedName = savedInstanceState.getString("selected_name", "")
        }
        showCheck = restoreUri.isNotEmpty()
        if (target.isNotEmpty()) {
            statusLabel = "Speichern nicht bestätigt"
            statusTone = NativeStatusTone.Attention
            status =
                "Der vorherige Speichervorgang ist noch nicht bestätigt. „Sicherung fortsetzen“ liest eine bereits bestätigte Datei zurück. Ohne passenden Speicherbeleg wird die ZIP am gewählten Speicherort erneut geschrieben und vollständig zurückgelesen."
        }
        nativeContent(
            "Sicherung",
            actions = {
                NativeActionBar(
                    primaryLabel =
                        when {
                            showWarnings -> "Zur Sicherung"
                            restorePending || saveFinished -> "Fertig"
                            showActivate -> "Wiederherstellen"
                            showCheck -> "Datei prüfen"
                            target.isNotEmpty() -> "Sicherung fortsetzen"
                            else -> "Jetzt sichern"
                        },
                    onPrimary = {
                        when {
                            showWarnings -> showWarnings = false
                            restorePending || saveFinished -> finish()
                            showActivate -> confirmRestore()
                            showCheck -> preview()
                            target.isNotEmpty() -> save()
                            else -> chooseSave()
                        }
                    },
                    // Restore stays reachable without scrolling; leaving uses the top back arrow or
                    // gesture.
                    secondaryLabel =
                        if (showWarnings || restorePending) null
                        else if (showCheck) "Abbrechen" else "Sicherung wiederherstellen",
                    onSecondary = { if (showCheck) leaveScreen() else chooseRestore() },
                    enabled = ready && !busy,
                    secondaryEnabled = !busy && (showCheck || ready),
                )
            },
        ) {
            BackHandler(enabled = busy || showWarnings || showCheck) {
                if (busy) status = "Der Vorgang läuft noch. Bitte auf das Ergebnis warten."
                else leaveScreen()
            }
            NativeStatus(statusLabel, if (busy) NativeStatusTone.Running else statusTone)
            Text(status)
            if (showWarnings) {

                val grouped = LinkedHashMap<String, Int>()
                for (i in 0 until warnings.length()) {
                    val message =
                        warnings.optJSONObject(i)?.optString("message").orEmpty().ifBlank {
                            "Inhalt nicht vollständig bestätigt."
                        }
                    grouped[message] = (grouped[message] ?: 0) + 1
                }
                if (grouped.isEmpty())
                    Text(
                        "$warningCount Hinweise zum Ausgangsbestand. Details stehen in der ZIP-Datei unter data/backup-notes.json."
                    )
                else
                    grouped.forEach { (message, count) ->
                        NativeInfoRow(if (count == 1) "1 Hinweis" else "$count Hinweise", message)
                    }
            } else {
                if (showCheck || saveFinished) {
                    if (selectedName.isNotEmpty()) NativeInfoRow("Gewählte Datei", selectedName)
                    if (capturedAt.isNotEmpty())
                        NativeInfoRow("Datenstand in der Datei", displayTime(capturedAt))
                    if (backupCounts != null)
                        NativeInfoRow("Bestand in der Datei", inventory(backupCounts))
                    if (showActivate) {
                        NativeInfoRow(
                            "Aktueller Bestand zum Prüfzeitpunkt",
                            inventory(currentCounts),
                        )
                    }
                }
                if (warningCount > 0)
                    NativeOutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showWarnings = true },
                    ) {
                        Text("Hinweise ansehen ($warningCount)")
                    }
                if (lastVerified.isNotEmpty())
                    NativeInfoRow(
                        "Letzte erfolgreiche Sicherung",
                        "${displayTime(lastVerified)} · Datei vollständig zurückgelesen",
                    )
                if (!restorePending) {
                    if (showNewTarget)
                        NativeOutlinedButton(
                            enabled = ready && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { chooseSave() },
                        ) {
                            Text("Anderen Speicherort wählen")
                        }
                    if (showCheck)
                        NativeOutlinedButton(
                            enabled = ready && !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { chooseRestore() },
                        ) {
                            Text("Andere Sicherungsdatei wählen")
                        }
                }
            }
            if (showConfirmation)
                AlertDialog(
                    onDismissRequest = { showConfirmation = false },
                    title = { Text("Bestand ersetzen?") },
                    text = {
                        Text(
                            "Die Sicherung vom ${displayTime(capturedAt)} ersetzt den aktuellen Datenstand.\n\nDatei: ${inventory(backupCounts)}\nAktuell zum Prüfzeitpunkt: ${inventory(currentCounts)}\n\nEs wird keine Rückfallkopie angelegt."
                        )
                    },
                    dismissButton = {
                        NativeTextButton(onClick = { showConfirmation = false }) {
                            Text("Abbrechen")
                        }
                    },
                    confirmButton = {
                        NativeTextButton(
                            onClick = {
                                showConfirmation = false
                                activateRestore()
                            }
                        ) {
                            Text("Bestand ersetzen")
                        }
                    },
                )
        }
        WORK.launch {
            if (!startupReady()) return@launch
            try {
                PixelBackupService.cancelRetiredJobs(this@FullBackupActivity)
                val initial =
                    PixelLocalStore(this@FullBackupActivity).use { store ->
                        PixelBackupService(this@FullBackupActivity, store)
                            .status(JSONObject().put("limit", 1))
                    }
                ui {
                    lastVerified = initial.optString("last_local_verified_at")
                    if (status == "Sicherungsstatus wird geprüft …") {
                        status = BackupStatusRules.message(initial)
                        val latest = initial.optJSONObject("latest_job")
                        warningCount = latest?.optInt("warning_count") ?: 0
                        warnings = latest?.optJSONArray("warnings") ?: JSONArray()
                        statusLabel =
                            when (initial.optString("state")) {
                                "FAILED" -> "Sicherung nicht bestätigt"
                                "VERIFIED" ->
                                    if (warningCount > 0)
                                        "Gespeichert · ${BackupInventory.warningSummary(warnings, warningCount)}"
                                    else "Gespeichert und geprüft"
                                "RUNNING" -> "Sicherung läuft"
                                else -> "Noch keine Sicherung bestätigt"
                            }
                        statusTone =
                            when (initial.optString("state")) {
                                "FAILED" -> NativeStatusTone.Failed
                                "VERIFIED" ->
                                    if (warningCount > 0) NativeStatusTone.Attention
                                    else NativeStatusTone.Confirmed
                                "RUNNING" -> NativeStatusTone.Running
                                else -> NativeStatusTone.Neutral
                            }
                    }
                }
            } catch (_: Exception) {
                ui {
                    status =
                        "Sicherungsstatus konnte nicht gelesen werden. Es ist keine neue Sicherung bestätigt."
                    statusLabel = "Prüfung erforderlich"
                    statusTone = NativeStatusTone.Attention
                }
            }
            ui {
                ready = true
                busy = resolvingDocument
                initialized.complete(Unit)
            }
            try {
                val pending = BackupRestoreService.pendingStatus(this@FullBackupActivity)
                if (pending.optBoolean("restart_required"))
                    ui {
                        restorePending = true
                        statusLabel = "Wiederherstellung vorgemerkt"
                        statusTone = NativeStatusTone.Attention
                        status =
                            "Der Bestand ist noch nicht ersetzt. App vollständig schließen und erneut öffnen; der geschützte Austausch erfolgt beim Neustart."
                    }
            } catch (_: Exception) {
                /* Historical proof never blocks a new save. */
            }
        }
    }

    private fun leaveScreen() {
        when {
            showWarnings -> showWarnings = false
            showCheck && !restorePending -> {
                clearPreview()
                restoreUri = ""
                selectedName = ""
                showCheck = false
                status = "Dateiauswahl beendet. Der aktuelle Bestand wurde nicht ersetzt."
                statusLabel = "Bestand unverändert"
                statusTone = NativeStatusTone.Neutral
            }
            else -> finish()
        }
    }

    private fun startupReady(): Boolean =
        try {
            RevierApplication.awaitStartup(this)
            if (RevierApplication.recoveryRequired())
                throw IllegalStateException("RESTORE_RESTART_REQUIRED")
            true
        } catch (_: Exception) {
            ui {
                startActivity(Intent(this, RestoreRecoveryActivity::class.java))
                finish()
            }
            false
        }

    private fun chooseSave() {
        clearPreview()
        restoreUri = ""
        showCheck = false
        target = ""
        saveFinished = false
        request = UUID.randomUUID().toString()
        val filename =
            "Haus-Garten-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) +
                ".zip"
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, filename)
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        try {
            saveDocument.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pickerUnavailable()
        }
    }

    private fun chooseRestore() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        try {
            openDocument.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pickerUnavailable()
        }
    }

    private fun pickerUnavailable() {
        status = "Die Android-Dateiauswahl ist nicht verfügbar."
        statusLabel = "Dateiauswahl fehlgeschlagen"
        statusTone = NativeStatusTone.Failed
    }

    private fun runTask(task: () -> Unit) {
        if (busy || !ready) return
        busy = true
        statusLabel = "Vorgang läuft"
        status = "Bitte auf das Ergebnis warten."
        WORK.launch {
            try {
                task()
            } catch (failure: Exception) {
                val safe = BackupScheduleRules.errorCode(failure)
                ui {
                    if (target.isNotEmpty()) showNewTarget = true
                    statusLabel =
                        when {
                            safe == "BACKUP_RUNNER_BUSY" -> "Sicherung läuft"
                            target.isNotEmpty() -> "Speichern nicht bestätigt"
                            else -> "Prüfung fehlgeschlagen"
                        }
                    statusTone =
                        when (safe) {
                            "BACKUP_RUNNER_BUSY" -> NativeStatusTone.Running
                            "BACKUP_LOCAL_READBACK_FAILED" -> NativeStatusTone.Attention
                            else -> NativeStatusTone.Failed
                        }
                    status = BackupStatusRules.failureMessage(safe)
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
                val result =
                    PixelBackupService(this, store).save(current, Uri.parse(destination)) { message
                        ->
                        ui { status = message }
                    }
                if (!result.optBoolean("verified"))
                    throw IOException("BACKUP_LOCAL_READBACK_FAILED")
                ui {
                    warnings = result.optJSONArray("warnings") ?: JSONArray()
                    warningCount = maxOf(result.optInt("warning_count"), warnings.length())
                    backupCounts = result.optJSONObject("counts")
                    capturedAt = result.optString("captured_at")
                    lastVerified = result.optString("verified_at")
                    statusLabel =
                        if (warningCount > 0)
                            "Gespeichert · ${BackupInventory.warningSummary(warnings, warningCount)}"
                        else "Gespeichert und geprüft"
                    statusTone =
                        if (warningCount > 0) NativeStatusTone.Attention
                        else NativeStatusTone.Confirmed
                    status =
                        if (warningCount > 0)
                            "ZIP vollständig zurückgelesen; der Ausgangsbestand enthält Einschränkungen. Hinweise vor einer Wiederherstellung ansehen."
                        else
                            "ZIP vollständig zurückgelesen · keine Hinweise. Die Datei liegt am gewählten Speicherort."
                    request = ""
                    target = ""
                    showNewTarget = false
                    saveFinished = true
                }
            }
        }
    }

    private fun preview() {
        if (restoreUri.isEmpty()) return
        if (restoreRequest.isEmpty()) restoreRequest = UUID.randomUUID().toString()
        val id = restoreRequest
        val uri = restoreUri
        runTask { showPreview(BackupRestoreService(this).preview(id, Uri.parse(uri))) }
    }

    private fun clearPreview() {
        val discarded = restoreId
        if (discarded.isNotEmpty())
            WORK.launch {
                runCatching {
                    BackupRestoreService.discardPreview(this@FullBackupActivity, discarded)
                }
            }
        restoreRequest = ""
        restoreId = ""
        previewHash = ""
        showActivate = false
        showWarnings = false
        showConfirmation = false
        backupCounts = null
        currentCounts = null
        capturedAt = ""
        warnings = JSONArray()
        warningCount = 0
    }

    private fun showPreview(result: JSONObject) {
        val summary = result.getJSONObject("summary")
        if (isDestroyed || isFinishing) {
            BackupRestoreService.discardPreview(this, result.optString("restore_id"))
            return
        }
        ui {
            restoreId = result.optString("restore_id")
            previewHash = result.optString("preview_hash")
            capturedAt = summary.optString("captured_at")
            backupCounts = summary.optJSONObject("counts")
            currentCounts = result.optJSONObject("current_counts")
            warnings = summary.optJSONArray("warnings") ?: JSONArray()
            warningCount = maxOf(summary.optInt("warning_count"), warnings.length())
            statusLabel =
                if (warningCount > 0)
                    "Datei geprüft · ${BackupInventory.warningSummary(warnings, warningCount)}"
                else "Datei geprüft"
            statusTone =
                if (warningCount > 0) NativeStatusTone.Attention else NativeStatusTone.Neutral
            status = "Vorschau bereit. Der aktuelle Bestand ist unverändert."
            showCheck = true
            showActivate = true
            saveFinished = false
        }
    }

    private fun confirmRestore() {
        if (restoreId.isNotEmpty() && previewHash.isNotEmpty()) showConfirmation = true
    }

    private fun activateRestore() {
        val id = restoreId
        val hash = previewHash
        val activationRequest = "activate_$restoreRequest"
        if (id.isEmpty() || hash.isEmpty()) return
        runTask {
            BackupRestoreService(this).requestActivation(activationRequest, id, hash, true)
            ui {
                restorePending = true
                statusLabel = "Wiederherstellung vorgemerkt"
                statusTone = NativeStatusTone.Attention
                status =
                    "Der Bestand ist noch nicht ersetzt. App vollständig schließen und erneut öffnen; der geschützte Austausch erfolgt beim Neustart."
            }
        }
    }

    private fun documentChosen(code: Int, result: Int, data: Intent?) {
        if (result != RESULT_OK) return
        val uri = data?.data ?: return
        if (code == SAVE) target = uri.toString()
        else {
            restoreUri = uri.toString()
            clearPreview()
            saveFinished = false
            target = ""
        }
        resolvingDocument = true
        busy = true
        WORK.launch {
            try {
                if (!startupReady()) return@launch
                initialized.await()
                PixelBackupService.requireDocument(uri)
                try {
                    val flags =
                        if (code == SAVE)
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        else Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                    /* This explicit operation may use its temporary grant. */
                }
                val name =
                    try {
                        contentResolver
                            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                            .orEmpty()
                    } catch (_: Exception) {
                        "Gewählte ZIP-Datei"
                    }
                ui {
                    selectedName = name
                    resolvingDocument = false
                    busy = false
                    if (code == SAVE) save()
                    else {
                        showCheck = true
                        statusLabel = "Datei gewählt"
                        statusTone = NativeStatusTone.Neutral
                        status =
                            "Zuerst die Datei prüfen und den Bestand vergleichen. Der aktuelle Bestand bleibt bis zur ausdrücklichen Bestätigung unverändert."
                    }
                }
            } catch (_: Exception) {
                ui {
                    resolvingDocument = false
                    busy = false
                    statusLabel = "Dateizugriff fehlgeschlagen"
                    statusTone = NativeStatusTone.Failed
                    status =
                        "Die gewählte Datei ist nicht zugänglich. Bitte die Android-Dateiauswahl erneut öffnen. Es wurde kein neuer Sicherungserfolg bestätigt."
                    request = ""
                    target = ""
                }
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations && restoreId.isNotEmpty()) {
            val discarded = restoreId
            WORK.launch {
                runCatching {
                    BackupRestoreService.discardPreview(this@FullBackupActivity, discarded)
                }
            }
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("request", request)
        outState.putString("target", target)
        outState.putString("restore_request", restoreRequest)
        outState.putString("restore_uri", restoreUri)
        outState.putString("restore_id", restoreId)
        outState.putString("selected_name", selectedName)
        // A preview and its replacement confirmation must be checked again after recreation.
    }

    private fun inventory(counts: JSONObject?): String =
        if (counts == null || !counts.has("customers") || !counts.has("worklogs"))
            "Bestandszahlen nicht vollständig ermittelbar"
        else
            "${counts.optInt("customers")} Kundendatensätze · ${counts.optInt("worklogs")} Arbeitsaufzeichnungen"

    private fun displayTime(value: String): String =
        try {
            DateTimeFormatter.ofPattern("d. MMMM yyyy, HH:mm", java.util.Locale.GERMAN)
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(value))
        } catch (_: Exception) {
            "Zeitpunkt nicht bestätigt"
        }
}
