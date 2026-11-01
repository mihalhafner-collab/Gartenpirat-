package at.hausgarten.service

import org.json.JSONObject
import java.time.Instant
import java.util.Base64

/** Photo use cases. Effects pass through the existing owner repository transaction. */
class PhotoApplicationService(private val repository: OwnerRepository) : OwnerCommand {
    @Throws(Exception::class)
    override fun perform(name: String, args: JSONObject, request: String): JSONObject {
        val a = args
        val all = repository.effective()
        val stamp = Instant.now().toString()
        return when (name) {
            "begin_file_upload" -> {
                val id = "upload_" + PixelMcpWriteTools.hash(request)
                val m = JSONObject(a.toString()).put("kind", "photo_upload")
                m.remove("request_id")
                repository.putFile(id, m, ByteArray(0))
                JSONObject().put("upload_id", id).put("next_offset", 0).put("max_chunk_bytes", PixelMcpOwnerTools.CHUNK)
            }
            "write_file_chunk" -> {
                val id = a.getString("upload_id")
                val m = repository.fileMeta(id)
                if (m.optString("kind") != "photo_upload" || m.has("attached_photo")) throw IllegalArgumentException("Upload ist nicht beschreibbar.")
                val previous = repository.fileBytes(id)
                val combined = McpFileTransfer.append(previous, a.getString("chunk_base64"), a.getInt("offset"), m.getInt("byte_size"), a.getString("chunk_sha256"))
                repository.putFile(id, m, combined)
                JSONObject().put("upload_id", id).put("next_offset", combined.size).put("complete", combined.size == m.getInt("byte_size"))
            }
            "create_photo" -> {
                val upload = a.getString("upload_id")
                val m = repository.fileMeta(upload)
                val bytes = repository.fileBytes(upload)
                if (m.has("attached_photo") || bytes.size != m.getInt("byte_size") || McpFileTransfer.sha(bytes) != m.getString("sha256")) throw IllegalArgumentException("Upload unvollständig, verändert oder bereits verwendet.")
                val p = JSONObject(a.toString())
                p.remove("request_id"); p.remove("upload_id")
                p.put("id", "pho_mcp_" + PixelMcpWriteTools.hash(request).substring(0, 32)).put("data", "data:" + m.getString("mime_type") + ";base64," + Base64.getEncoder().encodeToString(bytes))
                val id = repository.savePhoto(p)
                m.put("attached_photo", id)
                repository.putFile(upload, m, ByteArray(0))
                JSONObject().put("photo_id", id).put("local_state", "local_saved").put("sync_state", "sync_pending")
            }
            "update_photo" -> {
                val old = PixelMcpWriteTools.find(all.getJSONArray("photos"), a.getString("photo_id"))
                if (PixelMcpWriteTools.hash(PixelMcpOwnerTools.photoHash(old)) != a.getString("expected_record_hash")) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Foto wurde inzwischen geändert.")
                val p = DriveJournalRules.clean(old)
                val updates = a.getJSONObject("updates")
                val keys = updates.keys()
                while (keys.hasNext()) { val key = keys.next(); p.put(key, updates.get(key)) }
                EntityProtectionRules.validatePhoto(p, all.getJSONArray("customers"), all.getJSONArray("orders"), all.getJSONArray("worklogs"))
                ManualChangeRules.prepare("photo", p, old, stamp)
                repository.enqueue("photo", p, request)
            }
            else -> throw IllegalArgumentException("Eigentümerfunktion fehlt.")
        }
    }
}
