package at.hausgarten.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/** Reboot/update recovery after explicit opt-in and complete configuration. */
class PixelTunnelBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        executor.execute {
            try {
                // Broadcasts have a short completion deadline; a protected restore resumes in-app.
                try { RevierApplication.awaitStartup(context, 5000) } catch (_: RuntimeException) { return@execute }
                if (PixelTunnelService.recoveryStartBlocked(context)) return@execute
                val config = PixelTunnelConfig(context)
                if (!config.enabled() || !config.configured()) return@execute
                try {
                    context.startForegroundService(Intent(context, PixelTunnelService::class.java))
                } catch (_: RuntimeException) {
                    context.getSharedPreferences("pixel_tunnel_status", Context.MODE_PRIVATE).edit()
                        .putString("state", "start_blocked")
                        .putString("detail", "Android hat den Start eingeschränkt. Bitte Verbindung in der App starten.").apply()
                }
            } finally { pending.finish() }
        }
    }
    companion object {
        private val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "pixel-tunnel-boot").apply { isDaemon = true }
        }
    }
}
