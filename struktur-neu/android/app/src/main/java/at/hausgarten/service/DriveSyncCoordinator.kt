package at.hausgarten.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject

/** Compatibility responses only. The retired business transport is absent. */
internal class DriveSyncCoordinator private constructor() {
    companion object {
        @JvmField val LOCK = Any()

        @JvmStatic fun running(): Boolean = false

        @JvmStatic
        @Throws(Exception::class)
        fun status(context: Context, queue: JSONObject?): JSONObject {
            val result =
                DriveRolePolicy.retiredResponse()
                    .put("ok", true)
                    .put("mode", DriveRolePolicy.role())
                    .put("sync_contract_version", DriveRolePolicy.contractVersion())
                    .put("configured", false)
                    .put("legacy_sync_enabled", false)
                    .put("automatic_enabled", false)
                    .put("syncing", false)
                    .put("legacy_queue_preserved", true)
                    .put("confirmation_scope", "local_only")
                    .put("migration_required", !LocalAuthorityGuard.local(context))
                    .put("device_action", "openFullBackup")
            if (queue != null)
                for (key in
                    arrayOf("pending", "pending_upload", "review", "total", "locally_committed")) {
                    if (queue.has(key)) result.put(key, queue.get(key))
                }
            return result
        }

        @JvmStatic
        @Throws(Exception::class)
        fun requestExplicit(context: Context, request: String): JSONObject = status(context, null)

        @JvmStatic
        fun hasNetwork(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
