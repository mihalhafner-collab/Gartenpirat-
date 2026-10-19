package at.hausgarten.service;
import org.json.*;
import java.time.Instant;
public final class WorkSessionPauseTests {
    static JSONArray events(String... values)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<values.length;i+=2)out.put(SyntheticBusinessData.object().put("event_type",values[i]).put("occurred_at","2026-10-12T"+values[i+1]+":00Z"));return out;}
    static void check(String name,JSONArray events,String state,String now,int expected,boolean uncertain)throws Exception{String before=events.toString();JSONObject result=WorkSessionPauseRules.calculate(events,state,Instant.parse("2026-10-12T"+now+":00Z").toEpochMilli());if(result.getInt("break_minutes")!=expected||result.getBoolean("pause_unverified")!=uncertain||!before.equals(events.toString()))throw new AssertionError(name+" "+result);System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
        check("Stop without pause",events("START","08:00","STOP","12:00"),"STOPPED_PENDING_REVIEW","14:00",0,false);
        check("Lunch pause",events("START","08:00","PAUSE","12:00","RESUME","13:00","STOP","14:30"),"STOPPED_PENDING_REVIEW","16:00",60,false);
        check("Stop while paused",events("START","08:00","PAUSE","12:00","STOP","13:00"),"STOPPED_PENDING_REVIEW","14:00",60,false);
        check("Stop continue gap excluded",events("START","08:00","STOP","10:00","CONTINUE","10:30","STOP","12:00"),"STOPPED_PENDING_REVIEW","14:00",30,false);
        check("Multiple pauses and continue",events("START","08:00","PAUSE","09:00","RESUME","09:15","STOP","10:00","CONTINUE","10:30","PAUSE","12:00","STOP","12:30"),"STOPPED_PENDING_REVIEW","16:00",75,false);
        check("Open pause",events("START","08:00","PAUSE","12:00"),"PAUSED","12:30",30,false);
        check("Discard restore pause",events("START","08:00","DISCARD","10:00","RESTORE","10:05","RESUME","10:15","STOP","12:00"),"STOPPED_PENDING_REVIEW","14:00",10,false);
        check("Finalize does not bill review gap",events("START","08:00","STOP","10:00","FINALIZE","14:00"),"FINALIZED","16:00",0,false);
        check("Clock went backward",events("START","08:00","PAUSE","12:00","RESUME","11:00","STOP","14:00"),"STOPPED_PENDING_REVIEW","16:00",0,true);
        check("Clock went backward during open pause",events("START","08:00","PAUSE","12:00"),"PAUSED","11:00",0,true);
        System.out.println("RESULT 10 pause calculations passed; native SQLite transitions not executed");
    }
}
