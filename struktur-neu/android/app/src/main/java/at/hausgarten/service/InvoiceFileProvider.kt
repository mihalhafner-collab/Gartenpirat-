package at.hausgarten.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Exposes only explicitly shared generated invoice PDFs, never the database. */
open class InvoiceFileProvider : ContentProvider() {
    override fun onCreate() = true

    @Throws(FileNotFoundException::class)
    private fun file(uri: Uri): File {
        RestoreStartupGuard.assertStoreMayOpen(requireNotNull(context))
        val name = uri.lastPathSegment
        if (name == null || !name.matches(Regex("(?:Rechnung|Angebot)-[a-f0-9-]{36}\\.pdf"))) throw FileNotFoundException()
        val value = File(File(context!!.cacheDir, "invoice-share"), name)
        if (!value.isFile) throw FileNotFoundException()
        return value
    }

    override fun getType(uri: Uri) = "application/pdf"

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Nur Lesezugriff erlaubt.")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        return try {
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
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
