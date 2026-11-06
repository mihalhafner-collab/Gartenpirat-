package at.hausgarten.service;

import java.util.*;
import org.json.*;

/** Financial invariants against the actual production rules, with synthetic records only. */
public final class AccountingTests {
    private static int checks;
    private static void yes(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static JSONObject order(String id, String status, String amount) {
        return new JSONObject().put("id",id).put("title","QA Auftrag "+id).put("status",status).put("scheduled_date","2026-11-01")
            .put("accounting_amount",amount).put("property_id","qa_property");
    }
    private static JSONObject work(String id, String parent, int billed, String amount) {
        return new JSONObject().put("id",id).put("activity","QA Arbeit "+id).put("order_id",parent).put("billed",billed)
            .put("work_date","2026-11-02").put("accounting_amount",amount);
    }
    private static JSONObject master(JSONArray orders, JSONArray works) { return new JSONObject().put("orders",orders).put("worklogs",works); }
    private static List<JSONObject> project(JSONObject data, String... personal) {
        return AccountingRules.INSTANCE.project(data,new LinkedHashSet<>(Arrays.asList(personal)),new JSONArray().put(new JSONObject().put("id","qa_property").put("name","QA Haus/Garten")));
    }
    private static long total(List<JSONObject> rows, String route) {
        long sum=0;
        for (JSONObject row:rows) if(row.getString("issue").isEmpty() && (route.isEmpty() || row.getString("route").equals(route))) sum=Math.addExact(sum,row.getLong("amount_minor"));
        return sum;
    }
    public static void main(String[] args) throws Exception {
        JSONObject a=order("a","Abgerechnet","900.00"), b=order("b","labor clandestinus","200.00"), c=order("c","Offen","400.00");
        c.put("paid",true); // A paid flag is not an accounting route.
        JSONObject data=master(new JSONArray().put(a).put(b).put(c),new JSONArray());
        var rows=project(data);
        yes(rows.size()==2,"Open and paid-only orders are excluded");
        yes(total(rows,"billed")==90000 && total(rows,"labor")==20000 && total(rows,"")==110000,"900 + 200 = 1100 acceptance example");
        yes(rows.get(0).getString("object_label").equals("QA Haus/Garten"),"Existing object label");
        yes(rows.get(0).getString("date_label").equals("Geplantes Auftragsdatum"),"Scheduled date is explicitly labelled");
        JSONArray works=new JSONArray().put(work("a1","a",1,"500.00")).put(work("a2","a",1,"400.00"));
        data.put("worklogs",works);
        rows=project(data);
        yes(rows.size()==2 && total(rows,"")==110000,"Parent amount and work amounts are not counted twice");
        a.remove("accounting_amount");
        yes(total(project(data),"billed")==90000,"Explicit child amounts are summed once per order when parent amount is absent");
        works.put(works.getJSONObject(0));
        yes(total(project(data),"billed")==90000,"Repeated identical work IDs do not duplicate amounts");
        a.put("accounting_amount","900.00");
        rows=project(data,"a1");
        yes(rows.stream().filter(row->row.getString("id").equals("a")).findFirst().get().getString("issue").contains("Widersprüchliche"),"Opposing marks are made visible");
        yes(total(rows,"")==20000,"A conflicting order is never arbitrarily assigned or counted");
        String proof=AccountingRules.INSTANCE.hash(data,Set.of(),"orders","a");
        yes(!proof.equals(AccountingRules.INSTANCE.hash(data,Set.of("a1"),"orders","a")),"CAS covers owner marks");
        a.put("accounting_amount","900.01");
        yes(!proof.equals(AccountingRules.INSTANCE.hash(data,Set.of(),"orders","a")),"CAS covers amount changes");
        yes(AccountingRules.INSTANCE.canonical(data,"worklogs","a1").getFirst().equals("orders"),"Child detail maps to original order");
        JSONObject next=new JSONObject(data.toString());
        try { AccountingRules.INSTANCE.requireConsistentChange(data,next,Set.of(),Set.of("a1")); throw new AssertionError("Conflict was accepted"); }
        catch (PixelMcpWriteTools.Rejected expected) { yes(expected.code.equals("ACCOUNTING_ROUTE_CONFLICT"),"Opposite marker rejected"); }
        AccountingRules.INSTANCE.requireConsistentChange(data,next,Set.of("a1"),Set.of()); checks++;
        AccountingRules.INSTANCE.requireConsistentChange(data,next,Set.of("a1"),Set.of("a1")); checks++;
        a.put("accounting_amount",JSONObject.NULL);
        works.getJSONObject(0).put("accounting_amount",JSONObject.NULL);
        yes(project(data).get(0).getString("issue").equals(AccountingRules.PENDING_AMOUNT),"Missing invoice money is deferred to phase 2, never manufactured from time/costs");
        JSONObject standalone=master(new JSONArray(),new JSONArray().put(work("solo","",0,"0.10")).put(work("solo2","",1,"0.20")).put(work("open","",0,"300.00")));
        rows=project(standalone,"solo");
        yes(rows.size()==2 && total(rows,"")==30,"Exact cents across both routes and exclusion of open amounts");
        yes(total(project(new JSONObject(standalone.toString()),"solo"),"")==30,"JSON reload preserves amounts and assignment semantics");
        for(String invalid:new String[]{"-1.00","1.001","NaN","x","9999999999999999999999"}) {
            var invalidData=master(new JSONArray().put(order("invalid","Abgerechnet",invalid)),new JSONArray());
            yes(!project(invalidData).get(0).getString("issue").isEmpty() && total(project(invalidData),"")==0,"Invalid invoice amount is flagged and excluded");
        }
        JSONObject schema=PixelMcpOwnerTools.schema("set_accounting_record");
        McpSchema.validate(new JSONObject().put("request_id","qa_accounting_request").put("collection","orders").put("entity_id","a").put("route","billed").put("expected_record_hash",proof),schema); checks++;
        yes(!schema.getJSONObject("properties").has("amount_decimal") && !Arrays.asList(PixelMcpWriteTools.EDITABLE).contains("accounting_amount") && !Arrays.asList(EntityContract.ORDER_FIELDS).contains("accounting_amount"),"Phase 1 exposes no manual invoice amount writer");
        System.out.println("PASS "+checks+" accounting amount, routes, grouping, conflict, reload and schema checks");
    }
}
