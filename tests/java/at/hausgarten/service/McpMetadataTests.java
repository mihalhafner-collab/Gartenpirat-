package at.hausgarten.service;
import org.json.*;
import java.util.Arrays;

/** Real metadata responses stay consistent, fresh and read-only; timing is host CPU only. */
public final class McpMetadataTests {
    public static void main(String[] unused)throws Exception{
        int[] writes={0};JSONObject state=SyntheticBusinessData.object().put("master",SyntheticBusinessData.object().put("customers",new JSONArray()).put("orders",new JSONArray()).put("worklogs",new JSONArray()).put("invoices",new JSONArray())).put("pending",new JSONArray());
        PixelMcpWriteTools.Sink sink=new PixelMcpWriteTools.Sink(){public JSONObject execute(String name,JSONObject args){writes[0]++;throw new AssertionError("Metadata wrote data");}public JSONObject status(String id,JSONObject s){return new JSONObject();}};
        PixelMcpReadTools tools=new PixelMcpReadTools(()->state,sink);
        JSONObject first=tools.call("get_capabilities",new JSONObject()).getJSONObject("data");
        if(!first.getString("tool_catalog_hash").equals(PixelMcpWriteTools.hash(first.getJSONArray("tools"))))throw new AssertionError("Catalog hash differs from advertised tools");
        int toolCount=first.getJSONArray("tools").length();
        first.getJSONArray("tools").remove(0);
        if(tools.call("get_capabilities",new JSONObject()).getJSONObject("data").getJSONArray("tools").length()!=toolCount)throw new AssertionError("Caller changed subsequent metadata");
        for(String name:new String[]{"get_capabilities","integration_health"}){
            for(int i=0;i<5;i++)tools.call(name,new JSONObject());long[] samples=new long[15];
            for(int i=0;i<samples.length;i++){long start=System.nanoTime();JSONObject data=tools.call(name,new JSONObject()).getJSONObject("data");samples[i]=System.nanoTime()-start;if(data.getString("tool_catalog_hash").isEmpty())throw new AssertionError("Missing hash");}
            Arrays.sort(samples);System.out.println("MCP_METADATA_CPU "+new JSONObject().put("tool",name).put("samples",samples.length).put("p50_ms",samples[7]/1e6).put("p95_ms",samples[14]/1e6).put("native_or_network_measured",false));
        }
        if(writes[0]!=0)throw new AssertionError("Metadata must remain read-only");
        PixelMcpReadTools local=new PixelMcpReadTools(()->state,sink,(name,arguments)->new JSONObject().put("invoice_finalization","pixel_local_reconciliation_required").put("storage_contract_version",3).put("drive_role","retired_local_backup"));
        JSONObject localCapabilities=local.call("get_capabilities",new JSONObject()).getJSONObject("data");if(!localCapabilities.getString("invoice_finalization").equals("pixel_local_reconciliation_required")||localCapabilities.getInt("storage_contract_version")!=3||!localCapabilities.getString("drive_role").equals("retired_local_backup")||localCapabilities.getBoolean("business_sync_allowed"))throw new AssertionError("Final local authority transition contract differs");
        JSONObject unmigrated=tools.call("get_capabilities",new JSONObject()).getJSONObject("data");if(!unmigrated.getString("invoice_finalization").equals("pixel_local_migration_required")||unmigrated.getInt("storage_contract_version")!=3||!unmigrated.getString("drive_role").equals("retired_local_backup"))throw new AssertionError("Unmigrated final app claimed an online invoice path");if(writes[0]!=0)throw new AssertionError("Capability transition performed a business write");
        int[] invoiceReads={0};JSONObject invoice=new JSONObject().put("id","qa_atomic_invoice").put("customer_id","qa_atomic_customer").put("invoice_date","2027-05-18").put("positions",new JSONArray().put(new JSONObject().put("title","QA atomically reviewed").put("quantity",1).put("price",1))),review=new JSONObject().put("context_hash","a".repeat(64)).put("record_hash","b".repeat(64));
        PixelMcpReadTools atomic=new PixelMcpReadTools(()->{throw new AssertionError("Invoice review split into another source read");},sink,(name,arguments)->{if(!name.equals("get_invoice_issue_review")||!arguments.getString("invoice_id").equals("qa_atomic_invoice"))throw new AssertionError("Wrong native review query");invoiceReads[0]++;return new JSONObject().put("invoice",invoice).put("record_hash","b".repeat(64)).put("issue_review",review);});
        JSONObject read=atomic.call("get_invoice",new JSONObject().put("invoice_id","qa_atomic_invoice"));if(invoiceReads[0]!=1||!read.getString("record_hash").equals(read.getJSONObject("issue_review").getString("record_hash"))||!read.getJSONObject("data").getString("id").equals("qa_atomic_invoice")||!read.getString("entity_hash").equals(OwnerJournalRules.version(invoice)))throw new AssertionError("Atomic invoice/record/context envelope separated");
        for(JSONObject invalid:new JSONObject[]{new JSONObject(),new JSONObject().put("invoice_id","qa_atomic_invoice").put("qa_unknown",true),new JSONObject().put("invoice_id",1)}){try{atomic.call("get_invoice",invalid);throw new AssertionError("Invalid invoice input reached native review");}catch(IllegalArgumentException expected){}}
        if(invoiceReads[0]!=1)throw new AssertionError("Native review must follow public read validation");
        JSONArray atomicCatalog=atomic.tools();for(int i=0;i<atomicCatalog.length();i++)if(atomicCatalog.getJSONObject(i).getString("name").equals("get_invoice_issue_review"))throw new AssertionError("Internal review adapter became a separate public tool");
        state.getJSONObject("master").getJSONArray("invoices").put(invoice);PixelMcpReadTools legacy=new PixelMcpReadTools(()->state,sink,(name,arguments)->null);if(legacy.call("get_invoice",new JSONObject().put("invoice_id","qa_atomic_invoice")).has("issue_review"))throw new AssertionError("Legacy invoice read was silently reinterpreted");if(writes[0]!=0)throw new AssertionError("Invoice review performed a business write");
        finalStorageStatus();System.out.println("PASS metadata catalog/hash consistency, final backup-only transition, fresh results and zero writes; host CPU measurements only");
    }
    private static void finalStorageStatus()throws Exception {
        JSONObject state=new JSONObject().put("storage_contract_version",3).put("drive_role","retired_local_backup").put("master",new JSONObject().put("processing_engine",PixelDriveState.ENGINE)).put("pending",new JSONArray());String before=PixelMcpWriteTools.hash(state);
        for(String phase:new String[]{"queued","accepted","transferred","failed","superseded","unknown"}){
            JSONObject old=new JSONObject().put("client_id","qa_backup_only_action").put("status",phase).put("publication_id","qa_old_publication").put("uploaded_to_drive",true).put("cloud_confirmed",true).put("confirmation_scope","drive_server_manifest");JSONObject status=PixelMcpBusinessTools.sync(old,state);boolean review=phase.equals("failed")||phase.equals("superseded");
            if(status.getInt("storage_contract_version")!=3||!status.getString("drive_role").equals("retired_local_backup")||!status.getString("code").equals("LEGACY_SYNC_RETIRED")||status.getBoolean("cloud_confirmed")||status.getBoolean("retryable")||status.getBoolean("uploaded_to_drive")||status.has("publication_id"))throw new AssertionError("Retired per-action status claims an operational Drive exchange");
            if(!status.getJSONObject("historical_sync_inventory").getString("status").equals(phase)||!status.getJSONObject("historical_sync_inventory").getString("publication_id").equals("qa_old_publication")||status.getBoolean("review_required")!=review)throw new AssertionError("Historical evidence or conflict was hidden");
            if(phase.equals("unknown")&&(status.getBoolean("saved_locally")||!status.getString("status").equals("unknown")))throw new AssertionError("Unknown action was invented as saved");String once=PixelMcpWriteTools.hash(status);if(!once.equals(PixelMcpWriteTools.hash(PixelMcpBusinessTools.sync(status,state))))throw new AssertionError("Repeated status decoration changed original evidence");
        }
        if(!before.equals(PixelMcpWriteTools.hash(state)))throw new AssertionError("Status changed pending or original business data");
        JSONObject retirement=DriveRolePolicy.retiredResponse();if(!retirement.getString("state").equals("retired")||retirement.getBoolean("backup_started")||retirement.getBoolean("business_sync_allowed")||!retirement.getJSONObject("next_action").getString("status_tool").equals("get_full_backup_status"))throw new AssertionError("Sync retirement silently starts a backup");
    }
}
