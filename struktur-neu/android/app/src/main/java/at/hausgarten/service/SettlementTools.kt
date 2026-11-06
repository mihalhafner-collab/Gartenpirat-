package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** One explicit contract used by native Compose and MCP. No remote accounting service. */
internal object SettlementTools {
    val READS = arrayOf("list_settlements", "get_settlement", "get_settlement_context", "get_settlement_pdf", "export_settlements")
    val WRITES = arrayOf("save_settlement_draft", "prepare_settlement", "complete_settlement", "correct_settlement", "abandon_prepared_settlement", "add_settlement_payment", "review_restored_sources", "record_settlement_delivery")
    private fun string() = JSONObject().put("type", "string").put("maxLength", 20000)
    private fun boolean() = JSONObject().put("type", "boolean")
    private fun obj(properties: JSONObject, vararg required: String) = JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray(required.toList())).put("additionalProperties", false)
    private fun array(items: JSONObject) = JSONObject().put("type", "array").put("items", items).put("maxItems", 500)
    private fun fields(vararg names: String) = JSONObject().apply { for (n in names) put(n, string()) }
    fun schema(name: String): JSONObject {
        val p = JSONObject(); val required = arrayListOf<String>()
        if (name in WRITES) { p.put("request_id", McpFields.text(128).put("minLength", 8)); required.add("request_id") }
        if (name in setOf("get_settlement", "get_settlement_pdf", "prepare_settlement", "complete_settlement", "correct_settlement", "abandon_prepared_settlement", "add_settlement_payment", "record_settlement_delivery")) { p.put("settlement_id", McpFields.id()); required.add("settlement_id") }
        if (name in setOf("correct_settlement", "abandon_prepared_settlement", "add_settlement_payment", "record_settlement_delivery")) { p.put("expected_record_hash", McpFields.hash()); required.add("expected_record_hash") }
        when (name) {
            "save_settlement_draft" -> {
                p.put("settlement_id", string()).put("expected_record_hash", McpFields.hash())
                val draft = fields("route", "title", "customer_id", "billing_customer_id", "property_id", "service_from", "service_to", "payment_terms", "payment_details", "replacement_for")
                draft.put("positions", array(obj(fields("title", "description", "quantity", "unit", "price", "source_collection", "source_id"))))
                p.put("draft", obj(draft, "route")); required.add("draft")
            }
            "prepare_settlement" -> {
                p.put("expected_review_hash", McpFields.hash()); required.add("expected_review_hash")
                for (key in listOf("confirmed", "domestic_eur_confirmed", "issuer_details_confirmed", "electronic_delivery_accepted")) { p.put(key, boolean()); required.add(key) }
            }
            "abandon_prepared_settlement" -> { p.put("reason", string()).put("confirmed", boolean()); required.addAll(listOf("reason", "confirmed")) }
            "correct_settlement" -> { p.put("reason", string()).put("access_evidence", string()).put("confirmed", boolean()); required.addAll(listOf("reason", "confirmed")) }
            "add_settlement_payment" -> { for (key in listOf("amount", "date", "note", "reversal_of")) p.put(key, string()); required.addAll(listOf("amount", "date")) }
            "record_settlement_delivery" -> { p.put("date", string()).put("evidence", string()); required.addAll(listOf("date", "evidence")) }
            "review_restored_sources" -> { p.put("settlement_id", string()).put("sources", array(obj(fields("collection", "id"), "collection", "id"))).put("evidence", string()).put("confirmed", boolean()); required.addAll(listOf("sources", "evidence", "confirmed")) }
            "export_settlements" -> for (key in listOf("query", "date_from", "date_to", "property_id", "route")) p.put(key, string())
        }
        return obj(p, *required.toTypedArray())
    }
    fun description(name: String) = when (name) {
        "abandon_prepared_settlement" -> "Hebt ausdrücklich nur eine noch nicht abgeschlossene Vorbereitung mit Begründung auf. Reservierte Nummer und eingefrorene Historie bleiben erhalten und werden nie wiederverwendet. Kein künstlicher Stornobeleg. Bestätigte Belege nur über Storno oder interne Korrektur berichtigen; restaurierte Vorbereitungen zuvor aktuell abgleichen."
        "prepare_settlement" -> "Reserviert nach bewusster Vorschauprüfung eingefrorene tatsächliche Leistungen, aktive Quellenbindungen und bei Rechnung eine eigene lokale Nummer. Noch kein abgeschlossener Beleg. Gewöhnlicher österreichischer EUR-Inlandsfall, Kleinunternehmerbefreiung und rechtsformabhängige Ausstellerangaben bewusst prüfen. Danach complete_settlement mit derselben settlement_id und eigener stabiler request_id fortsetzen."
        "complete_settlement" -> "Setzt einen dauerhaft vorbereiteten Vorgang fort. Rechnung erst nach geprüftem unveränderlichem Original-PDF abschließen; Labor intern ohne Rechnung oder Nummer bestätigen. PDF-Fehler bewahren die Reservierung. Keine Änderung der Benutzerzuordnung."
        "correct_settlement" -> "Vollständiges Storno mit eigener Nummer und Originalverweis vorbereiten, anschließend complete_settlement. Labor intern nachvollziehbar aufheben ohne Rechnungsbeleg. Zahlungen bleiben erhalten; keine behauptete Rückzahlung. Vorhandene Zugangsnachweise berücksichtigen."
        "add_settlement_payment" -> "Einzelnen tatsächlichen Zahlungseingang oder begründete negative Gegenbuchung mit Datum erfassen. Teil-/Überzahlung bleibt nachvollziehbar. Persönliche Markierungen sind keine Zahlungen. Kein Ersatz für eine Registrierkasse."
        "review_restored_sources" -> "Dokumentiert nach Restore die bewusste Prüfung des aktuellen Belegstands genau dieser Quellen. Neue Serie rekonstruiert keine fehlenden Belege oder Zahlungen. Nicht ohne tatsächlichen Abgleich bestätigen."
        "record_settlement_delivery" -> "Erfasst einen tatsächlichen Zugangsnachweis. Android-Teilen allein ist weder Versand- noch Zugangsnachweis."
        "export_settlements" -> "Liest vollständig nachvollziehbare lokale Rechnungs- und Laborvorgänge, Korrekturen, Zahlungen und getrennte Summen/Gesamtsumme. Filter werden ausdrücklich bezeichnet; Original-Metadaten dienen dem lokalen ZIP-Export. Keine garantierte Rechtskonformität durch PDF, Hash oder ZIP."
        else -> "Lokale Rechnungs- und Laborabrechnung: tatsächliche Leistungen, Mengen und bestätigte Preise; keine automatische Angebotsübernahme. Entwürfe dürfen unvollständig sein. Beide Abrechnungsarten, Zuordnung, Belegstatus und Zahlungsstand bleiben getrennt. request_id bei Wiederholung unverändert lassen."
    }
    fun query(name: String, a: JSONObject, repository: OwnerRepository): JSONObject {
        McpSchema.validate(a, schema(name))
        val ledger = SettlementRules.ledger(repository)
        val master = repository.effective()
        if (name == "get_settlement_context") return JSONObject().put("business_settings", master.optJSONObject("business_settings") ?: JSONObject())
            .put("customers", master.optJSONArray("customers") ?: JSONArray()).put("orders", master.optJSONArray("orders") ?: JSONArray())
            .put("worklogs", master.optJSONArray("worklogs") ?: JSONArray()).put("restored_sources", ledger.optJSONArray("restored_sources") ?: JSONArray())
            .put("sources_requiring_review", JSONArray((SettlementRules.rows(ledger.optJSONArray("restored_sources")) + SettlementRules.externalSources(master)).distinctBy(SettlementRules::sourceKey)))
        if (name == "list_settlements") return JSONObject().put("records", ledger.optJSONArray("records") ?: JSONArray())
        if (name == "get_settlement" || name == "get_settlement_pdf") {
            val row = SettlementRules.find(ledger, a.getString("settlement_id"))
            if (name == "get_settlement_pdf") {
                require(row.getString("route") == "billed" && row.has("pdf")) { "Für diesen Vorgang gibt es kein Original-PDF." }
                repository.readDocument(row.getJSONObject("pdf"))
                return JSONObject().put("metadata", row.getJSONObject("pdf"))
            }
            val review = SettlementRules.review(row, master)
            val paid = SettlementRules.paid(ledger, row.getString("id"))
            val due = if (row.optString("status") == "cancelled") 0L else row.optLong("total_minor")
            return JSONObject().put("record", review).put("record_hash", SettlementRules.hash(row)).put("review_hash", SettlementRules.hash(review))
                .put("requires_restore_review", SettlementApplicationService.requiresRestoreReview(ledger, row, repository.settlementRestoreEpoch()))
                .put("validation", if (row.getString("status") == "draft") SettlementRules.validation(review, master, ledger, AccountingRules.ids(JSONArray(repository.ownerValue("personal_paid", "[]")))) else JSONObject().put("valid", true).put("errors", JSONArray()))
                .put("payments", JSONArray(SettlementRules.rows(ledger.optJSONArray("payments")).filter { it.optString("settlement_id") == row.getString("id") }))
                .put("delivery_evidence", JSONArray(SettlementRules.rows(ledger.optJSONArray("delivery_evidence")).filter { it.optString("settlement_id") == row.getString("id") }))
                .put("paid_minor", paid).put("balance_minor", Math.subtractExact(due, paid))
        }
        throw IllegalArgumentException("Abrechnungsabfrage fehlt.")
    }
}
