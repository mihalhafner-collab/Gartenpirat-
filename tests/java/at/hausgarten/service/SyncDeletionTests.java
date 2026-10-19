package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Original production rules with synthetic state only; no Android/Drive emulation claim. */
public final class SyncDeletionTests {
    static int count;interface Check{void run()throws Exception;}
    static void yes(boolean b){count++;if(!b)throw new AssertionError("sync/delete check "+count);}
    static void bad(Check c)throws Exception{boolean rejected=false;try{c.run();}catch(Exception expected){rejected=true;}yes(rejected);}
    static JSONObject state()throws Exception {JSONObject s=OffersAndPhotosTests.snapshot();PixelDriveState.prepare(s);return s;}
    static JSONObject event(String id,String kind,JSONObject p)throws Exception{return OffersAndPhotosTests.event(id,kind,p);}
    static JSONObject archive(JSONObject s,String entity,String id,String action)throws Exception {
        JSONObject preview=DeletionRules.preview(s,entity,id);yes(preview.getBoolean("allowed"));yes(!preview.getBoolean("physical_delete")&&!preview.getBoolean("cascade"));
        return event(action,"archive",DeletionRules.payload(s,entity,id,preview.getString("expected_record_hash"),"2026-10-18T12:00:00Z"));
    }
    public static void main(String[] args)throws Exception { SyntheticBusinessData.verify();
        JSONObject configured=SyntheticBusinessData.object().put("configured",true),empty=SyntheticBusinessData.object().put("total",0).put("review",0).put("pending_upload",0);
        yes(SyncPolicy.automaticAllowed(configured,1000));yes(!SyncPolicy.automaticAllowed(SyntheticBusinessData.object().put("configured",false),1000));
        yes(!SyncPolicy.automaticAllowed(SyntheticBusinessData.object(configured.toString()).put("user_action_required",true),1000));
        yes(!SyncPolicy.automaticAllowed(SyntheticBusinessData.object(configured.toString()).put("retry_count",8),1000));
        yes(!SyncPolicy.automaticAllowed(SyntheticBusinessData.object(configured.toString()).put("next_retry",1001),1000));
        yes(SyncPolicy.automaticAllowed(SyntheticBusinessData.object(configured.toString()).put("next_retry",1000),1000));
        yes(SyncPolicy.retryDelay(100,0)==900000);yes(SyncPolicy.retryDelay(1,120000)==120000);yes(SyncPolicy.retryDelay(1,Long.MAX_VALUE)==86400000);
        yes(SyncPolicy.describe(SyntheticBusinessData.object(),empty,false,0).getString("state").equals("not_connected"));
        JSONObject failed=SyntheticBusinessData.object(configured.toString()).put("drive_sync_failed",true).put("user_action_required",true).put("last_error_code","PERMISSION_REQUIRED");
        JSONObject publicFailure=SyncPolicy.describe(failed,empty,false,0);yes(publicFailure.getString("next_action").equals("connect_drive"));yes(publicFailure.getString("state").equals("action_required"));
        failed.put("user_action_required",false).put("next_retry",1000);yes(SyncPolicy.describe(failed,empty,false,0).getString("state").equals("retry_wait"));
        yes(SyncPolicy.describe(configured,SyntheticBusinessData.object().put("pending_upload",2).put("review",7),false,0).getString("state").equals("pending"));
        yes(SyncPolicy.describe(configured,SyntheticBusinessData.object().put("review",7),false,0).getString("state").equals("review_required"));
        JSONObject proof=SyntheticBusinessData.object(configured.toString()).put("last_sync","now").put("cloud_confirmed",true).put("confirmation_scope","provider_readback");
        yes(!SyncPolicy.describe(proof,empty,false,0).getBoolean("cloud_confirmed"));proof.put("confirmation_scope","drive_server_manifest");yes(SyncPolicy.describe(proof,empty,false,0).getBoolean("cloud_confirmed"));
        yes(!SyncPolicy.describe(proof,SyntheticBusinessData.object().put("review",1),false,0).getBoolean("cloud_confirmed"));yes(SyncPolicy.describe(proof,empty,true,0).getString("state").equals("syncing"));

        JSONObject s=state(),photo=SyntheticBusinessData.object("{id:'qa_p',customer_id:'qa_c',caption:'alt',role:'documentation',updated_at:'t1',content_sha256:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',byte_size:10}");s.getJSONArray("photos").put(photo);
        JSONObject patch=SyntheticBusinessData.object(photo.toString()).put("role","before").put("caption","neu");ManualChangeRules.prepare("photo",patch,photo,"t2");
        JSONObject attachment=SyntheticBusinessData.object().put("role","documentation").put("content_sha256","a".repeat(64));
        JSONObject displayed=PhotoJournalRules.forSnapshot(patch,attachment);yes(PixelMcpWriteTools.hash(patch).equals(PixelMcpWriteTools.hash(displayed)));
        JSONObject a=event("photo-edit","photo",patch),readEvent=event("photo-edit","photo",displayed);
        JSONObject projection=PixelMcpReadTools.effective(SyntheticBusinessData.object().put("master",s).put("pending",new JSONArray().put(readEvent)));
        yes(projection.getJSONArray("photos").getJSONObject(0).getString("role").equals("before"));
        JSONObject remove=archive(projection,"photo","qa_p","photo-trash");JSONArray chain=new JSONArray().put(a).put(remove);yes(DriveJournalRules.apply(s,chain).length()==0);
        JSONObject saved=s.getJSONObject("owner_archive").getJSONObject("photo:qa_p");yes(saved.getJSONObject("record").getString("role").equals("before"));
        JSONObject reload=SyntheticBusinessData.object(s.toString());String receipts=PixelMcpWriteTools.hash(reload.getJSONObject("drive_action_receipts"));yes(DriveJournalRules.apply(reload,chain).length()==0);yes(PixelMcpWriteTools.hash(reload.getJSONObject("drive_action_receipts")).equals(receipts));
        JSONObject undo=event("photo-restore","restore",SyntheticBusinessData.object().put("entity","photo").put("id","qa_p").put("expected_record_hash",OwnerJournalRules.version(saved)));
        yes(DriveJournalRules.apply(reload,new JSONArray().put(undo)).length()==0);yes(reload.getJSONArray("photos").getJSONObject(0).getString("content_sha256").equals("a".repeat(64)));yes(reload.getJSONArray("photos").getJSONObject(0).getString("role").equals("before"));
        JSONObject oldInline=SyntheticBusinessData.object().put("data","data:image/gif;base64,SYNTHETIC").put("role","after");yes(PhotoJournalRules.forSnapshot(oldInline,attachment).getString("role").equals("after"));yes(oldInline.has("data"));yes(!PhotoJournalRules.forSnapshot(oldInline,attachment).has("data"));

        JSONObject catalog=state(),template=SyntheticBusinessData.object("{id:'qa_svc',title:'QA Vorlage',unit:'h',category:'Arbeitsstunde',default_price:null,active:true,price_confirmed:false,updated_at:'t1'}");catalog.getJSONArray("service_catalog").put(template);
        JSONObject offer=OffersAndPhotosTests.draft();OfferRules.normalize(offer,null,catalog);catalog.getJSONArray("offers").put(offer);
        String customerHash=PixelMcpWriteTools.hash(catalog.getJSONArray("customers"));JSONObject dropOffer=archive(catalog,"offer","qa_off","trash-offer"),dropTemplate=archive(catalog,"service_template","qa_svc","trash-template");
        yes(DriveJournalRules.apply(catalog,new JSONArray().put(dropOffer).put(dropTemplate)).length()==0);yes(catalog.getJSONArray("offers").length()==0);yes(!catalog.getJSONArray("service_catalog").getJSONObject(0).getBoolean("active"));
        yes(customerHash.equals(PixelMcpWriteTools.hash(catalog.getJSONArray("customers"))));yes(catalog.getJSONArray("invoices").length()==0);
        for(String entity:new String[]{"offer","service_template"}){String id=entity.equals("offer")?"qa_off":"qa_svc";JSONObject entry=catalog.getJSONObject("owner_archive").getJSONObject(entity+":"+id);yes(DriveJournalRules.apply(catalog,new JSONArray().put(event("restore-"+entity,"restore",SyntheticBusinessData.object().put("entity",entity).put("id",id).put("expected_record_hash",OwnerJournalRules.version(entry))))).length()==0);}
        yes(catalog.getJSONArray("offers").length()==1);yes(catalog.getJSONArray("service_catalog").getJSONObject(0).getBoolean("active"));
        catalog.getJSONArray("offers").getJSONObject(0).put("status","finalized");yes(!DeletionRules.preview(catalog,"offer","qa_off").getBoolean("allowed"));
        JSONObject invoiced=state(),issued=SyntheticBusinessData.object("{id:'qa_i',customer_id:'qa_c',status:'Gestellt',issued_at:'2026-10-18',positions:[]}");invoiced.getJSONArray("invoices").put(issued);yes(!DeletionRules.preview(invoiced,"invoice","qa_i").getBoolean("allowed"));
        JSONObject linked=state(),job=SyntheticBusinessData.object("{id:'qa_o',customer_id:'qa_c',title:'QA',updated_at:'t1'}");linked.getJSONArray("orders").put(job);JSONObject preview=DeletionRules.preview(linked,"order","qa_o");linked.getJSONArray("photos").put(SyntheticBusinessData.object().put("id","qa_p").put("customer_id","qa_c").put("order_id","qa_o"));
        yes(DeletionRules.payload(linked,"order","qa_o",preview.getString("expected_record_hash"),"t2").getString("deletion_policy").equals("recoverable_v2"));yes(linked.getJSONArray("orders").length()==1);yes(DeletionRules.preview(linked,"order","qa_o").getBoolean("allowed"));
        bad(()->DeletionRules.payload(linked,"customer","qa_c","0".repeat(64),"t2"));
        JSONObject independent=state();independent.getJSONArray("orders").put(SyntheticBusinessData.object(job.toString()));JSONObject archiveJob=archive(independent,"order","qa_o","valid-delete");
        JSONObject oldDelete=event("historical-delete","delete",SyntheticBusinessData.object().put("id","qa_c").put("entity","customer"));JSONArray both=new JSONArray().put(oldDelete).put(archiveJob);
        yes(DriveJournalRules.apply(independent,both).length()==1);yes(independent.getJSONArray("customers").length()==1);yes(independent.getJSONArray("orders").length()==0);yes(independent.getJSONObject("drive_action_receipts").has("valid-delete"));yes(!independent.getJSONObject("drive_action_receipts").has("historical-delete"));

        JSONObject canceled=state();canceled.getJSONArray("orders").put(SyntheticBusinessData.object(job.toString()));JSONObject staleArchive=archive(canceled,"order","qa_o","stale-delete");canceled.getJSONArray("orders").getJSONObject(0).put("title","QA parallel version").put("updated_at","t2");
        yes(DriveJournalRules.apply(canceled,new JSONArray().put(staleArchive)).length()==1);String unchanged=PixelDriveState.integrity(canceled).getString("business_state_hash");
        JSONObject cancelView=PixelMcpOwnerTools.conflicts(SyntheticBusinessData.object().put("master",canceled).put("pending",new JSONArray().put(staleArchive)),SyntheticBusinessData.object()).getJSONArray("conflicts").getJSONObject(0);
        yes(cancelView.getBoolean("resolution_available"));yes(cancelView.getJSONObject("current").getString("title").equals("QA parallel version"));
        JSONObject decision=SyntheticBusinessData.object().put("client_id","stale-delete").put("expected_action_hash",cancelView.getString("action_hash")).put("expected_current_hash",cancelView.getString("current_hash")).put("resolution","keep_current");
        JSONObject cancel=event("withdraw-delete","conflict_resolution",decision);JSONArray withdrawal=new JSONArray().put(staleArchive).put(cancel);
        yes(DriveJournalRules.apply(canceled,withdrawal).length()==0);yes(PixelDriveState.integrity(canceled).getString("business_state_hash").equals(unchanged));yes(canceled.getJSONObject("owner_conflict_history").has("stale-delete"));
        yes(canceled.getJSONObject("drive_action_receipts").getJSONObject("stale-delete").getString("state").equals("resolved"));
        yes(DriveJournalRules.apply(canceled,withdrawal).length()==0);JSONObject fresh=archive(canceled,"order","qa_o","fresh-delete");fresh.getJSONObject("payload").put("_sync_predecessors",new JSONArray().put("stale-delete").put("withdraw-delete"));
        yes(DriveJournalRules.apply(canceled,new JSONArray().put(staleArchive).put(cancel).put(fresh)).length()==0);yes(canceled.getJSONArray("orders").length()==0);yes(canceled.getJSONObject("owner_archive").getJSONObject("order:qa_o").getJSONObject("record").getString("title").equals("QA parallel version"));
        yes(!DeletionRules.cancelable(staleArchive,"ACTION_ID_CONFLICT"));yes(!DeletionRules.cancelable(staleArchive,"AUTH_REQUIRED"));yes(!DeletionRules.cancelable(oldDelete,"INVALID_ACTION"));
        JSONObject numbered=state();numbered.getJSONArray("invoices").put(SyntheticBusinessData.object("{id:'qa_numbered',customer_id:'qa_c',status:'Storniert',invoice_number:'HG-2026-0012'}"));yes(!DeletionRules.preview(numbered,"invoice","qa_numbered").getBoolean("allowed"));

        JSONArray attachments=new JSONArray();for(int i=0;i<100;i++)attachments.put(SyntheticBusinessData.object().put("photo_id","qa_p"+i).put("content_sha256",String.format("%064x",i)).put("byte_size",2*1024*1024));
        Set<String> selected=SyncMediaPlan.select(attachments);yes(selected.size()==4);yes(selected.contains("0".repeat(64)));yes(!selected.contains(String.format("%064x",4)));
        attachments.getJSONObject(0).put("byte_size",20*1024*1024);yes(SyncMediaPlan.select(attachments).size()==1);
        JSONArray duplicate=new JSONArray();for(int i=0;i<100;i++)duplicate.put(SyntheticBusinessData.object().put("photo_id","qa_p"+i).put("content_sha256","a".repeat(64)).put("byte_size",100));yes(SyncMediaPlan.select(duplicate).size()==1);
        JSONObject pendingMedia=state().put("drive_journal_errors",new JSONArray().put(SyntheticBusinessData.object().put("client_id","media-wait").put("code","MEDIA_TRANSFER_PENDING")));
        JSONObject queue=DriveJournalRules.queueStatus(pendingMedia,new JSONArray().put(event("media-wait","photo",SyntheticBusinessData.object().put("id","qa_p"))));yes(queue.getInt("pending_upload")==1&&queue.getInt("review")==0);
        PixelMcpReadTools tools=new PixelMcpReadTools(()->SyntheticBusinessData.object().put("master",state()).put("pending",new JSONArray()));
        JSONArray advertised=tools.call("get_capabilities",SyntheticBusinessData.object()).getJSONObject("data").getJSONArray("tools");
        // Read-only runtime has no upload. An enabled runtime must retain schema fields, not binary bytes.
        PixelMcpReadTools enabled=new PixelMcpReadTools(()->SyntheticBusinessData.object().put("master",state()).put("pending",new JSONArray()),new PixelMcpWriteTools.Sink(){public boolean writable(){return true;}public JSONObject execute(String n,JSONObject v){return SyntheticBusinessData.object();}public JSONObject status(String id,JSONObject state){return SyntheticBusinessData.object();}});
        JSONArray all=enabled.call("get_capabilities",SyntheticBusinessData.object()).getJSONObject("data").getJSONArray("tools");boolean chunk=false,delete=false;for(int i=0;i<all.length();i++){JSONObject t=all.getJSONObject(i);if(t.getString("name").equals("write_file_chunk")){chunk=true;yes(t.getJSONObject("inputSchema").getJSONObject("properties").has("chunk_base64"));}if(t.getString("name").equals("delete_entity"))delete=true;}yes(chunk&&delete);bad(()->tools.call("delete_entity",SyntheticBusinessData.object()));
        for(String entity:DeletionRules.ENTITIES)PixelMcpOwnerTools.validate("delete_entity",SyntheticBusinessData.object().put("request_id","delete-schema-"+entity).put("entity",entity).put("entity_id","qa").put("expected_record_hash","a".repeat(64)));
        yes(true);System.out.println("PASS "+count+" sync policy, bounded media, immutable photos, recoverable deletion, receipt and stale-version checks (synthetic JVM)");
    }
}
