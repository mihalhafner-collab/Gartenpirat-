package at.hausgarten.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only protection of a separately recoverable random backup key. No silent reset. */
class BackupRecoveryKeyStore private constructor(context: Context, scope: String) {
    private val protectedKey: AtomicFile
    private val alias: String
    private val capsuleAad: ByteArray

    @Throws(Exception::class)
    constructor(context: Context) : this(context, "")

    init {
        val directory = File(context.noBackupFilesDir, "backup-key-protection")
        protectedKey = AtomicFile(File(directory, if (scope.isEmpty()) "recovery.v1" else "recovery-$scope.v1"))
        capsuleAad = if (scope.isEmpty()) AAD else
            (String(AAD, StandardCharsets.US_ASCII) + "/scope/" + scope).toByteArray(StandardCharsets.US_ASCII)
        // An isolated QA Context receives a different alias; never access the active key in tests.
        val binding = context.filesDir.canonicalPath + if (scope.isEmpty()) "" else "/scope/$scope"
        val digest = MessageDigest.getInstance("SHA-256").digest(binding.toByteArray(StandardCharsets.UTF_8))
        val name = StringBuilder("hausgarten.backup.v1.")
        for (i in 0 until 16) name.append(String.format(Locale.ROOT, "%02x", digest[i].toInt() and 255))
        alias = name.toString()
    }

    fun configured(): Boolean = synchronized(KEY_LOCK) {
        protectedKey.baseFile.exists() || File("${protectedKey.baseFile}.bak").exists()
    }

    @Throws(Exception::class)
    fun confirmAndStore(requestId: String?, candidate: String?, confirmation: String?) {
        synchronized(KEY_LOCK) { confirmAndStoreLocked(requestId, candidate, confirmation) }
    }

    @Throws(Exception::class)
    private fun confirmAndStoreLocked(requestId: String?, candidate: String?, confirmation: String?) {
        if (requestId == null || !requestId.matches(Regex("[A-Za-z0-9_-]{8,200}"))) {
            throw IllegalArgumentException("BACKUP_REQUEST_ID_REQUIRED")
        }
        val candidateKey = BackupCrypto.recoveryKey(candidate)
        var confirmedKey: ByteArray? = null
        try {
            confirmedKey = BackupCrypto.recoveryKey(confirmation)
            if (!MessageDigest.isEqual(candidateKey, confirmedKey)) throw GeneralSecurityException("RECOVERY_CONFIRMATION_MISMATCH")
            if (configured()) {
                val saved = readProtected()
                if (saved.getString("request_id") == requestId && MessageDigest.isEqual(
                        saved.getString("recovery_code").toByteArray(StandardCharsets.US_ASCII),
                        candidate!!.trim { it <= ' ' }.toByteArray(StandardCharsets.US_ASCII))) return
                if (saved.getString("request_id") == requestId) throw GeneralSecurityException("REPLAY_MISMATCH")
                throw GeneralSecurityException("RECOVERY_ALREADY_CONFIGURED")
            }
            val value = JSONObject().put("formatVersion", 1).put("request_id", requestId)
                .put("recovery_code", candidate!!.trim { it <= ' ' })
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(true))
            cipher.updateAAD(capsuleAad)
            val sealed = cipher.doFinal(value.toString().toByteArray(StandardCharsets.UTF_8))
            val parent = protectedKey.baseFile.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("RECOVERY_STORAGE_UNAVAILABLE")
            var output: FileOutputStream? = null
            try {
                val started = protectedKey.startWrite()
                output = started
                val data = DataOutputStream(started)
                data.writeInt(1)
                data.write(cipher.iv)
                data.writeInt(sealed.size)
                data.write(sealed)
                data.flush()
                protectedKey.finishWrite(started)
                output = null
            } catch (failure: Exception) {
                output?.let { protectedKey.failWrite(it) }
                throw failure
            }
            // Readback includes platform authentication and a recovery-code checksum.
            if (!MessageDigest.isEqual(read().toByteArray(StandardCharsets.US_ASCII),
                    candidate.trim { it <= ' ' }.toByteArray(StandardCharsets.US_ASCII))) {
                throw GeneralSecurityException("RECOVERY_READBACK_FAILED")
            }
        } finally {
            Arrays.fill(candidateKey, 0.toByte())
            confirmedKey?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    @Throws(Exception::class)
    fun read(): String = synchronized(KEY_LOCK) {
        val code = readProtected().getString("recovery_code")
        val checked = BackupCrypto.recoveryKey(code)
        Arrays.fill(checked, 0.toByte())
        code
    }

    private fun repairAad(repairedAlias: String): ByteArray =
        (String(capsuleAad, StandardCharsets.US_ASCII) + "/v2/" + repairedAlias).toByteArray(StandardCharsets.US_ASCII)

    @Throws(Exception::class)
    private fun readProtected(): JSONObject {
        if (!configured()) throw GeneralSecurityException("RECOVERY_NOT_CONFIGURED")
        val iv = ByteArray(12)
        val sealed: ByteArray
        var selectedAlias = alias
        val version: Int
        DataInputStream(protectedKey.openRead()).use { input ->
            version = input.readInt()
            if (version != 1 && version != 2) throw IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED")
            if (version == 2) {
                selectedAlias = input.readUTF()
                if (!selectedAlias.matches(Regex(Pattern.quote(alias) + "\\.repair\\.[0-9a-f-]{36}"))) {
                    throw IOException("RECOVERY_STORAGE_INVALID")
                }
            }
            input.readFully(iv)
            val length = input.readInt()
            if (length < 16 || length > 4096) throw IOException("RECOVERY_STORAGE_INVALID")
            sealed = ByteArray(length)
            input.readFully(sealed)
            if (input.read() != -1) throw IOException("RECOVERY_STORAGE_INVALID")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(selectedAlias, false), GCMParameterSpec(128, iv))
        cipher.updateAAD(if (version == 1) capsuleAad else repairAad(selectedAlias))
        val plain = cipher.doFinal(sealed)
        try {
            val value = JSONObject(String(plain, StandardCharsets.UTF_8))
            if (value.getInt("formatVersion") != 1) throw IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED")
            return value
        } finally {
            Arrays.fill(plain, 0.toByte())
        }
    }

    @Throws(Exception::class)
    private fun key(create: Boolean): SecretKey? = key(alias, create)

    @Throws(Exception::class)
    private fun key(selectedAlias: String, create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        if (!store.containsAlias(selectedAlias)) {
            if (!create) throw GeneralSecurityException("RECOVERY_KEYSTORE_UNAVAILABLE_USE_RECOVERY_CODE")
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(selectedAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
            generator.generateKey()
        }
        return store.getKey(selectedAlias, null) as SecretKey?
    }

    companion object {
        private val AAD = "at.hausgarten.service/backup-recovery/v1".toByteArray(StandardCharsets.US_ASCII)
        private val KEY_LOCK = Any()

        @JvmStatic
        @Throws(Exception::class)
        fun scoped(context: Context?, scope: String?): BackupRecoveryKeyStore {
            if (scope == null || !scope.matches(Regex("[A-Za-z0-9_-]{8,120}"))) {
                throw IllegalArgumentException("RECOVERY_SCOPE_INVALID")
            }
            return BackupRecoveryKeyStore(context!!, scope)
        }
    }
}
