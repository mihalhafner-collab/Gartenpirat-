package at.hausgarten.service

import org.json.*

/** Saved journal rows are not equivalent to pending network transfers. Read only. */
class SyncQueueCounters {
    private constructor() {
    }
    companion object {
        @Throws(Exception::class)
        @JvmStatic fun describe(actions: JSONArray?, queue: JSONObject): JSONObject {
            var local: Int = 0
            run {
                var i: Int = 0
                while (((actions != null) && (i < actions!!.length()))) {
                    var p: JSONObject? = actions!!.getJSONObject(i).optJSONObject("payload")
                    if ((((p != null) && ((p!!.optBoolean("_local_only") || p!!.optBoolean("test_record")))))) {
                        local++
                    }
                    i++
                }
            }
            return JSONObject().put("saved_actions", (if ((actions == null)) 0 else actions!!.length())).put("local_only_actions", local).put("pending_upload", queue.optInt("pending_upload")).put("review", queue.optInt("review")).put("transfer_items", queue.optInt("total"))
        }
    }
}
