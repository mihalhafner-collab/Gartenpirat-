package at.hausgarten.service;

/** One operation, one account/scope snapshot. Credentials are memory-only. */
final class CalendarCredential implements CalendarClient.Token {
    interface Source {String acquire()throws Exception;void invalidate(String rejected)throws Exception;}
    private final Source source;
    private String value;
    CalendarCredential(String granted,Source source){this.value=granted==null?"":granted;this.source=source;}
    @Override public synchronized String get()throws Exception {
        if(value.isEmpty())value=source.acquire();
        if(value==null||value.isEmpty()){value="";throw new DriveRestClient.Failure("CALENDAR_AUTH_REQUIRED",CalendarAccessRules.message("CALENDAR_AUTH_REQUIRED"),0,true);}
        return value;
    }
    @Override public synchronized void invalidate(String rejected)throws Exception {
        if(value.equals(rejected)){value="";source.invalidate(rejected);}
    }
}
