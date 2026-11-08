package at.hausgarten.service

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Material 3 owns the gesture; the shared command queue owns execution and undo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeSwipeRow(
    controller: NativeAppController,
    entity: String,
    id: String,
    canDelete: Boolean = true,
    positive: String = "",
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var busy by remember(entity, id) { mutableStateOf(false) }
    var error by remember(entity, id) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val pending =
        controller.listActions.firstOrNull {
            (entity to id) in ListActionTools.targets(it) &&
                it.optString("state") in setOf("queued", "applying", "uncertain")
        }
    if (
        pending?.optString("original_tool") in
            setOf(
                "delete_entity",
                "delete_work_records",
                "purge_revier_entity",
                "delete_settlement_draft",
            )
    )
        return
    val scope = rememberCoroutineScope()
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (
                    value != SwipeToDismissBoxValue.Settled && !busy && pending == null && enabled
                ) {
                    busy = true
                    keyboard?.hide()
                    focus.clearFocus()
                    scope.launch {
                        try {
                            controller.queueSwipe(
                                entity,
                                id,
                                value == SwipeToDismissBoxValue.StartToEnd,
                            )
                            error = ""
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error =
                                nativeActionError(
                                    failure,
                                    "Listenaktion konnte nicht übernommen werden.",
                                )
                        } finally {
                            busy = false
                        }
                    }
                }
                false
            }
        )
    Column(Modifier.fillMaxWidth()) {
        SwipeToDismissBox(
            state,
            modifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (!enabled || pending != null || !canDelete && positive.isEmpty())
                            Modifier.pointerInput(entity, id) {
                                detectHorizontalDragGestures { change, _ -> change.consume() }
                            }
                        else Modifier
                    )
                    .semantics {
                        testTag = UiTags.swipe(entity, id)
                        stateDescription =
                            when {
                                !enabled -> "Wischen während der Auswahl nicht verfügbar"
                                pending != null -> "Aktion vorgemerkt"
                                !canDelete && positive.isEmpty() -> "Geschützt"
                                else ->
                                    listOfNotNull(
                                            if (positive.isNotEmpty()) "Nach rechts: $positive"
                                            else null,
                                            if (canDelete) "Nach links: Löschen" else null,
                                        )
                                        .joinToString(". ")
                            }
                    },
            enableDismissFromStartToEnd =
                enabled && !busy && pending == null && positive.isNotEmpty(),
            enableDismissFromEndToStart = enabled && !busy && pending == null && canDelete,
            backgroundContent = {
                if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    val removing = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color =
                            if (removing) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                        contentColor =
                            if (removing) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Box(
                            Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment =
                                if (removing) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Text(if (removing) "Löschen" else positive)
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotEmpty()) NativeStatus(error, NativeStatusTone.Failed)
    }
}

internal suspend fun NativeAppController.queueSwipe(entity: String, id: String, positive: Boolean) {
    flushDrafts()
    val args = JSONObject()
    val tool: String
    when {
        entity == "settlement" -> {
            require(draft("settlement-review:$id")?.optJSONObject("pending") == null) {
                "Ein Vorgang dieses Abschlusses wird noch geprüft."
            }
            val current = query("get_settlement", JSONObject().put("settlement_id", id))
            args
                .put("settlement_id", id)
                .put("expected_record_hash", current.getString("record_hash"))
            if (positive) {
                val record = current.getJSONObject("record")
                require(
                    record.optString("kind") == "internal" && current.optLong("balance_minor") > 0
                ) {
                    "Kein offener Betrag zum Markieren."
                }
                tool = "add_settlement_payment"
                args
                    .put("amount", OfferMoney.decimal(current.getLong("balance_minor")))
                    .put("date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                    .put("note", "Vollständig bezahlt")
            } else {
                tool = "delete_settlement_draft"
                args.put("confirmed", true)
            }
        }
        entity in setOf("property", "inquiry", "care_plan", "visit", "material", "equipment") -> {
            val collection =
                when (entity) {
                    "property" -> "properties"
                    "inquiry" -> "inquiries"
                    "equipment" -> "equipment"
                    else -> "${entity}s"
                }
            val record =
                withContext(Dispatchers.IO) {
                    store.nativeData().detail(collection, id) ?: error("Eintrag fehlt.")
                }
            args.put("expected_record_hash", RevierRules.hash(record))
            if (positive) {
                require(entity == "visit")
                tool = "update_visit"
                args
                    .put("visit_id", id)
                    .put("updates", JSONObject().put("status", "completed"))
                    .put("reason", "In der Liste als erledigt markiert")
            } else {
                tool = "purge_revier_entity"
                args.put("entity", entity).put("entity_id", id).put("confirmed", true)
            }
        }
        positive -> {
            require(entity in setOf("order", "appointment"))
            val current = query("get_$entity", JSONObject().put("${entity}_id", id))
            tool = "update_$entity"
            val completion = ListActionTools.completionArgs(entity, id, current)
            for (key in completion.keys()) args.put(key, completion.get(key))
        }
        else -> {
            val preview =
                query("preview_delete", JSONObject().put("entity", entity).put("entity_id", id))
            require(preview.optBoolean("allowed")) {
                preview.optString("message", "Dieser Eintrag ist geschützt.")
            }
            tool = "delete_entity"
            args
                .put("entity", entity)
                .put("entity_id", id)
                .put("expected_record_hash", preview.getString("expected_record_hash"))
            if (preview.optInt("related_photos") > 0)
                args.put(
                    "expected_photo_links_hash",
                    preview.getString("expected_photo_links_hash"),
                )
        }
    }
    query(
        "queue_list_action",
        JSONObject()
            .put("request_id", UUID.randomUUID().toString())
            .put("original_tool", tool)
            .put("original_args", args),
    )
    listActions = SettlementRules.rows(query("get_list_actions").optJSONArray("actions"))
}

/** The window is exactly five seconds; long accessibility snackbar defaults must not extend it. */
@Composable
internal fun NativeListUndo(controller: NativeAppController, snackbar: SnackbarHostState) {
    val changes by
        remember(controller) { controller.store.nativeData().watchCommittedChanges() }
            .collectAsStateWithLifecycle(-1L)
    var reported by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(controller) { ListActionRunner.schedule(controller.context) }
    LaunchedEffect(changes) {
        try {
            controller.listActions =
                SettlementRules.rows(controller.query("get_list_actions").optJSONArray("actions"))
            val failure =
                controller.listActions.lastOrNull {
                    it.optString("state") in setOf("failed", "uncertain") &&
                        it.optString("id") !in reported
                }
            if (failure != null) {
                reported = reported + failure.getString("id")
                controller.message(
                    failure.optString("message", "Listenaktion bitte erneut prüfen.")
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            controller.message(failure.message ?: "Listenaktion nicht lesbar.")
        }
    }
    val latest = controller.listActions.lastOrNull { it.optString("state") == "queued" }
    LaunchedEffect(latest?.optString("id")) {
        val row = latest ?: return@LaunchedEffect
        val remaining =
            withContext(Dispatchers.IO) { controller.store.remainingDeleteUndoMs() }
                .coerceAtMost(row.optLong("undo_until") - System.currentTimeMillis())
        if (remaining <= 0) return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val result =
            withTimeoutOrNull(remaining) {
                snackbar.showSnackbar(
                    when (row.optString("original_tool")) {
                        "delete_entity",
                        "delete_work_records",
                        "purge_revier_entity",
                        "delete_settlement_draft" -> "Wird gelöscht."
                        "add_settlement_payment" -> "Wird als bezahlt markiert."
                        else -> "Wird als erledigt markiert."
                    },
                    actionLabel = "Rückgängig",
                    duration = SnackbarDuration.Indefinite,
                )
            }
        if (result == SnackbarResult.ActionPerformed) {
            try {
                if (row.optString("original_tool") == "delete_entity") {
                    withContext(Dispatchers.IO) {
                        controller.store.undoRemoveEntity(
                            row.getString("entity"),
                            row.getString("entity_id"),
                        )
                    }
                } else {
                    controller.query(
                        "undo_list_action",
                        JSONObject()
                            .put("request_id", UUID.randomUUID().toString())
                            .put("action_id", row.getString("id")),
                    )
                }
                controller.message("Rückgängig.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                controller.message(failure.message ?: "Rückgängig nicht bestätigt.")
            }
        }
    }
}
