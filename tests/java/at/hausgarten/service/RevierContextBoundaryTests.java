package at.hausgarten.service;

import org.json.*;

/** Work/photo/START context is checked by the same staged-backup boundary, with optional legacy links. */
public final class RevierContextBoundaryTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Context boundary check "+checks);}
    interface Task {void run()throws Exception;}
    static void denied(String code,Task task)throws Exception {try{task.run();}catch(PixelMcpWriteTools.Rejected expected){yes(expected.code.equals(code));return;}throw new AssertionError("Expected "+code);}
    public static void main(String[] ignored)throws Exception {
        SyntheticBusinessData.verify();RevierDomainTests.Memory store=new RevierDomainTests.Memory();RevierApplicationService app=new RevierApplicationService(store,RevierDomainTests.CLOCK);String original=PixelMcpWriteTools.hash(store.snapshot);yes(app.validateRelationships().getString("status").equals("PASS")&&original.equals(PixelMcpWriteTools.hash(store.snapshot)));
        String property=RevierDomainTests.createProperty(app,"context_primary").getString("id"),other=RevierDomainTests.createProperty(app,"context_other").getString("id");String visit=app.execute("plan_visit",RevierDomainTests.args("qa_context_visit").put("property_id",property).put("title","QA Einsatz").put("date","2027-05-18").put("planned_minutes",90)).getString("visit_id");JSONObject log=store.snapshot.getJSONArray("worklogs").getJSONObject(0);log.put("property_id",property).put("visit_id",visit).put("session_id","qa_session");JSONObject photo=new JSONObject().put("id","qa_context_photo").put("customer_id","qa_customer_1").put("property_id",property).put("visit_id",visit).put("session_id","qa_session").put("worklog_id",log.getString("id")).put("role","work");store.snapshot.getJSONArray("photos").put(photo);JSONObject session=new JSONObject().put("id","qa_session").put("customer_id","qa_customer_1").put("order_id","").put("property_id",property).put("visit_id",visit).put("_start_customer_id","qa_customer_1").put("_start_order_id","");store.sessionRows.put(session);yes(app.validateRelationships().getString("status").equals("PASS"));
        log.put("visit_id","qa_missing_visit");denied("REFERENCE_NOT_FOUND",()->app.validateRelationships());log.put("visit_id",visit);
        photo.put("property_id",other);denied("REVIER_RELATION_INVALID",()->app.validateRelationships());photo.put("property_id",property);
        session.put("_start_customer_id","qa_wrong_customer");denied("REVIER_RELATION_INVALID",()->app.validateRelationships());session.put("_start_customer_id","qa_customer_1");
        session.put("property_id",other);denied("REVIER_RELATION_INVALID",()->app.validateRelationships());session.put("property_id",property);
        photo.put("role","profile");denied("REVIER_RELATION_INVALID",()->app.validateRelationships());photo.put("role","work");yes(app.validateRelationships().getString("status").equals("PASS"));
        System.out.println("PASS "+checks+" captured canonical work/photo/session START context/legacy/rollback checks;synthetic port,no SQLite/device proof");
    }
}
