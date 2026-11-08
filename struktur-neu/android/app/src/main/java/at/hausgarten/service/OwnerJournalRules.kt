package at.hausgarten.service

import java.util.*
import org.json.*

/** Additive owner journal protocol. Old journals and immutable receipts survive. */
class OwnerJournalRules {
    companion object {
        @JvmField
        val KINDS: Array<String> =
            arrayOf("purge_v1", "purge_trash_v1", "archive", "restore", "business_settings")

        @JvmStatic
        fun handles(kind: String): Boolean {
            return (Arrays.asList(*KINDS).contains(kind) || OfferJournalRules.handles(kind))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun archives(snapshot: JSONObject): JSONObject {
            var a: JSONObject? = snapshot.optJSONObject("owner_archive")
            if (((a == null))) {
                a = JSONObject()
                snapshot.put("owner_archive", a)
            }
            return a
        }

        @JvmStatic
        fun group(entity: String): String {
            return (if (entity.equals("invoice")) "invoices"
            else LocalJournalRules.collection(entity))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun findOrNull(rows: JSONArray?, id: String): JSONObject? {
            run {
                var i: Int = 0
                while (((rows != null) && (i < rows!!.length()))) {
                    if ((id.equals(rows!!.getJSONObject(i).optString("id")))) {
                        return rows!!.getJSONObject(i)
                    }
                    i++
                }
            }
            return null
        }

        @Throws(Exception::class)
        @JvmStatic
        fun version(row: JSONObject?): String {
            if (((row == null))) {
                return PixelMcpWriteTools.hash(JSONObject.NULL)
            }
            if ((row!!.has("starts_at"))) {
                return AppointmentRules.hash(row)
            }
            if ((row!!.has("id"))) {
                if (
                    (((row!!.has("offer_number") || row!!.has("pricing_mode")) ||
                        ((row!!.has("unit") && row!!.has("default_price")))))
                ) {
                    return PixelMcpWriteTools.hash(LocalJournalRules.clean(row))
                }
                var kind: String =
                    (if (row!!.has("name")) "customer"
                    else
                        (if (row!!.has("activity")) "worklog"
                        else
                            (if (row!!.has("title")) "order"
                            else
                                (if (
                                    ((row!!.has("invoice_number") || row!!.has("positions")) ||
                                        row!!.has("issued_at"))
                                )
                                    "invoice"
                                else "photo"))))
                return PixelMcpWriteTools.hash(LocalJournalRules.guard(row, kind))
            }
            return PixelMcpWriteTools.hash(LocalJournalRules.clean(row))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun checkHash(row: JSONObject?, expected: String) {
            if ((!version(row).equals(expected))) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Der geprüfte Stand hat sich verändert. Beide Varianten bleiben erhalten.",
                )
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun apply(
            snapshot: JSONObject,
            action: JSONObject,
            all: MutableMap<String, JSONObject>,
            resolved: JSONObject,
        ) {
            val kind: String = action.getString("kind")
            var p: JSONObject = action.getJSONObject("payload")
            if ((kind.equals(TrashPurgeBatch.KIND))) {
                TrashPurgeBatch.apply(snapshot, action)
                return
            }
            if ((kind.equals("purge_v1"))) {
                TrashRules.purge(snapshot, action)
                return
            }
            if ((OfferJournalRules.handles(kind))) {
                OfferJournalRules.apply(snapshot, action)
                return
            }
            if ((kind.equals("business_settings"))) {
                var current: JSONObject? = snapshot.optJSONObject("business_settings")
                if (((current == null))) {
                    throw IllegalArgumentException(
                        "Fachliche Einstellungen fehlen; Pixel-Datenstand erneut prüfen."
                    )
                }
                if (
                    (!PixelMcpWriteTools.hash(current).equals(p.getString("expected_record_hash")))
                ) {
                    throw PixelMcpWriteTools.Rejected(
                        "WRITE_CONFLICT",
                        "Fachliche Einstellungen wurden geändert.",
                    )
                }
                PixelMcpOwnerTools.validate(
                    "update_business_settings",
                    JSONObject()
                        .put("request_id", "journal-settings")
                        .put("expected_record_hash", p.getString("expected_record_hash"))
                        .put("updates", p.getJSONObject("updates")),
                )
                val updated: JSONObject = JSONObject(current!!.toString())
                val changes: JSONObject = p.getJSONObject("updates")
                run {
                    val keys: MutableIterator<String> = changes.keys()
                    while (keys.hasNext()) {
                        val key: String = keys.next()
                        var value: Any = changes.get(key)
                        updated.put(
                            key,
                            (if (value is Boolean) ((if (value) "1" else "0"))
                            else value.toString()),
                        )
                    }
                }
                snapshot.put("business_settings", updated)
                var issuer: JSONObject? = snapshot.optJSONObject("invoice_issuer")
                if (((issuer == null))) {
                    issuer = JSONObject()
                }
                for (key in PixelMcpOwnerTools.BUSINESS_KEYS) {
                    if ((!key.startsWith("invoice_"))) {
                        issuer!!.put(key, updated.optString(key))
                    }
                }
                snapshot.put("invoice_issuer", issuer)
                return
            }
            val entity: String = p.getString("entity")
            var id: String = p.getString("id")
            var collection: String = group(entity)
            if ((collection.isEmpty())) {
                throw IllegalArgumentException("Unbekannter Archivbereich.")
            }
            val rows: JSONArray = snapshot.getJSONArray(collection)
            var current: JSONObject? = findOrNull(rows, id)
            val archive: JSONObject = archives(snapshot)
            val key: String = (("" + entity + ":") + id)
            if ((kind.equals("restore"))) {
                val saved: JSONObject? = archive.optJSONObject(key)
                if (((saved == null))) {
                    throw IllegalArgumentException("Archiveintrag fehlt.")
                }
                checkHash(saved, p.getString("expected_record_hash"))
                if ((saved!!.optBoolean("purged"))) {
                    throw IllegalArgumentException(
                        "Endgültig gelöschte Einträge sind nicht wiederherstellbar."
                    )
                }
                var original: JSONObject = saved!!.getJSONObject("record")
                if (((entity.equals("customer") || entity.equals("service_template")))) {
                    if (
                        (((current == null) ||
                            ((current!!.optBoolean("active", true) &&
                                (current!!.optInt("active", 1) != 0)))))
                    ) {
                        throw IllegalArgumentException(
                            "Der archivierte Kunde wurde inzwischen geändert."
                        )
                    }
                    checkHash(current, saved!!.getString("archived_record_hash"))
                } else {
                    if (((current != null))) {
                        throw IllegalArgumentException("Die Original-ID ist bereits belegt.")
                    }
                }
                if ((!entity.equals("invoice"))) {
                    LocalJournalRules.validateChange(entity, original, null, snapshot)
                }
                replace(snapshot, collection, id, original)
                TrashRules.restoreLinks(snapshot, saved)
                archive.remove(key)
                return
            }
            checkHash(current, p.getString("expected_record_hash"))
            if (((current == null))) {
                throw IllegalArgumentException("Der Eintrag fehlt.")
            }
            validateArchive(entity, current, snapshot)
            if ((archive.has(key))) {
                throw IllegalArgumentException("Für diese ID besteht bereits ein Archivstand.")
            }
            var saved: JSONObject? =
                JSONObject()
                    .put("record", current)
                    .put("archived_at", action.optString("created_at"))
                    .put("archive_action", action.getString("client_id"))
            if ((p.has("deletion_policy"))) {
                saved!!.put("deletion_policy", p.getString("deletion_policy"))
            }
            val related: JSONArray = TrashRules.related(snapshot, entity, id)
            if (
                (("recoverable_v2".equals(p.optString("deletion_policy")) &&
                    !PixelMcpWriteTools.hash(related)
                        .equals(p.getString("expected_photo_links_hash"))))
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Fotozuordnungen inzwischen geändert.",
                )
            }
            if (
                (((related.length() > 0) &&
                    (((entity.equals("order") || entity.equals("worklog")) ||
                        "recoverable_v2".equals(p.optString("deletion_policy"))))))
            ) {
                if ((!"recoverable_v2".equals(p.optString("deletion_policy")))) {
                    throw IllegalArgumentException(
                        "Fotozuordnungen mit aktuellem Löschdialog erneut prüfen; alte Aktion bleibt erhalten."
                    )
                }
                if (
                    (!PixelMcpWriteTools.hash(related)
                        .equals(p.getString("expected_photo_links_hash")))
                ) {
                    throw PixelMcpWriteTools.Rejected(
                        "WRITE_CONFLICT",
                        "Fotozuordnungen inzwischen geändert. Nichts entfernt.",
                    )
                }
                saved!!.put(
                    "photo_links",
                    TrashRules.detachRelated(snapshot, entity, id, p.getString("updated_at")),
                )
            }
            if (((entity.equals("customer") || entity.equals("service_template")))) {
                var inactive: JSONObject =
                    JSONObject(current!!.toString())
                        .put("active", (if (entity.equals("customer")) 0 else false))
                        .put("updated_at", p.getString("updated_at"))
                replace(snapshot, collection, id, inactive)
                saved!!.put("archived_record_hash", version(inactive))
            } else {
                replace(snapshot, collection, id, null)
            }
            TrashRules.detachOptionalParent(snapshot, entity, id, p.getString("updated_at"))
            archive.put(key, saved)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun validateArchive(entity: String, row: JSONObject, snapshot: JSONObject) {
            if (entity == "order" || entity == "appointment") {
                TrashRules.requireDeletableParent(snapshot, entity, row.getString("id"))
                return
            }
            if ((entity.equals("customer"))) {
                for (group in arrayOf("customers", "orders", "worklogs")) {
                    val records: JSONArray? = snapshot.optJSONArray(group)
                    run {
                        var i: Int = 0
                        while (((records != null) && (i < records!!.length()))) {
                            val r: JSONObject = records!!.getJSONObject(i)
                            var key: String =
                                (if (group.equals("customers")) "default_billing_customer_id"
                                else "billing_customer_id")
                            if (
                                ((row.getString("id").equals(r.optString(key)) &&
                                    !row.getString("id")
                                        .equals(
                                            r.optString(
                                                (if (group.equals("customers")) "id"
                                                else "customer_id")
                                            )
                                        )))
                            ) {
                                throw IllegalArgumentException(
                                    "Dieser Kunde wird als Rechnungsempfänger verwendet. Zuerst die Abrechnungszuordnungen prüfen."
                                )
                            }
                            i++
                        }
                    }
                }
                return
            }
            if (((entity.equals("offer") && !OfferRules.editable(row)))) {
                throw IllegalArgumentException(
                    "Finalisierte oder angenommene Angebote bleiben als Beleg erhalten."
                )
            }
            if ((entity.equals("service_template"))) {
                return
            }
            var id: String = row.getString("id")
            if ((entity.equals("invoice"))) {
                EntityProtectionRules.validateInvoiceDelete(row)
            }
            for (collection in arrayOf("orders", "worklogs", "photos", "invoices", "offers")) {
                val rows: JSONArray = snapshot.getJSONArray(collection)
                run {
                    var i: Int = 0
                    while ((i < rows.length())) {
                        val other: JSONObject = rows.getJSONObject(i)
                        if (
                            ((!collection.equals("photos") &&
                                (((entity.equals("order") &&
                                    id.equals(other.optString("order_id"))) ||
                                    (entity.equals("worklog") &&
                                        id.equals(other.optString("worklog_id")))))))
                        ) {
                            throw IllegalArgumentException(
                                "Verknüpfte Daten zuerst prüfen; nichts entfernt."
                            )
                        }
                        if (((entity.equals("worklog") && collection.equals("invoices")))) {
                            val refs: JSONArray? = other.optJSONArray("worklog_ids")
                            val positions: JSONArray? = other.optJSONArray("positions")
                            run {
                                var j: Int = 0
                                while (((refs != null) && (j < refs!!.length()))) {
                                    if ((id.equals(refs!!.optString(j)))) {
                                        throw IllegalArgumentException(
                                            "Die Arbeit ist mit einer Rechnung verknüpft."
                                        )
                                    }
                                    j++
                                }
                            }
                            run {
                                var j: Int = 0
                                while (((positions != null) && (j < positions!!.length()))) {
                                    if (
                                        (id.equals(
                                            positions!!.getJSONObject(j).optString("worklog_id")
                                        ))
                                    ) {
                                        throw IllegalArgumentException(
                                            "Die Arbeit ist mit einer Rechnung verknüpft."
                                        )
                                    }
                                    j++
                                }
                            }
                        }
                        i++
                    }
                }
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun replace(
            snapshot: JSONObject,
            collection: String,
            id: String,
            replacement: JSONObject?,
        ) {
            val rows: JSONArray = snapshot.getJSONArray(collection)
            val next: JSONArray = JSONArray()
            var found: Boolean = false
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    val old: JSONObject = rows.getJSONObject(i)
                    if ((id.equals(old.optString("id")))) {
                        found = true
                        if (((replacement != null))) {
                            next.put(replacement)
                        }
                    } else {
                        next.put(old)
                    }
                    i++
                }
            }
            if (((!found && (replacement != null)))) {
                next.put(replacement)
            }
            snapshot.put(collection, next)
        }
    }
}
