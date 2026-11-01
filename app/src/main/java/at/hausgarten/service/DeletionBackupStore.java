package at.hausgarten.service;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Immutable recovery ZIPs in an explicitly supplied app-private directory. */
final class DeletionBackupStore {
 static final String SCOPE="deleted_records_and_originals";
 interface Originals {byte[] read(String entity,JSONObject record)throws Exception;}
 static String sha(File file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
 static JSONArray importance(String type,JSONObject row)throws Exception{
  JSONArray out=new JSONArray();if(type.equals("invoice")||type.equals("offer"))out.put("financial_document");if(type.equals("worklog"))out.put("work_evidence");if(type.equals("photo"))out.put("original_media");if(type.equals("customer"))out.put("customer_account");
  if(!row.optString("notes").trim().isEmpty()||!row.optString("description").trim().isEmpty())out.put("personal_notes");if(!row.optString("pdf_storage_name").isEmpty())out.put("original_document");if(out.length()==0)out.put("user_created_record");return out;
 }
 static JSONObject capture(File root,JSONObject snapshot,JSONArray selected,String request,Originals originals)throws Exception{
  if(selected.length()==0)throw new IllegalArgumentException("Keine bestätigte Löschmenge.");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Private Löschsicherung kann nicht angelegt werden. Nichts gelöscht.");
  JSONArray entries=new JSONArray(),files=new JSONArray();JSONObject counts=new JSONObject();for(String type:DeletionRules.ENTITIES)counts.put(OwnerJournalRules.group(type),0);LinkedHashMap<String,JSONObject> sources=new LinkedHashMap<>();
  for(int i=0;i<selected.length();i++){
   JSONObject item=selected.getJSONObject(i);String type=item.getString("entity"),id=item.getString("entity_id"),key=type+":"+id;if(!Arrays.asList(DeletionRules.ENTITIES).contains(type))throw new IllegalArgumentException("Unbekannter Sicherungsbereich.");
   JSONObject saved=OwnerJournalRules.archives(snapshot).getJSONObject(key);OwnerJournalRules.checkHash(saved,item.getString("expected_record_hash"));JSONObject row=saved.getJSONObject("record");if(!id.equals(row.getString("id")))throw new IllegalArgumentException("Sicherungs-ID stimmt nicht mit Archiv überein.");
   entries.put(new JSONObject().put("archive_key",key).put("archive",saved).put("importance",importance(type,row)));String group=OwnerJournalRules.group(type);counts.put(group,counts.getInt(group)+1);
   String name=type.equals("photo")?row.optString("storage_name"):row.optString("pdf_storage_name");if(name.isEmpty())continue;String hash=row.optString(type.equals("photo")?"content_sha256":"pdf_sha256");
   if(!hash.matches("[0-9a-f]{64}")||!name.matches("[0-9a-f]{64}\\.[a-zA-Z0-9]+")||!name.startsWith(hash+"."))throw new IOException("Original hat keine eindeutige Prüfsumme. Nichts gelöscht.");
   if(!sources.containsKey(name)){JSONObject file=new JSONObject().put("name",name).put("sha256",hash).put("byte_size",row.getLong(type.equals("photo")?"byte_size":"pdf_byte_size"));files.put(file);sources.put(name,new JSONObject().put("entity",type).put("record",row).put("file",file));}
  }
  JSONObject manifest=new JSONObject().put("format","hausgarten.deletion-backup.v1").put("created_at",java.time.Instant.now().toString()).put("entries",entries).put("files",files).put("collections",counts).put("includes_credentials",false);
  File temporary=File.createTempFile("deletion-",".partial",root);
  try{
   try(FileOutputStream raw=new FileOutputStream(temporary);ZipOutputStream zip=new ZipOutputStream(raw)){
    zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    for(Map.Entry<String,JSONObject> item:sources.entrySet()){JSONObject source=item.getValue(),file=source.getJSONObject("file");byte[] bytes=originals.read(source.getString("entity"),source.getJSONObject("record"));
     if(bytes.length!=file.getLong("byte_size")||!HttpTransport.sha256(bytes).equals(file.getString("sha256")))throw new IOException("Original für Löschsicherung stimmt nicht überein. Nichts gelöscht.");
     zip.putNextEntry(new ZipEntry("originals/"+item.getKey()));zip.write(bytes);zip.closeEntry();}
    zip.finish();zip.flush();raw.getFD().sync();
   }
   verify(temporary,manifest);String hash=sha(temporary);File target=new File(root,hash+".zip");if(target.exists()){if(!sha(target).equals(hash))throw new IOException("Vorhandene Löschsicherung ist verändert.");}else if(!temporary.renameTo(target))throw new IOException("Löschsicherung konnte nicht dauerhaft gespeichert werden. Nichts gelöscht.");
   JSONObject meta=new JSONObject().put("kind","backup").put("scope",SCOPE).put("mime_type","application/zip").put("storage_name",hash+".zip").put("sha256",hash).put("byte_size",target.length()).put("created_at",manifest.getString("created_at")).put("includes_credentials",false).put("protected_records",entries.length()).put("original_files",files.length()).put("collections",counts).put("verified_at_capture",true);
   return new JSONObject().put("backup_id","backup_deletion_"+PixelMcpWriteTools.hash(request)).put("metadata",meta).put("collections",counts).put("complete_device_backup",false);
  }finally{if(temporary.exists()&&!temporary.delete())temporary.deleteOnExit();}
 }
 private static byte[] read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>16*1024*1024)throw new IOException("Sicherungsmanifest zu groß.");out.write(b,0,n);}return out.toByteArray();}}
 private static void verify(File file,JSONObject manifest)throws Exception{
  try(ZipFile zip=new ZipFile(file)){if(zip.size()!=manifest.getJSONArray("files").length()+1)throw new IOException("Löschsicherung unvollständig.");JSONObject reread=new JSONObject(new String(read(zip.getInputStream(zip.getEntry("manifest.json"))),StandardCharsets.UTF_8));if(!PixelMcpWriteTools.hash(reread).equals(PixelMcpWriteTools.hash(manifest)))throw new IOException("Löschsicherung nicht vollständig zurückgelesen.");
   JSONArray files=manifest.getJSONArray("files");for(int i=0;i<files.length();i++){JSONObject expected=files.getJSONObject(i);ZipEntry entry=zip.getEntry("originals/"+expected.getString("name"));if(entry==null||entry.getSize()!=expected.getLong("byte_size"))throw new IOException("Gesichertes Original fehlt.");MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=zip.getInputStream(entry)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder hash=new StringBuilder();for(byte b:d.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!hash.toString().equals(expected.getString("sha256")))throw new IOException("Gesichertes Original beschädigt.");}
  }
 }
 private static File file(File root,JSONObject meta)throws Exception{String name=meta.getString("storage_name");if(!SCOPE.equals(meta.optString("scope"))||!name.matches("[0-9a-f]{64}\\.zip")||!name.equals(meta.getString("sha256")+".zip"))throw new IOException("Unbekannte Löschsicherung.");File file=new File(root,name);if(!file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())||!file.isFile()||file.length()!=meta.getLong("byte_size"))throw new IOException("Löschsicherung fehlt oder wurde verändert.");return file;}
 static JSONObject inspect(File root,JSONObject meta)throws Exception{
  File file=file(root,meta);if(!sha(file).equals(meta.getString("sha256")))throw new IOException("Löschsicherung beschädigt.");try(ZipFile zip=new ZipFile(file)){JSONObject manifest=new JSONObject(new String(read(zip.getInputStream(zip.getEntry("manifest.json"))),StandardCharsets.UTF_8));if(!"hausgarten.deletion-backup.v1".equals(manifest.optString("format")))throw new IOException("Unbekanntes Sicherungsformat.");return new JSONObject().put("collections",manifest.getJSONObject("collections")).put("protected_records",manifest.getJSONArray("entries").length()).put("original_files",manifest.getJSONArray("files").length()).put("restore_mode","review_deleted_records").put("complete_device_backup",false);}
 }
 /** ZIP chunks avoid large SQLite BLOBs and loading a whole archive into memory. */
 static JSONObject chunk(File root,JSONObject meta,JSONObject args)throws Exception{
  File file=file(root,meta);String hash=meta.getString("sha256");JSONObject out=new JSONObject().put("resource_type","backup").put("resource_id",args.getString("resource_id")).put("mime_type","application/zip").put("byte_size",file.length()).put("sha256",hash);
  if(!args.has("expected_sha256")){if(!sha(file).equals(hash))throw new IOException("Löschsicherung beschädigt.");return out;}if(!hash.equals(args.getString("expected_sha256")))throw new PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT","Sicherungsversion geändert.");
  int offset=args.getInt("offset"),length=args.optInt("length",PixelMcpOwnerTools.CHUNK);if(offset<0||offset>file.length()||length<1||length>PixelMcpOwnerTools.CHUNK)throw new IllegalArgumentException("Ungültiger Sicherungsbereich.");byte[] bytes=new byte[(int)Math.min(length,file.length()-offset)];try(RandomAccessFile in=new RandomAccessFile(file,"r")){in.seek(offset);in.readFully(bytes);}return out.put("offset",offset).put("next_offset",offset+bytes.length).put("eof",offset+bytes.length==file.length()).put("chunk_sha256",HttpTransport.sha256(bytes)).put("chunk_base64",Base64.getEncoder().encodeToString(bytes));
 }
 private DeletionBackupStore(){}
}
