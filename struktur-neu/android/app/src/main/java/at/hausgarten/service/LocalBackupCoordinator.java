package at.hausgarten.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Local capture, plaintext ZIP preparation and file-reference verification. No network. */
final class LocalBackupCoordinator {
    private static final ReentrantLock LOCAL_RUNNING=new ReentrantLock();
    private final Context context;private final PixelLocalStore store;private final BackupDeviceGuard guard;private final JSONObject identity;private final BackupJobRepository jobs;
    LocalBackupCoordinator(Context context,PixelLocalStore store)throws Exception {
        this.context=context;this.store=store;guard=new BackupDeviceGuard(context);identity=guard.current();jobs=new BackupJobRepository(store,identity.getString("installationId"),identity.getString("restoreEpoch"));
    }
    JSONObject create(String requestId,String backupId,JSONObject destination,JSONObject browser)throws Exception {
        return createWithIntent(requestId,backupId,destination,browser,null);
    }
    JSONObject createWithIntent(String requestId,String backupId,JSONObject destination,JSONObject browser,String intent)throws Exception {
        currentContext();if(browser==null||browser.optInt("formatVersion")!=1||browser.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_REQUIRED");
        String kind=destination.getString("kind");
        if(!kind.equals("saf")||!destination.optString("treeUri").startsWith("content://"))throw new IOException("BACKUP_LOCAL_DESTINATION_REQUIRED");
        if(intent!=null&&!Arrays.asList("manual","automatic").contains(intent))throw new IOException("BACKUP_INTENT_INVALID");
        byte[] browserBytes=browser.toString().getBytes(StandardCharsets.UTF_8);if(browserBytes.length>16*1024*1024)throw new IOException("BACKUP_BROWSER_STATE_TOO_LARGE");
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            String business=store.ownerValue("backup_business_id","");if(business.isEmpty()){business=UUID.randomUUID().toString();store.putOwnerValue("backup_business_id",business);}
            JSONObject inputs=new JSONObject().put("browserSha256",PixelMcpWriteTools.hash(browser)).put("businessId",business);if(intent!=null)inputs.put("intent",intent);JSONObject job=jobs.create(requestId,backupId,destination,inputs);
            if(!job.getBoolean("replayed")){
                store.putOwnerValue("backup_job_browser:"+backupId,browser.toString());
            }
            db.setTransactionSuccessful();return job;
        }finally{db.endTransaction();}
    }
    JSONObject status(String backupId)throws Exception {currentContext();return jobs.load(backupId);}
    JSONArray inventory()throws Exception {currentContext();return jobs.inventory();}
    JSONArray records()throws Exception {currentContext();return jobs.records();}
    JSONObject prepareLocal(String backupId)throws Exception {
        if(store.getWritableDatabase().inTransaction())throw new IOException("BACKUP_PREPARE_REQUIRES_OUTSIDE_TRANSACTION");
        if(!LOCAL_RUNNING.tryLock())return status(backupId).put("runnerBusy",true);
        try{beginAttempt(backupId);return prepareLocked(backupId);}catch(Exception failure){recordFailure(backupId,failure);throw failure;}finally{LOCAL_RUNNING.unlock();}
    }
    private void beginAttempt(String backupId)throws Exception {step(jobs.load(backupId),new JSONObject().put("lastLocalAttemptAt",java.time.Instant.now().toString()).put("lastErrorCode",""));}
    /** Persist the safe failure while retaining captured files and any earlier verification proof. */
    void recordFailure(String backupId,Exception failure){
        try{JSONObject job=jobs.load(backupId),changes=new JSONObject().put("lastErrorCode",BackupScheduleRules.errorCode(failure)).put("lastAttemptAt",java.time.Instant.now().toString());
            if(!job.optString("phase").equals("PROVIDER_VERIFIED"))changes.put("phase","FAILED");
            step(job,changes);
        }catch(Exception reportingFailure){failure.addSuppressed(reportingFailure);}
    }
    private CapturedJob captureLocked(String backupId)throws Exception {
        currentContext();JSONObject job=jobs.load(backupId);File root=root(job);JSONObject capture=job.optJSONObject("capture"),metadata=null;
        if(capture!=null){File info=child(root,capture.getString("metadataFile"));if(capture.has("metadataSha256")&&!info.isFile())throw new IOException("BACKUP_CAPTURE_METADATA_MISSING");if(info.isFile()){
            String actual=EncryptedBackupArchive.sha256(info);if(capture.has("metadataSha256")&&!capture.getString("metadataSha256").equals(actual))throw new IOException("BACKUP_CAPTURE_METADATA_CHANGED");
            metadata=readJson(info);requireIdentity(metadata,job);LocalBackupDataset.inspectStaged(child(root,capture.getString("directory")),metadata);
            capture.put("metadataSha256",actual);JSONObject changes=new JSONObject().put("capture",capture);if(job.optString("phase").equals("FAILED"))changes.put("phase","CAPTURED");job=step(job,changes);
        }}
        if(metadata==null){
            String nonce=UUID.randomUUID().toString();capture=new JSONObject().put("directory","capture-"+nonce).put("metadataFile","capture-"+nonce+".json");job=step(job,new JSONObject().put("phase","CAPTURING").put("capture",capture));
            JSONObject browser=new JSONObject(store.ownerValue("backup_job_browser:"+backupId,"{}"));if(!PixelMcpWriteTools.hash(browser).equals(job.getJSONObject("inputs").getString("browserSha256")))throw new IOException("BACKUP_BROWSER_STATE_CHANGED");
            String business=store.ownerValue("backup_business_id","");if(business.isEmpty())throw new IOException("BACKUP_BUSINESS_ID_REQUIRED");
            JSONObject captureIdentity=new JSONObject().put("backupId",backupId).put("installationId",identity.getString("installationId")).put("businessId",business).put("restoreEpoch",identity.getString("restoreEpoch"));if(job.getJSONObject("inputs").has("intent"))captureIdentity.put("requireDatabaseBrowserCheckpoint",true);
            LocalBackupDataset.Captured captured=LocalBackupDataset.capture(context,store,child(root,capture.getString("directory")),captureIdentity,browser);metadata=captured.metadata;
            File info=child(root,capture.getString("metadataFile"));writeJson(info,metadata);capture.put("metadataSha256",EncryptedBackupArchive.sha256(info));job=step(job,new JSONObject().put("phase","CAPTURED").put("capture",capture));
        }
        return new CapturedJob(job,root,capture,metadata);
    }
    private static final class CapturedJob {
        final JSONObject job,capture,metadata;final File root;CapturedJob(JSONObject job,File root,JSONObject capture,JSONObject metadata){this.job=job;this.root=root;this.capture=capture;this.metadata=metadata;}
    }
    LocalBackupDataset.Captured captureForPortable(String backupId)throws Exception {
        if(store.getWritableDatabase().inTransaction())throw new IOException("BACKUP_PREPARE_REQUIRES_OUTSIDE_TRANSACTION");if(!LOCAL_RUNNING.tryLock())throw new IOException("BACKUP_RUNNER_BUSY");
        try{beginAttempt(backupId);CapturedJob captured=captureLocked(backupId);return new LocalBackupDataset.Captured(child(captured.root,captured.capture.getString("directory")),captured.metadata);}catch(Exception failure){recordFailure(backupId,failure);throw failure;}finally{LOCAL_RUNNING.unlock();}
    }
    private JSONObject prepareLocked(String backupId)throws Exception {
        CapturedJob captured=captureLocked(backupId);JSONObject job=captured.job,capture=captured.capture,metadata=captured.metadata;File root=captured.root;
        JSONObject archive=job.optJSONObject("archive");File sealed=archive==null?null:child(root,archive.getString("file"));
        if(archive!=null&&archive.has("sha256")&&!sealed.isFile())throw new IOException("BACKUP_SEALED_ARCHIVE_MISSING");
        if(sealed!=null&&sealed.isFile()&&archive.has("sha256")&&!archive.getString("sha256").equals(EncryptedBackupArchive.sha256(sealed)))throw new IOException("BACKUP_SEALED_ARCHIVE_CHANGED");
        if(sealed==null||!sealed.isFile()||!archive.has("sha256")){
            // A partial, uncommitted attempt never overwrites a previously valid generation.
            archive=new JSONObject().put("file","sealed-"+UUID.randomUUID()+".zip").put("archiveFormat","PLAIN_ZIP");job=step(job,new JSONObject().put("phase","SEALING").put("archive",archive));sealed=child(root,archive.getString("file"));
            try(FileOutputStream out=new FileOutputStream(sealed)){
                EncryptedBackupArchive.writePlain(new FilterOutputStream(BackupStorageBudget.guard(out,root)){@Override public void close()throws IOException{flush();}},metadata,BackupFilePins.sources(child(root,capture.getString("directory"))));out.getFD().sync();
            }
            archive.put("sha256",EncryptedBackupArchive.sha256(sealed)).put("size",sealed.length()).put("capturedAt",metadata.getString("capturedAt"));job=step(job,new JSONObject().put("phase","SEALED_LOCAL").put("archive",archive));
        }
        if(!archive.has("localVerifiedAt")){
            BackupStorageBudget.require(root,0);
            try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyArchive(new FileInputStream(sealed),archive.optString("archiveFormat").equals("PLAIN_ZIP")?"":new BackupRecoveryKeyStore(context).read(),child(root,"verify-"+UUID.randomUUID()),root.getUsableSpace()-BackupStorageBudget.BUSINESS_RESERVE_BYTES,PixelLocalStore.SCHEMA_VERSION)){
                requireIdentity(verified.manifest,job);LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);
                if(!verified.manifest.getJSONObject("database").getString("sha256").equals(metadata.getJSONObject("database").getString("sha256")))throw new IOException("BACKUP_CAPTURE_ARCHIVE_MISMATCH");
                if(metadata.has("browser_media_inventory")){if(metadata.optInt("browser_media_format_version")!=1||verified.manifest.optInt("browser_media_format_version")!=1||!PixelMcpWriteTools.hash(metadata.getJSONObject("browser_media_inventory")).equals(PixelMcpWriteTools.hash(verified.manifest.getJSONObject("browser_media_inventory"))))throw new IOException("BACKUP_BROWSER_MEDIA_INVENTORY_MISMATCH");archive.put("mediaCoverage","VERIFIED_NATIVE_MIRROR").put("mediaFormatVersion",1).put("mediaInventorySha256",PixelMcpWriteTools.hash(verified.manifest.getJSONObject("browser_media_inventory"))).put("captureMetadataSha256",capture.getString("metadataSha256"));}
            }
            archive.put("localVerifiedAt",java.time.Instant.now().toString());job=step(job,new JSONObject().put("phase","LOCAL_VERIFIED").put("archive",archive));
        }
        if(!job.optString("phase").equals("PROVIDER_VERIFIED"))job=step(job,new JSONObject().put("phase","LOCAL_VERIFIED"));
        return job;
    }
    File verifiedArchive(String backupId)throws Exception {
        currentContext();JSONObject job=jobs.load(backupId),archive=job.optJSONObject("archive");if(archive==null||!archive.has("localVerifiedAt"))throw new IOException("BACKUP_LOCAL_VERIFICATION_REQUIRED");File sealed=child(root(job),archive.getString("file"));
        if(!sealed.isFile()||sealed.length()!=archive.getLong("size")||!EncryptedBackupArchive.sha256(sealed).equals(archive.getString("sha256")))throw new IOException("BACKUP_SEALED_ARCHIVE_CHANGED");metadata(backupId);return sealed;
    }
    JSONObject metadata(String backupId)throws Exception {
        currentContext();JSONObject job=jobs.load(backupId),capture=job.getJSONObject("capture");File info=child(root(job),capture.getString("metadataFile"));if(!info.isFile()||!EncryptedBackupArchive.sha256(info).equals(capture.getString("metadataSha256")))throw new IOException("BACKUP_CAPTURE_METADATA_CHANGED");JSONObject metadata=readJson(info);requireIdentity(metadata,job);return metadata;
    }
    /** Native SAF readback proof. Provider readback and independently external storage remain distinct. */
    JSONObject recordProviderProof(String backupId,JSONObject proof)throws Exception {
        if(store.getWritableDatabase().inTransaction())throw new IOException("BACKUP_PREPARE_REQUIRES_OUTSIDE_TRANSACTION");
        verifiedArchive(backupId);JSONObject supplied=new JSONObject(proof.toString());
        if(!backupId.equals(supplied.optString("backup_id"))||!"DOCUMENT_PROVIDER_READBACK".equals(supplied.optString("verification_scope"))||supplied.optBoolean("cloud_confirmed"))throw new IOException("BACKUP_PROVIDER_PROOF_INVALID");
        String location=supplied.optString("destination_location"),verified=supplied.optString("verified_at");
        if(!Arrays.asList("SAME_DEVICE","REMOVABLE_STORAGE_VOLUME","DOCUMENT_PROVIDER_EXTERNALITY_UNCONFIRMED","UNKNOWN").contains(location)||supplied.optBoolean("external_storage_verified")!=location.equals("REMOVABLE_STORAGE_VOLUME"))throw new IOException("BACKUP_PROVIDER_PROOF_INVALID");
        java.time.Instant checked;try{checked=java.time.Instant.parse(verified);}catch(Exception invalid){throw new IOException("BACKUP_PROVIDER_PROOF_INVALID");}if(checked.isAfter(java.time.Instant.now().plusSeconds(120)))throw new IOException("BACKUP_PROVIDER_PROOF_INVALID");
        for(int attempt=0;attempt<4;attempt++){
            currentContext();JSONObject job=jobs.load(backupId),archive=job.getJSONObject("archive");
            if(!archive.getString("sha256").equals(supplied.optString("archive_sha256"))||archive.getLong("size")!=supplied.optLong("byte_size",-1)||checked.isBefore(java.time.Instant.parse(archive.getString("localVerifiedAt"))))throw new IOException("BACKUP_PROVIDER_PROOF_INVALID");
            JSONObject safe=new JSONObject().put("formatVersion",1).put("backup_id",backupId).put("archive_sha256",archive.getString("sha256")).put("byte_size",archive.getLong("size")).put("verified_at",checked.toString()).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("destination_location",location).put("external_storage_verified",location.equals("REMOVABLE_STORAGE_VOLUME")).put("cloud_confirmed",false);
            JSONObject prior=job.optJSONObject("provider");if(prior!=null&&prior.optBoolean("external_storage_verified")&&archive.getString("sha256").equals(prior.optString("archive_sha256"))&&archive.getLong("size")==prior.optLong("byte_size",-1)&&backupId.equals(prior.optString("backup_id"))){
                if(!safe.getBoolean("external_storage_verified")||java.time.Instant.parse(prior.getString("verified_at")).isAfter(checked))return job;
            }
            JSONObject changes=new JSONObject().put("provider",safe);if(job.getJSONObject("destination").getString("kind").equals("saf"))changes.put("phase","PROVIDER_VERIFIED");
            try{return step(job,changes);}catch(IOException conflict){if(!"BACKUP_JOB_WRITE_CONFLICT".equals(conflict.getMessage())||attempt==3)throw conflict;}
        }
        throw new IOException("BACKUP_JOB_WRITE_CONFLICT");
    }
    private JSONObject step(JSONObject job,JSONObject changes)throws Exception {currentContext();return jobs.update(job.getString("backupId"),job.getString("revision"),changes);}
    private void currentContext()throws Exception {JSONObject now=guard.current();if(!now.getString("installationId").equals(identity.getString("installationId"))||!now.getString("restoreEpoch").equals(identity.getString("restoreEpoch")))throw new IOException("BACKUP_RESTORE_CONTEXT_CHANGED");}
    private void requireIdentity(JSONObject metadata,JSONObject job)throws Exception {if(!metadata.getString("backupId").equals(job.getString("backupId"))||!metadata.getString("businessId").equals(job.getJSONObject("inputs").getString("businessId"))||!metadata.getString("installationId").equals(identity.getString("installationId"))||!metadata.getString("restoreEpoch").equals(identity.getString("restoreEpoch")))throw new IOException("BACKUP_CAPTURE_IDENTITY_MISMATCH");}
    private File root(JSONObject job)throws Exception {
        File parent=new File(context.getNoBackupFilesDir(),"full-backup-work");if(Files.isSymbolicLink(parent.toPath()))throw new IOException("BACKUP_WORK_LINK_REJECTED");
        File root=child(parent,job.getString("backupId"));if(!root.isDirectory()&&!root.mkdirs())throw new IOException("BACKUP_WORK_STORAGE_UNAVAILABLE");
        File marker=child(root,"owner.json");if(!marker.exists()){
            String[] files=root.list();if(files==null||files.length!=0)throw new IOException("BACKUP_WORK_OWNERSHIP_UNCONFIRMED");writeJson(marker,new JSONObject().put("requestFingerprint",job.getString("requestFingerprint")));
        }
        if(!readJson(marker).getString("requestFingerprint").equals(job.getString("requestFingerprint")))throw new IOException("BACKUP_WORK_OWNERSHIP_MISMATCH");return root;
    }
    private static File child(File root,String name)throws Exception {if(name==null||!name.matches("[A-Za-z0-9_.-]{1,160}")||name.equals(".")||name.equals(".."))throw new IOException("BACKUP_WORK_PATH_REJECTED");File value=new File(root,name);if(Files.isSymbolicLink(value.toPath())||!value.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw new IOException("BACKUP_WORK_PATH_REJECTED");return value;}
    private static JSONObject readJson(File file)throws Exception {try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(n>EncryptedBackupArchive.MAX_MANIFEST_BYTES-out.size())throw new IOException("BACKUP_METADATA_TOO_LARGE");out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}
    private static void writeJson(File file,JSONObject value)throws Exception {AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);out=null;}catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}}
}
