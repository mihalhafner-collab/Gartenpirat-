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

/** Local capture/seal/reference verification and optional backup upload share one use case. */
final class BackupApplicationService {
    interface Tokens {String forAccount(String account)throws Exception;}
    private static final ReentrantLock RUNNING=new ReentrantLock();
    private final Context context;private final PixelLocalStore store;private final BackupDeviceGuard guard;private final JSONObject identity;private final BackupJobRepository jobs;private final DriveRestClient.Wire wire;private final Tokens tokens;
    BackupApplicationService(Context context,PixelLocalStore store)throws Exception {this(context,store,new DriveRestClient.HttpWire());}
    BackupApplicationService(Context context,PixelLocalStore store,DriveRestClient.Wire wire)throws Exception {this(context,store,wire,account->DriveAccount.token(context,account,null));}
    BackupApplicationService(Context context,PixelLocalStore store,DriveRestClient.Wire wire,Tokens tokens)throws Exception {
        this.context=context;this.store=store;this.wire=wire;this.tokens=tokens;guard=new BackupDeviceGuard(context);identity=guard.current();jobs=new BackupJobRepository(store,identity.getString("installationId"),identity.getString("restoreEpoch"));
    }
    JSONObject create(String requestId,String backupId,JSONObject destination,JSONObject browser)throws Exception {
        currentContext();if(browser==null||browser.optInt("formatVersion")!=1||browser.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_REQUIRED");
        if(!destination.getString("kind").equals("drive")||!DriveRestClient.validId(destination.optString("folderId"))||destination.optString("account").trim().isEmpty())throw new IOException("BACKUP_DRIVE_DESTINATION_REQUIRED");
        byte[] browserBytes=browser.toString().getBytes(StandardCharsets.UTF_8);if(browserBytes.length>16*1024*1024)throw new IOException("BACKUP_BROWSER_STATE_TOO_LARGE");
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            String business=store.ownerValue("backup_business_id","");if(business.isEmpty()){business=UUID.randomUUID().toString();store.putOwnerValue("backup_business_id",business);}
            JSONObject inputs=new JSONObject().put("browserSha256",PixelMcpWriteTools.hash(browser)).put("businessId",business);JSONObject job=jobs.create(requestId,backupId,destination,inputs);
            if(!job.getBoolean("replayed")){
                store.putOwnerValue("backup_job_browser:"+backupId,browser.toString());
            }
            db.setTransactionSuccessful();return job;
        }finally{db.endTransaction();}
    }
    JSONObject status(String backupId)throws Exception {currentContext();return jobs.load(backupId);}
    JSONArray inventory()throws Exception {currentContext();return jobs.inventory();}
    JSONObject prepareLocal(String backupId)throws Exception {
        if(!RUNNING.tryLock())return status(backupId).put("runnerBusy",true);
        try{return prepareLocked(backupId);}finally{RUNNING.unlock();}
    }
    private JSONObject prepareLocked(String backupId)throws Exception {
        currentContext();String recovery=new BackupRecoveryKeyStore(context).read();JSONObject job=jobs.load(backupId);File root=root(job);JSONObject capture=job.optJSONObject("capture"),metadata=null;
        if(capture!=null){File info=child(root,capture.getString("metadataFile"));if(info.isFile()){
            String actual=EncryptedBackupArchive.sha256(info);if(capture.has("metadataSha256")&&!capture.getString("metadataSha256").equals(actual))throw new IOException("BACKUP_CAPTURE_METADATA_CHANGED");
            metadata=readJson(info);requireIdentity(metadata,job);LocalBackupDataset.inspectStaged(child(root,capture.getString("directory")),metadata);
            capture.put("metadataSha256",actual);job=step(job,new JSONObject().put("capture",capture));
        }}
        if(metadata==null){
            String nonce=UUID.randomUUID().toString();capture=new JSONObject().put("directory","capture-"+nonce).put("metadataFile","capture-"+nonce+".json");job=step(job,new JSONObject().put("phase","CAPTURING").put("capture",capture));
            JSONObject browser=new JSONObject(store.ownerValue("backup_job_browser:"+backupId,"{}"));if(!PixelMcpWriteTools.hash(browser).equals(job.getJSONObject("inputs").getString("browserSha256")))throw new IOException("BACKUP_BROWSER_STATE_CHANGED");
            String business=store.ownerValue("backup_business_id","");if(business.isEmpty())throw new IOException("BACKUP_BUSINESS_ID_REQUIRED");
            LocalBackupDataset.Captured captured=LocalBackupDataset.capture(context,store,child(root,capture.getString("directory")),new JSONObject().put("backupId",backupId).put("installationId",identity.getString("installationId")).put("businessId",business).put("restoreEpoch",identity.getString("restoreEpoch")),browser);metadata=captured.metadata;
            File info=child(root,capture.getString("metadataFile"));writeJson(info,metadata);capture.put("metadataSha256",EncryptedBackupArchive.sha256(info));job=step(job,new JSONObject().put("phase","CAPTURED").put("capture",capture));
        }
        JSONObject archive=job.optJSONObject("archive");File sealed=archive==null?null:child(root,archive.getString("file"));
        if(archive!=null&&archive.has("sha256")&&!sealed.isFile())throw new IOException("BACKUP_SEALED_ARCHIVE_MISSING");
        if(sealed!=null&&sealed.isFile()&&archive.has("sha256")&&!archive.getString("sha256").equals(EncryptedBackupArchive.sha256(sealed)))throw new IOException("BACKUP_SEALED_ARCHIVE_CHANGED");
        if(sealed==null||!sealed.isFile()||!archive.has("sha256")){
            // A partial, uncommitted attempt never overwrites a previously valid generation.
            archive=new JSONObject().put("file","sealed-"+UUID.randomUUID()+".hgb");job=step(job,new JSONObject().put("phase","SEALING").put("archive",archive));sealed=child(root,archive.getString("file"));
            try(FileOutputStream out=new FileOutputStream(sealed)){
                EncryptedBackupArchive.write(new FilterOutputStream(out){@Override public void close()throws IOException{flush();}},recovery,metadata,BackupFilePins.sources(child(root,capture.getString("directory"))));out.getFD().sync();
            }
            archive.put("sha256",EncryptedBackupArchive.sha256(sealed)).put("size",sealed.length()).put("capturedAt",metadata.getString("capturedAt"));job=step(job,new JSONObject().put("phase","SEALED_LOCAL").put("archive",archive));
        }
        if(!archive.has("localVerifiedAt")){
            try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(new FileInputStream(sealed),recovery,child(root,"verify-"+UUID.randomUUID()),root.getUsableSpace(),PixelLocalStore.SCHEMA_VERSION)){
                requireIdentity(verified.manifest,job);LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);
                if(!verified.manifest.getJSONObject("database").getString("sha256").equals(metadata.getJSONObject("database").getString("sha256")))throw new IOException("BACKUP_CAPTURE_ARCHIVE_MISMATCH");
            }
            archive.put("localVerifiedAt",java.time.Instant.now().toString());job=step(job,new JSONObject().put("phase","LOCAL_VERIFIED").put("archive",archive));
        }
        return job;
    }
    JSONObject uploadToDrive(String backupId)throws Exception {
        if(!RUNNING.tryLock())return status(backupId).put("runnerBusy",true);
        try{
            JSONObject job=prepareLocked(backupId),destination=job.getJSONObject("destination"),archive=job.getJSONObject("archive");currentContext();
            DriveRestClient client=new DriveRestClient(destination.getString("folderId"),()->tokens.forAccount(destination.getString("account")),wire,new DriveRestClient.Checkpoints(){public String get(String key){return store.ownerValue(key,"");}public void put(String key,String value){store.putOwnerValue(key,value);}});
            DriveBackupUpload.Checkpoint saved=jobs.uploadCheckpoint(backupId);DriveBackupUpload.Checkpoint guarded=new DriveBackupUpload.Checkpoint(){public JSONObject read()throws Exception{currentContext();return saved.read();}public void save(JSONObject state)throws Exception{currentContext();saved.save(state);}};
            new DriveBackupUpload(client).upload(child(root(job),archive.getString("file")),backupId,archive.getString("sha256"),guarded);return status(backupId);
        }finally{RUNNING.unlock();}
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
