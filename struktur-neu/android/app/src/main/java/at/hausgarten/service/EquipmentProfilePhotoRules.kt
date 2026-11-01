package at.hausgarten.service

import org.json.*
import java.io.*
import java.nio.file.*

/** Device identity pictures use the existing private photo files, never a customer record. */
class EquipmentProfilePhotoRules {
    private constructor() {
    }
    companion object {
        @JvmField val MAX_BYTES: Int = ((20 * 1024) * 1024)
        @Throws(Exception::class)
        @JvmStatic fun validate(photo: JSONObject, equipmentId: String) {
            var hash: String = photo.optString("content_sha256")
            var mime: String = photo.optString("mime_type")
            var extension: String = (if (mime.equals("image/jpeg")) ".jpg" else (if (mime.equals("image/png")) ".png" else (if (mime.equals("image/webp")) ".webp" else (if (mime.equals("image/gif")) ".gif" else (if (mime.equals("image/heic")) ".heic" else "")))))
            if ((((((((((((((((photo.optInt("format_version") != 1) || !photo.optString("id").matches(Regex("eqp_photo_[0-9a-f]{32}"))) || !equipmentId.equals(photo.optString("equipment_id"))) || !photo.optString("role").equals("profile")) || !hash.matches(Regex("[0-9a-f]{64}"))) || extension.isEmpty()) || !photo.optString("storage_name").equals((hash + extension))) || (photo.optLong("byte_size") < 1)) || (photo.optLong("byte_size") > MAX_BYTES)) || (photo.optInt("width") < 1)) || (photo.optInt("height") < 1)) || (photo.optInt("width") > 16384)) || (photo.optInt("height") > 16384)) || (((photo.optInt("width")).toLong() * photo.optInt("height")) > 120000000L)))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Das Gerätefoto besitzt keine eindeutige geprüfte Zuordnung.")
            }
            var thumb: String = photo.optString("thumbnail_storage_name")
            var thumbHash: String = photo.optString("thumbnail_sha256")
            if (((!thumb.isEmpty() && ((!thumbHash.matches(Regex("[0-9a-f]{64}")) || !thumb.equals(("" + thumbHash + ".jpg"))))))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Die Fotovorschau besitzt keine gültige Referenz.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun upload(metadata: JSONObject, bytes: ByteArray) {
            if (((((((!metadata.optString("kind").equals("photo_upload") || metadata.has("attached_photo")) || (bytes.size < 1)) || (bytes.size > MAX_BYTES)) || (bytes.size.toLong() != metadata.optLong("byte_size"))) || !McpFileTransfer.sha(bytes).equals(metadata.optString("sha256"))))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_UPLOAD_INVALID", "Die Fotoauswahl ist unvollständig oder bereits verwendet. Auswahl erneut prüfen.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun draft(current: JSONObject, source: JSONObject): JSONObject {
            var session: String = source.optString("session_id")
            var selection: String = source.optString("selection")
            var graph: JSONObject? = current.optJSONObject("record")
            var origin: JSONObject? = (if ((graph == null)) null else graph!!.optJSONObject("action_context"))
            if ((((((((((((((((!session.matches(Regex("[A-Za-z0-9_.:-]{8,200}")) || !selection.matches(Regex("[a-f0-9]{32}"))) || !source.optString("media_key").equals(("equipment_" + session))) || !current.optString("key").equals(("customer-draft:" + source.optString("media_key")))) || source.optString("installation_id").isEmpty()) || source.optString("restore_epoch").isEmpty()) || !current.optString("installation_id").equals(source.optString("installation_id"))) || !current.optString("restore_epoch").equals(source.optString("restore_epoch"))) || (graph == null)) || (origin == null)) || !source.optString("installation_id").equals(origin!!.optString("installation_id"))) || !source.optString("restore_epoch").equals(origin!!.optString("restore_epoch"))) || graph!!.optBoolean("_restore_action_review_required")) || !session.equals(graph!!.optString("session_id"))) || !selection.equals(graph!!.optString("selection"))))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE", "Diese Fotoauswahl ist nicht mehr aktuell. Gerätefoto erneut bewusst auswählen; es wurde nichts angelegt.")
            }
            var ref: JSONObject? = graph!!.optJSONObject("file")
            if (((ref == null))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE", "Der lokale Fotoentwurf ist unvollständig.")
            }
            BrowserMediaFiles.validateReference(ref)
            if ((((!ref!!.optString("representation").equals("blob") || (ref!!.optLong("byte_size") < 1)) || (ref!!.optLong("byte_size") > MAX_BYTES)))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Bitte ein unterstütztes Foto bis 20 MB auswählen.")
            }
            return ref
        }
        @Throws(Exception::class)
        @JvmStatic fun draftSession(state: JSONObject, source: JSONObject, request: String) {
            var envelope: JSONObject = (if ((state.optBoolean("present") && !state.optBoolean("review_required"))) JSONObject(state.getString("value")) else JSONObject())
            var session: String = source.optString("session_id")
            var entries: JSONObject? = envelope.optJSONObject("entries")
            var removed: JSONObject? = envelope.optJSONObject("removed")
            var edit: JSONObject? = (if ((entries == null)) null else entries!!.optJSONObject(session))
            var context: JSONObject? = (if ((edit == null)) null else edit!!.optJSONObject("context"))
            var pending: JSONObject? = (if ((edit == null)) null else edit!!.optJSONObject("pending"))
            var args: JSONObject? = (if ((pending == null)) null else pending!!.optJSONObject("args"))
            if ((((((((((((((((envelope.optInt("formatVersion") != 1) || (removed == null)) || removed!!.has(session)) || (edit == null)) || !edit!!.optString("kind").equals("equipment")) || (context == null)) || !source.optString("media_key").equals(context!!.optString("media_key"))) || !source.optString("selection").equals(context!!.optString("photo_selection"))) || (pending == null)) || !pending!!.optString("tool").equals("save_equipment")) || (args == null)) || (args!!.optJSONObject("profile_photo_draft") == null)) || !request.equals(args!!.optString("request_id"))) || !PixelMcpWriteTools.hash(source).equals(PixelMcpWriteTools.hash(args!!.optJSONObject("profile_photo_draft")))))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE", "Diese Gerätebearbeitung wurde verworfen oder geändert. Aktuellen Entwurf prüfen; es wurde nichts angelegt.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun attachment(photo: JSONObject, attachment: JSONObject?) {
            if (((attachment == null))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_ORIGINAL_MISSING", "Das lokale Gerätefoto fehlt.")
            }
            for (field in arrayOf("content_sha256", "mime_type", "byte_size", "storage_name", "role")) {
                if ((((!photo.has(field) || !attachment!!.has(field)) || !photo.get(field).toString().equals(attachment!!.get(field).toString())))) {
                    throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Gerätefoto und lokales Original stimmen nicht überein.")
                }
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun original(photoDirectory: File?, photo: JSONObject) {
            if (((photoDirectory == null))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_ORIGINAL_MISSING", "Der geprüfte Fotospeicher fehlt.")
            }
            validate(photo, photo.getString("equipment_id"))
            var file: File = File(photoDirectory, photo.getString("storage_name"))
            if (((((((!file.getCanonicalFile().getParentFile().equals(photoDirectory!!.getCanonicalFile()) || Files.isSymbolicLink(file.toPath())) || !file.isFile()) || (file.length() != photo.getLong("byte_size"))) || (file.length() > MAX_BYTES)) || !McpFileTransfer.sha(Files.readAllBytes(file.toPath())).equals(photo.getString("content_sha256"))))) {
                throw RevierRules.error("EQUIPMENT_PHOTO_ORIGINAL_MISSING", "Das Gerätefoto fehlt oder wurde verändert. Diese Sicherung ist noch nicht vollständig geprüft.")
            }
        }
    }
}
