package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Existing Drive snapshot + action journals. No new database or replacement IDs. */
final class DriveJournalRules {
    static final String[] KINDS={"customer","order","worklog","photo"};
    static String collection(String kind) {
        switch(kind){case "customer":return "customers";case "order":return "orders";case "worklog":return "worklogs";case "photo":return "photos";default:return "";}
    }
    static void validateSnapshot(JSONObject snapshot)throws Exception{
        for(String key:new String[]{"customers","orders","worklogs","invoices","photos"}){
            JSONArray rows=snapshot.optJSONArray(key);if(rows==null)throw new IllegalArgumentException("Der Drive-Gesamtstand ist unvollständig. Lokale Daten bleiben erhalten.");
            Set<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||row.optString("id").isEmpty()||!ids.add(row.getString("id")))throw new IllegalArgumentException("Ungültige oder doppelte IDs im Drive-Gesamtstand. Abgleich angehalten.");}
        }
    }
    static JSONArray union(JSONArray... packages)throws Exception{
        LinkedHashMap<String,JSONObject> actions=new LinkedHashMap<>();
        for(JSONArray source:packages)for(int i=0;source!=null&&i<source.length();i++){
            JSONObject action=source.optJSONObject(i);if(action==null||action.optString("client_id").isEmpty()||action.optString("kind").isEmpty()||action.optJSONObject("payload")==null)throw new IllegalArgumentException("Ungültige Drive-Änderungsdatei. Sie wurde nicht überschrieben.");
            String id=action.getString("client_id");JSONObject old=actions.get(id);
            if(old!=null&&!PixelMcpWriteTools.hash(old).equals(PixelMcpWriteTools.hash(action)))throw new PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT","Eine Änderungskennung enthält verschiedene Angaben. Keine Datei überschrieben.");
            if(old==null)actions.put(id,new JSONObject(action.toString()));
        }
        List<JSONObject> ordered=new ArrayList<>(actions.values());
        ordered.sort(Comparator.comparingLong(DriveJournalRules::created));
        JSONArray out=new JSONArray();for(JSONObject action:ordered)out.put(action);return out;
    }
    private static long created(JSONObject action){try{return java.time.Instant.parse(action.optString("created_at")).toEpochMilli();}catch(Exception ignored){try{return java.time.OffsetDateTime.parse(action.optString("created_at")).toInstant().toEpochMilli();}catch(Exception missing){return 0;}}}
    static JSONObject guard(JSONObject row,String kind)throws Exception{
        JSONObject base=new JSONObject();if(row==null)return base;
        String[] keys=kind.equals("worklog")?PixelMcpWriteTools.BASE_FIELDS:kind.equals("customer")?
            new String[]{"id","updated_at","name","first_name","last_name","company","street","postal_code","city","country","email","phone","notes","active","garden_area","sevdesk_customer_number","custom_fields","billing_mode","default_billing_customer_id"}:
            kind.equals("order")?new String[]{"id","updated_at","customer_id","title","description","location","scheduled_date","status","notes","customer_visible","billing_customer_id"}:
            new String[]{"id","customer_id","order_id","worklog_id","filename","caption","customer_visible"};
        for(String key:keys)base.put(key,key.equals("custom_fields")?comparable(key,row.opt(key)):row.has(key)?row.get(key):JSONObject.NULL);return base;
    }
    static JSONObject clean(JSONObject value)throws Exception{
        JSONObject out=new JSONObject(value.toString());for(Iterator<String> keys=value.keys();keys.hasNext();){String key=keys.next();if(key.startsWith("_")||key.equals("pending")||key.equals("allow_duplicate")||key.equals("require_order"))out.remove(key);}return out;
    }
    static Object comparable(String key,Object value)throws Exception{
        if(key.equals("custom_fields"))return value==null||value==JSONObject.NULL||"".equals(value)?new JSONObject():value instanceof String?new JSONObject((String)value):value;
        if(Arrays.asList("hours","break_minutes","kilometers","billed","customer_visible","active","garden_area").contains(key)){
            if(value==null||value==JSONObject.NULL)return 0;
            if(value instanceof Boolean)return ((Boolean)value)?1:0;
            if(value.toString().isEmpty())return 0;
            return new java.math.BigDecimal(value.toString()).stripTrailingZeros();
        }
        if(value==null||value==JSONObject.NULL)return "";
        return value;
    }
    static void compare(JSONObject expected,JSONObject current)throws Exception{
        if(current==null||!expected.optString("id").equals(current.optString("id")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der ursprüngliche Datensatz fehlt. Keine Wiederanlage vorgenommen.");
        for(Iterator<String> keys=expected.keys();keys.hasNext();){String key=keys.next();if(!PixelMcpWriteTools.canonical(comparable(key,expected.get(key))).equals(PixelMcpWriteTools.canonical(comparable(key,current.opt(key)))))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der Datensatz wurde parallel geändert. Bitte die Änderung prüfen.");}
    }
    static Set<String> ids(JSONArray values){Set<String> ids=new HashSet<>();for(int i=0;values!=null&&i<values.length();i++)ids.add(values.optString(i));return ids;}
    /** Read-only classification. Never clears an action or pretends provider delivery is cloud proof. */
    static JSONObject queueStatus(JSONObject snapshot,JSONArray pending)throws Exception{
        Set<String> delivered=ids(snapshot.optJSONArray("drive_delivered_ids"));Map<String,JSONObject> errors=new HashMap<>();
        for(String key:new String[]{"sync_errors","drive_journal_errors"}){JSONArray rows=snapshot.optJSONArray(key);for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)errors.put(row.optString("client_id"),row);}}
        int upload=0,review=0;JSONArray items=new JSONArray();
        for(int i=0;pending!=null&&i<pending.length();i++){
            JSONObject action=pending.getJSONObject(i);String id=action.optString("client_id"),kind=action.optString("kind");JSONObject error=errors.get(id);
            boolean transferred=delivered.contains(id),held=collection(kind).isEmpty();
            String phase,message;
            if(error!=null){review++;phase=error.optString("code").contains("CONFLICT")?"sync_conflict":"sync_failed";message="Prüfung erforderlich: "+(error.optString("code").contains("CONFLICT")?"Widersprüchliche Änderungen; vorhandene Daten wurden nicht überschrieben.":"Die Änderung konnte nicht übernommen werden.");}
            else if(held&&transferred){review++;phase="review_required";message=kind.equals("delete")?"An die Drive-App übergeben · Löschauftrag bleibt bis zur gesonderten Prüfung zurückgehalten.":"An die Drive-App übergeben · geschützter Vorgang benötigt Prüfung.";}
            else{upload++;phase="sync_pending";message="Lokal gespeichert · Übertragung ausstehend.";}
            items.put(new JSONObject().put("client_id",id).put("state",phase).put("provider_readback",transferred).put("cloud_confirmed",false).put("message",message));
        }
        return new JSONObject().put("total",pending==null?0:pending.length()).put("pending_upload",upload).put("review",review).put("items",items);
    }
    static JSONArray apply(JSONObject snapshot,JSONArray actions)throws Exception{
        validateSnapshot(snapshot);Set<String> accepted=ids(snapshot.optJSONArray("accepted_ids")),seen=ids(snapshot.optJSONArray("drive_applied_ids"));
        JSONArray errors=new JSONArray();JSONObject hashes=snapshot.optJSONObject("drive_action_hashes");if(hashes==null)hashes=new JSONObject();
        for(int i=0;i<actions.length();i++){
            JSONObject action=actions.getJSONObject(i);String client=action.getString("client_id"),kind=action.getString("kind"),group=collection(kind);
            String digest=PixelMcpWriteTools.hash(action);
            if(hashes.has(client)&&!digest.equals(hashes.getString(client)))throw new PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT","Eine bestätigte Änderungskennung wurde verändert. Abgleich angehalten.");
            if(accepted.contains(client)||seen.contains(client))continue;
            // Invoice issuing and historical deletes keep their existing guarded laptop workflow.
            if(group.isEmpty())continue;
            try{
                JSONObject patch=action.getJSONObject("payload");JSONArray rows=new JSONArray(snapshot.getJSONArray(group).toString());
                String id=patch.optString("id");if(id.isEmpty())id=kind+"_sync_"+hashString(client).substring(0,32);
                JSONObject old=null;int at=-1;for(int j=0;j<rows.length();j++)if(id.equals(rows.getJSONObject(j).optString("id"))){old=rows.getJSONObject(j);at=j;break;}
                JSONObject expected=patch.optJSONObject("_sync_base");if(expected==null)expected=patch.optJSONObject("_mcp_base");
                if(patch.optBoolean("_mcp_create")&&old!=null)throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Die neue ID existiert bereits; keine Überschreibung.");
                if(expected!=null)compare(expected,old);
                JSONObject merged=old==null?new JSONObject():new JSONObject(old.toString());JSONObject sanitized=clean(patch);
                for(Iterator<String> keys=sanitized.keys();keys.hasNext();){String key=keys.next();merged.put(key,sanitized.get(key));}merged.put("id",id);
                if(old!=null&&expected==null&&!PixelMcpWriteTools.hash(merged).equals(PixelMcpWriteTools.hash(old)))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Ältere Änderung ohne Vergleichsstand. Bitte manuell prüfen; vorhandene Daten bleiben erhalten.");
                if(old==null&&kind.equals("customer")&&patch.optBoolean("allow_duplicate"))merged.put("allow_duplicate",true);
                validateChange(kind,merged,old,snapshot);merged.remove("allow_duplicate");
                if(at<0)rows.put(merged);else rows.put(at,merged);snapshot.put(group,rows);
                seen.add(client);hashes.put(client,digest);
            }catch(Exception error){errors.put(new JSONObject().put("client_id",client).put("kind",kind).put("code",error instanceof PixelMcpWriteTools.Rejected?((PixelMcpWriteTools.Rejected)error).code:"INVALID_ACTION").put("error",error.getMessage()));}
        }
        refreshDisplayFields(snapshot);
        snapshot.put("drive_applied_ids",new JSONArray(seen)).put("drive_action_hashes",hashes).put("drive_journal_errors",errors);return errors;
    }
    private static void refreshDisplayFields(JSONObject snapshot)throws Exception{
        Map<String,String> names=new HashMap<>(),titles=new HashMap<>();Map<String,Double> totals=new HashMap<>();
        JSONArray customers=snapshot.getJSONArray("customers"),orders=snapshot.getJSONArray("orders"),logs=snapshot.getJSONArray("worklogs");
        for(int i=0;i<customers.length();i++){JSONObject row=customers.getJSONObject(i);names.put(row.getString("id"),row.optString("name"));}
        for(int i=0;i<orders.length();i++){JSONObject row=orders.getJSONObject(i);titles.put(row.getString("id"),row.optString("title"));}
        for(int i=0;i<logs.length();i++){JSONObject row=logs.getJSONObject(i);String order=row.optString("order_id");totals.put(order,totals.getOrDefault(order,0.0)+row.optDouble("hours",0));}
        for(String group:new String[]{"orders","worklogs","photos","invoices"}){JSONArray rows=snapshot.getJSONArray(group);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.has("customer_name"))row.put("customer_name",names.getOrDefault(row.optString("customer_id"),row.optString("customer_name")));if(row.has("order_title"))row.put("order_title",titles.getOrDefault(row.optString("order_id"),""));if(group.equals("orders")&&row.has("total_hours"))row.put("total_hours",Math.round(totals.getOrDefault(row.optString("id"),0.0)*100)/100.0);}}
    }
    private static String hashString(String value)throws Exception{
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static void validateChange(String kind,JSONObject row,JSONObject old,JSONObject snapshot)throws Exception{
        JSONArray customers=snapshot.getJSONArray("customers"),orders=snapshot.getJSONArray("orders"),logs=snapshot.getJSONArray("worklogs"),photos=snapshot.getJSONArray("photos"),invoices=snapshot.getJSONArray("invoices");
        if(kind.equals("customer")){
            BillingRules.customerConfig(row,customers);if(row.optString("name").trim().isEmpty())throw new IllegalArgumentException("Der Kundenname fehlt.");if(old==null){EntityProtectionRules.validateNewCustomer(row,customers);if(!row.has("active"))row.put("active",1);if(!row.has("custom_fields"))row.put("custom_fields",new JSONObject());}return;}
        PixelMcpWriteTools.find(customers,row.optString("customer_id"));String orderId=row.isNull("order_id")?"":row.optString("order_id");
        if(!orderId.isEmpty()&&!row.optString("customer_id").equals(PixelMcpWriteTools.find(orders,orderId).optString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
        if(kind.equals("order")){
            BillingRules.order(row,snapshot);if(row.optString("title").trim().isEmpty())throw new IllegalArgumentException("Auftragsbezeichnung fehlt.");if(old==null){if(row.optString("status").isEmpty())row.put("status","Geplant");if(!row.has("customer_visible"))row.put("customer_visible",1);}if(!row.optString("scheduled_date").isEmpty()&&!row.isNull("scheduled_date"))java.time.LocalDate.parse(row.getString("scheduled_date"));EntityProtectionRules.validateOrderChange(row,old,logs,invoices,photos);}
        if(kind.equals("worklog")){if(row.optString("activity").trim().isEmpty()||row.optString("work_date").isEmpty())throw new IllegalArgumentException("Datum und Tätigkeit sind erforderlich.");BillingRules.work(row,old==null&&!row.has("billing_customer_id")?new JSONObject().put("customer_id",row.getString("customer_id")):old,snapshot);double hours=row.optDouble("hours",0);WorklogRules.validate(row,old==null);if(Math.abs(hours-row.optDouble("hours"))>0.005)throw new IllegalArgumentException("Stunden und Uhrzeiten widersprechen sich.");WorklogRelationRules.validate(row,old,photos,invoices);}
        if(kind.equals("photo"))EntityProtectionRules.validatePhoto(row,customers,orders,logs);
    }
}
