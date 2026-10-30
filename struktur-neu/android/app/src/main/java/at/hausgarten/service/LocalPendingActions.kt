package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/** One journal view shared by live reads and captured backup reads. */
internal class LocalPendingActions private constructor() {
    fun interface Photos {
        @Throws(Exception::class) fun get(id: String): JSONObject?
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun read(db: SQLiteDatabase, forDrive: Boolean, photos: Photos, now: Long): JSONArray {
            val result = JSONArray()
            db.rawQuery(
                    "SELECT id,kind,length(payload),created_at FROM pending WHERE kind!='mcp_receipt' ORDER BY created_at ASC,rowid ASC",
                    null,
                )
                .use { rows ->
                    while (rows.moveToNext()) {
                        val id = rows.getString(0)
                        val kind = rows.getString(1)
                        val length = rows.getInt(2)
                        val text = StringBuilder()
                        var offset = 0
                        while (offset < length) {
                            db.rawQuery(
                                    "SELECT substr(payload,?,32768) FROM pending WHERE id=?",
                                    arrayOf((offset + 1).toString(), id),
                                )
                                .use { part ->
                                    if (!part.moveToFirst())
                                        throw IOException("LOCAL_PENDING_ROW_MISSING")
                                    text.append(part.getString(0))
                                }
                            offset += 32768
                        }
                        var payload = JSONObject(text.toString())
                        if (
                            forDrive &&
                                (payload.optBoolean("_local_only") ||
                                    payload.optBoolean("test_record"))
                        )
                            continue
                        if (
                            forDrive &&
                                (kind == "delete" || kind == "archive") &&
                                payload.optLong("_undo_until", 0) > now
                        )
                            continue
                        payload.remove("_undo_until")
                        if (!forDrive && kind == "photo")
                            payload =
                                PhotoJournalRules.forSnapshot(
                                    payload,
                                    photos.get(payload.optString("id")),
                                )
                        result.put(
                            JSONObject()
                                .put("client_id", id)
                                .put("kind", kind)
                                .put("payload", payload)
                                .put("created_at", rows.getString(3))
                        )
                    }
                }
            return result
        }
    }
}
