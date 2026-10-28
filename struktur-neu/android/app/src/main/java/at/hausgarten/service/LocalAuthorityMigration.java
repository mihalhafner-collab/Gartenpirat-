package at.hausgarten.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;

/** Explicit, restart-safe local authority cutover. No remote data is applied or queue deleted. */
final class LocalAuthorityMigration {
    private final Context context;private final PixelLocalStore store;
    LocalAuthorityMigration(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    JSONObject preview()throws Exception {
        JSONObject state=withExtended(store.getState()),inventory=LocalMigrationInventory.inspect(state,new LocalOriginalVerifier(context.getFilesDir(),store::photoAttachment,id->new JSONObject(store.ownerValue("local_invoice_job:"+id,"{}"))));
        JSONObject result=new JSONObject().put("formatVersion",1).put("authority",new LocalAuthorityGuard(context).current()).put("inventory",inventory).put("state_sha256",businessHash(state)).put("drive_writer_running",DriveSyncCoordinator.running()).put("invoice_issue_allowed",false).put("requires_current_external_backup",true).put("requires_external_writer_evidence",true);
        return result.put("preview_hash",PixelMcpWriteTools.hash(result));
    }
    JSONObject activateFromArgs(JSONObject args)throws Exception {
        LocalBackupCoordinator backups=new LocalBackupCoordinator(context,store);String backupId=args.getString("backup_id");JSONObject job=backups.status(backupId),manifest=backups.metadata(backupId);backups.verifiedArchive(backupId);
        JSONObject proof=LocalAuthorityRules.externalBackupProof(job,manifest,java.time.Instant.now());
        return activate(args.getString("request_id"),args.getString("expected_preview_hash"),proof,args.getJSONObject("writer_evidence"));
    }
    JSONObject reconcile(JSONObject args)throws Exception {
        String request=args.getString("request_id"),fingerprint=PixelMcpWriteTools.hash(args);JSONObject evidence=args.getJSONObject("ledger");LocalAuthorityRules.requireEvidence(evidence);
        synchronized(DriveSyncCoordinator.LOCK){
            if(!LocalAuthorityGuard.local(context)||DriveSyncCoordinator.running())throw new IOException("LOCAL_AUTHORITY_REQUIRED");
            String contract=store.ownerValue("local_authority_contract","");if(contract.isEmpty()||!new JSONObject(contract).getString("migration_epoch").equals(new LocalAuthorityGuard(context).current().optString("migration_epoch")))throw new IOException("LOCAL_MIGRATION_REQUIRED_AFTER_RESTORE");
            String old=store.ownerValue("local_invoice_reconciliation:"+request,"");if(!old.isEmpty()){
                JSONObject saved=new JSONObject(old);if(!fingerprint.equals(saved.getString("fingerprint")))throw new IOException("INVOICE_RECONCILIATION_REPLAY_MISMATCH");requireCurrentReceipt(saved);
                int checkpoint=saved.getJSONObject("result").getInt("last_number");JSONObject authority=new LocalAuthorityGuard(context).current();
                if(!authority.optString("invoice_authority").equals("RECONCILED")){
                    if(new JSONObject(store.ownerValue("local_invoice_counter","{}")).getInt("last_number")!=checkpoint)throw new IOException("INVOICE_RECONCILIATION_REVIEW_REQUIRED");
                    new LocalAuthorityGuard(context).reconcileInvoices(request,evidence,checkpoint);
                }
                return scheduleAuthorizedDocuments(saved.getJSONObject("result").put("replayed",true));
            }
            requireCurrentEvidence(evidence,new LocalAuthorityGuard(context).current(),true);
            JSONObject acceptedPreview=preview();if(!acceptedPreview.getString("preview_hash").equals(args.getString("expected_preview_hash")))throw new IOException("LOCAL_MIGRATION_WRITE_CONFLICT");
            JSONObject capturedState=store.getState(),all=new JSONObject(capturedState.getJSONObject("master").toString());String stateCas=stateWriteHash(capturedState);DriveJournalRules.apply(all,capturedState.getJSONArray("pending"));JSONObject settings=all.getJSONObject("business_settings");int last=new java.math.BigDecimal(evidence.get("last_number").toString()).intValueExact();if(last<0||last>1000000000)throw new IOException("INVOICE_NUMBER_STATE_INVALID");
            JSONArray issued=evidence.getJSONArray("issued"),reservations=evidence.getJSONArray("reservations");Map<String,JSONObject> originals=new HashMap<>();Set<String> numbers=new HashSet<>();
            Map<String,JSONObject> recoveryJobs=new LinkedHashMap<>(),adoptions=new LinkedHashMap<>();Set<String> firstCreations=new HashSet<>();String evidenceHash=PixelMcpWriteTools.hash(evidence);
            for(int i=0;i<issued.length();i++){
                JSONObject row=issued.getJSONObject(i);String id=row.getString("invoice_id");if(originals.put(id,row)!=null||!numbers.add(row.getString("invoice_number")))throw new IOException("INVOICE_EVIDENCE_DUPLICATE");JSONObject known=OwnerJournalRules.findOrNull(all.getJSONArray("invoices"),id);boolean notCreated=row.optString("pdf_resolution").equals("not_created");
                if(known!=null&&!row.getString("invoice_number").equals(known.optString("invoice_number")))throw new IOException("INVOICE_EVIDENCE_MISMATCH");
                if(notCreated||known!=null&&known.optString("pdf_sha256").isEmpty()){
                    if(known==null)throw new IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED");JSONObject job=recoveryJob(known,row);recoveryJobs.put(id,job);
                    if(notCreated){requireNeverCreated(known,job,row);firstCreations.add(id);}
                    else{String sha=row.getString("pdf_sha256");long bytes=new java.math.BigDecimal(row.get("pdf_byte_size").toString()).longValueExact();if(!sha.matches("[0-9a-f]{64}")||bytes<5)throw new IOException("INVOICE_ORIGINAL_EVIDENCE_REQUIRED");adoptions.put(id,new JSONObject().put("document_id","invoice:"+id).put("pdf_sha256",sha).put("pdf_storage_name",sha+".pdf").put("pdf_byte_size",bytes));}
                }else{
                    String sha=row.getString("pdf_sha256");if(!sha.matches("[0-9a-f]{64}"))throw new IOException("INVOICE_ORIGINAL_EVIDENCE_REQUIRED");if(known!=null){
                        if(!sha.equals(known.optString("pdf_sha256")))throw new IOException("INVOICE_EVIDENCE_MISMATCH");new BusinessDocumentStore(context).read(known);String rawJob=store.ownerValue("local_invoice_job:"+id,"");
                        if(!rawJob.isEmpty()&&Arrays.asList("PDF_PENDING","ORIGINAL_REVIEW_REQUIRED").contains(new JSONObject(rawJob).optString("phase"))){JSONObject job=recoveryJob(known,row);if(!row.has("pdf_byte_size")||new java.math.BigDecimal(row.get("pdf_byte_size").toString()).longValueExact()!=known.getLong("pdf_byte_size"))throw new IOException("INVOICE_EVIDENCE_MISMATCH");recoveryJobs.put(id,job);adoptions.put(id,new JSONObject().put("document_id","invoice:"+id).put("pdf_sha256",sha).put("pdf_storage_name",sha+".pdf").put("pdf_byte_size",known.getLong("pdf_byte_size")));}
                    }else{java.io.File original=new java.io.File(context.getFilesDir(),"business-documents/"+sha+".pdf");if(!original.isFile()||original.length()!=row.getLong("pdf_byte_size")||!EncryptedBackupArchive.sha256(original).equals(sha))throw new IOException("INVOICE_EXTERNAL_ORIGINAL_MISSING");}
                }
            }
            for(int i=0;i<all.getJSONArray("invoices").length();i++){JSONObject invoice=all.getJSONArray("invoices").getJSONObject(i);if(IssuedInvoiceRules.isIssued(invoice)&&!originals.containsKey(invoice.getString("id")))throw new IOException("INVOICE_CURRENT_LEDGER_INCOMPLETE");}
            Map<String,JSONObject> reservationIds=new HashMap<>();Set<Integer> reservedCounters=new HashSet<>();for(int i=0;i<reservations.length();i++){JSONObject row=reservations.getJSONObject(i);int number=new java.math.BigDecimal(row.get("counter").toString()).intValueExact();if(number<=0||number>last||reservationIds.put(row.getString("reservation_id"),row)!=null||!reservedCounters.add(number)||!Arrays.asList("reserved","issued","cancelled").contains(row.getString("state")))throw new IOException("INVOICE_RESERVATION_REVIEW_REQUIRED");}
            try(android.database.Cursor keys=store.getReadableDatabase().rawQuery("SELECT value FROM owner_state WHERE key LIKE 'local_number_reservation:%'",null)){
                while(keys.moveToNext()){JSONObject known=new JSONObject(keys.getString(0)),proved=reservationIds.get(known.getString("request_id"));if(proved==null||proved.getInt("counter")!=known.getInt("counter")||!proved.optString("invoice_number").equals(known.getString("invoice_number"))||!proved.optString("invoice_id").equals(known.getString("invoice_id")))throw new IOException("INVOICE_RESERVATION_REVIEW_REQUIRED");}
            }
            for(String id:firstCreations){JSONObject job=recoveryJobs.get(id),frozen=job.getJSONObject("snapshot"),reservation=reservationIds.get(frozen.getString("local_issue_request"));if(reservation==null||!Arrays.asList("reserved","issued").contains(reservation.optString("state"))||!id.equals(reservation.optString("invoice_id"))||!frozen.getString("invoice_number").equals(reservation.optString("invoice_number"))||reservation.getInt("counter")!=frozen.getJSONObject("numbering_receipt").getInt("counter"))throw new IOException("INVOICE_RESERVATION_REVIEW_REQUIRED");}
            int observed=settings.optInt("invoice_numbering_last_number",settings.optInt("invoice_sevdesk_last_number",0));for(int i=0;i<all.getJSONArray("invoices").length();i++){JSONObject invoice=all.getJSONArray("invoices").getJSONObject(i);JSONObject receipt=invoice.optJSONObject("numbering_receipt");if(receipt!=null)observed=Math.max(observed,receipt.optInt("counter"));}
            String previousCounter=store.ownerValue("local_invoice_counter","");if(!previousCounter.isEmpty())observed=Math.max(observed,new JSONObject(previousCounter).getInt("last_number"));
            String format=settings.optString("invoice_number_format"),prefix=settings.optString("invoice_prefix");String regex=format.equals("sevdesk")?"RE-([0-9]+)(?:/[0-9]{4})?":java.util.regex.Pattern.quote(prefix)+"-[0-9]{4}-([0-9]+)";
            for(int i=0;i<issued.length();i++){java.util.regex.Matcher matcher=java.util.regex.Pattern.compile(regex).matcher(issued.getJSONObject(i).getString("invoice_number"));if(matcher.matches())observed=Math.max(observed,new java.math.BigDecimal(matcher.group(1)).intValueExact());}
            if(last<observed||last<new LocalAuthorityGuard(context).current().optInt("invoice_high_water"))throw new IOException("INVOICE_NUMBER_ROLLBACK_DETECTED");
            // A verified immutable receipt may outlive a failed SQL CAS; it never changes
            // the invoice body or grants number authority by itself.
            Map<String,String> receiptProofs=new HashMap<>();for(Map.Entry<String,JSONObject> adoption:adoptions.entrySet()){JSONObject job=recoveryJobs.get(adoption.getKey());adoption.setValue(LocalInvoiceJobs.prepareVerifiedOriginal(context,job,adoption.getValue(),evidenceHash));String receiptProof=LocalInvoiceJobs.verifiedReceiptEvidenceHash(context,job);if(!receiptProof.isEmpty())receiptProofs.put(adoption.getKey(),receiptProof);}
            SQLiteDatabase db=store.getWritableDatabase();store.beginBusinessTransaction(db);try{
                JSONObject currentState=store.getState();if(!acceptedPreview.getString("state_sha256").equals(businessHash(withExtended(currentState)))||!stateCas.equals(stateWriteHash(currentState))||!acceptedPreview.getJSONObject("authority").getString("revision").equals(new LocalAuthorityGuard(context).current().getString("revision"))||!contract.equals(store.ownerValue("local_authority_contract","")))throw new IOException("LOCAL_MIGRATION_WRITE_CONFLICT");requireCurrentEvidence(evidence,new LocalAuthorityGuard(context).current(),true);
                JSONObject identity=new BackupDeviceGuard(context).current();String reviewed=evidence.getJSONObject("current_review").getString("reviewed_at");boolean invoiceMetadataChanged=false;
                for(Map.Entry<String,JSONObject> entry:recoveryJobs.entrySet()){
                    String id=entry.getKey();JSONObject job=new JSONObject(entry.getValue().toString()),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),id);String before=PixelMcpWriteTools.hash(job),kind;
                    if(firstCreations.contains(id)){
                        requireNeverCreated(invoice,job,originals.get(id));job.put("creation_authorization",new JSONObject().put("formatVersion",1).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch")).put("snapshot_sha256",job.getString("snapshot_sha256")).put("ledger_proof_sha256",evidenceHash).put("request_id",request).put("authorized_at",reviewed)).put("phase","PDF_PENDING").put("attempts",0).remove("last_error_code");kind="first_creation_explicitly_authorized";
                    }else{
                        JSONObject metadata=adoptions.get(id),adoptionProof=new JSONObject().put("formatVersion",1).put("snapshot_sha256",job.getString("snapshot_sha256")).put("ledger_proof_sha256",evidenceHash).put("request_id",request).put("adopted_at",reviewed);if(receiptProofs.containsKey(id))adoptionProof.put("receipt_proof_sha256",receiptProofs.get(id));for(String field:new String[]{"document_id","pdf_sha256","pdf_storage_name","pdf_byte_size"})invoice.put(field,metadata.get(field));invoice.put("pdf_state","READY");OwnerJournalRules.replace(all,"invoices",id,invoice);job.put("document",metadata).put("phase","READY").put("verified_at",java.time.Instant.now().toString()).put("original_adoption",adoptionProof).remove("last_error_code");kind="verified_original_adopted";invoiceMetadataChanged=true;
                    }
                    JSONObject audit=new JSONObject().put("formatVersion",1).put("invoice_id",id).put("invoice_number",invoice.getString("invoice_number")).put("snapshot_sha256",job.getString("snapshot_sha256")).put("resolution",kind).put("reason",originals.get(id).optString("not_created_reason")).put("before_job_sha256",before).put("after_job_sha256",PixelMcpWriteTools.hash(job)).put("ledger_proof_sha256",evidenceHash).put("request_id",request).put("reviewed_at",reviewed).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch"));String auditKey="local_invoice_document_recovery:"+PixelMcpWriteTools.hash(new JSONObject().put("request_id",request).put("invoice_id",id)),previousAudit=store.ownerValue(auditKey,"");if(!previousAudit.isEmpty()&&!PixelMcpWriteTools.hash(new JSONObject(previousAudit)).equals(PixelMcpWriteTools.hash(audit)))throw new IOException("INVOICE_RECONCILIATION_REPLAY_MISMATCH");store.putOwnerValue("local_invoice_job:"+id,job.toString());if(previousAudit.isEmpty())store.putOwnerValue(auditKey,audit.toString());
                }
                if(invoiceMetadataChanged)store.commitLocalSnapshot(all);
                store.putOwnerValue("local_invoice_counter",new JSONObject().put("formatVersion",1).put("last_number",last).put("evidence_sha256",PixelMcpWriteTools.hash(evidence)).put("reconciled_at",java.time.Instant.now().toString()).toString());store.putOwnerValue("local_invoice_external_evidence",evidence.toString());JSONArray balances=evidence.optJSONArray("opening_balances");for(int i=0;balances!=null&&i<balances.length();i++){JSONObject b=balances.getJSONObject(i),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),b.getString("invoice_id"));long total=LocalAuthorityRules.minor(invoice.has("gross_total")?invoice.get("gross_total"):invoice.get("amount")),remaining=new java.math.BigDecimal(b.get("remaining_minor").toString()).longValueExact();if(remaining<0||remaining>Math.max(0,total))throw new IOException("INVOICE_OPENING_BALANCE_INVALID");String balanceKey="invoice_opening_balance:"+invoice.getString("id"),before=store.ownerValue(balanceKey,"");if(!before.isEmpty()&&new JSONObject(before).getLong("remaining_minor")!=remaining)throw new IOException("INVOICE_OPENING_BALANCE_CORRECTION_REQUIRED");if(before.isEmpty())store.putOwnerValue(balanceKey,new JSONObject(b.toString()).put("evidence_sha256",PixelMcpWriteTools.hash(evidence)).toString());}
                LocalFinancialSnapshotValidation.inspect(db,SnapshotProjection.effective(store.getState()));JSONObject result=new JSONObject().put("invoice_authority","RECONCILED").put("last_number",last).put("replayed",false).put("document_creation_jobs",firstCreations.size()).put("document_originals_adopted",adoptions.size());store.putOwnerValue("local_invoice_reconciliation:"+request,receipt(fingerprint,result).toString());db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            new LocalAuthorityGuard(context).reconcileInvoices(request,evidence,last);return scheduleAuthorizedDocuments(new JSONObject().put("invoice_authority","RECONCILED").put("last_number",last).put("replayed",false).put("document_creation_jobs",firstCreations.size()).put("document_originals_adopted",adoptions.size()));
        }
    }
    private JSONObject recoveryJob(JSONObject invoice,JSONObject proof)throws Exception {
        String id=invoice.getString("id"),raw=store.ownerValue("local_invoice_job:"+id,"");if(raw.isEmpty()||invoice.optString("local_issue_request").isEmpty())throw new IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED");JSONObject job=new JSONObject(raw),frozen=job.getJSONObject("snapshot");String hash=proof.optString("snapshot_sha256");
        if(!hash.matches("[0-9a-f]{64}")||!hash.equals(job.optString("snapshot_sha256"))||!hash.equals(PixelMcpWriteTools.hash(frozen))||!id.equals(job.optString("invoice_id"))||!id.equals(frozen.optString("id"))||!invoice.getString("invoice_number").equals(frozen.optString("invoice_number"))||!LocalFinancialValidation.sameFrozenInvoice(invoice,frozen)||!Arrays.asList("PDF_PENDING","ORIGINAL_REVIEW_REQUIRED").contains(job.optString("phase")))throw new IOException("INVOICE_EVIDENCE_MISMATCH");return job;
    }
    private static String stateWriteHash(JSONObject state)throws Exception {return PixelMcpWriteTools.hash(new JSONObject().put("master",state.getJSONObject("master")).put("pending",state.getJSONArray("pending")));}
    private void requireNeverCreated(JSONObject invoice,JSONObject job,JSONObject proof)throws Exception {
        JSONObject identity=new BackupDeviceGuard(context).current();boolean creationAlreadyAllowed=false;try{LocalInvoiceCreationRules.requireAllowed(job,identity,new LocalAuthorityGuard(context).current(),LocalAuthorityGuard.restorePending(context));creationAlreadyAllowed=true;}catch(IOException held){/* A held creation may be authorized only by the explicit evidence below. */}if(creationAlreadyAllowed)throw new IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED");
        if(!proof.optString("pdf_resolution").equals("not_created")||!Boolean.TRUE.equals(proof.opt("not_created_confirmed"))||proof.optString("not_created_reason").trim().isEmpty()||LocalInvoiceCreationRules.hasDocumentFields(proof)||!invoice.optString("pdf_sha256").isEmpty()||!invoice.optString("pdf_storage_name").isEmpty()||!invoice.optString("pdf_base64").isEmpty()||!invoice.optString("document_id").isEmpty()||invoice.has("pdf_byte_size")||invoice.has("document")||invoice.optString("pdf_state").equals("READY")||LocalInvoiceJobs.hasOriginalEvidence(context,job))throw new IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED");
    }
    private JSONObject scheduleAuthorizedDocuments(JSONObject result){if(result.optInt("document_creation_jobs")>0)try{LocalInvoiceJobs.schedule(context);}catch(Exception deferred){try{result.put("document_scheduling_pending",true);}catch(JSONException ignored){/* Fixed boolean only. */}}return result;}
    JSONObject activate(String request,String expectedPreview,JSONObject backupProof,JSONObject writerProof)throws Exception {
        if(request==null||!request.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("LOCAL_MIGRATION_REQUEST_ID_REQUIRED");
        LocalAuthorityRules.requireEvidence(writerProof);String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("expected_preview",expectedPreview).put("backup",backupProof).put("writers",writerProof));
        synchronized(DriveSyncCoordinator.LOCK){
            if(DriveSyncCoordinator.running())throw new IOException("LOCAL_MIGRATION_SYNC_STILL_RUNNING");
            String saved=store.ownerValue("local_migration_request:"+request,"");if(!saved.isEmpty()){
                JSONObject savedReceipt=new JSONObject(saved);if(!fingerprint.equals(savedReceipt.getString("fingerprint")))throw new IOException("LOCAL_MIGRATION_REPLAY_MISMATCH");requireCurrentReceipt(savedReceipt);
                JSONObject authority=new LocalAuthorityGuard(context).current(),contract=new JSONObject(store.ownerValue("local_authority_contract","{}"));
                if(authority.optString("review_reason").equals("MIGRATION_INTERRUPTED"))authority=new LocalAuthorityGuard(context).finishMigration(request,contract);
                return savedReceipt.getJSONObject("result").put("mode",authority.getString("mode")).put("invoice_authority",authority.getString("invoice_authority")).put("replayed",true);
            }
            JSONObject preview=preview();if(!preview.getString("preview_hash").equals(expectedPreview))throw new IOException("LOCAL_MIGRATION_WRITE_CONFLICT");JSONObject inventory=preview.getJSONObject("inventory");
            if(inventory.getInt("duplicate_ids")!=0||inventory.getInt("orphan_relations")!=0||inventory.getInt("missing_local_originals")!=0||inventory.optInt("historical_reviews")!=0)throw new IOException("LOCAL_MIGRATION_DATA_REVIEW_REQUIRED");
            JSONObject state=store.getState();for(int i=0;i<state.getJSONArray("pending").length();i++)if(Arrays.asList("invoice_issue","owner_invoice_issue","pixel_invoice_issue_v1").contains(state.getJSONArray("pending").getJSONObject(i).optString("kind")))throw new IOException("LOCAL_MIGRATION_OPEN_NUMBER_ACTIONS");
            requireBackup(backupProof,preview.getString("state_sha256"));requireCurrentEvidence(writerProof,preview.getJSONObject("authority"),false);LocalAuthorityGuard guard=new LocalAuthorityGuard(context);JSONObject staged=guard.beginMigration(request,preview.getJSONObject("authority").getString("revision"),writerProof);
            try(AutoCloseable permit=LocalAuthorityGuard.exclusiveStartup()){
                JSONObject contract,result;SQLiteDatabase db=store.getWritableDatabase();store.beginBusinessTransaction(db);try{
                    if(!businessHash(withExtended(store.getState())).equals(preview.getString("state_sha256")))throw new IOException("LOCAL_MIGRATION_WRITE_CONFLICT");
                    String original=store.snapshotText("{}");store.putOwnerValue("legacy_snapshot_before_local:"+request,original);JSONObject stateForLocal=store.getState(),canonical=new JSONObject(stateForLocal.getJSONObject("master").toString());DriveJournalRules.apply(canonical,stateForLocal.getJSONArray("pending"));store.commitLocalSnapshot(canonical);
                    contract=new JSONObject().put("formatVersion",1).put("engine",LocalAuthorityRules.ENGINE).put("migration_epoch",staged.getString("migration_epoch")).put("migration_request",request).put("activated_at",java.time.Instant.now().toString()).put("source_business_sha256",preview.getString("state_sha256")).put("backup_proof_sha256",PixelMcpWriteTools.hash(backupProof)).put("writer_proof_sha256",PixelMcpWriteTools.hash(writerProof)).put("old_journal_preserved",true);
                    store.putOwnerValue("local_authority_contract",contract.toString());result=new JSONObject().put("mode","LOCAL_ACTIVE").put("migration_epoch",staged.getString("migration_epoch")).put("invoice_authority","LOCKED").put("replayed",false).put("old_journal_preserved",true).put("drive_role",DriveRolePolicy.role());store.putOwnerValue("local_migration_request:"+request,receipt(fingerprint,result).toString());
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                guard.finishMigration(request,contract);return result;
            }
        }
    }
    private void requireCurrentEvidence(JSONObject proof,JSONObject authority,boolean ledger)throws Exception {
        java.time.Instant now=java.time.Instant.now(),checked=java.time.Instant.parse(proof.getString("checked_at"));
        String boundary=authority.optString("evidence_not_before",authority.optString("blocked_at",authority.optString("writer_checked_at","")));
        if(checked.isAfter(now)||!boundary.isEmpty()&&checked.isBefore(java.time.Instant.parse(boundary)))throw new IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED");
        if(ledger){
            JSONObject review=proof.optJSONObject("current_review"),identity=new BackupDeviceGuard(context).current();
            if(review==null||!review.optBoolean("confirmed")||!identity.getString("installationId").equals(review.optString("installation_id"))||!identity.getString("restoreEpoch").equals(review.optString("restore_epoch"))||!proof.getString("evidence_sha256").equals(review.optString("evidence_sha256")))throw new IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED");
            java.time.Instant reviewed=java.time.Instant.parse(review.getString("reviewed_at"));if(reviewed.isAfter(now)||reviewed.isBefore(checked))throw new IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED");
            for(String field:new String[]{"created_at","blocked_at","evidence_not_before"})if(!authority.optString(field).isEmpty()&&reviewed.isBefore(java.time.Instant.parse(authority.getString(field))))throw new IOException("LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED");
        }
    }
    private JSONObject receipt(String fingerprint,JSONObject result)throws Exception {JSONObject identity=new BackupDeviceGuard(context).current();return new JSONObject().put("fingerprint",fingerprint).put("result",result).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch"));}
    private void requireCurrentReceipt(JSONObject saved)throws Exception {JSONObject identity=new BackupDeviceGuard(context).current();if(!identity.getString("installationId").equals(saved.optString("installation_id"))||!identity.getString("restoreEpoch").equals(saved.optString("restore_epoch")))throw new IOException("LOCAL_AUTHORITY_RECEIPT_STALE_AFTER_RESTORE");}
    static String businessHash(JSONObject state)throws Exception {JSONObject effective=SnapshotProjection.effective(state);JSONObject selected=new JSONObject();for(String name:new String[]{"customers","orders","worklogs","photos","offers","invoices","appointments","service_catalog","business_settings","owner_archive"})if(effective.has(name))selected.put(name,effective.get(name));if(state.has("extended_business_fingerprint"))selected.put("extended_business_fingerprint",state.get("extended_business_fingerprint"));return PixelMcpWriteTools.hash(selected);}
    private JSONObject withExtended(JSONObject state)throws Exception {return state.put("extended_business_fingerprint",BusinessStateFingerprint.read(store.getReadableDatabase()));}
    private static void requireBackup(JSONObject proof,String current)throws Exception {
        if(!"1".equals(String.valueOf(proof.opt("formatVersion")))||!proof.optBoolean("verified")||!proof.optBoolean("external_verified")||!proof.optString("archive_sha256").matches("[0-9a-f]{64}")||!current.equals(proof.optString("business_snapshot_sha256")))throw new IOException("LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED");java.time.Instant.parse(proof.getString("captured_at"));java.time.Instant.parse(proof.getString("verified_at"));
    }
}
