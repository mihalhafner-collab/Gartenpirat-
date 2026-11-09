package at.hausgarten.service

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class RevierSection(
    val kind: String,
    val collection: String,
    val title: String,
    val singular: String,
)

private val revierSections =
    listOf(
        RevierSection("property", "properties", "Objekte", "Objekt"),
        RevierSection("inquiry", "inquiries", "Anfragen", "Anfrage"),
        RevierSection("care_plan", "care_plans", "Pflegepläne", "Pflegeplan"),
        RevierSection("visit", "visits", "Einsätze", "Einsatz"),
        RevierSection("material", "materials", "Material", "Material"),
        RevierSection("equipment", "equipment", "Geräte", "Gerät"),
    )
private val revierActionEditors =
    setOf(
        "accessories",
        "inspection",
        "direct_acceptance",
        "generate",
        "maintenance_plan",
        "maintenance_done",
        "maintenance_adjust",
        "movement",
    )

private data class RevierPhrase(val id: String, val label: String, val text: String)

private object RevierMaintenanceCatalog {
    @Volatile private var cached: List<RevierPhrase>? = null

    suspend fun actions(controller: NativeAppController): List<RevierPhrase> =
        withContext(Dispatchers.IO) {
            synchronized(this@RevierMaintenanceCatalog) {
                cached
                    ?: controller.context.assets
                        .open("equipment-maintenance-texts.json")
                        .bufferedReader(Charsets.UTF_8)
                        .use { reader ->
                            val catalog = JSONObject(reader.readText())
                            check(
                                catalog.getString("format") ==
                                    "hausgarten.equipment-maintenance-texts.v1"
                            )
                            val actions = catalog.getJSONArray("actions")
                            check(actions.length() in 1..100)
                            actions.revierObjects().map {
                                RevierPhrase(
                                    it.getString("id"),
                                    it.getString("label"),
                                    it.getString("text"),
                                )
                            }
                        }
                        .also { cached = it }
            }
        }
}

private val revierMaintenanceReasons =
    listOf(
            "Fehlschnitte festgestellt",
            "Erneutes Nachschärfen früher nötig",
            "Höhere Beanspruchung",
        )
        .mapIndexed { index, text -> RevierPhrase(index.toString(), text, text) }

private val revierStates =
    linkedMapOf(
        "new" to "Neu",
        "inspection_planned" to "Besichtigung geplant",
        "offered" to "Angebot vorhanden",
        "accepted" to "Angenommen",
        "declined" to "Abgelehnt",
        "closed" to "Abgeschlossen",
        "planned" to "Geplant",
        "in_progress" to "In Arbeit",
        "completed" to "Erledigt",
        "partial" to "Teilweise erledigt",
        "cancelled" to "Abgesagt",
        "ready" to "Einsatzbereit",
        "maintenance" to "Wartung",
        "defective" to "Defekt",
        "consume" to "Verbrauch",
        "active" to "Aktiv",
        "paused" to "Pausiert",
        "days" to "Tage",
        "weeks" to "Wochen",
        "months" to "Monate",
        "years" to "Jahre",
        "first" to "Erste Besichtigung",
        "reschedule" to "Verschieben",
        "additional" to "Weitere Besichtigung",
        "reopen" to "Abgesagte Besichtigung wieder öffnen",
        "bring_forward" to "Nächsten Termin vorziehen",
        "shorten" to "Intervall dauerhaft verkürzen",
    )

private fun revierLabel(key: String) = revierStates[key] ?: key

private fun revierDay() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()

private fun revierRequest() = "ui_revier_" + UUID.randomUUID().toString().replace("-", "")

private fun JSONArray.revierObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.revierCopy() = JSONObject(toString())

private fun JSONObject.revierString(key: String): String =
    if (!has(key) || isNull(key)) "" else opt(key)?.toString().orEmpty()

private fun revierCollection(kind: String) = revierSections.first { it.kind == kind }.collection

/**
 * All edits use the same native transactional writer as MCP. Only display pages use projections.
 */
@Composable
internal fun NativeRevierScreen(section: String, id: String, controller: NativeAppController) {
    key(section, id, controller.route.mode, controller.route.args.toString()) {
        RevierPage(section, id, controller)
    }
}

@Composable
private fun RevierPage(section: String, id: String, controller: NativeAppController) {
    val normalized =
        when (section.removePrefix("revier-")) {
            "property",
            "property-detail",
            "properties" -> "properties"
            "inquiry",
            "inquiry-detail",
            "inquiries" -> "inquiries"
            "care",
            "care_plan",
            "care-detail",
            "care_plans" -> "care_plans"
            "visit",
            "visit-detail",
            "visits" -> "visits"
            "material",
            "material-detail",
            "materials" -> "materials"
            "equipment-detail",
            "equipment" -> "equipment"
            "search",
            "revier_search" -> "revier_search"
            else -> section.removePrefix("revier-")
        }
    when (normalized) {
        "tour" -> {
            RevierTour(controller)
            return
        }
        "revier_search" -> {
            RevierSearch(controller)
            return
        }
    }
    val config = revierSections.firstOrNull { it.collection == normalized } ?: return
    val scope = rememberCoroutineScope()
    val repository = remember(controller.store) { NativeDataRepository(controller.store) }
    val listStates = rememberSaveableStateHolder()
    var selected by
        rememberSaveable(section, id) { mutableStateOf(id.takeUnless { it == "new" }.orEmpty()) }
    // The detail leaves composition while its editor is open. Keep the tab with
    // the selected machine so saving or cancelling returns to the same context.
    var equipmentTab by rememberSaveable(selected) { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<RevierEdit?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    fun openEditor(kind: String, record: JSONObject? = null, context: JSONObject = JSONObject()) {
        scope.launch {
            try {
                val computedKey =
                    "revier:$kind:${record?.optString("id").orEmpty()}:" +
                        listOf(
                                "equipment_id",
                                "plan_id",
                                "property_id",
                                "session_id",
                                "visit_id",
                                "worklog_id",
                            )
                            .joinToString(":") { context.optString(it) }
                val editKey = context.optString("_draft_key").ifEmpty { computedKey }
                val saved = controller.draft(editKey)
                editor =
                    if (saved != null) RevierEdit.restore(editKey, saved)
                    else RevierEdit.create(editKey, kind, record, context)
                // A photo shortcut reuses the machine's retained accessory editor;
                // only its presentation focus changes, never its saved inputs.
                if (kind == "accessories")
                    editor?.let { edit ->
                        edit.values =
                            edit
                                .row()
                                .put(
                                    "_focus_accessory_id",
                                    context.optString("_focus_accessory_id"),
                                )
                                .toString()
                    }
                failure = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            }
        }
    }
    LaunchedEffect(section, id, controller.route.mode) {
        val mode = controller.route.mode
        if (id == "new" || mode in listOf("new", "edit") || mode in revierActionEditors) {
            try {
                val retainedKey = controller.route.args.optString("_draft_key")
                if (retainedKey.isNotEmpty()) {
                    val saved =
                        controller.draft(retainedKey)
                            ?: error("Der ausgewählte Entwurf ist nicht mehr vorhanden.")
                    editor = RevierEdit.restore(retainedKey, saved)
                    return@LaunchedEffect
                }
                val original =
                    if (id.isNotEmpty() && id != "new" && mode != "new")
                        withContext(Dispatchers.IO) {
                            repository.detail(config.collection, id)?.let { RevierRules.view(it) }
                                ?: error("Eintrag nicht gefunden.")
                        }
                    else null
                openEditor(
                    if (mode in revierActionEditors) mode else config.kind,
                    original,
                    controller.route.args,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            }
        }
    }
    val currentEdit = editor
    if (currentEdit != null) {
        RevierEditor(
            controller,
            currentEdit,
            repository,
            onClose = {
                editor = null
                if (
                    controller.route.mode in revierActionEditors ||
                        controller.route.mode in listOf("new", "edit") ||
                        id == "new"
                )
                    controller.finishAction()
            },
            onSaved = { result ->
                editor = null
                val record = result.optJSONObject("record")
                if (record != null && currentEdit.kind in revierSections.map { it.kind })
                    selected = record.optString("id")
                refresh++
                if (
                    controller.route.mode in revierActionEditors ||
                        controller.route.mode in listOf("new", "edit") ||
                        id == "new"
                )
                    controller.finishAction()
                val targetKind =
                    when (currentEdit.kind) {
                        "movement" -> "material"
                        "maintenance_done",
                        "maintenance_plan",
                        "maintenance_adjust",
                        "accessories" -> "equipment"
                        else -> currentEdit.kind
                    }
                val targetRecord =
                    result.optJSONObject("record")
                        ?: result.optJSONObject(targetKind)
                        ?: currentEdit.original
                val targetId = result.optString("${targetKind}_id", targetRecord.optString("id"))
                val targetSection = revierSections.firstOrNull { it.kind == targetKind }?.collection
                controller.message(
                    "Gespeichert.",
                    if (targetSection != null && targetId.isNotBlank())
                        NativeRoute(targetSection, targetId, "detail")
                    else null,
                )
            },
        )
    } else if (selected.isNotEmpty()) {
        RevierDetail(
            config,
            selected,
            refresh,
            controller,
            repository,
            equipmentTab,
            { equipmentTab = it },
            onBack = { selected = "" },
            onEdit = ::openEditor,
            onRefresh = { refresh++ },
        )
    } else {
        listStates.SaveableStateProvider("revier-list") {
            RevierList(
                config,
                controller,
                repository,
                onSelect = { selected = it },
                onNew = {
                    openEditor(
                        config.kind,
                        context =
                            JSONObject().apply {
                                for (field in listOf("customer_id", "property_id", "order_id")) if (
                                    controller.route.args.optString(field).isNotEmpty()
                                )
                                    put(field, controller.route.args.getString(field))
                            },
                    )
                },
            )
        }
    }
    if (failure.isNotEmpty())
        Text(failure, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevierList(
    config: RevierSection,
    controller: NativeAppController,
    repository: NativeDataRepository,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var query by rememberSaveable(config.kind) { mutableStateOf("") }
    var status by
        rememberSaveable(config.kind) { mutableStateOf(controller.route.args.optString("status")) }
    var offset by rememberSaveable(config.kind) { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val source =
        remember(config.collection, query, status, offset, retry) {
            repository
                .watchList(
                    config.collection,
                    search = query,
                    status = status,
                    offset = offset,
                    limit = 50,
                    includeInactive = false,
                    customerId = controller.route.args.optString("customer_id"),
                    propertyId = controller.route.args.optString("property_id"),
                    orderId = controller.route.args.optString("order_id"),
                    dateFrom = controller.route.args.optString("date_from"),
                    dateTo = controller.route.args.optString("date_to"),
                    chronological = config.collection == "visits",
                )
                .catch { failure = NATIVE_LIST_READ_ERROR }
        }
    val displayFlow =
        remember(source) {
            source
                .map<NativeListPage, NativeListPage?> { it }
                .onStart {
                    failure = ""
                    emit(null)
                }
        }
    val loadedPage by displayFlow.collectAsStateWithLifecycle(null)
    val page = loadedPage ?: NativeListPage(emptyList(), 0, offset, 50)
    val listLoading = loadedPage == null && failure.isEmpty()
    NativeSearchList(
        "Suchen",
        query,
        {
            query = it
            offset = 0
        },
        if (listLoading) "Wird geladen …"
        else "${page.total} Treffer" + if (status.isNotBlank()) " · Filter aktiv" else "",
        onFilters = { filtersOpen = !filtersOpen },
        onFocus = { filtersOpen = false },
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!imeVisible) item { NativeContext(controller, controller.route.args) }
            if (filtersOpen)
                item(key = "native-list-filters") {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val states =
                            when (config.kind) {
                                "equipment" -> listOf("ready", "maintenance", "defective")
                                "inquiry" ->
                                    listOf(
                                        "new",
                                        "inspection_planned",
                                        "offered",
                                        "accepted",
                                        "declined",
                                        "closed",
                                    )
                                "visit" ->
                                    listOf(
                                        "planned",
                                        "in_progress",
                                        "completed",
                                        "partial",
                                        "cancelled",
                                    )
                                else -> emptyList()
                            }
                        if (states.isNotEmpty())
                            RevierChoice(
                                "Status",
                                status,
                                listOf("" to "Alle") + states.map { it to revierLabel(it) },
                            ) {
                                status = it
                                offset = 0
                            }
                        Text(
                            "${page.total} Treffer" +
                                if (query.isNotBlank() || status.isNotBlank()) " · Filter aktiv"
                                else ""
                        )
                        if (query.isNotBlank() || status.isNotBlank())
                            NativeTextButton({
                                query = ""
                                status = ""
                                offset = 0
                            }) {
                                Text("Filter zurücksetzen")
                            }
                    }
                }
            if (failure.isNotEmpty())
                item {
                    Column {
                        Text(failure, color = MaterialTheme.colorScheme.error)
                        NativeTextButton({
                            failure = ""
                            retry++
                        }) {
                            Text("Erneut lesen")
                        }
                    }
                }
            if (listLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!listLoading && failure.isEmpty() && page.rows.isEmpty())
                item {
                    Text(
                        when (config.kind) {
                            "property" ->
                                "Objekte sammeln die Pflege, Fotos und Einsätze eines Gartens oder Hauses."
                            "visit" -> "Einsätze halten fest, wann du welches Objekt besuchst."
                            "inquiry" -> "Anfragen halten die Wünsche deiner Kunden fest."
                            "care_plan" ->
                                "Pflegepläne merken die regelmäßigen Arbeiten am Objekt vor."
                            "material" -> "Material hält die verwendeten Produkte und Preise fest."
                            else -> "Geräte halten Ausstattung und Wartung fest."
                        },
                        Modifier.padding(12.dp),
                    )
                }
            items(page.rows, key = { it.id }) { row ->
                NativeSwipeRow(
                    controller,
                    config.kind,
                    row.id,
                    positive =
                        if (
                            config.kind == "visit" && row.status !in setOf("completed", "cancelled")
                        )
                            "Erledigt"
                        else "",
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .semantics {
                                    testTag = UiTags.recordChoice(config.collection, row.id)
                                }
                                .clickable { onSelect(row.id) }
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (row.thumbnailReference.isNotBlank())
                                RevierEquipmentThumbnail(row.thumbnailReference, controller)
                            else NativeInitials(row.title, 30.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle =
                                    listOf(row.subtitle, row.address)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                if (subtitle.isNotBlank())
                                    Text(
                                        nativeDisplayText(subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                            }
                            if (row.status.isNotBlank())
                                NativeStatus(
                                    revierLabel(row.status),
                                    when (row.status) {
                                        "completed" -> NativeStatusTone.Confirmed
                                        "defective",
                                        "maintenance",
                                        "new" -> NativeStatusTone.Due
                                        else -> NativeStatusTone.Neutral
                                    },
                                )
                        }
                    }
                    HorizontalDivider()
                }
            }
            if (offset > 0 || page.hasMore)
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NativeTextButton(
                            { offset = (offset - 50).coerceAtLeast(0) },
                            enabled = offset > 0,
                        ) {
                            Text("Vorherige")
                        }
                        NativeTextButton({ offset += 50 }, enabled = page.hasMore) {
                            Text("Weitere")
                        }
                    }
                }
        }
        if (!imeVisible) NativeActionBar("${config.singular} anlegen", onNew)
    }
}

internal class RevierEdit(
    val key: String,
    val kind: String,
    val original: JSONObject,
    initial: JSONObject,
    request: String,
    pending: JSONObject? = null,
    restored: Boolean = false,
) {
    var values by mutableStateOf(initial.toString())
    var request by mutableStateOf(request)
    var pending by mutableStateOf(pending?.toString())
    var restored by mutableStateOf(restored)
    var dirty by mutableStateOf(false)
    var mediaBusy by mutableStateOf(false)
    var observedHash by mutableStateOf(original.optString("record_hash"))

    fun row() = JSONObject(values)

    fun change(field: String, value: Any) {
        values = row().put(field, value).toString()
        dirty = true
    }

    fun snapshot() =
        JSONObject()
            .put("kind", kind)
            .put("original", original)
            .put("values", row())
            .put("request_id", request)
            .put("record_hash", observedHash)
            .put("dirty", dirty)
            .put("pending", pending?.let(::JSONObject) ?: JSONObject.NULL)
            .put("restore_review_required", restored)

    companion object {
        fun create(key: String, kind: String, row: JSONObject?, context: JSONObject): RevierEdit {
            val values = row?.revierCopy() ?: JSONObject()
            for (field in context.keys()) values.put(field, context.get(field))
            if (kind == "movement") values.put("_resource_fixed", row != null)
            val plan = values.optJSONObject("plan")
            if (kind == "maintenance_plan" && plan != null)
                for (field in listOf("title", "interval_value", "interval_unit")) values.put(
                    field,
                    plan.get(field),
                )
            if (kind == "maintenance_adjust" && plan != null) {
                values.put(
                    "operation",
                    if (plan.optString("next_due_on") <= revierDay()) "shorten" else "bring_forward",
                )
                values
                    .put("interval_value", plan.optInt("interval_value"))
                    .put("interval_unit", plan.optString("interval_unit"))
            }
            if (kind == "inspection" && values.optString("inspection_appointment_id").isNotEmpty())
                values.put("mode", "reschedule")
            if (kind == "direct_acceptance" && values.optString("billing_customer_id").isEmpty())
                values.put("billing_customer_id", values.optString("customer_id"))
            if (kind == "material")
                for ((input, field) in
                    listOf(
                        "purchase_price" to "purchase_price_minor",
                        "billing_price" to "billing_price_minor",
                    )) {
                    if (values.has(field) && !values.isNull(field))
                        values.put(
                            input,
                            BigDecimal(values.get(field).toString())
                                .movePointLeft(2)
                                .toPlainString(),
                        )
                }
            for ((field, value) in
                when (kind) {
                    "care_plan" ->
                        mapOf(
                            "anchor_date" to revierDay(),
                            "interval_days" to 7,
                            "planned_minutes" to 60,
                            "effective_from" to revierDay(),
                        )
                    "visit" ->
                        mapOf("date" to revierDay(), "planned_minutes" to 60, "status" to "planned")
                    "inspection" ->
                        mapOf(
                            "starts_local" to (revierDay() + "T09:00"),
                            "duration_minutes" to 60,
                            "mode" to "first",
                        )
                    "generate" ->
                        mapOf(
                            "from_date" to revierDay(),
                            "to_date" to LocalDate.parse(revierDay()).plusDays(30).toString(),
                        )
                    "equipment" -> mapOf("condition" to "ready")
                    "maintenance_plan" ->
                        mapOf("starts_on" to revierDay(), "interval_unit" to "days")
                    "maintenance_done" -> mapOf("performed_on" to revierDay())
                    "maintenance_adjust" ->
                        mapOf(
                            "operation" to "bring_forward",
                            "next_due_on" to revierDay(),
                            "interval_unit" to "days",
                        )
                    "movement" -> mapOf("movement_kind" to "consume")
                    else -> emptyMap()
                }) if (!values.has(field)) values.put(field, value)
            return RevierEdit(key, kind, row?.revierCopy() ?: JSONObject(), values, revierRequest())
        }

        fun restore(key: String, saved: JSONObject): RevierEdit =
            RevierEdit(
                    key,
                    saved.getString("kind"),
                    saved.getJSONObject("original"),
                    saved.getJSONObject("values"),
                    saved.getString("request_id"),
                    saved.optJSONObject("pending"),
                    saved.optBoolean("restore_review_required") ||
                        saved.optBoolean("_restore_action_review_required"),
                )
                .also {
                    it.dirty = saved.optBoolean("dirty")
                    it.observedHash = saved.optString("record_hash", it.observedHash)
                }
    }
}

@Composable
private fun RevierDetail(
    config: RevierSection,
    id: String,
    refresh: Int,
    controller: NativeAppController,
    repository: NativeDataRepository,
    equipmentTab: Int,
    onEquipmentTab: (Int) -> Unit,
    onBack: () -> Unit,
    onEdit: (String, JSONObject?, JSONObject) -> Unit,
    onRefresh: () -> Unit,
) {
    var data by remember(id) { mutableStateOf<JSONObject?>(null) }
    var failure by remember(id) { mutableStateOf("") }
    var loadFailure by remember(id) { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var editingField by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var maintenancePicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(config.kind, id, refresh) {
        loadFailure = ""
        try {
            data =
                when (config.kind) {
                    "property" ->
                        controller.query("get_property", JSONObject().put("property_id", id))
                    "inquiry" -> controller.query("get_inquiry", JSONObject().put("inquiry_id", id))
                    "visit" -> controller.query("get_visit", JSONObject().put("visit_id", id))
                    else ->
                        withContext(Dispatchers.IO) {
                            val record =
                                repository.detail(config.collection, id)
                                    ?: error("Eintrag nicht gefunden.")
                            JSONObject().put("record", RevierRules.view(record))
                        }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadFailure = error.message.orEmpty()
        }
    }
    val row = data?.optJSONObject("record")
    if (row == null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeTextButton(onBack) { Text("Zurück") }
            if (loadFailure.isEmpty()) CircularProgressIndicator()
            else {
                Text(loadFailure, color = MaterialTheme.colorScheme.error)
                NativeOutlinedButton(onRefresh) { Text("Erneut lesen") }
            }
        }
        return
    }
    if (config.kind == "property") {
        NativePropertyTimeline(controller, data!!, onBack, onEdit)
        return
    }
    val canStartVisit =
        row.optString("status") !in listOf("cancelled", "completed", "superseded") &&
            !row.optBoolean("archived")
    fun startVisit() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val key = "revier:start:$id"
                val saved = controller.draft(key)
                val args =
                    saved?.optJSONObject("args")
                        ?: JSONObject()
                            .put("request_id", revierRequest())
                            .put("customer_id", row.getString("customer_id"))
                            .put("property_id", row.getString("property_id"))
                            .put("visit_id", id)
                            .put("work_date", revierDay())
                            .put("activity", row.optString("title"))
                            .apply {
                                if (row.optString("order_id").isNotEmpty())
                                    put("order_id", row.getString("order_id"))
                            }
                val proof =
                    if (saved != null)
                        controller.query(
                            "inspect_edit_action",
                            JSONObject()
                                .put("original_tool", "start_work")
                                .put("original_args", args),
                        )
                    else null
                if (proof?.optString("state") == "content_removed")
                    error("Dieser Arbeitsstart wurde endgültig entfernt. Keine erneute Anlage.")
                if (saved != null && proof?.optString("state") != "confirmed")
                    error(
                        "Der ursprüngliche Arbeitsstart ist noch nicht bestätigt. Vorgang bleibt erhalten; keine neue Zeiterfassung gestartet."
                    )
                if (saved == null) {
                    val active = controller.query("get_active_work_session")
                    require(active.optString("state") == "IDLE") {
                        "Eine Zeiterfassung ist bereits offen. Diese zuerst auf der Heute-Seite prüfen."
                    }
                    controller.saveDraft(key, JSONObject().put("args", args))
                    controller.flushDrafts()
                    controller.query("start_work", args)
                }
                runCatching { controller.removeDraft(key) }
                if (saved == null) controller.haptic()
                controller.navigate("today")
                controller.message("Arbeitsstart lokal bestätigt. Aktuelle Sitzung prüfen.")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeTextButton(onBack) { Text("Zur Liste") }
            NativeDetailHeading(
                row.optString("name", row.optString("title", config.singular)),
                if (config.kind == "visit")
                    listOfNotNull(
                        NativeDetailAction(
                            "visit-tour",
                            "In Tagestour aufnehmen",
                            run = {
                                controller.navigate(
                                    "tour",
                                    args = JSONObject().put("date", row.optString("date")),
                                )
                            },
                        ),
                        if (row.optString("status") == "partial")
                            NativeDetailAction(
                                "visit-rest",
                                "Offenen Rest planen",
                                run = {
                                    onEdit(
                                        "visit",
                                        null,
                                        JSONObject()
                                            .put("property_id", row.getString("property_id"))
                                            .put("parent_visit_id", id)
                                            .put("title", row.optString("title"))
                                            .put(
                                                "planned_minutes",
                                                row.optInt("remaining_minutes", 60),
                                            ),
                                    )
                                },
                            )
                        else null,
                    )
                else emptyList(),
                onTitleClick = {
                    editingField =
                        (if (config.kind in setOf("equipment", "material")) "name" else "title") to
                            config.singular
                },
            )
            val state = row.optString(if (config.kind == "equipment") "condition" else "status")
            if (state.isNotEmpty())
                Box(
                    Modifier.clickable {
                        editingField =
                            (if (config.kind == "equipment") "condition" else "status") to "Status"
                    }
                ) {
                    NativeStatus(
                        revierLabel(state),
                        if (state in setOf("defective", "maintenance", "new")) NativeStatusTone.Due
                        else if (state == "completed") NativeStatusTone.Confirmed
                        else NativeStatusTone.Neutral,
                    )
                }
            NativeContext(controller, row, heading = true)
            val detailKeys =
                when (config.kind) {
                    "property" ->
                        listOf(
                            "address",
                            "district",
                            "access_notes",
                            "area_decimal",
                            "area_unit",
                            "notes",
                        )
                    "inquiry" ->
                        listOf(
                            "concern",
                            "received_at",
                            "wish_from",
                            "wish_until",
                            "inspection_notes",
                            "notes",
                        )
                    "care_plan" ->
                        listOf(
                            "anchor_date",
                            "interval_days",
                            "planned_minutes",
                            "season_start",
                            "season_end",
                            "notes",
                        )
                    "visit" ->
                        listOf(
                            "date",
                            "window_start",
                            "window_end",
                            "planned_minutes",
                            "remaining_minutes",
                            "notes",
                        )
                    "material" ->
                        listOf("unit", "purchase_price_minor", "billing_price_minor", "notes")
                    "equipment" -> listOf("condition", "maintenance_due", "notes")
                    else -> emptyList()
                }
            NativeGroup("Angaben") {
                for (key in detailKeys) if (row.revierString(key).isNotEmpty())
                    NativeKeyValue(
                        revierDetailLabels[key] ?: nativeLabel(key),
                        when {
                            key.endsWith("_price_minor") ->
                                OfferMoney.decimal(row.getLong(key)).replace('.', ',') + " €"
                            key == "condition" -> revierLabel(row.getString(key))
                            else -> nativeDisplayText(row.revierString(key))
                        },
                    ) {
                        editingField = key to (revierDetailLabels[key] ?: nativeLabel(key))
                    }
            }
            val missing = detailKeys.filter { row.revierString(it).isBlank() }
            if (missing.isNotEmpty())
                Box {
                    NativeTextButton({ adding = true }) { Text("Ergänzen") }
                    DropdownMenu(adding, { adding = false }) {
                        for (field in missing) DropdownMenuItem(
                            text = { Text(revierDetailLabels[field] ?: nativeLabel(field)) },
                            onClick = {
                                adding = false
                                editingField =
                                    field to (revierDetailLabels[field] ?: nativeLabel(field))
                            },
                        )
                    }
                }
            if (config.kind in setOf("visit", "care_plan"))
                NativeKeyValue(
                    "Benötigte Geräte",
                    "${row.optJSONArray("equipment_ids")?.length() ?: 0}",
                ) {
                    editingField = "equipment_ids" to "Benötigte Geräte"
                }
            when (config.kind) {
                "inquiry" -> {
                    if (row.optString("order_id").isEmpty())
                        NativeInfoRow(
                            "Auftrag direkt annehmen",
                            "Annahme und Nachweis prüfen",
                            { onEdit("direct_acceptance", row, JSONObject()) },
                        )
                    NativeInfoRow(
                        if (row.optString("inspection_appointment_id").isEmpty())
                            "Besichtigung planen"
                        else "Besichtigung ändern oder ergänzen",
                        onClick = { onEdit("inspection", row, JSONObject()) },
                    )
                    NativeOutlinedButton({
                        controller.navigate(
                            "offers",
                            "",
                            "new",
                            JSONObject()
                                .put("customer_id", row.getString("customer_id"))
                                .put("property_id", row.optString("property_id"))
                                .put("inquiry_id", id)
                                .put("inquiry_record_hash", row.getString("record_hash"))
                                .put("title", row.optString("title")),
                        )
                    }) {
                        Text("Angebot aus dieser Anfrage")
                    }
                    if (row.optString("offer_id").isNotEmpty())
                        NativeInfoRow(
                            "Zugeordnetes Angebot",
                            onClick = {
                                controller.navigate("offers", row.getString("offer_id"), "detail")
                            },
                        )
                    if (row.optString("order_id").isNotEmpty())
                        NativeInfoRow(
                            "Zugeordneter Auftrag",
                            onClick = {
                                controller.navigate("orders", row.getString("order_id"), "detail")
                            },
                        )
                    for (measurement in
                        row.optJSONArray("measurements")?.revierObjects().orEmpty()) Text(
                        listOf(
                                measurement.optString("name"),
                                nativeNumber(measurement.optString("value_decimal")),
                                measurement.optString("unit"),
                            )
                            .filter { it.isNotEmpty() }
                            .joinToString(" ")
                    )
                }
                "care_plan" ->
                    NativeInfoRow(
                        "Besuche planen",
                        "Zeitraum prüfen und fehlende Besuche erzeugen",
                        { onEdit("generate", row, JSONObject()) },
                    )
                "visit" -> {
                    val warnings =
                        data?.optJSONArray("equipment_warnings")?.revierObjects().orEmpty()
                    if (warnings.isNotEmpty())
                        Text(
                            "Geräte vor dem Einsatz prüfen: " +
                                warnings.joinToString { it.optString("name", "Gerät") }
                        )
                }
                "material" -> {
                    NativeInfoRow(
                        "Verbrauch erfassen",
                        onClick = { onEdit("movement", row, JSONObject().put("material_id", id)) },
                    )
                    RevierHistory(controller, repository, "material_movements", "material_id", id)
                }
                "equipment" -> {
                    TabRow(equipmentTab) {
                        Tab(equipmentTab == 0, { onEquipmentTab(0) }, text = { Text("Übersicht") })
                        Tab(equipmentTab == 1, { onEquipmentTab(1) }, text = { Text("Zubehör") })
                    }
                    if (equipmentTab == 0) {
                        RevierProfilePhoto(row.optJSONObject("profile_photo"), controller)
                        NativeInfoRow(
                            "Gerätefoto",
                            onClick = {
                                onEdit("equipment", row, JSONObject().put("_photo_only", true))
                            },
                        )
                        if (row.optString("condition") == "defective")
                            Text(
                                "Dieses Gerät ist als defekt markiert. Vor dem Einsatz prüfen.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        val currentChain =
                            row.optJSONArray("accessories")?.revierObjects()?.firstOrNull {
                                it.optString("id") == row.optString("current_chain_id")
                            }
                        NativeInfoRow(
                            "Aktuelle Kette",
                            currentChain?.optString("name")?.ifBlank { "Bezeichnung nicht erfasst" }
                                ?: "Keine Kette festgelegt",
                            { onEquipmentTab(1) },
                        )
                        RevierMaintenance(controller, row, refresh, onEdit)
                    } else {
                        Text(
                            "Zubehör für " + row.getString("name"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val accessories = row.optJSONArray("accessories")?.revierObjects().orEmpty()
                        if (accessories.isEmpty())
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Noch kein Zubehör zugeordnet.",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Lege zum Beispiel eine Kette oder einen Akku an. Ein Foto kannst du direkt dazu aufnehmen oder auswählen."
                                    )
                                }
                            }
                        for (accessory in accessories) key(accessory.optString("id")) {
                            var open by rememberSaveable { mutableStateOf(false) }
                            val name =
                                accessory.optString("name").ifBlank {
                                    listOf(
                                            accessory.optString("manufacturer"),
                                            accessory.optString("model"),
                                        )
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ")
                                        .ifBlank { "Zubehör ohne Bezeichnung" }
                                }
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(name, style = MaterialTheme.typography.titleMedium)
                                    val model =
                                        listOf(
                                                accessory.optString("manufacturer"),
                                                accessory.optString("model"),
                                            )
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                    if (model.isNotBlank() && model != name)
                                        Text(
                                            model,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    if (
                                        accessory.optString("id") ==
                                            row.optString("current_chain_id")
                                    )
                                        NativeStatus(
                                            "Aktuell verwendete Kette",
                                            NativeStatusTone.Confirmed,
                                        )
                                    RevierProfilePhoto(
                                        accessory.optJSONObject("photo"),
                                        controller,
                                        "Foto von $name",
                                    )
                                    if (!row.optBoolean("archived"))
                                        NativeOutlinedButton(
                                            {
                                                onEdit(
                                                    "accessories",
                                                    row,
                                                    JSONObject()
                                                        .put(
                                                            "_focus_accessory_id",
                                                            accessory.getString("id"),
                                                        ),
                                                )
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text(
                                                if (accessory.optJSONObject("photo") == null)
                                                    "Foto hinzufügen"
                                                else "Foto ändern"
                                            )
                                        }
                                    val details =
                                        accessoryFields.filterKeys {
                                            accessory.optString(it).isNotBlank()
                                        }
                                    if (details.isNotEmpty()) {
                                        NativeTextButton(
                                            { open = !open },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text(
                                                if (open) "Details schließen"
                                                else "Details anzeigen"
                                            )
                                        }
                                        if (open)
                                            for ((field, label) in details) {
                                                Text(
                                                    label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(accessory.getString(field))
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (row.optString("property_id").isNotEmpty())
                NativeInfoRow(
                    "Zugeordnetes Objekt",
                    onClick = {
                        controller.navigate("properties", row.getString("property_id"), "detail")
                    },
                )
            for (event in data?.optJSONArray("history")?.revierObjects().orEmpty()) Text(
                listOf(
                        nativeDisplayDate(event.optString("at")),
                        event.optString("reason", "Änderung gespeichert"),
                    )
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
            if (loadFailure.isNotEmpty()) {
                Text(loadFailure, color = MaterialTheme.colorScheme.error)
                NativeTextButton(onRefresh) { Text("Erneut lesen") }
            }
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
            NativeDetailDelete(enabled = !busy && !deleting) {
                deleting = true
                scope.launch {
                    try {
                        controller.queueSwipe(config.kind, id, false)
                        onBack()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error.message.orEmpty()
                    } finally {
                        deleting = false
                    }
                }
            }
        }
        when {
            config.kind == "visit" && canStartVisit ->
                NativeActionBar("Arbeit starten", ::startVisit, enabled = !busy)
            config.kind == "equipment" ->
                NativeActionBar(
                    if (equipmentTab == 0) "Wartung erfassen" else "Zubehör hinzufügen",
                    {
                        if (equipmentTab == 0) maintenancePicker = true
                        else onEdit("accessories", row, JSONObject())
                    },
                    enabled = !busy,
                )
            config.kind == "inquiry" && row.optString("order_id").isBlank() ->
                NativeActionBar(
                    "Annehmen",
                    { onEdit("direct_acceptance", row, JSONObject()) },
                    enabled = !busy,
                )
            config.kind == "care_plan" ->
                NativeActionBar(
                    "Einsätze planen",
                    { onEdit("generate", row, JSONObject()) },
                    enabled = !busy,
                )
            config.kind == "material" ->
                NativeActionBar(
                    "Verbrauch erfassen",
                    { onEdit("movement", row, JSONObject().put("material_id", id)) },
                    enabled = !busy,
                )
        }
    }
    editingField?.let { (field, label) ->
        NativeRevierValueSheet(controller, config.kind, row, field, label, onRefresh) {
            editingField = null
        }
    }
    if (maintenancePicker)
        AlertDialog(
            onDismissRequest = { maintenancePicker = false },
            title = { Text("Wartungsaufgabe auswählen") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val plans = EquipmentMaintenanceRules.plans(row).revierObjects()
                    if (plans.isEmpty())
                        Text("Für dieses Gerät ist noch kein Wartungsplan eingerichtet.")
                    for (plan in plans) NativeInfoRow(
                        plan.optString("title"),
                        "Fällig: " + nativeDisplayDate(plan.optString("next_due_on")),
                        {
                            maintenancePicker = false
                            onEdit(
                                "maintenance_done",
                                row,
                                JSONObject()
                                    .put("equipment_id", id)
                                    .put("plan_id", plan.getString("id"))
                                    .put("plan", plan),
                            )
                        },
                    )
                    NativeInfoRow(
                        "Wartung einrichten",
                        "Aufgabe und eigenes Intervall festlegen",
                        {
                            maintenancePicker = false
                            onEdit("maintenance_plan", row, JSONObject().put("equipment_id", id))
                        },
                    )
                }
            },
            confirmButton = { NativeTextButton({ maintenancePicker = false }) { Text("Zurück") } },
        )
}

private val revierDetailLabels =
    linkedMapOf(
        "address" to "Einsatzadresse",
        "district" to "Gebiet",
        "access_notes" to "Zugangshinweise",
        "area_decimal" to "Fläche",
        "area_unit" to "Flächeneinheit",
        "concern" to "Anliegen",
        "received_at" to "Eingang",
        "wish_from" to "Wunsch ab",
        "wish_until" to "Wunsch bis",
        "status" to "Status",
        "inspection_notes" to "Besichtigungsnotizen",
        "date" to "Datum",
        "window_start" to "Zeitfenster ab",
        "window_end" to "Zeitfenster bis",
        "planned_minutes" to "Geplante Minuten",
        "remaining_minutes" to "Offener Rest in Minuten",
        "anchor_date" to "Pflegebeginn",
        "interval_days" to "Turnus in Tagen",
        "season_start" to "Saisonbeginn",
        "season_end" to "Saisonende",
        "purchase_price_minor" to "Einstandspreis",
        "billing_price_minor" to "Abrechnungspreis",
        "unit" to "Einheit",
        "condition" to "Gerätezustand",
        "maintenance_due" to "Zusätzlich vorgemerkte Wartung",
        "notes" to "Notizen",
        "status_reason" to "Begründung",
    )

/**
 * Projected equipment rows resolve only their bounded thumbnail; they never load an original or
 * query photo metadata.
 */
@Composable
private fun RevierEquipmentThumbnail(reference: String, controller: NativeAppController) {
    val source by
        produceState<File?>(null, reference, controller) {
            value = null
            value =
                try {
                    withContext(Dispatchers.IO) {
                        controller.store.photoAttachmentStore().verifiedThumbnailFile(reference)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
        }
    if (source != null)
        AsyncImage(
            source,
            "Gerätefoto",
            imageLoader = controller.imageLoader,
            modifier = Modifier.size(32.dp),
        )
    else NativeInitials("Gerät", 30.dp)
}

@Composable
private fun RevierProfilePhoto(
    photo: JSONObject?,
    controller: NativeAppController,
    label: String = "Gerätefoto",
) {
    if (photo == null) return
    var decoded by remember(photo.toString()) { mutableStateOf(false) }
    var decodeFailed by remember(photo.toString()) { mutableStateOf(false) }
    var loading by remember(photo.toString()) { mutableStateOf(true) }
    var enlarged by rememberSaveable(photo.toString()) { mutableStateOf(false) }
    val model by
        produceState<File?>(null, photo.toString()) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching { controller.store.photoAttachmentStore().verifiedFile(photo) }
                        .getOrNull()
                }
            loading = false
        }
    if (model != null) {
        AsyncImage(
            model,
            if (decoded) "$label · zum Vergrößern antippen" else "$label wird geladen",
            imageLoader = controller.imageLoader,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp).clickable(
                    role = Role.Button,
                    onClickLabel = "Foto vergrößern",
                ) {
                    enlarged = true
                },
            onSuccess = {
                decoded = true
                decodeFailed = false
            },
            onError = { decodeFailed = true },
        )
        if (decodeFailed)
            Text(
                "Foto nicht lesbar. Bitte die gespeicherte Aufnahme prüfen.",
                color = MaterialTheme.colorScheme.error,
            )
    } else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    else Text("Foto derzeit nicht lesbar. Gespeicherter Eintrag bleibt erhalten.")
    if (enlarged && model != null)
        Dialog(
            onDismissRequest = { enlarged = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    AsyncImage(
                        model,
                        label,
                        imageLoader = controller.imageLoader,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    NativeOutlinedButton(
                        { enlarged = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Schließen")
                    }
                }
            }
        }
}

@Composable
private fun RevierHistory(
    controller: NativeAppController,
    repository: NativeDataRepository,
    collection: String,
    relation: String,
    id: String,
    extra: @Composable (JSONObject) -> Unit = {},
) {
    var offset by remember(id) { mutableIntStateOf(0) }
    var failure by remember(collection, relation, id) { mutableStateOf("") }
    var retry by remember(collection, relation, id) { mutableIntStateOf(0) }
    val source =
        remember(collection, relation, id, offset, retry) {
            repository.watchHistory(collection, relation, id, offset, 20).catch {
                failure = NATIVE_LIST_READ_ERROR
            }
        }
    val records by source.collectAsStateWithLifecycle(emptyList())
    if (failure.isNotEmpty()) {
        Text(failure, color = MaterialTheme.colorScheme.error)
        NativeTextButton({
            failure = ""
            retry++
        }) {
            Text("Verlauf erneut lesen")
        }
    }
    val hasMore = records.size == 20
    if (records.isNotEmpty()) Text("Verbrauch", style = MaterialTheme.typography.titleMedium)
    for (record in records.filter { it.optString("movement_kind") == "consume" }) {
        NativeInfoRow(
            listOf(
                    revierLabel(record.optString("movement_kind")),
                    nativeNumber(record.optString("quantity_decimal")),
                )
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "Verbrauch" },
            listOf(
                    nativeDisplayDate(record.optString("created_at")),
                    record.optString("reason", record.optString("notes")),
                )
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )
        extra(record)
    }
    if (offset > 0 || hasMore)
        Row {
            NativeTextButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0) {
                Text("Vorherige")
            }
            NativeTextButton({ offset += 20 }, enabled = hasMore) { Text("Weitere") }
        }
}

@Composable
private fun RevierMaintenance(
    controller: NativeAppController,
    row: JSONObject,
    refresh: Int,
    onEdit: (String, JSONObject?, JSONObject) -> Unit,
) {
    var result by remember(row.optString("id"), refresh) { mutableStateOf<JSONObject?>(null) }
    var offset by remember(row.optString("id")) { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    LaunchedEffect(row.toString(), offset, refresh) {
        try {
            result =
                controller.query(
                    "get_equipment_maintenance",
                    JSONObject()
                        .put("equipment_id", row.getString("id"))
                        .put("offset", offset)
                        .put("limit", 20),
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message.orEmpty()
        }
    }
    Text("Wartung", style = MaterialTheme.typography.titleLarge)
    val active = !row.optBoolean("archived") && row.optBoolean("active", true)
    if (result == null && failure.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
    val plans = result?.optJSONArray("plans")?.revierObjects().orEmpty()
    if (result != null && plans.isEmpty()) Text("Noch kein Wartungsplan eingerichtet.")
    for (plan in plans) {
        val due = plan.optString("next_due_on")
        val context =
            JSONObject()
                .put("equipment_id", row.getString("id"))
                .put("plan_id", plan.getString("id"))
                .put("plan", plan)
        NativeInfoRow(
            plan.optString("title"),
            (when {
                due.isBlank() -> "Termin nicht erfasst"
                due < revierDay() -> "Überfällig seit "
                due == revierDay() -> "Heute fällig · "
                else -> "Nächste Wartung: "
            }) + if (due.isBlank()) "" else nativeDisplayDate(due),
            if (active) ({ onEdit("maintenance_done", row, context) }) else null,
        )
        if (due.isNotBlank() && due <= revierDay())
            NativeStatus("Wartung fällig", NativeStatusTone.Attention)
        if (plan.optString("last_completed_on").isNotBlank())
            NativeInfoRow(
                "Zuletzt erledigt",
                nativeDisplayDate(plan.optString("last_completed_on")),
            )
        var detailsOpen by remember(plan.optString("id")) { mutableStateOf(false) }
        NativeTextButton({ detailsOpen = !detailsOpen }) {
            Text(if (detailsOpen) "Intervall schließen" else "Intervall ansehen und ändern")
        }
        if (detailsOpen) {
            Text(
                "Alle ${plan.optInt("interval_value")} ${revierLabel(plan.optString("interval_unit"))}"
            )
            if (plan.optString("last_change_reason").isNotBlank())
                Text(plan.optString("last_change_reason"))
            if (active) {
                NativeInfoRow(
                    "Intervall ändern",
                    onClick = { onEdit("maintenance_plan", row, context) },
                )
                NativeInfoRow(
                    "Früher warten",
                    onClick = { onEdit("maintenance_adjust", row, context) },
                )
            }
        }
    }
    if (active)
        NativeInfoRow(
            "Wartung einrichten",
            "Weitere Aufgabe und eigenes Intervall",
            {
                onEdit(
                    "maintenance_plan",
                    row,
                    JSONObject().put("equipment_id", row.getString("id")),
                )
            },
        )
    Text("Erledigte Wartungen", style = MaterialTheme.typography.titleMedium)
    for (event in result?.optJSONArray("items")?.revierObjects().orEmpty()) {
        Text(
            listOf(
                    event.optString("title"),
                    event.optString("performed_on"),
                    event.optString("reason"),
                    event.optString("notes"),
                )
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        )
    }
    Row {
        NativeTextButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0) {
            Text("Vorherige")
        }
        NativeTextButton(
            { offset += 20 },
            enabled =
                offset + (result?.optJSONArray("items")?.length() ?: 0) <
                    (result?.optInt("total") ?: 0),
        ) {
            Text("Weitere Wartungen")
        }
    }
    if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevierEditor(
    controller: NativeAppController,
    edit: RevierEdit,
    repository: NativeDataRepository,
    onClose: () -> Unit,
    onSaved: (JSONObject) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(edit.key) { mutableStateOf(false) }
    var failure by remember(edit.key) { mutableStateOf("") }
    var conflict by remember(edit.key) { mutableStateOf(false) }
    var comparison by remember(edit.key) { mutableStateOf<JSONObject?>(null) }
    var reviewed by remember(edit.key) { mutableStateOf(false) }
    var confirmDiscard by remember(edit.key) { mutableStateOf(false) }
    var confirmedResult by remember(edit.key) { mutableStateOf<JSONObject?>(null) }
    val values = edit.row()
    val locked = busy || edit.mediaBusy || edit.pending != null || edit.restored
    val equipmentPhotoEditor = edit.kind == "equipment" || edit.kind == "accessories"
    fun update(field: String, value: Any) {
        if (locked) return
        edit.change(field, value)
        controller.saveDraft(edit.key, edit.snapshot())
    }
    fun leave() {
        confirmedResult?.let {
            onSaved(it)
            return
        }
        if (busy || edit.mediaBusy) return
        busy = true
        scope.launch {
            try {
                if (edit.dirty || edit.pending != null) {
                    controller.saveDraft(edit.key, edit.snapshot())
                    controller.flushDrafts()
                }
                onClose()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }
    BackHandler { leave() }
    LaunchedEffect(edit.key) {
        if (
            edit.kind == "inspection" && values.optString("inspection_appointment_id").isNotEmpty()
        ) {
            try {
                val appointment =
                    repository.detail("appointments", values.getString("inspection_appointment_id"))
                if (appointment != null && edit.pending == null) {
                    edit.values =
                        edit
                            .row()
                            .put(
                                "appointment_hash",
                                withContext(Dispatchers.IO) { AppointmentRules.hash(appointment) },
                            )
                            .toString()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            }
        }
    }
    LaunchedEffect(edit.key, values.optString("material_id"), values.optString("equipment_id")) {
        if (edit.kind == "movement" && edit.pending == null && !edit.restored) {
            val field = "material_id"
            val sourceId = values.optString(field, edit.original.optString("id"))
            if (sourceId.isNotEmpty())
                try {
                    val source = repository.detail("materials", sourceId)
                    if (source != null) {
                        edit.observedHash = withContext(Dispatchers.IO) { RevierRules.hash(source) }
                        for (key in source.keys()) edit.original.put(key, source.get(key))
                        edit.values =
                            edit
                                .row()
                                .put("material_name", source.optString("name"))
                                .put("unit", source.optString("unit"))
                                .toString()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = error.message.orEmpty()
                }
        }
    }
    var inspectionAttempt by remember { mutableIntStateOf(0) }
    val retainedOperation = edit.pending?.let(::JSONObject)
    val inspection =
        rememberNativePendingInspection(
            controller,
            retainedOperation?.optString("tool").orEmpty(),
            retainedOperation?.optJSONObject("args"),
            !busy && !edit.mediaBusy,
            inspectionAttempt,
        ) { proof ->
            val result = proof.optJSONObject("result") ?: proof
            revierDeleteEditPhotos(controller, edit.row())
            controller.removeDraft(edit.key)
            edit.pending = null
            edit.dirty = false
            controller.message("Gespeichert.")
            onSaved(result)
        }
    confirmedResult?.let { result ->
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (edit.kind == "movement") "Material gebucht" else "Wartung gespeichert",
                    style = MaterialTheme.typography.headlineSmall,
                )
                NativeStatus(
                    if (
                        edit.kind == "movement" &&
                            values.optString("movement_kind", "consume") == "consume"
                    )
                        "Verbrauch gebucht"
                    else "Gespeichert",
                    NativeStatusTone.Confirmed,
                )
                NativeContext(controller, values)
                if (edit.kind == "movement") {
                    val material = result.optJSONObject("material")
                    val movement = result.optJSONObject("movement")
                    NativeInfoRow(
                        material?.optString("name").orEmpty().ifBlank {
                            values.optString("material_name")
                        },
                        listOf(
                                nativeNumber(
                                    movement?.optString("quantity_decimal").orEmpty().ifBlank {
                                        values.optString("quantity_decimal")
                                    }
                                ),
                                material?.optString("unit").orEmpty().ifBlank {
                                    values.optString("unit")
                                },
                                revierLabel(
                                    movement?.optString("movement_kind").orEmpty().ifBlank {
                                        values.optString("movement_kind", "consume")
                                    }
                                ),
                            )
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                    )
                } else {
                    val event = result.optJSONObject("maintenance")
                    NativeInfoRow(
                        edit.original.optString("name"),
                        event?.optString("title").orEmpty().ifBlank {
                            values.optJSONObject("plan")?.optString("title").orEmpty()
                        },
                    )
                    NativeInfoRow(
                        "Erledigt am",
                        nativeDisplayDate(
                            event?.optString("performed_on").orEmpty().ifBlank {
                                values.optString("performed_on")
                            }
                        ),
                    )
                    val note =
                        event?.optString("notes").orEmpty().ifBlank { values.optString("notes") }
                    if (note.isNotBlank()) NativeInfoRow("Notiz", note)
                }
            }
            NativeActionBar(
                if (values.optString("session_id").isNotBlank()) "Zur Arbeit"
                else if (edit.kind == "movement") "Zum Material" else "Zum Gerät",
                { onSaved(result) },
            )
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (edit.kind == "movement") "Verbrauch erfassen" else revierEditorTitle(edit.kind),
                style = MaterialTheme.typography.headlineSmall,
            )
            NativeStatus(
                when {
                    edit.pending != null -> "Speichern nicht bestätigt"
                    edit.restored -> "Entwurf prüfen"
                    else -> "Noch nicht gespeichert"
                },
                if (edit.pending != null || edit.restored) NativeStatusTone.Attention
                else NativeStatusTone.Neutral,
            )
            NativeContext(controller, values)
            if (edit.pending != null) NativeInspectionBanner(inspection) { inspectionAttempt++ }
            if (edit.restored) NativeRestoreBanner()
            RevierFields(edit, values, controller, !locked, ::update)
            if (edit.dirty && edit.pending == null)
                NativeTextButton({ confirmDiscard = true }, enabled = !busy && !edit.mediaBusy) {
                    Text("Entwurf verwerfen")
                }
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
            if ((conflict || edit.restored) && edit.pending == null)
                NativeOutlinedButton(
                    {
                        scope.launch {
                            try {
                                val collection =
                                    when {
                                        edit.kind.startsWith("maintenance_") ||
                                            edit.kind == "accessories" -> "equipment"
                                        edit.kind in listOf("inspection", "direct_acceptance") ->
                                            "inquiries"
                                        edit.kind == "generate" -> "care_plans"
                                        edit.kind == "movement" -> "materials"
                                        else -> revierCollection(edit.kind)
                                    }
                                comparison =
                                    if (edit.original.optString("id").isEmpty())
                                        JSONObject().put("_new_draft", true)
                                    else
                                        withContext(Dispatchers.IO) {
                                            repository
                                                .detail(collection, edit.original.getString("id"))
                                                ?.let { RevierRules.view(it) }
                                        }
                                reviewed = false
                                if (comparison == null)
                                    failure =
                                        "Der ursprüngliche Eintrag ist nicht mehr vorhanden. Aktuelle Liste prüfen."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error.message.orEmpty()
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Änderungen vergleichen")
                }
        }
        if (edit.pending != null)
            NativeActionBar("Schließen", ::leave, enabled = !busy && !edit.mediaBusy)
        else
            NativeActionBar(
                primaryLabel =
                    when {
                        edit.mediaBusy -> "Foto wird geladen …"
                        busy && equipmentPhotoEditor -> "Bitte warten …"
                        edit.pending != null -> "Ergebnis prüfen"
                        edit.kind == "movement" -> "Verbrauch speichern"
                        edit.kind == "maintenance_done" -> "Wartung speichern"
                        else -> "Speichern"
                    },
                onPrimary = {
                    scope.launch {
                        busy = true
                        failure = ""
                        if (equipmentPhotoEditor) controller.message("")
                        val checkingPending = edit.pending != null
                        try {
                            val frozen = edit.pending?.let(::JSONObject)
                            var proof: JSONObject? = null
                            if (frozen != null) {
                                val inspection =
                                    controller.query(
                                        "inspect_edit_action",
                                        JSONObject()
                                            .put("original_tool", frozen.getString("tool"))
                                            .put("original_args", frozen.getJSONObject("args")),
                                    )
                                when (inspection.optString("state")) {
                                    "confirmed" ->
                                        proof = inspection.optJSONObject("result") ?: JSONObject()
                                    "content_removed" ->
                                        error(
                                            "Dieser Vorgang wurde endgültig gelöscht. Keine erneute Anlage."
                                        )
                                    else ->
                                        error(
                                            "Das Ergebnis ist noch offen oder der Eintrag wurde verändert. Eingaben und ursprünglicher Vorgang bleiben erhalten. Es wurde nichts erneut gespeichert."
                                        )
                                }
                            }
                            if (edit.restored && proof == null)
                                error(
                                    "Aktuellen Eintrag und Entwurf zuerst vergleichen. Es wurde nichts gespeichert."
                                )
                            val result =
                                if (proof != null) proof
                                else {
                                    var argsValues = edit.row()
                                    if (
                                        edit.kind == "equipment" &&
                                            argsValues
                                                .optString("selected_photo_uri")
                                                .isNotEmpty() &&
                                            frozen == null
                                    ) {
                                        val upload =
                                            controller.stageEquipmentPhoto(
                                                Uri.parse(
                                                    argsValues.getString("selected_photo_uri")
                                                ),
                                                edit.request +
                                                    "_photo_" +
                                                    PixelMcpWriteTools.hash(
                                                            argsValues.getString(
                                                                "selected_photo_uri"
                                                            )
                                                        )
                                                        .take(16),
                                            )
                                        edit.values =
                                            argsValues
                                                .put("profile_photo_upload_id", upload)
                                                .toString()
                                        controller.saveDraft(edit.key, edit.snapshot())
                                        controller.flushDrafts()
                                        argsValues = edit.row()
                                    }
                                    if (edit.kind == "accessories" && frozen == null) {
                                        val accessories =
                                            argsValues.optJSONArray("accessories") ?: JSONArray()
                                        for (index in 0 until accessories.length()) {
                                            val item = accessories.getJSONObject(index)
                                            if (item.optString("selected_photo_uri").isNotEmpty())
                                                item.put(
                                                    "photo_upload_id",
                                                    controller.stageEquipmentPhoto(
                                                        Uri.parse(
                                                            item.getString("selected_photo_uri")
                                                        ),
                                                        edit.request +
                                                            "_" +
                                                            PixelMcpWriteTools.hash(
                                                                    item.getString("id") +
                                                                        item.getString(
                                                                            "selected_photo_uri"
                                                                        )
                                                                )
                                                                .take(24),
                                                    ),
                                                )
                                        }
                                        edit.values =
                                            argsValues.put("accessories", accessories).toString()
                                        controller.saveDraft(edit.key, edit.snapshot())
                                        controller.flushDrafts()
                                    }
                                    val operation = frozen ?: revierArguments(edit, argsValues)
                                    edit.pending = operation.toString()
                                    edit.dirty = true
                                    controller.saveDraft(edit.key, edit.snapshot())
                                    controller.flushDrafts()
                                    controller.query(
                                        operation.getString("tool"),
                                        operation.getJSONObject("args"),
                                    )
                                }
                            // A confirmed business save remains confirmed if draft cleanup or
                            // rendering
                            // fails.
                            edit.pending = null
                            edit.dirty = false
                            val cleanup = runCatching {
                                revierDeleteEditPhotos(controller, edit.row())
                                controller.removeDraft(edit.key)
                            }
                            controller.message(
                                if (cleanup.isSuccess) "Lokal gespeichert."
                                else "Gespeichert. Entwurfsbereinigung steht noch aus."
                            )
                            if (edit.kind in listOf("movement", "maintenance_done"))
                                confirmedResult = result
                            else onSaved(result)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            if (
                                !checkingPending &&
                                    error is NativeCallFailure &&
                                    error.detail.optString("commit_state") == "not_committed"
                            ) {
                                edit.pending = null
                                conflict =
                                    error.detail.optString("code").contains("CONFLICT") ||
                                        error.detail.optString("code").contains("STALE")
                            }
                            failure = error.message.orEmpty()
                            if (edit.dirty || edit.pending != null)
                                controller.saveDraft(edit.key, edit.snapshot())
                        } finally {
                            busy = false
                        }
                    }
                },
                secondaryLabel = "Zurück",
                onSecondary = { leave() },
                enabled = !busy && !edit.mediaBusy,
                secondaryEnabled = !busy && !edit.mediaBusy,
            )
    }
    if (confirmDiscard)
        LaunchedEffect(Unit) {
            busy = true
            try {
                revierDeleteEditPhotos(controller, edit.row())
                controller.removeDraft(edit.key)
                onClose()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            } finally {
                busy = false
                confirmDiscard = false
            }
        }
    val fresh = comparison
    if (fresh != null)
        AlertDialog(
            onDismissRequest = { comparison = null },
            title = { Text("Geänderten Stand prüfen") },
            text = {
                Column(
                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val newDraft = fresh.optBoolean("_new_draft")
                    Text(
                        if (newDraft)
                            "Dieser Entwurf hat noch keinen bestätigten Geschäftssatz. Angaben und aktuelle Zuordnungen prüfen; sie werden beim Speichern erneut geprüft."
                        else
                            "Aktuell gespeicherte Angaben und eigener Entwurf. Änderungen werden erst nach erneuter ausdrücklicher Speicherung übernommen."
                    )
                    for ((key, label) in revierDetailLabels + revierCompareLabels) {
                        val own = values.revierString(key)
                        val current = fresh.revierString(key)
                        if (
                            (newDraft && own.isNotEmpty()) ||
                                (own != current && (own.isNotEmpty() || current.isNotEmpty()))
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            if (!newDraft) {
                                Text("Gespeichert:", style = MaterialTheme.typography.labelMedium)
                                RevierComparisonValue(key, fresh.opt(key), repository)
                            }
                            Text("Entwurf:", style = MaterialTheme.typography.labelMedium)
                            RevierComparisonValue(key, values.opt(key), repository)
                        }
                    }
                    val currentPlan =
                        fresh.optJSONArray("maintenance_plans")?.revierObjects()?.firstOrNull {
                            it.optString("id") == values.optString("plan_id")
                        }
                    if (currentPlan != null)
                        Text(
                            "Aktuelles Intervall: ${currentPlan.optInt("interval_value")} ${revierLabel(currentPlan.optString("interval_unit"))} · Fällig: ${nativeDisplayDate(currentPlan.optString("next_due_on"))}"
                        )
                    Row {
                        Checkbox(reviewed, { reviewed = it })
                        Text(
                            "Aktuellen Stand und eigene Angaben geprüft",
                            Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
            dismissButton = {
                NativeTextButton({ comparison = null }) { Text("Zurück zum Entwurf") }
            },
            confirmButton = {
                NativeButton(
                    {
                        busy = true
                        scope.launch {
                            try {
                                check(edit.pending == null) {
                                    "Ergebnis der ursprünglichen Speicherung zuerst prüfen."
                                }
                                val nextValues = edit.row()
                                val plan =
                                    fresh
                                        .optJSONArray("maintenance_plans")
                                        ?.revierObjects()
                                        ?.firstOrNull {
                                            it.optString("id") == nextValues.optString("plan_id")
                                        }
                                if (plan != null) nextValues.put("plan", plan)
                                if (
                                    edit.kind == "inspection" &&
                                        fresh.optString("inspection_appointment_id").isNotEmpty()
                                ) {
                                    val appointment =
                                        repository.detail(
                                            "appointments",
                                            fresh.getString("inspection_appointment_id"),
                                        ) ?: error("Aktueller Besichtigungstermin nicht lesbar.")
                                    nextValues
                                        .put(
                                            "inspection_appointment_id",
                                            fresh.getString("inspection_appointment_id"),
                                        )
                                        .put(
                                            "appointment_hash",
                                            withContext(Dispatchers.IO) {
                                                AppointmentRules.hash(appointment)
                                            },
                                        )
                                }
                                val nextOriginal =
                                    (if (fresh.optBoolean("_new_draft")) edit.original else fresh)
                                        .revierCopy()
                                val next =
                                    edit
                                        .snapshot()
                                        .put("record_hash", fresh.optString("record_hash"))
                                        .put("original", nextOriginal)
                                        .put("values", nextValues)
                                        .put("request_id", revierRequest())
                                        .put("dirty", true)
                                        .put("restore_review_required", false)
                                        .put("pending", JSONObject.NULL)
                                controller.adoptDraft(edit.key, next)
                                for (key in edit.original.keys().asSequence().toList()) edit
                                    .original
                                    .remove(key)
                                for (key in nextOriginal.keys()) edit.original.put(
                                    key,
                                    nextOriginal.get(key),
                                )
                                edit.values = nextValues.toString()
                                edit.observedHash = next.optString("record_hash")
                                edit.pending = null
                                edit.restored = false
                                edit.request = next.getString("request_id")
                                edit.dirty = true
                                comparison = null
                                conflict = false
                                failure =
                                    "Vergleich bestätigt. Angaben prüfen und erneut ausdrücklich speichern."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error.message.orEmpty()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = reviewed && !busy && edit.pending == null,
                ) {
                    Text("Weiterbearbeiten")
                }
            },
        )
}

private fun revierEditorTitle(kind: String): String =
    when (kind) {
        "property" -> "Objekt"
        "inquiry" -> "Anfrage"
        "care_plan" -> "Pflegeplan"
        "visit" -> "Einsatz"
        "accessories" -> "Zubehör der Maschine"
        "material" -> "Material"
        "equipment" -> "Gerät"
        "inspection" -> "Besichtigung planen"
        "direct_acceptance" -> "Direkte Auftragsannahme"
        "generate" -> "Einsätze planen"
        "movement" -> "Verbrauch"
        "maintenance_plan" -> "Wartungsintervall"
        "maintenance_done" -> "Wartung erfassen"
        "maintenance_adjust" -> "Früher warten"
        else -> "Eintrag"
    }

@Composable
private fun RevierFields(
    edit: RevierEdit,
    value: JSONObject,
    controller: NativeAppController,
    enabled: Boolean,
    update: (String, Any) -> Unit,
) {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                controller.message(error.message.orEmpty())
            } finally {
                edit.mediaBusy = false
            }
        }
    }
    @Composable
    fun field(key: String, label: String, required: Boolean = false, multiline: Boolean = false) {
        val fieldLabel = label.substringBefore(" · ").substringBefore(" (JJJJ")
        when {
            key in
                setOf(
                    "wish_from",
                    "wish_until",
                    "anchor_date",
                    "effective_from",
                    "date",
                    "from_date",
                    "to_date",
                    "maintenance_due",
                    "starts_on",
                    "next_due_on",
                    "performed_on",
                ) -> NativeDateField(fieldLabel, text(key), { update(key, it) }, required, enabled)
            key in setOf("starts_local", "accepted_local") ->
                NativeDateTimeField(fieldLabel, text(key), { update(key, it) }, required, enabled)
            key in setOf("window_start", "window_end") ->
                NativeTimeField(fieldLabel, text(key), { update(key, it) }, enabled)
            enabled ->
                NativeField(
                    label,
                    text(key),
                    { update(key, it) },
                    required,
                    multiline,
                    fieldTag = UiTags.detailField(key),
                )
        }
        if (
            !enabled &&
                key !in
                    setOf(
                        "wish_from",
                        "wish_until",
                        "anchor_date",
                        "effective_from",
                        "date",
                        "from_date",
                        "to_date",
                        "maintenance_due",
                        "starts_on",
                        "next_due_on",
                        "performed_on",
                        "starts_local",
                        "accepted_local",
                        "window_start",
                        "window_end",
                    ) &&
                text(key).isNotEmpty()
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(text(key))
        }
    }
    @Composable
    fun relation(key: String, collection: String, label: String) {
        NativeRecordPicker(
            controller,
            collection,
            label,
            text(key),
            customerId =
                if (collection in listOf("orders", "properties", "visits", "photos"))
                    text("customer_id")
                else "",
        ) {
            if (enabled) update(key, it)
        }
    }
    @Composable
    fun choice(key: String, label: String, options: List<String>, fallback: String = "") {
        RevierChoice(label, text(key, fallback), options.map { it to revierLabel(it) }, enabled) {
            update(key, it)
        }
    }
    when (edit.kind) {
        "property" -> {
            if (!editing) relation("customer_id", "customers", "Kunde")
            field("name", "Objektbezeichnung", true)
            field("address", "Einsatzadresse", multiline = true)
            field("district", "Gebiet")
            field("area_decimal", "Fläche")
            field("area_unit", "Einheit der Fläche")
            field("access_notes", "Zugangshinweise", multiline = true)
            field("notes", "Notizen", multiline = true)
        }
        "inquiry" -> {
            if (!editing) relation("customer_id", "customers", "Kunde")
            relation("property_id", "properties", "Objekt, falls bekannt")
            field("title", "Bezeichnung", true)
            field("concern", "Anliegen", multiline = true)
            field("wish_from", "Wunsch ab · JJJJ-MM-TT")
            field("wish_until", "Wunsch bis · JJJJ-MM-TT")
            if (editing) {
                choice(
                    "status",
                    "Status",
                    listOf(
                        "new",
                        "inspection_planned",
                        "offered",
                        "accepted",
                        "declined",
                        "closed",
                    ),
                    "new",
                )
                relation("offer_id", "offers", "Vorhandenes Angebot")
                relation("order_id", "orders", "Vorhandener Auftrag")
            }
            if (text("inspection_appointment_id").isNotEmpty()) {
                field("inspection_notes", "Besichtigungsnotizen", multiline = true)
                RevierMeasurements(value.optJSONArray("measurements") ?: JSONArray(), enabled) {
                    update("measurements", it)
                }
                RevierRelations(
                    controller,
                    "photos",
                    "Dokumentationsfotos",
                    "photo_ids",
                    value,
                    enabled,
                    update,
                )
            }
        }
        "care_plan" -> {
            if (!editing) relation("property_id", "properties", "Objekt")
            field("title", "Pflegeleistung", true)
            field("anchor_date", "Beginn · JJJJ-MM-TT", true)
            field("interval_days", "Turnus in Tagen", true)
            field("planned_minutes", "Geplante Dauer in Minuten", true)
            field("season_start", "Saisonbeginn · MM-TT")
            field("season_end", "Saisonende · MM-TT")
            field("notes", "Hinweise", multiline = true)
            RevierMaterialNeeds(
                controller,
                value.optJSONArray("material_needs") ?: JSONArray(),
                enabled,
            ) {
                update("material_needs", it)
            }
            RevierRelations(
                controller,
                "equipment",
                "Benötigte Geräte",
                "equipment_ids",
                value,
                enabled,
                update,
            )
            if (editing) {
                Text(
                    "Vergangene Besuche bleiben erhalten. Die Änderung gilt für zukünftige Termine."
                )
                field("effective_from", "Änderung gilt ab · JJJJ-MM-TT", true)
            }
        }
        "visit" -> {
            if (!editing) {
                relation("property_id", "properties", "Objekt")
                relation("order_id", "orders", "Auftrag (optional)")
            }
            field("title", "Tätigkeit / Einsatz", true)
            field("date", "Datum · JJJJ-MM-TT", true)
            field("planned_minutes", "Geplante Minuten", true)
            field("window_start", "Zeitfenster ab · HH:mm")
            field("window_end", "Zeitfenster bis · HH:mm")
            field("notes", "Hinweise", multiline = true)
            RevierRelations(
                controller,
                "equipment",
                "Benötigte Geräte",
                "equipment_ids",
                value,
                enabled,
                update,
            )
            if (editing) {
                choice(
                    "status",
                    "Ergebnis / Status",
                    listOf("planned", "in_progress", "completed", "partial", "cancelled"),
                    "planned",
                )
                field("reason", "Grund für Änderung / Absage", true, true)
                field("remaining_minutes", "Offener Rest in Minuten")
            }
        }
        "direct_acceptance" -> {
            field("accepted_local", "Tatsächlicher Annahmezeitpunkt · JJJJ-MM-TTTHH:mm", true)
            field("evidence", "Annahmenachweis", true, true)
            relation("billing_customer_id", "customers", "Rechnungsempfänger")
            Row {
                Checkbox(
                    value.optBoolean("confirmed"),
                    { if (enabled) update("confirmed", it) },
                    enabled = enabled,
                )
                Text(
                    "Direkte Auftragsannahme ausdrücklich bestätigen",
                    Modifier.padding(top = 12.dp),
                )
            }
        }
        "inspection" -> {
            if (text("inspection_appointment_id").isNotEmpty())
                choice(
                    "mode",
                    "Gewünschter Vorgang",
                    listOf("reschedule", "additional", "reopen"),
                    "reschedule",
                )
            field("starts_local", "Beginn · JJJJ-MM-TTTHH:mm", true)
            field("duration_minutes", "Dauer in Minuten", true)
            field("notes", "Besichtigungsnotizen / Messwerte", multiline = true)
        }
        "generate" -> {
            field("from_date", "Ab · JJJJ-MM-TT", true)
            field("to_date", "Bis · höchstens 180 Tage", true)
        }
        "material" -> {
            field("name", "Materialbezeichnung", true)
            field("unit", "Einheit", true)
            field("purchase_price", "Einstandspreis in EUR (optional)")
            field("billing_price", "Abrechnungspreis in EUR (optional)")
            field("notes", "Notizen", multiline = true)
        }
        "accessories" -> RevierAccessoryFields(edit, value, controller, enabled, update)
        "equipment" -> {
            RevierProfilePhoto(value.optJSONObject("profile_photo"), controller)
            if (text("selected_photo_uri").isNotEmpty())
                AsyncImage(
                    Uri.parse(text("selected_photo_uri")),
                    "Ausgewähltes Gerätefoto · noch nicht gespeichert",
                    imageLoader = controller.imageLoader,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeOutlinedButton({ pickEquipmentPhoto(true) }, enabled = enabled) {
                    Text("Aufnehmen")
                }
                NativeOutlinedButton({ pickEquipmentPhoto(false) }, enabled = enabled) {
                    Text("Foto auswählen")
                }
            }
            if (value.optBoolean("_photo_only")) return
            field("name", "Gerätebezeichnung", true)
            if (enabled)
                NativeSuggestions(
                    "Gerätebezeichnung auswählen",
                    listOf(
                        "Rasenmäher",
                        "Freischneider/Motorsense",
                        "Rasentrimmer",
                        "Vertikutierer",
                        "Rasenlüfter",
                        "Heckenschere",
                        "Hochheckenschere",
                        "Kettensäge",
                        "Hochentaster",
                        "Häcksler",
                        "Laubbläser",
                        "Laubsauger",
                        "Laubsammler",
                        "Kehrmaschine",
                        "Hochdruckreiniger",
                        "Motorhacke/Bodenfräse",
                        "Rasenwalze",
                        "Streuwagen",
                        "Wasserpumpe",
                        "Drucksprühgerät",
                    ),
                ) {
                    update("name", it)
                }
            choice("condition", "Zustand", listOf("ready", "maintenance", "defective"), "ready")
            field("maintenance_due", "Zusätzlich vorgemerkt · JJJJ-MM-TT")
            field("notes", "Notizen / Defekt", multiline = true)
        }
        "movement" -> {
            if (text("material_name").isNotEmpty())
                NativeInfoRow(text("material_name"), text("unit"))
            if (!value.optBoolean("_resource_fixed"))
                relation("material_id", "materials", "Material")
            if (text("session_id").isBlank()) relation("worklog_id", "worklogs", "Arbeit")
            field(
                "quantity_decimal",
                "Verbrauch" + text("unit").takeIf { it.isNotBlank() }?.let { " in $it" }.orEmpty(),
                true,
            )
            field("reason", "Notiz", multiline = true)
        }
        "maintenance_plan",
        "maintenance_adjust",
        "maintenance_done" -> {
            val plan = value.optJSONObject("plan")
            NativeInfoRow("Gerät", edit.original.optString("name"))
            if (plan != null) NativeInfoRow("Aufgabe", plan.optString("title"))
            if (edit.kind == "maintenance_plan") {
                field("title", "Wartungstätigkeit", true)
                RevierMaintenanceSuggestions(controller, enabled) { update("title", it) }
                field("interval_value", "Alle", true)
                choice(
                    "interval_unit",
                    "Einheit",
                    listOf("days", "weeks", "months", "years"),
                    "days",
                )
                if (text("plan_id").isEmpty())
                    field("starts_on", "Intervall beginnt am · JJJJ-MM-TT", true)
                else field("reason", "Grund für die Änderung", true, true)
            } else if (edit.kind == "maintenance_adjust") {
                choice(
                    "operation",
                    "Änderung",
                    if ((plan?.optString("next_due_on") ?: "") <= revierDay()) listOf("shorten")
                    else listOf("bring_forward", "shorten"),
                    "bring_forward",
                )
                if (text("operation", "bring_forward") == "bring_forward")
                    field("next_due_on", "Nächste Wartung · JJJJ-MM-TT", true)
                else {
                    field("interval_value", "Neues Intervall", true)
                    choice(
                        "interval_unit",
                        "Einheit",
                        listOf("days", "weeks", "months", "years"),
                        "days",
                    )
                }
                field("reason", "Grund", true, true)
                RevierPhrasePicker("Grund auswählen", revierMaintenanceReasons, enabled) {
                    update("reason", it)
                }
            } else {
                field("performed_on", "Erledigt am · JJJJ-MM-TT", true)
                Row {
                    Checkbox(
                        value.optBoolean("shorten_interval"),
                        { if (enabled) update("shorten_interval", it) },
                        enabled = enabled,
                    )
                    Text("Intervall dauerhaft verkürzen", Modifier.padding(top = 12.dp))
                }
                if (value.optBoolean("shorten_interval")) {
                    field("shorter_interval_value", "Kürzeres Intervall", true)
                    choice(
                        "shorter_interval_unit",
                        "Neue Einheit",
                        listOf("days", "weeks", "months", "years"),
                        "days",
                    )
                }
                field("reason", "Grund / Anlass", value.optBoolean("shorten_interval"), true)
                RevierPhrasePicker("Grund auswählen", revierMaintenanceReasons, enabled) {
                    update("reason", it)
                }
                field("notes", "Notiz", multiline = true)
                RevierMaintenanceSuggestions(controller, enabled) { phrase ->
                    update(
                        "notes",
                        listOf(text("notes").trim(), phrase.trim())
                            .filter { it.isNotEmpty() }
                            .joinToString("\n"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RevierChoice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            NativeOutlinedButton(
                { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(choices.firstOrNull { it.first == value }?.second ?: "Bitte auswählen")
            }
            DropdownMenu(expanded, { expanded = false }) {
                for ((key, text) in choices) DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onChange(key)
                    },
                )
            }
        }
    }
}

@Composable
private fun RevierMaintenanceSuggestions(
    controller: NativeAppController,
    enabled: Boolean,
    onChoose: (String) -> Unit,
) {
    var phrases by remember { mutableStateOf<List<RevierPhrase>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller, retry) {
        try {
            phrases = RevierMaintenanceCatalog.actions(controller)
            failed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    RevierPhrasePicker("Tätigkeitstext auswählen", phrases, enabled, onChoose)
    if (failed) {
        Text(
            "Tätigkeitstexte derzeit nicht lesbar. Eine eigene Tätigkeit kannst du weiterhin eingeben."
        )
        NativeTextButton({ retry++ }, enabled = enabled) { Text("Texte erneut lesen") }
    }
}

@Composable
private fun RevierPhrasePicker(
    label: String,
    phrases: List<RevierPhrase>,
    enabled: Boolean,
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    NativeOutlinedButton(
        {
            search = ""
            open = true
        },
        enabled = enabled && phrases.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
    val matching =
        phrases.filter {
            search.isBlank() ||
                it.label.contains(search, ignoreCase = true) ||
                it.text.contains(search, ignoreCase = true)
        }
    if (open)
        NativeSearchDialog(
            label,
            search,
            { search = it },
            matching.size,
            searchLabel = "Text suchen",
            onDismiss = { open = false },
        ) { close ->
            items(matching, key = { it.id }) { phrase ->
                NativeTextButton(
                    {
                        if (enabled) onChoose(phrase.text)
                        close()
                    },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(phrase.label, style = MaterialTheme.typography.labelLarge)
                        if (phrase.text != phrase.label) Text(phrase.text)
                    }
                }
            }
        }
}

@Composable
private fun RevierRelations(
    controller: NativeAppController,
    collection: String,
    label: String,
    field: String,
    values: JSONObject,
    enabled: Boolean,
    update: (String, Any) -> Unit,
) {
    val selected = values.optJSONArray(field) ?: JSONArray()
    Text(label, style = MaterialTheme.typography.titleMedium)
    for (index in 0 until selected.length()) {
        NativeRecordPicker(
            controller,
            collection,
            label,
            selected.optString(index),
            customerId = if (collection == "photos") values.optString("customer_id") else "",
        ) { chosen ->
            if (enabled)
                update(
                    field,
                    JSONArray(
                        (0 until selected.length()).map {
                            if (it == index) chosen else selected.optString(it)
                        }
                    ),
                )
        }
        if (enabled)
            NativeTextButton({
                update(
                    field,
                    JSONArray(
                        (0 until selected.length())
                            .filter { it != index }
                            .map { selected.optString(it) }
                    ),
                )
            }) {
                Text("Auswahl entfernen")
            }
    }
    if (enabled)
        NativeOutlinedButton({
            update(
                field,
                JSONArray((0 until selected.length()).map { selected.optString(it) } + ""),
            )
        }) {
            Text("$label ergänzen")
        }
}

@Composable
private fun RevierMaterialNeeds(
    controller: NativeAppController,
    needs: JSONArray,
    enabled: Boolean,
    change: (JSONArray) -> Unit,
) {
    Text("Materialbedarf", style = MaterialTheme.typography.titleMedium)
    for (index in 0 until needs.length()) {
        val row = needs.getJSONObject(index)
        fun set(field: String, value: String) {
            if (enabled)
                change(
                    JSONArray(
                        (0 until needs.length()).map {
                            if (it == index) row.revierCopy().put(field, value)
                            else needs.getJSONObject(it)
                        }
                    )
                )
        }
        NativeRecordPicker(controller, "materials", "Material", row.optString("material_id")) {
            set("material_id", it)
        }
        if (enabled)
            NativeField("Menge", row.optString("quantity_decimal"), { set("quantity_decimal", it) })
        else Text("Menge: " + row.optString("quantity_decimal"))
        if (enabled)
            NativeTextButton({
                change(
                    JSONArray(
                        (0 until needs.length())
                            .filter { it != index }
                            .map { needs.getJSONObject(it) }
                    )
                )
            }) {
                Text("Bedarf entfernen")
            }
    }
    if (enabled)
        NativeOutlinedButton({
            change(
                JSONArray(needs.toString())
                    .put(JSONObject().put("material_id", "").put("quantity_decimal", ""))
            )
        }) {
            Text("Materialbedarf ergänzen")
        }
}

@Composable
private fun RevierMeasurements(rows: JSONArray, enabled: Boolean, change: (JSONArray) -> Unit) {
    Text("Messwerte", style = MaterialTheme.typography.titleMedium)
    for (index in 0 until rows.length()) {
        val row = rows.getJSONObject(index)
        for ((field, label) in
            listOf("name" to "Messwert", "value_decimal" to "Wert", "unit" to "Einheit")) {
            if (enabled)
                NativeField(
                    label,
                    row.optString(field),
                    { value ->
                        change(
                            JSONArray(
                                (0 until rows.length()).map {
                                    if (it == index) row.revierCopy().put(field, value)
                                    else rows.getJSONObject(it)
                                }
                            )
                        )
                    },
                )
            else Text("$label: " + row.optString(field))
        }
        if (enabled)
            NativeTextButton({
                change(
                    JSONArray(
                        (0 until rows.length())
                            .filter { it != index }
                            .map { rows.getJSONObject(it) }
                    )
                )
            }) {
                Text("Messwert entfernen")
            }
    }
    if (enabled)
        NativeOutlinedButton({
            change(
                JSONArray(rows.toString())
                    .put(JSONObject().put("name", "").put("value_decimal", "").put("unit", ""))
            )
        }) {
            Text("Messwert ergänzen")
        }
}

private fun revierDecimal(value: String): String {
    val normalized = value.trim().replace(',', '.')
    require(normalized.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?"))) {
        "Menge bitte als Dezimalzahl mit höchstens sechs Nachkommastellen eingeben."
    }
    return normalized
}

private fun revierLocalInstant(value: String): Instant {
    val local =
        try {
            LocalDateTime.parse(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("Datum und Uhrzeit als JJJJ-MM-TTTHH:mm angeben.")
        }
    val offsets = ZoneId.of("Europe/Vienna").rules.getValidOffsets(local)
    require(offsets.size == 1) {
        "Diese Uhrzeit ist durch die Zeitumstellung mehrdeutig oder nicht vorhanden. Bitte eine eindeutige Uhrzeit wählen."
    }
    return local.toInstant(offsets.single())
}

internal fun revierArguments(edit: RevierEdit, value: JSONObject): JSONObject {
    val editing = edit.original.optString("id").isNotEmpty()
    val args = JSONObject().put("request_id", edit.request)
    val id = edit.original.optString("id")
    fun fields(vararg keys: String, blanks: Boolean = editing): JSONObject =
        JSONObject().apply {
            for (key in keys) if (
                value.has(key) && (blanks || value.revierString(key).isNotEmpty())
            )
                put(key, value.get(key))
        }
    fun merge(source: JSONObject) {
        for (key in source.keys()) args.put(key, source.get(key))
    }
    fun number(key: String): Int =
        value.revierString(key).toIntOrNull()
            ?: throw IllegalArgumentException(
                "Bitte eine ganze Zahl für „${mapOf("planned_minutes" to "Geplante Minuten", "interval_days" to "Turnus", "interval_value" to "Intervall", "duration_minutes" to "Dauer")[key] ?: "Dauer / Intervall"}“ eingeben."
            )
    fun guarded(key: String) {
        args.put(key, id).put("expected_record_hash", edit.observedHash)
    }
    fun decimalRows(key: String, field: String): JSONArray =
        JSONArray().apply {
            for (row in value.optJSONArray(key)?.revierObjects().orEmpty()) put(
                row.revierCopy().put(field, revierDecimal(row.optString(field)))
            )
        }
    val tool =
        when (edit.kind) {
            "property" -> {
                val data =
                    fields(
                        "customer_id",
                        "name",
                        "address",
                        "district",
                        "access_notes",
                        "notes",
                        "area_decimal",
                        "area_unit",
                    )
                if (data.optString("area_decimal").isNotEmpty())
                    data.put("area_decimal", revierDecimal(data.getString("area_decimal")))
                if (editing) {
                    data.remove("customer_id")
                    guarded("property_id")
                    args.put("updates", data)
                    "update_property"
                } else {
                    merge(data)
                    "create_property"
                }
            }
            "inquiry" -> {
                val data =
                    fields(
                        "customer_id",
                        "property_id",
                        "title",
                        "concern",
                        "wish_from",
                        "wish_until",
                        "status",
                        "offer_id",
                        "order_id",
                    )
                if (value.optString("inspection_appointment_id").isNotEmpty())
                    data
                        .put("inspection_notes", value.optString("inspection_notes"))
                        .put("measurements", decimalRows("measurements", "value_decimal"))
                        .put("photo_ids", value.optJSONArray("photo_ids") ?: JSONArray())
                if (editing) {
                    data.remove("customer_id")
                    guarded("inquiry_id")
                    args.put("updates", data)
                    "update_inquiry"
                } else {
                    merge(data)
                    "create_inquiry"
                }
            }
            "care_plan" -> {
                val data =
                    fields(
                            "property_id",
                            "title",
                            "anchor_date",
                            "season_start",
                            "season_end",
                            "notes",
                        )
                        .put("interval_days", number("interval_days"))
                        .put("planned_minutes", number("planned_minutes"))
                        .put("material_needs", decimalRows("material_needs", "quantity_decimal"))
                        .put("equipment_ids", value.optJSONArray("equipment_ids") ?: JSONArray())
                if (editing) {
                    data.remove("property_id")
                    guarded("care_plan_id")
                    args
                        .put("updates", data)
                        .put("scope", "this_and_future")
                        .put("effective_from", value.getString("effective_from"))
                    "update_care_plan"
                } else {
                    merge(data)
                    "create_care_plan"
                }
            }
            "visit" -> {
                val data =
                    fields(
                            "property_id",
                            "order_id",
                            "title",
                            "date",
                            "window_start",
                            "window_end",
                            "notes",
                            "status",
                            "parent_visit_id",
                        )
                        .put("planned_minutes", number("planned_minutes"))
                        .put("equipment_ids", value.optJSONArray("equipment_ids") ?: JSONArray())
                if (editing) {
                    data.remove("property_id")
                    data.remove("order_id")
                    data.remove("parent_visit_id")
                    if (value.revierString("remaining_minutes").isNotEmpty())
                        data.put("remaining_minutes", number("remaining_minutes"))
                    guarded("visit_id")
                    args
                        .put("updates", data)
                        .put("reason", value.optString("reason"))
                        .put("scope", "only_this")
                    "update_visit"
                } else {
                    data.remove("status")
                    merge(data)
                    "plan_visit"
                }
            }
            "direct_acceptance" -> {
                guarded("inquiry_id")
                args
                    .put(
                        "accepted_at",
                        revierLocalInstant(value.getString("accepted_local")).toString(),
                    )
                    .put("evidence", value.optString("evidence"))
                    .put("confirmed", value.optBoolean("confirmed"))
                if (value.optString("billing_customer_id").isNotEmpty())
                    args.put("billing_customer_id", value.getString("billing_customer_id"))
                "accept_inquiry_direct"
            }
            "inspection" -> {
                guarded("inquiry_id")
                val start = revierLocalInstant(value.getString("starts_local"))
                args
                    .put("starts_at", start.toString())
                    .put("ends_at", start.plusSeconds(number("duration_minutes") * 60L).toString())
                    .put("timezone", "Europe/Vienna")
                    .put(
                        "mode",
                        value.optString(
                            "mode",
                            if (value.optString("inspection_appointment_id").isEmpty()) "first"
                            else "reschedule",
                        ),
                    )
                    .put("notes", value.optString("notes"))
                if (value.optString("appointment_hash").isNotEmpty())
                    args.put("expected_appointment_hash", value.getString("appointment_hash"))
                "plan_inquiry_inspection"
            }
            "generate" -> {
                args
                    .put("care_plan_id", id)
                    .put("from_date", value.getString("from_date"))
                    .put("to_date", value.getString("to_date"))
                "generate_care_visits"
            }
            "material" -> {
                merge(fields("name", "unit", "notes"))
                for ((input, key) in
                    listOf(
                        "purchase_price" to "purchase_price_minor",
                        "billing_price" to "billing_price_minor",
                    )) {
                    val text = value.revierString(input).trim().replace(',', '.')
                    if (text.isNotEmpty()) {
                        require(text.matches(Regex("(0|[1-9][0-9]*)(\\.[0-9]{1,2})?"))) {
                            "Preis als nicht negativen EUR-Betrag mit höchstens zwei Nachkommastellen eingeben."
                        }
                        val minor = BigDecimal(text).movePointRight(2).longValueExact()
                        require(minor <= 9_007_199_254_740_991L) { "Dieser Preis ist zu groß." }
                        args.put(key, minor)
                    } else if (editing && edit.original.has(key)) args.put(key, JSONObject.NULL)
                }
                if (editing) guarded("material_id")
                "save_material"
            }
            "accessories" -> {
                guarded("equipment_id")
                val accessories = JSONArray()
                for (row in
                    value.optJSONArray("accessories")?.revierObjects().orEmpty()) accessories.put(
                    row.revierCopy().apply {
                        remove("equipment_id")
                        remove("photo")
                        remove("selected_photo_uri")
                    }
                )
                args
                    .put("accessories", accessories)
                    .put("current_chain_id", value.optString("current_chain_id"))
                "save_equipment"
            }
            "equipment" -> {
                merge(
                    fields(
                        "name",
                        "condition",
                        "maintenance_due",
                        "notes",
                        "profile_photo_upload_id",
                    )
                )
                if (editing) guarded("equipment_id")
                "save_equipment"
            }
            "movement" -> {
                args
                    .put("material_id", value.optString("material_id", id))
                    .put("expected_record_hash", edit.observedHash)
                    .put("quantity_decimal", revierDecimal(value.getString("quantity_decimal")))
                    .put("reason", value.optString("reason"))
                if (value.optString("session_id").isNotBlank())
                    args.put("session_id", value.getString("session_id"))
                else args.put("worklog_id", value.getString("worklog_id"))
                "record_material_consumption"
            }
            "maintenance_plan",
            "maintenance_adjust",
            "maintenance_done" -> {
                args
                    .put("equipment_id", value.optString("equipment_id", id))
                    .put("expected_record_hash", edit.observedHash)
                if (value.optString("plan_id").isNotEmpty())
                    args.put("plan_id", value.getString("plan_id"))
                if (edit.kind == "maintenance_done") {
                    args
                        .put("performed_on", value.getString("performed_on"))
                        .put("reason", value.optString("reason"))
                        .put("notes", value.optString("notes"))
                    if (value.optBoolean("shorten_interval"))
                        args
                            .put("shorter_interval_value", number("shorter_interval_value"))
                            .put(
                                "shorter_interval_unit",
                                value.optString("shorter_interval_unit", "days"),
                            )
                    "record_equipment_maintenance"
                } else {
                    val operation =
                        if (edit.kind == "maintenance_plan") "configure"
                        else value.optString("operation", "bring_forward")
                    args.put("operation", operation)
                    if (edit.kind == "maintenance_plan") {
                        args.put("title", value.optString("title"))
                        if (value.optString("plan_id").isEmpty())
                            args.put("starts_on", value.getString("starts_on"))
                    }
                    if (operation == "bring_forward")
                        args.put("next_due_on", value.getString("next_due_on"))
                    else
                        args
                            .put("interval_value", number("interval_value"))
                            .put("interval_unit", value.optString("interval_unit", "days"))
                    if (value.optString("reason").isNotEmpty())
                        args.put("reason", value.getString("reason"))
                    "save_equipment_maintenance_plan"
                }
            }
            else -> error("Unbekannte Bearbeitung.")
        }
    return JSONObject().put("tool", tool).put("args", args)
}

@Composable
private fun RevierTour(controller: NativeAppController) {
    var dateInput by rememberSaveable {
        mutableStateOf(controller.route.args.optString("date", revierDay()))
    }
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
    var ordering by remember(date) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val key = "revier:tour:$date"
    fun retain() {
        controller.saveDraft(
            key,
            JSONObject()
                .put("selected", JSONArray(selected))
                .put("expected_record_hash", originalHash)
                .put("pending", pending ?: JSONObject.NULL),
        )
    }
    LaunchedEffect(date, revision) {
        try {
            val result = controller.query("get_day_tour", JSONObject().put("date", date))
            tour = result
            val visits =
                controller.query(
                    "list_visits",
                    JSONObject().put("from_date", date).put("to_date", date).put("limit", 100),
                )
            val all =
                (result.optJSONArray("stops")?.revierObjects().orEmpty() +
                        visits.optJSONArray("items")?.revierObjects().orEmpty())
                    .distinctBy { it.optString("id") }
            available = all
            val saved = controller.draft(key)
            val chosen =
                saved?.optJSONArray("selected")
                    ?: result.getJSONObject("tour").getJSONArray("visit_ids")
            selected = (0 until chosen.length()).map { chosen.getString(it) }
            originalHash =
                saved?.optString("expected_record_hash")?.takeIf { it.isNotEmpty() }
                    ?: result.getString("record_hash")
            pending = saved?.optJSONObject("pending")
            restored = saved?.optBoolean("_restore_action_review_required") == true
            ordering = saved != null || result.getJSONObject("tour").optBoolean("needs_review")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message.orEmpty()
        }
    }
    fun save(operation: JSONObject) {
        if (busy) return
        busy = true
        scope.launch {
            val checkingPending = pending != null
            try {
                val existing = pending
                if (existing != null) {
                    val proof =
                        controller.query(
                            "inspect_edit_action",
                            JSONObject()
                                .put("original_tool", existing.getString("tool"))
                                .put("original_args", existing.getJSONObject("args")),
                        )
                    if (proof.optString("state") == "content_removed")
                        error("Diese Touränderung wurde endgültig entfernt.")
                    if (proof.optString("state") == "confirmed") {
                        controller.removeDraft(key)
                        pending = null
                        revision++
                        controller.message("Touränderung bestätigt.")
                        return@launch
                    }
                    error(
                        "Das Ergebnis der ursprünglichen Touränderung ist noch offen oder wurde verändert. Vorgang bleibt erhalten; keine Touränderung wiederholt."
                    )
                }
                if (restored)
                    error(
                        "Tourentwurf stammt aus einem anderen Gerätestand. Aktuelle Tour zuerst prüfen."
                    )
                pending = existing ?: operation
                retain()
                controller.flushDrafts()
                controller.query(pending!!.getString("tool"), pending!!.getJSONObject("args"))
                controller.removeDraft(key)
                pending = null
                revision++
                failure = ""
                controller.message("Tagestour lokal gespeichert.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (
                    !checkingPending &&
                        error is NativeCallFailure &&
                        error.detail.optString("commit_state") == "not_committed"
                )
                    pending = null
                retain()
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
            pending?.optString("tool").orEmpty(),
            pending?.optJSONObject("args"),
            !busy,
            inspectionAttempt,
        ) { _ ->
            controller.removeDraft(key)
            pending = null
            revision++
            failure = ""
            controller.message(
                "Tour gespeichert.",
                NativeRoute("tour", args = JSONObject().put("date", date)),
            )
        }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tagestour", style = MaterialTheme.typography.headlineSmall)
                    NativeDateField("Datum", dateInput, { dateInput = it }, enabled = !busy)
                    NativeOutlinedButton(
                        {
                            date = dateInput
                            revision++
                        },
                        enabled = !busy,
                    ) {
                        Text("Datum öffnen")
                    }
                    FilterChip(
                        ordering,
                        { ordering = !ordering },
                        label = { Text("Reihenfolge") },
                        modifier = Modifier.semantics { testTag = UiTags.TOUR_ORDER },
                    )
                    NativeContext(controller, controller.route.args)
                    if (tour?.optJSONObject("tour")?.optBoolean("needs_review") == true)
                        Text(
                            "Tour nach Terminänderung prüfen: " +
                                tour?.optJSONObject("tour")?.optString("review_reason"),
                            color = MaterialTheme.colorScheme.error,
                        )
                    val savedIds = tour?.optJSONObject("tour")?.optJSONArray("visit_ids")
                    if (
                        savedIds != null &&
                            selected != (0 until savedIds.length()).map { savedIds.getString(it) }
                    )
                        Text("Tourentwurf · noch nicht gespeichert")
                    if (pending != null || restored)
                        Text(
                            "Eine frühere Änderung muss zuerst anhand ihres Ergebnisses geprüft werden."
                        )
                    if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
                    if ((failure.isNotEmpty() || restored) && pending == null)
                        NativeTextButton({
                            scope.launch {
                                try {
                                    comparison =
                                        controller.query(
                                            "get_day_tour",
                                            JSONObject().put("date", date),
                                        )
                                    reviewed = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    failure = error.message.orEmpty()
                                }
                            }
                        }) {
                            Text("Aktuelle Tour vergleichen")
                        }
                }
            }
            items(
                available
                    .filter { ordering || it.getString("id") in selected }
                    .sortedWith(
                        compareBy<JSONObject> {
                                selected.indexOf(it.getString("id")).takeIf { index -> index >= 0 }
                                    ?: Int.MAX_VALUE
                            }
                            .thenBy { it.optString("title") }
                    ),
                key = { it.getString("id") },
            ) { visit ->
                val visitId = visit.getString("id")
                val index = selected.indexOf(visitId)
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row {
                            if (ordering)
                                Checkbox(
                                    index >= 0,
                                    { checked ->
                                        selected =
                                            if (checked) selected + visitId else selected - visitId
                                        retain()
                                    },
                                    enabled = !busy && pending == null && !restored,
                                    modifier =
                                        Modifier.semantics { testTag = UiTags.tourSelect(visitId) },
                                )
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(
                                    (if (index >= 0) "${index + 1}. " else "") +
                                        visit.optString("title", "Einsatz")
                                )
                                Text(
                                    listOf(
                                            nativeDisplayDate(visit.optString("date")),
                                            revierLabel(visit.optString("status")),
                                            visit.optString("window_start"),
                                            visit.optString("window_end"),
                                        )
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeTextButton({ controller.navigate("visits", visitId, "detail") }) {
                                Text("Details")
                            }
                            if (ordering) {
                                NativeTextButton(
                                    {
                                        selected =
                                            selected.toMutableList().apply {
                                                java.util.Collections.swap(this, index, index - 1)
                                            }
                                        retain()
                                    },
                                    modifier =
                                        Modifier.semantics {
                                            testTag = UiTags.tourMove(visitId, "up")
                                        },
                                    enabled = index > 0 && pending == null && !restored && !busy,
                                ) {
                                    Text("Nach oben")
                                }
                                NativeTextButton(
                                    {
                                        selected =
                                            selected.toMutableList().apply {
                                                java.util.Collections.swap(this, index, index + 1)
                                            }
                                        retain()
                                    },
                                    modifier =
                                        Modifier.semantics {
                                            testTag = UiTags.tourMove(visitId, "down")
                                        },
                                    enabled =
                                        index >= 0 &&
                                            index < selected.lastIndex &&
                                            pending == null &&
                                            !restored &&
                                            !busy,
                                ) {
                                    Text("Nach unten")
                                }
                            }
                        }
                    }
                }
            }
            if (pending != null) item { NativeInspectionBanner(inspection) { inspectionAttempt++ } }
            if (restored) item { NativeRestoreBanner() }
            if (available.isEmpty())
                item { Text("Hier planst du die Reihenfolge der Einsätze dieses Tages.") }
        }
        NativeActions {
            NativeOutlinedButton({
                controller.navigate(
                    "visits",
                    args = JSONObject().put("date_from", date).put("date_to", date),
                )
            }) {
                Text("Einsätze")
            }
            if (ordering || pending != null)
                NativeButton(
                    {
                        if (pending != null) save(requireNotNull(pending))
                        else {
                            val previous =
                                tour
                                    ?.optJSONObject("tour")
                                    ?.optJSONArray("visit_ids")
                                    ?.let { ids ->
                                        (0 until ids.length()).map { ids.getString(it) }
                                    }
                                    .orEmpty()
                            val changed =
                                if (previous.size == selected.size)
                                    previous.indices.filter { previous[it] != selected[it] }
                                else emptyList()
                            val adjacent =
                                changed.size == 2 &&
                                    changed[1] == changed[0] + 1 &&
                                    previous[changed[0]] == selected[changed[1]] &&
                                    previous[changed[1]] == selected[changed[0]]
                            val args =
                                JSONObject()
                                    .put("request_id", revierRequest())
                                    .put("date", date)
                                    .put("expected_record_hash", originalHash)
                            if (adjacent)
                                args.put("visit_id", previous[changed[0]]).put("direction", "down")
                            else args.put("visit_ids", JSONArray(selected))
                            save(
                                JSONObject()
                                    .put("tool", if (adjacent) "move_tour_stop" else "set_day_tour")
                                    .put("args", args)
                            )
                        }
                    },
                    modifier = Modifier.semantics { testTag = UiTags.TOUR_SAVE },
                    enabled = !busy && originalHash.isNotEmpty(),
                ) {
                    Text(if (pending != null) "Ergebnis prüfen" else "Tour speichern")
                }
            if (!ordering)
                NativeButton(
                    {
                        scope.launch {
                            busy = true
                            try {
                                val nav =
                                    controller.query(
                                        "get_tour_navigation",
                                        JSONObject()
                                            .put("date", date)
                                            .put("expected_record_hash", originalHash),
                                    )
                                try {
                                    controller.context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(nav.getString("navigation_uri")),
                                        )
                                    )
                                } catch (_: android.content.ActivityNotFoundException) {
                                    controller.context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(nav.getString("fallback_uri")),
                                        )
                                    )
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure =
                                    nativeActionError(
                                        error,
                                        "Die Navigation konnte nicht geöffnet werden.",
                                    )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    Modifier.semantics { testTag = UiTags.TOUR_NAVIGATE },
                    enabled =
                        !busy &&
                            !restored &&
                            pending == null &&
                            selected.isNotEmpty() &&
                            tour?.optJSONObject("tour")?.optBoolean("needs_review") == false &&
                            selected ==
                                tour?.optJSONObject("tour")?.optJSONArray("visit_ids")?.let { ids ->
                                    (0 until ids.length()).map { ids.getString(it) }
                                },
                ) {
                    Text("Navigation starten")
                }
        }
    }
    comparison?.let { current ->
        AlertDialog(
            onDismissRequest = { comparison = null },
            title = { Text("Tagestour vergleichen") },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Aktuell gespeichert:", style = MaterialTheme.typography.titleMedium)
                    for ((index, stop) in
                        current.optJSONArray("stops")?.revierObjects().orEmpty().withIndex()) Text(
                        "${index + 1}. " + stop.optString("title", "Einsatz")
                    )
                    Text("Eigener Entwurf:", style = MaterialTheme.typography.titleMedium)
                    for ((index, visitId) in selected.withIndex()) Text(
                        "${index + 1}. " +
                            (available
                                .firstOrNull { it.optString("id") == visitId }
                                ?.optString("title") ?: "Zugeordneter Einsatz")
                    )
                    Row {
                        Checkbox(reviewed, { reviewed = it })
                        Text(
                            "Aktuelle Tour und eigenen Entwurf geprüft",
                            Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
            dismissButton = { NativeTextButton({ comparison = null }) { Text("Zurück") } },
            confirmButton = {
                NativeButton(
                    {
                        busy = true
                        scope.launch {
                            try {
                                check(pending == null) {
                                    "Ergebnis der ursprünglichen Touränderung zuerst prüfen."
                                }
                                controller.adoptDraft(
                                    key,
                                    JSONObject()
                                        .put("selected", JSONArray(selected))
                                        .put(
                                            "expected_record_hash",
                                            current.getString("record_hash"),
                                        )
                                        .put("pending", JSONObject.NULL),
                                )
                                originalHash = current.getString("record_hash")
                                restored = false
                                comparison = null
                                failure = "Vergleich bestätigt. Tour erneut ausdrücklich speichern."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error.message.orEmpty()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = reviewed && !busy && pending == null,
                ) {
                    Text("Weiterbearbeiten")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevierSearch(controller: NativeAppController) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    val compactGroups =
        imeVisible &&
            LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var query by rememberSaveable { mutableStateOf(controller.route.args.optString("query")) }
    var type by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var frozenText by rememberSaveable { mutableStateOf("") }
    val frozen = remember(frozenText) { frozenText.takeIf { it.isNotBlank() }?.let(::JSONObject) }
    val scope = rememberCoroutineScope()
    fun search(start: Int, usePrevious: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val args =
                    if (usePrevious) frozen?.revierCopy() ?: JSONObject()
                    else
                        JSONObject().put("query", query).apply {
                            if (type.isNotEmpty()) put("types", JSONArray().put(type))
                            for ((key, value) in
                                listOf(
                                    "status" to status,
                                    "from_date" to from,
                                    "to_date" to until,
                                )) if (value.isNotEmpty()) put(key, value)
                        }
                args.put("offset", start).put("limit", 50)
                result = controller.query("search_local", args)
                frozenText = args.toString()
                offset = start
                failure = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) {
        if (result == null && frozen != null) search(offset, true)
        else if (result == null && query.isNotBlank()) search(0)
    }
    // Without a query the search shows what was opened last.
    val recent = remember(query.isBlank()) { controller.recentSearchHits() }
    val rows = result?.optJSONArray("items")?.revierObjects().orEmpty()
    NativeSearchList(
        "Suchen",
        query,
        { query = it },
        result?.let { "${it.optInt("total")} Treffer" } ?: "Suchtext eingeben",
        onFilters = { filtersOpen = !filtersOpen },
        onFocus = { filtersOpen = false },
        onSearch = { if (query.isNotBlank()) search(0) },
        onVoice = {
            controller.speech { spoken ->
                query = spoken
                search(0)
            }
        },
    ) {
        if (!imeVisible && !filtersOpen && (result == null || frozen?.optString("query") != query))
            NativeTextButton(
                { search(0) },
                enabled = !busy && query.isNotBlank(),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("Lokal suchen")
            }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding =
                PaddingValues(horizontal = 16.dp, vertical = if (compactGroups) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filtersOpen)
                item(key = "native-list-filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RevierChoice(
                            "Art",
                            type,
                            listOf(
                                "" to "Alle Arten",
                                "customer" to "Kunden",
                                "property" to "Objekte",
                                "settlement" to "Abschlüsse",
                                "inquiry" to "Anfragen",
                                "care_plan" to "Pflegepläne",
                                "order" to "Aufträge",
                                "appointment" to "Einsätze",
                                "visit" to "Einsätze",
                                "activity" to "Arbeiten",
                                "note" to "Notizen",
                                "offer" to "Angebote",
                                "invoice" to "Rechnungen",
                                "material" to "Material",
                                "equipment" to "Geräte",
                            ),
                        ) {
                            type = it
                        }
                        NativeField("Status (optional)", status, { status = it })
                        NativeField("Zeitraum ab · JJJJ-MM-TT", from, { from = it })
                        NativeField("Zeitraum bis · JJJJ-MM-TT", until, { until = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeButton({ search(0) }, enabled = !busy && query.isNotBlank()) {
                                Text("Lokal suchen")
                            }
                            NativeTextButton({
                                query = ""
                                type = ""
                                status = ""
                                from = ""
                                until = ""
                                result = null
                                frozenText = ""
                                offset = 0
                            }) {
                                Text("Zurücksetzen")
                            }
                        }
                        result?.let { Text("${it.optInt("total")} Treffer") }
                        if (failure.isNotEmpty())
                            Text(failure, color = MaterialTheme.colorScheme.error)
                    }
                }
            if (failure.isNotEmpty())
                item { Text(failure, color = MaterialTheme.colorScheme.error) }
            if (result != null && rows.isEmpty())
                item { Text("Keine Treffer. Suchtext oder Filter ändern.") }
            if (query.isBlank() && recent.isNotEmpty()) {
                item(key = "recent-heading") {
                    Text("Zuletzt geöffnet", style = MaterialTheme.typography.titleSmall)
                }
                items(
                    recent,
                    key = { "recent:" + it.optString("type") + ":" + it.optString("id") },
                ) { row ->
                    RevierSearchHit(
                        controller,
                        row,
                        Modifier.semantics { testTag = UiTags.SEARCH_RECENT },
                    )
                }
            }
            // Design: results grouped by kind, the four main kinds first.
            for ((type, group) in
                (if (query.isBlank()) emptyList() else rows)
                    .groupBy { it.optString("type") }
                    .entries
                    .sortedBy { (type, _) ->
                        revierSearchOrder.indexOf(type).let {
                            if (it < 0) revierSearchOrder.size else it
                        }
                    }) {
                val heading: @Composable () -> Unit = {
                    Text(
                        "${revierSearchKinds[type] ?: "Weitere"} · ${group.size}",
                        Modifier.semantics { testTag = UiTags.searchGroup(type) },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (!compactGroups) item(key = "group:$type") { heading() }
                itemsIndexed(
                    group,
                    key = { _, row -> row.optString("type") + ":" + row.optString("id") },
                ) { index, row ->
                    if (compactGroups) {
                        // A tall landscape keyboard leaves too little height for a separate
                        // group header and a 48dp hit target. Keep the group beside its rows.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.width(72.dp)) { if (index == 0) heading() }
                            RevierSearchHit(controller, row, Modifier.weight(1f))
                        }
                    } else RevierSearchHit(controller, row)
                }
            }
        }
        if (!imeVisible && (offset > 0 || offset + rows.size < (result?.optInt("total") ?: 0)))
            NativeActions {
                if (offset > 0)
                    NativeOutlinedButton(
                        { search((offset - 50).coerceAtLeast(0), true) },
                        enabled = !busy,
                    ) {
                        Text("Vorherige")
                    }
                if (offset + rows.size < (result?.optInt("total") ?: 0))
                    NativeOutlinedButton({ search(offset + 50, true) }, enabled = !busy) {
                        Text("Weitere Treffer")
                    }
            }
    }
}

private val revierSearchOrder = listOf("property", "customer", "settlement", "activity")

private val revierSearchKinds =
    mapOf(
        "property" to "Objekte",
        "customer" to "Kunden",
        "settlement" to "Abschlüsse",
        "activity" to "Arbeiten",
        "order" to "Aufträge",
        "appointment" to "Einsätze",
        "offer" to "Angebote",
        "invoice" to "Rechnungen",
        "inquiry" to "Anfragen",
        "care_plan" to "Pflegepläne",
        "visit" to "Einsätze",
        "material" to "Material",
        "equipment" to "Geräte",
        "note" to "Notizen",
    )

/**
 * One search result: opens its record; customers and objects can be called or routed to directly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevierSearchHit(
    controller: NativeAppController,
    row: JSONObject,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth().clickable { openRevierSearchHit(controller, row) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.optString("label", row.optString("title", "Eintrag")),
                style = MaterialTheme.typography.titleMedium,
            )
            val detail = row.optString("snippet", row.optString("text", row.optString("status")))
            if (detail.isNotBlank()) Text(detail)
            val phone = row.optString("phone")
            val address = row.optString("address")
            if (phone.isNotBlank() || address.isNotBlank())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (phone.isNotBlank())
                        FilledTonalButton(
                            { controller.dial(phone) },
                            Modifier.heightIn(min = 48.dp).semantics {
                                testTag = UiTags.SEARCH_CALL
                            },
                        ) {
                            Text("Anrufen")
                        }
                    if (address.isNotBlank())
                        FilledTonalButton(
                            { controller.route(address) },
                            Modifier.heightIn(min = 48.dp).semantics {
                                testTag = UiTags.SEARCH_ROUTE
                            },
                        ) {
                            Text("Route")
                        }
                }
        }
    }
}

private fun openRevierSearchHit(controller: NativeAppController, row: JSONObject) {
    controller.rememberSearchHit(row)
    val target = row.optJSONObject("target") ?: row
    val targetType = target.optString("type", row.optString("entity_type"))
    val id = target.optString("id", row.optString("id"))
    if (targetType == "settlement") {
        controller.navigate("settlement_review", id)
        return
    }
    val route =
        when (targetType) {
            "customer" -> "customers"
            "property" -> "properties"
            "inquiry" -> "inquiries"
            "care_plan" -> "care_plans"
            "visit" -> "visits"
            "activity",
            "worklog" -> "worklogs"
            "order" -> "orders"
            "offer" -> "offers"
            "invoice" -> "invoices"
            "appointment" -> "planning"
            "material" -> "materials"
            "equipment" -> "equipment"
            else -> targetType
        }
    controller.navigate(route, id, "detail", JSONObject().put("entity", targetType))
}

private val revierCompareLabels =
    linkedMapOf(
        "name" to "Bezeichnung",
        "title" to "Bezeichnung",
        "customer_id" to "Kunde",
        "property_id" to "Objekt",
        "order_id" to "Auftrag",
        "offer_id" to "Angebot",
        "billing_customer_id" to "Rechnungsempfänger",
        "interval_value" to "Intervall",
        "interval_unit" to "Intervalleinheit",
        "next_due_on" to "Nächste Wartung",
        "performed_on" to "Erledigt am",
        "shorter_interval_value" to "Kürzeres Intervall",
        "shorter_interval_unit" to "Neue Einheit",
        "reason" to "Grund",
        "material_needs" to "Materialbedarf",
        "equipment_ids" to "Benötigte Geräte",
        "measurements" to "Messwerte",
        "photo_ids" to "Dokumentationsfotos",
    )

@Composable
private fun RevierComparisonValue(field: String, value: Any?, repository: NativeDataRepository) {
    val text by
        produceState("Wird gelesen …", field, value?.toString()) {
            this.value =
                try {
                    withContext(Dispatchers.IO) {
                        suspend fun recordLabel(collection: String, id: String): String {
                            if (id.isEmpty()) return "Keine Zuordnung"
                            val row = repository.detail(collection, id)
                            return if (row == null) "Zuordnung derzeit nicht lesbar"
                            else
                                listOf(
                                        row.optString("name"),
                                        row.optString("title"),
                                        row.optString("caption"),
                                    )
                                    .firstOrNull { it.isNotBlank() } ?: "Zugeordneter Eintrag"
                        }
                        when {
                            value == null ||
                                value == JSONObject.NULL ||
                                value.toString().isEmpty() -> "Leer"
                            field in
                                listOf(
                                    "customer_id",
                                    "billing_customer_id",
                                    "property_id",
                                    "offer_id",
                                    "order_id",
                                ) ->
                                recordLabel(
                                    when (field) {
                                        "customer_id",
                                        "billing_customer_id" -> "customers"
                                        "property_id" -> "properties"
                                        "offer_id" -> "offers"
                                        else -> "orders"
                                    },
                                    value.toString(),
                                )
                            field in listOf("equipment_ids", "photo_ids") && value is JSONArray ->
                                (0 until value.length())
                                    .map {
                                        recordLabel(
                                            if (field == "equipment_ids") "equipment" else "photos",
                                            value.optString(it),
                                        )
                                    }
                                    .joinToString("; ")
                                    .ifEmpty { "Keine Auswahl" }
                            field == "material_needs" && value is JSONArray ->
                                value
                                    .revierObjects()
                                    .map {
                                        recordLabel("materials", it.optString("material_id")) +
                                            ": " +
                                            it.optString("quantity_decimal")
                                    }
                                    .joinToString("; ")
                                    .ifEmpty { "Kein Bedarf" }
                            field == "measurements" && value is JSONArray ->
                                value
                                    .revierObjects()
                                    .joinToString("; ") {
                                        listOf(
                                                it.optString("name"),
                                                it.optString("value_decimal"),
                                                it.optString("unit"),
                                            )
                                            .filter { part -> part.isNotBlank() }
                                            .joinToString(" ")
                                    }
                                    .ifEmpty { "Keine Messwerte" }
                            field in
                                listOf(
                                    "status",
                                    "condition",
                                    "interval_unit",
                                    "shorter_interval_unit",
                                ) -> revierLabel(value.toString())
                            else -> value.toString()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "Angabe derzeit nicht lesbar. Vor dem Speichern erneut prüfen."
                }
        }
    Text(text)
}

/** A selected original is a file-backed draft, retained independently of provider grants. */
internal suspend fun nativeRetainPhotoDraft(controller: NativeAppController, source: Uri): Uri =
    withContext(Dispatchers.IO) {
        val mime = controller.context.contentResolver.getType(source).orEmpty()
        val extension =
            when (mime) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                "image/heic",
                "image/heif" -> ".heic"
                else -> ".jpg"
            }
        // Cache is outside the captured files tree. Only a complete immutable draft is published.
        val temporary =
            File.createTempFile("native-equipment-", extension, controller.context.cacheDir)
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
            val file =
                synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
                    val directory = File(controller.context.filesDir, "native-form-media")
                    check(directory.isDirectory || directory.mkdirs()) {
                        "Fotoentwurf kann nicht gespeichert werden."
                    }
                    val target = File(directory, "equipment-" + UUID.randomUUID() + extension)
                    check(temporary.renameTo(target)) {
                        "Fotoentwurf kann nicht veröffentlicht werden."
                    }
                    target
                }
            Uri.fromFile(file)
        } finally {
            temporary.delete()
        }
    }

internal suspend fun nativeDeletePhotoDraft(controller: NativeAppController, source: String) =
    withContext(Dispatchers.IO) {
        if (source.isEmpty()) return@withContext
        val uri = Uri.parse(source)
        if (uri.scheme != "file") return@withContext
        synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
            val directory = File(controller.context.filesDir, "native-form-media").canonicalFile
            val file = File(uri.path.orEmpty()).canonicalFile
            if (file.parentFile == directory && file.name.startsWith("equipment-")) {
                check(!file.exists() || file.delete()) {
                    "Das ungespeicherte Foto konnte nicht entfernt werden. Bitte erneut verwerfen."
                }
            }
        }
    }

private val accessoryFields =
    linkedMapOf(
        "pitch" to "Kettenteilung",
        "gauge" to "Treibgliedstärke",
        "drive_links" to "Anzahl Treibglieder",
        "article_number" to "Hersteller-Artikelnummer",
        "tooth_type" to "Schneidezahntyp",
        "file_size" to "Feilengröße",
        "notes" to "Weitere Angaben",
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevierAccessoryFields(
    edit: RevierEdit,
    value: JSONObject,
    controller: NativeAppController,
    enabled: Boolean,
    update: (String, Any) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Text(
        "Maschine: " + edit.original.optString("name"),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "Fotos helfen beim Wiederfinden. Technische Angaben sind freiwillig.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val entries = value.optJSONArray("accessories") ?: JSONArray()
    fun replace(index: Int, field: String, newValue: Any) {
        val changed = JSONArray(edit.row().optJSONArray("accessories")?.toString() ?: "[]")
        changed.getJSONObject(index).put(field, newValue)
        update("accessories", changed)
    }
    val focusId = value.optString("_focus_accessory_id")
    val displayIndices =
        (0 until entries.length()).sortedBy {
            if (entries.getJSONObject(it).optString("id") == focusId) 0 else 1
        }
    for (index in displayIndices) key(entries.getJSONObject(index).optString("id")) {
        val item = entries.getJSONObject(index)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    item.optString("name").ifBlank { "Zubehör" },
                    style = MaterialTheme.typography.titleMedium,
                )
                val selectedPhoto = item.optString("selected_photo_uri")
                if (selectedPhoto.isNotEmpty())
                    AsyncImage(
                        Uri.parse(selectedPhoto),
                        "Ausgewähltes Zubehörfoto · noch nicht gespeichert",
                        imageLoader = controller.imageLoader,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 180.dp),
                    )
                else RevierProfilePhoto(item.optJSONObject("photo"), controller, "Zubehörfoto")
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((camera, label) in
                        listOf(
                            true to "Foto aufnehmen",
                            false to "Foto auswählen",
                        )) NativeOutlinedButton(
                        {
                            if (edit.mediaBusy || edit.pending != null || edit.restored)
                                return@NativeOutlinedButton
                            edit.mediaBusy = true
                            scope.launch {
                                try {
                                    controller.pickPhoto(camera)?.let { source ->
                                        try {
                                            val retained =
                                                nativeRetainPhotoDraft(controller, source)
                                            val changed =
                                                JSONArray(
                                                    edit
                                                        .row()
                                                        .optJSONArray("accessories")
                                                        ?.toString() ?: "[]"
                                                )
                                            val current =
                                                changed.revierObjects().firstOrNull {
                                                    it.optString("id") == item.getString("id")
                                                }
                                                    ?: error(
                                                        "Das Zubehör ist nicht mehr im Entwurf vorhanden."
                                                    )
                                            val previous = current.optString("selected_photo_uri")
                                            current
                                                .put("selected_photo_uri", retained.toString())
                                                .remove("photo_upload_id")
                                            edit.change("accessories", changed)
                                            controller.saveDraft(edit.key, edit.snapshot())
                                            controller.flushDrafts()
                                            nativeDeletePhotoDraft(controller, previous)
                                        } finally {
                                            withContext(Dispatchers.IO) {
                                                PhotoCaptureProvider.release(
                                                    controller.context,
                                                    source,
                                                )
                                            }
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    controller.message(failure.message.orEmpty())
                                } finally {
                                    edit.mediaBusy = false
                                }
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(label)
                    }
                }
                RevierChoice(
                    "Art",
                    item.optString("type", "other"),
                    listOf("chain" to "Kette", "other" to "Anderes Zubehör"),
                    enabled,
                ) {
                    if (
                        it != "chain" && value.optString("current_chain_id") == item.optString("id")
                    )
                        update("current_chain_id", "")
                    replace(index, "type", it)
                }
                for ((key, label) in
                    linkedMapOf(
                        "name" to "Bezeichnung",
                        "manufacturer" to "Hersteller",
                        "model" to "Modell",
                    ) +
                        accessoryFields.filterKeys {
                            item.optString("type") == "chain" ||
                                it in listOf("article_number", "notes")
                        }) {
                    if (enabled)
                        NativeField(
                            label,
                            item.optString(key),
                            { replace(index, key, it) },
                            multiline = key == "notes",
                        )
                    else if (item.optString(key).isNotBlank())
                        Text("$label: " + item.optString(key))
                }
                if (item.optString("type") == "chain")
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected =
                                    value.optString("current_chain_id") == item.getString("id"),
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { update("current_chain_id", item.getString("id")) },
                            )
                    ) {
                        RadioButton(
                            value.optString("current_chain_id") == item.getString("id"),
                            onClick = null,
                            enabled = enabled,
                        )
                        Text("Aktuell verwendet", Modifier.padding(top = 12.dp))
                    }
            }
        }
    }
    if (value.optString("current_chain_id").isNotEmpty())
        NativeTextButton({ update("current_chain_id", "") }, enabled = enabled) {
            Text("Keine aktuelle Kette festlegen")
        }
    NativeOutlinedButton(
        {
            update(
                "accessories",
                JSONArray(entries.toString())
                    .put(
                        JSONObject()
                            .put("id", "acc_" + UUID.randomUUID().toString().replace("-", ""))
                            .put("type", "other")
                    ),
            )
        },
        enabled = enabled,
    ) {
        Text("Zubehör hinzufügen")
    }
}

private suspend fun revierDeleteEditPhotos(controller: NativeAppController, value: JSONObject) {
    nativeDeletePhotoDraft(controller, value.optString("selected_photo_uri"))
    for (item in
        value.optJSONArray("accessories")?.revierObjects().orEmpty()) nativeDeletePhotoDraft(
        controller,
        item.optString("selected_photo_uri"),
    )
}

/** One value, the same guarded writer as MCP, with the previous value available for undo. */
@Composable
internal fun NativeRevierValueSheet(
    controller: NativeAppController,
    kind: String,
    record: JSONObject,
    field: String,
    label: String,
    onSaved: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(field) { mutableStateOf(false) }
    var failure by remember(field) { mutableStateOf("") }
    var fieldConflict by remember(field) { mutableStateOf<JSONObject?>(null) }
    var attemptedInput by remember(field) { mutableStateOf("") }
    var proposed by remember(field) { mutableStateOf<Any?>(null) }
    val numeric =
        field in
            setOf(
                "planned_minutes",
                "remaining_minutes",
                "interval_days",
                "area_decimal",
                "purchase_price_minor",
                "billing_price_minor",
            )
    val before = remember(field, record.optString("id")) { JSONObject(record.toString()) }
    val initialHash = remember(field, record.optString("id")) { RevierRules.hash(before) }
    val original =
        before.opt(field)
            ?: if (field == "equipment_ids") JSONArray()
            else if (field.endsWith("_price_minor")) JSONObject.NULL else ""
    val shown =
        if (
            field.endsWith("_price_minor") &&
                original != JSONObject.NULL &&
                original.toString().isNotBlank()
        )
            OfferMoney.decimal(original.toString().toLong())
        else if (original == JSONObject.NULL) "" else original.toString()
    var chosen by remember(field) { mutableStateOf(shown) }
    suspend fun write(value: Any, expectedUndo: Any? = null, agreed: JSONObject? = null): Any {
        val current =
            withContext(Dispatchers.IO) {
                controller.store.nativeData().detail(revierCollection(kind), record.getString("id"))
                    ?: error("Der Eintrag fehlt.")
            }
        if (expectedUndo != null)
            require(
                PixelMcpWriteTools.hash(current.opt(field) ?: "") ==
                    PixelMcpWriteTools.hash(expectedUndo)
            ) {
                "Diese Angabe wurde inzwischen geändert. Beide Stände bitte prüfen."
            }
        val args =
            JSONObject()
                .put("request_id", revierRequest())
                .put("${kind}_id", record.getString("id"))
                .put(
                    "expected_record_hash",
                    if (expectedUndo == null) agreed?.let { RevierRules.hash(it) } ?: initialHash
                    else RevierRules.hash(current),
                )
        val tool =
            when (kind) {
                "material",
                "equipment" -> {
                    args.put(field, value)
                    "save_$kind"
                }
                else -> {
                    args.put("updates", JSONObject().put(field, value))
                    if (kind == "visit")
                        args.put("scope", "only_this").put("reason", "Angabe geändert")
                    if (kind == "care_plan")
                        args.put("scope", "this_and_future").put("effective_from", revierDay())
                    "update_$kind"
                }
            }
        return controller.query(tool, args).optJSONObject("record")?.opt(field) ?: value
    }
    fun finish(entered: String, agreed: JSONObject? = null) {
        attemptedInput = entered
        if (entered == shown) onClose()
        else {
            busy = true
            scope.launch {
                try {
                    val value: Any =
                        when {
                            field == "equipment_ids" -> JSONArray(entered)
                            field.endsWith("_price_minor") ->
                                if (entered.isBlank()) JSONObject.NULL
                                else OfferMoney.minor(entered.replace(',', '.'))
                            field in
                                setOf("planned_minutes", "remaining_minutes", "interval_days") ->
                                entered.toIntOrNull() ?: error("Bitte eine ganze Zahl eingeben.")
                            field == "area_decimal" -> entered.trim().replace(',', '.')
                            else -> entered
                        }
                    proposed = value
                    val replaced =
                        if (agreed == null) original
                        else
                            agreed.opt(field)
                                ?: if (field == "equipment_ids") JSONArray()
                                else if (field.endsWith("_price_minor")) JSONObject.NULL else ""
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
                                withContext(Dispatchers.IO) {
                                    controller.store
                                        .nativeData()
                                        .detail(revierCollection(kind), record.getString("id"))
                                        ?.let { RevierRules.view(it) }
                                }
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
                    if (field.endsWith("_price_minor"))
                        Text(
                            if (current.isNull(field)) "Leer"
                            else OfferMoney.decimal(current.getLong(field)).replace('.', ',') + " €"
                        )
                    else
                        RevierComparisonValue(
                            field,
                            current.opt(field),
                            controller.store.nativeData(),
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
                    if (field.endsWith("_price_minor"))
                        Text(
                            if (proposed == JSONObject.NULL) "Leer"
                            else
                                OfferMoney.decimal(proposed.toString().toLong()).replace('.', ',') +
                                    " €"
                        )
                    else RevierComparisonValue(field, proposed, controller.store.nativeData())
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
    if (field == "equipment_ids") {
        NativeRevierRelationsSheet(
            controller,
            record,
            field,
            label,
            chosen,
            { chosen = it },
            busy,
            failure,
        ) {
            finish(chosen)
        }
    } else if (field in setOf("condition", "status")) {
        val choices =
            when (kind) {
                "equipment" -> listOf("ready", "maintenance", "defective")
                "visit" -> listOf("planned", "in_progress", "completed", "partial", "cancelled")
                "inquiry" ->
                    listOf("new", "inspection_planned", "offered", "accepted", "declined", "closed")
                else -> listOf("active", "paused")
            }
        NativeRevierChoiceSheet(label, chosen, choices, { chosen = it }, busy, failure) {
            finish(chosen)
        }
    } else
        NativeValueSheet(
            label,
            shown,
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            multiline = field in setOf("notes", "concern", "inspection_notes", "access_notes"),
            error = failure,
            enabled = !busy,
            onClose = { finish(it) },
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeRevierRelationsSheet(
    controller: NativeAppController,
    record: JSONObject,
    field: String,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    busy: Boolean,
    error: String,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            RevierRelations(
                controller,
                "equipment",
                label,
                field,
                JSONObject(record.toString()).put(field, JSONArray(value)),
                !busy,
            ) { _, rows ->
                onChange(rows.toString())
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            NativeActions { NativeTextButton(onClose, enabled = !busy) { Text("Fertig") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeRevierChoiceSheet(
    label: String,
    value: String,
    choices: List<String>,
    onChange: (String) -> Unit,
    busy: Boolean,
    error: String,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            for (choice in choices) FilterChip(
                value == choice,
                { onChange(choice) },
                label = { Text(revierLabel(choice)) },
                enabled = !busy,
            )
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            NativeActions { NativeTextButton(onClose, enabled = !busy) { Text("Fertig") } }
        }
    }
}
