package at.hausgarten.service

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

/** Real SQLite review/optional-media transactions in new synthetic directories only. */
class RestoredBrowserCheckpointNativeTests private constructor(private val test: Instrumentation) {
    companion object {
        /** Run once, force-stop the target, then invoke with reopen=true in a new process. */
        @JvmStatic
        fun run(test: Instrumentation, reopen: Boolean): JSONObject = RestoredBrowserCheckpointNativeTests(test).execute(reopen)

        private const val ACTIVE = "restored_browser_checkpoint_active"
        private const val REQUIRED = "restored_browser_checkpoint_requires_explicit_review"
        private const val MEDIA = "browser_media:v1:state"
        private const val REQUEST = "qa_checkpoint_complete_001"
    }

    private var checks = 0
    private val fixtureOwner: Context by lazy { test.targetContext.createDeviceProtectedStorageContext() }

    private fun yes(value: Boolean, code: String) {
        if (!value) throw AssertionError("QA_RESTORED_BROWSER_$code")
        checks++
    }

    private fun rejects(code: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            yes(failure.message == code, "REJECTION_$code")
            return
        }
        throw AssertionError("QA_RESTORED_BROWSER_EXPECTED_$code")
    }

    private fun execute(reopen: Boolean): JSONObject {
        // Refuse physical devices before inspecting any fixture file or opening any store.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_APK_REQUIRED")
        yes(test.targetContext.packageName == "at.hausgarten.service", "TARGET_PACKAGE_REQUIRED")
        yes(Build.HARDWARE in listOf("ranchu", "goldfish"), "DISPOSABLE_EMULATOR_REQUIRED")
        val qemu = test.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        }
        yes(qemu == "1" && Build.VERSION.SDK_INT == 35, "API35_EMULATOR_REQUIRED")
        yes(fixtureOwner.isDeviceProtectedStorage, "DEVICE_PROTECTED_FIXTURE_OWNER")
        val marker = File(fixtureOwner.noBackupFilesDir, "qa-restored-browser-checkpoint-fixture.json")
        if (reopen) return reopen(marker)
        if (marker.exists()) throw IOException("FRESH_SYNTHETIC_CHECKPOINT_FIXTURE_REQUIRED")
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(fixtureOwner.cacheDir, "qa-restored-browser-$token")
        yes(!root.exists() && root.mkdir(), "NEW_SYNTHETIC_ROOT")
        write(File(root, "fixture.json"), JSONObject().put("fixture", "synthetic-restored-browser-v1").put("token", token))
        val context = isolated(root, token, "deferred")
        for (path in listOf(context.dataDir, context.filesDir, context.noBackupFilesDir, context.cacheDir,
            context.getDatabasePath("haus-garten-mobile.db"))) {
            yes(path.canonicalPath.startsWith(root.canonicalPath + File.separator), "ISOLATED_STORAGE_PATH")
        }
        val args: JSONObject
        PixelLocalStore(context).use { store ->
            store.putOwnerValue("qa_checkpoint_fixture", token)
            args = prepare(context, store, REQUEST, true)
            invalidCompletionChecks(context, store, args)
            coreRollbackCheck(context, store, args)
            val corrupt = JSONObject().put("formatVersion", 99).put("qa_synthetic", "optional media failure").toString()
            store.putOwnerValue(MEDIA, corrupt)
            // This valid namespace passes the repository constructor; Rules.mutate fails inside SQLite.
            rejects("BROWSER_MEDIA_VERSION_UNSUPPORTED") { BrowserMediaRepository(context, store).deferRestoreReview(REQUEST) }
            yes(!store.writableDatabase.inTransaction(), "FAILED_MEDIA_TRANSACTION_FINISHED")
            val result = RestoredBrowserCheckpoint(context, store).complete(args)
            yes(result.getBoolean("reviewed") && !result.getBoolean("replayed"), "COMPLETE_WITH_OPTIONAL_FAILURE")
            reviewed(context, store, args, true)
            yes(store.ownerValue(MEDIA, "") == corrupt, "CORRUPT_MEDIA_PRESERVED")
        }
        // A new SQLiteOpenHelper/connection must observe committed core state immediately.
        PixelLocalStore(context).use { store ->
            reviewed(context, store, args, true)
            val before = ownerHash(store)
            val replay = RestoredBrowserCheckpoint(context, store).complete(args)
            yes(replay.getBoolean("reviewed") && replay.getBoolean("replayed"), "REPLAY_AFTER_CONNECTION_REOPEN")
            yes(ownerHash(store) == before, "REPLAY_DOES_NOT_REWRITE_STATE_OR_MEDIA")
            write(marker, JSONObject().put("fixture", "synthetic-restored-browser-v1").put("token", token)
                .put("root", root.canonicalPath).put("pid", Process.myPid()).put("args", args)
                .put("owner_sha256", before))
        }
        deferredSuccessChecks(isolated(root, token, "deferred_success"))
        activeMediaChecks(isolated(root, token, "active"))
        epochChecks(isolated(root, token, "epoch"))
        return report(false)
    }

    private fun reopen(marker: File): JSONObject {
        yes(marker.isFile && !Files.isSymbolicLink(marker.toPath()), "REOPEN_MARKER")
        val saved = JSONObject(marker.readText())
        yes(saved.getString("fixture") == "synthetic-restored-browser-v1", "SYNTHETIC_MARKER")
        val token = saved.getString("token")
        yes(token.matches(Regex("[0-9a-f]{32}")), "SYNTHETIC_TOKEN")
        val root = File(fixtureOwner.cacheDir, "qa-restored-browser-$token")
        yes(root.canonicalPath == saved.getString("root") && root.isDirectory && !Files.isSymbolicLink(root.toPath()), "OWNED_REOPEN_ROOT")
        val identity = JSONObject(File(root, "fixture.json").readText())
        yes(identity.getString("fixture") == "synthetic-restored-browser-v1" && identity.getString("token") == token, "OWNED_REOPEN_FIXTURE")
        yes(saved.getInt("pid") != Process.myPid(), "NEW_TARGET_PROCESS")
        val context = isolated(root, token, "deferred")
        yes(context.getDatabasePath("haus-garten-mobile.db").isFile, "EXISTING_SYNTHETIC_DATABASE")
        PixelLocalStore(context).use { store ->
            yes(store.ownerValue("qa_checkpoint_fixture", "") == token, "DATABASE_FIXTURE_IDENTITY")
            yes(ownerHash(store) == saved.getString("owner_sha256"), "STATE_PERSISTED_ACROSS_PROCESS")
            val args = saved.getJSONObject("args")
            reviewed(context, store, args, true)
            val replay = RestoredBrowserCheckpoint(context, store).complete(args)
            yes(replay.getBoolean("reviewed") && replay.getBoolean("replayed"), "NEW_PROCESS_REPLAY")
            yes(ownerHash(store) == saved.getString("owner_sha256"), "NEW_PROCESS_REPLAY_UNCHANGED")
            yes(JSONObject(store.ownerValue(MEDIA, "{}")).getInt("formatVersion") == 99, "OPTIONAL_FAILURE_EVIDENCE_RETAINED")
        }
        return report(true)
    }

    private fun prepare(context: Context, store: PixelLocalStore, request: String, deferred: Boolean): JSONObject {
        val identity = BackupDeviceGuard(context).current()
        val source = browser("QA synthetic backup draft 🌿")
        store.putOwnerValue(MEDIA, JSONObject().put("formatVersion", 1)
            .put("installation_id", identity.getString("installationId")).put("restore_epoch", identity.getString("restoreEpoch"))
            .put("mode", "RESTORE_REVIEW").put("mirror_complete", false).put("device_mirror_complete", true).toString())
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            RestoredBrowserCheckpoint.install(db, request,
                JSONObject().put("backupId", "qa_synthetic_checkpoint_backup").put("capturedAt", "2026-10-29T00:00:00Z"), source, identity)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val review = RestoredBrowserCheckpoint(context, store)
        review.preserveDevice(browser("QA synthetic device draft äß"))
        val chosen = JSONObject().put("request_id", request).put("confirmed", true)
            .put("expected_record_hash", review.status(false).getString("record_hash"))
            .put("selected", JSONArray().put(JSONObject().put("source", "backup").put("key", "hausgarten.entry.draft.v1")))
            .put("defer_media", deferred).put("selected_media", JSONArray())
        if (!deferred) chosen.put("expected_media_hash", BrowserMediaRepository(context, store).reviewCandidates().getString("record_hash"))
        val selection = review.choose(chosen)
        yes(review.pending() && record(store).getString("phase") == "SELECTION_PENDING", "SELECTION_PENDING")
        return JSONObject().put("request_id", request).put("selection_hash", selection.getString("selection_hash")).put("readback_confirmed", true)
    }

    private fun invalidCompletionChecks(context: Context, store: PixelLocalStore, valid: JSONObject) {
        val before = ownerHash(store)
        val invalid = listOf(JSONObject(valid.toString()).apply { remove("request_id") },
            JSONObject(valid.toString()).put("request_id", "qa_checkpoint_wrong_request"),
            JSONObject(valid.toString()).put("selection_hash", "0".repeat(64)),
            JSONObject(valid.toString()).apply { remove("readback_confirmed") },
            JSONObject(valid.toString()).put("readback_confirmed", false),
            JSONObject(valid.toString()).put("readback_confirmed", "true"))
        for (args in invalid) {
            rejects("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED") { RestoredBrowserCheckpoint(context, store).complete(args) }
            yes(ownerHash(store) == before, "INVALID_ARGS_NO_CORE_OR_OPTIONAL_CHANGES")
            yes(!store.writableDatabase.inTransaction(), "INVALID_ARGS_NO_OPEN_TRANSACTION")
        }
    }

    private fun coreRollbackCheck(context: Context, store: PixelLocalStore, args: JSONObject) {
        val before = ownerHash(store)
        val db = store.writableDatabase
        db.execSQL("CREATE TRIGGER qa_checkpoint_commit_failure BEFORE INSERT ON owner_state WHEN NEW.key='$REQUIRED' AND NEW.value='false' BEGIN SELECT RAISE(ABORT,'QA_SYNTHETIC_CORE_WRITE_FAILURE'); END")
        try {
            rejects("LOCAL_STATE_WRITE_FAILED") { RestoredBrowserCheckpoint(context, store).complete(args) }
            yes(ownerHash(store) == before, "CORE_FAILURE_ATOMIC_AND_OPTIONAL_UNCHANGED")
            yes(record(store).getString("phase") == "SELECTION_PENDING" && store.ownerValue(REQUIRED, "") == "true", "CORE_FAILURE_REVIEW_REMAINS_PENDING")
            yes(!db.inTransaction(), "CORE_FAILURE_TRANSACTION_FINISHED")
        } finally {
            db.execSQL("DROP TRIGGER qa_checkpoint_commit_failure")
        }
    }

    private fun activeMediaChecks(context: Context) {
        PixelLocalStore(context).use { store ->
            val args = prepare(context, store, "qa_checkpoint_active_media", false)
            coreRollbackCheck(context, store, args)
            yes(JSONObject(store.ownerValue(MEDIA, "{}")).getString("mode") == "RESTORE_REVIEW", "ACTIVE_MEDIA_ROLLS_BACK_WITH_CORE")
            val result = RestoredBrowserCheckpoint(context, store).complete(args)
            yes(result.getBoolean("reviewed") && !result.getBoolean("replayed"), "ACTIVE_MEDIA_COMPLETE")
            reviewed(context, store, args, false)
            val media = JSONObject(store.ownerValue(MEDIA, "{}"))
            yes(media.getString("mode") == "ACTIVE" && media.getString("activation_selection") ==
                record(store).getJSONObject("selection").getString("media_selection_hash"), "ACTIVE_MEDIA_SELECTION_COMMITTED")
        }
    }

    private fun deferredSuccessChecks(context: Context) {
        PixelLocalStore(context).use { store ->
            val args = prepare(context, store, "qa_checkpoint_deferred_success", true)
            val result = RestoredBrowserCheckpoint(context, store).complete(args)
            yes(result.getBoolean("reviewed") && !result.getBoolean("replayed"), "DEFERRED_MEDIA_COMPLETE")
            reviewed(context, store, args, true)
            val media = JSONObject(store.ownerValue(MEDIA, "{}"))
            yes(media.getString("mode") == "ACTIVE" && media.getBoolean("review_deferred"), "OPTIONAL_DEFERRAL_COMMITTED")
            yes(store.ownerValue("browser_media:v1:request:" + PixelMcpWriteTools.hash(args.getString("request_id")), "").isNotEmpty(), "OPTIONAL_DEFERRAL_RECEIPT")
        }
    }

    private fun epochChecks(context: Context) {
        PixelLocalStore(context).use { store ->
            val args = prepare(context, store, "qa_checkpoint_stale_epoch", true)
            val before = ownerHash(store)
            val guard = BackupDeviceGuard(context)
            guard.advanceForRestore("qa_checkpoint_new_epoch", guard.current().getString("restoreEpoch"))
            rejects("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED") { RestoredBrowserCheckpoint(context, store).complete(args) }
            yes(ownerHash(store) == before, "STALE_EPOCH_NO_CORE_OR_OPTIONAL_CHANGES")
        }
    }

    private fun reviewed(context: Context, store: PixelLocalStore, args: JSONObject, deferred: Boolean) {
        val record = record(store)
        yes(record.getString("phase") == "REVIEWED", "REVIEWED_DURABLE")
        yes(record.getBoolean("media_deferred") == deferred && record.has("reviewed_at"), "REVIEW_METADATA_DURABLE")
        yes(record.getJSONObject("selection").getString("request_id") == args.getString("request_id") &&
            record.getJSONObject("selection").getString("selection_hash") == args.getString("selection_hash"), "SELECTION_PRESERVED")
        yes(store.ownerValue(REQUIRED, "") == "false", "EXPLICIT_REVIEW_FLAG_COMMITTED")
        val review = RestoredBrowserCheckpoint(context, store)
        yes(!review.pending() && review.status(false).getString("phase") == "REVIEWED", "PRODUCTION_REVIEW_GATE_OPEN")
        yes(!store.writableDatabase.inTransaction(), "CORE_TRANSACTION_FINISHED")
    }

    private fun record(store: PixelLocalStore): JSONObject = JSONObject(store.ownerValue(store.ownerValue(ACTIVE, ""), "{}"))

    private fun ownerHash(store: PixelLocalStore): String {
        val rows = JSONArray()
        store.readableDatabase.rawQuery("SELECT key,value FROM owner_state ORDER BY key", null).use { cursor ->
            while (cursor.moveToNext()) rows.put(JSONArray().put(cursor.getString(0)).put(cursor.getString(1)))
        }
        return PixelMcpWriteTools.hash(rows)
    }

    private fun browser(value: String): JSONObject = JSONObject().put("formatVersion", 1)
        .put("storage", JSONObject().put("hausgarten.entry.draft.v1", value))

    private fun isolated(root: File, token: String, scenario: String): Context =
        FixtureContext(fixtureOwner, File(root, scenario), "qa_rbc_${token}_$scenario")

    private fun write(file: File, value: JSONObject) {
        FileOutputStream(file).use { output ->
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun report(reopen: Boolean): JSONObject = JSONObject().put("checks", checks).put("synthetic_only", true)
        .put("productive_data_read", false).put("real_sqlite_transactions", true).put("isolated_context_paths", true)
        .put("new_process_readback", reopen).put("optional_media_failure_tested", !reopen)

    private class FixtureContext(private val owner: Context, private val root: File, private val prefix: String) : ContextWrapper(owner) {
        override fun getApplicationContext(): Context = this
        override fun getDataDir(): File = directory("")
        override fun getFilesDir(): File = directory("files")
        override fun getNoBackupFilesDir(): File = directory("no_backup")
        override fun getCacheDir(): File = directory("cache")
        override fun getDatabasePath(name: String): File {
            if (name != "haus-garten-mobile.db") throw IOException("QA_SYNTHETIC_DATABASE_NAME_REQUIRED")
            return File(directory("databases"), name)
        }
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            openOrCreateDatabase(name, mode, factory, null)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (!name.matches(Regex("[A-Za-z0-9_]+"))) throw IOException("QA_SYNTHETIC_PREFERENCE_NAME_REQUIRED")
            return owner.getSharedPreferences("${prefix}_$name", mode)
        }
        private fun directory(name: String): File {
            val value = if (name.isEmpty()) root else File(root, name)
            if (!value.isDirectory && !value.mkdirs()) throw IOException("QA_SYNTHETIC_STORAGE_REQUIRED")
            return value
        }
    }
}
