package at.hausgarten.service

import android.content.Intent
import android.database.sqlite.SQLiteDatabaseLockedException
import android.net.Uri
import coil3.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.retryWhen
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val nativeSections = linkedMapOf(
    "today" to "Heute", "worklogs" to "Arbeitsaufzeichnungen", "timer" to "Zeiterfassung", "customers" to "Kunden", "properties" to "Objekte",
    "inquiries" to "Anfragen", "care_plans" to "Pflegepläne", "visits" to "Einsätze", "tour" to "Tagestour", "materials" to "Material",
    "equipment" to "Geräte", "revier_search" to "Lokale Suche", "revier_archive" to "Revier-Archiv", "orders" to "Aufträge", "offers" to "Angebote",
    "billing" to "Offene Stunden", "catalog" to "Leistungskatalog", "photos" to "Fotos", "plants" to "Pflanzen", "appointments" to "Termine", "weather" to "Wetter",
    "planning" to "Planung", "trash" to "Papierkorb", "settings" to "Einstellungen", "connections" to "Verbindungen", "backup" to "Sicherung", "storage" to "Speicher", "accounting" to "Abrechnung", "accounting_edit" to "Abrechnung bearbeiten"
)
private fun nativeDay(): String = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
private fun primaryKind(collection: String) = when (collection) { "customers" -> "customer"; "orders" -> "order"; else -> "worklog" }
private fun primaryGet(kind: String) = when (kind) { "customer" -> "get_customer"; "order" -> "get_order"; else -> "get_work_record" }
private fun primaryId(kind: String) = if (kind == "worklog") "work_record_id" else "${kind}_id"

/** Complete native route host; each feature uses the shared Kotlin repositories and writers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeApplication(controller: NativeAppController) {
    val route = controller.route
    val imeVisible = WindowInsets.isImeVisible
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
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
    BackHandler(reviewChecked && route.section != "restore_review") { controller.back() }
    Row(Modifier.fillMaxSize()) {
    if (reviewChecked && route.section != "restore_review" && landscape && !imeVisible) NativeMainNavigation(controller, rail = true)
    Scaffold(modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!imeVisible) Column(Modifier.statusBarsPadding()) {
                if (route.section != "today") Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (reviewChecked && controller.hasParentScreen() && route.section != "restore_review") NativeBackButton { controller.requestBack() }
                    Text((if (route.section in listOf("office", "more")) "Büro" else nativeSections[route.section]) ?: if (route.section == "restore_review") "Wiederhergestellte Entwürfe" else "Haus & Garten", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.headlineSmall)
                }
                if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
        bottomBar = {
            if (reviewChecked && route.section != "restore_review" && !imeVisible) Column {
                if (route.section !in listOf("today", "timer", "work", "backup", "restore_review")) NativeRunningWorkBar(controller)
                if (!landscape) NativeMainNavigation(controller)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (!reviewChecked) Column(Modifier.padding(16.dp)) { CircularProgressIndicator(); Text("Übernommene Formulare werden geprüft …") }
            else routeStates.SaveableStateProvider(controller.savedRoute()) { when (route.section) {
                "today" -> NativeToday(controller)
                "more", "office" -> NativeOffice(controller)
                "billing" -> NativeBillingOverview(controller)
                "accounting" -> NativeAccounting(controller)
                "accounting_edit" -> NativeAccountingEditor(controller, route.mode, route.id)
                "customers", "orders", "worklogs" -> when {
                    route.mode in listOf("new", "edit", "duplicate") -> NativeEntityEditor(controller, route.section, route.id, route.mode)
                    route.id.isNotEmpty() -> NativeEntityDetail(controller, route.section, route.id)
                    else -> NativeEntityList(controller, route.section)
                }
                "timer", "work" -> NativeTimer(controller)
                "properties", "inquiries", "care_plans", "visits", "tour", "materials", "equipment", "revier_search", "revier_archive" -> NativeRevierScreen(route.section, route.id, controller)
                "offers", "catalog" -> NativeFinanceScreen(route.section, route.id, controller)
                "photos", "plants" -> NativeMediaScreen(route.section, route.id, controller)
                "appointments", "weather", "planning" -> NativePlanningScreen(route.section, route.id, controller)
                "action" -> NativeActionForm(controller, route.id, route.mode, route.args)
                "settings" -> NativeSettings(controller)
                "storage" -> NativeStorageSettings(controller)
                "trash" -> NativeTrash(controller)
                "connections" -> NativeConnections(controller)
                "backup" -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Lokale ZIP-Sicherung", style = MaterialTheme.typography.headlineSmall)
                        Text("Eine unverschlüsselte ZIP-Datei mit dem vorhandenen Bestand. Das Ziel wählst du im Android-Dateidialog.")
                        Text("Die Sicherungsansicht zeigt das letzte protokollierte Rücklesen, Hinweise und die Vorschau vor einer Wiederherstellung.")
                    }
                    NativeActionBar("Sicherung öffnen", { scope.launch { try { controller.flushDrafts(); controller.open(FullBackupActivity::class.java) } catch (failure: Exception) { controller.message(failure.message ?: "Entwürfe konnten nicht gesichert werden.") } } })
                }
                "restore_review", "restored_drafts" -> NativeRestoredDraftReview(controller)
                else -> NativeRevierScreen(route.section.removePrefix("revier-"), route.id, controller)
            } }
        }
    }
}
}

/** Buttons retain an explicit overview action even while their area is selected. */
@Composable
private fun NativeMainNavigation(controller: NativeAppController, rail: Boolean = false) {
    val entries = listOf("today" to "Heute", "worklogs" to "Arbeit", "customers" to "Kunden", "office" to "Büro")
    val large = LocalDensity.current.fontScale > 1.3f
    if (rail) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.width(112.dp).fillMaxHeight().safeDrawingPadding().padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
                entries.forEach { (section, label) ->
                    val active = controller.route.area == section
                    TextButton({ controller.openSection(section) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        selected = active; stateDescription = if (active) "Ausgewählter Bereich" else "Bereich öffnen"
                    }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(
                        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) { Text(label) }
                }
            }
        }
        return
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            entries.chunked(if (large) 2 else 4).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEach { (section, label) ->
                        val active = controller.route.area == section
                        TextButton(onClick = { controller.openSection(section) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = active; stateDescription = if (active) "Ausgewählter Bereich" else "Bereich öffnen" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (large && active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (!large) Surface(color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, shape = CircleShape) {
                                    Box(Modifier.width(56.dp).height(30.dp), contentAlignment = Alignment.Center) { NativeAreaIcon(section) }
                                }
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shows the existing persisted timer outside its detail, without introducing another writer. */
@Composable
private fun NativeRunningWorkBar(controller: NativeAppController) {
    var session by remember(controller) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(controller) {
        while (isActive) {
            session = try { withContext(Dispatchers.IO) { controller.store.workSessions().current() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            delay(1000)
        }
    }
    val current = session ?: return
    if (current.optString("state") !in listOf("RUNNING", "PAUSED")) return
    val seconds = current.optLong("worked_ms").coerceAtLeast(0) / 1000
    Surface(onClick = { controller.navigate("timer") }, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text((if (current.optString("state") == "PAUSED") "Arbeit pausiert" else "Arbeit läuft") + " · " +
                "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) + " ›", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NativeOffice(controller: NativeAppController) {
    var expanded by rememberSaveable { mutableStateOf("") }
    val groups = linkedMapOf(
        "Aufträge und Anfragen" to listOf("orders", "inquiries", "properties", "care_plans"),
        "Material und Pflanzen" to listOf("materials", "plants", "photos", "catalog"),
        "Weitere Verwaltung" to listOf("revier_search", "revier_archive", "trash")
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Text("Organisieren", style = MaterialTheme.typography.titleMedium) }
        items(listOf(Triple("billing", "Offene Stunden", "Alle noch offenen Arbeiten · für sevDesk"),
            Triple("accounting", "Abrechnung", "Abgerechnet und labor clandestinus"),
            Triple("offers", "Angebote", "Entwürfe und fertige Dokumente"),
            Triple("planning", "Planung", "Tag, Woche und Tagestour"),
            Triple("equipment", "Geräte", "Wartung, Zubehör und aktuelle Kette"))) { (section,title,subtitle) ->
            NativeInfoRow(title, subtitle, { controller.navigate(section) })
        }
        item { Text("Verwalten", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium) }
        for ((title, sections) in groups) {
            item { NativeInfoRow(title, if (expanded == title) "Schließen" else "Öffnen", { expanded = if (expanded == title) "" else title }) }
            if (expanded == title) items(sections, key = { it }) { section ->
                NativeInfoRow(nativeSections[section] ?: section, onClick = { controller.navigate(section) }, modifier = Modifier.padding(start = 16.dp))
            }
        }
        item { NativeInfoRow("Einstellungen und Daten", "Verbindungen, Sicherung, Speicher und App-Prüfung", { controller.navigate("settings") }) }
    }
}

/** Existing bounded lists shared by the customer record, billing and planning. */
@Composable
internal fun NativeRelatedList(controller: NativeAppController, section: String, title: String, args: JSONObject = JSONObject()) {
    val encoded = args.toString()
    var failure by remember(encoded, section) { mutableStateOf("") }
    val flow = remember(encoded, section) { controller.store.nativeData().watchList(section,
        customerId = args.optString("customer_id"), propertyId = args.optString("property_id"), orderId = args.optString("order_id"),
        dateFrom = args.optString("date_from"), dateTo = args.optString("date_to"), status = args.optString("status"), limit = 5,
        includeInactive = false, chronological = section in listOf("appointments", "visits")).catch { failure = it.message ?: "Einträge nicht lesbar." } }
    val page by flow.collectAsStateWithLifecycle(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title + (page?.let { " · ${it.total}" } ?: ""), style = MaterialTheme.typography.titleMedium)
        if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
        else if (page == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (page!!.rows.isEmpty()) Text("Keine Einträge vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        page?.rows?.forEach { row ->
            NativeInfoRow(row.title, listOf(nativeDisplayText(row.subtitle), row.address).filter { it.isNotBlank() }.joinToString(" · "), { controller.navigate(section, row.id, args = args) })
        }
        TextButton({ controller.navigate(section, args = args) }) { Text("Alle $title") }
    }
}

/** Context is resolved from existing records, never inferred from a display name. */
@Composable
internal fun NativeContext(controller: NativeAppController, args: JSONObject, customerLabel: String = "Kunde") {
    val encoded = args.toString()
    var label by remember(encoded) { mutableStateOf("") }
    LaunchedEffect(encoded) {
        val labels = ArrayList<String>()
        for ((key, collection, title) in listOf(Triple("customer_id", "customers", customerLabel), Triple("property_id", "properties", "Objekt"), Triple("inquiry_id", "inquiries", "Anfrage"), Triple("order_id", "orders", "Auftrag"))) {
            val id = args.optString(key)
            if (id.isNotEmpty()) {
                val row = try { controller.store.nativeData().detail(collection, id) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                labels.add(title + ": " + (row?.optString("name")?.ifBlank { row.optString("title") }?.ifBlank { "Eintrag prüfen" } ?: "Eintrag prüfen"))
            }
        }
        if (args.optString("date_from").isNotEmpty() || args.optString("date_to").isNotEmpty()) labels.add(listOf(args.optString("date_from"), args.optString("date_to")).filter { it.isNotEmpty() }.map(::nativeDisplayDate).joinToString(" – "))
        label = labels.joinToString(" · ")
    }
    if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun nativeDuration(hours: Double): String {
    val minutes = kotlin.math.round(hours * 60).toLong().coerceAtLeast(0)
    return "${minutes / 60} Std. ${minutes % 60} Min."
}

/** The PDF's timer treatment uses the same persisted intervals as every other view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeTimeCard(value: JSONObject) {
    val state = value.optString("state")
    val stopped = state == "STOPPED_PENDING_REVIEW"
    val seconds = value.optLong("worked_ms").coerceAtLeast(0) / 1000
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Color.White.copy(alpha = 0.14f), contentColor = Color.White, shape = CircleShape) {
                Text(when { stopped -> "Zeit gestoppt · Abschluss offen"; state == "PAUSED" -> "Pausiert"; else -> "Zeit läuft" },
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
            Text("%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
                Modifier.fillMaxWidth(), style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Arbeitszeit ohne Pause", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (value.optLong("started_at_ms") > 0) Column {
                    val start = java.time.Instant.ofEpochMilli(value.getLong("started_at_ms")).atZone(ZoneId.of("Europe/Vienna"))
                    val pattern = if (start.toLocalDate().toString() == value.optString("work_date")) "HH:mm" else "dd.MM.yyyy · HH:mm"
                    Text("Beginn", style = MaterialTheme.typography.bodySmall)
                    Text(start.format(java.time.format.DateTimeFormatter.ofPattern(pattern)) + " Uhr", fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Pausen gesamt", style = MaterialTheme.typography.bodySmall)
                    Text(nativeNumber(value.opt("break_minutes")) + " Minuten", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Existing report supplies only locally open work, across all dates by default. */
@Composable
private fun NativeOpenHours(controller: NativeAppController, customer: String, from: String = "", to: String = "") {
    var summary by remember(customer, from, to) { mutableStateOf<JSONObject?>(null) }
    var error by remember(customer, from, to) { mutableStateOf("") }
    var expanded by rememberSaveable(customer, from, to) { mutableStateOf("") }
    val changes = remember(controller) { controller.store.nativeData().watchCommittedChanges().nativeErrors(controller) }
    val latest by changes.collectAsStateWithLifecycle(-1L)
    LaunchedEffect(customer, from, to, latest) {
        try {
            summary = controller.query("get_work_record_summary", JSONObject().put("group_by_billing_customer", true).apply {
                if (customer.isNotEmpty()) put("customer_id", customer)
                if (from.isNotEmpty()) put("date_from", from)
                if (to.isNotEmpty()) put("date_to", to)
            }); error = ""
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Offene Stunden konnten nicht gelesen werden." }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Offene Stunden", style = MaterialTheme.typography.titleMedium)
        Text(if (from.isEmpty() && to.isEmpty()) "Alle noch offenen Arbeiten · alle Daten" else "Gewählter Zeitraum", style = MaterialTheme.typography.bodySmall)
        if (error.isNotEmpty()) NativeStatus(error, NativeStatusTone.Failed)
        else if (summary == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            val groups = summary!!.optJSONArray("billing_groups") ?: JSONArray()
            val total = (0 until groups.length()).sumOf { groups.getJSONObject(it).optDouble("hours") }
            Text(nativeDuration(total) + " offen", style = MaterialTheme.typography.headlineSmall)
            if (groups.length() == 0) Text("Keine offen markierten Arbeiten.")
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                val recipient = group.optString("billing_customer_id")
                val rows = group.optJSONArray("work_records") ?: JSONArray()
                NativeInfoRow(group.optString("billing_customer_name", "Empfänger prüfen"),
                    nativeDuration(group.optDouble("hours")) + " · ${rows.length()} Arbeiten · Rechnungsempfänger",
                    { expanded = if (expanded == recipient) "" else recipient })
                if (!group.optBoolean("valid")) NativeStatus("Zuordnung vor der Übertragung prüfen", NativeStatusTone.Attention)
                if (expanded == recipient) for (j in 0 until rows.length()) {
                    val row = rows.getJSONObject(j)
                    NativeInfoRow(row.optString("work_customer_name"),
                        listOf(nativeDisplayDate(row.optString("work_date")), nativeDuration(row.optDouble("hours")), row.optString("activity")).filter { it.isNotBlank() }.joinToString(" · "),
                        { controller.navigate("worklogs", row.optString("id")) }, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Text("Die Markierung gilt nur in dieser App. Rechnungen und Zahlungen werden in sevDesk verwaltet.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NativeBillingOverview(controller: NativeAppController) {
    var customer by rememberSaveable { mutableStateOf(controller.route.args.optString("work_customer_id")) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeInfoRow("Filter", if (from.isEmpty() && to.isEmpty()) "Alle Daten" else "Zeitraum eingeschränkt", { filters = !filters })
            if (filters) {
                NativeRecordPicker(controller, "customers", "Arbeitskunde", customer, onValueChange = { customer = it })
                NativeDateField("Ab Datum", from, { from = it })
                NativeDateField("Bis Datum", to, { to = it })
                TextButton({ customer = ""; from = ""; to = "" }) { Text("Filter zurücksetzen") }
            }
            NativeOpenHours(controller, customer, from, to)
        }
        NativeActionBar("Arbeiten prüfen", {
            controller.navigate("worklogs", args = JSONObject().apply {
                if (customer.isNotEmpty()) put("customer_id", customer)
                if (from.isNotEmpty()) put("date_from", from)
                if (to.isNotEmpty()) put("date_to", to)
            })
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeToday(controller: NativeAppController) {
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var session by remember { mutableStateOf<JSONObject?>(null) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var backup by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hints by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val day = nativeDay()
    val workFlow = remember(day) {
        controller.store.nativeData().watchList("worklogs", dateFrom = day, dateTo = day, limit = 3)
            .retryWhen { failure, _ ->
                if (failure !is SQLiteDatabaseLockedException) false else { delay(75); true }
            }.nativeErrors(controller)
    }
    val works by workFlow.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, 0, 3))
    suspend fun refresh() {
        try {
            overview = controller.query("get_revier_overview", JSONObject().put("date", day))
            summary = controller.query("get_work_record_summary", JSONObject().put("date_from", day).put("date_to", day))
            backup = controller.query("get_full_backup_status"); error = ""
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Heute konnte nicht gelesen werden." }
    }
    val changes = remember(controller) {
        controller.store.nativeData().watchCommittedChanges()
            .retryWhen { failure, _ ->
                if (failure !is SQLiteDatabaseLockedException) false else { delay(75); true }
            }.nativeErrors(controller)
    }
    val committedVersion by changes.collectAsStateWithLifecycle(-1L)
    LaunchedEffect(day, committedVersion) { refresh() }
    LaunchedEffect(day) {
        while (isActive) {
            try { session = withContext(Dispatchers.IO) { controller.store.workSessions().current() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Zeiterfassung konnte nicht gelesen werden." }
            delay(1000)
        }
    }
    fun rows(array: JSONArray?) = (0 until (array?.length() ?: 0)).map { array!!.getJSONObject(it) }
    val tourIds = rows(overview?.optJSONObject("tour")?.optJSONArray("stops")).map { it.optString("id") }
    val visits = rows(overview?.optJSONArray("planned_visits")).filter { it.optString("status") !in listOf("Erledigt", "Abgesagt", "completed", "cancelled") }
        .sortedWith(compareBy<JSONObject> { tourIds.indexOf(it.optString("id")).let { index -> if (index < 0) Int.MAX_VALUE else index } }.thenBy { it.optString("window_start").ifBlank { "99:99" } })
    val state = session?.optString("state")
    val active = state in listOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")
    val stopped = state == "STOPPED_PENDING_REVIEW"
    val nowTime = java.time.LocalTime.now(ZoneId.of("Europe/Vienna")).toString().take(5)
    val next = visits.firstOrNull { row ->
        row.optString("id") != session?.optString("visit_id") &&
            row.optString("window_end").let { it.isBlank() || it >= nowTime }
    }
    val context = JSONObject().apply {
        val source = if (active) session else next
        for (key in listOf("customer_id", "order_id", "property_id")) source?.optString(key)?.takeIf { it.isNotEmpty() }?.let { put(key, it) }
        if (active) {
            put("session_id", session!!.optString("id", session!!.optString("session_id")))
            session!!.optString("visit_id").takeIf { it.isNotEmpty() }?.let { put("visit_id", it) }
        } else next?.optString("id")?.let { put("visit_id", it) }
    }
    fun timer(tool: String) {
        if (busy) return
        busy = true
        scope.launch {
            try { session = controller.timerAction(tool, context.optString("session_id")); error = ""; if (tool == "stop_work") controller.navigate("timer") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Ergebnis unbestätigt. Ursprünglichen Zeitvorgang in Arbeit prüfen." }
            finally { busy = false }
        }
    }
    fun startWork() {
        if (busy) return
        if (context.optString("customer_id").isEmpty()) { controller.navigate("timer", args = context); return }
        busy = true
        scope.launch {
            try { session = controller.startKnownWork(context); controller.navigate("timer", args = context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { controller.navigate("timer", args = context); controller.message(failure.message ?: "Start nicht bestätigt. Ursprünglichen Vorgang prüfen.") }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Heute", style = MaterialTheme.typography.headlineLarge)
                Text(LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", java.util.Locale.GERMAN)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!active) item {
                val large = LocalDensity.current.fontScale > 1.3f
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val statModifier = if (large) Modifier.fillMaxWidth() else Modifier.weight(1f)
                    Surface(statModifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (overview == null) "…" else visits.size.toString(), style = MaterialTheme.typography.headlineSmall)
                            Text("Einsätze geplant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(statModifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(summary?.let { nativeNumber(it.opt("hours")) + " Std." } ?: "…", style = MaterialTheme.typography.headlineSmall)
                            Text("Heute gespeichert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (error.isNotBlank()) item { NativeStatus(error, NativeStatusTone.Attention); TextButton({ controller.navigate("timer") }) { Text("Ursprüngliches Ergebnis prüfen") } }
            if (active) item {
                Text(if (stopped) "Arbeit noch abschließen" else "Aktuelle Arbeit", style = MaterialTheme.typography.titleMedium)
                NativeContext(controller, context)
                session!!.optString("activity").takeIf { it.isNotBlank() }?.let { Text(it) }
                NativeTimeCard(session!!)
                if (stopped) Text("Die Zeit läuft nicht weiter. Deine Eingaben bleiben erhalten.")
                if (session!!.optBoolean("review_required")) NativeStatus("Zeit vor dem Speichern ausdrücklich prüfen", NativeStatusTone.Attention)
                if (!stopped) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ controller.navigate("photos", mode = "import", args = JSONObject(context.toString()).put("_open_camera", true)) }) { Text("Foto hinzufügen") }
                    OutlinedButton({ controller.navigate("materials", mode = "movement", args = context) }) { Text("Material") }
                    if (session!!.optString("activity").isBlank()) TextButton({ controller.navigate("timer") }) { Text("Tätigkeit ergänzen") }
                }
            }
            if (!stopped) item {
                Text("Nächster Einsatz" + (next?.optString("window_start")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                if (next == null) Text(if (overview == null) "Planung wird gelesen …" else "Heute kein weiterer Einsatz geplant.") else {
                    Surface(onClick = { controller.navigate("visits", next.optString("id")) }, shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeStatus("Geplant", NativeStatusTone.Confirmed)
                            Text(next.optString("title", "Einsatz"), style = MaterialTheme.typography.titleLarge)
                            NativeContext(controller, JSONObject(next.toString()))
                            listOf(next.optString("location"), listOf(next.optString("window_start"), next.optString("window_end")).filter { it.isNotBlank() }.joinToString("–"))
                                .filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            item {
                Text("Noch zu erledigen", style = MaterialTheme.typography.titleMedium)
                NativeInfoRow("Offene Arbeiten prüfen", "Arbeitsaufzeichnungen für sevDesk", { controller.navigate("billing") })
            }
            item {
                if (!active && works.rows.isNotEmpty()) NativeStatus("Arbeit gespeichert", NativeStatusTone.Confirmed)
                Text("Gespeicherte Arbeiten", style = MaterialTheme.typography.titleMedium)
                Text(summary?.let { nativeDuration(it.optDouble("hours")) + " · " + it.optInt("work_records") + " Arbeiten" } ?: "Gespeicherte Arbeiten werden gelesen …", style = MaterialTheme.typography.bodySmall)
                works.rows.forEach { row -> NativeInfoRow(row.title, nativeDisplayText(row.subtitle), { controller.navigate("worklogs", row.id) }) }
                if (works.hasMore) TextButton({ controller.navigate("worklogs", args = JSONObject().put("date_from", day).put("date_to", day)) }) { Text("Alle heutigen Arbeiten") }
            }
            item { NativeInfoRow("Weitere Tageshinweise", if (hints) "Schließen" else "Planung, Wartung und Sicherung", { hints = !hints }) }
            if (hints) {
                item { NativeInfoRow("Planung", "Termine, Einsätze und Tagestour", { controller.navigate("planning") }) }
                item { NativeInfoRow("Gerätewartung", "${overview?.optJSONArray("maintenance_due")?.length() ?: 0} Wartungen fällig", { controller.navigate("equipment") }) }
                item { NativeInfoRow("Anfragen", "${overview?.optInt("open_inquiries") ?: 0} offene Anfragen", { controller.navigate("inquiries") }) }
                item { NativeInfoRow("Wetter", "Optionale Vorhersage öffnen", { controller.navigate("weather") }) }
                item { NativeInfoRow("Sicherung", when {
                    backup == null -> "Status noch nicht gelesen"
                    backup!!.optString("state") == "FAILED" -> "Letzte Sicherung fehlgeschlagen · Ergebnis ansehen"
                    backup!!.optString("state") == "RUNNING" -> "Sicherung läuft"
                    backup!!.optBoolean("clock_review_required") -> "Uhrzeit prüfen"
                    backup!!.optString("last_local_verified_at").isNotBlank() -> "Letztes protokolliertes Rücklesen: " + nativeDisplayDate(backup!!.getString("last_local_verified_at"))
                    else -> "Noch kein bestätigtes Rücklesen"
                }, { controller.navigate("backup") }) }
                item { TextButton({ scope.launch { refresh() } }) { Text("Tageshinweise aktualisieren") } }
            }
        }
        when (state) {
            "RUNNING" -> NativeActionBar("Zeit stoppen", { timer("stop_work") }, "Pause", { timer("start_break") }, enabled = !busy, secondaryEnabled = !busy)
            "PAUSED" -> NativeActionBar("Fortsetzen", { timer("end_break") }, "Zeit stoppen", { timer("stop_work") }, enabled = !busy, secondaryEnabled = !busy)
            "STOPPED_PENDING_REVIEW" -> NativeActionBar("Abschluss prüfen", { controller.navigate("timer") }, "Arbeit nachtragen", { controller.navigate("worklogs", mode = "new") })
            else -> NativeActionBar("Arbeit starten", { startWork() }, "Arbeit nachtragen", { controller.navigate("worklogs", mode = "new") }, enabled = session != null && !busy, secondaryEnabled = !busy)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NativeEntityList(controller: NativeAppController, collection: String) {
    var search by rememberSaveable(collection) { mutableStateOf("") }
    var status by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("status")) }
    var customer by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("customer_id")) }
    var billing by rememberSaveable(collection) { mutableStateOf("") }
    var inactive by rememberSaveable(collection) { mutableStateOf(false) }
    var from by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("date_from")) }
    var to by rememberSaveable(collection) { mutableStateOf(controller.route.args.optString("date_to")) }
    var offset by rememberSaveable(collection) { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    BackHandler(selected.isNotEmpty()) { if (!deleting) selected = emptyList() }
    fun toggleSelection(id: String) {
        if (deleting) return
        selected = when {
            id in selected -> selected - id
            selected.size < 200 -> selected + id
            else -> { controller.message("Bis zu 200 Arbeiten gleichzeitig auswählen."); selected }
        }
    }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var listFailure by remember { mutableStateOf("") }
    val flow = remember(collection, search, status, customer, billing, inactive, from, to, offset) { controller.store.nativeData().watchList(collection, search = search, status = status, customerId = customer, billingType = billing, includeInactive = inactive, offset = offset, limit = 50, dateFrom = from, dateTo = to).catch { listFailure = NATIVE_LIST_READ_ERROR } }
    val displayFlow = remember(flow) { flow.map<NativeListPage, NativeListPage?> { it }.onStart { listFailure = ""; emit(null) } }
    val loadedPage by displayFlow.collectAsStateWithLifecycle(null)
    val page = loadedPage ?: NativeListPage(emptyList(), 0, offset, 50)
    val listLoading = loadedPage == null && listFailure.isEmpty()
    val scope = rememberCoroutineScope()
    NativeSearchList("Suchen", search, { search = it; offset = 0 },
        if (listLoading) "Wird geladen …" else "${page.total} Treffer · ${page.offset + if (page.rows.isEmpty()) 0 else 1}–${page.offset + page.rows.size}",
        onFilters = { filtersOpen = !filtersOpen }, onFocus = { filtersOpen = false },
        bottomSearch = collection == "worklogs", controlsVisible = selected.isEmpty()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (listLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (listFailure.isNotEmpty()) item { Text(listFailure, color = MaterialTheme.colorScheme.error) }
        if (!imeVisible && collection != "customers") item {
            NativeContext(controller, JSONObject(controller.route.args.toString()).put("customer_id", customer).put("date_from", from).put("date_to", to))
        }
        if (filtersOpen && collection != "worklogs") item(key = "native-list-filters") {
            if (collection == "customers") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Archivierte Kunden einbeziehen", Modifier.weight(1f)); Switch(inactive, { inactive = it; offset = 0 }) }
                NativeChoice("Abrechnungsweg", billing, listOf("" to "Alle", "direct" to "Kunde selbst", "via_customer" to "Anderer Rechnungsempfänger"), { billing = it; offset = 0 })
            } else {
                NativeRecordPicker(controller, "customers", "Kunde", customer, onValueChange = { customer = it; offset = 0 })
                if (collection == "orders") NativeChoice("Status", status, listOf("" to "Alle", "Geplant" to "Geplant", "In Arbeit" to "In Arbeit", "Wartet auf Material" to "Wartet auf Material", "Erledigt" to "Erledigt", "Abgerechnet" to "Abgerechnet"), { status = it; offset = 0 })

            }
        }
        if (!imeVisible && (search.isNotEmpty() || status.isNotEmpty() || customer.isNotEmpty() || billing.isNotEmpty() || from.isNotEmpty() || to.isNotEmpty() || inactive)) item {
            TextButton({ search = ""; status = ""; customer = ""; billing = ""; from = ""; to = ""; inactive = false; offset = 0 }) { Text("Filter zurücksetzen") }
        }
        items(page.rows, key = { it.id }) { row ->
            val worklog = collection == "worklogs"
            val checked = row.id in selected
            NativeInfoRow(row.title, listOf(row.address, nativeDisplayText(row.subtitle)).filter { it.isNotBlank() }.distinct().joinToString(" · "), {
                if (worklog && selected.isNotEmpty()) {
                    toggleSelection(row.id)
                } else controller.navigate(collection, row.id, mode = if (collection == "customers" && controller.route.args.optBoolean("_note_first")) "edit" else "list", args = JSONObject(controller.route.args.toString()).put("customer_id", customer).put("date_from", from).put("date_to", to))
            }, modifier = Modifier.semantics { if (worklog) { this.selected = checked; stateDescription = if (checked) "Ausgewählt" else "Zum Auswählen gedrückt halten" } },
                leading = if (collection == "customers" || worklog) ({
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Box(Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                            if (worklog && !checked) NativeAreaIcon("worklogs")
                            else Text(if (worklog) "✓" else row.title.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                        }
                    }
                }) else null,
                onLongClick = if (worklog) ({
                    if (!deleting) {
                        keyboard?.hide(); focus.clearFocus(); filtersOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggleSelection(row.id)
                    }
                }) else null, isSelected = checked)
            if (row.status.isNotBlank() && !worklog && collection != "customers") NativeStatus(nativeValue(row.status))
        }
        if (!listLoading && listFailure.isEmpty() && page.rows.isEmpty()) item { Text("Keine Treffer. Suchtext oder Filter ändern.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (offset > 0) OutlinedButton({ offset = maxOf(0, offset - 50) }) { Text("Vorherige 50") }; if (page.hasMore) OutlinedButton({ offset += 50 }) { Text("Weitere 50") } } }
        }
        if (collection == "worklogs" && selected.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    NativeSearchSummary("${selected.size} ausgewählt")
                    NativeActions {
                        TextButton({ selected = emptyList() }, enabled = !deleting) { Text("Abbrechen") }
                        TextButton({ selected = (selected + page.rows.map { it.id }).distinct().take(200) }, enabled = !deleting && !listLoading) { Text("Seite auswählen") }
                        if (selected.size == 1) TextButton({ controller.action("Labor Clandestinus", "set_personal_paid", JSONObject().put("work_record_id", selected.single())) }, enabled = !deleting) { Text("Labor Clandestinus") }
                        if (selected.size == 1) TextButton({ controller.navigate("worklogs", selected.single(), "duplicate", controller.route.args) }, enabled = !deleting) { Text("Duplizieren") }
                        Button({ if (!deleting) { deleting = true; scope.launch {
                            try {
                                controller.deleteDirect("delete_work_records", JSONObject().put("work_record_ids", JSONArray(selected)))
                                if (page.rows.all { it.id in selected }) offset = maxOf(0, offset - 50)
                                selected = emptyList()
                                controller.message("Auswahl gelöscht.")
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { controller.message(failure.message ?: "Löschen nicht bestätigt.") }
                            finally { deleting = false }
                        } } }, enabled = !deleting) { Text(if (deleting) "Löschen …" else "Löschen") }
                    }
                }
            }
        } else if (!imeVisible || collection != "worklogs") NativeActionBar(when (collection) { "customers" -> "Kunde anlegen"; "orders" -> "Auftrag anlegen"; else -> "Arbeit nachtragen" }, {
            controller.navigate(collection, mode = "new", args = JSONObject().apply { if (collection != "customers" && customer.isNotBlank()) put("customer_id", customer) })
        })
    }
    if (collection == "worklogs" && filtersOpen) ModalBottomSheet(onDismissRequest = { filtersOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Arbeiten filtern", style = MaterialTheme.typography.titleLarge)
                NativeRecordPicker(controller, "customers", "Kunde", customer, onValueChange = { customer = it; offset = 0 })
                NativeDateField("Ab Datum", from, { from = it; offset = 0 })
                NativeDateField("Bis Datum", to, { to = it; offset = 0 })
                TextButton({ controller.action("Wochen, Arbeitszeiten und Qualität prüfen", "get_work_record_summary", JSONObject().apply { if (customer.isNotEmpty()) put("customer_id", customer); if (from.isNotEmpty()) put("date_from", from); if (to.isNotEmpty()) put("date_to", to) }); filtersOpen = false }) { Text("Zusammenfassung / Qualität") }
            }
            NativeActionBar("Ergebnisse anzeigen", { filtersOpen = false }, "Zurücksetzen", { customer = ""; from = ""; to = ""; offset = 0 })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeEntityDetail(controller: NativeAppController, collection: String, id: String) {
    val kind = primaryKind(collection)
    var record by remember(id) { mutableStateOf<JSONObject?>(null) }
    var envelope by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var historyOffset by rememberSaveable(id) { mutableIntStateOf(0) }
    var details by rememberSaveable(id) { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val relatedFlow = remember(collection, id, historyOffset) { controller.store.nativeData().watchList("worklogs", customerId = if (kind == "customer") id else "", orderId = if (kind == "order") id else "", offset = historyOffset, limit = 20).nativeErrors(controller) }
    val relatedPage by relatedFlow.collectAsStateWithLifecycle(NativeListPage(emptyList(), 0, historyOffset, 20))
    LaunchedEffect(collection, id) {
        controller.store.nativeData().watchDetail(collection, id).nativeErrors(controller).collectLatest { changed ->
            try {
                if (changed == null) { record = null; error = "Eintrag nicht mehr vorhanden." }
                else { val loaded = controller.call(primaryGet(kind), JSONObject().put(primaryId(kind), id)); envelope = loaded; record = loaded.optJSONObject("data"); error = "" }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Eintrag konnte nicht gelesen werden." }
        }
    }
    fun editWorkField(field: String) {
        controller.navigate("worklogs", id, "edit", JSONObject(controller.route.args.toString()).put("_field", field))
    }
    val row = record
    val context = JSONObject().put("customer_id", if (kind == "customer") id else row?.optString("customer_id").orEmpty()).apply { if (kind == "order") put("order_id", id) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error.isNotEmpty()) item { NativeStatus(error, NativeStatusTone.Failed) }
            if (row == null && error.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (row != null) {
                item {
                    if (kind == "worklog") NativeInfoRow("Tätigkeit", row.optString("activity"), { editWorkField("activity") })
                    else Text(row.optString("name", row.optString("title", "Eintrag")), style = MaterialTheme.typography.headlineSmall)
                    if (kind == "customer") Text(row.optString("company").ifBlank { "Kundenakte" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else NativeContext(controller, context)
                }
                if (kind != "customer") item {
                    NativeInfoRow("Abrechnung", row.opt(AccountingRules.AMOUNT)?.takeUnless { it == JSONObject.NULL }?.let(::financeMoney) ?: "Gesamtbetrag und Abrechnungsweg",
                        { controller.navigate("accounting_edit", id, collection) })
                }
                if (kind == "customer") {
                    item {
                        val address = listOf(row.optString("street"), listOf(row.optString("postal_code"), row.optString("city")).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }
                        if (address.isNotEmpty()) NativeInfoRow(address.first(), address.drop(1).joinToString("\n"))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (row.optString("phone").isNotBlank()) OutlinedButton({ controller.context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(row.getString("phone"))))) }, Modifier.heightIn(min = 48.dp)) { Text("Anrufen") }
                            if (row.optString("email").isNotBlank()) OutlinedButton({ controller.context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(row.getString("email"))))) }, Modifier.heightIn(min = 48.dp)) { Text("E-Mail") }
                            if (row.optString("street").isNotBlank()) OutlinedButton({
                                val destination = (address + row.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                                try { controller.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))) }
                                catch (_: android.content.ActivityNotFoundException) { controller.message("Keine Karten-App zum Öffnen der Adresse vorhanden.") }
                            }, Modifier.heightIn(min = 48.dp)) { Text("Route") }
                        }
                        if (row.optString("phone").isBlank() && row.optString("email").isBlank()) Text("Noch keine Telefonnummer oder E-Mail hinterlegt.")
                    }
                    if (row.optString("notes").isNotBlank()) item {
                        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color(0xfffff2d4), contentColor = Color(0xff493c21)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Hinweise zum Einsatz", style = MaterialTheme.typography.titleSmall)
                                Text(row.optString("notes"))
                            }
                        }
                    }
                    item {
                        Text("Letzte Arbeit", style = MaterialTheme.typography.titleMedium)
                        val last = relatedPage.rows.firstOrNull()
                        if (last == null) Text("Noch keine gespeicherte Arbeit.")
                        else NativeInfoRow(last.title, nativeDisplayText(last.subtitle), { controller.navigate("worklogs", last.id) })
                    }
                    item { NativeRelatedList(controller, "properties", "Objekte", JSONObject().put("customer_id", id)) }
                    item {
                        val assignment = envelope?.optJSONObject("billing_configuration")
                        Text("Arbeitskunde und Rechnungsempfänger", style = MaterialTheme.typography.titleMedium)
                        Text("Arbeitskunde: " + row.optString("name"))
                        Text("Rechnungsempfänger: " + if (assignment?.optString("mode") == "direct") "Kunde selbst" else assignment?.optString("billing_customer_name").orEmpty().ifBlank { "Zuordnung prüfen" })
                        if (assignment?.optBoolean("valid") == false) NativeStatus("Rechnungsempfänger prüfen", NativeStatusTone.Attention)
                        NativeOpenHours(controller, id)
                        TextButton({ controller.navigate("billing", args = JSONObject().put("work_customer_id", id)) }) { Text("Offene Arbeiten prüfen") }
                    }
                    item { NativeInfoRow("Fotos und Angebote", "Zum Kunden gehörende Unterlagen")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ controller.navigate("photos", args = context) }) { Text("Fotos") }
                            OutlinedButton({ controller.navigate("offers", args = context) }) { Text("Angebote") }
                        }
                    }
                    if (row.optString("notes").isBlank()) item { Text("Notiz", style = MaterialTheme.typography.titleMedium); Text("Noch keine Notiz hinterlegt.") }
                    item { NativeRelatedList(controller, "appointments", "Nächste Termine", JSONObject().put("customer_id", id).put("date_from", nativeDay())) }
                    item { NativeRelatedList(controller, "visits", "Einsätze", JSONObject().put("customer_id", id).put("date_from", nativeDay())) }
                    item { NativeInfoRow("Aufträge", "Vorhandene Aufträge dieses Kunden", { controller.navigate("orders", args = context) }) }
                    item { NativeInfoRow("Anfragen", "Vorhandene Anfragen dieses Kunden", { controller.navigate("inquiries", args = context) }) }
                } else if (kind == "worklog") {
                    item {
                        NativeStatus("Gespeicherte Arbeit", NativeStatusTone.Confirmed)
                        NativeInfoRow("Datum", nativeDisplayDate(row.optString("work_date")), { editWorkField("work_date") })
                        NativeInfoRow("Arbeitszeit", nativeDuration(row.optDouble("hours")), { editWorkField("hours") })
                        if (row.optString("start_time").isNotBlank() || row.optString("end_time").isNotBlank()) {
                            NativeInfoRow("Beginn", row.optString("start_time"), { editWorkField("start_time") })
                            NativeInfoRow("Ende", row.optString("end_time"), { editWorkField("end_time") })
                            NativeInfoRow("Pause", nativeNumber(row.opt("break_minutes")) + " Min.", { editWorkField("break_minutes") })
                            NativeInfoRow("Arbeit über Mitternacht", if (row.optBoolean("allow_overnight")) "Ja" else "Nein", { editWorkField("allow_overnight") })
                        }
                        val assignment = envelope?.optJSONObject("billing_assignment")
                        NativeInfoRow("Rechnungsempfänger", assignment?.optString("billing_customer_name").orEmpty().ifBlank { "Zuordnung prüfen" }, {
                            controller.action("Zuordnung ändern", "reassign_work_record", JSONObject().put("work_record_id", id).put("expected_record_hash", envelope?.optString("record_hash", "")))
                        })
                        NativeStatus(if (row.optInt("billed") != 0) "Lokal als abgerechnet markiert" else "Lokal offen markiert")
                        Text("Die lokale Markierung bestätigt weder Rechnung noch Zahlung in sevDesk.", style = MaterialTheme.typography.bodySmall)
                        for (field in listOf("order_id", "material", "material_cost", "disposal_cost", "kilometers", "location", "notes", "customer_visible")) {
                            val value = when (field) {
                                "order_id" -> envelope?.optString("order_title").orEmpty().ifBlank { if (row.optString(field).isBlank()) "Nicht zugeordnet" else "Zugeordneter Auftrag" }
                                "customer_visible" -> if (row.optBoolean(field, row.optInt(field, 1) != 0)) "Ja" else "Nein"
                                else -> row.opt(field)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty().ifBlank { "Hinzufügen" }
                            }
                            NativeInfoRow(nativeLabel(field), nativeDisplayText(value), { editWorkField(field) })
                        }
                    }
                    item { NativeInfoRow("Fotos", "Dokumentation dieser Arbeit", { controller.navigate("photos", args = JSONObject(context.toString()).put("worklog_id", id).put("order_id", row.optString("order_id"))) }) }
                    item { NativeInfoRow("Als Vorlage duplizieren", "Neue vergangene Arbeit vorbereiten", { controller.navigate("worklogs", id, "duplicate", controller.route.args) }) }
                    item { NativeInfoRow("Zuordnung ändern", "Arbeitskunde und Rechnungsempfänger bewusst prüfen", { controller.action("Zuordnung ändern", "reassign_work_record", JSONObject().put("work_record_id", id).put("expected_record_hash", envelope?.optString("record_hash", ""))) }) }
                    item { NativeInfoRow("Abgerechnet / offen markieren", "Ändert nur die lokale Markierung", { controller.action("Lokale Abrechnungsmarkierung", "set_work_record_billed", JSONObject().put("work_record_id", id).put("expected_record_hash", envelope?.optString("record_hash", ""))) }) }
                    item { NativeInfoRow("Labor Clandestinus", "Vorhandene lokale Angabe ändern", { controller.action("Labor Clandestinus", "set_personal_paid", JSONObject().put("work_record_id", id)) }) }
                } else {
                    item {
                        if (row.optString("status").isNotBlank()) NativeStatus(nativeValue(row.optString("status")))
                        for (field in listOf("description", "location", "scheduled_date")) if (row.optString(field).isNotBlank()) NativeInfoRow(nativeLabel(field), nativeDisplayText(row.getString(field)))
                    }
                }
                if (kind != "worklog") {
                    item { Text("Tätigkeiten und Historie", style = MaterialTheme.typography.titleMedium) }
                    items(relatedPage.rows, key = { it.id }) { work -> NativeInfoRow(work.title, nativeDisplayText(work.subtitle), { controller.navigate("worklogs", work.id) }) }
                    item { Row {
                        if (historyOffset > 0) TextButton({ historyOffset = maxOf(0, historyOffset - 20) }) { Text("Vorherige Arbeiten") }
                        if (relatedPage.hasMore) TextButton({ historyOffset += 20 }) { Text("Weitere Arbeiten") }
                    } }
                }
                item { NativeInfoRow("Weitere Angaben", if (details) "Schließen" else "Zusätzliche gespeicherte Angaben", { details = !details }) }
                if (details) item {
                    NativeBusinessValues(JSONObject(row.toString()).apply { for (key in listOf("id", "customer_id", "order_id", "billing_customer_id", "default_billing_customer_id", "_sync_base", "_mcp_base", "created_at", "updated_at", "name", "activity", "title")) remove(key) })
                    if (kind != "worklog") {
                        NativeDeleteButton(controller, kind, id, label = "Löschen") { controller.returnToList(collection) }
                        NativeActionMenu(controller, if (kind == "customer") "kunden" else "auftraege", JSONObject().put(primaryId(kind), id).put("entity_id", id).put("entity", kind))
                    }
                }
            }
        }
        if (kind != "worklog") NativeActionBar("Arbeit starten", {
            if (!starting) {
                if (context.optString("customer_id").isEmpty()) controller.navigate("timer", args = context)
                else {
                    starting = true
                    scope.launch {
                        try { controller.startKnownWork(context); controller.navigate("timer", args = context) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { controller.navigate("timer", args = context); controller.message(failure.message ?: "Start nicht bestätigt. Ursprünglichen Vorgang prüfen.") }
                        finally { starting = false }
                    }
                }
            }
        }, "Bearbeiten", { controller.navigate(collection, id, "edit", controller.route.args) }, enabled = row != null && !starting, secondaryEnabled = row != null && !starting)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeEntityEditor(controller: NativeAppController, collection: String, id: String, mode: String) {
    val kind = primaryKind(collection)
    val singleField = if (kind == "worklog" && mode == "edit") controller.route.args.optString("_field") else ""
    fun showField(field: String) = singleField.isEmpty() || singleField == field
    val defaultDraftKey = "editor:$kind:" + (if (mode == "duplicate") "duplicate:$id" else id.ifEmpty { "new" }) + if (singleField.isNotEmpty()) ":$singleField" else ""
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
    var timeMode by rememberSaveable(draftKey) { mutableStateOf("times") }
    var durationHours by rememberSaveable(draftKey) { mutableStateOf("") }
    var durationMinutes by rememberSaveable(draftKey) { mutableStateOf("") }
    var durationSeeded by rememberSaveable(draftKey) { mutableStateOf(false) }
    var extraDetails by rememberSaveable(draftKey) { mutableStateOf(false) }
    fun persist() { controller.saveDraft(draftKey, JSONObject().put("values", JSONObject(valueText)).put("changes", JSONObject(changesText)).put("time_mode", timeMode).put("duration_hours", durationHours).put("duration_minutes", durationMinutes).put("duration_seeded", durationSeeded).put("conflict_review_required", conflict).put("selected_photo_uri", photoUri).put("photo_origin", JSONObject(photoOrigin)).put("saved_work_id", savedWorkId).apply { photoArgs?.let { put("photo_args", it) } }.apply { baseline?.let { put("base", it) }; pending?.let { put("original_args", it) } }) }
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
                val value = JSONObject(row.toString()).apply { remove("_note_first") }
                if (mode == "duplicate") { for (key in listOf("id", "created_at", "updated_at", "billed", "_sync_base", "_mcp_base")) value.remove(key); value.put("work_date", nativeDay()) }
                if (!value.has("country") && kind == "customer") value.put("country", "AT")
                if (kind == "worklog" && !value.has("work_date")) value.put("work_date", nativeDay())
                value.put("_request_id", UUID.randomUUID().toString()); valueText = value.toString()
            }
            val timing = JSONObject(valueText)
            timeMode = saved?.optString("time_mode")?.takeIf { it.isNotBlank() } ?: if (timing.optString("start_time").isBlank() && timing.optDouble("hours", 0.0) > 0) "duration" else "times"
            val totalMinutes = kotlin.math.round(timing.optDouble("hours", 0.0) * 60).toInt()
            durationHours = if (saved?.has("duration_hours") == true) saved.optString("duration_hours") else if (timeMode == "duration" && totalMinutes > 0) (totalMinutes / 60).toString() else ""
            durationMinutes = if (saved?.has("duration_minutes") == true) saved.optString("duration_minutes") else if (timeMode == "duration" && totalMinutes > 0) (totalMinutes % 60).toString() else ""
            durationSeeded = if (saved?.has("duration_seeded") == true) saved.optBoolean("duration_seeded") else timeMode == "duration" || saved?.optString("duration_hours").orEmpty().isNotBlank() || saved?.optString("duration_minutes").orEmpty().isNotBlank()
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "Formular konnte nicht geöffnet werden." }
    }
    suspend fun finishSaved(saved: String, includePhoto: Boolean = true) {
        savedWorkId = saved; pending = null; persist(); controller.flushDrafts()
        if (photoUri.isNotEmpty() && !includePhoto) {
            error = "Arbeit bestätigt gespeichert. Das ausgewählte Foto ist noch nicht bestätigt."
            return
        }
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
        controller.finishAction(); controller.message("${if (kind == "customer") "Kunde" else if (kind == "order") "Auftrag" else "Arbeit"} gespeichert.")
    }
    fun durationAsHours(): Double? {
        val hours = durationHours.ifBlank { "0" }.toLongOrNull() ?: return null
        val minutes = durationMinutes.ifBlank { "0" }.toIntOrNull() ?: return null
        return if (hours >= 0 && minutes in 0..59) hours.toDouble() + minutes / 60.0 else null
    }
    fun seedDuration() {
        if (durationSeeded) return
        val value = JSONObject(valueText)
        val start = runCatching { java.time.LocalTime.parse(value.optString("start_time")) }.getOrNull()
        val end = runCatching { java.time.LocalTime.parse(value.optString("end_time")) }.getOrNull()
        val pause = value.optString("break_minutes").ifBlank { "0" }.toIntOrNull()
        val worked = if (start != null && end != null && pause != null && pause >= 0) {
            var minutes = java.time.Duration.between(start, end).toMinutes()
            if (minutes < 0 && value.optBoolean("allow_overnight")) minutes += 1440
            (minutes - pause).takeIf { it > 0 }
        } else if (start == null && end == null) value.optDouble("hours", 0.0).takeIf { it.isFinite() && it > 0 }?.let { kotlin.math.round(it * 60).toLong() } else null
        if (worked != null) {
            durationHours = (worked / 60).toString()
            durationMinutes = (worked % 60).toString()
            durationSeeded = true
        }
    }
    // The draft keeps both inputs. Only the explicit save chooses a timing representation.
    fun editorPayload(): JSONObject {
        val args = JSONObject(valueText)
        val edits = JSONObject(changesText)
        if (kind == "worklog" && (singleField.isEmpty() || singleField in setOf("hours", "start_time", "end_time", "break_minutes", "allow_overnight"))) {
            fun timing(key: String, value: Any) { args.put(key, value); edits.put(key, value) }
            if (timeMode == "duration") {
                timing("start_time", ""); timing("end_time", ""); timing("break_minutes", 0)
                timing("allow_overnight", false)
                timing("hours", durationAsHours() ?: "Ungültige Dauer")
            } else {
                timing("hours", 0)
                for (key in listOf("start_time", "end_time", "break_minutes", "allow_overnight")) {
                    if (args.has(key)) edits.put(key, args.get(key))
                }
            }
        }
        baseline?.let { args.put("_edit_base", it).put("_edit_changes", edits) }
        return args
    }
    fun save() {
        scope.launch {
            busy = true; error = ""
            try {
                if (savedWorkId.isNotEmpty()) finishSaved(savedWorkId)
                else {
                    val args = editorPayload()
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
    fun inspectPending() {
        if (busy || pending == null) return
        scope.launch {
            busy = true
            try {
                val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", when (kind) { "customer" -> "saveCustomer"; "order" -> "saveOrder"; else -> "saveWorklog" }).put("original_args", pending))
                if (proof.optString("state") == "confirmed") finishSaved(proof.getString("entity_id"), includePhoto = false)
                else error = "Ergebnis weiterhin unbestätigt. Der ursprüngliche Vorgang bleibt erhalten; es wurde keine neue Speicherung ausgeführt."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Aktionsbeleg konnte nicht gelesen werden." }
            finally { busy = false }
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
        item { Text(if (singleField.isNotEmpty()) nativeLabel(singleField) else when (kind) { "customer" -> if (id.isEmpty()) "Kunde anlegen" else "Kunde bearbeiten"; "order" -> "Auftrag bearbeiten"; else -> "Arbeit nachtragen" }, style = MaterialTheme.typography.headlineSmall) }
        item { if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error); if (busy || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (savedWorkId.isNotEmpty()) {
            item { NativeStatus("Arbeit gespeichert · Foto noch offen", NativeStatusTone.Attention); Text("Die gespeicherte Arbeit bleibt erhalten. Foto speichern oder nur den verbliebenen Fotoentwurf verwerfen.") }
        } else if (restored || conflict || pending != null) {
            item { Text(when { conflict -> "Der Eintrag wurde zwischenzeitlich verändert. Vergleiche den aktuellen Stand mit deinen Eingaben."; restored -> "Wiederhergestellter Entwurf: zuerst den gespeicherten Vorgang prüfen."; else -> "Das Speicherergebnis ist ungeklärt. Die ursprünglichen Angaben bleiben unverändert." }); NativeBusinessValues(JSONObject(valueText)) }
            if (pending != null) item { NativeStatus("Speichern nicht bestätigt", NativeStatusTone.Attention) }
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
            if (kind != "customer" && singleField.isEmpty()) item { NativeRecordPicker(controller, "customers", "Kunde", v.optString("customer_id"), onValueChange = {
                change("customer_id", it)
                if (it != v.optString("customer_id")) { change("order_id", ""); change("billing_customer_id", "") }
            }) }
            if (kind == "worklog") {
                item {
                    if (showField("work_date")) NativeDateField("Datum", v.optString("work_date"), { change("work_date", it) }, required = true)
                    if (showField("hours")) NativeChoice("Zeitangabe", timeMode, listOf("times" to "Uhrzeiten", "duration" to "Dauer"), { next ->
                        if (next != timeMode) {
                            if (next == "duration") seedDuration()
                            timeMode = next
                            persist()
                        }
                    })
                    if (timeMode == "times") {
                        if (showField("hours") || showField("start_time")) NativeTimeField("Beginn", v.optString("start_time"), { change("start_time", it) })
                        if (showField("hours") || showField("end_time")) NativeTimeField("Ende", v.optString("end_time"), { change("end_time", it) })
                        if (showField("hours") || showField("break_minutes")) NativeAmountPresets("Pause (Minuten)", "Freie Minuten", v.optString("break_minutes"), listOf("15", "30", "45", "60"), "") { change("break_minutes", it.toIntOrNull() ?: it) }
                        val start = runCatching { java.time.LocalTime.parse(v.optString("start_time")) }.getOrNull()
                        val end = runCatching { java.time.LocalTime.parse(v.optString("end_time")) }.getOrNull()
                        if ((showField("hours") || singleField in setOf("start_time", "end_time", "break_minutes")) && start != null && end != null) {
                            var minutes = java.time.Duration.between(start, end).toMinutes()
                            if (minutes < 0 && v.optBoolean("allow_overnight")) minutes += 1440
                            minutes -= v.optInt("break_minutes")
                            if (minutes > 0) Text("Reine Arbeitszeit: " + nativeDuration(minutes / 60.0))
                        }
                        if (showField("hours") || showField("allow_overnight")) Row(verticalAlignment = Alignment.CenterVertically) { Text("Arbeit über Mitternacht", Modifier.weight(1f)); Switch(v.optBoolean("allow_overnight"), { change("allow_overnight", it) }) }
                    } else if (showField("hours")) {
                        fun updateDuration(hours: String, minutes: String) {
                            durationHours = hours; durationMinutes = minutes; durationSeeded = true; persist()
                        }
                        NativeField("Stunden", durationHours, { updateDuration(it, durationMinutes) })
                        NativeField("Minuten", durationMinutes, { updateDuration(durationHours, it) })
                        Text("Reine Arbeitszeit. Keine Pause zusätzlich abziehen.", style = MaterialTheme.typography.bodySmall)
                        durationAsHours()?.takeIf { it > 0 }?.let { Text(nativeDuration(it)) }
                    }
                    if (showField("activity")) {
                        NativeField("Was wurde erledigt?", v.optString("activity"), { change("activity", it) }, required = true, multiline = true)
                        NativeSuggestions("Textbausteine", listOf("Gartenpflege", "Rasen gemäht", "Hecke geschnitten", "Sträucher zurückgeschnitten", "Laub entfernt", "Beete gepflegt", "Reinigung durchgeführt", "Schnittgut aufgeräumt")) { phrase ->
                            change("activity", listOf(JSONObject(valueText).optString("activity"), phrase).filter { it.isNotBlank() }.joinToString("\n"))
                        }
                        OutlinedButton({ controller.speech { spoken -> change("activity", listOf(JSONObject(valueText).optString("activity"), spoken).filter { it.isNotBlank() }.joinToString(" ")) } }) { Text("Spracheingabe") }
                    }
                    if (singleField.isEmpty()) NativeInfoRow("Weitere Angaben", "Foto, Auftrag, Material, Entsorgung und Notiz", { extraDetails = !extraDetails })
                }
                if (extraDetails || singleField.isNotEmpty()) {
                    item {
                        if (singleField.isEmpty()) {
                            Text("Foto · optional", style = MaterialTheme.typography.titleSmall)
                            if (photoUri.isNotEmpty()) AsyncImage(Uri.parse(photoUri), "Ungespeichertes Foto", imageLoader = controller.imageLoader, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ choosePhoto(true) }, enabled = !busy) { Text("Foto hinzufügen") }
                                OutlinedButton({ choosePhoto(false) }, enabled = !busy) { Text("Foto auswählen") }
                            }
                        }
                        if (showField("order_id")) NativeRecordPicker(controller, "orders", "Auftrag (optional)", v.optString("order_id"), customerId = v.optString("customer_id"), onValueChange = { change("order_id", it) })
                        for (field in listOf("material", "material_cost", "kilometers", "location", "notes").filter { showField(it) }) NativeField(nativeLabel(field), v.opt(field)?.takeUnless { it == JSONObject.NULL }?.toString() ?: "", { entered -> change(field, entered) }, multiline = field in listOf("notes", "material"))
                        if (showField("disposal_cost")) NativeAmountPresets("Entsorgung (€) · optional", "Freier Betrag (€)", v.optString("disposal_cost"), listOf("50", "100", "150"), "€") { change("disposal_cost", it) }
                        if (showField("billing_customer_id")) NativeRecordPicker(controller, "customers", "Rechnungsempfänger · optional", v.optString("billing_customer_id"), onValueChange = { change("billing_customer_id", it) })
                        if (showField("billing_customer_id")) Text("Ohne Auswahl gilt die vorhandene Zuordnung von Kunde oder Auftrag.", style = MaterialTheme.typography.bodySmall)
                        if (showField("customer_visible")) Row { Text("In Kundenansicht zeigen", Modifier.weight(1f)); Switch(v.optBoolean("customer_visible", v.optInt("customer_visible", 1) != 0), { change("customer_visible", if (it) 1 else 0) }) }
                    }
                }
            } else if (kind == "customer") {
                items(listOf("name", "phone", "email", "street", "postal_code", "city", "notes")) { field ->
                    NativeField(if (field == "name") "Anzeigename" else nativeLabel(field), v.optString(field), { change(field, it) }, required = field == "name", multiline = field == "notes")
                }
                item {
                    NativeChoice("Rechnungsempfänger", v.optString("billing_mode", "direct"), listOf("direct" to "Kunde selbst", "via_customer" to "Anderer Rechnungsempfänger"), {
                        change("billing_mode", it); if (it == "direct") change("default_billing_customer_id", "")
                    })
                    if (v.optString("billing_mode") == "via_customer") NativeRecordPicker(controller, "customers", "Rechnungsempfänger", v.optString("default_billing_customer_id"), onValueChange = { change("default_billing_customer_id", it) })
                    NativeInfoRow("Weitere Kundenangaben", "Firma, Name, Land, Fläche und Zusatzfelder", { extraDetails = !extraDetails })
                }
                if (extraDetails) {
                    items(listOf("company", "first_name", "last_name", "country", "garden_area", "sevdesk_customer_number")) { field -> NativeField(nativeLabel(field), v.optString(field), { change(field, it) }) }
                    item {
                        Row { Text("Aktiv", Modifier.weight(1f)); Switch(v.optBoolean("active", v.optInt("active", 1) != 0), { change("active", if (it) 1 else 0) }) }
                        NativeCustomerExtras(v.opt("custom_fields"), { change("custom_fields", it) })
                    }
                }
            } else {
                items(listOf("title", "description", "location")) { field -> NativeField(nativeLabel(field), v.optString(field), { change(field, it) }, required = field == "title", multiline = field == "description") }
                item {
                    NativeDateField("Geplantes Datum", v.optString("scheduled_date"), { change("scheduled_date", it) })
                    NativeRecordPicker(controller, "customers", "Rechnungsempfänger · optional", v.optString("billing_customer_id"), onValueChange = { change("billing_customer_id", it) })
                    NativeRecordPicker(controller, "properties", "Objekt / Haus / Garten", v.optString("property_id"), customerId = v.optString("customer_id"), onValueChange = { change("property_id", it) })
                    NativeChoice("Status", v.optString("status", "Geplant"), listOf("Geplant" to "Geplant", "In Arbeit" to "In Arbeit", "Wartet auf Material" to "Wartet auf Material", "Erledigt" to "Erledigt", "Abgerechnet" to "Abgerechnet", "labor clandestinus" to "labor clandestinus"), { change("status", it) })
                    Row { Text("In Kundenansicht zeigen", Modifier.weight(1f)); Switch(v.optBoolean("customer_visible", v.optInt("customer_visible", 1) != 0), { change("customer_visible", if (it) 1 else 0) }) }
                }
            }
        }
        item { TextButton({ discard = true }, enabled = loaded && !busy && pending == null) { Text("Entwurf verwerfen") } }
    }
        val entered = JSONObject(valueText)
        val requiredComplete = when (kind) {
            "customer" -> entered.optString("name").isNotBlank()
            "order" -> entered.optString("title").isNotBlank() && entered.optString("customer_id").isNotBlank()
            else -> entered.optString("activity").isNotBlank() && entered.optString("customer_id").isNotBlank() && runCatching { WorklogRules.validate(editorPayload(), true) }.isSuccess
        }
        if (pending != null) NativeActionBar("Ergebnis prüfen", { inspectPending() }, "Später prüfen", { controller.back() }, enabled = !busy, secondaryEnabled = !busy)
        else NativeActionBar(if (savedWorkId.isNotEmpty()) "Foto speichern" else when (kind) { "customer" -> "Kunde speichern"; "order" -> "Auftrag speichern"; else -> "Arbeit speichern" }, { save() }, "Zurück", { controller.back() },
            enabled = loaded && !busy && !conflict && pending == null && (savedWorkId.isNotEmpty() || !restored && requiredComplete), secondaryEnabled = !busy)
    }
    if (discard) LaunchedEffect(Unit) {
        busy = true
        try { nativeDeletePhotoDraft(controller, photoUri); controller.removeDraft(draftKey); controller.back() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Entwurf konnte nicht gelöscht werden." }
        finally { busy = false; discard = false }
    }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeTimerValues(controller: NativeAppController, value: JSONObject) {
    Text(when (value.optString("state")) { "RUNNING" -> "Arbeit läuft"; "PAUSED" -> "Arbeit pausiert"; "STOPPED_PENDING_REVIEW" -> "Arbeit abschließen"; "CANCELLED" -> "Verworfene Arbeit"; else -> "Zeiterfassung" }, style = MaterialTheme.typography.headlineSmall)
    if (value.optString("state") in listOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")) NativeTimeCard(value)
    else NativeStatus("Zeitmessung beendet")
    NativeContext(controller, JSONObject().apply {
        for (key in listOf("customer_id", "property_id", "order_id")) put(key, value.optString(key))
    })
    for (key in listOf("activity", "location")) if (value.optString(key).isNotBlank()) Text(nativeLabel(key) + ": " + value.getString(key))
    Text("Arbeitsdatum: " + nativeDisplayDate(value.optString("work_date")))
    if (value.optLong("started_at_ms") > 0 && value.optString("state") !in listOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")) {
        val start = java.time.Instant.ofEpochMilli(value.getLong("started_at_ms")).atZone(java.time.ZoneId.of("Europe/Vienna"))
        val pattern = if (start.toLocalDate().toString() == value.optString("work_date")) "HH:mm" else "dd.MM.yyyy · HH:mm"
        Text("Beginn: " + start.format(java.time.format.DateTimeFormatter.ofPattern(pattern)))
    }
    if (value.optString("state") !in listOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")) Text("Pause: " + nativeNumber(value.opt("break_minutes")) + " Min.")
    if (value.optString("time_origin") == "manual_review") Text("Zeit wurde manuell geprüft.")
    if (value.optString("correction_reason").isNotBlank()) Text("Zeitkorrektur: " + value.getString("correction_reason"))
    if (value.optBoolean("review_required")) Text("Die Zeitmessung muss vor dem Speichern ausdrücklich geprüft werden.", color = MaterialTheme.colorScheme.error)
    if (value.optBoolean("pause_unverified")) Text("Die Pausendauer ist noch ungeklärt. Bitte bei der Zeitprüfung kontrollieren.", color = MaterialTheme.colorScheme.error)
}

@Composable
private fun NativeTimer(controller: NativeAppController) {
    var session by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var cancelledId by rememberSaveable { mutableStateOf("") }
    var cancelledReview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var discardNeedsReview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() { session = withContext(Dispatchers.IO) { controller.store.workSessions().current() } }
    LaunchedEffect(Unit) {
        try {
            controller.draft("timer_cancelled")?.let { cancelledId = it.optString("session_id"); cancelledReview = it.optBoolean("_restore_action_review_required") }
            controller.draft("timer_action")?.let { saved -> saved.optJSONObject("original_args")?.let { pending = saved.getString("original_tool") to it } }
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
                val id = pending?.second?.optString("session_id") ?: explicitSession.ifEmpty { session?.optString("id").orEmpty() }
                if (tool == "discard_work_session") {
                    val draft = controller.draft("action:complete_work_session:$id")
                    if (draft?.optJSONObject("original_args") != null || draft?.optBoolean("_restore_action_review_required") == true || draft?.optBoolean("review_required") == true) {
                        discardNeedsReview = true
                        throw IllegalStateException("Der Abschluss muss zuerst geprüft werden. Der ursprüngliche Vorgang bleibt erhalten.")
                    }
                }
                session = controller.timerAction(tool, id)
                if (tool == "discard_work_session") {
                    cancelledId = id; cancelledReview = false
                    controller.removeDraft("action:complete_work_session:$id")
                }
                else if (tool == "restore_work_session") { cancelledId = ""; cancelledReview = false }
                pending = null; error = ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = failure.message ?: "Ergebnis ungeklärt. Dieselbe Aktion erneut prüfen."
                controller.draft("timer_action")?.let { saved -> pending = saved.getString("original_tool") to saved.getJSONObject("original_args") } ?: run { pending = null }
            } finally { busy = false }
        }
    }
    val state = session?.optString("state", "IDLE")
    if (pending != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Der ursprüngliche Zeitvorgang wird mit derselben Kennung geprüft.")
            session?.let { NativeTimerValues(controller, it) }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button({ scope.launch {
                busy = true
                try {
                    val original = requireNotNull(pending)
                    val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", original.first).put("original_args", original.second))
                    if (proof.optString("state") == "confirmed") { controller.removeDraft("timer_action"); refresh(); pending = null; error = "" }
                    else error = "Ergebnis weiterhin unbestätigt. Der ursprüngliche Zeitvorgang bleibt erhalten."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Der ursprüngliche Zeitvorgang konnte nicht gelesen werden." }
                finally { busy = false }
            } }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text("Ursprünglichen Vorgang prüfen") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    if (state in listOf("IDLE", "UNAVAILABLE", "FINALIZED")) {
        Column(Modifier.fillMaxSize()) {
            if (error.isNotEmpty()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            if (cancelledId.isNotEmpty()) OutlinedButton({ execute("restore_work_session", cancelledId) }, enabled = !busy) { Text(if (cancelledReview) "Übernommene verworfene Sitzung prüfen und zurückholen" else "Letzte verworfene Sitzung zurückholen") }
            Box(Modifier.weight(1f)) { NativeActionForm(controller, "start_work", "Arbeit starten", controller.route.args, submitLabel = "Arbeit starten", onDone = { scope.launch { refresh() } }) }
        }
        return
    }
    val value = session
    val sessionId = value?.let { it.optString("id", it.optString("session_id")) }.orEmpty()
    val completionKey = "action:complete_work_session:$sessionId"
    var activity by rememberSaveable(sessionId) { mutableStateOf(value?.optString("activity").orEmpty()) }
    var activityReady by remember(sessionId) { mutableStateOf(false) }
    var activityLocked by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        if (sessionId.isNotEmpty()) {
            val saved = controller.draft(completionKey)
            saved?.optJSONObject("values")?.let { activity = it.optString("activity", activity) }
            activityLocked = saved?.optJSONObject("original_args") != null || saved?.optBoolean("_restore_action_review_required") == true
        }
        activityReady = true
    }
    if (state == "STOPPED_PENDING_REVIEW" && value != null) {
        // Finish reading the timer's draft before creating the action form.
        // A late activity prefill would reset its inputs and generated request ID.
        if (!activityReady) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Abschluss wird vorbereitet …")
                CircularProgressIndicator()
            }
            return
        }
        val args = JSONObject().put("session_id", sessionId).put("activity", activity).put("_draft_key", completionKey)
            .put("_preview", JSONObject().put("work_date", value.optString("work_date"))
                .put("nettoarbeitszeit", nativeDuration(value.optLong("worked_ms") / 3600000.0)).put("break_minutes", value.opt("break_minutes"))
                .put("status", "Zeit gestoppt · noch nicht gespeichert").apply {
                    if (value.optString("time_origin") != "manual_review") {
                        if (value.optLong("started_at_ms") > 0) put("start_time", java.time.Instant.ofEpochMilli(value.getLong("started_at_ms")).atZone(ZoneId.of("Europe/Vienna")).toLocalTime().toString().take(5))
                        runCatching { java.time.Instant.parse(value.optString("updated_at")).atZone(ZoneId.of("Europe/Vienna")).toLocalTime().toString().take(5) }.getOrNull()?.let { put("end_time", it) }
                    }
                })
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeContext(controller, value)
                if (value.optBoolean("review_required")) {
                    NativeStatus("Zeitprüfung erforderlich", NativeStatusTone.Attention)
                    OutlinedButton({ controller.action("Zeit ausdrücklich prüfen", "review_work_session", JSONObject().put("session_id", sessionId).put("expected_revision_hash", value.optString("revision_hash"))) }) { Text("Zeit ausdrücklich prüfen") }
                }
            }
            Box(Modifier.weight(1f)) {
                NativeActionForm(controller, "complete_work_session", "Arbeit abschließen", args, submitLabel = "Arbeit speichern", onCancel = { controller.finishAction() }, onDone = { scope.launch { refresh(); controller.finishAction(); controller.message("Arbeit gespeichert.") } }, detailsContent = {
                    val resourceContext = JSONObject().put("session_id", sessionId).apply {
                        for (field in listOf("customer_id", "order_id", "property_id", "visit_id")) if (value.optString(field).isNotEmpty()) put(field, value.getString(field))
                    }
                    NativeInfoRow("Foto hinzufügen", "Zur gestoppten Arbeit · Abschluss bleibt erhalten", { controller.navigate("photos", mode = "import", args = JSONObject(resourceContext.toString()).put("_open_camera", true)) })
                    NativeInfoRow("Material", "Verbrauch zur gestoppten Arbeit buchen", { controller.navigate("materials", mode = "movement", args = resourceContext) })
                    if (error.isNotBlank()) NativeStatus(error, NativeStatusTone.Attention)
                    OutlinedButton({ execute("discard_work_session", sessionId) }, enabled = !busy) { Text("Entwurf verwerfen") }
                })
            }
        }
        if (discardNeedsReview) AlertDialog(onDismissRequest = { discardNeedsReview = false },
            title = { Text("Abschluss zuerst prüfen") }, text = { Text("Der ursprüngliche Abschluss ist unbestätigt oder stammt aus einer Wiederherstellung. Er bleibt erhalten und wird nicht verworfen.") },
            confirmButton = { TextButton({ discardNeedsReview = false; controller.action("Ursprünglichen Abschluss prüfen", "complete_work_session", JSONObject().put("session_id", sessionId).put("_draft_key", completionKey)) }) { Text("Ergebnis prüfen") } },
            dismissButton = { TextButton({ discardNeedsReview = false }) { Text("Zurück") } })
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error.isNotEmpty()) NativeStatus(error, NativeStatusTone.Attention)
            if (value == null) CircularProgressIndicator() else {
                NativeTimerValues(controller, value)
                if (state in listOf("RUNNING", "PAUSED")) {
                    NativeField("Tätigkeit · optional bis zum Abschluss", activity, { entered ->
                        activity = entered
                        controller.saveDraft(completionKey, JSONObject().put("original_tool", "complete_work_session").put("values", JSONObject().put("session_id", sessionId).put("activity", activity)))
                    }, multiline = true, enabled = activityReady && !activityLocked)
                    if (activityLocked) {
                        NativeStatus("Offenen Abschluss zuerst prüfen", NativeStatusTone.Attention)
                        TextButton({ controller.action("Ursprünglichen Abschluss prüfen", "complete_work_session", JSONObject().put("session_id", sessionId).put("_draft_key", completionKey)) }) { Text("Ergebnis prüfen") }
                    }
                    if (activityReady && !activityLocked) NativeSuggestions("Textbausteine", listOf("Gartenpflege", "Rasen gemäht", "Hecke geschnitten", "Beete gepflegt", "Schnittgut aufgeräumt")) { phrase ->
                        activity = listOf(activity, phrase).filter { it.isNotBlank() }.joinToString("\n")
                        controller.saveDraft(completionKey, JSONObject().put("original_tool", "complete_work_session").put("values", JSONObject().put("session_id", sessionId).put("activity", activity)))
                    }
                    val resourceContext = JSONObject().put("session_id", sessionId).apply {
                        for (field in listOf("customer_id", "order_id", "property_id", "visit_id")) if (value.optString(field).isNotEmpty()) put(field, value.getString(field))
                    }
                    NativeInfoRow("Foto hinzufügen", "Zur laufenden Arbeit", { controller.navigate("photos", mode = "import", args = JSONObject(resourceContext.toString()).put("_open_camera", true)) })
                    NativeInfoRow("Material", "Verbrauch zur laufenden Arbeit buchen", { controller.navigate("materials", mode = "movement", args = resourceContext) })
                    NativeInfoRow("Gerätenutzung", "Nutzung zur laufenden Arbeit ergänzen", { controller.navigate("equipment", mode = "equipment_use", args = resourceContext) })
                }
                if (value.optBoolean("review_required")) OutlinedButton({ controller.action("Zeit ausdrücklich prüfen", "review_work_session", JSONObject().put("session_id", sessionId).put("expected_revision_hash", value.optString("revision_hash"))) }) { Text("Zeit ausdrücklich prüfen") }
            }
        }
        when (state) {
            "RUNNING" -> NativeActionBar("Zeit stoppen", { execute("stop_work") }, "Pause", { execute("start_break") }, enabled = !busy, secondaryEnabled = !busy)
            "PAUSED" -> NativeActionBar("Fortsetzen", { execute("end_break") }, "Zeit stoppen", { execute("stop_work") }, enabled = !busy, secondaryEnabled = !busy)
            "CANCELLED" -> NativeActionBar("Sitzung zurückholen", { execute("restore_work_session") }, enabled = !busy)
        }
    }
}

@Composable
private fun NativeSettings(controller: NativeAppController) {
    var settings by remember { mutableStateOf<JSONObject?>(null) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { settings = controller.query("get_settings") } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message.orEmpty() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Arbeiten und Unternehmen", style = MaterialTheme.typography.titleMedium) }
        if (error.isNotEmpty()) item { NativeStatus(error, NativeStatusTone.Attention) }
        if (settings == null && error.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { NativeInfoRow("Arbeitsvorgaben", "Tätigkeit, Einsatzort und Fotoqualität", {
            if (settings != null) controller.action("Arbeitsvorgaben ändern", "update_settings", JSONObject().put("expected_record_hash", settings?.optString("record_hash", "")).put("updates", settings?.optJSONObject("settings") ?: JSONObject()))
        }) }
        item { NativeInfoRow("Unternehmensdaten", "Angaben für Angebote", { scope.launch {
            try { val current = controller.query("get_business_settings"); controller.action("Unternehmensdaten bearbeiten", "update_business_settings", JSONObject().put("expected_record_hash", current.optString("record_hash")).put("updates", current.optJSONObject("settings") ?: JSONObject())) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
        } }) }
        item { Text("Verbindungen und Daten", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium) }
        item { NativeInfoRow("Verbindungen", "Kalender, Wetter und ChatGPT · optional", { controller.navigate("connections") }) }
        item { NativeInfoRow("Sicherung und Wiederherstellung", "ZIP speichern oder wiederherstellen", { scope.launch {
            try { controller.flushDrafts(); controller.open(FullBackupActivity::class.java) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
        } }) }
        item { NativeInfoRow("Speicher", "Belegung und Bereinigung", { controller.navigate("storage") }) }
        item { NativeInfoRow("Papierkorb", "Alte Einträge löschen", { controller.navigate("trash") }) }
        item { Text("App prüfen", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium) }
        item { OutlinedButton({ scope.launch {
            testing = true
            try { info = controller.query("run_self_test"); error = "" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
            finally { testing = false }
        } }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !testing) { Text(if (testing) "Prüfung läuft …" else "Lokale App-Funktionen prüfen") } }
        if (testing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        info?.let { result ->
            item { Text("Prüfung abgeschlossen", style = MaterialTheme.typography.titleMedium) }
            val checks = result.optJSONArray("checks") ?: JSONArray()
            val values = (0 until checks.length()).map { checks.getJSONObject(it) }
            val passed = values.count { it.optString("status") == "PASS" }
            item { NativeStatus("$passed von ${values.size} Prüfungen bestanden", if (passed == values.size) NativeStatusTone.Confirmed else NativeStatusTone.Neutral) }
            items(values.filter { it.optString("status") != "PASS" }) { check ->
                val title = when (check.optString("name")) {
                    "build_and_protocol" -> "App und lokales Protokoll"
                    "catalog_unique" -> "Eindeutige Funktionskennungen"
                    "write_gate" -> "Lokale Schreibfreigabe"
                    "business_settings_complete" -> "Unternehmensdaten vollständig"
                    "local_backup" -> "Protokollierte Sicherung"
                    "offers_and_catalog" -> "Angebote und Leistungskatalog"
                    "ui_mcp_parity" -> "Lokale Funktionszuordnung"
                    "private_document_storage" -> "Lokaler Dokumentenspeicher"
                    "physical_google_and_delivery" -> "Fremddienste und Versand"
                    else -> nativeLabel(check.optString("name"))
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    val state = check.optString("status")
                    NativeStatus(when (state) { "PASS" -> "Bestanden · begrenzte lokale Prüfung"; "FAIL" -> "Prüfung erforderlich"; else -> "Nicht geprüft" }, when (state) { "PASS" -> NativeStatusTone.Confirmed; "FAIL" -> NativeStatusTone.Attention; else -> NativeStatusTone.Neutral })
                }
                HorizontalDivider()
            }
            item { Text("Geprüft werden lokale App-Funktionen. Verbindungen und Wiederherstellung sind getrennte Prüfungen.", style = MaterialTheme.typography.bodySmall) }
        }
        item { Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun NativeTrash(controller: NativeAppController) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(offset, refresh) {
        try { data = controller.query("list_archive", JSONObject().put("offset", offset).put("limit", 40)); error = "" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
    }
    val rows = data?.optJSONArray("items") ?: data?.optJSONArray("archive") ?: data?.optJSONArray("entries") ?: JSONArray()
    val entries = (0 until rows.length()).map { rows.getJSONObject(it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error.isNotEmpty()) item { NativeStatus(error, NativeStatusTone.Failed) }
        else if (data == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (rows.length() == 0) item { Text("Der Papierkorb ist leer.") }
        items(entries, key = { it.optString("archive_key") }) { row ->
            val archived = row.optJSONObject("entry") ?: row
            val record = archived.optJSONObject("record") ?: archived.optJSONObject("_archive_record") ?: JSONObject()
            val key = row.optString("archive_key")
            val entity = key.substringBefore(':')
            val id = key.substringAfter(':')
            val fallback = when (entity) {
                "customer" -> "Kunde"; "order" -> "Auftrag"; "worklog" -> "Arbeit"
                "photo" -> "Foto"; "invoice" -> "Rechnung"; "offer" -> "Angebot"
                "appointment" -> "Termin"; "service_template" -> "Leistung"; else -> "Eintrag"
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listOf("name", "title", "activity", "caption").firstNotNullOfOrNull { field -> record.optString(field).takeIf { it.isNotBlank() } } ?: fallback,
                    style = MaterialTheme.typography.titleMedium)
                Text(nativeDisplayDate(archived.optString("archived_at")), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeDeleteButton(controller, entity, id, label = "Löschen", permanent = true) { refresh++ }
                }
                HorizontalDivider()
            }
        }
        item { Row {
            if (offset > 0) OutlinedButton({ offset = maxOf(0, offset - 40) }) { Text("Vorherige") }
            if (offset + rows.length() < (data?.optInt("total") ?: 0)) OutlinedButton({ offset += 40 }) { Text("Weitere Einträge") }
        } }
        item { NativeDeleteButton(controller, "", "", label = "Papierkorb leeren", emptyTrash = true, enabled = rows.length() > 0) { offset = 0; refresh++ } }
        item { NativeInfoRow("Revier-Archiv", "Archivierte Fachdatensätze ansehen", { controller.navigate("revier_archive") }) }
    }
}

@Composable
private fun NativeStorageSettings(controller: NativeAppController) {
    var usage by remember { mutableStateOf<JSONObject?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun readUsage() {
        try { usage = controller.storageUsage() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Belegung konnte nicht gelesen werden." }
    }
    LaunchedEffect(Unit) { readUsage() }
    fun size(bytes: Long) = android.text.format.Formatter.formatFileSize(controller.context, bytes)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speicher auf diesem Pixel", style = MaterialTheme.typography.titleMedium)
            if (usage != null) Text(size(usage!!.optLong("total_bytes")), style = MaterialTheme.typography.headlineLarge)
            else if (error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            NativeInfoRow("Belegung ansehen", "Datenbank, Fotos, PDFs, Arbeitsdateien und Cache", { expanded = !expanded })
            if (expanded) usage?.let { value ->
                for ((key, label) in listOf("database_bytes" to "Datenbank mit Journal", "photo_bytes" to "Fotos, Vorschauen und Medien", "document_bytes" to "PDF-Belege", "other_files_bytes" to "Weitere App-Dateien", "work_bytes" to "Arbeits- und Wiederherstellungsdaten", "cache_bytes" to "Zwischenspeicher")) NativeInfoRow(label, size(value.optLong(key)))
                value.optJSONObject("work_directories")?.let { directories ->
                    Text("Arbeits- und Wiederherstellungsordner", style = MaterialTheme.typography.titleSmall)
                    for (name in directories.keys().asSequence().sorted()) {
                        val label = when (name) {
                            "full-backup-work" -> "Alte Sicherungs-Arbeitskopien"
                            "browser-media-upload" -> "Alte Upload-Arbeitsdateien"
                            "pixel-backup" -> "Sicherungs-Arbeitsdateien"
                            "full-restore" -> "Wiederherstellungsvorschauen"
                            "before-restore" -> "Alte Wiederherstellungsdateien"
                            "deletion-backups" -> "Historische Löschsicherungen"
                            "legacy-conflict-backups" -> "Historische Konfliktsicherungen"
                            "backup-device-guard" -> "Lokale Gerätekennung"
                            else -> name
                        }
                        NativeInfoRow(label, size(directories.optLong(name)))
                    }
                }
                Text("Davon Backup-Arbeitsdateien: ${size(value.optLong("backup_work_bytes"))}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Entfernt ungenutzte Arbeitsdateien und alte interne Sicherungskopien.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (result.isNotEmpty()) NativeStatus(result, NativeStatusTone.Confirmed)
            if (error.isNotEmpty()) NativeStatus(error, NativeStatusTone.Attention)
            OutlinedButton({ scope.launch { busy = true; try { readUsage() } finally { busy = false } } }, enabled = !busy) { Text("Belegung erneut ansehen") }
        }
        NativeActionBar(if (busy) "Bereinigung läuft …" else "Speicher bereinigen", {
            busy = true; result = ""; error = ""
            scope.launch {
                try { val cleaned = controller.cleanStorage(); usage = cleaned; result = "Bereinigt · ${size(cleaned.optLong("freed_bytes"))} freigegeben" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    error = if (failure.message == "STORAGE_CHECKPOINT_BUSY") "Ein laufender Vorgang verwendet die Datenbank. Anschließend erneut versuchen." else "Bereinigung nicht vollständig abgeschlossen. Verwendete Dateien bleiben erhalten."
                    readUsage()
                } finally { busy = false }
            }
        }, "Zurück", { controller.back() }, enabled = !busy && usage != null, secondaryEnabled = !busy)
    }
}
