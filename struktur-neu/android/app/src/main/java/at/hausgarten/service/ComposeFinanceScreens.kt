package at.hausgarten.service

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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import org.json.JSONArray
import org.json.JSONObject

private fun financeToday() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
private fun financeCopy(value: JSONObject) = JSONObject(value.toString())
private fun financeData(envelope: JSONObject): JSONObject = when (val data = envelope.opt("data")) {
    is JSONObject -> data
    is JSONArray -> JSONObject().put("items", data).also { if (envelope.has("total")) it.put("total", envelope.get("total")) }
    else -> envelope
}
private fun financeRows(data: JSONObject, vararg names: String): List<JSONObject> {
    val array = (names.toList() + "items").firstNotNullOfOrNull { data.optJSONArray(it) } ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { it.optJSONObject("offer") ?: it.optJSONObject("entry") ?: it }
}
internal fun financeMoney(value: Any?): String = if (value == null || value == JSONObject.NULL) "offen" else runCatching { BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €" }.getOrDefault("Betrag prüfen")
private fun financeMinor(value: String): Long = BigDecimal(value.trim().replace(',', '.')).movePointRight(2).longValueExact()
private fun financeFields(row: JSONObject, keys: Array<String>) = JSONObject().also { out -> keys.forEach { if (row.has(it)) out.put(it, row.get(it)) } }
private fun financeStatus(value: String): String = mapOf("draft" to "Entwurf", "validated" to "Geprüft", "finalized" to "Finalisiert", "accepted" to "Angenommen", "declined" to "Abgelehnt", "share_prepared" to "Teilen vorbereitet", "sent" to "Versand bestätigt", "cash" to "Bar", "bank" to "Bank", "card" to "Karte", "other" to "Andere", "b2b" to "Unternehmen", "consumer" to "Verbraucher", "on_premise" to "Im Geschäft", "distance" to "Fernabsatz", "off_premise" to "Außerhalb der Geschäftsräume", "confirmed" to "Bestätigt", "catalog" to "Freigegebener Katalogpreis", "calculated" to "Berechnet", "estimated" to "Geschätzt", "unit" to "Einheitspreis", "text" to "Text", "own" to "Eigener Nummernkreis")[value] ?: nativeValue(value)
private fun financeInvoiceTitle(row: JSONObject): String = row.optString("invoice_number").takeUnless { it.isBlank() || it.startsWith("Entwurf-") } ?: "Rechnungsentwurf"
private val financePartyFields=listOf("name" to "Name","company" to "Firma","first_name" to "Vorname","last_name" to "Nachname","street" to "Straße","postal_code" to "Postleitzahl","city" to "Ort","country" to "Land","email" to "E-Mail","phone" to "Telefon")
private suspend fun financeInspect(controller: NativeAppController, tool: String, args: JSONObject): JSONObject? {
    val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", args))
    if (proof.optString("state") == "content_removed") throw IllegalStateException("Dieser Vorgang wurde entfernt. Der ursprüngliche Auftrag bleibt zur Prüfung erhalten.")
    return if (proof.optString("state") == "confirmed") proof.optJSONObject("result") ?: proof else null
}

@Composable
private fun FinanceValues(row: JSONObject?, fields: List<Pair<String, String>>) {
    row ?: return
    for ((name, label) in fields) if (row.has(name) && !row.isNull(name) && row.optString(name).isNotBlank()) {
        val value = row.opt(name)
        Text("$label: ${if (value is Boolean) { if (value) "Ja" else "Nein" } else if (name == "default_price") financeMoney(value) else if (name in listOf("default_quantity", "quantity", "hours")) nativeNumber(value) else nativeDisplayText(financeStatus(value?.toString().orEmpty()))}")
    }
}

@Composable
private fun FinanceIssuerSummary(issuer:JSONObject?) {
    Text("Aussteller und Belegkonfiguration",style=MaterialTheme.typography.titleMedium)
    if(issuer==null) Text("Aussteller fehlt",color=MaterialTheme.colorScheme.error)
    else FinanceValues(issuer,listOf("business_name" to "Betrieb","owner_name" to "Inhaber","business_street" to "Straße","business_postal_code" to "Postleitzahl","business_city" to "Ort","business_legal_info" to "Rechtliche Angaben","business_vat_id" to "UID","tax_mode" to "Steuermodus","tax_exemption_text" to "Steuerbefreiung","business_iban" to "IBAN","business_bank" to "Bank","business_bic" to "BIC","business_email" to "E-Mail","business_phone" to "Telefon","invoice_number_format" to "Nummernformat","invoice_prefix" to "Präfix","invoice_numbering_last_number" to "Bestätigter letzter Zähler","invoice_numbering_checked_at" to "Nummernprüfung am","invoice_issuer_confirmed" to "Aussteller bestätigt","invoice_numbering_confirmed" to "Nummernkreis bestätigt"))
}

@Composable
private fun FinanceInvoiceLabel(controller: NativeAppController, id: String, knownTitle: String = "") {
    if(knownTitle.isNotBlank()) {Text(if(knownTitle.startsWith("Entwurf-")) "Rechnungsentwurf" else knownTitle);return}
    var title by remember(id) { mutableStateOf("Rechnung") }
    LaunchedEffect(id) {
        title = try { controller.store.nativeData().detail("invoices", id)?.let(::financeInvoiceTitle) ?: "Rechnung nicht mehr vorhanden" }
        catch(cancelled:CancellationException) {throw cancelled} catch(_:Exception) {"Rechnung derzeit nicht lesbar"}
    }
    Text(title)
}

@Composable
private fun FinanceDraftSummary(controller: NativeAppController, row: JSONObject, section: String) {
    if(section=="invoices") Text(financeInvoiceTitle(row))
    FinanceValues(row,listOf("title" to "Bezeichnung","status" to "Status","document_type" to "Dokumentart","invoice_date" to "Rechnungsdatum","issue_date" to "Angebotsdatum","service_start" to "Leistungsbeginn","service_end" to "Leistungsende","valid_until" to "Gültig bis","execution_window" to "Ausführung","tax_mode" to "Steuermodus","payment_days" to "Zahlungsziel in Tagen","payment_terms" to "Zahlungsbedingungen","location" to "Einsatzort","recipient_type" to "Empfängertyp","recipient_vat_id" to "Empfänger-UID","domestic_service_confirmed" to "Inlandsleistung bestätigt","header_text" to "Kopftext","footer_text" to "Fußtext","notes" to "Notizen","binding_notice" to "Bindungshinweis","assumptions" to "Annahmen","exclusions" to "Ausschlüsse","description" to "Beschreibung","unit" to "Einheit","default_price" to "Standardpreis","default_quantity" to "Standardmenge","pricing_type" to "Positionsart","price_confirmed" to "Preis bestätigt","category" to "Kategorie","legal_notes" to "Rechtliche Hinweise","customer_type" to "Kundentyp","contract_channel" to "Vertragsweg","consumer_information" to "Verbraucherinformationen","consumer_information_confirmed" to "Verbraucherinformationen bestätigt","scope_confirmed" to "Leistungsumfang bestätigt","binding_confirmed" to "Bindungsart bestätigt","active" to "Aktiv"))
    for((field,collection,label) in listOf(Triple("customer_id","customers","Kunde"),Triple("billing_customer_id","customers","Rechnungsempfänger"),Triple("order_id","orders","Auftrag"))) {
        val id=row.optString(field)
        if(id.isNotEmpty()) {
            var title by remember(collection,id) {mutableStateOf("Wird gelesen …")}
            LaunchedEffect(collection,id) {title=try {controller.store.nativeData().detail(collection,id)?.let {it.optString("name",it.optString("title"))} ?: "Nicht mehr vorhanden"}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){"Derzeit nicht lesbar"}}
            Text("$label: $title")
        }
    }
    FinancePositionSummary(row.optJSONArray("positions"))
}

/** Offers, invoice originals, catalog and immutable payments use their existing owner commands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeFinanceScreen(section: String, id: String, controller: NativeAppController) {
    val listStates = rememberSaveableStateHolder()
    var editing by rememberSaveable(section, id, controller.route.mode, controller.route.args.optString("_draft_key")) { mutableStateOf(controller.route.mode in listOf("edit","new")) }
    var fresh by rememberSaveable(section, id, controller.route.mode) { mutableStateOf(controller.route.mode=="new") }
    var refresh by remember { mutableIntStateOf(0) }
    var detail by remember(section, id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(section, id) { mutableStateOf("") }
    var pendingAction by remember(section,id) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var issueReview by remember(section,id) { mutableStateOf<JSONObject?>(null) }
    var initialModeHandled by remember(section,id,controller.route.mode,controller.route.args.optString("_draft_key")) {mutableStateOf(false)}
    var offerReview by remember(section,id) { mutableStateOf<JSONObject?>(null) }
    var offerReviewed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(section, id, refresh) {
        if (id.isEmpty() || section == "payments") return@LaunchedEffect
        error = ""
        try {
            detail = when (section) {
                "invoices" -> controller.call("get_invoice", JSONObject().put("invoice_id", id))
                "offers" -> controller.query("get_offer", JSONObject().put("offer_id", id))
                else -> controller.query("get_offer_template", JSONObject().put("entry_id", id))
            }
            if(section=="invoices" && controller.route.mode=="issue" && !initialModeHandled) {issueReview=detail;initialModeHandled=true}
        } catch(cancelled:CancellationException){throw cancelled}catch (failure: Exception) { error = failure.message ?: "Datensatz konnte nicht gelesen werden." }
    }
    val row = detail?.let { when(section) { "invoices" -> it.optJSONObject("data") ?: it.optJSONObject("invoice"); "offers" -> it.optJSONObject("offer"); else -> it.optJSONObject("entry") } }
    when {
        pendingAction != null -> {
            val action = pendingAction!!
            NativeActionForm(controller, action.first, when(action.first) {
                "create_invoice_correction" -> "Korrekturentwurf / Storno"
                "record_offer_acceptance" -> "Annahme mit Nachweis erfassen"
                "decline_offer" -> "Ablehnung erfassen"
                "finalize_offer" -> "Geprüftes Angebot finalisieren"
                "convert_offer_to_order" -> "Angenommenes Angebot in Auftrag übernehmen"
                else -> "Angaben prüfen und bestätigen"
            }, action.second) { result ->
                val tool=action.first;pendingAction = null; refresh++
                if(tool=="archive_entity") controller.navigate(section)
                val saved=financeData(result)
                if(tool=="duplicate_offer" && saved.optString("offer_id").isNotEmpty()) controller.navigate("offers",saved.getString("offer_id"))
                if(tool=="convert_offer_to_order" && saved.optString("order_id").isNotEmpty()) controller.navigate("orders",saved.getString("order_id"))
                if(tool in listOf("prepare_offer_delivery","prepare_offer_share")) scope.launch {try{controller.shareDocument("offer",id,if(tool=="prepare_offer_delivery") "email" else "share")}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){controller.message(f.message.orEmpty())}}
            }
            TextButton(onClick = { pendingAction = null }) { Text("Zurück zum Beleg") }
        }
        offerReview != null -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val review=offerReview!!
            val snapshot=review.getJSONObject("review_snapshot")
            Text("Finalisierung vollständig prüfen",style=MaterialTheme.typography.headlineSmall)
            FinanceIssuerSummary(snapshot.optJSONObject("business_settings"))
            Text("Rechnungsempfänger",style=MaterialTheme.typography.titleMedium)
            FinanceValues(snapshot.optJSONObject("recipient"),financePartyFields)
            Text("Einsatzkunde",style=MaterialTheme.typography.titleMedium)
            FinanceValues(snapshot.optJSONObject("customer"),financePartyFields)
            FinanceValues(snapshot.optJSONObject("order"),listOf("title" to "Auftrag","location" to "Einsatzort","status" to "Status"))
            FinanceDraftSummary(controller,review.getJSONObject("offer"),"offers")
            val entries=snapshot.optJSONArray("service_catalog") ?: JSONArray()
            for(i in 0 until entries.length()) FinanceValues(entries.optJSONObject(i),listOf("title" to "Bestätigter Baustein","default_price" to "Katalogpreis","price_confirmed" to "Preis bestätigt"))
            val validation=review.optJSONObject("validation") ?: JSONObject()
            val errors=validation.optJSONArray("errors") ?: JSONArray()
            for(i in 0 until errors.length()) Text(errors.optString(i),color=MaterialTheme.colorScheme.error)
            FinanceCheck("Ich habe Aussteller, Empfänger, Bindungsart, Leistungsumfang und alle Preise dieses Angebots geprüft.",offerReviewed) {offerReviewed=it}
            Button(enabled=offerReviewed && validation.optBoolean("valid"),onClick={pendingAction="finalize_offer" to JSONObject().put("offer_id",id).put("expected_record_hash",review.getString("record_hash")).put("expected_context_hash",review.getString("context_hash"));offerReview=null},modifier=Modifier.fillMaxWidth()) {Text("Geprüfte Finalisierung bestätigen")}
            TextButton(onClick={offerReview=null}) {Text("Zum Angebot zurück")}
        }
        issueReview != null -> FinanceIssueReview(controller, id, issueReview!!, onClose = { issueReview = null; if(controller.route.args.optString("_draft_key").isNotEmpty()) controller.navigate(section,id) else refresh++ })
        section == "payments" -> FinancePayments(controller, id)
        editing && (fresh || row!=null || controller.route.args.optString("_draft_key").isNotEmpty()) -> FinanceEditor(section, if (fresh) null else row, if (fresh) "" else detail?.optString("record_hash").orEmpty(), controller,
            onClose = { editing = false; fresh = false; if(controller.route.args.optString("_draft_key").isNotEmpty()) controller.navigate(section,id) else refresh++ }, onSaved = { savedId -> editing = false; fresh = false; if (savedId.isNotEmpty()) controller.navigate(section, savedId, args = controller.route.args) else refresh++ })
        id.isEmpty() -> listStates.SaveableStateProvider("finance-list") { FinanceList(section, controller, onCreate = { fresh = true; editing = true }) }
        row == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error.isEmpty()) CircularProgressIndicator() else Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { refresh++ }) { Text("Erneut lesen") }
        }
        else -> Column(Modifier.fillMaxSize()) {
            val editable = section == "catalog" || if(section == "invoices") !IssuedInvoiceRules.isIssued(row) && row.optString("status", "Entwurf") == "Entwurf" else OfferRules.editable(row)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (section == "invoices") financeInvoiceTitle(row) else row.optString("title"), style = MaterialTheme.typography.headlineSmall)
            Text(financeStatus(if(section == "invoices") row.optString("status", "Entwurf") else row.optString("status", if(section == "catalog") "Leistungsbaustein" else "draft")))
            if (section != "catalog") {
                Text("Summe: ${financeMoney(row.opt("gross_total") ?: row.opt("amount"))}")
                val dates = if (section == "invoices") listOf("invoice_date" to "Rechnungsdatum", "service_start" to "Leistung ab", "service_end" to "Leistung bis") else listOf("issue_date" to "Datum", "valid_until" to "Gültig bis")
                val dateText = dates.mapNotNull { (key, label) -> row.optString(key).takeIf { it.isNotBlank() }?.let { "$label: ${nativeDisplayDate(it)}" } }.joinToString(" · ")
                if (dateText.isNotEmpty()) Text(dateText)
                FinancePositionSummary(row.optJSONArray("positions"))
                for ((key, label) in listOf("header_text" to "Kopftext", "footer_text" to "Fußtext", "notes" to "Notizen", "binding_notice" to "Bindungshinweis", "payment_terms" to "Zahlungsbedingungen", "assumptions" to "Annahmen", "exclusions" to "Ausschlüsse")) if(row.optString(key).isNotEmpty()) Text("$label: ${row.optString(key)}")
            } else {
                Text(row.optString("description")); Text("Einheit: ${row.optString("unit")} · Preis: ${financeMoney(row.opt("default_price"))}")
                Text(if (row.optBoolean("price_confirmed")) "Preis bestätigt" else "Preis noch nicht bestätigt")
                Text(if (row.optBoolean("active", true)) "Aktiv" else "Deaktiviert")
            }
            if (section == "invoices") {
                if (editable) OutlinedButton(onClick = { scope.launch { try { issueReview = controller.call("get_invoice", JSONObject().put("invoice_id", id)) } catch(cancelled:CancellationException){throw cancelled}catch(f: Exception) { controller.message(f.message.orEmpty()) } } }, Modifier.fillMaxWidth()) { Text("Ausgabe vollständig prüfen") }
                else {
                    Text("Ausgestellte Angaben und Original bleiben erhalten.")
                    OutlinedButton(onClick = { pendingAction = "create_invoice_correction" to JSONObject().put("invoice_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }, Modifier.fillMaxWidth()) { Text("Korrektur / Storno vorbereiten") }
                    FinanceInvoiceDocument(controller, id)
                }
            }
            if (section == "offers") {
                if (editable) {
                    OutlinedButton(onClick = { scope.launch { try { val validation = controller.query("validate_offer", JSONObject().put("offer_id", id)); detail = validation; controller.message(validation.optJSONObject("validation")?.let { if(it.optBoolean("valid")) "Angaben sind vollständig." else it.optJSONArray("errors")?.join("; ").orEmpty() } ?: "Prüfung gelesen.") } catch(cancelled:CancellationException){throw cancelled}catch(f: Exception) { controller.message(f.message.orEmpty()) } } }, Modifier.fillMaxWidth()) { Text("Pflichtangaben prüfen") }
                    OutlinedButton(onClick = { pendingAction = "validate_offer_draft" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }, Modifier.fillMaxWidth()) { Text("Geprüften Entwurfsstand speichern") }
                    OutlinedButton(onClick = {scope.launch {try {offerReview=controller.query("get_offer",JSONObject().put("offer_id",id).put("include_review",true));offerReviewed=false}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){controller.message(f.message.orEmpty())}}}, Modifier.fillMaxWidth()) { Text("Finalisierung prüfen") }
                } else {
                    Text(detail?.optJSONObject("decision")?.let { "Entscheidung: ${financeStatus(it.optString("decision"))} · ${nativeDisplayDate(it.optString("accepted_at"))}" } ?: "Annahme oder Ablehnung noch nicht erfasst.")
                    FinanceValues(detail?.optJSONObject("decision"),listOf("evidence" to "Annahmenachweis","reason" to "Begründung","recorded_at" to "Erfasst am"))
                    OutlinedButton(onClick={scope.launch {try{controller.viewDocument("offer",id)}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){controller.message(f.message.orEmpty())}}},modifier=Modifier.fillMaxWidth()) {Text("Original-PDF öffnen")}
                    OutlinedButton(onClick = { pendingAction = "prepare_offer_delivery" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }, Modifier.fillMaxWidth()) { Text("E-Mail-Übergabe vorbereiten") }
                    OutlinedButton(onClick = { pendingAction = "confirm_offer_delivery" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }, Modifier.fillMaxWidth()) { Text("Versandbeleg beim Anbieter prüfen") }
                    OutlinedButton(onClick = { pendingAction = "record_offer_acceptance" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }, Modifier.fillMaxWidth()) { Text("Annahme nachweisen") }
                    TextButton(onClick = { pendingAction = "decline_offer" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }) { Text("Ablehnung erfassen") }
                    OutlinedButton(onClick = { pendingAction = "convert_offer_to_order" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")).apply {if(row.optString("order_id").isNotEmpty()) put("existing_order_id",row.getString("order_id")).put("expected_order_hash",detail!!.optString("order_hash"))} }, Modifier.fillMaxWidth()) { Text("Auftrag / Nachtrag übernehmen") }
                    TextButton(onClick = { controller.action("Entscheidungsverlauf", "get_offer_decision_history", JSONObject().put("offer_id", id)) }) { Text("Entscheidungsverlauf lesen") }
                }
                TextButton(onClick = { pendingAction = "duplicate_offer" to JSONObject().put("offer_id", id) }) { Text("Als neue Version duplizieren") }
            }
            if (section == "catalog") OutlinedButton(onClick = { pendingAction = "update_catalog_entry" to JSONObject().put("entry_id", id).put("expected_record_hash", detail!!.optString("record_hash")).put("updates", JSONObject().put("active", !row.optBoolean("active", true))) }, Modifier.fillMaxWidth()) { Text(if(row.optBoolean("active", true)) "Baustein deaktivieren" else "Baustein reaktivieren") }
            if (editable && section != "catalog") NativeDeleteButton(controller, if (section == "offers") "offer" else "invoice", id, label = "Entwurf in Papierkorb") { controller.returnToList(section) }
            }
            NativeActions {
                TextButton(onClick = { controller.returnToList(section) }) { Text("Zur Liste") }
                if (editable) Button(onClick = { fresh = false; editing = true }) { Text("Bearbeiten") }
                else if (section == "invoices") Button(onClick = { controller.navigate("payments", id) }) { Text("Zahlungen und belegten Rest prüfen") }
                else if (section == "offers") Button(onClick = { pendingAction = "prepare_offer_share" to JSONObject().put("offer_id", id).put("expected_record_hash", detail!!.optString("record_hash")) }) { Text("Geprüftes Original-PDF teilen") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinanceList(section: String, controller: NativeAppController, onCreate: () -> Unit) {
    val scope=rememberCoroutineScope()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var customer by rememberSaveable(section) { mutableStateOf(controller.route.args.optString("customer_id")) }
    var status by rememberSaveable(section) { mutableStateOf(controller.route.args.optString("status")) }
    var query by rememberSaveable(section) { mutableStateOf("") }
    var offset by rememberSaveable(section) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val repository = remember(controller) { controller.store.nativeData() }
    val source = remember(section, customer, status, query, offset) {
        error = ""
        repository.watchList(if (section == "catalog") "service_catalog" else section,
            search = query, status = status, customerId = customer, offset = offset, limit = 50, includeInactive = section=="catalog")
            .catch { failure -> error = NATIVE_LIST_READ_ERROR }
    }
    val displayFlow = remember(source) { source.map<NativeListPage, NativeListPage?> { it }.onStart { error = ""; emit(null) } }
    val loadedPage by displayFlow.collectAsStateWithLifecycle(null)
    val page = loadedPage ?: NativeListPage(emptyList(), 0, offset, 50)
    val listLoading = loadedPage == null && error.isEmpty()
    NativeSearchList(if (section == "catalog") "Bausteine suchen" else "Suchen", query, { query = it; offset = 0 },
        if (listLoading) "Wird geladen …" else "${page.total} Treffer · ${page.rows.size} auf dieser Seite",
        onFilters = if (section == "catalog") null else ({ filtersOpen = !filtersOpen }), onFocus = { filtersOpen = false }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!imeVisible) item { NativeContext(controller, JSONObject(controller.route.args.toString()).put("customer_id", customer), if (section == "invoices") "Rechnungsempfänger" else "Kunde") }
        if (filtersOpen) item(key = "native-list-filters") {
        if (section != "catalog") {
            NativeRecordPicker(controller, "customers", if (section == "invoices") "Rechnungsempfänger" else "Kunde", customer) { customer = it; offset = 0 }
            FinanceChoice("Status", status, if(section == "invoices") listOf("" to "Alle", "Entwurf" to "Entwurf", "Gestellt" to "Gestellt", "Bezahlt" to "Bezahlt", "Storniert" to "Storniert") else listOf("" to "Alle", "draft" to "Entwurf", "validated" to "Geprüft", "finalized" to "Finalisiert", "accepted" to "Angenommen", "share_prepared" to "Teilen vorbereitet", "sent" to "Versand bestätigt")) { status = it; offset = 0 }
        }
        }
        if (!imeVisible && (customer.isNotEmpty() || status.isNotEmpty() || query.isNotEmpty())) item { TextButton(onClick = { customer="";status="";query="";offset=0 }) { Text("Filter und Suche zurücksetzen") } }
        if(error.isNotEmpty()) item { Text(error, color=MaterialTheme.colorScheme.error) }
        if (listLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!listLoading && page.rows.isEmpty() && error.isEmpty()) item { Text("Keine Treffer. Suchtext oder Filter ändern.") }
        items(page.rows, key = { it.id }) { row -> OutlinedCard(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { controller.navigate(section, row.id, args = JSONObject(controller.route.args.toString()).put("customer_id", customer)) }) {
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(if (section == "invoices" && row.title.startsWith("Entwurf-")) "Rechnungsentwurf" else row.title, style=MaterialTheme.typography.titleMedium)
                if (row.subtitle.isNotEmpty()) Text(nativeDisplayText(row.subtitle))
                if (row.status.isNotEmpty()) NativeStatus(financeStatus(row.status))
                if (section == "catalog") Text(if (row.active) "Aktiv" else "Deaktiviert")
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(enabled=offset>0,onClick={offset=(offset-50).coerceAtLeast(0)}) { Text("Zurück") }
            TextButton(enabled=page.hasMore,onClick={offset+=50}) { Text("Weitere") }
        }
        }
        if(section == "invoices" && !imeVisible) item {
            OutlinedButton(onClick={controller.navigate("payments")},Modifier.fillMaxWidth()) { Text("Zahlungsübersicht") }
            TextButton(onClick={controller.action("Aussteller und Steuermodus", "get_business_settings")}) { Text("Ausstellerdaten und Nummernkonfiguration lesen") }
            TextButton(onClick={scope.launch {try {val settings=controller.query("get_business_settings");controller.action("Ausstellerdaten bewusst aktualisieren", "update_business_settings",JSONObject().put("expected_record_hash",settings.getString("record_hash")).put("updates",JSONObject()))}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}}) { Text("Ausstellerdaten und Nummernkonfiguration bearbeiten") }
        }
        }
        if (!imeVisible) Surface(tonalElevation = 3.dp) {
            Button(onClick = onCreate, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(if (section == "catalog") "Neuer Leistungsbaustein" else if (section == "invoices") "Neuer Rechnungsentwurf" else "Neues Angebot / Kostenvoranschlag")
            }
        }
    }
}

@Composable
private fun FinanceChoice(label:String, value:String, choices:List<Pair<String,String>>, onChange:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},Modifier.fillMaxWidth()) { Text("$label: ${choices.firstOrNull { it.first==value }?.second ?: value.ifEmpty { "Bitte wählen" }}") }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { choices.forEach { (key,title) -> DropdownMenuItem(text={Text(title)},onClick={expanded=false;onChange(key)}) } }
    }
}

@Composable
private fun FinanceCheck(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) },horizontalArrangement=Arrangement.spacedBy(8.dp)) { Checkbox(value,onCheckedChange=onChange); Text(label,Modifier.weight(1f).padding(top=12.dp)) }
}

@Composable
private fun FinancePositionSummary(positions:JSONArray?) {
    positions ?: return
    for(index in 0 until positions.length()) { val row=positions.optJSONObject(index) ?: continue
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${index+1}. ${row.optString("title",row.optString("description"))}",style=MaterialTheme.typography.titleSmall)
            Text("${if(!row.has("quantity") || row.isNull("quantity")) "Menge offen" else row.optString("quantity")} ${row.optString("unit")} × ${financeMoney(row.opt("price"))} · USt ${if(!row.has("tax_rate") || row.isNull("tax_rate")) "offen" else row.optString("tax_rate")+" %"}")
            if(row.optString("description").isNotEmpty()) Text(row.optString("description"))
            if(row.has("net")) Text("Netto ${financeMoney(row.opt("net"))} · Steuer ${financeMoney(row.opt("tax"))}")
        } }
    }
}

@Composable
private fun FinanceEditor(section:String, original:JSONObject?, recordHash:String, controller:NativeAppController, onClose:()->Unit, onSaved:(String)->Unit) {
    val id=original?.optString("id").orEmpty().ifEmpty {if(controller.route.mode=="edit") controller.route.id else ""}
    val key=controller.route.args.optString("_draft_key").ifEmpty {"finance:$section:${id.ifEmpty { "new" }}"}
    var form by remember(key) { mutableStateOf(original?.let(::financeCopy) ?: JSONObject()) }
    var loaded by remember(key) { mutableStateOf(false) }
    var loadRetry by remember(key) {mutableIntStateOf(0)}
    var changed by remember(key) { mutableStateOf(false) }
    var pending by remember(key) { mutableStateOf<JSONObject?>(null) }
    var pendingTool by remember(key) { mutableStateOf("") }
    var restored by remember(key) { mutableStateOf(false) }
    var reviewed by remember(key) { mutableStateOf(false) }
    var request by rememberSaveable(key) { mutableStateOf(UUID.randomUUID().toString()) }
    var observedHash by rememberSaveable(key) { mutableStateOf(recordHash) }
    var error by remember(key) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sourceRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sourceLoading by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf("") }
    var sourceVisible by remember { mutableStateOf(false) }
    var sourceOffset by remember { mutableIntStateOf(0) }
    var sourceSearch by remember { mutableStateOf("") }
    var offerContext by remember { mutableStateOf<JSONObject?>(null) }
    var comparison by remember { mutableStateOf<JSONObject?>(null) }
    var comparisonHash by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    fun retain(next:JSONObject) {
        if(pending!=null) return
        form=next;changed=true
        controller.saveDraft(key,JSONObject().put("record",next).put("record_hash",observedHash).put("request_id",request))
    }
    fun field(name:String,value:Any?) { retain(financeCopy(form).put(name,value ?: JSONObject.NULL)) }
    LaunchedEffect(key,loadRetry) {
        try {
            val saved=controller.draft(key)
            if(saved!=null) {
                form=saved.optJSONObject("record") ?: form
                observedHash=saved.optString("record_hash",recordHash)
                request=saved.optString("request_id",request)
                pending=saved.optJSONObject("pending")?.takeIf {it.length()>0}
                pendingTool=saved.optString("original_tool")
                restored=saved.optBoolean("_restore_action_review_required")
                changed=true
            } else if(original==null) {
                if(id.isNotEmpty()) throw IllegalArgumentException("Der ursprüngliche Beleg ist nicht lesbar. Es wird kein Ersatzbeleg angelegt.")
                val settings=runCatching { controller.query("get_business_settings") }.getOrDefault(JSONObject()).let { it.optJSONObject("settings") ?: it }
                form=JSONObject().put(if(section=="invoices") "invoice_date" else "issue_date",financeToday()).put("positions",JSONArray())
                if(settings.optString("tax_mode") in listOf("small_business","standard")) form.put("tax_mode",settings.getString("tax_mode"))
                if(section=="invoices") form.put("payment_days",settings.optInt("default_payment_days",14))
                if(section=="catalog") form.put("active",true).put("pricing_type","unit")
                for (field in listOf("customer_id", "order_id")) if (controller.route.args.optString(field).isNotEmpty()) form.put(field, controller.route.args.getString(field))
                if (section in listOf("invoices", "offers")) form.put("_source_context", JSONObject().apply {
                    val fields = if (section == "invoices") listOf("work_customer_id", "order_id", "date_from", "date_to") else listOf("property_id", "inquiry_id")
                    for (field in fields) if (controller.route.args.optString(field).isNotEmpty()) put(field, controller.route.args.getString(field))
                })
                if (section == "offers") {
                    if (controller.route.args.optString("title").isNotBlank()) form.put("title", controller.route.args.getString("title"))
                    val propertyId = controller.route.args.optString("property_id")
                    if (propertyId.isNotEmpty()) controller.store.nativeData().detail("properties", propertyId)?.let { property ->
                        form.put("location", property.optString("address"))
                    }
                }
            }
            loaded=true
        } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception) {error=f.message.orEmpty();loaded=false}
    }
    fun save(validate:Boolean=false) {
        if(busy || !loaded) return
        scope.launch {
            busy=true;error=""
            var attemptedWrite=false
            try {
                val tool = pendingTool.ifEmpty { when(section) { "invoices" -> if(id.isEmpty()) "create_invoice_draft" else "update_invoice_draft"; "offers" -> if(id.isEmpty()) {if(validate) "create_and_validate_offer" else "create_offer_draft"} else "update_offer_draft"; else -> if(id.isEmpty()) "create_catalog_entry" else "update_catalog_entry" } }
                val prior = pending
                if (prior != null) {
                    val proof = financeInspect(controller, tool, prior)
                    if (proof != null) { controller.removeDraft(key); onSaved(proof.optString("invoice_id", proof.optString("offer_id", proof.optString("entry_id", proof.optString("entity_id", id))))); return@launch }
                }
                if (restored) throw IllegalStateException("Wiederhergestellten Entwurf und aktuellen Bestand zuerst bewusst prüfen. Der ursprüngliche Auftrag wird nicht erneut ausgeführt.")
                val args=pending ?: run {
                    if(!changed) { onClose();return@launch }
                    val payload=financeFields(form,when(section) { "invoices" -> EntityContract.INVOICE_FIELDS; "offers" -> OfferRules.FIELDS; else -> arrayOf("title","description","unit","pricing_type","category","legal_notes","assumptions","exclusions","default_price","default_quantity","active","price_confirmed") })
                    val numeric=if(section=="catalog") listOf("default_price","default_quantity") else emptyList()
                    numeric.forEach { name -> if(payload.has(name)) payload.put(name,if(payload.optString(name).isBlank() || payload.isNull(name)) JSONObject.NULL else BigDecimal(payload.get(name).toString().replace(',','.'))) }
                    if(section!="catalog") {
                        val positions=JSONArray()
                        val raw=payload.optJSONArray("positions") ?: JSONArray()
                        for(i in 0 until raw.length()) {
                            val line=financeFields(raw.getJSONObject(i),if(section=="offers") arrayOf("title","description","unit","catalog_entry_id","catalog_hash","price_source","price_basis","price_note","calculation","quantity","price","tax_rate") else arrayOf("title","description","unit","quantity","price","tax_rate","discount","worklog_id","source_type","source_id","source_quantity_decimal"))
                            for(name in listOf("quantity","price","tax_rate","discount")) if(line.has(name)) {
                                val text=if(line.isNull(name)) "" else line.get(name).toString().trim()
                                if(text.isEmpty()) { if(section=="invoices" && name in listOf("quantity","price")) throw IllegalArgumentException("Position ${i+1}: Menge und Preis ausdrücklich angeben.");line.put(name,if(section=="offers") JSONObject.NULL else if(name=="tax_rate" && payload.optString("tax_mode")!="small_business") throw IllegalArgumentException("Position ${i+1}: Steuersatz ausdrücklich angeben.") else 0) }
                                else line.put(name,BigDecimal(text.replace(',','.')))
                            }
                            if(section=="invoices") {
                                if(line.optString("title").isBlank()) throw IllegalArgumentException("Position ${i+1}: Bezeichnung fehlt.")
                                if(!line.has("tax_rate")) {if(payload.optString("tax_mode")!="small_business") throw IllegalArgumentException("Position ${i+1}: Steuersatz ausdrücklich angeben.");line.put("tax_rate",0)}
                            }
                            line.optJSONObject("calculation")?.let {calculation -> for(name in listOf("base_price","factor","surcharge")) if(calculation.has(name) && !calculation.isNull(name)) calculation.put(name,BigDecimal(calculation.get(name).toString().replace(',','.')))}
                            positions.put(line)
                        }
                        payload.put("positions",positions)
                        if(payload.has("payment_days")) payload.put("payment_days",payload.get("payment_days").toString().toInt())
                    }
                    val args=if(id.isEmpty()) payload else JSONObject().put(if(section=="catalog") "entry_id" else if(section=="invoices") "invoice_id" else "offer_id",id).put("expected_record_hash",observedHash).put("updates",payload)
                    args.put("request_id",request)
                }
                pending=financeCopy(args)
                pendingTool=tool
                controller.saveDraft(key,JSONObject().put("record",form).put("record_hash",observedHash).put("request_id",request).put("pending",args).put("original_tool",tool))
                controller.flushDrafts()
                attemptedWrite=true
                val result=financeData(controller.call(tool,args))
                controller.removeDraft(key)
                onSaved(result.optString(if(section=="catalog") "entry_id" else if(section=="offers") "offer_id" else "invoice_id",result.optString("entity_id",id)))
            } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception) {
                if (attemptedWrite && ((f as? NativeCallFailure)?.detail?.optString("commit_state") == "not_committed" || (f as? NativeCallFailure)?.detail?.optString("code") in setOf("INVALID_INPUT","WRITE_CONFLICT","INVOICE_SOURCE_QUANTITY_EXCEEDED","INVOICE_SOURCE_ALREADY_BILLED","INVOICE_SOURCE_UNIT_REVIEW_REQUIRED"))) {
                    pending=null;pendingTool="";request=UUID.randomUUID().toString()
                    controller.saveDraft(key,JSONObject().put("record",form).put("record_hash",observedHash).put("request_id",request))
                }
                error=f.message ?: "Speichern ist noch nicht eindeutig bestätigt. Derselbe Vorgang bleibt erhalten."
            } finally { busy=false }
        }
    }
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(id.isEmpty()) "Neuer Entwurf" else "Gespeicherten Entwurf bearbeiten",style=MaterialTheme.typography.headlineSmall)
        if(!loaded) {if(error.isEmpty()) CircularProgressIndicator() else {Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={error="";loadRetry++}) {Text("Entwurf erneut lesen")}};TextButton(onClick=onClose) {Text("Zurück")};return@Column}
        if (restored) Text("Dieser Entwurf stammt aus einem früheren Gerätestand. Aktuellen Beleg, Beziehungen und Eingaben vor einer neuen Aktion prüfen.")
        if (pending == null && (id.isNotEmpty() || restored)) {
            if (id.isNotEmpty()) TextButton(enabled=!busy,onClick={scope.launch {try {
                val latest=controller.call(if(section=="invoices") "get_invoice" else if(section=="offers") "get_offer" else "get_offer_template",JSONObject().put(if(section=="invoices") "invoice_id" else if(section=="offers") "offer_id" else "entry_id",id))
                val data=financeData(latest)
                val current=if(section=="invoices") latest.optJSONObject("data") else data.optJSONObject(if(section=="offers") "offer" else "entry")
                if(current==null || (section=="invoices" && IssuedInvoiceRules.isIssued(current)) || (section=="offers" && !OfferRules.editable(current))) throw IllegalArgumentException("Der aktuelle Beleg kann nicht als Entwurf bearbeitet werden.")
                comparison=current;comparisonHash=latest.optString("record_hash",data.optString("record_hash"));reviewed=false
            }catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}}) {Text("Aktuellen Stand zum Vergleich lesen")}
            comparison?.let { current -> Text("Aktuell gespeicherter Stand",style=MaterialTheme.typography.titleMedium);FinanceDraftSummary(controller,current,section);Text("Ihre erhaltenen Eingaben stehen darunter und bleiben bestehen.") }
            if (comparison!=null || id.isEmpty()) {
                FinanceCheck("Ich habe diesen Entwurf, seine Beziehungen und den aktuellen Bestand verglichen und übernehme ihn ausdrücklich als neue Bearbeitung.", reviewed) { reviewed=it }
                TextButton(enabled=reviewed && !busy,onClick={scope.launch {try {
                    if(id.isNotEmpty()) observedHash=comparisonHash
                    request=UUID.randomUUID().toString()
                    controller.adoptDraft(key,JSONObject().put("record",form).put("record_hash",observedHash).put("request_id",request));pendingTool="";restored=false;reviewed=false;comparison=null
                }catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}}) {Text("Geprüften Entwurf neu übernehmen")}
            }
        }
        if(pending!=null) {
            Text("Der ursprüngliche Speichervorgang und seine Eingaben bleiben erhalten. Vor einer geänderten Anfrage zuerst den gespeicherten Stand lesen.")
            TextButton(onClick={scope.launch { try { if(id.isNotEmpty()) { val read=controller.call(if(section=="invoices") "get_invoice" else if(section=="offers") "get_offer" else "get_offer_template",JSONObject().put(if(section=="invoices") "invoice_id" else if(section=="offers") "offer_id" else "entry_id",id));controller.message("Aktueller Datensatz gelesen. Ihre Eingaben und der ursprüngliche Vergleichsstand bleiben erhalten."); if(read.optString("record_hash")==observedHash) controller.message("Der gespeicherte Vergleichsstand ist unverändert.") } else controller.message("Denselben Speichervorgang erneut prüfen; seine Kennung bleibt erhalten.") } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()} }}) { Text("Gespeicherten Stand lesen") }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            if(pending==null) {
                if(section!="catalog") {
                    NativeRecordPicker(controller,"customers",if(section=="invoices") "Rechnungsempfänger" else "Einsatzkunde",form.optString("customer_id")) { field("customer_id",it) }
                    if(section=="offers") NativeRecordPicker(controller,"customers","Abweichender Rechnungsempfänger",form.optString("billing_customer_id")) { field("billing_customer_id",it) }
                    NativeRecordPicker(controller,"orders","Zugeordneter Auftrag (optional)",form.optString("order_id"), customerId = if (section == "offers") form.optString("customer_id") else "") { field("order_id",it) }
                    if(section=="offers") {
                        NativeContext(controller, form.optJSONObject("_source_context") ?: JSONObject())
                        NativeField("Titel",form.optString("title"),{field("title",it)},required=true)
                        FinanceChoice("Dokumentart",form.optString("document_type"),OfferContract.types().map { it to OfferRules.label(it) }) { field("document_type",it) }
                        NativeField("Angebotsdatum",form.optString("issue_date"),{field("issue_date",it)},required=true)
                        NativeField("Gültig bis",form.optString("valid_until"),{field("valid_until",it)},required=true)
                        NativeField("Ausführungszeitraum",form.optString("execution_window"),{field("execution_window",it)},required=true)
                        NativeField("Einsatzort",form.optString("location"),{field("location",it)})
                        NativeField("Zahlungsbedingungen",form.optString("payment_terms"),{field("payment_terms",it)},required=true,multiline=true)
                        FinanceChoice("Kundentyp",form.optString("customer_type"),listOf("b2b" to "Unternehmen (B2B)","consumer" to "Verbraucher")) {field("customer_type",it)}
                        FinanceChoice("Vertragsweg",form.optString("contract_channel"),listOf("on_premise" to "Im Geschäft","distance" to "Fernabsatz","off_premise" to "Außerhalb der Geschäftsräume")) {field("contract_channel",it)}
                        NativeField("Verbraucherinformationen / Rücktritt",form.optString("consumer_information"),{field("consumer_information",it)},multiline=true)
                        FinanceCheck("Verbraucherinformationen ausdrücklich geprüft",form.optBoolean("consumer_information_confirmed")) {field("consumer_information_confirmed",it)}
                        NativeField("Bindungshinweis",form.optString("binding_notice",OfferContract.type(form.optString("document_type")).optString("notice")),{field("binding_notice",it)},multiline=true)
                        FinanceCheck("Bindungsart und zugehörigen Hinweistext geprüft",form.optBoolean("binding_confirmed")) {field("binding_confirmed",it)}
                        FinanceCheck("Leistungsumfang, Annahmen und Ausschlüsse geprüft",form.optBoolean("scope_confirmed")) {field("scope_confirmed",it)}
                        NativeField("Annahmen",form.optString("assumptions"),{field("assumptions",it)},multiline=true)
                        NativeField("Ausschlüsse",form.optString("exclusions"),{field("exclusions",it)},multiline=true)
                    } else {
                        NativeField("Rechnungsdatum",form.optString("invoice_date"),{field("invoice_date",it)},required=true)
                        NativeField("Leistungsbeginn",form.optString("service_start"),{field("service_start",it)},required=true)
                        NativeField("Leistungsende",form.optString("service_end"),{field("service_end",it)},required=true)
                        NativeField("Zahlungsziel in Tagen",form.optString("payment_days"),{field("payment_days",it)})
                        FinanceCheck("Gewöhnliche Inlandsleistung ausdrücklich geprüft",form.optBoolean("domestic_service_confirmed")) {field("domestic_service_confirmed",it)}
                        FinanceChoice("Empfängertyp",form.optString("recipient_type"),listOf("" to "Noch nicht bestätigt","b2b" to "Unternehmen (B2B)","consumer" to "Verbraucher")) {field("recipient_type",it)}
                        NativeField("UID des Empfängers",form.optString("recipient_vat_id"),{field("recipient_vat_id",it)})
                    }
                    FinanceChoice("Steuermodus",form.optString("tax_mode"),listOf("small_business" to "Kleinunternehmer","standard" to "Umsatzsteuer")) {field("tax_mode",it)}
                    Text("Der Steuermodus muss mit den zentral bestätigten Ausstellerdaten übereinstimmen.")
                    if (section == "invoices") {
                        val sourceContext = form.optJSONObject("_source_context") ?: JSONObject()
                        NativeContext(controller, JSONObject(sourceContext.toString()).put("customer_id", sourceContext.optString("work_customer_id")), "Arbeitskunde")
                        if (sourceContext.length() > 0) TextButton({ field("_source_context", JSONObject()) }) { Text("Alle offenen Arbeiten dieses Empfängers anzeigen") }
                    }
                    FinancePositions(form.optJSONArray("positions") ?: JSONArray(),section=="offers") {field("positions",it)}
                    OutlinedButton(onClick={sourceVisible=!sourceVisible;sourceOffset=0},Modifier.fillMaxWidth()) {Text(if(section=="offers") "Leistungsbausteine auswählen" else "Offene Arbeiten mit Restmengen auswählen")}
                    if(sourceVisible) {
                        LaunchedEffect(section,sourceOffset,sourceSearch,form.optString("customer_id"),form.optJSONObject("_source_context")?.toString()) {
                            sourceRows=emptyList(); sourceLoading=true; sourceError=""
                            try {
                                sourceRows=if(section=="offers") financeRows(controller.query("search_offer_templates",JSONObject().put("query",sourceSearch).put("offset",sourceOffset).put("limit",40)),"entries")
                                else {
                                    if(form.optString("customer_id").isEmpty()) throw IllegalArgumentException("Zuerst den Rechnungsempfänger auswählen.")
                                    val sourceContext = form.optJSONObject("_source_context") ?: JSONObject()
                                    financeRows(controller.query("list_work_records",JSONObject().put("billing_customer_id",form.getString("customer_id")).put("billed",false).put("offset",sourceOffset).put("limit",40).apply {
                                        if (sourceContext.optString("work_customer_id").isNotEmpty()) put("customer_id", sourceContext.getString("work_customer_id"))
                                        for (field in listOf("order_id", "date_from", "date_to")) if (sourceContext.optString(field).isNotEmpty()) put(field, sourceContext.getString(field))
                                    }),"work_records")
                                }
                            } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception) {sourceError=f.message.orEmpty()} finally {sourceLoading=false}
                        }
                        NativeSearchDialog(if (section == "offers") "Leistungsbausteine" else "Offene Arbeiten", sourceSearch, { sourceSearch=it; sourceOffset=0 }, sourceRows.size,
                            loading=sourceLoading, error=sourceError, pageCount=true, searchLabel="Bausteine suchen", searchEnabled=section=="offers", onDismiss={sourceVisible=false}) { close ->
                        items(sourceRows, key={it.getString("id")}) { source -> TextButton(onClick={scope.launch {try {
                            val lines=JSONArray((form.optJSONArray("positions") ?: JSONArray()).toString())
                            if(section=="offers") {
                                val selected=controller.query("get_offer_template",JSONObject().put("entry_id",source.getString("id")))
                                val entry=selected.getJSONObject("entry")
                                val line=JSONObject().put("title",entry.optString("title")).put("description",entry.optString("description")).put("unit",entry.optString("unit")).put("quantity",entry.opt("default_quantity") ?: JSONObject.NULL).put("price",if(entry.optBoolean("price_confirmed")) entry.opt("default_price") ?: JSONObject.NULL else JSONObject.NULL)
                                if(entry.optBoolean("price_confirmed") && !entry.isNull("default_price")) line.put("catalog_entry_id",entry.getString("id")).put("catalog_hash",selected.getString("record_hash")).put("price_basis","catalog")
                                lines.put(line)
                            } else {
                                val selected=controller.call("get_work_record",JSONObject().put("work_record_id",source.getString("id")))
                                val work=selected.getJSONObject("data")
                                val remaining=selected.optString("quantity_remaining_decimal")
                                if(selected.optJSONObject("billing_quantity")?.optInt("formatVersion")!=1 || remaining.isEmpty()) throw IllegalArgumentException("Die exakte Restmenge fehlt. Den Arbeitseintrag zuerst prüfen.")
                                if(BigDecimal(remaining).signum()<=0) throw IllegalArgumentException("Diese Arbeit hat keine offene Restmenge.")
                                if((0 until lines.length()).any {lines.getJSONObject(it).optString("worklog_id")==work.optString("id")}) throw IllegalArgumentException("Diese Arbeit ist bereits ausgewählt.")
                                lines.put(JSONObject().put("title",work.optString("activity",work.optString("description"))).put("description",work.optString("description")).put("unit","h").put("quantity",remaining).put("price",JSONObject.NULL).put("worklog_id",work.getString("id")).put("source_type","worklog").put("source_id",work.getString("id")).put("source_quantity_decimal",remaining))
                            };field("positions",lines); close()
                        }catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){sourceError=f.message.orEmpty()}}}) {Text(if(section=="offers") "${source.optString("title")} · ${financeMoney(source.opt("default_price"))}" else "${nativeDisplayDate(source.optString("work_date"))} · ${source.optString("activity")}")} }
                        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(enabled=sourceOffset>0,onClick={sourceOffset=(sourceOffset-40).coerceAtLeast(0)}) {Text("Vorherige Quellen")}
                            TextButton(enabled=sourceRows.size==40,onClick={sourceOffset+=40}) {Text("Weitere Quellen")}
                        }
                    }
                        }
                        }
                    if(section=="offers") {
                        OutlinedButton(enabled=form.optString("customer_id").isNotEmpty(),onClick={scope.launch {try {offerContext=controller.query("get_offer_context",JSONObject().put("customer_id",form.getString("customer_id")))}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}},modifier=Modifier.fillMaxWidth()) {Text("Kundenkontext und bestätigte Preise lesen")}
                        offerContext?.let {context ->
                            FinanceValues(context.optJSONObject("customer"),listOf("name" to "Einsatzkunde","street" to "Straße","postal_code" to "Postleitzahl","city" to "Ort","notes" to "Hinweise","garden_area" to "Gartenfläche"))
                            val orders=context.optJSONArray("open_orders") ?: JSONArray()
                            for(i in 0 until orders.length()) FinanceValues(orders.optJSONObject(i),listOf("title" to "Offener Auftrag","location" to "Einsatzort"))
                        }
                    }
                    NativeField("Kopftext",form.optString("header_text"),{field("header_text",it)},multiline=true)
                    NativeField("Fußtext",form.optString("footer_text"),{field("footer_text",it)},multiline=true)
                    NativeField("Notizen",form.optString("notes"),{field("notes",it)},multiline=true)
                    FinanceCheck("Synthetischer Testdatensatz (nur Testkunden)",form.optBoolean("test_record")) {field("test_record",it)}
                } else {
                    for((name,label) in listOf("title" to "Bezeichnung","category" to "Kategorie","description" to "Beschreibung","unit" to "Einheit","default_quantity" to "Standardmenge (optional)","default_price" to "Einzelpreis (offen lassen, wenn unbekannt)","assumptions" to "Annahmen","exclusions" to "Ausschlüsse","legal_notes" to "Rechtliche Hinweise")) NativeField(label,if(form.isNull(name)) "" else form.optString(name),{field(name,it)},multiline=name in listOf("description","assumptions","exclusions","legal_notes"))
                    FinanceChoice("Positionsart",form.optString("pricing_type","unit"),listOf("unit" to "Einheitspreis","text" to "Text")) {field("pricing_type",it)}
                    FinanceCheck("Preis ausdrücklich bestätigt",form.optBoolean("price_confirmed")) {field("price_confirmed",it)}
                    FinanceCheck("Baustein aktiv",form.optBoolean("active",true)) {field("active",it)}
                }
            } else if(section!="catalog") FinancePositionSummary(form.optJSONArray("positions"))
        }
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        if(section=="offers" && id.isEmpty() && pending==null) OutlinedButton(enabled=!busy && loaded,onClick={save(true)},modifier=Modifier.fillMaxWidth()) {Text("Entwurf erstellen und atomar prüfen")}
        TextButton(enabled=!busy,onClick=onClose) {Text("Später weiterbearbeiten")}
    }
    NativeActions {
        OutlinedButton(enabled=!busy && pending==null,onClick={scope.launch {controller.removeDraft(key);onClose()}}) {Text("Verwerfen")}
        Button(enabled=!busy && loaded,onClick={save()}) {Text(if(busy) "Speichern …" else if(pending!=null) "Denselben Speichervorgang erneut prüfen" else "Entwurf speichern")}
    }
    }
}

@Composable
private fun FinancePositions(values:JSONArray,offer:Boolean,onChange:(JSONArray)->Unit) {
    var removed by remember(offer) {mutableStateOf<Pair<Int,JSONObject>?>(null)}
    fun update(index:Int,key:String,value:Any?) { val next=JSONArray(values.toString());val line=next.getJSONObject(index);line.put(key,value ?: JSONObject.NULL);for(name in listOf("net","tax","net_total","tax_total","gross_total")) line.remove(name);onChange(next) }
    Text("Positionen",style=MaterialTheme.typography.titleLarge)
    for(index in 0 until values.length()) {val row=values.getJSONObject(index)
        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Position ${index+1}",style=MaterialTheme.typography.titleMedium)
            NativeField("Bezeichnung",row.optString("title"),{update(index,"title",it)},required=true)
            NativeField("Beschreibung",row.optString("description"),{update(index,"description",it)},multiline=true)
            for((key,label) in listOf("quantity" to "Menge","unit" to "Einheit","price" to "Einzelpreis in EUR","tax_rate" to "Steuersatz in %")) NativeField(label,if(row.isNull(key)) "" else row.optString(key),{ value ->
                val next=JSONArray(values.toString());val line=next.getJSONObject(index);line.put(key,value)
                for(name in listOf("net","tax","net_total","tax_total","gross_total")) line.remove(name)
                if(key=="quantity" && line.has("source_quantity_decimal")) line.put("source_quantity_decimal",value.replace(',','.'))
                onChange(next)
            },required=key!="tax_rate")
            if(offer) {
                FinanceChoice("Preisgrundlage",row.optString("price_basis","confirmed"),listOf("confirmed" to "Bestätigt","catalog" to "Freigegebener Katalogpreis","calculated" to "Berechnet","estimated" to "Geschätzt")) {chosen ->
                    val next=JSONArray(values.toString());val line=next.getJSONObject(index);line.put("price_basis",chosen)
                    if(chosen!="catalog") {line.remove("catalog_entry_id");line.remove("catalog_hash")}
                    onChange(next)
                }
                if(row.optString("catalog_entry_id").isEmpty()) FinanceCheck("Diesen Einzelpreis ausdrücklich bestätigt",row.optString("price_source")=="user_confirmed") {checked ->
                    val next=JSONArray(values.toString());if(checked) next.getJSONObject(index).put("price_source","user_confirmed") else next.getJSONObject(index).remove("price_source");onChange(next)
                }
                NativeField("Preisberechnung / Schätzgrundlage",row.optString("price_note"),{update(index,"price_note",it)},multiline=true)
                if(row.optString("price_basis")=="calculated") for((name,label) in listOf("base_price" to "Berechnungsbasis","factor" to "Faktor","surcharge" to "Zuschlag")) NativeField(label,row.optJSONObject("calculation")?.optString(name).orEmpty(),{value ->val calculation=financeCopy(row.optJSONObject("calculation") ?: JSONObject()).put(name,value);update(index,"calculation",calculation)})
            } else NativeField("Rabatt in %",row.optString("discount","0"),{update(index,"discount",it)})
            if(row.optString("worklog_id").isNotEmpty() || row.optString("source_type")=="material_movement") Text("Mit ursprünglicher Leistungsquelle verknüpft. Die Restmenge wird beim Speichern und Ausstellen erneut geprüft.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=index>0,onClick={val next=JSONArray();for(i in 0 until values.length()) next.put(values.get(if(i==index-1) index else if(i==index) index-1 else i));onChange(next)}) {Text("Nach oben")}
                TextButton(onClick={removed=index to financeCopy(row);val next=JSONArray();for(i in 0 until values.length()) if(i!=index) next.put(values.get(i));onChange(next)}) {Text("Entfernen")}
            }
        }}
    }
    removed?.let { (originalIndex,line) -> TextButton(onClick={val next=JSONArray();val position=originalIndex.coerceAtMost(values.length());for(index in 0..values.length()) {if(index==position) next.put(financeCopy(line));if(index<values.length()) next.put(values.get(index))};removed=null;onChange(next)}) {Text("Entfernen rückgängig")}}
    OutlinedButton(onClick={onChange(JSONArray(values.toString()).put(JSONObject().put("title","").put("description","").put("quantity",JSONObject.NULL).put("price",JSONObject.NULL).put("unit","").put("tax_rate",JSONObject.NULL)))},modifier=Modifier.fillMaxWidth()) {Text("Position hinzufügen")}
    Text("Mengen und Preise bleiben offen, bis Sie sie ausdrücklich angeben. Summen werden beim Speichern durch dieselben Fachregeln wie bei MCP geprüft.")
}

@Composable
private fun FinanceIssueReview(controller:NativeAppController,id:String,envelope:JSONObject,onClose:()->Unit) {
    val key=controller.route.args.optString("_draft_key").ifEmpty {"finance:issue:$id"}
    var review by remember(id) { mutableStateOf(envelope.optJSONObject("issue_review") ?: JSONObject()) }
    var loaded by remember(id) {mutableStateOf(false)}
    var loadRetry by remember(id) {mutableIntStateOf(0)}
    var compared by remember { mutableStateOf<JSONObject?>(null) }
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var request by rememberSaveable(id) { mutableStateOf(UUID.randomUUID().toString()) }
    var confirmed by rememberSaveable(id) { mutableStateOf(false) }
    var acceptComparison by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf("") }
    var restored by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    fun retain() { controller.saveDraft(key,JSONObject().put("review",review).put("args",pending ?: JSONObject()).put("request_id",request).put("rejected_code",rejected)) }
    LaunchedEffect(id,loadRetry) {try {controller.draft(key)?.let { saved ->
        saved.optJSONObject("review")?.let {review=it}
        pending=saved.optJSONObject("args")?.takeIf {it.has("request_id")}
        request=saved.optString("request_id",request);rejected=saved.optString("rejected_code");confirmed=pending!=null
        restored=saved.optBoolean("_restore_action_review_required")
    };loaded=true}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty();loaded=false}}
    suspend fun compare() {
        val latest=controller.call("get_invoice",JSONObject().put("invoice_id",id))
        val invoice=latest.optJSONObject("data") ?: throw IllegalArgumentException("Der gespeicherte Beleg fehlt.")
        if(IssuedInvoiceRules.isIssued(invoice)) {controller.removeDraft(key);controller.message("Bereits ausgestellten Beleg gelesen. Keine zweite Nummer vergeben.");onClose();return}
        compared=latest.optJSONObject("issue_review") ?: throw IllegalArgumentException("Die vollständige Ausgabeprüfung fehlt.")
        acceptComparison=false
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Rechnung lokal verbindlich ausstellen",style=MaterialTheme.typography.headlineSmall)
        if(!loaded) {if(error.isEmpty()) CircularProgressIndicator() else {Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={error="";loadRetry++}) {Text("Ausgabeentwurf erneut lesen")}};TextButton(onClick=onClose) {Text("Zurück")};return@Column}
        Text("Prüfen Sie den gemeinsam gelesenen Aussteller, Empfänger, Beleg und alle Leistungsquellen. Eine Nummer wird erst mit erfolgreicher Ausgabe atomar vergeben. PDF-Erstellung und Teilen folgen getrennt.")
        FinanceReviewSummary(controller,review)
        val ready=review.optBoolean("ready") && review.optJSONArray("blockers")?.length()==0
        if(pending==null) FinanceCheck("Ich bestätige genau die oben gezeigten Aussteller-, Empfänger-, Beleg- und Quellenangaben einschließlich der Mengen.",confirmed) {confirmed=it}
        if(pending!=null) Text(if(rejected.isEmpty()) "Die ursprüngliche Aktion und der bestätigte Prüfstand bleiben für dieselbe Wiederholung erhalten." else "Die ursprüngliche Ausgabe wurde abgewiesen. Aktuellen Prüfstand vergleichen und bewusst neu übernehmen.")
        if(restored) Text("Diese frühere Ausgabeprüfung stammt aus einer Wiederherstellung. Zuerst den ursprünglichen Aktionsbeleg lesen. Eine unbestätigte Prüfung vor einer neuen Ausgabe ausdrücklich mit dem aktuellen Stand vergleichen.")
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        OutlinedButton(enabled=!busy,onClick={scope.launch {try{compare()}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}},modifier=Modifier.fillMaxWidth()) {Text("Gespeicherten Prüfstand vergleichen")}
        compared?.let { next ->
            HorizontalDivider();Text(if(next.optString("context_hash")==review.optString("context_hash")) "Neu gelesen: Prüfstand unverändert" else "Neu gelesen: Angaben wurden geändert",style=MaterialTheme.typography.titleMedium)
            FinanceReviewSummary(controller,next)
            if(pending==null || rejected.isNotEmpty()) {
                FinanceCheck("Ich habe den bisherigen und den neu gelesenen Prüfstand einschließlich aller Änderungen verglichen.",acceptComparison) {acceptComparison=it}
                Button(enabled=acceptComparison && !busy,onClick={scope.launch {try {review=financeCopy(next);request=UUID.randomUUID().toString();controller.adoptDraft(key,JSONObject().put("review",review).put("request_id",request));compared=null;pending=null;rejected="";restored=false;confirmed=false;error="Neuen Prüfstand übernommen. Erneut ausdrücklich bestätigen; es wurde keine Rechnung ausgestellt."}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}},modifier=Modifier.fillMaxWidth()) {Text("Geprüften Stand neu übernehmen")}
            } else Text("Die ursprüngliche Ausgabe zuerst eindeutig prüfen. Bis dahin bleibt dieselbe Aktionskennung erhalten.")
        }
        Button(enabled=!busy && ((ready && confirmed && rejected.isEmpty() && !restored) || (restored && pending!=null)),onClick={scope.launch {
            busy=true;error=""
            var attemptedWrite=false
            try {
                pending?.let { original ->
                    if(financeInspect(controller,"request_invoice_issue",original)!=null) {controller.removeDraft(key);controller.message("Ursprüngliche Ausgabe bestätigt. Keine zweite Rechnung erstellt.");onClose();return@launch}
                }
                if(restored) {compare();throw IllegalStateException("Kein eindeutiger Beleg für die ursprüngliche Ausgabe gefunden. Die alte Anfrage bleibt erhalten; sie wird nicht erneut ausgeführt.")}
                if(pending==null) pending=JSONObject().put("invoice_id",id).put("request_id",request).put("expected_record_hash",review.getString("record_hash")).put("expected_context_hash",review.getString("context_hash")).put("confirmed",true)
                retain();controller.flushDrafts()
                attemptedWrite=true
                val result=financeData(controller.call("request_invoice_issue",pending!!))
                if(result.optString("finalization")!="locally_issued" || result.optString("invoice_id")!=id || result.optString("invoice_number").isEmpty()) throw IllegalStateException("Ausgabe noch nicht eindeutig bestätigt. Dieselbe Aktion erneut prüfen.")
                controller.removeDraft(key);controller.message("Rechnung ${result.getString("invoice_number")} lokal ausgestellt. PDF-Status: ${result.optString("pdf_state")}. Kein Versand bestätigt.");onClose()
            } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception) {
                val code=(f as? NativeCallFailure)?.detail?.optString("code").orEmpty()
                if(attemptedWrite && code in setOf("WRITE_CONFLICT","INVOICE_REVIEW_CONTEXT_REQUIRED","INVALID_INPUT","INVOICE_SOURCE_QUANTITY_EXCEEDED","INVOICE_SOURCE_ALREADY_BILLED","INVOICE_SOURCE_UNIT_REVIEW_REQUIRED")) {rejected=code;retain()}
                else runCatching {compare()}
                error=f.message.orEmpty()+" Ursprünglicher Prüfstand und Anfrage bleiben erhalten."
            } finally {busy=false}
        }},modifier=Modifier.fillMaxWidth()) {Text(if(busy) "Ausgabe wird geprüft …" else if(restored && pending!=null) "Ursprünglichen Ausgabebeleg lesen" else if(pending!=null) "Dieselbe Ausgabe erneut prüfen" else "Rechnung verbindlich ausstellen")}
        TextButton(enabled=!busy,onClick=onClose) {Text("Später prüfen")}
    }
}

@Composable
private fun FinanceReviewSummary(controller:NativeAppController,review:JSONObject) {
    val snapshot=review.optJSONObject("snapshot") ?: JSONObject()
    val invoice=snapshot.optJSONObject("invoice")
    FinanceIssuerSummary(snapshot.optJSONObject("issuer"))
    Text("Rechnungsempfänger",style=MaterialTheme.typography.titleMedium)
    FinanceValues(snapshot.optJSONObject("recipient"),financePartyFields)
    invoice?.let {FinanceDraftSummary(controller,it,"invoices");Text("Netto ${financeMoney(it.opt("net_total"))} · Steuer ${financeMoney(it.opt("tax_total"))} · Gesamt ${financeMoney(it.opt("gross_total"))}")}
    for((collection,label) in listOf("worklogs" to "Arbeitsquellen","materials" to "Materialquellen","orders" to "Zugeordnete Aufträge")) {
        val rows=snapshot.optJSONArray(collection) ?: continue
        if(rows.length()>0) Text(label,style=MaterialTheme.typography.titleMedium)
        for(index in 0 until rows.length()) {
            val source=rows.optJSONObject(index) ?: continue
            val record=source.optJSONObject("record")
            FinanceValues(record,listOf("title" to "Bezeichnung","activity" to "Tätigkeit","description" to "Beschreibung","work_date" to "Arbeitsdatum","start_time" to "Beginn","end_time" to "Ende","break_minutes" to "Pause in Minuten","hours" to "Stunden","quantity_decimal" to "Ursprungsmenge","unit" to "Einheit","location" to "Einsatzort","notes" to "Notizen","material" to "Material","material_cost" to "Materialkosten","kilometers" to "Kilometer"))
            FinanceValues(source.optJSONObject("billing_customer_context"),listOf("name" to "Abrechnungskontext","billing_mode" to "Abrechnungsweg"))
            if(collection!="orders") {
                val quantity=source.optJSONObject("billing_quantity")
                Text("Angefragt: ${source.optString("requested_quantity_decimal")} · Gesamt: ${quantity?.optString("total_decimal") ?: source.optString("total_decimal")} · bereits zugeordnet: ${quantity?.optString("allocated_decimal") ?: source.optString("allocated_decimal")} · Rest: ${quantity?.optString("remaining_decimal") ?: source.optString("remaining_decimal")}")
                val allocations=source.optJSONObject("allocations_by_invoice") ?: JSONObject()
                for(invoiceId in allocations.keys()) {FinanceInvoiceLabel(controller,invoiceId);Text("Bereits zugeordnete Menge: ${allocations.optString(invoiceId)}")}
            }
        }
    }
    snapshot.optJSONObject("correction_original")?.let {original ->Text("Unverändertes Korrekturoriginal",style=MaterialTheme.typography.titleMedium);FinanceDraftSummary(controller,original,"invoices")}
    val blockers=review.optJSONArray("blockers") ?: JSONArray()
    if(!review.optBoolean("ready")) Text("Ausgabe noch gesperrt",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.titleMedium)
    for(index in 0 until blockers.length()) Text(blockers.optJSONObject(index)?.optString("message") ?: "Prüfung erforderlich",color=MaterialTheme.colorScheme.error)
}

@Composable
private fun FinanceInvoiceDocument(controller:NativeAppController,id:String) {
    var data by remember(id) {mutableStateOf(JSONObject())}
    var tick by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(id,tick) {try{data=controller.query("get_invoice_document_job",JSONObject().put("invoice_id",id));error=""}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}
    val state=data.optString("pdf_state")
    Text("Original-PDF: ${when(state){"READY"->"Geprüftes Original vorhanden";"PDF_PENDING"->"Lokale Erstellung ausstehend";"ORIGINAL_REVIEW_REQUIRED"->"Originalnachweis muss geprüft werden";else->state.ifEmpty{"Noch nicht gelesen"}}}")
    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
    TextButton(onClick={tick++}) {Text("PDF-Status lesen")}
    if(state=="READY" && data.optBoolean("original_verified")) {
        OutlinedButton(onClick={scope.launch {try{controller.viewDocument("invoice",id)}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}},modifier=Modifier.fillMaxWidth()) {Text("Original-PDF öffnen")}
        for((channel,label) in listOf("share" to "Original-PDF teilen","email" to "Original-PDF per E-Mail übergeben","whatsapp" to "Original-PDF an WhatsApp übergeben")) OutlinedButton(onClick={scope.launch {try{controller.call("prepare_invoice_share",JSONObject().put("invoice_id",id).put("request_id",UUID.randomUUID().toString()).put("channel",channel));controller.shareDocument("invoice",id,channel)}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}},modifier=Modifier.fillMaxWidth()) {Text(label)}
    } else if(state=="PDF_PENDING") OutlinedButton(onClick={controller.action("Lokale PDF-Erstellung erneut prüfen","retry_invoice_document_job",JSONObject().put("invoice_id",id).put("expected_record_hash",data.optString("record_hash")))},modifier=Modifier.fillMaxWidth()) {Text("PDF-Erstellung erneut anfordern")}
    Text("Teilen oder die Übergabe an eine App bestätigt weder Versand noch Zustellung.")
}

@Composable
private fun FinancePayments(controller:NativeAppController,invoiceId:String) {
    var ledger by remember(invoiceId) {mutableStateOf(JSONObject())}
    var offset by rememberSaveable(invoiceId) {mutableIntStateOf(0)}
    var refresh by remember {mutableIntStateOf(0)}
    var editing by rememberSaveable(invoiceId,controller.route.mode,controller.route.args.optString("_draft_key")) {mutableStateOf(controller.route.mode in listOf("edit","new"))}
    var correction by remember(invoiceId,controller.route.args.optString("payment_id")) {mutableStateOf(controller.route.args.optJSONObject("_correction") ?: controller.route.args.optString("payment_id").takeIf {it.isNotEmpty()}?.let {JSONObject().put("payment_id",it)})}
    var error by remember {mutableStateOf("")}
    LaunchedEffect(invoiceId,offset,refresh) {try{val args=JSONObject().put("offset",offset).put("limit",50);if(invoiceId.isNotEmpty()) args.put("invoice_id",invoiceId);ledger=controller.query("get_payment_ledger",args);error=""}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}
    if(editing) {
        if(ledger.optString("ledger_hash").isEmpty()) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(error.isEmpty()) CircularProgressIndicator() else Text(error,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={refresh++}) {Text("Zahlungsstand erneut lesen")}
            TextButton(onClick={controller.back()}) {Text("Zurück")}
        } else FinancePaymentEditor(controller,invoiceId,ledger,correction,onClose={editing=false;correction=null;if(controller.route.args.optString("_draft_key").isNotEmpty()) controller.navigate("payments",invoiceId) else refresh++})
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Zahlungen und belegte Reststände",style=MaterialTheme.typography.headlineSmall)
        Text("Zahlungen, Rechnungsstatus und persönliche b.b.-Markierungen bleiben getrennt. Eine Erfassung ist keine Bankbuchung.")
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        val balances=ledger.optJSONArray("balances") ?: JSONArray()
        for(i in 0 until balances.length()) {val balance=balances.getJSONObject(i)
            Card(Modifier.fillMaxWidth().clickable {controller.navigate("invoices",balance.getString("invoice_id"))}) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FinanceInvoiceLabel(controller,balance.optString("invoice_id"),balance.optString("invoice_number"))
                Text(financeStatus(balance.optString("legacy_status")))
                if(!balance.optBoolean("balance_known")) Text("Reststand noch nicht vollständig belegt. Historischen Bestand prüfen.") else {
                    Text("Forderung ${financeMoney(BigDecimal(balance.optLong("invoice_minor")).movePointLeft(2))}")
                    Text("Rest ${financeMoney(if(balance.isNull("remaining_minor")) null else BigDecimal(balance.optLong("remaining_minor")).movePointLeft(2))} · Guthaben ${financeMoney(BigDecimal(balance.optLong("credit_minor")).movePointLeft(2))}")
                }
            }}
        }
        val payments=ledger.optJSONArray("payments") ?: JSONArray()
        Text("${ledger.optInt("total",payments.length())} Zahlungen / Korrekturen")
        for(i in 0 until payments.length()) {val payment=payments.getJSONObject(i)
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${nativeDisplayDate(payment.optString("paid_on",payment.optString("recorded_at")))} · ${financeMoney(BigDecimal(payment.optLong("amount_minor")).movePointLeft(2))}",style=MaterialTheme.typography.titleMedium)
                Text("${financeStatus(payment.optString("method"))} ${payment.optString("reference")}")
                if(payment.optBoolean("reversal")) Text("Gegenbuchung; Original bleibt erhalten.")
                if(payment.optString("reason").isNotEmpty()) Text("Korrekturgrund: ${payment.optString("reason")}")
                val allocations=payment.optJSONArray("allocations") ?: JSONArray()
                for(j in 0 until allocations.length()) {val allocation=allocations.getJSONObject(j);FinanceInvoiceLabel(controller,allocation.optString("invoice_id"),allocation.optString("invoice_number"));Text("Zugeordnet: ${financeMoney(BigDecimal(allocation.optLong("amount_minor")).movePointLeft(2))}")}
                Text("Unzugeordnet: ${financeMoney(BigDecimal(payment.optLong("unallocated_minor")).movePointLeft(2))}")
                if(!payment.optBoolean("reversal") && payment.optLong("amount_minor")>0) OutlinedButton(onClick={correction=financeCopy(payment);editing=true}) {Text("Korrektur mit Grund erfassen")}
            }}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(enabled=offset>0,onClick={offset=(offset-50).coerceAtLeast(0)}) {Text("Zurück")}
            TextButton(enabled=offset+payments.length()<ledger.optInt("total",0),onClick={offset+=50}) {Text("Weitere")}
        }
        Button(enabled=ledger.optString("ledger_hash").isNotEmpty(),onClick={correction=null;editing=true},modifier=Modifier.fillMaxWidth()) {Text("Zahlung erfassen")}
        TextButton(onClick={refresh++}) {Text("Gespeicherten Zahlungsstand lesen")}
    }
}

@Composable
private fun FinancePaymentEditor(controller:NativeAppController,invoiceId:String,ledger:JSONObject,correction:JSONObject?,onClose:()->Unit) {
    val key=controller.route.args.optString("_draft_key").ifEmpty {"finance:payment:${correction?.optString("payment_id") ?: "new:$invoiceId"}"}
    var form by remember(key) {mutableStateOf(JSONObject().put("amount",correction?.takeIf {it.has("amount_minor")}?.let {BigDecimal(it.getLong("amount_minor")).movePointLeft(2).toPlainString()} ?: "").put("paid_on",correction?.optString("paid_on") ?: financeToday()).put("method",correction?.optString("method") ?: "").put("reference",correction?.optString("reference") ?: "").put("allocations",JSONArray()))}
    var loaded by remember(key) {mutableStateOf(false)}
    var loadRetry by remember(key) {mutableIntStateOf(0)}
    var ledgerHash by rememberSaveable(key) {mutableStateOf(ledger.optString("ledger_hash"))}
    var request by rememberSaveable(key) {mutableStateOf(UUID.randomUUID().toString())}
    var pending by remember {mutableStateOf<JSONObject?>(null)}
    var rejected by remember {mutableStateOf(false)}
    var restored by remember {mutableStateOf(false)}
    var compared by remember {mutableStateOf<JSONObject?>(null)}
    var confirmed by rememberSaveable(key) {mutableStateOf(false)}
    var adoptReviewed by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun retain(next:JSONObject) {if(pending!=null)return;form=next;controller.saveDraft(key,JSONObject().put("form",form).put("ledger_hash",ledgerHash).put("request_id",request))}
    fun field(name:String,value:Any) {retain(financeCopy(form).put(name,value))}
    LaunchedEffect(key,loadRetry) {try {
        val saved=controller.draft(key)
        if(saved!=null) {form=saved.optJSONObject("form") ?: form;ledgerHash=saved.optString("ledger_hash",ledgerHash);request=saved.optString("request_id",request);pending=saved.optJSONObject("args")?.takeIf {it.has("request_id")};rejected=saved.optBoolean("rejected");confirmed=pending!=null;restored=saved.optBoolean("_restore_action_review_required")}
        else {
            val allocations=JSONArray()
            if(correction!=null) {val old=correction.optJSONArray("allocations") ?: JSONArray();for(i in 0 until old.length()) {val value=old.getJSONObject(i);allocations.put(JSONObject().put("invoice_id",value.getString("invoice_id")).put("amount",BigDecimal(value.getLong("amount_minor")).movePointLeft(2).toPlainString()))}}
            else if(invoiceId.isNotEmpty()) allocations.put(JSONObject().put("invoice_id",invoiceId).put("amount",""))
            for(i in 0 until allocations.length()) {val allocation=allocations.getJSONObject(i);val balances=ledger.optJSONArray("balances") ?: JSONArray();for(j in 0 until balances.length()) {val balance=balances.getJSONObject(j);if(balance.optString("invoice_id")==allocation.optString("invoice_id")) allocation.put("expected_record_hash",balance.optString("record_hash")).put("remaining_minor",balance.opt("remaining_minor") ?: JSONObject.NULL).put("invoice_number",balance.optString("invoice_number"))}
                if(allocation.optString("expected_record_hash").isEmpty()) try {val current=controller.query("get_payment_ledger",JSONObject().put("invoice_id",allocation.getString("invoice_id"))).optJSONArray("balances")?.optJSONObject(0) ?: throw IllegalArgumentException("Die zugeordnete Rechnung ist nicht als ausgestellter Beleg lesbar.");allocation.put("expected_record_hash",current.getString("record_hash")).put("remaining_minor",current.opt("remaining_minor") ?: JSONObject.NULL).put("invoice_number",current.optString("invoice_number"))}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}
            }
            form=financeCopy(form).put("allocations",allocations)
        }
        loaded=true
    }catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty();loaded=false}}
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(correction==null) "Zahlung erfassen" else "Zahlungskorrektur mit Gegenbuchung",style=MaterialTheme.typography.headlineSmall)
        if(!loaded) {if(error.isEmpty()) CircularProgressIndicator() else {Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={error="";loadRetry++}) {Text("Zahlungsentwurf erneut lesen")}};TextButton(onClick=onClose) {Text("Zurück")};return@Column}
        if(correction!=null) Text("Die ursprüngliche Zahlung ${if(correction.has("amount_minor")) financeMoney(BigDecimal(correction.optLong("amount_minor")).movePointLeft(2)) else ""} bleibt unverändert. Eine Gegenbuchung und die bestätigte Ersatzquittung werden gemeinsam protokolliert. Betrag 0 mit leerer Zuordnung nimmt die Zahlung vollständig zurück.")
        if(pending==null) {
            NativeField("Tatsächlich gezahlter Betrag in EUR",form.optString("amount"),{field("amount",it)},required=true)
            NativeField("Zahlungsdatum",form.optString("paid_on"),{field("paid_on",it)},required=true)
            FinanceChoice("Zahlungsart",form.optString("method"),listOf("cash" to "Bar","bank" to "Bank","card" to "Karte","other" to "Andere")) {field("method",it)}
            NativeField("Zahlungsreferenz",form.optString("reference"),{field("reference",it)})
            if(correction!=null) NativeField("Grund der Korrektur",form.optString("reason"),{field("reason",it)},required=true,multiline=true)
            Text("Rechnungszuordnungen",style=MaterialTheme.typography.titleMedium)
            val allocations=form.optJSONArray("allocations") ?: JSONArray()
            for(i in 0 until allocations.length()) {val value=allocations.getJSONObject(i)
                NativeRecordPicker(controller,"invoices","Rechnung",value.optString("invoice_id")) {chosen ->scope.launch {try {
                    if(chosen.isEmpty()) {val next=JSONArray(allocations.toString());next.getJSONObject(i).remove("expected_record_hash");next.getJSONObject(i).put("invoice_id","").remove("invoice_number");field("allocations",next);return@launch}
                    val balance=controller.query("get_payment_ledger",JSONObject().put("invoice_id",chosen)).optJSONArray("balances")?.optJSONObject(0) ?: throw IllegalArgumentException("Nur ausgestellte Rechnungen können einer Zahlung zugeordnet werden.")
                    val next=JSONArray(allocations.toString());next.getJSONObject(i).put("invoice_id",chosen).put("invoice_number",balance.optString("invoice_number")).put("expected_record_hash",balance.getString("record_hash")).put("remaining_minor",balance.opt("remaining_minor") ?: JSONObject.NULL);field("allocations",next)
                } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()} }}
                if(value.optString("invoice_id").isNotEmpty()) FinanceInvoiceLabel(controller,value.getString("invoice_id"),value.optString("invoice_number"))
                Text("Gelesener Rest ${financeMoney(if(!value.has("remaining_minor") || value.isNull("remaining_minor")) null else BigDecimal(value.optLong("remaining_minor")).movePointLeft(2))}")
                NativeField("Zugeordneter Teilbetrag in EUR",value.optString("amount"),{amount ->val next=JSONArray(allocations.toString());next.getJSONObject(i).put("amount",amount);field("allocations",next)},required=true)
                TextButton(onClick={val next=JSONArray();for(j in 0 until allocations.length()) if(j!=i)next.put(allocations.get(j));field("allocations",next)}) {Text("Zuordnung entfernen")}
            }
            OutlinedButton(onClick={field("allocations",JSONArray(allocations.toString()).put(JSONObject().put("invoice_id","").put("amount","")))},modifier=Modifier.fillMaxWidth()) {Text("Weitere Rechnung zuordnen")}
            FinanceCheck("Betrag, Zahlungsdatum, Art und alle Rechnungszuordnungen ausdrücklich geprüft",confirmed) {confirmed=it}
        } else Text("Der ursprünglich bestätigte Zahlungsauftrag bleibt unverändert. Zuerst nachlesen und denselben Vorgang erneut prüfen.")
        if(restored) Text("Dieser Zahlungsentwurf stammt aus einer Wiederherstellung. Den ursprünglichen Speicherbeleg zuerst lesen; eine neue Zahlung erfordert die bewusste Übernahme des aktuellen Zahlungsstands.")
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        OutlinedButton(enabled=!busy,onClick={scope.launch {try{compared=controller.query("get_payment_ledger",JSONObject().put("limit",100));adoptReviewed=false;controller.message("Aktuellen Zahlungsstand gelesen. Der ursprüngliche Auftrag bleibt erhalten.")}catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}},modifier=Modifier.fillMaxWidth()) {Text("Gespeicherten Zahlungsstand vergleichen")}
        compared?.let {read ->
            Text(if(read.optString("ledger_hash")==ledgerHash) "Zahlungsstand unverändert." else "Der gespeicherte Zahlungsstand wurde geändert.")
            val balances=read.optJSONArray("balances") ?: JSONArray()
            for(i in 0 until balances.length()) {val b=balances.getJSONObject(i);FinanceInvoiceLabel(controller,b.optString("invoice_id"),b.optString("invoice_number"));Text(financeStatus(b.optString("legacy_status")));Text("Rest ${financeMoney(if(b.isNull("remaining_minor")) null else BigDecimal(b.optLong("remaining_minor")).movePointLeft(2))}")}
            if(pending==null || (rejected && !restored)) {
                FinanceCheck("Ich habe die Zahlung, ihre Zuordnungen und den neu gelesenen Zahlungsstand verglichen.",adoptReviewed) {adoptReviewed=it}
                TextButton(enabled=adoptReviewed && !busy,onClick={scope.launch {try {
                    ledgerHash=read.getString("ledger_hash")
                    val allocations=JSONArray((form.optJSONArray("allocations") ?: JSONArray()).toString())
                    for(i in 0 until allocations.length()) {
                        val a=allocations.getJSONObject(i)
                        val balance=(0 until balances.length()).map {balances.getJSONObject(it)}.firstOrNull {it.optString("invoice_id")==a.optString("invoice_id")} ?: throw IllegalArgumentException("Eine zugeordnete Rechnung fehlt im gelesenen Zahlungsstand. Die Auswahl bewusst neu prüfen.")
                        a.put("expected_record_hash",balance.getString("record_hash")).put("remaining_minor",balance.opt("remaining_minor") ?: JSONObject.NULL).put("invoice_number",balance.optString("invoice_number"))
                    }
                    form=financeCopy(form).put("allocations",allocations);request=UUID.randomUUID().toString()
                    controller.adoptDraft(key,JSONObject().put("form",form).put("ledger_hash",ledgerHash).put("request_id",request))
                    pending=null;rejected=false;restored=false;compared=null;confirmed=false;adoptReviewed=false
                }catch(cancelled:CancellationException){throw cancelled}catch(f:Exception){error=f.message.orEmpty()}}}) {Text("Diesen gelesenen Stand für erneute Prüfung übernehmen")}
            }
        }
        Button(enabled=!busy && ((confirmed && !rejected && !restored) || (restored && pending!=null)),onClick={scope.launch {
            busy=true;error=""
            var attemptedWrite=false
            try {
                pending?.let { original ->
                    if(financeInspect(controller,if(correction==null) "record_payment" else "correct_payment",original)!=null) {controller.removeDraft(key);controller.message("Ursprüngliche Zahlung bestätigt. Keine zweite Buchung erzeugt.");onClose();return@launch}
                }
                if(restored) throw IllegalStateException("Kein eindeutiger Beleg für die alte Zahlung gefunden. Der ursprüngliche Auftrag bleibt erhalten und wird nicht erneut ausgeführt.")
                val args=pending ?: run {
                    val allocations=JSONArray();val values=form.optJSONArray("allocations") ?: JSONArray()
                    for(i in 0 until values.length()) {val value=values.getJSONObject(i);val invoice=value.getString("invoice_id");if(value.optString("expected_record_hash").isEmpty()) throw IllegalArgumentException("Die gewählte Rechnung zuerst aktuell lesen.");allocations.put(JSONObject().put("invoice_id",invoice).put("amount_minor",financeMinor(value.getString("amount"))).put("expected_record_hash",value.getString("expected_record_hash")))}
                    JSONObject().put("request_id",request).put("expected_ledger_hash",ledgerHash).put("amount_minor",financeMinor(form.getString("amount"))).put("paid_on",form.getString("paid_on")).put("method",form.getString("method")).put("reference",form.optString("reference")).put("allocations",allocations).also {if(correction!=null) it.put("payment_id",correction.getString("payment_id")).put("reason",form.getString("reason"));it.put("confirmed",true)}
                }
                pending=financeCopy(args);controller.saveDraft(key,JSONObject().put("form",form).put("ledger_hash",ledgerHash).put("request_id",request).put("args",args));controller.flushDrafts()
                attemptedWrite=true
                controller.call(if(correction==null) "record_payment" else "correct_payment",args)
                controller.removeDraft(key);controller.message(if(correction==null) "Zahlung mit Zuordnungen gespeichert." else "Gegenbuchung und Ersatzquittung gespeichert. Originalzahlung erhalten.");onClose()
            } catch(cancelled:CancellationException){throw cancelled}catch(f:Exception) {
                val code=(f as? NativeCallFailure)?.detail?.optString("code").orEmpty()
                if(attemptedWrite && (f as? NativeCallFailure)?.detail?.optString("commit_state")=="not_committed") {pending=null;rejected=false;confirmed=false;request=UUID.randomUUID().toString();controller.saveDraft(key,JSONObject().put("form",form).put("ledger_hash",ledgerHash).put("request_id",request))}
                else if(attemptedWrite && code in setOf("WRITE_CONFLICT","PAYMENT_LEDGER_WRITE_CONFLICT","INVALID_INPUT","PAYMENT_POSITIVE_AMOUNT_REQUIRED","PAYMENT_ZERO_CORRECTION_REQUIRES_EMPTY_ALLOCATIONS","PAYMENT_CORRECTION_REASON_REQUIRED","PAYMENT_DATE_IN_FUTURE","PAYMENT_INVOICE_INVALID","PAYMENT_ALLOCATION_EXCEEDS_AMOUNT")) {rejected=true;controller.saveDraft(key,JSONObject().put("form",form).put("ledger_hash",ledgerHash).put("request_id",request).put("args",pending ?: JSONObject()).put("rejected",true))}
                error=f.message.orEmpty()
            } finally {busy=false}
        }},modifier=Modifier.fillMaxWidth()) {Text(if(busy) "Zahlung wird geprüft …" else if(restored && pending!=null) "Ursprünglichen Zahlungsbeleg lesen" else if(pending!=null) "Dieselbe Zahlung erneut prüfen" else if(correction==null) "Bestätigte Zahlung speichern" else "Bestätigte Korrektur protokollieren")}
        TextButton(enabled=!busy && pending==null,onClick={scope.launch {controller.removeDraft(key);onClose()}}) {Text("Verwerfen")}
        TextButton(enabled=!busy,onClick=onClose) {Text("Später prüfen")}
    }
}
