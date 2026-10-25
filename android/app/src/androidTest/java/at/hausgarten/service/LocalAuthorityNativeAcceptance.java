package at.hausgarten.service;

import org.json.*;

/** Actual isolated SQLite plus no-backup device fences; evidence is explicitly synthetic. */
final class LocalAuthorityNativeAcceptance {
    private static JSONObject evidence()throws Exception {return new JSONObject().put("formatVersion",1).put("external_writers_disabled",true).put("evidence_sha256",PixelMcpWriteTools.hash("qa_synthetic_writer_proof")).put("evidence_reference","qa_isolated_fixture_no_external_writer").put("checked_at",java.time.Instant.now().toString());}
    private static JSONObject ledger(OwnerNativeAcceptance.Fixture f,int last)throws Exception {JSONObject proof=evidence(),identity=new BackupDeviceGuard(f.context).current();return proof.put("last_number",last).put("issued",new JSONArray()).put("reservations",new JSONArray()).put("current_review",new JSONObject().put("confirmed",true).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch")).put("reviewed_at",java.time.Instant.now().toString()).put("evidence_sha256",proof.getString("evidence_sha256")));}
    private static JSONObject activate(OwnerNativeAcceptance.Fixture f,String request)throws Exception {
        LocalAuthorityMigration migration=new LocalAuthorityMigration(f.context,f.store);JSONObject preview=migration.preview();JSONObject backup=new JSONObject().put("formatVersion",1).put("verified",true).put("external_verified",true).put("archive_sha256",PixelMcpWriteTools.hash("qa_synthetic_archive_proof")).put("business_snapshot_sha256",preview.getString("state_sha256")).put("captured_at","2026-10-24T00:00:00Z").put("verified_at","2026-10-24T00:01:00Z");return migration.activate(request,preview.getString("preview_hash"),backup,evidence());
    }
    private static void rejected(PixelMcpInstrumentation test,String code,PixelMcpInstrumentation.Task task)throws Exception {
        try{task.run();throw new AssertionError("Expected "+code);}catch(java.io.IOException|IllegalArgumentException expected){test.yes(expected.getMessage().equals(code));}
    }
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("local authority cutover preserves journal and business data and fences legacy sync",()->{
            try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
                f.store.saveCustomer(new JSONObject().put("name","[TEST] QA Migration").put("test_record",true));int pending=f.store.pendingActions().length();String hours=PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs").getJSONObject(0).get("hours").toString();
                JSONObject result=activate(f,"qa_authority_activate");test.yes(result.getString("mode").equals("LOCAL_ACTIVE")&&result.getString("invoice_authority").equals("LOCKED"));test.yes(f.store.pendingActions().length()==pending&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("customers").length()==2);test.yes(!LocalAuthorityGuard.legacySyncAllowed(f.context));
                test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs").getJSONObject(0).get("hours").toString().equals(hours));test.yes(f.store.getReadableDatabase().getVersion()==6);
                try{f.store.commitSnapshot(f.store.getState().getJSONObject("master"),new java.util.HashSet<>());throw new AssertionError("legacy sync wrote local data");}catch(IllegalStateException|java.io.IOException expected){test.yes(true);}
                rejected(test,"INVOICE_AUTHORITY_RECONCILIATION_REQUIRED",()->new LocalAuthorityGuard(f.context).requireInvoiceAuthority());
            }
        });
        test.probe("invoice authority rejects restored reconciliation receipt and preserves device highwater through remigration",()->{
            try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
                activate(f,"qa_authority_first");LocalAuthorityMigration migration=new LocalAuthorityMigration(f.context,f.store);JSONObject ledger=ledger(f,77);JSONObject args=new JSONObject().put("request_id","qa_authority_reconcile").put("expected_preview_hash",migration.preview().getString("preview_hash")).put("ledger",ledger);
                test.yes(migration.reconcile(args).getString("invoice_authority").equals("RECONCILED"));new LocalAuthorityGuard(f.context).requireInvoiceAuthority();test.yes(migration.reconcile(args).getBoolean("replayed"));
                new LocalAuthorityGuard(f.context).blockAfterRestore("qa_authority_restore",new JSONObject().put("backupId","qa_older_backup"));rejected(test,"LOCAL_AUTHORITY_RECEIPT_STALE_AFTER_RESTORE",()->migration.reconcile(args));rejected(test,"INVOICE_AUTHORITY_RECONCILIATION_REQUIRED",()->new LocalAuthorityGuard(f.context).requireInvoiceAuthority());
                JSONObject stale=new JSONObject(args.toString()).put("request_id","qa_authority_old_ledger_new_request").put("expected_preview_hash",migration.preview().getString("preview_hash"));rejected(test,"LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED",()->migration.reconcile(stale));
                activate(f,"qa_authority_after_restore");test.yes(new LocalAuthorityGuard(f.context).current().getInt("invoice_high_water")==77);JSONObject lower=new JSONObject().put("request_id","qa_authority_lower").put("expected_preview_hash",migration.preview().getString("preview_hash")).put("ledger",ledger(f,1));rejected(test,"INVOICE_NUMBER_ROLLBACK_DETECTED",()->migration.reconcile(lower));
            }
        });
        test.probe("interrupted local authority migration remains locked and repeated startup keeps same business state",()->{
            try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
                LocalAuthorityGuard guard=new LocalAuthorityGuard(f.context);JSONObject staged=guard.beginMigration("qa_authority_interrupted",guard.current().getString("revision"),evidence());guard.recoverInterruptedMigration();test.yes(guard.current().getString("mode").equals("RESTORE_REVIEW"));guard.recoverInterruptedMigration();test.yes(guard.current().getString("migration_epoch").equals(staged.getString("migration_epoch"))&&!LocalAuthorityGuard.legacySyncAllowed(f.context));test.yes(f.store.pendingActions().length()==0&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs").length()==1);rejected(test,"INVOICE_AUTHORITY_RECONCILIATION_REQUIRED",guard::requireInvoiceAuthority);
            }
        });
        test.probe("locally reviewed conflict permits explicit cutover only after verified archive and current writer proof while preserving original queue",()->{
            try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
                BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);
                try{
                    // The normal mutation precedes the conflicting create; later writes must
                    // not be smuggled past an unresolved predecessor just to fabricate stale CAS.
                    JSONObject beforeMutation=f.tools.call("get_customer",new JSONObject().put("customer_id","qa_c"));
                    String oldCurrentHash=beforeMutation.getString("entity_hash");
                    f.call("update_customer",new JSONObject().put("request_id","qa_migration_before_decision").put("customer_id","qa_c").put("expected_record_hash",beforeMutation.getString("record_hash")).put("updates",new JSONObject().put("notes","QA pending before conflict decision")));
                    JSONObject changedMaster=f.store.getState().getJSONObject("master");test.yes(DriveJournalRules.apply(changedMaster,f.store.pendingActions()).length()==0);f.store.commitSnapshot(changedMaster,new java.util.HashSet<>());
                    f.store.saveAction("customer","qa_",new JSONObject().put("id","qa_c").put("name","QA proposed duplicate").put("_mcp_create",true));
                    JSONObject master=f.store.getState().getJSONObject("master");JSONArray errors=DriveJournalRules.apply(master,f.store.pendingActions());test.yes(errors.length()==1&&errors.getJSONObject(0).getString("code").equals("WRITE_CONFLICT"));
                    master.put("sync_errors",new JSONArray(errors.toString())).put("drive_review_actions",DriveJournalRules.reviewActions(master,f.store.pendingActions()));f.store.commitSnapshot(master,new java.util.HashSet<>());
                    LocalAuthorityMigration migration=new LocalAuthorityMigration(f.context,f.store);test.yes(migration.preview().getJSONObject("inventory").getInt("historical_reviews")==4);
                    rejected(test,"LOCAL_MIGRATION_DATA_REVIEW_REQUIRED",()->activate(f,"qa_unresolved_must_block"));
                    JSONObject conflict=f.call("get_sync_conflicts",new JSONObject()).getJSONArray("conflicts").getJSONObject(0);
                    test.yes(conflict.getJSONObject("current").getString("notes").equals("QA pending before conflict decision")&&!conflict.getString("current_hash").equals(oldCurrentHash));
                    try{f.call("resolve_sync_conflict",new JSONObject().put("request_id","qa_migration_stale_decision").put("client_id",conflict.getString("client_id")).put("expected_action_hash",conflict.getString("action_hash")).put("expected_current_hash",oldCurrentHash).put("resolution","keep_current"));throw new AssertionError("QA_AUTH_STALE_EFFECTIVE_CAS_ACCEPTED");}catch(PixelMcpWriteTools.Rejected expected){test.yes(expected.code.equals("WRITE_CONFLICT")&&f.store.pendingActions().length()==2);}
                    JSONObject decision=new JSONObject().put("request_id","qa_migration_explicit_resolution").put("client_id",conflict.getString("client_id")).put("expected_action_hash",conflict.getString("action_hash")).put("expected_current_hash",conflict.getString("current_hash")).put("resolution","keep_current");
                    f.call("resolve_sync_conflict",decision);f.call("resolve_sync_conflict",decision);test.yes(f.store.pendingActions().length()==3);
                    JSONObject reviewed=f.tools.call("get_customer",new JSONObject().put("customer_id","qa_c")),later=new JSONObject().put("request_id","qa_migration_after_decision").put("customer_id","qa_c").put("expected_record_hash",reviewed.getString("record_hash")).put("updates",new JSONObject().put("notes","QA pending after conflict decision"));f.call("update_customer",later);f.call("update_customer",later);test.yes(f.store.pendingActions().length()==4);
                    test.yes(f.call("get_customer",new JSONObject().put("customer_id","qa_c")).getString("notes").equals("QA pending after conflict decision"));
                    String raw=f.store.snapshotText(""),pending=PixelMcpWriteTools.hash(f.store.pendingActions()),business=PixelMcpWriteTools.hash(SnapshotProjection.effective(f.store.getState()).getJSONArray("customers"));
                    JSONObject preview=migration.preview();test.yes(preview.getJSONObject("inventory").getInt("historical_reviews")==0&&raw.equals(f.store.snapshotText(""))&&pending.equals(PixelMcpWriteTools.hash(f.store.pendingActions())));
                    JSONObject incomplete=new JSONObject().put("formatVersion",1).put("verified",true).put("external_verified",false).put("archive_sha256",PixelMcpWriteTools.hash("qa_incomplete_archive")).put("business_snapshot_sha256",preview.getString("state_sha256"));
                    rejected(test,"LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED",()->migration.activate("qa_missing_external_archive",preview.getString("preview_hash"),incomplete,evidence()));
                    String code=BackupCrypto.newRecoveryCode();keys.confirmAndStore("qa_migration_key",code,code);JSONObject browser=new JSONObject().put("formatVersion",1).put("storage",new JSONObject());
                    BackupSettingsRepository settings=new BackupSettingsRepository(f.context,f.store);settings.saveBrowser("qa_migration_browser",settings.browserRevision(),browser);
                    LocalBackupCoordinator backups=new LocalBackupCoordinator(f.context,f.store);String backupId="qa_resolved_migration_archive";
                    backups.createWithIntent("qa_migration_archive_request",backupId,new JSONObject().put("kind","saf").put("treeUri","content://qa.synthetic.documents/tree/qa_migration"),browser,"manual");backups.prepareLocal(backupId);
                    JSONObject args=new JSONObject().put("request_id","qa_resolved_migration_activate").put("expected_preview_hash",migration.preview().getString("preview_hash")).put("backup_id",backupId).put("writer_evidence",evidence());
                    // A sealed local file is not external protection. This is still blocked.
                    rejected(test,"LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED",()->migration.activateFromArgs(args));
                    JSONObject archive=backups.status(backupId).getJSONObject("archive"),provider=new JSONObject().put("backup_id",backupId).put("archive_sha256",archive.getString("sha256")).put("byte_size",archive.getLong("size")).put("verified_at",java.time.Instant.now().toString()).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("destination_location","REMOVABLE_STORAGE_VOLUME").put("external_storage_verified",true).put("cloud_confirmed",false);
                    // Explicit synthetic provider evidence; no USB/provider/device claim.
                    backups.recordProviderProof(backupId,provider);test.yes(backups.verifiedArchive(backupId).isFile());
                    JSONObject invalidWriter=new JSONObject(args.toString()).put("writer_evidence",new JSONObject(args.getJSONObject("writer_evidence").toString()).put("external_writers_disabled",false));
                    try{migration.activateFromArgs(invalidWriter);throw new AssertionError("QA invalid writer proof accepted");}catch(IllegalArgumentException expected){test.yes(expected.getMessage().equals("LOCAL_AUTHORITY_EVIDENCE_REQUIRED"));}
                    test.yes(new LocalAuthorityGuard(f.context).current().getString("mode").equals("LEGACY_DRIVE")&&raw.equals(f.store.snapshotText(""))&&pending.equals(PixelMcpWriteTools.hash(f.store.pendingActions())));
                    JSONObject result=migration.activateFromArgs(args);test.yes(result.getString("mode").equals("LOCAL_ACTIVE")&&result.getString("invoice_authority").equals("LOCKED")&&migration.activateFromArgs(args).getBoolean("replayed"));
                    test.yes(raw.equals(f.store.ownerValue("legacy_snapshot_before_local:qa_resolved_migration_activate",""))&&pending.equals(PixelMcpWriteTools.hash(f.store.pendingActions()))&&business.equals(PixelMcpWriteTools.hash(SnapshotProjection.effective(f.store.getState()).getJSONArray("customers"))));
                    JSONObject canonical=f.store.getState().getJSONObject("master"),history=canonical.getJSONObject("owner_conflict_history").getJSONObject(conflict.getString("client_id"));test.yes(history.getString("action_hash").equals(conflict.getString("action_hash"))&&history.getString("resolution").equals("keep_current")&&history.getJSONObject("previous").getString("notes").equals("QA pending before conflict decision")&&f.call("get_customer",new JSONObject().put("customer_id","qa_c")).getString("notes").equals("QA pending after conflict decision"));
                    rejected(test,"INVOICE_AUTHORITY_RECONCILIATION_REQUIRED",()->new LocalAuthorityGuard(f.context).requireInvoiceAuthority());
                }finally{BackupCoreNativeAcceptance.deleteQaKey(f,keys);}
            }
        });
    }
    private LocalAuthorityNativeAcceptance(){}
}
