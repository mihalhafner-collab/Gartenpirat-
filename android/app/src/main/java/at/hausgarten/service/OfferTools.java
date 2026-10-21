package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Additive owner tools; the existing private tunnel write gate remains authoritative. */
final class OfferTools {
    static final String[] READS={"get_offer_contract","list_offers","get_offer","validate_offer","search_offer_templates","get_offer_template","get_offer_context"};
    static final String[] WRITES={"create_and_validate_offer","validate_offer_draft","prepare_offer_delivery","confirm_offer_delivery","create_offer_draft","update_offer_draft","finalize_offer","duplicate_offer","prepare_offer_share","convert_offer_to_order","create_catalog_entry","update_catalog_entry"};
    static boolean read(String n){return Arrays.asList(READS).contains(n);}
    static boolean write(String n){return Arrays.asList(WRITES).contains(n);}
    static JSONObject fields()throws Exception{
        JSONObject p=new JSONObject();for(String k:OfferRules.FIELDS)p.put(k,McpFields.text(10000));
        for(String k:new String[]{"scope_confirmed","consumer_information_confirmed","binding_confirmed","test_record"})p.put(k,McpFields.bool());
        p.put("document_type",McpFields.enumeration(OfferRules.TYPES)).put("customer_type",McpFields.enumeration(OfferContract.values("customer_types"))).put("contract_channel",McpFields.enumeration(OfferContract.values("contract_channels"))).put("tax_mode",McpFields.enumeration(OfferContract.values("tax_modes")));
        JSONObject line=new JSONObject();for(String k:new String[]{"title","description","unit","catalog_entry_id","catalog_hash"})line.put(k,McpFields.text(10000));
        line.put("title",McpFields.text(500));
        line.put("price_source",McpFields.enumeration(OfferContract.values("price_sources")).put("description","Bei einem ausdrücklich vom Nutzer bestätigten manuellen Preis exakt user_confirmed verwenden; bei freigegebenem Katalogpreis weglassen und catalog_entry_id/catalog_hash verwenden."));
        line.put("price_basis",McpFields.enumeration(OfferContract.values("price_bases"))).put("price_note",McpFields.text(2000));
        JSONObject calculation=new JSONObject();for(String k:new String[]{"base_price","factor","surcharge"})calculation.put(k,new JSONObject().put("type",new JSONArray().put("number").put("null")).put("minimum",0).put("maximum",1000000000));line.put("calculation",McpFields.object(calculation));
        for(String k:new String[]{"quantity","price","tax_rate"})line.put(k,new JSONObject().put("type",new JSONArray().put("number").put("null")).put("minimum",0).put("maximum",1000000000));
        line.getJSONObject("tax_rate").put("maximum",100);line.getJSONObject("quantity").put("exclusiveMinimum",0).put("description","Bei bekannten Mengen größer als0, andernfalls null für einen unvollständigen Entwurf.");
        p.put("positions",new JSONObject().put("type","array").put("maxItems",200).put("items",McpFields.object(line,"title","unit")));return p;
    }
    static JSONObject catalogFields()throws Exception{
        JSONObject p=new JSONObject();for(String k:new String[]{"title","description","unit","pricing_type","category","legal_notes","assumptions","exclusions"})p.put(k,McpFields.text(10000));
        for(String k:new String[]{"default_price","default_quantity"})p.put(k,new JSONObject().put("type",new JSONArray().put("number").put("null")).put("minimum",0).put("maximum",1000000000));
        p.put("pricing_type",McpFields.enumeration(OfferContract.values("pricing_types")));
        p.put("active",McpFields.bool()).put("price_confirmed",McpFields.bool());return p;
    }
    static JSONObject schema(String n)throws Exception {
        JSONObject p=new JSONObject();List<String> required=new ArrayList<>();
        if(write(n)){p.put("request_id",McpFields.text(128).put("minLength",8));required.add("request_id");}
        if(Arrays.asList("get_offer","validate_offer","update_offer_draft","finalize_offer","duplicate_offer","prepare_offer_share","convert_offer_to_order","validate_offer_draft","prepare_offer_delivery","confirm_offer_delivery").contains(n)){p.put("offer_id",McpFields.id());required.add("offer_id");}
        if(Arrays.asList("update_offer_draft","finalize_offer","convert_offer_to_order","prepare_offer_share","validate_offer_draft","prepare_offer_delivery","confirm_offer_delivery").contains(n)){p.put("expected_record_hash",McpFields.hash());required.add("expected_record_hash");}
        switch(n){
            case "create_offer_draft":case "create_and_validate_offer":{JSONObject f=fields();for(Iterator<String> it=f.keys();it.hasNext();){String k=it.next();p.put(k,f.get(k));}required.addAll(Arrays.asList("customer_id","title","positions"));break;}
            case "update_offer_draft":p.put("updates",McpFields.object(fields()).put("minProperties",1));required.add("updates");break;
            case "list_offers":p.put("customer_id",McpFields.id()).put("document_type",McpFields.enumeration(OfferRules.TYPES)).put("status",McpFields.enumeration(OfferContract.values("workflow_statuses"))).put("offset",McpFields.number(0,1000000)).put("limit",McpFields.number(1,100));break;
            case "search_offer_templates":p.put("query",McpFields.text(200)).put("offset",McpFields.number(0,1000000)).put("limit",McpFields.number(1,100));break;
            case "get_offer_template":p.put("entry_id",McpFields.id());required.add("entry_id");break;
            case "get_offer_context":p.put("customer_id",McpFields.id());required.add("customer_id");break;
            case "prepare_offer_delivery":p.put("recipient_email",McpFields.text(320).put("minLength",3)).put("confirmed",McpFields.bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("recipient_email","confirmed"));break;
            case "confirm_offer_delivery":p.put("provider",McpFields.enumeration("gmail")).put("message_id",McpFields.id());required.addAll(Arrays.asList("provider","message_id"));break;
            case "finalize_offer":p.put("expected_context_hash",McpFields.hash()).put("confirmed",McpFields.bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("expected_context_hash","confirmed"));break;
            case "convert_offer_to_order":p.put("confirmed",McpFields.bool().put("enum",new JSONArray().put(true))).put("existing_order_id",McpFields.id()).put("expected_order_hash",McpFields.hash());required.add("confirmed");break;
            case "create_catalog_entry":{JSONObject f=catalogFields();for(Iterator<String> it=f.keys();it.hasNext();){String k=it.next();p.put(k,f.get(k));}required.addAll(Arrays.asList("title","unit","category"));break;}
            case "update_catalog_entry":p.put("entry_id",McpFields.id()).put("expected_record_hash",McpFields.hash()).put("updates",McpFields.object(catalogFields()).put("minProperties",1));required.addAll(Arrays.asList("entry_id","expected_record_hash","updates"));break;
        }
        return McpFields.object(p,required.toArray(new String[0]));
    }
    static JSONArray tools(boolean writable)throws Exception{
        JSONArray out=new JSONArray();List<String> names=new ArrayList<>(Arrays.asList(READS));if(writable)names.addAll(Arrays.asList(WRITES));
        for(String n:names){String d=n.equals("get_offer_contract")?"Liest den gemeinsamen Dokumentvertrag: Dokumentarten, Bindungsarten, Preisquellen/-grundlagen und Statuswerte. Keine Datenänderung.":n.equals("create_and_validate_offer")?"Erstellt und validiert einen Angebots-/Kostenvoranschlagsentwurf atomar. Bei ungültigen oder mehrdeutigen Angaben wird nichts gespeichert. Nur bestätigte Leistungen und Preise; Testflag erfordert Testkunden.":n.equals("prepare_offer_delivery")?"Bereitet das unveränderte Original-PDF für den ausdrücklich bestätigten Empfänger vor. Nicht gesendet. Anschließend separaten freigegebenen Mailkanal verwenden und dessen Nachrichten-ID prüfen.":n.equals("confirm_offer_delivery")?"Prüft ausschließlich lesend die echte Gmail-SENT-Nachricht, Aussteller, bestätigten Empfänger und identischen Original-PDF-Hash. Erst nach Provider-Readback sent; separate Gmail-Lesefreigabe am Pixel notwendig. Keine Mail wird versendet.":n.equals("validate_offer_draft")?"Validiert und speichert den Status validated für den aktuellen Entwurf; atomarer Hashschutz, kein PDF-Versand und keine Rechnung.":n.equals("prepare_offer_share")?"Bereitet das finalisierte Original-PDF mit SHA256 zum Teilen vor und protokolliert share_prepared. Niemals sent. Markierte Testdokumente liefern ausschließlich eine lokale Vorschau, keinen externen Versand.":n.contains("catalog")||n.contains("template")?"Gemeinsame editierbare Leistungsbausteine. Fehlende Preise sind null und müssen erfragt werden; keine erfundenen Preise.":n.equals("finalize_offer")?"Finalisiert ausschließlich das geprüfte Angebot nach ausdrücklicher Bestätigung. Kunde, Aussteller und Preise sind über context_hash gebunden; kein Rechnungsabschluss.":n.equals("convert_offer_to_order")?"Übernimmt ein ausdrücklich angenommenes Angebot mit Kunde, Einsatzort und offer_id in einen Auftrag. Keine Rechnung.":"Verwaltet Angebote in der bestehenden App und im bestehenden Drive-Journal. Entwürfe dürfen unvollständig sein; Dokumentart bewusst wählen. record_hash vor Updates lesen; request_id bei Wiederholung unverändert lassen. Niemals Kunden, Stunden oder Preise erfinden.";
            out.put(new JSONObject().put("name",n).put("description",d).put("inputSchema",schema(n)).put("annotations",new JSONObject().put("readOnlyHint",read(n)).put("destructiveHint",false).put("idempotentHint",true).put("openWorldHint",false)));}return out;
    }
    static JSONObject context(JSONObject p,JSONObject s)throws Exception {
        String recipient=p.optString("billing_customer_id");if(recipient.isEmpty())recipient=p.optString("customer_id");
        JSONObject c=PixelMcpWriteTools.find(s.getJSONArray("customers"),p.getString("customer_id")),r=PixelMcpWriteTools.find(s.getJSONArray("customers"),recipient),settings=s.optJSONObject("business_settings");
        return new JSONObject().put("customer",DriveJournalRules.guard(c,"customer")).put("recipient",DriveJournalRules.guard(r,"customer")).put("order",p.optString("order_id").isEmpty()?JSONObject.NULL:DriveJournalRules.guard(PixelMcpWriteTools.find(s.getJSONArray("orders"),p.getString("order_id")),"order")).put("business_settings",settings==null?new JSONObject():settings).put("service_catalog",s.optJSONArray("service_catalog")==null?new JSONArray():s.getJSONArray("service_catalog"));
    }
    static JSONObject query(String n,JSONObject a,JSONObject state)throws Exception{
        McpSchema.validate(a,schema(n));if(n.equals("get_offer_contract"))return OfferContract.data();JSONObject s=PixelMcpReadTools.effective(state);JSONArray offers=s.getJSONArray("offers");
        if(n.equals("get_offer")||n.equals("validate_offer")){JSONObject p=PixelMcpWriteTools.find(offers,a.getString("offer_id"));JSONObject result=new JSONObject().put("offer",p).put("document_contract",OfferRules.contractView(p)).put("workflow_status",p.optString("delivery_status").equals("sent")?"sent":p.optString("delivery_status").equals("share_prepared")?"share_prepared":p.optString("status","draft")).put("record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(p))).put("context_hash",PixelMcpWriteTools.hash(context(p,s)));if(!p.optString("order_id").isEmpty())result.put("order_hash",OwnerJournalRules.version(PixelMcpWriteTools.find(s.getJSONArray("orders"),p.getString("order_id"))));if(n.equals("validate_offer"))result.put("validation",OfferRules.validate(p,s));return result;}
        if(n.equals("get_offer_template")){JSONObject p=PixelMcpWriteTools.find(s.getJSONArray("service_catalog"),a.getString("entry_id"));return new JSONObject().put("entry",p).put("record_hash",PixelMcpWriteTools.hash(p));}
        if(n.equals("get_offer_context")){
            String id=a.getString("customer_id");JSONObject c=PixelMcpWriteTools.find(s.getJSONArray("customers"),id);JSONArray logs=new JSONArray(),orders=new JSONArray(),prices=new JSONArray();
            JSONArray all=s.getJSONArray("worklogs");for(int i=all.length()-1;i>=0&&logs.length()<10;i--)if(id.equals(all.getJSONObject(i).optString("customer_id")))logs.put(all.getJSONObject(i));
            all=s.getJSONArray("orders");for(int i=0;i<all.length()&&orders.length()<10;i++)if(id.equals(all.getJSONObject(i).optString("customer_id"))&&!Arrays.asList("Erledigt","Abgeschlossen","Storniert").contains(all.getJSONObject(i).optString("status")))orders.put(all.getJSONObject(i));
            all=s.getJSONArray("service_catalog");for(int i=0;i<all.length();i++){JSONObject e=all.getJSONObject(i);if(e.optBoolean("active",true)&&e.optBoolean("price_confirmed")&&!e.isNull("default_price"))prices.put(new JSONObject().put("entry",e).put("record_hash",PixelMcpWriteTools.hash(e)));}
            return new JSONObject().put("customer",c).put("billing_configuration",BillingRules.assignment(c,s,false)).put("recent_work",logs).put("open_orders",orders).put("approved_prices",prices).put("business_settings",s.optJSONObject("business_settings"));
        }
        JSONArray rows=n.equals("list_offers")?offers:s.getJSONArray("service_catalog"),out=new JSONArray();int total=0;
        if(n.equals("search_offer_templates")){java.text.Collator collator=java.text.Collator.getInstance(Locale.GERMAN);collator.setStrength(java.text.Collator.PRIMARY);List<JSONObject> ordered=new ArrayList<>();for(int i=0;i<rows.length();i++)ordered.add(rows.getJSONObject(i));ordered.sort((x,y)->{int order=SnapshotReadSupport.natural(x.optString("category"),y.optString("category"),collator);if(order==0)order=SnapshotReadSupport.natural(x.optString("title"),y.optString("title"),collator);return order==0?x.optString("id").compareTo(y.optString("id")):order;});rows=new JSONArray(ordered);}
        for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(!a.optString("customer_id").isEmpty()&&!a.getString("customer_id").equals(p.optString("customer_id")))continue;if(!a.optString("document_type").isEmpty()&&!a.getString("document_type").equals(p.optString("document_type")))continue;if(!a.optString("status").isEmpty()&&!a.getString("status").equals(p.optString("status"))&&!a.getString("status").equals(p.optString("delivery_status")))continue;if(!SnapshotReadSupport.matches(a.optString("query"),SnapshotReadSupport.text(p,"title","category","description","assumptions","exclusions","legal_notes","offer_number")))continue;if(total++>=a.optInt("offset",0)&&out.length()<a.optInt("limit",30))out.put(new JSONObject().put(n.equals("list_offers")?"offer":"entry",p).put("record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(p))));}
        return new JSONObject().put(n.equals("list_offers")?"offers":"entries",out).put("total",total).put("offset",a.optInt("offset")).put("limit",a.optInt("limit",30)).put("next_cursor",a.optInt("offset")+out.length()<total?a.optInt("offset")+out.length():JSONObject.NULL);
    }
}
