package at.hausgarten.service;

import java.util.*;

/** Real lease/CalendarClient with synthetic token/provider boundaries; no Google account. */
public final class CalendarAuthorizationTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Calendar authorization check "+checks);}
    static final class Source implements CalendarCredential.Source {
        int acquisitions,invalidations;
        String next="qa_fresh_credential";
        public String acquire(){acquisitions++;return next;}
        public void invalidate(String rejected){yes(rejected.equals("qa_granted_credential"));invalidations++;}
    }
    public static void main(String[] args)throws Exception {
        SyntheticBusinessData.verify();
        List<String> base=CalendarAccessRules.requestedScopes(false),busy=CalendarAccessRules.requestedScopes(true);
        yes(base.equals(Arrays.asList("https://www.googleapis.com/auth/calendar.app.created","https://www.googleapis.com/auth/calendar.calendarlist.readonly")));
        yes(busy.size()==3&&busy.containsAll(base)&&busy.contains(CalendarAccessRules.BUSY_SCOPE));
        yes(CalendarAccessRules.scope(true).equals("oauth2:"+String.join(" ",busy)));
        yes(CalendarAccessRules.grantCode(false,"qa_token",base).isEmpty());
        yes(CalendarAccessRules.grantCode(true,"qa_token",busy).isEmpty());
        yes(CalendarAccessRules.grantCode(false,"qa_token",busy).isEmpty());
        yes(CalendarAccessRules.grantCode(true,"qa_token",base).equals("CALENDAR_BUSY_PERMISSION_REQUIRED"));
        for(List<String> missing:Arrays.asList(Collections.<String>emptyList(),base.subList(0,1),Collections.singletonList(CalendarAccessRules.BUSY_SCOPE))){
            yes(CalendarAccessRules.grantCode(false,"qa_token",missing).equals("CALENDAR_AUTH_REQUIRED"));
            yes(CalendarAccessRules.grantCode(true,"qa_token",missing).equals("CALENDAR_AUTH_REQUIRED"));
        }
        yes(CalendarAccessRules.grantCode(false,null,base).equals("CALENDAR_AUTH_REQUIRED"));
        yes(CalendarAccessRules.grantCode(false,"",base).equals("CALENDAR_AUTH_REQUIRED"));
        String binding=CalendarAccessRules.bindingHash("qa_a@example.invalid","qa_calendar_a",false);
        yes(binding.equals(CalendarAccessRules.bindingHash("qa_a@example.invalid","qa_calendar_a",false)));
        yes(!binding.equals(CalendarAccessRules.bindingHash("qa_b@example.invalid","qa_calendar_a",false)));
        yes(!binding.equals(CalendarAccessRules.bindingHash("qa_a@example.invalid","qa_calendar_b",false)));
        yes(!binding.equals(CalendarAccessRules.bindingHash("qa_a@example.invalid","qa_calendar_a",true)));
        yes(!binding.contains("qa_a")&&!binding.contains("qa_calendar"));
        Source source=new Source();CalendarCredential credential=new CalendarCredential("qa_granted_credential",source);
        yes(credential.get().equals("qa_granted_credential"));yes(credential.get().equals("qa_granted_credential")&&source.acquisitions==0);
        final int[] requests={0};
        CalendarClient client=new CalendarClient(credential,(method,url,headers,body,max)->{
            requests[0]++;yes(method.equals("GET")&&body==null);
            if(requests[0]==1){yes(headers.get("Authorization").equals("Bearer qa_granted_credential"));return new DriveRestClient.Reply(401,new byte[0],Collections.emptyMap());}
            yes(headers.get("Authorization").equals("Bearer qa_fresh_credential"));return new DriveRestClient.Reply(404,new byte[0],Collections.emptyMap());
        });
        yes(client.get("qa_calendar","qa_event")==null);yes(requests[0]==2&&source.invalidations==1&&source.acquisitions==1);
        yes(credential.get().equals("qa_fresh_credential")&&source.acquisitions==1);
        credential.invalidate("qa_granted_credential");yes(credential.get().equals("qa_fresh_credential")&&source.invalidations==1);
        Source isolated=new Source();isolated.next="qa_other_account_credential";CalendarCredential other=new CalendarCredential("",isolated);
        yes(other.get().equals("qa_other_account_credential")&&isolated.acquisitions==1);yes(credential.get().equals("qa_fresh_credential"));
        Source empty=new Source();empty.next="";CalendarCredential absent=new CalendarCredential("",empty);
        try{absent.get();throw new AssertionError();}catch(DriveRestClient.Failure expected){yes(expected.code.equals("CALENDAR_AUTH_REQUIRED")&&expected.userAction);}
        empty.next="qa_recovered_credential";yes(absent.get().equals("qa_recovered_credential")&&empty.acquisitions==2);
        final int[] posts={0};CalendarSetupTests.State pending=new CalendarSetupTests.State();
        CalendarClient deniedRefresh=new CalendarClient(new CalendarCredential("qa_granted_credential",new CalendarCredential.Source(){public String acquire()throws Exception{throw new DriveRestClient.Failure("CALENDAR_BUSY_PERMISSION_REQUIRED",CalendarAccessRules.message("CALENDAR_BUSY_PERMISSION_REQUIRED"),0,true);}public void invalidate(String rejected){yes(rejected.equals("qa_granted_credential"));}}),(method,url,headers,body,max)->{
            if(method.equals("POST")){posts[0]++;return new DriveRestClient.Reply(401,new byte[0],Collections.emptyMap());}
            return new DriveRestClient.Reply(200,"{\"items\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8),Collections.emptyMap());
        });
        try{deniedRefresh.ensureOwnCalendar("qa_marker",pending);throw new AssertionError();}catch(DriveRestClient.Failure expected){yes(expected.code.equals("CALENDAR_BUSY_PERMISSION_REQUIRED"));}
        yes(posts[0]==1&&!pending.pending&&pending.id.isEmpty());
        yes(CalendarAccessRules.googleStatus(10).equals("CALENDAR_APP_REGISTRATION_REQUIRED"));
        yes(CalendarAccessRules.googleStatus(7).equals("CALENDAR_TOKEN_NETWORK"));yes(CalendarAccessRules.googleStatus(15).equals("CALENDAR_TOKEN_NETWORK"));
        yes(CalendarAccessRules.googleStatus(16).equals("CALENDAR_CONSENT_CANCELLED"));yes(CalendarAccessRules.googleStatus(17).equals("CALENDAR_GOOGLE_SERVICES_REQUIRED"));
        yes(CalendarAccessRules.googleStatus(5).equals("CALENDAR_ACCOUNT_ACCESS_REQUIRED"));yes(CalendarAccessRules.googleStatus(123456).equals("CALENDAR_GOOGLE_AUTH_ERROR"));
        yes(CalendarAccessRules.authenticationCode(new Exception("invalid_scope qa_secret@example.invalid")).equals("CALENDAR_SCOPE_UNSUPPORTED"));
        for(String code:Arrays.asList(CalendarAccessRules.googleStatus(10),CalendarAccessRules.googleStatus(7),CalendarAccessRules.googleStatus(17),CalendarAccessRules.grantCode(true,"qa_token",base))){
            yes(!CalendarAccessRules.message(code).contains("qa_token")&&!CalendarAccessRules.message(code).contains("qa_secret"));
            yes(CalendarAccessRules.safeCode(code).equals(code));
        }
        System.out.println("PASS "+checks+" Google scope/partial-grant/one-operation credential/401/isolated-account/retry/redaction checks; no Google connection");
    }
}
