package at.hausgarten.service

import java.util.*
import org.json.*

internal class PixelMcpBusinessTools {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun sync(legacy: JSONObject, state: JSONObject): JSONObject {
            val saved = legacy.optBoolean("saved_locally",
                legacy.optString("status") == "local_saved")
            return JSONObject(legacy.toString())
                .put("status", if (saved) "local_saved" else "unknown")
                .put("sync_state", "not_applicable")
                .put("local_state", if (saved) "local_saved" else "unknown")
                .put("cloud_confirmed", false)
                .put("confirmation_scope", "local_only")
        }

        @JvmStatic
        @Throws(Exception::class)
        fun billing(row: JSONObject, invoices: JSONArray?): String {

            var id = row.optString("id")
            var draft: Boolean = false
            var issued: Boolean = false
            run {
                var i: Int = 0
                var firstIteration81 = true
                while (true) {
                    if (firstIteration81) firstIteration81 = false
                    else {
                        i++
                    }
                    if (!(((invoices != null) && (i < invoices.length())))) break

                    var inv = invoices.getJSONObject(i)
                    if (
                        Arrays.asList("Storniert", "Cancelled", "Entfernt")
                            .contains(inv.optString("status"))
                    ) {
                        continue
                    }
                    var links = inv.optJSONArray("worklog_ids")
                    var linked: Boolean = false
                    run {
                        var j: Int = 0
                        var firstIteration79 = true
                        while (true) {
                            if (firstIteration79) firstIteration79 = false
                            else {
                                j++
                            }
                            if (!(((links != null) && (j < links.length())))) break

                            if ((id == links.optString(j))) {
                                linked = true
                            }
                        }
                    }
                    var positions = inv.optJSONArray("positions")
                    run {
                        var j: Int = 0
                        var firstIteration80 = true
                        while (true) {
                            if (firstIteration80) firstIteration80 = false
                            else {
                                j++
                            }
                            if (!(((positions != null) && (j < positions.length())))) break

                            if ((id == positions.getJSONObject(j).optString("worklog_id"))) {
                                linked = true
                            }
                        }
                    }
                    if (linked) {
                        if (
                            (!(inv.optString("issued_at").isEmpty()) ||
                                (!(inv.optString("sevdesk_id").isEmpty()) &&
                                    !(("Entwurf" == inv.optString("status")))))
                        ) {
                            issued = true
                        } else {
                            draft = true
                        }
                    }
                }
            }
            return (if (issued) "proven_invoiced"
            else
                (if ((row.optInt("billed", 0) != 0)) "billing_unclear"
                else (if (draft) "draft_linked_not_proven" else "marked_open_not_proven")))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun billing(row: JSONObject, invoices: JSONArray?, master: JSONObject): String {

            var quantity = LocalBillingRules.state(row, invoices, master)
            return (if ((quantity == null)) billing(row, invoices)
            else quantity.getString("evidence"))
        }

        @JvmStatic
        fun overlap(a: JSONObject, b: JSONObject): Boolean {

            if (
                (!((a.optString("work_date") == b.optString("work_date"))) ||
                    (a.optString("id") == b.optString("id")))
            ) {
                return false
            }
            try {

                var `as`: Int = java.time.LocalTime.parse(a.getString("start_time")).toSecondOfDay()
                var ae: Int = java.time.LocalTime.parse(a.getString("end_time")).toSecondOfDay()
                var bs: Int = java.time.LocalTime.parse(b.getString("start_time")).toSecondOfDay()
                var be: Int = java.time.LocalTime.parse(b.getString("end_time")).toSecondOfDay()
                return ((((ae > `as`) && (be > bs)) && (`as` < be)) && (bs < ae))
            } catch (e: Exception) {
                return false
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun preventOverlap(payload: JSONObject, logs: JSONArray?): Unit {

            run {
                var i: Int = 0
                var firstIteration82 = true
                while (true) {
                    if (firstIteration82) firstIteration82 = false
                    else {
                        i++
                    }
                    if (!(((logs != null) && (i < logs.length())))) break

                    if (overlap(payload, logs.getJSONObject(i))) {
                        throw PixelMcpWriteTools.Rejected(
                            "TIME_OVERLAP",
                            "Diese Uhrzeiten überschneiden sich mit einer vorhandenen Arbeitsaufzeichnung. Bitte den Tag prüfen; keine Änderung wurde gespeichert.",
                        )
                    }
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun report(logs: JSONArray, master: JSONObject): JSONObject {

            var hours: Double = 0.0
            var marked: Int = 0
            var proven: Int = 0
            var unclear: Int = 0
            var partial: Int = 0
            var issues = JSONArray()
            var customerIds = HashSet<String>()
            var customers = master.optJSONArray("customers")
            run {
                var i: Int = 0
                var firstIteration83 = true
                while (true) {
                    if (firstIteration83) firstIteration83 = false
                    else {
                        i++
                    }
                    if (!(((customers != null) && (i < customers.length())))) break

                    customerIds.add(customers.getJSONObject(i).optString("id"))
                }
            }
            if ((logs.length() > 5000)) {
                throw IllegalArgumentException(
                    "Bitte einen engeren Zeitraum für die Qualitätsprüfung wählen."
                )
            }
            run {
                var i: Int = 0
                var firstIteration85 = true
                while (true) {
                    if (firstIteration85) firstIteration85 = false
                    else {
                        i++
                    }
                    if (!((i < logs.length()))) break

                    var row = logs.getJSONObject(i)
                    hours += row.optDouble("hours", 0.0)
                    var evidence = billing(row, master.optJSONArray("invoices"), master)
                    if ((evidence == "proven_invoiced")) {
                        proven++
                    } else {
                        if ((evidence == "marked_open_not_proven")) {
                            marked++
                        } else {
                            if ((evidence == "partially_invoiced")) {
                                partial++
                            } else {
                                unclear++
                            }
                        }
                    }
                    if (!(customerIds.contains(row.optString("customer_id")))) {
                        issues.put(
                            issue(
                                "MISSING_CUSTOMER",
                                row,
                                "Kundenverknüpfung fehlt oder ist ungültig.",
                            )
                        )
                    }
                    try {

                        var normalized = JSONObject(row.toString())
                        WorklogRules.validate(normalized, false)
                        if (
                            (Math.abs((normalized.optDouble("hours") - row.optDouble("hours"))) >
                                0.005)
                        ) {
                            issues.put(
                                issue(
                                    "TIME_MISMATCH",
                                    row,
                                    "Gespeicherte Stunden passen nicht zu den Uhrzeiten.",
                                )
                            )
                        }
                    } catch (e: Exception) {
                        issues.put(
                            issue(
                                "INVALID_WORK_RECORD",
                                row,
                                "Datum, Zeiten, Tätigkeit und Zahlen bitte prüfen.",
                            )
                        )
                    }
                    if (((row.optInt("billed", 0) == 0) && (evidence == "proven_invoiced"))) {
                        issues.put(
                            issue(
                                "BILLING_MISMATCH",
                                row,
                                "Verknüpfte ausgestellte Rechnung vorhanden, App-Markierung noch offen. Nicht automatisch geändert.",
                            )
                        )
                    }
                    run {
                        var j: Int = 0
                        var firstIteration84 = true
                        while (true) {
                            if (firstIteration84) firstIteration84 = false
                            else {
                                j++
                            }
                            if (!((j < i))) break

                            var other = logs.getJSONObject(j)
                            if (overlap(row, other)) {
                                issues.put(
                                    issue(
                                        "TIME_OVERLAP",
                                        row,
                                        ("Überschneidung mit " + other.optString("id")),
                                    )
                                )
                            }
                            if (
                                ((((row.optString("customer_id") ==
                                    other.optString("customer_id")) &&
                                    (row.optString("work_date") == other.optString("work_date"))) &&
                                    (Math.abs((row.optDouble("hours") - other.optDouble("hours"))) <
                                        0.005)) &&
                                    row.optString("activity")
                                        .trim()
                                        .equals(
                                            other.optString("activity").trim(),
                                            ignoreCase = true,
                                        ))
                            ) {
                                issues.put(
                                    issue(
                                        "POSSIBLE_DUPLICATE",
                                        row,
                                        ("Ähnlicher Eintrag: " + other.optString("id")),
                                    )
                                )
                            }
                        }
                    }
                }
            }
            var result =
                JSONObject()
                    .put("work_records", logs.length())
                    .put("hours", (Math.round((hours * 100)) / 100.0))
                    .put("marked_open_not_proven", marked)
                    .put("proven_invoiced", proven)
                    .put("billing_unclear_or_draft", unclear)
                    .put("issues", issues)
                    .put("timezone", "Europe/Vienna")
                    .put(
                        "billing_notice",
                        "Offen markiert ist kein Nachweis fehlender Rechnungen. Tatsächlich nicht abgerechnet ist ohne vollständige verlässliche Rechnungsquelle nicht bestimmbar.",
                    )
            if ((master.optJSONObject("local_billing_sources") != null)) {
                result.put("partially_invoiced", partial)
            }
            return result
        }

        private @JvmStatic @Throws(Exception::class) fun issue(
            code: String,
            row: JSONObject,
            message: String,
        ): JSONObject {

            return JSONObject()
                .put("code", code)
                .put("work_record_id", row.optString("id"))
                .put("message", message)
        }
    }
}
