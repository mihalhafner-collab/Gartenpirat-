package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

/** The same deterministic planning rules apply to UI, MCP and Drive replay. */
final class AppointmentRules {
    static final String[] FIELDS={"customer_id","order_id","title","purpose","starts_at","ends_at","timezone","location","latitude","longitude","weather_profile","status","notes","travel_minutes","break_minutes","flexible_from","flexible_until","series_id","scope_refs"};
    static final String[] PROFILES={"general","lawn","hedge","planting","inspection","indoor"};
    static String hash(JSONObject row)throws Exception{return PixelMcpWriteTools.hash(DriveJournalRules.clean(row));}
    static boolean active(JSONObject row){return !Arrays.asList("cancelled","completed").contains(row.optString("status"));}
    static Instant instant(String value)throws Exception{try{return OffsetDateTime.parse(value).toInstant();}catch(Exception bad){throw new IllegalArgumentException("Beginn und Ende benötigen Datum, Uhrzeit und Zeitzonenoffset.");}}
    static Instant local(String value,String zone,String offset)throws Exception {
        LocalDateTime time=LocalDateTime.parse(value);ZoneId id=ZoneId.of(zone);List<ZoneOffset> valid=id.getRules().getValidOffsets(time);
        if(valid.isEmpty())throw new IllegalArgumentException("Diese Uhrzeit existiert wegen der Zeitumstellung nicht.");
        if(valid.size()>1&&offset.isEmpty())throw new IllegalArgumentException("Diese Uhrzeit gibt es zweimal. Bitte den Zeitzonenoffset auswählen.");
        ZoneOffset chosen=offset.isEmpty()?valid.get(0):ZoneOffset.of(offset);if(!valid.contains(chosen))throw new IllegalArgumentException("Zeitzonenoffset passt nicht zur Ortszeit.");return time.toInstant(chosen);
    }
    static void validate(JSONObject row,JSONObject old,JSONObject s)throws Exception {
        JSONObject customer=OwnerJournalRules.findOrNull(s.getJSONArray("customers"),row.optString("customer_id"));
        if(customer==null||(old==null&&customer.optInt("active",1)==0))throw new IllegalArgumentException("Bitte einen vorhandenen aktiven Einsatzkunden wählen.");
        String orderId=row.optString("order_id");if(!orderId.isEmpty()){
            JSONObject order=PixelMcpWriteTools.find(s.getJSONArray("orders"),orderId);
            if(!row.getString("customer_id").equals(order.optString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
        }
        if(row.optString("title").trim().isEmpty())throw new IllegalArgumentException("Bitte eine Tätigkeit eintragen.");
        ZoneId zone=ZoneId.of(row.optString("timezone","Europe/Vienna"));row.put("timezone",zone.getId());
        Instant start=instant(row.getString("starts_at")),end=instant(row.getString("ends_at"));
        long minutes=Duration.between(start,end).toMinutes();if(minutes<5||minutes>24*60)throw new IllegalArgumentException("Ein Einsatz dauert mindestens5Minuten und höchstens24Stunden. Mehrtägige Arbeit bitte auf Einsätze aufteilen.");
        row.put("starts_at",start.toString()).put("ends_at",end.toString());
        if(!row.has("status"))row.put("status","tentative");
        if(!Arrays.asList("draft","tentative","confirmed","completed","cancelled").contains(row.optString("status")))throw new IllegalArgumentException("Ungültiger Terminstatus.");
        if(!row.has("purpose"))row.put("purpose","work");if(!Arrays.asList("work","inspection").contains(row.optString("purpose")))throw new IllegalArgumentException("Ungültiger Terminzweck.");
        if(!row.has("weather_profile"))row.put("weather_profile","general");if(!Arrays.asList(PROFILES).contains(row.optString("weather_profile")))throw new IllegalArgumentException("Wetterprofil bitte bewusst wählen.");
        if(row.has("scope_refs")){
            JSONArray refs=row.optJSONArray("scope_refs");if(refs==null||refs.length()>200)throw new IllegalArgumentException("Ungültige Leistungsreferenzen.");Set<String> seen=new HashSet<>();
            for(int i=0;i<refs.length();i++){String ref=refs.getString(i);int split=ref.lastIndexOf('#');if(split<1||!seen.add(ref))throw new IllegalArgumentException("Leistungsreferenz als Angebots-ID#Positionsindex angeben.");JSONObject offer=PixelMcpWriteTools.find(s.getJSONArray("offers"),ref.substring(0,split));int position=Integer.parseInt(ref.substring(split+1));if(!offer.optString("status").equals("accepted")||!row.getString("customer_id").equals(offer.optString("customer_id"))||orderId.isEmpty()||!orderId.equals(offer.optString("converted_order_id",offer.optString("order_id")))||position<0||position>=offer.getJSONArray("positions").length())throw new IllegalArgumentException("Leistung gehört nicht zu einem angenommenen Angebot dieses Auftrags.");}
        }
        for(String key:new String[]{"travel_minutes","break_minutes"}){int n=row.optInt(key,0);if(n<0||n>720)throw new IllegalArgumentException("Pause/Anfahrt muss zwischen0und720Minuten liegen.");row.put(key,n);}
        if(row.getInt("break_minutes")>=minutes)throw new IllegalArgumentException("Die Pause muss kürzer als der Einsatz sein.");
        boolean lat=row.has("latitude")&&!row.isNull("latitude"),lon=row.has("longitude")&&!row.isNull("longitude");
        if(lat!=lon)throw new IllegalArgumentException("Breiten- und Längengrad gemeinsam angeben.");
        if(lat&&(!Double.isFinite(row.getDouble("latitude"))||Math.abs(row.getDouble("latitude"))>90||!Double.isFinite(row.getDouble("longitude"))||Math.abs(row.getDouble("longitude"))>180))throw new IllegalArgumentException("Ungültige Einsatzkoordinaten.");
        for(String field:new String[]{"flexible_from","flexible_until"})if(!row.optString(field).isEmpty())LocalDate.parse(row.getString(field));
        if(!row.optString("flexible_from").isEmpty()&&start.atZone(zone).toLocalDate().isBefore(LocalDate.parse(row.getString("flexible_from"))))throw new IllegalArgumentException("Termin liegt vor dem vereinbarten Ausführungsfenster.");
        if(!row.optString("flexible_until").isEmpty()&&end.minusMillis(1).atZone(zone).toLocalDate().isAfter(LocalDate.parse(row.getString("flexible_until"))))throw new IllegalArgumentException("Termin liegt nach dem vereinbarten Ausführungsfenster.");
        if(old!=null&&(!old.optString("customer_id").equals(row.optString("customer_id"))||!old.optString("order_id").equals(orderId)))throw new IllegalArgumentException("Die ursprüngliche Kundenzuordnung bleibt erhalten. Bei falschem Kontext Termin absagen und neu planen.");
        if(old!=null&&old.optString("status").equals("cancelled")&&!row.optString("status").equals("cancelled"))throw new IllegalArgumentException("Abgesagten Termin nicht still reaktivieren. Bitte einen neuen Termin planen.");
        if(old!=null&&!old.optString("status").equals("draft")&&row.optString("status").equals("draft"))throw new IllegalArgumentException("Geplanten Termin verschieben oder ausdrücklich absagen; nicht zum Entwurf zurückstufen.");
        if(!active(row)||row.optString("status").equals("draft"))return;
        JSONArray all=s.optJSONArray("appointments");
        for(int i=0;all!=null&&i<all.length();i++){
            JSONObject other=all.getJSONObject(i);if(row.getString("id").equals(other.optString("id"))||!active(other)||other.optString("status").equals("draft"))continue;
            Instant a=instant(other.getString("starts_at")).minusSeconds(other.optInt("travel_minutes")*60L),b=instant(other.getString("ends_at"));
            if(start.minusSeconds(row.getInt("travel_minutes")*60L).isBefore(b)&&end.isAfter(a))throw new PixelMcpWriteTools.Rejected("APPOINTMENT_OVERLAP","Zeit oder Anfahrt überschneidet sich mit einem vorhandenen Einsatz. Bitte eine freie Zeit wählen.");
        }
    }
    static JSONObject prepare(String name,JSONObject a,JSONObject s,String request,String stamp)throws Exception {
        boolean create=name.equals("create_appointment");JSONObject old=create?null:PixelMcpWriteTools.find(s.getJSONArray("appointments"),a.getString("appointment_id"));
        if(old!=null&&!hash(old).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Termin wurde inzwischen geändert. Eingaben bleiben erhalten.");
        JSONObject row=old==null?new JSONObject():DriveJournalRules.clean(old),changes=create?a:a.optJSONObject("updates");
        if(changes!=null)for(String key:FIELDS)if(changes.has(key))row.put(key,changes.get(key));
        if(create)row.put("id","apt_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("created_at",stamp).put("source","owner_app_mcp");
        if(name.equals("cancel_appointment"))row.put("status","cancelled");row.put("updated_at",stamp);validate(row,old,s);
        if(old==null)row.put("_mcp_create",true);else row.put("_mcp_base",DriveJournalRules.guard(old,"appointment"));return row;
    }
    static JSONArray sorted(JSONObject s)throws Exception{List<JSONObject> rows=new ArrayList<>();JSONArray all=s.optJSONArray("appointments");for(int i=0;all!=null&&i<all.length();i++)rows.add(all.getJSONObject(i));rows.sort(Comparator.comparing(r->r.optString("starts_at")));return new JSONArray(rows);}
    static void protectParent(JSONObject s,String entity,String id)throws Exception {
        if(!entity.equals("customer")&&!entity.equals("order"))return;JSONArray rows=s.optJSONArray("appointments");
        for(int i=0;rows!=null&&i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString(entity+"_id")))throw new IllegalArgumentException("Zugeordnete Termine zuerst in den Papierkorb verschieben. Terminverlauf und Kundenbeziehungen bleiben geschützt.");
    }
    static void worklog(JSONObject row,JSONObject old,JSONObject snapshot)throws Exception {
        String id=row.optString("appointment_id");if(old!=null&&!old.optString("appointment_id").isEmpty()&&!old.optString("appointment_id").equals(id))throw new IllegalArgumentException("Die ursprüngliche Terminreferenz der Arbeit bleibt erhalten.");
        if(id.isEmpty())return;JSONObject a=PixelMcpWriteTools.find(snapshot.getJSONArray("appointments"),id);
        if(!a.getString("customer_id").equals(row.optString("customer_id"))||!a.optString("order_id").equals(row.optString("order_id")))throw new IllegalArgumentException("Arbeit und Termin gehören zu verschiedenen Kunden/Aufträgen.");
    }
    private AppointmentRules(){}
}
