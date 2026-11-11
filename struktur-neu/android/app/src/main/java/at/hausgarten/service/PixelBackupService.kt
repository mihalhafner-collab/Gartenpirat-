package at.hausgarten.service

import android.app.job.JobScheduler
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
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
        return BackupStatusRules.project(records, args, HashSet(RUNNING), Instant.now())
            .put("format_version", 3)
            .put("mode", "PIXEL_PLAINTEXT_ZIP")
            .put("encrypted", false)
            .put("settings_hash", PixelMcpWriteTools.hash(identity))
            .put("requires_google_account", false)
            .put("requires_recovery_key", false)
            .put("target_selected", targetSelection().optString("uri").isNotEmpty())
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
    fun save(request: String?, target: Uri?, progress: Consumer<String>): JSONObject {
        requireDocument(target)
        val selected = target!!
        return saveTo(
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
                    try {
                        context.contentResolver.openFileDescriptor(selected, "r").use { descriptor
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
                        }
                    } catch (failure: IOException) {
                        throw failure
                    } catch (failure: Exception) {
                        throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE", failure)
                    }
                }
            },
            progress,
        )
    }

    // Shared by the Android provider and an isolated native test destination.
    @Throws(Exception::class)
    fun saveTo(
        request: String?,
        destination: String,
        selectedFile: BackupDocumentTransfer.LocalFile,
        progress: Consumer<String>,
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
            val lastRaw = store.ownerValue("pixel_backup_last", "")
            val pending = if (lastRaw.isEmpty()) null else JSONObject(lastRaw)
            if (
                pending != null &&
                    request == pending.optString("requestId") &&
                    pending.optString("installationId") == identity.optString("installationId") &&
                    pending.optString("restoreEpoch") == identity.optString("restoreEpoch")
            ) {
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
                        val old = if (sameTarget) saved?.optJSONObject("archive") else null
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
                if (sameTarget) {
                    val oldArchive = saved.getJSONObject("archive")
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
