package at.hausgarten.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Only the original receipt is read; an uncertain write is never submitted here. */
internal object PendingInspectionRules {
    data class Result(val state: String, val proof: JSONObject = JSONObject())

    fun sameDeletionTarget(tool: String, initial: JSONObject, original: JSONObject): Boolean =
        when (tool) {
            "delete_work_records" ->
                initial
                    .optJSONArray("work_record_ids")
                    ?.takeIf { it.length() > 0 }
                    ?.toString()
                    ?.let { it == original.optJSONArray("work_record_ids")?.toString() } == true
            "delete_entity",
            "purge_entity" ->
                initial.optString("entity").isNotBlank() &&
                    initial.optString("entity_id").isNotBlank() &&
                    initial.getString("entity") == original.optString("entity") &&
                    initial.getString("entity_id") == original.optString("entity_id")
            "empty_trash" -> true
            else -> false
        }

    suspend fun inspect(
        tool: String,
        original: JSONObject,
        read: suspend (String, JSONObject) -> JSONObject,
        pause: suspend (Long) -> Unit = { delay(it) },
        observed: (Result) -> Unit = {},
    ): Result {
        val frozen = original.toString()
        var latest = Result("waiting")
        for (wait in listOf(0L, 750L, 2500L, 7500L)) {
            if (wait > 0) pause(wait)
            latest =
                try {
                    val proof =
                        read(
                            "inspect_edit_action",
                            JSONObject()
                                .put("original_tool", tool)
                                .put("original_args", JSONObject(frozen)),
                        )
                    Result(
                        if (proof.optBoolean("business_write_performed")) "conflict"
                        else
                            when (proof.optString("state")) {
                                "confirmed" -> "confirmed"
                                "content_removed" ->
                                    if (
                                        tool in
                                            setOf(
                                                "delete_entity",
                                                "purge_entity",
                                                "empty_trash",
                                                "delete_work_records",
                                            )
                                    )
                                        "confirmed"
                                    else "removed"
                                "unknown",
                                "" -> "waiting"
                                else -> "conflict"
                            },
                        JSONObject(proof.toString()),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val code =
                        (McpErrors.bridge(failure).optJSONObject("error") ?: JSONObject())
                            .optString("code")
                    Result(
                        if (
                            code in
                                setOf(
                                    "REPLAY_MISMATCH",
                                    "REQUEST_ID_REUSED",
                                    "INVALID_INPUT",
                                    "WRITE_CONFLICT",
                                    "RECORD_PURGED",
                                    "REFERENCE_NOT_FOUND",
                                )
                        )
                            "conflict"
                        else "waiting"
                    )
                }
            observed(latest)
            if (latest.state != "waiting") return latest
        }
        return latest
    }

    fun recoveryKey(status: JSONObject): String =
        if (status.optString("phase") == "ACTIVE" && status.optString("restore_id").isNotBlank())
            status.getString("restore_id") + ":" + status.optString("preview_hash")
        else ""
}
