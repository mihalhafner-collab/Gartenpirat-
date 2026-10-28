package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Separate local preparation and optional upload; neither adapter owns business data. */
final class RetiredBackupAutomation {
    private static final ReentrantLock LOCAL_RUNNING=new ReentrantLock(),UPLOAD_RUNNING=new ReentrantLock();
    private static final Set<String> COMPLETE=new HashSet<>(Arrays.asList("REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED"));
    private final Context context;private final PixelLocalStore store;private final RetiredLocalBackupCoordinator service;private final RetiredBackupSettingsRepository settings;private final java.util.function.Supplier<String> ids;
    RetiredBackupAutomation(Context context,PixelLocalStore store)throws Exception {this(context,store,new RetiredLocalBackupCoordinator(context,store));}
    RetiredBackupAutomation(Context context,PixelLocalStore store,RetiredLocalBackupCoordinator service)throws Exception {this(context,store,service,()->UUID.randomUUID().toString());}
    RetiredBackupAutomation(Context context,PixelLocalStore store,RetiredLocalBackupCoordinator service,java.util.function.Supplier<String> ids)throws Exception {this.context=context;this.store=store;this.service=service;this.ids=ids;settings=new RetiredBackupSettingsRepository(context,store);}
    static boolean stageRunning(boolean upload){return (upload?UPLOAD_RUNNING:LOCAL_RUNNING).isLocked();}

    JSONObject decision(long now)throws Exception {
        JSONObject config=settings.read();JSONArray rows=currentJobs();boolean anyManual=false;JSONArray candidates=new JSONArray();
        for(int i=0;i<rows.length();i++){
            JSONObject job=rows.getJSONObject(i);
            if(!RetiredFullBackupController.targetMatches(job,config))continue;
            if(!config.optBoolean("enabled")&&!manual(job))continue;
            candidates.put(new JSONObject(job.toString()).put("contextMatches",true));if(manual(job)&&!COMPLETE.contains(job.optString("phase")))anyManual=true;
        }
        if(!ready(config)||!config.optBoolean("enabled")&&!anyManual)return result("DISABLED",null,0);
        JSONObject policy=RetiredBackupScheduleRules.decide(new JSONObject(config.toString()).put("enabled",true),candidates,now);
        if(!config.optBoolean("enabled")&&policy.getString("action").equals("CREATE"))return policy.optBoolean("review_required")?result("JOB_REVIEW",null,0).put("review_jobs",policy.getJSONArray("review_jobs")):result("DISABLED",null,0);
        return policy;
    }

    /** Compatibility entry point used by existing native use-case tests, with the same durable jobs. */
    JSONObject run(long now)throws Exception {
        if(!UPLOAD_RUNNING.tryLock())return result("BUSY",null,now+30000);
        try{
            JSONObject decision=decision(now);String action=decision.getString("action");if(!action.equals("CREATE")&&!action.equals("RESUME"))return decision;
            JSONObject config=settings.read(),job=action.equals("CREATE")?createAutomatic(config):service.status(decision.getString("backupId"));return upload(job,config,now,true);
        }finally{UPLOAD_RUNNING.unlock();}
    }

    /** No network call, account lookup or remote condition may run in this path. */
    JSONObject runLocal(long now)throws Exception {
        if(!LOCAL_RUNNING.tryLock())return result("BUSY",null,now+30000);
        try{
            JSONObject config=settings.read();JSONArray rows=currentJobs();JSONObject invalid=invalid(rows,now);if(invalid!=null)return invalid;if(!ready(config))return result("DISABLED",null,0);
            JSONObject job=select(rows,config,now,false);
            if(job==null){
                JSONObject decision=decision(now);if(decision.getString("action").equals("CREATE"))job=createAutomatic(config);
                else {JSONObject pending=pending(now);if(pending.optBoolean("has_upload"))return result("UPLOAD_READY",null,pending.optLong("next_upload_at"));if(pending.optBoolean("has_local"))return result("WAIT",null,pending.optLong("next_local_at")).put("stage","local");return decision.getString("action").equals("RESUME")?result("IDLE",null,0):decision;}
            }
            String id=job.getString("backupId"),lease="local_"+UUID.randomUUID();int previous=job.optInt("localAttempts");RetiredBackupJobRepository jobs=repository();
            job=jobs.update(id,job.getString("revision"),new JSONObject().put("localAttempts",previous+1).put("lastLocalAttemptAt",Instant.ofEpochMilli(now).toString()).put("runnerAttemptId",lease).put("nextAttemptAt",0));
            try{
                JSONObject prepared=service.prepareLocal(id);
                if(prepared.optBoolean("runnerBusy")){releaseBusy(id,lease,"localAttempts",previous);return result("BUSY",id,now+30000);}
                JSONObject current=service.status(id);if(lease.equals(current.optString("runnerAttemptId")))jobs.update(id,current.getString("revision"),new JSONObject().put("runnerAttemptId","").put("lastErrorCode",""));
                JSONObject pending=pending(now);return result("PREPARED",id,0).put("more_local",pending.optBoolean("has_local")).put("phase",service.status(id).getString("phase"));
            }catch(Exception failure){return failure(id,lease,previous+1,now,failure);}
        }finally{LOCAL_RUNNING.unlock();}
    }

    /** Upload only an already verified immutable local generation. */
    JSONObject runUpload(long now)throws Exception {
        if(!UPLOAD_RUNNING.tryLock())return result("BUSY",null,now+30000);
        try{
            JSONObject config=settings.read();JSONArray rows=currentJobs();JSONObject invalid=invalid(rows,now);if(invalid!=null)return invalid;if(!ready(config))return result("DISABLED",null,0);
            JSONObject job=select(rows,config,now,true);
            if(job==null){JSONObject pending=pending(now);if(pending.optBoolean("has_local"))return result("LOCAL_PENDING",null,pending.optLong("next_local_at"));if(pending.optBoolean("has_upload"))return result("WAIT",null,pending.optLong("next_upload_at")).put("stage","upload");JSONObject limited=limited(rows,config);return limited==null?result("IDLE",null,0):limited;}
            return upload(job,config,now,false);
        }finally{UPLOAD_RUNNING.unlock();}
    }

    /** Explicit user action: prepare and upload exactly this durable job now.
     * No Android scheduler, fresh job, automatic opt-in or changed request ID. */
    JSONObject runManual(String id,long now)throws Exception {return runManual(id,now,stage->{});}
    JSONObject runManual(String id,long now,java.util.function.Consumer<String> progress)throws Exception {
        return runManual(id,now,()->{
            android.net.ConnectivityManager manager=context.getSystemService(android.net.ConnectivityManager.class);
            android.net.Network network=manager==null?null:manager.getActiveNetwork();
            android.net.NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
            String wait=RetiredBackupScheduleRules.networkWait(settings.read(),caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET),manager!=null&&manager.isActiveNetworkMetered());
            if(wait.equals("NETWORK_UNAVAILABLE"))throw new RetiredBackupDriveClient.Failure(wait,"Kein Internetzugang.",0,false);
            if(!wait.isEmpty())throw new IOException(wait);
        },progress);
    }
    JSONObject runManual(String id,long now,RetiredLocalBackupCoordinator.UploadGate network)throws Exception {return runManual(id,now,network,stage->{});}
    private static void progress(java.util.function.Consumer<String> callback,String stage){try{callback.accept(stage);}catch(RuntimeException unavailable){/* Feedback never changes a durable backup outcome. */}}
    JSONObject runManual(String id,long now,RetiredLocalBackupCoordinator.UploadGate network,java.util.function.Consumer<String> progress)throws Exception {
        if(!LOCAL_RUNNING.tryLock())return result("BUSY",id,0);
        boolean uploadLock=false;
        try{
            uploadLock=UPLOAD_RUNNING.tryLock();if(!uploadLock)return result("BUSY",id,0);
            JSONObject config=settings.read(),job=service.status(id);
            if(!ready(config)||!RetiredFullBackupController.targetMatches(job,config))throw new IOException("BACKUP_DESTINATION_CHANGED_REVIEW_REQUIRED");
            if(invalidEntries(new JSONArray().put(job),now)!=null)return result("JOB_REVIEW",id,0);
            if(COMPLETE.contains(job.optString("phase")))return result("COMPLETED",id,0).put("phase",job.getString("phase"));
            if(!active(job)||job.optInt(locallyVerified(job)?"attempts":"localAttempts")>=RetiredBackupScheduleRules.MAX_ATTEMPTS)return result("JOB_REVIEW",id,0).put("lastErrorCode",job.optString("lastErrorCode","BACKUP_RETRY_LIMIT_REVIEW_REQUIRED"));
            if(job.optLong("nextAttemptAt",0)>now)return result("WAIT",id,job.getLong("nextAttemptAt"));
            RetiredBackupJobRepository jobs=repository();
            if(!manual(job))job=jobs.update(id,job.getString("revision"),new JSONObject().put("manualRequested",true));
            if(!locallyVerified(job)){
                progress(progress,"local");
                String lease="local_"+UUID.randomUUID();int previous=job.optInt("localAttempts");
                job=jobs.update(id,job.getString("revision"),new JSONObject().put("localAttempts",previous+1).put("runnerAttemptId",lease).put("lastLocalAttemptAt",Instant.ofEpochMilli(now).toString()));
                try{
                    JSONObject prepared=service.prepareLocal(id);
                    if(prepared.optBoolean("runnerBusy")){releaseBusy(id,lease,"localAttempts",previous);return result("BUSY",id,0);}
                    job=service.status(id);if(lease.equals(job.optString("runnerAttemptId")))job=jobs.update(id,job.getString("revision"),new JSONObject().put("runnerAttemptId","").put("lastErrorCode",""));
                }catch(Exception failure){return failure(id,lease,previous+1,now,failure);}
            }
            progress(progress,"upload");
            try{network.check();}catch(RetiredBackupDriveClient.Failure unavailable){return result("WAIT_NETWORK",id,0).put("lastErrorCode",unavailable.code);}
            catch(IOException preference){if(!"BACKUP_WIFI_REQUIRED".equals(preference.getMessage()))throw preference;return result("WAIT_NETWORK",id,0).put("lastErrorCode","BACKUP_WIFI_REQUIRED");}
            return upload(job,config,System.currentTimeMillis(),false,network);
        }finally{if(uploadLock)UPLOAD_RUNNING.unlock();LOCAL_RUNNING.unlock();}
    }

    JSONObject pending(long now)throws Exception {
        JSONObject config=settings.read();JSONArray rows=currentJobs();JSONObject invalid=invalid(rows,now);long local=Long.MAX_VALUE,upload=Long.MAX_VALUE;
        if(invalid==null&&ready(config))for(int i=0;i<rows.length();i++){
            JSONObject job=rows.getJSONObject(i);if(invalidEntries(new JSONArray().put(job),now)!=null||!allowed(job,config)||!active(job))continue;long at=job.optLong("nextAttemptAt",0);if(at<0||at==Long.MAX_VALUE)continue;
            if(locallyVerified(job)){if(job.optInt("attempts")<RetiredBackupScheduleRules.MAX_ATTEMPTS)upload=Math.min(upload,at);}
            else if(job.optInt("localAttempts")<RetiredBackupScheduleRules.MAX_ATTEMPTS)local=Math.min(local,at);
        }
        return new JSONObject().put("has_local",local!=Long.MAX_VALUE).put("has_upload",upload!=Long.MAX_VALUE).put("next_local_at",local==Long.MAX_VALUE?0:Math.max(now,local)).put("next_upload_at",upload==Long.MAX_VALUE?0:Math.max(now,upload)).put("review_required",invalid!=null||invalidEntries(rows,now)!=null||limited(rows,config)!=null);
    }

    private JSONObject upload(JSONObject job,JSONObject config,long now,boolean prepare)throws Exception {
        return upload(job,config,now,prepare,()->{});
    }
    private JSONObject upload(JSONObject job,JSONObject config,long now,boolean prepare,RetiredLocalBackupCoordinator.UploadGate network)throws Exception {
        String id=job.getString("backupId"),lease="upload_"+UUID.randomUUID();int previous=job.optInt("attempts");if(previous>=RetiredBackupScheduleRules.MAX_ATTEMPTS)return result("JOB_REVIEW",id,0);
        RetiredBackupJobRepository jobs=repository();jobs.update(id,job.getString("revision"),new JSONObject().put("attempts",previous+1).put("lastAttemptAt",Instant.ofEpochMilli(now).toString()).put("runnerAttemptId",lease).put("nextAttemptAt",0));
        try{
            if(prepare){JSONObject prepared=service.prepareLocal(id);if(prepared.optBoolean("runnerBusy")){releaseBusy(id,lease,"attempts",previous);return result("BUSY",id,now+30000);}}
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            RetiredLocalBackupCoordinator.UploadGate gate=()->{
                network.check();
                JSONObject current=service.status(id);if(!lease.equals(current.optString("runnerAttemptId")))throw new IOException("BACKUP_JOB_SUPERSEDED");
                JSONObject selected=settings.read();if(!ready(selected)||!RetiredFullBackupController.targetMatches(current,selected))throw new IOException("BACKUP_DESTINATION_CHANGED_REVIEW_REQUIRED");
                if(!manual(current)&&!selected.optBoolean("enabled"))throw new IOException("BACKUP_AUTOMATIC_PAUSED");
                if(!selected.getString("revision").equals(config.getString("revision")))throw new IOException("BACKUP_SETTINGS_CHANGED");
            };
            JSONObject completed=service.uploadToDrive(id,gate);if(completed.optBoolean("runnerBusy")){releaseBusy(id,lease,"attempts",previous);return result("BUSY",id,now+30000);}
            JSONObject current=service.status(id);if(lease.equals(current.optString("runnerAttemptId")))jobs.update(id,current.getString("revision"),new JSONObject().put("runnerAttemptId","").put("lastErrorCode",""));
            JSONObject pending=pending(now);return result("COMPLETED",id,0).put("phase",completed.getString("phase")).put("more_uploads",pending.optBoolean("has_upload")).put("more_local",pending.optBoolean("has_local"));
        }catch(Exception failure){return failure(id,lease,previous+1,now,failure);}
    }
    private JSONObject failure(String id,String lease,int count,long now,Exception failure)throws Exception {
        if(Thread.currentThread().isInterrupted()||failure instanceof java.io.InterruptedIOException)throw failure;
        JSONObject latest=service.status(id);if(!lease.equals(latest.optString("runnerAttemptId")))return result("SUPERSEDED",id,0);
        String code=safeCode(failure);if(code.equals("BACKUP_JOB_SUPERSEDED"))return result("SUPERSEDED",id,0);
        boolean paused=code.equals("BACKUP_AUTOMATIC_PAUSED")||code.equals("BACKUP_SETTINGS_CHANGED")||code.equals("BACKUP_WIFI_REQUIRED"),transientCapture=Arrays.asList("BACKUP_PREFERENCES_CHANGED_RETRY","BACKUP_BROWSER_CAPTURE_REQUIRED","BACKUP_BROWSER_WRITE_CONFLICT","BACKUP_BROWSER_EDITOR_STATE_MISMATCH","BACKUP_SOURCE_CHANGED").contains(code);boolean needs=!paused&&!transientCapture&&(code.startsWith("BACKUP_")||code.startsWith("RECOVERY_")||failure instanceof SecurityException||failure instanceof JSONException||failure instanceof IllegalArgumentException||failure instanceof RetiredBackupDriveClient.Failure&&((RetiredBackupDriveClient.Failure)failure).userAction);
        long next=paused?0:needs?Long.MAX_VALUE:RetiredBackupScheduleRules.retryAt(Math.max(now,System.currentTimeMillis()),count,failure instanceof RetiredBackupDriveClient.Failure?((RetiredBackupDriveClient.Failure)failure).retryAfter:0);
        repository().update(id,latest.getString("revision"),new JSONObject().put("phase",next==Long.MAX_VALUE?"NEEDS_USER_ACTION":"RETRY_PENDING").put("nextAttemptAt",next).put("lastErrorCode",code).put("runnerAttemptId",""));
        return result(next==Long.MAX_VALUE?"JOB_REVIEW":paused?"PAUSED":"WAIT",id,next).put("lastErrorCode",code);
    }
    private void releaseBusy(String id,String lease,String counter,int previous)throws Exception {JSONObject current=service.status(id);if(lease.equals(current.optString("runnerAttemptId")))repository().update(id,current.getString("revision"),new JSONObject().put(counter,previous).put("runnerAttemptId",""));}
    private JSONObject createAutomatic(JSONObject config)throws Exception {JSONObject checkpoint=settings.browser(),browser=checkpoint.getJSONObject("browser").put("checkpointCapturedAt",checkpoint.getString("capturedAt"));String id=ids.get();return service.createWithIntent(id,id,RetiredFullBackupController.destination(config),browser,"automatic");}
    private JSONArray currentJobs()throws Exception {JSONArray result=new JSONArray(),records=service.records();for(int i=0;i<records.length();i++){JSONObject entry=records.getJSONObject(i);if(entry.getBoolean("sameInstallation")&&entry.getBoolean("sameRestoreEpoch"))result.put(entry.getJSONObject("job"));}return result;}
    private static JSONObject invalid(JSONArray jobs,long now)throws Exception {return now<0?result("CLOCK_REVIEW",null,0):null;}
    private static JSONObject invalidEntries(JSONArray jobs,long now)throws Exception {if(now<0)return result("CLOCK_REVIEW",null,0);for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i);if(job.optInt("formatVersion")!=1||!RetiredBackupJobRepository.validPhase(job.optString("phase"))||!validCounter(job,"attempts")||!validCounter(job,"localAttempts")||!validRetryTime(job))return result("JOB_REVIEW",job.optString("backupId"),0);try{long created=Instant.parse(job.getString("createdAt")).toEpochMilli();if(created>now&&created-now>120000)return result("CLOCK_REVIEW",job.optString("backupId"),0);}catch(Exception invalid){return result("JOB_REVIEW",job.optString("backupId"),0);}}return null;}
    private static boolean validCounter(JSONObject job,String field){Object value=job.opt(field);return value==null||(value instanceof Integer||value instanceof Long)&&((Number)value).longValue()>=0&&((Number)value).longValue()<=RetiredBackupScheduleRules.MAX_ATTEMPTS;}
    private static boolean validRetryTime(JSONObject job){Object value=job.opt("nextAttemptAt");return value==null||(value instanceof Integer||value instanceof Long)&&((Number)value).longValue()>=0;}
    private static JSONObject limited(JSONArray rows,JSONObject config)throws Exception {for(int i=0;i<rows.length();i++){JSONObject job=rows.getJSONObject(i);if(allowed(job,config)&&active(job)&&job.optInt(locallyVerified(job)?"attempts":"localAttempts")>=RetiredBackupScheduleRules.MAX_ATTEMPTS)return result("JOB_REVIEW",job.optString("backupId"),0).put("lastErrorCode","BACKUP_RETRY_LIMIT_REVIEW_REQUIRED");}return null;}
    private static JSONObject select(JSONArray jobs,JSONObject config,long now,boolean upload)throws Exception {JSONObject selected=null;for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i);if(invalidEntries(new JSONArray().put(job),now)!=null||!allowed(job,config)||!active(job)||locallyVerified(job)!=upload||job.optLong("nextAttemptAt",0)>now||job.optInt(upload?"attempts":"localAttempts")>=RetiredBackupScheduleRules.MAX_ATTEMPTS)continue;if(selected==null||Instant.parse(job.getString("createdAt")).isBefore(Instant.parse(selected.getString("createdAt"))))selected=job;}return selected;}
    private static boolean ready(JSONObject config){return config.optBoolean("contextMatches")&&config.optBoolean("keyConfigured")&&RetiredFullBackupController.validTarget(config);}
    private static boolean manual(JSONObject job){JSONObject inputs=job.optJSONObject("inputs");return job.optBoolean("manualRequested")||inputs!=null&&inputs.optString("intent").equals("manual");}
    private static boolean allowed(JSONObject job,JSONObject config){return RetiredFullBackupController.targetMatches(job,config)&&(config.optBoolean("enabled")||manual(job));}
    private static boolean locallyVerified(JSONObject job){JSONObject archive=job.optJSONObject("archive");return archive!=null&&archive.has("localVerifiedAt");}
    private static boolean active(JSONObject job){return !COMPLETE.contains(job.optString("phase"))&&!Arrays.asList("NEEDS_USER_ACTION","FAILED").contains(job.optString("phase"))&&RetiredBackupJobRepository.validPhase(job.optString("phase"));}
    private RetiredBackupJobRepository repository()throws Exception {JSONObject identity=new BackupDeviceGuard(context).current();return new RetiredBackupJobRepository(store,identity.getString("installationId"),identity.getString("restoreEpoch"));}
    private static JSONObject result(String action,String id,long next)throws Exception{return new JSONObject().put("action",action).put("backupId",id==null?JSONObject.NULL:id).put("nextAttemptAt",next).put("exactExecutionGuaranteed",false);}
    static String safeCode(Exception failure){String message=failure.getMessage();if(message!=null&&message.matches("(?:BACKUP|RECOVERY)_[A-Z0-9_]{1,90}"))return message;return failure instanceof RetiredBackupDriveClient.Failure?((RetiredBackupDriveClient.Failure)failure).code:SyncFailureRules.failureCode(failure);}
}
