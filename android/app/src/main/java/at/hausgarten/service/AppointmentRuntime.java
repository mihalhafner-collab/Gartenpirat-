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
    static JSONObject request(Context c,PixelLocalStore store)throws Exception {
        boolean configured=calendarEnabled(c,store)||flag(c,store,"weather_enabled");
        if(configured){PlanningAccount.prefs(c).edit().putInt("calendar_failures",0).putInt("weather_failures",0).putBoolean("weather_blocked",false).remove("calendar_retry_at").remove("weather_retry_at").apply();
            JSONObject master=PixelMcpReadTools.effective(store.getState());JSONArray all=master.getJSONArray("appointments");for(int i=0;i<all.length();i++){JSONObject a=weatherLocation(store,all.getJSONObject(i),master);if(flag(c,store,"weather_enabled")&&WeatherLocationRules.retryable(a.optString("weather_location_state"))){String key=WeatherLocationRules.cacheKey(a);JSONObject cached=new JSONObject(store.ownerValue(key,"{}"));cached.remove("attempted_at");cached.put("state","pending");store.putOwnerValue(key,cached.toString());}}
        }
        return new JSONObject().put("state",configured?"queued":"configuration_required").put("confirmed",false).put("integration",integration(c,store));
    }
    static JSONObject settings(PixelLocalStore store)throws Exception {
        JSONObject s=new JSONObject(store.ownerValue("planning:settings","{}"));
        if(!s.has("workdays"))s.put("workdays",new JSONArray(new int[]{1,2,3,4,5}));
        if(!s.has("day_start"))s.put("day_start","08:00");if(!s.has("day_end"))s.put("day_end","17:00");
        if(!s.has("travel_minutes"))s.put("travel_minutes",0);return s;
    }
    static JSONObject publicSettings(Context c,PixelLocalStore store)throws Exception {
        JSONObject s=settings(store);android.content.SharedPreferences p=PlanningAccount.prefs(c);for(String k:new String[]{"weather_enabled","weather_license_confirmed","notifications"})if(!s.has(k))s.put(k,p.getBoolean(k,false));if(!s.has("calendar_enabled"))s.put("calendar_enabled",PlanningAccount.connected(c));return s;
    }
    static boolean flag(Context c,PixelLocalStore store,String key){try{return publicSettings(c,store).optBoolean(key);}catch(Exception unavailable){return false;}}
    static boolean calendarEnabled(Context c,PixelLocalStore store){return PlanningAccount.connected(c)&&flag(c,store,"calendar_enabled");}
    static void setCalendarEnabled(Context c,PixelLocalStore store,boolean enabled,String expected)throws Exception {
        if(enabled&&!PlanningAccount.connected(c))throw new IllegalArgumentException("Google Kalender zuerst am Gerät verbinden.");
        JSONObject s=settings(store);s.put("calendar_enabled",enabled);saveSettings(c,store,s,expected);
        if(enabled)PlanningAccount.prefs(c).edit().putBoolean("calendar_blocked",false).putInt("calendar_failures",0).remove("calendar_retry_at").apply();
        PlanningJob.schedule(c,1000);
    }
    static void saveSettings(Context c,PixelLocalStore store,JSONObject s,String expected)throws Exception {
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            if(!PixelMcpWriteTools.hash(publicSettings(c,store)).equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Planungseinstellungen wurden inzwischen geändert. Angaben bleiben erhalten; neu prüfen.");
            saveSettings(store,s);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void saveSettings(PixelLocalStore store,JSONObject s)throws Exception {
        LocalTime start=LocalTime.parse(s.getString("day_start")),end=LocalTime.parse(s.getString("day_end"));if(!end.isAfter(start))throw new IllegalArgumentException("Ende muss nach Beginn liegen.");
        JSONArray days=s.getJSONArray("workdays");Set<Integer> seen=new HashSet<>();for(int i=0;i<days.length();i++)if(days.getInt(i)<1||days.getInt(i)>7||!seen.add(days.getInt(i)))throw new IllegalArgumentException("Ungültige Arbeitstage.");if(days.length()==0)throw new IllegalArgumentException("Mindestens einen Arbeitstag wählen.");
        int travel=s.optInt("travel_minutes");if(travel<0||travel>720)throw new IllegalArgumentException("Anfahrtpuffer ungültig.");
        for(String key:new String[]{"rain_mm","rain_probability","gust_kmh","frost_c","heat_c"})if(s.has(key)&&(!Double.isFinite(s.getDouble(key))||s.getDouble(key)<(key.equals("frost_c")?-30:0)||s.getDouble(key)>200))throw new IllegalArgumentException("Wetterschwelle ungültig.");
        if(s.has("rain_probability")&&s.getDouble("rain_probability")>100)throw new IllegalArgumentException("Regenwahrscheinlichkeit muss zwischen0 und100 liegen.");
        store.putOwnerValue("planning:settings",s.toString());
    }
    static JSONObject integration(Context c,PixelLocalStore store)throws Exception {
        android.content.SharedPreferences p=PlanningAccount.prefs(c);JSONObject status=new JSONObject(store.ownerValue("planning:status","{}"));
        boolean busyEnabled=p.getBoolean("read_busy",false);JSONObject busy=availability(c,store,Instant.now());
        String busyState=!PlanningAccount.connected(c)?"not_connected":!calendarEnabled(c,store)?"paused":!busyEnabled?"disabled":busy.optBoolean("verified")?"verified":"unverified";
        status.put("calendar_busy_enabled",busyEnabled).put("calendar_busy_state",busyState).put("calendar_busy_scope","primary")
            .put("calendar_busy_checked_at",busy.optString("checked_at")).put("calendar_busy_from",busy.optString("from")).put("calendar_busy_to",busy.optString("to"));
        status.put("calendar_last_success_at",store.ownerValue("calendar:last_success_at","")).put("calendar_last_attempt_at",store.ownerValue("calendar:last_attempt_at","")).put("calendar_next_retry_at",p.getString("calendar_retry_at","")).put("calendar_setup_attempt_at",p.getString("calendar_setup_attempt_at","")).put("calendar_setup_failed_at",p.getString("calendar_setup_failed_at",""));
        String setup=p.getString("calendar_setup_code","");status.put("calendar_setup_stage",setup.isEmpty()?"":CalendarAccessRules.phase(p.getString("calendar_setup_stage","grant")));if(p.contains("calendar_setup_result_code"))status.put("calendar_setup_result_code",p.getInt("calendar_setup_result_code",0));
        return status.put("calendar_setup_error_code",CalendarAccessRules.safeCode(p.getString("calendar_setup_code",""))).put("calendar_error_code",CalendarAccessRules.safeCode(PlanningAccount.connected(c)?store.ownerValue("calendar:last_error_code",""):p.getString("calendar_setup_code",""))).put("calendar_connected",PlanningAccount.connected(c)).put("calendar_configured",calendarEnabled(c,store)).put("weather_configured",flag(c,store,"weather_enabled")).put("calendar_state",store.ownerValue("calendar:status",calendarEnabled(c,store)?"pending":"not_connected")).put("weather_state",store.ownerValue("weather:status",flag(c,store,"weather_enabled")?"pending":"not_configured"))
            .put("calendar_registration_required",setup.equals("CALENDAR_APP_REGISTRATION_REQUIRED")).put("calendar_setup_next_action",setup.equals("CALENDAR_APP_REGISTRATION_REQUIRED")||setup.equals("CALENDAR_API_DISABLED")?"google_project_configuration":!PlanningAccount.connected(c)||store.ownerValue("calendar:status","").equals("authorization_required")?"authorize_on_device":"sync")
            .put("weather_provider",OpenMeteoWeatherSource.PROVIDER).put("weather_use_mode","non_commercial").put("weather_requested_forecast_days",OpenMeteoWeatherSource.FORECAST_DAYS)
            .put("weather_optional",true).put("weather_business_use_status","unresolved").put("weather_business_use_confirmed",false).put("weather_terms_url",OpenMeteoWeatherSource.TERMS)
            .put("notification_permission",Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED).put("notification_state",WeatherNotifications.state(c)).put("weather_notifications_enabled",flag(c,store,"notifications")).put("notifications_available",WeatherNotifications.ready(c)).put("background_best_effort",true).put("credentials_exposed",false);
    }
    static JSONObject weatherLocation(PixelLocalStore store,JSONObject appointment)throws Exception {
        JSONObject master=PixelMcpReadTools.effective(store.getState());return weatherLocation(store,appointment,master);
    }
    static JSONObject weatherLocation(JSONObject appointment,JSONArray customers)throws Exception {
        return WeatherLocationRules.context(appointment,customers,new JSONArray());
    }
    private static JSONObject weatherLocation(PixelLocalStore store,JSONObject appointment,JSONObject master)throws Exception {
        JSONObject a=WeatherLocationRules.context(appointment,master.getJSONArray("customers"),master.getJSONArray("orders"));
        if(!WeatherLocationRules.coordinates(a)&&!a.optString("weather_location_label").isEmpty())a=WeatherLocationRules.cached(a,new JSONObject(store.ownerValue(WeatherLocationRules.cacheKey(a),"{}")));return a;
    }
    static JSONObject risk(PixelLocalStore store,JSONObject a,Instant now)throws Exception {
        return riskAtLocation(store,weatherLocation(store,a),now);
    }
    private static JSONObject riskAtLocation(PixelLocalStore store,JSONObject a,Instant now)throws Exception {
        JSONObject cache=null;if(a.has("latitude")&&!a.isNull("latitude"))cache=new JSONObject(store.ownerValue("weather:"+WeatherRiskRules.locationKey(a),"{}"));
        JSONObject risk=WeatherRiskRules.evaluate(a,cache==null||cache.length()==0?null:cache,now,settings(store));
        if(cache==null||cache.optString("forecast_provider").isEmpty())risk.put("source",OpenMeteoWeatherSource.ATTRIBUTION);
        String state=a.optString("weather_location_state","missing");risk.put("location_label",a.optString("weather_location_label",a.optString("location"))).put("location_state",state);
        if(state.equals("locality_resolved"))risk.put("location_precision","locality").put("location_area",a.optString("weather_location_area")).put("location_notice","Ortsvorhersage: Die genaue Hausadresse wurde nicht erkannt. Wetter am Grundstück kann abweichen.");
        if(!WeatherLocationRules.coordinates(a)){
            String reason=state.equals("ambiguous")?"Einsatzadresse ist mehrdeutig. Ort oder Postleitzahl ergänzen.":state.equals("unavailable")?"Adresssuche derzeit nicht verfügbar. Erneut prüfen oder Standort am Einsatzort übernehmen.":state.equals("not_found")?"Einsatzadresse nicht gefunden. Straße, Postleitzahl und Ort prüfen.":state.equals("missing")?"Einsatzort fehlt. Adresse im Termin oder beim Kunden ergänzen.":"Einsatzadresse wird bei der Wetterprüfung ermittelt. Bei Bedarf Jetzt prüfen wählen.";
            risk.put("reasons",new JSONArray().put(reason));
        }return risk;
    }
    static JSONObject query(Context c,PixelLocalStore store,String name,JSONObject args)throws Exception {
        if(name.equals("get_planning_settings")){McpSchema.validate(args,AppointmentTools.schema(name));JSONObject s=publicSettings(c,store);return new JSONObject().put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("integration",integration(c,store));}
        McpSchema.validate(args,AppointmentTools.schema(name));Instant now=Instant.now();if(name.equals("get_workweek_weather")||name.equals("get_weather_risks")){args=new JSONObject(args.toString());if(!args.has("date_from"))args.put("date_from",now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().toString());if(!args.has("date_to"))args.put("date_to",now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate().plusDays(14).toString());}JSONObject state=store.getState(),master=PixelMcpReadTools.effective(state),out=AppointmentTools.query(name,args,state),availability=availability(c,store,now);
        if(name.equals("suggest_appointment_slots"))out=suggestions(store,master,args,now,availability);
        else if(name.equals("get_appointment")){JSONObject a=out.getJSONObject("appointment");out.put("weather",riskAtLocation(store,weatherLocation(store,a,master),now)).put("calendar",calendarState(c,store,a)).put("collisions",AppointmentAvailability.assess(a,master.getJSONArray("appointments"),availability));}
        else {
            JSONArray rows=out.getJSONArray("appointments"),risks=new JSONArray();for(int i=0;i<rows.length();i++){JSONObject entry=rows.getJSONObject(i),a=entry.getJSONObject("appointment"),r=riskAtLocation(store,weatherLocation(store,a,master),now);entry.put("weather",r).put("calendar",calendarState(c,store,a)).put("collisions",AppointmentAvailability.assess(a,master.getJSONArray("appointments"),availability));if(!r.optString("severity").equals("clear")&&!r.optString("severity").equals("inactive"))risks.put(new JSONObject().put("appointment_id",a.getString("id")).put("weather",r).put("acknowledged",!r.optString("alert_hash").isEmpty()&&r.optString("alert_hash").equals(store.ownerValue("weather:ack:"+a.getString("id"),""))));}
            out.put("weather_risks",risks);
            if(name.equals("get_workweek_weather")||name.equals("get_weather_risks")){
                int actualDays=rows.length()==0?0:OpenMeteoWeatherSource.FORECAST_DAYS,covered=0;boolean complete=rows.length()>0;Set<String> sources=new LinkedHashSet<>();
                for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i).getJSONObject("weather");boolean available=r.optBoolean("forecast_available");if(available){covered++;sources.add(r.optString("source"));}complete&=available&&r.optBoolean("forecast_complete");actualDays=Math.min(actualDays,available?r.optInt("forecast_days"):0);}
                out.put("forecast_days",actualDays).put("requested_forecast_days",OpenMeteoWeatherSource.FORECAST_DAYS).put("forecast_covered_appointments",covered).put("forecast_coverage_complete",complete)
                    .put("next_workdays",nextWorkdays(store,now)).put("source",sources.isEmpty()?OpenMeteoWeatherSource.ATTRIBUTION:String.join(" / ",sources)).put("weather_use_mode","non_commercial");
            }
        }
        return out.put("availability",availability).put("integration",integration(c,store)).put("planning_settings",settings(store));
    }
    static JSONObject availability(Context c,PixelLocalStore store,Instant now)throws Exception {
        boolean enabled=calendarEnabled(c,store)&&PlanningAccount.prefs(c).getBoolean("read_busy",false)&&store.ownerValue("calendar:status","").equals("checked");
        return AppointmentAvailability.context(new JSONObject(store.ownerValue("calendar:busy","{}")),enabled,now);
    }
    static String bindingKey(Context c,String id)throws Exception{return "calendar:binding:"+PixelMcpWriteTools.hash(PlanningAccount.prefs(c).getString("calendar_id","")).substring(0,24)+":"+id;}
    static JSONObject calendarState(Context c,PixelLocalStore store,JSONObject a)throws Exception {
        JSONObject b=new JSONObject(store.ownerValue(bindingKey(c,a.getString("id")),"{}"));boolean matching=b.has("base")&&AppointmentRules.hash(a).equals(AppointmentRules.hash(b.getJSONObject("base")));
        return new JSONObject().put("state",!calendarEnabled(c,store)?"not_connected":b.has("conflict")?"review_required":b.optBoolean("detached")?"detached":matching?b.optString("state","pending"):"pending").put("last_confirmed_at",b.optString("confirmed_at")).put("error",b.optString("error")).put("conflict_hash",b.has("conflict")?PixelMcpWriteTools.hash(b):"").put("conflict",b.opt("conflict")==null?JSONObject.NULL:b.get("conflict"));
    }
    static JSONArray nextWorkdays(PixelLocalStore store,Instant now)throws Exception {Set<Integer> days=new HashSet<>();JSONArray configured=settings(store).getJSONArray("workdays");for(int i=0;i<configured.length();i++)days.add(configured.getInt(i));JSONArray out=new JSONArray();LocalDate day=now.atZone(ZoneId.of("Europe/Vienna")).toLocalDate();for(int i=0;i<16&&out.length()<5;i++,day=day.plusDays(1))if(days.contains(day.getDayOfWeek().getValue()))out.put(day.toString());return out;}
    static boolean hasOpenEnd(JSONArray rows)throws Exception{for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(AppointmentRules.openEnd(row)&&AppointmentRules.active(row)&&!row.optString("status").equals("draft"))return true;}return false;}
    static JSONObject suggestions(PixelLocalStore store,JSONObject s,JSONObject a,Instant now,JSONObject availability)throws Exception {
        PixelMcpWriteTools.find(s.getJSONArray("customers"),a.getString("customer_id"));LocalDate from=LocalDate.parse(a.getString("date_from")),to=LocalDate.parse(a.getString("date_to"));if(to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>30)throw new IllegalArgumentException("Bitte einen Zeitraum bis31Tage wählen.");
        JSONObject preferences=settings(store),busy=availability;boolean verified=false;
        try{verified=busy.optBoolean("verified")&&Instant.parse(busy.getString("checked_at")).isAfter(now.minusSeconds(1800))&&AppointmentRules.instant(busy.getString("from")).isBefore(from.atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant().plusSeconds(1))&&!AppointmentRules.instant(busy.getString("to")).isBefore(to.plusDays(1).atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant());}catch(Exception ignored){}
        List<JSONObject> found=new ArrayList<>();JSONArray workdays=preferences.getJSONArray("workdays");Set<Integer> days=new HashSet<>();for(int i=0;i<workdays.length();i++)days.add(workdays.getInt(i));
        JSONObject prior=a.optString("order_id").isEmpty()?null:PixelMcpWriteTools.find(s.getJSONArray("orders"),a.getString("order_id"));if(prior!=null&&!a.getString("customer_id").equals(prior.getString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
        // Reuse only confirmed coordinates from appointments of this exact customer and location.
        JSONObject customer=PixelMcpWriteTools.find(s.getJSONArray("customers"),a.getString("customer_id"));String expectedPlace=prior!=null&&!prior.optString("location").trim().isEmpty()?prior.optString("location").trim():WeatherLocationRules.address(customer);JSONObject location=null;JSONArray appts=s.getJSONArray("appointments");for(int i=0;i<appts.length();i++){JSONObject row=appts.getJSONObject(i);if(a.getString("customer_id").equals(row.optString("customer_id"))&&row.has("latitude")&&!row.isNull("latitude")&&!expectedPlace.isEmpty()&&WeatherLocationRules.normalized(expectedPlace).equals(WeatherLocationRules.normalized(row.optString("location"))))location=row;}
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1))if(days.contains(day.getDayOfWeek().getValue())){
            ZonedDateTime first=day.atTime(LocalTime.parse(preferences.getString("day_start"))).atZone(ZoneId.of("Europe/Vienna")),end=day.atTime(LocalTime.parse(preferences.getString("day_end"))).atZone(ZoneId.of("Europe/Vienna"));
            for(ZonedDateTime time=first;!time.plusMinutes(a.getInt("duration_minutes")).isAfter(end);time=time.plusMinutes(30)){
                if(time.toInstant().isBefore(now))continue;JSONObject row=new JSONObject().put("id","suggestion").put("customer_id",a.getString("customer_id")).put("order_id",a.optString("order_id")).put("title",prior==null?"Geplanter Einsatz":prior.optString("title")).put("starts_at",time.toInstant().toString()).put("ends_at",time.plusMinutes(a.getInt("duration_minutes")).toInstant().toString()).put("timezone","Europe/Vienna").put("status","tentative").put("weather_profile",a.optString("weather_profile","general")).put("travel_minutes",preferences.optInt("travel_minutes"));
                row.put("location",expectedPlace);if(location!=null)row.put("latitude",location.get("latitude")).put("longitude",location.get("longitude")).put("location",location.optString("location"));
                try{AppointmentRules.validate(row,null,s);}catch(Exception blocked){continue;}
                if(!AppointmentAvailability.assess(row,s.getJSONArray("appointments"),new JSONObject()).optBoolean("app_checked",true))continue;
                boolean clash=false;JSONArray periods=busy.optJSONArray("busy");for(int j=0;verified&&periods!=null&&j<periods.length();j++){JSONObject b=periods.getJSONObject(j);if(AppointmentRules.instant(b.getString("start")).isBefore(time.plusMinutes(a.getInt("duration_minutes")).toInstant())&&AppointmentRules.instant(b.getString("end")).isAfter(time.minusMinutes(preferences.optInt("travel_minutes")).toInstant()))clash=true;}
                if(!clash){JSONObject r=riskAtLocation(store,weatherLocation(store,row,s),now);found.add(new JSONObject().put("appointment",row).put("weather",r).put("calendar_availability_verified",verified).put("customer_availability_confirmed",false));}
            }
        }
        found.sort(Comparator.comparingInt((JSONObject x)->Arrays.asList("clear","difficult","unknown","critical").indexOf(x.optJSONObject("weather").optString("severity"))).thenComparing(x->x.optJSONObject("appointment").optString("starts_at")));
        return new JSONObject().put("suggestions",new JSONArray(found.subList(0,Math.min(5,found.size())))).put("booked",false).put("calendar_availability_verified",verified).put("notice",hasOpenEnd(appts)?"Offene Endzeiten begrenzen die Zeitfenstersuche. Enden zuerst klären; Kundenverfügbarkeit separat bestätigen.":verified?"Kundenverfügbarkeit separat bestätigen. Andere Kalender können sich nach der Prüfung ändern.":"Nur App-Belegung geprüft. Externe Belegtzeiten fehlen oder sind veraltet; keine Buchung ausgeführt.");
    }
    static JSONObject acknowledge(Context c,PixelLocalStore store,JSONObject a)throws Exception {JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),a.getString("appointment_id")),r=risk(store,row,Instant.now());if(!r.optString("alert_hash").equals(a.getString("expected_alert_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Wetterhinweis wurde geändert. Bitte neu prüfen.");store.putOwnerValue("weather:ack:"+row.getString("id"),r.getString("alert_hash"));WeatherNotifications.cancel(c,row.getString("id"));return new JSONObject().put("acknowledged",true).put("appointment_changed",false);}

    static boolean checkBusy(Context c,PixelLocalStore store,JSONObject row)throws Exception {
        if(!availability(c,store,Instant.now()).optBoolean("verified"))return false;
        if(!AppointmentRules.active(row)||row.optString("status").equals("draft"))return false;
        JSONObject report=AppointmentAvailability.assess(row,new JSONArray(),availability(c,store,Instant.now()));
        JSONArray periods=report.getJSONArray("conflicts");for(int i=0;i<periods.length();i++)if(!periods.getJSONObject(i).optBoolean("uncertain"))throw new PixelMcpWriteTools.Rejected("CALENDAR_BUSY","Zeit oder Anfahrt überschneidet sich mit aktuell geprüften Google-Belegtzeiten.");return report.getBoolean("external_checked");
    }
    static JSONObject resolve(Context c,PixelLocalStore store,JSONObject a)throws Exception {
        JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),a.getString("appointment_id"));if(!AppointmentRules.hash(row).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","App-Termin wurde geändert.");
        String key=bindingKey(c,row.getString("id"));JSONObject b=new JSONObject(store.ownerValue(key,"{}"));if(!b.has("conflict")||!PixelMcpWriteTools.hash(b).equals(a.getString("expected_conflict_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Kalendervergleich wurde geändert. Neu prüfen.");
        if(!a.getBoolean("confirmed"))throw new IllegalArgumentException("Entscheidung ausdrücklich bestätigen.");String choice=a.getString("resolution");
        if(choice.equals("keep_without_calendar")){b.put("state","detached").put("detached",true);b.remove("conflict");b.remove("decision");}
        else b.put("decision",new JSONObject().put("resolution",choice).put("local_hash",AppointmentRules.hash(row)).put("remote_hash",PixelMcpWriteTools.hash(b.getJSONObject("conflict").isNull("remote")?JSONObject.NULL:b.getJSONObject("conflict").get("remote"))));
        store.putOwnerValue(key,b.toString());return new JSONObject().put("state",choice.equals("keep_without_calendar")?"detached":"queued").put("business_data_changed",false).put("google_confirmed",false);
    }

    interface AdapterTask {void run()throws Exception;}
    static void refresh(Context c,PixelLocalStore store){synchronized(LOCK){
        Instant now=Instant.now();boolean weatherEnabled=flag(c,store,"weather_enabled");
        long calendarNext=adapter(c,store,"calendar",calendarEnabled(c,store),now,()->syncCalendar(c,store,now));
        if(Thread.currentThread().isInterrupted())return; // Android cancellation is not a completed planning check.
        long weatherNext=adapter(c,store,"weather",weatherEnabled,now,()->refreshWeather(c,store,now));
        if(Thread.currentThread().isInterrupted())return;
        long weatherCadence=Long.MAX_VALUE;
        if(weatherEnabled)try{weatherCadence=FreeWeatherSource.nextDelayMillis(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),now);}catch(Exception ignored){}
        try{notifyRisks(c,store,now);store.putOwnerValue("planning:status",new JSONObject().put("last_check_at",now.toString()).put("background_best_effort",true).toString());}catch(Exception ignored){}
        MainActivity.refreshPlanningView();
        long next=Math.min(Math.min(calendarNext,weatherNext),weatherCadence);if(calendarEnabled(c,store))next=Math.min(next,15*60*1000L);if(next<Long.MAX_VALUE)PlanningJob.schedule(c,next);
    }}
    static long adapter(Context c,PixelLocalStore store,String kind,boolean enabled,Instant now,AdapterTask task){
        android.content.SharedPreferences p=PlanningAccount.prefs(c);String prefix=kind+"_";boolean calendar=kind.equals("calendar");int failures=p.getInt(prefix+"failures",0);if(!enabled)return Long.MAX_VALUE;
        // Older background cancellation was incorrectly persisted as a revoked grant.
        if(calendar&&p.getBoolean(prefix+"blocked",false)&&Arrays.asList("CALENDAR_CONSENT_CANCELLED","CALENDAR_INTERRUPTED").contains(store.ownerValue("calendar:last_error_code","")))p.edit().putBoolean(prefix+"blocked",false).apply();
        if(p.getBoolean(prefix+"blocked",false)||!calendar&&failures>=8)return Long.MAX_VALUE;
        try{String retry=p.getString(prefix+"retry_at","");if(!retry.isEmpty()){long remaining=Duration.between(now,Instant.parse(retry)).toMillis();if(remaining>0)return remaining;}}catch(Exception ignored){}
        try{if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(calendar)store.putOwnerValue("calendar:last_attempt_at",now.toString());task.run();if(Thread.currentThread().isInterrupted())throw new InterruptedException();p.edit().putInt(prefix+"failures",0).remove(prefix+"retry_at").apply();store.putOwnerValue(kind+":status","checked");if(calendar){store.putOwnerValue("calendar:last_error_code","");store.putOwnerValue("calendar:last_success_at",Instant.now().toString());}return Long.MAX_VALUE;}
        catch(Exception e){
            if(CalendarRecoveryRules.interrupted(e)||Thread.currentThread().isInterrupted()){
                Thread.currentThread().interrupt();if(calendar)store.putOwnerValue("calendar:last_error_code","CALENDAR_INTERRUPTED");
                p.edit().putString(prefix+"retry_at",now.plusMillis(CalendarRecoveryRules.MIN_RETRY).toString()).apply();store.putOwnerValue(kind+":status","retry_pending");return CalendarRecoveryRules.MIN_RETRY;
            }
            if(kind.equals("calendar"))store.putOwnerValue("calendar:last_error_code",CalendarAccessRules.code(e));
            if(kind.equals("weather")&&e instanceof HttpTransport.Failure&&Arrays.asList("WEATHER_BATCH_PENDING","WEATHER_RATE_LIMIT").contains(((HttpTransport.Failure)e).code)){
                long delay=Math.max(30000,((HttpTransport.Failure)e).retryAfter);p.edit().putString(prefix+"retry_at",now.plusMillis(delay).toString()).apply();store.putOwnerValue(kind+":status","pending");return delay;
            }
            boolean auth=e instanceof HttpTransport.Failure&&((HttpTransport.Failure)e).userAction&&!(calendar&&((HttpTransport.Failure)e).code.equals("CALENDAR_CONSENT_CANCELLED"));
            if(auth){p.edit().putBoolean(prefix+"blocked",true).apply();store.putOwnerValue(kind+":status","authorization_required");return Long.MAX_VALUE;}
            if(e instanceof PixelMcpWriteTools.Rejected){store.putOwnerValue(kind+":status","review_required");return Long.MAX_VALUE;}
            if(calendar){long delay=CalendarRecoveryRules.retryDelay(failures,e instanceof HttpTransport.Failure?((HttpTransport.Failure)e).retryAfter:0,new Random().nextInt(3000));p.edit().putInt(prefix+"failures",CalendarRecoveryRules.nextFailures(failures)).putString(prefix+"retry_at",now.plusMillis(delay).toString()).apply();store.putOwnerValue("calendar:status","retry_pending");return delay;}
            long delay=Math.min(3600000,30000L*(1L<<failures))+new Random().nextInt(3000);if(e instanceof HttpTransport.Failure)delay=Math.max(delay,((HttpTransport.Failure)e).retryAfter);
            p.edit().putInt(prefix+"failures",failures+1).putString(prefix+"retry_at",now.plusMillis(delay).toString()).apply();store.putOwnerValue(kind+":status",failures>=7?"retry_limit_reached":"retry_pending");return failures>=7?Long.MAX_VALUE:delay;
        }
    }
    static void syncCalendar(Context c,PixelLocalStore store,Instant now)throws Exception {
        syncCalendar(c,store,now,PlanningAccount.client(c),PlanningAccount.prefs(c).getString("calendar_id",""));
    }
    static void syncCalendar(Context c,PixelLocalStore store,Instant now,CalendarClient client,String calendar)throws Exception {
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
            if(!binding.has("base")&&(current.optString("status").equals("cancelled")||!AppointmentRules.pending(current,now)))continue;
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
                    if(remote!=null&&!remote.optString("status").equals("cancelled")){CalendarRules.requireIdentity(remote,id);if(!force&&binding.has("base")&&!remote.optString("etag").equals(binding.optString("etag")))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Google-Termin geändert. Absage erst nach Vergleich bestätigen.");client.remove(calendar,eventId,remote);}
                    binding=new JSONObject().put("base",current).put("state","cancelled").put("confirmed_at",now.toString());
                }else{
                    if(!force&&binding.has("base")&&(remote==null||remote.optString("status").equals("cancelled")))throw new PixelMcpWriteTools.Rejected("CALENDAR_REMOVED","Google-Eintrag entfernt. App-Termin prüfen.");
                    if(remote!=null){
                        CalendarRules.requireIdentity(remote,id);JSONObject base=binding.optJSONObject("base");
                        if(base==null){JSONObject wanted=CalendarRules.changes(current,current,remote);if(!CalendarRules.projectionHash(wanted).equals(CalendarRules.projectionHash(current)))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Bereits vorhandener Google-Eintrag weicht ab. Beide Stände prüfen.");}
                        else if(!force&&!remote.optString("etag").equals(binding.optString("etag")))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Google-Termin wurde außerhalb der App geändert. Beide Stände bleiben erhalten; Entscheidung erforderlich.");
                    }
                    // A readback import must itself pass the current SQLite version/overlap guard.
                    JSONObject latest=OwnerJournalRules.findOrNull(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),id);
                    if(latest==null||!AppointmentRules.hash(latest).equals(AppointmentRules.hash(current)))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","App-Termin während Kalenderprüfung geändert.");
                    boolean localChanged=force||!binding.has("base")||!CalendarRules.projectionHash(current).equals(CalendarRules.projectionHash(binding.getJSONObject("base")));
                    if(remote==null||localChanged)remote=client.save(calendar,current,remote,PixelMcpWriteTools.find(snapshot.getJSONArray("customers"),current.getString("customer_id")).optString("name"));
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
    private static JSONObject importCalendar(Context c,PixelLocalStore store,JSONObject current,JSONObject merged,JSONObject remote)throws Exception {
        JSONObject updates=new JSONObject();for(String field:CalendarRules.SHARED)if(!field.equals("status")&&!merged.optString(field).equals(current.optString(field)))updates.put(field,merged.get(field));
        if(updates.length()>0){new PixelOwnerAccess(c,store).execute("update_appointment",new JSONObject().put("appointment_id",current.getString("id")).put("expected_record_hash",AppointmentRules.hash(current)).put("updates",updates).put("request_id","calendar-import-"+PixelMcpWriteTools.hash(current.getString("id")+remote.optString("etag"))));return PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments"),current.getString("id"));}
        return current;
    }
    private static boolean contains(JSONArray list,String id){for(int i=0;i<list.length();i++)if(id.equals(list.optString(i)))return true;return false;}
    static void enableWeatherForAppointment(Context c,PixelLocalStore store)throws Exception {
        JSONObject s=settings(store);
        android.content.SharedPreferences p=PlanningAccount.prefs(c);
        // Defaults are transactional with the first appointment, never overrides of an explicit AUS.
        boolean changed=false;
        for(String key:new String[]{"weather_enabled","notifications"})if(!s.has(key)&&!p.contains(key)){s.put(key,true);changed=true;}
        if(changed)saveSettings(store,s);
    }
    static void refreshWeather(Context c,PixelLocalStore store,Instant now)throws Exception {refreshWeather(c,store,now,new PlanningWire());}
    static void refreshWeather(Context c,PixelLocalStore store,Instant now,HttpTransport.Wire upstream)throws Exception {
        refreshWeather(c,store,now,upstream,query->WeatherLocationResolver.lookup(c,query));
    }
    static void refreshWeather(Context c,PixelLocalStore store,Instant now,HttpTransport.Wire upstream,WeatherLocationResolver.Lookup lookup)throws Exception {
        JSONObject snapshot=PixelMcpReadTools.effective(store.getState());JSONArray all=snapshot.getJSONArray("appointments");
        Map<String,Long> sites=new LinkedHashMap<>();WeatherLocationResolver.Budget locationBudget=new WeatherLocationResolver.Budget();boolean locationsPending=false;
        for(int i=0;i<all.length();i++){
            JSONObject original=all.getJSONObject(i);
            if(!AppointmentRules.active(original)||original.optString("status").equals("draft")||!AppointmentRules.pending(original,now)||AppointmentRules.instant(original.getString("starts_at")).isAfter(now.plusSeconds(FreeWeatherSource.MONITOR_WINDOW)))continue;
            JSONObject a=weatherLocation(store,original,snapshot);
            if(!WeatherLocationRules.coordinates(a))a=WeatherLocationResolver.resolve(c,store,a,now,locationBudget,lookup);all.put(i,a);if(a.optString("weather_location_state").equals("pending"))locationsPending=true;
            if(!AppointmentRules.active(a)||a.optString("status").equals("draft")||!a.has("latitude")||a.isNull("latitude")||!a.has("longitude")||a.isNull("longitude"))continue;
            Instant start=AppointmentRules.weatherStart(a,now),end=AppointmentRules.weatherEnd(a,now);
            if(!end.isAfter(now)||start.isAfter(now.plusSeconds(FreeWeatherSource.MONITOR_WINDOW)))continue;
            String loc=WeatherRiskRules.locationKey(a);long delta=Math.max(0,Duration.between(now,start).getSeconds());
            Long prior=sites.get(loc);if(prior==null||delta<prior)sites.put(loc,delta);
        }
        JSONObject common=new JSONObject(store.ownerValue("weather:public-source-cache","{}"));
        final int[] requests={0};final long deadline=android.os.SystemClock.elapsedRealtime()+45000;
        HttpTransport.Wire wire=(method,url,headers,body,max)->{
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            if(++requests[0]>12||android.os.SystemClock.elapsedRealtime()>deadline)throw new HttpTransport.Failure("WEATHER_BATCH_PENDING","Weitere Wetterprüfungen werden später fortgesetzt.",300000,false);
            long bucket=now.getEpochSecond()/3600;JSONObject budget=new JSONObject(store.ownerValue("weather:request-budget","{}"));int count=budget.optLong("hour",-1)==bucket?budget.optInt("count"):0;
            if(count>=180)throw new HttpTransport.Failure("WEATHER_RATE_LIMIT","Wetterprüfung wird später fortgesetzt.",(3600-now.getEpochSecond()%3600)*1000,false);
            store.putOwnerValue("weather:request-budget",new JSONObject().put("hour",bucket).put("count",count+1).toString());
            if(requests[0]>1)Thread.sleep(300); // Below the public 5 requests/second quota; worker only.
            return upstream.send(method,url,headers,body,max);
        };
        Exception failure=null;
        // Oldest site first prevents starvation when a large batch spans several jobs.
        List<Map.Entry<String,Long>> ordered=new ArrayList<>(sites.entrySet());
        ordered.sort(Comparator.comparing(e->store.ownerValue("weather:attempt:"+e.getKey(),"")));
        for(Map.Entry<String,Long> site:ordered){
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            String loc=site.getKey();JSONObject cache=new JSONObject(store.ownerValue("weather:"+loc,"{}"));
            for(Iterator<String> keys=common.keys();keys.hasNext();){String key=keys.next();cache.put(key,common.get(key));}
            try{
                FreeWeatherSource.refreshNonCommercialSite(loc,cache,now,site.getValue(),wire);cache.remove("source_unavailable");
                if(site.getValue()<=3*3600){JSONObject assessment=null;for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);if(loc.equals(WeatherRiskRules.locationKey(a))){assessment=WeatherRiskRules.evaluate(a,cache,now,settings(store));if(!assessment.optString("severity").equals("clear"))break;}}
                    if(assessment!=null&&!assessment.optString("severity").equals("clear"))try{FreeWeatherSource.refreshNowcast(loc,cache,now,wire);cache.remove("nowcast_status");}
                    catch(Exception unavailable){cache.put("nowcast_status","Kurzfristwetter derzeit nicht verfügbar.");if(CalendarRecoveryRules.interrupted(unavailable)||(unavailable instanceof HttpTransport.Failure&&Arrays.asList("WEATHER_RATE_LIMIT","WEATHER_BATCH_PENDING").contains(((HttpTransport.Failure)unavailable).code)))throw unavailable;}
                }
            }catch(Exception unavailable){failure=unavailable;if(unavailable instanceof HttpTransport.Failure&&((HttpTransport.Failure)unavailable).userAction)cache.put("source_unavailable",true);}
            finally{
                for(Iterator<String> keys=cache.keys();keys.hasNext();){String key=keys.next();if(key.equals("licence_checked_at")||key.contains(":metadata"))common.put(key,cache.get(key));}
                store.putOwnerValue("weather:public-source-cache",common.toString());store.putOwnerValue("weather:"+loc,cache.toString());store.putOwnerValue("weather:attempt:"+loc,now.toString());
            }
            if(failure instanceof HttpTransport.Failure)break; // Respect Retry-After globally, never hammer another site.
        }
        if(failure!=null)throw failure;if(locationsPending)throw new HttpTransport.Failure("WEATHER_BATCH_PENDING","Weitere Einsatzadressen werden später geprüft.",300000,false);
    }
    static void notifyRisks(Context c,PixelLocalStore store,Instant now)throws Exception {
        if(!flag(c,store,"weather_enabled")||!flag(c,store,"notifications")){WeatherNotifications.cancelAll(c);store.putOwnerValue("weather:delivery-paused","true");return;}
        if(!WeatherNotifications.ready(c)){store.putOwnerValue("weather:delivery-paused","true");return;}
        boolean resumed=store.ownerValue("weather:delivery-paused","false").equals("true");
        JSONObject snapshot=PixelMcpReadTools.effective(store.getState());JSONArray all=snapshot.getJSONArray("appointments"),customers=snapshot.getJSONArray("customers");Set<String> active=new HashSet<>();
        for(int i=0;i<all.length();i++){
            JSONObject a=all.getJSONObject(i);String id=a.getString("id");JSONObject r=riskAtLocation(store,weatherLocation(store,a,snapshot),now);
            if(!WeatherAlertRules.actionable(a,r,now)){
                WeatherNotifications.cancel(c,id);
                // Once a stale/unknown notice is removed, a fresh unacknowledged risk may be delivered again.
                store.putOwnerValue("weather:notified:"+id,"");
                if(r.optString("severity").equals("clear")||r.optString("severity").equals("inactive")){store.putOwnerValue("weather:notified:"+id,"");store.putOwnerValue("weather:ack:"+id,"");}continue;
            }
            active.add(id);if(resumed)store.putOwnerValue("weather:notified:"+id,"");if(!WeatherAlertRules.shouldNotify(a,r,now,store.ownerValue("weather:ack:"+id,""),store.ownerValue("weather:notified:"+id,"")))continue;
            JSONObject customer=OwnerJournalRules.findOrNull(customers,a.optString("customer_id"));
            if(WeatherNotifications.post(c,a,r,customer==null?"":customer.optString("name")))store.putOwnerValue("weather:notified:"+id,r.getString("alert_hash"));
        }
        // Retired/archive notifications must not keep showing a removed appointment.
        WeatherNotifications.retain(c,active);
        store.putOwnerValue("weather:delivery-paused","false");
    }
    private AppointmentRuntime(){}
}
