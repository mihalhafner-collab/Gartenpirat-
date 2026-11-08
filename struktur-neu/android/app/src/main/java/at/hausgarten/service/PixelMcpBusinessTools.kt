package at.hausgarten.service

import java.util.*
import org.json.*

internal class PixelMcpBusinessTools {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun sync(legacy: JSONObject, state: JSONObject): JSONObject {
            val saved =
                legacy.optBoolean("saved_locally", legacy.optString("status") == "local_saved")
            return JSONObject(legacy.toString())
                .put("status", if (saved) "local_saved" else "unknown")
                .put("sync_state", "not_applicable")
                .put("local_state", if (saved) "local_saved" else "unknown")
                .put("cloud_confirmed", false)
                .put("confirmation_scope", "local_only")
        }

        @JvmStatic
        fun billing(row: JSONObject): String =
            if (row.optInt("billed", 0) != 0) "marked_billed" else "marked_open"

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
                    if (row.optInt("billed", 0) == 0) marked++
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
                    .put("marked_open", marked)
                    .put("marked_billed", logs.length() - marked)
                    .put("issues", issues)
                    .put("timezone", "Europe/Vienna")
                    .put(
                        "billing_notice",
                        "Diese Übersicht verwendet die lokalen Abrechnungsmarkierungen. Bestätigte Beträge beider Abrechnungsarten stehen getrennt und gemeinsam in get_accounting_overview; Zahlungen werden als eigene Einträge geführt.",
                    )
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
