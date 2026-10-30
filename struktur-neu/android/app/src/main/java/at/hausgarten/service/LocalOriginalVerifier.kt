package at.hausgarten.service

import java.io.*
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** Shared read-only original checks for live diagnostics and isolated backup snapshots. */
internal class LocalOriginalVerifier : LocalMigrationInventory.Originals {
    fun interface PhotoMetadata {
        @Throws(Exception::class) fun get(id: String): JSONObject?
    }

    fun interface DocumentJobs {
        @Throws(Exception::class) fun get(invoiceId: String): JSONObject?
    }

    private val files: File
    private val photos: PhotoMetadata
    private val documents: DocumentJobs?

    constructor(files: File, photos: PhotoMetadata) : this(files, photos, null) {}

    constructor(files: File, photos: PhotoMetadata, documents: DocumentJobs?) {
        this.files = files
        this.photos = photos
        this.documents = documents
    }

    @Throws(Exception::class)
    fun problem(kind: String, reference: JSONObject): String {
        if (
            (verified(kind, reference) || (kind!!.equals("document") && pendingDocument(reference)))
        ) {
            return ""
        }
        var metadata =
            (if (kind!!.equals("photo")) photos!!.get(reference!!.optString("id")) else reference)
        if ((metadata === null)) {
            metadata = reference
        }
        var name =
            metadata!!.optString(
                (if (kind!!.equals("photo")) "storage_name" else "pdf_storage_name")
            )
        var sha =
            metadata!!.optString((if (kind!!.equals("photo")) "content_sha256" else "pdf_sha256"))
        if ((name!!.isEmpty() || !sha!!.matches(Regex("[0-9a-f]{64}")))) {
            return "ORIGINAL_METADATA_INCOMPLETE"
        }
        var file: File =
            File(
                File(
                    files,
                    (if (kind!!.equals("photo")) "customer-photo-originals"
                    else "business-documents"),
                ),
                name,
            )
        if (!file!!.isFile()) {
            return "ORIGINAL_FILE_MISSING"
        }
        return "ORIGINAL_NOT_VERIFIABLE"
    }

    override @Throws(Exception::class) fun verified(kind: String, reference: JSONObject): Boolean {
        var metadata = reference
        var file: File? = null
        if (kind!!.equals("photo")) {
            var local = photos!!.get(reference!!.optString("id"))
            if ((local !== null)) {
                metadata = local
            }
            if (!PhotoAttachmentStore.canonicalName(metadata!!.optString("storage_name"))) {
                return false
            }
            file =
                File(File(files, "customer-photo-originals"), metadata!!.getString("storage_name"))
        } else {
            if ((reference!!.optString("pdf_state"))!!.equals("PDF_PENDING")) {
                return false
            }
            if (
                !(metadata!!.optString("pdf_storage_name"))!!.matches(Regex("[0-9a-f]{64}\\.pdf"))
            ) {
                return false
            }
            file = File(File(files, "business-documents"), metadata!!.getString("pdf_storage_name"))
        }
        var expected =
            metadata!!.optString((if (kind!!.equals("photo")) "content_sha256" else "pdf_sha256"))
        var size: Long =
            metadata!!.optLong((if (kind!!.equals("photo")) "byte_size" else "pdf_byte_size"), -1)
        if (
            (((!expected!!.matches(Regex("[0-9a-f]{64}")) || !file!!.isFile()) ||
                java.nio.file.Files.isSymbolicLink(file!!.toPath())) || (file!!.length() != size))
        ) {
            return false
        }
        if (!kind!!.equals("photo")) {
            run {
                FileInputStream(file).use { header ->
                    var prefix: ByteArray = ByteArray(5)
                    if (
                        ((header!!.read(prefix) != 5) ||
                            !(String(prefix, java.nio.charset.StandardCharsets.US_ASCII))!!.equals(
                                "%PDF-"
                            ))
                    ) {
                        return false
                    }
                }
            }
        }
        var digest = MessageDigest.getInstance("SHA-256")
        var read: Long = 0
        run {
            BufferedInputStream(FileInputStream(file)).use { input ->
                var buffer: ByteArray = ByteArray(32768)
                var n: Int
                while (
                    (run {
                        n = input!!.read(buffer)
                        n
                    } != -1)
                ) {
                    digest!!.update(buffer, 0, n)
                    read += n
                }
            }
        }
        var hash: StringBuilder = StringBuilder()
        for (value in (digest!!.digest())!!) {
            hash!!.append(
                java.lang.String.format(Locale.ROOT, "%02x", (value.toInt() and (255).toInt()))
            )
        }
        if (((read != size) || !expected!!.equals(hash!!.toString()))) {
            return false
        }
        if ((!kind!!.equals("photo") && (documents !== null))) {
            var job = documents!!.get(reference!!.optString("id"))
            if (
                (((job !== null) && job!!.has("original_adoption")) &&
                    !adoptionVerified(reference, job))
            ) {
                return false
            }
        }
        return true
    }

    private @Throws(Exception::class) fun adoptionVerified(
        invoice: JSONObject,
        job: JSONObject,
    ): Boolean {
        var id = invoice!!.optString("id")
        var sha = job!!.optString("snapshot_sha256")
        var frozen = job!!.optJSONObject("snapshot")
        var adoption = job!!.optJSONObject("original_adoption")
        var document = job!!.optJSONObject("document")
        if (
            ((((((((((((((job!!.optInt("formatVersion") != 1) ||
                !(job!!.optString("phase"))!!.equals("READY")) ||
                !id!!.equals(job!!.optString("invoice_id"))) ||
                !sha!!.matches(Regex("[0-9a-f]{64}"))) || (frozen === null)) ||
                !sha!!.equals(PixelMcpWriteTools.hash(frozen))) ||
                !LocalFinancialValidation.sameFrozenInvoice(invoice, frozen)) ||
                (adoption === null)) || (adoption!!.optInt("formatVersion") != 1)) ||
                !sha!!.equals(adoption!!.optString("snapshot_sha256"))) ||
                !(adoption!!.optString("ledger_proof_sha256"))!!.matches(Regex("[0-9a-f]{64}"))) ||
                (adoption!!.optString("request_id"))!!.isEmpty()) || (document === null)) ||
                !sameDocument(document, invoice))
        ) {
            return false
        }
        java.time.Instant.parse(adoption!!.getString("adopted_at"))
        var directory: File = File(files, "invoice-document-receipts")
        var base: File = File(directory, (sha.toString() + ".json"))
        var backup: File = File((base.toString() + ".bak"))
        if (java.nio.file.Files.isSymbolicLink(directory!!.toPath())) {
            return false
        }
        for (candidate in (arrayOf(base, backup, File((base.toString() + ".new"))))!!) {
            if (java.nio.file.Files.isSymbolicLink(candidate!!.toPath())) {
                return false
            }
        }
        var source = (if (backup!!.exists()) backup else base)
        if ((!source!!.isFile() || (source!!.length() > 65536))) {
            return false
        }
        var receipt: JSONObject? = null
        run {
            FileInputStream(source).use { input ->
                ByteArrayOutputStream().use { output ->
                    var block: ByteArray = ByteArray(4096)
                    var n: Int
                    while (
                        (run {
                            n = input!!.read(block)
                            n
                        } != -1)
                    ) {
                        if ((n > (65536 - output!!.size()))) {
                            return false
                        }
                        output!!.write(block, 0, n)
                    }
                    receipt =
                        JSONObject(
                            String(output!!.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                        )
                }
            }
        }
        if (
            ((((((receipt!!.optInt("formatVersion") != 1) ||
                !id!!.equals(receipt!!.optString("invoice_id"))) ||
                !(invoice!!.optString("invoice_number"))!!.equals(
                    receipt!!.optString("invoice_number")
                )) || !sha!!.equals(receipt!!.optString("snapshot_sha256"))) ||
                (receipt!!.optJSONObject("document") === null)) ||
                !sameDocument(document, receipt!!.getJSONObject("document")))
        ) {
            return false
        }
        return LocalFinancialOriginals.receiptEvidenceMatches(adoption, receipt)
    }

    override @Throws(Exception::class) fun pendingDocument(invoice: JSONObject): Boolean {
        var id = invoice!!.optString("id")
        if (
            ((((((documents === null) || id!!.isEmpty()) ||
                !(invoice!!.optString("document_job_id"))!!.equals(("invoice:" + id))) ||
                !(invoice!!.optString("pdf_state"))!!.equals("PDF_PENDING")) ||
                ((invoice!!.optString("invoice_number"))!!.trim())!!.isEmpty()) ||
                (invoice!!.optString("issued_at"))!!.isEmpty())
        ) {
            return false
        }
        for (name in (arrayOf("pdf_sha256", "pdf_storage_name", "pdf_base64"))!!) {
            if (!(invoice!!.optString(name))!!.isEmpty()) {
                return false
            }
        }
        var job = documents!!.get(id)
        if (
            ((((job === null) || (job!!.optInt("formatVersion") != 1)) ||
                !(job!!.optString("invoice_id"))!!.equals(id)) ||
                !(job!!.optString("phase"))!!.equals("PDF_PENDING"))
        ) {
            return false
        }
        if (job!!.has("original_adoption")) {
            return false
        }
        if (job!!.has("creation_authorization")) {
            var proof = job!!.optJSONObject("creation_authorization")
            if (
                (((((((proof === null) || (proof!!.optInt("formatVersion") != 1)) ||
                    !(job!!.optString("snapshot_sha256"))!!.equals(
                        proof!!.optString("snapshot_sha256")
                    )) ||
                    !(proof!!.optString("ledger_proof_sha256"))!!.matches(Regex("[0-9a-f]{64}"))) ||
                    (proof!!.optString("installation_id"))!!.isEmpty()) ||
                    (proof!!.optString("restore_epoch"))!!.isEmpty()) ||
                    (proof!!.optString("request_id"))!!.isEmpty())
            ) {
                return false
            }
            java.time.Instant.parse(proof!!.getString("authorized_at"))
        }
        var snapshot = job!!.optJSONObject("snapshot")
        var hash = job!!.optString("snapshot_sha256")
        if (
            (((snapshot === null) || !hash!!.matches(Regex("[0-9a-f]{64}"))) ||
                !(PixelMcpWriteTools.hash(snapshot))!!.equals(hash))
        ) {
            return false
        }
        if (
            ((((!(snapshot!!.optString("id"))!!.equals(id) ||
                !(snapshot!!.optString("invoice_number"))!!.equals(
                    invoice!!.optString("invoice_number")
                )) ||
                !(snapshot!!.optString("issued_at"))!!.equals(invoice!!.optString("issued_at"))) ||
                !(snapshot!!.optString("document_job_id"))!!.equals(("invoice:" + id))) ||
                !(snapshot!!.optString("pdf_state"))!!.equals("PDF_PENDING"))
        ) {
            return false
        }
        var issuer = snapshot!!.optJSONObject("issuer")
        var recipient = snapshot!!.optJSONObject("recipient")
        var money = snapshot!!.optJSONObject("money_minor")
        var positions = snapshot!!.optJSONArray("positions")
        if (
            (((((issuer === null) || (recipient === null)) || (money === null)) ||
                (positions === null)) || (positions!!.length() == 0))
        ) {
            return false
        }
        for (field in
            (arrayOf("owner_name", "business_street", "business_postal_code", "business_city"))!!) {
            if (((issuer!!.optString(field))!!.trim())!!.isEmpty()) {
                return false
            }
        }
        for (field in (arrayOf("name", "street", "postal_code", "city"))!!) {
            if (((recipient!!.optString(field))!!.trim())!!.isEmpty()) {
                return false
            }
        }
        var tax = snapshot!!.optString("tax_mode")
        if (
            ((!(java.util.Arrays.asList("small_business", "standard"))!!.contains(tax) ||
                (tax!!.equals("small_business") &&
                    ((issuer!!.optString("tax_exemption_text"))!!.trim())!!.isEmpty())) ||
                (tax!!.equals("standard") &&
                    ((issuer!!.optString("business_vat_id"))!!.trim())!!.isEmpty()))
        ) {
            return false
        }
        java.time.Instant.parse(snapshot!!.getString("issued_at"))
        for (field in (arrayOf("invoice_date", "service_start", "service_end"))!!) {
            java.time.LocalDate.parse(snapshot!!.getString(field))
        }
        if (
            !(PixelMcpWriteTools.hash(LocalAuthorityRules.exactTotals(snapshot)))!!.equals(
                PixelMcpWriteTools.hash(money)
            )
        ) {
            return false
        }
        var original: JSONObject = JSONObject(snapshot!!.toString())
        var current: JSONObject = JSONObject(invoice!!.toString())
        for (mutable in
            (arrayOf(
                "status",
                "updated_at",
                "payment_balance",
                "pdf_state",
                "pdf_sha256",
                "pdf_storage_name",
                "pdf_byte_size",
                "pdf_base64",
                "document_id",
            ))!!) {
            original!!.remove(mutable)
            current!!.remove(mutable)
        }
        return (PixelMcpWriteTools.hash(original))!!.equals(PixelMcpWriteTools.hash(current))
    }

    companion object {
        private @JvmStatic @Throws(Exception::class) fun sameDocument(
            first: JSONObject,
            second: JSONObject,
        ): Boolean {
            for (key in
                (arrayOf("document_id", "pdf_sha256", "pdf_storage_name", "pdf_byte_size"))!!) {
                if (
                    ((!first!!.has(key) || !second!!.has(key)) ||
                        !((first!!.get(key))!!.toString())!!.equals(
                            (second!!.get(key))!!.toString()
                        ))
                ) {
                    return false
                }
            }
            return true
        }
    }
}
