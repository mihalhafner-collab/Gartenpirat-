package at.hausgarten.service

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** Bounded, contiguous, hash-verified upload blocks; no platform I/O. */
class McpFileTransfer {
    companion object {
        @JvmStatic @Throws(Exception::class)
        fun sha(bytes: ByteArray): String = buildString {
            for (byte in MessageDigest.getInstance("SHA-256").digest(bytes)) append(String.format(Locale.ROOT, "%02x", byte.toInt() and 255))
        }

        @JvmStatic @Throws(Exception::class)
        fun append(prior: ByteArray, encoded: String, offset: Int, total: Int, expected: String): ByteArray {
            val block = try { Base64.getDecoder().decode(encoded) }
            catch (_: IllegalArgumentException) { throw PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT", "Ungültiger Dateiblock.") }
            if (Base64.getEncoder().encodeToString(block) != encoded || block.isEmpty() || block.size > PixelMcpOwnerTools.CHUNK || offset != prior.size || total < 1 || total > 20 * 1024 * 1024 || prior.size + block.size > total || sha(block) != expected) {
                throw PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT", "Dateiblock, Position, Größe oder Prüfsumme stimmt nicht. Upload bleibt erhalten.")
            }
            val out = prior.copyOf(prior.size + block.size)
            System.arraycopy(block, 0, out, prior.size, block.size)
            return out
        }
    }
}
