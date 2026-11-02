package at.hausgarten.service

import android.content.Context
import java.io.*
import java.util.*
import org.json.*

internal class LocalAuthorityMigration {
    private val context: Context
    private val store: PixelLocalStore

    constructor(context: Context, store: PixelLocalStore) {
        this.context = context
        this.store = store
    }

    @Throws(Exception::class)
    fun preview(): JSONObject {

        var state = withExtended(store.getState())
        var inventory =
            LocalMigrationInventory.inspect(
                state,
                LocalOriginalVerifier(
                    context.getFilesDir(),
                    store::photoAttachment,
                    { id -> JSONObject(store.ownerValue(("local_invoice_job:" + id), "{}")) },
                ),
            )
        var result =
            JSONObject()
                .put("formatVersion", 1)
                .put("authority", LocalAuthorityGuard(context).current())
                .put("inventory", inventory)
                .put("state_sha256", businessHash(state))
                .put("drive_writer_running", DriveSyncCoordinator.running())
                .put("invoice_issue_allowed", false)
                .put("requires_external_writer_evidence", true)
        return result.put("preview_hash", PixelMcpWriteTools.hash(result))
    }

    @Throws(Exception::class)
    fun activateFromArgs(args: JSONObject): JSONObject =
        activate(
            args.getString("request_id"),
            args.getString("expected_preview_hash"),
            args.getJSONObject("writer_evidence"),
        )

    @Throws(Exception::class)
    fun reconcile(args: JSONObject): JSONObject {

        var request = args.getString("request_id")
        var fingerprint = PixelMcpWriteTools.hash(args)
        var evidence = args.getJSONObject("ledger")
        LocalAuthorityRules.requireEvidence(evidence)
        synchronized(DriveSyncCoordinator.LOCK) {
            if ((!(LocalAuthorityGuard.local(context)) || DriveSyncCoordinator.running())) {
                throw IOException("LOCAL_AUTHORITY_REQUIRED")
            }
            var contract = store.ownerValue("local_authority_contract", "")
            if (
                (contract.isEmpty() ||
                    !((JSONObject(contract).getString("migration_epoch") ==
                        LocalAuthorityGuard(context).current().optString("migration_epoch"))))
            ) {
                throw IOException("LOCAL_MIGRATION_REQUIRED_AFTER_RESTORE")
            }
            var old = store.ownerValue(("local_invoice_reconciliation:" + request), "")
            if (!(old.isEmpty())) {
                var saved = JSONObject(old)
                if (!((fingerprint == saved.getString("fingerprint")))) {
                    throw IOException("INVOICE_RECONCILIATION_REPLAY_MISMATCH")
                }
                requireCurrentReceipt(saved)
                var checkpoint: Int = saved.getJSONObject("result").getInt("last_number")
                var authority = LocalAuthorityGuard(context).current()
                if (!((authority.optString("invoice_authority") == "RECONCILED"))) {
                    if (
                        (JSONObject(store.ownerValue("local_invoice_counter", "{}"))
                            .getInt("last_number") != checkpoint)
                    ) {
                        throw IOException("INVOICE_RECONCILIATION_REVIEW_REQUIRED")
                    }
                    LocalAuthorityGuard(context).reconcileInvoices(request, evidence, checkpoint)
                }
                return scheduleAuthorizedDocuments(
                    saved.getJSONObject("result").put("replayed", true)
                )
            }
            requireCurrentEvidence(evidence, LocalAuthorityGuard(context).current(), true)
            var acceptedPreview = preview()
            if (
                !((acceptedPreview.getString("preview_hash") ==
                    args.getString("expected_preview_hash")))
            ) {
                throw IOException("LOCAL_MIGRATION_WRITE_CONFLICT")
            }
            var capturedState = store.getState()
            var all = JSONObject(capturedState.getJSONObject("master").toString())
            var stateCas = stateWriteHash(capturedState)
            DriveJournalRules.apply(all, capturedState.getJSONArray("pending"))
            var settings = all.getJSONObject("business_settings")
            var last: Int =
                java.math.BigDecimal(evidence.get("last_number").toString()).intValueExact()
            if (((last < 0) || (last > 1000000000))) {
                throw IOException("INVOICE_NUMBER_STATE_INVALID")
            }
            var issued = evidence.getJSONArray("issued")
            var reservations = evidence.getJSONArray("reservations")
            var originals = HashMap<String, JSONObject>()
            var numbers = HashSet<String>()
            var recoveryJobs = LinkedHashMap<String, JSONObject>()
            var adoptions = LinkedHashMap<String, JSONObject>()
            var firstCreations = HashSet<String>()
            var evidenceHash = PixelMcpWriteTools.hash(evidence)
            run {
                var i: Int = 0
                var firstIteration42 = true
                while (true) {
                    if (firstIteration42) firstIteration42 = false
                    else {
                        i++
                    }
                    if (!((i < issued.length()))) break

                    var row = issued.getJSONObject(i)
                    var id = row.getString("invoice_id")
                    if (
                        ((originals.put(id, row) != null) ||
                            !(numbers.add(row.getString("invoice_number"))))
                    ) {
                        throw IOException("INVOICE_EVIDENCE_DUPLICATE")
                    }
                    var known = OwnerJournalRules.findOrNull(all.getJSONArray("invoices"), id)
                    var notCreated: Boolean = (row.optString("pdf_resolution") == "not_created")
                    if (
                        ((known != null) &&
                            !((row.getString("invoice_number") ==
                                known.optString("invoice_number"))))
                    ) {
                        throw IOException("INVOICE_EVIDENCE_MISMATCH")
                    }
                    if (
                        (notCreated || ((known != null) && known.optString("pdf_sha256").isEmpty()))
                    ) {
                        if ((known == null)) {
                            throw IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED")
                        }
                        var job = recoveryJob(known, row)
                        recoveryJobs.put(id, job)
                        if (notCreated) {
                            requireNeverCreated(known, job, row)
                            firstCreations.add(id)
                        } else {
                            var sha = row.getString("pdf_sha256")
                            var bytes: Long =
                                java.math
                                    .BigDecimal(row.get("pdf_byte_size").toString())
                                    .longValueExact()
                            if ((!(sha.matches(Regex("[0-9a-f]{64}"))) || (bytes < 5))) {
                                throw IOException("INVOICE_ORIGINAL_EVIDENCE_REQUIRED")
                            }
                            adoptions.put(
                                id,
                                JSONObject()
                                    .put("document_id", ("invoice:" + id))
                                    .put("pdf_sha256", sha)
                                    .put("pdf_storage_name", (sha + ".pdf"))
                                    .put("pdf_byte_size", bytes),
                            )
                        }
                    } else {
                        var sha = row.getString("pdf_sha256")
                        if (!(sha.matches(Regex("[0-9a-f]{64}")))) {
                            throw IOException("INVOICE_ORIGINAL_EVIDENCE_REQUIRED")
                        }
                        if ((known != null)) {
                            if (!((sha == known.optString("pdf_sha256")))) {
                                throw IOException("INVOICE_EVIDENCE_MISMATCH")
                            }
                            BusinessDocumentStore(context).read(known)
                            var rawJob = store.ownerValue(("local_invoice_job:" + id), "")
                            if (
                                (!(rawJob.isEmpty()) &&
                                    Arrays.asList("PDF_PENDING", "ORIGINAL_REVIEW_REQUIRED")
                                        .contains(JSONObject(rawJob).optString("phase")))
                            ) {
                                var job = recoveryJob(known, row)
                                if (
                                    (!(row.has("pdf_byte_size")) ||
                                        (java.math
                                            .BigDecimal(row.get("pdf_byte_size").toString())
                                            .longValueExact() != known.getLong("pdf_byte_size")))
                                ) {
                                    throw IOException("INVOICE_EVIDENCE_MISMATCH")
                                }
                                recoveryJobs.put(id, job)
                                adoptions.put(
                                    id,
                                    JSONObject()
                                        .put("document_id", ("invoice:" + id))
                                        .put("pdf_sha256", sha)
                                        .put("pdf_storage_name", (sha + ".pdf"))
                                        .put("pdf_byte_size", known.getLong("pdf_byte_size")),
                                )
                            }
                        } else {
                            var original =
                                java.io.File(
                                    context.getFilesDir(),
                                    (("business-documents/" + sha) + ".pdf"),
                                )
                            if (
                                ((!(original.isFile()) ||
                                    (original.length() != row.getLong("pdf_byte_size"))) ||
                                    !((EncryptedBackupArchive.sha256(original) == sha)))
                            ) {
                                throw IOException("INVOICE_EXTERNAL_ORIGINAL_MISSING")
                            }
                        }
                    }
                }
            }
            run {
                var i: Int = 0
                var firstIteration43 = true
                while (true) {
                    if (firstIteration43) firstIteration43 = false
                    else {
                        i++
                    }
                    if (!((i < all.getJSONArray("invoices").length()))) break

                    var invoice = all.getJSONArray("invoices").getJSONObject(i)
                    if (
                        (IssuedInvoiceRules.isIssued(invoice) &&
                            !(originals.containsKey(invoice.getString("id"))))
                    ) {
                        throw IOException("INVOICE_CURRENT_LEDGER_INCOMPLETE")
                    }
                }
            }
            var reservationIds = HashMap<String, JSONObject>()
            var reservedCounters = HashSet<Int>()
            run {
                var i: Int = 0
                var firstIteration44 = true
                while (true) {
                    if (firstIteration44) firstIteration44 = false
                    else {
                        i++
                    }
                    if (!((i < reservations.length()))) break

                    var row = reservations.getJSONObject(i)
                    var number: Int =
                        java.math.BigDecimal(row.get("counter").toString()).intValueExact()
                    if (
                        (((((number <= 0) || (number > last)) ||
                            (reservationIds.put(row.getString("reservation_id"), row) != null)) ||
                            !(reservedCounters.add(number))) ||
                            !(Arrays.asList("reserved", "issued", "cancelled")
                                .contains(row.getString("state"))))
                    ) {
                        throw IOException("INVOICE_RESERVATION_REVIEW_REQUIRED")
                    }
                }
            }
            store
                .getReadableDatabase()
                .rawQuery(
                    "SELECT value FROM owner_state WHERE key LIKE 'local_number_reservation:%'",
                    null,
                )
                .use { keys ->
                    while (keys.moveToNext()) {
                        var known = JSONObject(keys.getString(0))
                        var proved = reservationIds.get(known.getString("request_id"))
                        if (
                            ((((proved == null) ||
                                (proved.getInt("counter") != known.getInt("counter"))) ||
                                !((proved.optString("invoice_number") ==
                                    known.getString("invoice_number")))) ||
                                !((proved.optString("invoice_id") ==
                                    known.getString("invoice_id"))))
                        ) {
                            throw IOException("INVOICE_RESERVATION_REVIEW_REQUIRED")
                        }
                    }
                }
            for (id in firstCreations) {
                var job = recoveryJobs.get(id)!!
                var frozen = job.getJSONObject("snapshot")
                var reservation = reservationIds.get(frozen.getString("local_issue_request"))
                if (
                    (((((reservation == null) ||
                        !(Arrays.asList("reserved", "issued")
                            .contains(reservation.optString("state")))) ||
                        !((id == reservation.optString("invoice_id")))) ||
                        !((frozen.getString("invoice_number") ==
                            reservation.optString("invoice_number")))) ||
                        (reservation.getInt("counter") !=
                            frozen.getJSONObject("numbering_receipt").getInt("counter")))
                ) {
                    throw IOException("INVOICE_RESERVATION_REVIEW_REQUIRED")
                }
            }
            var observed: Int =
                settings.optInt(
                    "invoice_numbering_last_number",
                    settings.optInt("invoice_sevdesk_last_number", 0),
                )
            run {
                var i: Int = 0
                var firstIteration45 = true
                while (true) {
                    if (firstIteration45) firstIteration45 = false
                    else {
                        i++
                    }
                    if (!((i < all.getJSONArray("invoices").length()))) break

                    var invoice = all.getJSONArray("invoices").getJSONObject(i)
                    var receipt = invoice.optJSONObject("numbering_receipt")
                    if ((receipt != null)) {
                        observed = Math.max(observed, receipt.optInt("counter"))
                    }
                }
            }
            var previousCounter = store.ownerValue("local_invoice_counter", "")
            if (!(previousCounter.isEmpty())) {
                observed = Math.max(observed, JSONObject(previousCounter).getInt("last_number"))
            }
            var format = settings.optString("invoice_number_format")
            var prefix = settings.optString("invoice_prefix")
            var regex =
                (if ((format == "sevdesk")) "RE-([0-9]+)(?:/[0-9]{4})?"
                else
                    (java.lang.String.valueOf(java.util.regex.Pattern.quote(prefix)) +
                        "-[0-9]{4}-([0-9]+)"))
            run {
                var i: Int = 0
                var firstIteration46 = true
                while (true) {
                    if (firstIteration46) firstIteration46 = false
                    else {
                        i++
                    }
                    if (!((i < issued.length()))) break

                    var matcher =
                        java.util.regex.Pattern.compile(regex)
                            .matcher(issued.getJSONObject(i).getString("invoice_number"))
                    if (matcher.matches()) {
                        observed =
                            Math.max(
                                observed,
                                java.math.BigDecimal(matcher.group(1)).intValueExact(),
                            )
                    }
                }
            }
            if (
                ((last < observed) ||
                    (last < LocalAuthorityGuard(context).current().optInt("invoice_high_water")))
            ) {
                throw IOException("INVOICE_NUMBER_ROLLBACK_DETECTED")
            }
            var receiptProofs = HashMap<String, String>()
            for (adoption in adoptions.entries) {
                val job = recoveryJobs.getValue(adoption.key)
                adoption.setValue(
                    LocalInvoiceJobs.prepareVerifiedOriginal(
                        context,
                        job,
                        adoption.value,
                        evidenceHash,
                    )
                )
                var receiptProof = LocalInvoiceJobs.verifiedReceiptEvidenceHash(context, job)
                if (!(receiptProof.isEmpty())) {
                    receiptProofs.put(adoption.key, receiptProof)
                }
            }
            var db = store.getWritableDatabase()
            store.beginBusinessTransaction(db)
            try {

                var currentState = store.getState()
                if (
                    (((!((acceptedPreview.getString("state_sha256") ==
                        businessHash(withExtended(currentState)))) ||
                        !((stateCas == stateWriteHash(currentState)))) ||
                        !((acceptedPreview.getJSONObject("authority").getString("revision") ==
                            LocalAuthorityGuard(context).current().getString("revision")))) ||
                        !((contract == store.ownerValue("local_authority_contract", ""))))
                ) {
                    throw IOException("LOCAL_MIGRATION_WRITE_CONFLICT")
                }
                requireCurrentEvidence(evidence, LocalAuthorityGuard(context).current(), true)
                var identity = BackupDeviceGuard(context).current()
                var reviewed = evidence.getJSONObject("current_review").getString("reviewed_at")
                var invoiceMetadataChanged: Boolean = false
                for (entry in recoveryJobs.entries) {
                    var id = entry.key
                    var job = JSONObject(entry.value.toString())
                    var invoice = PixelMcpWriteTools.find(all.getJSONArray("invoices"), id)
                    var before = PixelMcpWriteTools.hash(job)
                    var kind: String
                    if (firstCreations.contains(id)) {
                        requireNeverCreated(invoice, job, originals.get(id)!!)
                        job.put(
                                "creation_authorization",
                                JSONObject()
                                    .put("formatVersion", 1)
                                    .put("installation_id", identity.getString("installationId"))
                                    .put("restore_epoch", identity.getString("restoreEpoch"))
                                    .put("snapshot_sha256", job.getString("snapshot_sha256"))
                                    .put("ledger_proof_sha256", evidenceHash)
                                    .put("request_id", request)
                                    .put("authorized_at", reviewed),
                            )
                            .put("phase", "PDF_PENDING")
                            .put("attempts", 0)
                            .remove("last_error_code")
                        kind = "first_creation_explicitly_authorized"
                    } else {
                        var metadata = adoptions.get(id)!!
                        var adoptionProof =
                            JSONObject()
                                .put("formatVersion", 1)
                                .put("snapshot_sha256", job.getString("snapshot_sha256"))
                                .put("ledger_proof_sha256", evidenceHash)
                                .put("request_id", request)
                                .put("adopted_at", reviewed)
                        if (receiptProofs.containsKey(id)) {
                            adoptionProof.put("receipt_proof_sha256", receiptProofs.get(id))
                        }
                        for (field in
                            arrayOf(
                                "document_id",
                                "pdf_sha256",
                                "pdf_storage_name",
                                "pdf_byte_size",
                            )) {
                            invoice.put(field, metadata.get(field))
                        }
                        invoice.put("pdf_state", "READY")
                        OwnerJournalRules.replace(all, "invoices", id, invoice)
                        job.put("document", metadata)
                            .put("phase", "READY")
                            .put("verified_at", java.time.Instant.now().toString())
                            .put("original_adoption", adoptionProof)
                            .remove("last_error_code")
                        kind = "verified_original_adopted"
                        invoiceMetadataChanged = true
                    }
                    var audit =
                        JSONObject()
                            .put("formatVersion", 1)
                            .put("invoice_id", id)
                            .put("invoice_number", invoice.getString("invoice_number"))
                            .put("snapshot_sha256", job.getString("snapshot_sha256"))
                            .put("resolution", kind)
                            .put("reason", originals.get(id)!!.optString("not_created_reason"))
                            .put("before_job_sha256", before)
                            .put("after_job_sha256", PixelMcpWriteTools.hash(job))
                            .put("ledger_proof_sha256", evidenceHash)
                            .put("request_id", request)
                            .put("reviewed_at", reviewed)
                            .put("installation_id", identity.getString("installationId"))
                            .put("restore_epoch", identity.getString("restoreEpoch"))
                    var auditKey =
                        ("local_invoice_document_recovery:" +
                            PixelMcpWriteTools.hash(
                                JSONObject().put("request_id", request).put("invoice_id", id)
                            ))
                    var previousAudit = store.ownerValue(auditKey, "")
                    if (
                        (!(previousAudit.isEmpty()) &&
                            !((PixelMcpWriteTools.hash(JSONObject(previousAudit)) ==
                                PixelMcpWriteTools.hash(audit))))
                    ) {
                        throw IOException("INVOICE_RECONCILIATION_REPLAY_MISMATCH")
                    }
                    store.putOwnerValue(("local_invoice_job:" + id), job.toString())
                    if (previousAudit.isEmpty()) {
                        store.putOwnerValue(auditKey, audit.toString())
                    }
                }
                if (invoiceMetadataChanged) {
                    store.commitLocalSnapshot(all)
                }
                store.putOwnerValue(
                    "local_invoice_counter",
                    JSONObject()
                        .put("formatVersion", 1)
                        .put("last_number", last)
                        .put("evidence_sha256", PixelMcpWriteTools.hash(evidence))
                        .put("reconciled_at", java.time.Instant.now().toString())
                        .toString(),
                )
                store.putOwnerValue("local_invoice_external_evidence", evidence.toString())
                var balances = evidence.optJSONArray("opening_balances")
                run {
                    var i: Int = 0
                    var firstIteration47 = true
                    while (true) {
                        if (firstIteration47) firstIteration47 = false
                        else {
                            i++
                        }
                        if (!(((balances != null) && (i < balances.length())))) break

                        var b = balances.getJSONObject(i)
                        var invoice =
                            PixelMcpWriteTools.find(
                                all.getJSONArray("invoices"),
                                b.getString("invoice_id"),
                            )
                        var total: Long =
                            LocalAuthorityRules.minor(
                                (if (invoice.has("gross_total")) invoice.get("gross_total")
                                else invoice.get("amount"))
                            )
                        var remaining: Long =
                            java.math
                                .BigDecimal(b.get("remaining_minor").toString())
                                .longValueExact()
                        if (((remaining < 0) || (remaining > Math.max(0, total)))) {
                            throw IOException("INVOICE_OPENING_BALANCE_INVALID")
                        }
                        var balanceKey = ("invoice_opening_balance:" + invoice.getString("id"))
                        var before = store.ownerValue(balanceKey, "")
                        if (
                            (!(before.isEmpty()) &&
                                (JSONObject(before).getLong("remaining_minor") != remaining))
                        ) {
                            throw IOException("INVOICE_OPENING_BALANCE_CORRECTION_REQUIRED")
                        }
                        if (before.isEmpty()) {
                            store.putOwnerValue(
                                balanceKey,
                                JSONObject(b.toString())
                                    .put("evidence_sha256", PixelMcpWriteTools.hash(evidence))
                                    .toString(),
                            )
                        }
                    }
                }
                LocalFinancialSnapshotValidation.inspect(
                    db,
                    SnapshotProjection.effective(store.getState()),
                )
                var result =
                    JSONObject()
                        .put("invoice_authority", "RECONCILED")
                        .put("last_number", last)
                        .put("replayed", false)
                        .put("document_creation_jobs", firstCreations.size)
                        .put("document_originals_adopted", adoptions.size)
                store.putOwnerValue(
                    ("local_invoice_reconciliation:" + request),
                    receipt(fingerprint, result).toString(),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            LocalAuthorityGuard(context).reconcileInvoices(request, evidence, last)
            return scheduleAuthorizedDocuments(
                JSONObject()
                    .put("invoice_authority", "RECONCILED")
                    .put("last_number", last)
                    .put("replayed", false)
                    .put("document_creation_jobs", firstCreations.size)
                    .put("document_originals_adopted", adoptions.size)
            )
        }
    }

    private @Throws(Exception::class) fun recoveryJob(
        invoice: JSONObject,
        proof: JSONObject,
    ): JSONObject {

        var id = invoice.getString("id")
        var raw = store.ownerValue(("local_invoice_job:" + id), "")
        if ((raw.isEmpty() || invoice.optString("local_issue_request").isEmpty())) {
            throw IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED")
        }
        var job = JSONObject(raw)
        var frozen = job.getJSONObject("snapshot")
        var hash = proof.optString("snapshot_sha256")
        if (
            (((((((!(hash.matches(Regex("[0-9a-f]{64}"))) ||
                !((hash == job.optString("snapshot_sha256")))) ||
                !((hash == PixelMcpWriteTools.hash(frozen)))) ||
                !((id == job.optString("invoice_id")))) || !((id == frozen.optString("id")))) ||
                !((invoice.getString("invoice_number") == frozen.optString("invoice_number")))) ||
                !(LocalFinancialValidation.sameFrozenInvoice(invoice, frozen))) ||
                !(Arrays.asList("PDF_PENDING", "ORIGINAL_REVIEW_REQUIRED")
                    .contains(job.optString("phase"))))
        ) {
            throw IOException("INVOICE_EVIDENCE_MISMATCH")
        }
        return job
    }

    private @Throws(Exception::class) fun requireNeverCreated(
        invoice: JSONObject,
        job: JSONObject,
        proof: JSONObject,
    ): Unit {

        var identity = BackupDeviceGuard(context).current()
        var creationAlreadyAllowed: Boolean = false
        try {

            LocalInvoiceCreationRules.requireAllowed(
                job,
                identity,
                LocalAuthorityGuard(context).current(),
                LocalAuthorityGuard.restorePending(context),
            )
            creationAlreadyAllowed = true
        } catch (held: IOException) {}

        if (creationAlreadyAllowed) {
            throw IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED")
        }
        if (
            (((((((((((!((proof.optString("pdf_resolution") == "not_created")) ||
                !((true == proof.opt("not_created_confirmed")))) ||
                proof.optString("not_created_reason").trim().isEmpty()) ||
                LocalInvoiceCreationRules.hasDocumentFields(proof)) ||
                !(invoice.optString("pdf_sha256").isEmpty())) ||
                !(invoice.optString("pdf_storage_name").isEmpty())) ||
                !(invoice.optString("pdf_base64").isEmpty())) ||
                !(invoice.optString("document_id").isEmpty())) || invoice.has("pdf_byte_size")) ||
                invoice.has("document")) || (invoice.optString("pdf_state") == "READY")) ||
                LocalInvoiceJobs.hasOriginalEvidence(context, job))
        ) {
            throw IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED")
        }
    }

    private fun scheduleAuthorizedDocuments(result: JSONObject): JSONObject {

        if ((result.optInt("document_creation_jobs") > 0)) {
            try {

                LocalInvoiceJobs.schedule(context)
            } catch (deferred: Exception) {
                try {

                    result.put("document_scheduling_pending", true)
                } catch (ignored: JSONException) {}
            }
        }
        return result
    }

    @Throws(Exception::class)
    fun activate(
        request: String?,
        expectedPreview: String,
        writerProof: JSONObject,
    ): JSONObject {

        if (((request == null) || !(request.matches(Regex("[A-Za-z0-9_-]{8,200}"))))) {
            throw IOException("LOCAL_MIGRATION_REQUEST_ID_REQUIRED")
        }
        LocalAuthorityRules.requireEvidence(writerProof)
        var fingerprint =
            PixelMcpWriteTools.hash(
                JSONObject()
                    .put("expected_preview", expectedPreview)
                    .put("writers", writerProof)
            )
        synchronized(DriveSyncCoordinator.LOCK) {
            if (DriveSyncCoordinator.running()) {
                throw IOException("LOCAL_MIGRATION_SYNC_STILL_RUNNING")
            }
            var saved = store.ownerValue(("local_migration_request:" + request), "")
            if (!(saved.isEmpty())) {
                var savedReceipt = JSONObject(saved)
                if (!((fingerprint == savedReceipt.getString("fingerprint")))) {
                    throw IOException("LOCAL_MIGRATION_REPLAY_MISMATCH")
                }
                requireCurrentReceipt(savedReceipt)
                var authority = LocalAuthorityGuard(context).current()
                var contract = JSONObject(store.ownerValue("local_authority_contract", "{}"))
                if ((authority.optString("review_reason") == "MIGRATION_INTERRUPTED")) {
                    authority = LocalAuthorityGuard(context).finishMigration(request, contract)
                }
                return savedReceipt
                    .getJSONObject("result")
                    .put("mode", authority.getString("mode"))
                    .put("invoice_authority", authority.getString("invoice_authority"))
                    .put("replayed", true)
            }
            var preview = preview()
            if (!((preview.getString("preview_hash") == expectedPreview))) {
                throw IOException("LOCAL_MIGRATION_WRITE_CONFLICT")
            }
            var inventory = preview.getJSONObject("inventory")
            if (
                ((((inventory.getInt("duplicate_ids") != 0) ||
                    (inventory.getInt("orphan_relations") != 0)) ||
                    (inventory.getInt("missing_local_originals") != 0)) ||
                    (inventory.optInt("historical_reviews") != 0))
            ) {
                throw IOException("LOCAL_MIGRATION_DATA_REVIEW_REQUIRED")
            }
            var state = store.getState()
            run {
                var i: Int = 0
                var firstIteration48 = true
                while (true) {
                    if (firstIteration48) firstIteration48 = false
                    else {
                        i++
                    }
                    if (!((i < state.getJSONArray("pending").length()))) break

                    if (
                        Arrays.asList(
                                "invoice_issue",
                                "owner_invoice_issue",
                                "pixel_invoice_issue_v1",
                            )
                            .contains(
                                state.getJSONArray("pending").getJSONObject(i).optString("kind")
                            )
                    ) {
                        throw IOException("LOCAL_MIGRATION_OPEN_NUMBER_ACTIONS")
                    }
                }
            }
            requireCurrentEvidence(writerProof, preview.getJSONObject("authority"), false)
            var guard = LocalAuthorityGuard(context)
            var staged =
                guard.beginMigration(
                    request,
                    preview.getJSONObject("authority").getString("revision"),
                    writerProof,
                )
            LocalAuthorityGuard.exclusiveStartup().use { permit ->
                var contract: JSONObject
                var result: JSONObject
                var db = store.getWritableDatabase()
                store.beginBusinessTransaction(db)
                try {

                    if (
                        !((businessHash(withExtended(store.getState())) ==
                            preview.getString("state_sha256")))
                    ) {
                        throw IOException("LOCAL_MIGRATION_WRITE_CONFLICT")
                    }
                    var original = store.snapshotText("{}")
                    store.putOwnerValue(("legacy_snapshot_before_local:" + request), original)
                    var stateForLocal = store.getState()
                    var canonical = JSONObject(stateForLocal.getJSONObject("master").toString())
                    DriveJournalRules.apply(canonical, stateForLocal.getJSONArray("pending"))
                    store.commitLocalSnapshot(canonical)
                    contract =
                        JSONObject()
                            .put("formatVersion", 1)
                            .put("engine", LocalAuthorityRules.ENGINE)
                            .put("migration_epoch", staged.getString("migration_epoch"))
                            .put("migration_request", request)
                            .put("activated_at", java.time.Instant.now().toString())
                            .put("source_business_sha256", preview.getString("state_sha256"))
                            .put("writer_proof_sha256", PixelMcpWriteTools.hash(writerProof))
                            .put("old_journal_preserved", true)
                    store.putOwnerValue("local_authority_contract", contract.toString())
                    result =
                        JSONObject()
                            .put("mode", "LOCAL_ACTIVE")
                            .put("migration_epoch", staged.getString("migration_epoch"))
                            .put("invoice_authority", "LOCKED")
                            .put("replayed", false)
                            .put("old_journal_preserved", true)
                            .put("drive_role", DriveRolePolicy.role())
                    store.putOwnerValue(
                        ("local_migration_request:" + request),
                        receipt(fingerprint, result).toString(),
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                guard.finishMigration(request, contract)
                return result
            }
        }
    }

    private @Throws(Exception::class) fun requireCurrentEvidence(
        proof: JSONObject,
        authority: JSONObject,
        ledger: Boolean,
    ): Unit {

        var now = java.time.Instant.now()
        var checked = java.time.Instant.parse(proof.getString("checked_at"))
        var boundary =
            authority.optString(
                "evidence_not_before",
                authority.optString("blocked_at", authority.optString("writer_checked_at", "")),
            )
        if (
            (checked.isAfter(now) ||
                (!(boundary.isEmpty()) && checked.isBefore(java.time.Instant.parse(boundary))))
        ) {
            throw IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED")
        }
        if (ledger) {
            var review = proof.optJSONObject("current_review")
            var identity = BackupDeviceGuard(context).current()
            if (
                (((((review == null) || !(review.optBoolean("confirmed"))) ||
                    !((identity.getString("installationId") ==
                        review.optString("installation_id")))) ||
                    !((identity.getString("restoreEpoch") == review.optString("restore_epoch")))) ||
                    !((proof.getString("evidence_sha256") == review.optString("evidence_sha256"))))
            ) {
                throw IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED")
            }
            var reviewed = java.time.Instant.parse(review.getString("reviewed_at"))
            if ((reviewed.isAfter(now) || reviewed.isBefore(checked))) {
                throw IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED")
            }
            for (field in arrayOf("created_at", "blocked_at", "evidence_not_before")) {
                if (
                    (!(authority.optString(field).isEmpty()) &&
                        reviewed.isBefore(java.time.Instant.parse(authority.getString(field))))
                ) {
                    throw IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED")
                }
            }
        }
    }

    private @Throws(Exception::class) fun receipt(
        fingerprint: String,
        result: JSONObject,
    ): JSONObject {

        var identity = BackupDeviceGuard(context).current()
        return JSONObject()
            .put("fingerprint", fingerprint)
            .put("result", result)
            .put("installation_id", identity.getString("installationId"))
            .put("restore_epoch", identity.getString("restoreEpoch"))
    }

    private @Throws(Exception::class) fun requireCurrentReceipt(saved: JSONObject): Unit {

        var identity = BackupDeviceGuard(context).current()
        if (
            (!((identity.getString("installationId") == saved.optString("installation_id"))) ||
                !((identity.getString("restoreEpoch") == saved.optString("restore_epoch"))))
        ) {
            throw IOException("LOCAL_AUTHORITY_RECEIPT_STALE_AFTER_RESTORE")
        }
    }

    private @Throws(Exception::class) fun withExtended(state: JSONObject): JSONObject {

        return state.put(
            "extended_business_fingerprint",
            BusinessStateFingerprint.read(store.getReadableDatabase()),
        )
    }

    companion object {
        private @JvmStatic @Throws(Exception::class) fun stateWriteHash(state: JSONObject): String {

            return PixelMcpWriteTools.hash(
                JSONObject()
                    .put("master", state.getJSONObject("master"))
                    .put("pending", state.getJSONArray("pending"))
            )
        }

        @JvmStatic
        @Throws(Exception::class)
        fun businessHash(state: JSONObject): String {

            var effective = SnapshotProjection.effective(state)
            var selected = JSONObject()
            for (name in
                arrayOf(
                    "customers",
                    "orders",
                    "worklogs",
                    "photos",
                    "offers",
                    "invoices",
                    "appointments",
                    "service_catalog",
                    "business_settings",
                    "owner_archive",
                )) {
                if (effective.has(name)) {
                    selected.put(name, effective.get(name))
                }
            }
            if (state.has("extended_business_fingerprint")) {
                selected.put(
                    "extended_business_fingerprint",
                    state.get("extended_business_fingerprint"),
                )
            }
            return PixelMcpWriteTools.hash(selected)
        }

    }
}
