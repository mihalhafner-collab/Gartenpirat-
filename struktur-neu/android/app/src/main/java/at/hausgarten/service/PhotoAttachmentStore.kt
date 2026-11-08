package at.hausgarten.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** Private content-addressed originals; metadata stays in its existing owner record. */
class PhotoAttachmentStore(context: Context) {
    private val directory = File(context.filesDir, "customer-photo-originals")
    private val staging = File(context.cacheDir, "immutable-photo-staging")

    class Prepared(
        @JvmField val bytes: ByteArray,
        @JvmField val metadata: JSONObject,
        @JvmField val created: Boolean,
    )

    @Throws(Exception::class) fun prepare(payload: JSONObject): Prepared = prepare(payload, false)

    @Throws(Exception::class)
    fun prepareLegacy(payload: JSONObject): Prepared = prepare(payload, true)

    @Throws(Exception::class)
    private fun prepare(payload: JSONObject, legacy: Boolean): Prepared {
        var value = payload.optString("data", "").trim()
        if (value.isEmpty()) throw IllegalArgumentException("Kunde und Foto sind erforderlich.")
        var declared = ""
        val comma = value.indexOf(',')
        if (value.startsWith("data:")) {
            if (comma < 0 || !value.substring(0, comma).lowercase(Locale.ROOT).contains(";base64"))
                throw IllegalArgumentException("Das Foto besitzt kein gültiges Base64-Datenformat.")
            val header = value.substring(5, comma)
            val semicolon = header.indexOf(';')
            declared = canonicalMime(if (semicolon < 0) header else header.substring(0, semicolon))
            value = value.substring(comma + 1)
        }
        if (
            value.length > ((MAX_BYTES + 2L) / 3L) * 4L + 8L ||
                value.length % 4 != 0 ||
                !value.matches(Regex("[A-Za-z0-9+/]*={0,2}"))
        )
            throw IllegalArgumentException("Das Foto besitzt keine gültigen Base64-Daten.")
        var bytes =
            try {
                Base64.decode(value, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Das Foto besitzt keine gültigen Base64-Daten.")
            }
        val before = inspect(bytes, declared)
        var role = payload.optString("role", "").trim().lowercase(Locale.ROOT)
        val caption = payload.optString("caption", "").trim().lowercase(Locale.ROOT)
        if (role.isEmpty() && caption in listOf("profilbild", "profile", "profilfoto"))
            role = "profile"
        if (role !in ROLES) role = "documentation"
        if (!legacy)
            bytes =
                optimize(
                    bytes,
                    before,
                    payload.optString("_photo_quality", "optimized"),
                    role == "profile",
                )
        val metadata = inspect(bytes, "").put("role", role)
        metadata
            .put("original_width", before.getInt("width"))
            .put("original_height", before.getInt("height"))
            .put("original_byte_size", before.getLong("byte_size"))
            .put(
                "optimization",
                if (legacy) "preserved_legacy" else payload.optString("_photo_quality", "optimized"),
            )
        val created = store(bytes, metadata)
        if (!legacy)
            try {
                makeThumbnail(bytes, metadata)
            } catch (_: Exception) {
                /* Original stays verified; regenerate previews on demand. */
            }
        return Prepared(bytes, metadata, created)
    }

    @Throws(Exception::class)
    private fun makeThumbnail(bytes: ByteArray, metadata: JSONObject) {
        val raw = displayBytes(bytes, metadata)
        val hash = sha256(raw)
        storeThumbnail(raw, "$hash.jpg")
        metadata.put("thumbnail_storage_name", "$hash.jpg").put("thumbnail_sha256", hash)
    }

    // Compatibility renderer uses a bounded decode from the verified original.
    @Throws(Exception::class)
    fun displayDataUri(metadata: JSONObject): String =
        "data:image/jpeg;base64," +
            Base64.encodeToString(displayBytes(read(metadata), metadata), Base64.NO_WRAP)

    @Throws(Exception::class)
    private fun displayBytes(bytes: ByteArray, metadata: JSONObject): ByteArray {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (
            maxOf(metadata.getInt("width"), metadata.getInt("height")) /
                (options.inSampleSize * 2) > 480
        ) options.inSampleSize *= 2
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var small: Bitmap? = null
        try {
            decoded =
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw IOException("Vorschau nicht lesbar.")
            oriented = orient(decoded, PhotoPolicy.orientation(bytes))
            val scale = minOf(1.0, 240.0 / maxOf(oriented.width, oriented.height))
            small =
                Bitmap.createScaledBitmap(
                    oriented,
                    maxOf(1, (oriented.width * scale).toInt()),
                    maxOf(1, (oriented.height * scale).toInt()),
                    true,
                )
            val out = ByteArrayOutputStream()
            if (
                !small.compress(Bitmap.CompressFormat.JPEG, 65, out) ||
                    out.size() == 0 ||
                    out.size() > 65536
            )
                throw IOException("Gerätebild kann nicht dargestellt werden.")
            return out.toByteArray()
        } catch (_: OutOfMemoryError) {
            throw IOException("Gerätebild kann nicht geladen werden.")
        } finally {
            if (small != null && small !== oriented) small.recycle()
            if (oriented != null && oriented !== decoded) oriented.recycle()
            decoded?.recycle()
        }
    }

    @Throws(Exception::class)
    fun thumbnailFile(name: String): File {
        if (!name.matches(Regex("[0-9a-f]{64}\\.jpg")))
            throw IllegalArgumentException("Ungültige Vorschaureferenz.")
        return File(File(directory, "thumbnails"), name)
    }

    @Throws(Exception::class)
    fun storeThumbnail(bytes: ByteArray, name: String) {
        if (
            bytes.isEmpty() ||
                bytes.size > 65536 ||
                sniff(bytes) != "image/jpeg" ||
                name != sha256(bytes) + ".jpg"
        )
            throw IOException("Vorschau-Prüfsumme ist ungültig.")
        synchronized(FILE_LOCK) {
            val target = thumbnailFile(name)
            val dir = target.parentFile!!
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Vorschauspeicher fehlt.")
            if (target.exists()) {
                readThumbnail(name)
                return
            }
            publish(bytes, target, name.removeSuffix(".jpg"))
            readThumbnail(name)
        }
    }

    @Throws(Exception::class)
    fun readThumbnail(name: String): ByteArray {
        val f = thumbnailFile(name)
        if (
            !f.isFile ||
                f.length() !in 1..65536 ||
                f.canonicalFile.parentFile != File(directory, "thumbnails").canonicalFile
        )
            throw IOException("Vorschau fehlt oder ist zu groß.")
        val output = ByteArrayOutputStream()
        FileInputStream(f).use { input ->
            val block = ByteArray(8192)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                if (output.size() + count > 65536) throw IOException("Vorschau ist zu groß.")
                output.write(block, 0, count)
            }
        }
        val bytes = output.toByteArray()
        if (bytes.size > 65536 || name != sha256(bytes) + ".jpg")
            throw IOException("Vorschau-Prüfsumme ist ungültig.")
        return bytes
    }

    /** Small immutable preview only; this path never opens or hashes an original. Resolve on IO. */
    @Throws(Exception::class)
    fun verifiedThumbnailFile(name: String): File {
        readThumbnail(name)
        return thumbnailFile(name)
    }

    @Throws(Exception::class)
    private fun optimize(
        bytes: ByteArray,
        before: JSONObject,
        quality: String,
        profile: Boolean,
    ): ByteArray {
        if (quality == "original") return bytes // Explicit choice; never reprocess existing files.
        val orientation = PhotoPolicy.orientation(bytes)
        val edge = PhotoPolicy.edge(quality, profile)
        if (
            maxOf(before.getInt("width"), before.getInt("height")) <= edge &&
                bytes.size <= 3 * 1024 * 1024 &&
                orientation == 1
        )
            return PhotoPolicy.withoutMetadata(bytes, before.getString("mime_type"))
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (
                maxOf(before.getInt("width"), before.getInt("height")) /
                    (options.inSampleSize * 2) > edge
            ) options.inSampleSize *= 2
            decoded =
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw IOException("Foto kann nicht optimiert werden.")
            oriented = orient(decoded, orientation)
            val scale = minOf(1.0, edge.toDouble() / maxOf(oriented.width, oriented.height))
            scaled =
                Bitmap.createScaledBitmap(
                    oriented,
                    maxOf(1, Math.round(oriented.width * scale).toInt()),
                    maxOf(1, Math.round(oriented.height * scale).toInt()),
                    true,
                )
            var result = ByteArray(0)
            for (qualityValue in intArrayOf(if (quality == "high") 92 else 84, 80, 76)) {
                val out = ByteArrayOutputStream()
                if (
                    !scaled.compress(Bitmap.CompressFormat.JPEG, qualityValue, out) ||
                        out.size() == 0
                )
                    throw IOException("Optimiertes Foto ist leer.")
                result = out.toByteArray()
                if (result.size <= 3 * 1024 * 1024) break
            }
            inspect(result, "image/jpeg")
            return result
        } catch (_: OutOfMemoryError) {
            throw IOException(
                "Für dieses Foto ist zu wenig Arbeitsspeicher verfügbar. Entwurf bleibt erhalten."
            )
        } finally {
            if (scaled != null && scaled !== oriented) scaled.recycle()
            if (oriented != null && oriented !== decoded) oriented.recycle()
            decoded?.recycle()
        }
    }

    @Throws(Exception::class)
    fun storageSummary(): JSONObject {
        var bytes = 0L
        var count = 0
        for (f in directory.listFiles() ?: emptyArray()) if (f.isFile && canonicalName(f.name)) {
            bytes += f.length()
            count++
        }
        var thumbnailBytes = 0L
        for (f in File(directory, "thumbnails").listFiles() ?: emptyArray()) if (
            f.isFile && f.name.matches(Regex("[0-9a-f]{64}\\.jpg"))
        )
            thumbnailBytes += f.length()
        return JSONObject()
            .put("local_files", count)
            .put("original_bytes", bytes)
            .put("thumbnail_bytes", thumbnailBytes)
            .put("local_bytes", bytes + thumbnailBytes)
            .put("available_bytes", directory.parentFile!!.usableSpace)
            .put("automatic_deletion", false)
    }

    @Throws(Exception::class)
    fun inspect(bytes: ByteArray?, declaredMime: String?): JSONObject {
        if (bytes == null || bytes.isEmpty()) throw IllegalArgumentException("Das Foto ist leer.")
        if (bytes.size > MAX_BYTES)
            throw IllegalArgumentException("Das Foto darf maximal 20 MB groß sein.")
        val mime = sniff(bytes)
        if (mime.isEmpty()) throw IllegalArgumentException("Die Datei ist kein unterstütztes Foto.")
        val declared = canonicalMime(declaredMime)
        if (declared.isNotEmpty() && mime != declared)
            throw IllegalArgumentException(
                "Dateityp und tatsächlicher Bildinhalt stimmen nicht überein."
            )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw IllegalArgumentException(
                "Das Foto ist beschädigt oder kann nicht gelesen werden."
            )
        if (
            bounds.outWidth > MAX_EDGE ||
                bounds.outHeight > MAX_EDGE ||
                bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS
        )
            throw IllegalArgumentException("Das Foto besitzt unzulässig große Bildabmessungen.")
        val decode = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / decode.inSampleSize > 2048) decode
            .inSampleSize *= 2
        var verified: Bitmap? = null
        try {
            verified = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
            if (verified == null)
                throw IllegalArgumentException(
                    "Das Foto ist beschädigt oder kann nicht gelesen werden."
                )
        } catch (_: OutOfMemoryError) {
            throw IllegalArgumentException("Das Foto kann nicht speicherschonend geprüft werden.")
        } finally {
            verified?.recycle()
        }
        val hash = sha256(bytes)
        val extension =
            when (mime) {
                "image/jpeg" -> ".jpg"
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                else -> ".heic"
            }
        return JSONObject()
            .put("content_sha256", hash)
            .put("mime_type", mime)
            .put("byte_size", bytes.size)
            .put("storage_name", hash + extension)
            .put("width", bounds.outWidth)
            .put("height", bounds.outHeight)
    }

    @Throws(Exception::class)
    fun store(bytes: ByteArray, metadata: JSONObject): Boolean {
        val name = metadata.getString("storage_name")
        if (!canonicalName(name))
            throw IllegalArgumentException("Ungültiger interner Foto-Dateiname.")
        synchronized(FILE_LOCK) {
            if (!directory.isDirectory && !directory.mkdirs())
                throw IOException("Privater Foto-Speicher ist nicht verfügbar.")
            val target = File(directory, name)
            if (target.exists()) {
                verify(target, metadata)
                return false
            }
            val actual = inspect(bytes, metadata.optString("mime_type"))
            if (
                actual.getString("content_sha256") != metadata.optString("content_sha256") ||
                    actual.getLong("byte_size") != metadata.optLong("byte_size") ||
                    actual.getString("storage_name") != name
            )
                throw IOException("Die Foto-Metadaten stimmen nicht mit dem Original überein.")
            publish(bytes, target, actual.getString("content_sha256"))
            verifiedFile(metadata)
            return true
        }
    }

    /** Only a complete synced inode enters the directories treated as immutable by backup. */
    @Throws(Exception::class)
    private fun publish(bytes: ByteArray, target: File, expectedHash: String) {
        if (!staging.isDirectory && !staging.mkdirs())
            throw IOException("Foto-Zwischenspeicher ist nicht verfügbar.")
        val temporary = File.createTempFile("photo-", ".tmp", staging)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (temporary.length() != bytes.size.toLong() || fileHash(temporary) != expectedHash)
                throw IOException("Die vorbereitete Fotodatei hat eine abweichende Prüfsumme.")
            // FILE_LOCK serializes app publishers; final paths are never opened for writing.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    private fun fileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val block = ByteArray(8192)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                digest.update(block, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            String.format(Locale.ROOT, "%02x", it.toInt() and 255)
        }
    }

    @Throws(Exception::class)
    fun storeExpected(bytes: ByteArray, expected: JSONObject) {
        val actual = inspect(bytes, expected.optString("mime_type"))
        if (
            actual.getString("content_sha256") != expected.optString("content_sha256") ||
                actual.getLong("byte_size") != expected.optLong("byte_size") ||
                actual.getString("storage_name") != expected.optString("storage_name")
        )
            throw IllegalArgumentException(
                "Die Foto-Metadaten stimmen nicht mit dem Original überein."
            )
        store(bytes, actual)
    }

    fun file(metadata: JSONObject): File = File(directory, metadata.optString("storage_name"))

    @Throws(Exception::class)
    fun read(metadata: JSONObject): ByteArray = verify(file(metadata), metadata)

    /** Resolve off the UI thread before giving Coil this verified private original. */
    @Throws(Exception::class)
    fun verifiedFile(metadata: JSONObject): File {
        val target = file(metadata)
        if (
            !canonicalName(metadata.optString("storage_name")) ||
                target.canonicalFile.parentFile != directory.canonicalFile
        )
            throw IOException("Ungültiger interner Foto-Dateiname.")
        // Stream the integrity check: Coil alone owns decoding and caching for native tiles.
        if (
            !target.isFile ||
                target.length() > MAX_BYTES ||
                target.length() != metadata.optLong("byte_size")
        )
            throw IOException("Das Foto-Original fehlt oder ist zu groß.")
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(target).use { input ->
            val block = ByteArray(8192)
            var countTotal = 0L
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                countTotal += count
                if (countTotal > MAX_BYTES) throw IOException("Das Foto-Original ist zu groß.")
                digest.update(block, 0, count)
            }
            if (countTotal != metadata.optLong("byte_size"))
                throw IOException("Das gespeicherte Foto-Original hat eine abweichende Prüfsumme.")
        }
        val actual =
            digest.digest().joinToString("") {
                String.format(Locale.ROOT, "%02x", it.toInt() and 255)
            }
        if (actual != metadata.optString("content_sha256") || !target.name.startsWith(actual + "."))
            throw IOException("Das gespeicherte Foto-Original hat eine abweichende Prüfsumme.")
        return target
    }

    @Throws(Exception::class)
    fun deleteThumbnailVerified(name: String) {
        synchronized(FILE_LOCK) {
            if (!name.matches(Regex("[0-9a-f]{64}\\.jpg"))) throw IOException("Ungültige Vorschau.")
            val target = thumbnailFile(name)
            if (!target.exists()) return
            if (name != sha256(readThumbnail(name)) + ".jpg")
                throw IOException("Vorschau verändert.")
            if (!target.delete()) throw IOException("Vorschau konnte nicht entfernt werden.")
        }
    }

    @Throws(Exception::class)
    fun discardCreated(metadata: JSONObject) {
        synchronized(FILE_LOCK) {
            val target = file(metadata)
            if (!target.exists()) return
            verify(target, metadata)
            if (!target.delete())
                throw IOException("Unvollständig gespeichertes Foto konnte nicht bereinigt werden.")
        }
    }

    @Throws(Exception::class)
    fun dataUri(metadata: JSONObject): String =
        "data:" +
            metadata.getString("mime_type") +
            ";base64," +
            Base64.encodeToString(read(metadata), Base64.NO_WRAP)

    @Throws(Exception::class)
    private fun verify(file: File, metadata: JSONObject): ByteArray {
        if (!file.isFile || file.length() > MAX_BYTES)
            throw IOException("Das Foto-Original fehlt oder ist zu groß.")
        val output = ByteArrayOutputStream(file.length().toInt())
        FileInputStream(file).use { input ->
            val block = ByteArray(8192)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES)
                    throw IOException("Das Foto-Original ist zu groß.")
                output.write(block, 0, count)
            }
        }
        val bytes = output.toByteArray()
        val actual = inspect(bytes, metadata.optString("mime_type"))
        if (
            actual.getString("content_sha256") != metadata.optString("content_sha256") ||
                actual.getLong("byte_size") != metadata.optLong("byte_size") ||
                file.name != metadata.optString("storage_name")
        )
            throw IOException("Das gespeicherte Foto-Original hat eine abweichende Prüfsumme.")
        return bytes
    }

    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        const val MAX_PIXELS = 120_000_000L
        const val MAX_EDGE = 16_384
        private val FILE_LOCK = Any()
        private val ROLES =
            setOf("profile", "documentation", "before", "during", "after", "damage", "other")

        private fun orient(decoded: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                2 -> matrix.setScale(-1f, 1f)
                3 -> matrix.setRotate(180f)
                4 -> matrix.setScale(1f, -1f)
                5 -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                6 -> matrix.setRotate(90f)
                7 -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                8 -> matrix.setRotate(-90f)
            }
            return if (orientation == 1) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun legacyName(photo: JSONObject): String {
            val name = photo.optString("filename")
            if (
                !name.matches(
                    Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,179}\\.(?:jpg|jpeg|png|webp|gif|heic|heif)")
                ) || name.contains("..") || photo.optString("storage_name").isNotEmpty()
            )
                throw PixelMcpWriteTools.Rejected(
                    "LEGACY_MEDIA_UNAVAILABLE",
                    "Altes Foto benötigt eine sichere Dateireferenz und sein Original im freigegebenen Fotoordner.",
                )
            return name
        }

        @JvmStatic
        fun canonicalName(name: String?): Boolean =
            name != null && name.matches(Regex("[0-9a-f]{64}\\.(?:jpg|png|webp|gif|heic)"))

        @JvmStatic
        fun canonicalMime(value: String?): String =
            when (val mime = value?.trim()?.lowercase(Locale.ROOT) ?: "") {
                "image/jpg",
                "image/pjpeg" -> "image/jpeg"
                "image/heif" -> "image/heic"
                else -> mime
            }

        @JvmStatic
        fun sniff(bytes: ByteArray): String {
            if (
                bytes.size >= 3 &&
                    bytes[0].toInt() and 255 == 255 &&
                    bytes[1].toInt() and 255 == 216 &&
                    bytes[2].toInt() and 255 == 255
            )
                return "image/jpeg"
            if (
                bytes.size >= 8 &&
                    bytes[0].toInt() and 255 == 137 &&
                    bytes[1] == 'P'.code.toByte() &&
                    bytes[2] == 'N'.code.toByte() &&
                    bytes[3] == 'G'.code.toByte() &&
                    bytes[4].toInt() and 255 == 13 &&
                    bytes[5].toInt() and 255 == 10 &&
                    bytes[6].toInt() and 255 == 26 &&
                    bytes[7].toInt() and 255 == 10
            )
                return "image/png"
            if (
                bytes.size >= 6 &&
                    String(bytes, 0, 6, StandardCharsets.US_ASCII) in listOf("GIF87a", "GIF89a")
            )
                return "image/gif"
            if (
                bytes.size >= 12 &&
                    String(bytes, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
                    String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
            )
                return "image/webp"
            if (
                bytes.size >= 12 &&
                    String(bytes, 4, 4, StandardCharsets.US_ASCII) == "ftyp" &&
                    String(bytes, 8, 4, StandardCharsets.US_ASCII) in
                        listOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
            )
                return "image/heic"
            return ""
        }

        @JvmStatic
        @Throws(Exception::class)
        fun sha256(bytes: ByteArray): String = buildString {
            for (value in MessageDigest.getInstance("SHA-256").digest(bytes)) append(
                String.format(Locale.ROOT, "%02x", value.toInt() and 255)
            )
        }
    }
}
