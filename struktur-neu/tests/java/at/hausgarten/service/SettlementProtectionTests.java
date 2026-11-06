package at.hausgarten.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Proxy;

/** Synthetic service evidence, route bindings and independent user markings. */
public final class SettlementProtectionTests {
    private static int checks;
    private interface Checked { void run() throws Exception; }
    private static void allowed(Checked action, String reason) throws Exception {
        action.run(); checks++;
    }
    private static void yes(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
    private static void rejected(Checked action, String reason) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(reason);
    }
    private static JSONObject copy(JSONObject value) { return new JSONObject(value.toString()); }
    private static JSONObject ledger(String status, String route, String kind, String collection, String id) {
        return new JSONObject().put("records",new JSONArray().put(new JSONObject().put("id","qa_settlement")
            .put("status",status).put("route",route).put("kind",kind)
            .put("sources",new JSONArray().put(new JSONObject().put("collection",collection).put("id",id)))));
    }
    public static void main(String[] args) throws Exception {
        JSONObject order=new JSONObject().put("id","qa_order").put("customer_id","qa_customer").put("status","Abgerechnet").put("title","QA Baumarbeiten");
        JSONObject work=new JSONObject().put("id","qa_work").put("customer_id","qa_customer").put("order_id","qa_order").put("activity","QA Schnitt").put("hours","2.5").put("billed",0);
        JSONObject other=copy(work).put("id","qa_open_work");
        JSONObject master=new JSONObject().put("orders",new JSONArray().put(order)).put("worklogs",new JSONArray().put(work).put(other));
        for (String status:new String[]{"prepared","confirmed","cancelled"}) {
            JSONObject retained=ledger(status,"labor","internal","worklogs","qa_work");
            rejected(()->SettlementSourceProtection.INSTANCE.requireMutable(master,retained,"worklogs","qa_work",work,copy(work).put("hours","3")),"retained performed quantity must stay immutable");
            allowed(()->SettlementSourceProtection.INSTANCE.requireMutable(master,retained,"worklogs","qa_work",copy(work).put("billed",1),work),"removing a marking is independent of service evidence");
            allowed(()->SettlementSourceProtection.INSTANCE.requireMutable(master,retained,"worklogs","qa_work",work,copy(work).put("notes","QA interne Notiz")),"internal note remains editable");
            allowed(()->SettlementSourceProtection.INSTANCE.requireMutable(master,retained,"worklogs","qa_open_work",other,copy(other).put("hours","3")),"other open work stays usable");
            rejected(()->SettlementSourceProtection.INSTANCE.requireDeletable(master,retained,"worklog","qa_work"),"retained source must not be deleted");
            rejected(()->SettlementSourceProtection.INSTANCE.requireDeletable(master,retained,"order","qa_order"),"parent deletion must not detach retained source evidence");
            rejected(()->SettlementSourceProtection.INSTANCE.requireDeletable(master,retained,"customer","qa_customer"),"customer must not orphan retained evidence");
            allowed(()->SettlementSourceProtection.INSTANCE.requireDeletable(master,retained,"worklog","qa_open_work"),"unbilled unrelated source stays deletable");
        }
        for (String status:new String[]{"prepared","confirmed"}) {
            JSONObject internal=ledger(status,"labor","internal","worklogs","qa_work");
            rejected(()->SettlementSourceProtection.INSTANCE.requireRouteChange(master,internal,"orders","qa_order","billed"),"active internal settlement cannot also become invoice");
            rejected(()->SettlementSourceProtection.INSTANCE.requireRouteChange(master,internal,"orders","qa_order",""),"active internal binding cannot silently disappear");
            allowed(()->SettlementSourceProtection.INSTANCE.requireRouteChange(master,internal,"orders","qa_order","labor"),"same route remains available");
            JSONObject invoice=ledger(status,"billed","invoice","orders","qa_order");
            rejected(()->SettlementSourceProtection.INSTANCE.requireRouteChange(master,invoice,"orders","qa_order","labor"),"active invoice binding requires explicit cancellation");
        }
        JSONObject cancelled=ledger("cancelled","labor","internal","worklogs","qa_work");
        allowed(()->SettlementSourceProtection.INSTANCE.requireRouteChange(master,cancelled,"orders","qa_order","billed"),"cancelled operation permits deliberate route correction");
        JSONObject scoped=ledger("confirmed","billed","invoice","orders","qa_order");
        scoped.getJSONArray("records").getJSONObject(0).put("source_snapshots",new JSONArray().put(new JSONObject().put("collection","orders").put("id","qa_order").put("included_work_ids",new JSONArray().put("qa_work"))));
        rejected(()->SettlementSourceProtection.INSTANCE.requireMutable(master,scoped,"worklogs","qa_work",work,copy(work).put("hours","3")),"order source retains exactly its frozen included work evidence");
        allowed(()->SettlementSourceProtection.INSTANCE.requireMutable(master,scoped,"worklogs","qa_open_work",other,copy(other).put("hours","3")),"work outside frozen order scope stays editable");
        JSONObject boundInternal=ledger("confirmed","labor","internal","worklogs","qa_work");
        OwnerRepository internalRepository=repository(master,boundInternal);
        rejected(()->new WorkApplicationService(internalRepository).perform("set_work_record_billed",new JSONObject().put("work_record_id","qa_work").put("billed",true).put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpWriteTools.base(work))).put("reason","QA Auswahl"),"qa-mark-billed"),"direct billed writer cannot bypass active internal route");
        OwnerRepository invoiceRepository=repository(master,ledger("confirmed","billed","invoice","worklogs","qa_work"));
        rejected(()->new WorkApplicationService(invoiceRepository).perform("set_personal_paid",new JSONObject().put("work_record_id","qa_work").put("paid",true),"qa-mark-labor"),"personal writer cannot bypass active invoice route");
        JSONObject issued=new JSONObject().put("id","qa_old_invoice").put("issued_at","2026-11-06T10:00:00Z").put("worklog_ids",new JSONArray().put("qa_work"));
        allowed(()->WorklogRelationRules.validate(work,copy(work).put("billed",1),new JSONArray(),new JSONArray().put(issued)),"legacy invoice relation must not force billed marking");
        JSONObject original=ledger("confirmed","billed","invoice","worklogs","qa_work").getJSONArray("records").getJSONObject(0);
        JSONObject internal=ledger("confirmed","labor","internal","worklogs","qa_work").getJSONArray("records").getJSONObject(0).put("id","qa_internal");
        JSONObject inventory=BackupInventory.inspect(new JSONObject().put("settlements",new JSONArray().put(original).put(internal)),(kind,row)->"ORIGINAL_FILE_MISSING");
        yes(inventory.getJSONObject("counts").getInt("settlements")==2,"all local settlement types counted in backup inventory");
        yes(inventory.getJSONArray("warnings").length()==1,"only invoice requires an original PDF");
        yes(inventory.getJSONArray("lastIssuedDocumentIds").length()==1,"issued local invoice identified in backup metadata");
        yes(inventory.getJSONArray("lastIssuedDocumentIds").getString(0).equals("qa_settlement"),"stable issued identifier retained");
        System.out.println("PASS settlement source protection: "+checks+" checks");
    }
    private static OwnerRepository repository(JSONObject master,JSONObject ledger) {
        return (OwnerRepository)Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(),new Class<?>[]{OwnerRepository.class},(proxy,method,args)-> {
            if(method.getName().equals("effective"))return master;
            if(method.getName().equals("ownerValue"))return args[0].equals("personal_paid_migrated")?"1":args[0].equals("personal_paid")?"[]":args[0].equals("settlement:v1")?ledger.toString():args[1];
            throw new AssertionError("guard failed before storage mutation: "+method.getName());
        });
    }
}
