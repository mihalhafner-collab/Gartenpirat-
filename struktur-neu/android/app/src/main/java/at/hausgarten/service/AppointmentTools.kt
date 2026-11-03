package at.hausgarten.service

import org.json.*
import java.time.*

internal object AppointmentTools {
    @JvmField val READS = arrayOf("get_planning_settings", "get_planning_context", "list_appointments", "get_appointment", "suggest_appointment_slots", "get_workweek_weather", "get_weather_risks")
    @JvmField val WRITES = arrayOf("update_planning_settings", "request_weather_refresh", "create_appointment", "create_appointment_series", "resolve_calendar_conflict", "update_appointment", "cancel_appointment", "acknowledge_weather_alert", "request_calendar_sync")
    @JvmStatic fun read(name: String): Boolean = name in READS
    @JvmStatic fun write(name: String): Boolean = name in WRITES
    @JvmStatic @Throws(Exception::class) fun fields(): JSONObject {
        val properties = JSONObject()
        for (key in AppointmentRules.FIELDS) properties.put(key, McpFields.text(4000))
        for (key in arrayOf("latitude", "longitude")) properties.put(key, JSONObject().put("type", JSONArray().put("number").put("null")).put("minimum", if (key == "latitude") -90 else -180).put("maximum", if (key == "latitude") 90 else 180))
        for (key in arrayOf("travel_minutes", "break_minutes")) properties.put(key, McpFields.number(0, 720))
        properties.put("status", McpFields.enumeration("draft", "tentative", "confirmed", "completed", "cancelled")).put("purpose", McpFields.enumeration("work", "inspection")).put("weather_profile", McpFields.enumeration(*AppointmentRules.PROFILES))
        return properties.put("scope_refs", JSONObject().put("type", "array").put("maxItems", 200).put("items", McpFields.text(200)))
    }
    @JvmStatic @Throws(Exception::class) fun schema(name: String): JSONObject {
        val properties = JSONObject(); val required = ArrayList<String>()
        if (write(name)) { properties.put("request_id", McpFields.text(128).put("minLength", 8)); required.add("request_id") }
        if (name == "update_planning_settings") {
            val fields = JSONObject().put("workdays", JSONObject().put("type", "array").put("minItems", 1).put("maxItems", 7).put("items", McpFields.number(1, 7)))
                .put("day_start", McpFields.text(5)).put("day_end", McpFields.text(5)).put("travel_minutes", McpFields.number(0, 720))
            for (key in arrayOf("calendar_enabled", "weather_enabled", "weather_license_confirmed", "notifications")) fields.put(key, McpFields.bool())
            for (key in arrayOf("rain_mm", "rain_probability", "gust_kmh", "frost_c", "heat_c")) fields.put(key, JSONObject().put("type", "number").put("minimum", if (key == "frost_c") -30 else 0).put("maximum", if (key == "rain_probability") 100 else 200))
            properties.put("expected_record_hash", McpFields.hash()).put("updates", McpFields.`object`(fields).put("minProperties", 1)); required.addAll(listOf("expected_record_hash", "updates"))
        }
        if (name in listOf("get_appointment", "update_appointment", "cancel_appointment", "acknowledge_weather_alert")) { properties.put("appointment_id", McpFields.id()); required.add("appointment_id") }
        if (name == "create_appointment" || name == "create_appointment_series") {
            val fields = fields(); for (key in fields.keys()) properties.put(key, fields.get(key))
            required.addAll(listOf("customer_id", "title", "starts_at", "ends_at", "timezone", "weather_profile"))
        }
        if (name == "create_appointment_series") { properties.put("occurrences", McpFields.number(2, 26)).put("interval_weeks", McpFields.number(1, 12)); required.addAll(listOf("occurrences", "interval_weeks")) }
        if (name == "resolve_calendar_conflict") {
            properties.put("appointment_id", McpFields.id()).put("expected_record_hash", McpFields.hash()).put("expected_conflict_hash", McpFields.hash())
                .put("resolution", McpFields.enumeration("keep_app", "use_google", "keep_without_calendar")).put("confirmed", McpFields.bool().put("enum", JSONArray().put(true)))
            required.addAll(listOf("appointment_id", "expected_record_hash", "expected_conflict_hash", "resolution", "confirmed"))
        }
        if (name == "update_appointment") { properties.put("updates", McpFields.`object`(fields()).put("minProperties", 1)); required.add("updates") }
        if (name == "update_appointment" || name == "cancel_appointment") { properties.put("expected_record_hash", McpFields.hash()); required.add("expected_record_hash") }
        if (name == "acknowledge_weather_alert") { properties.put("expected_alert_hash", McpFields.hash()); required.add("expected_alert_hash") }
        if (name == "list_appointments") properties.put("offset", McpFields.number(0, 1000000)).put("limit", McpFields.number(1, 100))
        if (name in listOf("get_planning_context", "list_appointments", "suggest_appointment_slots", "get_workweek_weather", "get_weather_risks")) for (key in arrayOf("customer_id", "order_id", "date_from", "date_to")) properties.put(key, McpFields.text(128))
        if (name == "suggest_appointment_slots") { properties.put("duration_minutes", McpFields.number(5, 720)).put("weather_profile", McpFields.enumeration(*AppointmentRules.PROFILES)); required.addAll(listOf("customer_id", "duration_minutes", "date_from", "date_to")) }
        return McpFields.`object`(properties, *required.toTypedArray())
    }
    @JvmStatic @Throws(Exception::class) fun tools(writable: Boolean): JSONArray {
        val out = JSONArray(); val names = READS.toMutableList(); if (writable) names.addAll(WRITES)
        for (name in names) out.put(JSONObject().put("name", name).put("description", if (write(name)) "Verwaltet Einsätze der bestehenden App. Nur auf konkrete Nutzeranweisung, keine Ist-Stunden/Rechnung/Einladung. request_id bei Retry beibehalten; Updates mit aktuellem record_hash. Kalenderbestätigung getrennt beachten." else "Liest App-Planung und zuletzt geprüfte Kalender-/Wetterdaten. Unbekannte oder veraltete Verfügbarkeit ist keine freie Zeit. Kunden/Ort/Dauer nicht erfinden; Vorschläge buchen nichts.")
            .put("inputSchema", schema(name)).put("annotations", JSONObject().put("readOnlyHint", read(name)).put("idempotentHint", true).put("destructiveHint", name == "cancel_appointment").put("openWorldHint", false)))
        return out
    }
    @JvmStatic @Throws(Exception::class) fun query(name: String, args: JSONObject, state: JSONObject): JSONObject {
        McpSchema.validate(args, schema(name)); val effective = PixelMcpReadTools.effective(state)
        if (name == "get_appointment") {
            val row = PixelMcpWriteTools.find(effective.getJSONArray("appointments"), args.getString("appointment_id")); val work = JSONArray(); var actual = 0.0
            val logs = effective.getJSONArray("worklogs")
            for (i in 0 until logs.length()) {
                val log = logs.getJSONObject(i)
                if (row.getString("id") == log.optString("appointment_id")) { actual += log.optDouble("hours", 0.0); if (work.length() < 50) work.put(log) }
            }
            return JSONObject().put("appointment", row).put("record_hash", AppointmentRules.hash(row)).put("actual_hours", Math.round(actual * 100) / 100.0).put("actual_work", work)
                .put("duration_unknown", AppointmentRules.openEnd(row)).put("planned_hours", if (AppointmentRules.openEnd(row)) JSONObject.NULL else (Duration.between(AppointmentRules.instant(row.getString("starts_at")), AppointmentRules.instant(row.getString("ends_at"))).toMinutes() - row.optInt("break_minutes")) / 60.0)
        }
        val found = JSONArray(); val all = AppointmentRules.sorted(effective); var total = 0
        for (i in 0 until all.length()) {
            val row = all.getJSONObject(i)
            if (args.optString("customer_id").isNotEmpty() && args.getString("customer_id") != row.optString("customer_id")) continue
            if (args.optString("order_id").isNotEmpty() && args.getString("order_id") != row.optString("order_id")) continue
            val day = AppointmentRules.instant(row.getString("starts_at")).atZone(ZoneId.of(row.optString("timezone", "Europe/Vienna"))).toLocalDate().toString()
            if (args.optString("date_from").isNotEmpty() && day < args.getString("date_from")) continue
            if (args.optString("date_to").isNotEmpty() && day > args.getString("date_to")) continue
            if (total++ >= args.optInt("offset") && found.length() < args.optInt("limit", 50)) found.put(JSONObject().put("appointment", row).put("record_hash", AppointmentRules.hash(row)))
        }
        val result = JSONObject().put("appointments", found).put("total", total).put("calendar_availability_verified", false)
        if (name == "get_planning_context") {
            val customer = if (args.optString("customer_id").isEmpty()) null else PixelMcpWriteTools.find(effective.getJSONArray("customers"), args.getString("customer_id"))
            result.put("customer", customer ?: JSONObject.NULL)
            if (args.optString("order_id").isNotEmpty()) {
                val order = PixelMcpWriteTools.find(effective.getJSONArray("orders"), args.getString("order_id"))
                require(customer == null || order.optString("customer_id") == customer.getString("id")) { "Kunde und Auftrag passen nicht zusammen." }
                result.put("order", order).put("order_hash", OwnerJournalRules.version(order))
                if (order.optString("offer_id").isNotEmpty()) result.put("offer", PixelMcpWriteTools.find(effective.getJSONArray("offers"), order.getString("offer_id")))
            }
            result.put("missing", JSONArray().put("duration_may_remain_unknown_with_ends_at_empty_string;customer_availability_must_be_confirmed"))
        }
        return result
    }
}
