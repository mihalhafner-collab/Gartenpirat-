package at.hausgarten.service;

import org.json.*;
import java.math.BigDecimal;

/** New local integer money and the previously rejected explicit subcontractor recipient. */
public final class LocalMoneyCompatibilityTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Local money check "+checks);}
    interface Task {void run()throws Exception;}
    static void denied(Task task)throws Exception {try{task.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("Expected wrong recipient refusal");}
    static JSONObject snapshot(boolean local)throws Exception {JSONObject state=SyntheticBusinessData.profile("minimal");state.getJSONObject("business_settings").put("tax_mode","standard");if(local)state.put("processing_engine",LocalAuthorityRules.ENGINE);return state;}
    static JSONArray lines()throws Exception {return new JSONArray().put(new JSONObject().put("title","QA Zeit").put("quantity",new BigDecimal("2.5")).put("price",52).put("tax_rate",0).put("unit","h").put("price_source","user_confirmed")).put(new JSONObject().put("title","QA Material").put("quantity",2).put("price",9).put("tax_rate",0).put("unit","Sack").put("price_source","user_confirmed"));}
    static JSONObject invoice(JSONArray positions)throws Exception {for(int i=0;i<positions.length();i++)positions.getJSONObject(i).remove("price_source");return new JSONObject().put("id","qa_money_invoice").put("customer_id","qa_customer_1").put("status","Entwurf").put("invoice_date","2027-05-18").put("positions",positions);}
    static void totals()throws Exception {
        JSONObject legacy=snapshot(false),local=snapshot(true);String before=PixelMcpWriteTools.hash(legacy);JSONObject a=invoice(lines()),b=invoice(lines());InvoiceDraftRules.normalize(a,null,legacy);InvoiceDraftRules.normalize(b,null,local);yes(!a.has("money_minor")&&b.getJSONObject("money_minor").getLong("gross_minor")==14800);yes(a.getDouble("amount")==148&&b.getDouble("amount")==148);yes(PixelMcpWriteTools.hash(legacy).equals(before));yes(b.getJSONObject("money_minor").getJSONArray("positions").getJSONObject(0).getString("quantity_decimal").equals("2.5"));
        JSONObject oldOffer=OffersAndPhotosTests.draft().put("customer_id","qa_customer_1").put("positions",lines()),newOffer=new JSONObject(oldOffer.toString());OfferRules.normalize(oldOffer,null,legacy);OfferRules.normalize(newOffer,null,local);yes(!oldOffer.has("money_minor")&&newOffer.getJSONObject("money_minor").getLong("gross_minor")==14800);newOffer.getJSONArray("positions").getJSONObject(0).put("price",JSONObject.NULL);OfferRules.normalize(newOffer,newOffer,local);yes(newOffer.isNull("gross_total")&&!newOffer.has("money_minor"));
        JSONArray fractional=new JSONArray().put(new JSONObject().put("title","QA Bruchteile").put("quantity",3).put("price",new BigDecimal("0.335")).put("unit","Stk.").put("tax_rate",0));JSONObject precise=invoice(fractional);InvoiceDraftRules.normalize(precise,null,local);yes(precise.getDouble("amount")==1.01&&precise.getJSONObject("money_minor").getLong("gross_minor")==101);yes(precise.getJSONObject("money_minor").getJSONArray("positions").getJSONObject(0).getString("quantity_decimal").equals("3"));
    }
    static void recipients()throws Exception {
        for(boolean local:new boolean[]{false,true}){
            JSONObject state=snapshot(local);JSONObject payer=new JSONObject(state.getJSONArray("customers").getJSONObject(0).toString()).put("id","qa_general_contractor").put("name","QA Abrechnungskunde");state.getJSONArray("customers").put(payer);JSONObject order=state.getJSONArray("orders").getJSONObject(0);order.put("billing_customer_id","qa_general_contractor");JSONObject log=state.getJSONArray("worklogs").getJSONObject(0);log.put("billing_customer_id","qa_general_contractor");
            JSONObject correct=invoice(new JSONArray().put(new JSONObject().put("title","QA Pflege").put("quantity",2).put("price",50).put("worklog_id",log.getString("id")))).put("customer_id","qa_general_contractor").put("order_id",order.getString("id"));InvoiceDraftRules.normalize(correct,null,state);yes(correct.getString("customer_id").equals("qa_general_contractor")&&log.getString("customer_id").equals("qa_customer_1"));
            JSONObject wrong=invoice(lines()).put("order_id",order.getString("id"));denied(()->InvoiceDraftRules.normalize(wrong,null,state));
            order.remove("billing_customer_id");JSONObject direct=invoice(lines()).put("order_id",order.getString("id"));InvoiceDraftRules.normalize(direct,null,state);yes(direct.getString("customer_id").equals("qa_customer_1"));
        }
    }
    public static void main(String[] ignored)throws Exception {SyntheticBusinessData.verify();totals();recipients();System.out.println("PASS "+checks+" local minor-unit/decimal quantity/rounding/missing-price/legacy/Subcontractor recipient checks;synthetic only");}
}
