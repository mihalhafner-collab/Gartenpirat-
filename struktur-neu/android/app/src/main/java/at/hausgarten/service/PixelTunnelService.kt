package at.hausgarten.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.PowerManager
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** User-visible and user-stoppable private MCP responder. */
class PixelTunnelService : Service() {

    private val closing = AtomicBoolean()
    private var worker: Thread? = null
    @Volatile private var http: PixelTunnelWire.HttpTransport? = null
    private var manager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var store: PixelLocalStore? = null
    @Volatile private var activeBinding = ""
    private val control =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "pixel-tunnel-control").apply { isDaemon = true }
        }

    override fun onCreate() {
        super.onCreate()
        val channel =
            NotificationChannel(CHANNEL, "ChatGPT-Verbindung", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )
        startForeground(NOTICE, notice("Verbindung startet"))
        running = true
        closing.set(false)
        manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        callback =
            object : ConnectivityManager.NetworkCallback() {
                private var ready: Network? = null

                override fun onLost(network: Network) {
                    if (network == ready) ready = null
                    http?.close()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        if (network != ready) ready = network
                    } else if (network == ready) ready = null
                }
            }
        manager!!.registerDefaultNetworkCallback(callback!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            closing.set(true)
            http?.close()
            worker?.interrupt()
            control.execute {
                try {
                    RevierApplication.awaitStartup(this)
                    PixelTunnelConfig(this).enabled(false)
                } catch (_: RuntimeException) {} finally {
                    stopSelf(startId)
                }
            }
            return START_NOT_STICKY
        }
        if (worker?.isAlive != true) {
            worker = Thread(::runTunnel, "pixel-mcp-tunnel")
            worker!!.start()
        }
        return START_STICKY
    }

    private fun online(): Boolean {
        val current = manager!!
        val network = current.activeNetwork ?: return false
        return current
            .getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun runTunnel() {
        try {
            if (recoveryStartBlocked(this) || closing.get()) return
            val initial = PixelTunnelConfig(this)
            activeBinding = connectionBinding(initial)
            if (!initial.enabled() || !initial.configured()) {
                status("not_configured", "Pixel-Verbindung einmalig einrichten.")
                return
            }
            PixelLocalStore(this).use { localStore ->
                store = localStore
                runTunnel(localStore)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            if (!closing.get())
                status("local_review_required", "Gespeicherten lokalen Datenstand prüfen.")
        } finally {
            store = null
            if (!closing.get()) stopSelf()
        }
    }

    private fun runTunnel(localStore: PixelLocalStore) {
        var failures = 0
        while (!closing.get()) {
            try {
                val config = PixelTunnelConfig(this)
                if (!config.enabled()) break
                val binding = connectionBinding(config)
                activeBinding = binding
                if (!online()) {
                    status("offline", "Kein Internet. Verbindung wird automatisch erneut versucht.")
                    Thread.sleep(5000)
                    continue
                }
                readState()
                val transport =
                    PixelTunnelWire.HttpTransport(config.secret(), config.organization())
                http = transport
                val allowed =
                    if (config.writeEnabled())
                        object : PixelMcpWriteTools.Sink {
                            override fun writable(): Boolean =
                                PixelTunnelConfig(this@PixelTunnelService).writeEnabled()

                            override fun execute(tool: String, args: JSONObject): JSONObject {
                                if (!PixelTunnelConfig(this@PixelTunnelService).writeEnabled())
                                    throw PixelMcpWriteTools.Rejected(
                                        "WRITE_DISABLED",
                                        "Schreibzugriff wurde beendet.",
                                    )
                                return localStore.execute(tool, args)
                            }

                            override fun status(id: String, state: JSONObject): JSONObject =
                                localStore.status(id, state)
                        }
                    else null
                val tunnel =
                    PixelTunnelWire(
                        config.id(),
                        transport,
                        PixelMcpReadTools(
                            localStore.readSource(),
                            allowed,
                            PixelOwnerAccess(this, localStore),
                        ),
                    )
                status("connecting", "OpenAI-Tunnel wird geprüft.")
                tunnel.verify()
                failures = 0
                while (!closing.get() && config.enabled() && connectionBinding(config) == binding) {
                    if (config.writeEnabled() != (allowed != null)) break
                    val delivered = tunnel.cycle()
                    status(
                        "ready",
                        if (delivered > 0) "Anfrageantwort an den Tunnel übergeben."
                        else "Tunnel erreichbar · wartet auf Anfragen.",
                        contact = true,
                        response = delivered > 0,
                    )
                }
            } catch (limited: PixelTunnelWire.RateLimit) {
                failures = minOf(8, failures + 1)
                status(
                    "rate_limited",
                    "Tunnel-Anfragen vorübergehend begrenzt. Nach der Wartezeit wird erneut verbunden.",
                )
                try {
                    Thread.sleep(maxOf(limited.retryAfterMs, minOf(60000L, 1000L shl failures)))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            } catch (denied: PixelTunnelWire.AccessError) {
                status("auth_error", denied.userMessage())
                val current = PixelTunnelConfig(this)
                if (connectionBinding(current) == activeBinding) current.enabled(false)
                break
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (_: Exception) {
                failures++
                status(
                    "reconnecting",
                    "Verbindung oder lokalen Datenstand prüfen. Neuer Versuch folgt.",
                )
                try {
                    Thread.sleep(minOf(60000L, 1000L shl minOf(failures, 6)))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            } finally {
                http?.close()
            }
        }
    }

    private fun readState(): JSONObject {
        val state = store!!.getState()
        if (state.has("error")) throw IOException("Gespeicherten lokalen Datenstand prüfen.")
        return state
    }

    private fun notice(detail: String): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, PixelTunnelActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, PixelTunnelService::class.java).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Haus & Garten · ChatGPT")
            .setContentText(detail)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Beenden", stop).build())
            .build()
    }

    private fun status(
        state: String,
        detail: String,
        contact: Boolean = false,
        response: Boolean = false,
    ) {
        val prefs = getSharedPreferences("pixel_tunnel_status", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val edit = prefs.edit()
        if (prefs.getString("binding", "") != activeBinding) {
            edit.remove("last_contact_at").remove("last_response_at")
        }
        edit
            .putString("binding", activeBinding)
            .putString("state", state)
            .putString("detail", detail)
            .putLong("at", now)
        if (contact) edit.putLong("last_contact_at", now)
        if (response) edit.putLong("last_response_at", now)
        edit.apply()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTICE,
            notice(detail),
        )
    }

    override fun onDestroy() {
        closing.set(true)
        running = false
        http?.close()
        worker?.interrupt()
        val currentManager = manager
        val currentCallback = callback
        if (currentManager != null && currentCallback != null)
            try {
                currentManager.unregisterNetworkCallback(currentCallback)
            } catch (_: Exception) {}
        control.execute {
            try {
                RevierApplication.awaitStartup(this)
                val prefs = getSharedPreferences("pixel_tunnel_status", MODE_PRIVATE)
                if (
                    prefs.getString("binding", "") == activeBinding &&
                        prefs.getString("state", "") != "auth_error"
                )
                    prefs
                        .edit()
                        .putString("state", "stopped")
                        .putString("detail", "Verbindung beendet.")
                        .apply()
            } catch (_: RuntimeException) {}
        }
        control.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STOP = "at.hausgarten.service.PIXEL_TUNNEL_STOP"
        private const val CHANNEL = "pixel_tunnel"
        private const val NOTICE = 2810
        @Volatile private var running = false

        private fun connectionBinding(config: PixelTunnelConfig): String =
            PixelMcpWriteTools.hash(config.id() + "\n" + config.organization())

        @JvmStatic
        fun recoveryStartBlocked(context: Context): Boolean {
            if (RevierApplication.recoveryRequired()) return true
            return try {
                RestoreStartupGuard.assertStoreMayOpen(context)
                false
            } catch (_: RuntimeException) {
                true
            }
        }

        @JvmStatic
        fun status(context: Context): JSONObject {
            val result = JSONObject()
            val prefs = context.getSharedPreferences("pixel_tunnel_status", MODE_PRIVATE)
            val power = context.getSystemService(POWER_SERVICE) as PowerManager
            try {
                val config = PixelTunnelConfig(context)
                val bound = prefs.getString("binding", "") == connectionBinding(config)
                var state =
                    if (bound) prefs.getString("state", "stopped") ?: "stopped" else "stopped"
                val preserve = state == "auth_error" || state == "start_blocked"
                if (!running && !preserve) state = "stopped"
                val contact = if (bound) prefs.getLong("last_contact_at", 0) else 0
                val response = if (bound) prefs.getLong("last_response_at", 0) else 0
                val stale =
                    running &&
                        state == "ready" &&
                        (contact == 0L || System.currentTimeMillis() - contact > 90000)
                if (stale) state = "reconnecting"
                result
                    .put("running", running)
                    .put("state", state)
                    .put(
                        "detail",
                        if (stale) "Verbindung länger nicht bestätigt. Wird erneut geprüft."
                        else if (!running && !preserve) "Pixel-Verbindung ist beendet."
                        else if (bound)
                            prefs.getString("detail", "Verbindung noch nicht gestartet.")
                        else "Für diese Verbindung liegt noch keine lokale Statusmeldung vor.",
                    )
                    .put(
                        "battery_exempt",
                        power.isIgnoringBatteryOptimizations(context.packageName),
                    )
                    .put("configured", config.configured())
                    .put("enabled", config.enabled())
                    .put("write_enabled", config.writeEnabled())
                    .put("last_contact_at", contact)
                    .put("last_response_at", response)
                    .put(
                        "transport_confirmed",
                        config.enabled() && running && state == "ready" && !stale,
                    )
            } catch (_: Exception) {
                result
                    .put("state", "status_unavailable")
                    .put("detail", "Lokaler Verbindungsstatus derzeit nicht lesbar.")
            }
            return result
        }
    }
}
