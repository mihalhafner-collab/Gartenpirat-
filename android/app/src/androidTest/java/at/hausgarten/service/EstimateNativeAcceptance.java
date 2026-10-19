package at.hausgarten.service;
import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** SQLite/PDF/file transport of the actual app; isolated private files and synthetic customers only. */
final class EstimateNativeAcceptance {
 static JSONObject args(String request,String type)throws Exception{return SyntheticBusinessData.object().put("request_id",request).put("customer_id","qa_test-c").put("test_record",true).put("title","[TEST] Kostenvoranschlag").put("document_type",type).put("binding_confirmed",true).put("issue_date","2026-10-19").put("valid_until","2026-11-19").put("execution_window","November 2026").put("payment_terms","14 Tage").put("customer_type","b2b").put("scope_confirmed",true).put("positions",new JSONArray().put(SyntheticBusinessData.object().put("title","Synthetische Pflege").put("unit","h").put("quantity",14).put("price",50).put("tax_rate",0).put("price_source","user_confirmed").put("price_basis","confirmed")));}
 static JSONObject offer(OwnerNativeAcceptance.Fixture f,String id)throws Exception{return f.call("get_offer",SyntheticBusinessData.object().put("offer_id",id));}
 static void setup(OwnerNativeAcceptance.Fixture f)throws Exception{
  JSONObject s=f.store.getState().getJSONObject("master");PixelDriveState.prepare(s);JSONObject config=s.getJSONObject("business_settings");config.put("owner_name","[TEST] Firma").put("business_street","Test 1").put("business_postal_code","1000").put("business_city","Wien").put("business_email","sender@example.invalid").put("invoice_issuer_confirmed","1").put("tax_mode","small_business").put("tax_exemption_text","Synthetischer Steuerhinweis");s.getJSONArray("customers").put(SyntheticBusinessData.object().put("id","qa_test-c").put("name","[TEST] Kunde").put("test_record",true).put("street","Test 2").put("postal_code","1000").put("city","Wien").put("country","AT"));f.store.commitSnapshot(s,new HashSet<>());
 }
 static void run(PixelMcpInstrumentation t)throws Exception{
  for(String type:OfferContract.types())t.probe("isolated native "+type+" validate final PDF share restart and no Drive export",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
   setup(f);JSONObject a=args("estimate-"+type,type);String id=f.call("create_and_validate_offer",a).getString("offer_id");
   t.yes(f.call("create_and_validate_offer",a).getBoolean("replayed"));JSONObject read=offer(f,id),row=read.getJSONObject("offer");
   t.yes(row.getString("status").equals("validated"));
   t.yes(row.getString("binding_mode").equals(OfferContract.type(type).getString("binding_mode")));
   t.yes(f.store.pendingActionsForDrive().length()==0);
   JSONObject finalArgs=SyntheticBusinessData.object().put("request_id","final-"+type).put("offer_id",id).put("expected_record_hash",read.getString("record_hash")).put("expected_context_hash",read.getString("context_hash")).put("confirmed",true);f.call("finalize_offer",finalArgs);
   t.yes(f.call("finalize_offer",finalArgs).getBoolean("replayed"));read=offer(f,id);row=read.getJSONObject("offer");byte[] pdf=new BusinessDocumentStore(f.context).read(row);
   t.yes(pdf.length>1000&&new String(pdf,0,5,StandardCharsets.US_ASCII).equals("%PDF-")&&DriveRestClient.sha256(pdf).equals(row.getString("pdf_sha256")));
   t.yes(row.getString("pdf_filename").startsWith(type.startsWith("estimate_")?"TEST-KV-":"TEST-ANG-"));
   JSONObject resource=f.call("read_file_chunk",SyntheticBusinessData.object().put("resource_type","offer").put("resource_id",id).put("offset",0).put("length",49152).put("expected_sha256",row.getString("pdf_sha256")));
   t.yes(resource.getString("sha256").equals(row.getString("pdf_sha256")));
   JSONObject share=f.call("prepare_offer_share",SyntheticBusinessData.object().put("request_id","share-"+type).put("offer_id",id).put("expected_record_hash",read.getString("record_hash")));
   t.yes(share.getString("state").equals("test_preview_only")&&!share.getBoolean("sent"));read=offer(f,id);
   t.yes(read.getJSONObject("offer").getString("delivery_status").equals("share_prepared"));
   final String currentHash=read.getString("record_hash");t.rejected(()->f.call("prepare_offer_delivery",SyntheticBusinessData.object().put("request_id","blocked-send-"+type).put("offer_id",id).put("expected_record_hash",currentHash).put("recipient_email","never-send@example.invalid").put("confirmed",true)));
   JSONObject roundtrip=f.store.getState().getJSONObject("master");
   t.yes(DriveJournalRules.apply(roundtrip,f.store.pendingActions()).length()==0);
   t.yes(PixelMcpWriteTools.find(roundtrip.getJSONArray("offers"),id).getString("delivery_status").equals("share_prepared"));
   t.yes(DriveJournalRules.apply(roundtrip,f.store.pendingActions()).length()==0);
   t.yes(f.store.pendingActionsForDrive().length()==0&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("invoices").length()==0);f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){JSONObject restored=PixelMcpWriteTools.find(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("offers"),id);
   t.yes(restored.getString("binding_mode").equals(row.getString("binding_mode"))&&new BusinessDocumentStore(f.context).read(restored).length==pdf.length&&reopened.pendingActionsForDrive().length()==0);}
  }});
  t.probe("native complete paginated UI catalog and read-only selftest leave store unchanged",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
   setup(f);String before=PixelMcpWriteTools.hash(f.store.getState());JSONObject first=f.call("get_ui_action_catalog",SyntheticBusinessData.object().put("limit",100));
   t.yes(first.getInt("total")==156&&first.getJSONArray("actions").length()==100&&first.getInt("next_cursor")==100);JSONObject last=f.call("get_ui_action_catalog",SyntheticBusinessData.object().put("limit",100).put("offset",100).put("expected_catalog_hash",first.getString("catalog_hash")));
   t.yes(last.getJSONArray("actions").length()==56&&last.isNull("next_cursor"));t.rejected(()->f.call("get_ui_action_catalog",SyntheticBusinessData.object().put("expected_catalog_hash","a".repeat(64))));JSONObject result=f.call("run_self_test",SyntheticBusinessData.object());
   t.yes(!result.getBoolean("business_data_changed")&&result.getJSONObject("build_fingerprint").getInt("version_code")==BuildConfig.VERSION_CODE);
   t.yes(PixelMcpWriteTools.hash(f.store.getState()).equals(before));
  }});
  t.probe("native test customer provenance stays local across UI MCP archive and retry",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
   setup(f);String c=f.call("create_customer",SyntheticBusinessData.object().put("request_id","new-test-customer").put("name","[TEST] MCP isoliert").put("test_record",true)).getString("entity_id");
   t.yes(f.store.pendingActionsForDrive().length()==0);String c2=f.store.saveCustomer(SyntheticBusinessData.object().put("name","[TEST] UI isoliert").put("test_record",true));
   t.yes(f.store.pendingActionsForDrive().length()==0);t.rejected(()->f.store.saveCustomer(SyntheticBusinessData.object().put("id","qa_c").put("name","[TEST] Keine Umwidmung")));
   t.yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("customers"),"qa_c").getString("name").equals("QA Eigentümer"));
   t.yes(!c.equals(c2));
  }});
  t.probe("native test draft archive and confirmed empty trash never export test tombstones",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
   setup(f);String id=f.call("create_offer_draft",args("test-trash-draft","fixed_price")).getString("offer_id");JSONObject preview=f.call("preview_delete",SyntheticBusinessData.object().put("entity","offer").put("entity_id",id));f.call("archive_entity",SyntheticBusinessData.object().put("request_id","test-trash-archive").put("entity","offer").put("entity_id",id).put("expected_record_hash",preview.getString("expected_record_hash")));t.yes(f.store.pendingActionsForDrive().length()==0);JSONObject purge=f.call("preview_empty_trash",SyntheticBusinessData.object());t.yes(purge.getInt("count")==1);f.call("empty_trash",SyntheticBusinessData.object().put("request_id","test-trash-empty").put("expected_preview_hash",purge.getString("preview_hash")).put("confirmed",true));t.yes(f.store.pendingActionsForDrive().length()==0&&f.call("list_archive",SyntheticBusinessData.object()).getInt("total")==0);
  }});
  t.probe("native test-to-real customer rejected atomically and no hidden exported test action",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
   setup(f);t.rejected(()->f.call("create_offer_draft",args("bad-isolation","estimate_binding").put("customer_id","qa_c")));
   t.yes(f.store.pendingActions().length()==0);JSONObject input=args("valid-rollback","estimate_binding").put("binding_confirmed",false);t.rejected(()->f.call("create_and_validate_offer",input));
   t.yes(f.store.pendingActions().length()==0&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("offers").length()==0);
  }});
 }
}
