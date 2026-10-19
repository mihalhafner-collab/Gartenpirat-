package at.hausgarten.service;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** An immutable manifest is published last. Its receipt binds action ID AND content. */
final class DrivePublication {
    static JSONObject manifest(JSONObject snapshot,JSONArray actions,String stateId,String sha)throws Exception{
        Set<String> applied=DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids"));applied.addAll(DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids")));
        JSONObject resolved=snapshot.optJSONObject("drive_resolved_actions");JSONObject receipts=new JSONObject();
        Map<String,JSONObject> errors=new HashMap<>();for(String field:new String[]{"sync_errors","drive_journal_errors"}){JSONArray list=snapshot.optJSONArray(field);for(int i=0;list!=null&&i<list.length();i++){JSONObject e=list.getJSONObject(i);errors.put(e.optString("client_id"),e);}}
        Set<String> missing=new HashSet<>();JSONArray media=snapshot.optJSONArray("drive_media_errors");for(int i=0;media!=null&&i<media.length();i++){JSONObject m=media.getJSONObject(i);missing.add(m.optString("entity_type","photo")+":"+m.optString("entity_id",m.optString("photo_id")));}
        for(int i=0;i<actions.length();i++){JSONObject a=actions.getJSONObject(i);String id=a.getString("client_id");boolean done=applied.contains(id)||(resolved!=null&&resolved.has(id));String kind=a.optString("kind"),entity=kind.startsWith("offer")?"offer":kind.contains("invoice")?"invoice":kind;if(missing.contains(entity+":"+a.getJSONObject("payload").optString("id")))done=false;JSONObject e=errors.get(id);
            receipts.put(id,new JSONObject().put("action_sha256",PixelMcpWriteTools.hash(a)).put("applied",done&&e==null).put("code",e==null?(done?"":"PROCESSOR_REQUIRED"):e.optString("code","REVIEW_REQUIRED")));
        }
        return new JSONObject().put("format","hausgarten.drive.commit.v1").put("state_file_id",stateId).put("state_sha256",sha).put("receipts",receipts);
    }
    static void verify(JSONObject manifest,byte[] state,JSONArray actions)throws Exception{
        if(!"hausgarten.drive.commit.v1".equals(manifest.optString("format"))||!DriveRestClient.sha256(state).equals(manifest.optString("state_sha256")))throw new IllegalArgumentException("Drive-Veröffentlichung ist unvollständig oder verändert.");
        JSONObject receipts=manifest.getJSONObject("receipts");for(int i=0;i<actions.length();i++){JSONObject action=actions.getJSONObject(i),r=receipts.optJSONObject(action.getString("client_id"));if(r==null||!PixelMcpWriteTools.hash(action).equals(r.optString("action_sha256")))throw new IllegalArgumentException("Drive-Aktionsbeleg stimmt nicht mit der Änderung überein.");}
    }
    static JSONObject progress(JSONObject manifest,String publicationId)throws Exception{
        JSONObject out=new JSONObject(),rs=manifest.getJSONObject("receipts");for(Iterator<String> keys=rs.keys();keys.hasNext();){String id=keys.next();JSONObject r=new JSONObject(rs.getJSONObject(id).toString());r.put("cloud_confirmed",r.optBoolean("applied")).put("provider_readback",true).put("drive_manifest_readback",true).put("publication_id",publicationId).put("state_sha256",manifest.getString("state_sha256"));out.put(id,r);}return out;
    }
    static byte[] stateBytes(JSONObject snapshot)throws Exception{
        JSONObject clean=new JSONObject(snapshot.toString());for(String key:new String[]{"drive_cloud_receipts","drive_last_publication","drive_parent_publication_id","drive_parent_state_sha256","drive_parent_sequence"})clean.remove(key);return PixelMcpWriteTools.canonical(clean).getBytes(StandardCharsets.UTF_8);
    }
    static JSONObject evidence(JSONObject result,JSONObject state)throws Exception{
        JSONObject master=state.getJSONObject("master"),rs=master.optJSONObject("drive_cloud_receipts"),r=rs==null?null:rs.optJSONObject(result.optString("client_id"));
        if(r==null||!r.optBoolean("cloud_confirmed"))return result;
        JSONArray pending=state.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject a=pending.getJSONObject(i);if(result.optString("client_id").equals(a.optString("client_id"))&&!r.optString("action_sha256").equals(PixelMcpWriteTools.hash(a)))return result;}
        result.put("cloud_confirmed",true).put("confirmation_scope","drive_server_manifest").put("publication_id",r.optString("publication_id")).put("fully_synchronized",r.optBoolean("applied"));
        if(r.optBoolean("applied"))result.put("sync_state","sync_confirmed").put("sync_message","Änderung übernommen und mit Drive-Serverbeleg bestätigt.");return result;
    }
    static JSONObject head(JSONArray summaries,String publisher)throws Exception{
        TreeMap<Long,JSONObject> chain=new TreeMap<>();
        for(int i=0;i<summaries.length();i++){JSONObject s=summaries.getJSONObject(i);if(!publisher.equals(s.getString("publisher")))throw new IllegalArgumentException("PRIMARY_DEVICE_CONFLICT: Mehrere primäre Pixel-Herausgeber.");long n=s.getLong("sequence");if(n<1)throw new IllegalArgumentException("PUBLICATION_INVALID: Ungültige Veröffentlichungsfolge.");JSONObject previous=chain.putIfAbsent(n,s);if(previous!=null&&!previous.getString("id").equals(s.getString("id")))throw new IllegalArgumentException("PUBLICATION_CONFLICT: Zwei Varianten desselben Veröffentlichungsstands.");}
        long sequence=0;JSONObject prior=null;for(Map.Entry<Long,JSONObject> row:chain.entrySet()){JSONObject s=row.getValue();if(row.getKey()!=++sequence||(prior==null?!s.optString("parent").isEmpty():!prior.getString("id").equals(s.optString("parent"))||!prior.getString("sha").equals(s.optString("parent_sha"))))throw new IllegalArgumentException("PUBLICATION_GAP: Die Veröffentlichungskette ist unvollständig. Originale bleiben erhalten.");prior=s;}
        return prior==null?new JSONObject():prior;
    }
}
