package at.hausgarten.service

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.*

private val planningStatuses =
    linkedMapOf(
        "draft" to "Entwurf",
        "tentative" to "Vorgemerkt",
        "confirmed" to "Mit Kunde bestätigt",
        "completed" to "Erledigt",
        "cancelled" to "Abgesagt",
    )
private val planningProfiles =
    linkedMapOf(
        "general" to "Allgemeine Außenarbeit",
        "lawn" to "Rasen / Boden",
        "hedge" to "Hecken / Bäume / Arbeiten in Höhe",
        "planting" to "Pflanzen / Bewässerung",
        "inspection" to "Besichtigung",
        "indoor" to "Wetterarme Tätigkeit",
    )
private val planningZone = ZoneId.of("Europe/Vienna")

private fun planningDay(): LocalDate = LocalDate.now(planningZone)

private fun planningRequest(): String = "ui-planning-" + UUID.randomUUID()

private fun JSONObject.planningCopy(): JSONObject = JSONObject(toString())

private fun JSONArray.planningRows(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun planningStamp(value: String): String =
    runCatching {
            DateTimeFormatter.ofPattern("EEE dd.MM.yyyy HH:mm", Locale.GERMAN)
                .withZone(planningZone)
                .format(AppointmentRules.instant(value))
        }
        .getOrDefault(value)

private fun planningLocal(value: String): String =
    AppointmentRules.instant(value)
        .atZone(planningZone)
        .toLocalDateTime()
        .withSecond(0)
        .withNano(0)
        .toString()

private fun planningText(row: JSONObject, key: String): String =
    if (!row.has(key) || row.isNull(key)) "" else row.get(key).toString()

/** Projected calendar lists; details and writes use the same guarded planning commands as MCP. */
@Composable
internal fun NativePlanningScreen(section: String, id: String, controller: NativeAppController) {
    val listStates = rememberSaveableStateHolder()
    val repository = remember(controller.store) { NativeDataRepository(controller.store) }
    val scope = rememberCoroutineScope()
    var selected by
        rememberSaveable(section, id) { mutableStateOf(id.takeUnless { it == "new" }.orEmpty()) }
    var edit by remember { mutableStateOf<PlanningEdit?>(null) }
    var editorTarget by rememberSaveable(section, id) { mutableStateOf<String?>(null) }
    var editorDraftKey by rememberSaveable(section, id) { mutableStateOf("") }
    var routeEditorOpened by
        rememberSaveable(section, id, controller.route.mode) { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    var weatherView by rememberSaveable(section) { mutableStateOf(section == "weather") }
    fun openEditor(
        recordId: String = "",
        seed: JSONObject = controller.route.args,
        draftKey: String = "",
    ) {
        editorTarget = recordId
        val key = draftKey.ifEmpty { "planning:appointment:" + recordId.ifEmpty { "new" } }
        editorDraftKey = key
        scope.launch {
            try {
                val draft = controller.draft(key)
                val result =
                    if (recordId.isEmpty()) null
                    else
                        controller.query(
                            "get_appointment",
                            JSONObject().put("appointment_id", recordId),
                        )
                edit =
                    if (draft != null) PlanningEdit.restore(key, draft)
                    else PlanningEdit.create(key, result, seed)
                failure = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            }
        }
    }
    BackHandler(edit != null || selected.isNotEmpty()) {
        val open = edit
        if (open != null) {
            open.retain(controller)
            edit = null
            editorTarget = null
            if (routeEditorOpened && controller.route.mode in setOf("new", "edit"))
                controller.back()
        } else if (id.isNotEmpty()) controller.back() else selected = ""
    }
    LaunchedEffect(id, controller.route.mode) {
        val restoring = editorTarget
        if (restoring != null) openEditor(restoring, draftKey = editorDraftKey)
        else if (
            !routeEditorOpened &&
                (id == "new" || controller.route.mode == "new" || controller.route.mode == "edit")
        ) {
            routeEditorOpened = true
            openEditor(
                id.takeUnless { it == "new" }.orEmpty(),
                draftKey = controller.route.args.optString("_draft_key"),
            )
        }
    }
    when {
        edit != null ->
            PlanningEditor(
                controller,
                repository,
                edit!!,
                onClose = {
                    edit = null
                    editorTarget = null
                    if (routeEditorOpened && controller.route.mode in setOf("new", "edit"))
                        controller.back()
                },
                onSaved = { result ->
                    val savedId = result.optString("appointment_id", editorTarget.orEmpty())
                    edit = null
                    editorTarget = null
                    if (routeEditorOpened && controller.route.mode in setOf("new", "edit"))
                        controller.finishAction()
                    controller.message(
                        "Einsatz gespeichert.",
                        if (savedId.isNotBlank()) NativeRoute("appointments", savedId, "detail")
                        else null,
                    )
                },
            )
        selected.isNotEmpty() ->
            PlanningDetail(
                controller,
                repository,
                selected,
                onBack = { if (id.isNotEmpty()) controller.back() else selected = "" },
                onAlternative = { proposal ->
                    scope.launch {
                        try {
                            val result =
                                controller.query(
                                    "get_appointment",
                                    JSONObject().put("appointment_id", selected),
                                )
                            val editor =
                                PlanningEdit.create(
                                    "planning:appointment:$selected",
                                    result,
                                    JSONObject(),
                                )
                            editor.change(
                                "local_start",
                                planningLocal(proposal.getString("starts_at")),
                                controller,
                            )
                            editor.change("end_open", false, controller)
                            editor.change(
                                "duration_hours",
                                (Duration.between(
                                            AppointmentRules.instant(
                                                proposal.getString("starts_at")
                                            ),
                                            AppointmentRules.instant(proposal.getString("ends_at")),
                                        )
                                        .toMinutes() / 60.0)
                                    .toString(),
                                controller,
                            )
                            editorTarget = selected
                            editorDraftKey = editor.key
                            edit = editor
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failure = error.message.orEmpty()
                        }
                    }
                },
            )
        else ->
            listStates.SaveableStateProvider("planning-list") {
                NativePlanningBoardScreen(
                    controller,
                    weatherView,
                    { weatherView = it },
                    onSelect = { selected = it },
                    onNew = { repeats, date, customer ->
                        openEditor(
                            seed =
                                JSONObject(controller.route.args.toString())
                                    .put("_repeat", repeats)
                                    .put("date", date)
                                    .put("customer_id", customer)
                        )
                    },
                )
            }
    }
    if (failure.isNotEmpty())
        Text(failure, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PlanningDetail(
    controller: NativeAppController,
    repository: NativeDataRepository,
    id: String,
    onBack: () -> Unit,
    onAlternative: (JSONObject) -> Unit,
) {
    val watched by
        remember(id) { repository.watchDetail("appointments", id) }
            .collectAsStateWithLifecycle(null)
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember(id) { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Triple<String, JSONObject, String>?>(null) }
    var details by rememberSaveable(id) { mutableStateOf(false) }
    var suggestions by rememberSaveable(id) { mutableStateOf(false) }
    var editingField by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, controller.route.args) {
        if (controller.route.args.optString("action") == "alternatives") {
            suggestions = true
            controller.route.args.remove("action")
        }
    }
    LaunchedEffect(id, refresh, watched?.toString()) {
        try {
            data = controller.query("get_appointment", JSONObject().put("appointment_id", id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message.orEmpty()
        }
    }
    val result = data
    if (result == null) {
        Column(Modifier.padding(16.dp)) {
            if (failure.isEmpty()) CircularProgressIndicator() else Text(failure)
            NativeTextButton(onBack) { Text("Zur Planung") }
        }
        return
    }
    val row = result.getJSONObject("appointment")
    val risk = result.optJSONObject("weather") ?: JSONObject()
    val calendar = result.optJSONObject("calendar") ?: JSONObject()
    LaunchedEffect(confirm) {
        val action = confirm ?: return@LaunchedEffect
        busy = true
        val key = "planning:action:$id:${action.first}"
        try {
            val saved = controller.draft(key)
            val args =
                saved?.optJSONObject("args")
                    ?: action.second.planningCopy().put("request_id", planningRequest())
            if (saved == null) {
                controller.saveDraft(key, JSONObject().put("tool", action.first).put("args", args))
                controller.flushDrafts()
            }
            controller.query(action.first, args)
            controller.removeDraft(key)
            refresh++
            if (action.first == "cancel_appointment")
                controller.messageUndo("Einsatz abgesagt") {
                    val current =
                        controller.query("get_appointment", JSONObject().put("appointment_id", id))
                    controller.query(
                        "update_appointment",
                        JSONObject()
                            .put("request_id", planningRequest())
                            .put("appointment_id", id)
                            .put("expected_record_hash", current.getString("record_hash"))
                            .put("updates", JSONObject().put("status", row.getString("status"))),
                    )
                    refresh++
                }
            else controller.message("Gespeichert")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (
                error is NativeCallFailure &&
                    error.detail.optString("commit_state") == "not_committed"
            )
                controller.removeDraft(key)
            failure = error.message.orEmpty()
        } finally {
            busy = false
            confirm = null
        }
    }
    if (suggestions)
        PlanningSuggestions(
            controller,
            row,
            { suggestions = false },
            { proposal ->
                suggestions = false
                onAlternative(proposal)
            },
        )
    fun refreshWeather() {
        scope.launch {
            busy = true
            try {
                val checked =
                    controller.query(
                        "request_weather_refresh",
                        JSONObject().put("request_id", planningRequest()),
                    )
                controller.message(
                    if (checked.optString("state") == "configuration_required")
                        "Wetter zuerst unter Kalender und Wetter einrichten."
                    else
                        "Wetterprüfung angefordert. Aktuelle Angaben sind erst nach bestätigtem Prüfstand verfügbar."
                )
                refresh++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NativeDetailHeading(
                row.optString("title", "Einsatz"),
                buildList {
                    add(
                        NativeDetailAction(
                            "appointment-details",
                            "Wetter & Kalender",
                            run = { details = !details },
                        )
                    )
                    add(
                        NativeDetailAction(
                            "appointment-alternative",
                            "Alternative suchen",
                            !busy,
                            { suggestions = true },
                        )
                    )
                    add(
                        NativeDetailAction(
                            "appointment-weather",
                            "Wetter prüfen",
                            !busy,
                            ::refreshWeather,
                        )
                    )
                    if (risk.optString("alert_hash").isNotBlank())
                        add(
                            NativeDetailAction(
                                "appointment-acknowledge",
                                "Wetterhinweis geprüft",
                                !busy,
                                {
                                    confirm =
                                        Triple(
                                            "acknowledge_weather_alert",
                                            JSONObject()
                                                .put("appointment_id", id)
                                                .put(
                                                    "expected_alert_hash",
                                                    risk.getString("alert_hash"),
                                                ),
                                            "Diesen Wetterhinweis als geprüft markieren? Der Einsatz bleibt unverändert.",
                                        )
                                },
                            )
                        )
                    add(
                        NativeDetailAction(
                            "appointment-settings",
                            "Kalender & Wetter einrichten",
                            run = { controller.open(PlanningConnectionActivity::class.java) },
                        )
                    )
                    add(
                        NativeDetailAction(
                            "appointment-cancel",
                            "Einsatz absagen",
                            !busy && row.optString("status") != "cancelled",
                            {
                                confirm =
                                    Triple(
                                        "cancel_appointment",
                                        JSONObject()
                                            .put("appointment_id", id)
                                            .put(
                                                "expected_record_hash",
                                                result.getString("record_hash"),
                                            ),
                                        "Diesen Einsatz absagen? Auftrag und erfasste Arbeiten bleiben erhalten.",
                                    )
                            },
                        )
                    )
                },
                onTitleClick = { editingField = "title" to "Tätigkeit" },
            )
            NativeContext(
                controller,
                JSONObject(row.toString()).put("appointment_id", id),
                heading = true,
            )
            NativeGroup("Zeit und Ort") {
                NativeKeyValue("Beginn", planningStamp(row.optString("starts_at"))) {
                    editingField = "starts_at" to "Beginn"
                }
                NativeKeyValue(
                    "Ende",
                    if (AppointmentRules.openEnd(row)) "Offen"
                    else planningStamp(row.optString("ends_at")),
                ) {
                    editingField = "ends_at" to "Ende"
                }
                NativeKeyValue(
                    "Status",
                    planningStatuses[row.optString("status")] ?: row.optString("status"),
                ) {
                    editingField = "status" to "Status"
                }
                NativeKeyValue("Einsatzort", row.optString("location")) {
                    editingField = "location" to "Einsatzort"
                }
                NativeKeyValue("Pause", "${row.optInt("break_minutes")} Min.") {
                    editingField = "break_minutes" to "Pause in Minuten"
                }
                NativeKeyValue("Anfahrtpuffer", "${row.optInt("travel_minutes")} Min.") {
                    editingField = "travel_minutes" to "Anfahrtpuffer in Minuten"
                }
                NativeKeyValue("Notizen", row.optString("notes")) {
                    editingField = "notes" to "Notizen"
                }
                NativeInfoRow(
                    "Erfasste Arbeit",
                    nativeNumber(result.optDouble("actual_hours", 0.0)) + " Std.",
                )
            }
            val collisions = result.optJSONObject("collisions")
            if (details || collisions?.optJSONArray("conflicts")?.length() ?: 0 > 0)
                PlanningCollisions(collisions)
            HorizontalDivider()
            if (details || risk.optString("severity") in setOf("critical", "difficult"))
                PlanningWeather(risk)
            HorizontalDivider()
            Text(
                "Google Kalender: " +
                    when (calendar.optString("state")) {
                        "confirmed" -> "synchronisiert"
                        "pending" -> "wird später aktualisiert"
                        "not_connected" -> "nicht verbunden"
                        "review_required" -> "Änderung prüfen"
                        "detached" -> "bewusst nicht verbunden"
                        "cancelled" -> "Absage bestätigt"
                        else -> calendar.optString("state", "Status prüfen")
                    }
            )
            val conflict = calendar.optJSONObject("conflict")
            if (conflict != null) {
                val remote = conflict.optJSONObject("remote")
                Text("Kalenderänderung vergleichen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Deine App: ${row.optString("title")}\n${planningStamp(row.optString("starts_at"))} – ${if (AppointmentRules.openEnd(row)) "Ende offen" else planningStamp(row.optString("ends_at"))}\n${row.optString("location")}"
                )
                Text(
                    if (remote == null) "Google Kalender: Eintrag entfernt"
                    else
                        "Google Kalender: ${remote.optString("summary")}\n${planningStamp(remote.optJSONObject("start")?.optString("dateTime").orEmpty())} – ${planningStamp(remote.optJSONObject("end")?.optString("dateTime").orEmpty())}\n${remote.optString("location")}"
                )
                for ((choice, label) in
                    listOf(
                        "keep_app" to "Meine App-Version behalten",
                        "use_google" to "Google-Version übernehmen",
                        "keep_without_calendar" to "Nur in der App behalten",
                    )) NativeOutlinedButton(
                    {
                        confirm =
                            Triple(
                                "resolve_calendar_conflict",
                                JSONObject()
                                    .put("appointment_id", id)
                                    .put("expected_record_hash", result.getString("record_hash"))
                                    .put(
                                        "expected_conflict_hash",
                                        calendar.getString("conflict_hash"),
                                    )
                                    .put("resolution", choice)
                                    .put("confirmed", true),
                                "Diese Variante auf Basis des angezeigten Vergleichs verwenden? Bei zwischenzeitlicher Änderung wird erneut geprüft." +
                                    if (choice == "keep_without_calendar")
                                        " Der Einsatz wird nicht mehr mit Google abgeglichen."
                                    else "",
                            )
                    },
                    enabled = !busy,
                ) {
                    Text(label)
                }
            }
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
            NativeTextButton(onBack) { Text("Zur Planung") }
            NativeDeleteButton(
                controller,
                "appointment",
                id,
                modifier = Modifier.fillMaxWidth().semantics { testTag = UiTags.DETAIL_DELETE },
                enabled = !busy,
            ) {
                onBack()
            }
        }
        val canStart = row.optString("status") !in listOf("draft", "completed", "cancelled")
        if (canStart)
            NativeActionBar(
                "Arbeit starten",
                {
                    if (!busy) {
                        val args =
                            JSONObject()
                                .put("appointment_id", id)
                                .put("customer_id", row.getString("customer_id"))
                                .put("location", row.optString("location"))
                                .put("activity", row.optString("title"))
                                .put("work_date", planningDay().toString())
                        if (row.optString("order_id").isNotEmpty())
                            args.put("order_id", row.getString("order_id"))
                        busy = true
                        scope.launch {
                            try {
                                controller.startKnownWork(args)
                                controller.navigate("timer", args = args)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error.message.orEmpty()
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy,
            )
    }
    editingField?.let { (field, label) ->
        NativePlanningValueSheet(controller, row, field, label, { refresh++ }) {
            editingField = null
        }
    }
}

@Composable
private fun PlanningCollisions(report: JSONObject?) {
    if (report == null) {
        Text("Belegung noch nicht geprüft. Externe Überschneidungen sind möglich.")
        return
    }
    val conflicts = report.optJSONArray("conflicts")?.planningRows().orEmpty()
    Text(
        if (report.optBoolean("inactive")) "Dieser Einsatz belegt keine Einsatzzeit."
        else if (conflicts.any { !it.optBoolean("uncertain") }) "Einsatzkollision"
        else if (conflicts.isNotEmpty()) "Mögliche Einsatzkollision"
        else if (!report.optBoolean("app_checked", true))
            (if (report.optBoolean("duration_unknown")) "Ende offen · Belegung noch ungeklärt"
            else "Belegung noch nicht vollständig geprüft")
        else "Keine Überschneidung in der App"
    )
    for (conflict in conflicts.take(5)) Text(
        (if (conflict.optString("source") == "google") "Google Kalender: belegt"
        else "Vorhandener Einsatz") +
            " · " +
            planningStamp(conflict.optString("starts_at")) +
            " – " +
            (if (conflict.optString("ends_at").isEmpty()) "Ende offen"
            else planningStamp(conflict.optString("ends_at"))) +
            (if (conflict.optBoolean("uncertain")) " · mögliche Überschneidung"
            else if (conflict.optBoolean("travel_only")) " · Anfahrtpuffer" else "")
    )
    if (conflicts.size > 5) Text("${conflicts.size - 5} weitere Überschneidungen")
    Text(
        if (report.optBoolean("external_checked"))
            "Google-Belegung geprüft · " + planningStamp(report.optString("checked_at"))
        else "Google-Belegung nicht geprüft. Externe Einsatzkollisionen sind möglich.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PlanningWeather(risk: JSONObject) {
    val checked = risk.optString("checked_at").isNotBlank()
    NativeStatus(
        if (checked) "Wetter geprüft" else "Wetter noch nicht geprüft",
        if (checked) NativeStatusTone.Neutral else NativeStatusTone.Attention,
    )
    Text(
        if (checked) risk.optString("label", "Wetter unbekannt")
        else "Ohne aktuelle Prüfung gibt es keine Wetteraussage.",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "Einsatzgebiet: " +
            risk.optString("location_area", risk.optString("location_label", "Adresse fehlt"))
    )
    if (risk.optString("location_notice").isNotEmpty()) Text(risk.optString("location_notice"))
    val reasons = risk.optJSONArray("reasons")
    if (reasons != null) for (i in 0 until reasons.length()) Text(reasons.optString(i))
    Text(
        if (risk.optString("checked_at").isEmpty()) "Noch keine Vorhersage"
        else "Prüfstand " + planningStamp(risk.optString("checked_at"))
    )
    if (risk.optString("warning_status").isNotEmpty()) Text(risk.optString("warning_status"))
    if (
        risk.optBoolean("forecast_available") && risk.optString("forecast_valid_until").isNotEmpty()
    )
        Text(
            "Vorhersage reicht bis " +
                planningStamp(risk.optString("forecast_valid_until")) +
                if (risk.optBoolean("forecast_complete")) "" else " · teilweise unvollständig"
        )
    if (risk.optString("forecast_notice").isNotEmpty()) Text(risk.optString("forecast_notice"))
    if (checked)
        Text(
            risk.optString("source", OpenMeteoWeatherSource.ATTRIBUTION),
            style = MaterialTheme.typography.bodySmall,
        )
}

private class PlanningEdit(
    val key: String,
    val original: JSONObject,
    initial: JSONObject,
    val observedHash: String,
    request: String,
    pendingValue: JSONObject? = null,
    restored: Boolean = false,
    initiallyDirty: Boolean = false,
) {
    var values by mutableStateOf(initial)
    var requestId by mutableStateOf(request)
    var pending by mutableStateOf(pendingValue)
    var restoreReview by mutableStateOf(restored)
    var dirty by mutableStateOf(initiallyDirty)

    fun saved(): JSONObject =
        JSONObject()
            .put("original", original)
            .put("values", values)
            .put("record_hash", observedHash)
            .put("request_id", requestId)
            .put("pending", pending ?: JSONObject.NULL)
            .put("restore_review_required", restoreReview)
            .put("dirty", dirty)

    fun retain(controller: NativeAppController) {
        if (dirty || pending != null) controller.saveDraft(key, saved())
    }

    fun change(key: String, value: Any, controller: NativeAppController) {
        if (pending != null) return
        values = values.planningCopy().put(key, value)
        dirty = true
        requestId = planningRequest()
        retain(controller)
    }

    fun businessValues(): JSONObject {
        val row = JSONObject()
        for (key in AppointmentRules.FIELDS) if (
            values.has(key) && key !in listOf("starts_at", "ends_at")
        )
            row.put(key, values.get(key))
        val zone = values.optString("timezone", "Europe/Vienna")
        val local = values.getString("local_start")
        val offset = values.optString("utc_offset")
        val sameStart =
            original.optString("starts_at").isNotEmpty() &&
                local == planningLocal(original.getString("starts_at")) &&
                offset.isEmpty()
        val start =
            if (sameStart) AppointmentRules.instant(original.getString("starts_at"))
            else AppointmentRules.local(local, zone, offset)
        row.put("starts_at", if (sameStart) original.getString("starts_at") else start.toString())
        if (values.optBoolean("end_open")) row.put("ends_at", "")
        else {
            val minutes =
                Math.round(values.getString("duration_hours").replace(',', '.').toDouble() * 60)
            require(minutes in 5..1440) {
                "Bitte eine Dauer von 5 Minuten bis 24 Stunden eintragen oder Ende offen wählen."
            }
            val sameDuration =
                !AppointmentRules.openEnd(original) &&
                    original.optString("ends_at").isNotEmpty() &&
                    minutes ==
                        Duration.between(
                                AppointmentRules.instant(original.getString("starts_at")),
                                AppointmentRules.instant(original.getString("ends_at")),
                            )
                            .toMinutes()
            row.put(
                "ends_at",
                if (sameStart && sameDuration) original.getString("ends_at")
                else start.plusSeconds(minutes * 60).toString(),
            )
        }
        for (key in arrayOf("latitude", "longitude")) {
            val value = planningText(values, key)
            row.put(key, if (value.isBlank()) JSONObject.NULL else value.toDouble())
        }
        for (key in arrayOf("travel_minutes", "break_minutes")) row.put(
            key,
            planningText(values, key).ifBlank { "0" }.toInt(),
        )
        return row
    }

    fun freeze(): JSONObject? {
        pending?.let {
            return it
        }
        val row = businessValues()
        var tool = "create_appointment"
        var args = row
        if (original.optString("id").isNotEmpty()) {
            val updates = JSONObject()
            for (key in row.keys()) if (
                PixelMcpWriteTools.hash(row.get(key)) !=
                    PixelMcpWriteTools.hash(original.opt(key) ?: JSONObject.NULL)
            )
                updates.put(key, row.get(key))
            if (updates.length() == 0) return null
            tool = "update_appointment"
            args =
                JSONObject()
                    .put("appointment_id", original.getString("id"))
                    .put("expected_record_hash", observedHash)
                    .put("updates", updates)
        } else {
            val count = planningText(values, "occurrences").ifBlank { "1" }.toInt()
            require(count in 1..26) { "Anzahl Einsätze muss zwischen 1 und 26 liegen." }
            if (count > 1) {
                tool = "create_appointment_series"
                args
                    .put("occurrences", count)
                    .put(
                        "interval_weeks",
                        planningText(values, "interval_weeks").ifBlank { "1" }.toInt(),
                    )
            }
        }
        args.put("request_id", requestId)
        McpSchema.validate(args, AppointmentTools.schema(tool))
        return JSONObject().put("tool", tool).put("args", args).also { pending = it }
    }

    companion object {
        fun create(key: String, result: JSONObject?, seed: JSONObject): PlanningEdit {
            val original = result?.getJSONObject("appointment")?.planningCopy() ?: JSONObject()
            val values = original.planningCopy()
            for (field in
                arrayOf(
                    "customer_id",
                    "order_id",
                    "title",
                    "location",
                    "notes",
                    "flexible_from",
                    "flexible_until",
                )) if (!values.has(field)) values.put(field, seed.optString(field))
            for ((field, value) in
                mapOf(
                    "timezone" to "Europe/Vienna",
                    "status" to "tentative",
                    "purpose" to "work",
                    "weather_profile" to "general",
                    "travel_minutes" to 0,
                    "break_minutes" to 0,
                )) if (!values.has(field))
                values.put(field, if (seed.has(field)) seed.get(field) else value)
            for (field in arrayOf("latitude", "longitude")) if (!values.has(field))
                values.put(field, seed.opt(field) ?: JSONObject.NULL)
            val start = original.optString("starts_at", seed.optString("starts_at"))
            values.put(
                "local_start",
                if (start.isEmpty()) seed.optString("date", planningDay().toString()) + "T"
                else planningLocal(start),
            )
            val end = original.optString("ends_at", seed.optString("ends_at"))
            values
                .put("end_open", end.isEmpty())
                .put(
                    "duration_hours",
                    if (start.isEmpty() || end.isEmpty()) ""
                    else
                        (Duration.between(
                                    AppointmentRules.instant(start),
                                    AppointmentRules.instant(end),
                                )
                                .toMinutes() / 60.0)
                            .toString(),
                )
                .put("utc_offset", "")
                .put("occurrences", 1)
                .put("interval_weeks", 1)
                .put("_repeat", seed.optBoolean("_repeat"))
            return PlanningEdit(
                key,
                original,
                values,
                result?.optString("record_hash") ?: "",
                planningRequest(),
            )
        }

        fun restore(key: String, saved: JSONObject): PlanningEdit =
            PlanningEdit(
                key,
                saved.getJSONObject("original"),
                saved.getJSONObject("values"),
                saved.optString("record_hash"),
                saved.getString("request_id"),
                saved.optJSONObject("pending"),
                saved.optBoolean("restore_review_required") ||
                    saved.optBoolean("_restore_action_review_required"),
                saved.optBoolean("dirty"),
            )
    }
}

@Composable
private fun PlanningEditor(
    controller: NativeAppController,
    repository: NativeDataRepository,
    edit: PlanningEdit,
    onClose: () -> Unit,
    onSaved: (JSONObject) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(false) }
    var collision by remember { mutableStateOf<JSONObject?>(null) }
    var more by rememberSaveable(edit.key) { mutableStateOf(false) }
    var repeatOpen by
        rememberSaveable(edit.key) { mutableStateOf(edit.values.optBoolean("_repeat")) }
    var seriesReview by remember { mutableStateOf<List<JSONObject>?>(null) }
    val values = edit.values
    val existing = edit.original.optString("id").isNotEmpty()
    val enabled = !busy && edit.pending == null
    fun change(key: String, value: Any) {
        edit.change(key, value, controller)
        failure = ""
    }
    fun save(seriesConfirmed: Boolean = false) {
        if (busy) return
        if (
            edit.pending == null &&
                !existing &&
                planningText(values, "occurrences").toIntOrNull()?.let { it > 1 } == true &&
                !seriesConfirmed
        ) {
            try {
                val candidate = edit.businessValues()
                val count = planningText(values, "occurrences").toInt()
                val interval = planningText(values, "interval_weeks").toInt()
                require(count in 2..26 && interval in 1..12) {
                    "Wiederholung: 2 bis 26 Einsätze im Abstand von 1 bis 12 Wochen wählen."
                }
                val zone = candidate.getString("timezone")
                val start =
                    AppointmentRules.instant(candidate.getString("starts_at"))
                        .atZone(ZoneId.of(zone))
                        .toLocalDateTime()
                val end =
                    if (AppointmentRules.openEnd(candidate)) null
                    else
                        AppointmentRules.instant(candidate.getString("ends_at"))
                            .atZone(ZoneId.of(zone))
                            .toLocalDateTime()
                seriesReview =
                    (0 until count).map { index ->
                        val weeks = index.toLong() * interval
                        candidate
                            .planningCopy()
                            .put(
                                "starts_at",
                                AppointmentRules.local(start.plusWeeks(weeks).toString(), zone, "")
                                    .toString(),
                            )
                            .put(
                                "ends_at",
                                if (end == null) ""
                                else
                                    AppointmentRules.local(
                                            end.plusWeeks(weeks).toString(),
                                            zone,
                                            "",
                                        )
                                        .toString(),
                            )
                    }
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            }
            return
        }
        busy = true
        scope.launch {
            var mutationAttempted = false
            try {
                val prior = edit.pending
                if (prior != null) {
                    val proof =
                        controller.query(
                            "inspect_edit_action",
                            JSONObject()
                                .put("original_tool", prior.getString("tool"))
                                .put("original_args", prior.getJSONObject("args")),
                        )
                    if (proof.optString("state") == "confirmed") {
                        controller.removeDraft(edit.key)
                        edit.pending = null
                        controller.message("Der bereits gespeicherte Einsatz wurde gefunden.")
                        onSaved(proof.optJSONObject("result") ?: proof)
                        return@launch
                    }
                    require(proof.optString("state") != "content_removed") {
                        "Der ursprüngliche Beleg wurde entfernt. Aktuellen Einsatz prüfen; keine erneute Übernahme."
                    }
                    throw IllegalStateException(
                        "Speichern weiterhin nicht bestätigt. Die ursprünglichen Angaben bleiben erhalten; es wird nur das Ergebnis geprüft."
                    )
                }
                require(!edit.restoreReview) {
                    "Gespeicherten Stand und alte Aktion nach Wiederherstellung zuerst prüfen. Kein eindeutiger Speicherbeleg vorhanden."
                }
                val action = withContext(Dispatchers.IO) { edit.freeze() }
                if (action == null) {
                    controller.removeDraft(edit.key)
                    onClose()
                    return@launch
                }
                edit.dirty = true
                edit.retain(controller)
                controller.flushDrafts()
                mutationAttempted = true
                val result =
                    controller.query(action.getString("tool"), action.getJSONObject("args"))
                controller.removeDraft(edit.key)
                edit.pending = null
                controller.message(
                    "Einsatz auf diesem Gerät gespeichert. Kalenderübertragung wird gesondert geprüft."
                )
                onSaved(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (
                    mutationAttempted &&
                        error is NativeCallFailure &&
                        error.detail.optString("commit_state") == "not_committed"
                )
                    edit.pending = null
                edit.retain(controller)
                failure = error.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }
    var inspectionAttempt by remember { mutableIntStateOf(0) }
    val inspection =
        rememberNativePendingInspection(
            controller,
            edit.pending?.optString("tool").orEmpty(),
            edit.pending?.optJSONObject("args"),
            !busy,
            inspectionAttempt,
        ) { proof ->
            controller.removeDraft(edit.key)
            edit.pending = null
            controller.message("Einsatz gespeichert.")
            onSaved(proof.optJSONObject("result") ?: proof)
        }
    seriesReview?.let { proposals ->
        AlertDialog(
            onDismissRequest = { seriesReview = null },
            title = { Text("Wiederholungen prüfen") },
            text = {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "${proposals.size} Vorschläge · noch nicht gespeichert. Jeder Einsatz wird beim Anlegen auf Überschneidungen geprüft; bestehende Einsätze werden nicht verschoben."
                    )
                    proposals.forEachIndexed { index, row ->
                        NativeInfoRow(
                            "${index+1}. ${planningStamp(row.getString("starts_at"))}",
                            if (AppointmentRules.openEnd(row)) "Ende offen"
                            else "Bis ${planningStamp(row.getString("ends_at"))}",
                        )
                    }
                }
            },
            dismissButton = { NativeTextButton({ seriesReview = null }) { Text("Zurück") } },
            confirmButton = {
                NativeTextButton({
                    seriesReview = null
                    save(true)
                }) {
                    Text("${proposals.size} Einsätze anlegen")
                }
            },
        )
    }
    LaunchedEffect(values.toString()) {
        collision =
            try {
                val candidate =
                    edit.businessValues().put("id", edit.original.optString("id", "ui-draft"))
                val availability =
                    withContext(Dispatchers.IO) {
                        AppointmentRuntime.availability(
                            controller.context,
                            controller.store,
                            Instant.now(),
                        )
                    }
                val from =
                    AppointmentRules.instant(candidate.getString("starts_at"))
                        .atZone(planningZone)
                        .toLocalDate()
                val end =
                    if (AppointmentRules.openEnd(candidate)) from
                    else
                        AppointmentRules.instant(candidate.getString("ends_at"))
                            .atZone(planningZone)
                            .toLocalDate()
                val page =
                    repository.list(
                        "appointments",
                        dateFrom = from.minusDays(1).toString(),
                        dateTo = end.toString(),
                        limit = 100,
                    )
                val appointments = JSONArray()
                for (row in page.rows) repository.detail("appointments", row.id)?.let {
                    appointments.put(it)
                }
                val report =
                    withContext(Dispatchers.IO) {
                        AppointmentAvailability.assess(candidate, appointments, availability)
                    }
                report.put("app_checked", false).put("display_incomplete", true)
                report
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: Exception) {
                null
            }
    }
    if (discard)
        LaunchedEffect(Unit) {
            try {
                controller.removeDraft(edit.key)
                onClose()
            } catch (failure: Exception) {
                failure.message?.let { controller.message(it) }
            } finally {
                discard = false
            }
        }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (existing) "Einsatz" else "Einsatz planen",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (edit.pending != null) NativeInspectionBanner(inspection) { inspectionAttempt++ }
            if (edit.restoreReview) NativeRestoreBanner()
            PlanningPicker(
                "Kunde",
                "customers",
                values.optString("customer_id"),
                controller,
                repository,
                enabled = enabled && !existing,
            ) { row ->
                change("customer_id", row?.id.orEmpty())
                change("order_id", "")
                change("location", row?.address.orEmpty())
                change("latitude", JSONObject.NULL)
                change("longitude", JSONObject.NULL)
            }
            NativeDateTimeField(
                "Geplanter Beginn",
                planningText(values, "local_start"),
                { change("local_start", it) },
                required = true,
                enabled = enabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((hours, label) in
                    listOf(
                        "1" to "1 Std",
                        "2" to "2 Std",
                        "4" to "Halber Tag",
                    )) NativeOutlinedButton(
                    {
                        change("duration_hours", hours)
                        change("end_open", false)
                    },
                    enabled = enabled,
                ) {
                    Text(label)
                }
            }
            Row {
                Checkbox(
                    values.optBoolean("end_open"),
                    { change("end_open", it) },
                    enabled = enabled,
                )
                Text("Ende offen · Dauer noch ungeklärt", Modifier.padding(top = 12.dp))
            }
            if (!values.optBoolean("end_open"))
                PlanningField(
                    "Dauer inkl. Pause in Stunden (z. B. 1,5)",
                    planningText(values, "duration_hours"),
                    { change("duration_hours", it) },
                    enabled,
                )
            PlanningField(
                "Titel / Tätigkeit",
                values.optString("title"),
                { change("title", it) },
                enabled,
            )
            PlanningCollisions(collision)
            NativeOutlinedButton(
                { suggestions = true },
                enabled = enabled && values.optString("customer_id").isNotEmpty(),
            ) {
                Text("Freie Zeit suchen")
            }
            PlanningPicker(
                "Auftrag (optional)",
                "orders",
                values.optString("order_id"),
                controller,
                repository,
                enabled = enabled && !existing,
                customerId = values.optString("customer_id"),
                allowEmpty = true,
            ) { order ->
                change("order_id", order?.id.orEmpty())
                if (order != null) {
                    change("title", order.title)
                    scope.launch {
                        repository.detail("orders", order.id)?.let { record ->
                            if (
                                record.optString("location").isNotEmpty() &&
                                    record.optString("location") != values.optString("location")
                            ) {
                                change("location", record.getString("location"))
                                change("latitude", JSONObject.NULL)
                                change("longitude", JSONObject.NULL)
                            }
                        }
                    }
                }
            }
            PlanningChoice("Status", values.optString("status"), planningStatuses, enabled) {
                change("status", it)
            }
            PlanningChoice(
                "Zweck",
                values.optString("purpose"),
                linkedMapOf("work" to "Arbeitseinsatz", "inspection" to "Besichtigung"),
                enabled,
            ) {
                change("purpose", it)
            }
            PlanningField(
                "Einsatzort",
                values.optString("location"),
                {
                    change("location", it)
                    change("latitude", JSONObject.NULL)
                    change("longitude", JSONObject.NULL)
                },
                enabled,
            )
            NativeInfoRow(
                "Kalender und Wetter",
                "Zusatzangaben bei Bedarf",
                onClick = { more = !more },
            )
            if (more) {
                PlanningChoice(
                    "Wetterprofil",
                    values.optString("weather_profile"),
                    planningProfiles,
                    enabled,
                ) {
                    change("weather_profile", it)
                }
                for ((key, label) in
                    listOf(
                        "travel_minutes" to "Anfahrtpuffer (Minuten)",
                        "break_minutes" to "Geplante Pause (Minuten)",
                    )) PlanningField(label, planningText(values, key), { change(key, it) }, enabled)
                NativeDateField(
                    "Frühester vereinbarter Arbeitstag",
                    planningText(values, "flexible_from"),
                    { change("flexible_from", it) },
                    enabled = enabled,
                )
                NativeDateField(
                    "Spätester vereinbarter Arbeitstag",
                    planningText(values, "flexible_until"),
                    { change("flexible_until", it) },
                    enabled = enabled,
                )
                PlanningField(
                    "Bei doppelter Uhrzeit: UTC-Offset",
                    planningText(values, "utc_offset"),
                    { change("utc_offset", it) },
                    enabled,
                )
            }
            if (!existing) {
                NativeInfoRow(
                    "Wiederholungen",
                    "Vorschläge vor dem Anlegen prüfen",
                    onClick = { repeatOpen = !repeatOpen },
                )
                if (repeatOpen) {
                    PlanningField(
                        "Anzahl Einsätze (1–26)",
                        planningText(values, "occurrences"),
                        { change("occurrences", it) },
                        enabled,
                    )
                    PlanningField(
                        "Abstand in Wochen (1–12)",
                        planningText(values, "interval_weeks"),
                        { change("interval_weeks", it) },
                        enabled,
                    )
                }
            }
            OutlinedTextField(
                values.optString("notes"),
                { change("notes", it) },
                label = { Text("Notizen") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            if (edit.restoreReview && edit.pending == null)
                NativeOutlinedButton(
                    {
                        scope.launch {
                            busy = true
                            try {
                                val reviewed = edit.saved().put("restore_review_required", false)
                                controller.adoptDraft(edit.key, reviewed)
                                edit.restoreReview = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error.message.orEmpty()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Angaben nach Wiederherstellung geprüft")
                }
            if (failure.isNotEmpty())
                Text(
                    failure + " Dein Entwurf bleibt erhalten.",
                    color = MaterialTheme.colorScheme.error,
                )
            NativeTextButton(
                {
                    if (edit.pending == null) discard = true
                    else failure = "Zuerst dieselbe Speicherung erneut prüfen."
                },
                enabled = !busy,
            ) {
                Text("Ungespeicherten Entwurf verwerfen")
            }
        }
        val hardConflict =
            collision?.optJSONArray("conflicts")?.planningRows()?.any {
                !it.optBoolean("uncertain")
            } == true
        if (edit.pending != null)
            NativeActionBar(
                "Schließen",
                {
                    edit.retain(controller)
                    onClose()
                },
                enabled = !busy,
            )
        else
            NativeActionBar(
                primaryLabel =
                    when {
                        busy -> "Speichern …"
                        edit.pending != null -> "Ergebnis prüfen"
                        hardConflict -> "Andere Zeit wählen"
                        !existing && (planningText(values, "occurrences").toIntOrNull() ?: 1) > 1 ->
                            "Wiederholungen prüfen"
                        else -> "Einsatz speichern"
                    },
                onPrimary = {
                    if (hardConflict && edit.pending == null) suggestions = true else save()
                },
                secondaryLabel = "Zurück",
                onSecondary = {
                    edit.retain(controller)
                    onClose()
                },
                enabled = !busy,
                secondaryEnabled = !busy,
            )
    }
}

@Composable
private fun PlanningSuggestions(
    controller: NativeAppController,
    source: JSONObject,
    onClose: () -> Unit,
    onSelect: (JSONObject) -> Unit,
) {
    var duration by remember {
        mutableStateOf(
            runCatching {
                    if (AppointmentRules.openEnd(source)) ""
                    else
                        (Duration.between(
                                    AppointmentRules.instant(source.getString("starts_at")),
                                    AppointmentRules.instant(source.getString("ends_at")),
                                )
                                .toMinutes() / 60.0)
                            .toString()
                }
                .getOrDefault("")
        )
    }
    var from by rememberSaveable { mutableStateOf(planningDay().toString()) }
    var to by rememberSaveable { mutableStateOf(planningDay().plusDays(13).toString()) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val proposal = selected
    if (proposal != null)
        AlertDialog(
            onDismissRequest = { selected = null },
            text = {
                Text(
                    "Dieses Zeitfenster in den Entwurf übernehmen? Gespeichert wird erst mit Einsatz speichern. Kundenverfügbarkeit ist separat zu bestätigen."
                )
            },
            dismissButton = { NativeTextButton({ selected = null }) { Text("Abbrechen") } },
            confirmButton = {
                NativeTextButton({
                    onSelect(proposal)
                    selected = null
                }) {
                    Text("In Entwurf übernehmen")
                }
            },
        )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Vorgeschlagene Zeitfenster") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanningField(
                    "Dauer in Stunden (5 Minuten bis 12 Stunden)",
                    duration,
                    { duration = it },
                    !busy,
                )
                NativeDateField("Von", from, { from = it }, required = true, enabled = !busy)
                NativeDateField("Bis", to, { to = it }, required = true, enabled = !busy)
                NativeOutlinedButton(
                    {
                        scope.launch {
                            busy = true
                            try {
                                require(source.optString("customer_id").isNotEmpty()) {
                                    "Zuerst einen eindeutigen Kunden wählen."
                                }
                                val minutes = Math.round(duration.replace(',', '.').toDouble() * 60)
                                require(minutes in 5..720) {
                                    "Für Zeitfenster bitte 5 Minuten bis 12 Stunden angeben."
                                }
                                val args =
                                    JSONObject()
                                        .put("customer_id", source.getString("customer_id"))
                                        .put("duration_minutes", minutes)
                                        .put("date_from", from)
                                        .put("date_to", to)
                                        .put(
                                            "weather_profile",
                                            source.optString("weather_profile", "general"),
                                        )
                                if (source.optString("order_id").isNotEmpty())
                                    args.put("order_id", source.getString("order_id"))
                                result = controller.query("suggest_appointment_slots", args)
                                failure = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error.message.orEmpty()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Zeitfenster prüfen")
                }
                result?.let { response ->
                    Text(response.optString("notice"))
                    val suggestions = response.optJSONArray("suggestions")?.planningRows().orEmpty()
                    if (suggestions.isEmpty())
                        Text("Keine passenden Zeitfenster. Zeitraum, Dauer oder Belegung prüfen.")
                    for (entry in suggestions) OutlinedCard(
                        Modifier.fillMaxWidth().clickable {
                            selected = entry.getJSONObject("appointment")
                        }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                planningStamp(
                                    entry.getJSONObject("appointment").getString("starts_at")
                                )
                            )
                            Text(
                                entry
                                    .optJSONObject("weather")
                                    ?.optString("label", "Wetter unbekannt")
                                    .orEmpty()
                            )
                            Text(
                                if (entry.optBoolean("calendar_availability_verified"))
                                    "Google-Belegtzeiten geprüft"
                                else "Externe Belegtzeiten unbekannt",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { NativeTextButton(onClose) { Text("Schließen") } },
    )
}

@Composable
private fun PlanningField(
    label: String,
    value: String,
    change: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanningChoice(
    label: String,
    value: String,
    choices: Map<String, String>,
    enabled: Boolean = true,
    change: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NativeOutlinedButton(
            { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label + ": " + (choices[value] ?: value))
        }
        DropdownMenu(expanded, { expanded = false }) {
            for ((key, title) in choices) DropdownMenuItem(
                text = { Text(title) },
                onClick = {
                    expanded = false
                    change(key)
                },
            )
        }
    }
}

/**
 * Picker reads fixed display columns and at most 30 rows; selected details load only on selection.
 */
@Composable
private fun PlanningPicker(
    label: String,
    collection: String,
    selected: String,
    controller: NativeAppController,
    repository: NativeDataRepository,
    enabled: Boolean = true,
    customerId: String = "",
    allowEmpty: Boolean = false,
    change: (NativeListRow?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedLabel by remember(selected) { mutableStateOf("") }
    LaunchedEffect(selected, collection) {
        selectedLabel =
            if (selected.isEmpty()) ""
            else
                try {
                    repository.detail(collection, selected)?.let {
                        nativeReferenceLabel(it, collection)
                    } ?: "Eintrag nicht mehr vorhanden"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "Ausgewählter Eintrag nicht lesbar"
                }
    }
    NativeOutlinedButton(
        { expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label +
                ": " +
                selectedLabel.ifEmpty { if (allowEmpty) "Keine Auswahl" else "Bitte wählen" }
        )
    }
    NativeRecordDialog(
        expanded,
        controller,
        collection,
        label,
        customerId = customerId,
        allowEmpty = allowEmpty,
        onDismiss = { expanded = false },
        onChoose = change,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativePlanningValueSheet(
    controller: NativeAppController,
    record: JSONObject,
    field: String,
    label: String,
    onSaved: () -> Unit,
    onClose: () -> Unit,
) {
    val id = record.getString("id")
    val scope = rememberCoroutineScope()
    var busy by remember(field) { mutableStateOf(false) }
    var failure by remember(field) { mutableStateOf("") }
    var fieldConflict by remember(field) { mutableStateOf<JSONObject?>(null) }
    var attemptedInput by remember(field) { mutableStateOf("") }
    var proposed by remember(field) { mutableStateOf<Any?>(null) }
    val before = remember(field, id) { JSONObject(record.toString()) }
    val initialHash = remember(field, id) { AppointmentRules.hash(before) }
    val original = before.opt(field) ?: ""
    val shown =
        if (field in setOf("starts_at", "ends_at") && original.toString().isNotBlank())
            planningLocal(original.toString())
        else original.toString()
    var chosen by remember(field) { mutableStateOf(shown) }
    suspend fun write(value: Any, expectedUndo: Any? = null, agreed: JSONObject? = null): Any {
        val current = controller.query("get_appointment", JSONObject().put("appointment_id", id))
        if (expectedUndo != null)
            require(
                PixelMcpWriteTools.hash(current.getJSONObject("appointment").opt(field) ?: "") ==
                    PixelMcpWriteTools.hash(expectedUndo)
            ) {
                "Diese Angabe wurde inzwischen geändert. Beide Stände bitte prüfen."
            }
        val updates = JSONObject().put(field, value)
        if (
            field == "starts_at" &&
                expectedUndo == null &&
                !AppointmentRules.openEnd(current.getJSONObject("appointment"))
        ) {
            val row = current.getJSONObject("appointment")
            val duration =
                Duration.between(
                    Instant.parse(row.getString("starts_at")),
                    Instant.parse(row.getString("ends_at")),
                )
            updates.put("ends_at", Instant.parse(value.toString()).plus(duration).toString())
        }
        val undoBefore = agreed ?: before
        if (expectedUndo != null && field == "starts_at" && !AppointmentRules.openEnd(undoBefore)) {
            val duration =
                Duration.between(
                    Instant.parse(undoBefore.getString("starts_at")),
                    Instant.parse(undoBefore.getString("ends_at")),
                )
            require(
                current.getJSONObject("appointment").optString("ends_at") ==
                    Instant.parse(expectedUndo.toString()).plus(duration).toString()
            ) {
                "Die Endzeit wurde inzwischen geändert. Bitte beide Stände prüfen."
            }
            updates.put("ends_at", undoBefore.optString("ends_at"))
        }
        val saved =
            controller.query(
                "update_appointment",
                JSONObject()
                    .put("request_id", planningRequest())
                    .put("appointment_id", id)
                    .put(
                        "expected_record_hash",
                        if (expectedUndo == null)
                            agreed?.let { AppointmentRules.hash(it) } ?: initialHash
                        else current.getString("record_hash"),
                    )
                    .put("updates", updates),
            )
        return saved.optJSONObject("appointment")?.opt(field) ?: value
    }
    fun finish(entered: String, agreed: JSONObject? = null) {
        attemptedInput = entered
        if (entered == shown) onClose()
        else {
            busy = true
            scope.launch {
                try {
                    val value: Any =
                        when (field) {
                            "starts_at",
                            "ends_at" ->
                                if (entered.isBlank() && field == "ends_at") ""
                                else
                                    AppointmentRules.local(
                                            entered,
                                            record.optString("timezone", "Europe/Vienna"),
                                            "",
                                        )
                                        .toString()
                            "break_minutes",
                            "travel_minutes" ->
                                entered.toIntOrNull()
                                    ?: error("Bitte eine ganze Minutenzahl eingeben.")
                            else -> entered
                        }
                    proposed = value
                    val replaced = if (agreed == null) original else agreed.opt(field) ?: ""
                    val savedValue = write(value, agreed = agreed)
                    onSaved()
                    onClose()
                    controller.messageUndo("Gespeichert") {
                        write(replaced, savedValue, agreed)
                        onSaved()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure =
                        nativeActionError(error, "Die Änderung konnte nicht gespeichert werden.")
                    if (
                        error is NativeCallFailure &&
                            error.detail.optString("code") in
                                setOf("WRITE_CONFLICT", "EDIT_CONFLICT")
                    ) {
                        fieldConflict =
                            try {
                                controller
                                    .query(
                                        "get_appointment",
                                        JSONObject().put("appointment_id", id),
                                    )
                                    .getJSONObject("appointment")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                    }
                } finally {
                    busy = false
                }
            }
        }
    }
    fieldConflict?.let { current ->
        AlertDialog(
            onDismissRequest = { fieldConflict = null },
            title = { Text("Angabe geändert") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gespeichert", style = MaterialTheme.typography.labelMedium)
                    Text(
                        when (field) {
                            "starts_at",
                            "ends_at" ->
                                current
                                    .optString(field)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::planningStamp) ?: "Offen"
                            "status" ->
                                planningStatuses[current.optString(field)]
                                    ?: current.optString(field)
                            else ->
                                nativeDisplayText(current.opt(field)?.toString().orEmpty())
                                    .ifBlank { "Leer" }
                        }
                    )
                    NativeTextButton(
                        {
                            fieldConflict = null
                            failure = ""
                            onSaved()
                            onClose()
                        },
                        Modifier.semantics { contentDescription = "Diesen behalten · gespeichert" },
                    ) {
                        Text("Diesen behalten")
                    }
                    Text("Meine Angabe", style = MaterialTheme.typography.labelMedium)
                    Text(
                        when (field) {
                            "starts_at",
                            "ends_at" ->
                                proposed
                                    ?.toString()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(::planningStamp) ?: "Offen"
                            "status" ->
                                planningStatuses[proposed?.toString()]
                                    ?: proposed?.toString().orEmpty()
                            else ->
                                nativeDisplayText(proposed?.toString().orEmpty()).ifBlank { "Leer" }
                        }
                    )
                }
            },
            confirmButton = {
                NativeTextButton(
                    {
                        fieldConflict = null
                        failure = ""
                        finish(attemptedInput, current)
                    },
                    Modifier.semantics { contentDescription = "Diesen behalten · meine Angabe" },
                    enabled = !busy,
                ) {
                    Text("Diesen behalten")
                }
            },
        )
    }
    if (field == "status")
        ModalBottomSheet(onDismissRequest = { if (!busy) finish(chosen) }) {
            Column(Modifier.padding(16.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                for ((value, title) in planningStatuses) FilterChip(
                    chosen == value,
                    { chosen = value },
                    label = { Text(title) },
                    enabled = !busy,
                )
                if (failure.isNotBlank()) Text(failure, color = MaterialTheme.colorScheme.error)
                NativeActions {
                    NativeTextButton({ finish(chosen) }, enabled = !busy) { Text("Fertig") }
                }
            }
        }
    else
        NativeValueSheet(
            label,
            shown,
            keyboardType =
                if (field in setOf("break_minutes", "travel_minutes")) KeyboardType.Number
                else KeyboardType.Text,
            multiline = field == "notes",
            error = failure,
            enabled = !busy,
            onClose = { finish(it) },
        )
}
