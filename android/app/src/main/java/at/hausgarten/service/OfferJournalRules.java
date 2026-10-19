package at.hausgarten.service;
import org.json.*;
import java.util.*;

final class OfferJournalRules {
    static boolean handles(String kind){return Arrays.asList("offer_finalize","offer_convert").contains(kind);}
    static void apply(JSONObject s,JSONObject a)throws Exception {
        JSONObject p=a.getJSONObject("payload"),old=PixelMcpWriteTools.find(s.getJSONArray("offers"),p.getString("id"));
        if(!PixelMcpWriteTools.hash(DriveJournalRules.clean(old)).equals(p.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Angebot wurde inzwischen verändert.");
        if(!p.optBoolean("confirmed"))throw new IllegalArgumentException("Ausdrückliche Bestätigung erforderlich.");
        JSONObject next=new JSONObject(old.toString());
        if(a.getString("kind").equals("offer_finalize")){
            if(!PixelMcpWriteTools.hash(OfferTools.context(old,s)).equals(p.getString("expected_context_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Kunde, Aussteller oder Preise wurden geändert. Erneut validieren.");
            OfferRules.normalize(next,old,s);JSONObject v=OfferRules.validate(next,s);if(!v.getBoolean("valid"))throw new IllegalArgumentException(v.getJSONArray("errors").join("; "));
            next.put("offer_number","ANG-"+next.getString("issue_date").substring(0,4)+"-"+PixelMcpWriteTools.hash(next.getString("id")).substring(0,16).toUpperCase(Locale.ROOT));
            for(int i=0;i<s.getJSONArray("offers").length();i++){JSONObject other=s.getJSONArray("offers").getJSONObject(i);if(!other.getString("id").equals(next.getString("id"))&&next.getString("offer_number").equals(other.optString("offer_number")))throw new IllegalArgumentException("Angebotsnummer ist bereits vergeben.");}
            next.put("status","finalized").put("finalized_at",a.getString("created_at")).put("issuer",OfferTools.context(old,s).getJSONObject("business_settings")).put("recipient",OfferTools.context(old,s).getJSONObject("recipient"));
            for(String key:new String[]{"document_id","pdf_storage_name","pdf_sha256","pdf_byte_size"})if(p.has(key))next.put(key,p.get(key));
        }else{
            if(!old.optString("status").equals("finalized"))throw new IllegalArgumentException("Nur ein finalisiertes, ausdrücklich angenommenes Angebot übernehmen.");
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
