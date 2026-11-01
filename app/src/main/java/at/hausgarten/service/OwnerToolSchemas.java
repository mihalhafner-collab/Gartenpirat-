package at.hausgarten.service;
import org.json.*;
import java.util.*;
import static at.hausgarten.service.McpFields.*;

/** Owner schema registry. Builders separate field contracts from catalog/authorization. */
final class OwnerToolSchemas {
    interface Fields { void build(String name, JSONObject p, List<String> required) throws Exception; }
    private static final Map<String, Fields> BUILDERS = new HashMap<>();
    static {
        BUILDERS.put("get_ui_action_catalog", OwnerToolSchemas::getUiActionCatalog);
        BUILDERS.put("inspect_order_review", OwnerToolSchemas::inspectOrderReview);
        BUILDERS.put("save_reviewed_order", OwnerToolSchemas::saveReviewedOrder);
        BUILDERS.put("prepare_legacy_conflict_backup", OwnerToolSchemas::prepareLegacyConflictBackup);
        BUILDERS.put("preview_archive_work_records", OwnerToolSchemas::previewArchiveWorkRecords);
        BUILDERS.put("archive_work_records", OwnerToolSchemas::previewArchiveWorkRecords);
        BUILDERS.put("empty_trash", OwnerToolSchemas::emptyTrash);
        BUILDERS.put("close_legacy_conflicts", OwnerToolSchemas::closeLegacyConflicts);
        BUILDERS.put("get_photo", OwnerToolSchemas::getPhoto);
        BUILDERS.put("list_archive", OwnerToolSchemas::listArchive);
        BUILDERS.put("get_sync_conflicts", OwnerToolSchemas::getSyncConflicts);
        BUILDERS.put("search_catalog", OwnerToolSchemas::searchCatalog);
        BUILDERS.put("get_catalog_entry", OwnerToolSchemas::getCatalogEntry);
        BUILDERS.put("get_file_metadata", OwnerToolSchemas::getFileMetadata);
        BUILDERS.put("read_file_chunk", OwnerToolSchemas::getFileMetadata);
        BUILDERS.put("begin_file_upload", OwnerToolSchemas::beginFileUpload);
        BUILDERS.put("write_file_chunk", OwnerToolSchemas::writeFileChunk);
        BUILDERS.put("create_photo", OwnerToolSchemas::createPhoto);
        BUILDERS.put("set_work_record_billed", OwnerToolSchemas::setWorkRecordBilled);
        BUILDERS.put("reassign_work_record", OwnerToolSchemas::setWorkRecordBilled);
        BUILDERS.put("update_photo", OwnerToolSchemas::updatePhoto);
        BUILDERS.put("preview_purge", OwnerToolSchemas::previewPurge);
        BUILDERS.put("preview_delete", OwnerToolSchemas::previewPurge);
        BUILDERS.put("purge_entity", OwnerToolSchemas::purgeEntity);
        BUILDERS.put("delete_entity", OwnerToolSchemas::purgeEntity);
        BUILDERS.put("archive_entity", OwnerToolSchemas::purgeEntity);
        BUILDERS.put("restore_entity", OwnerToolSchemas::purgeEntity);
        BUILDERS.put("resolve_sync_conflict", OwnerToolSchemas::resolveSyncConflict);
        BUILDERS.put("update_business_settings", OwnerToolSchemas::updateBusinessSettings);
        BUILDERS.put("update_settings", OwnerToolSchemas::updateSettings);
        BUILDERS.put("set_personal_paid", OwnerToolSchemas::setPersonalPaid);
        BUILDERS.put("inspect_backup", OwnerToolSchemas::inspectBackup);
        BUILDERS.put("restore_backup", OwnerToolSchemas::restoreBackup);
        BUILDERS.put("request_invoice_issue", OwnerToolSchemas::requestInvoiceIssue);
        BUILDERS.put("prepare_invoice_share", OwnerToolSchemas::prepareInvoiceShare);
        BUILDERS.put("create_invoice_correction", OwnerToolSchemas::createInvoiceCorrection);
        BUILDERS.put("update_invoice_payment", OwnerToolSchemas::updateInvoicePayment);
    }
    static JSONObject schema(String name,boolean writable)throws Exception{
        if(LocalWorkflowTools.handles(name))return LocalWorkflowTools.schema(name);
        JSONObject p=new JSONObject();List<String> required=new ArrayList<>();
        if(writable){p.put("request_id",text(128).put("minLength",8));required.add("request_id");}
        Fields fields=BUILDERS.get(name);
        if(fields!=null)fields.build(name,p,required);
        if(p.has("expected_record_hash"))p.getJSONObject("expected_record_hash").put("description",Arrays.asList("delete_entity","archive_entity","update_invoice_payment").contains(name)?"entity_hash des zuvor gelesenen Datensatzes.":name.equals("restore_entity")?"record_hash des Eintrags aus list_archive.":"record_hash des zuvor gelesenen Datensatzes.");
        return object(p,required.toArray(new String[0]));
    }
    private static JSONObject photoFields(JSONObject p)throws Exception{return p.put("session_id",text(500)).put("capture_time",text(80)).put("order_id",text(500)).put("worklog_id",text(500)).put("caption",text(10000)).put("role",enumeration("profile","documentation","before","during","after","damage","other")).put("customer_visible",bool()).put("original_name",text(240)).put("property_id",id()).put("visit_id",id());}
    private static void getUiActionCatalog(String name, JSONObject p, List<String> required) throws Exception {
        p.put("expected_catalog_hash",hash()).put("offset",number(0,1000000)).put("limit",number(1,100));
    }
    private static void inspectOrderReview(String name, JSONObject p, List<String> required) throws Exception {
        p.put("order_id",id());required.add("order_id");
    }
    private static void saveReviewedOrder(String name, JSONObject p, List<String> required) throws Exception {
        p.put("id",id()).put("token",hash()).put("decision",enumeration("keep","save")).put("confirmed",bool().put("enum",new JSONArray().put(true)));JSONObject edits=new JSONObject();for(String k:new String[]{"title","customer_id","billing_customer_id","description","location","scheduled_date","status"})edits.put(k,text(10000));edits.put("customer_visible",number(0,1));p.put("order",object(edits));required.addAll(Arrays.asList("id","token","decision","confirmed"));
    }
    private static void prepareLegacyConflictBackup(String name, JSONObject p, List<String> required) throws Exception {
        p.put("expected_preview_hash",hash());
    }
    private static void previewArchiveWorkRecords(String name, JSONObject p, List<String> required) throws Exception {
        p.put("work_record_ids",new JSONObject().put("type","array").put("minItems",1).put("maxItems",WorklogArchiveBatch.MAX).put("items",id()));required.add("work_record_ids");
                if(name.equals("archive_work_records")){p.put("expected_preview_hash",hash()).put("confirmed",bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("expected_preview_hash","confirmed"));}
    }
    private static void emptyTrash(String name, JSONObject p, List<String> required) throws Exception {
        p.put("expected_preview_hash",hash()).put("confirmed",bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("expected_preview_hash","confirmed"));
    }
    private static void closeLegacyConflicts(String name, JSONObject p, List<String> required) throws Exception {
        p.put("backup_id",id()).put("expected_preview_hash",hash()).put("confirmed",bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("backup_id","expected_preview_hash","confirmed"));
    }
    private static void getPhoto(String name, JSONObject p, List<String> required) throws Exception {
        p.put("photo_id",id());required.add("photo_id");
    }
    private static void listArchive(String name, JSONObject p, List<String> required) throws Exception {
        p.put("limit",number(1,100)).put("offset",number(0,1000000)).put("entity",enumeration(DeletionRules.ENTITIES)).put("entity_id",id());
    }
    private static void getSyncConflicts(String name, JSONObject p, List<String> required) throws Exception {
        p.put("limit",number(1,100)).put("offset",number(0,1000000)).put("client_id",id()).put("include_history",bool());
    }
    private static void searchCatalog(String name, JSONObject p, List<String> required) throws Exception {
        p.put("catalog_type",enumeration("plant","service")).put("query",text(200)).put("offset",number(0,1000000)).put("limit",number(1,100));
    }
    private static void getCatalogEntry(String name, JSONObject p, List<String> required) throws Exception {
        p.put("entry_id",id());required.add("entry_id");
    }
    private static void getFileMetadata(String name, JSONObject p, List<String> required) throws Exception {
        p.put("resource_type",enumeration("photo","invoice","offer","catalog","backup")).put("resource_id",id()).put("offset",number(0,1024*1024*1024)).put("length",number(1,CHUNK)).put("expected_sha256",hash());required.addAll(Arrays.asList("resource_type","resource_id"));if(name.equals("read_file_chunk"))required.addAll(Arrays.asList("offset","expected_sha256"));
    }
    private static void beginFileUpload(String name, JSONObject p, List<String> required) throws Exception {
        p.put("byte_size",number(1,20*1024*1024)).put("sha256",hash()).put("mime_type",enumeration("image/jpeg","image/png","image/webp","image/gif","image/heic"));required.addAll(Arrays.asList("byte_size","sha256","mime_type"));
    }
    private static void writeFileChunk(String name, JSONObject p, List<String> required) throws Exception {
        p.put("upload_id",id()).put("offset",number(0,20*1024*1024)).put("chunk_base64",text(CHUNK/3*4).put("minLength",4)).put("chunk_sha256",hash());required.addAll(Arrays.asList("upload_id","offset","chunk_base64","chunk_sha256"));
    }
    private static void createPhoto(String name, JSONObject p, List<String> required) throws Exception {
        p.put("upload_id",id()).put("customer_id",id());required.addAll(Arrays.asList("upload_id","customer_id"));photoFields(p);
    }
    private static void setWorkRecordBilled(String name, JSONObject p, List<String> required) throws Exception {
        {
                p.put("work_record_id",id()).put("expected_record_hash",hash()).put("reason",text(2000).put("minLength",1));required.addAll(Arrays.asList("work_record_id","expected_record_hash","reason"));
                if(name.equals("set_work_record_billed")){p.put("billed",bool());required.add("billed");}else{p.put("customer_id",id()).put("billing_customer_id",id()).put("order_id",text(500));required.addAll(Arrays.asList("customer_id","billing_customer_id"));}
            }
    }
    private static void updatePhoto(String name, JSONObject p, List<String> required) throws Exception {
        p.put("photo_id",id()).put("expected_record_hash",hash()).put("updates",object(photoFields(new JSONObject())) .put("minProperties",1));required.addAll(Arrays.asList("photo_id","expected_record_hash","updates"));
    }
    private static void previewPurge(String name, JSONObject p, List<String> required) throws Exception {
        p.put("entity",enumeration(DeletionRules.ENTITIES)).put("entity_id",id());required.addAll(Arrays.asList("entity","entity_id"));
    }
    private static void purgeEntity(String name, JSONObject p, List<String> required) throws Exception {
        if(name.equals("purge_entity")){p.put("confirmed",new JSONObject().put("type","boolean").put("enum",new JSONArray().put(true)));required.add("confirmed");}p.put("entity",enumeration(DeletionRules.ENTITIES)).put("entity_id",id()).put("expected_record_hash",hash());required.addAll(Arrays.asList("entity","entity_id","expected_record_hash"));if(name.equals("delete_entity")||name.equals("archive_entity"))p.put("expected_photo_links_hash",hash());
    }
    private static void resolveSyncConflict(String name, JSONObject p, List<String> required) throws Exception {
        p.put("client_id",id()).put("expected_action_hash",hash()).put("expected_current_hash",hash()).put("resolution",enumeration("keep_current","apply_proposed"));required.addAll(Arrays.asList("client_id","expected_action_hash","expected_current_hash","resolution"));
    }
    private static void updateBusinessSettings(String name, JSONObject p, List<String> required) throws Exception {
        {
                JSONObject fields=new JSONObject();for(String key:BUSINESS_KEYS)fields.put(key,key.endsWith("_confirmed")?bool():(key.equals("invoice_sevdesk_last_number")||key.equals("invoice_numbering_last_number"))?number(0,1000000000):key.equals("invoice_number_format")?enumeration("own","sevdesk"):text(2000));
                fields.put("invoice_numbering_authority",enumeration("","pixel_drive").put("minLength",0)).put("invoice_numbering_device_id",text(200)).put("tax_mode",enumeration("small_business","standard")).put("default_payment_days",number(0,365));
                fields.put("invoice_prefix",text(20).put("pattern","[A-Za-z][A-Za-z0-9-]{0,19}"));
                p.put("expected_record_hash",hash()).put("updates",object(fields).put("minProperties",1));required.addAll(Arrays.asList("expected_record_hash","updates"));
            }
    }
    private static void updateSettings(String name, JSONObject p, List<String> required) throws Exception {
        p.put("expected_record_hash",hash()).put("updates",object(new JSONObject().put("default_activity",text(500)).put("default_location",text(500)).put("photo_quality",enumeration("optimized","high","original"))).put("minProperties",1));required.addAll(Arrays.asList("expected_record_hash","updates"));
    }
    private static void setPersonalPaid(String name, JSONObject p, List<String> required) throws Exception {
        p.put("work_record_id",id()).put("paid",bool());required.addAll(Arrays.asList("work_record_id","paid"));
    }
    private static void inspectBackup(String name, JSONObject p, List<String> required) throws Exception {
        p.put("backup_id",id());required.add("backup_id");
    }
    private static void restoreBackup(String name, JSONObject p, List<String> required) throws Exception {
        p.put("backup_id",id()).put("expected_state_hash",hash());required.addAll(Arrays.asList("backup_id","expected_state_hash"));
    }
    private static void requestInvoiceIssue(String name, JSONObject p, List<String> required) throws Exception {
        p.put("invoice_id",id()).put("expected_record_hash",hash()).put("expected_context_hash",hash()).put("confirmed",bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("invoice_id","expected_record_hash","confirmed"));
    }
    private static void prepareInvoiceShare(String name, JSONObject p, List<String> required) throws Exception {
        p.put("invoice_id",id()).put("channel",enumeration("share","email","whatsapp"));required.addAll(Arrays.asList("invoice_id","channel"));
    }
    private static void createInvoiceCorrection(String name, JSONObject p, List<String> required) throws Exception {
        p.put("invoice_id",id()).put("expected_record_hash",hash()).put("reason",text(2000).put("minLength",1)).put("correction_type",enumeration("replacement","cancellation"));required.addAll(Arrays.asList("invoice_id","expected_record_hash","reason","correction_type"));
    }
    private static void updateInvoicePayment(String name, JSONObject p, List<String> required) throws Exception {
        p.put("invoice_id",id()).put("expected_record_hash",hash()).put("paid",bool());required.addAll(Arrays.asList("invoice_id","expected_record_hash","paid"));
    }
}
