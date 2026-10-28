package at.hausgarten.service;

import org.json.*;
import java.time.Instant;

public final class BackupScheduleRulesTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("backup schedule check "+checks);}
    static JSONObject job(String id,String phase,long at)throws Exception{return new JSONObject().put("formatVersion",1).put("backupId",id).put("phase",phase).put("createdAt",Instant.ofEpochMilli(at).toString()).put("contextMatches",true).put("destination",new JSONObject().put("kind","drive").put("account","qa@example.invalid").put("folderId","qa_folder_123")).put("attempts",0);}
    public static void main(String[] args)throws Exception {
        long now=Instant.parse("2027-05-18T18:00:00Z").toEpochMilli();JSONObject settings=new JSONObject().put("enabled",true).put("contextMatches",true).put("keyConfigured",true).put("account","qa@example.invalid").put("folderId","qa_folder_123");
        yes(RetiredBackupScheduleRules.decide(settings,new JSONArray(),now).getString("action").equals("CREATE"));
        for(String key:new String[]{"enabled","contextMatches","keyConfigured"}){JSONObject disabled=new JSONObject(settings.toString()).put(key,false);yes(RetiredBackupScheduleRules.decide(disabled,new JSONArray(),now).getString("action").equals("DISABLED"));}
        JSONObject recent=job("qa_recent","REMOTE_VERIFIED",now-3600000).put("archive",new JSONObject().put("capturedAt",Instant.ofEpochMilli(now-3600000).toString()).put("localVerifiedAt",Instant.ofEpochMilli(now-3500000).toString()));yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(recent),now).getString("action").equals("WAIT"));
        JSONObject stale=job("qa_stale","REMOTE_VERIFIED",now-3*RetiredBackupScheduleRules.INTERVAL_MS).put("upload",new JSONObject().put("remoteVerifiedAt",Instant.ofEpochMilli(now).toString()));yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(stale),now).getString("action").equals("CREATE"));
        JSONObject pending=job("qa_pending","UPLOADING",now-1000).put("archive",new JSONObject().put("capturedAt",Instant.ofEpochMilli(now-1000).toString()).put("localVerifiedAt",Instant.ofEpochMilli(now-1000).toString())),older=job("qa_older","SEALED_LOCAL",now-2000).put("archive",new JSONObject().put("capturedAt",Instant.ofEpochMilli(now-2000).toString()).put("localVerifiedAt",Instant.ofEpochMilli(now-2000).toString()));yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(pending).put(older),now).getString("backupId").equals("qa_older"));
        JSONObject retry=job("qa_retry","UPLOADING",now-1000).put("archive",new JSONObject().put("capturedAt",Instant.ofEpochMilli(now-1000).toString()).put("localVerifiedAt",Instant.ofEpochMilli(now-1000).toString())).put("nextAttemptAt",now+60000);yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(retry),now).getLong("nextAttemptAt")==now+60000);
        retry.put("attempts",8);yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(retry),now).getBoolean("review_required"));
        JSONObject restored=job("qa_restored","UPLOADING",now-1000).put("contextMatches",false);yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(restored),now).getString("action").equals("CREATE"));
        JSONObject foreign=job("qa_foreign","UPLOADING",now-1000);foreign.getJSONObject("destination").put("folderId","qa_other_folder");yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(foreign),now).getString("action").equals("CREATE"));
        JSONObject future=job("qa_future","REMOTE_VERIFIED",now+600000);yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(future),now).getString("action").equals("CREATE"));
        yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(job("qa_newer","UPLOADING",now).put("formatVersion",2)),now).getString("action").equals("CREATE"));
        yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(job("qa_unknown","qa_unsupported",now)),now).getString("action").equals("CREATE"));
        yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(job("qa_provider","PROVIDER_VERIFIED",now)),now).getString("action").equals("CREATE"));
        yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(job("qa_future_pending","CAPTURE_PENDING",now+600000)),now).getString("action").equals("CREATE"));
        yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(job("qa_held","UPLOADING",now).put("nextAttemptAt",Long.MAX_VALUE)),now).getString("action").equals("CREATE"));
        JSONObject oldUpload=new JSONObject(retry.toString()).put("attempts",0).put("nextAttemptAt",now+60000);oldUpload.getJSONObject("archive").put("capturedAt",Instant.ofEpochMilli(now-2*RetiredBackupScheduleRules.INTERVAL_MS).toString());yes(RetiredBackupScheduleRules.decide(settings,new JSONArray().put(oldUpload),now).getString("action").equals("CREATE"));
        JSONObject broken=job("qa_broken","RETRY_PENDING",now-10000).put("formatVersion",77);JSONObject healthy=job("qa_healthy","CAPTURE_PENDING",now-1000);JSONObject mixed=RetiredBackupScheduleRules.decide(settings,new JSONArray().put(broken).put(healthy),now);yes(mixed.getString("backupId").equals("qa_healthy")&&mixed.getBoolean("review_required"));
        JSONObject localOnly=job("qa_local_only","LOCAL_VERIFIED",now-1000).put("archive",new JSONObject().put("capturedAt",Instant.ofEpochMilli(now-1000).toString()).put("localVerifiedAt",Instant.ofEpochMilli(now-500).toString()));yes(!RetiredBackupScheduleRules.decide(settings,new JSONArray().put(localOnly),now).getString("action").equals("CREATE"));
        yes(RetiredBackupScheduleRules.retryAt(now,1,0)==now+30000);yes(RetiredBackupScheduleRules.retryAt(now,2,0)==now+60000);yes(RetiredBackupScheduleRules.retryAt(now,7,3600000)==now+3600000);yes(RetiredBackupScheduleRules.retryAt(now,8,0)==Long.MAX_VALUE);yes(RetiredBackupScheduleRules.retryAt(now,2,Long.MAX_VALUE)==now+6L*60*60*1000);
        yes(!RetiredBackupScheduleRules.decide(settings,new JSONArray(),now).getBoolean("exactExecutionGuaranteed"));
        JSONObject pendingManual=new JSONObject().put("backup_id","qa_pending_manual").put("phase","CAPTURE_PENDING");
        String manual=RetiredBackupScheduleRules.manualStartAction("qa_request_manual","qa_pending_manual",new JSONArray().put(pendingManual),"qa_pending_manual");
        yes(manual.equals("REQUEST"));
        yes(RetiredBackupScheduleRules.manualStartAction("qa_request_unknown","",new JSONArray(),"").equals("REQUEST"));
        yes(RetiredBackupScheduleRules.manualStartAction("","",new JSONArray().put(pendingManual),"qa_pending_manual").equals("RESUME"));
        for(String phase:new String[]{"REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED","FAILED","NEEDS_USER_ACTION","FORMAT_REVIEW_REQUIRED"})yes(RetiredBackupScheduleRules.manualStartAction("qa_request_manual","qa_pending_manual",new JSONArray().put(new JSONObject(pendingManual.toString()).put("phase",phase)),"qa_pending_manual").equals("STATUS"));
        JSONObject receipt=new JSONObject().put("backup_id","qa_pending_manual").put("accepted_locally",true).put("phase","CAPTURE_PENDING").put("cloud_confirmed",false).put("next_action","schedule_after_commit");
        yes(RetiredBackupScheduleRules.networkWait(new JSONObject(),true,true).isEmpty());
        yes(RetiredBackupScheduleRules.networkWait(new JSONObject().put("unmeteredOnly",false),true,true).isEmpty());
        yes(RetiredBackupScheduleRules.networkWait(new JSONObject().put("unmeteredOnly",true),true,true).equals("BACKUP_WIFI_REQUIRED"));
        yes(RetiredBackupScheduleRules.networkWait(new JSONObject().put("unmeteredOnly",true),true,false).isEmpty());
        yes(RetiredBackupScheduleRules.networkWait(new JSONObject(),false,false).equals("NETWORK_UNAVAILABLE"));
        int[] requests={0},foreground={0};
        JSONObject completed=RetiredBackupScheduleRules.dispatchManual(()->{requests[0]++;return receipt;},id->{foreground[0]++;yes(id.equals("qa_pending_manual"));return new JSONObject().put("backupId",id).put("action","COMPLETED").put("phase","REMOTE_VERIFIED");});
        yes(requests[0]==1&&foreground[0]==1&&completed.getJSONObject("foreground_result").getString("phase").equals("REMOTE_VERIFIED"));yes(completed.getString("backup_id").equals(receipt.getString("backup_id"))&&completed.getBoolean("accepted_locally")&&!completed.getBoolean("cloud_confirmed")&&!receipt.has("foreground_result"));
        JSONObject waiting=RetiredBackupScheduleRules.dispatchManual(()->receipt,id->new JSONObject().put("backupId",id).put("action","WAIT_NETWORK").put("lastErrorCode","NETWORK_UNAVAILABLE"));yes(waiting.getBoolean("accepted_locally")&&!waiting.getBoolean("cloud_confirmed")&&waiting.getJSONObject("foreground_result").getString("action").equals("WAIT_NETWORK"));
        JSONObject thrown=RetiredBackupScheduleRules.dispatchManual(()->receipt,id->{throw new java.io.IOException("QA private execution detail");});yes(thrown.getString("execution_error_code").equals("BACKUP_OPERATION_FAILED")&&!thrown.toString().contains("private execution detail")&&thrown.getBoolean("accepted_locally"));
        int[] durable={0},dispatches={0},replays={0};RetiredBackupScheduleRules.ManualRequest lostReply=()->{if(durable[0]==0){durable[0]++;throw new java.io.IOException("QA response lost after durable request");}replays[0]++;return receipt;};
        try{RetiredBackupScheduleRules.dispatchManual(lostReply,id->{dispatches[0]++;return new JSONObject().put("backupId",id).put("action","COMPLETED");});throw new AssertionError("lost reply accepted");}catch(java.io.IOException expected){yes(durable[0]==1&&dispatches[0]==0);}
        JSONObject recovered=RetiredBackupScheduleRules.dispatchManual(lostReply,id->{dispatches[0]++;return new JSONObject().put("backupId",id).put("action","COMPLETED");});yes(recovered.getString("backup_id").equals("qa_pending_manual")&&durable[0]==1&&dispatches[0]==1&&replays[0]==1);
        try{RetiredBackupScheduleRules.dispatchManual(()->new JSONObject().put("accepted_locally",true),id->{dispatches[0]++;return new JSONObject();});throw new AssertionError("missing identity accepted");}catch(java.io.IOException expected){yes(dispatches[0]==1);}
        JSONObject mismatch=RetiredBackupScheduleRules.dispatchManual(()->receipt,id->new JSONObject().put("backupId","qa_wrong_job").put("action","COMPLETED"));yes(mismatch.getString("execution_error_code").equals("BACKUP_RUN_RESULT_UNKNOWN")&&!mismatch.has("foreground_result"));
        JSONObject interrupted=RetiredBackupScheduleRules.dispatchManual(()->receipt,id->{throw new java.io.InterruptedIOException();});try{yes(Thread.currentThread().isInterrupted()&&interrupted.getString("execution_error_code").equals("BACKUP_RUN_INTERRUPTED")&&interrupted.getString("backup_id").equals("qa_pending_manual"));}finally{Thread.interrupted();}
        JSONObject manualWaiting=new JSONObject().put("phase","LOCAL_VERIFIED").put("retry_available",true).put("context_matches",true).put("target_matches",true).put("next_attempt_at",now+60000);
        yes(RetiredBackupScheduleRules.manualRetryAvailable(manualWaiting,now));
        yes(!RetiredBackupScheduleRules.manualRetryAvailable(manualWaiting,now+60000));
        for(String key:new String[]{"retry_available","context_matches","target_matches"})yes(!RetiredBackupScheduleRules.manualRetryAvailable(new JSONObject(manualWaiting.toString()).put(key,false),now));
        for(String phase:new String[]{"REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED","FORMAT_REVIEW_REQUIRED","qa_unknown"})yes(!RetiredBackupScheduleRules.manualRetryAvailable(new JSONObject(manualWaiting.toString()).put("phase",phase),now));
        for(String phase:new String[]{"FAILED","NEEDS_USER_ACTION","RETRY_PENDING"})yes(RetiredBackupScheduleRules.manualRetryAvailable(new JSONObject(manualWaiting.toString()).put("phase",phase).put("next_attempt_at",0),now));
        Exception disk=new java.io.IOException("QA private file path must never become status");yes(BackupScheduleRules.captureFailure("DATABASE",disk).getMessage().equals("BACKUP_CAPTURE_DATABASE_IO_FAILED"));
        Exception known=new java.io.IOException("BACKUP_STORAGE_RESERVE_REQUIRED");yes(BackupScheduleRules.captureFailure("FILES",known)==known);
        Exception cancel=new java.io.InterruptedIOException();yes(BackupScheduleRules.captureFailure("FILES",cancel)==cancel);
        yes(BackupScheduleRules.captureFailure("QA private stage",disk).getMessage().equals("BACKUP_CAPTURE_UNKNOWN_IO_FAILED"));
        yes(BackupScheduleRules.captureFailure("INVENTORY",new org.json.JSONException("QA data")).getMessage().equals("BACKUP_CAPTURE_INVENTORY_DATA_FAILED"));
        System.out.println("MANUAL_START_AFTER action="+manual+" existing_generations="+durable[0]+" foreground_runs="+dispatches[0]+" Android_scheduler_calls=0");
        System.out.println("PASS "+checks+" daily backup/retry/restore-context/data-age/clock/target guards;no scheduler timing or account claim");
    }
}
