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
    private static final Object LOCK=new Object();
    private static final ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduled;
    private static volatile boolean running;
    static boolean running(){return running;}
    static synchronized void request(Context context,long delay){
        DriveSyncJob.schedule(context,delay);
        Context app=context.getApplicationContext();if(scheduled!=null)scheduled.cancel(false);
        scheduled=EXECUTOR.schedule(()->{try(MainActivity.LocalStore store=new MainActivity.LocalStore(app)){
            if(DriveAccount.available(app)&&!app.getSharedPreferences("haus_garten",0).getBoolean("drive_user_action_required",false)&&app.getSharedPreferences("haus_garten",0).getInt("drive_retry_count",0)<8)run(app,store);
        }catch(Exception ignored){/* run persists a bounded, user-facing failure; local actions survive. */}},Math.max(500,delay),TimeUnit.MILLISECONDS);
    }
    static JSONObject run(Context context,MainActivity.LocalStore store)throws Exception{
        if(DriveAccount.configured(context))return run(context,store,new DriveDirectFiles(context,DriveAccount.client(context,store)),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
        String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Bitte zuerst den bestehenden Drive-Ordner auswählen.");
        return run(context,store,new Files(context,Uri.parse(tree),prefs),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
    }
    // Package-private dependency boundary for isolated provider tests, never an MCP/UI endpoint.
    static JSONObject run(Context context,MainActivity.LocalStore store,Files files,String device)throws Exception{
        synchronized(LOCK){
            running=true;SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            try{
                String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty()&&!files.cloudVerified())throw new IOException("Bitte zuerst den bestehenden Drive-Ordner auswählen.");
                String incoming=files.read("HausGarten-Laptop.json");
                if(incoming==null)throw new IOException("Der Gesamtstand HausGarten-Laptop.json fehlt im ausgewählten Ordner. Es wird kein leerer Ersatz angelegt.");
                JSONObject snapshot=new JSONObject(incoming);DriveJournalRules.validateSnapshot(snapshot);
                if(device==null||!device.matches("[a-zA-Z0-9_-]+"))throw new IOException("Die Gerätekennung fehlt. Keine neue Änderungsdatei angelegt.");
                String name="HausGarten-Mobile-"+device+".json";
                LinkedHashMap<String,String> originals=new LinkedHashMap<>();
                JSONArray own=new JSONArray(),others=new JSONArray();
                for(Doc doc:files.documents()){
                    if(!doc.name.startsWith("HausGarten-Mobile-")||!doc.name.endsWith(".json"))continue;
                    String text=files.read(doc.uri);JSONObject pkg=new JSONObject(text);JSONArray actions=pkg.getJSONArray("actions");
                    if(doc.name.equals(name)){originals.put(doc.id,text);own=DriveJournalRules.union(own,actions);}else others=DriveJournalRules.union(others,actions);
                }
                JSONArray pending=store.pendingActionsForDrive();
                JSONArray attachments=store.pendingPhotoAttachments(pending);
                Map<String,Exception> blockedPhotos=new HashMap<>();
                for(int i=0;i<attachments.length();i++){
                    JSONObject metadata=attachments.getJSONObject(i);
                    try{files.writePhotoVerified(metadata,store.photoAttachmentStore());store.markPhotoAttachmentTransferred(metadata.getString("photo_id"));}
                    catch(Exception failure){blockedPhotos.put(metadata.getString("photo_id"),failure);}
                }
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
                JSONArray all=DriveJournalRules.union(others,chatActions,combined);
                JSONArray ready=new JSONArray(),mediaErrors=new JSONArray();
                for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);Exception failure="photo".equals(a.optString("kind"))?blockedPhotos.get(a.getJSONObject("payload").optString("id")):null;
                    if(failure==null)ready.put(a);else mediaErrors.put(new JSONObject().put("client_id",a.getString("client_id")).put("kind","photo").put("code",failure instanceof DriveRestClient.Failure?((DriveRestClient.Failure)failure).code:"MEDIA_TRANSFER_PENDING").put("error",friendly(failure)));}
                JSONArray errors=DriveJournalRules.apply(snapshot,ready);
                for(int i=0;i<mediaErrors.length();i++)errors.put(mediaErrors.getJSONObject(i));snapshot.put("drive_journal_errors",errors);
                snapshot.put("drive_review_actions",DriveJournalRules.reviewActions(snapshot,all));
                store.recordSnapshotPhotoAttachments(snapshot);
                // Commit data before dropping acknowledged local queue rows. Crash retries are idempotent.
                snapshot.put("sync_primary","pixel").put("drive_storage","snapshot_and_journals").put("drive_delivered_ids",new JSONArray(deliveredIds)).put("drive_publisher_id",device);
                JSONObject publication=files.publish(snapshot,all);
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Abgleich unterbrochen; Warteschlange bleibt erhalten.");
                if(files.cloudVerified())snapshot.put("drive_cloud_receipts",publication.getJSONObject("receipts")).put("drive_last_publication",publication.getString("publication_id"));
                boolean mediaComplete=snapshot.optJSONArray("drive_media_errors")==null||snapshot.getJSONArray("drive_media_errors").length()==0;
                String now=java.time.Instant.now().toString();
                Set<String> committed=DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids"));committed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
                JSONObject resolutions=snapshot.optJSONObject("drive_resolved_actions");if(resolutions!=null)resolutions.keys().forEachRemaining(committed::add);
                store.commitSnapshot(snapshot,committed);
                boolean fully=files.cloudVerified()&&mediaComplete&&errors.length()==0&&store.getState().getJSONObject("sync_queue").getInt("total")==0;
                if(!prefs.edit().putString("last_drive_sync",now)
                    .putString("last_sync_attempt",now).putBoolean("drive_sync_failed",false).putString("drive_last_error","")
                    .putString("drive_confirmation_scope",files.cloudVerified()?"drive_server_manifest":"provider_readback").putBoolean("drive_cloud_confirmed",fully).putString("last_full_sync",fully?now:prefs.getString("last_full_sync",""))
                    .putBoolean("drive_user_action_required",false).putLong("drive_next_retry",0).putInt("drive_duplicate_files",files.duplicates).putInt("drive_conflict_count",errors.length()).putInt("drive_retry_count",0).commit())throw new IOException("Drive-Änderungen konnten nicht lokal gesichert werden. Warteschlange bleibt erhalten.");
                store.markUploaded(pending);
                Set<String> confirmed=DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids"));confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")));
                confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
                JSONObject reviewed=snapshot.optJSONObject("drive_resolved_actions");if(reviewed!=null)reviewed.keys().forEachRemaining(confirmed::add);
                store.cleanupConfirmedPhotoDeletes(pending,confirmed,snapshot);
                store.acknowledge(confirmed);
                return new JSONObject().put("ok",errors.length()==0).put("state",store.getState()).put("accepted",confirmed.size()).put("chatgpt_imported",chatActions.length())
                    .put("journal_errors",errors).put("error",errors.length()==0?"":"Einige Änderungen benötigen Prüfung. Gültige Daten bleiben erhalten.")
                    .put("confirmation_scope",files.cloudVerified()?"drive_server_manifest":"provider_readback").put("cloud_confirmed",files.cloudVerified()).put("duplicate_files",files.duplicates);
            }catch(Exception failure){int attempts=Math.min(8,prefs.getInt("drive_retry_count",0)+1);boolean needs=failure instanceof JSONException||failure instanceof IllegalArgumentException||failure instanceof SecurityException||(failure instanceof DriveRestClient.Failure&&((DriveRestClient.Failure)failure).userAction);
                long delay=Math.max(failure instanceof DriveRestClient.Failure?((DriveRestClient.Failure)failure).retryAfter:0,Math.min(15*60*1000,15000L<<Math.min(attempts,6)));
                prefs.edit().putBoolean("drive_sync_failed",true).putBoolean("drive_cloud_confirmed",false).putBoolean("drive_user_action_required",needs||attempts>=8).putLong("drive_next_retry",needs||attempts>=8?0:System.currentTimeMillis()+delay).putString("last_sync_attempt",java.time.Instant.now().toString()).putString("drive_last_error",friendly(failure)).putInt("drive_retry_count",attempts).commit();
                if(!needs&&attempts<8)request(context,delay);
                throw failure;}
            finally{running=false;}
        }
    }
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
    static final class Doc{
        final String id,name;final Uri uri;final long modified;
        Doc(String id,String name,Uri uri,long modified){this.id=id;this.name=name;this.uri=uri;this.modified=modified;}
    }
    static class Files{
        final Context context;final ContentResolver resolver;final Uri tree;final SharedPreferences prefs;int duplicates;
        Files(Context c,Uri t,SharedPreferences p){context=c;resolver=c.getContentResolver();tree=t;prefs=p;}
        boolean cloudVerified(){return false;}
        JSONObject publish(JSONObject snapshot,JSONArray actions)throws Exception{return new JSONObject();}
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
        byte[] readPhotoVerified(JSONObject metadata)throws Exception{
            String name=metadata.optString("storage_name");if(!PhotoAttachmentStore.canonicalName(name))throw new IllegalArgumentException("Ungültige Foto-Dateireferenz.");Uri folder=photoDirectory(false);if(folder==null)throw new IOException("Der Ordner HausGarten-Fotos fehlt. Das lokale Original bleibt erhalten.");ArrayList<Doc> found=photoMatches(folder,name);if(found.isEmpty())throw new IOException("Das Foto-Original fehlt in HausGarten-Fotos.");byte[] bytes=readBytes(found.get(0).uri);verifyBytes(bytes,metadata);return bytes;
        }
        void writePhotoVerified(JSONObject metadata,PhotoAttachmentStore local)throws Exception{
            String name=metadata.optString("storage_name");if(!PhotoAttachmentStore.canonicalName(name))throw new IllegalArgumentException("Ungültige Foto-Dateireferenz.");byte[] bytes=local.read(metadata);verifyBytes(bytes,metadata);Uri folder=photoDirectory(true);ArrayList<Doc> found=photoMatches(folder,name);if(!found.isEmpty()){verifyBytes(readBytes(found.get(0).uri),metadata);return;}
            String temporaryName=".uploading-"+metadata.getString("content_sha256")+"-"+UUID.randomUUID();Uri temporary=DocumentsContract.createDocument(resolver,folder,metadata.getString("mime_type"),temporaryName);if(temporary==null)throw new IOException("Temporäre Drive-Fotodatei konnte nicht angelegt werden.");
            Uri owned=temporary;boolean complete=false;try{
                try(OutputStream out=resolver.openOutputStream(temporary,"wt")){if(out==null)throw new IOException("Drive-Fotodatei ist nicht beschreibbar.");out.write(bytes);}
                verifyBytes(readBytes(temporary),metadata);Uri finalUri=DocumentsContract.renameDocument(resolver,temporary,name);if(finalUri==null)throw new IOException("Drive-Fotodatei konnte nicht sicher benannt werden.");owned=finalUri;verifyBytes(readBytes(finalUri),metadata);
                ArrayList<Doc> finalMatches=photoMatches(folder,name);if(finalMatches.size()!=1)throw new IOException("Drive hat das Foto-Original nicht eindeutig zurückgegeben.");verifyBytes(readBytes(finalMatches.get(0).uri),metadata);
                complete=true;
            }finally{if(!complete)try{DocumentsContract.deleteDocument(resolver,owned);}catch(Exception ignored){/* Only this newly-created document URI is eligible for cleanup. */}}
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
