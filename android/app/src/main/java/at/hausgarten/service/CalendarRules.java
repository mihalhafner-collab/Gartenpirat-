package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Pure event projection and three-way comparison; no customer data in default titles. */
final class CalendarRules {
    static final String[] SHARED={"title","location","starts_at","ends_at","timezone","status"};
    static String eventId(String appointmentId)throws Exception{return "hg"+PixelMcpWriteTools.hash("hausgarten.appointment.v1:"+appointmentId);}
    static JSONObject event(JSONObject a)throws Exception {
        return new JSONObject().put("id",eventId(a.getString("id"))).put("summary",a.getString("title")).put("location",a.optString("location"))
            .put("start",new JSONObject().put("dateTime",a.getString("starts_at")).put("timeZone",a.optString("timezone","Europe/Vienna")))
            .put("end",new JSONObject().put("dateTime",a.getString("ends_at")).put("timeZone",a.optString("timezone","Europe/Vienna")))
            .put("visibility","private").put("transparency","opaque").put("status",a.optString("status").equals("tentative")?"tentative":"confirmed")
            .put("extendedProperties",new JSONObject().put("private",new JSONObject().put("hg_appointment_id",a.getString("id"))));
    }
    static void requireIdentity(JSONObject event,String id)throws Exception {
        JSONObject props=event.optJSONObject("extendedProperties"),privateProps=props==null?null:props.optJSONObject("private");
        if(privateProps==null||!id.equals(privateProps.optString("hg_appointment_id")))throw new PixelMcpWriteTools.Rejected("CALENDAR_ID_CONFLICT","Kalendereintrag hat keine passende App-Identität. Keine Änderung vorgenommen.");
    }
    static JSONObject changes(JSONObject a,JSONObject base,JSONObject remote)throws Exception {
        if(remote.optJSONArray("recurrence")!=null||!remote.optString("recurringEventId").isEmpty())throw new PixelMcpWriteTools.Rejected("CALENDAR_FORMAT_CONFLICT","Google-Serie separat prüfen. App-Wiederholungen sind ausdrücklich geplante einzelne Einsätze.");
        requireIdentity(remote,a.getString("id"));if(remote.optString("status").equals("cancelled"))throw new PixelMcpWriteTools.Rejected("CALENDAR_REMOVED","Google-Eintrag wurde entfernt. Termin in der App prüfen; keine automatische Neuanlage.");
        JSONObject next=new JSONObject(a.toString()),r=new JSONObject().put("title",remote.optString("summary")).put("location",remote.optString("location"));
        for(String[] field:new String[][]{{"starts_at","start"},{"ends_at","end"}}){JSONObject date=remote.optJSONObject(field[1]);if(date==null||date.optString("dateTime").isEmpty())throw new PixelMcpWriteTools.Rejected("CALENDAR_FORMAT_CONFLICT","Ganztägigen/ungültigen Google-Eintrag in der App prüfen.");r.put(field[0],AppointmentRules.instant(date.getString("dateTime")).toString());}
        r.put("timezone",remote.getJSONObject("start").optString("timeZone",a.optString("timezone","Europe/Vienna")));
        for(String key:new String[]{"title","location","starts_at","ends_at","timezone"}){
            String before=base.optString(key),local=a.optString(key),external=r.optString(key);if(!external.equals(before)){
                if(!local.equals(before)&&!local.equals(external))throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Termin wurde in App und Google unterschiedlich geändert. Beide Varianten bleiben erhalten.");next.put(key,external);
            }
        }
        return next;
    }
    static String projectionHash(JSONObject a)throws Exception{JSONObject p=new JSONObject();for(String key:SHARED)p.put(key,a.opt(key)==null?JSONObject.NULL:a.get(key));return PixelMcpWriteTools.hash(p);}
    private CalendarRules(){}
}
