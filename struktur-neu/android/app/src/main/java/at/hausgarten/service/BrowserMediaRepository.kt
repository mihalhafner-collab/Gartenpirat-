package at.hausgarten.service

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.*
import java.io.*
import java.util.*

/** Existing owner_state transaction adapter for retained draft/media archives. */
class BrowserMediaRepository {
    fun interface Identity {
        @Throws(Exception::class) fun current(): JSONObject
    }
    private val rules: BrowserMediaRepositoryRules
    @Throws(Exception::class) constructor(context: Context, store: PixelLocalStore) {
        try {

            checkNamespaceVersion(store.getReadableDatabase())
            rules = BrowserMediaRepositoryRules(Values(store.getWritableDatabase(), { BackupDeviceGuard(context).current() }), File(context.getFilesDir(), "browser-media"))

        } catch (failure: Exception) {
            throw safe(failure)
        }
    }
    private @Throws(Exception::class) fun run(work: BrowserMediaRepositoryRules.Work): JSONObject {

        try {

            return work.run()

        } catch (failure: Exception) {
            throw safe(failure)
        }
    }
    @Throws(Exception::class) fun status(): JSONObject {

        return run(rules::status)
    }
    @Throws(Exception::class) fun deferRestoreReview(request: String): JSONObject {

        return run({ rules.deferRestoreReview(request) })
    }
    @Throws(Exception::class) fun readRecord(key: String): JSONObject {

        return run({ rules.readRecord(key) })
    }
    @Throws(Exception::class) fun prepareRecordWrite(request: String, key: String, expected: String): JSONObject {

        return run({ rules.prepareRecordWrite(request, key, expected) })
    }
    @Throws(Exception::class) fun cancelRecordWrite(request: String, key: String, expected: String, reason: String): JSONObject {

        return run({ rules.cancelRecordWrite(request, key, expected, reason) })
    }
    @Throws(Exception::class) fun writeRecord(request: String, key: String, expected: String, graph: JSONObject?): JSONObject {

        return run({ rules.writeRecord(request, key, expected, graph) })
    }
    @Throws(Exception::class) fun cleanupFiles(): JSONObject {

        return run(rules::cleanupFiles)
    }
    @Throws(Exception::class) fun cleanupDeletedKey(key: String, request: String): JSONObject {

        return run({ rules.cleanupDeletedKey(key, request) })
    }
    @Throws(Exception::class) fun beginMirror(request: String, source: String): JSONObject {

        return run({ rules.beginMirror(request, source) })
    }
    @Throws(Exception::class) fun writeMirrorRecord(request: String, source: String, key: String, graph: JSONObject?): JSONObject {

        return run({ rules.writeMirrorRecord(request, source, key, graph) })
    }
    @Throws(Exception::class) fun completeMirror(request: String, source: String, keys: JSONArray): JSONObject {

        var result = run({ rules.completeMirror(request, source, keys) })
        try {

            rules.cleanupFiles()

        } catch (deferred: Exception) {
            result.put("cleanup_pending", true)
        }
        return result
    }
    @Throws(Exception::class) fun reviewCandidates(): JSONObject {

        return run(rules::reviewCandidates)
    }
    @Throws(Exception::class) fun prepareRestoreSelection(request: String, expected: String, selected: JSONArray): JSONObject {

        return run({ rules.prepareRestoreSelection(request, expected, selected) })
    }
    @Throws(Exception::class) fun verifyPreparedRestoreSelection(request: String, expected: String, selected: JSONArray, selectionHash: String): JSONObject {

        return run({ rules.verifyPreparedRestoreSelection(request, expected, selected, selectionHash) })
    }
    @Throws(Exception::class) fun activateSelection(request: String, selectionHash: String): JSONObject {

        var before = rules.reviewCandidates()
        var result = run({ rules.activateSelection("media_activate_" + PixelMcpWriteTools.hash(request), selectionHash) })
        var keys = TreeSet<String>()
        var candidates = before.getJSONArray("candidates")
        for (j in 0 until candidates.length()) {

            keys.add(candidates.getJSONObject(j).getString("key"))

        }
        for (key in keys) {
            try {

                var current = rules.readRecord(key)
                var cleanup = "media_selection_cleanup_" + PixelMcpWriteTools.hash(request + ":" + key)
                rules.prepareRecordWrite(cleanup, key, current.getString("record_hash"))
                rules.writeRecord(cleanup, key, current.getString("record_hash"), (if (current.isNull("record")) null else current.getJSONObject("record")))
                rules.cleanupDeletedKey(key, cleanup)

            } catch (deferred: Exception) {
                result.put("cleanup_pending", true)
            }
        }
        return result
    }
    @Throws(Exception::class) fun assertReadableReference(id: String, ref: JSONObject): Unit {

        try {

            rules.assertReadableReference(id, ref)

        } catch (failure: Exception) {
            throw safe(failure)
        }
    }
    private class Values : BrowserMediaRepositoryRules.Store {
        private val database: SQLiteDatabase
        private val identity: Identity
        @Throws(Exception::class) constructor(database: SQLiteDatabase, identity: Identity) {
            this.database = database
            this.identity = identity
        }
        @Throws(Exception::class) override fun identity(): JSONObject {

            return identity.current()
        }
        @Throws(Exception::class) override fun get(key: String, fallback: String): String {

            return DatabaseBackupSnapshot.ownerValue(database, key, fallback)!!
        }
        override fun put(key: String, value: String): Unit {

            var row = ContentValues()
            row.put("key", key)
            row.put("value", value)
            database.insertWithOnConflict("owner_state", null, row, SQLiteDatabase.CONFLICT_REPLACE)
        }
        override fun remove(key: String): Unit {

            database.delete("owner_state", "key=?", arrayOf(key))
        }
        @Throws(Exception::class) override fun entries(prefix: String): JSONArray {

            var entries = JSONArray()
            database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? ORDER BY key", arrayOf(prefix, prefix + '\uffff')).use { rows ->

                while (rows.moveToNext()) {
                    var key = rows.getString(0)
                    entries.put(JSONObject().put("key", key).put("value", JSONObject(get(key, "{}"))))
                }

            }
            return entries
        }
        @Throws(Exception::class) override fun transaction(work: BrowserMediaRepositoryRules.Work): JSONObject {

            database.beginTransaction()
            try {

                var result = work.run()
                database.setTransactionSuccessful()
                return result

            } finally {
                database.endTransaction()
            }
        }
        override fun fileDeletionAllowed(): Boolean {

            return !(database.inTransaction())
        }
    }
    companion object {
        private val SAFE_CODES: MutableSet<String> = HashSet(Arrays.asList("CONTEXT_INVALID", "VERSION_UNSUPPORTED", "CONTEXT_CHANGED", "STATE_INVALID", "KEY_INVALID", "REQUEST_ID_REQUIRED", "REPLAY_MISMATCH", "RECORD_INVALID", "RECORD_MISSING", "GRAPH_TOO_LARGE", "WRITE_CONFLICT", "RESTORE_REVIEW_REQUIRED", "MIRROR_REQUIRED", "SOURCE_INVALID", "MIRROR_NOT_ALLOWED", "MIRROR_CLOSED", "DUPLICATE_KEY", "MIRROR_INCOMPLETE", "ACTIVE_CONFLICT", "RESTORE_MIRROR_REQUIRED", "SELECTION_INVALID", "SELECTION_REQUIRED", "REFERENCE_NOT_ACTIVE", "REFERENCE_INVALID", "PATH_INVALID", "STORAGE_UNAVAILABLE", "UPLOAD_INVALID", "UPLOAD_REVIEW_REQUIRED", "SIZE_INVALID", "MIME_INVALID", "CHUNK_INVALID", "CHUNK_ORDER_INVALID", "UPLOAD_CLOSED", "UPLOAD_INCOMPLETE", "ORIGINAL_MISSING_OR_CHANGED", "ORIGINAL_MISSING", "GRAPH_INVALID", "GRAPH_SECRET_FIELD", "INLINE_MEDIA_FORBIDDEN", "SPACE_REQUIRED", "INTERRUPTED", "INPUT_INVALID", "WRITE_PREPARATION_REQUIRED", "WRITE_PENDING", "WRITE_CANCELLED", "WRITE_ALREADY_COMMITTED"))
        private @JvmStatic fun safe(failure: Exception): IOException {

            var code = failure.message
            var prefix = "BROWSER_MEDIA_"
            return IOException((if (code != null && code.startsWith(prefix) && SAFE_CODES.contains(code.substring(prefix.length))) code else prefix + "STATE_INVALID"))
        }
        @JvmStatic @Throws(Exception::class) fun installRestored(database: SQLiteDatabase, request: String, identity: JSONObject): Unit {

            try {

                BrowserMediaRepositoryRules(Values(database, { identity }), null).installRestored(request)

            } catch (failure: Exception) {
                throw safe(failure)
            }
        }
        @JvmStatic @Throws(Exception::class) fun hasCapturedNamespace(database: SQLiteDatabase): Boolean {

            try {
                database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? LIMIT 1", arrayOf("browser_media:", "browser_media:\uffff")).use { rows ->

                    return rows.moveToFirst()

                }
            } catch (failure: Exception) {
                throw safe(failure)
            }
        }
        private @JvmStatic @Throws(Exception::class) fun checkNamespaceVersion(database: SQLiteDatabase): Unit {

            database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<?", arrayOf("browser_media:", "browser_media:\uffff")).use { rows ->

                while (rows.moveToNext()) {
                    if (!(rows.getString(0).startsWith(BrowserMediaRepositoryRules.PREFIX))) {
                        throw IOException("BROWSER_MEDIA_VERSION_UNSUPPORTED")
                    }
                }

            }
        }
        @JvmStatic @Throws(Exception::class) fun inspectCaptured(database: SQLiteDatabase, files: File): JSONObject {

            try {

                checkNamespaceVersion(database)
                var raw = DatabaseBackupSnapshot.ownerValue(database, BrowserMediaRepositoryRules.PREFIX + "state", "")!!
                if (raw.isEmpty()) {
                    throw IOException("BROWSER_MEDIA_MIRROR_REQUIRED")
                }
                var state = JSONObject(raw)
                var identity = JSONObject().put("installationId", state.getString("installation_id")).put("restoreEpoch", state.getString("restore_epoch"))
                return BrowserMediaRepositoryRules(Values(database, { identity }), File(files, "browser-media")).inspectCaptured()

            } catch (failure: Exception) {
                throw safe(failure)
            }
        }
    }
}
