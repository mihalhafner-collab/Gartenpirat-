package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

/**
 * Permanent no-subscription weather sources.
 *
 * Long range: ECMWF Open Charts (CC BY 4.0) is used only as an early 15-day
 * outlook/availability signal. It is deliberately not converted into a hard
 * work warning because the public Open Charts endpoint is graphical.
 *
 * Actionable forecast: GeoSphere Austria Data Hub nwp-v2-1h-1km (CC BY 4.0).
 * Official warnings: GeoSphere Austria warning API.
 *
 * No API key, paid account or business secret is required.
 */
final class FreeWeatherSource {
    static final long HOUR=3600L, DAY=24*HOUR, MONITOR_WINDOW=14*DAY;
    static final String ATTRIBUTION="Datenquelle: GeoSphere Austria - https://data.hub.geosphere.at; Langfristblick: ECMWF Open Charts (CC BY 4.0)";

    static long intervalSeconds(long untilStart){
        if(untilStart>MONITOR_WINDOW)return Math.max(60,untilStart-MONITOR_WINDOW);
        if(untilStart>7*DAY)return DAY;
        if(untilStart>4*DAY)return 12*HOUR;
        if(untilStart>60*HOUR)return 6*HOUR;
        if(untilStart>24*HOUR)return 3*HOUR;
        if(untilStart>6*HOUR)return HOUR;
        if(untilStart>HOUR)return 30*60L;
        return 15*60L;
    }

    static long nextDelayMillis(JSONArray appointments,Instant now)throws Exception{
        long best=Long.MAX_VALUE;
        for(int i=0;i<appointments.length();i++){
            JSONObject a=appointments.getJSONObject(i);
            if(!AppointmentRules.active(a)||!a.has("starts_at"))continue;
            Instant start=AppointmentRules.instant(a.getString("starts_at")),end=AppointmentRules.instant(a.getString("ends_at"));
            if(!end.isAfter(now))continue;
            long delta=Math.max(0,Duration.between(now,start).getSeconds());
            long wait=intervalSeconds(delta);
            if(delta>MONITOR_WINDOW)wait=Math.max(60,delta-MONITOR_WINDOW);
            best=Math.min(best,wait*1000L);
        }
        return best;
    }

    static boolean due(JSONObject cache,String key,Instant now,long maxAgeSeconds){
        try{return Instant.parse(cache.getString(key)).isBefore(now.minusSeconds(maxAgeSeconds));}
        catch(Exception missing){return true;}
    }

    static JSONObject refreshSite(String loc,JSONObject cache,Instant now,long untilStart,DriveRestClient.Wire wire)throws Exception{
        String[] coords=loc.split(",");
        if(coords.length!=2)throw new IllegalArgumentException("Ungültige Einsatzkoordinaten.");
        double lat=Double.parseDouble(coords[0]),lon=Double.parseDouble(coords[1]);
        long cadence=intervalSeconds(untilStart);

        if(untilStart<=MONITOR_WINDOW&&untilStart>60*HOUR&&due(cache,"early_checked_at",now,cadence)){
            JSONObject early=new JSONObject().put("checked_at",now.toString()).put("source","ECMWF Open Charts").put("forecast_only",true);
            try{
                String url="https://charts.ecmwf.int/opencharts-api/v1/products/opencharts_meteogram/?epsgram=classical_15d_with_climate&lat="+lat+"&lon="+lon;
                DriveRestClient.Reply reply=wire.send("GET",url,Collections.emptyMap(),null,256*1024);
                if(reply.status==200){
                    JSONObject body=reply.json(),meta=body.optJSONObject("meta"),data=body.optJSONObject("data"),link=data==null?null:data.optJSONObject("link");
                    String license=meta==null?"":meta.optString("license"),href=link==null?"":link.optString("href");
                    boolean valid=license.toUpperCase(Locale.ROOT).contains("CC-BY-4.0")&&href.startsWith("https://charts.ecmwf.int/");
                    early.put("available",valid);
                    if(valid)early.put("chart_url",href);
                }else early.put("available",false);
            }catch(Exception unavailable){early.put("available",false);}
            cache.put("early_outlook",early).put("early_checked_at",now.toString());
        }

        if(untilStart<=4*DAY&&due(cache,"warnings_fetched_at",now,Math.max(15*60L,Math.min(cadence,3*HOUR)))){
            try{
                DriveRestClient.Reply warnings=wire.send("GET","https://warnungen.zamg.at/wsapp/api/getWarningsForCoords?lat="+lat+"&lon="+lon+"&lang=de",Collections.emptyMap(),null,512*1024);
                if(warnings.status!=200)throw new java.io.IOException();
                cache.put("warnings",WeatherRiskRules.parseWarnings(warnings.json())).put("warnings_fetched_at",now.toString()).remove("warning_status");
            }catch(Exception unavailable){cache.put("warning_status","Amtliche Warnungen konnten zuletzt nicht aktualisiert werden.");}
        }

        if(untilStart<=60*HOUR&&due(cache,"fetched_at",now,cadence)){
            String params="t2m,rain_acc,ugust,vgust";
            String url="https://dataset.api.hub.geosphere.at/v1/timeseries/forecast/nwp-v2-1h-1km?parameters="+params+"&lat_lon="+lat+","+lon+"&output_format=geojson";
            DriveRestClient.Reply reply=wire.send("GET",url,Collections.emptyMap(),null,1024*1024);
            if(reply.status==429)throw new DriveRestClient.Failure("WEATHER_RETRY","Wetterdienst vorübergehend begrenzt.",30_000,false);
            if(reply.status!=200)throw new java.io.IOException("GeoSphere-Wetterdaten derzeit nicht verfügbar.");
            cache.put("forecast",normalizeGeoSphere(reply.json())).put("fetched_at",now.toString()).put("forecast_provider","geosphere_nwp_v2").put("source",ATTRIBUTION);
        }

        cache.put("monitoring",new JSONObject()
            .put("active",untilStart<=MONITOR_WINDOW)
            .put("first_check_days_before",14)
            .put("next_interval_seconds",cadence)
            .put("source",ATTRIBUTION));
        return cache;
    }

    static JSONObject normalizeGeoSphere(JSONObject raw)throws Exception{
        JSONArray times=raw.optJSONArray("timestamps"),features=raw.optJSONArray("features");
        if(times==null||features==null||features.length()==0)throw new java.io.IOException("GeoSphere-Vorhersage unvollständig.");
        JSONObject parameters=features.getJSONObject(0).getJSONObject("properties").getJSONObject("parameters");
        JSONArray temp=data(parameters,"t2m"),rainAcc=data(parameters,"rain_acc"),uGust=data(parameters,"ugust"),vGust=data(parameters,"vgust");
        int n=times.length();if(n<2||n>200||temp.length()<n||rainAcc.length()<n||uGust.length()<n||vGust.length()<n)throw new java.io.IOException("GeoSphere-Zeitreihe unvollständig.");
        JSONObject hourly=new JSONObject();JSONArray t=new JSONArray(),temperature=new JSONArray(),rain=new JSONArray(),gust=new JSONArray();
        Double previousRain=null;
        for(int i=0;i<n;i++){
            t.put(parseTime(times.get(i)));
            temperature.put(numberOrNull(temp,i));
            Double current=doubleOrNull(rainAcc,i);
            if(current==null)rain.put(JSONObject.NULL);
            else {double amount=previousRain==null?0:Math.max(0,current-previousRain);rain.put(amount);previousRain=current;}
            Double u=doubleOrNull(uGust,i),v=doubleOrNull(vGust,i);
            gust.put(u==null||v==null?JSONObject.NULL:Math.sqrt(u*u+v*v)*3.6);
        }
        hourly.put("time",t).put("temperature_2m",temperature).put("precipitation",rain).put("wind_gusts_10m",gust);
        return new JSONObject().put("hourly",hourly);
    }

    private static JSONArray data(JSONObject parameters,String name)throws Exception{
        JSONObject p=parameters.optJSONObject(name);JSONArray rows=p==null?null:p.optJSONArray("data");
        if(rows==null)throw new java.io.IOException("GeoSphere-Parameter fehlt: "+name);
        return rows;
    }
    private static Object numberOrNull(JSONArray rows,int i){Double n=doubleOrNull(rows,i);return n==null?JSONObject.NULL:n;}
    private static Double doubleOrNull(JSONArray rows,int i){
        try{if(i<0||i>=rows.length()||rows.isNull(i))return null;double n=rows.getDouble(i);return Double.isFinite(n)?n:null;}catch(Exception e){return null;}
    }
    private static long parseTime(Object value)throws Exception{
        if(value instanceof Number){long n=((Number)value).longValue();return n>100000000000L?n/1000:n;}
        String s=String.valueOf(value);try{return Instant.parse(s).getEpochSecond();}catch(Exception ignored){}
        return OffsetDateTime.parse(s).toInstant().getEpochSecond();
    }
    private FreeWeatherSource(){}
}
