package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Additive owner tools; the existing private tunnel write gate remains authoritative. */
final class OfferTools {
    static final String[] READS={"list_offers","get_offer","validate_offer","search_offer_templates","get_offer_template","get_offer_context"};
    static final String[] WRITES={"create_offer_draft","update_offer_draft","finalize_offer","duplicate_offer","prepare_offer_share","convert_offer_to_order","create_catalog_entry","update_catalog_entry"};
    static boolean read(String n){return Arrays.asList(READS).contains(n);}
    static boolean write(String n){return Arrays.asList(WRITES).contains(n);}
    static JSONObject fields()throws Exception{
        JSONObject p=new JSONObject();for(String k:OfferRules.FIELDS)p.put(k,PixelMcpOwnerTools.text(10000));
        for(String k:new String[]{"scope_confirmed","consumer_information_confirmed"})p.put(k,PixelMcpOwnerTools.bool());
        p.put("document_type",PixelMcpOwnerTools.enumeration(OfferRules.TYPES)).put("customer_type",PixelMcpOwnerTools.enumeration("b2b","consumer")).put("contract_channel",PixelMcpOwnerTools.enumeration("on_premise","distance","off_premise")).put("tax_mode",PixelMcpOwnerTools.enumeration("small_business","standard"));
        JSONObject line=new JSONObject();for(String k:new String[]{"title","description","unit","catalog_entry_id","catalog_hash","price_source"})line.put(k,PixelMcpOwnerTools.text(10000));
        for(String k:new String[]{"quantity","price","tax_rate"})line.put(k,new JSONObject().put("type",new JSONArray().put("number").put("null")).put("minimum",0).put("maximum",1000000000));
        p.put("positions",new JSONObject().put("type","array").put("maxItems",200).put("items",PixelMcpOwnerTools.object(line,"title","unit")));return p;
    }
    static JSONObject catalogFields()throws Exception{
        JSONObject p=new JSONObject();for(String k:new String[]{"title","description","unit","pricing_type","category","legal_notes","assumptions","exclusions"})p.put(k,PixelMcpOwnerTools.text(10000));
        for(String k:new String[]{"default_price","default_quantity"})p.put(k,new JSONObject().put("type",new JSONArray().put("number").put("null")).put("minimum",0).put("maximum",1000000000));
        p.put("active",PixelMcpOwnerTools.bool()).put("price_confirmed",PixelMcpOwnerTools.bool());return p;
    }
    static JSONObject schema(String n)throws Exception {
        JSONObject p=new JSONObject();List<String> required=new ArrayList<>();
        if(write(n)){p.put("request_id",PixelMcpOwnerTools.text(128).put("minLength",8));required.add("request_id");}
        if(Arrays.asList("get_offer","validate_offer","update_offer_draft","finalize_offer","duplicate_offer","prepare_offer_share","convert_offer_to_order").contains(n)){p.put("offer_id",PixelMcpOwnerTools.id());required.add("offer_id");}
        if(Arrays.asList("update_offer_draft","finalize_offer","convert_offer_to_order","prepare_offer_share").contains(n)){p.put("expected_record_hash",PixelMcpOwnerTools.hash());required.add("expected_record_hash");}
        switch(n){
            case "create_offer_draft":{JSONObject f=fields();for(Iterator<String> it=f.keys();it.hasNext();){String k=it.next();p.put(k,f.get(k));}required.addAll(Arrays.asList("customer_id","title","positions"));break;}
            case "update_offer_draft":p.put("updates",PixelMcpOwnerTools.object(fields()).put("minProperties",1));required.add("updates");break;
            case "list_offers":p.put("customer_id",PixelMcpOwnerTools.id()).put("status",PixelMcpOwnerTools.enumeration("draft","finalized","accepted")).put("offset",PixelMcpOwnerTools.number(0,1000000)).put("limit",PixelMcpOwnerTools.number(1,100));break;
            case "search_offer_templates":p.put("query",PixelMcpOwnerTools.text(200)).put("offset",PixelMcpOwnerTools.number(0,1000000)).put("limit",PixelMcpOwnerTools.number(1,100));break;
            case "get_offer_template":p.put("entry_id",PixelMcpOwnerTools.id());required.add("entry_id");break;
            case "get_offer_context":p.put("customer_id",PixelMcpOwnerTools.id());required.add("customer_id");break;
            case "finalize_offer":p.put("expected_context_hash",PixelMcpOwnerTools.hash()).put("confirmed",PixelMcpOwnerTools.bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("expected_context_hash","confirmed"));break;
            case "convert_offer_to_order":p.put("confirmed",PixelMcpOwnerTools.bool().put("enum",new JSONArray().put(true))).put("existing_order_id",PixelMcpOwnerTools.id()).put("expected_order_hash",PixelMcpOwnerTools.hash());required.add("confirmed");break;
            case "create_catalog_entry":{JSONObject f=catalogFields();for(Iterator<String> it=f.keys();it.hasNext();){String k=it.next();p.put(k,f.get(k));}required.addAll(Arrays.asList("title","unit","category"));break;}
            case "update_catalog_entry":p.put("entry_id",PixelMcpOwnerTools.id()).put("expected_record_hash",PixelMcpOwnerTools.hash()).put("updates",PixelMcpOwnerTools.object(catalogFields()).put("minProperties",1));required.addAll(Arrays.asList("entry_id","expected_record_hash","updates"));break;
        }
        return PixelMcpOwnerTools.object(p,required.toArray(new String[0]));
    }
    static JSONArray tools(boolean writable)throws Exception{
        JSONArray out=new JSONArray();List<String> names=new ArrayList<>(Arrays.asList(READS));if(writable)names.addAll(Arrays.asList(WRITES));
        for(String n:names){String d=n.contains("catalog")||n.contains("template")?"Gemeinsame editierbare Leistungsbausteine. Fehlende Preise sind null und müssen erfragt werden; keine erfundenen Preise.":n.equals("finalize_offer")?"Finalisiert ausschließlich das geprüfte Angebot nach ausdrücklicher Bestätigung. Kunde, Aussteller und Preise sind über context_hash gebunden; kein Rechnungsabschluss.":n.equals("convert_offer_to_order")?"Übernimmt ein ausdrücklich angenommenes Angebot mit Kunde, Einsatzort und offer_id in einen Auftrag. Keine Rechnung.":"Verwaltet Angebote in der bestehenden App und im bestehenden Drive-Journal. Entwürfe dürfen unvollständig sein; Dokumentart bewusst wählen. record_hash vor Updates lesen; request_id bei Wiederholung unverändert lassen. Niemals Kunden, Stunden oder Preise erfinden.";
            out.put(new JSONObject().put("name",n).put("description",d).put("inputSchema",schema(n)).put("annotations",new JSONObject().put("readOnlyHint",read(n)).put("destructiveHint",false).put("idempotentHint",true).put("openWorldHint",false)));}return out;
    }
    static JSONObject context(JSONObject p,JSONObject s)throws Exception {
        String recipient=p.optString("billing_customer_id");if(recipient.isEmpty())recipient=p.optString("customer_id");
        JSONObject c=PixelMcpWriteTools.find(s.getJSONArray("customers"),p.getString("customer_id")),r=PixelMcpWriteTools.find(s.getJSONArray("customers"),recipient),settings=s.optJSONObject("business_settings");
        return new JSONObject().put("customer",DriveJournalRules.guard(c,"customer")).put("recipient",DriveJournalRules.guard(r,"customer")).put("order",p.optString("order_id").isEmpty()?JSONObject.NULL:DriveJournalRules.guard(PixelMcpWriteTools.find(s.getJSONArray("orders"),p.getString("order_id")),"order")).put("business_settings",settings==null?new JSONObject():settings).put("service_catalog",s.optJSONArray("service_catalog")==null?new JSONArray():s.getJSONArray("service_catalog"));
    }
    static JSONObject query(String n,JSONObject a,JSONObject state)throws Exception{
        McpSchema.validate(a,schema(n));JSONObject s=PixelMcpReadTools.effective(state);JSONArray offers=s.getJSONArray("offers");
        if(n.equals("get_offer")||n.equals("validate_offer")){JSONObject p=PixelMcpWriteTools.find(offers,a.getString("offer_id"));JSONObject result=new JSONObject().put("offer",p).put("record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(p))).put("context_hash",PixelMcpWriteTools.hash(context(p,s)));if(!p.optString("order_id").isEmpty())result.put("order_hash",OwnerJournalRules.version(PixelMcpWriteTools.find(s.getJSONArray("orders"),p.getString("order_id"))));if(n.equals("validate_offer"))result.put("validation",OfferRules.validate(p,s));return result;}
        if(n.equals("get_offer_template")){JSONObject p=PixelMcpWriteTools.find(s.getJSONArray("service_catalog"),a.getString("entry_id"));return new JSONObject().put("entry",p).put("record_hash",PixelMcpWriteTools.hash(p));}
        if(n.equals("get_offer_context")){
            String id=a.getString("customer_id");JSONObject c=PixelMcpWriteTools.find(s.getJSONArray("customers"),id);JSONArray logs=new JSONArray(),orders=new JSONArray(),prices=new JSONArray();
            JSONArray all=s.getJSONArray("worklogs");for(int i=all.length()-1;i>=0&&logs.length()<10;i--)if(id.equals(all.getJSONObject(i).optString("customer_id")))logs.put(all.getJSONObject(i));
            all=s.getJSONArray("orders");for(int i=0;i<all.length()&&orders.length()<10;i++)if(id.equals(all.getJSONObject(i).optString("customer_id"))&&!Arrays.asList("Erledigt","Abgeschlossen","Storniert").contains(all.getJSONObject(i).optString("status")))orders.put(all.getJSONObject(i));
            all=s.getJSONArray("service_catalog");for(int i=0;i<all.length();i++){JSONObject e=all.getJSONObject(i);if(e.optBoolean("active",true)&&e.optBoolean("price_confirmed")&&!e.isNull("default_price"))prices.put(new JSONObject().put("entry",e).put("record_hash",PixelMcpWriteTools.hash(e)));}
            return new JSONObject().put("customer",c).put("billing_configuration",BillingRules.assignment(c,s,false)).put("recent_work",logs).put("open_orders",orders).put("approved_prices",prices).put("business_settings",s.optJSONObject("business_settings"));
        }
        JSONArray rows=n.equals("list_offers")?offers:s.getJSONArray("service_catalog"),out=new JSONArray();int total=0;
        for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(!a.optString("customer_id").isEmpty()&&!a.getString("customer_id").equals(p.optString("customer_id")))continue;if(!a.optString("status").isEmpty()&&!a.getString("status").equals(p.optString("status")))continue;if(!p.toString().toLowerCase(Locale.GERMAN).contains(a.optString("query").toLowerCase(Locale.GERMAN)))continue;if(total++>=a.optInt("offset",0)&&out.length()<a.optInt("limit",30))out.put(new JSONObject().put(n.equals("list_offers")?"offer":"entry",p).put("record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(p))));}
        return new JSONObject().put(n.equals("list_offers")?"offers":"entries",out).put("total",total);
    }
}
