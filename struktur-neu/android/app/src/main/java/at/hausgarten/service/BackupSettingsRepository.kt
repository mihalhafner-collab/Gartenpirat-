package at.hausgarten.service

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Local browser checkpoint in the existing database; no account or cloud settings. */
class BackupSettingsRepository @Throws(Exception::class) internal constructor(
    private val context: Context,
    private val store: PixelLocalStore
) {
    private val guard = BackupDeviceGuard(context)

    @Throws(Exception::class)
    fun browser(): JSONObject {
        val error = browserError()
        if (error.isNotEmpty()) throw IOException(error)
        val raw = store.ownerValue("full_backup_browser", "")
        if (raw.isEmpty()) throw IOException("BACKUP_BROWSER_STATE_REQUIRED_OPEN_APP")
        val saved = JSONObject(raw)
        val identity = guard.current()
        if (saved.getInt("formatVersion") != 1 ||
            saved.getString("installationId") != identity.getString("installationId") ||
            saved.getString("restoreEpoch") != identity.getString("restoreEpoch")) {
            throw IOException("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED")
        }
        return saved
    }

    fun browserError(): String {
        val raw = store.ownerValue("full_backup_browser_status", "")
        if (raw.isEmpty()) return ""
        try {
            val status = JSONObject(raw)
            if (status.optBoolean("capturePending")) return "BACKUP_BROWSER_CAPTURE_REQUIRED"
            val code = status.optString("code")
            if (code.isEmpty()) return ""
            return if (code in listOf("BACKUP_BROWSER_CAPTURE_REQUIRED", "BACKUP_BROWSER_STATE_TOO_LARGE", "BACKUP_BROWSER_WRITE_CONFLICT")) {
                code
            } else {
                "BACKUP_BROWSER_CAPTURE_REQUIRED"
            }
        } catch (invalid: Exception) {
            return "BACKUP_BROWSER_CAPTURE_REQUIRED"
        }
    }

    fun browserCapturePending(): Boolean = try {
        JSONObject(store.ownerValue("full_backup_browser_status", "{}")).optBoolean("capturePending")
    } catch (invalid: Exception) {
        false
    }

    @Throws(Exception::class)
    fun browserRevision(): String = PixelMcpWriteTools.hash(store.ownerValue("full_backup_browser", ""))

    @Throws(Exception::class)
    fun saveBrowser(requestId: String?, expectedRevision: String?, browser: JSONObject?): JSONObject {
        RestoredBrowserCheckpoint(context, store).assertCheckpointAllowed()
        if (requestId == null || !requestId.matches(Regex("[A-Za-z0-9_-]{8,200}"))) {
            throw IOException("BACKUP_REQUEST_ID_REQUIRED")
        }
        if (expectedRevision == null || !expectedRevision.matches(Regex("[0-9a-f]{64}"))) {
            throw IOException("BACKUP_BROWSER_REVISION_REQUIRED")
        }
        if (browser == null || browser.optInt("formatVersion") != 1 || browser.optJSONObject("storage") == null) {
            throw IOException("BACKUP_BROWSER_STATE_REQUIRED")
        }
        val storage = browser.getJSONObject("storage")
        val keys = storage.keys()
        while (keys.hasNext()) {
            if (storage.get(keys.next()) !is String) throw IOException("BACKUP_BROWSER_STORAGE_INVALID")
        }
        if (browser.toString().toByteArray(StandardCharsets.UTF_8).size > 16 * 1024 * 1024) {
            throw IOException("BACKUP_BROWSER_STATE_TOO_LARGE")
        }
        val identity = guard.current()
        val hash = PixelMcpWriteTools.hash(browser)
        val receiptKey = "full_backup_browser_request:" + PixelMcpWriteTools.hash(requestId)
        val fingerprint = PixelMcpWriteTools.hash(JSONObject().put("browserSha256", hash)
            .put("expectedRevision", expectedRevision).put("identity", identity))
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val receipt = store.ownerValue(receiptKey, "")
            if (receipt.isNotEmpty()) {
                val prior = JSONObject(receipt)
                if (fingerprint != prior.getString("fingerprint")) throw IOException("BACKUP_BROWSER_REPLAY_MISMATCH")
                db.setTransactionSuccessful()
                return prior.getJSONObject("result").put("replayed", true)
            }
            val raw = store.ownerValue("full_backup_browser", "")
            if (raw.isNotEmpty()) {
                val saved = JSONObject(raw)
                val same = identity.getString("installationId") == saved.optString("installationId") &&
                    identity.getString("restoreEpoch") == saved.optString("restoreEpoch")
                if (same && hash == saved.optString("contentSha256")) {
                    val result = JSONObject().put("contentSha256", hash).put("capturedAt", saved.getString("capturedAt"))
                    store.putOwnerValue(receiptKey, JSONObject().put("fingerprint", fingerprint).put("result", result).toString())
                    db.setTransactionSuccessful()
                    return result
                }
                if (requestId == saved.optString("requestId")) throw IOException("BACKUP_BROWSER_REPLAY_MISMATCH")
            }
            if (browserRevision() != expectedRevision) throw IOException("BACKUP_BROWSER_WRITE_CONFLICT")
            BrowserDraftRepository.validateCaptured(db, identity, browser)
            val saved = JSONObject().put("formatVersion", 1)
                .put("installationId", identity.getString("installationId"))
                .put("restoreEpoch", identity.getString("restoreEpoch")).put("requestId", requestId)
                .put("capturedAt", Instant.now().toString()).put("contentSha256", hash)
                .put("browser", JSONObject(browser.toString()))
            store.putOwnerValue("full_backup_browser", saved.toString())
            val result = JSONObject().put("contentSha256", hash).put("capturedAt", saved.getString("capturedAt"))
            store.putOwnerValue(receiptKey, JSONObject().put("fingerprint", fingerprint).put("result", result).toString())
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
}
