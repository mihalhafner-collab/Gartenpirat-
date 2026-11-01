package at.hausgarten.service

import org.json.*
import java.util.*

/** Work use cases. Effects pass through the existing owner repository transaction. */
class WorkApplicationService : OwnerCommand {
    private val repository: OwnerRepository
    constructor(repository: OwnerRepository) {
        this.repository = repository
    }
    @Throws(Exception::class)
    override fun perform(name: String, a: JSONObject, request: String): JSONObject {
        var all: JSONObject = repository.effective()
        var stamp: String = java.time.Instant.now().toString()
        when ((name)) {
            "save_reviewed_order" -> {
                run {
                    var row: JSONObject = OrderReviewRules.prepare(repository.state(), a)
                    return repository.enqueue("order", row, request).put("order_id", row.getString("id"))
                }
            }
            "set_work_record_billed", "reassign_work_record" -> {
                run {
                    var old: JSONObject = PixelMcpWriteTools.find(all.getJSONArray("worklogs"), a.getString("work_record_id"))
                    if ((!PixelMcpWriteTools.hash(PixelMcpWriteTools.base(old)).equals(a.getString("expected_record_hash")))) {
                        throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Arbeitsaufzeichnung wurde verändert.")
                    }
                    var p: JSONObject = DriveJournalRules.clean(old)
                    if ((name.equals("set_work_record_billed"))) {
                        p.put("billed", (if (a.getBoolean("billed")) 1 else 0))
                    } else {
                        p.put("customer_id", a.getString("customer_id")).put("billing_customer_id", a.getString("billing_customer_id")).put("order_id", a.optString("order_id"))
                    }
                    repository.replaceWorklog(p)
                    p.put("_owner_reason", a.getString("reason"))
                    return repository.enqueue("worklog", p, request)
                }
            }
            "set_personal_paid" -> {
                run {
                    if ((!"1".equals(repository.ownerValue("personal_paid_migrated", "")))) {
                        throw PixelMcpWriteTools.Rejected("DEVICE_MIGRATION_REQUIRED", "App einmal öffnen, damit vorhandene persönliche Markierungen verlustfrei übernommen werden.")
                    }
                    var id: String = a.getString("work_record_id")
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
                    if ((a.getBoolean("paid"))) {
                        ids.add(id)
                    } else {
                        ids.remove(id)
                    }
                    repository.putOwnerValue("personal_paid", JSONArray(ids).toString())
                    return JSONObject().put("work_record_id", id).put("paid", a.getBoolean("paid")).put("scope", "device_only")
                }
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }
}
