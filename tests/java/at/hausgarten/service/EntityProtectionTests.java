package at.hausgarten.service;
import org.json.*;

public final class EntityProtectionTests {
    interface Test { void run() throws Exception; }
    static int passed;
    static void check(String title,Test task)throws Exception{task.run();passed++;System.out.println("PASS "+title);}
    static void rejects(Test task)throws Exception{try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected validation rejection");}
    public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
        JSONArray customers=new JSONArray("[{\"id\":\"qa_c1\",\"name\":\"Georg Seidl\",\"active\":0,\"email\":\"a@example.invalid\",\"phone\":\"+43 123 456\",\"sevdesk_contact_id\":\"s1\"},{\"id\":\"qa_c2\",\"name\":\"Other\"}]");
        JSONArray orders=new JSONArray("[{\"id\":\"qa_o1\",\"customer_id\":\"qa_c1\"},{\"id\":\"qa_o2\",\"customer_id\":\"qa_c1\"}]");
        JSONArray works=new JSONArray("[{\"id\":\"qa_w1\",\"customer_id\":\"qa_c1\",\"order_id\":\"qa_o1\"},{\"id\":\"qa_w2\",\"customer_id\":\"qa_c2\"}]");
        JSONObject photo=SyntheticBusinessData.object().put("customer_id","qa_c1").put("order_id","qa_o1").put("worklog_id","qa_w1");
        check("valid photo relations",()->EntityProtectionRules.validatePhoto(photo,customers,orders,works));
        check("unknown photo customer",()->rejects(()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object(photo.toString()).put("customer_id","qa_missing"),customers,orders,works)));
        check("unknown photo order",()->rejects(()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object(photo.toString()).put("order_id","qa_missing"),customers,orders,works)));
        check("photo customer order mismatch",()->rejects(()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object(photo.toString()).put("customer_id","qa_c2"),customers,orders,works)));
        check("unknown photo worklog",()->rejects(()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object(photo.toString()).put("worklog_id","qa_missing"),customers,orders,works)));
        check("photo customer worklog mismatch",()->rejects(()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object(photo.toString()).put("worklog_id","qa_w2"),customers,orders,works)));
        check("photo order worklog mismatch",()->rejects(()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object(photo.toString()).put("order_id","qa_o2"),customers,orders,works)));
        check("optional photo relations and JSON null",()->EntityProtectionRules.validatePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("order_id",JSONObject.NULL),customers,orders,works));
        JSONObject previous=SyntheticBusinessData.object().put("id","qa_o1").put("customer_id","qa_c1"),changed=SyntheticBusinessData.object(previous.toString()).put("customer_id","qa_c2");
        check("linked order reassignment rejects work",()->rejects(()->EntityProtectionRules.validateOrderChange(changed,previous,works,new JSONArray(),new JSONArray())));
        check("linked order reassignment rejects invoice",()->rejects(()->EntityProtectionRules.validateOrderChange(changed,previous,new JSONArray(),new JSONArray("[{\"order_id\":\"qa_o1\"}]"),new JSONArray())));
        check("linked order reassignment rejects photo",()->rejects(()->EntityProtectionRules.validateOrderChange(changed,previous,new JSONArray(),new JSONArray(),new JSONArray("[{\"order_id\":\"qa_o1\"}]"))));
        check("unchanged owner and unlinked reassignment allowed",()->{EntityProtectionRules.validateOrderChange(previous,previous,works,null,null);EntityProtectionRules.validateOrderChange(changed,previous,new JSONArray(),null,null);});
        check("duplicate name whitespace inactive",()->rejects(()->EntityProtectionRules.validateNewCustomer(SyntheticBusinessData.object().put("name"," GEORG   SEIDL "),customers)));
        check("duplicate email",()->rejects(()->EntityProtectionRules.validateNewCustomer(SyntheticBusinessData.object().put("name","New name").put("email","A@EXAMPLE.INVALID"),customers)));
        check("duplicate phone formatting",()->rejects(()->EntityProtectionRules.validateNewCustomer(SyntheticBusinessData.object().put("name","New name").put("phone","43123456"),customers)));
        check("duplicate sevDesk reference",()->rejects(()->EntityProtectionRules.validateNewCustomer(SyntheticBusinessData.object().put("name","New name").put("sevdesk_contact_id","s1"),customers)));
        check("duplicate override explicit and unchanged IDs",()->{String before=customers.toString();EntityProtectionRules.validateNewCustomer(SyntheticBusinessData.object().put("name","Georg Seidl").put("allow_duplicate",true),customers);if(!before.equals(customers.toString()))throw new AssertionError();});
        check("new unique customer allowed",()->EntityProtectionRules.validateNewCustomer(SyntheticBusinessData.object().put("name","Unique customer"),customers));
        check("local draft delete allowed",()->EntityProtectionRules.validateInvoiceDelete(SyntheticBusinessData.object().put("status","Entwurf")));
        check("cancelled unissued local draft delete allowed",()->EntityProtectionRules.validateInvoiceDelete(SyntheticBusinessData.object().put("status","Storniert")));
        check("issued invoice delete blocked",()->rejects(()->EntityProtectionRules.validateInvoiceDelete(SyntheticBusinessData.object().put("status","Gestellt"))));
        check("issued date protects apparently draft invoice",()->rejects(()->EntityProtectionRules.validateInvoiceDelete(SyntheticBusinessData.object().put("status","Entwurf").put("issued_at","2026-10-12"))));
        check("external invoice remains protected after cancellation",()->rejects(()->EntityProtectionRules.validateInvoiceDelete(SyntheticBusinessData.object().put("status","Storniert").put("sevdesk_id","external"))));
        check("missing or unknown invoice state cannot be deleted",()->{rejects(()->EntityProtectionRules.validateInvoiceDelete(null));rejects(()->EntityProtectionRules.validateInvoiceDelete(SyntheticBusinessData.object().put("status","Unknown")));});
        System.out.println("RESULT "+passed+" entity protection checks passed; no Android storage simulated");
    }
}
