package at.hausgarten.service;

import org.json.*;
import java.time.Instant;
import java.util.*;

/** Daily backup intent and bounded retries. Android still decides actual execution time. */
final class BackupScheduleRules {
    static final long INTERVAL_MS=24L*60*60*1000;
    static final int MAX_ATTEMPTS=8;
    private static final Set<String> COMPLETE=new HashSet<>(Arrays.asList("REMOTE_VERIFIED","CANCELLED"));
    private static final Set<String> ACTIVE=new HashSet<>(Arrays.asList("CAPTURE_PENDING","CAPTURING","CAPTURED","SEALING","SEALED_LOCAL","LOCAL_VERIFIED","UPLOAD_PENDING","UPLOADING","REMOTE_VERIFY_PENDING","RETRY_PENDING"));
    interface ManualRequest {JSONObject request()throws Exception;}
    interface Scheduler {boolean schedule()throws Exception;}
    static String manualStartAction(String request,String requestedBackup,JSONArray jobs,String activeBackup)throws Exception {
        String selected=request!=null&&!request.isEmpty()?requestedBackup:activeBackup;
        if(selected!=null&&!selected.isEmpty()&&jobs!=null)for(int i=0;i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i);if(job==null||!selected.equals(job.optString("backup_id")))continue;
            if(Arrays.asList("REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED","FAILED","NEEDS_USER_ACTION","FORMAT_REVIEW_REQUIRED").contains(job.optString("phase")))return "STATUS";
        }
        // An unresolved request must be read back with the original ID/arguments.
        // The controller's durable receipt prevents a second generation.
        return request!=null&&!request.isEmpty()?"REQUEST":activeBackup!=null&&!activeBackup.isEmpty()?"RESUME":"REQUEST";
    }
    static boolean manualRetryAvailable(JSONObject job,long now){
        if(!job.optBoolean("retry_available")||!job.optBoolean("context_matches")||!job.optBoolean("target_matches"))return false;
        String phase=job.optString("phase");
        if(!ACTIVE.contains(phase)&&!Arrays.asList("NEEDS_USER_ACTION","FAILED").contains(phase))return false;
        return Arrays.asList("NEEDS_USER_ACTION","FAILED","RETRY_PENDING").contains(phase)||job.optLong("next_attempt_at",0)>now;
    }
    static String scheduleAttempt(Scheduler scheduler){
        try{return scheduler.schedule()?"":"BACKUP_SCHEDULER_UNAVAILABLE";}
        catch(Exception unavailable){return "BACKUP_SCHEDULER_UNAVAILABLE";}
    }
    static String networkWait(JSONObject settings,boolean connected,boolean metered){
        return !connected?"NETWORK_UNAVAILABLE":settings.optBoolean("unmeteredOnly",false)&&metered?"BACKUP_WIFI_REQUIRED":"";
    }
    interface ManualWork {JSONObject run(String backupId)throws Exception;}
    static JSONObject dispatchManual(ManualRequest request,ManualWork work)throws Exception {
        JSONObject accepted=request.request();
        String id=accepted==null?"":accepted.optString("backup_id");
        if(accepted==null||!accepted.optBoolean("accepted_locally")||!id.matches("[A-Za-z0-9_-]{8,100}"))throw new java.io.IOException("BACKUP_CONTROL_RESULT_UNKNOWN");
        JSONObject result=new JSONObject(accepted.toString());
        try{
            JSONObject executed=work.run(id);
            if(executed==null||!id.equals(executed.optString("backupId")))return result.put("execution_error_code","BACKUP_RUN_RESULT_UNKNOWN");
            return result.put("foreground_result",executed);
        }catch(Exception failure){
            boolean interrupted=failure instanceof java.io.InterruptedIOException||failure instanceof InterruptedException||Thread.currentThread().isInterrupted();
            if(interrupted)Thread.currentThread().interrupt();
            // Preserve the committed identity even if foreground execution stops.
            return result.put("execution_error_code",interrupted?"BACKUP_RUN_INTERRUPTED":"BACKUP_OPERATION_FAILED");
        }
    }
    static JSONObject decide(JSONObject settings,JSONArray currentJobs,long now)throws Exception {
        if(!settings.optBoolean("enabled")||!settings.optBoolean("contextMatches")||!settings.optBoolean("keyConfigured"))return decision("DISABLED",null,0);
        if(now<0)return decision("CLOCK_REVIEW",null,0);
        long latest=-1,nextRetry=Long.MAX_VALUE,localWait=Long.MAX_VALUE;JSONObject localReady=null,uploadReady=null;JSONArray reviews=new JSONArray();
        for(int i=0;i<currentJobs.length();i++){
            JSONObject job=currentJobs.getJSONObject(i),target=job.optJSONObject("destination");if(target==null||!target.optString("kind").equals("drive")||!target.optString("folderId").equals(settings.optString("folderId"))||!target.optString("account").equals(settings.optString("account"))||!job.optBoolean("contextMatches",false))continue;
            String id=job.optString("backupId"),phase=job.optString("phase");long created;
            try{created=Instant.parse(job.getString("createdAt")).toEpochMilli();if(job.optInt("formatVersion")!=1||created>now+120000||!ACTIVE.contains(phase)&&!COMPLETE.contains(phase)&&!Arrays.asList("PROVIDER_VERIFIED","NEEDS_USER_ACTION","FAILED").contains(phase))throw new IllegalArgumentException();}catch(Exception invalid){reviews.put(id);continue;}
            JSONObject archive=job.optJSONObject("archive");boolean verified=archive!=null&&archive.has("localVerifiedAt");
            if(verified)try{long at=Instant.parse(archive.getString("capturedAt")).toEpochMilli();Instant.parse(archive.getString("localVerifiedAt"));if(at>now+120000)throw new IllegalArgumentException();latest=Math.max(latest,at);}catch(Exception invalid){reviews.put(id);continue;}
            if(!verified&&Arrays.asList("UPLOADING","UPLOAD_PENDING","REMOTE_VERIFY_PENDING","SEALED_LOCAL","LOCAL_VERIFIED").contains(phase)){reviews.put(id);continue;}
            if(COMPLETE.contains(phase)||phase.equals("PROVIDER_VERIFIED"))continue;
            int attempts=job.optInt(verified?"attempts":"localAttempts",0);long due=job.optLong("nextAttemptAt",0);
            if(attempts<0||attempts>=MAX_ATTEMPTS||due<0||due==Long.MAX_VALUE||phase.equals("NEEDS_USER_ACTION")||phase.equals("FAILED")){reviews.put(id);continue;}
            if(due>now){nextRetry=Math.min(nextRetry,due);if(!verified)localWait=Math.min(localWait,due);continue;}
            if(!verified){if(localReady==null||created<Instant.parse(localReady.getString("createdAt")).toEpochMilli())localReady=job;}
            else if(uploadReady==null||created<Instant.parse(uploadReady.getString("createdAt")).toEpochMilli())uploadReady=job;
        }
        JSONObject result;long due=latest<0?now:latest>Long.MAX_VALUE-INTERVAL_MS?Long.MAX_VALUE:latest+INTERVAL_MS;
        if(localReady!=null)result=decision("RESUME",localReady.getString("backupId"),now);
        else if(now>=due&&localWait==Long.MAX_VALUE)result=decision("CREATE",null,now);
        else if(uploadReady!=null)result=decision("RESUME",uploadReady.getString("backupId"),now);
        else result=decision("WAIT",null,Math.min(due>now?due:localWait,nextRetry));
        return result.put("review_jobs",reviews).put("review_required",reviews.length()>0).put("local_due_at",due);
    }
    static long retryAt(long now,int failures,long providerDelay)throws Exception {
        if(failures<1||failures>=MAX_ATTEMPTS)return Long.MAX_VALUE;
        long delay=Math.min(6L*60*60*1000,Math.max(Math.min(6L*60*60*1000,Math.max(0,providerDelay)),30000L<<(failures-1)));
        return now>Long.MAX_VALUE-delay?Long.MAX_VALUE:now+delay;
    }
    private static JSONObject decision(String action,String id,long due)throws Exception{return new JSONObject().put("action",action).put("backupId",id==null?JSONObject.NULL:id).put("nextAttemptAt",due).put("exactExecutionGuaranteed",false);}
    private BackupScheduleRules(){}
}
