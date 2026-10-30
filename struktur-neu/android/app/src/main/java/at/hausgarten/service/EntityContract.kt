package at.hausgarten.service

import org.json.*
import java.util.*

/** Existing entity fields, stable record hashes and value validation. */
class EntityContract {
    companion object {
        @JvmField val CUSTOMER_FIELDS: Array<String> = arrayOf("test_record", "name", "company", "first_name", "last_name", "street", "postal_code", "city", "country", "phone", "email", "notes", "active", "garden_area", "sevdesk_customer_number", "custom_fields", "billing_mode", "default_billing_customer_id")
        @JvmField val ORDER_FIELDS: Array<String> = arrayOf("test_record", "customer_id", "billing_customer_id", "title", "description", "location", "scheduled_date", "status", "customer_visible")
        @JvmField val INVOICE_FIELDS: Array<String> = arrayOf("test_record", "customer_id", "invoice_date", "service_start", "service_end", "payment_days", "notes", "positions", "domestic_service_confirmed", "order_id", "header_text", "footer_text", "tax_mode", "recipient_type", "recipient_vat_id")
        @Throws(Exception::class)
        @JvmStatic fun validateCustomFields(fields: JSONObject) {
            if (((fields.length() > 50))) {
                throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Bitte höchstens 50 Kunden-Zusatzfelder angeben.")
            }
            run {
                var it: MutableIterator<String> = fields.keys()
                while (it.hasNext()) {
                    var key: String = it.next()
                    var value: Any = fields.get(key)
                    if (((((key.trim { it <= ' ' }.isEmpty() || (key.length > 80)) || key.matches(Regex(".*[\\p{Cntrl}].*"))) || Arrays.asList("__proto__", "constructor", "prototype").contains(key)))) {
                        throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Bitte eine eindeutige Zusatzfeld-Bezeichnung mit höchstens 80 Zeichen verwenden.")
                    }
                    if (((value == JSONObject.NULL))) {
                        continue
                    }
                    if (((value is String && (((value as String)).length <= 2000)))) {
                        continue
                    }
                    if ((value is Boolean)) {
                        continue
                    }
                    if (((value is Number && java.lang.Double.isFinite(((value as Number)).toDouble())))) {
                        continue
                    }
                    throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Zusatzfelder unterstützen Text, endliche Zahlen, Ja/Nein oder null.")
                }
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun customFields(row: JSONObject): JSONObject {
            var value: Any? = row.opt("custom_fields")
            if (((((value == null) || (value == JSONObject.NULL)) || "".equals(value)))) {
                return JSONObject()
            }
            if ((value is JSONObject)) {
                return JSONObject(value!!.toString())
            }
            if ((value is String)) {
                try {
                    return JSONObject((value as String))
                } catch (ignored: Exception) {
                }
            }
            throw PixelMcpWriteTools.Rejected("INVALID_STORED_DATA", "Bestehende Kunden-Zusatzfelder sind nicht lesbar. Bitte prüfen; keine Änderung gespeichert.")
        }
        @Throws(Exception::class)
        @JvmStatic fun editable(row: JSONObject, fields: Array<String>): JSONObject {
            var out: JSONObject = JSONObject().put("id", row.optString("id"))
            if ((row.has("updated_at"))) {
                out.put("updated_at", row.get("updated_at"))
            }
            for (key in fields) {
                if ((row.has(key))) {
                    out.put(key, row.get(key))
                }
            }
            return out
        }
        @Throws(Exception::class)
        @JvmStatic fun validatePositions(positions: JSONArray) {
            run {
                var i: Int = 0
                while ((i < positions.length())) {
                    var p: JSONObject? = positions.optJSONObject(i)
                    if ((((p == null) || p!!.optString("title").trim { it <= ' ' }.isEmpty()))) {
                        throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Jede Rechnungsposition benötigt eine Bezeichnung.")
                    }
                    run {
                        var keys: MutableIterator<String> = p!!.keys()
                        while (keys.hasNext()) {
                            if ((!Arrays.asList("title", "description", "quantity", "price", "unit", "tax_rate", "discount", "worklog_id", "source_type", "source_id", "source_quantity_decimal", "net", "tax", "net_total", "tax_total", "gross_total").contains(keys.next()))) {
                                throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Unbekannte Angabe in Rechnungsposition.")
                            }
                        }
                    }
                    if ((p!!.has("source_type"))) {
                        var type: String = p!!.getString("source_type")
                        if ((!Arrays.asList("manual", "worklog", "material_movement").contains(type))) {
                            throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Leistungsquelle ist ungültig.")
                        }
                        if (((!type.equals("manual") && p!!.optString("source_id").trim { it <= ' ' }.isEmpty()))) {
                            throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Eindeutige Leistungsquelle fehlt.")
                        }
                        if (((p!!.has("source_quantity_decimal") && ((!p!!.getString("source_quantity_decimal").matches(Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,12})?")) || (java.math.BigDecimal(p!!.getString("source_quantity_decimal")).signum() <= 0)))))) {
                            throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Quellmenge muss eine positive Dezimalzahl sein.")
                        }
                        if ((type.equals("worklog"))) {
                            if (((!p!!.optString("worklog_id").isEmpty() && !p!!.getString("worklog_id").equals(p!!.getString("source_id"))))) {
                                throw PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH", "Leistungsquellen passen nicht zusammen.")
                            }
                            p!!.put("worklog_id", p!!.getString("source_id"))
                        }
                    }
                    for (key in arrayOf("quantity", "price", "discount", "tax_rate")) {
                        if (((p!!.has(key) && !(p!!.opt(key) is Number)))) {
                            throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Menge, Preis, Rabatt und Steuer müssen Zahlen sein.")
                        }
                    }
                    var quantity: Double = p!!.optDouble("quantity", (Double.NaN).toDouble())
                    var price: Double = p!!.optDouble("price", (Double.NaN).toDouble())
                    var discount: Double = p!!.optDouble("discount", (0).toDouble())
                    if (((((((((((!java.lang.Double.isFinite(quantity) || (quantity <= 0)) || !java.lang.Double.isFinite(price)) || (price < 0)) || !java.lang.Double.isFinite(discount)) || (discount < 0)) || (discount > 100)) || !java.lang.Double.isFinite(p!!.optDouble("tax_rate", (0).toDouble()))) || (p!!.optDouble("tax_rate", (0).toDouble()) < 0)) || (p!!.optDouble("tax_rate", (0).toDouble()) > 100)))) {
                        throw PixelMcpWriteTools.Rejected("INVALID_INPUT", "Bitte Menge, Preis, Rabatt und Steuersatz prüfen.")
                    }
                    i++
                }
            }
        }
    }
}
