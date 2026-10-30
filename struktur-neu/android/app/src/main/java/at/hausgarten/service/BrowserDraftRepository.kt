package at.hausgarten.service

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet
import java.util.TreeMap

/** Native compatibility storage for existing browser drafts and restored checkpoints; no business records. */
class BrowserDraftRepository {
    private val store: PixelLocalStore
    private val guard: BackupDeviceGuard
    class Failure : IOException {
        @JvmField val code: String
        constructor(code: String) : super(code) {
            this.code = code
        }
    }
    constructor(context: Context, store: PixelLocalStore) {
        this.store = store
        guard = BackupDeviceGuard(context)
    }
    private @Throws(Exception::class) fun current(): JSONObject {

        var value = guard.current()
        return identity(JSONObject().put("installationId", value.get("installationId")).put("restoreEpoch", value.get("restoreEpoch")))
    }
    private @Throws(Exception::class) fun unchangedContext(expected: JSONObject): Unit {

        if (!(same(expected, current()))) {
            throw fail("EDITOR_STORAGE_CONTEXT_CHANGED")
        }
    }
    @Throws(Exception::class) fun read(raw: String?): JSONObject {

        var input = parse(raw)
        var action = text(input, "action")
        if ((action == "get")) {
            fields(input, "formatVersion", "action", "key")
        } else {
            if ((action == "list")) {
                fields(input, "formatVersion", "action")
            } else {
                throw fail("EDITOR_STORAGE_INVALID")
            }
        }
        var wanted = (if ((action == "get")) key(input) else null)
        var context = current()
        var database = store.getReadableDatabase()
        database.beginTransaction()
        var result: JSONObject
        try {

            unchangedContext(context)
            var held: Boolean = review(database)
            result = envelope(context, held)
            if (wanted != null) {
                head(database)
                storedKeys(database)
                var state = state(wanted, row(database, storageKey(wanted)), context, held)
                val keys = state.keys()
                while (keys.hasNext()) {

                    var key = keys.next()
                    result.put(key, state.get(key))

                }
            } else {
                var states = states(database, context, held)
                result.put("states", states).put("record_hash", digest(context, held, states))
            }
            unchangedContext(context)
            database.setTransactionSuccessful()

        } finally {
            database.endTransaction()
        }
        return result
    }
    @Throws(Exception::class) fun write(raw: String?): JSONObject {

        var input = parse(raw)
        fields(input, "formatVersion", "request_id", "context", "mutations")
        var request = request(input)
        var suppliedContext = input.optJSONObject("context")
        if (suppliedContext == null) {
            throw fail("EDITOR_STORAGE_INVALID")
        }
        var expected = identity(suppliedContext)
        var mutations = input.optJSONArray("mutations")
        if (mutations == null || mutations.length() < 1 || mutations.length() > MAX_MUTATIONS) {
            throw fail("EDITOR_STORAGE_INVALID")
        }
        var changes = TreeMap<String, JSONObject>()
        for (i in 0 until mutations.length()) {

            var change = mutations.optJSONObject(i)
            if (change == null) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            fields(change, "key", "expected_record_hash", "value")
            var key = key(change)
            if (changes.put(key, change) != null || !(text(change, "expected_record_hash").matches(Regex("[0-9a-f]{64}"))) || !(change.has("value"))) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            var value = change.get("value")
            if (value != JSONObject.NULL && !((value is String)) || value is String && ((value as String)).toByteArray(StandardCharsets.UTF_8).size > MAX_VALUE_BYTES) {
                throw fail("EDITOR_STORAGE_INVALID")
            }

        }
        var context = current()
        if (!(same(expected, context))) {
            throw fail("EDITOR_STORAGE_CONTEXT_CHANGED")
        }
        var fingerprint = PixelMcpWriteTools.hash(input)
        var database = store.getWritableDatabase()
        database.beginTransaction()
        var result: JSONObject
        try {

            unchangedContext(context)
            if (review(database)) {
                throw fail("EDITOR_STORAGE_RESTORE_REVIEW_REQUIRED")
            }
            storedKeys(database)
            val head = head(database)
            if (head != null && same(head.getJSONObject("context"), context) && (request == head.getString("last_request_id")) && !((fingerprint == head.getString("last_fingerprint")))) {
                throw fail("EDITOR_STORAGE_REPLAY_MISMATCH")
            }
            var before = TreeMap<String, JSONObject?>()
            var replay: Boolean = true
            for (key in changes.keys) {
                var row = row(database, storageKey(key))
                before.put(key, row)
                var sameContext: Boolean = row != null && same(row.getJSONObject("context"), context)
                var sameRequest: Boolean = sameContext && (request == row!!.getString("last_request_id"))
                if (sameRequest && !((fingerprint == row!!.getString("last_fingerprint")))) {
                    throw fail("EDITOR_STORAGE_REPLAY_MISMATCH")
                }
                if (sameRequest) {
                    var saved = row!!.get("value")
                    var wanted = changes.getValue(key).get("value")
                    if ((if (saved == JSONObject.NULL) wanted != JSONObject.NULL else !((wanted is String)) || !((saved == wanted)))) {
                        throw fail("EDITOR_STORAGE_INVALID")
                    }
                }
                if (!(sameRequest)) {
                    replay = false
                }
            }
            if (!(replay)) {
                for (key in changes.keys) {
                    if (!((changes.getValue(key).getString("expected_record_hash") == state(key, before.get(key), context, false).getString("record_hash")))) {
                        throw fail("EDITOR_STORAGE_CONFLICT")
                    }
                }
                unchangedContext(context)
                for (key in changes.keys) {
                    var old = before.get(key)
                    var next: Long = (if (old == null) 1 else Math.addExact(revision(old), 1))
                    var value = changes.getValue(key).get("value")
                    var changed = JSONObject().put("formatVersion", 1).put("key", key).put("context", context).put("revision", next).put("present", value is String).put("value", value).put("last_request_id", request).put("last_fingerprint", fingerprint)
                    put(database, storageKey(key), changed)
                    before.put(key, changed)
                }
                put(database, HEAD, JSONObject().put("formatVersion", 1).put("context", context).put("last_request_id", request).put("last_fingerprint", fingerprint))
            }
            var changedStates = JSONArray()
            for (key in changes.keys) {
                changedStates.put(state(key, before.get(key), context, false))
            }
            var all = states(database, context, false)
            result = envelope(context, false).put("states", changedStates).put("record_hash", digest(context, false, all)).put("replayed", replay)
            unchangedContext(context)
            database.setTransactionSuccessful()

        } finally {
            database.endTransaction()
        }
        return result
    }
    companion object {
        const val PREFIX: String = "browser_drafts:v1:"
        const val ROW: String = PREFIX + "key:"
        const val HEAD: String = PREFIX + "head"
        private val REVIEW: String = "restored_browser_checkpoint_requires_explicit_review"
        private val MAX_VALUE_BYTES: Int = 16 * 1024 * 1024
        private val MAX_REQUEST_BYTES: Int = 32 * 1024 * 1024
        private val MAX_MUTATIONS: Int = 10000
        @JvmStatic fun selectable(key: String): Boolean {

            return RestoredBrowserReviewRules.selectable(key) || (key == "hausgarten.invoice.issue.action.v1") || (key == "hausgarten.offer.order.action.v1")
        }
        private @JvmStatic fun fail(code: String): Failure {

            return Failure(code)
        }
        private @JvmStatic @Throws(Exception::class) fun fields(value: JSONObject, vararg allowed: String): Unit {

            var names = HashSet<String>(allowed.asList())
            val keys = value.keys()
            while (keys.hasNext()) {

                if (!(names.contains(keys.next()))) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }

            }
        }
        private @JvmStatic @Throws(Exception::class) fun text(value: JSONObject, key: String): String {

            var raw = value.opt(key)
            if (!((raw is String))) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            return (raw as String)
        }
        private @JvmStatic @Throws(Exception::class) fun version(value: JSONObject): Unit {

            var raw = value.opt("formatVersion")
            if (!((raw is Int)) || ((raw).toInt()) != 1) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun identity(value: JSONObject): JSONObject {

            fields(value, "installationId", "restoreEpoch")
            var installation = text(value, "installationId")
            var epoch = text(value, "restoreEpoch")
            if (!(installation.matches(Regex("[0-9a-f-]{36}"))) || !(epoch.matches(Regex("[0-9a-f-]{36}")))) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            return JSONObject().put("installationId", installation).put("restoreEpoch", epoch)
        }
        private @JvmStatic @Throws(Exception::class) fun same(a: JSONObject, b: JSONObject): Boolean {

            return (a.getString("installationId") == b.getString("installationId")) && (a.getString("restoreEpoch") == b.getString("restoreEpoch"))
        }
        private @JvmStatic @Throws(Exception::class) fun key(value: JSONObject): String {

            var key = text(value, "key")
            if (!(selectable(key))) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            return key
        }
        private @JvmStatic @Throws(Exception::class) fun request(value: JSONObject): String {

            var request = text(value, "request_id")
            if (!(request.matches(Regex("[A-Za-z0-9_.:-]{8,200}")))) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            return request
        }
        private @JvmStatic @Throws(Exception::class) fun parse(raw: String?): JSONObject {

            if (raw == null || raw.length > MAX_REQUEST_BYTES || raw.toByteArray(StandardCharsets.UTF_8).size > MAX_REQUEST_BYTES) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            var value: JSONObject
            try {

                value = JSONObject(raw)

            } catch (invalid: Exception) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            version(value)
            return value
        }
        private @JvmStatic @Throws(Exception::class) fun review(database: SQLiteDatabase): Boolean {

            var value = DatabaseBackupSnapshot.ownerValue(database, REVIEW, null)
            if (value == null || (value == "false")) {
                return false
            }
            if ((value == "true")) {
                return true
            }
            throw fail("EDITOR_STORAGE_INVALID")
        }
        private @JvmStatic @Throws(Exception::class) fun storageKey(key: String): String {

            return ROW + PixelMcpWriteTools.hash(key)
        }
        private @JvmStatic @Throws(Exception::class) fun revision(row: JSONObject): Long {

            var value = row.opt("revision")
            if (!((value is Int)) && !((value is Long))) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            var revision: Long = ((value as Number)).toLong()
            if (revision < 1) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
            return revision
        }
        private @JvmStatic @Throws(Exception::class) fun row(database: SQLiteDatabase, storageKey: String): JSONObject? {

            var raw = DatabaseBackupSnapshot.ownerValue(database, storageKey, null)
            if (raw == null) {
                return null
            }
            var row: JSONObject
            try {

                row = JSONObject(raw)
                version(row)
                fields(row, "formatVersion", "key", "context", "revision", "present", "value", "last_request_id", "last_fingerprint")
                var key = key(row)
                if (!((storageKey == storageKey(key)))) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                identity(row.getJSONObject("context"))
                revision(row)
                if (!((row.opt("present") is Boolean)) || !(row.has("value")) || !((row.get("value") is String)) && row.get("value") != JSONObject.NULL) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                if (row.getBoolean("present") != (row.get("value") is String)) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                if (row.get("value") is String && row.getString("value").toByteArray(StandardCharsets.UTF_8).size > MAX_VALUE_BYTES) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                request(JSONObject().put("request_id", row.get("last_request_id")))
                if (!(text(row, "last_fingerprint").matches(Regex("[0-9a-f]{64}")))) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                return row

            } catch (invalid: Failure) {
                throw invalid
            } catch (invalid: Exception) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun head(database: SQLiteDatabase): JSONObject? {

            var raw = DatabaseBackupSnapshot.ownerValue(database, HEAD, null)
            if (raw == null) {
                return null
            }
            try {

                var value = JSONObject(raw)
                version(value)
                fields(value, "formatVersion", "context", "last_request_id", "last_fingerprint")
                identity(value.getJSONObject("context"))
                request(JSONObject().put("request_id", value.get("last_request_id")))
                if (!(text(value, "last_fingerprint").matches(Regex("[0-9a-f]{64}")))) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                return value

            } catch (invalid: Failure) {
                throw invalid
            } catch (invalid: Exception) {
                throw fail("EDITOR_STORAGE_INVALID")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun storedKeys(database: SQLiteDatabase): MutableList<String> {

            var result = ArrayList<String>()
            database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? ORDER BY key", arrayOf("browser_drafts:", "browser_drafts:\uffff")).use { rows ->

                while (rows.moveToNext()) {
                    var key = rows.getString(0)
                    if ((key == HEAD)) {
                        continue
                    }
                    if (!(key.matches(Regex("browser_drafts:v1:key:[0-9a-f]{64}")))) {
                        throw fail("EDITOR_STORAGE_INVALID")
                    }
                    result.add(key)
                }

            }
            return result
        }
        private @JvmStatic @Throws(Exception::class) fun state(key: String, row: JSONObject?, context: JSONObject, held: Boolean): JSONObject {

            var initialized: Boolean = !(held) && row != null && same(row.getJSONObject("context"), context)
            var revision: Long = (if (initialized) revision(row!!) else 0)
            var present: Boolean = initialized && row!!.getBoolean("present")
            var value = (if (present) row!!.get("value") else JSONObject.NULL)
            var state = JSONObject().put("key", key).put("initialized", initialized).put("present", present).put("value", value).put("revision", revision)
            state.put("record_hash", PixelMcpWriteTools.hash(JSONObject(state.toString()).put("formatVersion", 1).put("context", context)))
            return state
        }
        private @JvmStatic @Throws(Exception::class) fun states(database: SQLiteDatabase, context: JSONObject, held: Boolean): JSONArray {

            var values = TreeMap<String, JSONObject>()
            head(database)
            for (storageKey in storedKeys(database)) {
                var row = row(database, storageKey)
                if (row == null) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                if (!(held) && same(row.getJSONObject("context"), context)) {
                    values.put(row.getString("key"), state(row.getString("key"), row, context, false))
                }
            }
            var result = JSONArray()
            for (state in values.values) {
                result.put(state)
            }
            return result
        }
        private @JvmStatic @Throws(Exception::class) fun digest(context: JSONObject, held: Boolean, states: JSONArray): String {

            var hashes = JSONArray()
            for (i in 0 until states.length()) {

                var state = states.getJSONObject(i)
                hashes.put(JSONObject().put("key", state.getString("key")).put("record_hash", state.getString("record_hash")))

            }
            return PixelMcpWriteTools.hash(JSONObject().put("formatVersion", 1).put("context", context).put("review_required", held).put("states", hashes))
        }
        @JvmStatic @Throws(Exception::class) fun snapshotDigest(database: SQLiteDatabase, identity: JSONObject): String {

            var context = identity(JSONObject().put("installationId", identity.get("installationId")).put("restoreEpoch", identity.get("restoreEpoch")))
            var held: Boolean = review(database)
            return digest(context, held, states(database, context, held))
        }
        @JvmStatic @Throws(IOException::class) fun validateCaptured(database: SQLiteDatabase, sourceIdentity: JSONObject, browser: JSONObject?): Unit {

            try {

                var declared: Boolean = browser != null && browser.has("editor_state")
                var hasNamespace: Boolean
                database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? LIMIT 1", arrayOf("browser_drafts:", "browser_drafts:\uffff")).use { keys ->

                    hasNamespace = keys.moveToFirst()

                }
                if (!(hasNamespace) && !(declared)) {
                    return
                }
                var context = identity(JSONObject().put("installationId", sourceIdentity.get("installationId")).put("restoreEpoch", sourceIdentity.get("restoreEpoch")))
                var held: Boolean = review(database)
                var current = states(database, context, false)
                var descriptor = browser!!.optJSONObject("editor_state")
                var storage = browser.optJSONObject("storage")
                if (descriptor == null || storage == null) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                version(descriptor)
                fields(descriptor, "formatVersion", "context", "review_required", "record_hash")
                if (!((descriptor.opt("review_required") is Boolean)) || descriptor.getBoolean("review_required") != held || !(same(identity(descriptor.getJSONObject("context")), context)) || !((text(descriptor, "record_hash") == digest(context, held, (if (held) JSONArray() else current))))) {
                    throw fail("EDITOR_STORAGE_INVALID")
                }
                var backed = HashSet<String>()
                for (i in 0 until current.length()) {

                    var state = current.getJSONObject(i)
                    var key = state.getString("key")
                    backed.add(key)
                    if (state.getBoolean("present")) {
                        if (!((storage.opt(key) is String)) || !((state.getString("value") == storage.getString(key)))) {
                            throw fail("EDITOR_STORAGE_INVALID")
                        }
                    } else {
                        if (storage.has(key)) {
                            throw fail("EDITOR_STORAGE_INVALID")
                        }
                    }

                }
                if (!(held)) {
                    val keys = storage.keys()
                    while (keys.hasNext()) {

                        var key = keys.next()
                        if (selectable(key) && !(backed.contains(key))) {
                            throw fail("EDITOR_STORAGE_INVALID")
                        }

                    }
                }

            } catch (invalid: Exception) {
                throw IOException("BACKUP_BROWSER_EDITOR_STATE_MISMATCH")
            }
        }
        private @JvmStatic @Throws(Exception::class) fun envelope(context: JSONObject, held: Boolean): JSONObject {

            return JSONObject().put("formatVersion", 1).put("context", context).put("review_required", held)
        }
        private @JvmStatic @Throws(Exception::class) fun put(database: SQLiteDatabase, key: String, value: JSONObject): Unit {

            var content = ContentValues()
            content.put("key", key)
            content.put("value", value.toString())
            if (database.insertWithOnConflict("owner_state", null, content, SQLiteDatabase.CONFLICT_REPLACE) < 0) {
                throw fail("EDITOR_STORAGE_SAVE_FAILED")
            }
        }
        @JvmStatic fun responseFailure(failure: Exception): String {

            var code = (if (failure is Failure) ((failure as Failure)).code else "EDITOR_STORAGE_SAVE_FAILED")
            var message: String
            when (code) {
                "EDITOR_STORAGE_INVALID" -> {
                    message = "Der Entwurfsauftrag ist nicht lesbar. Eingaben erhalten und die Ansicht erneut öffnen."
                }
                "EDITOR_STORAGE_CONFLICT" -> {
                    message = "Der Entwurf wurde inzwischen geändert. Den aktuellen Stand erneut lesen; keine Änderungen überschreiben."
                }
                "EDITOR_STORAGE_CONTEXT_CHANGED" -> {
                    message = "Der Gerätestand wurde gewechselt. Die aktuelle Ansicht erneut öffnen; ältere Entwürfe bleiben getrennt."
                }
                "EDITOR_STORAGE_REPLAY_MISMATCH" -> {
                    message = "Diese Aktionskennung gehört zu anderen Entwurfsangaben. Die ursprüngliche Aktion unverändert prüfen."
                }
                "EDITOR_STORAGE_RESTORE_REVIEW_REQUIRED" -> {
                    message = "Erhaltene Entwürfe nach der Wiederherstellung zuerst ausdrücklich prüfen."
                }
                else -> {
                    code = "EDITOR_STORAGE_SAVE_FAILED"
                    message = "Der Entwurf konnte noch nicht als dauerhaft gespeichert bestätigt werden. Dieselbe Aktion erneut prüfen."
                }
            }
            try {

                return JSONObject().put("ok", false).put("error", JSONObject().put("code", code).put("message", message)).toString()

            } catch (impossible: Exception) {
                return "{\"ok\":false,\"error\":{\"code\":\"EDITOR_STORAGE_SAVE_FAILED\",\"message\":\"Entwurf erneut prüfen.\"}}"
            }
        }
    }
}
