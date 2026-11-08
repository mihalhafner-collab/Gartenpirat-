package at.hausgarten.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Design: one billing area with tabs; "Offen" leads with what is still to bill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeAccounting(controller: NativeAppController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Offen", "Abschlüsse", "Summen").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    modifier = Modifier.semantics { testTag = UiTags.billingTab(index) },
                    text = { Text(label, maxLines = 2) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NativeOpenHours(controller, "")
                        Button(
                            { controller.navigate("settlement_edit") },
                            Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Neue Abrechnung")
                        }
                        Text(
                            "Fertig → abgerechnet → bezahlt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                1 -> Column(Modifier.fillMaxSize()) { NativeSettlements(controller, "") }
                else -> AccountingTotals(controller)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountingTotals(controller: NativeAppController) {
    var search by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var property by rememberSaveable { mutableStateOf("") }
    var route by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var reviewOffset by rememberSaveable { mutableIntStateOf(0) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changes =
        remember(controller) {
            controller.store.nativeData().watchCommittedChanges().nativeErrors(controller)
        }
    val latest by changes.collectAsStateWithLifecycle(-1L)
    fun changed() {
        offset = 0
        reviewOffset = 0
    }
    fun selectRoute(value: String) {
        route = value
        changed()
    }
    LaunchedEffect(search, from, to, property, route, offset, reviewOffset, latest, attempt) {
        result = null
        error = ""
        try {
            result =
                controller.query(
                    "get_accounting_overview",
                    JSONObject()
                        .put("query", search)
                        .put("date_from", from)
                        .put("date_to", to)
                        .put("route", route)
                        .put("offset", offset)
                        .put("review_offset", reviewOffset)
                        .put("limit", 40)
                        .apply { if (property.isNotEmpty()) put("property_id", property) },
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Abrechnung konnte nicht geladen werden."
        }
    }
    NativeSearchList(
        "Auftragsbezeichnung",
        search,
        {
            search = it
            changed()
        },
        result?.let { "${it.optInt("total")} Einträge" }
            ?: if (error.isEmpty()) "Wird geladen …" else "Nicht geladen",
        onFilters = { filters = !filters },
        bottomSearch = true,
    ) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((path, field) in
                        listOf(
                            AccountingRules.BILLED to "billed_minor",
                            AccountingRules.LABOR to "labor_minor",
                            "" to "total_minor",
                        )) {
                        Surface(
                            onClick = { selectRoute(path) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color =
                                if (route == path) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (route == path) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    if (path.isEmpty()) "Gesamt" else AccountingRules.label(path),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                val pending =
                                    result?.optInt(
                                        when (path) {
                                            AccountingRules.BILLED -> "pending_billed"
                                            AccountingRules.LABOR -> "pending_labor"
                                            else -> "pending_amounts"
                                        }
                                    ) ?: 0
                                Text(
                                    result?.let {
                                        if (pending > 0 && it.getLong(field) == 0L)
                                            "Betrag ausstehend"
                                        else financeMoney(OfferMoney.decimal(it.getLong(field)))
                                    } ?: "…",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NativeMoney,
                                )
                                if (pending > 0)
                                    Text(
                                        "$pending Beträge fehlen oder sind unvollständig",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                            }
                        }
                    }
                }
            }
            item {
                TextButton(
                    {
                        scope.launch {
                            exporting = true
                            try {
                                controller.shareSettlementExport(
                                    JSONObject()
                                        .put("query", search)
                                        .put("date_from", from)
                                        .put("date_to", to)
                                        .put("property_id", property)
                                        .put("route", route)
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "Export nicht möglich."
                            } finally {
                                exporting = false
                            }
                        }
                    },
                    enabled = !exporting,
                ) {
                    Text(
                        if (
                            search.isNotBlank() ||
                                from.isNotBlank() ||
                                to.isNotBlank() ||
                                property.isNotBlank() ||
                                route.isNotBlank()
                        )
                            "Gefilterte Abrechnung exportieren"
                        else "Vollständige Abrechnung exportieren"
                    )
                }
                NativeStatus(
                    if (route.isEmpty()) "Beide Abrechnungswege" else AccountingRules.label(route)
                )
                if (from.isNotEmpty() || to.isNotEmpty())
                    Text(
                        "${from.takeIf { it.isNotEmpty() }?.let(::nativeDisplayDate) ?: "Ohne Beginn"} – ${to.takeIf { it.isNotEmpty() }?.let(::nativeDisplayDate) ?: "Ohne Ende"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
            if (error.isNotEmpty())
                item {
                    NativeStatus(error, NativeStatusTone.Failed)
                    TextButton({ attempt++ }) { Text("Erneut laden") }
                }
            else if (result == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                val data = result!!
                val records = data.getJSONArray("records").objects()
                if (records.isEmpty())
                    item { Text("Keine abgerechneten Aufträge für diese Auswahl.") }
                items(records, key = { it.getString("key") }) { row ->
                    AccountingRow(controller, row)
                }
                item { AccountingPages(offset, data.getInt("total"), 40) { offset = it } }
                if (data.getInt("review_total") > 0) {
                    item {
                        NativeStatus(
                            "${data.getInt("review_total")} Aufträge prüfen · nicht in den Summen",
                            NativeStatusTone.Attention,
                        )
                        Text(
                            "Widersprüchliche Zuordnungen werden keinem Abrechnungsweg zugerechnet.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(
                        data.getJSONArray("reviews").objects(),
                        key = { "review:" + it.getString("key") },
                    ) { row ->
                        AccountingRow(controller, row)
                        NativeStatus(row.getString("issue"), NativeStatusTone.Attention)
                        TextButton({
                            controller.navigate(
                                "accounting_edit",
                                row.getString("id"),
                                row.getString("collection"),
                            )
                        }) {
                            Text("Zuordnung ändern")
                        }
                    }
                    item {
                        AccountingPages(reviewOffset, data.getInt("review_total"), 40) {
                            reviewOffset = it
                        }
                    }
                }
            }
        }
    }
    if (filters)
        ModalBottomSheet(
            onDismissRequest = { filters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Abrechnung filtern", style = MaterialTheme.typography.titleLarge)
                    NativeDateField(
                        "Ab Datum",
                        from,
                        {
                            from = it
                            changed()
                        },
                    )
                    NativeDateField(
                        "Bis Datum",
                        to,
                        {
                            to = it
                            changed()
                        },
                    )
                    NativeRecordPicker(
                        controller,
                        "properties",
                        "Objekt / Haus / Garten",
                        property,
                        onValueChange = {
                            property = it
                            changed()
                        },
                    )
                    NativeChoice(
                        "Abrechnungsweg",
                        route,
                        listOf(
                            "" to "Beide",
                            AccountingRules.BILLED to "Rechnung",
                            AccountingRules.LABOR to "Labor clandestino",
                        ),
                        ::selectRoute,
                    )
                }
                NativeActionBar(
                    "Ergebnisse anzeigen",
                    { filters = false },
                    "Zurücksetzen",
                    {
                        from = ""
                        to = ""
                        property = ""
                        route = ""
                        search = ""
                        changed()
                    },
                )
            }
        }
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

@Composable
private fun AccountingPages(offset: Int, total: Int, size: Int, change: (Int) -> Unit) {
    NativeActions {
        if (offset > 0)
            TextButton({ change((offset - size).coerceAtLeast(0)) }) { Text("Vorherige Aufträge") }
        if (offset + size < total)
            TextButton({ change(offset + size) }) { Text("Weitere Aufträge") }
    }
}

@Composable
private fun AccountingRow(controller: NativeAppController, row: JSONObject) {
    NativeInfoRow(
        row.getString("title"),
        listOf(
                "${row.optString("date_label")}: ${row.optString("work_date").takeIf { it.isNotEmpty() }?.let(::nativeDisplayDate) ?: "Nicht hinterlegt"}",
                row.optString("object_label"),
                if (row.isNull("amount_minor")) AccountingRules.PENDING_AMOUNT
                else financeMoney(OfferMoney.decimal(row.getLong("amount_minor"))),
                row.optString("route").takeIf { it.isNotEmpty() }?.let(AccountingRules::label)
                    ?: "Zuordnung prüfen",
                row.optString("issue"),
            )
            .filter { it.isNotBlank() }
            .joinToString("\n"),
        {
            if (row.getString("collection") == "settlements")
                controller.navigate("settlement_review", row.getString("id"))
            else controller.navigate(row.getString("collection"), row.getString("id"))
        },
    )
}

/** Reuse the existing persistent action form, conflict handling and replay flow. */
@Composable
internal fun NativeAccountingEditor(
    controller: NativeAppController,
    collection: String,
    id: String,
) {
    var initial by rememberSaveable(collection, id) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(collection, id, attempt) {
        if (initial != null) return@LaunchedEffect
        try {
            val data =
                controller.query(
                    "get_accounting_record",
                    JSONObject().put("collection", collection).put("entity_id", id),
                )
            initial =
                JSONObject()
                    .put("collection", data.getString("collection"))
                    .put("entity_id", data.getString("id"))
                    .put("expected_record_hash", data.getString("expected_record_hash"))
                    .apply {
                        if (data.optString("route").isNotEmpty())
                            put("route", data.getString("route"))
                        else if (data.optString("issue").isEmpty()) put("route", "open")
                    }
                    .put("_accounting_title", data.getString("title"))
                    .toString()
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Auftrag konnte nicht gelesen werden."
        }
    }
    if (initial != null) {
        val values = JSONObject(initial!!)
        NativeActionForm(
            controller,
            "set_accounting_record",
            values.getString("_accounting_title"),
            values,
            submitLabel = "Abrechnung speichern",
        )
    } else
        Column(Modifier.padding(16.dp)) {
            if (error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                NativeStatus(error, NativeStatusTone.Failed)
                TextButton({ attempt++ }) { Text("Erneut laden") }
            }
        }
}
