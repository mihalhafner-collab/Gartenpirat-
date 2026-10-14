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
        Context app=context.getApplicationContext();if(scheduled!=null)scheduled.cancel(false);
        scheduled=EXECUTOR.schedule(()->{try(MainActivity.LocalStore store=new MainActivity.LocalStore(app)){
            if(!app.getSharedPreferences("haus_garten",0).getString("drive_tree_uri","").isEmpty())run(app,store);
        }catch(Exception ignored){/* run persists a bounded, user-facing failure; local actions survive. */}},Math.max(500,delay),TimeUnit.MILLISECONDS);
    }
    static JSONObject run(Context context,MainActivity.LocalStore store)throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
        String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Bitte zuerst den bestehenden Drive-Ordner auswählen.");
        return run(context,store,new Files(context,Uri.parse(tree),prefs),Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
    }
    // Package-private dependency boundary for isolated provider tests, never an MCP/UI endpoint.
    static JSONObject run(Context context,MainActivity.LocalStore store,Files files,String device)throws Exception{
        synchronized(LOCK){
            running=true;SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            try{
                String tree=prefs.getString("drive_tree_uri","");if(tree.isEmpty())throw new IOException("Bitte zuerst den bestehenden Drive-Ordner auswählen.");
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
                JSONArray errors=DriveJournalRules.apply(snapshot,all);
                // Commit data before dropping acknowledged local queue rows. Crash retries are idempotent.
                snapshot.put("sync_primary","pixel").put("drive_storage","snapshot_and_journals").put("drive_delivered_ids",new JSONArray(deliveredIds));
                String now=java.time.Instant.now().toString();
                if(!prefs.edit().putString("drive_snapshot",snapshot.toString()).putString("last_drive_sync",now)
                    .putString("last_sync_attempt",now).putBoolean("drive_sync_failed",false).putString("drive_last_error","")
                    .putString("drive_confirmation_scope","provider_readback").putInt("drive_duplicate_files",files.duplicates).putInt("drive_conflict_count",errors.length()).putInt("drive_retry_count",0).commit())throw new IOException("Drive-Änderungen konnten nicht lokal gesichert werden. Warteschlange bleibt erhalten.");
                store.markUploaded(pending);
                Set<String> confirmed=DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids"));confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")));
                confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));store.acknowledge(confirmed);
                return new JSONObject().put("ok",errors.length()==0).put("state",store.getState()).put("accepted",confirmed.size()).put("chatgpt_imported",chatActions.length())
                    .put("journal_errors",errors).put("error",errors.length()==0?"":"Einige Änderungen benötigen Prüfung. Gültige Daten bleiben erhalten.")
                    .put("confirmation_scope","provider_readback").put("cloud_confirmed",false).put("duplicate_files",files.duplicates);
            }catch(Exception failure){int attempts=Math.min(8,prefs.getInt("drive_retry_count",0)+1);prefs.edit().putBoolean("drive_sync_failed",true).putString("last_sync_attempt",java.time.Instant.now().toString()).putString("drive_last_error",friendly(failure)).putInt("drive_retry_count",attempts).commit();
                if(!(failure instanceof JSONException)&&!(failure instanceof IllegalArgumentException)&&!(failure instanceof SecurityException))request(context,Math.min(15*60*1000,15000L<<Math.min(attempts,6)));
                throw failure;}
            finally{running=false;}
        }
    }
    static String friendly(Exception error){
        if(error instanceof SecurityException)return "Drive-Zugriff fehlt oder ist abgelaufen. Bestehenden Ordner erneut auswählen; lokale Daten bleiben erhalten.";
        if(error instanceof JSONException)return "Eine Drive-Datei ist unvollständig oder beschädigt. Keine Datei überschrieben; Sicherung prüfen.";
        String value=error.getMessage();return value!=null&&value.length()<300&&error instanceof IllegalArgumentException?value:"Drive-Abgleich nicht abgeschlossen. Internet und Ordnerfreigabe prüfen. Lokale Einträge bleiben erhalten.";
    }
    static final class Doc{
        final String id,name;final Uri uri;final long modified;
        Doc(String id,String name,Uri uri,long modified){this.id=id;this.name=name;this.uri=uri;this.modified=modified;}
    }
    static final class Files{
        final Context context;final ContentResolver resolver;final Uri tree;final SharedPreferences prefs;int duplicates;
        Files(Context c,Uri t,SharedPreferences p){context=c;resolver=c.getContentResolver();tree=t;prefs=p;}
        ArrayList<Doc> documents()throws Exception{
            ArrayList<Doc> out=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(Cursor rows=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){
                if(rows==null)throw new IOException("Drive-Ordner ist vorübergehend nicht verfügbar.");
                while(rows.moveToNext()){String id=rows.getString(0);out.add(new Doc(id,rows.getString(1),DocumentsContract.buildDocumentUriUsingTree(tree,id),rows.isNull(2)?0:rows.getLong(2)));}
            }
            out.sort((a,b)->{int date=Long.compare(b.modified,a.modified);return date==0?a.id.compareTo(b.id):date;});return out;
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
