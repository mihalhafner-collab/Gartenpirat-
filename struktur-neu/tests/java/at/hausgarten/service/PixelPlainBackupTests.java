package at.hausgarten.service;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
public final class PixelPlainBackupTests {
    static int checks;static void yes(boolean v){checks++;if(!v)throw new AssertionError("plain backup "+checks);}
    interface Case {void run()throws Exception;}static void reject(Case test)throws Exception{try{test.run();}catch(Exception expected){checks++;return;}throw new AssertionError("invalid plain archive accepted");}
    static JSONObject metadata()throws Exception{return new JSONObject().put("schemaVersion",6).put("applicationId","at.hausgarten.service").put("appVersion","qa").put("backupId","qa_plain_backup").put("installationId","qa_install").put("businessId","qa_business").put("capturedAt","2026-10-28T00:00:00Z").put("counts",new JSONObject()).put("numberState",new JSONObject()).put("lastIssuedDocumentIds",new JSONArray());}
    static void diagnosticChecks()throws Exception{
        // These failures used to collapse to BACKUP_CAPTURE_INVENTORY_IO_FAILED.
        for(String code:new String[]{"BROWSER_MEDIA_RECORD_MISSING","FINANCIAL_NUMBER_COLLISION","FINANCIAL_ORIGINAL_HASH_MISMATCH"}){
            Exception error=BackupScheduleRules.captureFailure("INVENTORY",new IOException(code));
            yes(BackupScheduleRules.errorCode(error).equals(code));
        }
        IOException privateDetail=new IOException("QA path /private/customer-document.pdf");
        Exception sanitized=BackupScheduleRules.captureFailure("INVENTORY_BROWSER_MEDIA",privateDetail);
        yes(BackupScheduleRules.errorCode(sanitized).equals("BACKUP_CAPTURE_INVENTORY_BROWSER_MEDIA_IO_FAILED"));
        yes(sanitized.getCause()==privateDetail);yes(BackupScheduleRules.errorCode(privateDetail).equals("BACKUP_OPERATION_FAILED"));
        yes(BackupScheduleRules.errorCode(new IOException("FINANCIAL_SECRET\nQA private text")).equals("BACKUP_OPERATION_FAILED"));
        InterruptedIOException interrupted=new InterruptedIOException("QA cancelled");yes(BackupScheduleRules.captureFailure("INVENTORY",interrupted)==interrupted);
    }
    public static void main(String[] args)throws Exception{
        diagnosticChecks();
        File root=Files.createTempDirectory("qa_plain_backup_").toFile();try{
            File source=new File(root,"qa_database.sqlite"),zip=new File(root,"qa_backup.zip");byte[] bytes="QA local plaintext contents".getBytes("UTF-8");Files.write(source.toPath(),bytes);
            try(OutputStream out=new FileOutputStream(zip)){EncryptedBackupArchive.writePlain(out,metadata(),Arrays.asList(new EncryptedBackupArchive.Source("database.sqlite",source)));}
            try(ZipFile readable=new ZipFile(zip)){yes(readable.getEntry("data/database.sqlite")!=null&&readable.getEntry("manifest.json")!=null);}
            try(EncryptedBackupArchive.Verified checked=EncryptedBackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"verify"),1024*1024,6)){yes(Arrays.equals(bytes,Files.readAllBytes(checked.file("database.sqlite").toPath())));yes(checked.manifest.getString("archiveProtection").equals("NONE"));}
            yes(!new File(root,"verify").exists());
            try(EncryptedBackupArchive.Verified checked=EncryptedBackupArchive.verifyArchive(new FileInputStream(zip),"",new File(root,"detect"),1024*1024,6)){yes(checked.manifest.getInt("schemaVersion")==6);}
            reject(()->EncryptedBackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"old_schema"),1024*1024,5));
            byte[] raw=Files.readAllBytes(zip.toPath());File truncated=new File(root,"qa_truncated.zip");Files.write(truncated.toPath(),Arrays.copyOf(raw,raw.length-7));reject(()->EncryptedBackupArchive.verifyPlain(new FileInputStream(truncated),new File(root,"truncated"),1024*1024,6));
            File missing=new File(root,"qa_missing.zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(missing))){out.putNextEntry(new ZipEntry("data/database.sqlite"));out.write(bytes);out.closeEntry();}reject(()->EncryptedBackupArchive.verifyPlain(new FileInputStream(missing),new File(root,"missing"),1024*1024,6));
            reject(()->EncryptedBackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"space"),1,6));yes(Arrays.equals(bytes,Files.readAllBytes(source.toPath())));
            File saved=new File(root,"qa_saved.zip");int[] writes={0};BackupDocumentTransfer.LocalFile destination=new BackupDocumentTransfer.LocalFile(){public OutputStream write()throws Exception{writes[0]++;return new FileOutputStream(saved);}public InputStream read()throws Exception{return new FileInputStream(saved);}};
            String expected=EncryptedBackupArchive.sha256(zip);BackupDocumentTransfer.exportLocal(zip,expected,false,destination);yes(EncryptedBackupArchive.sha256(saved).equals(expected)&&writes[0]==1);
            BackupDocumentTransfer.exportLocal(zip,expected,true,destination);yes(writes[0]==1);
            try(OutputStream output=new FileOutputStream(saved)){output.write(1);}reject(()->BackupDocumentTransfer.exportLocal(zip,expected,true,destination));yes(writes[0]==1);
            BackupDocumentTransfer.exportLocal(zip,expected,false,destination);yes(writes[0]==2&&EncryptedBackupArchive.sha256(saved).equals(expected));
            reject(()->BackupDocumentTransfer.exportLocal(zip,expected,false,new BackupDocumentTransfer.LocalFile(){public OutputStream write()throws Exception{throw new IOException("QA storage full");}public InputStream read(){return null;}}));
            yes(EncryptedBackupArchive.sha256(zip).equals(expected));
            File large=new File(root,"qa_large.bin"),largeZip=new File(root,"qa_large.zip");byte[] block=new byte[65536];new Random(27).nextBytes(block);try(OutputStream out=new FileOutputStream(large)){for(int i=0;i<768;i++)out.write(block);}
            try(OutputStream out=new FileOutputStream(largeZip)){EncryptedBackupArchive.writePlain(out,metadata(),Arrays.asList(new EncryptedBackupArchive.Source("files/qa_large.bin",large)));}
            try(EncryptedBackupArchive.Verified checked=EncryptedBackupArchive.verifyPlain(new FileInputStream(largeZip),new File(root,"large_verify"),192L*1024*1024,6)){yes(EncryptedBackupArchive.sha256(checked.file("files/qa_large.bin")).equals(EncryptedBackupArchive.sha256(large)));}
            yes(largeZip.length()>48L*1024*1024);
            System.out.println("PASS "+checks+" plaintext ZIP/no-key/readback/auto-detect/schema/truncation/manifest/space/48MiB-stream/source-preservation checks");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);}}
    }
}
