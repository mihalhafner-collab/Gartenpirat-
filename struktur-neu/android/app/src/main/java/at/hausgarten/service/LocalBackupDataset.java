package at.hausgarten.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Private captured inputs for backup; no provider, network or activation. */
final class LocalBackupDataset {
    static final class Captured {
        final File directory;
        final JSONObject metadata;
        Captured(File directory,JSONObject metadata){this.directory=directory;this.metadata=metadata;}
        List<EncryptedBackupArchive.Source> sources()throws Exception{return BackupFilePins.sources(directory);}
    }
    static Captured capture(Context context, PixelLocalStore store, File directory, JSONObject identity, JSONObject suppliedBrowser) throws Exception {
        synchronized (BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
            String stage = "FILES";
            try {
                for (File source : new File[]{context.getFilesDir(), context.getCacheDir(), new File(context.getNoBackupFilesDir(), "deletion-backups"), new File(context.getNoBackupFilesDir(), "legacy-conflict-backups")})
                    if (directory.getCanonicalFile().toPath().startsWith(source.getCanonicalFile().toPath())) throw new IOException("BACKUP_CAPTURE_RECURSIVE_TARGET_REJECTED");
                if (directory.exists() || !directory.mkdirs()) throw new IOException("BACKUP_CAPTURE_TARGET_EXISTS_OR_UNAVAILABLE");
                String initial = asset(context, "initial-data.json"), catalog = asset(context, "service-catalog.json");
                // Freeze immutable originals before the DB snapshot so concurrent removal cannot lose them.
                for (String name : new String[]{"customer-photo-originals", "business-documents", "browser-media"})
                    BackupFilePins.pinImmutable(new File(context.getFilesDir(), name), directory, "files/" + name);
                stage = "PREFERENCES";
                JSONObject preferences = BackupPreferences.capture(context);
                write(new File(directory, "preferences.json"), preferences.toString());
                write(new File(directory, "bootstrap/initial-data.json"), initial);
                write(new File(directory, "bootstrap/service-catalog.json"), catalog);
                stage = "DATABASE";
                File database = new File(directory, "database.sqlite");
                JSONObject db = DatabaseBackupSnapshot.capture(store.getWritableDatabase(), database);
                stage = "FILES";
                BackupFilePins.capture(context.getFilesDir(), directory, "files");
                for (String name : new String[]{"photo-capture", "invoice-share"}) BackupFilePins.capture(new File(context.getCacheDir(), name), directory, "cache/" + name);
                for (String name : new String[]{"deletion-backups", "legacy-conflict-backups"}) BackupFilePins.capture(new File(context.getNoBackupFilesDir(), name), directory, "no_backup/" + name);
                if (!PixelMcpWriteTools.hash(preferences).equals(PixelMcpWriteTools.hash(BackupPreferences.capture(context)))) throw new IOException("BACKUP_PREFERENCES_CHANGED_RETRY");
                JSONObject report = new JSONObject().put("counts", new JSONObject()).put("warnings", new JSONArray()).put("lastIssuedDocumentIds", new JSONArray());
                JSONArray extra = new JSONArray();
                JSONObject browser;
                try (SQLiteDatabase snapshot = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
                    stage = "BROWSER";
                    browser = browserSnapshot(snapshot, suppliedBrowser, extra);
                    try { if (BrowserMediaRepository.hasCapturedNamespace(snapshot)) BrowserMediaRepository.inspectCaptured(snapshot, new File(directory, "files")); }
                    catch (Exception mediaProblem) { extra.put(new JSONObject().put("kind", "browser_media").put("reason", BackupScheduleRules.errorCode(mediaProblem)).put("message", "Medienverweise sind nicht vollständig verifiziert; vorhandene Rohdaten und Dateien bleiben enthalten.")); }
                    // Diagnostic only. Raw SQLite and every readable file have already been captured.
                    try {
                        JSONObject state = BackupSnapshotState.read(snapshot, BackupPreferences.values(preferences, "haus_garten"), initial, catalog);
                        LocalOriginalVerifier originals = new LocalOriginalVerifier(new File(directory, "files"), id -> BackupSnapshotState.photo(snapshot, id), id -> new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot, "local_invoice_job:" + id, "{}")));
                        report = BackupInventory.inspect(SnapshotProjection.effective(state), (kind, row) -> originals.problem(kind, row));
                    } catch (Exception unavailable) {
                        extra.put(new JSONObject().put("kind", "inventory").put("reason", "INVENTORY_NOT_VERIFIABLE").put("message", "Bestandsübersicht nicht vollständig auswertbar; Datenbank und vorhandene Dateien sind unverändert enthalten."));
                    }
                }
                JSONArray warnings = report.getJSONArray("warnings");
                for (int i = 0; i < extra.length(); i++) warnings.put(extra.get(i));
                if (db.optLong("foreignKeyViolations") > 0) warnings.put(new JSONObject().put("kind", "database").put("reason", "EXISTING_RELATION_ERRORS").put("count", db.getLong("foreignKeyViolations")).put("message", "Bereits vorhandene fehlerhafte Datenbezüge wurden unverändert gesichert."));
                write(new File(directory, "browser-storage.json"), browser.toString());
                write(new File(directory, "backup-notes.json"), report.toString(2));
                JSONObject metadata = new JSONObject(identity.toString()).put("datasetFormat", "pixel.snapshot.v2")
                        .put("schemaVersion", db.getInt("schemaVersion")).put("database", db).put("applicationId", context.getPackageName())
                        .put("appVersion", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName)
                        .put("capturedAt", java.time.Instant.now().toString()).put("counts", report.getJSONObject("counts"))
                        .put("numberState", new JSONObject()).put("lastIssuedDocumentIds", report.getJSONArray("lastIssuedDocumentIds"))
                        .put("warning_count", warnings.length()).put("captureScope", "database_all_tables_private_files_preferences_browser_storage_and_legacy_backups")
                        .put("media_coverage", "AVAILABLE_FILES_WITH_NOTES");
                return new Captured(directory, metadata);
            } catch (Exception failure) { throw BackupScheduleRules.captureFailure(stage, failure); }
        }
    }
    static JSONObject inspectStaged(File directory, JSONObject manifest) throws Exception {
        JSONObject database = DatabaseBackupSnapshot.inspect(new File(directory, "database.sqlite"));
        for(String required:new String[]{"owner_state","pending","work_sessions","work_session_events","photo_attachments","mcp_uploads"})if(!database.getJSONObject("tableCounts").has(required))throw new IOException("BACKUP_DATABASE_SCHEMA_INVALID");
        if (database.getInt("schemaVersion") != manifest.getInt("schemaVersion") || !database.getString("sha256").equals(manifest.getJSONObject("database").getString("sha256"))) throw new IOException("BACKUP_DATABASE_MANIFEST_MISMATCH");
        JSONObject preferences = new JSONObject(text(new File(directory, "preferences.json")));
        for (String group : BackupPreferences.GROUPS) BackupPreferences.values(preferences, group);
        validBrowser(new JSONObject(text(new File(directory, "browser-storage.json"))));
        // Restore checks copy fidelity, not the validity of invoices, references or old originals.
        File notes = new File(directory, "backup-notes.json");
        return notes.isFile() ? new JSONObject(text(notes)) : new JSONObject().put("counts", manifest.getJSONObject("counts")).put("warnings", new JSONArray());
    }
    private static JSONObject browserSnapshot(SQLiteDatabase database, JSONObject supplied, JSONArray warnings) throws Exception {
        JSONObject browser = supplied;
        try {
            JSONObject saved = new JSONObject(DatabaseBackupSnapshot.ownerValue(database, "full_backup_browser", "{}"));
            if (saved.optJSONObject("browser") != null) browser = saved.getJSONObject("browser");
            validBrowser(browser);
            JSONObject status = new JSONObject(DatabaseBackupSnapshot.ownerValue(database, "full_backup_browser_status", "{}"));
            if (status.optBoolean("capturePending") || !status.optString("code").isEmpty()) throw new IOException("CHECKPOINT_STALE");
        } catch (Exception unavailable) {
            warnings.put(new JSONObject().put("kind", "browser").put("reason", "BROWSER_CHECKPOINT_INCOMPLETE").put("message", "Letzter lesbarer Formularstand enthalten; neuere ungespeicherte Eingaben sind nicht bestätigt. Rohdaten bleiben in der Datenbank."));
            try { validBrowser(browser); } catch (Exception invalid) { browser = new JSONObject().put("formatVersion", 1).put("storage", new JSONObject()); }
        }
        return new JSONObject(browser.toString());
    }
    private static void validBrowser(JSONObject browser) throws Exception {
        if (browser == null || browser.optInt("formatVersion") != 1 || browser.optJSONObject("storage") == null) throw new IOException("BACKUP_BROWSER_STATE_INVALID");
        RestoredBrowserReviewRules.validate(browser);
        JSONObject storage = browser.getJSONObject("storage");
        for (Iterator<String> keys = storage.keys(); keys.hasNext();) if (!(storage.get(keys.next()) instanceof String)) throw new IOException("BACKUP_BROWSER_STORAGE_INVALID");
    }
    /** Evidence for an explicitly invoked table transfer; never called by startup or restore.
     * Verifies the actual selected archive on every step and compares captured/live business
     * state, pending actions, relationships and originals. The staging directory must be fresh.
     */
    static BusinessTableStore.Evidence tableEvidence(PixelLocalStore store,File archive,String recovery,File staging){
        return source->{
            String digest=EncryptedBackupArchive.sha256(archive);
            try(InputStream input=new BufferedInputStream(new FileInputStream(archive));
                EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyArchive(input,recovery,staging,staging.getParentFile().getUsableSpace(),PixelLocalStore.SCHEMA_VERSION)){
                JSONObject manifest=verified.manifest,identity=new BackupDeviceGuard(store.appContext()).current();
                for(String key:new String[]{"installationId","restoreEpoch"})if(!identity.getString(key).equals(manifest.getString(key)))throw new IOException("TABLE_BACKUP_DEVICE_CONTEXT_MISMATCH");
                if(!store.appContext().getPackageName().equals(manifest.getString("applicationId")))throw new IOException("TABLE_BACKUP_APPLICATION_MISMATCH");
                File directory=new File(verified.directory,"data");inspectStaged(directory,manifest);JSONObject capturedInventory;String capturedBusinessHash;
                try(SQLiteDatabase captured=SQLiteDatabase.openDatabase(new File(directory,"database.sqlite").getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
                    JSONObject capturedState=BackupSnapshotState.read(captured,BackupPreferences.values(new JSONObject(text(new File(directory,"preferences.json"))),"haus_garten"),text(new File(directory,"bootstrap/initial-data.json")),text(new File(directory,"bootstrap/service-catalog.json")));
                    capturedBusinessHash=LocalAuthorityMigration.businessHash(capturedState);
                    capturedInventory=LocalMigrationInventory.inspect(capturedState,new LocalOriginalVerifier(new File(directory,"files"),id->BackupSnapshotState.photo(captured,id),id->new JSONObject(DatabaseBackupSnapshot.ownerValue(captured,"local_invoice_job:"+id,"{}"))));
                    JSONObject prefs=BackupPreferences.values(new JSONObject(text(new File(directory,"preferences.json"))),"haus_garten");
                    JSONObject capturedRaw=PixelLocalStore.businessTables(captured).snapshot(prefs.optString("drive_snapshot",text(new File(directory,"bootstrap/initial-data.json"))));
                    if(!PixelMcpWriteTools.hash(source).equals(PixelMcpWriteTools.hash(capturedRaw)))throw new IOException("TABLE_BACKUP_SOURCE_MISMATCH");
                }
                JSONObject live=store.getState().put("extended_business_fingerprint",BusinessStateFingerprint.read(store.getReadableDatabase()));
                if(!LocalAuthorityMigration.businessHash(live).equals(manifest.optString("business_snapshot_sha256",capturedBusinessHash)))throw new IOException("TABLE_BACKUP_BUSINESS_STATE_CHANGED");
                JSONObject inventory=LocalMigrationInventory.inspect(live,new LocalOriginalVerifier(store.appContext().getFilesDir(),store::photoAttachment,id->new JSONObject(store.ownerValue("local_invoice_job:"+id,"{}"))));
                if(!inventory.optBoolean("projection_applied")||inventory.getInt("duplicate_ids")!=0||inventory.getInt("orphan_relations")!=0||inventory.getInt("missing_local_originals")!=0)throw new IOException("TABLE_SOURCE_INTEGRITY_REVIEW_REQUIRED");
                for(String key:new String[]{"counts","id_set_sha256","collection_sha256","pending_sha256","historical_hours_decimal","historical_invoice_amounts_decimal"})if(!PixelMcpWriteTools.hash(inventory.get(key)).equals(PixelMcpWriteTools.hash(capturedInventory.get(key))))throw new IOException("TABLE_BACKUP_INVENTORY_MISMATCH");
                new RevierApplicationService(new RevierSnapshotRepository(store.getReadableDatabase(),SnapshotProjection.effective(live),store.appContext().getFilesDir())).validateRelationships();
                LocalFinancialSnapshotValidation.inspect(store.getReadableDatabase(),SnapshotProjection.effective(live));
                if(!digest.equals(EncryptedBackupArchive.sha256(archive)))throw new IOException("TABLE_BACKUP_CHANGED");
                return digest;
            }
        };
    }
    private static String asset(Context context,String name)throws Exception {try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String text(File file)throws Exception {return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private static void write(File file,String value)throws Exception {File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_STAGING_UNAVAILABLE");try(FileOutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    private LocalBackupDataset(){}
}
