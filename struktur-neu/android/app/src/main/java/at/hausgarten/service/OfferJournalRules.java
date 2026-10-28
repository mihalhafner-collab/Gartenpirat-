package at.hausgarten.service;
import org.json.*;
import java.util.*;

final class OfferJournalRules {
    static boolean handles(String kind){return Arrays.asList("offer_finalize","offer_convert","offer_validate","offer_share","offer_delivery").contains(kind);}
    static void apply(JSONObject s,JSONObject a)throws Exception {
        JSONObject p=a.getJSONObject("payload"),old=PixelMcpWriteTools.find(s.getJSONArray("offers"),p.getString("id"));
        if(!PixelMcpWriteTools.hash(DriveJournalRules.clean(old)).equals(p.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Angebot wurde inzwischen verändert.");
        if(!Arrays.asList("offer_validate","offer_share").contains(a.getString("kind"))&&!p.optBoolean("confirmed"))throw new IllegalArgumentException("Ausdrückliche Bestätigung erforderlich.");
        JSONObject next=new JSONObject(old.toString());
        String kind=a.getString("kind");
        if(kind.equals("offer_validate")){
            OfferRules.normalize(next,old,s);JSONObject validation=OfferRules.validate(next,s);if(!validation.getBoolean("valid"))throw new PixelMcpWriteTools.Rejected("DOCUMENT_VALIDATION_FAILED",validation.getJSONArray("errors").join("; "));
            next.put("status","validated").put("validated_at",a.getString("created_at")).put("validation_context_hash",PixelMcpWriteTools.hash(OfferTools.context(old,s)));
        }else if(kind.equals("offer_share")||kind.equals("offer_delivery")){
            if(!Arrays.asList("finalized","accepted").contains(old.optString("status")))throw new IllegalArgumentException("Dokument zuerst finalisieren.");if(kind.equals("offer_delivery")||p.has("recipient_email"))TestDataRules.noDelivery(old,s);
            if(kind.equals("offer_share")){
                if(!old.optString("delivery_status").equals("sent"))next.put("delivery_status","share_prepared");
                next.put("share_prepared_at",a.getString("created_at"));if(p.has("recipient_email"))next.put("delivery_recipient",p.getString("recipient_email"));
            }else{
                JSONObject receipt=p.getJSONObject("receipt");if(!"pixel_gmail_readback".equals(receipt.optString("verified_by"))||!old.optString("pdf_sha256").equals(receipt.optString("pdf_sha256"))||!old.optString("delivery_recipient").equals(receipt.optString("recipient_email"))||receipt.optString("message_id").isEmpty())throw new IllegalArgumentException("Geprüfter Versandbeleg fehlt.");
                next.put("delivery_status","sent").put("delivery_receipt",receipt).put("sent_at",receipt.getString("sent_at"));
            }
        }else if(kind.equals("offer_finalize")){
            if(!PixelMcpWriteTools.hash(OfferTools.context(old,s)).equals(p.getString("expected_context_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Kunde, Aussteller oder Preise wurden geändert. Erneut validieren.");
            OfferRules.normalize(next,old,s);JSONObject v=OfferRules.validate(next,s);if(!v.getBoolean("valid"))throw new IllegalArgumentException(v.getJSONArray("errors").join("; "));
            next.put("offer_number",(next.optBoolean("test_record")?"TEST-":"")+(next.optString("document_type").startsWith("estimate_")?"KV-":"ANG-")+next.getString("issue_date").substring(0,4)+"-"+PixelMcpWriteTools.hash(next.getString("id")).substring(0,16).toUpperCase(Locale.ROOT));
            for(int i=0;i<s.getJSONArray("offers").length();i++){JSONObject other=s.getJSONArray("offers").getJSONObject(i);if(!other.getString("id").equals(next.getString("id"))&&next.getString("offer_number").equals(other.optString("offer_number")))throw new IllegalArgumentException("Angebotsnummer ist bereits vergeben.");}
            next.put("status","finalized").put("finalized_at",a.getString("created_at")).put("issuer",OfferTools.context(old,s).getJSONObject("business_settings")).put("recipient",OfferTools.context(old,s).getJSONObject("recipient"));
            for(String key:new String[]{"document_id","pdf_storage_name","pdf_sha256","pdf_byte_size","pdf_filename"})if(p.has(key))next.put(key,p.get(key));
        }else{
            if(!old.optString("status").equals("finalized"))throw new IllegalArgumentException("Nur ein finalisiertes, ausdrücklich angenommenes Angebot übernehmen.");
            if(old.optBoolean("test_record"))throw new PixelMcpWriteTools.Rejected("TEST_CONVERSION_BLOCKED","Testdokumente nicht in produktive Aufträge übernehmen.");
            String id=p.optString("existing_order_id");JSONObject order;
            if(!id.isEmpty()){
                order=new JSONObject(PixelMcpWriteTools.find(s.getJSONArray("orders"),id).toString());if(!old.getString("customer_id").equals(order.getString("customer_id")))throw new IllegalArgumentException("Angebot und Auftrag gehören zu verschiedenen Kunden.");
                if(!OwnerJournalRules.version(order).equals(p.optString("expected_order_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Auftrag wurde geändert.");
            }else{ id="ord_offer_"+PixelMcpWriteTools.hash(a.getString("client_id")).substring(0,32);if(OwnerJournalRules.findOrNull(s.getJSONArray("orders"),id)!=null)throw new IllegalArgumentException("Auftrags-ID ist bereits belegt.");JSONObject c=PixelMcpWriteTools.find(s.getJSONArray("customers"),old.getString("customer_id"));order=new JSONObject().put("id",id).put("customer_id",old.getString("customer_id")).put("billing_customer_id",(old.optString("billing_customer_id").isEmpty()?old.getString("customer_id"):old.getString("billing_customer_id"))).put("title",old.getString("title")).put("location",(old.optString("location").isEmpty()?(c.optString("street")+", "+c.optString("postal_code")+" "+c.optString("city")).trim():old.getString("location"))).put("status","Geplant").put("created_at",a.getString("created_at"));}
            JSONArray refs=order.optJSONArray("offer_refs");if(refs==null)refs=new JSONArray();for(int i=0;i<refs.length();i++)if(old.getString("id").equals(refs.getJSONObject(i).optString("offer_id")))throw new IllegalArgumentException("Angebot ist bereits mit dem Auftrag verbunden.");refs.put(new JSONObject().put("offer_id",old.getString("id")).put("accepted_record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(old))).put("accepted_at",a.getString("created_at")).put("role",order.optString("offer_id").isEmpty()?"main":"addendum"));order.put("offer_refs",refs);if(order.optString("offer_id").isEmpty())order.put("offer_id",old.getString("id"));order.put("description",(order.optString("description")+"\n"+old.getString("offer_number")+"\n"+OfferRules.positionText(old.getJSONArray("positions"))).trim()).put("updated_at",a.getString("created_at"));DriveJournalRules.validateChange("order",order,null,s);OwnerJournalRules.replace(s,"orders",id,order);next.put("status","accepted").put("converted_order_id",id);
        }
        next.put("updated_at",a.getString("created_at"));OwnerJournalRules.replace(s,"offers",old.getString("id"),next);
    }
}
