package at.hausgarten.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.*
import java.security.*
import java.time.Instant
import java.util.*
import org.json.*

internal class LocalAuthorityEvidence {
    class Loaded {
        @JvmField val sha256: String
        @JvmField val reference: String
        @JvmField val sourceCheckedAt: String
        private val source: JSONObject
        private val preparedSummary: JSONObject

        constructor(hash: String, source: JSONObject) {
            this.sha256 = hash
            reference = ("sha256:" + hash)
            this.source = source
            sourceCheckedAt = source.optString("checked_at")
            preparedSummary = buildSummary()
        }

        @Throws(Exception::class)
        fun writer(checkedAt: String): JSONObject {

            Instant.parse(checkedAt)
            return JSONObject()
                .put("formatVersion", 1)
                .put("external_writers_disabled", true)
                .put("evidence_sha256", sha256)
                .put("evidence_reference", reference)
                .put("checked_at", checkedAt)
        }

        @Throws(Exception::class)
        fun ledger(checkedAt: String): JSONObject {

            var result = normalizeLedger(source)
            var evidence = writer(checkedAt)
            run {
                var keys = evidence.keys()
                var firstIteration35 = true
                while (true) {
                    if (firstIteration35) firstIteration35 = false else {}
                    if (!(keys.hasNext())) break

                    var key = keys.next()
                    result.put(key, evidence.get(key))
                }
            }
            return result
        }

        @Throws(Exception::class)
        fun reviewedLedger(
            reviewedAt: String,
            authority: JSONObject?,
            explicitlyConfirmed: Boolean,
        ): JSONObject {

            return reviewedLedger(reviewedAt, authority, explicitlyConfirmed, false)
        }

        @Throws(Exception::class)
        fun reviewedLedger(
            reviewedAt: String,
            authority: JSONObject?,
            explicitlyConfirmed: Boolean,
            notCreatedExplicitlyConfirmed: Boolean,
        ): JSONObject {

            if (
                (((!(explicitlyConfirmed) || (authority == null)) ||
                    authority.optString("installation_id").isEmpty()) ||
                    authority.optString("restore_epoch").isEmpty())
            ) {
                throw IOException("INVOICE_CURRENT_REVIEW_REQUIRED")
            }
            var review = Instant.parse(reviewedAt)
            if (review.isAfter(Instant.now().plusSeconds(2))) {
                throw IOException("INVOICE_CURRENT_REVIEW_REQUIRED")
            }
            for (key in arrayOf("created_at", "blocked_at")) {
                if (
                    (!(authority.optString(key).isEmpty()) &&
                        review.isBefore(Instant.parse(authority.getString(key))))
                ) {
                    throw IOException("INVOICE_CURRENT_REVIEW_REQUIRED")
                }
            }
            if (
                ((summary().optInt("pending_not_created_count") > 0) &&
                    !(notCreatedExplicitlyConfirmed))
            ) {
                throw IOException("INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED")
            }
            return ledger(reviewedAt)
                .put(
                    "current_review",
                    JSONObject()
                        .put("confirmed", true)
                        .put("installation_id", authority.getString("installation_id"))
                        .put("restore_epoch", authority.getString("restore_epoch"))
                        .put("reviewed_at", reviewedAt)
                        .put("evidence_sha256", sha256),
                )
        }

        @Throws(Exception::class)
        fun summary(): JSONObject {

            return JSONObject(preparedSummary.toString())
        }

        private @Throws(Exception::class) fun buildSummary(): JSONObject {

            var result =
                JSONObject().put("sha256", sha256).put("source_checked_at", sourceCheckedAt)
            var entries = source.optJSONArray("issued")
            var original: Int = 0
            var notCreated: Int = 0
            var unresolved: Int = 0
            var unbound: Int = 0
            run {
                var i: Int = 0
                var firstIteration36 = true
                while (true) {
                    if (firstIteration36) firstIteration36 = false
                    else {
                        i++
                    }
                    if (!(((entries != null) && (i < entries.length())))) break

                    try {

                        var row = normalizeIssued(entries.getJSONObject(i))
                        if ((row.getString("pdf_resolution") == "not_created")) {
                            notCreated++
                        } else {
                            original++
                            if (!(row.has("snapshot_sha256"))) {
                                unbound++
                            }
                        }
                    } catch (invalid: Exception) {
                        unresolved++
                    }
                }
            }
            result
                .put("issued_count", (if ((entries == null)) 0 else entries.length()))
                .put("original_reference_count", original)
                .put("pending_not_created_count", notCreated)
                .put("unresolved_pdf_count", unresolved)
                .put("snapshot_binding_missing_count", unbound)
            try {

                var l = normalizeLedger(source)
                result
                    .put("ledger_valid", true)
                    .put("last_number", l.getInt("last_number"))
                    .put("reservation_count", l.getJSONArray("reservations").length())
                    .put(
                        "opening_balance_count",
                        (if ((l.optJSONArray("opening_balances") == null)) 0
                        else l.getJSONArray("opening_balances").length()),
                    )
            } catch (invalid: Exception) {
                result.put("ledger_valid", false)
            }
            return result
        }
    }

    private class Directory {
        @JvmField val id: String
        @JvmField val depth: Int

        constructor(id: String, depth: Int) {
            this.id = id
            this.depth = depth
        }
    }

    constructor() {}

    companion object {
        @JvmField val MAX_JSON_BYTES: Int = ((8 * 1024) * 1024)
        @JvmField val MAX_PDF_BYTES: Int = ((20 * 1024) * 1024)

        @JvmStatic
        @Throws(Exception::class)
        fun read(context: Context, uri: Uri): Loaded {

            requireContent(uri)
            context.getContentResolver().openInputStream(uri).use { input ->
                if ((input == null)) {
                    throw IOException("LOCAL_EVIDENCE_READ_FAILED")
                }
                return parse(input)
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun parse(input: InputStream): Loaded {

            var raw = bounded(input, MAX_JSON_BYTES)
            var text =
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString()
            var parser = JSONTokener(text)
            var value = parser.nextValue()
            if ((!((value is JSONObject)) || (parser.nextClean() != '\u0000'))) {
                throw IOException("LOCAL_EVIDENCE_JSON_INVALID")
            }
            var source = (value as JSONObject)
            if ((integer(source, "formatVersion", 1, 1) != 1L)) {
                throw IOException("LOCAL_EVIDENCE_VERSION_UNSUPPORTED")
            }
            if (source.has("checked_at")) {
                try {

                    Instant.parse(source.getString("checked_at"))
                } catch (invalid: Exception) {
                    throw IOException("LOCAL_EVIDENCE_JSON_INVALID")
                }
            }
            return Loaded(PhotoAttachmentStore.sha256(raw), source)
        }

        private val DRAFT_LOCK: Any = Any()

        @JvmStatic
        @Throws(Exception::class)
        fun savePending(
            context: Context,
            name: String,
            body: String,
            uri: String,
            hash: String,
        ): Unit {

            synchronized(DRAFT_LOCK) {
                if (
                    !(Arrays.asList("activate_local_authority", "reconcile_local_invoice_authority")
                        .contains(name))
                ) {
                    throw IOException("LOCAL_AUTHORITY_DRAFT_INVALID")
                }
                JSONObject(body)
                var record =
                    JSONObject()
                        .put("formatVersion", 1)
                        .put("context", BackupDeviceGuard(context).current())
                        .put("pending_name", name)
                        .put("pending_body", body)
                        .put("evidence_uri", uri)
                        .put("evidence_hash", hash)
                var target = draft(context)
                var bytes = record.toString().toByteArray(StandardCharsets.UTF_8)
                if ((bytes.size > ((16 * 1024) * 1024))) {
                    throw IOException("LOCAL_AUTHORITY_DRAFT_TOO_LARGE")
                }
                var output: FileOutputStream? = null
                try {

                    output = target.startWrite()
                    output.write(bytes)
                    target.finishWrite(output)
                    output = null
                } catch (failed: Exception) {
                    if ((output != null)) {
                        target.failWrite(output)
                    }
                    throw failed
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun loadPending(context: Context): JSONObject {

            synchronized(DRAFT_LOCK) {
                var target = draft(context)
                if (
                    (!(target.getBaseFile().exists()) &&
                        !(File((java.lang.String.valueOf(target.getBaseFile()) + ".bak")).exists()))
                ) {
                    return JSONObject()
                }
                var record: JSONObject
                target.openRead().use { input ->
                    record =
                        JSONObject(
                            String(bounded(input, ((16 * 1024) * 1024)), StandardCharsets.UTF_8)
                        )
                }
                if ((record.optInt("formatVersion") != 1)) {
                    throw IOException("LOCAL_AUTHORITY_DRAFT_INVALID")
                }
                var current = BackupDeviceGuard(context).current()
                var saved = record.getJSONObject("context")
                if (
                    (!((current.getString("installationId") ==
                        saved.optString("installationId"))) ||
                        !((current.getString("restoreEpoch") == saved.optString("restoreEpoch"))))
                ) {
                    return JSONObject().put("stale_context", true)
                }
                if (
                    !(Arrays.asList("activate_local_authority", "reconcile_local_invoice_authority")
                        .contains(record.optString("pending_name")))
                ) {
                    throw IOException("LOCAL_AUTHORITY_DRAFT_INVALID")
                }
                JSONObject(record.getString("pending_body"))
                return record
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun clearPending(context: Context, expectedBody: String): Unit {

            synchronized(DRAFT_LOCK) {
                var record = loadPending(context)
                if ((expectedBody == record.optString("pending_body"))) {
                    draft(context).delete()
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun draft(context: Context): AtomicFile {

            var folder = File(context.getNoBackupFilesDir(), "local-authority-review")
            if (java.nio.file.Files.isSymbolicLink(folder.toPath())) {
                throw IOException("LOCAL_AUTHORITY_DRAFT_INVALID")
            }
            if ((!(folder.isDirectory()) && !(folder.mkdirs()))) {
                throw IOException("LOCAL_AUTHORITY_DRAFT_WRITE_FAILED")
            }
            var file = File(folder, "request.v1")
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
                throw IOException("LOCAL_AUTHORITY_DRAFT_INVALID")
            }
            return AtomicFile(file)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun normalizeLedger(source: JSONObject): JSONObject {

            integer(source, "formatVersion", 1, 1)
            var last: Long = integer(source, "last_number", 0, 1000000000)
            var input = source.getJSONArray("issued")
            var reservations = source.getJSONArray("reservations")
            if (((input.length() > 100000) || (reservations.length() > 10000))) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            var issued = JSONArray()
            var reserved = JSONArray()
            var ids = HashSet<String>()
            var numbers = HashSet<String>()
            var reservationIds = HashSet<String>()
            run {
                var i: Int = 0
                var firstIteration37 = true
                while (true) {
                    if (firstIteration37) firstIteration37 = false
                    else {
                        i++
                    }
                    if (!((i < input.length()))) break

                    var row = normalizeIssued(input.getJSONObject(i))
                    if (
                        (!(ids.add(row.getString("invoice_id"))) ||
                            !(numbers.add(row.getString("invoice_number"))))
                    ) {
                        throw IOException("INVOICE_EVIDENCE_DUPLICATE")
                    }
                    issued.put(row)
                }
            }
            run {
                var i: Int = 0
                var firstIteration38 = true
                while (true) {
                    if (firstIteration38) firstIteration38 = false
                    else {
                        i++
                    }
                    if (!((i < reservations.length()))) break

                    var row = reservations.getJSONObject(i)
                    var id = id(row, "reservation_id")
                    var state = row.getString("state")
                    if (
                        (!(reservationIds.add(id)) ||
                            !(Arrays.asList("reserved", "issued", "cancelled").contains(state)))
                    ) {
                        throw IOException("INVOICE_RESERVATION_REVIEW_REQUIRED")
                    }
                    reserved.put(
                        JSONObject()
                            .put("reservation_id", id)
                            .put("invoice_id", id(row, "invoice_id"))
                            .put("invoice_number", nonempty(row, "invoice_number", 500))
                            .put("counter", integer(row, "counter", 0, last))
                            .put("state", state)
                    )
                }
            }
            var result =
                JSONObject()
                    .put("formatVersion", 1)
                    .put("last_number", last)
                    .put("issued", issued)
                    .put("reservations", reserved)
            var balances = source.optJSONArray("opening_balances")
            if ((source.has("opening_balances") && (balances == null))) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            if ((balances != null)) {
                if ((balances.length() > 100000)) {
                    throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
                }
                var output = JSONArray()
                var unique = HashSet<String>()
                run {
                    var i: Int = 0
                    var firstIteration39 = true
                    while (true) {
                        if (firstIteration39) firstIteration39 = false
                        else {
                            i++
                        }
                        if (!((i < balances.length()))) break

                        var row = balances.getJSONObject(i)
                        var id = id(row, "invoice_id")
                        if ((!(ids.contains(id)) || !(unique.add(id)))) {
                            throw IOException("INVOICE_OPENING_BALANCE_INVALID")
                        }
                        output.put(
                            JSONObject()
                                .put("invoice_id", id)
                                .put(
                                    "remaining_minor",
                                    integer(row, "remaining_minor", 0, Long.MAX_VALUE),
                                )
                        )
                    }
                }
                result.put("opening_balances", output)
            }
            return result
        }

        private @JvmStatic @Throws(Exception::class) fun normalizeIssued(
            row: JSONObject
        ): JSONObject {

            var resolution =
                (if (row.has("pdf_resolution")) nonempty(row, "pdf_resolution", 20) else "original")
            if (!(Arrays.asList("original", "not_created").contains(resolution))) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            var copy =
                JSONObject()
                    .put("invoice_id", id(row, "invoice_id"))
                    .put("invoice_number", nonempty(row, "invoice_number", 500))
                    .put("pdf_resolution", resolution)
            if (row.has("snapshot_sha256")) {
                copy.put("snapshot_sha256", hash(row, "snapshot_sha256"))
            }
            if ((resolution == "not_created")) {
                if (
                    (!((true == row.opt("not_created_confirmed"))) ||
                        !(copy.has("snapshot_sha256")))
                ) {
                    throw IOException("INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED")
                }
                for (field in
                    arrayOf(
                        "pdf_sha256",
                        "pdf_byte_size",
                        "pdf_storage_name",
                        "pdf_base64",
                        "document_id",
                        "document",
                    )) {
                    if (row.has(field)) {
                        throw IOException("INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED")
                    }
                }
                copy
                    .put("not_created_confirmed", true)
                    .put("not_created_reason", nonempty(row, "not_created_reason", 4000))
            } else {
                if ((row.has("not_created_confirmed") || row.has("not_created_reason"))) {
                    throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
                }
                copy.put("pdf_sha256", hash(row, "pdf_sha256"))
                if (row.has("pdf_byte_size")) {
                    copy.put(
                        "pdf_byte_size",
                        integer(row, "pdf_byte_size", 5, MAX_PDF_BYTES.toLong()),
                    )
                }
            }
            return copy
        }

        @JvmStatic
        @Throws(Exception::class)
        fun importOriginals(
            context: Context,
            tree: Uri?,
            ledger: JSONObject,
            requestId: String?,
        ): JSONObject {

            if (((requestId == null) || !(requestId.matches(Regex("[A-Za-z0-9_-]{8,200}"))))) {
                throw IOException("LOCAL_AUTHORITY_REQUEST_ID_REQUIRED")
            }
            var receipt = ("local_authority_original_import:" + PixelMcpWriteTools.hash(requestId))
            var fingerprint =
                PixelMcpWriteTools.hash(
                    JSONObject()
                        .put("request_id", requestId)
                        .put("tree", (if ((tree == null)) "" else tree.toString()))
                        .put("ledger", normalizeLedger(ledger))
                        .put("context", BackupDeviceGuard(context).current())
                )
            PixelLocalStore(context).use { store ->
                var raw = store.ownerValue(receipt, "")
                if (!(raw.isEmpty())) {
                    var prior = JSONObject(raw)
                    if (!((fingerprint == prior.getString("fingerprint")))) {
                        throw IOException("LOCAL_AUTHORITY_IMPORT_REPLAY_MISMATCH")
                    }
                    return JSONObject(prior.getJSONObject("result").toString())
                        .put("replayed", true)
                }
                var result =
                    importOriginalFiles(context, tree, ledger)
                        .put("request_id", requestId)
                        .put("replayed", false)
                var database = store.getWritableDatabase()
                database.beginTransaction()
                try {

                    raw = store.ownerValue(receipt, "")
                    if (!(raw.isEmpty())) {
                        var prior = JSONObject(raw)
                        if (!((fingerprint == prior.getString("fingerprint")))) {
                            throw IOException("LOCAL_AUTHORITY_IMPORT_REPLAY_MISMATCH")
                        }
                        result = prior.getJSONObject("result").put("replayed", true)
                    } else {
                        store.putOwnerValue(
                            receipt,
                            JSONObject()
                                .put("fingerprint", fingerprint)
                                .put("result", result)
                                .toString(),
                        )
                    }
                    database.setTransactionSuccessful()
                    return result
                } finally {
                    database.endTransaction()
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun importOriginalFiles(
            context: Context,
            tree: Uri?,
            ledger: JSONObject,
        ): JSONObject {

            requireContent(tree)
            if (!(DocumentsContract.isTreeUri(tree))) {
                throw IOException("DOCUMENT_TREE_REQUIRED")
            }
            var normalized = normalizeLedger(ledger)
            var wanted = HashMap<String, Long>()
            var issued = normalized.getJSONArray("issued")
            run {
                var i: Int = 0
                var firstIteration40 = true
                while (true) {
                    if (firstIteration40) firstIteration40 = false
                    else {
                        i++
                    }
                    if (!((i < issued.length()))) break

                    var row = issued.getJSONObject(i)
                    if ((row.getString("pdf_resolution") == "not_created")) {
                        continue
                    }
                    var sha = row.getString("pdf_sha256")
                    var size: Long = row.optLong("pdf_byte_size", -(1))
                    var prior = wanted.get(sha)
                    if (
                        ((((prior != null) && (size != -1L)) && (prior != -1L)) && (prior != size))
                    ) {
                        throw IOException("INVOICE_EVIDENCE_MISMATCH")
                    }
                    wanted.put(sha, (if (((prior != null) && (size == -1L))) prior else size))
                }
            }
            var found = HashSet<String>()
            var pending = ArrayDeque<Directory>()
            pending.add(Directory(DocumentsContract.getTreeDocumentId(tree), 0))
            var seen = HashSet<String>()
            var scanned: Int = 0
            var stored: Int = 0
            var oversized: Int = 0
            while (!(pending.isEmpty())) {
                if (Thread.currentThread().isInterrupted()) {
                    throw InterruptedIOException("LOCAL_EVIDENCE_IMPORT_INTERRUPTED")
                }
                var directory = pending.removeFirst()
                if (!(seen.add(directory.id))) {
                    continue
                }
                if (((directory.depth > 32) || (seen.size > 5000))) {
                    throw IOException("LOCAL_EVIDENCE_TREE_LIMIT")
                }
                var children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.id)
                context
                    .getContentResolver()
                    .query(
                        children,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                        ),
                        null,
                        null,
                        null,
                    )
                    .use { rows ->
                        if ((rows == null)) {
                            throw IOException("LOCAL_EVIDENCE_READ_FAILED")
                        }
                        while (rows.moveToNext()) {
                            if ((++scanned > 50000)) {
                                throw IOException("LOCAL_EVIDENCE_TREE_LIMIT")
                            }
                            var documentId = rows.getString(0)
                            var mime = rows.getString(1)
                            if ((DocumentsContract.Document.MIME_TYPE_DIR == mime)) {
                                if ((pending.size >= 5000)) {
                                    throw IOException("LOCAL_EVIDENCE_TREE_LIMIT")
                                }
                                pending.add(Directory(documentId, (directory.depth + 1)))
                                continue
                            }
                            if (!(("application/pdf" == mime))) {
                                continue
                            }
                            if ((!(rows.isNull(2)) && (rows.getLong(2) > MAX_PDF_BYTES))) {
                                oversized++
                                continue
                            }
                            var raw: ByteArray
                            context
                                .getContentResolver()
                                .openInputStream(
                                    DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                                )
                                .use { input ->
                                    if ((input == null)) {
                                        throw IOException("LOCAL_EVIDENCE_READ_FAILED")
                                    }
                                    try {

                                        raw = bounded(input, MAX_PDF_BYTES)
                                    } catch (tooLarge: IOException) {
                                        if (("LOCAL_EVIDENCE_FILE_TOO_LARGE" == tooLarge.message)) {
                                            oversized++
                                            continue
                                        }
                                        throw tooLarge
                                    }
                                }
                            try {

                                var sha = PhotoAttachmentStore.sha256(raw)
                                var expected = wanted.get(sha)
                                if ((expected == null)) {
                                    continue
                                }
                                if (((expected != -1L) && (expected != raw.size.toLong()))) {
                                    throw IOException("INVOICE_EVIDENCE_MISMATCH")
                                }
                                var meta =
                                    JSONObject()
                                        .put("pdf_storage_name", (sha + ".pdf"))
                                        .put("pdf_sha256", sha)
                                        .put("pdf_byte_size", raw.size)
                                BusinessDocumentStore(context).store(raw, meta)
                                BusinessDocumentStore(context).read(meta)
                                if (found.add(sha)) {
                                    stored++
                                }
                            } finally {
                                Arrays.fill(raw, (0).toByte())
                            }
                        }
                    }
            }
            var notCreated: Int = 0
            run {
                var i: Int = 0
                var firstIteration41 = true
                while (true) {
                    if (firstIteration41) firstIteration41 = false
                    else {
                        i++
                    }
                    if (!((i < issued.length()))) break

                    if ((issued.getJSONObject(i).getString("pdf_resolution") == "not_created")) {
                        notCreated++
                    }
                }
            }
            return JSONObject()
                .put("pending_not_created_count", notCreated)
                .put("matched_unique_originals", stored)
                .put("required_unique_originals", wanted.size)
                .put("not_found_in_selected_tree", Math.max(0, (wanted.size - found.size)))
                .put("oversized_pdf_files", oversized)
                .put("source_folder_unchanged", true)
                .put("invoice_records_created", 0)
        }

        private @JvmStatic @Throws(Exception::class) fun bounded(
            input: InputStream,
            maximum: Int,
        ): ByteArray {

            var bytes = ByteArrayOutputStream()
            var block = ByteArray(32768)
            var count: Int
            while (true) {
                count = input.read(block)
                if (count == -1) break
                if (Thread.currentThread().isInterrupted()) {
                    throw InterruptedIOException("LOCAL_EVIDENCE_IMPORT_INTERRUPTED")
                }
                if ((count > (maximum - bytes.size()))) {
                    throw IOException("LOCAL_EVIDENCE_FILE_TOO_LARGE")
                }
                bytes.write(block, 0, count)
            }
            return bytes.toByteArray()
        }

        private @JvmStatic @Throws(Exception::class) fun requireContent(uri: Uri?): Unit {

            if (((uri == null) || !(("content" == uri.getScheme())))) {
                throw IOException("DOCUMENT_URI_REQUIRED")
            }
        }

        private @JvmStatic @Throws(Exception::class) fun integer(
            row: JSONObject,
            key: String,
            min: Long,
            max: Long,
        ): Long {

            var value = row.get(key)
            if (
                ((!(((value is Int || value is Long))) || (((value as Number)).toLong() < min)) ||
                    (((value as Number)).toLong() > max))
            ) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            return ((value as Number)).toLong()
        }

        private @JvmStatic @Throws(Exception::class) fun id(row: JSONObject, key: String): String {

            return nonempty(row, key, 200)
        }

        private @JvmStatic @Throws(Exception::class) fun nonempty(
            row: JSONObject,
            key: String,
            max: Int,
        ): String {

            var raw = row.get(key)
            if (!((raw is String))) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            var value = (raw as String)
            if (
                ((value.trim().isEmpty() || (value.length > max)) || (value.indexOf('\u0000') >= 0))
            ) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            return value
        }

        private @JvmStatic @Throws(Exception::class) fun hash(
            row: JSONObject,
            key: String,
        ): String {

            var value = nonempty(row, key, 64)
            if (!(value.matches(Regex("[0-9a-f]{64}")))) {
                throw IOException("LOCAL_EVIDENCE_LEDGER_INVALID")
            }
            return value
        }
    }
}
