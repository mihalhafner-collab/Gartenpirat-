package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Mirrors the existing laptop relation/issued-invoice guards without changing any records. */
class WorklogRelationRules {
    companion object {
        private fun text(row: JSONObject?, key: String): String {
            return (if ((row == null) || row.isNull(key)) "" else row.optString(key))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun validate(
            payload: JSONObject,
            old: JSONObject?,
            photos: JSONArray?,
            invoices: JSONArray?,
        ) {
            if (old == null) {
                return
            }
            var id: String = old.getString("id")
            val customer: String = text(payload, "customer_id")
            var order: String = text(payload, "order_id")
            run {
                var i: Int = 0
                while ((photos != null) && (i < photos.length())) {
                    val photo: JSONObject? = photos.optJSONObject(i)
                    if ((photo == null) || !id.equals(text(photo, "worklog_id"))) {
                        i++
                        continue
                    }
                    if (
                        (!customer.equals(text(photo, "customer_id")) ||
                            (!text(photo, "order_id").isEmpty() &&
                                !order.equals(text(photo, "order_id"))))
                    ) {
                        throw IllegalArgumentException(
                            "Bitte zuerst die Fotozuordnung prüfen. Kunde oder Auftrag würden dem Foto widersprechen."
                        )
                    }
                    i++
                }
            }
            run {
                var i: Int = 0
                while ((invoices != null) && (i < invoices.length())) {
                    val invoice: JSONObject? = invoices.optJSONObject(i)
                    if (invoice == null) {
                        i++
                        continue
                    }
                    val ids: JSONArray? = invoice.optJSONArray("worklog_ids")
                    var linked: Boolean = false
                    run {
                        var j: Int = 0
                        while ((ids != null) && (j < ids.length())) {
                            if (id.equals(ids.optString(j))) {
                                linked = true
                            }
                            j++
                        }
                    }
                    val positions: JSONArray? = invoice.optJSONArray("positions")
                    run {
                        var j: Int = 0
                        while ((positions != null) && (j < positions.length())) {
                            val position: JSONObject? = positions.optJSONObject(j)
                            if (
                                ((position != null) && id.equals(position.optString("worklog_id")))
                            ) {
                                linked = true
                            }
                            j++
                        }
                    }
                    if (!linked) {
                        i++
                        continue
                    }
                    if (
                        ((!customer.equals(text(old, "customer_id")) ||
                            !order.equals(text(old, "order_id"))) ||
                            !BillingRules.recipient(payload).equals(BillingRules.recipient(old)))
                    ) {
                        throw IllegalArgumentException(
                            "Diese Arbeit ist mit einer Rechnung verknüpft. Kunde und Auftrag bleiben erhalten."
                        )
                    }
                    if (text(invoice, "issued_at").isEmpty()) {
                        i++
                        continue
                    }
                    for (field in
                        arrayOf(
                            "work_date",
                            "start_time",
                            "end_time",
                            "break_minutes",
                            "hours",
                            "activity",
                            "material",
                            "kilometers",
                        )) {
                        if (!payload.has(field)) {
                            continue
                        }
                        var numeric: Boolean =
                            ((field.equals("break_minutes") || field.equals("hours")) ||
                                field.equals("kilometers"))
                        var changed: Boolean =
                            (if (numeric)
                                (java.lang.Double.compare(
                                    payload.optDouble(field, (0).toDouble()),
                                    old.optDouble(field, (0).toDouble()),
                                ) != 0)
                            else !text(payload, field).equals(text(old, field)))
                        if (changed) {
                            throw IllegalArgumentException(
                                "Abgerechnete Leistungsdaten bleiben unverändert. Interne Notizen können ergänzt werden."
                            )
                        }
                    }
                    // User markings are independent of retained invoice evidence and payments.
                    i++
                }
            }
        }
    }
}
