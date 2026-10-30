package at.hausgarten.service

import org.json.*

/** Pure event projection and three-way comparison; optional customer name, no public internal IDs. */
internal object CalendarRules {
    @JvmField val SHARED = arrayOf("title", "location", "starts_at", "ends_at", "timezone", "status")
    @JvmStatic @Throws(Exception::class) fun eventId(appointmentId: String): String = "hg" + PixelMcpWriteTools.hash("hausgarten.appointment.v1:" + appointmentId)
    @JvmStatic @Throws(Exception::class) fun event(a: JSONObject): JSONObject {
        val open = AppointmentRules.openEnd(a)
        // Google requires an end even when endTimeUnspecified=true. Never import this marker as a planned duration.
        val end = if (open) AppointmentRules.instant(a.getString("starts_at")).plusSeconds(60).toString() else a.getString("ends_at")
        return JSONObject().put("id", eventId(a.getString("id"))).put("summary", a.getString("title")).put("location", a.optString("location"))
            .put("start", JSONObject().put("dateTime", a.getString("starts_at")).put("timeZone", a.optString("timezone", "Europe/Vienna")))
            .put("end", JSONObject().put("dateTime", end).put("timeZone", a.optString("timezone", "Europe/Vienna"))).put("endTimeUnspecified", open)
            .put("visibility", "private").put("transparency", "opaque").put("status", if (a.optString("status") == "tentative") "tentative" else "confirmed")
            .put("extendedProperties", JSONObject().put("private", JSONObject().put("hg_appointment_id", a.getString("id"))))
    }
    @JvmStatic @Throws(Exception::class) fun event(a: JSONObject, customer: String?): JSONObject {
        val event = event(a)
        if (!customer.isNullOrBlank()) {
            val prefix = customer.trim() + " · "
            event.put("summary", prefix + a.getString("title"))
            event.getJSONObject("extendedProperties").getJSONObject("private").put("hg_customer_prefix", prefix)
        }
        event.put("description", (if (AppointmentRules.openEnd(a)) "Ende offen – Zeitaufwand noch ungeklärt. Keine feste Dauer reserviert.\n\n" else "") + a.optString("notes"))
        return event
    }
    @JvmStatic @Throws(Exception::class) fun title(remote: JSONObject): String {
        val title = remote.optString("summary")
        val prefix = remote.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("hg_customer_prefix") ?: ""
        return if (prefix.isNotEmpty() && title.startsWith(prefix)) title.substring(prefix.length) else title
    }
    @JvmStatic @Throws(Exception::class) fun requireIdentity(event: JSONObject, id: String) {
        val props = event.optJSONObject("extendedProperties")?.optJSONObject("private")
        if (props == null || id != props.optString("hg_appointment_id") || eventId(id) != event.optString("id"))
            throw PixelMcpWriteTools.Rejected("CALENDAR_ID_CONFLICT", "Kalendereintrag hat keine passende App-Identität. Keine Änderung vorgenommen.")
    }
    @JvmStatic @Throws(Exception::class) fun changes(a: JSONObject, base: JSONObject, remote: JSONObject): JSONObject {
        if (remote.optJSONArray("recurrence") != null || remote.optString("recurringEventId").isNotEmpty())
            throw PixelMcpWriteTools.Rejected("CALENDAR_FORMAT_CONFLICT", "Google-Serie separat prüfen. App-Wiederholungen sind ausdrücklich geplante einzelne Einsätze.")
        requireIdentity(remote, a.getString("id"))
        if (remote.optString("status") == "cancelled") throw PixelMcpWriteTools.Rejected("CALENDAR_REMOVED", "Google-Eintrag wurde entfernt. Termin in der App prüfen; keine automatische Neuanlage.")
        val next = JSONObject(a.toString())
        val external = JSONObject().put("title", title(remote)).put("location", remote.optString("location"))
        for ((key, edge) in arrayOf("starts_at" to "start", "ends_at" to "end")) {
            val date = remote.optJSONObject(edge)
            if (date == null || date.optString("dateTime").isEmpty()) throw PixelMcpWriteTools.Rejected("CALENDAR_FORMAT_CONFLICT", "Ganztägigen/ungültigen Google-Eintrag in der App prüfen.")
            external.put(key, AppointmentRules.instant(date.getString("dateTime")).toString())
        }
        if (remote.optBoolean("endTimeUnspecified")) external.put("ends_at", "")
        external.put("timezone", remote.getJSONObject("start").optString("timeZone", a.optString("timezone", "Europe/Vienna")))
        for (key in arrayOf("title", "location", "starts_at", "ends_at", "timezone")) {
            val before = base.optString(key); val local = a.optString(key); val value = external.optString(key)
            if (value != before) {
                if (local != before && local != value) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Termin wurde in App und Google unterschiedlich geändert. Beide Varianten bleiben erhalten.")
                next.put(key, value)
            }
        }
        return next
    }
    @JvmStatic @Throws(Exception::class) fun projectionHash(a: JSONObject): String {
        val projection = JSONObject()
        for (key in SHARED) projection.put(key, a.opt(key) ?: JSONObject.NULL)
        return PixelMcpWriteTools.hash(projection)
    }
}
