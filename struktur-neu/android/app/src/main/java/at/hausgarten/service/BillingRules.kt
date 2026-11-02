package at.hausgarten.service

import java.math.BigDecimal
import java.util.*
import org.json.*

/** Explicit recipient IDs; customer_id remains the actual work site. No historical reassignment. */
internal class BillingRules {
    companion object {
        @JvmStatic
        fun text(row: JSONObject?, key: String): String {
            return (if (((row === null) || row!!.isNull(key))) ""
            else (row!!.optString(key))!!.trim())
        }

        @JvmStatic
        fun recipient(work: JSONObject): String {
            var id = text(work, "billing_customer_id")
            return (if (id!!.isEmpty()) text(work, "customer_id") else id)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun customer(rows: JSONArray?, id: String): JSONObject {
            return PixelMcpWriteTools.find(rows, id)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun customerConfig(row: JSONObject, customers: JSONArray): Unit {
            var mode = text(row, "billing_mode")
            if (mode!!.isEmpty()) {
                mode = "direct"
            }
            if (!(Arrays.asList("direct", "via_customer"))!!.contains(mode)) {
                throw PixelMcpWriteTools.Rejected(
                    "INVALID_BILLING_MODE",
                    "Abrechnung muss Direkt oder Über einen anderen Kunden sein.",
                )
            }
            var target = text(row, "default_billing_customer_id")
            if (mode!!.equals("direct")) {
                if ((!target!!.isEmpty() && !target!!.equals(text(row, "id")))) {
                    throw PixelMcpWriteTools.Rejected(
                        "BILLING_MISMATCH",
                        "Bei Direktabrechnung bitte keinen anderen Standard-Rechnungsempfänger angeben.",
                    )
                }
                row!!.put("default_billing_customer_id", "")
            } else {
                if ((target!!.isEmpty() || target!!.equals(text(row, "id")))) {
                    throw PixelMcpWriteTools.Rejected(
                        "MISSING_BILLING_CUSTOMER",
                        "Bitte einen anderen vorhandenen Rechnungsempfänger auswählen.",
                    )
                }
                customer(customers, target)
                var visited: MutableSet<String> = HashSet()
                visited!!.add(text(row, "id"))
                var next = target
                while (!next!!.isEmpty()) {
                    if (!visited!!.add(next)) {
                        throw PixelMcpWriteTools.Rejected(
                            "BILLING_CYCLE",
                            "Die Standard-Abrechnung würde einen Kreis bilden. Bitte Zuordnung prüfen.",
                        )
                    }
                    var other = customer(customers, next)
                    next =
                        (if (("via_customer")!!.equals(text(other, "billing_mode")))
                            text(other, "default_billing_customer_id")
                        else "")
                }
            }
            row!!.put("billing_mode", mode)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun defaultRecipient(customerId: String, orderId: String, master: JSONObject): String {
            var workCustomer = customer(master!!.optJSONArray("customers"), customerId)
            var recipient = customerId
            if (("via_customer")!!.equals(text(workCustomer, "billing_mode"))) {
                recipient = text(workCustomer, "default_billing_customer_id")
            }
            if (!orderId!!.isEmpty()) {
                var order = PixelMcpWriteTools.find(master!!.optJSONArray("orders"), orderId)
                if (!customerId!!.equals(text(order, "customer_id"))) {
                    throw PixelMcpWriteTools.Rejected(
                        "REFERENCE_MISMATCH",
                        "Einsatzkunde und Auftrag passen nicht zusammen.",
                    )
                }
                if (!(text(order, "billing_customer_id"))!!.isEmpty()) {
                    recipient = text(order, "billing_customer_id")
                }
            }
            if (recipient!!.isEmpty()) {
                throw PixelMcpWriteTools.Rejected(
                    "MISSING_BILLING_CUSTOMER",
                    "Der Standard-Rechnungsempfänger fehlt. Bitte Kundenabrechnung prüfen.",
                )
            }
            customer(master!!.optJSONArray("customers"), recipient)
            return recipient
        }

        @JvmStatic
        @Throws(Exception::class)
        fun work(row: JSONObject, old: JSONObject?, master: JSONObject): Unit {
            var workCustomer = text(row, "customer_id")
            customer(master!!.optJSONArray("customers"), workCustomer)
            var target = text(row, "billing_customer_id")
            if (target!!.isEmpty()) {
                target =
                    (if ((old !== null)) recipient(old)
                    else defaultRecipient(workCustomer, text(row, "order_id"), master))
            }
            customer(master!!.optJSONArray("customers"), target)
            row!!.put("billing_customer_id", target)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun order(row: JSONObject, master: JSONObject): Unit {
            if (!(text(row, "billing_customer_id"))!!.isEmpty()) {
                customer(master!!.optJSONArray("customers"), text(row, "billing_customer_id"))
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun assignment(row: JSONObject, master: JSONObject, work: Boolean): JSONObject {
            var customerId = text(row, (if (work) "customer_id" else "id"))
            var recipient =
                (if (work) recipient(row)
                else
                    (if (("via_customer")!!.equals(text(row, "billing_mode")))
                        text(row, "default_billing_customer_id")
                    else customerId))
            var out: JSONObject =
                (((JSONObject())!!.put("work_customer_id", customerId))!!.put(
                        "billing_customer_id",
                        recipient,
                    ))!!
                    .put("mode", (if (recipient!!.equals(customerId)) "direct" else "via_customer"))
            try {
                (out!!.put(
                        "billing_customer_name",
                        (customer(master!!.optJSONArray("customers"), recipient))!!.optString(
                            "name"
                        ),
                    ))!!
                    .put("valid", true)
            } catch (missing: Exception) {
                (out!!.put("valid", false))!!.put(
                    "notice",
                    "Rechnungsempfänger nicht vorhanden. Keine automatische Neuzuordnung.",
                )
            }
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun groups(logs: JSONArray?, master: JSONObject): JSONArray {
            val groups = java.util.TreeMap<String, JSONObject>()
            val sorted = (0 until (logs?.length() ?: 0))
                .map { logs!!.getJSONObject(it) }
                .sortedWith(compareByDescending<JSONObject> { it.optString("work_date") }
                    .thenBy { it.optString("id") })
            for (row in sorted) {
                if (row.optInt("billed", 0) != 0) continue
                val recipientId = recipient(row)
                val group = groups.getOrPut(recipientId) {
                    val detail = assignment(row, master, true)
                    JSONObject().put("billing_customer_id", recipientId)
                        .put("billing_customer_name", detail.optString("billing_customer_name", "Zuordnung prüfen"))
                        .put("valid", detail.optBoolean("valid"))
                        .put("hours", 0.0)
                        .put("work_records", JSONArray())
                        .put("billing_status", "marked_open_not_proven")
                }
                val copy = JSONObject(row.toString())
                    .put("billing_customer_id", recipientId)
                    .put("billing_evidence", "marked_open_not_proven")
                try {
                    copy.put("work_customer_name",
                        customer(master.optJSONArray("customers"), text(row, "customer_id")).optString("name"))
                } catch (_: Exception) {
                    copy.put("work_customer_name", "Einsatzkunde fehlt")
                    group.put("valid", false)
                }
                group.getJSONArray("work_records").put(copy)
                group.put("hours", group.optDouble("hours") + row.optDouble("hours"))
            }
            val result = JSONArray()
            for (group in groups.values) {
                group.put("hours", Math.round(group.getDouble("hours") * 100) / 100.0)
                result.put(group)
            }
            return result
        }
    }
}
