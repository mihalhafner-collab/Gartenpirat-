package at.hausgarten.service

import org.json.*
import java.util.*

/** Validated customer/order/invoice-draft use cases, independent of transport and storage. */
class EntityApplicationService {
    companion object {
        @Throws(Exception::class)
        @JvmStatic fun prepare(name: String, args: JSONObject, state: JSONObject, actionId: String): JSONObject {
            var master: JSONObject = SnapshotProjection.effective(state)
            var update: Boolean = name.startsWith("update_")
            var entity: String = (if (name.contains("customer")) "customer" else (if (name.contains("order")) "order" else "invoice"))
            var collection: String = (if (entity.equals("customer")) "customers" else (if (entity.equals("order")) "orders" else "invoices"))
            var fields: Array<String> = (if (entity.equals("customer")) EntityContract.CUSTOMER_FIELDS else (if (entity.equals("order")) EntityContract.ORDER_FIELDS else EntityContract.INVOICE_FIELDS))
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
                    throw PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED", "Der Änderungsstand fehlt. Bitte zuerst vollständig abgleichen.")
                }
                if ((!PixelMcpWriteTools.hash(EntityContract.editable(old, fields)).equals(args.getString("expected_record_hash")))) {
                    throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Der Datensatz wurde inzwischen geändert. Bitte erneut öffnen.")
                }
                var pending: JSONArray? = (if (PixelDriveState.ENGINE.equals(master.optString("processing_engine"))) null else state.optJSONArray("pending"))
                run {
                    var i: Int = 0
                    while (((pending != null) && (i < pending!!.length()))) {
                        var a: JSONObject = pending!!.getJSONObject(i)
                        var p: JSONObject? = a.optJSONObject("payload")
                        if ((((p != null) && id.equals(p!!.optString("id"))))) {
                            throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Für diesen Datensatz ist bereits eine Änderung vorgemerkt. Bitte zuerst abgleichen.")
                        }
                        i++
                    }
                }
                payload = JSONObject().put("id", id)
                for (key in fields) {
                    if ((old.has(key))) {
                        payload.put(key, old.get(key))
                    }
                }
                for (key in arrayOf("status", "sevdesk_id", "sevdesk_contact_id", "issued_at", "invoice_number", "order_id", "created_at", "document_type", "correction_of", "correction_reason", "header_text", "footer_text")) {
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
                    if (((((!key.equals("positions") && !key.equals("domestic_service_confirmed")) && !key.equals("payment_days")) && old.has(key)))) {
                        base.put(key, old.get(key))
                    }
                }
                for (key in arrayOf("updated_at", "status", "invoice_number")) {
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
                if ((entity.equals("invoice"))) {
                    payload.put("_mcp_invoice_positions", old.optJSONArray("positions")).put("_mcp_invoice_payment_days", old.optInt("payment_days", 14))
                }
            } else {
                payload = JSONObject()
                for (key in fields) {
                    if ((args.has(key))) {
                        payload.put(key, args.get(key))
                    }
                }
                payload.put("id", (((if (entity.equals("customer")) "cus_mobile_mcp_" else (if (entity.equals("order")) "ord_mobile_mcp_" else "inv_mobile_mcp_"))) + PixelMcpWriteTools.hash(actionId).substring(0, 32)))
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
                BillingRules.customerConfig(payload, master.optJSONArray("customers"))
                if ((payload.optString("name").trim { it <= ' ' }.isEmpty())) {
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
                    if ((payload.optString("title").trim { it <= ' ' }.isEmpty())) {
                        throw PixelMcpWriteTools.Rejected("MISSING_FIELD", "Die Auftragsbezeichnung fehlt.")
                    }
                    if ((!update)) {
                        var rows: JSONArray? = master.optJSONArray("orders")
                        run {
                            var i: Int = 0
                            while (((rows != null) && (i < rows!!.length()))) {
                                var row: JSONObject = rows!!.getJSONObject(i)
                                if ((((payload.optString("customer_id").equals(row.optString("customer_id")) && payload.optString("title").trim { it <= ' ' }.equals(row.optString("title").trim { it <= ' ' }, ignoreCase = true)) && payload.optString("scheduled_date").equals(row.optString("scheduled_date"))))) {
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
                } else {
                    PixelMcpWriteTools.find(master.optJSONArray("customers"), payload.optString("customer_id"))
                    if ((((!"Entwurf".equals(payload.optString("status", "Entwurf")) || !payload.optString("sevdesk_id").isEmpty()) || !payload.optString("issued_at").isEmpty()))) {
                        throw PixelMcpWriteTools.Rejected("INVOICE_LOCKED", "Nur lokale Rechnungsentwürfe dürfen bearbeitet werden.")
                    }
                    for (key in arrayOf("invoice_date", "service_start", "service_end")) {
                        if ((!payload.optString(key).isEmpty())) {
                            try {
                                java.time.LocalDate.parse(payload.optString(key))
                            } catch (e: Exception) {
                                throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Bitte gültige Rechnungs- und Leistungsdaten angeben.")
                            }
                        }
                    }
                    EntityContract.validatePositions(payload.getJSONArray("positions"))
                    payload.put("status", "Entwurf").put("sevdesk_id", "").put("source", "ChatGPT Sprache")
                    var positions: JSONArray = payload.getJSONArray("positions")
                    var used: MutableSet<String> = HashSet()
                    run {
                        var i: Int = 0
                        while ((i < positions.length())) {
                            var worklogId: String = positions.getJSONObject(i).optString("worklog_id")
                            if ((!worklogId.isEmpty())) {
                                var log: JSONObject = PixelMcpWriteTools.find(master.optJSONArray("worklogs"), worklogId)
                                if ((((!payload.optString("customer_id").equals(BillingRules.recipient(log)) || (log.optInt("billed", 0) != 0)) || !used.add(worklogId)))) {
                                    throw PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH", "Rechnungspositionen müssen offene Arbeiten dieses Kunden verwenden und dürfen keine Arbeit doppelt enthalten.")
                                }
                            }
                            i++
                        }
                    }
                    if ((!update)) {
                        payload.put("invoice_number", ("Entwurf-" + payload.getString("id")))
                    }
                    if (((payload.optInt("payment_days", 14) > 365))) {
                        throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Das Zahlungsziel muss zwischen 0 und 365 Tagen liegen.")
                    }
                    var total: java.math.BigDecimal = java.math.BigDecimal.ZERO
                    run {
                        var i: Int = 0
                        while ((i < positions.length())) {
                            var p: JSONObject? = positions.getJSONObject(i)
                            var quantity: java.math.BigDecimal = java.math.BigDecimal(p!!.get("quantity").toString())
                            var price: java.math.BigDecimal = java.math.BigDecimal(p!!.get("price").toString())
                            var discount: java.math.BigDecimal = java.math.BigDecimal((if ((p!!.opt("discount") == null)) "0" else p!!.get("discount").toString()))
                            var amount: java.math.BigDecimal = quantity.multiply(price).multiply(java.math.BigDecimal.ONE.subtract(discount.divide(java.math.BigDecimal("100")))).setScale(2, java.math.RoundingMode.HALF_UP)
                            p!!.put("net", amount.toDouble()).put("tax", 0)
                            total = total.add(amount)
                            i++
                        }
                    }
                    payload.put("amount", total.toDouble()).put("net_total", total.toDouble()).put("gross_total", total.toDouble()).put("tax_total", 0)
                    InvoiceDraftRules.normalize(payload, (if (update) PixelMcpWriteTools.find(master.getJSONArray("invoices"), payload.getString("id")) else null), master)
                }
            }
            return JSONObject().put("entity_type", entity).put("payload", payload)
        }
    }
}
