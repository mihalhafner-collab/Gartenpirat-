package at.hausgarten.service

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device consent and private weather key. Neither is part of snapshot/MCP/backup data. */
internal object PlanningAccount {
    const val BASE_SCOPE = CalendarAccessRules.BASE_SCOPE
    @JvmStatic fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("planning_private", 0)
    @JvmStatic fun connected(context: Context): Boolean = !prefs(context).getString("calendar_id", "").isNullOrEmpty() && !prefs(context).getString("account", "").isNullOrEmpty()
    @JvmStatic @Throws(Exception::class) fun token(context: Context, account: String, busy: Boolean, consent: Activity?): String = try {
        CalendarAuthorization.token(context, account, busy)
    } catch (required: CalendarAuthorization.ConsentRequired) {
        if (consent != null) throw required
        throw CalendarAuthorization.failure("CALENDAR_AUTH_REQUIRED")
    }
    @JvmStatic @Throws(Exception::class) fun bindingHash(context: Context): String {
        val values = HashMap(prefs(context).all)
        return CalendarAccessRules.bindingHash(values.getOrDefault("account", "") as String, values.getOrDefault("calendar_id", "") as String, values["read_busy"] == true)
    }
    @JvmStatic fun client(context: Context): CalendarClient {
        val values = HashMap(prefs(context).all)
        return client(context, values.getOrDefault("account", "") as String, values["read_busy"] == true, "")
    }
    @JvmStatic fun client(context: Context, account: String, busy: Boolean, granted: String): CalendarClient {
        val app = context.applicationContext
        return CalendarClient(CalendarCredential(granted, object : CalendarCredential.Source {
            override fun acquire(): String = token(app, account, busy, null)
            override fun invalidate(rejected: String) = CalendarAuthorization.invalidate(app, rejected)
        }), PlanningWire())
    }
    @JvmStatic @Throws(Exception::class) fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "hausgarten.weather.private.v1"
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        return keyStore.getKey(alias, null) as SecretKey
    }
    @JvmStatic @Throws(Exception::class) fun weatherKey(context: Context, value: String) {
        if (value.isEmpty()) { prefs(context).edit().remove("weather_secret").remove("weather_iv").commit(); return }
        require(value.length <= 512 && value.matches(Regex("[A-Za-z0-9_.-]+"))) { "Ungültiges API-Schlüsselformat." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
        if (!prefs(context).edit().putString("weather_secret", Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(java.nio.charset.StandardCharsets.UTF_8))))
            .putString("weather_iv", Base64.getEncoder().encodeToString(cipher.iv)).commit()) throw java.io.IOException("Wetterzugang nicht gespeichert.")
    }
    @JvmStatic @Throws(Exception::class) fun weatherKey(context: Context): String {
        val preferences = prefs(context); val value = preferences.getString("weather_secret", "") ?: ""
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.getDecoder().decode(preferences.getString("weather_iv", ""))))
        return String(cipher.doFinal(Base64.getDecoder().decode(value)), java.nio.charset.StandardCharsets.UTF_8)
    }
}
