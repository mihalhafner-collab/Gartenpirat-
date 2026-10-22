package at.hausgarten.service;

import java.util.*;

/** Presentation of observed setup steps; account selection never grants Calendar access. */
final class CalendarSetupRules {
    static boolean grantVerified(boolean existingGrant,String stage,String code){
        if(Arrays.asList("CALENDAR_APP_REGISTRATION_REQUIRED","CALENDAR_AUTH_REQUIRED","CALENDAR_BUSY_PERMISSION_REQUIRED","CALENDAR_SCOPE_UNSUPPORTED","CALENDAR_ACCOUNT_ACCESS_REQUIRED","CALENDAR_CONSENT_CANCELLED","CALENDAR_AUTH_RESULT_MISSING","CALENDAR_GOOGLE_AUTH_ERROR").contains(code))return false;
        return existingGrant||Arrays.asList("calendar_lookup","calendar_create","calendar_readback","settings_save").contains(stage);
    }
    static String progress(boolean accountSelected,boolean existingGrant,boolean calendarConfirmed,String stage,String code,boolean waitingForGoogle){
        return "1 Konto "+(accountSelected?"gewählt":"offen")+" · 2 Freigabe "+(waitingForGoogle?"bei Google bestätigen":grantVerified(existingGrant,stage,code)?"erteilt":"ausstehend")+" · 3 Kalender "+(calendarConfirmed?"bestätigt":"offen");
    }
    static String registrationChecklist(String packageName,String sha1){
        return "Google-Projekt prüfen: Calendar API aktiviert; Android-OAuth-Client für "+packageName+"; Originalsignatur SHA-1: "+sha1
            +". Veröffentlichungsstatus und nötige Verifizierung prüfen: Ein externer Testbetrieb ist kein dauerhafter Zugang. Danach die Google-Kalenderfreigabe auf dem Pixel bestätigen.";
    }
    private CalendarSetupRules(){}
}
