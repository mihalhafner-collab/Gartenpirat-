package at.hausgarten.service;
import org.json.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Real production rules and transports with isolated records and a deterministic provider wire. */
public final class PlanningTests {
 static int checks;
 interface Task{void run()throws Exception;}
 static void yes(boolean value){checks++;if(!value)throw new AssertionError("Planning assertion "+checks);}
 static void rejects(Task run)throws Exception{try{run.run();throw new AssertionError("Expected rejection "+checks);}catch(IllegalArgumentException|java.io.IOException expected){checks++;}}
 static JSONObject snapshot()throws Exception{JSONObject s=SyntheticBusinessData.object("{customers:[{id:qa_c,name:QA,active:1},{id:qa_d,name:Other,active:1}],orders:[{id:qa_o,title:QA,customer_id:qa_c}],worklogs:[],photos:[],invoices:[]}");PixelDriveState.prepare(s);return s;}
 static JSONObject row()throws Exception{return SyntheticBusinessData.object().put("id","qa_a").put("customer_id","qa_c").put("order_id","qa_o").put("title","QA Rasen").put("starts_at","2026-10-19T08:00:00Z").put("ends_at","2026-10-19T10:00:00Z").put("timezone","Europe/Vienna").put("status","tentative").put("weather_profile","lawn").put("latitude",48.2).put("longitude",16.3);}
 static JSONObject forecast(Instant now)throws Exception{
  JSONObject h=SyntheticBusinessData.object();for(String key:new String[]{"time","temperature_2m","precipitation","precipitation_probability","wind_gusts_10m","weather_code"})h.put(key,new JSONArray());
  long first=Instant.parse("2026-10-19T00:00:00Z").getEpochSecond();for(int i=0;i<73;i++){h.getJSONArray("time").put(first+i*3600);h.getJSONArray("temperature_2m").put(15);h.getJSONArray("precipitation").put(0);h.getJSONArray("precipitation_probability").put(0);h.getJSONArray("wind_gusts_10m").put(10);h.getJSONArray("weather_code").put(0);}
  return SyntheticBusinessData.object().put("forecast",SyntheticBusinessData.object().put("hourly",h)).put("fetched_at",now.toString()).put("warnings",new JSONArray()).put("warnings_fetched_at",now.toString());
 }
 static JSONObject event(JSONObject r,String etag)throws Exception{return CalendarRules.event(r).put("etag",etag);}
 static final class Provider implements DriveRestClient.Wire {
  JSONObject remote;int inserts,patches,deletes;boolean failAck,stale,expired,deny,retry;
  DriveRestClient.Reply reply(int code,JSONObject body)throws Exception{return new DriveRestClient.Reply(code,body==null?new byte[0]:body.toString().getBytes(StandardCharsets.UTF_8),new HashMap<>());}
  public DriveRestClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int max)throws Exception{
   yes(url.startsWith(CalendarClient.API)&&headers.get("Authorization").equals("Bearer synthetic"));
   if(deny)return reply(401,null);if(retry)return new DriveRestClient.Reply(429,new byte[0],Collections.singletonMap("Retry-After","120"));
   if(method.equals("GET")&&url.contains("maxResults")){if(expired)return reply(410,null);return reply(200,SyntheticBusinessData.object().put("items",remote==null?new JSONArray():new JSONArray().put(remote)).put("nextSyncToken","next"));}
   if(method.equals("GET"))return reply(remote==null?404:200,remote);
   if(method.equals("DELETE")){if(stale)return reply(412,null);yes(headers.get("If-Match").equals(remote.getString("etag")));deletes++;remote=null;return reply(204,null);}
   if(method.equals("POST")){if(remote!=null)return reply(409,null);inserts++;remote=SyntheticBusinessData.object(new String(body,StandardCharsets.UTF_8)).put("etag","v1");if(failAck){failAck=false;throw new java.io.IOException("Synthetic response loss");}return reply(200,remote);}
   if(method.equals("PATCH")){if(stale)return reply(412,null);yes(headers.get("If-Match").equals(remote.getString("etag")));patches++;remote=SyntheticBusinessData.object(new String(body,StandardCharsets.UTF_8)).put("etag","v2");return reply(200,remote);}
   throw new AssertionError("Unexpected wire method");
  }
 }
 public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
  JSONObject s=snapshot();yes(s.getJSONArray("appointments").length()==0&&s.getJSONObject("capabilities").getBoolean("appointments_v1"));JSONObject a=row();AppointmentRules.validate(a,null,s);yes(a.getString("starts_at").equals("2026-10-19T08:00:00Z"));
  rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(a.toString()).put("customer_id","qa_Hofer"),null,s));rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(a.toString()).put("customer_id","qa_d"),null,s));
  rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(a.toString()).put("ends_at",a.getString("starts_at")),null,s));rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(a.toString()).put("latitude",91),null,s));
  rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(a.toString()).put("break_minutes",120),null,s));
  s.getJSONArray("appointments").put(a);JSONObject second=SyntheticBusinessData.object(a.toString()).put("id","qa_b").put("starts_at","2026-10-19T10:00:00Z").put("ends_at","2026-10-19T11:00:00Z");AppointmentRules.validate(second,null,s);yes(true);
  rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(second.toString()).put("travel_minutes",10),null,s));
  AppointmentRules.validate(SyntheticBusinessData.object(second.toString()).put("travel_minutes",10).put("status","cancelled"),null,s);yes(true);
  rejects(()->AppointmentRules.validate(SyntheticBusinessData.object(a.toString()).put("customer_id","qa_d").put("order_id",""),a,s));
  yes(!AppointmentRules.hash(a).equals(OwnerJournalRules.version(SyntheticBusinessData.object(a.toString()).put("notes","changed"))));
  JSONObject update=SyntheticBusinessData.object().put("appointment_id","qa_a").put("expected_record_hash",AppointmentRules.hash(a)).put("updates",SyntheticBusinessData.object().put("notes","new"));
  JSONObject saved=AppointmentRules.prepare("update_appointment",update,s,"req","2026-10-19T07:00:00Z");yes(saved.getJSONObject("_mcp_base").getString("title").equals(a.getString("title")));
  rejects(()->AppointmentRules.prepare("update_appointment",SyntheticBusinessData.object(update.toString()).put("expected_record_hash",PixelMcpWriteTools.hash("stale")),s,"req","t"));
  JSONObject action=SyntheticBusinessData.object().put("client_id","req").put("kind","appointment").put("payload",saved).put("created_at","2026-10-19T07:00:00Z");yes(DriveJournalRules.apply(s,new JSONArray().put(action)).length()==0);yes(s.getJSONArray("appointments").getJSONObject(0).getString("notes").equals("new"));yes(DriveJournalRules.apply(s,new JSONArray().put(action)).length()==0);yes(s.getJSONArray("appointments").length()==1);
  JSONObject conflict=snapshot();conflict.getJSONArray("appointments").put(SyntheticBusinessData.object(a.toString()).put("notes","parallel"));yes(DriveJournalRules.apply(conflict,new JSONArray().put(action)).getJSONObject(0).getString("code").equals("WRITE_CONFLICT"));yes(conflict.getJSONArray("appointments").getJSONObject(0).getString("notes").equals("parallel"));
  rejects(()->OwnerJournalRules.validateArchive("order",s.getJSONArray("orders").getJSONObject(0),s));
  rejects(()->AppointmentRules.local("2026-03-29T02:30","Europe/Vienna",""));rejects(()->AppointmentRules.local("2026-10-25T02:30","Europe/Vienna",""));
  yes(AppointmentRules.local("2026-10-25T02:30","Europe/Vienna","+02:00").equals(Instant.parse("2026-10-25T00:30:00Z")));yes(AppointmentRules.local("2026-10-25T02:30","Europe/Vienna","+01:00").equals(Instant.parse("2026-10-25T01:30:00Z")));
  JSONObject utc=SyntheticBusinessData.object(a.toString()).put("starts_at","2026-12-31T23:30:00Z").put("ends_at","2027-01-01T00:30:00Z");AppointmentRules.validate(utc,null,snapshot());yes(utc.getString("timezone").equals("Europe/Vienna"));
  JSONObject remote=event(a,"v1"),base=SyntheticBusinessData.object(a.toString());CalendarRules.requireIdentity(remote,"qa_a");yes(CalendarRules.eventId("qa_a").matches("[0-9a-v]{5,1024}"));
  JSONObject moved=event(a,"v2");moved.getJSONObject("start").put("dateTime","2026-10-19T09:00:00Z");moved.getJSONObject("end").put("dateTime","2026-10-19T11:00:00Z");
  JSONObject merged=CalendarRules.changes(SyntheticBusinessData.object(a.toString()).put("notes","local note"),base,moved);yes(merged.getString("starts_at").equals("2026-10-19T09:00:00Z")&&merged.getString("notes").equals("local note"));
  rejects(()->CalendarRules.changes(SyntheticBusinessData.object(a.toString()).put("starts_at","2026-10-19T12:00:00Z"),base,moved));rejects(()->CalendarRules.requireIdentity(event(a,"e"),"wrong"));
  rejects(()->CalendarRules.changes(a,base,event(a,"x").put("status","cancelled")));rejects(()->CalendarRules.changes(a,base,event(a,"x").put("start",SyntheticBusinessData.object().put("date","2026-10-19"))));
  rejects(()->CalendarRules.changes(a,base,event(a,"x").put("recurrence",new JSONArray().put("RRULE:FREQ=WEEKLY"))));
  Instant now=Instant.parse("2026-10-19T07:00:00Z");JSONObject cache=forecast(now),preferences=SyntheticBusinessData.object();yes(WeatherRiskRules.evaluate(a,cache,now,preferences).getString("severity").equals("clear"));
  JSONObject h=cache.getJSONObject("forecast").getJSONObject("hourly");h.getJSONArray("precipitation").put(9,2);h.getJSONArray("precipitation_probability").put(9,90);
  JSONObject rainy=WeatherRiskRules.evaluate(a,cache,now,preferences);yes(rainy.getString("severity").equals("difficult"));yes(rainy.getString("alert_hash").equals(WeatherRiskRules.evaluate(SyntheticBusinessData.object(a.toString()).put("notes","irrelevant note"),cache,now,preferences).getString("alert_hash")));
  JSONObject late=SyntheticBusinessData.object(a.toString()).put("starts_at","2026-10-19T11:00:00Z").put("ends_at","2026-10-19T12:00:00Z");yes(WeatherRiskRules.evaluate(late,cache,now,preferences).getString("severity").equals("clear"));
  yes(WeatherRiskRules.evaluate(a,null,now,preferences).getString("severity").equals("unknown"));yes(WeatherRiskRules.evaluate(a,cache,now.plusSeconds(7*3600),preferences).getString("severity").equals("unknown"));
  JSONObject noLocation=SyntheticBusinessData.object(a.toString());noLocation.remove("latitude");yes(WeatherRiskRules.evaluate(noLocation,cache,now,preferences).getString("severity").equals("unknown"));
  h.getJSONArray("wind_gusts_10m").put(9,60);yes(WeatherRiskRules.evaluate(SyntheticBusinessData.object(a.toString()).put("weather_profile","hedge"),cache,now,preferences).getString("severity").equals("critical"));
  h.getJSONArray("temperature_2m").put(8,-2);yes(WeatherRiskRules.evaluate(a,cache,now,preferences).getJSONArray("reasons").toString().contains("Frost"));h.getJSONArray("temperature_2m").put(8,34);yes(WeatherRiskRules.evaluate(a,cache,now,preferences).getJSONArray("reasons").toString().contains("Hitze"));
  h.getJSONArray("precipitation_probability").put(9,JSONObject.NULL);yes(WeatherRiskRules.evaluate(a,cache,now,preferences).getString("severity").equals("unknown"));
  JSONObject warnings=SyntheticBusinessData.object("{properties:{warnings:[{properties:{begin:1792396800,end:1792404000,warnstufeid:2,warntypid:5}}]}}");yes(WeatherRiskRules.parseWarnings(warnings).getJSONObject(0).getString("type").equals("Gewitter"));rejects(()->WeatherRiskRules.parseWarnings(SyntheticBusinessData.object()));
  JSONObject official=SyntheticBusinessData.object().put("fetched_at",now.minusSeconds(86400).toString()).put("warnings_fetched_at",now.toString()).put("warnings",new JSONArray().put(SyntheticBusinessData.object().put("starts_at",a.getString("starts_at")).put("ends_at",a.getString("ends_at")).put("level",2).put("type","Gewitter")));JSONObject officialOnly=WeatherRiskRules.evaluate(a,official,now,preferences);yes(officialOnly.getString("severity").equals("critical")&&officialOnly.getBoolean("forecast_unknown"));
  Provider wire=new Provider();CalendarClient client=new CalendarClient(()->"synthetic",wire);wire.failAck=true;rejects(()->client.save("test",a,null));yes(wire.inserts==1);JSONObject after=client.save("test",a,null);yes(wire.inserts==1&&after.getString("etag").equals("v1"));
  JSONObject changedRow=SyntheticBusinessData.object(a.toString()).put("title","changed");client.save("test",changedRow,after);yes(wire.patches==1);wire.stale=true;rejects(()->client.save("test",a,after));wire.stale=false;client.remove("test",CalendarRules.eventId("qa_a"),wire.remote);yes(wire.deletes==1&&wire.remote==null);client.remove("test",CalendarRules.eventId("qa_a"),null);yes(wire.deletes==1);
  wire.expired=true;yes(client.changes("test","old").getBoolean("reset_required"));wire.expired=false;yes(client.changes("test","").getString("next_sync_token").equals("next"));wire.deny=true;rejects(()->client.get("test","id"));wire.deny=false;wire.retry=true;try{client.get("test","id");throw new AssertionError();}catch(DriveRestClient.Failure f){yes(f.retryAfter==120000&&!f.userAction);}
  for(String name:AppointmentTools.READS)McpSchema.validate(SyntheticBusinessData.object(),name.equals("get_appointment")?AppointmentTools.schema("get_planning_context"):name.equals("suggest_appointment_slots")?AppointmentTools.schema("get_planning_context"):AppointmentTools.schema(name));
  JSONObject create=SyntheticBusinessData.object(row().toString()).put("request_id","qa-create");create.remove("id");McpSchema.validate(create,AppointmentTools.schema("create_appointment"));rejects(()->McpSchema.validate(SyntheticBusinessData.object(create.toString()).put("sql","unsafe"),AppointmentTools.schema("create_appointment")));
  PixelMcpReadTools reader=new PixelMcpReadTools(()->SyntheticBusinessData.object().put("master",snapshot()).put("pending",new JSONArray()));rejects(()->reader.call("create_appointment",create));yes(reader.tools().toString().contains("get_workweek_weather"));
  System.out.println("PASS "+checks+" planning/time/weather/Calendar/CAS/response-loss/schema checks; synthetic JVM, no real account access");
 }
}
