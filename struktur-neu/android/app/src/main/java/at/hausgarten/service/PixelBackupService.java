package at.hausgarten.service;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** A direct local snapshot -> ZIP -> provider readback. No durable job engine or migration gate. */
final class PixelBackupService {
    private static final Set<String> RUNNING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Object SAVE_LOCK = new Object();
    private final Context context;
    private final PixelLocalStore store;
    PixelBackupService(Context context, PixelLocalStore store) { this.context = context; this.store = store; }
    static void cancelRetiredJobs(Context context) {
        try {
            android.app.job.JobScheduler jobs = context.getSystemService(android.app.job.JobScheduler.class);
            if (jobs != null) for (android.app.job.JobInfo job : jobs.getAllPendingJobs())
                if (job.getService().getClassName().endsWith(".FullBackupJob")) jobs.cancel(job.getId());
        } catch (RuntimeException unavailable) { /* No retired service remains in the manifest. */ }
    }
    static void requirePixel(Uri uri) throws Exception {
        if (uri == null || !"content".equals(uri.getScheme()) || !("com.android.providers.downloads.documents".equals(uri.getAuthority())
                || "com.android.externalstorage.documents".equals(uri.getAuthority()) && DocumentsContract.getDocumentId(uri).startsWith("primary:"))) throw new IOException("BACKUP_PIXEL_FOLDER_REQUIRED");
    }
    JSONObject status(JSONObject args) throws Exception {
        JSONArray records = new JSONArray(); Set<String> ids = new HashSet<>();
        JSONObject identity = new BackupDeviceGuard(context).current();
        for (String key : new String[]{"pixel_backup_last", "pixel_backup_success"}) {
            String raw = store.ownerValue(key, ""); if (raw.isEmpty()) continue;
            JSONObject job = new JSONObject(raw);
            if (ids.add(job.getString("backupId"))) records.put(new JSONObject().put("job", job.put("revision", PixelMcpWriteTools.hash(job)))
                    .put("sameInstallation", identity.getString("installationId").equals(job.optString("installationId")))
                    .put("sameRestoreEpoch", identity.getString("restoreEpoch").equals(job.optString("restoreEpoch"))));
        }
        return BackupStatusRules.project(records, args, new HashSet<>(RUNNING), Instant.now())
                .put("format_version", 3).put("mode", "PIXEL_PLAINTEXT_ZIP").put("encrypted", false)
                .put("settings_hash", PixelMcpWriteTools.hash(identity)).put("requires_google_account", false)
                .put("requires_recovery_key", false).put("cloud_confirmed", false);
    }
    JSONObject request(JSONObject args) throws Exception { return filePicker("open_backup_screen_and_choose_pixel_file"); }
    JSONObject retry(JSONObject args) throws Exception { return filePicker("resume_existing_save_in_backup_screen"); }
    private JSONObject filePicker(String action) throws Exception {
        return new JSONObject().put("accepted_locally", false).put("code", "BACKUP_OPEN_PIXEL_FILE_PICKER").put("next_action", action).put("cloud_confirmed", false);
    }
    JSONObject save(String request, Uri target, java.util.function.Consumer<String> progress) throws Exception {
        requirePixel(target);
        return saveTo(request,target.toString(),new BackupDocumentTransfer.LocalFile(){
            public OutputStream write()throws Exception{return context.getContentResolver().openOutputStream(target,"wt");}
            public InputStream read()throws Exception{return context.getContentResolver().openInputStream(target);}
        },progress);
    }
    // Same implementation for the Android provider and an isolated native test destination.
    JSONObject saveTo(String request,String destination,BackupDocumentTransfer.LocalFile selectedFile,java.util.function.Consumer<String> progress)throws Exception {
        if (request == null || !request.matches("[A-Za-z0-9_-]{8,200}")) throw new IOException("BACKUP_REQUEST_ID_REQUIRED");
        String id = "pixel_" + PixelMcpWriteTools.hash(request).substring(0, 32);
        synchronized (SAVE_LOCK) { if (!RUNNING.isEmpty()) throw new IOException("BACKUP_RUNNER_BUSY"); RUNNING.add(id); }
        File work = null; JSONObject record = null;
        try {
            JSONObject identity = new BackupDeviceGuard(context).current();
            String previous = store.ownerValue("pixel_backup_success", "");
            if (!previous.isEmpty()) {
                JSONObject saved = new JSONObject(previous);
                if (destination.equals(saved.getJSONObject("destination").optString("treeUri"))) {
                    if (!request.equals(saved.optString("requestId"))) throw new IOException("BACKUP_TARGET_ALREADY_VERIFIED");
                    record=new JSONObject(saved.toString());
                    record.put("lastLocalAttemptAt",Instant.now().toString());
                    JSONObject archive = saved.getJSONObject("archive");
                    try (InputStream input = selectedFile.read()) {
                        BackupDocumentTransfer.verifyReadback(input, archive.getString("sha256"), archive.getLong("size"));
                    }
                    record.remove("lastErrorCode");record.put("phase","PROVIDER_VERIFIED");
                    store.putOwnerValue("pixel_backup_last", record.toString());
                    return result(record);
                }
            }
            String now = Instant.now().toString();
            record = new JSONObject().put("backupId", id).put("requestId", request).put("createdAt", now).put("lastLocalAttemptAt", now)
                    .put("installationId", identity.getString("installationId")).put("restoreEpoch", identity.getString("restoreEpoch"))
                    .put("destination", new JSONObject().put("purpose", "pixel_plain_backup").put("treeUri", destination)).put("phase", "CAPTURING");
            store.putOwnerValue("pixel_backup_last", record.toString());
            File parent = new File(context.getNoBackupFilesDir(), "pixel-backup");
            if (Files.isSymbolicLink(parent.toPath()) || !parent.isDirectory() && !parent.mkdirs()) throw new IOException("BACKUP_WORK_STORAGE_UNAVAILABLE");
            work = Files.createTempDirectory(parent.toPath(), "attempt-").toFile();
            progress.accept("Vorhandenen Bestand aufnehmen …");
            JSONObject captureIdentity = new JSONObject(identity.toString()).put("backupId", id).put("businessId", context.getPackageName());
            LocalBackupDataset.Captured captured = LocalBackupDataset.capture(context, store, new File(work, "capture"), captureIdentity, null);
            File zip = new File(work, "backup.zip");
            progress.accept("ZIP-Datei erstellen und prüfen …");
            try (FileOutputStream output = new FileOutputStream(zip)) {
                EncryptedBackupArchive.writePlain(new FilterOutputStream(BackupStorageBudget.guard(output, work)) { @Override public void close() throws IOException { flush(); } }, captured.metadata, captured.sources());
                output.getFD().sync();
            }
            try (EncryptedBackupArchive.Verified checked = EncryptedBackupArchive.verifyPlain(new FileInputStream(zip), new File(work, "verify"), work.getUsableSpace() - BackupStorageBudget.BUSINESS_RESERVE_BYTES, PixelLocalStore.SCHEMA_VERSION)) {
                LocalBackupDataset.inspectStaged(new File(checked.directory, "data"), checked.manifest);
            }
            String sha = EncryptedBackupArchive.sha256(zip);
            progress.accept("ZIP-Datei speichern und vollständig zurücklesen …");
            BackupDocumentTransfer.exportLocal(zip, sha, false, selectedFile);
            String verifiedAt = Instant.now().toString();
            record.put("phase", "PROVIDER_VERIFIED").put("warning_count", captured.metadata.optInt("warning_count"))
                    .put("archive", new JSONObject().put("archiveFormat", "PLAIN_ZIP").put("sha256", sha).put("size", zip.length())
                            .put("capturedAt", captured.metadata.getString("capturedAt")).put("localVerifiedAt", verifiedAt).put("mediaCoverage", "AVAILABLE_FILES_WITH_NOTES"))
                    .put("provider", new JSONObject().put("backup_id", id).put("archive_sha256", sha).put("byte_size", zip.length()).put("verified_at", verifiedAt)
                            .put("verification_scope", "DOCUMENT_PROVIDER_READBACK").put("destination_location", "SAME_DEVICE").put("external_storage_verified", false));
            store.putOwnerValue("pixel_backup_success", record.toString()); store.putOwnerValue("pixel_backup_last", record.toString());
            return result(record);
        } catch (Exception failure) {
            if (record != null) try {
                record.put("phase", "FAILED").put("lastErrorCode", BackupScheduleRules.errorCode(failure)).put("lastAttemptAt", Instant.now().toString());
                store.putOwnerValue("pixel_backup_last", record.toString());
            } catch (Exception statusFailure) { failure.addSuppressed(statusFailure); }
            throw failure;
        } finally {
            RUNNING.remove(id);
            if (work != null) try { EncryptedBackupArchive.removeOwned(work); } catch (IOException cleanup) { /* Only this attempt is disposable; saved user files stay untouched. */ }
        }
    }
    private static JSONObject result(JSONObject record) throws Exception {
        return new JSONObject().put("backup_id", record.getString("backupId")).put("saved", true).put("verified", true)
                .put("encrypted", false).put("byte_size", record.getJSONObject("archive").getLong("size")).put("warning_count", record.optInt("warning_count")).put("cloud_confirmed", false);
    }
}
