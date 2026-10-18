package at.hausgarten.service;

import org.json.*;

public final class ManualChangeTests {
    static int tests;
    static void check(boolean ok) { tests++; if (!ok) throw new AssertionError("manual guard"); }
    static JSONObject row() throws Exception { return new JSONObject("{id:'w',customer_id:'c',billing_customer_id:'c',break_minutes:0,kilometers:0,customer_visible:1,work_date:'2026-10-18',activity:'Pflege',hours:2,notes:'Alt',billed:0,updated_at:'t1'}"); }
    public static void main(String[] args) throws Exception {
        JSONObject original=row(), current=row().put("notes","Synchronisierter Nachtrag").put("updated_at","t2");
        JSONObject edit=new JSONObject().put("id","w").put("_edit_base",original).put("_edit_changes",new JSONObject().put("activity","Hecke"));
        try { ManualChangeRules.editor(edit,current,"worklog"); throw new AssertionError("stale form accepted"); }
        catch (PixelMcpWriteTools.Rejected e) { check(e.code.equals("WRITE_CONFLICT")); }
        JSONObject fresh=ManualChangeRules.editor(edit,original,"worklog");
        check(fresh.getString("notes").equals("Alt")); check(fresh.getString("activity").equals("Hecke"));
        JSONObject inherited=row().put("_mcp_base",new JSONObject().put("id","w").put("notes","Vorgänger"));
        ManualChangeRules.prepare("worklog",inherited,current,"t3");
        check(!inherited.has("_mcp_base"));
        DriveJournalRules.compare(inherited.getJSONObject("_sync_base"),current); check(true);
        JSONObject snapshot=new JSONObject("{customers:[{id:'c',name:'Kunde',billing_mode:'direct'}],orders:[],worklogs:[],photos:[],invoices:[]}");
        JSONObject first=row(); ManualChangeRules.prepare("worklog",first,null,"t1");
        JSONObject second=new JSONObject(first.toString()).put("notes","Nachtrag");
        ManualChangeRules.prepare("worklog",second,first,"t2");
        JSONArray actions=new JSONArray().put(new JSONObject().put("client_id","first").put("kind","worklog").put("payload",first))
          .put(new JSONObject().put("client_id","second").put("kind","worklog").put("payload",second));
        check(DriveJournalRules.apply(snapshot,actions).length()==0);
        check(snapshot.getJSONArray("worklogs").getJSONObject(0).getString("notes").equals("Nachtrag"));
        check(DriveJournalRules.apply(snapshot,actions).length()==0);
        check(snapshot.getJSONArray("worklogs").length()==1);
        System.out.println("PASS "+tests+" manual baseline and chained replay checks");
    }
}
