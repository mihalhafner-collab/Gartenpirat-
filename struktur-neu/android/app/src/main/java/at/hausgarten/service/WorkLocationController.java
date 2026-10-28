package at.hausgarten.service;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-triggered foreground fix only. No tracking, background service or coordinate log. */
final class WorkLocationController {
    static final int PERMISSION_REQUEST=44;
    interface Result { void deliver(String request,JSONObject value); }
    private final Activity activity;
    private final Result result;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<CancellationSignal> signals=new ArrayList<>();
    private final ArrayList<LocationListener> listeners=new ArrayList<>();
    private final ExecutorService legacyGeocoder=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"work-address");t.setDaemon(true);return t;});
    private LocationManager manager;
    private String request="";
    private long generation;
    private boolean locating;
    private boolean destroyed;
    WorkLocationController(Activity activity,Result result){this.activity=activity;this.result=result;}
    void request(String id){
        if(destroyed||id==null||!id.matches("[a-zA-Z0-9_-]{1,120}"))return;
        cancel();request=id;generation++;
        if(!permitted()){
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSION_REQUEST);
        }else locate(generation);
    }
    boolean permissionResult(int code){
        if(code!=PERMISSION_REQUEST)return false;
        if(!request.isEmpty()){
            if(permitted())locate(generation);
            else finish(generation,message("PERMISSION_DENIED","Standortzugriff nicht erlaubt. Bitte Kunden manuell auswählen."));
        }return true;
    }
    private boolean permitted(){return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void locate(long token){
        if(token!=generation||request.isEmpty()||destroyed)return;
        manager=(LocationManager)activity.getSystemService(Activity.LOCATION_SERVICE);locating=true;
        handler.postDelayed(()->{if(token==generation&&!request.isEmpty())finish(token,message("LOCATION_UNAVAILABLE","Kein aktueller Standort verfügbar. Bitte Kunden manuell auswählen."));},15000);
        boolean started=false;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            if(LocationManager.GPS_PROVIDER.equals(provider)&&activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)continue;
            try{
                if(manager==null||!manager.isProviderEnabled(provider))continue;
                started=true;
                if(Build.VERSION.SDK_INT>=30){
                    CancellationSignal signal=new CancellationSignal();signals.add(signal);
                    manager.getCurrentLocation(provider,signal,activity.getMainExecutor(),location->accept(token,location));
                }else{
                    LocationListener listener=new LocationListener(){
                        @Override public void onLocationChanged(Location location){accept(token,location);}
                        @Override public void onStatusChanged(String provider,int status,Bundle extras){}
                        @Override public void onProviderEnabled(String provider){}
                        @Override public void onProviderDisabled(String provider){}
                    };listeners.add(listener);manager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());
                }
            }catch(SecurityException error){finish(token,message("PERMISSION_DENIED","Standortzugriff nicht erlaubt. Bitte Kunden manuell auswählen."));return;}
            catch(Exception ignored){/* A second provider or the bounded timeout can still complete. */}
        }
        if(!started)finish(token,message("LOCATION_DISABLED","Standort ist ausgeschaltet. Bitte Kunden manuell auswählen."));
    }
    private void accept(long token,Location fix){
        if(token!=generation||request.isEmpty()||!locating||fix==null)return;
        long ageNanos=android.os.SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos();
        if(ageNanos<0||ageNanos>120_000_000_000L||!fix.hasAccuracy())return;
        // Wait briefly for GPS if a very imprecise network fix arrives first.
        if(fix.getAccuracy()>500)return;
        locating=false;stopProviders();handler.removeCallbacksAndMessages(null);
        JSONObject value=new JSONObject();
        try{value.put("status","OK").put("latitude",fix.getLatitude()).put("longitude",fix.getLongitude()).put("accuracy_m",fix.getAccuracy()).put("approximate",activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED).put("message","Standort ermittelt. Adresse und Kunde bitte bestätigen.");}catch(Exception ignored){}
        handler.postDelayed(()->finish(token,value),6000);
        if(!Geocoder.isPresent()){finish(token,value);return;}
        Geocoder geocoder=new Geocoder(activity,Locale.GERMAN);
        try{
            if(Build.VERSION.SDK_INT>=33){
                geocoder.getFromLocation(fix.getLatitude(),fix.getLongitude(),1,new Geocoder.GeocodeListener(){
                    @Override public void onGeocode(List<Address> addresses){handler.post(()->address(token,value,addresses));}
                    @Override public void onError(String error){handler.post(()->finish(token,value));}
                });
            }else legacyGeocoder.execute(()->{
                List<Address> addresses=null;try{addresses=geocoder.getFromLocation(fix.getLatitude(),fix.getLongitude(),1);}catch(Exception ignored){}
                final List<Address> resolved=addresses;handler.post(()->address(token,value,resolved));
            });
        }catch(Exception ignored){finish(token,value);}
    }
    private void address(long token,JSONObject value,List<Address> addresses){
        if(token!=generation||request.isEmpty())return;
        if(addresses!=null&&!addresses.isEmpty()){
            Address a=addresses.get(0);JSONObject place=new JSONObject();
            try{place.put("street",a.getThoroughfare()==null?"":a.getThoroughfare()).put("house_number",a.getSubThoroughfare()==null?"":a.getSubThoroughfare()).put("postal_code",a.getPostalCode()==null?"":a.getPostalCode()).put("city",a.getLocality()==null?"":a.getLocality()).put("country",a.getCountryName()==null?"":a.getCountryName());value.put("address",place);}catch(Exception ignored){}
        }finish(token,value);
    }
    private JSONObject message(String code,String text){JSONObject value=new JSONObject();try{value.put("status",code).put("message",text);}catch(Exception ignored){}return value;}
    private void finish(long token,JSONObject value){
        if(token!=generation||request.isEmpty()||destroyed)return;
        String id=request;request="";locating=false;stopProviders();handler.removeCallbacksAndMessages(null);result.deliver(id,value);
    }
    private void stopProviders(){
        for(CancellationSignal signal:signals)signal.cancel();signals.clear();
        if(manager!=null)for(LocationListener listener:listeners)try{manager.removeUpdates(listener);}catch(Exception ignored){}
        listeners.clear();
    }
    void cancel(){generation++;request="";locating=false;stopProviders();handler.removeCallbacksAndMessages(null);}
    void destroy(){cancel();destroyed=true;legacyGeocoder.shutdownNow();}
}
