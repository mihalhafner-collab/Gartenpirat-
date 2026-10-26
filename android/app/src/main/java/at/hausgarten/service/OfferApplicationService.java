package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Offer use cases. Effects pass through the existing owner repository transaction. */
final class OfferApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    OfferApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "record_offer_acceptance":case "decline_offer":return new OfferDecisionService(repository).perform(name,a,request);
            case "create_and_validate_offer":{
                JSONObject created=perform("create_offer_draft",a,request+"_draft");String id=created.getString("offer_id");JSONObject row=PixelMcpWriteTools.find(repository.effective().getJSONArray("offers"),id);JSONObject result=perform("validate_offer_draft",new JSONObject().put("offer_id",id).put("expected_record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(row))),request+"_validate");return result.put("created",true);
            }
            case "validate_offer_draft":{
                JSONObject row=PixelMcpWriteTools.find(all.getJSONArray("offers"),a.getString("offer_id"));JSONObject payload=new JSONObject().put("id",row.getString("id")).put("expected_record_hash",a.getString("expected_record_hash"));JSONObject event=new JSONObject().put("client_id",request).put("kind","offer_validate").put("created_at",stamp).put("payload",payload);OfferJournalRules.apply(new JSONObject(all.toString()),event);return repository.enqueue("offer_validate",payload,request).put("offer_id",row.getString("id")).put("validation_state","validated");
            }
            case "create_offer_draft":case "update_offer_draft":case "duplicate_offer":{
                JSONObject old=null,p;
                if(name.equals("update_offer_draft")){old=PixelMcpWriteTools.find(all.getJSONArray("offers"),a.getString("offer_id"));if(!PixelMcpWriteTools.hash(DriveJournalRules.clean(old)).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Angebot wurde geändert.");p=DriveJournalRules.clean(old);JSONObject changes=a.getJSONObject("updates");for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();p.put(key,changes.get(key));}}
                else{p=new JSONObject();JSONObject source=name.equals("duplicate_offer")?PixelMcpWriteTools.find(all.getJSONArray("offers"),a.getString("offer_id")):a;for(String key:OfferRules.FIELDS)if(source.has(key))p.put(key,source.get(key));p.put("id","off_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("offer_number","").put("source","owner_app_mcp").put("created_at",stamp);}
                OfferRules.normalize(p,old,all);if(name.equals("update_offer_draft")&&a.getJSONObject("updates").optBoolean("binding_confirmed")){p.put("binding_confirmed",true).put("binding_confirmation_hash",OfferRules.bindingHash(p));OfferRules.normalize(p,old,all);}
                ManualChangeRules.prepare("offer",p,old,stamp);JSONObject saved=repository.enqueue("offer",p,request).put("offer_id",p.getString("id")).put("record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(PixelMcpWriteTools.find(repository.effective().getJSONArray("offers"),p.getString("id")))));if(name.equals("duplicate_offer")&&OfferDecisionRules.local(repository.state()))new OfferDecisionService(repository).linkRevision(a.getString("offer_id"),p.getString("id"),request);return saved;
            }
            case "create_catalog_entry":case "update_catalog_entry":{
                JSONObject p=new JSONObject(),old=null;if(name.equals("update_catalog_entry")){old=PixelMcpWriteTools.find(all.getJSONArray("service_catalog"),a.getString("entry_id"));if(!PixelMcpWriteTools.hash(old).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Baustein wurde geändert.");p=new JSONObject(old.toString());}
                JSONObject changes=name.equals("update_catalog_entry")?a.getJSONObject("updates"):a;JSONObject fields=OfferTools.catalogFields();for(Iterator<String> it=fields.keys();it.hasNext();){String key=it.next();if(changes.has(key))p.put(key,changes.get(key));}if(old==null)p.put("id","svc_"+PixelMcpWriteTools.hash(request).substring(0,32));DriveJournalRules.validateTemplate(p);ManualChangeRules.prepare("service_template",p,old,stamp);return repository.enqueue("service_template",p,request).put("entry_id",p.getString("id"));
            }
            case "finalize_offer":case "convert_offer_to_order":{
                JSONObject existingOffer=PixelMcpWriteTools.find(all.getJSONArray("offers"),a.getString("offer_id"));if(name.equals("convert_offer_to_order")&&existingOffer.optString("status").equals("accepted")&&!existingOffer.optString("converted_order_id").isEmpty()){if(!AppointmentRules.hash(existingOffer).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Angebot wurde geändert.");return new JSONObject().put("offer_id",existingOffer.getString("id")).put("order_id",existingOffer.getString("converted_order_id")).put("already_converted",true).put("invoice_created",false);}
                if(name.equals("convert_offer_to_order"))new OfferDecisionService(repository).requireConversionDecision(existingOffer);
                JSONObject p=new JSONObject(a.toString()).put("id",a.getString("offer_id"));p.remove("request_id");p.remove("offer_id");String kind=name.equals("finalize_offer")?"offer_finalize":"offer_convert";
                if(!a.optString("existing_order_id").isEmpty())p.put("_expected_order",DriveJournalRules.guard(PixelMcpWriteTools.find(all.getJSONArray("orders"),a.getString("existing_order_id")),"order"));
                JSONObject event=new JSONObject().put("client_id",request).put("kind",kind).put("payload",p).put("created_at",stamp),copy=new JSONObject(all.toString());OfferJournalRules.apply(copy,event);
                if(kind.equals("offer_finalize")){JSONObject finalized=PixelMcpWriteTools.find(copy.getJSONArray("offers"),p.getString("id")),media=repository.createDocument(finalized);for(Iterator<String> it=media.keys();it.hasNext();){String key=it.next();p.put(key,media.get(key));}}
                return repository.enqueue(kind,p,request).put("offer_id",p.getString("id")).put("order_id",PixelMcpWriteTools.find(copy.getJSONArray("offers"),p.getString("id")).optString("converted_order_id")).put("invoice_created",false);
            }
            case "prepare_offer_share":case "prepare_offer_delivery":case "confirm_offer_delivery":{
                JSONObject offer=PixelMcpWriteTools.find(all.getJSONArray("offers"),a.getString("offer_id"));if(!PixelMcpWriteTools.hash(DriveJournalRules.clean(offer)).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Angebot wurde geändert.");
                if(!Arrays.asList("finalized","accepted").contains(offer.optString("status")))throw new IllegalArgumentException("Dokument zuerst finalisieren.");repository.readDocument(offer);
                JSONObject payload=new JSONObject().put("id",offer.getString("id")).put("expected_record_hash",a.getString("expected_record_hash"));String kind="offer_share";JSONObject receipt=null;
                if(name.equals("prepare_offer_delivery")){TestDataRules.noDelivery(offer,all);String address=OfferDeliveryClient.oneAddress(a.getString("recipient_email"));if(address.isEmpty()||!address.equals(a.getString("recipient_email")))throw new IllegalArgumentException("Eine eindeutige ausdrücklich bestätigte Empfängeradresse erforderlich.");payload.put("recipient_email",address);}
                if(name.equals("confirm_offer_delivery")){TestDataRules.noDelivery(offer,all);receipt=repository.verifyDelivery(a.getString("message_id"),offer);payload.put("receipt",receipt).put("confirmed",true);kind="offer_delivery";}
                JSONObject event=new JSONObject().put("client_id",request).put("kind",kind).put("payload",payload).put("created_at",stamp);OfferJournalRules.apply(new JSONObject(all.toString()),event);JSONObject out=repository.enqueue(kind,payload,request).put("offer_id",offer.getString("id")).put("sent",receipt!=null).put("state",receipt!=null?"sent":offer.optBoolean("test_record")?"test_preview_only":name.equals("prepare_offer_delivery")?"mail_provider_handoff":"device_interaction_required");
                out.put("document",new JSONObject().put("resource_type","offer").put("resource_id",offer.getString("id")).put("sha256",offer.getString("pdf_sha256")).put("file_name",offer.optString("pdf_filename",offer.getString("offer_number")+".pdf")));
                if(name.equals("prepare_offer_delivery"))out.put("recipient_email",payload.getString("recipient_email")).put("subject",OfferRules.label(offer.getString("document_type"))+" "+offer.getString("offer_number")).put("body",offer.getString("title")+"\n"+offer.optString("binding_notice")+"\n"+offer.optString("payment_terms"));
                if(name.equals("prepare_offer_share")&&!offer.optBoolean("test_record"))repository.putOwnerValue("offer_share_handoff",offer.getString("id"));if(receipt!=null)out.put("delivery_receipt",receipt);return out;
            }
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
}
