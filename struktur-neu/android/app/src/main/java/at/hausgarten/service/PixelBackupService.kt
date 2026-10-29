package at.hausgarten.service

import android.app.job.JobScheduler
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/** Direct snapshot -> plaintext ZIP -> selected document provider -> byte readback. */
class PixelBackupService internal constructor(private val context: Context, private val store: PixelLocalStore) {
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

        /** The explicit Android picker grants access to this document, including Drive documents. */
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
                    JSONObject().put("job", job.put("revision", PixelMcpWriteTools.hash(job)))
                        .put("sameInstallation", identity.getString("installationId") == job.optString("installationId"))
                        .put("sameRestoreEpoch", identity.getString("restoreEpoch") == job.optString("restoreEpoch"))
                )
            }
        }
        return BackupStatusRules.project(records, args, HashSet(RUNNING), Instant.now())
            .put("format_version", 3).put("mode", "PIXEL_PLAINTEXT_ZIP").put("encrypted", false)
            .put("settings_hash", PixelMcpWriteTools.hash(identity)).put("requires_google_account", false)
            .put("requires_recovery_key", false).put("cloud_confirmed", false)
    }

    @Throws(Exception::class)
    fun request(args: JSONObject): JSONObject = filePicker("open_backup_screen_and_choose_file")

    @Throws(Exception::class)
    fun retry(args: JSONObject): JSONObject = filePicker("resume_existing_save_in_backup_screen")

    private fun filePicker(action: String): JSONObject = JSONObject()
        .put("accepted_locally", false).put("code", "BACKUP_OPEN_DOCUMENT_PICKER")
        .put("next_action", action).put("cloud_confirmed", false)

    @Throws(Exception::class)
    fun save(request: String?, target: Uri?, progress: Consumer<String>): JSONObject {
        requireDocument(target)
        val selected = target!!
        return saveTo(request, selected.toString(), object : BackupDocumentTransfer.LocalFile {
            override fun write(): OutputStream? = context.contentResolver.openOutputStream(selected, "wt")
            override fun read(): InputStream? = context.contentResolver.openInputStream(selected)
        }, progress)
    }

    // Shared by the Android provider and an isolated native test destination.
    @Throws(Exception::class)
    fun saveTo(
        request: String?,
        destination: String,
        selectedFile: BackupDocumentTransfer.LocalFile,
        progress: Consumer<String>
    ): JSONObject {
        if (request == null || !request.matches(Regex("[A-Za-z0-9_-]{8,200}"))) {
            throw IOException("BACKUP_REQUEST_ID_REQUIRED")
        }
        val id = "pixel_" + PixelMcpWriteTools.hash(request).substring(0, 32)
        synchronized(SAVE_LOCK) {
            if (RUNNING.isNotEmpty()) throw IOException("BACKUP_RUNNER_BUSY")
            RUNNING.add(id)
        }
        var work: File? = null
        var record: JSONObject? = null
        try {
            val identity = BackupDeviceGuard(context).current()
            val previous = store.ownerValue("pixel_backup_success", "")
            if (previous.isNotEmpty()) {
                val saved = JSONObject(previous)
                if (destination == saved.getJSONObject("destination").optString("treeUri")) {
                    if (request != saved.optString("requestId")) throw IOException("BACKUP_TARGET_ALREADY_VERIFIED")
                    val repeated = JSONObject(saved.toString())
                    record = repeated
                    repeated.put("lastLocalAttemptAt", Instant.now().toString())
                    val archive = saved.getJSONObject("archive")
                    selectedFile.read().use { input ->
                        BackupDocumentTransfer.verifyReadback(input, archive.getString("sha256"), archive.getLong("size"))
                    }
                    repeated.remove("lastErrorCode")
                    repeated.put("phase", "PROVIDER_VERIFIED")
                    store.putOwnerValue("pixel_backup_last", repeated.toString())
                    return result(repeated)
                }
            }
            val now = Instant.now().toString()
            val current = JSONObject().put("backupId", id).put("requestId", request)
                .put("createdAt", now).put("lastLocalAttemptAt", now)
                .put("installationId", identity.getString("installationId"))
                .put("restoreEpoch", identity.getString("restoreEpoch"))
                .put("destination", JSONObject().put("purpose", "pixel_plain_backup").put("treeUri", destination))
                .put("phase", "CAPTURING")
            record = current
            store.putOwnerValue("pixel_backup_last", current.toString())
            val parent = File(context.noBackupFilesDir, "pixel-backup")
            if (Files.isSymbolicLink(parent.toPath()) || !parent.isDirectory && !parent.mkdirs()) {
                throw IOException("BACKUP_WORK_STORAGE_UNAVAILABLE")
            }
            val attempt = Files.createTempDirectory(parent.toPath(), "attempt-").toFile()
            work = attempt
            progress.accept("Vorhandenen Bestand aufnehmen …")
            val captureIdentity = JSONObject(identity.toString()).put("backupId", id)
                .put("businessId", context.packageName)
            val captured = LocalBackupDataset.capture(context, store, File(attempt, "capture"), captureIdentity, null)
            val zip = File(attempt, "backup.zip")
            progress.accept("ZIP-Datei erstellen und prüfen …")
            FileOutputStream(zip).use { output ->
                val guarded = object : FilterOutputStream(BackupStorageBudget.guard(output, attempt)) {
                    override fun close() = flush()
                }
                EncryptedBackupArchive.writePlain(guarded, captured.metadata, captured.sources())
                output.fd.sync()
            }
            EncryptedBackupArchive.verifyPlain(
                FileInputStream(zip), File(attempt, "verify"),
                attempt.usableSpace - BackupStorageBudget.BUSINESS_RESERVE_BYTES, PixelLocalStore.SCHEMA_VERSION
            ).use { checked ->
                LocalBackupDataset.inspectStaged(File(checked.directory, "data"), checked.manifest)
            }
            val sha = EncryptedBackupArchive.sha256(zip)
            progress.accept("ZIP-Datei speichern und vollständig zurücklesen …")
            BackupDocumentTransfer.exportLocal(zip, sha, false, selectedFile)
            val verifiedAt = Instant.now().toString()
            current.put("phase", "PROVIDER_VERIFIED").put("warning_count", captured.metadata.optInt("warning_count"))
                .put("archive", JSONObject().put("archiveFormat", "PLAIN_ZIP").put("sha256", sha).put("size", zip.length())
                    .put("capturedAt", captured.metadata.getString("capturedAt")).put("localVerifiedAt", verifiedAt)
                    .put("mediaCoverage", "AVAILABLE_FILES_WITH_NOTES"))
                .put("provider", JSONObject().put("backup_id", id).put("archive_sha256", sha)
                    .put("byte_size", zip.length()).put("verified_at", verifiedAt)
                    .put("verification_scope", "DOCUMENT_PROVIDER_READBACK")
                    .put("destination_location", "DOCUMENT_PROVIDER").put("external_storage_verified", false))
            store.putOwnerValue("pixel_backup_success", current.toString())
            store.putOwnerValue("pixel_backup_last", current.toString())
            return result(current)
        } catch (failure: Exception) {
            record?.let { current ->
                try {
                    current.put("phase", "FAILED").put("lastErrorCode", BackupScheduleRules.errorCode(failure))
                        .put("lastAttemptAt", Instant.now().toString())
                    store.putOwnerValue("pixel_backup_last", current.toString())
                } catch (statusFailure: Exception) {
                    failure.addSuppressed(statusFailure)
                }
            }
            throw failure
        } finally {
            RUNNING.remove(id)
            work?.let { attempt ->
                try {
                    EncryptedBackupArchive.removeOwned(attempt)
                } catch (_: IOException) {
                    // Only this attempt is disposable; saved user files stay untouched.
                }
            }
        }
    }

    private fun result(record: JSONObject): JSONObject = JSONObject()
        .put("backup_id", record.getString("backupId")).put("saved", true).put("verified", true)
        .put("encrypted", false).put("byte_size", record.getJSONObject("archive").getLong("size"))
        .put("warning_count", record.optInt("warning_count")).put("cloud_confirmed", false)
}
