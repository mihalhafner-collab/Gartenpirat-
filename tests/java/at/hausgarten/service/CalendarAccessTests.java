package at.hausgarten.service;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class CalendarAccessTests {
 static int checks;static void yes(boolean v){checks++;if(!v)throw new AssertionError("Calendar access check "+checks);}
 static HttpTransport.Reply reply(int code,String reason)throws Exception{return new HttpTransport.Reply(code,new JSONObject().put("error",new JSONObject().put("message","qa_secret@example.invalid synthetic-private").put("errors",new JSONArray().put(new JSONObject().put("reason",reason)))).toString().getBytes(StandardCharsets.UTF_8),Collections.singletonMap("Retry-After","120"));}
 static final class Creation implements HttpTransport.Wire {
  int status,attempts,created;JSONObject calendar;
  Creation(int status){this.status=status;}
  public HttpTransport.Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception {
   JSONObject out=new JSONObject();if(url.contains("calendarList"))out.put("items",calendar==null?new JSONArray():new JSONArray().put(calendar));
   else if(method.equals("POST")){attempts++;if(status!=200)return reply(status,"insufficientPermissions");created++;calendar=new JSONObject(new String(body,StandardCharsets.UTF_8)).put("id","qa_calendar").put("accessRole","owner");out=calendar;}else out=calendar;
   return new HttpTransport.Reply(200,out.toString().getBytes(StandardCharsets.UTF_8),Collections.emptyMap());
  }
 }
 static final class OperationCanceledException extends Exception{}
 public static void main(String[] args)throws Exception {
  SyntheticBusinessData.verify();
  for(String reason:new String[]{"rateLimitExceeded","userRateLimitExceeded","quotaExceeded","dailyLimitExceeded","RATE_LIMIT_EXCEEDED"}){HttpTransport.Failure f=CalendarAccessRules.failure(reply(403,reason));yes(f.code.equals("CALENDAR_RATE_LIMIT")&&!f.userAction&&f.retryAfter==120000);yes(!f.getMessage().contains("qa_secret"));}
  for(String reason:new String[]{"accessNotConfigured","SERVICE_DISABLED","API_DISABLED"})yes(CalendarAccessRules.failure(reply(403,reason)).code.equals("CALENDAR_API_DISABLED"));
  yes(CalendarAccessRules.failure(reply(403,"insufficientPermissions")).userAction);yes(CalendarAccessRules.failure(reply(500,"")).code.equals("CALENDAR_RETRY"));
  yes(CalendarAccessRules.authenticationCode(new OperationCanceledException()).equals("CALENDAR_CONSENT_CANCELLED"));yes(CalendarAccessRules.authenticationCode(new Exception("UNREGISTERED_ON_API_CONSOLE qa_secret")).equals("CALENDAR_APP_REGISTRATION_REQUIRED"));yes(CalendarAccessRules.authenticationCode(new java.io.IOException("qa_secret")).equals("CALENDAR_TOKEN_NETWORK"));yes(CalendarAccessRules.safeCode("qa_secret").equals("CALENDAR_UNKNOWN"));yes(CalendarAccessRules.safeCode("").isEmpty());
  yes(!CalendarAccessRules.scope(false).contains("freebusy")&&CalendarAccessRules.scope(true).endsWith("calendar.freebusy"));yes(!CalendarAccessRules.scope(false).contains("auth/calendar "));yes(CalendarAccessRules.message("qa_secret").equals(CalendarAccessRules.message("CALENDAR_UNKNOWN")));
  final int[] reads={0},invalidations={0};CalendarClient refreshed=new CalendarClient(new CalendarClient.Token(){public String get(){return reads[0]==0?"qa_expired":"qa_fresh";}public void invalidate(String token){yes(token.equals("qa_expired"));invalidations[0]++;}},(m,u,h,b,max)->{reads[0]++;if(reads[0]==1)return reply(401,"");yes(h.get("Authorization").equals("Bearer qa_fresh"));return new HttpTransport.Reply(404,new byte[0],Collections.emptyMap());});
  yes(refreshed.get("qa_calendar","qa_event")==null&&reads[0]==2&&invalidations[0]==1);
  for(int code:new int[]{400,401,403,405,429}){Creation wire=new Creation(code);CalendarSetupTests.State state=new CalendarSetupTests.State();CalendarClient client=new CalendarClient(()->"qa_token",wire);try{client.ensureOwnCalendar("qa_marker",state);throw new AssertionError();}catch(HttpTransport.Failure expected){yes(CalendarAccessRules.rejectedCreation(expected));}yes(!state.pending&&wire.created==0&&state.id.isEmpty());wire.status=200;yes(client.ensureOwnCalendar("qa_marker",state).getString("id").equals("qa_calendar")&&wire.created==1&&!state.pending);}
  Creation uncertain=new Creation(503);CalendarSetupTests.State state=new CalendarSetupTests.State();CalendarClient client=new CalendarClient(()->"qa_token",uncertain);try{client.ensureOwnCalendar("qa_marker",state);throw new AssertionError();}catch(HttpTransport.Failure expected){yes(!CalendarAccessRules.rejectedCreation(expected)&&state.pending);}uncertain.status=200;try{client.ensureOwnCalendar("qa_marker",state);throw new AssertionError();}catch(HttpTransport.Failure expected){yes(expected.code.equals("CALENDAR_CREATE_PENDING")&&uncertain.attempts==1&&uncertain.created==0);}
  System.out.println("PASS "+checks+" calendar token refresh, explicit denial recovery, ambiguous response quarantine, redaction and unchanged scope checks; synthetic only");
 }
}
