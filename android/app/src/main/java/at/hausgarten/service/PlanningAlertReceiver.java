package at.hausgarten.service;
import android.content.*;
import org.json.*;

/** Only an immutable owner-device notification intent can acknowledge this exact risk version. */
public final class PlanningAlertReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String id=intent.getStringExtra("appointment_id"),hash=intent.getStringExtra("alert_hash");if(id==null||hash==null||!hash.matches("[0-9a-f]{64}"))return;
        PendingResult pending=goAsync();new Thread(()->{try(PixelLocalStore store=new PixelLocalStore(context)){
            new PixelOwnerAccess(context,store).execute("acknowledge_weather_alert",new JSONObject().put("appointment_id",id).put("expected_alert_hash",hash).put("request_id","notification-ack-"+hash));
            android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);if(manager!=null)manager.cancel(0x484714);
        }catch(Exception stale){/* Stale/deleted risk is never acknowledged as the new version. In-app review remains. */}finally{pending.finish();}}).start();
    }
}
