package at.hausgarten.service

import org.json.*

internal class LocalBusinessState {
    companion object {
        @JvmField val CONTRACT: Int = 1
        @JvmField val ENGINE: String = "pixel_local_v2"
        @JvmField
        val CAPABILITIES: Array<String> =
            arrayOf(
                "offers_v1",
                "service_catalog_v1",
                "owner_journal_v2",
                "photo_compare_and_save",
                "durable_action_receipts",
                "worklog_compare_and_save",
                "entity_compare_and_save",
                "customer_custom_fields_compare_and_save",
                "billing_relations",
                "photo_attachments_v1",
                "recoverable_delete_v1",
                "trash_purge_v1",
                "trash_empty_v1",
                "appointments_v1",
                "offer_workflow_v2",
            )

        @JvmStatic
        @Throws(Exception::class)
        fun prepare(snapshot: JSONObject): Unit {
            LocalJournalRules.validateSnapshot(snapshot)
            if (!(snapshot.has("business_settings"))) {
                val known = JSONObject()
                val issuer = snapshot.optJSONObject("invoice_issuer")
                for (key in PixelMcpOwnerTools.BUSINESS_KEYS) {
                    if ((issuer != null) && issuer.has(key)) {
                        known.put(key, issuer.get(key))
                    }
                }
                snapshot
                    .put("business_settings", known)
                    .put("business_settings_origin", "legacy_known_fields_only")
            }
            if (snapshot.optJSONObject("business_settings") == null) {
                throw IllegalArgumentException(
                    "Ungültige Geschäftseinstellungen; keine Vorgaben eingesetzt."
                )
            }
            val caps = JSONObject()
            for (key in CAPABILITIES) {
                caps.put(key, true)
            }
            snapshot
                .put("capabilities", caps)
                .put("processing_engine", ENGINE)
                .put("schema_version", Math.max(2, snapshot.optInt("schema_version", 1)))
        }
    }
}
