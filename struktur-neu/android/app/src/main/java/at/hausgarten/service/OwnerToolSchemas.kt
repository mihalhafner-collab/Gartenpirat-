package at.hausgarten.service

import java.util.*
import org.json.*

internal class OwnerToolSchemas {
    fun interface Fields {
        @Throws(Exception::class)
        fun build(name: String, p: JSONObject, required: MutableList<String>): Unit
    }

    companion object {
        private val BUILDERS: MutableMap<String, Fields> = HashMap()

        init {
            BUILDERS.put("get_accounting_overview", OwnerToolSchemas::accounting)
            BUILDERS.put("get_accounting_record", OwnerToolSchemas::accounting)
            BUILDERS.put("set_accounting_record", OwnerToolSchemas::accounting)
            BUILDERS.put("get_ui_action_catalog", OwnerToolSchemas::getUiActionCatalog)
            BUILDERS.put(
                "preview_archive_work_records",
                OwnerToolSchemas::previewArchiveWorkRecords,
            )
            BUILDERS.put("archive_work_records", OwnerToolSchemas::previewArchiveWorkRecords)
            BUILDERS.put("delete_work_records", OwnerToolSchemas::previewArchiveWorkRecords)
            BUILDERS.put("empty_trash", OwnerToolSchemas::emptyTrash)
            BUILDERS.put("get_photo", OwnerToolSchemas::getPhoto)
            BUILDERS.put("list_archive", OwnerToolSchemas::listArchive)
            BUILDERS.put("search_catalog", OwnerToolSchemas::searchCatalog)
            BUILDERS.put("get_catalog_entry", OwnerToolSchemas::getCatalogEntry)
            BUILDERS.put("get_file_metadata", OwnerToolSchemas::getFileMetadata)
            BUILDERS.put("read_file_chunk", OwnerToolSchemas::getFileMetadata)
            BUILDERS.put("begin_file_upload", OwnerToolSchemas::beginFileUpload)
            BUILDERS.put("write_file_chunk", OwnerToolSchemas::writeFileChunk)
            BUILDERS.put("create_photo", OwnerToolSchemas::createPhoto)
            BUILDERS.put("set_work_record_billed", OwnerToolSchemas::setWorkRecordBilled)
            BUILDERS.put("reassign_work_record", OwnerToolSchemas::setWorkRecordBilled)
            BUILDERS.put("update_photo", OwnerToolSchemas::updatePhoto)
            BUILDERS.put("preview_purge", OwnerToolSchemas::previewPurge)
            BUILDERS.put("preview_delete", OwnerToolSchemas::previewPurge)
            BUILDERS.put("purge_entity", OwnerToolSchemas::purgeEntity)
            BUILDERS.put("delete_entity", OwnerToolSchemas::purgeEntity)
            BUILDERS.put("archive_entity", OwnerToolSchemas::purgeEntity)
            BUILDERS.put("restore_entity", OwnerToolSchemas::purgeEntity)
            BUILDERS.put("update_business_settings", OwnerToolSchemas::updateBusinessSettings)
            BUILDERS.put("update_settings", OwnerToolSchemas::updateSettings)
            BUILDERS.put("set_personal_paid", OwnerToolSchemas::setPersonalPaid)
        }

        private fun accounting(name: String, p: JSONObject, required: MutableList<String>) {
            if (name == "get_accounting_overview") {
                p.put("query", McpFields.text(500))
                    .put("date_from", McpFields.text(10))
                    .put("date_to", McpFields.text(10))
                    .put(
                        "review_offset",
                        JSONObject()
                            .put("type", "integer")
                            .put("minimum", 0)
                            .put("maximum", 1000000),
                    )
                    .put("property_id", McpFields.id())
                    .put(
                        "route",
                        McpFields.text(20)
                            .put("enum", JSONArray().put("").put("billed").put("labor")),
                    )
                    .put(
                        "offset",
                        JSONObject()
                            .put("type", "integer")
                            .put("minimum", 0)
                            .put("maximum", 1000000),
                    )
                    .put(
                        "limit",
                        JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 100),
                    )
                return
            }
            p.put(
                    "collection",
                    McpFields.text(20).put("enum", JSONArray().put("orders").put("worklogs")),
                )
                .put("entity_id", McpFields.id())
            required.addAll(listOf("collection", "entity_id"))
            if (name == "set_accounting_record") {
                p.put("expected_record_hash", McpFields.hash())
                    .put(
                        "route",
                        McpFields.text(20)
                            .put("enum", JSONArray().put("open").put("billed").put("labor")),
                    )
                required.addAll(listOf("expected_record_hash", "route"))
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String, writable: Boolean): JSONObject {

            if (LocalWorkflowTools.handles(name)) {
                return LocalWorkflowTools.schema(name)
            }
            var p = JSONObject()
            val required = ArrayList<String>()
            if (writable) {
                p.put("request_id", McpFields.text(128).put("minLength", 8))
                required.add("request_id")
            }
            val fields = BUILDERS.get(name)
            if (fields != null) {
                fields.build(name, p, required)
            }
            if (p.has("expected_record_hash")) {
                p.getJSONObject("expected_record_hash")
                    .put(
                        "description",
                        (if (Arrays.asList("delete_entity", "archive_entity").contains(name))
                            "entity_hash des zuvor gelesenen Datensatzes."
                        else
                            (if (name == "restore_entity")
                                "record_hash des Eintrags aus list_archive."
                            else "record_hash des zuvor gelesenen Datensatzes.")),
                    )
            }
            return McpFields.`object`(p, *required.toTypedArray())
        }

        private @JvmStatic @Throws(Exception::class) fun photoFields(p: JSONObject): JSONObject {

            return p.put("session_id", McpFields.text(500))
                .put("capture_time", McpFields.text(80))
                .put("order_id", McpFields.text(500))
                .put("worklog_id", McpFields.text(500))
                .put("caption", McpFields.text(10000))
                .put(
                    "role",
                    McpFields.enumeration(
                        "profile",
                        "documentation",
                        "before",
                        "during",
                        "after",
                        "damage",
                        "other",
                    ),
                )
                .put("customer_visible", McpFields.bool())
                .put("original_name", McpFields.text(240))
                .put("property_id", McpFields.id())
                .put("visit_id", McpFields.id())
        }

        private @JvmStatic @Throws(Exception::class) fun getUiActionCatalog(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("expected_catalog_hash", McpFields.hash())
                .put("offset", McpFields.number(0, 1000000))
                .put("limit", McpFields.number(1, 100))
        }

        private @JvmStatic @Throws(Exception::class) fun previewArchiveWorkRecords(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put(
                "work_record_ids",
                JSONObject()
                    .put("type", "array")
                    .put("minItems", 1)
                    .put("maxItems", WorklogArchiveBatch.MAX)
                    .put("items", McpFields.id()),
            )
            required.add("work_record_ids")
            if (name == "archive_work_records" || name == "delete_work_records") {
                p.put("expected_preview_hash", McpFields.hash())
                    .put("confirmed", McpFields.bool().put("enum", JSONArray().put(true)))
                required.addAll(Arrays.asList("expected_preview_hash", "confirmed"))
            }
        }

        private @JvmStatic @Throws(Exception::class) fun emptyTrash(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("expected_preview_hash", McpFields.hash())
                .put("confirmed", McpFields.bool().put("enum", JSONArray().put(true)))
            required.add("confirmed")
        }

        private @JvmStatic @Throws(Exception::class) fun getPhoto(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("photo_id", McpFields.id())
            required.add("photo_id")
        }

        private @JvmStatic @Throws(Exception::class) fun listArchive(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("limit", McpFields.number(1, 100))
                .put("offset", McpFields.number(0, 1000000))
                .put("entity", McpFields.enumeration(*DeletionRules.ENTITIES))
                .put("entity_id", McpFields.id())
        }

        private @JvmStatic @Throws(Exception::class) fun searchCatalog(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("query", McpFields.text(200))
                .put("offset", McpFields.number(0, 1000000))
                .put("limit", McpFields.number(1, 100))
        }

        private @JvmStatic @Throws(Exception::class) fun getCatalogEntry(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("entry_id", McpFields.id())
            required.add("entry_id")
        }

        private @JvmStatic @Throws(Exception::class) fun getFileMetadata(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("resource_type", McpFields.enumeration("photo", "offer"))
                .put("resource_id", McpFields.id())
                .put("offset", McpFields.number(0, ((1024 * 1024) * 1024)))
                .put("length", McpFields.number(1, McpFields.CHUNK))
                .put("expected_sha256", McpFields.hash())
            required.addAll(Arrays.asList("resource_type", "resource_id"))
            if (name == "read_file_chunk") {
                required.addAll(Arrays.asList("offset", "expected_sha256"))
            }
        }

        private @JvmStatic @Throws(Exception::class) fun beginFileUpload(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("byte_size", McpFields.number(1, ((20 * 1024) * 1024)))
                .put("sha256", McpFields.hash())
                .put(
                    "mime_type",
                    McpFields.enumeration(
                        "image/jpeg",
                        "image/png",
                        "image/webp",
                        "image/gif",
                        "image/heic",
                    ),
                )
            required.addAll(Arrays.asList("byte_size", "sha256", "mime_type"))
        }

        private @JvmStatic @Throws(Exception::class) fun writeFileChunk(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("upload_id", McpFields.id())
                .put("offset", McpFields.number(0, ((20 * 1024) * 1024)))
                .put("chunk_base64", McpFields.text((McpFields.CHUNK / 3) * 4).put("minLength", 4))
                .put("chunk_sha256", McpFields.hash())
            required.addAll(Arrays.asList("upload_id", "offset", "chunk_base64", "chunk_sha256"))
        }

        private @JvmStatic @Throws(Exception::class) fun createPhoto(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("upload_id", McpFields.id()).put("customer_id", McpFields.id())
            required.addAll(Arrays.asList("upload_id", "customer_id"))
            photoFields(p)
        }

        private @JvmStatic @Throws(Exception::class) fun setWorkRecordBilled(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            run {
                p.put("work_record_id", McpFields.id())
                    .put("expected_record_hash", McpFields.hash())
                    .put("reason", McpFields.text(2000).put("minLength", 1))
                required.addAll(Arrays.asList("work_record_id", "expected_record_hash", "reason"))
                if (name == "set_work_record_billed") {
                    p.put("billed", McpFields.bool())
                    required.add("billed")
                } else {
                    p.put("customer_id", McpFields.id())
                        .put("billing_customer_id", McpFields.id())
                        .put("order_id", McpFields.text(500))
                    required.addAll(Arrays.asList("customer_id", "billing_customer_id"))
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun updatePhoto(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("photo_id", McpFields.id())
                .put("expected_record_hash", McpFields.hash())
                .put(
                    "updates",
                    McpFields.`object`(photoFields(JSONObject())).put("minProperties", 1),
                )
            required.addAll(Arrays.asList("photo_id", "expected_record_hash", "updates"))
        }

        private @JvmStatic @Throws(Exception::class) fun previewPurge(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("entity", McpFields.enumeration(*DeletionRules.ENTITIES))
                .put("entity_id", McpFields.id())
            required.addAll(Arrays.asList("entity", "entity_id"))
        }

        private @JvmStatic @Throws(Exception::class) fun purgeEntity(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            if (name == "purge_entity") {
                p.put(
                    "confirmed",
                    JSONObject().put("type", "boolean").put("enum", JSONArray().put(true)),
                )
                required.add("confirmed")
            }
            p.put("entity", McpFields.enumeration(*DeletionRules.ENTITIES))
                .put("entity_id", McpFields.id())
                .put("expected_record_hash", McpFields.hash())
            required.addAll(Arrays.asList("entity", "entity_id"))
            if (name == "restore_entity") required.add("expected_record_hash")
            if ((name == "delete_entity") || (name == "archive_entity")) {
                p.put("expected_photo_links_hash", McpFields.hash())
            }
        }

        private @JvmStatic @Throws(Exception::class) fun updateBusinessSettings(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            run {
                val fields = JSONObject()
                for (key in McpFields.BUSINESS_KEYS) {
                    fields.put(
                        key,
                        if (key.endsWith("_confirmed")) McpFields.bool() else McpFields.text(2000),
                    )
                }
                fields.put("tax_mode", McpFields.enumeration("small_business", "standard"))
                p.put("expected_record_hash", McpFields.hash())
                    .put("updates", McpFields.`object`(fields).put("minProperties", 1))
                required.addAll(Arrays.asList("expected_record_hash", "updates"))
            }
        }

        private @JvmStatic @Throws(Exception::class) fun updateSettings(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("expected_record_hash", McpFields.hash())
                .put(
                    "updates",
                    McpFields.`object`(
                            JSONObject()
                                .put("default_activity", McpFields.text(500))
                                .put("default_location", McpFields.text(500))
                                .put(
                                    "photo_quality",
                                    McpFields.enumeration("optimized", "high", "original"),
                                )
                        )
                        .put("minProperties", 1),
                )
            required.addAll(Arrays.asList("expected_record_hash", "updates"))
        }

        private @JvmStatic @Throws(Exception::class) fun setPersonalPaid(
            name: String,
            p: JSONObject,
            required: MutableList<String>,
        ): Unit {

            p.put("work_record_id", McpFields.id()).put("paid", McpFields.bool())
            required.addAll(Arrays.asList("work_record_id", "paid"))
        }
    }
}
