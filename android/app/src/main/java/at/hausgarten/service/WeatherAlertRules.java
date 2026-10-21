package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared, testable wording and delivery decisions. Never changes a planned job. */
final class WeatherAlertRules {
    static boolean actionable(JSONObject appointment,JSONObject risk,Instant now)throws Exception {
        if(!AppointmentRules.active(appointment)||appointment.optString("status").equals("draft")||!AppointmentRules.pending(appointment,now))return false;
        return Arrays.asList("difficult","critical").contains(risk.optString("severity"))&&risk.optString("alert_hash").matches("[0-9a-f]{64}");
    }
    static boolean shouldNotify(JSONObject appointment,JSONObject risk,Instant now,String acknowledged,String notified)throws Exception {
        if(!actionable(appointment,risk,now))return false;
        String hash=risk.getString("alert_hash");if(hash.equals(acknowledged)||hash.equals(notified))return false;
        int hour=now.atZone(ZoneId.of("Europe/Vienna")).getHour();
        return (hour>=6&&hour<21)||risk.optBoolean("critical_official_warning");
    }
    static JSONObject message(JSONObject appointment,JSONObject risk,String customer)throws Exception {
        JSONArray reasons=risk.optJSONArray("reasons");String all=reasons==null?"":reasons.toString().toLowerCase(Locale.GERMAN);List<String> topics=new ArrayList<>();
        for(String[] item:new String[][]{{"gewitter","Gewitter"},{"böen","Böen"},{"wind","Wind"},{"schnee","Schnee / Eis"},{"glatteis","Glatteis"},{"frost","Frost"},{"hitze","Hitze"},{"regen","Regen"},{"niederschlag","Niederschlag"}})if(all.contains(item[0])&&!(item[0].equals("wind")&&topics.contains("Böen"))&&!(item[0].equals("niederschlag")&&topics.contains("Regen")))topics.add(item[1]);
        String title=(risk.optString("severity").equals("critical")?"Wetterwarnung: ":"Wetterhinweis: ")+(topics.isEmpty()?"Einsatz prüfen":String.join(" · ",topics.subList(0,Math.min(3,topics.size()))));
        String when=DateTimeFormatter.ofPattern("EEE, dd.MM. HH:mm",Locale.GERMAN).withZone(ZoneId.of(appointment.optString("timezone","Europe/Vienna"))).format(AppointmentRules.instant(appointment.getString("starts_at")));
        String reason=reasons==null?risk.optString("label"):reasons.optString(0,risk.optString("label"));
        if(reasons!=null)for(int i=0;i<reasons.length();i++)if(reasons.optString(i).startsWith("GeoSphere-Warnung:")){reason=reasons.getString(i);break;}
        String area=risk.optString("location_precision").equals("locality")?"\nOrtsvorhersage · "+risk.optString("location_area"):"";
        String body=(customer==null||customer.trim().isEmpty()?"Geplanter Einsatz":customer.trim())+" · "+when+"\n"+reason+area;
        return new JSONObject().put("title",title).put("body",body).put("detail",appointment.optString("title")+"\n"+body);
    }
    private WeatherAlertRules(){}
}
