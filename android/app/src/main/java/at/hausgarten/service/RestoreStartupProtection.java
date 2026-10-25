package at.hausgarten.service;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs before providers and store handles exist. Completed exchanges require a fresh process. */
final class RestoreStartupProtection implements BackupRestoreService.Protection {
    private final Context context;
    RestoreStartupProtection(Context context){this.context=context;}
    public AutoCloseable enterExclusiveStartup()throws Exception {return enterMaintenance(context);}
    static AutoCloseable enterMaintenance(Context context)throws Exception {PixelLocalStore.assertNoOpenStores(context);return LocalAuthorityGuard.exclusiveStartup();}
    public BackupRestoreService.ActivePaths activePaths()throws Exception {
        File data=context.getDataDir();if(!context.getFilesDir().getCanonicalFile().equals(new File(data,"files").getCanonicalFile()))throw new IOException("RESTORE_TRUSTED_APPLICATION_PATH_REQUIRED");
        Map<String,File> preferences=new LinkedHashMap<>();for(String group:BackupPreferences.GROUPS)preferences.put(group,new File(data,"shared_prefs/"+group+".xml"));
        return new BackupRestoreService.ActivePaths(context.getDatabasePath("haus-garten-mobile.db"),context.getFilesDir(),context.getCacheDir(),context.getNoBackupFilesDir(),preferences);
    }
    public JSONObject preserveCurrent(String requestId,String incomingRecoveryCode)throws Exception {
        File root=new File(context.getNoBackupFilesDir(),"before-restore/"+PixelMcpWriteTools.hash(requestId));if(!root.isDirectory()&&!root.mkdirs())throw new IOException("RESTORE_CURRENT_BACKUP_STORAGE_REQUIRED");File proofFile=new File(root,"proof.json"),archive=new File(root,"generation.hgb");
        if(proofFile.isFile()){
            JSONObject proof=read(proofFile);if(!archive.isFile()||archive.length()!=proof.getLong("byte_size")||!EncryptedBackupArchive.sha256(archive).equals(proof.getString("archive_sha256")))throw new IOException("RESTORE_CURRENT_BACKUP_CHANGED");
            try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(new FileInputStream(archive),incomingRecoveryCode,new File(root,"recheck-"+UUID.randomUUID()),root.getUsableSpace()-BackupStorageBudget.BUSINESS_RESERVE_BYTES,PixelLocalStore.SCHEMA_VERSION)){LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);}return proof;
        }
        try(PixelLocalStore store=new PixelLocalStore(context)){
            // Empty business collections say nothing about editor drafts or localStorage.
            // A failed/stale capture is never evidence that an empty checkpoint is safe.
            JSONObject browser=requireBrowserCheckpoint(store);
            JSONObject identity=new BackupDeviceGuard(context).current();String business=store.ownerValue("backup_business_id","");if(business.isEmpty()){business=UUID.randomUUID().toString();store.putOwnerValue("backup_business_id",business);}String backupId="before_restore_"+PixelMcpWriteTools.hash(requestId).substring(0,32);
            File capture=new File(root,"capture-"+UUID.randomUUID());LocalBackupDataset.Captured captured=LocalBackupDataset.capture(context,store,capture,new JSONObject().put("backupId",backupId).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch")).put("businessId",business).put("requireDatabaseBrowserCheckpoint",true),browser);
            if(archive.exists())throw new IOException("RESTORE_CURRENT_BACKUP_INCOMPLETE_REVIEW_REQUIRED");try(FileOutputStream out=new FileOutputStream(archive)){EncryptedBackupArchive.write(new FilterOutputStream(BackupStorageBudget.guard(out,root)){@Override public void close()throws IOException{flush();}},incomingRecoveryCode,captured.metadata,captured.sources());out.getFD().sync();}
            try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(new FileInputStream(archive),incomingRecoveryCode,new File(root,"verified-"+UUID.randomUUID()),root.getUsableSpace()-BackupStorageBudget.BUSINESS_RESERVE_BYTES,PixelLocalStore.SCHEMA_VERSION)){LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);}
            JSONObject proof=new JSONObject().put("formatVersion",1).put("verified",true).put("backup_id",backupId).put("archive_sha256",EncryptedBackupArchive.sha256(archive)).put("database_sha256",captured.metadata.getJSONObject("database").getString("sha256")).put("byte_size",archive.length()).put("captured_at",captured.metadata.getString("capturedAt")).put("local_verified_at",java.time.Instant.now().toString()).put("private_archive_ref","before-restore/"+PixelMcpWriteTools.hash(requestId)+"/generation.hgb");write(proofFile,proof);return proof;
        }
    }
    private JSONObject requireBrowserCheckpoint(PixelLocalStore store)throws IOException {
        try{
            JSONObject saved=new BackupSettingsRepository(context,store).browser(),browser=saved.getJSONObject("browser"),storage=browser.getJSONObject("storage");
            for(JSONObject versioned:new JSONObject[]{saved,browser}){Object version=versioned.opt("formatVersion");if(!(version instanceof Number)||!version.toString().equals("1"))throw new IOException("BACKUP_BROWSER_STATE_INVALID");}
            for(Iterator<String> keys=storage.keys();keys.hasNext();)if(!(storage.get(keys.next()) instanceof String))throw new IOException("BACKUP_BROWSER_STORAGE_INVALID");
            if(browser.toString().getBytes(StandardCharsets.UTF_8).length>16*1024*1024||!PixelMcpWriteTools.hash(browser).equals(saved.getString("contentSha256")))throw new IOException("BACKUP_BROWSER_STATE_INVALID");
            if(java.time.Instant.parse(saved.getString("capturedAt")).isAfter(java.time.Instant.now().plusSeconds(120)))throw new IOException("BACKUP_BROWSER_CLOCK_REVIEW_REQUIRED");
            return new JSONObject(browser.toString());
        }catch(Exception unavailable){throw new IOException("RESTORE_CURRENT_BROWSER_CHECKPOINT_REQUIRED");}
    }
    public void blockBeforeActivation(String requestId,JSONObject manifest,JSONObject currentBackupProof)throws Exception {
        if(!currentBackupProof.optBoolean("verified"))throw new IOException("RESTORE_CURRENT_BACKUP_REQUIRED");JSONObject target=manifest.getJSONObject("_activation_context"),identity=new BackupDeviceGuard(context).current();
        if(!identity.getString("installationId").equals(target.getString("installation_id")))throw new IOException("RESTORE_TARGET_CONTEXT_CHANGED");
        if(!identity.getString("restoreEpoch").equals(target.getString("expected_restore_epoch"))&&!requestId.equals(identity.optString("lastRestoreRequest")))throw new IOException("RESTORE_TARGET_CONTEXT_CHANGED");
        new LocalAuthorityGuard(context).blockAfterRestore(requestId,manifest);
    }
    public void afterActivation(String requestId,JSONObject manifest,File database)throws Exception {
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE)){
            if(db.getVersion()<4||db.getVersion()>PixelLocalStore.SCHEMA_VERSION)throw new IOException("RESTORE_DATABASE_VERSION_UNSUPPORTED");db.beginTransaction();try{
                try(Cursor sessions=db.rawQuery("SELECT id FROM work_sessions WHERE state IN ('RUNNING','PAUSED','STOPPED_PENDING_REVIEW')",null)){while(sessions.moveToNext()){String id=sessions.getString(0);android.content.ContentValues row=new android.content.ContentValues();String token=PixelMcpWriteTools.hash(requestId+":"+id);row.put("id","evt_restore_"+token);row.put("session_id",id);row.put("event_type","TIME_REVIEW_REQUIRED");row.put("request_id","restore-review-"+token);row.put("occurred_at",java.time.Instant.now().toString());row.put("payload",new JSONObject().put("reason","RESTORED_SESSION_REVIEW_REQUIRED").put("original_time_values_preserved",true).toString());db.insertWithOnConflict("work_session_events",null,row,SQLiteDatabase.CONFLICT_IGNORE);}}
                try(Cursor row=db.rawQuery("SELECT value FROM owner_state WHERE key='planning_settings'",null)){if(row.moveToFirst()){String original=row.getString(0);put(db,"restored_inactive_planning_settings:"+requestId,original);JSONObject settings=new JSONObject(original);for(String key:new String[]{"calendar_sync_enabled","weather_enabled","read_busy"})settings.put(key,false);settings.put("requires_device_reauthorization",true);put(db,"planning_settings",settings.toString());}}
                BrowserMediaRepository.installRestored(db,requestId,new BackupDeviceGuard(context).current());
                RestoredBrowserCheckpoint.install(db,requestId,manifest,manifest.getJSONObject("_restored_browser_checkpoint"),new BackupDeviceGuard(context).current());
                put(db,"restore_requires_invoice_reconciliation","true");db.setVersion(PixelLocalStore.SCHEMA_VERSION);db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        // Existing device account grants are retained, but no responder writes restored data
        // before the user has reviewed this new business context.
        new PixelTunnelConfig(context).enabled(false);new PixelTunnelConfig(context).writeEnabled(false);
        write(new File(context.getNoBackupFilesDir(),"local-authority/restore-process.json"),new JSONObject().put("pid",Process.myPid()).put("request_id",requestId).put("requires_cold_restart",true));
    }
    static boolean requiresColdRestart(Context context){try{File file=new File(context.getNoBackupFilesDir(),"local-authority/restore-process.json");return file.isFile()&&read(file).getInt("pid")==Process.myPid();}catch(Exception invalid){return true;}}
    private static void put(SQLiteDatabase db,String key,String value){android.content.ContentValues row=new android.content.ContentValues();row.put("key",key);row.put("value",value);db.insertWithOnConflict("owner_state",null,row,SQLiteDatabase.CONFLICT_REPLACE);}
    private static JSONObject read(File file)throws Exception {try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>256*1024)throw new IOException("RESTORE_SAFETY_PROOF_INVALID");out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}
    private static void write(File file,JSONObject value)throws Exception {File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("RESTORE_SAFETY_STORAGE_REQUIRED");AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);out=null;}catch(Exception failure){if(out!=null)atomic.failWrite(out);throw failure;}}
}
