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
                }catch(Exception error){if(Thread.currentThread().isInterrupted()||error instanceof InterruptedIOException)throw error;boolean originalChanged=error instanceof IOException&&"INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED".equals(error.getMessage());job.put("phase",originalChanged?"ORIGINAL_REVIEW_REQUIRED":"PDF_PENDING").put("last_error_code",originalChanged?"INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED":error instanceof IOException&&"INVOICE_DOCUMENT_RECEIPT_INVALID".equals(error.getMessage())?"INVOICE_DOCUMENT_RECEIPT_INVALID":"INVOICE_PDF_CREATION_OR_STORAGE_FAILED");store.putOwnerValue(key,job.toString());failed++;}
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
    private static JSONObject document(Context context,JSONObject job)throws Exception {
        String sha=job.getString("snapshot_sha256");if(!sha.matches("[0-9a-f]{64}"))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"invoice-document-receipts/"+sha+".json"));
        JSONObject prior=readReceipt(context,job);if(prior!=null)return prior;
        // Existing produced metadata is authoritative too; never silently replace an original.
        JSONObject metadata=job.optJSONObject("document");if(metadata==null)metadata=new BusinessDocumentStore(context).createInvoice(job.getJSONObject("snapshot"));if(!metadata.optString("document_id").equals("invoice:"+job.getString("invoice_id")))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");verifiedOriginal(context,metadata);
        JSONObject receipt=new JSONObject().put("formatVersion",1).put("invoice_id",job.getString("invoice_id")).put("invoice_number",job.getJSONObject("snapshot").getString("invoice_number")).put("snapshot_sha256",sha).put("document",metadata).put("verified_at",java.time.Instant.now().toString());
        File directory=file.getBaseFile().getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("INVOICE_DOCUMENT_RECEIPT_STORAGE_UNAVAILABLE");FileOutputStream out=null;try{out=file.startWrite();out.write(receipt.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);out=null;}catch(Exception failure){if(out!=null)file.failWrite(out);throw new IOException("INVOICE_DOCUMENT_RECEIPT_STORAGE_UNAVAILABLE");}
        return metadata;
    }
    private static JSONObject readReceipt(Context context,JSONObject job)throws Exception {
        String sha=job.getString("snapshot_sha256");if(!sha.matches("[0-9a-f]{64}"))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"invoice-document-receipts/"+sha+".json"));
        if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists()){
            JSONObject receipt;try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>65536)throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");out.write(buffer,0,n);}receipt=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception invalid){throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");}
            if(receipt.optInt("formatVersion")!=1||!sha.equals(receipt.optString("snapshot_sha256"))||!job.getString("invoice_id").equals(receipt.optString("invoice_id"))||!job.getJSONObject("snapshot").getString("invoice_number").equals(receipt.optString("invoice_number")))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");
            JSONObject metadata=receipt.getJSONObject("document");if(!metadata.optString("document_id").equals("invoice:"+job.getString("invoice_id")))throw new IOException("INVOICE_DOCUMENT_RECEIPT_INVALID");verifiedOriginal(context,metadata);return metadata;
        }
        return null;
    }
    private LocalInvoiceJobs(){}
}
