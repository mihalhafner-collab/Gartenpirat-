package at.hausgarten.service;

import org.json.*;
import java.math.BigDecimal;

/** Corrupt synthetic financial states must fail semantic backup validation without mutation. */
public final class LocalFinancialValidationTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Financial validation "+checks);}
    interface Change{void apply(JSONObject state,JSONObject namespace)throws Exception;}
    static JSONObject line(String title,String quantity,int price)throws Exception{return new JSONObject().put("title",title).put("quantity",new BigDecimal(quantity)).put("price",price).put("tax_rate",0).put("unit","h");}
    static JSONObject[] fixture()throws Exception{
        JSONObject state=new JSONObject().put("invoices",new JSONArray()).put("worklogs",new JSONArray().put(new JSONObject().put("id","qa_w").put("hours",new BigDecimal("2.5"))));
        JSONObject invoice=new JSONObject().put("id","qa_i").put("invoice_number","QA-2027-0001").put("invoice_date","2027-05-18").put("service_start","2027-05-18").put("service_end","2027-05-18").put("issued_at","2027-05-18T18:00:00Z").put("status","Gestellt").put("local_issue_request","qa_issue_request").put("pdf_state","PDF_PENDING").put("document_job_id","invoice:qa_i").put("tax_mode","small_business").put("issuer",new JSONObject().put("owner_name","QA Betrieb").put("business_street","QA Weg 1").put("business_postal_code","1000").put("business_city","Wien")).put("recipient",new JSONObject().put("name","QA Kunde").put("street","QA Weg 2").put("postal_code","1000").put("city","Wien")).put("positions",new JSONArray().put(line("QA Arbeit","2.5",52).put("worklog_id","qa_w")).put(line("QA Sack","2",9).put("unit","Sack").put("source_type","material_movement").put("source_id","qa_movement").put("source_quantity_decimal","2"))).put("net_total",148).put("tax_total",0).put("gross_total",148).put("amount",148).put("local_source_allocations",new JSONObject().put("qa_w","2.5")).put("local_material_source_allocations",new JSONObject().put("qa_movement","2")).put("numbering_receipt",new JSONObject().put("authority",LocalAuthorityRules.ENGINE).put("invoice_id","qa_i").put("invoice_number","QA-2027-0001").put("counter",1).put("request_id","qa_issue_request"));
        invoice.put("money_minor",LocalAuthorityRules.exactTotals(invoice));state.getJSONArray("invoices").put(invoice);String sha=PixelMcpWriteTools.hash(invoice);
        JSONObject namespaces=new JSONObject().put("local_invoice_sources:qa_i",invoice.getJSONObject("local_source_allocations")).put("local_invoice_material_sources:qa_i",invoice.getJSONObject("local_material_source_allocations")).put("revier:v1:entity:material_movement:qa_movement",new JSONObject().put("id","qa_movement").put("movement_kind","consume").put("quantity_decimal","-2").put("unit","Sack")).put("local_invoice_job:qa_i",new JSONObject().put("formatVersion",1).put("invoice_id","qa_i").put("phase","PDF_PENDING").put("attempts",0).put("snapshot",new JSONObject(invoice.toString())).put("snapshot_sha256",sha)).put("local_number_reservation:qa_issue_request",new JSONObject().put("formatVersion",1).put("request_id","qa_issue_request").put("invoice_id","qa_i").put("invoice_number","QA-2027-0001").put("counter",1).put("snapshot_sha256",sha)).put("local_invoice_counter",new JSONObject().put("last_number",1));
        return new JSONObject[]{state,namespaces};
    }
    static JSONObject payment(String id,long amount)throws Exception{return new JSONObject().put("formatVersion",1).put("payment_id",id).put("request_id","qa_request_"+id).put("amount_minor",amount).put("unallocated_minor",0).put("paid_on","2027-05-18").put("method","bank").put("recorded_at","2027-05-18T18:00:00Z").put("allocations",new JSONArray().put(new JSONObject().put("invoice_id","qa_i").put("amount_minor",amount)));}
    static JSONObject evidence(int last)throws Exception{return new JSONObject().put("formatVersion",1).put("external_writers_disabled",true).put("evidence_sha256","a".repeat(64)).put("evidence_reference","qa_original_ledger").put("checked_at","2027-05-18T18:00:00Z").put("last_number",last).put("issued",new JSONArray()).put("reservations",new JSONArray());}
    static void denied(String code,Change change)throws Exception{JSONObject[] fixture=fixture();change.apply(fixture[0],fixture[1]);String before=PixelMcpWriteTools.hash(new JSONArray().put(fixture[0]).put(fixture[1]));try{LocalFinancialValidation.inspect(fixture[0],fixture[1]);throw new AssertionError("Expected "+code);}catch(java.io.IOException expected){yes(expected.getMessage().equals("FINANCIAL_"+code));}yes(before.equals(PixelMcpWriteTools.hash(new JSONArray().put(fixture[0]).put(fixture[1]))));}
    public static void main(String[] ignored)throws Exception{
        SyntheticBusinessData.verify();JSONObject[] fixture=fixture();String before=PixelMcpWriteTools.hash(new JSONArray().put(fixture[0]).put(fixture[1]));JSONObject result=LocalFinancialValidation.inspect(fixture[0],fixture[1]);yes(result.getString("status").equals("PASS")&&!result.getBoolean("original_files_verified")&&!result.getBoolean("data_changed"));yes(before.equals(PixelMcpWriteTools.hash(new JSONArray().put(fixture[0]).put(fixture[1]))));
        fixture[1].put("local_payment:qa_pay_60",payment("qa_pay_60",6000)).put("local_payment:qa_pay_88",payment("qa_pay_88",8800));yes(LocalFinancialValidation.inspect(fixture[0],fixture[1]).getJSONObject("counts").getInt("payments")==2);
        JSONObject reversal=new JSONObject().put("formatVersion",1).put("payment_id","qa_pay_fixed_reversal").put("amount_minor",-6000).put("recorded_at","2027-05-18T19:00:00Z").put("reversal",true).put("reverses","qa_pay_60").put("reason","QA korrigiert").put("allocations",new JSONArray().put(new JSONObject().put("invoice_id","qa_i").put("amount_minor",-6000)));fixture[1].put("local_payment:qa_pay_fixed_reversal",reversal).put("local_payment:qa_pay_fixed",payment("qa_pay_fixed",6000).put("corrects","qa_pay_60").put("reason","QA korrigiert"));yes(LocalFinancialValidation.inspect(fixture[0],fixture[1]).getJSONObject("counts").getInt("payments")==4);
        yes(LocalFinancialValidation.inspect(new JSONObject().put("invoices",new JSONArray()).put("worklogs",new JSONArray()),new JSONObject()).getString("status").equals("PASS"));
        denied("DUPLICATE_ID",(s,n)->s.getJSONArray("invoices").put(new JSONObject(s.getJSONArray("invoices").getJSONObject(0).toString())));
        denied("JOB_MISSING",(s,n)->n.remove("local_invoice_job:qa_i"));
        denied("JOB_SNAPSHOT_MISMATCH",(s,n)->n.getJSONObject("local_invoice_job:qa_i").getJSONObject("snapshot").put("invoice_number","QA-wrong"));
        denied("SOURCE_SNAPSHOT_MISMATCH",(s,n)->n.put("local_invoice_sources:qa_i",new JSONObject().put("qa_w","2")));
        denied("SOURCE_QUANTITY_EXCEEDED",(s,n)->s.getJSONArray("worklogs").getJSONObject(0).put("hours",2));
        denied("REFERENCE_MISSING",(s,n)->n.remove("revier:v1:entity:material_movement:qa_movement"));
        denied("MATERIAL_REFERENCE_INVALID",(s,n)->n.getJSONObject("revier:v1:entity:material_movement:qa_movement").put("movement_kind","receive"));
        denied("MATERIAL_REFERENCE_INVALID",(s,n)->n.put("revier:v1:reversed:qa_movement","qa_reversal"));
        denied("COUNTER_INVALID",(s,n)->n.getJSONObject("local_invoice_counter").put("last_number",0));
        denied("COUNTER_MISSING",(s,n)->n.remove("local_invoice_counter"));
        denied("RESERVATION_MISSING",(s,n)->n.remove("local_number_reservation:qa_issue_request"));
        denied("RESERVATION_SNAPSHOT_MISMATCH",(s,n)->n.getJSONObject("local_number_reservation:qa_issue_request").put("snapshot_sha256","a".repeat(64)));
        denied("PAYMENT_SUM_MISMATCH",(s,n)->n.put("local_payment:qa_bad",payment("qa_bad",6000).put("unallocated_minor",1)));
        denied("AMOUNT_INVALID",(s,n)->{JSONObject payment=payment("qa_bad",6000);payment.getJSONArray("allocations").getJSONObject(0).put("amount_minor",6000.5);n.put("local_payment:qa_bad",payment);});
        denied("REFERENCE_MISSING",(s,n)->{JSONObject payment=payment("qa_bad",6000);payment.getJSONArray("allocations").getJSONObject(0).put("invoice_id","qa_missing");n.put("local_payment:qa_bad",payment);});
        denied("PAYMENT_CORRECTION_INVALID",(s,n)->n.put("local_payment:qa_bad",payment("qa_bad",6000).put("corrects","qa_missing").put("reason","QA Begründung")));
        denied("PAYMENT_DUPLICATE_REQUEST",(s,n)->n.put("local_payment:qa_once",payment("qa_once",6000).put("request_id","qa_duplicate_request")).put("local_payment:qa_twice",payment("qa_twice",6000).put("request_id","qa_duplicate_request")));
        denied("OPENING_BALANCE_INVALID",(s,n)->n.put("invoice_opening_balance:qa_i",new JSONObject().put("remaining_minor",-1)));
        denied("COUNTER_INVALID",(s,n)->n.put("local_invoice_external_evidence",evidence(5)));
        denied("NUMBER_EVIDENCE_INVALID",(s,n)->n.put("local_invoice_external_evidence",evidence(1).put("reservations",new JSONArray().put(new JSONObject().put("reservation_id","qa_external_reservation").put("invoice_id","qa_external_invoice").put("invoice_number","QA-2027-0002").put("counter",2).put("state","reserved")))));
        System.out.println("PASS "+checks+" captured financial semantic checks: valid pending PDF, exact partial payments/correction, corrupted IDs/quantities/jobs/reservations/counter rejected without writes; no original-file claim");
    }
}
