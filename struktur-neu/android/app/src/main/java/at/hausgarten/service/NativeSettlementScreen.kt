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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun settlementObjects(values: JSONArray?): List<JSONObject> =
    if (values == null) emptyList()
    else (0 until values.length()).mapNotNull { values.optJSONObject(it) }

private fun settlementDay() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()

private fun settlementMoney(value: Long) = financeMoney(OfferMoney.decimal(value))

private fun settlementDraftInput(value: JSONObject): JSONObject {
    val schema =
        SettlementTools.schema("save_settlement_draft")
            .getJSONObject("properties")
            .getJSONObject("draft")
            .getJSONObject("properties")
    val result = JSONObject()
    for (field in schema.keys()) if (field != "positions" && value.has(field))
        result.put(field, value.get(field))
    val lineFields =
        schema.getJSONObject("positions").getJSONObject("items").getJSONObject("properties")
    result.put(
        "positions",
        JSONArray(
            settlementObjects(value.optJSONArray("positions")).map { line ->
                JSONObject().apply {
                    for (field in lineFields.keys()) if (line.has(field))
                        put(field, line.get(field))
                }
            }
        ),
    )
    return result
}

private fun settlementStatus(value: JSONObject) =
    when (value.optString("status")) {
        "draft" -> "Entwurf"
        "prepared" -> "Vorbereitet"
        "confirmed" -> "Bestätigt"
        "cancelled" -> "Aufgehoben"
        "abandoned" -> "Vorbereitung aufgehoben"
        else -> "Status prüfen"
    }

/** Remaining amount of a confirmed invoice or internal closure, or 0 when nothing is open. */
private fun settlementOpen(record: JSONObject): Long =
    if (
        record.optString("status") == "confirmed" &&
            record.optString("kind") in setOf("invoice", "internal")
    )
        (record.optLong("total_minor") - record.optLong("paid_minor")).coerceAtLeast(0)
    else 0L

/**
 * One tap: records the current remainder with today's date through the review screen's durable
 * operation.
 */
internal suspend fun settlementPaid(controller: NativeAppController, id: String): Long {
    val current = controller.query("get_settlement", JSONObject().put("settlement_id", id))
    val open = current.optLong("balance_minor")
    require(open > 0) { "Kein offener Betrag." }
    controller.query(
        "queue_list_action",
        JSONObject()
            .put("request_id", UUID.randomUUID().toString())
            .put("source", "action")
            .put("original_tool", "add_settlement_payment")
            .put(
                "original_args",
                JSONObject()
                    .put("settlement_id", id)
                    .put("expected_record_hash", current.getString("record_hash"))
                    .put("amount", OfferMoney.decimal(open))
                    .put("date", settlementDay())
                    .put("note", "Vollständig bezahlt"),
            ),
    )
    controller.listActions =
        SettlementRules.rows(controller.query("get_list_actions").optJSONArray("actions"))
    controller.haptic()
    return open
}

@Composable
internal fun NativeSettlements(controller: NativeAppController, selectedId: String) {
    if (selectedId.isNotEmpty()) {
        NativeSettlementReview(controller, selectedId)
        return
    }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val committed by
        remember(controller) { controller.store.nativeData().watchCommittedChanges() }
            .collectAsStateWithLifecycle(-1L)
    LaunchedEffect(attempt, committed) {
        try {
            result = controller.query("list_settlements")
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Abschlüsse nicht lesbar."
        }
    }
    val records =
        settlementObjects(result?.optJSONArray("records")).filter {
            listOf(
                    it.optString("title"),
                    it.optString("invoice_number"),
                    it.optString("issue_date"),
                )
                .any { text -> text.contains(search, true) }
        }
    NativeSearchList(
        "Abschluss oder Rechnungsnummer",
        search,
        { search = it },
        "${records.size} Einträge",
        bottomSearch = true,
    ) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NativeActions {
                    NativeButton(
                        { controller.navigate("settlement_edit") },
                        Modifier.semantics { testTag = UiTags.EMPTY_ACTION },
                    ) {
                        Text("Neue Abrechnung")
                    }
                }
            }
            if (error.isNotEmpty())
                item {
                    NativeStatus(error, NativeStatusTone.Failed)
                    NativeTextButton({ attempt++ }) { Text("Erneut laden") }
                }
            if (result == null && error.isEmpty())
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(records, key = { it.getString("id") }) { record ->
                NativeSwipeRow(
                    controller,
                    "settlement",
                    record.getString("id"),
                    canDelete = record.optString("status") == "draft",
                    positive =
                        if (record.optString("kind") == "internal" && settlementOpen(record) > 0)
                            "Bezahlt"
                        else "",
                ) {
                    NativeInfoRow(
                        record.optString("title").ifBlank { "Abschluss" },
                        listOf(
                                AccountingRules.label(record.optString("route")),
                                settlementStatus(record),
                                record.optString("invoice_number"),
                                if (record.has("total_minor"))
                                    settlementMoney(record.optLong("total_minor"))
                                else "Betrag ausstehend",
                            )
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        { controller.navigate("settlement_review", record.getString("id")) },
                    )
                    val open = settlementOpen(record)
                    when (record.optString("status")) {
                        "draft" -> NativeProgressSteps(1)
                        "prepared" -> NativeProgressSteps(2)
                        "confirmed" ->
                            NativeProgressSteps(if (open > 0) 2 else 3, paymentOpen = open > 0)
                    }
                    if (open > 0)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NativeStatus(
                                "Offen " + settlementMoney(open),
                                NativeStatusTone.Attention,
                            )
                            NativeButton(
                                {
                                    val id = record.getString("id")
                                    paying = id
                                    scope.launch {
                                        try {
                                            settlementPaid(controller, id)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            controller.message(
                                                failure.message
                                                    ?: "Zahlung nicht bestätigt. Bitte im Abschluss prüfen."
                                            )
                                        } finally {
                                            paying = ""
                                            attempt++
                                        }
                                    }
                                },
                                enabled =
                                    paying.isEmpty() &&
                                        controller.listActions.none { queued ->
                                            ("settlement" to record.getString("id")) in
                                                ListActionTools.targets(queued) &&
                                                queued.optString("state") in
                                                    setOf("queued", "applying", "uncertain")
                                        },
                            ) {
                                Text(
                                    if (paying == record.getString("id")) "Wird erfasst …"
                                    else "Bezahlt"
                                )
                            }
                        }
                    else if (
                        record.optString("status") == "confirmed" &&
                            record.optLong("total_minor") > 0
                    )
                        NativeStatus("Bezahlt", NativeStatusTone.Confirmed)
                }
            }
            if (result != null && records.isEmpty())
                item {
                    Text(
                        if (search.isBlank())
                            "Abschlüsse verbinden erbrachte Leistungen mit Belegen und Zahlungen."
                        else "Keine Abschlüsse für diese Suche."
                    )
                }
        }
    }
}

/** The editable draft is the preview; issuing keeps the two existing durable commits. */
@Composable
internal fun NativeSettlementEditor(
    controller: NativeAppController,
    id: String,
    onFinished: ((Boolean) -> Unit)? = null,
) {
    val recipient = if (id.isEmpty()) controller.route.args.optString("billing_customer_id") else ""
    val key = "settlement-editor:$id" + if (recipient.isNotEmpty()) ":$recipient" else ""
    var raw by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var savedHash by rememberSaveable(key) { mutableStateOf("") }
    var pending by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var operationId by rememberSaveable(key) { mutableStateOf(UUID.randomUUID().toString()) }
    var context by remember { mutableStateOf<JSONObject?>(null) }
    var priorRecords by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    var fieldError by remember { mutableStateOf("") }
    LaunchedEffect(field) { fieldError = "" }
    var loadAttempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val undo = remember { SnackbarHostState() }
    LaunchedEffect(key, loadAttempt) {
        try {
            context = controller.query("get_settlement_context")
            priorRecords =
                settlementObjects(controller.query("list_settlements").optJSONArray("records"))
            val local = controller.draft(key)
            raw = local?.optJSONObject("values")?.toString()
            savedHash = local?.optString("record_hash").orEmpty()
            pending = local?.optJSONObject("pending_save")?.toString()
            operationId = local?.optString("operation_id", operationId) ?: operationId
            if (raw == null && id.isNotEmpty()) {
                val data = controller.query("get_settlement", JSONObject().put("settlement_id", id))
                val row = data.getJSONObject("record")
                require(row.optString("status") == "draft") {
                    "Dieser Abschluss ist bereits ausgestellt."
                }
                raw = row.toString()
                savedHash = data.getString("record_hash")
            }
            if (raw == null) {
                val business = context!!.optJSONObject("business_settings") ?: JSONObject()
                val prefillArgs =
                    JSONObject(controller.route.args.toString()).apply {
                        put(
                            "billing_interval",
                            optString("billing_interval").ifBlank {
                                business.optString("billing_interval").ifBlank { "minute" }
                            },
                        )
                        put(
                            "work_record_ids",
                            JSONArray(
                                settlementObjects(context!!.optJSONArray("worklogs"))
                                    .filter {
                                        BillingRules.recipient(it) == recipient &&
                                            it.optInt("billed", 0) == 0
                                    }
                                    .map { it.getString("id") }
                            ),
                        )
                    }
                raw =
                    SettlementRules.prefill(
                            prefillArgs,
                            context!!.optJSONArray("worklogs"),
                            priorRecords,
                            business.optString("hourly_rate"),
                        )
                        .apply {
                            if (optString("payment_terms").isBlank())
                                put("payment_terms", business.optString("default_payment_terms"))
                            if (optString("payment_details").isBlank())
                                put(
                                    "payment_details",
                                    business.optString("payment_details").ifBlank {
                                        listOf(
                                                business.optString("business_iban"),
                                                business.optString("business_bic"),
                                            )
                                            .filter(String::isNotBlank)
                                            .joinToString(" · ")
                                    },
                                )
                        }
                        .toString()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Entwurf nicht lesbar."
        }
    }
    fun persist(value: JSONObject) {
        raw = value.toString()
        controller.saveDraft(
            key,
            JSONObject()
                .put("values", value)
                .put("record_hash", savedHash)
                .put("operation_id", operationId)
                .apply { pending?.let { put("pending_save", JSONObject(it)) } },
        )
    }
    fun changed(value: JSONObject, before: String) {
        persist(value)
        scope.launch {
            undo.currentSnackbarData?.dismiss()
            val result =
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    undo.showSnackbar(
                        "Gespeichert",
                        "Rückgängig",
                        duration = SnackbarDuration.Indefinite,
                    )
                }
            if (result == SnackbarResult.ActionPerformed) persist(JSONObject(before))
            else undo.currentSnackbarData?.dismiss()
        }
    }
    fun save(issue: Boolean) {
        scope.launch {
            busy = true
            error = ""
            var savedId = id
            var savedSuccessfully = false
            try {
                val args =
                    pending?.let(::JSONObject)
                        ?: JSONObject()
                            .put("draft", settlementDraftInput(JSONObject(raw!!)))
                            .apply {
                                if (id.isNotEmpty())
                                    put("settlement_id", id).put("expected_record_hash", savedHash)
                            }
                pending = args.toString()
                persist(JSONObject(raw!!))
                controller.flushDrafts()
                val saved =
                    controller.settlementAction(
                        "save:$id:$operationId",
                        "save_settlement_draft",
                        args,
                    )
                savedId = saved.getString("settlement_id")
                savedSuccessfully = true
                if (issue) {
                    val reviewed =
                        controller.query(
                            "get_settlement",
                            JSONObject().put("settlement_id", savedId),
                        )
                    controller.settlementAction(
                        "issue:$savedId:$operationId:prepare",
                        "prepare_settlement",
                        JSONObject()
                            .put("settlement_id", savedId)
                            .put("expected_review_hash", reviewed.getString("review_hash"))
                            .put("confirmed", true)
                            .put("domestic_eur_confirmed", true)
                            .put("issuer_details_confirmed", true)
                            .put("electronic_delivery_accepted", false),
                    )
                    controller.settlementAction(
                        "issue:$savedId:$operationId:complete",
                        "complete_settlement",
                        JSONObject().put("settlement_id", savedId),
                    )
                    controller.haptic()
                }
                controller.removeDraft(key)
                raw = null
                pending = null
                if (issue) {
                    if (onFinished != null) onFinished(true)
                    else
                        controller.navigate(
                            "settlement_review",
                            savedId,
                            args = JSONObject().put("_share_after_issue", true),
                        )
                } else {
                    onFinished?.invoke(false)
                    controller.navigate("settlements")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (
                    failure is NativeCallFailure &&
                        failure.detail.optString("commit_state") == "not_committed"
                ) {
                    pending = null
                    raw?.let { persist(JSONObject(it)) }
                    controller.flushDrafts()
                }
                error = failure.message ?: "Abrechnung nicht ausgestellt."
                if (savedSuccessfully) {
                    controller.removeDraft(key)
                    raw = null
                    pending = null
                    loadAttempt++
                    onFinished?.invoke(false)
                    controller.navigate("settlement_review", savedId)
                    controller.message(error)
                }
            } finally {
                busy = false
            }
        }
    }
    val draft = raw?.let(::JSONObject)
    Column(Modifier.fillMaxSize().semantics { testTag = UiTags.settlementState("draft") }) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error.isNotBlank()) NativeStatus(error, NativeStatusTone.Failed)
            if (draft == null) {
                if (error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val editable = !busy && pending == null
                NativeChoice(
                    "Art",
                    draft.optString("route"),
                    listOf(
                        AccountingRules.BILLED to "Rechnung",
                        AccountingRules.LABOR to "Labor clandestino",
                    ),
                ) {
                    persist(JSONObject(raw!!).put("route", it))
                }
                NativeGroup("Rechnungsempfänger") {
                    NativeRecordPicker(
                        controller,
                        "customers",
                        "Empfänger",
                        draft.optString("billing_customer_id"),
                        onValueChange = { selected ->
                            val next = JSONObject(raw!!).put("billing_customer_id", selected)
                            val works =
                                settlementObjects(context?.optJSONArray("worklogs")).filter {
                                    it.optString("billing_customer_id").ifBlank {
                                        it.optString("customer_id")
                                    } == selected && it.optBoolean("billable", true)
                                }
                            val fresh =
                                SettlementRules.prefill(
                                    JSONObject()
                                        .put("billing_customer_id", selected)
                                        .put(
                                            "work_record_ids",
                                            JSONArray(works.map { it.getString("id") }),
                                        ),
                                    context?.optJSONArray("worklogs"),
                                    priorRecords,
                                    context
                                        ?.optJSONObject("business_settings")
                                        ?.optString("hourly_rate")
                                        .orEmpty(),
                                )
                            for (name in
                                listOf(
                                    "positions",
                                    "service_from",
                                    "service_to",
                                    "customer_id",
                                    "title",
                                )) if (fresh.has(name)) next.put(name, fresh.get(name))
                            persist(next)
                        },
                    )
                }
                NativeGroup("Leistungen") {
                    NativeKeyValue("Bezeichnung", draft.optString("title")) {
                        if (editable) field = Triple(-1, "title", "Bezeichnung")
                    }
                    NativeKeyValue("Von", nativeDisplayDate(draft.optString("service_from"))) {
                        if (editable) field = Triple(-1, "service_from", "Leistungsbeginn")
                    }
                    NativeKeyValue("Bis", nativeDisplayDate(draft.optString("service_to"))) {
                        if (editable) field = Triple(-1, "service_to", "Leistungsende")
                    }
                }
                NativeChoice(
                    "Takt",
                    draft.optString("billing_interval", "minute"),
                    listOf(
                        "minute" to "Minutengenau",
                        "tenth_hour" to "0,1 Std.",
                        "quarter_hour" to "15 Min.",
                    ),
                ) { tick ->
                    val next = JSONObject(raw!!).put("billing_interval", tick)
                    for (position in settlementObjects(next.optJSONArray("positions"))) {
                        val work =
                            settlementObjects(context?.optJSONArray("worklogs")).firstOrNull {
                                position.optString("source_collection") == "worklogs" &&
                                    it.optString("id") == position.optString("source_id")
                            }
                        if (work != null)
                            position.put(
                                "quantity",
                                SettlementRules.billableQuantity(work.optString("hours"), tick),
                            )
                    }
                    persist(next)
                }
                val positions = settlementObjects(draft.optJSONArray("positions"))
                var total = java.math.BigDecimal.ZERO
                var completePrices = positions.isNotEmpty()
                for ((index, position) in positions.withIndex()) {
                    val amount =
                        runCatching {
                                java.math
                                    .BigDecimal(position.optString("quantity").replace(',', '.'))
                                    .multiply(
                                        java.math.BigDecimal(
                                            position.optString("price").replace(',', '.')
                                        )
                                    )
                                    .setScale(2, java.math.RoundingMode.HALF_UP)
                            }
                            .getOrNull()
                    if (amount == null) completePrices = false else total = total.add(amount)
                    NativeGroup(
                        position.optString("description").ifBlank { "Position ${index + 1}" }
                    ) {
                        NativeKeyValue("Leistung", position.optString("title")) {
                            if (editable) field = Triple(index, "title", "Leistung")
                        }
                        NativeKeyValue(
                            "Menge · ${position.optString("unit")}",
                            nativeNumber(position.opt("quantity")),
                        ) {
                            if (editable) field = Triple(index, "quantity", "Menge")
                        }
                        if (position.optString("price").isBlank()) {
                            NativeTextButton(
                                {
                                    if (editable)
                                        field = Triple(index, "price", "Preis je Einheit (€)")
                                },
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                            ) {
                                Text("Preis je Einheit (€)")
                            }
                        } else
                            NativeKeyValue(
                                "Preis je Einheit",
                                financeMoney(position.opt("price")),
                                money = true,
                            ) {
                                if (editable) field = Triple(index, "price", "Preis je Einheit (€)")
                            }
                        if (amount != null)
                            Text(
                                financeMoney(amount),
                                Modifier.padding(horizontal = 16.dp),
                                color = NativeMoney,
                            )
                        NativeTextButton(
                            {
                                val next = JSONObject(raw!!)
                                next.put(
                                    "positions",
                                    JSONArray(positions.filterIndexed { i, _ -> i != index }),
                                )
                                changed(next, raw!!)
                            },
                            enabled = editable,
                        ) {
                            Text("Abwählen")
                        }
                    }
                }
                val selected = positions.map { it.optString("source_id") }.toSet()
                val recipientId = draft.optString("billing_customer_id")
                val available =
                    settlementObjects(context?.optJSONArray("worklogs")).filter {
                        it.optString("billing_customer_id").ifBlank {
                            it.optString("customer_id")
                        } == recipientId &&
                            it.optString("id") !in selected &&
                            it.optBoolean("billable", true)
                    }
                for (work in available) NativeTextButton(
                    {
                        val next = JSONObject(raw!!)
                        val added =
                            SettlementRules.prefill(
                                JSONObject()
                                    .put("billing_customer_id", recipientId)
                                    .put("work_record_ids", JSONArray().put(work.getString("id"))),
                                context?.optJSONArray("worklogs"),
                                priorRecords,
                                context
                                    ?.optJSONObject("business_settings")
                                    ?.optString("hourly_rate")
                                    .orEmpty(),
                            )
                        for (line in settlementObjects(added.optJSONArray("positions"))) next
                            .getJSONArray("positions")
                            .put(line)
                        persist(next)
                    },
                    enabled = editable,
                ) {
                    Text(
                        work.optString("activity") +
                            " · " +
                            nativeDisplayDate(work.optString("work_date"))
                    )
                }
                if (positions.isEmpty()) Text("Wähle die erbrachten Arbeiten für diese Abrechnung.")
                Text(
                    "Gesamtbetrag: ${if (completePrices) financeMoney(total) else ""}",
                    color = if (completePrices) NativeMoney else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Umsatzsteuerbefreit gemäß § 6 Abs. 1 Z 27 UStG")
                NativeGroup("Zahlung") {
                    NativeKeyValue("Zahlungsziel", draft.optString("payment_terms")) {
                        if (editable) field = Triple(-1, "payment_terms", "Zahlungsziel")
                    }
                    NativeKeyValue("Bankverbindung", draft.optString("payment_details")) {
                        if (editable) field = Triple(-1, "payment_details", "Bankverbindung")
                    }
                }
                if (draft.optString("route") == AccountingRules.BILLED)
                    for (missing in
                        settlementObjects(
                            context?.optJSONArray("invoice_missing_fields")
                        )) NativeTextButton(
                        {
                            controller.navigate(
                                "settings",
                                args = JSONObject().put("_field", missing.getString("field")),
                            )
                        },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text(missing.getString("label"))
                    }
                NativeActions {
                    if (draft.optString("route") == AccountingRules.BILLED)
                        NativeTextButton(
                            {
                                scope.launch {
                                    try {
                                        controller.settlementPreview(
                                            settlementDraftInput(JSONObject(raw!!))
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        error = failure.message ?: "Vorschau nicht geöffnet."
                                    }
                                }
                            },
                            enabled = editable && completePrices,
                        ) {
                            Text("PDF ansehen")
                        }
                    NativeTextButton({ save(false) }, enabled = editable) {
                        Text("Entwurf sichern")
                    }
                }
            }
        }
        SnackbarHost(undo)
        val prices = settlementObjects(draft?.optJSONArray("positions"))
        val ready =
            draft != null &&
                prices.isNotEmpty() &&
                prices.all {
                    it.optString("price").isNotBlank() && it.optString("quantity").isNotBlank()
                } &&
                (draft.optString("route") != AccountingRules.BILLED ||
                    context?.optJSONArray("invoice_missing_fields")?.length() == 0)
        NativeActionBar(
            if (draft?.optString("route") == AccountingRules.LABOR) "Bestätigen" else "Ausstellen",
            { save(true) },
            enabled = ready && !busy && pending == null,
        )
    }
    field?.let { current ->
        val value =
            if (current.first < 0) draft?.optString(current.second).orEmpty()
            else
                draft
                    ?.optJSONArray("positions")
                    ?.getJSONObject(current.first)
                    ?.optString(current.second)
                    .orEmpty()
        NativeValueSheet(
            current.third,
            value,
            keyboardType =
                if (current.second in setOf("price", "quantity"))
                    androidx.compose.ui.text.input.KeyboardType.Decimal
                else androidx.compose.ui.text.input.KeyboardType.Text,
            error = fieldError,
            onClose = { entered ->
                val valid = runCatching {
                    if (entered.isNotBlank() && current.second in setOf("price", "quantity"))
                        require(entered.replace(',', '.').toBigDecimal().signum() >= 0) {
                            "Bitte einen positiven Betrag eingeben."
                        }
                    if (
                        entered.isNotBlank() &&
                            current.second in setOf("service_from", "service_to")
                    )
                        LocalDate.parse(entered)
                }
                if (valid.isFailure) fieldError = "Bitte einen gültigen Wert eingeben."
                else {
                    field = null
                    if (entered != value && raw != null) {
                        val before = raw!!
                        val next = JSONObject(before)
                        if (current.first < 0) next.put(current.second, entered)
                        else
                            next
                                .getJSONArray("positions")
                                .getJSONObject(current.first)
                                .put(current.second, entered)
                        changed(next, before)
                    }
                }
            },
        )
    }
}

@Composable
private fun SettlementText(
    label: String,
    value: String,
    enabled: Boolean = true,
    multiline: Boolean = false,
    change: (String) -> Unit,
) {
    NativeField(label, value, change, multiline = multiline, enabled = enabled)
}

@Composable
private fun SettlementAddress(title: String, row: JSONObject?, issuer: Boolean = false) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    val fields =
        if (issuer)
            listOf(
                "business_name",
                "owner_name",
                "business_street",
                "business_postal_code",
                "business_city",
                "business_legal_form",
                "business_legal_info",
                "business_register_number",
                "business_register_court",
            )
        else listOf("name", "company", "street", "postal_code", "city")
    val text =
        fields.mapNotNull { row?.optString(it)?.takeIf(String::isNotBlank) }.joinToString("\n")
    Text(text.ifBlank { "Angaben fehlen" })
}

/** Review reads current dependencies; closing uses exactly that review hash. */
@Composable
internal fun NativeSettlementReview(controller: NativeAppController, id: String) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var reviewedHash by rememberSaveable(id) { mutableStateOf("") }
    var editorOpen by rememberSaveable(id) { mutableStateOf(false) }
    val committed by
        remember(controller) { controller.store.nativeData().watchCommittedChanges() }
            .collectAsStateWithLifecycle(-1L)
    var consentStored by remember { mutableStateOf(false) }
    var consentDialog by remember { mutableStateOf(false) }
    var shareRequested by
        rememberSaveable(id) {
            mutableStateOf(controller.route.args.optBoolean("_share_after_issue"))
        }
    var abandoning by rememberSaveable(id) { mutableStateOf(false) }
    var correction by rememberSaveable(id) { mutableStateOf(false) }
    var reason by rememberSaveable(id) { mutableStateOf("") }
    var abandonmentReason by rememberSaveable(id) { mutableStateOf("") }
    var evidence by rememberSaveable(id) { mutableStateOf("") }
    var payment by rememberSaveable(id) { mutableStateOf(false) }
    var amount by rememberSaveable(id) { mutableStateOf("") }
    var day by rememberSaveable(id) { mutableStateOf(settlementDay()) }
    var note by rememberSaveable(id) { mutableStateOf("") }
    var reversal by rememberSaveable(id) { mutableStateOf("") }
    var restoreEvidence by rememberSaveable(id) { mutableStateOf("") }
    var delivery by rememberSaveable(id) { mutableStateOf(false) }
    var deliveryDay by rememberSaveable(id) { mutableStateOf(settlementDay()) }
    var deliveryEvidence by rememberSaveable(id) { mutableStateOf("") }
    var context by remember { mutableStateOf<JSONObject?>(null) }
    var original by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, attempt, committed) {
        try {
            val loaded = controller.query("get_settlement", JSONObject().put("settlement_id", id))
            if (reviewedHash != loaded.optString("review_hash")) {
                reviewedHash = loaded.optString("review_hash")
            }
            if (loaded.optJSONObject("record")?.optString("status") == "draft") editorOpen = true
            context = controller.query("get_settlement_context")
            val recipient =
                loaded.optJSONObject("record")?.optString("billing_customer_id").orEmpty()
            consentStored =
                settlementObjects(context?.optJSONArray("customers"))
                    .firstOrNull { it.optString("id") == recipient }
                    ?.optBoolean("electronic_invoice_consent") == true
            data = loaded
            val operation = controller.draft("settlement-review:$id")
            original = operation?.optJSONObject("pending")?.toString()
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Abschluss nicht lesbar."
        }
    }
    fun act(tool: String, args: JSONObject, after: (JSONObject) -> Unit = { attempt++ }) {
        scope.launch {
            busy = true
            error = ""
            try {
                val operation =
                    original?.let(::JSONObject)
                        ?: JSONObject()
                            .put("tool", tool)
                            .put("args", args)
                            .put("operation_id", UUID.randomUUID().toString())
                original = operation.toString()
                controller.saveDraft(
                    "settlement-review:$id",
                    JSONObject().put("pending", operation),
                )
                controller.flushDrafts()
                val result =
                    controller.settlementAction(
                        "review:$id:${operation.getString("operation_id")}",
                        operation.getString("tool"),
                        operation.getJSONObject("args"),
                    )
                controller.removeDraft("settlement-review:$id")
                original = null
                after(result)
                if (tool != "prepare_settlement")
                    controller.message(
                        "Abschluss gespeichert.",
                        NativeRoute(
                            "settlement_review",
                            result.optString("settlement_id", id),
                            "detail",
                        ),
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (
                    failure is NativeCallFailure &&
                        failure.detail.optString("commit_state") == "not_committed"
                ) {
                    original = null
                    controller.removeDraft("settlement-review:$id")
                }
                error =
                    failure.message
                        ?: "Vorgang noch nicht bestätigt. Mit denselben Angaben erneut prüfen."
            } finally {
                busy = false
            }
        }
    }
    var inspectionAttempt by remember { mutableIntStateOf(0) }
    val retainedOperation = original?.let(::JSONObject)
    val retainedKey = retainedOperation?.let { "review:$id:${it.getString("operation_id")}" }
    val retainedArgs =
        retainedOperation?.getJSONObject("args")?.let {
            JSONObject(it.toString())
                .put("request_id", "native_settlement_" + PixelMcpWriteTools.hash(retainedKey))
        }
    val inspection =
        rememberNativePendingInspection(
            controller,
            retainedOperation?.optString("tool").orEmpty(),
            retainedArgs,
            !busy,
            inspectionAttempt,
        ) { proof ->
            controller.removeDraft("settlement-action:$retainedKey")
            controller.removeDraft("settlement-review:$id")
            original = null
            val target = proof.optJSONObject("result")?.optString("settlement_id", id) ?: id
            if (target != id) controller.navigate("settlement_review", target) else attempt++
            controller.message(
                "Abschluss gespeichert.",
                NativeRoute("settlement_review", target, "detail"),
            )
        }
    fun document(share: Boolean) {
        if (share && !consentStored) {
            consentDialog = true
            return
        }
        scope.launch {
            try {
                controller.settlementDocument(id, share)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = nativeActionError(failure, "PDF nicht geöffnet.")
            }
        }
    }
    LaunchedEffect(controller.route.args.toString()) {
        if (controller.route.args.optBoolean("_share_after_issue")) shareRequested = true
    }
    LaunchedEffect(data, shareRequested) {
        val record = data?.optJSONObject("record")
        if (shareRequested && record?.optString("status") == "confirmed") {
            shareRequested = false
            if (record.optString("route") == AccountingRules.BILLED) document(true)
        }
    }
    if (consentDialog)
        AlertDialog(
            onDismissRequest = { consentDialog = false },
            title = { Text("E-Rechnung") },
            text = { Text("Stimmt der Kunde der elektronischen Rechnung zu?") },
            confirmButton = {
                NativeTextButton(
                    {
                        scope.launch {
                            busy = true
                            try {
                                val recipient =
                                    data!!.getJSONObject("record").getString("billing_customer_id")
                                val customer =
                                    controller.query(
                                        "get_customer",
                                        JSONObject().put("customer_id", recipient),
                                    )
                                controller.query(
                                    "update_customer",
                                    JSONObject()
                                        .put("request_id", UUID.randomUUID().toString())
                                        .put("customer_id", recipient)
                                        .put(
                                            "expected_record_hash",
                                            PixelMcpWriteTools.hash(
                                                PixelMcpEntityWriteTools.recordForHash(
                                                    "customer",
                                                    customer,
                                                )
                                            ),
                                        )
                                        .put(
                                            "updates",
                                            JSONObject().put("electronic_invoice_consent", true),
                                        ),
                                )
                                consentStored = true
                                consentDialog = false
                                document(true)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "Zustimmung nicht gespeichert."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Zustimmung speichern")
                }
            },
            dismissButton = { NativeTextButton({ consentDialog = false }) { Text("Später") } },
        )
    if (editorOpen && original == null) {
        NativeSettlementEditor(controller, id) { issued ->
            editorOpen = false
            if (issued) shareRequested = true
            attempt++
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (original != null) NativeInspectionBanner(inspection) { inspectionAttempt++ }
            if (error.isNotEmpty() && original == null) {
                NativeStatus(error, NativeStatusTone.Failed)
                NativeTextButton({ attempt++ }) { Text("Aktuellen Stand laden") }
            }
            val current = data
            if (current == null) {
                if (error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val record = current.getJSONObject("record")
                val status = record.optString("status")
                val invoice = record.optString("route") == AccountingRules.BILLED
                NativeDetailHeading(
                    record.optString("title"),
                    buildList {
                        val enabled = !busy && original == null
                        if (status == "prepared")
                            add(
                                NativeDetailAction(
                                    "settlement-abandon",
                                    "Vorbereitung aufheben",
                                    enabled,
                                    { abandoning = !abandoning },
                                )
                            )
                        if (status in setOf("confirmed", "cancelled")) {
                            if (invoice) {
                                add(
                                    NativeDetailAction(
                                        "settlement-pdf",
                                        "PDF ansehen",
                                        enabled,
                                        { document(false) },
                                    )
                                )
                                add(
                                    NativeDetailAction(
                                        "settlement-share",
                                        "Teilen",
                                        enabled,
                                        { document(true) },
                                    )
                                )
                                add(
                                    NativeDetailAction(
                                        "settlement-delivery",
                                        "Tatsächlichen Zugang dokumentieren",
                                        enabled,
                                        { delivery = !delivery },
                                    )
                                )
                            }
                            if (record.optString("kind") in setOf("invoice", "internal")) {
                                add(
                                    NativeDetailAction(
                                        "settlement-payment",
                                        "Teilzahlung oder anderes Datum",
                                        enabled,
                                        { payment = !payment },
                                    )
                                )
                                for (entry in
                                    settlementObjects(
                                        current.optJSONArray("payments")
                                            ?: record.optJSONArray("payments")
                                    )) if (entry.optLong("amount_minor") > 0L)
                                    add(
                                        NativeDetailAction(
                                            "settlement-reverse:${entry.getString("id")}",
                                            "Zahlung vom ${nativeDisplayDate(entry.optString("date"))} gegenbuchen",
                                            enabled,
                                            {
                                                payment = true
                                                reversal = entry.getString("id")
                                                amount =
                                                    OfferMoney.decimal(
                                                        -entry.getLong("amount_minor")
                                                    )
                                                note = "Gegenbuchung"
                                            },
                                        )
                                    )
                            }
                            if (
                                status == "confirmed" &&
                                    record.optString("kind") !in setOf("cancellation", "void")
                            )
                                add(
                                    NativeDetailAction(
                                        "settlement-correction",
                                        if (invoice) "Vollständige Stornorechnung vorbereiten"
                                        else "Interne Abrechnung aufheben",
                                        enabled,
                                        { correction = !correction },
                                    )
                                )
                        }
                    },
                )
                NativeContext(
                    controller,
                    JSONObject().put("customer_id", record.optString("billing_customer_id")),
                    customerLabel = "Rechnungsempfänger",
                    heading = true,
                )
                val relatedContexts =
                    settlementObjects(record.optJSONArray("source_snapshots"))
                        .mapNotNull { it.optJSONObject("record") }
                        .map { row ->
                            JSONObject().apply {
                                for (key in listOf("property_id", "order_id", "visit_id")) if (
                                    row.optString(key).isNotBlank()
                                )
                                    put(key, row.getString(key))
                            }
                        }
                        .filter { it.length() > 0 }
                        .distinctBy { it.toString() }
                for (related in relatedContexts) NativeContext(controller, related)
                Box(Modifier.semantics { testTag = UiTags.settlementState(status) }) {
                    NativeStatus(settlementStatus(record))
                }
                Text(if (invoice) "Rechnung" else "Labor clandestino · interner Nachweis")
                if (record.optString("invoice_number").isNotBlank())
                    Text("Rechnungsnummer: ${record.getString("invoice_number")}")
                if (record.optString("original_number").isNotBlank())
                    Text("Storno zu ${record.getString("original_number")}")
                Text(
                    "${if (invoice) "Rechnungsdatum" else "Datum"}: ${record.optString("issue_date").ifBlank { settlementDay() }}"
                )
                Text(
                    "Leistung: ${record.optString("service_from")} – ${record.optString("service_to")}"
                )
                SettlementAddress(
                    "Aussteller",
                    record.optJSONObject("issuer")
                        ?: current.optJSONObject("issuer")
                        ?: context?.optJSONObject("business_settings"),
                    true,
                )
                SettlementAddress(
                    "Rechtlicher Empfänger",
                    record.optJSONObject("recipient") ?: current.optJSONObject("recipient"),
                )
                for ((index, line) in
                    settlementObjects(record.optJSONArray("positions")).withIndex()) {
                    HorizontalDivider()
                    Text(
                        "${index + 1}. ${line.optString("title")}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (line.optString("description").isNotBlank())
                        Text(line.getString("description"))
                    val sourceCollection = line.optString("source_collection")
                    if (sourceCollection.isNotEmpty()) {
                        val source =
                            settlementObjects(record.optJSONArray("source_snapshots"))
                                .firstOrNull {
                                    it.optString("collection") == sourceCollection &&
                                        it.optString("id") == line.optString("source_id")
                                }
                                ?.optJSONObject("record")
                        NativeTextButton({
                            controller.navigate(sourceCollection, line.getString("source_id"))
                        }) {
                            Text(
                                "Quelle: " +
                                    (source
                                        ?.optString(
                                            if (sourceCollection == "worklogs") "activity"
                                            else "title"
                                        )
                                        ?.takeIf(String::isNotBlank)
                                        ?: if (sourceCollection == "worklogs") "Arbeitsaufzeichnung"
                                        else "Auftrag")
                            )
                        }
                    }
                    Text(
                        "${line.optString("quantity")} ${line.optString("unit")} × ${line.optString("price")} €"
                    )
                    if (line.has("amount_minor") && !line.isNull("amount_minor"))
                        Text(settlementMoney(line.getLong("amount_minor")))
                    else Text("Betrag ausstehend")
                }
                HorizontalDivider()
                Text(
                    if (record.has("total_minor"))
                        "Gesamt: ${settlementMoney(record.getLong("total_minor"))}"
                    else "Gesamtbetrag ausstehend",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text("Umsatzsteuerbefreit gemäß § 6 Abs. 1 Z 27 UStG")
                Text(record.optString("payment_terms"))
                Text(record.optString("payment_details"))
                val sources = record.optJSONArray("sources") ?: JSONArray()
                val restored =
                    settlementObjects(
                        context?.optJSONArray("sources_requiring_review")
                            ?: context?.optJSONArray("restored_sources")
                    )
                val reviewSources = linkedMapOf<String, JSONObject>()
                for (source in settlementObjects(sources)) {
                    reviewSources[source.getString("collection") + ":" + source.getString("id")] =
                        source
                    if (source.optString("collection") == "orders") {
                        val snapshot =
                            settlementObjects(record.optJSONArray("source_snapshots")).firstOrNull {
                                it.optString("collection") == "orders" &&
                                    it.optString("id") == source.optString("id")
                            }
                        val included = snapshot?.optJSONArray("included_work_ids")
                        val workIds =
                            if (included != null)
                                (0 until included.length()).map { included.getString(it) }
                            else
                                settlementObjects(context?.optJSONArray("worklogs"))
                                    .filter { it.optString("order_id") == source.optString("id") }
                                    .map { it.getString("id") }
                        for (workId in workIds) reviewSources["worklogs:$workId"] =
                            JSONObject().put("collection", "worklogs").put("id", workId)
                    }
                }
                val affected =
                    reviewSources.values.filter { source ->
                        restored.any {
                            it.optString("collection") == source.optString("collection") &&
                                it.optString("id") == source.optString("id")
                        }
                    }
                if (
                    status in setOf("draft", "prepared") &&
                        (affected.isNotEmpty() || current.optBoolean("requires_restore_review"))
                ) {
                    NativeStatus(
                        "Wiederhergestellte Quellen: Belegstand prüfen und Nachweis ergänzen.",
                        NativeStatusTone.Attention,
                    )
                    SettlementText(
                        "Aktuellen Belegstand geprüft: Nachweis und Ergebnis",
                        restoreEvidence,
                        !busy && original == null,
                        true,
                    ) {
                        restoreEvidence = it
                    }
                    NativeTextButton(
                        {
                            act(
                                "review_restored_sources",
                                JSONObject()
                                    .put("sources", JSONArray(affected))
                                    .put("settlement_id", id)
                                    .put("evidence", restoreEvidence)
                                    .put("confirmed", true),
                            )
                        },
                        enabled = restoreEvidence.isNotBlank() && !busy && original == null,
                    ) {
                        Text("Aktuellen Belegabgleich bestätigen")
                    }
                }
                if (status == "prepared" && abandoning) {
                    SettlementText(
                        "Grund für das Aufheben der Vorbereitung",
                        abandonmentReason,
                        !busy && original == null,
                        true,
                    ) {
                        abandonmentReason = it
                    }

                    NativeTextButton(
                        {
                            act(
                                "abandon_prepared_settlement",
                                JSONObject()
                                    .put("settlement_id", id)
                                    .put("expected_record_hash", current.getString("record_hash"))
                                    .put("reason", abandonmentReason)
                                    .put("confirmed", true),
                            )
                        },
                        enabled =
                            !busy &&
                                original == null &&
                                abandonmentReason.isNotBlank() &&
                                !current.optBoolean("requires_restore_review"),
                    ) {
                        Text("Vorbereitung aufheben")
                    }
                }
                if (status in setOf("confirmed", "cancelled")) {

                    if (invoice) {
                        for (proof in
                            settlementObjects(current.optJSONArray("delivery_evidence"))) Text(
                            "${proof.optString("date")} · ${proof.optString("evidence")}"
                        )
                        if (delivery) {
                            NativeDateField(
                                "Nachweisdatum",
                                deliveryDay,
                                { deliveryDay = it },
                                required = true,
                                enabled = !busy && original == null,
                            )
                            SettlementText(
                                "Zugangsnachweis / Berichtigung beim Empfänger",
                                deliveryEvidence,
                                !busy && original == null,
                                true,
                            ) {
                                deliveryEvidence = it
                            }
                            NativeTextButton(
                                {
                                    act(
                                        "record_settlement_delivery",
                                        JSONObject()
                                            .put("settlement_id", id)
                                            .put(
                                                "expected_record_hash",
                                                current.getString("record_hash"),
                                            )
                                            .put("date", deliveryDay)
                                            .put("evidence", deliveryEvidence),
                                    ) {
                                        delivery = false
                                        deliveryEvidence = ""
                                        attempt++
                                    }
                                },
                                enabled = !busy && original == null && deliveryEvidence.isNotBlank(),
                            ) {
                                Text("Zugangsnachweis speichern")
                            }
                        }
                    }
                    if (record.optString("kind") in setOf("invoice", "internal")) {
                        Text(
                            "Erfasst bezahlt: ${settlementMoney(current.optLong("paid_minor"))} · Rest: ${settlementMoney(current.optLong("balance_minor"))}"
                        )
                        val payments =
                            settlementObjects(
                                current.optJSONArray("payments") ?: record.optJSONArray("payments")
                            )
                        for (entry in payments) {
                            Text(
                                "${entry.optString("date")} · ${settlementMoney(entry.optLong("amount_minor"))} · ${entry.optString("note")}"
                            )
                        }
                        if (payment) {
                            if (reversal.isNotEmpty())
                                NativeStatus(
                                    "Gegenbuchung zur gewählten Zahlung",
                                    NativeStatusTone.Attention,
                                )
                            SettlementText("Betrag (€)", amount, !busy && original == null) {
                                amount = it
                            }
                            NativeDateField(
                                "Zahlungsdatum",
                                day,
                                { day = it },
                                required = true,
                                enabled = !busy && original == null,
                            )
                            SettlementText("Zahlungsnotiz", note, !busy && original == null, true) {
                                note = it
                            }
                        }
                    }
                    if (
                        status == "confirmed" &&
                            record.optString("kind") !in setOf("cancellation", "void")
                    ) {
                        if (correction) {
                            SettlementText(
                                "Grund der Korrektur",
                                reason,
                                !busy && original == null,
                                true,
                            ) {
                                reason = it
                            }
                            SettlementText(
                                "Vorhandene Übermittlung / Zugang / Berichtigung",
                                evidence,
                                !busy && original == null,
                                true,
                            ) {
                                evidence = it
                            }

                            NativeTextButton(
                                {
                                    act(
                                        "correct_settlement",
                                        JSONObject()
                                            .put("settlement_id", id)
                                            .put(
                                                "expected_record_hash",
                                                current.getString("record_hash"),
                                            )
                                            .put("reason", reason)
                                            .put("access_evidence", evidence)
                                            .put("confirmed", true),
                                    ) { result ->
                                        controller.navigate(
                                            "settlement_review",
                                            result.getString("settlement_id"),
                                        )
                                    }
                                },
                                enabled = !busy && original == null && reason.isNotBlank(),
                            ) {
                                Text(
                                    if (invoice) "Storno bewusst vorbereiten"
                                    else "Aufhebung bewusst bestätigen"
                                )
                            }
                        }
                    }
                }
                NativeDetailDelete(enabled = status == "draft" && original == null && !busy) {
                    scope.launch {
                        busy = true
                        try {
                            controller.queueSwipe("settlement", id, false)
                            controller.back()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error =
                                nativeActionError(
                                    failure,
                                    "Der Entwurf konnte nicht gelöscht werden.",
                                )
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        val current = data
        val record = current?.optJSONObject("record")
        if (original != null) {
            NativeActionBar("Schließen", { controller.back() }, enabled = !busy)
        } else if (record?.optString("status") == "prepared") {
            NativeActionBar(
                if (record.optString("route") == AccountingRules.BILLED) "Ausstellen fortsetzen"
                else "Bestätigen fortsetzen",
                {
                    act("complete_settlement", JSONObject().put("settlement_id", id)) {
                        shareRequested = true
                        attempt++
                    }
                },
                enabled = !busy && current?.optBoolean("requires_restore_review") != true,
            )
        } else if (current != null && record != null && payment) {
            NativeActionBar(
                if (reversal.isEmpty()) "Zahlung speichern" else "Gegenbuchung speichern",
                {
                    act(
                        "add_settlement_payment",
                        JSONObject()
                            .put("settlement_id", id)
                            .put("expected_record_hash", current.getString("record_hash"))
                            .put("amount", amount)
                            .put("date", day)
                            .put("note", note)
                            .apply { if (reversal.isNotEmpty()) put("reversal_of", reversal) },
                    ) {
                        payment = false
                        amount = ""
                        note = ""
                        reversal = ""
                        attempt++
                    }
                },
                "Abbrechen",
                { payment = false },
                enabled = !busy && amount.isNotBlank(),
                secondaryEnabled = !busy,
            )
        } else if (
            current != null &&
                record?.optString("status") == "confirmed" &&
                current.optLong("balance_minor") > 0 &&
                record.optString("kind") in setOf("invoice", "internal")
        ) {
            NativeActionBar(
                "Bezahlt · " + settlementMoney(current.getLong("balance_minor")),
                {
                    scope.launch {
                        busy = true
                        try {
                            settlementPaid(controller, id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Zahlung nicht vorgemerkt."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled =
                    !busy &&
                        controller.listActions.none {
                            ("settlement" to id) in ListActionTools.targets(it) &&
                                it.optString("state") in setOf("queued", "applying", "uncertain")
                        },
            )
        } else if (record != null) {
            NativeActionBar(
                if (
                    record.optString("route") == AccountingRules.BILLED &&
                        record.optString("status") in setOf("confirmed", "cancelled")
                )
                    "PDF ansehen"
                else "Zur Abrechnung",
                {
                    if (
                        record.optString("route") == AccountingRules.BILLED &&
                            record.optString("status") in setOf("confirmed", "cancelled")
                    )
                        document(false)
                    else controller.navigate("accounting")
                },
                enabled = !busy,
            )
        }
    }
}
