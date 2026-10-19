package at.hausgarten.service;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Private streaming backup, outside operational storage. No shared/public export. */
final class LegacyConflictBackup {
    static void requireReady(Context c,MainActivity.LocalStore store)throws Exception {
        android.content.SharedPreferences prefs=c.getSharedPreferences("haus_garten",0);JSONObject state=store.getState();
        LegacyBackupPolicy.require(state,DriveAccount.available(c),DriveAccount.configured(c)||DriveAccount.providerPermission(c),prefs.getBoolean("drive_sync_failed",false),DriveSyncCoordinator.running(),prefs.getString("drive_confirmation_scope",""));
    }
    static synchronized JSONObject create(Context c,MainActivity.LocalStore store,String request)throws Exception {
        requireReady(c,store);
        android.content.SharedPreferences prefs=c.getSharedPreferences("haus_garten",0);
        DriveSyncCoordinator.Files drive=DriveAccount.configured(c)?new DriveDirectFiles(c,DriveAccount.client(c,store)):new DriveSyncCoordinator.Files(c,Uri.parse(prefs.getString("drive_tree_uri","")),prefs);
        return createVerified(c,store,request,drive);
    }
    /** Same serialized provider core, injectable for isolated ContentResolver regression tests. */
    static synchronized JSONObject createVerified(Context c,MainActivity.LocalStore store,String request,DriveSyncCoordinator.Files drive)throws Exception {
        String prior=store.ownerValue("legacy-backup-request:"+PixelMcpWriteTools.hash(request),"");if(!prior.isEmpty()){JSONObject saved=new JSONObject(prior);verify(c,store,saved.getJSONObject("backup").getString("backup_id"),saved.getJSONObject("preview").getString("preview_hash"));return saved.put("replayed",true);}
        JSONObject state=store.getState(),preview=LegacyConflictRules.preview(state);
        if(preview.getInt("count")==0)throw new IllegalArgumentException("Keine unverifizierbaren Alt-Konflikte vorhanden.");
        String session=state.getJSONObject("work_session").optString("state");if(!Arrays.asList("IDLE","FINALIZED").contains(session))throw new IllegalArgumentException("Zeiterfassung vor vollständiger Sicherung abschließen.");
        File root=new File(c.getNoBackupFilesDir(),"legacy-conflict-backups");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Privater Sicherungsordner fehlt.");
        String id="legacy_"+PixelMcpWriteTools.hash(request).substring(0,32);File directory=new File(root,id);if(directory.exists()){
            // A process crash may leave an uncommitted backup. Preserve its bytes and
            // retry at the same stable ID; a committed receipt above is never moved.
            File interrupted=new File(root,id+"_interrupted_"+java.util.UUID.randomUUID());
            if(!directory.renameTo(interrupted))throw new IOException("Unterbrochene Sicherung kann nicht erhalten werden.");
        }
        if(!directory.mkdir())throw new IOException("Sicherungsordner kann nicht angelegt werden.");
        File dbCopy=new File(directory,"local.sqlite"),zip=new File(directory,"backup.zip");boolean complete=false;
        try {
            // SQLite creates a consistent database image, including WAL contents. Older
            // SQLite engines fail closed; copying a live database file is not a fallback.
            store.getWritableDatabase().execSQL("VACUUM INTO '"+dbCopy.getAbsolutePath().replace("'","''")+"'");
            if(drive==null)throw new IOException("Drive-Snapshot ist für die Sicherung nicht lesbar.");
            String raw=drive.read("HausGarten-Laptop.json");if(raw==null)throw new IOException("Drive-Snapshot fehlt; nichts abgeschlossen.");DriveJournalRules.validateSnapshot(new JSONObject(raw));
            String device=state.getJSONObject("master").getString("drive_publisher_id");
            JSONObject primary=drive.readPrimary(new JSONObject(raw),device);
            String primaryId=primary.optString("drive_parent_publication_id");if(primaryId.isEmpty())throw new IOException("Bestätigter Pixel-Gesamtstand fehlt; keine Altentscheidung.");
            String stateId="";byte[] manifest=null;
            for(DriveSyncCoordinator.Doc d:drive.documents())if(primaryId.equals(d.id)){manifest=drive.read(d.uri).getBytes(StandardCharsets.UTF_8);stateId=new JSONObject(new String(manifest,StandardCharsets.UTF_8)).getString("state_file_id");}
            byte[] primaryBytes=null;for(DriveSyncCoordinator.Doc d:drive.documents())if(stateId.equals(d.id))primaryBytes=drive.read(d.uri).getBytes(StandardCharsets.UTF_8);
            if(manifest==null||primaryBytes==null)throw new IOException("Aktueller Drive-Gesamtstand ist nicht vollständig sicherbar.");
            DrivePublication.verify(new JSONObject(new String(manifest,StandardCharsets.UTF_8)),primaryBytes,new JSONArray());
            // Fail before confirmation if any referenced local original is absent/corrupt.
            JSONObject all=PixelMcpReadTools.effective(state);JSONArray photos=new JSONArray(all.getJSONArray("photos").toString());
            JSONObject archived=OwnerJournalRules.archives(all);for(Iterator<String> it=archived.keys();it.hasNext();){String key=it.next();JSONObject entry=archived.getJSONObject(key);if(key.startsWith("photo:")&&entry.has("record"))photos.put(entry.getJSONObject("record"));}
            for(int i=0;i<photos.length();i++){JSONObject photo=photos.getJSONObject(i),m=store.photoAttachment(photo.getString("id"));if(m==null)m=photo;
                try{store.photoAttachmentStore().read(m);}catch(IOException absent){store.photoAttachmentStore().storeExpected(drive.readPhotoVerified(m),m);}
            }
            JSONArray documents=new JSONArray();for(String collection:new String[]{"invoices","offers"}){JSONArray docs=all.getJSONArray(collection);for(int i=0;i<docs.length();i++)documents.put(docs.getJSONObject(i));}
            for(Iterator<String> it=archived.keys();it.hasNext();){String key=it.next();JSONObject entry=archived.getJSONObject(key);if((key.startsWith("invoice:")||key.startsWith("offer:"))&&entry.has("record"))documents.put(entry.getJSONObject("record"));}
            for(int i=0;i<documents.length();i++){JSONObject doc=documents.getJSONObject(i);if(!doc.optString("pdf_storage_name").isEmpty())try{new BusinessDocumentStore(c).read(doc);}catch(IOException absent){new BusinessDocumentStore(c).store(drive.readDocumentVerified(doc),doc);}}
            // Read the same publication again: a concurrent remote change invalidates this backup.
            JSONObject latest=drive.readPrimary(new JSONObject(drive.read("HausGarten-Laptop.json")),device);
            if(!primaryId.equals(latest.optString("drive_parent_publication_id"))||!PixelMcpWriteTools.hash(primary).equals(PixelMcpWriteTools.hash(latest)))throw new IOException("Drive-Stand während der Sicherung geändert. Sicherung erneut erstellen.");
            try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
                add(out,"database/local.sqlite",dbCopy);add(out,"snapshot/local.json",state.toString().getBytes(StandardCharsets.UTF_8));add(out,"snapshot/drive.json",raw.getBytes(StandardCharsets.UTF_8));add(out,"preview.json",preview.toString().getBytes(StandardCharsets.UTF_8));
                add(out,"snapshot/drive-primary.json",primaryBytes);add(out,"snapshot/drive-primary-commit.json",manifest);
                walk(out,c.getFilesDir(),"files/");File prefs=new File(c.getApplicationInfo().dataDir,"shared_prefs");walk(out,prefs,"shared_prefs/");
                // WebView browser drafts and Android credentials require the separate device backup.
                // The operative database, queue, local media and private business files are included.
            }
            LegacyConflictRules.require(store.getState(),preview.getString("preview_hash"));
            if(!PixelMcpWriteTools.hash(state).equals(PixelMcpWriteTools.hash(store.getState())))throw new IOException("Lokaler Bestand wurde während der Sicherung geändert. Sicherung erneut erstellen.");
            JSONObject meta=new JSONObject().put("backup_id",id).put("complete",true).put("scope","local_business_database_queue_all_local_media_and_drive_snapshot").put("confirmation_scope",drive.cloudVerified()?"drive_server_manifest":"provider_readback").put("cloud_confirmed",false).put("drive_publication_id",primaryId).put("complete_device_backup",false).put("preview_hash",preview.getString("preview_hash")).put("sha256",sha(zip)).put("byte_size",zip.length()).put("created_at",java.time.Instant.now().toString());
            store.putOwnerValue("legacy-backup:"+id,meta.toString());JSONObject result=new JSONObject().put("backup",meta).put("preview",preview);store.putOwnerValue("legacy-backup-request:"+PixelMcpWriteTools.hash(request),result.toString());complete=true;return result;
        } finally {if(dbCopy.exists())dbCopy.delete();if(!complete){zip.delete();directory.delete();}}
    }
    static JSONObject verify(Context c,MainActivity.LocalStore s,String id,String preview)throws Exception {
        if(!id.matches("legacy_[0-9a-f]{32}"))throw new IllegalArgumentException("Ungültige Sicherungskennung.");
        JSONObject meta=new JSONObject(s.ownerValue("legacy-backup:"+id,"{}"));File file=new File(new File(new File(c.getNoBackupFilesDir(),"legacy-conflict-backups"),id),"backup.zip");
        if(!meta.optBoolean("complete")||!preview.equals(meta.optString("preview_hash"))||!file.isFile()||!sha(file).equals(meta.optString("sha256")))throw new IllegalArgumentException("Vollständige unveränderte Sicherung fehlt. Nichts abgeschlossen.");return meta;
    }
    private static String sha(File f)throws Exception{java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte b:d.digest())h.append(String.format(Locale.ROOT,"%02x",b&255));return h.toString();}
    private static void walk(ZipOutputStream out,File dir,String prefix)throws Exception{File[] files=dir.listFiles();if(files==null)return;for(File f:files){if(Files.isSymbolicLink(f.toPath()))throw new IOException("Sicherung enthält einen ungeprüften Dateiverweis.");if(f.isDirectory())walk(out,f,prefix+f.getName()+"/");else if(f.isFile())add(out,prefix+f.getName(),f);}}
    private static void add(ZipOutputStream out,String name,File f)throws Exception{out.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.closeEntry();}
    private static void add(ZipOutputStream out,String name,byte[] b)throws Exception{out.putNextEntry(new ZipEntry(name));out.write(b);out.closeEntry();}
}
