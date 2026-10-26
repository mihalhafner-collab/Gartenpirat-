package at.hausgarten.service;

import org.json.*;

/** Stable object/visit linkage through the actual persistent timer and photo repository. */
final class LocalSessionContextNativeAcceptance {
    private static final class Clock implements WorkSessionStore.Clock {
        long wall=java.time.Instant.parse("2027-05-18T06:00:00Z").toEpochMilli(),elapsed=1000;
        public long wallMillis(){return wall;}public long elapsedMillis(){return elapsed;}public String bootMarker(){return "qa_context_boot";}
        void minutes(int n){wall+=n*60000L;elapsed+=n*60000L;}
    }
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("local timer immutable object visit links survive reopen and exact150min completion",()->{
            Clock clock=new Clock();try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext(),clock)){
                RevierApplicationService app=new RevierApplicationService(new RevierLocalRepository(f.store,null));
                JSONObject property=app.execute("create_property",new JSONObject().put("request_id","qa_context_property").put("customer_id","qa_c").put("name","QA Wiese")).getJSONObject("record");
                JSONObject visit=app.execute("plan_visit",new JSONObject().put("request_id","qa_context_visit").put("property_id",property.getString("id")).put("title","QA Pflege").put("date","2027-05-18").put("planned_minutes",180)).getJSONObject("record");
                JSONObject start=new JSONObject().put("request_id","qa_context_start").put("customer_id","qa_c").put("property_id",property.getString("id")).put("visit_id",visit.getString("id")).put("activity","QA Pflege").put("work_date","2027-05-18");
                JSONObject session=f.store.startWorkSession(start);String id=session.getString("id");test.yes(session.getString("property_id").equals(property.getString("id"))&&session.getString("visit_id").equals(visit.getString("id")));
                test.yes(f.store.startWorkSession(new JSONObject(start.toString()).put("request_id","qa_context_second_start")).getString("id").equals(id));
                clock.minutes(75);f.store.workSessions().pause(id,"qa_context_pause");clock.minutes(15);f.store.workSessions().resume(id,"qa_context_resume");clock.minutes(60);test.yes(f.store.workSessions().current(id).getLong("worked_ms")==135*60000L);clock.minutes(15);f.store.workSessions().stop(id,"qa_context_stop");
                try(PixelLocalStore reopened=new PixelLocalStore(f.context,clock)){
                    JSONObject persisted=reopened.workSessions().current(id);test.yes(persisted.getLong("worked_ms")==150*60000L&&persisted.getString("visit_id").equals(visit.getString("id")));
                    JSONObject changed=new JSONObject().put("customer_id","qa_c").put("activity","QA Pflege").put("property_id","qa_wrong_property");try{reopened.completeWorkSession(id,changed,"qa_context_changed");throw new AssertionError("changed session context accepted");}catch(IllegalArgumentException expected){test.yes(true);}
                    String work=reopened.completeWorkSession(id,new JSONObject().put("customer_id","qa_c").put("activity","QA Pflege"),"qa_context_finish");test.yes(reopened.completeWorkSession(id,new JSONObject(),"qa_context_finish_retry").equals(work));JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs"),work);test.yes(row.getDouble("hours")==2.5&&row.getString("property_id").equals(property.getString("id"))&&row.getString("visit_id").equals(visit.getString("id")));
                }
            }
        });
        test.probe("local timer rejects unrelated or cancelled visit before persisting any session",()->{
            try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
                JSONObject start=new JSONObject().put("request_id","qa_context_missing_visit").put("customer_id","qa_c").put("property_id","qa_absent").put("visit_id","qa_absent_visit").put("activity","QA Pflege");
                try{f.store.startWorkSession(start);throw new AssertionError("unrelated visit accepted");}catch(IllegalArgumentException expected){test.yes(true);}test.yes(f.store.workSessions().current().getString("state").equals("IDLE"));
            }
        });
    }
    private LocalSessionContextNativeAcceptance(){}
}
