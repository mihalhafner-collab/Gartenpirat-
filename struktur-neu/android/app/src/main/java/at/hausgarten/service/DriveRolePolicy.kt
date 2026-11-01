package at.hausgarten.service

import org.json.JSONException
import org.json.JSONObject

/** Retired business synchronization contract retained for old MCP callers. */
internal class DriveRolePolicy private constructor() {
    companion object {
        @JvmStatic fun businessSyncAllowed(): Boolean = false

        @JvmStatic fun role(): String = "retired_local_backup"

        @JvmStatic fun contractVersion(): Int = 3

        @JvmStatic
        @Throws(JSONException::class)
        fun retiredResponse(): JSONObject =
            JSONObject()
                .put("state", "retired")
                .put("code", "LEGACY_SYNC_RETIRED")
                .put("drive_role", role())
                .put("storage_contract_version", contractVersion())
                .put("business_sync_allowed", false)
                .put("backup_started", false)
                .put("cloud_confirmed", false)
                .put(
                    "next_action",
                    JSONObject()
                        .put("status_tool", "get_full_backup_status")
                        .put("request_tool", "request_full_backup")
                        .put("setup_requires_device", true),
                )
                .put(
                    "message",
                    "Der frühere Drive-Geschäftsabgleich ist stillgelegt. Vorhandene Daten, Kontofreigaben und Warteschlangen bleiben erhalten. Die Vollsicherung besitzt einen eigenen Status und eine bewusste Anforderung; es wurde keine Sicherung gestartet.",
                )
    }
}
