package at.hausgarten.service;

import android.content.Context;
import android.location.*;
import android.os.Build;
import org.json.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Android's existing address service, on the planning worker; no GPS or new permission. */
final class WeatherLocationResolver {
    interface Lookup extends WeatherLocationRules.Lookup {}
    static final class Budget {int remaining=3;final long deadline=android.os.SystemClock.elapsedRealtime()+24000;}
    // At most one legacy lookup may outlive a timeout; never create an unbounded worker queue.
    private static final ThreadPoolExecutor LEGACY=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"weather-address");t.setDaemon(true);return t;});
    static JSONObject resolve(Context c,PixelLocalStore store,JSONObject context,Instant now,Budget budget)throws Exception {
        return resolve(c,store,context,now,budget,query->lookup(c,query));
    }
    static JSONObject resolve(Context c,PixelLocalStore store,JSONObject context,Instant now,Budget budget,Lookup lookup)throws Exception {
        if(WeatherLocationRules.coordinates(context)||context.optString("weather_location_label").isEmpty())return context;
        String key=WeatherLocationRules.cacheKey(context);JSONObject cached=new JSONObject(store.ownerValue(key,"{}"));
        if(WeatherLocationRules.coordinates(cached))return WeatherLocationRules.cached(context,cached);
        try{if(cached.optInt("resolver_version")==WeatherLocationRules.RESOLVER_VERSION&&!cached.optString("state").equals("pending")&&Instant.parse(cached.getString("attempted_at")).isAfter(now.minusSeconds(3600)))return WeatherLocationRules.cached(context,cached);}catch(Exception ignored){}
        JSONObject result=WeatherLocationRules.search(context.getString("weather_location_label"),query->{
            if(budget.remaining<=0||android.os.SystemClock.elapsedRealtime()>=budget.deadline)return null;budget.remaining--;return lookup.find(query);
        });
        result.put("attempted_at",now.toString()).put("resolver_version",WeatherLocationRules.RESOLVER_VERSION);store.putOwnerValue(key,result.toString());return WeatherLocationRules.cached(context,result);
    }
    static JSONArray lookup(Context c,String query)throws Exception {
        if(!Geocoder.isPresent())throw new java.io.IOException("Address service unavailable");Geocoder geocoder=new Geocoder(c,Locale.GERMAN);List<Address> found;
        if(Build.VERSION.SDK_INT>=33){
            CompletableFuture<List<Address>> future=new CompletableFuture<>();geocoder.getFromLocationName(query,5,new Geocoder.GeocodeListener(){public void onGeocode(List<Address> result){future.complete(result);}public void onError(String message){future.completeExceptionally(new java.io.IOException("Address lookup unavailable"));}});found=future.get(8,TimeUnit.SECONDS);
        }else{Future<List<Address>> future=LEGACY.submit(()->geocoder.getFromLocationName(query,5));try{found=future.get(8,TimeUnit.SECONDS);}finally{future.cancel(true);}}
        JSONArray out=new JSONArray();if(found==null)return out;for(Address a:found){if(!a.hasLatitude()||!a.hasLongitude())continue;
            StringBuilder label=new StringBuilder();for(int i=0;i<=a.getMaxAddressLineIndex();i++)label.append(' ').append(a.getAddressLine(i));
            for(String part:new String[]{a.getThoroughfare(),a.getSubThoroughfare(),a.getPostalCode(),a.getLocality(),a.getSubLocality(),a.getAdminArea(),a.getCountryName()})if(part!=null)label.append(' ').append(part);
            out.put(new JSONObject().put("address",label.toString()).put("house_number",a.getSubThoroughfare()==null?"":a.getSubThoroughfare()).put("postal_code",a.getPostalCode()==null?"":a.getPostalCode()).put("locality",a.getLocality()==null?"":a.getLocality()).put("latitude",a.getLatitude()).put("longitude",a.getLongitude()));
        }return out;
    }
    private WeatherLocationResolver(){}
}
