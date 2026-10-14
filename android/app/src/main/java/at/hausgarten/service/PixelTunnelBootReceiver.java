package at.hausgarten.service;
import android.content.*;
/** Reboot/update recovery, only after the user has explicitly enabled the configured connection. */
public final class PixelTunnelBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
        PixelTunnelConfig config=new PixelTunnelConfig(context);if(!config.enabled()||!config.configured())return;
        try{context.startForegroundService(new Intent(context,PixelTunnelService.class));}
        catch(RuntimeException denied){context.getSharedPreferences("pixel_tunnel_status",Context.MODE_PRIVATE).edit().putString("state","start_blocked").putString("detail","Android hat den Start eingeschränkt. Bitte Verbindung in der App starten.").apply();}
    }
}
