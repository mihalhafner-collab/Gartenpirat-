package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only semantic validation port for captured/restored SQLite images, never the active store.
 */
class RevierSnapshotRepository
@JvmOverloads
constructor(
    private val database: SQLiteDatabase,
    private val canonical: JSONObject,
    privateFiles: File? = null,
) : RevierRepository {
    private val photoDirectory = privateFiles?.let { File(it, "customer-photo-originals") }

    override fun effective(): JSONObject = JSONObject(canonical.toString())

    override fun get(key: String, fallback: String): String =
        DatabaseBackupSnapshot.ownerValue(database, key, fallback)!!

    override fun put(key: String, value: String): Unit =
        throw IllegalStateException("REVIER_SNAPSHOT_READ_ONLY")

    override fun transaction(work: RevierRepository.Work): JSONObject =
        throw IllegalStateException("REVIER_SNAPSHOT_READ_ONLY")

    override fun planInspection(appointmentArgs: JSONObject): JSONObject =
        throw IllegalStateException("REVIER_SNAPSHOT_READ_ONLY")

    override fun list(prefix: String): JSONArray {
        val out = JSONArray()
        database
            .rawQuery(
                "SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",
                arrayOf(prefix, prefix + '\uffff'),
            )
            .use { rows -> while (rows.moveToNext()) out.put(JSONObject(rows.getString(1))) }
        return out
    }

    override fun rawEntries(prefix: String): JSONArray {
        val out = JSONArray()
        database
            .rawQuery(
                "SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",
                arrayOf(prefix, prefix + '\uffff'),
            )
            .use { rows ->
                while (rows.moveToNext()) out.put(
                    JSONObject().put("key", rows.getString(0)).put("value", rows.getString(1))
                )
            }
        return out
    }

    override fun session(id: String): JSONObject? = RevierSessionContexts.session(database, id)

    override fun sessionContexts(): JSONArray = RevierSessionContexts.all(database)

    override fun verifyEquipmentProfilePhoto(photo: JSONObject) {
        EquipmentProfilePhotoRules.attachment(
            photo,
            BackupSnapshotState.photo(database, photo.getString("id")),
        )
        EquipmentProfilePhotoRules.original(photoDirectory, photo)
    }
}
