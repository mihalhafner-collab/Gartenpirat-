package at.hausgarten.service;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

/** Read-only transition evidence. An inventory never grants local write/number authority. */
final class LocalMigrationInventory {
    interface Originals { boolean verified(String kind,JSONObject reference)throws Exception;default boolean pendingDocument(JSONObject reference)throws Exception{return false;} }
    static final String FORMAT="hausgarten.local-migration-inventory.v1";
    private static final String[] COLLECTIONS={"customers","orders","worklogs","photos","offers","invoices","appointments","service_catalog"};

    static JSONObject inspect(JSONObject state,Originals originals)throws Exception{
        String originalHash=PixelMcpWriteTools.hash(state);
        JSONObject master=state.getJSONObject("master"),effective;boolean projectionApplied=true;
        try{effective=SnapshotProjection.effective(state);}catch(IllegalArgumentException invalid){effective=new JSONObject(master.toString());projectionApplied=false;}
        JSONObject counts=new JSONObject(),ids=new JSONObject(),hashes=new JSONObject();
        JSONArray blockers=new JSONArray();
        int duplicateIds=0,orphanRelations=0,missingOriginals=0,verifiedOriginals=0,pendingDocuments=0;
        Map<String,Set<String>> known=new HashMap<>();
        for(String key:COLLECTIONS){
            JSONArray rows=effective.optJSONArray(key);if(rows==null)rows=new JSONArray();
            Set<String> seen=new TreeSet<>();
            for(int i=0;i<rows.length();i++){
                String id=rows.getJSONObject(i).optString("id");
                if(id.isEmpty()||!seen.add(id))duplicateIds++;
            }
            known.put(key,seen);counts.put(key,rows.length());
            ids.put(key,PixelMcpWriteTools.hash(new JSONArray(seen)));
            hashes.put(key,PixelMcpWriteTools.hash(rows));
        }
        BigDecimal hours=BigDecimal.ZERO,invoiceAmounts=BigDecimal.ZERO;
        for(String key:COLLECTIONS){
            JSONArray rows=effective.optJSONArray(key);if(rows==null)continue;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                for(String[] relation:new String[][]{{"customer_id","customers"},{"billing_customer_id","customers"},{"order_id","orders"},{"appointment_id","appointments"},{"worklog_id","worklogs"}}){
                    String target=row.optString(relation[0]);if(!target.isEmpty()&&!known.get(relation[1]).contains(target))orphanRelations++;
                }
                if(key.equals("worklogs"))hours=hours.add(decimal(row,"hours"));
                if(key.equals("invoices"))invoiceAmounts=invoiceAmounts.add(decimal(row,row.has("gross_total")?"gross_total":"amount"));
                if(key.equals("invoices")){
                    JSONArray links=row.optJSONArray("worklog_ids");for(int j=0;links!=null&&j<links.length();j++)if(!known.get("worklogs").contains(links.getString(j)))orphanRelations++;
                }
                boolean document=(key.equals("invoices")&&IssuedInvoiceRules.isIssued(row))||key.equals("offers")&&!row.optString("finalized_at").isEmpty();
                if(key.equals("photos")||document){
                    // No fetch, repair, normalization or deletion is permitted here.
                    boolean present=false;
                    try{present=originals.verified(key.equals("photos")?"photo":"document",new JSONObject(row.toString()));}catch(Exception unavailable){/* Count only; never leak filenames or contents into diagnostics. */}
                    boolean pendingDocument=false;if(!present&&key.equals("invoices"))try{pendingDocument=originals.pendingDocument(new JSONObject(row.toString()));}catch(Exception invalidJob){}
                    if(present)verifiedOriginals++;else if(pendingDocument)pendingDocuments++;else missingOriginals++;
                }
            }
        }
        int archivedRecords=0;JSONObject archive=effective.optJSONObject("owner_archive");
        if(archive!=null)for(Iterator<String> keys=archive.keys();keys.hasNext();){
            JSONObject entry=archive.optJSONObject(keys.next());if(entry==null||entry.optBoolean("purged"))continue;
            JSONObject row=entry.optJSONObject("record");if(row==null)continue;archivedRecords++;
            String kind=row.has("content_sha256")||row.has("filename")?"photo":row.has("pdf_sha256")||row.has("pdf_storage_name")||row.has("document_job_id")?"document":"";
            if(!kind.isEmpty()){boolean present=false,pendingDocument=false;try{present=originals.verified(kind,new JSONObject(row.toString()));if(!present&&kind.equals("document"))pendingDocument=originals.pendingDocument(new JSONObject(row.toString()));}catch(Exception unavailable){}if(present)verifiedOriginals++;else if(pendingDocument)pendingDocuments++;else missingOriginals++;}
        }
        int pending=state.optJSONArray("pending")==null?0:state.getJSONArray("pending").length();
        int reviews=remainingHistoricalReviews(master,Collections.emptyMap());
        if(!projectionApplied)blockers.put("PENDING_PROJECTION_REVIEW_REQUIRED");
        if(PixelDriveState.ENGINE.equals(master.optString("processing_engine"))||LocalAuthorityRules.ENGINE.equals(master.optString("processing_engine"))){
            try{
                JSONArray actions=DriveJournalRules.union(state.optJSONArray("pending")==null?new JSONArray():state.getJSONArray("pending"));
                JSONObject replay=new JSONObject(master.toString());JSONArray errors=DriveJournalRules.apply(replay,actions);
                reviews=remainingHistoricalReviews(master,validatedLocalResolutions(master,actions,replay,errors))+errors.length();
            }catch(IllegalArgumentException invalid){blockers.put("PENDING_PROJECTION_REVIEW_REQUIRED");reviews++;}
        }
        if(master.optInt("schema_version",1)>2)blockers.put("SCHEMA_NOT_SUPPORTED");
        if(duplicateIds>0)blockers.put("DUPLICATE_IDS");
        if(orphanRelations>0)blockers.put("RELATION_REVIEW_REQUIRED");
        if(missingOriginals>0)blockers.put("LOCAL_ORIGINALS_INCOMPLETE");
        if(pending>0)blockers.put("PENDING_ACTION_RECONCILIATION_REQUIRED");
        if(reviews>0)blockers.put("EXISTING_CONFLICT_REVIEW_REQUIRED");
        // These require independently verified evidence, not booleans supplied by a client.
        blockers.put("DATABASE_SETTINGS_AND_STAGED_FILES_INVENTORY_REQUIRED").put("FULL_EXTERNAL_BACKUP_REQUIRED").put("OLD_WRITER_RETIREMENT_PROOF_REQUIRED").put("CURRENT_INVOICE_NUMBER_RECONCILIATION_REQUIRED");
        JSONObject session=state.optJSONObject("work_session");
        if(session!=null&&session.optBoolean("review_required"))blockers.put("SESSION_REVIEW_REQUIRED");
        JSONObject result=new JSONObject().put("format",FORMAT).put("source_schema_version",master.optInt("schema_version",1))
                .put("counts",counts).put("id_set_sha256",ids).put("collection_sha256",hashes)
                .put("source_master_sha256",PixelMcpWriteTools.hash(master))
                .put("pending_sha256",PixelMcpWriteTools.hash(state.optJSONArray("pending")))
                .put("projection_applied",projectionApplied).put("inventory_scope","effective_business_records_and_archive_references").put("full_device_inventory",false).put("archived_records",archivedRecords).put("pending_actions",pending).put("historical_reviews",reviews).put("duplicate_ids",duplicateIds)
                .put("orphan_relations",orphanRelations).put("verified_local_originals",verifiedOriginals).put("missing_local_originals",missingOriginals).put("pending_document_jobs",pendingDocuments)
                .put("historical_hours_decimal",hours.toPlainString()).put("historical_invoice_amounts_decimal",invoiceAmounts.toPlainString())
                .put("session_state",session==null?"UNKNOWN":session.optString("state","UNKNOWN"))
                .put("session_revision_sha256",session==null?"":session.optString("revision_hash"))
                .put("blockers",blockers).put("local_authority_activated",false).put("invoice_issue_allowed",false)
                .put("state_changed",false).put("external_backup_verified",false).put("remote_upload_verified",false);
        if(!originalHash.equals(PixelMcpWriteTools.hash(state)))throw new IllegalStateException("Migrationsinventar darf den Ausgangsstand nicht verändern.");
        return result;
    }
    /** Historical failures are obligations, not an upload queue. Missing actions must never erase them. */
    private static int remainingHistoricalReviews(JSONObject master,Map<String,JSONObject> verified)throws Exception{
        int remaining=0;
        for(String key:new String[]{"sync_errors","drive_journal_errors","drive_review_actions"}){
            JSONArray entries=master.optJSONArray(key);
            for(int i=0;entries!=null&&i<entries.length();i++){
                JSONObject entry=entries.optJSONObject(i),original=entry==null?null:verified.get(entry.optString("client_id"));
                boolean fulfilled=original!=null;
                if(fulfilled&&key.equals("drive_review_actions"))fulfilled=OrderReviewRules.actionHash(original).equals(OrderReviewRules.actionHash(entry));
                else if(fulfilled&&entry.has("kind"))fulfilled=original.getString("kind").equals(entry.optString("kind"));
                if(!fulfilled)remaining++;
            }
        }
        return remaining;
    }
    /**
     * Recheck the explicit owner decision as well as its complete journal replay. Existing resolved
     * flags/receipts alone cannot discharge a failure, and this method never edits the source state.
     */
    private static Map<String,JSONObject> validatedLocalResolutions(JSONObject master,JSONArray actions,JSONObject replay,JSONArray errors)throws Exception{
        Map<String,JSONObject> all=new HashMap<>(),verified=new HashMap<>();Set<String> failed=new HashSet<>();
        for(int i=0;i<actions.length();i++){JSONObject action=actions.getJSONObject(i);all.put(action.getString("client_id"),action);}
        for(int i=0;i<errors.length();i++)failed.add(errors.getJSONObject(i).optString("client_id"));
        JSONObject history=replay.optJSONObject("owner_conflict_history"),resolved=replay.optJSONObject("drive_resolved_actions"),receipts=replay.optJSONObject("drive_action_receipts");
        if(history==null||resolved==null||receipts==null)return verified;
        JSONArray ordered=SyncActionOrder.order(actions),prefix=new JSONArray();
        for(int i=0;i<ordered.length();i++){
            JSONObject action=ordered.getJSONObject(i);prefix.put(action);if(!"conflict_resolution".equals(action.optString("kind")))continue;
            String request=action.getString("client_id"),target=action.getJSONObject("payload").optString("client_id");
            if(target.isEmpty()||failed.contains(target)||failed.contains(request))continue;
            try{
                // Validate at the decision's actual journal position, before later valid edits.
                // A prefix replay may clear an orphan historical error; restore those original
                // obligations for the owner's infrastructure checks rather than trusting that loss.
                JSONArray before=new JSONArray();for(int j=0;j<prefix.length()-1;j++)before.put(prefix.get(j));
                JSONObject checked=new JSONObject(master.toString());DriveJournalRules.apply(checked,before);
                for(String key:new String[]{"sync_errors","drive_journal_errors"})if(master.has(key))checked.put(key,new JSONArray(master.getJSONArray(key).toString()));
                OwnerJournalRules.apply(checked,action,all,new JSONObject(checked.getJSONObject("drive_resolved_actions").toString()));
                JSONObject proof=checked.getJSONObject("owner_conflict_history").getJSONObject(target),original=proof.getJSONObject("original_action");
                String hash=OrderReviewRules.actionHash(original);JSONObject applied=history.getJSONObject(target),receipt=receipts.getJSONObject(request);
                if(!hash.equals(resolved.optString(target))||!request.equals(applied.optString("resolution_action"))||!hash.equals(applied.optString("action_hash"))
                    ||!hash.equals(OrderReviewRules.actionHash(applied.getJSONObject("original_action")))
                    ||!OwnerJournalRules.version(proof.optJSONObject("previous")).equals(OwnerJournalRules.version(applied.optJSONObject("previous")))
                    ||!proof.getString("resolution").equals(applied.optString("resolution"))||!proof.getString("reason").equals(applied.optString("reason"))||!proof.getString("resolved_at").equals(applied.optString("resolved_at"))
                    ||!"conflict_resolution".equals(receipt.optString("kind"))||!"applied".equals(receipt.optString("state"))
                    ||!PixelMcpWriteTools.hash(action).equals(receipt.optString("action_sha256"))||!OrderReviewRules.actionHash(action).equals(receipt.optString("review_sha256")))continue;
                verified.put(target,original);
            }catch(Exception unproven){/* Keep every historical obligation when any proof is incomplete. */}
        }
        return verified;
    }
    private static BigDecimal decimal(JSONObject row,String key){
        Object value=row.opt(key);if(value==null||value==JSONObject.NULL||value.toString().isEmpty())return BigDecimal.ZERO;
        return new BigDecimal(value.toString());
    }
}
