package at.hausgarten.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

/** Writes one local ZIP and verifies every byte through the selected document provider. */
object BackupDocumentTransfer {
    private const val BUFFER = 64 * 1024

    interface LocalFile {
        @Throws(Exception::class) fun write(): OutputStream?

        @Throws(Exception::class) fun read(): InputStream?

        /** Only a destination with a proven atomic replacement strategy may override this. */
        @Throws(Exception::class)
        fun replaceFrom(archive: File, previousHash: String, previousSize: Long) {
            throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
        }
    }

    /** The old target is never opened for writing. Both temporary files share its filesystem. */
    @JvmStatic
    @Throws(Exception::class)
    fun atomicReplace(target: File, archive: File, previousHash: String, previousSize: Long) {
        val path = target.toPath()
        if (
            Files.isSymbolicLink(path) ||
                !target.isFile ||
                target.canonicalFile != target.absoluteFile
        )
            throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
        val parent = target.parentFile ?: throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
        val staged =
            try {
                Files.createTempFile(parent.toPath(), ".hg-backup-", ".tmp").toFile()
            } catch (failure: Exception) {
                throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE", failure)
            }
        try {
            FileInputStream(archive).use { input ->
                FileOutputStream(staged).use { output ->
                    BackupArchive.copy(
                        input,
                        BackupStorageBudget.guard(output, parent),
                        null,
                        archive.length(),
                    )
                    output.fd.sync()
                }
            }
            if (
                staged.length() != archive.length() ||
                    BackupArchive.sha256(staged) != BackupArchive.sha256(archive)
            )
                throw IOException("BACKUP_LOCAL_SOURCE_CHANGED")
            // A different app/user may have changed the selected file since its last readback.
            FileInputStream(target).use { verifyReadback(it, previousHash, previousSize) }
            BackupArchive.checkCancelled()
            try {
                Files.move(
                    staged.toPath(),
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (failure: Exception) {
                throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE", failure)
            }
        } finally {
            Files.deleteIfExists(staged.toPath())
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun updateLocal(
        archive: File,
        expectedHash: String,
        previousHash: String,
        previousSize: Long,
        destination: LocalFile,
    ) {
        if (!archive.isFile || BackupArchive.sha256(archive) != expectedHash)
            throw IOException("BACKUP_LOCAL_SOURCE_CHANGED")
        try {
            destination.read().use { verifyReadback(it, previousHash, previousSize) }
        } catch (failure: Exception) {
            throw IOException("BACKUP_TARGET_CHANGED", failure)
        }
        destination.replaceFrom(archive, previousHash, previousSize)
        try {
            destination.read().use { verifyReadback(it, expectedHash, archive.length()) }
        } catch (failure: Exception) {
            throw IOException("BACKUP_LOCAL_READBACK_FAILED", failure)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun exportLocal(
        archive: File,
        expectedHash: String,
        alreadyVerified: Boolean,
        destination: LocalFile,
    ) {
        if (!archive.isFile || BackupArchive.sha256(archive) != expectedHash) {
            throw IOException("BACKUP_LOCAL_SOURCE_CHANGED")
        }
        if (!alreadyVerified) {
            requireEmpty(destination)
            try {
                FileInputStream(archive).use { input ->
                    destination.write().use { output ->
                        if (output == null) throw IOException("BACKUP_LOCAL_WRITE_FAILED")
                        BackupArchive.copy(input, output, null, archive.length())
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
            destination.read().use { input ->
                verifyReadback(input, expectedHash, archive.length())
            }
        } catch (interrupted: InterruptedIOException) {
            throw interrupted
        } catch (failure: Exception) {
            throw IOException("BACKUP_LOCAL_READBACK_FAILED", failure)
        }
    }

    private fun requireEmpty(destination: LocalFile) {
        destination.read().use { input ->
            if (input == null) throw IOException("BACKUP_LOCAL_READBACK_FAILED")
            if (input.read() != -1) throw IOException("BACKUP_SAFE_REPLACE_UNAVAILABLE")
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun verifyReadback(input: InputStream?, expected: String, size: Long) {
        if (input == null || hash(input, size) != expected)
            throw IOException("BACKUP_LOCAL_READBACK_FAILED")
    }

    private fun hash(input: InputStream, maximum: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var count = 0L
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            BackupArchive.checkCancelled()
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
