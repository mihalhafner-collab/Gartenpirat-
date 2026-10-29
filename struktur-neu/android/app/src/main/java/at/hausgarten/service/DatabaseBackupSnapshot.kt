package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.TreeMap

/** Consistent private SQLite image, including pending rows, receipts, sessions and BLOB jobs. */
object DatabaseBackupSnapshot {
    @JvmStatic
    @Throws(Exception::class)
    fun capture(source: SQLiteDatabase, freshTarget: File): JSONObject {
        if (source.inTransaction()) throw IOException("BACKUP_SNAPSHOT_REQUIRES_OUTSIDE_TRANSACTION")
        if (freshTarget.exists() || freshTarget.isDirectory) throw IOException("BACKUP_SNAPSHOT_TARGET_EXISTS")
        val parent = freshTarget.parentFile!!
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("BACKUP_SNAPSHOT_STORAGE_UNAVAILABLE")
        EncryptedBackupArchive.checkCancelled()
        val pages = source.rawQuery("PRAGMA page_count", null).use { count ->
            if (!count.moveToFirst()) throw IOException("BACKUP_DATABASE_SIZE_UNAVAILABLE")
            count.getLong(0)
        }
        BackupStorageBudget.require(parent, Math.multiplyExact(Math.multiplyExact(pages, source.pageSize), 2))
        // SQLite performs a transactional snapshot, including committed WAL pages.
        // Never copy only the live main DB file. Older unsupported engines fail without changing source.
        source.execSQL("VACUUM INTO '${freshTarget.absolutePath.replace("'", "''")}'")
        EncryptedBackupArchive.checkCancelled()
        FileOutputStream(freshTarget, true).use { it.fd.sync() }
        return inspect(freshTarget)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun inspect(database: File): JSONObject {
        if (!database.isFile) throw IOException("BACKUP_DATABASE_MISSING")
        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("PRAGMA integrity_check", null).use { check ->
                if (!check.moveToFirst() || check.getString(0) != "ok" || check.moveToNext()) {
                    throw IOException("BACKUP_DATABASE_INTEGRITY_FAILED")
                }
            }
            var foreignKeyViolations = 0L
            copy.rawQuery("PRAGMA foreign_key_check", null).use { foreign ->
                while (foreign.moveToNext()) foreignKeyViolations++
            }
            val counts = JSONObject()
            val definitions = TreeMap<String, String>()
            copy.rawQuery("SELECT type,name,sql FROM sqlite_master ORDER BY type,name", null).use { schema ->
                while (schema.moveToNext()) {
                    val kind = schema.getString(0)
                    val name = schema.getString(1)
                    val sql = schema.getString(2)
                    definitions["$kind:$name"] = sql ?: ""
                    if (kind == "table") {
                        copy.rawQuery("SELECT count(*) FROM \"${name.replace("\"", "\"\"")}\"", null).use { count ->
                            if (!count.moveToFirst()) throw IOException("BACKUP_DATABASE_COUNT_FAILED")
                            counts.put(name, count.getLong(0))
                        }
                    }
                }
            }
            return JSONObject()
                .put("schemaVersion", copy.version)
                .put("foreignKeyViolations", foreignKeyViolations)
                .put("tableCounts", counts)
                .put("schemaSha256", PixelMcpWriteTools.hash(JSONObject(definitions)))
                .put("size", database.length())
                .put("sha256", EncryptedBackupArchive.sha256(database))
        }
    }

    /** Large snapshot/payload rows must not require a CursorWindow-sized JSON value. */
    @JvmStatic
    @Throws(Exception::class)
    fun ownerValue(snapshot: SQLiteDatabase, key: String, fallback: String?): String? {
        val length = snapshot.rawQuery("SELECT length(value) FROM owner_state WHERE key=?", arrayOf(key)).use { row ->
            if (!row.moveToFirst()) return fallback
            row.getInt(0)
        }
        val value = StringBuilder()
        var offset = 0
        while (offset < length) {
            snapshot.rawQuery("SELECT substr(value,?,32768) FROM owner_state WHERE key=?", arrayOf((offset + 1).toString(), key)).use { row ->
                if (!row.moveToFirst()) throw IOException("BACKUP_DATABASE_ROW_MISSING")
                value.append(row.getString(0))
            }
            offset += 32768
        }
        return value.toString()
    }
}
