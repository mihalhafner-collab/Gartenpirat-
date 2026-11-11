package at.hausgarten.service

import java.math.BigDecimal
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Read-only search over user fields and explicit original relationships, never raw JSON. */
internal object LocalSearchRules {
    private val zone = ZoneId.of("Europe/Vienna")
    private val marks = Regex("\\p{M}+")
    private val fields =
        mapOf(
            "customer" to
                listOf(
                    "name",
                    "company",
                    "first_name",
                    "last_name",
                    "phone",
                    "email",
                    "street",
                    "postal_code",
                    "city",
                    "notes",
                ),
            "property" to listOf("name", "address", "district", "access_notes", "notes"),
            "appointment" to
                listOf(
                    "title",
                    "activity",
                    "location",
                    "location_text",
                    "address",
                    "description",
                    "notes",
                ),
            "visit" to listOf("title", "activity", "location", "description", "notes"),
            "activity" to listOf("activity", "title", "description", "location", "notes", "note"),
            "inquiry" to listOf("title", "description", "concern", "notes", "inspection_notes"),
            "order" to
                listOf("title", "number", "order_number", "description", "location", "notes"),
            "offer" to listOf("title", "number", "offer_number", "description", "notes"),
            "invoice" to listOf("title", "number", "invoice_number", "description", "notes"),
            "settlement" to listOf("title", "number", "invoice_number", "description", "notes"),
            "equipment" to
                listOf("name", "manufacturer", "brand", "model", "serial_number", "notes"),
            "material" to listOf("name", "category", "unit", "notes"),
            "photo" to
                listOf(
                    "caption",
                    "title",
                    "notes",
                    "note",
                    "filename",
                    "original_filename",
                    "capture_time",
                    "taken_at",
                    "date",
                ),
        )
    private val positionFields =
        listOf(
            "name",
            "title",
            "label",
            "description",
            "text",
            "notes",
            "product_name",
            "service_name",
            "unit",
        )
    private val bases =
        setOf("default", "work", "document", "payment", "planned", "created", "updated", "photo")

    private fun text(row: JSONObject?, key: String): String =
        if (row == null || row.isNull(key)) "" else row.optString(key).trim()

    private fun normalize(value: String) =
        marks.replace(
            Normalizer.normalize(
                value.lowercase(Locale.GERMAN).replace("ß", "ss"),
                Normalizer.Form.NFD,
            ),
            "",
        )

    private fun rows(array: JSONArray?) = SettlementRules.rows(array)

    private fun index(array: JSONArray?) = rows(array).associateBy { text(it, "id") }

    private fun first(row: JSONObject, vararg keys: String) =
        keys.firstNotNullOfOrNull { text(row, it).takeIf(String::isNotBlank) }.orEmpty()

    private fun values(row: JSONObject?, keys: List<String>) =
        keys.map { text(row, it) }.filter(String::isNotBlank)

    private fun names(row: JSONObject?) =
        values(row, listOf("name", "company", "first_name", "last_name"))

    private fun day(value: String): String {
        if (value.isBlank()) return ""
        return try {
            if (value.length == 10) LocalDate.parse(value).toString()
            else OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate().toString()
        } catch (_: DateTimeException) {
            // Legacy local timestamps have a stated calendar day, but no verified offset/instant.
            try {
                LocalDateTime.parse(value.replace(' ', 'T')).toLocalDate().toString()
            } catch (_: DateTimeException) {
                ""
            }
        }
    }

    private fun decimal(value: Any?): BigDecimal? {
        if (value == null || value == JSONObject.NULL || value.toString().isBlank()) return null
        return try {
            BigDecimal(value.toString().trim().replace(',', '.'))
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun amount(row: JSONObject, type: String): BigDecimal? {
        val minor =
            if (type == "material") listOf("billing_price_minor", "price_minor")
            else listOf("total_minor", "amount_minor")
        for (key in minor) decimal(row.opt(key))?.let {
            return it.movePointLeft(2)
        }
        for (key in
            if (type == "material") listOf("price", "billing_price")
            else listOf("accounting_amount", "amount", "total", "gross_total")) decimal(
                row.opt(key)
            )
            ?.let {
                return it
            }
        return null
    }

    private data class Candidate(
        val type: String,
        val row: JSONObject,
        val archived: Boolean = false,
    )

    fun search(
        args: JSONObject,
        master: JSONObject,
        revier: JSONObject,
        ledger: JSONObject,
        lcr: Set<String>,
        today: LocalDate,
        legacyLabor: Set<String> = emptySet(),
    ): JSONObject {
        val query = RevierRules.text(args, "query", 200, false)
        val words = normalize(query).split(Regex("\\s+")).filter(String::isNotBlank)
        val types =
            args
                .optJSONArray("types")
                ?.let { (0 until it.length()).map(it::getString).toSet() }
                .orEmpty()
        val basis = text(args, "date_basis").ifBlank { "default" }
        require(basis in bases) { "Datumsbasis ist ungültig." }
        val from = text(args, "from_date").also { if (it.isNotBlank()) LocalDate.parse(it) }
        val until = text(args, "to_date").also { if (it.isNotBlank()) LocalDate.parse(it) }
        require(from.isBlank() || until.isBlank() || from <= until) {
            "Datum von liegt nach Datum bis."
        }
        fun bound(key: String) =
            if (!args.has(key) || args.isNull(key) || text(args, key).isBlank()) null
            else
                decimal(args.get(key))
                    ?: throw IllegalArgumentException("Betragsgrenze ist ungültig.")
        val minimum = bound("amount_from")
        val maximum = bound("amount_to")
        require(minimum == null || maximum == null || minimum <= maximum) {
            "Betrag von liegt über Betrag bis."
        }
        val customers = index(master.optJSONArray("customers"))
        val properties = index(revier.optJSONArray("property"))
        val orders = index(master.optJSONArray("orders"))
        val works = index(master.optJSONArray("worklogs"))
        val payments = rows(ledger.optJSONArray("payments")).groupBy { text(it, "settlement_id") }
        val boundWorks =
            SettlementRules.boundWorkIds(
                SettlementRules.records(ledger),
                master.optJSONArray("worklogs"),
            )
        val externallyBound =
            SettlementRules.externalSources(master).map { SettlementRules.sourceKey(it) }.toSet()
        fun open(work: JSONObject): Boolean {
            val workId = text(work, "id")
            val orderId = text(work, "order_id")
            val route = orders[orderId]?.let { AccountingRules.orderRoute(it) }.orEmpty()
            return workId !in lcr &&
                workId !in legacyLabor &&
                workId !in boundWorks &&
                "worklogs:$workId" !in externallyBound &&
                (orderId.isBlank() || "orders:$orderId" !in externallyBound) &&
                route !in setOf(AccountingRules.BILLED, AccountingRules.LABOR) &&
                !work.optBoolean("billed") &&
                work.optInt("billed", 0) == 0 &&
                work.optBoolean("billable", true)
        }
        val openCustomers = works.values.filter(::open).map { text(it, "customer_id") }.toSet()
        val candidates = ArrayList<Candidate>()
        for ((collection, type) in
            listOf(
                "customers" to "customer",
                "orders" to "order",
                "appointments" to "appointment",
                "worklogs" to "activity",
                "offers" to "offer",
                "invoices" to "invoice",
                "photos" to "photo",
            )) rows(master.optJSONArray(collection)).forEach { candidates.add(Candidate(type, it)) }
        val appointments = index(master.optJSONArray("appointments"))
        for (type in listOf("property", "inquiry", "visit", "material", "equipment")) {
            for (row in rows(revier.optJSONArray(type))) {
                // Only an actual link identifies one appointment; similar names/dates never do.
                if (
                    type == "visit" &&
                        text(row, "appointment_id") in appointments &&
                        (types.isEmpty() || "appointment" in types)
                )
                    continue
                candidates.add(Candidate(type, row))
            }
        }
        SettlementRules.records(ledger)
            .filter { text(it, "status") != "abandoned" }
            .forEach { candidates.add(Candidate("settlement", it)) }
        val includeArchived =
            RevierRules.bool(args, "include_archived", false) || args.optBoolean("archived")
        if (includeArchived)
            master.optJSONObject("owner_archive")?.let { archive ->
                for (key in archive.keys()) {
                    val entry = archive.optJSONObject(key) ?: continue
                    if (entry.optBoolean("purged")) continue
                    val row = entry.optJSONObject("record") ?: continue
                    val type =
                        key.substringBefore(':').let { if (it == "worklog") "activity" else it }
                    if (type in fields) candidates.add(Candidate(type, row, true))
                }
            }
        val hits = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        for ((type, row, old) in candidates) {
            val id = text(row, "id")
            if (id.isBlank() || row.optBoolean("deleted")) continue
            val archived =
                old ||
                    row.optBoolean("archived") ||
                    row.has("active") &&
                        (!row.optBoolean("active", true) || row.optInt("active", 1) == 0)
            if (
                !includeArchived && archived ||
                    args.has("archived") && archived != args.getBoolean("archived")
            )
                continue
            val noteOnly = "note" in types && type !in types
            if (types.isNotEmpty() && type !in types && !noteOnly) continue
            val sourceRows =
                rows(row.optJSONArray("sources")).mapNotNull { source ->
                    when (text(source, "collection")) {
                        "worklogs" -> works[text(source, "id")]
                        "orders" -> orders[text(source, "id")]
                        else -> null
                    }
                } +
                    rows(row.optJSONArray("source_snapshots")).mapNotNull {
                        it.optJSONObject("record")
                    }
            val sourceCustomers =
                sourceRows.map { text(it, "customer_id") }.filter(String::isNotBlank).toSet()
            val sourceProperties =
                sourceRows.map { text(it, "property_id") }.filter(String::isNotBlank).toSet()
            val work =
                if (type == "activity") row else works[first(row, "worklog_id", "work_record_id")]
            val order = orders[text(row, "order_id").ifBlank { text(work, "order_id") }]
            val propertyId =
                text(row, "property_id").ifBlank {
                    text(work, "property_id").ifBlank { text(order, "property_id") }
                }
            val property = properties[propertyId]
            val customerId =
                if (type == "customer") id
                else
                    text(row, "customer_id").ifBlank {
                        text(work, "customer_id").ifBlank {
                            text(order, "customer_id").ifBlank {
                                text(property, "customer_id").ifBlank {
                                    sourceCustomers.singleOrNull().orEmpty()
                                }
                            }
                        }
                    }
            val recipientId =
                if (type == "customer") text(row, "default_billing_customer_id").ifBlank { id }
                else
                    text(row, "billing_customer_id").ifBlank {
                        text(work, "billing_customer_id").ifBlank {
                            text(order, "billing_customer_id").ifBlank { customerId }
                        }
                    }
            val customer = customers[customerId]
            val recipient = customers[recipientId]
            if (
                text(args, "customer_id").isNotBlank() &&
                    text(args, "customer_id") != customerId &&
                    text(args, "customer_id") !in sourceCustomers
            )
                continue
            if (
                text(args, "billing_customer_id").isNotBlank() &&
                    text(args, "billing_customer_id") != recipientId
            )
                continue
            if (
                text(args, "property_id").isNotBlank() &&
                    text(args, "property_id") != (if (type == "property") id else propertyId) &&
                    text(args, "property_id") !in sourceProperties
            )
                continue
            if (
                text(args, "work_record_id").isNotBlank() &&
                    text(args, "work_record_id") != text(work, "id")
            )
                continue
            if (args.has("lcr") && (type != "activity" || (id in lcr) != args.getBoolean("lcr")))
                continue
            if (
                args.has("without_customer") &&
                    customerId.isBlank() != args.getBoolean("without_customer")
            )
                continue
            if (
                args.has("without_time") &&
                    (type !in setOf("appointment", "visit") ||
                        first(row, "starts_at", "window_start").isBlank() !=
                            args.getBoolean("without_time"))
            )
                continue
            if (
                args.has("open_work") &&
                    (customerId in openCustomers) != args.getBoolean("open_work")
            )
                continue
            if (
                text(args, "city").isNotBlank() &&
                    !normalize(text(customer, "city")).contains(normalize(text(args, "city")))
            )
                continue
            if (
                text(args, "category").isNotBlank() &&
                    (type != "material" ||
                        normalize(text(row, "category")) != normalize(text(args, "category")))
            )
                continue
            if (args.has("maintenance_due")) {
                if (type != "equipment") continue
                val due =
                    try {
                        EquipmentMaintenanceRules.nextDue(row).ifBlank {
                            text(row, "maintenance_due")
                        }
                    } catch (_: Exception) {
                        ""
                    }
                if (
                    (due.isNotBlank() && due <= today.toString()) !=
                        args.getBoolean("maintenance_due")
                )
                    continue
            }
            val linkedPayments = payments[id].orEmpty()
            val total = decimal(row.opt("total_minor"))
            val paid =
                linkedPayments.fold(BigDecimal.ZERO) { sum, payment ->
                    sum + (decimal(payment.opt("amount_minor")) ?: BigDecimal.ZERO)
                }
            val paymentStatus =
                if (total == null) ""
                else if (paid >= total && total.signum() > 0) "paid"
                else if (paid.signum() > 0) "partial" else "open"
            val workStatus =
                if (type != "activity") ""
                else if (id in lcr) "lcr"
                else if (!row.optBoolean("billable", true)) "excluded"
                else if (open(row)) "open" else "billed"
            val status = text(row, "status").ifBlank { workStatus }
            if (
                text(args, "status").isNotBlank() &&
                    normalize(text(args, "status")) !in
                        listOf(status, paymentStatus, workStatus).map(::normalize)
            )
                continue
            val actualBasis =
                if (basis != "default") basis
                else
                    when (type) {
                        "activity" -> "work"
                        "settlement",
                        "invoice",
                        "offer" -> "document"
                        "appointment",
                        "visit" -> "planned"
                        "photo" -> "photo"
                        else -> "updated"
                    }
            val dates =
                when (actualBasis) {
                        "work" -> listOf(first(row, "work_date", "service_from"))
                        "document" ->
                            listOf(
                                first(
                                    row,
                                    "issue_date",
                                    "issued_at",
                                    "date",
                                    "created_at",
                                    "updated_at",
                                )
                            )
                        "payment" -> linkedPayments.map { text(it, "date") }
                        "planned" -> listOf(first(row, "date", "starts_at", "scheduled_date"))
                        "created" -> listOf(first(row, "created_at", "received_at"))
                        "photo" ->
                            listOf(first(row, "capture_time", "taken_at", "date", "created_at"))
                        else ->
                            listOf(first(row, "date", "received_at", "updated_at", "created_at"))
                    }
                    .map(::day)
                    .filter(String::isNotBlank)
            val matchingDates = dates.filter {
                (from.isBlank() || it >= from) && (until.isBlank() || it <= until)
            }
            if (
                (from.isNotBlank() || until.isNotBlank() || basis != "default") &&
                    matchingDates.isEmpty()
            )
                continue
            val resultDate = matchingDates.maxOrNull().orEmpty()
            val money = amount(row, type)
            if (
                minimum != null && (money == null || money < minimum) ||
                    maximum != null && (money == null || money > maximum)
            )
                continue
            val notes = first(row, "notes", "note", "concern")
            val content =
                if (noteOnly) listOf(notes)
                else
                    buildList {
                        addAll(values(row, fields[type].orEmpty()))
                        addAll(names(customer))
                        addAll(names(recipient))
                        addAll(values(property, listOf("name", "address")))
                        addAll(values(order, listOf("title", "number", "order_number")))
                        addAll(values(work, listOf("activity", "title")))
                        addAll(names(row.optJSONObject("recipient")))
                        for (source in sourceRows) {
                            addAll(names(customers[text(source, "customer_id")]))
                            addAll(
                                values(
                                    properties[text(source, "property_id")],
                                    listOf("name", "address"),
                                )
                            )
                        }
                        for (position in rows(row.optJSONArray("positions"))) addAll(
                            values(position, positionFields)
                        )
                        if (type == "appointment")
                            rows(revier.optJSONArray("visit"))
                                .filter { text(it, "appointment_id") == id }
                                .forEach { addAll(values(it, fields.getValue("visit"))) }
                    }
            val body = normalize(content.joinToString(" "))
            if (noteOnly && notes.isBlank() || words.any { !body.contains(it) }) continue
            val hitType = if (noteOnly) "note" else type
            if (!seen.add("$hitType:$type:$id:$old")) continue
            val title =
                if (type == "settlement")
                    listOf(
                            text(row, "invoice_number"),
                            names(row.optJSONObject("recipient")).joinToString(" "),
                            text(row, "title"),
                        )
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                else first(row, "name", "title", "activity", "caption", "invoice_number", "number")
            val target = JSONObject().put("type", type).put("id", id)
            val item =
                JSONObject()
                    .put("type", hitType)
                    .put("id", id)
                    .put(
                        "title",
                        title.ifBlank {
                            if (type == "appointment" || type == "visit") "Termin" else id
                        },
                    )
                    .put("date", resultDate)
                    .put("date_basis", actualBasis)
                    .put("archived", archived)
                    .put("target", target)
            item.put("label", item.getString("title"))
            if (noteOnly) item.put("snippet", notes.take(160))
            else {
                item
                    .put("record_hash", RevierRules.hash(row))
                    .put("status", status)
                    .put("customer_id", customerId)
                    .put("billing_customer_id", recipientId)
                    .put("property_id", propertyId)
                target.put("customer_id", customerId).put("property_id", propertyId)
                if (type == "activity") item.put("lcr", id in lcr).put("billing_status", workStatus)
                if (money != null)
                    item
                        .put("amount", money.toPlainString())
                        .put("amount_basis", if (type == "material") "price" else "document")
                if (type == "customer" || type == "property") {
                    text(customer, "phone").takeIf(String::isNotBlank)?.let {
                        item.put("phone", it)
                    }
                    val address =
                        if (type == "property") text(row, "address")
                        else
                            listOf(
                                    text(row, "street"),
                                    listOf(text(row, "postal_code"), text(row, "city"))
                                        .filter(String::isNotBlank)
                                        .joinToString(" "),
                                )
                                .filter(String::isNotBlank)
                                .joinToString(", ")
                    address.takeIf(String::isNotBlank)?.let { item.put("address", it) }
                }
            }
            hits.add(item)
        }
        hits.sortWith(
            compareBy<JSONObject, String>(String.CASE_INSENSITIVE_ORDER) { it.getString("title") }
                .thenBy { it.getString("type") }
                .thenBy { it.getString("id") }
                .thenBy { it.getBoolean("archived") }
        )
        val offset = RevierRules.integer(args, "offset", 0, 1000000, 0)
        val limit = RevierRules.integer(args, "limit", 1, 100, 50)
        val page = hits.drop(offset).take(limit)
        val items = JSONArray(page)
        val groups = JSONArray()
        for ((type, all) in hits.groupBy { it.getString("type") }.toSortedMap()) groups.put(
            JSONObject()
                .put("type", type)
                .put("total", all.size)
                .put("items", JSONArray(page.filter { it.getString("type") == type }))
        )
        return JSONObject()
            .put("items", items)
            .put("results", items)
            .put("groups", groups)
            .put("total", hits.size)
            .put("offset", offset)
            .put("limit", limit)
            .put("date_basis", basis)
            .put("index_scope", "all_local_records")
            .put("index_rebuild", "read_from_canonical_state")
    }
}
