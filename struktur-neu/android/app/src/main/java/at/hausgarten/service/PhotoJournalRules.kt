package at.hausgarten.service

import org.json.JSONObject

/** Attachment cache must never rewrite an immutable metadata change or its role/hash. */
class PhotoJournalRules private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun forSnapshot(original: JSONObject, metadata: JSONObject?): JSONObject {
            val p = JSONObject(original.toString())
            val legacy = p.has("data") || p.has("full_data")
            if (legacy && metadata != null) {
                for (key in
                    arrayOf(
                        "content_sha256",
                        "mime_type",
                        "byte_size",
                        "storage_name",
                        "filename",
                        "role",
                    )) {
                    if (!p.has(key) && metadata.has(key)) p.put(key, metadata.get(key))
                }
            }
            p.remove("data")
            p.remove("full_data")
            return p
        }
    }
}
