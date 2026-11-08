package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import java.io.IOException
import org.json.JSONObject

/**
 * Read the captured DB/prefs using existing projection/catalog/tombstone rules, never live state.
 */
internal object BackupSnapshotState {
    @JvmStatic
    @Throws(Exception::class)
    fun read(
        db: SQLiteDatabase,
        preferences: JSONObject,
        initialData: String,
        serviceCatalog: String,
    ): JSONObject {
        if (db.version < 4 || db.version > PixelLocalStore.SCHEMA_VERSION) {
            throw IOException("BACKUP_DATABASE_VERSION_UNSUPPORTED")
        }
        val fallback = preferences.optString("drive_snapshot", initialData)
        val master = PixelLocalStore.businessTables(db).snapshot(fallback)
        PixelLocalStore.removeSnapshotPhotoBinaries(master)
        LocalBusinessState.prepare(master)
        ServiceCatalogBootstrap.seed(master) { serviceCatalog }
        LocalTestCleanup.overlay(
            { key, otherwise ->
                DatabaseBackupSnapshot.ownerValue(db, key, otherwise) ?: otherwise
            },
            master,
        )
        OfferDecisionProjection.apply(db, master)
        val actions = LocalPendingActions.read(db, { id -> photo(db, id) })
        val state =
            JSONObject()
                .put("master", master)
                .put("pending", actions)
                .put("extended_business_fingerprint", BusinessStateFingerprint.read(db))
        db.rawQuery(
                "SELECT id,state,updated_at FROM work_sessions WHERE state NOT IN ('FINALIZED','CANCELLED') ORDER BY rowid DESC LIMIT 1",
                null,
            )
            .use { session ->
                state.put(
                    "work_session",
                    if (session.moveToFirst()) {
                        JSONObject()
                            .put("id", session.getString(0))
                            .put("state", session.getString(1))
                            .put("updated_at", session.getString(2))
                    } else {
                        JSONObject().put("state", "IDLE")
                    },
                )
            }
        return state
    }

    @JvmStatic
    @Throws(Exception::class)
    fun photo(db: SQLiteDatabase, id: String): JSONObject? {
        db.rawQuery(
                "SELECT content_sha256,mime_type,byte_size,storage_name,role FROM photo_attachments WHERE photo_id=?",
                arrayOf(id),
            )
            .use { row ->
                if (!row.moveToFirst()) return null
                return JSONObject()
                    .put("id", id)
                    .put("content_sha256", row.getString(0))
                    .put("mime_type", row.getString(1))
                    .put("byte_size", row.getLong(2))
                    .put("storage_name", row.getString(3))
                    .put("role", row.getString(4))
            }
    }
}
