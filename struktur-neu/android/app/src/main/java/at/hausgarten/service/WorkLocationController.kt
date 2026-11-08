package at.hausgarten.service

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/** User-triggered foreground fix only. No tracking, background service or coordinate log. */
internal class WorkLocationController(
    private val activity: Activity,
    private val requestPermissions: (Array<String>) -> Unit,
    private val result: Result,
) {
    fun interface Result { fun deliver(request: String, value: JSONObject) }
    private val handler = Handler(Looper.getMainLooper())
    private val signals = ArrayList<CancellationSignal>()
    private val listeners = ArrayList<LocationListener>()
    private val legacyGeocoder = Executors.newSingleThreadExecutor { task -> Thread(task, "work-address").apply { isDaemon = true } }
    private var manager: LocationManager? = null
    private var request = ""
    private var generation = 0L
    private var locating = false
    private var destroyed = false
    fun request(id: String?) {
        if (destroyed || id == null || !id.matches(Regex("[a-zA-Z0-9_-]{1,120}"))) return
        cancel(); request = id; generation++
        if (!permitted()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        else locate(generation)
    }
    fun permissionResult() {
        if (request.isEmpty()) return
        if (permitted()) locate(generation)
        else finish(generation, message("PERMISSION_DENIED", "Standortzugriff nicht erlaubt. Bitte Kunden manuell auswählen."))
    }
    private fun permitted(): Boolean = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun locate(token: Long) {
        if (token != generation || request.isEmpty() || destroyed) return
        manager = activity.getSystemService(Activity.LOCATION_SERVICE) as LocationManager?
        locating = true
        handler.postDelayed({ if (token == generation && request.isNotEmpty()) finish(token, message("LOCATION_UNAVAILABLE", "Kein aktueller Standort verfügbar. Bitte Kunden manuell auswählen.")) }, 15000)
        var started = false
        for (provider in arrayOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (provider == LocationManager.GPS_PROVIDER && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) continue
            try {
                val currentManager = manager ?: continue
                if (!currentManager.isProviderEnabled(provider)) continue
                started = true
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal(); signals.add(signal)
                    currentManager.getCurrentLocation(provider, signal, activity.mainExecutor) { location -> accept(token, location) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) { accept(token, location) }
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    listeners.add(listener); currentManager.requestLocationUpdates(provider, 0, 0f, listener, Looper.getMainLooper())
                }
            } catch (error: SecurityException) { finish(token, message("PERMISSION_DENIED", "Standortzugriff nicht erlaubt. Bitte Kunden manuell auswählen.")); return }
            catch (ignored: Exception) { /* A second provider or the bounded timeout can still complete. */ }
        }
        if (!started) finish(token, message("LOCATION_DISABLED", "Standort ist ausgeschaltet. Bitte Kunden manuell auswählen."))
    }
    private fun accept(token: Long, fix: Location?) {
        if (token != generation || request.isEmpty() || !locating || fix == null) return
        val age = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
        if (age < 0 || age > 120_000_000_000L || !fix.hasAccuracy() || fix.accuracy > 500) return
        locating = false; stopProviders(); handler.removeCallbacksAndMessages(null)
        val value = JSONObject()
        try {
            value.put("status", "OK").put("latitude", fix.latitude).put("longitude", fix.longitude).put("accuracy_m", fix.accuracy)
                .put("approximate", activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                .put("message", "Standort ermittelt. Adresse und Kunde bitte bestätigen.")
        } catch (ignored: Exception) {}
        handler.postDelayed({ finish(token, value) }, 6000)
        if (!Geocoder.isPresent()) { finish(token, value); return }
        val geocoder = Geocoder(activity, Locale.GERMAN)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(fix.latitude, fix.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { handler.post { address(token, value, addresses) } }
                    override fun onError(error: String?) { handler.post { finish(token, value) } }
                })
            } else legacyGeocoder.execute {
                @Suppress("DEPRECATION") // Blocking geocoder is the API 26-32 path.
                val addresses = try { geocoder.getFromLocation(fix.latitude, fix.longitude, 1) } catch (ignored: Exception) { null }
                handler.post { address(token, value, addresses) }
            }
        } catch (ignored: Exception) { finish(token, value) }
    }
    private fun address(token: Long, value: JSONObject, addresses: List<Address>?) {
        if (token != generation || request.isEmpty()) return
        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]; val place = JSONObject()
            try {
                place.put("street", address.thoroughfare ?: "").put("house_number", address.subThoroughfare ?: "").put("postal_code", address.postalCode ?: "")
                    .put("city", address.locality ?: "").put("country", address.countryName ?: "")
                value.put("address", place)
            } catch (ignored: Exception) {}
        }
        finish(token, value)
    }
    private fun message(code: String, text: String): JSONObject = JSONObject().apply { try { put("status", code).put("message", text) } catch (ignored: Exception) {} }
    private fun finish(token: Long, value: JSONObject) {
        if (token != generation || request.isEmpty() || destroyed) return
        val id = request; request = ""; locating = false; stopProviders(); handler.removeCallbacksAndMessages(null); result.deliver(id, value)
    }
    private fun stopProviders() {
        for (signal in signals) signal.cancel()
        signals.clear()
        manager?.let { manager -> for (listener in listeners) try { manager.removeUpdates(listener) } catch (ignored: Exception) {} }
        listeners.clear()
    }
    fun cancel() { generation++; request = ""; locating = false; stopProviders(); handler.removeCallbacksAndMessages(null) }
    fun destroy() { cancel(); destroyed = true; legacyGeocoder.shutdownNow() }
}
