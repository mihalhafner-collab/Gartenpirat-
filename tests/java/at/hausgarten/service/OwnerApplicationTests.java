package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Real application services through a synthetic in-memory effects port, never Android/Drive. */
public final class OwnerApplicationTests {
 static int checks;
 static void yes(boolean value){checks++;if(!value)throw new AssertionError("application check "+checks);}
 interface Task{void run()throws Exception;}
 static void rejects(Task task)throws Exception{boolean denied=false;try{task.run();}catch(IllegalArgumentException|UnsupportedOperationException expected){denied=true;}yes(denied);}
 static final class MemoryRepository implements OwnerRepository {
  final JSONObject state;final Map<String,String> preferences=new HashMap<>();final Map<String,JSONObject> metadata=new HashMap<>();final Map<String,byte[]> bytes=new HashMap<>();int workSaves,protections;boolean failProtection;
  MemoryRepository()throws Exception{JSONObject master=OffersAndPhotosTests.snapshot();PixelDriveState.prepare(master);state=SyntheticBusinessData.object().put("master",master).put("pending",new JSONArray());}
  public JSONObject state(){return state;}
  public String ownerValue(String key,String fallback){return preferences.getOrDefault(key,fallback);}
  public void putOwnerValue(String key,String value){preferences.put(key,value);}
  public JSONArray pendingActions()throws Exception{return state.getJSONArray("pending");}
  public JSONObject enqueue(String kind,JSONObject payload,String request)throws Exception{
   state.getJSONArray("pending").put(SyntheticBusinessData.object().put("client_id",request).put("kind",kind).put("payload",SyntheticBusinessData.object(payload.toString())).put("created_at","2026-10-19T12:00:00Z"));
   return SyntheticBusinessData.object().put("client_id",request).put("entity_id",payload.optString("id"));
  }
  public JSONObject fileMeta(String id){if(!metadata.containsKey(id))throw new IllegalArgumentException("QA missing upload");return metadata.get(id);}
  public byte[] fileBytes(String id){if(!bytes.containsKey(id))throw new IllegalArgumentException("QA missing upload");return bytes.get(id);}
  public void putFile(String id,JSONObject meta,byte[] value){metadata.put(id,meta);bytes.put(id,value);}
  public String savePhoto(JSONObject p){throw new UnsupportedOperationException("Native image store required");}
  public void replaceWorklog(JSONObject p){workSaves++;}
  public JSONObject createDocument(JSONObject p){throw new UnsupportedOperationException("Native PDF store required");}
  public void readDocument(JSONObject p){throw new UnsupportedOperationException("Native PDF store required");}
  public JSONObject verifyDelivery(String id,JSONObject p){throw new UnsupportedOperationException("Provider receipt required");}
  public void verifyLegacyBackup(String id,String hash){throw new UnsupportedOperationException("Verified backup required");}
  public JSONObject createBackup(String request){throw new UnsupportedOperationException("Native backup required");}
  public JSONObject protectDeletion(JSONObject snapshot,JSONArray entries,String request)throws Exception{protections++;if(failProtection)throw new java.io.IOException("QA safety backup unavailable");return SyntheticBusinessData.object().put("backup_id","qa_deletion_backup_"+request).put("protected_records",entries.length());}
  public JSONObject restoreBackup(JSONObject a,String request){throw new UnsupportedOperationException("Native backup required");}
  public JSONObject planningSettings(){return SyntheticBusinessData.object();}
  public boolean calendarConnected(){return false;}
  public boolean weatherCredentialAvailable(){return false;}
  public void savePlanningSettings(JSONObject s,String expected){throw new UnsupportedOperationException("Native settings required");}
  public JSONObject requestPlanning(){return SyntheticBusinessData.object();}
  public JSONObject acknowledgeWeather(JSONObject a){throw new UnsupportedOperationException();}
  public JSONObject resolveCalendar(JSONObject a){throw new UnsupportedOperationException();}
  public void checkBusy(JSONObject row){}
 }
 static JSONObject invoke(OwnerApplicationService app,String name,JSONObject args)throws Exception{
  if(OfferTools.write(name))McpSchema.validate(args,OfferTools.schema(name));else if(AppointmentTools.write(name))McpSchema.validate(args,AppointmentTools.schema(name));else PixelMcpOwnerTools.validate(name,args);
  return app.perform(name,args,"qa_application_"+args.optString("request_id"));
 }
 public static void main(String[] ignored)throws Exception{
  MemoryRepository localPlanning=new MemoryRepository();localPlanning.state.getJSONObject("master").put("processing_engine",LocalAuthorityRules.ENGINE);localPlanning.state.put("sync_queue",new JSONObject().put("contract_version",3).put("mode","backup_only").put("total",0));String site=localPlanning.state.getJSONObject("master").getJSONArray("customers").getJSONObject(0).getString("id");JSONObject appointment=new JSONObject().put("request_id","qa_local_planning_without_drive_items").put("customer_id",site).put("title","QA lokale Besichtigung").put("purpose","inspection").put("weather_profile","inspection").put("starts_at","2027-05-18T07:00:00+02:00").put("ends_at","2027-05-18T07:30:00+02:00").put("timezone","Europe/Vienna");JSONObject planned=invoke(new OwnerApplicationService(localPlanning),"create_appointment",appointment);yes(!planned.getString("appointment_id").isEmpty()&&localPlanning.pendingActions().length()==1);yes(!localPlanning.state.getJSONObject("sync_queue").has("items"));
  JSONObject oldConflict=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("entity_id",planned.getString("appointment_id")).put("kind","appointment").put("state","write_conflict")));localPlanning.state.put("legacy_queue_inventory",oldConflict);String retained=PixelMcpWriteTools.hash(localPlanning.state);try{invoke(new OwnerApplicationService(localPlanning),"update_appointment",new JSONObject().put("request_id","qa_final_local_historical_conflict").put("appointment_id",planned.getString("appointment_id")).put("expected_record_hash","a".repeat(64)).put("updates",new JSONObject().put("notes","QA nicht still entscheiden")));throw new AssertionError("QA final historical conflict overwritten");}catch(PixelMcpWriteTools.Rejected expected){yes(expected.code.equals("SYNC_CONFLICT_PENDING")&&localPlanning.pendingActions().length()==1&&retained.equals(PixelMcpWriteTools.hash(localPlanning.state)));}
  MemoryRepository malformedLocal=new MemoryRepository();malformedLocal.state.getJSONObject("master").put("processing_engine",LocalAuthorityRules.ENGINE);malformedLocal.state.put("sync_queue",new JSONObject());try{invoke(new OwnerApplicationService(malformedLocal),"create_appointment",appointment);throw new AssertionError("QA invalid local queue accepted");}catch(IllegalStateException expected){yes(expected.getMessage().equals("LOCAL_STATE_CONTRACT_INVALID")&&malformedLocal.pendingActions().length()==0);}
  MemoryRepository legacyPlanning=new MemoryRepository();legacyPlanning.state.getJSONObject("master").put("processing_engine",PixelDriveState.ENGINE);legacyPlanning.state.put("sync_queue",new JSONObject().put("items",new JSONArray().put(new JSONObject().put("entity_id","qa_legacy_appointment").put("kind","appointment").put("state","write_conflict"))));try{invoke(new OwnerApplicationService(legacyPlanning),"update_appointment",new JSONObject().put("request_id","qa_legacy_conflict_still_held").put("appointment_id","qa_legacy_appointment").put("expected_record_hash","a".repeat(64)).put("updates",new JSONObject().put("notes","QA muss gehalten bleiben")));throw new AssertionError("QA legacy conflict overwritten");}catch(PixelMcpWriteTools.Rejected expected){yes(expected.code.equals("SYNC_CONFLICT_PENDING")&&legacyPlanning.pendingActions().length()==0);}
  SyntheticBusinessData.verify();MemoryRepository repository=new MemoryRepository();OwnerApplicationService app=new OwnerApplicationService(repository);
  for(String name:PixelMcpOwnerTools.WRITES)yes(app.handles(name)==!name.equals("prepare_legacy_conflict_backup"));
  for(String name:OfferTools.WRITES)yes(app.handles(name));for(String name:AppointmentTools.WRITES)yes(app.handles(name));yes(!app.handles("unknown_operation"));
  String customers=PixelMcpWriteTools.hash(repository.state.getJSONObject("master").getJSONArray("customers"));
  JSONObject source=OffersAndPhotosTests.draft(),draft=SyntheticBusinessData.object();JSONObject fields=OfferTools.schema("create_offer_draft").getJSONObject("properties");for(Iterator<String> keys=fields.keys();keys.hasNext();){String key=keys.next();if(source.has(key))draft.put(key,source.get(key));}draft.put("request_id","qa_offer_create");
  JSONObject created=invoke(app,"create_offer_draft",draft);yes(created.has("offer_id"));yes(created.getString("record_hash").equals(PixelMcpWriteTools.hash(DriveJournalRules.clean(PixelMcpWriteTools.find(repository.effective().getJSONArray("offers"),created.getString("offer_id"))))));yes(repository.pendingActions().length()==1);
  JSONObject row=PixelMcpWriteTools.find(repository.effective().getJSONArray("offers"),created.getString("offer_id"));
  JSONObject update=SyntheticBusinessData.object().put("request_id","qa_offer_update").put("offer_id",row.getString("id")).put("expected_record_hash",PixelMcpWriteTools.hash(DriveJournalRules.clean(row))).put("updates",SyntheticBusinessData.object().put("title","QA changed"));
  invoke(app,"update_offer_draft",update);yes(repository.pendingActions().length()==2);
  rejects(()->invoke(app,"update_offer_draft",update));yes(repository.pendingActions().length()==2);yes(PixelMcpWriteTools.hash(repository.state.getJSONObject("master").getJSONArray("customers")).equals(customers));
  JSONObject settings=SyntheticBusinessData.object().put("request_id","qa_settings_update").put("expected_record_hash",PixelMcpWriteTools.hash(repository.settings())).put("updates",SyntheticBusinessData.object().put("default_activity","QA Pflege"));
  invoke(app,"update_settings",settings);yes(repository.settings().getString("default_activity").equals("QA Pflege"));rejects(()->invoke(app,"update_settings",settings));yes(repository.pendingActions().length()==2);
  JSONObject planning=SyntheticBusinessData.object().put("request_id","qa_planning_enable").put("expected_record_hash",PixelMcpWriteTools.hash(repository.planningSettings())).put("updates",SyntheticBusinessData.object().put("calendar_enabled",true));rejects(()->invoke(app,"update_planning_settings",planning));yes(repository.pendingActions().length()==2);
  byte[] image={1,2,3};String hash=DriveRestClient.sha256(image);
  JSONObject upload=invoke(app,"begin_file_upload",SyntheticBusinessData.object().put("request_id","qa_upload_start").put("byte_size",image.length).put("sha256",hash).put("mime_type","image/jpeg"));
  JSONObject chunk=SyntheticBusinessData.object().put("request_id","qa_upload_chunk").put("upload_id",upload.getString("upload_id")).put("offset",0).put("chunk_base64",Base64.getEncoder().encodeToString(image)).put("chunk_sha256",hash);
  yes(invoke(app,"write_file_chunk",chunk).getBoolean("complete"));yes(Arrays.equals(image,repository.fileBytes(upload.getString("upload_id"))));
  rejects(()->invoke(app,"write_file_chunk",SyntheticBusinessData.object(chunk.toString()).put("chunk_sha256","0".repeat(64))));
  JSONObject noBackup=SyntheticBusinessData.object().put("request_id","qa_close_missing").put("backup_id","qa_missing").put("expected_preview_hash","0".repeat(64)).put("confirmed",true);rejects(()->invoke(app,"close_legacy_conflicts",noBackup));yes(repository.pendingActions().length()==2);
  JSONObject mark=SyntheticBusinessData.object().put("request_id","qa_personal_paid").put("work_record_id","qa_missing").put("paid",true);rejects(()->invoke(app,"set_personal_paid",mark));yes(!repository.preferences.containsKey("personal_paid"));
  String beforeRetirement=PixelMcpWriteTools.hash(repository.state);JSONObject retired=invoke(app,"request_sync",SyntheticBusinessData.object().put("request_id","qa_sync_request"));yes(retired.getString("state").equals("retired")&&retired.getString("code").equals("LEGACY_SYNC_RETIRED")&&retired.getInt("storage_contract_version")==3&&!retired.getBoolean("backup_started"));yes(beforeRetirement.equals(PixelMcpWriteTools.hash(repository.state)));
  yes(repository.state.getJSONObject("master").getJSONArray("invoices").length()==0);
  JSONObject master=repository.state.getJSONObject("master");
  JSONObject work=SyntheticBusinessData.object().put("id","qa_work").put("customer_id","qa_c").put("hours",1).put("work_date","2026-10-19").put("activity","QA Pflege").put("billed",0).put("updated_at","qa_t1");master.getJSONArray("worklogs").put(work);
  repository.putOwnerValue("personal_paid_migrated","1");mark.put("work_record_id","qa_work");invoke(app,"set_personal_paid",mark);yes(new JSONArray(repository.ownerValue("personal_paid","[]")).getString(0).equals("qa_work"));yes(repository.pendingActions().length()==2);
  invoke(app,"set_work_record_billed",SyntheticBusinessData.object().put("request_id","qa_billed_mark").put("work_record_id","qa_work").put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpWriteTools.base(work))).put("reason","QA explicit mark").put("billed",true));yes(repository.workSaves==1);yes(repository.pendingActions().length()==3);
  JSONObject photo=SyntheticBusinessData.object().put("id","qa_photo").put("customer_id","qa_c").put("caption","QA before").put("role","documentation").put("updated_at","qa_t1");master.getJSONArray("photos").put(photo);
  JSONObject photoUpdate=SyntheticBusinessData.object().put("request_id","qa_photo_edit").put("photo_id","qa_photo").put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpOwnerTools.photoHash(photo))).put("updates",SyntheticBusinessData.object().put("caption","QA after"));invoke(app,"update_photo",photoUpdate);yes(repository.pendingActions().length()==4);
  JSONObject badPhoto=SyntheticBusinessData.object(photoUpdate.toString()).put("request_id","qa_bad_photo");JSONObject currentPhoto=PixelMcpWriteTools.find(repository.effective().getJSONArray("photos"),"qa_photo");badPhoto.put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpOwnerTools.photoHash(currentPhoto))).put("updates",SyntheticBusinessData.object().put("order_id","qa_missing_order"));rejects(()->invoke(app,"update_photo",badPhoto));yes(repository.pendingActions().length()==4);
  JSONObject invoice=SyntheticBusinessData.object().put("id","qa_invoice").put("customer_id","qa_c").put("issued_at","2026-10-19T12:00:00Z").put("status","Gestellt").put("invoice_number","QA-R-1").put("pdf_storage_name","a".repeat(64)+".pdf");master.getJSONArray("invoices").put(invoice);
  JSONObject share=invoke(app,"prepare_invoice_share",SyntheticBusinessData.object().put("request_id","qa_invoice_share").put("invoice_id","qa_invoice").put("channel","share"));yes(share.getString("state").equals("device_interaction_required"));yes(!share.getBoolean("sent")&&!share.getBoolean("delivered"));yes(repository.pendingActions().length()==4);
  yes(PixelMcpWriteTools.hash(master.getJSONArray("customers")).equals(customers));
  System.out.println("PASS "+checks+" application routing/port/CAS/isolation/upload/account/backup guards; durable SQLite replay tested natively");
 }
}
