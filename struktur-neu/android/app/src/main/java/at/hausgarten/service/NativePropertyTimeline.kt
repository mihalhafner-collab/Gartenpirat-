package at.hausgarten.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativePropertyTimeline(
    controller: NativeAppController,
    initial: JSONObject,
    onBack: () -> Unit,
    onEdit: (String, JSONObject?, JSONObject) -> Unit,
) {
    val id = initial.getJSONObject("record").getString("id")
    var view by rememberSaveable(id) { mutableStateOf("history") }
    var offset by rememberSaveable(id, view) { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var editingField by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    var adding by remember { mutableStateOf(false) }
    val changes by
        remember(controller) { controller.store.nativeData().watchCommittedChanges() }
            .collectAsStateWithLifecycle(-1L)
    val scope = rememberCoroutineScope()
    val property = page?.optJSONObject("record") ?: initial.getJSONObject("record")
    val customer = page?.optJSONObject("customer") ?: initial.getJSONObject("customer")
    val context =
        JSONObject().put("customer_id", property.getString("customer_id")).put("property_id", id)
    fun plan() = onEdit("visit", null, JSONObject(context.toString()))
    fun photo() {
        controller.navigate(
            "photos",
            mode = "import",
            args = JSONObject(context.toString()).put("_open_camera", true),
        )
    }
    fun money() {
        controller.navigate("accounting", args = context)
    }
    fun external(uri: String) {
        try {
            controller.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            controller.message("Keine passende App verfügbar.")
        }
    }
    LaunchedEffect(id, view, offset, changes, attempt) {
        try {
            page =
                controller.query(
                    "get_property_timeline",
                    JSONObject()
                        .put("property_id", id)
                        .put("view", view)
                        .put("offset", offset)
                        .put("limit", 50),
                )
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = nativeActionError(failure, "Der Objektverlauf konnte nicht geladen werden.")
        }
    }
    val entries = SettlementRules.rows(page?.optJSONArray("items"))
    val fields =
        linkedMapOf(
            "address" to "Adresse",
            "district" to "Gebiet",
            "access_notes" to "Zugang",
            "area_decimal" to "Fläche",
            "area_unit" to "Einheit der Fläche",
            "notes" to "Notizen",
        )
    val density = LocalDensity.current
    var footerHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            NativeDetailHeading(
                                property.optString("name", "Objekt"),
                                listOf(
                                    NativeDetailAction(
                                        "property-care",
                                        "Pflegeplan anlegen",
                                        run = {
                                            onEdit(
                                                "care_plan",
                                                null,
                                                JSONObject(context.toString()),
                                            )
                                        },
                                    ),
                                    NativeDetailAction(
                                        "property-photo",
                                        "Foto hinzufügen",
                                        run = ::photo,
                                    ),
                                    NativeDetailAction(
                                        "property-offer",
                                        "Angebot vorbereiten",
                                        run = {
                                            controller.navigate(
                                                "offers",
                                                mode = "new",
                                                args =
                                                    JSONObject(context.toString())
                                                        .put("title", property.optString("name")),
                                            )
                                        },
                                    ),
                                ),
                                onTitleClick = { editingField = "name" to "Objekt" },
                            )
                            NativeStatus(
                                if (property.optBoolean("active", true)) "Aktiv" else "Inaktiv"
                            )
                        }
                    }
                }
                item {
                    NativeContext(
                        controller,
                        JSONObject(context.toString()).apply { remove("property_id") },
                        heading = true,
                    )
                    NativeActions {
                        val address = property.optString("address")
                        NativeOutlinedButton(
                            { external("geo:0,0?q=" + Uri.encode(address)) },
                            Modifier.semantics { testTag = UiTags.OBJECT_ROUTE },
                            enabled = address.isNotBlank(),
                        ) {
                            Text("Route")
                        }
                        val phone = customer.optString("phone")
                        NativeOutlinedButton(
                            {
                                try {
                                    controller.context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.fromParts("tel", phone, null),
                                        )
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    controller.message("Keine Telefon-App verfügbar.")
                                }
                            },
                            Modifier.semantics { testTag = UiTags.OBJECT_CALL },
                            enabled = phone.isNotBlank(),
                        ) {
                            Text("Anrufen")
                        }
                        NativeButton(::plan, Modifier.semantics { testTag = UiTags.OBJECT_PLAN }) {
                            Text("Einsatz planen")
                        }
                    }
                }
                item {
                    NativeGroup("Angaben") {
                        for ((field, label) in fields) if (property.optString(field).isNotBlank())
                            NativeKeyValue(label, nativeDisplayText(property.optString(field))) {
                                editingField = field to label
                            }
                    }
                    val missing = fields.filterKeys { property.optString(it).isBlank() }
                    if (missing.isNotEmpty())
                        Box {
                            NativeTextButton({ adding = true }) { Text("Ergänzen") }
                            DropdownMenu(adding, { adding = false }) {
                                for ((field, label) in missing) DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        adding = false
                                        editingField = field to label
                                    },
                                )
                            }
                        }
                }
                item {
                    val selected = listOf("history", "care", "photos", "money").indexOf(view)
                    ScrollableTabRow(selected, edgePadding = 0.dp) {
                        for ((key, label) in
                            listOf(
                                "history" to "Verlauf",
                                "care" to "Pflege",
                                "photos" to "Fotos",
                                "money" to "Geld",
                            )) Tab(
                            view == key,
                            {
                                view = key
                                offset = 0
                            },
                            modifier = Modifier.semantics { testTag = UiTags.objectTab(key) },
                            text = { Text(label) },
                        )
                    }
                }
                if (error.isNotBlank())
                    item {
                        NativeStatus(error, NativeStatusTone.Failed)
                        NativeTextButton(
                            { attempt++ },
                            Modifier.semantics { testTag = UiTags.OBJECT_RETRY },
                        ) {
                            Text("Erneut laden")
                        }
                    }
                if (page == null && error.isBlank())
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (page != null && entries.isEmpty())
                    item {
                        Text(
                            when (view) {
                                "care" ->
                                    "Pflegepläne halten die regelmäßige Pflege dieses Objekts fest."
                                "photos" ->
                                    "Hier sammelst du Fotos zum Objekt und seinen Einsätzen."
                                "money" -> "Hier findest du die zugeordneten Belege und Zahlungen."
                                else -> "Hier wächst der Verlauf von der Anfrage bis zur Zahlung."
                            }
                        )
                    }
                items(entries, key = { it.getString("key") }) { entry ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (view == "history") {
                            val filled = entry.optBoolean("filled")
                            val marker = if (filled) NativeDone else NativeNeutral
                            Canvas(
                                Modifier.padding(top = 16.dp).size(12.dp).semantics {
                                    contentDescription = if (filled) "Erledigt" else "Geplant"
                                }
                            ) {
                                if (filled) drawCircle(marker)
                                else drawCircle(marker, style = Stroke(2.dp.toPx()))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            val amount =
                                if (entry.has("amount_minor"))
                                    (if (entry.optString("amount_scope") == "document")
                                        "Beleg gesamt: "
                                    else "Zahlung: ") +
                                        OfferMoney.decimal(entry.getLong("amount_minor"))
                                            .replace('.', ',') +
                                        " " +
                                        entry.optString("currency")
                                else ""
                            val detail =
                                listOf(
                                        entry
                                            .optString("at")
                                            .takeIf(String::isNotBlank)
                                            ?.let(::nativeDisplayDate) ?: "Datum offen",
                                        nativeDetailStatus(
                                            entry.optString("status"),
                                            entry.optString("kind"),
                                            entry.optString("record_kind"),
                                        ),
                                        amount,
                                    )
                                    .filter(String::isNotBlank)
                                    .joinToString(" · ")
                            NativeInfoRow(
                                entry.getString("title"),
                                detail,
                                {
                                    controller.navigate(
                                        entry.getString("route"),
                                        entry.getString("target_id"),
                                        "detail",
                                    )
                                },
                                Modifier.semantics {
                                    testTag = UiTags.objectEntry(entry.getString("key"))
                                    stateDescription =
                                        if (entry.optBoolean("filled")) "Erledigt" else "Geplant"
                                },
                            )
                        }
                    }
                }
                if (page?.optInt("total", 0) ?: 0 > 50)
                    item {
                        NativeActions {
                            NativeTextButton(
                                { offset = (offset - 50).coerceAtLeast(0) },
                                Modifier.semantics { testTag = UiTags.OBJECT_PREVIOUS },
                                enabled = offset > 0,
                            ) {
                                Text("Vorige Einträge")
                            }
                            NativeTextButton(
                                { offset += 50 },
                                Modifier.semantics { testTag = UiTags.OBJECT_NEXT },
                                enabled = page?.optBoolean("has_more") == true,
                            ) {
                                Text("Weitere Einträge")
                            }
                        }
                    }
                item {
                    NativeDetailDelete(enabled = !deleting) {
                        deleting = true
                        scope.launch {
                            try {
                                controller.queueSwipe("property", id, false)
                                onBack()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error =
                                    nativeActionError(
                                        failure,
                                        "Das Objekt konnte nicht gelöscht werden.",
                                    )
                            } finally {
                                deleting = false
                            }
                        }
                    }
                }
            }
            Box(Modifier.onSizeChanged { footerHeight = it.height }) {
                NativeActionBar(
                    when (view) {
                        "care" -> "Pflegeplan anlegen"
                        "photos" -> "Foto hinzufügen"
                        "money" -> "Abrechnung öffnen"
                        else -> "Einsatz planen"
                    },
                    {
                        when (view) {
                            "care" -> onEdit("care_plan", null, JSONObject().put("property_id", id))
                            "photos" -> photo()
                            "money" -> money()
                            else -> plan()
                        }
                    },
                    enabled = !deleting,
                    secondaryEnabled = !deleting,
                )
            }
        }
        NativeCreateButton(
            controller,
            Modifier.align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom =
                        if (footerHeight > 0) with(density) { footerHeight.toDp() } + 16.dp
                        else 84.dp,
                ),
            context,
        )
    }
    editingField?.let { (field, label) ->
        NativeRevierValueSheet(controller, "property", property, field, label, { attempt++ }) {
            editingField = null
        }
    }
}
