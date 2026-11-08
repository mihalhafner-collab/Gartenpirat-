package at.hausgarten.service

import java.util.*
import org.json.*

/** Shared local UI/MCP trash policy. Test cleanup includes owned test dependencies. */
class TrashRules {
    private constructor() {}

    companion object {
        private fun existingArchive(s: JSONObject): JSONObject {
            val a: JSONObject? = s.optJSONObject("owner_archive")
            return (if (a == null) JSONObject() else a)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun purged(s: JSONObject, entity: String, id: String): Boolean {
            val a: JSONObject? = existingArchive(s).optJSONObject(("" + entity + ":") + id)
            return ((a != null) && a.optBoolean("purged"))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun links(photo: JSONObject, entity: String, id: String): JSONObject {
            val fields: JSONObject = JSONObject()
            if (entity.equals("customer") && id.equals(photo.optString("customer_id"))) {
                for (key in
                    arrayOf(
                        "customer_id",
                        "order_id",
                        "worklog_id",
                        "session_id",
                        "role",
                        "customer_visible",
                    )) {
                    fields.put(key, (if (photo.has(key)) photo.get(key) else JSONObject.NULL))
                }
            } else {
                if (entity.equals("order") && id.equals(photo.optString("order_id"))) {
                    fields.put("order_id", photo.get("order_id"))
                } else {
                    if (entity.equals("worklog") && id.equals(photo.optString("worklog_id"))) {
                        fields.put("worklog_id", photo.get("worklog_id"))
                        fields.put(
                            "session_id",
                            (if (photo.opt("session_id") == null) JSONObject.NULL
                            else photo.get("session_id")),
                        )
                    }
                }
            }
            return fields
        }

        @Throws(Exception::class)
        @JvmStatic
        fun related(s: JSONObject, entity: String, id: String): JSONArray {
            val sorted: TreeMap<String, JSONObject> = TreeMap()
            val photos: JSONArray = s.getJSONArray("photos")
            run {
                var i: Int = 0
                while (i < photos.length()) {
                    var p: JSONObject? = photos.getJSONObject(i)
                    if (links((p)!!, entity, id).length() > 0) {
                        sorted.put(
                            p.getString("id"),
                            JSONObject()
                                .put("id", p.getString("id"))
                                .put("hash", OwnerJournalRules.version(p)),
                        )
                    }
                    i++
                }
            }
            val archive: JSONObject = existingArchive(s)
            run {
                val it: MutableIterator<String> = archive.keys()
                while (it.hasNext()) {
                    val key: String = it.next()
                    val entry: JSONObject = archive.getJSONObject(key)
                    var p: JSONObject? = entry.optJSONObject("record")
                    if (
                        ((key.startsWith("photo:") && (p != null)) &&
                            (links(p, entity, id).length() > 0))
                    ) {
                        sorted.put(
                            ("archive:" + p.getString("id")),
                            JSONObject()
                                .put("id", p.getString("id"))
                                .put("hash", OwnerJournalRules.version(entry))
                                .put("location", "archive"),
                        )
                    }
                }
            }
            return JSONArray(sorted.values)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun detached(p: JSONObject, fields: JSONObject, stamp: String): JSONObject {
            val next: JSONObject = JSONObject(p.toString())
            run {
                val it: MutableIterator<String> = fields.keys()
                while (it.hasNext()) {
                    val key: String = it.next()
                    next.put(
                        key,
                        (if (key.equals("role")) "documentation"
                        else (if (key.equals("customer_visible")) 0 else "")),
                    )
                }
            }
            return next.put("updated_at", stamp)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun detachRelated(s: JSONObject, entity: String, id: String, stamp: String): JSONArray {
            val saved: JSONArray = JSONArray()
            val rows: JSONArray = s.getJSONArray("photos")
            run {
                var i: Int = 0
                while (i < rows.length()) {
                    var p: JSONObject? = rows.getJSONObject(i)
                    val fields: JSONObject = links((p)!!, entity, id)
                    if (fields.length() == 0) {
                        i++
                        continue
                    }
                    val next: JSONObject = detached((p)!!, fields, stamp)
                    saved.put(
                        JSONObject()
                            .put("id", p.getString("id"))
                            .put("fields", fields)
                            .put("detached_hash", OwnerJournalRules.version(next))
                    )
                    rows.put(i, next)
                    i++
                }
            }
            val archive: JSONObject = OwnerJournalRules.archives(s)
            run {
                val it: MutableIterator<String> = archive.keys()
                while (it.hasNext()) {
                    val key: String = it.next()
                    val entry: JSONObject = archive.getJSONObject(key)
                    var p: JSONObject? = entry.optJSONObject("record")
                    if (!key.startsWith("photo:") || (p == null)) {
                        continue
                    }
                    val fields: JSONObject = links(p, entity, id)
                    if (fields.length() == 0) {
                        continue
                    }
                    entry.put("record", detached(p, fields, stamp))
                    saved.put(
                        JSONObject()
                            .put("id", p.getString("id"))
                            .put("fields", fields)
                            .put("location", "archive")
                            .put("detached_hash", OwnerJournalRules.version(entry))
                    )
                }
            }
            return saved
        }

        @Throws(Exception::class)
        @JvmStatic
        fun restoreLinks(s: JSONObject, archive: JSONObject) {
            val links: JSONArray? = archive.optJSONArray("photo_links")
            run {
                var i: Int = 0
                while ((links != null) && (i < links.length())) {
                    val link: JSONObject = links.getJSONObject(i)
                    val archived: Boolean = "archive".equals(link.optString("location"))
                    var entry: JSONObject? =
                        (if (archived)
                            OwnerJournalRules.archives(s)
                                .optJSONObject("photo:" + link.getString("id"))
                        else null)
                    var p: JSONObject? =
                        (if (archived) (if (entry == null) null else entry.optJSONObject("record"))
                        else
                            OwnerJournalRules.findOrNull(
                                s.getJSONArray("photos"),
                                link.getString("id"),
                            ))
                    if (
                        ((p == null) ||
                            !OwnerJournalRules.version(if (archived) entry else p)
                                .equals(link.getString("detached_hash")))
                    ) {
                        i++
                        continue
                    }
                    val restored: JSONObject = JSONObject(p!!.toString())
                    val fields: JSONObject = link.getJSONObject("fields")
                    run {
                        val it: MutableIterator<String> = fields.keys()
                        while (it.hasNext()) {
                            val key: String = it.next()
                            restored.put(key, fields.get(key))
                        }
                    }
                    try {
                        EntityProtectionRules.validatePhoto(
                            restored,
                            s.getJSONArray("customers"),
                            s.getJSONArray("orders"),
                            s.getJSONArray("worklogs"),
                        )
                    } catch (changedRelation: IllegalArgumentException) {
                        i++
                        continue
                    }
                    if (archived) {
                        entry!!.put("record", restored)
                    } else {
                        OwnerJournalRules.replace(s, "photos", p.getString("id"), restored)
                    }
                    i++
                }
            }
        }

        /** Optional planning links never own the work or its customer. */
        @JvmStatic
        fun detachOptionalParent(s: JSONObject, entity: String, id: String, stamp: String) {
            val field =
                when (entity) {
                    "order" -> "order_id"
                    "appointment" -> "appointment_id"
                    else -> return
                }
            fun detach(row: JSONObject) {
                if (row.optString(field) == id) row.put(field, "").put("updated_at", stamp)
            }
            for (group in
                arrayOf("orders", "worklogs", "photos", "invoices", "offers", "appointments")) {
                val rows = s.optJSONArray(group) ?: continue
                for (i in 0 until rows.length()) detach(rows.getJSONObject(i))
            }
            val archive = existingArchive(s)
            for (key in archive.keys().asSequence().toList()) {
                if (key != "$entity:$id")
                    archive.optJSONObject(key)?.optJSONObject("record")?.let(::detach)
            }
        }

        @JvmStatic
        fun requireDeletableParent(s: JSONObject, entity: String, id: String) {
            val field =
                when (entity) {
                    "order" -> "order_id"
                    "appointment" -> "appointment_id"
                    else -> return
                }
            val workIds = HashSet<String>()
            val work = s.optJSONArray("worklogs") ?: JSONArray()
            for (i in 0 until work.length()) if (work.getJSONObject(i).optString(field) == id)
                workIds.add(work.getJSONObject(i).getString("id"))
            val archive = existingArchive(s)
            for (key in archive.keys().asSequence().toList()) {
                if (key.startsWith("worklog:"))
                    archive.optJSONObject(key)?.optJSONObject("record")?.let {
                        if (it.optString(field) == id) workIds.add(it.getString("id"))
                    }
            }
            fun protect(row: JSONObject, invoice: Boolean) {
                val linked = row.optString(field) == id || workIds.any { containsWork(row, it) }
                if (!linked) return
                if (invoice) EntityProtectionRules.validateInvoiceDelete(row)
                else if (!OfferRules.editable(row))
                    throw IllegalArgumentException(
                        "Der Eintrag gehört zu einem abgeschlossenen Angebot und bleibt als Belegbezug erhalten."
                    )
            }
            for (group in arrayOf("invoices", "offers")) {
                val rows = s.optJSONArray(group) ?: JSONArray()
                for (i in 0 until rows.length()) protect(rows.getJSONObject(i), group == "invoices")
            }
            for (key in archive.keys().asSequence().toList()) {
                if (key.startsWith("invoice:") || key.startsWith("offer:"))
                    archive.optJSONObject(key)?.optJSONObject("record")?.let {
                        protect(it, key.startsWith("invoice:"))
                    }
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun requireUnreferenced(s: JSONObject, entity: String, id: String) {
            var ref: String =
                (if (entity.equals("customer")) "customer_id"
                else
                    (if (entity.equals("order")) "order_id"
                    else
                        (if (entity.equals("worklog")) "worklog_id"
                        else
                            (if (entity.equals("offer")) "offer_id"
                            else (if (entity.equals("appointment")) "appointment_id" else "")))))
            if (ref.isEmpty()) {
                return
            }
            for (group in
                arrayOf(
                    "customers",
                    "orders",
                    "worklogs",
                    "photos",
                    "invoices",
                    "offers",
                    "appointments",
                )) {
                val rows: JSONArray? = s.optJSONArray(group)
                if ((rows == null) && group.equals("appointments")) {
                    continue
                }
                if (rows == null) {
                    throw IllegalArgumentException("Unvollständiger Geschäftsstand.")
                }
                run {
                    var i: Int = 0
                    while (i < rows.length()) {
                        val row: JSONObject = rows.getJSONObject(i)
                        if (
                            (group.equals(OwnerJournalRules.group(entity)) &&
                                id.equals(row.optString("id")))
                        ) {
                            i++
                            continue
                        }
                        if (
                            ((id.equals(EntityProtectionRules.text(row, ref)) ||
                                (entity.equals("customer") &&
                                    (id.equals(row.optString("billing_customer_id")) ||
                                        id.equals(
                                            row.optString("default_billing_customer_id")
                                        )))) || (entity.equals("worklog") && containsWork(row, id)))
                        ) {
                            throw IllegalArgumentException(
                                ("" +
                                    ("Noch verknüpfte " + group) +
                                    " vorhanden. Zuordnung zuerst gezielt lösen; keine Kaskadenlöschung.")
                            )
                        }
                        i++
                    }
                }
            }
            val archive: JSONObject = OwnerJournalRules.archives(s)
            run {
                val it: MutableIterator<String> = archive.keys()
                while (it.hasNext()) {
                    val key: String = it.next()
                    if (key.equals(("" + entity + ":") + id)) {
                        continue
                    }
                    val r: JSONObject? = archive.getJSONObject(key).optJSONObject("record")
                    if (r == null) {
                        continue
                    }
                    var billing: Boolean =
                        (entity.equals("customer") &&
                            (id.equals(r.optString("billing_customer_id")) ||
                                id.equals(r.optString("default_billing_customer_id"))))
                    if (
                        ((id.equals(r.optString(ref)) || billing) ||
                            (entity.equals("worklog") && containsWork(r, id)))
                    ) {
                        throw IllegalArgumentException(
                            "Ein anderer Papierkorbeintrag verweist darauf. Diesen zuerst wiederherstellen und die Zuordnung lösen oder einzeln löschen."
                        )
                    }
                }
            }
        }

        private fun containsWork(row: JSONObject, id: String): Boolean {
            val refs: JSONArray? = row.optJSONArray("worklog_ids")
            val positions: JSONArray? = row.optJSONArray("positions")
            run {
                var i: Int = 0
                while ((refs != null) && (i < refs.length())) {
                    if (id.equals(refs.optString(i))) {
                        return true
                    }
                    i++
                }
            }
            run {
                var i: Int = 0
                while ((positions != null) && (i < positions.length())) {
                    if (
                        (id.equals(
                            (if (positions.optJSONObject(i) == null) ""
                            else positions.optJSONObject(i).optString("worklog_id"))
                        ))
                    ) {
                        return true
                    }
                    i++
                }
            }
            return false
        }

        private val collections =
            linkedMapOf(
                "customer" to "customers",
                "order" to "orders",
                "worklog" to "worklogs",
                "photo" to "photos",
                "invoice" to "invoices",
                "offer" to "offers",
                "appointment" to "appointments",
                "service_template" to "service_catalog",
            )

        @JvmStatic
        fun testRecord(s: JSONObject, entity: String, row: JSONObject): Boolean {
            if (
                row.optBoolean("test_record") ||
                    listOf("name", "title", "activity", "caption").any {
                        row.optString(it).startsWith("[TEST]")
                    }
            )
                return true
            if (entity == "customer") return TestDataRules.customer(row)
            val customerId = row.optString("customer_id")
            val customer =
                OwnerJournalRules.findOrNull(s.optJSONArray("customers"), customerId)
                    ?: existingArchive(s)
                        .optJSONObject("customer:$customerId")
                        ?.optJSONObject("record")
            return TestDataRules.customer(customer)
        }

        private fun refers(row: JSONObject, entity: String, id: String): Boolean =
            when (entity) {
                "customer" ->
                    listOf("customer_id", "billing_customer_id", "default_billing_customer_id")
                        .any { row.optString(it) == id }
                "worklog" -> row.optString("worklog_id") == id || containsWork(row, id)
                "order",
                "appointment",
                "offer" -> row.optString("${entity}_id") == id
                else -> false
            }

        /** Exact current versions of the test descendants; ancestors are never selected. */
        @JvmStatic
        fun testTargets(s: JSONObject, entity: String, id: String): JSONArray {
            val archive = existingArchive(s)
            val rootKey = "$entity:$id"
            val root = archive.optJSONObject(rootKey)?.optJSONObject("record") ?: return JSONArray()
            if (!testRecord(s, entity, root)) return JSONArray()
            val records = sortedMapOf<String, JSONObject>()
            val proofs = sortedMapOf<String, JSONObject>()
            for ((type, group) in collections) {
                val rows = s.optJSONArray(group) ?: JSONArray()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val key = "$type:${row.getString("id")}"
                    records[key] = row
                    proofs[key] =
                        JSONObject()
                            .put("entity", type)
                            .put("entity_id", row.getString("id"))
                            .put("location", "active")
                            .put("hash", OwnerJournalRules.version(row))
                }
            }
            for (key in archive.keys().asSequence().sorted()) {
                val saved = archive.getJSONObject(key)
                val row = saved.optJSONObject("record") ?: continue
                if (saved.optBoolean("purged") || key.substringBefore(':') !in collections) continue
                records[key] = row
                proofs[key] =
                    JSONObject()
                        .put("entity", key.substringBefore(':'))
                        .put("entity_id", key.substringAfter(':'))
                        .put("location", "archive")
                        .put("hash", OwnerJournalRules.version(saved))
                        .put(
                            "active_hash",
                            OwnerJournalRules.version(
                                OwnerJournalRules.findOrNull(
                                    s.optJSONArray(collections[key.substringBefore(':')]),
                                    key.substringAfter(':'),
                                )
                            ),
                        )
            }
            val selected = sortedSetOf(rootKey)
            var changed: Boolean
            do {
                changed = false
                for ((key, row) in records) {
                    if (key in selected) continue
                    val linked =
                        selected.any { target ->
                            refers(row, target.substringBefore(':'), target.substringAfter(':'))
                        }
                    // Archiving a parent detaches photos; its unchanged link proof still identifies
                    // ownership.
                    val detachedPhoto =
                        key.startsWith("photo:") &&
                            selected.any { target ->
                                val links =
                                    archive.optJSONObject(target)?.optJSONArray("photo_links")
                                        ?: JSONArray()
                                (0 until links.length()).any { index ->
                                    val link = links.getJSONObject(index)
                                    link.optString("id") == row.optString("id") &&
                                        link.optString("detached_hash") ==
                                            proofs[key]!!.optString("hash")
                                }
                            }
                    if ((linked && testRecord(s, key.substringBefore(':'), row)) || detachedPhoto) {
                        selected.add(key)
                        changed = true
                    }
                }
            } while (changed)
            return JSONArray(selected.filter { it != rootKey }.map { proofs.getValue(it) })
        }

        @JvmStatic
        fun removeTestTargets(s: JSONObject, targets: JSONArray) {
            for (i in 0 until targets.length()) {
                val target = targets.getJSONObject(i)
                val entity = target.getString("entity")
                val id = target.getString("entity_id")
                OwnerJournalRules.replace(s, OwnerJournalRules.group(entity), id, null)
                OwnerJournalRules.archives(s).remove("$entity:$id")
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun purge(s: JSONObject, action: JSONObject) {
            var p: JSONObject = action.getJSONObject("payload")
            if (!p.optBoolean("confirmed")) {
                throw IllegalArgumentException("Endgültiges Löschen ausdrücklich bestätigen.")
            }
            val entity: String = p.getString("entity")
            var id: String = p.getString("id")
            val key: String = (("" + entity + ":") + id)
            val archive: JSONObject = OwnerJournalRules.archives(s)
            val saved: JSONObject? = archive.optJSONObject(key)
            if ((saved == null) || saved.optBoolean("purged")) {
                throw IllegalArgumentException(
                    "Eintrag nicht im Papierkorb oder bereits endgültig gelöscht."
                )
            }
            OwnerJournalRules.checkHash(saved, p.getString("expected_record_hash"))
            var record: JSONObject = saved.getJSONObject("record")
            val targets =
                if (p.has("expected_test_dependencies_hash")) testTargets(s, entity, id)
                else JSONArray()
            if (
                p.has("expected_test_dependencies_hash") &&
                    p.getString("expected_test_dependencies_hash") !=
                        PixelMcpWriteTools.hash(targets)
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Verknüpfte Testdaten wurden geändert. Erneut löschen.",
                )
            }
            if (
                p.has("test_dependencies") &&
                    PixelMcpWriteTools.hash(p.getJSONArray("test_dependencies")) !=
                        PixelMcpWriteTools.hash(targets)
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Die Testauswahl wurde geändert. Erneut löschen.",
                )
            }
            val view = JSONObject(s.toString())
            removeTestTargets(view, targets)
            if (!testRecord(s, entity, record))
                OwnerJournalRules.validateArchive(entity, record, view)
            detachOptionalParent(view, entity, id, action.getString("created_at"))
            requireUnreferenced(view, entity, id)
            OwnerJournalRules.replace(view, OwnerJournalRules.group(entity), id, null)
            OwnerJournalRules.archives(view).remove(key)
            for (i in 0 until targets.length()) {
                val target = targets.getJSONObject(i)
                requireUnreferenced(view, target.getString("entity"), target.getString("entity_id"))
            }
            var current: JSONObject? =
                OwnerJournalRules.findOrNull(s.getJSONArray(OwnerJournalRules.group(entity)), id)
            if (entity.equals("customer") || entity.equals("service_template")) {
                OwnerJournalRules.checkHash(current, saved.getString("archived_record_hash"))
            } else {
                if (current != null) {
                    throw IllegalArgumentException(
                        "Die ID ist inzwischen wieder aktiv. Nichts gelöscht."
                    )
                }
            }
            removeTestTargets(s, targets)
            commitPurge(s, action, entity, id, saved)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun commitPurge(
            s: JSONObject,
            action: JSONObject,
            entity: String,
            id: String,
            saved: JSONObject,
        ) {
            detachOptionalParent(s, entity, id, action.getString("created_at"))
            OwnerJournalRules.replace(s, OwnerJournalRules.group(entity), id, null)
            OwnerJournalRules.archives(s).remove("$entity:$id")
        }

        @Throws(Exception::class)
        @JvmStatic
        fun referenced(s: JSONObject, pending: JSONArray?, name: String): Boolean {
            if (name.isEmpty()) {
                return false
            }
            for (group in arrayOf("photos", "invoices", "offers", "appointments")) {
                if (hasFile(s.optJSONArray(group), name)) {
                    return true
                }
            }
            val a: JSONObject = OwnerJournalRules.archives(s)
            run {
                val it: MutableIterator<String> = a.keys()
                while (it.hasNext()) {
                    val entry: JSONObject = a.getJSONObject(it.next())
                    if (
                        (!entry.optBoolean("purged") &&
                            hasFile(entry.optJSONObject("record"), name))
                    ) {
                        return true
                    }
                }
            }
            return hasFile(pending, name)
        }

        @Throws(Exception::class)
        private fun hasFile(value: Any?, name: String): Boolean {
            if (value is JSONObject) {
                val row: JSONObject = (value as JSONObject)
                if (
                    ((name.equals(row.optString("storage_name")) ||
                        name.equals(row.optString("thumbnail_storage_name"))) ||
                        name.equals(row.optString("pdf_storage_name")))
                ) {
                    return true
                }
                run {
                    val it: MutableIterator<String> = row.keys()
                    while (it.hasNext()) {
                        if (hasFile(row.get(it.next()), name)) {
                            return true
                        }
                    }
                }
            }
            if (value is JSONArray) {
                val rows: JSONArray = (value as JSONArray)
                run {
                    var i: Int = 0
                    while (i < rows.length()) {
                        if (hasFile(rows.get(i), name)) {
                            return true
                        }
                        i++
                    }
                }
            }
            return false
        }
    }
}
