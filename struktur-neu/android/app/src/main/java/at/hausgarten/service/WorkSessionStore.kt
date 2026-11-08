package at.hausgarten.service

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Persistent, event-backed work timer. It creates no business record by itself. */
class WorkSessionStore {
    interface Clock {
        fun wallMillis(): Long
        fun elapsedMillis(): Long
        fun bootMarker(): String
    }
    class DeviceClock : Clock {
        private val context: android.content.Context?
        constructor() {
            this.context = null
        }
        constructor(context: android.content.Context?) {
            this.context = context
        }
        override fun wallMillis(): Long {
            return System.currentTimeMillis()
        }
        override fun elapsedMillis(): Long {
            return SystemClock.elapsedRealtime()
        }
        override fun bootMarker(): String {
            if (((context != null))) {
                var count: Int = android.provider.Settings.Global.getInt(context!!.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1)
                if (((count >= 0))) {
                    return ("boot:" + count)
                }
            }
            return "unverified_boot"
        }
    }
    private val db: SQLiteDatabase
    private val clock: Clock
    constructor(db: SQLiteDatabase) : this(db, DeviceClock()) {
    }
    constructor(db: SQLiteDatabase, context: android.content.Context) : this(db, DeviceClock(context)) {
    }
    constructor(db: SQLiteDatabase, clock: Clock) {
        this.db = db
        this.clock = clock
    }
    @Throws(Exception::class)
    fun start(input: JSONObject): JSONObject {
        db.beginTransaction()
        try {
            var request: String = required(input, "request_id")
            var existing: String = sessionForRequest(request)
            if ((!existing.isEmpty())) {
                db.rawQuery("SELECT event_type,payload FROM work_session_events WHERE request_id=?", arrayOf(request)).use { start ->

                    if (((!start.moveToFirst() || !java.util.Arrays.asList("START", "START_EXISTING").contains(start.getString(0))))) {
                        throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Anfragekennung gehört zu einer anderen Aktion.")
                    }
                    var original: JSONObject = JSONObject(start.getString(1))
                    original.remove("_clock")
                    if ((original.has("_request_input_hash"))) {
                        var saved: String = original.optString("_request_input_hash")
                        if (((!saved.matches(Regex("[0-9a-f]{64}")) || !saved.equals(input.optString("_request_input_hash"))))) {
                            throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Anfragekennung enthält andere Startangaben.")
                        }
                    } else {
                        var comparison: JSONObject = JSONObject(input.toString())
                        comparison.remove("_request_input_hash")
                        if ((!PixelMcpWriteTools.hash(original).equals(PixelMcpWriteTools.hash(comparison)))) {
                            throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Anfragekennung enthält andere Startangaben.")
                        }
                    }
                }
                var replayed: JSONObject = current(existing)
                db.setTransactionSuccessful()
                return replayed
            }
            if (((activeId() != null))) {
                var active: String = activeId()!!
                event(active, "START_EXISTING", request, input)
                db.setTransactionSuccessful()
                return current(active)
            }
            var date: String = input.optString("work_date", LocalDate.now(java.time.ZoneId.of("Europe/Vienna")).toString())
            try {
                LocalDate.parse(date)
            } catch (e: Exception) {
                throw IllegalArgumentException("Bitte ein gültiges Arbeitsdatum auswählen.")
            }
            var wall: Long = clock.wallMillis()
            var elapsed: Long = clock.elapsedMillis()
            var id: String = ("ses_pixel_" + UUID.randomUUID().toString().replace("-", ""))
            var row: ContentValues = ContentValues()
            row.put("id", id)
            row.put("state", RUNNING)
            row.put("customer_id", input.optString("customer_id").trim())
            row.put("order_id", input.optString("order_id").trim())
            row.put("work_date", date)
            row.put("activity", input.optString("activity").trim())
            row.put("started_at_ms", wall)
            row.put("segment_started_wall_ms", wall)
            row.put("segment_started_elapsed_ms", elapsed)
            row.put("boot_marker", clock.bootMarker())
            row.put("worked_ms", 0)
            row.put("updated_at", Instant.ofEpochMilli(wall).toString())
            transaction(lambda0@ {
                db.insertOrThrow("work_sessions", null, row)
                event(id, "START", request, input)
 })
            db.setTransactionSuccessful()
            return current(id)
        } finally {
            db.endTransaction()
        }
    }
    @Throws(Exception::class)
    fun pause(id: String, request: String): JSONObject {
        return transition(id, request, RUNNING, PAUSED, "PAUSE", true)
    }
    @Throws(Exception::class)
    fun discard(id: String, request: String): JSONObject {
        db.beginTransaction()
        try {
            var replay: String = sessionForRequest(request)
            if ((!replay.isEmpty())) {
                if ((!id.equals(replay))) {
                    throw IllegalArgumentException("Anfrage gehört zu einer anderen Sitzung.")
                }
                requireEvent(request, "DISCARD")
                var replayed: JSONObject = current(id)
                db.setTransactionSuccessful()
                return replayed
            }
            var before: JSONObject = current(id)
            var state: String = before.getString("state")
            if ((((!RUNNING.equals(state) && !PAUSED.equals(state)) && !STOPPED.equals(state)))) {
                throw IllegalStateException("Nur eine noch nicht gespeicherte Zeiterfassung kann verworfen werden.")
            }
            var values: ContentValues = ContentValues()
            values.put("state", CANCELLED)
            values.put("worked_ms", before.getLong("worked_ms"))
            values.putNull("segment_started_wall_ms")
            values.putNull("segment_started_elapsed_ms")
            values.put("updated_at", nowIso())
            db.update("work_sessions", values, "id=?", arrayOf(id))
            event(id, "DISCARD", request, JSONObject())
            db.setTransactionSuccessful()
            return current(id)
        } finally {
            db.endTransaction()
        }
    }
    @Throws(Exception::class)
    fun restore(id: String, request: String): JSONObject {
        db.beginTransaction()
        try {
            var replay: String = sessionForRequest(request)
            if ((!replay.isEmpty())) {
                if ((!id.equals(replay))) {
                    throw IllegalArgumentException("Anfrage gehört zu einer anderen Sitzung.")
                }
                requireEvent(request, "RESTORE")
                var replayed: JSONObject = current(id)
                db.setTransactionSuccessful()
                return replayed
            }
            if (((activeId() != null))) {
                throw IllegalStateException("Eine andere Zeiterfassung ist aktiv. Bitte diese zuerst abschließen.")
            }
            if ((!CANCELLED.equals(current(id).getString("state")))) {
                throw IllegalStateException("Diese Zeiterfassung wurde nicht verworfen.")
            }
            var values: ContentValues = ContentValues()
            values.put("state", PAUSED)
            values.put("updated_at", nowIso())
            db.update("work_sessions", values, "id=?", arrayOf(id))
            event(id, "RESTORE", request, JSONObject())
            db.setTransactionSuccessful()
            return current(id)
        } finally {
            db.endTransaction()
        }
    }
    @Throws(Exception::class)
    fun resume(id: String, request: String): JSONObject {
        return transition(id, request, PAUSED, RUNNING, "RESUME", false)
    }
    @Throws(Exception::class)
    fun continueStopped(id: String, request: String): JSONObject {
        return transition(id, request, STOPPED, RUNNING, "CONTINUE", false)
    }
    @Throws(Exception::class)
    fun stop(id: String, request: String): JSONObject {
        var current: JSONObject = current(id)
        var state: String = current.getString("state")
        if ((STOPPED.equals(state))) {
            if ((id.equals(sessionForRequest(request)))) {
                requireEvent(request, "STOP")
                return current
            }
            throw IllegalStateException("Die Arbeit ist bereits gestoppt.")
        }
        if (((!RUNNING.equals(state) && !PAUSED.equals(state)))) {
            throw IllegalStateException("Diese Arbeitssitzung kann nicht gestoppt werden.")
        }
        return transition(id, request, state, STOPPED, "STOP", RUNNING.equals(state))
    }
    @Throws(Exception::class)
    fun finalizeSession(id: String, worklogId: String?, request: String): JSONObject {
        if ((((worklogId == null) || worklogId!!.trim().isEmpty()))) {
            throw IllegalArgumentException("Die Arbeitsaufzeichnung fehlt.")
        }
        var before: JSONObject = current(id)
        if ((FINALIZED.equals(before.getString("state")))) {
            if (((worklogId!!.equals(before.optString("finalized_worklog_id")) && id.equals(sessionForRequest(request))))) {
                requireEvent(request, "FINALIZE")
                return before
            }
            throw IllegalStateException("Diese Arbeitssitzung wurde bereits abgeschlossen.")
        }
        if ((!STOPPED.equals(before.getString("state")))) {
            throw IllegalStateException("Bitte die Arbeit zuerst stoppen und prüfen.")
        }
        var values: ContentValues = ContentValues()
        values.put("state", FINALIZED)
        values.put("finalized_worklog_id", worklogId)
        values.put("updated_at", nowIso())
        transaction(lambda1@ {
            db.update("work_sessions", values, "id=?", arrayOf(id))
            event(id, "FINALIZE", request, JSONObject().put("worklog_id", worklogId))
 })
        return current(id)
    }
    @Throws(Exception::class)
    fun reviewTime(id: String, input: JSONObject): JSONObject {
        db.beginTransaction()
        try {
            var request: String = required(input, "request_id")
            var replay: String = sessionForRequest(request)
            if ((!replay.isEmpty())) {
                if ((!id.equals(replay))) {
                    throw IllegalArgumentException("Anfrage gehört zu einer anderen Sitzung.")
                }
                db.rawQuery("SELECT payload FROM work_session_events WHERE request_id=? AND event_type='TIME_REVIEW'", arrayOf(request)).use { row ->

                    if (((!row.moveToFirst() || !JSONObject(row.getString(0)).getString("input_hash").equals(PixelMcpWriteTools.hash(input))))) {
                        throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Anfragekennung gehört zu anderen Angaben.")
                    }
                }
                return current(id)
            }
            var before: JSONObject = current(id)
            var originalStorage: JSONObject = JSONObject()
            db.rawQuery("SELECT * FROM work_sessions WHERE id=?", arrayOf(id)).use { original ->

                if ((!original.moveToFirst())) {
                    throw IllegalArgumentException("Sitzung fehlt.")
                }
                run {
                    var i: Int = 0
                    while ((i < original.getColumnCount())) {
                        var value: Any = (if (original.isNull(i)) JSONObject.NULL else (if ((original.getType(i) == Cursor.FIELD_TYPE_INTEGER)) original.getLong(i) else (if ((original.getType(i) == Cursor.FIELD_TYPE_FLOAT)) original.getDouble(i) else original.getString(i))))
                        originalStorage.put(original.getColumnName(i), value)
                        i++
                    }
                }
            }
            if ((!before.getString("revision_hash").equals(required(input, "expected_revision_hash")))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Sitzung geändert. Erneut öffnen; Eingaben bleiben erhalten.")
            }
            if ((!java.util.Arrays.asList(RUNNING, PAUSED, STOPPED).contains(before.getString("state")))) {
                throw IllegalStateException("Nur offene Sitzungen können geprüft werden.")
            }
            var minutes: Long = java.math.BigDecimal(input.get("worked_minutes").toString()).longValueExact()
            var pause: Long = java.math.BigDecimal(input.get("break_minutes").toString()).longValueExact()
            var reason: String = required(input, "reason")
            if (((reason.length > 4000))) {
                throw IllegalArgumentException("Korrekturgrund ist zu lang.")
            }
            if ((((((((minutes <= 0) || (minutes > 10080)) || (pause < 0)) || (pause > 10080)) || !input.optBoolean("confirmed")) || ((((minutes + pause) > 1440) && !input.optBoolean("allow_overnight")))))) {
                throw IllegalArgumentException("Nettozeit, Pause und gegebenenfalls Übernachtarbeit ausdrücklich bestätigen.")
            }
            var values: ContentValues = ContentValues()
            values.put("state", STOPPED)
            values.put("worked_ms", Math.multiplyExact(minutes, 60000L))
            values.putNull("segment_started_wall_ms")
            values.putNull("segment_started_elapsed_ms")
            values.put("updated_at", nowIso())
            db.update("work_sessions", values, "id=?", arrayOf(id))
            event(id, "TIME_REVIEW", request, JSONObject().put("input_hash", PixelMcpWriteTools.hash(input)).put("original", before).put("original_storage", originalStorage).put("worked_minutes", minutes).put("break_minutes", pause).put("reason", reason).put("origin", "manual_review").put("no_end_time_inferred", true))
            db.setTransactionSuccessful()
            return current(id)
        } finally {
            db.endTransaction()
        }
    }
    @Throws(Exception::class)
    fun current(): JSONObject {
        var id: String? = activeId()
        return (if ((id == null)) JSONObject().put("state", "IDLE") else current(id))
    }
    @Throws(Exception::class)
    fun current(id: String): JSONObject {
        db.rawQuery("SELECT id,state,customer_id,order_id,work_date,activity,started_at_ms,segment_started_wall_ms,segment_started_elapsed_ms,boot_marker,worked_ms,finalized_worklog_id,updated_at FROM work_sessions WHERE id=?", arrayOf(id)).use { c ->

            if ((!c.moveToFirst())) {
                throw IllegalArgumentException("Arbeitssitzung nicht gefunden.")
            }
            var worked: Long = c.getLong(10)
            var state: String = c.getString(1)
            var revisionEvent: String = ""
            db.rawQuery("SELECT id FROM work_session_events WHERE session_id=? ORDER BY rowid DESC LIMIT 1", arrayOf(id)).use { last ->

                if ((last.moveToFirst())) {
                    revisionEvent = last.getString(0)
                }
            }
            var manuallyReviewed: Boolean = false
            var correctionReason: String = ""
            db.rawQuery("SELECT payload FROM work_session_events WHERE session_id=? AND event_type='TIME_REVIEW' ORDER BY rowid DESC LIMIT 1", arrayOf(id)).use { corrected ->

                if ((corrected.moveToFirst())) {
                    manuallyReviewed = true
                    correctionReason = JSONObject(corrected.getString(0)).optString("reason")
                }
            }
            var review: String = (if (RUNNING.equals(state)) reviewReason(c.getLong(7), c.getLong(8), c.getString(9)) else "")
            if ((RUNNING.equals(state))) {
                worked += runningDelta(c.getLong(7), c.getLong(8), c.getString(9))
            }
            if ((PAUSED.equals(state))) {
                db.rawQuery("SELECT payload FROM work_session_events WHERE session_id=? ORDER BY rowid DESC LIMIT 1", arrayOf(id)).use { last ->

                    if ((last.moveToFirst())) {
                        var mark: JSONObject? = JSONObject(last.getString(0)).optJSONObject("_clock")
                        review = (if ((mark == null)) "LEGACY_PAUSE_REVIEW_REQUIRED" else reviewReason(mark!!.getLong("wall_ms"), mark!!.getLong("elapsed_ms"), mark!!.getString("boot_marker")))
                    }
                }
            }
            var location: String = ""
            var appointment: String = ""
            var property: String = ""
            var visit: String = ""
            var billingCustomer: String = c.getString(2)
            db.rawQuery("SELECT payload FROM work_session_events WHERE session_id=? AND event_type='START' LIMIT 1", arrayOf(id)).use { start ->

                if ((start.moveToFirst())) {
                    var origin: JSONObject = JSONObject(start.getString(0))
                    location = origin.optString("location")
                    appointment = origin.optString("appointment_id")
                    property = origin.optString("property_id")
                    visit = origin.optString("visit_id")
                    billingCustomer = origin.optString("billing_customer_id", c.getString(2))
                }
            }
            var pauseEvents: org.json.JSONArray = org.json.JSONArray()
            db.rawQuery("SELECT event_type,occurred_at,payload FROM work_session_events WHERE session_id=? ORDER BY rowid ASC", arrayOf(id)).use { events ->

                while ((events.moveToNext())) {
                    pauseEvents.put(JSONObject().put("event_type", events.getString(0)).put("occurred_at", events.getString(1)).put("payload", JSONObject(events.getString(2))))
                }
            }
            var pauses: JSONObject = WorkSessionPauseRules.calculate(pauseEvents, state, clock.wallMillis(), clock.elapsedMillis(), clock.bootMarker())
            var pauseMs: Long = pauses.getLong("pause_ms")
            var pauseUnverified: Boolean = pauses.getBoolean("pause_unverified")
            if (((pauseUnverified && review.isEmpty()))) {
                review = "PAUSE_REVIEW_REQUIRED"
            }
            var reviewLatched: Boolean = false
            db.rawQuery("SELECT event_type,payload FROM work_session_events WHERE session_id=? AND event_type IN ('TIME_REVIEW_REQUIRED','TIME_REVIEW') ORDER BY rowid DESC LIMIT 1", arrayOf(id)).use { marker ->

                if (((marker.moveToFirst() && "TIME_REVIEW_REQUIRED".equals(marker.getString(0))))) {
                    review = JSONObject(marker.getString(1)).getString("reason")
                    reviewLatched = true
                }
            }
            if (((!review.isEmpty() && !reviewLatched))) {
                var marker: ContentValues = ContentValues()
                marker.put("id", ("evt_review_" + UUID.randomUUID().toString().replace("-", "")))
                marker.put("session_id", id)
                marker.put("event_type", "TIME_REVIEW_REQUIRED")
                marker.put("request_id", (("" + ("system-review-" + id) + "-") + revisionEvent))
                marker.put("occurred_at", nowIso())
                marker.put("payload", JSONObject().put("reason", review).toString())
                db.insertWithOnConflict("work_session_events", null, marker, SQLiteDatabase.CONFLICT_IGNORE)
                db.rawQuery("SELECT id FROM work_session_events WHERE session_id=? ORDER BY rowid DESC LIMIT 1", arrayOf(id)).use { last ->

                    if ((last.moveToFirst())) {
                        revisionEvent = last.getString(0)
                    }
                }
            }
            if (((!review.isEmpty() && RUNNING.equals(state)))) {
                worked = c.getLong(10)
            }
            var result: JSONObject = JSONObject().put("id", c.getString(0)).put("state", state).put("customer_id", c.getString(2)).put("order_id", c.getString(3)).put("work_date", c.getString(4)).put("activity", c.getString(5)).put("location", location).put("billing_customer_id", billingCustomer).put("started_at_ms", c.getLong(6)).put("worked_ms", worked).put("hours", (Math.round((worked / 36000.0)) / 100.0)).put("break_minutes", Math.round((pauseMs / 60000.0))).put("pause_unverified", pauseUnverified).put("pause_measurement", "persistent_event_boundaries").put("finalized_worklog_id", c.getString(11)).put("updated_at", c.getString(12))
            result.put("revision_hash", PixelMcpWriteTools.hash(JSONObject().put("id", id).put("state", state).put("updated_at", c.getString(12)).put("worked_ms", c.getLong(10)).put("last_event", revisionEvent))).put("time_origin", (if (manuallyReviewed) "manual_review" else "timer")).put("correction_reason", correctionReason).put("timezone", "Europe/Vienna").put("review_required", !review.isEmpty()).put("review_reason", review).put("time_measurement", "persistent_segments_monotonic")
            if ((!appointment.isEmpty())) {
                result.put("appointment_id", appointment)
            }
            if ((!property.isEmpty())) {
                result.put("property_id", property)
            }
            if ((!visit.isEmpty())) {
                result.put("visit_id", visit)
            }
            return result
        }
    }
    @Throws(Exception::class)
    private fun transition(id: String, request: String, expected: String, next: String, event: String, accrue: Boolean): JSONObject {
        db.beginTransaction()
        try {
            var replay: String = sessionForRequest(request)
            if ((!replay.isEmpty())) {
                if ((!replay.equals(id))) {
                    throw IllegalArgumentException("Diese Anfragekennung gehört zu einer anderen Sitzung.")
                }
                requireEvent(request, event)
                var replayed: JSONObject = current(id)
                db.setTransactionSuccessful()
                return replayed
            }
            var before: JSONObject = current(id)
            if ((before.optBoolean("review_required"))) {
                throw IllegalStateException("Sitzung prüfen: Geräte-Neustart, Uhränderung oder unplausible Dauer. Es wurde kein Arbeitsende geschätzt.")
            }
            if ((!expected.equals(before.getString("state")))) {
                throw IllegalStateException("Aktion passt nicht zum aktuellen Sitzungszustand.")
            }
            var wall: Long = clock.wallMillis()
            var elapsed: Long = clock.elapsedMillis()
            var worked: Long = before.getLong("worked_ms")
            if ((accrue)) {
                worked = (storedWorked(id) + runningDeltaFor(id))
            }
            var values: ContentValues = ContentValues()
            values.put("state", next)
            values.put("worked_ms", worked)
            values.put("updated_at", Instant.ofEpochMilli(wall).toString())
            if ((RUNNING.equals(next))) {
                values.put("segment_started_wall_ms", wall)
                values.put("segment_started_elapsed_ms", elapsed)
                values.put("boot_marker", clock.bootMarker())
            } else {
                values.putNull("segment_started_wall_ms")
                values.putNull("segment_started_elapsed_ms")
            }
            transaction(lambda2@ {
                db.update("work_sessions", values, "id=?", arrayOf(id))
                event(id, event, request, JSONObject())
 })
            db.setTransactionSuccessful()
            return current(id)
        } finally {
            db.endTransaction()
        }
    }
    private fun storedWorked(id: String): Long {
        db.rawQuery("SELECT worked_ms FROM work_sessions WHERE id=?", arrayOf(id)).use { c ->

            if ((c.moveToFirst())) {
                return c.getLong(0)
            }
        }
        throw IllegalArgumentException("Arbeitssitzung nicht gefunden.")
    }
    private fun runningDeltaFor(id: String): Long {
        db.rawQuery("SELECT segment_started_wall_ms,segment_started_elapsed_ms,boot_marker FROM work_sessions WHERE id=?", arrayOf(id)).use { c ->

            if ((c.moveToFirst())) {
                return runningDelta(c.getLong(0), c.getLong(1), c.getString(2))
            }
        }
        throw IllegalArgumentException("Arbeitssitzung nicht gefunden.")
    }
    private fun reviewReason(wall: Long, elapsed: Long, boot: String): String {
        return WorkSessionTimingRules.reviewReason(wall, elapsed, boot, clock.wallMillis(), clock.elapsedMillis(), clock.bootMarker())
    }
    private fun runningDelta(wall: Long, elapsed: Long, boot: String): Long {
        return WorkSessionTimingRules.verifiedDelta(wall, elapsed, boot, clock.wallMillis(), clock.elapsedMillis(), clock.bootMarker())
    }
    private fun activeId(): String? {
        db.rawQuery("SELECT id FROM work_sessions WHERE state IN (?,?,?) ORDER BY updated_at DESC LIMIT 1", arrayOf(RUNNING, PAUSED, STOPPED)).use { c ->

            return (if (c.moveToFirst()) c.getString(0) else null)
        }
    }
    @Throws(Exception::class)
    private fun requireEvent(request: String, expected: String) {
        db.rawQuery("SELECT event_type FROM work_session_events WHERE request_id=?", arrayOf(request)).use { row ->

            if (((!row.moveToFirst() || !expected.equals(row.getString(0))))) {
                throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Anfragekennung gehört zu einer anderen Zeitaktion.")
            }
        }
    }
    private fun sessionForRequest(request: String): String {
        db.rawQuery("SELECT session_id FROM work_session_events WHERE request_id=?", arrayOf(request)).use { c ->

            return (if (c.moveToFirst()) c.getString(0) else "")
        }
    }
    @Throws(Exception::class)
    private fun event(session: String, type: String, request: String?, payload: JSONObject) {
        var payload = payload
        if ((((request == null) || request!!.trim().isEmpty()))) {
            throw IllegalArgumentException("Eine eindeutige Anfragekennung fehlt.")
        }
        var e: ContentValues = ContentValues()
        e.put("id", ("evt_" + UUID.randomUUID().toString().replace("-", "")))
        e.put("session_id", session)
        e.put("event_type", type)
        e.put("request_id", request!!.trim())
        e.put("occurred_at", nowIso())
        payload = JSONObject(payload.toString()).put("_clock", JSONObject().put("wall_ms", clock.wallMillis()).put("elapsed_ms", clock.elapsedMillis()).put("boot_marker", clock.bootMarker()).put("timezone", "Europe/Vienna"))
        e.put("payload", payload.toString())
        db.insertOrThrow("work_session_events", null, e)
    }
    private fun required(value: JSONObject, name: String): String {
        var result: String = value.optString(name).trim()
        if ((result.isEmpty())) {
            throw IllegalArgumentException("Eine eindeutige Anfragekennung fehlt.")
        }
        return result
    }
    private fun nowIso(): String {
        return Instant.ofEpochMilli(clock.wallMillis()).toString()
    }
    private fun interface Change {
        @Throws(Exception::class)
        fun run()
    }
    @Throws(Exception::class)
    private fun transaction(change: Change) {
        db.beginTransaction()
        try {
            change.run()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    companion object {
        @JvmField val RUNNING: String = "RUNNING"
        @JvmField val PAUSED: String = "PAUSED"
        @JvmField val STOPPED: String = "STOPPED_PENDING_REVIEW"
        @JvmField val FINALIZED: String = "FINALIZED"
        @JvmField val CANCELLED: String = "CANCELLED"
        @JvmStatic fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS work_sessions(id TEXT PRIMARY KEY,state TEXT NOT NULL,customer_id TEXT NOT NULL DEFAULT '',order_id TEXT NOT NULL DEFAULT '',work_date TEXT NOT NULL,activity TEXT NOT NULL DEFAULT '',started_at_ms INTEGER NOT NULL,segment_started_wall_ms INTEGER,segment_started_elapsed_ms INTEGER,boot_marker TEXT NOT NULL,worked_ms INTEGER NOT NULL DEFAULT 0,finalized_worklog_id TEXT NOT NULL DEFAULT '',updated_at TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS work_session_events(id TEXT PRIMARY KEY,session_id TEXT NOT NULL,event_type TEXT NOT NULL,request_id TEXT NOT NULL UNIQUE,occurred_at TEXT NOT NULL,payload TEXT NOT NULL DEFAULT '{}',FOREIGN KEY(session_id) REFERENCES work_sessions(id))")
            db.execSQL("CREATE INDEX IF NOT EXISTS work_session_events_session ON work_session_events(session_id,occurred_at)")
        }
    }
}
