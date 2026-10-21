package at.hausgarten.service;

import org.json.*;

/** Real trash use case and journal replay; all rows are synthetic and memory-only. */
public final class TrashBoundaryTests {
    static int checks;
    static void yes(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    interface Task {void run()throws Exception;}
    static void rejected(Task task,String code)throws Exception {
        try{task.run();}catch(PixelMcpWriteTools.Rejected failure){yes(failure.code.equals(code),"expected rejection "+code);return;}
        throw new AssertionError("Expected "+code);
    }
    static OwnerApplicationTests.MemoryRepository fixture()throws Exception {
        OwnerApplicationTests.MemoryRepository repository=new OwnerApplicationTests.MemoryRepository();
        TrashPurgeBatchTests.archive(repository.state.getJSONObject("master"),"worklog",TrashPurgeBatchTests.record("qa_regular_trash","worklog"));
        return repository;
    }
    static void localCustomer(OwnerApplicationTests.MemoryRepository repository)throws Exception {
        repository.pendingActions().put(OffersAndPhotosTests.event("qa_local_customer_create","customer",
            SyntheticBusinessData.object().put("id","qa_local_customer").put("name","[TEST] QA local only")
                .put("active",1).put("_mcp_create",true).put("_local_only",true)));
    }
    static JSONObject empty(OwnerApplicationTests.MemoryRepository repository,String id)throws Exception {
        JSONObject preview=TrashPurgeBatch.preview(repository.effective());
        return new SyncArchiveApplicationService(repository).perform("empty_trash",SyntheticBusinessData.object()
            .put("expected_preview_hash",preview.getString("preview_hash")).put("confirmed",true),id);
    }
    static JSONArray driveActions(OwnerApplicationTests.MemoryRepository repository)throws Exception {
        JSONArray result=new JSONArray();
        for(int i=0;i<repository.pendingActions().length();i++){
            JSONObject action=repository.pendingActions().getJSONObject(i),p=action.getJSONObject("payload");
            if(!p.optBoolean("_local_only")&&!p.optBoolean("test_record"))result.put(action);
        }
        return result;
    }
    static void entityRoundTrips()throws Exception {
        for(String type:DeletionRules.ENTITIES){
            JSONObject state=TrashWorkflowTests.state(),row;
            switch(type){
                case "customer":row=new JSONObject(state.getJSONArray("customers").getJSONObject(0).toString());state.put("customers",new JSONArray());break;
                case "offer":row=OffersAndPhotosTests.draft();break;
                case "photo":row=TrashWorkflowTests.photo("qa_cycle_photo");break;
                case "invoice":row=SyntheticBusinessData.object().put("id","qa_cycle_invoice").put("customer_id","qa_c").put("status","Entwurf")
                    .put("invoice_number","").put("invoice_date","2026-10-21").put("positions",new JSONArray().put(SyntheticBusinessData.object().put("title","QA draft line").put("quantity",1).put("price",1)));break;
                case "service_template":row=SyntheticBusinessData.object().put("id","qa_cycle_service").put("title","QA service").put("category","QA").put("unit","h").put("default_price",1);break;
                case "appointment":row=PlanningTests.row().put("order_id","").put("status","cancelled");break;
                default:row=TrashPurgeBatchTests.record("qa_cycle_"+type,type);
            }
            DriveJournalRules.validateChange(type,row,null,state);
            String id=row.getString("id"),group=OwnerJournalRules.group(type);state.getJSONArray(group).put(row);
            String original=PixelMcpWriteTools.hash(row);
            JSONObject archive=TrashWorkflowTests.trash(state,type,id,"qa_cycle_archive_"+type);
            yes(DriveJournalRules.apply(state,new JSONArray().put(archive)).length()==0,type+" archive succeeds");
            JSONObject saved=OwnerJournalRules.archives(state).getJSONObject(type+":"+id);
            yes(PixelMcpWriteTools.hash(saved.getJSONObject("record")).equals(original),type+" original is retained in trash");
            JSONObject restore=OffersAndPhotosTests.event("qa_cycle_restore_"+type,"restore",SyntheticBusinessData.object().put("entity",type).put("id",id).put("expected_record_hash",OwnerJournalRules.version(saved)));
            yes(DriveJournalRules.apply(state,new JSONArray().put(restore)).length()==0,type+" restore succeeds");
            yes(PixelMcpWriteTools.hash(OwnerJournalRules.findOrNull(state.getJSONArray(group),id)).equals(original),type+" restore preserves original ID and content");
            yes(DriveJournalRules.apply(state,new JSONArray().put(TrashWorkflowTests.trash(state,type,id,"qa_cycle_rearchive_"+type))).length()==0,type+" can be archived again");
            yes(DriveJournalRules.apply(state,new JSONArray().put(TrashWorkflowTests.purge(state,type,id,"qa_cycle_purge_"+type))).length()==0,type+" explicit single purge succeeds");
            yes(OwnerJournalRules.findOrNull(state.getJSONArray(group),id)==null&&TrashRules.purged(state,type,id),type+" purge retains only its tombstone");
            JSONObject stale=new JSONObject(row.toString());rejected(()->DriveJournalRules.validateChange(type,stale,null,state),"RECORD_PURGED");
        }
    }
    public static void main(String[] ignored)throws Exception {
        SyntheticBusinessData.verify();
        entityRoundTrips();
        OwnerApplicationTests.MemoryRepository repository=fixture();localCustomer(repository);
        String active=PixelMcpWriteTools.hash(repository.state.getJSONObject("master").getJSONArray("customers"));
        JSONObject result=empty(repository,"qa_empty_regular_with_local_customer");
        yes(result.getInt("deleted")==1,"exactly the confirmed regular trash entry is queued");
        yes(TrashPurgeBatch.preview(repository.effective()).getInt("total")==0,"local projection removes the same entry");
        JSONObject remote=new JSONObject(repository.state.getJSONObject("master").toString());
        JSONArray errors=DriveJournalRules.apply(remote,driveActions(repository));
        yes(errors.length()==0,"local-only customer must not cause a false remote bulk-delete conflict: "+errors);
        yes(TrashPurgeBatch.preview(remote).getInt("total")==0,"remote replay removes confirmed trash");
        yes(active.equals(PixelMcpWriteTools.hash(remote.getJSONArray("customers"))),"unrelated active customers stay byte-identical");
        yes(OwnerJournalRules.findOrNull(remote.getJSONArray("customers"),"qa_local_customer")==null,"local-only customer is never uploaded");
        JSONObject action=driveActions(repository).getJSONObject(0),payload=action.getJSONObject("payload");
        yes(payload.has("_drive_preview_hash"),"different full views are bound before enqueue");
        yes(!payload.has("_sync_predecessors"),"private actions are not required remotely");
        String after=PixelMcpWriteTools.hash(remote);yes(DriveJournalRules.apply(remote,driveActions(repository)).length()==0,"retry succeeds");
        yes(after.equals(PixelMcpWriteTools.hash(remote)),"retry has no second effect");
        // Both guards reject actual concurrent changes and preserve the complete graph.
        for(boolean local:new boolean[]{false,true})for(boolean archive:new boolean[]{false,true}){
            JSONObject changed=local?SnapshotProjection.effective(SyntheticBusinessData.object().put("master",repository.state.getJSONObject("master")).put("pending",new JSONArray().put(repository.pendingActions().getJSONObject(0)))):new JSONObject(repository.state.getJSONObject("master").toString());
            if(archive)OwnerJournalRules.archives(changed).getJSONObject("worklog:qa_regular_trash").getJSONObject("record").put("notes","QA parallel archive change");
            else changed.getJSONArray("customers").getJSONObject(0).put("notes","QA parallel active change");
            String before=PixelMcpWriteTools.hash(changed);rejected(()->TrashPurgeBatch.apply(changed,action),"WRITE_CONFLICT");
            yes(before.equals(PixelMcpWriteTools.hash(changed)),"conflict leaves all data intact");
        }
        JSONObject missingConfirmation=new JSONObject(action.toString());missingConfirmation.getJSONObject("payload").put("confirmed",false);
        try{TrashPurgeBatch.apply(new JSONObject(repository.state.getJSONObject("master").toString()),missingConfirmation);throw new AssertionError("confirmation required");}catch(IllegalArgumentException expected){yes(true,"confirmation remains required");}
        JSONObject tampered=new JSONObject(action.toString());tampered.getJSONObject("payload").getJSONArray("entries").getJSONObject(0).put("entity_id","qa_unconfirmed");
        rejected(()->TrashPurgeBatch.apply(new JSONObject(repository.state.getJSONObject("master").toString()),tampered),"WRITE_CONFLICT");
        // Unchanged legacy requests retain their original payload/hash path.
        OwnerApplicationTests.MemoryRepository plain=fixture();empty(plain,"qa_plain_empty");
        yes(!driveActions(plain).getJSONObject(0).getJSONObject("payload").has("_drive_preview_hash"),"normal batches keep the legacy guard");
        yes(DriveJournalRules.apply(new JSONObject(plain.state.getJSONObject("master").toString()),driveActions(plain)).length()==0,"legacy replay still works");
        // Regular archive predecessors are shared; private customer creation is not.
        OwnerApplicationTests.MemoryRepository pending=fixture();localCustomer(pending);
        JSONObject live=TrashPurgeBatchTests.record("qa_pending_work","worklog");pending.state.getJSONObject("master").getJSONArray("worklogs").put(live);
        JSONObject effective=pending.effective();pending.pendingActions().put(OffersAndPhotosTests.event("qa_shared_archive","archive",DeletionRules.payload(effective,"worklog","qa_pending_work",OwnerJournalRules.version(live),"qa_t2")));
        yes(empty(pending,"qa_empty_pending_archive").getInt("deleted")==2,"both confirmed archives are selected");
        JSONObject last=pending.pendingActions().getJSONObject(pending.pendingActions().length()-1);JSONArray predecessors=last.getJSONObject("payload").getJSONArray("_sync_predecessors");
        yes(predecessors.length()==1&&predecessors.getString(0).equals("qa_shared_archive"),"only incorporated shared predecessor is bound");
        JSONObject pendingRemote=new JSONObject(pending.state.getJSONObject("master").toString());
        yes(DriveJournalRules.apply(pendingRemote,driveActions(pending)).length()==0,"archive then bulk purge replays remotely");
        yes(TrashPurgeBatch.preview(pendingRemote).getInt("total")==0,"all confirmed pending archives were purged");
        // Do not silently broaden a local selection when a local-only reference protects trash.
        JSONObject protectedLocal=fixture().effective(),unprotected=new JSONObject(protectedLocal.toString());
        TrashPurgeBatchTests.archive(protectedLocal,"worklog",TrashPurgeBatchTests.record("qa_free_trash","worklog"));
        TrashPurgeBatchTests.archive(unprotected,"worklog",TrashPurgeBatchTests.record("qa_free_trash","worklog"));
        protectedLocal.getJSONArray("invoices").put(SyntheticBusinessData.object().put("id","qa_private_invoice").put("customer_id","qa_c")
            .put("invoice_number","QA-1").put("status","Gestellt").put("issued_at","2026-10-21")
            .put("positions",new JSONArray().put(SyntheticBusinessData.object().put("worklog_id","qa_regular_trash"))));
        JSONObject protectedPreview=TrashPurgeBatch.preview(protectedLocal);
        yes(protectedPreview.getInt("count")==1,"invoice protects its referenced archive in the local view");
        JSONObject protectedPayload=TrashPurgeBatch.payload(protectedLocal,protectedPreview.getString("preview_hash"),true);
        rejected(()->TrashPurgeBatch.bindDrivePreview(protectedPayload,unprotected),"WRITE_CONFLICT");
        // A differing archived version/selection cannot acquire a transport guard.
        OwnerApplicationTests.MemoryRepository different=fixture();localCustomer(different);
        JSONObject changedView=different.effective();TrashPurgeBatchTests.archive(changedView,"worklog",TrashPurgeBatchTests.record("qa_only_local_trash","worklog"));
        JSONObject mismatched=TrashPurgeBatch.payload(changedView,TrashPurgeBatch.preview(changedView).getString("preview_hash"),true);
        rejected(()->TrashPurgeBatch.bindDrivePreview(mismatched,different.state.getJSONObject("master")),"WRITE_CONFLICT");
        // Explicit test/regular mixtures must still be refused before another queue write.
        OwnerApplicationTests.MemoryRepository mixed=fixture();
        TrashPurgeBatchTests.archive(mixed.state.getJSONObject("master"),"worklog",TrashPurgeBatchTests.record("qa_test_trash","worklog").put("test_record",true));
        rejected(()->empty(mixed,"qa_mixed_not_queued"),"TEST_DATA_ISOLATION");yes(mixed.pendingActions().length()==0,"mixed batch queues nothing");
        OwnerApplicationTests.MemoryRepository onlyLocal=fixture();
        OwnerJournalRules.archives(onlyLocal.state.getJSONObject("master")).getJSONObject("worklog:qa_regular_trash").getJSONObject("record").put("test_record",true);
        empty(onlyLocal,"qa_local_empty");yes(driveActions(onlyLocal).length()==0,"test-only batch cannot leave the device");
        yes(TrashPurgeBatch.preview(onlyLocal.effective()).getInt("total")==0,"test-only batch remains usable locally");
        System.out.println("PASS "+checks+" trash local/Drive boundary checks (synthetic JVM)");
    }
}
