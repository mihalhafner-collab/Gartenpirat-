package at.hausgarten.service

import org.json.*
import java.time.*

/** Planning use cases; account/device work is an explicit repository port. */
internal class PlanningApplicationService(private val repository: OwnerRepository) : OwnerCommand {
    @Throws(Exception::class) override fun perform(name: String, a: JSONObject, request: String): JSONObject {
        val all = repository.effective(); val stamp = Instant.now().toString()
        if (name == "update_planning_settings") {
            val settings = repository.planningSettings()
            if (PixelMcpWriteTools.hash(settings) != a.getString("expected_record_hash")) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Planungseinstellungen wurden geändert.")
            val changes = a.getJSONObject("updates"); for (key in changes.keys()) settings.put(key, changes.get(key))
            require(!settings.optBoolean("calendar_enabled") || repository.calendarConnected()) { "Google-Kalender zuerst am Gerät autorisieren." }
            repository.savePlanningSettings(settings, a.getString("expected_record_hash"))
            return JSONObject().put("record_hash", PixelMcpWriteTools.hash(settings)).put("local_state", "local_saved").put("credentials_changed", false)
        }
        if (name == "request_weather_refresh" || name == "request_calendar_sync") return repository.requestPlanning()
        if (name == "acknowledge_weather_alert") return repository.acknowledgeWeather(a)
        if (name == "resolve_calendar_conflict") return repository.resolveCalendar(a)
        if (name == "create_appointment_series") {
            val ids = JSONArray(); val zone = ZoneId.of(a.getString("timezone")); val starts = AppointmentRules.instant(a.getString("starts_at")).atZone(zone)
            val ends = if (AppointmentRules.openEnd(a)) null else AppointmentRules.instant(a.getString("ends_at")).atZone(zone)
            for (i in 0 until a.getInt("occurrences")) {
                val weeks = i.toLong() * a.getInt("interval_weeks")
                val occurrence = JSONObject(a.toString()).put("series_id", "series_" + PixelMcpWriteTools.hash(request).substring(0, 32))
                    .put("starts_at", AppointmentRules.local(starts.toLocalDateTime().plusWeeks(weeks).toString(), a.getString("timezone"), "").toString())
                    .put("ends_at", if (ends == null) "" else AppointmentRules.local(ends.toLocalDateTime().plusWeeks(weeks).toString(), a.getString("timezone"), "").toString())
                val row = AppointmentRules.prepare("create_appointment", occurrence, all, request + "_" + i, stamp)
                repository.checkBusy(row); repository.enqueue("appointment", row, request + "_" + i)
                OwnerJournalRules.replace(all, "appointments", row.getString("id"), DriveJournalRules.clean(row)); ids.put(row.getString("id"))
            }
            return JSONObject().put("appointment_ids", ids).put("local_state", "local_saved").put("cloud_confirmed", false)
        }
        if (name == "update_appointment") {
            val updates = a.getJSONObject("updates"); val before = PixelMcpWriteTools.find(all.getJSONArray("appointments"), a.getString("appointment_id"))
            if (updates.has("location") && updates.optString("location") != before.optString("location") && !updates.has("latitude") && !updates.has("longitude")) updates.put("latitude", JSONObject.NULL).put("longitude", JSONObject.NULL)
        }
        val row = AppointmentRules.prepare(name, a, all, request, stamp)
        repository.checkBusy(row)
        return repository.enqueue("appointment", row, request).put("appointment_id", row.getString("id"))
    }
}
