package at.hausgarten.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/** App-private thumbnails only. No originals, free paths or exported provider. */
class PhotoThumbnailProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/jpeg"

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment
        if (mode != "r" || name == null || !name.matches(Regex("[0-9a-f]{64}\\.jpg")) || uri.pathSegments.size != 1) throw FileNotFoundException("Vorschaureferenz ungültig.")
        val store = PhotoAttachmentStore(context!!)
        try {
            LocalAuthorityGuard.assertStoreMayOpen(context!!)
            val f = store.verifiedThumbnailFile(name)
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Exception) { throw FileNotFoundException("Vorschau noch nicht verfügbar.") }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
