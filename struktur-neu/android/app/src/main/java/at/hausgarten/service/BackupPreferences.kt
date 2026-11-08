package at.hausgarten.service

import android.content.Context
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale
import java.util.TreeSet
import org.json.JSONArray
import org.json.JSONObject

/** Typed settings; account references grant no permission on a replacement device. */
object BackupPreferences {
    @JvmField val GROUPS = arrayOf("haus_garten", "planning_private", "haus_garten_mail")

    @JvmStatic
    @Throws(Exception::class)
    fun capture(context: Context): JSONObject {
        val groups = JSONObject()
        for (name in GROUPS) {
            val values = JSONObject()
            val types = JSONObject()
            for ((key, original) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                val lower = key.lowercase(Locale.ROOT)
                if (
                    lower.contains("secret") ||
                        lower.contains("token") ||
                        lower.contains("password") ||
                        lower.contains("credential") ||
                        lower.contains("api_key") ||
                        lower == "weather_iv"
                )
                    continue
                var value: Any? = original
                val type =
                    when (original) {
                        is String -> "string"
                        is Boolean -> "boolean"
                        is Int -> "int"
                        is Long -> "long"
                        is Float -> {
                            value = java.lang.Float.toHexString(original)
                            "float"
                        }
                        is Set<*> -> {
                            val sorted = TreeSet<String>()
                            for (item in original) {
                                if (item !is String)
                                    throw IOException("BACKUP_PREFERENCE_TYPE_UNSUPPORTED")
                                sorted.add(item)
                            }
                            value = JSONArray(sorted)
                            "string_set"
                        }
                        else -> throw IOException("BACKUP_PREFERENCE_TYPE_UNSUPPORTED")
                    }
                values.put(key, value)
                types.put(key, type)
            }
            groups.put(name, JSONObject().put("values", values).put("types", types))
        }
        return JSONObject().put("formatVersion", 1).put("groups", groups)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun values(envelope: JSONObject, name: String): JSONObject {
        if (envelope.getInt("formatVersion") != 1)
            throw IOException("BACKUP_PREFERENCES_VERSION_UNSUPPORTED")
        val group = envelope.getJSONObject("groups").getJSONObject(name)
        val values = group.getJSONObject("values")
        val types = group.getJSONObject("types")
        if (types.length() != values.length()) throw IOException("BACKUP_PREFERENCE_TYPES_MISSING")
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = values.get(key)
            val valid =
                when (types.getString(key)) {
                    "string" -> value is String
                    "boolean" -> value is Boolean
                    "int",
                    "long" ->
                        try {
                            val number = BigDecimal(value.toString())
                            if (types.getString(key) == "int") number.intValueExact()
                            else number.longValueExact()
                            value is Number
                        } catch (invalid: ArithmeticException) {
                            false
                        } catch (invalid: NumberFormatException) {
                            false
                        }
                    "float" ->
                        try {
                            (value as String).toFloat()
                            true
                        } catch (invalid: ClassCastException) {
                            false
                        } catch (invalid: NumberFormatException) {
                            false
                        }
                    "string_set" ->
                        if (value is JSONArray) {
                            val seen = HashSet<String>()
                            var uniqueStrings = true
                            for (i in 0 until value.length()) {
                                if (value.get(i) !is String || !seen.add(value.getString(i)))
                                    uniqueStrings = false
                            }
                            uniqueStrings
                        } else {
                            false
                        }
                    else -> false
                }
            if (!valid) throw IOException("BACKUP_PREFERENCE_TYPE_INVALID")
        }
        return values
    }
}
