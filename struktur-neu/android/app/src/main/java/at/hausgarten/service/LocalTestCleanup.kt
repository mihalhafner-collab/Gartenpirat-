package at.hausgarten.service

import android.content.ContentValues
import java.util.*
import org.json.*

internal class LocalTestCleanup {
    fun interface Values {
        @Throws(Exception::class) fun get(key: String, fallback: String): String
    }

    constructor() {}

    companion object {
        private val KEY: String = "purged_local_test_records_v1"

        @JvmStatic
        @Throws(Exception::class)
        fun overlay(store: PixelLocalStore, master: JSONObject): Unit {

            overlay(store::ownerValue, master)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun overlay(values: Values, master: JSONObject): Unit {

            var tombstones = JSONObject(values.get(KEY, "{}"))
            run {
                var it = tombstones.keys()
                var firstIteration60 = true
                while (true) {
                    if (firstIteration60) firstIteration60 = false else {}
                    if (!(it.hasNext())) break

                    var key = it.next()
                    LocalTestTombstone.project(master, key, tombstones.getJSONObject(key))
                }
            }
        }

        @JvmStatic
        fun sameTarget(event: JSONObject, entity: String, id: String): Boolean {

            var kind = event.optString("kind")
            var p = event.optJSONObject("payload")
            if (((p == null) || !((id == p.optString("id"))))) {
                return false
            }
            return ((((entity == kind) ||
                (Arrays.asList("archive", "restore", "purge_v1").contains(kind) &&
                    (entity == p.optString("entity")))) ||
                ((entity == "offer") && kind.startsWith("offer_"))) ||
                ((entity == "invoice") && kind.startsWith("invoice_")))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun syntheticOrigin(
            raw: JSONObject,
            pending: JSONArray,
            entity: String,
            id: String,
        ): Boolean {

            var record =
                OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)), id)
            if ((record == null)) {
                var a = raw.optJSONObject("owner_archive")
                var saved = (if ((a == null)) null else a.optJSONObject(((entity + ":") + id)))
                record = (if ((saved == null)) null else saved.optJSONObject("record"))
            }
            run {
                var i: Int = 0
                var firstIteration61 = true
                while (true) {
                    if (firstIteration61) firstIteration61 = false
                    else {
                        i++
                    }
                    if (!(((record == null) && (i < pending.length())))) break

                    var event = pending.getJSONObject(i)
                    var p = event.getJSONObject("payload")
                    if ((!(sameTarget(event, entity, id)) || !(p.optBoolean("_local_only")))) {
                        continue
                    }
                    record = p.optJSONObject("_archive_record")
                    if (((record == null) && (entity == event.optString("kind")))) {
                        record = p
                    }
                }
            }
            if ((record == null)) {
                return false
            }
            if ((entity == "customer")) {
                return TestDataRules.customer(record)
            }
            if (TestDataRules.marked(record)) {
                return true
            }
            var customer = record.optString("customer_id")
            var c = OwnerJournalRules.findOrNull(raw.optJSONArray("customers"), customer)
            if (TestDataRules.customer(c)) {
                return true
            }
            run {
                var i: Int = 0
                var firstIteration62 = true
                while (true) {
                    if (firstIteration62) firstIteration62 = false
                    else {
                        i++
                    }
                    if (!((i < pending.length()))) break

                    var a = pending.getJSONObject(i)
                    if (
                        (((a.optString("kind") == "customer") &&
                            (a.getJSONObject("payload").optString("id") == customer)) &&
                            TestDataRules.customer(a.getJSONObject("payload")))
                    ) {
                        return true
                    }
                }
            }
            return false
        }

        @JvmStatic
        @Throws(Exception::class)
        fun compact(store: PixelLocalStore): Int {

            var initial = store.pendingActions()
            var purge: Boolean = false
            run {
                var i: Int = 0
                var firstIteration63 = true
                while (true) {
                    if (firstIteration63) firstIteration63 = false
                    else {
                        i++
                    }
                    if (!((i < initial.length()))) break

                    var a = initial.getJSONObject(i)
                    if (
                        (a.getJSONObject("payload").optBoolean("_local_only") &&
                            Arrays.asList("purge_v1", TrashPurgeBatch.KIND)
                                .contains(a.optString("kind")))
                    ) {
                        purge = true
                    }
                }
            }
            if (!(purge)) {
                cleanupMedia(store)
                return 0
            }
            var db = store.getWritableDatabase()
            store.beginBusinessTransaction(db)
            var removed: Int = 0
            try {

                var tables = store.businessTables()
                var rowStorage: Boolean = tables.projectionCurrent()
                var state = store.getState()
                var raw = (if (rowStorage) tables.originalSnapshot() else store.rawSnapshot("{}"))
                var pending = store.pendingActions()
                var effective = PixelMcpReadTools.effective(state)
                var archive = OwnerJournalRules.archives(effective)
                var receipts = JSONObject(store.ownerValue(KEY, "{}"))
                var session = state.getJSONObject("work_session")
                var candidates = JSONArray()
                run {
                    var i: Int = 0
                    var firstIteration65 = true
                    while (true) {
                        if (firstIteration65) firstIteration65 = false
                        else {
                            i++
                        }
                        if (!((i < pending.length()))) break

                        var a = pending.getJSONObject(i)
                        var p = a.getJSONObject("payload")
                        if (!(p.optBoolean("_local_only"))) {
                            continue
                        }
                        if ((a.optString("kind") == "purge_v1")) {
                            candidates.put(a)
                        } else {
                            if ((a.optString("kind") == TrashPurgeBatch.KIND)) {
                                var entries = p.getJSONArray("entries")
                                run {
                                    var j: Int = 0
                                    var firstIteration64 = true
                                    while (true) {
                                        if (firstIteration64) firstIteration64 = false
                                        else {
                                            j++
                                        }
                                        if (!((j < entries.length()))) break

                                        var e = entries.getJSONObject(j)
                                        var entity = e.getString("entity")
                                        var id = e.getString("entity_id")
                                        candidates.put(
                                            JSONObject()
                                                .put(
                                                    "client_id",
                                                    ((((a.getString("client_id") + ":") + entity) +
                                                        ":") + id),
                                                )
                                                .put("_backup_request", a.getString("client_id"))
                                                .put("created_at", a.getString("created_at"))
                                                .put(
                                                    "payload",
                                                    JSONObject().put("entity", entity).put("id", id),
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                run {
                    var i: Int = 0
                    var firstIteration67 = true
                    while (true) {
                        if (firstIteration67) firstIteration67 = false
                        else {
                            i++
                        }
                        if (!((i < candidates.length()))) break

                        var action = candidates.getJSONObject(i)
                        var p = action.getJSONObject("payload")
                        var entity = p.optString("entity")
                        var id = p.optString("id")
                        var key = ((entity + ":") + id)
                        var tombstone = archive.optJSONObject(key)
                        if (
                            ((((tombstone == null) || !(tombstone.optBoolean("purged"))) ||
                                !((action.optString("client_id") ==
                                    tombstone.optString("purge_action")))) ||
                                !(syntheticOrigin(raw, pending, entity, id)))
                        ) {
                            continue
                        }
                        if (
                            (!(Arrays.asList("IDLE", "FINALIZED", "CANCELLED")
                                .contains(session.optString("state"))) &&
                                (((id == session.optString((entity + "_id"))) ||
                                    (id == session.optString("finalized_worklog_id")))))
                        ) {
                            continue
                        }
                        TrashRules.requireUnreferenced(effective, entity, id)
                        var receipt =
                            JSONObject(
                                store.ownerValue(
                                    ("receipt:" +
                                        action.optString(
                                            "_backup_request",
                                            action.getString("client_id"),
                                        )),
                                    "{}",
                                )
                            )
                        var result = receipt.optJSONObject("result")
                        var backup =
                            (if ((result == null)) "" else result.optString("deletion_backup_id"))
                        if (backup.isEmpty()) {
                            continue
                        }
                        var repo = LocalOwnerRepository(store.appContext(), store)
                        var meta = repo.fileMeta(backup)
                        repo.inspectDeletionBackup(backup, meta)
                        var local: Boolean =
                            ((OwnerJournalRules.findOrNull(
                                raw.optJSONArray(OwnerJournalRules.group(entity)),
                                id,
                            ) == null) && !(OwnerJournalRules.archives(raw).has(key)))
                        if (!(rowStorage)) {
                            promoteSharedRemoval(store, raw, pending, entity, id, action)
                        }
                        var compacted =
                            JSONObject(tombstone.toString()).put("local_test_only", local)
                        var original =
                            OwnerJournalRules.findOrNull(
                                raw.optJSONArray(OwnerJournalRules.group(entity)),
                                id,
                            )
                        var oldArchive = OwnerJournalRules.archives(raw).optJSONObject(key)
                        if ((original != null)) {
                            compacted.put("source_record_hash", OwnerJournalRules.version(original))
                        }
                        if ((oldArchive != null)) {
                            compacted.put(
                                "source_archive_hash",
                                OwnerJournalRules.version(oldArchive),
                            )
                        }
                        receipts.put(key, compacted)
                        run {
                            var j: Int = 0
                            var firstIteration66 = true
                            while (true) {
                                if (firstIteration66) firstIteration66 = false
                                else {
                                    j++
                                }
                                if (!((j < pending.length()))) break

                                var old = pending.getJSONObject(j)
                                if (
                                    (old.getJSONObject("payload").optBoolean("_local_only") &&
                                        sameTarget(old, entity, id))
                                ) {
                                    markReceipt(store, old.getString("client_id"), entity, id)
                                    removed +=
                                        db.delete(
                                            "pending",
                                            "id=? AND kind!='mcp_receipt'",
                                            arrayOf(old.getString("client_id")),
                                        )
                                }
                            }
                        }
                    }
                }
                run {
                    var i: Int = 0
                    var firstIteration69 = true
                    while (true) {
                        if (firstIteration69) firstIteration69 = false
                        else {
                            i++
                        }
                        if (!((i < pending.length()))) break

                        var a = pending.getJSONObject(i)
                        var p = a.getJSONObject("payload")
                        if (
                            (!((a.optString("kind") == TrashPurgeBatch.KIND)) ||
                                !(p.optBoolean("_local_only")))
                        ) {
                            continue
                        }
                        var complete: Boolean = true
                        var entries = p.getJSONArray("entries")
                        run {
                            var j: Int = 0
                            var firstIteration68 = true
                            while (true) {
                                if (firstIteration68) firstIteration68 = false
                                else {
                                    j++
                                }
                                if (!((j < entries.length()))) break

                                var e = entries.getJSONObject(j)
                                if (
                                    !(receipts.has(
                                        ((e.getString("entity") + ":") + e.getString("entity_id"))
                                    ))
                                ) {
                                    complete = false
                                }
                            }
                        }
                        if (complete) {
                            markReceipt(store, a.getString("client_id"), TrashPurgeBatch.KIND, "")
                            removed +=
                                db.delete("pending", "id=?", arrayOf(a.getString("client_id")))
                        }
                    }
                }
                store.putOwnerValue(KEY, receipts.toString())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            cleanupMedia(store)
            return removed
        }

        private @JvmStatic @Throws(Exception::class) fun cleanupMedia(
            store: PixelLocalStore
        ): Unit {

            var tombstones = JSONObject(store.ownerValue(KEY, "{}"))
            run {
                var it = tombstones.keys()
                var firstIteration70 = true
                while (true) {
                    if (firstIteration70) firstIteration70 = false else {}
                    if (!(it.hasNext())) break

                    var key = it.next()
                    var receipt = tombstones.getJSONObject(key)
                    if (!(receipt.optBoolean("local_test_only"))) {
                        continue
                    }
                    var marker = ("local_test_media_cleanup:" + PixelMcpWriteTools.hash(key))
                    if ((store.ownerValue(marker, "") == "done")) {
                        continue
                    }
                    try {

                        if ((key.startsWith("photo:") && receipt.has("media"))) {
                            store.cleanupPurgedPhoto(
                                key.substring(6),
                                receipt.getJSONObject("media"),
                                marker,
                            )
                        } else {
                            if (receipt.has("document")) {
                                store.cleanupPurgedDocument(
                                    key,
                                    receipt.getJSONObject("document"),
                                    marker,
                                )
                            }
                        }
                    } catch (pending: Exception) {
                        store.putOwnerValue("local_test_cleanup_error", "MEDIA_CLEANUP_PENDING")
                    }
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun markReceipt(
            store: PixelLocalStore,
            client: String,
            entity: String,
            id: String,
        ): Unit {

            var db = store.getWritableDatabase()
            var key = ("receipt_" + client)
            db.rawQuery(
                    "SELECT payload FROM pending WHERE id=? AND kind='mcp_receipt'",
                    arrayOf(key),
                )
                .use { row ->
                    if (row.moveToFirst()) {
                        var saved = JSONObject(row.getString(0))
                        if (
                            ((entity == saved.optString("entity_type")) &&
                                (id ==
                                    saved.optString(
                                        "entity_id",
                                        saved.optString("work_record_id"),
                                    )))
                        ) {
                            saved.put("test_local_only", true).put("test_purged", true)
                            var values = ContentValues()
                            values.put("payload", saved.toString())
                            db.update(
                                "pending",
                                values,
                                "id=? AND kind='mcp_receipt'",
                                arrayOf(key),
                            )
                        }
                    }
                }
            var old = store.ownerValue(("receipt:" + client), "")
            if (!(old.isEmpty())) {
                var saved = JSONObject(old)
                var result = saved.optJSONObject("result")
                if ((result != null)) {
                    result.put("test_local_only", true).put("test_purged", true)
                    store.putOwnerValue(("receipt:" + client), saved.toString())
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun promoteSharedRemoval(
            store: PixelLocalStore,
            raw: JSONObject,
            pending: JSONArray,
            entity: String,
            id: String,
            purge: JSONObject,
        ): Unit {

            var record =
                OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)), id)
            var saved = OwnerJournalRules.archives(raw).optJSONObject(((entity + ":") + id))
            if (((record == null) && (((saved == null) || saved.optBoolean("purged"))))) {
                return
            }
            var shared = JSONObject(raw.toString())
            var existing = JSONArray()
            run {
                var i: Int = 0
                var firstIteration71 = true
                while (true) {
                    if (firstIteration71) firstIteration71 = false
                    else {
                        i++
                    }
                    if (!((i < pending.length()))) break

                    if (
                        !(pending
                            .getJSONObject(i)
                            .getJSONObject("payload")
                            .optBoolean("_local_only"))
                    ) {
                        existing.put(pending.get(i))
                    }
                }
            }
            DriveJournalRules.apply(shared, existing)
            if (TrashRules.purged(shared, entity, id)) {
                return
            }
            var stamp = purge.optString("created_at")
            var base =
                ("test_cleanup_" +
                    PixelMcpWriteTools.hash(
                            ((((purge.getString("client_id") + ":") + entity) + ":") + id)
                        )
                        .substring(0, 40))
            var events = JSONArray()
            if (!(OwnerJournalRules.archives(shared).has(((entity + ":") + id)))) {
                var current =
                    OwnerJournalRules.findOrNull(
                        shared.getJSONArray(OwnerJournalRules.group(entity)),
                        id,
                    )
                if ((current == null)) {
                    throw IllegalArgumentException(
                        "Testoriginal fehlt für versionsgeschützte Bereinigung."
                    )
                }
                var event =
                    JSONObject()
                        .put("client_id", (base + "_archive"))
                        .put("kind", "archive")
                        .put("created_at", stamp)
                        .put(
                            "payload",
                            DeletionRules.payload(
                                shared,
                                entity,
                                id,
                                OwnerJournalRules.version(current),
                                stamp,
                            ),
                        )
                OwnerJournalRules.apply(shared, event, HashMap(), JSONObject())
                events.put(event)
            }
            var entry = OwnerJournalRules.archives(shared).getJSONObject(((entity + ":") + id))
            var payload =
                JSONObject()
                    .put("entity", entity)
                    .put("id", id)
                    .put("confirmed", true)
                    .put("expected_record_hash", OwnerJournalRules.version(entry))
                    .put("updated_at", stamp)
            if ((events.length() > 0)) {
                payload.put("_sync_predecessors", JSONArray().put((base + "_archive")))
            }
            var event =
                JSONObject()
                    .put("client_id", (base + "_purge"))
                    .put("kind", "purge_v1")
                    .put("created_at", stamp)
                    .put("payload", payload)
            TrashRules.purge(shared, event)
            events.put(event)
            run {
                var i: Int = 0
                var firstIteration72 = true
                while (true) {
                    if (firstIteration72) firstIteration72 = false
                    else {
                        i++
                    }
                    if (!((i < events.length()))) break

                    var e = events.getJSONObject(i)
                    var row = ContentValues()
                    row.put("id", e.getString("client_id"))
                    row.put("kind", e.getString("kind"))
                    row.put("created_at", stamp)
                    row.put("payload", e.getJSONObject("payload").toString())
                    store.getWritableDatabase().insertOrThrow("pending", null, row)
                }
            }
        }
    }
}
