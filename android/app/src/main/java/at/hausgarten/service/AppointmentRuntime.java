package at.hausgarten.service;

import android.content.*;
import android.app.*;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.*;

/** Private device adapters. Business state is always changed through the guarded journal. */
final class AppointmentRuntime {
    private static final Object LOCK=new Object();
    static JSONObject request(Context c,MainActivity.LocalStore store)throws Exception {
        boolean configured=calendarEnabled(c,store)||flag(c,store,"weather_enabled");
        if(configured)PlanningAccount.prefs(c).edit().putInt("calendar_failures",0).putInt("weather_failures",0).remove("calendar_retry_at").remove("weather_retry_at").apply();
        return new JSONObject().put("state",configured?"queued":"configuration_required").put("confirmed",false).put("integration",integration(c,store));
    }
    static JSONObject settings(MainActivity.LocalStore store)throws Exception {
        JSONObject s=new JSONObject(store.ownerValue("planning:settings","{}"));
        if(!s.has("workdays"))s.put("workdays",new JSONArray(new int[]{1,2,3,4,5}));
        if(!s.has("day_start"))s.put("day_start","08:00");if(!s.has("day_end"))s.put("day_end","17:00");
        if(!s.has("travel_minutes"))s.put("travel_minutes",0);return s;
    }
    static JSONObject publicSettings(Context c,MainActivity.LocalStore store)throws Exception {
        JSONObject s=settings(store);android.content.SharedPreferences p=PlanningAccount.prefs(c);for(String k:new String[]{"weather_enabled","weather_license_confirmed","notifications"})if(!s.has(k))s.put(k,p.getBoolean(k,false));if(!s.has("calendar_enabled"))s.put("calendar_enabled",PlanningAccount.connected(c));return s;
    }
    static boolean flag(Context c,MainActivity.LocalStore store,String key){try{return publicSettings(c,store).optBoolean(key);}catch(Exception unavailable){return false;}}
    static boolean calendarEnabled(Context c,MainActivity.LocalStore store){return PlanningAccount.connected(c)&&flag(c,store,"calendar_enabled");}
    static void saveSettings(Context c,MainActivity.LocalStore store,JSONObject s,String expected)throws Exception {
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            if(!PixelMcpWriteTools.hash(publicSettings(c,store)).equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Planungseinstellungen wurden inzwischen geändert. Angaben bleiben erhalten; neu prüfen.");
            saveSettings(store,s);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void saveSettings(MainActivity.LocalStore store,JSONObject s)throws Exception {
        LocalTime start=LocalTime.parse(s.getString("day_start")),end=LocalTime.parse(s.getString("day_end"));if(!end.isAfter(start))throw new IllegalArgumentException("Ende muss nach Beginn liegen.");
        JSONArray days=s.getJSONArray("workdays");Set<Integer> seen=new HashSet<>();for(int i=0;i<days.length();i++)if(days.getInt(i)<1||days.getInt(i)>7||!seen.add(days.getInt(i)))throw new IllegalArgumentException("Ungültige Arbeitstage.");if(days.length()==0)throw new IllegalArgumentException("Mindestens einen Arbeitstag wählen.");
        int travel=s.optInt("travel_minutes");if(travel<0||travel>720)throw new IllegalArgumentException("Anfahrtpuffer ungültig.");
        for(String key:new String[]{"rain_mm","rain_probability","gust_kmh","frost_c","heat_c"})if(s.has(key)&&(!Double.isFinite(s.getDouble(key))||s.getDouble(key)<(key.equals("frost_c")?-30:0)||s.getDouble(key)>200))throw new IllegalArgumentException("Wetterschwelle ungültig.");
        if(s.has("rain_probability")&&s.getDouble("rain_probability")>100)throw new IllegalArgumentException("Regenwahrscheinlichkeit muss zwischen0 und100 liegen.");
        store.putOwnerValue("planning:settings",s.toString());
    }
    static JSONObject integration(Context c,MainActivity.LocalStore store)throws Exception {
        android.content.SharedPreferences p=PlanningAccount.prefs(c);JSONObject status=new JSONObject(store.ownerValue("planning:status","{}"));
        return status.put("calendar_configured",calendarEnabled(c,store)).put("weather_configured",flag(c,store,"weather_enabled")&&p.contains("weather_secret")).put("calendar_state",store.ownerValue("calendar:status",calendarEnabled(c,store)?"pending":"not_connected")).put("weather_state",store.ownerValue("weather:status",flag(c,store,"weather_enabled")?"pending":"not_configured"))
            .put("notification_permission",Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED).put("background_best_effort",true).put("credentials_exposed",false);
    }
    static JSONObject risk(MainActivity.LocalStore store,JSONObject a,Instant now)throws Exception {
        JSONObject cache=null;if(a.has("latitude")&&!a.isNull("latitude"))cache=new JSONObject(store.ownerValue("weather:"+WeatherRiskRules.locationKey(a),"{}"));
        return WeatherRiskRules.evaluate(a,cache==null||cache.length()==0?null:cache,now,settings(store));
    }
    static JSONObject query(Context c,MainActivity.LocalStore store,String name,JSONObject args)throws Exception {
        if(name.equals("get_planning_settings")){McpSchema.validate(args,AppointmentTools.schema(name));JSONObject s=publicSettings(c,store);return new JSONObject().put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("integration",integration(c,store));}
        McpSchema.validate(args,AppointmentTools.schema(name));Instant now=Instant.now();if(name.equals("get_workweek_weather")||name.equals("get_weather_risks")){args=new JSONObject(args.toString());if(!args.has("date_from"))args.put("date_from",now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().toString());if(!args.has("date_to"))args.put("date_to",now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().plusDays(14).toString());}JSONObject state=store.getState(),master=PixelMcpReadTools.effective(state),out=AppointmentTools.query(name,args,state);
        if(name.equals("suggest_appointment_slots"))out=suggestions(store,master,args,now);
        else if(name.equals("get_appointment")){JSONObject a=out.getJSONObject("appointment");out.put("weather",risk(store,a,now)).put("calendar",calendarState(c,store,a));}
        else {
            JSONArray rows=out.getJSONArray("appointments"),risks=new JSONArray();for(int i=0;i<rows.length();i++){JSONObject entry=rows.getJSONObject(i),a=entry.getJSONObject("appointment"),r=risk(store,a,now);entry.put("weather",r).put("calendar",calendarState(c,store,a));if(!r.optString("severity").equals("clear")&&!r.optString("severity").equals("inactive"))risks.put(new JSONObject().put("appointment_id",a.getString("id")).put("weather",r).put("acknowledged",r.optString("alert_hash").equals(store.ownerValue("weather:ack:"+a.getString("id"),""))));}
            out.put("weather_risks",risks);
            if(name.equals("get_workweek_weather")||name.equals("get_weather_risks"))out.put("forecast_days",14).put("next_workdays",nextWorkdays(store,now)).put("source","Open-Meteo / GeoSphere Austria");
        }
        return out.put("integration",integration(c,store)).put("planning_settings",settings(store));
    }
    static String bindingKey(Context c,String id)throws Exception{return "calendar:binding:"+PixelMcpWriteTools.hash(PlanningAccount.prefs(c).getString("calendar_id","")).substring(0,24)+":"+id;}
    static JSONObject calendarState(Context c,MainActivity.LocalStore store,JSONObject a)throws Exception {
        JSONObject b=new JSONObject(store.ownerValue(bindingKey(c,a.getString("id")),"{}"));boolean matching=b.has("base")&&AppointmentRules.hash(a).equals(AppointmentRules.hash(b.getJSONObject("base")));
        return new JSONObject().put("state",!calendarEnabled(c,store)?"not_connected":b.has("conflict")?"review_required":b.optBoolean("detached")?"detached":matching?b.optString("state","pending"):"pending").put("last_confirmed_at",b.optString("confirmed_at")).put("error",b.optString("error")).put("conflict_hash",b.has("conflict")?PixelMcpWriteTools.hash(b):"").put("conflict",b.opt("conflict")==null?JSONObject.NULL:b.get("conflict"));
    }
    static JSONArray nextWorkdays(MainActivity.LocalStore store,Instant now)throws Exception {Set<Integer> days=new HashSet<>();JSONArray configured=settings(store).getJSONArray("workdays");for(int i=0;i<configured.length();i++)days.add(configured.getInt(i));JSONArray out=new JSONArray();LocalDate day=now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate();for(int i=0;i<16&&out.length()<5;i++,day=day.plusDays(1))if(days.contains(day.getDayOfWeek().getValue()))out.put(day.toString());return out;}
    static JSONObject suggestions(MainActivity.LocalStore store,JSONObject s,JSONObject a,Instant now)throws Exception {
        PixelMcpWriteTools.find(s.getJSONArray("customers"),a.getString("customer_id"));LocalDate from=LocalDate.parse(a.getString("date_from")),to=LocalDate.parse(a.getString("date_to"));if(to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>30)throw new IllegalArgumentException("Bitte einen Zeitraum bis31Tage wählen.");
        JSONObject preferences=settings(store),busy=new JSONObject(store.ownerValue("calendar:busy","{}"));boolean verified=false;
        try{verified=busy.optBoolean("verified")&&Instant.parse(busy.getString("checked_at")).isAfter(now.minusSeconds(1800))&&AppointmentRules.instant(busy.getString("from")).isBefore(from.atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant().plusSeconds(1))&&!AppointmentRules.instant(busy.getString("to")).isBefore(to.plusDays(1).atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant());}catch(Exception ignored){}
        List<JSONObject> found=new ArrayList<>();JSONArray workdays=preferences.getJSONArray("workdays");Set<Integer> days=new HashSet<>();for(int i=0;i<workdays.length();i++)days.add(workdays.getInt(i));
        JSONObject prior=a.optString("order_id").isEmpty()?null:PixelMcpWriteTools.find(s.getJSONArray("orders"),a.getString("order_id"));if(prior!=null&&!a.getString("customer_id").equals(prior.getString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
        // Reuse only confirmed coordinates from appointments of this exact customer and location.
        JSONObject location=null;JSONArray appts=s.getJSONArray("appointments");for(int i=0;i<appts.length();i++){JSONObject row=appts.getJSONObject(i);if(a.getString("customer_id").equals(row.optString("customer_id"))&&row.has("latitude")&&!row.isNull("latitude")&&(prior==null||prior.optString("location").equals(row.optString("location"))))location=row;}
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1))if(days.contains(day.getDayOfWeek().getValue())){
            ZonedDateTime first=day.atTime(LocalTime.parse(preferences.getString("day_start"))).atZone(ZoneId.of("Europe/Vienna")),end=day.atTime(LocalTime.parse(preferences.getString("day_end"))).atZone(ZoneId.of("Europe/Vienna"));
            for(ZonedDateTime time=first;!time.plusMinutes(a.getInt("duration_minutes")).isAfter(end);time=time.plusMinutes(30)){
                if(time.toInstant().isBefore(now))continue;JSONObject row=new JSONObject().put("id","suggestion").put("customer_id",a.getString("customer_id")).put("order_id",a.optString("order_id")).put("title",prior==null?"Geplanter Einsatz":prior.optString("title")).put("starts_at",time.toInstant().toString()).put("ends_at",time.plusMinutes(a.getInt("duration_minutes")).toInstant().toString()).put("timezone","Europe/Vienna").put("status","tentative").put("weather_profile",a.optString("weather_profile","general")).put("travel_minutes",preferences.optInt("travel_minutes"));
                if(location!=null)row.put("latitude",location.get("latitude")).put("longitude",location.get("longitude")).put("location",location.optString("location"));
                try{AppointmentRules.validate(row,null,s);}catch(Exception blocked){continue;}
                boolean clash=false;JSONArray periods=busy.optJSONArray("busy");for(int j=0;verified&&periods!=null&&j<periods.length();j++){JSONObject b=periods.getJSONObject(j);if(AppointmentRules.instant(b.getString("start")).isBefore(time.plusMinutes(a.getInt("duration_minutes")).toInstant())&&AppointmentRules.instant(b.getString("end")).isAfter(time.minusMinutes(preferences.optInt("travel_minutes")).toInstant()))clash=true;}
                if(!clash){JSONObject r=risk(store,row,now);found.add(new JSONObject().put("appointment",row).put("weather",r).put("calendar_availability_verified",verified).put("customer_availability_confirmed",false));}
            }
        }
        found.sort(Comparator.comparingInt((JSONObject x)->Arrays.asList("clear","difficult","unknown","critical").indexOf(x.optJSONObject("weather").optString("severity"))).thenComparing(x->x.optJSONObject("appointment").optString("starts_at")));
        return new JSONObject().put("suggestions",new JSONArray(found.subList(0,Math.min(5,found.size())))).put("booked",false).put("calendar_availability_verified",verified).put("notice",verified?"Kundenverfügbarkeit separat bestätigen. Andere Kalender können sich nach der Prüfung ändern.":"Nur App-Belegung geprüft. Externe Belegtzeiten fehlen oder sind veraltet; keine Buchung ausgeführt.");
    }
    static JSONObject acknowledge(Context c,MainActivity.LocalStore store,JSONObject a)throws Exception {JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),a.getString("appointment_id")),r=risk(store,row,Instant.now());if(!r.optString("alert_hash").equals(a.getString("expected_alert_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Wetterhinweis wurde geändert. Bitte neu prüfen.");store.putOwnerValue("weather:ack:"+row.getString("id"),r.getString("alert_hash"));return new JSONObject().put("acknowledged",true).put("appointment_changed",false);}

    static boolean checkBusy(MainActivity.LocalStore store,JSONObject row)throws Exception {
        if(!AppointmentRules.active(row)||row.optString("status").equals("draft"))return false;
        JSONObject cache=new JSONObject(store.ownerValue("calendar:busy","{}"));Instant now=Instant.now(),start=AppointmentRules.instant(row.getString("starts_at")).minusSeconds(row.optInt("travel_minutes")*60L),end=AppointmentRules.instant(row.getString("ends_at"));
        boolean current=false;try{current=cache.optBoolean("verified")&&Instant.parse(cache.getString("checked_at")).isAfter(now.minusSeconds(1800))&&!AppointmentRules.instant(cache.getString("from")).isAfter(start)&&!AppointmentRules.instant(cache.getString("to")).isBefore(end);}catch(Exception ignored){}
        JSONArray periods=cache.optJSONArray("busy");for(int i=0;current&&periods!=null&&i<periods.length();i++){JSONObject p=periods.getJSONObject(i);if(AppointmentRules.instant(p.getString("start")).isBefore(end)&&AppointmentRules.instant(p.getString("end")).isAfter(start))throw new PixelMcpWriteTools.Rejected("CALENDAR_BUSY","Zeit oder Anfahrt überschneidet sich mit aktuell geprüften Google-Belegtzeiten.");}return current;
    }
    static JSONObject resolve(Context c,MainActivity.LocalStore store,JSONObject a)throws Exception {
        JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),a.getString("appointment_id"));if(!AppointmentRules.hash(row).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","App-Termin wurde geändert.");
        String key=bindingKey(c,row.getString("id"));JSONObject b=new JSONObject(store.ownerValue(key,"{}"));if(!b.has("conflict")||!PixelMcpWriteTools.hash(b).equals(a.getString("expected_conflict_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Kalendervergleich wurde geändert. Neu prüfen.");
        if(!a.getBoolean("confirmed"))throw new IllegalArgumentException("Entscheidung ausdrücklich bestätigen.");String choice=a.getString("resolution");
        if(choice.equals("keep_without_calendar")){b.put("state","detached").put("detached",true);b.remove("conflict");b.remove("decision");}
        else b.put("decision",new JSONObject().put("resolution",choice).put("local_hash",AppointmentRules.hash(row)).put("remote_hash",PixelMcpWriteTools.hash(b.getJSONObject("conflict").isNull("remote")?JSONObject.NULL:b.getJSONObject("conflict").get("remote"))));
        store.putOwnerValue(key,b.toString());return new JSONObject().put("state",choice.equals("keep_without_calendar")?"detached":"queued").put("business_data_changed",false).put("google_confirmed",false);
    }

    interface AdapterTask {void run()throws Exception;}
    static void refresh(Context c,MainActivity.LocalStore store){synchronized(LOCK){
        Instant now=Instant.now();long calendarNext=adapter(c,store,"calendar",calendarEnabled(c,store),now,()->syncCalendar(c,store,now));
        long weatherNext=adapter(c,store,"weather",flag(c,store,"weather_enabled"),now,()->refreshWeather(c,store,now));
        try{notifyRisks(c,store,now);store.putOwnerValue("planning:status",new JSONObject().put("last_check_at",now.toString()).put("background_best_effort",true).toString());}catch(Exception ignored){}
        long next=Math.min(calendarNext,weatherNext);if(next<Long.MAX_VALUE)PlanningJob.schedule(c,next);
    }}
    private static long adapter(Context c,MainActivity.LocalStore store,String kind,boolean enabled,Instant now,AdapterTask task){
        android.content.SharedPreferences p=PlanningAccount.prefs(c);String prefix=kind+"_";int failures=p.getInt(prefix+"failures",0);if(!enabled||p.getBoolean(prefix+"blocked",false)||failures>=8)return Long.MAX_VALUE;
        try{String retry=p.getString(prefix+"retry_at","");if(!retry.isEmpty()){long remaining=Duration.between(now,Instant.parse(retry)).toMillis();if(remaining>0)return remaining;}}catch(Exception ignored){}
        try{task.run();p.edit().putInt(prefix+"failures",0).remove(prefix+"retry_at").apply();store.putOwnerValue(kind+":status","checked");return Long.MAX_VALUE;}
        catch(Exception e){boolean auth=e instanceof DriveRestClient.Failure&&((DriveRestClient.Failure)e).userAction;
            if(auth){p.edit().putBoolean(prefix+"blocked",true).apply();store.putOwnerValue(kind+":status","authorization_required");return Long.MAX_VALUE;}
            if(e instanceof PixelMcpWriteTools.Rejected){store.putOwnerValue(kind+":status","review_required");return Long.MAX_VALUE;}
            long delay=Math.min(3600000,30000L*(1L<<failures))+new Random().nextInt(3000);if(e instanceof DriveRestClient.Failure)delay=Math.max(delay,((DriveRestClient.Failure)e).retryAfter);
            p.edit().putInt(prefix+"failures",failures+1).putString(prefix+"retry_at",now.plusMillis(delay).toString()).apply();store.putOwnerValue(kind+":status",failures>=7?"retry_limit_reached":"retry_pending");return failures>=7?Long.MAX_VALUE:delay;
        }
    }
    static void syncCalendar(Context c,MainActivity.LocalStore store,Instant now)throws Exception {
        syncCalendar(c,store,now,PlanningAccount.client(c),PlanningAccount.prefs(c).getString("calendar_id",""));
    }
    static void syncCalendar(Context c,MainActivity.LocalStore store,Instant now,CalendarClient client,String calendar)throws Exception {
        JSONObject changes=client.changes(calendar,store.ownerValue("calendar:token",""));
        if(changes.optBoolean("reset_required")){store.putOwnerValue("calendar:token","");changes=client.changes(calendar,"");}
        if(changes.optBoolean("reset_required"))throw new java.io.IOException("Kalenderabgleich benötigt erneutes Lesen.");
        JSONObject pendingRemote=new JSONObject(store.ownerValue("calendar:dirty","{}"));JSONArray changed=changes.getJSONArray("events");
        for(int i=0;i<changed.length();i++){JSONObject event=changed.getJSONObject(i),properties=event.optJSONObject("extendedProperties"),privateData=properties==null?null:properties.optJSONObject("private");String appointment=privateData==null?"":privateData.optString("hg_appointment_id");if(!appointment.isEmpty()){JSONObject bound=new JSONObject(store.ownerValue(bindingKey(c,appointment),"{}"));if(!bound.has("conflict")&&!bound.optString("etag").isEmpty()&&bound.optString("etag").equals(event.optString("etag")))continue;}pendingRemote.put(event.getString("id"),true);}
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{store.putOwnerValue("calendar:dirty",pendingRemote.toString());store.putOwnerValue("calendar:token",changes.getString("next_sync_token"));db.setTransactionSuccessful();}finally{db.endTransaction();}
        JSONObject snapshot=PixelMcpReadTools.effective(store.getState());JSONArray all=snapshot.getJSONArray("appointments"),index=new JSONArray(store.ownerValue("calendar:index","[]"));Set<String> ids=new LinkedHashSet<>();for(int i=0;i<all.length();i++)ids.add(all.getJSONObject(i).getString("id"));for(int i=0;i<index.length();i++)ids.add(index.getString(i));
        Map<String,JSONObject> indexed=new HashMap<>();for(int i=0;i<all.length();i++)indexed.put(all.getJSONObject(i).getString("id"),all.getJSONObject(i));
        int network=0;boolean more=false;long deadline=android.os.SystemClock.elapsedRealtime()+55000;
        for(String id:ids){
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();String eventId=CalendarRules.eventId(id),bindingKey=bindingKey(c,id);
            JSONObject binding=new JSONObject(store.ownerValue(bindingKey,"{}")),current=indexed.get(id);
            if(binding.optBoolean("detached"))continue;
            if(current==null){JSONObject entry=OwnerJournalRules.archives(snapshot).optJSONObject("appointment:"+id);if(entry==null||!binding.has("base"))continue;current=new JSONObject(binding.getJSONObject("base").toString()).put("status","cancelled");}
            if(current.optString("status").equals("draft"))continue;
            boolean unchanged=binding.has("base")&&AppointmentRules.hash(current).equals(AppointmentRules.hash(binding.getJSONObject("base")));
            if(unchanged&&!pendingRemote.has(eventId)&&!binding.has("decision"))continue;
            if(binding.has("conflict")&&!binding.has("decision")&&!pendingRemote.has(eventId))continue;
            if(network>=12||android.os.SystemClock.elapsedRealtime()>deadline){more=true;break;}network++;
            JSONObject latestBeforeNetwork=PixelMcpReadTools.effective(store.getState()),latestAppointment=OwnerJournalRules.findOrNull(latestBeforeNetwork.getJSONArray("appointments"),id);if(latestAppointment!=null)current=latestAppointment;else if(!OwnerJournalRules.archives(latestBeforeNetwork).has("appointment:"+id))continue;
            JSONObject remote=client.get(calendar,eventId);
            try{
                JSONObject decision=binding.optJSONObject("decision");boolean force=false;
                if(decision!=null){
                    if(!decision.getString("local_hash").equals(AppointmentRules.hash(current))||!decision.getString("remote_hash").equals(PixelMcpWriteTools.hash(remote==null?JSONObject.NULL:remote)))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Vergleich nach Entscheidung geändert. Neu prüfen; nichts überschrieben.");
                    if(decision.getString("resolution").equals("use_google")){
                        if(remote==null||remote.optString("status").equals("cancelled"))throw new PixelMcpWriteTools.Rejected("CALENDAR_REMOVED","Google-Eintrag fehlt. Termin behalten oder ausdrücklich in der App absagen.");
                        JSONObject chosen=CalendarRules.changes(current,current,remote);current=importCalendar(c,store,current,chosen,remote);binding.put("base",current).put("etag",remote.getString("etag"));
                    }else force=true;
                }
                if(current.optString("status").equals("cancelled")){
                    if(remote!=null&&!remote.optString("status").equals("cancelled")){CalendarRules.requireIdentity(remote,id);client.remove(calendar,eventId,remote);}
                    binding=new JSONObject().put("base",current).put("state","cancelled").put("confirmed_at",now.toString());
                }else{
                    if(!force&&binding.has("base")&&(remote==null||remote.optString("status").equals("cancelled")))throw new PixelMcpWriteTools.Rejected("CALENDAR_REMOVED","Google-Eintrag entfernt. App-Termin prüfen.");
                    if(remote!=null){
                        CalendarRules.requireIdentity(remote,id);JSONObject base=binding.optJSONObject("base");
                        if(base==null){JSONObject wanted=CalendarRules.changes(current,current,remote);if(!CalendarRules.projectionHash(wanted).equals(CalendarRules.projectionHash(current)))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Bereits vorhandener Google-Eintrag weicht ab. Beide Stände prüfen.");}
                        else if(!force&&!remote.optString("etag").equals(binding.optString("etag")))current=importCalendar(c,store,current,CalendarRules.changes(current,base,remote),remote);
                    }
                    // A readback import must itself pass the current SQLite version/overlap guard.
                    JSONObject latest=OwnerJournalRules.findOrNull(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),id);
                    if(latest==null||!AppointmentRules.hash(latest).equals(AppointmentRules.hash(current)))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","App-Termin während Kalenderprüfung geändert.");
                    boolean localChanged=force||!binding.has("base")||!CalendarRules.projectionHash(current).equals(CalendarRules.projectionHash(binding.getJSONObject("base")));
                    if(remote==null||localChanged)remote=client.save(calendar,current,remote);
                    CalendarRules.requireIdentity(remote,id);JSONObject readback=CalendarRules.changes(current,current,remote);
                    if(!CalendarRules.projectionHash(current).equals(CalendarRules.projectionHash(readback)))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Google-Rücklesen stimmt nicht mit dem Termin überein.");
                    binding=new JSONObject().put("base",current).put("etag",remote.getString("etag")).put("state","confirmed").put("confirmed_at",now.toString());
                }
            }catch(PixelMcpWriteTools.Rejected e){binding.remove("decision");binding.put("state","review_required").put("error",e.code).put("conflict",new JSONObject().put("local",current).put("remote",remote==null?JSONObject.NULL:remote));}
            store.putOwnerValue(bindingKey,binding.toString());pendingRemote.remove(eventId);store.putOwnerValue("calendar:dirty",pendingRemote.toString());
            if(!contains(index,id)){index.put(id);store.putOwnerValue("calendar:index",index.toString());}
        }
        if(more)PlanningJob.schedule(c,3000);
        // Foreign events in the app calendar never create customers or orders.
        Set<String> ownEvents=new HashSet<>();for(String id:ids)ownEvents.add(CalendarRules.eventId(id));for(Iterator<String> it=pendingRemote.keys();it.hasNext();){String id=it.next();if(!ownEvents.contains(id))it.remove();}store.putOwnerValue("calendar:dirty",pendingRemote.toString());
        if(PlanningAccount.prefs(c).getBoolean("read_busy",false)){
            Instant from=now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant();
            store.putOwnerValue("calendar:busy",client.busy(new JSONArray().put("primary"),from.toString(),from.plusSeconds(16*86400L).toString()).toString());
        }
    }
    private static JSONObject importCalendar(Context c,MainActivity.LocalStore store,JSONObject current,JSONObject merged,JSONObject remote)throws Exception {
        JSONObject updates=new JSONObject();for(String field:CalendarRules.SHARED)if(!field.equals("status")&&!merged.optString(field).equals(current.optString(field)))updates.put(field,merged.get(field));
        if(updates.length()>0){new PixelOwnerAccess(c,store).execute("update_appointment",new JSONObject().put("appointment_id",current.getString("id")).put("expected_record_hash",AppointmentRules.hash(current)).put("updates",updates).put("request_id","calendar-import-"+PixelMcpWriteTools.hash(current.getString("id")+remote.optString("etag"))));return PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),current.getString("id"));}
        return current;
    }
    private static boolean contains(JSONArray list,String id){for(int i=0;i<list.length();i++)if(id.equals(list.optString(i)))return true;return false;}
    static void refreshWeather(Context c,MainActivity.LocalStore store,Instant now)throws Exception {
        if(!flag(c,store,"weather_license_confirmed"))throw new IllegalArgumentException("Gewerbliche Wetterfreigabe fehlt.");String key=PlanningAccount.weatherKey(c);if(key.isEmpty())throw new IllegalArgumentException("Wetterzugang fehlt.");JSONArray all=PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments");Map<String,JSONObject> sites=new LinkedHashMap<>();
        for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);if(!AppointmentRules.active(a)||!a.has("latitude")||a.isNull("latitude"))continue;Instant start=AppointmentRules.instant(a.getString("starts_at"));if(start.isBefore(now.minusSeconds(86400))||start.isAfter(now.plusSeconds(14*86400)))continue;sites.put(WeatherRiskRules.locationKey(a),a);}
        for(Map.Entry<String,JSONObject> site:sites.entrySet()){
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();String loc=site.getKey();JSONObject cache=new JSONObject(store.ownerValue("weather:"+loc,"{}"));
            long interval=12*3600;for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);if(a.has("latitude")&&loc.equals(WeatherRiskRules.locationKey(a))){long delta=AppointmentRules.instant(a.optString("starts_at")).getEpochSecond()-now.getEpochSecond();if(delta>=0&&delta<86400)interval=Math.min(interval,3600);else if(delta>=0&&delta<2*86400)interval=Math.min(interval,3*3600);}}
            try{if(Instant.parse(cache.getString("fetched_at")).isAfter(now.minusSeconds(interval)))continue;}catch(Exception ignored){}
            String[] coords=loc.split(",");String url="https://customer-api.open-meteo.com/v1/forecast?latitude="+coords[0]+"&longitude="+coords[1]+"&forecast_days=14&timeformat=unixtime&timezone=UTC&wind_speed_unit=kmh&temperature_unit=celsius&precipitation_unit=mm&hourly=temperature_2m,precipitation,precipitation_probability,wind_gusts_10m,weather_code&apikey="+CalendarClient.enc(key);
            DriveRestClient.Reply reply=new PlanningWire().send("GET",url,Collections.emptyMap(),null,1024*1024);if(reply.status==401||reply.status==403)throw new DriveRestClient.Failure("WEATHER_AUTH_REQUIRED","Wetterzugang am Gerät prüfen.",0,true);if(reply.status==429){long wait=30000;try{wait=Math.min(3600000,Math.max(wait,Long.parseLong(reply.header("Retry-After"))*1000));}catch(Exception ignored){}throw new DriveRestClient.Failure("WEATHER_RETRY","Wetterdienst vorübergehend begrenzt.",wait,false);}if(reply.status!=200)throw new java.io.IOException("Wetterdienst nicht bestätigt. Zugang oder Netz prüfen.");JSONObject forecast=reply.json();if(forecast.optJSONObject("hourly")==null)throw new java.io.IOException("Unvollständige Wetterantwort.");
            JSONObject cleanHourly=new JSONObject();for(String field:new String[]{"time","temperature_2m","precipitation","precipitation_probability","wind_gusts_10m","weather_code"})if(forecast.getJSONObject("hourly").has(field))cleanHourly.put(field,forecast.getJSONObject("hourly").get(field));cache.put("forecast",new JSONObject().put("hourly",cleanHourly)).put("fetched_at",now.toString());
            try{DriveRestClient.Reply warnings=new PlanningWire().send("GET","https://warnungen.zamg.at/wsapp/api/getWarningsForCoords?lat="+coords[0]+"&lon="+coords[1]+"&lang=de",Collections.emptyMap(),null,512*1024);if(warnings.status!=200)throw new java.io.IOException();cache.put("warnings",WeatherRiskRules.parseWarnings(warnings.json())).put("warnings_fetched_at",now.toString());}catch(Exception unavailable){cache.put("warning_status","Nicht aktuell geprüft.");}
            store.putOwnerValue("weather:"+loc,cache.toString());
        }
    }
    static void notifyRisks(Context c,MainActivity.LocalStore store,Instant now)throws Exception {
        android.content.SharedPreferences p=PlanningAccount.prefs(c);if(!flag(c,store,"notifications"))return;NotificationManager manager=c.getSystemService(NotificationManager.class);if(manager==null)return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        manager.createNotificationChannel(new NotificationChannel("planning_weather","Einsätze & Wetter",NotificationManager.IMPORTANCE_DEFAULT));
        JSONArray all=PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments");List<String> alerts=new ArrayList<>();List<String> ids=new ArrayList<>();
        int hour=now.atZone(ZoneId.of("Europe/Vienna")).getHour();if(hour>=21||hour<6)return;
        for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);Instant end=AppointmentRules.instant(a.getString("ends_at"));if(end.isBefore(now)||!AppointmentRules.active(a))continue;JSONObject r=risk(store,a,now);String severity=r.optString("severity"),hash=r.optString("alert_hash"),id=a.getString("id");
            if(severity.equals("clear")||severity.equals("inactive")){store.putOwnerValue("weather:notified:"+id,"");store.putOwnerValue("weather:ack:"+id,"");continue;}
            if(!severity.equals("critical")&&!severity.equals("difficult"))continue;
            if(hash.equals(store.ownerValue("weather:ack:"+id,""))||hash.equals(store.ownerValue("weather:notified:"+id,"")))continue;
            alerts.add(hash);ids.add(id);
        }
        if(alerts.isEmpty())return;Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_planning",true);PendingIntent click=PendingIntent.getActivity(c,0x484713,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,"planning_weather").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Geplante Arbeit: Wetter prüfen").setContentText(alerts.size()+" Einsatz/Einsätze mit Wetterhinweis. Alternativen in der Planung ansehen.").setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(click).setAutoCancel(true);
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_view),"Termine ansehen",click).build());
        if(ids.size()==1){
            Intent alternative=new Intent(open).putExtra("planning_appointment_id",ids.get(0)).putExtra("planning_action","alternatives");PendingIntent choose=PendingIntent.getActivity(c,0x484715,alternative,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_today),"Alternative suchen",choose).build());
            Intent ack=new Intent(c,PlanningAlertReceiver.class).setData(android.net.Uri.parse("hausgarten-internal://weather-ack/"+android.net.Uri.encode(ids.get(0))+"/"+alerts.get(0))).putExtra("appointment_id",ids.get(0)).putExtra("alert_hash",alerts.get(0));PendingIntent acknowledge=PendingIntent.getBroadcast(c,0x484716,ack,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_agenda),"Geprüft",acknowledge).build());
        }
        manager.notify(0x484714,builder.build());
        for(int i=0;i<ids.size();i++)store.putOwnerValue("weather:notified:"+ids.get(i),alerts.get(i));
    }
    private AppointmentRuntime(){}
}
