package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import org.json.*

/**
 * Reads immutable START assignments without changing timers or inferring historical object links.
 */
class RevierSessionContexts {
    private constructor() {}

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun session(database: SQLiteDatabase, id: String): JSONObject? {
            var result: JSONObject
            database
                .rawQuery(
                    "SELECT id,customer_id,order_id,state FROM work_sessions WHERE id=?",
                    arrayOf(id),
                )
                .use { row ->
                    if ((!row.moveToFirst())) {
                        return null
                    }
                    result =
                        JSONObject()
                            .put("id", row.getString(0))
                            .put("customer_id", row.getString(1))
                            .put("order_id", row.getString(2))
                            .put("state", row.getString(3))
                }
            database
                .rawQuery(
                    "SELECT payload FROM work_session_events WHERE session_id=? AND event_type='START' ORDER BY rowid",
                    arrayOf(id),
                )
                .use { start ->
                    if ((start.moveToFirst())) {
                        var origin: JSONObject = JSONObject(start.getString(0))
                        for (key in arrayOf("property_id", "visit_id")) {
                            if ((origin.has(key))) {
                                result.put(key, origin.get(key))
                            }
                        }
                        result
                            .put("_start_customer_id", origin.optString("customer_id"))
                            .put("_start_order_id", origin.optString("order_id"))
                        if ((start.moveToNext())) {
                            throw PixelMcpWriteTools.Rejected(
                                "REVIER_RELATION_INVALID",
                                "Eine Sitzung besitzt mehrere ursprüngliche Startzuordnungen.",
                            )
                        }
                    }
                }
            return result
        }

        @Throws(Exception::class)
        @JvmStatic
        fun all(database: SQLiteDatabase): JSONArray {
            var out: JSONArray = JSONArray()
            database.rawQuery("SELECT id FROM work_sessions ORDER BY id", null).use { rows ->
                while ((rows.moveToNext())) {
                    out.put(session(database, rows.getString(0)))
                }
            }
            database
                .rawQuery(
                    "SELECT e.id FROM work_session_events e LEFT JOIN work_sessions s ON s.id=e.session_id WHERE e.event_type='START' AND s.id IS NULL LIMIT 1",
                    null,
                )
                .use { orphan ->
                    if ((orphan.moveToFirst())) {
                        throw PixelMcpWriteTools.Rejected(
                            "REVIER_RELATION_INVALID",
                            "Ursprüngliche Startzuordnung besitzt keine Sitzung.",
                        )
                    }
                }
            return out
        }
    }
}
