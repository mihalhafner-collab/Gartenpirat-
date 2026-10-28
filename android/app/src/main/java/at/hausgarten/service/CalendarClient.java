package at.hausgarten.service;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/** Bounded Calendar API transport. Caller persists intent before any network request. */
final class CalendarClient {
    static final String API="https://www.googleapis.com/calendar/v3/";
    interface Token {String get()throws Exception;default void invalidate(String rejected)throws Exception{}}
    final HttpTransport.Wire wire;final Token token;
    CalendarClient(Token token,HttpTransport.Wire wire){this.token=token;this.wire=wire;}
    static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    HttpTransport.Reply send(String method,String path,JSONObject body,String etag)throws Exception {
        Map<String,String> headers=new HashMap<>();headers.put("Accept","application/json");
        if(body!=null)headers.put("Content-Type","application/json; charset=UTF-8");
        if(etag!=null){if(etag.isEmpty()||etag.equals("*")||etag.contains("\r")||etag.contains("\n"))throw new IllegalArgumentException("Google liefert keinen sicheren Vergleichsstand.");headers.put("If-Match",etag);}
        HttpTransport.Reply r=null;
        for(int attempt=0;attempt<2;attempt++){
            String credential=token.get();headers.put("Authorization","Bearer "+credential);
            r=wire.send(method,API+path,headers,body==null?null:body.toString().getBytes(StandardCharsets.UTF_8),3*1024*1024);
            if(r.status!=401)break;
            token.invalidate(credential); // A 401 rejects the request; one fresh-token retry is safe.
        }
        HttpTransport.Failure failure=CalendarAccessRules.failure(r);if(failure!=null)throw failure;
        if(r.status==412)throw new PixelMcpWriteTools.Rejected("CALENDAR_WRITE_CONFLICT","Google-Termin parallel verändert. Beide Varianten bleiben erhalten.");
        return r;
    }
    JSONArray calendars()throws Exception {
        JSONArray all=new JSONArray();String page="";Set<String> seen=new HashSet<>();
        do{HttpTransport.Reply r=send("GET","users/me/calendarList?maxResults=250"+(page.isEmpty()?"":"&pageToken="+enc(page)),null,null);if(r.status!=200)throw new java.io.IOException("Kalenderliste nicht bestätigt.");JSONObject data=r.json();JSONArray rows=data.optJSONArray("items");for(int i=0;rows!=null&&i<rows.length();i++){if(all.length()>=2000)throw new IllegalArgumentException("Kalenderliste zu groß.");all.put(rows.getJSONObject(i));}page=data.optString("nextPageToken");if(!page.isEmpty()&&!seen.add(page))throw new java.io.IOException("Wiederholte Kalenderseite.");}while(!page.isEmpty());return all;
    }
    JSONObject createCalendar(String marker)throws Exception {
        HttpTransport.Reply r=send("POST","calendars",new JSONObject().put("summary","Haus & Garten – Einsätze").put("description",marker).put("timeZone","Europe/Vienna"),null);
        if(r.status>=400&&r.status<500&&r.status!=408)throw new HttpTransport.Failure("CALENDAR_REQUEST_REJECTED",CalendarAccessRules.message("CALENDAR_REQUEST_REJECTED"),0,true);
        if(r.status!=200&&r.status!=201)throw new java.io.IOException("Kalenderanlage unbestätigt. Vor Wiederholung vorhandene Kalender prüfen.");return r.json();
    }
    interface CalendarSetup {boolean pending();void pending(boolean value)throws Exception;default String confirmedId(){return "";}default void confirmedId(String id)throws Exception{}default void phase(String value){}}
    JSONObject ensureOwnCalendar(String marker,CalendarSetup state)throws Exception {synchronized(CalendarClient.class){
        if(marker==null||marker.isEmpty())throw new IllegalArgumentException("Kalenderzuordnung fehlt.");
        state.phase("calendar_lookup");JSONArray rows=calendars();JSONObject found=null;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(marker.equals(row.optString("description"))){if(found!=null)throw new HttpTransport.Failure("CALENDAR_DUPLICATE",CalendarAccessRules.message("CALENDAR_DUPLICATE"),0,true);if(!"owner".equals(row.optString("accessRole")))throw new HttpTransport.Failure("CALENDAR_OWNER_REQUIRED",CalendarAccessRules.message("CALENDAR_OWNER_REQUIRED"),0,true);found=row;}}
        if(found==null&&!state.confirmedId().isEmpty()){state.phase("calendar_readback");HttpTransport.Reply known=send("GET","calendars/"+enc(state.confirmedId()),null,null);if(known.status!=200||!marker.equals(known.json().optString("description")))throw new HttpTransport.Failure("CALENDAR_READBACK_PENDING",CalendarAccessRules.message("CALENDAR_READBACK_PENDING"),30000,false);found=known.json();}
        if(found==null){
            if(state.pending())throw new HttpTransport.Failure("CALENDAR_CREATE_PENDING",CalendarAccessRules.message("CALENDAR_CREATE_PENDING"),0,true);
            state.phase("calendar_create");state.pending(true);
            try{found=createCalendar(marker);}catch(HttpTransport.Failure rejected){if(CalendarAccessRules.rejectedCreation(rejected))state.pending(false);throw rejected;}
            state.confirmedId(found.getString("id"));
        }
        state.phase("calendar_readback");
        String id=found.getString("id");HttpTransport.Reply read=send("GET","calendars/"+enc(id),null,null);
        if(read.status!=200||!id.equals(read.json().optString("id"))||!marker.equals(read.json().optString("description")))throw new HttpTransport.Failure("CALENDAR_READBACK_PENDING",CalendarAccessRules.message("CALENDAR_READBACK_PENDING"),30000,false);
        state.confirmedId(id);state.pending(false);return found;
    }}
    String path(String calendar,String event)throws Exception{return "calendars/"+enc(calendar)+"/events"+(event.isEmpty()?"":"/"+enc(event));}
    JSONObject get(String calendar,String id)throws Exception {HttpTransport.Reply r=send("GET",path(calendar,id),null,null);if(r.status==404||r.status==410)return null;if(r.status!=200)throw new java.io.IOException("Kalendereintrag nicht lesbar.");return r.json();}
    JSONObject save(String calendar,JSONObject a,JSONObject remote)throws Exception {
        return save(calendar,a,remote,"");
    }
    JSONObject save(String calendar,JSONObject a,JSONObject remote,String customer)throws Exception {
        JSONObject body=CalendarRules.event(a,customer);String id=body.getString("id");
        if(remote!=null){if(remote.has("recurrence"))body.put("recurrence",JSONObject.NULL);for(String edge:new String[]{"start","end"})if(remote.optJSONObject(edge)!=null&&remote.getJSONObject(edge).has("date"))body.getJSONObject(edge).put("date",JSONObject.NULL);}
        if(remote!=null)CalendarRules.requireIdentity(remote,a.getString("id"));
        HttpTransport.Reply r=send(remote==null?"POST":"PATCH",path(calendar,remote==null?"":id)+"?sendUpdates=none",body,remote==null?null:remote.getString("etag"));
        if(r.status==409){JSONObject prior=get(calendar,id);if(prior==null)throw new java.io.IOException("Google-ID noch nicht lesbar. Dieselbe Anfrage wiederholen.");CalendarRules.requireIdentity(prior,a.getString("id"));return prior;}
        if(r.status!=200&&r.status!=201)throw new java.io.IOException("Google-Kalenderspeicherung nicht bestätigt.");JSONObject saved=get(calendar,id);if(saved==null)throw new java.io.IOException("Google-Kalender konnte nicht zurückgelesen werden.");return saved;
    }
    void remove(String calendar,String id,JSONObject remote)throws Exception {
        if(remote==null||"cancelled".equals(remote.optString("status")))return;
        HttpTransport.Reply r=send("DELETE",path(calendar,id)+"?sendUpdates=none",null,remote.getString("etag"));if(r.status!=204&&r.status!=404&&r.status!=410)throw new java.io.IOException("Kalenderabsage noch nicht bestätigt.");
        JSONObject after=get(calendar,id);if(after!=null&&!after.optString("status").equals("cancelled"))throw new java.io.IOException("Kalenderabsage nicht zurückgelesen.");
    }
    JSONObject changes(String calendar,String syncToken)throws Exception {
        JSONArray all=new JSONArray();String page="",next="";Set<String> seen=new HashSet<>();int pages=0;
        do{if(++pages>40)throw new java.io.IOException("Kalenderabgleich zu groß; bestehender Stand bleibt erhalten.");
            HttpTransport.Reply r=send("GET",path(calendar,"")+"?maxResults=250&showDeleted=true"+(syncToken.isEmpty()?"":"&syncToken="+enc(syncToken))+(page.isEmpty()?"":"&pageToken="+enc(page)),null,null);
            if(r.status==410)return new JSONObject().put("reset_required",true);if(r.status!=200)throw new java.io.IOException("Kalenderänderungen nicht bestätigt.");JSONObject data=r.json();JSONArray rows=data.optJSONArray("items");for(int i=0;rows!=null&&i<rows.length();i++)all.put(rows.getJSONObject(i));next=data.optString("nextSyncToken");page=data.optString("nextPageToken");if(!page.isEmpty()&&!seen.add(page))throw new java.io.IOException("Wiederholte Kalenderseite.");
        }while(!page.isEmpty());if(next.isEmpty())throw new java.io.IOException("Google-Synctoken fehlt.");return new JSONObject().put("events",all).put("next_sync_token",next);
    }
    JSONObject busy(JSONArray ids,String from,String to)throws Exception {
        JSONArray items=new JSONArray();for(int i=0;i<ids.length();i++)items.put(new JSONObject().put("id",ids.getString(i)));
        HttpTransport.Reply r=send("POST","freeBusy",new JSONObject().put("timeMin",from).put("timeMax",to).put("timeZone","Europe/Vienna").put("items",items),null);
        if(r.status!=200)throw new java.io.IOException("Belegtzeiten nicht bestätigt.");JSONObject data=r.json(),calendars=data.optJSONObject("calendars");if(calendars==null)throw new java.io.IOException("Belegtzeiten fehlen.");JSONArray result=new JSONArray();
        for(int i=0;i<ids.length();i++){JSONObject c=calendars.optJSONObject(ids.getString(i));if(c==null||c.has("errors"))throw new HttpTransport.Failure("CALENDAR_BUSY_PERMISSION_REQUIRED","Belegtzeiten eines gewählten Kalenders nicht lesbar.",0,true);JSONArray b=c.getJSONArray("busy");for(int j=0;j<b.length();j++)result.put(b.getJSONObject(j));}
        return new JSONObject().put("busy",result).put("from",from).put("to",to).put("checked_at",java.time.Instant.now().toString()).put("verified",true);
    }
}
