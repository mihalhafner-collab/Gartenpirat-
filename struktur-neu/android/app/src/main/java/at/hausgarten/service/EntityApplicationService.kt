package at.hausgarten.service

import org.json.*
import java.util.*

/** Validated local customer and order commands. */
class EntityApplicationService {
    companion object {
        @Throws(Exception::class)
        @JvmStatic fun prepare(name: String, args: JSONObject, state: JSONObject, actionId: String): JSONObject {
            var master: JSONObject = SnapshotProjection.effective(state)
            var update: Boolean = name.startsWith("update_")
            val entity = when (name) {
                "create_customer", "update_customer" -> "customer"
                "create_order", "update_order" -> "order"
                else -> throw IllegalArgumentException("Unbekannter Geschäftsvorgang.")
            }
            val collection = if (entity == "customer") "customers" else "orders"
            val fields = if (entity == "customer") EntityContract.CUSTOMER_FIELDS else EntityContract.ORDER_FIELDS
            var payload: JSONObject
            if ((update)) {
                var capabilities: JSONObject? = master.optJSONObject("capabilities")
                if ((((capabilities == null) || !true.equals(capabilities!!.opt("entity_compare_and_save"))))) {
                    throw PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED", "Bitte Pixel-Datenstand und App-Version prüfen. Der Konfliktschutz ist noch nicht bestätigt.")
                }
                var idKey: String = ("" + entity + "_id")
                var id: String = args.getString(idKey)
                var old: JSONObject = PixelMcpWriteTools.find(master.optJSONArray(collection), id)
                if ((old.optString("updated_at").isEmpty())) {
                    throw PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED", "Der Änderungsstand fehlt. Bitte den lokalen Eintrag erneut öffnen.")
                }
                if ((!PixelMcpWriteTools.hash(EntityContract.editable(old, fields)).equals(args.getString("expected_record_hash")))) {
                    throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Der Datensatz wurde inzwischen geändert. Bitte erneut öffnen.")
                }
                // The effective local snapshot already applies committed journal entries.
                // CAS above protects concurrent edits; only unresolved entries block another write.
                val applied = LocalJournalRules.ids(master.optJSONArray("drive_applied_ids")).apply {
                    addAll(LocalJournalRules.ids(master.optJSONArray("accepted_ids")))
                    master.optJSONObject("drive_resolved_actions")?.keys()?.forEach { add(it) }
                }
                val pending = state.optJSONArray("pending") ?: JSONArray()
                for (i in 0 until pending.length()) {
                    val action = pending.getJSONObject(i)
                    if (action.optString("client_id") !in applied && action.optJSONObject("payload")?.optString("id") == id) {
                        throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Ein früherer lokaler Vorgang ist ungeklärt. Bitte zuerst prüfen.")
                    }
                }
                payload = JSONObject().put("id", id)
                for (key in fields) {
                    if ((old.has(key))) {
                        payload.put(key, old.get(key))
                    }
                }
                for (key in arrayOf("status", "order_id", "created_at")) {
                    if ((old.has(key))) {
                        payload.put(key, old.get(key))
                    }
                }
                var patch: JSONObject = args.getJSONObject("updates")
                run {
                    var it: MutableIterator<String> = patch.keys()
                    while (it.hasNext()) {
                        var key: String = it.next()
                        payload.put(key, patch.get(key))
                    }
                }
                if (((entity.equals("customer") && patch.has("custom_fields")))) {
                    if ((!capabilities!!.optBoolean("customer_custom_fields_compare_and_save"))) {
                        throw PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED", "Der Pixel-Vergleichsschutz für Kunden-Zusatzfelder fehlt; keine Änderung gespeichert.")
                    }
                    var merged: JSONObject = EntityContract.customFields(old)
                    var changes: JSONObject = patch.getJSONObject("custom_fields")
                    run {
                        var it: MutableIterator<String> = changes.keys()
                        while (it.hasNext()) {
                            var key: String = it.next()
                            merged.put(key, changes.get(key))
                        }
                    }
                    payload.put("custom_fields", merged)
                }
                var base: JSONObject = JSONObject().put("id", id)
                for (key in fields) {
                    if (old.has(key)) {
                        base.put(key, old.get(key))
                    }
                }
                for (key in arrayOf("updated_at", "status")) {
                    if ((old.has(key))) {
                        base.put(key, old.get(key))
                    }
                }
                if (((entity.equals("customer") && !capabilities!!.optBoolean("customer_custom_fields_compare_and_save")))) {
                    base.remove("custom_fields")
                    payload.remove("custom_fields")
                } else {
                    if ((entity.equals("customer"))) {
                        base.put("custom_fields", EntityContract.customFields(old))
                    }
                }
                payload.put("_mcp_base", base)
                payload.remove("_mcp_create")
            } else {
                payload = JSONObject()
                for (key in fields) {
                    if ((args.has(key))) {
                        payload.put(key, args.get(key))
                    }
                }
                payload.put("id", (if (entity == "customer") "cus_mobile_mcp_" else "ord_mobile_mcp_") + PixelMcpWriteTools.hash(actionId).substring(0, 32))
                var rows: JSONArray? = master.optJSONArray(collection)
                run {
                    var i: Int = 0
                    while (((rows != null) && (i < rows!!.length()))) {
                        if ((payload.getString("id").equals(rows!!.getJSONObject(i).optString("id")))) {
                            throw PixelMcpWriteTools.Rejected("ID_ALREADY_EXISTS", "Dieser Datensatz existiert bereits.")
                        }
                        i++
                    }
                }
                payload.put("_mcp_create", true).put("_new_record", true)
            }
            if ((entity.equals("customer"))) {
                BillingRules.customerConfig(payload, master.getJSONArray("customers"))
                if ((payload.optString("name").trim().isEmpty())) {
                    throw PixelMcpWriteTools.Rejected("MISSING_FIELD", "Der Kundenname fehlt.")
                }
                if ((!update)) {
                    try {
                        EntityProtectionRules.validateNewCustomer(payload, (if ((master.optJSONArray("customers") == null)) JSONArray() else master.getJSONArray("customers")))
                    } catch (duplicate: IllegalArgumentException) {
                        throw PixelMcpWriteTools.Rejected("POSSIBLE_DUPLICATE", duplicate.message ?: "Möglicherweise besteht bereits ein ähnlicher Kunde. Bestehende Einträge vor dem Speichern prüfen.")
                    }
                }
                var incoming: JSONObject = (if (update) args.getJSONObject("updates") else args)
                if ((((incoming.has("email") && !incoming.optString("email").isEmpty()) && !incoming.optString("email").matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))))) {
                    throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Bitte eine gültige E-Mail-Adresse angeben oder das Feld leer lassen.")
                }
                if (((!update && !payload.has("custom_fields")))) {
                    payload.put("custom_fields", JSONObject())
                }
                payload.put("active", (if (payload.optBoolean("active", (payload.optInt("active", 1) != 0))) 1 else 0))
            } else {
                if ((entity.equals("order"))) {
                    BillingRules.order(payload, master)
                    PixelMcpWriteTools.find(master.optJSONArray("customers"), payload.optString("customer_id"))
                    if ((payload.optString("title").trim().isEmpty())) {
                        throw PixelMcpWriteTools.Rejected("MISSING_FIELD", "Die Auftragsbezeichnung fehlt.")
                    }
                    if ((!update)) {
                        var rows: JSONArray? = master.optJSONArray("orders")
                        run {
                            var i: Int = 0
                            while (((rows != null) && (i < rows!!.length()))) {
                                var row: JSONObject = rows!!.getJSONObject(i)
                                if ((((payload.optString("customer_id").equals(row.optString("customer_id")) && payload.optString("title").trim().equals(row.optString("title").trim(), ignoreCase = true)) && payload.optString("scheduled_date").equals(row.optString("scheduled_date"))))) {
                                    throw PixelMcpWriteTools.Rejected("POSSIBLE_DUPLICATE", "Ein ähnlicher Auftrag existiert bereits. Bitte vorhandenen Auftrag suchen.")
                                }
                                i++
                            }
                        }
                    }
                    if ((update)) {
                        var previous: JSONObject = PixelMcpWriteTools.find(master.optJSONArray("orders"), payload.getString("id"))
                        EntityProtectionRules.validateOrderChange(payload, previous, master.optJSONArray("worklogs"), master.optJSONArray("invoices"), master.optJSONArray("photos"))
                    }
                    if ((!payload.optString("scheduled_date").isEmpty())) {
                        try {
                            java.time.LocalDate.parse(payload.optString("scheduled_date"))
                        } catch (e: Exception) {
                            throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Bitte ein gültiges Auftragsdatum angeben.")
                        }
                    }
                    payload.put("customer_visible", (if (payload.optBoolean("customer_visible", (payload.optInt("customer_visible", 1) != 0))) 1 else 0)).put("source", "ChatGPT Sprache")
                }
            }
                        return JSONObject().put("entity_type", entity).put("payload", payload)
        }
    }
}
