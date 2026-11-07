package at.hausgarten.service

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Xml
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.Collections
import java.util.Locale
import java.util.UUID

/** Fully verified local ZIP preview, then a protected cold-start exchange. Never merges inventories. */
class BackupRestoreService @Throws(Exception::class) constructor(private val context: Context) {
    interface Protection {
        @Throws(Exception::class)
        fun enterExclusiveStartup(): AutoCloseable?

        @Throws(Exception::class)
        fun activePaths(): ActivePaths

        @Throws(Exception::class)
        fun blockBeforeActivation(requestId: String, restoredManifest: JSONObject)

        @Throws(Exception::class)
        fun afterActivation(requestId: String, restoredManifest: JSONObject, restoredDatabase: File)
    }

    fun interface Source {
        @Throws(Exception::class)
        fun open(): InputStream
    }

    class ActivePaths @Throws(Exception::class) constructor(
        database: File, filesDir: File, cacheDir: File, preferenceFiles: Map<String, File>
    ) {
        @JvmField val paths = LinkedHashMap<String, File>()

        init {
            paths["database_wal"] = File("$database-wal")
            paths["database_shm"] = File("$database-shm")
            paths["database_journal"] = File("$database-journal")
            paths["database"] = database
            paths["files"] = filesDir
            paths["photo_capture"] = File(cacheDir, "photo-capture")
            paths["invoice_share"] = File(cacheDir, "invoice-share")
            for (group in BackupPreferences.GROUPS) {
                val file = preferenceFiles[group] ?: throw IOException("RESTORE_PREFERENCE_PATH_REQUIRED")
                paths["preferences_${group}_bak"] = File("$file.bak")
                paths["preferences_$group"] = file
            }
            val seen = ArrayList<File>()
            for (value in paths.values) {
                val canonical = value.canonicalFile
                if (Files.isSymbolicLink(value.toPath())) throw IOException("RESTORE_ACTIVE_PATH_LINK_REJECTED")
                for (other in seen) {
                    if (canonical == other || canonical.toPath().startsWith(other.toPath()) ||
                        other.toPath().startsWith(canonical.toPath())) {
                        throw IOException("RESTORE_ACTIVE_PATH_OVERLAP")
                    }
                }
                seen.add(canonical)
            }
        }

        fun database(): File = paths["database"]!!
    }

    private val root = File(context.noBackupFilesDir, "full-restore")

    init {
        if (Files.isSymbolicLink(root.toPath())) throw IOException("RESTORE_STAGING_LINK_REJECTED")
    }

    @Throws(Exception::class)
    fun preview(requestId: String?, archiveUri: Uri?): JSONObject {
        if (archiveUri == null || archiveUri.scheme != "content") throw IOException("RESTORE_DOCUMENT_REQUIRED")
        return previewFrom(requestId, archiveUri.toString()) {
            context.contentResolver.openInputStream(archiveUri) ?: throw IOException("RESTORE_SOURCE_UNAVAILABLE")
        }
    }

    @Throws(Exception::class)
    fun previewFrom(requestId: String?, sourceId: String?, source: Source): JSONObject {
        request(requestId)
        val identity = BackupDeviceGuard(context).current()
        val restoreId = "restore_" + PixelMcpWriteTools.hash(
            JSONObject().put("request_id", requestId).put("installationId", identity.getString("installationId"))
                .put("restoreEpoch", identity.getString("restoreEpoch"))
        ).substring(0, 36)
        val fingerprint = PixelMcpWriteTools.hash(
            JSONObject().put("request_id", requestId).put("source", sourceId)
                .put("archive_format", "PLAIN_ZIP").put("identity", identity)
        )
        return synchronized(LOCK) {
            val work = work(restoreId)
            val info = File(work, "preview.json")
            val previous = readOptional(info)
            if (previous != null && previous.optInt("formatVersion") != 1) {
                throw IOException("RESTORE_PREVIEW_SCHEMA_UNSUPPORTED")
            }
            if (previous != null && previous.getString("request_fingerprint") != fingerprint) {
                throw IOException("RESTORE_REPLAY_MISMATCH")
            }
            if (previous != null && previous.optString("phase") == "VALIDATED") {
                validateStage(work, previous)
                return@synchronized previewView(previous)
            }
            if (previous != null && previous.optString("phase") !in listOf("VALIDATING", "FAILED")) {
                throw IOException("RESTORE_PREVIEW_NOT_REUSABLE")
            }
            val record = JSONObject().put("formatVersion", 1).put("restore_id", restoreId)
                .put("request_id", requestId).put("request_fingerprint", fingerprint)
                .put("installation_id", identity.getString("installationId"))
                .put("expected_restore_epoch", identity.getString("restoreEpoch"))
                .put("phase", "VALIDATING").put("started_at", Instant.now().toString())
            val directory = "stage-" + UUID.randomUUID()
            record.put("directory", directory)
            write(info, record)
            var staged: BackupArchive.Verified? = null
            try {
                BackupStorageBudget.require(work, 0)
                staged = BackupArchive.verifyPlain(
                    source.open(), child(work, directory),
                    Math.max(0L, work.usableSpace - BackupStorageBudget.BUSINESS_RESERVE_BYTES),
                    PixelLocalStore.SCHEMA_VERSION
                )
                val notes = LocalBackupDataset.inspectStaged(File(staged.directory, "data"), staged.manifest)
                write(File(work, "manifest.json"), staged.manifest)
                val manifestHash = BackupArchive.sha256(File(work, "manifest.json"))
                record.put("manifest_sha256", manifestHash).put(
                    "preview_hash", PixelMcpWriteTools.hash(
                        JSONObject().put("restore_id", restoreId).put("manifest_sha256", manifestHash)
                            .put("request_fingerprint", fingerprint)
                    )
                ).put("phase", "VALIDATED").put("validated_at", Instant.now().toString())
                record.put("warnings", notes.optJSONArray("warnings") ?: org.json.JSONArray())
                write(info, record)
                previewView(record)
            } catch (failure: Exception) {
                if (staged != null) try {
                    staged.close()
                } catch (ignored: Exception) {
                }
                record.put("phase", "FAILED").put("last_error_code", safeCode(failure))
                write(info, record)
                throw failure
            }
        }
    }

    @Throws(Exception::class)
    fun requestActivation(
        requestId: String?, restoreId: String?, expectedPreviewHash: String?, confirmed: Boolean
    ): JSONObject = requestActivation(requestId, restoreId, expectedPreviewHash, confirmed, false)

    @Throws(Exception::class)
    fun requestActivation(
        requestId: String?, restoreId: String?, expectedPreviewHash: String?, confirmed: Boolean,
        legacyMediaCoverageConfirmed: Boolean
    ): JSONObject {
        request(requestId)
        if (!confirmed) throw IOException("RESTORE_CONFIRMATION_REQUIRED")
        hash(expectedPreviewHash)
        return synchronized(LOCK) {
            val intent = JSONObject().put("request_id", requestId).put("restore_id", restoreId)
                .put("preview_hash", expectedPreviewHash).put("confirmed", true)
            if (legacyMediaCoverageConfirmed) intent.put("legacy_media_coverage_confirmed", true)
            val fingerprint = PixelMcpWriteTools.hash(intent)
            val receipt = receipt(requestId)
            if (receipt.exists() || File("$receipt.bak").exists()) {
                val previous = read(receipt)
                if (previous.getString("request_fingerprint") != fingerprint) {
                    throw IOException("RESTORE_ACTIVATION_REPLAY_MISMATCH")
                }
                val current = readOptional(activationFile())
                return@synchronized activationView(
                    if (current != null && current.optString("request_id") == requestId) current else previous
                )
            }
            val pending = readOptional(activationFile())
            if (pending != null) activationVersion(pending)
            if (pending != null && pending.optString("request_id") == requestId) {
                if (pending.getString("request_fingerprint") != fingerprint) {
                    throw IOException("RESTORE_ACTIVATION_REPLAY_MISMATCH")
                }
                write(receipt, pending)
                return@synchronized activationView(pending)
            }
            if (pending != null && PENDING.contains(pending.optString("phase"))) {
                throw IOException("RESTORE_OTHER_ACTIVATION_PENDING")
            }
            val work = work(restoreId)
            val record = read(File(work, "preview.json"))
            if (record.getString("phase") != "VALIDATED" || record.getString("preview_hash") != expectedPreviewHash) {
                throw IOException("RESTORE_PREVIEW_CHANGED")
            }
            requireTargetContext(record)
            val manifest = validateStage(work, record)
            val coverage = mediaCoverage(manifest)
            if (coverage == "LEGACY_NOT_PROVEN" && !legacyMediaCoverageConfirmed) {
                throw IOException("RESTORE_MEDIA_COVERAGE_CONFIRMATION_REQUIRED")
            }
            val request = JSONObject().put("formatVersion", 1).put("request_id", requestId)
                .put("request_fingerprint", fingerprint).put("restore_id", restoreId)
                .put("preview_hash", expectedPreviewHash).put("phase", "REQUESTED")
                .put("requested_at", Instant.now().toString()).put("media_coverage", coverage)
                .put("legacy_media_coverage_confirmed", legacyMediaCoverageConfirmed)
            write(activationFile(), request)
            write(receipt, request)
            activationView(request)
        }
    }

    @Throws(Exception::class)
    fun cancelActivation(requestId: String?, restoreId: String?, expectedPreviewHash: String?): JSONObject {
        request(requestId)
        hash(expectedPreviewHash)
        return synchronized(LOCK) {
            val state = read(activationFile())
            activationVersion(state)
            if (state.getString("restore_id") != restoreId || state.getString("preview_hash") != expectedPreviewHash) {
                throw IOException("RESTORE_PREVIEW_CHANGED")
            }
            if (state.optString("phase") == "CANCELLED") {
                if (state.optString("cancel_request_id") != requestId) {
                    throw IOException("RESTORE_CANCELLATION_REPLAY_MISMATCH")
                }
                return@synchronized activationView(state)
            }
            if (state.optString("phase") !in listOf("REQUESTED", "FAILED_HOLD", "ROLLED_BACK") ||
                state.has("targets") && BackupDatasetExchange.anyOldMoved(state) &&
                state.optString("phase") != "ROLLED_BACK") {
                throw IOException("RESTORE_CANCELLATION_REQUIRES_RECOVERY")
            }
            state.put("phase", "CANCELLED").put("cancel_request_id", requestId)
            write(receipt(state.getString("request_id")), state)
            write(activationFile(), state)
            activationView(state)
        }
    }

    @Throws(Exception::class)
    fun status(restoreId: String?): JSONObject = synchronized(LOCK) {
        val activation = readOptional(activationFile())
        if (activation != null && activation.optString("restore_id") == restoreId) {
            return@synchronized activationView(activation)
        }
        previewView(read(File(work(restoreId), "preview.json")))
    }

    @Throws(Exception::class)
    private fun prepareTargets(work: File, record: JSONObject, manifest: JSONObject): Map<String, File> {
        val data = File(child(work, record.getString("directory")), "data")
        val targets = LinkedHashMap<String, File>()
        targets["database"] = File(data, "database.sqlite")
        for (item in arrayOf(
            arrayOf("files", "files"), arrayOf("photo_capture", "cache/photo-capture"),
            arrayOf("invoice_share", "cache/invoice-share")
        )) {
            val directory = File(data, item[1])
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("RESTORE_PREPARED_STORAGE_UNAVAILABLE")
            targets[item[0]] = directory
        }
        val preferences = JSONObject(readText(File(data, "preferences.json"), Int.MAX_VALUE))
        val prepared = File(work, "prepared-preferences")
        if (!prepared.isDirectory && !prepared.mkdirs()) throw IOException("RESTORE_PREPARED_STORAGE_UNAVAILABLE")
        for (group in BackupPreferences.GROUPS) {
            val values = BackupPreferences.values(preferences, group)
            val types = preferences.getJSONObject("groups").getJSONObject(group).getJSONObject("types")
            val safe = JSONObject(values.toString())
            val safeTypes = JSONObject(types.toString())
            sanitize(group, safe, safeTypes)
            val file = File(prepared, "$group.xml")
            writePreferences(file, safe, safeTypes)
            targets["preferences_$group"] = file
        }
        return targets
    }

    @Throws(Exception::class)
    private fun validateStage(work: File, record: JSONObject): JSONObject {
        val manifestFile = File(work, "manifest.json")
        if (!manifestFile.isFile || BackupArchive.sha256(manifestFile) != record.getString("manifest_sha256")) {
            throw IOException("RESTORE_PREVIEW_CHANGED")
        }
        val manifest = read(manifestFile)
        if (manifest.optString("archiveProtection") != "NONE") throw IOException("BACKUP_FORMAT_UNSUPPORTED")
        val stage = child(work, record.getString("directory"))
        val data = File(stage, "data")
        val expected = HashSet<String>()
        val files = manifest.getJSONArray("files")
        for (i in 0 until files.length()) {
            val entry = files.getJSONObject(i)
            val path = entry.getString("path")
            val file = BackupArchive.Source(path, File(data, path)).file
            if (!file.canonicalPath.startsWith(data.canonicalPath + File.separator) ||
                Files.isSymbolicLink(file.toPath()) || !file.isFile || file.length() != entry.getLong("size") ||
                BackupArchive.sha256(file) != entry.getString("sha256")) {
                throw IOException("RESTORE_STAGED_FILE_CHANGED")
            }
            expected.add(path)
        }
        for (source in BackupFilePins.sources(data)) {
            if (!expected.contains(source.path)) throw IOException("RESTORE_UNEXPECTED_STAGED_FILE")
        }
        LocalBackupDataset.inspectStaged(data, manifest)
        return manifest
    }

    @Throws(Exception::class)
    private fun requireTargetContext(record: JSONObject) {
        val identity = BackupDeviceGuard(context).current()
        if (record.getString("installation_id") != identity.getString("installationId") ||
            record.getString("expected_restore_epoch") != identity.getString("restoreEpoch")) {
            throw IOException("RESTORE_TARGET_CONTEXT_CHANGED")
        }
    }

    @Throws(Exception::class)
    private fun previewView(record: JSONObject): JSONObject {
        val out = JSONObject().put("restore_id", record.getString("restore_id")).put("phase", record.getString("phase"))
            .put("current_state_unchanged", true)
        if (record.has("preview_hash")) {
            out.put("preview_hash", record.getString("preview_hash"))
            val manifest = read(File(work(record.getString("restore_id")), "manifest.json"))
            out.put(
                "summary", JSONObject().put("captured_at", manifest.getString("capturedAt"))
                    .put("app_version", manifest.getString("appVersion")).put("warning_count", manifest.optInt("warning_count"))
                    .put("schema_version", manifest.getInt("schemaVersion")).put("total_bytes", manifest.getLong("totalBytes"))
                    .put("counts", manifest.getJSONObject("counts"))
                    .put("warnings", record.optJSONArray("warnings") ?: org.json.JSONArray())
                    .put("last_issued_document_count", manifest.getJSONArray("lastIssuedDocumentIds").length())
                    .put("media_coverage", mediaCoverage(manifest))
            )
        }
        if (record.optString("phase") == "VALIDATED") {
            OPEN_PREVIEWS.add(record.getString("restore_id"))
            // Read current counts separately: the backup is a replacement, never a merge.
            try {
                PixelLocalStore(context).use { store ->
                    val effective = LocalOwnerRepository(context, store).effective()
                    val counts = JSONObject()
                    for (collection in arrayOf("customers", "worklogs", "photos", "offers", "orders", "appointments")) {
                        counts.put(collection, effective.optJSONArray(collection)?.length() ?: 0)
                    }
                    out.put("current_counts", counts).put("current_counts_at", Instant.now().toString())
                }
            } catch (_: Exception) {
                out.put("current_counts_unavailable", true)
            }
        }
        if (record.has("last_error_code")) out.put("last_error_code", record.getString("last_error_code"))
        return out
    }

    private fun activationFile(): File = File(root, "activation.json")

    @Throws(Exception::class)
    private fun receipt(request: String?): File = File(root, "activation-requests/${PixelMcpWriteTools.hash(request)}.json")

    @Throws(Exception::class)
    private fun work(id: String?): File {
        if (id == null || !id.matches(Regex("restore_[a-f0-9]{36}"))) throw IOException("RESTORE_ID_INVALID")
        val directory = child(root, id)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("RESTORE_STAGING_UNAVAILABLE")
        return directory
    }

    companion object {
        private val LOCK = Any()
        private val OPEN_PREVIEWS = HashSet<String>()
        private val PENDING = hashSetOf("REQUESTED", "ACTIVATING", "SWAPPED", "ROLLING_BACK", "FAILED_HOLD")

        @JvmStatic
        @Throws(Exception::class)
        fun discardPreview(context: Context, id: String) = synchronized(LOCK) {
            if (!id.matches(Regex("restore_[a-f0-9]{36}"))) return@synchronized
            OPEN_PREVIEWS.remove(id)
            val state = readOptional(File(context.noBackupFilesDir, "full-restore/activation.json"))
            if (state != null && PENDING.contains(state.optString("phase")) && state.optString("restore_id") == id) return@synchronized
            val root = File(context.noBackupFilesDir, "full-restore")
            BackupArchive.removeWorkTree(File(root, id))
            if (root.listFiles()?.isEmpty() == true && !root.delete()) throw IOException("RESTORE_STAGING_CLEANUP_REQUIRED")
        }

        @JvmStatic
        @Throws(Exception::class)
        fun cleanupUnusedWork(context: Context): Long = synchronized(LOCK) {
            if (requiresExclusiveStartup(context) || RestoreStartupProtection.requiresColdRestart(context)) return@synchronized 0L
            var removed = BackupArchive.removeWorkDirectories(context.noBackupFilesDir)
            val root = File(context.noBackupFilesDir, "full-restore")
            if (!root.exists()) return@synchronized removed
            if (Files.isSymbolicLink(root.toPath())) throw IOException("RESTORE_STAGING_LINK_REJECTED")
            val state = readOptional(File(root, "activation.json"))
            if (state != null && PENDING.contains(state.optString("phase"))) return@synchronized 0L
            for (child in root.listFiles() ?: throw IOException("RESTORE_STAGING_UNAVAILABLE")) {
                if (child.name.matches(Regex("restore_[a-f0-9]{36}")) && child.name !in OPEN_PREVIEWS)
                    removed += BackupArchive.removeWorkTree(child)
            }
            if (root.listFiles()?.isEmpty() == true && !root.delete()) throw IOException("RESTORE_STAGING_CLEANUP_REQUIRED")
            removed
        }

        @JvmStatic
        @Throws(Exception::class)
        fun pendingStatus(context: Context): JSONObject {
            val state = readOptional(File(context.noBackupFilesDir, "full-restore/activation.json"))
            return if (state == null) JSONObject().put("phase", "NONE") else activationView(state)
        }

        @JvmStatic
        fun requiresExclusiveStartup(context: Context): Boolean = try {
            val state = readOptional(File(context.noBackupFilesDir, "full-restore/activation.json"))
            if (state == null) false else {
                activationVersion(state)
                PENDING.contains(state.optString("phase"))
            }
        } catch (damaged: Exception) {
            true
        }

        @JvmStatic
        @Throws(Exception::class)
        fun activatePending(context: Context, protection: Protection?): JSONObject {
            val service = BackupRestoreService(context)
            return synchronized(LOCK) {
                var state = readOptional(service.activationFile()) ?: return@synchronized JSONObject().put("phase", "NONE")
                activationVersion(state)
                if (!PENDING.contains(state.optString("phase"))) {
                    if (state.has("request_id")) write(service.receipt(state.getString("request_id")), state)
                    return@synchronized activationView(state)
                }
                if (protection == null) throw IOException("RESTORE_PROTECTION_REQUIRED")
                protection.enterExclusiveStartup().use {
                    val work = service.work(state.getString("restore_id"))
                    val record = read(File(work, "preview.json"))
                    val manifest = read(File(work, "manifest.json"))
                    if (state.getString("preview_hash") != record.getString("preview_hash") ||
                        BackupArchive.sha256(File(work, "manifest.json")) != record.getString("manifest_sha256")) {
                        throw IOException("RESTORE_PREVIEW_CHANGED")
                    }
                    val coverage = mediaCoverage(manifest)
                    if (coverage == "LEGACY_NOT_PROVEN" && !state.optBoolean("legacy_media_coverage_confirmed") ||
                        state.has("media_coverage") && coverage != state.getString("media_coverage")) {
                        throw IOException("RESTORE_MEDIA_COVERAGE_CONFIRMATION_REQUIRED")
                    }
                    val hookManifest = JSONObject(manifest.toString()).put(
                        "_activation_context", JSONObject().put("installation_id", record.getString("installation_id"))
                            .put("expected_restore_epoch", record.getString("expected_restore_epoch"))
                            .put("restore_id", record.getString("restore_id"))
                    )
                    val current = arrayOf(state)
                    val journal = object : BackupDatasetExchange.Journal {
                        override fun read(): JSONObject = current[0]

                        @Throws(Exception::class)
                        override fun save(value: JSONObject) {
                            write(service.activationFile(), value)
                            current[0] = value
                        }
                    }
                    try {
                        if (!state.has("targets")) {
                            service.requireTargetContext(record)
                            service.validateStage(work, record)
                            val active = protection.activePaths()
                            val prepared = service.prepareTargets(work, record, manifest)
                            state.put("targets", BackupDatasetExchange.targets(active.paths, prepared, File(work, "rollback")))
                                .put("phase", "ACTIVATING")
                            journal.save(state)
                        }
                        protection.blockBeforeActivation(
                            state.getString("request_id"), hookManifest
                        )
                        if (state.getString("phase") == "ROLLING_BACK" || state.getString("phase") == "FAILED_HOLD") {
                            BackupDatasetExchange.rollback(journal, BackupDatasetExchange.ATOMIC)
                            state = current[0]
                            write(service.receipt(state.getString("request_id")), state)
                            return@use activationView(state)
                        }
                        if (state.getString("phase") != "SWAPPED") {
                            BackupDatasetExchange.install(journal, BackupDatasetExchange.ATOMIC)
                        }
                        protection.afterActivation(state.getString("request_id"), hookManifest, protection.activePaths().database())
                        state = current[0]
                        state.put("phase", "ACTIVE").put("activated_at", Instant.now().toString())
                        state.remove("targets")
                        write(service.receipt(state.getString("request_id")), state)
                        journal.save(state)
                        OPEN_PREVIEWS.remove(state.getString("restore_id"))
                        runCatching { BackupArchive.removeWorkTree(work) }
                        activationView(state)
                    } catch (failure: Exception) {
                        state = current[0]
                        state.put("last_error_code", safeCode(failure))
                        if (state.has("targets")) {
                            try {
                                BackupDatasetExchange.rollback(journal, BackupDatasetExchange.ATOMIC)
                                state = current[0]
                                state.put("last_error_code", safeCode(failure))
                                write(service.receipt(state.getString("request_id")), state)
                                journal.save(state)
                            } catch (rollbackFailure: Exception) {
                                state = current[0]
                                state.put("phase", "FAILED_HOLD").put("last_error_code", "RESTORE_ROLLBACK_REQUIRES_REVIEW")
                                journal.save(state)
                            }
                        } else {
                            state.put("phase", "FAILED_HOLD")
                            journal.save(state)
                        }
                        throw failure
                    }
                }
            }
        }

        @Throws(Exception::class)
        private fun sanitize(group: String, values: JSONObject, types: JSONObject) {
            for (key in keys(values)) {
                val lower = key.lowercase(Locale.ROOT)
                if (lower.contains("secret") || lower.contains("token") || lower.contains("password") ||
                    lower.contains("credential") || lower.contains("api_key") || lower == "weather_iv") {
                    values.remove(key)
                    types.remove(key)
                }
            }
            if (group == "haus_garten") {
                for (key in arrayOf("drive_account", "drive_folder_id", "drive_tree_uri", "drive_transport")) {
                    values.put(key, "")
                    types.put(key, "string")
                }
                values.put("drive_automatic_enabled", false)
                types.put("drive_automatic_enabled", "boolean")
            }
            if (group == "planning_private") {
                for (key in arrayOf("account", "calendar_id")) {
                    values.put(key, "")
                    types.put(key, "string")
                }
                for (key in arrayOf("weather_enabled", "read_busy", "calendar_sync_enabled")) {
                    values.put(key, false)
                    types.put(key, "boolean")
                }
            }
            if (group == "haus_garten_mail") {
                values.put("account", "")
                types.put("account", "string")
            }
        }

        @Throws(Exception::class)
        private fun writePreferences(file: File, values: JSONObject, types: JSONObject) {
            FileOutputStream(file).use { output ->
                val xml = Xml.newSerializer()
                xml.setOutput(output, "UTF-8")
                xml.startDocument("UTF-8", true)
                xml.startTag(null, "map")
                val names = keys(values)
                Collections.sort(names)
                for (key in names) {
                    val type = types.getString(key)
                    val value = values.get(key)
                    when (type) {
                        "string" -> xml.startTag(null, "string").attribute(null, "name", key)
                            .text(value as String).endTag(null, "string")
                        "string_set" -> {
                            xml.startTag(null, "set").attribute(null, "name", key)
                            val list = value as org.json.JSONArray
                            for (i in 0 until list.length()) {
                                xml.startTag(null, "string").text(list.getString(i)).endTag(null, "string")
                            }
                            xml.endTag(null, "set")
                        }
                        "boolean", "int", "long", "float" -> {
                            val raw = if (type == "float") {
                                java.lang.Float.toString(java.lang.Float.parseFloat(value as String))
                            } else value.toString()
                            xml.startTag(null, type).attribute(null, "name", key).attribute(null, "value", raw)
                                .endTag(null, type)
                        }
                        else -> throw IOException("RESTORE_PREFERENCE_TYPE_UNSUPPORTED")
                    }
                }
                xml.endTag(null, "map")
                xml.endDocument()
                xml.flush()
                output.fd.sync()
            }
        }

        @Throws(Exception::class)
        private fun activationView(state: JSONObject): JSONObject {
            val phase = state.optString("phase")
            return JSONObject().put("restore_id", state.getString("restore_id"))
                .put("phase", if (phase == "REQUESTED") "RESTART_REQUIRED" else phase)
                .put("preview_hash", state.optString("preview_hash")).put("restart_required", PENDING.contains(phase))
                .put("current_state_unchanged", phase in listOf("REQUESTED", "CANCELLED", "ROLLED_BACK"))
                .put("last_error_code", state.optString("last_error_code"))
                .put("media_coverage", state.optString("media_coverage", "LEGACY_NOT_PROVEN"))
                .put("legacy_media_coverage_confirmed", state.optBoolean("legacy_media_coverage_confirmed"))
        }

        @Throws(Exception::class)
        private fun child(root: File, name: String?): File {
            if (name == null || !name.matches(Regex("[A-Za-z0-9_.-]{1,160}")) || name == "." || name == "..") {
                throw IOException("RESTORE_PATH_INVALID")
            }
            val file = File(root, name)
            if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile != root.canonicalFile) {
                throw IOException("RESTORE_PATH_INVALID")
            }
            return file
        }

        @Throws(Exception::class)
        private fun readOptional(file: File): JSONObject? =
            if (file.exists() || File("$file.bak").exists()) read(file) else null

        @Throws(Exception::class)
        private fun read(file: File): JSONObject {
            val atomic = AtomicFile(file)
            return atomic.openRead().use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (read > BackupArchive.MAX_MANIFEST_BYTES - output.size()) {
                            throw IOException("RESTORE_METADATA_TOO_LARGE")
                        }
                        output.write(buffer, 0, read)
                    }
                    JSONObject(String(output.toByteArray(), StandardCharsets.UTF_8))
                }
            }
        }

        @Throws(Exception::class)
        private fun readText(file: File, maximum: Int): String = FileInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(32768)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read > maximum - output.size()) throw IOException("RESTORE_METADATA_TOO_LARGE")
                    output.write(buffer, 0, read)
                }
                String(output.toByteArray(), StandardCharsets.UTF_8)
            }
        }

        @Throws(Exception::class)
        private fun write(file: File, value: JSONObject) {
            val parent = file.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("RESTORE_STAGING_UNAVAILABLE")
            val atomic = AtomicFile(file)
            var output: FileOutputStream? = null
            try {
                val opened = atomic.startWrite()
                output = opened
                opened.write(value.toString().toByteArray(StandardCharsets.UTF_8))
                atomic.finishWrite(opened)
                output = null
            } catch (failure: Exception) {
                if (output != null) atomic.failWrite(output)
                throw failure
            }
        }

        private fun mediaCoverage(manifest: JSONObject): String {
            if (manifest.optString("datasetFormat") == "pixel.snapshot.v2") return "AVAILABLE_FILES_WITH_NOTES"
            return if (manifest.has("browser_media_inventory") && manifest.optInt("browser_media_format_version") == 1) {
                "VERIFIED_NATIVE_MIRROR"
            } else "LEGACY_NOT_PROVEN"
        }

        @Throws(IOException::class)
        private fun request(value: String?) {
            if (value == null || !value.matches(Regex("[A-Za-z0-9_-]{8,200}"))) {
                throw IOException("RESTORE_REQUEST_ID_REQUIRED")
            }
        }

        @Throws(IOException::class)
        private fun hash(value: String?) {
            if (value == null || !value.matches(Regex("[0-9a-f]{64}"))) throw IOException("RESTORE_HASH_REQUIRED")
        }

        @Throws(IOException::class)
        private fun activationVersion(state: JSONObject) {
            if (state.optInt("formatVersion") != 1 || state.optString("phase") !in listOf(
                    "REQUESTED", "ACTIVATING", "SWAPPED", "ROLLING_BACK", "FAILED_HOLD", "ACTIVE", "ROLLED_BACK", "CANCELLED"
                )) {
                throw IOException("RESTORE_ACTIVATION_SCHEMA_UNSUPPORTED")
            }
        }

        private fun keys(value: JSONObject): ArrayList<String> {
            val result = ArrayList<String>()
            val keys = value.keys()
            while (keys.hasNext()) result.add(keys.next())
            return result
        }

        private fun safeCode(failure: Exception): String {
            val value = failure.message
            return if (value != null && value.matches(Regex("(?:RESTORE|BACKUP)_[A-Z0-9_]{1,100}"))) {
                value
            } else "RESTORE_OPERATION_FAILED"
        }
    }
}
