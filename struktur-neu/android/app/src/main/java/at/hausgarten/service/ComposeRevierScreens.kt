package at.hausgarten.service

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.util.UUID

private data class RevierSection(val kind: String, val collection: String, val title: String, val singular: String)
private val revierSections = listOf(
    RevierSection("property", "properties", "Objekte", "Objekt"),
    RevierSection("inquiry", "inquiries", "Anfragen", "Anfrage"),
    RevierSection("care_plan", "care_plans", "Pflegepläne", "Pflegeplan"),
    RevierSection("visit", "visits", "Besuche", "Einsatz"),
    RevierSection("material", "materials", "Material", "Material"),
    RevierSection("equipment", "equipment", "Gerätewartung", "Gerät")
)
private val revierActionEditors = setOf("accessories", "inspection", "direct_acceptance", "generate", "maintenance_plan", "maintenance_done", "maintenance_adjust", "movement", "equipment_use")
private data class RevierPhrase(val id: String, val label: String, val text: String)
private object RevierMaintenanceCatalog {
    @Volatile private var cached: List<RevierPhrase>? = null
    suspend fun actions(controller: NativeAppController): List<RevierPhrase> = withContext(Dispatchers.IO) {
        synchronized(this@RevierMaintenanceCatalog) {
            cached ?: controller.context.assets.open("equipment-maintenance-texts.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val catalog = JSONObject(reader.readText())
                check(catalog.getString("format") == "hausgarten.equipment-maintenance-texts.v1")
                val actions = catalog.getJSONArray("actions")
                check(actions.length() in 1..100)
                actions.revierObjects().map { RevierPhrase(it.getString("id"), it.getString("label"), it.getString("text")) }
            }.also { cached = it }
        }
    }
}
private val revierMaintenanceReasons = listOf("Fehlschnitte festgestellt", "Erneutes Nachschärfen früher nötig", "Höhere Beanspruchung")
    .mapIndexed { index, text -> RevierPhrase(index.toString(), text, text) }

private val revierStates = linkedMapOf(
    "new" to "Neu", "inspection_planned" to "Besichtigung geplant", "offered" to "Angebot vorhanden",
    "accepted" to "Angenommen", "declined" to "Abgelehnt", "closed" to "Abgeschlossen",
    "planned" to "Geplant", "in_progress" to "In Arbeit", "completed" to "Erledigt",
    "partial" to "Teilweise erledigt", "cancelled" to "Abgesagt", "ready" to "Einsatzbereit",
    "maintenance" to "Wartung", "defective" to "Defekt", "receive" to "Zugang", "consume" to "Verbrauch",
    "adjust" to "Bestandskorrektur", "reversal" to "Gegenbuchung", "active" to "Aktiv", "paused" to "Pausiert",
    "days" to "Tage", "weeks" to "Wochen", "months" to "Monate", "years" to "Jahre",
    "first" to "Erste Besichtigung", "reschedule" to "Verschieben", "additional" to "Weitere Besichtigung",
    "reopen" to "Abgesagte Besichtigung wieder öffnen", "bring_forward" to "Nächsten Termin vorziehen",
    "shorten" to "Intervall dauerhaft verkürzen"
)
private fun revierLabel(key: String) = revierStates[key] ?: key
private fun revierDay() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
private fun revierRequest() = "ui_revier_" + UUID.randomUUID().toString().replace("-", "")
private fun JSONArray.revierObjects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.revierCopy() = JSONObject(toString())
private fun JSONObject.revierString(key: String): String = if (!has(key) || isNull(key)) "" else opt(key)?.toString().orEmpty()
private fun revierCollection(kind: String) = revierSections.first { it.kind == kind }.collection

/** All edits use the same native transactional writer as MCP. Only display pages use projections. */
@Composable
internal fun NativeRevierScreen(section: String, id: String, controller: NativeAppController) {
    key(section, id, controller.route.mode, controller.route.args.toString()) {
        RevierPage(section, id, controller)
    }
}

@Composable
private fun RevierPage(section: String, id: String, controller: NativeAppController) {
    val normalized = when (section.removePrefix("revier-")) {
        "property", "property-detail", "properties" -> "properties"
        "inquiry", "inquiry-detail", "inquiries" -> "inquiries"
        "care", "care_plan", "care-detail", "care_plans" -> "care_plans"
        "visit", "visit-detail", "visits" -> "visits"
        "material", "material-detail", "materials" -> "materials"
        "equipment-detail", "equipment" -> "equipment"
        "search", "revier_search" -> "revier_search"
        else -> section.removePrefix("revier-")
    }
    when (normalized) {
        "tour" -> { RevierTour(controller); return }
        "revier_search" -> { RevierSearch(controller); return }
        "archive", "revier_archive" -> { RevierArchive(controller); return }
        "office" -> {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Büro", style = MaterialTheme.typography.headlineSmall)
                for ((route, label) in listOf("inquiries" to "Anfragen", "offers" to "Angebote", "orders" to "Aufträge", "invoices" to "Rechnungen", "materials" to "Material", "equipment" to "Gerätewartung", "revier_search" to "Lokale Suche")) {
                    OutlinedButton({ controller.navigate(route) }, Modifier.fillMaxWidth()) { Text(label) }
                }
            }
            return
        }
    }
    val config = revierSections.firstOrNull { it.collection == normalized } ?: return
    val scope = rememberCoroutineScope()
    val repository = remember(controller.store) { NativeDataRepository(controller.store) }
    var selected by rememberSaveable(section, id) { mutableStateOf(id.takeUnless { it == "new" }.orEmpty()) }
    var editor by remember { mutableStateOf<RevierEdit?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    fun openEditor(kind: String, record: JSONObject? = null, context: JSONObject = JSONObject()) {
        scope.launch {
            try {
                val computedKey = "revier:$kind:${record?.optString("id").orEmpty()}:" + listOf("equipment_id", "plan_id", "property_id", "session_id", "visit_id", "reverses_movement_id").joinToString(":") { context.optString(it) }
                val editKey = context.optString("_draft_key").ifEmpty { computedKey }
                val saved = controller.draft(editKey)
                editor = if (saved != null) RevierEdit.restore(editKey, saved) else RevierEdit.create(editKey, kind, record, context)
                failure = ""
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
        }
    }
    LaunchedEffect(section, id, controller.route.mode) {
        val mode = controller.route.mode
        if (id == "new" || mode in listOf("new", "edit") || mode in revierActionEditors) {
            try {
                val retainedKey = controller.route.args.optString("_draft_key")
                if (retainedKey.isNotEmpty()) {
                    val saved = controller.draft(retainedKey) ?: error("Der ausgewählte Entwurf ist nicht mehr vorhanden.")
                    editor = RevierEdit.restore(retainedKey, saved)
                    return@LaunchedEffect
                }
                val original = if (id.isNotEmpty() && id != "new" && mode != "new") withContext(Dispatchers.IO) {
                    repository.detail(config.collection, id)?.let { RevierRules.view(it) } ?: error("Eintrag nicht gefunden.")
                } else null
                openEditor(if (mode in revierActionEditors) mode else config.kind, original, controller.route.args)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error.message.orEmpty() }
        }
    }
    val currentEdit = editor
    if (currentEdit != null) {
        RevierEditor(controller, currentEdit, repository, onClose = { editor = null }, onSaved = { result ->
            editor = null
            val record = result.optJSONObject("record")
            if (record != null && currentEdit.kind in revierSections.map { it.kind }) selected = record.optString("id")
            refresh++
        })
    } else if (selected.isNotEmpty()) {
        RevierDetail(config, selected, refresh, controller, repository, onBack = { selected = "" }, onEdit = ::openEditor, onRefresh = { refresh++ })
    } else {
        RevierList(config, controller, repository, onSelect = { selected = it }, onNew = { openEditor(config.kind) })
    }
    if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
}

@Composable
private fun RevierList(config: RevierSection, controller: NativeAppController, repository: NativeDataRepository, onSelect: (String) -> Unit, onNew: () -> Unit) {
    var query by rememberSaveable(config.kind) { mutableStateOf("") }
    var status by rememberSaveable(config.kind) { mutableStateOf("") }
    var archived by rememberSaveable(config.kind) { mutableStateOf(false) }
    var offset by rememberSaveable(config.kind) { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val source = remember(config.collection, query, status, archived, offset, retry) {
        repository.watchList(config.collection, search = query, status = status, offset = offset, limit = 50, includeInactive = archived).catch { failure = it.message.orEmpty() }
    }
    val page by source.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, offset, 50))
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(config.title, style = MaterialTheme.typography.headlineSmall)
                    NativeField("Suchen", query, { query = it; offset = 0 })
                    val states = when (config.kind) {
                        "equipment" -> listOf("ready", "maintenance", "defective")
                        "inquiry" -> listOf("new", "inspection_planned", "offered", "accepted", "declined", "closed")
                        "visit" -> listOf("planned", "in_progress", "completed", "partial", "cancelled")
                        else -> emptyList()
            }
            if (states.isNotEmpty()) RevierChoice("Status", status, listOf("" to "Alle") + states.map { it to revierLabel(it) }) { status = it; offset = 0 }
            Row(Modifier.fillMaxWidth()) {
                Checkbox(archived, { archived = it; offset = 0 })
                Text("Archivierte / inaktive einschließen", Modifier.padding(top = 12.dp))
            }
            if (failure.isNotEmpty()) {
                Text(failure, color = MaterialTheme.colorScheme.error)
                TextButton({ failure = ""; retry++ }) { Text("Erneut lesen") }
            }
            Text("${page.total} Treffer" + if (query.isNotBlank() || status.isNotBlank() || archived) " · Filter aktiv" else "")
            if (query.isNotBlank() || status.isNotBlank() || archived) TextButton({ query = ""; status = ""; archived = false; offset = 0 }) { Text("Filter zurücksetzen") }
        }
            }
            if (page.rows.isEmpty()) item { Text("Keine passenden Einträge. Suchtext und Filter prüfen.", Modifier.padding(12.dp)) }
            items(page.rows, key = { it.id }) { row ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { onSelect(row.id) }) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (row.thumbnailReference.isNotBlank()) RevierEquipmentThumbnail(row.thumbnailReference, controller)
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            if (row.subtitle.isNotBlank()) Text(row.subtitle)
                            if (row.address.isNotBlank()) Text(row.address)
                            if (row.status.isNotBlank()) Text(revierLabel(row.status))
                            if (!row.active) Text("Archiviert / inaktiv")
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ offset = (offset - 50).coerceAtLeast(0) }, enabled = offset > 0) { Text("Zurück") }
            OutlinedButton({ offset += 50 }, enabled = page.hasMore) { Text("Weitere") }
            Button(onNew, Modifier.weight(1f)) { Text("${config.singular} anlegen") }
        }
    }
}

private class RevierEdit(
    val key: String, val kind: String, val original: JSONObject, initial: JSONObject, request: String,
    pending: JSONObject? = null, restored: Boolean = false
) {
    var values by mutableStateOf(initial.toString())
    var request by mutableStateOf(request)
    var pending by mutableStateOf(pending?.toString())
    var restored by mutableStateOf(restored)
    var dirty by mutableStateOf(false)
    var mediaBusy by mutableStateOf(false)
    var observedHash by mutableStateOf(original.optString("record_hash"))
    fun row() = JSONObject(values)
    fun change(field: String, value: Any) { values = row().put(field, value).toString(); dirty = true }
    fun snapshot() = JSONObject().put("kind", kind).put("original", original).put("values", row()).put("request_id", request)
        .put("record_hash", observedHash).put("dirty", dirty).put("pending", pending?.let(::JSONObject) ?: JSONObject.NULL)
        .put("restore_review_required", restored)
    companion object {
        fun create(key: String, kind: String, row: JSONObject?, context: JSONObject): RevierEdit {
            val values = row?.revierCopy() ?: JSONObject()
            for (field in context.keys()) values.put(field, context.get(field))
            if (kind in listOf("movement", "equipment_use")) values.put("_resource_fixed", row != null)
            val plan = values.optJSONObject("plan")
            if (kind == "maintenance_plan" && plan != null) for (field in listOf("title", "interval_value", "interval_unit")) values.put(field, plan.get(field))
            if (kind == "maintenance_adjust" && plan != null) {
                values.put("operation", if (plan.optString("next_due_on") <= revierDay()) "shorten" else "bring_forward")
                values.put("interval_value", plan.optInt("interval_value")).put("interval_unit", plan.optString("interval_unit"))
            }
            if (kind == "inspection" && values.optString("inspection_appointment_id").isNotEmpty()) values.put("mode", "reschedule")
            if (kind == "direct_acceptance" && values.optString("billing_customer_id").isEmpty()) values.put("billing_customer_id", values.optString("customer_id"))
            if (kind == "material") for ((input, field) in listOf("purchase_price" to "purchase_price_minor", "billing_price" to "billing_price_minor")) {
                if (values.has(field) && !values.isNull(field)) values.put(input, BigDecimal(values.get(field).toString()).movePointLeft(2).toPlainString())
            }
            for ((field, value) in when (kind) {
                "care_plan" -> mapOf("anchor_date" to revierDay(), "interval_days" to 7, "planned_minutes" to 60, "effective_from" to revierDay())
                "visit" -> mapOf("date" to revierDay(), "planned_minutes" to 60, "status" to "planned")
                "inspection" -> mapOf("starts_local" to (revierDay() + "T09:00"), "duration_minutes" to 60, "mode" to "first")
                "generate" -> mapOf("from_date" to revierDay(), "to_date" to LocalDate.parse(revierDay()).plusDays(30).toString())
                "equipment" -> mapOf("condition" to "ready")
                "maintenance_plan" -> mapOf("starts_on" to revierDay(), "interval_unit" to "days")
                "maintenance_done" -> mapOf("performed_on" to revierDay())
                "maintenance_adjust" -> mapOf("operation" to "bring_forward", "next_due_on" to revierDay(), "interval_unit" to "days")
                "movement" -> mapOf("movement_kind" to "consume")
                else -> emptyMap()
            }) if (!values.has(field)) values.put(field, value)
            return RevierEdit(key, kind, row?.revierCopy() ?: JSONObject(), values, revierRequest())
        }
        fun restore(key: String, saved: JSONObject): RevierEdit = RevierEdit(
            key, saved.getString("kind"), saved.getJSONObject("original"), saved.getJSONObject("values"), saved.getString("request_id"),
            saved.optJSONObject("pending"), saved.optBoolean("restore_review_required") || saved.optBoolean("_restore_action_review_required")
        ).also { it.dirty = saved.optBoolean("dirty"); it.observedHash = saved.optString("record_hash", it.observedHash) }
    }
}

@Composable
private fun RevierDetail(
    config: RevierSection, id: String, refresh: Int, controller: NativeAppController, repository: NativeDataRepository,
    onBack: () -> Unit, onEdit: (String, JSONObject?, JSONObject) -> Unit, onRefresh: () -> Unit
) {
    var data by remember(id) { mutableStateOf<JSONObject?>(null) }
    var failure by remember(id) { mutableStateOf("") }
    var loadFailure by remember(id) { mutableStateOf("") }
    var equipmentTab by rememberSaveable(id) { mutableIntStateOf(0) }
    var archiveMode by remember { mutableStateOf("") }
    var archiveReason by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(config.kind, id, refresh) {
        loadFailure = ""
        try {
            data = when (config.kind) {
                "property" -> controller.query("get_property", JSONObject().put("property_id", id))
                "inquiry" -> controller.query("get_inquiry", JSONObject().put("inquiry_id", id))
                "visit" -> controller.query("get_visit", JSONObject().put("visit_id", id))
                else -> withContext(Dispatchers.IO) {
                    val record = repository.detail(config.collection, id) ?: error("Eintrag nicht gefunden.")
                    JSONObject().put("record", RevierRules.view(record))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { loadFailure = error.message.orEmpty() }
    }
    val row = data?.optJSONObject("record")
    if (row == null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onBack) { Text("Zurück") }
            if (loadFailure.isEmpty()) CircularProgressIndicator() else { Text(loadFailure, color = MaterialTheme.colorScheme.error); OutlinedButton(onRefresh) { Text("Erneut lesen") } }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onBack) { Text("Zur Liste") }
            Text(row.optString("name", row.optString("title", config.singular)), style = MaterialTheme.typography.headlineSmall)
            if (row.optBoolean("archived")) Text("Archivierter Eintrag")
            for ((key, label) in revierDetailLabels) {
                val text = row.revierString(key)
                if (text.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(if (key in listOf("condition", "status")) revierLabel(text) else text)
                }
            }
            if (row.optString("customer_id").isNotEmpty()) TextButton({ controller.navigate("customers", row.getString("customer_id"), "detail") }) { Text("Kundenakte und Fotos") }
            when (config.kind) {
                "property" -> {
                    OutlinedButton({ onEdit("care_plan", null, JSONObject().put("property_id", id)) }) { Text("Pflegeplan anlegen") }
                    OutlinedButton({ onEdit("visit", null, JSONObject().put("property_id", id)) }) { Text("Einsatz planen") }
                    OutlinedButton({ controller.navigate("offers", "", "new", JSONObject().put("customer_id", row.getString("customer_id")).put("property_id", id).put("title", row.optString("name"))) }) { Text("Angebot aus diesem Objekt") }
                    if (row.optString("address").isNotEmpty()) OutlinedButton({ controller.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(row.optString("address"))))) }) { Text("Navigation öffnen") }
                    for ((collection, kind, title) in listOf(Triple("visits", "visits", "Besuche"), Triple("care_plans", "care_plans", "Pflegepläne"), Triple("inquiries", "inquiries", "Anfragen"))) {
                        val related = data?.optJSONArray(collection)?.revierObjects().orEmpty()
                        if (related.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium)
                        for (relatedRow in related) OutlinedButton({ controller.navigate(kind, relatedRow.getString("id"), "detail") }, Modifier.fillMaxWidth()) { Text(relatedRow.optString("title", relatedRow.optString("name", title))) }
                    }
                }
                "inquiry" -> {
                    if (row.optString("order_id").isEmpty()) OutlinedButton({ onEdit("direct_acceptance", row, JSONObject()) }) { Text("Auftrag direkt annehmen") }
                    OutlinedButton({ onEdit("inspection", row, JSONObject()) }) { Text(if (row.optString("inspection_appointment_id").isEmpty()) "Besichtigung planen" else "Besichtigung ändern / ergänzen") }
                    OutlinedButton({ controller.navigate("offers", "", "new", JSONObject().put("customer_id", row.getString("customer_id")).put("property_id", row.optString("property_id")).put("inquiry_id", id).put("inquiry_record_hash", row.getString("record_hash")).put("title", row.optString("title"))) }) { Text("Angebot aus dieser Anfrage") }
                    if (row.optString("offer_id").isNotEmpty()) OutlinedButton({ controller.navigate("offers", row.getString("offer_id"), "detail") }) { Text("Zugeordnetes Angebot") }
                    if (row.optString("order_id").isNotEmpty()) OutlinedButton({ controller.navigate("orders", row.getString("order_id"), "detail") }) { Text("Zugeordneten Auftrag öffnen") }
                    for (measurement in row.optJSONArray("measurements")?.revierObjects().orEmpty()) Text(listOf(measurement.optString("name"), measurement.optString("value_decimal"), measurement.optString("unit")).filter { it.isNotEmpty() }.joinToString(" "))
                }
                "care_plan" -> OutlinedButton({ onEdit("generate", row, JSONObject()) }) { Text("Fällige Besuche erzeugen") }
                "visit" -> {
                    OutlinedButton({
                        scope.launch {
                            try {
                                val key = "revier:start:$id"
                                val saved = controller.draft(key)
                                val args = saved?.optJSONObject("args") ?: JSONObject().put("request_id", revierRequest()).put("customer_id", row.getString("customer_id"))
                                    .put("property_id", row.getString("property_id")).put("visit_id", id).put("work_date", revierDay()).put("activity", row.optString("title")).apply {
                                        if (row.optString("order_id").isNotEmpty()) put("order_id", row.getString("order_id"))
                                    }
                                val proof = if (saved != null) controller.query("inspect_edit_action", JSONObject().put("original_tool", "start_work").put("original_args", args)) else null
                                if (proof?.optString("state") == "content_removed") error("Dieser Arbeitsstart wurde endgültig entfernt. Keine erneute Anlage.")
                                if (proof?.optString("state") != "confirmed") {
                                    if (saved?.optBoolean("_restore_action_review_required") == true) error("Dieser Arbeitsstart stammt aus einem anderen Gerätestand. Laufende Sitzung zuerst prüfen.")
                                    val active = controller.query("get_active_work_session")
                                    require(active.optString("state") == "IDLE") { "Eine Zeiterfassung ist bereits offen. Diese zuerst auf der Heute-Seite prüfen." }
                                    controller.saveDraft(key, JSONObject().put("args", args)); controller.flushDrafts()
                                    controller.query("start_work", args)
                                }
                                runCatching { controller.removeDraft(key) }
                                controller.navigate("today"); controller.message("Arbeitsstart lokal bestätigt. Aktuelle Sitzung prüfen.")
                            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
                        }
                    }, enabled = row.optString("status") !in listOf("cancelled", "completed", "superseded")) { Text("Arbeit beginnen") }
                    OutlinedButton({ controller.navigate("tour", args = JSONObject().put("date", row.optString("date"))) }) { Text("In Tagestour aufnehmen") }
                    if (row.optString("status") == "partial") OutlinedButton({ onEdit("visit", null, JSONObject().put("property_id", row.getString("property_id")).put("parent_visit_id", id).put("title", row.optString("title")).put("planned_minutes", row.optInt("remaining_minutes", 60))) }) { Text("Offenen Rest planen") }
                    val warnings = data?.optJSONArray("equipment_warnings")?.revierObjects().orEmpty()
                    if (warnings.isNotEmpty()) Text("Geräte vor dem Einsatz prüfen: " + warnings.joinToString { it.optString("name", "Gerät") })
                }
                "material" -> {
                    OutlinedButton({ onEdit("movement", row, JSONObject().put("material_id", id)) }) { Text("Zugang / Verbrauch buchen") }
                    RevierHistory(controller, repository, "material_movements", "material_id", id) { movement ->
                        if (movement.optString("movement_kind") != "reversal") TextButton({ onEdit("movement", row, JSONObject().put("material_id", id).put("reverses_movement_id", movement.getString("id")).put("movement_kind", "reversal")) }) { Text("Gegenbuchung") }
                    }
                }
                "equipment" -> {
                    TabRow(equipmentTab) {
                        Tab(equipmentTab == 0, { equipmentTab = 0 }, text = { Text("Gerät & Wartung") })
                        Tab(equipmentTab == 1, { equipmentTab = 1 }, text = { Text("Zubehör") })
                    }
                    if (equipmentTab == 0) {
                        RevierProfilePhoto(row.optJSONObject("profile_photo"), controller)
                        if (row.optString("condition") == "defective") Text("Dieses Gerät ist als defekt markiert. Vor dem Einsatz prüfen.", color = MaterialTheme.colorScheme.error)
                        OutlinedButton({ onEdit("equipment_use", row, JSONObject().put("equipment_id", id)) }) { Text("Nutzung zuordnen") }
                        RevierMaintenance(controller, row, refresh, onEdit)
                        RevierHistory(controller, repository, "equipment_usage", "equipment_id", id)
                    } else {
                        Text("Zubehör für " + row.getString("name"), style = MaterialTheme.typography.titleMedium)
                        val accessories = row.optJSONArray("accessories")?.revierObjects().orEmpty()
                        if (accessories.isEmpty()) Text("Noch kein Zubehör zugeordnet.")
                        for (accessory in accessories) OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(listOf(accessory.optString("name"), accessory.optString("manufacturer"), accessory.optString("model")).filter { it.isNotBlank() }.joinToString(" · "))
                                if (accessory.optString("id") == row.optString("current_chain_id")) Text("Aktuell verwendete Kette", color = MaterialTheme.colorScheme.primary)
                                for ((key, label) in accessoryFields) if (accessory.optString(key).isNotBlank()) Text("$label: " + accessory.getString(key))
                                RevierProfilePhoto(accessory.optJSONObject("photo"), controller)
                            }
                        }
                        OutlinedButton({ onEdit("accessories", row, JSONObject()) }, enabled = !row.optBoolean("archived")) { Text("Zubehör bearbeiten") }
                    }
                }
            }
            if (row.optString("property_id").isNotEmpty()) TextButton({ controller.navigate("properties", row.getString("property_id"), "detail") }) { Text("Objekt öffnen") }
            for (event in data?.optJSONArray("history")?.revierObjects().orEmpty()) Text(listOf(event.optString("at"), event.optString("reason", "Änderung gespeichert")).filter { it.isNotBlank() }.joinToString(" · "))
            if (loadFailure.isNotEmpty()) { Text(loadFailure, color = MaterialTheme.colorScheme.error); TextButton(onRefresh) { Text("Erneut lesen") } }
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!row.optBoolean("archived")) Button({ onEdit(config.kind, row, JSONObject()) }, Modifier.weight(1f)) { Text("Bearbeiten") }
            OutlinedButton({ archiveMode = if (row.optBoolean("archived")) "restore" else "archive"; confirmed = false }, Modifier.weight(1f)) { Text(if (row.optBoolean("archived")) "Wiederherstellen" else "Archivieren") }
            if (row.optBoolean("archived")) TextButton({ archiveMode = "purge"; confirmed = false }) { Text("Endgültig löschen") }
        }
    }
    if (archiveMode.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!busy) archiveMode = "" },
        title = { Text(when (archiveMode) { "purge" -> "Endgültig löschen?"; "restore" -> "Eintrag wiederherstellen?"; else -> "Eintrag archivieren?" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (archiveMode == "purge") "Der Eintrag wird unwiederbringlich entfernt. Geschützte Bezüge werden geprüft." else "Bezüge und Verlauf bleiben erhalten.")
                NativeField("Grund", archiveReason, { archiveReason = it }, multiline = true)
                Row { Checkbox(confirmed, { confirmed = it }); Text("Diese Aktion ausdrücklich bestätigen", Modifier.padding(top = 12.dp)) }
            }
        },
        dismissButton = { TextButton({ archiveMode = "" }, enabled = !busy) { Text("Zurück") } },
        confirmButton = { Button({
            busy = true
            scope.launch {
                try {
                    val tool = if (archiveMode == "purge") "purge_revier_entity" else "set_revier_archived"
                    val key = "revier:$tool:$id:$archiveMode"
                    val old = controller.draft(key)
                    val args = old?.optJSONObject("args") ?: JSONObject().put("request_id", revierRequest()).put("entity", config.kind).put("entity_id", id)
                        .put("expected_record_hash", row.getString("record_hash")).put("confirmed", true).put("reason", archiveReason).apply { if (tool == "set_revier_archived") put("archived", archiveMode == "archive") }
                    try {
                        val proof = if (old != null) controller.query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", args)) else null
                        if (proof?.optString("state") == "content_removed") error("Dieser Vorgang wurde endgültig entfernt. Keine erneute Anlage.")
                        if (proof?.optString("state") != "confirmed") {
                            if (old?.optBoolean("_restore_action_review_required") == true) error("Alte Aktion zuerst anhand des aktuellen Eintrags prüfen. Keine Aktion wiederholt.")
                            controller.saveDraft(key, JSONObject().put("args", args)); controller.flushDrafts()
                            controller.query(tool, args)
                        }
                        runCatching { controller.removeDraft(key) }
                    } catch (error: NativeCallFailure) {
                        if (error.detail.optString("commit_state") == "not_committed") {
                            controller.removeDraft(key)
                            onRefresh()
                        }
                        throw error
                    }
                    archiveMode = ""; if (tool == "purge_revier_entity") onBack() else onRefresh()
                } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty(); archiveMode = "" }
                finally { busy = false }
            }
        }, enabled = confirmed && !busy) { Text("Bestätigen") } }
    )
}

private val revierDetailLabels = linkedMapOf(
    "address" to "Einsatzadresse", "district" to "Gebiet", "access_notes" to "Zugangshinweise", "area_decimal" to "Fläche", "area_unit" to "Flächeneinheit",
    "concern" to "Anliegen", "received_at" to "Eingang", "wish_from" to "Wunsch ab", "wish_until" to "Wunsch bis", "status" to "Status",
    "inspection_notes" to "Besichtigungsnotizen", "date" to "Datum", "window_start" to "Zeitfenster ab", "window_end" to "Zeitfenster bis",
    "planned_minutes" to "Geplante Minuten", "remaining_minutes" to "Offener Rest in Minuten", "anchor_date" to "Pflegebeginn", "interval_days" to "Turnus in Tagen",
    "season_start" to "Saisonbeginn", "season_end" to "Saisonende", "unit" to "Einheit", "stock_decimal" to "Bestand", "condition" to "Gerätezustand",
    "maintenance_due" to "Zusätzlich vorgemerkte Wartung", "notes" to "Notizen", "status_reason" to "Begründung"
)

/** Projected equipment rows resolve only their bounded thumbnail; they never load an original or query photo metadata. */
@Composable
private fun RevierEquipmentThumbnail(reference: String, controller: NativeAppController) {
    val source by produceState<File?>(null, reference, controller) {
        value = null
        value = try { withContext(Dispatchers.IO) { controller.store.photoAttachmentStore().verifiedThumbnailFile(reference) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    if (source != null) AsyncImage(source, "Gerätefoto", imageLoader = controller.imageLoader, modifier = Modifier.size(56.dp))
    else Text("Vorschau fehlt", Modifier.size(56.dp), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun RevierProfilePhoto(photo: JSONObject?, controller: NativeAppController) {
    if (photo == null) return
    var decoded by remember(photo.toString()) { mutableStateOf(false) }
    var decodeFailed by remember(photo.toString()) { mutableStateOf(false) }
    val model by produceState<File?>(null, photo.toString()) {
        value = withContext(Dispatchers.IO) { runCatching { controller.store.photoAttachmentStore().verifiedFile(photo) }.getOrNull() }
    }
    if (model != null) {
        AsyncImage(model, if (decoded) "Gerätefoto" else "Gerätefoto wird geladen", imageLoader = controller.imageLoader,
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), onSuccess = { decoded = true; decodeFailed = false }, onError = { decodeFailed = true })
        if (decodeFailed) Text("Foto nicht lesbar. Bitte die gespeicherte Aufnahme prüfen.", color = MaterialTheme.colorScheme.error)
    }
    else Text("Gerätefoto derzeit nicht lesbar. Gespeicherter Eintrag bleibt erhalten.")
}

@Composable
private fun RevierHistory(controller: NativeAppController, repository: NativeDataRepository, collection: String, relation: String, id: String, extra: @Composable (JSONObject) -> Unit = {}) {
    var offset by remember(id) { mutableIntStateOf(0) }
    var failure by remember(collection, relation, id) { mutableStateOf("") }
    var retry by remember(collection, relation, id) { mutableIntStateOf(0) }
    val source = remember(collection, relation, id, offset, retry) {
        repository.watchHistory(collection, relation, id, offset, 20).catch { failure = it.message.orEmpty() }
    }
    val records by source.collectAsStateWithLifecycle(emptyList())
    if (failure.isNotEmpty()) {
        Text(failure, color = MaterialTheme.colorScheme.error)
        TextButton({ failure = ""; retry++ }) { Text("Verlauf erneut lesen") }
    }
    val hasMore = records.size == 20
    if (records.isNotEmpty()) Text(if (collection == "material_movements") "Bestandsbewegungen" else "Nutzungen", style = MaterialTheme.typography.titleMedium)
    for (record in records) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(listOf(revierLabel(record.optString("movement_kind")), record.optString("quantity_decimal"), record.optString("created_at"), record.optString("reason", record.optString("notes"))).filter { it.isNotBlank() }.joinToString(" · "))
                extra(record)
            }
        }
    }
    if (offset > 0 || hasMore) Row {
        TextButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0) { Text("Vorherige") }
        TextButton({ offset += 20 }, enabled = hasMore) { Text("Weitere") }
    }
}

@Composable
private fun RevierMaintenance(controller: NativeAppController, row: JSONObject, refresh: Int, onEdit: (String, JSONObject?, JSONObject) -> Unit) {
    var result by remember(row.optString("id"), refresh) { mutableStateOf<JSONObject?>(null) }
    var offset by remember(row.optString("id")) { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    LaunchedEffect(row.toString(), offset, refresh) {
        try { result = controller.query("get_equipment_maintenance", JSONObject().put("equipment_id", row.getString("id")).put("offset", offset).put("limit", 20)) }
        catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
    }
    Text("Gerätewartung", style = MaterialTheme.typography.titleLarge)
    val active = !row.optBoolean("archived") && row.optBoolean("active", true)
    if (active) OutlinedButton({ onEdit("maintenance_plan", row, JSONObject().put("equipment_id", row.getString("id"))) }) { Text("Wartung einrichten") }
    for (plan in result?.optJSONArray("plans")?.revierObjects().orEmpty()) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.optString("title"), style = MaterialTheme.typography.titleMedium)
                Text("Alle ${plan.optInt("interval_value")} ${revierLabel(plan.optString("interval_unit"))}")
                val due = plan.optString("next_due_on")
                Text((if (due < revierDay()) "Überfällig seit " else if (due == revierDay()) "Heute fällig · " else "Nächste Wartung: ") + due)
                if (plan.optString("last_completed_on").isNotBlank()) Text("Zuletzt erledigt: " + plan.optString("last_completed_on"))
                if (plan.optString("last_change_reason").isNotBlank()) Text(plan.optString("last_change_reason"))
                if (active) {
                    val context = JSONObject().put("equipment_id", row.getString("id")).put("plan_id", plan.getString("id")).put("plan", plan)
                    OutlinedButton({ onEdit("maintenance_plan", row, context) }) { Text("Intervall bearbeiten") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ onEdit("maintenance_done", row, context) }) { Text("Erledigt erfassen") }
                        OutlinedButton({ onEdit("maintenance_adjust", row, context) }) { Text("Früher warten") }
                    }
                }
            }
        }
    }
    Text("Erledigte Wartungen", style = MaterialTheme.typography.titleMedium)
    for (event in result?.optJSONArray("items")?.revierObjects().orEmpty()) {
        Text(listOf(event.optString("title"), event.optString("performed_on"), event.optString("reason"), event.optString("notes")).filter { it.isNotBlank() }.joinToString(" · "))
    }
    Row {
        TextButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0) { Text("Vorherige") }
        TextButton({ offset += 20 }, enabled = offset + (result?.optJSONArray("items")?.length() ?: 0) < (result?.optInt("total") ?: 0)) { Text("Weitere Wartungen") }
    }
    if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevierEditor(controller: NativeAppController, edit: RevierEdit, repository: NativeDataRepository, onClose: () -> Unit, onSaved: (JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember(edit.key) { mutableStateOf(false) }
    var failure by remember(edit.key) { mutableStateOf("") }
    var conflict by remember(edit.key) { mutableStateOf(false) }
    var comparison by remember(edit.key) { mutableStateOf<JSONObject?>(null) }
    var reviewed by remember(edit.key) { mutableStateOf(false) }
    val values = edit.row()
    val locked = busy || edit.mediaBusy || edit.pending != null || edit.restored
    fun update(field: String, value: Any) {
        if (locked) return
        edit.change(field, value)
        controller.saveDraft(edit.key, edit.snapshot())
    }
    LaunchedEffect(edit.key) {
        if (edit.kind == "inspection" && values.optString("inspection_appointment_id").isNotEmpty()) {
            try {
                val appointment = repository.detail("appointments", values.getString("inspection_appointment_id"))
                if (appointment != null && edit.pending == null) {
                    edit.values = edit.row().put("appointment_hash", withContext(Dispatchers.IO) { AppointmentRules.hash(appointment) }).toString()
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
        }
    }
    LaunchedEffect(edit.key, values.optString("material_id"), values.optString("equipment_id")) {
        if (edit.kind in listOf("movement", "equipment_use") && edit.pending == null && !edit.restored) {
            val field = if (edit.kind == "movement") "material_id" else "equipment_id"
            val sourceId = values.optString(field, edit.original.optString("id"))
            if (sourceId.isNotEmpty()) try {
                val source = repository.detail(if (edit.kind == "movement") "materials" else "equipment", sourceId)
                if (source != null) {
                    edit.observedHash = withContext(Dispatchers.IO) { RevierRules.hash(source) }
                    for (key in source.keys()) edit.original.put(key, source.get(key))
                    edit.values = edit.row().put(if (edit.kind == "movement") "material_name" else "equipment_name", source.optString("name")).put("stock_decimal", source.optString("stock_decimal")).toString()
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(revierEditorTitle(edit.kind), style = MaterialTheme.typography.headlineSmall)
            Text(when { edit.pending != null -> "Bestätigung ausstehend. Angaben und Vorgangskennung bleiben für die Prüfung erhalten."; edit.restored -> "Entwurf aus einem anderen Gerätestand. Aktuelle Originale vor einer neuen Aktion prüfen."; edit.dirty -> "Entwurf · noch nicht gespeichert"; else -> "Angaben ergänzen und ausdrücklich speichern." })
            RevierFields(edit, values, controller, !locked, ::update)
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
            if ((conflict || edit.restored) && edit.pending == null) OutlinedButton({
                scope.launch {
                    try {
                        val collection = when {
                            edit.kind.startsWith("maintenance_") || edit.kind in listOf("equipment_use", "accessories") -> "equipment"
                            edit.kind in listOf("inspection", "direct_acceptance") -> "inquiries"
                            edit.kind == "generate" -> "care_plans"
                            edit.kind == "movement" -> "materials"
                            else -> revierCollection(edit.kind)
                        }
                        comparison = if (edit.original.optString("id").isEmpty()) JSONObject().put("_new_draft", true)
                        else withContext(Dispatchers.IO) { repository.detail(collection, edit.original.getString("id"))?.let { RevierRules.view(it) } }
                        reviewed = false
                        if (comparison == null) failure = "Der ursprüngliche Eintrag ist nicht mehr vorhanden. Aktuelle Liste prüfen."
                    } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
                }
            }, enabled = !busy) { Text("Änderungen vergleichen") }
        }
        FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton({
                busy = true
                scope.launch {
                    try {
                        if (edit.dirty || edit.pending != null) { controller.saveDraft(edit.key, edit.snapshot()); controller.flushDrafts() }
                        onClose()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failure = error.message.orEmpty() }
                    finally { busy = false }
                }
            }, enabled = !busy && !edit.mediaBusy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("Später", maxLines = 1) }
            TextButton({
                busy = true
                scope.launch {
                    try { revierDeleteEditPhotos(controller, edit.row()); controller.removeDraft(edit.key); onClose() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failure = error.message.orEmpty() }
                    finally { busy = false }
                }
            }, enabled = !busy && !edit.mediaBusy && edit.pending == null) { Text("Verwerfen") }
            Button({
                scope.launch {
                    busy = true; failure = ""
                    try {
                        val frozen = edit.pending?.let(::JSONObject)
                        var proof: JSONObject? = null
                        if (frozen != null) {
                            val inspection = controller.query("inspect_edit_action", JSONObject().put("original_tool", frozen.getString("tool")).put("original_args", frozen.getJSONObject("args")))
                            when (inspection.optString("state")) {
                                "confirmed" -> proof = inspection.optJSONObject("result") ?: JSONObject()
                                "content_removed" -> error("Dieser Vorgang wurde endgültig gelöscht. Keine erneute Anlage.")
                                else -> if (edit.restored) error("Für diese alte Speicherung ist kein eindeutiger Beleg vorhanden. Aktuellen Bestand prüfen; keine Aktion wiederholt.")
                            }
                        }
                        if (edit.restored && proof == null) error("Aktuellen Eintrag und Entwurf zuerst vergleichen. Es wurde nichts gespeichert.")
                        val result = if (proof != null) proof else {
                            var argsValues = edit.row()
                            if (edit.kind == "equipment" && argsValues.optString("selected_photo_uri").isNotEmpty() && frozen == null) {
                                val upload = controller.stageEquipmentPhoto(Uri.parse(argsValues.getString("selected_photo_uri")), edit.request + "_photo_" + PixelMcpWriteTools.hash(argsValues.getString("selected_photo_uri")).take(16))
                                edit.values = argsValues.put("profile_photo_upload_id", upload).toString()
                                controller.saveDraft(edit.key, edit.snapshot()); controller.flushDrafts()
                                argsValues = edit.row()
                            }
                            if (edit.kind == "accessories" && frozen == null) {
                                val accessories = argsValues.optJSONArray("accessories") ?: JSONArray()
                                for (index in 0 until accessories.length()) {
                                    val item = accessories.getJSONObject(index)
                                    if (item.optString("selected_photo_uri").isNotEmpty()) item.put("photo_upload_id", controller.stageEquipmentPhoto(Uri.parse(item.getString("selected_photo_uri")), edit.request + "_" + PixelMcpWriteTools.hash(item.getString("id") + item.getString("selected_photo_uri")).take(24)))
                                }
                                edit.values = argsValues.put("accessories", accessories).toString()
                                controller.saveDraft(edit.key, edit.snapshot()); controller.flushDrafts()
                            }
                            val operation = frozen ?: revierArguments(edit, argsValues)
                            edit.pending = operation.toString(); edit.dirty = true
                            controller.saveDraft(edit.key, edit.snapshot()); controller.flushDrafts()
                            controller.query(operation.getString("tool"), operation.getJSONObject("args"))
                        }
                        // A confirmed business save remains confirmed if draft cleanup or rendering fails.
                        edit.pending = null
                        edit.dirty = false
                        val cleanup = runCatching { revierDeleteEditPhotos(controller, edit.row()); controller.removeDraft(edit.key) }
                        controller.message(if (cleanup.isSuccess) "Lokal gespeichert." else "Gespeichert. Entwurfsbereinigung steht noch aus.")
                        onSaved(result)
                    } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) {
                        if (error is NativeCallFailure && error.detail.optString("commit_state") == "not_committed") {
                            edit.pending = null
                            conflict = error.detail.optString("code").contains("CONFLICT") || error.detail.optString("code").contains("STALE")
                        }
                        failure = error.message.orEmpty()
                        if (edit.dirty || edit.pending != null) controller.saveDraft(edit.key, edit.snapshot())
                    } finally { busy = false }
                }
            }, enabled = !busy && !edit.mediaBusy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(if (edit.pending != null) "Ergebnis prüfen" else "Speichern", maxLines = 1) }
        }
    }
    val fresh = comparison
    if (fresh != null) AlertDialog(
        onDismissRequest = { comparison = null },
        title = { Text("Geänderten Stand prüfen") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val newDraft = fresh.optBoolean("_new_draft")
                Text(if (newDraft) "Dieser Entwurf hat noch keinen bestätigten Geschäftssatz. Angaben und aktuelle Zuordnungen prüfen; sie werden beim Speichern erneut geprüft."
                    else "Aktuell gespeicherte Angaben und eigener Entwurf. Änderungen werden erst nach erneuter ausdrücklicher Speicherung übernommen.")
                for ((key, label) in revierDetailLabels + revierCompareLabels) {
                    val own = values.revierString(key)
                    val current = fresh.revierString(key)
                    if ((newDraft && own.isNotEmpty()) || (own != current && (own.isNotEmpty() || current.isNotEmpty()))) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        if (!newDraft) {
                            Text("Gespeichert:", style = MaterialTheme.typography.labelMedium)
                            RevierComparisonValue(key, fresh.opt(key), repository)
                        }
                        Text("Entwurf:", style = MaterialTheme.typography.labelMedium)
                        RevierComparisonValue(key, values.opt(key), repository)
                    }
                }
                val currentPlan = fresh.optJSONArray("maintenance_plans")?.revierObjects()?.firstOrNull { it.optString("id") == values.optString("plan_id") }
                if (currentPlan != null) Text("Aktuelles Intervall: ${currentPlan.optInt("interval_value")} ${revierLabel(currentPlan.optString("interval_unit"))} · Fällig: ${currentPlan.optString("next_due_on")}")
                Row { Checkbox(reviewed, { reviewed = it }); Text("Aktuellen Stand und eigene Angaben geprüft", Modifier.padding(top = 12.dp)) }
            }
        },
        dismissButton = { TextButton({ comparison = null }) { Text("Zurück zum Entwurf") } },
        confirmButton = { Button({
            busy = true
            scope.launch {
                try {
                    check(edit.pending == null) { "Ergebnis der ursprünglichen Speicherung zuerst prüfen." }
                    val nextValues = edit.row()
                    val plan = fresh.optJSONArray("maintenance_plans")?.revierObjects()?.firstOrNull { it.optString("id") == nextValues.optString("plan_id") }
                    if (plan != null) nextValues.put("plan", plan)
                    if (edit.kind == "inspection" && fresh.optString("inspection_appointment_id").isNotEmpty()) {
                        val appointment = repository.detail("appointments", fresh.getString("inspection_appointment_id")) ?: error("Aktueller Besichtigungstermin nicht lesbar.")
                        nextValues.put("inspection_appointment_id", fresh.getString("inspection_appointment_id"))
                            .put("appointment_hash", withContext(Dispatchers.IO) { AppointmentRules.hash(appointment) })
                    }
                    val nextOriginal = (if (fresh.optBoolean("_new_draft")) edit.original else fresh).revierCopy()
                    val next = edit.snapshot().put("record_hash", fresh.optString("record_hash")).put("original", nextOriginal).put("values", nextValues)
                        .put("request_id", revierRequest()).put("dirty", true).put("restore_review_required", false).put("pending", JSONObject.NULL)
                    controller.adoptDraft(edit.key, next)
                    for (key in edit.original.keys().asSequence().toList()) edit.original.remove(key)
                    for (key in nextOriginal.keys()) edit.original.put(key, nextOriginal.get(key))
                    edit.values = nextValues.toString(); edit.observedHash = next.optString("record_hash")
                    edit.pending = null; edit.restored = false; edit.request = next.getString("request_id"); edit.dirty = true
                    comparison = null; conflict = false; failure = "Vergleich bestätigt. Angaben prüfen und erneut ausdrücklich speichern."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { failure = error.message.orEmpty() }
                finally { busy = false }
            }
        }, enabled = reviewed && !busy && edit.pending == null) { Text("Weiterbearbeiten") } }
    )
}

private fun revierEditorTitle(kind: String): String = when (kind) {
    "property" -> "Objekt"; "inquiry" -> "Anfrage"; "care_plan" -> "Pflegeplan"; "visit" -> "Einsatz"
    "accessories" -> "Zubehör der Maschine"; "material" -> "Material"; "equipment" -> "Gerät"; "inspection" -> "Besichtigung planen"
    "direct_acceptance" -> "Direkte Auftragsannahme"; "generate" -> "Besuche erzeugen"; "movement" -> "Materialbewegung"
    "equipment_use" -> "Gerätenutzung"; "maintenance_plan" -> "Wartungsintervall"; "maintenance_done" -> "Wartung erledigt"
    "maintenance_adjust" -> "Früher warten"; else -> "Eintrag"
}

@Composable
private fun RevierFields(edit: RevierEdit, value: JSONObject, controller: NativeAppController, enabled: Boolean, update: (String, Any) -> Unit) {
    val editing = edit.original.optString("id").isNotEmpty()
    val scope = rememberCoroutineScope()
    fun text(key: String, fallback: String = "") = value.revierString(key).ifEmpty { fallback }
    fun pickEquipmentPhoto(camera: Boolean) {
        if (edit.mediaBusy || edit.pending != null || edit.restored) return
        edit.mediaBusy = true
        scope.launch {
            try {
                controller.pickPhoto(camera)?.let { source ->
                    val retained = nativeRetainPhotoDraft(controller, source)
                    val previous = text("selected_photo_uri")
                    update("selected_photo_uri", retained.toString())
                    controller.flushDrafts()
                    nativeDeletePhotoDraft(controller, previous)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { controller.message(error.message.orEmpty()) }
            finally { edit.mediaBusy = false }
        }
    }
    @Composable fun field(key: String, label: String, required: Boolean = false, multiline: Boolean = false) {
        if (enabled) NativeField(label, text(key), { update(key, it) }, required, multiline)
        else if (text(key).isNotEmpty()) { Text(label, style = MaterialTheme.typography.labelLarge); Text(text(key)) }
    }
    @Composable fun relation(key: String, collection: String, label: String) {
        NativeRecordPicker(controller, collection, label, text(key)) { if (enabled) update(key, it) }
    }
    @Composable fun choice(key: String, label: String, options: List<String>, fallback: String = "") {
        RevierChoice(label, text(key, fallback), options.map { it to revierLabel(it) }, enabled) { update(key, it) }
    }
    when (edit.kind) {
        "property" -> {
            if (!editing) relation("customer_id", "customers", "Kunde")
            field("name", "Objektbezeichnung", true); field("address", "Einsatzadresse", multiline = true); field("district", "Gebiet")
            field("area_decimal", "Fläche"); field("area_unit", "Einheit der Fläche"); field("access_notes", "Zugangshinweise", multiline = true); field("notes", "Notizen", multiline = true)
        }
        "inquiry" -> {
            if (!editing) relation("customer_id", "customers", "Kunde")
            relation("property_id", "properties", "Objekt, falls bekannt")
            field("title", "Bezeichnung", true); field("concern", "Anliegen", multiline = true)
            field("wish_from", "Wunsch ab · JJJJ-MM-TT"); field("wish_until", "Wunsch bis · JJJJ-MM-TT")
            if (editing) {
                choice("status", "Status", listOf("new", "inspection_planned", "offered", "accepted", "declined", "closed"), "new")
                relation("offer_id", "offers", "Vorhandenes Angebot"); relation("order_id", "orders", "Vorhandener Auftrag")
            }
            if (text("inspection_appointment_id").isNotEmpty()) {
                field("inspection_notes", "Besichtigungsnotizen", multiline = true)
                RevierMeasurements(value.optJSONArray("measurements") ?: JSONArray(), enabled) { update("measurements", it) }
                RevierRelations(controller, "photos", "Dokumentationsfotos", "photo_ids", value, enabled, update)
            }
        }
        "care_plan" -> {
            if (!editing) relation("property_id", "properties", "Objekt")
            field("title", "Pflegeleistung", true); field("anchor_date", "Beginn · JJJJ-MM-TT", true)
            field("interval_days", "Turnus in Tagen", true); field("planned_minutes", "Geplante Dauer in Minuten", true)
            field("season_start", "Saisonbeginn · MM-TT"); field("season_end", "Saisonende · MM-TT"); field("notes", "Hinweise", multiline = true)
            RevierMaterialNeeds(controller, value.optJSONArray("material_needs") ?: JSONArray(), enabled) { update("material_needs", it) }
            RevierRelations(controller, "equipment", "Benötigte Geräte", "equipment_ids", value, enabled, update)
            if (editing) { Text("Vergangene Besuche bleiben erhalten. Die Änderung gilt für zukünftige Termine."); field("effective_from", "Änderung gilt ab · JJJJ-MM-TT", true) }
        }
        "visit" -> {
            if (!editing) { relation("property_id", "properties", "Objekt"); relation("order_id", "orders", "Auftrag (optional)") }
            field("title", "Tätigkeit / Einsatz", true); field("date", "Datum · JJJJ-MM-TT", true); field("planned_minutes", "Geplante Minuten", true)
            field("window_start", "Zeitfenster ab · HH:mm"); field("window_end", "Zeitfenster bis · HH:mm"); field("notes", "Hinweise", multiline = true)
            RevierRelations(controller, "equipment", "Benötigte Geräte", "equipment_ids", value, enabled, update)
            if (editing) {
                choice("status", "Ergebnis / Status", listOf("planned", "in_progress", "completed", "partial", "cancelled"), "planned")
                field("reason", "Grund für Änderung / Absage", true, true); field("remaining_minutes", "Offener Rest in Minuten")
                if (text("care_plan_id").isNotEmpty()) Text("Nur dieser Termin wird geändert. Die Pflegeplanregel bleibt erhalten.")
            }
        }
        "direct_acceptance" -> {
            Text("Die bestätigte direkte oder mündliche Annahme erzeugt einen Auftrag.")
            field("accepted_local", "Tatsächlicher Annahmezeitpunkt · JJJJ-MM-TTTHH:mm", true)
            field("evidence", "Annahmenachweis", true, true); relation("billing_customer_id", "customers", "Rechnungsempfänger")
            Row { Checkbox(value.optBoolean("confirmed"), { if (enabled) update("confirmed", it) }, enabled = enabled); Text("Direkte Auftragsannahme ausdrücklich bestätigen", Modifier.padding(top = 12.dp)) }
        }
        "inspection" -> {
            if (text("inspection_appointment_id").isNotEmpty()) choice("mode", "Gewünschter Vorgang", listOf("reschedule", "additional", "reopen"), "reschedule")
            field("starts_local", "Beginn · JJJJ-MM-TTTHH:mm", true); field("duration_minutes", "Dauer in Minuten", true)
            field("notes", "Besichtigungsnotizen / Messwerte", multiline = true)
            Text("Bekannte Konflikte werden geprüft. Ein unbekannter externer Kalenderstand bedeutet nicht frei.")
        }
        "generate" -> {
            Text("Fehlende Vorkommen werden erzeugt. Bereits verschobene Termine bleiben erhalten.")
            field("from_date", "Ab · JJJJ-MM-TT", true); field("to_date", "Bis · höchstens 180 Tage", true)
        }
        "material" -> {
            field("name", "Materialbezeichnung", true); field("unit", "Einheit", true)
            field("purchase_price", "Einstandspreis in EUR (optional)"); field("billing_price", "Abrechnungspreis in EUR (optional)")
            field("notes", "Notizen", multiline = true)
        }
        "accessories" -> RevierAccessoryFields(edit, value, controller, enabled, update)
        "equipment" -> {
            RevierProfilePhoto(value.optJSONObject("profile_photo"), controller)
            if (text("selected_photo_uri").isNotEmpty()) AsyncImage(Uri.parse(text("selected_photo_uri")), "Ausgewähltes Gerätefoto · noch nicht gespeichert", imageLoader = controller.imageLoader, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickEquipmentPhoto(true) }, enabled = enabled) { Text("Aufnehmen") }
                OutlinedButton({ pickEquipmentPhoto(false) }, enabled = enabled) { Text("Foto auswählen") }
            }
            field("name", "Gerätebezeichnung", true)
            if (enabled) NativeSuggestions("Gerätebezeichnung auswählen", listOf("Rasenmäher", "Freischneider/Motorsense", "Rasentrimmer", "Vertikutierer", "Rasenlüfter", "Heckenschere", "Hochheckenschere", "Kettensäge", "Hochentaster", "Häcksler", "Laubbläser", "Laubsauger", "Laubsammler", "Kehrmaschine", "Hochdruckreiniger", "Motorhacke/Bodenfräse", "Rasenwalze", "Streuwagen", "Wasserpumpe", "Drucksprühgerät")) { update("name", it) }
            choice("condition", "Zustand", listOf("ready", "maintenance", "defective"), "ready")
            field("maintenance_due", "Zusätzlich vorgemerkt · JJJJ-MM-TT"); field("notes", "Notizen / Defekt", multiline = true)
        }
        "movement" -> {
            if (text("material_name").isNotEmpty()) Text(text("material_name") + " · Bestand: " + text("stock_decimal"))
            if (!value.optBoolean("_resource_fixed")) relation("material_id", "materials", "Material")
            if (text("reverses_movement_id").isEmpty()) {
                choice("movement_kind", "Bewegung", if (text("session_id").isEmpty()) listOf("receive", "consume", "adjust") else listOf("consume"), "consume")
                field("quantity_decimal", "Menge", true)
            } else Text("Nachvollziehbare Gegenbuchung der ursprünglichen Materialbewegung.")
            if (text("session_id").isEmpty()) { relation("property_id", "properties", "Objekt bei Einsatz"); relation("visit_id", "visits", "Besuch (optional)") }
            else Text("Die Zuordnung zur laufenden Arbeit bleibt erhalten. Der Timer läuft unverändert weiter.")
            field("reason", "Grund", true, true)
        }
        "equipment_use" -> {
            if (value.optBoolean("_resource_fixed")) Text(value.optString("equipment_name", edit.original.optString("name"))) else relation("equipment_id", "equipment", "Gerät")
            if (text("session_id").isEmpty()) { relation("property_id", "properties", "Objekt"); relation("visit_id", "visits", "Besuch (optional)") }
            else Text("Die Zuordnung zur laufenden Arbeit bleibt erhalten. Der Timer läuft unverändert weiter.")
            field("notes", "Hinweise zur Nutzung", multiline = true)
        }
        "maintenance_plan", "maintenance_adjust", "maintenance_done" -> {
            val plan = value.optJSONObject("plan")
            if (plan != null) Text(plan.optString("title"), style = MaterialTheme.typography.titleMedium)
            if (edit.kind == "maintenance_plan") {
                field("title", "Wartungstätigkeit", true)
                RevierMaintenanceSuggestions(controller, enabled) { update("title", it) }
                field("interval_value", "Alle", true)
                choice("interval_unit", "Einheit", listOf("days", "weeks", "months", "years"), "days")
                if (text("plan_id").isEmpty()) field("starts_on", "Intervall beginnt am · JJJJ-MM-TT", true) else field("reason", "Grund für die Änderung", true, true)
                Text("Dein eigenes Intervall. Hierdurch wird noch keine Wartung als erledigt eingetragen.")
            } else if (edit.kind == "maintenance_adjust") {
                choice("operation", "Änderung", if ((plan?.optString("next_due_on") ?: "") <= revierDay()) listOf("shorten") else listOf("bring_forward", "shorten"), "bring_forward")
                if (text("operation", "bring_forward") == "bring_forward") field("next_due_on", "Nächste Wartung · JJJJ-MM-TT", true)
                else { field("interval_value", "Neues Intervall", true); choice("interval_unit", "Einheit", listOf("days", "weeks", "months", "years"), "days") }
                field("reason", "Grund", true, true)
                RevierPhrasePicker("Grund auswählen", revierMaintenanceReasons, enabled) { update("reason", it) }
            } else {
                field("performed_on", "Erledigt am · JJJJ-MM-TT", true)
                Row { Checkbox(value.optBoolean("shorten_interval"), { if (enabled) update("shorten_interval", it) }, enabled = enabled); Text("Intervall dauerhaft verkürzen", Modifier.padding(top = 12.dp)) }
                if (value.optBoolean("shorten_interval")) { field("shorter_interval_value", "Kürzeres Intervall", true); choice("shorter_interval_unit", "Neue Einheit", listOf("days", "weeks", "months", "years"), "days") }
                field("reason", "Grund / Anlass", value.optBoolean("shorten_interval"), true)
                RevierPhrasePicker("Grund auswählen", revierMaintenanceReasons, enabled) { update("reason", it) }
                field("notes", "Notiz", multiline = true)
            }
        }
    }
}

@Composable
private fun RevierChoice(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(choices.firstOrNull { it.first == value }?.second ?: "Bitte auswählen") }
            DropdownMenu(expanded, { expanded = false }) {
                for ((key, text) in choices) DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onChange(key) })
            }
        }
    }
}

@Composable
private fun RevierMaintenanceSuggestions(controller: NativeAppController, enabled: Boolean, onChoose: (String) -> Unit) {
    var phrases by remember { mutableStateOf<List<RevierPhrase>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller, retry) {
        try { phrases = RevierMaintenanceCatalog.actions(controller); failed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    RevierPhrasePicker("Tätigkeitstext auswählen", phrases, enabled, onChoose)
    if (failed) {
        Text("Tätigkeitstexte derzeit nicht lesbar. Eine eigene Tätigkeit kannst du weiterhin eingeben.")
        TextButton({ retry++ }, enabled = enabled) { Text("Texte erneut lesen") }
    }
}

@Composable
private fun RevierPhrasePicker(label: String, phrases: List<RevierPhrase>, enabled: Boolean, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    OutlinedButton({ search = ""; open = true }, enabled = enabled && phrases.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bewusst auswählen und bei Bedarf frei bearbeiten. Die Auswahl speichert noch keine Wartung.")
                NativeField("Text suchen", search, { search = it })
                val matching = phrases.filter { search.isBlank() || it.label.contains(search, ignoreCase = true) || it.text.contains(search, ignoreCase = true) }
                Text("${matching.size} Texte")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matching, key = { it.id }) { phrase ->
                        TextButton({ if (enabled) onChoose(phrase.text); open = false }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(phrase.label, style = MaterialTheme.typography.labelLarge)
                                if (phrase.text != phrase.label) Text(phrase.text)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ open = false }) { Text("Zurück") } }
    )
}

@Composable
private fun RevierRelations(controller: NativeAppController, collection: String, label: String, field: String, values: JSONObject, enabled: Boolean, update: (String, Any) -> Unit) {
    val selected = values.optJSONArray(field) ?: JSONArray()
    Text(label, style = MaterialTheme.typography.titleMedium)
    for (index in 0 until selected.length()) {
        NativeRecordPicker(controller, collection, label, selected.optString(index)) { chosen ->
            if (enabled) update(field, JSONArray((0 until selected.length()).map { if (it == index) chosen else selected.optString(it) }))
        }
        if (enabled) TextButton({ update(field, JSONArray((0 until selected.length()).filter { it != index }.map { selected.optString(it) })) }) { Text("Auswahl entfernen") }
    }
    if (enabled) OutlinedButton({ update(field, JSONArray((0 until selected.length()).map { selected.optString(it) } + "")) }) { Text("$label ergänzen") }
}

@Composable
private fun RevierMaterialNeeds(controller: NativeAppController, needs: JSONArray, enabled: Boolean, change: (JSONArray) -> Unit) {
    Text("Materialbedarf", style = MaterialTheme.typography.titleMedium)
    for (index in 0 until needs.length()) {
        val row = needs.getJSONObject(index)
        fun set(field: String, value: String) {
            if (enabled) change(JSONArray((0 until needs.length()).map { if (it == index) row.revierCopy().put(field, value) else needs.getJSONObject(it) }))
        }
        NativeRecordPicker(controller, "materials", "Material", row.optString("material_id")) { set("material_id", it) }
        if (enabled) NativeField("Menge", row.optString("quantity_decimal"), { set("quantity_decimal", it) }) else Text("Menge: " + row.optString("quantity_decimal"))
        if (enabled) TextButton({ change(JSONArray((0 until needs.length()).filter { it != index }.map { needs.getJSONObject(it) })) }) { Text("Bedarf entfernen") }
    }
    if (enabled) OutlinedButton({ change(JSONArray(needs.toString()).put(JSONObject().put("material_id", "").put("quantity_decimal", ""))) }) { Text("Materialbedarf ergänzen") }
}

@Composable
private fun RevierMeasurements(rows: JSONArray, enabled: Boolean, change: (JSONArray) -> Unit) {
    Text("Messwerte", style = MaterialTheme.typography.titleMedium)
    for (index in 0 until rows.length()) {
        val row = rows.getJSONObject(index)
        for ((field, label) in listOf("name" to "Messwert", "value_decimal" to "Wert", "unit" to "Einheit")) {
            if (enabled) NativeField(label, row.optString(field), { value -> change(JSONArray((0 until rows.length()).map { if (it == index) row.revierCopy().put(field, value) else rows.getJSONObject(it) })) })
            else Text("$label: " + row.optString(field))
        }
        if (enabled) TextButton({ change(JSONArray((0 until rows.length()).filter { it != index }.map { rows.getJSONObject(it) })) }) { Text("Messwert entfernen") }
    }
    if (enabled) OutlinedButton({ change(JSONArray(rows.toString()).put(JSONObject().put("name", "").put("value_decimal", "").put("unit", ""))) }) { Text("Messwert ergänzen") }
}

private fun revierDecimal(value: String): String {
    val normalized = value.trim().replace(',', '.')
    require(normalized.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?"))) { "Menge bitte als Dezimalzahl mit höchstens sechs Nachkommastellen eingeben." }
    return normalized
}
private fun revierLocalInstant(value: String): Instant {
    val local = try { LocalDateTime.parse(value) } catch (_: Exception) { throw IllegalArgumentException("Datum und Uhrzeit als JJJJ-MM-TTTHH:mm angeben.") }
    val offsets = ZoneId.of("Europe/Vienna").rules.getValidOffsets(local)
    require(offsets.size == 1) { "Diese Uhrzeit ist durch die Zeitumstellung mehrdeutig oder nicht vorhanden. Bitte eine eindeutige Uhrzeit wählen." }
    return local.toInstant(offsets.single())
}
private fun revierArguments(edit: RevierEdit, value: JSONObject): JSONObject {
    val editing = edit.original.optString("id").isNotEmpty()
    val args = JSONObject().put("request_id", edit.request)
    val id = edit.original.optString("id")
    fun fields(vararg keys: String, blanks: Boolean = editing): JSONObject = JSONObject().apply {
        for (key in keys) if (value.has(key) && (blanks || value.revierString(key).isNotEmpty())) put(key, value.get(key))
    }
    fun merge(source: JSONObject) { for (key in source.keys()) args.put(key, source.get(key)) }
    fun number(key: String): Int = value.revierString(key).toIntOrNull() ?: throw IllegalArgumentException("Bitte eine ganze Zahl für „${mapOf("planned_minutes" to "Geplante Minuten", "interval_days" to "Turnus", "interval_value" to "Intervall", "duration_minutes" to "Dauer")[key] ?: "Dauer / Intervall"}“ eingeben.")
    fun guarded(key: String) { args.put(key, id).put("expected_record_hash", edit.observedHash) }
    fun decimalRows(key: String, field: String): JSONArray = JSONArray().apply {
        for (row in value.optJSONArray(key)?.revierObjects().orEmpty()) put(row.revierCopy().put(field, revierDecimal(row.optString(field))))
    }
    val tool = when (edit.kind) {
        "property" -> {
            val data = fields("customer_id", "name", "address", "district", "access_notes", "notes", "area_decimal", "area_unit")
            if (data.optString("area_decimal").isNotEmpty()) data.put("area_decimal", revierDecimal(data.getString("area_decimal")))
            if (editing) { data.remove("customer_id"); guarded("property_id"); args.put("updates", data); "update_property" } else { merge(data); "create_property" }
        }
        "inquiry" -> {
            val data = fields("customer_id", "property_id", "title", "concern", "wish_from", "wish_until", "status", "offer_id", "order_id")
            if (value.optString("inspection_appointment_id").isNotEmpty()) data.put("inspection_notes", value.optString("inspection_notes")).put("measurements", decimalRows("measurements", "value_decimal")).put("photo_ids", value.optJSONArray("photo_ids") ?: JSONArray())
            if (editing) { data.remove("customer_id"); guarded("inquiry_id"); args.put("updates", data); "update_inquiry" } else { merge(data); "create_inquiry" }
        }
        "care_plan" -> {
            val data = fields("property_id", "title", "anchor_date", "season_start", "season_end", "notes")
                .put("interval_days", number("interval_days")).put("planned_minutes", number("planned_minutes"))
                .put("material_needs", decimalRows("material_needs", "quantity_decimal")).put("equipment_ids", value.optJSONArray("equipment_ids") ?: JSONArray())
            if (editing) { data.remove("property_id"); guarded("care_plan_id"); args.put("updates", data).put("scope", "this_and_future").put("effective_from", value.getString("effective_from")); "update_care_plan" }
            else { merge(data); "create_care_plan" }
        }
        "visit" -> {
            val data = fields("property_id", "order_id", "title", "date", "window_start", "window_end", "notes", "status", "parent_visit_id")
                .put("planned_minutes", number("planned_minutes")).put("equipment_ids", value.optJSONArray("equipment_ids") ?: JSONArray())
            if (editing) {
                data.remove("property_id"); data.remove("order_id"); data.remove("parent_visit_id")
                if (value.revierString("remaining_minutes").isNotEmpty()) data.put("remaining_minutes", number("remaining_minutes"))
                guarded("visit_id"); args.put("updates", data).put("reason", value.optString("reason")).put("scope", "only_this"); "update_visit"
            } else { merge(data); "plan_visit" }
        }
        "direct_acceptance" -> {
            guarded("inquiry_id"); args.put("accepted_at", revierLocalInstant(value.getString("accepted_local")).toString()).put("evidence", value.optString("evidence"))
                .put("confirmed", value.optBoolean("confirmed")); if (value.optString("billing_customer_id").isNotEmpty()) args.put("billing_customer_id", value.getString("billing_customer_id")); "accept_inquiry_direct"
        }
        "inspection" -> {
            guarded("inquiry_id"); val start = revierLocalInstant(value.getString("starts_local"))
            args.put("starts_at", start.toString()).put("ends_at", start.plusSeconds(number("duration_minutes") * 60L).toString()).put("timezone", "Europe/Vienna")
                .put("mode", value.optString("mode", if (value.optString("inspection_appointment_id").isEmpty()) "first" else "reschedule")).put("notes", value.optString("notes"))
            if (value.optString("appointment_hash").isNotEmpty()) args.put("expected_appointment_hash", value.getString("appointment_hash")); "plan_inquiry_inspection"
        }
        "generate" -> { args.put("care_plan_id", id).put("from_date", value.getString("from_date")).put("to_date", value.getString("to_date")); "generate_care_visits" }
        "material" -> {
            merge(fields("name", "unit", "notes"))
            for ((input, key) in listOf("purchase_price" to "purchase_price_minor", "billing_price" to "billing_price_minor")) {
                val text = value.revierString(input).trim().replace(',', '.')
                if (text.isNotEmpty()) {
                    require(text.matches(Regex("(0|[1-9][0-9]*)(\\.[0-9]{1,2})?"))) { "Preis als nicht negativen EUR-Betrag mit höchstens zwei Nachkommastellen eingeben." }
                    val minor = BigDecimal(text).movePointRight(2).longValueExact()
                    require(minor <= 9_007_199_254_740_991L) { "Dieser Preis ist zu groß." }; args.put(key, minor)
                } else if (editing && edit.original.has(key)) args.put(key, JSONObject.NULL)
            }
            if (editing) guarded("material_id"); "save_material"
        }
        "accessories" -> {
            guarded("equipment_id")
            val accessories = JSONArray()
            for (row in value.optJSONArray("accessories")?.revierObjects().orEmpty()) accessories.put(row.revierCopy().apply { remove("equipment_id"); remove("photo"); remove("selected_photo_uri") })
            args.put("accessories", accessories).put("current_chain_id", value.optString("current_chain_id"))
            "save_equipment"
        }
        "equipment" -> { merge(fields("name", "condition", "maintenance_due", "notes", "profile_photo_upload_id")); if (editing) guarded("equipment_id"); "save_equipment" }
        "movement" -> {
            args.put("material_id", value.optString("material_id", id)).put("expected_record_hash", edit.observedHash).put("kind", value.optString("movement_kind", "consume"))
            merge(fields("property_id", "visit_id", "session_id", "reason", "reverses_movement_id", blanks = false))
            if (value.optString("reverses_movement_id").isEmpty()) args.put("quantity_decimal", revierDecimal(value.getString("quantity_decimal")))
            else args.put("kind", "reversal")
            "record_material_movement"
        }
        "equipment_use" -> {
            args.put("equipment_id", value.optString("equipment_id", id)).put("expected_record_hash", edit.observedHash)
            merge(fields("property_id", "visit_id", "session_id", "notes", blanks = false)); "record_equipment_usage"
        }
        "maintenance_plan", "maintenance_adjust", "maintenance_done" -> {
            args.put("equipment_id", value.optString("equipment_id", id)).put("expected_record_hash", edit.observedHash)
            if (value.optString("plan_id").isNotEmpty()) args.put("plan_id", value.getString("plan_id"))
            if (edit.kind == "maintenance_done") {
                args.put("performed_on", value.getString("performed_on")).put("reason", value.optString("reason")).put("notes", value.optString("notes"))
                if (value.optBoolean("shorten_interval")) args.put("shorter_interval_value", number("shorter_interval_value")).put("shorter_interval_unit", value.optString("shorter_interval_unit", "days"))
                "record_equipment_maintenance"
            } else {
                val operation = if (edit.kind == "maintenance_plan") "configure" else value.optString("operation", "bring_forward")
                args.put("operation", operation)
                if (edit.kind == "maintenance_plan") { args.put("title", value.optString("title")); if (value.optString("plan_id").isEmpty()) args.put("starts_on", value.getString("starts_on")) }
                if (operation == "bring_forward") args.put("next_due_on", value.getString("next_due_on"))
                else args.put("interval_value", number("interval_value")).put("interval_unit", value.optString("interval_unit", "days"))
                if (value.optString("reason").isNotEmpty()) args.put("reason", value.getString("reason"))
                "save_equipment_maintenance_plan"
            }
        }
        else -> error("Unbekannte Bearbeitung.")
    }
    return JSONObject().put("tool", tool).put("args", args)
}

@Composable
private fun RevierTour(controller: NativeAppController) {
    var dateInput by rememberSaveable { mutableStateOf(controller.route.args.optString("date", revierDay())) }
    var date by rememberSaveable { mutableStateOf(dateInput) }
    var tour by remember(date) { mutableStateOf<JSONObject?>(null) }
    var available by remember(date) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember(date) { mutableStateOf<List<String>>(emptyList()) }
    var originalHash by remember(date) { mutableStateOf("") }
    var pending by remember(date) { mutableStateOf<JSONObject?>(null) }
    var restored by remember(date) { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var comparison by remember { mutableStateOf<JSONObject?>(null) }
    var reviewed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val key = "revier:tour:$date"
    fun retain() { controller.saveDraft(key, JSONObject().put("selected", JSONArray(selected)).put("expected_record_hash", originalHash).put("pending", pending ?: JSONObject.NULL)) }
    LaunchedEffect(date, revision) {
        try {
            val result = controller.query("get_day_tour", JSONObject().put("date", date))
            tour = result
            val visits = controller.query("list_visits", JSONObject().put("from_date", date).put("to_date", date).put("limit", 100))
            val all = (result.optJSONArray("stops")?.revierObjects().orEmpty() + visits.optJSONArray("items")?.revierObjects().orEmpty()).distinctBy { it.optString("id") }
            available = all
            val saved = controller.draft(key)
            val chosen = saved?.optJSONArray("selected") ?: result.getJSONObject("tour").getJSONArray("visit_ids")
            selected = (0 until chosen.length()).map { chosen.getString(it) }
            originalHash = saved?.optString("expected_record_hash")?.takeIf { it.isNotEmpty() } ?: result.getString("record_hash")
            pending = saved?.optJSONObject("pending")
            restored = saved?.optBoolean("_restore_action_review_required") == true
        } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
    }
    fun save(operation: JSONObject) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val existing = pending
                if (existing != null) {
                    val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", existing.getString("tool")).put("original_args", existing.getJSONObject("args")))
                    if (proof.optString("state") == "content_removed") error("Diese Touränderung wurde endgültig entfernt.")
                    if (proof.optString("state") == "confirmed") {
                        controller.removeDraft(key); pending = null; revision++; controller.message("Touränderung bestätigt."); return@launch
                    }
                    if (restored) error("Alte Touränderung ohne eindeutigen Beleg. Aktuellen Bestand zuerst prüfen.")
                }
                if (restored) error("Tourentwurf stammt aus einem anderen Gerätestand. Aktuelle Tour zuerst prüfen.")
                pending = existing ?: operation
                retain(); controller.flushDrafts()
                controller.query(pending!!.getString("tool"), pending!!.getJSONObject("args"))
                controller.removeDraft(key); pending = null; revision++; failure = ""; controller.message("Tagestour lokal gespeichert.")
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) {
                if (error is NativeCallFailure && error.detail.optString("commit_state") == "not_committed") pending = null
                retain(); failure = error.message.orEmpty()
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tagestour", style = MaterialTheme.typography.headlineSmall)
                    NativeField("Datum · JJJJ-MM-TT", dateInput, { dateInput = it })
                    OutlinedButton({ date = dateInput; revision++ }, enabled = !busy) { Text("Datum öffnen") }
                    Text("Reihenfolge selbst festlegen.")
                    if (tour?.optJSONObject("tour")?.optBoolean("needs_review") == true) Text("Tour nach Terminänderung prüfen: " + tour?.optJSONObject("tour")?.optString("review_reason"), color = MaterialTheme.colorScheme.error)
                    val savedIds = tour?.optJSONObject("tour")?.optJSONArray("visit_ids")
                    if (savedIds != null && selected != (0 until savedIds.length()).map { savedIds.getString(it) }) Text("Tourentwurf · noch nicht gespeichert")
                    if (pending != null || restored) Text("Eine frühere Änderung muss zuerst anhand ihres Ergebnisses geprüft werden.")
                    if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
                    if ((failure.isNotEmpty() || restored) && pending == null) TextButton({
                        scope.launch {
                            try { comparison = controller.query("get_day_tour", JSONObject().put("date", date)); reviewed = false }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { failure = error.message.orEmpty() }
                        }
                    }) { Text("Aktuelle Tour vergleichen") }
                }
            }
            items(available.sortedWith(compareBy<JSONObject> { selected.indexOf(it.getString("id")).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }.thenBy { it.optString("title") }), key = { it.getString("id") }) { visit ->
                val visitId = visit.getString("id")
                val index = selected.indexOf(visitId)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Checkbox(index >= 0, { checked -> selected = if (checked) selected + visitId else selected - visitId; retain() }, enabled = !busy && pending == null && !restored)
                            Column(Modifier.padding(top = 8.dp)) { Text((if (index >= 0) "${index + 1}. " else "") + visit.optString("title", "Einsatz")); Text(listOf(visit.optString("date"), revierLabel(visit.optString("status")), visit.optString("window_start"), visit.optString("window_end")).filter { it.isNotBlank() }.joinToString(" · ")) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ controller.navigate("visits", visitId, "detail") }) { Text("Details") }
                            TextButton({ selected = selected.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) }; retain() }, enabled = index > 0 && pending == null && !restored && !busy) { Text("Nach oben") }
                            TextButton({ selected = selected.toMutableList().apply { java.util.Collections.swap(this, index, index + 1) }; retain() }, enabled = index >= 0 && index < selected.lastIndex && pending == null && !restored && !busy) { Text("Nach unten") }
                        }
                    }
                }
            }
            if (available.isEmpty()) item { Text("Für diesen Tag sind keine Besuche geplant.") }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ controller.navigate("visits") }, Modifier.weight(1f)) { Text("Besuche") }
            Button({
                val previous = tour?.optJSONObject("tour")?.optJSONArray("visit_ids")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } }.orEmpty()
                val changed = if (previous.size == selected.size) previous.indices.filter { previous[it] != selected[it] } else emptyList()
                val adjacent = changed.size == 2 && changed[1] == changed[0] + 1 && previous[changed[0]] == selected[changed[1]] && previous[changed[1]] == selected[changed[0]]
                val args = JSONObject().put("request_id", revierRequest()).put("date", date).put("expected_record_hash", originalHash)
                if (adjacent) args.put("visit_id", previous[changed[0]]).put("direction", "down") else args.put("visit_ids", JSONArray(selected))
                save(JSONObject().put("tool", if (adjacent) "move_tour_stop" else "set_day_tour").put("args", args))
            }, enabled = !busy && originalHash.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (pending != null) "Ergebnis prüfen" else "Tour speichern") }
        }
    }
    comparison?.let { current ->
        AlertDialog(onDismissRequest = { comparison = null }, title = { Text("Tagestour vergleichen") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aktuell gespeichert:", style = MaterialTheme.typography.titleMedium)
                for ((index, stop) in current.optJSONArray("stops")?.revierObjects().orEmpty().withIndex()) Text("${index + 1}. " + stop.optString("title", "Einsatz"))
                Text("Eigener Entwurf:", style = MaterialTheme.typography.titleMedium)
                for ((index, visitId) in selected.withIndex()) Text("${index + 1}. " + (available.firstOrNull { it.optString("id") == visitId }?.optString("title") ?: "Zugeordneter Einsatz"))
                Row { Checkbox(reviewed, { reviewed = it }); Text("Aktuelle Tour und eigenen Entwurf geprüft", Modifier.padding(top = 12.dp)) }
            }
        }, dismissButton = { TextButton({ comparison = null }) { Text("Zurück") } }, confirmButton = {
            Button({
                busy = true
                scope.launch {
                    try {
                        check(pending == null) { "Ergebnis der ursprünglichen Touränderung zuerst prüfen." }
                        controller.adoptDraft(key, JSONObject().put("selected", JSONArray(selected)).put("expected_record_hash", current.getString("record_hash")).put("pending", JSONObject.NULL))
                        originalHash = current.getString("record_hash"); restored = false; comparison = null; failure = "Vergleich bestätigt. Tour erneut ausdrücklich speichern."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failure = error.message.orEmpty() }
                    finally { busy = false }
                }
            }, enabled = reviewed && !busy && pending == null) { Text("Weiterbearbeiten") }
        })
    }
}

@Composable
private fun RevierArchive(controller: NativeAppController) {
    var kind by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(kind, offset, retry) {
        try { result = controller.query("list_revier_archive", JSONObject().put("offset", offset).put("limit", 20).apply { if (kind.isNotEmpty()) put("entity", kind) }); failure = "" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message.orEmpty() }
    }
    val rows = result?.optJSONArray("items")?.revierObjects().orEmpty()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Revier-Archiv", style = MaterialTheme.typography.headlineSmall)
                RevierChoice("Art", kind, listOf("" to "Alle Arten") + revierSections.map { it.kind to it.title }) { kind = it; offset = 0 }
                result?.let { Text("${it.optInt("total")} archivierte Einträge") }
                if (failure.isNotEmpty()) { Text(failure, color = MaterialTheme.colorScheme.error); TextButton({ retry++ }) { Text("Erneut lesen") } }
            }
            items(rows, key = { it.optString("entity") + ":" + it.getJSONObject("record").getString("id") }) { entry ->
                val row = entry.getJSONObject("record")
                OutlinedCard(Modifier.fillMaxWidth().clickable { controller.navigate(revierCollection(entry.getString("entity")), row.getString("id"), "detail") }) {
                    Column(Modifier.padding(12.dp)) { Text(row.optString("name", row.optString("title", "Eintrag"))); Text("Archiviert · zum Prüfen und Wiederherstellen öffnen") }
                }
            }
            if (result != null && rows.isEmpty()) item { Text("Keine archivierten Einträge dieser Art.") }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0) { Text("Vorherige") }
            OutlinedButton({ offset += 20 }, enabled = offset + rows.size < (result?.optInt("total") ?: 0)) { Text("Weitere") }
        }
    }
}

@Composable
private fun RevierSearch(controller: NativeAppController) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(controller.route.mode == "archive") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    fun search(start: Int, usePrevious: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val args = if (usePrevious) frozen?.revierCopy() ?: JSONObject() else JSONObject().put("query", query).put("include_archived", archived).apply {
                    if (type.isNotEmpty()) put("types", JSONArray().put(type))
                    for ((key, value) in listOf("status" to status, "from_date" to from, "to_date" to until)) if (value.isNotEmpty()) put(key, value)
                }
                args.put("offset", start).put("limit", 50)
                result = controller.query("search_local", args); frozen = args; offset = start; failure = ""
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { failure = error.message.orEmpty() }
            finally { busy = false }
        }
    }
    val rows = result?.optJSONArray("items")?.revierObjects().orEmpty()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lokale Suche", style = MaterialTheme.typography.headlineSmall)
                    NativeField("Name, Objekt, Tätigkeit, Notiz oder Beleg", query, { query = it })
                    RevierChoice("Art", type, listOf("" to "Alle Arten", "customer" to "Kunden", "property" to "Objekte", "inquiry" to "Anfragen", "care_plan" to "Pflegepläne", "order" to "Aufträge", "appointment" to "Termine", "visit" to "Besuche", "activity" to "Arbeiten", "note" to "Notizen", "offer" to "Angebote", "invoice" to "Rechnungen", "material" to "Material", "equipment" to "Geräte")) { type = it }
                    NativeField("Status (optional)", status, { status = it })
                    NativeField("Zeitraum ab · JJJJ-MM-TT", from, { from = it }); NativeField("Zeitraum bis · JJJJ-MM-TT", until, { until = it })
                    Row { Checkbox(archived, { archived = it }); Text("Archivierte und entfernte Einträge einschließen", Modifier.padding(top = 12.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ search(0) }, enabled = !busy && query.isNotBlank()) { Text("Lokal suchen") }
                        TextButton({ query = ""; type = ""; status = ""; from = ""; until = ""; archived = false; result = null; frozen = null; offset = 0 }) { Text("Zurücksetzen") }
                    }
                    result?.let { Text("${it.optInt("total")} Treffer im gesamten lokalen Bestand") }
                    if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
                }
            }
            items(rows, key = { it.optString("type") + ":" + it.optString("id") }) { row ->
                OutlinedCard(Modifier.fillMaxWidth().clickable {
                    val target = row.optJSONObject("target") ?: row
                    val targetType = target.optString("type", row.optString("entity_type"))
                    val route = when (targetType) {
                        "customer" -> "customers"; "property" -> "properties"; "inquiry" -> "inquiries"; "care_plan" -> "care_plans"; "visit" -> "visits"
                        "activity", "worklog" -> "worklogs"; "order" -> "orders"; "offer" -> "offers"; "invoice" -> "invoices"; "appointment" -> "planning"; "material" -> "materials"; "equipment" -> "equipment"; else -> targetType
                    }
                    controller.navigate(if (row.optBoolean("archived") && targetType !in listOf("property", "inquiry", "care_plan", "visit", "material", "equipment")) "trash" else route, target.optString("id", row.optString("id")), "detail", JSONObject().put("entity", targetType))
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.optString("label", row.optString("title", "Eintrag")), style = MaterialTheme.typography.titleMedium)
                        Text(row.optString("snippet", row.optString("text", row.optString("status"))))
                        if (row.optBoolean("archived")) Text("Archiviert / entfernt")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ search((offset - 50).coerceAtLeast(0), true) }, enabled = offset > 0 && !busy) { Text("Vorherige") }
            OutlinedButton({ search(offset + 50, true) }, enabled = offset + rows.size < (result?.optInt("total") ?: 0) && !busy) { Text("Weitere Treffer") }
        }
    }
}

private val revierCompareLabels = linkedMapOf(
    "name" to "Bezeichnung", "title" to "Bezeichnung", "customer_id" to "Kunde", "property_id" to "Objekt", "order_id" to "Auftrag", "offer_id" to "Angebot",
    "billing_customer_id" to "Rechnungsempfänger", "interval_value" to "Intervall", "interval_unit" to "Intervalleinheit", "next_due_on" to "Nächste Wartung",
    "performed_on" to "Erledigt am", "shorter_interval_value" to "Kürzeres Intervall", "shorter_interval_unit" to "Neue Einheit", "reason" to "Grund",
    "material_needs" to "Materialbedarf", "equipment_ids" to "Benötigte Geräte", "measurements" to "Messwerte", "photo_ids" to "Dokumentationsfotos"
)

@Composable
private fun RevierComparisonValue(field: String, value: Any?, repository: NativeDataRepository) {
    val text by produceState("Wird gelesen …", field, value?.toString()) {
        this.value = try { withContext(Dispatchers.IO) {
            suspend fun recordLabel(collection: String, id: String): String {
                if (id.isEmpty()) return "Keine Zuordnung"
                val row = repository.detail(collection, id)
                return if (row == null) "Zuordnung derzeit nicht lesbar" else listOf(row.optString("name"), row.optString("title"), row.optString("caption")).firstOrNull { it.isNotBlank() } ?: "Zugeordneter Eintrag"
            }
            when {
                value == null || value == JSONObject.NULL || value.toString().isEmpty() -> "Leer"
                field in listOf("customer_id", "billing_customer_id", "property_id", "offer_id", "order_id") -> recordLabel(when (field) { "customer_id", "billing_customer_id" -> "customers"; "property_id" -> "properties"; "offer_id" -> "offers"; else -> "orders" }, value.toString())
                field in listOf("equipment_ids", "photo_ids") && value is JSONArray -> (0 until value.length()).map { recordLabel(if (field == "equipment_ids") "equipment" else "photos", value.optString(it)) }.joinToString("; ").ifEmpty { "Keine Auswahl" }
                field == "material_needs" && value is JSONArray -> value.revierObjects().map { recordLabel("materials", it.optString("material_id")) + ": " + it.optString("quantity_decimal") }.joinToString("; ").ifEmpty { "Kein Bedarf" }
                field == "measurements" && value is JSONArray -> value.revierObjects().joinToString("; ") { listOf(it.optString("name"), it.optString("value_decimal"), it.optString("unit")).filter { part -> part.isNotBlank() }.joinToString(" ") }.ifEmpty { "Keine Messwerte" }
                field in listOf("status", "condition", "interval_unit", "shorter_interval_unit") -> revierLabel(value.toString())
                else -> value.toString()
            }
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "Angabe derzeit nicht lesbar. Vor dem Speichern erneut prüfen." }
    }
    Text(text)
}

/** A selected original is a file-backed draft, retained independently of provider grants. */
internal suspend fun nativeRetainPhotoDraft(controller: NativeAppController, source: Uri): Uri = withContext(Dispatchers.IO) {
    val mime = controller.context.contentResolver.getType(source).orEmpty()
    val extension = when (mime) { "image/png" -> ".png"; "image/webp" -> ".webp"; "image/gif" -> ".gif"; "image/heic", "image/heif" -> ".heic"; else -> ".jpg" }
    // Cache is outside the captured files tree. Only a complete immutable draft is published.
    val temporary = File.createTempFile("native-equipment-", extension, controller.context.cacheDir)
    try {
        controller.context.contentResolver.openInputStream(source)?.use { input ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(32768)
                var size = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= 20L * 1024 * 1024) { "Bitte ein Foto bis 20 MB auswählen." }
                    output.write(buffer, 0, count)
                }
                require(size > 0) { "Das Foto ist leer." }
                output.flush()
            }
        } ?: error("Das gewählte Foto ist nicht mehr lesbar.")
        currentCoroutineContext().ensureActive()
        val file = synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
            val directory = File(controller.context.filesDir, "native-form-media")
            check(directory.isDirectory || directory.mkdirs()) { "Fotoentwurf kann nicht gespeichert werden." }
            val target = File(directory, "equipment-" + UUID.randomUUID() + extension)
            check(temporary.renameTo(target)) { "Fotoentwurf kann nicht veröffentlicht werden." }
            target
        }
        Uri.fromFile(file)
    } finally { temporary.delete() }
}
internal suspend fun nativeDeletePhotoDraft(controller: NativeAppController, source: String) = withContext(Dispatchers.IO) {
    if (source.isEmpty()) return@withContext
    val uri = Uri.parse(source)
    if (uri.scheme != "file") return@withContext
    synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
        val directory = File(controller.context.filesDir, "native-form-media").canonicalFile
        val file = File(uri.path.orEmpty()).canonicalFile
        if (file.parentFile == directory && file.name.startsWith("equipment-")) {
            check(!file.exists() || file.delete()) { "Das ungespeicherte Foto konnte nicht entfernt werden. Bitte erneut verwerfen." }
        }
    }
}

private val accessoryFields = linkedMapOf("pitch" to "Kettenteilung", "gauge" to "Treibgliedstärke", "drive_links" to "Anzahl Treibglieder", "article_number" to "Hersteller-Artikelnummer", "tooth_type" to "Schneidezahntyp", "file_size" to "Feilengröße", "notes" to "Weitere Angaben")

@Composable
private fun RevierAccessoryFields(edit: RevierEdit, value: JSONObject, controller: NativeAppController, enabled: Boolean, update: (String, Any) -> Unit) {
    val scope = rememberCoroutineScope()
    Text("Maschine: " + edit.original.optString("name"), style = MaterialTheme.typography.titleMedium)
    Text("Unbekannte Angaben leer lassen. Es werden keine Spezifikationen ergänzt.")
    val entries = value.optJSONArray("accessories") ?: JSONArray()
    fun replace(index: Int, field: String, newValue: Any) {
        val changed = JSONArray(edit.row().optJSONArray("accessories")?.toString() ?: "[]")
        changed.getJSONObject(index).put(field, newValue); update("accessories", changed)
    }
    for (index in 0 until entries.length()) {
        val item = entries.getJSONObject(index)
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RevierChoice("Art", item.optString("type", "other"), listOf("chain" to "Kette", "other" to "Anderes Zubehör"), enabled) {
                if (it != "chain" && value.optString("current_chain_id") == item.optString("id")) update("current_chain_id", "")
                replace(index, "type", it)
            }
            for ((key, label) in linkedMapOf("name" to "Bezeichnung", "manufacturer" to "Hersteller", "model" to "Modell") + accessoryFields.filterKeys { item.optString("type") == "chain" || it in listOf("article_number", "notes") }) {
                if (enabled) NativeField(label, item.optString(key), { replace(index, key, it) }, multiline = key == "notes")
                else if (item.optString(key).isNotBlank()) Text("$label: " + item.optString(key))
            }
            if (item.optString("type") == "chain") Row(Modifier.fillMaxWidth().selectable(
                selected = value.optString("current_chain_id") == item.getString("id"), enabled = enabled,
                role = Role.RadioButton, onClick = { update("current_chain_id", item.getString("id")) }
            )) {
                RadioButton(value.optString("current_chain_id") == item.getString("id"), onClick = null, enabled = enabled)
                Text("Aktuell verwendet", Modifier.padding(top = 12.dp))
            }
            RevierProfilePhoto(item.optJSONObject("photo"), controller)
            if (item.optString("selected_photo_uri").isNotEmpty()) AsyncImage(Uri.parse(item.getString("selected_photo_uri")), "Ungespeichertes Zubehörfoto", imageLoader = controller.imageLoader, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((camera, label) in listOf(true to "Aufnehmen", false to "Foto auswählen")) OutlinedButton({
                    edit.mediaBusy = true
                    scope.launch {
                        try {
                            controller.pickPhoto(camera)?.let { source ->
                                val retained = nativeRetainPhotoDraft(controller, source)
                                val changed = JSONArray(edit.row().optJSONArray("accessories")?.toString() ?: "[]")
                                val current = changed.getJSONObject(index)
                                val previous = current.optString("selected_photo_uri")
                                current.put("selected_photo_uri", retained.toString()).remove("photo_upload_id")
                                edit.change("accessories", changed); controller.saveDraft(edit.key, edit.snapshot()); controller.flushDrafts()
                                nativeDeletePhotoDraft(controller, previous)
                                withContext(Dispatchers.IO) { PhotoCaptureProvider.release(controller.context, source) }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { controller.message(failure.message.orEmpty()) }
                        finally { edit.mediaBusy = false }
                    }
                }, enabled = enabled) { Text(label) }
            }
        } }
    }
    if (value.optString("current_chain_id").isNotEmpty()) TextButton({ update("current_chain_id", "") }, enabled = enabled) { Text("Keine aktuelle Kette festlegen") }
    OutlinedButton({ update("accessories", JSONArray(entries.toString()).put(JSONObject().put("id", "acc_" + UUID.randomUUID().toString().replace("-", "")).put("type", "other"))) }, enabled = enabled) { Text("Zubehör hinzufügen") }
}

private suspend fun revierDeleteEditPhotos(controller: NativeAppController, value: JSONObject) {
    nativeDeletePhotoDraft(controller, value.optString("selected_photo_uri"))
    for (item in value.optJSONArray("accessories")?.revierObjects().orEmpty()) nativeDeletePhotoDraft(controller, item.optString("selected_photo_uri"))
}
