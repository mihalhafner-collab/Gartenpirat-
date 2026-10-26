package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Captured-store boundary checks use the real decision service and the same restore validator. */
public final class OfferDecisionBoundaryTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Offer decision boundary check "+checks);}
    interface Task {void run()throws Exception;}
    static void denied(String code,Task task)throws Exception {try{task.run();}catch(PixelMcpWriteTools.Rejected expected){yes(code.equals(expected.code));return;}throw new AssertionError("Expected "+code);}
    static RevierDomainTests.Memory captured(OfferDecisionTests.Fixture fixture)throws Exception {RevierDomainTests.Memory copy=new RevierDomainTests.Memory();copy.snapshot=fixture.repository.effective();for(Map.Entry<String,String> row:fixture.base.preferences.entrySet())if(row.getKey().startsWith(OfferDecisionRules.PREFIX))copy.values.put(row.getKey(),row.getValue());return copy;}
    static JSONObject check(RevierDomainTests.Memory store)throws Exception {return new RevierApplicationService(store,OfferDecisionTests.CLOCK).validateRelationships();}
    public static void main(String[] ignored)throws Exception {
        SyntheticBusinessData.verify();OfferDecisionTests.Fixture f=new OfferDecisionTests.Fixture(true);JSONObject offer=f.finalized("boundary");String id=offer.getString("id"),request="qa_offer_boundary_accept";JSONObject acceptance=OfferDecisionTests.args(request).put("offer_id",id).put("expected_record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(offer))).put("accepted_at","2026-10-19T09:00:00Z").put("evidence","QA dokumentierte Annahme");new OfferDecisionService(f.repository,OfferDecisionTests.CLOCK).perform("record_offer_acceptance",acceptance,request);String revision=new OfferApplicationService(f.repository).perform("duplicate_offer",new JSONObject().put("offer_id",id),"qa_boundary_revision").getString("offer_id");
        RevierDomainTests.Memory baseline=captured(f);JSONObject result=check(baseline).getJSONObject("offer_decisions");yes(result.getInt("decisions")==1&&result.getInt("histories")==1&&result.getInt("revisions")==1&&result.getInt("receipts")==1);String original=PixelMcpWriteTools.hash(new JSONObject(baseline.values));check(baseline);yes(original.equals(PixelMcpWriteTools.hash(new JSONObject(baseline.values))));denied("REVIER_REFERENCE_PROTECTED",()->new RevierApplicationService(baseline).protectParent("offer",id));denied("REVIER_REFERENCE_PROTECTED",()->new RevierApplicationService(baseline).protectParent("offer",revision));
        String current=OfferDecisionRules.PREFIX+"current:"+id,history=OfferDecisionRules.PREFIX+"history:"+id,receipt=OfferDecisionRules.PREFIX+"request:"+PixelMcpWriteTools.hash(request),link=OfferDecisionRules.PREFIX+"revision:"+revision;
        RevierDomainTests.Memory bad=captured(f);bad.values.remove(history);RevierDomainTests.Memory missingHistory=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(missingHistory));
        bad=captured(f);bad.values.put(history,"[]");RevierDomainTests.Memory emptyHistory=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(emptyHistory));
        bad=captured(f);bad.values.remove(receipt);RevierDomainTests.Memory missingReceipt=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(missingReceipt));
        bad=captured(f);JSONObject response=new JSONObject(bad.values.get(receipt));response.getJSONObject("result").put("offer_id","qa_other_offer");bad.values.put(receipt,response.toString());RevierDomainTests.Memory wrongReceipt=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(wrongReceipt));
        bad=captured(f);bad.values.put(current,new JSONObject(bad.values.get(current)).put("pdf_sha256","d".repeat(64)).toString());RevierDomainTests.Memory wrongPdf=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(wrongPdf));
        bad=captured(f);bad.values.put(current,new JSONObject(bad.values.get(current)).put("formatVersion",2).toString());RevierDomainTests.Memory future=bad;denied("OFFER_DECISION_VERSION_UNSUPPORTED",()->check(future));
        bad=captured(f);bad.values.put(link,new JSONObject(bad.values.get(link)).put("parent_offer_id","qa_missing_offer").toString());RevierDomainTests.Memory missingParent=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(missingParent));
        bad=captured(f);bad.values.put(link,new JSONObject(bad.values.get(link)).put("parent_offer_id",revision).toString());RevierDomainTests.Memory cycle=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(cycle));
        bad=captured(f);bad.values.put(OfferDecisionRules.PREFIX+"future:qa_unknown","{}");RevierDomainTests.Memory unknown=bad;denied("OFFER_DECISION_RELATION_INVALID",()->check(unknown));
        check(baseline);yes(original.equals(PixelMcpWriteTools.hash(new JSONObject(baseline.values))));System.out.println("PASS "+checks+" offer decision captured-store/history/receipt/PDF/version/revision/corruption checks;synthetic port,no device restore proof");
    }
}
