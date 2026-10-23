package at.hausgarten.service;

import org.json.*;
import java.time.Instant;

public final class BackupScheduleRulesTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("backup schedule check "+checks);}
    static JSONObject job(String id,String phase,long at)throws Exception{return new JSONObject().put("formatVersion",1).put("backupId",id).put("phase",phase).put("createdAt",Instant.ofEpochMilli(at).toString()).put("contextMatches",true).put("destination",new JSONObject().put("kind","drive").put("account","qa@example.invalid").put("folderId","qa_folder_123")).put("attempts",0);}
    public static void main(String[] args)throws Exception {
        long now=Instant.parse("2027-05-18T18:00:00Z").toEpochMilli();JSONObject settings=new JSONObject().put("enabled",true).put("contextMatches",true).put("keyConfigured",true).put("account","qa@example.invalid").put("folderId","qa_folder_123");
        yes(BackupScheduleRules.decide(settings,new JSONArray(),now).getString("action").equals("CREATE"));
        for(String key:new String[]{"enabled","contextMatches","keyConfigured"}){JSONObject disabled=new JSONObject(settings.toString()).put(key,false);yes(BackupScheduleRules.decide(disabled,new JSONArray(),now).getString("action").equals("DISABLED"));}
        JSONObject recent=job("qa_recent","REMOTE_VERIFIED",now-3600000).put("archive",new JSONObject().put("capturedAt",Instant.ofEpochMilli(now-3600000).toString()));yes(BackupScheduleRules.decide(settings,new JSONArray().put(recent),now).getString("action").equals("WAIT"));
        JSONObject stale=job("qa_stale","REMOTE_VERIFIED",now-3*BackupScheduleRules.INTERVAL_MS).put("upload",new JSONObject().put("remoteVerifiedAt",Instant.ofEpochMilli(now).toString()));yes(BackupScheduleRules.decide(settings,new JSONArray().put(stale),now).getString("action").equals("CREATE"));
        JSONObject pending=job("qa_pending","UPLOADING",now-1000),older=job("qa_older","SEALED_LOCAL",now-2000);yes(BackupScheduleRules.decide(settings,new JSONArray().put(pending).put(older),now).getString("backupId").equals("qa_older"));
        JSONObject retry=job("qa_retry","UPLOADING",now-1000).put("nextAttemptAt",now+60000);yes(BackupScheduleRules.decide(settings,new JSONArray().put(retry),now).getLong("nextAttemptAt")==now+60000);
        retry.put("attempts",8);yes(BackupScheduleRules.decide(settings,new JSONArray().put(retry),now).getString("action").equals("JOB_REVIEW"));
        JSONObject restored=job("qa_restored","UPLOADING",now-1000).put("contextMatches",false);yes(BackupScheduleRules.decide(settings,new JSONArray().put(restored),now).getString("action").equals("CREATE"));
        JSONObject foreign=job("qa_foreign","UPLOADING",now-1000);foreign.getJSONObject("destination").put("folderId","qa_other_folder");yes(BackupScheduleRules.decide(settings,new JSONArray().put(foreign),now).getString("action").equals("CREATE"));
        JSONObject future=job("qa_future","REMOTE_VERIFIED",now+600000);yes(BackupScheduleRules.decide(settings,new JSONArray().put(future),now).getString("action").equals("CLOCK_REVIEW"));
        yes(BackupScheduleRules.decide(settings,new JSONArray().put(job("qa_newer","UPLOADING",now).put("formatVersion",2)),now).getString("action").equals("JOB_REVIEW"));
        yes(BackupScheduleRules.decide(settings,new JSONArray().put(job("qa_unknown","qa_unsupported",now)),now).getString("action").equals("JOB_REVIEW"));
        yes(BackupScheduleRules.decide(settings,new JSONArray().put(job("qa_provider","PROVIDER_VERIFIED",now)),now).getString("action").equals("JOB_REVIEW"));
        yes(BackupScheduleRules.decide(settings,new JSONArray().put(job("qa_future_pending","CAPTURE_PENDING",now+600000)),now).getString("action").equals("CLOCK_REVIEW"));
        yes(BackupScheduleRules.decide(settings,new JSONArray().put(job("qa_held","UPLOADING",now).put("nextAttemptAt",Long.MAX_VALUE)),now).getString("action").equals("JOB_REVIEW"));
        yes(BackupScheduleRules.retryAt(now,1,0)==now+30000);yes(BackupScheduleRules.retryAt(now,2,0)==now+60000);yes(BackupScheduleRules.retryAt(now,7,3600000)==now+3600000);yes(BackupScheduleRules.retryAt(now,8,0)==Long.MAX_VALUE);yes(BackupScheduleRules.retryAt(now,2,Long.MAX_VALUE)==now+6L*60*60*1000);
        yes(!BackupScheduleRules.decide(settings,new JSONArray(),now).getBoolean("exactExecutionGuaranteed"));
        System.out.println("PASS "+checks+" daily backup/retry/restore-context/data-age/clock/target guards;no scheduler timing or account claim");
    }
}
