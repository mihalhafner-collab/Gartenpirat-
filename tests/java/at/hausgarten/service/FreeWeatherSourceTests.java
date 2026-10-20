package at.hausgarten.service;
import org.json.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Public-format fixtures only. No customer, weather account or network access. */
public final class FreeWeatherSourceTests {
 static int checks;
 static void yes(boolean v){checks++;if(!v)throw new AssertionError("Weather source check "+checks);}
 interface Task{void run()throws Exception;}
 static void rejects(Task t)throws Exception{try{t.run();throw new AssertionError("Expected fail-closed weather result");}catch(java.io.IOException|IllegalArgumentException expected){checks++;}}
 static JSONObject data()throws Exception{
  JSONObject p=new JSONObject();for(String id:new String[]{"t2m","rain_acc","ugust","vgust"})p.put(id,new JSONObject().put("unit",id.equals("t2m")?"K":id.equals("rain_acc")?"mm":"m/s").put("data",new JSONArray(id.equals("t2m")?"[283.15,284.15,285.15]":id.equals("rain_acc")?"[0,2,3]":"[3,4,0]")));
  return new JSONObject().put("timestamps",new JSONArray("[\"2026-10-20T00:00:00Z\",\"2026-10-20T01:00:00Z\",\"2026-10-20T02:00:00Z\"]")).put("features",new JSONArray().put(new JSONObject().put("geometry",new JSONObject().put("coordinates",new JSONArray("[16.3,48.2]"))).put("properties",new JSONObject().put("parameters",p))));
 }
 static DriveRestClient.Reply reply(int code,String body,Map<String,String> h){return new DriveRestClient.Reply(code,body.getBytes(StandardCharsets.UTF_8),h);}
 public static void main(String[] args)throws Exception{
  SyntheticBusinessData.verify();long h=3600,d=24*h;long[][] cadence={{15*d,d},{14*d,d},{8*d,d},{7*d,12*h},{4*d,12*h},{72*h,6*h},{25*h,6*h},{24*h,3*h},{7*h,3*h},{6*h,h},{3*h,h},{0,h}};
  for(long[] c:cadence)yes(FreeWeatherSource.intervalSeconds(c[0])==c[1]);
  Instant now=Instant.parse("2026-10-20T00:00:00Z");final int[] calls={0};DriveRestClient.Wire never=(m,u,headers,b,max)->{calls[0]++;throw new AssertionError("No network before T-14");};
  JSONObject cache=new JSONObject();FreeWeatherSource.refreshSite("48.2,16.3",cache,now,15*d,never);yes(calls[0]==0&&!cache.getJSONObject("monitoring").getBoolean("active"));
  rejects(()->FreeWeatherSource.coords("91,16"));rejects(()->FreeWeatherSource.coords("NaN,16"));rejects(()->FreeWeatherSource.coords("48,16,1"));
  JSONObject raw=data(),hourly=FreeWeatherSource.normalizeGeoSphere(raw).getJSONObject("hourly");yes(Math.abs(hourly.getJSONArray("temperature_2m").getDouble(0)-10)<.001);yes(hourly.getJSONArray("precipitation").isNull(0));yes(hourly.getJSONArray("precipitation").getDouble(1)==2);
  JSONObject p=raw.getJSONArray("features").getJSONObject(0).getJSONObject("properties").getJSONObject("parameters");p.getJSONObject("rain_acc").getJSONArray("data").put(1,JSONObject.NULL);yes(FreeWeatherSource.normalizeGeoSphere(raw).getJSONObject("hourly").getJSONArray("precipitation").isNull(2));
  p.getJSONObject("rain_acc").put("data",new JSONArray("[3,1,2]"));yes(FreeWeatherSource.normalizeGeoSphere(raw).getJSONObject("hourly").getJSONArray("precipitation").isNull(1));
  p.getJSONObject("t2m").put("unit","unknown");rejects(()->FreeWeatherSource.normalizeGeoSphere(raw));p.getJSONObject("t2m").put("unit","K");raw.getJSONArray("timestamps").put(2,"2026-10-20T04:00:00Z");rejects(()->FreeWeatherSource.normalizeGeoSphere(raw));
  yes(!FreeWeatherSource.due(new JSONObject().put("at",now.toString()),"at",now,h));yes(FreeWeatherSource.due(new JSONObject().put("at",now.minusSeconds(h).toString()),"at",now,h));yes(FreeWeatherSource.due(new JSONObject().put("at",now.plusSeconds(301).toString()),"at",now,h));
  DriveRestClient.Wire licensed=(m,u,headers,b,max)->reply(200,"All data that is publicly accessible without authentication is licensed under https://creativecommons.org/licenses/by/4.0/",Collections.emptyMap());FreeWeatherSource.requireLicence(cache,now,licensed);yes(cache.has("licence_checked_at"));
  FreeWeatherSource.requireLicence(cache,now.plusSeconds(d),never);yes(calls[0]==0);
  rejects(()->FreeWeatherSource.requireLicence(cache,now.plusSeconds(8*d),(m,u,headers,b,max)->reply(200,"Payment required",Collections.emptyMap())));yes(!cache.has("licence_checked_at"));
  try{FreeWeatherSource.fetch((m,u,headers,b,max)->reply(429,"",Collections.singletonMap("Retry-After","120")),"https://dataset.api.hub.geosphere.at/",100,now);throw new AssertionError();}catch(DriveRestClient.Failure f){yes(f.retryAfter==120000&&!f.userAction);}
  try{FreeWeatherSource.fetch((m,u,headers,b,max)->reply(403,"",Collections.emptyMap()),"https://dataset.api.hub.geosphere.at/",100,now);throw new AssertionError();}catch(DriveRestClient.Failure f){yes(f.userAction);}
  JSONObject a=PlanningTests.row();JSONObject future=new JSONObject(a.toString()).put("starts_at",now.plusSeconds(10*d).toString()).put("ends_at",now.plusSeconds(10*d+h).toString());yes(WeatherRiskRules.evaluate(future,new JSONObject().put("availability_checked_at",now.toString()),now,new JSONObject()).getString("severity").equals("unknown"));
  JSONObject clear=PlanningTests.forecast(Instant.parse("2026-10-19T07:00:00Z"));clear.getJSONObject("forecast").getJSONObject("hourly").getJSONArray("precipitation").put(9,3); // Actual rain cannot be waived by a low probability field.
  yes(WeatherRiskRules.evaluate(a,clear,Instant.parse("2026-10-19T07:00:00Z"),new JSONObject()).getString("severity").equals("difficult"));
  System.out.println("PASS "+checks+" exact cadence, horizon, licence, units, missing/reset, throttling and unknown weather checks; synthetic only");
 }
}
