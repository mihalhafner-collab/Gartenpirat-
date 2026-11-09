package at.hausgarten.service

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The recipient's open work and open payments share one billing page. */
@Composable
internal fun NativeAccounting(controller: NativeAppController) {
    var year by rememberSaveable { mutableStateOf(false) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var openWork by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val changes by
        remember(controller) { controller.store.nativeData().watchCommittedChanges() }
            .collectAsStateWithLifecycle(-1L)
    LaunchedEffect(changes, attempt) {
        try {
            data = controller.query("list_settlements")
            openWork =
                controller.query(
                    "get_work_record_summary",
                    JSONObject().put("group_by_billing_customer", true),
                )
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Abrechnung nicht lesbar."
        }
    }
    val today = LocalDate.now(ZoneId.of("Europe/Vienna"))
    val records = data?.optJSONArray("records").objects()
    val period =
        records.filter {
            val date = it.optString("issue_date").ifBlank { it.optString("service_to") }
            date.startsWith(if (year) today.year.toString() else today.toString().take(7))
        }
    val invoices =
        period.filter {
            it.optString("route") == AccountingRules.BILLED &&
                it.optString("kind") == "invoice" &&
                it.optString("status") == "confirmed"
        }
    val issued = invoices.sumOf { it.optLong("total_minor") }
    val paid = invoices.sumOf { it.optLong("paid_minor") }
    val outstanding =
        invoices.sumOf { (it.optLong("total_minor") - it.optLong("paid_minor")).coerceAtLeast(0) }
    val labor =
        period
            .filter {
                it.optString("route") == AccountingRules.LABOR &&
                    it.optString("kind") == "internal" &&
                    it.optString("status") == "confirmed"
            }
            .sumOf { it.optLong("total_minor") }
    val neutral = NativeNeutral
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NativeChoice(
            "Zeitraum",
            if (year) "year" else "month",
            listOf("month" to "Monat", "year" to "Jahr"),
        ) {
            year = it == "year"
        }
        if (error.isNotBlank()) {
            NativeStatus(error, NativeStatusTone.Failed)
            NativeTextButton({ attempt++ }) { Text("Erneut laden") }
        }
        if (data == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, amount) in
                listOf("ausgestellt" to issued, "bezahlt" to paid, "offen" to outstanding)) Column(
                Modifier.weight(1f)
            ) {
                Text(
                    financeMoney(OfferMoney.decimal(amount)),
                    color = if (label == "bezahlt") NativeDone else NativeMoney,
                    fontWeight = FontWeight.Bold,
                )
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
        LinearProgressIndicator(
            progress = { if (issued > 0) (paid.toFloat() / issued).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = NativeDone,
        )
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    neutral,
                    style =
                        Stroke(
                            width = 1.dp.toPx(),
                            pathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Labor clandestino", color = neutral)
                Text(financeMoney(OfferMoney.decimal(labor)), color = neutral)
            }
        }
        NativeGroup("Offen abzurechnen") {
            val groups = openWork?.optJSONArray("billing_groups").objects()
            if (groups.isEmpty() && openWork != null)
                Text(
                    "Offene Arbeiten bilden deine nächste Abrechnung.",
                    Modifier.padding(12.dp).semantics { testTag = UiTags.EMPTY_STATE },
                )
            for (group in groups) {
                val recipient = group.optString("billing_customer_id")
                val works = group.optJSONArray("work_records").objects()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NativeInitials(group.optString("billing_customer_name"), 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.optString("billing_customer_name").ifBlank { "Empfänger fehlt" },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${works.size} Arbeiten · ${nativeNumber(group.opt("hours"))} Std.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NativeMoney,
                        )
                    }
                    NativeTextButton(
                        {
                            controller.navigate(
                                "settlement_edit",
                                args =
                                    JSONObject()
                                        .put("billing_customer_id", recipient)
                                        .put(
                                            "work_record_ids",
                                            JSONArray(works.map { it.getString("id") }),
                                        ),
                            )
                        },
                        enabled =
                            group.optBoolean("valid") &&
                                recipient.isNotEmpty() &&
                                works.isNotEmpty(),
                    ) {
                        Text("Abrechnen")
                    }
                }
                HorizontalDivider()
            }
        }
        NativeGroup("Zahlung offen") {
            val unpaid =
                records.filter {
                    it.optString("status") == "confirmed" &&
                        it.optString("kind") in setOf("invoice", "internal") &&
                        it.optLong("total_minor") > it.optLong("paid_minor")
                }
            if (unpaid.isEmpty() && data != null)
                Text("Hier stehen noch offene Zahlungen.", Modifier.padding(12.dp))
            for (record in unpaid) {
                val id = record.getString("id")
                val due =
                    runCatching {
                            if (record.optString("due_date").isNotBlank())
                                LocalDate.parse(record.getString("due_date"))
                            else {
                                val days =
                                    Regex("(\\d+)\\s*Tag", RegexOption.IGNORE_CASE)
                                        .find(record.optString("payment_terms"))
                                        ?.groupValues
                                        ?.get(1)
                                        ?.toLongOrNull()
                                if (days == null) null
                                else LocalDate.parse(record.getString("issue_date")).plusDays(days)
                            }
                        }
                        .getOrNull()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        NativeTextButton(
                            { controller.navigate("settlement_review", id) },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                record.optString("invoice_number").ifBlank {
                                    record.optString("title")
                                }
                            )
                        }
                        if (due != null)
                            Text(
                                if (due.isBefore(today))
                                    "fällig seit ${nativeDisplayDate(due.toString())}"
                                else "fällig ${nativeDisplayDate(due.toString())}",
                                color = NativeDue,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        Text(
                            financeMoney(
                                OfferMoney.decimal(
                                    record.optLong("total_minor") - record.optLong("paid_minor")
                                )
                            ),
                            color = NativeMoney,
                        )
                    }
                    NativeTextButton(
                        {
                            paying = id
                            scope.launch {
                                try {
                                    settlementPaid(controller, id)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Zahlung nicht vorgemerkt."
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
        NativeActions {
            NativeTextButton({
                scope.launch {
                    try {
                        controller.shareSettlementExport(JSONObject())
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

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

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
