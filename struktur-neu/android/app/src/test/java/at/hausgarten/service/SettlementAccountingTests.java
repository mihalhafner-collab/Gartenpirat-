package at.hausgarten.service;

import java.util.*;
import org.json.*;

/** Actual production projection with synthetic confirmed, partial and corrected scope. */
public final class SettlementAccountingTests {
    private static int checks;
    private static void yes(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static JSONObject copy(JSONObject value) throws Exception { return new JSONObject(value.toString()); }
    private static JSONObject order(String id,String status) throws Exception { return new JSONObject().put("id",id).put("title","QA "+id).put("status",status).put("scheduled_date","2026-01-01").put("accounting_amount","9999.99"); }
    private static JSONObject work(String id,String order,int billed,String date) throws Exception { return new JSONObject().put("id",id).put("order_id",order).put("billed",billed).put("activity","QA "+id).put("work_date",date).put("property_id","qa_property"); }
    private static JSONObject line(String id,String collection,String source,long amount) throws Exception { return new JSONObject().put("id",id).put("source_collection",collection).put("source_id",source).put("amount_minor",amount); }
    private static JSONObject settlement(String id,String status,String route,JSONObject... lines) throws Exception {
        JSONArray refs=new JSONArray();Set<String>seen=new HashSet<>();
        for(JSONObject line:lines)if(!line.getString("source_collection").isEmpty()&&seen.add(line.getString("source_collection")+":"+line.getString("source_id"))) refs.put(new JSONObject().put("collection",line.getString("source_collection")).put("id",line.getString("source_id")));
        return new JSONObject().put("id",id).put("status",status).put("kind",route.equals("labor")?"internal":"invoice").put("route",route).put("title","QA Abschluss "+id).put("service_to","2026-02-01").put("issue_date","2026-11-06").put("positions",new JSONArray(Arrays.asList(lines))).put("sources",refs);
    }
    private static JSONObject ledger(JSONObject... records) throws Exception { return new JSONObject().put("records",new JSONArray(Arrays.asList(records))); }
    private static List<JSONObject> rows(JSONObject master,JSONObject ledger,String...personal) throws Exception { return AccountingRules.INSTANCE.project(master,new LinkedHashSet<>(Arrays.asList(personal)),new JSONArray(),ledger); }
    private static JSONObject row(List<JSONObject>rows,String key) throws Exception { return rows.stream().filter(row->row.optString("key").equals(key)).findFirst().orElseThrow(); }
    private static long total(List<JSONObject>rows,String route) throws Exception { long amount=0;for(JSONObject row:rows)if((row.getString("issue_code").equals("")||row.getString("issue_code").equals("partial_amount"))&&!row.isNull("amount_minor")&&(route.isEmpty()||route.equals(row.getString("route"))))amount=Math.addExact(amount,row.getLong("amount_minor"));return amount; }
    public static void main(String[]args) throws Exception {
        JSONObject master=new JSONObject().put("orders",new JSONArray().put(order("o1","Abgerechnet")).put(order("o2","labor clandestinus")))
            .put("worklogs",new JSONArray().put(work("w1","o1",1,"2026-02-01")).put(work("w2","o2",0,"2026-02-02")));
        JSONObject invoice=settlement("invoice","confirmed","billed",line("p1","worklogs","w1",80000),line("p2","worklogs","w1",10000));
        JSONObject internal=settlement("internal","confirmed","labor",line("p3","worklogs","w2",20000));
        JSONObject ledger=ledger(invoice,internal);
        List<JSONObject> result=rows(master,ledger,"w2");
        yes(result.size()==2,"one unit per order");
        yes(total(result,"billed")==90000,"invoice amount counts multiple positions of same source once each");
        yes(total(result,"labor")==20000,"internal confirmed amount included");
        yes(total(result,"")==110000,"900 EUR invoice plus 200 EUR internal is 1100 EUR");
        yes(total(rows(copy(master),copy(ledger),"w2"),"")==110000,"same values after JSON persistence/reopen");
        yes(row(result,"orders:o1").getString("work_date").equals("2026-02-01"),"invoice date must not replace source work date");
        String before=master.toString();
        JSONObject unmarked=copy(master);for(int i=0;i<unmarked.getJSONArray("orders").length();i++)unmarked.getJSONArray("orders").getJSONObject(i).put("status","Erledigt");unmarked.getJSONArray("worklogs").getJSONObject(0).put("billed",0);
        String unmarkedBefore=unmarked.toString();
        yes(total(rows(unmarked,ledger),"")==110000,"active bindings visible without stored user markings");
        yes(unmarked.toString().equals(unmarkedBefore)&&master.toString().equals(before),"projection never changes stored assignment");

        JSONObject partial=copy(master);partial.getJSONArray("worklogs").put(work("w3","o1",0,"2026-03-01"));
        JSONObject partialRow=row(rows(partial,ledger,"w2"),"orders:o1");
        yes(partialRow.getLong("amount_minor")==90000,"known part remains known");
        yes(partialRow.getString("issue_code").equals("partial_amount"),"additional assigned work keeps scope incomplete");
        yes(total(rows(partial,ledger,"w2"),"")==110000,"partial row and sum use same known amount");
        JSONObject unmarkedPartial=copy(unmarked);unmarkedPartial.getJSONArray("worklogs").put(work("w3","o1",0,"2026-03-01"));
        yes(row(rows(unmarkedPartial,ledger),"orders:o1").getString("issue_code").equals("partial_amount"),"other unmarked work remains visibly open within order scope");
        yes(row(rows(unmarkedPartial,ledger),"orders:o1").getString("work_date").equals("2026-02-01"),"open unselected work does not shift the included work date");
        JSONObject prepared=copy(invoice).put("status","prepared");
        JSONObject pending=row(rows(master,ledger(prepared,internal),"w2"),"orders:o1");
        yes(pending.isNull("amount_minor")&&pending.getString("issue_code").equals("pending_amount"),"reserved invoice has no confirmed amount and does not revive legacy amount");
        yes(total(rows(master,ledger(prepared,internal),"w2"),"")==20000,"only internal confirmed amount summed before PDF completion");
        JSONObject abandoned=copy(invoice).put("status","abandoned");
        List<JSONObject> abandonedRows=rows(master,ledger(abandoned,internal),"w2");
        yes(row(abandonedRows,"orders:o1").isNull("amount_minor")&&row(abandonedRows,"orders:o1").getString("issue_code").equals("pending_amount"),"abandoned reservation must not resurrect legacy accounting_amount");
        yes(abandonedRows.size()==2&&total(abandonedRows,"")==20000,"marked abandoned source remains pending while unrelated internal amount survives");
        yes(rows(unmarked,ledger(abandoned)).isEmpty(),"abandoned source binding does not create an unmarked overview unit");
        JSONObject abandonedMixed=copy(abandoned);abandonedMixed.getJSONArray("positions").put(line("abandoned-manual","","",10000));
        yes(rows(master,ledger(abandonedMixed,internal),"w2").size()==2,"abandoned mixed reservation creates no artificial manual overview unit");
        JSONObject abandonedManual=settlement("abandoned-manual-only","abandoned","billed",line("abandoned-manual-only-line","","",10000));
        yes(rows(new JSONObject().put("orders",new JSONArray()).put("worklogs",new JSONArray()),ledger(abandonedManual)).isEmpty(),"abandoned purely manual reservation contributes no pending unit or amount");
        yes(total(rows(master,ledger(abandoned,copy(invoice).put("id","recovered-invoice"),internal),"w2"),"")==110000,"new confirmed operation may use released source without reviving legacy amount");

        JSONObject cancelled=copy(invoice).put("status","cancelled");
        JSONObject correction=copy(invoice).put("id","correction").put("kind","cancellation").put("positions",new JSONArray().put(line("negative","worklogs","w1",-90000)));
        List<JSONObject> corrected=rows(master,ledger(cancelled,internal,correction),"w2");
        yes(row(corrected,"orders:o1").getLong("amount_minor")==0,"full correction neutralizes only original amount");
        yes(total(corrected,"")==20000,"unaffected internal amount survives invoice cancellation");
        yes(corrected.size()==2,"marked corrected order remains visible");
        JSONObject replacement=settlement("replacement","confirmed","billed",line("replacement-line","worklogs","w1",85000));
        yes(total(rows(master,ledger(cancelled,internal,correction,replacement),"w2"),"")==105000,"replacement counts once and old legacy amount stays neutralized");

        JSONObject mixed=copy(invoice);mixed.getJSONArray("positions").put(line("manual-line","","",10000));
        List<JSONObject> mixedRows=rows(master,ledger(mixed,internal),"w2");
        yes(mixedRows.size()==3,"manual share creates independent settlement unit");
        yes(row(mixedRows,"settlements:invoice").getLong("amount_minor")==10000,"only manual share represented in manual row");
        yes(row(mixedRows,"orders:o1").getLong("amount_minor")==90000,"manual share not copied onto order");
        yes(total(mixedRows,"")==120000,"mixed total has no extra invoice-wide addition");
        yes(row(mixedRows,"settlements:invoice").getString("date_label").contains("manuell"),"manual time basis visibly identified");
        yes(master.toString().equals(before),"manual settlement creates no work or order");

        JSONObject collectionMaster=copy(master);collectionMaster.getJSONArray("orders").put(order("o3","Abgerechnet"));collectionMaster.getJSONArray("worklogs").put(work("w3","o3",1,"2026-03-01"));
        JSONObject collection=settlement("collection","confirmed","billed",line("first-share","worklogs","w1",60000),line("second-share","worklogs","w3",30000));
        List<JSONObject>collectionRows=rows(collectionMaster,ledger(collection,internal),"w2");
        yes(row(collectionRows,"orders:o1").getLong("amount_minor")==60000&&row(collectionRows,"orders:o3").getLong("amount_minor")==30000,"collection invoice allocated by actual positions");
        yes(total(collectionRows,"")==110000,"collection invoice not copied in full onto both orders");

        JSONObject whole=settlement("whole","confirmed","billed",line("whole-line","orders","o1",90000));
        whole.put("source_snapshots",new JSONArray().put(new JSONObject().put("collection","orders").put("id","o1").put("included_work_ids",new JSONArray().put("w1"))));
        yes(row(rows(master,ledger(whole,internal),"w2"),"orders:o1").getString("issue_code").equals(""),"frozen order scope initially complete");
        yes(row(rows(partial,ledger(whole,internal),"w2"),"orders:o1").getString("issue_code").equals("partial_amount"),"new work after order scope closure remains open");
        JSONObject followup=settlement("followup","confirmed","billed",line("new-work-line","worklogs","w3",10000));
        yes(total(rows(partial,ledger(whole,internal,followup),"w2"),"")==120000,"followup contributes only new work");
        yes(row(rows(partial,ledger(whole,internal,followup),"w2"),"orders:o1").getString("issue_code").equals(""),"independent followup completes known assigned scope");
        JSONObject standalone=new JSONObject().put("orders",new JSONArray()).put("worklogs",new JSONArray().put(work("standalone","",0,"2026-05-01")));
        yes(rows(standalone,ledger(settlement("standalone-ledger","confirmed","labor",line("standalone-line","worklogs","standalone",20000)))).get(0).getString("key").equals("worklogs:standalone"),"work without order remains its own unit");
        System.out.println("PASS settlement accounting: "+checks+" checks");
    }
}
