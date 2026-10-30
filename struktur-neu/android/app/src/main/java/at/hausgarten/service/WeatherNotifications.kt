package at.hausgarten.service

import android.app.*
import android.content.*
import android.os.Build
import org.json.*

/** Pixel notification boundary. A blocked channel never consumes a risk receipt. */
internal object WeatherNotifications {
    const val CHANNEL = "planning_weather"
    const val ID = 0x484714
    const val TEST_ID = 0x484717
    @JvmStatic fun tag(appointment: String): String = "weather:" + appointment
    @JvmStatic fun state(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return "unavailable"
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return "permission_required"
        if (!manager.areNotificationsEnabled()) return "app_blocked"
        val channel = manager.getNotificationChannel(CHANNEL)
        return if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) "channel_blocked" else "ready"
    }
    @JvmStatic fun ready(context: Context): Boolean = state(context) == "ready"
    @JvmStatic fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Wetterwarnungen für Einsätze", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Wetterrisiken für geplante Arbeiten. Keine automatische Terminänderung."
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(channel)
    }
    @JvmStatic fun open(context: Context, id: String): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .setData(android.net.Uri.parse("hausgarten-internal://weather/" + android.net.Uri.encode(id)))
        .putExtra("open_planning", true).putExtra("planning_appointment_id", id)
    @JvmStatic @Throws(Exception::class) fun post(context: Context, appointment: JSONObject, risk: JSONObject, customer: String?): Boolean {
        if (!ready(context)) return false
        channel(context)
        val id = appointment.getString("id"); val hash = risk.getString("alert_hash"); val text = WeatherAlertRules.message(appointment, risk, customer)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val click = PendingIntent.getActivity(context, 0, open(context, id), flags)
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(text.getString("title")).setContentText(text.getString("body"))
            .setStyle(Notification.BigTextStyle().bigText(text.getString("detail"))).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_EVENT).setGroup("hausgarten_weather").setContentIntent(click).setAutoCancel(true)
        builder.setPublicVersion(Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Haus & Garten · Wetterwarnung").setContentText("Einsatz in der App prüfen.").build())
        builder.addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_view), "Einsatz ansehen", click).build())
        val alternatives = PendingIntent.getActivity(context, 1, open(context, id).putExtra("planning_action", "alternatives"), flags)
        builder.addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_today), "Alternative suchen", alternatives).build())
        val acknowledge = Intent(context, PlanningAlertReceiver::class.java).setData(android.net.Uri.parse("hausgarten-internal://weather-ack/" + android.net.Uri.encode(id) + "/" + hash))
            .putExtra("appointment_id", id).putExtra("alert_hash", hash)
        builder.addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_agenda), "Geprüft", PendingIntent.getBroadcast(context, 0, acknowledge, flags)).build())
        context.getSystemService(NotificationManager::class.java).notify(tag(id), ID, builder.build())
        return true
    }
    @JvmStatic fun test(context: Context): Boolean {
        if (!ready(context)) return false
        channel(context)
        val click = PendingIntent.getActivity(context, TEST_ID, Intent(context, PlanningConnectionActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify("weather:test", TEST_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Test · Haus & Garten Wetterwarnungen")
            .setContentText("Dies ist eine Testmeldung. Es liegt dadurch keine Wetterwarnung vor.")
            .setStyle(Notification.BigTextStyle().bigText("Dies ist eine Testmeldung. Es liegt dadurch keine Wetterwarnung vor."))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(click).setAutoCancel(true).build())
        return true
    }
    @JvmStatic fun cancel(context: Context, id: String) { context.getSystemService(NotificationManager::class.java)?.cancel(tag(id), ID) }
    @JvmStatic fun retain(context: Context, ids: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (notification in manager.activeNotifications) if (notification.id == ID && notification.notification.channelId == CHANNEL &&
            (notification.tag == null || ids.none { tag(it) == notification.tag })) manager.cancel(notification.tag, notification.id)
    }
    @JvmStatic fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (notification in manager.activeNotifications) if (notification.notification.channelId == CHANNEL) manager.cancel(notification.tag, notification.id)
    }
}
