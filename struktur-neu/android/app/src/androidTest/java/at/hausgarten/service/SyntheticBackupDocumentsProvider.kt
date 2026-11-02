package at.hausgarten.service

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/** A real SAF provider in the test APK and a separate UID/process from the app. */
class SyntheticBackupDocumentsProvider : DocumentsProvider() {
    companion object {
        const val AUTHORITY = "at.hausgarten.service.test.backupdocs"
        const val ROOT_LABEL = "QA synthetic backups"
        const val ROOT_ID = "qa_root"
        const val CONTROL = "qa_backup_"
        val CONTROL_URI: Uri = Uri.parse("content://$AUTHORITY/qa-control")
        fun document(id: String): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, id)
    }

    private val root: File get() = File(requireNotNull(context).noBackupFilesDir, "qa-documents")
    private val index: File get() = File(root, "index.json")

    override fun onCreate(): Boolean {
        require(Build.HARDWARE in listOf("ranchu", "goldfish")) { "DISPOSABLE_EMULATOR_REQUIRED" }
        require(requireNotNull(context).packageName == "at.hausgarten.service.test") { "TEST_APK_REQUIRED" }
        return true
    }

    private fun state(): JSONObject {
        if (!index.isFile) throw FileNotFoundException("QA_PROVIDER_NOT_PREPARED")
        return JSONObject(index.readText(Charsets.UTF_8)).also {
            require(it.getString("fixture") == "synthetic-backup-provider-v1")
        }
    }

    private fun write(value: JSONObject) {
        val file = android.util.AtomicFile(index)
        val stream = file.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    private fun file(id: String): File {
        if (!id.matches(Regex("qa_[a-f0-9]{32}"))) throw FileNotFoundException("QA_DOCUMENT_ID_REQUIRED")
        return File(root, "$id.zip")
    }

    @Synchronized
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!method.startsWith(CONTROL)) return super.call(method, arg, extras)
        // ContentProvider.call has no path-specific permission check. Require the actual shell UID;
        // adopting permissions in the target process must never authorize fixture administration.
        if (method != CONTROL + "shell" || Binder.getCallingUid() != Process.SHELL_UID) {
            throw SecurityException("QA_CONTROL_REQUIRES_ACTUAL_SHELL_UID")
        }
        val request = JSONObject(String(Base64.decode(requireNotNull(arg), Base64.URL_SAFE), Charsets.UTF_8))
        val arguments = Bundle().apply {
            if (request.has("fault")) putString("fault", request.getString("fault"))
            if (request.has("read_only")) putBoolean("read_only", request.getBoolean("read_only"))
        }
        val caller = Binder.getCallingUid()
        val identity = Binder.clearCallingIdentity()
        try {
            val result = control(CONTROL + request.getString("method"), request.optString("id", ""), arguments)
            val values = JSONObject()
            for (key in result.keySet()) values.put(key, result.get(key))
            val response = JSONObject().put("caller_uid", caller).put("result", values)
            return Bundle().apply {
                putString("qa_result", Base64.encodeToString(response.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP))
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun control(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method == CONTROL + "prepare") {
            if (root.exists() && root.listFiles()?.isNotEmpty() != false) throw IOException("FRESH_PROVIDER_FIXTURE_REQUIRED")
            if (!root.isDirectory && !root.mkdirs()) throw IOException("QA_PROVIDER_STORAGE_UNAVAILABLE")
            write(JSONObject().put("fixture", "synthetic-backup-provider-v1").put("documents", JSONObject()))
            requireNotNull(context).contentResolver.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
            return Bundle.EMPTY
        }
        val value = state()
        val documents = value.getJSONObject("documents")
        val id = arg ?: ""
        when (method.removePrefix(CONTROL)) {
            "create" -> {
                val created = create(value, "QA synthetic ${documents.length()}.zip")
                return Bundle().apply { putString("id", created) }
            }
            "stats" -> return Bundle().apply {
                putString("state", value.toString())
                putInt("pid", android.os.Process.myPid())
                putInt("uid", android.os.Process.myUid())
            }
            "fault" -> {
                val fault = extras?.getString("fault") ?: "NONE"
                require(fault in listOf("NONE", "WRITE_OPEN", "WRITE_STREAM", "READ_OPEN", "READ_TRUNCATED"))
                documents.getJSONObject(id).put("fault", fault)
                write(value)
            }
            "grant" -> {
                documents.getJSONObject(id)
                requireNotNull(context).grantUriPermission("at.hausgarten.service", document(id),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (extras?.getBoolean("read_only") == true) 0 else Intent.FLAG_GRANT_WRITE_URI_PERMISSION) or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            "revoke" -> requireNotNull(context).revokeUriPermission(document(id),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            else -> throw IllegalArgumentException("UNKNOWN_QA_PROVIDER_CONTROL")
        }
        return Bundle.EMPTY
    }

    private fun create(value: JSONObject, title: String): String {
        val id = "qa_" + UUID.randomUUID().toString().replace("-", "")
        file(id).writeBytes(byteArrayOf())
        value.getJSONObject("documents").put(id, JSONObject().put("title", title).put("fault", "NONE")
            .put("writes", 0).put("reads", 0))
        value.put("last_created", id)
        write(value)
        return id
    }

    @Synchronized
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (parentDocumentId != ROOT_ID || mimeType != "application/zip") throw FileNotFoundException("QA_ZIP_ONLY")
        return create(state(), displayName)
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
        return MatrixCursor(columns).apply {
            if (index.isFile) addRow(columns.map {
                when (it) {
                    DocumentsContract.Root.COLUMN_ROOT_ID -> ROOT_ID
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID -> ROOT_ID
                    DocumentsContract.Root.COLUMN_TITLE -> ROOT_LABEL
                    DocumentsContract.Root.COLUMN_FLAGS -> DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_LOCAL_ONLY
                    DocumentsContract.Root.COLUMN_MIME_TYPES -> "application/zip"
                    DocumentsContract.Root.COLUMN_AVAILABLE_BYTES -> root.usableSpace
                    else -> null
                }
            })
        }
    }

    private fun row(cursor: MatrixCursor, id: String, data: JSONObject?) {
        cursor.addRow(cursor.columnNames.map {
            when (it) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> data?.getString("title") ?: ROOT_LABEL
                DocumentsContract.Document.COLUMN_MIME_TYPE -> if (id == ROOT_ID) DocumentsContract.Document.MIME_TYPE_DIR else "application/zip"
                DocumentsContract.Document.COLUMN_FLAGS -> if (id == ROOT_ID) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                DocumentsContract.Document.COLUMN_SIZE -> if (id == ROOT_ID) 0L else file(id).length()
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> if (id == ROOT_ID) 0L else file(id).lastModified()
                else -> null
            }
        })
    }

    private fun cursor(projection: Array<out String>?): MatrixCursor = MatrixCursor(projection ?: arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED))

    @Synchronized
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = cursor(projection).apply {
        row(this, documentId, if (documentId == ROOT_ID) null else state().getJSONObject("documents").getJSONObject(documentId))
    }

    @Synchronized
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        if (parentDocumentId != ROOT_ID) throw FileNotFoundException("QA_ROOT_REQUIRED")
        return cursor(projection).apply {
            val documents = state().getJSONObject("documents")
            documents.keys().forEach { row(this, it, documents.getJSONObject(it)) }
        }
    }

    @Synchronized
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val value = state()
        val document = value.getJSONObject("documents").getJSONObject(documentId)
        val writing = mode.contains('w')
        val counter = if (writing) "writes" else "reads"
        document.put(counter, document.getInt(counter) + 1)
        write(value)
        val fault = document.getString("fault")
        if (fault == if (writing) "WRITE_OPEN" else "READ_OPEN") throw FileNotFoundException("QA_${fault}_FAILURE")
        if (writing && fault == "WRITE_STREAM") {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            Thread({
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                        input.read(ByteArray(1024))
                        pipe[0].closeWithError("QA_WRITE_STREAM_FAILURE")
                    }
                } catch (_: IOException) { /* Writer must observe a broken pipe. */ }
            }, "qa-provider-write-fault").start()
            return pipe[1]
        }
        if (!writing && fault == "READ_TRUNCATED") {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            Thread({
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    file(documentId).inputStream().use { input -> output.write(input.readNBytes(32)) }
                }
            }, "qa-provider-short-read").start()
            return pipe[0]
        }
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }
}
