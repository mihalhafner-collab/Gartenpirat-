package at.hausgarten.service;

import org.json.*;

/** One public state machine for UI, MCP and background scheduling. No business data. */
final class SyncPolicy {
    static final int MAX_ATTEMPTS=8;
    static boolean automaticAllowed(JSONObject s,long now) {
        return s.optBoolean("configured")&&!s.optBoolean("user_action_required")
            &&s.optInt("retry_count")<MAX_ATTEMPTS&&s.optLong("next_retry")<=now;
    }
    static long retryDelay(int attempt,long retryAfter) {
        return Math.max(Math.min(24*60*60*1000L,Math.max(0,retryAfter)),Math.min(15*60*1000L,15000L<<Math.min(Math.max(attempt,0),6)));
    }
    static String message(String code) {
        switch(code) {
            case "NOT_CONNECTED":return "Drive verbinden oder den bestehenden Ordner auswählen. Lokale Daten bleiben erhalten.";
            case "AUTH_REQUIRED":case "PERMISSION_REQUIRED":case "HTTP_401":case "HTTP_403":return "Drive-Freigabe am Gerät prüfen und die Verbindung bestätigen. Lokale Daten bleiben erhalten.";
            case "INVALID_REMOTE_DATA":return "Eine Drive-Datei ist unvollständig oder ungültig. Originale bleiben erhalten; Datei und Sicherung prüfen.";
            case "LEGACY_SOURCE_CHANGED":case "PUBLICATION_CONFLICT":case "PUBLICATION_INVALID":case "ACTION_ID_CONFLICT":return "Drive enthält unterschiedliche Versionen. Beide Stände bleiben erhalten; Versionen prüfen.";
            case "RETRY_LIMIT":return "Automatische Versuche pausiert. Verbindung prüfen und einmal erneut versuchen.";
            default:return "Drive-Abgleich nicht abgeschlossen. Internet und Ordnerfreigabe prüfen; erneut versuchen. Lokale Daten bleiben erhalten.";
        }
    }
    static JSONObject describe(JSONObject s,JSONObject queue,boolean running,long now)throws Exception {
        JSONObject out=new JSONObject(s.toString());int pending=queue.optInt("pending_upload"),review=queue.optInt("review");
        out.put("pending",pending).put("review",review).put("pending_total",queue.optInt("total",pending+review)).put("syncing",running);
        String code=s.optString("last_error_code"),state,title,action="none",detail;
        if(!s.optBoolean("configured")){state="not_connected";title="Drive verbinden";detail=message("NOT_CONNECTED");action="connect_drive";}
        else if(running){state="syncing";title="Abgleich läuft";detail="Änderungen werden geprüft und übertragen.";}
        else if(s.optBoolean("drive_sync_failed")){
            boolean paused=s.optBoolean("user_action_required")||s.optInt("retry_count")>=MAX_ATTEMPTS;
            state=paused?"action_required":s.optLong("next_retry")>now?"retry_wait":"error";
            title=paused?"Abgleich braucht deine Hilfe":state.equals("retry_wait")?"Erneuter Versuch folgt":"Abgleich erneut versuchen";
            detail=message(code.isEmpty()?"TRANSFER_FAILED":code);action=java.util.Arrays.asList("LEGACY_SOURCE_CHANGED","PUBLICATION_CONFLICT","PUBLICATION_INVALID","ACTION_ID_CONFLICT").contains(code)?"compare_versions":java.util.Arrays.asList("AUTH_REQUIRED","PERMISSION_REQUIRED","HTTP_401","HTTP_403").contains(code)?"connect_drive":"retry_sync";
        }else if(pending>0){state="pending";title=pending+" Änderung(en) warten auf Drive";detail="Auf diesem Gerät gespeichert. Der Abgleich erfolgt automatisch bei verfügbarer Verbindung.";}
        else if(review>0){state="review_required";title=review+" Änderung(en) prüfen";detail="Die Übertragung ist getrennt von diesen Prüfungen. Unabhängige Änderungen werden weiter abgeglichen.";action="review_changes";}
        else if(s.optInt("trash_cleanup_pending")>0){state="cleanup_pending";title="Dateibereinigung ausstehend";detail="Papierkorbeinträge entfernt. Nicht mehr verwendete Dateien werden nach bestätigtem Abgleich bereinigt; andere Arbeiten bleiben möglich.";action="retry_sync";}
        else if(s.optBoolean("cloud_confirmed")&&"drive_server_manifest".equals(s.optString("confirmation_scope"))){state="synced";title="Vollständig mit Drive abgeglichen";detail="Änderungen und Originale mit Drive-Serverbelegen bestätigt.";}
        else if(!s.optString("last_sync").isEmpty()){state="provider_readback";title="An Drive übergeben";detail="Vom Speicheranbieter zurückgelesen. Ein vollständiger Cloud-Abschluss ist damit nicht bestätigt.";}
        else{state="ready";title="Erster Abgleich ausstehend";detail="Die lokale Erfassung funktioniert auch ohne Verbindung.";}
        return out.put("state",state).put("title",title).put("detail",detail).put("next_action",action)
            .put("cloud_confirmed",state.equals("synced"));
    }
    private SyncPolicy(){}
}
