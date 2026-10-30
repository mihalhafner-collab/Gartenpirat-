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
    var customerListEntry by mutableStateOf(0)
        private set
    private val history = ArrayDeque<NativeRoute>()
    private val draftMutex get() = NativeDraftPersistence.mutex
    private val draftScope get() = NativeDraftPersistence.scope
    private var draftTail: Deferred<Unit>?
        get() = NativeDraftPersistence.tail
        set(value) { NativeDraftPersistence.tail = value }
    private val draftFailures get() = NativeDraftPersistence.failures
    private var catalog: Map<String, JSONObject>? = null

    fun navigate(section: String, id: String = "", mode: String = "list", args: JSONObject = JSONObject()) {
        messages.value = ""
        history.addLast(route)
        route = NativeRoute(section, id, mode, JSONObject(args.toString()), if (section == "action") route.area else if (section == "drafts" && args.optString("scope") == "billing") "office" else nativeMainSection(section))
    }
    fun openSection(section: String) {
        if (section == "customers") customerListEntry++
        if (route.section != section || route.id.isNotEmpty() || route.mode != "list") navigate(section)
    }
    fun back() { messages.value = ""; route = if (history.isEmpty()) NativeRoute() else history.removeLast() }
    fun returnToList(section: String) {
        val previous = history.lastOrNull()
        if (previous?.section == section && previous.id.isEmpty() && previous.mode == "list") back()
        else navigate(section, args = route.args)
    }
    fun requestBack() { activity.onBackPressedDispatcher.onBackPressed() }
    fun canGoBack(): Boolean = history.isNotEmpty() || route.section != "today"
    fun restoreRoute(saved: String?) {
        if (saved.isNullOrEmpty()) return
        runCatching { JSONObject(saved) }.getOrNull()?.let {
            history.clear()
            val stack = it.optJSONArray("back_stack") ?: JSONArray()
            for (i in 0 until stack.length()) {
                val entry = stack.getJSONObject(i)
                val section = entry.optString("section", "today")
                history.addLast(NativeRoute(section, entry.optString("id"), entry.optString("mode", "list"), entry.optJSONObject("args") ?: JSONObject(), entry.optString("area", nativeMainSection(section))))
            }
            val section = it.optString("section", "today")
            route = NativeRoute(section, it.optString("id"), it.optString("mode", "list"), it.optJSONObject("args") ?: JSONObject(), it.optString("area", nativeMainSection(section)))
        }
    }
    fun savedRoute(): String = JSONObject().put("section", route.section).put("id", route.id).put("mode", route.mode).put("args", route.args).put("area", route.area).toString()
    fun savedNavigation(): String = JSONObject(savedRoute()).put("back_stack", JSONArray().apply {
        history.forEach { entry -> put(JSONObject().put("section", entry.section).put("id", entry.id).put("mode", entry.mode).put("args", entry.args).put("area", entry.area)) }
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
        try { store.saveEditorRecord(kind, JSONObject(value.toString())).also { store.notifyCommittedChange(); MainActivity.requestMcpSync(context) } }
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

    suspend fun shareDocument(kind: String, id: String, channel: String = "share") {
        val send = withContext(Dispatchers.IO) {
            require(channel in listOf("share", "email", "whatsapp")) { "Unbekannter Versandweg." }
            val row = query(if (kind == "offer") "get_offer" else "get_invoice", JSONObject().put(if (kind == "offer") "offer_id" else "invoice_id", id))
            val record = row.optJSONObject(kind) ?: row.optJSONObject("record") ?: row
            val all = PixelMcpReadTools.effective(store.getState())
            TestDataRules.noDelivery(record, all)
            if (kind == "invoice" && !IssuedInvoiceRules.isIssued(record)) throw IOException("Nur ausgestellte Originalbelege teilen.")
            if (kind == "offer" && record.optString("finalized_at").isEmpty()) throw IOException("Nur finalisierte Originalangebote teilen.")
            val bytes = BusinessDocumentStore(context).read(record)
            if (bytes.size !in 5..(20 * 1024 * 1024) || String(bytes, 0, 5, Charsets.US_ASCII) != "%PDF-") throw IOException("Original-PDF fehlt oder ist verändert.")
            val dir = File(context.cacheDir, "invoice-share"); if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Freigabeordner fehlt.")
            val file = File(dir, (if (kind == "offer") "Angebot-" else "Rechnung-") + UUID.randomUUID() + ".pdf")
            file.writeBytes(bytes)
            val uri = Uri.parse("content://${context.packageName}.invoicefiles/${file.name}")
            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, if (kind == "offer") "Angebot" else "Rechnung " + record.optString("invoice_number"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
                    clipData = ClipData.newRawUri("PDF", uri)
                    val recipient = if (kind == "offer") record.optString("delivery_recipient", record.optJSONObject("recipient")?.optString("email") ?: "") else record.optJSONObject("recipient")?.optString("email") ?: ""
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
            val data = query(if (kind == "offer") "get_offer" else "get_invoice", JSONObject().put(if (kind == "offer") "offer_id" else "invoice_id", id))
            val record = data.optJSONObject(kind) ?: data.optJSONObject("record") ?: data
            val bytes = BusinessDocumentStore(context).read(record)
            val directory = File(context.cacheDir, "invoice-share"); if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Freigabeordner fehlt.")
            val file = File(directory, (if (kind == "offer") "Angebot-" else "Rechnung-") + UUID.randomUUID() + ".pdf"); file.writeBytes(bytes)
            val uri = Uri.parse("content://${context.packageName}.invoicefiles/${file.name}")
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
        tool == "update_settings" -> call("get_settings")
        tool == "update_business_settings" -> call("get_business_settings")
        tool == "archive_entity" -> call("preview_delete", JSONObject().put("entity", id("entity")).put("entity_id", id("entity_id")))
        tool == "purge_entity" -> call("preview_purge", JSONObject().put("entity", id("entity")).put("entity_id", id("entity_id")))
        tool == "empty_trash" -> call("preview_empty_trash")
        tool == "archive_work_records" -> call("preview_archive_work_records", JSONObject().put("work_record_ids", arguments.getJSONArray("work_record_ids")))
        tool == "review_work_session" && id("session_id").isNotEmpty() -> withContext(Dispatchers.IO) { JSONObject().put("data", store.workSessions().current(id("session_id"))) }
        id("offer_id").isNotEmpty() -> call("get_offer", JSONObject().put("offer_id", id("offer_id")).apply { if (tool == "finalize_offer") put("include_review", true) })
        id("invoice_id").isNotEmpty() -> call("get_invoice", JSONObject().put("invoice_id", id("invoice_id")))
        id("work_record_id").isNotEmpty() -> call("get_work_record", JSONObject().put("work_record_id", id("work_record_id")))
        id("photo_id").isNotEmpty() -> call("get_photo", JSONObject().put("photo_id", id("photo_id")))
        id("order_id").isNotEmpty() -> call("get_order", JSONObject().put("order_id", id("order_id")))
        id("customer_id").isNotEmpty() -> call("get_customer", JSONObject().put("customer_id", id("customer_id")))
        else -> JSONObject()
    }
}
