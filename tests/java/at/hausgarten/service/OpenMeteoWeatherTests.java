package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URL;

/** Synthetic provider, long-range, fallback and network-boundary cases; no real accounts. */
public final class OpenMeteoWeatherTests {
    static int checks;
    static final Instant NOW=Instant.parse("2026-10-18T08:00:00Z");
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Open-Meteo check "+checks);}
    interface Action{void run()throws Exception;}
    static void rejects(Action action)throws Exception{try{action.run();throw new AssertionError("Malformed forecast accepted");}catch(java.io.IOException expected){checks++;}}
    static JSONObject raw()throws Exception {
        JSONObject data=new JSONObject(),units=new JSONObject().put("time","unixtime");JSONArray times=new JSONArray();
        long first=Instant.parse("2026-10-18T00:00:00Z").getEpochSecond();for(int i=0;i<384;i++)times.put(first+i*3600);
        data.put("time",times);String[] fields={"temperature_2m","precipitation","precipitation_probability","wind_gusts_10m","wind_speed_10m","snowfall","weather_code"};
        String[] unit={"°C","mm","%","km/h","km/h","cm","wmo code"};double[] defaults={15,0,20,12,6,0,0};
        for(int k=0;k<fields.length;k++){JSONArray values=new JSONArray();for(int i=0;i<384;i++)values.put(defaults[k]);data.put(fields[k],values);units.put(fields[k],unit[k]);}
        return new JSONObject().put("latitude",48.2).put("longitude",16.3).put("utc_offset_seconds",0).put("timezone","GMT").put("hourly_units",units).put("hourly",data);
    }
    static HttpTransport.Reply reply(int status,JSONObject body){return new HttpTransport.Reply(status,body.toString().getBytes(StandardCharsets.UTF_8),Collections.emptyMap());}
    static JSONObject appointment(Instant start)throws Exception{return new JSONObject().put("id","qa_long_range_appointment").put("customer_id","qa_weather_customer").put("status","confirmed").put("weather_profile","general").put("starts_at",start.toString()).put("ends_at",start.plusSeconds(8*3600).toString()).put("latitude",48.2).put("longitude",16.3);}
    static JSONObject cache()throws Exception{return new JSONObject().put("forecast",OpenMeteoWeatherSource.normalize(raw(),new double[]{48.2,16.3})).put("fetched_at",NOW.toString()).put("forecast_provider",OpenMeteoWeatherSource.PROVIDER).put("forecast_source",OpenMeteoWeatherSource.ATTRIBUTION);}
    public static void main(String[] args)throws Exception {
        SyntheticBusinessData.verify();String url=OpenMeteoWeatherSource.url("48.2,16.3");
        yes(url.startsWith("https://api.open-meteo.com/v1/forecast?"));yes(url.contains("forecast_days=16")&&url.contains("timeformat=unixtime")&&url.contains("timezone=GMT"));yes(!url.contains("apikey")&&!url.contains("customer-api"));
        JSONObject normalized=OpenMeteoWeatherSource.normalize(raw(),new double[]{48.2,16.3});yes(normalized.getJSONObject("hourly").getJSONArray("time").length()==384);
        JSONObject cache=cache(),a=appointment(NOW.plusSeconds(14*86400));
        JSONObject risk=WeatherRiskRules.evaluate(a,cache,NOW,new JSONObject());
        yes(!risk.getString("severity").equals("unknown"));yes(risk.getBoolean("uncertain_long_range")&&risk.getBoolean("forecast_available"));yes(risk.getString("forecast_stage").equals("trend"));yes(risk.getString("label").contains("Wettertrend"));yes(risk.getString("source").contains("Open-Meteo"));
        yes(risk.getInt("forecast_days")>=14&&risk.getInt("forecast_days")<=16);
        yes(!Instant.parse(risk.getString("forecast_valid_until")).isBefore(Instant.parse(a.getString("ends_at"))));
        yes(Instant.parse(a.getString("starts_at")).atZone(ZoneId.of("Europe/Vienna")).getOffset().getTotalSeconds()==3600); // crosses the autumn DST change
        JSONObject far=WeatherRiskRules.evaluate(appointment(NOW.plusSeconds(17*86400)),cache,NOW,new JSONObject());yes(far.getString("severity").equals("unknown")&&!far.getBoolean("forecast_available")&&far.getInt("forecast_days")==0);
        JSONObject indoor=WeatherRiskRules.evaluate(new JSONObject(a.toString()).put("weather_profile","indoor"),cache,NOW,new JSONObject());yes(indoor.getString("severity").equals("clear")&&!indoor.getBoolean("forecast_available")&&indoor.getInt("forecast_days")==0&&!indoor.getBoolean("forecast_complete"));
        JSONObject corrupt=new JSONObject(cache.toString());corrupt.getJSONObject("forecast").getJSONObject("hourly").getJSONArray("time").put(5,"qa_invalid_time");yes(OpenMeteoWeatherSource.coverage(corrupt,NOW).getInt("forecast_days")==0);
        JSONObject old=new JSONObject(cache.toString()).put("fetched_at",NOW.minusSeconds(31*3600).toString());yes(WeatherRiskRules.evaluate(a,old,NOW,new JSONObject()).getString("severity").equals("unknown"));
        JSONObject daily=new JSONObject(cache.toString()).put("fetched_at",NOW.minusSeconds(25*3600).toString());yes(!WeatherRiskRules.evaluate(a,daily,NOW,new JSONObject()).getString("severity").equals("unknown"));
        JSONObject incomplete=raw();incomplete.getJSONObject("hourly").getJSONArray("wind_gusts_10m").put(14*24+10,JSONObject.NULL);
        JSONObject hole=new JSONObject(cache.toString()).put("forecast",OpenMeteoWeatherSource.normalize(incomplete,new double[]{48.2,16.3}));
        yes(hole.getJSONObject("forecast").getJSONObject("hourly").getJSONArray("wind_gusts_10m").isNull(14*24+10));
        yes(WeatherRiskRules.evaluate(a,hole,NOW,new JSONObject()).getString("severity").equals("unknown"));yes(OpenMeteoWeatherSource.coverage(hole,NOW).getInt("forecast_days")==0);
        rejects(()->OpenMeteoWeatherSource.normalize(raw().put("error",true),new double[]{48.2,16.3}));
        rejects(()->OpenMeteoWeatherSource.normalize(raw().put("latitude",0),new double[]{48.2,16.3}));
        rejects(()->OpenMeteoWeatherSource.normalize(raw().put("utc_offset_seconds",3600),new double[]{48.2,16.3}));
        JSONObject badUnit=raw();badUnit.getJSONObject("hourly_units").put("wind_gusts_10m","m/s");rejects(()->OpenMeteoWeatherSource.normalize(badUnit,new double[]{48.2,16.3}));
        JSONObject timeGap=raw();timeGap.getJSONObject("hourly").getJSONArray("time").put(40,NOW.getEpochSecond());rejects(()->OpenMeteoWeatherSource.normalize(timeGap,new double[]{48.2,16.3}));
        JSONObject shortArray=raw();shortArray.getJSONObject("hourly").put("precipitation",new JSONArray("[0,0]"));rejects(()->OpenMeteoWeatherSource.normalize(shortArray,new double[]{48.2,16.3}));
        JSONObject malformed=raw();malformed.getJSONObject("hourly").getJSONArray("temperature_2m").put(0,"15");rejects(()->OpenMeteoWeatherSource.normalize(malformed,new double[]{48.2,16.3}));
        JSONObject badProbability=raw();badProbability.getJSONObject("hourly").getJSONArray("precipitation_probability").put(0,101);rejects(()->OpenMeteoWeatherSource.normalize(badProbability,new double[]{48.2,16.3}));
        int[] requests={0};JSONObject loaded=new JSONObject();HttpTransport.Wire weather=(m,u,h,b,max)->{requests[0]++;yes(m.equals("GET")&&h.isEmpty()&&b==null&&u.equals(url));return reply(200,raw());};
        FreeWeatherSource.refreshNonCommercialSite("48.2,16.3",loaded,NOW,14*86400,weather);yes(requests[0]==1&&loaded.has("forecast"));yes(loaded.getJSONObject("monitoring").getInt("first_check_days_before")==16);
        FreeWeatherSource.refreshNonCommercialSite("48.2,16.3",loaded,NOW.plusSeconds(3600),14*86400-3600,weather);yes(requests[0]==1);
        final int[] noRequests={0};FreeWeatherSource.refreshNonCommercialSite("48.2,16.3",new JSONObject(),NOW,17*86400,(m,u,h,b,max)->{noRequests[0]++;throw new AssertionError();});yes(noRequests[0]==0);
        JSONObject retained=cache();String previous=retained.getJSONObject("forecast").toString(),stamp=retained.getString("fetched_at");
        try{OpenMeteoWeatherSource.refreshSite("48.2,16.3",retained,NOW.plusSeconds(86400),14*86400,(m,u,h,b,max)->reply(503,new JSONObject()));throw new AssertionError();}
        catch(HttpTransport.Failure expected){yes(expected.retryAfter>=30000&&!expected.userAction);}
        yes(previous.equals(retained.getJSONObject("forecast").toString())&&stamp.equals(retained.getString("fetched_at")));
        int[] rateRequests={0};try{FreeWeatherSource.refreshNonCommercialSite("48.2,16.3",new JSONObject(),NOW,3600,(m,u,h,b,max)->{rateRequests[0]++;return new HttpTransport.Reply(429,new byte[0],Collections.singletonMap("Retry-After","120"));});throw new AssertionError();}
        catch(HttpTransport.Failure expected){yes(expected.code.equals("WEATHER_RATE_LIMIT")&&expected.retryAfter==120000&&rateRequests[0]==1);}
        JSONObject warningsFail=new JSONObject();FreeWeatherSource.refreshNonCommercialSite("48.2,16.3",warningsFail,NOW,24*3600,(m,u,h,b,max)->u.startsWith(OpenMeteoWeatherSource.API)?reply(200,raw()):reply(503,new JSONObject()));
        yes(warningsFail.has("forecast")&&warningsFail.has("warning_status")&&!warningsFail.optBoolean("source_unavailable"));
        yes(!WeatherRiskRules.evaluate(appointment(NOW.plusSeconds(24*3600)),warningsFail,NOW,new JSONObject()).getString("severity").equals("unknown"));
        JSONObject fallback=new JSONObject().put("licence_checked_at",NOW.toString());JSONObject geo=FreeWeatherSourceTests.data();JSONObject parameters=geo.getJSONArray("features").getJSONObject(0).getJSONObject("properties").getJSONObject("parameters");
        fallback.put("nwp-v2-1h-1km:metadata",new JSONObject().put("parameters",parameters)).put("nwp-v2-1h-1km:metadata:at",NOW.toString());
        FreeWeatherSource.refreshNonCommercialSite("48.2,16.3",fallback,NOW,24*3600,(m,u,h,b,max)->{
            if(u.startsWith(OpenMeteoWeatherSource.API))throw new java.io.IOException("QA synthetic source outage");
            if(u.contains("getWarningsForCoords"))return reply(200,new JSONObject().put("properties",new JSONObject().put("warnings",new JSONArray())));
            return reply(200,geo);
        });yes(fallback.getString("forecast_provider").equals("geosphere_nwp_v2")&&fallback.has("forecast_notice"));
        yes(PlanningWire.allowed("GET",new URL(url),Collections.emptyMap()));
        yes(!PlanningWire.allowed("GET",new URL("http://api.open-meteo.com/v1/forecast"),Collections.emptyMap()));
        yes(!PlanningWire.allowed("GET",new URL("https://api.open-meteo.com.qa.invalid/v1/forecast"),Collections.emptyMap()));
        yes(!PlanningWire.allowed("GET",new URL(url),Collections.singletonMap("Authorization","Bearer qa_private_token")));
        yes(!PlanningWire.allowed("GET",new URL(url),Collections.singletonMap("Cookie","qa_session")));
        yes(!PlanningWire.allowed("POST",new URL(url),Collections.emptyMap()));
        yes(PlanningWire.allowed("POST",new URL("https://www.googleapis.com/calendar/v3/calendars"),Collections.singletonMap("Authorization","Bearer qa_private_token")));
        yes(!PlanningWire.allowed("GET",new URL("https://qa_user@api.open-meteo.com/v1/forecast"),Collections.emptyMap()));
        yes(!PlanningWire.allowed("GET",new URL("https://api.open-meteo.com:443/v1/forecast"),Collections.emptyMap()));
        System.out.println("PASS "+checks+" non-commercial keyless 16-day forecast, T+14/DST, partial/stale/null/unit/HTTP, real fallback and credential-boundary checks; synthetic only");
    }
}
