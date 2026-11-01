package at.hausgarten.service

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig2026
import com.google.crypto.tink.aead.AesGcmKey
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig2026
import com.google.crypto.tink.util.SecretBytes
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Arrays
import java.util.Base64
import java.util.Locale

/** Reader for existing encrypted archives. New backups use plaintext ZIP only. */
object BackupCrypto {
    private val MAGIC = "HG-BACKUP-1\n".toByteArray(StandardCharsets.US_ASCII)
    private const val MAX_ENVELOPE = 16 * 1024

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun recoveryKey(code: String?): ByteArray {
        if (code == null || !code.trim { it <= ' ' }.matches(Regex("HG1\\.[A-Za-z0-9_-]{43}\\.[0-9a-f]{8}"))) {
            throw GeneralSecurityException("RECOVERY_CODE_INVALID")
        }
        val value = code.trim { it <= ' ' }
        val key = try {
            Base64.getUrlDecoder().decode(value.substring(4, 47))
        } catch (invalid: IllegalArgumentException) {
            throw GeneralSecurityException("RECOVERY_CODE_INVALID")
        }
        if (key.size != 32 || !MessageDigest.isEqual(value.toByteArray(StandardCharsets.US_ASCII), code(key).toByteArray(StandardCharsets.US_ASCII))) {
            Arrays.fill(key, 0.toByte())
            throw GeneralSecurityException("RECOVERY_CODE_INVALID")
        }
        return key
    }

    @Throws(GeneralSecurityException::class)
    private fun code(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("Hausgarten recovery checksum v1\n".toByteArray(StandardCharsets.US_ASCII))
        val hash = digest.digest(key)
        val checksum = StringBuilder()
        for (i in 0 until 4) checksum.append(String.format(Locale.ROOT, "%02x", hash[i].toInt() and 255))
        return "HG1." + Base64.getUrlEncoder().withoutPadding().encodeToString(key) + "." + checksum
    }

    @Throws(GeneralSecurityException::class)
    private fun recoveryAead(recovery: String?): Aead {
        val key = recoveryKey(recovery)
        try {
            val parameters = AesGcmParameters.builder().setKeySizeBytes(32).setIvSizeBytes(12).setTagSizeBytes(16)
                .setVariant(AesGcmParameters.Variant.NO_PREFIX).build()
            val imported = AesGcmKey.builder().setParameters(parameters)
                .setKeyBytes(SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get())).build()
            return KeysetHandle.newBuilder().setConfiguration(AeadConfig2026.get())
                .addEntry(KeysetHandle.importKey(imported).withFixedId(1).makePrimary()).build()
                .getPrimitive(AeadConfig2026.get(), Aead::class.java)
        } finally {
            Arrays.fill(key, 0.toByte())
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decrypt(source: InputStream, recovery: String?): InputStream {
        val input = DataInputStream(source)
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        if (!Arrays.equals(magic, MAGIC)) throw IOException("BACKUP_FORMAT_UNSUPPORTED")
        val length = input.readInt()
        if (length < 1 || length > MAX_ENVELOPE) throw IOException("BACKUP_ENVELOPE_INVALID")
        val wrapped = ByteArray(length)
        input.readFully(wrapped)
        val header = ByteArrayOutputStream()
        val bytes = DataOutputStream(header)
        bytes.write(magic)
        bytes.writeInt(length)
        bytes.write(wrapped)
        bytes.flush()
        val handle = TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
            String(wrapped, StandardCharsets.UTF_8), recoveryAead(recovery), MAGIC, StreamingAeadConfig2026.get()
        )
        return handle.getPrimitive(StreamingAeadConfig2026.get(), StreamingAead::class.java)
            .newDecryptingStream(source, header.toByteArray())
    }
}
