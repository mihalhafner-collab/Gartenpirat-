package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Invoice use cases. Effects pass through the existing owner repository transaction. */
final class InvoiceApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    InvoiceApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "request_invoice_issue":case "create_invoice_correction":case "update_invoice_payment":{
                JSONObject invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),a.getString("invoice_id"));String expected=name.equals("update_invoice_payment")?OwnerJournalRules.version(invoice):PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice",invoice));if(!expected.equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Rechnung wurde geändert.");
                JSONObject p=new JSONObject(a.toString()).put("id",invoice.getString("id")).put("updated_at",stamp);p.remove("request_id");p.remove("invoice_id");p.put("_record_base",DriveJournalRules.guard(invoice,"invoice"));
                if(name.equals("request_invoice_issue")){TestDataRules.noDelivery(invoice,all);
                    InvoiceDraftRules.normalize(new JSONObject(invoice.toString()),invoice,all);for(int i=0;i<repository.state().getJSONArray("pending").length();i++){JSONObject old=repository.state().getJSONArray("pending").getJSONObject(i);if(Arrays.asList("invoice_issue","owner_invoice_issue","pixel_invoice_issue_v1").contains(old.optString("kind"))&&invoice.getString("id").equals(old.getJSONObject("payload").optString("id")))throw new IllegalArgumentException("Abschluss bereits vorgemerkt.");}p.put("_expected_invoice",DriveJournalRules.guard(invoice,"invoice"));JSONObject works=new JSONObject();JSONArray links=invoice.optJSONArray("worklog_ids");for(int j=0;links!=null&&j<links.length();j++){String linked=links.getString(j);works.put(linked,DriveJournalRules.guard(PixelMcpWriteTools.find(all.getJSONArray("worklogs"),linked),"worklog"));}p.put("_expected_worklogs",works);p.put("_expected_settings_hash",PixelMcpWriteTools.hash(all.getJSONObject("business_settings"))).put("_expected_recipient_hash",OwnerJournalRules.version(PixelMcpWriteTools.find(all.getJSONArray("customers"),invoice.getString("customer_id"))));InvoiceIssueRules.prepare(all,new JSONObject().put("payload",p),java.time.LocalDate.now(java.time.ZoneId.of("Europe/Vienna")));return repository.enqueue("pixel_invoice_issue_v1",p,request).put("finalization","pixel_drive_numbering_pending");
                }
                if(name.equals("create_invoice_correction")){if(invoice.optString("issued_at").isEmpty())throw new IllegalArgumentException("Korrekturen benötigen einen ausgestellten Originalbeleg.");p.put("_expected_invoice",DriveJournalRules.guard(invoice,"invoice"));return repository.enqueue("invoice_correction",p,request).put("correction_state","draft_pending").put("requires_issue_confirmation",true);}
                OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind","invoice_payment").put("payload",p),new HashMap<>(),new JSONObject());return repository.enqueue("invoice_payment",p,request);
            }
            case "prepare_invoice_share":{
                JSONObject inv=PixelMcpWriteTools.find(all.getJSONArray("invoices"),a.getString("invoice_id"));if(inv.optString("issued_at").isEmpty()||(inv.optString("pdf_base64").isEmpty()&&inv.optString("pdf_storage_name").isEmpty()))throw new IllegalArgumentException("Original-PDF einer ausgestellten Rechnung fehlt.");JSONObject handoff=new JSONObject().put("invoice_id",inv.getString("id")).put("channel",a.getString("channel"));repository.putOwnerValue("invoice_share_handoff",handoff.toString());return handoff.put("state","device_interaction_required").put("sent",false).put("delivered",false);
            }
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
}
