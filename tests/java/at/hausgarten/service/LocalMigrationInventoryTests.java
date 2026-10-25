package at.hausgarten.service;
import org.json.*;

/** Representative synthetic old snapshots; no device/account/file contents. */
public final class LocalMigrationInventoryTests {
    static int checks;
    private static String fixtureEngine=PixelDriveState.ENGINE;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("inventory "+checks);}
    private static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    /** Reuse the real owner use case and the existing synthetic effects port. */
    private static OwnerApplicationTests.MemoryRepository conflict()throws Exception{
        OwnerApplicationTests.MemoryRepository repository=new OwnerApplicationTests.MemoryRepository();JSONObject master=OrderReviewTests.snapshot();PixelDriveState.prepare(master);master.put("processing_engine",fixtureEngine);
        JSONObject current=master.getJSONArray("orders").getJSONObject(0),original=OrderReviewTests.action("qa_inventory_conflict","order",copy(current).put("title","QA proposed").put("_sync_base",DriveJournalRules.guard(current,"order")));
        current.put("title","QA current");JSONArray pending=new JSONArray().put(original),errors=DriveJournalRules.apply(master,pending);yes(errors.length()==1&&errors.getJSONObject(0).getString("code").equals("WRITE_CONFLICT"));
        master.put("sync_errors",new JSONArray(errors.toString())).put("drive_review_actions",DriveJournalRules.reviewActions(master,pending));repository.state.put("master",master).put("pending",pending);return repository;
    }
    private static JSONObject resolve(OwnerApplicationTests.MemoryRepository repository,String resolution)throws Exception{
        JSONObject found=PixelMcpOwnerTools.conflicts(repository.state,new JSONObject().put("client_id","qa_inventory_conflict").put("limit",1)).getJSONArray("conflicts").getJSONObject(0);
        yes(found.getBoolean("resolution_available"));OwnerApplicationTests.invoke(new OwnerApplicationService(repository),"resolve_sync_conflict",new JSONObject().put("request_id","qa_inventory_owner_decision").put("client_id",found.getString("client_id")).put("expected_action_hash",found.getString("action_hash")).put("expected_current_hash",found.getString("current_hash")).put("resolution",resolution));
        return repository.state.getJSONArray("pending").getJSONObject(repository.pendingActions().length()-1);
    }
    private static int reviews(OwnerApplicationTests.MemoryRepository repository)throws Exception{
        String hash=PixelMcpWriteTools.hash(repository.state);JSONObject report=LocalMigrationInventory.inspect(repository.state,(kind,reference)->true);yes(hash.equals(PixelMcpWriteTools.hash(repository.state)));return report.getInt("historical_reviews");
    }
    private static void localConflictDecisions()throws Exception{
        OwnerApplicationTests.MemoryRepository untreated=conflict();yes(reviews(untreated)==4);
        OwnerApplicationTests.MemoryRepository keep=conflict();resolve(keep,"keep_current");JSONObject before=copy(keep.state),replay=copy(keep.state.getJSONObject("master"));
        yes(DriveJournalRules.apply(replay,keep.state.getJSONArray("pending")).length()==0&&DriveJournalRules.reviewActions(replay,keep.state.getJSONArray("pending")).length()==0);
        yes(replay.getJSONObject("owner_conflict_history").length()==1&&replay.getJSONArray("sync_errors").length()==0&&replay.getJSONArray("drive_journal_errors").length()==0);
        // Regression: all three raw entries formerly blocked activation despite this exact replay.
        yes(reviews(keep)==0&&PixelMcpWriteTools.hash(before).equals(PixelMcpWriteTools.hash(keep.state)));
        yes(keep.state.getJSONObject("master").getJSONArray("drive_review_actions").length()==1&&keep.state.getJSONArray("pending").length()==2);
        OwnerApplicationTests.MemoryRepository proposed=conflict();resolve(proposed,"apply_proposed");yes(reviews(proposed)==0);JSONObject applied=copy(proposed.state.getJSONObject("master"));DriveJournalRules.apply(applied,proposed.pendingActions());yes(applied.getJSONArray("orders").getJSONObject(0).getString("title").equals("QA proposed"));
        OwnerApplicationTests.MemoryRepository priorEdit=conflict();JSONObject old=priorEdit.state.getJSONObject("master").getJSONArray("orders").getJSONObject(0);
        JSONObject earlier=OrderReviewTests.action("qa_before_resolution","order",copy(old).put("notes","QA earlier valid edit").put("updated_at","2026-10-19T10:00:00Z").put("_sync_base",DriveJournalRules.guard(old,"order"))).put("created_at","2026-10-19T10:00:00Z");
        priorEdit.pendingActions().put(earlier);JSONObject priorDecision=resolve(priorEdit,"keep_current");
        yes(!priorDecision.getJSONObject("payload").getString("expected_current_hash").equals(OwnerJournalRules.version(old))&&reviews(priorEdit)==0);
        OwnerApplicationTests.MemoryRepository laterEdit=conflict();resolve(laterEdit,"keep_current");JSONObject decided=copy(laterEdit.state.getJSONObject("master"));DriveJournalRules.apply(decided,laterEdit.pendingActions());JSONObject decidedRow=decided.getJSONArray("orders").getJSONObject(0);
        laterEdit.pendingActions().put(OrderReviewTests.action("qa_after_resolution","order",copy(decidedRow).put("title","QA later valid edit").put("updated_at","2026-10-19T13:00:00Z").put("_sync_base",DriveJournalRules.guard(decidedRow,"order"))).put("created_at","2026-10-19T13:00:00Z"));yes(reviews(laterEdit)==0);
        OwnerApplicationTests.MemoryRepository embedded=conflict();JSONObject embeddedDecision=resolve(embedded,"keep_current");embedded.state.put("pending",new JSONArray().put(embeddedDecision));yes(reviews(embedded)==0);
        OwnerApplicationTests.MemoryRepository wrongCurrent=conflict();resolve(wrongCurrent,"keep_current").getJSONObject("payload").put("expected_current_hash",PixelMcpWriteTools.hash("qa_wrong_current"));yes(reviews(wrongCurrent)>0);
        OwnerApplicationTests.MemoryRepository wrongAction=conflict();resolve(wrongAction,"keep_current").getJSONObject("payload").put("expected_action_hash",PixelMcpWriteTools.hash("qa_wrong_action"));yes(reviews(wrongAction)>0);
        OwnerApplicationTests.MemoryRepository changedOriginal=conflict();resolve(changedOriginal,"keep_current").getJSONObject("payload").getJSONObject("_original_action").getJSONObject("payload").put("title","QA changed evidence");yes(reviews(changedOriginal)>0);
        OwnerApplicationTests.MemoryRepository missing=conflict();JSONObject missingDecision=resolve(missing,"keep_current");missingDecision.getJSONObject("payload").remove("_original_action");missing.state.put("pending",new JSONArray().put(missingDecision));missing.state.getJSONObject("master").put("drive_review_actions",new JSONArray());yes(reviews(missing)>0);
        OwnerApplicationTests.MemoryRepository infrastructure=conflict();resolve(infrastructure,"keep_current");for(String key:new String[]{"sync_errors","drive_journal_errors"})infrastructure.state.getJSONObject("master").getJSONArray(key).getJSONObject(0).put("code","NUMBERING_ONLINE_REQUIRED");yes(reviews(infrastructure)>0);
        OwnerApplicationTests.MemoryRepository flags=conflict();JSONObject original=flags.pendingActions().getJSONObject(0);flags.state.getJSONObject("master").getJSONObject("drive_resolved_actions").put(original.getString("client_id"),OrderReviewRules.actionHash(original));yes(reviews(flags)>0);
        // apply() clears these journal errors without validating anything; inventory must retain them.
        OwnerApplicationTests.MemoryRepository orphan=conflict();orphan.state.put("pending",new JSONArray());orphan.state.getJSONObject("master").put("sync_errors",new JSONArray()).put("drive_review_actions",new JSONArray());yes(reviews(orphan)==1);
        OwnerApplicationTests.MemoryRepository unrelated=conflict();resolve(unrelated,"keep_current");unrelated.state.getJSONObject("master").getJSONArray("drive_journal_errors").put(new JSONObject().put("client_id","qa_missing_unrelated").put("code","WRITE_CONFLICT"));yes(reviews(unrelated)==1);
        OwnerApplicationTests.MemoryRepository replacedReview=conflict();resolve(replacedReview,"keep_current");replacedReview.state.getJSONObject("master").getJSONArray("drive_review_actions").getJSONObject(0).getJSONObject("payload").put("title","QA different original");yes(reviews(replacedReview)==1);
        OwnerApplicationTests.MemoryRepository wrongKind=conflict();resolve(wrongKind,"keep_current");wrongKind.state.getJSONObject("master").getJSONArray("drive_journal_errors").getJSONObject(0).put("kind","customer");yes(reviews(wrongKind)==1);
        OwnerApplicationTests.MemoryRepository malformed=conflict();resolve(malformed,"keep_current");malformed.state.getJSONObject("master").getJSONArray("drive_review_actions").put(JSONObject.NULL);yes(reviews(malformed)==1);
    }
    public static void main(String[] args)throws Exception{
        JSONObject state=PixelMcpWriteTests.state();String before=state.toString();
        JSONObject report=LocalMigrationInventory.inspect(state,(kind,reference)->true);
        yes(state.toString().equals(before));yes(!report.getBoolean("local_authority_activated"));
        yes(!report.getBoolean("invoice_issue_allowed"));yes(!report.getBoolean("external_backup_verified"));
        yes(report.getJSONArray("blockers").toString().contains("OLD_WRITER_RETIREMENT_PROOF_REQUIRED"));
        JSONObject master=state.getJSONObject("master");
        JSONArray work=master.getJSONArray("worklogs");work.getJSONObject(0).put("hours","0.1");work.getJSONObject(1).put("hours","0.2");
        JSONObject decimal=LocalMigrationInventory.inspect(state,(kind,reference)->true);
        yes(decimal.getString("historical_hours_decimal").equals("0.3"));
        master.getJSONArray("customers").getJSONObject(0).put("notes","QA PRIVATE TEXT NEVER IN INVENTORY");
        master.put("photos",new JSONArray().put(SyntheticBusinessData.object().put("id","qa_missing_original").put("customer_id","qa_c1")));
        JSONObject missing=LocalMigrationInventory.inspect(state,(kind,reference)->false);
        yes(missing.getInt("missing_local_originals")>0);yes(!missing.toString().contains("QA PRIVATE TEXT"));
        work.getJSONObject(0).put("customer_id","qa_missing_customer");
        JSONObject orphan=LocalMigrationInventory.inspect(state,(kind,reference)->true);
        yes(orphan.getInt("orphan_relations")>0);yes(orphan.getJSONArray("blockers").toString().contains("RELATION_REVIEW_REQUIRED"));
        String hash=orphan.getJSONObject("id_set_sha256").getString("worklogs");
        work.getJSONObject(0).put("notes","QA extra history");
        JSONObject changed=LocalMigrationInventory.inspect(state,(kind,reference)->true);
        yes(changed.getJSONObject("id_set_sha256").getString("worklogs").equals(hash));
        yes(!changed.getJSONObject("collection_sha256").getString("worklogs").equals(orphan.getJSONObject("collection_sha256").getString("worklogs")));
        master.getJSONArray("customers").put(new JSONObject(master.getJSONArray("customers").getJSONObject(0).toString()));
        JSONObject duplicate=LocalMigrationInventory.inspect(state,(kind,reference)->{throw new java.io.IOException("QA unavailable");});
        yes(duplicate.getInt("duplicate_ids")==1);yes(!duplicate.getBoolean("projection_applied"));
        yes(duplicate.getInt("missing_local_originals")>0);
        master.put("schema_version",999);
        yes(LocalMigrationInventory.inspect(state,(kind,reference)->true).getJSONArray("blockers").toString().contains("SCHEMA_NOT_SUPPORTED"));
        for(String engine:new String[]{PixelDriveState.ENGINE,LocalAuthorityRules.ENGINE}){fixtureEngine=engine;localConflictDecisions();}
        System.out.println("PASS "+checks+" old-format inventory/count/decimal/privacy/authority guards; no productive migration");
    }
}
