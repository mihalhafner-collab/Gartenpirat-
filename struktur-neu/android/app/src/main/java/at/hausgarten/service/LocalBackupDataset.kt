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
    class Captured(@JvmField val directory: File, @JvmField val metadata: JSONObject, private val originals: List<BackupArchive.Source> = emptyList(), @JvmField val notes: JSONObject = JSONObject()) {
        @Throws(Exception::class)
        fun sources(): List<BackupArchive.Source> = BackupFilePins.sources(directory) + originals
    }

    @JvmStatic
    /** Caller holds FILE_CAPTURE_LOCK until the originals have been streamed. */
    fun forStreaming(context: Context, store: PixelLocalStore, directory: File, identity: JSONObject): Captured =
        capture(context, store, directory, identity, true)

    private fun capture(context: Context, store: PixelLocalStore, directory: File, identity: JSONObject, streamOriginals: Boolean): Captured =
        synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
            var stage = "FILES"
            try {
                for (source in arrayOf(context.filesDir, context.cacheDir)) {
                    if (directory.canonicalFile.toPath().startsWith(source.canonicalFile.toPath())) {
                        throw IOException("BACKUP_CAPTURE_RECURSIVE_TARGET_REJECTED")
                    }
                }
                if (directory.exists() || !directory.mkdirs()) throw IOException("BACKUP_CAPTURE_TARGET_EXISTS_OR_UNAVAILABLE")
                val initial = asset(context, "initial-data.json")
                val catalog = asset(context, "service-catalog.json")
                // Freeze immutable originals before the DB snapshot so concurrent removal cannot lose them.
                if (!streamOriginals) for (name in arrayOf("customer-photo-originals", "business-documents", "mcp-photo-uploads", "native-form-media")) {
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
                val originals = ArrayList<BackupArchive.Source>()
                fun include(root: File, prefix: String) {
                    if (streamOriginals) originals.addAll(BackupFilePins.directSources(root, prefix))
                    else BackupFilePins.capture(root, directory, prefix)
                }
                include(context.filesDir, "files")
                for (name in arrayOf("photo-capture")) include(File(context.cacheDir, name), "cache/$name")
                val filesRoot = if (streamOriginals) context.filesDir else File(directory, "files")
                if (PixelMcpWriteTools.hash(preferences) != PixelMcpWriteTools.hash(BackupPreferences.capture(context))) {
                    throw IOException("BACKUP_PREFERENCES_CHANGED_RETRY")
                }
                var report = JSONObject().put("counts", JSONObject()).put("warnings", JSONArray()).put("lastIssuedDocumentIds", JSONArray())
                val extra = JSONArray()
                SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { snapshot ->
                    // Diagnostic only. Raw SQLite and every readable file have already been captured.
                    try {
                        val state = BackupSnapshotState.read(snapshot, BackupPreferences.values(preferences, "haus_garten"), initial, catalog)
                        val effective = SnapshotProjection.effective(state)
                        effective.put("settlements", JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot, "settlement:v1", "{}")!!).optJSONArray("records") ?: JSONArray())
                        report = BackupInventory.inspect(effective) { kind, row ->
                            val metadata = if (kind == "photo")
                                BackupSnapshotState.photo(snapshot, row.optString("id")) ?: row else row
                            BackupInventory.originalProblem(filesRoot, kind, metadata)
                        }
                    } catch (unavailable: Exception) {
                        extra.put(JSONObject().put("kind", "inventory").put("reason", "INVENTORY_NOT_VERIFIABLE")
                            .put("message", "Bestandsübersicht nicht vollständig auswertbar; Datenbank und vorhandene Dateien sind unverändert enthalten."))
                    }
                }
                val warnings = report.getJSONArray("warnings")
                for (i in 0 until extra.length()) warnings.put(extra.get(i))
                if (db.optLong("foreignKeyViolations") > 0) {
                    warnings.put(JSONObject().put("kind", "database").put("reason", "EXISTING_RELATION_ERRORS")
                        .put("count", db.getLong("foreignKeyViolations"))
                        .put("message", "Bereits vorhandene fehlerhafte Datenbezüge wurden unverändert gesichert."))
                }
                write(File(directory, "backup-notes.json"), report.toString(2))
                val metadata = JSONObject(identity.toString()).put("datasetFormat", "pixel.snapshot.v2")
                    .put("schemaVersion", db.getInt("schemaVersion")).put("database", db).put("applicationId", context.packageName)
                    .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                    .put("capturedAt", Instant.now().toString()).put("counts", report.getJSONObject("counts"))
                    .put("numberState", JSONObject()).put("lastIssuedDocumentIds", report.getJSONArray("lastIssuedDocumentIds"))
                    .put("warning_count", warnings.length()).put("captureScope", "database_all_tables_private_files_preferences")
                    .put("media_coverage", "AVAILABLE_FILES_WITH_NOTES")
                Captured(directory, metadata, originals, report)
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
        // Restore checks copy fidelity, not the validity of invoices, references or old originals.
        val notes = File(directory, "backup-notes.json")
        return if (notes.isFile) JSONObject(text(notes)) else JSONObject().put("counts", manifest.getJSONObject("counts")).put("warnings", JSONArray())
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
