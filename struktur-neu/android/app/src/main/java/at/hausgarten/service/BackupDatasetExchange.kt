package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Atomic component renames behind an external reader/writer gate, with a durable roll-back journal. */
class BackupDatasetExchange private constructor() {
    interface Journal {
        @Throws(Exception::class)
        fun read(): JSONObject

        @Throws(Exception::class)
        fun save(state: JSONObject)
    }

    fun interface Moves {
        @Throws(Exception::class)
        fun move(from: File, to: File)
    }

    companion object {
        @JvmField
        val ATOMIC = Moves { from, to ->
            if (Files.isSymbolicLink(from.toPath()) || Files.isSymbolicLink(to.toPath()) || to.exists()) {
                throw IOException("RESTORE_EXCHANGE_PATH_CONFLICT")
            }
            val parent = to.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("RESTORE_EXCHANGE_STORAGE_UNAVAILABLE")
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun install(journal: Journal, moves: Moves) {
            val state = journal.read()
            val targets = state.getJSONArray("targets")
            for (i in 0 until targets.length()) {
                BackupArchive.checkCancelled()
                val target = targets.getJSONObject(i)
                val live = file(target, "live")
                val old = file(target, "old")
                if (!target.optBoolean("old_done")) {
                    if (!target.has("old_present")) {
                        target.put("old_present", live.exists())
                        journal.save(state)
                    }
                    if (target.getBoolean("old_present")) {
                        if (live.exists() && !old.exists()) moves.move(live, old)
                        else if (live.exists() || !old.exists()) throw IOException("RESTORE_EXCHANGE_OLD_STATE_AMBIGUOUS")
                    } else if (live.exists() || old.exists()) throw IOException("RESTORE_EXCHANGE_OLD_STATE_CHANGED")
                    target.put("old_done", true)
                    journal.save(state)
                }
                if (!target.optBoolean("new_done")) {
                    val source = target.optString("prepared", "")
                    if (source.isNotEmpty()) {
                        val prepared = File(source)
                        if (prepared.exists() && !live.exists()) moves.move(prepared, live)
                        else if (prepared.exists() || !live.exists()) throw IOException("RESTORE_EXCHANGE_NEW_STATE_AMBIGUOUS")
                    } else if (live.exists()) throw IOException("RESTORE_EXCHANGE_NEW_STATE_CHANGED")
                    target.put("new_done", true)
                    journal.save(state)
                }
            }
            state.put("phase", "SWAPPED")
            journal.save(state)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun rollback(journal: Journal, moves: Moves) {
            val state = journal.read()
            state.put("phase", "ROLLING_BACK")
            journal.save(state)
            val targets = state.getJSONArray("targets")
            for (i in targets.length() - 1 downTo 0) {
                val target = targets.getJSONObject(i)
                val live = file(target, "live")
                val old = file(target, "old")
                val source = target.optString("prepared", "")
                // A process may have died immediately after a move but before its completion flag.
                val installed = source.isNotEmpty() && !File(source).exists() && live.exists() && target.optBoolean("old_done")
                if (!target.optBoolean("new_rolled_back")) {
                    if (installed) {
                        val prepared = File(source)
                        moves.move(live, prepared)
                    } else if (source.isEmpty() && target.optBoolean("runtime_sidecar") &&
                        target.optBoolean("new_done") && target.optBoolean("old_done") && live.exists()) {
                        moves.move(live, File(old.parentFile, target.getString("kind") + "_restored_sidecar"))
                    } else if (target.optBoolean("new_done") && source.isNotEmpty() && live.exists() && File(source).exists()) {
                        throw IOException("RESTORE_ROLLBACK_NEW_STATE_AMBIGUOUS")
                    }
                    target.put("new_rolled_back", true)
                    journal.save(state)
                }
                if (!target.optBoolean("old_rolled_back")) {
                    if (target.optBoolean("old_present")) {
                        if (old.exists() && !live.exists()) moves.move(old, live)
                        else if (old.exists() || !live.exists()) throw IOException("RESTORE_ROLLBACK_OLD_STATE_AMBIGUOUS")
                    } else if (target.has("old_present") && live.exists()) throw IOException("RESTORE_ROLLBACK_NEW_STATE_REMAINS")
                    target.put("old_rolled_back", true)
                    journal.save(state)
                }
            }
            state.put("phase", "ROLLED_BACK")
            journal.save(state)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun targets(live: Map<String, File>, prepared: Map<String, File>, rollback: File): JSONArray {
            val out = JSONArray()
            val paths = HashSet<String>()
            for ((key, value) in live) {
                if (!key.matches(Regex("[a-z0-9_]{1,80}"))) throw IOException("RESTORE_EXCHANGE_TARGET_INVALID")
                val target = value.canonicalFile
                val source = prepared[key]
                val old = File(rollback, key).canonicalFile
                if (!paths.add(target.path) || target == old || source != null && target == source.canonicalFile) {
                    throw IOException("RESTORE_EXCHANGE_TARGET_OVERLAP")
                }
                out.put(JSONObject().put("kind", key).put("live", target.path).put("old", old.path)
                    .put("prepared", source?.canonicalPath ?: "")
                    .put("runtime_sidecar", key.startsWith("database_") || key.startsWith("preferences_") && key.endsWith("_bak")))
            }
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun anyOldMoved(journal: JSONObject): Boolean {
            val targets = journal.optJSONArray("targets") ?: return false
            for (i in 0 until targets.length()) {
                val target = targets.getJSONObject(i)
                if (target.optBoolean("old_done") || File(target.getString("old")).exists()) return true
            }
            return false
        }

        @Throws(Exception::class)
        private fun file(value: JSONObject, key: String): File = File(value.getString(key))
    }
}
