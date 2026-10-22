package at.hausgarten.service;

import java.util.*;

/** Account, observed grant and server-calendar steps must remain separate. */
public final class CalendarSetupProgressTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Setup progress check "+checks);}
    public static void main(String[] args)throws Exception {
        SyntheticBusinessData.verify();
        yes(CalendarSetupRules.progress(false,false,false,"grant","",false).equals("1 Konto offen · 2 Freigabe ausstehend · 3 Kalender offen"));
        yes(CalendarSetupRules.progress(true,false,false,"grant","",false).contains("2 Freigabe ausstehend"));
        yes(CalendarSetupRules.progress(true,false,false,"grant","",true).contains("bei Google bestätigen"));
        for(String code:Arrays.asList("CALENDAR_APP_REGISTRATION_REQUIRED","CALENDAR_AUTH_REQUIRED","CALENDAR_CONSENT_CANCELLED","CALENDAR_AUTH_RESULT_MISSING","CALENDAR_BUSY_PERMISSION_REQUIRED","CALENDAR_SCOPE_UNSUPPORTED","CALENDAR_ACCOUNT_ACCESS_REQUIRED","CALENDAR_GOOGLE_AUTH_ERROR")){
            yes(!CalendarSetupRules.grantVerified(false,"grant",code));
            yes(!CalendarSetupRules.grantVerified(true,"calendar_readback",code));
            yes(!CalendarSetupRules.progress(true,false,false,"grant",code,false).contains("Freigabe erteilt"));
        }
        for(String stage:Arrays.asList("calendar_lookup","calendar_create","calendar_readback","settings_save")){
            yes(CalendarSetupRules.grantVerified(false,stage,"CALENDAR_NETWORK"));
            yes(CalendarSetupRules.progress(true,false,false,stage,"CALENDAR_NETWORK",false).contains("3 Kalender offen"));
        }
        yes(!CalendarSetupRules.grantVerified(false,"qa_unknown_stage",""));
        yes(CalendarSetupRules.progress(true,true,true,"grant","",false).contains("3 Kalender bestätigt"));
        String info=CalendarSetupRules.registrationChecklist("qa.example.application","00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33");
        yes(info.contains("Android-OAuth")&&info.contains("Calendar API")&&info.contains("Testbetrieb"));
        yes(!info.contains("Bearer")&&!info.contains("@")&&!info.contains("refresh_token"));
        yes(CalendarAccessRules.message("CALENDAR_APP_REGISTRATION_REQUIRED").contains("vor dem Berechtigungsdialog"));
        yes(CalendarAccessRules.requestedScopes(false).size()==2&&CalendarAccessRules.requestedScopes(true).size()==3);
        System.out.println("PASS "+checks+" observed Google authorization/account/calendar progress, early registration failure, minimal scopes and non-secret diagnostics checks; synthetic only");
    }
}
