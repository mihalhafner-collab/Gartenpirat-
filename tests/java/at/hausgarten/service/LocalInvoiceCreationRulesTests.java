package at.hausgarten.service;

import org.json.*;

/** Actual document creation guard across process restart, restore, remigration and current proof. */
public final class LocalInvoiceCreationRulesTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("PDF creation guard "+checks);}
    static JSONObject identity()throws Exception{return new JSONObject().put("installationId","qa_installation").put("restoreEpoch","qa_epoch_current");}
    static JSONObject authority()throws Exception{return new JSONObject().put("mode","LOCAL_ACTIVE").put("invoice_authority","RECONCILED").put("installation_id","qa_installation").put("restore_epoch","qa_epoch_current").put("invoice_evidence_sha256","b".repeat(64)).put("evidence_not_before","2027-05-18T18:00:00Z");}
    static JSONObject job()throws Exception{return new JSONObject().put("snapshot_sha256","a".repeat(64)).put("origin_installation_id","qa_installation").put("origin_restore_epoch","qa_epoch_old");}
    static JSONObject permission()throws Exception{return new JSONObject().put("formatVersion",1).put("installation_id","qa_installation").put("restore_epoch","qa_epoch_current").put("snapshot_sha256","a".repeat(64)).put("ledger_proof_sha256","b".repeat(64)).put("request_id","qa_current_manual_review").put("authorized_at","2027-05-18T18:01:00Z");}
    static void denied(JSONObject job,JSONObject identity,JSONObject authority,boolean pending)throws Exception{String before=new JSONArray().put(job).put(identity).put(authority).toString();try{LocalInvoiceCreationRules.requireAllowed(job,identity,authority,pending);throw new AssertionError("Unproven regenerated PDF allowed");}catch(java.io.IOException expected){yes(expected.getMessage().equals("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED"));}yes(before.equals(new JSONArray().put(job).put(identity).put(authority).toString()));}
    public static void main(String[] ignored)throws Exception{
        SyntheticBusinessData.verify();JSONObject current=job().put("origin_restore_epoch","qa_epoch_current");LocalInvoiceCreationRules.requireAllowed(current,identity(),authority(),false);checks++;
        LocalInvoiceCreationRules.requireAllowed(current,identity(),authority().put("invoice_authority","LOCKED"),false);checks++;
        denied(job(),identity(),authority(),false);denied(job(),identity(),authority().put("mode","RESTORE_REVIEW").put("invoice_authority","LOCKED"),false);
        JSONObject unknown=job();unknown.remove("origin_restore_epoch");unknown.remove("origin_installation_id");denied(unknown,identity(),authority(),false);
        denied(current,identity(),authority(),true);denied(current,identity(),authority().put("mode","MIGRATING"),false);
        JSONObject authorized=job().put("creation_authorization",permission());LocalInvoiceCreationRules.requireAllowed(authorized,identity(),authority(),false);checks++;
        denied(authorized,identity(),authority().put("invoice_authority","LOCKED"),false);denied(authorized,identity(),authority().put("invoice_evidence_sha256","c".repeat(64)),false);
        for(String field:new String[]{"installation_id","restore_epoch","snapshot_sha256","ledger_proof_sha256","request_id","authorized_at"}){JSONObject broken=job().put("creation_authorization",permission());broken.getJSONObject("creation_authorization").remove(field);denied(broken,identity(),authority(),false);}
        denied(job().put("creation_authorization",permission().put("snapshot_sha256","d".repeat(64))),identity(),authority(),false);
        denied(job().put("creation_authorization",permission().put("restore_epoch","qa_epoch_old")),identity(),authority(),false);
        denied(job().put("creation_authorization",permission().put("authorized_at","2027-05-18T17:00:00Z")),identity(),authority(),false);
        denied(job().put("creation_authorization",permission().put("formatVersion",1.9)),identity(),authority(),false);
        denied(authorized,identity().put("restoreEpoch","qa_epoch_after_second_restore"),authority().put("restore_epoch","qa_epoch_after_second_restore"),false);
        System.out.println("PASS "+checks+" original-context/current-proof/restore/remigration/stale-authorization PDF byte-creation checks; no files or writes");
    }
}
