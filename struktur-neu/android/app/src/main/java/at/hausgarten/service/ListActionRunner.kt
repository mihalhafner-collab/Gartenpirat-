package at.hausgarten.service

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Pending actions use the existing database and writers, including their replay protection. */
internal object ListActionRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun schedule(context: Context) {
        val app = context.applicationContext ?: context
        scope.launch {
            mutex.withLock {
                PixelLocalStore(app).use { store ->
                    val repository = LocalOwnerRepository(app, store)
                    val service = ListActionService(repository)
                    val tools =
                        PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(app, store))
                    fun <T> transaction(block: () -> T): T {
                        val db = store.writableDatabase
                        store.beginBusinessTransaction(db)
                        try {
                            val result = block()
                            db.setTransactionSuccessful()
                            return result
                        } finally {
                            db.endTransaction()
                        }
                    }
                    while (true) {
                        val rows =
                            ListActionTools.rows(repository).filter {
                                it.optString("state") in setOf("queued", "applying", "uncertain")
                            }
                        if (rows.isEmpty()) break
                        val remaining =
                            rows.minOf { it.getLong("undo_until") } - System.currentTimeMillis()
                        if (remaining > 0) {
                            delay(remaining.coerceAtMost(ListActionTools.UNDO_MS))
                            continue
                        }
                        val row = transaction { service.claimDue() }
                        store.notifyCommittedChange()
                        if (row == null) continue
                        var failure = ""
                        var uncertain = false
                        try {
                            val inspected =
                                tools.call(
                                    "inspect_edit_action",
                                    JSONObject()
                                        .put("original_tool", row.getString("original_tool"))
                                        .put("original_args", row.getJSONObject("original_args")),
                                )
                            val proof = inspected.optJSONObject("data") ?: inspected
                            if (proof.optString("state") in setOf("confirmed", "content_removed")) {
                                transaction { service.finish(row.getString("id")) }
                                store.notifyCommittedChange()
                                continue
                            }
                            val result =
                                tools.call(
                                    row.getString("original_tool"),
                                    row.getJSONObject("original_args"),
                                )
                            if (result.has("ok") && !result.optBoolean("ok")) {
                                val error = result.optJSONObject("error") ?: result
                                failure =
                                    error.optString(
                                        "message",
                                        "Listenaktion konnte nicht abgeschlossen werden.",
                                    )
                                uncertain = error.optString("commit_state") != "not_committed"
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            val detail =
                                McpErrors.bridge(error).optJSONObject("error") ?: JSONObject()
                            failure =
                                detail.optString(
                                    "message",
                                    "Listenaktion wird nach dem nächsten Öffnen erneut geprüft.",
                                )
                            uncertain = detail.optString("commit_state") != "not_committed"
                            if (
                                (error is IllegalArgumentException ||
                                    error is org.json.JSONException) &&
                                    detail.optString("code") !in
                                        setOf(
                                            "REPLAY_MISMATCH",
                                            "REQUEST_ID_REUSED",
                                            "REQUEST_EXPIRED",
                                            "COMMIT_UNKNOWN",
                                        )
                            )
                                uncertain = false
                        }
                        transaction { service.finish(row.getString("id"), failure, uncertain) }
                        store.notifyCommittedChange()
                        MainActivity.refreshBusinessView()
                        if (uncertain) break
                    }
                }
            }
        }
    }
}
