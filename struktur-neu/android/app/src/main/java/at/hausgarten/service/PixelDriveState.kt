package at.hausgarten.service

import org.json.*

internal class PixelDriveState {
    companion object {
        @JvmField val ENGINE: String = "pixel_drive_v1"
        @JvmField
        val CAPABILITIES: Array<String> =
            arrayOf(
                "offers_v1",
                "service_catalog_v1",
                "owner_journal_v2",
                "invoice_compare_and_save",
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

            DriveJournalRules.validateSnapshot(snapshot)
            if (!(snapshot.has("business_settings"))) {
                var known = JSONObject()
                var issuer = snapshot.optJSONObject("invoice_issuer")
                for (key in PixelMcpOwnerTools.BUSINESS_KEYS) {
                    if (((issuer != null) && issuer.has(key))) {
                        known.put(key, issuer.get(key))
                    }
                }
                snapshot
                    .put("business_settings", known)
                    .put("business_settings_origin", "legacy_known_fields_only")
            }
            if ((snapshot.optJSONObject("business_settings") == null)) {
                throw IllegalArgumentException(
                    "Ungültige Rechnungseinstellungen; keine Vorgaben eingesetzt."
                )
            }
            var caps = snapshot.optJSONObject("capabilities")
            if ((caps == null)) {
                caps = JSONObject()
            }
            for (key in CAPABILITIES) {
                caps.put(key, true)
            }
            snapshot
                .put("capabilities", caps)
                .put("processing_engine", ENGINE)
                .put("schema_version", Math.max(2, snapshot.optInt("schema_version", 1)))
        }

        @JvmStatic
        fun retryableInfrastructure(failure: JSONObject?, kind: String): Boolean {

            return (((failure != null) &&
                ("SYNC_COMPONENT_UPDATE_REQUIRED" == failure.optString("code"))) &&
                ((java.util.Arrays.asList("offer", "service_template").contains(kind) ||
                    OfferJournalRules.handles(kind))))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun integrity(stored: JSONObject): JSONObject {

            var counts = JSONObject()
            var hashes = JSONObject()
            for (group in
                arrayOf(
                    "customers",
                    "orders",
                    "worklogs",
                    "invoices",
                    "photos",
                    "service_catalog",
                    "offers",
                    "appointments",
                )) {
                var rows = stored.optJSONArray(group)
                var sorted = java.util.TreeMap<String, JSONObject>()
                run {
                    var i: Int = 0
                    var firstIteration74 = true
                    while (true) {
                        if (firstIteration74) firstIteration74 = false
                        else {
                            i++
                        }
                        if (!(((rows != null) && (i < rows.length())))) break

                        var row = rows.getJSONObject(i)
                        sorted.put(row.getString("id"), DriveJournalRules.clean(row))
                    }
                }
                counts.put(group, sorted.size)
                hashes.put(group, PixelMcpWriteTools.hash(JSONArray(sorted.values)))
            }
            return JSONObject()
                .put("scope", "stored_business_collections_without_pending_overlay")
                .put("counts", counts)
                .put("collection_hashes", hashes)
                .put("business_state_hash", PixelMcpWriteTools.hash(hashes))
        }
    }
}
