package at.hausgarten.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NativeField(label: String, value: String, onValueChange: (String) -> Unit, required: Boolean = false, multiline: Boolean = false, search: Boolean = false, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null, enabled: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val searching = search || label in listOf("Suchen", "Suche", "Bausteine suchen", "Text suchen", "Vorschläge durchsuchen")
    val finish = { keyboard?.hide(); focus.clearFocus(); Unit }
    val displayedLabel = label + if (required) " *" else ""
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth().semantics {
        testTagsAsResourceId = true; testTag = "native-field:$displayedLabel"
    }, label = { Text(displayedLabel) },
        enabled = enabled, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else if (searching) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { finish(); onSearch?.invoke() }, onDone = { finish() }))
}

@Composable
internal fun NativeChoice(label: String, selected: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text(label + ": " + (options.firstOrNull { it.first == selected }?.second ?: "Auswählen")) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) { items(options) { option -> TextButton({ onChange(option.first); open = false }, Modifier.fillMaxWidth()) { Text(option.second) } } }
    }, confirmButton = { TextButton({ open = false }) { Text("Abbrechen") } })
}

/** Optional suggestions never replace a free input with a mandatory catalog value. */
@Composable
internal fun NativeSuggestions(label: String, options: List<String>, onChoose: (String) -> Unit) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    var search by rememberSaveable(label) { mutableStateOf("") }
    TextButton({ open = !open }) { Text(label + if (open) " ▴" else " ▾") }
    val matches = options.filter { it.contains(search, ignoreCase = true) }
    if (open) NativeSearchDialog(label, search, { search = it }, matches.size, searchLabel = "Vorschläge durchsuchen", onDismiss = { open = false }) { close ->
        items(matches) { value -> TextButton({ onChoose(value); close() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(value) } }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun NativeAmountPresets(label: String, inputLabel: String, value: String, presets: List<String>, suffix: String, change: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { preset ->
                FilterChip(selected = value.replace(',', '.').toDoubleOrNull() == preset.toDoubleOrNull(), onClick = { change(preset) }, label = { Text("$preset $suffix".trim()) })
            }
        }
        OutlinedTextField(value, change, Modifier.fillMaxWidth().semantics {
            testTagsAsResourceId = true; testTag = "native-field:$inputLabel"
        }, label = { Text(inputLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
    }
}

/** One bounded, keyboard-aware selection surface for customer, planning and media paths. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeSearchDialog(title: String, search: String, onSearch: (String) -> Unit, total: Int,
    loading: Boolean = false, error: String = "", pageCount: Boolean = false, searchLabel: String = "Suchen", searchEnabled: Boolean = true, onDismiss: () -> Unit,
    content: LazyListScope.(close: () -> Unit) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val imeVisible = WindowInsets.isImeVisible || WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val close = { keyboard?.hide(); focus.clearFocus(); onDismiss() }
        val count = if (loading) "Wird geladen …" else "$total Treffer" + if (pageCount && !imeVisible) " auf dieser Seite" else ""
        Surface(Modifier.fillMaxSize()) {
            // Keep the same text-field node when IME/rotation changes placement;
            // replacing a Column with a Row would dispose its focused editor.
            Layout(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 12.dp, vertical = 4.dp), content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!imeVisible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(close) { Text("Schließen") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (searchEnabled) NativeField(searchLabel, search, onSearch, search = true, modifier = Modifier.weight(1f))
                        if (imeVisible) IconButton(close, Modifier.semantics { contentDescription = "Schließen" }) { Text("×") }
                    }
                    Text(count, Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.labelLarge)
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
                    else if (!loading && total == 0) item { Text("Keine Treffer. Suchtext oder Filter ändern.", Modifier.padding(vertical = 8.dp)) }
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    content(close)
                }
            }) { children, limits ->
                val sideBySide = landscape && imeVisible
                val gap = if (sideBySide) 12.dp.roundToPx() else 0
                val controlWidth = if (sideBySide) ((limits.maxWidth-gap)*0.42f).toInt() else limits.maxWidth
                val controls = children[0].measure(Constraints(minWidth = controlWidth, maxWidth = controlWidth, maxHeight = limits.maxHeight))
                val resultsWidth = if (sideBySide) limits.maxWidth-controlWidth-gap else limits.maxWidth
                val resultsHeight = if (sideBySide) limits.maxHeight else (limits.maxHeight-controls.height).coerceAtLeast(0)
                val results = children[1].measure(Constraints.fixed(resultsWidth, resultsHeight))
                layout(limits.maxWidth, limits.maxHeight) {
                    controls.placeRelative(0,0)
                    results.placeRelative(if (sideBySide) controlWidth+gap else 0, if (sideBySide) 0 else controls.height)
                }
            }
        }
    }
}

@Composable
internal fun NativeRecordDialog(visible: Boolean, controller: NativeAppController, collection: String, title: String,
    customerId: String = "", allowEmpty: Boolean = true, onDismiss: () -> Unit, onChoose: (NativeListRow?) -> Unit) {
    var search by rememberSaveable(collection, title) { mutableStateOf("") }
    var offset by rememberSaveable(collection, title) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    if (!visible) return
    val page by produceState<NativeListPage?>(null, collection, customerId, search, offset, retry) {
        value = null; error = ""
        try { controller.store.nativeData().watchList(collection, search = search, customerId = customerId, offset = offset, limit = 30).collect { value = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Einträge nicht lesbar. Bitte erneut versuchen." }
    }
    NativeSearchDialog(title, search, { search = it; offset = 0 }, page?.total ?: 0, loading = page == null && error.isEmpty(), error = error, onDismiss = onDismiss) { close ->
        if (error.isNotBlank()) item { TextButton({ retry++ }) { Text("Erneut versuchen") } }
        items(page?.rows ?: emptyList(), key = { it.id }) { row ->
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChoose(row); close() }.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge)
                val detail = listOf(row.address, row.subtitle).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                if (detail.isNotBlank()) Text(nativeDisplayText(detail), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (offset > 0 || page?.hasMore == true) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ offset = maxOf(0, offset - 30) }, enabled = offset > 0) { Text("Vorherige") }
                TextButton({ offset += 30 }, enabled = page?.hasMore == true) { Text("Weitere") }
            }
        }
        if (allowEmpty) item { TextButton({ onChoose(null); close() }, Modifier.fillMaxWidth()) { Text("Keine Zuordnung") } }
    }
}

internal fun nativeReferenceLabel(row: JSONObject, collection: String): String {
    val title = row.optString("name").ifBlank { row.optString("title").ifBlank { row.optString("activity").ifBlank { "Ausgewählter Eintrag" } } }
    val address = if (collection == "customers") row.optString("address").ifBlank {
        listOf(row.optString("street"), listOf(row.optString("postal_code"), row.optString("city")).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ")
    } else ""
    return listOf(title, address).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
internal fun NativeRecordPicker(controller: NativeAppController, collection: String, label: String, value: String, customerId: String = "", onValueChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var selectedLabel by remember(value) { mutableStateOf(if (value.isEmpty()) "Nicht zugeordnet" else "Wird geladen …") }
    LaunchedEffect(value, collection) {
        selectedLabel = if (value.isEmpty()) "Nicht zugeordnet" else try {
            val row = controller.store.nativeData().detail(collection, value)
            row?.let { nativeReferenceLabel(it, collection) } ?: "Eintrag nicht mehr vorhanden"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "Ausgewählter Eintrag nicht lesbar" }
    }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text("$label: $selectedLabel") }
    NativeRecordDialog(open, controller, collection, label, customerId = customerId, onDismiss = { open = false }) { onValueChange(it?.id.orEmpty()) }
}

private val nativeLabels = mapOf(
    "name" to "Name", "title" to "Bezeichnung", "description" to "Beschreibung", "notes" to "Notizen", "reason" to "Begründung",
    "existing_order_id" to "Vorhandener Auftrag (optional)", "customer_id" to "Kunde", "billing_customer_id" to "Rechnungsempfänger", "order_id" to "Auftrag", "property_id" to "Objekt", "visit_id" to "Einsatz",
    "equipment_id" to "Gerät", "material_id" to "Material", "care_plan_id" to "Pflegeplan", "appointment_id" to "Termin", "worklog_id" to "Tätigkeit",
    "phone" to "Telefon", "email" to "E-Mail", "company" to "Firma", "first_name" to "Vorname", "last_name" to "Nachname", "street" to "Straße",
    "postal_code" to "Postleitzahl", "city" to "Ort", "country" to "Land", "address" to "Adresse", "location" to "Einsatzort", "garden_area" to "Gartenfläche",
    "disposal_cost" to "Entsorgung (€)", "activity" to "Tätigkeit", "work_date" to "Arbeitsdatum", "date" to "Datum", "date_from" to "Ab Datum", "date_to" to "Bis Datum", "hours" to "Stunden",
    "start_time" to "Beginn", "end_time" to "Ende", "break_minutes" to "Pause in Minuten", "material" to "Material", "material_cost" to "Materialkosten",
    "kilometers" to "Kilometer", "customer_visible" to "Für Kunden sichtbar", "allow_overnight" to "Arbeit über Mitternacht", "billed" to "Abgerechnet",
    "active" to "Aktiv", "archived" to "Archiviert", "status" to "Status", "updates" to "Änderungen", "confirmed" to "Ich bestätige diese Aktion",
    "positions" to "Positionen", "quantity" to "Menge", "unit" to "Einheit", "unit_price" to "Einzelpreis", "tax_rate" to "Steuersatz", "tax_mode" to "Steuerregelung",
    "invoice_date" to "Rechnungsdatum", "service_start" to "Leistungsbeginn", "service_end" to "Leistungsende", "payment_days" to "Zahlungsfrist in Tagen",
    "header_text" to "Einleitung", "footer_text" to "Schlusstext", "caption" to "Bildunterschrift", "capture_time" to "Aufgenommen am", "role" to "Fotorolle",
    "planned_minutes" to "Geplante Minuten", "starts_at" to "Beginn", "ends_at" to "Ende", "timezone" to "Zeitzone", "recurrence" to "Wiederholung",
    "interval_days" to "Abstand in Tagen", "anchor_date" to "Beginn", "condition" to "Zustand", "purchase_price_minor" to "Einstandspreis in Cent",
    "billing_price_minor" to "Abrechnungspreis in Cent", "quantity_decimal" to "Menge", "movement_kind" to "Buchungsart", "performed_on" to "Erledigt am",
    "amount_decimal" to "Betrag", "amount_minor" to "Betrag in Cent", "paid_on" to "Zahlungsdatum", "payment_method" to "Zahlungsart", "document_type" to "Dokumentart",
    "window_start" to "Von Uhrzeit", "window_end" to "Bis Uhrzeit", "scheduled_date" to "Geplant am", "billing_mode" to "Abrechnungsweg", "query" to "Suche"
)
internal fun nativeLabel(key: String): String = nativeLabels[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
internal fun nativeValue(value: String): String = mapOf("active" to "Aktiv", "inactive" to "Inaktiv", "open" to "Offen", "completed" to "Erledigt", "cancelled" to "Abgesagt", "draft" to "Entwurf", "ready" to "Einsatzbereit", "defective" to "Defekt", "maintenance" to "Wartung", "profile" to "Profilfoto", "documentation" to "Dokumentation", "before" to "Vorher", "during" to "Währenddessen", "after" to "Nachher", "damage" to "Schaden", "other" to "Sonstiges", "small_business" to "Kleinunternehmer", "standard" to "Umsatzsteuer", "direct" to "Kunde selbst", "via_customer" to "Anderer Rechnungsempfänger", "fixed_price" to "Fixpreisangebot", "time_material" to "Regieangebot", "estimate_binding" to "Verbindlicher Kostenvoranschlag", "estimate_nonbinding" to "Unverbindlicher Kostenvoranschlag")[value] ?: value
private fun technical(key: String) = key.startsWith("_") || key.startsWith("expected_") || key.endsWith("_id") || key.endsWith("_ids") || key.endsWith("_hash") || key.endsWith("_sha256") || key in setOf("id", "source", "created_at", "updated_at", "formatVersion", "schema_version", "local_revision", "test_record", "revision", "deleted_at", "device_id", "cloud_confirmed", "untrusted_business_data", "security_notice", "pdf_base64", "data_base64", "chunk_base64", "token", "installation_id", "restore_epoch", "protocol_version")

/** Read-only business values, never a raw JSON editor or a binary payload dump. */
@Composable
internal fun NativeBusinessValues(value: JSONObject, depth: Int = 0) {
    for (key in value.keys().asSequence().toList()) {
        if (technical(key)) continue
        when (val field = value.opt(key)) {
            is JSONObject -> if (depth < 3 && field.length() > 0) { Text(nativeLabel(key), style = MaterialTheme.typography.titleSmall); NativeBusinessValues(field, depth + 1) }
            is JSONArray -> if (depth < 3 && field.length() > 0) {
                Text(nativeLabel(key), style = MaterialTheme.typography.titleSmall)
                for (i in 0 until minOf(field.length(), 40)) when (val item = field.opt(i)) {
                    is JSONObject -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { NativeBusinessValues(item, depth + 1) } }
                    else -> Text(item?.toString() ?: "")
                }
                if (field.length() > 40) Text("Weitere ${field.length() - 40} Einträge sind in der zugehörigen Liste verfügbar.")
            }
            null, JSONObject.NULL -> Unit
            else -> if (field.toString().isNotBlank()) Text(nativeLabel(key).removeSuffix(" in Cent") + ": " + if (field is Boolean) { if (field) "Ja" else "Nein" } else when {
                key.endsWith("_minor") -> nativeNumber(field.toString().toBigDecimalOrNull()?.movePointLeft(2)) + " €"
                key in setOf("hours", "quantity", "quantity_decimal", "kilometers") -> nativeNumber(field)
                else -> nativeDisplayText(nativeValue(field.toString()))
            })
        }
    }
}

private fun referenceCollection(name: String): String? = mapOf("customer_id" to "customers", "billing_customer_id" to "customers", "default_billing_customer_id" to "customers", "order_id" to "orders", "existing_order_id" to "orders", "property_id" to "properties", "visit_id" to "visits", "work_record_id" to "worklogs", "worklog_id" to "worklogs", "equipment_id" to "equipment", "material_id" to "materials", "care_plan_id" to "care_plans", "appointment_id" to "appointments", "offer_id" to "offers", "invoice_id" to "invoices", "photo_id" to "photos")[name]

@Composable
private fun NativeSchemaField(controller: NativeAppController, name: String, spec: JSONObject, value: Any?, required: Boolean, change: (Any?) -> Unit, depth: Int = 0) {
    if (technical(name) && referenceCollection(name) == null) return
    val label = nativeLabel(name)
    val choices = spec.optJSONArray("enum")
    val collection = referenceCollection(name)
    when {
        collection != null -> NativeRecordPicker(controller, collection, label + if (required) " *" else "", if (value is String) value else "", onValueChange = change)
        spec.optString("type") == "boolean" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f)); Switch(value == true, change) }
        choices != null -> NativeChoice(label, value?.toString() ?: "", (0 until choices.length()).map { choices.getString(it) to nativeValue(choices.getString(it)) }, change)
        spec.optString("type") == "object" && depth < 4 -> {
            val obj = value as? JSONObject ?: JSONObject()
            val fields = spec.optJSONObject("properties") ?: JSONObject()
            val requiredFields = spec.optJSONArray("required") ?: JSONArray()
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                for (child in fields.keys().asSequence().toList()) NativeSchemaField(controller, child, fields.getJSONObject(child), obj.opt(child), (0 until requiredFields.length()).any { requiredFields.getString(it) == child }, {
                    val copy = JSONObject(obj.toString()); if (it == null) copy.remove(child) else copy.put(child, it); change(copy)
                }, depth + 1)
                if (fields.length() == 0) {
                    val keys = obj.keys().asSequence().filterNot { technical(it) }.toList()
                    for (key in keys) NativeField(nativeLabel(key), obj.optString(key), { change(JSONObject(obj.toString()).put(key, it)) })
                    if (keys.isEmpty()) Text("Hier sind keine zusätzlichen Angaben erforderlich.")
                }
            } }
        }
        spec.optString("type") == "array" && depth < 4 -> {
            val array = value as? JSONArray ?: JSONArray()
            val item = spec.optJSONObject("items") ?: JSONObject().put("type", "string")
            Text(label, style = MaterialTheme.typography.titleMedium)
            for (index in 0 until array.length()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                NativeSchemaField(controller, if (item.optString("type") == "object") "Eintrag ${index + 1}" else label, item, array.opt(index), true, {
                    val copy = JSONArray(array.toString()); copy.put(index, it ?: JSONObject.NULL); change(copy)
                }, depth + 1)
                TextButton({ val copy = JSONArray(); for (i in 0 until array.length()) if (i != index) copy.put(array.get(i)); change(copy) }) { Text("Eintrag entfernen") }
            } }
            OutlinedButton({ change(JSONArray(array.toString()).put(if (item.optString("type") == "object") JSONObject() else "")) }) { Text("Eintrag ergänzen") }
        }
        else -> {
            val numeric = spec.optString("type") in listOf("number", "integer")
            OutlinedTextField(value?.takeUnless { it == JSONObject.NULL }?.toString() ?: "", {
                if (numeric && it.isNotBlank()) { val parsed = if (spec.optString("type") == "integer") it.toLongOrNull() else it.replace(',', '.').toDoubleOrNull(); change(parsed ?: it) } else change(it)
            }, Modifier.fillMaxWidth(), label = { Text(label + if (required) " *" else "") }, minLines = if (name in listOf("notes", "description", "concern", "scope", "header_text", "footer_text")) 3 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
        }
    }
}

/** Rare actions reuse the registered business schema; primary workflows have their own native screens. */
@Composable
internal fun NativeActionForm(controller: NativeAppController, tool: String, title: String, initial: JSONObject = JSONObject(), onDone: (JSONObject) -> Unit = { controller.back() }) {
    val scope = rememberCoroutineScope()
    val defaultDraftKey = "action:$tool:" + initial.optString("entity_id", initial.optString("customer_id", initial.optString("invoice_id", initial.optString("offer_id"))))
    val draftKey = controller.route.args.optString("_draft_key", initial.optString("_draft_key", defaultDraftKey))
    var spec by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var text by rememberSaveable(tool, draftKey, initial.toString()) { mutableStateOf(initial.toString()) }
    var loaded by remember(tool, draftKey) { mutableStateOf(false) }
    var prepareRevision by remember(tool, draftKey) { mutableIntStateOf(0) }
    var busy by remember(tool, draftKey) { mutableStateOf(false) }
    var error by remember(tool, draftKey) { mutableStateOf("") }
    var response by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var pending by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var restored by remember(tool, draftKey) { mutableStateOf(false) }
    var reviewNeeded by remember(tool, draftKey) { mutableStateOf(false) }
    var comparison by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var comparisonAccepted by remember(tool, draftKey) { mutableStateOf(false) }
    var orderLoading by remember(tool, draftKey) { mutableStateOf(false) }
    var orderError by remember(tool, draftKey) { mutableStateOf("") }
    var orderObservation by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var orderRevision by remember(tool, draftKey) { mutableIntStateOf(0) }
    var updateBaseText by rememberSaveable(tool, draftKey) {
        mutableStateOf(if (tool in listOf("update_settings", "update_business_settings")) initial.optJSONObject("updates")?.toString() ?: "{}" else "{}")
    }
    LaunchedEffect(tool, draftKey, prepareRevision) {
        loaded = false
        try {
            spec = controller.spec(tool)
            val saved = controller.draft(draftKey)
            if (saved != null) {
                text = saved.optJSONObject("values")?.toString() ?: text
                pending = saved.optJSONObject("original_args")
                restored = saved.optBoolean("_restore_action_review_required")
                reviewNeeded = saved.optBoolean("review_required")
                updateBaseText = saved.optJSONObject("update_base")?.toString() ?: updateBaseText
            }
            val inputSchema = spec?.getJSONObject("inputSchema") ?: throw IllegalArgumentException("Aktion ist in dieser Appversion nicht verfügbar.")
            if (pending == null && !restored && !reviewNeeded) {
                val entered = JSONObject(text)
                val props = inputSchema.getJSONObject("properties")
                if (props.keys().asSequence().any { it.startsWith("expected_") && entered.optString(it).isEmpty() && it != "expected_order_hash" }) {
                    val observed = controller.actionObservation(tool, entered)
                    text = nativeEnrichProofs(inputSchema, entered, observed).toString()
                }
                text = nativeBooleanDefaults(inputSchema, JSONObject(text)).toString()
            }
            if (inputSchema.getJSONObject("properties").has("request_id") && !JSONObject(text).has("request_id")) text = JSONObject(text).put("request_id", UUID.randomUUID().toString()).toString()
            val entered = JSONObject(text)
            if (pending == null && !restored && !reviewNeeded && entered.optString("existing_order_id").isNotEmpty() && !entered.optString("expected_order_hash").matches(Regex("[0-9a-f]{64}"))) {
                try {
                    val selected = entered.getString("existing_order_id")
                    val observed = controller.call("get_order", JSONObject().put("order_id", selected))
                    text = entered.put("expected_order_hash", nativeExistingOrderProof(observed, selected)).toString()
                    orderObservation = observed
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { orderError = failure.message ?: "Der gewählte Auftrag konnte nicht gelesen werden." }
            }
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Aktion konnte nicht vorbereitet werden." }
    }
    fun draftValue(value: String, frozen: JSONObject? = pending): JSONObject = JSONObject()
        .put("values", JSONObject(value)).put("update_base", JSONObject(updateBaseText))
        .put("original_tool", tool).put("review_required", reviewNeeded)
        .apply { if (frozen != null) put("original_args", frozen) }
    fun persist(value: String, frozen: JSONObject? = pending) { controller.saveDraft(draftKey, draftValue(value, frozen)) }
    fun selectExistingOrder(chosen: String) {
        if (busy || pending != null || restored) return
        val inputSchema = spec?.optJSONObject("inputSchema") ?: return
        val revision = ++orderRevision
        val changed = nativeResetConfirmations(inputSchema, JSONObject(text))
        changed.remove("expected_order_hash")
        if (chosen.isEmpty()) changed.remove("existing_order_id") else changed.put("existing_order_id", chosen)
        text = changed.toString(); persist(text)
        comparison = null; comparisonAccepted = false; orderObservation = null; orderError = ""
        orderLoading = chosen.isNotEmpty()
        if (chosen.isEmpty()) return
        scope.launch {
            try {
                val observed = controller.call("get_order", JSONObject().put("order_id", chosen))
                val proof = nativeExistingOrderProof(observed, chosen)
                if (revision == orderRevision && JSONObject(text).optString("existing_order_id") == chosen) {
                    text = JSONObject(text).put("expected_order_hash", proof).toString()
                    orderObservation = observed; persist(text)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (revision == orderRevision) orderError = failure.message ?: "Der gewählte Auftrag konnte nicht gelesen werden."
            } finally { if (revision == orderRevision) orderLoading = false }
        }
    }
    suspend fun readComparison(arguments: JSONObject): JSONObject {
        val observed = controller.actionObservation(tool, arguments)
        val existing = arguments.optString("existing_order_id")
        if (existing.isNotEmpty()) {
            val order = controller.call("get_order", JSONObject().put("order_id", existing))
            observed.put("expected_order_hash", nativeExistingOrderProof(order, existing))
                .put("selected_order", order.optJSONObject("data") ?: order)
        }
        return observed
    }
    suspend fun perform(arguments: JSONObject) {
        busy = true; error = ""
        val readonly = spec?.optJSONObject("annotations")?.optBoolean("readOnlyHint", true) != false
        try {
            if (!nativeProofsReady(requireNotNull(spec).getJSONObject("inputSchema"), arguments)) throw IllegalArgumentException("Den benötigten aktuellen Vergleichsstand zuerst lesen und bewusst übernehmen.")
            if (!readonly) { pending = JSONObject(arguments.toString()); persist(text, pending); controller.flushDrafts() }
            val result = controller.call(tool, arguments)
            response = result.optJSONObject("data") ?: result
            if (!readonly) { controller.removeDraft(draftKey); pending = null; controller.message("Änderung lokal gespeichert."); onDone(result) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = failure.message ?: "Ergebnis ist ungeklärt. Dieselbe Aktion erneut prüfen."
            if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") {
                pending = null
                if (nativeNeedsComparison(failure.detail.optString("code"))) {
                    reviewNeeded = true; comparison = null; comparisonAccepted = false
                }
                persist(text, null)
            }
        } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (busy || orderLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loaded) {
            if (error.isBlank()) Text("Aktion wird vorbereitet …")
            else TextButton({ error = ""; prepareRevision++ }) { Text("Entwurf erneut lesen") }
            OutlinedButton({ controller.back() }, enabled = !busy) { Text("Zurück") }
            return@Column
        }
        val schema = requireNotNull(spec).getJSONObject("inputSchema")
        val properties = schema.getJSONObject("properties")
        val required = schema.optJSONArray("required") ?: JSONArray()
        val args = JSONObject(text)
        args.optJSONObject("_preview")?.let { NativeBusinessValues(it) }
        if (restored || pending != null) {
            Text(if (restored) "Dieser Entwurf stammt aus einer Wiederherstellung. Zuerst den vorhandenen Aktionsbeleg prüfen." else "Der ursprüngliche Speichervorgang ist noch ungeklärt. Seine Angaben bleiben unverändert.")
            NativeBusinessValues(args)
        }
        if (pending != null) Button(enabled = !busy && !orderLoading, onClick = { scope.launch {
            busy = true
            try {
                val original = requireNotNull(pending)
                val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", original))
                response = proof
                if (proof.optString("state") == "confirmed") { controller.removeDraft(draftKey); pending = null; controller.message("Der bereits gespeicherte Vorgang wurde gefunden."); onDone(proof) }
                else if (!restored && proof.optString("state") == "unknown") perform(original)
                else error = "Kein eindeutiger Speicherbeleg vorhanden. Die ursprünglichen Angaben bleiben erhalten."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Aktionsbeleg konnte nicht gelesen werden." } finally { busy = false }
        } }) { Text("Gespeicherten Vorgang prüfen") }
        if ((restored || reviewNeeded) && pending == null) {
            if (reviewNeeded) Text("Der gespeicherte Stand hat sich geändert. Ihre Eingaben bleiben erhalten. Vor erneutem Speichern den aktuellen Stand vergleichen und ausdrücklich übernehmen.")
            OutlinedButton(enabled = !busy && !orderLoading, onClick = { scope.launch {
                busy = true; comparison = null; comparisonAccepted = false
                try { comparison = readComparison(JSONObject(text)); error = "" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Aktueller Stand nicht lesbar." } finally { busy = false }
            } }) { Text("Aktuellen Stand zum Vergleichen lesen") }
            comparison?.let { current ->
                Text("Aktuell gespeicherter Stand", style = MaterialTheme.typography.titleMedium)
                NativeBusinessValues(current.optJSONObject("data") ?: current)
                current.optJSONObject("selected_order")?.let { Text("Gewählter Auftrag", style = MaterialTheme.typography.titleMedium); NativeBusinessValues(it) }
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(comparisonAccepted, onCheckedChange = { comparisonAccepted = it })
                    Text("Ich habe den aktuellen Stand mit meinen erhaltenen Eingaben verglichen.", Modifier.weight(1f).padding(top = 12.dp))
                }
                Button(enabled = !busy && !orderLoading && comparisonAccepted, onClick = { scope.launch {
                    busy = true
                    try {
                        val entered = JSONObject(text)
                        val rebased = nativeRebaseUpdates(entered, JSONObject(updateBaseText), current)
                        val reviewed = nativeResetConfirmations(schema, nativeEnrichProofs(schema, nativeWithoutExpectedProofs(schema, rebased.first), current))
                        if (!nativeProofsReady(schema, reviewed)) throw IllegalArgumentException("Der gelesene Stand enthält nicht alle benötigten Vergleichsnachweise. Keine neue Aktion wurde freigegeben.")
                        if (properties.has("request_id")) reviewed.put("request_id", UUID.randomUUID().toString())
                        val adopted = JSONObject().put("values", reviewed).put("update_base", rebased.second).put("original_tool", tool).put("review_required", false)
                        controller.adoptDraft(draftKey, adopted)
                        updateBaseText = rebased.second.toString(); text = reviewed.toString()
                        restored = false; reviewNeeded = false; comparison = null; comparisonAccepted = false; orderError = ""; error = ""
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Entwurf konnte nicht übernommen werden." } finally { busy = false }
                } }) { Text("Geprüfte Eingaben als neuen Entwurf fortsetzen") }
            }
            if (restored) TextButton(enabled = !busy && !orderLoading, onClick = { scope.launch {
                try { controller.removeDraft(draftKey); controller.back() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
            } }) { Text("Diesen ungespeicherten Entwurf verwerfen") }
        }
        if (!restored && pending == null) {
            for (key in properties.keys().asSequence().toList()) {
                NativeSchemaField(controller, key, properties.getJSONObject(key), args.opt(key), (0 until required.length()).any { required.getString(it) == key }, fieldChange@{ value ->
                    if (busy || pending != null) return@fieldChange
                    if (key == "existing_order_id") selectExistingOrder(value as? String ?: "")
                    else {
                        val changed = JSONObject(text); if (value == null) changed.remove(key) else changed.put(key, value)
                        text = changed.toString(); comparisonAccepted = false
                        if (referenceCollection(key) != null) comparison = null
                        persist(text)
                    }
                })
            }
            if (orderError.isNotEmpty()) {
                Text(orderError, color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy && !orderLoading, onClick = { selectExistingOrder(JSONObject(text).optString("existing_order_id")) }) { Text("Gewählten Auftrag erneut lesen") }
            }
            orderObservation?.let { Text("Gelesener Auftrag", style = MaterialTheme.typography.titleMedium); NativeBusinessValues(it.optJSONObject("data") ?: it) }
            val confirmationsChosen = (0 until required.length()).map { required.getString(it) }.all { key ->
                val field = properties.optJSONObject(key)
                !(field?.optString("type") == "boolean" && field.optJSONArray("enum")?.let { it.length() == 1 && it.opt(0) == true } == true) || args.opt(key) == true
            }
            val proofsReady = nativeProofsReady(schema, args)
            if (!proofsReady && !orderLoading && orderError.isEmpty() && !reviewNeeded) Text("Die benötigten Versionsnachweise fehlen. Zuerst den zugehörigen Datensatz öffnen oder die aktuelle Auswahl erneut lesen.")
            Button(enabled = !busy && !orderLoading && orderError.isEmpty() && !reviewNeeded && confirmationsChosen && proofsReady, onClick = {
                val allowed = nativeFilterInput(schema, JSONObject(text))
                allowed.optJSONObject("updates")?.let { allowed.put("updates", nativeChangedUpdates(it, JSONObject(updateBaseText))) }
                scope.launch { perform(allowed) }
            }) { Text(if (spec?.optJSONObject("annotations")?.optBoolean("readOnlyHint", true) == true) "Anzeigen / prüfen" else "Bewusst speichern") }
        }
        response?.let { HorizontalDivider(); NativeBusinessValues(it) }
        OutlinedButton({ controller.back() }, enabled = !busy) { Text("Zurück") }
    }
}

@Composable
internal fun NativeActionMenu(controller: NativeAppController, area: String, contextArgs: JSONObject = JSONObject()) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton({ show = true }) { Text("Weitere Aktionen") }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("Aktionen") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) { items(NativeActionCatalog.forArea(area).filter { it.tools.isNotEmpty() && !it.platformOnly && !it.retired }, key = { it.id }) { action ->
            TextButton({
                show = false
                when {
                    action.tools.any { it == "inspect_order_review" || it == "save_reviewed_order" } -> controller.navigate("order_review", contextArgs.optString("order_id"), args = JSONObject(contextArgs.toString()))
                    action.tools.any { it == "get_sync_conflicts" || it == "resolve_sync_conflict" } -> controller.navigate("conflict_review", contextArgs.optString("client_id"), args = JSONObject(contextArgs.toString()))
                    else -> controller.action(action.label, action.tools.first(), contextArgs)
                }
            }, Modifier.fillMaxWidth()) { Text(action.label) }
        } }
    }, confirmButton = { TextButton({ show = false }) { Text("Schließen") } })
}

internal fun <T> Flow<T>.nativeErrors(controller: NativeAppController): Flow<T> = catch { failure ->
    if (failure is CancellationException) throw failure
    controller.message("Die Ansicht konnte nicht aktualisiert werden. Bitte erneut öffnen oder die Suche ändern.")
}

private fun nativeBooleanDefaults(schema: JSONObject, values: JSONObject): JSONObject {
    val properties = schema.optJSONObject("properties") ?: return values
    val required = schema.optJSONArray("required") ?: JSONArray()
    for (i in 0 until required.length()) {
        val key = required.getString(i); val field = properties.optJSONObject(key) ?: continue
        if (!values.has(key) && field.optString("type") == "boolean" && field.optJSONArray("enum") == null) values.put(key, false)
    }
    return values
}

private fun nativeFilterInput(schema: JSONObject, entered: JSONObject): JSONObject {
    val properties = schema.optJSONObject("properties") ?: return JSONObject(entered.toString())
    val result = JSONObject()
    for (key in properties.keys().asSequence()) {
        if (!entered.has(key)) continue
        val value = entered.get(key); val field = properties.getJSONObject(key)
        result.put(key, if (value is JSONObject && field.optString("type") == "object") nativeFilterInput(field, value) else value)
    }
    return result
}

private fun nativeEnrichProofs(schema: JSONObject, entered: JSONObject, observation: JSONObject): JSONObject {
    fun find(value: JSONObject, key: String): Any? {
        if (value.has(key)) return value.get(key)
        value.optJSONObject("data")?.let { if (it.has(key)) return it.get(key) }
        return null
    }
    val properties = schema.optJSONObject("properties") ?: return entered
    for (key in properties.keys().asSequence().filter { it.startsWith("expected_") }) {
        val found = find(observation, key) ?: find(observation, key.removePrefix("expected_"))
        if (found != null) entered.put(key, found)
    }
    return nativeBooleanDefaults(schema, entered)
}

private fun nativeNeedsComparison(code: String): Boolean = code.contains("WRITE_CONFLICT") || code.contains("EPOCH_CONFLICT") || code.endsWith("CHANGED") || code == "INVOICE_REVIEW_CONTEXT_REQUIRED"

private fun nativeChangedUpdates(updates: JSONObject, base: JSONObject): JSONObject = JSONObject().also { changed ->
    for (key in updates.keys().asSequence()) {
        if (!base.has(key) || PixelMcpWriteTools.hash(JSONObject().put("value", updates.get(key))) != PixelMcpWriteTools.hash(JSONObject().put("value", base.get(key)))) changed.put(key, updates.get(key))
    }
}

/** Rebase only the user's actual delta; unchanged old settings must not replace newer values. */
private fun nativeRebaseUpdates(entered: JSONObject, previousBase: JSONObject, observation: JSONObject): Pair<JSONObject, JSONObject> {
    val values = JSONObject(entered.toString())
    val updates = values.optJSONObject("updates") ?: return values to JSONObject(previousBase.toString())
    val data = observation.optJSONObject("data") ?: observation
    val latest = listOf("settings", "record", "customer", "order", "offer", "invoice", "entry").firstNotNullOfOrNull { data.optJSONObject(it) }
        ?: data.takeIf { it.has("id") }
        ?: throw IllegalArgumentException("Der aktuelle Bearbeitungsstand fehlt. Ihre Eingaben bleiben erhalten.")
    val base = JSONObject(latest.toString())
    val merged = JSONObject(latest.toString())
    val changed = nativeChangedUpdates(updates, previousBase)
    for (key in changed.keys().asSequence()) merged.put(key, changed.get(key))
    values.put("updates", merged)
    return values to base
}

private fun nativeWithoutExpectedProofs(schema: JSONObject, entered: JSONObject): JSONObject {
    val result = JSONObject(entered.toString())
    schema.optJSONObject("properties")?.keys()?.asSequence()?.filter { it.startsWith("expected_") }?.forEach { result.remove(it) }
    return result
}

private fun nativeResetConfirmations(schema: JSONObject, entered: JSONObject): JSONObject {
    val properties = schema.optJSONObject("properties") ?: return entered
    for (key in properties.keys().asSequence()) {
        val field = properties.getJSONObject(key)
        if (field.optString("type") == "boolean" && field.optJSONArray("enum")?.let { it.length() == 1 && it.opt(0) == true } == true) entered.put(key, false)
    }
    return entered
}

private fun nativeProofsReady(schema: JSONObject, entered: JSONObject): Boolean {
    val required = schema.optJSONArray("required") ?: JSONArray()
    for (index in 0 until required.length()) {
        val key = required.getString(index)
        if (!key.startsWith("expected_")) continue
        if (!entered.has(key) || entered.isNull(key)) return false
        if (key.endsWith("_hash") && !entered.optString(key).matches(Regex("[0-9a-f]{64}"))) return false
        if (entered.opt(key) is String && entered.optString(key).isBlank()) return false
    }
    return entered.optString("existing_order_id").isEmpty() || entered.optString("expected_order_hash").matches(Regex("[0-9a-f]{64}"))
}

private fun nativeExistingOrderProof(observation: JSONObject, chosen: String): String {
    val data = observation.optJSONObject("data") ?: observation
    val order = data.optJSONObject("order") ?: data
    val hash = observation.optString("entity_hash", data.optString("entity_hash"))
    if (chosen.isEmpty() || order.optString("id") != chosen || !hash.matches(Regex("[0-9a-f]{64}"))) throw IllegalArgumentException("Der gewählte Auftrag ist nicht mit seinem aktuellen Vergleichsnachweis lesbar.")
    return hash
}
