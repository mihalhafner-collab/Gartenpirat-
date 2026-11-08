package at.hausgarten.service

import java.util.*
import org.json.*

/** Bounded selection, one native transaction, ordinary compatible archive events. */
class WorklogArchiveBatch {
    private constructor() {}

    companion object {
        @JvmField val MAX: Int = 200

        @Throws(Exception::class)
        @JvmStatic
        fun preview(snapshot: JSONObject, ids: JSONArray): JSONObject {
            if ((((ids.length() < 1) || (ids.length() > MAX)))) {
                throw IllegalArgumentException("Bitte 1 bis 200 Arbeiten auswählen.")
            }
            val seen: MutableSet<String> = HashSet()
            val entries: JSONArray = JSONArray()
            val guards: JSONArray = JSONArray()
            var allowed: Boolean = true
            run {
                var i: Int = 0
                while ((i < ids.length())) {
                    var id: String = ids.getString(i)
                    if (((id.isEmpty() || !seen.add(id)))) {
                        throw IllegalArgumentException(
                            "Auswahl enthält eine leere oder doppelte ID."
                        )
                    }
                    var p: JSONObject = DeletionRules.preview(snapshot, "worklog", id)
                    var row: JSONObject =
                        (OwnerJournalRules.findOrNull(snapshot.getJSONArray("worklogs"), id))!!
                    entries.put(
                        JSONObject(p.toString())
                            .put("activity", row.optString("activity"))
                            .put("work_date", row.optString("work_date"))
                    )
                    guards.put(
                        JSONObject()
                            .put("id", id)
                            .put("record", p.getString("expected_record_hash"))
                            .put("photos", p.getString("expected_photo_links_hash"))
                            .put("allowed", p.getBoolean("allowed"))
                    )
                    allowed = allowed and p.getBoolean("allowed")
                    i++
                }
            }
            return JSONObject()
                .put("count", ids.length())
                .put("allowed", allowed)
                .put("entries", entries)
                .put("preview_hash", PixelMcpWriteTools.hash(guards))
                .put("recoverable", true)
                .put("physical_delete", false)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun actions(
            snapshot: JSONObject,
            ids: JSONArray,
            expected: String,
            request: String,
            stamp: String,
        ): JSONArray {
            val preview: JSONObject = preview(snapshot, ids)
            if ((!preview.getString("preview_hash").equals(expected))) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Ausgewählte Arbeiten oder Fotozuordnungen wurden geändert. Nichts entfernt; Auswahl erneut prüfen.",
                )
            }
            if ((!preview.getBoolean("allowed"))) {
                throw IllegalArgumentException(
                    "Mindestens eine Arbeit ist geschützt. Nichts entfernt; Auswahl prüfen."
                )
            }
            val copy: JSONObject = JSONObject(snapshot.toString())
            var out: JSONArray = JSONArray()
            run {
                var i: Int = 0
                while ((i < ids.length())) {
                    var id: String = ids.getString(i)
                    var p: JSONObject =
                        DeletionRules.payload(
                            copy,
                            "worklog",
                            id,
                            preview
                                .getJSONArray("entries")
                                .getJSONObject(i)
                                .getString("expected_record_hash"),
                            stamp,
                        )
                    var event: JSONObject =
                        JSONObject()
                            .put("client_id", (("" + request + "_") + i))
                            .put("kind", "archive")
                            .put("payload", p)
                            .put("created_at", stamp)
                    OwnerJournalRules.apply(copy, event, HashMap(), JSONObject())
                    out.put(event)
                    i++
                }
            }
            return out
        }
    }
}
