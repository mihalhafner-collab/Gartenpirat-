package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Complete local plaintext ZIP reader and writer. No business activation. */
object BackupArchive {
    const val FORMAT_VERSION = 1

    // Metadata-only anti-exhaustion limit; payload/media have no fixed archive-size cap.
    const val MAX_MANIFEST_BYTES = 32 * 1024 * 1024
    private const val BUFFER = 64 * 1024

    class Source @Throws(IOException::class) constructor(path: String?, @JvmField val file: File) {
        @JvmField val path: String

        init {
            requirePath(path)
            this.path = path!!
        }
    }

    class Verified(@JvmField val directory: File, @JvmField val manifest: JSONObject) : AutoCloseable {
        @Throws(IOException::class)
        fun file(path: String?): File {
            requirePath(path)
            return File(directory, "data/$path")
        }

        // Only our freshly created isolated directory is owned here. Never the active store.
        @Throws(IOException::class)
        override fun close() = removeOwned(directory)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun writePlain(destination: OutputStream, metadata: JSONObject, sources: List<Source>): JSONObject {
        val manifest = JSONObject(metadata.toString())
            .put("formatVersion", FORMAT_VERSION)
            .put("archiveProtection", "NONE")
        requireMetadata(manifest, Int.MAX_VALUE)
        val names = HashSet<String>()
        val files = JSONArray()
        var total = 0L
        ZipOutputStream(destination).use { archive ->
            archive.setLevel(Deflater.NO_COMPRESSION)
            for (source in sources) {
                requirePath(source.path)
                if (!names.add(source.path)) throw IOException("BACKUP_DUPLICATE_FILE")
                if (!source.file.isFile || Files.isSymbolicLink(source.file.toPath())) {
                    throw IOException("BACKUP_SOURCE_UNAVAILABLE")
                }
                val expectedSize = source.file.length()
                val expectedHash = sha256(source.file)
                val entry = ZipEntry("data/${source.path}")
                entry.time = 0
                archive.putNextEntry(entry)
                val digest = MessageDigest.getInstance("SHA-256")
                val copied = BufferedInputStream(FileInputStream(source.file)).use { input ->
                    copy(input, archive, digest, expectedSize)
                }
                archive.closeEntry()
                if (copied != expectedSize || expectedHash != hex(digest.digest())) {
                    throw IOException("BACKUP_SOURCE_CHANGED")
                }
                total = Math.addExact(total, copied)
                files.put(JSONObject().put("path", source.path).put("size", copied).put("sha256", expectedHash))
            }
            manifest.put("files", files).put("totalBytes", total).put("completion", "COMPLETE")
            val json = manifest.toString().toByteArray(StandardCharsets.UTF_8)
            if (json.size > MAX_MANIFEST_BYTES) throw IOException("BACKUP_MANIFEST_TOO_LARGE")
            val commit = ZipEntry("manifest.json")
            commit.time = 0
            archive.putNextEntry(commit)
            archive.write(json)
            archive.closeEntry()
        }
        // Completion here only means written. Destination readback is separately required.
        return manifest
    }

    @JvmStatic
    @Throws(Exception::class)
    fun verifyPlain(
        input: InputStream, newDirectory: File, availableBytes: Long, supportedSchema: Int
    ): Verified = input.use { owned ->
        if (availableBytes <= 0 || newDirectory.exists() || !newDirectory.mkdirs()) {
            throw IOException("RESTORE_STAGING_UNAVAILABLE")
        }
        var verified = false
        val plain = File(newDirectory, "archive.zip")
        try {
            BufferedInputStream(owned).use { source ->
                source.mark(4)
                val signature = ByteArray(4)
                var count = 0
                while (count < signature.size) {
                    val read = source.read(signature, count, signature.size - count)
                    if (read == -1) break
                    if (read == 0) continue
                    count += read
                }
                if (count != 4 || signature[0] != 80.toByte() || signature[1] != 75.toByte() ||
                    signature[2] != 3.toByte() || signature[3] != 4.toByte()) {
                    throw IOException("BACKUP_FORMAT_UNSUPPORTED")
                }
                source.reset()
                FileOutputStream(plain).use { output ->
                    copy(source, BackupStorageBudget.guard(output, newDirectory), null, availableBytes)
                    output.fd.sync()
                }
            }
            val manifest = ZipFile(plain).use { archive ->
                val record = archive.getEntry("manifest.json")
                if (record == null || record.isDirectory || record.size < 1 || record.size > MAX_MANIFEST_BYTES) {
                    throw IOException("BACKUP_MANIFEST_INVALID")
                }
                val value = archive.getInputStream(record).use { input ->
                    ByteArrayOutputStream().use { json ->
                        copy(input, json, null, MAX_MANIFEST_BYTES.toLong())
                        JSONObject(String(json.toByteArray(), StandardCharsets.UTF_8))
                    }
                }
                requireMetadata(value, supportedSchema)
                if (value.optString("completion") != "COMPLETE") throw IOException("BACKUP_COMMIT_MISSING")
                val files = value.getJSONArray("files")
                val expected = LinkedHashMap<String, JSONObject>()
                var declaredTotal = 0L
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val path = file.getString("path")
                    requirePath(path)
                    if (expected.put("data/$path", file) != null || file.getLong("size") < 0 ||
                        !file.getString("sha256").matches(Regex("[0-9a-f]{64}"))) {
                        throw IOException("BACKUP_MANIFEST_INVALID")
                    }
                    declaredTotal = Math.addExact(declaredTotal, file.getLong("size"))
                }
                if (declaredTotal != value.getLong("totalBytes") || declaredTotal > availableBytes - plain.length()) {
                    throw IOException("RESTORE_SPACE_REQUIRED")
                }
                val seen = HashSet<String>()
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!seen.add(name) || entry.isDirectory) throw IOException("BACKUP_DUPLICATE_FILE")
                    if (name == "manifest.json") continue
                    val wanted = expected[name]
                    if (wanted == null || entry.size != wanted.getLong("size")) {
                        throw IOException("BACKUP_FILE_SET_INVALID")
                    }
                    val file = File(newDirectory, name)
                    if (!file.canonicalPath.startsWith(newDirectory.canonicalPath + File.separator)) {
                        throw IOException("BACKUP_PATH_INVALID")
                    }
                    val parent = file.parentFile!!
                    if (!parent.isDirectory && !parent.mkdirs()) throw IOException("RESTORE_SPACE_REQUIRED")
                    val digest = MessageDigest.getInstance("SHA-256")
                    val size = archive.getInputStream(entry).use { input ->
                        FileOutputStream(file).use { output ->
                            val copied = copy(
                                input, BackupStorageBudget.guard(output, newDirectory), digest, wanted.getLong("size")
                            )
                            output.fd.sync()
                            copied
                        }
                    }
                    if (size != wanted.getLong("size") || wanted.getString("sha256") != hex(digest.digest())) {
                        throw IOException("BACKUP_FILE_HASH_MISMATCH")
                    }
                }
                if (seen.size != expected.size + 1 || !seen.containsAll(expected.keys)) {
                    throw IOException("BACKUP_FILE_SET_INVALID")
                }
                value
            }
            if (!plain.delete()) throw IOException("RESTORE_STAGING_CLEANUP_REQUIRED")
            verified = true
            Verified(newDirectory, manifest)
        } finally {
            if (!verified) removeOwned(newDirectory)
        }
    }

    @Throws(Exception::class)
    private fun requireMetadata(manifest: JSONObject, supportedSchema: Int) {
        if (manifest.optString("archiveProtection") != "NONE") {
            throw IOException("BACKUP_FORMAT_UNSUPPORTED")
        }

        if (manifest.getInt("formatVersion") != FORMAT_VERSION || manifest.getInt("schemaVersion") < 1 ||
            manifest.getInt("schemaVersion") > supportedSchema) {
            throw IOException("BACKUP_VERSION_UNSUPPORTED")
        }
        if (manifest.getString("applicationId") != "at.hausgarten.service" || manifest.getString("appVersion").isEmpty() ||
            !manifest.getString("backupId").matches(Regex("[a-zA-Z0-9_-]{8,120}")) ||
            manifest.getString("installationId").isEmpty() || manifest.getString("businessId").isEmpty()) {
            throw IOException("BACKUP_MANIFEST_INVALID")
        }
        Instant.parse(manifest.getString("capturedAt"))
        manifest.getJSONObject("counts")
        manifest.getJSONObject("numberState")
        manifest.getJSONArray("lastIssuedDocumentIds")
    }

    @Throws(IOException::class)
    private fun requirePath(path: String?) {
        if (path == null || path.isEmpty() || path.length > 1024 || path.startsWith("/") || path.endsWith("/") ||
            path.contains("//") || path.indexOf('\\') >= 0) {
            throw IOException("BACKUP_PATH_INVALID")
        }
        for (character in path) {
            if (character.code < 32 || character.code == 127) throw IOException("BACKUP_PATH_INVALID")
        }
        for (part in path.split('/')) {
            if (part == "." || part == "..") throw IOException("BACKUP_PATH_INVALID")
        }
    }

    @JvmStatic
    @Throws(InterruptedIOException::class)
    fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("BACKUP_INTERRUPTED")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream, digest: MessageDigest?, maximum: Long): Long {
        checkCancelled()
        val buffer = ByteArray(BUFFER)
        var count = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            checkCancelled()
            if (read == 0) continue
            if (read > maximum - count) throw IOException("RESTORE_SPACE_REQUIRED_OR_SOURCE_CHANGED")
            output.write(buffer, 0, read)
            digest?.update(buffer, 0, read)
            count += read
        }
        return count
    }

    @JvmStatic
    @Throws(Exception::class)
    fun sha256(file: File): String {
        checkCancelled()
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                checkCancelled()
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    @JvmStatic
    fun hex(value: ByteArray): String {
        val result = StringBuilder()
        for (byte in value) result.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 255))
        return result.toString()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun removeWorkDirectories(parent: File): Long {
        if (Files.isSymbolicLink(parent.toPath()) || !parent.isDirectory) throw IOException("BACKUP_WORK_STORAGE_UNAVAILABLE")
        var removed = 0L
        // Retired private work/backup trees explicitly released by the owner.
        for (name in listOf("pixel-backup", "full-backup-work", "browser-media-upload", "before-restore",
            "deletion-backups", "legacy-conflict-backups", "backup-key-protection", "local-authority-review", "local-authority")) {
            val directory = File(parent, name)
            checkCancelled()
            removed += removeWorkTree(directory)
        }
        return removed
    }

    @JvmStatic
    @Throws(IOException::class)
    fun removeWorkTree(directory: File): Long {
        checkCancelled()
        if (Files.isSymbolicLink(directory.toPath())) { Files.delete(directory.toPath()); return 0 }
        if (!directory.exists()) return 0
        val bytes = directory.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }
            .filter { !Files.isSymbolicLink(it.toPath()) && it.isFile }.sumOf { it.length() }
        removeOwned(directory)
        return bytes
    }

    @JvmStatic
    @Throws(IOException::class)
    fun removeOwned(directory: File) {
        if (Files.isSymbolicLink(directory.toPath())) throw IOException("RESTORE_STAGING_LINK_REJECTED")
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory && !Files.isSymbolicLink(child.toPath())) {
                removeOwned(child)
            } else if (!child.delete()) {
                throw IOException("RESTORE_STAGING_CLEANUP_REQUIRED")
            }
        }
        if (directory.exists() && !directory.delete()) throw IOException("RESTORE_STAGING_CLEANUP_REQUIRED")
    }
}
