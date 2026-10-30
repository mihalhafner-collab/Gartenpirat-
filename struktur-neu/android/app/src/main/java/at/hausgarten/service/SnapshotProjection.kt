package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Existing validated pending-action projection; no persistence or transport. */
object SnapshotProjection {
    @JvmStatic @Throws(Exception::class)
    fun effective(state: JSONObject): JSONObject {
        val master = JSONObject(state.getJSONObject("master").toString())
        for (key in arrayOf("offers", "service_catalog", "appointments")) if (!master.has(key)) master.put(key, JSONArray())
        val pending = state.optJSONArray("pending") ?: JSONArray()
        if (PixelDriveState.ENGINE == master.optString("processing_engine") || LocalAuthorityRules.ENGINE == master.optString("processing_engine")) {
            val projected = JSONObject(master.toString())
            DriveJournalRules.apply(projected, pending)
            for (key in arrayOf("customers", "orders", "worklogs", "invoices", "photos", "offers", "service_catalog", "appointments", "business_settings", "invoice_issuer", "owner_archive")) if (projected.has(key)) master.put(key, projected.get(key))
            return master
        }
        val blocked = HashSet<String>()
        for (key in arrayOf("sync_errors", "drive_journal_errors")) {
            val errors = master.optJSONArray(key)
            for (i in 0 until (errors?.length() ?: 0)) blocked.add(errors!!.getJSONObject(i).optString("client_id"))
        }
        for (pair in arrayOf("customers:customer", "orders:order", "worklogs:worklog", "invoices:invoice", "photos:photo", "offers:offer", "service_catalog:service_template", "appointments:appointment")) {
            val parts = pair.split(':')
            val rows = LinkedHashMap<String, JSONObject>()
            val base = master.optJSONArray(parts[0])
            for (i in 0 until (base?.length() ?: 0)) {
                val row = base!!.getJSONObject(i)
                val id = row.getString("id")
                if (rows.containsKey(id)) throw IllegalArgumentException("Doppelte Datensatz-ID im Datenstand.")
                rows[id] = JSONObject(row.toString())
            }
            for (i in 0 until pending.length()) {
                val action = pending.getJSONObject(i)
                if (parts[1] != action.optString("kind") || blocked.contains(action.optString("client_id"))) continue
                val payload = action.getJSONObject("payload")
                val id = payload.getString("id")
                val row = rows[id] ?: JSONObject()
                for (key in payload.keys()) row.put(key, payload.get(key))
                rows[id] = row
            }
            master.put(parts[0], JSONArray(rows.values))
        }
        val all = HashMap<String, JSONObject>()
        for (i in 0 until pending.length()) all[pending.getJSONObject(i).optString("client_id")] = pending.getJSONObject(i)
        val resolved = master.optJSONObject("drive_resolved_actions") ?: JSONObject().also { master.put("drive_resolved_actions", it) }
        val seen = DriveJournalRules.ids(master.optJSONArray("drive_applied_ids"))
        for (i in 0 until pending.length()) {
            val action = pending.getJSONObject(i)
            if (OwnerJournalRules.handles(action.optString("kind")) && !blocked.contains(action.optString("client_id")) && !seen.contains(action.optString("client_id"))) {
                try { OwnerJournalRules.apply(master, action, all, resolved) } catch (_: Exception) { /* Keep reviewable; preview is never a receipt. */ }
            }
        }
        return master
    }
}
