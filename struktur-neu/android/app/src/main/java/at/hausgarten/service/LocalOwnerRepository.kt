package at.hausgarten.service

import android.content.*
import android.database.sqlite.SQLiteDatabase
import java.io.*
import java.util.*
import org.json.*

class LocalOwnerRepository : OwnerRepository {
    private var context: Context?
    private var store: PixelLocalStore?
    private val requests: OwnerRequestTransactions.Store

    constructor(context: Context, store: PixelLocalStore) {
        this.context = context
        this.store = store
        this.requests =
            object : OwnerRequestTransactions.Store {
                override fun begin(): Unit {
                    store!!.beginBusinessTransaction(store!!.getWritableDatabase())
                }

                override fun read(key: String): String {
                    return store!!.ownerValue(key, "")!!
                }

                override fun write(key: String, value: String): Unit {
                    store!!.putOwnerValue(key, value)
                }

                override fun successful(): Unit {
                    store!!.getWritableDatabase().setTransactionSuccessful()
                }

                override fun end(): Unit {
                    store!!.getWritableDatabase().endTransaction()
                }
            }
    }

    constructor(requests: OwnerRequestTransactions.Store) {
        this.context = null
        this.store = null
        this.requests = requests
    }

    override fun state(): JSONObject {
        return store!!.getState()
    }

    @Throws(Exception::class)
    override fun effective(): JSONObject {
        return PixelMcpReadTools.effective(state())
    }

    @Throws(Exception::class)
    override fun settings(): JSONObject {
        return JSONObject(store!!.ownerValue("settings", "{}"))
    }

    override fun settlementSeries(): String {
        val identity = BackupDeviceGuard(context!!).current()
        return "GP-" +
            PixelMcpWriteTools.hash(
                    identity.getString("installationId") + ":" + identity.getString("restoreEpoch")
                )
                .take(32)
    }

    override fun settlementRestoreEpoch(): String =
        BackupDeviceGuard(context!!).current().getString("restoreEpoch")

    override fun createSettlementDocument(record: JSONObject): JSONObject =
        BusinessDocumentStore(context!!).createSettlement(record)

    @Throws(Exception::class)
    override fun fileMeta(id: String): JSONObject {
        store!!
            .getReadableDatabase()
            .rawQuery("SELECT metadata FROM mcp_uploads WHERE id=?", arrayOf(id))
            .use { row ->
                if (!(row.moveToFirst())) {
                    throw IllegalArgumentException("Die Dateireferenz fehlt.")
                }
                return JSONObject(row.getString(0))
            }
    }

    @Throws(Exception::class)
    override fun fileBytes(id: String): ByteArray {
        val metadata = fileMeta(id)
        if (metadata.has("private_upload_storage_name")) {
            val name = metadata.getString("private_upload_storage_name")
            if (!name.matches(Regex("[a-f0-9]{64}\\.bin")))
                throw IOException("UPLOAD_FILE_REFERENCE_INVALID")
            val file = File(File(context!!.filesDir, "mcp-photo-uploads"), name)
            val expected = metadata.getLong("private_upload_byte_size")
            if (expected < 0 || expected > 64L * 1024 * 1024 || file.length() != expected)
                throw IOException("UPLOAD_FILE_SIZE_MISMATCH")
            val bytes = java.nio.file.Files.readAllBytes(file.toPath())
            if (McpFileTransfer.sha(bytes) != metadata.getString("private_upload_sha256"))
                throw IOException("UPLOAD_FILE_HASH_MISMATCH")
            return bytes
        }

        var size: Int
        store!!
            .getReadableDatabase()
            .rawQuery("SELECT length(data) FROM mcp_uploads WHERE id=?", arrayOf(id))
            .use { row ->
                if (!(row.moveToFirst())) {
                    throw IllegalArgumentException("Dateireferenz fehlt.")
                }
                size = row.getInt(0)
            }
        if (size > 64 * 1024 * 1024) {
            throw IOException("Datei überschreitet Transfergrenze.")
        }
        var out = ByteArrayOutputStream(size)
        run {
            var offset: Int = 0
            var firstIteration20 = true
            while (true) {
                if (firstIteration20) firstIteration20 = false
                else {
                    offset += PixelMcpOwnerTools.CHUNK
                }
                if (!(offset < size)) break

                store!!
                    .getReadableDatabase()
                    .rawQuery(
                        "SELECT substr(data,?,?) FROM mcp_uploads WHERE id=?",
                        arrayOf(
                            (offset + 1).toString(),
                            (Math.min(PixelMcpOwnerTools.CHUNK, size - offset)).toString(),
                            id,
                        ),
                    )
                    .use { row ->
                        if (!(row.moveToFirst())) {
                            throw IOException("Dateiblock fehlt.")
                        }
                        out.write(row.getBlob(0))
                    }
            }
        }
        return out.toByteArray()
    }

    @Throws(Exception::class)
    override fun putFile(id: String, metadata: JSONObject, data: ByteArray) {
        // Immutable content files keep a rolled-back SQLite reference valid. Legacy
        // mcp_uploads BLOBs remain readable and remain in complete SQLite backups.
        val stored =
            JSONObject(metadata.toString())
                .put("last_written_at", java.time.Instant.now().toString())
        val photoUpload = stored.optString("kind") == "photo_upload"
        val database = store!!.writableDatabase
        // Cleanup also takes the SQLite writer. Even callers without an outer owner
        // transaction must publish the file and its reference within that boundary.
        if (photoUpload) database.acquireReference()
        var began = false
        try {
            if (photoUpload) {
                database.beginTransactionNonExclusive()
                began = true
            }
            if (photoUpload) {
                if (data.size > 64 * 1024 * 1024)
                    throw IOException("Datei überschreitet Transfergrenze.")
                val sha = McpFileTransfer.sha(data)
                val directory = File(context!!.filesDir, "mcp-photo-uploads")
                if (!directory.isDirectory && !directory.mkdirs())
                    throw IOException("UPLOAD_DIRECTORY_UNAVAILABLE")
                val name = sha + ".bin"
                val file = File(directory, name)
                if (file.exists()) {
                    if (
                        file.length() != data.size.toLong() ||
                            McpFileTransfer.sha(java.nio.file.Files.readAllBytes(file.toPath())) !=
                                sha
                    )
                        throw IOException("UPLOAD_FILE_HASH_MISMATCH")
                } else {
                    // Backup pins this directory as immutable. Publish only a complete,
                    // synced file; no .new or partial stream may appear in its listing.
                    val staging =
                        File.createTempFile("mcp-photo-stage-", ".tmp", context!!.cacheDir)
                    try {
                        FileOutputStream(staging).use { output ->
                            output.write(data)
                            output.fd.sync()
                        }
                        synchronized(UPLOAD_PUBLICATION_LOCK) {
                            if (file.exists()) {
                                if (
                                    file.length() != data.size.toLong() ||
                                        McpFileTransfer.sha(
                                            java.nio.file.Files.readAllBytes(file.toPath())
                                        ) != sha
                                )
                                    throw IOException("UPLOAD_FILE_HASH_MISMATCH")
                            } else
                                java.nio.file.Files.move(
                                    staging.toPath(),
                                    file.toPath(),
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                )
                        }
                    } finally {
                        staging.delete()
                    }
                }
                stored
                    .put("private_upload_storage_name", name)
                    .put("private_upload_sha256", sha)
                    .put("private_upload_byte_size", data.size)
            }
            val values =
                ContentValues().apply {
                    put("id", id)
                    put("metadata", stored.toString())
                    put("data", if (photoUpload) ByteArray(0) else data)
                }
            database.insertWithOnConflict(
                "mcp_uploads",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            if (photoUpload) database.setTransactionSuccessful()
        } finally {
            try {
                if (began) database.endTransaction()
            } finally {
                if (photoUpload) database.releaseReference()
            }
        }
    }

    /** Reclaim replaced upload prefixes only after the owner transaction has ended. */
    @Throws(Exception::class)
    fun pruneUnreferencedPhotoUploads() {
        val database = store!!.writableDatabase
        database.acquireReference()
        try {
            // Nested owner work has not physically committed and must never wait on the
            // capture monitor while holding SQLite's writer (backup takes the reverse).
            if (database.inTransaction()) return
            synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
                val directory = File(context!!.filesDir, "mcp-photo-uploads")
                if (!directory.exists()) return
                if (
                    !directory.isDirectory || java.nio.file.Files.isSymbolicLink(directory.toPath())
                )
                    throw IOException("UPLOAD_DIRECTORY_INVALID")
                database.beginTransactionNonExclusive()
                try {
                    val referenced = HashSet<String>()
                    database.rawQuery("SELECT metadata FROM mcp_uploads", null).use { rows ->
                        while (rows.moveToNext()) {
                            val metadata = JSONObject(rows.getString(0))
                            if (metadata.has("private_upload_storage_name")) {
                                val name = metadata.getString("private_upload_storage_name")
                                if (!name.matches(Regex("[a-f0-9]{64}\\.bin")))
                                    throw IOException("UPLOAD_FILE_REFERENCE_INVALID")
                                referenced.add(name)
                            }
                        }
                    }
                    val files =
                        directory.listFiles() ?: throw IOException("UPLOAD_DIRECTORY_UNREADABLE")
                    val canonical = directory.canonicalFile
                    for (file in files) {
                        if (
                            file.name.matches(Regex("[a-f0-9]{64}\\.bin")) &&
                                file.name !in referenced &&
                                !java.nio.file.Files.isSymbolicLink(file.toPath()) &&
                                file.isFile &&
                                file.canonicalFile.parentFile == canonical
                        ) {
                            if (!file.delete() && file.exists())
                                throw IOException("UPLOAD_PREFIX_CLEANUP_PENDING")
                        }
                    }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            }
        } finally {
            database.releaseReference()
        }
    }

    @Throws(Exception::class)
    fun asset(path: String): ByteArray {
        context!!.getAssets().open(path).use { input ->
            var out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var n: Int
            while (true) {
                n = input.read(buffer)
                if (n == -1) break
                if (out.size() + n > 20 * 1024 * 1024) {
                    throw IOException("Datei zu groß.")
                }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun localOriginalMissing(): PixelMcpWriteTools.Rejected {
        return PixelMcpWriteTools.Rejected(
            "LOCAL_ORIGINAL_MISSING",
            "Das benötigte Original fehlt im lokalen Bestand. Das Original gezielt importieren oder eine geprüfte vollständige Sicherung am Gerät prüfen; nichts geändert.",
        )
    }

    @Throws(Exception::class)
    fun readChunk(a: JSONObject): JSONObject {
        val type = a.getString("resource_type")
        var id = a.getString("resource_id")
        var mime: String
        var bytes: ByteArray
        when (type) {
            "photo" -> {
                run {
                    var listed: Boolean = false
                    val photos = effective().getJSONArray("photos")
                    for (i in 0 until photos.length()) {
                        if (id == photos.getJSONObject(i).optString("id")) {
                            listed = true
                        }
                    }
                    if (
                        !(listed) &&
                            RevierApplicationService(RevierLocalRepository(store!!, null))
                                .equipmentPhoto(id) == null
                    ) {
                        throw IllegalArgumentException("Das Foto besitzt keine aktuelle Zuordnung.")
                    }
                    val m = store!!.photoAttachment(id)
                    if (m == null) {
                        throw localOriginalMissing()
                    }
                    try {
                        bytes = store!!.photoAttachmentStore().read(m)
                    } catch (absent: IOException) {
                        throw localOriginalMissing()
                    }
                    mime = m.getString("mime_type")
                }
            }
            "offer" -> {
                run {
                    val offer = PixelMcpWriteTools.find(effective().getJSONArray("offers"), id)
                    if (
                        (offer.optString("pdf_storage_name").isEmpty() ||
                            offer.optString("pdf_sha256").isEmpty())
                    ) {
                        throw localOriginalMissing()
                    }
                    try {
                        bytes = BusinessDocumentStore(context!!).read(offer)
                    } catch (missing: IOException) {
                        throw localOriginalMissing()
                    }
                    mime = "application/pdf"
                }
            }
            else -> throw IllegalArgumentException("Unbekannter Dateityp.")
        }

        val hash = digest(bytes)
        if (!(a.has("expected_sha256"))) {
            return JSONObject()
                .put("resource_type", type)
                .put("resource_id", id)
                .put("mime_type", mime)
                .put("byte_size", bytes.size)
                .put("sha256", hash)
        }
        if (!(hash == a.getString("expected_sha256"))) {
            throw PixelMcpWriteTools.Rejected(
                "FILE_VERSION_CONFLICT",
                "Prüfsumme des Originals stimmt nicht mit dem angeforderten Stand überein.",
            )
        }
        var offset: Int = a.getInt("offset")
        val length: Int = a.optInt("length", PixelMcpOwnerTools.CHUNK)
        if (offset > bytes.size) {
            throw IllegalArgumentException("Dateibereich fehlt.")
        }
        val part = Arrays.copyOfRange(bytes, offset, Math.min(bytes.size, offset + length))
        return JSONObject()
            .put("resource_type", type)
            .put("resource_id", id)
            .put("mime_type", mime)
            .put("byte_size", bytes.size)
            .put("sha256", hash)
            .put("offset", offset)
            .put("next_offset", offset + part.size)
            .put("eof", offset + part.size == bytes.size)
            .put("chunk_sha256", digest(part))
            .put("chunk_base64", Base64.getEncoder().encodeToString(part))
    }

    @Throws(Exception::class)
    override fun enqueue(kind: String, payload: JSONObject, request: String): JSONObject {
        val db = store!!.getWritableDatabase()
        store!!.beginBusinessTransaction(db)
        try {
            // All guards before Revier's optional detach see the same journal version.
            // Replaying it for every guard made even a two-work selection needlessly slow.
            val currentBeforeRevier by lazy { effective() }
            val settlementLedger by lazy { JSONObject(ownerValue("settlement:v1", "{}")) }
            val hasSettlementRecords by lazy {
                (settlementLedger.optJSONArray("records")?.length() ?: 0) > 0
            }
            fun protectSettlement(entity: String, id: String) {
                if (hasSettlementRecords)
                    SettlementSourceProtection.requireDeletable(
                        currentBeforeRevier,
                        settlementLedger,
                        entity,
                        id,
                    )
            }
            if (kind in setOf("archive", "delete", "purge_v1"))
                protectSettlement(payload.optString("entity"), payload.optString("id"))
            if ((kind == "order" || kind == "worklog") && hasSettlementRecords) {
                val collection = if (kind == "order") "orders" else "worklogs"
                val id = payload.optString("id")
                val previous =
                    OwnerJournalRules.findOrNull(currentBeforeRevier.getJSONArray(collection), id)
                SettlementSourceProtection.requireMutable(
                    currentBeforeRevier,
                    settlementLedger,
                    collection,
                    id,
                    previous,
                    payload,
                )
            }

            if (kind == "purge_v1" && payload.has("expected_test_dependencies_hash")) {
                val targets =
                    TrashRules.testTargets(
                        currentBeforeRevier,
                        payload.getString("entity"),
                        payload.getString("id"),
                    )
                for (i in 0 until targets.length()) {
                    val target = targets.getJSONObject(i)
                    protectSettlement(target.getString("entity"), target.getString("entity_id"))
                    ActiveWorkProtection.require(
                        state(),
                        target.getString("entity"),
                        target.getString("entity_id"),
                    )
                    revier()
                        .protectParent(target.getString("entity"), target.getString("entity_id"))
                }
            }
            if (Arrays.asList("archive", "delete", "purge_v1").contains(kind)) {
                revier().protectParent(payload.optString("entity"), payload.optString("id"))
                revier()
                    .detachOptionalParent(
                        payload.optString("entity"),
                        payload.optString("id"),
                        request,
                    )
            }
            if ((kind == "photo") && (payload.optString("role") == "profile")) {
                revier().protectParent("photo", payload.optString("id"))
            }
            if (kind == TrashPurgeBatch.KIND) {
                val entries = payload.getJSONArray("entries")
                val targets = TrashPurgeBatch.testTargets(currentBeforeRevier, entries)
                for (i in 0 until targets.length()) {
                    val target = targets.getJSONObject(i)
                    protectSettlement(target.getString("entity"), target.getString("entity_id"))
                    ActiveWorkProtection.require(
                        state(),
                        target.getString("entity"),
                        target.getString("entity_id"),
                    )
                    revier()
                        .protectParent(target.getString("entity"), target.getString("entity_id"))
                }
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    protectSettlement(entry.getString("entity"), entry.getString("entity_id"))
                    revier().protectParent(entry.getString("entity"), entry.getString("entity_id"))
                    revier()
                        .detachOptionalParent(
                            entry.getString("entity"),
                            entry.getString("entity_id"),
                            request,
                        )
                }
            }
            val prior = store!!.pendingActions()
            // Only order/appointment detach and a mixed trash batch can change Revier state.
            // Re-read after those paths; ordinary work deletion has no intervening mutation.
            val currentAfterRevier by lazy {
                if (
                    kind == TrashPurgeBatch.KIND ||
                        kind in setOf("archive", "delete", "purge_v1") &&
                            payload.optString("entity") in setOf("order", "appointment")
                )
                    effective()
                else currentBeforeRevier
            }
            if (kind == "order" || kind == "worklog") {
                AccountingRules.requireRecordChange(
                    currentAfterRevier,
                    AccountingRules.ids(JSONArray(ownerValue("personal_paid", "[]"))),
                    if (kind == "order") "orders" else "worklogs",
                    payload,
                )
            }
            val removal = kind in setOf("archive", "delete", "purge_v1")
            var predecessors =
                LocalActionOrder.predecessors(
                    kind,
                    payload,
                    prior,
                    if (removal) currentAfterRevier else null,
                )
            if (predecessors.length() > 0) payload.put("_sync_predecessors", predecessors)
            if ((kind == "appointment") || (kind == "offer_convert")) {
                val parentProjection = JSONObject(state().getJSONObject("master").toString())
                LocalJournalRules.apply(parentProjection, prior)
                var incorporated =
                    LocalJournalRules.ids(parentProjection.optJSONArray("drive_applied_ids"))
                var targetOrder =
                    payload.optString("order_id", payload.optString("existing_order_id"))
                for (i in 0 until prior.length()) {
                    val event = prior.getJSONObject(i)
                    var p = event.getJSONObject("payload")
                    val previous = event.getString("client_id")
                    val type = event.getString("kind")
                    if (!(incorporated.contains(previous))) {
                        continue
                    }
                    var parent: Boolean =
                        (type == "customer") &&
                            p.optBoolean("_mcp_create") &&
                            (payload.optString("customer_id") == p.optString("id"))
                    if (
                        !(targetOrder.isEmpty()) &&
                            (type == "order") &&
                            (targetOrder == p.optString("id"))
                    ) {
                        parent = true
                    }
                    if (!(targetOrder.isEmpty()) && (type == "offer_convert")) {
                        var converted = p.optString("existing_order_id")
                        if (converted.isEmpty()) {
                            converted =
                                "ord_offer_" + PixelMcpWriteTools.hash(previous).substring(0, 32)
                        }
                        if (targetOrder == converted) {
                            parent = true
                        }
                    }
                    if (parent && !(predecessors.toString().contains(JSONObject.quote(previous)))) {
                        predecessors.put(previous)
                    }
                }
            }
            if (predecessors.length() > 0) {
                payload.put("_sync_predecessors", predecessors)
            }
            if (store!!.testLocalOnly(kind, payload)) {
                payload.put("_local_only", true)
            }
            val a = ContentValues()
            a.put("id", request)
            a.put("kind", kind)
            a.put("payload", payload.toString())
            a.put("created_at", java.time.Instant.now().toString())
            store!!.getWritableDatabase().insertOrThrow("pending", null, a)
            var result =
                JSONObject()
                    .put("client_id", request)
                    .put("entity_type", payload.optString("entity", kind))
                    .put("entity_id", payload.optString("id"))
                    .put("local_state", "local_saved")
                    .put("sync_state", "not_applicable")
                    .put("test_local_only", payload.optBoolean("_local_only"))
                    .put("cloud_confirmed", false)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override @Throws(Exception::class) fun pendingActions(): JSONArray {
        return store!!.pendingActions()
    }

    override fun ownerValue(key: String, fallback: String): String {
        return store!!.ownerValue(key, fallback)!!
    }

    override fun putOwnerValue(key: String, value: String): Unit {
        if (key == "personal_paid") {
            val before = AccountingRules.ids(JSONArray(store!!.ownerValue("personal_paid", "[]")))
            val added = AccountingRules.ids(JSONArray(value)) - before
            if (added.isNotEmpty()) {
                val master = effective()
                val ledger = JSONObject(store!!.ownerValue("settlement:v1", "{}"))
                for (id in added) {
                    val (collection, record) = AccountingRules.canonical(master, "worklogs", id)
                    SettlementSourceProtection.requireRouteChange(
                        master,
                        ledger,
                        collection,
                        record.getString("id"),
                        AccountingRules.LABOR,
                    )
                }
            }
        }
        store!!.putOwnerValue(key, value)
    }

    override @Throws(Exception::class) fun savePhoto(payload: JSONObject): String {
        return store!!.savePhoto(payload)
    }

    override @Throws(Exception::class) fun replaceWorklog(payload: JSONObject): Unit {
        val prior = HashSet<String>()
        val before = store!!.pendingActions()
        for (i in 0 until before.length()) {
            prior.add(before.getJSONObject(i).getString("client_id"))
        }
        store!!.saveWorklog(payload)
        val after = store!!.pendingActions()
        for (i in 0 until after.length()) {
            val added = after.getJSONObject(i)
            if (
                !(prior.contains(added.getString("client_id"))) &&
                    ("worklog" == added.getString("kind")) &&
                    (payload.getString("id") == added.getJSONObject("payload").optString("id"))
            ) {
                store!!
                    .getWritableDatabase()
                    .delete("pending", "id=?", arrayOf(added.getString("client_id")))
            }
        }
    }

    override @Throws(Exception::class) fun createDocument(record: JSONObject): JSONObject {
        return BusinessDocumentStore(context!!).create(record)
    }

    override @Throws(Exception::class) fun readDocument(record: JSONObject): Unit {
        BusinessDocumentStore(context!!).read(record)
    }

    override @Throws(Exception::class) fun verifyDelivery(
        messageId: String,
        offer: JSONObject,
    ): JSONObject {
        return MailAccount.client(context!!).verify(messageId, offer)
    }

    override @Throws(Exception::class) fun planningSettings(): JSONObject {
        return AppointmentRuntime.publicSettings(context!!, store!!)
    }

    override fun calendarConnected(): Boolean {
        return PlanningAccount.connected(context!!)
    }

    override fun weatherCredentialAvailable(): Boolean {
        return PlanningAccount.prefs(context!!).contains("weather_secret")
    }

    override @Throws(Exception::class) fun savePlanningSettings(
        settings: JSONObject,
        expected: String,
    ): Unit {
        AppointmentRuntime.saveSettings(context!!, store!!, settings, expected)
    }

    override @Throws(Exception::class) fun requestPlanning(): JSONObject {
        return AppointmentRuntime.request(context!!, store!!)
    }

    override @Throws(Exception::class) fun acknowledgeWeather(args: JSONObject): JSONObject {
        return AppointmentRuntime.acknowledge(context!!, store!!, args)
    }

    override @Throws(Exception::class) fun resolveCalendar(args: JSONObject): JSONObject {
        return AppointmentRuntime.resolve(context!!, store!!, args)
    }

    override @Throws(Exception::class) fun checkBusy(appointment: JSONObject): Unit {
        AppointmentRuntime.checkBusy(context!!, store!!, appointment)
    }

    override @Throws(Exception::class) fun fullBackupStatus(args: JSONObject): JSONObject {
        return PixelBackupService(context!!, store!!).status(args)
    }

    override @Throws(Exception::class) fun fullBackupAction(
        name: String,
        args: JSONObject,
    ): JSONObject {
        val control = PixelBackupService(context!!, store!!)
        if (name == "request_full_backup") {
            return control.request(args)
        }
        if (name == "retry_full_backup") {
            return control.retry(args)
        }
        throw IllegalArgumentException("Sicherungsaktion fehlt.")
    }

    private @Throws(Exception::class) fun revier(): RevierApplicationService {
        return RevierApplicationService(
            RevierLocalRepository(
                store!!,
                LAM@{ a ->
                    val operation = a.optString("_operation", "create_appointment")
                    a.remove("_operation")
                    return@LAM PlanningApplicationService(this)
                        .perform(
                            operation,
                            a,
                            "mcp_revier_inspection_" +
                                PixelMcpWriteTools.hash(a.getString("request_id")),
                        )
                },
            )
        )
    }

    override @Throws(Exception::class) fun revierQuery(name: String, args: JSONObject): JSONObject {
        return revier().query(name, args)
    }

    override @Throws(Exception::class) fun revierAction(
        name: String,
        args: JSONObject,
    ): JSONObject {
        return revier().execute(name, args)
    }

    fun interface Request {
        @Throws(Exception::class) fun run(): JSONObject
    }

    @Throws(Exception::class)
    fun runRequest(request: String, fingerprint: String, command: Request): JSONObject {
        return OwnerRequestTransactions.runRequest(
            requests,
            request,
            fingerprint,
            command::run,
            java.time.Instant.now().toString(),
        )
    }

    @Throws(Exception::class)
    fun photoStorageSummary(): JSONObject {
        return store!!.photoAttachmentStore().storageSummary()
    }

    fun migratePersonalPaid(value: String): String {
        val db = store!!.getWritableDatabase()
        db.beginTransaction()
        try {
            if (!("1" == store!!.ownerValue("personal_paid_migrated", ""))) {
                val old = JSONArray(value)
                var current = JSONArray(store!!.ownerValue("personal_paid", "[]"))
                val union = java.util.LinkedHashSet<String>()
                for (list in arrayOf(old, current)) {
                    for (i in 0 until list.length()) {
                        var id = list.getString(i)
                        if (id.isEmpty() || id.length > 500) {
                            throw IllegalArgumentException()
                        }
                        union.add(id)
                    }
                }
                store!!.putOwnerValue("personal_paid", JSONArray(union).toString())
                store!!.putOwnerValue("personal_paid_migrated", "1")
            }
            db.setTransactionSuccessful()
            return "OK"
        } catch (e: Exception) {
            return "ERROR: Persönliche Übersicht bleibt erhalten; Übernahme nicht abgeschlossen."
        } finally {
            db.endTransaction()
        }
    }

    fun setPersonalPaid(value: String): String {
        try {
            val ids = JSONArray(value)
            var all = SnapshotProjection.effective(store!!.getState())
            for (i in 0 until ids.length()) {
                PixelMcpWriteTools.find(all.getJSONArray("worklogs"), ids.getString(i))
            }
            AccountingRules.requireConsistentChange(
                all,
                all,
                AccountingRules.ids(JSONArray(store!!.ownerValue("personal_paid", "[]"))),
                AccountingRules.ids(ids),
            )
            putOwnerValue("personal_paid", ids.toString())
            return "OK"
        } catch (e: Exception) {
            return "ERROR: Persönliche Übersicht konnte nicht gespeichert werden."
        }
    }

    companion object {
        // File publication only: never acquire SQLite while holding this monitor.
        private val UPLOAD_PUBLICATION_LOCK = Any()

        private @JvmStatic @Throws(Exception::class) fun digest(bytes: ByteArray): String {
            return PhotoAttachmentStore.sha256(bytes)
        }
    }
}
