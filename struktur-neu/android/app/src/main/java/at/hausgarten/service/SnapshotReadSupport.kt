package at.hausgarten.service

import java.math.BigInteger
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject

/** Pure bounded selection/redaction. No Android, provider or writes. */
object SnapshotReadSupport {
    private val NATURAL = Pattern.compile("\\d+|\\D+")

    @JvmStatic
    @Throws(Exception::class)
    fun find(rows: JSONArray?, id: String): JSONObject {
        for (i in 0 until (rows?.length() ?: 0)) if (id == rows!!.getJSONObject(i).optString("id"))
            return rows.getJSONObject(i)
        throw IllegalArgumentException("Die Datensatz-ID wurde im Pixel-Datenstand nicht gefunden.")
    }

    @JvmStatic
    fun text(row: JSONObject, vararg keys: String): String =
        buildString { for (key in keys) append(' ').append(row.optString(key)) }
            .lowercase(Locale.GERMAN)

    @JvmStatic
    fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.GERMAN)
            .replace("ß", "ss")
            .replace("ae", "a")
            .replace("oe", "o")
            .replace("ue", "u")

    @JvmStatic
    fun matches(query: String, haystack: String): Boolean {
        val value = fold(haystack)
        for (term in fold(query).trim().split(Regex("\\s+"))) if (!value.contains(term))
            return false
        return true
    }

    @JvmStatic
    fun natural(a: String, b: String, collator: Collator): Int {
        val left = NATURAL.matcher(a)
        val right = NATURAL.matcher(b)
        while (left.find()) {
            if (!right.find()) return 1
            val x = left.group()
            val y = right.group()
            val order =
                if (Character.isDigit(x[0]) && Character.isDigit(y[0]))
                    BigInteger(x).compareTo(BigInteger(y))
                else collator.compare(x, y)
            if (order != 0) return order
        }
        return if (right.find()) -1 else 0
    }

    @JvmStatic
    @Throws(Exception::class)
    fun withoutBinary(value: Any?): Any? {
        if (value is JSONArray) {
            val result = JSONArray()
            for (i in 0 until value.length()) result.put(withoutBinary(value.get(i)))
            return result
        }
        if (value !is JSONObject) return value
        val result = JSONObject()
        val photo =
            value.has("original_name") &&
                (value.has("data") || value.has("storage_name") || value.has("mime_type"))
        for (key in value.keys()) {
            val lower = key.lowercase(Locale.ROOT)
            val item = value.get(key)
            val inlineBinary =
                item is String &&
                    item.trim().lowercase(Locale.ROOT).matches(Regex("^data:[^,]+;base64,.*"))
            val plainBase64 =
                lower == "data" &&
                    item is String &&
                    item.length > 256 &&
                    item.matches(Regex("[A-Za-z0-9+/]*={0,2}"))
            if (
                lower.endsWith("_base64") ||
                    lower == "thumbnail_data" ||
                    (photo && lower == "data") ||
                    inlineBinary ||
                    plainBase64
            )
                continue
            result.put(key, withoutBinary(item))
        }
        return result
    }

    @JvmStatic
    @JvmOverloads
    @Throws(Exception::class)
    fun select(
        rows: JSONArray?,
        a: JSONObject,
        kind: String,
        context: JSONObject? = null,
    ): JSONArray {
        val result = JSONArray()
        if (rows == null) return result
        val query = a.optString("query", "").trim()
        val limit = a.optInt("limit", if (kind == "worklog") 50 else 20)
        if (limit !in 1..100)
            throw IllegalArgumentException("Bitte 1 bis 100 Ergebnisse anfordern.")
        val sorted = ArrayList<JSONObject>()
        for (i in 0 until rows.length()) sorted.add(rows.getJSONObject(i))
        if (kind == "customer" || kind == "order") {
            val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
            val field = if (kind == "customer") "name" else "title"
            sorted.sortWith { x, y ->
                var order =
                    if (kind == "customer")
                        java.lang.Boolean.compare(
                            x.optInt("active", 1) == 0 || !x.optBoolean("active", true),
                            y.optInt("active", 1) == 0 || !y.optBoolean("active", true),
                        )
                    else 0
                if (order == 0) order = natural(x.optString(field), y.optString(field), collator)
                if (order == 0) x.optString("id").compareTo(y.optString("id")) else order
            }
        }
        if (kind == "worklog")
            sorted.sortWith(
                compareByDescending<JSONObject> { it.optString("work_date") }
                    .thenBy { it.optString("id") }
            )
        if (kind == "photo")
            sorted.sortWith(
                compareByDescending<JSONObject> {
                        it.optString("capture_time", it.optString("created_at"))
                    }
                    .thenBy { it.optString("id") }
            )
        val customerNames = HashMap<String, String>()
        val orderTitles = HashMap<String, String>()
        if (context != null) {
            val customers = context.optJSONArray("customers")
            val orders = context.optJSONArray("orders")
            for (i in 0 until (customers?.length() ?: 0)) {
                val c = customers!!.getJSONObject(i)
                customerNames[c.optString("id")] = text(c, "name", "company")
            }
            for (i in 0 until (orders?.length() ?: 0)) {
                val o = orders!!.getJSONObject(i)
                orderTitles[o.optString("id")] = o.optString("title")
            }
        }
        val offset = a.optInt("offset")
        var matched = 0
        if (offset < 0 || offset > 1_000_000)
            throw IllegalArgumentException("Ungültiger Listenversatz.")
        for (row in sorted) {
            if (
                kind == "photo" &&
                    arrayOf("role", "worklog_id", "session_id").any {
                        a.optString(it).isNotEmpty() && a.getString(it) != row.optString(it)
                    }
            )
                continue
            if (
                kind == "customer" &&
                    !a.optBoolean("include_inactive", false) &&
                    (row.optInt("active", 1) == 0 || !row.optBoolean("active", true))
            )
                continue
            if (
                a.optString("customer_id").isNotEmpty() &&
                    a.optString("customer_id") != row.optString("customer_id")
            )
                continue
            if (
                a.optString("billing_customer_id").isNotEmpty() &&
                    a.optString("billing_customer_id") != BillingRules.recipient(row)
            )
                continue
            if (
                a.optString("order_id").isNotEmpty() &&
                    a.optString("order_id") != row.optString("order_id")
            )
                continue
            if (
                a.optString("status").isNotEmpty() &&
                    a.optString("status") != row.optString("status")
            )
                continue
            if (
                kind == "order" &&
                    a.optBoolean("open_only", false) &&
                    row.optString("status") !in
                        listOf("", "Geplant", "Aktiv", "In Arbeit", "Wartet auf Material", "Offen")
            )
                continue
            if (a.has("billed") && a.getBoolean("billed") != (row.optInt("billed", 0) != 0))
                continue
            val day =
                (if (kind == "photo") row.optString("capture_time", row.optString("created_at"))
                    else row.optString("work_date"))
                    .take(10)
            if (a.optString("date_from").isNotEmpty() && day < a.optString("date_from")) continue
            if (a.optString("date_to").isNotEmpty() && day > a.optString("date_to")) continue
            var haystack =
                when (kind) {
                    "customer" ->
                        text(
                            row,
                            "name",
                            "company",
                            "first_name",
                            "last_name",
                            "street",
                            "postal_code",
                            "city",
                            "email",
                            "phone",
                        )
                    "order" -> text(row, "title", "description", "location", "customer_name")
                    else ->
                        text(
                            row,
                            "activity",
                            "notes",
                            "material",
                            "location",
                            "customer_name",
                            "order_title",
                        )
                }
            if (context != null)
                haystack +=
                    " " +
                        customerNames.getOrDefault(row.optString("customer_id"), "") +
                        " " +
                        orderTitles.getOrDefault(row.optString("order_id"), "")
            haystack +=
                " " +
                    text(
                        row,
                        "invoice_number",
                        "offer_number",
                        "caption",
                        "original_name",
                        "filename",
                    )
            if (query.isNotEmpty() && !matches(query, haystack)) {
                val digits = query.replace(Regex("\\D"), "")
                if (
                    kind != "customer" ||
                        digits.length < 4 ||
                        !query.matches(Regex("[+0-9\\s()./\\-]+")) ||
                        !row.optString("phone").replace(Regex("\\D"), "").contains(digits)
                )
                    continue
            }
            if (matched++ < offset) continue
            result.put(JSONObject(row.toString()))
            if (result.length() >= limit) break
        }
        return withoutBinary(result) as JSONArray
    }
}
