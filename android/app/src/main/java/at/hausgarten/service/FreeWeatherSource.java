package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Fixed, keyless GeoSphere adapters. Unknown format/unit/licence fails closed. */
final class FreeWeatherSource {
    static final long HOUR=3600L, DAY=24*HOUR, MONITOR_WINDOW=14*DAY;
    static final String API="https://dataset.api.hub.geosphere.at/v1/timeseries/forecast/";
    static final String LICENCE="https://dataset.api.hub.geosphere.at/v1/docs/index.html";
    static final String ATTRIBUTION="GeoSphere Austria · CC BY 4.0 · https://data.hub.geosphere.at";

    static long intervalSeconds(long untilStart){
        if(untilStart>MONITOR_WINDOW)return Math.max(60,untilStart-MONITOR_WINDOW);
        if(untilStart>7*DAY)return DAY;
        if(untilStart>72*HOUR)return 12*HOUR;
        if(untilStart>24*HOUR)return 6*HOUR;
        if(untilStart>6*HOUR)return 3*HOUR;
        return HOUR;
    }
    static long nextDelayMillis(JSONArray appointments,Instant now){
        long best=Long.MAX_VALUE;
        for(int i=0;i<appointments.length();i++)try{
            JSONObject a=appointments.getJSONObject(i);
            if(!AppointmentRules.active(a)||a.optString("status").equals("draft"))continue;
            Instant start=AppointmentRules.instant(a.getString("starts_at")),end=AppointmentRules.instant(a.getString("ends_at"));
            if(!end.isAfter(now))continue;
            best=Math.min(best,intervalSeconds(Math.max(0,Duration.between(now,start).getSeconds()))*1000L);
        }catch(Exception legacy){/* An incomplete historical row must not disable other appointments. */}
        return best;
    }
    static boolean due(JSONObject cache,String key,Instant now,long age){
        try{Instant at=Instant.parse(cache.getString(key));return at.isAfter(now.plusSeconds(300))||!at.isAfter(now.minusSeconds(age));}
        catch(Exception missing){return true;}
    }
    static DriveRestClient.Reply fetch(DriveRestClient.Wire wire,String url,int max,Instant now)throws Exception{
        DriveRestClient.Reply reply=wire.send("GET",url,Collections.emptyMap(),null,max);
        if(reply.status==429||reply.status>=500){
            long delay=30000;String retry=reply.header("Retry-After");
            try{delay=Math.max(delay,Long.parseLong(retry)*1000);}catch(Exception numeric){try{delay=Math.max(delay,Duration.between(now,ZonedDateTime.parse(retry,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());}catch(Exception ignored){}}
            throw new DriveRestClient.Failure("WEATHER_RETRY","Wetterdaten derzeit nicht verfügbar.",Math.min(24*HOUR*1000,delay),false);
        }
        if(reply.status!=200)throw new DriveRestClient.Failure("WEATHER_UNAVAILABLE","Wetterdaten derzeit nicht verfügbar.",0,reply.status==401||reply.status==403);
        return reply;
    }
    static void requireLicence(JSONObject cache,Instant now,DriveRestClient.Wire wire)throws Exception{
        if(!due(cache,"licence_checked_at",now,7*DAY))return;
        String html=new String(fetch(wire,LICENCE,512*1024,now).body,StandardCharsets.UTF_8);
        String plain=html.replaceAll("<[^>]*>"," ").replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        if(!plain.contains("all data that is publicly accessible without authentication")||!html.contains("creativecommons.org/licenses/by/4.0")){
            cache.remove("licence_checked_at");throw new DriveRestClient.Failure("WEATHER_LICENCE_UNVERIFIED","Wetterdaten derzeit nicht verfügbar. Kostenfreier öffentlicher Datenzugang ist nicht bestätigt.",0,true);
        }
        cache.put("licence_checked_at",now.toString());
    }
    static JSONObject parameters(JSONObject metadata)throws Exception{
        JSONObject object=metadata.optJSONObject("parameters");if(object!=null)return object;
        JSONArray list=metadata.optJSONArray("parameters");if(list==null)throw new IOException("Wetterdaten derzeit nicht verfügbar.");
        JSONObject out=new JSONObject();for(int i=0;i<list.length();i++){JSONObject p=list.getJSONObject(i);String id=p.optString("name",p.optString("id"));if(id.isEmpty()||out.has(id))throw new IOException("Wetterparameter nicht eindeutig.");out.put(id,p);}return out;
    }
    static JSONObject metadata(String dataset,JSONObject cache,Instant now,DriveRestClient.Wire wire)throws Exception{
        String key=dataset+":metadata";
        if(due(cache,key+":at",now,DAY)){
            JSONObject m=fetch(wire,API+dataset+"/metadata",1024*1024,now).json();parameters(m);
            cache.put(key,m).put(key+":at",now.toString());
        }
        return cache.getJSONObject(key);
    }
    static double[] coords(String location)throws Exception{
        String[] pair=location.split(",");if(pair.length!=2)throw new IOException("Einsatzort für Wetterprüfung ergänzen.");
        double lat=Double.parseDouble(pair[0]),lon=Double.parseDouble(pair[1]);if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat< -90||lat>90||lon< -180||lon>180)throw new IOException("Einsatzort für Wetterprüfung ergänzen.");return new double[]{lat,lon};
    }
    static JSONObject response(String dataset,String requested,String loc,JSONObject meta,DriveRestClient.Wire wire,Instant now)throws Exception{
        double[] point=coords(loc);JSONObject raw=fetch(wire,API+dataset+"?parameters="+requested+"&lat_lon="+point[0]+","+point[1]+"&output_format=geojson",1024*1024,now).json();
        JSONArray features=raw.optJSONArray("features");if(features==null||features.length()!=1)throw new IOException("Wetterort nicht eindeutig.");
        JSONObject geometry=features.getJSONObject(0).optJSONObject("geometry");JSONArray coordinates=geometry==null?null:geometry.optJSONArray("coordinates");
        if(coordinates==null||coordinates.length()<2||Math.abs(coordinates.getDouble(1)-point[0])>.05||Math.abs(coordinates.getDouble(0)-point[1])>.08)throw new IOException("Wetterdaten für diesen Einsatzort nicht verfügbar.");
        JSONObject actual=features.getJSONObject(0).getJSONObject("properties").getJSONObject("parameters"),declared=parameters(meta);
        for(String id:requested.split(",")){JSONObject p=actual.optJSONObject(id),m=declared.optJSONObject(id);if(p==null||m==null)throw new IOException("Wetterparameter fehlt.");if(p.optString("unit").isEmpty())p.put("unit",m.getString("unit"));else if(!p.getString("unit").equals(m.getString("unit")))throw new IOException("Wettereinheiten widersprüchlich.");}
        return raw;
    }
    static JSONObject refreshSite(String loc,JSONObject cache,Instant now,long untilStart,DriveRestClient.Wire wire)throws Exception{
        long cadence=intervalSeconds(untilStart);
        cache.put("monitoring",new JSONObject().put("active",untilStart<=MONITOR_WINDOW).put("first_check_days_before",14).put("next_interval_seconds",cadence).put("source",ATTRIBUTION));
        if(untilStart>MONITOR_WINDOW)return cache; // No request before T-14.
        coords(loc);requireLicence(cache,now,wire);
        JSONObject meta=metadata("nwp-v2-1h-1km",cache,now,wire);
        cache.put("availability_checked_at",now.toString()).put("source",ATTRIBUTION);
        if(untilStart<=4*DAY&&due(cache,"warnings_fetched_at",now,cadence)){
            double[] point=coords(loc);
            try{JSONObject warnings=fetch(wire,"https://warnungen.zamg.at/wsapp/api/getWarningsForCoords?lat="+point[0]+"&lon="+point[1]+"&lang=de",512*1024,now).json();
                cache.put("warnings",WeatherRiskRules.parseWarnings(warnings)).put("warnings_fetched_at",now.toString()).remove("warning_status");
            }catch(DriveRestClient.Failure unavailable){cache.put("warning_status","Amtliche Warnungen derzeit nicht verfügbar.");throw unavailable;}
            catch(Exception unavailable){cache.put("warning_status","Amtliche Warnungen derzeit nicht verfügbar.");}
        }
        if(untilStart<=60*HOUR&&due(cache,"fetched_at",now,cadence)){
            JSONObject p=parameters(meta);boolean interval=p.has("2t")&&p.has("tp")&&p.has("10fg");String names=interval?"2t,tp,10fg":"t2m,rain_acc,ugust,vgust";
            for(String id:names.split(","))if(!p.has(id))throw new IOException("Wetterdaten derzeit nicht verfügbar.");
            for(String id:interval?new String[]{"10u","10v","sf"}:new String[]{"u10m","v10m","snow_acc"})if(p.has(id))names+=","+id;
            JSONObject raw=response("nwp-v2-1h-1km",names,loc,meta,wire,now);
            cache.put("forecast",normalizeGeoSphere(raw)).put("fetched_at",now.toString()).put("forecast_provider","geosphere_nwp_v2");
        }
        return cache;
    }
    static void refreshNowcast(String loc,JSONObject cache,Instant now,DriveRestClient.Wire wire)throws Exception{
        if(!due(cache,"nowcast_fetched_at",now,HOUR))return;
        requireLicence(cache,now,wire);
        JSONObject m=metadata("nowcast-v1-15min-1km",cache,now,wire),p=parameters(m);
        for(String id:new String[]{"t2m","rr","fx"})if(!p.has(id))throw new IOException("Kurzfristwetter derzeit nicht verfügbar.");
        JSONObject raw=response("nowcast-v1-15min-1km","t2m,rr,fx",loc,m,wire,now);
        cache.put("nowcast",normalizeNowcast(raw)).put("nowcast_fetched_at",now.toString());
    }
    static JSONObject normalized(JSONObject raw,boolean nowcast)throws Exception{
        JSONArray times=raw.optJSONArray("timestamps"),features=raw.optJSONArray("features");
        if(times==null||times.length()<2||times.length()>200||features==null||features.length()!=1)throw new IOException("Wetterzeitreihe unvollständig.");
        JSONObject p=features.getJSONObject(0).getJSONObject("properties").getJSONObject("parameters"),out=new JSONObject();
        JSONArray t=new JSONArray(),temp=new JSONArray(),rain=new JSONArray(),gust=new JSONArray(),snow=new JSONArray(),wind=new JSONArray();
        boolean interval=!nowcast&&p.has("2t");String tempId=interval?"2t":"t2m",rainId=nowcast?"rr":interval?"tp":"rain_acc",snowId=interval?"sf":"snow_acc",uId=interval?"10u":"u10m",vId=interval?"10v":"v10m";
        JSONArray rainData=data(p,rainId),snowData=p.has(snowId)?data(p,snowId):null;
        Double previousRain=null,previousSnow=null;long previous=Long.MIN_VALUE;
        for(int i=0;i<times.length();i++){
            long stamp=parseTime(times.get(i));if(previous!=Long.MIN_VALUE&&stamp-previous!=(nowcast?900:3600))throw new IOException("Wetterzeitreihe widersprüchlich.");previous=stamp;t.put(stamp);
            temp.put(convert(p,tempId,i,"temperature"));
            Double current=number(rainData,i),currentSnow=snowData==null?null:number(snowData,i);
            double factor=factor(p.getJSONObject(rainId).getString("unit"),"rain");
            // NWP tp/sf and INCA rr are sums for the preceding interval, not
            // cumulative model totals. Legacy rain_acc caches still use deltas.
            if(nowcast||interval){rain.put(current==null?JSONObject.NULL:current*factor);}
            else{rain.put(current==null||previousRain==null||current<previousRain?JSONObject.NULL:(current-previousRain)*factor);previousRain=current;}
            if(snowData!=null){double snowFactor=factor(p.getJSONObject(snowId).getString("unit"),"rain");snow.put(interval?(currentSnow==null?JSONObject.NULL:currentSnow*snowFactor):currentSnow==null||previousSnow==null||currentSnow<previousSnow?JSONObject.NULL:(currentSnow-previousSnow)*snowFactor);previousSnow=currentSnow;}
            if(nowcast)gust.put(convert(p,"fx",i,"wind"));
            else if(interval)gust.put(convert(p,"10fg",i,"wind"));
            else{Double u=value(p,"ugust",i,"wind"),v=value(p,"vgust",i,"wind");gust.put(u==null||v==null?JSONObject.NULL:Math.hypot(u,v));}
            if(p.has(uId)&&p.has(vId)){Double u=value(p,uId,i,"wind"),v=value(p,vId,i,"wind");wind.put(u==null||v==null?JSONObject.NULL:Math.hypot(u,v));}
        }
        out.put("time",t).put("temperature_2m",temp).put("precipitation",rain).put("wind_gusts_10m",gust);
        if(snowData!=null)out.put("snowfall",snow);if(wind.length()>0)out.put("wind_speed_10m",wind);
        return new JSONObject().put(nowcast?"quarter_hourly":"hourly",out);
    }
    static JSONObject normalizeGeoSphere(JSONObject raw)throws Exception{return normalized(raw,false);}
    static JSONObject normalizeNowcast(JSONObject raw)throws Exception{return normalized(raw,true);}
    private static JSONArray data(JSONObject p,String id)throws Exception{JSONObject row=p.optJSONObject(id);JSONArray values=row==null?null:row.optJSONArray("data");if(values==null)throw new IOException("Wetterparameter fehlt.");return values;}
    private static Double number(JSONArray rows,int i)throws Exception{if(i>=rows.length())throw new IOException("Wetterzeitreihe unvollständig.");if(rows.isNull(i))return null;double n=rows.getDouble(i);return Double.isFinite(n)?n:null;}
    private static double factor(String unit,String kind)throws Exception{
        String u=unit.toLowerCase(Locale.ROOT).replace(" ","").replace("**","^").replace("{","").replace("}","");
        if(kind.equals("temperature")){if(Arrays.asList("°c","degc","c","celsius","degreecelsius","degree_celsius","k","kelvin").contains(u))return 1;}
        if(kind.equals("wind")){if(Arrays.asList("ms-1","ms^-1","m/s").contains(u))return 3.6;if(Arrays.asList("km/h","kmh-1","kmh^-1").contains(u))return 1;}
        if(kind.equals("rain")&&Arrays.asList("mm","kgm-2","kgm^-2","kg/m2","mm/15min").contains(u))return 1;
        throw new IOException("Wettereinheit derzeit nicht unterstützt.");
    }
    private static Double value(JSONObject p,String id,int i,String kind)throws Exception{JSONObject row=p.getJSONObject(id);double f=factor(row.getString("unit"),kind);Double n=number(data(p,id),i);if(n==null)return null;String u=row.getString("unit");return kind.equals("temperature")&&(u.equalsIgnoreCase("K")||u.equalsIgnoreCase("kelvin"))?n-273.15:n*f;}
    private static Object convert(JSONObject p,String id,int i,String kind)throws Exception{Double n=value(p,id,i,kind);return n==null?JSONObject.NULL:n;}
    private static long parseTime(Object value)throws Exception{if(value instanceof Number){long n=((Number)value).longValue();return n>100000000000L?n/1000:n;}return OffsetDateTime.parse(String.valueOf(value)).toInstant().getEpochSecond();}
    private FreeWeatherSource(){}
}
