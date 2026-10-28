package at.hausgarten.service;
import org.json.*;
import java.io.*;
import java.net.*;

/** Executes production decisions, including the exact fields persisted by recordFailure. */
public final class SyncFailureRulesTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("failure rules "+checks);}
    static JSONObject decision(Exception error,int previous)throws Exception{return SyncFailureRules.recordFailure(error,previous,100000);}
    static JSONObject display(JSONObject d)throws Exception{return SyncPolicy.describe(new JSONObject(d.toString()).put("configured",true),new JSONObject(),false,100000);}
    public static void main(String[] args)throws Exception{
        SyntheticBusinessData.verify();
        for(String code:new String[]{"FOLDER_NOT_SELECTED","DEVICE_ID_MISSING"}){
            for(int previous:new int[]{0,4,8}){
                JSONObject d=decision(new SyncIoFailure(code),previous);
                yes(d.getString("last_error_code").equals(code));yes(d.getBoolean("user_action_required"));yes(d.getInt("retry_count")==1);
                yes(!d.getBoolean("schedule_retry"));yes(d.getLong("next_retry")==0);yes(d.getBoolean("drive_sync_failed"));
                JSONObject shown=display(d);yes(shown.getString("state").equals("action_required"));
                yes(shown.getString("next_action").equals(code.equals("FOLDER_NOT_SELECTED")?"connect_drive":"retry_sync"));yes(shown.getString("detail").equals(SyncPolicy.message(code)));
            }
        }
        for(Exception error:new Exception[]{new UnknownHostException("qa.example.invalid"),new SocketTimeoutException("qa timeout"),new ConnectException("qa unavailable")}){
            JSONObject d=decision(error,0);yes(d.getString("last_error_code").equals("NETWORK_UNAVAILABLE"));yes(!d.getBoolean("user_action_required"));yes(d.getInt("retry_count")==1);
            yes(d.getBoolean("schedule_retry"));yes(d.getLong("next_retry")>100000);yes(display(d).getString("state").equals("retry_wait"));yes(display(d).getString("detail").contains("Internetverbindung"));
            JSONObject exhausted=decision(error,7);yes(exhausted.getInt("retry_count")==8);yes(!exhausted.getBoolean("user_action_required"));yes(exhausted.getBoolean("schedule_retry"));yes(display(exhausted).getString("state").equals("retry_wait"));yes(SyncPolicy.automaticAllowed(display(exhausted),exhausted.getLong("next_retry")));
        }
        JSONObject invalid=decision(new JSONException("qa-private"),2);yes(invalid.getString("last_error_code").equals("INVALID_REMOTE_DATA"));yes(invalid.getBoolean("user_action_required"));yes(invalid.getInt("retry_count")==3);
        JSONObject permission=decision(new SecurityException("qa-private"),0);yes(permission.getString("last_error_code").equals("PERMISSION_REQUIRED"));yes(permission.getBoolean("user_action_required"));
        JSONObject rest=decision(new HttpTransport.Failure("HTTP_429","qa bounded",150000,false),0);yes(rest.getString("last_error_code").equals("HTTP_429"));yes(rest.getLong("next_retry")==250000);
        JSONObject logic=decision(new IllegalStateException("QA Musterkunde /private/qa-file Bearer qa-secret "+"x".repeat(300)),0);
        yes(logic.getString("last_error_code").equals("TRANSFER_FAILED"));String detail=logic.getString("last_error_detail");yes(detail.contains("IllegalStateException"));yes(detail.length()<=120);
        for(String secret:new String[]{"Musterkunde","/private","Bearer","qa-secret","xxxxx"})yes(!detail.contains(secret));
        yes(SyncFailureRules.detail(new IOException("Stream closed")).contains("Stream closed"));yes(SyncFailureRules.detail(new IOException((String)null)).length()<=120);
        // A failed preference write after the snapshot transaction is a status warning, never another transfer attempt.
        JSONObject saved=decision(new SyncIoFailure("STATUS_NOT_SAVED"),7);yes(saved.getInt("retry_count")==0);yes(!saved.getBoolean("drive_sync_failed"));yes(!saved.getBoolean("user_action_required"));
        yes(!saved.getBoolean("schedule_retry"));yes(saved.getLong("next_retry")==0);yes(display(saved).getString("state").equals("status_warning"));yes(display(saved).getString("detail").startsWith("Daten gesichert"));
        JSONObject missing=decision(new SyncIoFailure("REMOTE_STATE_REFERENCE_MISSING"),0);yes(missing.getBoolean("user_action_required"));yes(missing.getBoolean("schedule_retry"));yes(missing.getLong("retry_delay")==900000);
        JSONObject ready=new JSONObject(missing.toString()).put("configured",true).put("automatic_enabled",true);yes(!SyncPolicy.automaticAllowed(ready,missing.getLong("next_retry")-1));yes(SyncPolicy.automaticAllowed(ready,missing.getLong("next_retry")));
        JSONObject exhaustedReference=decision(new SyncIoFailure("REMOTE_STATE_REFERENCE_MISSING"),7);yes(exhaustedReference.getBoolean("user_action_required"));yes(!exhaustedReference.getBoolean("schedule_retry"));yes(exhaustedReference.getLong("next_retry")==0);yes(!SyncPolicy.automaticAllowed(new JSONObject(exhaustedReference.toString()).put("configured",true).put("automatic_enabled",true),Long.MAX_VALUE));
        System.out.println("PASS "+checks+" production sync classification/status/backoff/privacy checks");
    }
}
