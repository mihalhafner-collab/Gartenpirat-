package at.hausgarten.service

import java.util.*
import org.json.*

/** Stable dependency order, independent of device clocks. Original action bytes stay unchanged. */
class LocalActionOrder {
    companion object {
        /** Explicit removal is based on the currently read version, not an unapplied older edit. */
        @JvmStatic
        fun predecessors(
            kind: String,
            payload: JSONObject,
            prior: JSONArray,
            current: JSONObject?,
        ): JSONArray {
            val selected = linkedSetOf<String>()
            val explicit = payload.optJSONArray("_sync_predecessors") ?: JSONArray()
            for (i in 0 until explicit.length()) selected.add(explicit.getString(i))
            val removal = kind in setOf("archive", "delete", "purge_v1")
            val incorporated =
                if (removal) {
                    requireNotNull(current) { "Aktueller Löschstand fehlt." }
                    LocalJournalRules.ids(current.optJSONArray("drive_applied_ids")).apply {
                        addAll(LocalJournalRules.ids(current.optJSONArray("accepted_ids")))
                        current.optJSONObject("drive_resolved_actions")?.keys()?.forEach { add(it) }
                    }
                } else null
            val links = payload.optJSONArray("photo_link_ids") ?: JSONArray()
            for (i in 0 until prior.length()) {
                val event = prior.getJSONObject(i)
                val client = event.getString("client_id")
                if (incorporated != null && client !in incorporated) continue
                val previous = event.getJSONObject("payload").optString("id")
                val same =
                    payload.optString("id").isNotEmpty() && payload.optString("id") == previous
                val linked =
                    (0 until links.length()).any {
                        links.getJSONObject(it).getString("id") == previous
                    }
                if (
                    same || linked || kind == "business_settings" && event.optString("kind") == kind
                )
                    selected.add(client)
            }
            return JSONArray(selected.toList())
        }

        @Throws(Exception::class)
        @JvmStatic
        fun order(input: JSONArray): JSONArray {
            var actions: LinkedHashMap<String, JSONObject> = LinkedHashMap()
            var creators: MutableMap<String, String> = HashMap()
            var versions: MutableMap<String, String> = HashMap()
            run {
                var i: Int = 0
                while ((i < input.length())) {
                    var a: JSONObject = input.getJSONObject(i)
                    var p: JSONObject = a.getJSONObject("payload")
                    var id: String = a.getString("client_id")
                    var entity: String = (("" + a.getString("kind") + ":") + p.optString("id"))
                    actions.put(id, a)
                    if (((p.optBoolean("_mcp_create") && !p.optString("id").isEmpty()))) {
                        creators.putIfAbsent(entity, id)
                    }
                    if ((!p.optString("updated_at").isEmpty())) {
                        versions.put((("" + entity + ":") + p.getString("updated_at")), id)
                    }
                    i++
                }
            }
            run {
                var i: Int = 0
                while ((i < input.length())) {
                    var a: JSONObject = input.getJSONObject(i)
                    var p: JSONObject = a.getJSONObject("payload")
                    if (("offer_convert".equals(a.optString("kind")))) {
                        var order: String = p.optString("existing_order_id")
                        if ((order.isEmpty())) {
                            order =
                                ("ord_offer_" +
                                    PixelMcpWriteTools.hash(a.getString("client_id"))
                                        .substring(0, 32))
                            creators.putIfAbsent(("order:" + order), a.getString("client_id"))
                        }
                        versions.put(
                            (("" + ("order:" + order) + ":") + a.optString("created_at")),
                            a.getString("client_id"),
                        )
                    }
                    i++
                }
            }
            var dependencies: MutableMap<String, MutableSet<String>> = LinkedHashMap()
            for (entry in actions.entries) {
                var id: String = entry.key
                var a: JSONObject = entry.value
                var p: JSONObject = a.getJSONObject("payload")
                var deps: MutableSet<String> = LinkedHashSet()
                var ownKind: String =
                    (if (a.getString("kind").startsWith("offer_")) "offer" else a.getString("kind"))
                var creator: String? = creators.get((("" + ownKind + ":") + p.optString("id")))
                if ((((creator != null) && !creator!!.equals(id)))) {
                    deps.add(creator)
                }
                var explicit: JSONArray? = p.optJSONArray("_sync_predecessors")
                run {
                    var j: Int = 0
                    while (((explicit != null) && (j < explicit!!.length()))) {
                        if ((actions.containsKey(explicit!!.getString(j)))) {
                            deps.add(explicit!!.getString(j))
                        }
                        j++
                    }
                }
                for (ref in
                    arrayOf(
                        arrayOf("customer_id", "customer"),
                        arrayOf("billing_customer_id", "customer"),
                        arrayOf("order_id", "order"),
                        arrayOf("worklog_id", "worklog"),
                        arrayOf("appointment_id", "appointment"),
                    )) {
                    var parent: String? = creators.get((("" + ref[1] + ":") + p.optString(ref[0])))
                    if ((((parent != null) && !parent!!.equals(id)))) {
                        deps.add(parent)
                    }
                }
                var expectedOrder: JSONObject? = p.optJSONObject("_expected_order")
                if (((expectedOrder != null))) {
                    var parent: String? =
                        versions.get(
                            (("" + ("order:" + expectedOrder!!.optString("id")) + ":") +
                                expectedOrder!!.optString("updated_at"))
                        )
                    if ((((parent != null) && !parent!!.equals(id)))) {
                        deps.add(parent)
                    }
                }
                var base: JSONObject? = p.optJSONObject("_sync_base")
                if (((base == null))) {
                    base = p.optJSONObject("_mcp_base")
                }
                if (((base != null))) {
                    var prior: String? =
                        versions.get(
                            (("" + (("" + a.getString("kind") + ":") + p.optString("id")) + ":") +
                                base!!.optString("updated_at"))
                        )
                    if ((((prior != null) && !prior!!.equals(id)))) {
                        deps.add(prior)
                    }
                }
                if ((deps.contains(id))) {
                    throw PixelMcpWriteTools.Rejected(
                        "DEPENDENCY_CYCLE",
                        "Änderungen enthalten eine zyklische Vorgängerkette. Originale bleiben erhalten.",
                    )
                }
                dependencies.put(id, deps)
            }
            var result: JSONArray = JSONArray()
            var done: MutableSet<String> = HashSet()
            while (((done.size < actions.size))) {
                var moved: Boolean = false
                for (id in actions.keys) {
                    if (((!done.contains(id) && done.containsAll((dependencies.get(id))!!)))) {
                        done.add(id)
                        result.put(actions.get(id))
                        moved = true
                    }
                }
                if ((!moved)) {
                    throw PixelMcpWriteTools.Rejected(
                        "DEPENDENCY_CYCLE",
                        "Änderungen enthalten eine zyklische Vorgängerkette. Originale bleiben erhalten.",
                    )
                }
            }
            return result
        }
    }
}
