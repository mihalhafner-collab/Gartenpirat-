package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Exact additive schemas, shared routing ports, clear semantics and stale-context boundary. */
public final class LocalWorkflowContractsTests {
    private static int checks;
    private static void yes(boolean value){checks++;if(!value)throw new AssertionError("Local workflow contract "+checks);}
    private static void denied(String name,JSONObject args)throws Exception {try{PixelMcpOwnerTools.validate(name,args);}catch(PixelMcpWriteTools.Rejected expected){yes(expected.code.equals("INVALID_INPUT"));return;}throw new AssertionError("Expected invalid local schema "+name);}
    private static JSONObject request()throws Exception{return new JSONObject().put("request_id","qa_local_contract_request");}
    public static void main(String[] unused)throws Exception {
        SyntheticBusinessData.verify();yes(LocalWorkflowTools.READS.length==18&&LocalWorkflowTools.WRITES.length==25);
        Set<String> names=new HashSet<>();for(String name:LocalWorkflowTools.READS){yes(names.add(name)&&PixelMcpOwnerTools.read(name)&&!PixelMcpOwnerTools.write(name));denied(name,new JSONObject().put("qa_unknown_input",true));}
        for(String name:LocalWorkflowTools.WRITES){yes(names.add(name)&&PixelMcpOwnerTools.write(name)&&!PixelMcpOwnerTools.read(name));JSONArray required=PixelMcpOwnerTools.schema(name).getJSONArray("required");yes(required.toList().contains("request_id"));denied(name,new JSONObject());}
        yes(names.size()==43);
        JSONObject create=request().put("customer_id","qa_c").put("name","QA Objekt");PixelMcpOwnerTools.validate("create_property",create);checks++;
        denied("create_property",new JSONObject(create.toString()).put("area_decimal",1.25));
        PixelMcpOwnerTools.validate("create_property",new JSONObject(create.toString()).put("area_decimal","1.25"));checks++;
        JSONObject update=request().put("property_id","qa_property").put("expected_record_hash","a".repeat(64)).put("updates",new JSONObject().put("area_decimal","").put("notes",""));PixelMcpOwnerTools.validate("update_property",update);checks++;
        JSONObject inquiry=request().put("inquiry_id","qa_inquiry").put("expected_record_hash","b".repeat(64)).put("updates",new JSONObject().put("property_id","").put("wish_from","").put("wish_until","").put("measurements",new JSONArray().put(new JSONObject().put("name","QA Breite").put("value_decimal","2.5").put("unit","m"))).put("photo_ids",new JSONArray().put("qa_photo")));PixelMcpOwnerTools.validate("update_inquiry",inquiry);checks++;
        JSONObject visit=request().put("visit_id","qa_visit").put("expected_record_hash","c".repeat(64)).put("scope","only_this").put("reason","QA explicit change").put("updates",new JSONObject().put("window_start","").put("window_end","").put("remaining_minutes",15));PixelMcpOwnerTools.validate("update_visit",visit);checks++;
        JSONObject material=request().put("name","QA Sack").put("unit","Sack").put("purchase_price_minor",JSONObject.NULL).put("billing_price_minor",900);PixelMcpOwnerTools.validate("save_material",material);checks++;
        denied("save_material",new JSONObject(material.toString()).put("billing_price_minor",9.25));
        denied("save_material",new JSONObject(material.toString()).put("billing_price_minor",-1));
        JSONObject movement=request().put("material_id","qa_material").put("kind","consume").put("quantity_decimal","2").put("reason","QA Verbrauch").put("expected_record_hash","d".repeat(64));PixelMcpOwnerTools.validate("record_material_movement",movement);checks++;
        denied("record_material_movement",new JSONObject(movement.toString()).put("quantity_decimal",2.0));
        denied("record_material_movement",new JSONObject(movement.toString()).put("quantity_decimal","1e6"));
        JSONObject legacyStart=new JSONObject().put("request_id","qa_start_old_context").put("customer_id","qa_c");McpSchema.validate(legacyStart,PixelMcpSessionTools.schema("start_work"));checks++;
        McpSchema.validate(new JSONObject(legacyStart.toString()).put("property_id","qa_property").put("visit_id","qa_visit"),PixelMcpSessionTools.schema("start_work"));checks++;
        denied("retry_full_backup",request().put("backup_id","qa_backup").put("expected_record_hash","stale"));
        denied("request_full_backup",request().put("expected_settings_hash","e".repeat(64)).put("recovery_code","qa_must_never_be_accepted"));
        PixelMcpOwnerTools.validate("request_full_backup",request().put("expected_settings_hash","e".repeat(64)));checks++;
        JSONObject evidence=new JSONObject().put("formatVersion",1).put("external_writers_disabled",true).put("evidence_sha256","f".repeat(64)).put("evidence_reference","QA external original ledger").put("checked_at","2027-05-18T18:00:00Z");JSONObject activation=request().put("backup_id","qa_verified_backup").put("expected_preview_hash","a".repeat(64)).put("writer_evidence",evidence);PixelMcpOwnerTools.validate("activate_local_authority",activation);checks++;
        denied("activate_local_authority",new JSONObject(activation.toString()).put("backup_proof",new JSONObject().put("valid",true)));
        JSONObject deniedWriters=new JSONObject(activation.toString());deniedWriters.getJSONObject("writer_evidence").put("external_writers_disabled",false);denied("activate_local_authority",deniedWriters);
        JSONObject currentReview=new JSONObject().put("confirmed",true).put("installation_id","qa_installation").put("restore_epoch","qa_restore_epoch").put("reviewed_at","2027-05-18T18:00:00Z").put("evidence_sha256","a".repeat(64));JSONObject ledgerProof=new JSONObject(evidence.toString()).put("last_number",0).put("issued",new JSONArray()).put("reservations",new JSONArray()).put("current_review",currentReview);JSONObject reconcile=request().put("expected_preview_hash","b".repeat(64)).put("ledger",ledgerProof);PixelMcpOwnerTools.validate("reconcile_local_invoice_authority",reconcile);checks++;
        JSONObject oldLedger=new JSONObject(reconcile.toString());oldLedger.getJSONObject("ledger").remove("current_review");denied("reconcile_local_invoice_authority",oldLedger);JSONObject unconfirmed=new JSONObject(reconcile.toString());unconfirmed.getJSONObject("ledger").getJSONObject("current_review").put("confirmed",false);denied("reconcile_local_invoice_authority",unconfirmed);
        JSONObject recovery=new JSONObject(reconcile.toString()),unproduced=new JSONObject().put("invoice_id","qa_invoice").put("invoice_number","QA-1").put("pdf_resolution","not_created").put("snapshot_sha256","c".repeat(64)).put("not_created_confirmed",true).put("not_created_reason","QA controlled missing-original review");recovery.getJSONObject("ledger").put("issued",new JSONArray().put(unproduced));PixelMcpOwnerTools.validate("reconcile_local_invoice_authority",recovery);checks++;
        JSONObject notConfirmed=new JSONObject(recovery.toString());notConfirmed.getJSONObject("ledger").getJSONArray("issued").getJSONObject(0).put("not_created_confirmed",false);denied("reconcile_local_invoice_authority",notConfirmed);JSONObject malformedSnapshot=new JSONObject(recovery.toString());malformedSnapshot.getJSONObject("ledger").getJSONArray("issued").getJSONObject(0).put("snapshot_sha256","qa_not_a_hash");denied("reconcile_local_invoice_authority",malformedSnapshot);
        JSONObject legacyIssue=request().put("invoice_id","qa_invoice").put("expected_record_hash","a".repeat(64)).put("confirmed",true);PixelMcpOwnerTools.validate("request_invoice_issue",legacyIssue);checks++;PixelMcpOwnerTools.validate("request_invoice_issue",new JSONObject(legacyIssue.toString()).put("expected_context_hash","b".repeat(64)));checks++;denied("request_invoice_issue",new JSONObject(legacyIssue.toString()).put("expected_context_hash","stale"));
        JSONObject inspection=request().put("inquiry_id","qa_inquiry").put("expected_record_hash","c".repeat(64)).put("starts_at","2027-05-18T08:00:00Z").put("ends_at","2027-05-18T09:00:00Z").put("notes","Q".repeat(4001));denied("plan_inquiry_inspection",inspection);
        JSONObject payment=request().put("amount_minor",6000).put("paid_on","2027-05-18").put("method","bank").put("expected_ledger_hash","b".repeat(64)).put("allocations",new JSONArray().put(new JSONObject().put("invoice_id","qa_invoice").put("amount_minor",6000).put("expected_record_hash","c".repeat(64))));PixelMcpOwnerTools.validate("record_payment",payment);checks++;
        denied("record_payment",new JSONObject(payment.toString()).put("amount_minor",60.5));denied("record_payment",new JSONObject(payment.toString()).put("paid",true));
        JSONObject correction=new JSONObject(payment.toString()).put("payment_id","qa_payment").put("reason","QA explicit correction");PixelMcpOwnerTools.validate("correct_payment",correction);checks++;
        JSONObject zeroCorrection=new JSONObject(correction.toString()).put("amount_minor",0).put("allocations",new JSONArray());PixelMcpOwnerTools.validate("correct_payment",zeroCorrection);checks++;
        denied("record_payment",new JSONObject(payment.toString()).put("amount_minor",0).put("allocations",new JSONArray()));denied("correct_payment",new JSONObject(zeroCorrection.toString()).put("amount_minor",-1));
        correction.remove("reason");denied("correct_payment",correction);
        OwnerApplicationService app=new OwnerApplicationService(new OwnerApplicationTests.MemoryRepository());for(String name:LocalWorkflowTools.WRITES)yes(app.handles(name));
        int[] ports={0,0,0};OwnerRepository routed=(OwnerRepository)java.lang.reflect.Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(),new Class<?>[]{OwnerRepository.class},(object,method,values)->{
            int index=method.getName().equals("fullBackupAction")?0:method.getName().equals("revierAction")?1:method.getName().equals("localInvoiceAction")?2:-1;if(index<0)throw new AssertionError("Unexpected local effect port "+method.getName());ports[index]++;return new JSONObject().put("operation",values[0]).put("request_id",((JSONObject)values[1]).getString("request_id"));
        });
        OwnerApplicationService dispatch=new OwnerApplicationService(routed);for(String name:LocalWorkflowTools.WRITES){if(LocalWorkflowTools.offerDecision(name))continue;JSONObject result=dispatch.perform(name,request(),"qa_routed_request");yes(result.getString("operation").equals(name)&&result.getString("request_id").equals("qa_local_contract_request"));}yes(ports[0]==2&&ports[1]==16&&ports[2]==5);
        System.out.println("PASS "+checks+" additive local schemas/shared dispatch/decimal and explicit-clear/secret-field rejection checks;native persistence tested separately");
    }
}
