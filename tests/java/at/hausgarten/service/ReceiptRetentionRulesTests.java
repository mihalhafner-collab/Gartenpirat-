package at.hausgarten.service;
import org.json.*;
import java.time.Instant;

public final class ReceiptRetentionRulesTests {
    static int checks;static void yes(boolean b){checks++;if(!b)throw new AssertionError("receipt retention "+checks);}
    public static void main(String[] args)throws Exception{
        SyntheticBusinessData.verify();long now=Instant.parse("2026-10-22T12:00:00Z").toEpochMilli();
        JSONObject old=new JSONObject().put("created_at","2026-06-01T12:00:00Z").put("fingerprint","qa_fingerprint").put("result",new JSONObject().put("client_id","qa_done"));
        JSONArray empty=new JSONArray();yes(ReceiptRetentionRules.eligible(old,"qa_done",empty,empty,now));
        yes(!ReceiptRetentionRules.eligible(old,"qa_done",new JSONArray().put(new JSONObject().put("client_id","qa_done")),empty,now));
        for(String field:new String[]{"purge_action","_backup_request","client_id"})yes(!ReceiptRetentionRules.eligible(old,"qa_done",empty,new JSONObject().put("qa_tombstone",new JSONObject().put(field,"qa_done")),now));
        for(String field:new String[]{"deletion_backup_id","backup_id"}){JSONObject copy=new JSONObject(old.toString());copy.getJSONObject("result").put(field,"qa_backup");yes(!ReceiptRetentionRules.eligible(copy,"qa_done",empty,empty,now));}
        JSONObject recent=new JSONObject(old.toString()).put("created_at",Instant.ofEpochMilli(now-ReceiptRetentionRules.AGE_MS).toString());yes(!ReceiptRetentionRules.eligible(recent,"qa_done",empty,empty,now));
        recent.put("created_at",Instant.ofEpochMilli(now+1).toString());yes(!ReceiptRetentionRules.eligible(recent,"qa_done",empty,empty,now));
        recent.remove("created_at");yes(!ReceiptRetentionRules.eligible(recent,"qa_done",empty,empty,now));recent.put("observed_at","2026-06-01T12:00:00Z");yes(ReceiptRetentionRules.eligible(recent,"qa_done",empty,empty,now));
        recent.put("created_at","qa_invalid_time");yes(!ReceiptRetentionRules.eligible(recent,"qa_done",empty,empty,now));
        yes(!ReceiptRetentionRules.references(new JSONObject().put("qa_text","qa_done_other"),"qa_done"));
        System.out.println("PASS "+checks+" receipt age/queue/backup/tombstone/reference gates");
    }
}
