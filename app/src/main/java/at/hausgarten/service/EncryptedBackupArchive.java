package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Complete plaintext ZIP and legacy encrypted-container reader. No business activation. */
final class EncryptedBackupArchive {
    static final int FORMAT_VERSION=1;
    // Metadata-only anti-exhaustion limit; payload/media have no fixed archive-size cap.
    static final int MAX_MANIFEST_BYTES=32*1024*1024;
    private static final int BUFFER=64*1024;
    static final class Source {
        final String path;
        final File file;
        Source(String path,File file)throws IOException {requirePath(path);this.path=path;this.file=file;}
    }
    static final class Verified implements AutoCloseable {
        final File directory;
        final JSONObject manifest;
        private Verified(File directory,JSONObject manifest){this.directory=directory;this.manifest=manifest;}
        File file(String path)throws IOException {requirePath(path);return new File(directory,"data/"+path);}
        // Only our freshly created isolated directory is owned here. Never the active store.
        @Override public void close()throws IOException {removeOwned(directory);}
    }
    static JSONObject writePlain(OutputStream destination,JSONObject metadata,List<Source> sources)throws Exception {
        JSONObject manifest=new JSONObject(metadata.toString());manifest.put("formatVersion",FORMAT_VERSION).put("archiveProtection","NONE");
        requireMetadata(manifest,Integer.MAX_VALUE);
        Set<String> names=new HashSet<>();JSONArray files=new JSONArray();long total=0;
        try(ZipOutputStream archive=new ZipOutputStream(destination)){
            archive.setLevel(Deflater.NO_COMPRESSION);
            for(Source source:sources){
                requirePath(source.path);if(!names.add(source.path))throw new IOException("BACKUP_DUPLICATE_FILE");
                if(!source.file.isFile()||Files.isSymbolicLink(source.file.toPath()))throw new IOException("BACKUP_SOURCE_UNAVAILABLE");
                long expectedSize=source.file.length();String expectedHash=sha256(source.file);ZipEntry entry=new ZipEntry("data/"+source.path);entry.setTime(0);archive.putNextEntry(entry);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");long copied;
                try(InputStream in=new BufferedInputStream(new FileInputStream(source.file))){copied=copy(in,archive,digest,expectedSize);}
                archive.closeEntry();if(copied!=expectedSize||!expectedHash.equals(hex(digest.digest())))throw new IOException("BACKUP_SOURCE_CHANGED");
                total=Math.addExact(total,copied);files.put(new JSONObject().put("path",source.path).put("size",copied).put("sha256",expectedHash));
            }
            manifest.put("files",files).put("totalBytes",total).put("completion","COMPLETE");byte[] json=manifest.toString().getBytes(StandardCharsets.UTF_8);
            if(json.length>MAX_MANIFEST_BYTES)throw new IOException("BACKUP_MANIFEST_TOO_LARGE");
            ZipEntry commit=new ZipEntry("manifest.json");commit.setTime(0);archive.putNextEntry(commit);archive.write(json);archive.closeEntry();
        }
        // Completion here only means written. Destination readback is separately required.
        return manifest;
    }
    static Verified verifyAndStage(InputStream encrypted,String recovery,File newDirectory,long availableBytes,int supportedSchema)throws Exception {
        return verifyContainer(encrypted,recovery,newDirectory,availableBytes,supportedSchema,false);
    }
    static Verified verifyPlain(InputStream input,File newDirectory,long availableBytes,int supportedSchema)throws Exception {
        return verifyContainer(input,"",newDirectory,availableBytes,supportedSchema,true);
    }
    static Verified verifyArchive(InputStream input,String recovery,File directory,long bytes,int schema)throws Exception {
        java.io.PushbackInputStream source=new java.io.PushbackInputStream(input,4);byte[] magic=new byte[4];int count=0,n;
        while(count<4&&(n=source.read(magic,count,4-count))!=-1)count+=n;
        source.unread(magic,0,count);
        return verifyContainer(source,recovery,directory,bytes,schema,count==4&&magic[0]==80&&magic[1]==75&&magic[2]==3&&magic[3]==4);
    }
    private static Verified verifyContainer(InputStream encrypted,String recovery,File newDirectory,long availableBytes,int supportedSchema,boolean unencrypted)throws Exception {
        try(InputStream owned=encrypted){
        if(availableBytes<=0||newDirectory.exists()||!newDirectory.mkdirs())throw new IOException("RESTORE_STAGING_UNAVAILABLE");
        boolean verified=false;File plain=new File(newDirectory,"authenticated.zip");
        try{
            // Read the AEAD stream to EOF, including the authenticated final segment, before
            // parsing ZIP metadata. ZipInputStream alone could stop before that final tag.
            try(InputStream decrypted=unencrypted?owned:BackupCrypto.decrypt(owned,recovery);FileOutputStream out=new FileOutputStream(plain)){
                copy(decrypted,BackupStorageBudget.guard(out,newDirectory),null,availableBytes);out.getFD().sync();
            }
            JSONObject manifest;
            try(ZipFile archive=new ZipFile(plain)){
                ZipEntry record=archive.getEntry("manifest.json");if(record==null||record.isDirectory()||record.getSize()<1||record.getSize()>MAX_MANIFEST_BYTES)throw new IOException("BACKUP_MANIFEST_INVALID");
                try(InputStream in=archive.getInputStream(record);ByteArrayOutputStream json=new ByteArrayOutputStream()){
                    copy(in,json,null,MAX_MANIFEST_BYTES);manifest=new JSONObject(new String(json.toByteArray(),StandardCharsets.UTF_8));
                }
                requireMetadata(manifest,supportedSchema);
                if(!"COMPLETE".equals(manifest.optString("completion")))throw new IOException("BACKUP_COMMIT_MISSING");
                JSONArray files=manifest.getJSONArray("files");Map<String,JSONObject> expected=new LinkedHashMap<>();long declaredTotal=0;
                for(int i=0;i<files.length();i++){
                    JSONObject f=files.getJSONObject(i);String path=f.getString("path");requirePath(path);
                    if(expected.put("data/"+path,f)!=null||f.getLong("size")<0||!f.getString("sha256").matches("[0-9a-f]{64}"))throw new IOException("BACKUP_MANIFEST_INVALID");
                    declaredTotal=Math.addExact(declaredTotal,f.getLong("size"));
                }
                if(declaredTotal!=manifest.getLong("totalBytes")||declaredTotal>availableBytes-plain.length())throw new IOException("RESTORE_SPACE_REQUIRED");
                Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=archive.entries();
                while(entries.hasMoreElements()){
                    ZipEntry entry=entries.nextElement();String name=entry.getName();if(!seen.add(name)||entry.isDirectory())throw new IOException("BACKUP_DUPLICATE_FILE");
                    if(name.equals("manifest.json"))continue;
                    JSONObject wanted=expected.get(name);if(wanted==null||entry.getSize()!=wanted.getLong("size"))throw new IOException("BACKUP_FILE_SET_INVALID");
                    File file=new File(newDirectory,name);if(!file.getCanonicalPath().startsWith(newDirectory.getCanonicalPath()+File.separator))throw new IOException("BACKUP_PATH_INVALID");
                    File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("RESTORE_SPACE_REQUIRED");
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");long size;
                    try(InputStream input=archive.getInputStream(entry);FileOutputStream output=new FileOutputStream(file)){
                        size=copy(input,BackupStorageBudget.guard(output,newDirectory),digest,wanted.getLong("size"));output.getFD().sync();
                    }
                    if(size!=wanted.getLong("size")||!wanted.getString("sha256").equals(hex(digest.digest())))throw new IOException("BACKUP_FILE_HASH_MISMATCH");
                }
                if(seen.size()!=expected.size()+1||!seen.containsAll(expected.keySet()))throw new IOException("BACKUP_FILE_SET_INVALID");
            }
            if(!plain.delete())throw new IOException("RESTORE_STAGING_CLEANUP_REQUIRED");
            verified=true;return new Verified(newDirectory,manifest);
        }finally{
            if(!verified)removeOwned(newDirectory);
        }
        }
    }
    private static void requireMetadata(JSONObject m,int supportedSchema)throws Exception {
        if(m.getInt("formatVersion")!=FORMAT_VERSION||m.getInt("schemaVersion")<1||m.getInt("schemaVersion")>supportedSchema)throw new IOException("BACKUP_VERSION_UNSUPPORTED");
        if(!m.getString("applicationId").equals(BuildConfig.APPLICATION_ID)||m.getString("appVersion").isEmpty()||!m.getString("backupId").matches("[a-zA-Z0-9_-]{8,120}")||m.getString("installationId").isEmpty()||m.getString("businessId").isEmpty())throw new IOException("BACKUP_MANIFEST_INVALID");
        java.time.Instant.parse(m.getString("capturedAt"));m.getJSONObject("counts");m.getJSONObject("numberState");m.getJSONArray("lastIssuedDocumentIds");
    }
    private static void requirePath(String path)throws IOException {
        if(path==null||path.isEmpty()||path.length()>1024||path.startsWith("/")||path.endsWith("/")||path.contains("//")||path.indexOf('\\')>=0)throw new IOException("BACKUP_PATH_INVALID");
        for(int i=0;i<path.length();i++)if(path.charAt(i)<32||path.charAt(i)==127)throw new IOException("BACKUP_PATH_INVALID");
        for(String part:path.split("/"))if(part.equals(".")||part.equals(".."))throw new IOException("BACKUP_PATH_INVALID");
    }
    static void checkCancelled()throws java.io.InterruptedIOException {if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("BACKUP_INTERRUPTED");}
    static long copy(InputStream input,OutputStream output,MessageDigest digest,long maximum)throws IOException {
        checkCancelled();
        byte[] buffer=new byte[BUFFER];long count=0;int read;
        while((read=input.read(buffer))!=-1){checkCancelled();if(read==0)continue;if(read>maximum-count)throw new IOException("RESTORE_SPACE_REQUIRED_OR_SOURCE_CHANGED");output.write(buffer,0,read);if(digest!=null)digest.update(buffer,0,read);count+=read;}
        return count;
    }
    static String sha256(File file)throws Exception {
        checkCancelled();MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(file))){byte[] b=new byte[BUFFER];int n;while((n=in.read(b))!=-1){checkCancelled();digest.update(b,0,n);}}return hex(digest.digest());
    }
    static String hex(byte[] value){StringBuilder result=new StringBuilder();for(byte b:value)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    static void removeOwned(File directory)throws IOException {
        if(Files.isSymbolicLink(directory.toPath()))throw new IOException("RESTORE_STAGING_LINK_REJECTED");
        File[] children=directory.listFiles();if(children!=null)for(File child:children){if(child.isDirectory()&&!Files.isSymbolicLink(child.toPath()))removeOwned(child);else if(!child.delete())throw new IOException("RESTORE_STAGING_CLEANUP_REQUIRED");}
        if(directory.exists()&&!directory.delete())throw new IOException("RESTORE_STAGING_CLEANUP_REQUIRED");
    }
    private EncryptedBackupArchive(){}
}
