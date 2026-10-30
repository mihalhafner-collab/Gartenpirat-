package at.hausgarten.service

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Display fields only. Original records and their histories are loaded by ID. */
data class NativeListRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val address: String,
    val status: String,
    val customerId: String,
    val workDate: String,
    val thumbnailReference: String,
    val active: Boolean,
    val billingType: String
)

data class NativeListPage(val rows: List<NativeListRow>, val total: Int, val offset: Int, val limit: Int) {
    val hasMore: Boolean get() = offset + rows.size < total
}

/**
 * Disposable, indexed read cache in the existing schema-7 database. Its rows are
 * never business authority, never activate a storage transfer, and can always be
 * rebuilt from the unchanged snapshot, journal and Revier entities. SQL list
 * reads select fixed display columns, never record_json or selection_json.
 */
class NativeDataRepository(private val store: PixelLocalStore) {
    private val path by lazy { store.appContext().getDatabasePath("haus-garten-mobile.db").canonicalPath }
    private val changes get() = versions.computeIfAbsent(path) { MutableStateFlow(0L) }

    @JvmOverloads
    suspend fun list(
        collection: String,
        search: String = "",
        status: String = "",
        customerId: String = "",
        offset: Int = 0,
        limit: Int = 50,
        includeInactive: Boolean = true,
        billingType: String = "",
        propertyId: String = "",
        role: String = "",
        dateFrom: String = "",
        dateTo: String = "",
        orderId: String = "",
        worklogId: String = "",
        sessionId: String = "",
        chronological: Boolean = false
    ): NativeListPage = withContext(Dispatchers.IO) {
        requireCollection(collection)
        require(offset in 0..1_000_000 && limit in 1..100) { "TABLE_PAGE_OUT_OF_RANGE" }
        readCurrent { db ->
            val args = ArrayList<String>()
            val where = StringBuilder("collection=?").also { args.add(collection) }
            if (status.isNotEmpty()) {
                if (collection == "offers") {
                    where.append(" AND (status=? OR delivery_status=?)")
                    args.add(status)
                } else where.append(" AND status=?")
                args.add(status)
            }
            if (customerId.isNotEmpty()) { where.append(" AND customer_id=?"); args.add(customerId) }
            if (propertyId.isNotEmpty()) { where.append(" AND property_id=?"); args.add(propertyId) }
            if (role.isNotEmpty()) { where.append(" AND role=?"); args.add(role) }
            if (orderId.isNotEmpty()) { where.append(" AND order_id=?"); args.add(orderId) }
            if (worklogId.isNotEmpty()) { where.append(" AND worklog_id=?"); args.add(worklogId) }
            if (sessionId.isNotEmpty()) { where.append(" AND session_id=?"); args.add(sessionId) }
            if (dateFrom.isNotEmpty()) { where.append(" AND work_date>=?"); args.add(dateFrom) }
            if (dateTo.isNotEmpty()) { where.append(" AND work_date<=?"); args.add(dateTo + '\uffff') }
            if (!includeInactive) where.append(" AND active=1")
            if (billingType.isNotEmpty()) { where.append(" AND billing_type=?"); args.add(billingType) }
            for (term in SnapshotReadSupport.fold(search).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                where.append(" AND instr(search_text,?)>0")
                args.add(term)
            }
            val total = db.rawQuery("SELECT count(*) FROM native_read_rows WHERE $where", args.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
            val order = if (collection in datedCollections) "work_date ${if (chronological) "ASC" else "DESC"},id" else "active DESC,sort_key,id"
            val rows = ArrayList<NativeListRow>(limit)
            db.rawQuery("SELECT id,title,subtitle,address,status,customer_id,work_date,thumbnail_reference,active,billing_type FROM native_read_rows WHERE $where ORDER BY $order LIMIT ? OFFSET ?", (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) rows.add(NativeListRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getInt(8) != 0, cursor.getString(9)))
            }
            NativeListPage(rows, total, offset, limit)
        }
    }

    @JvmOverloads
    fun watchList(
        collection: String,
        search: String = "",
        status: String = "",
        customerId: String = "",
        offset: Int = 0,
        limit: Int = 50,
        includeInactive: Boolean = true,
        billingType: String = "",
        propertyId: String = "",
        role: String = "",
        dateFrom: String = "",
        dateTo: String = "",
        orderId: String = "",
        worklogId: String = "",
        sessionId: String = "",
        chronological: Boolean = false
    ): Flow<NativeListPage> = flow { changes.collect { awaitCommit(); emit(list(collection, search, status, customerId, offset, limit, includeInactive, billingType, propertyId, role, dateFrom, dateTo, orderId, worklogId, sessionId, chronological)) } }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun detail(collection: String, id: String): JSONObject? = withContext(Dispatchers.IO) {
        requireCollection(collection)
        readCurrent { db -> record(db, collection, id) }
    }

    fun watchDetail(collection: String, id: String): Flow<JSONObject?> = flow { changes.collect { awaitCommit(); emit(detail(collection, id)) } }.distinctUntilChanged { old, next -> old?.toString() == next?.toString() }.flowOn(Dispatchers.IO)

    @JvmOverloads
    suspend fun history(collection: String, relation: String, id: String, offset: Int = 0, limit: Int = 20): List<JSONObject> = withContext(Dispatchers.IO) {
        require((collection == "material_movements" && relation == "material_id") || (collection in setOf("equipment_usage", "equipment_maintenance") && relation == "equipment_id")) { "TABLE_FILTER_UNSUPPORTED" }
        require(offset in 0..1_000_000 && limit in 1..100) { "TABLE_PAGE_OUT_OF_RANGE" }
        readCurrent { db ->
            val ids = ArrayList<String>(limit)
            db.rawQuery("SELECT id FROM native_read_rows WHERE collection=? AND $relation=? ORDER BY work_date DESC,id LIMIT ? OFFSET ?", arrayOf(collection, id, limit.toString(), offset.toString())).use { cursor -> while (cursor.moveToNext()) ids.add(cursor.getString(0)) }
            val records = ids.map { record(db, collection, it) ?: throw IllegalStateException("NATIVE_DETAIL_MISSING") }
            records
        }
    }

    @JvmOverloads
    fun watchHistory(collection: String, relation: String, id: String, offset: Int = 0, limit: Int = 20): Flow<List<JSONObject>> = flow { changes.collect { awaitCommit(); emit(history(collection, relation, id, offset, limit)) } }.distinctUntilChanged { left, right -> left.map { it.toString() } == right.map { it.toString() } }.flowOn(Dispatchers.IO)

    /** Existing MCP response bodies, selected through the same indexed cache. */
    internal fun queryState(tool: String, args: JSONObject, metadata: JSONObject): JSONObject? {
        val collection = when (tool) {
            "get_customer", "search_customers" -> "customers"
            "get_order", "search_orders", "get_open_orders" -> "orders"
            "get_photo", "list_photos" -> "photos"
            "list_work_records", "get_customer_work_records" -> "worklogs"
            "list_invoices" -> "invoices"
            "search_business_data" -> ""
            else -> return null
        }
        return readCurrent { db ->
            val master = JSONObject()
            for (name in snapshotCollections) master.put(name, JSONArray())
            fun reference(group: String, id: String) {
                if (id.isEmpty()) return
                val rows = master.getJSONArray(group)
                for (i in 0 until rows.length()) if (rows.getJSONObject(i).optString("id") == id) return
                record(db, group, id)?.let { rows.put(it) }
            }
            val point = tool in setOf("get_customer", "get_order", "get_photo")
            if (point) {
                val key = when (tool) { "get_customer" -> "customer_id"; "get_order" -> "order_id"; else -> "photo_id" }
                val row = record(db, collection, args.getString(key))
                if (row != null) {
                    master.getJSONArray(collection).put(row)
                    if (tool == "get_order") { reference("customers", row.optString("customer_id")); reference("customers", row.optString("billing_customer_id")) }
                    if (tool == "get_customer") reference("customers", row.optString("default_billing_customer_id"))
                }
            } else if (tool == "search_business_data") {
                val filters = JSONObject().put("query", args.getString("query")).put("include_inactive", true).put("limit", args.optInt("limit_per_type", 10))
                for (group in arrayOf("customers", "orders", "worklogs")) master.put(group, selected(db, group, filters))
            } else {
                val filters = JSONObject(args.toString())
                if (tool == "get_open_orders") filters.put("open_only", true).put("limit", args.optInt("limit", 50))
                master.put(collection, selected(db, collection, filters))
            }
            reference("customers", args.optString("customer_id"))
            reference("customers", args.optString("billing_customer_id"))
            reference("orders", args.optString("order_id"))
            if (point && tool == "get_order") {
                val customers = master.getJSONArray("customers")
                val size = customers.length()
                for (i in 0 until size) reference("customers", customers.getJSONObject(i).optString("default_billing_customer_id"))
            }
            metadata.put("master", master).put("pending", JSONArray()).put("storage_point_read", true).put("storage_list_selected", !point)
        }
    }

    private fun selected(db: SQLiteDatabase, collection: String, filters: JSONObject): JSONArray {
        val limit = filters.optInt("limit", if (collection == "worklogs") 50 else 20)
        if (limit !in 1..100) throw IllegalArgumentException("Bitte 1 bis 100 Ergebnisse anfordern.")
        val offset = filters.optInt("offset")
        if (offset !in 0..1_000_000) throw IllegalArgumentException("Ungültiger Listenversatz.")
        val where = StringBuilder("collection=?")
        val args = arrayListOf(collection)
        for ((filter, column) in mapOf("customer_id" to "customer_id", "billing_customer_id" to "billing_customer_id", "order_id" to "order_id", "status" to "status")) {
            if (filters.optString(filter).isNotEmpty()) { where.append(" AND $column=?"); args.add(filters.getString(filter)) }
        }
        if (collection == "photos") for (field in arrayOf("role", "worklog_id", "session_id")) {
            if (filters.optString(field).isNotEmpty()) { where.append(" AND $field=?"); args.add(filters.getString(field)) }
        }
        if (collection == "customers" && !filters.optBoolean("include_inactive", false)) where.append(" AND active=1")
        if (collection == "orders" && filters.optBoolean("open_only", false)) where.append(" AND is_open=1")
        if (filters.has("billed")) { where.append(" AND billed=?"); args.add(if (filters.getBoolean("billed")) "1" else "0") }
        if (filters.optString("date_from").isNotEmpty()) { where.append(" AND work_date>=?"); args.add(filters.getString("date_from")) }
        if (filters.optString("date_to").isNotEmpty()) { where.append(" AND work_date<=?"); args.add(filters.getString("date_to") + '\uffff') }
        val query = filters.optString("query").trim()
        if (query.isNotEmpty()) {
            val terms = SnapshotReadSupport.fold(query).trim().split(Regex("\\s+"))
            where.append(" AND ((").append(terms.joinToString(" AND ") { "instr(mcp_search_text,?)>0" }).append(")")
            args.addAll(terms)
            val digits = query.replace(Regex("\\D"), "")
            if (collection == "customers" && digits.length >= 4 && query.matches(Regex("[+0-9\\s()./\\-]+"))) {
                where.append(" OR instr(phone_digits,?)>0"); args.add(digits)
            }
            where.append(')')
        }
        val order = if (collection == "worklogs" || collection == "photos") "work_date DESC,id" else "sort_key,id"
        val ids = ArrayList<String>(limit)
        db.rawQuery("SELECT id FROM native_read_rows WHERE $where ORDER BY $order LIMIT ? OFFSET ?", (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor -> while (cursor.moveToNext()) ids.add(cursor.getString(0)) }
        val result = JSONArray()
        for (id in ids) result.put(record(db, collection, id) ?: throw IllegalStateException("NATIVE_DETAIL_MISSING"))
        return SnapshotReadSupport.withoutBinary(result) as JSONArray
    }

    private fun record(db: SQLiteDatabase, collection: String, id: String): JSONObject? {
        val count = db.rawQuery("SELECT length(record_json) FROM native_read_records WHERE collection=? AND id=?", arrayOf(collection, id)).use { if (it.moveToFirst()) it.getInt(0) else return null }
        val raw = StringBuilder(count)
        var offset = 0
        while (offset < count) {
            db.rawQuery("SELECT substr(record_json,?,32768) FROM native_read_records WHERE collection=? AND id=?", arrayOf((offset + 1).toString(), collection, id)).use { check(it.moveToFirst()) { "NATIVE_DETAIL_CHANGED" }; raw.append(it.getString(0)) }
            offset += 32768
        }
        return JSONObject(raw.toString())
    }

    /** Synthetic diagnostics contain query plans and counts, never row contents. */
    suspend fun diagnostics(): JSONObject = withContext(Dispatchers.IO) {
        readCurrent { db ->
            val plans = JSONObject()
            for ((name, statement) in mapOf(
                "customer_active_sort" to "SELECT id,title,address FROM native_read_rows WHERE collection='customers' AND active=1 ORDER BY active DESC,sort_key,id LIMIT 50",
                "status_sort" to "SELECT id,title,status FROM native_read_rows WHERE collection='orders' AND status='Aktiv' ORDER BY active DESC,sort_key,id LIMIT 50",
                "object_work_history" to "SELECT id,title,work_date FROM native_read_rows WHERE collection='worklogs' AND property_id='synthetic_property' ORDER BY work_date DESC,id LIMIT 50",
                "customer_work_history" to "SELECT id,title,work_date FROM native_read_rows WHERE collection='worklogs' AND customer_id='synthetic_customer' ORDER BY work_date DESC,id LIMIT 50"
            )) {
                val rows = JSONArray()
                db.rawQuery("EXPLAIN QUERY PLAN $statement", null).use { cursor -> while (cursor.moveToNext()) rows.put(cursor.getString(3)) }
                plans.put(name, rows)
            }
            val mode = db.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst(); it.getString(0) }
            val revision = revision(db)
            JSONObject().put("journal_mode", mode).put("source_revision", revision.first).put("cached_revision", revision.second).put("rebuild_count", rebuilds.get()).put("plans", plans)
        }
    }

    // Android's transaction listener fires before the writer releases its lock.
    // Observers cross that boundary before reading; ordinary reads need no lock.
    private fun awaitCommit() {
        val db = store.writableDatabase
        db.acquireReference()
        try {
            db.beginTransactionNonExclusive()
            try { db.setTransactionSuccessful() } finally { db.endTransaction() }
        } finally { db.releaseReference() }
    }

    /** Retry if a commit changes the derived generation between count/chunk reads. */
    private fun <T> readCurrent(read: (SQLiteDatabase) -> T): T {
        val db = store.writableDatabase
        db.acquireReference()
        try {
        if (!synchronized(initialized) { initialized.containsKey(db) }) {
            // SQLite serializes first-use DDL. Do not hold its object monitor
            // across refresh: the helper's close path takes those locks in reverse.
            val nested = db.inTransaction()
            if (!hasCurrentSchema(db)) {
                db.beginTransactionNonExclusive()
                try {
                    val generation = discardSchema(db)
                    ensureSchema(db)
                    db.execSQL("UPDATE native_read_meta SET source_revision=?,cached_revision=-1 WHERE id=1", arrayOf(generation))
                    refresh(db)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            // A caller may still roll back its outer transaction, including DDL.
            if (!nested) synchronized(initialized) { initialized[db] = true }
        }
        while (true) {
            if (revision(db).let { it.first != it.second }) {
                db.beginTransactionNonExclusive()
                try { ensureSchema(db); refresh(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
            }
            val before = revision(db)
            if (before.first != before.second) continue
            try {
                val result = read(db)
                if (revision(db) == before) return result
            } catch (failure: Exception) {
                if (revision(db) == before) throw failure
            }
        }
        } finally { db.releaseReference() }
    }

    private fun schemaDefinitions(db: SQLiteDatabase): Map<String, String> {
        val definitions = linkedMapOf<String, String>()
        definitions["native_read_meta"] = "CREATE TABLE IF NOT EXISTS native_read_meta(id INTEGER PRIMARY KEY CHECK(id=1),source_revision INTEGER NOT NULL,cached_revision INTEGER NOT NULL)"
        definitions["native_read_rows"] = "CREATE TABLE IF NOT EXISTS native_read_rows(collection TEXT NOT NULL,id TEXT NOT NULL,title TEXT NOT NULL,subtitle TEXT NOT NULL,address TEXT NOT NULL,status TEXT NOT NULL,delivery_status TEXT NOT NULL,customer_id TEXT NOT NULL,billing_customer_id TEXT NOT NULL,billed INTEGER NOT NULL,is_open INTEGER NOT NULL,mcp_search_text TEXT NOT NULL,phone_digits TEXT NOT NULL,property_id TEXT NOT NULL,material_id TEXT NOT NULL,equipment_id TEXT NOT NULL,order_id TEXT NOT NULL,worklog_id TEXT NOT NULL,session_id TEXT NOT NULL,role TEXT NOT NULL,work_date TEXT NOT NULL,thumbnail_reference TEXT NOT NULL,active INTEGER NOT NULL,billing_type TEXT NOT NULL,sort_key TEXT NOT NULL,search_text TEXT NOT NULL,PRIMARY KEY(collection,id))"
        definitions["native_read_records"] = "CREATE TABLE IF NOT EXISTS native_read_records(collection TEXT NOT NULL,id TEXT NOT NULL,record_json TEXT NOT NULL,PRIMARY KEY(collection,id))"
        for ((name, columns) in mapOf(
            "sort" to "collection,active DESC,sort_key,id",
            "mcp_sort" to "collection,sort_key,id",
            "status_sort" to "collection,status,active DESC,sort_key,id",
            "customer_date" to "collection,customer_id,work_date DESC,id",
            "property_date" to "collection,property_id,work_date DESC,id",
            "date" to "collection,work_date DESC,id",
            "billing_sort" to "collection,billing_type,active DESC,sort_key,id",
            "photo_role_date" to "collection,role,work_date DESC,id",
            "order_date" to "collection,order_id,work_date DESC,id",
            "worklog_date" to "collection,worklog_id,work_date DESC,id",
            "session_date" to "collection,session_id,work_date DESC,id",
            "material_date" to "collection,material_id,work_date DESC,id",
            "equipment_date" to "collection,equipment_id,work_date DESC,id"
        )) definitions["ix_native_read_$name"] = "CREATE INDEX IF NOT EXISTS ix_native_read_$name ON native_read_rows($columns)"
        for (table in arrayOf("pending", "owner_state", "work_sessions", "work_session_events", "photo_attachments")) for (action in arrayOf("INSERT", "UPDATE", "DELETE")) {
            val row = if (action == "DELETE") "OLD" else "NEW"
            val condition = when (table) {
                "owner_state" -> " WHEN $row.key='snapshot' OR $row.key='purged_local_test_records_v1' OR $row.key LIKE 'revier:v1:entity:%' OR $row.key LIKE 'offer_decision:v1:%' OR $row.key LIKE 'local_payment:%' OR $row.key LIKE 'local_invoice_%' OR $row.key LIKE 'local_number_%' OR $row.key='business_tables:v1'"
                "pending" -> " WHEN $row.kind<>'mcp_receipt'"
                else -> ""
            }
            definitions["native_read_revision_${table}_$action"] = "CREATE TRIGGER IF NOT EXISTS native_read_revision_${table}_$action AFTER $action ON $table$condition BEGIN UPDATE native_read_meta SET source_revision=source_revision+1 WHERE id=1; END"
        }
        for (collection in BusinessTableStore.COLLECTIONS) {
            val table = "business_$collection"
            val exists = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
            if (exists) for (action in arrayOf("INSERT", "UPDATE", "DELETE")) definitions["native_read_revision_${table}_$action"] = "CREATE TRIGGER IF NOT EXISTS native_read_revision_${table}_$action AFTER $action ON $table BEGIN UPDATE native_read_meta SET source_revision=source_revision+1 WHERE id=1; END"
        }
        return definitions
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        val definitions = schemaDefinitions(db)
        db.execSQL(definitions.getValue("native_read_meta"))
        db.execSQL("INSERT OR IGNORE INTO native_read_meta(id,source_revision,cached_revision) VALUES(1,0,-1)")
        db.execSQL(definitions.getValue("native_read_rows"))
        for ((name, sql) in definitions) if (name != "native_read_meta" && name != "native_read_rows") db.execSQL(sql)
    }

    private fun discardSchema(db: SQLiteDatabase): Long {
        val hasMeta = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='native_read_meta'", null).use { it.moveToFirst() }
        val previous = if (hasMeta) try { revision(db).let { maxOf(it.first, it.second) } }
            catch (_: android.database.sqlite.SQLiteException) { 0L }
            catch (_: IllegalStateException) { 0L } else 0L
        val generation = Math.addExact(maxOf(previous, 0L), 1L)
        for (name in schemaDefinitions(db).keys) {
            if (name.startsWith("native_read_revision_")) db.execSQL("DROP TRIGGER IF EXISTS $name")
            if (name.startsWith("ix_native_read_")) db.execSQL("DROP INDEX IF EXISTS $name")
        }
        for (name in listOf("native_read_records", "native_read_rows", "native_read_meta")) db.execSQL("DROP TABLE IF EXISTS $name")
        return generation
    }

    /** Cold helpers must not request the writer merely to recognize a valid WAL cache. */
    private fun hasCurrentSchema(db: SQLiteDatabase): Boolean {
        val definitions = schemaDefinitions(db)
        val actual = HashMap<String, String>()
        db.rawQuery("SELECT name,sql FROM sqlite_master WHERE name LIKE 'native_read_%' OR name LIKE 'ix_native_read_%'", null).use { rows ->
            while (rows.moveToNext()) actual[rows.getString(0)] = rows.getString(1).orEmpty()
        }
        fun normalized(sql: String) = sql.replace(Regex("(?i)IF\\s+NOT\\s+EXISTS\\s+"), "").replace(Regex("\\s+"), "").lowercase(Locale.ROOT)
        for ((name, sql) in definitions) {
            val found = actual[name] ?: return false
            if (name.startsWith("native_read_") && !name.startsWith("native_read_revision_")) {
                // ALTER TABLE may append delivery_status; column order/default text
                // does not affect display projections, unlike type and key shape.
                val required = Regex("(?:\\(|,)([a-z_]+) (TEXT|INTEGER)").findAll(sql).associate { it.groupValues[1] to it.groupValues[2] }
                val columns = HashMap<String, String>()
                val primary = HashMap<String, Int>()
                db.rawQuery("PRAGMA table_info($name)", null).use { rows ->
                    while (rows.moveToNext()) { columns[rows.getString(1)] = rows.getString(2).uppercase(Locale.ROOT); primary[rows.getString(1)] = rows.getInt(5) }
                }
                if (columns != required) return false
                val expectedKey = if (name == "native_read_meta") mapOf("id" to 1) else mapOf("collection" to 1, "id" to 2)
                if (primary.filterValues { it > 0 } != expectedKey) return false
            } else if (normalized(found) != normalized(sql)) return false
        }
        return db.rawQuery("SELECT source_revision,cached_revision FROM native_read_meta WHERE id=1", null).use { it.moveToFirst() }
    }

    private fun revision(db: SQLiteDatabase): Pair<Long, Long> = db.rawQuery("SELECT source_revision,cached_revision FROM native_read_meta WHERE id=1", null).use { check(it.moveToFirst()); it.getLong(0) to it.getLong(1) }

    private fun refresh(db: SQLiteDatabase) {
        val revision = revision(db)
        if (revision.first == revision.second) return
        val master = SnapshotProjection.effective(store.getState())
        db.delete("native_read_rows", null, null)
        db.delete("native_read_records", null, null)
        val customers = HashMap<String, String>()
        val customerRows = master.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until customerRows.length()) { val row = customerRows.getJSONObject(i); customers[row.optString("id")] = row.optString("name") }
        val customerSearch = HashMap<String, String>()
        for (i in 0 until customerRows.length()) { val row = customerRows.getJSONObject(i); customerSearch[row.optString("id")] = SnapshotReadSupport.text(row, "name", "company") }
        val orderTitles = HashMap<String, String>()
        val orders = master.optJSONArray("orders") ?: JSONArray()
        for (i in 0 until orders.length()) { val row = orders.getJSONObject(i); orderTitles[row.optString("id")] = row.optString("title") }
        for (collection in snapshotCollections) {
            val rows = master.optJSONArray(collection) ?: JSONArray()
            val ordered = (0 until rows.length()).map { rows.getJSONObject(it) }.toMutableList()
            if (collection == "customers" || collection == "orders") ordered.sortWith { left, right ->
                var order = if (collection == "customers") java.lang.Boolean.compare(!isActive(left), !isActive(right)) else 0
                val field = if (collection == "customers") "name" else "title"
                if (order == 0) order = SnapshotReadSupport.natural(left.optString(field), right.optString(field), collator)
                if (order == 0) left.optString("id").compareTo(right.optString("id")) else order
            }
            for ((position, row) in ordered.withIndex()) insert(db, collection, row, customers, customerSearch, orderTitles, position)
        }
        for ((collection, kind) in ownerCollections) {
            val prefix = "revier:v1:entity:$kind:"
            db.rawQuery("SELECT value FROM owner_state WHERE key>=? AND key<? ORDER BY key", arrayOf(prefix, prefix + '\uffff')).use { cursor ->
                while (cursor.moveToNext()) { val row = JSONObject(cursor.getString(0)); if (!row.optBoolean("deleted")) insert(db, collection, row, customers, customerSearch, orderTitles, null) }
            }
        }
        db.execSQL("UPDATE native_read_meta SET cached_revision=source_revision WHERE id=1")
        rebuilds.incrementAndGet()
    }

    private fun insert(db: SQLiteDatabase, collection: String, row: JSONObject, customers: Map<String, String>, customerSearch: Map<String, String>, orderTitles: Map<String, String>, position: Int?) {
        val id = row.getString("id")
        val title = arrayOf("name", "title", "activity", "invoice_number", "offer_number", "caption", "original_name", "description").map { row.optString(it) }.firstOrNull { it.isNotBlank() } ?: id
        val customer = row.optString("customer_id")
        val address = row.optString("address").ifEmpty { listOf(row.optString("street"), listOf(row.optString("postal_code"), row.optString("city")).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ") }
        val date = when (collection) {
            "worklogs" -> row.optString("work_date")
            "photos" -> row.optString("capture_time", row.optString("created_at"))
            "appointments" -> row.optString("starts_at").let { value ->
                if (value.isEmpty()) "" else try { java.time.Instant.parse(value).atZone(java.time.ZoneId.of("Europe/Vienna")).toLocalDate().toString() } catch (_: java.time.DateTimeException) { value.take(10) }
            }
            else -> row.optString("date", row.optString("start_at", row.optString("created_at")))
        }
        val finance = when (collection) {
            "invoices", "offers" -> amount(row, "gross_total")
            "service_catalog" -> listOf(row.optString("category"), listOf(amount(row, "default_price"), row.optString("unit")).filter { it.isNotBlank() }.joinToString(" / ")).filter { it.isNotBlank() }.joinToString(" · ")
            else -> ""
        }
        val subtitle = listOf(customers[customer].orEmpty(), row.optString("company"), row.optString("location"), finance, if (collection == "worklogs" && row.has("hours")) row.opt("hours").toString() + " h" else "", date.take(10)).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val photo = row.optJSONObject("profile_photo")
        val thumbnail = photo?.optString("thumbnail_storage_name").orEmpty().ifEmpty { row.optString("thumbnail_storage_name") }
        val active = isActive(row) && (collection == "customers" || !row.optBoolean("archived"))
        val billing = row.optString("billing_type", row.optString("billing_mode", row.optString("billing_method"))).ifEmpty { if (collection == "customers") "direct" else "" }
        val kind = when (collection) { "customers" -> "customer"; "orders" -> "order"; else -> "other" }
        var search = when (kind) {
            "customer" -> SnapshotReadSupport.text(row, "name", "company", "first_name", "last_name", "street", "postal_code", "city", "email", "phone")
            "order" -> SnapshotReadSupport.text(row, "title", "description", "location", "customer_name")
            else -> SnapshotReadSupport.text(row, "activity", "notes", "material", "location", "customer_name", "order_title")
        }
        search += " " + customerSearch[row.optString("customer_id")].orEmpty() + " " + orderTitles[row.optString("order_id")].orEmpty()
        search += " " + SnapshotReadSupport.text(row, "invoice_number", "offer_number", "caption", "original_name", "filename")
        val values = ContentValues().apply {
            put("collection", collection); put("id", id); put("title", title); put("subtitle", subtitle); put("address", address); put("status", if (collection == "equipment") row.optString("condition") else row.optString("status")); put("delivery_status", row.optString("delivery_status")); put("customer_id", customer); put("billing_customer_id", BillingRules.recipient(row)); put("billed", if (row.optInt("billed", 0) != 0) 1 else 0); put("is_open", if (row.optString("status") in listOf("", "Geplant", "Aktiv", "In Arbeit", "Wartet auf Material", "Offen")) 1 else 0); put("mcp_search_text", SnapshotReadSupport.fold(search)); put("phone_digits", row.optString("phone").replace(Regex("\\D"), "")); put("property_id", row.optString("property_id")); put("material_id", row.optString("material_id")); put("equipment_id", row.optString("equipment_id")); put("order_id", row.optString("order_id")); put("worklog_id", row.optString("worklog_id")); put("session_id", row.optString("session_id")); put("role", row.optString("role")); put("work_date", date); put("thumbnail_reference", thumbnail); put("active", if (active) 1 else 0); put("billing_type", billing); put("sort_key", position?.toString()?.padStart(12, '0') ?: naturalKey(title)); put("search_text", SnapshotReadSupport.fold(listOf(title, subtitle, address, search, row.optString("email"), row.optString("phone"), row.optString("invoice_number"), row.optString("offer_number"), row.optString("category"), row.optString("description"), row.optString("assumptions"), row.optString("exclusions"), row.optString("legal_notes")).joinToString(" ")))
        }
        db.insertOrThrow("native_read_rows", null, values)
        db.insertOrThrow("native_read_records", null, ContentValues().apply { put("collection", collection); put("id", id); put("record_json", row.toString()) })
    }

    private fun isActive(row: JSONObject): Boolean = row.optInt("active", 1) != 0 && row.optBoolean("active", true)

    private fun amount(row: JSONObject, field: String): String {
        if (!row.has(field) || row.isNull(field)) return ""
        return try {
            java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY).format(java.math.BigDecimal(row.get(field).toString()))
        } catch (_: NumberFormatException) { "Betrag nicht lesbar" }
    }

    private fun naturalKey(value: String): String = Regex("\\d+|\\D+").findAll(value).joinToString("") { match ->
        if (match.value[0].isDigit()) {
            val digits = match.value.trimStart('0').ifEmpty { "0" }
            "0" + digits.length.toString().padStart(10, '0') + digits
        } else "1" + collator.getCollationKey(match.value).toByteArray().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    companion object {
        private val initialized = java.util.WeakHashMap<SQLiteDatabase, Boolean>()
        private val versions = ConcurrentHashMap<String, MutableStateFlow<Long>>()
        private val rebuilds = AtomicLong()
        private val collator get() = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
        private val snapshotCollections = arrayOf("customers", "orders", "worklogs", "invoices", "photos", "offers", "appointments", "service_catalog")
        private val ownerCollections = linkedMapOf("properties" to "property", "inquiries" to "inquiry", "care_plans" to "care_plan", "visits" to "visit", "materials" to "material", "equipment" to "equipment", "material_movements" to "material_movement", "equipment_usage" to "equipment_usage", "equipment_maintenance" to "equipment_maintenance")
        private val datedCollections = setOf("worklogs", "photos", "appointments", "visits", "material_movements", "equipment_usage", "equipment_maintenance")
        private fun requireCollection(collection: String) { require(collection in snapshotCollections || collection in ownerCollections) { "TABLE_COLLECTION_UNSUPPORTED" } }
        @JvmStatic fun notifyCommitted(databasePath: String) {
            val path = java.io.File(databasePath).canonicalPath
            val version = versions.computeIfAbsent(path) { MutableStateFlow(0L) }
            synchronized(version) { version.value = version.value + 1L }
        }
    }
}
