package at.hausgarten.service

import org.json.JSONObject

/** Shared existing mobile duration/default rules; does not allocate or change any ID. */
class WorklogRules {
    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun validate(payload: JSONObject, isNew: Boolean) {
            if (payload.has("disposal_cost") && !payload.isNull("disposal_cost")) {
                val entered = payload.get("disposal_cost").toString().trim().replace(',', '.')
                if (entered.isEmpty()) payload.remove("disposal_cost")
                else {
                    val amount =
                        entered.toBigDecimalOrNull()
                            ?: throw IllegalArgumentException(
                                "Bitte einen gültigen Entsorgungsbetrag eingeben."
                            )
                    require(amount.signum() >= 0 && amount.stripTrailingZeros().scale() <= 2) {
                        "Entsorgung als Eurobetrag mit höchstens zwei Nachkommastellen eingeben."
                    }
                    payload.put("disposal_cost", amount)
                }
            }
            for (field in listOf("break_minutes", "hours", "kilometers")) {
                if (
                    payload.has(field) &&
                        !payload.isNull(field) &&
                        payload.get(field).toString().isNotBlank()
                ) {
                    val number =
                        payload.get(field).toString().trim().replace(',', '.').toDoubleOrNull()
                    require(number?.isFinite() == true) {
                        "Bitte gültige Stunden, Kilometer und eine Pause in ganzen Minuten eingeben."
                    }
                    payload.put(field, number)
                }
            }
            val pause: Double = payload.optDouble("break_minutes", (0).toDouble())
            val km: Double = payload.optDouble("kilometers", (0).toDouble())
            var hours: Double = payload.optDouble("hours", (0).toDouble())
            if (
                (((((((!java.lang.Double.isFinite(pause) || !java.lang.Double.isFinite(km)) ||
                    !java.lang.Double.isFinite(hours)) || (pause < 0)) || (km < 0)) ||
                    (hours < 0)) || (pause != Math.floor(pause))))
            ) {
                throw IllegalArgumentException(
                    "Bitte gültige Stunden, Kilometer und eine Pause in ganzen Minuten eingeben."
                )
            }
            var day: String = payload.optString("work_date")
            if ((day.isEmpty())) {
                day = java.time.LocalDate.now().toString()
                payload.put("work_date", day)
            }
            try {
                java.time.LocalDate.parse(day)
            } catch (error: Exception) {
                throw IllegalArgumentException("Bitte ein gültiges Arbeitsdatum auswählen.")
            }
            var start: String =
                (if (payload.isNull("start_time")) "" else payload.optString("start_time"))
            var end: String =
                (if (payload.isNull("end_time")) "" else payload.optString("end_time"))
            if (((start.isEmpty() != end.isEmpty()))) {
                throw IllegalArgumentException(
                    "Bitte Beginn und Ende gemeinsam angeben oder nur die Arbeitsdauer."
                )
            }
            if ((!start.isEmpty())) {
                var minutes: Int = (timeMinutes(end) - timeMinutes(start))
                if ((((minutes < 0) && payload.optBoolean("allow_overnight", false)))) {
                    minutes += 1440
                }
                if (((minutes <= 0))) {
                    throw IllegalArgumentException("Die Endzeit muss nach dem Beginn liegen.")
                }
                if (((pause >= minutes))) {
                    throw IllegalArgumentException(
                        "Die Pause muss kürzer als der gesamte Einsatz sein."
                    )
                }
                hours = (Math.round(((((minutes - pause)) / 60.0) * 100.0)) / 100.0)
                payload.put("hours", hours)
            }
            if (((isNew && (hours <= 0)))) {
                throw IllegalArgumentException("Bitte Arbeitsdauer oder Beginn und Ende eintragen.")
            }
            payload
                .put("break_minutes", (pause).toInt())
                .put("kilometers", km)
                .put("billed", payload.optInt("billed", 0))
                .put("customer_visible", payload.optInt("customer_visible", 1))
        }

        private fun timeMinutes(value: String): Int {
            if ((!value.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")))) {
                throw IllegalArgumentException("Bitte gültige Uhrzeiten im Format HH:MM eingeben.")
            }
            return ((Integer.parseInt(value.substring(0, 2)) * 60) +
                Integer.parseInt(value.substring(3)))
        }
    }
}
