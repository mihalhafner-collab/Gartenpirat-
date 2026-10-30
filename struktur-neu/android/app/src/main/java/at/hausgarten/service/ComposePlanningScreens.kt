package at.hausgarten.service

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val planningStatuses = linkedMapOf("draft" to "Entwurf", "tentative" to "Vorgemerkt", "confirmed" to "Mit Kunde bestätigt", "completed" to "Erledigt", "cancelled" to "Abgesagt")
private val planningProfiles = linkedMapOf("general" to "Allgemeine Außenarbeit", "lawn" to "Rasen / Boden", "hedge" to "Hecken / Bäume / Arbeiten in Höhe", "planting" to "Pflanzen / Bewässerung", "inspection" to "Besichtigung", "indoor" to "Wetterarme Tätigkeit")
private val planningZone = ZoneId.of("Europe/Vienna")
private fun planningDay(): LocalDate = LocalDate.now(planningZone)
private fun planningRequest(): String = "ui-planning-" + UUID.randomUUID()
private fun JSONObject.planningCopy(): JSONObject = JSONObject(toString())
private fun JSONArray.planningRows(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun planningStamp(value: String): String = runCatching { DateTimeFormatter.ofPattern("EEE dd.MM.yyyy HH:mm", Locale.GERMAN).withZone(planningZone).format(AppointmentRules.instant(value)) }.getOrDefault(value)
private fun planningLocal(value: String): String = AppointmentRules.instant(value).atZone(planningZone).toLocalDateTime().withSecond(0).withNano(0).toString()
private fun planningText(row: JSONObject, key: String): String = if (!row.has(key) || row.isNull(key)) "" else row.opt(key).toString()

/** Projected calendar lists; details and writes use the same guarded planning commands as MCP. */
@Composable
internal fun NativePlanningScreen(section: String, id: String, controller: NativeAppController) {
    val listStates = rememberSaveableStateHolder()
    val repository = remember(controller.store) { NativeDataRepository(controller.store) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(section, id) { mutableStateOf(id.takeUnless { it == "new" }.orEmpty()) }
    var edit by remember { mutableStateOf<PlanningEdit?>(null) }
    var editorTarget by rememberSaveable(section, id) { mutableStateOf<String?>(null) }
    var editorDraftKey by rememberSaveable(section, id) { mutableStateOf("") }
    var routeEditorOpened by rememberSaveable(section, id, controller.route.mode) { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    var weatherView by rememberSaveable(section) { mutableStateOf(section == "weather") }
    fun openEditor(recordId: String = "", seed: JSONObject = controller.route.args, draftKey: String = "") {
        editorTarget = recordId
        val key = draftKey.ifEmpty { "planning:appointment:" + recordId.ifEmpty { "new" } }
        editorDraftKey = key
        scope.launch {
            try {
                val draft = controller.draft(key)
                val result = if (recordId.isEmpty()) null else controller.query("get_appointment", JSONObject().put("appointment_id", recordId))
                edit = if (draft != null) PlanningEdit.restore(key, draft) else PlanningEdit.create(key, result, seed)
                failure = ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error.message.orEmpty() }
        }
    }
    BackHandler(edit != null || selected.isNotEmpty()) {
        val open = edit
        if (open != null) { open.retain(controller); edit = null; editorTarget = null } else selected = ""
    }
    LaunchedEffect(id, controller.route.mode) {
        val restoring = editorTarget
        if (restoring != null) openEditor(restoring, draftKey = editorDraftKey)
        else if (!routeEditorOpened && (id == "new" || controller.route.mode == "new" || controller.route.mode == "edit")) {
            routeEditorOpened = true
            openEditor(id.takeUnless { it == "new" }.orEmpty(), draftKey = controller.route.args.optString("_draft_key"))
        }
    }
    when {
        edit != null -> PlanningEditor(controller, repository, edit!!, onClose = { edit = null; editorTarget = null }, onSaved = { result ->
            selected = result.optString("appointment_id", edit?.original?.optString("id").orEmpty()); edit = null; editorTarget = null
        })
        selected.isNotEmpty() -> PlanningDetail(controller, repository, selected, onBack = { selected = "" }, onEdit = { openEditor(selected) }, onAlternative = { proposal ->
            scope.launch {
                try {
                    val result = controller.query("get_appointment", JSONObject().put("appointment_id", selected))
                    val editor = PlanningEdit.create("planning:appointment:$selected", result, JSONObject())
                    editor.change("local_start", planningLocal(proposal.getString("starts_at")), controller)
                    editor.change("end_open", false, controller)
                    editor.change("duration_hours", (Duration.between(AppointmentRules.instant(proposal.getString("starts_at")), AppointmentRules.instant(proposal.getString("ends_at"))).toMinutes() / 60.0).toString(), controller)
                    editorTarget = selected; editorDraftKey = editor.key; edit = editor
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { failure = error.message.orEmpty() }
            }
        })
        else -> listStates.SaveableStateProvider("planning-list") { PlanningList(controller, repository, weatherView, { weatherView = it }, onSelect = { selected = it }, onNew = { openEditor() }) }
    }
    if (failure.isNotEmpty()) Text(failure, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningList(controller: NativeAppController, repository: NativeDataRepository, weatherView: Boolean, onWeather: (Boolean) -> Unit, onSelect: (String) -> Unit, onNew: () -> Unit) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var anchor by rememberSaveable { mutableStateOf(controller.route.args.optString("date", controller.route.args.optString("date_from", planningDay().toString()))) }
    var week by rememberSaveable { mutableStateOf(true) }
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var customer by rememberSaveable { mutableStateOf(controller.route.args.optString("customer_id")) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var context by remember { mutableStateOf<JSONObject?>(null) }
    var savedDraft by remember { mutableStateOf(false) }
    val day = runCatching { LocalDate.parse(anchor) }.getOrDefault(planningDay())
    val from = if (week) day.minusDays((day.dayOfWeek.value - 1).toLong()) else day
    val to = if (week) from.plusDays(6) else day
    val flow = remember(search, status, customer, offset, from, to) { repository.watchList("appointments", search = search, status = status, customerId = customer, offset = offset, limit = 50, dateFrom = from.toString(), dateTo = to.toString(), orderId = controller.route.args.optString("order_id"), chronological = true) }
    val page by flow.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, offset, 50))
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    LaunchedEffect(refresh, from, to, weatherView) {
        try {
            context = controller.query(if (weatherView) "get_workweek_weather" else "get_planning_settings", if (weatherView) JSONObject().put("date_from", from.toString()).put("date_to", to.toString()) else JSONObject())
            savedDraft = controller.draft("planning:appointment:new") != null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message.orEmpty() }
    }
    val risks = remember(context?.toString()) { context?.optJSONArray("appointments")?.planningRows()?.associateBy { it.getJSONObject("appointment").optString("id") }.orEmpty() }
    Column(Modifier.fillMaxSize()) {
        NativeField("Suche", search, { search = it; offset = 0 }, search = true, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${page.total} Treffer · ${page.rows.size} auf dieser Seite", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton({ filtersOpen = !filtersOpen }) { Text(if (filtersOpen) "Filter schließen" else "Zeitraum / Filter") }
        }
        if (!imeVisible) Text(if (from == to) from.toString() else "$from – $to", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!imeVisible) item {
              NativeContext(controller, JSONObject(controller.route.args.toString()).put("customer_id", customer))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  TextButton({ controller.navigate("tour", args = JSONObject().put("date", anchor).put("customer_id", customer)) }) { Text("Tagestour") }
                  TextButton({ controller.navigate("visits", args = JSONObject().put("customer_id", customer).put("date_from", from.toString()).put("date_to", to.toString())) }) { Text("Einsätze") }
                  TextButton({ onWeather(!weatherView) }) { Text(if (weatherView) "Termine" else "Wetterhinweise") }
              }
          }
          if (!imeVisible) item { NativeRelatedList(controller, "visits", "Geplante Einsätze", JSONObject().put("customer_id", customer).put("date_from", from.toString()).put("date_to", to.toString())) }
          if (filtersOpen && !imeVisible) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (weatherView) "Wetter & Einsätze" else "Planung", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ anchor = day.minusDays(if (week) 7 else 1).toString(); offset = 0 }) { Text("‹") }
                TextButton({ anchor = planningDay().toString(); offset = 0 }) { Text("Heute") }
                OutlinedButton({ anchor = day.plusDays(if (week) 7 else 1).toString(); offset = 0 }) { Text("›") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !week, onClick = { week = false; offset = 0 }, label = { Text("Tag") })
                FilterChip(selected = week, onClick = { week = true; offset = 0 }, label = { Text("Woche") })
                Text(if (from == to) from.toString() else "$from – $to", Modifier.padding(top = 12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !weatherView, onClick = { onWeather(false) }, label = { Text("Termine") })
                FilterChip(selected = weatherView, onClick = { onWeather(true) }, label = { Text("Wetter") })
            }
            TextButton({ controller.open(PlanningConnectionActivity::class.java) }) { Text("Kalender & Wetter") }
            PlanningPicker("Kundenfilter", "customers", customer, controller, repository, allowEmpty = true) { customer = it?.id.orEmpty(); offset = 0 }
            PlanningChoice("Status", status, linkedMapOf("" to "Alle") + planningStatuses) { status = it; offset = 0 }
            Text("${page.total} Treffer · ${page.rows.size} auf dieser Seite")
            if (search.isNotEmpty() || customer.isNotEmpty() || status.isNotEmpty()) TextButton({ search = ""; customer = ""; status = ""; offset = 0 }) { Text("Filter zurücksetzen") }
            val integration = context?.optJSONObject("integration")
            if (integration != null) Text("Google Kalender: " + integration.optString("calendar_state", "unbekannt") + " · Belegtzeiten: " + integration.optString("calendar_busy_state", "ungeprüft"))
            if (weatherView) Text("Wetter optional · gewerbliche API-Nutzung ungeklärt. Fehlende oder veraltete Wetterdaten bleiben unbekannt. Ein Wetterhinweis verschiebt keinen Termin.")
          } }
            if (failure.isNotEmpty()) item { Text(failure, color = MaterialTheme.colorScheme.error) }
            if (page.rows.isEmpty()) item { Text("Keine Termine in diesem Zeitraum. Zeitraum und Filter prüfen.") }
            items(page.rows.sortedBy { it.workDate }, key = { it.id }) { row ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { onSelect(row.id) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(nativeDisplayText(row.subtitle))
                        NativeStatus(planningStatuses[row.status] ?: row.status)
                        val entry = risks[row.id]; val risk = entry?.optJSONObject("weather")
                        if (weatherView) {
                            Text(risk?.optString("label") ?: "Wetter im Detail prüfen")
                            if (risk?.optString("location_precision") == "locality") Text("Ortsvorhersage · " + risk.optString("location_area"))
                            val reasons = risk?.optJSONArray("reasons"); if (reasons != null) for (i in 0 until reasons.length()) Text(reasons.optString(i), style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry?.optJSONObject("calendar")?.optString("state") == "review_required") Text("Kalenderänderung prüfen", color = MaterialTheme.colorScheme.error)
                        if ((entry?.optJSONObject("collisions")?.optJSONArray("conflicts")?.length() ?: 0) > 0) Text("Mögliche Terminkollision prüfen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ offset = maxOf(0, offset - 50) }, enabled = offset > 0) { Text("Vorige Seite") }
                OutlinedButton({ offset += 50 }, enabled = page.hasMore) { Text("Weitere") }
            } }
        }
        if (!imeVisible) NativeActions {
            Button(onNew, enabled = !busy) { Text(if (savedDraft) "Entwurf fortsetzen" else "Einsatz planen") }
            OutlinedButton({ scope.launch { busy = true; try {
                val result = controller.query("request_calendar_sync", JSONObject().put("request_id", planningRequest()))
                controller.message(if (result.optString("state") == "configuration_required") "Kalender oder Wetter zuerst einrichten." else "Prüfung angefordert. Abschluss hängt von Geräteplanung und Netz ab."); refresh++
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() } finally { busy = false } } }, enabled = !busy) { Text("Jetzt prüfen") }
        }
    }
}

@Composable
private fun PlanningDetail(controller: NativeAppController, repository: NativeDataRepository, id: String, onBack: () -> Unit, onEdit: () -> Unit, onAlternative: (JSONObject) -> Unit) {
    val watched by remember(id) { repository.watchDetail("appointments", id) }.collectAsStateWithLifecycle(null)
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember(id) { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Triple<String, JSONObject, String>?>(null) }
    var suggestions by rememberSaveable(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, controller.route.args) {
        if (controller.route.args.optString("action") == "alternatives") {
            suggestions = true
            controller.route.args.remove("action")
        }
    }
    LaunchedEffect(id, refresh, watched?.toString()) {
        try { data = controller.query("get_appointment", JSONObject().put("appointment_id", id)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message.orEmpty() }
    }
    val result = data
    if (result == null) { Column(Modifier.padding(16.dp)) { if (failure.isEmpty()) CircularProgressIndicator() else Text(failure); TextButton(onBack) { Text("Zur Planung") } }; return }
    val row = result.getJSONObject("appointment"); val risk = result.optJSONObject("weather") ?: JSONObject(); val calendar = result.optJSONObject("calendar") ?: JSONObject()
    val action = confirm
    if (action != null) AlertDialog(onDismissRequest = { confirm = null }, text = { Text(action.third) }, dismissButton = { TextButton({ confirm = null }) { Text("Abbrechen") } }, confirmButton = {
        TextButton({
            confirm = null; busy = true
            scope.launch {
                val key = "planning:action:$id:${action.first}"
                var mutationAttempted = false
                try {
                    val saved = controller.draft(key)
                    if (saved != null) {
                        val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", saved.getString("tool")).put("original_args", saved.getJSONObject("args")))
                        if (proof.optString("state") == "confirmed") {
                            controller.removeDraft(key); controller.message("Der bereits gespeicherte Vorgang wurde gefunden.")
                            if (action.first == "delete_entity") onBack() else if (action.first == "start_work") controller.navigate("today") else refresh++
                            return@launch
                        }
                        require(proof.optString("state") != "content_removed") { "Der ursprüngliche Beleg wurde entfernt. Aktuellen Termin prüfen; keine erneute Übernahme." }
                        if (saved.optBoolean("_restore_action_review_required")) error("Diese unbestätigte Aktion stammt aus einem anderen Gerätestand. Aktuellen Termin und Vorgang zuerst prüfen; keine automatische Wiederholung.")
                    }
                    val args = saved?.optJSONObject("args") ?: action.second.planningCopy().put("request_id", planningRequest())
                    controller.saveDraft(key, JSONObject().put("tool", action.first).put("args", args)); controller.flushDrafts()
                    mutationAttempted = true
                    controller.query(action.first, args); controller.removeDraft(key)
                    controller.message(when (action.first) { "start_work" -> "Zeiterfassung für diesen Einsatz gestartet."; "cancel_appointment" -> "Termin lokal abgesagt. Kalenderabgleich folgt."; "resolve_calendar_conflict" -> "Entscheidung gespeichert. Google-Version wird vor Verarbeitung erneut geprüft."; "acknowledge_weather_alert" -> "Kenntnisnahme gespeichert. Termin bleibt unverändert."; else -> "Eintrag im Papierkorb. Original bleibt erhalten." })
                    if (action.first == "delete_entity") onBack() else if (action.first == "start_work") controller.navigate("today") else refresh++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (mutationAttempted && error is NativeCallFailure && error.detail.optString("commit_state") == "not_committed") controller.removeDraft(key)
                    failure = error.message.orEmpty()
                } finally { busy = false }
            }
        }) { Text("Bestätigen") }
    })
    if (suggestions) PlanningSuggestions(controller, row, { suggestions = false }, { proposal -> suggestions = false; onAlternative(proposal) })
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(row.optString("title", "Termin"), style = MaterialTheme.typography.headlineSmall)
            Text(planningStamp(row.optString("starts_at")) + " – " + if (AppointmentRules.openEnd(row)) "Ende offen" else planningStamp(row.optString("ends_at")))
            Text(planningStatuses[row.optString("status")] ?: row.optString("status"))
            Text(row.optString("location").ifEmpty { "Einsatzort offen" })
            Text("Geplant: " + (if (result.isNull("planned_hours")) "offen" else result.opt("planned_hours").toString() + " h") + " · tatsächlich erfasst: " + result.optDouble("actual_hours", 0.0) + " h")
            if (row.optInt("break_minutes") > 0) Text("Geplante Pause: ${row.optInt("break_minutes")} min")
            if (row.optInt("travel_minutes") > 0) Text("Anfahrtpuffer: ${row.optInt("travel_minutes")} min")
            if (row.optString("notes").isNotEmpty()) Text(row.optString("notes"))
            Text("Geplante Zeit ist kein Arbeitsnachweis. Ein Vormerken ist keine Kundenzusage.", style = MaterialTheme.typography.bodySmall)
            val collisions = result.optJSONObject("collisions")
            PlanningCollisions(collisions)
            HorizontalDivider()
            PlanningWeather(risk)
            OutlinedButton({ suggestions = true }, enabled = !busy) { Text("Alternative suchen") }
            if (risk.optString("alert_hash").isNotEmpty()) OutlinedButton({ confirm = Triple("acknowledge_weather_alert", JSONObject().put("appointment_id", id).put("expected_alert_hash", risk.getString("alert_hash")), "Diesen Wetterhinweis als geprüft markieren? Der Termin bleibt unverändert.") }, enabled = !busy) { Text("Wetterhinweis geprüft") }
            HorizontalDivider()
            Text("Google Kalender: " + when (calendar.optString("state")) { "confirmed" -> "synchronisiert"; "pending" -> "wird später aktualisiert"; "not_connected" -> "nicht verbunden"; "review_required" -> "Änderung prüfen"; "detached" -> "bewusst nicht verbunden"; "cancelled" -> "Absage bestätigt"; else -> calendar.optString("state", "Status prüfen") })
            val conflict = calendar.optJSONObject("conflict")
            if (conflict != null) {
                val remote = conflict.optJSONObject("remote")
                Text("Kalenderänderung vergleichen", style = MaterialTheme.typography.titleMedium)
                Text("Deine App: ${row.optString("title")}\n${planningStamp(row.optString("starts_at"))} – ${if (AppointmentRules.openEnd(row)) "Ende offen" else planningStamp(row.optString("ends_at"))}\n${row.optString("location")}")
                Text(if (remote == null) "Google Kalender: Eintrag entfernt" else "Google Kalender: ${remote.optString("summary")}\n${planningStamp(remote.optJSONObject("start")?.optString("dateTime").orEmpty())} – ${planningStamp(remote.optJSONObject("end")?.optString("dateTime").orEmpty())}\n${remote.optString("location")}")
                for ((choice, label) in listOf("keep_app" to "Meine App-Version behalten", "use_google" to "Google-Version übernehmen", "keep_without_calendar" to "Nur in der App behalten"))
                    OutlinedButton({ confirm = Triple("resolve_calendar_conflict", JSONObject().put("appointment_id", id).put("expected_record_hash", result.getString("record_hash")).put("expected_conflict_hash", calendar.getString("conflict_hash")).put("resolution", choice).put("confirmed", true), "Diese Variante auf Basis des angezeigten Vergleichs verwenden? Bei zwischenzeitlicher Änderung wird erneut geprüft." + if (choice == "keep_without_calendar") " Der Termin wird nicht mehr mit Google abgeglichen." else "") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
            TextButton({ controller.open(PlanningConnectionActivity::class.java) }) { Text("Kalender & Wetter einrichten") }
            OutlinedButton({ confirm = Triple("cancel_appointment", JSONObject().put("appointment_id", id).put("expected_record_hash", result.getString("record_hash")), "Diesen Termin absagen? Auftrag und erfasste Arbeiten bleiben erhalten.") }, enabled = !busy && row.optString("status") != "cancelled") { Text("Termin absagen") }
            OutlinedButton({ scope.launch {
                try {
                    val preview = controller.query("preview_delete", JSONObject().put("entity", "appointment").put("entity_id", id))
                    if (!preview.optBoolean("allowed")) error(preview.optString("message", "Eintrag kann derzeit nicht entfernt werden."))
                    val args = JSONObject().put("entity", "appointment").put("entity_id", id).put("expected_record_hash", preview.getString("expected_record_hash"))
                    if (preview.has("expected_photo_links_hash")) args.put("expected_photo_links_hash", preview.getString("expected_photo_links_hash"))
                    confirm = Triple("delete_entity", args, preview.optString("message", "Diesen Termin in den Papierkorb verschieben?"))
                } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
            } }, enabled = !busy) { Text("In Papierkorb") }
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
        }
        NativeActions {
            TextButton(onBack) { Text("Zur Planung") }
            OutlinedButton(onEdit, enabled = !busy) { Text("Bearbeiten") }
            if (row.optString("status") !in listOf("draft", "completed", "cancelled")) Button({
                val args = JSONObject().put("appointment_id", id).put("customer_id", row.getString("customer_id")).put("location", row.optString("location")).put("activity", row.optString("title")).put("work_date", planningDay().toString())
                if (row.optString("order_id").isNotEmpty()) args.put("order_id", row.getString("order_id"))
                confirm = Triple("start_work", args, "Zeiterfassung für diesen Einsatz starten? Eine bereits laufende oder noch offene Erfassung muss zuerst abgeschlossen werden.")
            }, enabled = !busy) { Text("Arbeit starten") }
        }
    }
}

@Composable
private fun PlanningCollisions(report: JSONObject?) {
    if (report == null) { Text("Belegung noch nicht geprüft. Externe Überschneidungen sind möglich."); return }
    val conflicts = report.optJSONArray("conflicts")?.planningRows().orEmpty()
    Text(if (report.optBoolean("inactive")) "Dieser Termin belegt keine Einsatzzeit." else if (conflicts.any { !it.optBoolean("uncertain") }) "Terminkollision" else if (conflicts.isNotEmpty()) "Mögliche Terminkollision" else if (!report.optBoolean("app_checked", true)) (if (report.optBoolean("duration_unknown")) "Ende offen · Belegung noch ungeklärt" else "Belegung noch nicht vollständig geprüft") else "Keine Überschneidung in der App")
    for (conflict in conflicts.take(5)) Text((if (conflict.optString("source") == "google") "Google Kalender: belegt" else "Vorhandener Einsatz") + " · " + planningStamp(conflict.optString("starts_at")) + " – " + (if (conflict.optString("ends_at").isEmpty()) "Ende offen" else planningStamp(conflict.optString("ends_at"))) + (if (conflict.optBoolean("uncertain")) " · mögliche Überschneidung" else if (conflict.optBoolean("travel_only")) " · Anfahrtpuffer" else ""))
    if (conflicts.size > 5) Text("${conflicts.size - 5} weitere Überschneidungen")
    Text(if (report.optBoolean("external_checked")) "Google-Belegung geprüft · " + planningStamp(report.optString("checked_at")) else "Google-Belegung nicht geprüft. Externe Terminkollisionen sind möglich.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PlanningWeather(risk: JSONObject) {
    Text(risk.optString("label", "Wetter unbekannt"), style = MaterialTheme.typography.titleMedium)
    Text("Einsatzgebiet: " + risk.optString("location_area", risk.optString("location_label", "Adresse fehlt")))
    if (risk.optString("location_notice").isNotEmpty()) Text(risk.optString("location_notice"))
    val reasons = risk.optJSONArray("reasons"); if (reasons != null) for (i in 0 until reasons.length()) Text(reasons.optString(i))
    Text(if (risk.optString("checked_at").isEmpty()) "Noch keine Vorhersage" else "Prüfstand " + planningStamp(risk.optString("checked_at")))
    if (risk.optString("warning_status").isNotEmpty()) Text(risk.optString("warning_status"))
    if (risk.optBoolean("forecast_available") && risk.optString("forecast_valid_until").isNotEmpty()) Text("Vorhersage reicht bis " + planningStamp(risk.optString("forecast_valid_until")) + if (risk.optBoolean("forecast_complete")) "" else " · teilweise unvollständig")
    if (risk.optString("forecast_notice").isNotEmpty()) Text(risk.optString("forecast_notice"))
    if (risk.optBoolean("uncertain_long_range")) Text("Früher Wettertrend: erhöhte Unsicherheit. Wird näher am Termin erneut geprüft.")
    Text(risk.optString("source", OpenMeteoWeatherSource.ATTRIBUTION), style = MaterialTheme.typography.bodySmall)
    Text("Wetter optional · gewerbliche API-Nutzung ungeklärt. Open-Meteo stellt den öffentlichen Endpunkt für nichtkommerzielle Nutzung bereit. Die lokale Planung funktioniert ohne Wetter. Bedingungen: https://open-meteo.com/en/terms", style = MaterialTheme.typography.bodySmall)
}

private class PlanningEdit(
    val key: String, val original: JSONObject, initial: JSONObject, val observedHash: String,
    request: String, pendingValue: JSONObject? = null, restored: Boolean = false, initiallyDirty: Boolean = false
) {
    var values by mutableStateOf(initial)
    var requestId by mutableStateOf(request)
    var pending by mutableStateOf(pendingValue)
    var restoreReview by mutableStateOf(restored)
    var dirty by mutableStateOf(initiallyDirty)
    fun saved(): JSONObject = JSONObject().put("original", original).put("values", values).put("record_hash", observedHash).put("request_id", requestId).put("pending", pending ?: JSONObject.NULL).put("restore_review_required", restoreReview).put("dirty", dirty)
    fun retain(controller: NativeAppController) { if (dirty || pending != null) controller.saveDraft(key, saved()) }
    fun change(key: String, value: Any, controller: NativeAppController) {
        if (pending != null) return
        values = values.planningCopy().put(key, value); dirty = true; requestId = planningRequest(); retain(controller)
    }
    fun businessValues(): JSONObject {
        val row = JSONObject()
        for (key in AppointmentRules.FIELDS) if (values.has(key) && key !in listOf("starts_at", "ends_at")) row.put(key, values.get(key))
        val zone = values.optString("timezone", "Europe/Vienna"); val local = values.getString("local_start"); val offset = values.optString("utc_offset")
        val sameStart = original.optString("starts_at").isNotEmpty() && local == planningLocal(original.getString("starts_at")) && offset.isEmpty()
        val start = if (sameStart) AppointmentRules.instant(original.getString("starts_at")) else AppointmentRules.local(local, zone, offset)
        row.put("starts_at", if (sameStart) original.getString("starts_at") else start.toString())
        if (values.optBoolean("end_open")) row.put("ends_at", "") else {
            val minutes = Math.round(values.getString("duration_hours").replace(',', '.').toDouble() * 60)
            require(minutes in 5..1440) { "Bitte eine Dauer von 5 Minuten bis 24 Stunden eintragen oder Ende offen wählen." }
            val sameDuration = !AppointmentRules.openEnd(original) && original.optString("ends_at").isNotEmpty() && minutes == Duration.between(AppointmentRules.instant(original.getString("starts_at")), AppointmentRules.instant(original.getString("ends_at"))).toMinutes()
            row.put("ends_at", if (sameStart && sameDuration) original.getString("ends_at") else start.plusSeconds(minutes * 60).toString())
        }
        for (key in arrayOf("latitude", "longitude")) { val value = planningText(values, key); row.put(key, if (value.isBlank()) JSONObject.NULL else value.toDouble()) }
        for (key in arrayOf("travel_minutes", "break_minutes")) row.put(key, planningText(values, key).ifBlank { "0" }.toInt())
        return row
    }
    fun freeze(): JSONObject? {
        pending?.let { return it }
        val row = businessValues(); var tool = "create_appointment"; var args = row
        if (original.optString("id").isNotEmpty()) {
            val updates = JSONObject()
            for (key in row.keys()) if (PixelMcpWriteTools.hash(row.get(key)) != PixelMcpWriteTools.hash(original.opt(key) ?: JSONObject.NULL)) updates.put(key, row.get(key))
            if (updates.length() == 0) return null
            tool = "update_appointment"; args = JSONObject().put("appointment_id", original.getString("id")).put("expected_record_hash", observedHash).put("updates", updates)
        } else {
            val count = planningText(values, "occurrences").ifBlank { "1" }.toInt()
            require(count in 1..26) { "Anzahl Einsätze muss zwischen 1 und 26 liegen." }
            if (count > 1) { tool = "create_appointment_series"; args.put("occurrences", count).put("interval_weeks", planningText(values, "interval_weeks").ifBlank { "1" }.toInt()) }
        }
        args.put("request_id", requestId); McpSchema.validate(args, AppointmentTools.schema(tool))
        return JSONObject().put("tool", tool).put("args", args).also { pending = it }
    }
    companion object {
        fun create(key: String, result: JSONObject?, seed: JSONObject): PlanningEdit {
            val original = result?.getJSONObject("appointment")?.planningCopy() ?: JSONObject()
            val values = original.planningCopy()
            for (field in arrayOf("customer_id", "order_id", "title", "location", "notes", "flexible_from", "flexible_until")) if (!values.has(field)) values.put(field, seed.optString(field))
            for ((field, value) in mapOf("timezone" to "Europe/Vienna", "status" to "tentative", "purpose" to "work", "weather_profile" to "general", "travel_minutes" to 0, "break_minutes" to 0)) if (!values.has(field)) values.put(field, if (seed.has(field)) seed.get(field) else value)
            for (field in arrayOf("latitude", "longitude")) if (!values.has(field)) values.put(field, seed.opt(field) ?: JSONObject.NULL)
            val start = original.optString("starts_at", seed.optString("starts_at"))
            values.put("local_start", if (start.isEmpty()) planningDay().toString() + "T09:00" else planningLocal(start))
            val end = original.optString("ends_at", seed.optString("ends_at"))
            values.put("end_open", end.isEmpty()).put("duration_hours", if (start.isEmpty() || end.isEmpty()) "" else (Duration.between(AppointmentRules.instant(start), AppointmentRules.instant(end)).toMinutes() / 60.0).toString())
                .put("utc_offset", "").put("occurrences", 1).put("interval_weeks", 1)
            return PlanningEdit(key, original, values, result?.optString("record_hash") ?: "", planningRequest())
        }
        fun restore(key: String, saved: JSONObject): PlanningEdit = PlanningEdit(key, saved.getJSONObject("original"), saved.getJSONObject("values"), saved.optString("record_hash"), saved.getString("request_id"), saved.optJSONObject("pending"), saved.optBoolean("restore_review_required") || saved.optBoolean("_restore_action_review_required"), saved.optBoolean("dirty"))
    }
}

@Composable
private fun PlanningEditor(controller: NativeAppController, repository: NativeDataRepository, edit: PlanningEdit, onClose: () -> Unit, onSaved: (JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var locationConfirmation by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(false) }
    var collision by remember { mutableStateOf<JSONObject?>(null) }
    val values = edit.values; val existing = edit.original.optString("id").isNotEmpty(); val enabled = !busy && edit.pending == null
    fun change(key: String, value: Any) { edit.change(key, value, controller); failure = "" }
    LaunchedEffect(values.toString()) {
        collision = try {
            val candidate = edit.businessValues().put("id", edit.original.optString("id", "ui-draft"))
            val availability = withContext(Dispatchers.IO) { AppointmentRuntime.availability(controller.context, controller.store, Instant.now()) }
            val from = AppointmentRules.instant(candidate.getString("starts_at")).atZone(planningZone).toLocalDate()
            val end = if (AppointmentRules.openEnd(candidate)) from else AppointmentRules.instant(candidate.getString("ends_at")).atZone(planningZone).toLocalDate()
            val page = repository.list("appointments", dateFrom = from.minusDays(1).toString(), dateTo = end.toString(), limit = 100)
            val appointments = JSONArray()
            for (row in page.rows) repository.detail("appointments", row.id)?.let { appointments.put(it) }
            val report = withContext(Dispatchers.IO) { AppointmentAvailability.assess(candidate, appointments, availability) }
            report.put("app_checked", false).put("display_incomplete", true)
            report
        } catch (cancelled: CancellationException) { throw cancelled } catch (invalid: Exception) { null }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, text = { Text("Nur diesen ungespeicherten Entwurf verwerfen? Gespeicherter Termin bleibt erhalten.") }, dismissButton = { TextButton({ discard = false }) { Text("Abbrechen") } }, confirmButton = { TextButton({ scope.launch { controller.removeDraft(edit.key); discard = false; onClose() } }) { Text("Verwerfen") } })
    if (locationConfirmation) AlertDialog(onDismissRequest = { locationConfirmation = false }, text = { Text("Bist du am Einsatzort dieses Kunden? Nur dann den aktuellen Gerätestandort übernehmen.") }, dismissButton = { TextButton({ locationConfirmation = false }) { Text("Abbrechen") } }, confirmButton = { TextButton({
        locationConfirmation = false
        scope.launch { try {
            val location = controller.location()
            require(location.optString("status") == "OK" && WeatherLocationRules.coordinates(location)) { "Standort konnte nicht bestimmt werden. Termin kann ohne Wetter gespeichert werden." }
            change("latitude", location.getDouble("latitude")); change("longitude", location.getDouble("longitude")); controller.message("Standort übernommen. Einsatzort vor dem Speichern prüfen.")
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() } }
    }) { Text("Standort übernehmen") } })
    if (suggestions) {
        val source = runCatching { edit.businessValues() }.getOrElse { values.planningCopy() }
        PlanningSuggestions(controller, source, { suggestions = false }, { proposal ->
            suggestions = false; change("local_start", planningLocal(proposal.getString("starts_at"))); change("end_open", false)
            change("duration_hours", (Duration.between(AppointmentRules.instant(proposal.getString("starts_at")), AppointmentRules.instant(proposal.getString("ends_at"))).toMinutes() / 60.0).toString())
        })
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (existing) "Termin bearbeiten" else "Einsatz planen", style = MaterialTheme.typography.headlineSmall)
            if (edit.restoreReview) Text("Dieser Entwurf stammt aus einem wiederhergestellten Gerätestand. Angaben und gespeicherten Termin ausdrücklich prüfen. Eine alte unbestätigte Speicherung wird nicht automatisch wiederholt.", color = MaterialTheme.colorScheme.error)
            PlanningPicker("Kunde", "customers", values.optString("customer_id"), controller, repository, enabled = enabled && !existing) { row ->
                change("customer_id", row?.id.orEmpty()); change("order_id", "")
                change("location", row?.address.orEmpty()); change("latitude", JSONObject.NULL); change("longitude", JSONObject.NULL)
            }
            PlanningField("Datum und Startzeit (z. B. 2027-05-19T09:00)", planningText(values, "local_start"), { change("local_start", it) }, enabled)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((hours, label) in listOf("1" to "1 Std", "2" to "2 Std", "4" to "Halber Tag")) OutlinedButton({ change("duration_hours", hours); change("end_open", false) }, enabled = enabled) { Text(label) }
            }
            Row { Checkbox(values.optBoolean("end_open"), { change("end_open", it) }, enabled = enabled); Text("Ende offen · Dauer noch ungeklärt", Modifier.padding(top = 12.dp)) }
            if (!values.optBoolean("end_open")) PlanningField("Dauer inkl. Pause in Stunden (z. B. 1,5)", planningText(values, "duration_hours"), { change("duration_hours", it) }, enabled)
            PlanningField("Titel / Tätigkeit", values.optString("title"), { change("title", it) }, enabled)
            PlanningCollisions(collision)
            if (collision?.optBoolean("display_incomplete") == true) Text("Die Vorabprüfung zeigt Termine im nahen Zeitraum. Beim Speichern wird der vollständige Bestand erneut geprüft.")
            OutlinedButton({ suggestions = true }, enabled = enabled && values.optString("customer_id").isNotEmpty()) { Text("Freie Zeit suchen") }
            PlanningPicker("Auftrag (optional)", "orders", values.optString("order_id"), controller, repository, enabled = enabled && !existing, customerId = values.optString("customer_id"), allowEmpty = true) { order ->
                change("order_id", order?.id.orEmpty())
                if (order != null) {
                    change("title", order.title)
                    scope.launch { repository.detail("orders", order.id)?.let { record ->
                        if (record.optString("location").isNotEmpty() && record.optString("location") != values.optString("location")) { change("location", record.getString("location")); change("latitude", JSONObject.NULL); change("longitude", JSONObject.NULL) }
                    } }
                }
            }
            PlanningChoice("Status", values.optString("status"), planningStatuses, enabled) { change("status", it) }
            PlanningChoice("Zweck", values.optString("purpose"), linkedMapOf("work" to "Arbeitseinsatz", "inspection" to "Besichtigung"), enabled) { change("purpose", it) }
            PlanningField("Einsatzort", values.optString("location"), { change("location", it); change("latitude", JSONObject.NULL); change("longitude", JSONObject.NULL) }, enabled)
            PlanningChoice("Wetterprofil", values.optString("weather_profile"), planningProfiles, enabled) { change("weather_profile", it) }
            Text("Die Einsatzadresse wird bei der Wetterprüfung gesucht. Koordinaten sind nur bei unklarer Adresse nötig.")
            for ((key, label) in listOf("latitude" to "Breitengrad", "longitude" to "Längengrad", "travel_minutes" to "Anfahrtpuffer vor dem Einsatz (Minuten)", "break_minutes" to "Geplante Pause (Minuten)", "flexible_from" to "Frühester vereinbarter Arbeitstag (JJJJ-MM-TT)", "flexible_until" to "Spätester vereinbarter Arbeitstag (JJJJ-MM-TT)", "utc_offset" to "Bei Zeitumstellung: UTC-Offset (z. B. +02:00)"))
                PlanningField(label, planningText(values, key), { change(key, it) }, enabled)
            OutlinedButton({ locationConfirmation = true }, enabled = enabled) { Text("Ich bin am Einsatzort – Standort übernehmen") }
            if (!existing) {
                Text("Termin wiederholen (optional)", style = MaterialTheme.typography.titleMedium)
                PlanningField("Anzahl Einsätze (1–26)", planningText(values, "occurrences"), { change("occurrences", it) }, enabled)
                PlanningField("Abstand in Wochen (1–12)", planningText(values, "interval_weeks"), { change("interval_weeks", it) }, enabled)
            }
            OutlinedTextField(values.optString("notes"), { change("notes", it) }, label = { Text("Notizen") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Text("Bestätigt bedeutet: Kundenzusage liegt vor. Es wird keine Nachricht gesendet. Keine Ist-Stunden oder Rechnung.", style = MaterialTheme.typography.bodySmall)
            if (edit.restoreReview && edit.pending == null) OutlinedButton({
                scope.launch {
                    busy = true
                    try {
                        val reviewed = edit.saved().put("restore_review_required", false)
                        controller.adoptDraft(edit.key, reviewed); edit.restoreReview = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failure = error.message.orEmpty() }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text("Angaben nach Wiederherstellung geprüft") }
            if (failure.isNotEmpty()) Text(failure + " Dein Entwurf bleibt erhalten.", color = MaterialTheme.colorScheme.error)
            TextButton({ if (edit.pending == null) discard = true else failure = "Zuerst dieselbe Speicherung erneut prüfen." }, enabled = !busy) { Text("Ungespeicherten Entwurf verwerfen") }
        }
        NativeActions {
            OutlinedButton({ edit.retain(controller); onClose() }, enabled = !busy) { Text("Zur Planung") }
            Button({
                busy = true
                scope.launch {
                    var mutationAttempted = false
                    try {
                        val prior = edit.pending
                        if (prior != null) {
                            val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", prior.getString("tool")).put("original_args", prior.getJSONObject("args")))
                            if (proof.optString("state") == "confirmed") {
                                controller.removeDraft(edit.key); edit.pending = null
                                controller.message("Der bereits gespeicherte Termin wurde gefunden.")
                                onSaved(proof.optJSONObject("result") ?: proof); return@launch
                            }
                            require(proof.optString("state") != "content_removed") { "Der ursprüngliche Beleg wurde entfernt. Aktuellen Termin prüfen; keine erneute Übernahme." }
                        }
                        require(!edit.restoreReview) { "Gespeicherten Stand und alte Aktion nach Wiederherstellung zuerst prüfen. Kein eindeutiger Speicherbeleg vorhanden." }
                        val action = withContext(Dispatchers.IO) { edit.freeze() }
                        if (action == null) { controller.removeDraft(edit.key); onClose(); return@launch }
                        edit.dirty = true; edit.retain(controller); controller.flushDrafts()
                        mutationAttempted = true
                        val result = controller.query(action.getString("tool"), action.getJSONObject("args"))
                        controller.removeDraft(edit.key); edit.pending = null
                        controller.message("Termin auf diesem Gerät gespeichert. Kalenderübertragung wird gesondert geprüft."); onSaved(result)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (mutationAttempted && error is NativeCallFailure && error.detail.optString("commit_state") == "not_committed") edit.pending = null
                        edit.retain(controller); failure = error.message.orEmpty()
                    } finally { busy = false }
                }
            }, enabled = !busy) { Text(if (edit.pending == null) "Termin speichern" else if (edit.restoreReview) "Gespeicherten Vorgang prüfen" else "Dieselbe Speicherung erneut prüfen") }
        }
    }
}

@Composable
private fun PlanningSuggestions(controller: NativeAppController, source: JSONObject, onClose: () -> Unit, onSelect: (JSONObject) -> Unit) {
    var duration by remember { mutableStateOf(runCatching { if (AppointmentRules.openEnd(source)) "" else (Duration.between(AppointmentRules.instant(source.getString("starts_at")), AppointmentRules.instant(source.getString("ends_at"))).toMinutes() / 60.0).toString() }.getOrDefault("")) }
    var from by rememberSaveable { mutableStateOf(planningDay().toString()) }
    var to by rememberSaveable { mutableStateOf(planningDay().plusDays(13).toString()) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val proposal = selected
    if (proposal != null) AlertDialog(onDismissRequest = { selected = null }, text = { Text("Dieses Zeitfenster in den Entwurf übernehmen? Gespeichert wird erst mit Termin speichern. Kundenverfügbarkeit ist separat zu bestätigen.") }, dismissButton = { TextButton({ selected = null }) { Text("Abbrechen") } }, confirmButton = { TextButton({ onSelect(proposal); selected = null }) { Text("In Entwurf übernehmen") } })
    AlertDialog(onDismissRequest = onClose, title = { Text("Vorgeschlagene Zeitfenster") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanningField("Dauer in Stunden (5 Minuten bis 12 Stunden)", duration, { duration = it }, !busy)
            PlanningField("Von (JJJJ-MM-TT)", from, { from = it }, !busy)
            PlanningField("Bis (JJJJ-MM-TT)", to, { to = it }, !busy)
            OutlinedButton({ scope.launch {
                busy = true
                try {
                    require(source.optString("customer_id").isNotEmpty()) { "Zuerst einen eindeutigen Kunden wählen." }
                    val minutes = Math.round(duration.replace(',', '.').toDouble() * 60)
                    require(minutes in 5..720) { "Für Zeitfenster bitte 5 Minuten bis 12 Stunden angeben." }
                    val args = JSONObject().put("customer_id", source.getString("customer_id")).put("duration_minutes", minutes).put("date_from", from).put("date_to", to).put("weather_profile", source.optString("weather_profile", "general"))
                    if (source.optString("order_id").isNotEmpty()) args.put("order_id", source.getString("order_id"))
                    result = controller.query("suggest_appointment_slots", args); failure = ""
                } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() } finally { busy = false }
            } }, enabled = !busy) { Text("Zeitfenster prüfen") }
            result?.let { response ->
                Text(response.optString("notice"))
                val suggestions = response.optJSONArray("suggestions")?.planningRows().orEmpty()
                if (suggestions.isEmpty()) Text("Keine passenden Zeitfenster. Zeitraum, Dauer oder Belegung prüfen.")
                for (entry in suggestions) OutlinedCard(Modifier.fillMaxWidth().clickable { selected = entry.getJSONObject("appointment") }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(planningStamp(entry.getJSONObject("appointment").getString("starts_at")))
                        Text(entry.optJSONObject("weather")?.optString("label", "Wetter unbekannt").orEmpty())
                        Text(if (entry.optBoolean("calendar_availability_verified")) "Google-Belegtzeiten geprüft" else "Externe Belegtzeiten unbekannt", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClose) { Text("Schließen") } })
}

@Composable
private fun PlanningField(label: String, value: String, change: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PlanningChoice(label: String, value: String, choices: Map<String, String>, enabled: Boolean = true, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label + ": " + (choices[value] ?: value)) }
        DropdownMenu(expanded, { expanded = false }) {
            for ((key, title) in choices) DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; change(key) })
        }
    }
}

/** Picker reads fixed display columns and at most 30 rows; selected details load only on selection. */
@Composable
private fun PlanningPicker(label: String, collection: String, selected: String, controller: NativeAppController, repository: NativeDataRepository,
    enabled: Boolean = true, customerId: String = "", allowEmpty: Boolean = false, change: (NativeListRow?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedLabel by remember(selected) { mutableStateOf("") }
    LaunchedEffect(selected, collection) {
        selectedLabel = if (selected.isEmpty()) "" else try {
            repository.detail(collection, selected)?.let { it.optString("name").ifBlank { it.optString("title") } } ?: "Eintrag nicht mehr vorhanden"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "Ausgewählter Eintrag nicht lesbar" }
    }
    OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label + ": " + selectedLabel.ifEmpty { if (allowEmpty) "Keine Auswahl" else "Bitte wählen" }) }
    NativeRecordDialog(expanded, controller, collection, label, customerId = customerId, allowEmpty = allowEmpty, onDismiss = { expanded = false }, onChoose = change)
}
