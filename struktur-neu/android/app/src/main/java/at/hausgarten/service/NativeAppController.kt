package at.hausgarten.service

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import okio.Path.Companion.toOkioPath

internal fun nativeMainSection(section: String): String = when (section) {
    "today" -> "today"
    "worklogs", "timer", "work", "drafts" -> "worklogs"
    "customers" -> "customers"
    else -> "office"
}
internal data class NativeRoute(val section: String = "today", val id: String = "", val mode: String = "list", val args: JSONObject = JSONObject(), val area: String = nativeMainSection(section))
internal class NativeCallFailure(val detail: JSONObject) : IllegalArgumentException(detail.optString("message", "Die Aktion konnte nicht abgeschlossen werden."))

/** Native UI uses the exact same transactional writers and read contracts as MCP. */
internal class NativeAppController(private val activity: MainActivity, val store: PixelLocalStore) {
    val context: Context get() = activity
    val imageLoader: ImageLoader get() = NativeImages.loader(context)
    val messages = MutableStateFlow("")
    var route by mutableStateOf(NativeRoute())
        private set
    private val history = ArrayDeque<NativeRoute>()
    private val draftMutex get() = NativeDraftPersistence.mutex
    private val draftScope get() = NativeDraftPersistence.scope
    private var draftTail: Deferred<Unit>?
        get() = NativeDraftPersistence.tail
        set(value) { NativeDraftPersistence.tail = value }
    private val draftFailures get() = NativeDraftPersistence.failures
    private var catalog: Map<String, JSONObject>? = null

    private val areaNavigation = mutableMapOf<String, List<NativeRoute>>()
    private val mainAreas = setOf("today", "worklogs", "customers", "office")

    private fun sameScreen(left: NativeRoute, right: NativeRoute): Boolean =
        left.section == right.section && left.id == right.id && left.mode == right.mode && left.area == right.area &&
            (left.section != "action" || left.args.optString("_draft_key", left.args.optString("customer_id")) == right.args.optString("_draft_key", right.args.optString("customer_id")))

    fun navigate(section: String, id: String = "", mode: String = "list", args: JSONObject = JSONObject()) {
        if (section in mainAreas && id.isEmpty() && mode == "list" && args.length() == 0 && section != route.area) {
            openSection(section)
            return
        }
        messages.value = ""
        val next = NativeRoute(section, id, mode, JSONObject(args.toString()), route.area)
        if (sameScreen(route, next)) return
        val previous = history.indexOfLast { sameScreen(it, next) }
        if (previous >= 0) {
            // Reuse the original route and its saveable-state key, including list filters.
            while (history.size > previous + 1) history.removeLast()
            route = history.removeLast()
        } else {
            history.addLast(route)
            route = next
        }
    }
    fun openSection(section: String) {
        require(section in mainAreas)
        messages.value = ""
        if (route.area == section) {
            val root = history.indexOfFirst { it.section == section && it.id.isEmpty() && it.mode == "list" }
            if (root >= 0) {
                while (history.size > root + 1) history.removeLast()
                route = history.removeLast()
            } else if (route.section != section || route.id.isNotEmpty() || route.mode != "list") {
                history.clear()
                route = NativeRoute(section, area = section)
            }
            return
        }
        areaNavigation[route.area] = history.toList() + route
        val saved = areaNavigation[section].orEmpty()
        history.clear()
        if (section == "today") {
            // Today always means the day overview, including when its previous visit opened a timer.
            route = saved.firstOrNull { it.section == "today" && it.id.isEmpty() && it.mode == "list" } ?: NativeRoute("today")
        } else {
            // A timer is reached through the running-work bar; an area tab returns to the area's own screens.
            val kept = saved.filter { it.section !in setOf("timer", "work") }
            if (kept.isEmpty()) route = NativeRoute(section, area = section)
            else {
                kept.dropLast(1).forEach(history::addLast)
                route = kept.last()
            }
        }
    }
    fun back() {
        messages.value = ""
        if (!hasParentScreen()) {
            openSection("today")
            return
        }
        route = if (history.isEmpty()) NativeRoute(route.area, area = route.area) else history.removeLast()
    }
    /** A saved work returns to its original caller; the stopped timer is no longer an editing step. */
    fun finishAction() {
        fun workStep(entry: NativeRoute): Boolean = entry.section in setOf("timer", "work") ||
            entry.section == "worklogs" && entry.mode in setOf("new", "edit", "duplicate") ||
            entry.section == "action" && entry.id in setOf("start_work", "complete_work_session", "finish_work", "discard_work_session", "create_worklog", "update_worklog")
        val completedWork = workStep(route)
        var target = if (history.isEmpty()) NativeRoute(route.area, area = route.area) else history.removeLast()
        if (completedWork) while (workStep(target) && history.isNotEmpty()) target = history.removeLast()
        route = if (completedWork && workStep(target)) NativeRoute(route.area, area = route.area) else target
    }
    fun returnToList(section: String) {
        val previous = history.indexOfLast { it.section == section && it.id.isEmpty() && it.mode == "list" }
        if (previous >= 0) {
            while (history.size > previous + 1) history.removeLast()
            back()
        } else {
            history.clear()
            route = NativeRoute(section, args = JSONObject(route.args.toString()), area = route.area)
        }
    }
    fun requestBack() { activity.onBackPressedDispatcher.onBackPressed() }
    fun hasParentScreen(): Boolean = history.isNotEmpty() || route.section != route.area || route.id.isNotEmpty() || route.mode != "list"
    private fun routeValue(entry: NativeRoute): JSONObject = JSONObject().put("section", entry.section).put("id", entry.id).put("mode", entry.mode).put("args", entry.args).put("area", entry.area)
    private fun readRoute(value: JSONObject): NativeRoute {
        val section = value.optString("section", "today").let { if (it == "drafts") "office" else it }
        return NativeRoute(section, value.optString("id"), value.optString("mode", "list"), value.optJSONObject("args") ?: JSONObject(), value.optString("area", nativeMainSection(section)))
    }
    fun restoreRoute(saved: String?) {
        if (saved.isNullOrEmpty()) return
        runCatching { JSONObject(saved) }.getOrNull()?.let { value ->
            history.clear()
            areaNavigation.clear()
            val areas = value.optJSONObject("area_navigation") ?: JSONObject()
            areas.keys().asSequence().filter { it in mainAreas }.forEach { area ->
                val entries = areas.optJSONArray(area) ?: JSONArray()
                areaNavigation[area] = (0 until entries.length()).map { readRoute(entries.getJSONObject(it)) }
            }
            val stack = value.optJSONArray("back_stack") ?: JSONArray()
            for (i in 0 until stack.length()) {
                val entry = readRoute(stack.getJSONObject(i))
                val repeated = history.indexOfLast { sameScreen(it, entry) }
                if (repeated >= 0) while (history.size > repeated) history.removeLast()
                history.addLast(entry)
            }
            route = readRoute(value)
            // Old saved tab visits must not become a new chain of main-area back steps.
            val sameArea = history.filter { it.area == route.area }
            history.clear(); sameArea.forEach(history::addLast)
        }
    }
    fun savedRoute(): String = routeValue(route).toString()
    fun savedNavigation(): String = routeValue(route).put("back_stack", JSONArray().apply {
        history.forEach { put(routeValue(it)) }
    }).put("area_navigation", JSONObject().apply {
        areaNavigation.forEach { (area, entries) -> put(area, JSONArray().apply { entries.forEach { put(routeValue(it)) } }) }
    }).toString()
    fun message(value: String) { messages.value = value }
    fun open(type: Class<*>) { activity.startActivity(Intent(activity, type)) }
    fun action(title: String, tool: String, args: JSONObject = JSONObject()) {
        navigate("action", tool, title, args)
    }

    suspend fun call(name: String, args: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val result = try {
            PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store)).call(name, JSONObject(args.toString()))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw nativeFailure(failure) }
        if (result.has("ok") && !result.optBoolean("ok")) {
            val error = result.optJSONObject("error")
            throw NativeCallFailure(error ?: result)
        }
        if (spec(name)?.optJSONObject("annotations")?.optBoolean("readOnlyHint") == false) store.notifyCommittedChange()
        result
    }
    internal fun actionDraftKey(tool: String, initial: JSONObject): String {
        val identity = initial.optString("entity_id", initial.optString("work_record_id", initial.optString("customer_id", initial.optString("offer_id"))))
        val defaultKey = "action:$tool:" + if (tool == "set_accounting_record") initial.optString("collection") + ":" + identity else identity
        return route.args.optString("_draft_key", initial.optString("_draft_key", defaultKey))
    }

    /** Called by an explicit start click with a known customer, never by opening a route. */
    suspend fun startKnownWork(args: JSONObject): JSONObject {
        require(args.optString("customer_id").isNotBlank()) { "Bitte zuerst einen Kunden auswählen." }
        val key = actionDraftKey("start_work", args)
        val saved = draft(key)
        val original = saved?.optJSONObject("original_args")
        if (original != null) {
            require(saved?.optString("original_tool", "start_work") == "start_work") { "Zuerst den ursprünglichen Vorgang prüfen." }
            val proof = query("inspect_edit_action", JSONObject().put("original_tool", "start_work").put("original_args", JSONObject(original.toString())))
            if (proof.optString("state") == "confirmed") {
                removeDraft(key)
                return query("get_active_work_session")
            }
            throw IllegalStateException(when (proof.optString("state")) {
                "content_removed" -> "Dieser Arbeitsstart wurde gespeichert und später entfernt. Bitte den aktuellen Zeitstand ansehen."
                "changed" -> "Der Arbeitsstart wurde inzwischen geändert. Bitte den aktuellen Zeitstand ansehen."
                else -> "Der Arbeitsstart ist weiterhin unbestätigt. Eingaben und ursprünglicher Vorgang bleiben erhalten; bitte das Ergebnis prüfen."
            })
        }
        val current = query("get_active_work_session")
        require(current.optString("state") != "UNAVAILABLE") { "Die aktuelle Zeiterfassung ist nicht lesbar. Bitte zuerst den Zeitstand prüfen." }
        if (current.optString("state") in setOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")) return current
        require(saved?.optBoolean("_restore_action_review_required") != true && saved?.optBoolean("review_required") != true) {
            "Diesen übernommenen Startentwurf zuerst im Formular prüfen und ausdrücklich weiterbearbeiten."
        }
        val entered = saved?.optJSONObject("values")?.let { JSONObject(it.toString()) } ?: JSONObject(args.toString())
        require(entered.optString("customer_id") == args.optString("customer_id")) { "Der erhaltene Startentwurf gehört zu einem anderen Kunden. Bitte zuerst den Entwurf prüfen." }
        val properties = requireNotNull(spec("start_work")).getJSONObject("inputSchema").getJSONObject("properties")
        val frozen = JSONObject()
        properties.keys().asSequence().filter { entered.has(it) }.forEach { frozen.put(it, entered.get(it)) }
        if (frozen.optString("request_id").isBlank()) frozen.put("request_id", UUID.randomUUID().toString())
        val envelope = JSONObject().put("values", JSONObject(frozen.toString())).put("original_tool", "start_work")
            .put("update_base", JSONObject()).put("review_required", false).put("original_args", JSONObject(frozen.toString()))
        saveDraft(key, envelope)
        flushDrafts()
        try { call("start_work", frozen) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") {
                envelope.remove("original_args")
                saveDraft(key, envelope)
                flushDrafts()
            }
            throw failure
        }
        removeDraft(key)
        return query("get_active_work_session")
    }

    /** Today and the timer use one durable action/recovery path. */
    suspend fun timerAction(tool: String, sessionId: String): JSONObject {
        require(tool in listOf("start_break", "end_break", "stop_work", "continue_work", "discard_work_session", "restore_work_session"))
        val previous = draft("timer_action")
        if (previous != null && previous.optString("original_tool") != tool) throw IllegalStateException("Zuerst den ungeklärten Zeitvorgang prüfen.")
        val args = previous?.getJSONObject("original_args") ?: JSONObject().put("request_id", UUID.randomUUID().toString()).put("session_id", sessionId)
        suspend fun confirmed(): JSONObject {
            if (tool == "discard_work_session") {
                saveDraft("timer_cancelled", JSONObject().put("session_id", args.getString("session_id"))); flushDrafts()
            } else if (tool == "restore_work_session") removeDraft("timer_cancelled")
            removeDraft("timer_action")
            return query("get_active_work_session")
        }
        if (previous != null) {
            val proof = query("inspect_edit_action", JSONObject().put("original_tool", tool).put("original_args", args))
            if (proof.optString("state") == "confirmed") return confirmed()
            throw IllegalStateException(when (proof.optString("state")) {
                "content_removed" -> "Der ursprüngliche Zeitvorgang ist bestätigt, sein Inhalt wurde später entfernt. Es wurde nichts erneut ausgeführt."
                "changed" -> "Der Zeitvorgang wurde inzwischen geändert. Bitte den aktuellen Zeitstand ansehen."
                else -> "Das Ergebnis ist weiterhin offen. Der ursprüngliche Zeitvorgang bleibt erhalten; bitte später erneut prüfen."
            })
        }
        saveDraft("timer_action", JSONObject().put("original_tool", tool).put("original_args", args)); flushDrafts()
        try { call(tool, args) }
        catch (failure: Exception) {
            if (failure is NativeCallFailure && failure.detail.optString("commit_state") == "not_committed") removeDraft("timer_action")
            throw failure
        }
        return confirmed()
    }

    suspend fun storageUsage(): JSONObject = withContext(Dispatchers.IO) { LocalStorageMaintenance(context, store).usage() }
    suspend fun cleanStorage(): JSONObject {
        flushDrafts()
        return withContext(Dispatchers.IO) { LocalStorageMaintenance(context, store).clean(true).also { store.notifyCommittedChange() } }
    }
    suspend fun query(name: String, args: JSONObject = JSONObject()): JSONObject {
        val result = call(name, args)
        return when (val data = result.opt("data")) {
            is JSONObject -> data
            is JSONArray -> JSONObject().put("items", data)
            else -> result
        }
    }
    suspend fun spec(name: String): JSONObject? = withContext(Dispatchers.IO) {
        if (catalog == null) {
            val tools = PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store)).tools()
            catalog = (0 until tools.length()).associate { tools.getJSONObject(it).getString("name") to tools.getJSONObject(it) }
        }
        catalog?.get(name)
    }
    suspend fun saveEditor(kind: String, value: JSONObject): String = withContext(Dispatchers.IO) {
        try { store.saveEditorRecord(kind, JSONObject(value.toString())).also { store.notifyCommittedChange() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw nativeFailure(failure) }
    }
    suspend fun pickPhoto(camera: Boolean = false): Uri? = withContext(Dispatchers.Main) { activity.pickPhoto(camera) }
    suspend fun location(): JSONObject = withContext(Dispatchers.Main) { activity.currentWorkLocation() }
    fun speech(result: (String) -> Unit) { activity.recognizeSpeech(result) }

    /** Selection is a draft only; this method is called exclusively by an explicit Save. */
    suspend fun importPhoto(uri: Uri, contextMetadata: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject(contextMetadata.toString())
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > 20L * 1024 * 1024) throw IOException("Das Bild ist größer als 20 MB.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("Das gewählte Bild ist nicht mehr lesbar. Bitte erneut auswählen.")
        val mime = context.contentResolver.getType(uri) ?: PhotoAttachmentStore.sniff(bytes)
        payload.put("data", "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        if (!payload.has("_photo_request_id")) payload.put("_photo_request_id", payload.optString("request_id", UUID.randomUUID().toString()))
        payload.remove("request_id")
        val id = store.savePhoto(payload)
        store.notifyCommittedChange()
        JSONObject().put("id", id).put("photo_id", id).put("saved", true)
    }

    /** Existing bounded upload contract, streamed in fixed chunks for equipment/profile imports. */
    suspend fun stageEquipmentPhoto(uri: Uri, request: String): String = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("native-photo-", ".upload", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val bytes = ByteArray(32768)
                    while (true) {
                        val count = input.read(bytes)
                        if (count < 0) break
                        size += count
                        if (size > 20L * 1024 * 1024) throw IOException("Das Bild ist größer als 20 MB.")
                        digest.update(bytes, 0, count); output.write(bytes, 0, count)
                    }
                }
            } ?: throw IOException("Bild nicht mehr verfügbar.")
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val inferred = context.contentResolver.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase())
            val mime = when (inferred) { "image/jpg" -> "image/jpeg"; "image/heif" -> "image/heic"; else -> inferred ?: "image/jpeg" }
            val upload = query("begin_file_upload", JSONObject().put("request_id", request + "_begin").put("byte_size", size).put("sha256", sha).put("mime_type", mime)).getString("upload_id")
            temporary.inputStream().use { input ->
                val bytes = ByteArray(32768); var offset = 0
                while (true) {
                    val count = input.read(bytes); if (count < 0) break
                    val part = bytes.copyOf(count)
                    query("write_file_chunk", JSONObject().put("request_id", request + "_" + offset).put("upload_id", upload).put("offset", offset)
                        .put("chunk_base64", Base64.encodeToString(part, Base64.NO_WRAP)).put("chunk_sha256", McpFileTransfer.sha(part)))
                    offset += count
                }
            }
            upload
        } finally { temporary.delete() }
    }

    suspend fun draft(key: String): JSONObject? = withContext(Dispatchers.IO) {
        awaitDraftWrites()
        val raw = store.ownerValue("native_form_draft:" + PixelMcpWriteTools.hash(key), "")
        val unsaved = NativeDraftPersistence.unsaved[key]
        if (raw.isEmpty() && unsaved == null) return@withContext null
        val draft = JSONObject(unsaved ?: raw)
        val earlier = raw.takeIf { it.isNotEmpty() }?.let(::JSONObject)
        for (metadata in listOf("_creation_identity", "_inbox_route", "_legacy_source")) {
            if (!draft.has(metadata) && earlier?.has(metadata) == true) draft.put(metadata, earlier.get(metadata))
        }
        val identity = BackupDeviceGuard(context).current()
        // An unsaved new form belongs to this running process. Existing identities
        // retain their restore boundary even if a later draft write failed.
        if (unsaved != null && !draft.has("_creation_identity")) draft.put("_creation_identity", identity)
        val original = draft.optJSONObject("_creation_identity")
        if (original == null || original.optString("installationId") != identity.getString("installationId") || original.optString("restoreEpoch") != identity.getString("restoreEpoch")) {
            draft.put("_restore_action_review_required", true)
        }
        draft
    }
    fun saveDraft(key: String, value: JSONObject) {
        val frozen = value.toString()
        synchronized(NativeDraftPersistence) {
        NativeDraftPersistence.unsaved[key] = frozen
        val previous = draftTail
        draftTail = draftScope.async {
            previous?.join()
            try { draftMutex.withLock {
                val saved = JSONObject(frozen).put("_draft_key", key)
                val prior = store.ownerValue("native_form_draft:" + PixelMcpWriteTools.hash(key), "")
                val earlier = if (prior.isNotEmpty()) JSONObject(prior) else null
                for (metadata in listOf("_creation_identity", "_inbox_route", "_legacy_source")) {
                    if (!saved.has(metadata) && earlier?.has(metadata) == true) saved.put(metadata, earlier.get(metadata))
                }
                if (!saved.has("_creation_identity")) saved.put("_creation_identity", BackupDeviceGuard(context).current())
                synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
                    store.putOwnerValue("native_form_draft:" + PixelMcpWriteTools.hash(key), saved.toString())
                }
                draftFailures.remove(key)
                NativeDraftPersistence.unsaved.remove(key, frozen)
            } } catch (failure: Throwable) { draftFailures[key] = failure; throw failure }
        }
        }
    }
    private suspend fun awaitDraftWrites() {
        while (true) {
            val latest = draftTail
            latest?.join()
            if (latest === draftTail) break
        }
    }
    suspend fun flushDrafts() {
        awaitDraftWrites()
        if (draftFailures.isNotEmpty()) throw IOException("Ein Formularentwurf konnte nicht gesichert werden. Eingaben bleiben geöffnet; bitte erneut speichern.")
    }
    suspend fun close() { flushDrafts() }
    /** Call only after explicit review; unresolved original actions must first be inspected. */
    suspend fun adoptDraft(key: String, value: JSONObject) = withContext(Dispatchers.IO) {
        awaitDraftWrites()
        draftMutex.withLock {
            val storageKey = "native_form_draft:" + PixelMcpWriteTools.hash(key)
            val prior = store.ownerValue(storageKey, "")
            val earlier = if (prior.isNotEmpty()) JSONObject(prior) else null
            val adopted = JSONObject(value.toString()).put("_draft_key", key)
            for (metadata in listOf("_inbox_route", "_legacy_source")) {
                if (!adopted.has(metadata) && earlier?.has(metadata) == true) adopted.put(metadata, earlier.get(metadata))
            }
            adopted.put("_creation_identity", BackupDeviceGuard(context).current()).remove("_restore_action_review_required")
            synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) { store.putOwnerValue(storageKey, adopted.toString()) }
            draftFailures.remove(key)
            NativeDraftPersistence.unsaved.remove(key)
        }
    }
    suspend fun removeDraft(key: String) = withContext(Dispatchers.IO) {
        awaitDraftWrites(); draftMutex.withLock {
            synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) { store.putOwnerValue("native_form_draft:" + PixelMcpWriteTools.hash(key), "") }
            draftFailures.remove(key)
            NativeDraftPersistence.unsaved.remove(key)
        }
    }

    /** Persist the exact action before sending it; uncertain results always replay it. */
    suspend fun settlementAction(key: String, tool: String, args: JSONObject): JSONObject {
        val draftKey = "settlement-action:$key"
        val prior = draft(draftKey)
        val original = prior?.optJSONObject("original_args")
        // The UI's durable operation key survives even the narrow window after a
        // successful receipt read and before its outer form has been cleared.
        val frozen = original ?: JSONObject(args.toString()).put("request_id", "native_settlement_" + PixelMcpWriteTools.hash(key))
        if (original != null) {
            require(requireNotNull(prior).optString("original_tool") == tool) { "Zuerst den ursprünglichen Abschluss prüfen." }
            val compared = JSONObject(original.toString()).apply { remove("request_id") }
            require(PixelMcpWriteTools.hash(compared) == PixelMcpWriteTools.hash(args)) {
                "Ein früherer Vorgang ist noch ungeklärt. Zuerst mit den ursprünglichen Angaben erneut prüfen."
            }
        } else {
            saveDraft(draftKey, JSONObject().put("original_tool", tool).put("original_args", frozen))
            flushDrafts()
        }
        val result = try { query(tool, frozen) }
        catch (failure: NativeCallFailure) {
            if (failure.detail.optString("commit_state") == "not_committed") removeDraft(draftKey)
            throw failure
        }
        removeDraft(draftKey)
        return result
    }

    suspend fun settlementDocument(id: String, share: Boolean) {
        val intent = withContext(Dispatchers.IO) {
            val result = query("get_settlement", JSONObject().put("settlement_id", id))
            val record = result.getJSONObject("record")
            require(record.optString("route") == "billed" && record.optString("status") in setOf("confirmed", "cancelled")) { "Ein ausgestelltes Original auswählen." }
            require(!share || record.optBoolean("electronic_delivery_accepted")) { "Elektronische Übermittlung wurde für diesen Abschluss nicht als vereinbart bestätigt. Original lokal öffnen; Teilen bleibt gesperrt." }
            val bytes = BusinessDocumentStore(context).read(record.getJSONObject("pdf"))
            val directory = File(context.cacheDir, "offer-share")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Freigabeordner fehlt.")
            val file = File(directory, "Rechnung-${UUID.randomUUID()}.pdf").apply { writeBytes(bytes) }
            val uri = Uri.parse("content://${context.packageName}.offerfiles/${file.name}")
            if (share) Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, record.optString("invoice_number"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Original-PDF", uri) }
            else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("Original-PDF", uri) }
        }
        withContext(Dispatchers.Main) { activity.startActivity(Intent.createChooser(intent, if (share) "Original-PDF teilen" else "Original-PDF öffnen")) }
        if (share) message("Original-PDF übergeben. Teilen bestätigt weder Versand noch Zugang.")
    }

    suspend fun shareSettlementExport(filters: JSONObject) {
        val intent = withContext(Dispatchers.IO) {
            val data = query("export_settlements", filters)
            val directory = File(context.cacheDir, "offer-share")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Freigabeordner fehlt.")
            val file = File(directory, "Abrechnung-${UUID.randomUUID()}.zip")
            try {
                java.util.zip.ZipOutputStream(file.outputStream()).use { output ->
                    output.putNextEntry(java.util.zip.ZipEntry("export.json")); output.write(data.toString(2).toByteArray(Charsets.UTF_8)); output.closeEntry()
                    val records = data.optJSONArray("records") ?: JSONArray()
                    val included = mutableSetOf<String>()
                    for (index in 0 until records.length()) {
                        val meta = records.getJSONObject(index).optJSONObject("pdf") ?: continue
                        val name = meta.getString("pdf_storage_name")
                        if (!included.add(name)) continue
                        val bytes = BusinessDocumentStore(context).read(meta)
                        output.putNextEntry(java.util.zip.ZipEntry("originals/$name")); output.write(bytes); output.closeEntry()
                    }
                }
            } catch (failure: Exception) { file.delete(); throw failure }
            val uri = Uri.parse("content://${context.packageName}.offerfiles/${file.name}")
            Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Abrechnungsexport", uri) }
        }
        withContext(Dispatchers.Main) { activity.startActivity(Intent.createChooser(intent, "Lokalen Abrechnungsexport übergeben")) }
    }

    suspend fun shareOffer(id: String, channel: String = "share") {
        val send = withContext(Dispatchers.IO) {
            require(channel in listOf("share", "email", "whatsapp")) { "Unbekannter Versandweg." }
            val row = query("get_offer", JSONObject().put("offer_id", id))
            val record = row.optJSONObject("offer") ?: row.optJSONObject("record") ?: row
            val all = PixelMcpReadTools.effective(store.getState())
            TestDataRules.noDelivery(record, all)
            if (record.optString("finalized_at").isEmpty()) throw IOException("Nur finalisierte Originalangebote teilen.")
            val bytes = BusinessDocumentStore(context).read(record)
            if (bytes.size !in 5..(20 * 1024 * 1024) || String(bytes, 0, 5, Charsets.US_ASCII) != "%PDF-") throw IOException("Original-PDF fehlt oder ist verändert.")
            val dir = File(context.cacheDir, "offer-share"); if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Freigabeordner fehlt.")
            val file = File(dir, "Angebot-" + UUID.randomUUID() + ".pdf")
            file.writeBytes(bytes)
            val uri = Uri.parse("content://${context.packageName}.offerfiles/${file.name}")
            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Angebot")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
                    clipData = ClipData.newRawUri("PDF", uri)
                    val recipient = record.optString("delivery_recipient", record.optJSONObject("recipient")?.optString("email") ?: "")
                    recipient.takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                }
        }
        withContext(Dispatchers.Main) {
            when (channel) {
                "whatsapp" -> try { activity.startActivity(Intent(send).setPackage("com.whatsapp")) }
                    catch (_: android.content.ActivityNotFoundException) { activity.startActivity(Intent(send).setPackage("com.whatsapp.w4b")) }
                "email" -> {
                    val targets = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0).map {
                        Intent(send).setComponent(ComponentName(it.activityInfo.packageName, it.activityInfo.name))
                    }
                    if (targets.isEmpty()) throw IOException("Keine E-Mail-App für PDF-Anhänge eingerichtet.")
                    activity.startActivity(Intent.createChooser(targets.first(), "PDF per E-Mail").putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray()))
                }
                else -> activity.startActivity(Intent.createChooser(send, "Original-PDF teilen"))
            }
        }
        message("PDF an die gewählte App übergeben. Der Versand ist dort zu bestätigen.")
    }

    suspend fun viewDocument(kind: String, id: String) {
        val intent = withContext(Dispatchers.IO) {
            val data = query("get_offer", JSONObject().put("offer_id", id))
            val record = data.optJSONObject(kind) ?: data.optJSONObject("record") ?: data
            val bytes = BusinessDocumentStore(context).read(record)
            val directory = File(context.cacheDir, "offer-share"); if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Freigabeordner fehlt.")
            val file = File(directory, "Angebot-" + UUID.randomUUID() + ".pdf"); file.writeBytes(bytes)
            val uri = Uri.parse("content://${context.packageName}.offerfiles/${file.name}")
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Original-PDF", uri) }
        }
        withContext(Dispatchers.Main) { activity.startActivity(Intent.createChooser(intent, "Original-PDF öffnen")) }
    }
}

private object NativeImages {
    @Volatile private var current: ImageLoader? = null
    fun loader(context: Context): ImageLoader = current ?: synchronized(this) {
        current ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context.applicationContext, 0.12).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "coil-images").toOkioPath()).maxSizeBytes(48L * 1024 * 1024).build() }
            .build().also { current = it }
    }
}

/** Process-wide ordering keeps recreation and backup ahead of every queued form write. */
private object NativeDraftPersistence {
    val mutex = Mutex()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile var tail: Deferred<Unit>? = null
    val failures = java.util.concurrent.ConcurrentHashMap<String, Throwable>()
    val unsaved = java.util.concurrent.ConcurrentHashMap<String, String>()
}

private fun nativeFailure(failure: Exception): NativeCallFailure {
    val detail = McpErrors.bridge(failure).getJSONObject("error")
    if ((failure is IllegalArgumentException || failure is org.json.JSONException) && detail.optString("code") !in listOf("REPLAY_MISMATCH", "REQUEST_ID_REUSED", "REQUEST_EXPIRED", "COMMIT_UNKNOWN")) {
        detail.put("commit_state", "not_committed")
    }
    return NativeCallFailure(detail)
}

/** Capture the displayed version before editing; saving never refreshes a stale proof. */
internal suspend fun NativeAppController.actionObservation(tool: String, arguments: JSONObject): JSONObject {
    fun id(field: String) = arguments.optString(field)
    return when {
        tool == "set_accounting_record" -> call("get_accounting_record", JSONObject().put("collection", id("collection")).put("entity_id", id("entity_id")))
        tool == "update_settings" -> call("get_settings")
        tool == "update_business_settings" -> call("get_business_settings")
        tool == "delete_entity" -> call("preview_delete", JSONObject().put("entity", id("entity")).put("entity_id", id("entity_id")))
        tool == "purge_entity" -> call("preview_purge", JSONObject().put("entity", id("entity")).put("entity_id", id("entity_id")))
        tool == "empty_trash" -> call("preview_empty_trash")
        tool == "delete_work_records" -> call("preview_archive_work_records", JSONObject().put("work_record_ids", arguments.getJSONArray("work_record_ids")))
        tool == "review_work_session" && id("session_id").isNotEmpty() -> withContext(Dispatchers.IO) { JSONObject().put("data", store.workSessions().current(id("session_id"))) }
        id("offer_id").isNotEmpty() -> call("get_offer", JSONObject().put("offer_id", id("offer_id")).apply { if (tool == "finalize_offer") put("include_review", true) })
        id("work_record_id").isNotEmpty() -> call("get_work_record", JSONObject().put("work_record_id", id("work_record_id")))
        id("photo_id").isNotEmpty() -> call("get_photo", JSONObject().put("photo_id", id("photo_id")))
        id("order_id").isNotEmpty() -> call("get_order", JSONObject().put("order_id", id("order_id")))
        id("customer_id").isNotEmpty() -> call("get_customer", JSONObject().put("customer_id", id("customer_id")))
        else -> JSONObject()
    }
}
