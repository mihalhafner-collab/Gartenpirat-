package at.hausgarten.service;

import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Writes one local ZIP and verifies every byte through the selected document provider. */
final class BackupDocumentTransfer {
    private static final int BUFFER=64*1024;
    interface LocalFile {OutputStream write()throws Exception;InputStream read()throws Exception;}
    static void exportLocal(File archive,String expectedHash,boolean alreadyVerified,LocalFile destination)throws Exception {
        if(!archive.isFile()||!EncryptedBackupArchive.sha256(archive).equals(expectedHash))throw new IOException("BACKUP_LOCAL_SOURCE_CHANGED");
        if(!alreadyVerified)try(InputStream input=new FileInputStream(archive);OutputStream output=destination.write()){
            if(output==null)throw new IOException("BACKUP_LOCAL_WRITE_FAILED");EncryptedBackupArchive.copy(input,output,null,archive.length());output.flush();
        }catch(InterruptedIOException interrupted){throw interrupted;}catch(Exception failure){throw new IOException("BACKUP_LOCAL_WRITE_FAILED",failure);}
        try(InputStream input=destination.read()){
            verifyReadback(input,expectedHash,archive.length());
        }catch(InterruptedIOException interrupted){throw interrupted;}catch(Exception failure){throw new IOException("BACKUP_LOCAL_READBACK_FAILED",failure);}
    }
    static void verifyReadback(InputStream input,String expected,long size)throws Exception {if(input==null||!hash(input,size).equals(expected))throw new IOException("BACKUP_LOCAL_READBACK_FAILED");}
    private static String hash(InputStream input,long maximum)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[BUFFER];long count=0;int n;while((n=input.read(buffer))!=-1){EncryptedBackupArchive.checkCancelled();if(n>maximum-count)return "";digest.update(buffer,0,n);count+=n;}return count==maximum?hex(digest.digest()):"";}
    private static String hex(byte[] value){StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private BackupDocumentTransfer(){}
}
