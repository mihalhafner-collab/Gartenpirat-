package at.hausgarten.service

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Catalogue serialization only; never business/CAS/publication hashes. */
internal class CatalogSemanticHash private constructor() {
    companion object {
        const val VERSION = "catalog-json-v1"

        @JvmStatic
        @Throws(Exception::class)
        fun canonical(value: Any?): String =
            when (value) {
                is JSONObject -> {
                    val keys = ArrayList<String>()
                    val iterator = value.keys()
                    while (iterator.hasNext()) keys.add(iterator.next())
                    keys.sort()
                    "{" +
                        keys.joinToString(",") { quote(it) + ":" + canonical(value.get(it)) } +
                        "}"
                }
                is JSONArray ->
                    (0 until value.length()).joinToString(",", "[", "]") {
                        canonical(value.get(it))
                    }
                is String -> quote(value)
                else -> PixelMcpWriteTools.canonical(value)
            }

        private fun quote(value: String): String {
            val out = StringBuilder("\"")
            for (char in value) {
                when (char) {
                    '\\' -> out.append("\\\\")
                    '"' -> out.append("\\\"")
                    '\b' -> out.append("\\b")
                    '\u000c' -> out.append("\\f")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else ->
                        if (char.code < 32 || char == '\u2028' || char == '\u2029')
                            out.append(String.format(Locale.ROOT, "\\u%04x", char.code))
                        else out.append(char)
                }
            }
            return out.append('"').toString()
        }

        @JvmStatic
        @Throws(Exception::class)
        fun hash(value: Any?): String =
            HttpTransport.sha256(canonical(value).toByteArray(Charsets.UTF_8))

        @JvmStatic
        @Throws(Exception::class)
        fun describe(value: Any?): JSONObject =
            JSONObject().put("version", VERSION).put("sha256", hash(value))
    }
}
