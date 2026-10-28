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
    // Bounded, process-local technical samples only. Never retain arguments,
    // responses, tool names, record IDs, timestamps or business content.
    private final Map<String,ArrayDeque<double[]>> performanceSamples = new LinkedHashMap<>();
    private String measured(String label, java.util.function.Supplier<String> operation) {
        long started=System.nanoTime();String result=null;
        try { return result=operation.get(); }
        finally {
            double elapsed=(System.nanoTime()-started)/1_000_000.0;
            int bytes=result==null?0:result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            synchronized(performanceSamples){
                ArrayDeque<double[]> samples=performanceSamples.computeIfAbsent(label,key->new ArrayDeque<>());
                if(samples.size()==128)samples.removeFirst();samples.addLast(new double[]{elapsed,bytes});
            }
        }
    }
    final String performanceMetrics() {
        JSONObject result=new JSONObject();
        try { synchronized(performanceSamples){for(Map.Entry<String,ArrayDeque<double[]>> entry:performanceSamples.entrySet()){
            JSONArray rows=new JSONArray();for(double[] row:entry.getValue())rows.put(new JSONObject().put("duration_ms",row[0]).put("response_bytes",(long)row[1]));
            result.put(entry.getKey(),rows);
        }} } catch(JSONException unavailable){return "{}";}
        return result.toString();
    }
    private void notifyCommitted() {
        // The business transaction already succeeded. A deferrable adapter/scheduler
        // failure cannot turn that local commit into a failed save or a second create.
        try { requestSync.run(); } catch (RuntimeException unavailable) { /* Durable local pending work remains retryable. */ }
    }
    private String editorFailure(String kind,String raw,Exception failure){
        try{JSONObject payload=new JSONObject(raw);String request=payload.optString("_request_id");if(!request.isEmpty()){payload.remove("_request_id");if(kind.equals("worklog")&&payload.optString("_session_id").isEmpty())payload.remove("_session_id");String saved=store.ownerValue("editor_commit:"+PixelMcpWriteTools.hash(request),"");if(!saved.isEmpty()){JSONObject receipt=new JSONObject(saved);String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("kind",kind).put("payload",payload));if(fingerprint.equals(receipt.getString("fingerprint")))return receipt.getString("id");return "ERROR: REQUEST_ID_REUSED: Ursprüngliche Aktion unverändert prüfen.";}}}catch(Exception unreadable){return "ERROR: COMMIT_UNKNOWN: Speicherbeleg ist noch nicht lesbar. Dieselbe Aktion erneut prüfen.";}
        return failure instanceof IllegalArgumentException||failure instanceof JSONException?"ERROR: "+failure.getMessage():"ERROR: COMMIT_UNKNOWN: Speicherergebnis ist ungeklärt. Dieselbe Aktion erneut prüfen.";
    }
@JavascriptInterface public String getBrowserDraftState(String raw){try{return new JSONObject().put("ok",true).put("data",new BrowserDraftRepository(context,store).read(raw)).toString();}catch(Exception failure){return BrowserDraftRepository.responseFailure(failure);}}
@JavascriptInterface public String writeBrowserDraftState(String raw){try{return new JSONObject().put("ok",true).put("data",new BrowserDraftRepository(context,store).write(raw)).toString();}catch(Exception failure){return BrowserDraftRepository.responseFailure(failure);}}
@JavascriptInterface public String getState() { return measured("getState", () -> { try{JSONObject state=store.getState();state.put("master",PixelMcpReadTools.effective(state)).put("ui_effective",true);JSONArray photos=state.getJSONObject("master").getJSONArray("photos");for(int i=0;i<photos.length();i++){JSONObject photo=photos.getJSONObject(i);String thumbnail=photo.optString("thumbnail_storage_name");if(thumbnail.matches("[0-9a-f]{64}\\.jpg"))photo.put("thumbnail_data","content://"+context.getPackageName()+".photothumbnails/"+thumbnail);}return state.toString();}catch(Exception e){return store.getState().toString();} }); }
@JavascriptInterface public String planningTime(String value,String zone,String offset){try{return AppointmentRules.local(value,zone,offset).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
@JavascriptInterface public String ownerCall(String name,String arguments){ return measured("ownerCall", () -> {
    try{if(!name.equals("get_invoice")&&!name.equals("get_business_summary")&&!PixelMcpOwnerTools.read(name)&&!PixelMcpOwnerTools.write(name)&&!OfferTools.read(name)&&!OfferTools.write(name)&&!AppointmentTools.read(name)&&!AppointmentTools.write(name))throw new IllegalArgumentException("Unbekannte Eigentümerfunktion.");PixelMcpReadTools tools=new PixelMcpReadTools(()->store.getState(),store,new PixelOwnerAccess(context,store));return tools.call(name,new JSONObject(arguments)).toString();}
    catch(Exception e){try{return McpErrors.bridge(e).toString();}catch(Exception ignored){return "{}";}}
}); }
@JavascriptInterface public String getSyncDetails(){try{JSONObject s=store.getState();return DriveJournalRules.queueStatus(s.getJSONObject("master"),s.getJSONArray("pending")).toString();}catch(Exception e){return "{}";}}
@JavascriptInterface public String getSyncConflict(String id){try{return PixelMcpOwnerTools.conflicts(store.getState(),new JSONObject().put("limit",1).put("client_id",id)).toString();}catch(Exception e){return "ERROR: Konflikt konnte nicht vollständig gelesen werden.";}}
@JavascriptInterface public String resolveSyncConflict(String request){try{return new PixelOwnerAccess(context,store).execute("resolve_sync_conflict",new JSONObject(request)).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
@JavascriptInterface public String getWorkSession() { return measured("getWorkSession", () -> {
    try { return store.workSessions().current().toString(); }
    catch (Exception error) { return "{\"state\":\"ERROR\",\"message\":"+JSONObject.quote(String.valueOf(error.getMessage()))+"}"; }
}); }
@JavascriptInterface public String reviewWorkSession(String sessionId,String input) {
    try { return store.workSessions().reviewTime(sessionId,new JSONObject(input)).toString(); }
    catch(Exception error){ return "ERROR: "+error.getMessage(); }
}
@JavascriptInterface public String startWorkSession(String value) {
    try { return store.startWorkSession(new JSONObject(value)).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String pauseWorkSession(String sessionId,String requestId) {
    try { return store.workSessions().pause(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String resumeWorkSession(String sessionId,String requestId) {
    try { return store.workSessions().resume(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String stopWorkSession(String sessionId,String requestId) {
    try { return store.workSessions().stop(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String continueWorkSession(String sessionId,String requestId) {
    try { return store.workSessions().continueStopped(sessionId,requestId).toString(); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String discardWorkSession(String sessionId,String requestId) {
    try { return store.workSessions().discard(sessionId,requestId).toString(); }
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String restoreWorkSession(String sessionId,String requestId) {
    try { return store.workSessions().restore(sessionId,requestId).toString(); }
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String completeWorkSession(String sessionId,String value,String requestId) {
    try { String id=store.completeWorkSession(sessionId,new JSONObject(value),requestId);notifyCommitted();return id; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String saveWorklog(String value) {
    try {String id=store.saveEditorRecord("worklog",new JSONObject(value));notifyCommitted();return id; }
    catch (Exception error) { return editorFailure("worklog",value,error); }
}
@JavascriptInterface public String saveCustomer(String value) {
    try { String id = store.saveEditorRecord("customer",new JSONObject(value)); notifyCommitted(); return id; }
    catch (Exception error) { return editorFailure("customer",value,error); }
}
@JavascriptInterface public String inspectOrder(String id) {
    try{return OrderReviewRules.inspect(store.getState(),id).toString();}
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String saveReviewedOrder(String value) {
    try{String id=store.saveReviewedOrder(new JSONObject(value));notifyCommitted();return new JSONObject().put("id",id).put("saved",true).toString();}
    catch(Exception error){return "ERROR: "+error.getMessage();}
}
@JavascriptInterface public String saveOrder(String value) {
    try { String id = store.saveEditorRecord("order",new JSONObject(value)); notifyCommitted(); return id; }
    catch (Exception error) { return editorFailure("order",value,error); }
}
@JavascriptInterface public String getPhotoStorageSummary(){try{return store.photoAttachmentStore().storageSummary().toString();}catch(Exception e){return "ERROR: Speicherstatus nicht verfügbar.";}}
@JavascriptInterface public String getOwnerSettings(){return store.ownerValue("settings","{}");}
@JavascriptInterface public String getPersonalPaid(){return "1".equals(store.ownerValue("personal_paid_migrated",""))?store.ownerValue("personal_paid","[]"):"";}
@JavascriptInterface public String migratePersonalPaid(String value){return repository.migratePersonalPaid(value);}
@JavascriptInterface public String setPersonalPaid(String value){return repository.setPersonalPaid(value);}
@JavascriptInterface public String getInvoiceShareHandoff(){return store.ownerValue("invoice_share_handoff", "{}");}
@JavascriptInterface public String saveInvoice(String value) {
    try{String id=store.saveEditorRecord("invoice",new JSONObject(value));notifyCommitted();return id;}
    catch(Exception error){return editorFailure("invoice",value,error);}
}
@JavascriptInterface public String requestInvoiceIssue(String identifier) {
    try{JSONObject all=PixelMcpReadTools.effective(store.getState()),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),identifier);store.execute("request_invoice_issue",new JSONObject().put("request_id","ui-issue-"+UUID.randomUUID()).put("invoice_id",identifier).put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice",invoice))).put("confirmed",true));return "OK";}catch(Exception e){return "ERROR: "+e.getMessage();}
}
private static String pendingPhotoHash(JSONObject value)throws Exception {JSONObject copy=new JSONObject(value.toString());copy.remove("thumbnail_data");copy.remove("_capture_context");return PixelMcpWriteTools.hash(copy);}
private void noteLegacyPhotoOutcome(String value,String id,String outcome){try{String staged=pendingWorkPhotos.read();if(staged.isEmpty()||!pendingPhotoHash(new JSONObject(staged)).equals(pendingPhotoHash(new JSONObject(value))))return;store.putOwnerValue("pending_work_photo_outcome",new JSONObject().put("hash",pendingPhotoHash(new JSONObject(staged))).put("id",id).put("state",outcome).toString());}catch(Exception deferred){/* A failed cleanup receipt never changes a confirmed photo commit. */}}
@JavascriptInterface public String savePhoto(String value) {
    try { String id = store.savePhoto(new JSONObject(value));noteLegacyPhotoOutcome(value,id,"confirmed"); notifyCommitted(); return id; }
    catch (Exception error) {try{JSONObject original=new JSONObject(value);original.remove("thumbnail_data");original.remove("_capture_context");String request=original.optString("_photo_request_id"),saved=request.isEmpty()?"":store.ownerValue("photo-ui:"+request,"");if(!saved.isEmpty()){JSONObject receipt=new JSONObject(saved);if(receipt.getString("hash").equals(PixelMcpWriteTools.hash(original))){noteLegacyPhotoOutcome(value,receipt.getString("id"),"confirmed");return receipt.getString("id");}return "ERROR: COMMIT_UNKNOWN: Ursprüngliche Fotoangaben unverändert prüfen.";}}catch(Exception unreadable){return "ERROR: COMMIT_UNKNOWN: Fotobeleg noch nicht lesbar.";}if(error instanceof IllegalArgumentException||error instanceof JSONException){noteLegacyPhotoOutcome(value,"","not_committed");return "ERROR: NOT_COMMITTED: "+error.getMessage();}return "ERROR: COMMIT_UNKNOWN: Fotoergebnis ungeklärt; ursprüngliche Aktion erneut prüfen.";}
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
    try {String staged=pendingWorkPhotos.read();if(staged.isEmpty())return "OK";JSONObject original=new JSONObject(staged),outcome=new JSONObject(store.ownerValue("pending_work_photo_outcome","{}"));boolean resolved=pendingPhotoHash(original).equals(outcome.optString("hash"))&&java.util.Arrays.asList("confirmed","not_committed").contains(outcome.optString("state"));String request=original.optString("_photo_request_id");if(!resolved&&!request.isEmpty()){String raw=store.ownerValue("photo-ui:"+request,"");resolved=!raw.isEmpty()&&pendingPhotoHash(original).equals(new JSONObject(raw).optString("hash"));}if(!resolved)throw new IllegalStateException("Zuerst das Ergebnis der ursprünglichen Fotoaktion prüfen. Andere Arbeiten bleiben möglich.");pendingWorkPhotos.clear();store.putOwnerValue("pending_work_photo_outcome","{}");return "OK"; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String getPhotoData(String photoId) {
    try { return store.loadPhotoData(photoId); }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String removeEntity(String entity, String id) {
    try { String actionId = store.removeEntity(entity, id); notifyCommitted(); return actionId; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
@JavascriptInterface public String undoRemoveEntity(String entity, String id) {
    try { String result = store.undoRemoveEntity(entity, id); notifyCommitted(); return result; }
    catch (Exception error) { return "ERROR: " + error.getMessage(); }
}
}
