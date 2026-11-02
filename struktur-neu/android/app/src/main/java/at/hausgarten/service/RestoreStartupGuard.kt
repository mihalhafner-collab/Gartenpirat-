package at.hausgarten.service

import android.content.Context

/** Prevent ordinary database access during a verified full local restore. */
internal object RestoreStartupGuard {
    private val exclusive = ThreadLocal.withInitial { false }

    @JvmStatic
    fun assertStoreMayOpen(context: Context) {
        if (exclusive.get()) return
        RevierApplication.awaitStartup(context)
        if (BackupRestoreService.requiresExclusiveStartup(context) ||
            RestoreStartupProtection.requiresColdRestart(context)) {
            throw IllegalStateException("RESTORE_RESTART_REQUIRED")
        }
    }

    /** Advance the device restore epoch once without any invoice or Drive migration. */
    @JvmStatic
    fun prepareForRestore(context: Context, requestId: String) {
        val guard = BackupDeviceGuard(context)
        val identity = guard.current()
        if (identity.optString("lastRestoreRequest") != requestId) {
            guard.advanceForRestore(requestId, identity.getString("restoreEpoch"))
        }
    }

    @JvmStatic
    fun exclusiveStartup(): AutoCloseable {
        if (exclusive.get()) throw IllegalStateException("RESTORE_STARTUP_REENTRY")
        exclusive.set(true)
        return AutoCloseable { exclusive.remove() }
    }
}
