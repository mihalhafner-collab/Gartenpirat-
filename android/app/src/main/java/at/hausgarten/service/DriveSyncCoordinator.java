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
final class DriveSyncCoordinator {
    static final Object LOCK=new Object();
    private static final ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduled;
    private static volatile boolean running;
    static boolean running(){return running;}
    static JSONObject status(Context c,JSONObject queue)throws Exception {
        SharedPreferences p=c.getSharedPreferences("haus_garten",0);
        JSONObject s=new JSONObject().put("configured",DriveAccount.available(c)).put("transport",DriveAccount.configured(c)?"direct_drive":"android_provider")
            .put("drive_sync_failed",p.getBoolean("drive_sync_failed",false)).put("last_error_code",p.getString("drive_last_error_code",""))
            .put("last_error",p.getString("drive_last_error","")).put("phase",p.getString("drive_sync_phase","idle"))
            .put("user_action_required",p.getBoolean("drive_user_action_required",false)).put("retry_count",p.getInt("drive_retry_count",0))
            .put("next_retry",p.getLong("drive_next_retry",0)).put("last_sync",p.getString("last_drive_sync",""))
            .put("last_sync_attempt",p.getString("last_sync_attempt","")).put("last_full_sync",p.getString("last_full_sync",""))
            .put("confirmation_scope",p.getString("drive_confirmation_scope","local_only")).put("cloud_confirmed",p.getBoolean("drive_cloud_confirmed",false));
        boolean rest=DriveAccount.configured(c),permission=rest||DriveAccount.providerPermission(c);
        s.put("persisted_tree_permission",!rest&&permission).put("trash_cleanup_pending",p.getInt("trash_cleanup_pending",0)).put("trash_cleanup_error",p.getString("trash_cleanup_error",""));
        if(s.optString("last_error_code").isEmpty()&&s.optString("last_error").contains("ursprüngliche Drive-Gesamtstand"))s.put("last_error_code","LEGACY_SOURCE_CHANGED");
        if(s.optBoolean("configured")&&!permission)s.put("drive_sync_failed",true).put("user_action_required",true).put("last_error_code","PERMISSION_REQUIRED").put("last_error",SyncPolicy.message("PERMISSION_REQUIRED"));
        return SyncPolicy.describe(s,queue,running,System.currentTimeMillis());
    }
    static boolean prepareExplicit(Context c,String id)throws Exception {
        SharedPreferences p=c.getSharedPreferences("haus_garten",0);
        if(!DriveAccount.available(c))return false;
        String marker="drive_explicit_request:"+PixelMcpWriteTools.hash(id);
        if(!p.getBoolean(marker,false)&&!p.edit().putBoolean(marker,true).putInt("drive_retry_count",0).putBoolean("drive_user_action_required",false).putLong("drive_next_retry",0).commit())throw new IOException("Abgleichanforderung konnte nicht gesichert werden.");
        return true;
    }
    static JSONObject requestExplicit(Context c,String id)throws Exception {
        if(DriveAccount.available(c)&&!DriveAccount.configured(c)&&!DriveAccount.providerPermission(c))return new JSONObject().put("state","action_required").put("next_action","connect_drive").put("message",SyncPolicy.message("PERMISSION_REQUIRED"));
        if(!prepareExplicit(c,id))return new JSONObject().put("state","not_connected").put("next_action","connect_drive").put("message",SyncPolicy.message("NOT_CONNECTED"));
        request(c,500);return new JSONObject().put("state","requested").put("cloud_confirmed",false).put("message","Abgleich angefordert. Das Ergebnis wird gesondert geprüft.");
    }
    static boolean automaticAllowed(Context c)throws Exception{return SyncPolicy.automaticAllowed(status(c,new JSONObject()),System.currentTimeMillis());}
    static synchronized void request(Context context,long delay){
        long due=context.getSharedPreferences("haus_garten",0).getLong("drive_next_retry",0)-System.currentTimeMillis();
        delay=Math.max(delay,due);
        DriveSyncJob.schedule(context,delay);
        Context app=context.getApplicationContext();if(scheduled!=null)scheduled.cancel(false);
        scheduled=EXECUTOR.schedule(()->{try(MainActivity.LocalStore store=new MainActivity.LocalStore(app)){
            if(automaticAllowed(app))run(app,store);
        }catch(Exception ignored){/* run persists a bounded, user-facing failure; local actions survive. */}},Math.max(500,delay),TimeUnit.MILLISECONDS);
    }
    static JSONObject run(Context context,MainActivity.LocalStore store)throws Exception{
        if(DriveAccount.configured(context))return run(context,store,new DriveDirectFiles(context,DriveAccount.client(context,store)),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
        String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Bitte zuerst den bestehenden Drive-Ordner auswählen.");
        if(!DriveAccount.providerPermission(context))throw new SecurityException("Drive-Ordnerfreigabe fehlt. Bestehenden Ordner erneut auswählen.");
        return run(context,store,new Files(context,Uri.parse(tree),prefs),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
    }
    // Package-private dependency boundary for isolated provider tests, never an MCP/UI endpoint.
    static JSONObject run(Context context,MainActivity.LocalStore store,Files files,String device)throws Exception{
        synchronized(LOCK){
            running=true;SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            try{
                prefs.edit().putString("drive_sync_phase","reading").putString("last_sync_attempt",java.time.Instant.now().toString()).commit();
                String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty()&&!files.cloudVerified())throw new IOException("Bitte zuerst den bestehenden Drive-Ordner auswählen.");
                String incoming=files.read("HausGarten-Laptop.json");
                if(incoming==null)throw new IOException("Der Gesamtstand HausGarten-Laptop.json fehlt im ausgewählten Ordner. Es wird kein leerer Ersatz angelegt.");
                JSONObject legacy=new JSONObject(incoming);DriveJournalRules.validateSnapshot(legacy);
                if(device==null||!device.matches("[a-zA-Z0-9_-]+"))throw new IOException("Die Gerätekennung fehlt. Keine neue Änderungsdatei angelegt.");
                JSONObject snapshot=files.readPrimary(legacy,device);store.seedServiceCatalog(snapshot);snapshot.put("drive_publisher_id",device);
                String name="HausGarten-Mobile-"+device+".json";
                LinkedHashMap<String,String> originals=new LinkedHashMap<>();
                JSONArray own=new JSONArray(),others=new JSONArray();
                for(Doc doc:files.documents()){
                    if(!doc.name.startsWith("HausGarten-Mobile-")||!doc.name.endsWith(".json"))continue;
                    String text=files.read(doc.uri);JSONObject pkg=new JSONObject(text);JSONArray actions=pkg.getJSONArray("actions");
                    if(doc.name.equals(name)){originals.put(doc.id,text);own=DriveJournalRules.union(own,actions);}else others=DriveJournalRules.union(others,actions);
                }
                JSONArray allPending=store.pendingActionsForDrive(),pending=new JSONArray(),deferred=new JSONArray();
                for(int i=0;i<allPending.length();i++){JSONObject a=allPending.getJSONObject(i);if(componentReady(snapshot,a))pending.put(a);else deferred.put(a);}
                JSONArray attachments=store.pendingPhotoAttachments(pending);
                prefs.edit().putString("drive_sync_phase","media").commit();
                Map<String,Exception> blockedPhotos=new HashMap<>();
                Map<String,Exception> blockedDocuments=new HashMap<>();
                for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.getJSONObject("payload");if(!"offer_finalize".equals(a.optString("kind")))continue;
                    try{files.writeDocumentVerified(p,new BusinessDocumentStore(context).read(p));}catch(Exception error){blockedDocuments.put(a.getString("client_id"),error);}}
                Set<String> selected=SyncMediaPlan.select(attachments),verified=new HashSet<>(),thumbnails=new HashSet<>();Map<String,Exception> mediaFailures=new HashMap<>();int mediaProgress=0,deferredMedia=0;
                for(int i=0;i<attachments.length();i++){
                    JSONObject metadata=attachments.getJSONObject(i);String photoId=metadata.getString("photo_id"),hash=metadata.optString("content_sha256");
                    if(metadata.optBoolean("missing")||!hash.matches("[0-9a-f]{64}")){blockedPhotos.put(photoId,new DriveRestClient.Failure("PHOTO_ORIGINAL_REQUIRED","Foto-Original fehlt lokal. Original wieder bereitstellen; andere Änderungen werden weiter abgeglichen.",0,true));continue;}
                    if(!selected.contains(hash)){deferredMedia++;blockedPhotos.put(photoId,new DriveRestClient.Failure("MEDIA_TRANSFER_PENDING","Foto folgt automatisch im nächsten Übertragungsabschnitt.",0,false));continue;}
                    if(mediaFailures.containsKey(hash)){blockedPhotos.put(photoId,mediaFailures.get(hash));continue;}
                    try{if(!verified.contains(hash)){files.writePhotoVerified(metadata,store.photoAttachmentStore());verified.add(hash);mediaProgress++;}String thumb=metadata.optString("thumbnail_storage_name");if(!thumbnails.contains(thumb)){files.writeThumbnailVerified(metadata,store.photoAttachmentStore());thumbnails.add(thumb);}store.markPhotoAttachmentTransferred(photoId);}
                    catch(Exception failure){blockedPhotos.put(photoId,failure);mediaFailures.put(hash,failure);}
                }
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
                    if(failure==null)ready.put(a);else mediaErrors.put(new JSONObject().put("client_id",a.getString("client_id")).put("kind",a.getString("kind")).put("code",failure instanceof DriveRestClient.Failure?((DriveRestClient.Failure)failure).code:"MEDIA_TRANSFER_PENDING").put("error",friendly(failure)));}
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
                Set<String> committed=DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids"));committed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
                JSONObject resolutions=snapshot.optJSONObject("drive_resolved_actions");if(resolutions!=null)resolutions.keys().forEachRemaining(committed::add);
                store.commitSnapshot(snapshot,committed);
                boolean fully=files.cloudVerified()&&mediaComplete&&errors.length()==0&&store.getState().getJSONObject("sync_queue").getInt("total")==0;
                if(!prefs.edit().putString("last_drive_sync",now)
                    .putString("last_sync_attempt",now).putBoolean("drive_sync_failed",false).putString("drive_last_error","").putString("drive_last_error_code","").putString("drive_sync_phase","idle")
                    .putString("drive_confirmation_scope",files.cloudVerified()?"drive_server_manifest":"provider_readback").putBoolean("drive_cloud_confirmed",fully).putString("last_full_sync",fully?now:prefs.getString("last_full_sync",""))
                    .putBoolean("drive_user_action_required",false).putLong("drive_next_retry",0).putInt("drive_duplicate_files",files.duplicates).putInt("drive_conflict_count",errors.length()).putInt("drive_retry_count",0).commit())throw new IOException("Drive-Änderungen konnten nicht lokal gesichert werden. Warteschlange bleibt erhalten.");
                store.markUploaded(pending);
                Set<String> confirmed=DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids"));confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")));
                confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
                JSONObject reviewed=snapshot.optJSONObject("drive_resolved_actions");if(reviewed!=null)reviewed.keys().forEachRemaining(confirmed::add);
                store.cleanupConfirmedPhotoDeletes(pending,confirmed,snapshot);
                store.acknowledge(confirmed);
                JSONObject cleanup=TrashMediaCleanup.run(store,files,snapshot);
                prefs.edit().putInt("trash_cleanup_pending",cleanup.getInt("pending")).putString("trash_cleanup_error",cleanup.getString("error_code")).commit();
                if(cleanup.getInt("pending")>0){fully=false;prefs.edit().putBoolean("drive_cloud_confirmed",false).commit();if(cleanup.getInt("completed")>0)request(context,1500);}
                if(mediaProgress>0&&deferredMedia>0)request(context,1500);
                return new JSONObject().put("ok",true).put("transport_ok",true).put("needs_review",errors.length()>0).put("state",store.getState()).put("accepted",confirmed.size()).put("chatgpt_imported",chatActions.length())
                    .put("journal_errors",errors).put("error","")
                    .put("confirmation_scope",files.cloudVerified()?"drive_server_manifest":"provider_readback").put("cloud_confirmed",fully).put("duplicate_files",files.duplicates);
            }catch(Exception failure){int attempts=Math.min(8,prefs.getInt("drive_retry_count",0)+1);boolean needs=failure instanceof JSONException||failure instanceof IllegalArgumentException||failure instanceof SecurityException||(failure instanceof DriveRestClient.Failure&&((DriveRestClient.Failure)failure).userAction);
                long delay=SyncPolicy.retryDelay(attempts,failure instanceof DriveRestClient.Failure?((DriveRestClient.Failure)failure).retryAfter:0);
                prefs.edit().putBoolean("drive_sync_failed",true).putBoolean("drive_cloud_confirmed",false).putBoolean("drive_user_action_required",needs||attempts>=8).putLong("drive_next_retry",needs||attempts>=8?0:System.currentTimeMillis()+delay).putString("last_sync_attempt",java.time.Instant.now().toString()).putString("drive_last_error_code",failureCode(failure)).putString("drive_last_error",friendly(failure)).putInt("drive_retry_count",attempts).commit();
                if(!needs&&attempts<8)request(context,delay);
                throw failure;}
            finally{running=false;}
        }
    }
    private static boolean componentReady(JSONObject snapshot,JSONObject action){return DriveJournalRules.componentReady(snapshot,action.optString("kind"));}
    static String failureCode(Exception error){return error instanceof DriveRestClient.Failure?((DriveRestClient.Failure)error).code:error instanceof PixelMcpWriteTools.Rejected?((PixelMcpWriteTools.Rejected)error).code:error instanceof SecurityException?"PERMISSION_REQUIRED":error instanceof JSONException?"INVALID_REMOTE_DATA":"TRANSFER_FAILED";}
    static String friendly(Exception error){
        if(error instanceof DriveRestClient.Failure)return error.getMessage();
        if(error instanceof SecurityException)return "Drive-Zugriff fehlt oder ist abgelaufen. Bestehenden Ordner erneut auswählen; lokale Daten bleiben erhalten.";
        if(error instanceof JSONException)return "Eine Drive-Datei ist unvollständig oder beschädigt. Keine Datei überschrieben; Sicherung prüfen.";
        String value=error.getMessage();return value!=null&&value.length()<300&&error instanceof IllegalArgumentException?value:"Drive-Abgleich nicht abgeschlossen. Internet und Ordnerfreigabe prüfen. Lokale Einträge bleiben erhalten.";
    }
    static boolean fetchPhoto(Context context,JSONObject metadata,PhotoAttachmentStore local)throws Exception{
        if(DriveAccount.configured(context))try(MainActivity.LocalStore store=new MainActivity.LocalStore(context)){synchronized(LOCK){byte[] bytes=new DriveDirectFiles(context,DriveAccount.client(context,store)).readPhotoVerified(metadata);local.storeExpected(bytes,metadata);return true;}}
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty())return false;
        synchronized(LOCK){Files files=new Files(context,Uri.parse(tree),prefs);byte[] bytes=files.readPhotoVerified(metadata);local.storeExpected(bytes,metadata);return true;}
    }
    static byte[] fetchDocument(Context context,JSONObject metadata)throws Exception{
        synchronized(LOCK){if(DriveAccount.configured(context))try(MainActivity.LocalStore store=new MainActivity.LocalStore(context)){return new DriveDirectFiles(context,DriveAccount.client(context,store)).readDocumentVerified(metadata);}
            SharedPreferences p=context.getSharedPreferences("haus_garten",0);String tree=p.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Dokument fehlt lokal und Drive ist nicht verbunden.");return new Files(context,Uri.parse(tree),p).readDocumentVerified(metadata);}
    }
    static byte[] fetchThumbnail(Context c,String name)throws Exception{
        if(!name.matches("[0-9a-f]{64}\\.jpg"))throw new IOException("Ungültige Vorschaureferenz.");
        synchronized(LOCK){if(DriveAccount.configured(c))try(MainActivity.LocalStore s=new MainActivity.LocalStore(c)){return new DriveDirectFiles(c,DriveAccount.client(c,s)).readThumbnailVerified(name);}
            SharedPreferences p=c.getSharedPreferences("haus_garten",0);String tree=p.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Drive nicht verbunden.");return new Files(c,Uri.parse(tree),p).readThumbnailVerified(name);}
    }
    static final class Doc{
        final String id,name;final Uri uri;final long modified;
        Doc(String id,String name,Uri uri,long modified){this.id=id;this.name=name;this.uri=uri;this.modified=modified;}
    }
    static class Files{
        final Context context;final ContentResolver resolver;final Uri tree;final SharedPreferences prefs;int duplicates;
        Files(Context c,Uri t,SharedPreferences p){context=c;resolver=c.getContentResolver();tree=t;prefs=p;}
        boolean cloudVerified(){return false;}
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
        void deleteDocumentFileVerified(JSONObject metadata)throws Exception{
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
                public byte[] read(String id)throws Exception{Doc d=index.get(id);if(d!=null)return Files.this.read(d.uri).getBytes(StandardCharsets.UTF_8);throw new IOException("Veröffentlichter Gesamtstand fehlt; keine leere Wiederanlage.");}
            },evidenceOnly);
        }
        Doc immutable(String name,byte[] bytes)throws Exception {
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
            JSONObject current=readPrimary(new JSONObject(read("HausGarten-Laptop.json")),snapshot.getString("drive_publisher_id"));DrivePrimaryState.checkParent(snapshot,current);
            byte[] state=DrivePublication.stateBytes(snapshot);String sha=DriveRestClient.sha256(state);
            if(sha.equals(snapshot.optString("drive_parent_state_sha256")))return new JSONObject().put("publication_id",snapshot.getString("drive_parent_publication_id")).put("receipts",new JSONObject());
            Doc stored=immutable("HausGarten-State-"+sha+".json",state);
            JSONObject manifest=DrivePublication.manifest(snapshot,actions,stored.id,sha).put("state_name",stored.name)
                .put("publisher_id",snapshot.getString("drive_publisher_id")).put("sequence",snapshot.optLong("drive_parent_sequence")+1)
                .put("previous_publication_id",snapshot.optString("drive_parent_publication_id")).put("previous_state_sha256",snapshot.optString("drive_parent_state_sha256"));
            byte[] bytes=PixelMcpWriteTools.canonical(manifest).getBytes(StandardCharsets.UTF_8);Doc commit=immutable("HausGarten-Commit-"+DriveRestClient.sha256(bytes)+".json",bytes);
            DrivePublication.verify(new JSONObject(read(commit.uri)),read(stored.uri).getBytes(StandardCharsets.UTF_8),actions);
            return new JSONObject().put("publication_id",commit.id).put("receipts",new JSONObject()); // provider readback only
        }
        ArrayList<Doc> documents()throws Exception{
            ArrayList<Doc> out=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(Cursor rows=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){
                if(rows==null)throw new IOException("Drive-Ordner ist vorübergehend nicht verfügbar.");
                while(rows.moveToNext()){String id=rows.getString(0);out.add(new Doc(id,rows.getString(1),DocumentsContract.buildDocumentUriUsingTree(tree,id),rows.isNull(2)?0:rows.getLong(2)));}
            }
            out.sort((a,b)->{int date=Long.compare(b.modified,a.modified);return date==0?a.id.compareTo(b.id):date;});return out;
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
        void deletePhotoFileVerified(String name,String hash)throws Exception{
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
            try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new IOException("Drive-Datei momentan nicht lesbar.");ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))>=0){if(buffer.size()+n>64*1024*1024)throw new IOException("Drive-Datei zu groß; bitte Datenarchiv prüfen.");buffer.write(bytes,0,n);}return buffer.toString("UTF-8");}
        }
        String read(String name)throws Exception{
            ArrayList<Doc> matches=matches(name);if(matches.isEmpty())return null;String result=read(matches.get(0).uri);
            // Never silently choose one of different central snapshots or ChatGPT files.
            for(int i=1;i<matches.size();i++)if(!PixelMcpWriteTools.hash(new JSONObject(result)).equals(PixelMcpWriteTools.hash(new JSONObject(read(matches.get(i).uri)))))throw new IOException("Mehrere unterschiedliche "+name+" gefunden. Bitte die Dateien prüfen; keine überschrieben.");return result;
        }
        JSONArray readActions(String name)throws Exception{JSONArray out=new JSONArray();for(Doc d:matches(name))out=DriveJournalRules.union(out,new JSONObject(read(d.uri)).getJSONArray("actions"));return out;}
        void writeVerified(String name,JSONObject pkg,Map<String,String> originals)throws Exception{
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
