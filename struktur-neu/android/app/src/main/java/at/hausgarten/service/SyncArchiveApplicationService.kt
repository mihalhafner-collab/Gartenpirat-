package at.hausgarten.service

import org.json.*
import java.util.*

/** SyncArchive use cases. Effects pass through the existing owner repository transaction. */
class SyncArchiveApplicationService : OwnerCommand {
    private val repository: OwnerRepository
    constructor(repository: OwnerRepository) {
        this.repository = repository
    }
    @Throws(Exception::class)
    override fun perform(name: String, a: JSONObject, request: String): JSONObject {
        var all: JSONObject = repository.effective()
        var stamp: String = java.time.Instant.now().toString()
        when ((name)) {
            "empty_trash" -> {
                run {
                    val payload = TrashPurgeBatch.payload(all, TrashPurgeBatch.preview(all).getString("preview_hash"), a.getBoolean("confirmed"))
                    val targets = payload.getJSONArray("entries")
                    for (i in 0 until targets.length()) {
                        val target = targets.getJSONObject(i)
                        requireInactiveSession(target.getString("entity"), target.getString("entity_id"))
                    }
                    return repository.enqueue(TrashPurgeBatch.KIND, payload, request)
                        .put("deleted", targets.length()).put("recoverable", false).put("media_cleanup", "local_after_commit")
                }
            }
            "archive_work_records" -> {
                run {
                    var events: JSONArray = WorklogArchiveBatch.actions(all, a.getJSONArray("work_record_ids"), a.getString("expected_preview_hash"), request, stamp)
                    var saved: JSONArray = JSONArray()
                    run {
                        var i: Int = 0
                        while ((i < events.length())) {
                            var event: JSONObject = events.getJSONObject(i)
                            saved.put(repository.enqueue("archive", event.getJSONObject("payload"), event.getString("client_id")))
                            i++
                        }
                    }
                    return JSONObject().put("count", saved.length()).put("actions", saved).put("recoverable", true).put("physical_delete", false).put("local_state", "local_saved").put("sync_state", "sync_pending")
                }
            }
            "purge_entity" -> {
                run {
                    requireInactiveSession(a.getString("entity"), a.getString("entity_id"))
                    var kind: String = "purge_v1"
                    var p: JSONObject = JSONObject().put("id", a.getString("entity_id")).put("expected_record_hash", OwnerJournalRules.version(OwnerJournalRules.archives(all).getJSONObject(a.getString("entity") + ":" + a.getString("entity_id")))).put("updated_at", stamp)
                    if ((name.equals("purge_entity"))) {
                        p.put("entity", a.getString("entity")).put("confirmed", a.getBoolean("confirmed"))
                    }
                    OwnerJournalRules.apply(JSONObject(all.toString()), JSONObject().put("kind", kind).put("payload", p).put("client_id", request).put("created_at", stamp), HashMap(), JSONObject())
                    return repository.enqueue(kind, p, request).put("recoverable", false).put("media_cleanup", "local_after_commit")
                }
            }
            "delete_entity", "archive_entity" -> {
                return archiveOne(all, a, request, stamp)
            }
            "restore_entity" -> {
                run {
                    var entity: String = a.getString("entity")
                    var id: String = a.getString("entity_id")
                    var p: JSONObject = JSONObject(a.toString()).put("id", id).put("updated_at", stamp)
                    p.remove("request_id")
                    p.remove("entity_id")
                    p.put("_archive_entry", OwnerJournalRules.archives(all).getJSONObject((("" + entity + ":") + id)))
                    OwnerJournalRules.apply(JSONObject(all.toString()), JSONObject().put("client_id", request).put("kind", "restore").put("payload", p).put("created_at", stamp), HashMap(), JSONObject())
                    return repository.enqueue("restore", p, request)
                }
            }
            "create_backup" -> {
                return repository.createBackup(request)
            }
            "restore_backup" -> {
                return repository.restoreBackup(a, request)
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }
    @Throws(Exception::class)
    private fun requireInactiveSession(entity: String, id: String) {
        ActiveWorkProtection.require(repository.state(), entity, id)
    }
    @Throws(Exception::class)
    private fun archiveOne(all: JSONObject, args: JSONObject, request: String, stamp: String): JSONObject {
        requireInactiveSession(args.getString("entity"), args.getString("entity_id"))
        if ((args.getString("entity").equals("appointment"))) {
            var session: JSONObject = repository.state().getJSONObject("work_session")
            if (((args.getString("entity_id").equals(session.optString("appointment_id")) && !Arrays.asList("IDLE", "FINALIZED", "CANCELLED").contains(session.optString("state"))))) {
                throw IllegalArgumentException("Zeiterfassung dieses Termins zuerst abschließen oder bewusst verwerfen. Nichts archiviert.")
            }
        }
        val entity = args.getString("entity")
        val id = args.getString("entity_id")
        val current = OwnerJournalRules.findOrNull(all.getJSONArray(OwnerJournalRules.group(entity)), id)
            ?: throw IllegalArgumentException("Der Eintrag fehlt oder liegt bereits im Papierkorb.")
        val p = DeletionRules.payload(all, entity, id, OwnerJournalRules.version(current), stamp)
        OwnerJournalRules.apply(JSONObject(all.toString()), JSONObject().put("kind", "archive").put("client_id", request).put("payload", p).put("created_at", stamp), HashMap(), JSONObject())
        return repository.enqueue("archive", p, request).put("recoverable", true).put("physical_delete", false)
    }
}
