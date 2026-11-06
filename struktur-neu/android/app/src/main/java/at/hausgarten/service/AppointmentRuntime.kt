package at.hausgarten.service

import android.content.Context
import android.os.Build
import org.json.*
import java.time.*
import java.util.Random

/** Private device adapters. Business state is always changed through the guarded journal. */
internal object AppointmentRuntime {
    private val LOCK = Any()
    @JvmStatic @Throws(Exception::class) fun request(context: Context, store: PixelLocalStore): JSONObject {
        val configured = calendarEnabled(context, store) || flag(context, store, "weather_enabled")
        if (configured) {
            PlanningAccount.prefs(context).edit().putInt("calendar_failures", 0).putInt("weather_failures", 0).putBoolean("weather_blocked", false).remove("calendar_retry_at").remove("weather_retry_at").apply()
            val master = PixelMcpReadTools.effective(store.getState()); val all = master.getJSONArray("appointments")
            for (i in 0 until all.length()) {
                val appointment = weatherLocation(store, all.getJSONObject(i), master)
                if (flag(context, store, "weather_enabled") && WeatherLocationRules.retryable(appointment.optString("weather_location_state"))) {
                    val key = WeatherLocationRules.cacheKey(appointment); val cached = JSONObject(store.ownerValue(key, "{}"))
                    cached.remove("attempted_at"); cached.put("state", "pending"); store.putOwnerValue(key, cached.toString())
                }
            }
        }
        return JSONObject().put("state", if (configured) "queued" else "configuration_required").put("confirmed", false).put("integration", integration(context, store))
    }
    @JvmStatic @Throws(Exception::class) fun settings(store: PixelLocalStore): JSONObject {
        val settings = JSONObject(store.ownerValue("planning:settings", "{}"))
        if (!settings.has("workdays")) settings.put("workdays", JSONArray(intArrayOf(1, 2, 3, 4, 5)))
        if (!settings.has("day_start")) settings.put("day_start", "08:00")
        if (!settings.has("day_end")) settings.put("day_end", "17:00")
        if (!settings.has("travel_minutes")) settings.put("travel_minutes", 0)
        return settings
    }
    @JvmStatic @Throws(Exception::class) fun publicSettings(context: Context, store: PixelLocalStore): JSONObject {
        val settings = settings(store); val preferences = PlanningAccount.prefs(context)
        for (key in arrayOf("weather_enabled", "weather_license_confirmed", "notifications")) if (!settings.has(key)) settings.put(key, preferences.getBoolean(key, false))
        if (!settings.has("calendar_enabled")) settings.put("calendar_enabled", PlanningAccount.connected(context))
        return settings
    }
    @JvmStatic fun flag(context: Context, store: PixelLocalStore, key: String): Boolean = try { publicSettings(context, store).optBoolean(key) } catch (unavailable: Exception) { false }
    @JvmStatic fun calendarEnabled(context: Context, store: PixelLocalStore): Boolean = PlanningAccount.connected(context) && flag(context, store, "calendar_enabled")
    @JvmStatic @Throws(Exception::class) fun setCalendarEnabled(context: Context, store: PixelLocalStore, enabled: Boolean, expected: String) {
        require(!enabled || PlanningAccount.connected(context)) { "Google Kalender zuerst am Gerät verbinden." }
        val settings = settings(store); settings.put("calendar_enabled", enabled); saveSettings(context, store, settings, expected)
        if (enabled) PlanningAccount.prefs(context).edit().putBoolean("calendar_blocked", false).putInt("calendar_failures", 0).remove("calendar_retry_at").apply()
        PlanningJob.schedule(context, 1000)
    }
    @JvmStatic @Throws(Exception::class) fun saveSettings(context: Context, store: PixelLocalStore, settings: JSONObject, expected: String) {
        val database = store.writableDatabase; database.beginTransaction()
        try {
            if (PixelMcpWriteTools.hash(publicSettings(context, store)) != expected) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Planungseinstellungen wurden inzwischen geändert. Angaben bleiben erhalten; neu prüfen.")
            saveSettings(store, settings); database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }
    @JvmStatic @Throws(Exception::class) fun saveSettings(store: PixelLocalStore, settings: JSONObject) {
        val start = LocalTime.parse(settings.getString("day_start")); val end = LocalTime.parse(settings.getString("day_end"))
        require(end.isAfter(start)) { "Ende muss nach Beginn liegen." }
        val days = settings.getJSONArray("workdays"); val seen = HashSet<Int>()
        for (i in 0 until days.length()) require(days.getInt(i) in 1..7 && seen.add(days.getInt(i))) { "Ungültige Arbeitstage." }
        require(days.length() > 0) { "Mindestens einen Arbeitstag wählen." }
        require(settings.optInt("travel_minutes") in 0..720) { "Anfahrtpuffer ungültig." }
        for (key in arrayOf("rain_mm", "rain_probability", "gust_kmh", "frost_c", "heat_c")) if (settings.has(key))
            require(settings.getDouble(key).isFinite() && settings.getDouble(key) >= (if (key == "frost_c") -30 else 0) && settings.getDouble(key) <= 200) { "Wetterschwelle ungültig." }
        require(!settings.has("rain_probability") || settings.getDouble("rain_probability") <= 100) { "Regenwahrscheinlichkeit muss zwischen0 und100 liegen." }
        store.putOwnerValue("planning:settings", settings.toString())
    }
    @JvmStatic @Throws(Exception::class) fun integration(context: Context, store: PixelLocalStore): JSONObject {
        val preferences = PlanningAccount.prefs(context); val status = JSONObject(store.ownerValue("planning:status", "{}"))
        val busyEnabled = preferences.getBoolean("read_busy", false); val busy = availability(context, store, Instant.now())
        val busyState = if (!PlanningAccount.connected(context)) "not_connected" else if (!calendarEnabled(context, store)) "paused" else if (!busyEnabled) "disabled" else if (busy.optBoolean("verified")) "verified" else "unverified"
        status.put("calendar_busy_enabled", busyEnabled).put("calendar_busy_state", busyState).put("calendar_busy_scope", "primary")
            .put("calendar_busy_checked_at", busy.optString("checked_at")).put("calendar_busy_from", busy.optString("from")).put("calendar_busy_to", busy.optString("to"))
        status.put("calendar_last_success_at", store.ownerValue("calendar:last_success_at", "")).put("calendar_last_attempt_at", store.ownerValue("calendar:last_attempt_at", ""))
            .put("calendar_next_retry_at", preferences.getString("calendar_retry_at", "")).put("calendar_setup_attempt_at", preferences.getString("calendar_setup_attempt_at", "")).put("calendar_setup_failed_at", preferences.getString("calendar_setup_failed_at", ""))
        val setup = preferences.getString("calendar_setup_code", "") ?: ""
        status.put("calendar_setup_stage", if (setup.isEmpty()) "" else CalendarAccessRules.phase(preferences.getString("calendar_setup_stage", "grant")))
        if (preferences.contains("calendar_setup_result_code")) status.put("calendar_setup_result_code", preferences.getInt("calendar_setup_result_code", 0))
        return status.put("calendar_setup_error_code", CalendarAccessRules.safeCode(preferences.getString("calendar_setup_code", "")))
            .put("calendar_error_code", CalendarAccessRules.safeCode(if (PlanningAccount.connected(context)) store.ownerValue("calendar:last_error_code", "") else preferences.getString("calendar_setup_code", "")))
            .put("calendar_connected", PlanningAccount.connected(context)).put("calendar_configured", calendarEnabled(context, store)).put("weather_configured", flag(context, store, "weather_enabled"))
            .put("calendar_state", store.ownerValue("calendar:status", if (calendarEnabled(context, store)) "pending" else "not_connected"))
            .put("weather_state", store.ownerValue("weather:status", if (flag(context, store, "weather_enabled")) "pending" else "not_configured"))
            .put("calendar_registration_required", setup == "CALENDAR_APP_REGISTRATION_REQUIRED")
            .put("calendar_setup_next_action", if (setup == "CALENDAR_APP_REGISTRATION_REQUIRED" || setup == "CALENDAR_API_DISABLED") "google_project_configuration" else if (!PlanningAccount.connected(context) || store.ownerValue("calendar:status", "") == "authorization_required") "authorize_on_device" else "sync")
            .put("weather_provider", OpenMeteoWeatherSource.PROVIDER).put("weather_use_mode", "non_commercial").put("weather_requested_forecast_days", OpenMeteoWeatherSource.FORECAST_DAYS)
            .put("weather_optional", true).put("weather_business_use_status", "unresolved").put("weather_business_use_confirmed", false).put("weather_terms_url", OpenMeteoWeatherSource.TERMS)
            .put("notification_permission", Build.VERSION.SDK_INT < 33 || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED)
            .put("notification_state", WeatherNotifications.state(context)).put("weather_notifications_enabled", flag(context, store, "notifications")).put("notifications_available", WeatherNotifications.ready(context))
            .put("background_best_effort", true).put("credentials_exposed", false)
    }
    @JvmStatic @Throws(Exception::class) fun weatherLocation(store: PixelLocalStore, appointment: JSONObject): JSONObject = weatherLocation(store, appointment, PixelMcpReadTools.effective(store.getState()))
    @JvmStatic @Throws(Exception::class) fun weatherLocation(appointment: JSONObject, customers: JSONArray): JSONObject = WeatherLocationRules.context(appointment, customers, JSONArray())
    @Throws(Exception::class) private fun weatherLocation(store: PixelLocalStore, appointment: JSONObject, master: JSONObject): JSONObject {
        var value = WeatherLocationRules.context(appointment, master.getJSONArray("customers"), master.getJSONArray("orders"))
        if (!WeatherLocationRules.coordinates(value) && value.optString("weather_location_label").isNotEmpty()) value = WeatherLocationRules.cached(value, JSONObject(store.ownerValue(WeatherLocationRules.cacheKey(value), "{}")))
        return value
    }
    @JvmStatic @Throws(Exception::class) fun risk(store: PixelLocalStore, appointment: JSONObject, now: Instant): JSONObject = riskAtLocation(store, weatherLocation(store, appointment), now)
    @Throws(Exception::class) private fun riskAtLocation(store: PixelLocalStore, appointment: JSONObject, now: Instant): JSONObject {
        var cache: JSONObject? = null
        if (appointment.has("latitude") && !appointment.isNull("latitude")) cache = JSONObject(store.ownerValue("weather:" + WeatherRiskRules.locationKey(appointment), "{}"))
        val risk = WeatherRiskRules.evaluate(appointment, if (cache == null || cache.length() == 0) null else cache, now, settings(store))
        if (cache == null || cache.optString("forecast_provider").isEmpty()) risk.put("source", OpenMeteoWeatherSource.ATTRIBUTION)
        val state = appointment.optString("weather_location_state", "missing")
        risk.put("location_label", appointment.optString("weather_location_label", appointment.optString("location"))).put("location_state", state)
        if (state == "locality_resolved") risk.put("location_precision", "locality").put("location_area", appointment.optString("weather_location_area")).put("location_notice", "Ortsvorhersage: Die genaue Hausadresse wurde nicht erkannt. Wetter am Grundstück kann abweichen.")
        if (!WeatherLocationRules.coordinates(appointment)) {
            val reason = when (state) {
                "ambiguous" -> "Einsatzadresse ist mehrdeutig. Ort oder Postleitzahl ergänzen."
                "unavailable" -> "Adresssuche derzeit nicht verfügbar. Erneut prüfen oder Standort am Einsatzort übernehmen."
                "not_found" -> "Einsatzadresse nicht gefunden. Straße, Postleitzahl und Ort prüfen."
                "missing" -> "Einsatzort fehlt. Adresse im Termin oder beim Kunden ergänzen."
                else -> "Einsatzadresse wird bei der Wetterprüfung ermittelt. Bei Bedarf Jetzt prüfen wählen."
            }
            risk.put("reasons", JSONArray().put(reason))
        }
        return risk
    }
    @JvmStatic @Throws(Exception::class) fun query(context: Context, store: PixelLocalStore, name: String, arguments: JSONObject): JSONObject {
        if (name == "get_planning_settings") {
            McpSchema.validate(arguments, AppointmentTools.schema(name)); val settings = publicSettings(context, store)
            return JSONObject().put("settings", settings).put("record_hash", PixelMcpWriteTools.hash(settings)).put("integration", integration(context, store))
        }
        McpSchema.validate(arguments, AppointmentTools.schema(name)); val now = Instant.now(); var args = arguments
        if (name == "get_workweek_weather" || name == "get_weather_risks") {
            args = JSONObject(args.toString())
            if (!args.has("date_from")) args.put("date_from", now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().toString())
            if (!args.has("date_to")) args.put("date_to", now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().plusDays(14).toString())
        }
        val state = store.getState(); val master = PixelMcpReadTools.effective(state)
        var out = AppointmentTools.query(name, args, state); val availability = availability(context, store, now)
        if (name == "suggest_appointment_slots") out = suggestions(store, master, args, now, availability)
        else if (name == "get_appointment") {
            val appointment = out.getJSONObject("appointment")
            out.put("weather", riskAtLocation(store, weatherLocation(store, appointment, master), now)).put("calendar", calendarState(context, store, appointment)).put("collisions", AppointmentAvailability.assess(appointment, master.getJSONArray("appointments"), availability))
        } else {
            val rows = out.getJSONArray("appointments"); val risks = JSONArray()
            for (i in 0 until rows.length()) {
                val entry = rows.getJSONObject(i); val appointment = entry.getJSONObject("appointment"); val risk = riskAtLocation(store, weatherLocation(store, appointment, master), now)
                entry.put("weather", risk).put("calendar", calendarState(context, store, appointment)).put("collisions", AppointmentAvailability.assess(appointment, master.getJSONArray("appointments"), availability))
                if (risk.optString("severity") != "clear" && risk.optString("severity") != "inactive") risks.put(JSONObject().put("appointment_id", appointment.getString("id")).put("weather", risk)
                    .put("acknowledged", risk.optString("alert_hash").isNotEmpty() && risk.optString("alert_hash") == store.ownerValue("weather:ack:" + appointment.getString("id"), "")))
            }
            out.put("weather_risks", risks)
            if (name == "get_workweek_weather" || name == "get_weather_risks") {
                var actualDays = if (rows.length() == 0) 0 else OpenMeteoWeatherSource.FORECAST_DAYS; var covered = 0; var complete = rows.length() > 0; val sources = LinkedHashSet<String>()
                for (i in 0 until rows.length()) {
                    val risk = rows.getJSONObject(i).getJSONObject("weather"); val available = risk.optBoolean("forecast_available")
                    if (available) { covered++; sources.add(risk.optString("source")) }
                    complete = complete && available && risk.optBoolean("forecast_complete"); actualDays = minOf(actualDays, if (available) risk.optInt("forecast_days") else 0)
                }
                out.put("forecast_days", actualDays).put("requested_forecast_days", OpenMeteoWeatherSource.FORECAST_DAYS).put("forecast_covered_appointments", covered).put("forecast_coverage_complete", complete)
                    .put("next_workdays", nextWorkdays(store, now)).put("source", if (sources.isEmpty()) OpenMeteoWeatherSource.ATTRIBUTION else sources.joinToString(" / ")).put("weather_use_mode", "non_commercial")
            }
        }
        return out.put("availability", availability).put("integration", integration(context, store)).put("planning_settings", settings(store))
    }
    @JvmStatic @Throws(Exception::class) fun availability(context: Context, store: PixelLocalStore, now: Instant): JSONObject {
        val enabled = calendarEnabled(context, store) && PlanningAccount.prefs(context).getBoolean("read_busy", false) && store.ownerValue("calendar:status", "") == "checked"
        return AppointmentAvailability.context(JSONObject(store.ownerValue("calendar:busy", "{}")), enabled, now)
    }
    @JvmStatic @Throws(Exception::class) fun bindingKey(context: Context, id: String): String = "calendar:binding:" + PixelMcpWriteTools.hash(PlanningAccount.prefs(context).getString("calendar_id", "")).substring(0, 24) + ":" + id
    @JvmStatic @Throws(Exception::class) fun calendarState(context: Context, store: PixelLocalStore, appointment: JSONObject): JSONObject {
        val binding = JSONObject(store.ownerValue(bindingKey(context, appointment.getString("id")), "{}"))
        val matching = binding.has("base") && AppointmentRules.hash(appointment) == AppointmentRules.hash(binding.getJSONObject("base"))
        return JSONObject().put("state", if (!calendarEnabled(context, store)) "not_connected" else if (binding.has("conflict")) "review_required" else if (binding.optBoolean("detached")) "detached" else if (matching) binding.optString("state", "pending") else "pending")
            .put("last_confirmed_at", binding.optString("confirmed_at")).put("error", binding.optString("error")).put("conflict_hash", if (binding.has("conflict")) PixelMcpWriteTools.hash(binding) else "").put("conflict", binding.opt("conflict") ?: JSONObject.NULL)
    }
    @JvmStatic @Throws(Exception::class) fun nextWorkdays(store: PixelLocalStore, now: Instant): JSONArray {
        val days = HashSet<Int>(); val configured = settings(store).getJSONArray("workdays"); for (i in 0 until configured.length()) days.add(configured.getInt(i))
        val out = JSONArray(); var day = now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate(); var i = 0
        while (i++ < 16 && out.length() < 5) { if (days.contains(day.dayOfWeek.value)) out.put(day.toString()); day = day.plusDays(1) }
        return out
    }
    @JvmStatic @Throws(Exception::class) fun hasOpenEnd(rows: JSONArray): Boolean {
        for (i in 0 until rows.length()) { val row = rows.getJSONObject(i); if (AppointmentRules.openEnd(row) && AppointmentRules.active(row) && row.optString("status") != "draft") return true }
        return false
    }
    @JvmStatic @Throws(Exception::class) fun suggestions(store: PixelLocalStore, state: JSONObject, args: JSONObject, now: Instant, availability: JSONObject): JSONObject {
        PixelMcpWriteTools.find(state.getJSONArray("customers"), args.getString("customer_id"))
        val from = LocalDate.parse(args.getString("date_from")); val to = LocalDate.parse(args.getString("date_to"))
        require(!to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 30) { "Bitte einen Zeitraum bis31Tage wählen." }
        val preferences = settings(store); val busy = availability
        val verified = try {
            busy.optBoolean("verified") && Instant.parse(busy.getString("checked_at")).isAfter(now.minusSeconds(1800)) &&
                AppointmentRules.instant(busy.getString("from")).isBefore(from.atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant().plusSeconds(1)) &&
                !AppointmentRules.instant(busy.getString("to")).isBefore(to.plusDays(1).atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant())
        } catch (ignored: Exception) { false }
        val found = ArrayList<JSONObject>(); val workdays = preferences.getJSONArray("workdays"); val days = HashSet<Int>()
        for (i in 0 until workdays.length()) days.add(workdays.getInt(i))
        val prior = if (args.optString("order_id").isEmpty()) null else PixelMcpWriteTools.find(state.getJSONArray("orders"), args.getString("order_id"))
        require(prior == null || args.getString("customer_id") == prior.getString("customer_id")) { "Kunde und Auftrag passen nicht zusammen." }
        // Reuse only confirmed coordinates of this exact customer and location.
        val customer = PixelMcpWriteTools.find(state.getJSONArray("customers"), args.getString("customer_id"))
        val expectedPlace = if (prior != null && prior.optString("location").trim().isNotEmpty()) prior.optString("location").trim() else WeatherLocationRules.address(customer)
        var location: JSONObject? = null; val appointments = state.getJSONArray("appointments")
        for (i in 0 until appointments.length()) {
            val row = appointments.getJSONObject(i)
            if (args.getString("customer_id") == row.optString("customer_id") && row.has("latitude") && !row.isNull("latitude") && expectedPlace.isNotEmpty() && WeatherLocationRules.normalized(expectedPlace) == WeatherLocationRules.normalized(row.optString("location"))) location = row
        }
        var day = from
        while (!day.isAfter(to)) {
            if (days.contains(day.dayOfWeek.value)) {
                val first = day.atTime(LocalTime.parse(preferences.getString("day_start"))).atZone(ZoneId.of("Europe/Vienna"))
                val end = day.atTime(LocalTime.parse(preferences.getString("day_end"))).atZone(ZoneId.of("Europe/Vienna"))
                var time = first
                while (!time.plusMinutes(args.getInt("duration_minutes").toLong()).isAfter(end)) {
                    val candidateTime = time; time = time.plusMinutes(30)
                    if (candidateTime.toInstant().isBefore(now)) continue
                    val row = JSONObject().put("id", "suggestion").put("customer_id", args.getString("customer_id")).put("order_id", args.optString("order_id"))
                        .put("title", if (prior == null) "Geplanter Einsatz" else prior.optString("title")).put("starts_at", candidateTime.toInstant().toString())
                        .put("ends_at", candidateTime.plusMinutes(args.getInt("duration_minutes").toLong()).toInstant().toString()).put("timezone", "Europe/Vienna").put("status", "tentative")
                        .put("weather_profile", args.optString("weather_profile", "general")).put("travel_minutes", preferences.optInt("travel_minutes"))
                    row.put("location", expectedPlace)
                    if (location != null) row.put("latitude", location.get("latitude")).put("longitude", location.get("longitude")).put("location", location.optString("location"))
                    try { AppointmentRules.validate(row, null, state) } catch (blocked: Exception) { continue }
                    if (!AppointmentAvailability.assess(row, state.getJSONArray("appointments"), JSONObject()).optBoolean("app_checked", true)) continue
                    var clash = false; val periods = busy.optJSONArray("busy")
                    for (j in 0 until (if (verified) periods?.length() ?: 0 else 0)) {
                        val period = periods!!.getJSONObject(j)
                        if (AppointmentRules.instant(period.getString("start")).isBefore(candidateTime.plusMinutes(args.getInt("duration_minutes").toLong()).toInstant()) && AppointmentRules.instant(period.getString("end")).isAfter(candidateTime.minusMinutes(preferences.optInt("travel_minutes").toLong()).toInstant())) clash = true
                    }
                    if (!clash) found.add(JSONObject().put("appointment", row).put("weather", riskAtLocation(store, weatherLocation(store, row, state), now)).put("calendar_availability_verified", verified).put("customer_availability_confirmed", false))
                }
            }
            day = day.plusDays(1)
        }
        found.sortWith(compareBy<JSONObject> { listOf("clear", "difficult", "unknown", "critical").indexOf(it.getJSONObject("weather").optString("severity")) }.thenBy { it.getJSONObject("appointment").optString("starts_at") })
        return JSONObject().put("suggestions", JSONArray(found.take(5))).put("booked", false).put("calendar_availability_verified", verified)
            .put("notice", if (hasOpenEnd(appointments)) "Offene Endzeiten begrenzen die Zeitfenstersuche. Enden zuerst klären; Kundenverfügbarkeit separat bestätigen." else if (verified) "Kundenverfügbarkeit separat bestätigen. Andere Kalender können sich nach der Prüfung ändern." else "Nur App-Belegung geprüft. Externe Belegtzeiten fehlen oder sind veraltet; keine Buchung ausgeführt.")
    }
    @JvmStatic @Throws(Exception::class) fun acknowledge(context: Context, store: PixelLocalStore, args: JSONObject): JSONObject {
        val row = PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"), args.getString("appointment_id")); val risk = risk(store, row, Instant.now())
        if (risk.optString("alert_hash") != args.getString("expected_alert_hash")) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Wetterhinweis wurde geändert. Bitte neu prüfen.")
        store.putOwnerValue("weather:ack:" + row.getString("id"), risk.getString("alert_hash")); WeatherNotifications.cancel(context, row.getString("id"))
        return JSONObject().put("acknowledged", true).put("appointment_changed", false)
    }
    @JvmStatic @Throws(Exception::class) fun checkBusy(context: Context, store: PixelLocalStore, row: JSONObject): Boolean {
        if (!availability(context, store, Instant.now()).optBoolean("verified")) return false
        if (!AppointmentRules.active(row) || row.optString("status") == "draft") return false
        val report = AppointmentAvailability.assess(row, JSONArray(), availability(context, store, Instant.now())); val periods = report.getJSONArray("conflicts")
        for (i in 0 until periods.length()) if (!periods.getJSONObject(i).optBoolean("uncertain")) throw PixelMcpWriteTools.Rejected("CALENDAR_BUSY", "Zeit oder Anfahrt überschneidet sich mit aktuell geprüften Google-Belegtzeiten.")
        return report.getBoolean("external_checked")
    }
    @JvmStatic @Throws(Exception::class) fun resolve(context: Context, store: PixelLocalStore, args: JSONObject): JSONObject {
        val row = PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"), args.getString("appointment_id"))
        if (AppointmentRules.hash(row) != args.getString("expected_record_hash")) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "App-Termin wurde geändert.")
        val key = bindingKey(context, row.getString("id")); val binding = JSONObject(store.ownerValue(key, "{}"))
        if (!binding.has("conflict") || PixelMcpWriteTools.hash(binding) != args.getString("expected_conflict_hash")) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Kalendervergleich wurde geändert. Neu prüfen.")
        require(args.getBoolean("confirmed")) { "Entscheidung ausdrücklich bestätigen." }
        val choice = args.getString("resolution")
        if (choice == "keep_without_calendar") { binding.put("state", "detached").put("detached", true); binding.remove("conflict"); binding.remove("decision") }
        else binding.put("decision", JSONObject().put("resolution", choice).put("local_hash", AppointmentRules.hash(row))
            .put("remote_hash", PixelMcpWriteTools.hash(if (binding.getJSONObject("conflict").isNull("remote")) JSONObject.NULL else binding.getJSONObject("conflict").get("remote"))))
        store.putOwnerValue(key, binding.toString())
        return JSONObject().put("state", if (choice == "keep_without_calendar") "detached" else "queued").put("business_data_changed", false).put("google_confirmed", false)
    }
    fun interface AdapterTask { @Throws(Exception::class) fun run() }
    @JvmStatic fun refresh(context: Context, store: PixelLocalStore) = synchronized(LOCK) {
        val now = Instant.now(); val weatherEnabled = flag(context, store, "weather_enabled")
        val calendarNext = adapter(context, store, "calendar", calendarEnabled(context, store), now, AdapterTask { syncCalendar(context, store, now) })
        if (Thread.currentThread().isInterrupted) return@synchronized
        val weatherNext = adapter(context, store, "weather", weatherEnabled, now, AdapterTask { refreshWeather(context, store, now) })
        if (Thread.currentThread().isInterrupted) return@synchronized
        var weatherCadence = Long.MAX_VALUE
        if (weatherEnabled) try { weatherCadence = FreeWeatherSource.nextDelayMillis(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"), now) } catch (failure: Exception) { android.util.Log.w("HausGartenPlanning", "WEATHER_CADENCE_FAILED; type=" + failure.javaClass.simpleName) }
        try { notifyRisks(context, store, now); store.putOwnerValue("planning:status", JSONObject().put("last_check_at", now.toString()).put("background_best_effort", true).toString()) } catch (failure: Exception) { android.util.Log.w("HausGartenPlanning", "RISK_NOTIFY_FAILED; type=" + failure.javaClass.simpleName) }
        MainActivity.refreshPlanningView()
        var next = minOf(calendarNext, weatherNext, weatherCadence)
        if (calendarEnabled(context, store)) next = minOf(next, 15 * 60 * 1000L)
        if (next < Long.MAX_VALUE) PlanningJob.schedule(context, next)
    }
    @JvmStatic fun adapter(context: Context, store: PixelLocalStore, kind: String, enabled: Boolean, now: Instant, task: AdapterTask): Long {
        val preferences = PlanningAccount.prefs(context); val prefix = kind + "_"; val calendar = kind == "calendar"; val failures = preferences.getInt(prefix + "failures", 0)
        if (!enabled) return Long.MAX_VALUE
        // Older background cancellation was incorrectly persisted as a revoked grant.
        if (calendar && preferences.getBoolean(prefix + "blocked", false) && store.ownerValue("calendar:last_error_code", "") in listOf("CALENDAR_CONSENT_CANCELLED", "CALENDAR_INTERRUPTED")) preferences.edit().putBoolean(prefix + "blocked", false).apply()
        if (preferences.getBoolean(prefix + "blocked", false) || !calendar && failures >= 8) return Long.MAX_VALUE
        try {
            val retry = preferences.getString(prefix + "retry_at", "") ?: ""
            if (retry.isNotEmpty()) { val remaining = Duration.between(now, Instant.parse(retry)).toMillis(); if (remaining > 0) return remaining }
        } catch (ignored: Exception) {}
        try {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (calendar) store.putOwnerValue("calendar:last_attempt_at", now.toString())
            task.run()
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            preferences.edit().putInt(prefix + "failures", 0).remove(prefix + "retry_at").apply(); store.putOwnerValue(kind + ":status", "checked")
            if (calendar) { store.putOwnerValue("calendar:last_error_code", ""); store.putOwnerValue("calendar:last_success_at", Instant.now().toString()) }
            return Long.MAX_VALUE
        } catch (error: Exception) {
            if (CalendarRecoveryRules.interrupted(error) || Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt(); if (calendar) store.putOwnerValue("calendar:last_error_code", "CALENDAR_INTERRUPTED")
                preferences.edit().putString(prefix + "retry_at", now.plusMillis(CalendarRecoveryRules.MIN_RETRY).toString()).apply(); store.putOwnerValue(kind + ":status", "retry_pending")
                return CalendarRecoveryRules.MIN_RETRY
            }
            if (calendar) store.putOwnerValue("calendar:last_error_code", CalendarAccessRules.code(error))
            if (kind == "weather" && error is HttpTransport.Failure && error.code in listOf("WEATHER_BATCH_PENDING", "WEATHER_RATE_LIMIT")) {
                val delay = maxOf(30000, error.retryAfter); preferences.edit().putString(prefix + "retry_at", now.plusMillis(delay).toString()).apply(); store.putOwnerValue(kind + ":status", "pending"); return delay
            }
            val auth = error is HttpTransport.Failure && error.userAction && !(calendar && error.code == "CALENDAR_CONSENT_CANCELLED")
            if (auth) { preferences.edit().putBoolean(prefix + "blocked", true).apply(); store.putOwnerValue(kind + ":status", "authorization_required"); return Long.MAX_VALUE }
            if (error is PixelMcpWriteTools.Rejected) { store.putOwnerValue(kind + ":status", "review_required"); return Long.MAX_VALUE }
            if (calendar) {
                val delay = CalendarRecoveryRules.retryDelay(failures, if (error is HttpTransport.Failure) error.retryAfter else 0, Random().nextInt(3000))
                preferences.edit().putInt(prefix + "failures", CalendarRecoveryRules.nextFailures(failures)).putString(prefix + "retry_at", now.plusMillis(delay).toString()).apply(); store.putOwnerValue("calendar:status", "retry_pending"); return delay
            }
            var delay = minOf(3600000, 30000L * (1L shl failures)) + Random().nextInt(3000)
            if (error is HttpTransport.Failure) delay = maxOf(delay, error.retryAfter)
            preferences.edit().putInt(prefix + "failures", failures + 1).putString(prefix + "retry_at", now.plusMillis(delay).toString()).apply(); store.putOwnerValue(kind + ":status", if (failures >= 7) "retry_limit_reached" else "retry_pending")
            return if (failures >= 7) Long.MAX_VALUE else delay
        }
    }
    @JvmStatic @Throws(Exception::class) fun syncCalendar(context: Context, store: PixelLocalStore, now: Instant) =
        syncCalendar(context, store, now, PlanningAccount.client(context), PlanningAccount.prefs(context).getString("calendar_id", "") ?: "")
    @JvmStatic @Throws(Exception::class) fun syncCalendar(context: Context, store: PixelLocalStore, now: Instant, client: CalendarClient, calendar: String) {
        var changes = client.changes(calendar, store.ownerValue("calendar:token", ""))
        if (changes.optBoolean("reset_required")) { store.putOwnerValue("calendar:token", ""); changes = client.changes(calendar, "") }
        if (changes.optBoolean("reset_required")) throw java.io.IOException("Kalenderabgleich benötigt erneutes Lesen.")
        val pendingRemote = JSONObject(store.ownerValue("calendar:dirty", "{}")); val changed = changes.getJSONArray("events")
        for (i in 0 until changed.length()) {
            val event = changed.getJSONObject(i); val appointment = event.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("hg_appointment_id") ?: ""
            if (appointment.isNotEmpty()) {
                val bound = JSONObject(store.ownerValue(bindingKey(context, appointment), "{}"))
                if (!bound.has("conflict") && bound.optString("etag").isNotEmpty() && bound.optString("etag") == event.optString("etag")) continue
            }
            pendingRemote.put(event.getString("id"), true)
        }
        val database = store.writableDatabase; database.beginTransaction()
        try { store.putOwnerValue("calendar:dirty", pendingRemote.toString()); store.putOwnerValue("calendar:token", changes.getString("next_sync_token")); database.setTransactionSuccessful() }
        finally { database.endTransaction() }
        val snapshot = PixelMcpReadTools.effective(store.getState()); val all = snapshot.getJSONArray("appointments"); val index = JSONArray(store.ownerValue("calendar:index", "[]")); val ids = LinkedHashSet<String>()
        for (i in 0 until all.length()) ids.add(all.getJSONObject(i).getString("id"))
        for (i in 0 until index.length()) ids.add(index.getString(i))
        val indexed = HashMap<String, JSONObject>(); for (i in 0 until all.length()) indexed[all.getJSONObject(i).getString("id")] = all.getJSONObject(i)
        var network = 0; var more = false; val deadline = android.os.SystemClock.elapsedRealtime() + 55000
        for (id in ids) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val eventId = CalendarRules.eventId(id); val bindingKey = bindingKey(context, id)
            var binding = JSONObject(store.ownerValue(bindingKey, "{}")); var current = indexed[id]
            if (binding.optBoolean("detached")) continue
            if (current == null) {
                val entry = OwnerJournalRules.archives(snapshot).optJSONObject("appointment:" + id)
                if (entry == null || !binding.has("base")) continue
                current = JSONObject(binding.getJSONObject("base").toString()).put("status", "cancelled")
            }
            if (current.optString("status") == "draft") continue
            if (!binding.has("base") && (current.optString("status") == "cancelled" || !AppointmentRules.pending(current, now))) continue
            val unchanged = binding.has("base") && AppointmentRules.hash(current) == AppointmentRules.hash(binding.getJSONObject("base"))
            if (unchanged && !pendingRemote.has(eventId) && !binding.has("decision")) continue
            if (binding.has("conflict") && !binding.has("decision") && !pendingRemote.has(eventId)) continue
            if (network >= 12 || android.os.SystemClock.elapsedRealtime() > deadline) { more = true; break }
            network++
            val latestBeforeNetwork = PixelMcpReadTools.effective(store.getState()); val latestAppointment = OwnerJournalRules.findOrNull(latestBeforeNetwork.getJSONArray("appointments"), id)
            if (latestAppointment != null) current = latestAppointment else if (!OwnerJournalRules.archives(latestBeforeNetwork).has("appointment:" + id)) continue
            var remote = client.get(calendar, eventId)
            try {
                val decision = binding.optJSONObject("decision"); var force = false
                if (decision != null) {
                    if (decision.getString("local_hash") != AppointmentRules.hash(current) || decision.getString("remote_hash") != PixelMcpWriteTools.hash(remote ?: JSONObject.NULL)) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Vergleich nach Entscheidung geändert. Neu prüfen; nichts überschrieben.")
                    if (decision.getString("resolution") == "use_google") {
                        if (remote == null || remote.optString("status") == "cancelled") throw PixelMcpWriteTools.Rejected("CALENDAR_REMOVED", "Google-Eintrag fehlt. Termin behalten oder ausdrücklich in der App absagen.")
                        val chosen = CalendarRules.changes(current, current, remote); current = importCalendar(context, store, current, chosen, remote)
                        binding.put("base", current).put("etag", remote.getString("etag"))
                    } else force = true
                }
                if (current.optString("status") == "cancelled") {
                    if (remote != null && remote.optString("status") != "cancelled") {
                        CalendarRules.requireIdentity(remote, id)
                        if (!force && binding.has("base") && remote.optString("etag") != binding.optString("etag")) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Google-Termin geändert. Absage erst nach Vergleich bestätigen.")
                        client.remove(calendar, eventId, remote)
                    }
                    binding = JSONObject().put("base", current).put("state", "cancelled").put("confirmed_at", now.toString())
                } else {
                    if (!force && binding.has("base") && (remote == null || remote.optString("status") == "cancelled")) throw PixelMcpWriteTools.Rejected("CALENDAR_REMOVED", "Google-Eintrag entfernt. App-Termin prüfen.")
                    if (remote != null) {
                        CalendarRules.requireIdentity(remote, id); val base = binding.optJSONObject("base")
                        if (base == null) {
                            val wanted = CalendarRules.changes(current, current, remote)
                            if (CalendarRules.projectionHash(wanted) != CalendarRules.projectionHash(current)) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Bereits vorhandener Google-Eintrag weicht ab. Beide Stände prüfen.")
                        } else if (!force && remote.optString("etag") != binding.optString("etag")) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Google-Termin wurde außerhalb der App geändert. Beide Stände bleiben erhalten; Entscheidung erforderlich.")
                    }
                    // Readback imports must pass the current SQLite version/overlap guard.
                    val latest = OwnerJournalRules.findOrNull(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"), id)
                    if (latest == null || AppointmentRules.hash(latest) != AppointmentRules.hash(current)) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "App-Termin während Kalenderprüfung geändert.")
                    val localChanged = force || !binding.has("base") || CalendarRules.projectionHash(current) != CalendarRules.projectionHash(binding.getJSONObject("base"))
                    if (remote == null || localChanged) remote = client.save(calendar, current, remote, PixelMcpWriteTools.find(snapshot.getJSONArray("customers"), current.getString("customer_id")).optString("name"))
                    CalendarRules.requireIdentity(remote!!, id); val readback = CalendarRules.changes(current, current, remote)
                    if (CalendarRules.projectionHash(current) != CalendarRules.projectionHash(readback)) throw PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT", "Google-Rücklesen stimmt nicht mit dem Termin überein.")
                    binding = JSONObject().put("base", current).put("etag", remote.getString("etag")).put("state", "confirmed").put("confirmed_at", now.toString())
                }
            } catch (error: PixelMcpWriteTools.Rejected) {
                binding.remove("decision"); binding.put("state", "review_required").put("error", error.code).put("conflict", JSONObject().put("local", current).put("remote", remote ?: JSONObject.NULL))
            }
            store.putOwnerValue(bindingKey, binding.toString()); pendingRemote.remove(eventId); store.putOwnerValue("calendar:dirty", pendingRemote.toString())
            if (!contains(index, id)) { index.put(id); store.putOwnerValue("calendar:index", index.toString()) }
        }
        if (more) PlanningJob.schedule(context, 3000)
        // Foreign events in the app calendar never create customers or orders.
        val ownEvents = ids.map { CalendarRules.eventId(it) }.toSet(); val iterator = pendingRemote.keys()
        while (iterator.hasNext()) if (iterator.next() !in ownEvents) iterator.remove()
        store.putOwnerValue("calendar:dirty", pendingRemote.toString())
        if (PlanningAccount.prefs(context).getBoolean("read_busy", false)) {
            val from = now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant()
            store.putOwnerValue("calendar:busy", client.busy(JSONArray().put("primary"), from.toString(), from.plusSeconds(16 * 86400L).toString()).toString())
        }
    }
    @Throws(Exception::class) private fun importCalendar(context: Context, store: PixelLocalStore, current: JSONObject, merged: JSONObject, remote: JSONObject): JSONObject {
        val updates = JSONObject()
        for (field in CalendarRules.SHARED) if (field != "status" && merged.optString(field) != current.optString(field)) updates.put(field, merged.get(field))
        if (updates.length() > 0) {
            PixelOwnerAccess(context, store).execute("update_appointment", JSONObject().put("appointment_id", current.getString("id")).put("expected_record_hash", AppointmentRules.hash(current)).put("updates", updates)
                .put("request_id", "calendar-import-" + PixelMcpWriteTools.hash(current.getString("id") + remote.optString("etag"))))
            return PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"), current.getString("id"))
        }
        return current
    }
    private fun contains(list: JSONArray, id: String): Boolean { for (i in 0 until list.length()) if (id == list.optString(i)) return true; return false }
    @JvmStatic @Throws(Exception::class) fun refreshWeather(context: Context, store: PixelLocalStore, now: Instant) = refreshWeather(context, store, now, PlanningWire())
    @JvmStatic @Throws(Exception::class) fun refreshWeather(context: Context, store: PixelLocalStore, now: Instant, upstream: HttpTransport.Wire) =
        refreshWeather(context, store, now, upstream, WeatherLocationResolver.Lookup { query -> WeatherLocationResolver.lookup(context, query) })
    @JvmStatic @Throws(Exception::class) fun refreshWeather(context: Context, store: PixelLocalStore, now: Instant, upstream: HttpTransport.Wire, lookup: WeatherLocationResolver.Lookup) {
        val snapshot = PixelMcpReadTools.effective(store.getState()); val all = snapshot.getJSONArray("appointments")
        val sites = LinkedHashMap<String, Long>(); val locationBudget = WeatherLocationResolver.Budget(); var locationsPending = false
        for (i in 0 until all.length()) {
            val original = all.getJSONObject(i)
            if (!AppointmentRules.active(original) || original.optString("status") == "draft" || !AppointmentRules.pending(original, now) || AppointmentRules.instant(original.getString("starts_at")).isAfter(now.plusSeconds(FreeWeatherSource.MONITOR_WINDOW))) continue
            var appointment = weatherLocation(store, original, snapshot)
            if (!WeatherLocationRules.coordinates(appointment)) appointment = WeatherLocationResolver.resolve(context, store, appointment, now, locationBudget, lookup)
            all.put(i, appointment)
            if (appointment.optString("weather_location_state") == "pending") locationsPending = true
            if (!AppointmentRules.active(appointment) || appointment.optString("status") == "draft" || !appointment.has("latitude") || appointment.isNull("latitude") || !appointment.has("longitude") || appointment.isNull("longitude")) continue
            val start = AppointmentRules.weatherStart(appointment, now); val end = AppointmentRules.weatherEnd(appointment, now)
            if (!end.isAfter(now) || start.isAfter(now.plusSeconds(FreeWeatherSource.MONITOR_WINDOW))) continue
            val location = WeatherRiskRules.locationKey(appointment); val delta = maxOf(0, Duration.between(now, start).seconds)
            val prior = sites[location]; if (prior == null || delta < prior) sites[location] = delta
        }
        val common = JSONObject(store.ownerValue("weather:public-source-cache", "{}")); var requests = 0; val deadline = android.os.SystemClock.elapsedRealtime() + 45000
        val wire = object : HttpTransport.Wire {
            override fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?, maximum: Int): HttpTransport.Reply {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                if (++requests > 12 || android.os.SystemClock.elapsedRealtime() > deadline) throw HttpTransport.Failure("WEATHER_BATCH_PENDING", "Weitere Wetterprüfungen werden später fortgesetzt.", 300000, false)
                val bucket = now.epochSecond / 3600; val budget = JSONObject(store.ownerValue("weather:request-budget", "{}")); val count = if (budget.optLong("hour", -1) == bucket) budget.optInt("count") else 0
                if (count >= 180) throw HttpTransport.Failure("WEATHER_RATE_LIMIT", "Wetterprüfung wird später fortgesetzt.", (3600 - now.epochSecond % 3600) * 1000, false)
                store.putOwnerValue("weather:request-budget", JSONObject().put("hour", bucket).put("count", count + 1).toString())
                if (requests > 1) Thread.sleep(300) // Below public 5 requests/second quota; worker only.
                return upstream.send(method, url, headers, body, maximum)
            }
        }
        var failure: Exception? = null
        // Oldest site first prevents starvation across multiple jobs.
        val ordered = sites.entries.sortedBy { store.ownerValue("weather:attempt:" + it.key, "") }
        for (site in ordered) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val location = site.key; val cache = JSONObject(store.ownerValue("weather:" + location, "{}"))
            for (key in common.keys()) cache.put(key, common.get(key))
            try {
                FreeWeatherSource.refreshNonCommercialSite(location, cache, now, site.value, wire); cache.remove("source_unavailable")
                if (site.value <= 3 * 3600) {
                    var assessment: JSONObject? = null
                    for (i in 0 until all.length()) {
                        val appointment = all.getJSONObject(i)
                        if (location == WeatherRiskRules.locationKey(appointment)) { assessment = WeatherRiskRules.evaluate(appointment, cache, now, settings(store)); if (assessment.optString("severity") != "clear") break }
                    }
                    if (assessment != null && assessment.optString("severity") != "clear") try { FreeWeatherSource.refreshNowcast(location, cache, now, wire); cache.remove("nowcast_status") }
                    catch (unavailable: Exception) {
                        cache.put("nowcast_status", "Kurzfristwetter derzeit nicht verfügbar.")
                        if (CalendarRecoveryRules.interrupted(unavailable) || unavailable is HttpTransport.Failure && unavailable.code in listOf("WEATHER_RATE_LIMIT", "WEATHER_BATCH_PENDING")) throw unavailable
                    }
                }
            } catch (unavailable: Exception) { failure = unavailable; if (unavailable is HttpTransport.Failure && unavailable.userAction) cache.put("source_unavailable", true) }
            finally {
                for (key in cache.keys()) if (key == "licence_checked_at" || key.contains(":metadata")) common.put(key, cache.get(key))
                store.putOwnerValue("weather:public-source-cache", common.toString()); store.putOwnerValue("weather:" + location, cache.toString()); store.putOwnerValue("weather:attempt:" + location, now.toString())
            }
            if (failure is HttpTransport.Failure) break // Respect Retry-After globally.
        }
        if (failure != null) throw failure
        if (locationsPending) throw HttpTransport.Failure("WEATHER_BATCH_PENDING", "Weitere Einsatzadressen werden später geprüft.", 300000, false)
    }
    @JvmStatic @Throws(Exception::class) fun notifyRisks(context: Context, store: PixelLocalStore, now: Instant) {
        if (!flag(context, store, "weather_enabled") || !flag(context, store, "notifications")) { WeatherNotifications.cancelAll(context); store.putOwnerValue("weather:delivery-paused", "true"); return }
        if (!WeatherNotifications.ready(context)) { store.putOwnerValue("weather:delivery-paused", "true"); return }
        val resumed = store.ownerValue("weather:delivery-paused", "false") == "true"
        val snapshot = PixelMcpReadTools.effective(store.getState()); val all = snapshot.getJSONArray("appointments"); val customers = snapshot.getJSONArray("customers"); val active = HashSet<String>()
        for (i in 0 until all.length()) {
            val appointment = all.getJSONObject(i); val id = appointment.getString("id"); val risk = riskAtLocation(store, weatherLocation(store, appointment, snapshot), now)
            if (!WeatherAlertRules.actionable(appointment, risk, now)) {
                WeatherNotifications.cancel(context, id)
                // Once stale/unknown notices are removed a fresh, unacknowledged risk may be delivered again.
                store.putOwnerValue("weather:notified:" + id, "")
                if (risk.optString("severity") == "clear" || risk.optString("severity") == "inactive") { store.putOwnerValue("weather:notified:" + id, ""); store.putOwnerValue("weather:ack:" + id, "") }
                continue
            }
            active.add(id)
            if (resumed) store.putOwnerValue("weather:notified:" + id, "")
            if (!WeatherAlertRules.shouldNotify(appointment, risk, now, store.ownerValue("weather:ack:" + id, ""), store.ownerValue("weather:notified:" + id, ""))) continue
            val customer = OwnerJournalRules.findOrNull(customers, appointment.optString("customer_id"))
            if (WeatherNotifications.post(context, appointment, risk, customer?.optString("name") ?: "")) store.putOwnerValue("weather:notified:" + id, risk.getString("alert_hash"))
        }
        WeatherNotifications.retain(context, active)
        store.putOwnerValue("weather:delivery-paused", "false")
    }
}
