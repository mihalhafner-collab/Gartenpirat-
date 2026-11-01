package at.hausgarten.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

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
        for (group in BackupPreferences.GROUPS) preferences[group] = File(data, "shared_prefs/$group.xml")
        return BackupRestoreService.ActivePaths(context.getDatabasePath("haus-garten-mobile.db"),
            context.filesDir, context.cacheDir, context.noBackupFilesDir, preferences)
    }

    @Throws(Exception::class)
    override fun preserveCurrent(requestId: String, incomingRecoveryCode: String?): JSONObject {
        val root = File(context.noBackupFilesDir, "before-restore/" + PixelMcpWriteTools.hash(requestId))
        if (!root.isDirectory && !root.mkdirs()) throw IOException("RESTORE_CURRENT_BACKUP_STORAGE_REQUIRED")
        val proofFile = File(root, "proof.json")
        var archive = File(root, "generation.zip")
        if (proofFile.isFile) {
            val proof = read(proofFile)
            if (proof.optString("archive_format") != "PLAIN_ZIP") archive = File(root, "generation.hgb")
            if (!archive.isFile || archive.length() != proof.getLong("byte_size") ||
                EncryptedBackupArchive.sha256(archive) != proof.getString("archive_sha256")) {
                throw IOException("RESTORE_CURRENT_BACKUP_CHANGED")
            }
            EncryptedBackupArchive.verifyArchive(FileInputStream(archive), incomingRecoveryCode,
                File(root, "recheck-" + UUID.randomUUID()),
                root.usableSpace - BackupStorageBudget.BUSINESS_RESERVE_BYTES, PixelLocalStore.SCHEMA_VERSION).use { verified ->
                LocalBackupDataset.inspectStaged(File(verified.directory, "data"), verified.manifest)
            }
            return proof
        }
        PixelLocalStore(context).use { store ->
            // Empty business collections say nothing about editor drafts or localStorage.
            // A failed/stale capture is never evidence that an empty checkpoint is safe.
            val browser: JSONObject? = null
            val identity = BackupDeviceGuard(context).current()
            var business = store.ownerValue("backup_business_id", "")
            if (business.isEmpty()) {
                business = UUID.randomUUID().toString()
                store.putOwnerValue("backup_business_id", business)
            }
            val backupId = "before_restore_" + PixelMcpWriteTools.hash(requestId).substring(0, 32)
            val capture = File(root, "capture-" + UUID.randomUUID())
            val captured = LocalBackupDataset.capture(context, store, capture,
                JSONObject().put("backupId", backupId).put("installationId", identity.getString("installationId"))
                    .put("restoreEpoch", identity.getString("restoreEpoch")).put("businessId", business)
                    .put("requireDatabaseBrowserCheckpoint", true), browser)
            if (archive.exists()) throw IOException("RESTORE_CURRENT_BACKUP_INCOMPLETE_REVIEW_REQUIRED")
            FileOutputStream(archive).use { out ->
                EncryptedBackupArchive.writePlain(object : FilterOutputStream(BackupStorageBudget.guard(out, root)) {
                    @Throws(IOException::class)
                    override fun close() { flush() }
                }, captured.metadata, captured.sources())
                out.fd.sync()
            }
            EncryptedBackupArchive.verifyArchive(FileInputStream(archive), incomingRecoveryCode,
                File(root, "verified-" + UUID.randomUUID()),
                root.usableSpace - BackupStorageBudget.BUSINESS_RESERVE_BYTES, PixelLocalStore.SCHEMA_VERSION).use { verified ->
                LocalBackupDataset.inspectStaged(File(verified.directory, "data"), verified.manifest)
            }
            val proof = JSONObject().put("formatVersion", 1).put("verified", true).put("backup_id", backupId)
                .put("archive_sha256", EncryptedBackupArchive.sha256(archive))
                .put("database_sha256", captured.metadata.getJSONObject("database").getString("sha256"))
                .put("byte_size", archive.length()).put("captured_at", captured.metadata.getString("capturedAt"))
                .put("local_verified_at", Instant.now().toString())
                .put("private_archive_ref", "before-restore/" + PixelMcpWriteTools.hash(requestId) + "/generation.zip")
                .put("archive_format", "PLAIN_ZIP")
            write(proofFile, proof)
            return proof
        }
    }

    @Throws(Exception::class)
    override fun blockBeforeActivation(requestId: String, restoredManifest: JSONObject, currentBackupProof: JSONObject) {
        if (!currentBackupProof.optBoolean("verified")) throw IOException("RESTORE_CURRENT_BACKUP_REQUIRED")
        val target = restoredManifest.getJSONObject("_activation_context")
        val identity = BackupDeviceGuard(context).current()
        if (identity.getString("installationId") != target.getString("installation_id")) {
            throw IOException("RESTORE_TARGET_CONTEXT_CHANGED")
        }
        if (identity.getString("restoreEpoch") != target.getString("expected_restore_epoch") &&
            requestId != identity.optString("lastRestoreRequest")) {
            throw IOException("RESTORE_TARGET_CONTEXT_CHANGED")
        }
        LocalAuthorityGuard(context).blockAfterRestore(requestId, restoredManifest)
    }

    @Throws(Exception::class)
    override fun afterActivation(requestId: String, restoredManifest: JSONObject, restoredDatabase: File) {
        SQLiteDatabase.openDatabase(restoredDatabase.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            if (db.version < 4 || db.version > PixelLocalStore.SCHEMA_VERSION) {
                throw IOException("RESTORE_DATABASE_VERSION_UNSUPPORTED")
            }
            // A failed optional media transaction must finish before the core restore transaction.
            // Catching a nested SQLite failure inside it would silently roll back checkpoint activation.
            var mediaWarning = ""
            try {
                BrowserMediaRepository.installRestored(db, requestId, BackupDeviceGuard(context).current())
            } catch (mediaProblem: Exception) {
                if (restoredManifest.optString("datasetFormat") != "pixel.snapshot.v2") throw mediaProblem
                mediaWarning = BackupScheduleRules.errorCode(mediaProblem)
            }
            db.beginTransaction()
            try {
                db.rawQuery("SELECT id FROM work_sessions WHERE state IN ('RUNNING','PAUSED','STOPPED_PENDING_REVIEW')", null).use { sessions ->
                    while (sessions.moveToNext()) {
                        val id = sessions.getString(0)
                        val row = ContentValues()
                        val token = PixelMcpWriteTools.hash("$requestId:$id")
                        row.put("id", "evt_restore_$token")
                        row.put("session_id", id)
                        row.put("event_type", "TIME_REVIEW_REQUIRED")
                        row.put("request_id", "restore-review-$token")
                        row.put("occurred_at", Instant.now().toString())
                        row.put("payload", JSONObject().put("reason", "RESTORED_SESSION_REVIEW_REQUIRED")
                            .put("original_time_values_preserved", true).toString())
                        db.insertWithOnConflict("work_session_events", null, row, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }
                db.rawQuery("SELECT value FROM owner_state WHERE key='planning_settings'", null).use { row ->
                    if (row.moveToFirst()) {
                        val original = row.getString(0)
                        put(db, "restored_inactive_planning_settings:$requestId", original)
                        val settings = JSONObject(original)
                        for (key in arrayOf("calendar_sync_enabled", "weather_enabled", "read_busy")) settings.put(key, false)
                        settings.put("requires_device_reauthorization", true)
                        put(db, "planning_settings", settings.toString())
                    }
                }
                if (mediaWarning.isNotEmpty()) put(db, "restore_media_warning", mediaWarning)
                RestoredBrowserCheckpoint.install(db, requestId, restoredManifest,
                    restoredManifest.getJSONObject("_restored_browser_checkpoint"), BackupDeviceGuard(context).current())
                put(db, "restore_requires_invoice_reconciliation", "true")
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
        write(File(context.noBackupFilesDir, "local-authority/restore-process.json"),
            JSONObject().put("pid", Process.myPid()).put("request_id", requestId).put("requires_cold_restart", true))
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun enterMaintenance(context: Context): AutoCloseable {
            PixelLocalStore.assertNoOpenStores(context)
            return LocalAuthorityGuard.exclusiveStartup()
        }

        @JvmStatic
        fun requiresColdRestart(context: Context): Boolean = try {
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
                        if (count == -1) break
                        if (out.size() + count > 256 * 1024) throw IOException("RESTORE_SAFETY_PROOF_INVALID")
                        out.write(block, 0, count)
                    }
                    return JSONObject(String(out.toByteArray(), StandardCharsets.UTF_8))
                }
            }
        }

        @Throws(Exception::class)
        private fun write(file: File, value: JSONObject) {
            val parent = file.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("RESTORE_SAFETY_STORAGE_REQUIRED")
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
