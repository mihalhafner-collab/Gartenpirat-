package at.hausgarten.service

import android.content.*
import org.json.JSONObject
import kotlinx.coroutines.*

/** Only an immutable owner-device notification intent can acknowledge this exact risk version. */
class PlanningAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("appointment_id") ?: return
        val hash = intent.getStringExtra("alert_hash") ?: return
        if (!hash.matches(Regex("[0-9a-f]{64}"))) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PixelLocalStore(context).use { store ->
                    PixelOwnerAccess(context, store).execute("acknowledge_weather_alert", JSONObject().put("appointment_id", id).put("expected_alert_hash", hash).put("request_id", "notification-ack-" + hash))
                    WeatherNotifications.cancel(context, id)
                    context.getSystemService(android.app.NotificationManager::class.java)?.cancel(WeatherNotifications.ID)
                }
            } catch (stale: Exception) { /* A stale/deleted risk is never acknowledged as the new version. In-app review remains. */ }
            finally { pending.finish() }
        }
    }
}
