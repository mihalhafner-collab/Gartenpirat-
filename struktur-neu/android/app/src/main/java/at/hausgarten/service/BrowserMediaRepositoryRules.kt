package at.hausgarten.service

import org.json.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

/** Transactional retained media graph ownership, replay, restore review and verified cleanup. */
class BrowserMediaRepositoryRules {
    fun interface Work {
        @Throws(Exception::class) fun run(): JSONObject
    }
    interface Store {
        @Throws(Exception::class) fun identity(): JSONObject
        @Throws(Exception::class) fun get(key: String, fallback: String): String
        @Throws(Exception::class) fun put(key: String, value: String): Unit
        @Throws(Exception::class) fun remove(key: String): Unit
        @Throws(Exception::class) fun entries(prefix: String): JSONArray
        @Throws(Exception::class) fun transaction(work: Work): JSONObject
        fun fileDeletionAllowed(): Boolean {

            return true
        }
    }
    private val store: Store
    private val media: File?
    private val bound: JSONObject
    @Throws(Exception::class) constructor(store: Store, media: File?) {
        this.store = store
        this.media = media
        bound = identity(store.identity())
    }
    private @Throws(Exception::class) fun assertContext(): Unit {

        if (!((hash(bound) == hash(identity(store.identity()))))) {
            throw fail("CONTEXT_CHANGED")
        }
    }
    private fun matches(value: JSONObject): Boolean {

        return (value.optString("installation_id") == bound.optString("installation_id")) && (value.optString("restore_epoch") == bound.optString("restore_epoch"))
    }
    private @Throws(Exception::class) fun fresh(): JSONObject {

        return copy(bound).put("formatVersion", 1).put("mode", "MIRROR_REQUIRED").put("mirror_complete", false).put("device_mirror_complete", false)
    }
    private @Throws(Exception::class) fun state(): JSONObject {

        assertContext()
        var raw = store.get(PREFIX + "state", "")
        var state = (if (raw.isEmpty()) fresh() else JSONObject(raw))
        version(state)
        if (!(matches(state))) {
            throw fail("CONTEXT_CHANGED")
        }
        if (!(Arrays.asList("MIRROR_REQUIRED", "ACTIVE", "RESTORE_REVIEW").contains(state.optString("mode")))) {
            throw fail("STATE_INVALID")
        }
        return state
    }
    private @Throws(Exception::class) fun saveState(state: JSONObject): Unit {

        store.put(PREFIX + "state", state.toString())
    }
    private @Throws(Exception::class) fun mutate(operation: String, request: String?, args: JSONObject, work: Work): JSONObject {

        val request = request(request)
        return store.transaction(LAM@ {
            state()
            var receiptKey = PREFIX + "request:" + hash(request)
            var fingerprint = hash(JSONObject().put("operation", operation).put("args", args).put("context", bound))
            var raw = store.get(receiptKey, "")
            if (!(raw.isEmpty())) {
                var receipt = JSONObject(raw)
                version(receipt)
                if (!(matches(receipt))) {
                    throw fail("CONTEXT_CHANGED")
                }
                if (!((fingerprint == receipt.optString("fingerprint")))) {
                    throw fail("REPLAY_MISMATCH")
                }
                return@LAM copy(receipt.getJSONObject("result")).put("replayed", true)
            }
            var result = work.run().put("formatVersion", 1).put("installation_id", bound.getString("installation_id")).put("restore_epoch", bound.getString("restore_epoch")).put("replayed", false)
            var receipt = copy(bound).put("formatVersion", 1).put("fingerprint", fingerprint).put("result", result)
            store.put(receiptKey, receipt.toString())
            return@LAM result
        })
    }
    @Throws(Exception::class) fun status(): JSONObject {

        var state = state()
        var pending = JSONArray()
        var cleanups = JSONArray()
        var entries = rows("write:")
        for (i in 0 until entries.length()) {

            var row = value(entries.getJSONObject(i))
            if (matches(row) && (row.optString("phase") == "COMMITTED") && ((row.optString("record_id") == hash(JSONObject.NULL)) || row.optBoolean("cleanup_requested")) && !(row.optBoolean("cleanup_complete"))) {
                cleanups.put(JSONObject().put("request_id", row.getString("request_id")).put("key", row.getString("key")))
            }
            if (matches(row) && (row.optString("phase") == "PREPARED")) {
                pending.put(JSONObject().put("request_id", row.getString("request_id")).put("key", row.getString("key")).put("expected_record_hash", row.getString("expected")).put("created_at", row.optString("created_at")))
            }

        }
        return copy(bound).put("formatVersion", 1).put("mode", state.getString("mode")).put("mirror_complete", complete(state)).put("pending_write_count", pending.length()).put("pending_writes", pending).put("pending_cleanups", cleanups).put("file_cleanup_pending", state.optBoolean("file_cleanup_pending"))
    }
    private @Throws(Exception::class) fun rows(prefix: String): JSONArray {

        return store.entries(PREFIX + prefix)
    }
    private @Throws(Exception::class) fun record(id: String?): JSONObject {

        if (id == null || !(id.matches(Regex("[0-9a-f]{64}")))) {
            throw fail("RECORD_INVALID")
        }
        var raw = store.get(PREFIX + "record:" + id, "")
        if (raw.isEmpty()) {
            throw fail("RECORD_MISSING")
        }
        var record = JSONObject(raw)
        version(record)
        var graph = record.opt("graph")
        if (graph == null || !((graph == JSONObject.NULL || graph is JSONObject)) || !((id == record.optString("record_id"))) || !((id == hash(graph)))) {
            throw fail("RECORD_INVALID")
        }
        if (graph is JSONObject) {
            BrowserMediaFiles.references((graph as JSONObject))
        }
        return record
    }
    private @Throws(Exception::class) fun storeGraph(graph: JSONObject?): String {

        var body = (if (graph == null) JSONObject.NULL else graph)
        if (graph != null) {
            if (graph.toString().toByteArray(StandardCharsets.UTF_8).size > 2 * 1024 * 1024) {
                throw fail("GRAPH_TOO_LARGE")
            }
            verify(graph)
        }
        var id = hash(body)
        var raw = store.get(PREFIX + "record:" + id, "")
        if (raw.isEmpty()) {
            store.put(PREFIX + "record:" + id, JSONObject().put("formatVersion", 1).put("record_id", id).put("graph", body).toString())
        } else {
            record(id)
        }
        return id
    }
    private @Throws(Exception::class) fun verify(graph: JSONObject): Unit {

        var refs = BrowserMediaFiles.references(graph)
        for (i in 0 until refs.length()) {

            BrowserMediaFiles.verifyReference(media!!, refs.getJSONObject(i))

        }
    }
    private @Throws(Exception::class) fun verifyRecord(id: String): Unit {

        var graph = record(id).get("graph")
        if (graph is JSONObject) {
            verify((graph as JSONObject))
        }
    }
    private @Throws(Exception::class) fun pointer(key: String?): JSONObject? {

        var raw = store.get(PREFIX + "active:" + hash(key), "")
        if (raw.isEmpty()) {
            return null
        }
        var value = JSONObject(raw)
        version(value)
        if (!((key == value.optString("key"))) || !(matches(value))) {
            throw fail("CONTEXT_CHANGED")
        }
        record(value.getString("record_id"))
        return value
    }
    @Throws(Exception::class) fun deferRestoreReview(request: String?): JSONObject {

        return mutate("defer_review", request, JSONObject(), LAM@ {
            var state = state()
            if ((state.optString("mode") == "ACTIVE")) {
                return@LAM status()
            }
            if (!((state.optString("mode") == "RESTORE_REVIEW"))) {
                throw fail("RESTORE_REVIEW_REQUIRED")
            }
            var id = hash(JSONObject().put("defer_request", request).put("context", bound))
            store.put(PREFIX + "mirror:" + id, copy(bound).put("formatVersion", 1).put("mirror_id", id).put("source", "active").put("phase", "COMPLETE").toString())
            state.put("mode", "ACTIVE").put("review_deferred", true).put("active_mirror", id).put("mirror_complete", true)
            saveState(state)
            return@LAM status()
        })
    }
    @Throws(Exception::class) fun readRecord(key: String?): JSONObject {

        val key = key(key)
        var state = state()
        if (!((state.getString("mode") == "ACTIVE")) || !(complete(state))) {
            throw fail((if ((state.getString("mode") == "RESTORE_REVIEW")) "RESTORE_REVIEW_REQUIRED" else "MIRROR_REQUIRED"))
        }
        var pointer = pointer(key)
        if (pointer == null && state.optBoolean("review_deferred")) {
            var candidates = rows("candidate:")
            for (i in 0 until candidates.length()) {

                var candidate = value(candidates.getJSONObject(i))
                if (currentCandidate(candidate, state) && (key == candidate.optString("key"))) {
                    throw fail("RESTORE_REVIEW_REQUIRED")
                }

            }
        }
        var id = (if (pointer == null) hash(JSONObject.NULL) else pointer.getString("record_id"))
        if (pointer != null) {
            verifyRecord(id)
        }
        var graph = (if (pointer == null) JSONObject.NULL else record(id).get("graph"))
        return copy(bound).put("formatVersion", 1).put("key", key).put("record_id", (if (pointer == null) JSONObject.NULL else id)).put("record_hash", id).put("record", graph).put("restored_request_review_required", pointer != null && pointer.optBoolean("restored_request_review_required"))
    }
    private @Throws(Exception::class) fun intentKey(request: String?): String {

        return PREFIX + "write:" + hash(request)
    }
    private @Throws(Exception::class) fun intent(request: String?): JSONObject {

        var raw = store.get(intentKey(request), "")
        if (raw.isEmpty()) {
            throw fail("WRITE_PREPARATION_REQUIRED")
        }
        var value = JSONObject(raw)
        version(value)
        if (!(matches(value)) || !((request == value.optString("request_id"))) || !(Arrays.asList("PREPARED", "COMMITTED", "CANCELLED").contains(value.optString("phase")))) {
            throw fail("CONTEXT_CHANGED")
        }
        return value
    }
    private @Throws(Exception::class) fun pendingWrites(): Int {

        var count: Int = 0
        var entries = rows("write:")
        for (i in 0 until entries.length()) {

            var value = value(entries.getJSONObject(i))
            if (matches(value) && (value.optString("phase") == "PREPARED")) {
                count++
            }

        }
        return count
    }
    @Throws(Exception::class) fun prepareRecordWrite(request: String?, key: String?, expected: String?): JSONObject {

        synchronized(FILE_CAPTURE_LOCK) {
            return prepareRecordWriteLocked(request, key, expected)
        }
    }
    private @Throws(Exception::class) fun prepareRecordWriteLocked(request: String?, key: String?, expected: String?): JSONObject {

        val request = request(request)
        val key = key(key)
        return store.transaction(LAM@ {
            var current = readRecord(key)
            var raw = store.get(intentKey(request), "")
            if (!(raw.isEmpty())) {
                var saved = intent(request)
                if (!((key == saved.getString("key"))) || !(Objects.equals(expected, saved.optString("expected")))) {
                    throw fail("REPLAY_MISMATCH")
                }
                return@LAM JSONObject().put("request_id", request).put("phase", saved.getString("phase")).put("replayed", true)
            }
            if (!(Objects.equals(expected, current.getString("record_hash")))) {
                throw fail("WRITE_CONFLICT")
            }
            var intents = rows("write:")
            for (i in 0 until intents.length()) {

                var other = value(intents.getJSONObject(i))
                if (matches(other) && (key == other.optString("key")) && (other.optString("phase") == "PREPARED")) {
                    throw fail("WRITE_PENDING")
                }

            }
            var saved = copy(bound).put("formatVersion", 1).put("request_id", request).put("key", key).put("expected", expected).put("phase", "PREPARED").put("created_at", stamp())
            store.put(intentKey(request), saved.toString())
            return@LAM JSONObject().put("request_id", request).put("phase", "PREPARED").put("replayed", false)
        })
    }
    @Throws(Exception::class) fun cancelRecordWrite(request: String?, key: String?, expected: String?, reason: String): JSONObject {

        val request = request(request)
        val key = key(key)
        if (!(Arrays.asList("BUFFER_STORAGE_FAILED", "DISCARDED").contains(reason))) {
            throw fail("INPUT_INVALID")
        }
        return store.transaction(LAM@ {
            var saved = intent(request)
            if (!((key == saved.getString("key"))) || !(Objects.equals(expected, saved.optString("expected")))) {
                throw fail("REPLAY_MISMATCH")
            }
            if ((saved.getString("phase") == "COMMITTED")) {
                throw fail("WRITE_ALREADY_COMMITTED")
            }
            var replay: Boolean = (saved.getString("phase") == "CANCELLED")
            saved.put("phase", "CANCELLED").put("reason", reason)
            store.put(intentKey(request), saved.toString())
            return@LAM JSONObject().put("request_id", request).put("phase", "CANCELLED").put("replayed", replay)
        })
    }
    @Throws(Exception::class) fun writeRecord(request: String?, key: String?, expected: String?, graph: JSONObject?): JSONObject {

        val key = key(key)
        var result = mutate("write", request, JSONObject().put("key", key).put("expected", expected).put("graph_hash", hash((if (graph == null) JSONObject.NULL else graph))), LAM@ {
            var prepared = intent(request)
            if (!((key == prepared.getString("key"))) || !(Objects.equals(expected, prepared.optString("expected")))) {
                throw fail("REPLAY_MISMATCH")
            }
            if (!((prepared.getString("phase") == "PREPARED"))) {
                throw fail("WRITE_CANCELLED")
            }
            var current = readRecord(key)
            if (expected == null || !((expected == current.getString("record_hash")))) {
                throw fail("WRITE_CONFLICT")
            }
            var id = storeGraph(graph)
            var pointer = copy(bound).put("formatVersion", 1).put("key", key).put("record_id", id).put("restored_request_review_required", graph != null && current.optBoolean("restored_request_review_required"))
            store.put(PREFIX + "active:" + hash(key), pointer.toString())
            store.put(PREFIX + "history:" + hash(request), copy(pointer).put("before", current.opt("record_id")).put("request_id", request).put("at", stamp()).toString())
            prepared.put("phase", "COMMITTED").put("record_id", id)
            store.put(intentKey(request), prepared.toString())
            return@LAM JSONObject().put("key", key).put("record_id", id).put("record_hash", id).put("local_state", "local_saved")
        })
        if (graph == null) {
            try {

                cleanupDeletedKey(key, request)

            } catch (deferred: Exception) {
                result.put("cleanup_pending", true)
            }
        }
        return result
    }
    @Throws(Exception::class) fun cleanupFiles(): JSONObject {

        synchronized(FILE_CAPTURE_LOCK) {
            store.transaction(LAM@ {
                var state = state()
                state.put("file_cleanup_pending", true)
                saveState(state)
                return@LAM state
            })
            if (pendingWrites() > 0 || !(store.fileDeletionAllowed())) {
                throw fail("WRITE_PENDING")
            }
            var mirrors = rows("mirror:")
            for (i in 0 until mirrors.length()) {

                var mirror = value(mirrors.getJSONObject(i))
                if (matches(mirror) && (mirror.optString("phase") == "OPEN")) {
                    throw fail("MIRROR_REQUIRED")
                }

            }
            var keep = HashSet<String>()
            var records = rows("record:")
            for (i in 0 until records.length()) {

                var graph = value(records.getJSONObject(i)).get("graph")
                if (graph is JSONObject) {
                    var refs = BrowserMediaFiles.references((graph as JSONObject))
                    for (j in 0 until refs.length()) {

                        keep.add(refs.getJSONObject(j).getString("sha256"))

                    }
                }

            }
            BrowserMediaFiles.purgeUnreferenced(media, keep)
            store.transaction(LAM@ {
                var state = state()
                state.put("file_cleanup_pending", false)
                saveState(state)
                return@LAM state
            })
            return JSONObject().put("cleanup_complete", true)
        }
    }
    @Throws(Exception::class) fun cleanupDeletedKey(key: String?, request: String?): JSONObject {

        val key = key(key)
        val request = request(request)
        synchronized(FILE_CAPTURE_LOCK) {
            var proof = intent(request)
            if (!((proof.optString("phase") == "COMMITTED")) || !((proof.optString("key") == key))) {
                throw fail("WRITE_PENDING")
            }
            store.transaction(LAM@ {
                var started = intent(request)
                started.put("cleanup_requested", true)
                store.put(intentKey(request), started.toString())
                return@LAM started
            })
            if (pendingWrites() > 0) {
                throw fail("WRITE_PENDING")
            }
            var mirrors = rows("mirror:")
            for (i in 0 until mirrors.length()) {

                var mirror = value(mirrors.getJSONObject(i))
                if (matches(mirror) && (mirror.optString("phase") == "OPEN")) {
                    throw fail("MIRROR_REQUIRED")
                }

            }
            var removed = HashSet<String>()
            store.transaction(LAM@ {
                var state = state()
                if (!((state.getString("mode") == "ACTIVE"))) {
                    throw fail("RESTORE_REVIEW_REQUIRED")
                }
                for (type in arrayOf("history:", "mirror_entry:", "candidate:")) {
                    var entries = rows(type)
                    for (i in 0 until entries.length()) {

                        var entry = entries.getJSONObject(i)
                        var row = value(entry)
                        if (!((key == row.optString("key")))) {
                            continue
                        }
                        if ((type == "candidate:") && !(row.optBoolean("archived"))) {
                            continue
                        }
                        if ((type == "mirror_entry:")) {
                            var session = store.get(PREFIX + "mirror:" + row.optString("mirror_id"), "")
                            if (!(session.isEmpty()) && (JSONObject(session).optString("phase") == "OPEN")) {
                                throw fail("MIRROR_REQUIRED")
                            }
                        }
                        if (row.has("record_id") && !(row.isNull("record_id"))) {
                            removed.add(row.getString("record_id"))
                        }
                        if (row.has("before") && !(row.isNull("before"))) {
                            removed.add(row.getString("before"))
                        }
                        store.remove(entry.getString("key"))

                    }
                }
                var mirrorId = hash(JSONObject().put("cleanup_request", request).put("context", bound))
                var mirror = copy(bound).put("formatVersion", 1).put("mirror_id", mirrorId).put("source", "active").put("phase", "COMPLETE")
                store.put(PREFIX + "mirror:" + mirrorId, mirror.toString())
                state.put("active_mirror", mirrorId)
                state.remove("activation_selection")
                saveState(state)
                var selections = rows("selection:")
                for (i in 0 until selections.length()) {

                    var entry = selections.getJSONObject(i)
                    var selected = value(entry).getJSONArray("selected")
                    for (j in 0 until selected.length()) {

                        if ((key == selected.getJSONObject(j).optString("key"))) {
                            store.remove(entry.getString("key"))
                            break
                        }

                    }

                }
                var keep = HashSet<String>()
                for (type in arrayOf("active:", "candidate:", "mirror_entry:", "history:")) {
                    var entries = rows(type)
                    for (i in 0 until entries.length()) {

                        var row = value(entries.getJSONObject(i))
                        if (row.has("record_id") && !(row.isNull("record_id"))) {
                            keep.add(row.getString("record_id"))
                        }
                        if (row.has("before") && !(row.isNull("before"))) {
                            keep.add(row.getString("before"))
                        }

                    }
                }
                var selectionsLeft = rows("selection:")
                for (i in 0 until selectionsLeft.length()) {

                    var selected = value(selectionsLeft.getJSONObject(i)).getJSONArray("selected")
                    for (j in 0 until selected.length()) {

                        keep.add(selected.getJSONObject(j).getString("record_id"))

                    }

                }
                var live = pointer(key)
                for (id in removed) {
                    if (!((id == hash(JSONObject.NULL))) && (live == null || !((id == live.optString("record_id"))))) {
                        store.put(PREFIX + "retired:" + hash(key) + ":" + id, copy(bound).put("formatVersion", 1).put("key", key).put("record_hash", id).toString())
                    }
                    if (!(keep.contains(id))) {
                        store.remove(PREFIX + "record:" + id)
                    }
                }
                return@LAM JSONObject().put("metadata_compacted", true)
            })
            if (!(store.fileDeletionAllowed())) {
                throw fail("WRITE_PENDING")
            }
            var keepBlobs = HashSet<String>()
            var remaining = rows("record:")
            for (i in 0 until remaining.length()) {

                var graph = value(remaining.getJSONObject(i)).get("graph")
                if (graph is JSONObject) {
                    var refs = BrowserMediaFiles.references((graph as JSONObject))
                    for (j in 0 until refs.length()) {

                        keepBlobs.add(refs.getJSONObject(j).getString("sha256"))

                    }
                }

            }
            BrowserMediaFiles.purgeUnreferenced(media, keepBlobs)
            store.transaction(LAM@ {
                var completed = intent(request)
                completed.put("cleanup_complete", true)
                store.put(intentKey(request), completed.toString())
                return@LAM completed
            })
            return JSONObject().put("cleanup_complete", true)
        }
    }
    @Throws(Exception::class) fun beginMirror(request: String?, source: String): JSONObject {

        synchronized(FILE_CAPTURE_LOCK) {
            return beginMirrorLocked(request, source)
        }
    }
    private @Throws(Exception::class) fun beginMirrorLocked(request: String?, source: String): JSONObject {

        source(source)
        return mutate("begin_mirror", request, JSONObject().put("source", source), LAM@ {
            var state = state()
            if ((source == "active") && (state.getString("mode") == "ACTIVE")) {
                return@LAM status().put("already_complete", true).put("mirror_id", state.optString("active_mirror"))
            }
            if ((source == "active") && !((state.getString("mode") == "MIRROR_REQUIRED")) || (source == "device") && !((state.getString("mode") == "RESTORE_REVIEW")) && !(state.optBoolean("review_deferred"))) {
                throw fail("MIRROR_NOT_ALLOWED")
            }
            if ((source == "device") && state.optBoolean("device_mirror_complete")) {
                return@LAM status().put("already_complete", true).put("mirror_id", state.optString("device_mirror"))
            }
            var field = source + "_mirror"
            var id = state.optString(field)
            if (id.isEmpty()) {
                id = hash(JSONObject().put("request", request).put("context", bound).put("source", source))
                var session = copy(bound).put("formatVersion", 1).put("mirror_id", id).put("source", source).put("phase", "OPEN")
                store.put(PREFIX + "mirror:" + id, session.toString())
                state.put(field, id)
                saveState(state)
            }
            return@LAM status().put("already_complete", false).put("mirror_id", id)
        })
    }
    private @Throws(Exception::class) fun mirror(source: String): JSONObject {

        var state = state()
        var id = state.optString(source + "_mirror")
        var raw = store.get(PREFIX + "mirror:" + id, "")
        if (id.isEmpty() || raw.isEmpty()) {
            throw fail("MIRROR_REQUIRED")
        }
        var session = JSONObject(raw)
        version(session)
        if (!(matches(session)) || !((source == session.optString("source"))) || !((session.optString("phase") == "OPEN"))) {
            throw fail("MIRROR_CLOSED")
        }
        return session
    }
    @Throws(Exception::class) fun writeMirrorRecord(request: String?, source: String, key: String?, graph: JSONObject?): JSONObject {

        source(source)
        val key = key(key)
        if (graph == null) {
            throw fail("GRAPH_INVALID")
        }
        return mutate("mirror_record", request, JSONObject().put("source", source).put("key", key).put("graph_hash", hash(graph)), LAM@ {
            var session = mirror(source)
            var graphId = hash(graph)
            if (!(store.get(PREFIX + "retired:" + hash(key) + ":" + graphId, "").isEmpty())) {
                return@LAM JSONObject().put("key", key).put("record_hash", graphId).put("skipped_retired", true)
            }
            var id = storeGraph(graph)
            var pointer = copy(bound).put("formatVersion", 1).put("source", source).put("mirror_id", session.getString("mirror_id")).put("key", key).put("record_id", id)
            store.put(PREFIX + "mirror_entry:" + session.getString("mirror_id") + ":" + hash(key) + ((if ((source == "device")) ":" + id else "")), pointer.toString())
            return@LAM JSONObject().put("key", key).put("record_id", id).put("record_hash", id).put("local_state", "local_saved")
        })
    }
    @Throws(Exception::class) fun completeMirror(request: String?, source: String, keys: JSONArray): JSONObject {

        source(source)
        return mutate("complete_mirror", request, JSONObject().put("source", source).put("keys", keys), LAM@ {
            var state = state()
            var session = mirror(source)
            var expected = TreeSet<String>()
            for (i in 0 until keys.length()) {

                if (!(expected.add(key(keys.getString(i))))) {
                    throw fail("DUPLICATE_KEY")
                }

            }
            var entries = rows("mirror_entry:" + session.getString("mirror_id") + ":")
            var actual = TreeSet<String>()
            for (i in 0 until entries.length()) {

                var pointer = value(entries.getJSONObject(i))
                actual.add(pointer.getString("key"))
                verifyRecord(pointer.getString("record_id"))

            }
            if (!((actual == expected))) {
                throw fail("MIRROR_INCOMPLETE")
            }
            if ((source == "active")) {
                if (!((state.getString("mode") == "MIRROR_REQUIRED")) || rows("active:").length() != 0) {
                    throw fail("ACTIVE_CONFLICT")
                }
                for (i in 0 until entries.length()) {

                    var pointer = value(entries.getJSONObject(i))
                    store.put(PREFIX + "active:" + hash(pointer.getString("key")), copy(bound).put("formatVersion", 1).put("key", pointer.getString("key")).put("record_id", pointer.getString("record_id")).toString())

                }
                state.put("mode", "ACTIVE").put("mirror_complete", true)
            } else {
                if (!((state.getString("mode") == "RESTORE_REVIEW")) && !(state.optBoolean("review_deferred"))) {
                    throw fail("MIRROR_NOT_ALLOWED")
                }
                for (i in 0 until entries.length()) {

                    candidate(state, source, session.getString("mirror_id"), value(entries.getJSONObject(i)))

                }
                state.put("device_mirror_complete", true)
            }
            session.put("phase", "COMPLETE")
            store.put(PREFIX + "mirror:" + session.getString("mirror_id"), session.toString())
            saveState(state)
            return@LAM status().put("mirror_id", session.getString("mirror_id")).put("record_count", actual.size).put("already_complete", false)
        })
    }
    private @Throws(Exception::class) fun candidate(state: JSONObject, source: String, sourceId: String, pointer: JSONObject): Unit {

        var value = copy(bound).put("formatVersion", 1).put("source", source).put("source_id", sourceId).put("key", pointer.getString("key")).put("record_id", pointer.getString("record_id")).put("archived", false)
        store.put(PREFIX + "candidate:" + sourceId + ":" + hash(pointer.getString("key") + ":" + pointer.getString("record_id")), value.toString())
    }
    private fun currentCandidate(candidate: JSONObject, state: JSONObject): Boolean {

        return !(candidate.optBoolean("archived")) && matches(candidate) && ((candidate.optString("source") == "backup") && (candidate.optString("source_id") == state.optString("backup_source")) || (candidate.optString("source") == "device") && (candidate.optString("source_id") == state.optString("device_mirror")))
    }
    @Throws(Exception::class) fun reviewCandidates(): JSONObject {

        var state = state()
        var candidates = JSONArray()
        var all = rows("candidate:")
        for (i in 0 until all.length()) {

            var candidate = value(all.getJSONObject(i))
            if (!(currentCandidate(candidate, state))) {
                continue
            }
            var projected = JSONObject().put("source", candidate.getString("source")).put("key", candidate.getString("key")).put("record_id", candidate.getString("record_id")).put("record_hash", candidate.getString("record_id")).put("label", (if ((candidate.getString("key") == "selected")) "Gespeichertes Bild" else "Kundenfoto-Entwurf"))
            try {

                var row = record(candidate.getString("record_id"))
                var graph = row.get("graph")
                var refs = (if (graph is JSONObject) BrowserMediaFiles.references((graph as JSONObject)) else JSONArray())
                var bytes: Long = 0
                for (j in 0 until refs.length()) {

                    bytes = Math.addExact(bytes, refs.getJSONObject(j).getLong("byte_size"))

                }
                projected.put("blob_count", refs.length()).put("byte_size", bytes)

            } catch (optional: Exception) {
                projected.put("blob_count", 0).put("byte_size", 0).put("error_code", "BROWSER_MEDIA_RECORD_REVIEW_REQUIRED")
            }
            candidates.put(projected)

        }
        var result = copy(bound).put("formatVersion", 1).put("required", (state.getString("mode") == "RESTORE_REVIEW") || state.optBoolean("review_deferred")).put("mirror_complete", (if (state.optBoolean("review_deferred")) state.optBoolean("device_mirror_complete") else complete(state))).put("candidates", candidates)
        return result.put("record_hash", hash(JSONObject().put("state", state).put("candidates", candidates)))
    }
    @Throws(Exception::class) fun prepareRestoreSelection(request: String?, expected: String?, selected: JSONArray): JSONObject {

        return mutate("prepare_selection", request, JSONObject().put("expected", expected).put("selected", selected), LAM@ {
            var state = state()
            var review = reviewCandidates()
            if (!((state.getString("mode") == "RESTORE_REVIEW")) && !(state.optBoolean("review_deferred")) || !(complete(state)) || state.optBoolean("review_deferred") && !(state.optBoolean("device_mirror_complete"))) {
                throw fail("RESTORE_MIRROR_REQUIRED")
            }
            if (expected == null || !((expected == review.getString("record_hash")))) {
                throw fail("WRITE_CONFLICT")
            }
            var keys = TreeSet<String>()
            var chosen = JSONArray()
            for (i in 0 until selected.length()) {

                var item = selected.getJSONObject(i)
                var key = key(item.getString("key"))
                if (!(keys.add(key))) {
                    throw fail("DUPLICATE_KEY")
                }
                var found: JSONObject? = null
                var options = review.getJSONArray("candidates")
                for (j in 0 until options.length()) {

                    var candidate = options.getJSONObject(j)
                    if ((key == candidate.getString("key")) && (item.optString("source") == candidate.getString("source")) && (item.optString("record_id") == candidate.getString("record_id"))) {
                        found = candidate
                    }

                }
                if (found == null || item.has("record_hash") && !((item.optString("record_hash") == found.getString("record_hash")))) {
                    throw fail("SELECTION_INVALID")
                }
                verifyRecord(found.getString("record_id"))
                chosen.put(JSONObject().put("source", found.getString("source")).put("key", key).put("record_id", found.getString("record_id")))

            }
            var selection = copy(bound).put("formatVersion", 1).put("review_hash", expected).put("selected", chosen).put("request_id", request)
            var selectionHash = hash(selection)
            store.put(PREFIX + "selection:" + selectionHash, selection.toString())
            return@LAM JSONObject().put("selection_hash", selectionHash).put("selected_count", chosen.length())
        })
    }
    @Throws(Exception::class) fun activateSelection(request: String?, selectionHash: String?): JSONObject {

        return mutate("activate_selection", request, JSONObject().put("selection_hash", selectionHash), LAM@ {
            var state = state()
            if (selectionHash == null || !(selectionHash.matches(Regex("[0-9a-f]{64}")))) {
                throw fail("SELECTION_INVALID")
            }
            if ((state.getString("mode") == "ACTIVE") && (selectionHash == state.optString("activation_selection"))) {
                return@LAM status().put("already_active", true)
            }
            var raw = store.get(PREFIX + "selection:" + selectionHash, "")
            if (raw.isEmpty()) {
                throw fail("SELECTION_REQUIRED")
            }
            var selection = JSONObject(raw)
            version(selection)
            if (!(matches(selection)) || !((hash(selection) == selectionHash)) || !((state.getString("mode") == "RESTORE_REVIEW")) && !(state.optBoolean("review_deferred")) || !(complete(state)) || !((selection.getString("review_hash") == reviewCandidates().getString("record_hash")))) {
                throw fail("WRITE_CONFLICT")
            }
            if (rows("active:").length() != 0 && !(state.optBoolean("review_deferred"))) {
                throw fail("ACTIVE_CONFLICT")
            }
            var chosen = selection.getJSONArray("selected")
            var active = JSONArray()
            for (i in 0 until chosen.length()) {

                var item = chosen.getJSONObject(i)
                if (pointer(item.getString("key")) != null) {
                    throw fail("ACTIVE_CONFLICT")
                }
                verifyRecord(item.getString("record_id"))
                store.put(PREFIX + "active:" + hash(item.getString("key")), copy(bound).put("formatVersion", 1).put("key", item.getString("key")).put("record_id", item.getString("record_id")).put("restored_request_review_required", true).toString())
                active.put(item.getString("key"))

            }
            var candidates = rows("candidate:")
            for (i in 0 until candidates.length()) {

                var entry = candidates.getJSONObject(i)
                var candidate = value(entry)
                if (currentCandidate(candidate, state)) {
                    store.put(entry.getString("key"), candidate.put("archived", true).put("selection_hash", selectionHash).toString())
                }

            }
            state.put("mode", "ACTIVE").put("review_deferred", false).put("mirror_complete", true).put("activation_selection", selectionHash)
            saveState(state)
            return@LAM status().put("active_keys", active).put("already_active", false)
        })
    }
    @Throws(Exception::class) fun verifyPreparedRestoreSelection(request: String?, expected: String?, selected: JSONArray, selectionHash: String?): JSONObject {

        val request = request(request)
        var state = state()
        if (selectionHash == null || !(selectionHash.matches(Regex("[0-9a-f]{64}")))) {
            throw fail("SELECTION_INVALID")
        }
        var raw = store.get(PREFIX + "selection:" + selectionHash, "")
        if (raw.isEmpty()) {
            throw fail("SELECTION_REQUIRED")
        }
        var selection = JSONObject(raw)
        version(selection)
        if (!(matches(selection)) || !((hash(selection) == selectionHash)) || !((request == selection.optString("request_id"))) || !(Objects.equals(expected, selection.optString("review_hash")))) {
            throw fail("WRITE_CONFLICT")
        }
        var normalized = JSONArray()
        var seen = HashSet<String>()
        for (i in 0 until selected.length()) {

            var item = selected.getJSONObject(i)
            var key = key(item.getString("key"))
            var id = item.getString("record_id")
            if (!(seen.add(key)) || item.has("record_hash") && !((id == item.optString("record_hash")))) {
                throw fail("SELECTION_INVALID")
            }
            normalized.put(JSONObject().put("source", item.getString("source")).put("key", key).put("record_id", id))
            verifyRecord(id)

        }
        if (!((hash(normalized) == hash(selection.getJSONArray("selected"))))) {
            throw fail("WRITE_CONFLICT")
        }
        if ((state.optString("mode") == "RESTORE_REVIEW")) {
            if (!(complete(state)) || !(Objects.equals(expected, reviewCandidates().getString("record_hash")))) {
                throw fail("WRITE_CONFLICT")
            }
        } else {
            if ((state.optString("mode") == "ACTIVE") && (selectionHash == state.optString("activation_selection"))) {
                var active = rows("active:")
                if (active.length() != normalized.length()) {
                    throw fail("WRITE_CONFLICT")
                }
                for (i in 0 until normalized.length()) {

                    var item = normalized.getJSONObject(i)
                    var pointer = pointer(item.getString("key"))
                    if (pointer == null || !((item.getString("record_id") == pointer.optString("record_id")))) {
                        throw fail("WRITE_CONFLICT")
                    }

                }
            } else {
                throw fail("WRITE_CONFLICT")
            }
        }
        return JSONObject().put("valid", true).put("selection_hash", selectionHash).put("selected_count", normalized.length())
    }
    @Throws(Exception::class) fun assertReadableReference(recordId: String, ref: JSONObject): Unit {

        var state = state()
        if (!((state.getString("mode") == "ACTIVE")) || !(complete(state))) {
            throw fail("RESTORE_REVIEW_REQUIRED")
        }
        BrowserMediaFiles.validateReference(ref)
        var active: Boolean = false
        var pointers = rows("active:")
        for (i in 0 until pointers.length()) {

            var pointer = value(pointers.getJSONObject(i))
            if (matches(pointer) && (recordId == pointer.optString("record_id"))) {
                active = true
            }

        }
        if (!(active)) {
            throw fail("REFERENCE_NOT_ACTIVE")
        }
        var graph = record(recordId).get("graph")
        var refs = (if (graph is JSONObject) BrowserMediaFiles.references((graph as JSONObject)) else JSONArray())
        for (i in 0 until refs.length()) {

            if ((hash(ref) == hash(refs.getJSONObject(i)))) {
                return
            }

        }
        throw fail("REFERENCE_NOT_ACTIVE")
    }
    @Throws(Exception::class) fun installRestored(request: String?): Unit {

        val request = request(request)
        store.transaction(LAM@ {
            assertContext()
            var source = hash(JSONObject().put("request", request).put("context", bound))
            var raw = store.get(PREFIX + "state", "")
            if (!(raw.isEmpty())) {
                var prior = JSONObject(raw)
                version(prior)
                if (matches(prior) && (request == prior.optString("restore_request"))) {
                    return@LAM prior
                }
            }
            var state = fresh().put("mode", "RESTORE_REVIEW").put("restore_request", request).put("backup_source", source)
            var active = rows("active:")
            var candidates = rows("candidate:")
            for (i in 0 until active.length()) {

                var entry = active.getJSONObject(i)
                var pointer = value(entry)
                record(pointer.getString("record_id"))
                candidate(state, "backup", source, pointer)
                store.remove(entry.getString("key"))

            }
            for (i in 0 until candidates.length()) {

                var entry = candidates.getJSONObject(i)
                var pointer = value(entry)
                if (!(pointer.optBoolean("archived"))) {
                    record(pointer.getString("record_id"))
                    candidate(state, "backup", source, pointer)
                    store.put(entry.getString("key"), pointer.put("archived", true).put("carried_to", source).toString())
                }

            }
            saveState(state)
            return@LAM state
        })
    }
    private @Throws(Exception::class) fun completionProof(state: JSONObject): Unit {

        var id = (if ((state.optString("mode") == "ACTIVE")) state.optString("active_mirror") else state.optString("device_mirror"))
        if ((state.optString("mode") == "ACTIVE") && !(state.optString("activation_selection").isEmpty())) {
            var raw = store.get(PREFIX + "selection:" + state.getString("activation_selection"), "")
            if (raw.isEmpty()) {
                throw fail("SELECTION_REQUIRED")
            }
            var selection = JSONObject(raw)
            version(selection)
            if (!(matches(selection)) || !((hash(selection) == state.getString("activation_selection")))) {
                throw fail("SELECTION_INVALID")
            }
            var selected = selection.getJSONArray("selected")
            for (i in 0 until selected.length()) {

                record(selected.getJSONObject(i).getString("record_id"))

            }
            return
        }
        var raw = store.get(PREFIX + "mirror:" + id, "")
        if (id.isEmpty() || raw.isEmpty()) {
            throw fail("MIRROR_REQUIRED")
        }
        var session = JSONObject(raw)
        version(session)
        if (!(matches(session)) || !((session.optString("phase") == "COMPLETE")) || !((session.optString("source") == (if ((state.optString("mode") == "ACTIVE")) "active" else "device")))) {
            throw fail("MIRROR_REQUIRED")
        }
    }
    @Throws(Exception::class) fun inspectCaptured(): JSONObject {

        var state = state()
        if (!(complete(state))) {
            throw fail("MIRROR_REQUIRED")
        }
        if (pendingWrites() > 0) {
            throw fail("WRITE_PENDING")
        }
        completionProof(state)
        var records = rows("record:")
        var unique = TreeMap<String, JSONObject>()
        var recordIds = TreeSet<String>()
        var references: Long = 0
        var bytes: Long = 0
        for (i in 0 until records.length()) {

            var entry = records.getJSONObject(i)
            var row = value(entry)
            var id = row.getString("record_id")
            if (!((entry.getString("key") == PREFIX + "record:" + id)) || !(recordIds.add(id))) {
                throw fail("RECORD_INVALID")
            }
            var graph = record(id).get("graph")
            var refs = (if (graph is JSONObject) BrowserMediaFiles.references((graph as JSONObject)) else JSONArray())
            references += refs.length()
            for (j in 0 until refs.length()) {

                var ref = refs.getJSONObject(j)
                BrowserMediaFiles.verifyReference(media!!, ref)
                var old = unique.put(ref.getString("sha256"), ref)
                if (old != null && old.getLong("byte_size") != ref.getLong("byte_size")) {
                    throw fail("REFERENCE_INVALID")
                }

            }

        }
        for (ref in unique.values) {
            bytes = Math.addExact(bytes, ref.getLong("byte_size"))
        }
        var entries = rows("")
        for (i in 0 until entries.length()) {

            var entry = entries.getJSONObject(i)
            var row = value(entry)
            var key = entry.getString("key")
            if (!(((key == PREFIX + "state") || Arrays.asList("record:", "active:", "history:", "request:", "mirror:", "mirror_entry:", "candidate:", "selection:", "write:", "retired:").stream().anyMatch({ part -> key.startsWith(PREFIX + part) })))) {
                throw fail("VERSION_UNSUPPORTED")
            }
            if (key.startsWith(PREFIX + "retired:")) {
                if (!((key == PREFIX + "retired:" + hash(key(row.getString("key"))) + ":" + row.getString("record_hash"))) || !(row.getString("record_hash").matches(Regex("[0-9a-f]{64}")))) {
                    throw fail("RECORD_INVALID")
                }
            }
            if (key.startsWith(PREFIX + "active:")) {
                if (!((state.optString("mode") == "ACTIVE")) || !(matches(row)) || !((key == PREFIX + "active:" + hash(key(row.getString("key")))))) {
                    throw fail("CONTEXT_CHANGED")
                }
            }
            if (key.startsWith(PREFIX + "mirror:") && (row.optString("phase") == "OPEN") && matches(row)) {
                throw fail("MIRROR_REQUIRED")
            }
            if (key.startsWith(PREFIX + "active:") || key.startsWith(PREFIX + "candidate:") || key.startsWith(PREFIX + "mirror_entry:") || key.startsWith(PREFIX + "history:")) {
                key(row.getString("key"))
                record(row.getString("record_id"))
                if (row.has("before") && !(row.isNull("before"))) {
                    record(row.getString("before"))
                }
            }
            if (key.startsWith(PREFIX + "selection:")) {
                var selected = row.getJSONArray("selected")
                for (j in 0 until selected.length()) {

                    record(selected.getJSONObject(j).getString("record_id"))

                }
            }

        }
        return JSONObject().put("formatVersion", 1).put("mode", state.getString("mode")).put("mirror_complete", true).put("record_count", records.length()).put("reference_count", references).put("blob_count", unique.size).put("byte_size", bytes).put("record_hash", hash(JSONArray(recordIds)))
    }
    companion object {
        @JvmField val FILE_CAPTURE_LOCK: Any = Any()
        const val PREFIX: String = "browser_media:v1:"
        private @JvmStatic fun fail(code: String): IOException {

            return IOException("BROWSER_MEDIA_" + code)
        }
        private @JvmStatic @Throws(Exception::class) fun copy(value: JSONObject): JSONObject {

            return JSONObject(value.toString())
        }
        private @JvmStatic @Throws(Exception::class) fun hash(value: Any?): String {

            return PixelMcpWriteTools.hash(value)
        }
        private @JvmStatic fun stamp(): String {

            return Instant.now().toString()
        }
        private @JvmStatic @Throws(Exception::class) fun identity(raw: JSONObject): JSONObject {

            var installation = raw.optString("installationId")
            var epoch = raw.optString("restoreEpoch")
            if (installation.isEmpty() || epoch.isEmpty() || installation.length > 200 || epoch.length > 200) {
                throw fail("CONTEXT_INVALID")
            }
            return JSONObject().put("installation_id", installation).put("restore_epoch", epoch)
        }
        private @JvmStatic @Throws(Exception::class) fun version(value: JSONObject): Unit {

            var v = value.opt("formatVersion")
            if (!((v is Number)) || !((v.toString() == "1"))) {
                throw fail("VERSION_UNSUPPORTED")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun key(key: String?): String {

            if (key == null || key.length > 500 || !(((key == "selected") || key.startsWith("customer-draft:") && key.length > 15)) || key.matches(Regex("(?s).*[\\p{Cntrl}].*"))) {
                throw fail("KEY_INVALID")
            }
            return key
        }
        private @JvmStatic @Throws(Exception::class) fun request(request: String?): String {

            if (request == null || !(request.matches(Regex("[A-Za-z0-9_.:-]{8,200}")))) {
                throw fail("REQUEST_ID_REQUIRED")
            }
            return request
        }
        private @JvmStatic fun complete(state: JSONObject): Boolean {

            return (if ((state.optString("mode") == "ACTIVE")) state.optBoolean("mirror_complete") else (state.optString("mode") == "RESTORE_REVIEW") && state.optBoolean("device_mirror_complete"))
        }
        private @JvmStatic @Throws(Exception::class) fun value(entry: JSONObject): JSONObject {

            var value = entry.getJSONObject("value")
            version(value)
            return value
        }
        private @JvmStatic @Throws(Exception::class) fun source(source: String): Unit {

            if (!(Arrays.asList("active", "device").contains(source))) {
                throw fail("SOURCE_INVALID")
            }
        }
    }
}
