package at.hausgarten.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Lock screen notice for running work. Its actions open the app and use the usual timer path. */
internal object WorkNotice {
    const val EXTRA = "work_action"
    private const val CHANNEL = "work_time"
    private const val ID = 0x574b31

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

    /** Shows running, paused or stopped work; any other state removes the notice. No business data is shown. */
    fun update(context: Context, session: JSONObject) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val state = session.optString("state")
        if (state !in setOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW") || !allowed(context)) { manager.cancel(ID); return }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Laufende Arbeitszeit", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Arbeitszeit mit Pause und Stopp, auch auf dem Sperrbildschirm."
            setShowBadge(false)
        })
        val id = session.optString("id", session.optString("session_id"))
        fun open(action: String, code: Int) = PendingIntent.getActivity(context, code,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.parse("hausgarten-internal://work/$action")).putExtra(EXTRA, action).putExtra("session_id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(label: String, action: String, code: Int) =
            Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_recent_history), label, open(action, code)).build()
        val worked = session.optLong("worked_ms").coerceAtLeast(0) / 60000
        val started = session.optLong("started_at_ms").takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("Europe/Vienna")).toLocalTime().toString().take(5) }
        @SuppressLint("InlinedApi") // Older Android versions ignore the newer stopwatch category.
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(state != "STOPPED_PENDING_REVIEW")
            .setContentIntent(open("open", 0))
        when (state) {
            "RUNNING" -> builder.setContentTitle("Arbeit läuft").setContentText(started?.let { "seit $it Uhr" } ?: "Pause oder Stopp direkt hier")
                .setWhen(System.currentTimeMillis() - session.optLong("worked_ms").coerceAtLeast(0)).setUsesChronometer(true).setShowWhen(true)
                .addAction(action("Pause", "start_break", 1)).addAction(action("Foto", "photo", 2)).addAction(action("Stopp", "stop_work", 3))
            "PAUSED" -> builder.setContentTitle("Arbeit pausiert").setContentText("Bisher ${worked / 60} Std. ${worked % 60} Min.").setShowWhen(false)
                .addAction(action("Fortsetzen", "end_break", 4)).addAction(action("Stopp", "stop_work", 3))
            else -> builder.setContentTitle("Arbeit noch abschließen").setContentText("Zeit gestoppt · zum Speichern antippen").setShowWhen(false)
        }
        manager.notify(ID, builder.build())
    }
}

/** Quick settings tile: opens the timer like the "Arbeit starten" shortcut. */
class WorkTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Arbeit"; updateTile() }
    }

    // The Intent variant runs only below Android 14, where it is the only API.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.SHORTCUT, "start")
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
