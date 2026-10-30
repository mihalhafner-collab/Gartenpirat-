package at.hausgarten.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Opening only reads. Explicit decisions use the same owner use cases as MCP. */
class LocalAuthorityActivity : ComponentActivity() {
    private var status by mutableStateOf("Bestand wird ausschließlich lokal geprüft …")
    private var inventory by mutableStateOf("")
    private var evidenceText by mutableStateOf("Noch kein externer Nachweis gewählt.")
    private var selection by mutableStateOf("Keine Sicherung ausdrücklich gewählt.")
    private var originals by
        mutableStateOf(
            "Originale werden ausschließlich anhand der PDF-Prüfsummen aus dem ausgewählten Nachweis zugeordnet."
        )
    private var writers by mutableStateOf(false)
    private var currentLedger by mutableStateOf(false)
    private var notCreatedReview by mutableStateOf(false)
    private var preview by mutableStateOf<JSONObject?>(null)
    private var evidence by mutableStateOf<LocalAuthorityEvidence.Loaded?>(null)
    private var evidenceUri = ""
    private var evidenceHash = ""
    private var selectedBackup by mutableStateOf("")
    private var pendingName = ""
    private var pendingBody by mutableStateOf("")
    private var importRequest = ""
    private var busy by mutableStateOf(false)
    private var backupOffset by mutableStateOf(0)
    private var backupTotal by mutableStateOf(0)
    private val backupRows = mutableStateListOf<JSONObject>()
    private var backupEmpty by mutableStateOf(false)

    private data class Dialog(
        val title: String,
        val body: String,
        val cancel: String?,
        val confirm: String,
        val action: () -> Unit,
    )

    private var dialog by mutableStateOf<Dialog?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RevierApplication.recoveryRequired()) {
            startActivity(Intent(this, RestoreRecoveryActivity::class.java))
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        title = "Lokalen Betrieb prüfen"
        if (savedInstanceState != null) {
            evidenceUri = savedInstanceState.getString("evidence_uri", "")
            evidenceHash = savedInstanceState.getString("evidence_hash", "")
            selectedBackup = savedInstanceState.getString("backup_id", "")
            importRequest = savedInstanceState.getString("import_request", "")
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    leave()
                }
            },
        )
        nativeContent("Lokalen Betrieb prüfen und umstellen") {
            Text(
                "Die Umstellung macht den Pixel zum maßgeblichen Arbeitsbestand. Sicherungen werden lokal auf dem Pixel gespeichert. Bestehende Konten bleiben erhalten. Erst die ausdrücklich bestätigte Umstellung hält alte Schreibwege an; Rechnungen bleiben bis zum separaten aktuellen Belegabgleich gesperrt."
            )
            Text("1 · Bestand lesen", style = MaterialTheme.typography.titleLarge)
            Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(inventory)
            TextButton(onClick = { refresh(true) }, enabled = !busy) { Text("Bestand neu prüfen") }
            HorizontalDivider()
            Text(
                "2 · Sicherung und alte Schreiber prüfen",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Vor der Umstellung den aktuellen Stand vollständig extern sichern. Eine alte Sicherung oder nur das Rücklesen eines Cloud-Dateianbieters genügt nicht. Offene Konflikte und fehlende Originale müssen geklärt bleiben."
            )
            Text(selection)
            for (job in backupRows) {
                val id = job.optString("backup_id")
                val historical =
                    job.optString("phase") == "REMOTE_VERIFIED" &&
                        job.optString("remote_verified_at").isNotEmpty()
                TextButton(
                    onClick = {
                        selectedBackup = id
                        clearChecks()
                        selection =
                            "Gewählte Generation: $id\nDatenstand: ${time(job.optString("captured_at"))}. Der Geschäftsstand wird beim Ausführen erneut verglichen."
                    },
                    enabled = !busy,
                ) {
                    Text(
                        (if (id == selectedBackup) "Gewählt · " else "Wählen · ") +
                            (if (historical) "Historischer externer Nachweis"
                            else "Entfernbares Medium zurückgelesen") +
                            "\nDatenstand: ${time(job.optString("captured_at"))}\nGeneration: $id"
                    )
                }
            }
            if (backupEmpty)
                Text(
                    "Auf dieser Seite liegt keine aktuelle extern geprüfte Generation vor. Vollsicherung durchführen oder weitere Generationen lesen."
                )
            if (backupOffset < backupTotal)
                TextButton(onClick = ::loadMore, enabled = !busy) {
                    Text("Weitere Sicherungen lesen")
                }
            TextButton(
                onClick = {
                    startActivity(
                        Intent(this@LocalAuthorityActivity, FullBackupActivity::class.java)
                    )
                },
                enabled = !busy,
            ) {
                Text("Zur Vollsicherung")
            }
            Text(evidenceText)
            TextButton(onClick = ::chooseEvidence, enabled = !busy) {
                Text("Externen JSON-Nachweis auswählen")
            }
            TextButton(onClick = ::showFormat, enabled = !busy) {
                Text("Erforderliche Nachweisfelder anzeigen")
            }
            Row(Modifier.fillMaxWidth()) {
                Checkbox(
                    writers,
                    { writers = it },
                    enabled = !busy && evidence != null && pendingBody.isEmpty(),
                )
                Text(
                    "Ich habe die externen Nachweise jetzt geprüft: Alte Geräte, Clients und Schreibjobs sind zuverlässig stillgelegt. Es gibt keinen zweiten aktiven Schreiber oder Rechnungsaussteller."
                )
            }
            val mode = preview?.optJSONObject("authority")?.optString("mode") ?: "UNKNOWN"
            val ready =
                !busy && preview != null && evidence != null && pendingBody.isEmpty() && writers
            Button(
                onClick = ::confirmActivate,
                enabled =
                    ready &&
                        selectedBackup.isNotEmpty() &&
                        mode in listOf("LEGACY_DRIVE", "RESTORE_REVIEW"),
            ) {
                Text("Lokalen Arbeitsbestand umstellen")
            }
            HorizontalDivider()
            Text(
                "3 · Aktuelle Rechnungsoriginale und Nummern abgleichen",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Auch nach einer Wiederherstellung beweist der Backupzähler keinen aktuellen Nummernstand. Der externe Nachweis muss alle seither ausgestellten Belege, Reservierungen, Stornos und den belegten letzten Zähler enthalten. Fehlender Nachweis lässt die Rechnungsausgabe gesperrt."
            )
            Text(originals)
            TextButton(onClick = ::chooseOriginals, enabled = !busy) {
                Text("Passende PDF-Originale aus Ordner einlesen")
            }
            Row {
                Checkbox(
                    currentLedger,
                    { currentLedger = it },
                    enabled = !busy && evidence != null && pendingBody.isEmpty(),
                )
                Text(
                    "Ich habe die ausgewählte Beleg- und Reservierungsliste jetzt erneut mit den aktuellen externen Originalen geprüft, auch für alle Belege seit dieser Sicherung oder Wiederherstellung. Sie enthält den vollständigen heutigen Nummernstand; etwaige Restbeträge sind ausdrücklich belegt."
                )
            }
            if (notCreatedCount() > 0)
                Row {
                    Checkbox(
                        notCreatedReview,
                        { notCreatedReview = it },
                        enabled = !busy && evidence != null && pendingBody.isEmpty(),
                    )
                    Text(
                        "Für jeden ausdrücklich als nie erstellt markierten PDF-Job habe ich den aktuellen Original- und Quittungsbestand geprüft. Es wurde weder früher ein PDF erzeugt noch geteilt. Der Backupzähler beweist dies nicht; fehlende oder unbekannte Originale dürfen so nicht ersetzt werden."
                    )
                }
            Button(
                onClick = ::confirmReconcile,
                enabled =
                    ready &&
                        currentLedger &&
                        notCreatedConfirmed() &&
                        mode in listOf("LOCAL_ACTIVE", "RESTORE_REVIEW"),
            ) {
                Text("Geprüften Beleg- und Nummernstand übernehmen")
            }
            if (pendingBody.isNotEmpty()) {
                TextButton(onClick = ::submitPending, enabled = !busy) {
                    Text("Angeforderten Schritt erneut prüfen")
                }
                TextButton(onClick = ::reviewPending, enabled = !busy) {
                    Text("Angeforderten Schritt mit aktuellem Bestand vergleichen")
                }
            }
            TextButton(onClick = ::leave) { Text("Zurück zur App") }
            dialog?.let { value ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(value.title) },
                    text = { Text(value.body) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                dialog = null
                                value.action()
                            }
                        ) {
                            Text(value.confirm)
                        }
                    },
                    dismissButton =
                        if (value.cancel != null)
                            ({ TextButton(onClick = { dialog = null }) { Text(value.cancel) } })
                        else null,
                )
            }
        }
        refresh(false)
    }

    private fun clearChecks() {
        writers = false
        currentLedger = false
        notCreatedReview = false
    }

    private fun confirm(
        title: String,
        message: String,
        cancel: String?,
        positive: String,
        action: () -> Unit = {},
    ) {
        dialog = Dialog(title, message, cancel, positive, action)
    }

    private fun run(task: () -> Unit) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { task() }
            } catch (error: Exception) {
                if (!isDestroyed) showError(safeCode(error))
            } finally {
                if (!isDestroyed) busy = false
            }
        }
    }

    private fun ui(action: () -> Unit) {
        runOnUiThread { if (!isDestroyed) action() }
    }

    private fun refresh(explicit: Boolean) {
        if (busy) return
        val previousBody = pendingBody
        val previousEvidence = evidence
        val previousUri = evidenceUri
        val previousHash = evidenceHash
        run {
            PixelLocalStore(this).use { store ->
                val migration = LocalAuthorityMigration(this, store)
                val current = migration.preview()
                val backups =
                    migration.historicalBackups(JSONObject().put("offset", 0).put("limit", 20))
                val durable =
                    if (previousBody.isEmpty()) LocalAuthorityEvidence.loadPending(this)
                    else JSONObject()
                val chosenUri = durable.optString("evidence_uri", previousUri)
                val chosenHash = durable.optString("evidence_hash", previousHash)
                var restored: LocalAuthorityEvidence.Loaded? = null
                var readError = ""
                if (previousEvidence == null && chosenUri.isNotEmpty())
                    try {
                        restored = LocalAuthorityEvidence.read(this, Uri.parse(chosenUri))
                        if (chosenHash.isNotEmpty() && chosenHash != restored.sha256) {
                            restored = null
                            readError = "LOCAL_EVIDENCE_SOURCE_CHANGED"
                        }
                    } catch (error: Exception) {
                        readError = safeCode(error)
                    }
                val loaded = restored
                val error = readError
                ui {
                    if (durable.optString("pending_body").isNotEmpty()) {
                        pendingName = durable.optString("pending_name")
                        pendingBody = durable.optString("pending_body")
                        evidenceUri = chosenUri
                        evidenceHash = chosenHash
                        selectedBackup = durable.optString("backup_id", selectedBackup)
                    }
                    val changed =
                        preview != null &&
                            preview!!.optString("preview_hash") != current.optString("preview_hash")
                    preview = current
                    if (changed || explicit) clearChecks()
                    if (loaded != null) {
                        evidence = loaded
                        evidenceHash = loaded.sha256
                        renderEvidence()
                    }
                    renderPreview()
                    backupRows.clear()
                    backupOffset = 0
                    backupTotal = backups.optInt("total")
                    renderBackups(backups.optJSONArray("jobs"))
                    if (durable.optBoolean("stale_context"))
                        status +=
                            "\nEine frühere Bildschirmaktion gehört zu einem älteren Wiederherstellungsstand und wird nicht erneut ausgeführt."
                    if (error.isNotEmpty()) showError(error)
                }
            }
        }
    }

    private fun renderPreview() {
        val current = preview ?: return
        val authority = current.optJSONObject("authority")
        val data = current.optJSONObject("inventory")
        val counts = data?.optJSONObject("counts")
        val mode = authority?.optString("mode") ?: "UNKNOWN"
        val invoice = authority?.optString("invoice_authority") ?: "LOCKED"
        status =
            "Arbeitsbestand: ${modeLabel(mode)}\nRechnungsausgabe: " +
                if (invoice == "RECONCILED")
                    "Nummernstand abgeglichen; fachliche Ausgabeprüfungen bleiben aktiv"
                else "gesperrt bis zum aktuellen Original- und Nummernabgleich"
        if (data == null) {
            inventory = "Inventar noch nicht vollständig lesbar. Keine Umstellung möglich."
            return
        }
        val body = StringBuilder()
        for ((key, label) in
            listOf(
                "customers" to "Kunden",
                "orders" to "Aufträge",
                "worklogs" to "Arbeitsaufzeichnungen",
                "photos" to "Fotos",
                "offers" to "Angebote",
                "invoices" to "Rechnungen",
                "appointments" to "Termine",
            )) body.append(label).append(": ").append(counts?.optInt(key) ?: 0).append('\n')
        body
            .append("Doppelte IDs: ")
            .append(data.optInt("duplicate_ids"))
            .append(" · Ungeklärte Beziehungen: ")
            .append(data.optInt("orphan_relations"))
            .append("\nFehlende Originale: ")
            .append(data.optInt("missing_local_originals"))
            .append(" · PDF-Aufträge ausstehend: ")
            .append(data.optInt("pending_document_jobs"))
            .append("\nOffene Aktionen: ")
            .append(data.optInt("pending_actions"))
            .append(" · Historische Prüfhinweise: ")
            .append(data.optInt("historical_reviews"))
        if (current.optBoolean("drive_writer_running"))
            body.append(
                "\nEin bisheriger Abgleich läuft noch. Sein Abschluss muss vor einer Umstellung geprüft werden."
            )
        inventory = body.toString()
        selection =
            if (selectedBackup.isEmpty()) "Keine Sicherung ausdrücklich gewählt."
            else
                "Gewählte Generation: $selectedBackup\nDer aktuelle Geschäftsstand wird bei der Umstellung erneut mit dieser Sicherung verglichen."
    }

    private fun loadMore() {
        val offset = backupOffset
        run {
            PixelLocalStore(this).use { store ->
                val result =
                    LocalAuthorityMigration(this, store)
                        .historicalBackups(JSONObject().put("offset", offset).put("limit", 20))
                ui {
                    backupTotal = result.optInt("total")
                    renderBackups(result.optJSONArray("jobs"))
                }
            }
        }
    }

    private fun renderBackups(jobs: JSONArray?) {
        var accepted = 0
        if (jobs != null)
            for (i in 0 until jobs.length()) {
                val job = jobs.optJSONObject(i) ?: continue
                if (
                    !job.optBoolean("current_context") ||
                        job.optString("media_coverage") != "VERIFIED_NATIVE_MIRROR"
                )
                    continue
                val historical =
                    job.optString("phase") == "REMOTE_VERIFIED" &&
                        job.optString("remote_verified_at").isNotEmpty()
                val removable =
                    job.optBoolean("external_storage_verified") &&
                        job.optString("provider_verified_at").isNotEmpty()
                if (!historical && !removable) continue
                accepted++
                backupRows.add(job)
            }
        if (backupOffset == 0) backupEmpty = accepted == 0
        backupOffset += jobs?.length() ?: 0
    }

    private fun chooseEvidence() {
        if (busy) return
        if (pendingBody.isNotEmpty()) {
            showError("LOCAL_AUTHORITY_REQUEST_PENDING")
            return
        }
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                ),
            EVIDENCE,
        )
    }

    private fun chooseOriginals() {
        if (busy) return
        val selected = evidence
        if (selected == null) {
            showError("LOCAL_AUTHORITY_EVIDENCE_REQUIRED")
            return
        }
        try {
            selected.ledger(Instant.now().toString())
        } catch (_: Exception) {
            showError("LOCAL_EVIDENCE_LEDGER_INVALID")
            return
        }
        confirm(
            "Originale ausdrücklich einlesen?",
            "Nur PDFs, deren Inhalt exakt zu einer Prüfsumme im gewählten Nachweis passt, werden privat gespeichert. Der Quellordner bleibt unverändert. Maximal 20 MiB je PDF. Es entstehen keine Rechnungen und keine erfundenen Positionen.",
            "Abbrechen",
            "PDF-Ordner wählen",
        ) {
            importRequest = UUID.randomUUID().toString()
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                PDF_TREE,
            )
        }
    }

    @Deprecated("Preserves the existing document selection request codes")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (uri.scheme != "content") {
            showError("DOCUMENT_URI_REQUIRED")
            return
        }
        if (requestCode == EVIDENCE) {
            try {
                if (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
            } catch (_: SecurityException) {}
            run {
                val loaded = LocalAuthorityEvidence.read(this, uri)
                ui {
                    evidence = loaded
                    evidenceUri = uri.toString()
                    evidenceHash = loaded.sha256
                    clearChecks()
                    renderEvidence()
                }
            }
        } else if (requestCode == PDF_TREE) {
            val selected = evidence
            val request = importRequest
            run {
                if (selected == null) throw IOException("LOCAL_AUTHORITY_EVIDENCE_REQUIRED")
                val result =
                    LocalAuthorityEvidence.importOriginals(
                        this,
                        uri,
                        selected.ledger(Instant.now().toString()),
                        request,
                    )
                ui {
                    originals =
                        "Bewusst nicht als Original importierte Nie-erstellt-Einträge: ${result.optInt("pending_not_created_count")}\nPassende eindeutige Originale geprüft: ${result.optInt("matched_unique_originals")} von ${result.optInt("required_unique_originals")} im Nachweis.\nIn diesem Ordner nicht gefunden: ${result.optInt("not_found_in_selected_tree")} · PDFs über 20 MiB: ${result.optInt("oversized_pdf_files")}\nQuellordner unverändert; keine Rechnungsdatensätze erzeugt. Vorhandene private Originale werden beim Belegabgleich zusätzlich geprüft."
                    clearChecks()
                }
            }
        }
    }

    private fun renderEvidence() {
        val selected = evidence ?: return
        try {
            val summary = selected.summary()
            val ledger =
                if (summary.optBoolean("ledger_valid"))
                    "Belege: ${summary.optInt("issued_count")} · Reservierungen: ${summary.optInt("reservation_count")} · letzter Zähler: ${summary.optLong("last_number")} · Eröffnungsstände: ${summary.optInt("opening_balance_count")}\nOriginalzuordnungen: ${summary.optInt("original_reference_count")} · ausdrücklich nie erstellt: ${summary.optInt("pending_not_created_count")} · ungeklärt: ${summary.optInt("unresolved_pdf_count")}\nOriginaleinträge ohne Snapshotbindung: ${summary.optInt("snapshot_binding_missing_count")}. Bei bekanntem ausstehendem PDF-Job sind Snapshot-SHA und Dateigröße zwingend."
                else
                    "Kein vollständiges Beleg-/Reservierungsschema. Ungeklärte PDF-Einträge: ${summary.optInt("unresolved_pdf_count")}. Für den Rechnungsabgleich einen vollständigen Nachweis wählen."
            evidenceText =
                "Nachweisdatei lokal gelesen. Prüfsumme: ${selected.sha256}\nZeitpunkt in der Quelldatei: ${time(summary.optString("source_checked_at"))}\n$ledger\nDas Einlesen allein bestätigt weder Aktualität noch stillgelegte Schreiber."
        } catch (_: Exception) {
            showError("LOCAL_EVIDENCE_JSON_INVALID")
        }
    }

    private fun confirmActivate() {
        val current = preview ?: return
        val selected = evidence ?: return
        if (busy || selectedBackup.isEmpty() || !writers) return
        if (pendingBody.isNotEmpty()) {
            showError("LOCAL_AUTHORITY_REQUEST_PENDING")
            return
        }
        confirm(
            "Pixel als Arbeitsbestand aktivieren?",
            "Den eben gelesenen Geschäftsbestand mit der ausdrücklich gewählten extern geprüften Sicherung vergleichen und auf lokalen Betrieb umstellen? Alte Schreibwege werden geschützt angehalten; Verbindungen bleiben erhalten. Die Rechnungsausgabe bleibt gesperrt. Betriebsdaten und Journale werden nicht gelöscht.",
            "Weiter prüfen",
            "Lokal umstellen",
        ) {
            try {
                pendingName = "activate_local_authority"
                pendingBody =
                    JSONObject()
                        .put("request_id", UUID.randomUUID().toString())
                        .put("expected_preview_hash", current.getString("preview_hash"))
                        .put("backup_id", selectedBackup)
                        .put("writer_evidence", selected.writer(Instant.now().toString()))
                        .toString()
                submitPending()
            } catch (error: Exception) {
                showError(safeCode(error))
            }
        }
    }

    private fun confirmReconcile() {
        val current = preview ?: return
        val selected = evidence ?: return
        if (busy || !writers || !currentLedger || !notCreatedConfirmed()) return
        if (pendingBody.isNotEmpty()) {
            showError("LOCAL_AUTHORITY_REQUEST_PENDING")
            return
        }
        val ledger =
            try {
                selected.ledger(Instant.now().toString())
            } catch (_: Exception) {
                showError("LOCAL_EVIDENCE_LEDGER_INVALID")
                return
            }
        confirm(
            "Aktuellen Nummernstand übernehmen?",
            "Alle ${ledger.optJSONArray("issued").length()} Belege und ${ledger.optJSONArray("reservations").length()} Reservierungen mit dem lokalen Bestand und den Original-PDFs prüfen? Belegter letzter Zähler: ${ledger.optLong("last_number")}. Ausdrücklich als nie erstellt markierte PDFs: ${notCreatedCount()}. Diese Zahl ist kein Originalnachweis. Fehlende Nachweise oder abweichende Originale verhindern die Freigabe. Restbeträge werden nur gemäß ausgewähltem Nachweis übernommen.",
            "Weiter prüfen",
            "Belege und Nummern prüfen",
        ) {
            try {
                pendingName = "reconcile_local_invoice_authority"
                pendingBody =
                    JSONObject()
                        .put("request_id", UUID.randomUUID().toString())
                        .put("expected_preview_hash", current.getString("preview_hash"))
                        .put(
                            "ledger",
                            selected.reviewedLedger(
                                Instant.now().toString(),
                                current.getJSONObject("authority"),
                                writers && currentLedger,
                                notCreatedReview,
                            ),
                        )
                        .toString()
                submitPending()
            } catch (error: Exception) {
                showError(safeCode(error))
            }
        }
    }

    private fun submitPending() {
        if (busy || pendingBody.isEmpty()) return
        val name = pendingName
        val body = pendingBody
        val uri = evidenceUri
        val hash = evidenceHash
        val backup = selectedBackup
        run {
            PixelLocalStore(this).use { store ->
                LocalAuthorityEvidence.savePending(this, name, body, uri, hash, backup)
                val response = PixelOwnerAccess(this, store).execute(name, JSONObject(body))
                if (!response.optBoolean("ok"))
                    throw IOException("LOCAL_AUTHORITY_OPERATION_FAILED")
                val refreshed = LocalAuthorityMigration(this, store).preview()
                LocalAuthorityEvidence.clearPending(this, body)
                ui {
                    preview = refreshed
                    pendingBody = ""
                    pendingName = ""
                    clearChecks()
                    renderPreview()
                    status +=
                        "\nDer angeforderte Schritt ist dauerhaft protokolliert. Der aktuelle Arbeits- und Rechnungsstatus steht oben."
                }
            }
        }
    }

    private fun notCreatedCount(): Int =
        try {
            evidence?.summary()?.optInt("pending_not_created_count") ?: 0
        } catch (_: Exception) {
            Int.MAX_VALUE
        }

    private fun notCreatedConfirmed(): Boolean = notCreatedCount() == 0 || notCreatedReview

    private fun reviewPending() {
        if (busy || pendingBody.isEmpty()) return
        val previous = pendingBody
        run {
            PixelLocalStore(this).use { store ->
                val current = LocalAuthorityMigration(this, store).preview()
                ui {
                    preview = current
                    renderPreview()
                    val authority = current.optJSONObject("authority")
                    confirm(
                        "Neue Entscheidung vorbereiten?",
                        "Der aktuelle Bestand wurde erneut gelesen. Arbeitsstatus: ${modeLabel(authority?.optString("mode") ?: "UNKNOWN")}. Rechnungsautorität: ${authority?.optString("invoice_authority") ?: "unbekannt"}.\n\nEin bereits ausgeführter Schritt wird dadurch nicht zurückgenommen. Die bisherige Aktions-ID kann weiterhin identisch geprüft werden. Nur nach Vergleich dieses Ergebnisses neue Nachweise und eine neue Entscheidung vorbereiten.",
                        "Bisherige Aktion erhalten",
                        "Neue Entscheidung vorbereiten",
                    ) {
                        run {
                            LocalAuthorityEvidence.clearPending(this, previous)
                            ui {
                                pendingName = ""
                                pendingBody = ""
                                clearChecks()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("evidence_uri", evidenceUri)
        outState.putString("evidence_hash", evidenceHash)
        outState.putString("backup_id", selectedBackup)
        outState.putString("import_request", importRequest)
    }

    private fun leave() {
        if (busy) {
            confirm(
                "Vorgang läuft noch",
                "Der ausdrücklich angeforderte Schritt kann weiterlaufen. Bei späterer Rückkehr den aktuellen Stand prüfen; keine erneute Umstellung aus einem unbestätigten Ergebnis ableiten.",
                "Weiter warten",
                "Zur App zurück",
                ::finish,
            )
        } else if (pendingBody.isNotEmpty() || writers || currentLedger) {
            confirm(
                "Prüfung verlassen?",
                "Ein bereits angeforderter Schritt bleibt privat mit derselben Aktions-ID erhalten. Eine neue Öffnung prüft den aktuellen Bestand. Bereits ausgeführte Schritte sind dauerhaft protokolliert; Kontrollkästchen allein ändern keine Betriebsdaten.",
                "Weiter prüfen",
                "Prüfung verlassen",
                ::finish,
            )
        } else finish()
    }

    private fun showFormat() {
        confirm(
            "Externer Nachweis · Format 1",
            "UTF-8 JSON, höchstens 8 MiB, formatVersion: 1. Ein reines Einlesen ersetzt keine Prüfung externer Schreiber.\n\nFür Rechnungen zusätzlich: last_number (belegter Zähler), issued (Liste aller Belege mit invoice_id, invoice_number, pdf_resolution: original, pdf_sha256 und Dateigröße pdf_byte_size; für bekannte ausstehende Jobs zusätzlich snapshot_sha256 aus dem PDF-Job), reservations (reservation_id, invoice_id, invoice_number, counter, state: reserved/issued/cancelled). Nur nach bewusster aktueller Prüfung darf ein nachweislich nie erzeugter Job pdf_resolution: not_created, not_created_confirmed: true, not_created_reason und snapshot_sha256 verwenden; pdf_sha256 und pdf_byte_size müssen dann fehlen. Ein verlorenes oder unbekanntes Original fällt nicht darunter. Nummern, Reservierung und aktuelle Prüfung bleiben zwingend. Leere Listen nur wenn tatsächlich belegt. Optional opening_balances mit invoice_id und remaining_minor.\n\nKeine Schlüssel oder Kontotokens in diese Datei aufnehmen. Die App berechnet die Prüfsumme selbst und bindet sie an die ausdrücklich bestätigte Prüfung. Weder neue Nummern noch Rechnungsdaten werden aus Dateinamen erraten.",
            null,
            "Verstanden",
        )
    }

    private fun showError(code: String) {
        val detail =
            when (code) {
                "LOCAL_MIGRATION_WRITE_CONFLICT" ->
                    "Der Bestand wurde inzwischen geändert. Eingaben bleiben erhalten. Bestand neu prüfen und die Nachweise erneut vergleichen."
                "LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED" ->
                    "Die gewählte Sicherung bestätigt den aktuellen Geschäftsstand nicht vollständig extern. Aktuellen Stand vollständig sichern und danach neu prüfen."
                "LOCAL_MIGRATION_REQUIRED_AFTER_RESTORE" ->
                    "Nach dieser Wiederherstellung muss zunächst der lokale Arbeitsbestand erneut mit aktueller externer Sicherung und stillgelegten Schreibern abgeglichen werden."
                "LOCAL_MIGRATION_DATA_REVIEW_REQUIRED" ->
                    "IDs, Beziehungen oder Originale sind noch ungeklärt. Bestand und fehlende Originale prüfen; es wird nichts automatisch entschieden."
                "LOCAL_MIGRATION_OPEN_NUMBER_ACTIONS",
                "LOCAL_MIGRATION_SYNC_STILL_RUNNING" ->
                    "Ein bisheriger Schreib- oder Nummernvorgang ist noch offen. Diesen kontrolliert abschließen und den Stand erneut prüfen."
                "INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED" ->
                    "Die Angabe nie erstellt benötigt eine genaue Snapshotbindung, Begründung und erneute ausdrückliche Prüfung des aktuellen Original- und Quittungsbestands. Ein fehlendes oder unbekanntes PDF darf nicht neu erzeugt werden."
                "LOCAL_EVIDENCE_LEDGER_INVALID",
                "LOCAL_EVIDENCE_JSON_INVALID",
                "LOCAL_EVIDENCE_VERSION_UNSUPPORTED" ->
                    "Die Datei hat nicht das erforderliche Nachweisformat. Felder anzeigen und den belegten Nachweis korrigieren; gespeicherte Daten bleiben erhalten."
                "LOCAL_AUTHORITY_REQUEST_PENDING" ->
                    "Ein bereits angeforderter Schritt ist noch nicht abschließend bestätigt. Diesen mit derselben Aktion erneut prüfen. Für neue Eingaben zuerst das Ergebnis prüfen und anschließend den angeforderten Schritt ausdrücklich abschließen."
                "LOCAL_EVIDENCE_SOURCE_CHANGED" ->
                    "Die gewählte Nachweisdatei wurde verändert. Datei erneut ausdrücklich wählen und ihren aktuellen Inhalt prüfen."
                else ->
                    "Schritt nicht abgeschlossen. Nachweise und aktuellen Bestand prüfen. Fehlende externe Originale oder ein unklarer Nummernstand lassen die Rechnungsausgabe gesperrt."
            }
        status = detail + "\nFehlercode: " + code
    }

    companion object {
        private const val EVIDENCE = 301
        private const val PDF_TREE = 302

        private fun safeCode(error: Exception): String =
            error.message?.takeIf {
                it.matches(
                    Regex("(?:LOCAL|INVOICE|BACKUP|DOCUMENT|RECOVERY|RESTORE)_[A-Z0-9_]{1,100}")
                )
            }
                ?: if (error is SecurityException) "DOCUMENT_PERMISSION_REQUIRED"
                else "LOCAL_AUTHORITY_OPERATION_FAILED"

        private fun modeLabel(mode: String): String =
            when (mode) {
                "LEGACY_DRIVE" -> "bisheriger Betrieb · lokale Prüfung offen"
                "LOCAL_ACTIVE" -> "Pixel lokal maßgeblich"
                "RESTORE_REVIEW" -> "Prüfung nach Wiederherstellung erforderlich"
                "MIGRATING" -> "unterbrochene oder laufende Umstellung prüfen"
                else -> "unbekannt · keine Freigabe"
            }

        private fun time(value: String): String =
            try {
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
                    .withZone(ZoneId.of("Europe/Vienna"))
                    .format(Instant.parse(value))
            } catch (_: Exception) {
                "nicht belegt"
            }
    }
}
