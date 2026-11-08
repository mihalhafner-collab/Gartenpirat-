package at.hausgarten.service

import java.util.*
import org.json.*

/**
 * Local archive and trash use cases. Effects pass through the existing owner repository
 * transaction.
 */
class ArchiveApplicationService : OwnerCommand {
    private val repository: OwnerRepository

    constructor(repository: OwnerRepository) {
        this.repository = repository
    }

    @Throws(Exception::class)
    override fun perform(name: String, args: JSONObject, request: String): JSONObject {
        var all: JSONObject = repository.effective()
        var stamp: String = java.time.Instant.now().toString()
        when ((name)) {
            "empty_trash" -> {
                run {
                    val payload =
                        TrashPurgeBatch.payload(
                            all,
                            args.optString(
                                "expected_preview_hash",
                                TrashPurgeBatch.preview(all).getString("preview_hash"),
                            ),
                            args.getBoolean("confirmed"),
                        )
                    val targets = payload.getJSONArray("entries")
                    for (i in 0 until targets.length()) {
                        val target = targets.getJSONObject(i)
                        requireInactiveSession(
                            target.getString("entity"),
                            target.getString("entity_id"),
                        )
                    }
                    val dependents = TrashPurgeBatch.testTargets(all, targets)
                    for (i in 0 until dependents.length()) {
                        val target = dependents.getJSONObject(i)
                        requireInactiveSession(
                            target.getString("entity"),
                            target.getString("entity_id"),
                        )
                    }
                    val result = repository.enqueue(TrashPurgeBatch.KIND, payload, request)
                    val removed = verifyRemoval(targets, true)
                    verifyRemoval(dependents, true, removed)
                    return result
                        .put("deleted", targets.length())
                        .put("recoverable", false)
                        .put("media_cleanup", "local_after_commit")
                }
            }
            "archive_work_records" -> {
                run {
                    var events: JSONArray =
                        WorklogArchiveBatch.actions(
                            all,
                            args.getJSONArray("work_record_ids"),
                            args.getString("expected_preview_hash"),
                            request,
                            stamp,
                        )
                    var saved: JSONArray = JSONArray()
                    run {
                        var i: Int = 0
                        while ((i < events.length())) {
                            var event: JSONObject = events.getJSONObject(i)
                            saved.put(
                                repository.enqueue(
                                    "archive",
                                    event.getJSONObject("payload"),
                                    event.getString("client_id"),
                                )
                            )
                            i++
                        }
                    }
                    verifyRemoval(
                        WorklogArchiveBatch.preview(all, args.getJSONArray("work_record_ids"))
                            .getJSONArray("entries"),
                        false,
                    )
                    return JSONObject()
                        .put("count", saved.length())
                        .put("actions", saved)
                        .put("recoverable", true)
                        .put("physical_delete", false)
                        .put("local_state", "local_saved")
                        .put("sync_state", "not_applicable")
                }
            }
            "purge_entity" -> {
                return purgeOne(all, args, request, stamp).first
            }
            "delete_entity" -> {
                return deleteOne(all, args, request, stamp).first
            }
            "archive_entity" -> {
                return archiveOne(all, args, request, stamp).first
            }
            "delete_work_records" -> {
                val ids = args.getJSONArray("work_record_ids")
                // Validate the whole selection before its first change; the owner request owns the
                // transaction.
                val selection =
                    WorklogArchiveBatch.actions(
                        all,
                        ids,
                        args.getString("expected_preview_hash"),
                        request,
                        stamp,
                    )
                if (!args.getBoolean("confirmed"))
                    throw IllegalArgumentException("Löschauswahl fehlt.")
                var current = all
                for (i in 0 until selection.length()) {
                    val payload = selection.getJSONObject(i).getJSONObject("payload")
                    current =
                        deleteOne(
                                current,
                                JSONObject()
                                    .put("entity", "worklog")
                                    .put("entity_id", payload.getString("id"))
                                    .put(
                                        "expected_record_hash",
                                        payload.getString("expected_record_hash"),
                                    ),
                                "${request}_$i",
                                stamp,
                            )
                            .second
                }
                return JSONObject()
                    .put("count", ids.length())
                    .put("recoverable", false)
                    .put("physical_delete", true)
                    .put("local_state", "local_saved")
                    .put("sync_state", "not_applicable")
            }
            "restore_entity" -> {
                run {
                    var entity: String = args.getString("entity")
                    var id: String = args.getString("entity_id")
                    var p: JSONObject =
                        JSONObject(args.toString()).put("id", id).put("updated_at", stamp)
                    p.remove("request_id")
                    p.remove("entity_id")
                    p.put(
                        "_archive_entry",
                        OwnerJournalRules.archives(all).getJSONObject((("" + entity + ":") + id)),
                    )
                    OwnerJournalRules.apply(
                        JSONObject(all.toString()),
                        JSONObject()
                            .put("client_id", request)
                            .put("kind", "restore")
                            .put("payload", p)
                            .put("created_at", stamp),
                        HashMap(),
                        JSONObject(),
                    )
                    return repository.enqueue("restore", p, request)
                }
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }

    /** A queue acknowledgement alone must never become a successful deletion receipt. */
    private fun verifyRemoval(
        targets: JSONArray,
        permanent: Boolean,
        actual: JSONObject = repository.effective(),
    ): JSONObject {
        for (i in 0 until targets.length()) {
            val target = targets.getJSONObject(i)
            val entity = target.getString("entity")
            val id = target.getString("entity_id")
            val active =
                OwnerJournalRules.findOrNull(
                    actual.optJSONArray(OwnerJournalRules.group(entity)),
                    id,
                )
            val archived = OwnerJournalRules.archives(actual).optJSONObject("$entity:$id")
            val removed =
                if (permanent) active == null && archived == null
                else
                    archived?.has("record") == true &&
                        (active == null ||
                            entity in setOf("customer", "service_template") &&
                                (!active.optBoolean("active", true) ||
                                    active.optInt("active", 1) == 0))
            if (!removed)
                throw PixelMcpWriteTools.Rejected(
                    "DELETE_NOT_APPLIED",
                    "Löschen konnte nicht übernommen werden. Der Eintrag bleibt erhalten.",
                )
        }
        return actual
    }

    @Throws(Exception::class)
    private fun requireInactiveSession(entity: String, id: String) {
        ActiveWorkProtection.require(repository.state(), entity, id)
    }

    /** Both internal journal steps commit together; no recoverable row is published. */
    private fun deleteOne(
        all: JSONObject,
        args: JSONObject,
        request: String,
        stamp: String,
    ): Pair<JSONObject, JSONObject> {
        val afterArchive = archiveOne(all, args, request + "_archive", stamp).second
        val entity = args.getString("entity")
        val id = args.getString("entity_id")
        val archived = OwnerJournalRules.archives(afterArchive).getJSONObject("$entity:$id")
        val purged =
            purgeOne(
                afterArchive,
                JSONObject()
                    .put("entity", entity)
                    .put("entity_id", id)
                    .put("expected_record_hash", OwnerJournalRules.version(archived))
                    .put("confirmed", true),
                request,
                stamp,
            )
        purged.first.put("physical_delete", true)
        return purged
    }

    /** The private second value is the freshly verified state, never part of a public receipt. */
    private fun purgeOne(
        all: JSONObject,
        a: JSONObject,
        request: String,
        stamp: String,
    ): Pair<JSONObject, JSONObject> {
        requireInactiveSession(a.getString("entity"), a.getString("entity_id"))
        val p =
            JSONObject()
                .put("id", a.getString("entity_id"))
                .put(
                    "expected_record_hash",
                    a.optString(
                        "expected_record_hash",
                        OwnerJournalRules.version(
                            OwnerJournalRules.archives(all)
                                .getJSONObject(
                                    a.getString("entity") + ":" + a.getString("entity_id")
                                )
                        ),
                    ),
                )
                .put("updated_at", stamp)
                .put("entity", a.getString("entity"))
                .put("confirmed", a.getBoolean("confirmed"))
        val dependents =
            TrashRules.testTargets(all, a.getString("entity"), a.getString("entity_id"))
        p.put("expected_test_dependencies_hash", PixelMcpWriteTools.hash(dependents))
            .put("test_dependencies", dependents)
        for (i in 0 until dependents.length()) {
            val target = dependents.getJSONObject(i)
            requireInactiveSession(target.getString("entity"), target.getString("entity_id"))
        }
        OwnerJournalRules.apply(
            JSONObject(all.toString()),
            JSONObject()
                .put("kind", "purge_v1")
                .put("payload", p)
                .put("client_id", request)
                .put("created_at", stamp),
            HashMap(),
            JSONObject(),
        )
        val result = repository.enqueue("purge_v1", p, request)
        val removed =
            verifyRemoval(
                JSONArray()
                    .put(
                        JSONObject()
                            .put("entity", a.getString("entity"))
                            .put("entity_id", a.getString("entity_id"))
                    ),
                true,
            )
        verifyRemoval(dependents, true, removed)
        return result.put("recoverable", false).put("media_cleanup", "local_after_commit") to
            removed
    }

    @Throws(Exception::class)
    private fun archiveOne(
        all: JSONObject,
        args: JSONObject,
        request: String,
        stamp: String,
    ): Pair<JSONObject, JSONObject> {
        requireInactiveSession(args.getString("entity"), args.getString("entity_id"))
        if ((args.getString("entity").equals("appointment"))) {
            var session: JSONObject = repository.state().getJSONObject("work_session")
            if (
                ((args.getString("entity_id").equals(session.optString("appointment_id")) &&
                    !Arrays.asList("IDLE", "FINALIZED", "CANCELLED")
                        .contains(session.optString("state"))))
            ) {
                throw IllegalArgumentException(
                    "Zeiterfassung dieses Termins zuerst abschließen oder bewusst verwerfen. Nichts archiviert."
                )
            }
        }
        val entity = args.getString("entity")
        val id = args.getString("entity_id")
        val current =
            OwnerJournalRules.findOrNull(all.getJSONArray(OwnerJournalRules.group(entity)), id)
                ?: throw IllegalArgumentException(
                    "Der Eintrag fehlt oder liegt bereits im Papierkorb."
                )
        val p =
            DeletionRules.payload(
                all,
                entity,
                id,
                args.optString("expected_record_hash", OwnerJournalRules.version(current)),
                stamp,
            )
        if (args.has("expected_photo_links_hash"))
            p.put("expected_photo_links_hash", args.getString("expected_photo_links_hash"))
        OwnerJournalRules.apply(
            JSONObject(all.toString()),
            JSONObject()
                .put("kind", "archive")
                .put("client_id", request)
                .put("payload", p)
                .put("created_at", stamp),
            HashMap(),
            JSONObject(),
        )
        val result = repository.enqueue("archive", p, request)
        val archived =
            verifyRemoval(
                JSONArray().put(JSONObject().put("entity", entity).put("entity_id", id)),
                false,
            )
        return result.put("recoverable", true).put("physical_delete", false) to archived
    }
}
