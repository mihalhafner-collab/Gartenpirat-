package at.hausgarten.service

import org.json.*
import java.util.*

/** Existing entity fields, stable record hashes and value validation. */
class EntityContract {
    companion object {
        @JvmField val CUSTOMER_FIELDS: Array<String> = arrayOf("test_record", "name", "company", "first_name", "last_name", "street", "postal_code", "city", "country", "phone", "email", "notes", "active", "garden_area", "sevdesk_customer_number", "custom_fields", "billing_mode", "default_billing_customer_id")
        @JvmField val ORDER_FIELDS: Array<String> = arrayOf("test_record", "customer_id", "billing_customer_id", "title", "description", "location", "scheduled_date", "status", "customer_visible")
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

    }
}
