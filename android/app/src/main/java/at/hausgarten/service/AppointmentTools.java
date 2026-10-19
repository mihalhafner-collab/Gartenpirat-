package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

final class AppointmentTools {
    static final String[] READS={"get_planning_settings","get_planning_context","list_appointments","get_appointment","suggest_appointment_slots","get_workweek_weather","get_weather_risks"};
    static final String[] WRITES={"update_planning_settings","request_weather_refresh","create_appointment","create_appointment_series","resolve_calendar_conflict","update_appointment","cancel_appointment","acknowledge_weather_alert","request_calendar_sync"};
    static boolean read(String n){return Arrays.asList(READS).contains(n);}static boolean write(String n){return Arrays.asList(WRITES).contains(n);}
    static JSONObject fields()throws Exception {
        JSONObject p=new JSONObject();for(String k:AppointmentRules.FIELDS)p.put(k,McpFields.text(4000));
        for(String k:new String[]{"latitude","longitude"})p.put(k,new JSONObject().put("type",new JSONArray().put("number").put("null")).put("minimum",k.equals("latitude")?-90:-180).put("maximum",k.equals("latitude")?90:180));
        for(String k:new String[]{"travel_minutes","break_minutes"})p.put(k,McpFields.number(0,720));
        p.put("status",McpFields.enumeration("draft","tentative","confirmed","completed","cancelled")).put("purpose",McpFields.enumeration("work","inspection")).put("weather_profile",McpFields.enumeration(AppointmentRules.PROFILES));
        p.put("scope_refs",new JSONObject().put("type","array").put("maxItems",200).put("items",McpFields.text(200)));return p;
    }
    static JSONObject schema(String n)throws Exception {
        JSONObject p=new JSONObject();List<String> required=new ArrayList<>();
        if(write(n)){p.put("request_id",McpFields.text(128).put("minLength",8));required.add("request_id");}
        if(n.equals("update_planning_settings")){
            JSONObject f=new JSONObject().put("workdays",new JSONObject().put("type","array").put("minItems",1).put("maxItems",7).put("items",McpFields.number(1,7))).put("day_start",McpFields.text(5)).put("day_end",McpFields.text(5)).put("travel_minutes",McpFields.number(0,720));
            for(String k:new String[]{"calendar_enabled","weather_enabled","weather_license_confirmed","notifications"})f.put(k,McpFields.bool());
            for(String k:new String[]{"rain_mm","rain_probability","gust_kmh","frost_c","heat_c"})f.put(k,new JSONObject().put("type","number").put("minimum",k.equals("frost_c")?-30:0).put("maximum",k.equals("rain_probability")?100:200));
            p.put("expected_record_hash",McpFields.hash()).put("updates",McpFields.object(f).put("minProperties",1));required.addAll(Arrays.asList("expected_record_hash","updates"));
        }
        if(Arrays.asList("get_appointment","update_appointment","cancel_appointment","acknowledge_weather_alert").contains(n)){p.put("appointment_id",McpFields.id());required.add("appointment_id");}
        if(n.equals("create_appointment")||n.equals("create_appointment_series")){JSONObject f=fields();for(Iterator<String> it=f.keys();it.hasNext();){String k=it.next();p.put(k,f.get(k));}required.addAll(Arrays.asList("customer_id","title","starts_at","ends_at","timezone","weather_profile"));}
        if(n.equals("create_appointment_series")){p.put("occurrences",McpFields.number(2,26)).put("interval_weeks",McpFields.number(1,12));required.addAll(Arrays.asList("occurrences","interval_weeks"));}
        if(n.equals("resolve_calendar_conflict")){p.put("appointment_id",McpFields.id()).put("expected_record_hash",McpFields.hash()).put("expected_conflict_hash",McpFields.hash()).put("resolution",McpFields.enumeration("keep_app","use_google","keep_without_calendar")).put("confirmed",McpFields.bool().put("enum",new JSONArray().put(true)));required.addAll(Arrays.asList("appointment_id","expected_record_hash","expected_conflict_hash","resolution","confirmed"));}
        if(n.equals("update_appointment")){p.put("updates",McpFields.object(fields()).put("minProperties",1));required.add("updates");}
        if(n.equals("update_appointment")||n.equals("cancel_appointment")){p.put("expected_record_hash",McpFields.hash());required.add("expected_record_hash");}
        if(n.equals("acknowledge_weather_alert")){p.put("expected_alert_hash",McpFields.hash());required.add("expected_alert_hash");}
        if(n.equals("list_appointments")){p.put("offset",McpFields.number(0,1000000)).put("limit",McpFields.number(1,100));}
        if(Arrays.asList("get_planning_context","list_appointments","suggest_appointment_slots","get_workweek_weather","get_weather_risks").contains(n)){
            for(String k:new String[]{"customer_id","order_id","date_from","date_to"})p.put(k,McpFields.text(128));
        }
        if(n.equals("suggest_appointment_slots")){p.put("duration_minutes",McpFields.number(5,720)).put("weather_profile",McpFields.enumeration(AppointmentRules.PROFILES));required.addAll(Arrays.asList("customer_id","duration_minutes","date_from","date_to"));}
        return McpFields.object(p,required.toArray(new String[0]));
    }
    static JSONArray tools(boolean writable)throws Exception {
        JSONArray out=new JSONArray();List<String> names=new ArrayList<>(Arrays.asList(READS));if(writable)names.addAll(Arrays.asList(WRITES));
        for(String n:names)out.put(new JSONObject().put("name",n).put("description",write(n)?"Verwaltet Einsätze der bestehenden App. Nur auf konkrete Nutzeranweisung, keine Ist-Stunden/Rechnung/Einladung. request_id bei Retry beibehalten; Updates mit aktuellem record_hash. Kalender-/Drive-Bestätigung getrennt beachten.":"Liest App-Planung und zuletzt geprüfte Kalender-/Wetterdaten. Unbekannte oder veraltete Verfügbarkeit ist keine freie Zeit. Kunden/Ort/Dauer nicht erfinden; Vorschläge buchen nichts.").put("inputSchema",schema(n)).put("annotations",new JSONObject().put("readOnlyHint",read(n)).put("idempotentHint",true).put("destructiveHint",n.equals("cancel_appointment")).put("openWorldHint",false)));return out;
    }
    static JSONObject query(String n,JSONObject a,JSONObject state)throws Exception {
        McpSchema.validate(a,schema(n));JSONObject s=PixelMcpReadTools.effective(state);
        if(n.equals("get_appointment")){JSONObject row=PixelMcpWriteTools.find(s.getJSONArray("appointments"),a.getString("appointment_id"));JSONArray work=new JSONArray();double actual=0;JSONArray logs=s.getJSONArray("worklogs");for(int i=0;i<logs.length();i++){JSONObject log=logs.getJSONObject(i);if(row.getString("id").equals(log.optString("appointment_id"))){actual+=log.optDouble("hours",0);if(work.length()<50)work.put(log);}}return new JSONObject().put("appointment",row).put("record_hash",AppointmentRules.hash(row)).put("actual_hours",Math.round(actual*100)/100.0).put("actual_work",work).put("planned_hours",(Duration.between(AppointmentRules.instant(row.getString("starts_at")),AppointmentRules.instant(row.getString("ends_at"))).toMinutes()-row.optInt("break_minutes"))/60.0);}
        JSONArray found=new JSONArray(),all=AppointmentRules.sorted(s);int total=0;
        for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(!a.optString("customer_id").isEmpty()&&!a.getString("customer_id").equals(row.optString("customer_id")))continue;if(!a.optString("order_id").isEmpty()&&!a.getString("order_id").equals(row.optString("order_id")))continue;
            String day=AppointmentRules.instant(row.getString("starts_at")).atZone(ZoneId.of(row.optString("timezone","Europe/Vienna"))).toLocalDate().toString();if(!a.optString("date_from").isEmpty()&&day.compareTo(a.getString("date_from"))<0)continue;if(!a.optString("date_to").isEmpty()&&day.compareTo(a.getString("date_to"))>0)continue;
            if(total++>=a.optInt("offset")&&found.length()<a.optInt("limit",50))found.put(new JSONObject().put("appointment",row).put("record_hash",AppointmentRules.hash(row)));}
        JSONObject result=new JSONObject().put("appointments",found).put("total",total).put("calendar_availability_verified",false);
        if(n.equals("get_planning_context")){
            JSONObject customer=a.optString("customer_id").isEmpty()?null:PixelMcpWriteTools.find(s.getJSONArray("customers"),a.getString("customer_id"));result.put("customer",customer==null?JSONObject.NULL:customer);
            if(!a.optString("order_id").isEmpty()){JSONObject order=PixelMcpWriteTools.find(s.getJSONArray("orders"),a.getString("order_id"));if(customer!=null&&!order.optString("customer_id").equals(customer.getString("id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");result.put("order",order).put("order_hash",OwnerJournalRules.version(order));if(!order.optString("offer_id").isEmpty())result.put("offer",PixelMcpWriteTools.find(s.getJSONArray("offers"),order.getString("offer_id")));}
            result.put("missing",new JSONArray().put("duration_and_customer_availability_must_be_confirmed_if_unknown"));
        }
        return result;
    }
    private AppointmentTools(){}
}
