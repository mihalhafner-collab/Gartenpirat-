package at.hausgarten.service;

import java.time.Instant;

/** Bounded request cadence, without permanently exhausting a calendar connection. */
final class CalendarRecoveryRules {
    static final long MIN_RETRY=30000,MAX_RETRY=3600000;
    static int nextFailures(int previous){return previous>=8?8:Math.max(0,previous)+1;}
    static long retryDelay(int failures,long providerDelay,int jitter){
        long backoff=Math.min(MAX_RETRY,MIN_RETRY*(1L<<Math.min(7,Math.max(0,failures))));
        return Math.max(providerDelay,Math.min(MAX_RETRY,backoff+Math.max(0,Math.min(2999,jitter))));
    }
    static boolean interrupted(Throwable error){
        for(int i=0;i<8&&error!=null;i++,error=error.getCause())
            if(error instanceof InterruptedException||error instanceof java.io.InterruptedIOException&&!(error instanceof java.net.SocketTimeoutException)||error instanceof java.util.concurrent.CancellationException||error instanceof HttpTransport.Failure&&((HttpTransport.Failure)error).code.equals("CALENDAR_INTERRUPTED"))return true;
        return false;
    }
    static boolean fresh(String value,Instant now){
        try{Instant checked=Instant.parse(value);return !checked.isAfter(now.plusSeconds(300))&&!checked.isBefore(now.minusSeconds(1800));}catch(Exception absent){return false;}
    }
    private CalendarRecoveryRules(){}
}
