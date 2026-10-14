package at.hausgarten.service;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Narrow write adapter. Storage, transactions and Drive remain owned by the existing app. */
final class PixelMcpWriteTools {
    interface Sink { JSONObject execute(String tool, JSONObject args) throws Exception; JSONObject status(String id, JSONObject state) throws Exception; }
    static final String[] NAMES = {"create_work_record", "create_customer_work_record", "update_work_record"};
    static final String[] EDITABLE = {"billing_customer_id","order_id","work_date","activity","hours","start_time","end_time","break_minutes","material","material_cost","kilometers","notes","location","customer_visible","allow_overnight"};
    static final String[] BASE_FIELDS = {"id","customer_id","billing_customer_id","order_id","work_date","activity","hours","start_time","end_time","break_minutes","material","kilometers","notes","location","customer_visible","billed","updated_at"};
    static boolean handles(String name) { return Arrays.asList(NAMES).contains(name); }
    static class Rejected extends IllegalArgumentException {
        final String code;
        Rejected(String code, String message) { super(message); this.code = code; }
    }
    static JSONObject field(String key) throws Exception {
        String type = key.equals("hours") || key.equals("kilometers") || key.equals("material_cost") ? "number" : key.equals("break_minutes") ? "integer" : key.equals("customer_visible") || key.equals("allow_overnight") ? "boolean" : "string";
        JSONObject spec = new JSONObject().put("type", type);
        if (type.equals("string")) spec.put("maxLength", key.equals("activity") || key.equals("material") || key.equals("notes") ? 10000 : 500);
        if (type.equals("number") || type.equals("integer")) spec.put("minimum", 0);
        return spec;
    }
    static JSONObject schema(String name) throws Exception {
        JSONObject props = new JSONObject().put("request_id", new JSONObject().put("type","string").put("minLength",8).put("maxLength",128));
        JSONArray required = new JSONArray().put("request_id");
        if (name.equals("update_work_record")) {
            props.put("work_record_id",field("work_record_id")).put("expected_record_hash",field("expected_record_hash"));
            JSONObject patch = new JSONObject(); for (String key : EDITABLE) patch.put(key,field(key));
            props.put("updates",new JSONObject().put("type","object").put("properties",patch).put("additionalProperties",false).put("minProperties",1));
            required.put("work_record_id").put("expected_record_hash").put("updates");
        } else {
            for (String key : EDITABLE) props.put(key,field(key));
            props.put("customer_id",field("customer_id"));
            required.put(name.equals("create_work_record") ? "order_id" : "customer_id").put("work_date").put("activity");
        }
        return new JSONObject().put("type","object").put("properties",props).put("required",required).put("additionalProperties",false);
    }
    static JSONArray tools() throws Exception {
        JSONArray result = new JSONArray();
        for (String name : NAMES) result.put(new JSONObject().put("name",name).put("inputSchema",schema(name))
            .put("description",name.equals("update_work_record") ? "Korrigiert eine Arbeitsaufzeichnung. Zuerst get_work_record aufrufen und record_hash als expected_record_hash übernehmen. Keine Änderung von Kunden-ID, Abrechnung oder Rechnungen."
                : "Legt eine Arbeitsaufzeichnung mit tatsächlichen Kunden-/Auftrags-IDs an. request_id bei Wiederholung unverändert lassen. Erst lokal gesichert und im mobilen Drive-Abgleich vorgemerkt; get_sync_status prüft die spätere Laptop-Bestätigung.")
            .put("annotations",new JSONObject().put("readOnlyHint",false).put("destructiveHint",false).put("idempotentHint",true).put("openWorldHint",false)));
        return result;
    }
    static void validate(JSONObject args, JSONObject schema) throws Exception {
        JSONObject props = schema.getJSONObject("properties");
        for (Iterator<String> it=args.keys();it.hasNext();) {
            String key=it.next(); if (!props.has(key)) throw new Rejected("INVALID_INPUT","Diese Angabe ist nicht erlaubt: "+key);
            Object value=args.get(key); JSONObject spec=props.getJSONObject(key); String type=spec.getString("type");
            boolean valid=type.equals("string") ? value instanceof String : type.equals("boolean") ? value instanceof Boolean : type.equals("object") ? value instanceof JSONObject : value instanceof Number;
            if (!valid) throw new Rejected("INVALID_INPUT","Ungültiger Eingabetyp: "+key);
            if (value instanceof String && (((String)value).length()>spec.optInt("maxLength",10000) || ((String)value).length()<spec.optInt("minLength",0))) throw new Rejected("INVALID_INPUT","Bitte die Länge der Angabe prüfen: "+key);
            if (value instanceof Number) { double n=((Number)value).doubleValue(); if (!Double.isFinite(n)||n<0||(type.equals("integer")&&n!=Math.floor(n))) throw new Rejected("INVALID_INPUT","Bitte eine gültige nicht negative Zahl eingeben: "+key); }
            if (type.equals("object")) { if (((JSONObject)value).length()==0) throw new Rejected("INVALID_INPUT","Bitte mindestens eine Änderung angeben."); validate((JSONObject)value,spec); }
        }
        JSONArray required=schema.optJSONArray("required");
        for(int i=0;required!=null&&i<required.length();i++) { String key=required.getString(i); if(!args.has(key)||(args.get(key) instanceof String && args.getString(key).trim().isEmpty())) throw new Rejected("MISSING_FIELD","Pflichtangabe fehlt: "+key); }
    }
    static String canonical(Object value) throws Exception {
        if (value instanceof JSONObject) { JSONObject obj=(JSONObject)value; List<String> keys=new ArrayList<>();obj.keys().forEachRemaining(keys::add);Collections.sort(keys);StringJoiner out=new StringJoiner(",","{","}");for(String key:keys)out.add(JSONObject.quote(key)+":"+canonical(obj.get(key)));return out.toString(); }
        if (value instanceof JSONArray) { StringJoiner out=new StringJoiner(",","[","]");JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)out.add(canonical(a.get(i)));return out.toString(); }
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof Number)return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        return value instanceof String ? JSONObject.quote((String)value) : value.toString();
    }
    static String hash(Object value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static JSONObject base(JSONObject row) throws Exception { JSONObject result=new JSONObject();for(String key:BASE_FIELDS)if(row.has(key))result.put(key,row.get(key));return result; }
    static void applyMaterialCost(JSONObject payload)throws Exception{
        if(!payload.has("material_cost"))return;
        java.math.BigDecimal cost=new java.math.BigDecimal(payload.get("material_cost").toString());
        if(cost.scale()>2||cost.signum()<0)throw new Rejected("INVALID_INPUT","Materialkosten müssen nicht negativ sein und höchstens zwei Nachkommastellen haben.");
        String material=payload.optString("material").replaceAll("(?m)^Materialkosten \\[EUR\\]: [0-9]+(?:\\.[0-9]{1,2})?$","").trim();
        payload.put("material",(material.isEmpty()?"":material+"\n")+"Materialkosten [EUR]: "+cost.setScale(2).toPlainString());payload.remove("material_cost");
    }
    static JSONObject find(JSONArray rows,String id) throws Exception {
        for(int i=0;rows!=null&&i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);
        throw new Rejected("REFERENCE_NOT_FOUND","Die Datensatz-ID existiert nicht. Bitte den Kunden, Auftrag oder Eintrag zuerst suchen.");
    }
    static JSONObject prepare(String name,JSONObject args,JSONObject state,String actionId) throws Exception {
        validate(args,schema(name));
        JSONObject master=PixelMcpReadTools.effective(state),payload; boolean update=name.equals("update_work_record");
        if(update) {
            JSONObject capabilities=master.optJSONObject("capabilities");
            if(capabilities==null || !Boolean.TRUE.equals(capabilities.opt("worklog_compare_and_save")))
                throw new Rejected("REMOTE_GUARD_REQUIRED","Der Laptop hat den neuen Änderungsschutz noch nicht bestätigt. Bitte die aktuelle Laptop-App neu starten und mit Drive abgleichen; danach die Pixel-App abgleichen.");
            JSONObject old=find(master.optJSONArray("worklogs"),args.getString("work_record_id"));
            if(!hash(base(old)).equals(args.getString("expected_record_hash")))throw new Rejected("WRITE_CONFLICT","Der Eintrag wurde inzwischen geändert. Bitte erneut öffnen und die Änderung prüfen.");
            JSONArray pending=state.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.optJSONObject("payload");if(p!=null&&old.getString("id").equals(p.optString("id")))throw new Rejected("WRITE_CONFLICT","Für diesen Eintrag ist bereits eine Änderung vorgemerkt. Bitte zuerst abgleichen.");}
            JSONObject patch=args.getJSONObject("updates");
            // Issued/billed service data is immutable; private notes may still be supplemented.
            boolean invoiced=old.optInt("billed",0)!=0; JSONArray invoices=master.optJSONArray("invoices");
            for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject inv=invoices.getJSONObject(i);JSONArray ids=inv.optJSONArray("worklog_ids");for(int j=0;ids!=null&&j<ids.length();j++)if(old.getString("id").equals(ids.optString(j)))invoiced=true;}
            if(invoiced)for(Iterator<String> it=patch.keys();it.hasNext();)if(!it.next().equals("notes"))throw new Rejected("BILLED_RECORD_LOCKED","Abgerechnete Leistungsdaten bleiben unverändert. Nur interne Notizen können ergänzt werden.");
            payload=new JSONObject(old.toString());for(Iterator<String> it=patch.keys();it.hasNext();){String key=it.next();payload.put(key,patch.get(key));}
            payload.remove("_mcp_create");
            payload.put("_mcp_base",base(old));
        }else {
            payload=new JSONObject(args.toString());payload.remove("request_id");
            String orderId=payload.optString("order_id").trim(),customerId=payload.optString("customer_id").trim();
            if(!orderId.isEmpty()){JSONObject order=find(master.optJSONArray("orders"),orderId);if(!customerId.isEmpty()&&!customerId.equals(order.optString("customer_id")))throw new Rejected("REFERENCE_MISMATCH","Kunde und Auftrag passen nicht zusammen.");customerId=order.optString("customer_id");payload.put("customer_id",customerId);}
            payload.put("id","wrk_mobile_mcp_"+hash(actionId).substring(0,32)).put("order_id",orderId).put("billed",0).put("source","ChatGPT Sprache").put("_mcp_create",true);
            JSONArray existing=master.optJSONArray("worklogs");
            for(int i=0;existing!=null&&i<existing.length();i++)if(payload.getString("id").equals(existing.getJSONObject(i).optString("id")))
                throw new Rejected("ID_ALREADY_EXISTS","Diese Anfrage gehört zu einem bereits vorhandenen Eintrag. Bitte den Eintrag prüfen statt ihn erneut anzulegen.");
        }
        String customerId=payload.optString("customer_id").trim(),orderId=payload.isNull("order_id")?"":payload.optString("order_id").trim();
        payload.put("order_id",orderId);
        find(master.optJSONArray("customers"),customerId);
        if(!orderId.isEmpty()){JSONObject order=find(master.optJSONArray("orders"),orderId);if(!customerId.equals(order.optString("customer_id")))throw new Rejected("REFERENCE_MISMATCH","Kunde und Auftrag passen nicht zusammen.");if(payload.optString("location").trim().isEmpty())payload.put("location",order.optString("location"));}
        if(payload.optString("activity").trim().isEmpty())throw new Rejected("MISSING_FIELD","Die Tätigkeit fehlt.");
        if(payload.has("customer_visible")&&payload.get("customer_visible") instanceof Boolean)payload.put("customer_visible",payload.getBoolean("customer_visible")?1:0);
        BillingRules.work(payload,update?find(master.optJSONArray("worklogs"),payload.getString("id")):null,master);
        applyMaterialCost(payload);WorklogRules.validate(payload,true);
        JSONObject submitted=update?args.getJSONObject("updates"):args;
        if(submitted.has("hours")&&!payload.isNull("start_time")&&!payload.optString("start_time").isEmpty()&&Math.abs(submitted.getDouble("hours")-payload.getDouble("hours"))>0.005)
            throw new Rejected("TIME_MISMATCH","Die Stunden passen nicht zu Beginn, Ende und Pause. Bitte die Zeitangaben prüfen.");
        // Same customer/day/duration/activity is unsafe to create twice, even with a new request_id.
        if(!update){JSONArray logs=master.optJSONArray("worklogs");for(int i=0;logs!=null&&i<logs.length();i++){JSONObject row=logs.getJSONObject(i);if(customerId.equals(row.optString("customer_id"))&&payload.getString("work_date").equals(row.optString("work_date"))&&Math.abs(payload.optDouble("hours")-row.optDouble("hours",-1))<0.005&&payload.getString("activity").trim().equalsIgnoreCase(row.optString("activity").trim()))throw new Rejected("POSSIBLE_DUPLICATE","Eine gleiche Arbeitsaufzeichnung ist bereits vorhanden. Bitte zuerst prüfen.");}}
        if(!update||submitted.has("work_date")||submitted.has("start_time")||submitted.has("end_time")||submitted.has("hours"))PixelMcpBusinessTools.preventOverlap(payload,master.optJSONArray("worklogs"));return payload;
    }
}
