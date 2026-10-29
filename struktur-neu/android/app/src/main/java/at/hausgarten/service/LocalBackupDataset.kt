package at.hausgarten.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

/** Private captured inputs for backup; no provider, network or activation. */
internal object LocalBackupDataset {
    class Captured(@JvmField val directory: File, @JvmField val metadata: JSONObject) {
        @Throws(Exception::class)
        fun sources(): List<EncryptedBackupArchive.Source> = BackupFilePins.sources(directory)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun capture(context: Context, store: PixelLocalStore, directory: File, identity: JSONObject, suppliedBrowser: JSONObject?): Captured =
        synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
            var stage = "FILES"
            try {
                for (source in arrayOf(context.filesDir, context.cacheDir,
                    File(context.noBackupFilesDir, "deletion-backups"),
                    File(context.noBackupFilesDir, "legacy-conflict-backups"))) {
                    if (directory.canonicalFile.toPath().startsWith(source.canonicalFile.toPath())) {
                        throw IOException("BACKUP_CAPTURE_RECURSIVE_TARGET_REJECTED")
                    }
                }
                if (directory.exists() || !directory.mkdirs()) throw IOException("BACKUP_CAPTURE_TARGET_EXISTS_OR_UNAVAILABLE")
                val initial = asset(context, "initial-data.json")
                val catalog = asset(context, "service-catalog.json")
                // Freeze immutable originals before the DB snapshot so concurrent removal cannot lose them.
                for (name in arrayOf("customer-photo-originals", "business-documents", "browser-media")) {
                    BackupFilePins.pinImmutable(File(context.filesDir, name), directory, "files/$name")
                }
                stage = "PREFERENCES"
                val preferences = BackupPreferences.capture(context)
                write(File(directory, "preferences.json"), preferences.toString())
                write(File(directory, "bootstrap/initial-data.json"), initial)
                write(File(directory, "bootstrap/service-catalog.json"), catalog)
                stage = "DATABASE"
                val database = File(directory, "database.sqlite")
                val db = DatabaseBackupSnapshot.capture(store.writableDatabase, database)
                stage = "FILES"
                BackupFilePins.capture(context.filesDir, directory, "files")
                for (name in arrayOf("photo-capture", "invoice-share")) {
                    BackupFilePins.capture(File(context.cacheDir, name), directory, "cache/$name")
                }
                for (name in arrayOf("deletion-backups", "legacy-conflict-backups")) {
                    BackupFilePins.capture(File(context.noBackupFilesDir, name), directory, "no_backup/$name")
                }
                if (PixelMcpWriteTools.hash(preferences) != PixelMcpWriteTools.hash(BackupPreferences.capture(context))) {
                    throw IOException("BACKUP_PREFERENCES_CHANGED_RETRY")
                }
                var report = JSONObject().put("counts", JSONObject()).put("warnings", JSONArray()).put("lastIssuedDocumentIds", JSONArray())
                val extra = JSONArray()
                val browser = SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { snapshot ->
                    stage = "BROWSER"
                    val capturedBrowser = browserSnapshot(snapshot, suppliedBrowser, extra)
                    try {
                        if (BrowserMediaRepository.hasCapturedNamespace(snapshot)) {
                            BrowserMediaRepository.inspectCaptured(snapshot, File(directory, "files"))
                        }
                    } catch (mediaProblem: Exception) {
                        extra.put(JSONObject().put("kind", "browser_media").put("reason", BackupScheduleRules.errorCode(mediaProblem))
                            .put("message", "Medienverweise sind nicht vollständig verifiziert; vorhandene Rohdaten und Dateien bleiben enthalten."))
                    }
                    // Diagnostic only. Raw SQLite and every readable file have already been captured.
                    try {
                        val state = BackupSnapshotState.read(snapshot, BackupPreferences.values(preferences, "haus_garten"), initial, catalog)
                        val originals = LocalOriginalVerifier(File(directory, "files"),
                            { id -> BackupSnapshotState.photo(snapshot, id) },
                            { id -> JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot, "local_invoice_job:$id", "{}")!!) })
                        report = BackupInventory.inspect(SnapshotProjection.effective(state)) { kind, row -> originals.problem(kind, row) }
                    } catch (unavailable: Exception) {
                        extra.put(JSONObject().put("kind", "inventory").put("reason", "INVENTORY_NOT_VERIFIABLE")
                            .put("message", "Bestandsübersicht nicht vollständig auswertbar; Datenbank und vorhandene Dateien sind unverändert enthalten."))
                    }
                    capturedBrowser
                }
                val warnings = report.getJSONArray("warnings")
                for (i in 0 until extra.length()) warnings.put(extra.get(i))
                if (db.optLong("foreignKeyViolations") > 0) {
                    warnings.put(JSONObject().put("kind", "database").put("reason", "EXISTING_RELATION_ERRORS")
                        .put("count", db.getLong("foreignKeyViolations"))
                        .put("message", "Bereits vorhandene fehlerhafte Datenbezüge wurden unverändert gesichert."))
                }
                write(File(directory, "browser-storage.json"), browser.toString())
                write(File(directory, "backup-notes.json"), report.toString(2))
                val metadata = JSONObject(identity.toString()).put("datasetFormat", "pixel.snapshot.v2")
                    .put("schemaVersion", db.getInt("schemaVersion")).put("database", db).put("applicationId", context.packageName)
                    .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                    .put("capturedAt", Instant.now().toString()).put("counts", report.getJSONObject("counts"))
                    .put("numberState", JSONObject()).put("lastIssuedDocumentIds", report.getJSONArray("lastIssuedDocumentIds"))
                    .put("warning_count", warnings.length()).put("captureScope", "database_all_tables_private_files_preferences_browser_storage_and_legacy_backups")
                    .put("media_coverage", "AVAILABLE_FILES_WITH_NOTES")
                Captured(directory, metadata)
            } catch (failure: Exception) {
                throw BackupScheduleRules.captureFailure(stage, failure)
            }
        }

    @JvmStatic
    @Throws(Exception::class)
    fun inspectStaged(directory: File, manifest: JSONObject): JSONObject {
        val database = DatabaseBackupSnapshot.inspect(File(directory, "database.sqlite"))
        for (required in arrayOf("owner_state", "pending", "work_sessions", "work_session_events", "photo_attachments", "mcp_uploads")) {
            if (!database.getJSONObject("tableCounts").has(required)) throw IOException("BACKUP_DATABASE_SCHEMA_INVALID")
        }
        if (database.getInt("schemaVersion") != manifest.getInt("schemaVersion") ||
            database.getString("sha256") != manifest.getJSONObject("database").getString("sha256")) {
            throw IOException("BACKUP_DATABASE_MANIFEST_MISMATCH")
        }
        val preferences = JSONObject(text(File(directory, "preferences.json")))
        for (group in BackupPreferences.GROUPS) BackupPreferences.values(preferences, group)
        validBrowser(JSONObject(text(File(directory, "browser-storage.json"))))
        // Restore checks copy fidelity, not the validity of invoices, references or old originals.
        val notes = File(directory, "backup-notes.json")
        return if (notes.isFile) JSONObject(text(notes)) else JSONObject().put("counts", manifest.getJSONObject("counts")).put("warnings", JSONArray())
    }

    @Throws(Exception::class)
    private fun browserSnapshot(database: SQLiteDatabase, supplied: JSONObject?, warnings: JSONArray): JSONObject {
        var browser = supplied
        try {
            val saved = JSONObject(DatabaseBackupSnapshot.ownerValue(database, "full_backup_browser", "{}")!!)
            if (saved.optJSONObject("browser") != null) browser = saved.getJSONObject("browser")
            validBrowser(browser)
            val status = JSONObject(DatabaseBackupSnapshot.ownerValue(database, "full_backup_browser_status", "{}")!!)
            if (status.optBoolean("capturePending") || status.optString("code").isNotEmpty()) throw IOException("CHECKPOINT_STALE")
        } catch (unavailable: Exception) {
            warnings.put(JSONObject().put("kind", "browser").put("reason", "BROWSER_CHECKPOINT_INCOMPLETE")
                .put("message", "Letzter lesbarer Formularstand enthalten; neuere ungespeicherte Eingaben sind nicht bestätigt. Rohdaten bleiben in der Datenbank."))
            try {
                validBrowser(browser)
            } catch (invalid: Exception) {
                browser = JSONObject().put("formatVersion", 1).put("storage", JSONObject())
            }
        }
        return JSONObject(browser!!.toString())
    }

    @Throws(Exception::class)
    private fun validBrowser(browser: JSONObject?) {
        if (browser == null || browser.optInt("formatVersion") != 1 || browser.optJSONObject("storage") == null) {
            throw IOException("BACKUP_BROWSER_STATE_INVALID")
        }
        RestoredBrowserReviewRules.validate(browser)
        val storage = browser.getJSONObject("storage")
        val keys = storage.keys()
        while (keys.hasNext()) {
            if (storage.get(keys.next()) !is String) throw IOException("BACKUP_BROWSER_STORAGE_INVALID")
        }
    }

    /** Evidence for an explicitly invoked table transfer; never called by startup or restore.
     * Verifies the actual selected archive on every step and compares captured/live business
     * state, pending actions, relationships and originals. The staging directory must be fresh.
     */
    @JvmStatic
    fun tableEvidence(store: PixelLocalStore, archive: File, recovery: String?, staging: File): BusinessTableStore.Evidence =
        BusinessTableStore.Evidence { source ->
            val digest = EncryptedBackupArchive.sha256(archive)
            BufferedInputStream(FileInputStream(archive)).use { input ->
                EncryptedBackupArchive.verifyArchive(input, recovery, staging, staging.parentFile!!.usableSpace, PixelLocalStore.SCHEMA_VERSION).use { verified ->
                    val manifest = verified.manifest
                    val identity = BackupDeviceGuard(store.appContext()).current()
                    for (key in arrayOf("installationId", "restoreEpoch")) {
                        if (identity.getString(key) != manifest.getString(key)) throw IOException("TABLE_BACKUP_DEVICE_CONTEXT_MISMATCH")
                    }
                    if (store.appContext().packageName != manifest.getString("applicationId")) throw IOException("TABLE_BACKUP_APPLICATION_MISMATCH")
                    val directory = File(verified.directory, "data")
                    inspectStaged(directory, manifest)
                    var capturedInventory: JSONObject
                    var capturedBusinessHash: String
                    SQLiteDatabase.openDatabase(File(directory, "database.sqlite").absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { captured ->
                        val capturedState = BackupSnapshotState.read(captured,
                            BackupPreferences.values(JSONObject(text(File(directory, "preferences.json"))), "haus_garten"),
                            text(File(directory, "bootstrap/initial-data.json")), text(File(directory, "bootstrap/service-catalog.json")))
                        capturedBusinessHash = LocalAuthorityMigration.businessHash(capturedState)
                        capturedInventory = LocalMigrationInventory.inspect(capturedState, LocalOriginalVerifier(File(directory, "files"),
                            { id -> BackupSnapshotState.photo(captured, id) },
                            { id -> JSONObject(DatabaseBackupSnapshot.ownerValue(captured, "local_invoice_job:$id", "{}")!!) }))
                        val prefs = BackupPreferences.values(JSONObject(text(File(directory, "preferences.json"))), "haus_garten")
                        val capturedRaw = PixelLocalStore.businessTables(captured).snapshot(prefs.optString("drive_snapshot", text(File(directory, "bootstrap/initial-data.json"))))
                        if (PixelMcpWriteTools.hash(source) != PixelMcpWriteTools.hash(capturedRaw)) throw IOException("TABLE_BACKUP_SOURCE_MISMATCH")
                    }
                    val live = store.getState().put("extended_business_fingerprint", BusinessStateFingerprint.read(store.readableDatabase))
                    if (LocalAuthorityMigration.businessHash(live) != manifest.optString("business_snapshot_sha256", capturedBusinessHash)) {
                        throw IOException("TABLE_BACKUP_BUSINESS_STATE_CHANGED")
                    }
                    val inventory = LocalMigrationInventory.inspect(live, LocalOriginalVerifier(store.appContext().filesDir,
                        store::photoAttachment, { id -> JSONObject(store.ownerValue("local_invoice_job:$id", "{}")) }))
                    if (!inventory.optBoolean("projection_applied") || inventory.getInt("duplicate_ids") != 0 ||
                        inventory.getInt("orphan_relations") != 0 || inventory.getInt("missing_local_originals") != 0) {
                        throw IOException("TABLE_SOURCE_INTEGRITY_REVIEW_REQUIRED")
                    }
                    for (key in arrayOf("counts", "id_set_sha256", "collection_sha256", "pending_sha256", "historical_hours_decimal", "historical_invoice_amounts_decimal")) {
                        if (PixelMcpWriteTools.hash(inventory.get(key)) != PixelMcpWriteTools.hash(capturedInventory.get(key))) {
                            throw IOException("TABLE_BACKUP_INVENTORY_MISMATCH")
                        }
                    }
                    RevierApplicationService(RevierSnapshotRepository(store.readableDatabase, SnapshotProjection.effective(live), store.appContext().filesDir)).validateRelationships()
                    LocalFinancialSnapshotValidation.inspect(store.readableDatabase, SnapshotProjection.effective(live))
                    if (digest != EncryptedBackupArchive.sha256(archive)) throw IOException("TABLE_BACKUP_CHANGED")
                    digest
                }
            }
        }

    @Throws(Exception::class)
    private fun asset(context: Context, name: String): String = context.assets.open(name).use { input ->
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                output.write(buffer, 0, count)
            }
            String(output.toByteArray(), StandardCharsets.UTF_8)
        }
    }

    @Throws(Exception::class)
    private fun text(file: File): String = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)

    @Throws(Exception::class)
    private fun write(file: File, value: String) {
        val parent = file.parentFile!!
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("BACKUP_STAGING_UNAVAILABLE")
        FileOutputStream(file).use { output ->
            output.write(value.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
    }
}
