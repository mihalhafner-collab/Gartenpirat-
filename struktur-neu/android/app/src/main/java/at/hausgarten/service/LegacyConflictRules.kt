package at.hausgarten.service

import org.json.*
import java.util.*

/** Read-only, hash-bound selection. Never applies a business proposal. */
class LegacyConflictRules {
    companion object {
        @Throws(Exception::class)
        @JvmStatic fun preview(state: JSONObject): JSONObject {
            var conflicts: JSONArray = PixelMcpOwnerTools.conflicts(state, JSONObject().put("limit", 100)).getJSONArray("conflicts")
            var entries: JSONArray = JSONArray()
            run {
                var i: Int = 0
                while ((i < conflicts.length())) {
                    var c: JSONObject = conflicts.getJSONObject(i)
                    var a: JSONObject? = c.optJSONObject("action")
                    if ((((a == null) || !c.optBoolean("resolution_available")))) {
                        i++
                        continue
                    }
                    var code: String = c.optString("code")
                    var kind: String = a!!.optString("kind")
                    var stamp: String = a!!.optString("created_at")
                    if (((stamp.isEmpty() || (stamp.substring(0, Math.min(10, stamp.length)).compareTo("2026-10-18") >= 0)))) {
                        i++
                        continue
                    }
                    if ((!(((kind.equals("delete") && code.equals("DELETE_REVIEW_REQUIRED")) || (kind.equals("customer") && code.equals("WRITE_CONFLICT")))))) {
                        i++
                        continue
                    }
                    entries.put(JSONObject().put("client_id", a!!.getString("client_id")).put("expected_action_hash", c.getString("action_hash")).put("expected_current_hash", c.getString("current_hash")).put("resolution", "keep_current").put("reason", "historisch_nicht_mehr_pruefbar"))
                    i++
                }
            }
            var sorted: MutableList<JSONObject> = ArrayList()
            run {
                var i: Int = 0
                while ((i < entries.length())) {
                    sorted.add(entries.getJSONObject(i))
                    i++
                }
            }
            sorted.sortWith(Comparator.comparing(lambda0@ { x -> x.optString("client_id") }))
            entries = JSONArray(sorted)
            return JSONObject().put("count", entries.length()).put("entries", entries).put("preview_hash", PixelMcpWriteTools.hash(entries)).put("resolution", "keep_current").put("effect", "no_business_data_changed").put("message", ("" + entries.length() + " historische Änderungen können nicht mehr verifiziert werden. Der aktuelle Datenbestand bleibt unverändert. Keine Löschung wird ausgeführt."))
        }
        @Throws(Exception::class)
        @JvmStatic fun require(state: JSONObject, expected: String) {
            var p: JSONObject = preview(state)
            if ((((p.getInt("count") == 0) || !p.getString("preview_hash").equals(expected)))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Die Vorschau hat sich verändert. Nichts abgeschlossen; erneut prüfen.")
            }
        }
    }
}
