package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Describes existing defects. These observations never decide whether bytes can be backed up. */
object BackupInventory {
    fun interface Originals {
        @Throws(Exception::class)
        fun problem(kind: String, row: JSONObject): String?
    }

    @JvmStatic
    @Throws(Exception::class)
    fun inspect(effective: JSONObject, originals: Originals): JSONObject {
        val counts = JSONObject()
        val warnings = JSONArray()
        val issued = JSONArray()
        val inspected = HashSet<String>()
        for (collection in arrayOf("customers", "orders", "worklogs", "photos", "offers", "invoices", "appointments")) {
            val rows = effective.optJSONArray(collection)
            counts.put(collection, rows?.length() ?: 0)
            var i = 0
            while (rows != null && i < rows.length()) {
                val row = rows.optJSONObject(i++) ?: continue
                val document = collection == "offers" && row.optString("finalized_at").isNotEmpty()
                if (collection == "photos" || document) {
                    inspectRow(if (collection == "photos") "photo" else "document", row, originals, warnings, inspected)
                }
            }
        }
        val archive = effective.optJSONObject("owner_archive")
        if (archive != null) {
            val keys = archive.keys()
            while (keys.hasNext()) {
                val entry = archive.optJSONObject(keys.next()) ?: continue
                if (entry.optBoolean("purged")) continue
                val row = entry.optJSONObject("record") ?: continue
                val kind = when {
                    row.has("content_sha256") || row.has("filename") -> "photo"
                    row.has("pdf_sha256") || row.has("pdf_storage_name") || row.has("document_job_id") -> "document"
                    else -> ""
                }
                if (kind.isNotEmpty()) inspectRow(kind, row, originals, warnings, inspected)
            }
        }
        return JSONObject().put("counts", counts).put("warnings", warnings).put("lastIssuedDocumentIds", issued)
    }

    private fun inspectRow(kind: String, row: JSONObject, originals: Originals, warnings: JSONArray, inspected: MutableSet<String>) {
        val id = row.optString("id")
        val identity = "$kind:$id"
        if (id.isNotEmpty() && !inspected.add(identity)) return
        val problem = try {
            originals.problem(kind, row)
        } catch (unavailable: Exception) {
            "ORIGINAL_NOT_VERIFIABLE"
        }
        if (problem.isNullOrEmpty()) return
        warnings.put(JSONObject().put("kind", kind).put("record_id", id).put("reason", problem)
            .put("message", if (kind == "photo") "Fotooriginal fehlt oder ist nicht verifiziert; vorhandene Dateien bleiben enthalten."
                else "Dokumentoriginal fehlt oder ist nicht verifiziert; vorhandene Dateien bleiben enthalten."))
    }

    /** Only verify local originals; missing files are backup notes, not authority gates. */
    @JvmStatic
    fun originalProblem(filesRoot: java.io.File, kind: String, row: JSONObject): String? {
        val photo = kind == "photo"
        val name = row.optString(if (photo) "storage_name" else "pdf_storage_name")
        val sha = row.optString(if (photo) "content_sha256" else "pdf_sha256")
        val bytes = row.optLong(if (photo) "byte_size" else "pdf_byte_size", -1)
        val allowed = if (photo) Regex("[a-f0-9]{64}\\.[A-Za-z0-9]{1,8}")
            else Regex("[a-f0-9]{64}\\.pdf")
        if (!allowed.matches(name) || !Regex("[a-f0-9]{64}").matches(sha) || bytes < 0)
            return "ORIGINAL_METADATA_INCOMPLETE"
        val root = java.io.File(filesRoot, if (photo) "customer-photo-originals" else "business-documents")
        val file = java.io.File(root, name)
        if (!file.isFile || java.nio.file.Files.isSymbolicLink(file.toPath())) return "ORIGINAL_FILE_MISSING"
        if (file.length() != bytes) return "ORIGINAL_FILE_SIZE_MISMATCH"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        return if (hash == sha) null else "ORIGINAL_HASH_MISMATCH"
    }

    @JvmStatic
    fun message(warnings: JSONArray?): String {
        val count = warnings?.length() ?: 0
        return if (count == 0) "" else " $count Hinweise zum Ausgangsbestand. Details stehen in der ZIP-Datei unter data/backup-notes.json."
    }
}
