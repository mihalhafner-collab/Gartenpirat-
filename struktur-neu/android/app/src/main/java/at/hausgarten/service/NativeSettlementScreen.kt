package at.hausgarten.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private fun settlementObjects(values: JSONArray?): List<JSONObject> = if (values == null) emptyList() else
    (0 until values.length()).mapNotNull { values.optJSONObject(it) }
private fun settlementDay() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
private fun settlementMoney(value: Long) = financeMoney(OfferMoney.decimal(value))
private fun settlementDraftInput(value: JSONObject): JSONObject {
    val schema = SettlementTools.schema("save_settlement_draft").getJSONObject("properties").getJSONObject("draft").getJSONObject("properties")
    val result = JSONObject()
    for (field in schema.keys()) if (field != "positions" && value.has(field)) result.put(field, value.get(field))
    val lineFields = schema.getJSONObject("positions").getJSONObject("items").getJSONObject("properties")
    result.put("positions", JSONArray(settlementObjects(value.optJSONArray("positions")).map { line ->
        JSONObject().apply { for (field in lineFields.keys()) if (line.has(field)) put(field, line.get(field)) }
    }))
    return result
}
private fun settlementStatus(value: JSONObject) = when (value.optString("status")) {
    "draft" -> "Entwurf"; "prepared" -> "Abschluss vorbereitet – fortsetzen"; "confirmed" -> "Bestätigt"; "cancelled" -> "Korrigiert / aufgehoben"; "abandoned" -> "Vorbereitung aufgehoben – nicht ausgestellt"; else -> "Status prüfen"
}

/** Remaining amount of a confirmed invoice or internal closure, or 0 when nothing is open. */
private fun settlementOpen(record: JSONObject): Long =
    if (record.optString("status") == "confirmed" && record.optString("kind") in setOf("invoice", "internal"))
        (record.optLong("total_minor") - record.optLong("paid_minor")).coerceAtLeast(0) else 0L

/** One tap: records the current remainder with today's date through the review screen's durable operation. */
private suspend fun settlementPaid(controller: NativeAppController, id: String): Long {
    if (controller.draft("settlement-review:$id")?.optJSONObject("pending") != null) throw IllegalStateException("Ein Vorgang dieses Abschlusses ist noch offen. Bitte im Abschluss prüfen.")
    val current = controller.query("get_settlement", JSONObject().put("settlement_id", id))
    val open = current.optLong("balance_minor")
    require(open > 0) { "Kein offener Betrag." }
    val operation = JSONObject().put("tool", "add_settlement_payment").put("operation_id", UUID.randomUUID().toString()).put("args", JSONObject()
        .put("settlement_id", id).put("expected_record_hash", current.getString("record_hash"))
        .put("amount", OfferMoney.decimal(open)).put("date", settlementDay()).put("note", "Vollständig bezahlt"))
    controller.saveDraft("settlement-review:$id", JSONObject().put("pending", operation)); controller.flushDrafts()
    try { controller.settlementAction("review:$id:${operation.getString("operation_id")}", "add_settlement_payment", operation.getJSONObject("args")) }
    catch (failure: Exception) {
        if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") controller.removeDraft("settlement-review:$id")
        throw failure
    }
    controller.removeDraft("settlement-review:$id")
    return open
}

@Composable
internal fun NativeSettlements(controller: NativeAppController, selectedId: String) {
    if (selectedId.isNotEmpty()) { NativeSettlementReview(controller, selectedId); return }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(attempt) {
        try { result = controller.query("list_settlements"); error = "" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Abschlüsse nicht lesbar." }
    }
    val records = settlementObjects(result?.optJSONArray("records")).filter {
        listOf(it.optString("title"), it.optString("invoice_number"), it.optString("issue_date")).any { text -> text.contains(search, true) }
    }
    NativeSearchList("Abschluss oder Rechnungsnummer", search, { search = it }, "${records.size} Einträge", bottomSearch = true) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                NativeActions {
                    Button({ controller.navigate("settlement_edit") }) { Text("Neue Abrechnung") }
                }
            }
            if (error.isNotEmpty()) item { NativeStatus(error, NativeStatusTone.Failed); TextButton({ attempt++ }) { Text("Erneut laden") } }
            if (result == null && error.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(records, key = { it.getString("id") }) { record ->
                NativeInfoRow(record.optString("title").ifBlank { "Abschluss" }, listOf(
                    AccountingRules.label(record.optString("route")), settlementStatus(record), record.optString("invoice_number"),
                    if (record.has("total_minor")) settlementMoney(record.optLong("total_minor")) else "Betrag ausstehend"
                ).filter { it.isNotBlank() }.joinToString(" · "), { controller.navigate("settlement_review", record.getString("id")) })
                val open = settlementOpen(record)
                if (open > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeStatus("Offen " + settlementMoney(open), NativeStatusTone.Attention)
                    Button({
                        val id = record.getString("id")
                        paying = id
                        scope.launch {
                            try { controller.message("Bezahlt erfasst: " + settlementMoney(settlementPaid(controller, id))) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { controller.message(failure.message ?: "Zahlung nicht bestätigt. Bitte im Abschluss prüfen.") }
                            finally { paying = ""; attempt++ }
                        }
                    }, enabled = paying.isEmpty()) { Text(if (paying == record.getString("id")) "Wird erfasst …" else "Bezahlt") }
                } else if (record.optString("status") == "confirmed" && record.optLong("total_minor") > 0) NativeStatus("Bezahlt", NativeStatusTone.Confirmed)
            }
            if (result != null && records.isEmpty()) item { Text("Keine Abschlüsse für diese Suche.") }
        }
    }
}

/** Native working copy is durable, including exact pending save arguments. */
@Composable
internal fun NativeSettlementEditor(controller: NativeAppController, id: String) {
    val recipient = if (id.isEmpty()) controller.route.args.optString("billing_customer_id") else ""
    val key = "settlement-editor:$id" + if (recipient.isNotEmpty()) ":$recipient" else ""
    var raw by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var savedHash by rememberSaveable(id) { mutableStateOf("") }
    var pending by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var operationId by rememberSaveable(id) { mutableStateOf(UUID.randomUUID().toString()) }
    var context by remember { mutableStateOf<JSONObject?>(null) }
    var priorRecords by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        try {
            context = controller.query("get_settlement_context")
            priorRecords = settlementObjects(controller.query("list_settlements").optJSONArray("records"))
            val local = controller.draft(key)
            if (local != null) {
                raw = local.optJSONObject("values")?.toString()
                savedHash = local.optString("record_hash")
                pending = local.optJSONObject("pending_save")?.toString()
                operationId = local.optString("operation_id", operationId)
            } else {
                // A completed editor may still have saveable route state. Only a
                // durable working copy keeps pending arguments across visits.
                raw = null; savedHash = ""; pending = null
                operationId = UUID.randomUUID().toString()
            }
            if (raw == null && id.isNotEmpty()) {
                val data = controller.query("get_settlement", JSONObject().put("settlement_id", id))
                val row = data.getJSONObject("record")
                require(row.optString("status") == "draft") { "Dieser Abschluss ist bereits eingefroren. Bitte die Vorschau öffnen." }
                raw = row.toString(); savedHash = data.getString("record_hash")
            }
            if (raw == null) raw = SettlementRules.prefill(controller.route.args, context!!.optJSONArray("worklogs"), priorRecords).toString()
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Entwurf nicht lesbar." }
    }
    fun persist(value: JSONObject) {
        raw = value.toString()
        controller.saveDraft(key, JSONObject().put("values", value).put("record_hash", savedHash).put("operation_id", operationId).apply { pending?.let { put("pending_save", JSONObject(it)) } })
    }
    fun update(field: String, value: String) { persist(JSONObject(raw!!).put(field, value)) }
    fun save() {
        scope.launch {
            busy = true; error = ""
            try {
                val args = pending?.let(::JSONObject) ?: JSONObject().put("draft", settlementDraftInput(JSONObject(raw!!))).apply {
                    if (id.isNotEmpty()) { put("settlement_id", id); put("expected_record_hash", savedHash) }
                }
                pending = args.toString(); persist(JSONObject(raw!!)); controller.flushDrafts()
                val result = controller.settlementAction("save:$id:$operationId", "save_settlement_draft", args)
                controller.removeDraft(key)
                raw = null; savedHash = ""; pending = null
                operationId = UUID.randomUUID().toString(); ready = false
                controller.navigate("settlement_review", result.getString("settlement_id"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") {
                    pending = null; persist(JSONObject(raw!!)); controller.flushDrafts()
                }
                error = failure.message ?: "Entwurf nicht gespeichert. Ursprünglichen Vorgang erneut prüfen."
            }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error.isNotEmpty()) NativeStatus(error, NativeStatusTone.Failed)
            if (!ready) { if (error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                val draft = JSONObject(raw!!)
                val editable = !busy && pending == null
                Text("Leistungen, Mengen und Preise bewusst prüfen. Ein Angebot belegt keine ausgeführte Leistung. Unvollständige Entwürfe dürfen gespeichert werden.", style = MaterialTheme.typography.bodySmall)
                if (pending != null) NativeStatus("Speichervorgang noch zu prüfen. Die ursprünglichen Angaben bleiben erhalten.", NativeStatusTone.Attention)
                if (editable) NativeChoice("Abrechnungsart", draft.optString("route"), listOf(AccountingRules.BILLED to "Rechnung", AccountingRules.LABOR to "Labor clandestino")) { update("route", it) }
                else Text(AccountingRules.label(draft.optString("route")))
                val replacements = priorRecords.filter { it.optString("status") == "cancelled" && it.optString("route") == "billed" && it.optString("kind") == "invoice" }
                if (editable && replacements.isNotEmpty() && draft.optString("route") == "billed") NativeChoice("Ersatzrechnung zu (optional)", draft.optString("replacement_for"), listOf("" to "Kein Ersatz") + replacements.map {
                    it.getString("id") to it.optString("invoice_number").ifBlank { it.optString("title") }
                }) { update("replacement_for", it) }
                SettlementText("Bezeichnung", draft.optString("title"), editable) { update("title", it) }
                if (editable) {
                    NativeRecordPicker(controller, "customers", "Einsatzkunde (optional)", draft.optString("customer_id"), onValueChange = { update("customer_id", it) })
                    NativeRecordPicker(controller, "customers", "Rechtlicher Rechnungsempfänger", draft.optString("billing_customer_id"), onValueChange = { update("billing_customer_id", it) })
                    NativeRecordPicker(controller, "properties", "Einsatzobjekt (optional)", draft.optString("property_id"), onValueChange = { update("property_id", it) })
                }
                NativeDateField("Leistungsbeginn", draft.optString("service_from"), { update("service_from", it) }, enabled = editable)
                NativeDateField("Leistungsende", draft.optString("service_to"), { update("service_to", it) }, enabled = editable)
                Text("Positionen", style = MaterialTheme.typography.titleLarge)
                val positions = draft.optJSONArray("positions") ?: JSONArray()
                for (index in 0 until positions.length()) {
                    val position = positions.getJSONObject(index)
                    fun change(field: String, value: String) {
                        val next = JSONObject(raw!!)
                        next.getJSONArray("positions").getJSONObject(index).put(field, value)
                        persist(next)
                    }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Position ${index + 1}", style = MaterialTheme.typography.titleMedium)
                            if (editable) NativeChoice("Leistungsquelle", position.optString("source_collection"), listOf("" to "Manuelle Leistung", "worklogs" to "Tatsächliche Arbeit", "orders" to "Auftrag ohne Arbeitsaufzeichnung")) {
                                change("source_collection", it); change("source_id", "")
                            }
                            val collection = position.optString("source_collection")
                            if (editable && collection.isNotEmpty()) NativeRecordPicker(controller, collection, if (collection == "worklogs") "Arbeit auswählen" else "Auftrag auswählen", position.optString("source_id"), onValueChange = { selected ->
                                change("source_id", selected)
                            })
                            if (collection == "orders") Text("Nur tatsächlich ausgeführte Leistungen erfassen. Zugehörige Arbeiten nicht zusätzlich abrechnen.", style = MaterialTheme.typography.bodySmall)
                            val source = settlementObjects(context?.optJSONArray(collection)).firstOrNull { it.optString("id") == position.optString("source_id") }
                            if (editable && source != null) TextButton({
                                change("title", source.optString(if (collection == "worklogs") "activity" else "title"))
                                if (collection == "worklogs" && source.has("hours") && !source.isNull("hours")) { change("quantity", source.get("hours").toString()); change("unit", "Stunden") }
                            }) { Text(if (collection == "worklogs") "Tätigkeit und Ist-Stunden übernehmen" else "Auftragsbezeichnung übernehmen") }
                            SettlementText("Leistung", position.optString("title"), editable) { change("title", it) }
                            SettlementText("Beschreibung / Material / Fahrt / Entsorgung", position.optString("description"), editable, multiline = true) { change("description", it) }
                            SettlementText("Tatsächlich geleistete Menge", position.optString("quantity"), editable) { change("quantity", it) }
                            SettlementText("Einheit", position.optString("unit"), editable) { change("unit", it) }
                            SettlementText("Preis je Einheit (€)", position.optString("price"), editable) { change("price", it) }
                            if (editable) TextButton({
                                val next = JSONObject(raw!!)
                                val entries = next.getJSONArray("positions")
                                next.put("positions", JSONArray((0 until entries.length()).filter { it != index }.map { entries.getJSONObject(it) }))
                                persist(next)
                            }) { Text("Position entfernen") }
                        }
                    }
                }
                if (editable) TextButton({
                    val next = JSONObject(raw!!)
                    next.getJSONArray("positions").put(JSONObject().put("source_collection", "").put("source_id", ""))
                    persist(next)
                }) { Text("Position hinzufügen") }
                SettlementText("Zahlungsbedingungen", draft.optString("payment_terms"), editable, multiline = true) { update("payment_terms", it) }
                SettlementText("Zahlungsangaben / Bankverbindung", draft.optString("payment_details"), editable, multiline = true) { update("payment_details", it) }
                Text("Gewöhnliche österreichische Inlandsabrechnung in Euro mit Kleinunternehmerbefreiung. Ausland, Reverse Charge, anderer Steuerstatus und strukturierte Bundesrechnungen werden hier nicht unterstützt.", style = MaterialTheme.typography.bodySmall)
            }
        }
        NativeActionBar(if (pending == null) "Entwurf speichern und prüfen" else "Speichervorgang erneut prüfen", ::save, enabled = ready && !busy)
    }
}

@Composable
private fun SettlementText(label: String, value: String, enabled: Boolean = true, multiline: Boolean = false, change: (String) -> Unit) {
    NativeField(label, value, change, multiline = multiline, enabled = enabled)
}

@Composable
private fun SettlementAddress(title: String, row: JSONObject?, issuer: Boolean = false) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    val fields = if (issuer) listOf("business_name", "owner_name", "business_street", "business_postal_code", "business_city", "business_legal_form", "business_legal_info", "business_register_number", "business_register_court")
        else listOf("name", "company", "street", "postal_code", "city")
    val text = fields.mapNotNull { row?.optString(it)?.takeIf(String::isNotBlank) }.joinToString("\n")
    Text(text.ifBlank { "Angaben fehlen" })
}

/** Review reads current dependencies; closing uses exactly that review hash. */
@Composable
internal fun NativeSettlementReview(controller: NativeAppController, id: String) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var reviewed by rememberSaveable(id) { mutableStateOf(false) }
    var reviewedHash by rememberSaveable(id) { mutableStateOf("") }
    var domestic by rememberSaveable(id) { mutableStateOf(false) }
    var issuerConfirmed by rememberSaveable(id) { mutableStateOf(false) }
    var electronic by rememberSaveable(id) { mutableStateOf(false) }
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
    LaunchedEffect(id, attempt) {
        try {
            val loaded = controller.query("get_settlement", JSONObject().put("settlement_id", id))
            if (reviewedHash != loaded.optString("review_hash")) {
                reviewed = false; domestic = false; issuerConfirmed = false; electronic = false
                reviewedHash = loaded.optString("review_hash")
            }
            data = loaded
            context = controller.query("get_settlement_context")
            val operation = controller.draft("settlement-review:$id")
            original = operation?.optJSONObject("pending")?.toString()
            error = ""
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Abschluss nicht lesbar." }
    }
    fun act(tool: String, args: JSONObject, after: (JSONObject) -> Unit = { attempt++ }) {
        scope.launch {
            busy = true; error = ""
            try {
                val operation = original?.let(::JSONObject) ?: JSONObject().put("tool", tool).put("args", args).put("operation_id", UUID.randomUUID().toString())
                original = operation.toString()
                controller.saveDraft("settlement-review:$id", JSONObject().put("pending", operation)); controller.flushDrafts()
                val result = controller.settlementAction("review:$id:${operation.getString("operation_id")}", operation.getString("tool"), operation.getJSONObject("args"))
                controller.removeDraft("settlement-review:$id"); original = null
                after(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") {
                    original = null; controller.removeDraft("settlement-review:$id")
                }
                error = failure.message ?: "Vorgang noch nicht bestätigt. Mit denselben Angaben erneut prüfen."
            }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error.isNotEmpty()) { NativeStatus(error, NativeStatusTone.Failed); TextButton({ attempt++ }) { Text("Aktuellen Stand laden") } }
            val current = data
            if (current == null) { if (error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                val record = current.getJSONObject("record")
                val status = record.optString("status")
                val invoice = record.optString("route") == AccountingRules.BILLED
                Text(record.optString("title"), style = MaterialTheme.typography.headlineSmall)
                NativeStatus(settlementStatus(record))
                Text(if (invoice) "Rechnung" else "Labor clandestino · interner Nachweis")
                if (record.optString("invoice_number").isNotBlank()) Text("Rechnungsnummer: ${record.getString("invoice_number")}")
                if (record.optString("original_number").isNotBlank()) Text("Storno zu ${record.getString("original_number")}")
                Text("${if (invoice) "Rechnungsdatum" else "Datum"}: ${record.optString("issue_date").ifBlank { settlementDay() }}")
                Text("Leistung: ${record.optString("service_from")} – ${record.optString("service_to")}")
                SettlementAddress("Aussteller", record.optJSONObject("issuer") ?: current.optJSONObject("issuer") ?: context?.optJSONObject("business_settings"), true)
                if (status == "draft") TextButton({ scope.launch {
                    try {
                        val business = controller.query("get_business_settings")
                        controller.action("Unternehmensdaten bearbeiten", "update_business_settings", JSONObject().put("expected_record_hash", business.getString("record_hash")).put("updates", business.getJSONObject("settings")))
                    } catch (failure: Exception) { error = failure.message ?: "Unternehmensdaten nicht lesbar." }
                } }, enabled = !busy && original == null) { Text("Ausstellerangaben bearbeiten") }
                SettlementAddress("Rechtlicher Empfänger", record.optJSONObject("recipient") ?: current.optJSONObject("recipient"))
                for ((index, line) in settlementObjects(record.optJSONArray("positions")).withIndex()) {
                    HorizontalDivider()
                    Text("${index + 1}. ${line.optString("title")}", style = MaterialTheme.typography.titleMedium)
                    if (line.optString("description").isNotBlank()) Text(line.getString("description"))
                    val sourceCollection = line.optString("source_collection")
                    if (sourceCollection.isEmpty()) Text("Bewusst angelegte manuelle Leistung", style = MaterialTheme.typography.bodySmall)
                    else {
                        val source = settlementObjects(record.optJSONArray("source_snapshots")).firstOrNull {
                            it.optString("collection") == sourceCollection && it.optString("id") == line.optString("source_id")
                        }?.optJSONObject("record")
                        TextButton({ controller.navigate(sourceCollection, line.getString("source_id")) }) {
                            Text("Quelle: " + (source?.optString(if (sourceCollection == "worklogs") "activity" else "title")?.takeIf(String::isNotBlank) ?: if (sourceCollection == "worklogs") "Arbeitsaufzeichnung" else "Auftrag"))
                        }
                    }
                    Text("${line.optString("quantity")} ${line.optString("unit")} × ${line.optString("price")} €")
                    if (line.has("amount_minor") && !line.isNull("amount_minor")) Text(settlementMoney(line.getLong("amount_minor"))) else Text("Betrag ausstehend")
                }
                HorizontalDivider()
                Text(if (record.has("total_minor")) "Gesamt: ${settlementMoney(record.getLong("total_minor"))}" else "Gesamtbetrag ausstehend", style = MaterialTheme.typography.headlineSmall)
                Text("Umsatzsteuerbefreit gemäß § 6 Abs. 1 Z 27 UStG")
                Text(record.optString("payment_terms")); Text(record.optString("payment_details"))
                val sources = record.optJSONArray("sources") ?: JSONArray()
                val restored = settlementObjects(context?.optJSONArray("sources_requiring_review") ?: context?.optJSONArray("restored_sources"))
                val reviewSources = linkedMapOf<String, JSONObject>()
                for (source in settlementObjects(sources)) {
                    reviewSources[source.getString("collection") + ":" + source.getString("id")] = source
                    if (source.optString("collection") == "orders") {
                        val snapshot = settlementObjects(record.optJSONArray("source_snapshots")).firstOrNull {
                            it.optString("collection") == "orders" && it.optString("id") == source.optString("id")
                        }
                        val included = snapshot?.optJSONArray("included_work_ids")
                        val workIds = if (included != null) (0 until included.length()).map { included.getString(it) }
                            else settlementObjects(context?.optJSONArray("worklogs")).filter { it.optString("order_id") == source.optString("id") }.map { it.getString("id") }
                        for (workId in workIds) reviewSources["worklogs:$workId"] = JSONObject().put("collection", "worklogs").put("id", workId)
                    }
                }
                val affected = reviewSources.values.filter { source -> restored.any { it.optString("collection") == source.optString("collection") && it.optString("id") == source.optString("id") } }
                if (status in setOf("draft", "prepared") && (affected.isNotEmpty() || current.optBoolean("requires_restore_review"))) {
                    NativeStatus("Frühere oder wiederhergestellte Zuordnung: aktuellen Belegstand einschließlich Originalen, Nummern, Leistungsbindungen und Zahlungen prüfen. Nur fortsetzen, wenn diese Leistungen nicht bereits anderweitig abgerechnet wurden. Eine neue Serie stellt fehlende Vorgänge nicht wieder her.", NativeStatusTone.Attention)
                    SettlementText("Aktuellen Belegstand geprüft: Nachweis und Ergebnis", restoreEvidence, !busy && original == null, true) { restoreEvidence = it }
                    TextButton({ act("review_restored_sources", JSONObject().put("sources", JSONArray(affected)).put("settlement_id", id).put("evidence", restoreEvidence).put("confirmed", true)) }, enabled = restoreEvidence.isNotBlank() && !busy && original == null) { Text("Aktuellen Belegabgleich bestätigen") }
                }
                if (status == "draft") {
                    val validation = current.optJSONObject("validation")
                    val errors = validation?.optJSONArray("errors") ?: JSONArray()
                    for (index in 0 until errors.length()) NativeStatus(errors.get(index).toString(), NativeStatusTone.Attention)
                    TextButton({ controller.navigate("settlement_edit", id) }, enabled = !busy && original == null) { Text("Entwurf bearbeiten") }
                    SettlementCheck("Leistungen, Mengen, Preise und Empfänger in dieser Vorschau geprüft", reviewed, !busy && original == null) { reviewed = it }
                    SettlementCheck("Gewöhnliche österreichische Inlandsabrechnung in Euro", domestic, !busy && original == null) { domestic = it }
                    SettlementCheck("Ausstellerangaben einschließlich Rechtsform und Kleinunternehmerbefreiung geprüft", issuerConfirmed, !busy && original == null) { issuerConfirmed = it }
                    if (invoice) SettlementCheck("Elektronische Übermittlung ist mit dem Empfänger vereinbart", electronic, !busy && original == null) { electronic = it }
                    Text("Beim bewussten Abschluss werden Inhalt und Quellen eingefroren. Die Rechnung erhält eine lokale, von sevDesk getrennte Nummer. Interne Labor-Abrechnungen erhalten keine Rechnungsnummer und kein Rechnungs-PDF.", style = MaterialTheme.typography.bodySmall)
                }
                if (status == "prepared") {
                    SettlementText("Grund für das Aufheben der Vorbereitung", abandonmentReason, !busy && original == null, true) { abandonmentReason = it }
                    Text(if (invoice) "Die reservierte Rechnungsnummer bleibt dauerhaft belegt. Originalvorgänge und Zahlungen bleiben erhalten."
                        else "Die Vorbereitung und ihre Historie bleiben nachvollziehbar. Andere Abrechnungen und Zahlungen bleiben erhalten.", style = MaterialTheme.typography.bodySmall)
                    TextButton({
                        act("abandon_prepared_settlement", JSONObject().put("settlement_id", id).put("expected_record_hash", current.getString("record_hash"))
                            .put("reason", abandonmentReason).put("confirmed", true))
                    }, enabled = !busy && original == null && abandonmentReason.isNotBlank() && !current.optBoolean("requires_restore_review")) { Text("Vorbereitung aufheben") }
                }
                if (status in setOf("confirmed", "cancelled")) {
                    if (invoice) NativeActions {
                        TextButton({ scope.launch { try { controller.settlementDocument(id, false) } catch (failure: Exception) { error = failure.message ?: "PDF konnte nicht geöffnet werden." } } }) { Text("Original-PDF öffnen") }
                        TextButton({ scope.launch { try { controller.settlementDocument(id, true) } catch (failure: Exception) { error = failure.message ?: "PDF konnte nicht geteilt werden." } } }, enabled = record.optBoolean("electronic_delivery_accepted")) { Text("Original-PDF teilen") }
                    }
                    if (invoice && !record.optBoolean("electronic_delivery_accepted")) Text("Elektronische Übermittlung wurde beim Abschluss nicht als vereinbart bestätigt. Das Original kann lokal geöffnet werden; Teilen bleibt gesperrt.", style = MaterialTheme.typography.bodySmall)
                    Text("Teilen bestätigt weder Versand noch Zugang. Original und Historie bleiben erhalten. Zahlungen sind davon getrennt.", style = MaterialTheme.typography.bodySmall)
                    Text("Originale und wesentliche Historie aufbewahren: grundsätzlich sieben Jahre ab Jahresende, gegebenenfalls länger. Die App löscht Belege nicht nach Alter.", style = MaterialTheme.typography.bodySmall)
                    if (invoice) {
                        for (proof in settlementObjects(current.optJSONArray("delivery_evidence"))) Text("${proof.optString("date")} · ${proof.optString("evidence")}")
                        TextButton({ delivery = !delivery }, enabled = !busy && original == null) { Text(if (delivery) "Zugangserfassung schließen" else "Tatsächlichen Zugang dokumentieren") }
                        if (delivery) {
                            NativeDateField("Nachweisdatum", deliveryDay, { deliveryDay = it }, required = true, enabled = !busy && original == null)
                            SettlementText("Zugangsnachweis / Berichtigung beim Empfänger", deliveryEvidence, !busy && original == null, true) { deliveryEvidence = it }
                            TextButton({ act("record_settlement_delivery", JSONObject().put("settlement_id", id).put("expected_record_hash", current.getString("record_hash"))
                                .put("date", deliveryDay).put("evidence", deliveryEvidence)) { delivery = false; deliveryEvidence = ""; attempt++ }
                            }, enabled = !busy && original == null && deliveryEvidence.isNotBlank()) { Text("Zugangsnachweis speichern") }
                        }
                    }
                    if (record.optString("kind") in setOf("invoice", "internal")) {
                      Text("Erfasst bezahlt: ${settlementMoney(current.optLong("paid_minor"))} · Rest: ${settlementMoney(current.optLong("balance_minor"))}")
                      val payments = settlementObjects(current.optJSONArray("payments") ?: record.optJSONArray("payments"))
                      for (entry in payments) {
                        Text("${entry.optString("date")} · ${settlementMoney(entry.optLong("amount_minor"))} · ${entry.optString("note")}")
                        if (entry.optLong("amount_minor") > 0L) TextButton({
                            payment = true; reversal = entry.getString("id"); amount = OfferMoney.decimal(-entry.getLong("amount_minor")); note = "Gegenbuchung"
                        }, enabled = !busy && original == null) { Text("Diese Zahlung gegenbuchen") }
                      }
                      val open = if (status == "confirmed") current.optLong("balance_minor") else 0L
                      if (open > 0 && !payment) Button({
                          act("add_settlement_payment", JSONObject().put("settlement_id", id).put("expected_record_hash", current.getString("record_hash"))
                              .put("amount", OfferMoney.decimal(open)).put("date", settlementDay()).put("note", "Vollständig bezahlt"))
                      }, enabled = !busy && original == null) { Text("Bezahlt · " + settlementMoney(open)) }
                      TextButton({ payment = !payment }, enabled = !busy && original == null) { Text(if (payment) "Zahlungsformular schließen" else "Teilzahlung oder anderes Datum") }
                      if (payment) {
                        if (reversal.isNotEmpty()) NativeStatus("Gegenbuchung zur gewählten Zahlung", NativeStatusTone.Attention)
                        SettlementText("Betrag (€)", amount, !busy && original == null) { amount = it }
                        NativeDateField("Zahlungsdatum", day, { day = it }, required = true, enabled = !busy && original == null)
                        SettlementText("Zahlungsnotiz", note, !busy && original == null, true) { note = it }
                        Text("Manuelle Zahlungserfassung ersetzt keine Registrierkasse. Eine Stornierung behauptet keine Rückzahlung.", style = MaterialTheme.typography.bodySmall)
                        TextButton({
                            act("add_settlement_payment", JSONObject().put("settlement_id", id).put("expected_record_hash", current.getString("record_hash"))
                                .put("amount", amount).put("date", day).put("note", note).apply { if (reversal.isNotEmpty()) put("reversal_of", reversal) }) {
                                payment = false; amount = ""; note = ""; reversal = ""; attempt++
                            }
                        }, enabled = !busy && original == null && amount.isNotBlank()) { Text(if (reversal.isEmpty()) "Zahlung speichern" else "Gegenbuchung speichern") }
                      }
                    } else Text("Zahlungen und Gegenbuchungen bleiben beim Originalvorgang.", style = MaterialTheme.typography.bodySmall)
                    if (status == "confirmed" && record.optString("kind") !in setOf("cancellation", "void")) {
                        TextButton({ correction = !correction }, enabled = !busy && original == null) { Text(if (invoice) "Vollständige Stornorechnung vorbereiten" else "Interne Abrechnung aufheben") }
                        if (correction) {
                            SettlementText("Grund der Korrektur", reason, !busy && original == null, true) { reason = it }
                            SettlementText("Vorhandene Übermittlung / Zugang / Berichtigung", evidence, !busy && original == null, true) { evidence = it }
                            Text("Nur dieser Abschluss und seine aktiven Bindungen werden neutralisiert. Zahlungen werden nicht gelöscht. Ausgestellte Originale bleiben unverändert. Ersatzleistungen erst nach bewusstem Artenwechsel neu abrechnen.", style = MaterialTheme.typography.bodySmall)
                            TextButton({
                                act("correct_settlement", JSONObject().put("settlement_id", id).put("expected_record_hash", current.getString("record_hash"))
                                    .put("reason", reason).put("access_evidence", evidence).put("confirmed", true)) { result ->
                                    controller.navigate("settlement_review", result.getString("settlement_id"))
                                }
                            }, enabled = !busy && original == null && reason.isNotBlank()) { Text(if (invoice) "Storno bewusst vorbereiten" else "Aufhebung bewusst bestätigen") }
                        }
                    }
                }
            }
        }
        val current = data
        val record = current?.optJSONObject("record")
        if (original != null) {
            NativeActionBar("Ursprünglichen Vorgang erneut prüfen", {
                val operation = JSONObject(original!!)
                act(operation.getString("tool"), operation.getJSONObject("args")) { result ->
                    val target = result.optString("settlement_id", id)
                    if (target != id) controller.navigate("settlement_review", target) else attempt++
                }
            }, enabled = !busy)
        } else if (current != null && record?.optString("status") == "draft") {
            NativeActionBar(if (record.optString("route") == AccountingRules.BILLED) "Rechnung verbindlich vorbereiten" else "Labor-Abrechnung bestätigen", {
                act("prepare_settlement", JSONObject().put("settlement_id", id).put("expected_review_hash", current.getString("review_hash"))
                    .put("confirmed", true).put("domestic_eur_confirmed", domestic).put("issuer_details_confirmed", issuerConfirmed).put("electronic_delivery_accepted", electronic))
            }, enabled = !busy && reviewed && domestic && issuerConfirmed && current.optJSONObject("validation")?.optBoolean("valid") == true)
        } else if (record?.optString("status") == "prepared") {
            NativeActionBar(if (record.optString("route") == AccountingRules.BILLED) "Original-PDF prüfen und ausstellen" else "Internen Abschluss fertigstellen", {
                act("complete_settlement", JSONObject().put("settlement_id", id))
            }, enabled = !busy && current?.optBoolean("requires_restore_review") != true)
        }
    }
}

@Composable
private fun SettlementCheck(label: String, value: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value, enabled = enabled, role = Role.Checkbox, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(vertical = 12.dp))
    }
}
