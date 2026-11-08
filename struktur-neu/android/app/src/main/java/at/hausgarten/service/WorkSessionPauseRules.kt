package at.hausgarten.service

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Pause evidence from immutable boundaries; legacy closed intervals are preserved. */
class WorkSessionPauseRules private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun calculate(events: JSONArray, state: String, now: Long): JSONObject =
            calculate(events, state, now, null)

        @JvmStatic
        @Throws(Exception::class)
        fun calculate(
            events: JSONArray,
            state: String,
            wall: Long,
            elapsed: Long,
            boot: String,
        ): JSONObject =
            calculate(
                events,
                state,
                wall,
                JSONObject()
                    .put("wall_ms", wall)
                    .put("elapsed_ms", elapsed)
                    .put("boot_marker", boot),
            )

        @Throws(Exception::class)
        private fun calculate(
            events: JSONArray,
            state: String,
            now: Long,
            currentClock: JSONObject?,
        ): JSONObject {
            var total = 0L
            var opened: JSONObject? = null
            var uncertain = false
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                when (val type = event.getString("event_type")) {
                    "TIME_REVIEW" -> {
                        total =
                            Math.multiplyExact(
                                event.getJSONObject("payload").getLong("break_minutes"),
                                60_000L,
                            )
                        opened = null
                        uncertain = false
                    }
                    "PAUSE",
                    "RESTORE" -> {
                        if (opened != null && type == "PAUSE") uncertain = true
                        opened = event
                    }
                    "RESUME",
                    "CONTINUE",
                    "STOP",
                    "DISCARD" -> {
                        opened?.let {
                            val delta = duration(it, event)
                            if (delta < 0) uncertain = true else total = Math.addExact(total, delta)
                            opened = null
                        }
                        if (type == "STOP") opened = event
                    }
                    "FINALIZE" -> opened = null
                }
            }
            if (opened != null && state == "PAUSED") {
                val end = JSONObject().put("occurred_at", Instant.ofEpochMilli(now).toString())
                if (currentClock != null)
                    end.put("payload", JSONObject().put("_clock", currentClock))
                val delta = duration(opened, end)
                if (delta < 0) uncertain = true else total = Math.addExact(total, delta)
            }
            return JSONObject()
                .put("pause_ms", total)
                .put("break_minutes", Math.round(total / 60_000.0))
                .put("pause_unverified", uncertain)
        }

        private fun clock(event: JSONObject): JSONObject? =
            event.optJSONObject("payload")?.optJSONObject("_clock")

        @Throws(Exception::class)
        private fun duration(start: JSONObject, end: JSONObject): Long {
            val a = clock(start)
            val b = clock(end)
            if (a != null && b != null) {
                val review =
                    WorkSessionTimingRules.reviewReason(
                        a.getLong("wall_ms"),
                        a.getLong("elapsed_ms"),
                        a.getString("boot_marker"),
                        b.getLong("wall_ms"),
                        b.getLong("elapsed_ms"),
                        b.getString("boot_marker"),
                    )
                return if (review.isEmpty()) b.getLong("elapsed_ms") - a.getLong("elapsed_ms")
                else -1
            }
            if (a != null || b != null) return -1
            val delta =
                Instant.parse(end.getString("occurred_at")).toEpochMilli() -
                    Instant.parse(start.getString("occurred_at")).toEpochMilli()
            return if (delta < 0) -1 else delta
        }
    }
}
