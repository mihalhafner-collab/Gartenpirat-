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
        try{task.run();throw new AssertionError("Expected "+code);}catch(java.io.IOException expected){test.yes(expected.getMessage().equals(code));}
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
    }
    private LocalAuthorityNativeAcceptance(){}
}
