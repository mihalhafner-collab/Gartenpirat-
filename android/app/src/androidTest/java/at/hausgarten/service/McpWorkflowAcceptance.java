package at.hausgarten.service;

import org.json.*;

/** Valid workflows through the real JSON-RPC boundary; every store is disposable. */
final class McpWorkflowAcceptance {
    static JSONObject rpc(McpAuditTools tools,String name,JSONObject args)throws Exception{
        JSONObject result=tools.rpc(new JSONObject().put("jsonrpc","2.0").put("id",1).put("method","tools/call")
            .put("params",new JSONObject().put("name",name).put("arguments",args))).getJSONObject("result");
        if(result.optBoolean("isError"))throw new AssertionError("Valid workflow rejected: "+name+" / "+result.getJSONObject("structuredContent").getJSONObject("error").optString("code"));
        return result.getJSONObject("structuredContent");
    }
    static JSONObject args(String request)throws Exception{return SyntheticBusinessData.object().put("request_id",request);}
    static JSONObject session(String request,String id)throws Exception{return args(request).put("session_id",id);}
    static void run(PixelMcpInstrumentation t)throws Exception{
        t.probe("MCP real RPC order-linked work create read guarded update and replay",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            String order=rpc(f.tools,"create_order",args("qa_rpc_order").put("customer_id","qa_c").put("title","QA RPC Auftrag")).getJSONObject("data").getString("entity_id");
            JSONObject read=rpc(f.tools,"get_order",SyntheticBusinessData.object().put("order_id",order));
            rpc(f.tools,"update_order",args("qa_rpc_order_update").put("order_id",order).put("expected_record_hash",read.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("title","QA RPC geändert")));
            JSONObject create=args("qa_rpc_order_work").put("order_id",order).put("work_date","2026-10-23").put("activity","QA RPC Pflege").put("hours",1);
            String work=rpc(f.tools,"create_work_record",create).getJSONObject("data").getString("entity_id");
            read=rpc(f.tools,"get_work_record",SyntheticBusinessData.object().put("work_record_id",work));
            rpc(f.tools,"update_work_record",args("qa_rpc_work_update").put("work_record_id",work).put("expected_record_hash",read.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("hours",1.5)));
            int pending=f.store.pendingActions().length();rpc(f.tools,"create_work_record",create);t.yes(f.store.pendingActions().length()==pending);
            rpc(f.tools,"get_customer_work_records",SyntheticBusinessData.object().put("customer_id","qa_c"));
            JSONObject saved=rpc(f.tools,"get_work_record",SyntheticBusinessData.object().put("work_record_id",work)).getJSONObject("data");
            t.yes(saved.getString("order_id").equals(order)&&saved.getDouble("hours")==1.5);
        }});
        t.probe("MCP real RPC full timer pause resume stop continue complete and replay",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            String id=rpc(f.tools,"start_work",args("qa_rpc_timer_start").put("customer_id","qa_c").put("work_date","2026-10-23")).getJSONObject("data").getJSONObject("session").getString("id");
            t.yes(rpc(f.tools,"start_break",session("qa_rpc_timer_pause",id)).getJSONObject("data").getJSONObject("session").getString("state").equals("PAUSED"));
            rpc(f.tools,"end_break",session("qa_rpc_timer_resume",id));rpc(f.tools,"stop_work",session("qa_rpc_timer_stop",id));
            rpc(f.tools,"continue_work",session("qa_rpc_timer_continue",id));rpc(f.tools,"stop_work",session("qa_rpc_timer_stop_again",id));
            // One synthetic measured hour in this disposable SQLite fixture only; never wait or alter the device clock.
            f.store.getWritableDatabase().execSQL("UPDATE work_sessions SET worked_ms=3600000 WHERE id=?",new Object[]{id});
            JSONObject complete=session("qa_rpc_timer_complete",id).put("activity","QA RPC Timerarbeit");rpc(f.tools,"complete_work_session",complete);
            int count=f.store.pendingActions().length();rpc(f.tools,"complete_work_session",complete);t.yes(f.store.pendingActions().length()==count&&count==1);
            JSONArray rows=PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs");t.yes(rows.length()==2&&rows.getJSONObject(1).getDouble("hours")==1);
        }});
        t.probe("MCP real RPC discarded timer restores same session and preserves no business writes",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            String id=rpc(f.tools,"start_work",args("qa_rpc_restore_start").put("customer_id","qa_c")).getJSONObject("data").getJSONObject("session").getString("id");
            rpc(f.tools,"discard_work_session",session("qa_rpc_discard",id));
            JSONObject restored=rpc(f.tools,"restore_work_session",session("qa_rpc_restore",id)).getJSONObject("data").getJSONObject("session");
            t.yes(restored.getString("id").equals(id)&&restored.getString("state").equals("PAUSED"));t.yes(f.store.pendingActions().length()==0);
        }});
        t.probe("MCP real RPC service template and offer draft edit validate duplicate",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            EstimateNativeAcceptance.setup(f);
            JSONObject entry=rpc(f.tools,"create_catalog_entry",args("qa_rpc_catalog").put("title","[TEST] QA Baustein").put("unit","h").put("category","QA")).getJSONObject("data");
            String entryId=entry.getString("entry_id");JSONObject template=rpc(f.tools,"get_offer_template",SyntheticBusinessData.object().put("entry_id",entryId)).getJSONObject("data");
            rpc(f.tools,"update_catalog_entry",args("qa_rpc_catalog_update").put("entry_id",entryId).put("expected_record_hash",template.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("title","[TEST] QA Baustein geändert")));
            rpc(f.tools,"get_offer_context",SyntheticBusinessData.object().put("customer_id","qa_test-c"));
            String id=rpc(f.tools,"create_offer_draft",EstimateNativeAcceptance.args("qa_rpc_offer",OfferContract.types()[0])).getJSONObject("data").getString("offer_id");
            JSONObject read=rpc(f.tools,"get_offer",SyntheticBusinessData.object().put("offer_id",id)).getJSONObject("data");
            rpc(f.tools,"update_offer_draft",args("qa_rpc_offer_update").put("offer_id",id).put("expected_record_hash",read.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("title","[TEST] QA Entwurf geändert")));
            read=rpc(f.tools,"get_offer",SyntheticBusinessData.object().put("offer_id",id)).getJSONObject("data");
            rpc(f.tools,"validate_offer_draft",args("qa_rpc_offer_validate").put("offer_id",id).put("expected_record_hash",read.getString("record_hash")));
            String duplicate=rpc(f.tools,"duplicate_offer",args("qa_rpc_offer_duplicate").put("offer_id",id)).getJSONObject("data").getString("offer_id");
            t.yes(!duplicate.equals(id)&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("invoices").length()==0);
        }});
    }
    private McpWorkflowAcceptance(){}
}
