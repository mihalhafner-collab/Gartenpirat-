package at.hausgarten.service;

import org.json.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Keyless public API for the owner's explicitly requested non-commercial use. */
final class OpenMeteoWeatherSource {
    static final int FORECAST_DAYS=16;
    static final String API="https://api.open-meteo.com/v1/forecast";
    static final String PROVIDER="open_meteo_non_commercial";
    static final String ATTRIBUTION="Open-Meteo · CC BY 4.0 · https://open-meteo.com/";
    private static final String[] VARIABLES={"temperature_2m","precipitation","precipitation_probability","wind_gusts_10m","wind_speed_10m","snowfall","weather_code"};
    private static final String[] UNITS={"°C","mm","%","km/h","km/h","cm","wmo code"};

    static String url(String location)throws Exception {
        double[] point=FreeWeatherSource.coords(location);
        return API+"?latitude="+point[0]+"&longitude="+point[1]+"&hourly="+String.join(",",VARIABLES)
            +"&forecast_days="+FORECAST_DAYS+"&timezone=GMT&timeformat=unixtime&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm";
    }
    static long maximumAge(long untilStart){
        if(untilStart>7*86400)return 30*3600;
        if(untilStart>72*3600)return 18*3600;
        if(untilStart>24*3600)return 8*3600;
        return untilStart>6*3600?4*3600:2*3600;
    }
    static JSONObject refreshSite(String location,JSONObject cache,Instant now,long untilStart,DriveRestClient.Wire wire)throws Exception {
        if(untilStart>FreeWeatherSource.MONITOR_WINDOW)return cache;
        if(cache.optBoolean("source_unavailable")||!PROVIDER.equals(cache.optString("forecast_provider"))||FreeWeatherSource.due(cache,"fetched_at",now,FreeWeatherSource.intervalSeconds(untilStart))){
            JSONObject raw=FreeWeatherSource.fetch(wire,url(location),1024*1024,now).json();
            JSONObject forecast=normalize(raw,FreeWeatherSource.coords(location));
            // Publish only the completely validated response; never stamp a failed refresh fresh.
            cache.put("forecast",forecast).put("fetched_at",now.toString()).put("forecast_provider",PROVIDER).put("forecast_source",ATTRIBUTION);
            cache.remove("source_unavailable");cache.remove("forecast_notice");
        }
        return cache;
    }
    static JSONObject normalize(JSONObject raw,double[] requested)throws Exception {
        if(raw.optBoolean("error")||raw.optInt("utc_offset_seconds",Integer.MIN_VALUE)!=0)throw invalid();
        double lat=raw.optDouble("latitude",Double.NaN),lon=raw.optDouble("longitude",Double.NaN);
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw invalid();
        double a=Math.sin(Math.toRadians(lat-requested[0])/2),b=Math.sin(Math.toRadians(lon-requested[1])/2);
        double distance=12742*Math.asin(Math.min(1,Math.sqrt(a*a+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(requested[0]))*b*b)));
        if(distance>50)throw new IOException("Wetterdaten passen nicht zum angefragten Einsatzgebiet.");
        JSONObject units=raw.optJSONObject("hourly_units"),hourly=raw.optJSONObject("hourly");
        if(units==null||hourly==null||!"unixtime".equals(units.optString("time")))throw invalid();
        JSONArray times=hourly.optJSONArray("time");if(times==null||times.length()<2||times.length()>FORECAST_DAYS*24+1)throw invalid();
        JSONObject result=new JSONObject();JSONArray normalizedTimes=new JSONArray();long previous=Long.MIN_VALUE;
        for(int i=0;i<times.length();i++){
            Object value=times.get(i);if(!(value instanceof Number))throw invalid();double numeric=((Number)value).doubleValue();
            if(!Double.isFinite(numeric)||numeric!=Math.rint(numeric)||numeric<0||numeric>4102444800L)throw invalid();long stamp=((Number)value).longValue();
            if(previous!=Long.MIN_VALUE&&stamp-previous!=3600)throw invalid();previous=stamp;normalizedTimes.put(stamp);
        }
        result.put("time",normalizedTimes);
        for(int k=0;k<VARIABLES.length;k++){
            String name=VARIABLES[k];if(!UNITS[k].equals(units.optString(name)))throw invalid();
            JSONArray values=hourly.optJSONArray(name);if(values==null||values.length()!=times.length())throw invalid();JSONArray normalized=new JSONArray();
            for(int i=0;i<values.length();i++){
                if(values.isNull(i)){normalized.put(JSONObject.NULL);continue;}
                Object value=values.get(i);if(!(value instanceof Number))throw invalid();double n=((Number)value).doubleValue();
                if(!Double.isFinite(n)||(k==0?(n< -90||n>65):(n<0||n>(k==2?100:k==6?99:1000))))throw invalid();
                if(k==6&&n!=Math.rint(n))throw invalid();normalized.put(n);
            }
            result.put(name,normalized);
        }
        return new JSONObject().put("hourly",result).put("hourly_units",new JSONObject(units.toString()));
    }
    static JSONObject coverage(JSONObject cache,Instant now)throws Exception {
        JSONObject result=new JSONObject().put("forecast_days",0).put("forecast_complete",false).put("forecast_valid_from","").put("forecast_valid_until","");
        JSONObject forecast=cache==null?null:cache.optJSONObject("forecast"),hourly=forecast==null?null:forecast.optJSONObject("hourly");if(hourly==null)return result;
        JSONArray times=hourly.optJSONArray("time");if(times==null||times.length()<2||times.length()>500)return result;
        long from=Long.MAX_VALUE,until=Long.MIN_VALUE,last=Long.MIN_VALUE;boolean complete=true;
        try{for(int i=0;i+1<times.length();i++){
            long t=times.getLong(i),end=times.getLong(i+1);if(end-t!=3600||t<=last)return result;last=t;if(end<=now.getEpochSecond())continue;
            boolean valid=WeatherRiskRules.optionalNumber(hourly.optJSONArray("temperature_2m"),i)!=null
                &&WeatherRiskRules.optionalNumber(hourly.optJSONArray("precipitation"),i+1)!=null
                &&WeatherRiskRules.optionalNumber(hourly.optJSONArray("wind_gusts_10m"),i+1)!=null
                &&(!hourly.has("weather_code")||WeatherRiskRules.optionalNumber(hourly.optJSONArray("weather_code"),i)!=null);
            if(!valid){complete=false;continue;}from=Math.min(from,t);until=end;
        }}catch(Exception invalid){return result;}
        if(until==Long.MIN_VALUE)return result;
        return result.put("forecast_valid_from",Instant.ofEpochSecond(from).toString()).put("forecast_valid_until",Instant.ofEpochSecond(until).toString())
            .put("forecast_complete",complete).put("forecast_days",complete?(int)Math.max(0,Math.min(FORECAST_DAYS,(until-now.getEpochSecond())/86400)):0);
    }
    private static IOException invalid(){return new IOException("Wetterantwort unvollständig oder widersprüchlich. Keine belastbare Vorhersage.");}
    private OpenMeteoWeatherSource(){}
}
