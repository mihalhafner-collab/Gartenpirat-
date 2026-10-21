package at.hausgarten.service;

import org.json.*;
import java.time.*;

/** Read-only collision projection. No event titles, writes, bookings or network requests. */
final class AppointmentAvailability {
    static JSONObject context(JSONObject cache,boolean enabled,Instant now)throws Exception {
        JSONObject out=new JSONObject().put("verified",false).put("busy",new JSONArray());
        if(!enabled||!cache.optBoolean("verified"))return out;
        try{
            Instant checked=AppointmentRules.instant(cache.getString("checked_at")),from=AppointmentRules.instant(cache.getString("from")),to=AppointmentRules.instant(cache.getString("to"));
            if(!checked.isAfter(now.minusSeconds(1800))||checked.isAfter(now.plusSeconds(60))||!to.isAfter(from))return out;
            JSONArray safe=new JSONArray(),periods=cache.getJSONArray("busy");
            for(int i=0;i<periods.length();i++){JSONObject p=periods.getJSONObject(i);Instant start=AppointmentRules.instant(p.getString("start")),end=AppointmentRules.instant(p.getString("end"));if(!end.isAfter(start))return out;safe.put(new JSONObject().put("start",start.toString()).put("end",end.toString()));}
            return out.put("verified",true).put("checked_at",checked.toString()).put("from",from.toString()).put("to",to.toString()).put("busy",safe);
        }catch(Exception invalid){return out;}
    }
    static boolean overlaps(Instant a,Instant b,Instant c,Instant d){return a.isBefore(d)&&b.isAfter(c);}
    static JSONObject assess(JSONObject row,JSONArray appointments,JSONObject availability)throws Exception {
        JSONArray clashes=new JSONArray();JSONObject out=new JSONObject().put("conflicts",clashes).put("external_checked",false);
        if(!AppointmentRules.active(row)||row.optString("status").equals("draft"))return out.put("inactive",true);
        Instant work=AppointmentRules.instant(row.getString("starts_at")),start=work.minusSeconds(row.optInt("travel_minutes")*60L),end=AppointmentRules.instant(row.getString("ends_at"));
        for(int i=0;i<appointments.length();i++){
            JSONObject other=appointments.getJSONObject(i);if(row.optString("id").equals(other.optString("id"))||!AppointmentRules.active(other)||other.optString("status").equals("draft"))continue;
            Instant a=AppointmentRules.instant(other.getString("starts_at")),b=AppointmentRules.instant(other.getString("ends_at")),travel=a.minusSeconds(other.optInt("travel_minutes")*60L);
            if(overlaps(start,end,travel,b))clashes.put(new JSONObject().put("source","app").put("appointment_id",other.getString("id")).put("starts_at",a.toString()).put("ends_at",b.toString()).put("travel_only",!overlaps(work,end,a,b)));
        }
        boolean verified=availability.optBoolean("verified");try{verified=verified&&!AppointmentRules.instant(availability.getString("from")).isAfter(start)&&!AppointmentRules.instant(availability.getString("to")).isBefore(end);}catch(Exception missing){verified=false;}
        if(verified){JSONArray periods=availability.getJSONArray("busy");for(int i=0;i<periods.length();i++){JSONObject p=periods.getJSONObject(i);Instant a=AppointmentRules.instant(p.getString("start")),b=AppointmentRules.instant(p.getString("end"));if(overlaps(start,end,a,b))clashes.put(new JSONObject().put("source","google").put("starts_at",a.toString()).put("ends_at",b.toString()).put("travel_only",!overlaps(work,end,a,b)));}}
        return out.put("external_checked",verified).put("checked_at",verified?availability.optString("checked_at"):"");
    }
    private AppointmentAvailability(){}
}
