package at.hausgarten.service

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.ZoneId
import java.io.File
import java.util.UUID

/** Explicit restore review uses the existing checkpoint and editor envelopes. */
@Composable
internal fun NativeRestoredDraftReview(controller: NativeAppController) {
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var selected by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedMedia by remember { mutableStateOf(emptyList<JSONObject>()) }
    var deferMedia by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var acceptedOmissions by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        state = withContext(Dispatchers.IO) {
            val review = RestoredBrowserCheckpoint(controller.context, controller.store)
            review.preserveDevice(JSONObject().put("formatVersion", 1).put("storage", JSONObject()))
            review.status(true)
        }
        state?.optJSONObject("selection")?.let { saved ->
            selected = restoredObjects(saved.optJSONArray("selected"))
            selectedMedia = restoredObjects(saved.optJSONArray("selected_media"))
            deferMedia = saved.optBoolean("media_deferred")
        }
    }
    LaunchedEffect(Unit) { try { refresh() } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) } }
    val current = state
    val locked = current?.optString("phase") != "REVIEW_REQUIRED"
    val reviewed = current?.optString("phase") == "REVIEWED"
    val candidates = restoredObjects(current?.optJSONArray("candidates"))
    val media = restoredObjects(current?.optJSONArray("media_candidates"))
    val mediaRequired = current?.optBoolean("media_review_required") == true || current?.optBoolean("media_review_pending") == true
    val mediaReady = current?.optBoolean("media_mirror_complete") == true && current.optString("media_error_code").isEmpty()
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Entwürfe prüfen", style = MaterialTheme.typography.headlineSmall)
            NativeStatus(if (reviewed) "Auswahl bestätigt" else "Prüfung erforderlich", if (reviewed) NativeStatusTone.Confirmed else NativeStatusTone.Attention)
            Text("Wähle je Entwurf eine Quelle. Bereits gespeicherte Arbeit und Dokumente werden dadurch nicht erneut angelegt. Nicht gewählte Werte bleiben im gesicherten Quellbestand erhalten.")
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (current == null) Text("Formularstände werden gelesen …")
            else if (!current.optBoolean("available")) Text("Keine offene Formularübernahme vorhanden.")
            else Text(when (current.optString("phase")) { "REVIEWED" -> "Formularauswahl gespeichert und zurückgelesen."; "SELECTION_PENDING" -> "Die bestätigte Auswahl wird unverändert fortgesetzt. Es wird keine neue Auswahl angenommen."; else -> "Die Formularauswahl benötigt deine Prüfung." })
        }
        items(candidates, key = { it.getString("source") + ":" + it.getString("key") }) { candidate ->
            val key = candidate.getString("key"); val source = candidate.getString("source")
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Checkbox(selected.any { it.optString("key") == key && it.optString("source") == source }, enabled = !locked && !busy, onCheckedChange = { checked ->
                        selected = selected.filterNot { it.optString("key") == key } + if (checked) listOf(JSONObject().put("key", key).put("source", source)) else emptyList()
                    })
                    Column(Modifier.weight(1f)) { Text(candidate.optString("label", "Formular")); Text(restoredSourceLabel(source), style = MaterialTheme.typography.bodySmall) }
                }
                RestoredCandidateValues(candidate.optString("value"), controller)
            } }
        }
        if (current?.optBoolean("available") == true) item {
            Text("${current.optInt("other_browser_values_preserved")} weitere Browserwerte bleiben im Quellbestand erhalten.")
            Row { Text("Medien später gesondert prüfen", Modifier.weight(1f)); Switch(deferMedia, enabled = !busy && (!locked || reviewed && current.optBoolean("media_review_pending")), onCheckedChange = { deferMedia = it; acceptedOmissions = false }) }
            if (deferMedia) Text("Fotoentwürfe bleiben getrennt zur Prüfung erhalten. Die Formularübernahme bestätigt keine vollständige Fotoabdeckung.")
            else {
                Text("Wähle je Fotoentwurf eine Quelle. Die vorhandenen Dateireferenzen werden vor Aktivierung geprüft.")
                if (!mediaReady && mediaRequired) Text("Die Medienquelle ist noch nicht vollständig geprüft. Medien zurückstellen oder die gewählte Sicherungsdatei prüfen.", color = MaterialTheme.colorScheme.error)
            }
        }
        if (!deferMedia) items(media, key = { it.optString("source") + ":" + it.optString("key") + ":" + it.optString("record_id") }) { candidate ->
            val key = candidate.getString("key"); val id = candidate.getString("record_id")
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Row {
                    Checkbox(selectedMedia.any { it.optString("key") == key && it.optString("record_id") == id && it.optString("source") == candidate.optString("source") },
                        enabled = !busy && (!locked || reviewed) && mediaRequired && mediaReady && candidate.optString("error_code").isEmpty(), onCheckedChange = { checked ->
                            selectedMedia = selectedMedia.filterNot { it.optString("key") == key } + if (checked) listOf(JSONObject().put("key", key).put("source", candidate.getString("source")).put("record_id", id).put("record_hash", candidate.getString("record_hash"))) else emptyList()
                            acceptedOmissions = false
                        })
                    Column(Modifier.weight(1f)) {
                        Text(candidate.optString("label", "Fotoentwurf") + " · " + restoredSourceLabel(candidate.optString("source")))
                        Text("${candidate.optInt("blob_count")} Dateien · ${(candidate.optLong("byte_size") + 1023) / 1024} KB")
                        if (candidate.optString("error_code").isNotBlank()) Text("Dieser Fotoentwurf ist noch nicht vollständig lesbar. Seine Quelle bleibt erhalten.", color = MaterialTheme.colorScheme.error)
                    }
                }
            } }
        }
        if (!deferMedia && mediaRequired && (!locked || reviewed)) item {
            Row { Checkbox(acceptedOmissions, { acceptedOmissions = it }, enabled = !busy); Text("Ich habe die Fotoauswahl geprüft. Nicht ausgewählte Fotoentwürfe bleiben als Quelle erhalten und werden nicht aktiviert.", Modifier.weight(1f)) }
        }
        }
        if (current?.optBoolean("available") == true && !reviewed) NativeActionBar(
            if (locked) "Ergebnis prüfen" else "Auswahl übernehmen",
            { scope.launch {
                busy = true; error = ""
                try {
                    withContext(Dispatchers.IO) {
                        val review = RestoredBrowserCheckpoint(controller.context, controller.store)
                        val live = review.status(true)
                        val result = if (live.optString("phase") == "SELECTION_PENDING") live.getJSONObject("selection") else {
                            review.choose(JSONObject().put("request_id", "native_review_" + UUID.randomUUID()).put("confirmed", true)
                                .put("expected_record_hash", current.getString("record_hash")).put("selected", JSONArray(selected))
                                .put("defer_media", deferMedia).put("selected_media", if (deferMedia) JSONArray() else JSONArray(selectedMedia))
                                .apply { if (!deferMedia && mediaRequired) put("expected_media_hash", current.getString("media_record_hash")) })
                        }
                        NativeRestoredDrafts.retainSelection(controller.store, result)
                        review.complete(JSONObject().put("request_id", result.getString("request_id")).put("selection_hash", result.getString("selection_hash")).put("readback_confirmed", true))
                    }
                    refresh(); controller.message("Formularauswahl gespeichert und vollständig zurückgelesen.")
                } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure); runCatching { refresh() } }
                finally { busy = false }
            } },
            "Zurück", { controller.back() },
            enabled = !busy && (deferMedia || !mediaRequired || mediaReady && (locked || acceptedOmissions)), secondaryEnabled = !busy
        )
        else if (reviewed && current?.optBoolean("media_review_pending") == true && !deferMedia) NativeActionBar(
            "Fotoauswahl übernehmen", { scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { RestoredBrowserCheckpoint(controller.context, controller.store).completeMedia(JSONObject()
                        .put("request_id", "native_media_review_" + UUID.randomUUID()).put("confirmed", true)
                        .put("expected_media_hash", current.getString("media_record_hash")).put("selected_media", JSONArray(selectedMedia))) }
                    refresh(); error = ""; controller.message("Die geprüfte Medienauswahl wurde übernommen.")
                } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
                finally { busy = false }
            } },
            "Zurück", { controller.back() }, enabled = !busy && mediaReady && acceptedOmissions, secondaryEnabled = !busy
        )
        else NativeActionBar("Fertig", { controller.back() }, "Zurück", { controller.back() }, enabled = !busy, secondaryEnabled = !busy)
    }
}

private fun restoredError(failure: Throwable) = failure.message?.takeIf { it.isNotBlank() } ?: "Die Übernahme benötigt eine Prüfung. Vorhandene Eingaben bleiben erhalten."
private fun restoredObjects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
private fun restoredSourceLabel(source: String) = if (source == "backup") "Sicherung" else if (source == "device") "Bisheriges Gerät" else "Nativer Entwurf"
private fun restoredParse(raw: String): Any = runCatching { JSONTokener(raw).nextValue() }.getOrDefault(raw)
private fun restoredCopy(value: JSONObject) = JSONObject(value.toString())

/** Scoped source storage and small path indexes are committed before the checkpoint completion proof. */
internal object NativeRestoredDrafts {
    private const val STORAGE = "native_restored_storage:"
    private const val INDEX = "native_restored_draft:"
    private const val MANIFEST = "native_restored_draft_index:"

    fun index(storage: JSONObject, selection: JSONArray, hash: String): List<JSONObject> {
        val rows = ArrayList<JSONObject>()
        val sessions = storage.optString("hausgarten.form.sessions.v1").takeIf { it.isNotEmpty() }?.let { restoredParse(it) as? JSONObject }
        val activeSessions = sessions?.optJSONObject("entries")
        val removedSessions = sessions?.optJSONObject("removed")
        fun add(key: String, path: JSONArray, kind: String, label: String) {
            val choice = restoredObjects(selection).firstOrNull { it.optString("key") == key }
            val identity = JSONObject().put("key", key).put("path", path)
            rows.add(JSONObject().put("formatVersion", 1).put("selection_hash", hash).put("storage_key", key).put("path", path)
                .put("source", choice?.optString("source") ?: "backup").put("kind", kind).put("label", label)
                .put("item_id", PixelMcpWriteTools.hash(identity)))
        }
        for (key in storage.keys().asSequence().toList().sorted()) {
            val parsed = restoredParse(storage.getString(key))
            val value = parsed as? JSONObject
            when {
                key == "hausgarten.form.sessions.v1" && value?.optJSONObject("entries") != null -> {
                    val entries = value.getJSONObject("entries"); val removed = value.optJSONObject("removed")
                    for (id in entries.keys().asSequence().toList().sorted()) if (removed?.has(id) != true) {
                        val edit = entries.optJSONObject(id)
                        add(key, JSONArray().put("entries").put(id), edit?.optString("kind").orEmpty(), edit?.optString("kind")?.let(::kindLabel) ?: "Erhaltener Entwurf")
                    }
                }
                key == RestoredBrowserReviewRules.MIRROR && value?.optJSONObject("pages") != null -> {
                    val pages = value.getJSONObject("pages")
                    for (page in pages.keys().asSequence().toList().sorted()) {
                        val forms = pages.getJSONObject(page).optJSONArray("forms") ?: continue
                        for (i in 0 until forms.length()) {
                            val form = forms.optJSONObject(i) ?: continue
                            val session = form.optString("session_id")
                            if (session.isNotEmpty() && removedSessions?.has(session) == true) continue
                            // A visible mirror complements its durable session; do not offer a second business edit.
                            val kind = if (session.isNotEmpty() && activeSessions?.has(session) == true) "" else mirrorKind(form.optString("key"), page)
                            add(key, JSONArray().put("pages").put(page).put("forms").put(i), kind, "Zusätzliches Formular · " + form.optString("key", page))
                        }
                    }
                }
                key == "hausgarten.planning.draft.v1.others" && value != null -> for (id in value.keys().asSequence().toList().sorted()) add(key, JSONArray().put(id), "appointment", "Terminplanung")
                else -> add(key, JSONArray(), when {
                    key.startsWith("hausgarten.garden.draft.") -> "garden"
                    key.startsWith("hausgarten.photo.edit.") -> "photo_edit"
                    key == "hausgarten.entry.draft.v1" -> "work_entry"
                    key == "hausgarten.offer.editor.v1" -> "offer"
                    key == "hausgarten.offer.decision.editor.v1" -> "offer_decision"
                    key.startsWith("hausgarten.planning.") -> "appointment"
                    key == "hausgarten.revier.editor.v1" -> value?.optString("kind").orEmpty()
                    key == "hausgarten.payment.editor.v1" -> "payment"
                    key == "hausgarten.business.settings.draft" -> "business_settings"
                    else -> ""
                }, RestoredBrowserReviewRules.label(key))
            }
        }
        return rows
    }

    fun retainSelection(store: PixelLocalStore, selection: JSONObject) {
        val storage = selection.getJSONObject("selected_storage")
        val hash = selection.getString("selection_hash")
        if (PixelMcpWriteTools.hash(storage) != hash) throw IllegalStateException("Die gewählten Formularwerte haben sich verändert.")
        val entries = index(storage, selection.getJSONArray("selected"), hash)
        val keys = JSONArray(entries.map { INDEX + hash + ":" + it.getString("item_id") })
        val manifest = JSONObject().put("formatVersion", 1).put("selection_hash", hash).put("request_id", selection.getString("request_id"))
            .put("entry_keys", keys).put("index_hash", PixelMcpWriteTools.hash(JSONArray(entries)))
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            store.putOwnerValue(STORAGE + hash, storage.toString())
            for (i in entries.indices) store.putOwnerValue(keys.getString(i), entries[i].toString())
            store.putOwnerValue(MANIFEST + hash, manifest.toString())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        val readback = JSONObject(store.ownerValue(STORAGE + hash, "{}"))
        if (PixelMcpWriteTools.hash(readback) != hash) throw IllegalStateException("Übernommene Entwürfe konnten nicht vollständig zurückgelesen werden.")
        val readIndex = JSONArray()
        for (i in 0 until keys.length()) readIndex.put(JSONObject(store.ownerValue(keys.getString(i), "{}")))
        val readManifest = JSONObject(store.ownerValue(MANIFEST + hash, "{}"))
        if (PixelMcpWriteTools.hash(readManifest) != PixelMcpWriteTools.hash(manifest) || PixelMcpWriteTools.hash(readIndex) != manifest.getString("index_hash")) throw IllegalStateException("Die Entwurfsübersicht konnte nicht vollständig zurückgelesen werden.")
    }

    fun source(store: PixelLocalStore, entry: JSONObject): Any {
        val hash = entry.getString("selection_hash")
        val storage = JSONObject(store.ownerValue(STORAGE + hash, "{}"))
        if (PixelMcpWriteTools.hash(storage) != hash) throw IllegalStateException("Die erhaltene Entwurfsquelle hat sich verändert.")
        var current: Any = restoredParse(storage.getString(entry.getString("storage_key")))
        val path = entry.getJSONArray("path")
        for (i in 0 until path.length()) current = when (current) {
            is JSONObject -> current.get(path.getString(i))
            is JSONArray -> current.get(path.getInt(i))
            else -> throw IllegalStateException("Der erhaltene Entwurf ist nicht mehr eindeutig zugeordnet.")
        }
        return current
    }

    private fun mirrorKind(form: String, page: String): String = when {
        form == "entry" || page == "work-entry" -> "work_entry"
        form == "customer-garden-form" -> "garden"
        form == "customer-form" || page == "customer-edit" -> "customer"
        form == "order-form" -> "order"
        form == "invoice-draft-form" -> "invoice"
        form == "offer-form" -> "offer"
        form == "appointment-form" -> "appointment"
        form == "local-payment-form" -> "payment"
        form.contains("photo") -> "photo_edit"
        else -> ""
    }

    fun kindLabel(kind: String): String = mapOf("customer" to "Kunde", "order" to "Auftrag", "work_entry" to "Arbeitsaufzeichnung", "worklog" to "Arbeitsaufzeichnung", "invoice" to "Rechnung", "offer" to "Angebot", "appointment" to "Termin", "property" to "Objekt", "inquiry" to "Anfrage", "care" to "Pflegeplan", "care_plan" to "Pflegeplan", "visit" to "Einsatz", "equipment" to "Gerät", "material" to "Material", "garden" to "Gartenangaben", "photo_edit" to "Fotobeschreibung", "payment" to "Zahlung", "business_settings" to "Unternehmensdaten", "offer_decision" to "Angebotsentscheidung")[kind] ?: "Erhaltener Entwurf"
}

private data class RestoredEditor(val key: String, val section: String, val id: String, val mode: String, val draft: JSONObject, val args: JSONObject = JSONObject())
private fun restoredEditorEnvelope(plan: RestoredEditor): JSONObject = restoredCopy(plan.draft).put("_inbox_route", JSONObject().put("section", plan.section).put("id", plan.id).put("mode", plan.mode).put("args", plan.args))

private fun restoredFields(fields: JSONArray): JSONObject {
    val result = JSONObject()
    for (field in restoredObjects(fields)) {
        val key = field.optString("name").ifEmpty { field.optString("id") }
        if (key.isEmpty() || field.optString("type") in listOf("file", "password", "button", "submit")) continue
        val name = mapOf("date" to "work_date", "customer" to "customer_id", "order" to "order_id")[key] ?: key
        result.put(name, when {
            field.has("checked") -> field.getBoolean("checked")
            field.optJSONArray("selected") != null -> field.getJSONArray("selected")
            name in listOf("hours", "material_cost", "kilometers", "garden_area") -> field.optString("value").replace(',', '.').toDoubleOrNull() ?: field.optString("value")
            name == "break_minutes" -> field.optString("value").toIntOrNull() ?: field.optString("value")
            else -> field.optString("value")
        })
    }
    return result
}

private fun hiddenDraftField(key: String) = key.startsWith("_") || key.endsWith("_hash") || key.endsWith("_sha256") || key in setOf("formatVersion", "projection_version", "request_id", "session_id", "edit_id", "record_id", "hash", "contextHash", "orderHash", "dirty", "touched", "restore_review_required", "reading", "tool") || key.matches(Regex("(?i).*(password|passwd|secret|credential|recovery.?code|api.?key|authorization|access.?token|refresh.?token).*"))
private fun binaryDraftField(key: String, value: Any?) = key in setOf("full_data", "thumbnail_data", "chunk_base64") || key.endsWith("_base64") || key == "data" && value is String

@Composable
private fun RestoredCandidateValues(raw: String, controller: NativeAppController) {
    val value by produceState<Any?>(null, raw) { value = withContext(Dispatchers.IO) { restoredParse(raw) } }
    if (value == null) Text("Eingaben werden gelesen …") else {
        RestoredDraftSummary(value, controller)
        RestoredValueView(value, "Alle erhaltenen Angaben")
    }
}

@Composable
private fun RestoredDraftSummary(value: Any?, controller: NativeAppController) {
    val raw = value as? JSONObject ?: return
    val fields = raw.optJSONObject("values") ?: raw.optJSONArray("fields")?.let(::restoredFields) ?: raw
    NativeContext(controller, fields)
    for ((key, label) in listOf("name" to "Bezeichnung", "title" to "Tätigkeit", "activity" to "Tätigkeit", "work_date" to "Arbeitstag", "date" to "Datum", "performed_on" to "Erledigt am", "quantity_decimal" to "Menge", "unit" to "Einheit", "caption" to "Bildunterschrift", "notes" to "Notiz", "reason" to "Grund")) {
        val text = fields.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
        if (text.isNotBlank()) NativeInfoRow(label, nativeDisplayText(text))
    }
    if (fields.optString("session_id").isNotBlank()) NativeInfoRow("Zuordnung", "Erhaltene Arbeitssitzung · aktuellen Stand im Fachformular prüfen")
}

/** Structured, expandable values keep unknown fields readable without exposing a JSON editor or binary data. */
@Composable
private fun RestoredValueView(value: Any?, label: String, initiallyOpen: Boolean = false) {
    if (value is JSONObject && value.has("value") && (value.has("label") || value.has("name")) && value.has("type")) {
        if (value.optString("type") == "password" || hiddenDraftField(value.optString("name"))) return
        val title = value.optString("label").ifBlank { nativeLabel(value.optString("name").ifBlank { label }) }
        RestoredValueView(if (value.has("checked")) value.opt("checked") else value.opt("selected") ?: value.opt("value"), title)
        return
    }
    var open by remember(value, label) { mutableStateOf(initiallyOpen) }
    var limit by remember(value, label) { mutableIntStateOf(20) }
    when (value) {
        is JSONObject -> {
            val keys = value.keys().asSequence().filterNot(::hiddenDraftField).toList()
            TextButton(onClick = { open = !open }) { Text(label + if (open) " ▴" else " · ${keys.size} Angaben ▾") }
            if (open) Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in keys.take(limit)) if (binaryDraftField(key, value.opt(key))) Text(nativeLabel(key) + ": Datei wird getrennt geprüft") else RestoredValueView(value.opt(key), nativeLabel(key), false)
                if (keys.size > limit) TextButton(onClick = { limit += 20 }) { Text("Weitere Angaben lesen") }
            }
        }
        is JSONArray -> {
            TextButton(onClick = { open = !open }) { Text(label + " · ${value.length()} Einträge" + if (open) " ▴" else " ▾") }
            if (open) Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until minOf(limit, value.length())) RestoredValueView(value.opt(i), "Eintrag ${i + 1}")
                if (value.length() > limit) TextButton(onClick = { limit += 20 }) { Text("Weitere Einträge lesen") }
            }
        }
        null, JSONObject.NULL -> Text("$label: Nicht erfasst")
        else -> {
            val text = value.toString()
            Text("$label: " + if (text.startsWith("data:") || text.startsWith("blob:")) "Datei wird getrennt geprüft" else if (value is Boolean) { if (value) "Ja" else "Nein" } else text.ifBlank { "Leer" })
        }
    }
}

@Composable
private fun RestoredMediaDraft(controller: NativeAppController, graph: JSONObject, sourceKey: String) {
    val items = restoredObjects(graph.optJSONArray("items")).ifEmpty { listOf(graph) }
    val scope = rememberCoroutineScope()
    for ((index, item) in items.withIndex()) key(sourceKey, index) {
        val payload = item.optJSONObject("prepared_payload") ?: item.optJSONObject("payload") ?: JSONObject()
        val reference = item.optJSONObject("file") ?: item.optJSONObject("image") ?: payload.optJSONObject("data")
        val submitted = item.optBoolean("submitted") || item.optJSONObject("prepared_payload") != null
        val draftKey = "restored_media:" + PixelMcpWriteTools.hash(sourceKey + ":" + index + ":" + reference.toString())
        val markerKey = "native_restored_adoption:" + PixelMcpWriteTools.hash(draftKey)
        var checked by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        val adoptedPhoto by produceState("", markerKey) {
            try { value = withContext(Dispatchers.IO) { JSONObject(controller.store.ownerValue(markerKey, "{}")).optString("photo_id") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Der gespeicherte Fotobeleg konnte noch nicht gelesen werden. Die Quelle bleibt geschützt." }
        }
        val confirmed = item.optString("confirmed_photo_id").ifEmpty { adoptedPhoto }
        val file by produceState<File?>(null, reference?.toString()) {
            if (reference != null) value = withContext(Dispatchers.IO) { runCatching {
                val directory = File(controller.context.filesDir, "browser-media")
                BrowserMediaFiles.verifyReference(directory, reference)
                BrowserMediaFiles.referenceFile(directory, reference)
            }.getOrNull() }
        }
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fotoentwurf ${index + 1}", style = MaterialTheme.typography.titleMedium)
            if (file != null && reference?.optString("mime_type")?.startsWith("image/") == true) AsyncImage(
                model = ImageRequest.Builder(controller.context).data(file).size(640, 640).build(), imageLoader = controller.imageLoader,
                contentDescription = "Erhaltene Bilddatei", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(230.dp)
            ) else Text("Die Bilddatei ist noch nicht lesbar. Die Quelle bleibt erhalten.")
            if (message.isNotBlank()) Text(message)
            when {
                confirmed.isNotBlank() -> Button(onClick = { controller.navigate("photos", confirmed) }) { Text("Bereits gespeichertes Foto öffnen") }
                submitted -> {
                    Text("Die ursprüngliche Fotoaktion bleibt geschützt. Ihr Ergebnis zuerst anhand des gespeicherten Belegs lesen.")
                    Button(enabled = !busy, onClick = { scope.launch {
                        busy = true
                        try {
                            val original = restoredCopy(payload).apply { remove("data"); remove("thumbnail_data") }
                            val result = controller.query("inspect_edit_action", JSONObject().put("original_tool", "savePhoto").put("original_args", original))
                            if (result.optString("state") == "confirmed" && result.optString("entity_id").isNotEmpty()) controller.navigate("photos", result.getString("entity_id"))
                            else message = "Kein eindeutiger aktueller Fotobeleg. Die alte Aktion bleibt unverändert erhalten und wird nicht erneut gespeichert."
                        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { message = restoredError(failure) }
                        finally { busy = false }
                    } }) { Text("Ursprünglichen Fotobeleg lesen") }
                }
                else -> {
                    Row { Checkbox(checked, { checked = it }, enabled = !busy); Text("Ich habe diese erhaltene Bilddatei geprüft und möchte ihre Angaben im Fotoformular bewusst übernehmen.", Modifier.weight(1f)) }
                    Button(enabled = !busy && checked && file != null, modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
                        busy = true
                        try {
                            val current = withContext(Dispatchers.IO) {
                                val directory = File(controller.context.filesDir, "browser-media")
                                BrowserMediaFiles.verifyReference(directory, requireNotNull(reference))
                                BackupDeviceGuard(controller.context).current()
                            }
                            val origin = JSONObject().put("formatVersion", 1).put("installation_id", current.getString("installationId")).put("restore_epoch", current.getString("restoreEpoch"))
                            val source = graph.optJSONObject("source") ?: JSONObject()
                            val draft = JSONObject().put("selected_uri", Uri.fromFile(requireNotNull(file)).toString()).put("capture_context", origin.toString())
                                .put("customer_id", payload.optString("customer_id", source.optString("customer_id"))).put("caption", payload.optString("caption"))
                                .put("role", payload.optString("role", "documentation")).put("customer_visible", payload.optBoolean("customer_visible"))
                                .put("order_id", payload.optString("order_id", source.optString("order_id"))).put("worklog_id", payload.optString("worklog_id", source.optString("worklog_id")))
                                .put("session_id", payload.optString("session_id", source.optString("session_id"))).put("pending", "")
                                .put("_legacy_source", JSONObject().put("media_key", sourceKey).put("item_index", index))
                            val plan = RestoredEditor(draftKey, "photos", "", "import", draft)
                            val existing = controller.draft(draftKey)
                            if (existing == null) {
                                if (withContext(Dispatchers.IO) { controller.store.ownerValue(markerKey, "").isNotEmpty() }) throw IllegalStateException("Diese übernommene Fotoauswahl wurde bereits geschlossen. Gespeicherte Fotos und die Quelle bleiben erhalten.")
                                controller.adoptDraft(draftKey, restoredEditorEnvelope(plan))
                            } else if (existing.optString("pending").isBlank()) {
                                controller.adoptDraft(draftKey, restoredCopy(existing).put("capture_context", origin.toString()).put("selected_uri", Uri.fromFile(requireNotNull(file)).toString()))
                            }
                            withContext(Dispatchers.IO) { val marker = JSONObject(controller.store.ownerValue(markerKey, "{}")); controller.store.putOwnerValue(markerKey, marker.put("draft_key", draftKey).put("source_media", sourceKey).toString()) }
                            controller.navigate("photos", "", "import", JSONObject().put("_draft_key", draftKey))
                        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { message = restoredError(failure) }
                        finally { busy = false }
                    } }) { Text("Bilddatei im Fotoformular übernehmen") }
                }
            }
        } }
    }
}
