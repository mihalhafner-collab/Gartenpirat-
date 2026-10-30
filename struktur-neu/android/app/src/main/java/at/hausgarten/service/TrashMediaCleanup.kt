package at.hausgarten.service

import android.content.Context
import org.json.JSONObject

/** Retryable cleanup after a verified publication and local snapshot commit, never before. */
class TrashMediaCleanup private constructor() {
    interface Files {
        @Throws(Exception::class) fun deletePhotoFileVerified(name: String, hash: String)
        @Throws(Exception::class) fun deleteDocumentFileVerified(document: JSONObject)
    }

    companion object {
        @JvmStatic @Throws(Exception::class)
        fun runLocal(context: Context, store: PixelLocalStore): JSONObject {
            if (!LocalAuthorityGuard.local(context)) return JSONObject().put("pending", 0).put("completed", 0).put("state", "legacy_receipts_required")
            return run(store, object : Files {
                override fun deletePhotoFileVerified(name: String, hash: String) {}
                override fun deleteDocumentFileVerified(document: JSONObject) {}
            }, PixelMcpReadTools.effective(store.getState()))
        }

        @JvmStatic @Throws(Exception::class)
        fun run(store: PixelLocalStore, files: Files, committed: JSONObject): JSONObject {
            val archive = OwnerJournalRules.archives(committed)
            var pending = 0
            var done = 0
            var error = ""
            val keys = archive.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val tombstone = archive.getJSONObject(key)
                if (!tombstone.optBoolean("purged") || !tombstone.has("media") && !tombstone.has("document")) continue
                val marker = "purge-cleaned:" + tombstone.getString("purge_action")
                if (store.ownerValue(marker, "") == "done") continue
                pending++
                if (done >= 4) continue
                try {
                    if (tombstone.has("document")) {
                        val document = tombstone.getJSONObject("document")
                        val current = PixelMcpReadTools.effective(store.getState())
                        val queued = store.pendingActions()
                        if (!TrashRules.referenced(current, queued, document.optString("pdf_storage_name"))) files.deleteDocumentFileVerified(document)
                        store.cleanupPurgedDocument(key, document, marker)
                        pending--; done++
                        continue
                    }
                    val media = tombstone.getJSONObject("media")
                    val current = PixelMcpReadTools.effective(store.getState())
                    val queued = store.pendingActions()
                    val original = media.optString("storage_name")
                    val thumb = media.optString("thumbnail_storage_name")
                    if (!TrashRules.referenced(current, queued, original) && !store.hasOtherPhotoAttachment(key.substring(6), media.optString("content_sha256"))) files.deletePhotoFileVerified(original, media.getString("content_sha256"))
                    if (thumb.isNotEmpty() && !TrashRules.referenced(current, queued, thumb)) files.deletePhotoFileVerified(thumb, media.getString("thumbnail_sha256"))
                    // Recheck under SQLite's writer lock before reclaiming local bytes.
                    store.cleanupPurgedPhoto(key.substring(6), media, marker)
                    pending--; done++
                } catch (failure: Exception) { error = SyncFailureRules.failureCode(failure) }
            }
            return JSONObject().put("pending", pending).put("completed", done).put("error_code", error)
        }
    }
}
