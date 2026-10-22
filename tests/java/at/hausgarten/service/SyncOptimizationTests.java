package at.hausgarten.service;
import org.json.*;
import java.nio.charset.StandardCharsets;
public final class SyncOptimizationTests {
    static int checks;
    static void yes(boolean b){if(!b)throw new AssertionError("sync optimization "+checks);checks++;}
    static JSONObject obj()throws Exception{return SyntheticBusinessData.object();}
    public static void main(String[] args)throws Exception {
        SyntheticBusinessData.verify();JSONObject configured=obj().put("configured",true).put("automatic_enabled",false);
        JSONObject q=obj().put("pending_upload",1),manual=SyncPolicy.describe(configured,q,false,0);
        yes(manual.getString("state").equals("pending"));yes(manual.getString("next_action").equals("retry_sync"));yes(!manual.getString("detail").contains("erfolgt automatisch"));yes(!SyncPolicy.automaticAllowed(configured,0));
        configured.put("manual_requested",true);yes(SyncPolicy.automaticAllowed(configured,0));yes(SyncPolicy.describe(configured,q,false,0).getString("next_action").equals("none"));
        configured.put("manual_requested",false).put("drive_sync_failed",true).put("next_retry",1000);yes(!SyncPolicy.describe(configured,q,false,0).getString("state").equals("retry_wait"));
        JSONObject state=PixelMcpWriteTests.state().getJSONObject("master");
        for(String kind:new String[]{"archive","purge_v1","delete"}){
            JSONObject action=obj().put("kind",kind).put("client_id","qa_untransferred").put("payload",obj().put("id","qa_c1"));JSONArray actions=new JSONArray().put(action);
            JSONObject status=DriveJournalRules.queueStatus(state,actions);yes(status.getInt("pending_upload")==1);yes(status.getInt("review")==0);yes(status.getJSONArray("items").getJSONObject(0).getString("next_action").equals("retry_sync"));
        }
        JSONArray actions=new JSONArray().put(obj().put("payload",obj().put("_local_only",true))).put(obj().put("payload",obj().put("test_record",true))).put(obj().put("payload",obj()));
        String before=actions.toString();JSONObject counters=SyncQueueCounters.describe(actions,obj().put("pending_upload",1).put("review",2).put("total",3));yes(counters.getInt("saved_actions")==3);yes(counters.getInt("local_only_actions")==2);yes(counters.getInt("pending_upload")==1);yes(counters.getInt("review")==2);yes(before.equals(actions.toString()));
        JSONObject escaped=new JSONObject("{\"z\":\"https:\\/\\/example.test/<\\/script>\",\"a\":\"ä☘\\\\/\"}");
        String semantic=CatalogSemanticHash.canonical(escaped);yes(!semantic.contains("\\/"+"/"));yes(semantic.equals("{\"a\":\"ä☘\\\\/\",\"z\":\"https://example.test/</script>\"}"));yes(CatalogSemanticHash.hash(escaped).equals(CatalogSemanticHash.hash(new JSONObject(escaped.toString()))));yes(CatalogSemanticHash.VERSION.equals("catalog-json-v1"));
        VerifiedSyncCache cache=new VerifiedSyncCache(12);byte[] raw="qa-bytes".getBytes(StandardCharsets.UTF_8);String name="HausGarten-Commit-"+DriveRestClient.sha256(raw)+".json";
        cache.remember("qa_a",name,raw);yes(cache.bytes()==raw.length);raw[0]=0;yes(cache.get("qa_a")[0]=='q');byte[] copy=cache.get("qa_a");copy[0]=0;yes(cache.get("qa_a")[0]=='q');cache.remember("qa_bad",name,new byte[]{0});yes(cache.get("qa_bad")==null);
        cache.remember("qa_b",name,copy);yes(cache.get("qa_b")==null);cache.remember("qa_b",name,"qa-bytes".getBytes(StandardCharsets.UTF_8));yes(cache.get("qa_a")==null&&cache.bytes()<=12);cache.clear();yes(cache.bytes()==0&&cache.get("qa_b")==null);
        yes(new SyncIoFailure("REMOTE_MASTER_MISSING").needsAction());yes(!new SyncIoFailure("PROVIDER_READ_FAILED").needsAction());yes(!new SyncIoFailure("PROVIDER_READ_FAILED",new java.io.IOException("qa-private-path")).getMessage().contains("qa-private-path"));
        for(String phase:new String[]{"RUNNING","PAUSED","STOPPED_PENDING_REVIEW"}){JSONObject active=obj().put("work_session",obj().put("state",phase).put("customer_id","qa_active"));try{ActiveWorkProtection.require(active,"customer","qa_active");throw new AssertionError("active reference deleted");}catch(PixelMcpWriteTools.Rejected e){yes(e.code.equals("ACTIVE_WORK_SESSION"));}ActiveWorkProtection.require(active,"customer","qa_other");}
        for(String phase:new String[]{"IDLE","FINALIZED","CANCELLED"})ActiveWorkProtection.require(obj().put("work_session",obj().put("state",phase).put("customer_id","qa_active")),"customer","qa_active");
        System.out.println("PASS "+checks+" truthful sync flags, queue actions/counters, semantic serialization and bounded verified cache checks");
    }
}
