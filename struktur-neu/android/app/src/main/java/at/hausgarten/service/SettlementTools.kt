package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** One explicit contract used by native Compose and MCP. No remote accounting service. */
internal object SettlementTools {
    val READS =
        arrayOf(
            "list_settlements",
            "get_settlement",
            "get_settlement_context",
            "get_settlement_pdf",
            "export_settlements",
            "get_lcr_work_records",
            "get_finance_overview",
        )
    val WRITES =
        arrayOf(
            "save_settlement_draft",
            "delete_settlement_draft",
            "prepare_settlement",
            "complete_settlement",
            "correct_settlement",
            "abandon_prepared_settlement",
            "add_settlement_payment",
            "review_restored_sources",
            "record_settlement_delivery",
            "set_work_record_lcr",
        )

    private fun string() = JSONObject().put("type", "string").put("maxLength", 20000)

    private fun boolean() = JSONObject().put("type", "boolean")

    private fun obj(properties: JSONObject, vararg required: String) =
        JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required.toList()))
            .put("additionalProperties", false)

    private fun array(items: JSONObject) =
        JSONObject().put("type", "array").put("items", items).put("maxItems", 500)

    private fun fields(vararg names: String) =
        JSONObject().apply { for (n in names) put(n, string()) }

    fun schema(name: String): JSONObject {
        val p = JSONObject()
        val required = arrayListOf<String>()
        if (name in WRITES) {
            p.put("request_id", McpFields.text(128).put("minLength", 8))
            required.add("request_id")
        }
        if (
            name in
                setOf(
                    "get_settlement",
                    "get_settlement_pdf",
                    "delete_settlement_draft",
                    "prepare_settlement",
                    "complete_settlement",
                    "correct_settlement",
                    "abandon_prepared_settlement",
                    "add_settlement_payment",
                    "record_settlement_delivery",
                )
        ) {
            p.put("settlement_id", McpFields.id())
            required.add("settlement_id")
        }
        if (
            name in
                setOf(
                    "correct_settlement",
                    "delete_settlement_draft",
                    "abandon_prepared_settlement",
                    "add_settlement_payment",
                    "record_settlement_delivery",
                )
        ) {
            p.put("expected_record_hash", McpFields.hash())
            required.add("expected_record_hash")
        }
        when (name) {
            "get_lcr_work_records" ->
                p.put("work_record_id", McpFields.id())
                    .put("offset", McpFields.number(0, 1000000))
                    .put("limit", McpFields.number(1, 100))
                    .put("query", McpFields.text(200))
                    .put("customer_id", McpFields.id())
                    .put("date_from", McpFields.text(10))
                    .put("date_to", McpFields.text(10))
            "set_work_record_lcr" -> {
                p.put("work_record_id", McpFields.id())
                    .put("expected_record_hash", McpFields.hash())
                    .put("lcr", McpFields.bool())
                required.addAll(listOf("work_record_id", "expected_record_hash", "lcr"))
            }
            "get_finance_overview" -> {
                p.put("date_from", McpFields.text(10))
                    .put("date_to", McpFields.text(10))
                    .put("date_basis", McpFields.enumeration("service", "document", "payment"))
            }

            "get_settlement_pdf" -> p.put("preview", boolean())
            "delete_settlement_draft" -> {
                p.put("confirmed", McpFields.bool().put("enum", JSONArray().put(true)))
                required.add("confirmed")
            }
            "save_settlement_draft" -> {
                p.put("settlement_id", string()).put("expected_record_hash", McpFields.hash())
                val draft =
                    fields(
                        "route",
                        "title",
                        "customer_id",
                        "billing_customer_id",
                        "property_id",
                        "service_from",
                        "service_to",
                        "payment_terms",
                        "payment_details",
                        "replacement_for",
                        "billing_interval",
                    )
                draft.put(
                    "positions",
                    array(
                        obj(
                            fields(
                                "title",
                                "description",
                                "quantity",
                                "unit",
                                "price",
                                "source_collection",
                                "source_id",
                            )
                        )
                    ),
                )
                p.put("draft", obj(draft, "route"))
                required.add("draft")
            }
            "prepare_settlement" -> {
                p.put("expected_review_hash", McpFields.hash())
                required.add("expected_review_hash")
                for (key in
                    listOf(
                        "confirmed",
                        "domestic_eur_confirmed",
                        "issuer_details_confirmed",
                        "electronic_delivery_accepted",
                    )) {
                    p.put(key, boolean())
                    required.add(key)
                }
            }
            "abandon_prepared_settlement" -> {
                p.put("reason", string()).put("confirmed", boolean())
                required.addAll(listOf("reason", "confirmed"))
            }
            "correct_settlement" -> {
                p.put("reason", string())
                    .put("access_evidence", string())
                    .put("confirmed", boolean())
                required.addAll(listOf("reason", "confirmed"))
            }
            "add_settlement_payment" -> {
                for (key in listOf("amount", "date", "note", "reversal_of")) p.put(key, string())
                p.put("payment_kind", McpFields.enumeration("receipt", "correction", "refund"))
                p.put("expected_payment_hash", McpFields.hash())
                required.addAll(listOf("amount", "date"))
            }
            "record_settlement_delivery" -> {
                p.put("date", string()).put("evidence", string())
                required.addAll(listOf("date", "evidence"))
            }
            "review_restored_sources" -> {
                p.put("settlement_id", string())
                    .put("sources", array(obj(fields("collection", "id"), "collection", "id")))
                    .put("evidence", string())
                    .put("confirmed", boolean())
                required.addAll(listOf("sources", "evidence", "confirmed"))
            }
            "export_settlements" ->
                for (key in listOf("query", "date_from", "date_to", "property_id", "route")) p.put(
                    key,
                    string(),
                )
        }
        return obj(p, *required.toTypedArray())
    }

    fun description(name: String) =
        when (name) {
            "get_lcr_work_records" ->
                "Liest Originalarbeiten mit L.C.R.-Kennzeichnung, lcr_record_hash und Belegbindung. Ohne work_record_id nur gekennzeichnete Originale; mit ID genau diese Originalarbeit auch ohne Kennzeichen. Historische Labor-Abschlüsse bleiben unverändert."
            "set_work_record_lcr" ->
                "Setzt oder entfernt L.C.R. an einer ungebundenen Originalarbeit. expected_record_hash aus lcr_record_hash verwenden; dieselbe request_id mit unveränderten Argumenten wiederholen. Keine Kopie, kein Abschluss, keine Nummer, Zahlung oder PDF. Historische Labor-Zuordnungen bleiben erhalten."
            "get_finance_overview" ->
                "Liest getrennte Beleg-, Leistungs- und Zahlungswerte in EUR-Cent. date_basis ist service, document oder payment; Datumsgrenzen einschließlich. Zahlungen zur Belegkohorte und tatsächliche Eingänge im Zeitraum bleiben getrennt. Originale, Stornos, Korrekturen, Rückzahlungen und Guthaben bleiben nachvollziehbar."
            "get_settlement_pdf" ->
                "Liest die Metadaten des Original-PDFs. Mit preview=true wird ein gespeicherter Rechnungsentwurf ausschließlich im lokalen Cache als ENTWURF gerendert, ohne Nummernreservierung, Quellenbindung oder Originalbeleg."
            "delete_settlement_draft" ->
                "Löscht ausschließlich einen unveränderten, unvorbereiteten Abrechnungsentwurf mit aktuellem record_hash endgültig. Vorbereitete und ausgestellte Abschlüsse, Zahlungen und Nummern bleiben geschützt. Dieselbe request_id wiederholen; gelöschte Entwurfs-IDs werden nicht erneut verwendet."
            "abandon_prepared_settlement" ->
                "Hebt ausdrücklich nur eine noch nicht abgeschlossene Vorbereitung mit Begründung auf. Reservierte Nummer und eingefrorene Historie bleiben erhalten und werden nie wiederverwendet. Kein künstlicher Stornobeleg. Bestätigte Belege nur über Storno oder interne Korrektur berichtigen; restaurierte Vorbereitungen zuvor aktuell abgleichen."
            "prepare_settlement" ->
                "Reserviert nach bewusster Vorschauprüfung eingefrorene tatsächliche Leistungen, aktive Quellenbindungen und bei Rechnung eine eigene lokale Nummer. Noch kein abgeschlossener Beleg. Gewöhnlicher österreichischer EUR-Inlandsfall, Kleinunternehmerbefreiung und rechtsformabhängige Ausstellerangaben bewusst prüfen. Danach complete_settlement mit derselben settlement_id und eigener stabiler request_id fortsetzen."
            "complete_settlement" ->
                "Setzt einen dauerhaft vorbereiteten Vorgang fort. Rechnung erst nach geprüftem unveränderlichem Original-PDF abschließen. Neue Labor-Abschlüsse sind entfernt; historische Originale bleiben lesbar. PDF-Fehler bewahren die Reservierung. Keine Änderung der Benutzerzuordnung."
            "correct_settlement" ->
                "Vollständiges Storno mit eigener Nummer und Originalverweis vorbereiten, anschließend complete_settlement. Labor intern nachvollziehbar aufheben ohne Rechnungsbeleg. Zahlungen bleiben erhalten; keine behauptete Rückzahlung. Vorhandene Zugangsnachweise berücksichtigen."
            "add_settlement_payment" ->
                "Einzelnen tatsächlichen Zahlungseingang oder begründete negative Gegenbuchung mit Datum erfassen. Neue Clients senden expected_payment_hash aus payment_record_hash des gelesenen Vorgangs; ein geänderter Zahlungsstand wird dann abgewiesen. payment_kind unterscheidet receipt, correction und tatsächliche refund. Teil-/Überzahlung bleibt nachvollziehbar. Persönliche Markierungen sind keine Zahlungen."
            "review_restored_sources" ->
                "Dokumentiert nach Restore die bewusste Prüfung des aktuellen Belegstands genau dieser Quellen. Neue Serie rekonstruiert keine fehlenden Belege oder Zahlungen. Nicht ohne tatsächlichen Abgleich bestätigen."
            "record_settlement_delivery" ->
                "Erfasst einen tatsächlichen Zugangsnachweis. Android-Teilen allein ist weder Versand- noch Zugangsnachweis."
            "export_settlements" ->
                "Liest vollständig nachvollziehbare lokale Rechnungs- und Laborvorgänge, Korrekturen, Zahlungen und getrennte Summen/Gesamtsumme. Filter werden ausdrücklich bezeichnet; Original-Metadaten dienen dem lokalen ZIP-Export. Keine garantierte Rechtskonformität durch PDF, Hash oder ZIP."
            else ->
                "Lokale Rechnungen auf tatsächliche Leistungen, Mengen und bestätigte Preise; keine automatische Angebotsübernahme. Entwürfe dürfen unvollständig sein. Historische Laborvorgänge bleiben unverändert lesbar. L.C.R. ist ausschließlich eine Kennzeichnung der Originalarbeit. request_id bei Wiederholung unverändert lassen."
        }

    fun query(name: String, a: JSONObject, repository: OwnerRepository): JSONObject {
        McpSchema.validate(a, schema(name))
        val ledger = SettlementRules.ledger(repository)
        val master = repository.effective()
        if (name == "get_lcr_work_records") {
            val marked = SettlementRules.lcrIds(repository)
            val id = a.optString("work_record_id")
            val query = a.optString("query").trim()
            val from =
                a.optString("date_from").takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse)
            val to =
                a.optString("date_to").takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse)
            require(from == null || to == null || from <= to) {
                "Zeitraumende liegt vor dem Beginn."
            }
            val customers =
                SettlementRules.rows(master.optJSONArray("customers")).associateBy {
                    it.getString("id")
                }
            val works =
                if (id.isNotEmpty()) listOf(SettlementRules.source(master, "worklogs", id))
                else
                    SettlementRules.rows(master.optJSONArray("worklogs"))
                        .filter { it.optString("id") in marked }
                        .filter {
                            (a.optString("customer_id").isEmpty() ||
                                it.optString("customer_id") == a.optString("customer_id")) &&
                                (from == null || it.optString("work_date") >= from.toString()) &&
                                (to == null || it.optString("work_date") <= to.toString())
                        }
                        .filter { work ->
                            val customer = customers[work.optString("customer_id")]
                            query.isEmpty() ||
                                listOf(
                                        work.optString("activity"),
                                        work.optString("work_date"),
                                        customer?.optString("name").orEmpty(),
                                        customer?.optString("company").orEmpty(),
                                    )
                                    .any { it.contains(query, true) }
                        }
                        .sortedWith(
                            compareByDescending<JSONObject> { it.optString("work_date") }
                                .thenBy { it.getString("id") }
                        )
            val offset = if (id.isNotEmpty()) 0 else a.optInt("offset", 0)
            val page = works.drop(offset).take(if (id.isNotEmpty()) 1 else a.optInt("limit", 50))
            val next = offset + page.size
            return JSONObject()
                .put(
                    "records",
                    JSONArray(page.map { SettlementRules.lcrView(it, master, ledger, marked) }),
                )
                .put("total", works.size)
                .put("has_more", next < works.size)
                .put("next_offset", if (next < works.size) next else JSONObject.NULL)
        }
        if (name == "get_finance_overview")
            return SettlementRules.financeOverview(master, ledger, a)
        if (name == "get_settlement_context")
            return JSONObject()
                .put("business_settings", master.optJSONObject("business_settings") ?: JSONObject())
                .put(
                    "invoice_missing_fields",
                    SettlementRules.invoiceMissingFields(
                        master.optJSONObject("business_settings") ?: JSONObject()
                    ),
                )
                .put("billing_ticks", JSONArray(listOf("minute", "tenth_hour", "quarter_hour")))
                .put("customers", master.optJSONArray("customers") ?: JSONArray())
                .put("orders", master.optJSONArray("orders") ?: JSONArray())
                .put("worklogs", master.optJSONArray("worklogs") ?: JSONArray())
                .put("lcr_work_ids", JSONArray(SettlementRules.lcrIds(repository).sorted()))
                .put("restored_sources", ledger.optJSONArray("restored_sources") ?: JSONArray())
                .put(
                    "sources_requiring_review",
                    JSONArray(
                        (SettlementRules.rows(ledger.optJSONArray("restored_sources")) +
                                SettlementRules.externalSources(master))
                            .distinctBy(SettlementRules::sourceKey)
                    ),
                )
        if (name == "list_settlements")
            return JSONObject()
                .put(
                    "records",
                    JSONArray(
                        SettlementRules.records(ledger).map {
                            JSONObject(it.toString())
                                .put("paid_minor", SettlementRules.paid(ledger, it.getString("id")))
                                .put(
                                    "payment_record_hash",
                                    SettlementRules.paymentHash(ledger, it.getString("id")),
                                )
                        }
                    ),
                )
        if (name == "get_settlement" || name == "get_settlement_pdf") {
            val row = SettlementRules.find(ledger, a.getString("settlement_id"))
            if (name == "get_settlement_pdf") {
                if (a.optBoolean("preview")) {
                    require(
                        row.optString("route") == "billed" && row.optString("status") == "draft"
                    ) {
                        "Für die Vorschau einen Rechnungsentwurf auswählen."
                    }
                    return JSONObject()
                        .put(
                            "metadata",
                            repository.previewSettlementDocument(
                                SettlementRules.review(row, master)
                            ),
                        )
                }
                require(row.getString("route") == "billed" && row.has("pdf")) {
                    "Für diesen Vorgang gibt es kein Original-PDF."
                }
                repository.readDocument(row.getJSONObject("pdf"))
                return JSONObject().put("metadata", row.getJSONObject("pdf"))
            }
            val review = SettlementRules.review(row, master)
            val paid = SettlementRules.paid(ledger, row.getString("id"))
            val due = if (row.optString("status") == "cancelled") 0L else row.optLong("total_minor")
            return JSONObject()
                .put("record", review)
                .put("record_hash", SettlementRules.hash(row))
                .put(
                    "payment_record_hash",
                    SettlementRules.paymentHash(ledger, row.getString("id")),
                )
                .put("review_hash", SettlementRules.hash(review))
                .put(
                    "requires_restore_review",
                    SettlementApplicationService.requiresRestoreReview(
                        ledger,
                        row,
                        repository.settlementRestoreEpoch(),
                    ),
                )
                .put(
                    "validation",
                    if (row.getString("status") == "draft")
                        SettlementRules.validation(
                            review,
                            master,
                            ledger,
                            AccountingRules.ids(
                                JSONArray(repository.ownerValue("personal_paid", "[]"))
                            ),
                            SettlementRules.lcrIds(repository),
                        )
                    else JSONObject().put("valid", true).put("errors", JSONArray()),
                )
                .put(
                    "payments",
                    JSONArray(
                        SettlementRules.rows(ledger.optJSONArray("payments")).filter {
                            it.optString("settlement_id") == row.getString("id")
                        }
                    ),
                )
                .put(
                    "delivery_evidence",
                    JSONArray(
                        SettlementRules.rows(ledger.optJSONArray("delivery_evidence")).filter {
                            it.optString("settlement_id") == row.getString("id")
                        }
                    ),
                )
                .put("paid_minor", paid)
                .put("balance_minor", Math.subtractExact(due, paid))
                .put("open_minor", Math.subtractExact(due, paid).coerceAtLeast(0L))
                .put(
                    "credit_minor",
                    if (row.optString("status") != "cancelled")
                        Math.subtractExact(paid, due).coerceAtLeast(0L)
                    else 0L,
                )
                .put(
                    "refund_due_minor",
                    if (row.optString("status") == "cancelled") paid.coerceAtLeast(0L) else 0L,
                )
        }
        throw IllegalArgumentException("Abrechnungsabfrage fehlt.")
    }
}
