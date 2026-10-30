package at.hausgarten.service

import android.content.Intent
import android.net.Uri
import coil3.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val nativeSections = linkedMapOf(
    "today" to "Heute", "worklogs" to "Arbeitsaufzeichnungen", "timer" to "Zeiterfassung", "customers" to "Kunden", "properties" to "Objekte",
    "inquiries" to "Anfragen", "care_plans" to "Pflegepläne", "visits" to "Einsätze", "tour" to "Tagestour", "materials" to "Material",
    "equipment" to "Geräte", "revier_search" to "Lokale Suche", "revier_archive" to "Revier-Archiv", "orders" to "Aufträge", "offers" to "Angebote", "invoices" to "Rechnungen", "payments" to "Zahlungen",
    "catalog" to "Leistungskatalog", "photos" to "Fotos", "plants" to "Pflanzen", "appointments" to "Termine", "weather" to "Wetter",
    "planning" to "Planung", "trash" to "Papierkorb", "local_actions" to "Lokale Vorgänge", "settings" to "Einstellungen", "connections" to "Verbindungen", "backup" to "Sicherung"
)
private fun nativeDay(): String = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
private fun primaryKind(collection: String) = when (collection) { "customers" -> "customer"; "orders" -> "order"; else -> "worklog" }
private fun primaryGet(kind: String) = when (kind) { "customer" -> "get_customer"; "order" -> "get_order"; else -> "get_work_record" }
private fun primaryId(kind: String) = if (kind == "worklog") "work_record_id" else "${kind}_id"

/** Complete native route host; each feature uses the shared Kotlin repositories and writers. */
@Composable
internal fun NativeApplication(controller: NativeAppController) {
    val route = controller.route
    val notice by controller.messages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val routeStates = rememberSaveableStateHolder()
    var reviewChecked by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(controller) {
        try {
            val pending = withContext(Dispatchers.IO) { RestoredBrowserCheckpoint(controller.context, controller.store).pending() }
            if (pending && controller.route.section != "restore_review") controller.navigate("restore_review")
            else if (!pending && controller.route.section == "restore_review") controller.navigate("today")
        } catch (failure: Exception) { controller.message("Wiederhergestellte Formulare benötigen Prüfung."); controller.navigate("restore_review") }
        reviewChecked = true
    }
    BackHandler(reviewChecked && controller.canGoBack() && route.section != "restore_review") { controller.back() }
    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (reviewChecked && controller.canGoBack() && route.section != "restore_review") TextButton({ controller.back() }) { Text("‹ Zurück") }
                    Text((if (route.section in listOf("office", "more")) "Büro" else nativeSections[route.section]) ?: if (route.section == "restore_review") "Wiederhergestellte Entwürfe" else "Haus & Garten", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
        bottomBar = {
            if (reviewChecked && route.section != "restore_review") NavigationBar {
                listOf("today" to "Heute", "worklogs" to "Arbeitsaufzeichnungen", "customers" to "Kunden", "office" to "Büro").forEach { (section, label) ->
                    // A selected tab has no accessibility click action. Keep the
                    // section overview reachable from its detail and editor routes.
                    val selected = route.section == section && route.id.isEmpty() && route.mode == "list"
                    NavigationBarItem(selected = selected, onClick = { controller.openSection(section) }, icon = { Text(when (section) { "today" -> "⌂"; "worklogs" -> "◷"; "customers" -> "♧"; else -> "☰" }) }, label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (!reviewChecked) Column(Modifier.padding(16.dp)) { CircularProgressIndicator(); Text("Übernommene Formulare werden geprüft …") }
            else routeStates.SaveableStateProvider(controller.savedRoute()) { when (route.section) {
                "today" -> NativeToday(controller)
                "more", "office" -> NativeOffice(controller)
                "customers", "orders", "worklogs" -> when {
                    route.mode in listOf("new", "edit", "duplicate") -> NativeEntityEditor(controller, route.section, route.id, route.mode)
                    route.id.isNotEmpty() -> NativeEntityDetail(controller, route.section, route.id)
                    else -> NativeEntityList(controller, route.section)
                }
                "timer", "work" -> NativeTimer(controller)
                "properties", "inquiries", "care_plans", "visits", "tour", "materials", "equipment", "revier_search", "revier_archive" -> NativeRevierScreen(route.section, route.id, controller)
                "offers", "invoices", "catalog", "payments" -> NativeFinanceScreen(route.section, route.id, controller)
                "photos", "plants" -> NativeMediaScreen(route.section, route.id, controller)
                "appointments", "weather", "planning" -> NativePlanningScreen(route.section, route.id, controller)
                "action" -> NativeActionForm(controller, route.id, route.mode, route.args)
                "settings" -> NativeSettings(controller)
                "drafts" -> NativeDraftInbox(controller)
                "trash" -> NativeTrash(controller)
                "local_actions" -> NativeLocalActions(controller)
                "order_review" -> NativeReviewedWorkflow(controller, true, route.id)
                "conflict_review" -> NativeReviewedWorkflow(controller, false, route.id)
                "connections" -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Kalender, Wetter und die private MCP-Verbindung werden getrennt eingerichtet.")
                    Button({ controller.open(PlanningConnectionActivity::class.java) }) { Text("Kalender und Wetter verbinden") }
                    Button({ controller.open(PixelTunnelActivity::class.java) }) { Text("Private MCP-Verbindung") }
                }
                "backup" -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Eine unverschlüsselte ZIP-Datei mit dem vorhandenen Bestand. Das Ziel wählst du im Android-Dateidialog.")
                    Button({ scope.launch { try { controller.flushDrafts(); controller.open(FullBackupActivity::class.java) } catch (failure: Exception) { controller.message(failure.message ?: "Entwürfe konnten nicht gesichert werden.") } } }) { Text("Sicherung öffnen") }
                }
                "restore_review", "restored_drafts" -> NativeRestoredDraftReview(controller)
                else -> NativeRevierScreen(route.section.removePrefix("revier-"), route.id, controller)
            } }
        }
    }
}

@Composable
private fun NativeOffice(controller: NativeAppController) {
    val groups = linkedMapOf(
        "Abrechnung" to listOf("orders", "offers", "invoices", "payments", "catalog"),
        "Einsätze und Betrieb" to listOf("properties", "inquiries", "care_plans", "visits", "tour", "materials", "equipment", "photos", "plants", "appointments", "weather", "planning", "timer"),
        "Verwaltung" to listOf("drafts", "revier_search", "revier_archive", "trash", "local_actions", "settings", "connections", "backup")
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((title, sections) in groups) {
            item { Text(title, style = MaterialTheme.typography.titleMedium) }
            if (title == "Abrechnung") item { OutlinedButton({ controller.navigate("invoices", mode = "new") }, Modifier.fillMaxWidth()) { Text("Arbeiten abrechnen") } }
            items(sections, key = { it }) { section ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { controller.openSection(section) }) {
                    Text(when (section) { "equipment" -> "Maschinenpark und Gerätewartung"; "payments" -> "Zahlungen und Guthaben"; "drafts" -> "Entwürfe"; else -> nativeSections[section] ?: section }, Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun NativeToday(controller: NativeAppController) {
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var session by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val day = nativeDay()
    val workFlow = remember(day) { controller.store.nativeData().watchList("worklogs", dateFrom = day, dateTo = day, limit = 50).nativeErrors(controller) }
    val works by workFlow.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, 0, 50))
    suspend fun refresh() {
        try { coroutineScope {
            val one = async { controller.query("get_revier_overview", JSONObject().put("date", day)) }
            val two = async { controller.query("get_active_work_session") }
            overview = one.await(); session = two.await(); error = ""
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Heute konnte nicht gelesen werden." }
    }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(day, style = MaterialTheme.typography.titleMedium) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ controller.navigate("visits") }) { Text("Einsätze") }; OutlinedButton({ controller.navigate("tour") }) { Text("Tagestour") }; OutlinedButton({ controller.navigate("appointments") }) { Text("Termine") } } }
            val planned = overview?.optJSONArray("planned_visits") ?: JSONArray()
            if (planned.length() > 0) item { Text("Geplante Einsätze", style = MaterialTheme.typography.titleMedium) }
            items((0 until planned.length()).map { planned.getJSONObject(it) }.distinctBy { it.optString("id") }, key = { "visit:" + it.optString("id") }) { visit ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { controller.navigate("visits", visit.getString("id")) }) { Text(visit.optString("title", "Einsatz"), Modifier.padding(12.dp)) }
            }
            item { Text("Erfasste Arbeiten", style = MaterialTheme.typography.titleMedium) }
            if (works.rows.isEmpty()) item { Text("Heute noch keine Arbeiten erfasst.") }
            items(works.rows, key = { "work:" + it.id }) { row ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { controller.navigate("worklogs", row.id) }) { Column(Modifier.padding(12.dp)) { Text(row.title); Text(row.subtitle) } }
            }
            if (works.hasMore) item { TextButton({ controller.navigate("worklogs", args = JSONObject().put("date_from", day).put("date_to", day)) }) { Text("Alle heutigen Arbeiten") } }
            item { Text("Hinweise", style = MaterialTheme.typography.titleMedium) }
            item {
                val due = overview?.optJSONArray("due_visits") ?: JSONArray()
                val overdue = (0 until due.length()).count { due.getJSONObject(it).optString("date") < day }
                val inquiries = overview?.optInt("open_inquiries") ?: 0
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                else if (overview == null) CircularProgressIndicator()
                else if (overdue == 0 && inquiries == 0) Text("Keine offenen Hinweise.")
                if (overdue > 0) TextButton({ controller.navigate("visits") }) { Text("$overdue überfällige Einsätze") }
                if (inquiries > 0) TextButton({ controller.navigate("inquiries") }) { Text("$inquiries offene Anfragen") }
            }
            item { TextButton({ scope.launch { refresh() } }) { Text("Aktualisieren") } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ controller.navigate("worklogs", mode = "new") }, Modifier.weight(1f)) { Text("Arbeit erfassen") }
            Button({ controller.navigate("timer") }, Modifier.weight(1f)) { Text(if (session?.optString("state", "IDLE") in listOf(null, "IDLE")) "Arbeit beginnen" else "Laufende Arbeit öffnen") }
        }
    }
}

@Composable
private fun NativeEntityList(controller: NativeAppController, collection: String) {
    var search by rememberSaveable(collection) { mutableStateOf("") }
    var status by rememberSaveable(collection) { mutableStateOf("") }
    var customer by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("customer_id")) }
    var billing by rememberSaveable(collection) { mutableStateOf("") }
    var inactive by rememberSaveable(collection) { mutableStateOf(false) }
    var from by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("date_from")) }
    var to by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("date_to")) }
    var offset by rememberSaveable(collection) { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val listState = rememberLazyListState()
    var seenCustomerEntry by rememberSaveable(collection) { mutableIntStateOf(-1) }
    LaunchedEffect(controller.customerListEntry) {
        if (collection == "customers" && seenCustomerEntry != controller.customerListEntry) { offset = 0; listState.scrollToItem(0); seenCustomerEntry = controller.customerListEntry }
    }
    val flow = remember(collection, search, status, customer, billing, inactive, from, to, offset) { controller.store.nativeData().watchList(collection, search = search, status = status, customerId = customer, billingType = billing, includeInactive = inactive, offset = offset, limit = 50, dateFrom = from, dateTo = to).nativeErrors(controller) }
    val page by flow.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, offset, 50))
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { NativeField("Suchen", search, { search = it; offset = 0 }) }
        item { Button({ controller.navigate(collection, mode = "new") }) { Text(when (collection) { "customers" -> "Kunde anlegen"; "orders" -> "Auftrag anlegen"; else -> "Arbeit erfassen" }) } }
        item {
            if (collection == "customers") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Archivierte Kunden einbeziehen"); Switch(inactive, { inactive = it; offset = 0 }) }
                NativeChoice("Abrechnungsweg", billing, listOf("" to "Alle", "direct" to "Kunde selbst", "via_customer" to "Anderer Rechnungsempfänger"), { billing = it; offset = 0 })
            } else {
                NativeRecordPicker(controller, "customers", "Kunde", customer, { customer = it; offset = 0 })
                if (collection == "orders") NativeChoice("Status", status, listOf("" to "Alle", "Geplant" to "Geplant", "In Arbeit" to "In Arbeit", "Wartet auf Material" to "Wartet auf Material", "Erledigt" to "Erledigt", "Abgerechnet" to "Abgerechnet"), { status = it; offset = 0 })
                if (collection == "worklogs") {
                    NativeField("Ab Datum (JJJJ-MM-TT)", from, { from = it; offset = 0 }); NativeField("Bis Datum (JJJJ-MM-TT)", to, { to = it; offset = 0 })
                    OutlinedButton({ controller.action("Wochen, Arbeitszeiten und Qualität prüfen", "get_work_record_summary", JSONObject().apply { if (customer.isNotEmpty()) put("customer_id", customer); if (from.isNotEmpty()) put("date_from", from); if (to.isNotEmpty()) put("date_to", to) }) }) { Text("Zusammenfassung / Qualität") }
                }
            }
        }
        item { Text("${page.total} Treffer · ${page.offset + if (page.rows.isEmpty()) 0 else 1}–${page.offset + page.rows.size}") }
        if (search.isNotEmpty() || status.isNotEmpty() || customer.isNotEmpty() || billing.isNotEmpty() || from.isNotEmpty() || to.isNotEmpty() || inactive) item {
            TextButton({ search = ""; status = ""; customer = ""; billing = ""; from = ""; to = ""; inactive = false; offset = 0 }) { Text("Filter zurücksetzen") }
        }
        items(page.rows, key = { it.id }) { row ->
            Card(Modifier.fillMaxWidth().clickable { controller.navigate(collection, row.id) }) {
                Row(Modifier.padding(12.dp)) {
                    if (collection == "worklogs") Checkbox(row.id in selected, { check -> selected = if (check) selected + row.id else selected - row.id })
                    Column { Text(row.title, style = MaterialTheme.typography.titleMedium); if (row.subtitle.isNotBlank()) Text(row.subtitle); if (row.address.isNotBlank()) Text(row.address); if (row.workDate.isNotBlank()) Text(row.workDate); if (row.status.isNotBlank()) Text(nativeValue(row.status)) }
                }
            }
        }
        if (page.rows.isEmpty()) item { Text("Keine passenden Einträge.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (offset > 0) OutlinedButton({ offset = maxOf(0, offset - 50) }) { Text("Vorherige 50") }; if (page.hasMore) OutlinedButton({ offset += 50 }) { Text("Weitere 50") } } }
        if (collection == "worklogs" && selected.isNotEmpty()) item {
            Button({ scope.launch {
                try {
                    val preview = controller.query("preview_archive_work_records", JSONObject().put("work_record_ids", JSONArray(selected)))
                    controller.action("${selected.size} Arbeiten in den Papierkorb", "archive_work_records", JSONObject().put("work_record_ids", JSONArray(selected)).put("expected_preview_hash", preview.getString("preview_hash")).put("_preview", preview))
                } catch (failure: Exception) { controller.message(failure.message ?: "Auswahl konnte nicht geprüft werden.") }
            } }) { Text("Auswahl in Papierkorb (${selected.size})") }
        }
    }
}

@Composable
private fun NativeEntityDetail(controller: NativeAppController, collection: String, id: String) {
    val kind = primaryKind(collection)
    var record by remember(id) { mutableStateOf<JSONObject?>(null) }
    var envelope by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var historyOffset by remember(id) { mutableIntStateOf(0) }
    val relatedFlow = remember(collection, id, historyOffset) { controller.store.nativeData().watchList("worklogs", customerId = if (kind == "customer") id else "", orderId = if (kind == "order") id else "", offset = historyOffset, limit = 20).nativeErrors(controller) }
    val relatedPage by relatedFlow.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, historyOffset, 20))
    val related = relatedPage.rows
    val scope = rememberCoroutineScope()
    LaunchedEffect(collection, id) {
        controller.store.nativeData().watchDetail(collection, id).nativeErrors(controller).collectLatest { changed ->
            try {
                if (changed == null) { record = null; error = "Eintrag nicht mehr vorhanden." }
                else { val loaded = controller.call(primaryGet(kind), JSONObject().put(primaryId(kind), id)); envelope = loaded; record = loaded.optJSONObject("data"); error = "" }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Eintrag fehlt." }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error) }
        item { record?.let { Text(it.optString("name", it.optString("title", it.optString("activity", "Eintrag"))), style = MaterialTheme.typography.headlineSmall); if (kind == "worklog" && it.optString("notes").isNotBlank()) Text("Notizen: " + it.getString("notes")); NativeBusinessValues(JSONObject(it.toString()).apply { if (kind == "worklog") remove("notes") }) } }
        item { Button({ controller.navigate(collection, id, "edit") }, enabled = record != null) { Text("Bearbeiten") } }
        if (kind == "customer") item {
            val row = record
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!row?.optString("phone").isNullOrBlank()) OutlinedButton({ controller.context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(row!!.getString("phone"))))) }) { Text("Anrufen") }
                if (!row?.optString("email").isNullOrBlank()) OutlinedButton({ controller.context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(row!!.getString("email"))))) }) { Text("E-Mail") }
                OutlinedButton({ controller.navigate("photos", args = JSONObject().put("customer_id", id)) }) { Text("Kundenfotos") }
                OutlinedButton({ controller.navigate("offers", args = JSONObject().put("customer_id", id)) }) { Text("Angebote") }
                OutlinedButton({ controller.navigate("orders", args = JSONObject().put("customer_id", id)) }) { Text("Aufträge") }
            }
        }
        item { OutlinedButton({ controller.navigate("timer", args = JSONObject().put("customer_id", if (kind == "customer") id else record?.optString("customer_id", "")).apply { if (kind == "order") put("order_id", id) }) }) { Text("Arbeit beginnen") } }
        if (kind == "worklog") {
            item { OutlinedButton({ controller.navigate("worklogs", id, "duplicate") }) { Text("Als Vorlage duplizieren") } }
            item { OutlinedButton({ controller.action("Kunde / Rechnungsempfänger ändern", "reassign_work_record", JSONObject().put("work_record_id", id).put("expected_record_hash", envelope?.optString("record_hash", ""))) }) { Text("Zuordnung ändern") } }
            item { OutlinedButton({ controller.action("Abrechnungsmarkierung", "set_work_record_billed", JSONObject().put("work_record_id", id).put("expected_record_hash", envelope?.optString("record_hash", ""))) }) { Text("Abgerechnet / offen") } }
            item { OutlinedButton({ controller.action("Persönlich bezahlt", "set_personal_paid", JSONObject().put("work_record_id", id)) }) { Text("Persönlich bezahlt / b.b.") } }
        }
        if (kind == "order") item { OutlinedButton({ controller.navigate("order_review", id) }) { Text("Auftragsversionen prüfen") } }
        if (kind != "worklog") {
            item { Text("Tätigkeiten und Historie", style = MaterialTheme.typography.titleMedium) }
            items(related, key = { it.id }) { work -> TextButton({ controller.navigate("worklogs", work.id) }) { Text(listOf(work.workDate, work.title).filter { it.isNotEmpty() }.joinToString(" · ")) } }
            item { Row { if (historyOffset > 0) TextButton({ historyOffset = maxOf(0, historyOffset - 20) }) { Text("Vorherige Tätigkeiten") }; if (related.size == 20) TextButton({ historyOffset += 20 }) { Text("Weitere Tätigkeiten") } } }
        }
        item { OutlinedButton({ scope.launch { try {
            val preview = controller.query("preview_delete", JSONObject().put("entity", kind).put("entity_id", id))
            val args = JSONObject(preview.toString()).put("entity", kind).put("entity_id", id).put("_preview", preview)
            controller.action("${if (kind == "customer") "Kunde archivieren" else "In den Papierkorb"}", "archive_entity", args)
        } catch (failure: Exception) { controller.message(failure.message ?: "Archivierung konnte nicht geprüft werden.") } } }, enabled = record != null) { Text(if (kind == "customer") "Kunde archivieren" else "In Papierkorb") } }
        item { NativeActionMenu(controller, if (kind == "customer") "kunden" else if (kind == "order") "auftraege" else "arbeiten", JSONObject().put(primaryId(kind), id).put("entity_id", id).put("entity", kind)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeEntityEditor(controller: NativeAppController, collection: String, id: String, mode: String) {
    val kind = primaryKind(collection)
    val defaultDraftKey = "editor:$kind:" + if (mode == "duplicate") "duplicate:$id" else id.ifEmpty { "new" }
    val draftKey = controller.route.args.optString("_draft_key", defaultDraftKey)
    var valueText by rememberSaveable(draftKey) { mutableStateOf("{}") }
    var baseline by remember { mutableStateOf<JSONObject?>(null) }
    var changesText by rememberSaveable(draftKey) { mutableStateOf("{}") }
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var restored by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf(false) }
    var comparison by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var discard by remember { mutableStateOf(false) }
    var photoUri by rememberSaveable(draftKey) { mutableStateOf("") }
    var photoOrigin by rememberSaveable(draftKey) { mutableStateOf("{}") }
    var savedWorkId by rememberSaveable(draftKey) { mutableStateOf("") }
    var photoArgs by remember { mutableStateOf<JSONObject?>(null) }
    fun persist() { controller.saveDraft(draftKey, JSONObject().put("values", JSONObject(valueText)).put("changes", JSONObject(changesText)).put("conflict_review_required", conflict).put("selected_photo_uri", photoUri).put("photo_origin", JSONObject(photoOrigin)).put("saved_work_id", savedWorkId).apply { photoArgs?.let { put("photo_args", it) } }.apply { baseline?.let { put("base", it) }; pending?.let { put("original_args", it) } }) }
    fun change(key: String, value: Any?) { valueText = JSONObject(valueText).put(key, value ?: JSONObject.NULL).toString(); changesText = JSONObject(changesText).put(key, value ?: JSONObject.NULL).toString(); persist() }
    LaunchedEffect(draftKey) {
        try {
            val saved = controller.draft(draftKey)
            if (saved != null) { photoUri = saved.optString("selected_photo_uri"); photoOrigin = saved.optJSONObject("photo_origin")?.toString() ?: "{}"; savedWorkId = saved.optString("saved_work_id"); photoArgs = saved.optJSONObject("photo_args"); valueText = saved.optJSONObject("values")?.toString() ?: "{}"; changesText = saved.optJSONObject("changes")?.toString() ?: "{}"; baseline = saved.optJSONObject("base"); pending = saved.optJSONObject("original_args"); restored = saved.optBoolean("_restore_action_review_required"); conflict = saved.optBoolean("conflict_review_required") }
            else {
                // A route can retain Compose state after its draft was saved or
                // discarded. A fresh entry must not inherit its photo or receipt.
                photoUri = ""; photoOrigin = "{}"; savedWorkId = ""; photoArgs = null; changesText = "{}"
                val row = if (id.isEmpty()) JSONObject(controller.route.args.toString()) else controller.query(primaryGet(kind), JSONObject().put(primaryId(kind), id))
                baseline = if (mode != "duplicate" && id.isNotEmpty()) JSONObject(row.toString()) else null
                val value = JSONObject(row.toString())
                if (mode == "duplicate") { for (key in listOf("id", "created_at", "updated_at", "billed", "_sync_base", "_mcp_base")) value.remove(key); value.put("work_date", nativeDay()) }
                if (!value.has("country") && kind == "customer") value.put("country", "AT")
                if (kind == "worklog" && !value.has("work_date")) value.put("work_date", nativeDay())
                value.put("_request_id", UUID.randomUUID().toString()); valueText = value.toString()
            }
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "Formular konnte nicht geöffnet werden." }
    }
    suspend fun finishSaved(saved: String) {
        savedWorkId = saved; pending = null; persist(); controller.flushDrafts()
        if (photoUri.isNotEmpty()) {
            val values = JSONObject(valueText)
            val args = photoArgs ?: JSONObject().put("_photo_request_id", values.getString("_request_id") + "_photo")
                .put("customer_id", values.getString("customer_id")).put("worklog_id", saved)
                .put("order_id", values.optString("order_id")).put("role", "documentation")
                .put("caption", "").put("customer_visible", values.optInt("customer_visible", 1) != 0)
                .put("_capture_context", JSONObject(photoOrigin))
            photoArgs = args; persist(); controller.flushDrafts()
            if (restored) {
                val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", "savePhoto").put("original_args", args))
                check(proof.optString("state") == "confirmed") { "Arbeit ist gespeichert. Das übernommene Foto zuerst in der Fotoliste prüfen." }
            } else controller.importPhoto(Uri.parse(photoUri), args)
        }
        nativeDeletePhotoDraft(controller, photoUri)
        controller.removeDraft(draftKey)
        controller.message("Lokal gespeichert."); controller.navigate(collection, saved)
    }
    fun save(frozen: JSONObject? = null) {
        scope.launch {
            busy = true; error = ""
            try {
                if (savedWorkId.isNotEmpty()) finishSaved(savedWorkId)
                else {
                    val args = frozen ?: JSONObject(valueText).apply { baseline?.let { put("_edit_base", it); put("_edit_changes", JSONObject(changesText)) } }
                    require(photoUri.isEmpty() || args.optString("customer_id").isNotEmpty()) { "Für das Foto zuerst einen Kunden wählen." }
                    pending = JSONObject(args.toString()); persist(); controller.flushDrafts()
                    val saved = controller.saveEditor(kind, args)
                    finishSaved(saved)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = (if (savedWorkId.isNotEmpty()) "Arbeit ist gespeichert. Foto oder Entwurf: " else "") + (failure.message ?: "Speicherergebnis ungeklärt. Angaben bleiben erhalten.")
                if (savedWorkId.isEmpty() && (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed" || failure is PixelMcpWriteTools.Rejected || failure is org.json.JSONException)) {
                    pending = null
                    if (failure is NativeCallFailure && failure.detail.optString("code").contains("CONFLICT")) conflict = true
                    persist()
                }
            } finally { busy = false }
        }
    }
    fun choosePhoto(camera: Boolean) {
        scope.launch {
            busy = true
            try {
                controller.pickPhoto(camera)?.let { selected ->
                    val retained = nativeRetainPhotoDraft(controller, selected)
                    val previous = photoUri
                    photoOrigin = withContext(Dispatchers.IO) { val identity = BackupDeviceGuard(controller.context).current(); JSONObject().put("formatVersion", 1).put("installation_id", identity.getString("installationId")).put("restore_epoch", identity.getString("restoreEpoch")).toString() }
                    photoUri = retained.toString(); photoArgs = null; persist(); controller.flushDrafts()
                    nativeDeletePhotoDraft(controller, previous)
                    withContext(Dispatchers.IO) { PhotoCaptureProvider.release(controller.context, selected) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Foto konnte nicht ausgewählt werden." }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(when (kind) { "customer" -> if (id.isEmpty()) "Kunde anlegen" else "Kunde bearbeiten"; "order" -> "Auftrag bearbeiten"; else -> "Arbeit erfassen" }, style = MaterialTheme.typography.headlineSmall) }
        item { if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error); if (busy || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (savedWorkId.isNotEmpty()) {
            item { Text("Die Arbeit ist gespeichert. Das Foto abschließen oder die verbleibenden ungespeicherten Eingaben verwerfen."); Button({ save() }, enabled = !busy) { Text("Foto abschließen") } }
        } else if (restored || conflict || pending != null) {
            item { Text(when { conflict -> "Der Eintrag wurde zwischenzeitlich verändert. Vergleiche den aktuellen Stand mit deinen Eingaben."; restored -> "Wiederhergestellter Entwurf: zuerst den gespeicherten Vorgang prüfen."; else -> "Das Speicherergebnis ist ungeklärt. Die ursprünglichen Angaben bleiben unverändert." }); NativeBusinessValues(JSONObject(valueText)) }
            if (pending != null) item { Button({ scope.launch {
                busy = true
                try {
                    val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", when (kind) { "customer" -> "saveCustomer"; "order" -> "saveOrder"; else -> "saveWorklog" }).put("original_args", pending))
                    if (proof.optString("state") == "confirmed") { finishSaved(proof.getString("entity_id")) }
                    else if (!restored) save(pending) else error = "Kein eindeutiger Speicherbeleg. Den Entwurf nicht erneut ausführen."
                } catch (failure: Exception) { error = failure.message ?: "Aktionsbeleg nicht lesbar." } finally { busy = false }
            } }, enabled = !busy) { Text("Ursprünglichen Vorgang prüfen") } }
            if ((restored || conflict) && pending == null) item {
                OutlinedButton({ scope.launch {
                    busy = true
                    try { comparison = if (id.isEmpty() || mode == "duplicate") JSONObject() else controller.query(primaryGet(kind), JSONObject().put(primaryId(kind), id)); error = "" }
                    catch (failure: Exception) { error = failure.message ?: "Aktueller Stand konnte nicht gelesen werden." }
                    finally { busy = false }
                } }, enabled = !busy) { Text("Aktuellen Stand zum Vergleichen lesen") }
                comparison?.let { now ->
                    Text("Aktuell gespeicherter Stand", style = MaterialTheme.typography.titleMedium)
                    NativeBusinessValues(now)
                    Button({ scope.launch {
                        busy = true
                        try {
                            val reviewed = if (id.isEmpty() || mode == "duplicate") JSONObject(valueText) else JSONObject(now.toString()).apply {
                                val changes = JSONObject(changesText)
                                for (key in changes.keys().asSequence()) put(key, changes.get(key))
                            }
                            val nextBase = if (id.isEmpty() || mode == "duplicate") null else JSONObject(now.toString())
                            reviewed.put("_request_id", UUID.randomUUID().toString())
                            controller.adoptDraft(draftKey, JSONObject().put("values", reviewed).put("changes", JSONObject(changesText)).apply { nextBase?.let { put("base", it) } })
                            valueText = reviewed.toString(); baseline = nextBase; pending = null; restored = false; conflict = false; comparison = null
                        } catch (failure: Exception) { error = failure.message ?: "Entwurf konnte nicht übernommen werden." }
                        finally { busy = false }
                    } }, enabled = !busy) { Text("Geprüfte Eingaben als neuen Entwurf fortsetzen") }
                }
            }
        } else if (loaded) {
            val v = JSONObject(valueText)
            val textFields = when (kind) {
                "customer" -> listOf("name", "company", "first_name", "last_name", "street", "postal_code", "city", "country", "phone", "email", "garden_area", "sevdesk_customer_number", "notes")
                "order" -> listOf("title", "description", "location", "scheduled_date")
                else -> listOf("work_date", "start_time", "end_time", "break_minutes", "activity", "disposal_cost")
            }
            if (kind != "customer") item { NativeRecordPicker(controller, "customers", "Kunde", v.optString("customer_id"), { change("customer_id", it) }) }
            items(textFields) { field ->
                val entered = v.opt(field)?.takeUnless { it == JSONObject.NULL }?.toString() ?: ""
                when (field) {
                    "break_minutes" -> NativeAmountPresets("Pause (Minuten)", "Freie Minuten", entered, listOf("15", "30", "45", "60"), "") { change(field, it.toIntOrNull() ?: it) }
                    "disposal_cost" -> NativeAmountPresets("Entsorgung (€) · optional", "Freier Betrag (€)", entered, listOf("50", "100", "150"), "€") { change(field, it) }
                    else -> {
                        NativeField(when (field) { "activity" -> "Was wurde erledigt?"; "hours" -> "Arbeitsdauer in Stunden (ohne Beginn/Ende; netto)"; else -> nativeLabel(field) }, entered, { input ->
                            change(field, if (field == "hours") input.replace(',', '.').toDoubleOrNull() ?: input else input)
                        }, required = field in listOf("name", "title", "activity", "work_date"), multiline = field in listOf("notes", "description", "activity"))
                        if (field == "activity") NativeSuggestions("Textbausteine", listOf("Gartenpflege", "Rasen gemäht", "Hecke zurückgeschnitten", "Sträucher zurückgeschnitten", "Laub entfernt", "Beete gepflegt", "Reinigung durchgeführt", "Schnittgut aufgeräumt")) { phrase ->
                            change("activity", listOf(JSONObject(valueText).optString("activity"), phrase).filter { it.isNotBlank() }.joinToString("\n"))
                        }
                    }
                }
            }
            if (kind == "worklog") {
                item {
                    Text("Fotos · optional", style = MaterialTheme.typography.titleSmall)
                    if (photoUri.isNotEmpty()) AsyncImage(Uri.parse(photoUri), "Ungespeichertes Foto", imageLoader = controller.imageLoader, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ choosePhoto(true) }, enabled = !busy) { Text("Aufnehmen") }; OutlinedButton({ choosePhoto(false) }, enabled = !busy) { Text("Foto auswählen") } }
                }
                item {
                    NativeRecordPicker(controller, "orders", "Auftrag (optional)", v.optString("order_id"), { change("order_id", it) })
                    for (field in listOf("hours", "material", "material_cost", "kilometers", "location", "notes")) NativeField(if (field == "hours") "Arbeitsdauer in Stunden (nur ohne Beginn/Ende; netto)" else nativeLabel(field), v.opt(field)?.takeUnless { it == JSONObject.NULL }?.toString() ?: "", { entered ->
                        change(field, if (field in listOf("hours", "material_cost", "kilometers")) entered.replace(',', '.').toDoubleOrNull() ?: entered else entered)
                    }, multiline = field in listOf("notes", "material"))
                }
            }
            item { OutlinedButton({ controller.speech { spoken -> val key = if (kind == "worklog") "activity" else "notes"; change(key, listOf(JSONObject(valueText).optString(key), spoken).filter { it.isNotBlank() }.joinToString(" ")) } }) { Text("Spracheingabe") } }
            if (kind != "customer") item { OutlinedButton({ scope.launch { try { val location = controller.location(); val address = location.optJSONObject("address"); val readable = if (address != null) listOf(listOf(address.optString("street"), address.optString("house_number")).filter { it.isNotBlank() }.joinToString(" "), listOf(address.optString("postal_code"), address.optString("city")).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ") else location.optString("address"); if (readable.isNotBlank()) change("location", readable) else error = location.optString("message", "Standort nicht verfügbar.") } catch (failure: Exception) { error = failure.message ?: "Standort nicht verfügbar." } } }) { Text("Aktuellen Standort übernehmen") } }
            item {
                NativeRecordPicker(controller, "customers", "Rechnungsempfänger", v.optString(if (kind == "customer") "default_billing_customer_id" else "billing_customer_id"), { change(if (kind == "customer") "default_billing_customer_id" else "billing_customer_id", it) })
                if (kind == "customer") {
                    NativeChoice("Abrechnungsweg", v.optString("billing_mode", "direct"), listOf("direct" to "Kunde selbst", "via_customer" to "Anderer Rechnungsempfänger"), { change("billing_mode", it) })
                    Row { Text("Aktiv", Modifier.weight(1f)); Switch(v.optBoolean("active", v.optInt("active", 1) != 0), { change("active", if (it) 1 else 0) }) }
                } else if (kind == "order") NativeChoice("Status", v.optString("status", "Geplant"), listOf("Geplant" to "Geplant", "In Arbeit" to "In Arbeit", "Wartet auf Material" to "Wartet auf Material", "Erledigt" to "Erledigt", "Abgerechnet" to "Abgerechnet"), { change("status", it) })
                if (kind in listOf("order", "worklog")) Row { Text("In Kundenansicht zeigen", Modifier.weight(1f)); Switch(v.optBoolean("customer_visible", v.optInt("customer_visible", 1) != 0), { change("customer_visible", if (it) 1 else 0) }) }
                if (kind == "worklog") Row { Text("Arbeit über Mitternacht", Modifier.weight(1f)); Switch(v.optBoolean("allow_overnight"), { change("allow_overnight", it) }) }
            }
            if (kind == "customer") item { NativeCustomerExtras(v.opt("custom_fields"), { change("custom_fields", it) }) }
        }
        item { OutlinedButton({ controller.back() }, enabled = !busy) { Text("Entwurf behalten und zurück") } }
    }
        FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton({ discard = true }, enabled = loaded && !busy && pending == null) { Text("Verwerfen", maxLines = 1) }
            Button({ save() }, enabled = loaded && !busy && !restored && !conflict && pending == null && savedWorkId.isEmpty()) { Text("Speichern", maxLines = 1) }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { if (!busy) discard = false }, text = { Text("Ungespeicherte Eingaben und Foto verwerfen?") },
        confirmButton = { TextButton({ scope.launch {
            busy = true
            try { nativeDeletePhotoDraft(controller, photoUri); controller.removeDraft(draftKey); discard = false; controller.back() }
            catch (failure: Exception) { error = failure.message ?: "Entwurf konnte nicht verworfen werden." }
            finally { busy = false }
        } }, enabled = !busy) { Text("OK") } },
        dismissButton = { TextButton({ discard = false }, enabled = !busy) { Text("Abbrechen") } })
}

@Composable
private fun NativeCustomerExtras(raw: Any?, change: (JSONObject) -> Unit) {
    val values = when (raw) { is JSONObject -> raw; is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject()); else -> JSONObject() }
    var name by remember { mutableStateOf("") }
    Text("Zusatzfelder", style = MaterialTheme.typography.titleMedium)
    for (key in values.keys().asSequence().toList()) {
        NativeField(key, values.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString() ?: "", { change(JSONObject(values.toString()).put(key, it)) })
        TextButton({ change(JSONObject(values.toString()).apply { remove(key) }) }) { Text("$key entfernen") }
    }
    NativeField("Neues Zusatzfeld", name, { name = it })
    OutlinedButton({ if (name.isNotBlank()) { change(JSONObject(values.toString()).put(name.trim(), "")); name = "" } }) { Text("Zusatzfeld ergänzen") }
}

@Composable
private fun NativeTimer(controller: NativeAppController) {
    var session by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var pendingRestored by remember { mutableStateOf(false) }
    var cancelledId by rememberSaveable { mutableStateOf("") }
    var cancelledReview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() { session = withContext(Dispatchers.IO) { controller.store.workSessions().current() } }
    LaunchedEffect(Unit) {
        try {
            controller.draft("timer_cancelled")?.let { cancelledId = it.optString("session_id"); cancelledReview = it.optBoolean("_restore_action_review_required") }
            controller.draft("timer_action")?.let { saved -> saved.optJSONObject("original_args")?.let { pending = saved.getString("original_tool") to it; pendingRestored = saved.optBoolean("_restore_action_review_required") } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Die letzte Sitzung konnte nicht gelesen werden." }
        while (isActive) {
            try { refresh() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Zeiterfassung nicht verfügbar." }
            delay(1000)
        }
    }
    fun execute(tool: String, explicitSession: String = "") {
        scope.launch {
            if (busy) return@launch
            busy = true
            try {
                val previous = pending
                if (previous != null && previous.first != tool) throw IllegalStateException("Zuerst den ungeklärten Vorgang prüfen.")
                val args = previous?.second ?: JSONObject().put("request_id", UUID.randomUUID().toString()).put("session_id", explicitSession.ifEmpty { session?.optString("id", session?.optString("session_id", "") ?: "") ?: "" })
                if (previous != null) {
                    val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", args))
                    refresh()
                    if (proof.optString("state") == "confirmed") {
                        controller.removeDraft("timer_action"); pending = null; pendingRestored = false; error = "Gespeicherten Vorgang gefunden."; return@launch
                    }
                    if (pendingRestored || proof.optString("state") != "unknown") throw IllegalStateException("Kein eindeutiger Speicherbeleg. Den übernommenden Vorgang nicht erneut ausführen.")
                }
                pending = tool to JSONObject(args.toString())
                controller.saveDraft("timer_action", JSONObject().put("original_tool", tool).put("original_args", args)); controller.flushDrafts()
                controller.call(tool, args)
                if (tool == "discard_work_session") {
                    cancelledId = args.getString("session_id"); cancelledReview = false
                    controller.saveDraft("timer_cancelled", JSONObject().put("session_id", cancelledId)); controller.flushDrafts()
                } else if (tool == "restore_work_session") {
                    cancelledId = ""; cancelledReview = false; controller.removeDraft("timer_cancelled")
                }
                controller.removeDraft("timer_action"); pending = null; pendingRestored = false; refresh(); error = ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = failure.message ?: "Ergebnis ungeklärt. Dieselbe Aktion erneut prüfen."
                if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") {
                    controller.removeDraft("timer_action"); pending = null; pendingRestored = false
                }
            } finally { busy = false }
        }
    }
    val state = session?.optString("state", "IDLE")
    if (pending != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Der ursprüngliche Zeitvorgang wird mit derselben Kennung geprüft.")
            session?.let { NativeBusinessValues(it) }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button({ execute(requireNotNull(pending).first) }, enabled = !busy) { Text("Ursprünglichen Vorgang prüfen") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    if (state in listOf("IDLE", "UNAVAILABLE", "FINALIZED")) {
        Column(Modifier.fillMaxSize()) {
            if (error.isNotEmpty()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            if (cancelledId.isNotEmpty()) OutlinedButton({ execute("restore_work_session", cancelledId) }, enabled = !busy) { Text(if (cancelledReview) "Übernommene verworfene Sitzung prüfen und zurückholen" else "Letzte verworfene Sitzung zurückholen") }
            Box(Modifier.weight(1f)) { NativeActionForm(controller, "start_work", "Arbeit beginnen", controller.route.args, onDone = { scope.launch { refresh() } }) }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        val value = session
        if (value == null) CircularProgressIndicator() else {
            Text(when (state) { "RUNNING" -> "Arbeit läuft"; "PAUSED" -> "Pause läuft"; "STOPPED_PENDING_REVIEW" -> "Arbeit gestoppt"; "CANCELLED" -> "Verworfene Arbeit"; else -> "Zeiterfassung" }, style = MaterialTheme.typography.headlineSmall)
            NativeBusinessValues(value)
            when (state) {
                "RUNNING" -> {
                    Button({ execute("start_break") }, enabled = !busy) { Text("Pause starten") }
                    Button({ execute("stop_work") }, enabled = !busy) { Text("Arbeit stoppen") }
                    OutlinedButton({ controller.action("Stoppen und Tätigkeit speichern", "finish_work", JSONObject().put("session_id", value.optString("id", value.optString("session_id")))) }) { Text("Stoppen und speichern") }
                }
                "PAUSED" -> { Button({ execute("end_break") }, enabled = !busy) { Text("Pause beenden") }; Button({ execute("stop_work") }, enabled = !busy) { Text("Arbeit stoppen") } }
                "STOPPED_PENDING_REVIEW" -> {
                    Button({ execute("continue_work") }, enabled = !busy) { Text("Arbeit fortsetzen") }
                    Button({ controller.action("Geprüfte Zeit speichern", "complete_work_session", JSONObject().put("session_id", value.optString("id", value.optString("session_id")))) }) { Text("Zeit prüfen und speichern") }
                    OutlinedButton({ execute("discard_work_session") }, enabled = !busy) { Text("Sitzung verwerfen") }
                }
                "CANCELLED" -> Button({ execute("restore_work_session") }, enabled = !busy) { Text("Sitzung zurückholen") }
            }
            if (state in listOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")) {
                val resourceContext = JSONObject().put("session_id", value.optString("id", value.optString("session_id"))).apply {
                    for (field in listOf("customer_id", "order_id", "property_id", "visit_id")) if (value.optString(field).isNotEmpty()) put(field, value.getString(field))
                }
                OutlinedButton({ controller.navigate("materials", mode = "movement", args = resourceContext) }) { Text("Materialverbrauch zur Arbeit erfassen") }
                OutlinedButton({ controller.navigate("equipment", mode = "equipment_use", args = resourceContext) }) { Text("Gerätenutzung zur Arbeit erfassen") }
            }
            if (value.optBoolean("review_required")) Button({ controller.action("Zeit nach Neustart prüfen", "review_work_session", JSONObject().put("session_id", value.optString("id", value.optString("session_id"))).put("expected_revision_hash", value.optString("revision_hash"))) }) { Text("Zeit ausdrücklich prüfen") }
        }
    }
}

@Composable
private fun NativeSettings(controller: NativeAppController) {
    var settings by remember { mutableStateOf<JSONObject?>(null) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { settings = controller.query("get_settings"); info = controller.query("get_capabilities") } catch (failure: Exception) { error = failure.message.orEmpty() } }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Haus & Garten ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}"); Text("SQLite ${PixelLocalStore.SCHEMA_VERSION} · Kotlin / Compose") }
        item { if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error); settings?.let { NativeBusinessValues(it) } }
        item { Button({ controller.action("Gerätestandards ändern", "update_settings", JSONObject().put("expected_record_hash", settings?.optString("record_hash", "")).put("updates", settings?.optJSONObject("settings") ?: JSONObject())) }) { Text("Tätigkeit, Einsatzort und Fotoqualität") } }
        item { Button({ controller.action("Unternehmensdaten lesen", "get_business_settings") }) { Text("Unternehmensdaten") } }
        item { Button({ scope.launch { try { val current = controller.query("get_business_settings"); controller.action("Unternehmensdaten bearbeiten", "update_business_settings", JSONObject().put("expected_record_hash", current.optString("record_hash")).put("updates", current.optJSONObject("settings") ?: JSONObject())) } catch (failure: Exception) { error = failure.message.orEmpty() } } }) { Text("Ausstellerdaten bearbeiten") } }
        item { Button({ controller.navigate("planning") }) { Text("Planung und Wetter") } }
        item { Button({ controller.open(LocalAuthorityActivity::class.java) }) { Text("Datenübernahme und Rechnungsfreigabe prüfen") } }
        item { Button({ controller.navigate("backup") }) { Text("Sicherung") } }
        item { Button({ controller.navigate("drafts") }) { Text("Formularentwürfe und übernommene Bearbeitungen") } }
        item { Button({ controller.navigate("connections") }) { Text("Verbindungen") } }
        item { Button({ scope.launch { try { info = controller.query("run_self_test"); error = "" } catch (failure: Exception) { error = failure.message.orEmpty() } } }) { Text("App-Selbsttest") } }
        item { info?.let { NativeBusinessValues(it) } }
        item { NativeActionMenu(controller, "einstellungen") }
    }
}

@Composable
private fun NativeTrash(controller: NativeAppController) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(offset, refresh) { try { data = controller.query("list_archive", JSONObject().put("offset", offset).put("limit", 40)); error = "" } catch (failure: Exception) { error = failure.message.orEmpty() } }
    val rows = data?.optJSONArray("items") ?: data?.optJSONArray("archive") ?: data?.optJSONArray("entries") ?: JSONArray()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Papierkorb", style = MaterialTheme.typography.headlineSmall); if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error) }
        items((0 until rows.length()).map { rows.getJSONObject(it) }) { row ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                NativeBusinessValues(row)
                val archived = row.optJSONObject("entry") ?: row
                val archiveKey = row.optString("archive_key")
                val entity = if (archiveKey.contains(':')) archiveKey.substringBefore(':') else archived.optString("entity", archived.optString("entity_type"))
                val id = if (archiveKey.contains(':')) archiveKey.substringAfter(':') else archived.optString("entity_id", archived.optString("id"))
                Button({ controller.action("Eintrag wiederherstellen", "restore_entity", JSONObject().put("entity", entity).put("entity_id", id).put("expected_record_hash", row.optString("record_hash"))) }) { Text("Wiederherstellen") }
                OutlinedButton({ scope.launch { try {
                    val preview = controller.query("preview_purge", JSONObject().put("entity", entity).put("entity_id", id))
                    controller.action("Eintrag endgültig löschen", "purge_entity", JSONObject(preview.toString()).put("entity", entity).put("entity_id", id).put("_preview", preview))
                } catch (failure: Exception) { error = failure.message.orEmpty() } } }) { Text("Endgültige Löschung prüfen") }
            } }
        }
        item { Row { if (offset > 0) OutlinedButton({ offset = maxOf(0, offset - 40) }) { Text("Vorherige") }; if (rows.length() == 40) OutlinedButton({ offset += 40 }) { Text("Weitere Einträge") } } }
        item { OutlinedButton({ scope.launch { try { val preview = controller.query("preview_empty_trash"); controller.action("Papierkorb bewusst leeren", "empty_trash", JSONObject().put("expected_preview_hash", preview.getString("preview_hash")).put("_preview", preview)) } catch (failure: Exception) { error = failure.message.orEmpty() } } }) { Text("Papierkorb leeren prüfen") } }
        item { OutlinedButton({ controller.navigate("revier_archive") }) { Text("Revier-Archiv") } }
        item { NativeActionMenu(controller, "papierkorb") }
    }
}

@Composable
private fun NativeLocalActions(controller: NativeAppController) {
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { status = controller.query("get_sync_overview") } catch (failure: Exception) { error = failure.message.orEmpty() } }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Geschäftsdaten bleiben lokal. Frühere Vorgänge und Konflikte bleiben zur Prüfung erhalten.")
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        status?.let { NativeBusinessValues(it) }
        Button({ controller.navigate("conflict_review") }) { Text("Konflikte lesen") }
        Button({ controller.navigate("conflict_review") }) { Text("Konflikt lösen") }
        Button({ controller.navigate("backup") }) { Text("ZIP-Sicherung öffnen") }
    }
}


/** Both review workflows freeze the actual read proof; an unclear write is inspected before any retry. */
@Composable
private fun NativeReviewedWorkflow(controller: NativeAppController, orderReview: Boolean, initialId: String) {
    val section = if (orderReview) "order_review" else "conflict_review"
    if (initialId.isEmpty()) {
        NativeReviewSelection(controller, orderReview)
        return
    }
    val key = controller.route.args.optString("_draft_key").ifEmpty { "review:$section:$initialId" }
    val tool = if (orderReview) "save_reviewed_order" else "resolve_sync_conflict"
    var proof by remember(key) { mutableStateOf<JSONObject?>(null) }
    var values by remember(key) { mutableStateOf("{}") }
    var decision by remember(key) { mutableStateOf("") }
    var request by remember(key) { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by remember(key) { mutableStateOf<JSONObject?>(null) }
    var restored by remember(key) { mutableStateOf(false) }
    var needsReview by remember(key) { mutableStateOf(false) }
    var comparison by remember(key) { mutableStateOf<JSONObject?>(null) }
    var checked by remember(key) { mutableStateOf(false) }
    var comparisonChecked by remember(key) { mutableStateOf(false) }
    var busy by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun snapshot() = JSONObject().put("proof", proof ?: JSONObject.NULL).put("values", JSONObject(values)).put("decision", decision)
        .put("request_id", request).put("pending", pending ?: JSONObject.NULL).put("review_required", needsReview)
        .put("_inbox_route", JSONObject().put("section", section).put("id", initialId).put("mode", "edit"))
    fun persist() { controller.saveDraft(key, snapshot()) }
    fun change(field: String, value: Any) { values = JSONObject(values).put(field, value).toString(); checked = false; persist() }
    suspend fun readProof(): JSONObject {
        if (orderReview) {
            val read = controller.query("inspect_order_review", JSONObject().put("order_id", initialId))
            return JSONObject().apply { for (field in listOf("id", "token", "order", "current", "checks", "reasons", "pending")) if (read.has(field)) put(field, read.get(field)) }
        }
        return controller.query("get_sync_conflicts", JSONObject().put("client_id", initialId).put("limit", 1)).getJSONArray("conflicts").optJSONObject(0)
            ?: throw IllegalArgumentException("Dieser Konflikt ist nicht mehr offen. Die Liste erneut lesen.")
    }
    LaunchedEffect(key) {
        try {
            val saved = controller.draft(key)
            if (saved != null) {
                proof = saved.optJSONObject("proof"); values = saved.optJSONObject("values")?.toString() ?: "{}"
                decision = saved.optString("decision"); request = saved.optString("request_id", request); pending = saved.optJSONObject("pending")
                restored = saved.optBoolean("_restore_action_review_required"); needsReview = saved.optBoolean("review_required")
            } else { proof = readProof(); values = proof?.optJSONObject("order")?.toString() ?: "{}" }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
    }
    fun complete() {
        if (busy) return
        busy = true
        scope.launch {
            error = ""
            try {
                val original = pending
                var confirmed = false
                if (original != null) {
                    val receipt = controller.query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", original))
                    confirmed = receipt.optString("state") == "confirmed"
                    if (receipt.optString("state") == "content_removed") throw IllegalStateException("Dieser Vorgang wurde endgültig entfernt. Keine erneute Ausführung.")
                    if (!confirmed && restored) throw IllegalStateException("Kein eindeutiger Beleg für den übernommenen Vorgang. Seine Angaben bleiben erhalten.")
                }
                if (!confirmed) {
                    check(!restored && !needsReview) { "Den aktuellen Stand zuerst lesen und ausdrücklich vergleichen." }
                    val observed = requireNotNull(proof)
                    val args = original ?: JSONObject().put("request_id", request).apply {
                        if (orderReview) {
                            put("id", observed.getString("id")).put("token", observed.getString("token")).put("decision", decision).put("confirmed", true)
                            if (decision == "save") {
                                val edits = JSONObject(); val entered = JSONObject(values)
                                for (field in listOf("title", "customer_id", "billing_customer_id", "description", "location", "scheduled_date", "status", "customer_visible")) if (entered.has(field)) edits.put(field, if (field == "customer_visible") { if (entered.optBoolean(field, entered.optInt(field, 1) != 0)) 1 else 0 } else entered.get(field))
                                put("order", edits)
                            }
                        }
                        else put("client_id", observed.getString("client_id")).put("expected_action_hash", observed.getString("action_hash")).put("expected_current_hash", observed.getString("current_hash")).put("resolution", decision)
                    }
                    pending = JSONObject(args.toString()); persist(); controller.flushDrafts()
                    controller.query(tool, args)
                }
                pending = null
                runCatching { controller.removeDraft(key) }.onFailure { controller.message("Entscheidung gespeichert. Entwurfsbereinigung steht noch aus.") }
                controller.message("Geprüfte Entscheidung lokal gespeichert.")
                controller.navigate(if (orderReview) "orders" else "conflict_review", if (orderReview) initialId else "")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") { pending = null; needsReview = true; checked = false; persist() }
                error = failure.message.orEmpty()
            } finally { busy = false }
        }
    }
    val observed = proof
    val locked = busy || pending != null || restored || needsReview
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (orderReview) "Auftragsversionen prüfen" else "Konfliktvarianten vergleichen", style = MaterialTheme.typography.headlineSmall); if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error) }
        if (observed == null) item { if (error.isEmpty()) CircularProgressIndicator() }
        else {
            item { Text("Aktuell gespeicherter Stand", style = MaterialTheme.typography.titleMedium); observed.optJSONObject("current")?.let { NativeReviewValues(controller, it) } ?: Text("Noch kein übernommener Eintrag vorhanden.") }
            if (orderReview) {
                item { Text("Zu prüfende Angaben", style = MaterialTheme.typography.titleMedium); NativeReviewValues(controller, JSONObject(values)); observed.optJSONArray("checks")?.let { NativeBusinessValues(JSONObject().put("checks", it)) }; observed.optJSONArray("reasons")?.let { NativeBusinessValues(JSONObject().put("reasons", it)) } }
                if (!locked) item { NativeChoice("Ausdrückliche Entscheidung", decision, listOf("save" to "Angezeigte Angaben übernehmen") + if (observed.optJSONObject("current") != null) listOf("keep" to "Gespeicherten Stand behalten") else emptyList()) { decision = it; checked = false; persist() } }
                if (!locked && decision == "save") {
                    items(listOf("title", "description", "location", "scheduled_date")) { field -> NativeField(nativeLabel(field), JSONObject(values).optString(field), { change(field, it) }, required = field == "title", multiline = field == "description") }
                    item { NativeRecordPicker(controller, "customers", "Kunde", JSONObject(values).optString("customer_id")) { change("customer_id", it) }; NativeRecordPicker(controller, "customers", "Rechnungsempfänger", JSONObject(values).optString("billing_customer_id")) { change("billing_customer_id", it) } }
                    item { NativeChoice("Status", JSONObject(values).optString("status"), listOf("Geplant", "In Arbeit", "Wartet auf Material", "Erledigt", "Abgerechnet").map { it to it }) { change("status", it) }; Row { val visible = JSONObject(values); Checkbox(visible.optBoolean("customer_visible", visible.optInt("customer_visible", 1) != 0), { change("customer_visible", if (it) 1 else 0) }); Text("In Kundenansicht zeigen", Modifier.padding(top = 12.dp)) } }
                }
            } else {
                val proposedOrder = observed.optJSONObject("action")?.takeIf { it.optString("kind") == "order" }?.optJSONObject("payload")?.optString("id").orEmpty()
                if (proposedOrder.isNotEmpty()) item { OutlinedButton({ controller.navigate("order_review", proposedOrder) }, enabled = !busy && pending == null) { Text("Auftragsangaben prüfen und korrigieren") } }
                val originalBase = observed.optJSONObject("action")?.optJSONObject("payload")?.let { it.optJSONObject("_sync_base") ?: it.optJSONObject("_mcp_base") }
                if (originalBase != null) item { Text("Ursprünglicher Stand", style = MaterialTheme.typography.titleMedium); NativeReviewValues(controller, originalBase) }
                item { Text("Vorgeschlagene Änderung", style = MaterialTheme.typography.titleMedium); observed.optJSONObject("action")?.optJSONObject("payload")?.let { NativeReviewValues(controller, it) }; Text(observed.optString("message", observed.optString("error"))) }
                if (!observed.optBoolean("resolution_available")) item { Text("Dieser Vorgang benötigt eine gesonderte Prüfung. Keine Konfliktentscheidung verfügbar.") }
                else if (!locked) item {
                    val allowed = observed.optJSONArray("allowed_resolutions")
                    NativeChoice("Ausdrückliche Entscheidung", decision, listOf("keep_current" to "Gespeicherten Stand behalten", "apply_proposed" to "Vorgeschlagene Änderung übernehmen").filter { choice -> allowed == null || (0 until allowed.length()).any { allowed.optString(it) == choice.first } }) { decision = it; checked = false; persist() }
                }
            }
            if (pending == null) item {
                OutlinedButton(enabled = !busy, onClick = { scope.launch { busy = true; try { comparison = readProof(); comparisonChecked = false; checked = false; error = "" } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message.orEmpty() } finally { busy = false } } }) { Text("Aktuellen Stand erneut zum Vergleich lesen") }
                comparison?.let { fresh ->
                    Text("Neu gelesener Stand", style = MaterialTheme.typography.titleMedium); NativeReviewValues(controller, fresh.optJSONObject("current") ?: JSONObject())
                    Row { Checkbox(comparisonChecked, { comparisonChecked = it }); Text("Mit meinen erhaltenen Angaben verglichen", Modifier.padding(top = 12.dp)) }
                    Button(enabled = comparisonChecked && !busy, onClick = { scope.launch {
                        busy = true
                        try {
                            val next = fresh.optJSONObject("order")?.let { JSONObject(it.toString()) } ?: JSONObject()
                            if (orderReview) { val base = observed.optJSONObject("order") ?: JSONObject(); val own = JSONObject(values); for (field in listOf("title", "customer_id", "billing_customer_id", "description", "location", "scheduled_date", "status", "customer_visible")) if (own.has(field) && own.opt(field)?.toString() != base.opt(field)?.toString()) next.put(field, own.get(field)) }
                            val nextRequest = UUID.randomUUID().toString()
                            val adopted = snapshot().put("proof", fresh).put("values", next).put("decision", "").put("pending", JSONObject.NULL).put("review_required", false).put("request_id", nextRequest)
                            controller.adoptDraft(key, adopted); proof = fresh; values = next.toString(); decision = ""; request = nextRequest; restored = false; needsReview = false; comparison = null; comparisonChecked = false; checked = false
                        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message.orEmpty() } finally { busy = false }
                    } }) { Text("Vergleich bestätigen und weiterbearbeiten") }
                }
            }
            if (!locked && decision.isNotEmpty()) item { Row { Checkbox(checked, { checked = it }); Text("Diese Entscheidung nach Prüfung ausdrücklich bestätigen", Modifier.padding(top = 12.dp)) } }
            item { Button(::complete, enabled = !busy && (pending != null || !locked && checked && decision.isNotEmpty())) { Text(if (pending != null) "Gespeicherten Vorgang prüfen" else "Entscheidung speichern") } }
        }
        item { OutlinedButton({ controller.back() }, enabled = !busy) { Text("Später entscheiden") } }
        if (pending == null) item { TextButton({ scope.launch { try { controller.removeDraft(key); controller.back() } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message.orEmpty() } } }, enabled = !busy) { Text("Entwurf verwerfen") } }
    }
}

@Composable
private fun NativeReviewSelection(controller: NativeAppController, orderReview: Boolean) {
    if (orderReview) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Auftrag zum Prüfen auswählen"); NativeRecordPicker(controller, "orders", "Auftrag", "") { if (it.isNotEmpty()) controller.navigate("order_review", it) }; OutlinedButton({ controller.navigate("conflict_review") }) { Text("Offene historische Vorgänge prüfen") } }
        return
    }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(offset, retry) { try { data = controller.query("get_sync_conflicts", JSONObject().put("offset", offset).put("limit", 20)); error = "" } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message.orEmpty() } }
    val rows = data?.optJSONArray("conflicts") ?: JSONArray()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Offene Konflikte", style = MaterialTheme.typography.headlineSmall); data?.let { Text("${it.optInt("total")} Vorgänge") }; if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error); TextButton({ retry++ }) { Text("Erneut lesen") } }
        items((0 until rows.length()).map { rows.getJSONObject(it) }, key = { it.getString("client_id") }) { row -> OutlinedCard(Modifier.fillMaxWidth().clickable { controller.navigate("conflict_review", row.getString("client_id")) }) { Column(Modifier.padding(12.dp)) { Text(row.optString("message", row.optString("error", "Historischen Vorgang prüfen"))); Text(if (row.optBoolean("resolution_available")) "Varianten vergleichen" else "Gesonderte Prüfung erforderlich") } } }
        if (data != null && rows.length() == 0) item { Text("Keine offenen Konflikte auf dieser Seite.") }
        item { Row { TextButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0) { Text("Vorherige") }; TextButton({ offset += 20 }, enabled = offset + rows.length() < (data?.optInt("total") ?: 0)) { Text("Weitere") } } }
    }
}

@Composable
private fun NativeReviewValues(controller: NativeAppController, row: JSONObject) {
    for ((field, collection) in listOf("customer_id" to "customers", "billing_customer_id" to "customers", "order_id" to "orders")) {
        val id = row.optString(field)
        if (id.isNotEmpty()) {
            val label by produceState("Wird gelesen …", field, id) {
                value = try { val record = controller.store.nativeData().detail(collection, id); record?.optString("name")?.ifBlank { record.optString("title") }?.ifBlank { "Zugeordneter Eintrag" } ?: "Zuordnung nicht mehr vorhanden" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { "Zuordnung derzeit nicht lesbar" }
            }
            Text(nativeLabel(field) + ": " + label)
        }
    }
    val safe = JSONObject(row.toString())
    for (field in listOf("data", "full_data", "thumbnail_data", "pdf_base64", "data_base64", "chunk_base64")) safe.remove(field)
    NativeBusinessValues(safe)
}
