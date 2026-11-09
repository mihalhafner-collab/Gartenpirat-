package at.hausgarten.service

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun rememberNativePendingInspection(
    controller: NativeAppController,
    tool: String,
    original: JSONObject?,
    enabled: Boolean,
    attempt: Int = 0,
    onConfirmed: suspend (JSONObject) -> Unit,
): PendingInspectionRules.Result {
    val encoded = original?.toString()
    var result by
        remember(tool, encoded) { mutableStateOf(PendingInspectionRules.Result("checking")) }
    val confirmed by rememberUpdatedState(onConfirmed)
    LaunchedEffect(tool, encoded, enabled, attempt) {
        if (!enabled || encoded == null) return@LaunchedEffect
        result = PendingInspectionRules.Result("checking")
        try {
            val checked =
                PendingInspectionRules.inspect(
                    tool,
                    JSONObject(encoded),
                    controller::query,
                    observed = { if (it.state != "waiting") result = it },
                )
            result = checked
            if (checked.state == "confirmed") confirmed(checked.proof)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            result = PendingInspectionRules.Result("waiting")
        }
    }
    return result
}

@Composable
internal fun NativeInspectionBanner(result: PendingInspectionRules.Result, onRetry: () -> Unit) {
    if (result.state in setOf("checking", "confirmed")) {
        LinearProgressIndicator(
            Modifier.fillMaxWidth().semantics { testTag = UiTags.inspection(result.state) }
        )
        return
    }
    Surface(
        Modifier.fillMaxWidth().semantics { testTag = UiTags.inspection(result.state) },
        color = NativeAttentionContainer,
        contentColor = NativeAttentionContent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (result.state) {
                    "conflict" -> "Der Vorgang benötigt deine Prüfung."
                    "removed" -> "Der gespeicherte Eintrag wurde später entfernt."
                    else -> "Der Speicherstand ist noch offen."
                }
            )
            if (result.state == "waiting")
                TextButton(onRetry, Modifier.semantics { testTag = UiTags.INSPECTION_RETRY }) {
                    Text("Erneut laden")
                }
        }
    }
}

/** Acknowledging a matching deletion receipt never re-enters a business writer. */
internal suspend fun NativeAppController.acknowledgeDeleteResult(
    tool: String,
    initial: JSONObject,
    original: JSONObject,
    proof: JSONObject,
): JSONObject {
    require(tool in setOf("delete_entity", "purge_entity", "empty_trash", "delete_work_records"))
    require(PendingInspectionRules.sameDeletionTarget(tool, initial, original))
    require(
        proof.optString("state") in setOf("confirmed", "content_removed") &&
            !proof.optBoolean("business_write_performed")
    )
    for (key in
        listOf("delete:$tool:" + PixelMcpWriteTools.hash(initial), actionDraftKey(tool, initial))
            .distinct()) {
        val saved = draft(key) ?: continue
        val savedArgs = saved.optJSONObject("original_args") ?: continue
        if (
            saved.optString("original_tool") == tool &&
                PixelMcpWriteTools.hash(savedArgs) == PixelMcpWriteTools.hash(original)
        )
            removeDraft(key)
    }
    return proof.optJSONObject("result") ?: JSONObject()
}

@Composable
internal fun NativeRestoreBanner(onReview: (() -> Unit)? = null) {
    Surface(
        Modifier.fillMaxWidth().semantics { testTag = UiTags.RESTORE_DRAFT_BANNER },
        color = NativeAttentionContainer,
        contentColor = NativeAttentionContent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Diese Angaben stammen aus einer Sicherung. Prüfe ihren Zusammenhang vor dem Speichern."
            )
            if (onReview != null) TextButton(onReview) { Text("Angaben ansehen") }
        }
    }
}

/**
 * An activated restore leaves all normal navigation available; startup's atomic guard stays intact.
 */
@Composable
internal fun NativeRestoreNotice(controller: NativeAppController) {
    var key by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) {
        try {
            val observed =
                PendingInspectionRules.recoveryKey(controller.query("get_restore_status"))
            val seen =
                withContext(Dispatchers.IO) {
                    controller.store.ownerValue("native_restore_notice_seen", "")
                }
            if (observed.isNotBlank() && observed != seen) key = observed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            key = ""
        }
    }
    if (key.isBlank()) return
    fun dismiss(open: Boolean) {
        val observed = key
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    controller.store.putOwnerValue("native_restore_notice_seen", observed)
                }
                key = ""
                if (open) controller.navigate("backup")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                controller.message("Der Hinweis bleibt bis zum nächsten Öffnen sichtbar.")
            }
        }
    }
    Surface(
        Modifier.fillMaxWidth().semantics { testTag = UiTags.RESTORE_BANNER },
        color = NativeAttentionContainer,
        contentColor = NativeAttentionContent,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Die Sicherung ist wiederhergestellt.", Modifier.weight(1f))
            TextButton({ dismiss(true) }, Modifier.semantics { testTag = UiTags.RESTORE_VIEW }) {
                Text("Ansehen")
            }
            IconButton(
                { dismiss(false) },
                Modifier.semantics {
                    testTag = UiTags.RESTORE_DISMISS
                    contentDescription = "Hinweis schließen"
                },
            ) {
                Text("×")
            }
        }
    }
}
