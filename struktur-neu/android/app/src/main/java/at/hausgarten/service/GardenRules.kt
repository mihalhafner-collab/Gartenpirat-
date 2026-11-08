package at.hausgarten.service

import org.json.*

/** Optional garden facts reuse existing customer fields and the same revision guard. */
class GardenRules {
    companion object {
        @JvmField val LAWN: String = "Rasenfläche (m²)"
        @JvmField val HEDGE: String = "Heckenlänge (m)"
        @JvmField val WATER: String = "Bewässerung"
        @JvmField val CARE: String = "Pflegehinweise"
        @JvmField val ACCESS: String = "Zugangshinweise"

        @Throws(Exception::class)
        @JvmStatic
        fun normalize(customer: JSONObject) {
            if ((customer.has("garden_area"))) {
                customer.put("garden_area", number(customer.opt("garden_area")))
            }
            var raw: Any? = customer.opt("custom_fields")
            var fields: JSONObject =
                (if (raw is JSONObject) JSONObject(raw.toString())
                else
                    (if ((raw is String && !((raw as String)).isEmpty()))
                        JSONObject((raw as String))
                    else JSONObject()))
            for (key in arrayOf(LAWN, HEDGE)) {
                if ((fields.has(key))) {
                    fields.put(key, number(fields.opt(key)))
                }
            }
            for (key in arrayOf(WATER, CARE, ACCESS)) {
                if (
                    (((fields.has(key) && (fields.opt(key) != JSONObject.NULL)) &&
                        ((!(fields.opt(key) is String) || (fields.getString(key).length > 2000)))))
                ) {
                    throw IllegalArgumentException(
                        "Gartenhinweise dürfen höchstens 2000 Zeichen enthalten."
                    )
                }
            }
            if (
                (((customer.opt("garden_area") is Number && fields.opt(LAWN) is Number) &&
                    (fields.getDouble(LAWN) > customer.getDouble("garden_area"))))
            ) {
                throw IllegalArgumentException(
                    "Die Rasenfläche darf nicht größer als die gesamte Gartenfläche sein."
                )
            }
            if ((customer.has("custom_fields"))) {
                customer.put("custom_fields", fields)
            }
        }

        private fun number(value: Any?): Any {
            if (
                ((((value == null) || (value == JSONObject.NULL)) ||
                    value!!.toString().trim().isEmpty()))
            ) {
                return JSONObject.NULL
            }
            try {
                var n: Double = java.lang.Double.parseDouble(value!!.toString().replace(',', '.'))
                if ((((!java.lang.Double.isFinite(n) || (n < 0)) || (n > 1_000_000_000)))) {
                    throw NumberFormatException()
                }
                return n
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Bitte eine gültige, nicht negative Gartenfläche oder Heckenlänge eingeben."
                )
            }
        }
    }
}
