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
            "close_legacy_conflicts" -> {
                run {
                    var expected: String = a.getString("expected_preview_hash")
                    repository.verifyLegacyBackup(a.getString("backup_id"), expected)
                    LegacyConflictRules.require(repository.state(), expected)
                    var preview: JSONObject = LegacyConflictRules.preview(repository.state())
                    var entries: JSONArray = preview.getJSONArray("entries")
                    run {
                        var i: Int = 0
                        while ((i < entries.length())) {
                            var entry: JSONObject = entries.getJSONObject(i)
                            var result: JSONObject = perform("resolve_sync_conflict", entry, (("" + request + "_") + i))
                            i++
                        }
                    }
                    return JSONObject().put("decisions_queued", entries.length()).put("resolution", "keep_current").put("business_data_changed", false).put("backup_id", a.getString("backup_id")).put("history_state", "sync_pending")
                }
            }
            "request_sync" -> {
                return DriveRolePolicy.retiredResponse()
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
            "resolve_sync_conflict" -> {
                run {
                    var resolutionState: JSONObject = repository.state()
                    var conflicts: JSONObject = PixelMcpOwnerTools.conflicts(resolutionState, JSONObject().put("limit", 1).put("client_id", a.getString("client_id")))
                    var found: JSONObject? = null
                    var list: JSONArray = conflicts.getJSONArray("conflicts")
                    run {
                        var i: Int = 0
                        while ((i < list.length())) {
                            if ((a.getString("client_id").equals(list.getJSONObject(i).optString("client_id")))) {
                                found = list.getJSONObject(i)
                            }
                            i++
                        }
                    }
                    if ((((found != null) && !found!!.optBoolean("resolution_available", false)))) {
                        var code: String = found!!.optString("code")
                        if (("SYNC_COMPONENT_UPDATE_REQUIRED".equals(code))) {
                            throw PixelMcpWriteTools.Rejected(code, "Dieser Vorgang ist kein Datenkonflikt. Zuerst Pixel-Verarbeitung aktualisieren und erneut synchronisieren; keine Konfliktentscheidung wurde erzeugt.")
                        }
                        if (("ACTION_ID_CONFLICT".equals(code))) {
                            throw PixelMcpWriteTools.Rejected(code, "Die Kontrollaktion selbst ist widersprüchlich und darf nicht durch eine weitere Konfliktentscheidung verändert werden.")
                        }
                        if (("legacy_inline_photo_requires_original_review".equals(found!!.optString("review_reason")))) {
                            throw PixelMcpWriteTools.Rejected("LEGACY_PHOTO_REVIEW_REQUIRED", "Älteres Fotojournal benötigt Prüfung des Originals. Keine Aktion verworfen oder verändert.")
                        }
                        throw PixelMcpWriteTools.Rejected("CONFLICT_RESOLUTION_NOT_AVAILABLE", "Dieser technische oder geschützte Vorgang kann nicht als normaler Datenkonflikt aufgelöst werden.")
                    }
                    if ((((found == null) || !found!!.has("action")))) {
                        throw IllegalArgumentException("Konfliktaktion nicht vollständig verfügbar; nichts geändert.")
                    }
                    var p: JSONObject = JSONObject(a.toString())
                    p.remove("request_id")
                    var original: JSONObject = found!!.getJSONObject("action")
                    p.put("_original_action", original)
                    var current: JSONObject? = found!!.optJSONObject("current")
                    p.put("_current_base", (if ((current == null)) JSONObject.NULL else (if ((("purge_v1".equals(original.getString("kind")) || TrashPurgeBatch.KIND.equals(original.getString("kind"))))) DriveJournalRules.clean(current) else DriveJournalRules.guard(current, (if ("delete".equals(original.getString("kind"))) original.getJSONObject("payload").getString("entity") else original.getString("kind"))))))
                    var actions: MutableMap<String, JSONObject> = HashMap()
                    actions.put(a.getString("client_id"), found!!.getJSONObject("action"))
                    OwnerJournalRules.apply(SnapshotProjection.effective(resolutionState), JSONObject().put("kind", "conflict_resolution").put("client_id", request).put("payload", p), actions, JSONObject())
                    return repository.enqueue("conflict_resolution", p, request)
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
