package at.hausgarten.service

import android.app.DatePickerDialog
import android.app.TimePickerDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Locale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NativeField(label: String, value: String, onValueChange: (String) -> Unit, required: Boolean = false, multiline: Boolean = false, search: Boolean = false, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null, enabled: Boolean = true, showLabel: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val searching = search || label in listOf("Suchen", "Suche", "Bausteine suchen", "Text suchen", "Vorschläge durchsuchen")
    val finish = { keyboard?.hide(); focus.clearFocus(); Unit }
    val displayedLabel = label + if (required) " *" else ""
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth().semantics {
        testTagsAsResourceId = true; testTag = "native-field:$displayedLabel"
        if (!showLabel) contentDescription = displayedLabel
    }, label = if (showLabel) ({ Text(displayedLabel) }) else null,
        placeholder = if (!showLabel) ({ Text(displayedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
        enabled = enabled, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (multiline) ImeAction.Default else if (searching) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { finish(); onSearch?.invoke() }, onDone = { finish() }))
}

/** Pickers only publish explicitly selected, complete ISO values. Cancel leaves the draft unchanged. */
@Composable
internal fun NativeDateField(label: String, value: String, onValueChange: (String) -> Unit, required: Boolean = false, enabled: Boolean = true) {
    val context = LocalContext.current
    val selected = runCatching { LocalDate.parse(value) }.getOrNull()
    NativePickerField(label, selected?.format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)) ?: value, required, enabled,
        onClear = if (required) null else ({ onValueChange("") }), onPick = {
            val start = selected ?: LocalDate.now()
            DatePickerDialog(context, { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day).toString()) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
        })
}

@Composable
internal fun NativeTimeField(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    val selected = runCatching { LocalTime.parse(value) }.getOrNull()
    NativePickerField(label, selected?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: value, false, enabled,
        onClear = { onValueChange("") }, onPick = {
            val start = selected ?: LocalTime.now()
            TimePickerDialog(context, { _, hour, minute -> onValueChange(LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))) }, start.hour, start.minute, true).show()
        })
}

@Composable
internal fun NativeDateTimeField(label: String, value: String, onValueChange: (String) -> Unit, required: Boolean = false, enabled: Boolean = true) {
    val context = LocalContext.current
    val offset = runCatching { OffsetDateTime.parse(value) }.getOrNull()
    val zone = ZoneId.of("Europe/Vienna")
    val selected = offset?.atZoneSameInstant(zone)?.toLocalDateTime() ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
    val selectedDay = selected?.toLocalDate() ?: runCatching { LocalDate.parse(value.removeSuffix("T")) }.getOrNull()
    val displayed = selected?.format(DateTimeFormatter.ofPattern("d. MMM yyyy · HH:mm", Locale.GERMAN))
        ?: selectedDay?.format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN))?.plus(" · Uhrzeit wählen") ?: value
    NativePickerField(label, displayed, required, enabled,
        onClear = if (required) null else ({ onValueChange("") }), onPick = {
            val start = (selectedDay ?: LocalDate.now()).atTime(selected?.toLocalTime() ?: LocalTime.now())
            DatePickerDialog(context, { _, year, month, day ->
                val chosenDate = LocalDate.of(year, month + 1, day)
                TimePickerDialog(context, { _, hour, minute ->
                    val chosen = chosenDate.atTime(hour, minute)
                    onValueChange(if (offset != null) chosen.atZone(zone).toInstant().toString() else chosen.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")))
                }, start.hour, start.minute, true).show()
            }, start.year, start.monthValue - 1, start.dayOfMonth).show()
        })
}

@Composable
private fun NativePickerField(label: String, displayed: String, required: Boolean, enabled: Boolean, onClear: (() -> Unit)?, onPick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedButton(onPick, Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label + if (required) " *" else "" }, enabled = enabled) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(label + if (required) " *" else "", style = MaterialTheme.typography.labelMedium)
                Text(displayed.ifBlank { "Auswählen" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (displayed.isNotBlank() && onClear != null) TextButton(onClear, enabled = enabled) { Text("$label entfernen") }
    }
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

internal const val NATIVE_LIST_READ_ERROR = "Die Einträge konnten nicht geladen werden. Bitte die Ansicht erneut öffnen oder die Suche ändern."

/** Same remaining-space layout for real lists and selection windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeSearchLayout(modifier: Modifier = Modifier, sideBySideWithoutIme: Boolean = false, bottomControls: Boolean = false, controlsVisible: Boolean = true, controls: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sideBySide = controlsVisible && LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && (WindowInsets.isImeVisible || WindowInsets.ime.getBottom(LocalDensity.current) > 0 || sideBySideWithoutIme)
    Layout(modifier = modifier, content = { controls(); Column(Modifier.fillMaxSize(), content = content) }) { children, limits ->
        val gap = if (sideBySide) 12.dp.roundToPx() else 0
        val controlWidth = if (sideBySide) ((limits.maxWidth-gap)*0.42f).toInt() else limits.maxWidth
        val head = children[0].measure(Constraints(minWidth = controlWidth, maxWidth = controlWidth, maxHeight = limits.maxHeight))
        val bodyWidth = if (sideBySide) limits.maxWidth-controlWidth-gap else limits.maxWidth
        val bodyHeight = if (sideBySide) limits.maxHeight else (limits.maxHeight-head.height).coerceAtLeast(0)
        val body = children[1].measure(Constraints.fixed(bodyWidth, bodyHeight))
        layout(limits.maxWidth, limits.maxHeight) {
            head.placeRelative(0, if (bottomControls) limits.maxHeight - head.height else 0)
            body.placeRelative(if (sideBySide) controlWidth+gap else 0, if (sideBySide || bottomControls) 0 else head.height)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeSearchList(label: String, query: String, onQuery: (String) -> Unit, count: String,
    onFilters: (() -> Unit)? = null, onFocus: () -> Unit = {}, onSearch: (() -> Unit)? = null,
    bottomSearch: Boolean = false, controlsVisible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    val ime = WindowInsets.isImeVisible
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val large = LocalDensity.current.fontScale > 1.3f
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val shortCount = count.substringBefore(" ·")
    NativeSearchLayout(Modifier.fillMaxSize(), sideBySideWithoutIme = true, bottomControls = bottomSearch, controlsVisible = controlsVisible, controls = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (controlsVisible) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NativeField(label, query, onQuery, search = true, onSearch = onSearch,
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocus() }, showLabel = !large)
                    if (ime || large && !landscape) Column(Modifier.widthIn(max = 128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ime) IconButton({ keyboard?.hide(); focus.clearFocus() }, Modifier.semantics { contentDescription = "Eingabe beenden" }) { Text("×") }
                        else if (onFilters != null) TextButton(onFilters) { Text("Filter") }
                        if (large && !landscape) Text(shortCount, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (!ime && !(large && !landscape)) NativeSearchSummary(count, onFilters)
                else if (ime && (!large || landscape)) Text(shortCount, Modifier.padding(horizontal = 12.dp, vertical = if (large) 0.dp else 2.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }, content = content)
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
            (view.parent as? DialogWindowProvider)?.window?.nativeLightSystemBars()
        }
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val imeVisible = WindowInsets.isImeVisible || WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow, landscape, imeVisible) {
            val bars = dialogWindow?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
            // Preserve full-size search text and result rows above a tall landscape IME.
            if (landscape && imeVisible) bars?.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            else bars?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            onDispose { bars?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars()) }
        }
        val largeSearch = imeVisible && LocalDensity.current.fontScale > 1.3f
        val compactControls = largeSearch && !landscape
        val compactLabel = largeSearch && landscape
        val close = { keyboard?.hide(); focus.clearFocus(); onDismiss() }
        val count = if (loading) "Wird geladen …" else "$total Treffer" + if (pageCount && !imeVisible) " auf dieser Seite" else ""
        Surface(Modifier.fillMaxSize()) {
            // Keep the same text-field node when IME/rotation changes placement;
            // replacing a Column with a Row would dispose its focused editor.
            NativeSearchLayout(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 12.dp, vertical = if (compactLabel) 0.dp else 4.dp), controls = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!imeVisible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(close) { Text("Abbrechen") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (searchEnabled) NativeField(searchLabel, search, onSearch, search = true, modifier = Modifier.weight(1f), showLabel = !compactLabel)
                        if (imeVisible) Column(Modifier.widthIn(max = 128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(close, Modifier.semantics { contentDescription = "Abbrechen" }) { Text("×") }
                            if (compactControls) Text(count, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (!compactControls) Text(count, Modifier.padding(vertical = if (compactLabel) 0.dp else 2.dp), style = MaterialTheme.typography.labelLarge)
                }
            }) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
                    else if (!loading && total == 0) item { Text("Keine Treffer. Suchtext oder Filter ändern.", Modifier.padding(vertical = 8.dp)) }
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    content(close)
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
        catch (failure: Exception) { error = NATIVE_LIST_READ_ERROR }
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
    "route" to "Abrechnungsweg", "accounting_amount" to "Gesamtbetrag (€)",
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
    "invoice_issuer_confirmed" to "Ausstellerangaben für Angebote bestätigt", "invoice_date" to "Rechnungsdatum", "service_start" to "Leistungsbeginn", "service_end" to "Leistungsende", "payment_days" to "Zahlungsfrist in Tagen",
    "header_text" to "Einleitung", "footer_text" to "Schlusstext", "caption" to "Bildunterschrift", "capture_time" to "Aufgenommen am", "role" to "Fotorolle",
    "planned_minutes" to "Geplante Minuten", "starts_at" to "Beginn", "ends_at" to "Ende", "timezone" to "Zeitzone", "recurrence" to "Wiederholung",
    "interval_days" to "Abstand in Tagen", "anchor_date" to "Beginn", "condition" to "Zustand", "purchase_price_minor" to "Einstandspreis in Cent",
    "billing_price_minor" to "Abrechnungspreis in Cent", "quantity_decimal" to "Menge", "movement_kind" to "Buchungsart", "performed_on" to "Erledigt am",
    "amount_decimal" to "Betrag", "amount_minor" to "Betrag in Cent", "paid_on" to "Zahlungsdatum", "payment_method" to "Zahlungsart", "document_type" to "Dokumentart",
    "window_start" to "Von Uhrzeit", "window_end" to "Bis Uhrzeit", "scheduled_date" to "Geplant am", "billing_mode" to "Abrechnungsweg", "query" to "Suche"
)
internal fun nativeLabel(key: String): String = nativeLabels[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
internal fun nativeValue(value: String): String = mapOf("billed" to "Rechnung", "labor" to "Labor clandestino", "labor clandestinus" to "Labor clandestino", "marked_open" to "Offen markiert", "marked_billed" to "Abgerechnet markiert", "active" to "Aktiv", "inactive" to "Inaktiv", "open" to "Offen", "completed" to "Erledigt", "cancelled" to "Abgesagt", "draft" to "Entwurf", "ready" to "Einsatzbereit", "defective" to "Defekt", "maintenance" to "Wartung", "profile" to "Profilfoto", "documentation" to "Dokumentation", "before" to "Vorher", "during" to "Währenddessen", "after" to "Nachher", "damage" to "Schaden", "other" to "Sonstiges", "small_business" to "Kleinunternehmer", "standard" to "Umsatzsteuer", "direct" to "Kunde selbst", "via_customer" to "Anderer Rechnungsempfänger", "fixed_price" to "Fixpreisangebot", "time_material" to "Regieangebot", "estimate_binding" to "Verbindlicher Kostenvoranschlag", "estimate_nonbinding" to "Unverbindlicher Kostenvoranschlag")[value] ?: value
private fun technical(key: String) = key.startsWith("_") || key.startsWith("expected_") || key.endsWith("_id") || key.endsWith("_ids") || key.endsWith("_hash") || key.endsWith("_sha256") || key in setOf("id", "source", "created_at", "updated_at", "formatVersion", "schema_version", "local_revision", "test_record", "revision", "deleted_at", "device_id", "cloud_confirmed", "untrusted_business_data", "security_notice", "pdf_base64", "data_base64", "chunk_base64", "token", "installation_id", "restore_epoch", "protocol_version", "prerequisites", "requirements", "request_id")

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
                key.endsWith("_minor") -> financeMoney(field.toString().toBigDecimalOrNull()?.movePointLeft(2))
                key in setOf("hours", "quantity", "quantity_decimal", "kilometers") -> nativeNumber(field)
                else -> nativeDisplayText(nativeValue(field.toString()))
            })
        }
    }
}

private fun referenceCollection(name: String): String? = mapOf("customer_id" to "customers", "billing_customer_id" to "customers", "default_billing_customer_id" to "customers", "order_id" to "orders", "existing_order_id" to "orders", "property_id" to "properties", "visit_id" to "visits", "work_record_id" to "worklogs", "worklog_id" to "worklogs", "equipment_id" to "equipment", "material_id" to "materials", "care_plan_id" to "care_plans", "appointment_id" to "appointments", "offer_id" to "offers", "photo_id" to "photos")[name]

private fun nativeDateKind(name: String, spec: JSONObject): String? = when {
    spec.optString("format") == "date-time" || name in setOf("starts_at", "ends_at", "starts_local", "accepted_local", "capture_time") -> "datetime"
    spec.optString("format") == "time" || name in setOf("start_time", "end_time", "window_start", "window_end") -> "time"
    spec.optString("format") == "date" || name == "date" || name.endsWith("_date") || name.endsWith("_on") || name in setOf("date_from", "date_to", "from_date", "to_date", "wish_from", "wish_until", "service_start", "service_end", "effective_from", "maintenance_due") -> "date"
    else -> null
}

private val nativeActivityPhrases = listOf("Gartenpflege", "Rasen gemäht", "Hecke zurückgeschnitten", "Sträucher zurückgeschnitten", "Laub entfernt", "Beete gepflegt", "Reinigung durchgeführt", "Schnittgut aufgeräumt")

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
        nativeDateKind(name, spec) == "date" -> NativeDateField(label, value?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(), change, required)
        nativeDateKind(name, spec) == "time" -> NativeTimeField(label, value?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(), change)
        nativeDateKind(name, spec) == "datetime" -> NativeDateTimeField(label, value?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(), { chosen ->
            // Instant fields keep their existing contract; local planning fields remain local ISO.
            change(if (chosen.isNotBlank() && name in setOf("starts_at", "ends_at", "capture_time") && runCatching { OffsetDateTime.parse(chosen) }.isFailure)
                LocalDateTime.parse(chosen).atZone(ZoneId.of("Europe/Vienna")).toInstant().toString() else chosen)
        }, required)
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
            if (name == "activity") NativeSuggestions("Textbausteine", nativeActivityPhrases) { phrase ->
                val existing = value?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                change(listOf(existing.trimEnd(), phrase).filter { it.isNotBlank() }.joinToString("\n"))
            }
        }
    }
}

/** Rare actions reuse the registered business schema; primary workflows have their own native screens. */
@Composable
internal fun NativeActionForm(controller: NativeAppController, tool: String, title: String, initial: JSONObject = JSONObject(), fixedInputs: Boolean = false, submitLabel: String = "Speichern", onCancel: () -> Unit = { controller.back() }, detailsContent: (@Composable () -> Unit)? = null, onDone: (JSONObject) -> Unit = { controller.finishAction() }) {
    val scope = rememberCoroutineScope()
    if (tool in setOf("delete_entity", "purge_entity", "empty_trash", "delete_work_records")) {
        var failure by remember(tool, initial.toString()) { mutableStateOf("") }
        var attempt by remember(tool, initial.toString()) { mutableIntStateOf(0) }
        LaunchedEffect(tool, attempt) {
            failure = ""
            try { onDone(controller.deleteDirect(tool, initial)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = nativeActionError(error, "Löschen nicht bestätigt.") }
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failure.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Löschen …") }
            else {
                NativeStatus(failure, NativeStatusTone.Attention)
                NativeActionBar("Ergebnis prüfen", { attempt++ }, "Zurück", onCancel)
            }
        }
        return
    }
    val draftKey = controller.actionDraftKey(tool, initial)
    var spec by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var text by rememberSaveable(tool, draftKey, initial.toString()) { mutableStateOf(initial.toString()) }
    var loaded by remember(tool, draftKey) { mutableStateOf(false) }
    var prepareRevision by remember(tool, draftKey) { mutableIntStateOf(0) }
    var busy by remember(tool, draftKey) { mutableStateOf(false) }
    var error by remember(tool, draftKey) { mutableStateOf("") }
    var failedConfirmed by remember(tool, draftKey) { mutableStateOf(false) }
    var response by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var pending by remember(tool, draftKey) { mutableStateOf<JSONObject?>(null) }
    var pendingState by remember(tool, draftKey) { mutableStateOf("") }
    var optionalDetails by rememberSaveable(tool, draftKey) { mutableStateOf(false) }
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
            } else {
                // A completed or discarded draft must not revive saved Compose input or its old request.
                text = initial.toString(); pending = null; restored = false; reviewNeeded = false
                updateBaseText = if (tool in listOf("update_settings", "update_business_settings")) initial.optJSONObject("updates")?.toString() ?: "{}" else "{}"
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
        catch (failure: Exception) { error = nativeActionError(failure, "Die Aktion konnte nicht vorbereitet werden. Bitte den Entwurf erneut lesen.") }
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
        response = null; failedConfirmed = false
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
        busy = true; error = ""; failedConfirmed = false; response = null
        val readonly = spec?.optJSONObject("annotations")?.optBoolean("readOnlyHint", true) != false
        try {
            if (!nativeProofsReady(requireNotNull(spec).getJSONObject("inputSchema"), arguments)) throw IllegalArgumentException("Den benötigten aktuellen Vergleichsstand zuerst lesen und bewusst übernehmen.")
            if (!readonly) { pending = JSONObject(arguments.toString()); persist(text, pending); controller.flushDrafts() }
            val result = controller.call(tool, arguments)
            response = result.optJSONObject("data") ?: result
            if (!readonly) { controller.removeDraft(draftKey); pending = null; controller.message("Änderung lokal gespeichert."); onDone(result) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = nativeActionError(failure, "Die Antwort ist offen. Bitte zuerst das Ergebnis des ursprünglichen Vorgangs prüfen.")
            if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") {
                pending = null; failedConfirmed = true
                if (nativeNeedsComparison(failure.detail.optString("code"))) {
                    reviewNeeded = true; comparison = null; comparisonAccepted = false
                }
                persist(text, null)
            }
        } finally { busy = false }
    }
    val currentSchema = spec?.optJSONObject("inputSchema")
    val currentProperties = currentSchema?.optJSONObject("properties") ?: JSONObject()
    val currentArgs = JSONObject(text)
    val readOnly = spec?.optJSONObject("annotations")?.optBoolean("readOnlyHint", true) != false
    val submitArgs = JSONObject(text)
    val submitReady = loaded && !busy && !orderLoading && orderError.isEmpty() && !restored && !reviewNeeded && pending == null &&
        currentSchema != null && nativeProofsReady(currentSchema, submitArgs) && nativeRequiredFieldsReady(currentSchema, submitArgs)
    val submit: () -> Unit = {
        currentSchema?.let { schema ->
            val allowed = nativeFilterInput(schema, submitArgs)
            allowed.optJSONObject("updates")?.let { allowed.put("updates", nativeChangedUpdates(it, JSONObject(updateBaseText))) }
            scope.launch { perform(allowed) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) content@{
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (tool == "set_accounting_record") Text("Der Abrechnungsweg gilt für den gesamten Auftrag einschließlich seiner Arbeitseinträge. Offen entfernt die Zuordnung. Rechnungsbeträge sind hier nicht bearbeitbar.", style = MaterialTheme.typography.bodySmall)
            when {
                pending != null -> NativeStatus(when (pendingState) {
                    "content_removed" -> "Gespeichert · Eintrag später entfernt"
                    "changed" -> "Gespeichert · inzwischen geändert"
                    else -> "Speichern nicht bestätigt"
                }, NativeStatusTone.Attention)
                error.isNotBlank() -> NativeStatus(if (failedConfirmed) "Nicht gespeichert" else "Prüfung erforderlich", if (failedConfirmed) NativeStatusTone.Failed else NativeStatusTone.Attention)
                busy -> NativeStatus("Wird geprüft …", NativeStatusTone.Running)
                readOnly && response != null -> NativeStatus("Prüfung abgeschlossen", NativeStatusTone.Confirmed)
                !readOnly -> NativeStatus("Noch nicht gespeichert", NativeStatusTone.Neutral)
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (busy || orderLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!loaded) {
                if (error.isBlank()) Text("Aktion wird vorbereitet …")
                else TextButton({ error = ""; prepareRevision++ }) { Text("Entwurf erneut lesen") }
                return@content
            }
            val schema = requireNotNull(currentSchema)
            val properties = currentProperties
            val required = schema.optJSONArray("required") ?: JSONArray()
            val args = currentArgs
            (args.optJSONObject("_preview") ?: initial.optJSONObject("_preview"))?.let {
                NativeBusinessValues(it)
            }
            if (restored || pending != null) {
                Text(if (restored) "Dieser Entwurf stammt aus einer Wiederherstellung. Zuerst das ursprüngliche Ergebnis prüfen." else "Die Antwort auf diesen Speichervorgang ist noch offen. Eingaben und ursprünglicher Vorgang bleiben unverändert erhalten.")
                NativeBusinessValues(pending ?: args)
            }
            if (pending != null) {
                when (pendingState) {
                    "unknown" -> {
                        Text("Ergebnis weiterhin offen. Die Prüfung hat keinen eindeutigen Speicherbeleg gefunden.")
                        if (!restored) OutlinedButton(enabled = !busy && !orderLoading, onClick = { scope.launch { perform(JSONObject(requireNotNull(pending).toString())) } }) { Text("Ursprünglichen Vorgang erneut senden") }
                        if (!restored) Text("Sendet dieselben unveränderten Angaben mit derselben Vorgangskennung. Eine Ergebnisprüfung allein speichert nichts.", style = MaterialTheme.typography.bodySmall)
                    }
                    "content_removed" -> Text("Der Vorgang wurde gespeichert; der Inhalt wurde später entfernt. Bitte den zugehörigen Eintrag oder Papierkorb ansehen. Er wird nicht erneut angelegt.")
                    "changed" -> Text("Der gespeicherte Vorgang wurde inzwischen geändert. Bitte den aktuellen Eintrag ansehen. Er wird nicht erneut ausgeführt.")
                }
            }
            if ((restored || reviewNeeded) && pending == null) {
                if (reviewNeeded) Text("Der gespeicherte Stand hat sich geändert. Deine Eingaben bleiben erhalten. Vor erneutem Speichern den aktuellen Stand vergleichen und ausdrücklich übernehmen.")
                OutlinedButton(enabled = !busy && !orderLoading, onClick = { scope.launch {
                    busy = true; comparison = null; comparisonAccepted = false
                    try { comparison = readComparison(JSONObject(text)); error = "" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = nativeActionError(failure, "Der aktuelle Stand konnte nicht gelesen werden. Bitte erneut prüfen.") } finally { busy = false }
                } }) { Text("Aktuellen Stand vergleichen") }
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
                            if (!nativeProofsReady(schema, reviewed)) throw IllegalArgumentException("Der aktuelle Stand konnte noch nicht vollständig gelesen werden. Bitte den zugehörigen Eintrag erneut öffnen.")
                            if (properties.has("request_id")) reviewed.put("request_id", UUID.randomUUID().toString())
                            val adopted = JSONObject().put("values", reviewed).put("update_base", rebased.second).put("original_tool", tool).put("review_required", false)
                            controller.adoptDraft(draftKey, adopted)
                            updateBaseText = rebased.second.toString(); text = reviewed.toString()
                            restored = false; reviewNeeded = false; comparison = null; comparisonAccepted = false; orderError = ""; error = ""
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = nativeActionError(failure, "Der Entwurf konnte nicht übernommen werden. Bitte erneut prüfen.") } finally { busy = false }
                    } }) { Text("Geprüfte Eingaben weiterbearbeiten") }
                }
            }
            if (!restored && pending == null) {
                val focusedKeys = when (tool) {
                    "start_work" -> setOf("customer_id")
                    "complete_work_session", "finish_work" -> setOf("activity")
                    else -> emptySet()
                }
                val editable = properties.keys().asSequence().filter { !technical(it) || referenceCollection(it) != null }
                    .filterNot { tool == "set_accounting_record" && it in setOf("collection", "entity_id") }
                    .filterNot { tool == "set_work_record_billed" && it == "work_record_id" && initial.optString("work_record_id").isNotBlank() }.toList()
                if (!fixedInputs) {
                    val mainFields = if (focusedKeys.isEmpty()) editable else editable.filter { it in focusedKeys }
                    val extraFields = if (focusedKeys.isEmpty()) emptyList() else editable.filterNot { it in focusedKeys }
                    val renderField: @Composable (String) -> Unit = { key ->
                        NativeSchemaField(controller, key, properties.getJSONObject(key), args.opt(key), (0 until required.length()).any { required.getString(it) == key }, fieldChange@{ value ->
                            if (busy || pending != null) return@fieldChange
                            if (key == "existing_order_id") selectExistingOrder(value as? String ?: "")
                            else {
                                val changed = JSONObject(text); if (value == null || value is String && value.isBlank()) changed.remove(key) else changed.put(key, value)
                                text = changed.toString(); comparisonAccepted = false; response = null; failedConfirmed = false
                                if (referenceCollection(key) != null) comparison = null
                                persist(text)
                            }
                        })
                    }
                    mainFields.forEach { renderField(it) }
                    if (extraFields.isNotEmpty() || detailsContent != null) {
                        TextButton({ optionalDetails = !optionalDetails }) { Text(if (optionalDetails) "Weniger Angaben" else "Weitere Angaben (optional)") }
                        if (optionalDetails) {
                            extraFields.forEach { renderField(it) }
                            detailsContent?.invoke()
                        }
                    }
                }
                if (orderError.isNotEmpty()) {
                    Text(orderError, color = MaterialTheme.colorScheme.error)
                    TextButton(enabled = !busy && !orderLoading, onClick = { selectExistingOrder(JSONObject(text).optString("existing_order_id")) }) { Text("Gewählten Auftrag erneut lesen") }
                }
                orderObservation?.let { Text("Gelesener Auftrag", style = MaterialTheme.typography.titleMedium); NativeBusinessValues(it.optJSONObject("data") ?: it) }
                if (!nativeProofsReady(schema, args) && !orderLoading && orderError.isEmpty() && !reviewNeeded) Text("Der aktuelle Eintrag muss zuerst gelesen werden. Bitte den zugehörigen Eintrag öffnen oder die Auswahl erneut lesen.")
            }
            response?.takeIf { pending == null }?.let { HorizontalDivider(); NativeBusinessValues(it) }
        }
        if (pending != null) NativeActionBar("Ergebnis prüfen", onPrimary = { scope.launch {
            busy = true; error = ""; pendingState = ""
            try {
                val original = JSONObject(requireNotNull(pending).toString())
                val proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", original))
                pendingState = proof.optString("state")
                if (pendingState == "confirmed") {
                    controller.removeDraft(draftKey); pending = null
                    controller.message("Der bereits gespeicherte Vorgang wurde gefunden."); onDone(proof.optJSONObject("result") ?: proof)
                } else if (pendingState !in setOf("unknown", "content_removed", "changed")) error = "Das Ergebnis bleibt offen. Der ursprüngliche Vorgang ist erhalten; bitte später erneut prüfen."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = nativeActionError(failure, "Das Ergebnis konnte nicht gelesen werden. Bitte später erneut prüfen.") } finally { busy = false }
        } }, secondaryLabel = "Später prüfen", onSecondary = onCancel, enabled = !busy && !orderLoading, secondaryEnabled = !busy)
        else if (loaded && !restored && !reviewNeeded) NativeActionBar(if (readOnly) "Prüfen" else submitLabel, submit,
            if (tool in setOf("complete_work_session", "finish_work")) "Später abschließen" else "Zurück", onCancel, enabled = submitReady, secondaryEnabled = !busy)
        else NativeActionBar("Zurück", onCancel, enabled = !busy)
    }
}

@Composable
internal fun NativeActionMenu(controller: NativeAppController, area: String, contextArgs: JSONObject = JSONObject()) {
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OutlinedButton({ show = true }) { Text("Weitere Aktionen") }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("Aktionen") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) { items(NativeActionCatalog.forArea(area).filter {
            it.tools.isNotEmpty() && !it.platformOnly && !it.retired && it.tools.any { tool -> !tool.startsWith("preview_") }
        }, key = { it.id }) { action ->
            TextButton({
                show = false
                val tool = action.tools.firstOrNull { it in setOf("delete_entity", "purge_entity", "empty_trash", "delete_work_records") } ?: action.tools.first()
                if (tool in setOf("delete_entity", "purge_entity", "empty_trash", "delete_work_records")) {
                    scope.launch {
                        try { controller.deleteDirect(tool, contextArgs); controller.finishAction(); controller.message("Gelöscht.") }
                        catch (failure: Exception) { controller.message(nativeActionError(failure, "Löschen nicht bestätigt.")) }
                    }
                } else controller.action(action.label, tool, contextArgs)
            }, Modifier.fillMaxWidth(), colors = if (action.tools.any { it in setOf("delete_entity", "purge_entity", "empty_trash", "delete_work_records") })
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) { Text(when {
                "empty_trash" in action.tools -> "Papierkorb leeren"
                "purge_entity" in action.tools -> "Endgültig löschen"
                "delete_entity" in action.tools -> "Löschen"
                "delete_work_records" in action.tools -> "Auswahl löschen"
                else -> action.label
            }) }
        } }
    }, confirmButton = { TextButton({ show = false }) { Text("Abbrechen") } })
}

internal fun <T> Flow<T>.nativeErrors(controller: NativeAppController): Flow<T> = catch { failure ->
    if (failure is CancellationException) throw failure
    controller.message("Die Ansicht konnte nicht aktualisiert werden. Bitte erneut öffnen oder die Suche ändern.")
}

private fun nativeRequiredFieldsReady(schema: JSONObject, values: JSONObject): Boolean {
    val properties = schema.optJSONObject("properties") ?: return true
    val required = schema.optJSONArray("required") ?: JSONArray()
    return (0 until required.length()).all { index ->
        val key = required.getString(index)
        val field = properties.optJSONObject(key) ?: return@all true
        val value = values.opt(key)
        val choices = field.optJSONArray("enum")
        value != null && value != JSONObject.NULL && (value !is String || value.isNotBlank()) &&
            (choices == null || (0 until choices.length()).any { choices.opt(it) == value }) &&
            (value !is JSONObject || nativeRequiredFieldsReady(field, value))
    }
}

private fun nativeActionError(failure: Exception, fallback: String): String {
    val message = failure.message.orEmpty()
    return if (message.isBlank() || message.trimStart().startsWith("{") || Regex("[0-9a-f]{64}|expected_|request_id|prerequisites", RegexOption.IGNORE_CASE).containsMatchIn(message)) fallback else nativeDisplayText(message)
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

private fun nativeNeedsComparison(code: String): Boolean = code.contains("WRITE_CONFLICT") || code.contains("EPOCH_CONFLICT") || code.endsWith("CHANGED")

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

/** A tap starts deletion; its durable request is inspected after an uncertain response. */
internal suspend fun NativeAppController.deleteDirect(tool: String, initial: JSONObject, onPrepared: (JSONObject) -> Unit = {}, onNotCommitted: () -> Unit = {}): JSONObject {
    require(tool in setOf("delete_entity", "purge_entity", "empty_trash", "delete_work_records"))
    val key = "delete:$tool:" + PixelMcpWriteTools.hash(initial)
    val legacyKey = actionDraftKey(tool, initial)
    val legacy = draft(legacyKey)?.takeIf { it.optString("original_tool") == tool }?.optJSONObject("original_args")
    if (legacy != null) {
        val proof = query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", legacy))
        if (proof.optString("state") !in setOf("confirmed", "content_removed"))
            throw IllegalStateException("Ein früherer Löschvorgang ist noch offen. Bitte später erneut prüfen.")
        val sameTarget = when (tool) {
            "delete_work_records" -> legacy.optJSONArray("work_record_ids")?.toString() == initial.optJSONArray("work_record_ids")?.toString()
            "empty_trash" -> true
            else -> legacy.optString("entity") == initial.optString("entity") && legacy.optString("entity_id") == initial.optString("entity_id")
        }
        if (sameTarget) onPrepared(JSONObject(legacy.toString()))
        removeDraft(legacyKey)
        if (sameTarget) return proof.optJSONObject("result") ?: JSONObject()
    }
    val original = draft(key)?.optJSONObject("original_args")
    if (original != null) {
        onPrepared(JSONObject(original.toString()))
        val proof = query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", original))
        if (proof.optString("state") == "confirmed" || proof.optString("state") == "content_removed") {
            removeDraft(key)
            return proof.optJSONObject("result") ?: JSONObject()
        }
        throw IllegalStateException("Löschergebnis noch offen. Erneutes Antippen prüft denselben Vorgang.")
    }
    val schema = requireNotNull(spec(tool)).getJSONObject("inputSchema")
    val observation = actionObservation(tool, initial)
    val entered = nativeEnrichProofs(schema, JSONObject(initial.toString()), observation)
        .put("request_id", UUID.randomUUID().toString()).put("confirmed", true)
    val args = nativeFilterInput(schema, entered)
    check(nativeProofsReady(schema, args)) { "Eintrag nicht aktuell lesbar. Bitte erneut öffnen." }
    saveDraft(key, JSONObject().put("original_tool", tool).put("values", args).put("original_args", args))
    flushDrafts()
    onPrepared(JSONObject(args.toString()))
    try {
        val result = query(tool, args)
        removeDraft(key)
        return result
    } catch (failure: NativeCallFailure) {
        if (failure.detail.optString("commit_state") == "not_committed") {
            removeDraft(key)
            onNotCommitted()
        }
        throw failure
    }
}

@Composable
internal fun NativeDeleteButton(controller: NativeAppController, entity: String, id: String,
    label: String = "Löschen", permanent: Boolean = false, emptyTrash: Boolean = false,
    enabled: Boolean = true, scope: CoroutineScope = rememberCoroutineScope(), onDone: () -> Unit) {
    var busy by remember(entity, id) { mutableStateOf(false) }
    OutlinedButton({
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    val tool = if (emptyTrash) "empty_trash" else if (permanent) "purge_entity" else "delete_entity"
                    val args = JSONObject().apply { if (!emptyTrash) put("entity", entity).put("entity_id", id) }
                    controller.deleteDirect(tool, args)
                    onDone()
                    controller.message(if (emptyTrash) "Papierkorb geleert." else "Gelöscht.")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { controller.message(nativeActionError(failure, "Löschen nicht bestätigt. Erneut antippen, um das Ergebnis zu prüfen.")) }
                finally { busy = false }
            }
        }
    }, enabled = enabled && !busy, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, if (enabled && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)) { Text(if (busy) "Löschen …" else label) }
}
