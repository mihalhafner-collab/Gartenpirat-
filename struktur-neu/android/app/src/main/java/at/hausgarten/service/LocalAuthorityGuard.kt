package at.hausgarten.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import org.json.JSONObject

/** Device-only writer and number fence, excluded from business archives. */
internal class LocalAuthorityGuard(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "local-authority/authority.v1"))

    @Throws(Exception::class)
    fun current(): JSONObject =
        synchronized(LOCK) {
            if (!file.baseFile.exists() && !File(file.baseFile.toString() + ".bak").exists()) {
                return@synchronized JSONObject()
                    .put("formatVersion", 1)
                    .put("mode", "LEGACY_DRIVE")
                    .put("invoice_authority", "LEGACY_ONLINE")
                    .put("revision", PixelMcpWriteTools.hash("legacy-authority-v1"))
            }
            val value =
                file.openRead().use { input ->
                    ByteArrayOutputStream().use { out ->
                        val block = ByteArray(4096)
                        while (true) {
                            val size = input.read(block)
                            if (size == -1) break
                            if (out.size() + size > 256 * 1024)
                                throw IOException("LOCAL_AUTHORITY_RECORD_INVALID")
                            out.write(block, 0, size)
                        }
                        JSONObject(String(out.toByteArray(), Charsets.UTF_8))
                    }
                }
            if (
                java.lang.String.valueOf(value.opt("formatVersion")) != "1" ||
                    value.getString("mode") !in
                        listOf("LEGACY_DRIVE", "MIGRATING", "LOCAL_ACTIVE", "RESTORE_REVIEW")
            ) {
                throw IOException("LOCAL_AUTHORITY_CONTRACT_UNSUPPORTED")
            }
            value.put("revision", PixelMcpWriteTools.hash(value))
        }

    @Throws(Exception::class)
    fun beginMigration(request: String, expected: String, evidence: JSONObject): JSONObject =
        synchronized(LOCK) {
            val current = current()
            if (request == current.optString("migration_request")) {
                if (
                    PixelMcpWriteTools.hash(evidence) !=
                        current.optString("migration_evidence_sha256")
                )
                    throw IOException("LOCAL_MIGRATION_REPLAY_MISMATCH")
                return@synchronized current
            }
            if (
                current.getString("revision") != expected ||
                    current.getString("mode") !in listOf("LEGACY_DRIVE", "RESTORE_REVIEW")
            )
                throw IOException("LOCAL_MIGRATION_WRITE_CONFLICT")
            LocalAuthorityRules.requireEvidence(evidence)
            val identity = BackupDeviceGuard(context).current()
            val next =
                JSONObject()
                    .put("formatVersion", 1)
                    .put("mode", "MIGRATING")
                    .put("invoice_authority", "LOCKED")
                    .put("migration_request", request)
                    .put("migration_evidence_sha256", PixelMcpWriteTools.hash(evidence))
                    .put("migration_epoch", UUID.randomUUID().toString())
                    .put("installation_id", identity.getString("installationId"))
                    .put("restore_epoch", identity.getString("restoreEpoch"))
                    .put("created_at", java.time.Instant.now().toString())
                    .put("invoice_high_water", current.optInt("invoice_high_water", 0))
                    .put("writer_checked_at", evidence.getString("checked_at"))
                    .put(
                        "evidence_not_before",
                        current.optString(
                            "evidence_not_before",
                            current.optString("blocked_at", ""),
                        ),
                    )
            save(next)
            current()
        }

    @Throws(Exception::class)
    fun finishMigration(request: String, contract: JSONObject): JSONObject =
        synchronized(LOCK) {
            val value = current()
            val identity = BackupDeviceGuard(context).current()
            if (
                request != value.optString("migration_request") ||
                    value.getString("migration_epoch") != contract.getString("migration_epoch") ||
                    identity.getString("installationId") != value.optString("installation_id") ||
                    identity.getString("restoreEpoch") != value.optString("restore_epoch")
            )
                throw IOException("LOCAL_MIGRATION_RECEIPT_MISMATCH")
            if (value.optString("mode") == "LOCAL_ACTIVE") return@synchronized value
            if (
                value.optString("mode") != "MIGRATING" &&
                    !(value.optString("mode") == "RESTORE_REVIEW" &&
                        value.optString("review_reason") == "MIGRATION_INTERRUPTED")
            )
                throw IOException("LOCAL_MIGRATION_REVIEW_REQUIRED")
            value.remove("revision")
            value
                .put("mode", "LOCAL_ACTIVE")
                .put("invoice_authority", "LOCKED")
                .put("contract_sha256", PixelMcpWriteTools.hash(contract))
            save(value)
            current()
        }

    @Throws(Exception::class)
    fun blockAfterRestore(request: String, manifest: JSONObject) =
        synchronized(LOCK) {
            val value = current()
            value.remove("revision")
            value
                .put("formatVersion", 1)
                .put("mode", "RESTORE_REVIEW")
                .put("invoice_authority", "LOCKED")
                .put("restore_request", request)
                .put("restored_backup_id", manifest.getString("backupId"))
                .put("blocked_at", java.time.Instant.now().toString())
            value.put("evidence_not_before", value.getString("blocked_at"))
            var identity = BackupDeviceGuard(context).current()
            if (request != identity.optString("lastRestoreRequest"))
                identity =
                    BackupDeviceGuard(context)
                        .advanceForRestore(request, identity.getString("restoreEpoch"))
            value
                .put("installation_id", identity.getString("installationId"))
                .put("restore_epoch", identity.getString("restoreEpoch"))
            save(value)
        }

    @Throws(Exception::class)
    fun requireInvoiceAuthority() {
        val value = current()
        val identity = BackupDeviceGuard(context).current()
        if (
            value.optString("mode") != "LOCAL_ACTIVE" ||
                value.optString("invoice_authority") != "RECONCILED" ||
                identity.getString("installationId") != value.optString("installation_id") ||
                identity.getString("restoreEpoch") != value.optString("restore_epoch") ||
                restorePending(context)
        )
            throw IOException("INVOICE_AUTHORITY_RECONCILIATION_REQUIRED")
    }

    @Throws(Exception::class)
    fun blockUnverifiedDatabase(request: String) =
        synchronized(LOCK) {
            val value = current()
            if (
                value.optString("mode") == "RESTORE_REVIEW" &&
                    request == value.optString("database_review_request")
            )
                return@synchronized
            value.remove("revision")
            if (value.optString("mode") != "RESTORE_REVIEW") {
                var identity = BackupDeviceGuard(context).current()
                identity =
                    BackupDeviceGuard(context)
                        .advanceForRestore(request, identity.getString("restoreEpoch"))
                value
                    .put("restore_epoch", identity.getString("restoreEpoch"))
                    .put("installation_id", identity.getString("installationId"))
            }
            value
                .put("mode", "RESTORE_REVIEW")
                .put("invoice_authority", "LOCKED")
                .put("review_reason", "DATABASE_CONTRACT_MISMATCH")
                .put("database_review_request", request)
                .put("blocked_at", java.time.Instant.now().toString())
            value.put("evidence_not_before", value.getString("blocked_at"))
            save(value)
        }

    @Throws(Exception::class)
    fun recoverInterruptedMigration() =
        synchronized(LOCK) {
            val value = current()
            if (value.optString("mode") == "MIGRATING") {
                value.remove("revision")
                value
                    .put("mode", "RESTORE_REVIEW")
                    .put("invoice_authority", "LOCKED")
                    .put("review_reason", "MIGRATION_INTERRUPTED")
                save(value)
            }
        }

    @Throws(Exception::class)
    fun reconcileInvoices(request: String, proof: JSONObject, lastNumber: Int) =
        synchronized(LOCK) {
            LocalAuthorityRules.requireEvidence(proof)
            if (lastNumber < 0) throw IOException("INVOICE_NUMBER_STATE_INVALID")
            val value = current()
            if (value.optString("mode") !in listOf("LOCAL_ACTIVE", "RESTORE_REVIEW"))
                throw IOException("LOCAL_AUTHORITY_REQUIRED")
            value.remove("revision")
            val identity = BackupDeviceGuard(context).current()
            value
                .put("mode", "LOCAL_ACTIVE")
                .put("invoice_authority", "RECONCILED")
                .put("invoice_evidence_sha256", PixelMcpWriteTools.hash(proof))
                .put("invoice_reconciliation_request", request)
                .put("invoice_high_water", maxOf(lastNumber, value.optInt("invoice_high_water")))
                .put("installation_id", identity.getString("installationId"))
                .put("restore_epoch", identity.getString("restoreEpoch"))
            save(value)
        }

    @Throws(Exception::class)
    fun advanceInvoiceHighWater(number: Int) =
        synchronized(LOCK) {
            requireInvoiceAuthority()
            val value = current()
            value.remove("revision")
            if (number < value.optInt("invoice_high_water"))
                throw IOException("INVOICE_NUMBER_ROLLBACK_DETECTED")
            value.put("invoice_high_water", number)
            save(value)
        }

    private fun save(value: JSONObject) {
        value.remove("revision")
        write(file, value)
    }

    companion object {
        private val LOCK = Any()
        private val EXCLUSIVE = ThreadLocal.withInitial { false }

        @JvmStatic
        fun legacySyncAllowed(context: Context): Boolean =
            try {
                LocalAuthorityGuard(context).current().getString("mode") == "LEGACY_DRIVE" &&
                    !restorePending(context)
            } catch (_: Exception) {
                false
            }

        @JvmStatic
        fun local(context: Context): Boolean =
            try {
                LocalAuthorityGuard(context).current().getString("mode") != "LEGACY_DRIVE"
            } catch (_: Exception) {
                true
            }

        @JvmStatic
        fun assertStoreMayOpen(context: Context) {
            if (EXCLUSIVE.get() == true) return
            RevierApplication.awaitStartup(context)
            if (restorePending(context)) throw IllegalStateException("RESTORE_RESTART_REQUIRED")
            try {
                if (LocalAuthorityGuard(context).current().getString("mode") == "MIGRATING")
                    throw IllegalStateException("LOCAL_MIGRATION_REVIEW_REQUIRED")
            } catch (error: IllegalStateException) {
                throw error
            } catch (invalid: Exception) {
                throw IllegalStateException("LOCAL_AUTHORITY_RECORD_INVALID", invalid)
            }
        }

        @JvmStatic
        fun restorePending(context: Context): Boolean =
            File(context.noBackupFilesDir, "local-authority/restore-pending.v1").isFile ||
                BackupRestoreService.requiresExclusiveStartup(context) ||
                RestoreStartupProtection.requiresColdRestart(context)

        @JvmStatic
        fun exclusiveStartup(): AutoCloseable {
            if (EXCLUSIVE.get() == true) throw IllegalStateException("RESTORE_STARTUP_REENTRY")
            EXCLUSIVE.set(true)
            return AutoCloseable { EXCLUSIVE.remove() }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun markRestorePending(context: Context, request: String?) {
            if (request == null || !request.matches(Regex("[A-Za-z0-9_-]{8,200}")))
                throw IOException("RESTORE_REQUEST_ID_REQUIRED")
            write(
                AtomicFile(File(context.noBackupFilesDir, "local-authority/restore-pending.v1")),
                JSONObject().put("request_id", request),
            )
        }

        @JvmStatic
        fun clearRestorePending(context: Context) {
            AtomicFile(File(context.noBackupFilesDir, "local-authority/restore-pending.v1"))
                .delete()
        }

        @JvmStatic
        @Throws(Exception::class)
        fun verifyDatabaseContract(context: Context, database: SQLiteDatabase) {
            val contract =
                database
                    .rawQuery(
                        "SELECT value FROM owner_state WHERE key='local_authority_contract'",
                        null,
                    )
                    .use { row -> if (row.moveToFirst()) row.getString(0) else "" }
            val guard = LocalAuthorityGuard(context)
            val state = guard.current()
            if (contract.isNotEmpty()) {
                val value = JSONObject(contract)
                if (java.lang.String.valueOf(value.opt("formatVersion")) != "1")
                    throw IOException("LOCAL_DATABASE_CONTRACT_UNSUPPORTED")
                if (
                    value.optString("migration_epoch") != state.optString("migration_epoch") ||
                        state.optString("mode") == "LEGACY_DRIVE"
                )
                    guard.blockUnverifiedDatabase(
                        "database-contract-" + PixelMcpWriteTools.hash(contract).substring(0, 32)
                    )
            } else if (state.optString("mode") == "LOCAL_ACTIVE")
                guard.blockUnverifiedDatabase("database-contract-missing")
        }

        private fun write(file: AtomicFile, value: JSONObject) {
            val directory = file.baseFile.parentFile!!
            if (!directory.isDirectory && !directory.mkdirs())
                throw IOException("LOCAL_AUTHORITY_STORAGE_UNAVAILABLE")
            var out: FileOutputStream? = null
            try {
                out = file.startWrite()
                out.write(value.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
                out = null
            } catch (error: Exception) {
                if (out != null) file.failWrite(out)
                throw error
            }
        }
    }
}
