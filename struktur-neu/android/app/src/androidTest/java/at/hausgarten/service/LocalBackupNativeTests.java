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

/** Full roundtrip in unused device-protected storage of a freshly installed disposable emulator. */
final class LocalBackupNativeTests {
    private int checks;
    private void yes(boolean value) { if (!value) throw new AssertionError("NATIVE_BACKUP_CHECK_" + checks); checks++; }
    private interface Task { void run() throws Exception; }
    private void rejects(Task task) throws Exception { try { task.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError("EXPECTED_BACKUP_REJECTION"); }
    static int run(Instrumentation test, boolean reopen) throws Exception { return new LocalBackupNativeTests().execute(test, reopen); }
    private int execute(Instrumentation test, boolean reopen) throws Exception {
        Context testContext = test.getContext();
        if (!"at.hausgarten.service.test".equals(testContext.getPackageName())) throw new IOException("ISOLATED_TEST_APK_REQUIRED");
        if (!Arrays.asList("ranchu", "goldfish").contains(android.os.Build.HARDWARE)) throw new IOException("DISPOSABLE_EMULATOR_REQUIRED");
        Context context = new ContextWrapper(test.getTargetContext().createDeviceProtectedStorageContext()) {
            @Override public Context getApplicationContext() { return this; }
        };
        if (!context.isDeviceProtectedStorage()) throw new IOException("ISOLATED_STORAGE_REQUIRED");
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
        int[] writes = {0};
        BackupDocumentTransfer.LocalFile provider = new BackupDocumentTransfer.LocalFile() {
            public OutputStream write() throws Exception { writes[0]++; return new FileOutputStream(saved); }
            public InputStream read() throws Exception { return new FileInputStream(saved); }
        };
        String id;
        try (PixelLocalStore store = new PixelLocalStore(context)) {
            store.putOwnerValue("qa_backup_marker", "QA before save");
            store.putOwnerValue("local_invoice_counter", new JSONObject().put("last_number", 42).toString());
            JSONObject identity = new BackupDeviceGuard(context).current();
            JSONObject browser = new JSONObject().put("formatVersion", 1).put("storage", new JSONObject().put("hausgarten.entry.draft.v1", "QA preserved draft 🌿"));
            store.putOwnerValue("full_backup_browser", new JSONObject(identity.toString()).put("formatVersion", 1).put("capturedAt", java.time.Instant.now().toString()).put("browser", browser).put("contentSha256", PixelMcpWriteTools.hash(browser)).toString());
            store.putOwnerValue(BrowserMediaRepositoryRules.PREFIX+"state",new JSONObject().put("formatVersion",99).toString());
            SQLiteDatabase db = store.getWritableDatabase();
            db.execSQL("CREATE TABLE qa_backup_payload (id INTEGER PRIMARY KEY, payload BLOB)");
            byte[] blob = new byte[65536]; new Random(71).nextBytes(blob);
            db.execSQL("INSERT INTO qa_backup_payload(id,payload) VALUES(1,?)", new Object[]{blob});
            // An existing foreign-key defect must be preserved, not repaired or used as a save gate.
            db.execSQL("PRAGMA foreign_keys=OFF");
            db.execSQL("CREATE TABLE qa_parent(id INTEGER PRIMARY KEY)");
            db.execSQL("CREATE TABLE qa_child(id INTEGER, parent_id INTEGER REFERENCES qa_parent(id))");
            db.execSQL("INSERT INTO qa_child VALUES(1,99)");
            PixelBackupService service = new PixelBackupService(context, store);
            JSONObject result = service.saveTo("qa_native_save_001", "qa_destination", provider, message -> {});
            id = result.getString("backup_id");
            yes(result.getBoolean("verified")); yes(result.getInt("warning_count") >= 4); yes(writes[0] == 1);
            yes(store.ownerValue("qa_backup_marker", "").equals("QA before save"));
            yes(service.status(new JSONObject()).getString("state").equals("VERIFIED"));
            service.saveTo("qa_native_save_001", "qa_destination", provider, message -> {}); yes(writes[0] == 1);
            rejects(() -> service.saveTo("qa_native_save_002", "qa_destination", provider, message -> {})); yes(writes[0] == 1);
            String savedHash = EncryptedBackupArchive.sha256(saved);
            BackupDocumentTransfer.LocalFile broken = new BackupDocumentTransfer.LocalFile() {
                public OutputStream write() throws Exception { throw new IOException("QA disk full"); }
                public InputStream read() { return null; }
            };
            rejects(() -> service.saveTo("qa_native_failure", "qa_other_destination", broken, message -> {}));
            yes(service.status(new JSONObject()).getString("state").equals("FAILED"));
            yes(EncryptedBackupArchive.sha256(saved).equals(savedHash));
            File work = new File(context.getNoBackupFilesDir(), "pixel-backup"); yes(work.list() != null && work.list().length == 0);
        }
        try (EncryptedBackupArchive.Verified checked = EncryptedBackupArchive.verifyPlain(new FileInputStream(saved), new File(context.getNoBackupFilesDir(), "qa-check"), context.getNoBackupFilesDir().getUsableSpace(), PixelLocalStore.SCHEMA_VERSION)) {
            JSONObject report = LocalBackupDataset.inspectStaged(new File(checked.directory, "data"), checked.manifest);
            yes(report.getJSONArray("warnings").toString().contains("qa_missing_original"));
            yes(report.getJSONArray("warnings").toString().contains("qa_missing_metadata"));
            yes(text(checked.file("files/qa-extra.txt")).equals("QA exact file before save"));
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(checked.file("database.sqlite").getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                yes(DatabaseBackupSnapshot.ownerValue(db, "qa_backup_marker", "").equals("QA before save"));
                yes(new JSONObject(DatabaseBackupSnapshot.ownerValue(db, "local_invoice_counter", "{}")).getInt("last_number") == 42);
                yes(DatabaseBackupSnapshot.inspect(checked.file("database.sqlite")).getLong("foreignKeyViolations") == 1);
            }
        }
        BackupRestoreService restore = new BackupRestoreService(context);
        byte[] zipBytes = Files.readAllBytes(saved.toPath());
        rejects(() -> restore.previewFrom("qa_corrupt_preview", "qa_corrupt", "", () -> new ByteArrayInputStream(Arrays.copyOf(zipBytes, zipBytes.length - 40))));
        JSONObject preview = restore.previewFrom("qa_native_preview", "qa_saved", "", () -> new FileInputStream(saved));
        yes(preview.getString("phase").equals("VALIDATED"));
        yes(preview.getJSONObject("summary").getInt("warning_count") >= 4);
        try (PixelLocalStore store = new PixelLocalStore(context)) { store.putOwnerValue("qa_backup_marker", "QA after save"); }
        write(new File(context.getFilesDir(), "qa-extra.txt"), "QA changed after save");
        restore.requestActivation("qa_native_activate", preview.getString("restore_id"), preview.getString("preview_hash"), true);
        JSONObject activated = BackupRestoreService.activatePending(context, new RestoreStartupProtection(context));
        yes(activated.getString("phase").equals("ACTIVE"));
        yes(restore.previousPreservation().getBoolean("available"));
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            yes(DatabaseBackupSnapshot.ownerValue(db, "qa_backup_marker", "").equals("QA before save"));
            yes(new JSONObject(DatabaseBackupSnapshot.ownerValue(db, "local_invoice_counter", "{}")).getInt("last_number") == 42);
            yes(!DatabaseBackupSnapshot.ownerValue(db,"restored_browser_checkpoint_active","").isEmpty());
            yes(!DatabaseBackupSnapshot.ownerValue(db,"restore_media_warning","").isEmpty());
        }
        yes(text(new File(context.getFilesDir(), "qa-extra.txt")).equals("QA exact file before save"));
        write(marker, new JSONObject().put("snapshot", originalSnapshot).put("backup_id", id).toString());
        return checks;
    }
    private static String text(File file) throws IOException { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private static void write(File file, String value) throws IOException { Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8)); }
    private LocalBackupNativeTests() {}
}
