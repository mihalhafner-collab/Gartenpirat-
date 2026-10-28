package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

/** Real local service over synthetic transaction ports. No Android/Drive/device acceptance. */
public final class EquipmentMaintenanceTests {
    static int checks;
    static void yes(boolean ok){checks++;if(!ok)throw new AssertionError("Maintenance check "+checks);}
    interface Work{void run()throws Exception;}
    static void denied(String code,Work work)throws Exception{try{work.run();}catch(PixelMcpWriteTools.Rejected e){yes(code.equals(e.code));return;}throw new AssertionError("Expected "+code);}
    static JSONObject obj(){return new JSONObject();}
    static JSONObject interval(int value,String unit)throws Exception{return obj().put("interval_value",value).put("interval_unit",unit);}
    static final class Fixture {
        final RevierDomainTests.Memory store=new RevierDomainTests.Memory();
        final RevierApplicationService app=new RevierApplicationService(store,RevierDomainTests.CLOCK);
        final String id;
        Fixture()throws Exception{id=app.execute("save_equipment",RevierDomainTests.args("qa_maintenance_device").put("name","QA Schere").put("condition","defective").put("maintenance_due","2027-06-01")).getJSONObject("record").getString("id");}
        JSONObject current()throws Exception{return app.query("get_equipment_maintenance",obj().put("equipment_id",id));}
        JSONObject args(String request)throws Exception{return RevierDomainTests.args(request).put("equipment_id",id).put("expected_record_hash",current().getJSONObject("record").getString("record_hash"));}
        JSONObject plan()throws Exception{return current().getJSONArray("plans").getJSONObject(0);}
        JSONObject createPlan()throws Exception{return app.execute("save_equipment_maintenance_plan",args("qa_maintenance_plan").put("operation","configure").put("title","QA Klinge geschärft").put("interval_value",1).put("interval_unit","months").put("starts_on","2027-05-18"));}
    }
    static void calendar()throws Exception {
        yes(EquipmentMaintenanceRules.advance(LocalDate.parse("2027-01-31"),interval(1,"months")).toString().equals("2027-02-28"));
        yes(EquipmentMaintenanceRules.advance(LocalDate.parse("2028-01-31"),interval(1,"months")).toString().equals("2028-02-29"));
        yes(EquipmentMaintenanceRules.advance(LocalDate.parse("2028-02-29"),interval(1,"years")).toString().equals("2029-02-28"));
        yes(EquipmentMaintenanceRules.advance(LocalDate.parse("2027-03-27"),interval(2,"days")).toString().equals("2027-03-29"));
        yes(EquipmentMaintenanceRules.advance(LocalDate.parse("2027-10-30"),interval(1,"weeks")).toString().equals("2027-11-06"));
        for(int value:new int[]{0,-1,3651})denied("INVALID_INPUT",()->EquipmentMaintenanceRules.advance(LocalDate.parse("2027-05-18"),interval(value,"days")));
        denied("INVALID_INPUT",()->EquipmentMaintenanceRules.advance(LocalDate.parse("2027-05-18"),interval(1,"cuts")));
        denied("INVALID_INPUT",()->EquipmentMaintenanceRules.advance(LocalDate.parse("2027-05-18"),interval(11,"years")));
    }
    static void lifecycle()throws Exception {
        Fixture f=new Fixture();String business=PixelMcpWriteTools.hash(f.store.snapshot);JSONObject created=f.createPlan();String plan=f.plan().getString("id");
        yes(created.getBoolean("local_saved")&&!created.getBoolean("cloud_confirmed"));yes(f.current().getInt("total")==0);
        yes(f.plan().getString("next_due_on").equals("2027-06-18"));
        JSONObject once=f.args("qa_maintenance_once").put("plan_id",plan).put("operation","bring_forward").put("next_due_on","2027-05-20").put("reason","QA Fehlschnitte");
        f.app.execute("save_equipment_maintenance_plan",once);yes(f.plan().getInt("interval_value")==1&&f.plan().getString("interval_unit").equals("months"));yes(f.plan().getString("next_due_on").equals("2027-05-20"));
        JSONObject done=f.args("qa_maintenance_done").put("plan_id",plan).put("performed_on","2027-05-18");
        JSONObject completed=f.app.execute("record_equipment_maintenance",done);yes(completed.getJSONObject("maintenance").getString("performed_on").equals("2027-05-18"));
        yes(f.plan().getString("next_due_on").equals("2027-06-18")&&!f.plan().has("one_off_due_on"));
        yes(f.current().getJSONObject("record").getString("condition").equals("defective"));
        yes(f.current().getJSONObject("record").getString("maintenance_due").equals("2027-06-01"));
        RevierApplicationService restarted=new RevierApplicationService(f.store,RevierDomainTests.CLOCK);
        yes(restarted.execute("record_equipment_maintenance",done).getBoolean("replayed"));yes(f.current().getInt("total")==1);
        JSONObject changed=new JSONObject(done.toString()).put("performed_on","2027-05-17");denied("REPLAY_MISMATCH",()->f.app.execute("record_equipment_maintenance",changed));
        JSONObject sharpen=f.args("qa_maintenance_sharpen_again").put("plan_id",plan).put("performed_on","2027-05-18").put("reason","QA erneut früher nachgeschärft").put("shorter_interval_value",1).put("shorter_interval_unit","weeks");
        f.app.execute("record_equipment_maintenance",sharpen);yes(f.current().getInt("total")==2);yes(f.plan().getString("next_due_on").equals("2027-05-25"));
        String lastEvent=f.plan().getString("last_event_id"),next=f.plan().getString("next_due_on");
        f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_older_note").put("plan_id",plan).put("performed_on","2027-05-01"));
        yes(f.current().getInt("total")==3);yes(f.plan().getString("last_event_id").equals(lastEvent)&&f.plan().getString("next_due_on").equals(next));
        String history=f.current().getJSONArray("items").toString();
        f.app.execute("save_equipment_maintenance_plan",f.args("qa_maintenance_interval_edit").put("plan_id",plan).put("operation","configure").put("title","QA Klinge schärfen").put("interval_value",2).put("interval_unit","weeks").put("reason","QA eigenes Intervall korrigiert"));
        yes(history.equals(f.current().getJSONArray("items").toString()));yes(f.plan().getString("last_completed_on").equals("2027-05-18")&&f.plan().getString("last_event_id").equals(lastEvent));
        JSONObject listed=f.app.query("list_equipment",obj()).getJSONArray("items").getJSONObject(0);
        yes(listed.getString("next_maintenance_due_on").equals("2027-06-01"));yes(listed.getString("record_hash").equals(f.current().getJSONObject("record").getString("record_hash")));
        String unchanged=f.store.values.toString();f.current();yes(unchanged.equals(f.store.values.toString()));
        yes(f.app.validateRelationships().getString("status").equals("PASS"));
        JSONObject firstPage=f.app.query("get_equipment_maintenance",obj().put("equipment_id",f.id).put("limit",1));yes(firstPage.getJSONArray("items").length()==1&&firstPage.getInt("total")==3);
        yes(f.app.query("get_equipment_maintenance",obj().put("equipment_id",f.id).put("offset",2).put("limit",1)).getJSONArray("items").getJSONObject(0).getString("performed_on").equals("2027-05-01"));
        yes(business.equals(PixelMcpWriteTools.hash(f.store.snapshot)));
        for(String key:f.store.values.keySet())yes(key.startsWith(RevierRules.ROOT));
    }
    static void failuresAndRestore()throws Exception {
        Fixture f=new Fixture();f.createPlan();String plan=f.plan().getString("id");
        f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_before_plan_begin").put("plan_id",plan).put("performed_on","2027-05-01"));
        yes(f.plan().getString("next_due_on").equals("2027-06-18")&&!f.plan().has("last_completed_on"));
        yes(!f.current().getJSONArray("items").getJSONObject(0).getBoolean("schedule_applied"));
        yes(f.app.validateRelationships().getString("status").equals("PASS"));
        JSONObject stale=f.args("qa_maintenance_stale").put("plan_id",plan).put("performed_on","2027-05-18");
        f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_first").put("plan_id",plan).put("performed_on","2027-05-18"));
        denied("WRITE_CONFLICT",()->f.app.execute("record_equipment_maintenance",stale));
        denied("INVALID_INPUT",()->f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_future").put("plan_id",plan).put("performed_on","2027-05-19")));
        denied("INVALID_INPUT",()->f.app.execute("save_equipment_maintenance_plan",f.args("qa_maintenance_not_shorter").put("operation","shorten").put("plan_id",plan).put("interval_value",2).put("interval_unit","months").put("reason","QA explicit")));
        denied("INVALID_INPUT",()->f.app.execute("save_equipment_maintenance_plan",f.args("qa_maintenance_reason_missing").put("operation","shorten").put("plan_id",plan).put("interval_value",1).put("interval_unit","weeks")));
        denied("INVALID_INPUT",()->f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_old_shortening").put("plan_id",plan).put("performed_on","2027-05-01").put("reason","QA older").put("shorter_interval_value",1).put("shorter_interval_unit","days")));
        String before=f.store.values.toString();f.store.abortReceipt=true;
        try{f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_abort").put("plan_id",plan).put("performed_on","2027-05-18"));throw new AssertionError("Expected receipt fault");}catch(IllegalStateException expected){}
        f.store.abortReceipt=false;yes(before.equals(f.store.values.toString()));
        RevierDomainTests.Memory restored=new RevierDomainTests.Memory();restored.values=new TreeMap<>(f.store.values);restored.snapshot=new JSONObject(f.store.snapshot.toString());
        RevierApplicationService app=new RevierApplicationService(restored,RevierDomainTests.CLOCK);yes(app.validateRelationships().getString("status").equals("PASS"));
        String eventKey=RevierRules.ROOT+"entity:equipment_maintenance:"+f.plan().getString("last_event_id");
        String saved=restored.values.remove(eventKey);denied("REVIER_RELATION_INVALID",app::validateRelationships);restored.values.put(eventKey,saved);
        JSONObject archive=f.args("qa_maintenance_archive").put("entity","equipment").put("entity_id",f.id).put("archived",true).put("confirmed",true);archive.remove("equipment_id");f.app.execute("set_revier_archived",archive);
        denied("EQUIPMENT_INACTIVE",()->f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_after_archive").put("plan_id",plan).put("performed_on","2027-05-18")));
        JSONObject purge=f.args("qa_maintenance_purge").put("entity","equipment").put("entity_id",f.id).put("confirmed",true);purge.remove("equipment_id");denied("DEPENDENCY_EXISTS",()->f.app.execute("purge_revier_entity",purge));
    }
    static void schemaAndSharedPort()throws Exception {
        Fixture ui=new Fixture(),mcp=new Fixture();JSONObject input=ui.args("qa_maintenance_parity").put("operation","configure").put("title","QA Klinge geschärft").put("interval_value",2).put("interval_unit","weeks").put("starts_on","2027-05-18");
        PixelMcpOwnerTools.validate("save_equipment_maintenance_plan",input);
        OwnerRepository repository=(OwnerRepository)java.lang.reflect.Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(),new Class<?>[]{OwnerRepository.class},(proxy,method,args)->{
            if(!method.getName().equals("revierAction"))throw new AssertionError("Unexpected effect port");return mcp.app.execute((String)args[0],(JSONObject)args[1]);
        });
        JSONObject fromUi=ui.app.execute("save_equipment_maintenance_plan",input),fromMcp=new OwnerApplicationService(repository).perform("save_equipment_maintenance_plan",input,"qa_maintenance_parity");
        yes(PixelMcpWriteTools.hash(fromUi).equals(PixelMcpWriteTools.hash(fromMcp)));yes(ui.store.values.equals(mcp.store.values));
        JSONObject done=ui.args("qa_maintenance_parity_done").put("plan_id",ui.plan().getString("id")).put("performed_on","2027-05-18");PixelMcpOwnerTools.validate("record_equipment_maintenance",done);
        ui.app.execute("record_equipment_maintenance",done);new OwnerApplicationService(repository).perform("record_equipment_maintenance",done,"qa_maintenance_parity_done");yes(ui.store.values.equals(mcp.store.values));
        denied("INVALID_INPUT",()->PixelMcpOwnerTools.validate("record_equipment_maintenance",new JSONObject(done.toString()).put("performed_on","today")));
    }
    static void earlierReview()throws Exception {
        Fixture f=new Fixture();f.createPlan();String plan=f.plan().getString("id");
        f.app.execute("save_equipment_maintenance_plan",f.args("qa_maintenance_review_early").put("operation","bring_forward").put("plan_id",plan).put("next_due_on","2027-05-20").put("reason","QA Fehlschnitte"));
        f.app.execute("save_equipment_maintenance_plan",f.args("qa_maintenance_review_shorter").put("operation","shorten").put("plan_id",plan).put("interval_value",1).put("interval_unit","weeks").put("reason","QA häufiger nötig"));
        yes(f.plan().getString("next_due_on").equals("2027-05-20"));yes(f.plan().getString("regular_due_on").equals("2027-05-25"));yes(f.current().getInt("total")==0);yes(f.app.validateRelationships().getString("status").equals("PASS"));
    }
    static void purgeEmptyPlan()throws Exception {
        Fixture f=new Fixture();JSONObject original=f.args("qa_maintenance_plan").put("operation","configure").put("title","QA Klinge geschärft").put("interval_value",1).put("interval_unit","months").put("starts_on","2027-05-18");
        f.app.execute("save_equipment_maintenance_plan",original);String plan=f.plan().getString("id");
        JSONObject archive=f.args("qa_maintenance_empty_archive").put("entity","equipment").put("entity_id",f.id).put("archived",true).put("confirmed",true);archive.remove("equipment_id");f.app.execute("set_revier_archived",archive);
        JSONObject purge=f.args("qa_maintenance_empty_purge").put("entity","equipment").put("entity_id",f.id).put("confirmed",true);purge.remove("equipment_id");yes(f.app.execute("purge_revier_entity",purge).getBoolean("purged"));
        denied("ENTITY_DELETED",f::current);
        denied("REQUEST_EXPIRED",()->f.app.execute("save_equipment_maintenance_plan",original));
        denied("ENTITY_DELETED",()->f.app.execute("record_equipment_maintenance",RevierDomainTests.args("qa_maintenance_deleted_retry").put("equipment_id",f.id).put("expected_record_hash","a".repeat(64)).put("plan_id",plan).put("performed_on","2027-05-18")));
        yes(new RevierApplicationService(f.store,RevierDomainTests.CLOCK).validateRelationships().getString("status").equals("PASS"));
    }
    static void formatBoundary()throws Exception {
        Fixture f=new Fixture();yes(f.current().getJSONObject("record").getInt("format_version")==1);f.createPlan();JSONObject device=f.current().getJSONObject("record");
        yes(device.getInt("format_version")==2);RevierRules.version(device);checks++;
        // The predecessor's exact gate accepts Number 1 only. Format 2 is refused
        // before its backup validator can certify this equipment as complete.
        yes(!(device.opt("format_version") instanceof Number&&device.opt("format_version").toString().equals("1")));
        denied("REVIER_SCHEMA_UPGRADE_REQUIRED",()->RevierRules.version(new JSONObject(device.toString()).put("format_version",1)));
        denied("REVIER_SCHEMA_UPGRADE_REQUIRED",()->RevierRules.version(new JSONObject(device.toString()).put("kind","property")));
        denied("REVIER_SCHEMA_UPGRADE_REQUIRED",()->RevierRules.version(new JSONObject(device.toString()).put("format_version",3)));
        f.app.execute("record_equipment_maintenance",f.args("qa_maintenance_format_event").put("plan_id",f.plan().getString("id")).put("performed_on","2027-05-18"));
        yes(f.current().getJSONArray("items").getJSONObject(0).getInt("format_version")==2);yes(f.app.validateRelationships().getString("status").equals("PASS"));
    }
    public static void main(String[] unused)throws Exception{SyntheticBusinessData.verify();calendar();lifecycle();failuresAndRestore();schemaAndSharedPort();earlierReview();purgeEmptyPlan();formatBoundary();System.out.println("PASS "+checks+" equipment maintenance calendar/early review/history/idempotency/CAS/rollback/restore/reference/shared-port checks; synthetic JVM only");}
}
