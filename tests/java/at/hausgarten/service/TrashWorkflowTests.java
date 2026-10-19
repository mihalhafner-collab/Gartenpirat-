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
    static JSONObject photo(String id)throws Exception{return new JSONObject().put("id",id).put("customer_id","c").put("order_id","").put("worklog_id","").put("role","documentation").put("caption","Synthetic photo").put("updated_at","t1").put("content_sha256","a".repeat(64)).put("storage_name","a".repeat(64)+".jpg").put("byte_size",10).put("mime_type","image/jpeg");}
    static JSONObject event(String id,String kind,JSONObject p)throws Exception{return OffersAndPhotosTests.event(id,kind,p);}
    static JSONObject trash(JSONObject s,String type,String id,String client)throws Exception{return event(client,"archive",DeletionRules.payload(s,type,id,OwnerJournalRules.version(OwnerJournalRules.findOrNull(s.getJSONArray(OwnerJournalRules.group(type)),id)),"t2"));}
    static void apply(JSONObject s,JSONObject a)throws Exception{JSONArray errors=DriveJournalRules.apply(s,new JSONArray().put(a));if(errors.length()>0)throw new AssertionError(errors.toString());yes(true);}
    static JSONObject purge(JSONObject s,String type,String id,String client)throws Exception{return event(client,"purge_v1",new JSONObject().put("entity",type).put("id",id).put("confirmed",true).put("expected_record_hash",OwnerJournalRules.version(OwnerJournalRules.archives(s).getJSONObject(type+":"+id))));}
    public static void main(String[] args)throws Exception{
        // A customer with photos goes straight to trash; an unrelated customer/photo stays byte-identical.
        JSONObject s=state();s.getJSONArray("customers").put(new JSONObject("{id:'other',name:'Other',active:1}"));
        s.getJSONArray("photos").put(photo("p").put("role","profile").put("session_id","session")).put(photo("other-photo").put("customer_id","other"));
        String other=PixelMcpWriteTools.hash(s.getJSONArray("photos").getJSONObject(1));JSONObject remove=trash(s,"customer","c","trash-c");apply(s,remove);
        JSONObject detached=OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"p");yes(detached.optString("customer_id").isEmpty());yes(detached.getString("role").equals("documentation"));yes(detached.optString("session_id").isEmpty());yes(!detached.optBoolean("customer_visible"));yes(PixelMcpWriteTools.hash(s.getJSONArray("photos").getJSONObject(1)).equals(other));yes(detached.getString("content_sha256").equals("a".repeat(64)));
        JSONObject saved=OwnerJournalRules.archives(s).getJSONObject("customer:c");apply(s,event("restore-c","restore",new JSONObject().put("entity","customer").put("id","c").put("expected_record_hash",OwnerJournalRules.version(saved))));yes(OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"p").getString("customer_id").equals("c"));yes(OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"p").getString("role").equals("profile"));
        // Restoring never overwrites a newer reassignment/caption of an independent photo.
        apply(s,trash(s,"customer","c","trash-c-2"));OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"p").put("caption","New decision").put("updated_at","t3");saved=OwnerJournalRules.archives(s).getJSONObject("customer:c");apply(s,event("restore-c-2","restore",new JSONObject().put("entity","customer").put("id","c").put("expected_record_hash",OwnerJournalRules.version(saved))));yes(OwnerJournalRules.findOrNull(s.getJSONArray("photos"),"p").optString("customer_id").isEmpty());
        // Parent removal is not blocked by photo references. Only the affected reference is detached.
        for(String type:new String[]{"order","worklog"}){
            JSONObject t=state();t.getJSONArray(OwnerJournalRules.group(type)).put(type.equals("order")?new JSONObject("{id:'parent',customer_id:'c',title:'Job'}"):new JSONObject("{id:'parent',customer_id:'c',work_date:'2026-10-18',activity:'Work',hours:1,billed:0}"));t.getJSONArray("photos").put(photo("linked").put(type+"_id","parent"));
            JSONObject a=trash(t,type,"parent","trash-"+type);apply(t,a);yes(t.getJSONArray(OwnerJournalRules.group(type)).length()==0);yes(t.getJSONArray("photos").length()==1);yes(t.getJSONArray("photos").getJSONObject(0).optString(type+"_id").isEmpty());yes(t.getJSONArray("photos").getJSONObject(0).getString("customer_id").equals("c"));
            JSONObject entry=OwnerJournalRules.archives(t).getJSONObject(type+":parent");apply(t,event("undo-"+type,"restore",new JSONObject().put("entity",type).put("id","parent").put("expected_record_hash",OwnerJournalRules.version(entry))));yes(t.getJSONArray("photos").getJSONObject(0).optString(type+"_id").equals("parent"));
        }
        // Already-trashed photos must not create a circular barrier to deleting their customer.
        JSONObject nested=stateWithPhoto();apply(nested,trash(nested,"photo","photo","nested-photo"));apply(nested,trash(nested,"customer","c","nested-customer"));
        yes(OwnerJournalRules.archives(nested).getJSONObject("photo:photo").getJSONObject("record").optString("customer_id").isEmpty());
        apply(nested,purge(nested,"customer","c","nested-purge-customer"));JSONObject archivedPhoto=OwnerJournalRules.archives(nested).getJSONObject("photo:photo");
        apply(nested,event("restore-independent-photo","restore",new JSONObject().put("entity","photo").put("id","photo").put("expected_record_hash",OwnerJournalRules.version(archivedPhoto))));yes(nested.getJSONArray("customers").length()==0);yes(nested.getJSONArray("photos").getJSONObject(0).optString("customer_id").isEmpty());yes(nested.getJSONArray("photos").getJSONObject(0).getString("content_sha256").equals("a".repeat(64)));
        // A photo edited between delete preview and replay cannot silently lose its new links.
        JSONObject raced=state();raced.getJSONArray("photos").put(photo("race"));JSONObject stale=trash(raced,"customer","c","stale");raced.getJSONArray("photos").getJSONObject(0).put("caption","Parallel");String before=PixelMcpWriteTools.hash(raced.getJSONArray("photos"));yes(DriveJournalRules.apply(raced,new JSONArray().put(stale)).length()==1);yes(raced.getJSONArray("customers").getJSONObject(0).getInt("active")==1);yes(PixelMcpWriteTools.hash(raced.getJSONArray("photos")).equals(before));
        // Purge confirmation, stale archive guards and financial references fail closed.
        JSONObject t=state();t.getJSONArray("photos").put(photo("photo"));JSONObject archivePhoto=trash(t,"photo","photo","archive-photo");apply(t,archivePhoto);JSONObject delete=purge(t,"photo","photo","purge-photo");
        no(()->OwnerJournalRules.apply(new JSONObject(t.toString()),event("unconfirmed","purge_v1",new JSONObject(delete.getJSONObject("payload").toString()).put("confirmed",false)),new HashMap<>(),new JSONObject()));
        no(()->OwnerJournalRules.apply(new JSONObject(t.toString()),event("stale-purge","purge_v1",new JSONObject(delete.getJSONObject("payload").toString()).put("expected_record_hash","0".repeat(64))),new HashMap<>(),new JSONObject()));
        apply(t,delete);JSONObject tombstone=OwnerJournalRules.archives(t).getJSONObject("photo:photo");yes(tombstone.getBoolean("purged"));yes(!tombstone.has("record"));yes(!tombstone.toString().contains("Synthetic photo"));yes(tombstone.getJSONObject("media").getString("content_sha256").equals("a".repeat(64)));
        String receipts=PixelMcpWriteTools.hash(t.getJSONObject("drive_action_receipts"));yes(DriveJournalRules.apply(t,new JSONArray().put(archivePhoto).put(delete)).length()==0);yes(PixelMcpWriteTools.hash(t.getJSONObject("drive_action_receipts")).equals(receipts));
        no(()->DriveJournalRules.validateChange("photo",photo("photo"),null,t));no(()->OwnerJournalRules.apply(new JSONObject(t.toString()),event("restore-purged","restore",new JSONObject().put("entity","photo").put("id","photo").put("expected_record_hash",OwnerJournalRules.version(tombstone))),new HashMap<>(),new JSONObject()));
        // Projection carries tombstones; a stale form cannot resurrect the same ID before online sync.
        JSONObject projected=PixelMcpReadTools.effective(new JSONObject().put("master",stateWithPhoto()).put("pending",new JSONArray().put(archivePhoto).put(delete)));yes(TrashRules.purged(projected,"photo","photo"));no(()->DriveJournalRules.validateChange("photo",photo("photo"),null,projected));
        yes(!TrashRules.referenced(t,new JSONArray(),"a".repeat(64)+".jpg"));t.getJSONArray("photos").put(photo("shared"));yes(TrashRules.referenced(t,new JSONArray(),"a".repeat(64)+".jpg"));t.put("photos",new JSONArray());yes(TrashRules.referenced(t,new JSONArray().put(event("pending-photo","photo",photo("pending"))),"a".repeat(64)+".jpg"));
        JSONObject protectedState=state();protectedState.getJSONArray("invoices").put(new JSONObject("{id:'issued',customer_id:'c',status:'Gestellt',invoice_number:'R-1',issued_at:'2026-10-18',positions:[]}"));no(()->DeletionRules.payload(protectedState,"invoice","issued",OwnerJournalRules.version(protectedState.getJSONArray("invoices").getJSONObject(0)),"t2"));apply(protectedState,trash(protectedState,"customer","c","customer-with-invoice"));no(()->TrashRules.purge(protectedState,purge(protectedState,"customer","c","purge-invoiced-customer")));
        // New destructive journal kind is unavailable to older processors; omitted confirmation rejected.
        yes(!DriveJournalRules.componentReady(new JSONObject(),"purge_v1"));yes(DriveJournalRules.componentReady(state(),"purge_v1"));no(()->PixelMcpOwnerTools.validate("purge_entity",new JSONObject().put("request_id","synthetic-request").put("entity","photo").put("entity_id","photo").put("expected_record_hash","a".repeat(64))));
        // A stale permanent-delete request has a hash-bound keep-current exit, without erasing its history.
        JSONObject canceled=stateWithPhoto();apply(canceled,trash(canceled,"photo","photo","cancel-archive"));JSONObject failedPurge=purge(canceled,"photo","photo","failed-purge");OwnerJournalRules.archives(canceled).getJSONObject("photo:photo").getJSONObject("record").put("caption","New archive decision");
        yes(DriveJournalRules.apply(canceled,new JSONArray().put(failedPurge)).length()==1);
        JSONObject conflict=PixelMcpOwnerTools.conflicts(new JSONObject().put("master",canceled).put("pending",new JSONArray().put(failedPurge)),new JSONObject()).getJSONArray("conflicts").getJSONObject(0);
        yes(conflict.getBoolean("resolution_available"));yes(conflict.getJSONArray("allowed_resolutions").getString(0).equals("keep_current"));yes(conflict.getString("current_hash").equals(OwnerJournalRules.version(OwnerJournalRules.archives(canceled).getJSONObject("photo:photo"))));
        JSONObject decision=new JSONObject().put("client_id","failed-purge").put("resolution","keep_current").put("expected_action_hash",OrderReviewRules.actionHash(failedPurge)).put("expected_current_hash",conflict.getString("current_hash")).put("_original_action",failedPurge);String archiveBefore=PixelMcpWriteTools.hash(OwnerJournalRules.archives(canceled));
        JSONObject unsafe=event("unsafe-purge-control","conflict_resolution",new JSONObject(decision.toString()).put("resolution","apply_proposed"));Map<String,JSONObject> original=new HashMap<>();original.put("failed-purge",failedPurge);no(()->OwnerJournalRules.apply(new JSONObject(canceled.toString()),unsafe,original,new JSONObject()));
        apply(canceled,event("withdraw-purge","conflict_resolution",decision));yes(archiveBefore.equals(PixelMcpWriteTools.hash(OwnerJournalRules.archives(canceled))));yes(canceled.getJSONObject("drive_resolved_actions").getString("failed-purge").equals(OrderReviewRules.actionHash(failedPurge)));yes(canceled.getJSONObject("owner_conflict_history").has("failed-purge"));
        JSONObject parallel=new JSONObject().put("configured",true).put("drive_sync_failed",true).put("last_error_code","LEGACY_SOURCE_CHANGED").put("user_action_required",true);yes(SyncPolicy.describe(parallel,new JSONObject(),false,0).getString("next_action").equals("compare_versions"));
        // Direct Drive cleanup uses the observed version; permission/CAS failures retain bytes.
        for(int mode=0;mode<4;mode++){
            final int scenario=mode;final int[] deletes={0};final boolean[] exists={true};byte[] data="synthetic media".getBytes(java.nio.charset.StandardCharsets.UTF_8);String sha=DriveRestClient.sha256(data);
            DriveRestClient.Wire wire=(method,url,headers,body,max)->{
                if(url.contains("files?q="))return DriveTransportTests.reply(200,new JSONObject().put("files",exists[0]?new JSONArray().put(new JSONObject().put("id","synthetic_file").put("name",sha+".jpg").put("version","1")):new JSONArray()).toString());
                if(method.equals("GET"))return new DriveRestClient.Reply(200,data,scenario==1?Collections.emptyMap():Collections.singletonMap("ETag","synthetic-v1"));
                if(method.equals("DELETE")){yes(headers.get("If-Match").equals("synthetic-v1"));deletes[0]++;if(scenario==2)return DriveTransportTests.reply(412,"");if(scenario==3)return DriveTransportTests.reply(403,"");exists[0]=false;return DriveTransportTests.reply(204,"");}
                throw new AssertionError("Unexpected synthetic request");
            };
            DriveRestClient client=new DriveRestClient("synthetic_folder",()->"synthetic-token",wire,new DriveTransportTests.Memory());
            if(mode==0){client.deleteVerified("synthetic_folder",sha+".jpg",sha);yes(!exists[0]);client.deleteVerified("synthetic_folder",sha+".jpg",sha);yes(deletes[0]==1);}else{no(()->client.deleteVerified("synthetic_folder",sha+".jpg",sha));yes(exists[0]);yes(deletes[0]==(mode==1?0:1));}
        }
        System.out.println("PASS "+count+" trash, relationship, purge, replay, stale-form and shared-media checks (synthetic JVM)");
    }
    static JSONObject stateWithPhoto()throws Exception{JSONObject s=state();s.getJSONArray("photos").put(photo("photo"));return s;}
}
