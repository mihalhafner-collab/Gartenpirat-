package at.hausgarten.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/** Random JPEG camera handoffs in the dedicated private cache directory. */
class PhotoCaptureProvider : ContentProvider() {
    // Providers start during recovery too; do not open business handles here.
    override fun onCreate() = true

    @Throws(FileNotFoundException::class)
    private fun file(uri: Uri?): File {
        val context = context ?: throw FileNotFoundException()
        RestoreStartupGuard.assertStoreMayOpen(context)
        if (uri == null || uri.scheme != "content" || uri.authority != context.packageName + ".photocapture" || uri.pathSegments.size != 1) throw FileNotFoundException()
        val file = checked(context, uri.lastPathSegment)
        if (!file.isFile) throw FileNotFoundException()
        return file
    }

    override fun getType(uri: Uri): String? = try { file(uri); "image/jpeg" } catch (_: FileNotFoundException) { null }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val target = file(uri)
        return when (mode) {
            "r" -> ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
            "w", "wt" -> ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
            "rw", "rwt" -> ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_WRITE or if (mode == "rwt") ParcelFileDescriptor.MODE_TRUNCATE else 0)
            else -> throw FileNotFoundException("Nur der vorbereitete Kamera-Lese-/Schreibzugriff ist erlaubt.")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = try {
        val value = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        MatrixCursor(columns).apply {
            addRow(Array<Any?>(columns.size) { i -> when (columns[i]) {
                OpenableColumns.SIZE -> value.length()
                OpenableColumns.DISPLAY_NAME -> value.name
                else -> null
            } })
        }
    } catch (_: FileNotFoundException) { null }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        private const val PREFIX = "capture-"
        private const val SUFFIX = ".jpg"

        @JvmStatic @Throws(IOException::class)
        fun createTarget(context: Context): File {
            RestoreStartupGuard.assertStoreMayOpen(context)
            val directory = directory(context)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Kamera-Zwischenspeicher ist nicht verfügbar.")
            cleanupStale(context)
            val target = File(directory, PREFIX + UUID.randomUUID().toString().replace("-", "") + SUFFIX)
            if (!target.createNewFile()) throw IOException("Kameradatei konnte nicht vorbereitet werden.")
            return target
        }

        @JvmStatic @Throws(FileNotFoundException::class)
        fun uri(context: Context, file: File?): Uri {
            RestoreStartupGuard.assertStoreMayOpen(context)
            if (file == null) throw FileNotFoundException("Die vorbereitete Kameradatei fehlt.")
            val checked = checked(context, file.name)
            try {
                // Android can expose the same private cache through a directory alias.
                if (checked != file.canonicalFile || !checked.isFile) throw FileNotFoundException("Die Kameradatei liegt nicht im erlaubten Zwischenspeicher oder fehlt.")
            } catch (error: IOException) {
                throw FileNotFoundException("Die Kameradatei konnte nicht freigegeben werden.").apply { initCause(error) }
            }
            return Uri.Builder().scheme("content").authority(context.packageName + ".photocapture").appendPath(file.name).build()
        }

        /** Release only the exact randomly named handoff owned by a completed/discarded capture. */
        @JvmStatic @Throws(IOException::class)
        fun release(context: Context, uri: Uri?) {
            RestoreStartupGuard.assertStoreMayOpen(context)
            if (uri == null || uri.scheme != "content" || uri.authority != context.packageName + ".photocapture" || uri.pathSegments.size != 1) return
            val target = checked(context, uri.lastPathSegment)
            if (target.exists() && !target.delete()) throw IOException("Die abgeschlossene Kameradatei konnte noch nicht entfernt werden.")
        }

        @JvmStatic fun cleanupStale(context: Context) {
            RestoreStartupGuard.assertStoreMayOpen(context)
            // Age does not prove an interrupted or restored capture is unreferenced.
            // The owning operation must explicitly release it.
        }

        private fun directory(context: Context) = File(context.cacheDir, "photo-capture")
        private fun validName(name: String?) = name != null && name.matches(Regex("capture-[0-9a-f]{32}\\.jpg"))

        @Throws(FileNotFoundException::class)
        private fun checked(context: Context, name: String?): File {
            RestoreStartupGuard.assertStoreMayOpen(context)
            if (!validName(name)) throw FileNotFoundException()
            try {
                val directory = directory(context).canonicalFile
                val file = File(directory, name!!).canonicalFile
                if (directory != file.parentFile) throw FileNotFoundException()
                return file
            } catch (error: IOException) {
                throw FileNotFoundException("Der Kamera-Dateipfad konnte nicht geprüft werden.").apply { initCause(error) }
            }
        }
    }
}
