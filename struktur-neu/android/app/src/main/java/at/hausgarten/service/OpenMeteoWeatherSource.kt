package at.hausgarten.service

import java.io.IOException
import java.time.Instant
import org.json.*

/** Retained keyless public endpoint. Commercial API eligibility remains unverified. */
internal object OpenMeteoWeatherSource {
    const val FORECAST_DAYS = 16
    const val API = "https://api.open-meteo.com/v1/forecast"
    const val TERMS = "https://open-meteo.com/en/terms"
    const val PROVIDER = "open_meteo_non_commercial"
    const val ATTRIBUTION = "Open-Meteo · CC BY 4.0 · https://open-meteo.com/"
    private val VARIABLES =
        arrayOf(
            "temperature_2m",
            "precipitation",
            "precipitation_probability",
            "wind_gusts_10m",
            "wind_speed_10m",
            "snowfall",
            "weather_code",
        )
    private val UNITS = arrayOf("°C", "mm", "%", "km/h", "km/h", "cm", "wmo code")

    @JvmStatic
    @Throws(Exception::class)
    fun url(location: String): String {
        val point = FreeWeatherSource.coords(location)
        return API +
            "?latitude=" +
            point[0] +
            "&longitude=" +
            point[1] +
            "&hourly=" +
            VARIABLES.joinToString(",") +
            "&forecast_days=" +
            FORECAST_DAYS +
            "&timezone=GMT&timeformat=unixtime&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"
    }

    @JvmStatic
    fun maximumAge(untilStart: Long): Long =
        when {
            untilStart > 7 * 86400 -> 30 * 3600L
            untilStart > 72 * 3600 -> 18 * 3600L
            untilStart > 24 * 3600 -> 8 * 3600L
            untilStart > 6 * 3600 -> 4 * 3600L
            else -> 2 * 3600L
        }

    @JvmStatic
    @Throws(Exception::class)
    fun refreshSite(
        location: String,
        cache: JSONObject,
        now: Instant,
        untilStart: Long,
        wire: HttpTransport.Wire,
    ): JSONObject {
        if (untilStart > FreeWeatherSource.MONITOR_WINDOW) return cache
        if (
            cache.optBoolean("source_unavailable") ||
                PROVIDER != cache.optString("forecast_provider") ||
                FreeWeatherSource.due(
                    cache,
                    "fetched_at",
                    now,
                    FreeWeatherSource.intervalSeconds(untilStart),
                )
        ) {
            val raw = FreeWeatherSource.fetch(wire, url(location), 1024 * 1024, now).json()
            val forecast = normalize(raw, FreeWeatherSource.coords(location))
            // Publish only a completely validated response; never stamp a failed refresh fresh.
            cache
                .put("forecast", forecast)
                .put("fetched_at", now.toString())
                .put("forecast_provider", PROVIDER)
                .put("forecast_source", ATTRIBUTION)
            cache.remove("source_unavailable")
            cache.remove("forecast_notice")
        }
        return cache
    }

    @JvmStatic
    @Throws(Exception::class)
    fun normalize(raw: JSONObject, requested: DoubleArray): JSONObject {
        if (raw.optBoolean("error") || raw.optInt("utc_offset_seconds", Int.MIN_VALUE) != 0)
            throw invalid()
        val latitude = raw.optDouble("latitude", Double.NaN)
        val longitude = raw.optDouble("longitude", Double.NaN)
        if (
            !latitude.isFinite() ||
                !longitude.isFinite() ||
                kotlin.math.abs(latitude) > 90 ||
                kotlin.math.abs(longitude) > 180
        )
            throw invalid()
        val a = Math.sin(Math.toRadians(latitude - requested[0]) / 2)
        val b = Math.sin(Math.toRadians(longitude - requested[1]) / 2)
        val distance =
            12742 *
                Math.asin(
                    minOf(
                        1.0,
                        Math.sqrt(
                            a * a +
                                Math.cos(Math.toRadians(latitude)) *
                                    Math.cos(Math.toRadians(requested[0])) *
                                    b *
                                    b
                        ),
                    )
                )
        if (distance > 50)
            throw IOException("Wetterdaten passen nicht zum angefragten Einsatzgebiet.")
        val units = raw.optJSONObject("hourly_units")
        val hourly = raw.optJSONObject("hourly")
        if (units == null || hourly == null || units.optString("time") != "unixtime")
            throw invalid()
        val times = hourly.optJSONArray("time")
        if (times == null || times.length() < 2 || times.length() > FORECAST_DAYS * 24 + 1)
            throw invalid()
        val result = JSONObject()
        val normalizedTimes = JSONArray()
        var previous = Long.MIN_VALUE
        for (i in 0 until times.length()) {
            val value = times.get(i)
            if (value !is Number) throw invalid()
            val numeric = value.toDouble()
            if (
                !numeric.isFinite() ||
                    numeric != Math.rint(numeric) ||
                    numeric < 0 ||
                    numeric > 4102444800L
            )
                throw invalid()
            val stamp = value.toLong()
            if (previous != Long.MIN_VALUE && stamp - previous != 3600L) throw invalid()
            previous = stamp
            normalizedTimes.put(stamp)
        }
        result.put("time", normalizedTimes)
        for (k in VARIABLES.indices) {
            val name = VARIABLES[k]
            if (UNITS[k] != units.optString(name)) throw invalid()
            val values = hourly.optJSONArray(name)
            if (values == null || values.length() != times.length()) throw invalid()
            val normalized = JSONArray()
            for (i in 0 until values.length()) {
                if (values.isNull(i)) {
                    normalized.put(JSONObject.NULL)
                    continue
                }
                val value = values.get(i)
                if (value !is Number) throw invalid()
                val n = value.toDouble()
                if (
                    !n.isFinite() ||
                        (if (k == 0) n < -90 || n > 65
                        else n < 0 || n > (if (k == 2) 100 else if (k == 6) 99 else 1000))
                )
                    throw invalid()
                if (k == 6 && n != Math.rint(n)) throw invalid()
                normalized.put(n)
            }
            result.put(name, normalized)
        }
        return JSONObject().put("hourly", result).put("hourly_units", JSONObject(units.toString()))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun coverage(cache: JSONObject?, now: Instant): JSONObject {
        val result =
            JSONObject()
                .put("forecast_days", 0)
                .put("forecast_complete", false)
                .put("forecast_valid_from", "")
                .put("forecast_valid_until", "")
        val hourly = cache?.optJSONObject("forecast")?.optJSONObject("hourly") ?: return result
        val times = hourly.optJSONArray("time")
        if (times == null || times.length() < 2 || times.length() > 500) return result
        var from = Long.MAX_VALUE
        var until = Long.MIN_VALUE
        var last = Long.MIN_VALUE
        var complete = true
        try {
            for (i in 0 until times.length() - 1) {
                val time = times.getLong(i)
                val end = times.getLong(i + 1)
                if (end - time != 3600L || time <= last) return result
                last = time
                if (end <= now.epochSecond) continue
                val valid =
                    WeatherRiskRules.optionalNumber(hourly.optJSONArray("temperature_2m"), i) !=
                        null &&
                        WeatherRiskRules.optionalNumber(
                            hourly.optJSONArray("precipitation"),
                            i + 1,
                        ) != null &&
                        WeatherRiskRules.optionalNumber(
                            hourly.optJSONArray("wind_gusts_10m"),
                            i + 1,
                        ) != null &&
                        (!hourly.has("weather_code") ||
                            WeatherRiskRules.optionalNumber(
                                hourly.optJSONArray("weather_code"),
                                i,
                            ) != null)
                if (!valid) {
                    complete = false
                    continue
                }
                from = minOf(from, time)
                until = end
            }
        } catch (invalid: Exception) {
            return result
        }
        if (until == Long.MIN_VALUE) return result
        return result
            .put("forecast_valid_from", Instant.ofEpochSecond(from).toString())
            .put("forecast_valid_until", Instant.ofEpochSecond(until).toString())
            .put("forecast_complete", complete)
            .put(
                "forecast_days",
                if (complete)
                    ((until - now.epochSecond) / 86400).coerceIn(0, FORECAST_DAYS.toLong()).toInt()
                else 0,
            )
    }

    private fun invalid(): IOException =
        IOException(
            "Wetterantwort unvollständig oder widersprüchlich. Keine belastbare Vorhersage."
        )
}
