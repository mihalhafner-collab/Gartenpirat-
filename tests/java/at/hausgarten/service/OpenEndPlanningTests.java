package at.hausgarten.service;
import org.json.*;
import java.time.*;

/** Real rules, Drive replay, Calendar projection and weather; no device/account I/O. */
public final class OpenEndPlanningTests {
 static int checks;
 static void yes(boolean value){checks++;if(!value)throw new AssertionError("Open end check "+checks);}
 interface Task{void run()throws Exception;}
 static void rejects(Task task)throws Exception{try{task.run();}catch(IllegalArgumentException expected){yes(true);return;}throw new AssertionError("Expected guarded rejection");}
 static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
 static JSONObject open()throws Exception{return PlanningTests.row().put("ends_at","");}
 public static void main(String[] args)throws Exception {
  SyntheticBusinessData.verify();JSONObject snapshot=PlanningTests.snapshot(),a=open();AppointmentRules.validate(a,null,snapshot);
  yes(a.getString("ends_at").isEmpty()&&AppointmentRules.openEnd(a));yes(a.getString("starts_at").equals("2026-10-19T08:00:00Z"));
  JSONObject missing=copy(a);missing.remove("ends_at");try{AppointmentRules.validate(missing,null,snapshot);throw new AssertionError("Missing end is not explicit open");}catch(JSONException expected){yes(true);}
  rejects(()->AppointmentRules.validate(copy(a).put("ends_at","invalid"),null,snapshot));
  rejects(()->AppointmentRules.validate(copy(a).put("ends_at",a.getString("starts_at")),null,snapshot));
  JSONObject paused=copy(a).put("break_minutes",30);AppointmentRules.validate(paused,null,snapshot);yes(paused.getInt("break_minutes")==30);
  JSONObject later=PlanningTests.row().put("id","qa_later").put("starts_at","2026-10-19T11:00:00Z").put("ends_at","2026-10-19T12:00:00Z");
  yes(AppointmentRules.overlapKind(a,later)==1&&AppointmentRules.overlapKind(later,a)==1);
  JSONObject before=PlanningTests.row().put("id","qa_before").put("starts_at","2026-10-19T07:00:00Z").put("ends_at",a.getString("starts_at"));yes(AppointmentRules.overlapKind(a,before)==0);
  JSONObject same=copy(a).put("id","qa_same");yes(AppointmentRules.overlapKind(a,same)==2);
  JSONObject intersect=PlanningTests.row().put("id","qa_intersect").put("starts_at","2026-10-19T07:30:00Z").put("ends_at","2026-10-19T08:30:00Z");yes(AppointmentRules.overlapKind(a,intersect)==2);
  snapshot.getJSONArray("appointments").put(a);AppointmentRules.validate(later,null,snapshot);yes(true);rejects(()->AppointmentRules.validate(same,null,snapshot));
  JSONObject report=AppointmentAvailability.assess(a,new JSONArray().put(later),new JSONObject());yes(!report.getBoolean("app_checked")&&!report.getBoolean("external_checked")&&report.getBoolean("duration_unknown"));yes(report.getJSONArray("conflicts").getJSONObject(0).getBoolean("uncertain"));
  report=AppointmentAvailability.assess(later,new JSONArray().put(a),new JSONObject());yes(!report.getBoolean("app_checked")&&report.getJSONArray("conflicts").getJSONObject(0).getBoolean("uncertain"));
  JSONObject availability=new JSONObject().put("verified",true).put("from","2026-10-19T00:00:00Z").put("to","2026-10-20T00:00:00Z").put("busy",new JSONArray().put(new JSONObject().put("start","2026-10-19T11:00:00Z").put("end","2026-10-19T12:00:00Z")));
  report=AppointmentAvailability.assess(a,new JSONArray(),availability);yes(!report.getBoolean("external_checked")&&report.getJSONArray("conflicts").getJSONObject(0).getBoolean("uncertain"));
  availability.getJSONArray("busy").getJSONObject(0).put("start","2026-10-19T07:30:00Z").put("end","2026-10-19T08:30:00Z");yes(!AppointmentAvailability.assess(a,new JSONArray(),availability).getJSONArray("conflicts").getJSONObject(0).getBoolean("uncertain"));
  JSONObject state=new JSONObject().put("master",snapshot).put("pending",new JSONArray());JSONObject detail=AppointmentTools.query("get_appointment",new JSONObject().put("appointment_id",a.getString("id")),state);yes(detail.isNull("planned_hours")&&detail.getDouble("actual_hours")==0);
  JSONObject payload=copy(a).put("request_id","qa_open_contract");payload.remove("id");McpSchema.validate(payload,AppointmentTools.schema("create_appointment"));yes(true);
  // Normal compare-and-save, immutable original ID and idempotent Drive replay.
  JSONObject edit=new JSONObject().put("appointment_id",a.getString("id")).put("expected_record_hash",AppointmentRules.hash(a)).put("updates",new JSONObject().put("ends_at","2026-10-19T09:30:00Z"));
  JSONObject updated=AppointmentRules.prepare("update_appointment",edit,snapshot,"qa_open_to_known","2026-10-19T07:00:00Z");yes(!AppointmentRules.openEnd(updated));
  JSONObject event=new JSONObject().put("client_id","qa_open_to_known").put("kind","appointment").put("payload",updated).put("created_at","2026-10-19T07:00:00Z");yes(DriveJournalRules.apply(snapshot,new JSONArray().put(event)).length()==0);yes(DriveJournalRules.apply(snapshot,new JSONArray().put(event)).length()==0);yes(snapshot.getJSONArray("appointments").length()==1);
  JSONObject known=snapshot.getJSONArray("appointments").getJSONObject(0);detail=AppointmentTools.query("get_appointment",new JSONObject().put("appointment_id",known.getString("id")),state);yes(detail.getDouble("planned_hours")==1.5);
  rejects(()->AppointmentRules.prepare("update_appointment",edit,snapshot,"qa_stale_open","t"));
  JSONObject back=new JSONObject().put("appointment_id",known.getString("id")).put("expected_record_hash",AppointmentRules.hash(known)).put("updates",new JSONObject().put("ends_at",""));yes(AppointmentRules.openEnd(AppointmentRules.prepare("update_appointment",back,snapshot,"qa_known_to_open","t")));
  JSONObject external=CalendarRules.event(a,"QA Testkunde");yes(external.getBoolean("endTimeUnspecified"));yes(external.getString("description").contains("Ende offen"));yes(CalendarRules.changes(a,a,external).getString("ends_at").isEmpty());
  JSONObject changed=copy(external);changed.getJSONObject("start").put("dateTime","2026-10-19T09:00:00Z");changed.getJSONObject("end").put("dateTime","2026-10-19T09:01:00Z");yes(CalendarRules.changes(a,a,changed).getString("ends_at").isEmpty());
  yes(!CalendarRules.event(known).getBoolean("endTimeUnspecified"));yes(!CalendarRules.projectionHash(a).equals(CalendarRules.projectionHash(known)));
  PlanningTests.Provider provider=new PlanningTests.Provider();CalendarClient client=new CalendarClient(()->"synthetic",provider);JSONObject published=client.save("qa_calendar",a,null,"QA Testkunde");yes(published.getBoolean("endTimeUnspecified")&&CalendarRules.changes(a,a,published).getString("ends_at").isEmpty());
  JSONObject fixedRemote=client.save("qa_calendar",known,published,"QA Testkunde");yes(!fixedRemote.getBoolean("endTimeUnspecified")&&CalendarRules.changes(known,known,fixedRemote).getString("ends_at").equals(known.getString("ends_at")));
  JSONObject openAgain=client.save("qa_calendar",a,fixedRemote,"QA Testkunde");yes(openAgain.getBoolean("endTimeUnspecified")&&CalendarRules.changes(a,a,openAgain).getString("ends_at").isEmpty());
  Instant now=Instant.parse("2026-10-19T07:00:00Z");JSONObject weather=WeatherRiskRules.evaluate(a,PlanningTests.forecast(now),now,new JSONObject());yes(weather.getBoolean("duration_unknown")&&weather.getString("time_scope").equals("start_only"));yes(weather.getString("severity").equals("clear"));yes(weather.getJSONArray("reasons").toString().contains("Ende offen"));
  yes(FreeWeatherSource.nextDelayMillis(new JSONArray().put(a),now.plusSeconds(4*3600))!=Long.MAX_VALUE);yes(AppointmentRules.pending(a,now.plusSeconds(86400)));
  JSONObject critical=weather.put("severity","critical");yes(WeatherAlertRules.actionable(a,critical,now.plusSeconds(4*3600)));
  JSONObject inactive=copy(a).put("status","completed");yes(!AppointmentRules.pending(inactive,now)&&!WeatherAlertRules.actionable(inactive,critical,now));
  OwnerApplicationTests.MemoryRepository repository=new OwnerApplicationTests.MemoryRepository();repository.state.put("master",PlanningTests.snapshot());
  JSONObject series=copy(a).put("occurrences",2).put("interval_weeks",1);series.remove("id");series.put("starts_at","2026-10-19T08:00:00Z");
  JSONObject created=new PlanningApplicationService(repository).perform("create_appointment_series",series,"qa_open_series");yes(created.getJSONArray("appointment_ids").length()==2&&repository.pendingActions().length()==2);
  for(int i=0;i<2;i++)yes(repository.pendingActions().getJSONObject(i).getJSONObject("payload").getString("ends_at").isEmpty());
  JSONObject drive=PlanningTests.snapshot();yes(DriveJournalRules.apply(drive,repository.pendingActions()).length()==0&&drive.getJSONArray("appointments").length()==2);yes(DriveJournalRules.apply(drive,repository.pendingActions()).length()==0&&drive.getJSONArray("appointments").length()==2);
  for(int i=0;i<2;i++)yes(AppointmentRules.openEnd(drive.getJSONArray("appointments").getJSONObject(i)));
  System.out.println("PASS "+checks+" explicit open ends, real/possible overlaps, hours, CAS/Drive replay, Calendar roundtrip, weather/Pixel notification eligibility and recurrence checks; synthetic only");
 }
}
