package at.hausgarten.service

import android.app.job.JobScheduler
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import org.json.JSONArray
import org.json.JSONObject

/** Complete staged snapshot -> safe target replacement -> document-provider byte readback. */
class PixelBackupService
internal constructor(private val context: Context, private val store: PixelLocalStore) {
    companion object {
        private val RUNNING = ConcurrentHashMap.newKeySet<String>()
        private val SAVE_LOCK = Any()

        @JvmStatic
        fun cancelRetiredJobs(context: Context) {
            try {
                val jobs = context.getSystemService(JobScheduler::class.java)
                jobs?.allPendingJobs?.forEach { job ->
                    if (job.service.className.endsWith(".FullBackupJob")) jobs.cancel(job.id)
                }
            } catch (_: RuntimeException) {
                // No retired service remains in the manifest.
            }
        }

        /** Only this dedicated work directory is disposable; never a chosen destination. */
        @JvmStatic
        fun cleanupWork(context: Context): Long =
            synchronized(SAVE_LOCK) {
                if (RUNNING.isNotEmpty()) return@synchronized 0L
                cleanupWorkLocked(context)
            }

        private fun cleanupWorkLocked(context: Context): Long {
            return BackupRestoreService.cleanupUnusedWork(context)
        }

        /** The explicit Android picker grants access to the selected local backup document. */
        @JvmStatic
        @Throws(IOException::class)
        fun requireDocument(uri: Uri?) {
            if (uri == null || uri.scheme != "content" || uri.authority.isNullOrEmpty()) {
                throw IOException("BACKUP_DOCUMENT_REQUIRED")
            }
        }
    }

    @Throws(Exception::class)
    fun status(args: JSONObject): JSONObject {
        val records = JSONArray()
        val ids = HashSet<String>()
        val identity = BackupDeviceGuard(context).current()
        for (key in arrayOf("pixel_backup_last", "pixel_backup_success")) {
            val raw = store.ownerValue(key, "")
            if (raw.isEmpty()) continue
            val job = JSONObject(raw)
            if (ids.add(job.getString("backupId"))) {
                records.put(
                    JSONObject()
                        .put("job", job.put("revision", PixelMcpWriteTools.hash(job)))
                        .put(
                            "sameInstallation",
                            identity.getString("installationId") == job.optString("installationId"),
                        )
                        .put(
                            "sameRestoreEpoch",
                            identity.getString("restoreEpoch") == job.optString("restoreEpoch"),
                        )
                )
            }
        }
        val selected = targetSelection().optString("uri")
        val accessError =
            if (selected.isEmpty()) ""
            else
                try {
                    requireSelectedFileAccess(Uri.parse(selected))
                    ""
                } catch (failure: IOException) {
                    BackupScheduleRules.errorCode(failure)
                }
        val result = BackupStatusRules.project(records, args, HashSet(RUNNING), Instant.now())
        if (accessError.isNotEmpty() && !args.has("backup_id")) result.put("state", "FAILED")
        return result
            .put("format_version", 3)
            .put("mode", "PIXEL_PLAINTEXT_ZIP")
            .put("encrypted", false)
            .put("settings_hash", PixelMcpWriteTools.hash(identity))
            .put("requires_google_account", false)
            .put("requires_recovery_key", false)
            .put("target_selected", selected.isNotEmpty())
            .put("current_access_error", if (args.has("backup_id")) "" else accessError)
            .put(
                "all_files_access_required",
                Build.VERSION.SDK_INT >= 31 && !Environment.isExternalStorageManager(),
            )
            .put(
                "document_permission_required",
                accessError == "BACKUP_DOCUMENT_PERMISSION_REQUIRED",
            )
            .put("cloud_confirmed", false)
    }

    @Throws(Exception::class)
    fun request(args: JSONObject): JSONObject =
        filePicker(
            if (targetSelection().optString("uri").isEmpty()) "open_backup_screen_and_choose_file"
            else "open_backup_screen_and_update_selected_file"
        )

    @Throws(Exception::class)
    fun retry(args: JSONObject): JSONObject = filePicker("resume_existing_save_in_backup_screen")

    private fun filePicker(action: String): JSONObject =
        JSONObject()
            .put("accepted_locally", false)
            .put("code", "BACKUP_OPEN_DOCUMENT_PICKER")
            .put("next_action", action)
            .put("target_selected", targetSelection().optString("uri").isNotEmpty())
            .put("safe_replacement_required", true)
            .put("cloud_confirmed", false)

    /** A restored URI or a different installation never silently inherits the old grant. */
    fun targetSelection(): JSONObject {
        val identity = BackupDeviceGuard(context).current()
        for (key in listOf("pixel_backup_last", "pixel_backup_success")) {
            val raw = store.ownerValue(key, "")
            if (raw.isEmpty()) continue
            val previous = JSONObject(raw)
            if (
                previous.optString("installationId") != identity.optString("installationId") ||
                    previous.optString("restoreEpoch") != identity.optString("restoreEpoch")
            )
                continue
            val uri = previous.optJSONObject("destination")?.optString("treeUri").orEmpty()
            if (uri.isNotEmpty())
                return JSONObject()
                    .put("uri", uri)
                    .put(
                        "request_id",
                        if (previous.optString("phase") == "PROVIDER_VERIFIED") ""
                        else previous.optString("requestId"),
                    )
        }
        return JSONObject()
    }

    @Throws(Exception::class)
    fun save(request: String?, target: Uri?, progress: Consumer<String>): JSONObject =
        saveSelected(request, target, null, progress)

    @Throws(Exception::class)
    fun saveWithApproval(
        request: String?,
        target: Uri?,
        approval: JSONObject,
        progress: Consumer<String>,
    ): JSONObject = saveSelected(request, target, approval, progress)

    fun hasVerifiedTarget(target: Uri): Boolean {
        val raw = store.ownerValue("pixel_backup_success", "")
        if (raw.isEmpty()) return false
        val previous = JSONObject(raw)
        val identity = BackupDeviceGuard(context).current()
        return previous.optJSONObject("destination")?.optString("treeUri") == target.toString() &&
            previous.optString("installationId") == identity.getString("installationId") &&
            previous.optString("restoreEpoch") == identity.getString("restoreEpoch")
    }

    /** Verify an existing file without importing its business or installation identity. */
    @Throws(Exception::class)
    fun inspectExistingTarget(target: Uri): JSONObject =
        synchronized(SAVE_LOCK) {
            requireDocument(target)
            requireSelectedFileAccess(target)
            if (Build.VERSION.SDK_INT < 31) throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
            if (RUNNING.isNotEmpty()) throw IOException("BACKUP_RUNNER_BUSY")
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val restore = BackupRestoreService(context)
            var previewId = ""
            try {
                val preview =
                    restore.previewFrom("backup_target_" + UUID.randomUUID(), target.toString()) {
                        val input =
                            context.contentResolver.openInputStream(target)
                                ?: throw IOException("BACKUP_LOCAL_READBACK_FAILED")
                        object : InputStream() {
                            override fun read(): Int {
                                val value = input.read()
                                if (value >= 0) {
                                    digest.update(value.toByte())
                                    size++
                                }
                                return value
                            }

                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                val count = input.read(buffer, offset, length)
                                if (count > 0) {
                                    digest.update(buffer, offset, count)
                                    size += count
                                }
                                return count
                            }

                            override fun close() = input.close()
                        }
                    }
                previewId = preview.getString("restore_id")
                val summary = preview.getJSONObject("summary")
                val identity = BackupDeviceGuard(context).current()
                JSONObject()
                    .put("destination", target.toString())
                    .put("archive_sha256", BackupArchive.hex(digest.digest()))
                    .put("archive_size", size)
                    .put("installation_id", identity.getString("installationId"))
                    .put("restore_epoch", identity.getString("restoreEpoch"))
                    .put("captured_at", summary.getString("captured_at"))
                    .put("counts", summary.getJSONObject("counts"))
                    .put("current_counts", preview.getJSONObject("current_counts"))
            } finally {
                if (previewId.isNotEmpty()) BackupRestoreService.discardPreview(context, previewId)
            }
        }

    private fun saveSelected(
        request: String?,
        target: Uri?,
        approval: JSONObject?,
        progress: Consumer<String>,
    ): JSONObject {
        requireDocument(target)
        val selected = target!!
        requireSelectedFileAccess(selected)
        return saveToApproved(
            request,
            selected.toString(),
            object : BackupDocumentTransfer.LocalFile {
                override fun write(): OutputStream? =
                    context.contentResolver.openOutputStream(selected, "wt")

                override fun read(): InputStream? =
                    context.contentResolver.openInputStream(selected)

                override fun replaceFrom(archive: File, previousHash: String, previousSize: Long) {
                    // SAF has no general atomic-replace contract. A path-stable local provider
                    // may be used only when its granted descriptor names the same local inode
                    // and the parent permits a same-filesystem atomic rename. No wt fallback.
                    if (
                        selected.authority != "com.android.externalstorage.documents" ||
                            !DocumentsContract.isDocumentUri(context, selected)
                    )
                        throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
                    if (Build.VERSION.SDK_INT < 31)
                        throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
                    requireSelectedFileAccess(selected)
                    try {
                        // "r" may return a MediaStore LowerFS descriptor. "rw" opens the
                        // selected existing file directly and never requests truncation.
                        context.contentResolver.openFileDescriptor(selected, "rw").use { descriptor
                            ->
                            if (descriptor == null)
                                throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
                            val file = File(Os.readlink("/proc/self/fd/${descriptor.fd}"))
                            val relative =
                                DocumentsContract.getDocumentId(selected).substringAfter(':', "")
                            if (relative.isEmpty() || !file.path.endsWith("/$relative"))
                                throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
                            val granted = Os.fstat(descriptor.fileDescriptor)
                            val local = Os.stat(file.path)
                            if (granted.st_dev != local.st_dev || granted.st_ino != local.st_ino)
                                throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
                            BackupDocumentTransfer.atomicReplace(
                                file,
                                archive,
                                previousHash,
                                previousSize,
                            )
                            // Raw access bypasses MediaStore bookkeeping. Scan only this chosen
                            // file after the exchange, never a directory or another user file.
                            try {
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(file.path),
                                    arrayOf("application/zip"),
                                    null,
                                )
                            } catch (_: RuntimeException) {
                                // Index maintenance cannot undo or reclassify a committed move.
                                // The subsequent complete archive readback confirms this save.
                            }
                        }
                    } catch (failure: IOException) {
                        throw failure
                    } catch (failure: Exception) {
                        throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE", failure)
                    }
                }
            },
            Consumer { message ->
                progress.accept(message)
                requireSelectedFileAccess(selected)
            },
            approval,
        )
    }

    private fun requireSelectedFileAccess(selected: Uri) {
        if (selected.authority != "com.android.externalstorage.documents") return
        val granted =
            context.contentResolver.persistedUriPermissions.any {
                it.uri == selected && it.isReadPermission && it.isWritePermission
            }
        if (!granted) throw IOException("BACKUP_DOCUMENT_PERMISSION_REQUIRED")
        if (Build.VERSION.SDK_INT >= 31 && !Environment.isExternalStorageManager())
            throw IOException("BACKUP_ALL_FILES_ACCESS_REQUIRED")
    }

    // Shared by the Android provider and an isolated native test destination.
    @Throws(Exception::class)
    fun saveTo(
        request: String?,
        destination: String,
        selectedFile: BackupDocumentTransfer.LocalFile,
        progress: Consumer<String>,
    ): JSONObject = saveToApproved(request, destination, selectedFile, progress, null)

    private fun saveToApproved(
        request: String?,
        destination: String,
        selectedFile: BackupDocumentTransfer.LocalFile,
        progress: Consumer<String>,
        suppliedApproval: JSONObject?,
    ): JSONObject {
        if (request == null || !request.matches(Regex("[A-Za-z0-9_-]{8,200}"))) {
            throw IOException("BACKUP_REQUEST_ID_REQUIRED")
        }
        val id = "pixel_" + PixelMcpWriteTools.hash(request).substring(0, 32)
        synchronized(SAVE_LOCK) {
            if (RUNNING.isNotEmpty()) throw IOException("BACKUP_RUNNER_BUSY")
            cleanupWorkLocked(context)
            RUNNING.add(id)
        }
        var record: JSONObject? = null
        try {
            val identity = BackupDeviceGuard(context).current()
            val previous = store.ownerValue("pixel_backup_success", "")
            val saved = if (previous.isEmpty()) null else JSONObject(previous)
            val sameTarget =
                saved != null &&
                    destination == saved.getJSONObject("destination").optString("treeUri") &&
                    saved.optString("installationId") == identity.optString("installationId") &&
                    saved.optString("restoreEpoch") == identity.optString("restoreEpoch")
            if (saved != null && request == saved.optString("requestId") && !sameTarget)
                throw IOException("BACKUP_REQUEST_ID_CONFLICT")
            val lastRaw = store.ownerValue("pixel_backup_last", "")
            val pending = if (lastRaw.isEmpty()) null else JSONObject(lastRaw)
            val pendingMatches =
                pending != null &&
                    request == pending.optString("requestId") &&
                    pending.optString("installationId") == identity.optString("installationId") &&
                    pending.optString("restoreEpoch") == identity.optString("restoreEpoch")
            if (
                pending != null &&
                    pendingMatches &&
                    pending.optJSONObject("destination")?.optString("treeUri") != destination
            )
                throw IOException("BACKUP_REQUEST_ID_CONFLICT")
            val recordedApproval =
                when {
                    saved != null && sameTarget && request == saved.optString("requestId") ->
                        saved.optJSONObject("approved_existing_target")
                    pendingMatches ->
                        checkNotNull(pending).optJSONObject("approved_existing_target")
                    else -> null
                }
            val approval = suppliedApproval ?: recordedApproval
            if (
                suppliedApproval != null &&
                    recordedApproval == null &&
                    (pendingMatches || saved != null && request == saved.optString("requestId"))
            )
                throw IOException("BACKUP_REQUEST_ID_CONFLICT")
            if (approval != null) {
                if (
                    approval.optString("destination") != destination ||
                        approval.optString("installation_id") !=
                            identity.getString("installationId") ||
                        approval.optString("restore_epoch") != identity.getString("restoreEpoch") ||
                        !approval.optString("archive_sha256").matches(Regex("[0-9a-f]{64}")) ||
                        approval.optLong("archive_size") <= 0
                )
                    throw IOException("BACKUP_TARGET_CHANGED")
                if (
                    suppliedApproval != null &&
                        recordedApproval != null &&
                        PixelMcpWriteTools.hash(suppliedApproval) !=
                            PixelMcpWriteTools.hash(recordedApproval)
                )
                    throw IOException("BACKUP_REQUEST_ID_CONFLICT")
            }
            if (saved != null) {
                if (sameTarget && request == saved.optString("requestId")) {
                    val repeated = JSONObject(saved.toString())
                    record = repeated
                    repeated.put("lastLocalAttemptAt", Instant.now().toString())
                    val archive = saved.getJSONObject("archive")
                    selectedFile.read().use { input ->
                        BackupDocumentTransfer.verifyReadback(
                            input,
                            archive.getString("sha256"),
                            archive.getLong("size"),
                        )
                    }
                    repeated.remove("lastErrorCode")
                    repeated.put("phase", "PROVIDER_VERIFIED")
                    store.putOwnerValue("pixel_backup_last", repeated.toString())
                    return result(repeated)
                }
            }
            if (pending != null && pendingMatches) {
                if (pending.optJSONObject("destination")?.optString("treeUri") != destination)
                    throw IOException("BACKUP_REQUEST_ID_CONFLICT")
                val prepared = pending.optJSONObject("archive")
                if (prepared != null) {
                    record = pending
                    val completed =
                        try {
                            selectedFile.read().use {
                                BackupDocumentTransfer.verifyReadback(
                                    it,
                                    prepared.getString("sha256"),
                                    prepared.getLong("size"),
                                )
                            }
                            true
                        } catch (_: IOException) {
                            false
                        }
                    if (completed) return confirm(pending)
                    // If the atomic replacement never happened, only the previously verified
                    // old target (or a still empty first target) may be retried.
                    try {
                        val old =
                            if (approval != null)
                                JSONObject()
                                    .put("sha256", approval.getString("archive_sha256"))
                                    .put("size", approval.getLong("archive_size"))
                            else if (sameTarget) saved?.optJSONObject("archive") else null
                        selectedFile.read().use { input ->
                            if (old != null)
                                BackupDocumentTransfer.verifyReadback(
                                    input,
                                    old.getString("sha256"),
                                    old.getLong("size"),
                                )
                            else if (input == null || input.read() != -1)
                                throw IOException("BACKUP_TARGET_CHANGED")
                        }
                    } catch (failure: Exception) {
                        throw IOException("BACKUP_TARGET_CHANGED", failure)
                    }
                }
            }
            val now = Instant.now().toString()
            val current =
                JSONObject()
                    .put("backupId", id)
                    .put("requestId", request)
                    .put("createdAt", now)
                    .put("lastLocalAttemptAt", now)
                    .put("installationId", identity.getString("installationId"))
                    .put("restoreEpoch", identity.getString("restoreEpoch"))
                    .put(
                        "destination",
                        JSONObject()
                            .put("purpose", "pixel_plain_backup")
                            .put("treeUri", destination),
                    )
                    .put("phase", "CAPTURING")
            if (approval != null)
                current.put("approved_existing_target", JSONObject(approval.toString()))
            record = current
            store.putOwnerValue("pixel_backup_last", current.toString())
            val parent = File(context.noBackupFilesDir, "pixel-backup")
            if (Files.isSymbolicLink(parent.toPath()) || !parent.isDirectory && !parent.mkdirs()) {
                throw IOException("BACKUP_WORK_STORAGE_UNAVAILABLE")
            }
            val attempt = Files.createTempDirectory(parent.toPath(), "attempt-").toFile()
            val captureIdentity =
                JSONObject(identity.toString())
                    .put("backupId", id)
                    .put("businessId", context.packageName)
            synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
                progress.accept("Datenbank und Einstellungen aufnehmen …")
                val captured =
                    LocalBackupDataset.forStreaming(
                        context,
                        store,
                        File(attempt, "snapshot"),
                        captureIdentity,
                    )
                progress.accept("Originaldateien sichern und ZIP vollständig prüfen …")
                val staged = File(attempt, "backup.zip")
                FileOutputStream(staged).use { output ->
                    // writePlain closes the ZIP stream. fsync uses a separate descriptor.
                    BackupArchive.writePlain(
                        BackupStorageBudget.guard(output, attempt),
                        captured.metadata,
                        captured.sources(),
                    )
                }
                FileOutputStream(staged, true).use { it.fd.sync() }
                BackupArchive.verifyWritten(staged, PixelLocalStore.SCHEMA_VERSION)
                val sha = BackupArchive.sha256(staged)
                current
                    .put("phase", "PROVIDER_WRITING")
                    .put("warning_count", captured.metadata.optInt("warning_count"))
                    .put("counts", captured.metadata.getJSONObject("counts"))
                    .put("warnings", captured.notes.getJSONArray("warnings"))
                    .put(
                        "archive",
                        JSONObject()
                            .put("archiveFormat", "PLAIN_ZIP")
                            .put("sha256", sha)
                            .put("size", staged.length())
                            .put("capturedAt", captured.metadata.getString("capturedAt"))
                            .put("localVerifiedAt", Instant.now().toString())
                            .put("mediaCoverage", "AVAILABLE_FILES_WITH_NOTES"),
                    )
                // Keep enough evidence before changing the target to recognize a completed
                // replacement after a crash/readback error under the same request_id.
                store.putOwnerValue("pixel_backup_last", current.toString())
                progress.accept("Gewählte Sicherungsdatei aktualisieren und zurücklesen …")
                if (sameTarget || approval != null) {
                    val oldArchive =
                        if (approval != null)
                            JSONObject()
                                .put("sha256", approval.getString("archive_sha256"))
                                .put("size", approval.getLong("archive_size"))
                        else checkNotNull(saved).getJSONObject("archive")
                    BackupDocumentTransfer.updateLocal(
                        staged,
                        sha,
                        oldArchive.getString("sha256"),
                        oldArchive.getLong("size"),
                        selectedFile,
                    )
                } else {
                    BackupDocumentTransfer.exportLocal(staged, sha, false, selectedFile)
                }
            }
            return confirm(current)
        } catch (failure: Exception) {
            record?.let { current ->
                try {
                    current
                        .put("phase", "FAILED")
                        .put("lastErrorCode", BackupScheduleRules.errorCode(failure))
                        .put("lastAttemptAt", Instant.now().toString())
                    store.putOwnerValue("pixel_backup_last", current.toString())
                } catch (statusFailure: Exception) {
                    failure.addSuppressed(statusFailure)
                }
            }
            throw failure
        } finally {
            synchronized(SAVE_LOCK) {
                try {
                    cleanupWorkLocked(context)
                } catch (_: IOException) {
                    /* Startup and the storage action retry orphan cleanup. */
                } finally {
                    RUNNING.remove(id)
                }
            }
        }
    }

    private fun confirm(record: JSONObject): JSONObject {
        val archive = record.getJSONObject("archive")
        record.remove("lastErrorCode")
        record
            .put("phase", "PROVIDER_VERIFIED")
            .put("lastLocalAttemptAt", Instant.now().toString())
            .put(
                "provider",
                JSONObject()
                    .put("backup_id", record.getString("backupId"))
                    .put("archive_sha256", archive.getString("sha256"))
                    .put("byte_size", archive.getLong("size"))
                    .put("verified_at", Instant.now().toString())
                    .put("verification_scope", "DOCUMENT_PROVIDER_READBACK")
                    .put("destination_location", "DOCUMENT_PROVIDER")
                    .put("external_storage_verified", false),
            )
        store.putOwnerValue("pixel_backup_success", record.toString())
        store.putOwnerValue("pixel_backup_last", record.toString())
        return result(record)
    }

    private fun result(record: JSONObject): JSONObject =
        JSONObject()
            .put("backup_id", record.getString("backupId"))
            .put("saved", true)
            .put("verified", true)
            .put("encrypted", false)
            .put("byte_size", record.getJSONObject("archive").getLong("size"))
            .put("warning_count", record.optInt("warning_count"))
            .put("warnings", record.optJSONArray("warnings") ?: JSONArray())
            .put("counts", record.optJSONObject("counts") ?: JSONObject())
            .put("captured_at", record.getJSONObject("archive").optString("capturedAt"))
            .put("verified_at", record.getJSONObject("provider").optString("verified_at"))
            .put("cloud_confirmed", false)
}
