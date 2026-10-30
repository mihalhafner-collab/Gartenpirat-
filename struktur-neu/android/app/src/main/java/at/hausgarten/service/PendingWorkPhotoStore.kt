package at.hausgarten.service

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Crash-safe handoff for a work photo whose worklog has already been stored. */
class PendingWorkPhotoStore(private val context: Context) {
    private val file: AtomicFile

    init {
        LocalAuthorityGuard.assertStoreMayOpen(context)
        file = AtomicFile(File(context.filesDir, "pending-work-photo.json"))
    }

    @Synchronized @Throws(Exception::class)
    fun stage(raw: String?): String = synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) { stageLocked(raw) }

    @Throws(Exception::class)
    private fun stageLocked(raw: String?): String {
        LocalAuthorityGuard.assertStoreMayOpen(context)
        if (raw == null) throw IllegalArgumentException("Der Foto-Wiederholauftrag fehlt.")
        val payload = JSONObject(raw)
        if (payload.optString("id").isEmpty()) payload.put("id", "pho_mobile_" + UUID.randomUUID().toString().replace("-", ""))
        if (!payload.has("_capture_context")) {
            val identity = BackupDeviceGuard(context).current()
            payload.put("_capture_context", JSONObject().put("formatVersion", 1).put("installation_id", identity.getString("installationId")).put("restore_epoch", identity.getString("restoreEpoch")))
        }
        val photo = validate(payload)
        if (payload.toString().toByteArray(StandardCharsets.UTF_8).size > MAX_JSON_BYTES) throw IllegalArgumentException("Der Foto-Wiederholauftrag ist zu groß.")
        val existing = read()
        if (existing.isNotEmpty()) {
            if (PixelMcpWriteTools.hash(JSONObject(existing)) != PixelMcpWriteTools.hash(payload)) throw IllegalStateException("Ein anderer Fotoauftrag wartet noch auf Prüfung. Neue Fotoentwürfe über die getrennte Arbeitserfassung anlegen.")
            return existing
        }
        // Keep the caller-visible legacy payload while new durable bytes live in a file.
        // The envelope also preserves every unknown payload field without reserving a key.
        val hash = PhotoAttachmentStore.sha256(photo)
        val target = stagedFile(hash)
        if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw IllegalStateException("Der Foto-Zwischenspeicher ist nicht verfügbar.")
        if (target.exists()) {
            val previous = readPhoto(target)
            if (PhotoAttachmentStore.sha256(previous) != hash) throw IllegalStateException("Das vorbereitete Foto ist beschädigt.")
        } else {
            write(AtomicFile(target), photo)
        }
        val stored = JSONObject(payload.toString())
        stored.remove("data")
        val envelope = JSONObject().put("__hg_pending_photo_file", 1).put("payload", stored)
            .put("sha256", hash).put("byte_size", photo.size)
        val bytes = envelope.toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_JSON_BYTES) throw IllegalArgumentException("Der Foto-Wiederholauftrag ist zu groß.")
        write(file, bytes)
        return payload.toString()
    }

    @Synchronized @Throws(Exception::class)
    fun read(): String = synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) { readLocked() }

    @Throws(Exception::class)
    private fun readLocked(): String {
        LocalAuthorityGuard.assertStoreMayOpen(context)
        val stored = readStored() ?: return ""
        val payload = if (isFileEnvelope(stored)) {
            val hash = stored.getString("sha256")
            val bytes = readPhoto(stagedFile(hash))
            if (bytes.size.toLong() != stored.getLong("byte_size") || PhotoAttachmentStore.sha256(bytes) != hash) throw IllegalStateException("Das vorbereitete Foto ist beschädigt.")
            JSONObject(stored.getJSONObject("payload").toString()).put("data", "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        } else stored // Existing inline pending-work-photo.json remains readable.
        validate(payload)
        if (!payload.has("_capture_context") && BackupDeviceGuard(context).current().optString("lastRestoreRequest").isNotEmpty()) payload.put("_capture_context", JSONObject().put("formatVersion", 1).put("unknown_restore", true))
        return payload.toString()
    }

    @Synchronized fun clear() = synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) { clearLocked() }

    private fun clearLocked() {
        LocalAuthorityGuard.assertStoreMayOpen(context)
        val owned = try {
            val value = readStored()
            if (value != null && isFileEnvelope(value)) {
                val hash = value.getString("sha256")
                val target = stagedFile(hash)
                val bytes = readPhoto(target)
                if (bytes.size.toLong() == value.getLong("byte_size") && PhotoAttachmentStore.sha256(bytes) == hash) target else null
            } else null
        } catch (_: Exception) { null }
        file.delete()
        // Only the verified file named by this discarded handoff is owned here.
        owned?.delete()
    }

    @Throws(Exception::class)
    private fun readStored(): JSONObject? {
        val source = file.baseFile
        if (!source.isFile && !File(source.toString() + ".bak").isFile) return null
        val output = ByteArrayOutputStream()
        val block = ByteArray(8192)
        file.openRead().use { input ->
            // openRead restores the previous valid .bak after an interrupted write.
            if (source.length() <= 0 || source.length() > MAX_JSON_BYTES) throw IllegalStateException("Der gespeicherte Foto-Wiederholauftrag ist beschädigt oder zu groß.")
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_JSON_BYTES) throw IllegalStateException("Der gespeicherte Foto-Wiederholauftrag ist zu groß.")
                output.write(block, 0, count)
            }
        }
        return JSONObject(String(output.toByteArray(), StandardCharsets.UTF_8))
    }

    private fun stagedFile(hash: String): File {
        if (!hash.matches(Regex("[0-9a-f]{64}"))) throw IllegalStateException("Das vorbereitete Foto ist beschädigt.")
        return File(File(context.filesDir, "pending-work-photo-files"), "$hash.jpg")
    }

    private fun readPhoto(source: File): ByteArray {
        if (!source.isFile || source.length() < 1 || source.length() > PhotoAttachmentStore.MAX_BYTES) throw IllegalStateException("Das vorbereitete Foto ist beschädigt.")
        val output = ByteArrayOutputStream(source.length().toInt())
        FileInputStream(source).use { input ->
            val block = ByteArray(8192)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                if (output.size() + count > PhotoAttachmentStore.MAX_BYTES) throw IllegalStateException("Das vorbereitete Foto ist zu groß oder beschädigt.")
                output.write(block, 0, count)
            }
        }
        return output.toByteArray()
    }

    companion object {
        private fun isFileEnvelope(value: JSONObject) = !value.has("data") && !value.has("id") && value.optInt("__hg_pending_photo_file") == 1 && value.optJSONObject("payload") != null

        private const val MAX_JSON_BYTES = ((PhotoAttachmentStore.MAX_BYTES + 2L) / 3L) * 4L + 64L * 1024L

        @Throws(Exception::class)
        private fun write(file: AtomicFile, bytes: ByteArray) {
            var output: FileOutputStream? = null
            try {
                output = file.startWrite()
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Exception) {
                if (output != null) file.failWrite(output)
                throw error
            }
        }

        private fun validate(payload: JSONObject): ByteArray {
            val id = payload.optString("id").trim()
            if (!id.matches(Regex("[A-Za-z0-9._:-]{1,180}"))) throw IllegalArgumentException("Die Foto-ID des Wiederholauftrags ist ungültig.")
            if (payload.optString("customer_id").trim().isEmpty() || payload.optString("worklog_id").trim().isEmpty()) throw IllegalArgumentException("Kunde und Arbeitsaufzeichnung des Fotos fehlen.")
            val data = payload.optString("data").trim()
            val prefix = "data:image/jpeg;base64,"
            if (!data.startsWith(prefix)) throw IllegalArgumentException("Der Wiederholauftrag enthält kein vorbereitetes JPEG-Foto.")
            val encoded = data.substring(prefix.length)
            if (encoded.length > ((PhotoAttachmentStore.MAX_BYTES + 2L) / 3L) * 4L + 8L || encoded.length % 4 != 0) throw IllegalArgumentException("Das vorbereitete Foto ist zu groß oder beschädigt.")
            val bytes = try { Base64.decode(encoded, Base64.DEFAULT) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("Das vorbereitete Foto ist beschädigt.") }
            if (bytes.isEmpty() || bytes.size > PhotoAttachmentStore.MAX_BYTES || PhotoAttachmentStore.sniff(bytes) != "image/jpeg") throw IllegalArgumentException("Der Wiederholauftrag enthält kein gültiges JPEG-Foto.")
            return bytes
        }
    }
}
