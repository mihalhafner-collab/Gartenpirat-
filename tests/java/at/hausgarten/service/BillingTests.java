package at.hausgarten.service;
import org.json.*;
/** Real adapters/journal on JVM using synthetic data; no Android persistence claimed. */
public final class BillingTests {
    static int passed;
    interface Task{void run()throws Exception;}
    static void test(String name,Task t)throws Exception{t.run();passed++;System.out.println("PASS "+name);}
    static void yes(boolean b){if(!b)throw new AssertionError();}
    static JSONObject obj(String s)throws Exception{return new JSONObject(s);}
    static JSONObject log(String id,String c,String b)throws Exception{return obj("{\"work_date\":\"2026-10-13\",\"activity\":\"QA Hecke\",\"hours\":3,\"billed\":0}").put("id",id).put("customer_id",c).put("billing_customer_id",b);}
    static JSONObject fixture()throws Exception{return obj("{\"master\":{\"customers\":[{\"id\":\"direct\",\"name\":\"QA Müller\"},{\"id\":\"payer\",\"name\":\"QA Brennter\"},{\"id\":\"site\",\"name\":\"QA Kirschnerhof\",\"billing_mode\":\"via_customer\",\"default_billing_customer_id\":\"payer\"},{\"id\":\"other\",\"name\":\"QA Hofer\",\"billing_mode\":\"via_customer\",\"default_billing_customer_id\":\"payer\"}],\"orders\":[{\"id\":\"order\",\"customer_id\":\"site\",\"title\":\"QA Pflege\"}],\"worklogs\":[],\"invoices\":[],\"photos\":[],\"capabilities\":{\"worklog_compare_and_save\":true,\"entity_compare_and_save\":true}},\"pending\":[]}");}
    public static void main(String[] ignored)throws Exception{
        JSONObject state=fixture(),master=state.getJSONObject("master");
        test("A direct default",()->yes(BillingRules.defaultRecipient("direct","",master).equals("direct")));
        test("B subcontractor default",()->yes(BillingRules.defaultRecipient("site","",master).equals("payer")));
        test("C second site same payer",()->yes(BillingRules.defaultRecipient("other","",master).equals("payer")));
        test("E single override leaves customer unchanged",()->{JSONObject w=log("a","site","site");BillingRules.work(w,null,master);yes(w.getString("billing_customer_id").equals("site"));yes(master.getJSONArray("customers").getJSONObject(2).getString("default_billing_customer_id").equals("payer"));});
        test("F historical missing recipient stays direct",()->{JSONObject old=log("a","site","");JSONObject row=new JSONObject(old.toString());BillingRules.work(row,old,master);yes(row.getString("billing_customer_id").equals("site"));});
        test("D group different sites and direct separately",()->{JSONArray rows=new JSONArray().put(log("a","site","payer")).put(log("b","other","payer")).put(log("c","direct",""));JSONArray groups=BillingRules.groups(rows,master);JSONObject payer=null;for(int i=0;i<groups.length();i++)if(groups.getJSONObject(i).getString("billing_customer_id").equals("payer"))payer=groups.getJSONObject(i);yes(payer!=null&&payer.getDouble("hours")==6&&payer.getJSONArray("work_records").length()==2);});
        test("invalid recipient rejected",()->{try{BillingRules.work(log("a","site","unknown"),null,master);throw new AssertionError();}catch(PixelMcpWriteTools.Rejected expected){}});
        test("billing cycle rejected",()->{try{BillingRules.customerConfig(obj("{\"id\":\"payer\",\"billing_mode\":\"via_customer\",\"default_billing_customer_id\":\"site\"}"),master.getJSONArray("customers"));throw new AssertionError();}catch(PixelMcpWriteTools.Rejected expected){}});
        test("MCP create resolves actual site and recipient",()->{JSONObject args=obj("{\"request_id\":\"qa-billing-create\",\"customer_id\":\"site\",\"work_date\":\"2026-10-13\",\"activity\":\"QA Pflege\",\"hours\":3}");JSONObject row=PixelMcpWriteTools.prepare("create_customer_work_record",args,state,"qa_action");yes(row.getString("customer_id").equals("site")&&row.getString("billing_customer_id").equals("payer"));});
        test("start_work carries recipient not payer as work customer",()->{state.put("work_session",obj("{\"state\":\"IDLE\"}"));JSONObject a=PixelMcpSessionTools.prepare("start_work",obj("{\"request_id\":\"qa-start-billing\",\"customer_id\":\"site\"}"),state);yes(a.getString("customer_id").equals("site")&&a.getString("billing_customer_id").equals("payer"));});
        test("completion retains pinned recipient",()->{state.put("work_session",obj("{\"id\":\"ses_qa\",\"state\":\"STOPPED_PENDING_REVIEW\",\"customer_id\":\"site\",\"billing_customer_id\":\"direct\",\"work_date\":\"2026-10-13\",\"hours\":3}"));JSONObject a=PixelMcpSessionTools.prepare("complete_work_session",obj("{\"request_id\":\"qa-stop-billing\",\"session_id\":\"ses_qa\",\"activity\":\"QA Pflege\"}"),state);yes(a.getString("billing_customer_id").equals("direct"));});
        test("read metadata and groups do not mutate record/hash",()->{master.getJSONArray("worklogs").put(log("a","site","payer"));String before=master.toString();PixelMcpReadTools tools=new PixelMcpReadTools(()->state);JSONObject result=tools.call("get_customer",obj("{\"customer_id\":\"site\"}"));yes(result.getJSONObject("billing_configuration").getString("billing_customer_id").equals("payer"));yes(master.toString().equals(before));JSONObject report=tools.call("get_work_record_summary",obj("{\"group_by_billing_customer\":true}"));yes(report.getJSONObject("data").getJSONArray("billing_groups").length()==1);});
        test("invoice linked recipient cannot silently change",()->{JSONObject old=log("a","site","payer"),changed=log("a","site","direct");JSONArray invoices=new JSONArray().put(obj("{\"id\":\"i\",\"customer_id\":\"payer\",\"positions\":[{\"worklog_id\":\"a\"}]}"));try{WorklogRelationRules.validate(changed,old,new JSONArray(),invoices);throw new AssertionError();}catch(IllegalArgumentException expected){}});
        System.out.println("RESULT "+passed+" billing tests passed");
    }
}
