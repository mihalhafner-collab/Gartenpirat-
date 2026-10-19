package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Additive owner journal protocol. Old journals and immutable receipts survive. */
final class OwnerJournalRules {
    static final String[] KINDS={"purge_v1","purge_trash_v1","archive","restore","conflict_resolution","invoice_payment","business_settings","invoice_correction"};
    static boolean handles(String kind){return Arrays.asList(KINDS).contains(kind)||OfferJournalRules.handles(kind);}
    static JSONObject archives(JSONObject snapshot)throws Exception{JSONObject a=snapshot.optJSONObject("owner_archive");if(a==null){a=new JSONObject();snapshot.put("owner_archive",a);}return a;}
    static String group(String entity){return entity.equals("invoice")?"invoices":DriveJournalRules.collection(entity);}
    static JSONObject findOrNull(JSONArray rows,String id)throws Exception{for(int i=0;rows!=null&&i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;}
    static String version(JSONObject row)throws Exception{
        if(row==null)return PixelMcpWriteTools.hash(JSONObject.NULL);
        if(row.has("id")){if(row.has("offer_number")||row.has("pricing_mode")||(row.has("unit")&&row.has("default_price")))return PixelMcpWriteTools.hash(DriveJournalRules.clean(row));String kind=row.has("name")?"customer":row.has("activity")?"worklog":row.has("title")?"order":row.has("invoice_number")||row.has("positions")||row.has("issued_at")?"invoice":"photo";return PixelMcpWriteTools.hash(DriveJournalRules.guard(row,kind));}
        return PixelMcpWriteTools.hash(DriveJournalRules.clean(row));
    }
    static void checkHash(JSONObject row,String expected)throws Exception{if(!version(row).equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der geprüfte Stand hat sich verändert. Beide Varianten bleiben erhalten.");}
    static void apply(JSONObject snapshot,JSONObject action,Map<String,JSONObject> all,JSONObject resolved)throws Exception{
        String kind=action.getString("kind");JSONObject p=action.getJSONObject("payload");
        if(kind.equals(TrashPurgeBatch.KIND)){TrashPurgeBatch.apply(snapshot,action);return;}
        if(kind.equals("purge_v1")){TrashRules.purge(snapshot,action);return;}
        if(kind.equals("invoice_correction")){InvoiceIssueRules.correction(snapshot,action);return;}
        if(OfferJournalRules.handles(kind)){OfferJournalRules.apply(snapshot,action);return;}
        if(kind.equals("business_settings")){
            JSONObject current=snapshot.optJSONObject("business_settings");if(current==null)throw new IllegalArgumentException("Fachliche Einstellungen fehlen; Pixel-Datenstand erneut prüfen.");
            if(!PixelMcpWriteTools.hash(current).equals(p.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Fachliche Einstellungen wurden geändert.");
            PixelMcpOwnerTools.validate("update_business_settings",new JSONObject().put("request_id","journal-settings").put("expected_record_hash",p.getString("expected_record_hash")).put("updates",p.getJSONObject("updates")));
            JSONObject updated=new JSONObject(current.toString()),changes=p.getJSONObject("updates");for(Iterator<String> keys=changes.keys();keys.hasNext();){String key=keys.next();Object value=changes.get(key);if((key.equals("invoice_sevdesk_checked_at")||key.equals("invoice_numbering_checked_at"))&&!value.toString().isEmpty())java.time.Instant.parse(value.toString());updated.put(key,value instanceof Boolean?((Boolean)value?"1":"0"):value.toString());}
            snapshot.put("business_settings",updated);JSONObject issuer=snapshot.optJSONObject("invoice_issuer");if(issuer==null)issuer=new JSONObject();for(String key:PixelMcpOwnerTools.BUSINESS_KEYS)if(!key.startsWith("invoice_"))issuer.put(key,updated.optString(key));snapshot.put("invoice_issuer",issuer);return;
        }
        if(kind.equals("conflict_resolution")){
            String target=p.getString("client_id");JSONObject prior=all.get(target),embedded=p.optJSONObject("_original_action");
            if(prior==null){
                boolean knownFailure=false;
                for(String key:new String[]{"sync_errors","drive_journal_errors"}){JSONArray failures=snapshot.optJSONArray(key);for(int i=0;failures!=null&&i<failures.length();i++)if(target.equals(failures.getJSONObject(i).optString("client_id"))){knownFailure=true;break;}}
                if(knownFailure)prior=embedded;
            }
            if(prior==null||!target.equals(prior.optString("client_id"))||!OrderReviewRules.actionHash(prior).equals(p.getString("expected_action_hash"))||resolved.has(target))throw new PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT","Die ursprüngliche Konfliktaktion wurde verändert, ist nicht mehr nachweisbar oder bereits geprüft.");
            if(embedded!=null&&!OrderReviewRules.actionHash(embedded).equals(p.getString("expected_action_hash")))throw new PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT","Der eingebettete Konfliktbeleg stimmt nicht mit dem geprüften Hash überein.");
            for(String errorKey:new String[]{"sync_errors","drive_journal_errors"}){JSONArray failures=snapshot.optJSONArray(errorKey);for(int i=0;failures!=null&&i<failures.length();i++){JSONObject failure=failures.getJSONObject(i);if(target.equals(failure.optString("client_id"))&&!Arrays.asList("WRITE_CONFLICT","DELETE_REVIEW_REQUIRED").contains(failure.optString("code"))&&!("keep_current".equals(p.optString("resolution"))&&DeletionRules.cancelable(prior,failure.optString("code"))))throw new PixelMcpWriteTools.Rejected("CONFLICT_RESOLUTION_NOT_AVAILABLE","Infrastrukturfehler darf nicht als Geschäftsentscheidung quittiert werden.");}}
            boolean legacyDelete="delete".equals(prior.getString("kind")),purgeCancel="purge_v1".equals(prior.getString("kind")),bulkCancel=TrashPurgeBatch.KIND.equals(prior.getString("kind")),archiveCancel="archive".equals(prior.getString("kind"))||purgeCancel||bulkCancel;if(archiveCancel&&!"keep_current".equals(p.optString("resolution")))throw new PixelMcpWriteTools.Rejected("CONFLICT_RESOLUTION_NOT_AVAILABLE","Löschauftrag nur zurücknehmen oder anhand des aktuellen Stands neu prüfen.");String entity=bulkCancel?"":(legacyDelete||archiveCancel)?prior.getJSONObject("payload").getString("entity"):prior.getString("kind"),collection=DriveJournalRules.collection(entity);if(!bulkCancel&&collection.isEmpty())throw new IllegalArgumentException("Diesen Vorgang fachlich separat prüfen.");
            JSONObject patch=prior.getJSONObject("payload"),current=bulkCancel?TrashPurgeBatch.currentGuard(snapshot):purgeCancel?archives(snapshot).optJSONObject(entity+":"+patch.optString("id")):findOrNull(snapshot.getJSONArray(collection),patch.optString("id"));checkHash(current,p.getString("expected_current_hash"));
            JSONObject history=snapshot.optJSONObject("owner_conflict_history");if(history==null)history=new JSONObject();
            if(!p.optString("resolution").equals("keep_current")){
                if(!p.optString("resolution").equals("apply_proposed"))throw new IllegalArgumentException("Ungültige Konfliktentscheidung.");
                if(legacyDelete){if(current!=null){JSONObject archive=new JSONObject().put("entity",entity).put("id",current.getString("id")).put("expected_record_hash",version(current)).put("_record_base",DriveJournalRules.guard(current,entity)).put("_archive_record",current).put("updated_at",action.optString("created_at",java.time.Instant.now().toString()));apply(snapshot,new JSONObject().put("kind","archive").put("client_id",action.getString("client_id")+"-archive").put("created_at",action.optString("created_at")).put("payload",archive),all,resolved);}}
                else{JSONObject merged=current==null?new JSONObject():DriveJournalRules.clean(current);JSONObject clean=DriveJournalRules.clean(patch);
                    for(Iterator<String> keys=clean.keys();keys.hasNext();){String key=keys.next();merged.put(key,clean.get(key));}
                    DriveJournalRules.validateChange(entity,merged,current,snapshot);replace(snapshot,collection,merged.getString("id"),merged);}
            }
            history.put(target,new JSONObject().put("original_action",prior).put("previous",current==null?JSONObject.NULL:current).put("resolution",p.getString("resolution")).put("action_hash",p.getString("expected_action_hash")).put("reason",p.optString("reason","explicit_owner_decision")).put("resolved_at",action.optString("created_at")).put("resolution_action",action.getString("client_id")));
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
            if(saved.optBoolean("purged"))throw new IllegalArgumentException("Endgültig gelöschte Einträge sind nicht wiederherstellbar.");
            JSONObject original=saved.getJSONObject("record");
            if(entity.equals("customer")||entity.equals("service_template")){if(current==null||(current.optBoolean("active",true)&&current.optInt("active",1)!=0))throw new IllegalArgumentException("Der archivierte Kunde wurde inzwischen geändert.");checkHash(current,saved.getString("archived_record_hash"));}
            else if(current!=null)throw new IllegalArgumentException("Die Original-ID ist bereits belegt.");
            if(!entity.equals("invoice"))DriveJournalRules.validateChange(entity,original,null,snapshot);
            replace(snapshot,collection,id,original);TrashRules.restoreLinks(snapshot,saved);archive.remove(key);return;
        }
        checkHash(current,p.getString("expected_record_hash"));if(current==null)throw new IllegalArgumentException("Der Eintrag fehlt.");
        validateArchive(entity,current,snapshot);
        if(archive.has(key))throw new IllegalArgumentException("Für diese ID besteht bereits ein Archivstand.");
        JSONObject saved=new JSONObject().put("record",current).put("archived_at",action.optString("created_at")).put("archive_action",action.getString("client_id"));
        if(p.has("deletion_policy"))saved.put("deletion_policy",p.getString("deletion_policy"));
        JSONArray related=TrashRules.related(snapshot,entity,id);
        if("recoverable_v2".equals(p.optString("deletion_policy"))&&!PixelMcpWriteTools.hash(related).equals(p.getString("expected_photo_links_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Fotozuordnungen inzwischen geändert.");
        if(related.length()>0&&(entity.equals("order")||entity.equals("worklog")||"recoverable_v2".equals(p.optString("deletion_policy")))){
            if(!"recoverable_v2".equals(p.optString("deletion_policy")))throw new IllegalArgumentException("Fotozuordnungen mit aktuellem Löschdialog erneut prüfen; alte Aktion bleibt erhalten.");
            if(!PixelMcpWriteTools.hash(related).equals(p.getString("expected_photo_links_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Fotozuordnungen inzwischen geändert. Nichts entfernt.");
            saved.put("photo_links",TrashRules.detachRelated(snapshot,entity,id,p.getString("updated_at")));
        }
        if(entity.equals("customer")||entity.equals("service_template")){JSONObject inactive=new JSONObject(current.toString()).put("active",entity.equals("customer")?0:false).put("updated_at",p.getString("updated_at"));replace(snapshot,collection,id,inactive);saved.put("archived_record_hash",version(inactive));}
        else replace(snapshot,collection,id,null);
        archive.put(key,saved);
    }
    static void validateArchive(String entity,JSONObject row,JSONObject snapshot)throws Exception{
        if(entity.equals("customer")){
            for(String group:new String[]{"customers","orders","worklogs"}){JSONArray records=snapshot.optJSONArray(group);for(int i=0;records!=null&&i<records.length();i++){JSONObject r=records.getJSONObject(i);String key=group.equals("customers")?"default_billing_customer_id":"billing_customer_id";if(row.getString("id").equals(r.optString(key))&&!row.getString("id").equals(r.optString(group.equals("customers")?"id":"customer_id")))throw new IllegalArgumentException("Dieser Kunde wird als Rechnungsempfänger verwendet. Zuerst die Abrechnungszuordnungen prüfen.");}}
            return; // Keep the row and all historic references.
        }
        if(entity.equals("offer")&&!"draft".equals(row.optString("status")))throw new IllegalArgumentException("Finalisierte oder angenommene Angebote bleiben als Beleg erhalten.");
        if(entity.equals("service_template"))return; // Inactive, never remove historical price references.
        String id=row.getString("id");
        if(entity.equals("invoice"))EntityProtectionRules.validateInvoiceDelete(row);
        for(String collection:new String[]{"orders","worklogs","photos","invoices","offers"}){
            JSONArray rows=snapshot.getJSONArray(collection);
            for(int i=0;i<rows.length();i++){JSONObject other=rows.getJSONObject(i);
                if(!collection.equals("photos")&&(entity.equals("order")&&id.equals(other.optString("order_id"))||entity.equals("worklog")&&id.equals(other.optString("worklog_id"))))throw new IllegalArgumentException("Verknüpfte Daten zuerst prüfen; nichts entfernt.");
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
