package at.hausgarten.service

import org.json.*
import java.util.*

/** One explicit trash selection, one guarded journal action. Never deletes active dependencies. */
class TrashPurgeBatch {
    private constructor() {
    }
    companion object {
        @JvmField val KIND: String = "purge_trash_v1"
        private val GROUPS: Array<String> = arrayOf("customers", "orders", "worklogs", "photos", "invoices", "offers", "service_catalog", "appointments")
        @Throws(Exception::class)
        @JvmStatic fun preview(snapshot: JSONObject): JSONObject {
            var archive: JSONObject = OwnerJournalRules.archives(snapshot)
            var basis: JSONObject = JSONObject()
            var candidates: TreeSet<String> = TreeSet()
            var blocked: TreeMap<String, String> = TreeMap()
            var archiveGuards: JSONArray = JSONArray()
            var archiveKeys: TreeSet<String> = TreeSet()
            archive.keys().forEachRemaining(archiveKeys::add)
            for (key in archiveKeys) {
                var entry: JSONObject = archive.getJSONObject(key)
                if ((entry.optBoolean("purged"))) {
                    continue
                }
                if ((((key.indexOf(':') < 1) || !entry.has("record")))) {
                    throw IllegalArgumentException("Ungültiger Papierkorbeintrag. Nichts entfernt.")
                }
                candidates.add(key)
                archiveGuards.put(JSONObject().put("key", key).put("hash", OwnerJournalRules.version(entry)))
            }
            basis.put("archive", archiveGuards)
            for (group in GROUPS) {
                var guards: TreeMap<String, String> = TreeMap()
                var rows: JSONArray? = snapshot.optJSONArray(group)
                if ((((rows == null) && group.equals("appointments")))) {
                    rows = JSONArray()
                }
                if (((rows == null))) {
                    throw IllegalArgumentException("Unvollständiger Geschäftsstand.")
                }
                run {
                    var i: Int = 0
                    while ((i < rows!!.length())) {
                        var row: JSONObject = rows!!.getJSONObject(i)
                        guards.put(row.getString("id"), OwnerJournalRules.version(row))
                        i++
                    }
                }
                basis.put(group, JSONObject(guards))
            }
            var changed: Boolean
            do {
                changed = false
                var view: JSONObject = referenceView(snapshot, candidates)
                for (key in ArrayList(candidates)) {
                    try {
                        validate(snapshot, view, key)
                    } catch (refused: IllegalArgumentException) {
                        candidates.remove(key)
                        blocked.put(key, (refused.message)!!)
                        changed = true
                    }
                }
            } while ((changed))
            var entries: JSONArray = JSONArray()
            var remaining: JSONArray = JSONArray()
            for (key in candidates) {
                var split: Int = key.indexOf(':')
                entries.put(JSONObject().put("entity", key.substring(0, split)).put("entity_id", key.substring((split + 1))).put("expected_record_hash", OwnerJournalRules.version(archive.getJSONObject(key))))
            }
            for (e in blocked.entries) {
                remaining.put(JSONObject().put("archive_key", e.key).put("message", e.value))
            }
            return JSONObject().put("total", archiveGuards.length()).put("count", entries.length()).put("allowed", (entries.length() > 0)).put("entries", entries).put("blocked", remaining).put("preview_hash", PixelMcpWriteTools.hash(basis)).put("recoverable", false).put("active_data_deleted", false)
        }
        @Throws(Exception::class)
        private fun referenceView(source: JSONObject, selected: MutableSet<String>): JSONObject {
            var view: JSONObject = JSONObject(source.toString())
            var archive: JSONObject = OwnerJournalRules.archives(view)
            for (key in selected) {
                archive.remove(key)
                var split: Int = key.indexOf(':')
                var type: String = key.substring(0, split)
                if (((type.equals("customer") || type.equals("service_template")))) {
                    OwnerJournalRules.replace(view, OwnerJournalRules.group(type), key.substring((split + 1)), null)
                }
            }
            return view
        }
        @Throws(Exception::class)
        private fun validate(original: JSONObject, view: JSONObject, key: String) {
            var split: Int = key.indexOf(':')
            var type: String = key.substring(0, split)
            var id: String = key.substring((split + 1))
            if ((!Arrays.asList(*DeletionRules.ENTITIES).contains(type))) {
                throw IllegalArgumentException("Geschützter oder unbekannter Papierkorbbereich.")
            }
            var saved: JSONObject = OwnerJournalRules.archives(original).getJSONObject(key)
            var record: JSONObject = saved.getJSONObject("record")
            if ((!id.equals(record.getString("id")))) {
                throw IllegalArgumentException("Papierkorb-ID stimmt nicht mit dem Original überein.")
            }
            var active: JSONObject? = OwnerJournalRules.findOrNull(original.getJSONArray(OwnerJournalRules.group(type)), id)
            if (((type.equals("customer") || type.equals("service_template")))) {
                OwnerJournalRules.checkHash((active)!!, saved.getString("archived_record_hash"))
                if ((((active == null) || (active!!.optBoolean("active", true) && (active!!.optInt("active", 1) != 0))))) {
                    throw IllegalArgumentException("Eintrag ist wieder aktiv. Nichts gelöscht.")
                }
            } else {
                if (((active != null))) {
                    throw IllegalArgumentException("Eintrag ist wieder aktiv. Nichts gelöscht.")
                }
            }
            OwnerJournalRules.validateArchive(type, record, view)
            TrashRules.detachOptionalParent(view, type, id, java.time.Instant.now().toString())
            TrashRules.requireUnreferenced(view, type, id)
        }
        @Throws(Exception::class)
        @JvmStatic fun payload(snapshot: JSONObject, expected: String, confirmed: Boolean): JSONObject {
            if ((!confirmed)) {
                throw IllegalArgumentException("Papierkorb leeren ausdrücklich bestätigen.")
            }
            var preview: JSONObject = preview(snapshot)
            if ((!preview.getString("preview_hash").equals(expected))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Papierkorb oder Verknüpfungen wurden geändert. Nichts gelöscht; Vorschau neu öffnen.")
            }
            if ((!preview.getBoolean("allowed"))) {
                throw IllegalArgumentException("Keine freigegebenen Papierkorbeinträge vorhanden.")
            }
            return JSONObject().put("confirmed", true).put("expected_preview_hash", expected).put("entries", preview.getJSONArray("entries"))
        }
        @Throws(Exception::class)
        @JvmStatic fun apply(snapshot: JSONObject, action: JSONObject) {
            var supplied: JSONObject = action.getJSONObject("payload")
            val expected = supplied.getString("expected_preview_hash")
            var checked: JSONObject = payload(snapshot, expected, supplied.optBoolean("confirmed"))
            if ((!PixelMcpWriteTools.hash(checked.getJSONArray("entries")).equals(PixelMcpWriteTools.hash(supplied.getJSONArray("entries"))))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Die bestätigte Löschmenge stimmt nicht mehr überein.")
            }
            var entries: JSONArray = checked.getJSONArray("entries")
            var archive: JSONObject = OwnerJournalRules.archives(snapshot)
            run {
                var i: Int = 0
                while ((i < entries.length())) {
                    var e: JSONObject = entries.getJSONObject(i)
                    var type: String = e.getString("entity")
                    var id: String = e.getString("entity_id")
                    TrashRules.commitPurge(snapshot, action, type, id, archive.getJSONObject((("" + type + ":") + id)))
                    i++
                }
            }
        }
    }
}
