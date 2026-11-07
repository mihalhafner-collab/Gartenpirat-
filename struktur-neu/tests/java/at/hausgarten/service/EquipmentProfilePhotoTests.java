package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Synthetic device images: no customer, production filesystem or live bridge. */
public final class EquipmentProfilePhotoTests {
 static int checks;
 static void yes(boolean v){checks++;if(!v)throw new AssertionError("equipment photo check "+checks);}
 interface Work {void run()throws Exception;}
 static void denied(String code,Work run)throws Exception {try{run.run();throw new AssertionError("Expected "+code);}catch(PixelMcpWriteTools.Rejected e){yes(e.code.equals(code));}}
 static JSONObject obj(){return new JSONObject();}
 static final byte[] IMAGE=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
 static final class Memory implements RevierRepository {
  JSONObject snapshot=SyntheticBusinessData.profile("minimal");TreeMap<String,String> values=new TreeMap<>();boolean abortReceipt;
  public JSONObject effective()throws Exception{return new JSONObject(snapshot.toString());}
  public String get(String k,String fallback){return values.getOrDefault(k,fallback);}
  public void put(String k,String v){if(abortReceipt&&k.startsWith(RevierRules.ROOT+"request:"))throw new IllegalStateException("qa receipt failed");values.put(k,v);}
  public JSONArray list(String prefix)throws Exception{JSONArray out=new JSONArray();for(String k:values.keySet())if(k.startsWith(prefix))out.put(new JSONObject(values.get(k)));return out;}
  public JSONArray rawEntries(String prefix)throws Exception{JSONArray out=new JSONArray();for(String k:values.keySet())if(k.startsWith(prefix))out.put(obj().put("key",k).put("value",values.get(k)));return out;}
  public JSONObject planInspection(JSONObject ignored){throw new UnsupportedOperationException("QA unused");}
  final File directory;Map<String,JSONObject> uploads=new HashMap<>(),attachments=new HashMap<>();int preparations;
  Memory(File directory)throws Exception {this.directory=directory;}
  void upload(String id)throws Exception {uploads.put(id,obj().put("kind","photo_upload").put("byte_size",IMAGE.length).put("sha256",McpFileTransfer.sha(IMAGE)).put("mime_type","image/png"));}
  public JSONObject transaction(RevierRepository.Work work)throws Exception {
   Map<String,JSONObject> u=new HashMap<>(),a=new HashMap<>();for(String k:uploads.keySet())u.put(k,new JSONObject(uploads.get(k).toString()));for(String k:attachments.keySet())a.put(k,new JSONObject(attachments.get(k).toString()));
   TreeMap<String,String> prior=new TreeMap<>(values);try{return work.run();}catch(Exception failure){values=prior;uploads=u;attachments=a;throw failure;}
  }
  public JSONObject prepareEquipmentProfilePhoto(String upload,String equipment,String request)throws Exception {
   preparations++;JSONObject meta=uploads.get(upload);if(meta==null)throw RevierRules.error("EQUIPMENT_PHOTO_UPLOAD_INVALID","QA missing upload");EquipmentProfilePhotoRules.upload(meta,IMAGE);
   String hash=McpFileTransfer.sha(IMAGE);JSONObject photo=obj().put("format_version",1).put("id","eqp_photo_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("equipment_id",equipment).put("role","profile").put("content_sha256",hash).put("storage_name",hash+".png").put("mime_type","image/png").put("byte_size",IMAGE.length).put("width",1).put("height",1);
   Files.write(new File(directory,hash+".png").toPath(),IMAGE);attachments.put(photo.getString("id"),new JSONObject(photo.toString()));meta.put("attached_photo",photo.getString("id"));return photo;
  }
  public void verifyEquipmentProfilePhoto(JSONObject photo)throws Exception {EquipmentProfilePhotoRules.attachment(photo,attachments.get(photo.getString("id")));EquipmentProfilePhotoRules.original(directory,photo);}
 }
 static JSONObject args(String request){return obj().put("request_id",request);}
 static JSONObject current(RevierApplicationService app)throws Exception{return app.query("list_equipment",obj()).getJSONArray("items").getJSONObject(0);}
 static JSONObject update(RevierApplicationService app,String request)throws Exception {JSONObject d=current(app);return args(request).put("equipment_id",d.getString("id")).put("expected_record_hash",d.getString("record_hash"));}
 public static void main(String[] argv)throws Exception {
  SyntheticBusinessData.verify();Path temp=Files.createTempDirectory("qa_equipment_photos_");
  try{
   Memory store=new Memory(temp.toFile());RevierApplicationService app=new RevierApplicationService(store,RevierDomainTests.CLOCK);String baseline=PixelMcpWriteTools.hash(store.snapshot);
   JSONObject original=app.execute("save_equipment",args("qa_equipment_identity").put("name","QA Gartenschere").put("condition","defective")).getJSONObject("record");
   yes(!original.has("profile_photo")&&original.getInt("format_version")==1);yes(store.preparations==0);
   store.upload("qa_photo_upload1");JSONObject save=update(app,"qa_photo_attach1").put("profile_photo_upload_id","qa_photo_upload1");JSONObject saved=app.execute("save_equipment",save),photo=saved.getJSONObject("record").getJSONObject("profile_photo");
   yes(saved.getBoolean("local_saved")&&!saved.getBoolean("cloud_confirmed"));yes(current(app).getInt("format_version")==3);yes(current(app).getString("condition").equals("defective"));yes(photo.getString("equipment_id").equals(original.getString("id")));yes(!photo.has("customer_id"));yes(app.validateRelationships().getString("status").equals("PASS"));yes(app.validateRelationships().getJSONObject("counts").getInt("equipment_profile_photos")==1);
   yes(app.execute("save_equipment",save).getBoolean("replayed"));yes(store.preparations==1);yes(app.equipmentPhoto(photo.getString("id"))!=null);
   JSONObject stale=update(app,"qa_stale_photo").put("profile_photo_upload_id","qa_photo_upload1");app.execute("save_equipment",update(app,"qa_rename_device").put("name","QA Neuer Name"));int before=store.preparations;denied("WRITE_CONFLICT",()->app.execute("save_equipment",stale));yes(store.preparations==before);
   store.upload("qa_photo_upload2");app.execute("save_equipment",update(app,"qa_photo_replace").put("profile_photo_upload_id","qa_photo_upload2"));String second=current(app).getJSONObject("profile_photo").getString("id");yes(!second.equals(photo.getString("id")));yes(app.equipmentPhoto(photo.getString("id"))==null);app.execute("save_equipment",save);yes(current(app).getJSONObject("profile_photo").getString("id").equals(second));
   denied("EQUIPMENT_PHOTO_UPLOAD_INVALID",()->app.execute("save_equipment",update(app,"qa_upload_consumed").put("profile_photo_upload_id","qa_photo_upload2")));
   JSONObject plan=update(app,"qa_photo_plan");app.execute("save_equipment_maintenance_plan",plan.put("operation","configure").put("title","QA Klinge geschärft").put("interval_value",1).put("interval_unit","weeks").put("starts_on","2027-05-18"));yes(current(app).getInt("format_version")==3);yes(app.validateRelationships().getString("status").equals("PASS"));
   JSONObject mixed=new JSONObject(current(app).toString()).put("format_version",2);denied("REVIER_SCHEMA_UPGRADE_REQUIRED",()->RevierRules.version(mixed));
   JSONObject badPhoto=new JSONObject(photo.toString()).put("equipment_id","qa_other_device");denied("EQUIPMENT_PHOTO_INVALID",()->EquipmentProfilePhotoRules.validate(badPhoto,original.getString("id")));
   final JSONObject traversal=new JSONObject(photo.toString()).put("storage_name","../private.png");denied("EQUIPMENT_PHOTO_INVALID",()->EquipmentProfilePhotoRules.original(temp.toFile(),traversal));
   JSONObject mismatch=new JSONObject(photo.toString()).put("content_sha256","a".repeat(64));denied("EQUIPMENT_PHOTO_INVALID",()->EquipmentProfilePhotoRules.attachment(mismatch,photo));
   JSONObject activePhoto=current(app).getJSONObject("profile_photo");File file=new File(temp.toFile(),activePhoto.getString("storage_name"));Files.write(file.toPath(),new byte[]{1});denied("EQUIPMENT_PHOTO_ORIGINAL_MISSING",app::validateRelationships);
   app.execute("save_equipment",update(app,"qa_business_without_photo").put("notes","QA local operation still works"));yes(current(app).getString("notes").contains("still works"));Files.write(file.toPath(),IMAGE);
   RevierApplicationService restarted=new RevierApplicationService(store,RevierDomainTests.CLOCK);yes(restarted.validateRelationships().getString("status").equals("PASS"));yes(current(restarted).getJSONObject("profile_photo").getString("id").equals(second));
   store.upload("qa_photo_abort");String oldValues=store.values.toString();store.abortReceipt=true;try{app.execute("save_equipment",update(app,"qa_transaction_abort").put("profile_photo_upload_id","qa_photo_abort"));throw new AssertionError("Expected receipt failure");}catch(IllegalStateException expected){}store.abortReceipt=false;yes(oldValues.equals(store.values.toString()));yes(!store.uploads.get("qa_photo_abort").has("attached_photo"));
   app.execute("set_revier_archived",args("qa_archive_device").put("entity","equipment").put("entity_id",original.getString("id")).put("expected_record_hash",current(app).getString("record_hash")).put("archived",true).put("confirmed",true));
   JSONObject archived=app.query("list_equipment",obj().put("include_archived",true)).getJSONArray("items").getJSONObject(0);denied("EQUIPMENT_INACTIVE",()->app.execute("save_equipment",args("qa_archive_photo_denied").put("equipment_id",original.getString("id")).put("expected_record_hash",archived.getString("record_hash")).put("profile_photo_upload_id","qa_photo_abort")));
   store.upload("qa_purge_photo");JSONObject disposable=app.execute("save_equipment",args("qa_disposable_creation").put("name","QA bewusst entfernbar").put("profile_photo_upload_id","qa_purge_photo")).getJSONObject("record");String disposableId=disposable.getString("id"),disposablePhoto=disposable.getJSONObject("profile_photo").getString("id");
   JSONObject archivedDisposable=app.execute("set_revier_archived",args("qa_disposable_archive").put("entity","equipment").put("entity_id",disposableId).put("expected_record_hash",disposable.getString("record_hash")).put("archived",true).put("confirmed",true)).getJSONObject("record");
   app.execute("purge_revier_entity",args("qa_disposable_purge").put("entity","equipment").put("entity_id",disposableId).put("expected_record_hash",archivedDisposable.getString("record_hash")).put("confirmed",true));yes(app.equipmentPhoto(disposablePhoto)==null);
   denied("REQUEST_EXPIRED",()->app.execute("save_equipment",args("qa_disposable_creation").put("name","QA bewusst entfernbar").put("profile_photo_upload_id","qa_purge_photo")));
   denied("ENTITY_DELETED",()->app.execute("save_equipment",args("qa_dead_device_new_request").put("equipment_id",disposableId).put("expected_record_hash",archivedDisposable.getString("record_hash")).put("name","QA nicht wiederbeleben")));
   yes(baseline.equals(PixelMcpWriteTools.hash(store.snapshot)));yes(store.snapshot.getJSONArray("photos").length()==SyntheticBusinessData.profile("minimal").getJSONArray("photos").length());
   File uiFiles=new File(temp.toFile(),"qa_ui"),mcpFiles=new File(temp.toFile(),"qa_mcp");yes(uiFiles.mkdir()&&mcpFiles.mkdir());Memory uiStore=new Memory(uiFiles),mcpStore=new Memory(mcpFiles);uiStore.upload("qa_shared_upload");mcpStore.upload("qa_shared_upload");
   RevierApplicationService uiApp=new RevierApplicationService(uiStore,RevierDomainTests.CLOCK),mcpApp=new RevierApplicationService(mcpStore,RevierDomainTests.CLOCK);JSONObject same=args("qa_same_ui_mcp_photo").put("name","QA selbes Gerät").put("profile_photo_upload_id","qa_shared_upload");
   OwnerRepository port=(OwnerRepository)java.lang.reflect.Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(),new Class<?>[]{OwnerRepository.class},(proxy,method,values)->{if(!method.getName().equals("revierAction"))throw new AssertionError("Unexpected photo side effect "+method.getName());return mcpApp.execute((String)values[0],(JSONObject)values[1]);});
   JSONObject uiResult=uiApp.execute("save_equipment",same),mcpResult=new OwnerApplicationService(port).perform("save_equipment",same,"qa_same_ui_mcp_photo");yes(PixelMcpWriteTools.hash(uiResult).equals(PixelMcpWriteTools.hash(mcpResult)));yes(uiStore.values.equals(mcpStore.values));yes(uiApp.validateRelationships().getString("status").equals("PASS")&&mcpApp.validateRelationships().getString("status").equals("PASS"));
   JSONObject schema=LocalWorkflowTools.schema("save_equipment");yes(schema.getJSONObject("properties").has("profile_photo_upload_id"));McpSchema.validate(args("qa_photo_schema").put("name","QA Beispiel").put("profile_photo_upload_id","qa_upload_schema"),schema);
   System.out.println("PASS "+checks+" equipment photo identity, local-only, replay/CAS/rollback, replace/history, format, maintenance, missing-original/business-continuity and restore checks");
  }finally{try(java.util.stream.Stream<Path> files=Files.walk(temp)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});}}
 }
}
