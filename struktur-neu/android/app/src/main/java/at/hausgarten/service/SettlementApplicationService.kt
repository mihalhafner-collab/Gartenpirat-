package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Each write is serialized by the existing owner request transaction. Preparation
 * and completion are deliberately separate commits across the SQLite/file boundary. */
internal class SettlementApplicationService(private val repository: OwnerRepository) : OwnerCommand {
    override fun perform(name: String, a: JSONObject, request: String): JSONObject {
        val ledger = SettlementRules.ledger(repository)
        val operations = ledger.optJSONObject("operations") ?: JSONObject().also { ledger.put("operations", it) }
        val fingerprint = SettlementRules.hash(JSONObject().put("tool", name).put("args", a))
        operations.optJSONObject(request)?.let {
            require(it.getString("fingerprint") == fingerprint) { "Die Vorgangskennung gehört zu anderen Argumenten." }
            return JSONObject(it.getJSONObject("result").toString()).put("replayed", true)
        }
        val records = ledger.optJSONArray("records") ?: JSONArray().also { ledger.put("records", it) }
        val master = repository.effective()
        val personal = AccountingRules.ids(JSONArray(repository.ownerValue("personal_paid", "[]")))
        val id = a.optString("settlement_id")
        val result: JSONObject = when (name) {
            "save_settlement_draft" -> {
                val actualId = id.ifBlank { "st_" + PixelMcpWriteTools.hash(request).take(32) }
                val old = SettlementRules.records(ledger).firstOrNull { it.getString("id") == actualId }
                if (old != null) { require(old.getString("status") == "draft") { "Abgeschlossene oder reservierte Vorgänge bleiben unverändert." }; expected(old, a) }
                val draft = SettlementRules.normalized(a.getJSONObject("draft"), actualId)
                if (old != null) replace(records, draft) else records.put(draft)
                JSONObject().put("settlement_id", actualId).put("record_hash", SettlementRules.hash(draft)).put("status", "draft")
            }
            "prepare_settlement" -> {
                require(repository.ownerValue("personal_paid_migrated", "") == "1") { "App einmal öffnen, damit vorhandene persönliche Zuordnungen verlustfrei übernommen werden." }
                val old = SettlementRules.find(ledger, id)
                require(old.getString("status") == "draft") { "Vorgang ist bereits reserviert; bestehenden Vorgang fortsetzen." }
                val view = SettlementRules.review(old, master)
                require(SettlementRules.hash(view) == a.getString("expected_review_hash")) { "Vorschau oder Stammdaten geändert. Erneut prüfen." }
                require(a.getBoolean("confirmed") && a.getBoolean("domestic_eur_confirmed") && a.getBoolean("issuer_details_confirmed")) { "Vorschau, tatsächliche Angaben und österreichischen Inlandsfall bewusst bestätigen." }
                val validation = SettlementRules.validation(view, master, ledger, personal)
                require(validation.getBoolean("valid")) { validation.getJSONArray("errors").join("\n") }
                val replacement = view.optString("replacement_for")
                if (replacement.isNotEmpty()) {
                    val original = SettlementRules.find(ledger, replacement)
                    require(original.getString("status") == "cancelled" && original.getString("kind") == "invoice" && view.getString("route") == "billed") { "Ersatzrechnung benötigt zuvor vollständig stornierte Originalrechnung." }
                    view.put("replacement_original_number", original.getString("invoice_number"))
                }
                view.put("status", "prepared").put("prepared_at", java.time.Instant.now().toString()).put("prepared_epoch", repository.settlementRestoreEpoch())
                    .put("electronic_delivery_accepted", a.optBoolean("electronic_delivery_accepted"))
                    .put("issuer_details_confirmed", true).put("total_minor", SettlementRules.total(view))
                if (view.getString("route") == "billed") reserve(ledger, view)
                replace(records, view)
                result(view)
            }
            "complete_settlement" -> {
                val row = SettlementRules.find(ledger, id)
                if (row.getString("status") == "confirmed" || row.getString("status") == "cancelled") result(row)
                else {
                    require(row.getString("status") == "prepared") { "Zuerst die Vorschau bewusst prüfen und reservieren." }
                    require(!requiresRestoreReview(ledger, row, repository.settlementRestoreEpoch())) { "Restaurierter vorbereiteter Vorgang: aktuellen Belegstand einschließlich Originalen und Zahlungen ausdrücklich abgleichen; nicht ungeprüft erneut ausstellen." }
                    val completed = JSONObject(row.toString())
                    if (row.getString("route") == "billed") {
                        // PDF errors leave the separately committed reservation and binding intact.
                        val meta = repository.createSettlementDocument(row)
                        repository.readDocument(meta)
                        completed.put("pdf", meta)
                    }
                    completed.put("status", "confirmed").put("confirmed_at", java.time.Instant.now().toString())
                    if (row.getString("kind") == "cancellation") {
                        val original = SettlementRules.find(ledger, row.getString("corrects"))
                        require(original.getString("status") == "confirmed") { "Original wurde bereits korrigiert." }
                        replace(records, JSONObject(original.toString()).put("status", "cancelled").put("corrected_by", id))
                    }
                    replace(records, completed)
                    result(completed)
                }
            }
            "correct_settlement" -> {
                val old = SettlementRules.find(ledger, id); expected(old, a)
                require(old.getString("status") == "confirmed" && old.getString("kind") in setOf("invoice", "internal")) { "Nur bestätigte Originalvorgänge vollständig korrigieren." }
                require(a.getBoolean("confirmed") && a.getString("reason").isNotBlank()) { "Vollständige Korrektur mit Begründung bestätigen." }
                require(SettlementRules.records(ledger).none { it.optString("corrects") == id }) { "Vorhandene Korrektur fortsetzen." }
                val correctionId = "st_" + PixelMcpWriteTools.hash(request).take(32)
                val correction = JSONObject(old.toString())
                for (key in listOf("pdf", "invoice_number", "series", "sequence", "confirmed_at", "prepared_at", "corrected_by", "replacement_for")) correction.remove(key)
                correction.put("id", correctionId).put("status", if (old.getString("route") == "billed") "prepared" else "confirmed")
                    .put("kind", if (old.getString("route") == "billed") "cancellation" else "void")
                    .put("corrects", id).put("original_number", old.optString("invoice_number")).put("reason", a.getString("reason"))
                    .put("access_evidence", a.optString("access_evidence")).put("prepared_epoch", repository.settlementRestoreEpoch()).put("issue_date", SettlementRules.today())
                val positions = JSONArray()
                for (line in SettlementRules.rows(old.getJSONArray("positions"))) positions.put(JSONObject(line.toString())
                    .put("settlement_id", correctionId).put("id", correctionId + ":" + positions.length())
                    .put("quantity", SettlementRules.decimal(line.get("quantity")).negate().toPlainString())
                    .put("amount_minor", Math.negateExact(line.getLong("amount_minor"))))
                correction.put("positions", positions).put("total_minor", Math.negateExact(old.getLong("total_minor")))
                if (old.getString("route") == "billed") reserve(ledger, correction)
                else {
                    correction.put("confirmed_at", java.time.Instant.now().toString())
                    replace(records, JSONObject(old.toString()).put("status", "cancelled").put("corrected_by", correctionId))
                }
                records.put(correction)
                result(correction)
            }
            "add_settlement_payment" -> {
                val row = SettlementRules.find(ledger, id); expected(row, a)
                require(row.getString("status") in setOf("confirmed", "cancelled") && row.getString("kind") in setOf("invoice", "internal")) { "Zahlungen gehören zum bestätigten Originalvorgang." }
                val payments = ledger.optJSONArray("payments") ?: JSONArray().also { ledger.put("payments", it) }
                val amount = SettlementRules.minor(a.get("amount"))
                require(amount != 0L) { "Zahlungsbetrag muss ungleich null sein." }
                java.time.LocalDate.parse(a.getString("date"))
                val reversal = a.optString("reversal_of")
                if (amount < 0) {
                    val original = SettlementRules.rows(payments).firstOrNull { it.optString("id") == reversal && it.optString("settlement_id") == id && it.getLong("amount_minor") > 0 }
                        ?: throw IllegalArgumentException("Gegenbuchung benötigt einen vorhandenen positiven Zahlungseintrag desselben Vorgangs.")
                    val previous = SettlementRules.rows(payments).filter { it.optString("reversal_of") == reversal }.fold(0L) { s,p -> Math.addExact(s, Math.negateExact(p.getLong("amount_minor"))) }
                    require(Math.addExact(previous, Math.negateExact(amount)) <= original.getLong("amount_minor")) { "Gegenbuchungen überschreiten die ursprüngliche Zahlung." }
                    require(a.optString("note").isNotBlank()) { "Gegenbuchung begründen; eine Stornierung allein belegt keine Rückzahlung." }
                } else require(reversal.isEmpty()) { "Nur negative Gegenbuchungen verweisen auf eine Zahlung." }
                payments.put(JSONObject().put("id", "pay_" + PixelMcpWriteTools.hash(request).take(32)).put("settlement_id", id).put("amount_minor", amount).put("date", a.getString("date")).put("note", a.optString("note")).put("reversal_of", reversal))
                JSONObject().put("settlement_id", id).put("paid_minor", SettlementRules.paid(ledger, id))
            }
            "record_settlement_delivery" -> {
                val row = SettlementRules.find(ledger, id); expected(row, a)
                require(row.getString("status") in setOf("confirmed", "cancelled") && row.getString("route") == "billed")
                require(a.getString("evidence").isNotBlank()) { "Tatsächlichen Zugangsnachweis angeben; Teilen allein genügt nicht." }
                val evidence = ledger.optJSONArray("delivery_evidence") ?: JSONArray().also { ledger.put("delivery_evidence", it) }
                evidence.put(JSONObject().put("settlement_id", id).put("date", java.time.LocalDate.parse(a.getString("date")).toString()).put("evidence", a.getString("evidence")))
                result(row)
            }
            "review_restored_sources" -> {
                require(a.getBoolean("confirmed") && a.getString("evidence").isNotBlank()) { "Aktuellen Belegstand bewusst prüfen und Ergebnis dokumentieren." }
                if (a.optString("settlement_id").isNotEmpty()) {
                    val row = SettlementRules.find(ledger, a.getString("settlement_id"))
                    require(row.getString("status") in setOf("draft", "prepared"))
                    val reviews = ledger.optJSONArray("reservation_reviews") ?: JSONArray().also { ledger.put("reservation_reviews", it) }
                    reviews.put(JSONObject().put("settlement_id", row.getString("id")).put("restore_epoch", repository.settlementRestoreEpoch()).put("evidence", a.getString("evidence")).put("checked_at", java.time.Instant.now().toString()))
                }
                val reviews = ledger.optJSONArray("source_reviews") ?: JSONArray().also { ledger.put("source_reviews", it) }
                for (ref in SettlementRules.rows(a.getJSONArray("sources"))) {
                    SettlementRules.source(master, ref.getString("collection"), ref.getString("id"))
                    reviews.put(JSONObject(ref.toString()).put("restore_epoch", ledger.optString("restore_epoch")).put("evidence", a.getString("evidence")).put("checked_at", java.time.Instant.now().toString()))
                }
                JSONObject().put("reviewed", true)
            }
            else -> throw IllegalArgumentException("Abrechnungsaktion fehlt.")
        }
        operations.put(request, JSONObject().put("fingerprint", fingerprint).put("result", result))
        repository.putOwnerValue(SettlementRules.KEY, ledger.toString())
        return result
    }
    companion object {
        fun requiresRestoreReview(ledger: JSONObject, row: JSONObject, epoch: String): Boolean = row.optString("status") == "prepared" && row.optString("prepared_epoch") != epoch &&
            SettlementRules.rows(ledger.optJSONArray("reservation_reviews")).none { it.optString("settlement_id") == row.getString("id") && it.optString("restore_epoch") == epoch }
    }
    private fun expected(row: JSONObject, a: JSONObject) { require(SettlementRules.hash(row) == a.getString("expected_record_hash")) { "Vorgang geändert. Erneut öffnen." } }
    private fun reserve(ledger: JSONObject, row: JSONObject) {
        val series = repository.settlementSeries()
        val counters = ledger.optJSONObject("series_counters") ?: JSONObject().also { ledger.put("series_counters", it) }
        val sequence = Math.addExact(counters.optLong(series), 1L)
        counters.put(series, sequence)
        row.put("series", series).put("sequence", sequence).put("invoice_number", "$series-${sequence.toString().padStart(6, '0')}")
    }
    private fun replace(records: JSONArray, row: JSONObject) {
        val index = (0 until records.length()).first { records.getJSONObject(it).getString("id") == row.getString("id") }
        records.put(index, row)
    }
    private fun result(row: JSONObject) = JSONObject().put("settlement_id", row.getString("id")).put("status", row.getString("status")).put("record", row).put("record_hash", SettlementRules.hash(row))
}
