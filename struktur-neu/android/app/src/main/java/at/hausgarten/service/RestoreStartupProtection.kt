package at.hausgarten.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Runs before providers and store handles exist. Completed exchanges require a fresh process. */
class RestoreStartupProtection(private val context: Context) : BackupRestoreService.Protection {
    @Throws(Exception::class)
    override fun enterExclusiveStartup(): AutoCloseable = enterMaintenance(context)

    @Throws(Exception::class)
    override fun activePaths(): BackupRestoreService.ActivePaths {
        val data = context.dataDir
        if (context.filesDir.canonicalFile != File(data, "files").canonicalFile) {
            throw IOException("RESTORE_TRUSTED_APPLICATION_PATH_REQUIRED")
        }
        val preferences = LinkedHashMap<String, File>()
        for (group in BackupPreferences.GROUPS) preferences[group] =
            File(data, "shared_prefs/$group.xml")
        return BackupRestoreService.ActivePaths(
            context.getDatabasePath("haus-garten-mobile.db"),
            context.filesDir,
            context.cacheDir,
            preferences,
        )
    }

    @Throws(Exception::class)
    override fun blockBeforeActivation(requestId: String, restoredManifest: JSONObject) {
        val target = restoredManifest.getJSONObject("_activation_context")
        val identity = BackupDeviceGuard(context).current()
        if (identity.getString("installationId") != target.getString("installation_id")) {
            throw IOException("RESTORE_TARGET_CONTEXT_CHANGED")
        }
        if (
            identity.getString("restoreEpoch") != target.getString("expected_restore_epoch") &&
                requestId != identity.optString("lastRestoreRequest")
        ) {
            throw IOException("RESTORE_TARGET_CONTEXT_CHANGED")
        }
        RestoreStartupGuard.prepareForRestore(context, requestId)
    }

    @Throws(Exception::class)
    override fun afterActivation(
        requestId: String,
        restoredManifest: JSONObject,
        restoredDatabase: File,
    ) {
        SQLiteDatabase.openDatabase(
                restoredDatabase.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            .use { db ->
                if (db.version < 4 || db.version > PixelLocalStore.SCHEMA_VERSION) {
                    throw IOException("RESTORE_DATABASE_VERSION_UNSUPPORTED")
                }
                db.beginTransaction()
                try {
                    db.rawQuery(
                            "SELECT id FROM work_sessions WHERE state IN ('RUNNING','PAUSED','STOPPED_PENDING_REVIEW')",
                            null,
                        )
                        .use { sessions ->
                            while (sessions.moveToNext()) {
                                val id = sessions.getString(0)
                                val row = ContentValues()
                                val token = PixelMcpWriteTools.hash("$requestId:$id")
                                row.put("id", "evt_restore_$token")
                                row.put("session_id", id)
                                row.put("event_type", "TIME_REVIEW_REQUIRED")
                                row.put("request_id", "restore-review-$token")
                                row.put("occurred_at", Instant.now().toString())
                                row.put(
                                    "payload",
                                    JSONObject()
                                        .put("reason", "RESTORED_SESSION_REVIEW_REQUIRED")
                                        .put("original_time_values_preserved", true)
                                        .toString(),
                                )
                                db.insertWithOnConflict(
                                    "work_session_events",
                                    null,
                                    row,
                                    SQLiteDatabase.CONFLICT_IGNORE,
                                )
                            }
                        }
                    db.rawQuery("SELECT value FROM owner_state WHERE key='planning_settings'", null)
                        .use { row ->
                            if (row.moveToFirst()) {
                                val original = row.getString(0)
                                put(db, "restored_inactive_planning_settings:$requestId", original)
                                val settings = JSONObject(original)
                                for (key in
                                    arrayOf(
                                        "calendar_sync_enabled",
                                        "weather_enabled",
                                        "read_busy",
                                    )) settings.put(key, false)
                                settings.put("requires_device_reauthorization", true)
                                put(db, "planning_settings", settings.toString())
                            }
                        }
                    // A new number series cannot reconstruct later documents lost by an old
                    // backup. Retain the restored source set for deliberate evidence review.
                    val ledger =
                        JSONObject(DatabaseBackupSnapshot.ownerValue(db, "settlement:v1", "{}")!!)
                    val preferences =
                        context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE)
                    val initial =
                        context.assets.open("initial-data.json").bufferedReader().use {
                            it.readText()
                        }
                    val restored =
                        SnapshotProjection.effective(
                            BackupSnapshotState.read(
                                db,
                                JSONObject()
                                    .put(
                                        "drive_snapshot",
                                        preferences.getString("drive_snapshot", initial),
                                    ),
                                initial,
                                context.assets.open("service-catalog.json").bufferedReader().use {
                                    it.readText()
                                },
                            )
                        )
                    val restoredSources = JSONArray()
                    for (collection in arrayOf("orders", "worklogs")) {
                        val rows = restored.optJSONArray(collection) ?: JSONArray()
                        for (i in 0 until rows.length()) {
                            val id = rows.optJSONObject(i)?.optString("id").orEmpty()
                            if (id.isNotBlank())
                                restoredSources.put(
                                    JSONObject().put("collection", collection).put("id", id)
                                )
                        }
                    }
                    ledger
                        .put("restored_sources", restoredSources)
                        .put(
                            "restore_epoch",
                            BackupDeviceGuard(context).current().getString("restoreEpoch"),
                        )
                        .put("restore_request_id", requestId)
                    put(db, "settlement:v1", ledger.toString())
                    db.version = PixelLocalStore.SCHEMA_VERSION
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        // Existing device account grants are retained, but no responder writes restored data
        // before the user has reviewed this new business context.
        PixelTunnelConfig(context).enabled(false)
        PixelTunnelConfig(context).writeEnabled(false)
        write(
            File(context.noBackupFilesDir, "local-authority/restore-process.json"),
            JSONObject()
                .put("pid", Process.myPid())
                .put("request_id", requestId)
                .put("requires_cold_restart", true),
        )
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun enterMaintenance(context: Context): AutoCloseable {
            PixelLocalStore.assertNoOpenStores(context)
            return RestoreStartupGuard.exclusiveStartup()
        }

        @JvmStatic
        fun requiresColdRestart(context: Context): Boolean =
            try {
                val file = File(context.noBackupFilesDir, "local-authority/restore-process.json")
                file.isFile && read(file).getInt("pid") == Process.myPid()
            } catch (invalid: Exception) {
                true
            }

        private fun put(db: SQLiteDatabase, key: String, value: String?) {
            val row = ContentValues()
            row.put("key", key)
            row.put("value", value)
            db.insertWithOnConflict("owner_state", null, row, SQLiteDatabase.CONFLICT_REPLACE)
        }

        @Throws(Exception::class)
        private fun read(file: File): JSONObject {
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { out ->
                    val block = ByteArray(4096)
                    while (true) {
                        val count = input.read(block)
                        if (count == -1) {
                            break
                        }
                        if (out.size() + count > 256 * 1024) {
                            throw IOException("RESTORE_SAFETY_PROOF_INVALID")
                        }
                        out.write(block, 0, count)
                    }
                    return JSONObject(String(out.toByteArray(), StandardCharsets.UTF_8))
                }
            }
        }

        @Throws(Exception::class)
        private fun write(file: File, value: JSONObject) {
            val parent = file.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs())
                throw IOException("RESTORE_SAFETY_STORAGE_REQUIRED")
            val atomic = AtomicFile(file)
            var out: FileOutputStream? = null
            try {
                val started = atomic.startWrite()
                out = started
                started.write(value.toString().toByteArray(StandardCharsets.UTF_8))
                atomic.finishWrite(started)
                out = null
            } catch (failure: Exception) {
                out?.let { atomic.failWrite(it) }
                throw failure
            }
        }
    }
}
