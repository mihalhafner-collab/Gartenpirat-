package at.hausgarten.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Shared local EUR settlement rules. Values in accounting_amount remain decimal euros. */
internal object SettlementRules {
    const val KEY = "settlement:v1"
    const val EXEMPTION = "Umsatzsteuerbefreit gemäß § 6 Abs. 1 Z 27 UStG"

    fun rows(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }

    fun records(ledger: JSONObject) = rows(ledger.optJSONArray("records"))

    fun ledger(repository: OwnerRepository) = JSONObject(repository.ownerValue(KEY, "{}"))

    fun today() = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()

    /** Settings completeness is shared by the issue action and the settings links. */
    fun invoiceMissingFields(settings: JSONObject): JSONArray {
        val missing = JSONArray()
        fun need(field: String, label: String, value: String = settings.optString(field)) {
            if (value.isBlank()) missing.put(JSONObject().put("field", field).put("label", label))
        }
        need(
            "business_logo",
            "Logo",
            if (runCatching { invoiceLogoBytes(settings.optString("business_logo")) }.isSuccess)
                "valid"
            else "",
        )
        need(
            "business_name",
            "Name",
            settings.optString("business_name").ifBlank { settings.optString("owner_name") },
        )
        need("business_street", "Straße")
        need("business_postal_code", "Postleitzahl")
        need("business_city", "Ort")
        need(
            "business_email",
            "Kontakt",
            settings.optString("business_email").ifBlank { settings.optString("business_phone") },
        )
        need("business_iban", "IBAN")
        need("business_bic", "BIC")
        need("default_payment_terms", "Zahlungsziel")
        return missing
    }

    fun invoiceLogoBytes(encoded: String): ByteArray {
        val prefix = encoded.substringBefore(',')
        require(
            prefix in setOf("data:image/png;base64", "data:image/jpeg;base64") &&
                encoded.length <= 200000
        ) {
            "Logo als kleines PNG- oder JPEG-Bild auswählen."
        }
        val bytes = java.util.Base64.getDecoder().decode(encoded.substringAfter(','))
        val png =
            bytes.size >= 8 &&
                bytes
                    .take(8)
                    .toByteArray()
                    .contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val jpeg = bytes.size >= 4 && bytes[0] == (-1).toByte() && bytes[1] == (-40).toByte()
        require(if (prefix == "data:image/png;base64") png else jpeg) {
            "Logo ist nicht lesbar; ein anderes Bild auswählen."
        }
        return bytes
    }

    /** A selected billing interval changes the invoice quantity, never the recorded work. */
    fun billableQuantity(hours: String, tick: String = "minute"): String {
        val duration = decimal(hours)
        require(duration.signum() >= 0) { "Arbeitsdauer darf nicht negativ sein." }
        val quantity =
            when (tick) {
                "minute" ->
                    duration
                        .multiply(BigDecimal(60))
                        .setScale(0, RoundingMode.HALF_UP)
                        .divide(BigDecimal(60), 8, RoundingMode.HALF_UP)
                "tenth_hour" -> duration.setScale(1, RoundingMode.CEILING)
                "quarter_hour" ->
                    duration
                        .multiply(BigDecimal(4))
                        .setScale(0, RoundingMode.CEILING)
                        .divide(BigDecimal(4))
                else -> throw IllegalArgumentException("Abrechnungstakt auswählen.")
            }
        return quantity.stripTrailingZeros().toPlainString()
    }

    fun decimal(value: Any): BigDecimal {
        val text = value.toString().trim()
        require(text.matches(Regex("-?[0-9]+([.,][0-9]{1,8})?"))) {
            "Dezimalzahl ohne Tausendertrennzeichen eingeben, zum Beispiel 12,50."
        }
        return BigDecimal(text.replace(',', '.')).also {
            require(it.precision() <= 18) { "Zahl ist zu groß." }
        }
    }

    fun minor(value: Any) =
        decimal(value).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()

    fun hash(row: JSONObject) = PixelMcpWriteTools.hash(row)

    fun find(ledger: JSONObject, id: String) =
        records(ledger).firstOrNull { it.getString("id") == id }
            ?: throw IllegalArgumentException("Abrechnungsvorgang ist nicht vorhanden.")

    fun sourceKey(source: JSONObject) =
        source.getString("collection") + ":" + source.getString("id")

    fun active(row: JSONObject) =
        row.optString("status") in setOf("prepared", "confirmed") &&
            row.optString("kind") in setOf("invoice", "internal")

    fun boundWorkIds(records: List<JSONObject>, worklogs: JSONArray?): Set<String> {
        val bound = linkedSetOf<String>()
        for (record in records.filter(::active)) {
            for (ref in rows(record.optJSONArray("sources"))) {
                if (ref.optString("collection") == "worklogs") bound.add(ref.getString("id"))
                else if (ref.optString("collection") == "orders") {
                    val frozen =
                        rows(record.optJSONArray("source_snapshots"))
                            .firstOrNull { sourceKey(it) == sourceKey(ref) }
                            ?.optJSONArray("included_work_ids")
                    if (frozen != null)
                        for (index in 0 until frozen.length()) bound.add(frozen.getString(index))
                    else
                        bound.addAll(
                            rows(worklogs)
                                .filter { it.optString("order_id") == ref.getString("id") }
                                .map { it.getString("id") }
                        )
                }
            }
        }
        return bound
    }

    fun source(master: JSONObject, collection: String, id: String): JSONObject {
        require(collection in setOf("orders", "worklogs")) {
            "Nur tatsächliche Arbeiten oder bewusst geprüfte Aufträge übernehmen."
        }
        return PixelMcpWriteTools.find(master.optJSONArray(collection), id)
    }

    fun group(master: JSONObject, collection: String, id: String): String {
        val row = source(master, collection, id)
        val order = row.optString("order_id")
        return if (
            collection == "worklogs" &&
                order.isNotEmpty() &&
                rows(master.optJSONArray("orders")).any { it.optString("id") == order }
        )
            "orders:$order"
        else "$collection:$id"
    }

    /** Known foreign/legacy references require evidence review, never automatic import. */
    fun externalSources(master: JSONObject): List<JSONObject> {
        val refs = linkedMapOf<String, JSONObject>()
        fun add(collection: String, id: String) {
            if (id.isNotEmpty())
                refs["$collection:$id"] = JSONObject().put("collection", collection).put("id", id)
        }
        for (invoice in rows(master.optJSONArray("invoices"))) {
            if (
                invoice.optString("sevdesk_id").isEmpty() &&
                    invoice.optString("issued_at").isEmpty() &&
                    invoice.optString("invoice_number").isBlank()
            )
                continue
            add("orders", invoice.optString("order_id"))
            val ids = invoice.optJSONArray("worklog_ids") ?: JSONArray()
            for (i in 0 until ids.length()) add("worklogs", ids.getString(i))
            for (position in rows(invoice.optJSONArray("positions"))) {
                add("worklogs", position.optString("worklog_id"))
                add("orders", position.optString("order_id"))
            }
        }
        return refs.values.toList()
    }

    fun normalized(draft: JSONObject, id: String): JSONObject {
        val out =
            JSONObject()
                .put("id", id)
                .put("status", "draft")
                .put("kind", if (draft.optString("route") == "labor") "internal" else "invoice")
        for (key in
            listOf(
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
            )) out.put(key, draft.optString(key))
        require(out.getString("route") in setOf("billed", "labor")) { "Abrechnungsart auswählen." }
        val interval = draft.optString("billing_interval").ifBlank { "minute" }
        require(interval in setOf("minute", "tenth_hour", "quarter_hour")) {
            "Abrechnungstakt auswählen."
        }
        out.put("billing_interval", interval)
        val positions = JSONArray()
        val sources = linkedMapOf<String, JSONObject>()
        for ((index, p) in rows(draft.optJSONArray("positions")).withIndex()) {
            val line = JSONObject().put("id", "$id:${index + 1}").put("settlement_id", id)
            for (key in
                listOf("title", "description", "unit", "source_collection", "source_id")) line.put(
                key,
                p.optString(key),
            )
            for (key in listOf("quantity", "price")) {
                val value = p.optString(key)
                line.put(
                    key,
                    if (value.isBlank()) ""
                    else
                        decimal(value)
                            .also {
                                require(it.signum() >= 0) {
                                    "Menge und Preis dürfen nicht negativ sein."
                                }
                            }
                            .toPlainString(),
                )
            }
            val amount =
                if (line.getString("quantity").isBlank() || line.getString("price").isBlank()) null
                else
                    BigDecimal(line.getString("quantity"))
                        .multiply(BigDecimal(line.getString("price")))
                        .setScale(2, RoundingMode.HALF_UP)
                        .movePointRight(2)
                        .longValueExact()
            line.put("amount_minor", amount ?: JSONObject.NULL)
            val collection = line.getString("source_collection")
            val sourceId = line.getString("source_id")
            require(collection in setOf("", "orders", "worklogs")) { "Unbekannte Leistungsquelle." }
            if (collection.isNotEmpty() && sourceId.isNotEmpty())
                sources["$collection:$sourceId"] =
                    JSONObject().put("collection", collection).put("id", sourceId)
            positions.put(line)
        }
        require(positions.length() <= 500) { "Höchstens 500 Positionen pro Vorgang." }
        return out.put("positions", positions)
            .put("sources", JSONArray(sources.values))
            .put("currency", "EUR")
            .put("tax_exemption_text", EXEMPTION)
    }

    fun review(record: JSONObject, master: JSONObject): JSONObject {
        val view = JSONObject(record.toString())
        if (record.optString("status") != "draft") return view
        val lines = rows(record.optJSONArray("positions"))
        if (lines.isNotEmpty() && lines.none { it.isNull("amount_minor") }) {
            try {
                view.put("total_minor", total(record))
            } catch (_: ArithmeticException) {
                view.put("total_overflow", true)
            }
        }
        val recipientId = record.optString("billing_customer_id")
        val recipient =
            rows(master.optJSONArray("customers")).firstOrNull { it.optString("id") == recipientId }
                ?: JSONObject()
        val snapshots = JSONArray()
        for (ref in rows(record.optJSONArray("sources"))) {
            val row =
                rows(master.optJSONArray(ref.getString("collection"))).firstOrNull {
                    it.optString("id") == ref.getString("id")
                }
            snapshots.put(
                JSONObject(ref.toString())
                    .put("record", row ?: JSONObject().put("missing", true))
                    .put(
                        "included_work_ids",
                        JSONArray(
                            AccountingRules.works(
                                    master,
                                    ref.getString("collection"),
                                    ref.getString("id"),
                                )
                                .map { it.getString("id") }
                        ),
                    )
            )
        }
        return view
            .put("issuer", master.optJSONObject("business_settings") ?: JSONObject())
            .put("recipient", recipient)
            .put("source_snapshots", snapshots)
            .put("issue_date", today())
    }

    fun validation(
        view: JSONObject,
        master: JSONObject,
        ledger: JSONObject,
        personal: Set<String>,
    ): JSONObject {
        val errors = arrayListOf<String>()
        fun needed(value: String, message: String) {
            if (value.isBlank()) errors.add(message)
        }
        needed(view.optString("title"), "Bezeichnung fehlt.")
        val issuer = view.optJSONObject("issuer") ?: JSONObject()
        val recipient = view.optJSONObject("recipient") ?: JSONObject()
        val missingIssuer =
            if (view.optString("route") == "billed") invoiceMissingFields(issuer) else JSONArray()
        for (field in rows(missingIssuer)) errors.add(
            "Rechnungsdaten: ${field.getString("label")} fehlt."
        )
        if (issuer.optString("tax_mode", "small_business") != "small_business")
            errors.add(
                "Abweichender Steuerstatus wird nicht unterstützt; Steuerstatus bewusst in den Stammdaten prüfen."
            )
        if (
            issuer.optBoolean("reverse_charge") ||
                recipient.optBoolean("reverse_charge") ||
                recipient.optBoolean("federal_structured_invoice_required")
        )
            errors.add(
                "Reverse Charge und strukturierte Rechnungen an Bundesdienststellen werden nicht unterstützt."
            )
        if (issuer.optString("business_name").isBlank() && issuer.optString("owner_name").isBlank())
            errors.add("Tatsächlicher Aussteller fehlt.")
        for ((key, label) in
            listOf(
                "business_street" to "Straße",
                "business_postal_code" to "Postleitzahl",
                "business_city" to "Ort",
            )) needed(issuer.optString(key), "Ausstelleradresse: $label fehlt.")
        if (recipient.optString("name").isBlank() && recipient.optString("company").isBlank())
            errors.add("Rechtlicher Rechnungsempfänger fehlt.")
        for ((key, label) in
            listOf("street" to "Straße", "postal_code" to "Postleitzahl", "city" to "Ort")) needed(
            recipient.optString(key),
            "Empfängeradresse: $label fehlt.",
        )
        for (party in listOf(issuer, recipient)) {
            val country =
                party
                    .optString("country", party.optString("business_country", "AT"))
                    .trim()
                    .uppercase(java.util.Locale.ROOT)
            if (country !in setOf("", "AT", "ÖSTERREICH", "AUSTRIA"))
                errors.add("Unterstützt wird nur gewöhnliche Inlandsabrechnung in Österreich.")
        }
        try {
            require(
                LocalDate.parse(view.getString("service_from")) <=
                    LocalDate.parse(view.getString("service_to"))
            )
        } catch (_: Exception) {
            errors.add("Gültiges Leistungsdatum oder Leistungszeitraum fehlt.")
        }
        needed(view.optString("payment_terms"), "Zahlungsbedingungen fehlen.")
        needed(
            view.optString("payment_details"),
            "Zahlungsangaben fehlen (zum Beispiel IBAN oder vereinbarte Zahlungsart).",
        )
        if (view.optBoolean("total_overflow"))
            errors.add("Gesamtbetrag überschreitet den unterstützten centgenauen Zahlenbereich.")
        val positions = rows(view.optJSONArray("positions"))
        if (positions.isEmpty()) errors.add("Mindestens eine tatsächliche Leistung angeben.")
        for ((i, p) in positions.withIndex()) {
            if (p.optString("source_collection").isEmpty() != p.optString("source_id").isEmpty())
                errors.add("Position ${i+1}: Quellzuordnung ist unvollständig.")
            if (
                p.optString("title").isBlank() ||
                    p.optString("unit").isBlank() ||
                    p.isNull("amount_minor") ||
                    p.optString("quantity").isBlank() ||
                    decimal(p.get("quantity")).signum() <= 0
            )
                errors.add(
                    "Position ${i+1}: Leistung, positive Menge, Einheit und bestätigter Preis erforderlich."
                )
        }
        if (
            view.optString("customer_id").isNotBlank() &&
                rows(master.optJSONArray("customers")).none {
                    it.optString("id") == view.optString("customer_id")
                }
        )
            errors.add("Einsatzkunde ist nicht mehr vorhanden.")
        val refs = rows(view.optJSONArray("sources"))
        val keys = refs.map(::sourceKey).toSet()
        for (ref in refs) {
            val collection = ref.getString("collection")
            val id = ref.getString("id")
            val row = rows(master.optJSONArray(collection)).firstOrNull { it.optString("id") == id }
            if (row == null) {
                errors.add("Leistungsquelle fehlt; Entwurf bearbeiten.")
                continue
            }
            if (BillingRules.recipient(row) != view.optString("billing_customer_id"))
                errors.add("Alle Quellen müssen denselben rechtlichen Rechnungsempfänger haben.")
            if (
                collection == "worklogs" && !row.optBoolean("billable", true) ||
                    collection == "orders" &&
                        AccountingRules.works(master, collection, id).any {
                            !it.optBoolean("billable", true)
                        }
            )
                errors.add("Nicht abzurechnende Arbeit abwählen.")
            if (row.optString("source").contains("sevdesk", true))
                errors.add("Historische Versuchsdaten dürfen nicht neu ausgestellt werden.")
            val groupKey = group(master, collection, id)
            val (kind, root) = AccountingRules.canonical(master, collection, id)
            val routeRow =
                AccountingRules.project(master, personal, JSONArray()).firstOrNull {
                    it.optString("key") == "$kind:${root.getString("id")}"
                }
            if (
                routeRow != null &&
                    (routeRow.optString("route").isBlank() ||
                        routeRow.optString("route") != view.optString("route"))
            )
                errors.add(
                    "Abrechnungsart widerspricht der Benutzerzuordnung. Erst bisherigen Vorgang korrigieren und Zuordnung bewusst ändern."
                )
            for (other in
                records(ledger).filter {
                    it.optString("id") != view.optString("id") && active(it)
                }) for (bound in rows(other.optJSONArray("sources"))) {
                val otherGroup = group(master, bound.getString("collection"), bound.getString("id"))
                val frozenIds =
                    rows(other.optJSONArray("source_snapshots"))
                        .firstOrNull { sourceKey(it) == sourceKey(bound) }
                        ?.optJSONArray("included_work_ids")
                val overlapsOrder =
                    if (collection == "orders") otherGroup == groupKey
                    else
                        bound.getString("collection") == "orders" &&
                            otherGroup == groupKey &&
                            (frozenIds == null ||
                                (0 until frozenIds.length()).any { frozenIds.getString(it) == id })
                if (sourceKey(bound) == sourceKey(ref) || overlapsOrder)
                    errors.add("Leistung ist bereits aktiv gebunden oder reserviert.")
                if (otherGroup == groupKey && other.optString("route") != view.optString("route"))
                    errors.add("Auftrag hat einen aktiven Vorgang der anderen Abrechnungsart.")
            }
            if (
                collection == "worklogs" &&
                    groupKey.startsWith("orders:") &&
                    keys.contains(groupKey)
            )
                errors.add("Auftrag und zugehörige Arbeit dürfen nicht gemeinsam berechnet werden.")
            val scope =
                listOf(ref) +
                    if (collection == "orders")
                        AccountingRules.works(master, collection, id).map {
                            JSONObject().put("collection", "worklogs").put("id", it.getString("id"))
                        }
                    else emptyList()
            if (
                scope.any { selected ->
                    externalSources(master).any { sourceKey(it) == sourceKey(selected) } &&
                        !rows(ledger.optJSONArray("source_reviews")).any {
                            sourceKey(it) == sourceKey(selected) &&
                                it.optString("restore_epoch") == ledger.optString("restore_epoch")
                        }
                }
            )
                errors.add(
                    "Bekannte frühere Belegverknüpfung: tatsächlichen aktuellen Belegstand prüfen; sevDesk-Umsätze nicht erneut ausstellen."
                )
            if (
                scope.any { selected ->
                    rows(ledger.optJSONArray("restored_sources")).any {
                        sourceKey(it) == sourceKey(selected)
                    } &&
                        !rows(ledger.optJSONArray("source_reviews")).any {
                            sourceKey(it) == sourceKey(selected) &&
                                it.optString("restore_epoch") == ledger.optString("restore_epoch")
                        }
                }
            )
                errors.add(
                    "Restaurierte Quelle: aktuellen Belegstand einschließlich enthaltener Arbeiten vor erneuter Abrechnung bewusst prüfen."
                )
        }
        return JSONObject()
            .put("valid", errors.isEmpty())
            .put("errors", JSONArray(errors.distinct()))
            .put("missing_issuer_fields", missingIssuer)
    }

    fun requireMutable(ledger: JSONObject, collection: String, id: String) {
        require(
            records(ledger).none { row ->
                row.optString("status") in setOf("prepared", "confirmed", "cancelled") &&
                    (rows(row.optJSONArray("sources")).any {
                        it.optString("collection") == collection && it.optString("id") == id
                    } ||
                        collection == "worklogs" &&
                            rows(row.optJSONArray("source_snapshots")).any { snapshot ->
                                snapshot.optString("collection") == "orders" &&
                                    (snapshot.optJSONArray("included_work_ids") ?: JSONArray())
                                        .let { ids ->
                                            (0 until ids.length()).any { ids.optString(it) == id }
                                        }
                            })
            }
        ) {
            "Diese Leistungsdaten gehören zu einem aufbewahrungspflichtigen Abrechnungsvorgang. Nur interne Notizen und persönliche Zuordnungen ändern."
        }
    }

    /** A draft of open work, priced only from the explicitly configured hourly rate. */
    @JvmOverloads
    fun prefill(
        args: JSONObject,
        worklogs: JSONArray?,
        records: List<JSONObject>,
        configuredRate: String = "",
    ): JSONObject {
        val draft =
            JSONObject()
                .put("route", "billed")
                .put("positions", JSONArray())
                .put("billing_interval", args.optString("billing_interval").ifBlank { "minute" })
        val recipient = args.optString("billing_customer_id")
        val ids = args.optJSONArray("work_record_ids")
        val wanted = (0 until (ids?.length() ?: 0)).map { ids!!.getString(it) }.toSet()
        if (recipient.isEmpty() || wanted.isEmpty()) return draft
        val bound = boundWorkIds(records, worklogs)
        val configured = configuredRate.trim().replace(',', '.')
        val rate = configured.takeIf { it.matches(Regex("[0-9]+([.][0-9]{1,2})?")) }.orEmpty()
        val works =
            rows(worklogs)
                .filter {
                    it.optString("id") in wanted &&
                        it.optString("id") !in bound &&
                        it.optBoolean("billable", true)
                }
                .sortedBy { it.optString("work_date") }
        draft.put("billing_customer_id", recipient)
        if (works.isEmpty()) return draft
        val positions = JSONArray()
        for (work in works) positions.put(
            JSONObject()
                .put("title", work.optString("activity").ifBlank { "Gartenarbeit" })
                .put(
                    "description",
                    runCatching {
                            LocalDate.parse(work.optString("work_date"))
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        }
                        .getOrDefault(""),
                )
                .put(
                    "quantity",
                    work
                        .optString("hours")
                        .takeIf { it.toBigDecimalOrNull() != null }
                        ?.let { billableQuantity(it, draft.getString("billing_interval")) }
                        .orEmpty(),
                )
                .put("unit", "Stunden")
                .put("price", rate)
                .put("source_collection", "worklogs")
                .put("source_id", work.getString("id"))
        )
        val from = works.first().optString("work_date")
        val to = works.last().optString("work_date")
        works
            .map { it.optString("customer_id") }
            .distinct()
            .singleOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { draft.put("customer_id", it) }
        return draft
            .put("service_from", from)
            .put("service_to", to)
            .put("title", "Leistungen")
            .put("positions", positions)
    }

    fun total(record: JSONObject) =
        rows(record.optJSONArray("positions")).fold(0L) { sum, row ->
            Math.addExact(sum, row.getLong("amount_minor"))
        }

    fun paid(ledger: JSONObject, id: String) =
        rows(ledger.optJSONArray("payments"))
            .filter { it.optString("settlement_id") == id }
            .fold(0L) { sum, row -> Math.addExact(sum, row.getLong("amount_minor")) }
}
