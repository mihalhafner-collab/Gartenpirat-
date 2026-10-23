package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** Runs one resumable backup intent; never requests account consent or operational Drive sync. */
final class BackupAutomation {
    private static final ReentrantLock RUNNING=new ReentrantLock();
    private final Context context;private final PixelLocalStore store;private final BackupApplicationService service;private final BackupSettingsRepository settings;private final java.util.function.Supplier<String> ids;
    BackupAutomation(Context context,PixelLocalStore store)throws Exception {this(context,store,new BackupApplicationService(context,store));}
    BackupAutomation(Context context,PixelLocalStore store,BackupApplicationService service)throws Exception {this(context,store,service,()->UUID.randomUUID().toString());}
    BackupAutomation(Context context,PixelLocalStore store,BackupApplicationService service,java.util.function.Supplier<String> ids)throws Exception {this.context=context;this.store=store;this.service=service;this.ids=ids;settings=new BackupSettingsRepository(context,store);}
    JSONObject decision(long now)throws Exception {
        JSONArray jobs=new JSONArray(),inventory=service.inventory();for(int i=0;i<inventory.length();i++){
            JSONObject row=inventory.getJSONObject(i);
            if(row.getBoolean("sameInstallation")&&row.getBoolean("sameRestoreEpoch")&&row.optInt("formatVersion")!=1)return new JSONObject().put("action","JOB_REVIEW").put("backupId",row.getString("backupId")).put("nextAttemptAt",0).put("exactExecutionGuaranteed",false);
            if(row.getBoolean("resumeContextMatches"))jobs.put(service.status(row.getString("backupId")).put("contextMatches",true));
        }return BackupScheduleRules.decide(settings.read(),jobs,now);
    }
    JSONObject run(long now)throws Exception {
        if(!RUNNING.tryLock())return new JSONObject().put("action","BUSY");
        try{
            JSONObject decision=decision(now);String action=decision.getString("action");if(!action.equals("CREATE")&&!action.equals("RESUME"))return decision;
            JSONObject config=settings.read();String backupId;
            if(action.equals("CREATE")){
                JSONObject checkpoint=settings.browser(),browser=checkpoint.getJSONObject("browser").put("checkpointCapturedAt",checkpoint.getString("capturedAt"));backupId=ids.get();service.create(backupId,backupId,new JSONObject().put("kind","drive").put("account",config.getString("account")).put("folderId",config.getString("folderId")),browser);
            }else backupId=decision.getString("backupId");
            JSONObject job=service.status(backupId);BackupJobRepository jobs=repository();int attempt=job.optInt("attempts")+1;jobs.update(backupId,job.getString("revision"),new JSONObject().put("attempts",attempt).put("lastAttemptAt",java.time.Instant.ofEpochMilli(now).toString()).put("nextAttemptAt",0));
            try{
                service.prepareLocal(backupId);if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                JSONObject current=settings.read();if(!current.optBoolean("enabled")||!current.optBoolean("contextMatches")||!current.getString("revision").equals(config.getString("revision")))throw new IOException("BACKUP_SETTINGS_CHANGED");
                JSONObject completed=service.uploadToDrive(backupId);return new JSONObject().put("action",completed.optBoolean("runnerBusy")?"BUSY":"COMPLETED").put("backupId",backupId).put("phase",completed.getString("phase"));
            }catch(Exception failure){
                if(Thread.currentThread().isInterrupted()||failure instanceof java.io.InterruptedIOException)throw failure;
                JSONObject latest=service.status(backupId);String code=safeCode(failure);boolean needs=code.startsWith("BACKUP_")||failure instanceof SecurityException||failure instanceof JSONException||failure instanceof DriveRestClient.Failure&&((DriveRestClient.Failure)failure).userAction;
                long next=needs?Long.MAX_VALUE:BackupScheduleRules.retryAt(Math.max(now,System.currentTimeMillis()),attempt,failure instanceof DriveRestClient.Failure?((DriveRestClient.Failure)failure).retryAfter:0);
                jobs.update(backupId,latest.getString("revision"),new JSONObject().put("phase",next==Long.MAX_VALUE?"NEEDS_USER_ACTION":"RETRY_PENDING").put("nextAttemptAt",next).put("lastErrorCode",code));
                return new JSONObject().put("action",next==Long.MAX_VALUE?"JOB_REVIEW":"WAIT").put("backupId",backupId).put("nextAttemptAt",next).put("lastErrorCode",code);
            }
        }finally{RUNNING.unlock();}
    }
    private BackupJobRepository repository()throws Exception {JSONObject identity=new BackupDeviceGuard(context).current();return new BackupJobRepository(store,identity.getString("installationId"),identity.getString("restoreEpoch"));}
    private static String safeCode(Exception failure){String message=failure.getMessage();if(message!=null&&message.matches("BACKUP_[A-Z0-9_]{1,90}"))return message;return SyncFailureRules.failureCode(failure);}
}
