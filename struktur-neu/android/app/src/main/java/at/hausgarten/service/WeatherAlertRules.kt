package at.hausgarten.service

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.*

/** Shared, testable wording and delivery decisions. Never changes a planned job. */
internal object WeatherAlertRules {
    @JvmStatic
    @Throws(Exception::class)
    fun actionable(appointment: JSONObject, risk: JSONObject, now: Instant): Boolean {
        if (
            !AppointmentRules.active(appointment) ||
                appointment.optString("status") == "draft" ||
                !AppointmentRules.pending(appointment, now)
        )
            return false
        return risk.optString("severity") in listOf("difficult", "critical") &&
            risk.optString("alert_hash").matches(Regex("[0-9a-f]{64}"))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun shouldNotify(
        appointment: JSONObject,
        risk: JSONObject,
        now: Instant,
        acknowledged: String,
        notified: String,
    ): Boolean {
        if (!actionable(appointment, risk, now)) return false
        val hash = risk.getString("alert_hash")
        if (hash == acknowledged || hash == notified) return false
        val hour = now.atZone(ZoneId.of("Europe/Vienna")).hour
        return hour in 6 until 21 || risk.optBoolean("critical_official_warning")
    }

    @JvmStatic
    @Throws(Exception::class)
    fun message(appointment: JSONObject, risk: JSONObject, customer: String?): JSONObject {
        val reasons = risk.optJSONArray("reasons")
        val all = reasons?.toString()?.lowercase(Locale.GERMAN) ?: ""
        val topics = ArrayList<String>()
        for ((word, label) in
            arrayOf(
                "gewitter" to "Gewitter",
                "böen" to "Böen",
                "wind" to "Wind",
                "schnee" to "Schnee / Eis",
                "glatteis" to "Glatteis",
                "frost" to "Frost",
                "hitze" to "Hitze",
                "regen" to "Regen",
                "niederschlag" to "Niederschlag",
            )) if (
            all.contains(word) &&
                !(word == "wind" && topics.contains("Böen")) &&
                !(word == "niederschlag" && topics.contains("Regen"))
        )
            topics.add(label)
        val title =
            (if (risk.optString("severity") == "critical") "Wetterwarnung: "
            else "Wetterhinweis: ") +
                (if (topics.isEmpty()) "Einsatz prüfen" else topics.take(3).joinToString(" · "))
        val whenText =
            DateTimeFormatter.ofPattern("EEE, dd.MM. HH:mm", Locale.GERMAN)
                .withZone(ZoneId.of(appointment.optString("timezone", "Europe/Vienna")))
                .format(AppointmentRules.instant(appointment.getString("starts_at")))
        var reason = reasons?.optString(0, risk.optString("label")) ?: risk.optString("label")
        if (reasons != null)
            for (i in 0 until reasons.length()) if (
                reasons.optString(i).startsWith("GeoSphere-Warnung:")
            ) {
                reason = reasons.getString(i)
                break
            }
        val area =
            if (risk.optString("location_precision") == "locality")
                "\nOrtsvorhersage · " + risk.optString("location_area")
            else ""
        val body =
            (if (customer.isNullOrBlank()) "Geplanter Einsatz" else customer.trim()) +
                " · " +
                whenText +
                "\n" +
                reason +
                area
        return JSONObject()
            .put("title", title)
            .put("body", body)
            .put("detail", appointment.optString("title") + "\n" + body)
    }
}
