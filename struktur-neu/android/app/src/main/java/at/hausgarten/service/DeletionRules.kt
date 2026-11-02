package at.hausgarten.service

import org.json.*

/** User-requested deletion means recoverable removal in the existing owner archive. */
class DeletionRules {
    private constructor() {
    }
    companion object {
        @JvmField val ENTITIES: Array<String> = arrayOf("customer", "order", "worklog", "photo", "invoice", "offer", "service_template", "appointment")
        @Throws(Exception::class)
        @JvmStatic fun preview(snapshot: JSONObject, entity: String, id: String): JSONObject {
            if ((!java.util.Arrays.asList(*ENTITIES).contains(entity))) {
                throw IllegalArgumentException("Dieser Bereich kann nicht gelöscht werden.")
            }
            var current: JSONObject? = OwnerJournalRules.findOrNull(snapshot.optJSONArray(OwnerJournalRules.group(entity)), id)
            if (((current == null))) {
                throw IllegalArgumentException("Der Eintrag fehlt oder liegt bereits im Papierkorb.")
            }
            var related: JSONArray = TrashRules.related(snapshot, entity, id)
            var out: JSONObject = JSONObject().put("entity", entity).put("entity_id", id).put("expected_record_hash", OwnerJournalRules.version(current)).put("recoverable", true).put("physical_delete", false).put("cascade", false).put("updated_at", current!!.optString("updated_at")).put("related_photos", related.length()).put("expected_photo_links_hash", PixelMcpWriteTools.hash(related)).put("mode", (if ((entity.equals("customer") || entity.equals("service_template"))) "deactivate" else "trash"))
            var archived: JSONObject? = snapshot.optJSONObject("owner_archive")
            if ((((archived != null) && archived!!.has((("" + entity + ":") + id))))) {
                return out.put("allowed", false).put("message", "Der Eintrag liegt bereits im Papierkorb.")
            }
            try {
                OwnerJournalRules.validateArchive(entity, current, snapshot)
                out.put("allowed", true).put("message", "Aus aktiven Ansichten entfernen. Original bleibt im Papierkorb. Fotos bleiben erhalten; betroffene Fotozuordnungen werden gelöst und beim Wiederherstellen nur bei unverändertem Foto wieder verbunden.")
            } catch (blocked: IllegalArgumentException) {
                out.put("allowed", false).put("message", blocked.message)
            }
            return out
        }
        @Throws(Exception::class)
        @JvmStatic fun payload(snapshot: JSONObject, entity: String, id: String, expected: String, stamp: String): JSONObject {
            var current: JSONObject? = OwnerJournalRules.findOrNull(snapshot.getJSONArray(OwnerJournalRules.group(entity)), id)
            OwnerJournalRules.checkHash(current, expected)
            if (((current == null))) {
                throw IllegalArgumentException("Der Eintrag fehlt.")
            }
            OwnerJournalRules.validateArchive(entity, current, snapshot)
            return JSONObject().put("entity", entity).put("id", id).put("expected_record_hash", expected).put("_record_base", LocalJournalRules.guard(current, entity)).put("_archive_record", current).put("photo_link_ids", TrashRules.related(snapshot, entity, id)).put("expected_photo_links_hash", PixelMcpWriteTools.hash(TrashRules.related(snapshot, entity, id))).put("deletion_policy", "recoverable_v2").put("updated_at", stamp)
        }
    }
}
