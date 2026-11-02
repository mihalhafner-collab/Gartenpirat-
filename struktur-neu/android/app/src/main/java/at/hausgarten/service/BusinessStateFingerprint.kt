package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Business namespaces outside the historical snapshot are covered by the same backup/CAS proof. */
object BusinessStateFingerprint {
    @JvmStatic @Throws(Exception::class)
    fun read(database: SQLiteDatabase): JSONObject {
        val hashes = JSONObject()
        database.rawQuery("SELECT key FROM owner_state WHERE key LIKE 'revier:v1:%' OR key LIKE 'offer_decision:v1:%' OR key IN ('personal_paid','personal_paid_migrated','settings') ORDER BY key", null).use { keys ->
            while (keys.moveToNext()) {
                val key = keys.getString(0)
                hashes.put(key, PixelMcpWriteTools.hash(DatabaseBackupSnapshot.ownerValue(database, key, "")))
            }
        }
        for (table in arrayOf("work_sessions", "work_session_events", "photo_attachments")) {
            val rows = JSONArray()
            database.rawQuery("SELECT * FROM $table ORDER BY 1", null).use { cursor ->
                val names = cursor.columnNames
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in names.indices) row.put(names[i], if (cursor.isNull(i)) JSONObject.NULL else cursor.getString(i))
                    rows.put(row)
                }
            }
            hashes.put("table:$table", PixelMcpWriteTools.hash(rows))
        }
        val uploads = JSONArray()
        database.rawQuery("SELECT id,metadata,length(data) FROM mcp_uploads ORDER BY id", null).use { rows ->
            while (rows.moveToNext()) uploads.put(JSONObject().put("id", rows.getString(0)).put("metadata_sha256", PixelMcpWriteTools.hash(rows.getString(1))).put("bytes", rows.getLong(2)))
        }
        hashes.put("table:mcp_uploads", PixelMcpWriteTools.hash(uploads))
        return JSONObject().put("formatVersion", 1).put("owner_business_sha256", PixelMcpWriteTools.hash(hashes)).put("keys_count", hashes.length())
    }
}
