package at.hausgarten.service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

/** Synthetic clock and error contracts for indefinitely recoverable calendar transport. */
public final class CalendarRecoveryTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Calendar recovery check "+checks);}
    public static void main(String[] args)throws Exception {
        SyntheticBusinessData.verify();
        int failures=0;long previous=0;
        for(int i=0;i<1000;i++){
            long delay=CalendarRecoveryRules.retryDelay(failures,0,2999);
            yes(delay>=previous&&delay>=30000&&delay<=3600000);
            previous=delay;failures=CalendarRecoveryRules.nextFailures(failures);yes(failures<=8);
        }
        yes(failures==8&&previous==3600000);
        yes(CalendarRecoveryRules.nextFailures(Integer.MAX_VALUE)==8);
        yes(CalendarRecoveryRules.retryDelay(Integer.MAX_VALUE,7200000,-5)==7200000);
        yes(CalendarRecoveryRules.retryDelay(-1,0,0)==30000);
        yes(CalendarRecoveryRules.retryDelay(0,120000,2999)==120000);
        yes(CalendarRecoveryRules.interrupted(new ExecutionException(new InterruptedException("qa_private"))));
        yes(CalendarRecoveryRules.interrupted(new java.util.concurrent.CancellationException("qa_private")));
        yes(!CalendarRecoveryRules.interrupted(new java.net.SocketTimeoutException("qa_private")));
        yes(!CalendarRecoveryRules.interrupted(new java.io.IOException("qa_private")));
        yes(CalendarAccessRules.code(new InterruptedException("qa_private")).equals("CALENDAR_INTERRUPTED"));
        yes(CalendarAccessRules.authenticationCode(new InterruptedException("qa_private")).equals("CALENDAR_INTERRUPTED"));
        yes(CalendarAccessRules.missingResultCode(0).equals("CALENDAR_CONSENT_CANCELLED"));
        yes(CalendarAccessRules.missingResultCode(-1).equals("CALENDAR_AUTH_RESULT_MISSING"));
        yes(CalendarAccessRules.missingResultCode(20).equals("CALENDAR_AUTH_RESULT_MISSING"));
        yes(!CalendarAccessRules.message("CALENDAR_CONSENT_CANCELLED").contains("wurde abgebrochen"));
        for(String code:new String[]{"CALENDAR_INTERRUPTED","CALENDAR_AUTH_RESULT_MISSING"}){
            yes(CalendarAccessRules.safeCode(code).equals(code));yes(!CalendarAccessRules.message(code).contains("qa_private"));
        }
        Instant now=Instant.parse("2026-10-21T12:00:00Z");
        yes(CalendarRecoveryRules.fresh(now.toString(),now));yes(CalendarRecoveryRules.fresh(now.minusSeconds(1800).toString(),now));
        yes(!CalendarRecoveryRules.fresh(now.minusSeconds(1801).toString(),now));yes(!CalendarRecoveryRules.fresh(now.plusSeconds(301).toString(),now));
        yes(!CalendarRecoveryRules.fresh("qa_invalid",now));yes(!CalendarRecoveryRules.fresh("",now));
        System.out.println("PASS "+checks+" bounded long-outage recovery, cancellation/network distinction, consent result and truthful check-time contracts; synthetic only");
    }
}
