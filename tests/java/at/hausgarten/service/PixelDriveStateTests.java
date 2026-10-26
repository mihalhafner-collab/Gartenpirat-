package at.hausgarten.service;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Runs the operative native rules and transport against isolated synthetic storage. */
public final class PixelDriveStateTests {
    static int count;interface Check {void run()throws Exception;}
    static void yes(boolean b){count++;if(!b)throw new AssertionError("Pixel/Drive check "+count);}
    static void bad(Check c)throws Exception{boolean rejected=false;try{c.run();}catch(Exception expected){rejected=true;}yes(rejected);}
    static JSONObject state()throws Exception{return OffersAndPhotosTests.snapshot();}
    static JSONObject event(String id,String kind,JSONObject p)throws Exception{return OffersAndPhotosTests.event(id,kind,p);}
    static final class Memory implements DrivePrimaryState.Storage {
        final Map<String,byte[]> data=new LinkedHashMap<>();final Map<String,String> names=new LinkedHashMap<>();
        public Map<String,String> commits(){return names;}
        public byte[] read(String id){return data.get(id);}
        void publish(JSONObject s,JSONArray actions,String id,int seq,String parent,String previous)throws Exception{
            byte[] bytes=DrivePublication.stateBytes(s);String sha=LegacyDriveRestFixture.sha256(bytes);data.put("state-"+id,bytes);
            JSONObject m=DrivePublication.manifest(s,actions,"state-"+id,sha).put("publisher_id","pixel-test").put("sequence",seq).put("previous_publication_id",parent).put("previous_state_sha256",previous);
            byte[] manifest=PixelMcpWriteTools.canonical(m).getBytes(StandardCharsets.UTF_8);data.put(id,manifest);names.put(id,"HausGarten-Commit-"+LegacyDriveRestFixture.sha256(manifest)+".json");
        }
    }
    static JSONObject invoice(JSONObject s)throws Exception {
        JSONObject p=SyntheticBusinessData.object("{id:'qa_inv-test',customer_id:'qa_c',invoice_date:'2026-10-18',service_start:'2026-10-17',service_end:'2026-10-18',status:'Entwurf',domestic_service_confirmed:true,positions:[{title:'Pflege',quantity:2,price:50,unit:'h',tax_rate:0,worklog_id:'qa_w'}]}");
        InvoiceDraftRules.normalize(p,null,s);s.getJSONArray("invoices").put(p);return p;
    }
    static JSONObject financialState()throws Exception {
        JSONObject s=state();s.getJSONArray("worklogs").put(SyntheticBusinessData.object("{id:'qa_w',customer_id:'qa_c',billing_customer_id:'qa_c',work_date:'2026-10-18',activity:'Pflege',hours:2,billed:0,updated_at:'t1'}"));
        s.put("drive_publisher_id","pixel-test");JSONObject settings=s.getJSONObject("business_settings");settings.put("invoice_numbering_confirmed","1").put("invoice_number_format","own").put("invoice_prefix","HG").put("invoice_numbering_authority","pixel_drive").put("invoice_numbering_device_id","pixel-test").put("invoice_numbering_last_number","80").put("invoice_numbering_checked_at",Instant.now().toString());return s;
    }
    static JSONObject issue(JSONObject s,JSONObject inv,String request)throws Exception {
        return event(request,"pixel_invoice_issue_v1",SyntheticBusinessData.object().put("id",inv.getString("id")).put("confirmed",true).put("_expected_invoice",DriveJournalRules.guard(inv,"invoice"))
            .put("_expected_settings_hash",PixelMcpWriteTools.hash(s.getJSONObject("business_settings"))).put("_expected_recipient_hash",OwnerJournalRules.version(s.getJSONArray("customers").getJSONObject(0)))
            .put("_expected_worklogs",SyntheticBusinessData.object().put("qa_w",DriveJournalRules.guard(s.getJSONArray("worklogs").getJSONObject(0),"worklog"))));
    }
    static final class Counter implements LegacyDriveRestFixture.Wire {
        JSONObject counter;int revision=1,patches;boolean etag=true,race,disconnect,ignoreCompare;
        Counter(JSONObject s)throws Exception{counter=SyntheticBusinessData.object().put("format","hausgarten.numbering.v1").put("config",DriveNumbering.config(s)).put("last_number",80).put("reservations",SyntheticBusinessData.object());}
        public LegacyDriveRestFixture.Reply send(String method,String url,Map<String,String> headers,byte[] body,int max)throws Exception {
            JSONObject result;int code=200;
            if(url.contains("qa_probe_file_test")&&method.equals("PATCH"))return new LegacyDriveRestFixture.Reply(ignoreCompare?200:412,new byte[0],Collections.emptyMap());
            if(url.contains("qa_probe_file_test"))return new LegacyDriveRestFixture.Reply(200,LegacyDriveRestFixture.compareProbeBytes(),etag?Collections.singletonMap("ETag","\"probe-v1\""):Collections.emptyMap());
            if(method.equals("PATCH")){yes(headers.get("If-Match").equals("\"v"+revision+"\""));patches++;if(race){race=false;counter.put("last_number",81);revision++;return new LegacyDriveRestFixture.Reply(412,new byte[0],Collections.emptyMap());}counter=SyntheticBusinessData.object(new String(body,StandardCharsets.UTF_8));revision++;if(disconnect){disconnect=false;throw new java.io.IOException("synthetic lost ack");}result=SyntheticBusinessData.object().put("id","qa_counter_file_test");}
            else if(url.contains("alt=media"))result=counter;
            else result=SyntheticBusinessData.object().put("files",new JSONArray().put(SyntheticBusinessData.object().put("id","qa_counter_file_test").put("name","HausGarten-Nummernkreis-"+PixelMcpWriteTools.hash(counter.getJSONObject("config"))+".json")).put(SyntheticBusinessData.object().put("id","qa_probe_file_test").put("name","HausGarten-CAS-Probe-"+LegacyDriveRestFixture.sha256("folder_test_123".getBytes(StandardCharsets.UTF_8))+".json")));
            return new LegacyDriveRestFixture.Reply(code,result.toString().getBytes(StandardCharsets.UTF_8),etag?Collections.singletonMap("ETag","\"v"+revision+"\""):Collections.emptyMap());
        }
        LegacyDriveRestFixture client(){Map<String,String> checkpoints=new HashMap<>();return new LegacyDriveRestFixture("folder_test_123",()->"synthetic-token",this,new LegacyDriveRestFixture.Checkpoints(){public String get(String k){return checkpoints.get(k);}public void put(String k,String v){checkpoints.put(k,v);}});}
    }
    static void correctionBusinessDates()throws Exception {
        String[][] cases={
            {"2026-10-25T23:30:00Z","2026-10-26"},
            {"2026-01-15T23:30:00Z","2026-01-16"},
            {"2026-01-15T00:30:00Z","2026-01-15"},
            {"2026-07-15T22:30:00Z","2026-07-16"},
            {"2026-07-16T00:30:00+02:00","2026-07-16"},
            {"2026-03-28T23:30:00Z","2026-03-29"},
            {"2026-03-29T01:30:00Z","2026-03-29"},
            {"2026-10-25T00:30:00Z","2026-10-25"},
            {"2026-10-25T01:30:00Z","2026-10-25"}
        };
        for(String type:new String[]{"cancellation","replacement"})for(int i=0;i<cases.length;i++){
            String stamp=cases[i][0];LocalDate businessDate=LocalDate.parse(cases[i][1]);
            JSONObject snapshot=financialState(),original=invoice(snapshot);String serviceDate=businessDate.minusDays(1).toString();
            original.put("invoice_date",serviceDate).put("service_start",serviceDate).put("service_end",serviceDate)
                .put("status","Gestellt").put("invoice_number","QA-2026-0081").put("issued_at",Instant.parse(stamp).minusSeconds(86400).toString());
            snapshot.getJSONArray("worklogs").getJSONObject(0).put("work_date",serviceDate).put("billed",1);
            String originalHash=PixelMcpWriteTools.hash(original),positionsHash=PixelMcpWriteTools.hash(original.getJSONArray("positions"));
            JSONObject action=event("qa_correction_business_date_"+type+"_"+i,"invoice_correction",new JSONObject()
                .put("id",original.getString("id")).put("_expected_invoice",DriveJournalRules.guard(original,"invoice"))
                .put("correction_type",type).put("reason","QA ausdrückliche Korrektur")).put("created_at",stamp);
            JSONArray actions=new JSONArray().put(action);yes(DriveJournalRules.apply(snapshot,actions).length()==0);
            String id="inv_correction_"+PixelMcpWriteTools.hash(action.getString("client_id")).substring(0,32);
            JSONObject correction=PixelMcpWriteTools.find(snapshot.getJSONArray("invoices"),id);
            yes(correction.getString("invoice_date").equals(businessDate.toString()));
            yes(correction.getString("created_at").equals(stamp)&&correction.getString("updated_at").equals(stamp));
            yes(correction.getString("correction_of").equals(original.getString("id"))&&correction.getString("service_start").equals(serviceDate)&&correction.getString("service_end").equals(serviceDate));
            yes(originalHash.equals(PixelMcpWriteTools.hash(PixelMcpWriteTools.find(snapshot.getJSONArray("invoices"),original.getString("id"))))&&positionsHash.equals(PixelMcpWriteTools.hash(original.getJSONArray("positions"))));
            JSONObject issuing=issue(snapshot,correction,"qa_issue_business_date_"+type+"_"+i);issuing.getJSONObject("payload").put("_expected_worklogs",new JSONObject());
            JSONObject prepared=InvoiceIssueRules.prepare(snapshot,issuing,businessDate);
            yes(prepared.getString("invoice_date").equals(businessDate.toString())&&prepared.getDouble("gross_total")==("cancellation".equals(type)?-100:100));
            String savedHash=PixelMcpWriteTools.hash(snapshot);yes(DriveJournalRules.apply(snapshot,actions).length()==0&&savedHash.equals(PixelMcpWriteTools.hash(snapshot))&&snapshot.getJSONArray("invoices").length()==2);
            try{InvoiceIssueRules.prepare(snapshot,issuing,businessDate.plusDays(1));throw new AssertionError("stale correction date accepted");}
            catch(IllegalArgumentException expected){yes(expected.getMessage().equals("Rechnungsdatum und Leistungszeitraum erneut prüfen.")&&savedHash.equals(PixelMcpWriteTools.hash(snapshot)));}
        }
    }
    public static void main(String[] args)throws Exception { SyntheticBusinessData.verify();
        correctionBusinessDates();
        JSONObject old=state();old.remove("business_settings");old.put("invoice_issuer",SyntheticBusinessData.object().put("owner_name","Bekannter Wert"));PixelDriveState.prepare(old);yes(old.getJSONObject("business_settings").length()==1);yes(!old.getJSONObject("business_settings").has("tax_mode"));yes(old.getInt("schema_version")==2);for(String c:PixelDriveState.CAPABILITIES)yes(old.getJSONObject("capabilities").getBoolean(c));
        JSONObject missing=state();missing.remove("business_settings");PixelDriveState.prepare(missing);yes(missing.getJSONObject("business_settings").length()==0);
        JSONObject settings=state(),before=SyntheticBusinessData.object(settings.getJSONObject("business_settings").toString());before.put("future_unknown","preserve");settings.put("business_settings",before);
        JSONObject change=event("settings-cas","business_settings",SyntheticBusinessData.object().put("expected_record_hash",PixelMcpWriteTools.hash(before)).put("updates",SyntheticBusinessData.object().put("business_city","Graz")));
        yes(DriveJournalRules.apply(settings,new JSONArray().put(change)).length()==0);yes(settings.getJSONObject("business_settings").getString("future_unknown").equals("preserve"));String after=PixelMcpWriteTools.hash(settings.getJSONObject("business_settings"));
        yes(DriveJournalRules.apply(settings,new JSONArray().put(event("stale-settings","business_settings",change.getJSONObject("payload")))).length()==1);yes(PixelMcpWriteTools.hash(settings.getJSONObject("business_settings")).equals(after));
        JSONObject s=state(),offer=OffersAndPhotosTests.draft();OfferRules.normalize(offer,null,s);JSONObject action=event("infra-offer","offer",offer);String hash=PixelMcpWriteTools.hash(action);
        s.put("sync_errors",new JSONArray().put(SyntheticBusinessData.object().put("client_id","infra-offer").put("code","SYNC_COMPONENT_UPDATE_REQUIRED")));
        JSONObject obsolete=event("old-control","conflict_resolution",SyntheticBusinessData.object().put("client_id","infra-offer").put("expected_action_hash",OrderReviewRules.actionHash(action)).put("resolution","keep_current"));
        JSONArray actions=new JSONArray().put(obsolete).put(action);String customers=PixelMcpWriteTools.hash(s.getJSONArray("customers"));
        yes(DriveJournalRules.apply(s,actions).length()==0);yes(s.getJSONArray("offers").length()==1);yes(s.getJSONObject("owner_conflict_history").getJSONObject("old-control").getString("resolution").equals("superseded"));yes(PixelMcpWriteTools.hash(s.getJSONArray("customers")).equals(customers));yes(s.getJSONArray("sync_errors").length()==0);yes(s.getJSONObject("drive_action_receipts").getJSONObject("infra-offer").getString("action_sha256").equals(hash));
        JSONObject unknown=event("future","future_type",SyntheticBusinessData.object());yes(DriveJournalRules.apply(s,new JSONArray().put(unknown)).getJSONObject(0).getString("code").equals("UNSUPPORTED_ACTION"));yes(DriveJournalRules.reviewActions(s,new JSONArray().put(unknown)).length()==1);
        JSONObject integrity=PixelDriveState.integrity(s);yes(integrity.getJSONObject("counts").getInt("offers")==1);yes(!integrity.toString().contains("Bekannter Wert"));JSONObject withQueueMetadata=SyntheticBusinessData.object(s.toString()).put("drive_applied_ids",new JSONArray());yes(PixelDriveState.integrity(withQueueMetadata).getString("business_state_hash").equals(integrity.getString("business_state_hash")));
        JSONObject legacy=state();Memory memory=new Memory();JSONObject initial=DrivePrimaryState.recover(legacy,"pixel-test",memory);initial.put("offers",s.getJSONArray("offers")).put("drive_action_hashes",s.getJSONObject("drive_action_hashes")).put("drive_applied_ids",s.getJSONArray("drive_applied_ids")).put("drive_action_receipts",s.getJSONObject("drive_action_receipts"));memory.publish(initial,actions,"commit-one",1,"","");
        JSONObject recovered=DrivePrimaryState.recover(legacy,"pixel-test",memory);yes(recovered.getJSONArray("offers").length()==1);yes(DriveJournalRules.apply(recovered,actions).length()==0);yes(recovered.getJSONArray("offers").length()==1);yes(recovered.getJSONObject("drive_action_receipts").getJSONObject("infra-offer").getString("action_sha256").equals(hash));
        bad(()->DrivePrimaryState.recover(legacy,"other-pixel",memory));JSONObject changed=SyntheticBusinessData.object(legacy.toString());changed.getJSONArray("customers").getJSONObject(0).put("name","Parallel");bad(()->DrivePrimaryState.recover(changed,"pixel-test",memory));
        bad(()->DrivePrimaryState.checkParent(initial,recovered));String previous=recovered.getString("drive_parent_state_sha256");memory.publish(recovered,actions,"commit-two",2,"commit-one",previous);yes(DrivePrimaryState.recover(legacy,"pixel-test",memory).getInt("drive_parent_sequence")==2);
        memory.data.get("state-commit-two")[0]=0;bad(()->DrivePrimaryState.recover(legacy,"pixel-test",memory));
        JSONObject chain=financialState();PixelDriveState.prepare(chain);JSONObject draft=invoice(chain);chain.put("invoices",new JSONArray());JSONObject created=event("offline-create-invoice","invoice",SyntheticBusinessData.object(draft.toString()).put("_mcp_create",true).put("updated_at","t1"));
        JSONObject queuedState=SyntheticBusinessData.object().put("master",chain).put("pending",new JSONArray().put(created)),effective=PixelMcpReadTools.effective(queuedState),effectiveInvoice=effective.getJSONArray("invoices").getJSONObject(0);
        JSONObject edited=PixelMcpEntityWriteTools.prepare("update_invoice_draft",SyntheticBusinessData.object().put("request_id","offline-edit-invoice").put("invoice_id",draft.getString("id")).put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice",effectiveInvoice))).put("updates",SyntheticBusinessData.object().put("notes","Offline korrigiert")),queuedState,"offline-edit-invoice").getJSONObject("payload");
        yes(edited.getString("notes").equals("Offline korrigiert"));ManualChangeRules.prepare("invoice",edited,effectiveInvoice,"t2");edited.put("_sync_predecessors",new JSONArray().put(created.getString("client_id")));yes(DriveJournalRules.apply(chain,new JSONArray().put(event("offline-edit-invoice","invoice",edited)).put(created)).length()==0);yes(chain.getJSONArray("invoices").length()==1&&chain.getJSONArray("invoices").getJSONObject(0).getString("notes").equals("Offline korrigiert"));
        JSONObject finance=financialState(),inv=invoice(finance),issue=issue(finance,inv,"issue-once");Counter wire=new Counter(finance);LegacyDriveRestFixture client=wire.client();JSONObject reservation=DriveNumbering.reserve(client,finance,issue);yes(reservation.getString("invoice_number").equals("HG-2026-0081"));yes(DriveNumbering.reserve(client,finance,issue).getString("invoice_number").equals("HG-2026-0081"));yes(wire.patches==1);
        JSONObject prepared=InvoiceIssueRules.prepare(finance,issue,LocalDate.parse("2026-10-18"));yes(prepared.getDouble("gross_total")==100);bad(()->InvoiceIssueRules.prepare(finance,SyntheticBusinessData.object(issue.toString()).put("payload",SyntheticBusinessData.object(issue.getJSONObject("payload").toString()).put("confirmed",false)),LocalDate.parse("2026-10-18")));
        JSONObject stale=SyntheticBusinessData.object(finance.toString());stale.getJSONArray("worklogs").getJSONObject(0).put("hours",3);bad(()->InvoiceIssueRules.prepare(stale,issue,LocalDate.parse("2026-10-18")));JSONObject changedSettings=SyntheticBusinessData.object(finance.toString());changedSettings.getJSONObject("business_settings").put("owner_name","Parallel");bad(()->InvoiceIssueRules.prepare(changedSettings,issue,LocalDate.parse("2026-10-18")));
        String original=PixelMcpWriteTools.hash(finance.getJSONArray("invoices"));yes(DriveJournalRules.apply(finance,new JSONArray().put(issue),(trial,event)->{InvoiceIssueRules.commit(trial,SyntheticBusinessData.object(prepared.toString()).put("pdf_sha256","a".repeat(64)),reservation,event.getString("created_at"));throw new java.io.IOException("synthetic interruption before transaction success");}).length()==1);yes(PixelMcpWriteTools.hash(finance.getJSONArray("invoices")).equals(original));yes(finance.getJSONArray("worklogs").getJSONObject(0).getInt("billed")==0);
        yes(DriveJournalRules.apply(finance,new JSONArray().put(issue),(trial,event)->InvoiceIssueRules.commit(trial,SyntheticBusinessData.object(prepared.toString()).put("pdf_sha256","a".repeat(64)),reservation,event.getString("created_at"))).length()==0);yes(finance.getJSONArray("worklogs").getJSONObject(0).getInt("billed")==1);yes(finance.getJSONArray("invoices").getJSONObject(0).getString("status").equals("Gestellt"));yes(DriveJournalRules.apply(finance,new JSONArray().put(issue)).length()==0);
        JSONObject late=SyntheticBusinessData.object(finance.getJSONArray("worklogs").getJSONObject(0).toString()).put("hours",3).put("billed",0).put("_sync_base",issue.getJSONObject("payload").getJSONObject("_expected_worklogs").getJSONObject("qa_w"));yes(DriveJournalRules.apply(finance,new JSONArray().put(event("late-save","worklog",late))).length()==1);yes(finance.getJSONArray("worklogs").getJSONObject(0).getInt("hours")==2);JSONObject display=PixelMcpReadTools.effective(SyntheticBusinessData.object().put("master",finance).put("pending",new JSONArray().put(event("late-save","worklog",late))));yes(display.getJSONArray("worklogs").getJSONObject(0).getInt("hours")==2&&display.getJSONArray("worklogs").getJSONObject(0).getInt("billed")==1);
        JSONObject f2=financialState();JSONObject i2=invoice(f2),a2=issue(f2,i2,"lost-ack");Counter lost=new Counter(f2);lost.disconnect=true;LegacyDriveRestFixture c2=lost.client();bad(()->DriveNumbering.reserve(c2,f2,a2));yes(DriveNumbering.reserve(c2,f2,a2).getInt("counter")==81);yes(lost.patches==1);
        Counter race=new Counter(f2);race.race=true;LegacyDriveRestFixture c3=race.client();bad(()->DriveNumbering.reserve(c3,f2,a2));yes(DriveNumbering.reserve(c3,f2,a2).getInt("counter")==82);
        Counter noTag=new Counter(f2);noTag.etag=false;bad(()->DriveNumbering.reserve(noTag.client(),f2,a2));yes(noTag.patches==0);
        Counter unsupported=new Counter(f2);unsupported.ignoreCompare=true;bad(()->DriveNumbering.reserve(unsupported.client(),f2,a2));yes(unsupported.patches==0);
        JSONObject wrongOwner=SyntheticBusinessData.object(f2.toString());wrongOwner.put("drive_publisher_id","other");bad(()->DriveNumbering.config(wrongOwner));
        System.out.println("PASS "+count+" native Pixel/Drive migration, recovery, CAS, receipt, crash and financial guards (synthetic host storage; no device/network proof)");
    }
}
