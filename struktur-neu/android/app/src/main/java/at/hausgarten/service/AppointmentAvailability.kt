package at.hausgarten.service

import org.json.*
import java.time.*

/** Read-only collision projection. No event titles, writes, bookings or network requests. */
class AppointmentAvailability {
    private constructor() {
    }
    companion object {
        @Throws(Exception::class)
        @JvmStatic fun context(cache: JSONObject, enabled: Boolean, now: Instant): JSONObject {
            var out: JSONObject = JSONObject().put("verified", false).put("busy", JSONArray())
            if (((!enabled || !cache.optBoolean("verified")))) {
                return out
            }
            try {
                var checked: Instant = AppointmentRules.instant(cache.getString("checked_at"))
                var from: Instant = AppointmentRules.instant(cache.getString("from"))
                var to: Instant = AppointmentRules.instant(cache.getString("to"))
                if ((((!checked.isAfter(now.minusSeconds(1800)) || checked.isAfter(now.plusSeconds(60))) || !to.isAfter(from)))) {
                    return out
                }
                var safe: JSONArray = JSONArray()
                var periods: JSONArray = cache.getJSONArray("busy")
                run {
                    var i: Int = 0
                    while ((i < periods.length())) {
                        var p: JSONObject = periods.getJSONObject(i)
                        var start: Instant = AppointmentRules.instant(p.getString("start"))
                        var end: Instant = AppointmentRules.instant(p.getString("end"))
                        if ((!end.isAfter(start))) {
                            return out
                        }
                        safe.put(JSONObject().put("start", start.toString()).put("end", end.toString()))
                        i++
                    }
                }
                return out.put("verified", true).put("checked_at", checked.toString()).put("from", from.toString()).put("to", to.toString()).put("busy", safe)
            } catch (invalid: Exception) {
                return out
            }
        }
        @JvmStatic fun overlaps(a: Instant, b: Instant, c: Instant, d: Instant): Boolean {
            return (a.isBefore(d) && b.isAfter(c))
        }
        @Throws(Exception::class)
        @JvmStatic fun assess(row: JSONObject, appointments: JSONArray, availability: JSONObject): JSONObject {
            var clashes: JSONArray = JSONArray()
            var out: JSONObject = JSONObject().put("conflicts", clashes).put("external_checked", false)
            if (((!AppointmentRules.active(row) || row.optString("status").equals("draft")))) {
                return out.put("inactive", true)
            }
            var open: Boolean = AppointmentRules.openEnd(row)
            var work: Instant = AppointmentRules.instant(row.getString("starts_at"))
            var start: Instant = work.minusSeconds((row.optInt("travel_minutes") * 60L))
            var end: Instant = (if (open) work else AppointmentRules.instant(row.getString("ends_at")))
            var appChecked: Boolean = !open
            run {
                var i: Int = 0
                while ((i < appointments.length())) {
                    var other: JSONObject = appointments.getJSONObject(i)
                    if ((((row.optString("id").equals(other.optString("id")) || !AppointmentRules.active(other)) || other.optString("status").equals("draft")))) {
                        i++
                        continue
                    }
                    var kind: Int = AppointmentRules.overlapKind(row, other)
                    if (((kind == 1))) {
                        appChecked = false
                    }
                    if (((kind > 0))) {
                        var plainLeft: JSONObject = JSONObject(row.toString()).put("travel_minutes", 0)
                        var plainRight: JSONObject = JSONObject(other.toString()).put("travel_minutes", 0)
                        clashes.put(JSONObject().put("source", "app").put("appointment_id", other.getString("id")).put("starts_at", other.getString("starts_at")).put("ends_at", other.getString("ends_at")).put("uncertain", (kind == 1)).put("travel_only", ((kind == 2) && (AppointmentRules.overlapKind(plainLeft, plainRight) != 2))))
                    }
                    i++
                }
            }
            var verified: Boolean = availability.optBoolean("verified")
            try {
                verified = ((verified && !AppointmentRules.instant(availability.getString("from")).isAfter(start)) && !AppointmentRules.instant(availability.getString("to")).isBefore(end))
            } catch (missing: Exception) {
                verified = false
            }
            if ((verified)) {
                var periods: JSONArray = availability.getJSONArray("busy")
                run {
                    var i: Int = 0
                    while ((i < periods.length())) {
                        var p: JSONObject = periods.getJSONObject(i)
                        var other: JSONObject = JSONObject().put("starts_at", p.getString("start")).put("ends_at", p.getString("end"))
                        var kind: Int = AppointmentRules.overlapKind(row, other)
                        if (((kind > 0))) {
                            clashes.put(JSONObject().put("source", "google").put("starts_at", p.getString("start")).put("ends_at", p.getString("end")).put("uncertain", (kind == 1)).put("travel_only", ((kind == 2) && (AppointmentRules.overlapKind(JSONObject(row.toString()).put("travel_minutes", 0), other) != 2))))
                        }
                        i++
                    }
                }
            }
            return out.put("app_checked", appChecked).put("duration_unknown", open).put("external_checked", (verified && !open)).put("checked_at", (if (verified) availability.optString("checked_at") else ""))
        }
    }
}
