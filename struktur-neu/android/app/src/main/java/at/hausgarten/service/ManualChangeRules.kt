package at.hausgarten.service

import org.json.*
import java.util.*

/** The editor's baseline belongs to the opened form, never to save time. */
class ManualChangeRules {
    companion object {
        @Throws(Exception::class)
        @JvmStatic fun editor(incoming: JSONObject, current: JSONObject?, kind: String): JSONObject {
            if ((!incoming.has("_edit_base"))) {
                return incoming
            }
            var base: JSONObject = incoming.getJSONObject("_edit_base")
            if ((!incoming.optString("id").equals(base.optString("id")))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Die Formularzuordnung wurde verändert.")
            }
            LocalJournalRules.compare(LocalJournalRules.guard(base, kind), current)
            var result: JSONObject = LocalJournalRules.clean(current ?: throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Der ursprüngliche Eintrag ist nicht mehr vorhanden."))
            var changes: JSONObject = incoming.getJSONObject("_edit_changes")
            var allowed: MutableSet<String> = HashSet(Arrays.asList(*(if (kind.equals("customer")) PixelMcpEntityWriteTools.CUSTOMER_FIELDS else (if (kind.equals("order")) PixelMcpEntityWriteTools.ORDER_FIELDS else PixelMcpWriteTools.BASE_FIELDS))))
            allowed.remove("id")
            allowed.remove("updated_at")
            run {
                var keys: MutableIterator<String> = changes.keys()
                while (keys.hasNext()) {
                    var key: String = keys.next()
                    if ((!allowed.contains(key))) {
                        throw IllegalArgumentException("Ungültiges Formularfeld.")
                    }
                    result.put(key, changes.get(key))
                }
            }
            return result
        }
        @Throws(Exception::class)
        @JvmStatic fun prepare(kind: String, payload: JSONObject, previous: JSONObject?, stamp: String) {
            for (key in arrayOf("_mcp_base", "_sync_base", "_sync_resolves", "_sync_predecessors", "_sync_field_merge", "_edit_base", "_edit_changes", "_new_record")) {
                payload.remove(key)
            }
            payload.put("_mcp_create", (previous == null))
            if (((previous != null))) {
                payload.put("_sync_base", LocalJournalRules.guard(previous, kind))
            }
            if ((((previous == null) && kind.equals("customer")))) {
                if ((!payload.has("active"))) {
                    payload.put("active", 1)
                }
                if ((!payload.has("custom_fields"))) {
                    payload.put("custom_fields", JSONObject())
                }
            }
            if (((((previous == null) && ((kind.equals("order") || kind.equals("photo")))) && !payload.has("customer_visible")))) {
                payload.put("customer_visible", 1)
            }
            if (((((previous == null) && kind.equals("order")) && payload.optString("status").isEmpty()))) {
                payload.put("status", "Geplant")
            }
            payload.put("updated_at", stamp)
            if ((!payload.has("created_at"))) {
                payload.put("created_at", (if (((previous != null) && previous!!.has("created_at"))) previous!!.get("created_at") else stamp))
            }
        }
    }
}
