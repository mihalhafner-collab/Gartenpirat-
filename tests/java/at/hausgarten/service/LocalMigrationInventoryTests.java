package at.hausgarten.service;
import org.json.*;

/** Representative synthetic old snapshots; no device/account/file contents. */
public final class LocalMigrationInventoryTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("inventory "+checks);}
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
        System.out.println("PASS "+checks+" old-format inventory/count/decimal/privacy/authority guards; no productive migration");
    }
}
