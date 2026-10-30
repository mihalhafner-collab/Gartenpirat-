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

/** Review and inbox share the existing checkpoint, owner_state and native editor envelopes. */
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Übernommene Formularstände prüfen", style = MaterialTheme.typography.headlineSmall)
            Text("Wähle je Formular eine Quelle. Gespeicherte Vorgänge werden dadurch nicht erneut ausgeführt. Nicht gewählte Werte bleiben im gesicherten Quellbestand erhalten.")
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
                RestoredCandidateValues(candidate.optString("value"))
            } }
        }
        if (current?.optBoolean("available") == true) item {
            Text("${current.optInt("other_browser_values_preserved")} weitere Browserwerte bleiben im Quellbestand erhalten.")
            Row { Text("Medien später gesondert prüfen", Modifier.weight(1f)); Switch(deferMedia, enabled = !busy && (!locked || reviewed && current.optBoolean("media_review_pending")), onCheckedChange = { deferMedia = it; acceptedOmissions = false }) }
            if (deferMedia) Text("Fotoentwürfe bleiben getrennt zur Prüfung erhalten. Die Formularübernahme bestätigt keine vollständige Fotoabdeckung.")
            else {
                Text("Wähle je Fotoentwurf eine Quelle. Die vorhandenen Dateireferenzen werden vor Aktivierung geprüft.")
                if (!mediaReady && mediaRequired) Text("Die Medienquelle ist noch nicht vollständig geprüft. Medien zurückstellen oder den gesicherten Quell- und Rückfallbestand prüfen.", color = MaterialTheme.colorScheme.error)
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
        if (current?.optBoolean("available") == true && !reviewed) item {
            Button(enabled = !busy && (deferMedia || !mediaRequired || mediaReady && (locked || acceptedOmissions)), modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
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
            } }) { Text(if (locked) "Bestätigte Auswahl zurücklesen und abschließen" else "Auswahl übernehmen und zurücklesen") }
        }
        if (reviewed && current?.optBoolean("media_review_pending") == true && !deferMedia) item {
            Button(enabled = !busy && mediaReady && acceptedOmissions, modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { RestoredBrowserCheckpoint(controller.context, controller.store).completeMedia(JSONObject()
                        .put("request_id", "native_media_review_" + UUID.randomUUID()).put("confirmed", true)
                        .put("expected_media_hash", current.getString("media_record_hash")).put("selected_media", JSONArray(selectedMedia))) }
                    refresh(); error = ""; controller.message("Die geprüfte Medienauswahl wurde übernommen.")
                } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
                finally { busy = false }
            } }) { Text("Geprüfte Fotoauswahl übernehmen") }
        }
        item { OutlinedButton(onClick = { controller.navigate("drafts") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Erhaltene Entwürfe öffnen") } }
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
private fun inboxEnvelope(plan: RestoredEditor): JSONObject = restoredCopy(plan.draft).put("_inbox_route", JSONObject().put("section", plan.section).put("id", plan.id).put("mode", plan.mode).put("args", plan.args))
private data class DraftInboxRow(val sqlKey: String, val draftKey: String, val label: String, val legacy: Boolean)
private data class LoadedInbox(val entry: JSONObject, val content: Any, val plan: RestoredEditor?, val action: JSONObject?, val closed: Boolean)

/** Both restored browser selections and restored native edits remain reachable after completion/restart. */
@Composable
internal fun NativeDraftInbox(controller: NativeAppController) {
    var rows by remember { mutableStateOf(emptyList<DraftInboxRow>()) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("") }
    var entry by remember { mutableStateOf<JSONObject?>(null) }
    var content by remember { mutableStateOf<Any?>(null) }
    var plan by remember { mutableStateOf<RestoredEditor?>(null) }
    var action by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var copyTo by rememberSaveable { mutableStateOf("worklog") }
    var copyConfirmed by remember { mutableStateOf(false) }
    var legacyClosed by remember { mutableStateOf(false) }
    var proof by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(selected.isNotEmpty()) { selected = ""; content = null; proof = null }
    LaunchedEffect(offset) {
        try {
            val page = withContext(Dispatchers.IO) {
                // Repair reachability for a review completed by an earlier native screen, without changing its decision.
                val review = RestoredBrowserCheckpoint(controller.context, controller.store).status(true)
                review.optJSONObject("selection")?.let { NativeRestoredDrafts.retainSelection(controller.store, it) }
                val found = ArrayList<DraftInboxRow>()
                controller.store.readableDatabase.rawQuery("SELECT key, CASE WHEN json_valid(value) THEN json_extract(value,'$._draft_key') END, CASE WHEN json_valid(value) THEN json_extract(value,'$.label') END FROM owner_state WHERE (key GLOB 'native_form_draft:*' OR key GLOB 'native_restored_draft:*' OR key GLOB 'browser_media:v1:active:*') AND value<>'' ORDER BY key LIMIT 41 OFFSET ?", arrayOf(offset.toString())).use { cursor ->
                    while (cursor.moveToNext()) {
                        val sql = cursor.getString(0); val key = cursor.getString(1).orEmpty()
                        val legacy = sql.startsWith("native_restored_draft:")
                        found.add(DraftInboxRow(sql, key, cursor.getString(2) ?: if (legacy) "Übernommener Entwurf" else if (sql.startsWith("browser_media:")) "Erhaltener Fotoentwurf" else draftKeyLabel(key), legacy))
                    }
                }
                found
            }
            more = page.size > 40; rows = page.take(40); error = ""
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
    }
    LaunchedEffect(selected) {
        content = null; entry = null; proof = null; copyConfirmed = false; legacyClosed = false; plan = null; action = null
        if (selected.isNotEmpty()) try {
            val loaded = withContext(Dispatchers.IO) {
                val metadata = JSONObject(controller.store.ownerValue(selected, "{}"))
                val value = if (selected.startsWith("native_restored_draft:")) NativeRestoredDrafts.source(controller.store, metadata)
                    else if (selected.startsWith("browser_media:")) BrowserMediaRepository(controller.context, controller.store).readRecord(metadata.getString("key")).get("record")
                    else metadata
                val raw = value as? JSONObject
                val mapped = if (raw == null) null else if (selected.startsWith("native_form_draft:")) nativeEditor(raw.optString("_draft_key"), raw) else legacyEditor(metadata, raw)
                val closed = selected.startsWith("native_restored_draft:") && mapped != null && controller.store.ownerValue("native_restored_adoption:" + PixelMcpWriteTools.hash(mapped.key), "").isNotEmpty() && controller.draft(mapped.key) == null
                LoadedInbox(metadata, value, mapped, raw?.let(::restoredAction), closed)
            }
            entry = loaded.entry; content = loaded.content; plan = loaded.plan; action = loaded.action; legacyClosed = loaded.closed; error = ""
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
    }
    val raw = content as? JSONObject
    val editor = plan
    val originalAction = action
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Erhaltene Entwürfe", style = MaterialTheme.typography.headlineSmall)
            Text("Öffnen übernimmt keine Geschäftsdaten. Frühere Speicheraktionen behalten ihre Kennung und werden zuerst anhand des gespeicherten Belegs geprüft.")
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (selected.isEmpty()) {
            if (rows.isEmpty()) item { Text("Keine erhaltenen Entwürfe in dieser Auswahl.") }
            items(rows, key = { it.sqlKey }) { row -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(row.label); Text(if (row.legacy) "Übernommene Formularquelle" else "Native Bearbeitung", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { selected = row.sqlKey }) { Text("Eingaben lesen und fortsetzen") }
            } } }
            item { Row { TextButton(enabled = offset > 0, onClick = { offset = (offset - 40).coerceAtLeast(0) }) { Text("Vorherige") }; TextButton(enabled = more, onClick = { offset += 40 }) { Text("Weitere") } } }
            item { OutlinedButton(onClick = { controller.navigate("restored_drafts") }) { Text("Formular- und Medienauswahl prüfen") } }
        } else {
            item { TextButton(onClick = { selected = ""; content = null; proof = null }) { Text("Zur Entwurfsübersicht") }; entry?.let { Text(restoredSourceLabel(it.optString("source")), style = MaterialTheme.typography.bodySmall) } }
            item { content?.let { RestoredValueView(it, "Erhaltene Eingaben", initiallyOpen = true) } ?: Text("Entwurf wird gelesen …") }
            if (selected.startsWith("browser_media:") && raw != null) item { RestoredMediaDraft(controller, raw, selected) }
            if (originalAction != null) item {
                Text("Eine frühere Speicheraktion ist erhalten. Ihre Argumente werden beim Öffnen des Fachformulars unverändert geschützt.")
                Button(enabled = !busy && originalAction.optString("tool").isNotBlank() && originalAction.optJSONObject("args") != null, onClick = { scope.launch {
                    busy = true
                    try { proof = controller.query("inspect_edit_action", JSONObject().put("original_tool", originalAction.getString("tool")).put("original_args", originalAction.getJSONObject("args"))); error = "" }
                    catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
                    finally { busy = false }
                } }) { Text("Beleg der ursprünglichen Aktion lesen") }
                proof?.let { NativeBusinessValues(it) }
            }
            if (editor != null) item {
                if (legacyClosed) Text("Diese übernommene Bearbeitung wurde bereits geschlossen. Ihre ursprünglichen Angaben bleiben lesbar; sie werden nicht erneut als alter Entwurf aktiviert.")
                Button(enabled = !busy && !legacyClosed, modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
                    busy = true
                    try {
                        if (selected.startsWith("native_restored_draft:")) {
                            val existing = controller.draft(editor.key)
                            val markerKey = "native_restored_adoption:" + PixelMcpWriteTools.hash(editor.key)
                            if (existing == null && withContext(Dispatchers.IO) { controller.store.ownerValue(markerKey, "").isNotEmpty() }) {
                                legacyClosed = true
                                throw IllegalStateException("Diese Bearbeitung wurde bereits geschlossen. Die erhaltene Quelle bleibt lesbar.")
                            }
                            if (existing == null) { controller.saveDraft(editor.key, inboxEnvelope(editor)); controller.flushDrafts() }
                            withContext(Dispatchers.IO) {
                                val marker = JSONObject().put("draft_key", editor.key).put("source_index", selected)
                                controller.store.putOwnerValue(markerKey, marker.toString())
                                if (PixelMcpWriteTools.hash(JSONObject(controller.store.ownerValue(markerKey, "{}"))) != PixelMcpWriteTools.hash(marker)) throw IllegalStateException("Die Entwurfszuordnung konnte nicht zurückgelesen werden.")
                            }
                        }
                        controller.navigate(editor.section, editor.id, editor.mode, restoredCopy(editor.args).put("_draft_key", editor.key))
                    } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
                    finally { busy = false }
                } }) { Text("Im Fachformular prüfen und fortsetzen") }
            }
            if ((editor == null || legacyClosed) && content != null) item {
                Text("Diese historischen Felder bleiben vollständig in ihrer Quelle erhalten. Du kannst die lesbaren Angaben bewusst als Notiz in ein neues Fachformular kopieren; frühere Aktionen werden dabei nicht ausgeführt.")
                NativeChoice("Ziel der Notiz", copyTo, listOf("customer" to "Kunde", "order" to "Auftrag", "worklog" to "Arbeitsaufzeichnung", "invoice" to "Rechnung", "offer" to "Angebot", "property" to "Objekt", "inquiry" to "Anfrage"), { copyTo = it; copyConfirmed = false })
                Row { Checkbox(copyConfirmed, { copyConfirmed = it }, enabled = !busy); Text("Ich möchte die angezeigten Angaben als Notiz übernehmen. Die ursprüngliche Quelle bleibt erhalten.", Modifier.weight(1f)) }
                Button(enabled = !busy && copyConfirmed, modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
                    busy = true
                    try {
                        val notes = restoredPlainText(content)
                        val copied = legacyEditor(JSONObject().put("kind", copyTo).put("selection_hash", PixelMcpWriteTools.hash(selected)).put("item_id", PixelMcpWriteTools.hash(copyTo + selected)), JSONObject().put("kind", copyTo).put("values", JSONObject().put("notes", notes))) ?: error("Das Zielformular ist nicht verfügbar.")
                        if (controller.draft(copied.key) == null) { controller.saveDraft(copied.key, inboxEnvelope(copied)); controller.flushDrafts() }
                        controller.navigate(copied.section, copied.id, copied.mode, restoredCopy(copied.args).put("_draft_key", copied.key))
                    } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = restoredError(failure) }
                    finally { busy = false }
                } }) { Text("Angaben als Notiz im Fachformular prüfen") }
            }
        }
    }
}

private fun draftKeyLabel(key: String) = when {
    key.startsWith("editor:customer:") -> "Kundenbearbeitung"
    key.startsWith("editor:order:") -> "Auftragsbearbeitung"
    key.startsWith("editor:worklog:") -> "Arbeitsaufzeichnung"
    key.startsWith("finance:invoices:") -> "Rechnungsentwurf"
    key.startsWith("finance:offers:") -> "Angebotsentwurf"
    key.startsWith("finance:payment:") -> "Zahlungserfassung"
    key.startsWith("planning:") -> "Terminplanung"
    key.startsWith("photo_") -> "Fotoentwurf"
    key.startsWith("revier:") -> "Objekt, Anfrage oder Pflege"
    else -> "Erhaltene Bearbeitung"
}

private fun restoredAction(raw: JSONObject): JSONObject? {
    raw.optJSONObject("original_args")?.let { args ->
        val key = raw.optString("_draft_key")
        val tool = raw.optString("original_tool").ifBlank { when { key.startsWith("editor:customer:") -> "saveCustomer"; key.startsWith("editor:order:") -> "saveOrder"; key.startsWith("editor:worklog:") -> "saveWorklog"; else -> "" } }
        return JSONObject().put("tool", tool).put("args", args)
    }
    val envelope = raw.optJSONObject("pending") ?: raw.optJSONObject("frozen") ?: raw.optJSONObject("request")
        ?: raw.optJSONObject("editor")?.let(::restoredAction) ?: return null
    if (envelope.has("tool") && envelope.optJSONObject("args") != null) return restoredCopy(envelope)
    if (envelope.opt("body") is String) {
        val body = runCatching { JSONObject(envelope.getString("body")) }.getOrNull()
        if (body != null) {
            val args = body.optJSONObject("body") ?: body
            val tool = envelope.optString("tool", body.optString("action"))
            val frozen = restoredCopy(args)
            if (!frozen.has("request_id") && envelope.optString("id").isNotEmpty()) frozen.put("request_id", envelope.getString("id"))
            return JSONObject().put("tool", tool).put("args", frozen)
        }
    }
    return JSONObject().put("tool", envelope.optString("tool")).put("args", envelope.optJSONObject("args") ?: envelope)
}

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

private fun legacyEditor(entry: JSONObject, original: JSONObject): RestoredEditor? {
    val raw = if (original.optJSONObject("editor") != null) restoredCopy(original.getJSONObject("editor")).apply { original.optJSONObject("request")?.let { put("pending", it) } } else original
    val kind = raw.optString("kind").ifEmpty { entry.optString("kind") }
    val context = raw.optJSONObject("context") ?: JSONObject()
    var values = restoredCopy(raw.optJSONObject("values") ?: raw.optJSONObject("data") ?: raw.optJSONObject("form") ?: raw)
    values.optJSONArray("fields")?.let { values = restoredFields(it) }
    raw.optJSONArray("fields")?.let { values = restoredFields(it) }
    val base = raw.optJSONObject("base") ?: context.optJSONObject("base") ?: raw.optJSONObject("baseline") ?: JSONObject()
    val pending = restoredAction(raw) ?: if (listOf("pending", "request", "frozen").any { raw.has(it) && !raw.isNull(it) && raw.opt(it)?.toString()?.isNotEmpty() == true }) JSONObject().put("tool", "").put("args", restoredCopy(raw)) else null
    val key = "restored:" + entry.optString("selection_hash") + ":" + entry.optString("item_id")
    val request = pending?.optJSONObject("args")?.optString("request_id")?.ifBlank { null } ?: raw.optString("request_id").ifBlank { "restored_" + PixelMcpWriteTools.hash(key).take(32) }
    val guarded = JSONObject().put("_restore_action_review_required", true).put("_legacy_source", JSONObject().put("selection_hash", entry.optString("selection_hash")).put("item_id", entry.optString("item_id")))
    fun args() = JSONObject()
    when (kind) {
        "customer", "order", "worklog", "work_entry", "garden" -> {
            val entity = when (kind) { "work_entry" -> "worklog"; "garden" -> "customer"; else -> kind }
            val gardenId = context.optString("record_id").ifEmpty { entry.optString("storage_key").takeIf { it.startsWith("hausgarten.garden.draft.") }?.removePrefix("hausgarten.garden.draft.").orEmpty() }.ifEmpty { values.optString("customer_id", values.optString("id")) }
            val id = base.optString("id").ifEmpty { if (kind == "garden") gardenId else if (entity in listOf("customer", "order")) values.optString("id") else "" }
            if (kind == "garden" && id.isEmpty()) return null
            if (kind == "garden") {
                val custom = when (val old = base.opt("custom_fields")) { is JSONObject -> restoredCopy(old); is String -> runCatching { JSONObject(old) }.getOrDefault(JSONObject()); else -> JSONObject() }
                for (field in values.keys().asSequence()) if (field != "garden_area" && !field.startsWith("_")) custom.put(field, values.get(field))
                values = restoredCopy(base).put("garden_area", values.opt("garden_area") ?: JSONObject.NULL).put("custom_fields", custom)
            }
            if (entity == "worklog" && values.optString("id").isEmpty() && context.optString("record_id").isNotEmpty()) values.put("id", context.getString("record_id"))
            val draft = restoredCopy(guarded).put("values", values).put("changes", values).put("base", base)
            pending?.let { draft.put("original_args", it.getJSONObject("args")) }
            return RestoredEditor(key, mapOf("customer" to "customers", "order" to "orders", "worklog" to "worklogs").getValue(entity), id, if (id.isEmpty()) "new" else "edit", draft)
        }
        "invoice", "offer" -> {
            val id = base.optString("id")
            val draft = restoredCopy(guarded).put("record", values).put("record_hash", raw.optString("hash", raw.optString("record_hash"))).put("request_id", request)
            pending?.let { draft.put("pending", it.getJSONObject("args")).put("original_tool", it.optString("tool")) }
            return RestoredEditor(key, if (kind == "invoice") "invoices" else "offers", id, if (id.isEmpty()) "new" else "edit", draft)
        }
        "appointment" -> {
            val data = values.optJSONObject("data") ?: raw.optJSONObject("data") ?: values
            val form = restoredCopy(data)
            for ((old, new) in listOf("localStart" to "local_start", "endOpen" to "end_open", "duration" to "duration_hours", "utcOffset" to "utc_offset", "occurrences" to "occurrences", "intervalWeeks" to "interval_weeks")) {
                when { values.has(old) -> form.put(new, values.get(old)); raw.has(old) -> form.put(new, raw.get(old)) }
            }
            if (!form.has("local_start") && data.optString("starts_at").isNotEmpty()) runCatching { form.put("local_start", Instant.parse(data.getString("starts_at")).atZone(ZoneId.of("Europe/Vienna")).toLocalDateTime().toString()) }
            val prior = base.optJSONObject("data") ?: base
            val id = prior.optString("id")
            val draft = restoredCopy(guarded).put("original", prior).put("values", form).put("record_hash", raw.optString("hash", raw.optString("record_hash"))).put("request_id", request).put("restore_review_required", true).put("dirty", true)
            pending?.let { draft.put("pending", it) }
            return RestoredEditor(key, "appointments", id, if (id.isEmpty()) "new" else "edit", draft)
        }
        "photo_edit" -> {
            val id = context.optString("record_id").ifEmpty { raw.optString("photo_id", values.optString("photo_id", values.optString("id"))).ifEmpty { base.optJSONObject("photo")?.optString("id").orEmpty() } }.ifEmpty { entry.optString("storage_key").takeIf { it.startsWith("hausgarten.photo.edit.") }?.removePrefix("hausgarten.photo.edit.").orEmpty() }
            if (id.isEmpty()) return null
            val draft = restoredCopy(values)
            for (field in guarded.keys()) draft.put(field, guarded.get(field))
            pending?.let { draft.put("pending", it.getJSONObject("args").toString()) }
            return RestoredEditor(key, "photos", id, "edit", draft)
        }
        "payment" -> {
            val correction = raw.optString("corrects", raw.optString("payment_id"))
            val allocations = values.optJSONArray("allocations")
            if (allocations != null) for (allocation in restoredObjects(allocations)) if (!allocation.has("amount") && allocation.has("value")) allocation.put("amount", allocation.get("value"))
            val draft = restoredCopy(guarded).put("form", values).put("ledger_hash", raw.optString("ledger_hash")).put("request_id", request)
            pending?.let { draft.put("args", it.getJSONObject("args")) }
            return RestoredEditor(key, "payments", raw.optString("invoice_id"), "edit", draft, args().apply { if (correction.isNotEmpty()) put("payment_id", correction); raw.optJSONObject("original")?.let { put("_correction", it) } })
        }
        "business_settings", "offer_decision" -> {
            val tool = if (kind == "business_settings") "update_business_settings" else pending?.optString("tool").orEmpty().ifBlank { if (!raw.has("acceptance")) "" else if (raw.optBoolean("acceptance")) "record_offer_acceptance" else "decline_offer" }
            if (tool.isEmpty()) return null
            val body = if (kind == "business_settings") {
                val updates = JSONObject()
                val source = context.optJSONObject("source") ?: raw.optJSONObject("source") ?: JSONObject()
                val allowed = listOf("business_name", "owner_name", "business_street", "business_postal_code", "business_city", "business_email", "business_phone", "business_bank", "business_iban", "business_bic", "business_legal_info", "invoice_issuer_confirmed", "tax_mode", "business_vat_id", "tax_exemption_text", "default_payment_days", "default_payment_terms", "offer_binding_text", "offer_nonbinding_text", "offer_time_material_text")
                for (field in allowed) if (values.has(field)) updates.put(field, if (field == "default_payment_days" && values.opt(field) is String) values.optString(field).toIntOrNull() ?: values.get(field) else values.get(field))
                JSONObject().put("expected_record_hash", source.optString("record_hash")).put("updates", updates).put("request_id", request)
            } else {
                JSONObject().put("offer_id", raw.optString("offer_id")).put("expected_record_hash", raw.optString("record_hash")).put("request_id", request).apply {
                    if (tool == "record_offer_acceptance") { put("evidence", raw.optString("evidence")); if (raw.optString("accepted_at").isNotEmpty()) put("accepted_at", raw.getString("accepted_at")) }
                    else put("reason", raw.optString("reason"))
                }
            }
            val draft = restoredCopy(guarded).put("values", body).put("original_tool", tool)
            pending?.let { draft.put("original_args", it.getJSONObject("args")) }
            return RestoredEditor(key, "action", tool, NativeRestoredDrafts.kindLabel(kind), draft)
        }
    }
    val normalized = if (kind == "care") "care_plan" else kind
    val section = when (normalized) { "property" -> "properties"; "inquiry", "inspection", "direct_acceptance" -> "inquiries"; "care_plan", "generate" -> "care_plans"; "visit" -> "visits"; "material", "movement" -> "materials"; "equipment", "maintenance_plan", "maintenance_done", "maintenance_adjust", "equipment_use" -> "equipment"; else -> return null }
    val id = if (raw.optBoolean("editing")) raw.optString("record_id") else base.optString("id")
    val prior = restoredCopy(base).apply { if (id.isNotEmpty() && !has("id")) put("id", id) }
    val draft = restoredCopy(guarded).put("kind", normalized).put("original", prior).put("values", values).put("record_hash", raw.optString("record_hash")).put("request_id", request).put("restore_review_required", true).put("dirty", true)
    pending?.let { draft.put("pending", it) }
    val modes = setOf("inspection", "direct_acceptance", "generate", "maintenance_plan", "maintenance_done", "maintenance_adjust", "movement", "equipment_use")
    return RestoredEditor(key, section, id, if (normalized in modes) normalized else if (id.isEmpty()) "new" else "edit", draft, restoredCopy(values).apply { remove("_draft_key") })
}

private fun nativeEditor(key: String, raw: JSONObject): RestoredEditor? {
    if (key.isEmpty()) return null
    raw.optJSONObject("_inbox_route")?.let { route -> return RestoredEditor(key, route.getString("section"), route.optString("id"), route.optString("mode"), raw, route.optJSONObject("args") ?: JSONObject()) }
    if (key.startsWith("restored:")) return null
    fun target(section: String, id: String, mode: String = if (id.isEmpty()) "new" else "edit", args: JSONObject = JSONObject()) = RestoredEditor(key, section, id, mode, raw, args)
    return when {
        key.startsWith("editor:") -> { val parts = key.split(':'); val section = mapOf("customer" to "customers", "order" to "orders", "worklog" to "worklogs")[parts.getOrNull(1)] ?: return null; val id = parts.drop(2).joinToString(":"); if (id.startsWith("duplicate:")) target(section, id.removePrefix("duplicate:"), "duplicate") else target(section, id.takeUnless { it == "new" }.orEmpty()) }
        key.startsWith("finance:payment:") -> { val id = key.removePrefix("finance:payment:"); target("payments", id.removePrefix("new:").takeIf { id.startsWith("new:") }.orEmpty(), "edit", JSONObject().apply { if (!id.startsWith("new:")) put("payment_id", id) }) }
        key.startsWith("finance:issue:") -> target("invoices", key.removePrefix("finance:issue:"), "issue")
        key.startsWith("finance:") -> { val parts = key.split(':'); val section = parts.getOrNull(1) ?: return null; target(section, parts.drop(2).joinToString(":").takeUnless { it == "new" }.orEmpty()) }
        key.startsWith("planning:appointment:") -> target("appointments", key.removePrefix("planning:appointment:").takeUnless { it == "new" }.orEmpty())
        key.startsWith("revier:") -> { val plan = legacyEditor(JSONObject().put("kind", raw.optString("kind")), raw) ?: return null; plan.copy(key = key, draft = raw) }
        key == "photo_import" -> target("photos", "", "import")
        key.startsWith("photo_edit_") -> target("photos", key.removePrefix("photo_edit_"), "edit")
        key.startsWith("action:") -> { val tool = raw.optString("original_tool", key.removePrefix("action:").substringBefore(':')); target("action", tool, "Erhaltene Aktion") }
        else -> null
    }
}

private fun hiddenDraftField(key: String) = key.startsWith("_") || key.endsWith("_hash") || key.endsWith("_sha256") || key in setOf("formatVersion", "projection_version", "request_id", "session_id", "edit_id", "record_id", "hash", "contextHash", "orderHash", "dirty", "touched", "restore_review_required", "reading", "tool") || key.matches(Regex("(?i).*(password|passwd|secret|credential|recovery.?code|api.?key|authorization|access.?token|refresh.?token).*"))
private fun binaryDraftField(key: String, value: Any?) = key in setOf("full_data", "thumbnail_data", "chunk_base64") || key.endsWith("_base64") || key == "data" && value is String

@Composable
private fun RestoredCandidateValues(raw: String) {
    val value by produceState<Any?>(null, raw) { value = withContext(Dispatchers.IO) { restoredParse(raw) } }
    if (value == null) Text("Eingaben werden gelesen …") else RestoredValueView(value, "Erhaltene Eingaben")
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

private fun restoredPlainText(value: Any?, prefix: String = ""): String = when (value) {
    is JSONObject -> if (value.has("value") && value.has("type") && (value.has("name") || value.has("label"))) {
        if (value.optString("type") == "password" || hiddenDraftField(value.optString("name"))) "" else restoredPlainText(if (value.has("checked")) value.opt("checked") else value.opt("selected") ?: value.opt("value"), value.optString("label").ifBlank { nativeLabel(value.optString("name")) })
    } else value.keys().asSequence().filterNot { hiddenDraftField(it) || binaryDraftField(it, value.opt(it)) }.map { key -> restoredPlainText(value.opt(key), listOf(prefix, nativeLabel(key)).filter { it.isNotBlank() }.joinToString(" · ")) }.filter { it.isNotBlank() }.joinToString("\n")
    is JSONArray -> (0 until value.length()).joinToString("\n") { restoredPlainText(value.opt(it), "$prefix · ${it + 1}") }
    null, JSONObject.NULL -> "$prefix: Nicht erfasst"
    else -> if (value.toString().startsWith("data:") || value.toString().startsWith("blob:")) "$prefix: Datei wird getrennt geprüft" else "$prefix: " + if (value is Boolean) { if (value) "Ja" else "Nein" } else value.toString()
}

/** Native access to already selected legacy blobs; an old submitted action is read, never replayed. */
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
                                controller.adoptDraft(draftKey, inboxEnvelope(plan))
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
