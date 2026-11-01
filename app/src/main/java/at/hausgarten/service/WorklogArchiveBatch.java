package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Bounded selection, one native transaction, ordinary compatible archive events. */
final class WorklogArchiveBatch {
    static final int MAX=200;
    static JSONObject preview(JSONObject snapshot,JSONArray ids)throws Exception {
        if(ids.length()<1||ids.length()>MAX)throw new IllegalArgumentException("Bitte 1 bis 200 Arbeiten auswählen.");
        Set<String> seen=new HashSet<>();JSONArray entries=new JSONArray(),guards=new JSONArray();boolean allowed=true;
        for(int i=0;i<ids.length();i++){
            String id=ids.getString(i);if(id.isEmpty()||!seen.add(id))throw new IllegalArgumentException("Auswahl enthält eine leere oder doppelte ID.");
            JSONObject p=DeletionRules.preview(snapshot,"worklog",id),row=OwnerJournalRules.findOrNull(snapshot.getJSONArray("worklogs"),id);
            entries.put(new JSONObject(p.toString()).put("activity",row.optString("activity")).put("work_date",row.optString("work_date")));
            guards.put(new JSONObject().put("id",id).put("record",p.getString("expected_record_hash")).put("photos",p.getString("expected_photo_links_hash")).put("allowed",p.getBoolean("allowed")));
            allowed&=p.getBoolean("allowed");
        }
        return new JSONObject().put("count",ids.length()).put("allowed",allowed).put("entries",entries)
            .put("preview_hash",PixelMcpWriteTools.hash(guards)).put("recoverable",true).put("physical_delete",false);
    }
    static JSONArray actions(JSONObject snapshot,JSONArray ids,String expected,String request,String stamp)throws Exception {
        JSONObject preview=preview(snapshot,ids);
        if(!preview.getString("preview_hash").equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Ausgewählte Arbeiten oder Fotozuordnungen wurden geändert. Nichts entfernt; Auswahl erneut prüfen.");
        if(!preview.getBoolean("allowed"))throw new IllegalArgumentException("Mindestens eine Arbeit ist geschützt. Nichts entfernt; Auswahl prüfen.");
        JSONObject copy=new JSONObject(snapshot.toString());JSONArray out=new JSONArray();
        for(int i=0;i<ids.length();i++){
            String id=ids.getString(i);JSONObject p=DeletionRules.payload(copy,"worklog",id,preview.getJSONArray("entries").getJSONObject(i).getString("expected_record_hash"),stamp);
            JSONObject event=new JSONObject().put("client_id",request+"_"+i).put("kind","archive").put("payload",p).put("created_at",stamp);
            OwnerJournalRules.apply(copy,event,new HashMap<>(),new JSONObject());out.put(event);
        }
        return out;
    }
    private WorklogArchiveBatch(){}
}
