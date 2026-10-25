package at.hausgarten.service;

import org.json.*;

public final class ManualChangeTests {
    static int tests;
    static void check(boolean ok) { tests++; if (!ok) throw new AssertionError("manual guard"); }
    static JSONObject row() throws Exception { return SyntheticBusinessData.object("{id:'qa_w',customer_id:'qa_c',billing_customer_id:'qa_c',break_minutes:0,kilometers:0,customer_visible:1,work_date:'2026-10-18',activity:'Pflege',hours:2,notes:'Alt',billed:0,updated_at:'t1'}"); }
    static JSONObject laterOrder(OwnerApplicationTests.MemoryRepository r)throws Exception {
        JSONObject current=PixelMcpWriteTools.find(r.effective().getJSONArray("orders"),"qa_o"),later=new JSONObject(current.toString()).put("description","QA after guarded decision");
        ManualChangeRules.prepare("order",later,current,"2026-10-19T13:00:00Z");
        later.put("_sync_predecessors",new JSONArray().put("qa_owner_context_conflict"));
        return OrderReviewTests.action("qa_after_guarded_decision","order",later).put("created_at","2026-10-19T13:00:00Z");
    }
    static boolean error(JSONArray errors,String id,String code)throws Exception {
        for(int i=0;i<errors.length();i++)if(id.equals(errors.getJSONObject(i).optString("client_id"))&&code.equals(errors.getJSONObject(i).optString("code")))return true;
        return false;
    }
    static void resolvedPredecessors()throws Exception {
        // Exercise the shared owner application, original journal and editor preparation.
        OwnerApplicationTests.MemoryRepository r=OwnerApplicationTests.conflictRepository();
        String originalState=PixelMcpWriteTools.hash(r.state.getJSONObject("master"));
        OwnerApplicationTests.invoke(new OwnerApplicationService(r),"resolve_sync_conflict",OwnerApplicationTests.conflictRequest(r,"qa_explicit_dependency_decision"));
        JSONObject later=laterOrder(r);r.pendingActions().put(later);
        JSONObject replay=new JSONObject(r.state.getJSONObject("master").toString());
        check(DriveJournalRules.apply(replay,r.pendingActions()).length()==0);
        check(replay.getJSONArray("orders").getJSONObject(0).getString("description").equals("QA after guarded decision"));
        JSONObject history=replay.getJSONObject("owner_conflict_history").getJSONObject("qa_owner_context_conflict");
        check(history.getString("action_hash").equals(OrderReviewRules.actionHash(r.pendingActions().getJSONObject(0)))&&history.getString("resolution").equals("keep_current"));
        check(originalState.equals(PixelMcpWriteTools.hash(r.state.getJSONObject("master"))));
        String businessHash=PixelMcpWriteTools.hash(replay.getJSONArray("orders")),historyHash=PixelMcpWriteTools.hash(history);
        check(DriveJournalRules.apply(replay,r.pendingActions()).length()==0&&businessHash.equals(PixelMcpWriteTools.hash(replay.getJSONArray("orders")))&&historyHash.equals(PixelMcpWriteTools.hash(replay.getJSONObject("owner_conflict_history").getJSONObject("qa_owner_context_conflict"))));
        // The same early dependency check also precedes owner journal actions.
        JSONObject settings=OrderReviewTests.action("qa_owner_after_decision","business_settings",new JSONObject().put("expected_record_hash",PixelMcpWriteTools.hash(replay.getJSONObject("business_settings"))).put("updates",new JSONObject().put("owner_name","QA guarded owner update")).put("_sync_predecessors",new JSONArray().put("qa_owner_context_conflict").put("qa_after_guarded_decision"))).put("created_at","2026-10-19T14:00:00Z");
        JSONArray ownerActions=new JSONArray(r.pendingActions().toString()).put(settings);JSONObject ownerReplay=new JSONObject(r.state.getJSONObject("master").toString());
        check(DriveJournalRules.apply(ownerReplay,ownerActions).length()==0&&ownerReplay.getJSONObject("business_settings").getString("owner_name").equals("QA guarded owner update"));
        // Neither a stale record decision nor a changed original hash resolves a dependency.
        for(String field:new String[]{"expected_current_hash","expected_action_hash"}){
            JSONArray invalid=new JSONArray(r.pendingActions().toString());invalid.getJSONObject(1).getJSONObject("payload").put(field,PixelMcpWriteTools.hash("qa_unproven_value"));
            JSONObject rejected=new JSONObject(r.state.getJSONObject("master").toString());JSONArray errors=DriveJournalRules.apply(rejected,invalid);
            check(error(errors,"qa_application_qa_explicit_dependency_decision",field.equals("expected_action_hash")?"ACTION_ID_CONFLICT":"WRITE_CONFLICT"));
            check(error(errors,"qa_after_guarded_decision","DEPENDENCY_BLOCKED")&&!rejected.getJSONArray("orders").getJSONObject(0).optString("description").equals("QA after guarded decision"));
            check(!rejected.optJSONObject("drive_resolved_actions").has("qa_owner_context_conflict")&&!rejected.has("owner_conflict_history"));
        }
        // A history-shaped object alone is not a resolution and cannot unblock a write.
        JSONObject forged=new JSONObject(r.state.getJSONObject("master").toString()).put("owner_conflict_history",new JSONObject().put("qa_owner_context_conflict",history));
        JSONArray originalAndLater=new JSONArray().put(r.pendingActions().getJSONObject(0)).put(later);
        check(error(DriveJournalRules.apply(forged,originalAndLater),"qa_after_guarded_decision","DEPENDENCY_BLOCKED"));
        // A stale resolved flag must still hit the exact original-action hash guard.
        JSONObject staleFlag=new JSONObject(r.state.getJSONObject("master").toString()).put("drive_resolved_actions",new JSONObject().put("qa_owner_context_conflict",PixelMcpWriteTools.hash("qa_changed_original")));
        try{DriveJournalRules.apply(staleFlag,originalAndLater);throw new AssertionError("stale resolved action hash accepted");}catch(PixelMcpWriteTools.Rejected expected){check(expected.code.equals("ACTION_ID_CONFLICT"));}
        // Missing originals cannot acquire a guarded owner decision or a migration licence.
        OwnerApplicationTests.MemoryRepository missing=OwnerApplicationTests.conflictRepository();JSONObject missingRequest=OwnerApplicationTests.conflictRequest(missing,"qa_missing_original_decision");missing.state.put("pending",new JSONArray());String missingBefore=PixelMcpWriteTools.hash(missing.state);
        try{OwnerApplicationTests.invoke(new OwnerApplicationService(missing),"resolve_sync_conflict",missingRequest);throw new AssertionError("missing original accepted");}catch(PixelMcpWriteTools.Rejected expected){check(expected.code.equals("CONFLICT_RESOLUTION_NOT_AVAILABLE")&&missingBefore.equals(PixelMcpWriteTools.hash(missing.state)));}
        OwnerApplicationTests.MemoryRepository bareFlag=OwnerApplicationTests.conflictRepository();JSONObject bareOriginal=bareFlag.pendingActions().getJSONObject(0);bareFlag.state.getJSONObject("master").getJSONObject("drive_resolved_actions").put(bareOriginal.getString("client_id"),OrderReviewRules.actionHash(bareOriginal));
        check(LocalMigrationInventory.inspect(bareFlag.state,(kind,row)->true).getInt("historical_reviews")>0&&!bareFlag.state.getJSONObject("master").has("owner_conflict_history"));
        // Unknown failed predecessors remain failed; the resolution exception is not generic.
        JSONObject unsupported=OrderReviewTests.action("qa_unsupported_predecessor","qa_unknown_action",new JSONObject().put("id","qa_unknown"));
        JSONObject dependent=new JSONObject(later.toString());dependent.getJSONObject("payload").put("_sync_predecessors",new JSONArray().put("qa_unsupported_predecessor"));
        JSONObject held=OrderReviewTests.snapshot();JSONArray errors=DriveJournalRules.apply(held,new JSONArray().put(unsupported).put(dependent));
        check(error(errors,"qa_unsupported_predecessor","UNSUPPORTED_ACTION")&&error(errors,"qa_after_guarded_decision","DEPENDENCY_BLOCKED"));
        check(!held.getJSONArray("orders").getJSONObject(0).optString("description").equals("QA after guarded decision"));
    }
    public static void main(String[] args) throws Exception { SyntheticBusinessData.verify();
        resolvedPredecessors();
        JSONObject original=row(), current=row().put("notes","Synchronisierter Nachtrag").put("updated_at","t2");
        JSONObject edit=SyntheticBusinessData.object().put("id","qa_w").put("_edit_base",original).put("_edit_changes",SyntheticBusinessData.object().put("activity","Hecke"));
        try { ManualChangeRules.editor(edit,current,"worklog"); throw new AssertionError("stale form accepted"); }
        catch (PixelMcpWriteTools.Rejected e) { check(e.code.equals("WRITE_CONFLICT")); }
        JSONObject fresh=ManualChangeRules.editor(edit,original,"worklog");
        check(fresh.getString("notes").equals("Alt")); check(fresh.getString("activity").equals("Hecke"));
        JSONObject inherited=row().put("_mcp_base",SyntheticBusinessData.object().put("id","qa_w").put("notes","Vorgänger"));
        ManualChangeRules.prepare("worklog",inherited,current,"t3");
        check(!inherited.has("_mcp_base"));
        DriveJournalRules.compare(inherited.getJSONObject("_sync_base"),current); check(true);
        JSONObject snapshot=SyntheticBusinessData.object("{customers:[{id:'qa_c',name:'Kunde',billing_mode:'direct'}],orders:[],worklogs:[],photos:[],invoices:[]}");
        JSONObject first=row(); ManualChangeRules.prepare("worklog",first,null,"t1");
        JSONObject second=SyntheticBusinessData.object(first.toString()).put("notes","Nachtrag");
        ManualChangeRules.prepare("worklog",second,first,"t2");
        JSONArray actions=new JSONArray().put(SyntheticBusinessData.object().put("client_id","first").put("kind","worklog").put("payload",first))
          .put(SyntheticBusinessData.object().put("client_id","second").put("kind","worklog").put("payload",second));
        check(DriveJournalRules.apply(snapshot,actions).length()==0);
        check(snapshot.getJSONArray("worklogs").getJSONObject(0).getString("notes").equals("Nachtrag"));
        check(DriveJournalRules.apply(snapshot,actions).length()==0);
        check(snapshot.getJSONArray("worklogs").length()==1);
        System.out.println("PASS "+tests+" manual baseline and chained replay checks");
    }
}
