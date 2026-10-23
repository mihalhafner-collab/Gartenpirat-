package at.hausgarten.service;

import org.json.*;
import java.io.*;

/** Prepared real SQLite/AtomicFile job restart and stale-restore protections; QA contexts only. */
final class BackupJobsNativeAcceptance {
    private static void denied(PixelMcpInstrumentation.Task action)throws Exception {try{action.run();}catch(IOException|JSONException expected){return;}throw new AssertionError("Expected guarded job refusal");}
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("backup native job receipt transaction replay CAS and upload checkpoints survive SQLite reopen",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupJobRepository jobs=new BackupJobRepository(f.store,"qa_installation_one","qa_epoch_one");JSONObject target=new JSONObject().put("kind","drive").put("folderId","qa_backup_folder_123");
            String before=PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master"));JSONObject created=jobs.create("qa_job_request_one","qa_backup_job_one",target);test.yes(!created.getBoolean("replayed"));
            test.yes(jobs.create("qa_job_request_one","qa_backup_job_one",target).getBoolean("replayed")&&jobs.inventory().length()==1);
            denied(()->jobs.create("qa_job_request_one","qa_backup_other",target));denied(()->jobs.create("qa_job_request_other","qa_backup_job_one",target));
            JSONObject changed=jobs.update("qa_backup_job_one",created.getString("revision"),new JSONObject().put("phase","CAPTURING"));
            denied(()->jobs.update("qa_backup_job_one",created.getString("revision"),new JSONObject().put("phase","FAILED")));
            test.yes(jobs.update("qa_backup_job_one",created.getString("revision"),new JSONObject().put("phase","CAPTURING")).getString("revision").equals(changed.getString("revision")));
            denied(()->jobs.update("qa_backup_job_one",changed.getString("revision"),new JSONObject().put("installationId","qa_forged_installation")));
            denied(()->jobs.update("qa_backup_job_one",changed.getString("revision"),new JSONObject().put("phase","qa_unknown")));
            DriveBackupUpload.Checkpoint first=jobs.uploadCheckpoint("qa_backup_job_one"),stale=jobs.uploadCheckpoint("qa_backup_job_one");test.yes(first.read()==null&&stale.read()==null);
            JSONObject checkpoint=new JSONObject().put("formatVersion",1).put("state","UPLOADING").put("binding","qa_binding").put("remoteId","qa_fixed_remote_123").put("session","https://www.googleapis.com/upload/drive/v3/files?upload_id=qa_only").put("uploadedBytes",1048576);
            first.save(checkpoint);denied(()->stale.save(new JSONObject(checkpoint.toString()).put("uploadedBytes",2097152)));first.save(checkpoint);
            f.store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER qa_fail_backup_receipt BEFORE INSERT ON owner_state WHEN NEW.key LIKE 'backup_job_request:%' BEGIN SELECT RAISE(ABORT,'qa synthetic receipt failure'); END");
            try{jobs.create("qa_job_atomic_abort","qa_backup_job_abort",target);throw new AssertionError("Expected atomic receipt abort");}catch(android.database.sqlite.SQLiteException expected){test.yes(jobs.inventory().length()==1);}
            f.store.getWritableDatabase().execSQL("DROP TRIGGER qa_fail_backup_receipt");jobs.create("qa_job_atomic_abort","qa_backup_job_abort",target);test.yes(jobs.inventory().length()==2);
            f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){
                BackupJobRepository after=new BackupJobRepository(reopened,"qa_installation_one","qa_epoch_one");test.yes(after.inventory().length()==2&&after.create("qa_job_request_one","qa_backup_job_one",target).getBoolean("replayed"));
                JSONObject saved=after.uploadCheckpoint("qa_backup_job_one").read();test.yes(saved.getLong("uploadedBytes")==1048576&&saved.getString("remoteId").equals("qa_fixed_remote_123"));
                test.yes(PixelMcpWriteTools.hash(reopened.getState().getJSONObject("master")).equals(before));
                denied(()->new BackupJobRepository(reopened,"qa_installation_two","qa_epoch_one").load("qa_backup_job_one"));
                BackupJobRepository restored=new BackupJobRepository(reopened,"qa_installation_one","qa_epoch_after_restore");denied(()->restored.load("qa_backup_job_one"));test.yes(!restored.inventory().getJSONObject(0).getBoolean("resumeContextMatches"));
            }
        }});
        test.probe("backup native device epoch stays outside datasets and restore retries cannot revive old jobs",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupDeviceGuard guard=new BackupDeviceGuard(f.context);JSONObject first=guard.current();test.yes(guard.current().toString().equals(first.toString()));
            JSONObject job=new BackupJobRepository(f.store,first.getString("installationId"),first.getString("restoreEpoch")).create("qa_device_job_request","qa_device_job",new JSONObject().put("kind","saf").put("treeUri","content://qa.example.invalid/qa_tree"));
            JSONObject next=guard.advanceForRestore("qa_restore_epoch_request",first.getString("restoreEpoch"));test.yes(next.getString("installationId").equals(first.getString("installationId"))&&!next.getString("restoreEpoch").equals(first.getString("restoreEpoch")));
            test.yes(guard.advanceForRestore("qa_restore_epoch_request",first.getString("restoreEpoch")).getString("restoreEpoch").equals(next.getString("restoreEpoch")));
            denied(()->guard.advanceForRestore("qa_restore_epoch_request",next.getString("restoreEpoch")));denied(()->guard.advanceForRestore("qa_restore_other_request",first.getString("restoreEpoch")));
            denied(()->new BackupJobRepository(f.store,next.getString("installationId"),next.getString("restoreEpoch")).load(job.getString("backupId")));
            JSONObject browser=new JSONObject().put("formatVersion",1).put("storage",new JSONObject());LocalBackupDataset.Captured capture=LocalBackupDataset.capture(f.context,f.store,new File(f.context.getNoBackupFilesDir(),"qa_guard_capture"),new JSONObject().put("backupId","qa_guard_backup").put("installationId",next.getString("installationId")).put("businessId","qa_business"),browser);
            for(EncryptedBackupArchive.Source source:capture.sources())test.yes(!source.path.contains("backup-device-guard")&&!source.path.contains("backup-key-protection"));
            File state=new File(f.context.getNoBackupFilesDir(),"backup-device-guard/identity.v1");try(FileOutputStream out=new FileOutputStream(state)){out.write("qa deliberately corrupted guard".getBytes(java.nio.charset.StandardCharsets.UTF_8));}String hash=EncryptedBackupArchive.sha256(state);denied(guard::current);test.yes(EncryptedBackupArchive.sha256(state).equals(hash));
        }});
    }
}
