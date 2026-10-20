package at.hausgarten.service;
import android.content.Context;
import android.webkit.JavascriptInterface;
import org.json.*;
import java.util.*;

/** Business WebView adapter; lifecycle, consent, camera and OS share remain in Activity. */
public class BusinessDataBridge {
    protected final Context context;
    protected final PixelLocalStore store;
    private final PendingWorkPhotoStore pendingWorkPhotos;
    private final Runnable requestSync;
    private final LocalOwnerRepository repository;
    protected BusinessDataBridge(Context context,PixelLocalStore store,PendingWorkPhotoStore pendingWorkPhotos,Runnable requestSync){
        this.context=context;this.store=store;this.pendingWorkPhotos=pendingWorkPhotos;this.requestSync=requestSync;
        repository=new LocalOwnerRepository(context,store);
    }
@JavascriptInterface public String getState() { try{JSONObject state=store.getState();state.put("master",PixelMcpReadTools.effective(state)).put("ui_effective",true);JSONArray photos=state.getJSONObject("master").getJSONArray("photos");for(int i=0;i<photos.length();i++){JSONObject photo=photos.getJSONObject(i);String thumbnail=photo.optString("thumbnail_storage_name");if(thumbnail.matches("[0-9a-f]{64}\\.jpg"))photo.put("thumbnail_data","content://"+context.getPackageName()+".photothumbnails/"+thumbnail);}return state.toString();}catch(Exception e){return store.getState().toString();} }
@JavascriptInterface public String planningTime(String value,String zone,String offset){try{return AppointmentRules.local(value,zone,offset).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
@JavascriptInterface public String ownerCall(String name,String arguments){
    try{if(!name.equals("get_invoice")&&!PixelMcpOwnerTools.read(name)&&!PixelMcpOwnerTools.write(name)&&!OfferTools.read(name)&&!OfferTools.write(name)&&!AppointmentTools.read(name)&&!AppointmentTools.write(name))throw new IllegalArgumentException("Unbekannte Eigentümerfunktion.");PixelMcpReadTools tools=new PixelMcpReadTools(()->store.getState(),store,new PixelOwnerAccess(context,store));return tools.call(name,new JSONObject(arguments)).toString();}
    catch(Exception e){try{return McpErrors.bridge(e).toString();}catch(Exception ignored){return "{}";}}
}
@JavascriptInterface public String getSyncDetails(){try{JSONObject s=store.getState();return DriveJournalRules.queueStatus(s.getJSONObject("master"),s.getJSONArray("pending")).toString();}catch(Exception e){return "{}";}}
@JavascriptInterface public String getSyncConflict(String id){try{return PixelMcpOwnerTools.conflicts(store.getState(),new JSONObject().put("limit",1).put("client_id",id)).toString();}catch(Exception e){return "ERROR: Konflikt konnte nicht vollständig gelesen werden.";}}
@JavascriptInterface public String resolveSyncConflict(String request){try{return new PixelOwnerAccess(context,store).execute("resolve_sync_conflict",new JSONObject(request)).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
@JavascriptInterface public String getWorkSession() {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).current().toString(); }
    catch (Exception error) { return "{\"state\":\"ERROR\",\"message\":"+JSONObject.quote(String.valueOf(error.getMessage()))+"}"; }
}
@JavascriptInterface public String startWorkSession(String value) {
    try { return store.startWorkSession(new JSONObject(value)).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String pauseWorkSession(String sessionId,String requestId) {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).pause(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String resumeWorkSession(String sessionId,String requestId) {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).resume(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String stopWorkSession(String sessionId,String requestId) {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).stop(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String continueWorkSession(String sessionId,String requestId) {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).continueStopped(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String discardWorkSession(String sessionId,String requestId) {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).discard(sessionId,requestId).toString(); }
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String restoreWorkSession(String sessionId,String requestId) {
    try { return new WorkSessionStore(store.getWritableDatabase(),context).restore(sessionId,requestId).toString(); }
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String completeWorkSession(String sessionId,String value,String requestId) {
    try { String id=store.completeWorkSession(sessionId,new JSONObject(value),requestId);requestSync.run();return id; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String saveWorklog(String value) {
    try { JSONObject payload=new JSONObject(value);String sessionId=payload.optString("_session_id");payload.remove("_session_id");String id=sessionId.isEmpty()?store.saveWorklog(payload):store.completeWorkSession(sessionId,payload,"ui-finalize-"+sessionId);requestSync.run();return id; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String saveCustomer(String value) {
    try { String id = store.saveCustomer(new JSONObject(value)); requestSync.run(); return id; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String inspectOrder(String id) {
    try{return OrderReviewRules.inspect(store.getState(),id).toString();}
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String saveReviewedOrder(String value) {
    try{String id=store.saveReviewedOrder(new JSONObject(value));requestSync.run();return new JSONObject().put("id",id).put("saved",true).toString();}
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String saveOrder(String value) {
    try { String id = store.saveOrder(new JSONObject(value)); requestSync.run(); return id; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String getPhotoStorageSummary(){try{return store.photoAttachmentStore().storageSummary().toString();}catch(Exception e){return "ERROR: Speicherstatus nicht verfügbar.";}}
@JavascriptInterface public String getOwnerSettings(){return store.ownerValue("settings","{}");}
@JavascriptInterface public String getPersonalPaid(){return "1".equals(store.ownerValue("personal_paid_migrated",""))?store.ownerValue("personal_paid","[]"):"";}
@JavascriptInterface public String migratePersonalPaid(String value){return repository.migratePersonalPaid(value);}
@JavascriptInterface public String setPersonalPaid(String value){return repository.setPersonalPaid(value);}
@JavascriptInterface public String getInvoiceShareHandoff(){return store.ownerValue("invoice_share_handoff", "{}");}
@JavascriptInterface public String saveInvoice(String value) {
    try{String id=store.saveInvoiceDraft(new JSONObject(value));requestSync.run();return id;}
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String requestInvoiceIssue(String identifier) {
    try{JSONObject all=PixelMcpReadTools.effective(store.getState()),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),identifier);store.execute("request_invoice_issue",new JSONObject().put("request_id","ui-issue-"+UUID.randomUUID()).put("invoice_id",identifier).put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice",invoice))).put("confirmed",true));return "OK";}catch(Exception e){return "ERROR: "+e.getMessage();}
}
@JavascriptInterface public String savePhoto(String value) {
    try { String id = store.savePhoto(new JSONObject(value)); requestSync.run(); return id; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String stagePendingWorkPhoto(String value) {
    try { return pendingWorkPhotos.stage(value); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String getPendingWorkPhoto() {
    try { return pendingWorkPhotos.read(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String clearPendingWorkPhoto() {
    try { pendingWorkPhotos.clear(); return "OK"; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String getPhotoData(String photoId) {
    try { return store.loadPhotoData(photoId); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String removeEntity(String entity, String id) {
    try { String actionId = store.removeEntity(entity, id); requestSync.run(); return actionId; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String undoRemoveEntity(String entity, String id) {
    try { String result = store.undoRemoveEntity(entity, id); requestSync.run(); return result; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
}
