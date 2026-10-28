package at.hausgarten.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Row storage for the five snapshot collections, in the existing database.
 * This storage boundary does not grant business, invoice or migration authority.
 * No constructor, read or schema upgrade starts a transfer. The caller of the
 * explicit transfer methods must verify backup bytes, originals and relations.
 */
final class BusinessTableStore {
    static final String META = "business_tables:v1";
    private static final String[] COLLECTIONS = {"customers", "orders", "worklogs", "invoices", "photos"};
    private static final Set<String> FILTERS = new HashSet<>(Arrays.asList(
        "customer_id", "billing_customer_id", "order_id", "worklog_id", "work_date", "status", "name"));

    interface Rows extends AutoCloseable {
        boolean next() throws Exception;
        String text(int column) throws Exception;
        long number(int column) throws Exception;
        void close() throws Exception;
    }
    interface Action<T> { T run() throws Exception; }
    interface Sql {
        Rows query(String sql, String... arguments) throws Exception;
        void execute(String sql, Object... arguments) throws Exception;
        <T> T transaction(Action<T> action) throws Exception;
    }
    /** Return the SHA256 of a fully verified backup bound to this exact source.
     * A client-supplied boolean or a historical backup filename is not evidence.
     * Implementations must also enforce the caller's encryption/originals policy.
     */
    interface Evidence { String verify(JSONObject source) throws Exception; }

    private final Sql sql;
    BusinessTableStore(Sql sql) { this.sql = sql; }

    private static String table(String collection) throws IOException {
        for (String value : COLLECTIONS) if (value.equals(collection)) return "business_" + collection;
        throw new IOException("TABLE_COLLECTION_UNSUPPORTED");
    }
    private static void require(boolean condition, String error) throws IOException {
        if (!condition) throw new IOException(error);
    }
    private static String hash(Object value) throws Exception { return PixelMcpWriteTools.hash(value); }
    private static String id(JSONObject row) throws Exception {
        Object value = row.opt("id");
        require(value instanceof String && !((String)value).isEmpty(), "TABLE_ID_REQUIRED");
        return (String)value;
    }
    private static void request(String value) throws IOException {
        require(value != null && value.matches("[A-Za-z0-9_-]{8,200}"), "TABLE_REQUEST_ID_REQUIRED");
    }

    /** Called only during a conscious preparation, not from onCreate/onUpgrade. */
    private void schema() throws Exception {
        for (String collection : COLLECTIONS) {
            String name = table(collection);
            sql.execute("CREATE TABLE IF NOT EXISTS " + name + " (generation TEXT NOT NULL,id TEXT NOT NULL,position INTEGER NOT NULL,record_json TEXT NOT NULL,record_hash TEXT NOT NULL,customer_id TEXT NOT NULL,billing_customer_id TEXT NOT NULL,order_id TEXT NOT NULL,worklog_id TEXT NOT NULL,work_date TEXT NOT NULL,status TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(generation,id),UNIQUE(generation,position))");
            for (String field : new String[]{"customer_id", "billing_customer_id", "order_id", "worklog_id", "work_date", "status", "name"})
                sql.execute("CREATE INDEX IF NOT EXISTS ix_" + name + "_" + field + " ON " + name + "(generation," + field + ")");
        }
    }
    private String owner(String key) throws Exception {
        return textChunks("owner_state", "value", "key=?", new String[]{key});
    }
    private void owner(String key, String value) throws Exception {
        sql.execute("INSERT OR REPLACE INTO owner_state(key,value) VALUES(?,?)", key, value);
    }
    private JSONObject metadata() throws Exception {
        String value = owner(META);
        if (value == null) return null;
        JSONObject result = new JSONObject(value);
        require(result.getInt("format") == 1, "TABLE_FORMAT_UNSUPPORTED");
        require(Arrays.asList("COPYING", "READY", "ACTIVE", "ROLLED_BACK").contains(result.getString("phase")), "TABLE_PHASE_UNSUPPORTED");
        return result;
    }
    private JSONObject transfer(String requestId) throws Exception {
        request(requestId);
        JSONObject meta = metadata();
        require(meta != null && requestId.equals(meta.optString("request")), "TABLE_TRANSFER_MISMATCH");
        return meta;
    }
    boolean active() throws Exception { JSONObject meta = metadata(); return meta != null && "ACTIVE".equals(meta.getString("phase")); }

    private static JSONObject remainder(JSONObject source) throws Exception {
        JSONObject result = new JSONObject(source.toString());
        for (String collection : COLLECTIONS) result.remove(collection);
        return result;
    }
    private static void validateRows(JSONObject source) throws Exception {
        for (String collection : COLLECTIONS) {
            JSONArray rows = source.getJSONArray(collection);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) require(seen.add(id(rows.getJSONObject(i))), "TABLE_DUPLICATE_ID");
        }
    }
    private static String evidence(Evidence check, JSONObject source) throws Exception {
        require(check != null, "TABLE_BACKUP_EVIDENCE_REQUIRED");
        String before = hash(source), result = check.verify(new JSONObject(source.toString()));
        require(result != null && result.matches("[a-f0-9]{64}") && before.equals(hash(source)), "TABLE_BACKUP_EVIDENCE_INVALID");
        return result;
    }
    private JSONObject source(JSONObject meta, Evidence check) throws Exception {
        String raw = owner("snapshot");
        require(raw != null && hash(raw).equals(meta.getString("source_text_hash")), "TABLE_SOURCE_CHANGED");
        JSONObject value = new JSONObject(raw);
        require(hash(value).equals(meta.getString("source_hash")), "TABLE_SOURCE_CHANGED");
        require(evidence(check, value).equals(meta.getString("backup_hash")), "TABLE_BACKUP_CHANGED");
        return value;
    }

    JSONObject prepare(String requestId, String expectedSourceHash, Evidence check) throws Exception {
        request(requestId);
        return sql.transaction(() -> {
            String raw = owner("snapshot");
            require(raw != null, "TABLE_PERSISTED_SNAPSHOT_REQUIRED");
            JSONObject source = new JSONObject(raw);
            require(hash(source).equals(expectedSourceHash), "TABLE_SOURCE_CHANGED");
            validateRows(source);
            String backup = evidence(check, source);
            JSONObject existing = metadata();
            if (existing != null) {
                require(requestId.equals(existing.getString("request")) && expectedSourceHash.equals(existing.getString("source_hash")) && backup.equals(existing.getString("backup_hash")), "TABLE_TRANSFER_EXISTS");
                require(!"ROLLED_BACK".equals(existing.getString("phase")), "TABLE_TRANSFER_ALREADY_ROLLED_BACK");
                return existing;
            }
            schema();
            JSONObject meta = new JSONObject().put("format", 1).put("request", requestId)
                .put("generation", hash(requestId)).put("phase", "COPYING")
                .put("source_hash", expectedSourceHash).put("source_text_hash", hash(raw))
                .put("backup_hash", backup).put("remainder", remainder(source))
                .put("collection", 0).put("offset", 0);
            owner(META, meta.toString());
            return meta;
        });
    }

    /** Each bounded batch and its checkpoint commit together; restart never activates it. */
    JSONObject copyBatch(String requestId, int maximumRows, Evidence check) throws Exception {
        require(maximumRows > 0 && maximumRows <= 1000, "TABLE_BATCH_OUT_OF_RANGE");
        return sql.transaction(() -> {
            JSONObject meta = transfer(requestId), source = source(meta, check);
            if ("READY".equals(meta.getString("phase"))) return meta;
            require("COPYING".equals(meta.getString("phase")), "TABLE_COPY_PHASE_REQUIRED");
            int collection = meta.getInt("collection"), offset = meta.getInt("offset"), copied = 0;
            while (collection < COLLECTIONS.length && copied < maximumRows) {
                JSONArray rows = source.getJSONArray(COLLECTIONS[collection]);
                if (offset == rows.length()) { collection++; offset = 0; continue; }
                insert(meta.getString("generation"), COLLECTIONS[collection], offset, rows.getJSONObject(offset));
                offset++; copied++;
            }
            while (collection < COLLECTIONS.length && offset == source.getJSONArray(COLLECTIONS[collection]).length()) { collection++; offset = 0; }
            meta.put("collection", collection).put("offset", offset);
            if (collection == COLLECTIONS.length) {
                require(hash(materialize(meta)).equals(meta.getString("source_hash")), "TABLE_READBACK_MISMATCH");
                meta.put("phase", "READY");
            }
            owner(META, meta.toString());
            return meta;
        });
    }

    JSONObject activate(String requestId, String expectedSourceHash, Evidence check) throws Exception {
        return sql.transaction(() -> {
            JSONObject meta = transfer(requestId);
            require(expectedSourceHash.equals(meta.getString("source_hash")), "TABLE_SOURCE_CHANGED");
            if ("ACTIVE".equals(meta.getString("phase"))) return meta;
            require("READY".equals(meta.getString("phase")), "TABLE_NOT_READY");
            source(meta, check);
            JSONObject readback = materialize(meta);
            require(hash(readback).equals(expectedSourceHash), "TABLE_READBACK_MISMATCH");
            require(evidence(check, readback).equals(meta.getString("backup_hash")), "TABLE_BACKUP_CHANGED");
            owner("business_tables:original:" + meta.getString("generation"), owner("snapshot"));
            meta.put("phase", "ACTIVE");
            owner(META, meta.toString());
            return meta;
        });
    }

    private JSONObject activeMetadata() throws Exception {
        JSONObject meta = metadata();
        require(meta != null && "ACTIVE".equals(meta.getString("phase")), "TABLE_STORAGE_NOT_ACTIVE");
        return meta;
    }
    private void insert(String generation, String collection, long position, JSONObject row) throws Exception {
        sql.execute("INSERT INTO " + table(collection) + "(generation,id,position,record_json,record_hash,customer_id,billing_customer_id,order_id,worklog_id,work_date,status,name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            generation, id(row), position, row.toString(), hash(row), row.optString("customer_id"), row.optString("billing_customer_id"), row.optString("order_id"), row.optString("worklog_id"), row.optString("work_date"), row.optString("status"), row.optString("name", row.optString("title")));
    }
    private JSONObject row(JSONObject meta, String collection, String id) throws Exception {
        String name = table(collection), generation = meta.getString("generation"), expected;
        try (Rows rows = sql.query("SELECT record_hash FROM " + name + " WHERE generation=? AND id=?", generation, id)) {
            if (!rows.next()) return null;
            expected = rows.text(0);
        }
        String raw = textChunks(name, "record_json", "generation=? AND id=?", new String[]{generation, id});
        require(raw != null, "TABLE_ROW_MISSING");
        JSONObject result = new JSONObject(raw);
        require(id.equals(id(result)) && expected.equals(hash(result)), "TABLE_ROW_HASH_MISMATCH");
        return result;
    }
    JSONObject get(String collection, String id) throws Exception {
        return sql.transaction(() -> row(activeMetadata(), collection, id));
    }
    JSONArray select(String collection, String field, String value, int offset, int limit) throws Exception {
        require(FILTERS.contains(field), "TABLE_FILTER_UNSUPPORTED");
        require(offset >= 0 && limit >= 1 && limit <= 100, "TABLE_PAGE_OUT_OF_RANGE");
        return sql.transaction(() -> {
            JSONObject meta = activeMetadata(); JSONArray ids = new JSONArray(), out = new JSONArray();
            try (Rows rows = sql.query("SELECT id FROM " + table(collection) + " WHERE generation=? AND " + field + "=? ORDER BY position LIMIT ? OFFSET ?", meta.getString("generation"), value, Integer.toString(limit), Integer.toString(offset))) {
                while (rows.next()) ids.put(rows.text(0));
            }
            for (int i = 0; i < ids.length(); i++) out.put(row(meta, collection, ids.getString(i)));
            return out;
        });
    }

    /** Domain rules run in the caller's transaction. This method supplies CAS and retry identity. */
    JSONObject put(String collection, JSONObject value, String expectedHash, String requestId) throws Exception {
        return mutate(collection, id(value), value, expectedHash, requestId);
    }
    JSONObject remove(String collection, String id, String expectedHash, String requestId) throws Exception {
        require(expectedHash != null && !expectedHash.isEmpty(), "TABLE_EXPECTED_HASH_REQUIRED");
        return mutate(collection, id, null, expectedHash, requestId);
    }
    private JSONObject mutate(String collection, String id, JSONObject value, String expectedHash, String requestId) throws Exception {
        request(requestId); table(collection);
        require(expectedHash != null, "TABLE_EXPECTED_HASH_REQUIRED");
        return sql.transaction(() -> {
            JSONObject meta = activeMetadata();
            String fingerprint = hash(new JSONObject().put("generation", meta.getString("generation")).put("collection", collection).put("id", id).put("value", value == null ? JSONObject.NULL : value).put("expected", expectedHash));
            String key = "business_tables:receipt:" + requestId, saved = owner(key);
            if (saved != null) { JSONObject receipt = new JSONObject(saved); require(fingerprint.equals(receipt.getString("fingerprint")), "TABLE_REQUEST_REUSED"); return receipt; }
            JSONObject current = row(meta, collection, id);
            require(expectedHash.equals(current == null ? "" : hash(current)), "TABLE_WRITE_CONFLICT");
            long position;
            try (Rows rows = sql.query("SELECT position FROM " + table(collection) + " WHERE generation=? AND id=?", meta.getString("generation"), id)) {
                if (rows.next()) position = rows.number(0);
                else try (Rows last = sql.query("SELECT COALESCE(MAX(position),-1)+1 FROM " + table(collection) + " WHERE generation=?", meta.getString("generation"))) { require(last.next(), "TABLE_POSITION_UNAVAILABLE"); position = last.number(0); }
            }
            sql.execute("DELETE FROM " + table(collection) + " WHERE generation=? AND id=?", meta.getString("generation"), id);
            if (value != null) insert(meta.getString("generation"), collection, position, value);
            JSONObject receipt = new JSONObject().put("fingerprint", fingerprint).put("record_hash", value == null ? JSONObject.NULL : hash(value)).put("deleted", value == null);
            owner(key, receipt.toString());
            return receipt;
        });
    }

    /** Bulk reconstruction is reserved for full export, comparison and rollback. */
    JSONObject exportSnapshot() throws Exception { return sql.transaction(() -> materialize(activeMetadata())); }
    private JSONObject materialize(JSONObject meta) throws Exception {
        JSONObject result = new JSONObject(meta.getJSONObject("remainder").toString());
        for (String collection : COLLECTIONS) {
            JSONArray ids = new JSONArray(), records = new JSONArray();
            try (Rows rows = sql.query("SELECT id FROM " + table(collection) + " WHERE generation=? ORDER BY position", meta.getString("generation"))) { while (rows.next()) ids.put(rows.text(0)); }
            for (int i = 0; i < ids.length(); i++) records.put(row(meta, collection, ids.getString(i)));
            result.put(collection, records);
        }
        return result;
    }
    /** Always reconstruct the current rows: never discard writes made after activation. */
    JSONObject rollback(String requestId, String expectedCurrentHash, Evidence check) throws Exception {
        request(requestId);
        return sql.transaction(() -> {
            JSONObject meta = metadata();
            require(meta != null, "TABLE_STORAGE_NOT_ACTIVE");
            if ("ROLLED_BACK".equals(meta.getString("phase"))) {
                require(requestId.equals(meta.optString("rollback_request")) && expectedCurrentHash.equals(meta.optString("rollback_hash")), "TABLE_ROLLBACK_MISMATCH");
                return meta;
            }
            require("ACTIVE".equals(meta.getString("phase")), "TABLE_STORAGE_NOT_ACTIVE");
            JSONObject current = materialize(meta);
            require(hash(current).equals(expectedCurrentHash), "TABLE_WRITE_CONFLICT");
            String backup = evidence(check, current);
            owner("snapshot", current.toString());
            require(hash(new JSONObject(owner("snapshot"))).equals(expectedCurrentHash), "TABLE_ROLLBACK_READBACK_MISMATCH");
            meta.put("phase", "ROLLED_BACK").put("rollback_request", requestId).put("rollback_hash", expectedCurrentHash).put("rollback_backup_hash", backup);
            owner(META, meta.toString());
            return meta;
        });
    }
    /** Chunk by SQLite characters, avoiding Android's bounded CursorWindow for large rows. */
    private String textChunks(String table, String column, String where, String[] arguments) throws Exception {
        long count;
        try (Rows rows = sql.query("SELECT length(" + column + ") FROM " + table + " WHERE " + where, arguments)) { if (!rows.next()) return null; count = rows.number(0); }
        StringBuilder out = new StringBuilder();
        for (long offset = 0; offset < count; offset += 32768) {
            String[] chunk = new String[arguments.length + 1]; chunk[0] = Long.toString(offset + 1); System.arraycopy(arguments, 0, chunk, 1, arguments.length);
            try (Rows rows = sql.query("SELECT substr(" + column + ",?,32768) FROM " + table + " WHERE " + where, chunk)) { require(rows.next(), "TABLE_TEXT_CHANGED"); out.append(rows.text(0)); }
        }
        return out.toString();
    }
}
