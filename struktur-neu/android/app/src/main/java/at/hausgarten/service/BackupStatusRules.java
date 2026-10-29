package at.hausgarten.service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.json.*;

/** Read-only evidence shared by the backup screen, MCP and the self-test. */
final class BackupStatusRules {
    private static final Set<String> UNFINISHED=new HashSet<>(Arrays.asList("CAPTURE_PENDING","CAPTURING","CAPTURED","SEALING","SEALED_LOCAL","LOCAL_VERIFIED","PROVIDER_WRITING","PROVIDER_VERIFY_PENDING","RETRY_PENDING"));
    private static Instant instant(String value){try{return Instant.parse(value);}catch(Exception invalid){return Instant.MIN;}}
    private static boolean clockInvalid(String value,Instant now){Instant time=instant(value);return time.equals(Instant.MIN)||time.isAfter(now.plusSeconds(120));}
    private static Instant activity(JSONObject job){Instant newest=Instant.MIN;for(String field:new String[]{"created_at","last_attempt_at","last_failure_at","provider_verified_at"}){Instant time=instant(job.optString(field));if(time.isAfter(newest))newest=time;}return newest;}

    static JSONObject project(JSONArray records,JSONObject args,Set<String> running,Instant now)throws Exception {
        List<JSONObject> ordered=new ArrayList<>();String verified="",captured="";boolean clockReview=false;
        for(int i=0;i<records.length();i++){
            JSONObject record=records.getJSONObject(i),job=record.getJSONObject("job"),destination=job.optJSONObject("destination");
            if(!record.optBoolean("sameInstallation")||!record.optBoolean("sameRestoreEpoch")||destination==null||(!destination.optString("purpose").equals("pixel_plain_backup")&&!args.optBoolean("legacy_evidence")))continue;
            String id=job.getString("backupId");if(args.has("backup_id")&&!args.getString("backup_id").equals(id))continue;
            JSONObject archive=job.optJSONObject("archive"),proof=job.optJSONObject("provider");
            String recorded=job.optString("phase"),phase=recorded,error=job.optString("lastErrorCode");boolean active=running.contains(id);
            boolean plain=destination.optString("purpose").equals("pixel_plain_backup");
            boolean proven=plain&&archive!=null&&proof!=null&&archive.optString("archiveFormat").equals("PLAIN_ZIP")
                &&archive.optString("sha256").matches("[0-9a-f]{64}")&&archive.optString("sha256").equals(proof.optString("archive_sha256"))
                &&archive.optLong("size")>0&&archive.optLong("size")==proof.optLong("byte_size")
                &&id.equals(proof.optString("backup_id"))&&proof.optString("verification_scope").equals("DOCUMENT_PROVIDER_READBACK");
            String checked=proof==null?"":proof.optString("verified_at"),capture=archive==null?"":archive.optString("capturedAt");
            boolean badClock=proven&&(clockInvalid(checked,now)||clockInvalid(capture,now)||clockInvalid(archive.optString("localVerifiedAt"),now)||instant(capture).isAfter(instant(checked))||instant(archive.optString("localVerifiedAt")).isAfter(instant(checked)));
            clockReview|=badClock;
            if(proven&&!badClock&&instant(checked).isAfter(instant(verified))){verified=checked;captured=capture;}
            if(!error.isEmpty())phase="FAILED";
            else if(plain&&!active&&UNFINISHED.contains(recorded)){phase="NEEDS_USER_ACTION";error="BACKUP_INTERRUPTED";}
            else if(plain&&recorded.equals("PROVIDER_VERIFIED")&&(!proven||badClock)){phase="NEEDS_USER_ACTION";error=badClock?"BACKUP_CLOCK_REVIEW_REQUIRED":"BACKUP_PROVIDER_PROOF_INVALID";}
            ordered.add(new JSONObject().put("backup_id",id).put("phase",phase).put("recorded_phase",recorded).put("running",active)
                .put("created_at",job.optString("createdAt")).put("last_attempt_at",job.optString("lastLocalAttemptAt"))
                .put("last_failure_at",job.optString("lastErrorCode").isEmpty()?"":job.optString("lastAttemptAt"))
                .put("last_error_code",error.isEmpty()?"":BackupScheduleRules.errorCode(new IOException(error)))
                .put("record_hash",job.getString("revision")).put("current_context",true).put("context_matches",true)
                .put("captured_at",capture).put("media_coverage",archive==null?"":archive.optString("mediaCoverage"))
                .put("external_storage_verified",proof!=null&&proof.optBoolean("external_storage_verified")).put("provider_verified_at",checked)
                .put("remote_verified_at",job.optJSONObject("upload")==null?"":job.getJSONObject("upload").optString("remoteVerifiedAt"))
                .put("local_verified_at",archive==null?"":archive.optString("localVerifiedAt")).put("byte_size",archive==null?0:archive.optLong("size"))
                .put("encrypted",archive!=null&&!archive.optString("archiveFormat").equals("PLAIN_ZIP")).put("saved_proof_valid",proven&&!badClock).put("cloud_confirmed",false));
        }
        ordered.sort((a,b)->{int time=activity(b).compareTo(activity(a));return time!=0?time:a.optString("backup_id").compareTo(b.optString("backup_id"));});
        int offset=Math.max(0,args.optInt("offset")),limit=Math.min(100,Math.max(1,args.optInt("limit",20)));JSONArray page=new JSONArray();
        for(int i=offset;i<ordered.size()&&page.length()<limit;i++)page.put(ordered.get(i));
        JSONObject latest=ordered.isEmpty()?new JSONObject():ordered.get(0);String state="NOT_SAVED";
        if(latest.optBoolean("running"))state="RUNNING";
        else if(Arrays.asList("FAILED","NEEDS_USER_ACTION","FORMAT_REVIEW_REQUIRED").contains(latest.optString("phase")))state="FAILED";
        else if(latest.optString("phase").equals("PROVIDER_VERIFIED")&&latest.optBoolean("saved_proof_valid"))state="VERIFIED";
        return new JSONObject().put("state",state).put("latest_job",latest).put("jobs",page).put("total",ordered.size()).put("offset",offset).put("limit",limit)
            .put("last_local_verified_at",verified).put("last_local_captured_at",captured).put("clock_review_required",clockReview);
    }

    static JSONObject selfTest(JSONObject status)throws Exception {
        String state=status.optString("state");return new JSONObject().put("name","local_backup")
            .put("status",state.equals("FAILED")||status.optBoolean("clock_review_required")?"FAIL":state.equals("VERIFIED")?"PASS":"NOT TESTABLE")
            .put("scope","recorded_pixel_file_readback").put("state",state).put("last_error_code",status.optJSONObject("latest_job")==null?"":status.getJSONObject("latest_job").optString("last_error_code"))
            .put("required_for_business",false).put("file_rechecked_now",false).put("restore_tested",false).put("cloud_confirmed",false);
    }

    static String failureMessage(String code){
        if(code.equals("BACKUP_PIXEL_FOLDER_REQUIRED"))return "Bitte einen Speicherort auf diesem Pixel wählen. Drive und andere Cloud-Anbieter werden nicht verwendet.";
        if(code.equals("BACKUP_INTERRUPTED"))return "Die letzte Sicherung wurde unterbrochen. Bitte die Sicherung erneut öffnen und speichern.";
        if(code.equals("BACKUP_RUNNER_BUSY"))return "Eine Sicherung läuft bereits. Bitte auf deren Ergebnis warten.";
        String reason="";
        switch(code){
            case "FINANCIAL_ISSUED_NUMBER_COLLISION":reason="Mehrere ausgestellte Rechnungen verwenden dieselbe Nummer.";break;
            case "FINANCIAL_RESERVATION_COUNTER_COLLISION":reason="Eine Rechnungsnummer wurde mehrfach reserviert.";break;
            case "FINANCIAL_RESERVATION_INVOICE_COLLISION":reason="Für eine Rechnung bestehen mehrere Nummernreservierungen.";break;
            case "FINANCIAL_EXTERNAL_NUMBER_COLLISION":reason="Eine Rechnungsnummer kollidiert mit dem vorhandenen externen Belegnachweis.";break;
            case "FINANCIAL_NUMBER_COLLISION":reason="Im Rechnungsbestand oder Nummernnachweis wurde eine doppelte Nummer erkannt.";break;
        }
        if(!reason.isEmpty())return "Sicherung nicht abgeschlossen. "+reason+" Bitte die Belege und den Nummernkreis prüfen lassen. Rechnungen und Nummern wurden nicht verändert. Fehlercode: "+code;
        return "Vorgang nicht abgeschlossen. Der vorhandene Bestand bleibt erhalten. Fehlercode: "+code;
    }
    private static String displayTime(String value){try{return java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(Locale.GERMAN).withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(value));}catch(Exception unknown){return "Zeitpunkt unbekannt";}}
    static String message(JSONObject status){
        JSONObject latest=status.optJSONObject("latest_job");String state=status.optString("state");
        if(state.equals("RUNNING"))return "Eine Sicherung läuft. Bitte auf das Ergebnis warten.";
        String earlier=status.optString("last_local_verified_at");
        if(state.equals("FAILED"))return failureMessage(latest==null?"BACKUP_OPERATION_FAILED":latest.optString("last_error_code","BACKUP_OPERATION_FAILED"))+(earlier.isEmpty()?"":" Früherer bestätigter Sicherungszeitpunkt: "+displayTime(earlier)+".");
        if(status.optBoolean("clock_review_required"))return "Sicherungszeit nicht bestätigt. Bitte Gerätezeit und Sicherungsdatei prüfen.";
        if(state.equals("VERIFIED"))return "Sicherung gespeichert und vollständig zurückgelesen: "+displayTime(earlier)+". Enthaltener Datenstand: "+displayTime(status.optString("last_local_captured_at"))+". Neuere Arbeit ist damit nicht automatisch gesichert.";
        return "Noch keine vollständig gespeicherte und zurückgelesene Sicherung bestätigt. Eine Kopie auf demselben Pixel schützt nicht bei Verlust oder Defekt des Geräts.";
    }
}
