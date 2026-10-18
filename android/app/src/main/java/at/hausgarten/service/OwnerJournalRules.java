package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Additive owner journal protocol. Old journals and immutable receipts survive. */
final class OwnerJournalRules {
    static final String[] KINDS={"archive","restore","conflict_resolution","invoice_payment","business_settings"};
    static boolean handles(String kind){return Arrays.asList(KINDS).contains(kind);}
    static JSONObject archives(JSONObject snapshot)throws Exception{JSONObject a=snapshot.optJSONObject("owner_archive");if(a==null){a=new JSONObject();snapshot.put("owner_archive",a);}return a;}
    static String group(String entity){return entity.equals("invoice")?"invoices":DriveJournalRules.collection(entity);}
    static JSONObject findOrNull(JSONArray rows,String id)throws Exception{for(int i=0;rows!=null&&i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;}
    static String version(JSONObject row)throws Exception{
        if(row==null)return PixelMcpWriteTools.hash(JSONObject.NULL);
        if(row.has("id")){String kind=row.has("name")?"customer":row.has("activity")?"worklog":row.has("title")?"order":row.has("invoice_number")||row.has("positions")||row.has("issued_at")?"invoice":"photo";return PixelMcpWriteTools.hash(DriveJournalRules.guard(row,kind));}
        return PixelMcpWriteTools.hash(DriveJournalRules.clean(row));
    }
    static void checkHash(JSONObject row,String expected)throws Exception{if(!version(row).equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der geprüfte Stand hat sich verändert. Beide Varianten bleiben erhalten.");}
    static void apply(JSONObject snapshot,JSONObject action,Map<String,JSONObject> all,JSONObject resolved)throws Exception{
        String kind=action.getString("kind");JSONObject p=action.getJSONObject("payload");
        if(kind.equals("business_settings")){
            JSONObject current=snapshot.optJSONObject("business_settings");if(current==null)throw new IllegalArgumentException("Fachliche Einstellungen fehlen im Gesamtstand. Aktualisierten Web-Nummernservice abgleichen.");
            if(!PixelMcpWriteTools.hash(current).equals(p.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Fachliche Einstellungen wurden geändert.");
            PixelMcpOwnerTools.validate("update_business_settings",new JSONObject().put("request_id","journal-settings").put("expected_record_hash",p.getString("expected_record_hash")).put("updates",p.getJSONObject("updates")));
            JSONObject updated=new JSONObject(current.toString()),changes=p.getJSONObject("updates");for(Iterator<String> keys=changes.keys();keys.hasNext();){String key=keys.next();Object value=changes.get(key);if(key.equals("invoice_sevdesk_checked_at")&&!value.toString().isEmpty())java.time.Instant.parse(value.toString());updated.put(key,value instanceof Boolean?((Boolean)value?"1":"0"):value.toString());}
            snapshot.put("business_settings",updated);JSONObject issuer=snapshot.optJSONObject("invoice_issuer");if(issuer==null)issuer=new JSONObject();for(String key:PixelMcpOwnerTools.BUSINESS_KEYS)if(!key.startsWith("invoice_"))issuer.put(key,updated.optString(key));snapshot.put("invoice_issuer",issuer);return;
        }
        if(kind.equals("conflict_resolution")){
            String target=p.getString("client_id");JSONObject prior=all.get(target);
            if(prior==null||!OrderReviewRules.actionHash(prior).equals(p.getString("expected_action_hash"))||resolved.has(target))throw new PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT","Die ursprüngliche Konfliktaktion wurde verändert oder bereits geprüft.");
            boolean legacyDelete="delete".equals(prior.getString("kind"));String entity=legacyDelete?prior.getJSONObject("payload").getString("entity"):prior.getString("kind"),collection=DriveJournalRules.collection(entity);if(collection.isEmpty())throw new IllegalArgumentException("Diesen Vorgang fachlich separat prüfen.");
            JSONObject patch=prior.getJSONObject("payload"),current=findOrNull(snapshot.getJSONArray(collection),patch.optString("id"));checkHash(current,p.getString("expected_current_hash"));
            JSONObject history=snapshot.optJSONObject("owner_conflict_history");if(history==null)history=new JSONObject();
            if(!p.optString("resolution").equals("keep_current")){
                if(!p.optString("resolution").equals("apply_proposed"))throw new IllegalArgumentException("Ungültige Konfliktentscheidung.");
                if(legacyDelete){if(current!=null){JSONObject archive=new JSONObject().put("entity",entity).put("id",current.getString("id")).put("expected_record_hash",version(current)).put("_record_base",DriveJournalRules.guard(current,entity)).put("_archive_record",current).put("updated_at",action.optString("created_at",java.time.Instant.now().toString()));apply(snapshot,new JSONObject().put("kind","archive").put("client_id",action.getString("client_id")+"-archive").put("created_at",action.optString("created_at")).put("payload",archive),all,resolved);}}
                else{JSONObject merged=current==null?new JSONObject():DriveJournalRules.clean(current);JSONObject clean=DriveJournalRules.clean(patch);
                    for(Iterator<String> keys=clean.keys();keys.hasNext();){String key=keys.next();merged.put(key,clean.get(key));}
                    DriveJournalRules.validateChange(entity,merged,current,snapshot);replace(snapshot,collection,merged.getString("id"),merged);}
            }
            history.put(target,new JSONObject().put("original_action",prior).put("previous",current==null?JSONObject.NULL:current).put("resolution",p.getString("resolution")).put("resolution_action",action.getString("client_id")));
            snapshot.put("owner_conflict_history",history);resolved.put(target,OrderReviewRules.actionHash(prior));return;
        }
        String entity=kind.equals("invoice_payment")?"invoice":p.getString("entity"),id=p.getString("id"),collection=group(entity);
        if(collection.isEmpty())throw new IllegalArgumentException("Unbekannter Archivbereich.");
        JSONArray rows=snapshot.getJSONArray(collection);JSONObject current=findOrNull(rows,id);
        if(kind.equals("invoice_payment")){
            checkHash(current,p.getString("expected_record_hash"));if(current==null||current.optString("issued_at").isEmpty()||"Storniert".equals(current.optString("status")))throw new IllegalArgumentException("Nur ausgestellte, gültige Rechnungen können als bezahlt/offen markiert werden.");
            JSONObject updated=new JSONObject(current.toString()).put("status",p.getBoolean("paid")?"Bezahlt":"Gestellt").put("updated_at",p.getString("updated_at"));replace(snapshot,collection,id,updated);return;
        }
        JSONObject archive=archives(snapshot);String key=entity+":"+id;
        if(kind.equals("restore")){
            JSONObject saved=archive.optJSONObject(key);if(saved==null)throw new IllegalArgumentException("Archiveintrag fehlt.");checkHash(saved,p.getString("expected_record_hash"));
            JSONObject original=saved.getJSONObject("record");
            if(entity.equals("customer")){if(current==null||current.optInt("active",1)!=0)throw new IllegalArgumentException("Der archivierte Kunde wurde inzwischen geändert.");checkHash(current,saved.getString("archived_record_hash"));}
            else if(current!=null)throw new IllegalArgumentException("Die Original-ID ist bereits belegt.");
            if(!entity.equals("invoice"))DriveJournalRules.validateChange(entity,original,null,snapshot);
            replace(snapshot,collection,id,original);archive.remove(key);return;
        }
        checkHash(current,p.getString("expected_record_hash"));if(current==null)throw new IllegalArgumentException("Der Eintrag fehlt.");
        validateArchive(entity,current,snapshot);
        if(archive.has(key))throw new IllegalArgumentException("Für diese ID besteht bereits ein Archivstand.");
        JSONObject saved=new JSONObject().put("record",current).put("archived_at",action.optString("created_at")).put("archive_action",action.getString("client_id"));
        if(entity.equals("customer")){JSONObject inactive=new JSONObject(current.toString()).put("active",0).put("updated_at",p.getString("updated_at"));replace(snapshot,collection,id,inactive);saved.put("archived_record_hash",version(inactive));}
        else replace(snapshot,collection,id,null);
        archive.put(key,saved);
    }
    static void validateArchive(String entity,JSONObject row,JSONObject snapshot)throws Exception{
        if(entity.equals("customer"))return; // Keep the row and all historic references.
        String id=row.getString("id");
        if(entity.equals("invoice"))EntityProtectionRules.validateInvoiceDelete(row);
        for(String collection:new String[]{"orders","worklogs","photos","invoices"}){
            JSONArray rows=snapshot.getJSONArray(collection);
            for(int i=0;i<rows.length();i++){JSONObject other=rows.getJSONObject(i);
                if(entity.equals("order")&&id.equals(other.optString("order_id"))||entity.equals("worklog")&&id.equals(other.optString("worklog_id")))throw new IllegalArgumentException("Verknüpfte Daten zuerst prüfen; nichts entfernt.");
                if(entity.equals("worklog")&&collection.equals("invoices")){JSONArray refs=other.optJSONArray("worklog_ids"),positions=other.optJSONArray("positions");for(int j=0;refs!=null&&j<refs.length();j++)if(id.equals(refs.optString(j)))throw new IllegalArgumentException("Die Arbeit ist mit einer Rechnung verknüpft.");for(int j=0;positions!=null&&j<positions.length();j++)if(id.equals(positions.getJSONObject(j).optString("worklog_id")))throw new IllegalArgumentException("Die Arbeit ist mit einer Rechnung verknüpft.");}
            }
        }
    }
    static void replace(JSONObject snapshot,String collection,String id,JSONObject replacement)throws Exception{
        JSONArray rows=snapshot.getJSONArray(collection),next=new JSONArray();boolean found=false;
        for(int i=0;i<rows.length();i++){JSONObject old=rows.getJSONObject(i);if(id.equals(old.optString("id"))){found=true;if(replacement!=null)next.put(replacement);}else next.put(old);}
        if(!found&&replacement!=null)next.put(replacement);snapshot.put(collection,next);
    }
}
