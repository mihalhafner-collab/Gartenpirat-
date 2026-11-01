package at.hausgarten.service

import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.function.LongSupplier

/** Leaves space for local business writes. This is a free-space reserve, not an archive limit. */
object BackupStorageBudget {
    const val BUSINESS_RESERVE_BYTES = 256L * 1024 * 1024

    @JvmStatic
    @Throws(IOException::class)
    fun require(volume: File, additionalBytes: Long) = requireAvailable(volume.usableSpace, additionalBytes)

    @JvmStatic
    @Throws(IOException::class)
    fun requireAvailable(available: Long, additionalBytes: Long) {
        if (additionalBytes < 0 || available < BUSINESS_RESERVE_BYTES || additionalBytes > available - BUSINESS_RESERVE_BYTES) {
            throw IOException("BACKUP_STORAGE_RESERVE_REQUIRED")
        }
    }

    @JvmStatic
    fun guard(output: OutputStream, volume: File): OutputStream = guard(output, LongSupplier { volume.usableSpace })

    @JvmStatic
    fun guard(output: OutputStream, available: LongSupplier): OutputStream = object : FilterOutputStream(output) {
        @Throws(IOException::class)
        override fun write(value: Int) {
            EncryptedBackupArchive.checkCancelled()
            requireAvailable(available.asLong, 1)
            `out`.write(value)
        }

        @Throws(IOException::class)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            EncryptedBackupArchive.checkCancelled()
            requireAvailable(available.asLong, length.toLong())
            `out`.write(bytes, offset, length)
        }
    }
}
