package at.hausgarten.service;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/** Bounded Calendar API transport. Caller persists intent before any network request. */
final class CalendarClient {
    static final String API="https://www.googleapis.com/calendar/v3/";
    interface Token {String get()throws Exception;default void invalidate(String rejected)throws Exception{}}
    final DriveRestClient.Wire wire;final Token token;
    CalendarClient(Token token,DriveRestClient.Wire wire){this.token=token;this.wire=wire;}
    static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    DriveRestClient.Reply send(String method,String path,JSONObject body,String etag)throws Exception {
        Map<String,String> headers=new HashMap<>();String credential=token.get();headers.put("Authorization","Bearer "+credential);headers.put("Accept","application/json");
        if(body!=null)headers.put("Content-Type","application/json; charset=UTF-8");
        if(etag!=null){if(etag.isEmpty()||etag.equals("*")||etag.contains("\r")||etag.contains("\n"))throw new IllegalArgumentException("Google liefert keinen sicheren Vergleichsstand.");headers.put("If-Match",etag);}
        DriveRestClient.Reply r=wire.send(method,API+path,headers,body==null?null:body.toString().getBytes(StandardCharsets.UTF_8),3*1024*1024);
        if(r.status==401)token.invalidate(credential);
        if(r.status==401||r.status==403)throw new DriveRestClient.Failure("CALENDAR_AUTH_REQUIRED","Google-Kalenderrechte am Gerät prüfen und erneut verbinden.",0,true);
        if(r.status==429||r.status>=500){long delay=30000;try{delay=Math.min(3600000,Math.max(delay,Long.parseLong(r.header("Retry-After"))*1000));}catch(Exception ignored){try{delay=Math.min(3600000,Math.max(delay,java.time.Duration.between(java.time.Instant.now(),java.time.ZonedDateTime.parse(r.header("Retry-After"),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()));}catch(Exception absent){}}throw new DriveRestClient.Failure("CALENDAR_RETRY","Google Kalender vorübergehend nicht erreichbar.",delay,false);}
        if(r.status==412)throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Google-Termin parallel verändert. Beide Varianten bleiben erhalten.");
        return r;
    }
    JSONArray calendars()throws Exception {
        JSONArray all=new JSONArray();String page="";Set<String> seen=new HashSet<>();
        do{DriveRestClient.Reply r=send("GET","users/me/calendarList?maxResults=250"+(page.isEmpty()?"":"&pageToken="+enc(page)),null,null);if(r.status!=200)throw new java.io.IOException("Kalenderliste nicht bestätigt.");JSONObject data=r.json();JSONArray rows=data.optJSONArray("items");for(int i=0;rows!=null&&i<rows.length();i++){if(all.length()>=2000)throw new IllegalArgumentException("Kalenderliste zu groß.");all.put(rows.getJSONObject(i));}page=data.optString("nextPageToken");if(!page.isEmpty()&&!seen.add(page))throw new java.io.IOException("Wiederholte Kalenderseite.");}while(!page.isEmpty());return all;
    }
    JSONObject createCalendar(String marker)throws Exception {
        DriveRestClient.Reply r=send("POST","calendars",new JSONObject().put("summary","Haus & Garten – Einsätze").put("description",marker).put("timeZone","Europe/Vienna"),null);
        if(r.status!=200&&r.status!=201)throw new java.io.IOException("Kalenderanlage unbestätigt. Vor Wiederholung vorhandene Kalender prüfen.");return r.json();
    }
    interface CalendarSetup {boolean pending();void pending(boolean value)throws Exception;}
    JSONObject ensureOwnCalendar(String marker,CalendarSetup state)throws Exception {
        if(marker==null||marker.isEmpty())throw new IllegalArgumentException("Kalenderzuordnung fehlt.");
        JSONArray rows=calendars();JSONObject found=null;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(marker.equals(row.optString("description"))){if(found!=null)throw new IllegalArgumentException("Mehrere App-Kalender gefunden. Zuordnung prüfen; kein weiterer Kalender wird angelegt.");if(!"owner".equals(row.optString("accessRole")))throw new IllegalArgumentException("Eigener App-Kalender benötigt Eigentümerfreigabe.");found=row;}}
        if(found==null){if(state.pending())throw new java.io.IOException("Frühere Kalenderanlage noch nicht bestätigt. Erneut prüfen; kein zweiter Kalender wird angelegt.");state.pending(true);found=createCalendar(marker);}
        String id=found.getString("id");DriveRestClient.Reply read=send("GET","calendars/"+enc(id),null,null);
        if(read.status!=200||!id.equals(read.json().optString("id"))||!marker.equals(read.json().optString("description")))throw new java.io.IOException("Eigener Kalender konnte noch nicht bestätigt werden.");
        state.pending(false);return found;
    }
    String path(String calendar,String event)throws Exception{return "calendars/"+enc(calendar)+"/events"+(event.isEmpty()?"":"/"+enc(event));}
    JSONObject get(String calendar,String id)throws Exception {DriveRestClient.Reply r=send("GET",path(calendar,id),null,null);if(r.status==404||r.status==410)return null;if(r.status!=200)throw new java.io.IOException("Kalendereintrag nicht lesbar.");return r.json();}
    JSONObject save(String calendar,JSONObject a,JSONObject remote)throws Exception {
        return save(calendar,a,remote,"");
    }
    JSONObject save(String calendar,JSONObject a,JSONObject remote,String customer)throws Exception {
        JSONObject body=CalendarRules.event(a,customer);String id=body.getString("id");
        if(remote!=null){if(remote.has("recurrence"))body.put("recurrence",JSONObject.NULL);for(String edge:new String[]{"start","end"})if(remote.optJSONObject(edge)!=null&&remote.getJSONObject(edge).has("date"))body.getJSONObject(edge).put("date",JSONObject.NULL);}
        if(remote!=null)CalendarRules.requireIdentity(remote,a.getString("id"));
        DriveRestClient.Reply r=send(remote==null?"POST":"PATCH",path(calendar,remote==null?"":id)+"?sendUpdates=none",body,remote==null?null:remote.getString("etag"));
        if(r.status==409){JSONObject prior=get(calendar,id);if(prior==null)throw new java.io.IOException("Google-ID noch nicht lesbar. Dieselbe Anfrage wiederholen.");CalendarRules.requireIdentity(prior,a.getString("id"));return prior;}
        if(r.status!=200&&r.status!=201)throw new java.io.IOException("Google-Kalenderspeicherung nicht bestätigt.");JSONObject saved=get(calendar,id);if(saved==null)throw new java.io.IOException("Google-Kalender konnte nicht zurückgelesen werden.");return saved;
    }
    void remove(String calendar,String id,JSONObject remote)throws Exception {
        if(remote==null||"cancelled".equals(remote.optString("status")))return;
        DriveRestClient.Reply r=send("DELETE",path(calendar,id)+"?sendUpdates=none",null,remote.getString("etag"));if(r.status!=204&&r.status!=404&&r.status!=410)throw new java.io.IOException("Kalenderabsage noch nicht bestätigt.");
        JSONObject after=get(calendar,id);if(after!=null&&!after.optString("status").equals("cancelled"))throw new java.io.IOException("Kalenderabsage nicht zurückgelesen.");
    }
    JSONObject changes(String calendar,String syncToken)throws Exception {
        JSONArray all=new JSONArray();String page="",next="";Set<String> seen=new HashSet<>();int pages=0;
        do{if(++pages>40)throw new java.io.IOException("Kalenderabgleich zu groß; bestehender Stand bleibt erhalten.");
            DriveRestClient.Reply r=send("GET",path(calendar,"")+"?maxResults=250&showDeleted=true"+(syncToken.isEmpty()?"":"&syncToken="+enc(syncToken))+(page.isEmpty()?"":"&pageToken="+enc(page)),null,null);
            if(r.status==410)return new JSONObject().put("reset_required",true);if(r.status!=200)throw new java.io.IOException("Kalenderänderungen nicht bestätigt.");JSONObject data=r.json();JSONArray rows=data.optJSONArray("items");for(int i=0;rows!=null&&i<rows.length();i++)all.put(rows.getJSONObject(i));next=data.optString("nextSyncToken");page=data.optString("nextPageToken");if(!page.isEmpty()&&!seen.add(page))throw new java.io.IOException("Wiederholte Kalenderseite.");
        }while(!page.isEmpty());if(next.isEmpty())throw new java.io.IOException("Google-Synctoken fehlt.");return new JSONObject().put("events",all).put("next_sync_token",next);
    }
    JSONObject busy(JSONArray ids,String from,String to)throws Exception {
        JSONArray items=new JSONArray();for(int i=0;i<ids.length();i++)items.put(new JSONObject().put("id",ids.getString(i)));
        DriveRestClient.Reply r=send("POST","freeBusy",new JSONObject().put("timeMin",from).put("timeMax",to).put("timeZone","Europe/Vienna").put("items",items),null);
        if(r.status!=200)throw new java.io.IOException("Belegtzeiten nicht bestätigt.");JSONObject data=r.json(),calendars=data.optJSONObject("calendars");if(calendars==null)throw new java.io.IOException("Belegtzeiten fehlen.");JSONArray result=new JSONArray();
        for(int i=0;i<ids.length();i++){JSONObject c=calendars.optJSONObject(ids.getString(i));if(c==null||c.has("errors"))throw new DriveRestClient.Failure("CALENDAR_BUSY_PERMISSION_REQUIRED","Belegtzeiten eines gewählten Kalenders nicht lesbar.",0,true);JSONArray b=c.getJSONArray("busy");for(int j=0;j<b.length();j++)result.put(b.getJSONObject(j));}
        return new JSONObject().put("busy",result).put("from",from).put("to",to).put("checked_at",java.time.Instant.now().toString()).put("verified",true);
    }
}
