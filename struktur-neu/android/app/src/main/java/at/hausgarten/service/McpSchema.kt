package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Strict domain-boundary validation, independent of connector validation. */
internal class McpSchema private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun validate(value: Any?, spec: JSONObject) {
            val declared = spec.opt("type")
            val types = if (declared is JSONArray) declared else JSONArray().put(declared)
            var accepted = false
            for (i in 0 until types.length()) {
                val type = types.optString(i)
                accepted =
                    accepted ||
                        type == "null" && value === JSONObject.NULL ||
                        type == "object" && value is JSONObject ||
                        type == "array" && value is JSONArray ||
                        type == "string" && value is String ||
                        type == "boolean" && value is Boolean ||
                        type == "number" && value is Number ||
                        type == "integer" &&
                            value is Number &&
                            value.toDouble().isFinite() &&
                            value.toDouble() == Math.rint(value.toDouble())
            }
            if (!accepted) reject()
            if (value is Number) {
                val number = value.toDouble()
                if (spec.has("exclusiveMinimum") && number <= spec.getDouble("exclusiveMinimum"))
                    reject()
                if (spec.has("exclusiveMaximum") && number >= spec.getDouble("exclusiveMaximum"))
                    reject()
                if (
                    !number.isFinite() ||
                        number < spec.optDouble("minimum", -Double.MAX_VALUE) ||
                        number > spec.optDouble("maximum", Double.MAX_VALUE)
                )
                    reject()
            }
            if (value is String) {
                if (
                    value.length < spec.optInt("minLength", 0) ||
                        value.length > spec.optInt("maxLength", 10000) ||
                        spec.has("pattern") && !value.matches(Regex(spec.getString("pattern")))
                )
                    reject()
            }
            spec.optJSONArray("enum")?.let { choices ->
                var found = false
                for (i in 0 until choices.length()) if (choices.get(i) == value) found = true
                if (!found) reject()
            }
            if (value is JSONObject) {
                val props = spec.optJSONObject("properties")
                val required = spec.optJSONArray("required")
                if (
                    value.length() < spec.optInt("minProperties", 0) ||
                        value.length() > spec.optInt("maxProperties", 1000)
                )
                    reject()
                if (required != null)
                    for (i in 0 until required.length()) if (!value.has(required.getString(i)))
                        reject()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = props?.optJSONObject(key)
                    if (child != null) validate(value.get(key), child)
                    else if (spec.opt("additionalProperties") is JSONObject)
                        validate(value.get(key), spec.getJSONObject("additionalProperties"))
                    else if (spec.opt("additionalProperties") == false) reject()
                }
            }
            if (value is JSONArray) {
                if (
                    value.length() < spec.optInt("minItems", 0) ||
                        value.length() > spec.optInt("maxItems", 1000)
                )
                    reject()
                if (spec.has("items"))
                    for (i in 0 until value.length()) validate(
                        value.get(i),
                        spec.getJSONObject("items"),
                    )
            }
        }

        private fun reject(): Nothing =
            throw PixelMcpWriteTools.Rejected(
                "INVALID_INPUT",
                "Eingaben entsprechen nicht dem erlaubten Schema. Pflichtangaben, Typen und Grenzen prüfen.",
            )
    }
}
