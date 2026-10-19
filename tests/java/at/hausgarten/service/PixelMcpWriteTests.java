package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Executes real schemas, overlay, validators and MCP dispatch with isolated JSON fixtures. */
public final class PixelMcpWriteTests {
    interface Task { void run() throws Exception; }
    static int count;
    static void test(String title,Task task)throws Exception{task.run();count++;System.out.println("PASS "+title);}
    static void yes(boolean value){if(!value)throw new AssertionError("Assertion failed");}
    static void bad(Task task)throws Exception{try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected rejection");}
    static JSONObject state()throws Exception{JSONObject s=PixelTunnelContractTests.fixture();s.getJSONObject("master").put("capabilities",new JSONObject().put("worklog_compare_and_save",true));return s;}
    static JSONObject create()throws Exception{return new JSONObject().put("request_id","test-request-001").put("customer_id","c1").put("work_date","2026-10-10").put("activity","Heckenschnitt ÄÖÜ").put("hours",3);}
    static JSONObject prepare(JSONObject args)throws Exception{return PixelMcpWriteTools.prepare("create_customer_work_record",args,state(),"test-action-001");}
    static JSONObject update(JSONObject state,JSONObject patch)throws Exception{
        JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(state).getJSONArray("worklogs"),"w1");
        return new JSONObject().put("request_id","update-request-001").put("work_record_id","w1").put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpWriteTools.base(row))).put("updates",patch);
    }
    public static void main(String[] ignored)throws Exception{
        test("customer-only create/defaults/stable new ID",()->{JSONObject p=prepare(create());yes(p.getString("order_id").isEmpty());yes(p.getInt("billed")==0&&p.getInt("customer_visible")==1&&p.getInt("break_minutes")==0&&p.getDouble("kilometers")==0);yes(p.getString("source").equals("ChatGPT Sprache"));yes(p.getString("id").equals(prepare(create()).getString("id")));});
        test("order auto-fills actual customer and location",()->{JSONObject s=state();s.getJSONObject("master").getJSONArray("orders").getJSONObject(0).put("location","Gartenweg 1");JSONObject a=create();a.remove("customer_id");a.put("order_id","o1");JSONObject p=PixelMcpWriteTools.prepare("create_work_record",a,s,"test2");yes(p.getString("customer_id").equals("c1")&&p.getString("location").equals("Gartenweg 1"));});
        for(String field:new String[]{"customer_id","work_date","activity","request_id"})test("missing "+field,()->{JSONObject a=create();a.remove(field);bad(()->prepare(a));});
        for(String field:new String[]{"id","billed","source","arbitrary_file","sql"})test("protected create field "+field,()->bad(()->prepare(create().put(field,"attacker"))));
        test("unknown customer",()->bad(()->prepare(create().put("customer_id","invented"))));
        test("customer/order mismatch",()->bad(()->prepare(create().put("order_id","o2"))));
        test("unknown order",()->bad(()->prepare(create().put("order_id","invented"))));
        test("strict create_work_record still requires order",()->bad(()->PixelMcpWriteTools.prepare("create_work_record",create(),state(),"test")));
        for(String field:new String[]{"hours","kilometers","break_minutes"})test("negative "+field,()->bad(()->prepare(create().put(field,-1))));
        test("fractional pause",()->bad(()->prepare(create().put("break_minutes",1.5))));
        test("numeric string rejected",()->bad(()->prepare(create().put("hours","3"))));
        test("zero/missing duration",()->{bad(()->prepare(create().put("hours",0)));JSONObject a=create();a.remove("hours");bad(()->prepare(a));});
        test("invalid calendar day",()->bad(()->prepare(create().put("work_date","2026-02-30"))));
        test("time calculation pause",()->{JSONObject a=create();a.remove("hours");JSONObject p=prepare(a.put("start_time","08:00").put("end_time","12:30").put("break_minutes",30));yes(p.getDouble("hours")==4);});
        test("contradictory supplied hours",()->bad(()->prepare(create().put("start_time","08:00").put("end_time","12:30"))));
        test("one time missing",()->bad(()->prepare(create().put("start_time","08:00"))));
        test("invalid time",()->bad(()->prepare(create().put("start_time","28:00").put("end_time","12:00"))));
        test("pause longer than job",()->{JSONObject a=create();a.remove("hours");bad(()->prepare(a.put("start_time","08:00").put("end_time","09:00").put("break_minutes",60)));});
        test("overnight requires explicit opt-in",()->{JSONObject a=create();a.remove("hours");a.put("start_time","23:00").put("end_time","01:00");bad(()->prepare(a));yes(prepare(a.put("allow_overnight",true)).getDouble("hours")==2);});
        test("exact duplicate blocked",()->bad(()->prepare(create().put("work_date","2026-10-09").put("activity"," Hecke pflegen ").put("hours",1.5))));
        test("existing deterministic ID never overwritten",()->{JSONObject s=state();String id=prepare(create()).getString("id");s.getJSONObject("master").getJSONArray("worklogs").put(new JSONObject().put("id",id).put("customer_id","c1").put("activity","other").put("hours",4).put("work_date","2026-10-08"));bad(()->PixelMcpWriteTools.prepare("create_customer_work_record",create(),s,"test-action-001"));});
        test("update fails closed without confirmed importer protection",()->{JSONObject s=state(),a=update(s,new JSONObject().put("notes","Zusatz"));s.getJSONObject("master").remove("capabilities");try{PixelMcpWriteTools.prepare("update_work_record",a,s,"upd");throw new AssertionError("Expected rejection");}catch(PixelMcpWriteTools.Rejected e){yes(e.code.equals("REMOTE_GUARD_REQUIRED"));}PixelMcpWriteTools.prepare("create_customer_work_record",create(),s,"new-request");s.getJSONObject("master").put("capabilities",new JSONObject().put("worklog_compare_and_save","true"));bad(()->PixelMcpWriteTools.prepare("update_work_record",a,s,"upd"));});
        test("update preserves ID/customer/source",()->{JSONObject s=state();JSONObject p=PixelMcpWriteTools.prepare("update_work_record",update(s,new JSONObject().put("notes","Zusatz")),s,"upd");yes(p.getString("id").equals("w1")&&p.getString("customer_id").equals("c1"));yes(p.has("_mcp_base"));});
        test("stale record hash",()->{JSONObject s=state(),a=update(s,new JSONObject().put("notes","Zusatz"));s.getJSONObject("master").getJSONArray("worklogs").getJSONObject(0).put("notes","parallel");bad(()->PixelMcpWriteTools.prepare("update_work_record",a,s,"upd"));});
        test("pending record update conflict",()->{JSONObject s=state(),a=update(s,new JSONObject().put("notes","Zusatz"));s.getJSONArray("pending").put(new JSONObject().put("kind","worklog").put("payload",new JSONObject().put("id","w1")));bad(()->PixelMcpWriteTools.prepare("update_work_record",a,s,"upd"));});
        for(String field:new String[]{"id","customer_id","billed","source"})test("protected update "+field,()->{JSONObject s=state();bad(()->PixelMcpWriteTools.prepare("update_work_record",update(s,new JSONObject().put(field,"x")),s,"upd"));});
        test("billed data locked/notes allowed",()->{JSONObject s=state();s.getJSONObject("master").getJSONArray("worklogs").getJSONObject(0).put("billed",1);bad(()->PixelMcpWriteTools.prepare("update_work_record",update(s,new JSONObject().put("hours",4)),s,"upd"));PixelMcpWriteTools.prepare("update_work_record",update(s,new JSONObject().put("notes","Intern")),s,"upd");});
        test("canonical hash numeric/order stable",()->yes(PixelMcpWriteTools.hash(new JSONObject("{\"hours\":3,\"id\":\"w\"}")).equals(PixelMcpWriteTools.hash(new JSONObject("{\"id\":\"w\",\"hours\":3.0}")))));
        test("read-only unchanged and cannot write",()->{PixelMcpReadTools tools=new PixelMcpReadTools(()->state());yes(tools.tools().length()==18+PixelMcpOwnerTools.READS.length+OfferTools.READS.length+AppointmentTools.READS.length);bad(()->tools.call("create_customer_work_record",create()));bad(()->tools.call("create_customer",new JSONObject().put("request_id","test-customer").put("name","Test")));});
        test("all three tools advertised non-destructive/idempotent",()->{JSONArray tools=PixelMcpWriteTools.tools();yes(tools.length()==3);for(int i=0;i<tools.length();i++){JSONObject a=tools.getJSONObject(i).getJSONObject("annotations");yes(!a.getBoolean("readOnlyHint")&&!a.getBoolean("destructiveHint")&&a.getBoolean("idempotentHint"));}});
        test("read provides revision without altering business row",()->{JSONObject s=state();PixelMcpReadTools tools=new PixelMcpReadTools(()->s);JSONObject r=tools.call("get_work_record",new JSONObject().put("work_record_id","w1"));yes(r.getString("record_hash").length()==64);yes(!r.getJSONObject("data").has("record_hash"));});
        test("write RPC routes / structured safe errors",()->{
            PixelMcpReadTools tools=new PixelMcpReadTools(()->state(),new PixelMcpWriteTools.Sink(){public JSONObject execute(String t,JSONObject a)throws Exception{return new JSONObject().put("ok",true).put("data",PixelMcpWriteTools.prepare(t,a,state(),"act"));}public JSONObject status(String id,JSONObject s){return new JSONObject();}});
            yes(tools.tools().length()==27+PixelMcpSessionTools.NAMES.length+PixelMcpOwnerTools.READS.length+PixelMcpOwnerTools.WRITES.length+OfferTools.READS.length+OfferTools.WRITES.length+AppointmentTools.READS.length+AppointmentTools.WRITES.length);JSONObject call=new JSONObject().put("jsonrpc","2.0").put("id",1).put("method","tools/call").put("params",new JSONObject().put("name","create_customer_work_record").put("arguments",create()));yes(!tools.rpc(call).getJSONObject("result").getBoolean("isError"));call.getJSONObject("params").getJSONObject("arguments").put("customer_id","unknown");yes(tools.rpc(call).getJSONObject("result").getJSONObject("structuredContent").getJSONObject("error").getString("code").equals("REFERENCE_NOT_FOUND"));
        });
        System.out.println("PASS total="+count+" isolated write/schema tests");
    }
}
