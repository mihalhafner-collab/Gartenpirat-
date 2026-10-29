package at.hausgarten.service

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

/** Writes one local ZIP and verifies every byte through the selected document provider. */
object BackupDocumentTransfer {
    private const val BUFFER = 64 * 1024

    interface LocalFile {
        @Throws(Exception::class)
        fun write(): OutputStream?

        @Throws(Exception::class)
        fun read(): InputStream?
    }

    @JvmStatic
    @Throws(Exception::class)
    fun exportLocal(archive: File, expectedHash: String, alreadyVerified: Boolean, destination: LocalFile) {
        if (!archive.isFile || EncryptedBackupArchive.sha256(archive) != expectedHash) {
            throw IOException("BACKUP_LOCAL_SOURCE_CHANGED")
        }
        if (!alreadyVerified) {
            try {
                FileInputStream(archive).use { input ->
                    destination.write().use { output ->
                        if (output == null) throw IOException("BACKUP_LOCAL_WRITE_FAILED")
                        EncryptedBackupArchive.copy(input, output, null, archive.length())
                        output.flush()
                    }
                }
            } catch (interrupted: InterruptedIOException) {
                throw interrupted
            } catch (failure: Exception) {
                throw IOException("BACKUP_LOCAL_WRITE_FAILED", failure)
            }
        }
        try {
            destination.read().use { input -> verifyReadback(input, expectedHash, archive.length()) }
        } catch (interrupted: InterruptedIOException) {
            throw interrupted
        } catch (failure: Exception) {
            throw IOException("BACKUP_LOCAL_READBACK_FAILED", failure)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun verifyReadback(input: InputStream?, expected: String, size: Long) {
        if (input == null || hash(input, size) != expected) throw IOException("BACKUP_LOCAL_READBACK_FAILED")
    }

    private fun hash(input: InputStream, maximum: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var count = 0L
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            EncryptedBackupArchive.checkCancelled()
            if (n > maximum - count) return ""
            digest.update(buffer, 0, n)
            count += n
        }
        return if (count == maximum) hex(digest.digest()) else ""
    }

    private fun hex(value: ByteArray): String {
        val out = StringBuilder()
        for (b in value) out.append(String.format(Locale.ROOT, "%02x", b.toInt() and 255))
        return out.toString()
    }
}
