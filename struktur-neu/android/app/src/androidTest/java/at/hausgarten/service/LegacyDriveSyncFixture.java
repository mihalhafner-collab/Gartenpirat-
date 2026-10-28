package at.hausgarten.service;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Single app-wide synchronizer, also usable by the opted-in Pixel foreground service. */
final class LegacyDriveSyncFixture {
    static final Object LOCK=new Object();
    private static final ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduled;
    private static final SyncWakeup.Window wakeup=new SyncWakeup.Window();
    private static long scheduledGeneration;
    private static final java.util.concurrent.atomic.AtomicLong requestedGeneration=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicBoolean polling=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean running;
    private static final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
    static boolean running(){return running||claimed.get();}
    private static JSONObject backupOnlyState(Context context,JSONObject queue)throws Exception{
        JSONObject result=DriveRolePolicy.retiredResponse().put("ok",true).put("mode",DriveRolePolicy.role()).put("sync_contract_version",DriveRolePolicy.contractVersion()).put("configured",false).put("legacy_sync_enabled",false).put("automatic_enabled",false).put("syncing",false).put("legacy_queue_preserved",true).put("confirmation_scope","local_only").put("migration_required",!LocalAuthorityGuard.local(context)).put("device_action","openFullBackup");
        if(queue!=null){for(String key:new String[]{"pending","pending_upload","review","total","locally_committed"})if(queue.has(key))result.put(key,queue.get(key));}return result;
    }
    static JSONObject status(Context c,JSONObject queue)throws Exception {
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(c))return backupOnlyState(c,queue);
        SharedPreferences p=c.getSharedPreferences("haus_garten",0);
        JSONObject s=new JSONObject().put("configured",LegacyDriveAccountFixture.available(c)).put("transport",LegacyDriveAccountFixture.configured(c)?"direct_drive":"android_provider")
            .put("automatic_enabled",automaticEnabled(c)).put("manual_requested",p.getBoolean("drive_manual_sync_requested",false)).put("read_step",p.getString("drive_sync_read_step",""))
            .put("drive_sync_failed",p.getBoolean("drive_sync_failed",false)).put("last_error_code",p.getString("drive_last_error_code",""))
            .put("last_error",p.getString("drive_last_error","")).put("last_error_detail",p.getString("drive_last_error_detail","")).put("phase",p.getString("drive_sync_phase","idle"))
            .put("user_action_required",p.getBoolean("drive_user_action_required",false)).put("retry_count",p.getInt("drive_retry_count",0))
            .put("next_retry",p.getLong("drive_next_retry",0)).put("last_sync",p.getString("last_drive_sync",""))
            .put("last_sync_attempt",p.getString("last_sync_attempt","")).put("last_full_sync",p.getString("last_full_sync",""))
            .put("confirmation_scope",p.getString("drive_confirmation_scope","local_only")).put("cloud_confirmed",p.getBoolean("drive_cloud_confirmed",false));
        boolean rest=LegacyDriveAccountFixture.configured(c),permission=rest||LegacyDriveAccountFixture.providerPermission(c);
        s.put("persisted_tree_permission",!rest&&permission).put("trash_cleanup_pending",p.getInt("trash_cleanup_pending",0)).put("trash_cleanup_error",p.getString("trash_cleanup_error",""));
        if(s.optString("last_error_code").isEmpty()&&s.optString("last_error").contains("ursprüngliche Drive-Gesamtstand"))s.put("last_error_code","LEGACY_SOURCE_CHANGED");
        if(s.optBoolean("configured")&&!permission)s.put("drive_sync_failed",true).put("user_action_required",true).put("last_error_code","PERMISSION_REQUIRED").put("last_error",SyncPolicy.message("PERMISSION_REQUIRED"));
        return SyncPolicy.describe(s,queue,running,System.currentTimeMillis());
    }
    static boolean prepareExplicit(Context c,String id)throws Exception {
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(c))return false;
        SharedPreferences p=c.getSharedPreferences("haus_garten",0);
        if(!LegacyDriveAccountFixture.available(c))return false;
        String marker="drive_explicit_request:"+PixelMcpWriteTools.hash(id);
        if(!p.getBoolean(marker,false)&&!p.edit().putBoolean(marker,true).putBoolean("drive_manual_sync_requested",true).putInt("drive_retry_count",0).putBoolean("drive_user_action_required",false).putLong("drive_next_retry",0).commit())throw new IOException("Abgleichanforderung konnte nicht gesichert werden.");
        return true;
    }
    static JSONObject requestExplicit(Context c,String id)throws Exception {
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(c))return backupOnlyState(c,null);
        if(LegacyDriveAccountFixture.available(c)&&!LegacyDriveAccountFixture.configured(c)&&!LegacyDriveAccountFixture.providerPermission(c))return new JSONObject().put("state","action_required").put("next_action","connect_drive").put("message",SyncPolicy.message("PERMISSION_REQUIRED"));
        if(!prepareExplicit(c,id))return new JSONObject().put("state","not_connected").put("next_action","connect_drive").put("message",SyncPolicy.message("NOT_CONNECTED"));
        request(c,0);return new JSONObject().put("state","requested").put("cloud_confirmed",false).put("message","Abgleich angefordert. Das Ergebnis wird gesondert geprüft.");
    }
    static boolean automaticAllowed(Context c)throws Exception{return DriveRolePolicy.businessSyncAllowed()&&(automaticEnabled(c)||c.getSharedPreferences("haus_garten",0).getBoolean("drive_manual_sync_requested",false))&&SyncPolicy.automaticAllowed(status(c,new JSONObject()),System.currentTimeMillis());}
    static boolean automaticEnabled(Context c){return DriveRolePolicy.businessSyncAllowed()&&LocalAuthorityGuard.legacySyncAllowed(c)&&c.getSharedPreferences("haus_garten",0).getBoolean("drive_automatic_enabled",true);}
    static boolean hasNetwork(Context c){
        android.net.ConnectivityManager manager=c.getSystemService(android.net.ConnectivityManager.class);if(manager==null)return false;
        android.net.Network network=manager.getActiveNetwork();android.net.NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
        return caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    static void noteRequest(){requestedGeneration.incrementAndGet();}
    static void request(Context context,long delay){if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(context))return;noteRequest();scheduleRequest(context,delay);}
    private static synchronized void scheduleRequest(Context context,long delay){
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(context))return;
        long now=System.currentTimeMillis(),previous=wakeup.due();
        long due=wakeup.request(now,delay,context.getSharedPreferences("haus_garten",0).getLong("drive_next_retry",0));
        if(scheduled!=null&&!scheduled.isDone()&&previous==due)return;
        DriveSyncJob.schedule(context,Math.max(0,due-now));
        Context app=context.getApplicationContext();if(scheduled!=null)scheduled.cancel(false);long generation=++scheduledGeneration;
        scheduled=EXECUTOR.schedule(()->{
            synchronized(LegacyDriveSyncFixture.class){if(generation!=scheduledGeneration)return;scheduled=null;wakeup.clear();}
            if(!DriveRolePolicy.businessSyncAllowed())return;
            try(PixelLocalStore store=new PixelLocalStore(app)){
            if(automaticAllowed(app)&&hasNetwork(app))run(app,store);
        }catch(Exception ignored){/* run persists a bounded, user-facing failure; local actions survive. */}},Math.max(0,due-now),TimeUnit.MILLISECONDS);
    }
    /** Active-use probe only. No service is enabled, no timestamp/receipt is fabricated on unchanged metadata. */
    static void poll(Context context){
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(context))return;
        if(BuildConfig.UI_PREVIEW||running||!polling.compareAndSet(false,true))return;Context app=context.getApplicationContext();
        EXECUTOR.execute(()->{if(!DriveRolePolicy.businessSyncAllowed()){polling.set(false);return;}try(PixelLocalStore store=new PixelLocalStore(app)){
            if(!automaticAllowed(app)||!hasNetwork(app))return;
            if(!LegacyDriveAccountFixture.configured(app)){long last=lastAttempt(app);if(last>=SyncWakeup.FULL_CHECK_MS)request(app,0);return;}
            LegacyDriveRestFixture client=LegacyDriveAccountFixture.client(app,store);String observed;
            try{observed=client.inventoryFingerprint();}catch(Exception unavailable){recordFailure(app,unavailable);return;}
            SharedPreferences p=app.getSharedPreferences("haus_garten",0);
            if(SyncWakeup.fullCheck(observed,client.checkpoint("poll-inventory"),store.pendingActionsForDrive().length()>0,
                p.getBoolean("drive_manual_sync_requested",false)||p.getInt("trash_cleanup_pending",0)>0||!p.getBoolean("drive_cloud_confirmed",false)&&p.getInt("drive_conflict_count",0)==0,p.getBoolean("drive_sync_failed",false),lastAttempt(app)))
                run(app,store,new LegacyDriveDirectFixture(app,client),Settings.Secure.getString(app.getContentResolver(),Settings.Secure.ANDROID_ID));
        }catch(Exception ignored){/* Full run owns durable failure/backoff; probe never confirms anything. */}finally{polling.set(false);}});
    }
    private static long lastAttempt(Context c){try{return Math.max(0,System.currentTimeMillis()-java.time.Instant.parse(c.getSharedPreferences("haus_garten",0).getString("last_sync_attempt","")).toEpochMilli());}catch(Exception absent){return Long.MAX_VALUE;}}
    static JSONObject run(Context context,PixelLocalStore store)throws Exception{
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(context))return backupOnlyState(context,null);
        if(LegacyDriveAccountFixture.configured(context))return run(context,store,new LegacyDriveDirectFixture(context,LegacyDriveAccountFixture.client(context,store)),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
        String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty()){SyncIoFailure failure=new SyncIoFailure("FOLDER_NOT_SELECTED");recordFailure(context,failure);throw failure;}
        if(!LegacyDriveAccountFixture.providerPermission(context))throw new SecurityException("Drive-Ordnerfreigabe fehlt. Bestehenden Ordner erneut auswählen.");
        return run(context,store,new Files(context,Uri.parse(tree),prefs),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
    }
    // Standalone compatibility engine with an explicitly supplied Files port. App/MCP use
    // only the guarded two-argument entrypoint; no runtime/production QA switch enables this.
    static JSONObject run(Context context,PixelLocalStore store,Files files,String device)throws Exception{
        if(!LocalAuthorityGuard.legacySyncAllowed(context))return backupOnlyState(context,null);
        // Activity, JobScheduler and MCP may request the same run concurrently.
        // Joining must not wait for the network lock and then repeat the entire I/O.
        // This is no acknowledgement: the active run alone checks/commits receipts.
        if(!claimed.compareAndSet(false,true))return new JSONObject().put("ok",true).put("coalesced",true).put("cloud_confirmed",false);
        long requestAtStart=requestedGeneration.get();
        try{
        synchronized(LOCK){
            if(!LocalAuthorityGuard.legacySyncAllowed(context))return backupOnlyState(context,null);
            running=true;SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            try{
                prefs.edit().putString("drive_sync_phase","reading").putString("last_sync_attempt",java.time.Instant.now().toString()).commit();
                files.beginRead();readStep(prefs,"inventory");String inventoryAtStart=files.inventoryFingerprint();
                String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty()&&!files.cloudVerified())throw new SyncIoFailure("FOLDER_NOT_SELECTED");
                readStep(prefs,"legacy_master");String incoming=files.read("HausGarten-Laptop.json");
                if(incoming==null)throw new SyncIoFailure("REMOTE_MASTER_MISSING");
                JSONObject legacy=new JSONObject(incoming);DriveJournalRules.validateSnapshot(legacy);
                if(device==null||!device.matches("[a-zA-Z0-9_-]+"))throw new SyncIoFailure("DEVICE_ID_MISSING");
                readStep(prefs,"publication_chain");JSONObject snapshot=files.readPrimary(legacy,device);store.seedServiceCatalog(snapshot);snapshot.put("drive_publisher_id",device);
                String name="HausGarten-Mobile-"+device+".json";
                LinkedHashMap<String,String> originals=new LinkedHashMap<>();
                JSONArray own=new JSONArray(),others=new JSONArray();
                readStep(prefs,"mobile_journals");for(Doc doc:files.documents()){
                    if(!doc.name.startsWith("HausGarten-Mobile-")||!doc.name.endsWith(".json"))continue;
                    String text=files.read(doc.uri);JSONObject pkg=new JSONObject(text);JSONArray actions=pkg.getJSONArray("actions");
                    if(doc.name.equals(name)){originals.put(doc.id,text);own=DriveJournalRules.union(own,actions);}else others=DriveJournalRules.union(others,actions);
                }
                files.endRead();readStep(prefs,"");JSONArray allPending=store.pendingActionsForDrive(),pending=new JSONArray(),deferred=new JSONArray();
                for(int i=0;i<allPending.length();i++){JSONObject a=allPending.getJSONObject(i);if(componentReady(snapshot,a))pending.put(a);else deferred.put(a);}
                JSONArray attachments=store.pendingPhotoAttachments(pending);
                prefs.edit().putString("drive_sync_phase","media").commit();
                Map<String,Exception> blockedPhotos=new HashMap<>();
                Map<String,Exception> blockedDocuments=new HashMap<>();
                for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.getJSONObject("payload");if(!"offer_finalize".equals(a.optString("kind")))continue;
                    try{files.writeDocumentVerified(p,new BusinessDocumentStore(context).read(p));}catch(Exception error){blockedDocuments.put(a.getString("client_id"),error);}}
                boolean urgentChanges=false;for(int i=0;i<pending.length();i++)if(!"photo".equals(pending.getJSONObject(i).optString("kind"))){urgentChanges=true;break;}
                Set<String> selected=SyncMediaPlan.select(attachments,prefs.getString("drive_media_cursor",""),urgentChanges);int mediaProgress=0,deferredMedia=0;JSONArray batch=new JSONArray();
                for(int i=0;i<attachments.length();i++){
                    JSONObject metadata=attachments.getJSONObject(i);String photoId=metadata.getString("photo_id"),hash=metadata.optString("content_sha256");
                    if(metadata.optBoolean("missing")||!hash.matches("[0-9a-f]{64}")){blockedPhotos.put(photoId,new LegacyDriveRestFixture.Failure("PHOTO_ORIGINAL_REQUIRED","Foto-Original fehlt lokal. Original wieder bereitstellen; andere Änderungen werden weiter abgeglichen.",0,true));continue;}
                    if(!selected.contains(hash)){deferredMedia++;blockedPhotos.put(photoId,new LegacyDriveRestFixture.Failure("MEDIA_TRANSFER_PENDING","Foto folgt automatisch im nächsten Übertragungsabschnitt.",0,false));continue;}
                    batch.put(metadata);
                }
                Map<String,Exception> mediaFailures=files.writeMediaBatchVerified(batch,store.photoAttachmentStore());Set<String> transferred=new HashSet<>();
                for(int i=0;i<batch.length();i++){JSONObject metadata=batch.getJSONObject(i);String photoId=metadata.getString("photo_id");Exception failure=mediaFailures.get(photoId);
                    if(failure!=null)blockedPhotos.put(photoId,failure);else{store.markPhotoAttachmentTransferred(photoId);transferred.add(metadata.getString("content_sha256"));}}
                mediaProgress=transferred.size();
                // Failed originals cannot occupy every slot forever. This cursor is not a receipt.
                String cursor=SyncMediaPlan.nextCursor(attachments,prefs.getString("drive_media_cursor",""),selected);if(!cursor.isEmpty())prefs.edit().putString("drive_media_cursor",cursor).apply();
                prefs.edit().putString("drive_sync_phase","journal").commit();
                JSONArray combined=DriveJournalRules.union(own,pending);
                // Preserve all existing action IDs, including actions in same-name duplicate files.
                if(!PixelMcpWriteTools.hash(combined).equals(PixelMcpWriteTools.hash(own))){
                    JSONObject pkg=new JSONObject().put("format",1).put("device_id",device).put("exported_at",java.time.Instant.now().toString()).put("actions",combined).put("journal_preserved",true);
                    files.writeVerified(name,pkg,originals);
                }
                // Even an unchanged journal must be read back before recording provider delivery.
                JSONArray delivered=files.readActions(name);DriveJournalRules.union(delivered,combined);
                Set<String> deliveredIds=DriveJournalRules.ids(new JSONArray());for(int i=0;i<delivered.length();i++)deliveredIds.add(delivered.getJSONObject(i).getString("client_id"));
                for(int i=0;i<combined.length();i++)if(!deliveredIds.contains(combined.getJSONObject(i).getString("client_id")))throw new IOException("Drive hat nicht alle Änderungen zurückgegeben. Die lokale Warteschlange bleibt erhalten.");
                String chat=files.read("HausGarten-ChatGPT.json");JSONArray chatActions=chat==null?new JSONArray():new JSONObject(chat).getJSONArray("actions");
                JSONArray deliveredActions=DriveJournalRules.union(others,chatActions,combined),all=DriveJournalRules.union(deliveredActions,deferred);
                JSONArray ready=new JSONArray(),mediaErrors=new JSONArray();
                for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);Exception failure="photo".equals(a.optString("kind"))?blockedPhotos.get(a.getJSONObject("payload").optString("id")):null;
                    if(failure==null)failure=blockedDocuments.get(a.getString("client_id"));
                    if(!componentReady(snapshot,a)){mediaErrors.put(new JSONObject().put("client_id",a.getString("client_id")).put("kind",a.getString("kind")).put("code","SYNC_COMPONENT_UPDATE_REQUIRED").put("error","Unbekannte Pixel-Verarbeitung; App-Aktualisierung prüfen. Lokale Aktion bleibt erhalten; andere Bereiche werden weiter abgeglichen."));continue;}
                    if(failure==null)ready.put(a);else mediaErrors.put(new JSONObject().put("client_id",a.getString("client_id")).put("kind",a.getString("kind")).put("code",failure instanceof LegacyDriveRestFixture.Failure?((LegacyDriveRestFixture.Failure)failure).code:"MEDIA_TRANSFER_PENDING").put("error",friendly(failure)));}
                JSONArray errors=DriveJournalRules.apply(snapshot,ready,(trial,event)->{
                    JSONObject prepared=InvoiceIssueRules.prepare(trial,event,java.time.LocalDate.now(java.time.ZoneId.of("Europe/Vienna")));
                    JSONObject reservation=files.reserveInvoice(trial,event);String key="issued-preparation:"+event.getString("client_id");
                    String saved=store.ownerValue(key,"");JSONObject document;
                    if(!saved.isEmpty()){JSONObject cached=new JSONObject(saved);if(!PixelMcpWriteTools.hash(event).equals(cached.getString("action_sha256"))||!PixelMcpWriteTools.hash(reservation).equals(cached.getString("reservation_sha256")))throw new IOException("Unveränderliche Abschlussvorbereitung weicht ab.");document=cached.getJSONObject("document");}
                    else{prepared.put("invoice_number",reservation.getString("invoice_number"));if(!prepared.optString("correction_of").isEmpty())prepared.put("correction_original_number",PixelMcpWriteTools.find(trial.getJSONArray("invoices"),prepared.getString("correction_of")).optString("invoice_number"));
                        document=new BusinessDocumentStore(context).createInvoice(prepared);store.putOwnerValue(key,new JSONObject().put("action_sha256",PixelMcpWriteTools.hash(event)).put("reservation_sha256",PixelMcpWriteTools.hash(reservation)).put("document",document).toString());}
                    files.writeDocumentVerified(document,new BusinessDocumentStore(context).read(document));for(Iterator<String> keys=document.keys();keys.hasNext();){String k=keys.next();prepared.put(k,document.get(k));}
                    InvoiceIssueRules.commit(trial,prepared,reservation,event.getString("created_at"));
                });
                for(int i=0;i<mediaErrors.length();i++)errors.put(mediaErrors.getJSONObject(i));snapshot.put("drive_journal_errors",errors);
                snapshot.put("drive_review_actions",DriveJournalRules.reviewActions(snapshot,all));
                store.recordSnapshotPhotoAttachments(snapshot);store.documentReferences(snapshot);
                JSONArray missingDocuments=new JSONArray();
                for(String collection:new String[]{"invoices","offers"})for(int i=0;i<snapshot.getJSONArray(collection).length();i++){JSONObject doc=snapshot.getJSONArray(collection).getJSONObject(i);if(doc.optString("pdf_storage_name").isEmpty())continue;
                    try{files.ensureDocument(doc,new BusinessDocumentStore(context));}
                    catch(Exception unavailable){missingDocuments.put(new JSONObject().put("entity_type",collection.equals("invoices")?"invoice":"offer").put("entity_id",doc.getString("id")).put("code","DOCUMENT_ORIGINAL_REQUIRED").put("message","Original-PDF fehlt oder ist noch nicht bestätigt; andere Änderungen bleiben nutzbar."));}}
                snapshot.put("drive_media_errors",missingDocuments);
                // Commit data before dropping acknowledged local queue rows. Crash retries are idempotent.
                snapshot.put("sync_primary","pixel").put("drive_storage","snapshot_and_journals").put("drive_delivered_ids",new JSONArray(deliveredIds)).put("drive_publisher_id",device);
                prefs.edit().putString("drive_sync_phase","publishing").commit();
                JSONObject publication=files.publish(snapshot,deliveredActions);
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Abgleich unterbrochen; Warteschlange bleibt erhalten.");
                if(files.cloudVerified())snapshot.put("drive_cloud_receipts",publication.getJSONObject("receipts")).put("drive_last_publication",publication.getString("publication_id"));
                boolean mediaComplete=snapshot.optJSONArray("drive_media_errors")==null||snapshot.getJSONArray("drive_media_errors").length()==0;
                String now=java.time.Instant.now().toString();
                boolean[] confirmedFully={false};
                SyncCommitRules.Result commit=SyncCommitRules.commit(snapshot,new SyncCommitRules.Storage(){
                    public void commitSnapshot(JSONObject saved,Set<String> ids)throws Exception{store.commitSnapshot(saved,ids);}
                    public boolean saveStatus()throws Exception{
                        // The standalone legacy engine must inspect its actual journal,
                        // never the final app's retired/zero-active-sync status projection.
                        JSONObject rawQueue=DriveJournalRules.queueStatus(snapshot,DriveJournalRules.union(store.pendingActions(),snapshot.optJSONArray("drive_review_actions")));
                        confirmedFully[0]=files.cloudVerified()&&mediaComplete&&errors.length()==0&&rawQueue.getInt("total")==0;
                        return prefs.edit().putString("last_drive_sync",now)
                            .putString("last_sync_attempt",now).putBoolean("drive_sync_failed",false).putString("drive_last_error","").putString("drive_last_error_code","").putString("drive_last_error_detail","").putString("drive_sync_phase","idle")
                            .putString("drive_confirmation_scope",files.cloudVerified()?"drive_server_manifest":"provider_readback").putBoolean("drive_cloud_confirmed",confirmedFully[0]).putString("last_full_sync",confirmedFully[0]?now:prefs.getString("last_full_sync",""))
                            .putBoolean("drive_user_action_required",false).putLong("drive_next_retry",0).putInt("drive_duplicate_files",files.duplicates).putInt("drive_conflict_count",errors.length()).putInt("drive_retry_count",0).commit();
                    }
                });
                boolean statusSaved=commit.statusSaved,fully=statusSaved&&confirmedFully[0];
                if(!statusSaved){fully=false;recordFailure(context,new SyncIoFailure("STATUS_NOT_SAVED"));}
                store.markUploaded(pending);
                Set<String> confirmed=commit.confirmed;
                store.cleanupConfirmedPhotoDeletes(pending,confirmed,snapshot);
                JSONObject cleanup=TrashMediaCleanup.run(store,files,snapshot);
                prefs.edit().putInt("trash_cleanup_pending",cleanup.getInt("pending")).putString("trash_cleanup_error",cleanup.getString("error_code")).commit();
                if(cleanup.getInt("pending")>0){fully=false;prefs.edit().putBoolean("drive_cloud_confirmed",false).commit();if(cleanup.getInt("completed")>0)scheduleRequest(context,0);}
                if(files.cloudVerified()&&statusSaved){
                    try{OwnerReceiptRetention.afterConfirmedSync(store,snapshot,System.currentTimeMillis());}
                    catch(Exception retentionDeferred){store.putOwnerValue("receipt_retention_summary",new JSONObject().put("removed",0).put("state","deferred").toString());}
                }
                if(mediaProgress>0&&deferredMedia>0)scheduleRequest(context,0);
                boolean follow=mediaProgress>0&&deferredMedia>0||cleanup.getInt("pending")>0&&cleanup.getInt("completed")>0;
                Set<String> captured=DriveJournalRules.ids(allPending);
                JSONArray later=store.pendingActionsForDrive();
                for(int i=0;i<later.length();i++)if(!captured.contains(later.getJSONObject(i).getString("client_id"))){follow=true;scheduleRequest(context,0);break;}
                // Only the inventory observed BEFORE applying actions may be remembered.
                // A remote change during this run must still trigger the next probe.
                files.rememberInventory(inventoryAtStart);
                long undoRemaining=store.remainingDeleteUndoMs();
                if(undoRemaining>0){follow=true;scheduleRequest(context,undoRemaining+200);}
                if(!follow)prefs.edit().putBoolean("drive_manual_sync_requested",false).apply();
                return new JSONObject().put("ok",true).put("transport_ok",true).put("needs_review",errors.length()>0).put("state",store.getState()).put("accepted",confirmed.size()).put("chatgpt_imported",chatActions.length())
                    .put("journal_errors",errors).put("error","").put("status_warning",statusSaved?"":"STATUS_NOT_SAVED")
                    .put("confirmation_scope",files.cloudVerified()?"drive_server_manifest":"provider_readback").put("cloud_confirmed",fully).put("duplicate_files",files.duplicates);
            }catch(Exception failure){recordFailure(context,failure);throw failure;}
            finally{files.endRead();running=false;}
        }
        }finally{claimed.set(false);if(requestedGeneration.get()>requestAtStart)scheduleRequest(context,0);}
    }
    private static void readStep(SharedPreferences prefs,String step){prefs.edit().putString("drive_sync_read_step",step).apply();}
    static void recordFailure(Context context,Exception failure){
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
        try{
            JSONObject d=SyncFailureRules.recordFailure(failure,prefs.getInt("drive_retry_count",0),System.currentTimeMillis());
            prefs.edit().putBoolean("drive_sync_failed",d.getBoolean("drive_sync_failed")).putBoolean("drive_cloud_confirmed",false)
                .putBoolean("drive_user_action_required",d.getBoolean("user_action_required")).putLong("drive_next_retry",d.getLong("next_retry"))
                .putString("last_sync_attempt",java.time.Instant.now().toString()).putString("drive_last_error_code",d.getString("last_error_code"))
                .putString("drive_last_error",d.getString("last_error")).putString("drive_last_error_detail",d.getString("last_error_detail"))
                .putInt("drive_retry_count",d.getInt("retry_count")).commit();
            if(d.getBoolean("schedule_retry"))scheduleRequest(context,d.getLong("retry_delay"));else prefs.edit().putBoolean("drive_manual_sync_requested",false).apply();
        }catch(JSONException impossible){throw new IllegalStateException("Syncfehlerentscheidung ungültig.",impossible);}
        catch(Exception impossible){throw new IllegalStateException("Syncfehlerentscheidung fehlt.",impossible);}
    }
    private static boolean componentReady(JSONObject snapshot,JSONObject action){return DriveJournalRules.componentReady(snapshot,action.optString("kind"));}
    static String failureCode(Exception error){return SyncFailureRules.failureCode(error);}
    static String friendly(Exception error){return SyncFailureRules.friendly(error);}
    static boolean fetchPhoto(Context context,JSONObject metadata,PhotoAttachmentStore local)throws Exception{
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(context))throw new IOException("LOCAL_ORIGINAL_MISSING");
        if(LegacyDriveAccountFixture.configured(context))try(PixelLocalStore store=new PixelLocalStore(context)){
            byte[] bytes=new LegacyDriveDirectFixture(context,LegacyDriveAccountFixture.client(context,store)).readPhotoVerified(metadata);
            android.database.sqlite.SQLiteDatabase database=store.getWritableDatabase();database.beginTransaction();
            try{JSONObject current=PixelMcpReadTools.effective(store.getState());
                if(!TrashRules.referenced(current,store.pendingActions(),metadata.optString("storage_name")))throw new IOException("Foto wird nicht mehr verwendet. Kein gelöschtes Original erneut gespeichert.");
                local.storeExpected(bytes,metadata);database.setTransactionSuccessful();return true;
            }finally{database.endTransaction();}
        }
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty())return false;
        synchronized(LOCK){Files files=new Files(context,Uri.parse(tree),prefs);byte[] bytes=files.readPhotoVerified(metadata);local.storeExpected(bytes,metadata);return true;}
    }
    static byte[] fetchDocument(Context context,JSONObject metadata)throws Exception{
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(context))throw new IOException("LOCAL_ORIGINAL_MISSING");
        if(LegacyDriveAccountFixture.configured(context))try(PixelLocalStore store=new PixelLocalStore(context)){return new LegacyDriveDirectFixture(context,LegacyDriveAccountFixture.client(context,store)).readDocumentVerified(metadata);}
        synchronized(LOCK){
            SharedPreferences p=context.getSharedPreferences("haus_garten",0);String tree=p.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Dokument fehlt lokal und Drive ist nicht verbunden.");return new Files(context,Uri.parse(tree),p).readDocumentVerified(metadata);}
    }
    static byte[] fetchThumbnail(Context c,String name)throws Exception{
        if(!DriveRolePolicy.businessSyncAllowed()||!LocalAuthorityGuard.legacySyncAllowed(c))throw new IOException("LOCAL_ORIGINAL_MISSING");
        if(!name.matches("[0-9a-f]{64}\\.jpg"))throw new IOException("Ungültige Vorschaureferenz.");
        if(LegacyDriveAccountFixture.configured(c))try(PixelLocalStore s=new PixelLocalStore(c)){return new LegacyDriveDirectFixture(c,LegacyDriveAccountFixture.client(c,s)).readThumbnailVerified(name);}
        synchronized(LOCK){
            SharedPreferences p=c.getSharedPreferences("haus_garten",0);String tree=p.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Drive nicht verbunden.");return new Files(c,Uri.parse(tree),p).readThumbnailVerified(name);}
    }
    static final class Doc{
        final String id,name;final Uri uri;final long modified;
        Doc(String id,String name,Uri uri,long modified){this.id=id;this.name=name;this.uri=uri;this.modified=modified;}
    }
    static class Files implements TrashMediaCleanup.Files{
        final Context context;final ContentResolver resolver;final Uri tree;final SharedPreferences prefs;int duplicates;
        Files(Context c,Uri t,SharedPreferences p){context=c;resolver=c.getContentResolver();tree=t;prefs=p;}
        private ArrayList<Doc> readInventory;private boolean reading;
        private final VerifiedSyncCache readCache=new VerifiedSyncCache(4*1024*1024);
        void beginRead(){endRead();reading=true;}
        void endRead(){reading=false;readInventory=null;readCache.clear();}
        boolean cloudVerified(){return false;}
        String inventoryFingerprint()throws Exception{return "";}
        void rememberInventory(String fingerprint){}
        Map<String,Exception> writeMediaBatchVerified(JSONArray batch,PhotoAttachmentStore local)throws Exception{
            Map<String,Exception> failures=new HashMap<>();Set<String> originals=new HashSet<>(),thumbs=new HashSet<>();
            for(int i=0;i<batch.length();i++){JSONObject m=batch.getJSONObject(i);try{
                if(originals.add(m.getString("content_sha256")))writePhotoVerified(m,local);
                if(thumbs.add(m.optString("thumbnail_storage_name")))writeThumbnailVerified(m,local);
            }catch(Exception error){failures.put(m.getString("photo_id"),error);originals.remove(m.getString("content_sha256"));thumbs.remove(m.optString("thumbnail_storage_name"));}}
            return failures;
        }
        void ensureDocument(JSONObject metadata,BusinessDocumentStore local)throws Exception {
            byte[] bytes;try{bytes=local.read(metadata);}catch(IOException absent){bytes=readDocumentVerified(metadata);local.store(bytes,metadata);}writeDocumentVerified(metadata,bytes);
        }
        private Uri documentDirectory(boolean create)throws Exception{
            ArrayList<Doc> found=matches("HausGarten-Dokumente");if(found.size()>1)throw new IOException("Mehrere Dokumentordner; nichts überschrieben.");if(!found.isEmpty())return found.get(0).uri;if(!create)throw new IOException("Dokumentordner fehlt.");
            Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));Uri d=DocumentsContract.createDocument(resolver,root,DocumentsContract.Document.MIME_TYPE_DIR,"HausGarten-Dokumente");if(d==null)throw new IOException("Dokumentordner nicht beschreibbar.");return d;
        }
        private void checkDocument(byte[] bytes,JSONObject m)throws Exception{
            if(!m.optString("pdf_sha256").matches("[0-9a-f]{64}")||!m.optString("pdf_storage_name").equals(m.getString("pdf_sha256")+".pdf")||bytes.length!=m.optLong("pdf_byte_size")||!PhotoAttachmentStore.sha256(bytes).equals(m.getString("pdf_sha256"))||bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("Dokument-Prüfsumme stimmt nicht überein.");
        }
        byte[] readDocumentVerified(JSONObject metadata)throws Exception{
            String name=metadata.optString("pdf_storage_name");if(!name.matches("[0-9a-f]{64}\\.pdf"))throw new IOException("Ungültige Dokumentreferenz.");ArrayList<Doc> found=photoMatches(documentDirectory(false),name);if(found.size()!=1)throw new IOException("Original-PDF ist nicht eindeutig verfügbar.");byte[] bytes=readBytes(found.get(0).uri);checkDocument(bytes,metadata);return bytes;
        }
        public void deleteDocumentFileVerified(JSONObject metadata)throws Exception{
            String name=metadata.optString("pdf_storage_name"),hash=metadata.optString("pdf_sha256");if(!hash.matches("[0-9a-f]{64}")||!name.equals(hash+".pdf"))throw new IOException("Ungültige Dokument-Löschreferenz.");
            ArrayList<Doc> folders=matches("HausGarten-Dokumente");if(folders.isEmpty())return;if(folders.size()!=1)throw new IOException("Dokumentordner nicht eindeutig.");
            Uri folder=folders.get(0).uri;ArrayList<Doc> found=photoMatches(folder,name);if(found.size()>1)throw new IOException("Dokumentdatei nicht eindeutig.");if(found.isEmpty())return;
            checkDocument(readBytes(found.get(0).uri),metadata);if(!DocumentsContract.deleteDocument(resolver,found.get(0).uri)||!photoMatches(folder,name).isEmpty())throw new IOException("Dokumentlöschung noch nicht zurückgelesen.");
        }
        void writeDocumentVerified(JSONObject metadata,byte[] bytes)throws Exception{
            checkDocument(bytes,metadata);String name=metadata.getString("pdf_storage_name");Uri folder=documentDirectory(true);ArrayList<Doc> found=photoMatches(folder,name);if(!found.isEmpty()){if(found.size()!=1)throw new IOException("PDF-Original ist nicht eindeutig. Keine Datei verändert.");checkDocument(readBytes(found.get(0).uri),metadata);return;}
            Uri temporary=DocumentsContract.createDocument(resolver,folder,"application/pdf",".uploading-"+UUID.randomUUID());if(temporary==null)throw new IOException("Dokument ist nicht beschreibbar.");boolean complete=false,publicationAttempted=false;Uri own=temporary;
            try{try(OutputStream out=resolver.openOutputStream(temporary,"wt")){if(out==null)throw new IOException("Dokument ist nicht beschreibbar.");out.write(bytes);}checkDocument(readBytes(temporary),metadata);publicationAttempted=true;Uri finalUri=DocumentsContract.renameDocument(resolver,temporary,name);if(finalUri==null)throw new IOException("Dokument konnte nicht benannt werden.");own=finalUri;checkDocument(readDocumentVerified(metadata),metadata);complete=true;}
            finally{if(!complete&&!publicationAttempted)try{DocumentsContract.deleteDocument(resolver,own);}catch(Exception ignored){}}
        }
        JSONObject reserveInvoice(JSONObject state,JSONObject action)throws Exception{throw new PixelMcpWriteTools.Rejected("DIRECT_DRIVE_REQUIRED","Rechnungsabschluss benötigt direkte Google-Drive-Verbindung mit atomarem Nummernvergleich. Entwurf bleibt erhalten.");}
        JSONObject readPrimary(JSONObject legacy,String device)throws Exception {
            return readPrimary(legacy,device,false);
        }
        JSONObject readPrimary(JSONObject legacy,String device,boolean evidenceOnly)throws Exception {
            Map<String,Doc> index=new LinkedHashMap<>();for(Doc d:documents())index.put(d.id,d);
            return DrivePrimaryState.recover(legacy,device,new DrivePrimaryState.Storage(){
                public Map<String,String> commits(){Map<String,String> out=new LinkedHashMap<>();for(Doc d:index.values())if(d.name.startsWith("HausGarten-Commit-"))out.put(d.id,d.name);return out;}
                public byte[] read(String id)throws Exception{Doc d=index.get(id);if(d!=null)return Files.this.readImmutable(d);throw new IOException("Veröffentlichter Gesamtstand fehlt; keine leere Wiederanlage.");}
                public byte[] readState(JSONObject manifest)throws Exception{return Files.this.readPublishedState(index,manifest);}
                public void readCommits(Map<String,String> names,DrivePrimaryState.CommitVisitor visitor)throws Exception{Files.this.readCommitFiles(index,names,visitor);}
            },evidenceOnly);
        }
        byte[] readPublishedState(Map<String,Doc> index,JSONObject manifest)throws Exception {
            Map<String,String> names=new LinkedHashMap<>();for(Doc d:index.values())names.put(d.id,d.name);
            return ImmutableStateReference.read(names,manifest,id->readImmutable(index.get(id)));
        }
        void readCommitFiles(Map<String,Doc> index,Map<String,String> names,DrivePrimaryState.CommitVisitor visitor)throws Exception{
            for(Map.Entry<String,String> entry:names.entrySet()){Doc doc=index.get(entry.getKey());if(doc==null)throw new IOException("Veröffentlichter Beleg fehlt.");visitor.accept(entry.getKey(),entry.getValue(),readImmutable(doc));}
        }
        byte[] readImmutable(Doc doc)throws Exception {
            byte[] cached=reading?readCache.get(doc.id):null;if(cached!=null)return cached;
            byte[] bytes=read(doc.uri).getBytes(StandardCharsets.UTF_8);
            if(reading)readCache.remember(doc.id,doc.name,bytes);return bytes;
        }
        Doc immutable(String name,byte[] bytes)throws Exception {
            endRead();
            ArrayList<Doc> found=matches(name);if(found.size()>1)throw new IOException("Mehrdeutige Veröffentlichung; Originale erhalten.");
            if(found.size()==1){if(!Arrays.equals(read(found.get(0).uri).getBytes(StandardCharsets.UTF_8),bytes))throw new IOException("Unveränderliche Veröffentlichung hat anderen Inhalt.");return found.get(0);}
            Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            Uri temp=DocumentsContract.createDocument(resolver,root,"application/json",".uploading-state-"+UUID.randomUUID());if(temp==null)throw new IOException("Gesamtstand nicht beschreibbar.");
            // Never delete a successfully written object after a rename/readback ambiguity.
            try(OutputStream out=resolver.openOutputStream(temp,"wt")){if(out==null)throw new IOException("Gesamtstand nicht beschreibbar.");out.write(bytes);}
            if(!Arrays.equals(read(temp).getBytes(StandardCharsets.UTF_8),bytes))throw new IOException("Gesamtstand-Lesekontrolle fehlgeschlagen.");
            Uri finalUri=DocumentsContract.renameDocument(resolver,temp,name);if(finalUri==null)throw new IOException("Gesamtstand-Veröffentlichung unbestätigt.");
            if(!Arrays.equals(read(finalUri).getBytes(StandardCharsets.UTF_8),bytes))throw new IOException("Gesamtstand-Lesekontrolle fehlgeschlagen.");
            return new Doc(DocumentsContract.getDocumentId(finalUri),name,finalUri,0);
        }
        JSONObject publish(JSONObject snapshot,JSONArray actions)throws Exception {
            endRead();
            JSONObject current=readPrimary(new JSONObject(read("HausGarten-Laptop.json")),snapshot.getString("drive_publisher_id"));DrivePrimaryState.checkParent(snapshot,current);
            byte[] state=DrivePublication.stateBytes(snapshot);String sha=LegacyDriveRestFixture.sha256(state);
            if(sha.equals(snapshot.optString("drive_parent_state_sha256")))return new JSONObject().put("publication_id",snapshot.getString("drive_parent_publication_id")).put("receipts",new JSONObject());
            Doc stored=immutable("HausGarten-State-"+sha+".json",state);
            JSONObject manifest=DrivePublication.manifest(snapshot,actions,stored.id,sha).put("state_name",stored.name)
                .put("publisher_id",snapshot.getString("drive_publisher_id")).put("sequence",snapshot.optLong("drive_parent_sequence")+1)
                .put("previous_publication_id",snapshot.optString("drive_parent_publication_id")).put("previous_state_sha256",snapshot.optString("drive_parent_state_sha256"));
            byte[] bytes=PixelMcpWriteTools.canonical(manifest).getBytes(StandardCharsets.UTF_8);Doc commit=immutable("HausGarten-Commit-"+LegacyDriveRestFixture.sha256(bytes)+".json",bytes);
            DrivePublication.verify(new JSONObject(read(commit.uri)),read(stored.uri).getBytes(StandardCharsets.UTF_8),actions);
            return new JSONObject().put("publication_id",commit.id).put("receipts",new JSONObject()); // provider readback only
        }
        ArrayList<Doc> documents()throws Exception{
            if(reading&&readInventory!=null)return new ArrayList<>(readInventory);
            ArrayList<Doc> out=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(Cursor rows=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){
                if(rows==null)throw new SyncIoFailure("PROVIDER_QUERY_FAILED");
                while(rows.moveToNext()){String id=rows.getString(0);out.add(new Doc(id,rows.getString(1),DocumentsContract.buildDocumentUriUsingTree(tree,id),rows.isNull(2)?0:rows.getLong(2)));}
            }
            out.sort((a,b)->{int date=Long.compare(b.modified,a.modified);return date==0?a.id.compareTo(b.id):date;});if(reading)readInventory=new ArrayList<>(out);return out;
        }
        private ArrayList<Doc> children(Uri directory)throws Exception{
            ArrayList<Doc> out=new ArrayList<>();String id=directory.equals(tree)?DocumentsContract.getTreeDocumentId(tree):DocumentsContract.getDocumentId(directory);Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
            try(Cursor rows=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){
                if(rows==null)throw new IOException("Drive-Fotoordner ist vorübergehend nicht verfügbar.");
                while(rows.moveToNext()){String child=rows.getString(0);out.add(new Doc(child,rows.getString(1),DocumentsContract.buildDocumentUriUsingTree(tree,child),rows.isNull(2)?0:rows.getLong(2)));}
            }
            out.sort((a,b)->{int date=Long.compare(b.modified,a.modified);return date==0?a.id.compareTo(b.id):date;});return out;
        }
        private Uri photoDirectory(boolean create)throws Exception{
            ArrayList<Doc> folders=new ArrayList<>();for(Doc doc:documents())if("HausGarten-Fotos".equals(doc.name))folders.add(doc);
            if(folders.size()>1)throw new IOException("Mehrere Ordner HausGarten-Fotos gefunden. Keine Foto-Datei verändert.");
            if(!folders.isEmpty()){
                Uri folder=folders.get(0).uri;String type=resolver.getType(folder);if(type!=null&&!DocumentsContract.Document.MIME_TYPE_DIR.equals(type))throw new IOException("HausGarten-Fotos ist kein Ordner. Keine Datei verändert.");return folder;
            }
            if(!create)return null;
            Uri folder=DocumentsContract.createDocument(resolver,DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),DocumentsContract.Document.MIME_TYPE_DIR,"HausGarten-Fotos");
            if(folder==null)throw new IOException("Drive-Fotoordner konnte nicht angelegt werden.");return folder;
        }
        private ArrayList<Doc> photoMatches(Uri folder,String name)throws Exception{ArrayList<Doc> found=new ArrayList<>();for(Doc doc:children(folder))if(name.equals(doc.name))found.add(doc);if(found.size()>1)throw new IOException("Mehrere gleichnamige Foto-Originale gefunden. Keine Datei überschrieben.");return found;}
        private byte[] readBytes(Uri uri)throws Exception{
            try(InputStream input=resolver.openInputStream(uri)){if(input==null)throw new IOException("Drive-Foto ist momentan nicht lesbar.");ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] block=new byte[8192];int count;while((count=input.read(block))>=0){if(output.size()+count>PhotoAttachmentStore.MAX_BYTES)throw new IOException("Drive-Foto ist größer als 20 MB.");output.write(block,0,count);}return output.toByteArray();}
        }
        private void verifyBytes(byte[] bytes,JSONObject metadata)throws Exception{
            if(bytes.length!=metadata.getLong("byte_size")||!PhotoAttachmentStore.sha256(bytes).equals(metadata.getString("content_sha256"))||!PhotoAttachmentStore.sniff(bytes).equals(PhotoAttachmentStore.canonicalMime(metadata.getString("mime_type"))))throw new IOException("Drive-Foto stimmt nicht mit Prüfsumme, Größe oder Dateityp überein.");
        }
        byte[] readLegacyPhoto(JSONObject photo)throws Exception {
            String name=PhotoAttachmentStore.legacyName(photo);Uri folder=photoDirectory(false);if(folder==null)throw new IOException("Legacy-Fotoordner fehlt; keine vollständige Sicherung.");ArrayList<Doc> found=photoMatches(folder,name);if(found.size()!=1)throw new IOException("Legacy-Foto fehlt oder ist nicht eindeutig; keine vollständige Sicherung.");return readBytes(found.get(0).uri);
        }
        byte[] readPhotoVerified(JSONObject metadata)throws Exception{
            String name=metadata.optString("storage_name");if(!PhotoAttachmentStore.canonicalName(name))throw new IllegalArgumentException("Ungültige Foto-Dateireferenz.");Uri folder=photoDirectory(false);if(folder==null)throw new IOException("Der Ordner HausGarten-Fotos fehlt. Das lokale Original bleibt erhalten.");ArrayList<Doc> found=photoMatches(folder,name);if(found.size()!=1)throw new IOException("Das Foto-Original fehlt oder ist nicht eindeutig in HausGarten-Fotos.");byte[] bytes=readBytes(found.get(0).uri);verifyBytes(bytes,metadata);return bytes;
        }
        public void deletePhotoFileVerified(String name,String hash)throws Exception{
            if(!hash.matches("[0-9a-f]{64}")||!name.startsWith(hash+".")||!PhotoAttachmentStore.canonicalName(name))throw new IOException("Ungültige Löschreferenz.");
            Uri folder=photoDirectory(false);if(folder==null)return;ArrayList<Doc> found=photoMatches(folder,name);
            if(found.size()>1)throw new IOException("Fotodatei nicht eindeutig; nichts entfernt.");if(found.isEmpty())return;
            if(!hash.equals(PhotoAttachmentStore.sha256(readBytes(found.get(0).uri))))throw new IOException("Foto verändert; nichts entfernt.");
            if(!DocumentsContract.deleteDocument(resolver,found.get(0).uri))throw new IOException("Dateilöschung nicht bestätigt.");
            if(!photoMatches(folder,name).isEmpty())throw new IOException("Dateilöschung noch nicht zurückgelesen.");
        }
        void writePhotoVerified(JSONObject metadata,PhotoAttachmentStore local)throws Exception{
            String name=metadata.optString("storage_name");if(!PhotoAttachmentStore.canonicalName(name))throw new IllegalArgumentException("Ungültige Foto-Dateireferenz.");byte[] bytes=local.read(metadata);verifyBytes(bytes,metadata);Uri folder=photoDirectory(true);ArrayList<Doc> found=photoMatches(folder,name);if(!found.isEmpty()){if(found.size()!=1)throw new IOException("Foto-Original ist nicht eindeutig. Keine Datei verändert.");verifyBytes(readBytes(found.get(0).uri),metadata);return;}
            String temporaryName=".uploading-"+metadata.getString("content_sha256")+"-"+UUID.randomUUID();Uri temporary=DocumentsContract.createDocument(resolver,folder,metadata.getString("mime_type"),temporaryName);if(temporary==null)throw new IOException("Temporäre Drive-Fotodatei konnte nicht angelegt werden.");
            // Preserve a verified upload after any publish attempt, even if readback fails.
            Uri owned=temporary;boolean complete=false,publicationAttempted=false;try{
                try(OutputStream out=resolver.openOutputStream(temporary,"wt")){if(out==null)throw new IOException("Drive-Fotodatei ist nicht beschreibbar.");out.write(bytes);}
                verifyBytes(readBytes(temporary),metadata);publicationAttempted=true;Uri finalUri=DocumentsContract.renameDocument(resolver,temporary,name);if(finalUri==null)throw new IOException("Drive-Fotodatei konnte nicht sicher benannt werden.");owned=finalUri;verifyBytes(readBytes(finalUri),metadata);
                ArrayList<Doc> finalMatches=photoMatches(folder,name);if(finalMatches.size()!=1)throw new IOException("Drive hat das Foto-Original nicht eindeutig zurückgegeben.");verifyBytes(readBytes(finalMatches.get(0).uri),metadata);
                complete=true;
            }finally{if(!complete&&!publicationAttempted)try{DocumentsContract.deleteDocument(resolver,owned);}catch(Exception ignored){/* Only this newly-created document URI is eligible for cleanup. */}}
        }
        byte[] readThumbnailVerified(String name)throws Exception{
            if(!name.matches("[0-9a-f]{64}\\.jpg"))throw new IOException("Ungültige Vorschaureferenz.");Uri folder=photoDirectory(false);if(folder==null)throw new IOException("Fotoordner fehlt.");ArrayList<Doc> found=photoMatches(folder,name);if(found.size()!=1)throw new IOException("Vorschau fehlt.");byte[] raw=readBytes(found.get(0).uri);if(raw.length>65536||!name.equals(PhotoAttachmentStore.sha256(raw)+".jpg"))throw new IOException("Vorschau-Prüfsumme stimmt nicht überein.");return raw;
        }
        void writeThumbnailVerified(JSONObject metadata,PhotoAttachmentStore local)throws Exception{
            String name=metadata.optString("thumbnail_storage_name");if(name.isEmpty())return;byte[] raw=local.readThumbnail(name);Uri folder=photoDirectory(true);ArrayList<Doc> found=photoMatches(folder,name);if(!found.isEmpty()){readThumbnailVerified(name);return;}Uri file=DocumentsContract.createDocument(resolver,folder,"image/jpeg",".uploading-thumb-"+UUID.randomUUID());if(file==null)throw new IOException("Vorschau kann nicht geschrieben werden.");boolean publicationAttempted=false,complete=false;try{try(OutputStream out=resolver.openOutputStream(file,"wt")){if(out==null)throw new IOException("Vorschau kann nicht geschrieben werden.");out.write(raw);}byte[] back=readBytes(file);if(!name.equals(PhotoAttachmentStore.sha256(back)+".jpg"))throw new IOException("Vorschau-Prüfsumme stimmt nicht überein.");publicationAttempted=true;if(DocumentsContract.renameDocument(resolver,file,name)==null)throw new IOException("Vorschau konnte nicht benannt werden.");readThumbnailVerified(name);complete=true;}finally{if(!complete&&!publicationAttempted)try{DocumentsContract.deleteDocument(resolver,file);}catch(Exception ignored){}}
        }
        ArrayList<Doc> matches(String name)throws Exception{ArrayList<Doc> out=new ArrayList<>();for(Doc d:documents())if(name.equals(d.name))out.add(d);duplicates=Math.max(duplicates,Math.max(0,out.size()-1));return out;}
        String read(Uri uri)throws Exception{
            try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new SyncIoFailure("PROVIDER_READ_FAILED");ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))>=0){if(buffer.size()+n>64*1024*1024)throw new SyncIoFailure("REMOTE_FILE_TOO_LARGE");buffer.write(bytes,0,n);}return buffer.toString("UTF-8");}catch(SecurityException|SyncIoFailure error){throw error;}catch(IOException error){throw new SyncIoFailure("PROVIDER_READ_FAILED",error);}
        }
        String read(String name)throws Exception{
            ArrayList<Doc> matches=matches(name);if(matches.isEmpty())return null;String result=read(matches.get(0).uri);
            // Never silently choose one of different central snapshots or ChatGPT files.
            for(int i=1;i<matches.size();i++)if(!PixelMcpWriteTools.hash(new JSONObject(result)).equals(PixelMcpWriteTools.hash(new JSONObject(read(matches.get(i).uri)))))throw new SyncIoFailure("REMOTE_DUPLICATES_DIFFER");return result;
        }
        JSONArray readActions(String name)throws Exception{JSONArray out=new JSONArray();for(Doc d:matches(name))out=DriveJournalRules.union(out,new JSONObject(read(d.uri)).getJSONArray("actions"));return out;}
        void writeVerified(String name,JSONObject pkg,Map<String,String> originals)throws Exception{
            endRead();
            ArrayList<Doc> matches=matches(name);Uri target=null;String saved=prefs.getString("drive_journal_document_id","");
            for(Doc d:matches)if(d.id.equals(saved))target=d.uri;if(target==null&&!matches.isEmpty())target=matches.get(0).uri;
            // Back up all existing variants before any provider overwrite and recheck their content.
            for(Doc d:matches){String before=originals.get(d.id);if(before==null||!before.equals(read(d.uri)))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Die Drive-Änderungsdatei wurde parallel verändert. Bitte erneut abgleichen.");backup(before);}
            if(target==null)target=DocumentsContract.createDocument(resolver,DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),"application/json",name);
            if(target==null)throw new IOException("Drive-Änderungsdatei konnte nicht angelegt werden.");
            try(OutputStream out=resolver.openOutputStream(target,"wt")){if(out==null)throw new IOException("Drive-Datei ist nicht beschreibbar.");out.write(pkg.toString().getBytes(StandardCharsets.UTF_8));}
            if(!PixelMcpWriteTools.hash(new JSONObject(read(target))).equals(PixelMcpWriteTools.hash(pkg)))throw new IOException("Drive-Lesekontrolle fehlgeschlagen. Lokale Daten bleiben erhalten.");
            prefs.edit().putString("drive_journal_document_id",DocumentsContract.getDocumentId(target)).commit();
        }
        private void backup(String content)throws Exception{
            File dir=new File(context.getFilesDir(),"drive-journal-backups");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Sicherungsordner nicht verfügbar.");
            File file=new File(dir,PixelMcpWriteTools.hash(new JSONObject(content))+".json");if(file.exists())return;
            android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=null;
            try{out=atomic.startWrite();out.write(content.getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}
        }
    }
}
