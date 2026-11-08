package at.hausgarten.service

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONObject

/** Outside all backup datasets. A restore must advance the epoch before activation. */
class BackupDeviceGuard(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "backup-device-guard/identity.v1"))

    @Throws(Exception::class)
    fun current(): JSONObject =
        synchronized(LOCK) {
            if (!file.baseFile.exists() && !File("${file.baseFile}.bak").exists()) {
                write(
                    JSONObject()
                        .put("formatVersion", 1)
                        .put("installationId", UUID.randomUUID().toString())
                        .put("restoreEpoch", UUID.randomUUID().toString())
                )
            }
            read()
        }

    @Throws(Exception::class)
    fun advanceForRestore(requestId: String?, expectedEpoch: String?): JSONObject {
        if (requestId == null || !requestId.matches(Regex("[A-Za-z0-9_-]{8,200}"))) {
            throw IOException("BACKUP_RESTORE_REQUEST_ID_REQUIRED")
        }
        return synchronized(LOCK) {
            val saved = current()
            if (requestId == saved.optString("lastRestoreRequest")) {
                if (expectedEpoch != saved.optString("lastRestoreExpectedEpoch")) {
                    throw IOException("BACKUP_RESTORE_REPLAY_MISMATCH")
                }
                return@synchronized saved
            }
            if (saved.getString("restoreEpoch") != expectedEpoch)
                throw IOException("BACKUP_DEVICE_EPOCH_CONFLICT")
            saved
                .put("restoreEpoch", UUID.randomUUID().toString())
                .put("lastRestoreRequest", requestId)
                .put("lastRestoreExpectedEpoch", expectedEpoch)
            write(saved)
            read()
        }
    }

    @Throws(Exception::class)
    private fun read(): JSONObject {
        val bytes =
            file.openRead().use { input ->
                ByteArrayOutputStream().use { output ->
                    val block = ByteArray(1024)
                    while (true) {
                        val count = input.read(block)
                        if (count == -1) break
                        if (output.size() + count > 4096)
                            throw IOException("BACKUP_DEVICE_GUARD_INVALID")
                        output.write(block, 0, count)
                    }
                    output.toByteArray()
                }
            }
        val value = JSONObject(String(bytes, StandardCharsets.UTF_8))
        if (
            value.getInt("formatVersion") != 1 ||
                !value.getString("installationId").matches(Regex("[0-9a-f-]{36}")) ||
                !value.getString("restoreEpoch").matches(Regex("[0-9a-f-]{36}"))
        ) {
            throw IOException("BACKUP_DEVICE_GUARD_INVALID")
        }
        return value
    }

    @Throws(Exception::class)
    private fun write(value: JSONObject) {
        val parent = file.baseFile.parentFile!!
        if (!parent.isDirectory && !parent.mkdirs())
            throw IOException("BACKUP_DEVICE_GUARD_UNAVAILABLE")
        var output: FileOutputStream? = null
        try {
            val started = file.startWrite()
            output = started
            started.write(value.toString().toByteArray(StandardCharsets.UTF_8))
            file.finishWrite(started)
            output = null
        } catch (failure: Exception) {
            output?.let { file.failWrite(it) }
            throw failure
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
