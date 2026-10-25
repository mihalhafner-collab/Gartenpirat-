package at.hausgarten.service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.json.*;

/** Pure production failure decisions shared by the Android adapter and executable JVM tests. */
final class SyncFailureRules {
    static String failureCode(Exception error){
        if(error instanceof SyncIoFailure)return ((SyncIoFailure)error).code;
        if(error instanceof DriveRestClient.Failure)return ((DriveRestClient.Failure)error).code;
        if(error instanceof PixelMcpWriteTools.Rejected)return ((PixelMcpWriteTools.Rejected)error).code;
        if(error instanceof UnknownHostException||error instanceof SocketTimeoutException||error instanceof ConnectException)return "NETWORK_UNAVAILABLE";
        if(error instanceof SecurityException)return "PERMISSION_REQUIRED";
        if(error instanceof JSONException)return "INVALID_REMOTE_DATA";
        return "TRANSFER_FAILED";
    }
    static String friendly(Exception error){
        if(error instanceof SyncIoFailure)return SyncPolicy.message(((SyncIoFailure)error).code);
        if(error instanceof DriveRestClient.Failure)return error.getMessage();
        if(error instanceof UnknownHostException||error instanceof SocketTimeoutException||error instanceof ConnectException)return SyncPolicy.message("NETWORK_UNAVAILABLE");
        if(error instanceof SecurityException)return "Drive-Zugriff fehlt oder ist abgelaufen. Bestehenden Ordner erneut auswählen; lokale Daten bleiben erhalten.";
        if(error instanceof JSONException)return "Eine Drive-Datei ist unvollständig oder beschädigt. Keine Datei überschrieben; Sicherung prüfen.";
        String value=error.getMessage();return value!=null&&value.length()<300&&error instanceof IllegalArgumentException?value:"Drive-Abgleich nicht abgeschlossen. Internet und Ordnerfreigabe prüfen. Lokale Einträge bleiben erhalten.";
    }
    static String detail(Exception error){
        if(!failureCode(error).equals("TRANSFER_FAILED"))return "";
        // Arbitrary provider/logic messages may contain customer text, URIs and credentials.
        // Keep only exact known platform messages; never regex-guess private text.
        String message=error.getMessage(),safe="Meldung aus Datenschutzgründen ausgeblendet.";
        if(message!=null&&Arrays.asList("Stream closed","stream closed","Socket closed","Connection reset","Broken pipe","Read timed out","unexpected end of stream").contains(message))safe=message;
        String name=error.getClass().getName();if(!name.matches("[A-Za-z0-9_.$]{1,100}"))name="Exception";
        String value=name+": "+safe;return value.substring(0,Math.min(120,value.length()));
    }
    static JSONObject recordFailure(Exception error,int previous,long now)throws Exception{
        String code=failureCode(error);boolean statusOnly=code.equals("STATUS_NOT_SAVED");
        boolean needs=error instanceof SyncIoFailure&&((SyncIoFailure)error).needsAction()||error instanceof JSONException||error instanceof IllegalArgumentException||error instanceof SecurityException||error instanceof DriveRestClient.Failure&&((DriveRestClient.Failure)error).userAction;
        boolean configuration=code.equals("FOLDER_NOT_SELECTED")||code.equals("DEVICE_ID_MISSING");
        int attempts=statusOnly?0:configuration?1:Math.min(SyncPolicy.MAX_ATTEMPTS,Math.max(0,previous)+1);
        boolean paused=!statusOnly&&(needs||attempts>=SyncPolicy.MAX_ATTEMPTS&&!code.equals("NETWORK_UNAVAILABLE"));
        long delay=code.equals("REMOTE_STATE_REFERENCE_MISSING")?Math.min(6L*60*60*1000,15L*60*1000*(1L<<Math.min(attempts-1,4))):SyncPolicy.retryDelay(attempts,error instanceof DriveRestClient.Failure?((DriveRestClient.Failure)error).retryAfter:0);
        boolean schedule=!statusOnly&&(!paused||code.equals("REMOTE_STATE_REFERENCE_MISSING")&&attempts<SyncPolicy.MAX_ATTEMPTS);
        return new JSONObject().put("last_error_code",code).put("last_error",friendly(error)).put("last_error_detail",detail(error))
            .put("drive_sync_failed",!statusOnly).put("cloud_confirmed",false).put("user_action_required",paused).put("retry_count",attempts)
            .put("next_retry",schedule?now+delay:0).put("schedule_retry",schedule).put("retry_delay",schedule?delay:0);
    }
    private SyncFailureRules(){}
}
