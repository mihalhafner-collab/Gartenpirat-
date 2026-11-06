package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Local accounting assignments, independent of invoices and payment records. */
internal object AccountingRules {
    const val BILLED = "billed"
    const val LABOR = "labor"
    const val AMOUNT = "accounting_amount"
    const val PENDING_AMOUNT = "Rechnungsbetrag ausstehend"
    fun label(route: String) = when (route) { BILLED -> "Abgerechnet"; LABOR -> "labor clandestinus"; else -> "Offen" }
    fun orderRoute(row: JSONObject): String = when (row.optString("status").lowercase(java.util.Locale.ROOT)) {
        "abgerechnet" -> BILLED
        "labor clandestinus" -> LABOR
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
    fun project(master: JSONObject, personal: Set<String>, properties: JSONArray): List<JSONObject> {
        val propertyNames = (0 until properties.length()).map { properties.getJSONObject(it) }.associate { it.getString("id") to it.optString("name", it.optString("title")) }
        val orders = rows(master, "orders")
        val allWorks = rows(master, "worklogs")
        val byOrder = allWorks.groupBy { it.optString("order_id") }
        val orderIds = orders.map { it.getString("id") }.toSet()
        val groups = orders.map { "orders" to it } + allWorks.filter { it.optString("order_id") !in orderIds }.map { "worklogs" to it }
        val output = arrayListOf<JSONObject>()
        for ((collection, record) in groups) {
            val id = record.getString("id")
            val entries = (if (collection == "orders") byOrder[id].orEmpty() else listOf(record)).sortedBy { it.getString("id") }
            val ownRoute = if (collection == "orders") orderRoute(record) else ""
            val routes = linkedSetOf<String>().apply {
                if (ownRoute.isNotEmpty()) add(ownRoute)
                for (work in entries) {
                    if (work.optInt("billed") != 0) add(BILLED)
                    if (work.getString("id") in personal) add(LABOR)
                }
            }
            if (routes.isEmpty()) continue // A payment or an open amount is never an assignment.
            val route = routes.singleOrNull().orEmpty()
            val included = if (ownRoute.isNotEmpty()) entries else entries.filter { it.optInt("billed") != 0 || it.getString("id") in personal }
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
                val ownAmount = minor(record)
                if (ownAmount != null && collection == "orders" && ownRoute.isEmpty() && included.size != entries.size) {
                    if (issue.isEmpty()) { issue = "Gesamtbetrag enthält möglicherweise offene Arbeitseinträge"; issueCode = "open_parts" }
                } else amount = ownAmount ?: if (included.isNotEmpty()) {
                    val amounts = included.map { minor(it) }
                    if (amounts.any { it == null }) null else amounts.fold(0L) { sum, value -> Math.addExact(sum, value!!) }
                } else null
                if (amount == null && issue.isEmpty()) { issue = PENDING_AMOUNT; issueCode = "pending_amount" }
            } catch (_: Exception) { if (issue.isEmpty()) { issue = "Gesamtbetrag ist nicht lesbar"; issueCode = "amount_invalid" } }
            output.add(JSONObject().put("key", "$collection:$id").put("collection", collection).put("id", id)
                .put("title", record.optString(if (collection == "orders") "title" else "activity").ifBlank { "Arbeit ohne Bezeichnung" })
                .put("work_date", date).put("date_label", dateLabel).put("property_ids", JSONArray(propertyIds))
                .put("object_label", objectLabel).put("route", route).put("issue", issue).put("issue_code", issueCode)
                .put("amount_minor", amount ?: JSONObject.NULL).put("expected_record_hash", recordHash(collection, record, entries, personal)))
        }
        return output
    }
}
