package at.hausgarten.service;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** One explicitly authorised primary Pixel; durable online CAS reservations, never offline numbers. */
final class DriveNumbering {
    static JSONObject config(JSONObject snapshot)throws Exception {
        JSONObject s=snapshot.getJSONObject("business_settings");
        if(!"pixel_drive".equals(s.optString("invoice_numbering_authority"))||!snapshot.optString("drive_publisher_id").equals(s.optString("invoice_numbering_device_id"))||s.optString("invoice_numbering_device_id").isEmpty())
            throw new PixelMcpWriteTools.Rejected("NUMBERING_AUTHORITY_REQUIRED","Eigenständigen Pixel-Nummernkreis und bisherigen Nummernstand ausdrücklich bestätigen; kein externer Nummerndienst erforderlich.");
        String format=s.optString("invoice_number_format"),prefix=s.optString("invoice_prefix");
        if(!Arrays.asList("own","sevdesk").contains(format)||format.equals("own")&&!prefix.matches("[A-Za-z][A-Za-z0-9-]{0,19}"))throw new IllegalArgumentException("Bestätigtes Nummernformat fehlt.");
        int last=s.getInt("invoice_numbering_last_number");if(last<0||last>1000000000)throw new IllegalArgumentException("Nummernstand ungültig.");
        Instant.parse(s.getString("invoice_numbering_checked_at"));
        return new JSONObject().put("authority","pixel_drive").put("device",s.getString("invoice_numbering_device_id")).put("format",format).put("prefix",prefix).put("baseline",last).put("checked_at",s.getString("invoice_numbering_checked_at"));
    }
    static JSONObject reserve(LegacyDriveRestFixture client,JSONObject snapshot,JSONObject action)throws Exception {
        JSONObject config=config(snapshot);client.verifyConditionalWrites();String scope=PixelMcpWriteTools.hash(config),name="HausGarten-Nummernkreis-"+scope+".json";
        JSONObject file=client.unique(client.folder,name);
        if(file==null) {
            Instant checked=Instant.parse(config.getString("checked_at"));if(checked.isAfter(Instant.now().plusSeconds(300))||checked.isBefore(Instant.now().minusSeconds(86400)))throw new PixelMcpWriteTools.Rejected("NUMBERING_REVIEW_REQUIRED","Bisherigen Nummernstand vor Übernahme aktuell prüfen.");
            int maximum=observedMaximum(snapshot,config);if(config.getInt("baseline")<maximum)throw new PixelMcpWriteTools.Rejected("NUMBERING_REVIEW_REQUIRED","Bestätigter Nummernstand liegt unter vorhandenen Rechnungen.");
            JSONObject initial=new JSONObject().put("format","hausgarten.numbering.v1").put("config",config).put("last_number",config.getInt("baseline")).put("reservations",new JSONObject());
            file=client.put(client.folder,name,"application/json",PixelMcpWriteTools.canonical(initial).getBytes(StandardCharsets.UTF_8));
            client.refresh();JSONObject unique=client.unique(client.folder,name);if(unique==null||!file.getString("id").equals(unique.getString("id")))throw new PixelMcpWriteTools.Rejected("NUMBERING_REVIEW_REQUIRED","Nummernkreis ist nicht eindeutig; keine Nummer vergeben.");
        }
        LegacyDriveRestFixture.Reply read=client.readConditional(file.getString("id"));JSONObject current=read.json();
        if(!"hausgarten.numbering.v1".equals(current.optString("format"))||!scope.equals(PixelMcpWriteTools.hash(current.getJSONObject("config"))))throw new IllegalArgumentException("Nummernhoheit wurde verändert; keine Nummer vergeben.");
        JSONObject reservations=current.getJSONObject("reservations");Set<String> allocated=new HashSet<>();int ceiling=current.getInt("last_number");
        for(Iterator<String> keys=reservations.keys();keys.hasNext();){JSONObject r=reservations.getJSONObject(keys.next());if(!r.optString("action_sha256").matches("[0-9a-f]{64}")||!scope.equals(r.optString("authority"))||r.getInt("counter")>ceiling||r.getInt("counter")<=config.getInt("baseline")||!allocated.add(r.getString("invoice_number")))throw new PixelMcpWriteTools.Rejected("NUMBERING_REVIEW_REQUIRED","Nummernreservierungen widersprechen sich; keine Nummer vergeben.");}
        String id=action.getString("client_id"),hash=PixelMcpWriteTools.hash(action);
        if(reservations.has(id)){JSONObject previous=reservations.getJSONObject(id);if(!hash.equals(previous.optString("action_sha256")))throw new PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT","Reservierte Anfragekennung enthält andere Angaben.");return previous;}
        int last=current.getInt("last_number");if(last<config.getInt("baseline")||last<observedMaximum(snapshot,config)||last>=1000000000||reservations.length()>=10000)throw new PixelMcpWriteTools.Rejected("NUMBERING_REVIEW_REQUIRED","Nummernstand oder Umfang benötigt Prüfung.");
        String year=PixelMcpWriteTools.find(snapshot.getJSONArray("invoices"),action.getJSONObject("payload").getString("id")).getString("invoice_date").substring(0,4);
        String number=config.getString("format").equals("sevdesk")?"RE-"+(last+1)+"/"+year:config.getString("prefix")+"-"+year+"-"+String.format(Locale.ROOT,"%04d",last+1);
        JSONObject reservation=new JSONObject().put("invoice_number",number).put("counter",last+1).put("invoice_id",action.getJSONObject("payload").getString("id"))
            .put("action_sha256",hash).put("authority",scope).put("counter_file_id",file.getString("id"));
        reservations.put(id,reservation);current.put("last_number",last+1);
        client.compareAndWrite(file.getString("id"),read.header("ETag"),PixelMcpWriteTools.canonical(current).getBytes(StandardCharsets.UTF_8));
        JSONObject back=client.readConditional(file.getString("id")).json().getJSONObject("reservations").getJSONObject(id);
        if(!PixelMcpWriteTools.hash(reservation).equals(PixelMcpWriteTools.hash(back)))throw new IllegalArgumentException("Nummernreservierung nicht bestätigt; Retry verwendet dieselbe Anfragekennung.");
        return back;
    }
    static int observedMaximum(JSONObject snapshot,JSONObject config)throws Exception {
        int max=0;Set<String> numbers=new HashSet<>();JSONArray rows=snapshot.getJSONArray("invoices");
        String regex=config.getString("format").equals("sevdesk")?"RE-([0-9]+)(?:/[0-9]{4})?":java.util.regex.Pattern.quote(config.getString("prefix"))+"-[0-9]{4}-([0-9]+)";
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String number=row.optString("invoice_number");if(number.isEmpty()||row.optString("status","Entwurf").equals("Entwurf"))continue;
            if(!numbers.add(number))throw new PixelMcpWriteTools.Rejected("NUMBER_COLLISION","Vorhandene Rechnungsnummern sind nicht eindeutig.");
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(regex).matcher(number);if(m.matches())max=Math.max(max,Integer.parseInt(m.group(1)));
        }return max;
    }
}
