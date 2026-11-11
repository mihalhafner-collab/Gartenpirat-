package at.hausgarten.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import org.json.*

class PixelMcpWriteTools {
    interface Sink {
        fun writable(): Boolean {
            return true
        }

        @Throws(Exception::class) fun execute(tool: String, args: JSONObject): JSONObject

        @Throws(Exception::class) fun status(id: String, state: JSONObject): JSONObject
    }

    open class Rejected : IllegalArgumentException {
        @JvmField val code: String

        constructor(code: String, message: String) : super(message) {
            this.code = code
        }
    }

    companion object {
        @JvmField
        val NAMES: Array<String> =
            arrayOf("create_work_record", "create_customer_work_record", "update_work_record")
        @JvmField
        val EDITABLE: Array<String> =
            arrayOf(
                "billing_customer_id",
                "property_id",
                "billable",
                "order_id",
                "work_date",
                "activity",
                "hours",
                "start_time",
                "end_time",
                "break_minutes",
                "material",
                "material_cost",
                "disposal_cost",
                "notes",
                "location",
                "customer_visible",
                "allow_overnight",
            )
        @JvmField
        val BASE_FIELDS: Array<String> =
            arrayOf(
                "id",
                "customer_id",
                "billing_customer_id",
                "property_id",
                "billable",
                "order_id",
                "work_date",
                "activity",
                "hours",
                "start_time",
                "end_time",
                "break_minutes",
                "material",
                "material_cost",
                "disposal_cost",
                "accounting_amount",
                "allow_overnight",
                "kilometers",
                "notes",
                "location",
                "customer_visible",
                "billed",
                "updated_at",
            )

        @JvmStatic
        fun handles(name: String): Boolean {
            return NAMES.asList().contains(name)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun field(key: String): JSONObject {
            var type =
                (if (((key == "hours") || (key in listOf("material_cost", "disposal_cost"))))
                    "number"
                else
                    (if (key == "break_minutes") "integer"
                    else
                        (if (key in setOf("customer_visible", "allow_overnight", "billable"))
                            "boolean"
                        else "string")))
            val spec = JSONObject().put("type", type)
            if (key in setOf("material_cost", "disposal_cost")) {
                spec.put("type", JSONArray().put(type).put("null"))
            }
            if (type == "string") {
                spec.put(
                    "maxLength",
                    (if (((key == "activity") || (key == "material")) || (key == "notes")) 10000
                    else 500),
                )
            }
            if ((type == "number") || (type == "integer")) {
                spec.put("minimum", 0)
            }
            return spec
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String): JSONObject {
            var props =
                JSONObject()
                    .put(
                        "request_id",
                        JSONObject().put("type", "string").put("minLength", 8).put("maxLength", 128),
                    )
            val required = JSONArray().put("request_id")
            if (name == "update_work_record") {
                props
                    .put("work_record_id", field("work_record_id"))
                    .put("expected_record_hash", field("expected_record_hash"))
                val patch = JSONObject()
                for (key in EDITABLE) {
                    patch.put(key, field(key))
                }
                props.put(
                    "updates",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", patch)
                        .put("additionalProperties", false)
                        .put("minProperties", 1),
                )
                required.put("work_record_id").put("expected_record_hash").put("updates")
            } else {
                for (key in EDITABLE) {
                    props.put(key, field(key))
                }
                props.put("customer_id", field("customer_id"))
                required
                    .put(if (name == "create_work_record") "order_id" else "customer_id")
                    .put("work_date")
                    .put("activity")
            }
            return JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", required)
                .put("additionalProperties", false)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun tools(): JSONArray {
            val result = JSONArray()
            for (name in NAMES) {
                result.put(
                    JSONObject()
                        .put("name", name)
                        .put("inputSchema", schema(name))
                        .put(
                            "description",
                            (if (name == "update_work_record")
                                "Korrigiert eine Arbeitsaufzeichnung. Zuerst get_work_record aufrufen und record_hash als expected_record_hash übernehmen. Keine Änderung von Kunden-ID, Abrechnung oder Rechnungen."
                            else
                                "Legt eine Arbeitsaufzeichnung mit tatsächlichen Kunden-/Auftrags-IDs dauerhaft auf dem Pixel an. request_id bei Wiederholung unverändert lassen. storage_contract_version 3: lokale Speicherung; get_full_backup_status liest den getrennten Vollsicherungsstand."),
                        )
                        .put(
                            "annotations",
                            JSONObject()
                                .put("readOnlyHint", false)
                                .put("destructiveHint", false)
                                .put("idempotentHint", true)
                                .put("openWorldHint", false),
                        )
                )
            }
            return result
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validate(args: JSONObject, schema: JSONObject): Unit {
            val props = schema.getJSONObject("properties")
            run {
                val it = args.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    if (!(props.has(key))) {
                        throw Rejected("INVALID_INPUT", ("Diese Angabe ist nicht erlaubt: " + key))
                    }
                    var value = args.get(key)
                    val spec = props.getJSONObject(key)
                    val declared = spec.get("type")
                    val types = if (declared is JSONArray) declared else JSONArray().put(declared)
                    if (
                        value === JSONObject.NULL &&
                            (0 until types.length()).any { types.optString(it) == "null" }
                    )
                        continue
                    val type = types.getString(0)
                    var valid: Boolean =
                        (if (type == "string") value is String
                        else
                            (if (type == "boolean") value is Boolean
                            else (if (type == "object") value is JSONObject else value is Number)))
                    if (!(valid)) {
                        throw Rejected("INVALID_INPUT", ("Ungültiger Eingabetyp: " + key))
                    }
                    if (
                        (value is String &&
                            (((value as String).length > spec.optInt("maxLength", 10000)) ||
                                ((value as String).length < spec.optInt("minLength", 0))))
                    ) {
                        throw Rejected(
                            "INVALID_INPUT",
                            ("Bitte die Länge der Angabe prüfen: " + key),
                        )
                    }
                    if (value is Number) {
                        val n: Double = (value as Number).toDouble()
                        if (
                            ((!(java.lang.Double.isFinite(n)) || (n < 0)) ||
                                ((type == "integer") && (n != Math.floor(n))))
                        ) {
                            throw Rejected(
                                "INVALID_INPUT",
                                ("Bitte eine gültige nicht negative Zahl eingeben: " + key),
                            )
                        }
                    }
                    if (type == "object") {
                        if ((value as JSONObject).length() == 0) {
                            throw Rejected(
                                "INVALID_INPUT",
                                "Bitte mindestens eine Änderung angeben.",
                            )
                        }
                        validate((value as JSONObject), spec)
                    }
                }
            }
            val required = schema.optJSONArray("required")
            run {
                var i: Int = 0
                while ((required != null) && (i < required.length())) {
                    val key = required.getString(i)
                    if (
                        (!(args.has(key)) ||
                            (args.get(key) is String && args.getString(key).trim().isEmpty()))
                    ) {
                        throw Rejected("MISSING_FIELD", ("Pflichtangabe fehlt: " + key))
                    }
                    i++
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun canonical(value: Any?): String {
            if (value is JSONObject) {
                val obj = (value as JSONObject)
                val keys = ArrayList<String>()
                obj.keys().forEachRemaining(keys::add)
                Collections.sort(keys)
                var out = StringJoiner(",", "{", "}")
                for (key in keys) {
                    out.add(
                        ((java.lang.String.valueOf(JSONObject.quote(key)) + ":") +
                            canonical(obj.get(key)))
                    )
                }
                return out.toString()
            }
            if (value is JSONArray) {
                var out = StringJoiner(",", "[", "]")
                val a = (value as JSONArray)
                for (i in 0 until a.length()) {
                    out.add(canonical(a.get(i)))
                }
                return out.toString()
            }
            if ((value == null) || (value == JSONObject.NULL)) {
                return "null"
            }
            if (value is Number) {
                return java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            }
            return (if (value is String) JSONObject.quote(value as String) else value.toString())
        }

        @JvmStatic
        @Throws(Exception::class)
        fun hash(value: Any?): String {
            var digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(canonical(value).toByteArray(StandardCharsets.UTF_8))
            var out = StringBuilder()
            for (b in digest) {
                out.append(String.format(Locale.ROOT, "%02x", (b.toInt() and 255)))
            }
            return out.toString()
        }

        @JvmStatic
        @Throws(Exception::class)
        fun base(row: JSONObject): JSONObject {
            val result = JSONObject()
            for (key in BASE_FIELDS) {
                if (row.has(key)) {
                    result.put(key, row.get(key))
                }
            }
            return result
        }

        @JvmStatic
        @Throws(Exception::class)
        fun applyMaterialCost(payload: JSONObject): Unit {
            if (!(payload.has("material_cost"))) {
                return
            }
            val material =
                payload
                    .optString("material")
                    .replace(Regex("(?m)^Materialkosten \\[EUR\\]: [0-9]+(?:\\.[0-9]{1,2})?\$"), "")
                    .trim()
            if (payload.isNull("material_cost") || payload.optString("material_cost").isBlank()) {
                payload.put("material", material).put("material_cost", JSONObject.NULL)
                return
            }
            var cost =
                java.math.BigDecimal(
                    payload.get("material_cost").toString().trim().replace(',', '.')
                )
            if ((cost.scale() > 2) || (cost.signum() < 0)) {
                throw Rejected(
                    "INVALID_INPUT",
                    "Materialkosten müssen nicht negativ sein und höchstens zwei Nachkommastellen haben.",
                )
            }
            payload.put(
                "material",
                ((java.lang.String.valueOf(if (material.isEmpty()) "" else (material + "\n")) +
                    "Materialkosten [EUR]: ") + cost.setScale(2).toPlainString()),
            )
            payload.remove("material_cost")
        }

        @JvmStatic
        @Throws(Exception::class)
        fun find(rows: JSONArray?, id: String): JSONObject {
            run {
                var i: Int = 0
                while ((rows != null) && (i < rows.length())) {
                    if (id == rows.getJSONObject(i).optString("id")) {
                        return rows.getJSONObject(i)
                    }
                    i++
                }
            }
            throw Rejected(
                "REFERENCE_NOT_FOUND",
                "Die Datensatz-ID existiert nicht. Bitte den Kunden, Auftrag oder Eintrag zuerst suchen.",
            )
        }

        @JvmStatic
        @Throws(Exception::class)
        fun prepare(
            name: String,
            args: JSONObject,
            state: JSONObject,
            actionId: String,
        ): JSONObject {
            validate(args, schema(name))
            val master = PixelMcpReadTools.effective(state)
            var payload: JSONObject
            val update: Boolean = (name == "update_work_record")
            if (update) {
                val capabilities = master.optJSONObject("capabilities")
                if (
                    ((capabilities == null) ||
                        !(true == capabilities.opt("worklog_compare_and_save")))
                ) {
                    throw Rejected(
                        "REMOTE_GUARD_REQUIRED",
                        "Der gemeinsame Änderungsschutz fehlt im Gesamtstand. Bitte Pixel-Datenstand und App-Version prüfen.",
                    )
                }
                val old = find(master.optJSONArray("worklogs"), args.getString("work_record_id"))
                if (!(hash(base(old)) == args.getString("expected_record_hash"))) {
                    throw Rejected(
                        "WRITE_CONFLICT",
                        "Der Eintrag wurde inzwischen geändert. Bitte erneut öffnen und die Änderung prüfen.",
                    )
                }
                val patch = args.getJSONObject("updates")
                var invoiced: Boolean = (old.optInt("billed", 0) != 0)
                val invoices = master.optJSONArray("invoices")
                run {
                    var i: Int = 0
                    while ((invoices != null) && (i < invoices.length())) {
                        val inv = invoices.getJSONObject(i)
                        val ids = inv.optJSONArray("worklog_ids")
                        run {
                            var j: Int = 0
                            while ((ids != null) && (j < ids.length())) {
                                if (old.getString("id") == ids.optString(j)) {
                                    invoiced = true
                                }
                                j++
                            }
                        }
                        i++
                    }
                }
                if (invoiced) {
                    run {
                        val it = patch.keys()
                        while (it.hasNext()) {
                            if (!(it.next() == "notes")) {
                                throw Rejected(
                                    "BILLED_RECORD_LOCKED",
                                    "Abgerechnete Leistungsdaten bleiben unverändert. Nur interne Notizen können ergänzt werden.",
                                )
                            }
                        }
                    }
                }
                payload = JSONObject(old.toString())
                run {
                    val it = patch.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        payload.put(key, patch.get(key))
                    }
                }
                payload.remove("_mcp_create")
                payload.put("_mcp_base", base(old))
            } else {
                payload = JSONObject(args.toString())
                payload.remove("request_id")
                val orderId = payload.optString("order_id").trim()
                var customerId = payload.optString("customer_id").trim()
                if (!(orderId.isEmpty())) {
                    var order = find(master.optJSONArray("orders"), orderId)
                    if (
                        (!(customerId.isEmpty()) && !(customerId == order.optString("customer_id")))
                    ) {
                        throw Rejected(
                            "REFERENCE_MISMATCH",
                            "Kunde und Auftrag passen nicht zusammen.",
                        )
                    }
                    customerId = order.optString("customer_id")
                    payload.put("customer_id", customerId)
                }
                payload
                    .put("id", ("wrk_mobile_mcp_" + hash(actionId).substring(0, 32)))
                    .put("order_id", orderId)
                    .put("billed", 0)
                    .put("source", "ChatGPT Sprache")
                    .put("_mcp_create", true)
                val existing = master.optJSONArray("worklogs")
                run {
                    var i: Int = 0
                    while ((existing != null) && (i < existing.length())) {
                        if (
                            (payload.getString("id") == existing.getJSONObject(i).optString("id"))
                        ) {
                            throw Rejected(
                                "ID_ALREADY_EXISTS",
                                "Diese Anfrage gehört zu einem bereits vorhandenen Eintrag. Bitte den Eintrag prüfen statt ihn erneut anzulegen.",
                            )
                        }
                        i++
                    }
                }
            }
            val customerId = payload.optString("customer_id").trim()
            var orderId =
                (if (payload.isNull("order_id")) "" else payload.optString("order_id").trim())
            payload.put("order_id", orderId)
            find(master.optJSONArray("customers"), customerId)
            if (!(orderId.isEmpty())) {
                var order = find(master.optJSONArray("orders"), orderId)
                if (!(customerId == order.optString("customer_id"))) {
                    throw Rejected("REFERENCE_MISMATCH", "Kunde und Auftrag passen nicht zusammen.")
                }
                if (payload.optString("location").trim().isEmpty()) {
                    payload.put("location", order.optString("location"))
                }
            }
            if (payload.optString("activity").trim().isEmpty()) {
                throw Rejected("MISSING_FIELD", "Die Tätigkeit fehlt.")
            }
            if (payload.has("customer_visible") && payload.get("customer_visible") is Boolean) {
                payload.put(
                    "customer_visible",
                    (if (payload.getBoolean("customer_visible")) 1 else 0),
                )
            }
            BillingRules.work(
                payload,
                (if (update) find(master.optJSONArray("worklogs"), payload.getString("id"))
                else null),
                master,
            )
            applyMaterialCost(payload)
            WorklogRules.validate(payload, true)
            val submitted = (if (update) args.getJSONObject("updates") else args)
            if (
                (((submitted.has("hours") && !(payload.isNull("start_time"))) &&
                    !(payload.optString("start_time").isEmpty())) &&
                    (Math.abs(submitted.getDouble("hours") - payload.getDouble("hours")) > 0.005))
            ) {
                throw Rejected(
                    "TIME_MISMATCH",
                    "Die Stunden passen nicht zu Beginn, Ende und Pause. Bitte die Zeitangaben prüfen.",
                )
            }
            if (!(update)) {
                val logs = master.optJSONArray("worklogs")
                run {
                    var i: Int = 0
                    while ((logs != null) && (i < logs.length())) {
                        val row = logs.getJSONObject(i)
                        if (
                            ((((customerId == row.optString("customer_id")) &&
                                (payload.getString("work_date") == row.optString("work_date"))) &&
                                (Math.abs(
                                    (payload.optDouble("hours") - row.optDouble("hours", -1.0))
                                ) < 0.005)) &&
                                payload
                                    .getString("activity")
                                    .trim()
                                    .equals(row.optString("activity").trim(), ignoreCase = true))
                        ) {
                            throw Rejected(
                                "POSSIBLE_DUPLICATE",
                                "Eine gleiche Arbeitsaufzeichnung ist bereits vorhanden. Bitte zuerst prüfen.",
                            )
                        }
                        i++
                    }
                }
            }
            if (
                ((((!(update) || submitted.has("work_date")) || submitted.has("start_time")) ||
                    submitted.has("end_time")) || submitted.has("hours"))
            ) {
                PixelMcpBusinessTools.preventOverlap(payload, master.optJSONArray("worklogs"))
            }
            return payload
        }
    }
}
