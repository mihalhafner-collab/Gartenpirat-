package at.hausgarten.service;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;

/** Pixel notification boundary. A blocked channel never consumes a risk receipt. */
final class WeatherNotifications {
    static final String CHANNEL="planning_weather";
    static final int ID=0x484714,TEST_ID=0x484717;
    static String tag(String appointment){return "weather:"+appointment;}
    static String state(Context c){
        NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return "unavailable";
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return "permission_required";
        if(!manager.areNotificationsEnabled())return "app_blocked";
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        return channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE?"channel_blocked":"ready";
    }
    static boolean ready(Context c){return state(c).equals("ready");}
    static void channel(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);if(m==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Wetterwarnungen für Einsätze",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Wetterrisiken für geplante Arbeiten. Keine automatische Terminänderung.");channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);m.createNotificationChannel(channel);
    }
    static Intent open(Context c,String id){return new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(android.net.Uri.parse("hausgarten-internal://weather/"+android.net.Uri.encode(id))).putExtra("open_planning",true).putExtra("planning_appointment_id",id);}
    static boolean post(Context c,JSONObject appointment,JSONObject risk,String customer)throws Exception {
        if(!ready(c))return false;channel(c);String id=appointment.getString("id"),hash=risk.getString("alert_hash");JSONObject text=WeatherAlertRules.message(appointment,risk,customer);
        PendingIntent click=PendingIntent.getActivity(c,0,open(c,id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(text.getString("title")).setContentText(text.getString("body")).setStyle(new Notification.BigTextStyle().bigText(text.getString("detail"))).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_EVENT).setGroup("hausgarten_weather").setContentIntent(click).setAutoCancel(true);
        builder.setPublicVersion(new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("Haus & Garten · Wetterwarnung").setContentText("Einsatz in der App prüfen.").build());
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_view),"Einsatz ansehen",click).build());
        PendingIntent alternatives=PendingIntent.getActivity(c,1,open(c,id).putExtra("planning_action","alternatives"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_today),"Alternative suchen",alternatives).build());
        Intent ack=new Intent(c,PlanningAlertReceiver.class).setData(android.net.Uri.parse("hausgarten-internal://weather-ack/"+android.net.Uri.encode(id)+"/"+hash)).putExtra("appointment_id",id).putExtra("alert_hash",hash);
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_agenda),"Geprüft",PendingIntent.getBroadcast(c,0,ack,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());
        c.getSystemService(NotificationManager.class).notify(tag(id),ID,builder.build());return true;
    }
    static boolean test(Context c){if(!ready(c))return false;channel(c);PendingIntent click=PendingIntent.getActivity(c,TEST_ID,new Intent(c,PlanningConnectionActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(NotificationManager.class).notify("weather:test",TEST_ID,new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Test · Haus & Garten Wetterwarnungen").setContentText("Dies ist eine Testmeldung. Es liegt dadurch keine Wetterwarnung vor.").setStyle(new Notification.BigTextStyle().bigText("Dies ist eine Testmeldung. Es liegt dadurch keine Wetterwarnung vor.")).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(click).setAutoCancel(true).build());return true;}
    static void cancel(Context c,String id){NotificationManager m=c.getSystemService(NotificationManager.class);if(m!=null)m.cancel(tag(id),ID);}
    static void retain(Context c,java.util.Set<String> ids){NotificationManager m=c.getSystemService(NotificationManager.class);if(m==null)return;for(android.service.notification.StatusBarNotification n:m.getActiveNotifications())if(n.getId()==ID&&CHANNEL.equals(n.getNotification().getChannelId())&&(n.getTag()==null||!ids.stream().anyMatch(id->tag(id).equals(n.getTag()))))m.cancel(n.getTag(),n.getId());}
    static void cancelAll(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);if(m==null)return;for(android.service.notification.StatusBarNotification n:m.getActiveNotifications())if(CHANNEL.equals(n.getNotification().getChannelId()))m.cancel(n.getTag(),n.getId());}
    private WeatherNotifications(){}
}
