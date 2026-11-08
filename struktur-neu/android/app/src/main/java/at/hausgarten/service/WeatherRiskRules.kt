package at.hausgarten.service

import java.time.*
import java.util.Locale
import org.json.*

/** Planning advice from hourly data; never a safety clearance or automatic reschedule. */
internal object WeatherRiskRules {
    @JvmStatic
    fun locationKey(a: JSONObject): String =
        String.format(Locale.ROOT, "%.3f,%.3f", a.optDouble("latitude"), a.optDouble("longitude"))

    @JvmStatic
    @Throws(Exception::class)
    fun unknown(reason: String): JSONObject =
        JSONObject()
            .put("severity", "unknown")
            .put("label", "Wetter unbekannt")
            .put("reasons", JSONArray().put(reason))
            .put("source", "GeoSphere Austria · CC BY 4.0")
            .put("forecast_only", true)

    @JvmStatic
    @Throws(Exception::class)
    fun number(rows: JSONArray?, index: Int): Double {
        require(rows != null && index >= 0 && index < rows.length() && !rows.isNull(index)) {
            "Wetterparameter fehlt."
        }
        val number = rows.getDouble(index)
        require(number.isFinite()) { "Wetterparameter ungültig." }
        return number
    }

    @JvmStatic
    fun optionalNumber(rows: JSONArray?, index: Int): Double? =
        try {
            if (rows == null || index < 0 || index >= rows.length() || rows.isNull(index)) null
            else rows.getDouble(index).takeIf { it.isFinite() }
        } catch (invalid: Exception) {
            null
        }

    @Throws(Exception::class)
    private fun model(
        a: JSONObject,
        cache: JSONObject?,
        now: Instant,
        settings: JSONObject,
    ): JSONObject {
        if (!AppointmentRules.active(a))
            return JSONObject()
                .put("severity", "inactive")
                .put("reasons", JSONArray())
                .put("label", "Kein aktiver Einsatz")
        if (
            a.isNull("latitude") ||
                !a.has("latitude") ||
                a.isNull("longitude") ||
                !a.has("longitude")
        )
            return unknown("Wetterprüfung noch nicht möglich. Einsatzort ergänzen.")
        if (cache == null) return unknown("Noch keine belastbare Vorhersage verfügbar.")
        if (cache.optBoolean("source_unavailable"))
            return unknown("Wetterdaten derzeit nicht verfügbar.")
        if (cache.optJSONObject("forecast") == null)
            return unknown("Noch keine belastbare Vorhersage verfügbar.")
        val fetched =
            try {
                Instant.parse(cache.getString("fetched_at"))
            } catch (error: Exception) {
                return unknown("Aktualisierungszeit fehlt.")
            }
        val untilStart =
            Duration.between(now, AppointmentRules.instant(a.getString("starts_at"))).seconds
        val maximumAge =
            if (OpenMeteoWeatherSource.PROVIDER == cache.optString("forecast_provider"))
                OpenMeteoWeatherSource.maximumAge(untilStart)
            else if (untilStart > 2 * 86400) 18 * 3600L else 6 * 3600L
        if (fetched.isAfter(now.plusSeconds(300)) || fetched.isBefore(now.minusSeconds(maximumAge)))
            return unknown("Vorhersage veraltet oder Gerätezeit widersprüchlich.")
                .put("checked_at", fetched.toString())
        val hourly = cache.optJSONObject("forecast")?.optJSONObject("hourly")
        if (hourly == null) {
            val early = cache.optJSONObject("early_outlook")
            if (early != null && early.optBoolean("available"))
                return unknown(
                        "Langfristiger 15-Tage-Ausblick geprüft; belastbare Arbeitsbewertung folgt näher am Termin."
                    )
                    .put("checked_at", early.optString("checked_at"))
                    .put("early_outlook_available", true)
            return unknown("Noch keine belastbare Vorhersage für diesen Einsatzort verfügbar.")
        }
        val times = hourly.optJSONArray("time")
        if (times == null || times.length() < 2 || times.length() > 500)
            return unknown("Stundenwerte fehlen oder sind unzulässig.")
        val start = AppointmentRules.weatherStart(a, now)
        val end = AppointmentRules.weatherEnd(a, now)
        val profile = a.optString("weather_profile", "general")
        if (profile == "indoor")
            return JSONObject()
                .put("severity", "clear")
                .put("label", "Wetterarme Tätigkeit")
                .put("reasons", JSONArray())
                .put("checked_at", fetched.toString())
                .put("forecast_only", true)
        val reasons = LinkedHashSet<String>()
        var severity = 0
        var covered = 0
        var peakRain = 0.0
        var peakGust = 0.0
        var minTemperature = 100.0
        var maxTemperature = -100.0
        val first = start.epochSecond / 3600 * 3600
        val last = (end.epochSecond - 1) / 3600 * 3600
        try {
            var previous = Long.MIN_VALUE
            for (i in 0 until times.length()) {
                val time = times.getLong(i)
                require(time > previous && (previous == Long.MIN_VALUE || time - previous == 3600L))
                previous = time
            }
            var time = first
            while (time <= last) {
                var at = -1
                for (i in 0 until times.length()) if (times.getLong(i) == time) {
                    at = i
                    break
                }
                if (at < 0 || at + 1 >= times.length())
                    return unknown("Termin liegt außerhalb der vollständigen Vorhersage.")
                covered++
                // Precipitation and gusts refer to the preceding hour; temperature to the
                // timestamp.
                val temperature = number(hourly.optJSONArray("temperature_2m"), at)
                val rain = number(hourly.optJSONArray("precipitation"), at + 1)
                val gust = number(hourly.optJSONArray("wind_gusts_10m"), at + 1)
                val probability =
                    optionalNumber(hourly.optJSONArray("precipitation_probability"), at + 1)
                peakRain = maxOf(peakRain, rain)
                peakGust = maxOf(peakGust, gust)
                minTemperature = minOf(minTemperature, temperature)
                maxTemperature = maxOf(maxTemperature, temperature)
                require(
                    rain >= 0 &&
                        (probability == null || probability in 0.0..100.0) &&
                        gust >= 0 &&
                        temperature in -90.0..65.0
                )
                if (rain >= settings.optDouble("rain_mm", 1.0) && profile != "inspection") {
                    reasons.add(
                        if (probability == null) "Niederschlag kann die geplante Arbeit erschweren."
                        else "Regen kann die geplante Arbeit erschweren."
                    )
                    severity = maxOf(severity, 1)
                }
                if (gust >= settings.optDouble("gust_kmh", 40.0)) {
                    reasons.add("Starke Böen: Tätigkeit und Arbeitsmittel vor Ort prüfen.")
                    severity = maxOf(severity, if (profile == "hedge") 2 else 1)
                }
                if (temperature <= settings.optDouble("frost_c", 0.0)) {
                    reasons.add("Frost: Boden und geplante Tätigkeit prüfen.")
                    severity = maxOf(severity, 1)
                }
                if (temperature >= settings.optDouble("heat_c", 30.0)) {
                    reasons.add("Hitze: frühere Arbeitszeit und Pausen prüfen.")
                    severity = maxOf(severity, 1)
                }
                val snow = optionalNumber(hourly.optJSONArray("snowfall"), at + 1)
                if (snow != null && snow > 0) {
                    reasons.add("Schnee oder Eis: Boden und Zugänge prüfen.")
                    severity = maxOf(severity, 1)
                }
                val wind = optionalNumber(hourly.optJSONArray("wind_speed_10m"), at + 1)
                if (wind != null && wind >= settings.optDouble("gust_kmh", 40.0)) {
                    reasons.add("Starker Wind: Tätigkeit vor Ort prüfen.")
                    severity = maxOf(severity, if (profile == "hedge") 2 else 1)
                }
                val codes = hourly.optJSONArray("weather_code")
                if (codes != null && number(codes, at) >= 95) {
                    reasons.add("Gewitter im Vorhersagemodell: Einsatz prüfen.")
                    severity = 2
                }
                time += 3600
            }
        } catch (malformed: Exception) {
            return unknown("Stundenwerte fehlen oder sind widersprüchlich.")
        }
        if (covered == 0) return unknown("Keine passenden Stundenwerte.")
        val level = if (severity == 2) "critical" else if (severity == 1) "difficult" else "clear"
        val result =
            JSONObject()
                .put("severity", level)
                .put(
                    "label",
                    if (severity == 2) "Kritisch – prüfen"
                    else if (severity == 1) "Arbeit erschwert"
                    else "Kein erkennbares Wetterhindernis",
                )
                .put("reasons", JSONArray(reasons))
                .put("checked_at", fetched.toString())
                .put("forecast_only", true)
                .put("source", "GeoSphere Austria · CC BY 4.0")
                .put("uncertain_long_range", start.isAfter(now.plusSeconds(5 * 86400)))
        result.put(
            "alert_hash",
            PixelMcpWriteTools.hash(
                JSONObject()
                    .put("appointment_id", a.getString("id"))
                    .put("location_key", locationKey(a))
                    .put("weather_profile", a.optString("weather_profile"))
                    .put("starts_at", a.getString("starts_at"))
                    .put("ends_at", a.getString("ends_at"))
                    .put("weather_profile", a.optString("weather_profile"))
                    .put("severity", level)
                    .put(
                        "risk_band",
                        JSONArray()
                            .put((peakRain / 5).toInt())
                            .put((peakGust / 20).toInt())
                            .put((minTemperature / 10).toInt())
                            .put((maxTemperature / 10).toInt()),
                    )
                    .put("reasons", JSONArray(reasons))
            ),
        )
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun evaluate(
        a: JSONObject,
        cache: JSONObject?,
        now: Instant,
        settings: JSONObject,
    ): JSONObject {
        val result = model(a, cache, now, settings)
        if (cache != null && cache.optString("forecast_provider").isNotEmpty()) {
            val coverage = OpenMeteoWeatherSource.coverage(cache, now)
            for (key in coverage.keys()) result.put(key, coverage.get(key))
            val available =
                result.optString("severity") !in listOf("unknown", "inactive") &&
                    a.optString("weather_profile") != "indoor"
            result
                .put("forecast_provider", cache.optString("forecast_provider"))
                .put("source", cache.optString("forecast_source", FreeWeatherSource.ATTRIBUTION))
                .put("forecast_available", available)
            if (!available) result.put("forecast_days", 0).put("forecast_complete", false)
            if (cache.has("forecast_notice"))
                result.put("forecast_notice", cache.getString("forecast_notice"))
            if (available && result.optBoolean("uncertain_long_range"))
                result
                    .put("forecast_stage", "trend")
                    .put(
                        "label",
                        if (result.optString("severity") == "clear") "Wettertrend – noch unsicher"
                        else "Wettertrend – Einsatz prüfen",
                    )
        }
        if (AppointmentRules.openEnd(a) && AppointmentRules.active(a)) {
            result.put("duration_unknown", true).put("time_scope", "start_only")
            result
                .getJSONArray("reasons")
                .put(
                    "Ende offen: Wetterhinweise beziehen sich nur auf den Beginn bzw. aktuellen Zeitpunkt."
                )
        }
        if (!AppointmentRules.active(a)) return result
        supplementNowcast(a, cache, now, settings, result)
        val fresh =
            try {
                cache != null &&
                    !cache.has("warning_status") &&
                    a.has("latitude") &&
                    !a.isNull("latitude") &&
                    Instant.parse(cache.getString("warnings_fetched_at"))
                        .isAfter(now.minusSeconds(3 * 3600)) &&
                    !Instant.parse(cache.getString("warnings_fetched_at"))
                        .isAfter(now.plusSeconds(300))
            } catch (ignored: Exception) {
                false
            }
        result.put("official_warnings_current", fresh)
        if (!fresh) return result.put("warning_status", "Amtliche Warnungen nicht aktuell geprüft.")
        val warnings =
            cache!!.optJSONArray("warnings")
                ?: return result
                    .put("official_warnings_current", false)
                    .put("warning_status", "Amtliche Warnungen unvollständig.")
        val severity = result.optString("severity")
        val unknown = severity == "unknown"
        var level = if (severity == "critical") 2 else if (severity == "difficult") 1 else 0
        val reasons = LinkedHashSet<String>()
        val current = result.getJSONArray("reasons")
        for (i in 0 until current.length()) reasons.add(current.getString(i))
        var relevant = false
        val start = AppointmentRules.weatherStart(a, now)
        val end = AppointmentRules.weatherEnd(a, now)
        for (i in 0 until warnings.length()) try {
            val warning = warnings.getJSONObject(i)
            if (
                AppointmentRules.instant(warning.getString("starts_at")).isBefore(end) &&
                    AppointmentRules.instant(warning.getString("ends_at")).isAfter(start) &&
                    warning.getInt("level") > 0
            ) {
                relevant = true
                if (warning.getInt("level") >= 2) result.put("critical_official_warning", true)
                level = maxOf(level, if (warning.getInt("level") >= 2) 2 else 1)
                reasons.add(
                    "GeoSphere-Warnung: " +
                        warning.optString("type", "Wetter") +
                        " (Stufe " +
                        warning.getInt("level") +
                        ") · " +
                        warning.getString("starts_at") +
                        " – " +
                        warning.getString("ends_at") +
                        "."
                )
            }
        } catch (invalid: Exception) {
            result
                .put("official_warnings_current", false)
                .put("warning_status", "Amtliche Warnung nicht vollständig auswertbar.")
        }
        if (relevant) {
            result
                .put("forecast_unknown", unknown)
                .put("severity", if (level >= 2) "critical" else "difficult")
                .put(
                    "label",
                    if (level >= 2) "Kritisch – amtliche Warnung prüfen"
                    else "Amtliche Wetterwarnung – Einsatz prüfen",
                )
                .put("reasons", JSONArray(reasons))
            result.put(
                "alert_hash",
                PixelMcpWriteTools.hash(
                    JSONObject()
                        .put("appointment_id", a.getString("id"))
                        .put("location_key", locationKey(a))
                        .put("weather_profile", a.optString("weather_profile"))
                        .put("starts_at", a.getString("starts_at"))
                        .put("ends_at", a.getString("ends_at"))
                        .put("severity", result.getString("severity"))
                        .put("reasons", JSONArray(reasons))
                ),
            )
        }
        return result
    }

    @Throws(Exception::class)
    private fun supplementNowcast(
        a: JSONObject,
        cache: JSONObject?,
        now: Instant,
        settings: JSONObject,
        result: JSONObject,
    ) {
        if (cache == null || a.optString("weather_profile") == "indoor") return
        try {
            if (FreeWeatherSource.due(cache, "nowcast_fetched_at", now, 3600)) return
            val data = cache.getJSONObject("nowcast").getJSONObject("quarter_hourly")
            val times = data.getJSONArray("time")
            val start = AppointmentRules.weatherStart(a, now)
            val end = AppointmentRules.weatherEnd(a, now)
            if (start.isAfter(now.plusSeconds(10800))) return
            var level =
                if (result.optString("severity") == "critical") 2
                else if (result.optString("severity") == "difficult") 1 else 0
            val reasons = LinkedHashSet<String>()
            val old = result.getJSONArray("reasons")
            for (i in 0 until old.length()) reasons.add(old.getString(i))
            var relevant = false
            for (i in 1 until times.length()) {
                val time = times.getLong(i)
                if (
                    time <= maxOf(now.epochSecond, start.epochSecond) ||
                        time - 900 >= end.epochSecond
                )
                    continue
                val rain = optionalNumber(data.optJSONArray("precipitation"), i)
                val gust = optionalNumber(data.optJSONArray("wind_gusts_10m"), i)
                val temperature = optionalNumber(data.optJSONArray("temperature_2m"), i)
                if (
                    rain != null &&
                        rain * 4 >= settings.optDouble("rain_mm", 1.0) &&
                        a.optString("weather_profile") != "inspection"
                ) {
                    reasons.add("Kurzfristig Niederschlag: geplante Arbeit prüfen.")
                    relevant = true
                    level = maxOf(level, 1)
                }
                if (gust != null && gust >= settings.optDouble("gust_kmh", 40.0)) {
                    reasons.add("Kurzfristig starke Böen: Tätigkeit prüfen.")
                    relevant = true
                    level = maxOf(level, if (a.optString("weather_profile") == "hedge") 2 else 1)
                }
                if (
                    temperature != null &&
                        (temperature <= settings.optDouble("frost_c", 0.0) ||
                            temperature >= settings.optDouble("heat_c", 30.0))
                ) {
                    reasons.add("Kurzfristig Frost oder Hitze: Tätigkeit prüfen.")
                    relevant = true
                    level = maxOf(level, 1)
                }
            }
            if (relevant) {
                result
                    .put("forecast_unknown", result.optString("severity") == "unknown")
                    .put("severity", if (level >= 2) "critical" else "difficult")
                    .put("label", "Kurzfristiges Wetterrisiko – Einsatz prüfen")
                    .put("reasons", JSONArray(reasons))
                result.put(
                    "alert_hash",
                    PixelMcpWriteTools.hash(
                        JSONObject()
                            .put("appointment_id", a.getString("id"))
                            .put("location_key", locationKey(a))
                            .put("weather_profile", a.optString("weather_profile"))
                            .put("starts_at", a.getString("starts_at"))
                            .put("ends_at", a.getString("ends_at"))
                            .put("reasons", JSONArray(reasons))
                    ),
                )
            }
        } catch (incomplete: Exception) {
            result.put("nowcast_status", "Kurzfristwetter derzeit nicht vollständig verfügbar.")
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parseWarnings(raw: JSONObject): JSONArray {
        val source =
            raw.optJSONObject("properties")?.optJSONArray("warnings")
                ?: throw IllegalArgumentException("Unbekanntes GeoSphere-Warnformat.")
        val out = JSONArray()
        require(source.length() <= 300) { "Zu viele Warnungen." }
        for (i in 0 until source.length()) {
            val warning = source.getJSONObject(i).getJSONObject("properties")
            var from = warningTime(warning.get("begin"))
            var until = warningTime(warning.get("end"))
            if (from > 100000000000L) from /= 1000
            if (until > 100000000000L) until /= 1000
            val level = warning.getInt("warnstufeid")
            val type = warning.getInt("warntypid")
            require(level in 0..3 && until > from) { "Ungültige Warnung." }
            val types =
                arrayOf(
                    "Wetter",
                    "Wind",
                    "Regen",
                    "Schnee",
                    "Glatteis",
                    "Gewitter",
                    "Hitze",
                    "Kälte",
                )
            out.put(
                JSONObject()
                    .put("starts_at", Instant.ofEpochSecond(from).toString())
                    .put("ends_at", Instant.ofEpochSecond(until).toString())
                    .put("level", level)
                    .put("type", if (type > 0 && type < types.size) types[type] else "Wetter")
            )
        }
        return out
    }

    @Throws(Exception::class)
    private fun warningTime(value: Any): Long {
        if (value is Number) return value.toLong()
        val string = value.toString()
        return if (string.matches(Regex("[0-9]+"))) string.toLong()
        else AppointmentRules.instant(string).epochSecond
    }
}
