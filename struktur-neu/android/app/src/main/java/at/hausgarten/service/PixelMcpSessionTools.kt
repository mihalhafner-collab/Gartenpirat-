package at.hausgarten.service

import java.time.*
import java.util.*
import org.json.*

internal class PixelMcpSessionTools {
    companion object {
        @JvmField
        val NAMES: Array<String> =
            arrayOf(
                "finish_work",
                "start_work",
                "start_break",
                "end_break",
                "stop_work",
                "complete_work_session",
                "continue_work",
                "discard_work_session",
                "restore_work_session",
                "review_work_session",
            )

        @JvmStatic
        fun handles(name: String): Boolean {
            return NAMES.asList().contains(name)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(name: String): JSONObject {
            var props =
                JSONObject()
                    .put(
                        "request_id",
                        PixelMcpWriteTools.field("request_id")
                            .put("minLength", 8)
                            .put("maxLength", 128),
                    )
            val required = JSONArray().put("request_id")
            if (name == "start_work") {
                for (key in
                    arrayOf(
                        "customer_id",
                        "billing_customer_id",
                        "order_id",
                        "appointment_id",
                        "work_date",
                        "activity",
                        "location",
                    )) {
                    props.put(key, PixelMcpWriteTools.field(key))
                }
                required.put("customer_id")
                props.put("property_id", McpFields.id()).put("visit_id", McpFields.id())
            } else {
                props.put("session_id", PixelMcpWriteTools.field("session_id"))
                required.put("session_id")
            }
            if ((name == "complete_work_session") || (name == "finish_work")) {
                for (key in
                    arrayOf(
                        "customer_id",
                        "billing_customer_id",
                        "activity",
                        "material",
                        "notes",
                        "location",
                        "material_cost",
                    )) {
                    props.put(key, PixelMcpWriteTools.field(key))
                }
                required.put("activity")
            }
            if (name == "review_work_session") {
                props.put(
                    "expected_revision_hash",
                    JSONObject().put("type", "string").put("pattern", "[0-9a-f]{64}"),
                )
                props.put(
                    "worked_minutes",
                    JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 10080),
                )
                props.put(
                    "break_minutes",
                    JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 10080),
                )
                props.put(
                    "reason",
                    JSONObject().put("type", "string").put("minLength", 1).put("maxLength", 4000),
                )
                props.put(
                    "confirmed",
                    JSONObject().put("type", "boolean").put("enum", JSONArray().put(true)),
                )
                props.put("allow_overnight", JSONObject().put("type", "boolean"))
                for (key in
                    arrayOf(
                        "expected_revision_hash",
                        "worked_minutes",
                        "break_minutes",
                        "reason",
                        "confirmed",
                    )) {
                    required.put(key)
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
                out.put(
                    JSONObject()
                        .put("name", name)
                        .put("inputSchema", schema(name))
                        .put(
                            "description",
                            "Bedient die vorhandene dauerhafte Pixel-Zeiterfassung. Vor Änderungen get_active_work_session lesen. request_id bei Wiederholung gleich lassen. start_work verlangt einen echten Kunden; Datum sonst heute in Europe/Vienna. stop_work stoppt nur die Sitzung, erzeugt noch keine Arbeitsaufzeichnung. complete_work_session übernimmt die geprüfte gestoppte Sitzung in den vorhandenen mobilen Abgleich; Nettozeit kommt ausschließlich aus dem Timer. finish_work stoppt und speichert dieselbe echte Sitzung atomar anhand session_id und Tätigkeit; Validierungsfehler erhalten den vorherigen Timerzustand. Keine Rechnungsänderung.",
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
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun prepare(name: String, args: JSONObject, state: JSONObject): JSONObject {
            PixelMcpWriteTools.validate(args, schema(name))
            var current = state.optJSONObject("work_session")
            var out = JSONObject(args.toString())
            if (current == null) {
                throw PixelMcpWriteTools.Rejected(
                    "SESSION_UNAVAILABLE",
                    "Die aktuelle Zeiterfassung ist nicht lesbar. Bitte die App prüfen.",
                )
            }
            if (name == "start_work") {
                if (!("IDLE" == current.optString("state"))) {
                    return out
                }
                val master = PixelMcpReadTools.effective(state)
                PixelMcpWriteTools.find(
                    master.optJSONArray("customers"),
                    out.getString("customer_id"),
                )
                var order = out.optString("order_id")
                if (!(order.isEmpty())) {
                    val row = PixelMcpWriteTools.find(master.optJSONArray("orders"), order)
                    if (!(row.optString("customer_id") == out.getString("customer_id"))) {
                        throw PixelMcpWriteTools.Rejected(
                            "REFERENCE_MISMATCH",
                            "Kunde und Auftrag passen nicht zusammen.",
                        )
                    }
                    if (out.optString("location").isEmpty()) {
                        out.put("location", row.optString("location"))
                    }
                }
                BillingRules.work(out, null, master)
                if (!(out.has("work_date"))) {
                    out.put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                }
                LocalDate.parse(out.getString("work_date"))
                return out
            }
            if (name == "restore_work_session") {
                if (!("IDLE" == current.optString("state"))) {
                    throw PixelMcpWriteTools.Rejected(
                        "SESSION_CONFLICT",
                        "Eine andere Sitzung ist aktiv.",
                    )
                }
                return out
            }
            if (!(args.getString("session_id") == current.optString("id"))) {
                throw PixelMcpWriteTools.Rejected(
                    "SESSION_CONFLICT",
                    "Die Sitzung ist nicht mehr die aktuelle Sitzung. Bitte erneut abfragen.",
                )
            }
            if (name == "review_work_session") {
                return out
            }
            if (name == "discard_work_session") {
                if (
                    !(Arrays.asList("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")
                        .contains(current.optString("state")))
                ) {
                    throw PixelMcpWriteTools.Rejected(
                        "SESSION_CONFLICT",
                        "Diese Sitzung kann nicht verworfen werden.",
                    )
                }
                return out
            }
            var expected =
                (if (name == "continue_work") "STOPPED_PENDING_REVIEW"
                else
                    (if (name == "start_break") "RUNNING"
                    else
                        (if (name == "end_break") "PAUSED"
                        else
                            (if ((name == "complete_work_session") || (name == "finish_work"))
                                "STOPPED_PENDING_REVIEW"
                            else ""))))
            if (
                ((!(expected.isEmpty()) && !(expected == current.optString("state"))) ||
                    ((name == "stop_work") &&
                        !(Arrays.asList("RUNNING", "PAUSED").contains(current.optString("state")))))
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "SESSION_CONFLICT",
                    "Diese Aktion passt nicht zum aktuellen Sitzungszustand.",
                )
            }
            if ((name == "complete_work_session") || (name == "finish_work")) {
                if (
                    (current.optBoolean("review_required") ||
                        current.optBoolean("pause_unverified"))
                ) {
                    throw PixelMcpWriteTools.Rejected(
                        "TIME_REVIEW_REQUIRED",
                        "Sitzung wegen Neustart oder widersprüchlicher Geräteuhr zuerst prüfen. Kein Arbeitsende geschätzt.",
                    )
                }
                val customer = current.optString("customer_id")
                if (
                    (!(out.optString("customer_id", customer) == customer) && !(customer.isEmpty()))
                ) {
                    throw PixelMcpWriteTools.Rejected(
                        "REFERENCE_MISMATCH",
                        "Die Kundenzuordnung der gestarteten Sitzung darf nicht still geändert werden.",
                    )
                }
                out.put("customer_id", out.optString("customer_id", customer))
                    .put("order_id", current.optString("order_id"))
                    .put("work_date", current.getString("work_date"))
                    .put("hours", current.getDouble("hours"))
                    .put("break_minutes", current.optInt("break_minutes", 0))
                    .put("billed", 0)
                    .put("customer_visible", 1)
                    .put("source", "ChatGPT Sprache")
                for (contextKey in arrayOf("property_id", "visit_id")) {
                    if (!(current.optString(contextKey).isEmpty())) {
                        out.put(contextKey, current.getString(contextKey))
                    }
                }
                if (current.has("started_at_ms")) {
                    var origin =
                        Instant.ofEpochMilli(current.getLong("started_at_ms"))
                            .atZone(ZoneId.of("Europe/Vienna"))
                            .toString()
                    var audit =
                        (((("Zeiterfassung: Beginn " + origin) + ", Ende ") +
                            current.optString("updated_at")) +
                            ". Nettozeit aus Timer; Pausen in Minuten aus Ereigniszeiten gerundet.")
                    if ("manual_review" == current.optString("time_origin")) {
                        audit =
                            ("Geprüfte Nettozeit; keine Endzeit rekonstruiert. Grund: " +
                                current.optString("correction_reason"))
                    }
                    out.put(
                        "notes",
                        (java.lang.String.valueOf(
                            (if (out.optString("notes").isEmpty()) ""
                            else (out.getString("notes") + "\n"))
                        ) + audit),
                    )
                }
                PixelMcpWriteTools.find(
                    PixelMcpReadTools.effective(state).optJSONArray("customers"),
                    out.getString("customer_id"),
                )
                if (
                    (!(out.has("billing_customer_id")) &&
                        !(current.optString("billing_customer_id").isEmpty()))
                ) {
                    out.put("billing_customer_id", current.getString("billing_customer_id"))
                }
                BillingRules.work(out, null, PixelMcpReadTools.effective(state))
                PixelMcpWriteTools.applyMaterialCost(out)
                WorklogRules.validate(out, true)
            }
            return out
        }
    }
}
