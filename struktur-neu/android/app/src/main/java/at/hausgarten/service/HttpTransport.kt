package at.hausgarten.service

import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared HTTP values for calendar, weather and delivery. No Drive API or backup client. */
internal open class HttpTransport {
    fun interface Token { @Throws(Exception::class) fun get(): String }
    interface Checkpoints { fun get(key: String): String; fun put(key: String, value: String) }
    fun interface Wire {
        @Throws(Exception::class)
        fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?, maximum: Int): Reply
        @Throws(Exception::class)
        fun readHash(url: String, headers: Map<String, String>, expectedBytes: Long): HashReply =
            throw IOException("HTTP_STREAMING_READBACK_UNSUPPORTED")
        fun cancel(thread: Thread) {}
    }
    class HashReply(@JvmField val response: Reply, @JvmField val bytes: Long, @JvmField val sha256: String)
    class Reply(@JvmField val status: Int, @JvmField val body: ByteArray, @JvmField val headers: Map<String, String>) {
        fun header(key: String): String = headers.entries.firstOrNull { key.equals(it.key, true) }?.value ?: ""
        @Throws(Exception::class) fun json(): JSONObject = JSONObject(String(body, StandardCharsets.UTF_8))
    }
    class Failure(@JvmField val code: String, message: String, @JvmField val retryAfter: Long, @JvmField val userAction: Boolean) : IOException(message)
    companion object {
        const val MAX_JSON = 64 * 1024 * 1024
        const val CHUNK = 256 * 1024
        const val LARGE_CHUNK = 4 * 1024 * 1024
        const val CACHE_LIMIT = 4 * 1024 * 1024
        const val CACHE_ENTRY_LIMIT = 512 * 1024
        const val HTTP_CONCURRENCY = 6
        @JvmStatic fun validId(id: String?): Boolean = id != null && id.matches(Regex("[A-Za-z0-9_-]{10,200}"))
        @JvmStatic fun retryAfter(value: String?): Long = try {
            value!!.trim().toLong().coerceIn(1, 3600) * 1000
        } catch (e: Exception) {
            try { (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceIn(1000, 3600000) }
            catch (ignored: Exception) { 30000 }
        }
        @JvmStatic @Throws(Exception::class) fun sha256(bytes: ByteArray): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
    }
}
