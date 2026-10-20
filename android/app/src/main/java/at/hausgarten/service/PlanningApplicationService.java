package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Planning use cases; account/device work is an explicit repository port. */
final class PlanningApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    PlanningApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();

            if(name.equals("update_planning_settings")){JSONObject s=repository.planningSettings();if(!PixelMcpWriteTools.hash(s).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Planungseinstellungen wurden geändert.");JSONObject changes=a.getJSONObject("updates");for(Iterator<String> i=changes.keys();i.hasNext();){String k=i.next();s.put(k,changes.get(k));}if(s.optBoolean("calendar_enabled")&&!repository.calendarConnected())throw new IllegalArgumentException("Google-Kalender zuerst am Gerät autorisieren.");if(s.optBoolean("weather_enabled"))s.put("weather_license_confirmed",true);repository.savePlanningSettings(s,a.getString("expected_record_hash"));return new JSONObject().put("record_hash",PixelMcpWriteTools.hash(s)).put("local_state","local_saved").put("credentials_changed",false);}
            if(name.equals("request_weather_refresh"))return repository.requestPlanning();
            if(name.equals("request_calendar_sync"))return repository.requestPlanning();
            if(name.equals("acknowledge_weather_alert"))return repository.acknowledgeWeather(a);
            if(name.equals("resolve_calendar_conflict"))return repository.resolveCalendar(a);
            if(name.equals("create_appointment_series")){JSONArray ids=new JSONArray();java.time.ZonedDateTime starts=AppointmentRules.instant(a.getString("starts_at")).atZone(java.time.ZoneId.of(a.getString("timezone"))),ends=AppointmentRules.instant(a.getString("ends_at")).atZone(java.time.ZoneId.of(a.getString("timezone")));for(int i=0;i<a.getInt("occurrences");i++){JSONObject occurrence=new JSONObject(a.toString()).put("series_id","series_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("starts_at",AppointmentRules.local(starts.toLocalDateTime().plusWeeks((long)i*a.getInt("interval_weeks")).toString(),a.getString("timezone"),"").toString()).put("ends_at",AppointmentRules.local(ends.toLocalDateTime().plusWeeks((long)i*a.getInt("interval_weeks")).toString(),a.getString("timezone"),"").toString());JSONObject row=AppointmentRules.prepare("create_appointment",occurrence,all,request+"_"+i,stamp);repository.checkBusy(row);repository.enqueue("appointment",row,request+"_"+i);OwnerJournalRules.replace(all,"appointments",row.getString("id"),DriveJournalRules.clean(row));ids.put(row.getString("id"));}return new JSONObject().put("appointment_ids",ids).put("local_state","local_saved").put("cloud_confirmed",false);}
            JSONArray syncItems=repository.state().getJSONObject("sync_queue").getJSONArray("items");for(int i=0;i<syncItems.length();i++){JSONObject item=syncItems.getJSONObject(i);if(a.optString("appointment_id").equals(item.optString("entity_id"))&&"appointment".equals(item.optString("kind"))&&item.optString("state").contains("conflict"))throw new PixelMcpWriteTools.Rejected("SYNC_CONFLICT_PENDING","Vorhandenen Drive-Terminkonflikt zuerst in der Sync-Zentrale prüfen. Beide Varianten bleiben erhalten.");}
            if(name.equals("update_appointment")){JSONObject updates=a.getJSONObject("updates"),before=PixelMcpWriteTools.find(all.getJSONArray("appointments"),a.getString("appointment_id"));if(updates.has("location")&&!updates.optString("location").equals(before.optString("location"))&&!updates.has("latitude")&&!updates.has("longitude"))updates.put("latitude",JSONObject.NULL).put("longitude",JSONObject.NULL);}
            JSONObject row=AppointmentRules.prepare(name,a,all,request,stamp);repository.checkBusy(row);return repository.enqueue("appointment",row,request).put("appointment_id",row.getString("id"));
        
    }
}
