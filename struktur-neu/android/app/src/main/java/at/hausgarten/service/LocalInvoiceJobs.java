package at.hausgarten.service;

import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import android.util.AtomicFile;

/** Local PDF outbox; one immutable frozen snapshot survives process death and retry. */
final class LocalInvoiceJobs {
    static final int JOB_ID=0x484708;private static final ReentrantLock LOCK=new ReentrantLock();
    static String storageScope(Context context){try{return PixelMcpWriteTools.hash(context.getFilesDir().getCanonicalPath());}catch(Exception invalid){throw new IllegalStateException("INVOICE_JOB_SCOPE_UNAVAILABLE",invalid);}}
    static void schedule(Context context){
        if(LocalAuthorityGuard.local(context))try{LocalAuthorityGuard guard=new LocalAuthorityGuard(context);JSONObject authority=guard.current();if("LOCAL_ACTIVE".equals(authority.optString("mode"))&&"RECONCILED".equals(authority.optString("invoice_authority"))&&!LocalAuthorityGuard.restorePending(context))try(PixelLocalStore store=new PixelLocalStore(context)){String counter=store.ownerValue("local_invoice_counter","");if(!counter.isEmpty())guard.advanceInvoiceHighWater(new JSONObject(counter).getInt("last_number"));}}catch(Exception pending){throw new IllegalStateException("INVOICE_AUTHORITY_CHECKPOINT_PENDING",pending);}
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs!=null){android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putString("storage_scope",storageScope(context));jobs.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,LocalInvoiceJobService.class)).setExtras(extras).setPersisted(true).setMinimumLatency(0).build());}}
    static JSONObject run(Context context,PixelLocalStore store)throws Exception {
        if(!LOCK.tryLock())return new JSONObject().put("busy",true);int completed=0,failed=0;
        try{
            ArrayList<String> keys=new ArrayList<>();try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT key FROM owner_state WHERE key LIKE 'local_invoice_job:%' ORDER BY rowid",null)){while(rows.moveToNext())keys.add(rows.getString(0));}
            for(String key:keys){EncryptedBackupArchive.checkCancelled();JSONObject job=new JSONObject(store.ownerValue(key,"{}"));JSONObject frozen=job.getJSONObject("snapshot");if(!PixelMcpWriteTools.hash(frozen).equals(job.getString("snapshot_sha256")))throw new IOException("INVOICE_FROZEN_SNAPSHOT_CHANGED");
                if(job.optString("phase").equals("READY")){
                    try{verifyReady(context,job,PixelMcpWriteTools.find(SnapshotProjection.effective(store.getState()).getJSONArray("invoices"),job.getString("invoice_id")));}catch(Exception changed){job.put("phase","ORIGINAL_REVIEW_REQUIRED").put("last_error_code","INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED");store.putOwnerValue(key,job.toString());failed++;}continue;
                }
                if(job.optString("phase").equals("ORIGINAL_REVIEW_REQUIRED")||job.optInt("attempts")>=8)continue;
                job.put("attempts",job.optInt("attempts")+1).put("phase","PDF_PENDING");store.putOwnerValue(key,job.toString());
                try{
                    // A durable file receipt precedes SQL publication. Once it exists, a
                    // retry must use those exact bytes, including after process death.
                    JSONObject metadata=document(context,job);
                    SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                        JSONObject state=store.getState(),all=new JSONObject(state.getJSONObject("master").toString());DriveJournalRules.apply(all,state.getJSONArray("pending"));JSONObject invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),frozen.getString("id"));
                        if(!sameFrozenInvoice(invoice,frozen))throw new IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH");
                        for(Iterator<String> it=metadata.keys();it.hasNext();){String field=it.next();invoice.put(field,metadata.get(field));}invoice.put("pdf_state","READY");OwnerJournalRules.replace(all,"invoices",invoice.getString("id"),invoice);store.commitLocalSnapshot(all);job.put("phase","READY").put("document",metadata).put("verified_at",java.time.Instant.now().toString()).remove("last_error_code");store.putOwnerValue(key,job.toString());db.setTransactionSuccessful();completed++;
                    }finally{db.endTransaction();}
                }catch(Exception error){if(Thread.currentThread().isInterrupted()||error instanceof InterruptedIOException)throw error;boolean originalChanged=error instanceof IOException&&"INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED".equals(error.getMessage());String code=originalChanged?"INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED":error instanceof IOException&&Arrays.asList("INVOICE_DOCUMENT_RECEIPT_INVALID","INVOICE_ORIGINAL_RECONCILIATION_REQUIRED").contains(error.getMessage())?error.getMessage():"INVOICE_PDF_CREATION_OR_STORAGE_FAILED";job.put("phase",originalChanged?"ORIGINAL_REVIEW_REQUIRED":"PDF_PENDING").put("last_error_code",code);store.putOwnerValue(key,job.toString());failed++;}
            }
            return new JSONObject().put("completed",completed).put("failed",failed);
        }finally{LOCK.unlock();}
    }
    static void verifiedOriginal(Context context,JSONObject metadata)throws IOException {
        try{new BusinessDocumentStore(context).read(metadata);}catch(Exception invalid){throw new IOException("INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED");}
    }
    static void verifyReady(Context context,JSONObject job,JSONObject invoice)throws Exception {
        if(!job.optString("phase").equals("READY"))throw new IOException("INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED");
        JSONObject frozen=job.getJSONObject("snapshot"),metadata=job.getJSONObject("document");
        if(!PixelMcpWriteTools.hash(frozen).equals(job.optString("snapshot_sha256"))||!sameFrozenInvoice(invoice,frozen))throw new IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH");
        if(!sameDocument(metadata,invoice))throw new IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH");
        JSONObject receipt=readReceipt(context,job);if(receipt!=null&&!sameDocument(receipt,metadata))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");verifiedOriginal(context,metadata);
    }
    private static boolean sameDocument(JSONObject first,JSONObject second)throws Exception {
        for(String field:new String[]{"document_id","pdf_sha256","pdf_storage_name","pdf_byte_size"})if(!first.has(field)||!second.has(field)||!first.get(field).toString().equals(second.get(field).toString()))return false;return true;
    }
    private static boolean sameFrozenInvoice(JSONObject invoice,JSONObject frozen)throws Exception {return LocalFinancialValidation.sameFrozenInvoice(invoice,frozen);}
    static boolean hasAnyReceipt(Context context,JSONObject job)throws Exception {AtomicFile file=receiptFile(context,job);return exists(file.getBaseFile())||exists(new File(file.getBaseFile()+".bak"))||exists(new File(file.getBaseFile()+".new"));}
    static boolean hasOriginalEvidence(Context context,JSONObject job)throws Exception {return job.has("document")||hasAnyReceipt(context,job);}
    static JSONObject prepareVerifiedOriginal(Context context,JSONObject job,JSONObject supplied,String fullEvidenceHash)throws Exception {
        LOCK.lock();try{
        requireFrozen(job);if(fullEvidenceHash==null||!fullEvidenceHash.matches("[0-9a-f]{64}"))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");JSONObject metadata=metadata(job,supplied);verifiedOriginal(context,metadata);
        JSONObject previous=readReceipt(context,job);if(previous!=null){if(!sameDocument(previous,metadata))throw new IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH");return new JSONObject(previous.toString());}
        if(job.has("document")){JSONObject produced=job.optJSONObject("document");if(produced!=null)produced=metadata(job,produced);if(produced==null||!sameDocument(produced,metadata))throw new IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH");verifiedOriginal(context,produced);}
        writeReceipt(context,job,metadata,fullEvidenceHash);return metadata;
        }finally{LOCK.unlock();}
    }
    private static boolean exists(File file){return file.exists()||java.nio.file.Files.isSymbolicLink(file.toPath());}
    private static AtomicFile receiptFile(Context context,JSONObject job)throws Exception {String sha=job.getString("snapshot_sha256");if(!sha.matches("[0-9a-f]{64}"))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");File directory=new File(context.getFilesDir(),"invoice-document-receipts"),file=new File(directory,sha+".json");if(java.nio.file.Files.isSymbolicLink(directory.toPath())||java.nio.file.Files.isSymbolicLink(file.toPath())||java.nio.file.Files.isSymbolicLink(new File(file+".bak").toPath())||java.nio.file.Files.isSymbolicLink(new File(file+".new").toPath()))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");return new AtomicFile(file);}
    private static void requireFrozen(JSONObject job)throws Exception {JSONObject frozen=job.getJSONObject("snapshot");if(!"1".equals(String.valueOf(job.opt("formatVersion")))||!PixelMcpWriteTools.hash(frozen).equals(job.optString("snapshot_sha256"))||!frozen.optString("id").equals(job.optString("invoice_id"))||frozen.optString("invoice_number").isEmpty())throw new IOException("INVOICE_FROZEN_SNAPSHOT_CHANGED");}
    private static JSONObject metadata(JSONObject job,JSONObject supplied)throws Exception {for(Iterator<String> fields=supplied.keys();fields.hasNext();)if(!Arrays.asList("document_id","pdf_sha256","pdf_storage_name","pdf_byte_size").contains(fields.next()))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");String id=job.getString("invoice_id"),sha=supplied.getString("pdf_sha256");long size;try{size=new java.math.BigDecimal(supplied.get("pdf_byte_size").toString()).longValueExact();}catch(Exception invalid){throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");}if(!sha.matches("[0-9a-f]{64}")||!supplied.optString("document_id").equals("invoice:"+id)||!supplied.optString("pdf_storage_name").equals(sha+".pdf")||size<5)throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");return new JSONObject().put("document_id","invoice:"+id).put("pdf_sha256",sha).put("pdf_storage_name",sha+".pdf").put("pdf_byte_size",size);}
    private static void requireCreationAllowed(Context context,JSONObject job)throws Exception {
        LocalInvoiceCreationRules.requireAllowed(job,new BackupDeviceGuard(context).current(),new LocalAuthorityGuard(context).current(),LocalAuthorityGuard.restorePending(context));
    }
    private static JSONObject document(Context context,JSONObject job)throws Exception {
        requireFrozen(job);JSONObject prior=readReceipt(context,job);if(prior!=null){if(job.has("document")&&(job.optJSONObject("document")==null||!sameDocument(prior,job.getJSONObject("document"))))throw new IOException("INVOICE_DOCUMENT_SOURCE_MISMATCH");return prior;}
        // Existing produced metadata is authoritative too; never silently replace an original.
        JSONObject supplied=job.optJSONObject("document");if(job.has("document")&&supplied==null)throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");if(supplied==null){requireCreationAllowed(context,job);supplied=new BusinessDocumentStore(context).createInvoice(job.getJSONObject("snapshot"));}JSONObject metadata=metadata(job,supplied);verifiedOriginal(context,metadata);writeReceipt(context,job,metadata,null);return metadata;
    }
    private static void writeReceipt(Context context,JSONObject job,JSONObject metadata,String fullEvidenceHash)throws Exception {
        AtomicFile file=receiptFile(context,job);if(hasAnyReceipt(context,job))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");JSONObject receipt=new JSONObject().put("formatVersion",1).put("invoice_id",job.getString("invoice_id")).put("invoice_number",job.getJSONObject("snapshot").getString("invoice_number")).put("snapshot_sha256",job.getString("snapshot_sha256")).put("document",metadata).put("verified_at",java.time.Instant.now().toString());if(fullEvidenceHash!=null)receipt.put("recovered_from_full_evidence_sha256",fullEvidenceHash);
        File directory=file.getBaseFile().getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("INVOICE_DOCUMENT_RECEIPT_STORAGE_UNAVAILABLE");FileOutputStream out=null;try{out=file.startWrite();out.write(receipt.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);out=null;}catch(Exception failure){if(out!=null)file.failWrite(out);throw new IOException("INVOICE_DOCUMENT_RECEIPT_STORAGE_UNAVAILABLE");}
    }
    static String verifiedReceiptEvidenceHash(Context context,JSONObject job)throws Exception {
        LOCK.lock();try{JSONObject receipt=readReceiptRecord(context,job);if(receipt==null)throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");return receipt.optString("recovered_from_full_evidence_sha256");}finally{LOCK.unlock();}
    }
    private static JSONObject readReceipt(Context context,JSONObject job)throws Exception {JSONObject receipt=readReceiptRecord(context,job);return receipt==null?null:receipt.getJSONObject("document");}
    private static JSONObject readReceiptRecord(Context context,JSONObject job)throws Exception {
        String sha=job.getString("snapshot_sha256");AtomicFile file=receiptFile(context,job);
        if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists()){
            JSONObject receipt;try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>65536)throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");out.write(buffer,0,n);}receipt=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception invalid){throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");}
            if(!"1".equals(String.valueOf(receipt.opt("formatVersion")))||!sha.equals(receipt.optString("snapshot_sha256"))||!job.getString("invoice_id").equals(receipt.optString("invoice_id"))||!job.getJSONObject("snapshot").getString("invoice_number").equals(receipt.optString("invoice_number")))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");
            JSONObject metadata;try{metadata=metadata(job,receipt.getJSONObject("document"));}catch(Exception invalid){throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");}verifiedOriginal(context,metadata);if(receipt.has("recovered_from_full_evidence_sha256")&&!receipt.optString("recovered_from_full_evidence_sha256").matches("[0-9a-f]{64}"))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");receipt.put("document",metadata);return receipt;
        }
        if(exists(new File(file.getBaseFile()+".new")))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");
        return null;
    }
    private LocalInvoiceJobs(){}
}
