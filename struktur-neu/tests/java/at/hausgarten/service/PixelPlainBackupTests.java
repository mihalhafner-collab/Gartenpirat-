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
    static JSONObject backup(String id,String created,String phase)throws Exception {
        return new JSONObject().put("sameInstallation",true).put("sameRestoreEpoch",true).put("job",new JSONObject().put("backupId",id).put("revision","a".repeat(64)).put("createdAt",created).put("phase",phase).put("destination",new JSONObject().put("purpose","pixel_plain_backup")));
    }
    static JSONObject proof(JSONObject record)throws Exception {
        JSONObject job=record.getJSONObject("job");job.put("archive",new JSONObject().put("archiveFormat","PLAIN_ZIP").put("sha256","b".repeat(64)).put("size",100).put("capturedAt","2026-10-28T10:00:00Z").put("localVerifiedAt","2026-10-28T10:01:00Z"));
        job.put("provider",new JSONObject().put("backup_id",job.getString("backupId")).put("archive_sha256","b".repeat(64)).put("byte_size",100).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("verified_at","2026-10-28T10:02:00Z"));return record;
    }
    static JSONObject status(JSONArray records,JSONObject args,String... active)throws Exception{return BackupStatusRules.project(records,args,new HashSet<>(Arrays.asList(active)),java.time.Instant.parse("2026-10-29T12:00:00Z"));}
    static void statusChecks()throws Exception {
        JSONObject empty=status(new JSONArray(),new JSONObject());yes(empty.getString("state").equals("NOT_SAVED"));yes(BackupStatusRules.selfTest(empty).getString("status").equals("NOT TESTABLE"));
        JSONObject success=proof(backup("qa_z_old","2026-10-28T10:00:00Z","PROVIDER_VERIFIED"));
        JSONObject failed=backup("qa_a_new","2026-10-29T10:00:00Z","FAILED");failed.getJSONObject("job").put("lastErrorCode","FINANCIAL_NUMBER_COLLISION").put("lastAttemptAt","2026-10-29T10:02:00Z");
        JSONObject stale=backup("qa_y_stale","2026-10-27T10:00:00Z","CAPTURING");
        JSONArray all=new JSONArray().put(failed).put(stale).put(success);String unchanged=all.toString();
        JSONObject shown=status(all,new JSONObject().put("limit",1));yes(shown.getJSONObject("latest_job").getString("backup_id").equals("qa_a_new"));yes(shown.getString("state").equals("FAILED"));yes(shown.getJSONArray("jobs").length()==1&&shown.getInt("total")==3);yes(!shown.getString("last_local_verified_at").isEmpty());yes(BackupStatusRules.selfTest(shown).getString("status").equals("FAIL"));yes(BackupStatusRules.message(shown).contains("doppelte Nummer"));
        JSONObject page=status(all,new JSONObject().put("offset",1).put("limit",1));yes(page.getString("state").equals("FAILED")&&page.getJSONObject("latest_job").getString("backup_id").equals("qa_a_new"));yes(page.getJSONArray("jobs").getJSONObject(0).getString("backup_id").equals("qa_z_old"));
        JSONObject older=status(all,new JSONObject().put("backup_id","qa_z_old"));yes(older.getString("state").equals("VERIFIED"));yes(BackupStatusRules.selfTest(older).getString("status").equals("PASS"));yes(!BackupStatusRules.selfTest(older).getBoolean("file_rechecked_now")&&!BackupStatusRules.selfTest(older).getBoolean("restore_tested"));
        JSONObject interrupted=status(new JSONArray().put(stale),new JSONObject());yes(interrupted.getString("state").equals("FAILED"));yes(interrupted.getJSONObject("latest_job").getString("last_error_code").equals("BACKUP_INTERRUPTED"));yes(interrupted.getJSONObject("latest_job").getString("recorded_phase").equals("CAPTURING"));
        JSONObject running=status(new JSONArray().put(stale),new JSONObject(),"qa_y_stale");yes(running.getString("state").equals("RUNNING"));yes(BackupStatusRules.selfTest(running).getString("status").equals("NOT TESTABLE"));yes(all.toString().equals(unchanged));
        JSONObject other=new JSONObject(success.toString()).put("sameRestoreEpoch",false);yes(status(new JSONArray().put(other),new JSONObject()).getInt("total")==0);
        other=new JSONObject(success.toString()).put("sameInstallation",false);yes(status(new JSONArray().put(other),new JSONObject()).getInt("total")==0);
        other=new JSONObject(success.toString());other.getJSONObject("job").getJSONObject("destination").put("purpose","legacy_encrypted");yes(status(new JSONArray().put(other),new JSONObject()).getInt("total")==0);yes(status(new JSONArray().put(other),new JSONObject().put("legacy_evidence",true)).getJSONObject("latest_job").getString("phase").equals("PROVIDER_VERIFIED"));
        for(String bad:new String[]{"not a date","2026-11-30T10:00:00Z","2026-10-28T09:00:00Z","2026-10-28T10:00:30Z"}){JSONObject invalid=new JSONObject(success.toString());invalid.getJSONObject("job").getJSONObject("provider").put("verified_at",bad);JSONObject result=status(new JSONArray().put(invalid),new JSONObject());yes(result.getBoolean("clock_review_required")&&result.getString("state").equals("FAILED"));}
        JSONObject corrupt=new JSONObject(success.toString());corrupt.getJSONObject("job").getJSONObject("provider").put("archive_sha256","c".repeat(64));yes(status(new JSONArray().put(corrupt),new JSONObject()).getString("state").equals("FAILED"));
        JSONObject retry=new JSONObject(success.toString());retry.getJSONObject("job").put("lastErrorCode","BACKUP_LOCAL_READBACK_FAILED").put("lastAttemptAt","2026-10-29T11:00:00Z");JSONObject retryStatus=status(new JSONArray().put(retry),new JSONObject());yes(retryStatus.getString("state").equals("FAILED")&&!retryStatus.getString("last_local_verified_at").isEmpty());yes(retry.getJSONObject("job").getString("phase").equals("PROVIDER_VERIFIED"));
        JSONObject utc=backup("qa_z_utc","2026-10-29T09:00:00Z","CAPTURE_PENDING"),offset=backup("qa_a_offset","2026-10-29T10:00:00+02:00","CAPTURE_PENDING");yes(status(new JSONArray().put(offset).put(utc),new JSONObject()).getJSONObject("latest_job").getString("backup_id").equals("qa_z_utc"));
    }
    static void financialChecks()throws Exception {
        JSONObject one=new JSONObject().put("id","qa_invoice_one").put("invoice_number","QA-2026-1").put("issued_at","2026-10-28T10:00:00Z").put("amount",10);
        JSONObject two=new JSONObject(one.toString()).put("id","qa_invoice_two");JSONObject data=new JSONObject().put("invoices",new JSONArray().put(one).put(two));String unchanged=data.toString();
        try{LocalFinancialValidation.inspect(data,new JSONObject());throw new AssertionError("duplicate invoice accepted");}catch(IOException expected){yes(expected.getMessage().equals("FINANCIAL_ISSUED_NUMBER_COLLISION"));}
        yes(data.toString().equals(unchanged));two.put("invoice_number","QA-2026-2");yes(LocalFinancialValidation.inspect(data,new JSONObject()).getString("status").equals("PASS"));
        JSONObject evidence=new JSONObject().put("formatVersion",1).put("external_writers_disabled",true).put("evidence_sha256","a".repeat(64)).put("evidence_reference","qa_review").put("checked_at","2026-10-28T10:00:00Z").put("last_number",2).put("reservations",new JSONArray()).put("issued",new JSONArray().put(new JSONObject().put("invoice_id","qa_external").put("invoice_number","QA-2026-1").put("pdf_sha256","c".repeat(64))));
        JSONObject namespace=new JSONObject().put("local_invoice_external_evidence",evidence).put("local_invoice_counter",new JSONObject().put("last_number",2));
        try{LocalFinancialValidation.inspect(data,namespace);throw new AssertionError("external collision accepted");}catch(IOException expected){yes(expected.getMessage().equals("FINANCIAL_EXTERNAL_NUMBER_COLLISION"));}
        evidence.getJSONArray("issued").getJSONObject(0).put("invoice_id","qa_invoice_one");yes(LocalFinancialValidation.inspect(data,namespace).getString("status").equals("PASS"));
        for(String code:new String[]{"FINANCIAL_ISSUED_NUMBER_COLLISION","FINANCIAL_RESERVATION_COUNTER_COLLISION","FINANCIAL_RESERVATION_INVOICE_COLLISION","FINANCIAL_EXTERNAL_NUMBER_COLLISION"}){yes(BackupScheduleRules.errorCode(BackupScheduleRules.captureFailure("INVENTORY",new IOException(code))).equals(code));yes(BackupStatusRules.failureMessage(code).contains("Rechnungen und Nummern wurden nicht verändert"));}
    }
    public static void main(String[] args)throws Exception{
        diagnosticChecks();statusChecks();financialChecks();
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
