package at.hausgarten.service;

import org.json.*;

/** Versioned migration of the existing snapshot, executed by the operative Pixel. */
final class PixelDriveState {
    static final String ENGINE="pixel_drive_v1";
    static final String[] CAPABILITIES={"offers_v1","service_catalog_v1","owner_journal_v2",
        "invoice_compare_and_save","photo_compare_and_save","durable_action_receipts",
        "worklog_compare_and_save","entity_compare_and_save","customer_custom_fields_compare_and_save",
        "billing_relations","photo_attachments_v1","recoverable_delete_v1","trash_purge_v1","trash_empty_v1","appointments_v1"};

    static void prepare(JSONObject snapshot)throws Exception {
        DriveJournalRules.validateSnapshot(snapshot);
        // Missing legacy settings mean UNKNOWN, never a licence to insert defaults.
        if(!snapshot.has("business_settings")) {
            JSONObject known=new JSONObject(),issuer=snapshot.optJSONObject("invoice_issuer");
            for(String key:PixelMcpOwnerTools.BUSINESS_KEYS)if(issuer!=null&&issuer.has(key))known.put(key,issuer.get(key));
            snapshot.put("business_settings",known).put("business_settings_origin","legacy_known_fields_only");
        }
        if(snapshot.optJSONObject("business_settings")==null)throw new IllegalArgumentException("Ungültige Rechnungseinstellungen; keine Vorgaben eingesetzt.");
        JSONObject caps=snapshot.optJSONObject("capabilities");if(caps==null)caps=new JSONObject();
        // These capabilities describe the handlers in THIS runtime, not an external service.
        for(String key:CAPABILITIES)caps.put(key,true);
        snapshot.put("capabilities",caps).put("processing_engine",ENGINE).put("schema_version",Math.max(2,snapshot.optInt("schema_version",1)));
    }

    static boolean retryableInfrastructure(JSONObject failure,String kind) {
        return failure!=null&&"SYNC_COMPONENT_UPDATE_REQUIRED".equals(failure.optString("code"))
            &&java.util.Arrays.asList("offer","offer_finalize","offer_convert","service_template").contains(kind);
    }
    static JSONObject integrity(JSONObject stored)throws Exception {
        JSONObject counts=new JSONObject(),hashes=new JSONObject();
        for(String group:new String[]{"customers","orders","worklogs","invoices","photos","service_catalog","offers","appointments"}){
            JSONArray rows=stored.optJSONArray(group);java.util.TreeMap<String,JSONObject> sorted=new java.util.TreeMap<>();
            for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.getJSONObject(i);sorted.put(row.getString("id"),DriveJournalRules.clean(row));}
            counts.put(group,sorted.size());hashes.put(group,PixelMcpWriteTools.hash(new JSONArray(sorted.values())));
        }
        return new JSONObject().put("scope","stored_business_collections_without_pending_overlay").put("counts",counts).put("collection_hashes",hashes).put("business_state_hash",PixelMcpWriteTools.hash(hashes));
    }
}
