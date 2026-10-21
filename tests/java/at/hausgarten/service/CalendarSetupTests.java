package at.hausgarten.service;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Same CalendarClient setup path as device consent; isolated provider, no real account. */
public final class CalendarSetupTests {
 static int checks;static void yes(boolean v){checks++;if(!v)throw new AssertionError("Calendar setup check "+checks);}
 interface Task{void run()throws Exception;}static void rejects(Task run)throws Exception{try{run.run();throw new AssertionError("Expected safe rejection");}catch(java.io.IOException|IllegalArgumentException expected){checks++;}}
 static final class State implements CalendarClient.CalendarSetup{boolean pending;String id="";public boolean pending(){return pending;}public void pending(boolean v){pending=v;}public String confirmedId(){return id;}public void confirmedId(String value){id=value;}}
 static final class Provider implements DriveRestClient.Wire{
  JSONArray calendars=new JSONArray();int created;boolean lost,hidden,readLost;int status=200;
  public DriveRestClient.Reply send(String m,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception{
   yes(url.startsWith(CalendarClient.API)&&headers.get("Authorization").equals("Bearer synthetic"));JSONObject out;
   if(status!=200)return new DriveRestClient.Reply(status,new byte[0],Collections.singletonMap("Retry-After","120"));
   if(url.contains("calendarList"))out=new JSONObject().put("items",hidden?new JSONArray():calendars);
   else if(m.equals("POST")){created++;JSONObject c=new JSONObject(new String(body,StandardCharsets.UTF_8)).put("id","qa-calendar").put("accessRole","owner");calendars.put(c);if(lost){lost=false;throw new java.io.IOException("Synthetic reply loss");}out=c;}
   else {if(readLost){readLost=false;hidden=true;throw new java.io.IOException("Synthetic first readback loss");}out=calendars.getJSONObject(0);}
   return new DriveRestClient.Reply(200,out.toString().getBytes(StandardCharsets.UTF_8),Collections.emptyMap());
  }
 }
 public static void main(String[] args)throws Exception{
  SyntheticBusinessData.verify();Provider wire=new Provider();State state=new State();CalendarClient client=new CalendarClient(()->"synthetic",wire);String marker="hausgarten.app.calendar.v1:qa_device";
  wire.lost=true;rejects(()->client.ensureOwnCalendar(marker,state));yes(wire.created==1&&state.pending);wire.hidden=true;rejects(()->client.ensureOwnCalendar(marker,state));yes(wire.created==1&&state.pending);wire.hidden=false;
  yes(client.ensureOwnCalendar(marker,state).getString("id").equals("qa-calendar")&&!state.pending&&wire.created==1);client.ensureOwnCalendar(marker,state);yes(wire.created==1);wire.hidden=true;client.ensureOwnCalendar(marker,state);yes(wire.created==1);wire.hidden=false;
  wire.calendars.put(new JSONObject(wire.calendars.getJSONObject(0).toString()).put("id","qa-duplicate"));rejects(()->client.ensureOwnCalendar(marker,state));yes(wire.created==1);
  wire.calendars=new JSONArray().put(new JSONObject().put("id","qa-foreign").put("description",marker).put("accessRole","reader"));rejects(()->client.ensureOwnCalendar(marker,state));yes(wire.created==1);
  final int[] invalidations={0};CalendarClient guarded=new CalendarClient(new CalendarClient.Token(){public String get(){return "synthetic";}public void invalidate(String value){yes(value.equals("synthetic"));invalidations[0]++;}},wire);
  for(int code:new int[]{401,403,429,500,503}){wire.status=code;try{guarded.get("qa-calendar","qa-event");throw new AssertionError();}catch(DriveRestClient.Failure failure){yes(failure.userAction==(code==401||code==403));if(code==429||code>=500)yes(failure.retryAfter==120000);}}
  yes(invalidations[0]==2);JSONObject a=PlanningTests.row(),event=CalendarRules.event(a,"QA Gartenkonto");yes(event.getString("summary").equals("QA Gartenkonto · "+a.getString("title")));yes(!event.has("attendees")&&!event.has("recurrence")&&!event.optString("description").contains("qa_a"));
  Provider delayed=new Provider();delayed.readLost=true;State remembered=new State();CalendarClient recovering=new CalendarClient(()->"synthetic",delayed);rejects(()->recovering.ensureOwnCalendar(marker,remembered));yes(remembered.pending&&remembered.id.equals("qa-calendar")&&delayed.created==1);
  State restarted=new State();restarted.pending=remembered.pending;restarted.id=remembered.id;yes(recovering.ensureOwnCalendar(marker,restarted).getString("id").equals("qa-calendar")&&!restarted.pending&&delayed.created==1);
  yes(CalendarRules.title(event).equals(a.getString("title")));yes(CalendarRules.projectionHash(CalendarRules.changes(a,a,event)).equals(CalendarRules.projectionHash(a)));
  event.put("summary","Extern geänderter Titel");yes(CalendarRules.title(event).equals("Extern geänderter Titel"));
  System.out.println("PASS "+checks+" calendar setup/readback/restart/lost-reply/duplicate/permissions/retry/title checks; synthetic provider only");
 }
}
