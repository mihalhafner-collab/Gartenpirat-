package at.hausgarten.service;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Full roundtrip in unused device-protected storage of a fresh synthetic fixture. */
final class LocalBackupNativeTests {
    private int checks;
    private void yes(boolean value) { if (!value) throw new AssertionError("NATIVE_BACKUP_CHECK_" + checks); checks++; }
    private interface Task { void run() throws Exception; }
    private void rejects(Task task) throws Exception { try { task.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError("EXPECTED_BACKUP_REJECTION"); }
    static int run(Instrumentation test, boolean reopen) throws Exception { return new LocalBackupNativeTests().execute(test, reopen); }
    private int execute(Instrumentation test, boolean reopen) throws Exception {
        Context testContext = test.getContext();
        if (!"at.hausgarten.service.test".equals(testContext.getPackageName())) throw new IOException("ISOLATED_TEST_APK_REQUIRED");
        Context context = new ContextWrapper(test.getTargetContext().createDeviceProtectedStorageContext()) {
            @Override public Context getApplicationContext() { return this; }
        };
        if (!context.isDeviceProtectedStorage()) throw new IOException("ISOLATED_STORAGE_REQUIRED");
        documentBoundaryChecks();
        File database = context.getDatabasePath("haus-garten-mobile.db");
        File marker = new File(context.getNoBackupFilesDir(), "qa-backup-roundtrip.json");
        if (reopen) {
            yes(marker.isFile());
            JSONObject expected = new JSONObject(text(marker));
            yes(!RestoreStartupProtection.requiresColdRestart(context));
            try (PixelLocalStore store = new PixelLocalStore(context)) {
                yes(store.ownerValue("qa_backup_marker", "").equals("QA before save"));
                yes(context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE).getString("drive_snapshot", "").equals(expected.getString("snapshot")));
                try (android.database.Cursor row = store.getReadableDatabase().rawQuery("SELECT payload FROM qa_backup_payload", null)) { yes(row.moveToFirst()); yes(row.getBlob(0).length == 65536); }
            }
            yes(text(new File(context.getFilesDir(), "qa-extra.txt")).equals("QA exact file before save"));
            return checks;
        }
        // The app uses credential-protected storage; this separate fixture must be empty.
        if (database.exists() || marker.exists() || context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE).getAll().size() != 0) throw new IOException("FRESH_TEST_APK_SANDBOX_REQUIRED");
        JSONObject snapshot = SyntheticBusinessData.profile("minimal");
        snapshot.getJSONArray("customers").getJSONObject(0).put("name", "QA Grün 🌿");
        for (int i = 0; i < 2; i++) snapshot.getJSONArray("invoices").put(new JSONObject().put("id", "qa_duplicate_invoice_" + i).put("customer_id", "qa_customer_1").put("invoice_number", "42").put("issued_at", "2026-10-29T10:00:00Z").put("amount", "45.00").put("test_record", true));
        snapshot.getJSONArray("photos").put(new JSONObject().put("id", "qa_missing_original").put("customer_id", "qa_customer_1").put("storage_name", "b".repeat(64) + ".jpg").put("content_sha256", "b".repeat(64)).put("byte_size", 10).put("test_record", true));
        snapshot.getJSONArray("photos").put(new JSONObject().put("id", "qa_missing_metadata").put("customer_id", "qa_customer_1").put("filename", "QA missing metadata.jpg").put("test_record", true));
        String originalSnapshot = snapshot.toString();
        yes(context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE).edit().putString("drive_snapshot", originalSnapshot).putLong("qa_long", 1234567890123L).commit());
        write(new File(context.getFilesDir(), "qa-extra.txt"), "QA exact file before save");
        File saved = new File(context.getNoBackupFilesDir(), "qa-saved.zip");
        yes(saved.createNewFile());
        int[] writes = {0};
        BackupDocumentTransfer.LocalFile provider = new BackupDocumentTransfer.LocalFile() {
            public OutputStream write() throws Exception { writes[0]++; return new FileOutputStream(saved); }
            public InputStream read() throws Exception { return new FileInputStream(saved); }
            public void replaceFrom(File archive, String previousHash, long previousSize) throws Exception { writes[0]++; BackupDocumentTransfer.atomicReplace(saved, archive, previousHash, previousSize); }
        };
        String id;
        try (PixelLocalStore store = new PixelLocalStore(context)) {
            store.putOwnerValue("qa_backup_marker", "QA before save");
            store.putOwnerValue("qa_backup_metadata", new JSONObject().put("synthetic_value", 42).toString());
            JSONObject identity = new BackupDeviceGuard(context).current();
            SQLiteDatabase db = store.getWritableDatabase();
            db.execSQL("CREATE TABLE qa_backup_payload (id INTEGER PRIMARY KEY, payload BLOB)");
            byte[] blob = new byte[65536]; new Random(71).nextBytes(blob);
            db.execSQL("INSERT INTO qa_backup_payload(id,payload) VALUES(1,?)", new Object[]{blob});
            walSnapshotChecks(context, db, blob);
            // An existing foreign-key defect must be preserved, not repaired or used as a save gate.
            db.execSQL("PRAGMA foreign_keys=OFF");
            db.execSQL("CREATE TABLE qa_parent(id INTEGER PRIMARY KEY)");
            db.execSQL("CREATE TABLE qa_child(id INTEGER, parent_id INTEGER REFERENCES qa_parent(id))");
            db.execSQL("INSERT INTO qa_child VALUES(1,99)");
            PixelBackupService service = new PixelBackupService(context, store);
            File orphan = new File(context.getNoBackupFilesDir(), "pixel-backup/attempt-orphan/capture/old.bin");
            yes(orphan.getParentFile().mkdirs());
            write(orphan, "QA orphaned backup bytes");
            File stray = new File(context.getNoBackupFilesDir(), "pixel-backup/old.zip");
            write(stray, "QA orphaned archive");
            yes(PixelBackupService.cleanupWork(context) > 0); yes(!orphan.exists() && !stray.exists());
            yes(orphan.getParentFile().mkdirs());
            write(orphan, "QA orphan before next backup");
            JSONObject result = service.saveTo("qa_native_save_001", "qa_destination", provider, message -> {
                if (message.startsWith("Originaldateien")) {
                    yes(!orphan.exists());
                    File root = new File(context.getNoBackupFilesDir(), "pixel-backup");
                    for (File attempt : root.listFiles()) {
                        yes(!new File(attempt,"capture").exists() && !new File(attempt,"verify").exists() && !new File(attempt,"backup.zip").exists());
                        yes(!new File(attempt,"snapshot/files").exists());
                        yes(new File(attempt,"snapshot/database.sqlite").isFile());
                    }
                }
            });
            id = result.getString("backup_id");
            yes(result.getJSONObject("logical_comparison").getString("mode").equals("FULL_BASELINE"));
            yes(result.getBoolean("verified")); yes(result.getInt("warning_count") >= 3); yes(writes[0] == 1);
            yes(store.ownerValue("qa_backup_marker", "").equals("QA before save"));
            yes(service.status(new JSONObject()).getString("state").equals("VERIFIED"));
            service.saveTo("qa_native_save_001", "qa_destination", provider, message -> {}); yes(writes[0] == 1);
            JSONObject second = service.saveTo("qa_native_save_002", "qa_destination", provider, message -> {});
            yes(second.getBoolean("verified")); yes(writes[0] == 2);
            JSONObject logical = second.getJSONObject("logical_comparison");
            yes(logical.getString("mode").equals("INCREMENTAL"));
            // Persisting the previous backup job/proof must not change any business original.
            JSONObject records = logical.getJSONObject("records");
            yes(records.getInt("added") == 0 && records.getInt("changed") == 0 && records.getInt("deleted") == 0);
            String confirmed = store.ownerValue("pixel_backup_success", "");
            boolean[] replacementDone = {false}, readbackFails = {true};
            BackupDocumentTransfer.LocalFile flaky = new BackupDocumentTransfer.LocalFile() {
                public OutputStream write() { throw new AssertionError("existing target must not be truncated"); }
                public InputStream read() throws Exception {
                    if (replacementDone[0] && readbackFails[0]) throw new IOException("QA readback interrupted after atomic replacement");
                    return new FileInputStream(saved);
                }
                public void replaceFrom(File archive, String hash, long size) throws Exception {
                    BackupDocumentTransfer.atomicReplace(saved, archive, hash, size); writes[0]++; replacementDone[0] = true;
                }
            };
            rejects(() -> service.saveTo("qa_native_save_003", "qa_destination", flaky, message -> {}));
            yes(replacementDone[0] && writes[0] == 3);
            yes(service.status(new JSONObject()).getString("state").equals("FAILED"));
            yes(store.ownerValue("pixel_backup_success", "").equals(confirmed));
            yes(service.targetSelection().getString("request_id").equals("qa_native_save_003"));
            String pendingComparison = PixelMcpWriteTools.hash(new JSONObject(store.ownerValue("pixel_backup_last", "")).getJSONObject("logical_comparison"));
            readbackFails[0] = false;
            JSONObject resumed = service.saveTo("qa_native_save_003", "qa_destination", flaky, message -> {});
            yes(resumed.getBoolean("verified") && writes[0] == 3);
            yes(PixelMcpWriteTools.hash(resumed.getJSONObject("logical_comparison")).equals(pendingComparison));
            yes(PixelMcpWriteTools.hash(service.saveTo("qa_native_save_003", "qa_destination", flaky, message -> {}).getJSONObject("logical_comparison")).equals(pendingComparison) && writes[0] == 3);
            yes(service.targetSelection().getString("uri").equals("qa_destination"));
            yes(service.targetSelection().getString("request_id").isEmpty());
            String savedHash = BackupArchive.sha256(saved);
            BackupDocumentTransfer.LocalFile broken = new BackupDocumentTransfer.LocalFile() {
                public OutputStream write() throws Exception { throw new IOException("QA disk full"); }
                public InputStream read() { return null; }
            };
            rejects(() -> service.saveTo("qa_native_failure", "qa_other_destination", broken, message -> {}));
            yes(service.status(new JSONObject()).getString("state").equals("FAILED"));
            yes(BackupArchive.sha256(saved).equals(savedHash));
            File work = new File(context.getNoBackupFilesDir(), "pixel-backup"); yes(!work.exists()); // No empty work folder remains (owner decision 03.11.2026).
        }
        try (BackupArchive.Verified checked = BackupArchive.verifyPlain(new FileInputStream(saved), new File(context.getNoBackupFilesDir(), "qa-check"), context.getNoBackupFilesDir().getUsableSpace(), PixelLocalStore.SCHEMA_VERSION)) {
            JSONObject report = LocalBackupDataset.inspectStaged(new File(checked.directory, "data"), checked.manifest);
            yes(report.getJSONArray("warnings").toString().contains("qa_missing_original"));
            yes(report.getJSONArray("warnings").toString().contains("qa_missing_metadata"));
            yes(text(checked.file("files/qa-extra.txt")).equals("QA exact file before save"));
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(checked.file("database.sqlite").getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                yes(DatabaseBackupSnapshot.ownerValue(db, "qa_backup_marker", "").equals("QA before save"));
                yes(new JSONObject(DatabaseBackupSnapshot.ownerValue(db, "qa_backup_metadata", "{}")).getInt("synthetic_value") == 42);
                yes(DatabaseBackupSnapshot.inspect(checked.file("database.sqlite")).getLong("foreignKeyViolations") == 1);
            }
        }
        BackupRestoreService restore = new BackupRestoreService(context);
        byte[] zipBytes = Files.readAllBytes(saved.toPath());
        rejects(() -> restore.previewFrom("qa_corrupt_preview", "qa_corrupt", () -> new ByteArrayInputStream(Arrays.copyOf(zipBytes, zipBytes.length - 40))));
        JSONObject preview = restore.previewFrom("qa_native_preview", "qa_saved", () -> new FileInputStream(saved));
        yes(preview.getString("phase").equals("VALIDATED"));
        yes(preview.getJSONObject("summary").getInt("warning_count") >= 3);
        try (PixelLocalStore store = new PixelLocalStore(context)) { store.putOwnerValue("qa_backup_marker", "QA after save"); }
        write(new File(context.getFilesDir(), "qa-extra.txt"), "QA changed after save");
        restore.requestActivation("qa_native_activate", preview.getString("restore_id"), preview.getString("preview_hash"), true);
        JSONObject activated = BackupRestoreService.activatePending(context, new RestoreStartupProtection(context));
        yes(activated.getString("phase").equals("ACTIVE"));
        yes(!new File(context.getNoBackupFilesDir(), "before-restore").exists());
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            yes(DatabaseBackupSnapshot.ownerValue(db, "qa_backup_marker", "").equals("QA before save"));
            yes(new JSONObject(DatabaseBackupSnapshot.ownerValue(db, "qa_backup_metadata", "{}")).getInt("synthetic_value") == 42);
            // The removed WebView review leaves no restore checkpoint or media warning behind.
            yes(DatabaseBackupSnapshot.ownerValue(db,"restored_browser_checkpoint_active","").isEmpty());
            yes(DatabaseBackupSnapshot.ownerValue(db,"restore_media_warning","").isEmpty());
        }
        yes(text(new File(context.getFilesDir(), "qa-extra.txt")).equals("QA exact file before save"));
        write(marker, new JSONObject().put("snapshot", originalSnapshot).put("backup_id", id).toString());
        return checks;
    }
    private void documentBoundaryChecks() throws Exception {
        // URI validation only: no real provider, account, Drive file or network is contacted.
        PixelBackupService.requireDocument(android.net.Uri.parse("content://com.android.providers.downloads.documents/document/qa_backup.zip")); yes(true);
        PixelBackupService.requireDocument(android.net.Uri.parse("content://com.google.android.apps.docs.storage/document/qa_backup.zip")); yes(true);
        rejects(() -> PixelBackupService.requireDocument(null));
        rejects(() -> PixelBackupService.requireDocument(android.net.Uri.parse("file:///qa_backup.zip")));
        rejects(() -> PixelBackupService.requireDocument(android.net.Uri.parse("https://example.invalid/qa_backup.zip")));
        rejects(() -> PixelBackupService.requireDocument(android.net.Uri.parse("content:///qa_backup.zip")));
    }
    private void walSnapshotChecks(Context context, SQLiteDatabase source, byte[] expected) throws Exception {
        yes(source.isWriteAheadLoggingEnabled());
        try (android.database.Cursor mode = source.rawQuery("PRAGMA journal_mode", null)) {
            yes(mode.moveToFirst()); yes("wal".equalsIgnoreCase(mode.getString(0)));
        }
        int checkpoint;
        try (android.database.Cursor setting = source.rawQuery("PRAGMA wal_autocheckpoint", null)) {
            if (!setting.moveToFirst()) throw new IOException("QA_WAL_SETTING_UNAVAILABLE");
            checkpoint = setting.getInt(0);
        }
        File directory = Files.createTempDirectory(context.getNoBackupFilesDir().toPath(), "qa-wal-snapshot-").toFile();
        try {
            try (android.database.Cursor setting = source.rawQuery("PRAGMA wal_autocheckpoint=0", null)) {
                if (!setting.moveToFirst() || setting.getInt(0) != 0) throw new IOException("QA_WAL_SETTING_UNAVAILABLE");
            }
            // Commit a fresh marker after disabling automatic checkpointing. Capture must
            // include it without copying the live DB file or requiring a checkpoint first.
            source.execSQL("INSERT INTO qa_backup_payload(id,payload) VALUES(2,?)", new Object[]{expected});
            File wal = new File(source.getPath() + "-wal"); yes(wal.isFile() && wal.length() > 32);
            File captured = new File(directory, "snapshot.sqlite");
            JSONObject evidence = DatabaseBackupSnapshot.capture(source, captured);
            yes(evidence.getInt("schemaVersion") == PixelLocalStore.SCHEMA_VERSION);
            try (SQLiteDatabase image = SQLiteDatabase.openDatabase(captured.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                 android.database.Cursor row = image.rawQuery("SELECT payload FROM qa_backup_payload WHERE id=2", null)) {
                yes(row.moveToFirst()); yes(Arrays.equals(expected, row.getBlob(0))); yes(!row.moveToNext());
            }
            // Keep the surrounding original roundtrip fixture at its original one-row size.
            source.execSQL("DELETE FROM qa_backup_payload WHERE id=2");
        } finally {
            try (android.database.Cursor setting = source.rawQuery("PRAGMA wal_autocheckpoint=" + checkpoint, null)) {
                if (!setting.moveToFirst() || setting.getInt(0) != checkpoint) throw new IOException("QA_WAL_SETTING_UNAVAILABLE");
            }
            BackupArchive.removeOwned(directory);
        }
    }
    private static String text(File file) throws IOException { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private static void write(File file, String value) throws IOException { Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8)); }
    private LocalBackupNativeTests() {}
}
