package at.hausgarten.service

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun financeToday() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()

private fun financeCopy(value: JSONObject) = JSONObject(value.toString())

private fun financeData(envelope: JSONObject): JSONObject =
    when (val data = envelope.opt("data")) {
        is JSONObject -> data
        is JSONArray ->
            JSONObject().put("items", data).also {
                if (envelope.has("total")) it.put("total", envelope.get("total"))
            }
        else -> envelope
    }

private fun financeRows(data: JSONObject, vararg names: String): List<JSONObject> {
    val array =
        (names.toList() + "items").firstNotNullOfOrNull { data.optJSONArray(it) }
            ?: return emptyList()
    return (0 until array.length())
        .mapNotNull { array.optJSONObject(it) }
        .map { it.optJSONObject("offer") ?: it.optJSONObject("entry") ?: it }
}

internal fun financeMoney(value: Any?): String =
    if (value == null || value == JSONObject.NULL) "offen"
    else
        runCatching {
                BigDecimal(value.toString())
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
                    .replace('.', ',') + " €"
            }
            .getOrDefault("Betrag prüfen")

private fun financeFields(row: JSONObject, keys: Array<String>) =
    JSONObject().also { out -> keys.forEach { if (row.has(it)) out.put(it, row.get(it)) } }

private fun financeStatus(value: String): String =
    mapOf(
        "draft" to "Entwurf",
        "validated" to "Geprüft",
        "finalized" to "Finalisiert",
        "accepted" to "Angenommen",
        "declined" to "Abgelehnt",
        "share_prepared" to "Teilen vorbereitet",
        "sent" to "Versand bestätigt",
        "cash" to "Bar",
        "bank" to "Bank",
        "card" to "Karte",
        "other" to "Andere",
        "b2b" to "Unternehmen",
        "consumer" to "Verbraucher",
        "on_premise" to "Im Geschäft",
        "distance" to "Fernabsatz",
        "off_premise" to "Außerhalb der Geschäftsräume",
        "confirmed" to "Bestätigt",
        "catalog" to "Freigegebener Katalogpreis",
        "calculated" to "Berechnet",
        "estimated" to "Geschätzt",
        "unit" to "Einheitspreis",
        "text" to "Text",
        "own" to "Eigener Nummernkreis",
    )[value] ?: nativeValue(value)

private val financePartyFields =
    listOf(
        "name" to "Name",
        "company" to "Firma",
        "first_name" to "Vorname",
        "last_name" to "Nachname",
        "street" to "Straße",
        "postal_code" to "Postleitzahl",
        "city" to "Ort",
        "country" to "Land",
        "email" to "E-Mail",
        "phone" to "Telefon",
    )

private suspend fun financeInspect(
    controller: NativeAppController,
    tool: String,
    args: JSONObject,
): JSONObject? {
    val proof =
        controller.query(
            "inspect_edit_action",
            JSONObject().put("original_tool", tool).put("original_args", args),
        )
    if (proof.optString("state") == "content_removed")
        throw IllegalStateException(
            "Dieser Vorgang wurde entfernt. Der ursprüngliche Auftrag bleibt zur Prüfung erhalten."
        )
    return if (proof.optString("state") == "confirmed") proof.optJSONObject("result") ?: proof
    else null
}

@Composable
private fun FinanceValues(row: JSONObject?, fields: List<Pair<String, String>>) {
    row ?: return
    for ((name, label) in fields) if (
        row.has(name) && !row.isNull(name) && row.optString(name).isNotBlank()
    ) {
        val value = row.opt(name)
        Text(
            "$label: ${if (value is Boolean) { if (value) "Ja" else "Nein" } else if (name == "default_price") financeMoney(value) else if (name in listOf("default_quantity", "quantity", "hours")) nativeNumber(value) else nativeDisplayText(financeStatus(value?.toString().orEmpty()))}"
        )
    }
}

@Composable
private fun FinanceIssuerSummary(issuer: JSONObject?) {
    Text("Aussteller und Belegkonfiguration", style = MaterialTheme.typography.titleMedium)
    if (issuer == null) Text("Aussteller fehlt", color = MaterialTheme.colorScheme.error)
    else
        FinanceValues(
            issuer,
            listOf(
                "business_name" to "Betrieb",
                "owner_name" to "Inhaber",
                "business_street" to "Straße",
                "business_postal_code" to "Postleitzahl",
                "business_city" to "Ort",
                "business_legal_info" to "Rechtliche Angaben",
                "business_vat_id" to "UID",
                "tax_mode" to "Steuermodus",
                "tax_exemption_text" to "Steuerbefreiung",
                "business_iban" to "IBAN",
                "business_bank" to "Bank",
                "business_bic" to "BIC",
                "business_email" to "E-Mail",
                "business_phone" to "Telefon",
            ),
        )
}

@Composable
private fun FinancePartySummary(label: String, party: JSONObject?) {
    party ?: return
    fun text(key: String) = if (party.isNull(key)) "" else party.optString(key).trim()
    val person =
        listOf(text("first_name"), text("last_name")).filter { it.isNotBlank() }.joinToString(" ")
    val names = listOf(text("name"), text("company"), person).filter { it.isNotBlank() }.distinct()
    val locality =
        listOf(text("postal_code"), text("city")).filter { it.isNotBlank() }.joinToString(" ")
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            names.forEachIndexed { index, name ->
                Text(
                    name,
                    style =
                        if (index == 0) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (text("street").isNotBlank())
                Text(text("street"), style = MaterialTheme.typography.bodyMedium)
            if (locality.isNotBlank()) Text(locality, style = MaterialTheme.typography.bodyMedium)
            FinanceValues(
                party,
                financePartyFields.filter { it.first in listOf("country", "email", "phone") },
            )
        }
    }
}

/** Display the saved offer total; editing and authoritative price calculation stay unchanged. */
@Composable
private fun FinanceOfferTotal(offer: JSONObject) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (
                    offer.optString("document_type") in
                        listOf("time_material", "estimate_nonbinding")
                )
                    "Geschätzter Gesamtbetrag"
                else "Angebotssumme",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                financeMoney(offer.opt("gross_total") ?: offer.opt("amount")),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FinanceDraftSummary(controller: NativeAppController, row: JSONObject, section: String) {
    FinanceValues(
        row,
        listOf(
            "title" to "Bezeichnung",
            "status" to "Status",
            "document_type" to "Dokumentart",
            "issue_date" to "Angebotsdatum",
            "valid_until" to "Gültig bis",
            "execution_window" to "Ausführung",
            "tax_mode" to "Steuermodus",
            "payment_terms" to "Zahlungsbedingungen",
            "location" to "Einsatzort",
            "header_text" to "Kopftext",
            "footer_text" to "Fußtext",
            "notes" to "Notizen",
            "binding_notice" to "Bindungshinweis",
            "assumptions" to "Annahmen",
            "exclusions" to "Ausschlüsse",
            "description" to "Beschreibung",
            "unit" to "Einheit",
            "default_price" to "Standardpreis",
            "default_quantity" to "Standardmenge",
            "pricing_type" to "Positionsart",
            "price_confirmed" to "Preis bestätigt",
            "category" to "Kategorie",
            "legal_notes" to "Rechtliche Hinweise",
            "customer_type" to "Kundentyp",
            "contract_channel" to "Vertragsweg",
            "consumer_information" to "Verbraucherinformationen",
            "consumer_information_confirmed" to "Verbraucherinformationen bestätigt",
            "scope_confirmed" to "Leistungsumfang bestätigt",
            "binding_confirmed" to "Bindungsart bestätigt",
            "active" to "Aktiv",
        ),
    )
    for ((field, collection, label) in
        listOf(
            Triple("customer_id", "customers", "Kunde"),
            Triple("billing_customer_id", "customers", "Rechnungsempfänger"),
            Triple("order_id", "orders", "Auftrag"),
        )) {
        val id = row.optString(field)
        if (id.isNotEmpty()) {
            var title by remember(collection, id) { mutableStateOf("Wird gelesen …") }
            LaunchedEffect(collection, id) {
                title =
                    try {
                        controller.store.nativeData().detail(collection, id)?.let {
                            it.optString("name", it.optString("title"))
                        } ?: "Nicht mehr vorhanden"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        "Derzeit nicht lesbar"
                    }
            }
            Text("$label: $title")
        }
    }
    FinancePositionSummary(row.optJSONArray("positions"))
}

/** UI hints lead to a field; the existing owner validation remains authoritative. */
private fun financeOpenItems(form: JSONObject): List<Pair<Int, String>> = buildList {
    if (form.optString("customer_id").isBlank()) add(0 to "Kunde auswählen")
    if (form.optString("title").isBlank()) add(0 to "Titel ergänzen")
    if (form.optString("document_type") !in OfferContract.types()) add(0 to "Dokumentart auswählen")
    for ((field, label) in
        listOf(
            "issue_date" to "Angebotsdatum",
            "valid_until" to "Gültigkeit",
            "execution_window" to "Ausführungszeitraum",
            "payment_terms" to "Zahlungsbedingungen",
            "tax_mode" to "Steuermodus",
            "customer_type" to "Kundentyp",
        )) {
        if (form.optString(field).isBlank()) add(0 to "$label ergänzen")
    }
    if (!form.optBoolean("binding_confirmed") || !form.optBoolean("scope_confirmed"))
        add(0 to "Bindungsart und Leistungsumfang prüfen")
    if (
        form.optString("customer_type") == "consumer" &&
            (form.optString("contract_channel").isBlank() ||
                (form.optString("contract_channel") in listOf("distance", "off_premise") &&
                    (form.optString("consumer_information").isBlank() ||
                        !form.optBoolean("consumer_information_confirmed"))))
    )
        add(0 to "Verbraucherinformationen und Vertragsweg prüfen")
    val lines = form.optJSONArray("positions") ?: JSONArray()
    if (lines.length() == 0) add(1 to "Mindestens eine Leistung ergänzen")
    for (index in 0 until lines.length()) {
        val line = lines.getJSONObject(index)
        if (line.optString("title").isBlank() || line.optString("unit").isBlank())
            add(1 to "Position ${index + 1}: Leistung und Einheit ergänzen")
        val quantity =
            runCatching { BigDecimal(line.optString("quantity").replace(',', '.')) }.getOrNull()
        val price =
            runCatching { BigDecimal(line.optString("price").replace(',', '.')) }.getOrNull()
        if (quantity == null || quantity.signum() <= 0 || price == null || price.signum() < 0)
            add(1 to "Position ${index + 1}: Menge und Preis ergänzen")
        if (
            line.optString("catalog_entry_id").isBlank() &&
                line.optString("price_source") != "user_confirmed"
        )
            add(1 to "Position ${index + 1}: Preis ausdrücklich bestätigen")
        if (
            form.optString("tax_mode") == "standard" &&
                (line.isNull("tax_rate") || line.optString("tax_rate").isBlank())
        )
            add(1 to "Position ${index + 1}: Steuersatz ergänzen")
    }
}

/** Offer and service-catalog editors use the existing local owner commands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeFinanceScreen(section: String, id: String, controller: NativeAppController) {
    val listStates = rememberSaveableStateHolder()
    var editing by
        rememberSaveable(
            section,
            id,
            controller.route.mode,
            controller.route.args.optString("_draft_key"),
        ) {
            mutableStateOf(controller.route.mode in listOf("edit", "new"))
        }
    var fresh by
        rememberSaveable(section, id, controller.route.mode) {
            mutableStateOf(controller.route.mode == "new")
        }
    var refresh by remember { mutableIntStateOf(0) }
    var detail by remember(section, id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(section, id) { mutableStateOf("") }
    var pendingAction by remember(section, id) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var offerReview by remember(section, id) { mutableStateOf<JSONObject?>(null) }
    var offerReviewed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var detailField by remember { mutableStateOf<Pair<String, String>?>(null) }
    var detailFieldError by remember { mutableStateOf("") }
    var detailFieldBusy by remember { mutableStateOf(false) }
    var detailFieldHash by remember { mutableStateOf("") }
    var detailConflict by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(detailField) {
        detailFieldError = ""
        detailFieldHash = detail?.optString("record_hash").orEmpty()
    }
    val detailUndo = remember { SnackbarHostState() }
    fun detailValue(record: JSONObject, path: String): String {
        val parts = path.split(':')
        return if (parts.size == 3)
            record
                .optJSONArray("positions")
                ?.optJSONObject(parts[1].toInt())
                ?.optString(parts[2])
                .orEmpty()
        else record.optString(path)
    }
    suspend fun readDetail(): JSONObject =
        controller.query(
            if (section == "offers") "get_offer" else "get_offer_template",
            JSONObject().put(if (section == "offers") "offer_id" else "entry_id", id),
        )
    suspend fun writeDetailValue(
        path: String,
        value: String,
        expected: String,
        expectedHash: String,
    ): JSONObject {
        val tool = if (section == "offers") "update_offer_draft" else "update_catalog_entry"
        val operationKey =
            "finance-field:$section:$id:" +
                PixelMcpWriteTools.hash(
                    JSONObject()
                        .put("field", path)
                        .put("value", value)
                        .put("record_hash", expectedHash)
                )
        val retained =
            controller.draft("settlement-action:$operationKey")?.optJSONObject("original_args")
        if (retained != null) {
            controller.settlementAction(
                operationKey,
                tool,
                JSONObject(retained.toString()).apply { remove("request_id") },
            )
            return readDetail().also { detail = it }
        }
        val latest = readDetail()
        if (latest.getString("record_hash") != expectedHash)
            throw PixelMcpWriteTools.Rejected(
                "WRITE_CONFLICT",
                "Der Wert wurde inzwischen geändert.",
            )
        val current = latest.getJSONObject(if (section == "offers") "offer" else "entry")
        val currentValue = detailValue(current, path)
        val numeric = path.endsWith("price") || path.endsWith("quantity")
        val currentNumber = currentValue.replace(',', '.').toBigDecimalOrNull()
        val expectedNumber = expected.replace(',', '.').toBigDecimalOrNull()
        if (
            !(if (numeric && currentNumber != null && expectedNumber != null)
                currentNumber.compareTo(expectedNumber) == 0
            else currentValue == expected)
        )
            throw PixelMcpWriteTools.Rejected(
                "WRITE_CONFLICT",
                "Der Wert wurde inzwischen geändert.",
            )
        val updates = JSONObject()
        if (path.startsWith("positions:")) {
            val parts = path.split(':')
            val positions = JSONArray()
            for ((index, line) in
                financeRows(JSONObject().put("items", current.getJSONArray("positions")))
                    .withIndex()) {
                val changed =
                    financeFields(
                        line,
                        arrayOf(
                            "title",
                            "description",
                            "unit",
                            "catalog_entry_id",
                            "catalog_hash",
                            "price_source",
                            "price_basis",
                            "price_note",
                            "calculation",
                            "quantity",
                            "price",
                            "tax_rate",
                        ),
                    )
                if (index == parts[1].toInt())
                    changed.put(
                        parts[2],
                        if (parts[2] in listOf("quantity", "price"))
                            BigDecimal(value.replace(',', '.'))
                        else value,
                    )
                positions.put(changed)
            }
            updates.put("positions", positions)
        } else
            updates.put(
                path,
                if (path in listOf("default_price", "default_quantity"))
                    BigDecimal(value.replace(',', '.'))
                else value,
            )
        controller.settlementAction(
            operationKey,
            tool,
            JSONObject()
                .put(if (section == "offers") "offer_id" else "entry_id", id)
                .put("expected_record_hash", latest.getString("record_hash"))
                .put("updates", updates),
        )
        return readDetail().also { detail = it }
    }
    fun saveDetailValue(
        field: Pair<String, String>,
        entered: String,
        before: String,
        expectedHash: String,
        showUndo: Boolean = true,
    ) {
        scope.launch {
            detailFieldBusy = true
            detailFieldError = ""
            try {
                val saved = writeDetailValue(field.first, entered, before, expectedHash)
                detailField = null
                detailFieldBusy = false
                if (showUndo) {
                    detailUndo.currentSnackbarData?.dismiss()
                    val result =
                        kotlinx.coroutines.withTimeoutOrNull(5000) {
                            detailUndo.showSnackbar(
                                "Gespeichert",
                                "Rückgängig",
                                duration = SnackbarDuration.Indefinite,
                            )
                        }
                    if (result == SnackbarResult.ActionPerformed)
                        saveDetailValue(
                            field,
                            before,
                            entered,
                            saved.getString("record_hash"),
                            false,
                        )
                    else detailUndo.currentSnackbarData?.dismiss()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (
                    (failure is NativeCallFailure &&
                        failure.detail.optString("code") == "WRITE_CONFLICT") ||
                        (failure is PixelMcpWriteTools.Rejected && failure.code == "WRITE_CONFLICT")
                ) {
                    try {
                        detailConflict =
                            JSONObject()
                                .put("field", field.first)
                                .put("label", field.second)
                                .put("mine", entered)
                                .put("latest", readDetail())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (readFailure: Exception) {
                        detailFieldError = readFailure.message ?: "Aktuellen Stand erneut laden."
                    }
                } else detailFieldError = failure.message ?: "Wert nicht gespeichert."
            } finally {
                detailFieldBusy = false
            }
        }
    }
    LaunchedEffect(section, id, refresh) {
        if (id.isEmpty()) return@LaunchedEffect
        error = ""
        try {
            detail =
                when (section) {
                    "offers" -> controller.query("get_offer", JSONObject().put("offer_id", id))
                    else -> controller.query("get_offer_template", JSONObject().put("entry_id", id))
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Datensatz konnte nicht gelesen werden."
        }
    }
    val row =
        detail?.let {
            if (section == "offers") it.optJSONObject("offer") else it.optJSONObject("entry")
        }
    when {
        pendingAction != null -> {
            val action = pendingAction!!
            NativeActionForm(
                controller,
                action.first,
                when (action.first) {
                    "record_offer_acceptance" -> "Annehmen"
                    "decline_offer" -> "Ablehnung erfassen"
                    "finalize_offer" -> "Geprüftes Angebots-PDF erstellen"
                    "convert_offer_to_order" -> "Angenommenes Angebot in Auftrag übernehmen"
                    else -> "Angaben prüfen und bestätigen"
                },
                action.second,
                fixedInputs =
                    action.first in
                        setOf(
                            "finalize_offer",
                            "prepare_offer_share",
                            "prepare_offer_delivery",
                            "duplicate_offer",
                            "validate_offer_draft",
                            "update_catalog_entry",
                        ),
                submitLabel =
                    when (action.first) {
                        "finalize_offer" -> "PDF erstellen"
                        "prepare_offer_share" -> "Android-Teilen öffnen"
                        "prepare_offer_delivery" -> "E-Mail-Übergabe öffnen"
                        else -> "Bestätigen"
                    },
                onCancel = { pendingAction = null },
            ) { result ->
                val tool = action.first
                pendingAction = null
                refresh++
                val saved = financeData(result)
                if (tool == "finalize_offer") {
                    controller.message("Angebots-PDF gespeichert.")
                    val finalizedId = action.second.optString("offer_id")
                    if (finalizedId != id) controller.navigate("offers", finalizedId)
                }
                if (tool == "duplicate_offer" && saved.optString("offer_id").isNotEmpty())
                    controller.navigate("offers", saved.getString("offer_id"))
                if (
                    tool in listOf("record_offer_acceptance", "convert_offer_to_order") &&
                        saved.optString("order_id").isNotEmpty()
                )
                    controller.navigate("orders", saved.getString("order_id"))
                if (tool in listOf("prepare_offer_delivery", "prepare_offer_share"))
                    scope.launch {
                        try {
                            controller.shareOffer(
                                id,
                                if (tool == "prepare_offer_delivery") "email" else "share",
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (f: Exception) {
                            controller.message(f.message.orEmpty())
                        }
                    }
            }
            NativeTextButton(onClick = { pendingAction = null }) { Text("Zurück zum Beleg") }
        }
        offerReview != null ->
            Column(Modifier.fillMaxSize()) {
                val review = offerReview!!
                val snapshot = review.getJSONObject("review_snapshot")
                val validation = review.optJSONObject("validation") ?: JSONObject()
                val errors = validation.optJSONArray("errors") ?: JSONArray()
                fun correct(message: String) {
                    when {
                        message.contains("Aussteller", true) ||
                            message.contains("UID") ||
                            message.contains("Steuerbefreiung") ||
                            message.contains("zentral") -> controller.navigate("settings")
                        message.contains("Empfänger") || message.contains("Auslandsfall") -> {
                            val recipient =
                                snapshot
                                    .optJSONObject("recipient")
                                    ?.optString("id")
                                    .orEmpty()
                                    .ifEmpty {
                                        review
                                            .getJSONObject("offer")
                                            .optString("billing_customer_id")
                                            .ifEmpty {
                                                review
                                                    .getJSONObject("offer")
                                                    .optString("customer_id")
                                            }
                                    }
                            if (recipient.isNotEmpty())
                                controller.navigate("customers", recipient, "edit")
                            else {
                                offerReview = null
                                editing = true
                            }
                        }
                        else -> {
                            val target = review.getJSONObject("offer").getString("id")
                            offerReview = null
                            controller.navigate(
                                "offers",
                                target,
                                "edit",
                                JSONObject()
                                    .put(
                                        "_offer_step",
                                        if (
                                            message.contains("Position") ||
                                                message.contains("Leistung erforderlich")
                                        )
                                            1
                                        else 0,
                                    ),
                            )
                        }
                    }
                }
                BackHandler { offerReview = null }
                Column(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Angebot prüfen · 3 von 3", style = MaterialTheme.typography.headlineSmall)
                    NativeStatus(
                        if (validation.optBoolean("valid")) "Bereit für PDF"
                        else "Angaben ergänzen",
                        if (validation.optBoolean("valid")) NativeStatusTone.Confirmed
                        else NativeStatusTone.Attention,
                    )
                    FinanceIssuerSummary(snapshot.optJSONObject("business_settings"))
                    FinancePartySummary("Rechnungsempfänger", snapshot.optJSONObject("recipient"))
                    FinancePartySummary("Kunde", snapshot.optJSONObject("customer"))
                    FinanceValues(
                        snapshot.optJSONObject("order"),
                        listOf(
                            "title" to "Auftrag",
                            "location" to "Einsatzort",
                            "status" to "Status",
                        ),
                    )
                    FinanceDraftSummary(controller, review.getJSONObject("offer"), "offers")
                    FinanceOfferTotal(review.getJSONObject("offer"))
                    val entries = snapshot.optJSONArray("service_catalog") ?: JSONArray()
                    for (index in 0 until entries.length()) FinanceValues(
                        entries.optJSONObject(index),
                        listOf(
                            "title" to "Bestätigter Baustein",
                            "default_price" to "Katalogpreis",
                            "price_confirmed" to "Preis bestätigt",
                        ),
                    )
                    for (index in 0 until errors.length()) NativeInfoRow(
                        nativeDisplayText(errors.optString(index)),
                        "Angaben ergänzen",
                        onClick = { correct(errors.optString(index)) },
                    )
                    FinanceCheck(
                        "Ich habe Aussteller, Empfänger, Bindungsart, Leistungsumfang und alle Preise dieses Angebots geprüft.",
                        offerReviewed,
                    ) {
                        offerReviewed = it
                    }
                    if (errors.length() > 0)
                        NativeButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("PDF erstellen")
                        }
                    NativeTextButton(onClick = { offerReview = null }) {
                        Text("Zum Angebot zurück")
                    }
                }
                NativeActionBar(
                    primaryLabel = if (errors.length() > 0) "Angaben ergänzen" else "PDF erstellen",
                    onPrimary = {
                        if (errors.length() > 0) correct(errors.optString(0))
                        else {
                            pendingAction =
                                "finalize_offer" to
                                    JSONObject()
                                        .put(
                                            "offer_id",
                                            review.getJSONObject("offer").getString("id"),
                                        )
                                        .put(
                                            "expected_record_hash",
                                            review.getString("record_hash"),
                                        )
                                        .put(
                                            "expected_context_hash",
                                            review.getString("context_hash"),
                                        )
                            offerReview = null
                        }
                    },
                    secondaryLabel = "Zurück",
                    onSecondary = { offerReview = null },
                    enabled = errors.length() > 0 || offerReviewed && validation.optBoolean("valid"),
                )
            }
        editing &&
            (fresh || row != null || controller.route.args.optString("_draft_key").isNotEmpty()) ->
            FinanceEditor(
                section,
                if (fresh) null else row,
                if (fresh) "" else detail?.optString("record_hash").orEmpty(),
                controller,
                onClose = {
                    editing = false
                    fresh = false
                    if (
                        controller.route.mode in setOf("new", "edit") ||
                            controller.route.args.optString("_draft_key").isNotEmpty()
                    )
                        controller.back()
                    else refresh++
                },
                onSaved = {
                    editing = false
                    fresh = false
                    if (
                        controller.route.mode in setOf("new", "edit") ||
                            controller.route.args.optString("_draft_key").isNotEmpty()
                    )
                        controller.finishAction()
                    else refresh++
                },
                onReview = { savedId ->
                    scope.launch {
                        try {
                            offerReview =
                                controller.query(
                                    "get_offer",
                                    JSONObject()
                                        .put("offer_id", savedId)
                                        .put("include_review", true),
                                )
                            offerReviewed = false
                            editing = false
                            fresh = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            controller.message(failure.message.orEmpty())
                        }
                    }
                },
            )
        id.isEmpty() ->
            listStates.SaveableStateProvider("finance-list") {
                FinanceList(
                    section,
                    controller,
                    onCreate = {
                        fresh = true
                        editing = true
                    },
                )
            }
        row == null ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error.isEmpty()) CircularProgressIndicator()
                else Text(error, color = MaterialTheme.colorScheme.error)
                NativeTextButton(onClick = { refresh++ }) { Text("Erneut lesen") }
            }
        else ->
            Column(Modifier.fillMaxSize()) {
                val editable = section == "catalog" || OfferRules.editable(row)
                Column(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NativeDetailHeading(row.optString("title"))
                    if (editable)
                        NativeKeyValue("Bezeichnung", row.optString("title")) {
                            detailField = "title" to "Bezeichnung"
                        }
                    NativeStatus(
                        if (section == "offers" && !editable) "PDF gespeichert"
                        else
                            financeStatus(
                                row.optString(
                                    "status",
                                    if (section == "catalog") "Leistungsbaustein" else "draft",
                                )
                            ),
                        if (section == "offers" && !editable) NativeStatusTone.Confirmed
                        else NativeStatusTone.Neutral,
                    )
                    if (section != "catalog") {
                        if (!editable)
                            NativeInfoRow("Angebotsstatus", financeStatus(row.optString("status")))
                        val dates = listOf("issue_date" to "Datum", "valid_until" to "Gültig bis")
                        val dateText =
                            dates
                                .mapNotNull { (key, label) ->
                                    row.optString(key)
                                        .takeIf { it.isNotBlank() }
                                        ?.let { "$label: ${nativeDisplayDate(it)}" }
                                }
                                .joinToString(" · ")
                        if (dateText.isNotEmpty()) Text(dateText)
                        if (editable)
                            NativeGroup("Leistungen") {
                                for ((index, line) in
                                    financeRows(
                                            JSONObject()
                                                .put(
                                                    "items",
                                                    row.optJSONArray("positions") ?: JSONArray(),
                                                )
                                        )
                                        .withIndex()) {
                                    NativeKeyValue("Leistung", line.optString("title")) {
                                        detailField = "positions:$index:title" to "Leistung"
                                    }
                                    NativeKeyValue(
                                        "Menge · ${line.optString("unit")}",
                                        nativeNumber(line.opt("quantity")),
                                    ) {
                                        detailField = "positions:$index:quantity" to "Menge"
                                    }
                                    NativeKeyValue(
                                        "Preis je Einheit",
                                        financeMoney(line.opt("price")),
                                        money = true,
                                    ) {
                                        detailField =
                                            "positions:$index:price" to "Preis je Einheit (€)"
                                    }
                                }
                            }
                        else FinancePositionSummary(row.optJSONArray("positions"))
                        FinanceOfferTotal(row)
                        for ((key, label) in
                            listOf(
                                "header_text" to "Kopftext",
                                "footer_text" to "Fußtext",
                                "notes" to "Notizen",
                                "binding_notice" to "Bindungshinweis",
                                "payment_terms" to "Zahlungsbedingungen",
                                "assumptions" to "Annahmen",
                                "exclusions" to "Ausschlüsse",
                            )) if (row.optString(key).isNotEmpty())
                            if (editable)
                                NativeKeyValue(label, row.optString(key)) {
                                    detailField = key to label
                                }
                            else Text("$label: ${row.optString(key)}")
                    } else {
                        NativeGroup("Leistung") {
                            for ((key, label) in
                                listOf(
                                    "description" to "Beschreibung",
                                    "unit" to "Einheit",
                                    "default_price" to "Preis je Einheit",
                                )) NativeKeyValue(
                                label,
                                if (key == "default_price") financeMoney(row.opt(key))
                                else row.optString(key),
                                money = key == "default_price",
                            ) {
                                detailField = key to label
                            }
                        }
                        NativeStatus(if (row.optBoolean("active", true)) "Aktiv" else "Deaktiviert")
                    }
                    if (section == "offers") {
                        if (editable) {
                            NativeOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val validation =
                                                controller.query(
                                                    "validate_offer",
                                                    JSONObject().put("offer_id", id),
                                                )
                                            detail = validation
                                            controller.message(
                                                validation.optJSONObject("validation")?.let {
                                                    if (it.optBoolean("valid"))
                                                        "Angaben sind vollständig."
                                                    else
                                                        it.optJSONArray("errors")
                                                            ?.join("; ")
                                                            .orEmpty()
                                                } ?: "Prüfung gelesen."
                                            )
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (f: Exception) {
                                            controller.message(f.message.orEmpty())
                                        }
                                    }
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Pflichtangaben prüfen")
                            }
                            NativeOutlinedButton(
                                onClick = {
                                    pendingAction =
                                        "validate_offer_draft" to
                                            JSONObject()
                                                .put("offer_id", id)
                                                .put(
                                                    "expected_record_hash",
                                                    detail!!.optString("record_hash"),
                                                )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Geprüften Entwurfsstand speichern")
                            }
                            NativeOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            offerReview =
                                                controller.query(
                                                    "get_offer",
                                                    JSONObject()
                                                        .put("offer_id", id)
                                                        .put("include_review", true),
                                                )
                                            offerReviewed = false
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (f: Exception) {
                                            controller.message(f.message.orEmpty())
                                        }
                                    }
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Prüfen und PDF erstellen")
                            }
                        } else {
                            Text(
                                detail?.optJSONObject("decision")?.let {
                                    "Entscheidung: ${financeStatus(it.optString("decision"))} · ${nativeDisplayDate(it.optString("accepted_at"))}"
                                } ?: "Annahme oder Ablehnung noch nicht erfasst."
                            )
                            FinanceValues(
                                detail?.optJSONObject("decision"),
                                listOf(
                                    "evidence" to "Annahmenachweis",
                                    "reason" to "Begründung",
                                    "recorded_at" to "Erfasst am",
                                ),
                            )
                            NativeOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            controller.viewDocument("offer", id)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (f: Exception) {
                                            controller.message(f.message.orEmpty())
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Original-PDF öffnen")
                            }
                            NativeOutlinedButton(
                                onClick = {
                                    pendingAction =
                                        "prepare_offer_delivery" to
                                            JSONObject()
                                                .put("offer_id", id)
                                                .put(
                                                    "expected_record_hash",
                                                    detail!!.optString("record_hash"),
                                                )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("E-Mail-Übergabe vorbereiten")
                            }
                            NativeOutlinedButton(
                                onClick = {
                                    pendingAction =
                                        "confirm_offer_delivery" to
                                            JSONObject()
                                                .put("offer_id", id)
                                                .put(
                                                    "expected_record_hash",
                                                    detail!!.optString("record_hash"),
                                                )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Versandbeleg beim Anbieter prüfen")
                            }
                            NativeOutlinedButton(
                                onClick = {
                                    pendingAction =
                                        "record_offer_acceptance" to
                                            JSONObject()
                                                .put("offer_id", id)
                                                .put(
                                                    "expected_record_hash",
                                                    detail!!.optString("record_hash"),
                                                )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Annehmen")
                            }
                            NativeTextButton(
                                onClick = {
                                    pendingAction =
                                        "decline_offer" to
                                            JSONObject()
                                                .put("offer_id", id)
                                                .put(
                                                    "expected_record_hash",
                                                    detail!!.optString("record_hash"),
                                                )
                                }
                            ) {
                                Text("Ablehnung erfassen")
                            }
                            NativeTextButton(
                                onClick = {
                                    controller.action(
                                        "Entscheidungsverlauf",
                                        "get_offer_decision_history",
                                        JSONObject().put("offer_id", id),
                                    )
                                }
                            ) {
                                Text("Entscheidungsverlauf lesen")
                            }
                        }
                        NativeTextButton(
                            onClick = {
                                pendingAction =
                                    "duplicate_offer" to JSONObject().put("offer_id", id)
                            }
                        ) {
                            Text("Als neue Version duplizieren")
                        }
                    }
                    if (section == "catalog")
                        NativeOutlinedButton(
                            onClick = {
                                pendingAction =
                                    "update_catalog_entry" to
                                        JSONObject()
                                            .put("entry_id", id)
                                            .put(
                                                "expected_record_hash",
                                                detail!!.optString("record_hash"),
                                            )
                                            .put(
                                                "updates",
                                                JSONObject()
                                                    .put("active", !row.optBoolean("active", true)),
                                            )
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (row.optBoolean("active", true)) "Baustein deaktivieren"
                                else "Baustein reaktivieren"
                            )
                        }
                    if (editable && section == "offers")
                        NativeDeleteButton(controller, "offer", id, label = "Entwurf löschen") {
                            controller.returnToList(section)
                        }
                }
                SnackbarHost(detailUndo)
                NativeActionBar(
                    primaryLabel =
                        if (editable && section == "offers") "PDF erstellen"
                        else if (editable) "Zur Liste" else "Teilen",
                    onPrimary = {
                        if (editable && section == "offers")
                            scope.launch {
                                try {
                                    offerReview =
                                        controller.query(
                                            "get_offer",
                                            JSONObject()
                                                .put("offer_id", id)
                                                .put("include_review", true),
                                        )
                                    offerReviewed = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    controller.message(failure.message.orEmpty())
                                }
                            }
                        else if (editable) controller.returnToList(section)
                        else
                            pendingAction =
                                "prepare_offer_share" to
                                    JSONObject()
                                        .put("offer_id", id)
                                        .put(
                                            "expected_record_hash",
                                            detail!!.optString("record_hash"),
                                        )
                    },
                    secondaryLabel = if (section == "offers") "Zur Liste" else null,
                    onSecondary =
                        if (section == "offers") ({ controller.returnToList(section) }) else null,
                )
            }
    }
    detailField
        ?.takeIf { detailConflict == null }
        ?.let { field ->
            val value = row?.let { detailValue(it, field.first) }.orEmpty()
            NativeValueSheet(
                field.second,
                value,
                keyboardType =
                    if (field.first.endsWith("price") || field.first.endsWith("quantity"))
                        androidx.compose.ui.text.input.KeyboardType.Decimal
                    else androidx.compose.ui.text.input.KeyboardType.Text,
                error = detailFieldError,
                enabled = !detailFieldBusy,
                onClose = { entered ->
                    if (entered == value) detailField = null
                    else saveDetailValue(field, entered, value, detailFieldHash)
                },
            )
        }
    detailConflict?.let { conflict ->
        val latest = conflict.getJSONObject("latest")
        val current = latest.getJSONObject(if (section == "offers") "offer" else "entry")
        val field = conflict.getString("field") to conflict.getString("label")
        val currentValue = detailValue(current, field.first)
        AlertDialog(
            onDismissRequest = {
                detailConflict = null
                detailField = null
            },
            title = { Text("Angabe geändert") },
            text = {
                Column {
                    Text("Gespeichert: $currentValue")
                    NativeTextButton(
                        {
                            detail = latest
                            detailConflict = null
                            detailField = null
                            detailFieldError = ""
                        },
                        Modifier.semantics { contentDescription = "Diesen behalten · gespeichert" },
                    ) {
                        Text("Diesen behalten")
                    }
                    Text("Meine Angabe: ${conflict.getString("mine")}")
                }
            },
            confirmButton = {
                NativeTextButton(
                    {
                        detailConflict = null
                        saveDetailValue(
                            field,
                            conflict.getString("mine"),
                            currentValue,
                            latest.getString("record_hash"),
                        )
                    },
                    Modifier.semantics { contentDescription = "Diesen behalten · meine Angabe" },
                ) {
                    Text("Diesen behalten")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinanceList(section: String, controller: NativeAppController, onCreate: () -> Unit) {
    val scope = rememberCoroutineScope()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var customer by
        rememberSaveable(section) { mutableStateOf(controller.route.args.optString("customer_id")) }
    var status by
        rememberSaveable(section) { mutableStateOf(controller.route.args.optString("status")) }
    var query by rememberSaveable(section) { mutableStateOf("") }
    var offset by rememberSaveable(section) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val repository = remember(controller) { controller.store.nativeData() }
    val source =
        remember(section, customer, status, query, offset) {
            error = ""
            repository
                .watchList(
                    if (section == "catalog") "service_catalog" else section,
                    search = query,
                    status = status,
                    customerId = customer,
                    offset = offset,
                    limit = 50,
                    includeInactive = section == "catalog",
                )
                .catch { failure -> error = NATIVE_LIST_READ_ERROR }
        }
    val displayFlow =
        remember(source) {
            source
                .map<NativeListPage, NativeListPage?> { it }
                .onStart {
                    error = ""
                    emit(null)
                }
        }
    val loadedPage by displayFlow.collectAsStateWithLifecycle(null)
    val page = loadedPage ?: NativeListPage(emptyList(), 0, offset, 50)
    val listLoading = loadedPage == null && error.isEmpty()
    NativeSearchList(
        if (section == "catalog") "Bausteine suchen" else "Suchen",
        query,
        {
            query = it
            offset = 0
        },
        if (listLoading) "Wird geladen …"
        else "${page.total} Treffer · ${page.rows.size} auf dieser Seite",
        onFilters = if (section == "catalog") null else ({ filtersOpen = !filtersOpen }),
        onFocus = { filtersOpen = false },
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!imeVisible)
                item {
                    NativeContext(
                        controller,
                        JSONObject(controller.route.args.toString()).put("customer_id", customer),
                        "Kunde",
                    )
                }
            if (filtersOpen)
                item(key = "native-list-filters") {
                    if (section != "catalog") {
                        NativeRecordPicker(controller, "customers", "Kunde", customer) {
                            customer = it
                            offset = 0
                        }
                        FinanceChoice(
                            "Status",
                            status,
                            listOf(
                                "" to "Alle",
                                "draft" to "Entwurf",
                                "validated" to "Geprüft",
                                "finalized" to "Finalisiert",
                                "accepted" to "Angenommen",
                                "share_prepared" to "Teilen vorbereitet",
                                "sent" to "Versand bestätigt",
                            ),
                        ) {
                            status = it
                            offset = 0
                        }
                    }
                }
            if (!imeVisible && (customer.isNotEmpty() || status.isNotEmpty() || query.isNotEmpty()))
                item {
                    NativeTextButton(
                        onClick = {
                            customer = ""
                            status = ""
                            query = ""
                            offset = 0
                        }
                    ) {
                        Text("Filter und Suche zurücksetzen")
                    }
                }
            if (error.isNotEmpty()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            if (listLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!listLoading && page.rows.isEmpty() && error.isEmpty())
                item {
                    Text(
                        if (query.isBlank() && customer.isBlank() && status.isBlank())
                            when (section) {
                                "catalog" ->
                                    "Leistungsbausteine speichern wiederkehrende Leistungen und Preise."
                                "offers" ->
                                    "Angebote halten die vereinbarten Leistungen und Preise deiner Kunden fest."
                                else ->
                                    "Rechnungen dokumentieren die abgerechneten Leistungen deiner Kunden."
                            }
                        else "Keine Treffer. Suchtext oder Filter ändern."
                    )
                }
            items(page.rows, key = { it.id }) { row ->
                NativeSwipeRow(
                    controller,
                    if (section == "catalog") "service_template"
                    else if (section == "offers") "offer" else "invoice",
                    row.id,
                    canDelete =
                        section == "catalog" || row.status.lowercase() in setOf("draft", "entwurf"),
                ) {
                    OutlinedCard(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                            controller.navigate(
                                section,
                                row.id,
                                args =
                                    JSONObject(controller.route.args.toString())
                                        .put("customer_id", customer),
                            )
                        }
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            if (row.subtitle.isNotEmpty()) Text(nativeDisplayText(row.subtitle))
                            if (row.status.isNotEmpty()) NativeStatus(financeStatus(row.status))
                            if (section == "catalog")
                                Text(if (row.active) "Aktiv" else "Deaktiviert")
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NativeTextButton(
                        enabled = offset > 0,
                        onClick = { offset = (offset - 50).coerceAtLeast(0) },
                    ) {
                        Text("Zurück")
                    }
                    NativeTextButton(enabled = page.hasMore, onClick = { offset += 50 }) {
                        Text("Weitere")
                    }
                }
            }
        }
        if (!imeVisible)
            NativeActionBar(
                if (section == "catalog") "Baustein anlegen" else "Angebot anlegen",
                onCreate,
            )
    }
}

@Composable
private fun FinanceChoice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        NativeOutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
            Text(
                "$label: ${choices.firstOrNull { it.first==value }?.second ?: value.ifEmpty { "Bitte wählen" }}"
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
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
private fun FinanceCheck(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(value, onCheckedChange = onChange)
        Text(label, Modifier.weight(1f).padding(top = 12.dp))
    }
}

@Composable
private fun FinancePositionSummary(positions: JSONArray?) {
    positions ?: return
    if (positions.length() == 0) return
    Text("Leistungen", style = MaterialTheme.typography.titleMedium)
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            for (index in 0 until positions.length()) {
                val row = positions.optJSONObject(index) ?: continue
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${index+1}. ${row.optString("title",row.optString("description"))}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${if(!row.has("quantity") || row.isNull("quantity")) "Menge offen" else nativeNumber(row.opt("quantity"))} ${row.optString("unit")} × ${financeMoney(row.opt("price"))} · USt ${if(!row.has("tax_rate") || row.isNull("tax_rate")) "offen" else nativeNumber(row.opt("tax_rate"))+" %"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.optString("description").isNotEmpty())
                        Text(
                            row.optString("description"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    if (row.has("net"))
                        Text(
                            "Netto ${financeMoney(row.opt("net"))} · Steuer ${financeMoney(row.opt("tax"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
}

@Composable
private fun FinanceEditor(
    section: String,
    original: JSONObject?,
    recordHash: String,
    controller: NativeAppController,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onReview: (String) -> Unit,
) {
    val id =
        original?.optString("id").orEmpty().ifEmpty {
            if (controller.route.mode == "edit") controller.route.id else ""
        }
    val key =
        controller.route.args.optString("_draft_key").ifEmpty {
            "finance:$section:${id.ifEmpty { "new" }}"
        }
    var step by
        rememberSaveable(key) {
            mutableIntStateOf(controller.route.args.optInt("_offer_step").coerceIn(0, 2))
        }
    var termsOpen by
        rememberSaveable(key) { mutableStateOf(controller.route.args.has("_offer_step")) }
    var discard by remember { mutableStateOf(false) }
    var form by remember(key) { mutableStateOf(original?.let(::financeCopy) ?: JSONObject()) }
    var loaded by remember(key) { mutableStateOf(false) }
    var loadRetry by remember(key) { mutableIntStateOf(0) }
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
    val scope = rememberCoroutineScope()
    fun retain(next: JSONObject) {
        if (pending != null) return
        form = next
        changed = true
        controller.saveDraft(
            key,
            JSONObject()
                .put("record", next)
                .put("record_hash", observedHash)
                .put("request_id", request),
        )
    }
    fun field(name: String, value: Any?) {
        retain(financeCopy(form).put(name, value ?: JSONObject.NULL))
    }
    LaunchedEffect(key, loadRetry) {
        try {
            val saved = controller.draft(key)
            if (saved != null) {
                form = saved.optJSONObject("record") ?: form
                observedHash = saved.optString("record_hash", recordHash)
                request = saved.optString("request_id", request)
                pending = saved.optJSONObject("pending")?.takeIf { it.length() > 0 }
                pendingTool = saved.optString("original_tool")
                restored = saved.optBoolean("_restore_action_review_required")
                changed = true
            } else if (original == null) {
                if (id.isNotEmpty())
                    throw IllegalArgumentException(
                        "Der ursprüngliche Beleg ist nicht lesbar. Es wird kein Ersatzbeleg angelegt."
                    )
                val settings =
                    runCatching { controller.query("get_business_settings") }
                        .getOrDefault(JSONObject())
                        .let { it.optJSONObject("settings") ?: it }
                form = JSONObject().put("issue_date", financeToday()).put("positions", JSONArray())
                if (settings.optString("tax_mode") in listOf("small_business", "standard"))
                    form.put("tax_mode", settings.getString("tax_mode"))
                if (section == "catalog") form.put("active", true).put("pricing_type", "unit")
                for (field in listOf("customer_id", "order_id")) if (
                    controller.route.args.optString(field).isNotEmpty()
                )
                    form.put(field, controller.route.args.getString(field))
                if (section == "offers")
                    form.put(
                        "_source_context",
                        JSONObject().apply {
                            for (field in listOf("property_id", "inquiry_id")) if (
                                controller.route.args.optString(field).isNotEmpty()
                            )
                                put(field, controller.route.args.getString(field))
                        },
                    )
                if (section == "offers") {
                    if (controller.route.args.optString("title").isNotBlank())
                        form.put("title", controller.route.args.getString("title"))
                    val propertyId = controller.route.args.optString("property_id")
                    if (propertyId.isNotEmpty())
                        controller.store.nativeData().detail("properties", propertyId)?.let {
                            property ->
                            form.put("location", property.optString("address"))
                        }
                }
            }
            loaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (f: Exception) {
            error = f.message.orEmpty()
            loaded = false
        }
    }
    fun save(validate: Boolean = false, reviewAfterSave: Boolean = false) {
        if (busy || !loaded) return
        scope.launch {
            busy = true
            error = ""
            var attemptedWrite = false
            try {
                val tool =
                    pendingTool.ifEmpty {
                        when (section) {
                            "offers" ->
                                if (id.isEmpty()) {
                                    if (validate) "create_and_validate_offer"
                                    else "create_offer_draft"
                                } else "update_offer_draft"
                            else ->
                                if (id.isEmpty()) "create_catalog_entry" else "update_catalog_entry"
                        }
                    }
                val prior = pending
                if (prior != null) {
                    val proof = financeInspect(controller, tool, prior)
                    if (proof != null) {
                        controller.removeDraft(key)
                        val savedId =
                            proof.optString(
                                "offer_id",
                                proof.optString("entry_id", proof.optString("entity_id", id)),
                            )
                        if (reviewAfterSave) onReview(savedId) else onSaved(savedId)
                        return@launch
                    }
                    throw IllegalStateException(
                        "Speichern weiterhin nicht bestätigt. Die ursprünglichen Angaben bleiben erhalten; es wird nur das Ergebnis geprüft."
                    )
                }
                if (restored)
                    throw IllegalStateException(
                        "Wiederhergestellten Entwurf und aktuellen Bestand zuerst bewusst prüfen. Der ursprüngliche Auftrag wird nicht erneut ausgeführt."
                    )
                val args =
                    pending
                        ?: run {
                            if (!changed) {
                                if (reviewAfterSave && id.isNotEmpty()) onReview(id) else onClose()
                                return@launch
                            }
                            val payload =
                                financeFields(
                                    form,
                                    when (section) {
                                        "offers" -> OfferRules.FIELDS
                                        else ->
                                            arrayOf(
                                                "title",
                                                "description",
                                                "unit",
                                                "pricing_type",
                                                "category",
                                                "legal_notes",
                                                "assumptions",
                                                "exclusions",
                                                "default_price",
                                                "default_quantity",
                                                "active",
                                                "price_confirmed",
                                            )
                                    },
                                )
                            val numeric =
                                if (section == "catalog")
                                    listOf("default_price", "default_quantity")
                                else emptyList()
                            numeric.forEach { name ->
                                if (payload.has(name))
                                    payload.put(
                                        name,
                                        if (
                                            payload.optString(name).isBlank() ||
                                                payload.isNull(name)
                                        )
                                            JSONObject.NULL
                                        else
                                            BigDecimal(
                                                payload.get(name).toString().replace(',', '.')
                                            ),
                                    )
                            }
                            if (section != "catalog") {
                                val positions = JSONArray()
                                val raw = payload.optJSONArray("positions") ?: JSONArray()
                                for (i in 0 until raw.length()) {
                                    val line =
                                        financeFields(
                                            raw.getJSONObject(i),
                                            arrayOf(
                                                "title",
                                                "description",
                                                "unit",
                                                "catalog_entry_id",
                                                "catalog_hash",
                                                "price_source",
                                                "price_basis",
                                                "price_note",
                                                "calculation",
                                                "quantity",
                                                "price",
                                                "tax_rate",
                                            ),
                                        )
                                    for (name in
                                        listOf("quantity", "price", "tax_rate", "discount")) if (
                                        line.has(name)
                                    ) {
                                        val text =
                                            if (line.isNull(name)) ""
                                            else line.get(name).toString().trim()
                                        if (text.isEmpty()) line.put(name, JSONObject.NULL)
                                        else line.put(name, BigDecimal(text.replace(',', '.')))
                                    }
                                    line.optJSONObject("calculation")?.let { calculation ->
                                        for (name in
                                            listOf("base_price", "factor", "surcharge")) if (
                                            calculation.has(name) && !calculation.isNull(name)
                                        )
                                            calculation.put(
                                                name,
                                                BigDecimal(
                                                    calculation
                                                        .get(name)
                                                        .toString()
                                                        .replace(',', '.')
                                                ),
                                            )
                                    }
                                    positions.put(line)
                                }
                                payload.put("positions", positions)
                            }
                            val args =
                                if (id.isEmpty()) payload
                                else
                                    JSONObject()
                                        .put(
                                            if (section == "catalog") "entry_id" else "offer_id",
                                            id,
                                        )
                                        .put("expected_record_hash", observedHash)
                                        .put("updates", payload)
                            args.put("request_id", request)
                        }
                pending = financeCopy(args)
                pendingTool = tool
                controller.saveDraft(
                    key,
                    JSONObject()
                        .put("record", form)
                        .put("record_hash", observedHash)
                        .put("request_id", request)
                        .put("pending", args)
                        .put("original_tool", tool),
                )
                controller.flushDrafts()
                attemptedWrite = true
                val result = financeData(controller.call(tool, args))
                controller.removeDraft(key)
                val savedId =
                    result.optString(
                        if (section == "catalog") "entry_id" else "offer_id",
                        result.optString("entity_id", id),
                    )
                if (reviewAfterSave) onReview(savedId) else onSaved(savedId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (f: Exception) {
                if (
                    attemptedWrite &&
                        ((f as? NativeCallFailure)?.detail?.optString("commit_state") ==
                            "not_committed" ||
                            (f as? NativeCallFailure)?.detail?.optString("code") in
                                setOf("INVALID_INPUT", "WRITE_CONFLICT"))
                ) {
                    pending = null
                    pendingTool = ""
                    request = UUID.randomUUID().toString()
                    controller.saveDraft(
                        key,
                        JSONObject()
                            .put("record", form)
                            .put("record_hash", observedHash)
                            .put("request_id", request),
                    )
                }
                error =
                    f.message
                        ?: "Speichern ist noch nicht eindeutig bestätigt. Derselbe Vorgang bleibt erhalten."
            } finally {
                busy = false
            }
        }
    }
    BackHandler { onClose() }
    if (discard)
        LaunchedEffect(Unit) {
            try {
                controller.removeDraft(key)
                onClose()
            } catch (failure: Exception) {
                controller.message(failure.message ?: "Entwurf konnte nicht gelöscht werden.")
            } finally {
                discard = false
            }
        }
    var inspectionAttempt by remember { mutableIntStateOf(0) }
    val originalTool =
        pendingTool.ifBlank {
            if (section == "offers")
                if (id.isBlank()) "create_offer_draft" else "update_offer_draft"
            else if (id.isBlank()) "create_catalog_entry" else "update_catalog_entry"
        }
    val inspection =
        rememberNativePendingInspection(
            controller,
            originalTool,
            pending,
            loaded && !busy,
            inspectionAttempt,
        ) { proof ->
            val result = proof.optJSONObject("result") ?: proof
            val savedId =
                result.optString(
                    "offer_id",
                    result.optString("entry_id", proof.optString("entity_id", id)),
                )
            controller.removeDraft(key)
            pending = null
            onSaved(savedId)
            controller.message("Gespeichert.", NativeRoute(section, savedId, "detail"))
        }
    val openItems = if (section == "offers") financeOpenItems(form) else emptyList()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (section == "catalog") "Leistungsbaustein"
                else listOf("Kunde und Dokumentart", "Leistungen", "Angebot prüfen")[step],
                style = MaterialTheme.typography.headlineSmall,
            )
            if (!loaded) {
                if (error.isEmpty()) CircularProgressIndicator()
                else {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    NativeTextButton(
                        onClick = {
                            error = ""
                            loadRetry++
                        }
                    ) {
                        Text("Entwurf erneut lesen")
                    }
                }
                return@Column
            }
            if (section == "offers") {
                Text(
                    "${step + 1} von 3 · " +
                        listOf("Kunde und Art", "Leistungen", "Prüfen und PDF")[step]
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Kunde und Art", "Leistungen", "Prüfen").forEachIndexed { index, label ->
                        FilterChip(
                            selected = step == index,
                            onClick = { step = index },
                            enabled = !busy && pending == null,
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (restored) NativeStatus("Wiederhergestellter Entwurf", NativeStatusTone.Attention)
            if (pending == null && (id.isNotEmpty() || restored)) {
                if (id.isNotEmpty())
                    NativeTextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                try {
                                    val latest =
                                        controller.call(
                                            if (section == "offers") "get_offer"
                                            else "get_offer_template",
                                            JSONObject()
                                                .put(
                                                    if (section == "offers") "offer_id"
                                                    else "entry_id",
                                                    id,
                                                ),
                                        )
                                    val data = financeData(latest)
                                    val current =
                                        data.optJSONObject(
                                            if (section == "offers") "offer" else "entry"
                                        )
                                    if (
                                        current == null ||
                                            (section == "offers" && !OfferRules.editable(current))
                                    )
                                        throw IllegalArgumentException(
                                            "Der aktuelle Beleg kann nicht als Entwurf bearbeitet werden."
                                        )
                                    comparison = current
                                    comparisonHash =
                                        latest.optString(
                                            "record_hash",
                                            data.optString("record_hash"),
                                        )
                                    reviewed = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (f: Exception) {
                                    error = f.message.orEmpty()
                                }
                            }
                        },
                    ) {
                        Text("Aktuellen Stand zum Vergleich lesen")
                    }
                comparison?.let { current ->
                    Text(
                        "Aktuell gespeicherter Stand",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FinanceDraftSummary(controller, current, section)
                    Text("Ihre erhaltenen Eingaben stehen darunter und bleiben bestehen.")
                }
                if (comparison != null || id.isEmpty()) {
                    FinanceCheck(
                        "Ich habe diesen Entwurf, seine Beziehungen und den aktuellen Bestand verglichen und übernehme ihn ausdrücklich als neue Bearbeitung.",
                        reviewed,
                    ) {
                        reviewed = it
                    }
                    NativeTextButton(
                        enabled = reviewed && !busy,
                        onClick = {
                            scope.launch {
                                try {
                                    if (id.isNotEmpty()) observedHash = comparisonHash
                                    request = UUID.randomUUID().toString()
                                    controller.adoptDraft(
                                        key,
                                        JSONObject()
                                            .put("record", form)
                                            .put("record_hash", observedHash)
                                            .put("request_id", request),
                                    )
                                    pendingTool = ""
                                    restored = false
                                    reviewed = false
                                    comparison = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (f: Exception) {
                                    error = f.message.orEmpty()
                                }
                            }
                        },
                    ) {
                        Text("Geprüften Entwurf neu übernehmen")
                    }
                }
            }
            if (pending != null) NativeInspectionBanner(inspection) { inspectionAttempt++ }
            if (restored) NativeRestoreBanner()

            if (pending == null) {
                if (section == "catalog") {
                    for ((name, label) in
                        listOf(
                            "title" to "Bezeichnung",
                            "category" to "Kategorie",
                            "description" to "Beschreibung",
                            "unit" to "Einheit",
                            "default_quantity" to "Standardmenge (optional)",
                            "default_price" to "Einzelpreis (offen lassen, wenn unbekannt)",
                            "assumptions" to "Annahmen",
                            "exclusions" to "Ausschlüsse",
                            "legal_notes" to "Rechtliche Hinweise",
                        )) NativeField(
                        label,
                        if (form.isNull(name)) "" else form.optString(name),
                        { field(name, it) },
                        multiline =
                            name in
                                listOf("description", "assumptions", "exclusions", "legal_notes"),
                        enabled = !busy,
                    )
                    FinanceChoice(
                        "Positionsart",
                        form.optString("pricing_type", "unit"),
                        listOf("unit" to "Einheitspreis", "text" to "Text"),
                    ) {
                        field("pricing_type", it)
                    }
                    FinanceCheck(
                        "Preis ausdrücklich bestätigt",
                        form.optBoolean("price_confirmed"),
                    ) {
                        field("price_confirmed", it)
                    }
                    FinanceCheck("Baustein aktiv", form.optBoolean("active", true)) {
                        field("active", it)
                    }
                } else
                    when (step) {
                        0 -> {
                            OutlinedCard(
                                Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                border =
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Empfänger und Auftrag",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    NativeRecordPicker(
                                        controller,
                                        "customers",
                                        "Kunde",
                                        form.optString("customer_id"),
                                    ) {
                                        field("customer_id", it)
                                    }
                                    NativeRecordPicker(
                                        controller,
                                        "customers",
                                        "Abweichender Rechnungsempfänger (optional)",
                                        form.optString("billing_customer_id"),
                                    ) {
                                        field("billing_customer_id", it)
                                    }
                                    NativeRecordPicker(
                                        controller,
                                        "orders",
                                        "Auftrag (optional)",
                                        form.optString("order_id"),
                                        customerId = form.optString("customer_id"),
                                    ) {
                                        field("order_id", it)
                                    }
                                }
                            }
                            NativeContext(
                                controller,
                                form.optJSONObject("_source_context") ?: JSONObject(),
                            )
                            NativeField(
                                "Titel",
                                form.optString("title"),
                                { field("title", it) },
                                required = true,
                                enabled = !busy,
                            )
                            FinanceChoice(
                                "Dokumentart",
                                form.optString("document_type"),
                                OfferContract.types().map { it to OfferRules.label(it) },
                            ) {
                                field("document_type", it)
                                field("binding_confirmed", false)
                            }
                            NativeInfoRow(
                                "Zeitraum und Bedingungen",
                                "Gültigkeit, Ausführung, Steuern und Hinweise prüfen",
                                onClick = { termsOpen = !termsOpen },
                            )
                            if (termsOpen) {
                                NativeDateField(
                                    "Angebotsdatum",
                                    form.optString("issue_date"),
                                    { field("issue_date", it) },
                                    required = true,
                                    enabled = !busy,
                                )
                                NativeDateField(
                                    "Gültig bis",
                                    form.optString("valid_until"),
                                    { field("valid_until", it) },
                                    required = true,
                                    enabled = !busy,
                                )
                                NativeField(
                                    "Ausführungszeitraum",
                                    form.optString("execution_window"),
                                    { field("execution_window", it) },
                                    required = true,
                                    enabled = !busy,
                                )
                                NativeField(
                                    "Einsatzort",
                                    form.optString("location"),
                                    { field("location", it) },
                                    enabled = !busy,
                                )
                                NativeField(
                                    "Zahlungsbedingungen",
                                    form.optString("payment_terms"),
                                    { field("payment_terms", it) },
                                    required = true,
                                    multiline = true,
                                    enabled = !busy,
                                )
                                FinanceChoice(
                                    "Steuermodus",
                                    form.optString("tax_mode"),
                                    listOf(
                                        "small_business" to "Kleinunternehmer",
                                        "standard" to "Umsatzsteuer",
                                    ),
                                ) {
                                    field("tax_mode", it)
                                }
                                FinanceChoice(
                                    "Kundentyp",
                                    form.optString("customer_type"),
                                    listOf(
                                        "b2b" to "Unternehmen (B2B)",
                                        "consumer" to "Verbraucher",
                                    ),
                                ) {
                                    field("customer_type", it)
                                }
                                FinanceChoice(
                                    "Vertragsweg",
                                    form.optString("contract_channel"),
                                    listOf(
                                        "on_premise" to "Im Geschäft",
                                        "distance" to "Fernabsatz",
                                        "off_premise" to "Außerhalb der Geschäftsräume",
                                    ),
                                ) {
                                    field("contract_channel", it)
                                }
                                if (form.optString("customer_type") == "consumer") {
                                    NativeField(
                                        "Verbraucherinformationen / Rücktritt",
                                        form.optString("consumer_information"),
                                        { field("consumer_information", it) },
                                        multiline = true,
                                        enabled = !busy,
                                    )
                                    FinanceCheck(
                                        "Verbraucherinformationen ausdrücklich geprüft",
                                        form.optBoolean("consumer_information_confirmed"),
                                    ) {
                                        field("consumer_information_confirmed", it)
                                    }
                                }
                                NativeField(
                                    "Bindungshinweis",
                                    form.optString("binding_notice"),
                                    { field("binding_notice", it) },
                                    multiline = true,
                                    enabled = !busy,
                                )
                                val notice =
                                    OfferContract.type(form.optString("document_type"))
                                        .optString("notice")
                                if (notice.isNotEmpty())
                                    NativeInfoRow(
                                        "Hinweis zur gewählten Dokumentart",
                                        notice,
                                        onClick = { field("binding_notice", notice) },
                                    )
                                FinanceCheck(
                                    "Bindungsart und Hinweistext geprüft",
                                    form.optBoolean("binding_confirmed"),
                                ) {
                                    field("binding_confirmed", it)
                                }
                                NativeField(
                                    "Annahmen",
                                    form.optString("assumptions"),
                                    { field("assumptions", it) },
                                    multiline = true,
                                    enabled = !busy,
                                )
                                NativeField(
                                    "Ausschlüsse",
                                    form.optString("exclusions"),
                                    { field("exclusions", it) },
                                    multiline = true,
                                    enabled = !busy,
                                )
                                FinanceCheck(
                                    "Leistungsumfang, Annahmen und Ausschlüsse geprüft",
                                    form.optBoolean("scope_confirmed"),
                                ) {
                                    field("scope_confirmed", it)
                                }
                                NativeField(
                                    "Kopftext",
                                    form.optString("header_text"),
                                    { field("header_text", it) },
                                    multiline = true,
                                    enabled = !busy,
                                )
                                NativeField(
                                    "Fußtext",
                                    form.optString("footer_text"),
                                    { field("footer_text", it) },
                                    multiline = true,
                                    enabled = !busy,
                                )
                                NativeField(
                                    "Notizen",
                                    form.optString("notes"),
                                    { field("notes", it) },
                                    multiline = true,
                                    enabled = !busy,
                                )
                                FinanceCheck(
                                    "Synthetischer Testdatensatz (nur Testkunden)",
                                    form.optBoolean("test_record"),
                                ) {
                                    field("test_record", it)
                                }
                            }
                            if (section == "offers") {
                                NativeOutlinedButton(
                                    enabled = form.optString("customer_id").isNotEmpty(),
                                    onClick = {
                                        scope.launch {
                                            try {
                                                offerContext =
                                                    controller.query(
                                                        "get_offer_context",
                                                        JSONObject()
                                                            .put(
                                                                "customer_id",
                                                                form.getString("customer_id"),
                                                            ),
                                                    )
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (f: Exception) {
                                                error = f.message.orEmpty()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Kundenkontext und bestätigte Preise lesen")
                                }
                                offerContext?.let { context ->
                                    FinanceValues(
                                        context.optJSONObject("customer"),
                                        listOf(
                                            "name" to "Einsatzkunde",
                                            "street" to "Straße",
                                            "postal_code" to "Postleitzahl",
                                            "city" to "Ort",
                                            "notes" to "Hinweise",
                                            "garden_area" to "Gartenfläche",
                                        ),
                                    )
                                    val orders = context.optJSONArray("open_orders") ?: JSONArray()
                                    for (i in 0 until orders.length()) FinanceValues(
                                        orders.optJSONObject(i),
                                        listOf(
                                            "title" to "Offener Auftrag",
                                            "location" to "Einsatzort",
                                        ),
                                    )
                                }
                            }
                        }
                        1 -> {
                            FinancePositions(form.optJSONArray("positions") ?: JSONArray()) {
                                field("positions", it)
                            }
                            NativeOutlinedButton(
                                onClick = {
                                    sourceVisible = true
                                    sourceOffset = 0
                                },
                                Modifier.fillMaxWidth(),
                                enabled = !busy,
                            ) {
                                Text("Aus Leistungskatalog hinzufügen")
                            }
                            if (sourceVisible) {
                                LaunchedEffect(
                                    section,
                                    sourceOffset,
                                    sourceSearch,
                                    form.optString("customer_id"),
                                    form.optJSONObject("_source_context")?.toString(),
                                ) {
                                    sourceRows = emptyList()
                                    sourceLoading = true
                                    sourceError = ""
                                    try {
                                        sourceRows =
                                            financeRows(
                                                controller.query(
                                                    "search_offer_templates",
                                                    JSONObject()
                                                        .put("query", sourceSearch)
                                                        .put("offset", sourceOffset)
                                                        .put("limit", 40),
                                                ),
                                                "entries",
                                            )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (f: Exception) {
                                        sourceError = f.message.orEmpty()
                                    } finally {
                                        sourceLoading = false
                                    }
                                }
                                NativeSearchDialog(
                                    "Leistungsbausteine",
                                    sourceSearch,
                                    {
                                        sourceSearch = it
                                        sourceOffset = 0
                                    },
                                    sourceRows.size,
                                    loading = sourceLoading,
                                    error = sourceError,
                                    pageCount = true,
                                    searchLabel = "Bausteine suchen",
                                    searchEnabled = true,
                                    onDismiss = { sourceVisible = false },
                                ) { close ->
                                    items(sourceRows, key = { it.getString("id") }) { source ->
                                        NativeTextButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val lines =
                                                            JSONArray(
                                                                (form.optJSONArray("positions")
                                                                        ?: JSONArray())
                                                                    .toString()
                                                            )
                                                        val selected =
                                                            controller.query(
                                                                "get_offer_template",
                                                                JSONObject()
                                                                    .put(
                                                                        "entry_id",
                                                                        source.getString("id"),
                                                                    ),
                                                            )
                                                        val entry = selected.getJSONObject("entry")
                                                        val line =
                                                            JSONObject()
                                                                .put(
                                                                    "title",
                                                                    entry.optString("title"),
                                                                )
                                                                .put(
                                                                    "description",
                                                                    entry.optString("description"),
                                                                )
                                                                .put(
                                                                    "unit",
                                                                    entry.optString("unit"),
                                                                )
                                                                .put(
                                                                    "quantity",
                                                                    entry.opt("default_quantity")
                                                                        ?: JSONObject.NULL,
                                                                )
                                                                .put(
                                                                    "price",
                                                                    if (
                                                                        entry.optBoolean(
                                                                            "price_confirmed"
                                                                        )
                                                                    )
                                                                        entry.opt("default_price")
                                                                            ?: JSONObject.NULL
                                                                    else JSONObject.NULL,
                                                                )
                                                        if (
                                                            entry.optBoolean("price_confirmed") &&
                                                                !entry.isNull("default_price")
                                                        )
                                                            line
                                                                .put(
                                                                    "catalog_entry_id",
                                                                    entry.getString("id"),
                                                                )
                                                                .put(
                                                                    "catalog_hash",
                                                                    selected.getString(
                                                                        "record_hash"
                                                                    ),
                                                                )
                                                                .put("price_basis", "catalog")
                                                        lines.put(line)
                                                        field("positions", lines)
                                                        close()
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (f: Exception) {
                                                        sourceError = f.message.orEmpty()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                "${source.optString("title")} · ${financeMoney(source.opt("default_price"))}"
                                            )
                                        }
                                    }
                                    item {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            NativeTextButton(
                                                enabled = sourceOffset > 0,
                                                onClick = {
                                                    sourceOffset =
                                                        (sourceOffset - 40).coerceAtLeast(0)
                                                },
                                            ) {
                                                Text("Vorherige Quellen")
                                            }
                                            NativeTextButton(
                                                enabled = sourceRows.size == 40,
                                                onClick = { sourceOffset += 40 },
                                            ) {
                                                Text("Weitere Quellen")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            Text(
                                form.optString("title").ifEmpty { "Titel offen" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            NativeStatus(
                                if (openItems.isEmpty()) "Bereit zur vollständigen Prüfung"
                                else "Angaben ergänzen",
                                if (openItems.isEmpty()) NativeStatusTone.Neutral
                                else NativeStatusTone.Attention,
                            )
                            FinanceDraftSummary(controller, form, section)
                            for ((target, label) in openItems) NativeInfoRow(
                                label,
                                if (target == 1) "Positionen ergänzen" else "Angaben ergänzen",
                                onClick = {
                                    step = target
                                    if (target == 0) termsOpen = true
                                },
                            )
                            if (openItems.isEmpty())
                            else {
                                NativeButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("PDF erstellen")
                                }
                            }
                        }
                    }
            } else FinancePositionSummary(form.optJSONArray("positions"))
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            NativeTextButton(enabled = !busy, onClick = onClose) { Text("Später weiterbearbeiten") }
            NativeTextButton(enabled = !busy && pending == null, onClick = { discard = true }) {
                Text("Entwurf verwerfen")
            }
        }
        if (pending != null) NativeActionBar("Schließen", onClose, enabled = !busy)
        else
            NativeActionBar(
                primaryLabel =
                    when {
                        busy -> "Speichern …"
                        pending != null -> "Ergebnis prüfen"
                        section == "catalog" -> "Baustein speichern"
                        step == 0 -> "Weiter zu Leistungen"
                        step == 1 -> "Weiter zur Prüfung"
                        openItems.isNotEmpty() -> "Angaben ergänzen"
                        else -> "Vollständig prüfen"
                    },
                onPrimary = {
                    when {
                        pending != null || section == "catalog" -> save()
                        step < 2 -> step++
                        openItems.isNotEmpty() -> {
                            step = openItems.first().first
                            termsOpen = true
                        }
                        else -> save(reviewAfterSave = true)
                    }
                },
                secondaryLabel = if (section == "offers") "Entwurf speichern" else "Zurück",
                onSecondary = { if (section == "offers") save() else onClose() },
                enabled = !busy && loaded,
                secondaryEnabled = !busy && loaded && pending == null,
            )
    }
}

@Composable
private fun FinancePositions(values: JSONArray, onChange: (JSONArray) -> Unit) {
    var removed by remember { mutableStateOf<Pair<Int, JSONObject>?>(null) }
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    fun update(index: Int, key: String, value: Any?) {
        val next = JSONArray(values.toString())
        val line = next.getJSONObject(index)
        line.put(key, value ?: JSONObject.NULL)
        for (name in listOf("net", "tax", "net_total", "tax_total", "gross_total")) line.remove(
            name
        )
        onChange(next)
    }
    Text("Positionen", style = MaterialTheme.typography.titleLarge)
    for (index in 0 until values.length()) {
        val row = values.getJSONObject(index)
        val missing = buildList {
            if (row.isNull("quantity") || row.optString("quantity").isBlank()) add("Menge fehlt")
            if (row.isNull("price") || row.optString("price").isBlank()) add("Preis fehlt")
        }
        NativeInfoRow(
            "${index+1}. ${row.optString("title").ifBlank {"Eigene Position"}}",
            if (missing.isEmpty())
                "${nativeNumber(row.opt("quantity"))} ${row.optString("unit")} × ${financeMoney(row.opt("price"))}"
            else missing.joinToString(" · "),
            onClick = { expanded = if (expanded == index) -1 else index },
        )
        if (expanded == index)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Position ${index+1}", style = MaterialTheme.typography.titleMedium)
                    NativeField(
                        "Bezeichnung",
                        row.optString("title"),
                        { update(index, "title", it) },
                        required = true,
                    )
                    NativeField(
                        "Beschreibung",
                        row.optString("description"),
                        { update(index, "description", it) },
                        multiline = true,
                    )
                    for ((key, label) in
                        listOf(
                            "quantity" to "Menge",
                            "unit" to "Einheit",
                            "price" to "Einzelpreis in EUR",
                            "tax_rate" to "Steuersatz in %",
                        )) NativeField(
                        label,
                        if (row.isNull(key)) "" else row.optString(key),
                        { value ->
                            val next = JSONArray(values.toString())
                            val line = next.getJSONObject(index)
                            line.put(key, value)
                            for (name in
                                listOf("net", "tax", "net_total", "tax_total", "gross_total")) line
                                .remove(name)
                            if (key == "quantity" && line.has("source_quantity_decimal"))
                                line.put("source_quantity_decimal", value.replace(',', '.'))
                            onChange(next)
                        },
                        required = key != "tax_rate",
                    )
                    FinanceChoice(
                        "Preisgrundlage",
                        row.optString("price_basis", "confirmed"),
                        listOf(
                            "confirmed" to "Bestätigt",
                            "catalog" to "Freigegebener Katalogpreis",
                            "calculated" to "Berechnet",
                            "estimated" to "Geschätzt",
                        ),
                    ) { chosen ->
                        val next = JSONArray(values.toString())
                        val line = next.getJSONObject(index)
                        line.put("price_basis", chosen)
                        if (chosen != "catalog") {
                            line.remove("catalog_entry_id")
                            line.remove("catalog_hash")
                        }
                        onChange(next)
                    }
                    if (row.optString("catalog_entry_id").isEmpty())
                        FinanceCheck(
                            "Diesen Einzelpreis ausdrücklich bestätigt",
                            row.optString("price_source") == "user_confirmed",
                        ) { checked ->
                            val next = JSONArray(values.toString())
                            if (checked)
                                next.getJSONObject(index).put("price_source", "user_confirmed")
                            else next.getJSONObject(index).remove("price_source")
                            onChange(next)
                        }
                    NativeField(
                        "Preisberechnung / Schätzgrundlage",
                        row.optString("price_note"),
                        { update(index, "price_note", it) },
                        multiline = true,
                    )
                    if (row.optString("price_basis") == "calculated")
                        for ((name, label) in
                            listOf(
                                "base_price" to "Berechnungsbasis",
                                "factor" to "Faktor",
                                "surcharge" to "Zuschlag",
                            )) NativeField(
                            label,
                            row.optJSONObject("calculation")?.optString(name).orEmpty(),
                            { value ->
                                val calculation =
                                    financeCopy(row.optJSONObject("calculation") ?: JSONObject())
                                        .put(name, value)
                                update(index, "calculation", calculation)
                            },
                        )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NativeTextButton(
                            enabled = index > 0,
                            onClick = {
                                val next = JSONArray()
                                for (i in 0 until values.length()) next.put(
                                    values.get(
                                        if (i == index - 1) index
                                        else if (i == index) index - 1 else i
                                    )
                                )
                                onChange(next)
                            },
                        ) {
                            Text("Nach oben")
                        }
                        NativeTextButton(
                            onClick = {
                                removed = index to financeCopy(row)
                                val next = JSONArray()
                                for (i in 0 until values.length()) if (i != index)
                                    next.put(values.get(i))
                                onChange(next)
                            }
                        ) {
                            Text("Entfernen")
                        }
                    }
                }
            }
    }
    removed?.let { (originalIndex, line) ->
        NativeTextButton(
            onClick = {
                val next = JSONArray()
                val position = originalIndex.coerceAtMost(values.length())
                for (index in 0..values.length()) {
                    if (index == position) next.put(financeCopy(line))
                    if (index < values.length()) next.put(values.get(index))
                }
                removed = null
                onChange(next)
            }
        ) {
            Text("Entfernen rückgängig")
        }
    }
    NativeOutlinedButton(
        onClick = {
            expanded = values.length()
            onChange(
                JSONArray(values.toString())
                    .put(
                        JSONObject()
                            .put("title", "")
                            .put("description", "")
                            .put("quantity", JSONObject.NULL)
                            .put("price", JSONObject.NULL)
                            .put("unit", "")
                            .put("tax_rate", JSONObject.NULL)
                    )
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Eigene Position hinzufügen")
    }
    if (
        (0 until values.length()).any {
            values.getJSONObject(it).isNull("quantity") || values.getJSONObject(it).isNull("price")
        }
    )
        Text("Summe noch offen")
}
