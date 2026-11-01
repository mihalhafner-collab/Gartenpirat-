package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.LinkedHashMap
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Immutable recovery ZIPs in an explicitly supplied app-private directory. */
object DeletionBackupStore {
    const val SCOPE = "deleted_records_and_originals"

    fun interface Originals {
        @Throws(Exception::class)
        fun read(entity: String, record: JSONObject): ByteArray
    }

    @JvmStatic
    @Throws(Exception::class)
    fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val block = ByteArray(16384)
            while (true) {
                val count = input.read(block)
                if (count == -1) break
                digest.update(block, 0, count)
            }
        }
        val out = StringBuilder()
        for (byte in digest.digest()) out.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 255))
        return out.toString()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun importance(type: String, row: JSONObject): JSONArray {
        val out = JSONArray()
        if (type == "invoice" || type == "offer") out.put("financial_document")
        if (type == "worklog") out.put("work_evidence")
        if (type == "photo") out.put("original_media")
        if (type == "customer") out.put("customer_account")
        if (row.optString("notes").trim { it <= ' ' }.isNotEmpty() ||
            row.optString("description").trim { it <= ' ' }.isNotEmpty()) out.put("personal_notes")
        if (row.optString("pdf_storage_name").isNotEmpty()) out.put("original_document")
        if (out.length() == 0) out.put("user_created_record")
        return out
    }

    @JvmStatic
    @Throws(Exception::class)
    fun capture(root: File, snapshot: JSONObject, selected: JSONArray, request: String?, originals: Originals): JSONObject {
        if (selected.length() == 0) throw IllegalArgumentException("Keine bestätigte Löschmenge.")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Private Löschsicherung kann nicht angelegt werden. Nichts gelöscht.")
        val entries = JSONArray()
        val files = JSONArray()
        val counts = JSONObject()
        for (type in DeletionRules.ENTITIES) counts.put(OwnerJournalRules.group(type), 0)
        val sources = LinkedHashMap<String, JSONObject>()
        for (i in 0 until selected.length()) {
            val item = selected.getJSONObject(i)
            val type = item.getString("entity")
            val id = item.getString("entity_id")
            val key = "$type:$id"
            if (!DeletionRules.ENTITIES.contains(type)) throw IllegalArgumentException("Unbekannter Sicherungsbereich.")
            val saved = OwnerJournalRules.archives(snapshot).getJSONObject(key)
            OwnerJournalRules.checkHash(saved, item.getString("expected_record_hash"))
            val row = saved.getJSONObject("record")
            if (id != row.getString("id")) throw IllegalArgumentException("Sicherungs-ID stimmt nicht mit Archiv überein.")
            entries.put(JSONObject().put("archive_key", key).put("archive", saved).put("importance", importance(type, row)))
            val group = OwnerJournalRules.group(type)
            counts.put(group, counts.getInt(group) + 1)
            val name = if (type == "photo") row.optString("storage_name") else row.optString("pdf_storage_name")
            if (name.isEmpty()) continue
            val hash = row.optString(if (type == "photo") "content_sha256" else "pdf_sha256")
            if (!hash.matches(Regex("[0-9a-f]{64}")) || !name.matches(Regex("[0-9a-f]{64}\\.[a-zA-Z0-9]+")) || !name.startsWith("$hash.")) {
                throw IOException("Original hat keine eindeutige Prüfsumme. Nichts gelöscht.")
            }
            if (!sources.containsKey(name)) {
                val file = JSONObject().put("name", name).put("sha256", hash)
                    .put("byte_size", row.getLong(if (type == "photo") "byte_size" else "pdf_byte_size"))
                files.put(file)
                sources[name] = JSONObject().put("entity", type).put("record", row).put("file", file)
            }
        }
        val manifest = JSONObject().put("format", "hausgarten.deletion-backup.v1").put("created_at", Instant.now().toString())
            .put("entries", entries).put("files", files).put("collections", counts).put("includes_credentials", false)
        val temporary = File.createTempFile("deletion-", ".partial", root)
        try {
            FileOutputStream(temporary).use { raw ->
                ZipOutputStream(raw).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toString().toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                    for ((name, source) in sources) {
                        val file = source.getJSONObject("file")
                        val bytes = originals.read(source.getString("entity"), source.getJSONObject("record"))
                        if (bytes.size.toLong() != file.getLong("byte_size") || HttpTransport.sha256(bytes) != file.getString("sha256")) {
                            throw IOException("Original für Löschsicherung stimmt nicht überein. Nichts gelöscht.")
                        }
                        zip.putNextEntry(ZipEntry("originals/$name"))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                    zip.finish()
                    zip.flush()
                    raw.fd.sync()
                }
            }
            verify(temporary, manifest)
            val hash = sha(temporary)
            val target = File(root, "$hash.zip")
            if (target.exists()) {
                if (sha(target) != hash) throw IOException("Vorhandene Löschsicherung ist verändert.")
            } else if (!temporary.renameTo(target)) {
                throw IOException("Löschsicherung konnte nicht dauerhaft gespeichert werden. Nichts gelöscht.")
            }
            val meta = JSONObject().put("kind", "backup").put("scope", SCOPE).put("mime_type", "application/zip")
                .put("storage_name", "$hash.zip").put("sha256", hash).put("byte_size", target.length())
                .put("created_at", manifest.getString("created_at")).put("includes_credentials", false)
                .put("protected_records", entries.length()).put("original_files", files.length())
                .put("collections", counts).put("verified_at_capture", true)
            return JSONObject().put("backup_id", "backup_deletion_" + PixelMcpWriteTools.hash(request))
                .put("metadata", meta).put("collections", counts).put("complete_device_backup", false)
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit()
        }
    }

    @Throws(Exception::class)
    private fun read(input: InputStream): ByteArray = input.use { source ->
        ByteArrayOutputStream().use { out ->
            val block = ByteArray(8192)
            while (true) {
                val count = source.read(block)
                if (count == -1) break
                if (out.size() + count > 16 * 1024 * 1024) throw IOException("Sicherungsmanifest zu groß.")
                out.write(block, 0, count)
            }
            out.toByteArray()
        }
    }

    @Throws(Exception::class)
    private fun verify(file: File, manifest: JSONObject) {
        ZipFile(file).use { zip ->
            if (zip.size() != manifest.getJSONArray("files").length() + 1) throw IOException("Löschsicherung unvollständig.")
            val reread = JSONObject(String(read(zip.getInputStream(zip.getEntry("manifest.json"))), StandardCharsets.UTF_8))
            if (PixelMcpWriteTools.hash(reread) != PixelMcpWriteTools.hash(manifest)) throw IOException("Löschsicherung nicht vollständig zurückgelesen.")
            val files = manifest.getJSONArray("files")
            for (i in 0 until files.length()) {
                val expected = files.getJSONObject(i)
                val entry = zip.getEntry("originals/" + expected.getString("name"))
                if (entry == null || entry.size != expected.getLong("byte_size")) throw IOException("Gesichertes Original fehlt.")
                val digest = MessageDigest.getInstance("SHA-256")
                zip.getInputStream(entry).use { input ->
                    val block = ByteArray(16384)
                    while (true) {
                        val count = input.read(block)
                        if (count == -1) break
                        digest.update(block, 0, count)
                    }
                }
                val hash = StringBuilder()
                for (byte in digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 255))
                if (hash.toString() != expected.getString("sha256")) throw IOException("Gesichertes Original beschädigt.")
            }
        }
    }

    @Throws(Exception::class)
    private fun file(root: File, meta: JSONObject): File {
        val name = meta.getString("storage_name")
        if (SCOPE != meta.optString("scope") || !name.matches(Regex("[0-9a-f]{64}\\.zip")) || name != meta.getString("sha256") + ".zip") {
            throw IOException("Unbekannte Löschsicherung.")
        }
        val file = File(root, name)
        if (file.canonicalFile.parentFile != root.canonicalFile || !file.isFile || file.length() != meta.getLong("byte_size")) {
            throw IOException("Löschsicherung fehlt oder wurde verändert.")
        }
        return file
    }

    @JvmStatic
    @Throws(Exception::class)
    fun inspect(root: File, meta: JSONObject): JSONObject {
        val file = file(root, meta)
        if (sha(file) != meta.getString("sha256")) throw IOException("Löschsicherung beschädigt.")
        ZipFile(file).use { zip ->
            val manifest = JSONObject(String(read(zip.getInputStream(zip.getEntry("manifest.json"))), StandardCharsets.UTF_8))
            if ("hausgarten.deletion-backup.v1" != manifest.optString("format")) throw IOException("Unbekanntes Sicherungsformat.")
            return JSONObject().put("collections", manifest.getJSONObject("collections"))
                .put("protected_records", manifest.getJSONArray("entries").length()).put("original_files", manifest.getJSONArray("files").length())
                .put("restore_mode", "review_deleted_records").put("complete_device_backup", false)
        }
    }

    /** ZIP chunks avoid large SQLite BLOBs and loading a whole archive into memory. */
    @JvmStatic
    @Throws(Exception::class)
    fun chunk(root: File, meta: JSONObject, args: JSONObject): JSONObject {
        val file = file(root, meta)
        val hash = meta.getString("sha256")
        val out = JSONObject().put("resource_type", "backup").put("resource_id", args.getString("resource_id"))
            .put("mime_type", "application/zip").put("byte_size", file.length()).put("sha256", hash)
        if (!args.has("expected_sha256")) {
            if (sha(file) != hash) throw IOException("Löschsicherung beschädigt.")
            return out
        }
        if (hash != args.getString("expected_sha256")) throw PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT", "Sicherungsversion geändert.")
        val offset = args.getInt("offset")
        val length = args.optInt("length", PixelMcpOwnerTools.CHUNK)
        if (offset < 0 || offset > file.length() || length < 1 || length > PixelMcpOwnerTools.CHUNK) {
            throw IllegalArgumentException("Ungültiger Sicherungsbereich.")
        }
        val bytes = ByteArray(Math.min(length.toLong(), file.length() - offset).toInt())
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset.toLong())
            input.readFully(bytes)
        }
        return out.put("offset", offset).put("next_offset", offset + bytes.size).put("eof", (offset + bytes.size).toLong() == file.length())
            .put("chunk_sha256", HttpTransport.sha256(bytes)).put("chunk_base64", Base64.getEncoder().encodeToString(bytes))
    }
}
