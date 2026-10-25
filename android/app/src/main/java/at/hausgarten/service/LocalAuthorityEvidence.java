package at.hausgarten.service;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Read-only external evidence and explicitly requested SHA-bound original import. Never guesses invoice data. */
final class LocalAuthorityEvidence {
    static final int MAX_JSON_BYTES=8*1024*1024,MAX_PDF_BYTES=20*1024*1024;
    static final class Loaded {
        final String sha256,reference,sourceCheckedAt;private final JSONObject source,preparedSummary;
        Loaded(String hash,JSONObject source)throws Exception {this.sha256=hash;reference="sha256:"+hash;this.source=source;sourceCheckedAt=source.optString("checked_at");preparedSummary=buildSummary();}
        JSONObject writer(String checkedAt)throws Exception {Instant.parse(checkedAt);return new JSONObject().put("formatVersion",1).put("external_writers_disabled",true).put("evidence_sha256",sha256).put("evidence_reference",reference).put("checked_at",checkedAt);}
        JSONObject ledger(String checkedAt)throws Exception {
            JSONObject result=normalizeLedger(source);JSONObject evidence=writer(checkedAt);for(Iterator<String> keys=evidence.keys();keys.hasNext();){String key=keys.next();result.put(key,evidence.get(key));}return result;
        }
        JSONObject reviewedLedger(String reviewedAt,JSONObject authority,boolean explicitlyConfirmed)throws Exception {return reviewedLedger(reviewedAt,authority,explicitlyConfirmed,false);}
        JSONObject reviewedLedger(String reviewedAt,JSONObject authority,boolean explicitlyConfirmed,boolean notCreatedExplicitlyConfirmed)throws Exception {
            if(!explicitlyConfirmed||authority==null||authority.optString("installation_id").isEmpty()||authority.optString("restore_epoch").isEmpty())throw new IOException("INVOICE_CURRENT_REVIEW_REQUIRED");
            Instant review=Instant.parse(reviewedAt);if(review.isAfter(Instant.now().plusSeconds(2)))throw new IOException("INVOICE_CURRENT_REVIEW_REQUIRED");for(String key:new String[]{"created_at","blocked_at"})if(!authority.optString(key).isEmpty()&&review.isBefore(Instant.parse(authority.getString(key))))throw new IOException("INVOICE_CURRENT_REVIEW_REQUIRED");
            if(summary().optInt("pending_not_created_count")>0&&!notCreatedExplicitlyConfirmed)throw new IOException("INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED");
            return ledger(reviewedAt).put("current_review",new JSONObject().put("confirmed",true).put("installation_id",authority.getString("installation_id")).put("restore_epoch",authority.getString("restore_epoch")).put("reviewed_at",reviewedAt).put("evidence_sha256",sha256));
        }
        JSONObject summary()throws Exception {return new JSONObject(preparedSummary.toString());}
        private JSONObject buildSummary()throws Exception {
            JSONObject result=new JSONObject().put("sha256",sha256).put("source_checked_at",sourceCheckedAt);JSONArray entries=source.optJSONArray("issued");int original=0,notCreated=0,unresolved=0,unbound=0;
            for(int i=0;entries!=null&&i<entries.length();i++)try{JSONObject row=normalizeIssued(entries.getJSONObject(i));if(row.getString("pdf_resolution").equals("not_created"))notCreated++;else{original++;if(!row.has("snapshot_sha256"))unbound++;}}catch(Exception invalid){unresolved++;}
            result.put("issued_count",entries==null?0:entries.length()).put("original_reference_count",original).put("pending_not_created_count",notCreated).put("unresolved_pdf_count",unresolved).put("snapshot_binding_missing_count",unbound);
            try{JSONObject l=normalizeLedger(source);result.put("ledger_valid",true).put("last_number",l.getInt("last_number")).put("reservation_count",l.getJSONArray("reservations").length()).put("opening_balance_count",l.optJSONArray("opening_balances")==null?0:l.getJSONArray("opening_balances").length());}catch(Exception invalid){result.put("ledger_valid",false);}return result;
        }
    }
    static Loaded read(Context context,Uri uri)throws Exception {
        requireContent(uri);try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("LOCAL_EVIDENCE_READ_FAILED");return parse(input);}
    }
    static Loaded parse(InputStream input)throws Exception {
        byte[] raw=bounded(input,MAX_JSON_BYTES);String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        JSONTokener parser=new JSONTokener(text);Object value=parser.nextValue();if(!(value instanceof JSONObject)||parser.nextClean()!=0)throw new IOException("LOCAL_EVIDENCE_JSON_INVALID");JSONObject source=(JSONObject)value;if(integer(source,"formatVersion",1,1)!=1)throw new IOException("LOCAL_EVIDENCE_VERSION_UNSUPPORTED");
        if(source.has("checked_at"))try{Instant.parse(source.getString("checked_at"));}catch(Exception invalid){throw new IOException("LOCAL_EVIDENCE_JSON_INVALID");}return new Loaded(PhotoAttachmentStore.sha256(raw),source);
    }
    private static final Object DRAFT_LOCK=new Object();
    /** The intent survives leaving the screen; old restore epochs cannot revive it. No automatic replay. */
    static void savePending(Context context,String name,String body,String uri,String hash,String backup)throws Exception {synchronized(DRAFT_LOCK){
        if(!Arrays.asList("activate_local_authority","reconcile_local_invoice_authority").contains(name))throw new IOException("LOCAL_AUTHORITY_DRAFT_INVALID");new JSONObject(body);JSONObject record=new JSONObject().put("formatVersion",1).put("context",new BackupDeviceGuard(context).current()).put("pending_name",name).put("pending_body",body).put("evidence_uri",uri).put("evidence_hash",hash).put("backup_id",backup);AtomicFile target=draft(context);byte[] bytes=record.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>16*1024*1024)throw new IOException("LOCAL_AUTHORITY_DRAFT_TOO_LARGE");FileOutputStream output=null;try{output=target.startWrite();output.write(bytes);target.finishWrite(output);output=null;}catch(Exception failed){if(output!=null)target.failWrite(output);throw failed;}
    }}
    static JSONObject loadPending(Context context)throws Exception {synchronized(DRAFT_LOCK){AtomicFile target=draft(context);if(!target.getBaseFile().exists()&&!new File(target.getBaseFile()+".bak").exists())return new JSONObject();JSONObject record;try(InputStream input=target.openRead()){record=new JSONObject(new String(bounded(input,16*1024*1024),StandardCharsets.UTF_8));}if(record.optInt("formatVersion")!=1)throw new IOException("LOCAL_AUTHORITY_DRAFT_INVALID");JSONObject current=new BackupDeviceGuard(context).current(),saved=record.getJSONObject("context");if(!current.getString("installationId").equals(saved.optString("installationId"))||!current.getString("restoreEpoch").equals(saved.optString("restoreEpoch")))return new JSONObject().put("stale_context",true);if(!Arrays.asList("activate_local_authority","reconcile_local_invoice_authority").contains(record.optString("pending_name")))throw new IOException("LOCAL_AUTHORITY_DRAFT_INVALID");new JSONObject(record.getString("pending_body"));return record;}}
    static void clearPending(Context context,String expectedBody)throws Exception {synchronized(DRAFT_LOCK){JSONObject record=loadPending(context);if(expectedBody.equals(record.optString("pending_body")))draft(context).delete();}}
    private static AtomicFile draft(Context context)throws Exception {File folder=new File(context.getNoBackupFilesDir(),"local-authority-review");if(java.nio.file.Files.isSymbolicLink(folder.toPath()))throw new IOException("LOCAL_AUTHORITY_DRAFT_INVALID");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("LOCAL_AUTHORITY_DRAFT_WRITE_FAILED");File file=new File(folder,"request.v1");if(java.nio.file.Files.isSymbolicLink(file.toPath()))throw new IOException("LOCAL_AUTHORITY_DRAFT_INVALID");return new AtomicFile(file);}
    static JSONObject normalizeLedger(JSONObject source)throws Exception {
        integer(source,"formatVersion",1,1);long last=integer(source,"last_number",0,1000000000);JSONArray input=source.getJSONArray("issued"),reservations=source.getJSONArray("reservations");if(input.length()>100000||reservations.length()>10000)throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");
        JSONArray issued=new JSONArray(),reserved=new JSONArray();Set<String> ids=new HashSet<>(),numbers=new HashSet<>(),reservationIds=new HashSet<>();
        for(int i=0;i<input.length();i++){JSONObject row=normalizeIssued(input.getJSONObject(i));if(!ids.add(row.getString("invoice_id"))||!numbers.add(row.getString("invoice_number")))throw new IOException("INVOICE_EVIDENCE_DUPLICATE");issued.put(row);}
        for(int i=0;i<reservations.length();i++){JSONObject row=reservations.getJSONObject(i);String id=id(row,"reservation_id"),state=row.getString("state");if(!reservationIds.add(id)||!Arrays.asList("reserved","issued","cancelled").contains(state))throw new IOException("INVOICE_RESERVATION_REVIEW_REQUIRED");reserved.put(new JSONObject().put("reservation_id",id).put("invoice_id",id(row,"invoice_id")).put("invoice_number",nonempty(row,"invoice_number",500)).put("counter",integer(row,"counter",0,last)).put("state",state));}
        JSONObject result=new JSONObject().put("formatVersion",1).put("last_number",last).put("issued",issued).put("reservations",reserved);JSONArray balances=source.optJSONArray("opening_balances");if(source.has("opening_balances")&&balances==null)throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");
        if(balances!=null){if(balances.length()>100000)throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");JSONArray output=new JSONArray();Set<String> unique=new HashSet<>();for(int i=0;i<balances.length();i++){JSONObject row=balances.getJSONObject(i);String id=id(row,"invoice_id");if(!ids.contains(id)||!unique.add(id))throw new IOException("INVOICE_OPENING_BALANCE_INVALID");output.put(new JSONObject().put("invoice_id",id).put("remaining_minor",integer(row,"remaining_minor",0,Long.MAX_VALUE)));}result.put("opening_balances",output);}return result;
    }
    private static JSONObject normalizeIssued(JSONObject row)throws Exception {
        String resolution=row.has("pdf_resolution")?nonempty(row,"pdf_resolution",20):"original";if(!Arrays.asList("original","not_created").contains(resolution))throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");
        JSONObject copy=new JSONObject().put("invoice_id",id(row,"invoice_id")).put("invoice_number",nonempty(row,"invoice_number",500)).put("pdf_resolution",resolution);if(row.has("snapshot_sha256"))copy.put("snapshot_sha256",hash(row,"snapshot_sha256"));
        if(resolution.equals("not_created")){
            if(!Boolean.TRUE.equals(row.opt("not_created_confirmed"))||!copy.has("snapshot_sha256"))throw new IOException("INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED");
            // A contradictory source is evidence to review, never fields to silently project away.
            for(String field:new String[]{"pdf_sha256","pdf_byte_size","pdf_storage_name","pdf_base64","document_id","document"})if(row.has(field))throw new IOException("INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED");
            copy.put("not_created_confirmed",true).put("not_created_reason",nonempty(row,"not_created_reason",4000));
        }else{
            if(row.has("not_created_confirmed")||row.has("not_created_reason"))throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");copy.put("pdf_sha256",hash(row,"pdf_sha256"));if(row.has("pdf_byte_size"))copy.put("pdf_byte_size",integer(row,"pdf_byte_size",5,MAX_PDF_BYTES));
        }
        return copy;
    }
    static JSONObject importOriginals(Context context,Uri tree,JSONObject ledger,String requestId)throws Exception {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("LOCAL_AUTHORITY_REQUEST_ID_REQUIRED");
        String receipt="local_authority_original_import:"+PixelMcpWriteTools.hash(requestId),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("tree",tree==null?"":tree.toString()).put("ledger",normalizeLedger(ledger)).put("context",new BackupDeviceGuard(context).current()));
        try(PixelLocalStore store=new PixelLocalStore(context)){
            String raw=store.ownerValue(receipt,"");if(!raw.isEmpty()){JSONObject prior=new JSONObject(raw);if(!fingerprint.equals(prior.getString("fingerprint")))throw new IOException("LOCAL_AUTHORITY_IMPORT_REPLAY_MISMATCH");return new JSONObject(prior.getJSONObject("result").toString()).put("replayed",true);}
            JSONObject result=importOriginalFiles(context,tree,ledger).put("request_id",requestId).put("replayed",false);android.database.sqlite.SQLiteDatabase database=store.getWritableDatabase();database.beginTransaction();try{raw=store.ownerValue(receipt,"");if(!raw.isEmpty()){JSONObject prior=new JSONObject(raw);if(!fingerprint.equals(prior.getString("fingerprint")))throw new IOException("LOCAL_AUTHORITY_IMPORT_REPLAY_MISMATCH");result=prior.getJSONObject("result").put("replayed",true);}else store.putOwnerValue(receipt,new JSONObject().put("fingerprint",fingerprint).put("result",result).toString());database.setTransactionSuccessful();return result;}finally{database.endTransaction();}
        }
    }
    private static JSONObject importOriginalFiles(Context context,Uri tree,JSONObject ledger)throws Exception {
        requireContent(tree);if(!DocumentsContract.isTreeUri(tree))throw new IOException("DOCUMENT_TREE_REQUIRED");JSONObject normalized=normalizeLedger(ledger);Map<String,Long> wanted=new HashMap<>();JSONArray issued=normalized.getJSONArray("issued");for(int i=0;i<issued.length();i++){JSONObject row=issued.getJSONObject(i);if(row.getString("pdf_resolution").equals("not_created"))continue;String sha=row.getString("pdf_sha256");long size=row.optLong("pdf_byte_size",-1);Long prior=wanted.get(sha);if(prior!=null&&size!=-1&&prior!=-1&&prior!=size)throw new IOException("INVOICE_EVIDENCE_MISMATCH");wanted.put(sha,prior!=null&&size==-1?prior:size);}
        Set<String> found=new HashSet<>();ArrayDeque<Directory> pending=new ArrayDeque<>();pending.add(new Directory(DocumentsContract.getTreeDocumentId(tree),0));Set<String> seen=new HashSet<>();int scanned=0,stored=0,oversized=0;
        while(!pending.isEmpty()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("LOCAL_EVIDENCE_IMPORT_INTERRUPTED");Directory directory=pending.removeFirst();if(!seen.add(directory.id))continue;if(directory.depth>32||seen.size()>5000)throw new IOException("LOCAL_EVIDENCE_TREE_LIMIT");
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,directory.id);try(Cursor rows=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
                if(rows==null)throw new IOException("LOCAL_EVIDENCE_READ_FAILED");while(rows.moveToNext()){
                    if(++scanned>50000)throw new IOException("LOCAL_EVIDENCE_TREE_LIMIT");String documentId=rows.getString(0),mime=rows.getString(1);if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){if(pending.size()>=5000)throw new IOException("LOCAL_EVIDENCE_TREE_LIMIT");pending.add(new Directory(documentId,directory.depth+1));continue;}if(!"application/pdf".equals(mime))continue;if(!rows.isNull(2)&&rows.getLong(2)>MAX_PDF_BYTES){oversized++;continue;}
                    byte[] raw;try(InputStream input=context.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,documentId))){if(input==null)throw new IOException("LOCAL_EVIDENCE_READ_FAILED");try{raw=bounded(input,MAX_PDF_BYTES);}catch(IOException tooLarge){if("LOCAL_EVIDENCE_FILE_TOO_LARGE".equals(tooLarge.getMessage())){oversized++;continue;}throw tooLarge;}}
                    try{String sha=PhotoAttachmentStore.sha256(raw);Long expected=wanted.get(sha);if(expected==null)continue;if(expected!=-1&&expected!=raw.length)throw new IOException("INVOICE_EVIDENCE_MISMATCH");JSONObject meta=new JSONObject().put("pdf_storage_name",sha+".pdf").put("pdf_sha256",sha).put("pdf_byte_size",raw.length);new BusinessDocumentStore(context).store(raw,meta);new BusinessDocumentStore(context).read(meta);if(found.add(sha))stored++;}finally{Arrays.fill(raw,(byte)0);}
                }
            }
        }
        int notCreated=0;for(int i=0;i<issued.length();i++)if(issued.getJSONObject(i).getString("pdf_resolution").equals("not_created"))notCreated++;return new JSONObject().put("pending_not_created_count",notCreated).put("matched_unique_originals",stored).put("required_unique_originals",wanted.size()).put("not_found_in_selected_tree",Math.max(0,wanted.size()-found.size())).put("oversized_pdf_files",oversized).put("source_folder_unchanged",true).put("invoice_records_created",0);
    }
    private static final class Directory {final String id;final int depth;Directory(String id,int depth){this.id=id;this.depth=depth;}}
    private static byte[] bounded(InputStream input,int maximum)throws Exception {ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[32768];int count;while((count=input.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("LOCAL_EVIDENCE_IMPORT_INTERRUPTED");if(count>maximum-bytes.size())throw new IOException("LOCAL_EVIDENCE_FILE_TOO_LARGE");bytes.write(block,0,count);}return bytes.toByteArray();}
    private static void requireContent(Uri uri)throws IOException {if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("DOCUMENT_URI_REQUIRED");}
    private static long integer(JSONObject row,String key,long min,long max)throws Exception {Object value=row.get(key);if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()<min||((Number)value).longValue()>max)throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");return ((Number)value).longValue();}
    private static String id(JSONObject row,String key)throws Exception{return nonempty(row,key,200);}
    private static String nonempty(JSONObject row,String key,int max)throws Exception {Object raw=row.get(key);if(!(raw instanceof String))throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");String value=(String)raw;if(value.trim().isEmpty()||value.length()>max||value.indexOf('\0')>=0)throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");return value;}
    private static String hash(JSONObject row,String key)throws Exception {String value=nonempty(row,key,64);if(!value.matches("[0-9a-f]{64}"))throw new IOException("LOCAL_EVIDENCE_LEDGER_INVALID");return value;}
    private LocalAuthorityEvidence(){}
}
