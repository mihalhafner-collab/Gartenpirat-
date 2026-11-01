package at.hausgarten.service

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Arrays
import org.json.*

/** Pure production failure decisions shared by the Android adapter and executable JVM tests. */
class SyncFailureRules {
    private constructor() {
    }
    companion object {
        @JvmStatic fun failureCode(error: Exception): String {
            if ((error is SyncIoFailure)) {
                return ((error as SyncIoFailure)).code
            }
            if ((error is HttpTransport.Failure)) {
                return ((error as HttpTransport.Failure)).code
            }
            if ((error is PixelMcpWriteTools.Rejected)) {
                return ((error as PixelMcpWriteTools.Rejected)).code
            }
            if ((((error is UnknownHostException || error is SocketTimeoutException) || error is ConnectException))) {
                return "NETWORK_UNAVAILABLE"
            }
            if ((error is SecurityException)) {
                return "PERMISSION_REQUIRED"
            }
            if ((error is JSONException)) {
                return "INVALID_REMOTE_DATA"
            }
            return "TRANSFER_FAILED"
        }
        @JvmStatic fun friendly(error: Exception): String? {
            if ((error is SyncIoFailure)) {
                return SyncPolicy.message(((error as SyncIoFailure)).code)
            }
            if ((error is HttpTransport.Failure)) {
                return error.message
            }
            if ((((error is UnknownHostException || error is SocketTimeoutException) || error is ConnectException))) {
                return SyncPolicy.message("NETWORK_UNAVAILABLE")
            }
            if ((error is SecurityException)) {
                return "Drive-Zugriff fehlt oder ist abgelaufen. Bestehenden Ordner erneut auswählen; lokale Daten bleiben erhalten."
            }
            if ((error is JSONException)) {
                return "Eine Drive-Datei ist unvollständig oder beschädigt. Keine Datei überschrieben; Sicherung prüfen."
            }
            var value: String? = error.message
            return (if ((((value != null) && (value!!.length < 300)) && error is IllegalArgumentException)) value else "Drive-Abgleich nicht abgeschlossen. Internet und Ordnerfreigabe prüfen. Lokale Einträge bleiben erhalten.")
        }
        @JvmStatic fun detail(error: Exception): String {
            if ((!failureCode(error).equals("TRANSFER_FAILED"))) {
                return ""
            }
            var message: String? = error.message
            var safe: String = "Meldung aus Datenschutzgründen ausgeblendet."
            if ((((message != null) && Arrays.asList("Stream closed", "stream closed", "Socket closed", "Connection reset", "Broken pipe", "Read timed out", "unexpected end of stream").contains(message)))) {
                safe = message
            }
            var name: String = error.javaClass.name
            if ((!name.matches(Regex("[A-Za-z0-9_.$]{1,100}")))) {
                name = "Exception"
            }
            var value: String = (("" + name + ": ") + safe)
            return value.substring(0, Math.min(120, value.length))
        }
        @Throws(Exception::class)
        @JvmStatic fun recordFailure(error: Exception, previous: Int, now: Long): JSONObject {
            var code: String = failureCode(error)
            var statusOnly: Boolean = code.equals("STATUS_NOT_SAVED")
            var needs: Boolean = (((((error is SyncIoFailure && ((error as SyncIoFailure)).needsAction()) || error is JSONException) || error is IllegalArgumentException) || error is SecurityException) || (error is HttpTransport.Failure && ((error as HttpTransport.Failure)).userAction))
            var configuration: Boolean = (code.equals("FOLDER_NOT_SELECTED") || code.equals("DEVICE_ID_MISSING"))
            var attempts: Int = (if (statusOnly) 0 else (if (configuration) 1 else Math.min(SyncPolicy.MAX_ATTEMPTS, (Math.max(0, previous) + 1))))
            var paused: Boolean = (!statusOnly && ((needs || ((attempts >= SyncPolicy.MAX_ATTEMPTS) && !code.equals("NETWORK_UNAVAILABLE")))))
            var delay: Long = (if (code.equals("REMOTE_STATE_REFERENCE_MISSING")) Math.min((((6L * 60) * 60) * 1000), (((15L * 60) * 1000) * ((1L shl Math.min((attempts - 1), 4))))) else SyncPolicy.retryDelay(attempts, (if (error is HttpTransport.Failure) ((error as HttpTransport.Failure)).retryAfter else 0)))
            var schedule: Boolean = (!statusOnly && ((!paused || (code.equals("REMOTE_STATE_REFERENCE_MISSING") && (attempts < SyncPolicy.MAX_ATTEMPTS)))))
            return JSONObject().put("last_error_code", code).put("last_error", friendly(error)).put("last_error_detail", detail(error)).put("drive_sync_failed", !statusOnly).put("cloud_confirmed", false).put("user_action_required", paused).put("retry_count", attempts).put("next_retry", (if (schedule) (now + delay) else 0)).put("schedule_retry", schedule).put("retry_delay", (if (schedule) delay else 0))
        }
    }
}
