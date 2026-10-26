package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Synthetic production-rule regression: relationships, stale forms, receipts and file ownership. */
public final class TrashWorkflowTests {
    static int count;
    interface Case { void run()throws Exception; }
    static void yes(boolean b){count++;if(!b)throw new AssertionError("trash workflow "+count);}
    static void no(Case c)throws Exception{boolean failed=false;try{c.run();}catch(Exception e){failed=true;}yes(failed);}
    static JSONObject state()throws Exception{JSONObject s=OffersAndPhotosTests.snapshot();PixelDriveState.prepare(s);return s;}
    static JSONObject photo(String id)throws Exception{return SyntheticBusinessData.object().put("id",id).put("customer_id","qa_c").put("order_id","").put("worklog_id","").put("role","documentation").put("caption","Synthetic photo").put("updated_at","t1").put("content_sha256","a".repeat(64)).put("storage_name","a".repeat(64)+".jpg").put("byte_size",10).put("mime_type","image/jpeg");}
    static JSONObject event(String id,String kind,JSONObject p)throws Exception{return OffersAndPhotosTests.event(id,kind,p);}
    static JSONObject trash(JSONObject s,String type,String id,String client)throws Exception{return event(client,"archive",DeletionRules.payload(s,type,id,OwnerJournalRules.version(OwnerJournalRules.findOrNull(s.getJSONArray(OwnerJournalRules.group(type)),id)),"t2"));}
    static void apply(JSONObject s,JSONObject a)throws Exception{JSONArray errors=DriveJournalRules.apply(s,new JSONArray().put(a));if(errors.length()>0)throw new AssertionError(errors.toString());yes(true);}
    static JSONObject purge(JSONObject s,String type,String id,String client)throws Exception{return event(client,"purge_v1",SyntheticBusinessData.object().put("entity",type).put("id",id).put("confirmed",true).put("expected_record_hash",OwnerJournalRules.version(OwnerJournalRules.archives(s).getJSONObject(type+":"+id))));}
    public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
        // A customer with photos goes straight to trash; an unrelated customer/photo stays byte-identical.
        JSONObject s=state();s.getJSONArray("customers").put(SyntheticBusinessData.object("{id:'qa_other',name:'Other',active:1}"));
        s.getJSONArray("photos").put(photo("qa_p").put("role","profile").put("session_id","session")).put(photo("other-photo").put("customer_id","qa_other"));
        String other=PixelMcpWriteTools.hash(s.getJSONArray("photos").getJSONObject(1));JSONObject remove=trash(s,"customer","qa_c","trash-c");apply(s,remove);
        JSONObject detached=OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"qa_p");yes(detached.optString("customer_id").isEmpty());yes(detached.getString("role").equals("documentation"));yes(detached.optString("session_id").isEmpty());yes(!detached.optBoolean("customer_visible"));yes(PixelMcpWriteTools.hash(s.getJSONArray("photos").getJSONObject(1)).equals(other));yes(detached.getString("content_sha256").equals("a".repeat(64)));
        JSONObject saved=OwnerJournalRules.archives(s).getJSONObject("customer:qa_c");apply(s,event("restore-c","restore",SyntheticBusinessData.object().put("entity","customer").put("id","qa_c").put("expected_record_hash",OwnerJournalRules.version(saved))));yes(OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"qa_p").getString("customer_id").equals("qa_c"));yes(OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"qa_p").getString("role").equals("profile"));
        // Restoring never overwrites a newer reassignment/caption of an independent photo.
        apply(s,trash(s,"customer","qa_c","trash-c-2"));OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"qa_p").put("caption","New decision").put("updated_at","t3");saved=OwnerJournalRules.archives(s).getJSONObject("customer:qa_c");apply(s,event("restore-c-2","restore",SyntheticBusinessData.object().put("entity","customer").put("id","qa_c").put("expected_record_hash",OwnerJournalRules.version(saved))));yes(OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"qa_p").optString("customer_id").isEmpty());
        // Parent removal is not blocked by photo references. Only the affected reference is detached.
        for(String type:new String[]{"order","worklog"}){
            JSONObject t=state();t.getJSONArray(OwnerJournalRules.group(type)).put(type.equals("order")?SyntheticBusinessData.object("{id:'qa_parent',customer_id:'qa_c',title:'Job'}"):SyntheticBusinessData.object("{id:'qa_parent',customer_id:'qa_c',work_date:'2026-10-18',activity:'Work',hours:1,billed:0}"));t.getJSONArray("photos").put(photo("linked").put(type+"_id","qa_parent"));
            JSONObject a=trash(t,type,"qa_parent","trash-"+type);apply(t,a);yes(t.getJSONArray(OwnerJournalRules.group(type)).length()==0);yes(t.getJSONArray("photos").length()==1);yes(t.getJSONArray("photos").getJSONObject(0).optString(type+"_id").isEmpty());yes(t.getJSONArray("photos").getJSONObject(0).getString("customer_id").equals("qa_c"));
            JSONObject entry=OwnerJournalRules.archives(t).getJSONObject(type+":qa_parent");apply(t,event("undo-"+type,"restore",SyntheticBusinessData.object().put("entity",type).put("id","qa_parent").put("expected_record_hash",OwnerJournalRules.version(entry))));yes(t.getJSONArray("photos").getJSONObject(0).optString(type+"_id").equals("qa_parent"));
        }
        // Already-trashed photos must not create a circular barrier to deleting their customer.
        JSONObject nested=stateWithPhoto();apply(nested,trash(nested,"photo","qa_photo","nested-photo"));apply(nested,trash(nested,"customer","qa_c","nested-customer"));
        yes(OwnerJournalRules.archives(nested).getJSONObject("photo:qa_photo").getJSONObject("record").optString("customer_id").isEmpty());
        apply(nested,purge(nested,"customer","qa_c","nested-purge-customer"));JSONObject archivedPhoto=OwnerJournalRules.archives(nested).getJSONObject("photo:qa_photo");
        apply(nested,event("restore-independent-photo","restore",SyntheticBusinessData.object().put("entity","photo").put("id","qa_photo").put("expected_record_hash",OwnerJournalRules.version(archivedPhoto))));yes(nested.getJSONArray("customers").length()==0);yes(nested.getJSONArray("photos").getJSONObject(0).optString("customer_id").isEmpty());yes(nested.getJSONArray("photos").getJSONObject(0).getString("content_sha256").equals("a".repeat(64)));
        // A photo edited between delete preview and replay cannot silently lose its new links.
        JSONObject raced=state();raced.getJSONArray("photos").put(photo("race"));JSONObject stale=trash(raced,"customer","qa_c","stale");raced.getJSONArray("photos").getJSONObject(0).put("caption","Parallel");String before=PixelMcpWriteTools.hash(raced.getJSONArray("photos"));yes(DriveJournalRules.apply(raced,new JSONArray().put(stale)).length()==1);yes(raced.getJSONArray("customers").getJSONObject(0).getInt("active")==1);yes(PixelMcpWriteTools.hash(raced.getJSONArray("photos")).equals(before));
        // Purge confirmation, stale archive guards and financial references fail closed.
        JSONObject t=state();t.getJSONArray("photos").put(photo("qa_photo"));JSONObject archivePhoto=trash(t,"photo","qa_photo","archive-photo");apply(t,archivePhoto);JSONObject delete=purge(t,"photo","qa_photo","purge-photo");
        no(()->OwnerJournalRules.apply(SyntheticBusinessData.object(t.toString()),event("unconfirmed","purge_v1",SyntheticBusinessData.object(delete.getJSONObject("payload").toString()).put("confirmed",false)),new HashMap<>(),SyntheticBusinessData.object()));
        no(()->OwnerJournalRules.apply(SyntheticBusinessData.object(t.toString()),event("stale-purge","purge_v1",SyntheticBusinessData.object(delete.getJSONObject("payload").toString()).put("expected_record_hash","0".repeat(64))),new HashMap<>(),SyntheticBusinessData.object()));
        apply(t,delete);JSONObject tombstone=OwnerJournalRules.archives(t).getJSONObject("photo:qa_photo");yes(tombstone.getBoolean("purged"));yes(!tombstone.has("record"));yes(!tombstone.toString().contains("Synthetic photo"));yes(tombstone.getJSONObject("media").getString("content_sha256").equals("a".repeat(64)));
        String receipts=PixelMcpWriteTools.hash(t.getJSONObject("drive_action_receipts"));yes(DriveJournalRules.apply(t,new JSONArray().put(archivePhoto).put(delete)).length()==0);yes(PixelMcpWriteTools.hash(t.getJSONObject("drive_action_receipts")).equals(receipts));
        no(()->DriveJournalRules.validateChange("photo",photo("qa_photo"),null,t));no(()->OwnerJournalRules.apply(SyntheticBusinessData.object(t.toString()),event("restore-purged","restore",SyntheticBusinessData.object().put("entity","photo").put("id","qa_photo").put("expected_record_hash",OwnerJournalRules.version(tombstone))),new HashMap<>(),SyntheticBusinessData.object()));
        // Projection carries tombstones; a stale form cannot resurrect the same ID before online sync.
        JSONObject projected=PixelMcpReadTools.effective(SyntheticBusinessData.object().put("master",stateWithPhoto()).put("pending",new JSONArray().put(archivePhoto).put(delete)));yes(TrashRules.purged(projected,"photo","qa_photo"));no(()->DriveJournalRules.validateChange("photo",photo("qa_photo"),null,projected));
        yes(!TrashRules.referenced(t,new JSONArray(),"a".repeat(64)+".jpg"));t.getJSONArray("photos").put(photo("shared"));yes(TrashRules.referenced(t,new JSONArray(),"a".repeat(64)+".jpg"));t.put("photos",new JSONArray());yes(TrashRules.referenced(t,new JSONArray().put(event("pending-photo","photo",photo("pending"))),"a".repeat(64)+".jpg"));
        JSONObject protectedState=state();protectedState.getJSONArray("invoices").put(SyntheticBusinessData.object("{id:'qa_issued',customer_id:'qa_c',status:'Gestellt',invoice_number:'R-1',issued_at:'2026-10-18',positions:[]}"));no(()->DeletionRules.payload(protectedState,"invoice","qa_issued",OwnerJournalRules.version(protectedState.getJSONArray("invoices").getJSONObject(0)),"t2"));apply(protectedState,trash(protectedState,"customer","qa_c","customer-with-invoice"));no(()->TrashRules.purge(protectedState,purge(protectedState,"customer","qa_c","purge-invoiced-customer")));
        // New destructive journal kind is unavailable to older processors; omitted confirmation rejected.
        yes(!DriveJournalRules.componentReady(SyntheticBusinessData.object(),"purge_v1"));yes(DriveJournalRules.componentReady(state(),"purge_v1"));no(()->PixelMcpOwnerTools.validate("purge_entity",SyntheticBusinessData.object().put("request_id","synthetic-request").put("entity","photo").put("entity_id","qa_photo").put("expected_record_hash","a".repeat(64))));
        // A stale permanent-delete request has a hash-bound keep-current exit, without erasing its history.
        JSONObject canceled=stateWithPhoto();apply(canceled,trash(canceled,"photo","qa_photo","cancel-archive"));JSONObject failedPurge=purge(canceled,"photo","qa_photo","failed-purge");OwnerJournalRules.archives(canceled).getJSONObject("photo:qa_photo").getJSONObject("record").put("caption","New archive decision");
        yes(DriveJournalRules.apply(canceled,new JSONArray().put(failedPurge)).length()==1);
        JSONObject conflict=PixelMcpOwnerTools.conflicts(SyntheticBusinessData.object().put("master",canceled).put("pending",new JSONArray().put(failedPurge)),SyntheticBusinessData.object()).getJSONArray("conflicts").getJSONObject(0);
        yes(conflict.getBoolean("resolution_available"));yes(conflict.getJSONArray("allowed_resolutions").getString(0).equals("keep_current"));yes(conflict.getString("current_hash").equals(OwnerJournalRules.version(OwnerJournalRules.archives(canceled).getJSONObject("photo:qa_photo"))));
        JSONObject decision=SyntheticBusinessData.object().put("client_id","failed-purge").put("resolution","keep_current").put("expected_action_hash",OrderReviewRules.actionHash(failedPurge)).put("expected_current_hash",conflict.getString("current_hash")).put("_original_action",failedPurge);String archiveBefore=PixelMcpWriteTools.hash(OwnerJournalRules.archives(canceled));
        JSONObject unsafe=event("unsafe-purge-control","conflict_resolution",SyntheticBusinessData.object(decision.toString()).put("resolution","apply_proposed"));Map<String,JSONObject> original=new HashMap<>();original.put("failed-purge",failedPurge);no(()->OwnerJournalRules.apply(SyntheticBusinessData.object(canceled.toString()),unsafe,original,SyntheticBusinessData.object()));
        apply(canceled,event("withdraw-purge","conflict_resolution",decision));yes(archiveBefore.equals(PixelMcpWriteTools.hash(OwnerJournalRules.archives(canceled))));yes(canceled.getJSONObject("drive_resolved_actions").getString("failed-purge").equals(OrderReviewRules.actionHash(failedPurge)));yes(canceled.getJSONObject("owner_conflict_history").has("failed-purge"));
        JSONObject parallel=SyntheticBusinessData.object().put("configured",true).put("drive_sync_failed",true).put("last_error_code","LEGACY_SOURCE_CHANGED").put("user_action_required",true);yes(SyncPolicy.describe(parallel,SyntheticBusinessData.object(),false,0).getString("next_action").equals("compare_versions"));
        // Direct Drive cleanup uses the observed version; permission/CAS failures retain bytes.
        for(int mode=0;mode<4;mode++){
            final int scenario=mode;final int[] deletes={0};final boolean[] exists={true};byte[] data="synthetic media".getBytes(java.nio.charset.StandardCharsets.UTF_8);String sha=LegacyDriveRestFixture.sha256(data);
            LegacyDriveRestFixture.Wire wire=(method,url,headers,body,max)->{
                if(url.contains("files?q="))return DriveTransportTests.reply(200,SyntheticBusinessData.object().put("files",exists[0]?new JSONArray().put(SyntheticBusinessData.object().put("id","qa_synthetic_file").put("name",sha+".jpg").put("version","1")):new JSONArray()).toString());
                if(method.equals("GET"))return new LegacyDriveRestFixture.Reply(200,data,scenario==1?Collections.emptyMap():Collections.singletonMap("ETag","synthetic-v1"));
                if(method.equals("DELETE")){yes(headers.get("If-Match").equals("synthetic-v1"));deletes[0]++;if(scenario==2)return DriveTransportTests.reply(412,"");if(scenario==3)return DriveTransportTests.reply(403,"");exists[0]=false;return DriveTransportTests.reply(204,"");}
                throw new AssertionError("Unexpected synthetic request");
            };
            LegacyDriveRestFixture client=new LegacyDriveRestFixture("synthetic_folder",()->"synthetic-token",wire,new DriveTransportTests.Memory());
            if(mode==0){client.deleteVerified("synthetic_folder",sha+".jpg",sha);yes(!exists[0]);client.deleteVerified("synthetic_folder",sha+".jpg",sha);yes(deletes[0]==1);}else{no(()->client.deleteVerified("synthetic_folder",sha+".jpg",sha));yes(exists[0]);yes(deletes[0]==(mode==1?0:1));}
        }
        System.out.println("PASS "+count+" trash, relationship, purge, replay, stale-form and shared-media checks (synthetic JVM)");
    }
    static JSONObject stateWithPhoto()throws Exception{JSONObject s=state();s.getJSONArray("photos").put(photo("qa_photo"));return s;}
}
