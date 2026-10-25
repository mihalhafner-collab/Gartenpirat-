package at.hausgarten.service;

import org.json.JSONObject;
import org.json.JSONException;

/** Final app contract. Drive is a backup destination, never a business-state authority. */
final class DriveRolePolicy {
    static boolean businessSyncAllowed(){return false;}
    static String role(){return "backup_only";}
    static int contractVersion(){return 3;}
    static JSONObject retiredResponse()throws JSONException{return new JSONObject().put("state","retired").put("code","LEGACY_SYNC_RETIRED").put("drive_role",role()).put("storage_contract_version",contractVersion()).put("business_sync_allowed",false).put("backup_started",false).put("cloud_confirmed",false).put("next_action",new JSONObject().put("status_tool","get_full_backup_status").put("request_tool","request_full_backup").put("setup_requires_device",true)).put("message","Der frühere Drive-Geschäftsabgleich ist stillgelegt. Vorhandene Daten, Kontofreigaben und Warteschlangen bleiben erhalten. Die Vollsicherung besitzt einen eigenen Status und eine bewusste Anforderung; es wurde keine Sicherung gestartet.");}
    private DriveRolePolicy(){}
}
