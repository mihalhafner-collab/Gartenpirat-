package at.hausgarten.service

import java.math.BigDecimal
import java.time.*
import org.json.JSONArray
import org.json.JSONObject

/** Only explicit object links or known parent references establish membership. */
internal object PropertyTimelineRules {
    private val contextFields =
        listOf(
            "customer_id",
            "property_id",
            "inquiry_id",
            "offer_id",
            "order_id",
            "visit_id",
            "worklog_id",
            "appointment_id",
        )

    fun contextSchema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "properties",
                JSONObject().apply { for (field in contextFields) put(field, McpFields.id()) },
            )
            .put("required", JSONArray())

    fun contextReferences(args: JSONObject): JSONObject =
        JSONObject().apply {
            for (field in contextFields) if (
                !args.isNull(field) && args.optString(field).isNotBlank()
            )
                put(field, args.getString(field))
        }

    private data class ParentProperties(
        val offers: Map<String, Set<String>>,
        val orders: Map<String, Set<String>>,
        val offerRecords: Map<String, JSONObject>,
    )

    private fun directProperty(row: JSONObject): Set<String> =
        if (row.isNull("property_id") || row.optString("property_id").isBlank()) emptySet()
        else setOf(row.getString("property_id"))

    private fun sameCustomer(first: JSONObject, second: JSONObject): Boolean =
        first.isNull("customer_id") ||
            second.isNull("customer_id") ||
            first.optString("customer_id").isBlank() ||
            second.optString("customer_id").isBlank() ||
            first.optString("customer_id") == second.optString("customer_id")

    /** Actual stored inverse references; child records never assign their siblings to an object. */
    private fun parentProperties(master: JSONObject, revier: JSONObject): ParentProperties {
        val inquiries = rows(revier, "inquiries")
        val inquiryById = inquiries.associateBy { it.optString("id") }
        val inquiryByOffer =
            inquiries
                .filter { it.optString("offer_id").isNotBlank() }
                .groupBy { it.getString("offer_id") }
        val inquiryByOrder =
            inquiries
                .filter { it.optString("order_id").isNotBlank() }
                .groupBy { it.getString("order_id") }
        val offers = rows(master, "offers")
        val offerById = offers.associateBy { it.optString("id") }
        val offerByOrder = mutableMapOf<String, MutableList<JSONObject>>()
        for (offer in offers) for (order in
            listOf(offer.optString("order_id"), offer.optString("converted_order_id"))
                .filter(String::isNotBlank)
                .distinct()) offerByOrder.getOrPut(order) { mutableListOf() }.add(offer)
        val offerProperties =
            offers.associate { offer ->
                val direct = directProperty(offer)
                val related =
                    inquiryByOffer[offer.optString("id")].orEmpty() +
                        listOf("inquiry_id", "source_inquiry_id").mapNotNull {
                            inquiryById[offer.optString(it)]
                        }
                offer.optString("id") to
                    if (direct.isNotEmpty()) direct
                    else
                        related
                            .filter { sameCustomer(offer, it) }
                            .flatMap { directProperty(it) }
                            .toSet()
            }
        val orderProperties =
            rows(master, "orders").associate { order ->
                val direct = directProperty(order)
                val relatedInquiries =
                    inquiryByOrder[order.optString("id")].orEmpty() +
                        listOf("inquiry_id", "source_inquiry_id").mapNotNull {
                            inquiryById[order.optString(it)]
                        }
                val relatedOffers =
                    offerByOrder[order.optString("id")].orEmpty() +
                        listOf("offer_id", "source_offer_id").mapNotNull {
                            offerById[order.optString(it)]
                        }
                order.optString("id") to
                    if (direct.isNotEmpty()) direct
                    else
                        (relatedInquiries
                                .filter { sameCustomer(order, it) }
                                .flatMap { directProperty(it) } +
                                relatedOffers
                                    .filter { sameCustomer(order, it) }
                                    .flatMap { offerProperties[it.optString("id")].orEmpty() })
                            .toSet()
            }
        return ParentProperties(offerProperties, orderProperties, offerById)
    }

    private fun appointmentProperties(row: JSONObject, parents: ParentProperties): Set<String> {
        val direct = directProperty(row)
        if (direct.isNotEmpty()) return direct
        val refs = row.optJSONArray("scope_refs") ?: JSONArray()
        val legacy =
            (0 until refs.length())
                .mapNotNull { index ->
                    refs
                        .optString(index)
                        .takeIf { it.startsWith("property:") || it.startsWith("properties:") }
                        ?.substringAfter(':')
                        ?.takeIf(String::isNotBlank)
                }
                .toSet()
        if (legacy.isNotEmpty()) return legacy
        val scoped = mutableSetOf<String>()
        for (index in 0 until refs.length()) {
            val ref = refs.optString(index)
            val split = ref.lastIndexOf('#')
            if (split < 1) continue
            val position = ref.substring(split + 1).toIntOrNull() ?: continue
            val offer = parents.offerRecords[ref.substring(0, split)] ?: continue
            if (
                offer.optString("status") != "accepted" ||
                    !sameCustomer(row, offer) ||
                    row.optString("order_id").isBlank() ||
                    row.getString("order_id") !=
                        offer.optString("converted_order_id", offer.optString("order_id")) ||
                    position !in 0 until (offer.optJSONArray("positions")?.length() ?: 0)
            )
                continue
            scoped.addAll(parents.offers[offer.optString("id")].orEmpty())
        }
        if (scoped.isNotEmpty()) return scoped
        return parents.orders[row.optString("order_id")].orEmpty().takeIf { it.size == 1 }
            ?: emptySet()
    }

    /** Resolve known parents only. A customer never assigns an object or sibling records. */
    fun linkedContext(args: JSONObject, master: JSONObject, revier: JSONObject): JSONObject {
        val context = contextReferences(args)
        val properties = rows(revier, "properties").associateBy { it.optString("id") }
        val inquiries = rows(revier, "inquiries")
        val offers = rows(master, "offers")
        val parents = parentProperties(master, revier)
        val parentRows =
            listOf(
                    "appointment_id" to rows(master, "appointments"),
                    "worklog_id" to rows(master, "worklogs"),
                    "visit_id" to rows(revier, "visits"),
                    "inquiry_id" to inquiries,
                    "offer_id" to offers,
                    "order_id" to rows(master, "orders"),
                    "property_id" to properties.values.toList(),
                )
                .map { (key, records) -> key to records.associateBy { it.optString("id") } }
        fun compatible(parent: JSONObject) =
            context.optString("customer_id").isBlank() ||
                parent.isNull("customer_id") ||
                parent.optString("customer_id").isBlank() ||
                parent.optString("customer_id") == context.optString("customer_id")
        fun adopt(parentField: String, parent: JSONObject?) {
            if (parent == null) return
            if (!compatible(parent)) {
                context.put("context_conflict", true)
                return
            }
            for (field in contextFields) if (
                !parent.isNull(field) && parent.optString(field).isNotBlank()
            ) {
                if (context.optString(field).isBlank()) context.put(field, parent.getString(field))
                else if (context.optString(field) != parent.optString(field))
                    context.put("context_conflict", true)
            }
            if (parentField == "appointment_id" && context.optString("property_id").isBlank()) {
                val selected = appointmentProperties(parent, parents)
                if (selected.size == 1) context.put("property_id", selected.single())
                else if (selected.size > 1) context.put("context_conflict", true)
            }
            if (
                parentField == "offer_id" &&
                    context.optString("order_id").isBlank() &&
                    !parent.isNull("converted_order_id") &&
                    parent.optString("converted_order_id").isNotBlank()
            )
                context.put("order_id", parent.getString("converted_order_id"))
            if (parentField in setOf("offer_id", "order_id"))
                for ((source, target) in
                    listOf("source_offer_id" to "offer_id", "source_inquiry_id" to "inquiry_id")) {
                    if (
                        context.optString(target).isBlank() &&
                            !parent.isNull(source) &&
                            parent.optString(source).isNotBlank()
                    )
                        context.put(target, parent.getString(source))
                }
        }
        repeat(4) {
            for ((field, records) in parentRows) adopt(field, records[context.optString(field)])
            if (context.optString("property_id").isBlank()) {
                val candidates =
                    parents.offers[context.optString("offer_id")].orEmpty().takeIf {
                        it.isNotEmpty()
                    } ?: parents.orders[context.optString("order_id")].orEmpty()
                if (candidates.size == 1 && !context.optBoolean("context_conflict")) {
                    val property = properties[candidates.single()]
                    if (property == null || compatible(property))
                        context.put("property_id", candidates.single())
                    else context.put("context_conflict", true)
                } else if (candidates.size > 1) context.put("context_conflict", true)
            }
        }
        return context
    }

    fun schema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "properties",
                JSONObject()
                    .put("property_id", McpFields.id())
                    .put("view", McpFields.enumeration("history", "care", "photos", "money"))
                    .put("offset", McpFields.number(0, 1000000))
                    .put("limit", McpFields.number(1, 50)),
            )
            .put("required", JSONArray().put("property_id"))

    private fun rows(source: JSONObject, key: String) =
        SettlementRules.rows(source.optJSONArray(key))

    private fun ids(rows: List<JSONObject>) =
        rows.map { it.optString("id") }.filter(String::isNotBlank).toSet()

    private fun references(row: JSONObject, keys: List<String>, known: Set<String>) =
        keys.any { row.optString(it).isNotBlank() && row.optString(it) in known }

    private fun explicit(row: JSONObject, propertyId: String): Boolean? {
        row.optString("property_id").takeIf(String::isNotBlank)?.let {
            return it == propertyId
        }
        val scopes = row.optJSONArray("scope_refs") ?: return null
        val properties =
            (0 until scopes.length()).mapNotNull {
                scopes
                    .optString(it)
                    .takeIf { ref -> ref.startsWith("property:") || ref.startsWith("properties:") }
                    ?.substringAfter(':')
            }
        return if (properties.isEmpty()) null else propertyId in properties
    }

    private fun belongs(row: JSONObject, propertyId: String, inherited: () -> Boolean = { false }) =
        explicit(row, propertyId) ?: inherited()

    private fun date(row: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = row.optString(key)
            if (value.isBlank()) continue
            try {
                return LocalDate.parse(value).toString()
            } catch (_: DateTimeException) {}
            try {
                return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.of("Europe/Vienna"))
                    .toOffsetDateTime()
                    .toString()
            } catch (_: DateTimeException) {}
            try {
                return LocalDateTime.parse(value).toLocalDate().toString()
            } catch (_: DateTimeException) {}
        }
        return ""
    }

    private fun amount(row: JSONObject, minor: String, decimal: String = ""): Long? =
        try {
            if (row.has(minor) && !row.isNull(minor))
                BigDecimal(row.get(minor).toString()).longValueExact()
            else if (decimal.isNotBlank() && row.has(decimal) && !row.isNull(decimal))
                SettlementRules.minor(row.get(decimal))
            else null
        } catch (_: Exception) {
            null
        }

    fun build(
        propertyId: String,
        master: JSONObject,
        revier: JSONObject,
        ledger: JSONObject,
        view: String = "history",
        offset: Int = 0,
        limit: Int = 50,
    ): JSONObject {
        require(
            view in setOf("history", "care", "photos", "money") &&
                offset in 0..1000000 &&
                limit in 1..50
        )
        val parents = parentProperties(master, revier)
        val inquiryOwned = rows(revier, "inquiries").filter { belongs(it, propertyId) }
        val inquiryOwnedIds = ids(inquiryOwned)
        val linkedOfferIds =
            inquiryOwned.map { it.optString("offer_id") }.filter(String::isNotBlank).toSet()
        val offers =
            rows(master, "offers").filter {
                belongs(it, propertyId) {
                    references(it, listOf("inquiry_id", "source_inquiry_id"), inquiryOwnedIds) ||
                        it.optString("id") in linkedOfferIds
                }
            }
        val offerIds = ids(offers)
        val linkedOrderIds =
            (inquiryOwned + offers)
                .flatMap { listOf(it.optString("order_id"), it.optString("converted_order_id")) }
                .filter(String::isNotBlank)
                .toSet()
        val ordersOwned =
            rows(master, "orders").filter {
                belongs(it, propertyId) {
                    references(it, listOf("inquiry_id", "source_inquiry_id"), inquiryOwnedIds) ||
                        references(it, listOf("offer_id", "source_offer_id"), offerIds) ||
                        it.optString("id") in linkedOrderIds
                }
            }
        val orderOwnedIds = ids(ordersOwned)
        val exclusiveOrderIds =
            orderOwnedIds.filter { parents.orders[it].orEmpty() == setOf(propertyId) }.toSet()
        val visits =
            rows(revier, "visits").filter {
                belongs(it, propertyId) { references(it, listOf("order_id"), exclusiveOrderIds) }
            }
        val visitIds = ids(visits)
        val works =
            rows(master, "worklogs").filter {
                belongs(it, propertyId) {
                    references(it, listOf("order_id"), exclusiveOrderIds) ||
                        references(it, listOf("visit_id"), visitIds)
                }
            }
        val workIds = ids(works)
        val relatedOrders =
            (works + visits + inquiryOwned + offers)
                .map { it.optString("order_id") }
                .filter(String::isNotBlank)
                .toSet()
        val orders =
            rows(master, "orders").filter {
                it.optString("id") in orderOwnedIds ||
                    it.optString("id") in relatedOrders && explicit(it, propertyId) != false
            }
        val orderIds = ids(orders)
        val relatedOffers =
            orders
                .map { it.optString("source_offer_id", it.optString("offer_id")) }
                .filter(String::isNotBlank)
                .toSet() + inquiryOwned.map { it.optString("offer_id") }.filter(String::isNotBlank)
        val allOffers =
            (offers +
                    rows(master, "offers").filter {
                        it.optString("id") in relatedOffers && explicit(it, propertyId) != false
                    })
                .distinctBy { it.optString("id") }
        val relatedInquiries =
            (orders + allOffers)
                .map { it.optString("source_inquiry_id", it.optString("inquiry_id")) }
                .filter(String::isNotBlank)
                .toSet()
        val inquiries =
            (inquiryOwned +
                    rows(revier, "inquiries").filter {
                        it.optString("id") in relatedInquiries && explicit(it, propertyId) != false
                    })
                .distinctBy { it.optString("id") }
        val inspectionIds =
            inquiries
                .map { it.optString("inspection_appointment_id") }
                .filter(String::isNotBlank)
                .toSet()
        val appointments =
            rows(master, "appointments").filter {
                belongs(it, propertyId) {
                    propertyId in appointmentProperties(it, parents) ||
                        it.optString("id") in inspectionIds
                }
            }
        val photos =
            rows(master, "photos").filter {
                belongs(it, propertyId) {
                    references(it, listOf("worklog_id", "work_record_id"), workIds) ||
                        references(it, listOf("visit_id"), visitIds) ||
                        references(it, listOf("order_id"), exclusiveOrderIds)
                }
            }
        val care = rows(revier, "care_plans").filter { belongs(it, propertyId) }
        val legacyInvoices =
            rows(master, "invoices").filter {
                belongs(it, propertyId) {
                    references(it, listOf("order_id", "source_order_id"), orderIds) ||
                        references(it, listOf("worklog_id", "work_record_id"), workIds)
                }
            }
        val settlements =
            SettlementRules.records(ledger).filter { record ->
                belongs(record, propertyId) {
                    rows(record, "sources").any { source ->
                        when (source.optString("collection")) {
                            "worklogs" -> source.optString("id") in workIds
                            "orders" -> source.optString("id") in orderIds
                            else -> false
                        }
                    }
                }
            }
        val settlementIds = ids(settlements)
        val payments =
            rows(ledger, "payments").filter { it.optString("settlement_id") in settlementIds }
        val history = mutableListOf<JSONObject>()
        fun event(
            kind: String,
            row: JSONObject,
            title: String,
            route: String,
            at: String,
            filled: Boolean,
            target: String = row.optString("id"),
            key: String = "$kind:${row.optString("id")}",
            amountMinor: Long? = null,
            amountScope: String = "",
        ): JSONObject {
            val result =
                JSONObject()
                    .put("key", key)
                    .put("kind", kind)
                    .put("record_kind", row.optString("kind"))
                    .put("id", row.optString("id"))
                    .put("title", title)
                    .put("route", route)
                    .put("target_id", target)
                    .put("at", at)
                    .put("status", row.optString("status"))
                    .put("filled", filled)
            if (amountMinor != null)
                result
                    .put("amount_minor", amountMinor)
                    .put("amount_scope", amountScope)
                    .put(
                        "currency",
                        row.optString("currency").ifBlank {
                            if (kind in setOf("settlement", "payment")) "EUR" else ""
                        },
                    )
            return result
        }
        for (row in inquiries) history +=
            event(
                "inquiry",
                row,
                row.optString("title", "Anfrage"),
                "inquiries",
                date(row, "received_at", "created_at"),
                true,
            )
        for (row in allOffers) history +=
            event(
                "offer",
                row,
                row.optString("title", row.optString("offer_number", "Angebot")),
                "offers",
                date(row, "issue_date", "created_at"),
                row.optString("status").lowercase() in
                    setOf(
                        "issued",
                        "sent",
                        "accepted",
                        "declined",
                        "rejected",
                        "cancelled",
                        "confirmed",
                    ),
            )
        for (row in orders) history +=
            event(
                "order",
                row,
                row.optString("title", "Auftrag"),
                "orders",
                date(row, "accepted_at", "created_at"),
                row.optString("status") in setOf("Erledigt", "Abgerechnet"),
            )
        for (row in visits) history +=
            event(
                "visit",
                row,
                row.optString("title", "Einsatz"),
                "visits",
                date(row, "date"),
                row.optString("status") == "completed",
            )
        for (row in appointments) history +=
            event(
                "appointment",
                row,
                row.optString("title", "Einsatz"),
                "planning",
                date(row, "starts_at"),
                row.optString("status") == "completed",
            )
        for (row in works) history +=
            event(
                "worklog",
                row,
                row.optString("activity", "Arbeit"),
                "worklogs",
                date(row, "work_date"),
                true,
            )
        val photoItems =
            photos.map { row ->
                event(
                        "photo",
                        row,
                        row.optString("caption").ifBlank { row.optString("original_name", "Foto") },
                        "photos",
                        date(row, "capture_time", "created_at"),
                        true,
                    )
                    .put("record", SnapshotReadSupport.withoutBinary(row))
            }
        history += photoItems
        val documents =
            settlements.map { row ->
                event(
                    "settlement",
                    row,
                    row.optString("title").ifBlank { row.optString("invoice_number", "Abschluss") },
                    "settlements",
                    date(row, "issue_date", "confirmed_at", "created_at"),
                    row.optString("status") in setOf("confirmed", "cancelled"),
                    amountMinor = amount(row, "total_minor"),
                    amountScope = "document",
                )
            } +
                legacyInvoices.map { row ->
                    event(
                        "invoice",
                        row,
                        row.optString("invoice_number", "Rechnung"),
                        "invoices",
                        date(row, "issue_date", "created_at"),
                        row.optString("status").lowercase() in
                            setOf(
                                "issued",
                                "sent",
                                "paid",
                                "cancelled",
                                "ausgestellt",
                                "versendet",
                                "bezahlt",
                                "storniert",
                            ),
                        amountMinor = amount(row, "total_minor", "gross_total"),
                        amountScope = "document",
                    )
                }
        val paymentItems =
            payments.mapIndexed { index, row ->
                event(
                    "payment",
                    row,
                    "Zahlung zum Beleg",
                    "settlements",
                    date(row, "date", "paid_at", "created_at"),
                    true,
                    target = row.getString("settlement_id"),
                    key =
                        "payment:${row.optString("id").ifBlank { row.getString("settlement_id") + ":" + index }}",
                    amountMinor = amount(row, "amount_minor"),
                    amountScope = "payment",
                )
            }
        history += documents + paymentItems
        val careItems =
            care.map { row ->
                event(
                        "care_plan",
                        row,
                        row.optString("title", "Pflegeplan"),
                        "care_plans",
                        date(row, "anchor_date", "created_at"),
                        false,
                    )
                    .put("record", JSONObject(row.toString()))
            }
        val groups =
            mapOf(
                "history" to history,
                "care" to careItems,
                "photos" to photoItems,
                "money" to documents + paymentItems,
            )
        val selected =
            groups
                .getValue(view)
                .sortedWith(
                    compareBy<JSONObject> { it.optString("at").ifBlank { "~" } }
                        .thenBy { it.getString("kind") }
                        .thenBy { it.getString("key") }
                )
        val counts =
            JSONObject().apply {
                for ((name, entries) in groups) put(name, entries.size)
                put("documents", documents.size)
                put("payments", paymentItems.size)
            }
        return JSONObject()
            .put("view", view)
            .put("items", JSONArray(selected.drop(offset).take(limit)))
            .put("total", selected.size)
            .put("has_more", offset + limit < selected.size)
            .put("offset", offset)
            .put("limit", limit)
            .put("counts", counts)
    }
}
