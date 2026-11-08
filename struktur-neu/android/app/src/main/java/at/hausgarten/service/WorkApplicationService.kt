package at.hausgarten.service

import java.util.*
import org.json.*

/** Work use cases. Effects pass through the existing owner repository transaction. */
class WorkApplicationService : OwnerCommand {
    private val repository: OwnerRepository

    constructor(repository: OwnerRepository) {
        this.repository = repository
    }

    @Throws(Exception::class)
    override fun perform(name: String, args: JSONObject, request: String): JSONObject {
        var all: JSONObject = repository.effective()
        var stamp: String = java.time.Instant.now().toString()
        when ((name)) {
            "set_accounting_record" -> return setAccounting(all, args, request)
            "set_work_record_billed",
            "reassign_work_record" -> {
                run {
                    var old: JSONObject =
                        PixelMcpWriteTools.find(
                            all.getJSONArray("worklogs"),
                            args.getString("work_record_id"),
                        )
                    if (
                        (!PixelMcpWriteTools.hash(PixelMcpWriteTools.base(old))
                            .equals(args.getString("expected_record_hash")))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "WRITE_CONFLICT",
                            "Arbeitsaufzeichnung wurde verändert.",
                        )
                    }
                    var p: JSONObject = LocalJournalRules.clean(old)
                    if ((name.equals("set_work_record_billed"))) {
                        p.put("billed", (if (args.getBoolean("billed")) 1 else 0))
                    } else {
                        p.put("customer_id", args.getString("customer_id"))
                            .put("billing_customer_id", args.getString("billing_customer_id"))
                            .put("order_id", args.optString("order_id"))
                    }
                    SettlementSourceProtection.requireMutable(
                        all,
                        JSONObject(repository.ownerValue("settlement:v1", "{}")),
                        "worklogs",
                        old.getString("id"),
                        old,
                        p,
                    )
                    repository.replaceWorklog(p)
                    p.put("_owner_reason", args.getString("reason"))
                    return repository.enqueue("worklog", p, request)
                }
            }
            "set_personal_paid" -> {
                run {
                    if ((!"1".equals(repository.ownerValue("personal_paid_migrated", "")))) {
                        throw PixelMcpWriteTools.Rejected(
                            "DEVICE_MIGRATION_REQUIRED",
                            "App einmal öffnen, damit vorhandene persönliche Markierungen verlustfrei übernommen werden.",
                        )
                    }
                    var id: String = args.getString("work_record_id")
                    PixelMcpWriteTools.find(all.getJSONArray("worklogs"), id)
                    var ids: MutableSet<String> = LinkedHashSet()
                    var old: JSONArray = JSONArray(repository.ownerValue("personal_paid", "[]"))
                    run {
                        var i: Int = 0
                        while ((i < old.length())) {
                            ids.add(old.getString(i))
                            i++
                        }
                    }
                    if ((args.getBoolean("paid"))) {
                        val (collection, record) = AccountingRules.canonical(all, "worklogs", id)
                        SettlementSourceProtection.requireRouteChange(
                            all,
                            JSONObject(repository.ownerValue("settlement:v1", "{}")),
                            collection,
                            record.getString("id"),
                            AccountingRules.LABOR,
                        )
                        ids.add(id)
                    } else {
                        ids.remove(id)
                    }
                    AccountingRules.requireConsistentChange(all, all, AccountingRules.ids(old), ids)
                    repository.putOwnerValue("personal_paid", JSONArray(ids).toString())
                    return JSONObject()
                        .put("work_record_id", id)
                        .put("paid", args.getBoolean("paid"))
                        .put("scope", "device_only")
                }
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }

    private fun setAccounting(master: JSONObject, args: JSONObject, request: String): JSONObject {
        require(repository.ownerValue("personal_paid_migrated", "") == "1") {
            "App einmal öffnen, damit vorhandene Markierungen übernommen werden."
        }
        val personal =
            AccountingRules.ids(JSONArray(repository.ownerValue("personal_paid", "[]")))
                .toMutableSet()
        val (collection, record) =
            AccountingRules.canonical(
                master,
                args.getString("collection"),
                args.getString("entity_id"),
            )
        val id = record.getString("id")
        if (
            AccountingRules.hash(master, personal, collection, id) !=
                args.getString("expected_record_hash")
        ) {
            throw PixelMcpWriteTools.Rejected(
                "WRITE_CONFLICT",
                "Auftrag oder Markierungen wurden verändert. Abrechnung erneut öffnen.",
            )
        }
        val route = args.getString("route").let { if (it == "open") "" else it }
        require(route in setOf("", AccountingRules.BILLED, AccountingRules.LABOR)) {
            "Abrechnungsweg auswählen."
        }
        SettlementSourceProtection.requireRouteChange(
            master,
            JSONObject(repository.ownerValue("settlement:v1", "{}")),
            collection,
            id,
            route,
        )
        val works = AccountingRules.works(master, collection, id)
        // Clear opposite marks before applying the selected route. The owner request
        // transaction commits all records, marks and its replay receipt together.
        personal.removeAll(works.map { it.getString("id") }.toSet())
        repository.putOwnerValue("personal_paid", JSONArray(personal).toString())
        if (
            collection == "orders" &&
                AccountingRules.orderRoute(record).isNotEmpty() &&
                AccountingRules.orderRoute(record) != route
        ) {
            val clear = LocalJournalRules.clean(record).put("status", "Erledigt")
            ManualChangeRules.prepare("order", clear, record, java.time.Instant.now().toString())
            repository.enqueue("order", clear, request + "_clear")
        }
        for (work in works) {
            val changed =
                LocalJournalRules.clean(work)
                    .put("billed", if (route == AccountingRules.BILLED) 1 else 0)
            repository.replaceWorklog(changed)
            repository.enqueue(
                "worklog",
                changed,
                request + "_" + PixelMcpWriteTools.hash(work.getString("id")).take(16),
            )
        }
        if (route == AccountingRules.LABOR) personal.addAll(works.map { it.getString("id") })
        repository.putOwnerValue("personal_paid", JSONArray(personal).toString())
        if (collection == "orders") {
            val previous =
                PixelMcpWriteTools.find(repository.effective().getJSONArray("orders"), id)
            val changed =
                LocalJournalRules.clean(previous)
                    .put(
                        "status",
                        if (route.isNotEmpty()) AccountingRules.orderStatus(route)
                        else if (AccountingRules.orderRoute(record).isNotEmpty()) "Erledigt"
                        else record.optString("status"),
                    )
            ManualChangeRules.prepare(
                "order",
                changed,
                previous,
                java.time.Instant.now().toString(),
            )
            repository.enqueue("order", changed, request + "_order")
        }
        val effective = repository.effective()
        val stored = AccountingRules.canonical(effective, collection, id).second
        val savedWorks = AccountingRules.works(effective, collection, id)
        val savedPersonal =
            AccountingRules.ids(JSONArray(repository.ownerValue("personal_paid", "[]")))
        val complete =
            (collection != "orders" || AccountingRules.orderRoute(stored) == route) &&
                savedWorks.map { it.getString("id") }.toSet() ==
                    works.map { it.getString("id") }.toSet() &&
                savedWorks.all {
                    (it.optInt("billed") != 0) == (route == AccountingRules.BILLED) &&
                        (it.getString("id") in savedPersonal) == (route == AccountingRules.LABOR)
                }
        if (!complete)
            throw PixelMcpWriteTools.Rejected(
                "WRITE_CONFLICT",
                "Die Abrechnungszuordnung ist nicht vollständig wirksam. Keine Änderung gespeichert; Auftrag erneut öffnen.",
            )
        return JSONObject()
            .put("collection", collection)
            .put("entity_id", id)
            .put("route", route)
            .put("local_state", "local_saved")
    }
}
