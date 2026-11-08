package at.hausgarten.service

import java.io.*
import java.nio.file.*
import org.json.*

/** Device identity pictures use the existing private photo files, never a customer record. */
class EquipmentProfilePhotoRules {
    private constructor() {}

    companion object {
        @JvmField val MAX_BYTES: Int = ((20 * 1024) * 1024)

        @Throws(Exception::class)
        @JvmStatic
        fun validate(photo: JSONObject, equipmentId: String) {
            val hash: String = photo.optString("content_sha256")
            val mime: String = photo.optString("mime_type")
            var extension: String =
                (if (mime.equals("image/jpeg")) ".jpg"
                else
                    (if (mime.equals("image/png")) ".png"
                    else
                        (if (mime.equals("image/webp")) ".webp"
                        else
                            (if (mime.equals("image/gif")) ".gif"
                            else (if (mime.equals("image/heic")) ".heic" else "")))))
            if (
                ((((((((((((((photo.optInt("format_version") != 1) ||
                    !photo.optString("id").matches(Regex("eqp_photo_[0-9a-f]{32}"))) ||
                    !equipmentId.equals(photo.optString("equipment_id"))) ||
                    !photo.optString("role").equals("profile")) ||
                    !hash.matches(Regex("[0-9a-f]{64}"))) || extension.isEmpty()) ||
                    !photo.optString("storage_name").equals(hash + extension)) ||
                    (photo.optLong("byte_size") < 1)) ||
                    (photo.optLong("byte_size") > MAX_BYTES)) || (photo.optInt("width") < 1)) ||
                    (photo.optInt("height") < 1)) || (photo.optInt("width") > 16384)) ||
                    (photo.optInt("height") > 16384)) ||
                    (((photo.optInt("width")).toLong() * photo.optInt("height")) > 120000000L))
            ) {
                throw RevierRules.error(
                    "EQUIPMENT_PHOTO_INVALID",
                    "Das Gerätefoto besitzt keine eindeutige geprüfte Zuordnung.",
                )
            }
            val thumb: String = photo.optString("thumbnail_storage_name")
            val thumbHash: String = photo.optString("thumbnail_sha256")
            if (
                (!thumb.isEmpty() &&
                    (!thumbHash.matches(Regex("[0-9a-f]{64}")) ||
                        !thumb.equals("" + thumbHash + ".jpg")))
            ) {
                throw RevierRules.error(
                    "EQUIPMENT_PHOTO_INVALID",
                    "Die Fotovorschau besitzt keine gültige Referenz.",
                )
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun upload(metadata: JSONObject, bytes: ByteArray) {
            if (
                (((((!metadata.optString("kind").equals("photo_upload") ||
                    metadata.has("attached_photo")) || (bytes.size < 1)) ||
                    (bytes.size > MAX_BYTES)) ||
                    (bytes.size.toLong() != metadata.optLong("byte_size"))) ||
                    !McpFileTransfer.sha(bytes).equals(metadata.optString("sha256")))
            ) {
                throw RevierRules.error(
                    "EQUIPMENT_PHOTO_UPLOAD_INVALID",
                    "Die Fotoauswahl ist unvollständig oder bereits verwendet. Auswahl erneut prüfen.",
                )
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun attachment(photo: JSONObject, attachment: JSONObject?) {
            if (attachment == null) {
                throw RevierRules.error(
                    "EQUIPMENT_PHOTO_ORIGINAL_MISSING",
                    "Das lokale Gerätefoto fehlt.",
                )
            }
            for (field in
                arrayOf("content_sha256", "mime_type", "byte_size", "storage_name", "role")) {
                if (
                    ((!photo.has(field) || !attachment!!.has(field)) ||
                        !photo.get(field).toString().equals(attachment!!.get(field).toString()))
                ) {
                    throw RevierRules.error(
                        "EQUIPMENT_PHOTO_INVALID",
                        "Gerätefoto und lokales Original stimmen nicht überein.",
                    )
                }
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun original(photoDirectory: File?, photo: JSONObject) {
            if (photoDirectory == null) {
                throw RevierRules.error(
                    "EQUIPMENT_PHOTO_ORIGINAL_MISSING",
                    "Der geprüfte Fotospeicher fehlt.",
                )
            }
            validate(photo, photo.getString("equipment_id"))
            val file: File = File(photoDirectory, photo.getString("storage_name"))
            if (
                (((((file.canonicalFile.parentFile != photoDirectory.canonicalFile ||
                    Files.isSymbolicLink(file.toPath())) || !file.isFile()) ||
                    (file.length() != photo.getLong("byte_size"))) ||
                    (file.length() > MAX_BYTES)) ||
                    !McpFileTransfer.sha(Files.readAllBytes(file.toPath()))
                        .equals(photo.getString("content_sha256")))
            ) {
                throw RevierRules.error(
                    "EQUIPMENT_PHOTO_ORIGINAL_MISSING",
                    "Das Gerätefoto fehlt oder wurde verändert. Diese Sicherung ist noch nicht vollständig geprüft.",
                )
            }
        }
    }
}
