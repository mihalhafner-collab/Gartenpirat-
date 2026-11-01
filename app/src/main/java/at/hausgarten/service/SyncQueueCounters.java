package at.hausgarten.service;
import org.json.*;
/** Saved journal rows are not equivalent to pending network transfers. Read only. */
final class SyncQueueCounters {
    static JSONObject describe(JSONArray actions,JSONObject queue)throws Exception {
        int local=0;for(int i=0;actions!=null&&i<actions.length();i++){
            JSONObject p=actions.getJSONObject(i).optJSONObject("payload");
            if(p!=null&&(p.optBoolean("_local_only")||p.optBoolean("test_record")))local++;
        }
        return new JSONObject().put("saved_actions",actions==null?0:actions.length())
            .put("local_only_actions",local).put("pending_upload",queue.optInt("pending_upload"))
            .put("review",queue.optInt("review")).put("transfer_items",queue.optInt("total"));
    }
    private SyncQueueCounters(){}
}
