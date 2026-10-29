package at.hausgarten.service

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

/** One selected plaintext ZIP save and one explicit restore; no app-managed cloud account. */
class FullBackupActivity : Activity() {
    companion object {
        private const val SAVE = 203
        private const val OPEN = 204
        private val WORK = Executors.newSingleThreadExecutor()
    }

    private lateinit var status: TextView
    private lateinit var saveButton: Button
    private lateinit var openButton: Button
    private lateinit var checkButton: Button
    private lateinit var activate: Button
    private lateinit var newTarget: Button
    private lateinit var recoveryCode: EditText
    private lateinit var root: LinearLayout
    private var busy = false
    private var request = ""
    private var target = ""
    private var restoreRequest = ""
    private var restoreUri = ""
    private var restoreId = ""
    private var previewHash = ""
    private var legacyMedia = false

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (RevierApplication.recoveryRequired()) {
            startActivity(Intent(this, RestoreRecoveryActivity::class.java))
            finish()
            return
        }
        PixelBackupService.cancelRetiredJobs(this)
        title = "Sicherung"
        if (state != null) {
            request = state.getString("request", "")
            target = state.getString("target", "")
            restoreRequest = state.getString("restore_request", "")
            restoreUri = state.getString("restore_uri", "")
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = NativeUi.dp(this@FullBackupActivity, 16)
            setPadding(pad, pad, pad, pad)
            fitsSystemWindows = true
        }
        text("Sicherung", 22)
        text("Speichert deinen vorhandenen Bestand als unverschlüsselte ZIP-Datei. Wähle in der Android-Dateiauswahl Drive oder einen lokalen Speicherort.", 17)
        saveButton = button(if (target.isEmpty()) "Sicherung speichern" else "Speichern erneut versuchen") {
            if (target.isEmpty()) chooseSave() else save()
        }
        newTarget = button("Anderen Speicherort wählen") { target = ""; chooseSave() }
        newTarget.visibility = View.GONE
        status = text("Sicherungsstatus wird geprüft …", 16)
        openButton = button("Sicherung wiederherstellen") {
            recoveryCode.setText("")
            recoveryCode.visibility = View.GONE
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            chooseRestore()
        }
        recoveryCode = EditText(this).apply {
            textSize = 16f
            hint = "Schlüssel – nur für alte verschlüsselte Sicherungen"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
        }
        root.addView(recoveryCode)
        recoveryCode.visibility = View.GONE
        checkButton = button("Gewählte Sicherung prüfen") { preview() }
        checkButton.visibility = if (restoreUri.isEmpty()) View.GONE else View.VISIBLE
        activate = button("Geprüfte Sicherung wiederherstellen") { confirmRestore() }
        activate.visibility = View.GONE
        recoveryCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                restoreRequest = ""
                restoreId = ""
                previewHash = ""
                activate.visibility = View.GONE
            }
            override fun afterTextChanged(e: Editable?) = Unit
        })
        button("Zurück") {
            if (busy) status.text = "Die Sicherung läuft noch. Bitte auf das Ergebnis warten." else finish()
        }
        setContentView(ScrollView(this).apply { addView(root) })
        WORK.execute {
            val initial = try {
                PixelLocalStore(this).use { store ->
                    BackupStatusRules.message(PixelBackupService(this, store).status(JSONObject().put("limit", 1)))
                }
            } catch (_: Exception) {
                "Sicherungsstatus konnte nicht gelesen werden. Es ist keine neue Sicherung bestätigt."
            }
            ui {
                if (!busy && status.text.toString() == "Sicherungsstatus wird geprüft …") status.text = initial
            }
        }
        if (target.isNotEmpty()) {
            status.text = "Der vorherige Speichervorgang ist noch nicht bestätigt. Mit „Speichern erneut versuchen“ prüfen und fortsetzen."
        }
        WORK.execute {
            try {
                val previous = BackupRestoreService(this).previousPreservation()
                if (previous.optBoolean("available")) {
                    val activation = previous.getString("activation_request_id")
                    ui {
                        button("Bestand vor letzter Wiederherstellung zurückholen") {
                            val id = UUID.randomUUID().toString()
                            val code = recoveryCode.text.toString()
                            restoreRequest = id
                            runTask { showPreview(BackupRestoreService(this).previewPrevious(id, activation, code)) }
                        }
                    }
                }
            } catch (_: Exception) {
                // A missing historical proof never blocks a new save.
            }
        }
    }

    private fun text(value: String, size: Int): TextView = NativeUi.text(TextView(this)).also {
        it.textSize = size.toFloat()
        it.text = value
        root.addView(it)
    }

    private fun button(label: String, action: Runnable): Button = NativeUi.style(Button(this), 0).also {
        it.text = label
        it.isAllCaps = false
        it.minHeight = NativeUi.dp(this, 52)
        root.addView(it)
        it.setOnClickListener { if (!busy) action.run() }
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
            status.text = "Die Android-Dateiauswahl ist nicht verfügbar."
        }
    }

    private fun chooseRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(intent, OPEN)
        } catch (_: ActivityNotFoundException) {
            status.text = "Die Android-Dateiauswahl ist nicht verfügbar."
        }
    }

    private fun runTask(task: Task) {
        if (busy) return
        busy = true
        status.text = "Vorgang läuft …"
        recoveryCode.isEnabled = false
        saveButton.isEnabled = false
        openButton.isEnabled = false
        checkButton.isEnabled = false
        activate.isEnabled = false
        WORK.execute {
            try {
                task.run()
            } catch (failure: Exception) {
                val safe = BackupScheduleRules.errorCode(failure)
                ui {
                    if (target.isNotEmpty()) newTarget.visibility = View.VISIBLE
                    if (safe == "RECOVERY_CODE_INVALID") {
                        recoveryCode.visibility = View.VISIBLE
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        status.text = "Diese Sicherung ist verschlüsselt. Den zugehörigen Schlüssel eingeben und erneut prüfen."
                    } else {
                        status.text = BackupStatusRules.failureMessage(safe)
                    }
                }
            } finally {
                ui {
                    busy = false
                    recoveryCode.isEnabled = true
                    saveButton.isEnabled = true
                    openButton.isEnabled = true
                    checkButton.isEnabled = true
                    activate.isEnabled = true
                }
            }
        }
    }

    private fun ui(action: Runnable) {
        runOnUiThread { if (!isDestroyed) action.run() }
    }

    private fun save() {
        val current = request
        val destination = target
        runTask {
            PixelLocalStore(this).use { store ->
                val result = PixelBackupService(this, store).save(current, Uri.parse(destination)) { message ->
                    ui { status.text = message }
                }
                if (!result.optBoolean("verified")) throw IOException("BACKUP_LOCAL_READBACK_FAILED")
                ui {
                    status.text = "Sicherung gespeichert und vollständig zurückgelesen. Du findest die ZIP-Datei am gewählten Speicherort." +
                        if (result.optInt("warning_count") > 0) {
                            " Hinweise zum Ausgangsbestand: ${result.optInt("warning_count")}. Details: data/backup-notes.json in der ZIP-Datei."
                        } else ""
                    request = ""
                    target = ""
                    newTarget.visibility = View.GONE
                    saveButton.text = "Neue Sicherung speichern"
                }
            }
        }
    }

    private fun preview() {
        if (restoreUri.isEmpty()) return
        if (restoreRequest.isEmpty()) restoreRequest = UUID.randomUUID().toString()
        val id = restoreRequest
        val uri = restoreUri
        val code = recoveryCode.text.toString()
        runTask { showPreview(BackupRestoreService(this).preview(id, Uri.parse(uri), code)) }
    }

    @Throws(Exception::class)
    private fun showPreview(result: JSONObject) {
        val summary = result.optJSONObject("summary")
        val incomplete = summary != null && summary.optString("media_coverage") == "LEGACY_NOT_PROVEN"
        ui {
            restoreId = result.optString("restore_id")
            previewHash = result.optString("preview_hash")
            legacyMedia = incomplete
            status.text = "Sicherungsdatei geprüft. Wiederherstellen ersetzt den aktuellen Bestand. Vorher wird eine lokale Rückfallkopie erstellt." +
                (if (summary != null && summary.optInt("warning_count") > 0) {
                    " Hinweise zum Ausgangsbestand: ${summary.optInt("warning_count")}. Vorhandene Unstimmigkeiten werden mit wiederhergestellt."
                } else "") +
                if (legacyMedia) " Bei dieser alten Sicherung ist die Fotoabdeckung nicht vollständig belegt." else ""
            activate.visibility = View.VISIBLE
        }
    }

    private fun confirmRestore() {
        if (restoreId.isEmpty() || previewHash.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Bestand wiederherstellen?")
            .setMessage("Der aktuelle Bestand wird durch die geprüfte Sicherung ersetzt. Eine lokale Rückfallkopie bleibt erhalten." +
                if (legacyMedia) " Die unvollständig belegte Fotoabdeckung dieser alten Sicherung wird ausdrücklich akzeptiert." else "")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Wiederherstellen") { _, _ ->
                val id = restoreId
                val hash = previewHash
                runTask {
                    BackupRestoreService(this).requestActivation("activate_$restoreRequest", id, hash, true, legacyMedia)
                    ui {
                        status.text = "Wiederherstellung vorgemerkt. App vollständig schließen und erneut öffnen. Der Austausch erfolgt beim Neustart."
                    }
                }
            }.show()
    }

    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (result != RESULT_OK || code != SAVE && code != OPEN) return
        val uri = data?.data ?: return
        try {
            PixelBackupService.requireDocument(uri)
            try {
                val flags = if (code == SAVE) {
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                } else Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // This explicit operation may use its temporary grant.
            }
            if (code == SAVE) {
                target = uri.toString()
                saveButton.text = "Speichern erneut versuchen"
                save()
            } else {
                restoreUri = uri.toString()
                restoreRequest = ""
                restoreId = ""
                previewHash = ""
                activate.visibility = View.GONE
                checkButton.visibility = View.VISIBLE
                status.text = "Sicherungsdatei gewählt. Mit „Gewählte Sicherung prüfen“ fortfahren."
            }
        } catch (_: Exception) {
            status.text = "Die gewählte Datei ist nicht zugänglich. Bitte die Android-Dateiauswahl erneut öffnen. Es wurde keine Sicherung geschrieben."
            request = ""
            target = ""
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("request", request)
        state.putString("target", target)
        state.putString("restore_request", restoreRequest)
        state.putString("restore_uri", restoreUri)
        // Never serialize an old recovery code.
    }

    fun interface Task {
        @Throws(Exception::class)
        fun run()
    }
}
