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
        for(String code:new String[]{"BROWSER_MEDIA_RECORD_MISSING","BACKUP_DATABASE_INTEGRITY_FAILED","BACKUP_FILE_HASH_MISMATCH"}){
            Exception error=BackupScheduleRules.captureFailure("INVENTORY",new IOException(code));
            yes(BackupScheduleRules.errorCode(error).equals(code));
        }
        IOException privateDetail=new IOException("QA path /private/customer-document.pdf");
        Exception sanitized=BackupScheduleRules.captureFailure("INVENTORY_BROWSER_MEDIA",privateDetail);
        yes(BackupScheduleRules.errorCode(sanitized).equals("BACKUP_CAPTURE_INVENTORY_BROWSER_MEDIA_IO_FAILED"));
        yes(sanitized.getCause()==privateDetail);yes(BackupScheduleRules.errorCode(privateDetail).equals("BACKUP_OPERATION_FAILED"));
        yes(BackupScheduleRules.errorCode(new IOException("UNSAFE_SECRET\nQA private text")).equals("BACKUP_OPERATION_FAILED"));
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
        JSONObject failed=backup("qa_a_new","2026-10-29T10:00:00Z","FAILED");failed.getJSONObject("job").put("lastErrorCode","BACKUP_DATABASE_INTEGRITY_FAILED").put("lastAttemptAt","2026-10-29T10:02:00Z");
        JSONObject stale=backup("qa_y_stale","2026-10-27T10:00:00Z","CAPTURING");
        JSONArray all=new JSONArray().put(failed).put(stale).put(success);String unchanged=all.toString();
        JSONObject shown=status(all,new JSONObject().put("limit",1));yes(shown.getJSONObject("latest_job").getString("backup_id").equals("qa_a_new"));yes(shown.getString("state").equals("FAILED"));yes(shown.getJSONArray("jobs").length()==1&&shown.getInt("total")==3);yes(!shown.getString("last_local_verified_at").isEmpty());yes(BackupStatusRules.selfTest(shown).getString("status").equals("FAIL"));yes(BackupStatusRules.message(shown).contains("Datenbank ist technisch nicht lesbar"));
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
    static void warningStatusChecks()throws Exception {
        JSONObject saved=proof(backup("qa_warning_saved","2026-10-28T10:00:00Z","PROVIDER_VERIFIED"));
        saved.getJSONObject("job").put("warning_count",1).put("warnings",new JSONArray().put(new JSONObject().put("kind","photo").put("message","QA original absent")));
        JSONObject result=status(new JSONArray().put(saved),new JSONObject());
        yes(result.getString("state").equals("VERIFIED"));
        yes(result.getJSONObject("latest_job").getJSONArray("warnings").length()==1);
        yes(BackupStatusRules.message(result).startsWith("Gespeichert mit Einschränkungen"));
        yes(BackupStatusRules.message(result).contains("1 Foto fehlt oder ist ungeprüft"));
        yes(BackupStatusRules.failureMessage("BACKUP_FORMAT_UNSUPPORTED").contains("unverschlüsselte lokale ZIP"));
    }
    static void uncheckedArchive(File file,JSONObject manifest,String path,byte[] payload)throws Exception {
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
            zip.putNextEntry(new ZipEntry("data/"+path));zip.write(payload);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes("UTF-8"));zip.closeEntry();
        }
    }
    static void archiveSafetyChecks(File root)throws Exception {
        byte[] payload="QA payload".getBytes("UTF-8");
        JSONObject file=new JSONObject().put("path","database.sqlite").put("size",payload.length).put("sha256",BackupArchive.hex(java.security.MessageDigest.getInstance("SHA-256").digest(payload)));
        JSONObject manifest=metadata().put("formatVersion",1).put("archiveProtection","NONE").put("completion","COMPLETE").put("files",new JSONArray().put(file)).put("totalBytes",payload.length);
        File invalid=new File(root,"qa_untrusted.zip");
        JSONObject changed=new JSONObject(manifest.toString());changed.getJSONArray("files").getJSONObject(0).put("path","../qa_escape.txt");
        uncheckedArchive(invalid,changed,"../qa_escape.txt",payload);
        reject(()->BackupArchive.verifyPlain(new FileInputStream(invalid),new File(root,"unsafe_path"),1024*1024,6));yes(!new File(root,"qa_escape.txt").exists()&&!new File(root,"unsafe_path").exists());
        changed=new JSONObject(manifest.toString());changed.getJSONArray("files").getJSONObject(0).put("sha256","0".repeat(64));
        uncheckedArchive(invalid,changed,"database.sqlite",payload);
        reject(()->BackupArchive.verifyPlain(new FileInputStream(invalid),new File(root,"hash_mismatch"),1024*1024,6));yes(!new File(root,"hash_mismatch").exists());
        changed=new JSONObject(manifest.toString()).put("totalBytes",payload.length+1);
        uncheckedArchive(invalid,changed,"database.sqlite",payload);
        reject(()->BackupArchive.verifyPlain(new FileInputStream(invalid),new File(root,"size_mismatch"),1024*1024,6));
        changed=new JSONObject(manifest.toString()).put("archiveProtection","OBSOLETE_ENCRYPTED_FORMAT");
        uncheckedArchive(invalid,changed,"database.sqlite",payload);
        try{BackupArchive.verifyPlain(new FileInputStream(invalid),new File(root,"unsupported_manifest"),1024*1024,6);throw new AssertionError("protected manifest accepted");}
        catch(IOException unsupported){yes(unsupported.getMessage().equals("BACKUP_FORMAT_UNSUPPORTED"));}
        File existing=new File(root,"existing");yes(existing.mkdir());File sentinel=new File(existing,"qa_preserve.txt");Files.writeString(sentinel.toPath(),"QA retained");
        reject(()->BackupArchive.verifyPlain(new FileInputStream(invalid),existing,1024*1024,6));yes(Files.readString(sentinel.toPath()).equals("QA retained"));
    }
    static void conflictingRecordsRoundTrip(File root)throws Exception {
        JSONObject first=new JSONObject().put("id","qa_conflict_one").put("invoice_number","QA-2026-0042").put("issued_at","2026-10-28T10:00:00Z").put("amount","12.3400");
        JSONObject second=new JSONObject(first.toString()).put("id","qa_conflict_two");
        JSONObject data=new JSONObject().put("invoices",new JSONArray().put(first).put(second));
        JSONObject namespaces=new JSONObject().put("local_invoice_counter",new JSONObject().put("last_number",42));
        byte[] original=new JSONObject().put("effective",data).put("namespaces",namespaces).put("unknown_optional","Grün 🌿").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        File source=new File(root,"qa_conflicts.json"),archive=new File(root,"qa_conflicts.zip");Files.write(source.toPath(),original);
        try(OutputStream output=new FileOutputStream(archive)){
            BackupArchive.writePlain(output,metadata(),Collections.singletonList(new BackupArchive.Source("files/qa-financial-state.json",source)));
        }
        // Same archive reader used by the restore path; synthetic JSON, not a native DB capture.
        try(BackupArchive.Verified restored=BackupArchive.verifyPlain(new FileInputStream(archive),new File(root,"conflict_restore"),1024*1024,6)){
            byte[] restoredBytes=Files.readAllBytes(restored.file("files/qa-financial-state.json").toPath());yes(Arrays.equals(original,restoredBytes));
            JSONObject saved=new JSONObject(new String(restoredBytes,java.nio.charset.StandardCharsets.UTF_8));
            JSONArray invoices=saved.getJSONObject("effective").getJSONArray("invoices");yes(invoices.length()==2);
            yes(!invoices.getJSONObject(0).getString("id").equals(invoices.getJSONObject(1).getString("id")));
            yes(invoices.getJSONObject(0).getString("invoice_number").equals(invoices.getJSONObject(1).getString("invoice_number")));
            yes(saved.getJSONObject("namespaces").getJSONObject("local_invoice_counter").getInt("last_number")==42);
        }
        yes(Arrays.equals(original,Files.readAllBytes(source.toPath())));yes(!new File(root,"conflict_restore").exists());
    }
    static void warningRoundTrip(File root)throws Exception {
        JSONObject photo=new JSONObject().put("id","qa_missing_photo").put("filename","qa_missing.jpg");
        JSONObject state=new JSONObject().put("photos",new JSONArray().put(photo))
            .put("offers",new JSONArray().put(new JSONObject().put("id","qa_offer").put("finalized_at","2026-10-29T10:00:00Z").put("offer_number","42")))
            .put("owner_archive",new JSONObject().put("qa_archived",new JSONObject().put("record",photo)));
        String before=state.toString();
        JSONObject report=BackupInventory.inspect(state,(kind,row)->kind.equals("photo")?"ORIGINAL_FILE_MISSING":"ORIGINAL_METADATA_INCOMPLETE");
        yes(state.toString().equals(before));yes(report.getJSONArray("warnings").length()==2);
        yes(report.getJSONArray("warnings").getJSONObject(0).getString("record_id").equals("qa_missing_photo"));
        yes(BackupInventory.message(report.getJSONArray("warnings")).contains("1 Foto fehlt oder ist ungeprüft"));
        yes(BackupInventory.message(report.getJSONArray("warnings")).contains("1 Dokument fehlt oder ist ungeprüft"));
        JSONObject failedInspection=BackupInventory.inspect(state,(kind,row)->{throw new IOException("qa unreadable metadata");});
        yes(failedInspection.getJSONArray("warnings").length()==2);
        File notes=new File(root,"qa-notes.json"),original=new File(root,"qa-readable.bin"),zip=new File(root,"qa-with-missing.zip");
        Files.writeString(notes.toPath(),report.toString());Files.writeString(original.toPath(),"QA available original even when old metadata disagrees");
        try(OutputStream out=new FileOutputStream(zip)){BackupArchive.writePlain(out,metadata().put("datasetFormat","pixel.snapshot.v2").put("warning_count",2),Arrays.asList(new BackupArchive.Source("backup-notes.json",notes),new BackupArchive.Source("files/original.bin",original)));}
        try(BackupArchive.Verified checked=BackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"qa_warning_restore"),1024*1024,6)){
            yes(Files.readString(checked.file("backup-notes.json").toPath()).equals(report.toString()));
            yes(Files.readString(checked.file("files/original.bin").toPath()).equals(Files.readString(original.toPath())));
            yes(checked.manifest.getInt("warning_count")==2);
        }
    }
    static void interruptedRestore(File root)throws Exception {
        for(int stop=1;stop<=4;stop++){
            File dir=new File(root,"qa_exchange_"+stop);yes(dir.mkdir());
            Map<String,File> live=new LinkedHashMap<>(),prepared=new LinkedHashMap<>();
            for(String key:new String[]{"database","files"}){
                File old=new File(dir,"live_"+key),next=new File(dir,"new_"+key);
                Files.writeString(old.toPath(),"old_"+key);Files.writeString(next.toPath(),"new_"+key);live.put(key,old);prepared.put(key,next);
            }
            JSONObject[] persisted={new JSONObject().put("targets",BackupDatasetExchange.targets(live,prepared,new File(dir,"rollback"))).put("phase","ACTIVATING")};
            BackupDatasetExchange.Journal journal=new BackupDatasetExchange.Journal(){public JSONObject read()throws Exception{return new JSONObject(persisted[0].toString());}public void save(JSONObject state)throws Exception{persisted[0]=new JSONObject(state.toString());}};
            final int failAt=stop;int[] moved={0};
            try{BackupDatasetExchange.install(journal,(from,to)->{BackupDatasetExchange.ATOMIC.move(from,to);if(++moved[0]==failAt)throw new IOException("QA process ended immediately after rename");});throw new AssertionError("missing interruption");}catch(IOException expected){yes(moved[0]==stop);}
            BackupDatasetExchange.rollback(journal,BackupDatasetExchange.ATOMIC);
            yes(journal.read().getString("phase").equals("ROLLED_BACK"));
            for(String key:live.keySet()){yes(Files.readString(live.get(key).toPath()).equals("old_"+key));yes(Files.readString(prepared.get(key).toPath()).equals("new_"+key));}
            BackupDatasetExchange.rollback(journal,BackupDatasetExchange.ATOMIC);yes(journal.read().getString("phase").equals("ROLLED_BACK"));
        }
    }
    public static void main(String[] args)throws Exception{
        diagnosticChecks();statusChecks();warningStatusChecks();
        File root=Files.createTempDirectory("qa_plain_backup_").toFile();try{
            archiveSafetyChecks(root);conflictingRecordsRoundTrip(root);warningRoundTrip(root);interruptedRestore(root);
            File source=new File(root,"qa_database.sqlite"),zip=new File(root,"qa_backup.zip");byte[] bytes="QA local plaintext contents".getBytes("UTF-8");Files.write(source.toPath(),bytes);
            try(OutputStream out=new FileOutputStream(zip)){BackupArchive.writePlain(out,metadata(),Arrays.asList(new BackupArchive.Source("database.sqlite",source)));}
            try(ZipFile readable=new ZipFile(zip)){yes(readable.getEntry("data/database.sqlite")!=null&&readable.getEntry("manifest.json")!=null);}
            try(BackupArchive.Verified checked=BackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"verify"),1024*1024,6)){yes(Arrays.equals(bytes,Files.readAllBytes(checked.file("database.sqlite").toPath())));yes(checked.manifest.getString("archiveProtection").equals("NONE"));}
            yes(!new File(root,"verify").exists());
            byte[] legacy="HG-BACKUP-1\nsynthetic obsolete encrypted container".getBytes("UTF-8");
            try{BackupArchive.verifyPlain(new ByteArrayInputStream(legacy),new File(root,"unsupported"),1024*1024,6);throw new AssertionError("obsolete encrypted container accepted");}
            catch(IOException unsupported){yes(unsupported.getMessage().equals("BACKUP_FORMAT_UNSUPPORTED"));}
            yes(!new File(root,"unsupported").exists());
            reject(()->BackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"old_schema"),1024*1024,5));
            byte[] raw=Files.readAllBytes(zip.toPath());File truncated=new File(root,"qa_truncated.zip");Files.write(truncated.toPath(),Arrays.copyOf(raw,raw.length-7));reject(()->BackupArchive.verifyPlain(new FileInputStream(truncated),new File(root,"truncated"),1024*1024,6));
            File missing=new File(root,"qa_missing.zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(missing))){out.putNextEntry(new ZipEntry("data/database.sqlite"));out.write(bytes);out.closeEntry();}reject(()->BackupArchive.verifyPlain(new FileInputStream(missing),new File(root,"missing"),1024*1024,6));
            reject(()->BackupArchive.verifyPlain(new FileInputStream(zip),new File(root,"space"),1,6));yes(Arrays.equals(bytes,Files.readAllBytes(source.toPath())));
            File saved=new File(root,"qa_saved.zip");int[] writes={0};BackupDocumentTransfer.LocalFile destination=new BackupDocumentTransfer.LocalFile(){public OutputStream write()throws Exception{writes[0]++;return new FileOutputStream(saved);}public InputStream read()throws Exception{return new FileInputStream(saved);}};
            String expected=BackupArchive.sha256(zip);BackupDocumentTransfer.exportLocal(zip,expected,false,destination);yes(BackupArchive.sha256(saved).equals(expected)&&writes[0]==1);
            BackupDocumentTransfer.exportLocal(zip,expected,true,destination);yes(writes[0]==1);
            try(OutputStream output=new FileOutputStream(saved)){output.write(1);}reject(()->BackupDocumentTransfer.exportLocal(zip,expected,true,destination));yes(writes[0]==1);
            BackupDocumentTransfer.exportLocal(zip,expected,false,destination);yes(writes[0]==2&&BackupArchive.sha256(saved).equals(expected));
            reject(()->BackupDocumentTransfer.exportLocal(zip,expected,false,new BackupDocumentTransfer.LocalFile(){public OutputStream write()throws Exception{throw new IOException("QA storage full");}public InputStream read(){return null;}}));
            yes(BackupArchive.sha256(zip).equals(expected));
            File large=new File(root,"qa_large.bin"),largeZip=new File(root,"qa_large.zip");byte[] block=new byte[65536];new Random(27).nextBytes(block);try(OutputStream out=new FileOutputStream(large)){for(int i=0;i<768;i++)out.write(block);}
            try(OutputStream out=new FileOutputStream(largeZip)){BackupArchive.writePlain(out,metadata(),Arrays.asList(new BackupArchive.Source("files/qa_large.bin",large)));}
            try(BackupArchive.Verified checked=BackupArchive.verifyPlain(new FileInputStream(largeZip),new File(root,"large_verify"),192L*1024*1024,6)){yes(BackupArchive.sha256(checked.file("files/qa_large.bin")).equals(BackupArchive.sha256(large)));}
            yes(largeZip.length()>48L*1024*1024);
            System.out.println("PASS "+checks+" plaintext ZIP/no-key/readback/unsupported-format/schema/truncation/manifest/space/48MiB-stream/source-preservation checks");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);}}
    }
}
