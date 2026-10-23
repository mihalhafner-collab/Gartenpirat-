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
    static JSONObject decide(JSONObject settings,JSONArray currentJobs,long now)throws Exception {
        if(!settings.optBoolean("enabled")||!settings.optBoolean("contextMatches")||!settings.optBoolean("keyConfigured"))return decision("DISABLED",null,0);
        if(now<0)return decision("CLOCK_REVIEW",null,0);
        long latest=-1,nextRetry=Long.MAX_VALUE;JSONObject ready=null,blocked=null;
        for(int i=0;i<currentJobs.length();i++){
            JSONObject job=currentJobs.getJSONObject(i),target=job.optJSONObject("destination");if(target==null||!target.optString("kind").equals("drive")||!target.optString("folderId").equals(settings.optString("folderId"))||!target.optString("account").equals(settings.optString("account")))continue;
            if(!job.optBoolean("contextMatches",false))continue;
            if(job.optInt("formatVersion")!=1)return decision("JOB_REVIEW",job.optString("backupId"),0);
            long created;try{created=Instant.parse(job.getString("createdAt")).toEpochMilli();}catch(Exception invalid){return decision("JOB_REVIEW",job.optString("backupId"),0);}
            if(created>now&&created-now>120000)return decision("CLOCK_REVIEW",job.optString("backupId"),0);
            String phase=job.getString("phase");
            if(phase.equals("REMOTE_VERIFIED")){
                JSONObject archive=job.optJSONObject("archive");String captured=archive==null?job.getString("createdAt"):archive.optString("capturedAt",job.getString("createdAt"));
                long at;try{at=Instant.parse(captured).toEpochMilli();}catch(Exception invalid){return decision("JOB_REVIEW",job.optString("backupId"),0);}
                if(at>now&&at-now>120000)return decision("CLOCK_REVIEW",job.getString("backupId"),0);latest=Math.max(latest,at);continue;
            }
            if(COMPLETE.contains(phase))continue;
            if(job.optInt("attempts")<0||job.optInt("attempts")>=MAX_ATTEMPTS||Arrays.asList("NEEDS_USER_ACTION","FAILED").contains(phase)){blocked=job;continue;}
            if(!ACTIVE.contains(phase))return decision("JOB_REVIEW",job.optString("backupId"),0);
            long due=job.optLong("nextAttemptAt",0);if(due<0||due==Long.MAX_VALUE){blocked=job;continue;}if(due>now){nextRetry=Math.min(nextRetry,due);continue;}
            if(ready==null||created<Instant.parse(ready.getString("createdAt")).toEpochMilli())ready=job;
        }
        if(ready!=null)return decision("RESUME",ready.getString("backupId"),now);
        if(blocked!=null)return decision("JOB_REVIEW",blocked.getString("backupId"),0);
        if(nextRetry!=Long.MAX_VALUE)return decision("WAIT",null,nextRetry);
        if(latest<0)return decision("CREATE",null,now);
        long due=latest>Long.MAX_VALUE-INTERVAL_MS?Long.MAX_VALUE:latest+INTERVAL_MS;
        return decision(now>=due?"CREATE":"WAIT",null,due);
    }
    static long retryAt(long now,int failures,long providerDelay)throws Exception {
        if(failures<1||failures>=MAX_ATTEMPTS)return Long.MAX_VALUE;
        long delay=Math.min(6L*60*60*1000,Math.max(Math.min(6L*60*60*1000,Math.max(0,providerDelay)),30000L<<(failures-1)));
        return now>Long.MAX_VALUE-delay?Long.MAX_VALUE:now+delay;
    }
    private static JSONObject decision(String action,String id,long due)throws Exception{return new JSONObject().put("action",action).put("backupId",id==null?JSONObject.NULL:id).put("nextAttemptAt",due).put("exactExecutionGuaranteed",false);}
    private BackupScheduleRules(){}
}
