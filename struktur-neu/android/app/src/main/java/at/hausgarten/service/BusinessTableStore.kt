package at.hausgarten.service

import java.io.IOException
import java.util.Arrays
import java.util.HashSet
import java.util.LinkedHashMap
import org.json.JSONArray
import org.json.JSONObject

class BusinessTableStore {
    interface Rows : AutoCloseable {
        @Throws(Exception::class) fun next(): Boolean

        @Throws(Exception::class) fun text(column: Int): String

        @Throws(Exception::class) fun number(column: Int): Long

        @Throws(Exception::class) override fun close(): Unit
    }

    fun interface Action<T> {
        @Throws(Exception::class) fun run(): T
    }

    interface Sql {
        @Throws(Exception::class) fun query(sql: String, vararg arguments: String): Rows

        @Throws(Exception::class) fun execute(sql: String, vararg arguments: Any?): Unit

        @Throws(Exception::class) fun <T> transaction(action: Action<T>): T
    }

    fun interface Evidence {
        @Throws(Exception::class) fun verify(source: JSONObject): String
    }

    fun interface PrepareJournal {
        @Throws(Exception::class) fun prepare(master: JSONObject): Unit
    }

    private val sql: Sql
    private val selectionSchemas: MutableMap<String, Boolean> = java.util.HashMap()

    constructor(sql: Sql) {
        this.sql = sql
    }

    private @Throws(Exception::class) fun schema(): Unit {

        for (action in arrayOf("INSERT", "UPDATE", "DELETE")) {
            sql.execute(
                "CREATE TRIGGER IF NOT EXISTS business_tables_journal_" +
                    action +
                    " AFTER " +
                    action +
                    " ON pending BEGIN INSERT OR REPLACE INTO owner_state(key,value) VALUES('" +
                    JOURNAL_REVISION +
                    "',CAST(COALESCE((SELECT value FROM owner_state WHERE key='" +
                    JOURNAL_REVISION +
                    "'),'0') AS INTEGER)+1); END"
            )
        }
        if (owner(JOURNAL_REVISION) == null) {
            owner(JOURNAL_REVISION, "0")
        }
        for (collection in COLLECTIONS) {
            val name = table(collection)
            sql.execute(
                "CREATE TABLE IF NOT EXISTS " +
                    name +
                    " (generation TEXT NOT NULL,id TEXT NOT NULL,position INTEGER NOT NULL,record_json TEXT NOT NULL,record_hash TEXT NOT NULL,selection_json TEXT NOT NULL,selection_hash TEXT NOT NULL,customer_id TEXT NOT NULL,billing_customer_id TEXT NOT NULL,order_id TEXT NOT NULL,worklog_id TEXT NOT NULL,work_date TEXT NOT NULL,status TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(generation,id),UNIQUE(generation,position))"
            )
            for (field in
                arrayOf(
                    "customer_id",
                    "billing_customer_id",
                    "order_id",
                    "worklog_id",
                    "work_date",
                    "status",
                    "name",
                )) {
                sql.execute(
                    "CREATE INDEX IF NOT EXISTS ix_" +
                        name +
                        "_" +
                        field +
                        " ON " +
                        name +
                        "(generation," +
                        field +
                        ")"
                )
            }
        }
    }

    private @Throws(Exception::class) fun fence(enabled: Boolean): Unit {

        val names = java.util.ArrayList<String>()
        sql.query(
                "SELECT name FROM sqlite_master WHERE type='trigger' AND name GLOB 'business_tables_fence_*'"
            )
            .use { rows ->
                while (rows.next()) {
                    names.add(rows.text(0))
                }
            }
        for (name in names) {
            require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")), "TABLE_SCHEMA_NAME_UNSUPPORTED")
            sql.execute("DROP TRIGGER " + name)
        }
        if (!(enabled)) {
            sql.execute("DELETE FROM owner_state WHERE key=?", "business_tables:write_lock")
            return
        }
        names.clear()
        sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ")
            .use { rows ->
                while (rows.next()) {
                    names.add(rows.text(0))
                }
            }
        for (name in names) {
            var target: Boolean = false
            for (collection in COLLECTIONS) {
                if ((name == table(collection))) {
                    target = true
                }
            }
            // The derived display cache must stay refreshable while authority is fenced.
            if (
                target ||
                    name == "native_read_meta" ||
                    name == "native_read_rows" ||
                    name == "native_read_records"
            ) {
                continue
            }
            require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")), "TABLE_SCHEMA_NAME_UNSUPPORTED")
            for (action in arrayOf("INSERT", "UPDATE", "DELETE")) {
                var guard =
                    "EXISTS(SELECT 1 FROM owner_state WHERE key='business_tables:write_lock')"
                if ((name == "owner_state")) {
                    var changed =
                        (if ((action == "DELETE")) "OLD.key NOT GLOB 'business_tables:*'"
                        else
                            (if ((action == "INSERT")) "NEW.key NOT GLOB 'business_tables:*'"
                            else
                                "(NEW.key NOT GLOB 'business_tables:*' OR OLD.key NOT GLOB 'business_tables:*')"))
                    guard += " AND " + changed
                }
                sql.execute(
                    "CREATE TRIGGER business_tables_fence_" +
                        name +
                        "_" +
                        action +
                        " BEFORE " +
                        action +
                        " ON " +
                        name +
                        " WHEN " +
                        guard +
                        " BEGIN SELECT RAISE(ABORT,'TABLE_TRANSFER_IN_PROGRESS'); END"
                )
            }
        }
        owner("business_tables:write_lock", "locked")
    }

    @Throws(Exception::class)
    fun cancel(requestId: String?): JSONObject {

        return sql.transaction(
            LAM@{
                val meta = transfer(requestId)
                require(
                    java.util.Arrays.asList("COPYING", "READY", "ABORTED")
                        .contains(meta.getString("phase")),
                    "TABLE_CANCEL_PHASE_REQUIRED",
                )
                meta.put("phase", "ABORTED")
                owner(META, meta.toString())
                fence(false)
                return@LAM meta
            }
        )
    }

    private @Throws(Exception::class) fun owner(key: String): String? {

        return textChunks("owner_state", "value", "key=?", arrayOf(key))
    }

    private @Throws(Exception::class) fun owner(key: String, value: String): Unit {

        sql.execute("INSERT OR REPLACE INTO owner_state(key,value) VALUES(?,?)", key, value)
    }

    private @Throws(Exception::class) fun metadata(): JSONObject? {

        var value = owner(META)
        if (value == null) {
            return null
        }
        val result = JSONObject(value)
        require(result.getInt("format") == 1, "TABLE_FORMAT_UNSUPPORTED")
        require(
            Arrays.asList("COPYING", "READY", "ACTIVE", "ROLLED_BACK", "ABORTED")
                .contains(result.getString("phase")),
            "TABLE_PHASE_UNSUPPORTED",
        )
        require(
            (hash(result.getJSONObject("remainder")) == result.getString("remainder_hash")),
            "TABLE_METADATA_HASH_MISMATCH",
        )
        return result
    }

    private @Throws(Exception::class) fun transfer(requestId: String?): JSONObject {

        request(requestId)
        val meta = metadata()
        require(meta != null && (requestId == meta.optString("request")), "TABLE_TRANSFER_MISMATCH")
        return meta!!
    }

    @Throws(Exception::class)
    fun active(): Boolean {

        val meta = metadata()
        return meta != null && ("ACTIVE" == meta.getString("phase"))
    }

    @Throws(Exception::class)
    fun status(): JSONObject {

        return sql.transaction(
            LAM@{
                val meta = metadata()
                if (meta == null) {
                    return@LAM JSONObject().put("phase", "SNAPSHOT")
                }
                return@LAM JSONObject()
                    .put("phase", meta.getString("phase"))
                    .put("request", meta.getString("request"))
                    .put("source_hash", meta.getString("source_hash"))
                    .put("collection", meta.getInt("collection"))
                    .put("offset", meta.getInt("offset"))
                    .put("format", meta.getInt("format"))
            }
        )
    }

    @Throws(Exception::class)
    fun snapshot(fallback: String): JSONObject {

        return sql.transaction(
            LAM@{
                val meta = metadata()
                if (meta != null && ("ACTIVE" == meta.getString("phase"))) {
                    return@LAM materialize(meta)
                }
                val raw = owner("snapshot")
                return@LAM JSONObject((if (raw == null) fallback else raw))
            }
        )
    }

    @Throws(Exception::class)
    fun metadataSnapshot(): JSONObject {

        return sql.transaction({
            JSONObject(activeMetadata().getJSONObject("remainder").toString())
        })
    }

    @Throws(Exception::class)
    fun all(collection: String): JSONArray {

        return sql.transaction({ collection(activeMetadata(), collection) })
    }

    @Throws(Exception::class)
    fun projectionCurrent(): Boolean {

        val meta = metadata()
        val projected = owner(PROJECTED_REVISION)
        return meta != null &&
            ("ACTIVE" == meta.getString("phase")) &&
            meta.optInt("journal_tracking_version") == 1 &&
            projected != null &&
            (projected == owner(JOURNAL_REVISION))
    }

    @Throws(Exception::class)
    fun invalidateJournal(): Unit {

        if (active()) {
            sql.execute(
                "INSERT OR REPLACE INTO owner_state(key,value) VALUES(?,CAST(COALESCE((SELECT value FROM owner_state WHERE key=?),'0') AS INTEGER)+1)",
                JOURNAL_REVISION,
                JOURNAL_REVISION,
            )
        }
    }

    @Throws(Exception::class)
    fun commitJournal(actions: JSONArray, prepare: PrepareJournal): Unit {

        sql.transaction(
            LAM@{
                if (
                    !(active()) ||
                        activeMetadata().optInt("journal_tracking_version") != 1 ||
                        projectionCurrent()
                ) {
                    return@LAM null
                }
                val master = materialize(activeMetadata())
                prepare.prepare(master)
                val journal = actions
                LocalJournalRules.apply(master, journal)
                replaceSnapshot(master)
                owner(PROJECTED_REVISION, owner(JOURNAL_REVISION)!!)
                return@LAM null
            }
        )
    }

    @Throws(Exception::class)
    fun candidates(collection: String, filters: JSONObject): JSONArray {

        return sql.transaction(
            LAM@{
                val meta = activeMetadata()
                val where = StringBuilder("generation=?")
                val args = java.util.ArrayList<String>()
                args.add(meta.getString("generation"))
                for (field in arrayOf("customer_id", "order_id", "status", "worklog_id")) {
                    if (!(filters.optString(field).isEmpty())) {
                        where.append(" AND ").append(field).append("=?")
                        args.add(filters.getString(field))
                    }
                }
                return@LAM collection(meta, collection, where.toString(), args.toTypedArray())
            }
        )
    }

    @Throws(Exception::class)
    fun replaceSnapshot(next: JSONObject): Unit {

        sql.transaction(
            LAM@{
                val meta = activeMetadata()
                validateRows(next)
                invalidateJournal()
                val generation = meta.getString("generation")
                for (collection in COLLECTIONS) {
                    val name = table(collection)
                    val rows = next.getJSONArray(collection)
                    val hashes = LinkedHashMap<String, String>()
                    val positions = LinkedHashMap<String, Long>()
                    sql.query(
                            "SELECT id,record_hash,position FROM " +
                                name +
                                " WHERE generation=? ORDER BY position",
                            generation,
                        )
                        .use { old ->
                            while (old.next()) {
                                hashes.put(old.text(0), old.text(1))
                                positions.put(old.text(0), old.number(2))
                            }
                        }
                    val retained = HashSet<String>()
                    for (i in 0 until rows.length()) {
                        retained.add(id(rows.getJSONObject(i)))
                    }
                    for (old in hashes.keys) {
                        if (!(retained.contains(old))) {
                            sql.execute(
                                "DELETE FROM " + name + " WHERE generation=? AND id=?",
                                generation,
                                old,
                            )
                        }
                    }
                    for (i in 0 until rows.length()) {
                        var id = id(rows.getJSONObject(i))
                        val old = positions.get(id)
                        if (old != null && old != i.toLong()) {
                            sql.execute(
                                "UPDATE " + name + " SET position=? WHERE generation=? AND id=?",
                                -(1L) - i,
                                generation,
                                id,
                            )
                        }
                    }
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        var id = id(row)
                        if (!((hash(row) == hashes.get(id)))) {
                            sql.execute(
                                "DELETE FROM " + name + " WHERE generation=? AND id=?",
                                generation,
                                id,
                            )
                            insert(generation, collection, i.toLong(), row)
                        } else {
                            if (positions.get(id) != i.toLong()) {
                                sql.execute(
                                    "UPDATE " +
                                        name +
                                        " SET position=? WHERE generation=? AND id=?",
                                    i,
                                    generation,
                                    id,
                                )
                            }
                        }
                    }
                }
                val rest = remainder(next)
                if (!((hash(rest) == meta.getString("remainder_hash")))) {
                    meta.put("remainder", rest).put("remainder_hash", hash(rest))
                    owner(META, meta.toString())
                }
                return@LAM null
            }
        )
    }

    private @Throws(Exception::class) fun selectionColumns(collection: String): Boolean {

        if (selectionSchemas.containsKey(collection)) {
            return selectionSchemas.getValue(collection)
        }
        var found: Boolean = false
        sql.query("PRAGMA table_info(" + table(collection) + ")").use { rows ->
            while (rows.next()) {
                if (("selection_json" == rows.text(1))) {
                    found = true
                }
            }
        }
        selectionSchemas.put(collection, found)
        return found
    }

    @Throws(Exception::class)
    fun selected(
        collection: String,
        kind: String,
        filters: JSONObject,
        context: JSONObject,
    ): JSONArray {

        return sql.transaction(
            LAM@{
                val meta = activeMetadata()
                val candidates = JSONArray()
                if (!(selectionColumns(collection))) {
                    return@LAM SnapshotReadSupport.select(
                        candidates(collection, filters),
                        filters,
                        kind,
                        context,
                    )
                }
                val where = StringBuilder("generation=?")
                val args = java.util.ArrayList<String>()
                args.add(meta.getString("generation"))
                for (field in arrayOf("customer_id", "order_id", "status", "worklog_id")) {
                    if (!(filters.optString(field).isEmpty())) {
                        where.append(" AND ").append(field).append("=?")
                        args.add(filters.getString(field))
                    }
                }
                sql.query(
                        "SELECT id,selection_hash,length(selection_json),substr(selection_json,1,32768) FROM " +
                            table(collection) +
                            " WHERE " +
                            where +
                            " ORDER BY position",
                        *args.toTypedArray(),
                    )
                    .use { rows ->
                        while (rows.next()) {
                            var raw =
                                (if (rows.number(2) <= 32768) rows.text(3)
                                else
                                    textChunks(
                                        table(collection),
                                        "selection_json",
                                        "generation=? AND id=?",
                                        arrayOf(meta.getString("generation"), rows.text(0)),
                                    ))
                            val selected = JSONObject(raw ?: error("TABLE_ROW_MISSING"))
                            require(
                                (rows.text(0) == id(selected)) && (rows.text(1) == hash(selected)),
                                "TABLE_SELECTION_HASH_MISMATCH",
                            )
                            candidates.put(selected)
                        }
                    }
                val page = SnapshotReadSupport.select(candidates, filters, kind, context)
                val result = JSONArray()
                for (i in 0 until page.length()) {
                    val selected = page.getJSONObject(i)
                    val full = row(meta, collection, id(selected))
                    require(
                        (hash(selected) ==
                            hash(SnapshotReadSupport.withoutBinary(selection(full!!)))),
                        "TABLE_SELECTION_STALE",
                    )
                    result.put(full)
                }
                return@LAM result
            }
        )
    }

    @Throws(Exception::class)
    fun referenceContext(): JSONObject {

        val context = JSONObject()
        for (collection in arrayOf("customers", "orders")) {
            val result = JSONArray()
            val meta = activeMetadata()
            if (!(selectionColumns(collection))) {
                context.put(collection, all(collection))
                continue
            }
            sql.query(
                    "SELECT id,selection_hash,length(selection_json),substr(selection_json,1,32768) FROM " +
                        table(collection) +
                        " WHERE generation=? ORDER BY position",
                    meta.getString("generation"),
                )
                .use { rows ->
                    while (rows.next()) {
                        var raw =
                            (if (rows.number(2) <= 32768) rows.text(3)
                            else
                                textChunks(
                                    table(collection),
                                    "selection_json",
                                    "generation=? AND id=?",
                                    arrayOf(meta.getString("generation"), rows.text(0)),
                                ))
                        var value = JSONObject(raw ?: error("TABLE_ROW_MISSING"))
                        require(
                            (rows.text(0) == id(value)) && (rows.text(1) == hash(value)),
                            "TABLE_SELECTION_HASH_MISMATCH",
                        )
                        val reference = JSONObject().put("id", value.getString("id"))
                        for (key in arrayOf("name", "company", "title")) {
                            if (value.has(key)) {
                                reference.put(key, value.get(key))
                            }
                        }
                        result.put(reference)
                    }
                }
            context.put(collection, result)
        }
        return context
    }

    private @Throws(Exception::class) fun source(meta: JSONObject, check: Evidence?): JSONObject {

        val raw = owner("snapshot")
        require(
            raw != null && (hash(raw) == meta.getString("source_text_hash")),
            "TABLE_SOURCE_CHANGED",
        )
        var value = JSONObject(raw ?: error("TABLE_ROW_MISSING"))
        require((hash(value) == meta.getString("source_hash")), "TABLE_SOURCE_CHANGED")
        require((evidence(check, value) == meta.getString("backup_hash")), "TABLE_BACKUP_CHANGED")
        return value
    }

    @Throws(Exception::class)
    fun prepare(requestId: String?, expectedSourceHash: String?, check: Evidence?): JSONObject {

        request(requestId)
        return sql.transaction(
            LAM@{
                val raw = owner("snapshot")
                require(raw != null, "TABLE_PERSISTED_SNAPSHOT_REQUIRED")
                val source = JSONObject(raw ?: error("TABLE_ROW_MISSING"))
                require((hash(source) == expectedSourceHash), "TABLE_SOURCE_CHANGED")
                validateRows(source)
                val backup = evidence(check, source)
                val existing = metadata()
                if (existing != null) {
                    require(
                        (requestId == existing.getString("request")) &&
                            (expectedSourceHash == existing.getString("source_hash")) &&
                            (backup == existing.getString("backup_hash")),
                        "TABLE_TRANSFER_EXISTS",
                    )
                    require(
                        !(("ROLLED_BACK" == existing.getString("phase"))),
                        "TABLE_TRANSFER_ALREADY_ROLLED_BACK",
                    )
                    if (existing.optInt("journal_tracking_version") != 1) {
                        schema()
                        existing.put("journal_tracking_version", 1)
                        owner(META, existing.toString())
                        invalidateJournal()
                    }
                    if (("ABORTED" == existing.getString("phase"))) {
                        source(existing, check)
                        existing.put(
                            "phase",
                            (if (existing.getInt("collection") == COLLECTIONS.size) "READY"
                            else "COPYING"),
                        )
                        owner(META, existing.toString())
                        fence(true)
                    }
                    return@LAM existing
                }
                schema()
                var meta =
                    JSONObject()
                        .put("format", 1)
                        .put("journal_tracking_version", 1)
                        .put("request", requestId)
                        .put("generation", hash(requestId))
                        .put("phase", "COPYING")
                        .put("source_hash", expectedSourceHash)
                        .put("source_text_hash", hash(raw))
                        .put("backup_hash", backup)
                        .put("remainder", remainder(source))
                        .put("remainder_hash", hash(remainder(source)))
                        .put("collection", 0)
                        .put("offset", 0)
                owner(META, meta.toString())
                fence(true)
                return@LAM meta
            }
        )
    }

    @Throws(Exception::class)
    fun copyBatch(requestId: String?, maximumRows: Int, check: Evidence?): JSONObject {

        require(maximumRows > 0 && maximumRows <= 1000, "TABLE_BATCH_OUT_OF_RANGE")
        return sql.transaction(
            LAM@{
                val meta = transfer(requestId)
                val source = source(meta, check)
                if (("READY" == meta.getString("phase"))) {
                    return@LAM meta
                }
                require(("COPYING" == meta.getString("phase")), "TABLE_COPY_PHASE_REQUIRED")
                var collection: Int = meta.getInt("collection")
                var offset: Int = meta.getInt("offset")
                var copied: Int = 0
                while (collection < COLLECTIONS.size && copied < maximumRows) {
                    val rows = source.getJSONArray(COLLECTIONS[collection])
                    if (offset == rows.length()) {
                        collection++
                        offset = 0
                        continue
                    }
                    insert(
                        meta.getString("generation"),
                        COLLECTIONS[collection],
                        offset.toLong(),
                        rows.getJSONObject(offset),
                    )
                    offset++
                    copied++
                }
                while (
                    collection < COLLECTIONS.size &&
                        offset == source.getJSONArray(COLLECTIONS[collection]).length()
                ) {
                    collection++
                    offset = 0
                }
                meta.put("collection", collection).put("offset", offset)
                if (collection == COLLECTIONS.size) {
                    require(
                        (hash(materialize(meta)) == meta.getString("source_hash")),
                        "TABLE_READBACK_MISMATCH",
                    )
                    meta.put("phase", "READY")
                }
                owner(META, meta.toString())
                return@LAM meta
            }
        )
    }

    @Throws(Exception::class)
    fun activate(requestId: String?, expectedSourceHash: String?, check: Evidence?): JSONObject {

        return sql.transaction(
            LAM@{
                val meta = transfer(requestId)
                require(
                    (expectedSourceHash == meta.getString("source_hash")),
                    "TABLE_SOURCE_CHANGED",
                )
                if (("ACTIVE" == meta.getString("phase"))) {
                    return@LAM meta
                }
                require(("READY" == meta.getString("phase")), "TABLE_NOT_READY")
                source(meta, check)
                val readback = materialize(meta)
                require((hash(readback) == expectedSourceHash), "TABLE_READBACK_MISMATCH")
                require(
                    (evidence(check, readback) == meta.getString("backup_hash")),
                    "TABLE_BACKUP_CHANGED",
                )
                meta.put("phase", "ACTIVE")
                owner(META, meta.toString())
                fence(false)
                return@LAM meta
            }
        )
    }

    private @Throws(Exception::class) fun activeMetadata(): JSONObject {

        val meta = metadata()
        require(meta != null && ("ACTIVE" == meta.getString("phase")), "TABLE_STORAGE_NOT_ACTIVE")
        return meta!!
    }

    private @Throws(Exception::class) fun insert(
        generation: String,
        collection: String,
        position: Long,
        row: JSONObject,
    ): Unit {

        val selected = selection(row)
        val indexed: Boolean = selectionColumns(collection)
        var args =
            java.util.ArrayList<Any>(
                java.util.Arrays.asList(generation, id(row), position, row.toString(), hash(row))
            )
        if (indexed) {
            args.add(selected.toString())
            args.add(hash(selected))
        }
        args.addAll(
            java.util.Arrays.asList(
                row.optString("customer_id"),
                row.optString("billing_customer_id"),
                row.optString("order_id"),
                row.optString("worklog_id"),
                row.optString("work_date"),
                row.optString("status"),
                row.optString("name", row.optString("title")),
            )
        )
        sql.execute(
            "INSERT INTO " +
                table(collection) +
                "(generation,id,position,record_json,record_hash," +
                ((if (indexed) "selection_json,selection_hash," else "")) +
                "customer_id,billing_customer_id,order_id,worklog_id,work_date,status,name) VALUES(" +
                java.util.Collections.nCopies(args.size, "?").joinToString(",") +
                ")",
            *args.toTypedArray(),
        )
    }

    private @Throws(Exception::class) fun row(
        meta: JSONObject,
        collection: String,
        id: String,
    ): JSONObject? {

        val name = table(collection)
        val generation = meta.getString("generation")
        var expected: String
        sql.query(
                "SELECT record_hash FROM " + name + " WHERE generation=? AND id=?",
                generation,
                id,
            )
            .use { rows ->
                if (!(rows.next())) {
                    return null
                }
                expected = rows.text(0)
            }
        val raw = textChunks(name, "record_json", "generation=? AND id=?", arrayOf(generation, id))
        require(raw != null, "TABLE_ROW_MISSING")
        val result = JSONObject(raw ?: error("TABLE_ROW_MISSING"))
        require((id == id(result)) && (expected == hash(result)), "TABLE_ROW_HASH_MISMATCH")
        return result
    }

    @Throws(Exception::class)
    fun get(collection: String, id: String): JSONObject? {

        return sql.transaction({ row(activeMetadata(), collection, id) })
    }

    @Throws(Exception::class)
    fun select(
        collection: String,
        field: String,
        value: String,
        offset: Int,
        limit: Int,
    ): JSONArray {

        require(FILTERS.contains(field), "TABLE_FILTER_UNSUPPORTED")
        require(offset >= 0 && limit >= 1 && limit <= 100, "TABLE_PAGE_OUT_OF_RANGE")
        return sql.transaction(
            LAM@{
                val meta = activeMetadata()
                val ids = JSONArray()
                var out = JSONArray()
                sql.query(
                        "SELECT id FROM " +
                            table(collection) +
                            " WHERE generation=? AND " +
                            field +
                            "=? ORDER BY position LIMIT ? OFFSET ?",
                        meta.getString("generation"),
                        value,
                        (limit).toString(),
                        (offset).toString(),
                    )
                    .use { rows ->
                        while (rows.next()) {
                            ids.put(rows.text(0))
                        }
                    }
                for (i in 0 until ids.length()) {
                    out.put(row(meta, collection, ids.getString(i)))
                }
                return@LAM out
            }
        )
    }

    @Throws(Exception::class)
    fun put(
        collection: String,
        value: JSONObject,
        expectedHash: String?,
        requestId: String?,
    ): JSONObject {

        return mutate(collection, id(value), value, expectedHash, requestId)
    }

    @Throws(Exception::class)
    fun remove(
        collection: String,
        id: String,
        expectedHash: String?,
        requestId: String?,
    ): JSONObject {

        require(expectedHash != null && !(expectedHash.isEmpty()), "TABLE_EXPECTED_HASH_REQUIRED")
        return mutate(collection, id, null, expectedHash, requestId)
    }

    private @Throws(Exception::class) fun mutate(
        collection: String,
        id: String,
        value: JSONObject?,
        expectedHash: String?,
        requestId: String?,
    ): JSONObject {

        request(requestId)
        table(collection)
        require(expectedHash != null, "TABLE_EXPECTED_HASH_REQUIRED")
        return sql.transaction(
            LAM@{
                val meta = activeMetadata()
                var fingerprint =
                    hash(
                        JSONObject()
                            .put("generation", meta.getString("generation"))
                            .put("collection", collection)
                            .put("id", id)
                            .put("value", (if (value == null) JSONObject.NULL else value))
                            .put("expected", expectedHash)
                    )
                val key = "business_tables:receipt:" + requestId
                val saved = owner(key)
                if (saved != null) {
                    val receipt = JSONObject(saved)
                    require(
                        (fingerprint == receipt.getString("fingerprint")),
                        "TABLE_REQUEST_REUSED",
                    )
                    return@LAM receipt
                }
                invalidateJournal()
                var current = row(meta, collection, id)
                require(
                    (expectedHash == (if (current == null) "" else hash(current))),
                    "TABLE_WRITE_CONFLICT",
                )
                var position: Long
                sql.query(
                        "SELECT position FROM " +
                            table(collection) +
                            " WHERE generation=? AND id=?",
                        meta.getString("generation"),
                        id,
                    )
                    .use { rows ->
                        if (rows.next()) {
                            position = rows.number(0)
                        } else {
                            sql.query(
                                    "SELECT COALESCE(MAX(position),-1)+1 FROM " +
                                        table(collection) +
                                        " WHERE generation=?",
                                    meta.getString("generation"),
                                )
                                .use { last ->
                                    require(last.next(), "TABLE_POSITION_UNAVAILABLE")
                                    position = last.number(0)
                                }
                        }
                    }
                sql.execute(
                    "DELETE FROM " + table(collection) + " WHERE generation=? AND id=?",
                    meta.getString("generation"),
                    id,
                )
                if (value != null) {
                    insert(meta.getString("generation"), collection, position, value)
                }
                var receipt =
                    JSONObject()
                        .put("fingerprint", fingerprint)
                        .put("record_hash", (if (value == null) JSONObject.NULL else hash(value)))
                        .put("deleted", value == null)
                owner(key, receipt.toString())
                return@LAM receipt
            }
        )
    }

    @Throws(Exception::class)
    fun exportSnapshot(): JSONObject {

        return sql.transaction({ materialize(activeMetadata()) })
    }

    private @Throws(Exception::class) fun materialize(meta: JSONObject): JSONObject {

        val result = JSONObject(meta.getJSONObject("remainder").toString())
        for (collection in COLLECTIONS) {
            result.put(collection, collection(meta, collection))
        }
        return result
    }

    private @Throws(Exception::class) fun collection(
        meta: JSONObject,
        collection: String,
    ): JSONArray {

        return collection(meta, collection, "generation=?", arrayOf(meta.getString("generation")))
    }

    private @Throws(Exception::class) fun collection(
        meta: JSONObject,
        collection: String,
        where: String,
        arguments: Array<String>,
    ): JSONArray {

        val result = JSONArray()
        val name = table(collection)
        val generation = meta.getString("generation")
        sql.query(
                "SELECT id,record_hash,length(record_json),substr(record_json,1,32768) FROM " +
                    name +
                    " WHERE " +
                    where +
                    " ORDER BY position",
                *arguments,
            )
            .use { rows ->
                while (rows.next()) {
                    var id = rows.text(0)
                    var raw =
                        (if (rows.number(2) <= 32768) rows.text(3)
                        else
                            textChunks(
                                name,
                                "record_json",
                                "generation=? AND id=?",
                                arrayOf(generation, id),
                            ))
                    var record = JSONObject(raw ?: error("TABLE_ROW_MISSING"))
                    require(
                        (id == id(record)) && (rows.text(1) == hash(record)),
                        "TABLE_ROW_HASH_MISMATCH",
                    )
                    result.put(record)
                }
            }
        return result
    }

    @Throws(Exception::class)
    fun rollback(requestId: String?, expectedCurrentHash: String?, check: Evidence?): JSONObject {

        request(requestId)
        return sql.transaction(
            LAM@{
                val meta = metadata()
                require(meta != null, "TABLE_STORAGE_NOT_ACTIVE")
                meta!!
                if (("ROLLED_BACK" == meta.getString("phase"))) {
                    require(
                        (requestId == meta.optString("rollback_request")) &&
                            (expectedCurrentHash == meta.optString("rollback_hash")),
                        "TABLE_ROLLBACK_MISMATCH",
                    )
                    return@LAM meta
                }
                require(("ACTIVE" == meta.getString("phase")), "TABLE_STORAGE_NOT_ACTIVE")
                var current = materialize(meta)
                require((hash(current) == expectedCurrentHash), "TABLE_WRITE_CONFLICT")
                val backup = evidence(check, current)
                owner("snapshot", current.toString())
                require(
                    (hash(JSONObject(owner("snapshot") ?: error("TABLE_ROW_MISSING"))) ==
                        expectedCurrentHash),
                    "TABLE_ROLLBACK_READBACK_MISMATCH",
                )
                meta
                    .put("phase", "ROLLED_BACK")
                    .put("rollback_request", requestId)
                    .put("rollback_hash", expectedCurrentHash)
                    .put("rollback_backup_hash", backup)
                owner(META, meta.toString())
                return@LAM meta
            }
        )
    }

    private @Throws(Exception::class) fun textChunks(
        table: String,
        column: String,
        where: String,
        arguments: Array<String>,
    ): String? {

        var count: Long
        sql.query("SELECT length(" + column + ") FROM " + table + " WHERE " + where, *arguments)
            .use { rows ->
                if (!(rows.next())) {
                    return null
                }
                count = rows.number(0)
            }
        var out = StringBuilder()
        run {
            var offset: Long = 0
            var firstIteration6 = true
            while (true) {
                if (firstIteration6) firstIteration6 = false
                else {
                    offset += 32768
                }
                if (!(offset < count)) break

                val chunk = Array(arguments.size + 1) { "" }
                chunk[0] = (offset + 1).toString()
                System.arraycopy(arguments, 0, chunk, 1, arguments.size)
                sql.query(
                        "SELECT substr(" + column + ",?,32768) FROM " + table + " WHERE " + where,
                        *chunk,
                    )
                    .use { rows ->
                        require(rows.next(), "TABLE_TEXT_CHANGED")
                        out.append(rows.text(0))
                    }
            }
        }
        return out.toString()
    }

    companion object {
        @JvmField val META: String = "business_tables:v1"
        @JvmField
        val COLLECTIONS: Array<String> =
            arrayOf("customers", "orders", "worklogs", "invoices", "photos")
        private val FILTERS: MutableSet<String> =
            HashSet(
                Arrays.asList(
                    "customer_id",
                    "billing_customer_id",
                    "order_id",
                    "worklog_id",
                    "work_date",
                    "status",
                    "name",
                )
            )
        private val JOURNAL_REVISION: String = "business_tables:journal_revision"
        private val PROJECTED_REVISION: String = "business_tables:projected_revision"

        private @JvmStatic @Throws(Exception::class) fun table(collection: String): String {

            for (value in COLLECTIONS) {
                if ((value == collection)) {
                    return "business_" + collection
                }
            }
            throw IOException("TABLE_COLLECTION_UNSUPPORTED")
        }

        private @JvmStatic @Throws(Exception::class) fun require(
            condition: Boolean,
            error: String,
        ): Unit {

            if (!(condition)) {
                throw IOException(error)
            }
        }

        private @JvmStatic @Throws(Exception::class) fun hash(value: Any?): String {

            return PixelMcpWriteTools.hash(value)
        }

        private @JvmStatic @Throws(Exception::class) fun id(row: JSONObject): String {

            var value = row.opt("id")
            require(value is String && !(((value as String)).isEmpty()), "TABLE_ID_REQUIRED")
            return (value as String)
        }

        private @JvmStatic @Throws(Exception::class) fun request(value: String?): Unit {

            require(
                value != null && value.matches(Regex("[A-Za-z0-9_-]{8,200}")),
                "TABLE_REQUEST_ID_REQUIRED",
            )
        }

        private @JvmStatic @Throws(Exception::class) fun selection(row: JSONObject): JSONObject {

            val result = JSONObject()
            for (key in
                arrayOf(
                    "id",
                    "name",
                    "title",
                    "company",
                    "first_name",
                    "last_name",
                    "street",
                    "postal_code",
                    "city",
                    "email",
                    "phone",
                    "active",
                    "customer_id",
                    "billing_customer_id",
                    "order_id",
                    "worklog_id",
                    "role",
                    "session_id",
                    "status",
                    "billed",
                    "work_date",
                    "capture_time",
                    "created_at",
                    "description",
                    "activity",
                    "notes",
                    "material",
                    "location",
                    "customer_name",
                    "order_title",
                    "invoice_number",
                    "offer_number",
                    "caption",
                    "original_name",
                    "filename",
                )) {
                if (row.has(key)) {
                    result.put(key, row.get(key))
                }
            }
            return result
        }

        private @JvmStatic @Throws(Exception::class) fun remainder(source: JSONObject): JSONObject {

            val result = JSONObject(source.toString())
            for (collection in COLLECTIONS) {
                result.remove(collection)
            }
            return result
        }

        private @JvmStatic @Throws(Exception::class) fun validateRows(source: JSONObject): Unit {

            for (collection in COLLECTIONS) {
                val rows = source.getJSONArray(collection)
                val seen = HashSet<String>()
                for (i in 0 until rows.length()) {
                    require(seen.add(id(rows.getJSONObject(i))), "TABLE_DUPLICATE_ID")
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun evidence(
            check: Evidence?,
            source: JSONObject,
        ): String {

            require(check != null, "TABLE_BACKUP_EVIDENCE_REQUIRED")
            val before = hash(source)
            val result = check!!.verify(JSONObject(source.toString()))
            require(
                result.matches(Regex("[a-f0-9]{64}")) && (before == hash(source)),
                "TABLE_BACKUP_EVIDENCE_INVALID",
            )
            return result
        }
    }
}
