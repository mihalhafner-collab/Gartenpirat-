package at.hausgarten.service

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Native photo views. Every business write uses the shared owner path. */
@Composable
internal fun NativeMediaScreen(section: String, id: String, controller: NativeAppController) {
    key(section, id, controller.route.mode, controller.route.args.toString()) {
        NativePhotoLibrary(id, controller)
    }
}

private val photoRoles =
    linkedMapOf(
        "documentation" to "Dokumentation",
        "profile" to "Profilfoto",
        "before" to "Vorher",
        "during" to "Währenddessen",
        "after" to "Nachher",
        "damage" to "Schaden",
        "other" to "Sonstiges",
    )

private fun mediaRequest() = "native_photo_" + UUID.randomUUID().toString().replace("-", "")

private fun mediaError(error: Throwable) =
    error.message?.takeIf { it.isNotBlank() }
        ?: "Der Vorgang konnte noch nicht bestätigt werden. Eingaben bleiben erhalten."

private fun mediaInspectionMessage(proof: JSONObject): String =
    when (proof.optString("state")) {
        "content_removed" ->
            "Der ursprüngliche Eintrag wurde entfernt. Die Eingaben bleiben zur Prüfung erhalten. Es wurde nichts erneut gespeichert."
        "changed" ->
            "Der ursprüngliche Eintrag wurde inzwischen verändert. Angaben und Vorgangskennung bleiben erhalten. Es wurde nichts erneut gespeichert."
        else ->
            "Das Ergebnis ist noch offen. Eingaben und ursprünglicher Vorgang bleiben erhalten. Es wurde nichts erneut gespeichert."
    }

private fun jsonRows(rows: JSONArray?): List<JSONObject> =
    (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }

private suspend fun mediaWrite(
    controller: NativeAppController,
    tool: String,
    args: JSONObject,
): JSONObject {
    val response = controller.call(tool, args)
    if (!response.optBoolean("ok"))
        throw IllegalStateException(
            response.optJSONObject("error")?.optString("message")
                ?: "Der Vorgang konnte noch nicht bestätigt werden."
        )
    return response.optJSONObject("data") ?: JSONObject()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativePhotoLibrary(initialId: String, controller: NativeAppController) {
    val listStates = rememberSaveableStateHolder()
    val imeVisible = WindowInsets.isImeVisible
    var selected by rememberSaveable(initialId) { mutableStateOf(initialId) }
    var importContext by
        rememberSaveable(initialId) { mutableStateOf(controller.route.args.toString()) }
    var importing by
        rememberSaveable(initialId) { mutableStateOf(controller.route.mode == "import") }
    var editorDraftKey by
        rememberSaveable(initialId) {
            mutableStateOf(controller.route.args.optString("_draft_key"))
        }
    var archive by rememberSaveable(initialId) { mutableStateOf(false) }
    var settings by rememberSaveable(initialId) { mutableStateOf(false) }
    when {
        importing ->
            NativePhotoImport(controller, editorDraftKey, JSONObject(importContext)) {
                importing = false
                editorDraftKey = ""
            }
        selected.isNotBlank() ->
            NativePhotoDetail(selected, controller, editorDraftKey) {
                selected = ""
                editorDraftKey = ""
            }
        archive -> NativePhotoTrash(controller) { archive = false }
        settings -> NativePhotoSettings(controller) { settings = false }
        else ->
            listStates.SaveableStateProvider("photo-list") {
                val repository =
                    remember(controller.store) { NativeDataRepository(controller.store) }
                var search by rememberSaveable { mutableStateOf("") }
                var customerId by rememberSaveable {
                    mutableStateOf(controller.route.args.optString("customer_id"))
                }
                var customerLabel by rememberSaveable { mutableStateOf("") }
                var role by rememberSaveable { mutableStateOf("") }
                var dateFrom by rememberSaveable {
                    mutableStateOf(controller.route.args.optString("date_from"))
                }
                var dateTo by rememberSaveable {
                    mutableStateOf(controller.route.args.optString("date_to"))
                }
                var orderId by rememberSaveable {
                    mutableStateOf(controller.route.args.optString("order_id"))
                }
                var orderLabel by rememberSaveable { mutableStateOf("") }
                var worklogId by rememberSaveable {
                    mutableStateOf(controller.route.args.optString("worklog_id"))
                }
                var worklogLabel by rememberSaveable { mutableStateOf("") }
                var sessionId by rememberSaveable {
                    mutableStateOf(controller.route.args.optString("session_id"))
                }
                var offset by rememberSaveable { mutableIntStateOf(0) }
                var filterOpen by rememberSaveable { mutableStateOf(false) }
                var picker by remember { mutableStateOf("") }
                var readFailure by remember { mutableStateOf("") }
                var readRetry by remember { mutableIntStateOf(0) }
                LaunchedEffect(customerId, orderId, worklogId) {
                    suspend fun label(collection: String, id: String): String {
                        if (id.isBlank()) return ""
                        val row =
                            repository.detail(collection, id)
                                ?: return "Eintrag nicht mehr vorhanden"
                        return row.optString("name")
                            .ifBlank { row.optString("title") }
                            .ifBlank { row.optString("activity") }
                            .ifBlank { "Ausgewählter Eintrag" }
                    }
                    try {
                        customerLabel = label("customers", customerId)
                        orderLabel = label("orders", orderId)
                        worklogLabel = label("worklogs", worklogId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        readFailure = mediaError(failure)
                    }
                }
                val pageFlow =
                    remember(
                        repository,
                        search,
                        role,
                        customerId,
                        dateFrom,
                        dateTo,
                        orderId,
                        worklogId,
                        sessionId,
                        offset,
                        readRetry,
                    ) {
                        repository
                            .watchList(
                                "photos",
                                search = search,
                                customerId = customerId,
                                offset = offset,
                                limit = 40,
                                role = role,
                                dateFrom = dateFrom,
                                dateTo = dateTo,
                                orderId = orderId,
                                worklogId = worklogId,
                                sessionId = sessionId,
                            )
                            .catch { readFailure = NATIVE_LIST_READ_ERROR }
                    }
                val displayFlow =
                    remember(pageFlow) {
                        pageFlow
                            .map<NativeListPage, NativeListPage?> { it }
                            .onStart {
                                readFailure = ""
                                emit(null)
                            }
                    }
                val loadedPage by displayFlow.collectAsStateWithLifecycle(null)
                val page = loadedPage ?: NativeListPage(emptyList(), 0, offset, 40)
                val listLoading = loadedPage == null && readFailure.isEmpty()
                val photoActions: @Composable () -> Unit = {
                    NativeActionBar(
                        "Foto hinzufügen",
                        {
                            importContext =
                                JSONObject()
                                    .put("customer_id", customerId)
                                    .put("order_id", orderId)
                                    .put("worklog_id", worklogId)
                                    .put("session_id", sessionId)
                                    .toString()
                            importing = true
                        },
                    )
                }
                NativeSearchList(
                    "Fotos suchen",
                    search,
                    {
                        search = it
                        offset = 0
                    },
                    if (listLoading) "Wird geladen …" else "${page.total} Treffer",
                    onFilters = { filterOpen = true },
                ) {
                    if (readFailure.isNotEmpty()) {
                        Text(
                            readFailure,
                            Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                readFailure = ""
                                readRetry++
                            }
                        ) {
                            Text("Fotos erneut lesen")
                        }
                    }
                    val active =
                        listOf(
                                customerLabel,
                                photoRoles[role].orEmpty(),
                                dateFrom
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "Ab ${nativeDisplayDate(it)}" }
                                    .orEmpty(),
                                dateTo
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "Bis ${nativeDisplayDate(it)}" }
                                    .orEmpty(),
                                orderLabel,
                                worklogLabel,
                                if (sessionId.isNotBlank()) "Laufende Sitzung" else "",
                            )
                            .filter { it.isNotBlank() }
                    if (active.isNotEmpty() && !imeVisible)
                        Text(
                            active.joinToString(" · "),
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    if (page.rows.isEmpty())
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (listLoading) CircularProgressIndicator()
                            else if (readFailure.isEmpty()) Text("Keine Fotos für diese Auswahl.")
                        }
                    else if (imeVisible || search.isNotBlank())
                        LazyColumn(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            items(page.rows, key = { it.id }) { row ->
                                ListItem(
                                    headlineContent = { Text(row.title) },
                                    supportingContent = { Text(nativeDisplayText(row.subtitle)) },
                                    modifier =
                                        Modifier.fillMaxWidth().clickable { selected = row.id },
                                )
                            }
                            if (!imeVisible && search.isNotBlank()) item { photoActions() }
                        }
                    else
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(144.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(page.rows, key = { it.id }) { row ->
                                Card(onClick = { selected = row.id }) {
                                    NativePhotoImage(
                                        row.id,
                                        controller,
                                        Modifier.fillMaxWidth().height(132.dp),
                                        ContentScale.Crop,
                                        thumbnailReference = row.thumbnailReference,
                                    )
                                    Text(row.title, Modifier.padding(8.dp), maxLines = 2)
                                    Text(
                                        nativeDisplayText(row.subtitle),
                                        Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    if (!imeVisible)
                        MediaPagination(offset, page.rows.size, page.total, 40) { offset = it }
                    if (!imeVisible && search.isBlank()) {
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            TextButton(onClick = { archive = true }) { Text("Papierkorb") }
                            TextButton(onClick = { settings = true }) { Text("Fotoqualität") }
                        }
                        photoActions()
                    }
                }
                if (filterOpen && picker.isBlank())
                    Dialog(onDismissRequest = { filterOpen = false }) {
                        Surface(shape = MaterialTheme.shapes.large) {
                            Column(
                                Modifier.heightIn(max = 600.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Fotos filtern", style = MaterialTheme.typography.titleLarge)
                                TextButton({
                                    search = ""
                                    customerId = ""
                                    customerLabel = ""
                                    role = ""
                                    dateFrom = ""
                                    dateTo = ""
                                    orderId = ""
                                    orderLabel = ""
                                    worklogId = ""
                                    worklogLabel = ""
                                    sessionId = ""
                                    offset = 0
                                }) {
                                    Text("Zurücksetzen")
                                }
                                MediaChoice(
                                    "Kunde",
                                    customerLabel.ifBlank { "Alle Kunden" },
                                    { picker = "customers" },
                                    {
                                        customerId = ""
                                        customerLabel = ""
                                        offset = 0
                                    },
                                )
                                MediaDropdown(
                                    "Rolle",
                                    role,
                                    linkedMapOf("" to "Alle Rollen").apply { putAll(photoRoles) },
                                ) {
                                    role = it
                                    offset = 0
                                }
                                NativeDateField(
                                    "Von",
                                    dateFrom,
                                    {
                                        dateFrom = it
                                        offset = 0
                                    },
                                )
                                NativeDateField(
                                    "Bis",
                                    dateTo,
                                    {
                                        dateTo = it
                                        offset = 0
                                    },
                                )
                                MediaChoice(
                                    "Auftrag",
                                    orderLabel.ifBlank { "Alle Aufträge" },
                                    { picker = "orders" },
                                    {
                                        orderId = ""
                                        orderLabel = ""
                                        offset = 0
                                    },
                                )
                                MediaChoice(
                                    "Arbeit",
                                    worklogLabel.ifBlank { "Alle Arbeiten" },
                                    { picker = "worklogs" },
                                    {
                                        worklogId = ""
                                        worklogLabel = ""
                                        offset = 0
                                    },
                                )
                                val scope = rememberCoroutineScope()
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val session =
                                                    controller.query(
                                                        "get_active_work_session",
                                                        JSONObject(),
                                                    )
                                                sessionId =
                                                    session.optString(
                                                        "id",
                                                        session.optString("session_id"),
                                                    )
                                                if (sessionId.isBlank())
                                                    controller.message(
                                                        "Keine laufende Arbeit vorhanden."
                                                    )
                                                offset = 0
                                            } catch (error: Exception) {
                                                controller.message(mediaError(error))
                                            }
                                        }
                                    }
                                ) {
                                    Text("Laufende Arbeit")
                                }
                                Button(onClick = { filterOpen = false }, Modifier.fillMaxWidth()) {
                                    Text("Anwenden")
                                }
                            }
                        }
                    }
                if (picker.isNotBlank())
                    MediaEntityPicker(picker, controller, customerId, dismiss = { picker = "" }) {
                        row ->
                        when (picker) {
                            "customers" -> {
                                customerId = row.id
                                customerLabel =
                                    listOf(row.title, row.address)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                orderId = ""
                                orderLabel = ""
                                worklogId = ""
                                worklogLabel = ""
                            }
                            "orders" -> {
                                orderId = row.id
                                orderLabel = row.title
                            }
                            "worklogs" -> {
                                worklogId = row.id
                                worklogLabel = row.title
                            }
                        }
                        offset = 0
                        picker = ""
                    }
            }
    }
}

/** Resolve on IO. A projected valid preview must not open metadata or an original. */
internal fun resolveNativePhotoSource(
    attachments: PhotoAttachmentStore,
    thumbnailReference: String,
    metadata: JSONObject?,
    original: Boolean,
    loadMetadata: () -> JSONObject?,
): File? {
    val preferred =
        thumbnailReference.ifBlank { metadata?.optString("thumbnail_storage_name").orEmpty() }
    if (!original && preferred.isNotBlank())
        runCatching { attachments.verifiedThumbnailFile(preferred) }
            .getOrNull()
            ?.let {
                return it
            }
    val record = metadata ?: loadMetadata() ?: return null
    val preview = record.optString("thumbnail_storage_name")
    if (!original && preview.isNotBlank() && preview != preferred)
        runCatching { attachments.verifiedThumbnailFile(preview) }
            .getOrNull()
            ?.let {
                return it
            }
    // Legacy/missing previews: one streamed integrity check for this item, then Coil's sampled file
    // decode.
    return attachments.verifiedFile(record)
}

/**
 * Projected previews avoid original and metadata reads; Coil alone decodes/caches bounded files.
 */
@Composable
internal fun NativePhotoImage(
    photoId: String,
    controller: NativeAppController,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    metadata: JSONObject? = null,
    thumbnailReference: String = "",
    original: Boolean = false,
) {
    val source by
        produceState<File?>(null, photoId, metadata?.toString(), thumbnailReference, original) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            resolveNativePhotoSource(
                                controller.store.photoAttachmentStore(),
                                thumbnailReference,
                                metadata,
                                original,
                            ) {
                                controller.store.photoAttachment(photoId)
                            }
                        }
                        .getOrNull()
                }
        }
    var failed by remember(photoId, source) { mutableStateOf(false) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val width = with(density) { maxWidth.roundToPx() }.coerceIn(32, 1280)
        val height = with(density) { maxHeight.roundToPx() }.coerceIn(32, 1280)
        if (source != null)
            AsyncImage(
                model =
                    ImageRequest.Builder(controller.context)
                        .data(source)
                        .size(width, height)
                        .build(),
                imageLoader = controller.imageLoader,
                contentDescription = "Foto",
                contentScale = scale,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        if (source == null || failed)
            Text(
                "Foto nicht lesbar",
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
private fun NativePhotoDetail(
    id: String,
    controller: NativeAppController,
    draftKeyOverride: String = "",
    close: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var record by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var caption by rememberSaveable(id) { mutableStateOf("") }
    var capture by rememberSaveable(id) { mutableStateOf("") }
    var role by rememberSaveable(id) { mutableStateOf("documentation") }
    var visible by rememberSaveable(id) { mutableStateOf(false) }
    var order by rememberSaveable(id) { mutableStateOf("") }
    var orderTitle by rememberSaveable(id) { mutableStateOf("") }
    var worklog by rememberSaveable(id) { mutableStateOf("") }
    var workTitle by rememberSaveable(id) { mutableStateOf("") }
    var session by rememberSaveable(id) { mutableStateOf("") }
    var pending by rememberSaveable(id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    var detailsOpen by rememberSaveable(id) { mutableStateOf(false) }
    var discardChanges by remember { mutableStateOf(false) }
    val draftKey = draftKeyOverride.ifEmpty { "photo_edit_" + id }
    var draftReady by remember { mutableStateOf(false) }
    var draftTouched by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(id, revision) {
        try {
            val draft = if (revision == 0) controller.draft(draftKey) else null
            val read =
                try {
                    controller.query("get_photo", JSONObject().put("photo_id", id))
                } catch (failure: Exception) {
                    if (draft == null) throw failure else null
                }
            record = read
            if (pending.isBlank() || draft != null) {
                val p = draft ?: requireNotNull(read).getJSONObject("photo")
                caption = p.optString("caption")
                capture = p.optString("capture_time")
                role = p.optString("role", "documentation")
                visible = p.optBoolean("customer_visible")
                order = p.optString("order_id")
                worklog = p.optString("worklog_id")
                session = p.optString("session_id")
                pending = draft?.optString("pending").orEmpty()
            }
            draftTouched = draft != null
            draftReady = true
            error =
                if (read == null)
                    "Das aktuelle Foto ist nicht lesbar. Erhaltene Angaben und die ursprüngliche Aktion bleiben zur Prüfung geöffnet."
                else ""
        } catch (failure: Exception) {
            error = mediaError(failure)
        }
    }
    fun draftValue() =
        JSONObject()
            .put("caption", caption)
            .put("capture_time", capture)
            .put("role", role)
            .put("customer_visible", visible)
            .put("order_id", order)
            .put("worklog_id", worklog)
            .put("session_id", session)
            .put("pending", pending)
    fun leave() {
        if (!busy)
            scope.launch {
                try {
                    if (draftTouched) {
                        controller.saveDraft(draftKey, draftValue())
                        controller.flushDrafts()
                    }
                    close()
                } catch (failure: Exception) {
                    controller.message(mediaError(failure))
                }
            }
    }
    BackHandler { leave() }
    val draftSnapshot = draftValue().toString()
    LaunchedEffect(draftSnapshot, draftReady, draftTouched) {
        if (draftReady && draftTouched) controller.saveDraft(draftKey, JSONObject(draftSnapshot))
    }
    val photo = record?.optJSONObject("photo")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { leave() }) { Text("Zurück") }
            Text("Foto", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeStatus(
                if (pending.isNotBlank()) "Speichern nicht bestätigt"
                else if (draftTouched) "Ungespeicherte Änderungen" else "Foto gespeichert",
                if (pending.isNotBlank()) NativeStatusTone.Attention
                else if (draftTouched) NativeStatusTone.Neutral else NativeStatusTone.Confirmed,
            )
            if (photo != null) NativeContext(controller, photo)
            if (error.isNotBlank())
                Text(
                    error + " Bereits gespeicherte Arbeit bleibt erhalten.",
                    color = MaterialTheme.colorScheme.error,
                )
            NativePhotoImage(
                id,
                controller,
                Modifier.fillMaxWidth().height(260.dp).clickable { fullscreen = true },
                ContentScale.Fit,
                original = true,
            )
            TextButton(onClick = { fullscreen = true }) { Text("Vollbild und Vergrößern") }
            OutlinedTextField(
                caption,
                {
                    caption = it
                    draftTouched = true
                },
                Modifier.fillMaxWidth(),
                label = { Text("Bildunterschrift") },
                enabled = !busy && pending.isBlank(),
            )
            NativeDateTimeField(
                "Aufnahmedatum",
                capture,
                { selectedTime ->
                    capture =
                        runCatching {
                                java.time.LocalDateTime.parse(selectedTime)
                                    .atZone(java.time.ZoneId.of("Europe/Vienna"))
                                    .toInstant()
                                    .toString()
                            }
                            .getOrDefault(selectedTime)
                    draftTouched = true
                },
                enabled = !busy && pending.isBlank(),
            )
            MediaDropdown("Rolle", role, photoRoles, enabled = !busy && pending.isBlank()) {
                role = it
                draftTouched = true
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    visible,
                    {
                        visible = it
                        draftTouched = true
                    },
                    enabled = !busy && pending.isBlank(),
                )
                Text("Für Kunden sichtbar")
            }
            MediaChoice(
                "Auftrag",
                orderTitle.ifBlank {
                    if (order.isBlank()) "Nicht zugeordnet"
                    else photo?.optString("order_title").orEmpty().ifBlank { "Auftrag zugeordnet" }
                },
                { picker = "orders" },
                {
                    order = ""
                    orderTitle = ""
                    draftTouched = true
                },
                pending.isBlank(),
            )
            MediaChoice(
                "Arbeit",
                workTitle.ifBlank {
                    if (worklog.isBlank()) "Nicht zugeordnet" else "Arbeit zugeordnet"
                },
                { picker = "worklogs" },
                {
                    worklog = ""
                    workTitle = ""
                    draftTouched = true
                },
                pending.isBlank(),
            )
            if (session.isNotBlank()) Text("Mit einer Arbeitssitzung verknüpft.")
            TextButton(
                enabled = !busy && pending.isBlank(),
                onClick = {
                    scope.launch {
                        try {
                            val active = controller.query("get_active_work_session", JSONObject())
                            val customer = photo?.optString("customer_id")
                            if (active.optString("customer_id") != customer)
                                throw IllegalArgumentException(
                                    "Die laufende Arbeit gehört zu einem anderen Kunden."
                                )
                            session = active.optString("id", active.optString("session_id"))
                            order = active.optString("order_id")
                            worklog = active.optString("finalized_worklog_id")
                            draftTouched = true
                        } catch (failure: Exception) {
                            controller.message(mediaError(failure))
                        }
                    }
                },
            ) {
                Text("Laufender Arbeit zuordnen")
            }
            if (session.isNotBlank())
                TextButton(
                    enabled = !busy && pending.isBlank(),
                    onClick = {
                        session = ""
                        draftTouched = true
                    },
                ) {
                    Text("Sitzungsverknüpfung lösen")
                }
            if (photo != null) {
                TextButton({ detailsOpen = !detailsOpen }) {
                    Text(if (detailsOpen) "Dateiangaben schließen" else "Dateiangaben ansehen")
                }
                if (detailsOpen)
                    NativeInfoRow(
                        "Bilddatei",
                        "${photo.optInt("width")} × ${photo.optInt("height")} Pixel · ${photo.optLong("byte_size") / 1024} KB",
                    )
            }
            if (draftTouched)
                TextButton(
                    enabled = !busy && pending.isBlank(),
                    onClick = { discardChanges = true },
                ) {
                    Text("Änderungen verwerfen")
                }
            NativeDeleteButton(controller, "photo", id, enabled = !busy && pending.isBlank()) {
                close()
            }
        }
        NativeActionBar(
            primaryLabel =
                if (busy) "Speichert …"
                else if (pending.isNotBlank()) "Ergebnis prüfen" else "Fotoangaben speichern",
            onPrimary = {
                scope.launch {
                    busy = true
                    try {
                        if (pending.isNotBlank()) {
                            val proof =
                                controller.query(
                                    "inspect_edit_action",
                                    JSONObject()
                                        .put("original_tool", "update_photo")
                                        .put("original_args", JSONObject(pending)),
                                )
                            if (proof.optString("state") != "confirmed") {
                                error = mediaInspectionMessage(proof)
                                return@launch
                            }
                        } else {
                            draftTouched = true
                            pending =
                                JSONObject()
                                    .put("request_id", mediaRequest())
                                    .put("photo_id", id)
                                    .put("expected_record_hash", record!!.getString("record_hash"))
                                    .put(
                                        "updates",
                                        JSONObject()
                                            .put("caption", caption)
                                            .put("capture_time", capture)
                                            .put("role", role)
                                            .put("customer_visible", visible)
                                            .put("order_id", order)
                                            .put("worklog_id", worklog)
                                            .put("session_id", session),
                                    )
                                    .toString()
                            controller.saveDraft(draftKey, draftValue())
                            controller.flushDrafts()
                            mediaWrite(controller, "update_photo", JSONObject(pending))
                        }
                        pending = ""
                        draftTouched = false
                        revision++
                        val cleanup = runCatching { controller.removeDraft(draftKey) }
                        error =
                            if (cleanup.isFailure)
                                "Fotoangaben bestätigt gespeichert. Entwurfsbereinigung steht noch aus."
                            else ""
                        controller.message("Fotoangaben gespeichert.")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = mediaError(failure)
                    } finally {
                        busy = false
                    }
                }
            },
            secondaryLabel = "Zurück",
            onSecondary = { leave() },
            enabled = (photo != null || pending.isNotBlank()) && !busy && draftReady,
            secondaryEnabled = !busy,
        )
    }
    if (discardChanges)
        LaunchedEffect(Unit) {
            try {
                controller.removeDraft(draftKey)
                draftTouched = false
                close()
            } catch (failure: Exception) {
                controller.message(mediaError(failure))
            } finally {
                discardChanges = false
            }
        }
    if (picker.isNotBlank())
        MediaEntityPicker(
            picker,
            controller,
            photo?.optString("customer_id").orEmpty(),
            dismiss = { picker = "" },
        ) { row ->
            draftTouched = true
            if (picker == "orders") {
                order = row.id
                orderTitle = row.title
            } else {
                worklog = row.id
                workTitle = row.title
            }
            picker = ""
        }
    if (fullscreen) MediaZoomDialog(id, controller, close = { fullscreen = false })
}

private suspend fun retainImportedPhotoResult(
    controller: NativeAppController,
    draftKey: String,
    photoId: String,
) {
    if (!draftKey.startsWith("restored_media:") || photoId.isEmpty()) return
    withContext(Dispatchers.IO) {
        val key = "native_restored_adoption:" + PixelMcpWriteTools.hash(draftKey)
        val marker = JSONObject(controller.store.ownerValue(key, "{}"))
        marker.put("draft_key", draftKey).put("photo_id", photoId)
        controller.store.putOwnerValue(key, marker.toString())
        if (JSONObject(controller.store.ownerValue(key, "{}")).optString("photo_id") != photoId)
            throw IllegalStateException(
                "Der gespeicherte Fotobeleg konnte noch nicht zurückgelesen werden."
            )
    }
}

@Composable
private fun NativePhotoImport(
    controller: NativeAppController,
    draftKeyOverride: String = "",
    initialArgs: JSONObject = controller.route.args,
    close: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf("") }
    var customer by rememberSaveable { mutableStateOf(initialArgs.optString("customer_id")) }
    var customerLabel by rememberSaveable { mutableStateOf("") }
    var caption by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("documentation") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf(initialArgs.optString("order_id")) }
    var orderLabel by rememberSaveable { mutableStateOf("") }
    var worklog by rememberSaveable { mutableStateOf(initialArgs.optString("worklog_id")) }
    var workLabel by rememberSaveable { mutableStateOf("") }
    var session by rememberSaveable { mutableStateOf(initialArgs.optString("session_id")) }
    var origin by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    var savedId by rememberSaveable { mutableStateOf("") }
    var failure by remember { mutableStateOf("") }
    val draftKey = draftKeyOverride.ifEmpty { "photo_import" }
    var draftReady by remember { mutableStateOf(false) }
    var draftTouched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            controller.draft(draftKey)?.let { d ->
                selected = d.optString("selected_uri")
                customer = d.optString("customer_id")
                customerLabel = d.optString("customer_label")
                caption = d.optString("caption")
                role = d.optString("role", "documentation")
                visible = d.optBoolean("customer_visible")
                order = d.optString("order_id")
                orderLabel = d.optString("order_label")
                worklog = d.optString("worklog_id")
                workLabel = d.optString("work_label")
                session = d.optString("session_id")
                origin = d.optString("capture_context")
                pending = d.optString("pending")
                draftTouched = true
            }
        } catch (failure: Exception) {
            controller.message(mediaError(failure))
        }
        draftReady = true
    }
    LaunchedEffect(customer, order, worklog) {
        suspend fun title(collection: String, id: String): String {
            if (id.isEmpty()) return ""
            return try {
                controller.store
                    .nativeData()
                    .detail(collection, id)
                    ?.let { nativeReferenceLabel(it, collection) }
                    .orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "Zuordnung prüfen"
            }
        }
        customerLabel = title("customers", customer)
        orderLabel = title("orders", order)
        workLabel = title("worklogs", worklog)
    }
    fun draftValue() =
        JSONObject()
            .put("selected_uri", selected)
            .put("customer_id", customer)
            .put("customer_label", customerLabel)
            .put("caption", caption)
            .put("role", role)
            .put("customer_visible", visible)
            .put("order_id", order)
            .put("order_label", orderLabel)
            .put("worklog_id", worklog)
            .put("work_label", workLabel)
            .put("session_id", session)
            .put("capture_context", origin)
            .put("pending", pending)
    val draftSnapshot = draftValue().toString()
    LaunchedEffect(draftSnapshot, draftReady, draftTouched) {
        if (draftReady && draftTouched) controller.saveDraft(draftKey, JSONObject(draftSnapshot))
    }
    val changeable = pending.isBlank() && !busy
    fun choose(camera: Boolean) {
        scope.launch {
            try {
                val uri = controller.pickPhoto(camera) ?: return@launch
                val creation =
                    withContext(Dispatchers.IO) {
                        val identity = BackupDeviceGuard(controller.context).current()
                        JSONObject()
                            .put("formatVersion", 1)
                            .put("installation_id", identity.getString("installationId"))
                            .put("restore_epoch", identity.getString("restoreEpoch"))
                            .toString()
                    }
                val previous = selected
                selected = uri.toString()
                origin = creation
                pending = ""
                draftTouched = true
                savedId = ""
                failure = ""
                controller.saveDraft(draftKey, draftValue())
                controller.flushDrafts()
                if (previous.isNotBlank() && previous != selected)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            PhotoCaptureProvider.release(controller.context, Uri.parse(previous))
                        }
                    }
            } catch (failure: Exception) {
                controller.message(mediaError(failure))
            }
        }
    }
    var cameraOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(draftReady) {
        if (draftReady && initialArgs.optBoolean("_open_camera") && !cameraOpened) {
            cameraOpened = true
            // An existing unsaved capture always wins over a new shortcut.
            if (!draftTouched && selected.isBlank() && pending.isBlank()) choose(true)
        }
    }
    fun finish() {
        close()
        if (controller.route.mode == "import") controller.finishAction()
    }
    fun leave() {
        if (!busy)
            scope.launch {
                try {
                    if (draftTouched || selected.isNotBlank() || pending.isNotBlank()) {
                        controller.saveDraft(draftKey, draftValue())
                        controller.flushDrafts()
                    }
                    finish()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = mediaError(error)
                }
            }
    }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { leave() }) { Text("Zurück") }
            Text("Foto hinzufügen", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeStatus(
                if (savedId.isNotBlank()) "Foto gespeichert"
                else if (pending.isNotBlank()) "Speichern nicht bestätigt"
                else "Noch nicht gespeichert",
                if (savedId.isNotBlank()) NativeStatusTone.Confirmed
                else if (pending.isNotBlank()) NativeStatusTone.Attention
                else NativeStatusTone.Neutral,
            )
            if (failure.isNotBlank()) Text(failure, color = MaterialTheme.colorScheme.error)
            NativeContext(
                controller,
                JSONObject().put("customer_id", customer).put("order_id", order),
            )
            if (session.isNotBlank())
                NativeInfoRow(
                    "Zuordnung",
                    "Laufende Arbeit" +
                        customerLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                )
            if (worklog.isNotBlank())
                NativeInfoRow("Arbeit", workLabel.ifBlank { "Gespeicherte Arbeit zugeordnet" })
            if (savedId.isNotBlank()) {
                Text(
                    "Das Foto wurde dieser Zuordnung hinzugefügt. Gespeicherte Arbeit bleibt auch bei einem späteren Fotofehler erhalten."
                )
                NativeInfoRow(
                    "Gespeichertes Foto öffnen",
                    onClick = { controller.navigate("photos", savedId) },
                )
            } else {
                if (selected.isNotBlank())
                    AsyncImage(
                        model =
                            ImageRequest.Builder(controller.context)
                                .data(Uri.parse(selected))
                                .size(640, 640)
                                .build(),
                        imageLoader = controller.imageLoader,
                        contentDescription = "Ausgewähltes Foto",
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                        contentScale = ContentScale.Fit,
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { choose(false) }, enabled = changeable) {
                        Text("Foto auswählen")
                    }
                    FilledTonalButton(onClick = { choose(true) }, enabled = changeable) {
                        Text("Foto aufnehmen")
                    }
                }
                Text(
                    "Die Kamera liefert nur die Aufnahme. Erst „Foto speichern“ legt den Fotodatensatz an.",
                    style = MaterialTheme.typography.bodySmall,
                )
                MediaChoice(
                    "Kunde *",
                    customerLabel.ifBlank { "Kunden auswählen" },
                    { picker = "customers" },
                    {
                        customer = ""
                        customerLabel = ""
                        order = ""
                        worklog = ""
                        session = ""
                    },
                    changeable,
                )
                OutlinedTextField(
                    caption,
                    {
                        caption = it
                        draftTouched = true
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Bildunterschrift") },
                    enabled = changeable,
                )
                MediaDropdown("Rolle", role, photoRoles, changeable) {
                    role = it
                    draftTouched = true
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        visible,
                        {
                            visible = it
                            draftTouched = true
                        },
                        enabled = changeable,
                    )
                    Text("Für Kunden sichtbar")
                }
                MediaChoice(
                    "Auftrag",
                    orderLabel.ifBlank { "Nicht zugeordnet" },
                    { picker = "orders" },
                    {
                        order = ""
                        orderLabel = ""
                        session = ""
                    },
                    changeable && customer.isNotBlank(),
                )
                MediaChoice(
                    "Arbeit",
                    workLabel.ifBlank { "Nicht zugeordnet" },
                    { picker = "worklogs" },
                    {
                        worklog = ""
                        workLabel = ""
                        session = ""
                    },
                    changeable && customer.isNotBlank(),
                )
                TextButton(
                    enabled = changeable,
                    onClick = {
                        scope.launch {
                            try {
                                val active =
                                    controller.query("get_active_work_session", JSONObject())
                                val chosen = active.optString("customer_id")
                                if (chosen.isBlank())
                                    throw IllegalArgumentException(
                                        "Keine laufende Arbeit vorhanden."
                                    )
                                customer = chosen
                                customerLabel =
                                    active.optString("customer_name", "Kunde der laufenden Arbeit")
                                order = active.optString("order_id")
                                orderLabel = active.optString("order_title")
                                worklog = active.optString("finalized_worklog_id")
                                session = active.optString("id", active.optString("session_id"))
                                draftTouched = true
                            } catch (failure: Exception) {
                                controller.message(mediaError(failure))
                            }
                        }
                    },
                ) {
                    Text("Laufender Arbeit zuordnen")
                }
                if (session.isNotBlank())
                    Text(
                        "Weitere Fotos bleiben dieser Arbeitssitzung zugeordnet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (pending.isNotBlank())
                    Text(
                        "Die Speicherantwort ist noch unklar. Den ursprünglichen Vorgang prüfen. Gespeicherte Arbeit bleibt erhalten."
                    )
            }
        }
        if (savedId.isNotBlank())
            NativeActionBar(
                if (session.isNotBlank() || worklog.isNotBlank()) "Zur Arbeit" else "Fertig",
                { finish() },
            )
        else
            NativeActionBar(
                primaryLabel =
                    if (busy) "Speichert …"
                    else if (pending.isNotBlank()) "Ergebnis prüfen" else "Foto speichern",
                onPrimary = {
                    scope.launch {
                        busy = true
                        try {
                            val saved =
                                if (pending.isNotBlank()) {
                                    val proof =
                                        controller.query(
                                            "inspect_edit_action",
                                            JSONObject()
                                                .put("original_tool", "savePhoto")
                                                .put("original_args", JSONObject(pending)),
                                        )
                                    if (proof.optString("state") != "confirmed") {
                                        failure = mediaInspectionMessage(proof)
                                        return@launch
                                    }
                                    val confirmedId = proof.optString("entity_id")
                                    check(confirmedId.isNotBlank()) {
                                        "Der Fotobeleg enthält noch keine eindeutige Zuordnung. Der ursprüngliche Vorgang bleibt erhalten."
                                    }
                                    JSONObject().put("photo_id", confirmedId)
                                } else {
                                    pending =
                                        JSONObject()
                                            .put("_photo_request_id", mediaRequest())
                                            .put("customer_id", customer)
                                            .put("caption", caption)
                                            .put("role", role)
                                            .put("customer_visible", visible)
                                            .put("order_id", order)
                                            .put("worklog_id", worklog)
                                            .put("session_id", session)
                                            .put("_capture_context", JSONObject(origin))
                                            .toString()
                                    draftTouched = true
                                    controller.saveDraft(draftKey, draftValue())
                                    controller.flushDrafts()
                                    controller.importPhoto(Uri.parse(selected), JSONObject(pending))
                                }
                            savedId = saved.optString("photo_id", saved.optString("id"))
                            val cleanup = runCatching {
                                retainImportedPhotoResult(controller, draftKey, savedId)
                                controller.removeDraft(draftKey)
                                withContext(Dispatchers.IO) {
                                    PhotoCaptureProvider.release(
                                        controller.context,
                                        Uri.parse(selected),
                                    )
                                }
                            }
                            pending = ""
                            selected = ""
                            caption = ""
                            draftTouched = false
                            failure =
                                if (cleanup.isFailure)
                                    "Foto gespeichert. Entwurfsbereinigung steht noch aus; die gespeicherte Arbeit bleibt erhalten."
                                else ""
                            controller.message("Foto gespeichert.")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failure =
                                mediaError(error) + " Bereits gespeicherte Arbeit bleibt erhalten."
                        } finally {
                            busy = false
                        }
                    }
                },
                secondaryLabel = "Verwerfen",
                onSecondary = {
                    if (draftTouched || selected.isNotBlank()) confirmDiscard = true else finish()
                },
                enabled =
                    !busy &&
                        draftReady &&
                        (pending.isNotBlank() || selected.isNotBlank() && customer.isNotBlank()),
                secondaryEnabled = !busy && pending.isBlank(),
            )
    }
    if (picker.isNotBlank())
        MediaEntityPicker(picker, controller, customer, dismiss = { picker = "" }) { row ->
            draftTouched = true
            when (picker) {
                "customers" -> {
                    customer = row.id
                    customerLabel =
                        listOf(row.title, row.address)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    order = ""
                    orderLabel = ""
                    worklog = ""
                    workLabel = ""
                    session = ""
                }
                "orders" -> {
                    order = row.id
                    orderLabel = row.title
                    session = ""
                }
                else -> {
                    worklog = row.id
                    workLabel = row.title
                    session = ""
                }
            }
            picker = ""
        }
    if (confirmDiscard)
        LaunchedEffect(Unit) {
            busy = true
            try {
                controller.removeDraft(draftKey)
                withContext(Dispatchers.IO) {
                    PhotoCaptureProvider.release(controller.context, Uri.parse(selected))
                }
                selected = ""
                pending = ""
                draftTouched = false
                finish()
            } catch (failure: Exception) {
                controller.message(mediaError(failure))
            } finally {
                busy = false
                confirmDiscard = false
            }
        }
}

@Composable
private fun NativePhotoTrash(controller: NativeAppController, close: () -> Unit) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(JSONObject()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(offset, revision) {
        try {
            result =
                controller.query(
                    "list_archive",
                    JSONObject().put("entity", "photo").put("offset", offset).put("limit", 30),
                )
            error = ""
        } catch (failure: Exception) {
            error = mediaError(failure)
        }
    }
    val rows = jsonRows(result.optJSONArray("archive"))
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Zurück") }
            Text("Foto-Papierkorb", style = MaterialTheme.typography.titleLarge)
        }
        if (error.isNotBlank())
            Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rows.isEmpty()) item { Text("Keine Fotos im Papierkorb.") }
            items(rows, key = { it.getString("archive_key") }) { row ->
                val entry = row.getJSONObject("entry")
                val restoreRequest =
                    rememberSaveable(row.getString("archive_key")) { mediaRequest() }
                val record =
                    entry.optJSONObject("record")
                        ?: entry.optJSONObject("_archive_record")
                        ?: JSONObject()
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            record.optString("caption").ifBlank {
                                record.optString("original_name", "Foto")
                            }
                        )
                        Text(nativeDisplayDate(entry.optString("archived_at").take(10)))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        mediaWrite(
                                            controller,
                                            "restore_entity",
                                            JSONObject()
                                                .put("request_id", restoreRequest)
                                                .put("entity", "photo")
                                                .put(
                                                    "entity_id",
                                                    row.getString("archive_key").substringAfter(':'),
                                                )
                                                .put(
                                                    "expected_record_hash",
                                                    row.getString("record_hash"),
                                                ),
                                        )
                                        revision++
                                        controller.message(
                                            "Foto einschließlich geprüfter Zuordnung wiederhergestellt."
                                        )
                                    } catch (failure: Exception) {
                                        controller.message(mediaError(failure))
                                    }
                                }
                            }
                        ) {
                            Text("Wiederherstellen")
                        }
                    }
                }
            }
        }
        MediaPagination(offset, rows.size, result.optInt("total"), 30) { offset = it }
    }
}

@Composable
private fun NativePhotoSettings(controller: NativeAppController, close: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var quality by rememberSaveable { mutableStateOf("optimized") }
    var pending by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            data = controller.query("get_settings", JSONObject())
            quality = data!!.getJSONObject("settings").optString("photo_quality", "optimized")
        } catch (failure: Exception) {
            controller.message(mediaError(failure))
        }
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fotoqualität und Speicher", style = MaterialTheme.typography.titleLarge)
        data?.optJSONObject("photo_storage")?.let { storage ->
            Text(
                "${storage.optInt("local_files")} Originale · ${storage.optLong("original_bytes") / (1024 * 1024)} MB\nVorschauen: ${storage.optLong("thumbnail_bytes") / 1024} KB"
            )
        }
        Text("Die Auswahl gilt für neue Fotos. Gespeicherte Originale bleiben erhalten.")
        MediaDropdown(
            "Fotoqualität",
            quality,
            linkedMapOf(
                "optimized" to "Optimiert",
                "high" to "Hohe Qualität",
                "original" to "Original behalten",
            ),
            enabled = pending.isBlank(),
        ) {
            quality = it
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.imePadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = close) { Text("Zurück") }
            Button(
                enabled = data != null,
                onClick = {
                    scope.launch {
                        try {
                            if (pending.isNotBlank()) {
                                val proof =
                                    controller.query(
                                        "inspect_edit_action",
                                        JSONObject()
                                            .put("original_tool", "update_settings")
                                            .put("original_args", JSONObject(pending)),
                                    )
                                if (proof.optString("state") != "confirmed") {
                                    controller.message(mediaInspectionMessage(proof))
                                    return@launch
                                }
                            } else {
                                pending =
                                    JSONObject()
                                        .put("request_id", mediaRequest())
                                        .put(
                                            "expected_record_hash",
                                            data!!.getString("record_hash"),
                                        )
                                        .put("updates", JSONObject().put("photo_quality", quality))
                                        .toString()
                                mediaWrite(controller, "update_settings", JSONObject(pending))
                            }
                            controller.message("Fotoqualität gespeichert.")
                            close()
                        } catch (failure: Exception) {
                            controller.message(mediaError(failure))
                        }
                    }
                },
            ) {
                Text(if (pending.isNotBlank()) "Ergebnis prüfen" else "Speichern")
            }
        }
    }
}

@Composable
private fun MediaZoomDialog(photoId: String, controller: NativeAppController, close: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, change, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        pan = if (scale <= 1f) Offset.Zero else pan + change
    }
    val source by
        produceState<File?>(null, photoId) {
            if (photoId.isNotBlank())
                value =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                controller.store.photoAttachment(photoId)?.let {
                                    controller.store.photoAttachmentStore().verifiedFile(it)
                                }
                            }
                            .getOrNull()
                    }
        }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().transformable(transform),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(controller.context)
                            .data(source)
                            .size(2048, 2048)
                            .build(),
                    imageLoader = controller.imageLoader,
                    contentDescription = "Bild in Vollansicht",
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                "Mit zwei Fingern vergrößern und verschieben.",
                Modifier.padding(horizontal = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    onClick = {
                        scale = 1f
                        pan = Offset.Zero
                    }
                ) {
                    Text("Zurücksetzen")
                }
                TextButton(onClick = close) { Text("Schließen") }
            }
        }
    }
}

@Composable
private fun MediaDropdown(
    label: String,
    selected: String,
    options: Map<String, String>,
    enabled: Boolean = true,
    changed: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(options[selected] ?: selected)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        changed(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaChoice(
    label: String,
    selected: String,
    open: () -> Unit,
    clear: () -> Unit,
    enabled: Boolean = true,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = open, Modifier.weight(1f), enabled = enabled) {
                Text(selected)
            }
            TextButton(onClick = clear, enabled = enabled) { Text("Lösen") }
        }
    }
}

@Composable
private fun MediaEntityPicker(
    collection: String,
    controller: NativeAppController,
    customerId: String = "",
    dismiss: () -> Unit = {},
    chosen: (NativeListRow) -> Unit,
) {
    NativeRecordDialog(
        true,
        controller,
        collection,
        when (collection) {
            "customers" -> "Kunden auswählen"
            "orders" -> "Auftrag auswählen"
            else -> "Arbeit auswählen"
        },
        customerId = if (collection == "customers") "" else customerId,
        allowEmpty = false,
        onDismiss = dismiss,
    ) { row ->
        row?.let(chosen)
    }
}

@Composable
private fun MediaPagination(
    offset: Int,
    count: Int,
    total: Int,
    limit: Int,
    changed: (Int) -> Unit,
) {
    if (offset > 0 || offset + count < total)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = offset > 0, onClick = { changed(maxOf(0, offset - limit)) }) {
                Text("Vorherige")
            }
            Text(
                "${if (total == 0) 0 else offset + 1}–${offset + count} / $total",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(enabled = offset + count < total, onClick = { changed(offset + limit) }) {
                Text("Weitere")
            }
        }
}
