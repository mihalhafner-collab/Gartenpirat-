package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Fixed diagnostic vocabulary only. Never exposes Google bodies, accounts or tokens. */
final class CalendarAccessRules {
    static final String BASE_SCOPE="https://www.googleapis.com/auth/calendar.app.created https://www.googleapis.com/auth/calendar.calendarlist.readonly";
    static final String BUSY_SCOPE="https://www.googleapis.com/auth/calendar.freebusy";
    static String scope(boolean busy){return "oauth2:"+BASE_SCOPE+(busy?" https://www.googleapis.com/auth/calendar.freebusy":"");}
    static List<String> requestedScopes(boolean busy){List<String> values=new ArrayList<>(Arrays.asList(BASE_SCOPE.split(" ")));if(busy)values.add(BUSY_SCOPE);return Collections.unmodifiableList(values);}
    static String bindingHash(String account,String calendar,boolean busy)throws Exception{return PixelMcpWriteTools.hash(new JSONObject().put("account",account).put("calendar",calendar).put("busy",busy));}
    static String grantCode(boolean busy,String token,Collection<String> granted){
        if(token==null||token.isEmpty()||granted==null||!granted.containsAll(requestedScopes(false)))return "CALENDAR_AUTH_REQUIRED";
        return busy&&!granted.contains(BUSY_SCOPE)?"CALENDAR_BUSY_PERMISSION_REQUIRED":"";
    }
    static String googleStatus(int status){switch(status){
        case 4:case 5:return "CALENDAR_ACCOUNT_ACCESS_REQUIRED";
        case 7:case 15:case 20:case 21:case 22:return "CALENDAR_TOKEN_NETWORK";
        case 10:return "CALENDAR_APP_REGISTRATION_REQUIRED";
        case 16:case 12501:return "CALENDAR_CONSENT_CANCELLED";
        case 17:case 18:return "CALENDAR_GOOGLE_SERVICES_REQUIRED";
        default:return "CALENDAR_GOOGLE_AUTH_ERROR";
    }}
    private static final Set<String> CODES=new HashSet<>(Arrays.asList(
        "CALENDAR_AUTH_REQUIRED","CALENDAR_API_DISABLED","CALENDAR_RATE_LIMIT","CALENDAR_RETRY",
        "CALENDAR_APP_REGISTRATION_REQUIRED","CALENDAR_GOOGLE_AUTH_ERROR","CALENDAR_ACCOUNT_ACCESS_REQUIRED",
        "CALENDAR_CONSENT_CANCELLED","CALENDAR_INTERRUPTED","CALENDAR_AUTH_RESULT_MISSING","CALENDAR_GOOGLE_SERVICES_REQUIRED","CALENDAR_SCOPE_UNSUPPORTED","CALENDAR_TOKEN_NETWORK","CALENDAR_NETWORK","CALENDAR_REQUEST_REJECTED",
        "CALENDAR_CREATE_PENDING","CALENDAR_READBACK_PENDING","CALENDAR_DUPLICATE","CALENDAR_OWNER_REQUIRED",
        "CALENDAR_SETTINGS_CHANGED","CALENDAR_REPLY_INVALID","CALENDAR_BUSY_PERMISSION_REQUIRED","CALENDAR_UNKNOWN"));
    static String safeCode(String value){return "".equals(value)?"":CODES.contains(value)?value:"CALENDAR_UNKNOWN";}
    static String phase(String value){return Arrays.asList("grant","calendar_lookup","calendar_create","calendar_readback","settings_save").contains(value)?value:"grant";}
    static String phaseLabel(String value){switch(phase(value)){
        case "calendar_lookup":return "App-Kalender suchen";
        case "calendar_create":return "App-Kalender anlegen";
        case "calendar_readback":return "App-Kalender bestätigen";
        case "settings_save":return "Verbindung speichern";
        default:return "Google-Freigabe";
    }}
    static String message(String value){switch(safeCode(value)){
        case "CALENDAR_AUTH_REQUIRED":return "Google hat die Kalenderfreigabe nicht bestätigt. Erneut verbinden und die Freigabe am Pixel bestätigen.";
        case "CALENDAR_API_DISABLED":return "Die Google Calendar API ist für die App nicht freigeschaltet. Die App-Einrichtung im Google-Projekt muss korrigiert werden; wiederholtes Antippen hilft hier nicht.";
        case "CALENDAR_APP_REGISTRATION_REQUIRED":return "Google hat die Anmeldung dieser App abgewiesen. Das kann bereits vor dem Berechtigungsdialog passieren. Die App-Freigabe muss im zugehörigen Google-Projekt korrigiert werden; erneutes Bestätigen allein hilft hier nicht.";
        case "CALENDAR_GOOGLE_AUTH_ERROR":return "Google konnte die Kalenderanmeldung nicht abschließen. Google-Konto erneut wählen. Erscheint keine Freigabe, muss die App-Einrichtung bei Google geprüft werden.";
        case "CALENDAR_GOOGLE_SERVICES_REQUIRED":return "Die Google-Play-Dienste sind nicht bereit. Am Pixel aktualisieren und die Kalenderfreigabe anschließend erneut starten.";
        case "CALENDAR_SCOPE_UNSUPPORTED":return "Google hat die angefragten Kalenderrechte abgewiesen. Die Kalenderfreigabe der App muss bei Google geprüft werden.";
        case "CALENDAR_ACCOUNT_ACCESS_REQUIRED":return "Das gewählte Google-Konto ist für die App nicht zugänglich. Unter Konto und Freigaben erneut auswählen.";
        case "CALENDAR_CONSENT_CANCELLED":return "Die Google-Freigabe wurde nicht abgeschlossen. Erneut verbinden. Falls du nicht abgebrochen hast, Konto und Google-App-Einrichtung prüfen.";
        case "CALENDAR_AUTH_RESULT_MISSING":return "Google hat kein auswertbares Freigabeergebnis zurückgegeben. Erneut verbinden; bei Wiederholung Google-Konto und App-Einrichtung prüfen.";
        case "CALENDAR_INTERRUPTED":return "Die Kalenderprüfung wurde technisch unterbrochen. Bei bestehender Verbindung folgt ein erneuter Abgleich; die Einrichtung kann erneut gestartet werden.";
        case "CALENDAR_RATE_LIMIT":return "Google begrenzt gerade die Kalenderzugriffe. Später erneut versuchen; keine neue Kontofreigabe nötig.";
        case "CALENDAR_RETRY":return "Google Kalender ist vorübergehend nicht verfügbar. Später erneut versuchen.";
        case "CALENDAR_TOKEN_NETWORK":case "CALENDAR_NETWORK":return "Google ist derzeit nicht erreichbar. Internetverbindung prüfen und erneut versuchen.";
        case "CALENDAR_REQUEST_REJECTED":return "Google hat die Kalenderanlage abgewiesen. Es wurde keine erfolgreiche Anlage bestätigt. Die App-Einrichtung muss geprüft werden.";
        case "CALENDAR_CREATE_PENDING":return "Eine frühere Kalenderanlage ist noch unbestätigt. Google Kalender aktualisieren und erneut verbinden. Die App legt bis zur Klärung keinen zweiten Kalender an.";
        case "CALENDAR_READBACK_PENDING":return "Der App-Kalender ist noch nicht vollständig bestätigt. Später erneut verbinden; die bekannte Kalenderzuordnung bleibt erhalten.";
        case "CALENDAR_DUPLICATE":return "Mehrere eigene App-Kalender gefunden. Zuordnung prüfen; es wird kein weiterer Kalender angelegt.";
        case "CALENDAR_OWNER_REQUIRED":return "Der gefundene App-Kalender gehört nicht dem gewählten Konto. Das ursprüngliche Google-Konto wählen.";
        case "CALENDAR_SETTINGS_CHANGED":return "Die Planungseinstellungen wurden inzwischen geändert. Diese Ansicht erneut öffnen; die bisherige Verbindung bleibt erhalten.";
        case "CALENDAR_BUSY_PERMISSION_REQUIRED":return "Die zusätzliche Freigabe für Belegtzeiten fehlt. Alle angefragten Kalenderrechte bestätigen oder bewusst nur den App-Kalender verbinden.";
        case "CALENDAR_REPLY_INVALID":return "Google hat keine vollständig lesbare Kalenderbestätigung geliefert. Später erneut prüfen; keine doppelte Kalenderanlage.";
        default:return "Die Kalenderverbindung konnte nicht abgeschlossen werden. Erneut prüfen; der genaue Grund ist noch unbekannt.";
    }}
    static String code(Throwable error){
        if(CalendarRecoveryRules.interrupted(error))return "CALENDAR_INTERRUPTED";
        if(error instanceof DriveRestClient.Failure)return safeCode(((DriveRestClient.Failure)error).code);
        if(error instanceof PixelMcpWriteTools.Rejected)return "CALENDAR_SETTINGS_CHANGED";
        if(error instanceof JSONException)return "CALENDAR_REPLY_INVALID";
        if(error instanceof java.io.IOException)return "CALENDAR_NETWORK";
        return "CALENDAR_UNKNOWN";
    }
    static String authenticationCode(Exception error){
        if(CalendarRecoveryRules.interrupted(error))return "CALENDAR_INTERRUPTED";
        String type=error.getClass().getSimpleName();
        if(type.equals("OperationCanceledException"))return "CALENDAR_CONSENT_CANCELLED";
        if(error instanceof SecurityException)return "CALENDAR_ACCOUNT_ACCESS_REQUIRED";
        // Compare known machine identifiers in memory; never retain or display exception text.
        String detail=String.valueOf(error.getMessage()).toUpperCase(Locale.ROOT);
        if(detail.contains("UNREGISTERED_ON_API_CONSOLE")||detail.contains("DEVELOPER_ERROR")||detail.contains("INVALID_CLIENT"))return "CALENDAR_APP_REGISTRATION_REQUIRED";
        if(detail.contains("INVALID_SCOPE")||detail.contains("INVALIDSCOPES"))return "CALENDAR_SCOPE_UNSUPPORTED";
        if(error instanceof java.io.IOException)return "CALENDAR_TOKEN_NETWORK";
        return "CALENDAR_GOOGLE_AUTH_ERROR";
    }
    static String missingResultCode(int result){return result==0?"CALENDAR_CONSENT_CANCELLED":"CALENDAR_AUTH_RESULT_MISSING";}
    static boolean rejectedCreation(DriveRestClient.Failure error){return Arrays.asList(
        "CALENDAR_AUTH_REQUIRED","CALENDAR_API_DISABLED","CALENDAR_RATE_LIMIT","CALENDAR_REQUEST_REJECTED",
        "CALENDAR_APP_REGISTRATION_REQUIRED","CALENDAR_GOOGLE_AUTH_ERROR","CALENDAR_ACCOUNT_ACCESS_REQUIRED",
        "CALENDAR_CONSENT_CANCELLED","CALENDAR_GOOGLE_SERVICES_REQUIRED","CALENDAR_SCOPE_UNSUPPORTED","CALENDAR_BUSY_PERMISSION_REQUIRED","CALENDAR_TOKEN_NETWORK").contains(error.code);}
    static Set<String> reasons(DriveRestClient.Reply reply){
        Set<String> reasons=new HashSet<>();try{
            JSONObject error=reply.json().optJSONObject("error");if(error==null)return reasons;
            JSONArray errors=error.optJSONArray("errors");for(int i=0;errors!=null&&i<errors.length();i++)if(errors.optJSONObject(i)!=null)reasons.add(errors.getJSONObject(i).optString("reason"));
            JSONArray details=error.optJSONArray("details");for(int i=0;details!=null&&i<details.length();i++)if(details.optJSONObject(i)!=null)reasons.add(details.getJSONObject(i).optString("reason"));
        }catch(Exception ignored){}return reasons;
    }
    static DriveRestClient.Failure failure(DriveRestClient.Reply reply){
        Set<String> reasons=reasons(reply);String code=null;boolean user=false;long retry=0;
        if(reply.status==401){code="CALENDAR_AUTH_REQUIRED";user=true;}
        else if(reply.status==403){
            if(!Collections.disjoint(reasons,Arrays.asList("accessNotConfigured","SERVICE_DISABLED","API_DISABLED"))){code="CALENDAR_API_DISABLED";user=true;}
            else if(!Collections.disjoint(reasons,Arrays.asList("rateLimitExceeded","userRateLimitExceeded","quotaExceeded","dailyLimitExceeded","RATE_LIMIT_EXCEEDED","QUOTA_EXCEEDED")))code="CALENDAR_RATE_LIMIT";
            else{code="CALENDAR_AUTH_REQUIRED";user=true;}
        }else if(reply.status==429)code="CALENDAR_RATE_LIMIT";
        else if(reply.status>=500)code="CALENDAR_RETRY";
        if(code==null)return null;
        if(!user){retry=30000;try{retry=Math.min(3600000,Math.max(retry,Long.parseLong(reply.header("Retry-After"))*1000));}catch(Exception absent){try{retry=Math.min(3600000,Math.max(retry,java.time.Duration.between(java.time.Instant.now(),java.time.ZonedDateTime.parse(reply.header("Retry-After"),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()));}catch(Exception ignored){}}}
        return new DriveRestClient.Failure(code,message(code),retry,user);
    }
    private CalendarAccessRules(){}
}
