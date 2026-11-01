package at.hausgarten.service

import org.json.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.util.Locale

/** Fixed, keyless GeoSphere adapters. Unknown format/unit/licence fails closed. */
internal object FreeWeatherSource {
    const val HOUR = 3600L
    const val DAY = 24 * HOUR
    const val MONITOR_WINDOW = 16 * DAY
    const val API = "https://dataset.api.hub.geosphere.at/v1/timeseries/forecast/"
    const val LICENCE = "https://dataset.api.hub.geosphere.at/v1/docs/index.html"
    const val ATTRIBUTION = "GeoSphere Austria · CC BY 4.0 · https://data.hub.geosphere.at"
    @JvmStatic fun intervalSeconds(untilStart: Long): Long = when {
        untilStart > MONITOR_WINDOW -> maxOf(60, untilStart - MONITOR_WINDOW)
        untilStart > 7 * DAY -> DAY
        untilStart > 72 * HOUR -> 12 * HOUR
        untilStart > 24 * HOUR -> 6 * HOUR
        untilStart > 6 * HOUR -> 3 * HOUR
        else -> HOUR
    }
    @JvmStatic fun nextDelayMillis(appointments: JSONArray, now: Instant): Long {
        var best = Long.MAX_VALUE
        for (i in 0 until appointments.length()) try {
            val appointment = appointments.getJSONObject(i)
            if (!AppointmentRules.active(appointment) || appointment.optString("status") == "draft") continue
            val start = AppointmentRules.instant(appointment.getString("starts_at"))
            if (!AppointmentRules.pending(appointment, now)) continue
            best = minOf(best, intervalSeconds(maxOf(0, Duration.between(now, start).seconds)) * 1000L)
        } catch (legacy: Exception) { /* An incomplete historical row must not disable other appointments. */ }
        return best
    }
    @JvmStatic fun due(cache: JSONObject, key: String, now: Instant, age: Long): Boolean = try {
        val at = Instant.parse(cache.getString(key)); at.isAfter(now.plusSeconds(300)) || !at.isAfter(now.minusSeconds(age))
    } catch (missing: Exception) { true }
    @JvmStatic @Throws(Exception::class) fun fetch(wire: HttpTransport.Wire, url: String, maximum: Int, now: Instant): HttpTransport.Reply {
        val reply = wire.send("GET", url, emptyMap(), null, maximum)
        if (reply.status == 429 || reply.status >= 500) {
            var delay = 30000L; val retry = reply.header("Retry-After")
            try { delay = maxOf(delay, retry.toLong() * 1000) }
            catch (numeric: Exception) { try { delay = maxOf(delay, Duration.between(now, ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()) } catch (ignored: Exception) {} }
            throw HttpTransport.Failure(if (reply.status == 429) "WEATHER_RATE_LIMIT" else "WEATHER_RETRY", "Wetterdaten derzeit nicht verfügbar.", minOf(24 * HOUR * 1000, delay), false)
        }
        if (reply.status != 200) throw HttpTransport.Failure("WEATHER_UNAVAILABLE", "Wetterdaten derzeit nicht verfügbar.", 0, reply.status == 401 || reply.status == 403)
        return reply
    }
    @JvmStatic @Throws(Exception::class) fun requireLicence(cache: JSONObject, now: Instant, wire: HttpTransport.Wire) {
        if (!due(cache, "licence_checked_at", now, 7 * DAY)) return
        val html = String(fetch(wire, LICENCE, 512 * 1024, now).body, StandardCharsets.UTF_8)
        val plain = html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        if (!plain.contains("all data that is publicly accessible without authentication") || !html.contains("creativecommons.org/licenses/by/4.0")) {
            cache.remove("licence_checked_at")
            throw HttpTransport.Failure("WEATHER_LICENCE_UNVERIFIED", "Wetterdaten derzeit nicht verfügbar. Kostenfreier öffentlicher Datenzugang ist nicht bestätigt.", 0, true)
        }
        cache.put("licence_checked_at", now.toString())
    }
    @JvmStatic @Throws(Exception::class) fun parameters(metadata: JSONObject): JSONObject {
        metadata.optJSONObject("parameters")?.let { return it }
        val list = metadata.optJSONArray("parameters") ?: throw IOException("Wetterdaten derzeit nicht verfügbar.")
        val out = JSONObject()
        for (i in 0 until list.length()) {
            val parameter = list.getJSONObject(i); val id = parameter.optString("name", parameter.optString("id"))
            if (id.isEmpty() || out.has(id)) throw IOException("Wetterparameter nicht eindeutig.")
            out.put(id, parameter)
        }
        return out
    }
    @JvmStatic @Throws(Exception::class) fun metadata(dataset: String, cache: JSONObject, now: Instant, wire: HttpTransport.Wire): JSONObject {
        val key = dataset + ":metadata"
        if (due(cache, "$key:at", now, DAY)) {
            val metadata = fetch(wire, API + dataset + "/metadata", 1024 * 1024, now).json(); parameters(metadata)
            cache.put(key, metadata).put("$key:at", now.toString())
        }
        return cache.getJSONObject(key)
    }
    @JvmStatic @Throws(Exception::class) fun coords(location: String): DoubleArray {
        val pair = java.util.regex.Pattern.compile(",").split(location)
        if (pair.size != 2) throw IOException("Einsatzort für Wetterprüfung ergänzen.")
        val latitude = pair[0].toDouble(); val longitude = pair[1].toDouble()
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) throw IOException("Einsatzort für Wetterprüfung ergänzen.")
        return doubleArrayOf(latitude, longitude)
    }
    @JvmStatic @Throws(Exception::class) fun response(dataset: String, requested: String, location: String, metadata: JSONObject, wire: HttpTransport.Wire, now: Instant): JSONObject {
        val point = coords(location)
        val raw = fetch(wire, API + dataset + "?parameters=" + requested + "&lat_lon=" + point[0] + "," + point[1] + "&output_format=geojson", 1024 * 1024, now).json()
        val features = raw.optJSONArray("features")
        if (features == null || features.length() != 1) throw IOException("Wetterort nicht eindeutig.")
        val coordinates = features.getJSONObject(0).optJSONObject("geometry")?.optJSONArray("coordinates")
        if (coordinates == null || coordinates.length() < 2 || kotlin.math.abs(coordinates.getDouble(1) - point[0]) > .05 || kotlin.math.abs(coordinates.getDouble(0) - point[1]) > .08) throw IOException("Wetterdaten für diesen Einsatzort nicht verfügbar.")
        val actual = features.getJSONObject(0).getJSONObject("properties").getJSONObject("parameters"); val declared = parameters(metadata)
        for (id in requested.split(",")) {
            val parameter = actual.optJSONObject(id); val declaration = declared.optJSONObject(id)
            if (parameter == null || declaration == null) throw IOException("Wetterparameter fehlt.")
            if (parameter.optString("unit").isEmpty()) parameter.put("unit", declaration.getString("unit"))
            else if (parameter.getString("unit") != declaration.getString("unit")) throw IOException("Wettereinheiten widersprüchlich.")
        }
        return raw
    }
    @JvmStatic @Throws(Exception::class) fun refreshSite(location: String, cache: JSONObject, now: Instant, untilStart: Long, wire: HttpTransport.Wire): JSONObject {
        val cadence = intervalSeconds(untilStart)
        cache.put("monitoring", JSONObject().put("active", untilStart <= MONITOR_WINDOW).put("first_check_days_before", 16).put("next_interval_seconds", cadence).put("source", ATTRIBUTION))
        if (untilStart > MONITOR_WINDOW) return cache
        coords(location); requireLicence(cache, now, wire)
        val metadata = metadata("nwp-v2-1h-1km", cache, now, wire)
        cache.put("availability_checked_at", now.toString()).put("source", ATTRIBUTION)
        if (untilStart <= 4 * DAY && due(cache, "warnings_fetched_at", now, cadence)) {
            val point = coords(location)
            try {
                val warnings = fetch(wire, "https://warnungen.zamg.at/wsapp/api/getWarningsForCoords?lat=" + point[0] + "&lon=" + point[1] + "&lang=de", 512 * 1024, now).json()
                cache.put("warnings", WeatherRiskRules.parseWarnings(warnings)).put("warnings_fetched_at", now.toString()).remove("warning_status")
            } catch (unavailable: HttpTransport.Failure) { cache.put("warning_status", "Amtliche Warnungen derzeit nicht verfügbar."); throw unavailable }
            catch (unavailable: Exception) { cache.put("warning_status", "Amtliche Warnungen derzeit nicht verfügbar.") }
        }
        if (untilStart <= 60 * HOUR && due(cache, "fetched_at", now, cadence)) {
            val parameters = parameters(metadata); val interval = parameters.has("2t") && parameters.has("tp") && parameters.has("10fg")
            var names = if (interval) "2t,tp,10fg" else "t2m,rain_acc,ugust,vgust"
            for (id in names.split(",")) if (!parameters.has(id)) throw IOException("Wetterdaten derzeit nicht verfügbar.")
            for (id in if (interval) arrayOf("10u", "10v", "sf") else arrayOf("u10m", "v10m", "snow_acc")) if (parameters.has(id)) names += "," + id
            val raw = response("nwp-v2-1h-1km", names, location, metadata, wire, now)
            cache.put("forecast", normalizeGeoSphere(raw)).put("fetched_at", now.toString()).put("forecast_provider", "geosphere_nwp_v2")
        }
        return cache
    }
    @JvmStatic @Throws(Exception::class) fun refreshNonCommercialSite(location: String, cache: JSONObject, now: Instant, untilStart: Long, wire: HttpTransport.Wire): JSONObject {
        cache.put("monitoring", JSONObject().put("active", untilStart <= MONITOR_WINDOW).put("first_check_days_before", 16)
            .put("minimum_forecast_lead_days", 14).put("next_interval_seconds", intervalSeconds(untilStart)).put("source", OpenMeteoWeatherSource.ATTRIBUTION))
        if (untilStart > MONITOR_WINDOW) return cache
        try { OpenMeteoWeatherSource.refreshSite(location, cache, now, untilStart, wire) }
        catch (primary: Exception) {
            if (CalendarRecoveryRules.interrupted(primary)) throw primary
            if (primary is HttpTransport.Failure && primary.code in listOf("WEATHER_RATE_LIMIT", "WEATHER_BATCH_PENDING")) throw primary
            // The public short-range adapter remains a fallback; it does not cover 14 days.
            if (untilStart <= 60 * HOUR) try {
                val fallback = JSONObject(cache.toString()); fallback.remove("fetched_at"); refreshSite(location, fallback, now, untilStart, wire)
                if (fallback.optString("forecast_provider") == "geosphere_nwp_v2" && fallback.optString("fetched_at") == now.toString()) {
                    for (key in fallback.keys()) cache.put(key, fallback.get(key))
                    cache.put("forecast_source", ATTRIBUTION).put("forecast_notice", "Kurzfristige Ersatzvorhersage: Open-Meteo derzeit nicht verfügbar."); cache.remove("source_unavailable")
                    return cache
                }
            } catch (secondary: Exception) { if (CalendarRecoveryRules.interrupted(secondary)) throw secondary }
            throw primary
        }
        if (untilStart <= 4 * DAY) try { refreshWarnings(location, cache, now, untilStart, wire) }
        catch (unavailable: Exception) {
            cache.put("warning_status", "Amtliche Warnungen derzeit nicht verfügbar.")
            if (CalendarRecoveryRules.interrupted(unavailable) || unavailable is HttpTransport.Failure && unavailable.code in listOf("WEATHER_RATE_LIMIT", "WEATHER_BATCH_PENDING")) throw unavailable
        }
        return cache
    }
    @JvmStatic @Throws(Exception::class) fun refreshWarnings(location: String, cache: JSONObject, now: Instant, untilStart: Long, wire: HttpTransport.Wire) {
        if (untilStart > 4 * DAY || !due(cache, "warnings_fetched_at", now, minOf(3 * HOUR, intervalSeconds(untilStart)))) return
        val point = coords(location); requireLicence(cache, now, wire)
        val warnings = fetch(wire, "https://warnungen.zamg.at/wsapp/api/getWarningsForCoords?lat=" + point[0] + "&lon=" + point[1] + "&lang=de", 512 * 1024, now).json()
        cache.put("warnings", WeatherRiskRules.parseWarnings(warnings)).put("warnings_fetched_at", now.toString()); cache.remove("warning_status")
    }
    @JvmStatic @Throws(Exception::class) fun refreshNowcast(location: String, cache: JSONObject, now: Instant, wire: HttpTransport.Wire) {
        if (!due(cache, "nowcast_fetched_at", now, HOUR)) return
        requireLicence(cache, now, wire)
        val metadata = metadata("nowcast-v1-15min-1km", cache, now, wire); val parameters = parameters(metadata)
        for (id in arrayOf("t2m", "rr", "fx")) if (!parameters.has(id)) throw IOException("Kurzfristwetter derzeit nicht verfügbar.")
        val raw = response("nowcast-v1-15min-1km", "t2m,rr,fx", location, metadata, wire, now)
        cache.put("nowcast", normalizeNowcast(raw)).put("nowcast_fetched_at", now.toString())
    }
    @JvmStatic @Throws(Exception::class) fun normalized(raw: JSONObject, nowcast: Boolean): JSONObject {
        val times = raw.optJSONArray("timestamps"); val features = raw.optJSONArray("features")
        if (times == null || times.length() < 2 || times.length() > 200 || features == null || features.length() != 1) throw IOException("Wetterzeitreihe unvollständig.")
        val parameters = features.getJSONObject(0).getJSONObject("properties").getJSONObject("parameters"); val out = JSONObject()
        val normalizedTimes = JSONArray(); val temperature = JSONArray(); val rain = JSONArray(); val gusts = JSONArray(); val snow = JSONArray(); val wind = JSONArray()
        val interval = !nowcast && parameters.has("2t")
        val temperatureId = if (interval) "2t" else "t2m"; val rainId = if (nowcast) "rr" else if (interval) "tp" else "rain_acc"
        val snowId = if (interval) "sf" else "snow_acc"; val uId = if (interval) "10u" else "u10m"; val vId = if (interval) "10v" else "v10m"
        val rainData = data(parameters, rainId); val snowData = if (parameters.has(snowId)) data(parameters, snowId) else null
        var previousRain: Double? = null; var previousSnow: Double? = null; var previous = Long.MIN_VALUE
        for (i in 0 until times.length()) {
            val stamp = parseTime(times.get(i))
            if (previous != Long.MIN_VALUE && stamp - previous != (if (nowcast) 900L else 3600L)) throw IOException("Wetterzeitreihe widersprüchlich.")
            previous = stamp; normalizedTimes.put(stamp); temperature.put(convert(parameters, temperatureId, i, "temperature"))
            val current = number(rainData, i); val currentSnow = snowData?.let { number(it, i) }; val factor = factor(parameters.getJSONObject(rainId).getString("unit"), "rain")
            // NWP tp/sf and INCA rr are preceding-interval sums; legacy rain_acc uses deltas.
            if (nowcast || interval) rain.put(if (current == null) JSONObject.NULL else current * factor)
            else { rain.put(if (current == null || previousRain == null || current < previousRain) JSONObject.NULL else (current - previousRain) * factor); previousRain = current }
            if (snowData != null) {
                val snowFactor = factor(parameters.getJSONObject(snowId).getString("unit"), "rain")
                snow.put(if (interval) { if (currentSnow == null) JSONObject.NULL else currentSnow * snowFactor }
                    else if (currentSnow == null || previousSnow == null || currentSnow < previousSnow) JSONObject.NULL else (currentSnow - previousSnow) * snowFactor)
                previousSnow = currentSnow
            }
            if (nowcast) gusts.put(convert(parameters, "fx", i, "wind"))
            else if (interval) gusts.put(convert(parameters, "10fg", i, "wind"))
            else { val u = value(parameters, "ugust", i, "wind"); val v = value(parameters, "vgust", i, "wind"); gusts.put(if (u == null || v == null) JSONObject.NULL else Math.hypot(u, v)) }
            if (parameters.has(uId) && parameters.has(vId)) { val u = value(parameters, uId, i, "wind"); val v = value(parameters, vId, i, "wind"); wind.put(if (u == null || v == null) JSONObject.NULL else Math.hypot(u, v)) }
        }
        out.put("time", normalizedTimes).put("temperature_2m", temperature).put("precipitation", rain).put("wind_gusts_10m", gusts)
        if (snowData != null) out.put("snowfall", snow)
        if (wind.length() > 0) out.put("wind_speed_10m", wind)
        return JSONObject().put(if (nowcast) "quarter_hourly" else "hourly", out)
    }
    @JvmStatic @Throws(Exception::class) fun normalizeGeoSphere(raw: JSONObject): JSONObject = normalized(raw, false)
    @JvmStatic @Throws(Exception::class) fun normalizeNowcast(raw: JSONObject): JSONObject = normalized(raw, true)
    @Throws(Exception::class) private fun data(parameters: JSONObject, id: String): JSONArray = parameters.optJSONObject(id)?.optJSONArray("data") ?: throw IOException("Wetterparameter fehlt.")
    @Throws(Exception::class) private fun number(rows: JSONArray, index: Int): Double? {
        if (index >= rows.length()) throw IOException("Wetterzeitreihe unvollständig.")
        if (rows.isNull(index)) return null
        return rows.getDouble(index).takeIf { it.isFinite() }
    }
    @Throws(Exception::class) private fun factor(unit: String, kind: String): Double {
        val normalized = unit.lowercase(Locale.ROOT).replace(" ", "").replace("**", "^").replace("{", "").replace("}", "")
        if (kind == "temperature" && normalized in listOf("°c", "degc", "c", "celsius", "degreecelsius", "degree_celsius", "k", "kelvin")) return 1.0
        if (kind == "wind") {
            if (normalized in listOf("ms-1", "ms^-1", "m/s")) return 3.6
            if (normalized in listOf("km/h", "kmh-1", "kmh^-1")) return 1.0
        }
        if (kind == "rain" && normalized in listOf("mm", "kgm-2", "kgm^-2", "kg/m2", "mm/15min")) return 1.0
        throw IOException("Wettereinheit derzeit nicht unterstützt.")
    }
    @Throws(Exception::class) private fun value(parameters: JSONObject, id: String, index: Int, kind: String): Double? {
        val row = parameters.getJSONObject(id); val factor = factor(row.getString("unit"), kind); val number = number(data(parameters, id), index) ?: return null
        val unit = row.getString("unit")
        return if (kind == "temperature" && (unit.equals("K", true) || unit.equals("kelvin", true))) number - 273.15 else number * factor
    }
    @Throws(Exception::class) private fun convert(parameters: JSONObject, id: String, index: Int, kind: String): Any = value(parameters, id, index, kind) ?: JSONObject.NULL
    @Throws(Exception::class) private fun parseTime(value: Any): Long = if (value is Number) value.toLong().let { if (it > 100000000000L) it / 1000 else it } else OffsetDateTime.parse(value.toString()).toInstant().epochSecond
}
