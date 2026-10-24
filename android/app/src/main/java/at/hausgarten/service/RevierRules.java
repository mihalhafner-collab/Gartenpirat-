package at.hausgarten.service;

import org.json.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Deterministic new-entity rules. No network, prices, times or historical links are guessed. */
final class RevierRules {
    static final String ROOT="revier:v1:";
    static final int ROW_BYTES=192*1024;
    static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    static PixelMcpWriteTools.Rejected error(String code,String text){return new PixelMcpWriteTools.Rejected(code,text);}
    static String text(JSONObject row,String key,int max,boolean required)throws Exception {
        Object raw=row.opt(key);if(raw==null||raw==JSONObject.NULL){if(required)throw error("INVALID_INPUT","Bitte "+key+" angeben.");return "";}
        if(!(raw instanceof String))throw error("INVALID_INPUT",key+" benötigt Text.");String value=((String)raw).trim();
        if(value.length()>max||required&&value.isEmpty())throw error("INVALID_INPUT",key+" ist leer oder zu lang.");return value;
    }
    static String id(JSONObject row,String key,boolean required)throws Exception {
        String value=text(row,key,200,required);if(!value.isEmpty()&&!value.matches("[A-Za-z0-9_.:-]{1,200}"))throw error("INVALID_INPUT","Ungültige Kennung für "+key+".");return value;
    }
    static String newId(String kind,String request)throws Exception {
        String prefix=request.startsWith("qa_")?"qa_":request.startsWith("test_")?"test_":"";
        return prefix+kind+"_"+PixelMcpWriteTools.hash(request).substring(0,28);
    }
    static String hash(JSONObject row)throws Exception {JSONObject clean=copy(row);clean.remove("record_hash");return PixelMcpWriteTools.hash(clean);}
    static JSONObject view(JSONObject row)throws Exception{return copy(row).put("record_hash",hash(row));}
    static void cas(JSONObject old,String expected)throws Exception {if(expected==null||!expected.matches("[0-9a-f]{64}")||!hash(old).equals(expected))throw error("WRITE_CONFLICT","Der Eintrag wurde inzwischen geändert. Eingaben bleiben erhalten; aktuellen Stand prüfen.");}
    static int integer(JSONObject row,String key,int min,int max,int fallback)throws Exception {
        if(!row.has(key))return fallback;Object raw=row.get(key);String value=raw.toString();if(!value.matches("-?(0|[1-9][0-9]*)"))throw error("INVALID_INPUT",key+" benötigt eine ganze Zahl.");
        try{int n=Integer.parseInt(value);if(n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException invalid){throw error("INVALID_INPUT",key+" liegt außerhalb des gültigen Bereichs.");}
    }
    static boolean bool(JSONObject row,String key,boolean fallback)throws Exception {if(!row.has(key))return fallback;if(!(row.get(key) instanceof Boolean))throw error("INVALID_INPUT",key+" benötigt Ja oder Nein.");return row.getBoolean(key);}
    static String decimal(JSONObject row,String key,boolean positive,boolean optional)throws Exception {
        if(optional&&(!row.has(key)||row.isNull(key)||row.optString(key).trim().isEmpty()))return "";Object raw=row.opt(key);
        if(!(raw instanceof String)||!((String)raw).matches("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?"))throw error("INVALID_INPUT",key+" als Dezimalzahl mit Punkt und höchstens sechs Nachkommastellen angeben.");
        BigDecimal n=new BigDecimal((String)raw);if(n.precision()>24||positive&&n.signum()<0)throw error("INVALID_INPUT","Ungültige Menge für "+key+".");return n.stripTrailingZeros().toPlainString();
    }
    static long minor(JSONObject row,String key)throws Exception {
        String value=row.get(key).toString();if(!value.matches("0|[1-9][0-9]*"))throw error("INVALID_INPUT",key+" in kleinsten Währungseinheiten angeben.");
        try{long result=new BigInteger(value).longValueExact();if(result>9007199254740991L)throw new ArithmeticException();return result;}catch(ArithmeticException invalid){throw error("INVALID_INPUT",key+" ist zu groß für die gemeinsame JSON-Schnittstelle.");}
    }
    static LocalDate date(String value)throws Exception {try{return LocalDate.parse(value);}catch(Exception invalid){throw error("INVALID_INPUT","Datum im Format JJJJ-MM-TT angeben.");}}
    static String date(JSONObject row,String key,boolean required)throws Exception {String value=text(row,key,10,required);if(!value.isEmpty())date(value);return value;}
    static String time(JSONObject row,String key)throws Exception {String value=text(row,key,5,false);if(!value.isEmpty())try{if(!value.matches("[0-9]{2}:[0-9]{2}"))throw new IllegalArgumentException();LocalTime.parse(value);}catch(Exception invalid){throw error("INVALID_INPUT","Zeitfenster als HH:mm angeben.");}return value;}
    static String instant(String value)throws Exception {try{return OffsetDateTime.parse(value).toInstant().toString();}catch(Exception invalid){throw error("INVALID_INPUT","Zeitpunkt mit Datum, Uhrzeit und Zeitzonenoffset angeben.");}}
    static void fields(JSONObject args,String... allowed)throws Exception {Set<String> names=new HashSet<>(Arrays.asList(allowed));for(Iterator<String> it=args.keys();it.hasNext();)if(!names.contains(it.next()))throw error("INVALID_INPUT","Unbekanntes Feld; Eingaben unverändert prüfen.");}
    static void bounded(JSONObject row)throws Exception {if(row.toString().getBytes(StandardCharsets.UTF_8).length>ROW_BYTES)throw error("RECORD_TOO_LARGE","Dieser technische Eintrag ist zu groß. Notizen kürzen oder mehrere getrennte Einträge verwenden.");}
    static void version(JSONObject row)throws Exception {Object version=row.opt("format_version");if(!(version instanceof Number)||!version.toString().equals("1"))throw error("REVIER_SCHEMA_UPGRADE_REQUIRED","Ein neues oder unbekanntes Datenformat benötigt eine passende Appversion. Keine Änderung vorgenommen.");}
    static boolean activeVisit(JSONObject row){return Arrays.asList("planned","in_progress","partial").contains(row.optString("status"));}
    static boolean season(JSONObject rule,LocalDate date)throws Exception {
        MonthDay day=MonthDay.from(date),start=MonthDay.parse("--"+rule.optString("season_start","01-01")),end=MonthDay.parse("--"+rule.optString("season_end","12-31"));
        return start.compareTo(end)<=0?day.compareTo(start)>=0&&day.compareTo(end)<=0:day.compareTo(start)>=0||day.compareTo(end)<=0;
    }
    static boolean occurs(JSONObject rule,LocalDate date)throws Exception {LocalDate anchor=date(rule.getString("anchor_date"));return !date.isBefore(anchor)&&ChronoUnit.DAYS.between(anchor,date)%rule.getInt("interval_days")==0&&season(rule,date);}
    static void seasonField(JSONObject row,String key)throws Exception {String value=text(row,key,5,false);if(value.isEmpty())value=key.equals("season_start")?"01-01":"12-31";try{MonthDay.parse("--"+value);}catch(Exception invalid){throw error("INVALID_INPUT","Saisonbeginn/-ende als MM-TT angeben.");}row.put(key,value);}
    private RevierRules(){}
}
