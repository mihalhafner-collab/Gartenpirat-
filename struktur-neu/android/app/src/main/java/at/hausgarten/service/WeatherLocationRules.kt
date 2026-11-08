package at.hausgarten.service

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import org.json.*

/** Derives weather context without changing an appointment or guessing another customer's site. */
internal object WeatherLocationRules {
    const val RESOLVER_VERSION = 3

    fun interface Lookup {
        @Throws(Exception::class) fun find(query: String): JSONArray?
    }

    @JvmStatic
    fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}"), "")
            .lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .replace(Regex("strasse\\b"), "str")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex(" +"), " ")

    @JvmStatic
    fun address(customer: JSONObject): String {
        val street = customer.optString("street").trim()
        val town = (customer.optString("postal_code") + " " + customer.optString("city")).trim()
        return if (street.isEmpty()) town else if (town.isEmpty()) street else "$street, $town"
    }

    @JvmStatic
    fun coordinates(value: JSONObject): Boolean {
        val latitude = value.optDouble("latitude", Double.NaN)
        val longitude = value.optDouble("longitude", Double.NaN)
        return latitude.isFinite() &&
            longitude.isFinite() &&
            kotlin.math.abs(latitude) <= 90 &&
            kotlin.math.abs(longitude) <= 180
    }

    @JvmStatic
    @Throws(Exception::class)
    fun context(appointment: JSONObject, customers: JSONArray, orders: JSONArray): JSONObject {
        val value = JSONObject(appointment.toString())
        var place = value.optString("location").trim()
        val customer = OwnerJournalRules.findOrNull(customers, value.optString("customer_id"))
        if (place.isEmpty() && value.optString("order_id").isNotEmpty()) {
            val order = OwnerJournalRules.findOrNull(orders, value.optString("order_id"))
            if (order != null && value.optString("customer_id") == order.optString("customer_id"))
                place = order.optString("location").trim()
        }
        if (place.isEmpty() && customer != null) place = address(customer)
        value
            .put("weather_location_label", place)
            .put("weather_location_state", if (place.isEmpty()) "missing" else "pending")
        if (coordinates(value)) return value.put("weather_location_state", "confirmed_coordinates")
        if (
            customer != null &&
                place.isNotEmpty() &&
                normalized(place) == normalized(address(customer)) &&
                coordinates(customer)
        )
            value
                .put("latitude", customer.getDouble("latitude"))
                .put("longitude", customer.getDouble("longitude"))
                .put("weather_location_state", "customer_coordinates")
        return value
    }

    @JvmStatic
    @Throws(Exception::class)
    fun cacheKey(value: JSONObject): String =
        "weather:location:" +
            PixelMcpWriteTools.hash(
                value.optString("customer_id") +
                    "\n" +
                    normalized(value.optString("weather_location_label"))
            )

    @JvmStatic
    @Throws(Exception::class)
    fun cached(context: JSONObject, cache: JSONObject): JSONObject {
        val value = JSONObject(context.toString())
        if (coordinates(value)) return value
        if (coordinates(cache))
            value
                .put("latitude", cache.getDouble("latitude"))
                .put("longitude", cache.getDouble("longitude"))
                .put(
                    "weather_location_state",
                    if (cache.optString("state") == "locality_resolved") "locality_resolved"
                    else "address_resolved",
                )
                .put("weather_location_area", cache.optString("area_label"))
                .put("weather_location_precision", cache.optString("precision", "address"))
        else if (cache.has("state")) value.put("weather_location_state", cache.getString("state"))
        return value
    }

    private fun matchText(value: String): String =
        normalized(value)
            .replace(Regex("\\bst\\b"), "sankt")
            .replace(Regex("(\\p{L})\\s+str\\b"), "$1str")

    @JvmStatic
    fun geocoderQuery(value: String): String =
        value.replace(Regex("(?iu)(?<!\\p{L})st(?:\\.(?=\\p{L}|\\s)|(?=\\s))\\s*"), "Sankt ")

    @JvmStatic
    fun matches(query: String, returnedAddress: String): Boolean {
        val wanted = matchText(query)
        val actual = " " + matchText(returnedAddress) + " "
        return wanted.length >= 3 && wanted.split(" ").all { actual.contains(" $it ") }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun choose(query: String, candidates: JSONArray): JSONObject {
        var chosen: JSONObject? = null
        for (i in 0 until candidates.length()) {
            val candidate = candidates.getJSONObject(i)
            if (!coordinates(candidate) || !matches(query, candidate.optString("address"))) continue
            if (
                chosen != null &&
                    (kotlin.math.abs(
                        chosen.getDouble("latitude") - candidate.getDouble("latitude")
                    ) > 0.009 ||
                        kotlin.math.abs(
                            chosen.getDouble("longitude") - candidate.getDouble("longitude")
                        ) > 0.015)
            )
                return JSONObject().put("state", "ambiguous")
            chosen = candidate
        }
        return if (chosen == null) JSONObject().put("state", "not_found")
        else
            JSONObject()
                .put("latitude", chosen.getDouble("latitude"))
                .put("longitude", chosen.getDouble("longitude"))
                .put("state", "address_resolved")
    }

    /** Only the explicitly written postal town may be used as a coarser weather area. */
    @JvmStatic
    fun localityQuery(query: String): String {
        val matcher =
            Pattern.compile("(?<![\\p{L}\\d])(\\d{4,5})\\s+([\\p{L}][^,;\\n]*)").matcher(query)
        var locality = ""
        while (matcher.find()) locality = matcher.group(1)!! + " " + matcher.group(2)!!.trim()
        return locality
    }

    @JvmStatic
    @Throws(Exception::class)
    fun chooseLocality(query: String, candidates: JSONArray): JSONObject {
        val towns = JSONArray()
        for (i in 0 until candidates.length()) {
            val candidate = candidates.getJSONObject(i)
            if (
                candidate.optString("house_number").trim().isEmpty() &&
                    matches(query, candidate.optString("address"))
            )
                towns.put(candidate)
        }
        val chosen = choose(query, towns)
        if (coordinates(chosen))
            chosen
                .put("state", "locality_resolved")
                .put("precision", "locality")
                .put("area_label", query)
        return chosen
    }

    @Throws(Exception::class)
    private fun find(query: String, lookup: Lookup, locality: Boolean): JSONObject =
        try {
            val rows = lookup.find(geocoderQuery(query))
            if (rows == null) JSONObject().put("state", "pending")
            else if (locality) chooseLocality(query, rows) else choose(query, rows)
        } catch (stopped: java.io.InterruptedIOException) {
            throw stopped
        } catch (stopped: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.InterruptedIOException()
        } catch (unavailable: Exception) {
            JSONObject().put("state", "unavailable")
        }

    @JvmStatic
    @Throws(Exception::class)
    fun search(query: String, lookup: Lookup): JSONObject {
        val locality = localityQuery(query)
        val onlyLocality =
            locality.isNotEmpty() &&
                normalized(query.split(Regex("[,;]"), 2)[0]) == normalized(locality)
        val exact = find(query, lookup, onlyLocality)
        if (coordinates(exact) || exact.optString("state") == "pending") return exact
        if (locality.isNotEmpty() && !onlyLocality) {
            val area = find(locality, lookup, true)
            if (
                coordinates(area) ||
                    area.optString("state") in listOf("pending", "ambiguous", "unavailable")
            )
                return area
        }
        return exact
    }

    @JvmStatic
    fun retryable(state: String): Boolean =
        state in listOf("unavailable", "not_found", "ambiguous", "pending")
}
