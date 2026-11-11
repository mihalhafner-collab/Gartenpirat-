package at.hausgarten.service

import java.util.*
import org.json.*

internal class PixelMcpEntityWriteTools {
    companion object {
        @JvmField
        val NAMES: Array<String> =
            arrayOf("create_customer", "update_customer", "create_order", "update_order")
        @JvmField val CUSTOMER_FIELDS: Array<String> = EntityContract.CUSTOMER_FIELDS
        @JvmField val ORDER_FIELDS: Array<String> = EntityContract.ORDER_FIELDS

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

        @JvmStatic
        @Throws(Exception::class)
        fun field(key: String): JSONObject {
            if (
                Arrays.asList(
                        "active",
                        "customer_visible",
                        "domestic_service_confirmed",
                        "test_record",
                        "electronic_invoice_consent",
                    )
                    .contains(key)
            ) {
                return bool()
            }
            if ("garden_area" == key) {
                return number().put("type", JSONArray().put("number").put("null"))
            }
            if ("custom_fields" == key) {
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
            if ("billing_mode" == key) {
                return string(30).put("enum", JSONArray().put("direct").put("via_customer"))
            }
            return string(if (Arrays.asList("notes", "description").contains(key)) 10000 else 500)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String): JSONObject {
            require(handles(name)) { "UNKNOWN_TOOL" }

            val props = JSONObject().put("request_id", string(128).put("minLength", 8))
            val required = JSONArray().put("request_id")
            val update: Boolean = name.startsWith("update_")
            val entity = (if (name.contains("customer")) "customer" else "order")
            val idKey = (entity + "_id")
            val fields = (if (entity == "customer") CUSTOMER_FIELDS else ORDER_FIELDS)
            if (update) {
                props.put(idKey, string(500)).put("expected_record_hash", string(128))
                val patch = JSONObject()
                for (key in fields) {
                    if (key != "electronic_invoice_consent_date") patch.put(key, field(key))
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
                    if (key != "electronic_invoice_consent_date") props.put(key, field(key))
                }
                if (entity == "customer") {
                    required.put("name")
                }
                if (entity == "order") {
                    required.put("customer_id").put("title")
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
                    (if (name == "create_customer")
                        "Legt einen neuen Kunden mit Stammdaten und Zusatzfeldern an. Vorher Kunden einschließlich inaktiver Kunden suchen; mögliche Dubletten werden abgewiesen. Kunden-ID wird sicher auf dem Pixel erzeugt."
                    else
                        "Erstellt oder ändert einen Auftrag mit einer tatsächlich vorhandenen Kunden-ID. Vor Änderungen Datensatz abrufen und record_hash übernehmen.")
                if (name == "update_customer") {
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
            val props = schema.getJSONObject("properties")
            run {
                val it = args.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    if (!(props.has(key))) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            ("Diese Angabe ist nicht erlaubt: " + key),
                        )
                    }
                    var value = args.get(key)
                    val spec = props.getJSONObject(key)
                    val declared = spec.get("type")
                    var type =
                        (if (declared is JSONArray) (declared as JSONArray).getString(0)
                        else (declared as String))
                    if ((value == JSONObject.NULL) && declared is JSONArray) {
                        var nullable: Boolean = false
                        val types = (declared as JSONArray)
                        for (i in 0 until types.length()) {
                            if ("null" == types.optString(i)) {
                                nullable = true
                            }
                        }
                        if (nullable) {
                            continue
                        }
                    }
                    var valid: Boolean =
                        (if (type == "string") value is String
                        else
                            (if (type == "boolean") value is Boolean
                            else
                                (if (type == "object") value is JSONObject
                                else
                                    (if (type == "array") value is JSONArray
                                    else value is Number))))
                    if (!(valid)) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            ("Ungültiger Eingabetyp: " + key),
                        )
                    }
                    if (
                        (value is String &&
                            (((value as String).length > spec.optInt("maxLength", 10000)) ||
                                ((value as String).length < spec.optInt("minLength", 0))))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
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
                            throw PixelMcpWriteTools.Rejected(
                                "INVALID_INPUT",
                                ("Bitte eine gültige nicht negative Zahl eingeben: " + key),
                            )
                        }
                    }
                    if (type == "object") {
                        if (key == "custom_fields") {
                            EntityContract.validateCustomFields(value as JSONObject)
                        } else {
                            if ((value as JSONObject).length() == 0) {
                                throw PixelMcpWriteTools.Rejected(
                                    "INVALID_INPUT",
                                    "Bitte mindestens eine Änderung angeben.",
                                )
                            }
                            validateObject((value as JSONObject), spec)
                        }
                    }
                    if (type == "array") {
                        val length: Int = (value as JSONArray).length()
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
            val required = schema.optJSONArray("required")
            run {
                var i: Int = 0
                while ((required != null) && (i < required.length())) {
                    val key = required.getString(i)
                    if (
                        (!(args.has(key)) ||
                            (args.opt(key) is String && args.optString(key).trim().isEmpty()))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "MISSING_FIELD",
                            ("Pflichtangabe fehlt: " + key),
                        )
                    }
                    i++
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
        fun recordForHash(entity: String, row: JSONObject): JSONObject {
            return EntityContract.editable(
                row,
                (if (entity == "customer") CUSTOMER_FIELDS else ORDER_FIELDS),
            )
        }
    }
}
