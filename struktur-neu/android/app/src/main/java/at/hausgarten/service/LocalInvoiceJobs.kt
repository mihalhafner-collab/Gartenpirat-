package at.hausgarten.service

import android.app.job.*
import android.content.*
import android.util.AtomicFile
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import org.json.*

/** Local PDF outbox; one immutable frozen snapshot survives process death and retry. */
internal object LocalInvoiceJobs {
    @JvmField val JOB_ID: Int = 0x484708
    private val LOCK: ReentrantLock = ReentrantLock()

    @JvmStatic
    fun storageScope(context: Context): String {
        try {
            return PixelMcpWriteTools.hash((context!!.filesDir)!!.canonicalPath)
        } catch (invalid: Exception) {
            throw IllegalStateException("INVOICE_JOB_SCOPE_UNAVAILABLE", invalid)
        }
    }

    @JvmStatic
    fun schedule(context: Context): Unit {
        if (LocalAuthorityGuard.local(context)) {
            try {
                var guard: LocalAuthorityGuard = LocalAuthorityGuard(context)
                var authority = guard!!.current()
                if (
                    ((("LOCAL_ACTIVE")!!.equals(authority!!.optString("mode")) &&
                        ("RECONCILED")!!.equals(authority!!.optString("invoice_authority"))) &&
                        !LocalAuthorityGuard.restorePending(context))
                ) {
                    run {
                        PixelLocalStore(context).use { store ->
                            var counter = store!!.ownerValue("local_invoice_counter", "")
                            if (!counter!!.isEmpty()) {
                                guard!!.advanceInvoiceHighWater(
                                    (JSONObject(counter))!!.getInt("last_number")
                                )
                            }
                        }
                    }
                }
            } catch (pending: Exception) {
                throw IllegalStateException("INVOICE_AUTHORITY_CHECKPOINT_PENDING", pending)
            }
        }
        var jobs = context!!.getSystemService(JobScheduler::class.java)
        if ((jobs !== null)) {
            var extras: android.os.PersistableBundle = android.os.PersistableBundle()
            extras!!.putString("storage_scope", storageScope(context))
            jobs!!.schedule(
                ((((JobInfo.Builder(
                                    JOB_ID,
                                    ComponentName(context, LocalInvoiceJobService::class.java),
                                ))!!
                                .setExtras(extras))!!
                            .setPersisted(true))!!
                        .setMinimumLatency(0))!!
                    .build()
            )
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun run(context: Context, store: PixelLocalStore): JSONObject {
        if (!LOCK!!.tryLock()) {
            return (JSONObject())!!.put("busy", true)
        }
        var completed: Int = 0
        var failed: Int = 0
        try {
            var keys: ArrayList<String> = ArrayList()
            run {
                (store!!.getReadableDatabase())!!
                    .rawQuery(
                        "SELECT key FROM owner_state WHERE key LIKE 'local_invoice_job:%' ORDER BY rowid",
                        null,
                    )
                    .use { rows ->
                        while (rows!!.moveToNext()) {
                            keys!!.add(rows!!.getString(0))
                        }
                    }
            }
            for (key in keys!!) {
                EncryptedBackupArchive.checkCancelled()
                var job: JSONObject = JSONObject(store!!.ownerValue(key, "{}"))
                var frozen = job!!.getJSONObject("snapshot")
                if (
                    !(PixelMcpWriteTools.hash(frozen))!!.equals(job!!.getString("snapshot_sha256"))
                ) {
                    throw IOException("INVOICE_FROZEN_SNAPSHOT_CHANGED")
                }
                if ((job!!.optString("phase"))!!.equals("READY")) {
                    try {
                        verifyReady(
                            context,
                            job,
                            PixelMcpWriteTools.find(
                                (SnapshotProjection.effective(store!!.getState()))!!.getJSONArray(
                                    "invoices"
                                ),
                                job!!.getString("invoice_id"),
                            ),
                        )
                    } catch (changed: Exception) {
                        (job!!.put("phase", "ORIGINAL_REVIEW_REQUIRED"))!!.put(
                            "last_error_code",
                            "INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED",
                        )
                        store!!.putOwnerValue(key, job!!.toString())
                        failed++
                    }
                    continue
                }
                if (
                    ((job!!.optString("phase"))!!.equals("ORIGINAL_REVIEW_REQUIRED") ||
                        (job!!.optInt("attempts") >= 8))
                ) {
                    continue
                }
                (job!!.put("attempts", (job!!.optInt("attempts") + 1)))!!.put(
                    "phase",
                    "PDF_PENDING",
                )
                store!!.putOwnerValue(key, job!!.toString())
                try {
                    var metadata = document(context, job)
                    var db = store!!.getWritableDatabase()
                    store!!.beginBusinessTransaction(db)
                    try {
                        var state = store!!.getState()
                        var all: JSONObject =
                            JSONObject((state!!.getJSONObject("master"))!!.toString())
                        DriveJournalRules.apply(all, state!!.getJSONArray("pending"))
                        var invoice =
                            PixelMcpWriteTools.find(
                                all!!.getJSONArray("invoices"),
                                frozen!!.getString("id"),
                            )
                        if (!sameFrozenInvoice(invoice, frozen)) {
                            throw IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH")
                        }
                        run {
                            var it = metadata!!.keys()
                            while (it!!.hasNext()) {
                                var field = it!!.next()
                                invoice!!.put(field, metadata!!.get(field))
                            }
                        }
                        invoice!!.put("pdf_state", "READY")
                        OwnerJournalRules.replace(
                            all,
                            "invoices",
                            invoice!!.getString("id"),
                            invoice,
                        )
                        store!!.commitLocalSnapshot(all)
                        (((job!!.put("phase", "READY"))!!.put("document", metadata))!!.put(
                                "verified_at",
                                (java.time.Instant.now())!!.toString(),
                            ))!!
                            .remove("last_error_code")
                        store!!.putOwnerValue(key, job!!.toString())
                        db!!.setTransactionSuccessful()
                        completed++
                    } finally {
                        db!!.endTransaction()
                    }
                } catch (error: Exception) {
                    if (
                        ((Thread.currentThread())!!.isInterrupted ||
                            (error is InterruptedIOException))
                    ) {
                        throw error
                    }
                    var originalChanged: Boolean =
                        ((error is IOException) &&
                            ("INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED")!!.equals(
                                error!!.message
                            ))
                    var code =
                        (if (originalChanged) "INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED"
                        else
                            (if (
                                ((error is IOException) &&
                                    (Arrays.asList(
                                            "INVOICE_DOCUMENT_RECEIPT_INVALID",
                                            "INVOICE_ORIGINAL_RECONCILIATION_REQUIRED",
                                        ))!!
                                        .contains(error!!.message))
                            )
                                error!!.message
                            else "INVOICE_PDF_CREATION_OR_STORAGE_FAILED"))
                    (job!!.put(
                            "phase",
                            (if (originalChanged) "ORIGINAL_REVIEW_REQUIRED" else "PDF_PENDING"),
                        ))!!
                        .put("last_error_code", code)
                    store!!.putOwnerValue(key, job!!.toString())
                    failed++
                }
            }
            return ((JSONObject())!!.put("completed", completed))!!.put("failed", failed)
        } finally {
            LOCK!!.unlock()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun verifiedOriginal(context: Context, metadata: JSONObject): Unit {
        try {
            (BusinessDocumentStore(context))!!.read(metadata)
        } catch (invalid: Exception) {
            throw IOException("INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED")
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun verifyReady(context: Context, job: JSONObject, invoice: JSONObject): Unit {
        if (!(job!!.optString("phase"))!!.equals("READY")) {
            throw IOException("INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED")
        }
        var frozen = job!!.getJSONObject("snapshot")
        var metadata = job!!.getJSONObject("document")
        if (
            (!(PixelMcpWriteTools.hash(frozen))!!.equals(job!!.optString("snapshot_sha256")) ||
                !sameFrozenInvoice(invoice, frozen))
        ) {
            throw IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH")
        }
        if (!sameDocument(metadata, invoice)) {
            throw IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH")
        }
        var receipt = readReceipt(context, job)
        if (((receipt !== null) && !sameDocument(receipt, metadata))) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        verifiedOriginal(context, metadata)
    }

    private @JvmStatic @Throws(Exception::class) fun sameDocument(
        first: JSONObject,
        second: JSONObject,
    ): Boolean {
        for (field in
            (arrayOf("document_id", "pdf_sha256", "pdf_storage_name", "pdf_byte_size"))!!) {
            if (
                ((!first!!.has(field) || !second!!.has(field)) ||
                    !((first!!.get(field))!!.toString())!!.equals(
                        (second!!.get(field))!!.toString()
                    ))
            ) {
                return false
            }
        }
        return true
    }

    private @JvmStatic @Throws(Exception::class) fun sameFrozenInvoice(
        invoice: JSONObject,
        frozen: JSONObject,
    ): Boolean {
        return LocalFinancialValidation.sameFrozenInvoice(invoice, frozen)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun hasAnyReceipt(context: Context, job: JSONObject): Boolean {
        var file = receiptFile(context, job)
        return ((exists(file!!.baseFile) ||
            exists(File(((file!!.baseFile).toString() + ".bak")))) ||
            exists(File(((file!!.baseFile).toString() + ".new"))))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun hasOriginalEvidence(context: Context, job: JSONObject): Boolean {
        return (job!!.has("document") || hasAnyReceipt(context, job))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun prepareVerifiedOriginal(
        context: Context,
        job: JSONObject,
        supplied: JSONObject,
        fullEvidenceHash: String?,
    ): JSONObject {
        LOCK!!.lock()
        try {
            requireFrozen(job)
            if (
                ((fullEvidenceHash === null) || !fullEvidenceHash!!.matches(Regex("[0-9a-f]{64}")))
            ) {
                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
            }
            var metadata = metadata(job, supplied)
            verifiedOriginal(context, metadata)
            var previous = readReceipt(context, job)
            if ((previous !== null)) {
                if (!sameDocument(previous, metadata)) {
                    throw IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH")
                }
                return JSONObject(previous!!.toString())
            }
            if (job!!.has("document")) {
                var produced = job!!.optJSONObject("document")
                if ((produced !== null)) {
                    produced = metadata(job, produced)
                }
                if (((produced === null) || !sameDocument(produced, metadata))) {
                    throw IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH")
                }
                verifiedOriginal(context, produced)
            }
            writeReceipt(context, job, metadata, fullEvidenceHash)
            return metadata
        } finally {
            LOCK!!.unlock()
        }
    }

    private @JvmStatic fun exists(file: File): Boolean {
        return (file!!.exists() || java.nio.file.Files.isSymbolicLink(file!!.toPath()))
    }

    private @JvmStatic @Throws(Exception::class) fun receiptFile(
        context: Context,
        job: JSONObject,
    ): AtomicFile {
        var sha = job!!.getString("snapshot_sha256")
        if (!sha!!.matches(Regex("[0-9a-f]{64}"))) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        var directory: File = File(context!!.filesDir, "invoice-document-receipts")
        var file: File = File(directory, (sha.toString() + ".json"))
        if (
            (((java.nio.file.Files.isSymbolicLink(directory!!.toPath()) ||
                java.nio.file.Files.isSymbolicLink(file!!.toPath())) ||
                java.nio.file.Files.isSymbolicLink(
                    (File((file.toString() + ".bak")))!!.toPath()
                )) ||
                java.nio.file.Files.isSymbolicLink((File((file.toString() + ".new")))!!.toPath()))
        ) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        return AtomicFile(file)
    }

    private @JvmStatic @Throws(Exception::class) fun requireFrozen(job: JSONObject): Unit {
        var frozen = job!!.getJSONObject("snapshot")
        if (
            (((!("1")!!.equals(java.lang.String.valueOf(job!!.opt("formatVersion"))) ||
                !(PixelMcpWriteTools.hash(frozen))!!.equals(job!!.optString("snapshot_sha256"))) ||
                !(frozen!!.optString("id"))!!.equals(job!!.optString("invoice_id"))) ||
                (frozen!!.optString("invoice_number"))!!.isEmpty())
        ) {
            throw IOException("INVOICE_FROZEN_SNAPSHOT_CHANGED")
        }
    }

    private @JvmStatic @Throws(Exception::class) fun metadata(
        job: JSONObject,
        supplied: JSONObject,
    ): JSONObject {
        run {
            var fields = supplied!!.keys()
            while (fields!!.hasNext()) {
                if (
                    !(Arrays.asList(
                            "document_id",
                            "pdf_sha256",
                            "pdf_storage_name",
                            "pdf_byte_size",
                        ))!!
                        .contains(fields!!.next())
                ) {
                    throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
                }
            }
        }
        var id = job!!.getString("invoice_id")
        var sha = supplied!!.getString("pdf_sha256")
        var size: Long
        try {
            size =
                (java.math.BigDecimal((supplied!!.get("pdf_byte_size"))!!.toString()))!!
                    .longValueExact()
        } catch (invalid: Exception) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        if (
            (((!sha!!.matches(Regex("[0-9a-f]{64}")) ||
                !(supplied!!.optString("document_id"))!!.equals(("invoice:" + id))) ||
                !(supplied!!.optString("pdf_storage_name"))!!.equals((sha.toString() + ".pdf"))) ||
                (size < 5))
        ) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        return ((((JSONObject())!!.put("document_id", ("invoice:" + id)))!!.put(
                    "pdf_sha256",
                    sha,
                ))!!
                .put("pdf_storage_name", (sha.toString() + ".pdf")))!!
            .put("pdf_byte_size", size)
    }

    private @JvmStatic @Throws(Exception::class) fun requireCreationAllowed(
        context: Context,
        job: JSONObject,
    ): Unit {
        LocalInvoiceCreationRules.requireAllowed(
            job,
            (BackupDeviceGuard(context))!!.current(),
            (LocalAuthorityGuard(context))!!.current(),
            LocalAuthorityGuard.restorePending(context),
        )
    }

    private @JvmStatic @Throws(Exception::class) fun document(
        context: Context,
        job: JSONObject,
    ): JSONObject {
        requireFrozen(job)
        var prior = readReceipt(context, job)
        if ((prior !== null)) {
            if (
                (job!!.has("document") &&
                    ((job!!.optJSONObject("document") === null) ||
                        !sameDocument(prior, job!!.getJSONObject("document"))))
            ) {
                throw IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH")
            }
            return prior
        }
        var supplied = job!!.optJSONObject("document")
        if ((job!!.has("document") && (supplied === null))) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        if ((supplied === null)) {
            requireCreationAllowed(context, job)
            supplied =
                (BusinessDocumentStore(context))!!.createInvoice(job!!.getJSONObject("snapshot"))
        }
        var metadata = metadata(job, supplied)
        verifiedOriginal(context, metadata)
        writeReceipt(context, job, metadata, null)
        return metadata
    }

    private @JvmStatic @Throws(Exception::class) fun writeReceipt(
        context: Context,
        job: JSONObject,
        metadata: JSONObject,
        fullEvidenceHash: String?,
    ): Unit {
        var file = receiptFile(context, job)
        if (hasAnyReceipt(context, job)) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        var receipt: JSONObject =
            ((((((JSONObject())!!.put("formatVersion", 1))!!.put(
                                "invoice_id",
                                job!!.getString("invoice_id"),
                            ))!!
                            .put(
                                "invoice_number",
                                (job!!.getJSONObject("snapshot"))!!.getString("invoice_number"),
                            ))!!
                        .put("snapshot_sha256", job!!.getString("snapshot_sha256")))!!
                    .put("document", metadata))!!
                .put("verified_at", (java.time.Instant.now())!!.toString())
        if ((fullEvidenceHash !== null)) {
            receipt!!.put("recovered_from_full_evidence_sha256", fullEvidenceHash)
        }
        var directory = (file!!.baseFile)!!.parentFile
        if ((!directory!!.isDirectory() && !directory!!.mkdirs())) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_STORAGE_UNAVAILABLE")
        }
        var out: FileOutputStream? = null
        try {
            out = file!!.startWrite()
            out!!.write((receipt!!.toString())!!.toByteArray(StandardCharsets.UTF_8))
            file!!.finishWrite(out)
            out = null
        } catch (failure: Exception) {
            if ((out !== null)) {
                file!!.failWrite(out)
            }
            throw IOException("INVOICE_DOCUMENT_RECEIPT_STORAGE_UNAVAILABLE")
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun verifiedReceiptEvidenceHash(context: Context, job: JSONObject): String {
        LOCK!!.lock()
        try {
            var receipt = readReceiptRecord(context, job)
            if ((receipt === null)) {
                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
            }
            return receipt!!.optString("recovered_from_full_evidence_sha256")
        } finally {
            LOCK!!.unlock()
        }
    }

    private @JvmStatic @Throws(Exception::class) fun readReceipt(
        context: Context,
        job: JSONObject,
    ): JSONObject? {
        var receipt = readReceiptRecord(context, job)
        return (if ((receipt === null)) null else receipt!!.getJSONObject("document"))
    }

    private @JvmStatic @Throws(Exception::class) fun readReceiptRecord(
        context: Context,
        job: JSONObject,
    ): JSONObject? {
        var sha = job!!.getString("snapshot_sha256")
        var file = receiptFile(context, job)
        if (
            ((file!!.baseFile)!!.exists() ||
                (File(((file!!.baseFile).toString() + ".bak")))!!.exists())
        ) {
            var receipt: JSONObject? = null
            try {
                file!!.openRead().use { `in` ->
                    ByteArrayOutputStream().use { out ->
                        var buffer: ByteArray = ByteArray(4096)
                        var n: Int
                        while (
                            (run {
                                n = (`in`)!!.read(buffer)
                                n
                            } != -1)
                        ) {
                            if (((out!!.size() + n) > 65536)) {
                                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
                            }
                            out!!.write(buffer, 0, n)
                        }
                        receipt = JSONObject(String(out!!.toByteArray(), StandardCharsets.UTF_8))
                    }
                }
            } catch (invalid: Exception) {
                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
            }
            if (
                (((!("1")!!.equals(java.lang.String.valueOf(receipt!!.opt("formatVersion"))) ||
                    !sha!!.equals(receipt!!.optString("snapshot_sha256"))) ||
                    !(job!!.getString("invoice_id"))!!.equals(receipt!!.optString("invoice_id"))) ||
                    !((job!!.getJSONObject("snapshot"))!!.getString("invoice_number"))!!.equals(
                        receipt!!.optString("invoice_number")
                    ))
            ) {
                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
            }
            var metadata: JSONObject? = null
            try {
                metadata = metadata(job, receipt!!.getJSONObject("document"))
            } catch (invalid: Exception) {
                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
            }
            verifiedOriginal(context, metadata)
            if (
                (receipt!!.has("recovered_from_full_evidence_sha256") &&
                    !(receipt!!.optString("recovered_from_full_evidence_sha256"))!!.matches(
                        Regex("[0-9a-f]{64}")
                    ))
            ) {
                throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
            }
            receipt!!.put("document", metadata)
            return receipt
        }
        if (exists(File(((file!!.baseFile).toString() + ".new")))) {
            throw IOException("INVOICE_DOCUMENT_RECEIPT_INVALID")
        }
        return null
    }
}
