package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Read-only, hash-bound selection. Never applies a business proposal. */
final class LegacyConflictRules {
    static JSONObject preview(JSONObject state)throws Exception {
        JSONArray conflicts=PixelMcpOwnerTools.conflicts(state,new JSONObject().put("limit",100)).getJSONArray("conflicts"),entries=new JSONArray();
        for(int i=0;i<conflicts.length();i++){
            JSONObject c=conflicts.getJSONObject(i),a=c.optJSONObject("action");if(a==null||!c.optBoolean("resolution_available"))continue;
            String code=c.optString("code"),kind=a.optString("kind"),stamp=a.optString("created_at");
            if(stamp.isEmpty()||stamp.substring(0,Math.min(10,stamp.length())).compareTo("2026-10-18")>=0)continue;
            if(!(kind.equals("delete")&&code.equals("DELETE_REVIEW_REQUIRED")||kind.equals("customer")&&code.equals("WRITE_CONFLICT")))continue;
            entries.put(new JSONObject().put("client_id",a.getString("client_id")).put("expected_action_hash",c.getString("action_hash")).put("expected_current_hash",c.getString("current_hash")).put("resolution","keep_current").put("reason","historisch_nicht_mehr_pruefbar"));
        }
        List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<entries.length();i++)sorted.add(entries.getJSONObject(i));sorted.sort(Comparator.comparing(x->x.optString("client_id")));entries=new JSONArray(sorted);
        return new JSONObject().put("count",entries.length()).put("entries",entries).put("preview_hash",PixelMcpWriteTools.hash(entries)).put("resolution","keep_current").put("effect","no_business_data_changed").put("message",entries.length()+" historische Änderungen können nicht mehr verifiziert werden. Der aktuelle Datenbestand bleibt unverändert. Keine Löschung wird ausgeführt.");
    }
    static void require(JSONObject state,String expected)throws Exception {
        JSONObject p=preview(state);if(p.getInt("count")==0||!p.getString("preview_hash").equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Die Vorschau hat sich verändert. Nichts abgeschlossen; erneut prüfen.");
    }
}
