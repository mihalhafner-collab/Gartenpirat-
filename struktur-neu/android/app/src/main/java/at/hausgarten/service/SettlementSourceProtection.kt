package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Protect retained service evidence without treating user markings as payments or bindings. */
internal object SettlementSourceProtection {
    private val workFields =
        arrayOf(
            "customer_id",
            "billing_customer_id",
            "property_id",
            "billable",
            "order_id",
            "work_date",
            "start_time",
            "end_time",
            "break_minutes",
            "hours",
            "activity",
            "material",
            "material_cost",
            "kilometers",
            "disposal_cost",
        )
    private val orderFields =
        arrayOf(
            "customer_id",
            "billing_customer_id",
            "property_id",
            "title",
            "description",
            "location",
            "scheduled_date",
        )

    fun requireMutable(
        master: JSONObject,
        ledger: JSONObject,
        collection: String,
        id: String,
        previous: JSONObject?,
        next: JSONObject,
    ) {
        if (id.isEmpty()) return
        if (previous == null) {
            SettlementRules.requireMutable(ledger, collection, id)
            return
        }
        val fields = if (collection == "worklogs") workFields else orderFields
        if (fields.any { next.has(it) && normalized(previous, it) != normalized(next, it) }) {
            SettlementRules.requireMutable(ledger, collection, id)
            if (collection == "orders") {
                for (work in AccountingRules.works(master, collection, id)) {
                    SettlementRules.requireMutable(ledger, "worklogs", work.getString("id"))
                }
            }
        }
        val previousRoute =
            if (collection == "orders") AccountingRules.orderRoute(previous)
            else if (previous.optInt("billed") != 0) AccountingRules.BILLED else ""
        val nextRoute =
            if (collection == "orders") AccountingRules.orderRoute(next)
            else if (next.optInt("billed") != 0) AccountingRules.BILLED else ""
        if (previousRoute != nextRoute && (collection == "orders" || nextRoute.isNotEmpty())) {
            val (groupCollection, group) = AccountingRules.canonical(master, collection, id)
            requireRouteChange(master, ledger, groupCollection, group.getString("id"), nextRoute)
        }
    }

    private fun normalized(row: JSONObject, field: String): String =
        if (row.isNull(field)) "" else row.opt(field)?.toString().orEmpty()

    fun requireDeletable(master: JSONObject, ledger: JSONObject, entity: String, id: String) {
        if (id.isEmpty()) return
        val collection =
            when (entity) {
                "order" -> "orders"
                "worklog" -> "worklogs"
                else -> ""
            }
        if (collection.isNotEmpty()) SettlementRules.requireMutable(ledger, collection, id)
        if (entity !in setOf("order", "customer")) return
        val orders = master.optJSONArray("orders") ?: JSONArray()
        val works = master.optJSONArray("worklogs") ?: JSONArray()
        for (i in 0 until orders.length()) {
            val row = orders.getJSONObject(i)
            if (
                entity == "customer" &&
                    (row.optString("customer_id") == id ||
                        row.optString("billing_customer_id") == id)
            ) {
                SettlementRules.requireMutable(ledger, "orders", row.getString("id"))
            }
        }
        for (i in 0 until works.length()) {
            val row = works.getJSONObject(i)
            if (
                entity == "order" && row.optString("order_id") == id ||
                    entity == "customer" &&
                        (row.optString("customer_id") == id ||
                            row.optString("billing_customer_id") == id)
            ) {
                SettlementRules.requireMutable(ledger, "worklogs", row.getString("id"))
            }
        }
    }

    fun requireRouteChange(
        master: JSONObject,
        ledger: JSONObject,
        collection: String,
        id: String,
        route: String,
    ) {
        val records = ledger.optJSONArray("records") ?: return
        val groupWorkIds =
            AccountingRules.works(master, collection, id).map { it.getString("id") }.toSet()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            if (
                record.optString("status") !in setOf("prepared", "confirmed") ||
                    record.optString("kind") !in setOf("invoice", "internal")
            )
                continue
            val sources = record.optJSONArray("sources") ?: continue
            val matching =
                (0 until sources.length()).any {
                    val source = sources.getJSONObject(it)
                    source.optString("collection") == collection && source.optString("id") == id ||
                        source.optString("collection") == "worklogs" &&
                            source.optString("id") in groupWorkIds
                }
            if (matching && record.optString("route") != route) {
                throw PixelMcpWriteTools.Rejected(
                    "SETTLEMENT_ROUTE_BOUND",
                    "Diese Leistung ist bereits gebunden. Zuerst den bisherigen Vorgang ausdrücklich stornieren oder intern aufheben, danach die Abrechnungsart ändern.",
                )
            }
        }
    }
}
