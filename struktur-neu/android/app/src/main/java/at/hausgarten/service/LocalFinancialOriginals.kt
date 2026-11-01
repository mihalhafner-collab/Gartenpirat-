package at.hausgarten.service

import java.io.*
import java.math.BigDecimal
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.*
import org.json.*

/** Streaming original proof for external ledger entries, including absent canonical invoices. */
internal object LocalFinancialOriginals {
    @JvmStatic
    @Throws(Exception::class)
    fun receiptEvidenceMatches(adoption: JSONObject, receipt: JSONObject): Boolean {
        var ledger = adoption!!.opt("ledger_proof_sha256")
        var bound =
            (if (adoption!!.has("receipt_proof_sha256")) adoption!!.opt("receipt_proof_sha256")
            else ledger)
        if (
            (((!(ledger is String) || !((ledger as String))!!.matches(Regex("[0-9a-f]{64}"))) ||
                !(bound is String)) || !((bound as String))!!.matches(Regex("[0-9a-f]{64}")))
        ) {
            return false
        }
        if (!receipt!!.has("recovered_from_full_evidence_sha256")) {
            return !adoption!!.has("receipt_proof_sha256")
        }
        var recorded = receipt!!.opt("recovered_from_full_evidence_sha256")
        return (((recorded is String) && ((recorded as String))!!.matches(Regex("[0-9a-f]{64}"))) &&
            bound!!.equals(recorded))
    }

    private @JvmStatic fun error(code: String): IOException {
        return IOException(("FINANCIAL_ORIGINAL_" + code))
    }

    private @JvmStatic @Throws(IOException::class) fun size(raw: Any?): Long {
        try {
            var value: Long = (BigDecimal(raw!!.toString()))!!.longValueExact()
            if ((value < 5)) {
                throw error("EVIDENCE_INVALID")
            }
            return value
        } catch (fixed: IOException) {
            throw fixed
        } catch (invalid: Exception) {
            throw error("EVIDENCE_INVALID")
        }
    }

    private @JvmStatic fun hex(bytes: ByteArray): String {
        var result: StringBuilder = StringBuilder()
        for (value in bytes!!) {
            result!!.append(
                java.lang.String.format(Locale.ROOT, "%02x", (value.toInt() and (255).toInt()))
            )
        }
        return result!!.toString()
    }

    private @JvmStatic @Throws(IOException::class) fun safeDirectory(path: Path): Unit {
        if ((Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) {
            throw error("PATH_REJECTED")
        }
    }

    private @JvmStatic @Throws(IOException::class) fun sameFile(
        before: BasicFileAttributes,
        after: BasicFileAttributes,
    ): Unit {
        if (
            (((!after!!.isRegularFile() || (before!!.size() != after!!.size())) ||
                !(before!!.lastModifiedTime())!!.equals(after!!.lastModifiedTime())) ||
                !Objects.equals(before!!.fileKey(), after!!.fileKey()))
        ) {
            throw error("CHANGED")
        }
    }

    private @JvmStatic @Throws(Exception::class) fun verify(
        root: Path,
        directory: Path,
        sha: String,
        expected: Long?,
    ): Long {
        safeDirectory(root)
        safeDirectory(directory)
        var path = directory!!.resolve((sha.toString() + ".pdf"))
        if (Files.isSymbolicLink(path)) {
            throw error("PATH_REJECTED")
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw error("MISSING")
        }
        var actual = path!!.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (
            (!(actual!!.getParent())!!.equals(directory!!.toRealPath(LinkOption.NOFOLLOW_LINKS)) ||
                !actual!!.startsWith(root!!.toRealPath(LinkOption.NOFOLLOW_LINKS)))
        ) {
            throw error("PATH_REJECTED")
        }
        var before =
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if ((before!!.size() < 5)) {
            throw error("PDF_INVALID")
        }
        if (((expected !== null) && (before!!.size() != expected))) {
            throw error("SIZE_MISMATCH")
        }
        var digest = MessageDigest.getInstance("SHA-256")
        var count: Long = 0
        var header: ByteArray = ByteArray(5)
        var options: MutableSet<OpenOption> = HashSet()
        options!!.add(StandardOpenOption.READ)
        options!!.add(LinkOption.NOFOLLOW_LINKS)
        run {
            BufferedInputStream(Channels.newInputStream(Files.newByteChannel(path, options))).use {
                input ->
                var buffer: ByteArray = ByteArray(32768)
                var n: Int
                while (
                    (run {
                        n = input!!.read(buffer)
                        n
                    } != -1)
                ) {
                    if ((Thread.currentThread())!!.isInterrupted) {
                        throw InterruptedIOException("FINANCIAL_ORIGINAL_INTERRUPTED")
                    }
                    if ((count < 5)) {
                        System.arraycopy(
                            buffer,
                            0,
                            header,
                            (count as Int),
                            Math.min(n, (5 - (count as Int))),
                        )
                    }
                    digest!!.update(buffer, 0, n)
                    count = Math.addExact(count.toLong(), n.toLong())
                }
            }
        }
        safeDirectory(root)
        safeDirectory(directory)
        if (Files.isSymbolicLink(path)) {
            throw error("PATH_REJECTED")
        }
        var after =
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        sameFile(before, after)
        if (((count != before!!.size()) || ((expected !== null) && (count != expected)))) {
            throw error("SIZE_MISMATCH")
        }
        if (!(String(header, StandardCharsets.US_ASCII))!!.equals("%PDF-")) {
            throw error("PDF_INVALID")
        }
        if (!sha!!.equals(hex(digest!!.digest()))) {
            throw error("HASH_MISMATCH")
        }
        return count
    }

    @JvmStatic
    @Throws(Exception::class)
    fun inspect(capturedFiles: File?, externalEvidence: JSONObject?): JSONObject {
        try {
            var entries =
                (if (((externalEvidence === null) || (externalEvidence!!.length() == 0)))
                    JSONArray()
                else externalEvidence!!.getJSONArray("issued"))
            var originals: TreeMap<String, Long> = TreeMap()
            var withSize: Int = 0
            var pendingNotCreated: Int = 0
            var originalReferences: Int = 0
            run {
                var i: Int = 0
                while ((i < entries!!.length())) {
                    var entry = entries!!.getJSONObject(i)
                    var resolution = entry!!.optString("pdf_resolution", "original")
                    if (!(Arrays.asList("original", "not_created"))!!.contains(resolution)) {
                        throw error("EVIDENCE_INVALID")
                    }
                    if (resolution!!.equals("not_created")) {
                        if (
                            ((((((((!(entry!!.opt("invoice_id") is String) ||
                                ((entry!!.getString("invoice_id"))!!.trim())!!.isEmpty()) ||
                                !(entry!!.opt("invoice_number") is String)) ||
                                ((entry!!.getString("invoice_number"))!!.trim())!!.isEmpty()) ||
                                !java.lang.Boolean.TRUE.equals(
                                    entry!!.opt("not_created_confirmed")
                                )) ||
                                !(entry!!.optString("snapshot_sha256"))!!.matches(
                                    Regex("[0-9a-f]{64}")
                                )) || !(entry!!.opt("not_created_reason") is String)) ||
                                ((entry!!.getString("not_created_reason"))!!.trim())!!.isEmpty()) ||
                                ((entry!!.getString("not_created_reason"))!!.length > 4000))
                        ) {
                            throw error("EVIDENCE_INVALID")
                        }
                        for (field in
                            (arrayOf(
                                "pdf_sha256",
                                "pdf_byte_size",
                                "pdf_storage_name",
                                "pdf_base64",
                                "document_id",
                                "document",
                            ))!!) {
                            if (entry!!.has(field)) {
                                throw error("EVIDENCE_INVALID")
                            }
                        }
                        pendingNotCreated++
                        run {
                            i++
                            continue
                        }
                    }
                    if (
                        ((entry!!.has("not_created_confirmed") ||
                            entry!!.has("not_created_reason")) ||
                            (entry!!.has("snapshot_sha256") &&
                                !(entry!!.optString("snapshot_sha256"))!!.matches(
                                    Regex("[0-9a-f]{64}")
                                )))
                    ) {
                        throw error("EVIDENCE_INVALID")
                    }
                    originalReferences++
                    var sha = entry!!.getString("pdf_sha256")
                    if (!sha!!.matches(Regex("[0-9a-f]{64}"))) {
                        throw error("EVIDENCE_INVALID")
                    }
                    var expected =
                        (if (entry!!.has("pdf_byte_size")) size(entry!!.get("pdf_byte_size"))
                        else null)
                    if ((expected !== null)) {
                        withSize++
                    }
                    if (originals!!.containsKey(sha)) {
                        var previous = originals!!.get(sha)
                        if (
                            (((previous !== null) && (expected !== null)) &&
                                !previous!!.equals(expected))
                        ) {
                            throw error("EVIDENCE_INVALID")
                        }
                        if (((previous === null) && (expected !== null))) {
                            originals!!.put(sha, expected!!)
                        }
                    } else {
                        originals!!.put(sha, expected!!)
                    }

                    i++
                }
            }
            var manifest = MessageDigest.getInstance("SHA-256")
            manifest!!.update(
                ("hausgarten.financial_originals.v1\n")!!.toByteArray(StandardCharsets.US_ASCII)
            )
            var bytes: Long = 0
            if (!originals!!.isEmpty()) {
                if ((capturedFiles === null)) {
                    throw error("PATH_REJECTED")
                }
                var root = ((capturedFiles!!.toPath())!!.toAbsolutePath())!!.normalize()
                var directory = root!!.resolve("business-documents")
                for (entry in (originals!!.entries)!!) {
                    var actual: Long = verify(root, directory, entry!!.key, entry!!.value)
                    bytes = Math.addExact(bytes.toLong(), actual.toLong())
                    manifest!!.update(
                        ((((((entry!!.key).toString() + " ") + actual)).toString() + "\n"))!!
                            .toByteArray(StandardCharsets.US_ASCII)
                    )
                }
            }
            return (((((((((((((JSONObject())!!.put("formatVersion", 1))!!.put("status", "PASS"))!!
                                                        .put(
                                                            "reference_count",
                                                            entries!!.length(),
                                                        ))!!
                                                    .put("original_count", originals!!.size))!!
                                                .put("byte_size", bytes))!!
                                            .put("size_evidence_count", withSize))!!
                                        .put(
                                            "size_observed_only_count",
                                            (originalReferences - withSize),
                                        ))!!
                                    .put("original_reference_count", originalReferences))!!
                                .put("pending_not_created_count", pendingNotCreated))!!
                            .put("manifest_format", "sha256_size_ascii_lines_v1"))!!
                        .put("manifest_sha256", hex(manifest!!.digest())))!!
                    .put("original_files_verified", (pendingNotCreated == 0)))!!
                .put("data_changed", false)
        } catch (fixed: IOException) {
            if (
                ((fixed!!.message !== null) &&
                    (fixed!!.message)!!.startsWith("FINANCIAL_ORIGINAL_"))
            ) {
                throw fixed
            }
            throw error("READ_FAILED")
        } catch (invalid: Exception) {
            throw error("EVIDENCE_INVALID")
        }
    }
}
