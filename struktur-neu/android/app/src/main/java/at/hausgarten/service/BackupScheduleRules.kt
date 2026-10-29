package at.hausgarten.service

import org.json.JSONException
import java.io.IOException
import java.io.InterruptedIOException

/** Safe stage codes for local backup capture. No scheduling or remote targets. */
object BackupScheduleRules {
    /** Only bounded machine codes, never exception prose, paths or business values. */
    @JvmStatic
    fun errorCode(failure: Throwable?): String {
        val code = failure?.message
        return if (code != null && code.matches(Regex("(?:BACKUP|RESTORE|RECOVERY|DOCUMENT|BROWSER_MEDIA|FINANCIAL)_[A-Z0-9_]{1,100}"))) {
            code
        } else {
            "BACKUP_OPERATION_FAILED"
        }
    }

    @JvmStatic
    fun captureFailure(stage: String?, failure: Exception): Exception {
        if (failure is InterruptedIOException || Thread.currentThread().isInterrupted) return failure
        if (errorCode(failure) != "BACKUP_OPERATION_FAILED") return failure
        val safeStage = if (stage in listOf(
                "READINESS", "MATERIALIZE", "PIN_FILES", "PREFERENCES", "DATABASE", "BROWSER", "FILES", "INVENTORY",
                "INVENTORY_STATE", "INVENTORY_BUSINESS", "INVENTORY_RELATIONS", "INVENTORY_FINANCIAL",
                "INVENTORY_ORIGINALS", "INVENTORY_BROWSER_MEDIA", "INVENTORY_NUMBERS", "INVENTORY_METADATA"
            )) stage else "UNKNOWN"
        val category = when {
            failure.javaClass.name.startsWith("android.database.") -> "DATABASE"
            failure is IOException -> "IO"
            failure is JSONException -> "DATA"
            failure is SecurityException -> "PERMISSION"
            else -> "STATE"
        }
        return IOException("BACKUP_CAPTURE_${safeStage}_${category}_FAILED", failure)
    }
}
