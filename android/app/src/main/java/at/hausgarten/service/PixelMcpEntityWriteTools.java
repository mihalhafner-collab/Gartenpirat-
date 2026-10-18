package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Typed, reversible business writes for the private Pixel MCP. */
final class PixelMcpEntityWriteTools {
    static final String[] NAMES={"create_customer","update_customer","create_order","update_order","create_invoice_draft","update_invoice_draft"};
    static final String[] CUSTOMER_FIELDS={"name","company","first_name","last_name","street","postal_code","city","country","phone","email","notes","active","garden_area","sevdesk_customer_number","custom_fields","billing_mode","default_billing_customer_id"};
    static final String[] ORDER_FIELDS={"customer_id","billing_customer_id","title","description","location","scheduled_date","status","customer_visible"};
    static final String[] INVOICE_FIELDS={"customer_id","invoice_date","service_start","service_end","payment_days","notes","positions","domestic_service_confirmed","order_id","header_text","footer_text","tax_mode","recipient_type","recipient_vat_id"};
    static boolean handles(String name){return Arrays.asList(NAMES).contains(name);}
    private static JSONObject string(int max)throws Exception{return new JSONObject().put("type","string").put("maxLength",max);}
    private static JSONObject number()throws Exception{return new JSONObject().put("type","number").put("minimum",0);}
    private static JSONObject integer()throws Exception{return new JSONObject().put("type","integer").put("minimum",0);}
    private static JSONObject bool()throws Exception{return new JSONObject().put("type","boolean");}
    private static JSONObject field(String key)throws Exception{
        if(Arrays.asList("active","customer_visible","domestic_service_confirmed").contains(key))return bool();
        if("garden_area".equals(key))return number().put("type",new JSONArray().put("number").put("null"));
        if("custom_fields".equals(key))return new JSONObject().put("type","object").put("maxProperties",50)
            .put("additionalProperties",new JSONObject().put("type",new JSONArray().put("string").put("number").put("boolean").put("null")).put("maxLength",2000))
            .put("description","Vorhandene frei benannte Kunden-Zusatzfelder. Änderungen werden mit bestehenden Feldern zusammengeführt; nicht genannte Felder bleiben erhalten. Werte sind Text, Zahl, Ja/Nein oder null.");
        if("billing_mode".equals(key))return string(30).put("enum",new JSONArray().put("direct").put("via_customer"));
        if("payment_days".equals(key))return integer();
        if("positions".equals(key)){
            JSONObject p=new JSONObject();for(String k:new String[]{"title","description","unit","worklog_id"})p.put(k,string(10000));for(String k:new String[]{"quantity","price","tax_rate","discount","net","tax","net_total","tax_total","gross_total"})p.put(k,new JSONObject().put("type","number"));return new JSONObject().put("type","array").put("minItems",1).put("maxItems",200).put("items",PixelMcpOwnerTools.object(p,"title","quantity","price"));}
        if("recipient_type".equals(key))return string(30).put("enum",new JSONArray().put("b2b").put("consumer").put(""));
        if("tax_mode".equals(key))return string(30).put("enum",new JSONArray().put("small_business").put("standard"));
        return string(Arrays.asList("notes","description").contains(key)?10000:500);
    }
    static JSONObject schema(String name)throws Exception{
        JSONObject props=new JSONObject().put("request_id",string(128).put("minLength",8));JSONArray required=new JSONArray().put("request_id");
        boolean update=name.startsWith("update_");String entity=name.contains("customer")?"customer":name.contains("order")?"order":"invoice";
        String idKey=entity+"_id";String[] fields=entity.equals("customer")?CUSTOMER_FIELDS:entity.equals("order")?ORDER_FIELDS:INVOICE_FIELDS;
        if(update){
            props.put(idKey,string(500)).put("expected_record_hash",string(128));
            JSONObject patch=new JSONObject();for(String key:fields)patch.put(key,field(key));
            props.put("updates",new JSONObject().put("type","object").put("properties",patch).put("additionalProperties",false).put("minProperties",1));
            required.put(idKey).put("expected_record_hash").put("updates");
        }else{
            for(String key:fields)props.put(key,field(key));
            if(entity.equals("customer"))required.put("name");
            if(entity.equals("order"))required.put("customer_id").put("title");
            if(entity.equals("invoice"))required.put("customer_id").put("invoice_date").put("positions");
        }
        return new JSONObject().put("type","object").put("properties",props).put("required",required).put("additionalProperties",false);
    }
    static JSONArray tools()throws Exception{
        JSONArray out=new JSONArray();
        for(String name:NAMES){
            String description=name.contains("invoice")?"Erstellt oder ändert ausschließlich einen lokalen Rechnungsentwurf. Dieses Werkzeug stellt keine Rechnung aus, versendet nichts und markiert nichts als bezahlt. Vorhandene sevDesk-Rechnungen bleiben unverändert.":name.equals("create_customer")?"Legt einen neuen Kunden mit Stammdaten und Zusatzfeldern an. Vorher Kunden einschließlich inaktiver Kunden suchen; mögliche Dubletten werden abgewiesen. Kunden-ID wird sicher auf dem Pixel erzeugt.":"Erstellt oder ändert einen Auftrag mit einer tatsächlich vorhandenen Kunden-ID. Vor Änderungen Datensatz abrufen und record_hash übernehmen.";
            if(name.equals("update_customer"))description="Korrigiert ausdrücklich genannte Stammdaten eines vorhandenen Kunden anhand customer_id. Zuerst get_customer aufrufen und dessen record_hash als expected_record_hash übernehmen. Nicht genannte Felder, IDs, Zeitstempel und Verknüpfungen bleiben erhalten. Zusatzfelder werden ergänzt, nicht blind ersetzt.";
            out.put(new JSONObject().put("name",name).put("description",description+" request_id bei Wiederholungen unverändert lassen.")
                .put("inputSchema",schema(name)).put("annotations",new JSONObject().put("readOnlyHint",false).put("destructiveHint",name.startsWith("update_")).put("idempotentHint",true).put("openWorldHint",false)));
        }
        return out;
    }
    static void validate(String name,JSONObject args)throws Exception{validateObject(args,schema(name));}
    private static void validateCustomFields(JSONObject fields)throws Exception{
        if(fields.length()>50)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte höchstens 50 Kunden-Zusatzfelder angeben.");
        for(Iterator<String> it=fields.keys();it.hasNext();){
            String key=it.next();Object value=fields.get(key);
            if(key.trim().isEmpty()||key.length()>80||key.matches(".*[\\p{Cntrl}].*")||Arrays.asList("__proto__","constructor","prototype").contains(key))
                throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte eine eindeutige Zusatzfeld-Bezeichnung mit höchstens 80 Zeichen verwenden.");
            if(value==JSONObject.NULL)continue;
            if(value instanceof String&&((String)value).length()<=2000)continue;
            if(value instanceof Boolean)continue;
            if(value instanceof Number&&Double.isFinite(((Number)value).doubleValue()))continue;
            throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Zusatzfelder unterstützen Text, endliche Zahlen, Ja/Nein oder null.");
        }
    }
    private static JSONObject customFields(JSONObject row)throws Exception{
        Object value=row.opt("custom_fields");
        if(value==null||value==JSONObject.NULL||"".equals(value))return new JSONObject();
        if(value instanceof JSONObject)return new JSONObject(value.toString());
        if(value instanceof String)try{return new JSONObject((String)value);}catch(Exception ignored){}
        throw new PixelMcpWriteTools.Rejected("INVALID_STORED_DATA","Bestehende Kunden-Zusatzfelder sind nicht lesbar. Bitte prüfen; keine Änderung gespeichert.");
    }
    private static void validateObject(JSONObject args,JSONObject schema)throws Exception{
        JSONObject props=schema.getJSONObject("properties");
        for(Iterator<String> it=args.keys();it.hasNext();){
            String key=it.next();if(!props.has(key))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Diese Angabe ist nicht erlaubt: "+key);
            Object value=args.get(key);JSONObject spec=props.getJSONObject(key);Object declared=spec.get("type");String type=declared instanceof JSONArray?((JSONArray)declared).getString(0):(String)declared;
            if(value==JSONObject.NULL&&declared instanceof JSONArray){boolean nullable=false;JSONArray types=(JSONArray)declared;for(int i=0;i<types.length();i++)if("null".equals(types.optString(i)))nullable=true;if(nullable)continue;}
            boolean valid=type.equals("string")?value instanceof String:type.equals("boolean")?value instanceof Boolean:type.equals("object")?value instanceof JSONObject:type.equals("array")?value instanceof JSONArray:value instanceof Number;
            if(!valid)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Ungültiger Eingabetyp: "+key);
            if(value instanceof String&&(((String)value).length()>spec.optInt("maxLength",10000)||((String)value).length()<spec.optInt("minLength",0)))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte die Länge der Angabe prüfen: "+key);
            if(value instanceof Number){double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n<0||(type.equals("integer")&&n!=Math.floor(n)))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte eine gültige nicht negative Zahl eingeben: "+key);}
            if(type.equals("object")){if(key.equals("custom_fields"))validateCustomFields((JSONObject)value);else{if(((JSONObject)value).length()==0)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte mindestens eine Änderung angeben.");validateObject((JSONObject)value,spec);}}
            if(type.equals("array")){int length=((JSONArray)value).length();if(length<spec.optInt("minItems",0)||length>spec.optInt("maxItems",200))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte die Anzahl der Rechnungspositionen prüfen.");}
        }
        JSONArray required=schema.optJSONArray("required");for(int i=0;required!=null&&i<required.length();i++){String key=required.getString(i);if(!args.has(key)||(args.opt(key) instanceof String&&args.optString(key).trim().isEmpty()))throw new PixelMcpWriteTools.Rejected("MISSING_FIELD","Pflichtangabe fehlt: "+key);}
    }
    private static JSONObject editable(JSONObject row,String[] fields)throws Exception{JSONObject out=new JSONObject().put("id",row.optString("id"));if(row.has("updated_at"))out.put("updated_at",row.get("updated_at"));for(String key:fields)if(row.has(key))out.put(key,row.get(key));return out;}
    static JSONObject prepare(String name,JSONObject args,JSONObject state,String actionId)throws Exception{
        validate(name,args);JSONObject master=PixelMcpReadTools.effective(state);boolean update=name.startsWith("update_");
        String entity=name.contains("customer")?"customer":name.contains("order")?"order":"invoice";
        String collection=entity.equals("customer")?"customers":entity.equals("order")?"orders":"invoices";
        String[] fields=entity.equals("customer")?CUSTOMER_FIELDS:entity.equals("order")?ORDER_FIELDS:INVOICE_FIELDS;
        JSONObject payload;
        if(update){
            JSONObject capabilities=master.optJSONObject("capabilities");
            if(capabilities==null||!Boolean.TRUE.equals(capabilities.opt("entity_compare_and_save")))throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Bitte zuerst die gemeinsame Web-/Drive-Verarbeitung und anschließend das Pixel mit Drive abgleichen. Der Konfliktschutz ist noch nicht bestätigt.");
            String idKey=entity+"_id",id=args.getString(idKey);JSONObject old=PixelMcpWriteTools.find(master.optJSONArray(collection),id);
            if(old.optString("updated_at").isEmpty())throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Der Änderungsstand fehlt. Bitte zuerst vollständig abgleichen.");
            if(!PixelMcpWriteTools.hash(editable(old,fields)).equals(args.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der Datensatz wurde inzwischen geändert. Bitte erneut öffnen.");
            JSONArray pending=state.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.optJSONObject("payload");if(p!=null&&id.equals(p.optString("id")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Für diesen Datensatz ist bereits eine Änderung vorgemerkt. Bitte zuerst abgleichen.");}
            payload=new JSONObject().put("id",id);for(String key:fields)if(old.has(key))payload.put(key,old.get(key));for(String key:new String[]{"status","sevdesk_id","sevdesk_contact_id","issued_at","invoice_number","order_id","created_at","document_type","correction_of","correction_reason","header_text","footer_text"})if(old.has(key))payload.put(key,old.get(key));JSONObject patch=args.getJSONObject("updates");for(Iterator<String> it=patch.keys();it.hasNext();){String key=it.next();payload.put(key,patch.get(key));}
            if(entity.equals("customer")&&patch.has("custom_fields")){
                if(!capabilities.optBoolean("customer_custom_fields_compare_and_save"))throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Kunden-Zusatzfelder benötigen die gemeinsame Web-/Drive-Verarbeitung. Bitte zuerst mit Drive abgleichen; keine Änderung wurde gespeichert.");
                JSONObject merged=customFields(old),changes=patch.getJSONObject("custom_fields");for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();merged.put(key,changes.get(key));}payload.put("custom_fields",merged);
            }
            JSONObject base=new JSONObject().put("id",id);for(String key:fields)if(!key.equals("positions")&&!key.equals("domestic_service_confirmed")&&!key.equals("payment_days")&&old.has(key))base.put(key,old.get(key));for(String key:new String[]{"updated_at","status","invoice_number"})if(old.has(key))base.put(key,old.get(key));
            if(entity.equals("customer")&&!capabilities.optBoolean("customer_custom_fields_compare_and_save")){base.remove("custom_fields");payload.remove("custom_fields");}
            else if(entity.equals("customer"))base.put("custom_fields",customFields(old));
            payload.put("_mcp_base",base);payload.remove("_mcp_create");
            if(entity.equals("invoice"))payload.put("_mcp_invoice_positions",old.optJSONArray("positions")).put("_mcp_invoice_payment_days",old.optInt("payment_days",14));
        }else{
            payload=new JSONObject();for(String key:fields)if(args.has(key))payload.put(key,args.get(key));
            payload.put("id",(entity.equals("customer")?"cus_mobile_mcp_":entity.equals("order")?"ord_mobile_mcp_":"inv_mobile_mcp_")+PixelMcpWriteTools.hash(actionId).substring(0,32));
            JSONArray rows=master.optJSONArray(collection);for(int i=0;rows!=null&&i<rows.length();i++)if(payload.getString("id").equals(rows.getJSONObject(i).optString("id")))throw new PixelMcpWriteTools.Rejected("ID_ALREADY_EXISTS","Dieser Datensatz existiert bereits.");
            payload.put("_mcp_create",true).put("_new_record",true);
        }
        if(entity.equals("customer")){
            BillingRules.customerConfig(payload,master.optJSONArray("customers"));
            if(payload.optString("name").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("MISSING_FIELD","Der Kundenname fehlt.");
            if(!update)try{EntityProtectionRules.validateNewCustomer(payload,master.optJSONArray("customers")==null?new JSONArray():master.getJSONArray("customers"));}catch(IllegalArgumentException duplicate){throw new PixelMcpWriteTools.Rejected("POSSIBLE_DUPLICATE",duplicate.getMessage());}
            JSONObject incoming=update?args.getJSONObject("updates"):args;
            if(incoming.has("email")&&!incoming.optString("email").isEmpty()&&!incoming.optString("email").matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte eine gültige E-Mail-Adresse angeben oder das Feld leer lassen.");
            if(!update&&!payload.has("custom_fields"))payload.put("custom_fields",new JSONObject());
            payload.put("active",payload.optBoolean("active",payload.optInt("active",1)!=0)?1:0);
        }else if(entity.equals("order")){
            BillingRules.order(payload,master);
            PixelMcpWriteTools.find(master.optJSONArray("customers"),payload.optString("customer_id"));
            if(payload.optString("title").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("MISSING_FIELD","Die Auftragsbezeichnung fehlt.");
            if(!update){JSONArray rows=master.optJSONArray("orders");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(payload.optString("customer_id").equals(row.optString("customer_id"))&&payload.optString("title").trim().equalsIgnoreCase(row.optString("title").trim())&&payload.optString("scheduled_date").equals(row.optString("scheduled_date")))throw new PixelMcpWriteTools.Rejected("POSSIBLE_DUPLICATE","Ein ähnlicher Auftrag existiert bereits. Bitte vorhandenen Auftrag suchen.");}}
            if(update){JSONObject previous=PixelMcpWriteTools.find(master.optJSONArray("orders"),payload.getString("id"));EntityProtectionRules.validateOrderChange(payload,previous,master.optJSONArray("worklogs"),master.optJSONArray("invoices"),master.optJSONArray("photos"));}
            if(!payload.optString("scheduled_date").isEmpty())try{java.time.LocalDate.parse(payload.optString("scheduled_date"));}catch(Exception e){throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte ein gültiges Auftragsdatum angeben.");}
            payload.put("customer_visible",payload.optBoolean("customer_visible",payload.optInt("customer_visible",1)!=0)?1:0).put("source","ChatGPT Sprache");
        }else{
            PixelMcpWriteTools.find(master.optJSONArray("customers"),payload.optString("customer_id"));
            if(!"Entwurf".equals(payload.optString("status","Entwurf"))||!payload.optString("sevdesk_id").isEmpty()||!payload.optString("issued_at").isEmpty())throw new PixelMcpWriteTools.Rejected("INVOICE_LOCKED","Nur lokale Rechnungsentwürfe dürfen bearbeitet werden.");
            for(String key:new String[]{"invoice_date","service_start","service_end"})if(!payload.optString(key).isEmpty())try{java.time.LocalDate.parse(payload.optString(key));}catch(Exception e){throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte gültige Rechnungs- und Leistungsdaten angeben.");}
            validatePositions(payload.getJSONArray("positions"));payload.put("status","Entwurf").put("sevdesk_id","").put("source","ChatGPT Sprache");
            JSONArray positions=payload.getJSONArray("positions");Set<String> used=new HashSet<>();for(int i=0;i<positions.length();i++){String worklogId=positions.getJSONObject(i).optString("worklog_id");if(!worklogId.isEmpty()){JSONObject log=PixelMcpWriteTools.find(master.optJSONArray("worklogs"),worklogId);if(!payload.optString("customer_id").equals(BillingRules.recipient(log))||log.optInt("billed",0)!=0||!used.add(worklogId))throw new PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH","Rechnungspositionen müssen offene Arbeiten dieses Kunden verwenden und dürfen keine Arbeit doppelt enthalten.");}}
            if(!update)payload.put("invoice_number","Entwurf-"+payload.getString("id"));
            if(payload.optInt("payment_days",14)>365)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Das Zahlungsziel muss zwischen 0 und 365 Tagen liegen.");
            java.math.BigDecimal total=java.math.BigDecimal.ZERO;for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);java.math.BigDecimal quantity=new java.math.BigDecimal(p.get("quantity").toString()),price=new java.math.BigDecimal(p.get("price").toString()),discount=new java.math.BigDecimal(p.opt("discount")==null?"0":p.get("discount").toString());java.math.BigDecimal amount=quantity.multiply(price).multiply(java.math.BigDecimal.ONE.subtract(discount.divide(new java.math.BigDecimal("100")))).setScale(2,java.math.RoundingMode.HALF_UP);p.put("net",amount.doubleValue()).put("tax",0);total=total.add(amount);}
            payload.put("amount",total.doubleValue()).put("net_total",total.doubleValue()).put("gross_total",total.doubleValue()).put("tax_total",0);
            InvoiceDraftRules.normalize(payload,update?PixelMcpWriteTools.find(master.getJSONArray("invoices"),payload.getString("id")):null,master);
        }
        return new JSONObject().put("entity_type",entity).put("payload",payload);
    }
    static void validatePositions(JSONArray positions)throws Exception{
        for(int i=0;i<positions.length();i++){
            JSONObject p=positions.optJSONObject(i);
            if(p==null||p.optString("title").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Jede Rechnungsposition benötigt eine Bezeichnung.");
            for(Iterator<String> keys=p.keys();keys.hasNext();)if(!Arrays.asList("title","description","quantity","price","unit","tax_rate","discount","worklog_id","net","tax","net_total","tax_total","gross_total").contains(keys.next()))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Unbekannte Angabe in Rechnungsposition.");
            for(String key:new String[]{"quantity","price","discount","tax_rate"})if(p.has(key)&&!(p.opt(key) instanceof Number))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Menge, Preis, Rabatt und Steuer müssen Zahlen sein.");
            double quantity=p.optDouble("quantity",Double.NaN),price=p.optDouble("price",Double.NaN),discount=p.optDouble("discount",0);
            if(!Double.isFinite(quantity)||quantity<=0||!Double.isFinite(price)||price<0||!Double.isFinite(discount)||discount<0||discount>100||!Double.isFinite(p.optDouble("tax_rate",0))||p.optDouble("tax_rate",0)<0||p.optDouble("tax_rate",0)>100)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte Menge, Preis, Rabatt und Steuersatz prüfen.");
        }
    }
    static JSONObject recordForHash(String entity,JSONObject row)throws Exception{return editable(row,entity.equals("customer")?CUSTOMER_FIELDS:entity.equals("order")?ORDER_FIELDS:INVOICE_FIELDS);}
}
