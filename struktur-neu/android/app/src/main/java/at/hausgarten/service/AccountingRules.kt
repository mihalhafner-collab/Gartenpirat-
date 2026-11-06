package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Local accounting assignments, independent of invoices and payment records. */
internal object AccountingRules {
    const val BILLED = "billed"
    const val LABOR = "labor"
    const val AMOUNT = "accounting_amount"
    const val PENDING_AMOUNT = "Abrechnungsbetrag ausstehend"
    fun label(route: String) = when (route) { BILLED -> "Rechnung"; LABOR -> "Labor clandestino"; else -> "Offen" }
    fun orderStatus(route: String) = when (route) { BILLED -> "Abgerechnet"; LABOR -> "labor clandestinus"; else -> "Erledigt" }
    fun orderRoute(row: JSONObject): String = when (row.optString("status").lowercase(java.util.Locale.ROOT)) {
        "abgerechnet", "rechnung" -> BILLED
        "labor clandestinus", "labor clandestino" -> LABOR
        else -> ""
    }
    fun ids(values: JSONArray): Set<String> = (0 until values.length()).map { values.getString(it) }.toSet()
    private fun rows(master: JSONObject, collection: String): List<JSONObject> {
        val values = master.optJSONArray(collection) ?: JSONArray()
        return (0 until values.length()).map { values.getJSONObject(it) }.distinctBy { it.getString("id") }
    }
    private fun minor(row: JSONObject): Long? {
        if (!row.has(AMOUNT) || row.isNull(AMOUNT) || row.optString(AMOUNT).isBlank()) return null
        return OfferMoney.minor(row.get(AMOUNT)).also { require(it >= 0) }
    }
    fun canonical(master: JSONObject, collection: String, id: String): Pair<String, JSONObject> {
        require(collection in setOf("orders", "worklogs")) { "Ungültige Auftragsart." }
        val record = PixelMcpWriteTools.find(master.getJSONArray(collection), id)
        val order = if (collection == "worklogs") record.optString("order_id") else ""
        if (order.isNotEmpty()) rows(master, "orders").firstOrNull { it.optString("id") == order }?.let { return "orders" to it }
        return collection to record
    }
    fun works(master: JSONObject, collection: String, id: String): List<JSONObject> =
        rows(master, "worklogs").filter { if (collection == "orders") it.optString("order_id") == id else it.optString("id") == id }

    fun hash(master: JSONObject, personal: Set<String>, collection: String, id: String): String {
        val (kind, record) = canonical(master, collection, id)
        val entries = works(master, kind, record.getString("id")).sortedBy { it.getString("id") }
        return recordHash(kind, record, entries, personal)
    }
    private fun recordHash(kind: String, record: JSONObject, entries: List<JSONObject>, personal: Set<String>): String =
        PixelMcpWriteTools.hash(JSONObject().put("collection", kind).put("record", record)
            .put("works", JSONArray(entries)).put("labor_ids", JSONArray(entries.map { it.getString("id") }.filter { it in personal })))
    private fun tokens(master: JSONObject, personal: Set<String>, collection: String = "", replacement: JSONObject? = null): Map<String, Set<String>> {
        fun records(kind: String): List<JSONObject> {
            val original = rows(master, kind)
            if (kind != collection || replacement == null) return original
            val id = replacement.getString("id")
            return original.filterNot { it.getString("id") == id } + replacement
        }
        val orders = records("orders").associateBy { it.getString("id") }
        val output = linkedMapOf<String, MutableSet<String>>()
        for ((id, row) in orders) orderRoute(row).takeIf { it.isNotEmpty() }?.let { output.getOrPut("orders:$id") { linkedSetOf() }.add("orders:$id:$it") }
        for (row in records("worklogs")) {
            val id = row.getString("id")
            val parent = row.optString("order_id").takeIf { it in orders }
            val group = if (parent == null) "worklogs:$id" else "orders:$parent"
            val values = output.getOrPut(group) { linkedSetOf() }
            if (row.optInt("billed") != 0) values.add("worklogs:$id:$BILLED")
            if (id in personal) values.add("worklogs:$id:$LABOR")
        }
        return output
    }
    /** Existing conflicts stay visible; no writer may introduce or extend one. */
    fun requireConsistentChange(before: JSONObject, after: JSONObject, oldPersonal: Set<String>, newPersonal: Set<String>) {
        requireConsistentTokens(tokens(before, oldPersonal), tokens(after, newPersonal))
    }
    private fun requireConsistentTokens(old: Map<String, Set<String>>, next: Map<String, Set<String>>) {
        for ((key, values) in next) {
            if (values.map { it.substringAfterLast(':') }.toSet().size > 1 && !old[key].orEmpty().containsAll(values)) {
                throw PixelMcpWriteTools.Rejected("ACCOUNTING_ROUTE_CONFLICT", "Abgerechnet und labor clandestinus widersprechen sich. In Abrechnung den gesamten Auftrag eindeutig zuordnen.")
            }
        }
    }
    fun requireRecordChange(master: JSONObject, personal: Set<String>, collection: String, record: JSONObject) {
        requireConsistentTokens(tokens(master, personal), tokens(master, personal, collection, record))
    }

    /** One row per order; standalone work stays a standalone accounting unit. */
    @JvmOverloads
    fun project(master: JSONObject, personal: Set<String>, properties: JSONArray, ledger: JSONObject = JSONObject()): List<JSONObject> {
        val propertyNames = (0 until properties.length()).map { properties.getJSONObject(it) }.associate { it.getString("id") to it.optString("name", it.optString("title")) }
        val orders = rows(master, "orders")
        val allWorks = rows(master, "worklogs")
        val byOrder = allWorks.groupBy { it.optString("order_id") }
        val orderIds = orders.map { it.getString("id") }.toSet()
        val orderById = orders.associateBy { it.getString("id") }
        val workById = allWorks.associateBy { it.getString("id") }
        val groups = orders.map { "orders" to it } + allWorks.filter { it.optString("order_id") !in orderIds }.map { "worklogs" to it }
        val output = arrayListOf<JSONObject>()
        val originals = SettlementRules.records(ledger).distinctBy { it.getString("id") }.filter { it.optString("kind") in setOf("invoice", "internal") && it.optString("status") in setOf("prepared", "confirmed", "cancelled", "abandoned") }
        fun sourceGroup(collection: String, id: String): String? {
            val source = when (collection) { "orders" -> orderById[id]; "worklogs" -> workById[id]; else -> null } ?: return null
            val parent = source.optString("order_id")
            return if (collection == "worklogs" && parent in orderIds) "orders:$parent" else "$collection:$id"
        }
        fun references(row: JSONObject) = SettlementRules.rows(row.optJSONArray("sources"))
        val history = originals.flatMap { row -> references(row).mapNotNull { sourceGroup(it.optString("collection"), it.optString("id")) }.distinct().map { it to row } }.groupBy({ it.first }, { it.second })
        fun boundWorks(row: JSONObject, key: String): Set<String> {
            val result = linkedSetOf<String>()
            for (source in references(row)) if (sourceGroup(source.optString("collection"), source.optString("id")) == key) {
                if (source.optString("collection") == "worklogs") result.add(source.getString("id"))
                else for (snapshot in SettlementRules.rows(row.optJSONArray("source_snapshots"))) {
                    if (snapshot.optString("collection") == "orders" && snapshot.optString("id") == source.optString("id")) {
                        val ids = snapshot.optJSONArray("included_work_ids") ?: JSONArray()
                        for (index in 0 until ids.length()) result.add(ids.getString(index))
                    }
                }
            }
            return result
        }
        for ((collection, record) in groups) {
            val id = record.getString("id")
            val key = "$collection:$id"
            val linked = history[key].orEmpty()
            val active = linked.filter(SettlementRules::active)
            val activeWorks = active.flatMap { boundWorks(it, key) }.toSet()
            val entries = (if (collection == "orders") byOrder[id].orEmpty() else listOf(record)).sortedBy { it.getString("id") }
            val ownRoute = if (collection == "orders") orderRoute(record) else ""
            val routes = linkedSetOf<String>().apply {
                if (ownRoute.isNotEmpty()) add(ownRoute)
                for (settlement in active) add(settlement.getString("route"))
                for (work in entries) {
                    if (work.optInt("billed") != 0) add(BILLED)
                    if (work.getString("id") in personal) add(LABOR)
                }
            }
            if (routes.isEmpty()) continue // A payment or an open amount is never an assignment.
            val route = routes.singleOrNull().orEmpty()
            val included = if (ownRoute.isNotEmpty()) entries else entries.filter { it.optInt("billed") != 0 || it.getString("id") in personal || it.getString("id") in activeWorks }
            val date = included.map { it.optString("work_date") }.filter { it.isNotEmpty() }.maxOrNull().orEmpty()
                .ifEmpty { if (collection == "orders") record.optString("scheduled_date") else record.optString("work_date") }
            val dateLabel = if (included.any { it.optString("work_date").isNotEmpty() }) "Letztes Arbeitsdatum" else "Geplantes Auftragsdatum"
            val propertyIds = (listOf(record) + included).map { it.optString("property_id") }.filter { it.isNotEmpty() }.distinct().sorted()
            val objectLabel = propertyIds.map { propertyNames[it].orEmpty().ifBlank { "Objekt nicht mehr vorhanden" } }.joinToString(", ")
                .ifBlank { record.optString("location").ifBlank { "Ohne Objektzuordnung" } }
            var issue = if (routes.size > 1) "Widersprüchliche Abrechnungswege" else ""
            var issueCode = if (routes.size > 1) "route_conflict" else ""
            if (issue.isEmpty() && collection == "worklogs" && record.optString("order_id").isNotEmpty()) { issue = "Zugeordneter Auftrag fehlt"; issueCode = "missing_order" }
            var amount: Long? = null
            try {
                if (linked.isNotEmpty()) {
                    val known = linked.filter { it.optString("status") in setOf("confirmed", "cancelled") }
                    val covered = known.flatMap { boundWorks(it, key) }.toSet()
                    val selectedPositions = active.filter { it.optString("status") == "confirmed" }.flatMap { completed ->
                        SettlementRules.rows(completed.optJSONArray("positions")).distinctBy { it.getString("id") }.filter { sourceGroup(it.optString("source_collection"), it.optString("source_id")) == key }
                    }
                    val knownScope = known.any { completed -> SettlementRules.rows(completed.optJSONArray("positions")).any { sourceGroup(it.optString("source_collection"), it.optString("source_id")) == key } }
                    val incomplete = entries.any { it.getString("id") !in covered } || active.any { it.optString("status") == "prepared" }
                    amount = if (knownScope) selectedPositions.fold(0L) { sum, position -> Math.addExact(sum, position.getLong("amount_minor")) } else null
                    if (issue.isEmpty() && incomplete && amount != null) { issue = "Bekannter Teilbetrag; weiterer Abrechnungsbetrag ausstehend"; issueCode = "partial_amount" }
                } else {
                val ownAmount = minor(record)
                if (ownAmount != null && collection == "orders" && ownRoute.isEmpty() && included.size != entries.size) {
                    if (issue.isEmpty()) { issue = "Gesamtbetrag enthält möglicherweise offene Arbeitseinträge"; issueCode = "open_parts" }
                } else amount = ownAmount ?: if (included.isNotEmpty()) {
                    val amounts = included.map { minor(it) }
                    if (amounts.any { it == null }) null else amounts.fold(0L) { sum, value -> Math.addExact(sum, value!!) }
                } else null
                }
                if (amount == null && issue.isEmpty()) { issue = PENDING_AMOUNT; issueCode = "pending_amount" }
            } catch (_: Exception) { if (issue.isEmpty()) { issue = "Gesamtbetrag ist nicht lesbar"; issueCode = "amount_invalid" } }
            output.add(JSONObject().put("key", "$collection:$id").put("collection", collection).put("id", id)
                .put("title", record.optString(if (collection == "orders") "title" else "activity").ifBlank { "Arbeit ohne Bezeichnung" })
                .put("work_date", date).put("date_label", dateLabel).put("property_ids", JSONArray(propertyIds))
                .put("object_label", objectLabel).put("route", route).put("issue", issue).put("issue_code", issueCode)
                .put("amount_minor", amount ?: JSONObject.NULL).put("expected_record_hash", recordHash(collection, record, entries, personal)))
        }
        // Manual lines form an intentional unit; no synthetic order or worklog is created.
        for (settlement in originals) {
            if (settlement.optString("status") == "abandoned") continue
            val lines = SettlementRules.rows(settlement.optJSONArray("positions")).distinctBy { it.getString("id") }
            val manual = lines.filter { it.optString("source_collection").isEmpty() }
            val missing = lines.filter { it.optString("source_collection").isNotEmpty() && sourceGroup(it.optString("source_collection"), it.optString("source_id")) == null }
            if (manual.isEmpty() && missing.isEmpty()) continue
            val state = settlement.getString("status")
            val confirmed = state in setOf("confirmed", "cancelled")
            val issueCode = if (missing.isNotEmpty()) "missing_source" else if (!confirmed) "pending_amount" else ""
            val amount = if (!confirmed || missing.isNotEmpty()) null else if (state == "cancelled") 0L else manual.fold(0L) { sum, line -> Math.addExact(sum, line.getLong("amount_minor")) }
            val property = settlement.optString("property_id")
            output.add(JSONObject().put("key", "settlements:" + settlement.getString("id")).put("collection", "settlements").put("id", settlement.getString("id"))
                .put("title", settlement.optString("title").ifBlank { "Manuelle Abrechnung" } + if (manual.size != lines.size) " · manuelle Leistungen" else "")
                .put("work_date", settlement.optString("service_to")).put("date_label", "Leistungsdatum (manuell)")
                .put("property_ids", JSONArray(if (property.isEmpty()) emptyList<String>() else listOf(property)))
                .put("object_label", if (property.isEmpty()) "Ohne Objektzuordnung" else propertyNames[property].orEmpty().ifBlank { "Objekt nicht mehr vorhanden" })
                .put("route", settlement.getString("route")).put("issue", when (issueCode) { "missing_source" -> "Gespeicherte Leistungsquelle fehlt; Belegbezug prüfen"; "pending_amount" -> PENDING_AMOUNT; else -> "" }).put("issue_code", issueCode)
                .put("amount_minor", amount ?: JSONObject.NULL).put("expected_record_hash", SettlementRules.hash(settlement)))
        }
        return output
    }
}
