package at.hausgarten.service

import org.json.JSONObject

/** Local deletion protection preserves changed originals and current server evidence. */
internal class LocalTestTombstone private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun project(master: JSONObject, key: String, receipt: JSONObject): Boolean {
            val split = key.indexOf(':')
            if (split <= 0) throw IllegalArgumentException("Ungültiger lokaler Löschbeleg.")
            val entity = key.substring(0, split)
            val id = key.substring(split + 1)
            if (!receipt.optBoolean("purged") || receipt.has("record"))
                throw IllegalArgumentException("Ungültiger lokaler Löschbeleg.")
            val remote = master.optJSONObject("owner_archive")?.optJSONObject(key)
            if (remote != null && remote.optBoolean("purged")) return false
            val record =
                OwnerJournalRules.findOrNull(
                    master.optJSONArray(OwnerJournalRules.group(entity)),
                    id,
                )
            if (
                record != null &&
                    OwnerJournalRules.version(record) != receipt.optString("source_record_hash")
            )
                return false
            if (
                remote != null &&
                    OwnerJournalRules.version(remote) != receipt.optString("source_archive_hash")
            )
                return false
            val tombstone = JSONObject(receipt.toString())
            for (privateKey in
                arrayOf("local_test_only", "source_record_hash", "source_archive_hash")) tombstone
                .remove(privateKey)
            OwnerJournalRules.replace(master, OwnerJournalRules.group(entity), id, null)
            OwnerJournalRules.archives(master).put(key, tombstone)
            return true
        }
    }
}
