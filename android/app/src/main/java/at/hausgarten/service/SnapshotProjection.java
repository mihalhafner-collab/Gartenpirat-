package at.hausgarten.service;
import org.json.*;
import java.util.Iterator;

/** Existing validated pending-action projection; no persistence or transport. */
final class SnapshotProjection {
    static JSONObject effective(JSONObject state) throws Exception {
        JSONObject master = new JSONObject(state.getJSONObject("master").toString());
        for(String key:new String[]{"offers","service_catalog","appointments"})if(!master.has(key))master.put(key,new JSONArray());
        JSONArray pending=state.optJSONArray("pending");
        if(pending==null)pending=new JSONArray();
        if(PixelDriveState.ENGINE.equals(master.optString("processing_engine"))||LocalAuthorityRules.ENGINE.equals(master.optString("processing_engine"))){
            // Local presentation uses exactly the same validation as Drive replay.
            // Never project receipts or pretend a queued financial request is issued.
            JSONObject projected=new JSONObject(master.toString());DriveJournalRules.apply(projected,pending);
            for(String key:new String[]{"customers","orders","worklogs","invoices","photos","offers","service_catalog","appointments","business_settings","invoice_issuer","owner_archive"})if(projected.has(key))master.put(key,projected.get(key));
            return master;
        }
        java.util.Set<String> blocked=new java.util.HashSet<>();for(String key:new String[]{"sync_errors","drive_journal_errors"}){JSONArray errors=master.optJSONArray(key);for(int i=0;errors!=null&&i<errors.length();i++)blocked.add(errors.getJSONObject(i).optString("client_id"));}
        for(String pair:new String[]{"customers:customer","orders:order","worklogs:worklog","invoices:invoice","photos:photo","offers:offer","service_catalog:service_template","appointments:appointment"}){
            String[] parts=pair.split(":"); java.util.LinkedHashMap<String,JSONObject> rows=new java.util.LinkedHashMap<>();
            JSONArray base=master.optJSONArray(parts[0]);
            if(base!=null)for(int i=0;i<base.length();i++){JSONObject row=base.getJSONObject(i);String id=row.getString("id");if(rows.containsKey(id))throw new IllegalArgumentException("Doppelte Datensatz-ID im Datenstand.");rows.put(id,new JSONObject(row.toString()));}
            for(int i=0;i<pending.length();i++){JSONObject action=pending.getJSONObject(i);if(!parts[1].equals(action.optString("kind"))||blocked.contains(action.optString("client_id")))continue;
                JSONObject payload=action.getJSONObject("payload");String id=payload.getString("id");JSONObject row=rows.containsKey(id)?rows.get(id):new JSONObject();
                Iterator<String> keys=payload.keys();while(keys.hasNext()){String key=keys.next();row.put(key,payload.get(key));}rows.put(id,row);
            }
            // Historical delete proposals remain reviewable and never hide the current record.
            master.put(parts[0],new JSONArray(rows.values()));
        }
        java.util.Map<String,JSONObject> all=new java.util.HashMap<>();for(int i=0;i<pending.length();i++)all.put(pending.getJSONObject(i).optString("client_id"),pending.getJSONObject(i));
        JSONObject resolved=master.optJSONObject("drive_resolved_actions");if(resolved==null){resolved=new JSONObject();master.put("drive_resolved_actions",resolved);}
        java.util.Set<String> seen=DriveJournalRules.ids(master.optJSONArray("drive_applied_ids"));
        for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i);if(OwnerJournalRules.handles(a.optString("kind"))&&!blocked.contains(a.optString("client_id"))&&!seen.contains(a.optString("client_id")))try{OwnerJournalRules.apply(master,a,all,resolved);}catch(Exception ignored){/* Keep the action reviewable; preview is never a receipt. */}}
        return master;
    }
}
