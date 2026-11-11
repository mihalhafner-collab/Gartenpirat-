package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Read-only inventory and logical comparison of captured originals; never business authority. */
object BackupInventory {
    fun interface Originals {
        @Throws(Exception::class) fun problem(kind: String, row: JSONObject): String?
    }

    @JvmStatic
    @Throws(Exception::class)
    fun inspect(effective: JSONObject, originals: Originals): JSONObject {
        val counts = JSONObject()
        val warnings = JSONArray()
        val issued = JSONArray()
        val inspected = HashSet<String>()
        for (collection in
            arrayOf(
                "customers",
                "orders",
                "worklogs",
                "photos",
                "offers",
                "invoices",
                "appointments",
                "settlements",
            )) {
            val rows = effective.optJSONArray(collection)
            counts.put(collection, rows?.length() ?: 0)
            var i = 0
            while (rows != null && i < rows.length()) {
                val row = rows.optJSONObject(i++) ?: continue
                val issuedSettlement =
                    collection == "settlements" &&
                        row.optString("status") in setOf("confirmed", "cancelled") &&
                        row.optString("kind") in setOf("invoice", "cancellation")
                val document =
                    collection == "offers" && row.optString("finalized_at").isNotEmpty() ||
                        issuedSettlement
                if (issuedSettlement) issued.put(row.getString("id"))
                if (collection == "photos" || document) {
                    inspectRow(
                        if (collection == "photos") "photo" else "document",
                        row,
                        originals,
                        warnings,
                        inspected,
                    )
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
                val kind =
                    when {
                        row.has("content_sha256") || row.has("filename") -> "photo"
                        row.has("pdf_sha256") ||
                            row.has("pdf_storage_name") ||
                            row.has("document_job_id") -> "document"
                        else -> ""
                    }
                if (kind.isNotEmpty()) inspectRow(kind, row, originals, warnings, inspected)
            }
        }
        return JSONObject()
            .put("counts", counts)
            .put("warnings", warnings)
            .put("lastIssuedDocumentIds", issued)
    }

    /** Stable original identity is the source collection plus its original ID, never position. */
    @JvmStatic
    fun logicalRecords(effective: JSONObject): JSONObject {
        val index = JSONObject().put("formatVersion", 1).put("collections", JSONObject())
        for (source in
            arrayOf(
                "customers",
                "orders",
                "worklogs",
                "photos",
                "offers",
                "invoices",
                "appointments",
                "settlements",
                "service_catalog",
            )) addLogicalRecords(index, source, effective.optJSONArray(source) ?: JSONArray(), "id")
        val archived = effective.optJSONObject("owner_archive")
        if (archived != null) {
            val records = JSONObject()
            archived.keys().forEachRemaining { id ->
                records.put(id, PixelMcpWriteTools.hash(archived.get(id)))
            }
            index.getJSONObject("collections").put("owner_archive", records)
        }
        return index
    }

    @JvmStatic
    fun addLogicalRecords(index: JSONObject, source: String, rows: JSONArray, idField: String) {
        val records = JSONObject()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.optString(idField)
            if (id.isEmpty() || records.has(id))
                throw java.io.IOException("BACKUP_LOGICAL_IDENTITY_INVALID")
            records.put(id, PixelMcpWriteTools.hash(row))
        }
        val collections = index.getJSONObject("collections")
        if (collections.has(source)) throw java.io.IOException("BACKUP_LOGICAL_IDENTITY_INVALID")
        collections.put(source, records)
    }

    /** File hashes come from the bytes actually streamed into the complete, verified ZIP. */
    @JvmStatic
    fun logicalIndex(manifest: JSONObject): JSONObject? {
        val records = manifest.optJSONObject("logical_records") ?: return null
        if (records.optInt("formatVersion") != 1) return null
        val files = JSONObject()
        val entries = manifest.getJSONArray("files")
        for (i in 0 until entries.length()) {
            val file = entries.getJSONObject(i)
            files.put(
                file.getString("path"),
                JSONObject()
                    .put("sha256", file.getString("sha256"))
                    .put("size", file.getLong("size")),
            )
        }
        return JSONObject()
            .put("formatVersion", 1)
            .put("records", JSONObject(records.getJSONObject("collections").toString()))
            .put("files", files)
    }

    /** A missing/foreign/old index is a new full basis, never fabricated added/deleted rows. */
    @JvmStatic
    fun compareLogical(
        current: JSONObject?,
        previous: JSONObject?,
        destination: String,
        identity: JSONObject,
        previousHash: String,
        previousSize: Long,
    ): JSONObject {
        val result = JSONObject().put("formatVersion", 1).put("physical_write", "FULL_SNAPSHOT")
        if (current == null || !validLogicalIndex(current))
            return result.put("mode", "UNAVAILABLE").put("reason", "CURRENT_INDEX_UNAVAILABLE")
        result
            .put("record_count", recordCount(current))
            .put("file_count", current.getJSONObject("files").length())
        val archive = previous?.optJSONObject("archive")
        val proof = previous?.optJSONObject("provider")
        val old = previous?.optJSONObject("logical_index")
        if (
            previous == null ||
                old == null ||
                !validLogicalIndex(old) ||
                previous.optString("phase") != "PROVIDER_VERIFIED" ||
                previous.optJSONObject("destination")?.optString("treeUri") != destination ||
                previous.optString("installationId") != identity.optString("installationId") ||
                previous.optString("restoreEpoch") != identity.optString("restoreEpoch") ||
                !previousHash.matches(Regex("[a-f0-9]{64}")) ||
                previousSize <= 0 ||
                archive?.optString("sha256") != previousHash ||
                archive.optLong("size", -1) != previousSize ||
                proof?.optString("backup_id") != previous.optString("backupId") ||
                proof?.optString("verification_scope") != "DOCUMENT_PROVIDER_READBACK" ||
                proof.optString("archive_sha256") != previousHash ||
                proof.optLong("byte_size", -1) != previousSize ||
                proof.optString("logical_index_sha256") != PixelMcpWriteTools.hash(old)
        )
            return result.put("mode", "FULL_BASELINE").put("reason", "NO_VERIFIED_INDEX_FOR_TARGET")
        val sources = java.util.TreeSet<String>()
        val before = old.getJSONObject("records")
        val after = current.getJSONObject("records")
        before.keys().forEachRemaining(sources::add)
        after.keys().forEachRemaining(sources::add)
        val records = counts()
        val bySource = JSONObject()
        for (source in sources) {
            val changes =
                compareEntries(
                    before.optJSONObject(source) ?: JSONObject(),
                    after.optJSONObject(source) ?: JSONObject(),
                )
            bySource.put(source, changes)
            for (kind in arrayOf("added", "changed", "deleted", "unchanged")) records.put(
                kind,
                records.getInt(kind) + changes.getInt(kind),
            )
        }
        return result
            .put("mode", "INCREMENTAL")
            .put("basis_backup_id", previous.getString("backupId"))
            .put("basis_archive_sha256", previousHash)
            .put("basis_archive_size", previousSize)
            .put("records", records.put("by_source", bySource))
            .put(
                "files",
                compareEntries(old.getJSONObject("files"), current.getJSONObject("files")),
            )
    }

    private fun counts() =
        JSONObject().put("added", 0).put("changed", 0).put("deleted", 0).put("unchanged", 0)

    private fun compareEntries(before: JSONObject, after: JSONObject): JSONObject {
        val result = counts()
        after.keys().forEachRemaining { id ->
            val kind =
                if (!before.has(id)) "added"
                else if (
                    PixelMcpWriteTools.hash(before.get(id)) ==
                        PixelMcpWriteTools.hash(after.get(id))
                )
                    "unchanged"
                else "changed"
            result.put(kind, result.getInt(kind) + 1)
        }
        before.keys().forEachRemaining { id ->
            if (!after.has(id)) result.put("deleted", result.getInt("deleted") + 1)
        }
        return result
    }

    private fun recordCount(index: JSONObject): Int {
        var total = 0
        val records = index.getJSONObject("records")
        records.keys().forEachRemaining { source ->
            total += records.getJSONObject(source).length()
        }
        return total
    }

    private fun validLogicalIndex(index: JSONObject): Boolean =
        try {
            require(index.getInt("formatVersion") == 1)
            val records = index.getJSONObject("records")
            records.keys().forEachRemaining { source ->
                val rows = records.getJSONObject(source)
                rows.keys().forEachRemaining { id ->
                    require(id.isNotEmpty() && rows.getString(id).matches(Regex("[a-f0-9]{64}")))
                }
            }
            val files = index.getJSONObject("files")
            files.keys().forEachRemaining { path ->
                val file = files.getJSONObject(path)
                require(
                    file.getString("sha256").matches(Regex("[a-f0-9]{64}")) &&
                        file.getLong("size") >= 0
                )
            }
            true
        } catch (_: Exception) {
            false
        }

    private fun inspectRow(
        kind: String,
        row: JSONObject,
        originals: Originals,
        warnings: JSONArray,
        inspected: MutableSet<String>,
    ) {
        val id = row.optString("id")
        val identity = "$kind:$id"
        if (id.isNotEmpty() && !inspected.add(identity)) return
        val problem =
            try {
                originals.problem(kind, row)
            } catch (unavailable: Exception) {
                "ORIGINAL_NOT_VERIFIABLE"
            }
        if (problem.isNullOrEmpty()) return
        warnings.put(
            JSONObject()
                .put("kind", kind)
                .put("record_id", id)
                .put("reason", problem)
                .put(
                    "message",
                    if (kind == "photo")
                        "Fotooriginal fehlt oder ist nicht verifiziert; vorhandene Dateien bleiben enthalten."
                    else
                        "Dokumentoriginal fehlt oder ist nicht verifiziert; vorhandene Dateien bleiben enthalten.",
                )
        )
    }

    /** Only verify local originals; missing files are backup notes, not authority gates. */
    @JvmStatic
    fun originalProblem(filesRoot: java.io.File, kind: String, row: JSONObject): String? {
        val photo = kind == "photo"
        val metadata = if (photo) row else row.optJSONObject("pdf") ?: row
        val name = metadata.optString(if (photo) "storage_name" else "pdf_storage_name")
        val sha = metadata.optString(if (photo) "content_sha256" else "pdf_sha256")
        val bytes = metadata.optLong(if (photo) "byte_size" else "pdf_byte_size", -1)
        val allowed =
            if (photo) Regex("[a-f0-9]{64}\\.[A-Za-z0-9]{1,8}") else Regex("[a-f0-9]{64}\\.pdf")
        if (!allowed.matches(name) || !Regex("[a-f0-9]{64}").matches(sha) || bytes < 0)
            return "ORIGINAL_METADATA_INCOMPLETE"
        val root =
            java.io.File(filesRoot, if (photo) "customer-photo-originals" else "business-documents")
        val file = java.io.File(root, name)
        if (!file.isFile || java.nio.file.Files.isSymbolicLink(file.toPath()))
            return "ORIGINAL_FILE_MISSING"
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

    /** Short limitation belongs in the main result, with fuller details available separately. */
    @JvmStatic
    fun warningSummary(warnings: JSONArray?, fallbackCount: Int): String {
        val count = maxOf(fallbackCount, warnings?.length() ?: 0)
        if (count == 0) return ""
        var photos = 0
        var documents = 0
        if (warnings != null)
            for (i in 0 until warnings.length()) {
                when (warnings.optJSONObject(i)?.optString("kind")) {
                    "photo" -> photos++
                    "document" -> documents++
                }
            }
        val parts = ArrayList<String>()
        if (photos > 0)
            parts.add(
                if (photos == 1) "1 Foto fehlt oder ist ungeprüft"
                else "$photos Fotos fehlen oder sind ungeprüft"
            )
        if (documents > 0)
            parts.add(
                if (documents == 1) "1 Dokument fehlt oder ist ungeprüft"
                else "$documents Dokumente fehlen oder sind ungeprüft"
            )
        val remaining = count - photos - documents
        if (remaining > 0)
            parts.add(
                if (parts.isEmpty()) {
                    if (remaining == 1) "1 Hinweis" else "$remaining Hinweise"
                } else {
                    if (remaining == 1) "1 weiterer Hinweis" else "$remaining weitere Hinweise"
                }
            )
        return parts.joinToString(" · ")
    }

    @JvmStatic
    fun message(warnings: JSONArray?): String {
        val summary = warningSummary(warnings, 0)
        return if (summary.isEmpty()) ""
        else " $summary. Details stehen in der ZIP-Datei unter data/backup-notes.json."
    }
}
