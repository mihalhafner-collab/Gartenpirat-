package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Shared UI/MCP/Drive trash policy. No cascade deletion, no filesystem side effects. */
final class TrashRules {
    static boolean purged(JSONObject s,String entity,String id)throws Exception {
        JSONObject a=OwnerJournalRules.archives(s).optJSONObject(entity+":"+id);
        return a!=null&&a.optBoolean("purged");
    }
    static JSONObject links(JSONObject photo,String entity,String id)throws Exception {
        JSONObject fields=new JSONObject();
        if(entity.equals("customer")&&id.equals(photo.optString("customer_id"))) {
            for(String key:new String[]{"customer_id","order_id","worklog_id","session_id","role","customer_visible"})
                fields.put(key,photo.has(key)?photo.get(key):JSONObject.NULL);
        }else if(entity.equals("order")&&id.equals(photo.optString("order_id")))fields.put("order_id",photo.get("order_id"));
        else if(entity.equals("worklog")&&id.equals(photo.optString("worklog_id"))) {
            fields.put("worklog_id",photo.get("worklog_id"));fields.put("session_id",photo.opt("session_id")==null?JSONObject.NULL:photo.get("session_id"));
        }
        return fields;
    }
    static JSONArray related(JSONObject s,String entity,String id)throws Exception {
        TreeMap<String,JSONObject> sorted=new TreeMap<>();JSONArray photos=s.getJSONArray("photos");
        for(int i=0;i<photos.length();i++){JSONObject p=photos.getJSONObject(i);if(links(p,entity,id).length()>0)
            sorted.put(p.getString("id"),new JSONObject().put("id",p.getString("id")).put("hash",OwnerJournalRules.version(p)));}
        JSONObject archive=OwnerJournalRules.archives(s);
        for(Iterator<String> it=archive.keys();it.hasNext();){String key=it.next();JSONObject entry=archive.getJSONObject(key),p=entry.optJSONObject("record");if(key.startsWith("photo:")&&p!=null&&links(p,entity,id).length()>0)
            sorted.put("archive:"+p.getString("id"),new JSONObject().put("id",p.getString("id")).put("hash",OwnerJournalRules.version(entry)).put("location","archive"));}
        return new JSONArray(sorted.values());
    }
    static JSONObject detached(JSONObject p,JSONObject fields,String stamp)throws Exception {
        JSONObject next=new JSONObject(p.toString());
        for(Iterator<String> it=fields.keys();it.hasNext();){String key=it.next();next.put(key,key.equals("role")?"documentation":key.equals("customer_visible")?0:"");}
        return next.put("updated_at",stamp);
    }
    static JSONArray detachRelated(JSONObject s,String entity,String id,String stamp)throws Exception {
        JSONArray saved=new JSONArray(),rows=s.getJSONArray("photos");
        for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i),fields=links(p,entity,id);if(fields.length()==0)continue;
            JSONObject next=detached(p,fields,stamp);saved.put(new JSONObject().put("id",p.getString("id")).put("fields",fields).put("detached_hash",OwnerJournalRules.version(next)));rows.put(i,next);}
        JSONObject archive=OwnerJournalRules.archives(s);
        for(Iterator<String> it=archive.keys();it.hasNext();){String key=it.next();JSONObject entry=archive.getJSONObject(key),p=entry.optJSONObject("record");if(!key.startsWith("photo:")||p==null)continue;
            JSONObject fields=links(p,entity,id);if(fields.length()==0)continue;
            entry.put("record",detached(p,fields,stamp));saved.put(new JSONObject().put("id",p.getString("id")).put("fields",fields).put("location","archive").put("detached_hash",OwnerJournalRules.version(entry)));
        }
        return saved;
    }
    static void restoreLinks(JSONObject s,JSONObject archive)throws Exception {
        JSONArray links=archive.optJSONArray("photo_links");
        for(int i=0;links!=null&&i<links.length();i++){
            JSONObject link=links.getJSONObject(i);boolean archived="archive".equals(link.optString("location"));
            JSONObject entry=archived?OwnerJournalRules.archives(s).optJSONObject("photo:"+link.getString("id")):null;
            JSONObject p=archived?(entry==null?null:entry.optJSONObject("record")):OwnerJournalRules.findOrNull(s.getJSONArray("photos"),link.getString("id"));
            // A newer reassignment, restore, purge or edited caption is never overwritten.
            if(p==null||!OwnerJournalRules.version(archived?entry:p).equals(link.getString("detached_hash")))continue;
            JSONObject restored=new JSONObject(p.toString()),fields=link.getJSONObject("fields");
            for(Iterator<String> it=fields.keys();it.hasNext();){String key=it.next();restored.put(key,fields.get(key));}
            try{EntityProtectionRules.validatePhoto(restored,s.getJSONArray("customers"),s.getJSONArray("orders"),s.getJSONArray("worklogs"));}
            catch(IllegalArgumentException changedRelation){continue;}
            if(archived)entry.put("record",restored);else OwnerJournalRules.replace(s,"photos",p.getString("id"),restored);
        }
    }
    static void requireUnreferenced(JSONObject s,String entity,String id)throws Exception {
        String ref=entity.equals("customer")?"customer_id":entity.equals("order")?"order_id":entity.equals("worklog")?"worklog_id":entity.equals("offer")?"offer_id":"";
        if(ref.isEmpty())return;
        for(String group:new String[]{"customers","orders","worklogs","photos","invoices","offers"}){
            JSONArray rows=s.getJSONArray(group);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);
                if(group.equals(OwnerJournalRules.group(entity))&&id.equals(row.optString("id")))continue;
                if(id.equals(EntityProtectionRules.text(row,ref))||(entity.equals("customer")&&(id.equals(row.optString("billing_customer_id"))||id.equals(row.optString("default_billing_customer_id"))))||
                    (entity.equals("worklog")&&containsWork(row,id)))throw new IllegalArgumentException("Noch verknüpfte "+group+" vorhanden. Zuordnung zuerst gezielt lösen; keine Kaskadenlöschung.");
            }
        }
        JSONObject archive=OwnerJournalRules.archives(s);
        for(Iterator<String> it=archive.keys();it.hasNext();){
            String key=it.next();if(key.equals(entity+":"+id))continue;
            JSONObject r=archive.getJSONObject(key).optJSONObject("record");if(r==null)continue;
            boolean billing=entity.equals("customer")&&(id.equals(r.optString("billing_customer_id"))||id.equals(r.optString("default_billing_customer_id")));
            if(id.equals(r.optString(ref))||billing||(entity.equals("worklog")&&containsWork(r,id)))
                throw new IllegalArgumentException("Ein anderer Papierkorbeintrag verweist darauf. Diesen zuerst wiederherstellen und die Zuordnung lösen oder einzeln löschen.");
        }
    }
    private static boolean containsWork(JSONObject row,String id){JSONArray refs=row.optJSONArray("worklog_ids"),positions=row.optJSONArray("positions");for(int i=0;refs!=null&&i<refs.length();i++)if(id.equals(refs.optString(i)))return true;for(int i=0;positions!=null&&i<positions.length();i++)if(id.equals(positions.optJSONObject(i)==null?"":positions.optJSONObject(i).optString("worklog_id")))return true;return false;}
    static void purge(JSONObject s,JSONObject action)throws Exception {
        JSONObject p=action.getJSONObject("payload");if(!p.optBoolean("confirmed"))throw new IllegalArgumentException("Endgültiges Löschen ausdrücklich bestätigen.");
        String entity=p.getString("entity"),id=p.getString("id"),key=entity+":"+id;JSONObject archive=OwnerJournalRules.archives(s),saved=archive.optJSONObject(key);
        if(saved==null||saved.optBoolean("purged"))throw new IllegalArgumentException("Eintrag nicht im Papierkorb oder bereits endgültig gelöscht.");
        OwnerJournalRules.checkHash(saved,p.getString("expected_record_hash"));JSONObject record=saved.getJSONObject("record");
        OwnerJournalRules.validateArchive(entity,record,s);requireUnreferenced(s,entity,id);
        JSONObject current=OwnerJournalRules.findOrNull(s.getJSONArray(OwnerJournalRules.group(entity)),id);
        if(entity.equals("customer")||entity.equals("service_template")){OwnerJournalRules.checkHash(current,saved.getString("archived_record_hash"));}
        else if(current!=null)throw new IllegalArgumentException("Die ID ist inzwischen wieder aktiv. Nichts gelöscht.");
        JSONObject tombstone=new JSONObject().put("purged",true).put("purged_at",action.getString("created_at")).put("purge_action",action.getString("client_id")).put("archive_hash",p.getString("expected_record_hash"));
        JSONObject media=new JSONObject();if(entity.equals("photo"))for(String field:new String[]{"content_sha256","storage_name","byte_size","mime_type","thumbnail_storage_name","thumbnail_sha256"})if(record.has(field))media.put(field,record.get(field));
        if(media.length()>0)tombstone.put("media",media);
        OwnerJournalRules.replace(s,OwnerJournalRules.group(entity),id,null);archive.put(key,tombstone);
    }
    static boolean referenced(JSONObject s,JSONArray pending,String name)throws Exception {
        if(name.isEmpty())return false;
        for(String group:new String[]{"photos","invoices","offers"})if(hasFile(s.optJSONArray(group),name))return true;
        JSONObject a=OwnerJournalRules.archives(s);for(Iterator<String> it=a.keys();it.hasNext();){JSONObject entry=a.getJSONObject(it.next());if(!entry.optBoolean("purged")&&hasFile(entry.optJSONObject("record"),name))return true;}
        return hasFile(pending,name);
    }
    private static boolean hasFile(Object value,String name)throws Exception {
        if(value instanceof JSONObject){JSONObject row=(JSONObject)value;if(name.equals(row.optString("storage_name"))||name.equals(row.optString("thumbnail_storage_name"))||name.equals(row.optString("pdf_storage_name")))return true;for(Iterator<String> it=row.keys();it.hasNext();)if(hasFile(row.get(it.next()),name))return true;}
        if(value instanceof JSONArray){JSONArray rows=(JSONArray)value;for(int i=0;i<rows.length();i++)if(hasFile(rows.get(i),name))return true;}
        return false;
    }
    private TrashRules(){}
}
