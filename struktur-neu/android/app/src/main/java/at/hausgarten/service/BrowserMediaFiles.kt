package at.hausgarten.service

import org.json.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.util.*

/** Immutable private media files and epoch-scoped streamed uploads. */
class BrowserMediaFiles {
    fun interface Budget {
        @Throws(IOException::class) fun require(additionalBytes: Long): Unit
    }
    private val media: File
    private val uploads: File
    private val budget: Budget
    private val verifiedReads: MutableMap<String, String> = HashMap()
    constructor(media: File, uploads: File) : this(media, uploads, LAM@ { bytes ->
        var parent = uploads
        while (!(parent.exists()) && parent.getParentFile() != null) {
            parent = parent.getParentFile()!!
        }
        if (parent.getUsableSpace() < bytes + 8L * 1024 * 1024) {
            throw failure("SPACE_REQUIRED")
        }
    }) {

    }
    constructor(media: File, uploads: File, budget: Budget) {
        this.media = media
        this.uploads = uploads
        this.budget = budget
    }
    private @Throws(Exception::class) fun upload(id: String?, suffix: String): File {

        if (id == null || !(id.matches(Regex("[0-9a-f]{64}")))) {
            throw failure("UPLOAD_INVALID")
        }
        directory(uploads)
        var file = File(uploads, id + suffix)
        if (Files.isSymbolicLink(file.toPath())) {
            throw failure("PATH_INVALID")
        }
        return file
    }
    @Synchronized @Throws(Exception::class) fun begin(id: String?, size: Long, type: String?): JSONObject {

        if (size < 0 || size > 9007199254740991L) {
            throw failure("SIZE_INVALID")
        }
        mime(type)
        var state = upload(id, ".json")
        var record: JSONObject
        if (state.exists()) {
            record = json(state)
            if (record.optInt("formatVersion") != 1 || record.getLong("byte_size") != size || !((record.getString("mime_type") == type))) {
                throw failure("REPLAY_MISMATCH")
            }
        } else {
            if (upload(id, ".part").exists()) {
                throw failure("UPLOAD_REVIEW_REQUIRED")
            }
            record = JSONObject().put("formatVersion", 1).put("byte_size", size).put("mime_type", type).put("phase", "UPLOADING")
            atomic(state, record)
        }
        if ((record.optString("phase") == "COMPLETE")) {
            verifyReference(media, record.getJSONObject("reference"))
            return JSONObject().put("upload_id", id).put("next_offset", 0).put("complete", true).put("reference", record.getJSONObject("reference"))
        }
        var part = upload(id, ".part")
        var offset: Long = (if (part.exists()) part.length() else 0)
        if (offset > size) {
            throw failure("UPLOAD_REVIEW_REQUIRED")
        }
        return JSONObject().put("upload_id", id).put("next_offset", offset).put("complete", false)
    }
    @Synchronized @Throws(Exception::class) fun append(id: String?, offset: Long, chunk: ByteArray?): JSONObject {

        if (chunk == null || chunk.size < 1 || chunk.size > CHUNK_BYTES || offset < 0) {
            throw failure("CHUNK_INVALID")
        }
        var record = json(upload(id, ".json"))
        var size: Long = record.getLong("byte_size")
        var end: Long
        try {

            end = Math.addExact(offset, chunk.size.toLong())

        } catch (invalid: ArithmeticException) {
            throw failure("CHUNK_INVALID")
        }
        if (end > size) {
            throw failure("CHUNK_INVALID")
        }
        if ((record.optString("phase") == "COMPLETE")) {
            var ref = record.getJSONObject("reference")
            verifyReference(media, ref)
            var previous = read(ref, offset, chunk.size)
            if (!(java.util.Arrays.equals(previous, chunk))) {
                throw failure("REPLAY_MISMATCH")
            }
            return JSONObject().put("next_offset", end)
        }
        if (!((record.optString("phase") == "UPLOADING"))) {
            throw failure("UPLOAD_CLOSED")
        }
        var part = upload(id, ".part")
        var previous: Long = (if (part.exists()) part.length() else 0)
        if (offset > previous) {
            throw failure("CHUNK_ORDER_INVALID")
        }
        var retained: Int = (Math.min(chunk.size.toLong(), previous - offset)).toInt()
        RandomAccessFile(part, "rw").use { file ->

            file.seek(offset)
            for (i in 0 until retained) {

                if (file.read() != (chunk[i].toInt() and 255)) {
                    throw failure("REPLAY_MISMATCH")
                }

            }
            if (end > previous) {
                budget.require(end - previous)
                file.seek(previous)
                file.write(chunk, retained, chunk.size - retained)
                file.getFD().sync()
            }

        }
        return JSONObject().put("next_offset", Math.max(previous, end))
    }
    @Synchronized @Throws(Exception::class) fun complete(id: String?): JSONObject {

        var state = upload(id, ".json")
        var record = json(state)
        if ((record.optString("phase") == "COMPLETE")) {
            var ref = record.getJSONObject("reference")
            verifyReference(media, ref)
            return ref
        }
        if (!(Arrays.asList("UPLOADING", "COMMITTING").contains(record.optString("phase")))) {
            throw failure("UPLOAD_REVIEW_REQUIRED")
        }
        var part = upload(id, ".part")
        var size: Long = record.getLong("byte_size")
        var sha = record.optString("sha256")
        if (sha.isEmpty()) {
            if (!(part.exists()) && size == 0L) {
                budget.require(0)
                FileOutputStream(part).use { output ->

                    output.getFD().sync()

                }
            }
            if (!(part.isFile()) || part.length() != size) {
                throw failure("UPLOAD_INCOMPLETE")
            }
            sha = hash(part)
            record.put("phase", "COMMITTING").put("sha256", sha)
            atomic(state, record)
        }
        if (!(sha.matches(Regex("[0-9a-f]{64}")))) {
            throw failure("UPLOAD_REVIEW_REQUIRED")
        }
        directory(media)
        var target = File(media, sha + ".blob")
        if (Files.isSymbolicLink(target.toPath())) {
            throw failure("PATH_INVALID")
        }
        var ref = JSONObject().put("__hg_media_ref", 1).put("sha256", sha).put("byte_size", size).put("mime_type", record.getString("mime_type")).put("representation", "blob")
        if (target.exists()) {
            verifyReference(media, ref)
        } else {
            if (!(part.isFile()) || part.length() != size || !((hash(part) == sha))) {
                throw failure("UPLOAD_INCOMPLETE")
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        verifyReference(media, ref)
        record.put("phase", "COMPLETE").put("reference", ref)
        atomic(state, record)
        if (part.isFile() && part.length() == size && (hash(part) == sha)) {
            try {

                Files.delete(part.toPath())

            } catch (retained: IOException) {

            }
        }
        return ref
    }
    @Synchronized @Throws(Exception::class) fun read(ref: JSONObject, offset: Long, length: Int): ByteArray {

        validateReference(ref)
        if (offset < 0 || length < 1 || length > CHUNK_BYTES || offset > ref.getLong("byte_size")) {
            throw failure("CHUNK_INVALID")
        }
        var file = referenceFile(media, ref)
        var before = stamp(file)
        var sha = ref.getString("sha256")
        if (!((before == verifiedReads.get(sha)))) {
            verifyReference(media, ref)
            if (!((before == stamp(file)))) {
                throw failure("ORIGINAL_MISSING_OR_CHANGED")
            }
            if (verifiedReads.size > 256) {
                verifiedReads.clear()
            }
            verifiedReads.put(sha, before)
        }
        if (file.length() != ref.getLong("byte_size")) {
            throw failure("ORIGINAL_MISSING_OR_CHANGED")
        }
        var count: Int = (Math.min(length.toLong(), file.length() - offset)).toInt()
        var data = ByteArray(count)
        RandomAccessFile(file, "r").use { input ->

            input.seek(offset)
            input.readFully(data)

        }
        if (!((before == stamp(file)))) {
            throw failure("ORIGINAL_MISSING_OR_CHANGED")
        }
        return data
    }
    companion object {
        const val CHUNK_BYTES: Int = 65536
        private @JvmStatic fun failure(code: String): IOException {

            return IOException("BROWSER_MEDIA_" + code)
        }
        private @JvmStatic @Throws(Exception::class) fun directory(directory: File): Unit {

            if (Files.isSymbolicLink(directory.toPath())) {
                throw failure("PATH_INVALID")
            }
            if (!(directory.isDirectory()) && !(directory.mkdirs())) {
                throw failure("STORAGE_UNAVAILABLE")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun mime(value: String?): Unit {

            if (value == null || value.length > 200 || !(value.matches(Regex("[A-Za-z0-9!#\$&^_.+*-]+/[A-Za-z0-9!#\$&^_.+*-]+")))) {
                throw failure("MIME_INVALID")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun stamp(file: File): String {

            var attrs = Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (!(attrs.isRegularFile())) {
                throw failure("ORIGINAL_MISSING")
            }
            return java.lang.String.valueOf(attrs.size()) + ":" + attrs.lastModifiedTime() + ":" + java.lang.String.valueOf(attrs.fileKey())
        }
        @JvmStatic @Throws(Exception::class) fun referenceFile(directory: File, ref: JSONObject): File {

            validateReference(ref)
            if (Files.isSymbolicLink(directory.toPath())) {
                throw failure("PATH_INVALID")
            }
            var file = File(directory, ref.getString("sha256") + ".blob")
            if (Files.isSymbolicLink(file.toPath()) || !((file.getCanonicalFile().getParentFile() == directory.getCanonicalFile()))) {
                throw failure("PATH_INVALID")
            }
            return file
        }
        @JvmStatic @Throws(Exception::class) fun verifyReference(directory: File, ref: JSONObject): Unit {

            var file = referenceFile(directory, ref)
            if (!(file.isFile()) || file.length() != ref.getLong("byte_size") || !((hash(file) == ref.getString("sha256")))) {
                throw failure("ORIGINAL_MISSING_OR_CHANGED")
            }
        }
        @JvmStatic @Throws(Exception::class) fun validateReference(ref: JSONObject): Unit {

            if (!(("1" == java.lang.String.valueOf(ref.opt("__hg_media_ref")))) || !(ref.optString("sha256").matches(Regex("[0-9a-f]{64}")))) {
                throw failure("REFERENCE_INVALID")
            }
            var size: Long
            try {

                size = java.math.BigDecimal(ref.get("byte_size").toString()).longValueExact()

            } catch (invalid: Exception) {
                throw failure("REFERENCE_INVALID")
            }
            if (size < 0 || size > 9007199254740991L) {
                throw failure("REFERENCE_INVALID")
            }
            mime(ref.optString("mime_type"))
            var representation = ref.optString("representation")
            if (!(Arrays.asList("blob", "data_url").contains(representation))) {
                throw failure("REFERENCE_INVALID")
            }
            if ((representation == "data_url") && !(ref.optString("data_url_prefix").matches(Regex("data:[A-Za-z0-9!#\$&^_.+*/=-]{1,240};base64,")))) {
                throw failure("REFERENCE_INVALID")
            }
            val fields = ref.keys()
            while (fields.hasNext()) {

                if (!(Arrays.asList("__hg_media_ref", "sha256", "byte_size", "mime_type", "representation", "data_url_prefix").contains(fields.next()))) {
                    throw failure("REFERENCE_INVALID")
                }

            }
        }
        @JvmStatic @Throws(Exception::class) fun references(graph: JSONObject): JSONArray {

            var found = JSONArray()
            walk(graph, found, 0)
            return found
        }
        private @JvmStatic @Throws(Exception::class) fun walk(value: Any, found: JSONArray, depth: Int): Unit {

            if (depth > 40) {
                throw failure("GRAPH_INVALID")
            }
            if (value is JSONObject) {
                var node = (value as JSONObject)
                if (node.has("__hg_media_ref")) {
                    validateReference(node)
                    found.put(JSONObject(node.toString()))
                    return
                }
                val fields = node.keys()
                while (fields.hasNext()) {

                    var field = fields.next()
                    if (field.matches(Regex("(?i).*(password|passwd|secret|credential|oauth|recovery.?code|api.?key|authorization|token).*")) || field.equals("token", ignoreCase = true)) {
                        throw failure("GRAPH_SECRET_FIELD")
                    }
                    walk(node.get(field), found, depth + 1)

                }
            } else {
                if (value is JSONArray) {
                    var array = (value as JSONArray)
                    for (i in 0 until array.length()) {

                        walk(array.get(i), found, depth + 1)

                    }
                } else {
                    if (value is String && ((value as String)).matches(Regex("(?s)^data:[^,]{1,256};base64,.*"))) {
                        throw failure("INLINE_MEDIA_FORBIDDEN")
                    }
                }
            }
        }
        @JvmStatic @Throws(IOException::class) fun purgeUnreferenced(directory: File?, keep: MutableSet<String>): Unit {

            if (directory == null || !(directory.exists())) {
                return
            }
            if (Files.isSymbolicLink(directory.toPath()) || !(directory.isDirectory())) {
                throw failure("PATH_INVALID")
            }
            var files = directory.listFiles()
            if (files == null) {
                throw failure("STORAGE_UNAVAILABLE")
            }
            for (file in files) {
                var name = file.getName()
                if (!(name.matches(Regex("[0-9a-f]{64}\\.blob")))) {
                    throw failure("PATH_INVALID")
                }
                if (!(keep.contains(name.substring(0, 64)))) {
                    if (Files.isSymbolicLink(file.toPath()) || !(file.isFile())) {
                        throw failure("PATH_INVALID")
                    }
                    Files.deleteIfExists(file.toPath())
                }
            }
        }
        private @JvmStatic @Throws(Exception::class) fun json(file: File): JSONObject {

            if (!(file.isFile()) || file.length() > 65536) {
                throw failure("UPLOAD_INVALID")
            }
            var record = JSONObject(String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
            if (!(("1" == java.lang.String.valueOf(record.opt("formatVersion"))))) {
                throw failure("UPLOAD_INVALID")
            }
            return record
        }
        private @JvmStatic @Throws(Exception::class) fun atomic(file: File, record: JSONObject): Unit {

            directory(file.getParentFile()!!)
            var part = File(file.getParentFile(), java.lang.String.valueOf(file.getName()) + ".new-" + UUID.randomUUID())
            try {

                FileOutputStream(part).use { output ->

                    output.write(record.toString().toByteArray(StandardCharsets.UTF_8))
                    output.getFD().sync()

                }
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

            } finally {
                Files.deleteIfExists(part.toPath())
            }
        }
        @JvmStatic @Throws(Exception::class) fun hash(file: File): String {

            var digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream(file)).use { input ->

                var block = ByteArray(CHUNK_BYTES)
                while (true) {
                    val count = input.read(block)
                    if (count == -1) break
                    if (Thread.currentThread().isInterrupted()) {
                        throw InterruptedIOException("BROWSER_MEDIA_INTERRUPTED")
                    }
                    digest.update(block, 0, count)
                }

            }
            var value = StringBuilder()
            for (b in digest.digest()) {
                value.append(String.format(Locale.ROOT, "%02x", b.toInt() and 255))
            }
            return value.toString()
        }
    }
}
