package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Synthetic active/trash graphs, immutable requests and provider-backup policy. */
public final class TrashPurgeBatchTests {
    static int checks;
    interface Case {void run()throws Exception;}
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("empty trash "+checks);}
    static void no(Case operation)throws Exception{boolean rejected=false;try{operation.run();}catch(Exception e){rejected=true;}yes(rejected);}
    static JSONObject state()throws Exception{JSONObject s=OffersAndPhotosTests.snapshot();PixelDriveState.prepare(s);s.put("owner_archive",new JSONObject());return s;}
    static JSONObject record(String id,String type)throws Exception {
        JSONObject row=new JSONObject().put("id",id).put("customer_id","c").put("updated_at","t1");
        if(type.equals("worklog"))row.put("activity","Synthetic work").put("work_date","2026-07-01").put("hours",1).put("billed",0);
        if(type.equals("order"))row.put("title","Synthetic order");
        if(type.equals("photo"))row.put("caption","Synthetic photo").put("content_sha256","a".repeat(64)).put("storage_name","a".repeat(64)+".jpg").put("byte_size",12).put("mime_type","image/jpeg");
        return row;
    }
    static void archive(JSONObject s,String type,JSONObject row)throws Exception {OwnerJournalRules.archives(s).put(type+":"+row.getString("id"),new JSONObject().put("record",row).put("archive_action","synthetic-archive").put("archived_at","t2"));}
    static JSONObject event(JSONObject s,String id)throws Exception {return OffersAndPhotosTests.event(id,TrashPurgeBatch.KIND,TrashPurgeBatch.payload(s,TrashPurgeBatch.preview(s).getString("preview_hash"),true));}
    public static void main(String[] args)throws Exception {
        JSONObject s=state();for(int i=0;i<128;i++)archive(s,"worklog",record("w"+i,"worklog"));
        archive(s,"order",record("o","order"));archive(s,"photo",record("p1","photo").put("order_id","o").put("worklog_id","w0"));archive(s,"photo",record("p2","photo").put("order_id","o"));
        JSONObject p=TrashPurgeBatch.preview(s);yes(p.getInt("count")==131&&p.getJSONArray("blocked").length()==0);
        String customer=PixelMcpWriteTools.hash(s.getJSONArray("customers"));JSONObject action=event(s,"bulk-empty");
        no(()->TrashPurgeBatch.payload(s,p.getString("preview_hash"),false));
        yes(DriveJournalRules.apply(s,new JSONArray().put(action)).length()==0);yes(TrashPurgeBatch.preview(s).getInt("total")==0);yes(PixelMcpWriteTools.hash(s.getJSONArray("customers")).equals(customer));
        Set<String> receipts=new HashSet<>();for(Iterator<String> it=OwnerJournalRules.archives(s).keys();it.hasNext();){JSONObject t=OwnerJournalRules.archives(s).getJSONObject(it.next());yes(t.getBoolean("purged")&&!t.has("record"));receipts.add(t.getString("purge_action"));}yes(receipts.size()==131);
        String applied=PixelMcpWriteTools.hash(s);yes(DriveJournalRules.apply(s,new JSONArray().put(action)).length()==0);yes(PixelMcpWriteTools.hash(s).equals(applied));
        // Cycles wholly inside trash are safe as one atomic set.
        JSONObject cycle=state();cycle.put("customers",new JSONArray());for(String id:new String[]{"a","b"}){JSONObject inactive=new JSONObject().put("id",id).put("name","Synthetic "+id).put("active",0).put("default_billing_customer_id",id.equals("a")?"b":"a");cycle.getJSONArray("customers").put(inactive);archive(cycle,"customer",new JSONObject(inactive.toString()).put("active",1));OwnerJournalRules.archives(cycle).getJSONObject("customer:"+id).put("archived_record_hash",OwnerJournalRules.version(inactive));}
        yes(TrashPurgeBatch.preview(cycle).getInt("count")==2);yes(DriveJournalRules.apply(cycle,new JSONArray().put(event(cycle,"empty-cycle"))).length()==0);yes(cycle.getJSONArray("customers").length()==0);
        // Active invoices protect linked trash and its parents; unrelated trash still empties.
        JSONObject protectedState=state();archive(protectedState,"worklog",record("kept","worklog"));archive(protectedState,"worklog",record("free","worklog"));
        protectedState.getJSONArray("invoices").put(new JSONObject().put("id","issued").put("customer_id","c").put("invoice_number","R-1").put("issued_at","2026-10-18").put("status","Gestellt").put("positions",new JSONArray().put(new JSONObject().put("worklog_id","kept"))));
        JSONObject protectedPreview=TrashPurgeBatch.preview(protectedState);yes(protectedPreview.getInt("count")==1&&protectedPreview.getJSONArray("blocked").length()==1);String invoice=PixelMcpWriteTools.hash(protectedState.getJSONArray("invoices"));
        yes(DriveJournalRules.apply(protectedState,new JSONArray().put(event(protectedState,"empty-free"))).length()==0);yes(!OwnerJournalRules.archives(protectedState).getJSONObject("worklog:kept").optBoolean("purged"));yes(PixelMcpWriteTools.hash(protectedState.getJSONArray("invoices")).equals(invoice));
        JSONObject stale=state();archive(stale,"photo",record("p","photo"));JSONObject staleAction=event(stale,"stale-empty");archive(stale,"worklog",record("new","worklog"));String before=PixelMcpWriteTools.hash(stale);no(()->TrashPurgeBatch.apply(stale,staleAction));yes(before.equals(PixelMcpWriteTools.hash(stale)));
        JSONObject tampered=event(stale,"tamper-empty");tampered.getJSONObject("payload").getJSONArray("entries").remove(0);no(()->TrashPurgeBatch.apply(stale,tampered));yes(before.equals(PixelMcpWriteTools.hash(stale)));
        yes(!DriveJournalRules.componentReady(new JSONObject(),TrashPurgeBatch.KIND));yes(DriveJournalRules.componentReady(state(),TrashPurgeBatch.KIND));
        no(()->PixelMcpOwnerTools.validate("empty_trash",new JSONObject().put("request_id","synthetic-request").put("expected_preview_hash","a".repeat(64))));
        // A failed bulk action has a keep-current exit and retains its immutable action hash.
        JSONObject failed=state();archive(failed,"photo",record("p","photo"));JSONObject original=event(failed,"failed-bulk");archive(failed,"worklog",record("later","worklog"));JSONArray errors=DriveJournalRules.apply(failed,new JSONArray().put(original));failed.put("drive_journal_errors",errors);
        JSONObject conflicted=PixelMcpOwnerTools.conflicts(new JSONObject().put("master",failed).put("pending",new JSONArray().put(original)),new JSONObject());JSONObject conflict=conflicted.getJSONArray("conflicts").getJSONObject(0);yes(conflict.getBoolean("resolution_available"));yes(conflict.getJSONArray("allowed_resolutions").getString(0).equals("keep_current"));
        JSONObject decision=OffersAndPhotosTests.event("cancel-bulk","conflict_resolution",new JSONObject().put("client_id",original.getString("client_id")).put("expected_action_hash",conflict.getString("action_hash")).put("expected_current_hash",conflict.getString("current_hash")).put("resolution","keep_current").put("_original_action",original));
        yes(DriveJournalRules.apply(failed,new JSONArray().put(original).put(decision)).length()==0);yes(!OwnerJournalRules.archives(failed).getJSONObject("photo:p").optBoolean("purged"));
        // Transport choice does not waive freshness, rights, scope or exact current selection.
        JSONObject legacy=state();JSONArray pending=new JSONArray(),legacyErrors=new JSONArray();for(int i=0;i<7;i++){JSONObject a=OffersAndPhotosTests.event("legacy-"+i,i<6?"delete":"customer",i<6?new JSONObject().put("entity","worklog").put("id","absent-"+i):new JSONObject().put("id","c").put("name","Proposed").put("_mcp_create",true));a.put("created_at","2026-10-13T00:00:00Z");pending.put(a);legacyErrors.put(new JSONObject().put("client_id",a.getString("client_id")).put("code",i<6?"DELETE_REVIEW_REQUIRED":"WRITE_CONFLICT"));}
        legacy.put("drive_journal_errors",legacyErrors);JSONObject local=new JSONObject().put("master",legacy).put("pending",pending).put("sync_queue",new JSONObject().put("total",7));
        String preview=LegacyConflictRules.preview(local).getString("preview_hash");
        LegacyBackupPolicy.require(local,true,true,true,false,"provider_readback","LEGACY_SOURCE_CHANGED",preview);yes(true);
        no(()->LegacyBackupPolicy.require(local,true,true,true,true,"provider_readback","LEGACY_SOURCE_CHANGED",preview));
        no(()->LegacyBackupPolicy.require(local,true,true,true,false,"provider_readback","LEGACY_SOURCE_CHANGED","0".repeat(64)));
        no(()->LegacyBackupPolicy.require(local,true,true,true,false,"provider_readback","PERMISSION_REQUIRED",preview));
        no(()->LegacyBackupPolicy.require(local,true,false,true,false,"provider_readback","LEGACY_SOURCE_CHANGED",preview));
        LegacyBackupPolicy.require(local,true,true,false,false,"provider_readback");yes(true);LegacyBackupPolicy.require(local,true,true,false,false,"drive_server_manifest");yes(true);
        no(()->LegacyBackupPolicy.require(local,true,false,false,false,"provider_readback"));no(()->LegacyBackupPolicy.require(local,true,true,false,true,"provider_readback"));no(()->LegacyBackupPolicy.require(local,true,true,true,false,"provider_readback"));no(()->LegacyBackupPolicy.require(local,true,true,false,false,"local_only"));
        JSONObject six=new JSONObject(local.toString());six.getJSONArray("pending").remove(0);six.getJSONObject("master").getJSONArray("drive_journal_errors").remove(0);six.getJSONObject("sync_queue").put("total",6);String sixHash=LegacyConflictRules.preview(six).getString("preview_hash");yes(!sixHash.equals(preview));
        no(()->LegacyBackupPolicy.require(six,true,true,false,false,"provider_readback"));no(()->LegacyBackupPolicy.require(six,true,true,false,false,"provider_readback","",preview));LegacyBackupPolicy.require(six,true,true,false,false,"provider_readback","",sixHash);yes(true);no(()->LegacyBackupPolicy.require(new JSONObject(six.toString()).put("sync_queue",new JSONObject().put("total",7)),true,true,false,false,"provider_readback","",sixHash));
        System.out.println("PASS empty-trash/provider-backup checks="+checks);
    }
}
