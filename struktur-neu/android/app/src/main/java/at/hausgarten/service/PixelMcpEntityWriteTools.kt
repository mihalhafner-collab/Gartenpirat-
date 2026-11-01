package at.hausgarten.service

import java.util.*
import org.json.*

internal class PixelMcpEntityWriteTools {
    companion object {
        @JvmField
        val NAMES: Array<String> =
            arrayOf(
                "create_customer",
                "update_customer",
                "create_order",
                "update_order",
                "create_invoice_draft",
                "update_invoice_draft",
            )
        @JvmField val CUSTOMER_FIELDS: Array<String> = EntityContract.CUSTOMER_FIELDS
        @JvmField val ORDER_FIELDS: Array<String> = EntityContract.ORDER_FIELDS
        @JvmField val INVOICE_FIELDS: Array<String> = EntityContract.INVOICE_FIELDS

        @JvmStatic
        fun handles(name: String): Boolean {

            return NAMES.asList().contains(name)
        }

        private @JvmStatic @Throws(Exception::class) fun string(max: Int): JSONObject {

            return JSONObject().put("type", "string").put("maxLength", max)
        }

        private @JvmStatic @Throws(Exception::class) fun number(): JSONObject {

            return JSONObject().put("type", "number").put("minimum", 0)
        }

        private @JvmStatic @Throws(Exception::class) fun integer(): JSONObject {

            return JSONObject().put("type", "integer").put("minimum", 0)
        }

        private @JvmStatic @Throws(Exception::class) fun bool(): JSONObject {

            return JSONObject().put("type", "boolean")
        }

        private @JvmStatic @Throws(Exception::class) fun field(key: String): JSONObject {

            if (
                Arrays.asList(
                        "active",
                        "customer_visible",
                        "domestic_service_confirmed",
                        "test_record",
                    )
                    .contains(key)
            ) {
                return bool()
            }
            if (("garden_area" == key)) {
                return number().put("type", JSONArray().put("number").put("null"))
            }
            if (("custom_fields" == key)) {
                return JSONObject()
                    .put("type", "object")
                    .put("maxProperties", 50)
                    .put(
                        "additionalProperties",
                        JSONObject()
                            .put(
                                "type",
                                JSONArray().put("string").put("number").put("boolean").put("null"),
                            )
                            .put("maxLength", 2000),
                    )
                    .put(
                        "description",
                        "Vorhandene frei benannte Kunden-Zusatzfelder. Änderungen werden mit bestehenden Feldern zusammengeführt; nicht genannte Felder bleiben erhalten. Werte sind Text, Zahl, Ja/Nein oder null.",
                    )
            }
            if (("billing_mode" == key)) {
                return string(30).put("enum", JSONArray().put("direct").put("via_customer"))
            }
            if (("payment_days" == key)) {
                return integer()
            }
            if (("positions" == key)) {
                var p = JSONObject()
                for (k in arrayOf("title", "description", "unit", "worklog_id")) {
                    p.put(k, string(10000))
                }
                for (k in
                    arrayOf(
                        "quantity",
                        "price",
                        "tax_rate",
                        "discount",
                        "net",
                        "tax",
                        "net_total",
                        "tax_total",
                        "gross_total",
                    )) {
                    p.put(k, JSONObject().put("type", "number"))
                }
                p.put(
                        "source_type",
                        McpFields.enumeration("worklog", "material_movement", "manual"),
                    )
                    .put("source_id", McpFields.id())
                    .put(
                        "source_quantity_decimal",
                        string(64).put("pattern", "(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"),
                    )
                return JSONObject()
                    .put("type", "array")
                    .put("minItems", 1)
                    .put("maxItems", 200)
                    .put("items", McpFields.`object`(p, "title", "quantity", "price"))
            }
            if (("recipient_type" == key)) {
                return string(30).put("enum", JSONArray().put("b2b").put("consumer").put(""))
            }
            if (("tax_mode" == key)) {
                return string(30).put("enum", JSONArray().put("small_business").put("standard"))
            }
            return string((if (Arrays.asList("notes", "description").contains(key)) 10000 else 500))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String): JSONObject {

            var props = JSONObject().put("request_id", string(128).put("minLength", 8))
            var required = JSONArray().put("request_id")
            var update: Boolean = name.startsWith("update_")
            var entity =
                (if (name.contains("customer")) "customer"
                else (if (name.contains("order")) "order" else "invoice"))
            var idKey = (entity + "_id")
            var fields =
                (if ((entity == "customer")) CUSTOMER_FIELDS
                else (if ((entity == "order")) ORDER_FIELDS else INVOICE_FIELDS))
            if (update) {
                props.put(idKey, string(500)).put("expected_record_hash", string(128))
                var patch = JSONObject()
                for (key in fields) {
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
                required.put(idKey).put("expected_record_hash").put("updates")
            } else {
                for (key in fields) {
                    props.put(key, field(key))
                }
                if ((entity == "customer")) {
                    required.put("name")
                }
                if ((entity == "order")) {
                    required.put("customer_id").put("title")
                }
                if ((entity == "invoice")) {
                    required.put("customer_id").put("invoice_date").put("positions")
                }
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

            var out = JSONArray()
            for (name in NAMES) {
                var description =
                    (if (name.contains("invoice"))
                        "Erstellt oder ändert ausschließlich einen lokalen Rechnungsentwurf. Dieses Werkzeug stellt keine Rechnung aus, versendet nichts und markiert nichts als bezahlt. Vorhandene sevDesk-Rechnungen bleiben unverändert."
                    else
                        (if ((name == "create_customer"))
                            "Legt einen neuen Kunden mit Stammdaten und Zusatzfeldern an. Vorher Kunden einschließlich inaktiver Kunden suchen; mögliche Dubletten werden abgewiesen. Kunden-ID wird sicher auf dem Pixel erzeugt."
                        else
                            "Erstellt oder ändert einen Auftrag mit einer tatsächlich vorhandenen Kunden-ID. Vor Änderungen Datensatz abrufen und record_hash übernehmen."))
                if ((name == "update_customer")) {
                    description =
                        "Korrigiert ausdrücklich genannte Stammdaten eines vorhandenen Kunden anhand customer_id. Zuerst get_customer aufrufen und dessen record_hash als expected_record_hash übernehmen. Nicht genannte Felder, IDs, Zeitstempel und Verknüpfungen bleiben erhalten. Zusatzfelder werden ergänzt, nicht blind ersetzt."
                }
                out.put(
                    JSONObject()
                        .put("name", name)
                        .put(
                            "description",
                            (description + " request_id bei Wiederholungen unverändert lassen."),
                        )
                        .put("inputSchema", schema(name))
                        .put(
                            "annotations",
                            JSONObject()
                                .put("readOnlyHint", false)
                                .put("destructiveHint", name.startsWith("update_"))
                                .put("idempotentHint", true)
                                .put("openWorldHint", false),
                        )
                )
            }
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validate(name: String, args: JSONObject): Unit {

            validateObject(args, schema(name))
        }

        private @JvmStatic @Throws(Exception::class) fun validateObject(
            args: JSONObject,
            schema: JSONObject,
        ): Unit {

            var props = schema.getJSONObject("properties")
            run {
                var it = args.keys()
                var firstIteration87 = true
                while (true) {
                    if (firstIteration87) firstIteration87 = false else {}
                    if (!(it.hasNext())) break

                    var key = it.next()
                    if (!(props.has(key))) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            ("Diese Angabe ist nicht erlaubt: " + key),
                        )
                    }
                    var value = args.get(key)
                    var spec = props.getJSONObject(key)
                    var declared = spec.get("type")
                    var type =
                        (if (declared is JSONArray) ((declared as JSONArray)).getString(0)
                        else (declared as String))
                    if (((value == JSONObject.NULL) && declared is JSONArray)) {
                        var nullable: Boolean = false
                        var types = (declared as JSONArray)
                        run {
                            var i: Int = 0
                            var firstIteration86 = true
                            while (true) {
                                if (firstIteration86) firstIteration86 = false
                                else {
                                    i++
                                }
                                if (!((i < types.length()))) break

                                if (("null" == types.optString(i))) {
                                    nullable = true
                                }
                            }
                        }
                        if (nullable) {
                            continue
                        }
                    }
                    var valid: Boolean =
                        (if ((type == "string")) value is String
                        else
                            (if ((type == "boolean")) value is Boolean
                            else
                                (if ((type == "object")) value is JSONObject
                                else
                                    (if ((type == "array")) value is JSONArray
                                    else value is Number))))
                    if (!(valid)) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            ("Ungültiger Eingabetyp: " + key),
                        )
                    }
                    if (
                        (value is String &&
                            (((((value as String)).length > spec.optInt("maxLength", 10000)) ||
                                (((value as String)).length < spec.optInt("minLength", 0)))))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            ("Bitte die Länge der Angabe prüfen: " + key),
                        )
                    }
                    if (value is Number) {
                        var n: Double = ((value as Number)).toDouble()
                        if (
                            ((!(java.lang.Double.isFinite(n)) || (n < 0)) ||
                                (((type == "integer") && (n != Math.floor(n)))))
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "INVALID_INPUT",
                                ("Bitte eine gültige nicht negative Zahl eingeben: " + key),
                            )
                        }
                    }
                    if ((type == "object")) {
                        if ((key == "custom_fields")) {
                            EntityContract.validateCustomFields((value as JSONObject))
                        } else {
                            if ((((value as JSONObject)).length() == 0)) {
                                throw PixelMcpWriteTools.Rejected(
                                    "INVALID_INPUT",
                                    "Bitte mindestens eine Änderung angeben.",
                                )
                            }
                            validateObject((value as JSONObject), spec)
                        }
                    }
                    if ((type == "array")) {
                        var length: Int = ((value as JSONArray)).length()
                        if (
                            ((length < spec.optInt("minItems", 0)) ||
                                (length > spec.optInt("maxItems", 200)))
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "INVALID_INPUT",
                                "Bitte die Anzahl der Rechnungspositionen prüfen.",
                            )
                        }
                    }
                }
            }
            var required = schema.optJSONArray("required")
            run {
                var i: Int = 0
                var firstIteration88 = true
                while (true) {
                    if (firstIteration88) firstIteration88 = false
                    else {
                        i++
                    }
                    if (!(((required != null) && (i < required.length())))) break

                    var key = required.getString(i)
                    if (
                        (!(args.has(key)) ||
                            ((args.opt(key) is String && args.optString(key).trim().isEmpty())))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "MISSING_FIELD",
                            ("Pflichtangabe fehlt: " + key),
                        )
                    }
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun prepare(
            name: String,
            args: JSONObject,
            state: JSONObject,
            actionId: String,
        ): JSONObject {

            validate(name, args)
            return EntityApplicationService.prepare(name, args, state, actionId)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validatePositions(positions: JSONArray): Unit {

            EntityContract.validatePositions(positions)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun recordForHash(entity: String, row: JSONObject): JSONObject {

            return EntityContract.editable(
                row,
                (if ((entity == "customer")) CUSTOMER_FIELDS
                else (if ((entity == "order")) ORDER_FIELDS else INVOICE_FIELDS)),
            )
        }
    }
}
