package at.hausgarten.service;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-visible, user-stoppable private MCP responder; no dataSync timeout evasion. */
public final class PixelTunnelService extends Service implements McpSyncHost {
    @Override public void requestMcpSync(){MainActivity.requestMcpSync(this);}
    public static final String STOP="at.hausgarten.service.PIXEL_TUNNEL_STOP";
    private static final String CHANNEL="pixel_tunnel";
    private static final int NOTICE=2810;
    private static volatile boolean running=false;
    private final AtomicBoolean closing=new AtomicBoolean();
    private Thread worker;
    private volatile PixelTunnelWire.HttpTransport http;
    private ConnectivityManager manager;
    private ConnectivityManager.NetworkCallback callback;
    private PixelLocalStore store;
    private boolean recoveryBlocked;
    @Override public void onCreate(){
        super.onCreate();
        if(recoveryStartBlocked(this)){recoveryBlocked=true;stopSelf();return;}
        try{store=new PixelLocalStore(this);}catch(RuntimeException protectedState){recoveryBlocked=true;stopSelf();return;}
        NotificationChannel channel=new NotificationChannel(CHANNEL,"ChatGPT-Verbindung",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        startForeground(NOTICE,notice("Verbindung startet"));running=true;closing.set(false);
        manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        callback=new ConnectivityManager.NetworkCallback(){private Network ready;
            @Override public void onLost(Network network){if(network.equals(ready))ready=null;if(http!=null)http.close();}
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)){if(!network.equals(ready)){ready=network;}}else if(network.equals(ready))ready=null;}
        };
        manager.registerDefaultNetworkCallback(callback);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(recoveryBlocked||recoveryStartBlocked(this)){recoveryBlocked=true;stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&STOP.equals(intent.getAction())){new PixelTunnelConfig(this).enabled(false);stopSelf();return START_NOT_STICKY;}
        PixelTunnelConfig config=new PixelTunnelConfig(this);
        if(!config.enabled()||!config.configured()){status("not_configured","Zweiten Tunnel einmalig einrichten.");stopSelf();return START_NOT_STICKY;}
        if(worker==null||!worker.isAlive()){worker=new Thread(this::runTunnel,"pixel-mcp-tunnel");worker.start();}
        return START_STICKY;
    }
    // Shared with boot/update entry: a held or damaged restore must leave recovery UI usable.
    // This check reads guards only; it never changes user account or tunnel configuration.
    static boolean recoveryStartBlocked(Context context){
        if(RevierApplication.recoveryRequired())return true;
        try{LocalAuthorityGuard.assertStoreMayOpen(context);return false;}catch(RuntimeException held){return true;}
    }
    private boolean online(){Network network=manager.getActiveNetwork();NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    private void runTunnel(){
        int failures=0;
        while(!closing.get()){
            try{
                PixelTunnelConfig config=new PixelTunnelConfig(this);if(!config.enabled())break;
                if(!online()){status("offline","Kein Internet. Verbindung wird automatisch erneut versucht.");Thread.sleep(5000);continue;}
                readState(); // Validate the local state before exposing the existing MCP connection.
                http=new PixelTunnelWire.HttpTransport(config.secret(),config.organization());
                PixelMcpWriteTools.Sink allowed=config.writeEnabled()?new PixelMcpWriteTools.Sink(){
                    public boolean writable(){return new PixelTunnelConfig(PixelTunnelService.this).writeEnabled();}
                    public JSONObject execute(String tool,JSONObject args)throws Exception{
                        if(!new PixelTunnelConfig(PixelTunnelService.this).writeEnabled())throw new PixelMcpWriteTools.Rejected("WRITE_DISABLED","Schreibzugriff wurde beendet.");
                        return store.execute(tool,args);
                    }
                    public JSONObject status(String id,JSONObject state)throws Exception{return store.status(id,state);}
                }:null;
                PixelTunnelWire tunnel=new PixelTunnelWire(config.id(),http,new PixelMcpReadTools(store.readSource(),allowed,new PixelOwnerAccess(this,store)));
                status("connecting","OpenAI-Tunnel wird geprüft.");tunnel.verify();failures=0;
                while(!closing.get()&&config.enabled()){
                    if(config.writeEnabled()!=(allowed!=null))break; // Rebuild the advertised catalogue after a permission change.
                    tunnel.cycle();status("ready",config.writeEnabled()?"Tunnel erreichbar · Betriebsdaten lesen und bearbeiten":"Tunnel erreichbar · Betriebsdaten lesen");
                }
            }catch(PixelTunnelWire.RateLimit limited){
                failures=Math.min(8,failures+1);status("rate_limited","Tunnel-Anfragen vorübergehend begrenzt. Nach der Wartezeit wird erneut verbunden.");
                try{Thread.sleep(Math.max(limited.retryAfterMs,Math.min(60000,1000L<<failures)));}catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}
            }catch(PixelTunnelWire.AccessError denied){status("auth_error",denied.userMessage());new PixelTunnelConfig(this).enabled(false);break;}
            catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}
            catch(Exception failed){
                failures++;status("reconnecting","Verbindung oder lokalen Datenstand prüfen. Neuer Versuch folgt.");
                try{Thread.sleep(Math.min(60000,1000L << Math.min(failures,6)));}catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}
            }finally{if(http!=null)http.close();}
        }
        if(!closing.get())stopSelf();
        store.close();
    }
    private JSONObject readState() throws Exception {
        JSONObject state=store.getState();if(state.has("error"))throw new IOException("Gespeicherten lokalen Datenstand prüfen.");return state;
    }
    private Notification notice(String detail){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,PixelTunnelActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PixelTunnelService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Haus & Garten · ChatGPT")
            .setContentText(detail).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Beenden",stop).build()).build();
    }
    private void status(String state,String detail){
        getSharedPreferences("pixel_tunnel_status",MODE_PRIVATE).edit().putString("state",state).putString("detail",detail).putLong("at",System.currentTimeMillis()).apply();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notice(detail));
    }
    public static JSONObject status(Context context){
        JSONObject result=new JSONObject();SharedPreferences p=context.getSharedPreferences("pixel_tunnel_status",MODE_PRIVATE);PowerManager power=(PowerManager)context.getSystemService(POWER_SERVICE);
        try{
            String state=p.getString("state","stopped");
            boolean preserve=state.equals("auth_error")||state.equals("start_blocked");
            if(!running&&!preserve)state="stopped";
            boolean stale=running&&state.equals("ready")&&System.currentTimeMillis()-p.getLong("at",0)>90000;
            if(stale)state="reconnecting";
            result.put("running",running).put("state",state).put("detail",stale?"Verbindung länger nicht bestätigt. Wird erneut geprüft.":!running&&!preserve?"Pixel-Verbindung ist beendet.":p.getString("detail","Verbindung noch nicht gestartet."))
                .put("battery_exempt",power.isIgnoringBatteryOptimizations(context.getPackageName())).put("configured",new PixelTunnelConfig(context).configured()).put("write_enabled",new PixelTunnelConfig(context).writeEnabled());
        }catch(Exception ignored){}
        return result;
    }
    @Override public void onDestroy(){
        closing.set(true);running=false;if(http!=null)http.close();if(worker!=null)worker.interrupt();
        if(manager!=null&&callback!=null)try{manager.unregisterNetworkCallback(callback);}catch(Exception ignored){}
        if(worker==null&&store!=null){store.close();store=null;}
        SharedPreferences p=getSharedPreferences("pixel_tunnel_status",MODE_PRIVATE);
        if(!"auth_error".equals(p.getString("state","")))p.edit().putString("state","stopped").putString("detail","Verbindung beendet.").apply();
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
        // Worker may still be unwinding its SQLite transaction; it owns the helper lifetime.
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
