package at.hausgarten.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/** Durable, epoch-bound browser review in the existing database, separate from ordinary checkpoints. */
class RestoredBrowserCheckpoint internal constructor(private val context: Context, private val store: PixelLocalStore) {
    fun available(): Boolean = store.ownerValue(ACTIVE, "").isNotEmpty()

    @Throws(Exception::class)
    fun pending(): Boolean {
        val record = read()
        return record != null && record.getString("phase") != "REVIEWED"
    }

    @Throws(Exception::class)
    fun assertCheckpointAllowed() {
        if (pending()) throw held()
    }

    @Throws(Exception::class)
    fun preserveDevice(browser: JSONObject) {
        val checked = RestoredBrowserReviewRules.validate(browser)
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val record = read()
            if (record == null || record.has("device_browser")) {
                db.setTransactionSuccessful()
                return
            }
            record.put("device_browser", checked).put("device_sha256", PixelMcpWriteTools.hash(checked))
                .put("device_captured_at", Instant.now().toString())
            save(record)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Throws(Exception::class)
    fun reopen() {
        read() // Opening a review never revokes a completed decision.
    }

    @Throws(Exception::class)
    fun status(includeDrafts: Boolean): JSONObject {
        val record = read() ?: return JSONObject().put("available", false).put("review_pending", false)
        val out = JSONObject().put("available", true).put("review_pending", record.getString("phase") != "REVIEWED")
            .put("media_review_pending", record.optBoolean("media_deferred")).put("phase", record.getString("phase"))
            .put("record_hash", PixelMcpWriteTools.hash(record)).put("captured_at", record.getString("captured_at"))
            .put("device_captured", record.has("device_browser")).put("device_captured_at", record.optString("device_captured_at"))
        if (includeDrafts && record.has("device_browser")) {
            out.put("candidates", RestoredBrowserReviewRules.candidates(record.getJSONObject("source_browser"), "backup"))
            val device = RestoredBrowserReviewRules.candidates(record.getJSONObject("device_browser"), "device")
            for (i in 0 until device.length()) out.getJSONArray("candidates").put(device.get(i))
            out.put("other_browser_values_preserved", record.getJSONObject("source_browser").getJSONObject("storage").length() +
                record.getJSONObject("device_browser").getJSONObject("storage").length() - out.getJSONArray("candidates").length())
            try {
                val media = BrowserMediaRepository(context, store).reviewCandidates()
                out.put("media_candidates", media.getJSONArray("candidates")).put("media_record_hash", media.getString("record_hash"))
                    .put("media_review_required", media.getBoolean("required")).put("media_mirror_complete", media.getBoolean("mirror_complete"))
            } catch (optional: Exception) {
                out.put("media_candidates", JSONArray()).put("media_review_required", true)
                    .put("media_mirror_complete", false).put("media_error_code", "BROWSER_MEDIA_REVIEW_REQUIRED")
            }
            if (record.has("selection")) out.put("selection", record.getJSONObject("selection"))
        }
        return out
    }

    @Throws(Exception::class)
    fun choose(args: JSONObject): JSONObject {
        requireRequest(args)
        if (!java.lang.Boolean.TRUE.equals(args.opt("confirmed"))) throw held()
        val identity = BackupDeviceGuard(context).current()
        val fingerprint = PixelMcpWriteTools.hash(JSONObject().put("args", args).put("identity", identity))
        val receipt = "restored_browser_selection:" + PixelMcpWriteTools.hash(args.getString("request_id"))
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val prior = store.ownerValue(receipt, "")
            if (prior.isNotEmpty()) {
                val saved = JSONObject(prior)
                val current = read()
                if (fingerprint != saved.getString("fingerprint") || current == null || !current.has("selection") ||
                    PixelMcpWriteTools.hash(current.getJSONObject("selection")) != PixelMcpWriteTools.hash(saved.getJSONObject("result"))) {
                    throw held()
                }
                db.setTransactionSuccessful()
                return saved.getJSONObject("result").put("replayed", true)
            }
            val record = read()
            if (record == null || !record.has("device_browser") || record.optString("phase") != "REVIEW_REQUIRED" ||
                PixelMcpWriteTools.hash(record) != args.getString("expected_record_hash")) throw held()
            val selected = RestoredBrowserReviewRules.selection(record.getJSONObject("source_browser"),
                record.getJSONObject("device_browser"), args.getJSONArray("selected"))
            val result = JSONObject().put("projection_version", 3).put("request_id", args.getString("request_id"))
                .put("selected", JSONArray(args.getJSONArray("selected").toString())).put("selected_storage", selected)
                .put("clear_storage", record.getJSONObject("device_browser").getJSONObject("storage"))
                .put("selection_hash", PixelMcpWriteTools.hash(selected)).put("replayed", false)
            if (java.lang.Boolean.TRUE.equals(args.opt("defer_media"))) {
                if (args.getJSONArray("selected_media").length() != 0) throw held()
                result.put("media_deferred", true)
            } else {
                val media = BrowserMediaRepository(context, store)
                val mediaStatus = media.reviewCandidates()
                if (mediaStatus.getBoolean("required")) {
                    if (!mediaStatus.getBoolean("mirror_complete")) throw held()
                    val picked = args.getJSONArray("selected_media")
                    val expectedMedia = args.getString("expected_media_hash")
                    val prepared = media.prepareRestoreSelection(args.getString("request_id"), expectedMedia, picked)
                    result.put("media_selection_hash", prepared.getString("selection_hash"))
                        .put("expected_media_hash", expectedMedia).put("selected_media", JSONArray(picked.toString()))
                } else if (args.optJSONArray("selected_media") != null && args.getJSONArray("selected_media").length() != 0) throw held()
            }
            record.put("phase", "SELECTION_PENDING").put("selection", result)
            save(record)
            store.putOwnerValue(receipt, JSONObject().put("fingerprint", fingerprint).put("result", result).toString())
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    @Throws(Exception::class)
    fun complete(args: JSONObject): JSONObject {
        requireRequest(args)
        completionRecord(args)
        val db = store.writableDatabase
        var deferredRequest: String? = null
        db.beginTransaction()
        try {
            val activeRecord = completionRecord(args)
            val selection = activeRecord.getJSONObject("selection")
            if (activeRecord.getString("phase") == "REVIEWED") {
                db.setTransactionSuccessful()
                return JSONObject().put("reviewed", true).put("replayed", true)
            }
            if (activeRecord.getString("phase") != "SELECTION_PENDING") throw held()
            if (selection.has("media_selection_hash")) {
                BrowserMediaRepository(context, store).activateSelection(selection.getString("request_id"), selection.getString("media_selection_hash"))
            }
            if (selection.optBoolean("media_deferred")) {
                deferredRequest = selection.getString("request_id")
            }
            activeRecord.put("media_deferred", selection.optBoolean("media_deferred"))
                .put("phase", "REVIEWED").put("reviewed_at", Instant.now().toString())
            save(activeRecord)
            store.putOwnerValue("restored_browser_checkpoint_requires_explicit_review", "false")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Finish the core commit before optional media starts its own SQLite transaction.
        // A failed nested transaction would otherwise silently roll back the completed review.
        deferredRequest?.let { request ->
            try {
                BrowserMediaRepository(context, store).deferRestoreReview(request)
            } catch (optional: Exception) {
                // Business review is durable; corrupt optional media remains isolated.
            }
        }
        return JSONObject().put("reviewed", true).put("replayed", false)
    }

    @Throws(Exception::class)
    private fun completionRecord(args: JSONObject): JSONObject {
        val record = read()
        val selection = record?.optJSONObject("selection")
        if (selection == null || selection.getString("request_id") != args.getString("request_id") ||
            selection.getString("selection_hash") != args.getString("selection_hash") ||
            !java.lang.Boolean.TRUE.equals(args.opt("readback_confirmed"))) throw held()
        return record!!
    }

    @Throws(Exception::class)
    fun completeMedia(args: JSONObject): JSONObject {
        requireRequest(args)
        if (!java.lang.Boolean.TRUE.equals(args.opt("confirmed"))) throw held()
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val record = read()
            if (record == null || record.getString("phase") != "REVIEWED") throw held()
            val media = BrowserMediaRepository(context, store)
            val prepared = media.prepareRestoreSelection(args.getString("request_id"),
                args.getString("expected_media_hash"), args.getJSONArray("selected_media"))
            media.activateSelection(args.getString("request_id"), prepared.getString("selection_hash"))
            record.put("media_deferred", false)
            save(record)
            db.setTransactionSuccessful()
            return JSONObject().put("media_reviewed", true)
        } finally {
            db.endTransaction()
        }
    }

    @Throws(Exception::class)
    private fun read(): JSONObject? {
        val key = store.ownerValue(ACTIVE, "")
        if (key.isEmpty()) return null
        if (!key.matches(Regex("restored_browser_checkpoint:[0-9a-f]{64}"))) throw held()
        val record = JSONObject(store.ownerValue(key, "{}"))
        val identity = BackupDeviceGuard(context).current()
        if (record.optInt("formatVersion") != 1 || record.optString("phase") !in listOf("REVIEW_REQUIRED", "SELECTION_PENDING", "REVIEWED") ||
            identity.getString("installationId") != record.optString("installationId") ||
            identity.getString("restoreEpoch") != record.optString("restoreEpoch") ||
            record.getString("source_sha256") != PixelMcpWriteTools.hash(RestoredBrowserReviewRules.validate(record.getJSONObject("source_browser")))) throw held()
        if (record.has("device_browser") && record.getString("device_sha256") !=
            PixelMcpWriteTools.hash(RestoredBrowserReviewRules.validate(record.getJSONObject("device_browser")))) throw held()
        if (record.getString("phase") == "REVIEW_REQUIRED") {
            if (record.has("selection")) throw held()
        } else {
            if (!record.has("device_browser") || record.optJSONObject("selection") == null) throw held()
            RestoredBrowserReviewRules.validateSelection(record.getJSONObject("source_browser"),
                record.getJSONObject("device_browser"), record.getJSONObject("selection"))
            val selection = record.getJSONObject("selection")
            if (record.getString("phase") == "SELECTION_PENDING" && selection.has("media_selection_hash")) {
                BrowserMediaRepository(context, store).verifyPreparedRestoreSelection(selection.getString("request_id"),
                    selection.getString("expected_media_hash"), selection.getJSONArray("selected_media"), selection.getString("media_selection_hash"))
            }
        }
        return record
    }

    @Throws(Exception::class)
    private fun save(record: JSONObject) {
        val key = store.ownerValue(ACTIVE, "")
        store.putOwnerValue(key, record.toString())
        if (record.has("device_browser")) {
            store.putOwnerValue("restored_browser_device_archive:" + PixelMcpWriteTools.hash(JSONObject().put("active", key)
                .put("device_sha256", record.getString("device_sha256"))), record.getJSONObject("device_browser").toString())
        }
    }

    companion object {
        private const val ACTIVE = "restored_browser_checkpoint_active"

        @JvmStatic
        @Throws(Exception::class)
        fun install(database: SQLiteDatabase, request: String, manifest: JSONObject, browser: JSONObject, identity: JSONObject) {
            val source = RestoredBrowserReviewRules.validate(browser)
            val key = "restored_browser_checkpoint:" + PixelMcpWriteTools.hash(request)
            val raw = DatabaseBackupSnapshot.ownerValue(database, key, "")
            if (raw!!.isNotEmpty()) {
                val old = JSONObject(raw)
                if (PixelMcpWriteTools.hash(source) != old.optString("source_sha256") ||
                    identity.getString("installationId") != old.optString("installationId") ||
                    identity.getString("restoreEpoch") != old.optString("restoreEpoch")) throw held()
            } else {
                put(database, key, JSONObject().put("formatVersion", 1).put("phase", "REVIEW_REQUIRED")
                    .put("activation_request", request).put("backup_id", manifest.getString("backupId"))
                    .put("captured_at", manifest.getString("capturedAt")).put("installationId", identity.getString("installationId"))
                    .put("restoreEpoch", identity.getString("restoreEpoch")).put("source_sha256", PixelMcpWriteTools.hash(source))
                    .put("source_browser", source).put("created_at", Instant.now().toString()).toString())
            }
            put(database, ACTIVE, key)
            put(database, "restored_browser_checkpoint_requires_explicit_review", "true")
        }

        @Throws(Exception::class)
        private fun requireRequest(args: JSONObject) {
            if (!args.optString("request_id").matches(Regex("[A-Za-z0-9_-]{8,200}"))) throw held()
        }

        private fun held(): IOException = IOException("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED")

        private fun put(db: SQLiteDatabase, key: String, value: String) {
            val row = ContentValues()
            row.put("key", key)
            row.put("value", value)
            db.insertWithOnConflict("owner_state", null, row, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}
