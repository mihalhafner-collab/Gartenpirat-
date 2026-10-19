package at.hausgarten.service;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistent, event-backed work timer. It creates no business record by itself. */
final class WorkSessionStore {
    static final String RUNNING="RUNNING", PAUSED="PAUSED", STOPPED="STOPPED_PENDING_REVIEW", FINALIZED="FINALIZED", CANCELLED="CANCELLED";
    interface Clock { long wallMillis(); long elapsedMillis(); String bootMarker(); }
    static final class DeviceClock implements Clock {
        private final android.content.Context context;
        DeviceClock(){this.context=null;}
        DeviceClock(android.content.Context context){this.context=context;}
        public long wallMillis(){ return System.currentTimeMillis(); }
        public long elapsedMillis(){ return SystemClock.elapsedRealtime(); }
        public String bootMarker(){
            if(context!=null){int count=android.provider.Settings.Global.getInt(context.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,-1);if(count>=0)return "boot:"+count;}
            return Long.toString(wallMillis()-elapsedMillis());
        }
    }
    private final SQLiteDatabase db;
    private final Clock clock;
    WorkSessionStore(SQLiteDatabase db){ this(db,new DeviceClock()); }
    WorkSessionStore(SQLiteDatabase db,android.content.Context context){ this(db,new DeviceClock(context)); }
    WorkSessionStore(SQLiteDatabase db,Clock clock){ this.db=db;this.clock=clock; }

    static void createSchema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS work_sessions(id TEXT PRIMARY KEY,state TEXT NOT NULL,customer_id TEXT NOT NULL DEFAULT '',order_id TEXT NOT NULL DEFAULT '',work_date TEXT NOT NULL,activity TEXT NOT NULL DEFAULT '',started_at_ms INTEGER NOT NULL,segment_started_wall_ms INTEGER,segment_started_elapsed_ms INTEGER,boot_marker TEXT NOT NULL,worked_ms INTEGER NOT NULL DEFAULT 0,finalized_worklog_id TEXT NOT NULL DEFAULT '',updated_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS work_session_events(id TEXT PRIMARY KEY,session_id TEXT NOT NULL,event_type TEXT NOT NULL,request_id TEXT NOT NULL UNIQUE,occurred_at TEXT NOT NULL,payload TEXT NOT NULL DEFAULT '{}',FOREIGN KEY(session_id) REFERENCES work_sessions(id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS work_session_events_session ON work_session_events(session_id,occurred_at)");
    }

    JSONObject start(JSONObject input) throws Exception {
        db.beginTransaction();try{
        String request=required(input,"request_id"), existing=sessionForRequest(request);
        if(!existing.isEmpty()) return current(existing);
        if(activeId()!=null) throw new IllegalStateException("Es läuft bereits eine Arbeitssitzung.");
        String date=input.optString("work_date",LocalDate.now(java.time.ZoneId.of("Europe/Vienna")).toString());
        try{LocalDate.parse(date);}catch(Exception e){throw new IllegalArgumentException("Bitte ein gültiges Arbeitsdatum auswählen.");}
        long wall=clock.wallMillis(), elapsed=clock.elapsedMillis();
        String id="ses_pixel_"+UUID.randomUUID().toString().replace("-","");
        ContentValues row=new ContentValues();row.put("id",id);row.put("state",RUNNING);row.put("customer_id",input.optString("customer_id").trim());row.put("order_id",input.optString("order_id").trim());row.put("work_date",date);row.put("activity",input.optString("activity").trim());row.put("started_at_ms",wall);row.put("segment_started_wall_ms",wall);row.put("segment_started_elapsed_ms",elapsed);row.put("boot_marker",clock.bootMarker());row.put("worked_ms",0);row.put("updated_at",Instant.ofEpochMilli(wall).toString());
        transaction(()->{db.insertOrThrow("work_sessions",null,row);event(id,"START",request,input);});
        db.setTransactionSuccessful();return current(id);
        }finally{db.endTransaction();}
    }
    JSONObject pause(String id,String request)throws Exception{return transition(id,request,RUNNING,PAUSED,"PAUSE",true);}
    JSONObject discard(String id,String request)throws Exception{
        db.beginTransaction();try{
            String replay=sessionForRequest(request);
            if(!replay.isEmpty()){if(!id.equals(replay))throw new IllegalArgumentException("Anfrage gehört zu einer anderen Sitzung.");return current(id);}
            JSONObject before=current(id);String state=before.getString("state");
            if(!RUNNING.equals(state)&&!PAUSED.equals(state)&&!STOPPED.equals(state))throw new IllegalStateException("Nur eine noch nicht gespeicherte Zeiterfassung kann verworfen werden.");
            ContentValues values=new ContentValues();values.put("state",CANCELLED);values.put("worked_ms",before.getLong("worked_ms"));values.putNull("segment_started_wall_ms");values.putNull("segment_started_elapsed_ms");values.put("updated_at",nowIso());
            db.update("work_sessions",values,"id=?",new String[]{id});event(id,"DISCARD",request,new JSONObject());db.setTransactionSuccessful();return current(id);
        }finally{db.endTransaction();}
    }
    JSONObject restore(String id,String request)throws Exception{
        db.beginTransaction();try{
            String replay=sessionForRequest(request);
            if(!replay.isEmpty()){if(!id.equals(replay))throw new IllegalArgumentException("Anfrage gehört zu einer anderen Sitzung.");return current(id);}
            if(activeId()!=null)throw new IllegalStateException("Eine andere Zeiterfassung ist aktiv. Bitte diese zuerst abschließen.");
            if(!CANCELLED.equals(current(id).getString("state")))throw new IllegalStateException("Diese Zeiterfassung wurde nicht verworfen.");
            ContentValues values=new ContentValues();values.put("state",PAUSED);values.put("updated_at",nowIso());db.update("work_sessions",values,"id=?",new String[]{id});event(id,"RESTORE",request,new JSONObject());db.setTransactionSuccessful();return current(id);
        }finally{db.endTransaction();}
    }
    JSONObject resume(String id,String request)throws Exception{return transition(id,request,PAUSED,RUNNING,"RESUME",false);}
    JSONObject continueStopped(String id,String request)throws Exception{return transition(id,request,STOPPED,RUNNING,"CONTINUE",false);}
    JSONObject stop(String id,String request)throws Exception{
        JSONObject current=current(id);String state=current.getString("state");
        if(STOPPED.equals(state)){ if(id.equals(sessionForRequest(request)))return current; throw new IllegalStateException("Die Arbeit ist bereits gestoppt."); }
        if(!RUNNING.equals(state)&&!PAUSED.equals(state))throw new IllegalStateException("Diese Arbeitssitzung kann nicht gestoppt werden.");
        return transition(id,request,state,STOPPED,"STOP",RUNNING.equals(state));
    }
    JSONObject finalizeSession(String id,String worklogId,String request)throws Exception{
        if(worklogId==null||worklogId.trim().isEmpty())throw new IllegalArgumentException("Die Arbeitsaufzeichnung fehlt.");
        JSONObject before=current(id);
        if(FINALIZED.equals(before.getString("state"))){
            if(worklogId.equals(before.optString("finalized_worklog_id"))&&id.equals(sessionForRequest(request)))return before;
            throw new IllegalStateException("Diese Arbeitssitzung wurde bereits abgeschlossen.");
        }
        if(!STOPPED.equals(before.getString("state")))throw new IllegalStateException("Bitte die Arbeit zuerst stoppen und prüfen.");
        ContentValues values=new ContentValues();values.put("state",FINALIZED);values.put("finalized_worklog_id",worklogId);values.put("updated_at",nowIso());
        transaction(()->{db.update("work_sessions",values,"id=?",new String[]{id});event(id,"FINALIZE",request,new JSONObject().put("worklog_id",worklogId));});
        return current(id);
    }
    JSONObject current()throws Exception{String id=activeId();return id==null?new JSONObject().put("state","IDLE"):current(id);}
    JSONObject current(String id)throws Exception{
        try(Cursor c=db.rawQuery("SELECT id,state,customer_id,order_id,work_date,activity,started_at_ms,segment_started_wall_ms,segment_started_elapsed_ms,boot_marker,worked_ms,finalized_worklog_id,updated_at FROM work_sessions WHERE id=?",new String[]{id})){
            if(!c.moveToFirst())throw new IllegalArgumentException("Arbeitssitzung nicht gefunden.");
            long worked=c.getLong(10);String state=c.getString(1);
            if(RUNNING.equals(state))worked+=runningDelta(c.getLong(7),c.getLong(8),c.getString(9));
            String location="",appointment="",billingCustomer=c.getString(2);try(Cursor start=db.rawQuery("SELECT payload FROM work_session_events WHERE session_id=? AND event_type='START' LIMIT 1",new String[]{id})){if(start.moveToFirst()){JSONObject origin=new JSONObject(start.getString(0));location=origin.optString("location");appointment=origin.optString("appointment_id");billingCustomer=origin.optString("billing_customer_id",c.getString(2));}}
            org.json.JSONArray pauseEvents=new org.json.JSONArray();try(Cursor events=db.rawQuery("SELECT event_type,occurred_at FROM work_session_events WHERE session_id=? ORDER BY rowid ASC",new String[]{id})){while(events.moveToNext())pauseEvents.put(new JSONObject().put("event_type",events.getString(0)).put("occurred_at",events.getString(1)));}JSONObject pauses=WorkSessionPauseRules.calculate(pauseEvents,state,clock.wallMillis());long pauseMs=pauses.getLong("pause_ms");boolean pauseUnverified=pauses.getBoolean("pause_unverified");
            JSONObject result=new JSONObject().put("id",c.getString(0)).put("state",state).put("customer_id",c.getString(2)).put("order_id",c.getString(3)).put("work_date",c.getString(4)).put("activity",c.getString(5)).put("location",location).put("billing_customer_id",billingCustomer).put("started_at_ms",c.getLong(6)).put("worked_ms",worked).put("hours",Math.round(worked/36000.0)/100.0).put("break_minutes",Math.round(pauseMs/60000.0)).put("pause_unverified",pauseUnverified).put("pause_measurement","event_wall_clock_rounded_minutes").put("finalized_worklog_id",c.getString(11)).put("updated_at",c.getString(12));if(!appointment.isEmpty())result.put("appointment_id",appointment);return result;
        }
    }
    private JSONObject transition(String id,String request,String expected,String next,String event,boolean accrue)throws Exception{
        db.beginTransaction();try{
        String replay=sessionForRequest(request);if(!replay.isEmpty()){if(!replay.equals(id))throw new IllegalArgumentException("Diese Anfragekennung gehört zu einer anderen Sitzung.");return current(id);}
        JSONObject before=current(id);if(!expected.equals(before.getString("state")))throw new IllegalStateException("Aktion passt nicht zum aktuellen Sitzungszustand.");
        long wall=clock.wallMillis(), elapsed=clock.elapsedMillis(), worked=before.getLong("worked_ms");
        if(accrue)worked=storedWorked(id)+runningDeltaFor(id);
        ContentValues values=new ContentValues();values.put("state",next);values.put("worked_ms",worked);values.put("updated_at",Instant.ofEpochMilli(wall).toString());
        if(RUNNING.equals(next)){values.put("segment_started_wall_ms",wall);values.put("segment_started_elapsed_ms",elapsed);values.put("boot_marker",clock.bootMarker());}
        else{values.putNull("segment_started_wall_ms");values.putNull("segment_started_elapsed_ms");}
        transaction(()->{db.update("work_sessions",values,"id=?",new String[]{id});event(id,event,request,new JSONObject());});db.setTransactionSuccessful();return current(id);
        }finally{db.endTransaction();}
    }
    private long storedWorked(String id){try(Cursor c=db.rawQuery("SELECT worked_ms FROM work_sessions WHERE id=?",new String[]{id})){if(c.moveToFirst())return c.getLong(0);}throw new IllegalArgumentException("Arbeitssitzung nicht gefunden.");}
    private long runningDeltaFor(String id){try(Cursor c=db.rawQuery("SELECT segment_started_wall_ms,segment_started_elapsed_ms,boot_marker FROM work_sessions WHERE id=?",new String[]{id})){if(c.moveToFirst())return runningDelta(c.getLong(0),c.getLong(1),c.getString(2));}throw new IllegalArgumentException("Arbeitssitzung nicht gefunden.");}
    private long runningDelta(long wall,long elapsed,String boot){long value=clock.bootMarker().equals(boot)?clock.elapsedMillis()-elapsed:clock.wallMillis()-wall;return Math.max(0,value);}
    private String activeId(){try(Cursor c=db.rawQuery("SELECT id FROM work_sessions WHERE state IN (?,?,?) ORDER BY updated_at DESC LIMIT 1",new String[]{RUNNING,PAUSED,STOPPED})){return c.moveToFirst()?c.getString(0):null;}}
    private String sessionForRequest(String request){try(Cursor c=db.rawQuery("SELECT session_id FROM work_session_events WHERE request_id=?",new String[]{request})){return c.moveToFirst()?c.getString(0):"";}}
    private void event(String session,String type,String request,JSONObject payload){if(request==null||request.trim().isEmpty())throw new IllegalArgumentException("Eine eindeutige Anfragekennung fehlt.");ContentValues e=new ContentValues();e.put("id","evt_"+UUID.randomUUID().toString().replace("-",""));e.put("session_id",session);e.put("event_type",type);e.put("request_id",request.trim());e.put("occurred_at",nowIso());e.put("payload",payload.toString());db.insertOrThrow("work_session_events",null,e);}
    private String required(JSONObject value,String name){String result=value.optString(name).trim();if(result.isEmpty())throw new IllegalArgumentException("Eine eindeutige Anfragekennung fehlt.");return result;}
    private String nowIso(){return Instant.ofEpochMilli(clock.wallMillis()).toString();}
    private interface Change{void run()throws Exception;}
    private void transaction(Change change)throws Exception{db.beginTransaction();try{change.run();db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
