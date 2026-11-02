package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Projects durable local changes without a network or alternate engine. */
object SnapshotProjection {
    @JvmStatic @Throws(Exception::class)
    fun effective(state: JSONObject): JSONObject {
        val master = JSONObject(state.getJSONObject("master").toString())
        LocalBusinessState.prepare(master)
        LocalJournalRules.apply(master, state.optJSONArray("pending") ?: JSONArray())
        return master
    }
}
