package at.hausgarten.service;

import org.json.*;
import java.util.*;
import static at.hausgarten.service.EntityContract.*;

/** Typed, reversible business writes for the private Pixel MCP. */
final class PixelMcpEntityWriteTools {
    static final String[] NAMES={"create_customer","update_customer","create_order","update_order","create_invoice_draft","update_invoice_draft"};
    static final String[] CUSTOMER_FIELDS=EntityContract.CUSTOMER_FIELDS;
    static final String[] ORDER_FIELDS=EntityContract.ORDER_FIELDS;
    static final String[] INVOICE_FIELDS=EntityContract.INVOICE_FIELDS;
    static boolean handles(String name){return Arrays.asList(NAMES).contains(name);}
    private static JSONObject string(int max)throws Exception{return new JSONObject().put("type","string").put("maxLength",max);}
    private static JSONObject number()throws Exception{return new JSONObject().put("type","number").put("minimum",0);}
    private static JSONObject integer()throws Exception{return new JSONObject().put("type","integer").put("minimum",0);}
    private static JSONObject bool()throws Exception{return new JSONObject().put("type","boolean");}
    private static JSONObject field(String key)throws Exception{
        if(Arrays.asList("active","customer_visible","domestic_service_confirmed","test_record").contains(key))return bool();
        if("garden_area".equals(key))return number().put("type",new JSONArray().put("number").put("null"));
        if("custom_fields".equals(key))return new JSONObject().put("type","object").put("maxProperties",50)
            .put("additionalProperties",new JSONObject().put("type",new JSONArray().put("string").put("number").put("boolean").put("null")).put("maxLength",2000))
            .put("description","Vorhandene frei benannte Kunden-Zusatzfelder. Änderungen werden mit bestehenden Feldern zusammengeführt; nicht genannte Felder bleiben erhalten. Werte sind Text, Zahl, Ja/Nein oder null.");
        if("billing_mode".equals(key))return string(30).put("enum",new JSONArray().put("direct").put("via_customer"));
        if("payment_days".equals(key))return integer();
        if("positions".equals(key)){
            JSONObject p=new JSONObject();for(String k:new String[]{"title","description","unit","worklog_id"})p.put(k,string(10000));for(String k:new String[]{"quantity","price","tax_rate","discount","net","tax","net_total","tax_total","gross_total"})p.put(k,new JSONObject().put("type","number"));return new JSONObject().put("type","array").put("minItems",1).put("maxItems",200).put("items",McpFields.object(p,"title","quantity","price"));}
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
    static JSONObject prepare(String name,JSONObject args,JSONObject state,String actionId)throws Exception{
        validate(name,args);return EntityApplicationService.prepare(name,args,state,actionId);
    }
    static void validatePositions(JSONArray positions)throws Exception{EntityContract.validatePositions(positions);}
    static JSONObject recordForHash(String entity,JSONObject row)throws Exception{return editable(row,entity.equals("customer")?CUSTOMER_FIELDS:entity.equals("order")?ORDER_FIELDS:INVOICE_FIELDS);}
}
