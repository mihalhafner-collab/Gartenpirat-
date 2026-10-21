package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

/** Planning advice from hourly data; never a safety clearance or automatic reschedule. */
final class WeatherRiskRules {
    static String locationKey(JSONObject a){return String.format(Locale.ROOT,"%.3f,%.3f",a.optDouble("latitude"),a.optDouble("longitude"));}
    static JSONObject unknown(String reason)throws Exception{return new JSONObject().put("severity","unknown").put("label","Wetter unbekannt").put("reasons",new JSONArray().put(reason)).put("source","GeoSphere Austria · CC BY 4.0").put("forecast_only",true);}
    static double number(JSONArray rows,int i)throws Exception{if(rows==null||i<0||i>=rows.length()||rows.isNull(i))throw new IllegalArgumentException("Wetterparameter fehlt.");double n=rows.getDouble(i);if(!Double.isFinite(n))throw new IllegalArgumentException("Wetterparameter ungültig.");return n;}
    static Double optionalNumber(JSONArray rows,int i){try{if(rows==null||i<0||i>=rows.length()||rows.isNull(i))return null;double n=rows.getDouble(i);return Double.isFinite(n)?n:null;}catch(Exception invalid){return null;}}
    private static JSONObject model(JSONObject a,JSONObject cache,Instant now,JSONObject settings)throws Exception {
        if(!AppointmentRules.active(a))return new JSONObject().put("severity","inactive").put("reasons",new JSONArray()).put("label","Kein aktiver Einsatz");
        if(a.isNull("latitude")||!a.has("latitude")||a.isNull("longitude")||!a.has("longitude"))return unknown("Wetterprüfung noch nicht möglich. Einsatzort ergänzen.");
        if(cache==null)return unknown("Noch keine belastbare Vorhersage verfügbar.");
        if(cache.optBoolean("source_unavailable"))return unknown("Wetterdaten derzeit nicht verfügbar.");
        if(cache.optJSONObject("forecast")==null)return unknown("Noch keine belastbare Vorhersage verfügbar.");
        Instant fetched;try{fetched=Instant.parse(cache.getString("fetched_at"));}catch(Exception e){return unknown("Aktualisierungszeit fehlt.");}
        if(fetched.isAfter(now.plusSeconds(300))||fetched.isBefore(now.minusSeconds(AppointmentRules.instant(a.getString("starts_at")).isAfter(now.plusSeconds(2*86400))?18*3600:6*3600)))return unknown("Vorhersage veraltet oder Gerätezeit widersprüchlich.").put("checked_at",fetched.toString());
        JSONObject data=cache.optJSONObject("forecast"),hourly=data==null?null:data.optJSONObject("hourly");if(hourly==null){JSONObject early=cache.optJSONObject("early_outlook");if(early!=null&&early.optBoolean("available"))return unknown("Langfristiger 15-Tage-Ausblick geprüft; belastbare Arbeitsbewertung folgt näher am Termin.").put("checked_at",early.optString("checked_at")).put("early_outlook_available",true);return unknown("Noch keine belastbare Vorhersage für diesen Einsatzort verfügbar.");}
        JSONArray times=hourly.optJSONArray("time");if(times==null||times.length()<2||times.length()>500)return unknown("Stundenwerte fehlen oder sind unzulässig.");
        Instant start=AppointmentRules.weatherStart(a,now),end=AppointmentRules.weatherEnd(a,now);String profile=a.optString("weather_profile","general");
        if(profile.equals("indoor"))return new JSONObject().put("severity","clear").put("label","Wetterarme Tätigkeit").put("reasons",new JSONArray()).put("checked_at",fetched.toString()).put("forecast_only",true);
        LinkedHashSet<String> reasons=new LinkedHashSet<>();int severity=0,covered=0;double peakRain=0,peakGust=0,minTemp=100,maxTemp=-100;long first=start.getEpochSecond()/3600*3600,last=(end.getEpochSecond()-1)/3600*3600;
        try{
            long previous=Long.MIN_VALUE;for(int i=0;i<times.length();i++){long t=times.getLong(i);if(t<=previous||(previous!=Long.MIN_VALUE&&t-previous!=3600))throw new IllegalArgumentException();previous=t;}
            for(long t=first;t<=last;t+=3600){int at=-1;for(int i=0;i<times.length();i++)if(times.getLong(i)==t){at=i;break;}if(at<0||at+1>=times.length())return unknown("Termin liegt außerhalb der vollständigen Vorhersage.");covered++;
                // Precipitation and gusts refer to the preceding hour; temperature to the timestamp.
                double temp=number(hourly.optJSONArray("temperature_2m"),at),rain=number(hourly.optJSONArray("precipitation"),at+1),gust=number(hourly.optJSONArray("wind_gusts_10m"),at+1);Double prob=optionalNumber(hourly.optJSONArray("precipitation_probability"),at+1);
                peakRain=Math.max(peakRain,rain);peakGust=Math.max(peakGust,gust);minTemp=Math.min(minTemp,temp);maxTemp=Math.max(maxTemp,temp);
                if(rain<0||(prob!=null&&(prob<0||prob>100))||gust<0||temp< -90||temp>65)throw new IllegalArgumentException();
                if(rain>=settings.optDouble("rain_mm",1)&&!profile.equals("inspection")){reasons.add(prob==null?"Niederschlag kann die geplante Arbeit erschweren.":"Regen kann die geplante Arbeit erschweren.");severity=Math.max(severity,1);}
                if(gust>=settings.optDouble("gust_kmh",40)){reasons.add("Starke Böen: Tätigkeit und Arbeitsmittel vor Ort prüfen.");severity=Math.max(severity,profile.equals("hedge")?2:1);}
                if(temp<=settings.optDouble("frost_c",0)){reasons.add("Frost: Boden und geplante Tätigkeit prüfen.");severity=Math.max(severity,1);}
                if(temp>=settings.optDouble("heat_c",30)){reasons.add("Hitze: frühere Arbeitszeit und Pausen prüfen.");severity=Math.max(severity,1);}
                Double snow=optionalNumber(hourly.optJSONArray("snowfall"),at+1);if(snow!=null&&snow>0){reasons.add("Schnee oder Eis: Boden und Zugänge prüfen.");severity=Math.max(severity,1);}
                Double wind=optionalNumber(hourly.optJSONArray("wind_speed_10m"),at+1);if(wind!=null&&wind>=settings.optDouble("gust_kmh",40)){reasons.add("Starker Wind: Tätigkeit vor Ort prüfen.");severity=Math.max(severity,profile.equals("hedge")?2:1);}
                JSONArray codes=hourly.optJSONArray("weather_code");if(codes!=null){double code=number(codes,at);if(code>=95){reasons.add("Gewitter im Vorhersagemodell: Einsatz prüfen.");severity=2;}}
            }
        }catch(Exception malformed){return unknown("Stundenwerte fehlen oder sind widersprüchlich.");}
        if(covered==0)return unknown("Keine passenden Stundenwerte.");
        String level=severity==2?"critical":severity==1?"difficult":"clear";
        JSONObject result=new JSONObject().put("severity",level).put("label",severity==2?"Kritisch – prüfen":severity==1?"Arbeit erschwert":"Kein erkennbares Wetterhindernis").put("reasons",new JSONArray(reasons)).put("checked_at",fetched.toString()).put("forecast_only",true).put("source","GeoSphere Austria · CC BY 4.0").put("uncertain_long_range",start.isAfter(now.plusSeconds(5*86400)));
        result.put("alert_hash",PixelMcpWriteTools.hash(new JSONObject().put("appointment_id",a.getString("id")).put("location_key",locationKey(a)).put("weather_profile",a.optString("weather_profile")).put("starts_at",a.getString("starts_at")).put("ends_at",a.getString("ends_at")).put("weather_profile",a.optString("weather_profile")).put("severity",level).put("risk_band",new JSONArray().put((int)(peakRain/5)).put((int)(peakGust/20)).put((int)(minTemp/10)).put((int)(maxTemp/10))).put("reasons",new JSONArray(reasons))));return result;
    }
    static JSONObject evaluate(JSONObject a,JSONObject cache,Instant now,JSONObject settings)throws Exception {
        JSONObject result=model(a,cache,now,settings);if(AppointmentRules.openEnd(a)&&AppointmentRules.active(a)){result.put("duration_unknown",true).put("time_scope","start_only");result.getJSONArray("reasons").put("Ende offen: Wetterhinweise beziehen sich nur auf den Beginn bzw. aktuellen Zeitpunkt.");}if(!AppointmentRules.active(a))return result;
        supplementNowcast(a,cache,now,settings,result);
        boolean fresh=false;try{fresh=cache!=null&&!cache.has("warning_status")&&a.has("latitude")&&!a.isNull("latitude")&&Instant.parse(cache.getString("warnings_fetched_at")).isAfter(now.minusSeconds(3*3600))&&!Instant.parse(cache.getString("warnings_fetched_at")).isAfter(now.plusSeconds(300));}catch(Exception ignored){}
        result.put("official_warnings_current",fresh);if(!fresh)return result.put("warning_status","Amtliche Warnungen nicht aktuell geprüft.");
        JSONArray warnings=cache.optJSONArray("warnings");if(warnings==null)return result.put("official_warnings_current",false).put("warning_status","Amtliche Warnungen unvollständig.");
        String severity=result.optString("severity");boolean unknown=severity.equals("unknown");int level=severity.equals("critical")?2:severity.equals("difficult")?1:0;LinkedHashSet<String> reasons=new LinkedHashSet<>();JSONArray current=result.getJSONArray("reasons");for(int i=0;i<current.length();i++)reasons.add(current.getString(i));boolean relevant=false;
        Instant start=AppointmentRules.weatherStart(a,now),end=AppointmentRules.weatherEnd(a,now);
        for(int i=0;i<warnings.length();i++)try{JSONObject w=warnings.getJSONObject(i);if(AppointmentRules.instant(w.getString("starts_at")).isBefore(end)&&AppointmentRules.instant(w.getString("ends_at")).isAfter(start)&&w.getInt("level")>0){relevant=true;if(w.getInt("level")>=2)result.put("critical_official_warning",true);level=Math.max(level,w.getInt("level")>=2?2:1);reasons.add("GeoSphere-Warnung: "+w.optString("type","Wetter")+" (Stufe "+w.getInt("level")+") · "+w.getString("starts_at")+" – "+w.getString("ends_at")+".");}}catch(Exception invalid){result.put("official_warnings_current",false).put("warning_status","Amtliche Warnung nicht vollständig auswertbar.");}
        if(relevant){result.put("forecast_unknown",unknown).put("severity",level>=2?"critical":"difficult").put("label",level>=2?"Kritisch – amtliche Warnung prüfen":"Amtliche Wetterwarnung – Einsatz prüfen").put("reasons",new JSONArray(reasons));result.put("alert_hash",PixelMcpWriteTools.hash(new JSONObject().put("appointment_id",a.getString("id")).put("location_key",locationKey(a)).put("weather_profile",a.optString("weather_profile")).put("starts_at",a.getString("starts_at")).put("ends_at",a.getString("ends_at")).put("severity",result.getString("severity")).put("reasons",new JSONArray(reasons))));}
        return result;
    }
    private static void supplementNowcast(JSONObject a,JSONObject cache,Instant now,JSONObject settings,JSONObject result)throws Exception {
        if(cache==null||a.optString("weather_profile").equals("indoor"))return;
        try{
            if(FreeWeatherSource.due(cache,"nowcast_fetched_at",now,3600))return;
            JSONObject data=cache.getJSONObject("nowcast").getJSONObject("quarter_hourly");JSONArray times=data.getJSONArray("time");
            Instant start=AppointmentRules.weatherStart(a,now),end=AppointmentRules.weatherEnd(a,now);if(start.isAfter(now.plusSeconds(10800)))return;
            int level=result.optString("severity").equals("critical")?2:result.optString("severity").equals("difficult")?1:0;LinkedHashSet<String> reasons=new LinkedHashSet<>();JSONArray old=result.getJSONArray("reasons");for(int i=0;i<old.length();i++)reasons.add(old.getString(i));
            boolean relevant=false;for(int i=1;i<times.length();i++){long t=times.getLong(i);if(t<=Math.max(now.getEpochSecond(),start.getEpochSecond())||t-900>=end.getEpochSecond())continue;
                Double rain=optionalNumber(data.optJSONArray("precipitation"),i),gust=optionalNumber(data.optJSONArray("wind_gusts_10m"),i),temp=optionalNumber(data.optJSONArray("temperature_2m"),i);
                if(rain!=null&&rain*4>=settings.optDouble("rain_mm",1)&&!a.optString("weather_profile").equals("inspection")){reasons.add("Kurzfristig Niederschlag: geplante Arbeit prüfen.");relevant=true;level=Math.max(level,1);}
                if(gust!=null&&gust>=settings.optDouble("gust_kmh",40)){reasons.add("Kurzfristig starke Böen: Tätigkeit prüfen.");relevant=true;level=Math.max(level,a.optString("weather_profile").equals("hedge")?2:1);}
                if(temp!=null&&(temp<=settings.optDouble("frost_c",0)||temp>=settings.optDouble("heat_c",30))){reasons.add("Kurzfristig Frost oder Hitze: Tätigkeit prüfen.");relevant=true;level=Math.max(level,1);}
            }
            if(relevant){result.put("forecast_unknown",result.optString("severity").equals("unknown")).put("severity",level>=2?"critical":"difficult").put("label","Kurzfristiges Wetterrisiko – Einsatz prüfen").put("reasons",new JSONArray(reasons));result.put("alert_hash",PixelMcpWriteTools.hash(new JSONObject().put("appointment_id",a.getString("id")).put("location_key",locationKey(a)).put("weather_profile",a.optString("weather_profile")).put("starts_at",a.getString("starts_at")).put("ends_at",a.getString("ends_at")).put("reasons",new JSONArray(reasons))));}
        }catch(Exception incomplete){result.put("nowcast_status","Kurzfristwetter derzeit nicht vollständig verfügbar.");}
    }
    static JSONArray parseWarnings(JSONObject raw)throws Exception {
        JSONObject props=raw.optJSONObject("properties");JSONArray source=props==null?null:props.optJSONArray("warnings");if(source==null)throw new IllegalArgumentException("Unbekanntes GeoSphere-Warnformat.");
        JSONArray out=new JSONArray();if(source.length()>300)throw new IllegalArgumentException("Zu viele Warnungen.");
        for(int i=0;i<source.length();i++){JSONObject w=source.getJSONObject(i).getJSONObject("properties");long from=warningTime(w.get("begin")),until=warningTime(w.get("end"));if(from>100000000000L)from/=1000;if(until>100000000000L)until/=1000;
            int level=w.getInt("warnstufeid"),type=w.getInt("warntypid");if(level<0||level>3||until<=from)throw new IllegalArgumentException("Ungültige Warnung.");String[] types={"Wetter","Wind","Regen","Schnee","Glatteis","Gewitter","Hitze","Kälte"};out.put(new JSONObject().put("starts_at",Instant.ofEpochSecond(from).toString()).put("ends_at",Instant.ofEpochSecond(until).toString()).put("level",level).put("type",type>0&&type<types.length?types[type]:"Wetter"));}
        return out;
    }
    private static long warningTime(Object value)throws Exception{if(value instanceof Number)return ((Number)value).longValue();String s=value.toString();if(s.matches("[0-9]+"))return Long.parseLong(s);return AppointmentRules.instant(s).getEpochSecond();}
    private WeatherRiskRules(){}
}
