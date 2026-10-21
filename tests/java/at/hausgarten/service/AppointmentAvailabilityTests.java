package at.hausgarten.service;
import org.json.*;
import java.time.*;

public final class AppointmentAvailabilityTests {
 static int checks;static void yes(boolean v){checks++;if(!v)throw new AssertionError("Availability check "+checks);}
 static JSONObject row(String id,String start,String end,int travel)throws Exception{return new JSONObject().put("id",id).put("starts_at","2026-10-20T"+start+":00Z").put("ends_at","2026-10-20T"+end+":00Z").put("travel_minutes",travel).put("status","confirmed");}
 public static void main(String[] args)throws Exception {
  SyntheticBusinessData.verify();Instant now=Instant.parse("2026-10-20T06:00:00Z");JSONObject a=row("qa_a","09:00","10:00",15),other=row("qa_b","08:00","09:00",0),cache=new JSONObject().put("verified",true).put("checked_at",now.toString()).put("from","2026-10-20T00:00:00Z").put("to","2026-10-21T00:00:00Z").put("busy",new JSONArray().put(new JSONObject().put("start","2026-10-20T09:30:00Z").put("end","2026-10-20T10:30:00Z").put("private_title","qa_secret")));
  String original=cache.toString();JSONObject context=AppointmentAvailability.context(cache,true,now),report=AppointmentAvailability.assess(a,new JSONArray().put(a).put(other),context);
  yes(context.getBoolean("verified")&&!context.toString().contains("qa_secret")&&cache.toString().equals(original));yes(report.getBoolean("external_checked")&&report.getJSONArray("conflicts").length()==2);yes(report.getJSONArray("conflicts").getJSONObject(0).getBoolean("travel_only"));yes(!report.getJSONArray("conflicts").getJSONObject(1).getBoolean("travel_only"));
  a.put("travel_minutes",0);yes(AppointmentAvailability.assess(a,new JSONArray().put(other),new JSONObject()).getJSONArray("conflicts").length()==0);
  other.put("starts_at","2026-10-20T09:45:00Z").put("ends_at","2026-10-20T11:00:00Z");yes(!AppointmentAvailability.assess(a,new JSONArray().put(other),context).getJSONArray("conflicts").getJSONObject(0).getBoolean("travel_only"));
  for(String status:new String[]{"cancelled","completed","draft"}){other.put("status",status);yes(AppointmentAvailability.assess(a,new JSONArray().put(other),new JSONObject()).getJSONArray("conflicts").length()==0);}
  for(boolean enabled:new boolean[]{false,true}){JSONObject unavailable=AppointmentAvailability.context(cache,enabled,now.plusSeconds(1801));yes(!unavailable.getBoolean("verified")&&unavailable.getJSONArray("busy").length()==0);}
  yes(!AppointmentAvailability.context(cache,false,now).getBoolean("verified"));yes(!AppointmentAvailability.context(cache,true,now.minusSeconds(120)).getBoolean("verified"));
  a.put("ends_at","2026-10-22T09:00:00Z");yes(!AppointmentAvailability.assess(a,new JSONArray(),context).getBoolean("external_checked"));a.put("status","cancelled");yes(AppointmentAvailability.assess(a,new JSONArray(),context).getBoolean("inactive"));
  cache.getJSONArray("busy").getJSONObject(0).put("end","2026-10-20T08:00:00Z");yes(!AppointmentAvailability.context(cache,true,now).getBoolean("verified"));
  System.out.println("PASS "+checks+" local and Google collisions, travel buffers, half-open boundaries, inactive rows, stale/disabled/invalid availability and no mutation checks; synthetic only");
 }
}
