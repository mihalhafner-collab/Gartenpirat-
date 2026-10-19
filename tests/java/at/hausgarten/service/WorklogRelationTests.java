package at.hausgarten.service;
import org.json.*;
public final class WorklogRelationTests {
    static int count;
    interface Task{void run()throws Exception;}
    static void test(String label,Task task)throws Exception{task.run();count++;System.out.println("PASS "+label);}
    static void blocked(Task task)throws Exception{try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected rejection");}
    static JSONObject row()throws Exception{return SyntheticBusinessData.object().put("id","qa_w1").put("customer_id","qa_c1").put("order_id","qa_o1").put("work_date","2026-10-12").put("hours",2).put("break_minutes",0).put("kilometers",0).put("activity","Gartenpflege").put("material","Mulch").put("billed",1);}
    static JSONArray invoice(boolean issued)throws Exception{return new JSONArray().put(SyntheticBusinessData.object().put("id","qa_i1").put("worklog_ids",new JSONArray().put("qa_w1")).put("issued_at",issued?"2026-10-12T08:00:00Z":""));}
    public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
        test("new work remains compatible",()->WorklogRelationRules.validate(row(),null,new JSONArray(),invoice(true)));
        test("unlinked work remains editable",()->WorklogRelationRules.validate(row().put("activity","Andere Arbeit"),row(),new JSONArray(),new JSONArray()));
        test("issued work permits private notes",()->WorklogRelationRules.validate(row().put("notes","Nachtrag"),row(),new JSONArray(),invoice(true)));
        for(String field:new String[]{"work_date","activity","material"})test("issued "+field+" locked",()->blocked(()->WorklogRelationRules.validate(row().put(field,"Geändert"),row(),new JSONArray(),invoice(true))));
        for(String field:new String[]{"hours","break_minutes","kilometers"})test("issued "+field+" locked",()->blocked(()->WorklogRelationRules.validate(row().put(field,3),row(),new JSONArray(),invoice(true))));
        test("issued billed cannot be cleared",()->blocked(()->WorklogRelationRules.validate(row().put("billed",0),row(),new JSONArray(),invoice(true))));
        test("draft service data remains editable",()->WorklogRelationRules.validate(row().put("hours",3),row(),new JSONArray(),invoice(false)));
        test("draft invoice preserves customer relation",()->blocked(()->WorklogRelationRules.validate(row().put("customer_id","qa_c2"),row(),new JSONArray(),invoice(false))));
        test("photo ownership cannot diverge",()->blocked(()->WorklogRelationRules.validate(row().put("customer_id","qa_c2"),row(),new JSONArray().put(SyntheticBusinessData.object().put("worklog_id","qa_w1").put("customer_id","qa_c1")),new JSONArray())));
        test("photo order cannot diverge",()->blocked(()->WorklogRelationRules.validate(row().put("order_id","qa_o2"),row(),new JSONArray().put(SyntheticBusinessData.object().put("worklog_id","qa_w1").put("customer_id","qa_c1").put("order_id","qa_o1")),new JSONArray())));
        test("numeric representation not false change",()->WorklogRelationRules.validate(row().put("hours",2.0),row(),new JSONArray(),invoice(true)));
        test("preassigned new ID still requires positive duration",()->blocked(()->WorklogRules.validate(row().put("hours",0),true)));
        test("historical zero duration remains compatible",()->WorklogRules.validate(row().put("hours",0),false));
        System.out.println("RESULT "+count+" relation tests passed");
    }
}
