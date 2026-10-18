package at.hausgarten.service;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Same journal protocol, direct Google HTTPS transport. Never rewrites legacy files. */
final class DriveDirectFiles extends DriveSyncCoordinator.Files {
    private final DriveRestClient client;
    DriveDirectFiles(Context context,DriveRestClient client){super(context,Uri.EMPTY,context.getSharedPreferences("haus_garten",0));this.client=client;}
    @Override boolean cloudVerified(){return true;}
    @Override void writeDocumentVerified(JSONObject m,byte[] bytes)throws Exception{
        if(!m.optString("pdf_storage_name").equals(DriveRestClient.sha256(bytes)+".pdf")||bytes.length!=m.getLong("pdf_byte_size"))throw new IOException("Dokument-Prüfsumme stimmt nicht überein.");client.put(client.ensureDirectory("HausGarten-Dokumente"),m.getString("pdf_storage_name"),"application/pdf",bytes);
    }
    @Override byte[] readDocumentVerified(JSONObject m)throws Exception{
        String hash=m.getString("pdf_sha256"),name=m.getString("pdf_storage_name");if(!hash.matches("[0-9a-f]{64}")||!name.equals(hash+".pdf"))throw new IOException("Ungültige Dokumentreferenz.");JSONObject directory=client.unique(client.folder,"HausGarten-Dokumente");if(directory==null)throw new IOException("Dokumentordner fehlt.");JSONObject file=client.unique(directory.getString("id"),name);if(file==null)throw new IOException("Original-PDF fehlt.");byte[] raw=client.read(file.getString("id"),20*1024*1024);if(!DriveRestClient.sha256(raw).equals(hash)||raw.length!=m.getLong("pdf_byte_size"))throw new IOException("Dokument-Prüfsumme stimmt nicht überein.");return raw;
    }
    @Override ArrayList<DriveSyncCoordinator.Doc> documents()throws Exception{
        ArrayList<DriveSyncCoordinator.Doc> out=new ArrayList<>();JSONArray rows=client.list(client.folder);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);out.add(new DriveSyncCoordinator.Doc(row.getString("id"),row.getString("name"),Uri.parse("https://www.googleapis.com/drive/v3/files/"+row.getString("id")),0));}return out;
    }
    @Override String read(Uri uri)throws Exception{return new String(client.read(uri.getLastPathSegment(),DriveRestClient.MAX_JSON),StandardCharsets.UTF_8);}
    @Override String read(String name)throws Exception{JSONObject file=client.unique(client.folder,name);return file==null?null:new String(client.read(file.getString("id"),DriveRestClient.MAX_JSON),StandardCharsets.UTF_8);}
    @Override void writeVerified(String name,JSONObject object,Map<String,String> originals)throws Exception{
        JSONObject stable=new JSONObject(object.toString());stable.remove("exported_at");byte[] bytes=PixelMcpWriteTools.canonical(stable).getBytes(StandardCharsets.UTF_8);String immutable=name.substring(0,name.length()-5)+"-"+DriveRestClient.sha256(bytes)+".json";client.put(client.folder,immutable,"application/json",bytes);
    }
    @Override JSONArray readActions(String name)throws Exception{
        JSONArray result=new JSONArray();String prefix=name.substring(0,name.length()-5)+"-";
        for(DriveSyncCoordinator.Doc doc:documents())if(doc.name.equals(name)||(doc.name.startsWith(prefix)&&doc.name.endsWith(".json")))result=DriveJournalRules.union(result,new JSONObject(read(doc.uri)).getJSONArray("actions"));return result;
    }
    @Override byte[] readPhotoVerified(JSONObject metadata)throws Exception{
        JSONObject directory=client.unique(client.folder,"HausGarten-Fotos");if(directory==null)throw new IOException("Foto-Original ist in Drive noch nicht verfügbar.");JSONObject file=client.unique(directory.getString("id"),metadata.getString("storage_name"));if(file==null)throw new IOException("Foto-Original ist in Drive noch nicht verfügbar.");byte[] bytes=client.read(file.getString("id"),PhotoAttachmentStore.MAX_BYTES);verifyBytes(bytes,metadata);return bytes;
    }
    @Override void writePhotoVerified(JSONObject metadata,PhotoAttachmentStore local)throws Exception{
        byte[] bytes=local.read(metadata);verifyBytes(bytes,metadata);client.put(client.ensureDirectory("HausGarten-Fotos"),metadata.getString("storage_name"),metadata.getString("mime_type"),bytes);
    }
    @Override byte[] readThumbnailVerified(String name)throws Exception{
        if(!name.matches("[0-9a-f]{64}\\.jpg"))throw new IOException("Ungültige Vorschaureferenz.");JSONObject folder=client.unique(client.folder,"HausGarten-Fotos");if(folder==null)throw new IOException("Fotoordner fehlt.");JSONObject f=client.unique(folder.getString("id"),name);if(f==null)throw new IOException("Vorschau fehlt.");byte[] raw=client.read(f.getString("id"),65536);if(!name.equals(DriveRestClient.sha256(raw)+".jpg"))throw new IOException("Vorschau-Prüfsumme stimmt nicht überein.");return raw;
    }
    @Override void writeThumbnailVerified(JSONObject metadata,PhotoAttachmentStore local)throws Exception{
        String name=metadata.optString("thumbnail_storage_name");if(name.isEmpty())return;byte[] raw=local.readThumbnail(name);client.put(client.ensureDirectory("HausGarten-Fotos"),name,"image/jpeg",raw);
    }
    @Override JSONObject publish(JSONObject snapshot,JSONArray actions)throws Exception{
        JSONArray missing=new JSONArray(),photos=snapshot.optJSONArray("photos");String directory="";
        for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.getJSONObject(i);
            try{
                if(!PhotoAttachmentStore.canonicalName(photo.optString("storage_name"))||!photo.optString("content_sha256").matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Für dieses ältere Foto fehlen geprüfte Original-Metadaten.");
                if(directory.isEmpty()){JSONObject d=client.unique(client.folder,"HausGarten-Fotos");if(d!=null)directory=d.getString("id");}
                try{if(directory.isEmpty())throw new IOException("Noch kein Fotoordner.");client.confirm(directory,photo.getString("storage_name"),photo.getString("content_sha256"),PhotoAttachmentStore.MAX_BYTES);}
                catch(IOException unavailable){if(unavailable instanceof DriveRestClient.Failure&&((DriveRestClient.Failure)unavailable).userAction)throw unavailable;
                    PhotoAttachmentStore local=new PhotoAttachmentStore(context);try{local.read(photo);}catch(Exception absent){String legacy=prefs.getString("drive_tree_uri","");if(legacy.isEmpty())throw absent;local.storeExpected(new DriveSyncCoordinator.Files(context,Uri.parse(legacy),prefs).readPhotoVerified(photo),photo);}
                    writePhotoVerified(photo,local);directory=client.ensureDirectory("HausGarten-Fotos");client.confirm(directory,photo.getString("storage_name"),photo.getString("content_sha256"),PhotoAttachmentStore.MAX_BYTES);
                }
            }catch(Exception error){missing.put(new JSONObject().put("photo_id",photo.optString("id")).put("code",error instanceof DriveRestClient.Failure?((DriveRestClient.Failure)error).code:"MEDIA_ORIGINAL_REQUIRED").put("message","Original und Zuordnung prüfen; vorhandene Bilder bleiben erhalten."));}
        }
        JSONObject exchange=new JSONObject(snapshot.toString());JSONArray invoices=exchange.optJSONArray("invoices");String documentFolder="";
        JSONArray offers=exchange.optJSONArray("offers");for(int i=0;offers!=null&&i<offers.length();i++){JSONObject offer=offers.getJSONObject(i);if(!offer.optString("pdf_storage_name").isEmpty())try{readDocumentVerified(offer);}catch(Exception error){missing.put(new JSONObject().put("entity_type","offer").put("entity_id",offer.optString("id")).put("code","DOCUMENT_ORIGINAL_REQUIRED").put("message","Angebots-PDF und Drive-Verbindung prüfen; Original bleibt erhalten."));}}
        for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject invoice=invoices.getJSONObject(i);String inline=invoice.optString("pdf_base64");
            try{if(!inline.isEmpty()){if(inline.length()>28*1024*1024)throw new IOException("PDF-Original ist zu groß.");byte[] pdf=Base64.getDecoder().decode(inline);if(pdf.length<5||pdf.length>20*1024*1024||!new String(pdf,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("PDF-Original ist ungültig.");String digest=DriveRestClient.sha256(pdf);if(!invoice.optString("pdf_sha256").isEmpty()&&!digest.equals(invoice.getString("pdf_sha256")))throw new IOException("PDF-Prüfsumme stimmt nicht überein.");if(documentFolder.isEmpty())documentFolder=client.ensureDirectory("HausGarten-Dokumente");client.put(documentFolder,digest+".pdf","application/pdf",pdf);invoice.put("pdf_storage_name",digest+".pdf").put("pdf_sha256",digest).put("pdf_byte_size",pdf.length);}
                else if(!invoice.optString("pdf_storage_name").isEmpty()){String name=invoice.getString("pdf_storage_name"),expected=invoice.getString("pdf_sha256");if(!expected.matches("[0-9a-f]{64}")||!name.equals(expected+".pdf"))throw new IOException("Ungültige PDF-Dateireferenz.");if(documentFolder.isEmpty()){JSONObject d=client.unique(client.folder,"HausGarten-Dokumente");if(d==null)throw new IOException("PDF-Original fehlt in Drive.");documentFolder=d.getString("id");}client.confirm(documentFolder,name,expected,20*1024*1024);}
                else if(!invoice.optString("issued_at").isEmpty())throw new IOException("Original des ausgestellten Belegs fehlt.");
            }catch(Exception error){missing.put(new JSONObject().put("entity_type","invoice").put("entity_id",invoice.optString("id")).put("code","DOCUMENT_ORIGINAL_REQUIRED").put("message","Originalbeleg und Drive-Verbindung prüfen; vorhandenes PDF bleibt erhalten."));}
            invoice.remove("pdf_base64");
        }
        exchange.put("drive_media_errors",missing);snapshot.put("drive_media_errors",missing);
        byte[] state=DrivePublication.stateBytes(exchange);String sha=DriveRestClient.sha256(state);JSONObject stored=client.put(client.folder,"HausGarten-State-"+sha+".json","application/json",state);
        JSONObject history=recoverPublication(snapshot.getString("drive_publisher_id"));
        JSONObject manifest=DrivePublication.manifest(snapshot,actions,stored.getString("id"),sha).put("state_name","HausGarten-State-"+sha+".json").put("publisher_id",snapshot.getString("drive_publisher_id")).put("media_complete",missing.length()==0);
        String generation=DriveRestClient.sha256(PixelMcpWriteTools.canonical(manifest).getBytes(StandardCharsets.UTF_8));boolean same=generation.equals(history.optString("generation"));long sequence=same?history.getLong("sequence"):history.optLong("sequence")+1;String parent=same?history.optString("parent"):history.optString("id"),parentSha=same?history.optString("parent_sha"):history.optString("sha");
        manifest.put("generation_sha256",generation).put("sequence",sequence).put("previous_publication_id",parent).put("previous_state_sha256",parentSha);byte[] bytes=PixelMcpWriteTools.canonical(manifest).getBytes(StandardCharsets.UTF_8);
        JSONObject published=client.put(client.folder,"HausGarten-Commit-"+DriveRestClient.sha256(bytes)+".json","application/json",bytes);
        JSONObject remote=new JSONObject(new String(client.read(published.getString("id"),DriveRestClient.MAX_JSON),StandardCharsets.UTF_8));DrivePublication.verify(remote,client.read(remote.getString("state_file_id"),DriveRestClient.MAX_JSON),actions);
        client.checkpoint("publication",new JSONObject().put("generation",generation).put("sha",sha).put("id",published.getString("id")).put("sequence",sequence).put("parent",parent).put("parent_sha",parentSha).toString());
        return new JSONObject().put("publication_id",published.getString("id")).put("receipts",DrivePublication.progress(remote,published.getString("id")));
    }
    private JSONObject recoverPublication(String publisher)throws Exception{
        JSONArray summaries=new JSONArray(),rows=client.list(client.folder);
        for(int i=0;i<rows.length();i++){JSONObject file=rows.getJSONObject(i);String name=file.optString("name");if(!name.startsWith("HausGarten-Commit-"))continue;
            if(!name.matches("HausGarten-Commit-[0-9a-f]{64}\\.json"))throw new DriveRestClient.Failure("PUBLICATION_INVALID","Eine Drive-Veröffentlichung benötigt Prüfung.",0,true);
            String key="publication-meta:"+file.getString("id"),fingerprint=file.optString("version")+":"+file.optString("md5Checksum")+":"+file.optString("size");String saved=client.checkpoint(key);JSONObject summary=saved==null||saved.isEmpty()?new JSONObject():new JSONObject(saved);
            if(file.optString("version").isEmpty()||!fingerprint.equals(summary.optString("fingerprint"))){byte[] raw=client.read(file.getString("id"),DriveRestClient.MAX_JSON);if(!DriveRestClient.sha256(raw).equals(name.substring(18,name.length()-5)))throw new DriveRestClient.Failure("PUBLICATION_INVALID","Eine Drive-Veröffentlichung wurde verändert. Originale bleiben erhalten.",0,true);JSONObject m=new JSONObject(new String(raw,StandardCharsets.UTF_8));
                if(!"hausgarten.drive.commit.v1".equals(m.optString("format"))||m.optLong("sequence")<1)throw new DriveRestClient.Failure("PUBLICATION_INVALID","Unbekannte Drive-Veröffentlichung benötigt Prüfung.",0,true);
                summary=new JSONObject().put("fingerprint",fingerprint).put("id",file.getString("id")).put("publisher",m.getString("publisher_id")).put("generation",m.optString("generation_sha256")).put("sha",m.getString("state_sha256")).put("sequence",m.getLong("sequence")).put("parent",m.optString("previous_publication_id")).put("parent_sha",m.optString("previous_state_sha256"));client.checkpoint(key,summary.toString());
            }
            if(!publisher.equals(summary.getString("publisher")))throw new DriveRestClient.Failure("PRIMARY_DEVICE_CONFLICT","Der Drive-Ordner enthält einen anderen primären Pixel-Herausgeber. Eigentümerprüfung erforderlich.",0,true);
            summaries.put(summary);
        }
        return DrivePublication.head(summaries,publisher);
    }
    private static void verifyBytes(byte[] bytes,JSONObject metadata)throws Exception{
        if(!PhotoAttachmentStore.canonicalName(metadata.optString("storage_name"))||bytes.length!=metadata.getLong("byte_size")||!PhotoAttachmentStore.sha256(bytes).equals(metadata.getString("content_sha256"))||!PhotoAttachmentStore.sniff(bytes).equals(PhotoAttachmentStore.canonicalMime(metadata.getString("mime_type"))))throw new IOException("Foto-Prüfsumme oder Dateityp stimmt nicht überein.");
    }
}
