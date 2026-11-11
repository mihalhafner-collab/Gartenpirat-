package at.hausgarten.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun accountingMoney(value: Long) = financeMoney(OfferMoney.decimal(value))

/** Invoices, cohort payments and current open work retain distinct meanings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeAccounting(controller: NativeAppController) {
    var year by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val changes by
        remember(controller) {
                controller.store.nativeData().watchCommittedChanges().nativeErrors(controller)
            }
            .collectAsStateWithLifecycle(-1L)
    val today = LocalDate.now(ZoneId.of("Europe/Vienna"))
    val from = if (year) today.withDayOfYear(1) else today.withDayOfMonth(1)
    val to = if (year) from.plusYears(1).minusDays(1) else from.plusMonths(1).minusDays(1)
    LaunchedEffect(changes, attempt, year) {
        try {
            report =
                controller.query(
                    "get_finance_overview",
                    JSONObject()
                        .put("date_from", from.toString())
                        .put("date_to", to.toString())
                        .put("date_basis", "document"),
                )
            data = controller.query("list_settlements")
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Abrechnung nicht lesbar."
        }
    }
    val records = data?.optJSONArray("records").objects()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NativeChoice(
            "Zeitraum · Belegdatum",
            if (year) "year" else "month",
            listOf("month" to "Monat", "year" to "Jahr"),
        ) {
            year = it == "year"
        }
        Text(
            nativeDisplayDate(from.toString()) + " – " + nativeDisplayDate(to.toString()),
            style = MaterialTheme.typography.bodySmall,
        )
        if (error.isNotBlank()) {
            NativeStatus(error, NativeStatusTone.Failed)
            NativeTextButton({ attempt++ }) { Text("Erneut laden") }
        }
        if (report == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        report?.let { summary ->
            @Composable
            fun total(label: String, key: String) {
                Column(Modifier.semantics { testTag = "accounting-total:$key" }) {
                    Text(
                        label,
                        modifier = Modifier.semantics { testTag = "accounting-total-label:$key" },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        accountingMoney(summary.optLong(key)),
                        modifier = Modifier.semantics { testTag = "accounting-total-value:$key" },
                        color = NativeMoney,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                total("ausgestellt", "issued_minor")
                total("bezahlt · diese Belege", "paid_minor")
                total("offen · diese Belege", "open_minor")
            }
            HorizontalDivider()
            if (summary.optLong("credit_minor") > 0)
                NativeInfoRow(
                    "Guthaben",
                    accountingMoney(summary.optLong("credit_minor")),
                    { controller.navigate("accounting_history") },
                )
            if (summary.optLong("refund_due_minor") > 0)
                NativeInfoRow(
                    "Rückzahlung offen",
                    accountingMoney(summary.optLong("refund_due_minor")),
                    { controller.navigate("accounting_history") },
                )
        }
        NativeDisclosure("Offen abzurechnen", initiallyExpanded = true) {
            NativeOpenHours(controller, "")
        }
        val unpaid = records.filter {
            it.optString("status") == "confirmed" &&
                it.optString("kind") == "invoice" &&
                it.optLong("total_minor") > it.optLong("paid_minor")
        }
        NativeDisclosure(
            "Zahlung offen",
            "${unpaid.size} Rechnungen",
            initiallyExpanded = unpaid.isNotEmpty(),
        ) {
            if (unpaid.isEmpty()) Text("Alle ausgestellten Rechnungen sind ausgeglichen.")
            for (row in unpaid) {
                val id = row.getString("id")
                NativeInfoRow(
                    row.optString("invoice_number").ifBlank { row.optString("title") },
                    accountingMoney(
                        (row.optLong("total_minor") - row.optLong("paid_minor")).coerceAtLeast(0)
                    ),
                    { controller.navigate("settlement_review", id) },
                )
                NativeActions {
                    NativeTextButton(
                        {
                            paying = id
                            scope.launch {
                                try {
                                    settlementPaid(controller, id)
                                    error = ""
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error =
                                        failure.message ?: "Zahlung konnte nicht vorgemerkt werden."
                                } finally {
                                    paying = ""
                                }
                            }
                        },
                        enabled =
                            paying.isEmpty() &&
                                controller.listActions.none {
                                    ("settlement" to id) in ListActionTools.targets(it) &&
                                        it.optString("state") in
                                            setOf("queued", "applying", "uncertain")
                                },
                    ) {
                        Text("Bezahlt")
                    }
                }
                HorizontalDivider()
            }
        }
        NativeInfoRow("Alle Abschlüsse", onClick = { controller.navigate("settlements") })
        NativeInfoRow(
            "Umsätze und Zahlungen",
            "Leistungs-, Beleg- und Zahlungsdatum",
            { controller.navigate("accounting_history") },
        )
        NativeActions {
            NativeTextButton({
                scope.launch {
                    try {
                        controller.shareSettlementExport(
                            JSONObject()
                                .put("date_from", from.toString())
                                .put("date_to", to.toString())
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "Export nicht möglich."
                    }
                }
            }) {
                Text("Exportieren")
            }
        }
    }
}

/** Period totals are computed by the same domain tool used by MCP. */
@Composable
internal fun NativeAccountingHistory(controller: NativeAppController) {
    val today = LocalDate.now(ZoneId.of("Europe/Vienna"))
    var from by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var to by rememberSaveable { mutableStateOf(today.toString()) }
    var basis by rememberSaveable { mutableStateOf("document") }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    val changes by
        remember(controller) {
                controller.store.nativeData().watchCommittedChanges().nativeErrors(controller)
            }
            .collectAsStateWithLifecycle(-1L)
    LaunchedEffect(from, to, basis, changes, attempt) {
        try {
            report =
                controller.query(
                    "get_finance_overview",
                    JSONObject().put("date_from", from).put("date_to", to).put("date_basis", basis),
                )
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Zeitraum konnte nicht gelesen werden."
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NativeChoice(
                "Datumsbasis",
                basis,
                listOf("service" to "Leistung", "document" to "Beleg", "payment" to "Zahlung"),
            ) {
                basis = it
            }
            NativeDisclosure("Zeitraum", "$from – $to") {
                NativeDateField("Von", from, { from = it })
                NativeDateField("Bis", to, { to = it })
            }
        }
        if (error.isNotBlank())
            item {
                NativeStatus(error, NativeStatusTone.Failed)
                NativeTextButton({ attempt++ }) { Text("Erneut laden") }
            }
        if (report == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        report?.let { data ->
            item {
                NativeDisclosure("Summen", initiallyExpanded = true) {
                    for ((label, key) in
                        listOf(
                            (if (basis == "service") "Leistungen · Rechnungen" else "Rechnungen") to
                                "issued_minor",
                            (if (basis == "service") "Leistungen · Stornos" else "Stornos") to
                                "cancellations_minor",
                            (if (basis == "service") "Leistungsnetto" else "Rechnungsnetto") to
                                "invoice_net_minor",
                            "Bezahlt · Belegkohorte" to "paid_minor",
                            "Forderung offen" to "open_minor",
                            "Guthaben" to "credit_minor",
                            "Rückzahlung offen" to "refund_due_minor",
                            "Zahlungseingänge · Zahlungsdatum" to "payment_inflow_minor",
                            "Korrekturen · Zahlungsdatum" to "payment_reversals_minor",
                            "Historische Gegenbuchungen" to "payment_legacy_adjustments_minor",
                            "Rückzahlungen · Zahlungsdatum" to "payment_refunds_minor",
                            "Zahlungsnetto · Zahlungsdatum" to "payment_net_minor",
                        )) {
                        NativeInfoRow(label, accountingMoney(data.optLong(key)))
                    }
                }
            }
            if (basis == "service") {
                item {
                    Text(
                        "Leistungspositionen im Zeitraum",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(
                    data.optJSONArray("service_positions").objects(),
                    key = { "service:" + it.getString("id") },
                ) { line ->
                    NativeInfoRow(
                        line.optString("title").ifBlank { "Leistung" },
                        nativeDisplayDate(line.optString("service_date")) +
                            " · " +
                            accountingMoney(line.optLong("amount_minor")),
                        {
                            controller.navigate(
                                "settlement_review",
                                line.getString("settlement_id"),
                            )
                        },
                    )
                }
            }
            item {
                Text(
                    if (basis == "service") "Zugehörige Originalbelege · Gesamtbetrag"
                    else "Originalbelege · Gesamtbetrag",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val records = data.optJSONArray("records").objects()
            items(records, key = { "record:" + it.getString("id") }) { row ->
                NativeInfoRow(
                    row.optString("invoice_number").ifBlank {
                        row.optString("title", "Originalbeleg")
                    },
                    listOf(
                            nativeDisplayDate(row.optString("issue_date")),
                            accountingMoney(row.optLong("total_minor")),
                            nativeDetailStatus(row.optString("status")),
                        )
                        .joinToString(" · "),
                    { controller.navigate("settlement_review", row.getString("id")) },
                )
            }
            val payments = data.optJSONArray("payments").objects()
            item {
                Text("Geldbewegungen · Zahlungsdatum", style = MaterialTheme.typography.titleMedium)
            }
            items(payments, key = { "payment:" + it.getString("id") }) { payment ->
                NativeInfoRow(
                    nativeDisplayDate(payment.optString("date")),
                    accountingMoney(payment.optLong("amount_minor")) +
                        " · " +
                        payment.optString("note"),
                    {
                        controller.navigate("settlement_review", payment.getString("settlement_id"))
                    },
                )
            }
            if (records.isEmpty() && payments.isEmpty())
                item { Text("In diesem Zeitraum gibt es keine Vorgänge.") }
        }
    }
}

@Composable
internal fun NativeLcr(controller: NativeAppController) {
    var records by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var search by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var customer by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    val changes by
        remember(controller) {
                controller.store.nativeData().watchCommittedChanges().nativeErrors(controller)
            }
            .collectAsStateWithLifecycle(-1L)
    LaunchedEffect(changes, attempt, offset, search, from, to, customer) {
        loading = true
        try {
            val page =
                controller.query(
                    "get_lcr_work_records",
                    JSONObject().put("query", search).put("offset", offset).put("limit", 50).apply {
                        if (from.isNotBlank()) put("date_from", from)
                        if (to.isNotBlank()) put("date_to", to)
                        if (customer.isNotBlank()) put("customer_id", customer)
                    },
                )
            records = page.optJSONArray("records").objects().filter { it.optBoolean("lcr") }
            total = page.optInt("total")
            hasMore = page.optBoolean("has_more")
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "L.C.R. konnte nicht gelesen werden."
        } finally {
            loading = false
        }
    }
    val visible = records
    NativeSearchList(
        "L.C.R. suchen",
        search,
        {
            search = it
            offset = 0
        },
        "$total Arbeiten",
        bottomSearch = true,
        onFilters = { filters = !filters },
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filters)
                item {
                    NativeRecordPicker(
                        controller,
                        "customers",
                        "Kunde",
                        customer,
                        onValueChange = {
                            customer = it
                            offset = 0
                        },
                    )
                    NativeDateField(
                        "Von",
                        from,
                        {
                            from = it
                            offset = 0
                        },
                    )
                    NativeDateField(
                        "Bis",
                        to,
                        {
                            to = it
                            offset = 0
                        },
                    )
                    NativeTextButton({
                        from = ""
                        to = ""
                        customer = ""
                        offset = 0
                    }) {
                        Text("Filter zurücksetzen")
                    }
                }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error.isNotBlank())
                item {
                    NativeStatus(error, NativeStatusTone.Failed)
                    NativeTextButton({ attempt++ }) { Text("Erneut laden") }
                }
            if (!loading && records.isEmpty())
                item {
                    Text("Unter L.C.R. stehen die gekennzeichneten Originalarbeiten.")
                    NativeTextButton({ controller.navigate("worklogs") }) {
                        Text("Arbeiten öffnen")
                    }
                }
            items(visible, key = { it.getString("id") }) { row ->
                NativeInfoRow(
                    row.optString("activity").ifBlank { "Arbeit" },
                    listOf(
                            nativeDisplayDate(row.optString("work_date")),
                            row.optString("customer_name"),
                            nativeNumber(row.opt("hours")) + " Std.",
                        )
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    { controller.navigate("worklogs", row.getString("id")) },
                )
            }
            item {
                NativeActions {
                    if (offset > 0)
                        NativeTextButton(
                            { offset = (offset - 50).coerceAtLeast(0) },
                            enabled = !loading,
                        ) {
                            Text("Vorherige")
                        }
                    if (hasMore)
                        NativeTextButton({ offset += 50 }, enabled = !loading) {
                            Text("Weitere Arbeiten")
                        }
                }
            }
        }
    }
}

/** A durable operation preserves the original arguments when a response is uncertain. */
@Composable
internal fun NativeLcrToggle(
    controller: NativeAppController,
    id: String,
    onLoaded: (Boolean) -> Unit = {},
) {
    var row by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var operation by rememberSaveable(id) { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val changes by
        remember(controller) {
                controller.store.nativeData().watchCommittedChanges().nativeErrors(controller)
            }
            .collectAsStateWithLifecycle(-1L)
    LaunchedEffect(id, changes, attempt) {
        try {
            row =
                controller
                    .query("get_lcr_work_records", JSONObject().put("work_record_id", id))
                    .optJSONArray("records")
                    ?.optJSONObject(0)
            row?.let { onLoaded(it.optBoolean("lcr")) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Kennzeichnung konnte nicht gelesen werden."
        }
    }
    fun submit(args: JSONObject) {
        if (busy) return
        pending = args.toString()
        busy = true
        scope.launch {
            try {
                controller.settlementAction("lcr:$id:$operation", "set_work_record_lcr", args)
                pending = null
                operation = UUID.randomUUID().toString()
                error = ""
                attempt++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Kennzeichnung nicht bestätigt."
                if (
                    failure is NativeCallFailure &&
                        failure.detail.optString("commit_state") == "not_committed"
                ) {
                    pending = null
                    operation = UUID.randomUUID().toString()
                    attempt++
                }
            } finally {
                busy = false
            }
        }
    }
    NativeDisclosure(
        "L.C.R.",
        if (row?.optBoolean("lcr") == true) "Gekennzeichnet" else "Nicht gekennzeichnet",
        initiallyExpanded = true,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("L.C.R.", Modifier.weight(1f))
            Switch(
                row?.optBoolean("lcr") == true,
                { checked ->
                    row?.let { current ->
                        submit(
                            JSONObject()
                                .put("work_record_id", id)
                                .put("expected_record_hash", current.getString("lcr_record_hash"))
                                .put("lcr", checked)
                        )
                    }
                },
                enabled = row?.optBoolean("lcr_mutable") == true && !busy && pending == null,
                modifier = Modifier.semantics { testTag = "work-lcr" },
            )
        }
        row?.optString("lcr_block_reason")
            ?.takeIf { it.isNotBlank() }
            ?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy || row == null && error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) {
            NativeStatus(error, NativeStatusTone.Failed)
            NativeTextButton(
                { pending?.let { submit(JSONObject(it)) } ?: run { attempt++ } },
                enabled = !busy,
            ) {
                Text(if (pending == null) "Erneut laden" else "Ergebnis prüfen")
            }
        }
    }
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
                NativeTextButton({ attempt++ }) { Text("Erneut laden") }
            }
        }
}
