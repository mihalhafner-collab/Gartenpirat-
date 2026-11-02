package at.hausgarten.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

/** Local ownership decides retention. No cloud receipt or restore epoch is needed. */
internal class LocalStorageMaintenance(private val context: Context, private val store: PixelLocalStore) {
    fun usage(): JSONObject {
        val database = context.getDatabasePath("haus-garten-mobile.db")
        val dbBytes = bytes(database) + bytes(File(database.path + "-wal")) + bytes(File(database.path + "-shm"))
        val photos = listOf("customer-photo-originals", "browser-media", "pending-work-photo-files", "native-form-media")
            .sumOf { bytes(File(context.filesDir, it)) }
        val documents = bytes(File(context.filesDir, "business-documents"))
        val other = (bytes(context.filesDir) - photos - documents).coerceAtLeast(0)
        val work = bytes(context.noBackupFilesDir)
        val cache = bytes(context.cacheDir)
        return JSONObject().put("database_bytes", dbBytes).put("photo_bytes", photos)
            .put("document_bytes", documents).put("other_files_bytes", other).put("work_bytes", work).put("cache_bytes", cache)
            .put("backup_work_bytes", bytes(File(context.noBackupFilesDir, "pixel-backup")))
            .put("total_bytes", dbBytes + photos + documents + other + work + cache)
    }

    fun clean(vacuum: Boolean): JSONObject {
        // Backup owns SAVE_LOCK before FILE_CAPTURE_LOCK. Keep that order here too.
        val backupRemoved = PixelBackupService.cleanupWork(context)
        val before = usage().getLong("total_bytes") + backupRemoved
        var removedActions = 0
        var removedFiles = 0
        val database = store.writableDatabase
        database.acquireReference()
        try {
            check(!database.inTransaction()) { "STORAGE_CLEANUP_REQUIRES_COMMITTED_DATA" }
            synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
                store.beginBusinessTransaction(database)
                try {
                    val state = store.getState()
                    val effective = JSONObject(state.getJSONObject("master").toString())
                    val pending = state.optJSONArray("pending") ?: JSONArray()
                    LocalJournalRules.apply(effective, pending)
                    val displayed = PixelMcpReadTools.effective(state)
                    for (collection in arrayOf("customers", "orders", "worklogs", "invoices", "photos", "offers", "appointments", "owner_archive")) {
                        if (PixelMcpWriteTools.hash(displayed.opt(collection)) != PixelMcpWriteTools.hash(effective.opt(collection)))
                            throw IOException("STORAGE_PROJECTION_MISMATCH")
                    }
                    val applied = LocalJournalRules.ids(effective.optJSONArray("drive_applied_ids")).toMutableSet()
                    applied.addAll(LocalJournalRules.ids(effective.optJSONArray("accepted_ids")))
                    effective.optJSONObject("drive_resolved_actions")?.keys()?.forEach { applied.add(it) }
                    val deleted = mutableSetOf<String>()
                    val archive = OwnerJournalRules.archives(effective)
                    val archiveKeys = archive.keys().asSequence().toList()
                    for (key in archiveKeys) if (archive.getJSONObject(key).optBoolean("purged")) {
                        deleted.add(key); archive.remove(key)
                    }
                    for (i in 0 until pending.length()) {
                        val action = pending.getJSONObject(i)
                        if (action.getString("client_id") !in applied) continue
                        val payload = action.getJSONObject("payload")
                        if (action.optString("kind") == "purge_v1") deleted.add(payload.optString("entity") + ":" + payload.optString("id"))
                        if (action.optString("kind") == TrashPurgeBatch.KIND) {
                            val entries = payload.getJSONArray("entries")
                            for (j in 0 until entries.length()) entries.getJSONObject(j).let { deleted.add(it.getString("entity") + ":" + it.getString("entity_id")) }
                        }
                    }
                    val removedRequests = mutableSetOf<String>()
                    val remaining = JSONArray()
                    for (i in 0 until pending.length()) {
                        val action = pending.getJSONObject(i)
                        val id = action.getString("client_id")
                        if (id in applied) {
                            removedActions += database.delete("pending", "id=?", arrayOf(id))
                            if (deleted.any { target -> LocalTestCleanup.sameTarget(action, target.substringBefore(':'), target.substringAfter(':')) } || action.optString("kind") == TrashPurgeBatch.KIND) removedRequests.add(id)
                        } else remaining.put(action)
                    }
                    // Retain dependencies of unresolved local work. Successfully materialized
                    // actions no longer require an ever-growing distributed replay history.
                    val dependencies = mutableSetOf<String>()
                    for (i in 0 until remaining.length()) {
                        val refs = remaining.getJSONObject(i).getJSONObject("payload").optJSONArray("_sync_predecessors") ?: continue
                        for (j in 0 until refs.length()) dependencies.add(refs.getString(j))
                    }
                    effective.put("drive_applied_ids", JSONArray(applied.filter { it in dependencies }))
                    effective.put("accepted_ids", JSONArray())
                    for (field in arrayOf("drive_action_hashes", "drive_action_receipts", "drive_resolved_actions", "owner_resolved_actions", "owner_conflict_history")) {
                        val values = effective.optJSONObject(field) ?: continue
                        for (id in values.keys().asSequence().toList()) if (id in applied && id !in dependencies) values.remove(id)
                    }
                    effective.remove("drive_delivered_ids")
                    // Persist the complete effective business state BEFORE discarding obsolete
                    // snapshots. A malformed or unresolved operation is never silently dropped.
                    store.putOwnerValue("snapshot", effective.toString())
                    database.execSQL("DELETE FROM owner_state WHERE key GLOB 'business_tables:original:*' OR key GLOB 'receipt_guard:*' OR key GLOB 'purge-cleaned:*' OR key='purged_local_test_records_v1'")
                    if (store.businessTables().active()) database.delete("owner_state", "key=?", arrayOf("snapshot"))
                    pruneReceipts(database, removedRequests, deleted)
                    database.setTransactionSuccessful()
                } finally { database.endTransaction() }
                // The authoritative snapshot is committed even when preference cleanup fails.
                val preferences = context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE)
                val editor = preferences.edit()
                preferences.all.keys.filter { it == "drive_snapshot" || it.startsWith("drive_snapshot_") }.forEach { editor.remove(it) }
                if (!editor.commit()) throw IOException("STORAGE_PREFERENCES_CLEANUP_PENDING")

                // Serialize reference recheck and file reclamation against other SQLite writers.
                store.beginBusinessTransaction(database)
                try {
                    val refs = references(database)
                    val uploads = mutableListOf<String>()
                    database.rawQuery("SELECT id,metadata FROM mcp_uploads", null).use { rows ->
                        while (rows.moveToNext()) {
                            val id = rows.getString(0)
                            val meta = JSONObject(rows.getString(1))
                            val recent = runCatching { Instant.parse(meta.optString("last_written_at")).toEpochMilli() > System.currentTimeMillis() - DAY }.getOrDefault(false)
                            // A currently uploading file may not yet belong to a saved form.
                            if (meta.optString("kind") == "backup" || id in refs || (recent && !meta.has("attached_photo"))) collect(meta, refs) else uploads.add(id)
                        }
                    }
                    uploads.forEach { database.delete("mcp_uploads", "id=?", arrayOf(it)) }
                    database.rawQuery("SELECT photo_id,storage_name,content_sha256 FROM photo_attachments", null).use { rows ->
                        val orphanRows = mutableListOf<String>()
                        while (rows.moveToNext()) {
                            if (rows.getString(0) in refs) { refs.add(rows.getString(1)); refs.add(rows.getString(2)) }
                            else orphanRows.add(rows.getString(0))
                        }
                        orphanRows.forEach { database.delete("photo_attachments", "photo_id=?", arrayOf(it)) }
                    }
                    for (name in arrayOf("customer-photo-originals", "business-documents", "browser-media", "pending-work-photo-files", "mcp-photo-uploads", "native-form-media")) {
                        val directory = File(context.filesDir, name)
                        // Form photo staging publishes a URI shortly before the draft writer.
                        // Give only that staging area a grace period; purged originals/PDFs go now.
                        removedFiles += removeUnreferenced(directory, refs, if (name == "native-form-media") DAY else 0)
                    }
                    for (name in arrayOf("immutable-photo-staging", "immutable-document-staging", "photo-capture", "offer-share")) {
                        removedFiles += removeUnreferenced(File(context.cacheDir, name), refs, DAY)
                    }
                    store.putOwnerValue("local_media_cleanup", JSONObject().put("pending", false).toString())
                    database.setTransactionSuccessful()
                } finally { database.endTransaction() }
                if (vacuum) {
                    database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { rows ->
                        if (rows.moveToFirst() && rows.getInt(0) != 0) throw IOException("STORAGE_CHECKPOINT_BUSY")
                    }
                    database.execSQL("VACUUM")
                    database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { rows ->
                        if (rows.moveToFirst() && rows.getInt(0) != 0) throw IOException("STORAGE_CHECKPOINT_BUSY")
                    }
                }
            }
        } finally { database.releaseReference() }
        val after = usage()
        return after.put("freed_bytes", (before - after.getLong("total_bytes")).coerceAtLeast(0))
            .put("journal_actions_removed", removedActions).put("files_removed", removedFiles)
    }

    private fun references(database: SQLiteDatabase): MutableSet<String> {
        val refs = mutableSetOf<String>()
        collect(PixelMcpReadTools.effective(store.getState()), refs)
        collect(store.pendingActions(), refs)
        database.rawQuery("SELECT key FROM owner_state", null).use { rows ->
            while (rows.moveToNext()) {
                val key = rows.getString(0)
                if (key == "snapshot" || key.startsWith("business_tables:") || key.startsWith("receipt:") ||
                    key.startsWith("receipt_guard:") || key.startsWith("photo-ui:") || key.startsWith("purge-cleaned:") ||
                    key.startsWith("revier:v1:audit:") || key == "purged_local_test_records_v1") continue
                if (key.startsWith("legacy-backup:")) refs.add(key.removePrefix("legacy-backup:"))
                // Include drafts, active/trash Revier records, invoice originals and retained
                // browser graphs, including restore candidates whose choice is still open.
                parse(store.ownerValue(key, ""))?.let { collect(it, refs) }
            }
        }
        for (name in arrayOf("pending-work-photo.json", "pending-work-photo.json.bak")) {
            val file = File(context.filesDir, name)
            if (file.isFile) collect(JSONObject(file.readText()), refs)
        }
        return refs
    }

    private fun pruneReceipts(database: SQLiteDatabase, requests: Set<String>, deleted: Set<String>) {
        val remove = mutableListOf<String>()
        database.rawQuery("SELECT key FROM owner_state WHERE key GLOB 'receipt:*'", null).use { rows ->
            while (rows.moveToNext()) {
                val key = rows.getString(0)
                val receipt = JSONObject(store.ownerValue(key, "{}"))
                val result = receipt.optJSONObject("result") ?: continue
                val target = result.optString("entity_type", result.optString("entity")) + ":" + result.optString("entity_id", result.optString("id"))
                val removedEntity = deleted.any { saved ->
                    val entity = saved.substringBefore(':'); val id = saved.substringAfter(':')
                    val field = if (entity == "worklog") "work_record_id" else "${entity}_id"
                    result.optString(field) == id || result.optJSONObject("record")?.let { it.optString("id") == id && (it.optString("kind", entity) == entity) } == true
                }
                if (key.removePrefix("receipt:") in requests || target in deleted || removedEntity || result.optBoolean("_result_content_removed")) remove.add(key)
            }
        }
        remove.forEach { database.delete("owner_state", "key=?", arrayOf(it)) }
        val nativeReceipts = mutableListOf<String>()
        database.rawQuery("SELECT id,payload FROM pending WHERE kind='mcp_receipt'", null).use { rows ->
            while (rows.moveToNext()) {
                val receipt = JSONObject(rows.getString(1))
                if ((receipt.optString("entity_type") + ":" + receipt.optString("entity_id")) in deleted) nativeReceipts.add(rows.getString(0))
            }
        }
        nativeReceipts.forEach { database.delete("pending", "id=?", arrayOf(it)) }
        database.rawQuery("SELECT key FROM owner_state WHERE key GLOB 'photo-ui:*'", null).use { rows ->
            val photos = mutableListOf<String>()
            while (rows.moveToNext()) {
                val key = rows.getString(0)
                if (("photo:" + JSONObject(store.ownerValue(key, "{}")).optString("id")) in deleted) photos.add(key)
            }
            photos.forEach { database.delete("owner_state", "key=?", arrayOf(it)) }
        }
    }

    private fun removeUnreferenced(root: File, refs: Set<String>, grace: Long): Int {
        if (!root.exists()) return 0
        if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory) throw IOException("STORAGE_DIRECTORY_INVALID")
        var count = 0
        root.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
            if (file == root || Files.isSymbolicLink(file.toPath()) || !file.isFile) return@forEach
            if (file.name in refs || file.nameWithoutExtension in refs || file.path in refs || file.toURI().toString() in refs || file.parentFile?.name in refs) return@forEach
            if (grace > 0 && file.lastModified() > System.currentTimeMillis() - grace) return@forEach
            if (!file.delete() && file.exists()) throw IOException("STORAGE_FILE_CLEANUP_PENDING")
            count++
        }
        return count
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
        private fun parse(value: String): Any? {
            if (value.isBlank()) return null
            if (!value.trimStart().let { it.startsWith("{") || it.startsWith("[") || it.startsWith("\"") }) return value
            return try { JSONTokener(value).nextValue() }
            catch (failure: Exception) { throw IOException("STORAGE_REFERENCE_UNREADABLE", failure) }
        }
        private fun collect(value: Any?, refs: MutableSet<String>) {
            when (value) {
                is JSONObject -> for (key in value.keys()) collect(value.opt(key), refs)
                is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i), refs)
                is String -> {
                    refs.add(value)
                    if (value.trimStart().startsWith("{") || value.trimStart().startsWith("[")) {
                        val embedded = parse(value)
                        if (embedded !is String) collect(embedded, refs)
                    }
                    if ('/' in value) refs.add(value.substringAfterLast('/').substringBefore('?'))
                }
            }
        }
        private fun bytes(root: File): Long {
            if (Files.isSymbolicLink(root.toPath())) return 0
            if (root.isFile) return root.length()
            return root.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }
                .filter { !Files.isSymbolicLink(it.toPath()) && it.isFile }.sumOf { it.length() }
        }
    }
}
