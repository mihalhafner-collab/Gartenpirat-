package at.hausgarten.service

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/** Read-only verification of existing historical evidence; no remote backup creation. */
internal object LegacyConflictBackup {
    private fun retired(): PixelMcpWriteTools.Rejected = PixelMcpWriteTools.Rejected(
        "LEGACY_SYNC_RETIRED", "Der frühere Drive-Geschäftsabgleich ist entfernt. Verschlüsselte Vollsicherung gesondert prüfen."
    )

    @JvmStatic
    @Throws(Exception::class)
    fun requireReady(context: Context?, store: PixelLocalStore?) { throw retired() }

    @JvmStatic
    @Throws(Exception::class)
    fun requireReady(context: Context?, store: PixelLocalStore?, expected: String?) { throw retired() }

    @JvmStatic
    @Throws(Exception::class)
    fun create(context: Context?, store: PixelLocalStore?, request: String?): JSONObject { throw retired() }

    @JvmStatic
    @Throws(Exception::class)
    fun create(context: Context?, store: PixelLocalStore?, request: String?, expected: String?): JSONObject { throw retired() }

    @JvmStatic
    @Throws(Exception::class)
    fun verify(c: Context, s: PixelLocalStore, id: String, preview: String): JSONObject {
        if (!id.matches(Regex("legacy_[0-9a-f]{32}"))) throw IllegalArgumentException("Ungültige Sicherungskennung.")
        val meta = JSONObject(s.ownerValue("legacy-backup:$id", "{}"))
        val file = File(File(File(c.noBackupFilesDir, "legacy-conflict-backups"), id), "backup.zip")
        if (!meta.optBoolean("complete") || preview != meta.optString("preview_hash") || !file.isFile || sha(file) != meta.optString("sha256")) {
            throw IllegalArgumentException("Vollständige unveränderte Sicherung fehlt. Nichts abgeschlossen.")
        }
        if (meta.has("business_hash") && meta.getString("business_hash") != businessHash(s.getState())) {
            throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Geschäftsdaten seit der Sicherung geändert. Neu sichern; nichts abgeschlossen.")
        }
        return meta
    }

    @Throws(Exception::class)
    private fun businessHash(state: JSONObject): String {
        val all = PixelMcpReadTools.effective(state)
        return PixelMcpWriteTools.hash(JSONObject().put("business", PixelDriveState.integrity(all))
            .put("settings", all.optJSONObject("business_settings")).put("archive", OwnerJournalRules.archives(all)))
    }

    @Throws(Exception::class)
    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val block = ByteArray(65536)
            while (true) {
                val count = input.read(block)
                if (count == -1) break
                digest.update(block, 0, count)
            }
        }
        val hash = StringBuilder()
        for (byte in digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 255))
        return hash.toString()
    }
}
