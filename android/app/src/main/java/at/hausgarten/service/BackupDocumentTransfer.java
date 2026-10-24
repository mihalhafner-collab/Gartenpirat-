package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Provider-neutral, resumable immutable-generation protocol; never overwrites an older generation. */
final class BackupDocumentTransfer {
    interface Documents {
        List<String> find(String displayName)throws Exception;
        String create(String displayName,String mime)throws Exception;
        InputStream read(String id)throws Exception;
        OutputStream replaceOwnCandidate(String id)throws Exception;
    }
    interface Checkpoint {JSONObject read()throws Exception;void save(JSONObject state)throws Exception;}
    interface ArchiveVerifier {void verify(InputStream ciphertext)throws Exception;}
    private interface CommitCodec {JSONObject read(InputStream source)throws Exception;void write(OutputStream destination,JSONObject value)throws Exception;}
    private static final int BUFFER=64*1024;
    static JSONObject export(File archive,File localCommit,String recovery,Documents destination,Checkpoint saved,ArchiveVerifier verifier)throws Exception {
        return transfer(archive,localCommit,destination,saved,verifier,new CommitCodec(){public JSONObject read(InputStream source)throws Exception{return readCommit(source,recovery);}public void write(OutputStream destination,JSONObject value)throws Exception{try(OutputStream encrypted=BackupCrypto.encrypt(destination,recovery)){encrypted.write(value.toString().getBytes(StandardCharsets.UTF_8));}}});
    }
    static JSONObject exportPortable(File archive,File localCommit,Documents destination,Checkpoint saved,ArchiveVerifier verifier)throws Exception {
        return transfer(archive,localCommit,destination,saved,verifier,new CommitCodec(){public JSONObject read(InputStream source)throws Exception{return readPlainCommit(source);}public void write(OutputStream destination,JSONObject value)throws Exception{destination.write(value.toString().getBytes(StandardCharsets.UTF_8));destination.flush();}});
    }
    private static JSONObject transfer(File archive,File localCommit,Documents destination,Checkpoint saved,ArchiveVerifier verifier,CommitCodec codec)throws Exception {
        JSONObject state=saved.read();String sha=state.getString("archive_sha256");long size=state.getLong("byte_size");
        if(!archive.isFile()||archive.length()!=size||!sha.equals(EncryptedBackupArchive.sha256(archive)))throw new IOException("BACKUP_SAF_SOURCE_CHANGED");
        if(state.optString("phase").equals("COMMITTED"))return state;
        String candidate=state.optString("candidate_id","");
        if(candidate.isEmpty()){
            candidate=findOrCreate(destination,state,saved,"archive_name","candidate_id","CANDIDATE_CREATE_REQUESTED","application/octet-stream");state=saved.read();
        }
        String phase=state.getString("phase");
        if(state.optBoolean("candidate_unconfirmed")){
            try(InputStream input=destination.read(candidate)){if(!hash(input,size).equals(sha))throw new IOException("BACKUP_SAF_CREATE_RESPONSE_UNCERTAIN");}
            state.put("candidate_unconfirmed",false).put("phase","WRITTEN");saved.save(state);phase="WRITTEN";
        }
        if(!Arrays.asList("WRITTEN","READBACK_VERIFIED","COMMIT_CREATE_REQUESTED","COMMIT_CREATED","COMMIT_WRITING").contains(phase)){
            state.put("phase","WRITING");saved.save(state);
            try(InputStream input=new FileInputStream(archive);OutputStream output=destination.replaceOwnCandidate(candidate)){copy(input,output,size);}
            state.put("phase","WRITTEN");saved.save(state);
        }
        // Even a lost prior readback reply is rechecked against the complete ciphertext.
        MessageDigest digest=MessageDigest.getInstance("SHA-256");CountingInputStream counted;
        try(InputStream input=destination.read(candidate)){
            counted=new CountingInputStream(input,digest,size);verifier.verify(counted);
        }
        if(counted.count!=size||!sha.equals(hex(digest.digest())))throw new IOException("BACKUP_SAF_READBACK_HASH_MISMATCH");
        state=saved.read();state.put("phase","READBACK_VERIFIED").put("provider_readback_at",Instant.now().toString());saved.save(state);
        JSONObject commit=new JSONObject().put("formatVersion",1).put("kind",state.optString("commit_kind","hausgarten.encrypted-generation.commit")).put("operation_id",state.getString("operation_id")).put("backup_id",state.getString("backup_id")).put("archive_name",state.getString("archive_name")).put("archive_sha256",sha).put("byte_size",size).put("captured_at",state.getString("captured_at"));
        if(localCommit.exists()){
            JSONObject prior=codec.read(new FileInputStream(localCommit));
            if(!PixelMcpWriteTools.hash(prior).equals(PixelMcpWriteTools.hash(commit)))throw new IOException("BACKUP_SAF_COMMIT_SOURCE_CHANGED");
        }else{
            File parent=localCommit.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_SAF_STAGING_UNAVAILABLE");
            File temporary=new File(parent,localCommit.getName()+".part-"+UUID.randomUUID());
            try{
                try(FileOutputStream output=new FileOutputStream(temporary)){
                    OutputStream nonClosing=new FilterOutputStream(output){@Override public void close()throws IOException{flush();}};
                    codec.write(nonClosing,commit);output.getFD().sync();
                }
                if(localCommit.exists())throw new IOException("BACKUP_SAF_COMMIT_SOURCE_CHANGED");Files.move(temporary.toPath(),localCommit.toPath(),StandardCopyOption.ATOMIC_MOVE);
            }catch(Exception failure){if(temporary.exists()&&!temporary.delete())failure.addSuppressed(new IOException("BACKUP_SAF_OWNED_TEMP_CLEANUP_REQUIRED"));throw failure;}
        }
        String markerSha=EncryptedBackupArchive.sha256(localCommit);state=saved.read();
        if(state.has("commit_sha256")&&!state.getString("commit_sha256").equals(markerSha))throw new IOException("BACKUP_SAF_COMMIT_SOURCE_CHANGED");
        state.put("commit_sha256",markerSha).put("commit_byte_size",localCommit.length());saved.save(state);
        String marker=state.optString("commit_id","");
        if(marker.isEmpty()){marker=findOrCreate(destination,state,saved,"commit_name","commit_id","COMMIT_CREATE_REQUESTED","application/octet-stream");state=saved.read();}
        boolean existingMatches=false;
        try(InputStream input=destination.read(marker)){existingMatches=hash(input,localCommit.length()).equals(markerSha);}catch(FileNotFoundException empty){/* Newly created or unavailable candidate, no protected prior generation. */}
        if(!existingMatches&&state.optBoolean("commit_unconfirmed"))throw new IOException("BACKUP_SAF_CREATE_RESPONSE_UNCERTAIN");
        if(!existingMatches){
            state.put("phase","COMMIT_WRITING");saved.save(state);
            try(InputStream input=new FileInputStream(localCommit);OutputStream output=destination.replaceOwnCandidate(marker)){copy(input,output,localCommit.length());}
        }
        try(InputStream input=destination.read(marker)){if(!hash(input,localCommit.length()).equals(markerSha))throw new IOException("BACKUP_SAF_COMMIT_READBACK_MISMATCH");}
        try(InputStream input=destination.read(marker)){if(!PixelMcpWriteTools.hash(codec.read(input)).equals(PixelMcpWriteTools.hash(commit)))throw new IOException("BACKUP_SAF_COMMIT_CONTENT_MISMATCH");}
        state=saved.read();state.put("phase","COMMITTED").put("verified_at",Instant.now().toString()).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("cloud_confirmed",false);saved.save(state);return state;
    }
    private static String findOrCreate(Documents destination,JSONObject state,Checkpoint saved,String nameKey,String idKey,String requested,String mime)throws Exception {
        String attemptedKey=idKey.equals("commit_id")?"commit_create_attempted":"candidate_create_attempted",unconfirmedKey=idKey.equals("commit_id")?"commit_unconfirmed":"candidate_unconfirmed";
        List<String> found=destination.find(state.getString(nameKey));if(found.size()>1)throw new IOException("BACKUP_SAF_DUPLICATE_CANDIDATE");
        if(found.size()==1){if(!state.optBoolean(attemptedKey))throw new IOException("BACKUP_SAF_NAME_COLLISION");String id=found.get(0);state.put(idKey,id).put(unconfirmedKey,true).put("phase",idKey.equals("commit_id")?"COMMIT_CREATED":"CANDIDATE_CREATED");saved.save(state);return id;}
        if(state.optBoolean(attemptedKey))throw new IOException("BACKUP_SAF_CREATE_RESPONSE_UNCERTAIN");
        state.put("phase",requested).put(attemptedKey,true);saved.save(state);String id=destination.create(state.getString(nameKey),mime);
        if(id==null||id.isEmpty())throw new IOException("BACKUP_SAF_CREATE_RESPONSE_UNCERTAIN");
        state.put(idKey,id).put("phase",idKey.equals("commit_id")?"COMMIT_CREATED":"CANDIDATE_CREATED");saved.save(state);return id;
    }
    static JSONObject readCommit(InputStream ciphertext,String recovery)throws Exception {
        try(InputStream owned=ciphertext;InputStream clear=BackupCrypto.decrypt(owned,recovery);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=clear.read(buffer))!=-1){EncryptedBackupArchive.checkCancelled();if(n>65536-output.size())throw new IOException("BACKUP_SAF_COMMIT_TOO_LARGE");output.write(buffer,0,n);}JSONObject value=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));if(value.getInt("formatVersion")!=1||!value.getString("kind").equals("hausgarten.encrypted-generation.commit"))throw new IOException("BACKUP_SAF_COMMIT_FORMAT_UNSUPPORTED");return value;
        }
    }
    private static JSONObject readPlainCommit(InputStream source)throws Exception {
        try(InputStream input=source;ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(n>65536-output.size())throw new IOException("BACKUP_SAF_COMMIT_TOO_LARGE");output.write(b,0,n);}JSONObject value=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));if(value.getInt("formatVersion")!=1||!value.getString("kind").equals("hausgarten.portable-export.commit"))throw new IOException("BACKUP_SAF_COMMIT_FORMAT_UNSUPPORTED");return value;}
    }
    private static void copy(InputStream input,OutputStream output,long expected)throws Exception {long bytes=0;byte[] buffer=new byte[BUFFER];int n;while((n=input.read(buffer))!=-1){EncryptedBackupArchive.checkCancelled();if(n>expected-bytes)throw new IOException("BACKUP_SAF_SOURCE_CHANGED");output.write(buffer,0,n);bytes+=n;}if(bytes!=expected)throw new IOException("BACKUP_SAF_SOURCE_CHANGED");output.flush();}
    private static String hash(InputStream input,long maximum)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[BUFFER];long count=0;int n;while((n=input.read(buffer))!=-1){EncryptedBackupArchive.checkCancelled();if(n>maximum-count)return "";digest.update(buffer,0,n);count+=n;}return count==maximum?hex(digest.digest()):"";}
    private static String hex(byte[] value){StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static final class CountingInputStream extends FilterInputStream {
        final MessageDigest digest;final long maximum;long count;
        CountingInputStream(InputStream input,MessageDigest digest,long maximum){super(input);this.digest=digest;this.maximum=maximum;}
        @Override public int read()throws IOException{int value=super.read();if(value>=0){if(count>=maximum)throw new IOException("BACKUP_SAF_READBACK_SIZE_MISMATCH");digest.update((byte)value);count++;}return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=in.read(bytes,offset,length);if(n>0){if(n>maximum-count)throw new IOException("BACKUP_SAF_READBACK_SIZE_MISMATCH");digest.update(bytes,offset,n);count+=n;}return n;}
    }
    private BackupDocumentTransfer(){}
}
