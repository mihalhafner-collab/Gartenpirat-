package at.hausgarten.service

import android.content.Context
import android.location.*
import android.os.Build
import org.json.*
import java.time.Instant
import java.util.Locale
import java.util.concurrent.*

/** Android's existing address service, on the planning worker; no GPS or new permission. */
internal object WeatherLocationResolver {
    fun interface Lookup : WeatherLocationRules.Lookup
    class Budget {
        @JvmField var remaining = 3
        @JvmField val deadline = android.os.SystemClock.elapsedRealtime() + 24000
    }
    // At most one legacy lookup may outlive a timeout; never create an unbounded worker queue.
    private val LEGACY = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, SynchronousQueue()) { task -> Thread(task, "weather-address").apply { isDaemon = true } }
    @JvmStatic @Throws(Exception::class) fun resolve(context: Context, store: PixelLocalStore, value: JSONObject, now: Instant, budget: Budget): JSONObject =
        resolve(context, store, value, now, budget, Lookup { query -> lookup(context, query) })
    @JvmStatic @Throws(Exception::class) fun resolve(context: Context, store: PixelLocalStore, value: JSONObject, now: Instant, budget: Budget, lookup: Lookup): JSONObject {
        if (WeatherLocationRules.coordinates(value) || value.optString("weather_location_label").isEmpty()) return value
        val key = WeatherLocationRules.cacheKey(value); val cached = JSONObject(store.ownerValue(key, "{}"))
        if (WeatherLocationRules.coordinates(cached)) return WeatherLocationRules.cached(value, cached)
        try {
            if (cached.optInt("resolver_version") == WeatherLocationRules.RESOLVER_VERSION && cached.optString("state") != "pending" && Instant.parse(cached.getString("attempted_at")).isAfter(now.minusSeconds(3600))) return WeatherLocationRules.cached(value, cached)
        } catch (ignored: Exception) {}
        val result = WeatherLocationRules.search(value.getString("weather_location_label"), WeatherLocationRules.Lookup { query ->
            if (budget.remaining <= 0 || android.os.SystemClock.elapsedRealtime() >= budget.deadline) null else { budget.remaining--; lookup.find(query) }
        })
        result.put("attempted_at", now.toString()).put("resolver_version", WeatherLocationRules.RESOLVER_VERSION)
        store.putOwnerValue(key, result.toString())
        return WeatherLocationRules.cached(value, result)
    }
    @JvmStatic @Throws(Exception::class) fun lookup(context: Context, query: String): JSONArray {
        if (!Geocoder.isPresent()) throw java.io.IOException("Address service unavailable")
        val geocoder = Geocoder(context, Locale.GERMAN)
        val found: List<Address>? = if (Build.VERSION.SDK_INT >= 33) {
            val future = CompletableFuture<List<Address>>()
            geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                override fun onGeocode(result: MutableList<Address>) { future.complete(result) }
                override fun onError(message: String?) { future.completeExceptionally(java.io.IOException("Address lookup unavailable")) }
            })
            future.get(8, TimeUnit.SECONDS)
        } else {
            @Suppress("DEPRECATION") // Blocking geocoder is the API 26-32 path.
            val future = LEGACY.submit(Callable { geocoder.getFromLocationName(query, 5) })
            try { future.get(8, TimeUnit.SECONDS) } finally { future.cancel(true) }
        }
        val out = JSONArray()
        for (address in found.orEmpty()) {
            if (!address.hasLatitude() || !address.hasLongitude()) continue
            val label = StringBuilder()
            for (i in 0..address.maxAddressLineIndex) label.append(' ').append(address.getAddressLine(i))
            for (part in arrayOf(address.thoroughfare, address.subThoroughfare, address.postalCode, address.locality, address.subLocality, address.adminArea, address.countryName)) if (part != null) label.append(' ').append(part)
            out.put(JSONObject().put("address", label.toString()).put("house_number", address.subThoroughfare ?: "").put("postal_code", address.postalCode ?: "").put("locality", address.locality ?: "").put("latitude", address.latitude).put("longitude", address.longitude))
        }
        return out
    }
}
