package at.hausgarten.service

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Production repository/SQLite/coroutines, with a new synthetic target-UID root per invocation. */
class NativeDataAccessNativeTests private constructor(private val test: Instrumentation) {
    companion object {
        @JvmStatic fun run(test: Instrumentation): JSONObject {
            val fixture = NativeDataAccessNativeTests(test)
            try { return fixture.execute() }
            catch (failure: Throwable) { throw IllegalStateException("QA_NATIVE_DATA_PHASE_${fixture.phase}", failure) }
        }
    }

    private var phase = "guards"
    private var checks = 0
    private fun yes(condition: Boolean, code: String) {
        if (!condition) throw AssertionError("QA_NATIVE_DATA_$code")
        checks++
    }

    private fun execute(): JSONObject {
        // All guards precede any target storage lookup, preference access or database open.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_APK_REQUIRED")
        yes(test.targetContext.packageName == "at.hausgarten.service", "TARGET_PACKAGE_REQUIRED")
        yes(Build.HARDWARE in listOf("ranchu", "goldfish"), "DISPOSABLE_EMULATOR_REQUIRED")
        yes(Build.VERSION.SDK_INT == 35, "API35_REQUIRED")
        val qemu = test.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        }
        yes(qemu == "1", "QEMU_REQUIRED")
        yes(Looper.myLooper() != Looper.getMainLooper(), "INSTRUMENTATION_WORKER_REQUIRED")
        val owner = test.targetContext.createDeviceProtectedStorageContext()
        yes(owner.isDeviceProtectedStorage, "DEVICE_PROTECTED_OWNER")
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-native-data-$token")
        yes(!root.exists() && root.mkdir(), "NEW_SYNTHETIC_ROOT")
        val context = FixtureContext(owner, root, "qa_native_data_$token")
        for (path in listOf(context.dataDir, context.filesDir, context.noBackupFilesDir, context.cacheDir,
            context.getDatabasePath("haus-garten-mobile.db"))) {
            yes(path.canonicalPath == root.canonicalPath || path.canonicalPath.startsWith(root.canonicalPath + File.separator), "ISOLATED_PATH")
        }
        return PixelLocalStore(context).use { store ->
            runBlocking {
                val data = store.nativeData()
                phase = "scale"
                val fixture = scaleFixture()
                store.putOwnerValue("snapshot", fixture.toString())
                val count = data.list("worklogs", offset = 115, limit = 50)
                yes(count.rows.size == 50 && count.total == 38_800 && count.hasMore, "BOUNDED_SCALE_PAGE")
                yes(count.rows.zipWithNext().all { (a, b) -> a.workDate > b.workDate || a.workDate == b.workDate && a.id < b.id }, "HISTORY_SORT")
                val projected = data.list("worklogs", propertyId = "synthetic_property", dateFrom = "2026-10-01", dateTo = "2026-10-28", limit = 13)
                yes(projected.rows.size == 13 && projected.total > 1000, "OBJECT_DATE_FILTER")
                val firstDiagnostics = data.diagnostics()
                yes(firstDiagnostics.getString("journal_mode") == "wal" && store.writableDatabase.isWriteAheadLoggingEnabled, "RUNTIME_WAL")
                val plans = firstDiagnostics.getJSONObject("plans")
                for (name in plans.keys()) {
                    val plan = plans.getJSONArray(name).toString()
                    yes(plan.contains("USING INDEX ix_native_read_") && !plan.contains("TEMP B-TREE"), "INDEX_$name")
                }
                store.readableDatabase.rawQuery("SELECT count(*) FROM native_read_rows WHERE collection IN ('customers','orders','worklogs')", null).use { rows ->
                    yes(rows.moveToFirst() && rows.getInt(0) == 40_000, "ACTUAL_40000_PROJECTION_ROWS")
                }
                store.readableDatabase.rawQuery("PRAGMA table_info(native_read_rows)", null).use { rows ->
                    val names = ArrayList<String>()
                    while (rows.moveToNext()) names.add(rows.getString(1))
                    yes("record_json" !in names && "selection_json" !in names && "notes" !in names, "DISPLAY_COLUMNS_ONLY")
                }
                val detail = data.detail("worklogs", "qa_work_00000")!!
                yes(detail.getString("notes") == longNote(), "CHUNKED_UNICODE_DETAIL")
                for (i in 0 until 5) {
                    data.list("customers", search = "Muster", limit = 7, offset = i)
                    data.list("worklogs", propertyId = "synthetic_property", limit = 20, offset = i)
                    data.detail("worklogs", "qa_work_00000")
                }
                yes(data.diagnostics().getLong("rebuild_count") == firstDiagnostics.getLong("rebuild_count"), "REPEATED_READS_REUSE_CACHE")
                phase = "mcp_parity"
                mcpParity(store, fixture)

                // A small second source keeps mutation/Flow checks independent of scale timings.
                val smallSource = SyntheticBusinessData.profile("minimal")
                    .put("offers", JSONArray().put(JSONObject().put("id", "qa_offer_sent").put("title", "QA offer")
                        .put("status", "draft").put("delivery_status", "sent").put("gross_total", "123.45").put("assumptions", "QA Suchannahme")))
                    .put("service_catalog", JSONArray().put(JSONObject().put("id", "svc_qa_projection").put("title", "QA service")
                        .put("category", "QA category").put("default_price", "7.50").put("unit", "h").put("active", true)))
                    .put("appointments", JSONArray().put(JSONObject().put("id", "qa_appointment").put("title", "QA appointment")
                        .put("starts_at", "2026-10-28T23:30:00Z")))
                store.putOwnerValue("snapshot", smallSource.toString())
                yes(data.list("offers", status = "sent").rows.single().id == "qa_offer_sent", "OFFER_DELIVERY_STATUS_FILTER")
                yes(data.list("offers", status = "sent").rows.single().subtitle.contains("123,45"), "PROJECTED_OFFER_AMOUNT")
                val service = data.list("service_catalog", search = "QA service").rows.single()
                yes(service.subtitle.contains("QA category") && service.subtitle.contains("7,50") && service.subtitle.contains(" / h"), "PROJECTED_CATALOG_PRICE_CATEGORY_UNIT")
                yes(data.list("offers", status = "draft").rows.single().id == "qa_offer_sent", "OFFER_DOMAIN_STATUS_FILTER")
                yes(data.list("offers", search = "Suchannahme").total == 1, "OFFER_SEARCH_FIELDS")
                yes(data.list("appointments", dateFrom = "2026-10-29", dateTo = "2026-10-29").total == 1, "VIENNA_DAY_BOUNDARY")
                smallSource.getJSONArray("appointments").put(JSONObject().put("id", "qa_later_appointment").put("title", "QA later").put("starts_at", "2027-01-01T09:00:00Z"))
                store.putOwnerValue("snapshot", smallSource.toString())
                yes(data.list("appointments", limit = 1, chronological = true).rows.single().id == "qa_appointment", "NEXT_APPOINTMENT_BEFORE_PAGE_LIMIT")
                yes(data.list("appointments", limit = 1).rows.single().id == "qa_later_appointment", "DEFAULT_HISTORY_ORDER_UNCHANGED")
                val timer = store.readSource().stateFor("get_active_work_session", JSONObject())
                yes(timer.getJSONObject("master").length() == 0 && timer.getJSONArray("pending").length() == 0 && timer.optBoolean("storage_point_read") && timer.getJSONObject("work_session").has("state"), "MCP_TIMER_MINIMAL_STATE")
                phase = "transfer_cache"
                transferCacheProof(store, data)
                val equipment = JSONObject().put("id", "qa_equipment").put("kind", "equipment").put("format_version", 1)
                    .put("name", "QA initial").put("condition", "ready").put("test_record", true)
                val key = "revier:v1:entity:equipment:qa_equipment"
                store.putOwnerValue(key, equipment.toString())
                store.putOwnerValue("revier:v1:entity:property:qa_property", JSONObject().put("id", "qa_property").put("kind", "property")
                    .put("format_version", 1).put("name", "QA property").put("address", "QA Synthetic Street 1").toString())
                yes(data.list("equipment", status = "ready").total == 1, "EQUIPMENT_CONDITION")
                yes(data.list("properties").rows.single().address == "QA Synthetic Street 1", "PROPERTY_ADDRESS")
                store.putOwnerValue("revier:v1:entity:equipment_usage:qa_usage", JSONObject().put("id", "qa_usage").put("kind", "equipment_usage")
                    .put("format_version", 1).put("equipment_id", "qa_equipment").put("created_at", "2026-10-29T09:00:00Z").toString())
                yes(data.history("equipment_usage", "equipment_id", "qa_equipment").single().getString("id") == "qa_usage", "BOUNDED_RELATION_HISTORY")
                phase = "receipt"
                receiptProof(store, data)
                phase = "main_dispatcher"
                mainDispatcherProof(store)
                phase = "flow_wal"
                flowAndWalProof(store, data, key, equipment)
                phase = "cache_photo_close"
                cachePhotoConcurrencyProof(context)
                phase = "cache_init_close"
                cacheInitializationCloseProof(context)
                phase = "cache_schema_repair"
                cacheSchemaRepairProof(context)
                phase = "business_transaction_lifetime"
                businessTransactionLifetimeProof(context)
                phase = "snapshot_close_lifetime"
                snapshotCloseLifetimeProof(context)
                phase = "thumbnail_tiles"
                thumbnailTileProof(context, store)
                phase = "upload_publication"
                uploadPublicationProof(context)
                phase = "upload_chunk_bounds"
                chunkedUploadStorageProof(context)
                phase = "immutable_publication"
                immutablePublishedFileProof(context, store)
                phase = "office_baseline"
                val office = officeReadMeasurements(context, store, data)
                phase = "local_storage_cleanup"
                localStorageCleanup(owner)
                phase = "final_revision"
                val final = data.diagnostics()
                yes(final.getLong("source_revision") == final.getLong("cached_revision"), "COMMITTED_CACHE_REVISION")
                JSONObject().put("checks", checks).put("synthetic_only", true).put("productive_data_read", false)
                    .put("isolated_context_paths", true).put("projection_rows", 40_000).put("plans", plans)
                    .put("real_sqlite_transactions", true).put("wal_concurrent_reader", true).put("actual_coroutine_flow", true)
                    .put("local_storage_cleanup", true).put("local_delete_without_cloud_guards", true)
                    .put("cache_photo_concurrency", true)
                    .put("cache_initialization_close_concurrency", true)
                    .put("upload_publication_backup_concurrency", true)
                    .put("chunk_upload_prefix_storage_bounded", true)
                    .put("main_thread_disk_violations", 0).put("performance_claim", false).put("office_reads", office)
            }
        }
    }

    private fun localStorageCleanup(owner: Context) {
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-local-cleanup-$token")
        yes(!root.exists() && root.mkdir(), "CLEANUP_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_cleanup_$token")
        fun file(directory: String, name: String, content: ByteArray = ByteArray(8192) { 7 }): File =
            File(File(context.filesDir, directory).apply { mkdirs() }, name).apply { writeBytes(content) }
        val issued = file("business-documents", "qa-issued.pdf")
        val draft = file("business-documents", "qa-draft.pdf")
        val lost = file("customer-photo-originals", "qa-unused.jpg")
        val browser = file("browser-media", "a".repeat(64) + ".blob")
        val pending = file("pending-work-photo-files", "b".repeat(64) + ".jpg")
        val staged = file("native-form-media", "qa-form.jpg")
        staged.setLastModified(0)
        File(context.filesDir, "pending-work-photo.json").writeText(JSONObject().put("sha256", "b".repeat(64)).toString())
        val fixture = SyntheticBusinessData.profile("minimal")
        fixture.getJSONArray("invoices").put(JSONObject().put("id", "qa_issued").put("invoice_number", "QA-001")
            .put("issued_at", "2026-10-30T10:00:00Z").put("customer_id", "qa_customer_1").put("pdf_storage_name", issued.name))
        fixture.getJSONArray("invoices").put(JSONObject().put("id", "qa_draft").put("invoice_number", "Entwurf").put("status", "Entwurf")
            .put("customer_id", "qa_customer_1").put("pdf_storage_name", draft.name))
        fixture.getJSONArray("appointments").put(JSONObject().put("id", "qa_appointment").put("customer_id", "qa_customer_1")
            .put("order_id", "qa_order_1").put("title", "QA Erledigter Termin").put("status", "completed"))
        fixture.getJSONArray("worklogs").getJSONObject(0).put("appointment_id", "qa_appointment")
        val linkedInvoice = JSONObject(fixture.toString())
        linkedInvoice.getJSONArray("invoices").getJSONObject(0).put("worklog_ids", JSONArray().put("qa_work_1"))
        var protected = false
        try { OwnerJournalRules.validateArchive("order", linkedInvoice.getJSONArray("orders").getJSONObject(0), linkedInvoice) } catch (_: IllegalArgumentException) { protected = true }
        yes(protected, "ISSUED_WORK_PROTECTS_PARENT_ORDER")
        context.getSharedPreferences("haus_garten", 0).edit().putString("drive_snapshot", fixture.toString()).commit()
        lateinit var keptPhoto: File
        PixelLocalStore(context).use { store ->
            val db = store.writableDatabase
            store.putOwnerValue("business_tables:original:qa_obsolete", fixture.toString())
            store.putOwnerValue("browser_media:v1:record:qa", JSONObject().put("graph", JSONObject().put("sha256", "a".repeat(64))).toString())
            store.putOwnerValue("native-form:qa", JSONObject().put("uri", staged.toURI().toString()).toString())
            store.putOwnerValue("revier:v1:entity:inquiry:qa_inspection", JSONObject().put("format_version", 1).put("kind", "inquiry")
                .put("id", "qa_inspection").put("customer_id", "qa_customer_1").put("inspection_appointment_id", "qa_appointment")
                .put("inspection_visits", JSONArray().put(JSONObject().put("appointment_id", "qa_appointment").put("notes", "QA Befund erhalten"))).toString())
            db.execSQL("INSERT INTO mcp_uploads VALUES('qa_old_upload', '{}', ?)", arrayOf(ByteArray(1024 * 1024)))
            db.execSQL("INSERT INTO mcp_uploads VALUES('qa_retained_backup', '{\"kind\":\"backup\"}', ?)", arrayOf(ByteArray(4096) { 42 }))
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val encoded = java.io.ByteArrayOutputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            bitmap.recycle()
            fun photo(request: String): String = store.savePhoto(JSONObject().put("customer_id", "qa_customer_1").put("order_id", "qa_order_1")
                .put("_photo_request_id", request).put("_capture_context", JSONObject().put("installation_id", "old-device").put("restore_epoch", "old-epoch"))
                .put("data", "data:image/png;base64," + android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)))
            val first = photo("qa_photo_cleanup_first")
            val second = photo("qa_photo_cleanup_second")
            val access = PixelOwnerAccess(context, store)
            var sequence = 0
            fun command(name: String, entity: String, id: String): JSONObject = access.execute(name,
                JSONObject().put("request_id", "qa_cleanup_${sequence++}").put("entity", entity).put("entity_id", id).apply { if (name == "purge_entity") put("confirmed", true) })
            fun staleDeletion(name: String, args: JSONObject) {
                val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
                var rejected = false
                try { access.execute(name, args.put("request_id", "qa_stale_delete_${sequence++}")) }
                catch (failure: PixelMcpWriteTools.Rejected) { rejected = failure.code == "WRITE_CONFLICT" }
                yes(rejected, "STALE_DELETE_PROOF_REJECTED_$name")
                yes(before == PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())), "STALE_DELETE_PRESERVES_DATA_$name")
            }
            staleDeletion("archive_entity", JSONObject().put("entity", "order").put("entity_id", "qa_order_1").put("expected_record_hash", "0".repeat(64)))
            staleDeletion("archive_entity", JSONObject().put("entity", "order").put("entity_id", "qa_order_1").put("expected_photo_links_hash", "0".repeat(64)))
            command("archive_entity", "order", "qa_order_1")
            staleDeletion("purge_entity", JSONObject().put("entity", "order").put("entity_id", "qa_order_1").put("confirmed", true).put("expected_record_hash", "0".repeat(64)))
            staleDeletion("empty_trash", JSONObject().put("confirmed", true).put("expected_preview_hash", "0".repeat(64)))
            var current = PixelMcpReadTools.effective(store.getState())
            yes(current.getJSONArray("orders").length() == 0, "ORDER_ARCHIVED_WITH_WORK")
            yes(current.getJSONArray("worklogs").getJSONObject(0).optString("order_id").isEmpty(), "WORK_ORDER_DETACHED")
            yes(current.getJSONArray("worklogs").getJSONObject(0).getString("customer_id") == "qa_customer_1", "WORK_CUSTOMER_PRESERVED")
            yes(current.getJSONArray("photos").getJSONObject(0).optString("order_id").isEmpty(), "PHOTO_ORDER_DETACHED")
            command("purge_entity", "order", "qa_order_1")
            yes(!OwnerJournalRules.archives(PixelMcpReadTools.effective(store.getState())).has("order:qa_order_1"), "NO_ORDER_TOMBSTONE")
            command("archive_entity", "appointment", "qa_appointment")
            yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").getJSONObject(0).optString("appointment_id").isEmpty(), "COMPLETED_WORK_APPOINTMENT_DETACHED")
            command("purge_entity", "appointment", "qa_appointment")
            val inspection = JSONObject(store.ownerValue("revier:v1:entity:inquiry:qa_inspection", "{}"))
            yes(inspection.optString("inspection_appointment_id").isEmpty() && inspection.getJSONArray("inspection_visits").getJSONObject(0).optString("appointment_id").isEmpty(), "INSPECTION_REFERENCES_DETACHED")
            yes(inspection.optString("customer_id") == "qa_customer_1" && inspection.getJSONArray("inspection_visits").getJSONObject(0).optString("notes") == "QA Befund erhalten", "INSPECTION_HISTORY_PRESERVED")
            val metadata = store.photoAttachment(first)!!
            keptPhoto = File(File(context.filesDir, "customer-photo-originals"), metadata.getString("storage_name"))
            store.putOwnerValue("receipt:qa_deleted_photo", JSONObject().put("result", JSONObject().put("photo_id", first)).toString())
            db.execSQL("INSERT INTO pending VALUES('receipt_qa_deleted_photo','mcp_receipt',?,'2026-10-30T00:00:00Z')", arrayOf(JSONObject().put("entity_type", "photo").put("entity_id", first).toString()))
            command("archive_entity", "photo", first)
            yes(keptPhoto.isFile, "TRASH_RETAINS_SHARED_PHOTO")
            command("purge_entity", "photo", first)
            yes(keptPhoto.isFile, "ACTIVE_SHARED_PHOTO_RETAINED")
            yes(store.ownerValue("receipt:qa_deleted_photo", "").isEmpty() && store.ownerValue("photo-ui:qa_photo_cleanup_first", "").isEmpty(), "DELETED_PHOTO_RECEIPTS_REMOVED")
            db.rawQuery("SELECT count(*) FROM pending WHERE id='receipt_qa_deleted_photo'", null).use { rows -> yes(rows.moveToFirst() && rows.getInt(0) == 0, "DELETED_NATIVE_RECEIPT_REMOVED") }
            command("archive_entity", "invoice", "qa_draft")
            yes(draft.isFile, "TRASH_RETAINS_DRAFT_PDF")
            command("purge_entity", "invoice", "qa_draft")
            yes(!draft.exists() && issued.isFile, "DRAFT_PDF_REMOVED_ISSUED_RETAINED")
            var rejected = false
            try { command("archive_entity", "invoice", "qa_issued") } catch (_: Exception) { rejected = true }
            yes(rejected && issued.isFile, "ISSUED_INVOICE_PROTECTED")
            val orphan = File(context.noBackupFilesDir, "pixel-backup/attempt-abandoned/capture/large.bin")
            orphan.parentFile!!.mkdirs(); orphan.writeBytes(ByteArray(16384))
            val oldDeletion = File(context.noBackupFilesDir, "deletion-backups/qa-orphan.zip")
            oldDeletion.parentFile!!.mkdirs(); oldDeletion.writeBytes(ByteArray(8192))
            val maintenance = LocalStorageMaintenance(context, store)
            val cleaned = maintenance.clean(true)
            yes(cleaned.getLong("database_bytes") > 0 && cleaned.getLong("freed_bytes") > 0, "ACTUAL_USAGE_AND_RECLAIM")
            yes(!orphan.exists() && !lost.exists(), "ORPHANS_REMOVED")
            yes(oldDeletion.isFile && oldDeletion.readBytes().contentEquals(ByteArray(8192)), "HISTORICAL_BACKUP_BYTES_RETAINED")
            db.rawQuery("SELECT data FROM mcp_uploads WHERE id='qa_retained_backup'", null).use { rows -> yes(rows.moveToFirst() && rows.getBlob(0).contentEquals(ByteArray(4096) { 42 }), "HISTORICAL_BACKUP_BLOB_RETAINED") }
            yes(browser.isFile && pending.isFile && staged.isFile && keptPhoto.isFile && issued.isFile, "ALL_LIVE_REFERENCES_RETAINED")
            yes(!context.getSharedPreferences("haus_garten", 0).contains("drive_snapshot"), "LEGACY_PREFERENCES_REMOVED")
            db.rawQuery("SELECT count(*) FROM owner_state WHERE key GLOB 'business_tables:original:*' OR key GLOB 'receipt_guard:*'", null).use { rows -> yes(rows.moveToFirst() && rows.getInt(0) == 0, "OLD_DUPLICATES_REMOVED") }
            db.rawQuery("SELECT count(*) FROM mcp_uploads WHERE id='qa_old_upload'", null).use { rows -> yes(rows.moveToFirst() && rows.getInt(0) == 0, "ORPHAN_UPLOAD_BLOB_REMOVED") }
            command("archive_entity", "photo", second)
            access.execute("empty_trash", JSONObject().put("request_id", "qa_cleanup_empty_trash").put("confirmed", true))
            yes(!keptPhoto.exists(), "LAST_PHOTO_REFERENCE_RECLAIMED_IMMEDIATELY")
            current = PixelMcpReadTools.effective(store.getState())
            yes(current.getJSONArray("worklogs").length() == 1 && current.getJSONArray("invoices").length() == 1, "BUSINESS_ROWS_PRESERVED")
            yes(OwnerJournalRules.archives(current).length() == 0, "TRASH_PHYSICALLY_EMPTY")
        }
        PixelLocalStore(context).use { reopened ->
            val current = PixelMcpReadTools.effective(reopened.getState())
            yes(current.getJSONArray("customers").length() == 1 && current.getJSONArray("worklogs").length() == 1 && current.getJSONArray("invoices").length() == 1, "COMPACTED_REOPEN_EXACT_ROWS")
            yes(current.getJSONArray("photos").length() == 0 && current.getJSONArray("orders").length() == 0 && OwnerJournalRules.archives(current).length() == 0, "DELETED_ROWS_DO_NOT_REAPPEAR")
        }
    }

    /** Office performance covers retained work-record reads; no invoice ledger exists in this build. */
    private suspend fun officeReadMeasurements(context: Context, store: PixelLocalStore, data: NativeDataRepository): JSONObject {
        val scope = "EMULATOR_WORK_RECORD_READS;NO_UI_OR_PIXEL_LATENCY"
        repeat(2) { data.list("worklogs", limit = 50) }
        val values = ArrayList<Double>()
        repeat(40) {
            val start = System.nanoTime()
            data.list("worklogs", limit = 50)
            values.add((System.nanoTime() - start) / 1_000_000.0)
        }
        val sorted = values.sorted()
        return JSONObject().put("scope", scope).put("samples_per_path", values.size)
            .put("billing_work_records", JSONObject().put("mean_ms", values.average())
                .put("p95_ms", sorted[37]).put("samples_ms", JSONArray(values)))
    }

    private fun longNote(): String = "QA synthetic 🌿 äß ".repeat(7000)

    private fun scaleFixture(): JSONObject {
        val fixture = SyntheticBusinessData.profile("minimal")
        val customers = JSONArray()
        for (i in 0 until 400) customers.put(JSONObject().put("id", "qa_customer_$i").put("name", "QA Musterkunde $i")
            .put("street", "QA Synthetic Street $i").put("active", if (i % 7 == 0) 0 else 1).put("test_record", true))
        val orders = JSONArray()
        for (i in 0 until 800) orders.put(JSONObject().put("id", "qa_order_$i").put("title", "QA order $i")
            .put("customer_id", "qa_customer_${i % 400}").put("status", if (i % 3 == 0) "Geplant" else "Aktiv").put("test_record", true))
        val work = JSONArray()
        for (i in 0 until 38_800) work.put(JSONObject().put("id", "qa_work_" + i.toString().padStart(5, '0'))
            .put("customer_id", "qa_customer_${i % 400}").put("order_id", "qa_order_${i % 800}")
            .put("property_id", if (i % 5 == 0) "other" else "synthetic_property")
            .put("work_date", "2026-10-" + (i % 28 + 1).toString().padStart(2, '0')).put("activity", "QA synthetic $i")
            .put("hours", 1).put("billed", i % 2).put("test_record", true).apply { if (i == 0) put("notes", longNote()) })
        return fixture.put("customers", customers).put("orders", orders).put("worklogs", work)
    }

    private fun mcpParity(store: PixelLocalStore, fixture: JSONObject) {
        for ((collection, kind, tool) in listOf(Triple("customers", "customer", "search_customers"), Triple("orders", "order", "search_orders"), Triple("worklogs", "worklog", "list_work_records"))) {
            for (filter in listOf(JSONObject().put("offset", 3).put("limit", 7), JSONObject().put("query", "QA 2").put("limit", 13))) {
                val actual = store.readSource().stateFor(tool, filter).getJSONObject("master").getJSONArray(collection)
                val expected = SnapshotReadSupport.select(fixture.getJSONArray(collection), filter, kind, fixture)
                yes(PixelMcpWriteTools.hash(actual) == PixelMcpWriteTools.hash(expected), "MCP_FILTER_$collection")
            }
        }
    }

    private suspend fun receiptProof(store: PixelLocalStore, data: NativeDataRepository) {
        val request = "qa_native_receipt_request"
        val args = JSONObject().put("request_id", request).put("activity", "QA synthetic")
        val receipt = JSONObject().put("tool", "create_work_record").put("fingerprint", PixelMcpWriteTools.hash(JSONObject().put("tool", "create_work_record").put("args", args)))
            .put("entity_type", "worklog").put("entity_id", "qa_work_1").put("work_record_id", "qa_work_1")
        val revision = data.diagnostics().getLong("rebuild_count")
        store.writableDatabase.execSQL("INSERT INTO pending(id,kind,payload,created_at) VALUES(?,?,?,?)", arrayOf(
            "receipt_mcp_pixel_" + PixelMcpWriteTools.hash(request).substring(0, 40), "mcp_receipt", receipt.toString(), "2026-10-29T00:00:00Z"))
        yes(PixelMcpWriteTools.hash(RevierLocalRepository(store, null).nativeReceipt(request)!!) == PixelMcpWriteTools.hash(receipt), "NATIVE_RECEIPT_UNCHANGED")
        yes(store.lookupNativeReceipt("qa_missing_receipt") == null, "MISSING_RECEIPT_NO_REPLAY")
        yes(data.diagnostics().getLong("rebuild_count") == revision, "RECEIPT_EXCLUDED_FROM_CACHE_INVALIDATION")
    }

    private suspend fun transferCacheProof(store: PixelLocalStore, data: NativeDataRepository) {
        val before = data.list("customers", limit = 5)
        val source = JSONObject(store.snapshotText(""))
        val tables = store.businessTables()
        val request = "qa_native_cache_transfer"
        tables.prepare(request, PixelMcpWriteTools.hash(source)) { PixelMcpWriteTools.hash("QA synthetic transfer evidence") }
        try {
            yes(tables.status().getString("phase") == "COPYING", "SYNTHETIC_TRANSFER_PREPARED_ONLY")
            yes(data.list("customers", limit = 5) == before, "DERIVED_CACHE_REFRESH_DURING_TRANSFER")
            var fenced = false
            try { store.writableDatabase.execSQL("UPDATE owner_state SET value=value WHERE key='snapshot'") }
            catch (failure: android.database.sqlite.SQLiteException) { fenced = failure.message.orEmpty().contains("TABLE_TRANSFER_IN_PROGRESS") }
            yes(fenced, "TRANSFER_AUTHORITY_WRITE_FENCE_RETAINED")
        } finally { tables.cancel(request) }
        yes(tables.status().getString("phase") == "ABORTED" && data.list("customers", limit = 5) == before, "TRANSFER_CANCEL_WITH_NATIVE_REVISION_TRIGGERS")
    }

    private suspend fun mainDispatcherProof(store: PixelLocalStore) {
        val violations = CopyOnWriteArrayList<String>()
        withContext(Dispatchers.Main) {
            val previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener(Executor { command -> command.run() }) { violations.add(it.javaClass.simpleName) }.build())
            try {
                val data = store.nativeData()
                data.list("customers")
                data.detail("customers", "qa_customer_1")
                data.history("equipment_usage", "equipment_id", "qa_equipment")
                data.diagnostics()
                withTimeout(10_000) { data.watchList("equipment").first() }
                withTimeout(10_000) { data.watchDetail("equipment", "qa_equipment").first() }
                withTimeout(10_000) { data.watchHistory("equipment_usage", "equipment_id", "qa_equipment").first() }
            } finally { StrictMode.setThreadPolicy(previous) }
        }
        yes(violations.isEmpty(), "NO_MAIN_THREAD_DATABASE_OR_FILE_WORK")
    }

    private suspend fun cachePhotoConcurrencyProof(owner: Context) {
        val context = FixtureContext(owner, File(owner.cacheDir, "photo-refresh-race"), "qa_photo_refresh_race")
        var store = PixelLocalStore(context)
        val writerHeld = CountDownLatch(1)
        val refreshAllowed = CountDownLatch(1)
        val refreshed = CompletableDeferred<Unit>()
        val saved = CompletableDeferred<String>()
        val closed = CompletableDeferred<Unit>()
        var completed = false
        try {
            val source = SyntheticBusinessData.profile("minimal")
            source.getJSONArray("customers").getJSONObject(0).put("name", "[TEST] QA concurrent customer")
            store.putOwnerValue("snapshot", source.toString())
            var data = store.nativeData()
            data.list("customers", limit = 5)
            store.putOwnerValue("revier:v1:entity:equipment:qa_refresh_photo", JSONObject().put("id", "qa_refresh_photo")
                .put("kind", "equipment").put("name", "[TEST] QA cache photo race").put("condition", "ready").toString())
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val bytes = try { java.io.ByteArrayOutputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            } } finally { bitmap.recycle() }
            val request = "qa_native_refresh_photo_race"
            val photo = JSONObject().put("customer_id", "qa_customer_1").put("caption", "[TEST] QA concurrent cache photo")
                .put("original_name", "qa-cache-photo.png").put("role", "documentation").put("test_record", true)
                .put("_photo_request_id", request).put("data", "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            val db = store.writableDatabase
            // Invoke the actual refresh at its transaction boundary, without a production test hook.
            val refresh = NativeDataRepository::class.java.getDeclaredMethod("refresh", SQLiteDatabase::class.java).apply { isAccessible = true }
            val reader = Thread({
                try {
                    db.beginTransactionNonExclusive()
                    try {
                        writerHeld.countDown()
                        check(refreshAllowed.await(10, TimeUnit.SECONDS)) { "QA_NATIVE_DATA_REFRESH_RELEASE_TIMEOUT" }
                        refresh.invoke(data, db)
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                    refreshed.complete(Unit)
                } catch (failure: Throwable) { refreshed.completeExceptionally(failure) }
            }, "qa-cache-refresh").apply { isDaemon = true }
            val writer = Thread({
                try { saved.complete(store.savePhoto(photo)) }
                catch (failure: Throwable) { saved.completeExceptionally(failure) }
            }, "qa-photo-save").apply { isDaemon = true }
            val closer = Thread({
                try { store.close(); closed.complete(Unit) }
                catch (failure: Throwable) { closed.completeExceptionally(failure) }
            }, "qa-photo-store-close").apply { isDaemon = true }
            reader.start()
            yes(withContext(Dispatchers.IO) { writerHeld.await(5, TimeUnit.SECONDS) }, "REFRESH_WRITER_ACQUIRED")
            writer.start()
            try {
                withTimeout(5_000) {
                    while (writer.stackTrace.none { it.className == "android.database.sqlite.SQLiteConnectionPool" && it.methodName == "waitForConnection" }) {
                        if (saved.isCompleted) saved.await()
                        delay(10)
                    }
                }
                yes(!saved.isCompleted, "PHOTO_WAITING_FOR_REFRESH_WRITER")
                closer.start()
                withTimeout(5_000) {
                    while (closer.state != Thread.State.WAITING || closer.stackTrace.none { it.className == PixelLocalStore::class.java.name && it.methodName == "close" }) {
                        if (closed.isCompleted) { closed.await(); error("QA_NATIVE_DATA_CLOSE_DID_NOT_DRAIN_PHOTO") }
                        delay(10)
                    }
                }
                yes(!closed.isCompleted, "CLOSE_DRAINS_PHOTO_WITHOUT_HELPER_MONITOR")
            } finally { refreshAllowed.countDown() }
            val id = withTimeout(10_000) { refreshed.await(); saved.await().also { closed.await() } }
            completed = true
            store = PixelLocalStore(context)
            data = store.nativeData()
            yes(store.photoAttachment(id) != null && data.detail("photos", id)?.optString("id") == id, "REFRESH_AND_PHOTO_BOTH_COMMITTED")
            yes(JSONObject(store.ownerValue("photo-ui:$request", "{}")).getString("id") == id, "CONCURRENT_PHOTO_RECEIPT_COMMITTED")
            yes(store.savePhoto(JSONObject(photo.toString())) == id, "CONCURRENT_PHOTO_REPLAY_SAME_ID")
            val nested = withContext(Dispatchers.IO) {
                val transaction = store.writableDatabase
                store.beginBusinessTransaction(transaction)
                try {
                    val result = store.savePhoto(JSONObject(photo.toString()).put("_photo_request_id", request + "_nested"))
                    transaction.setTransactionSuccessful()
                    result
                } finally { transaction.endTransaction() }
            }
            yes(nested != id && data.detail("photos", nested)?.optString("id") == nested, "PHOTO_IN_EXISTING_OWNER_TRANSACTION")
        } finally {
            refreshAllowed.countDown()
            // A timeout must report failure rather than block closing a deliberately deadlocked fixture.
            // Only this fresh, isolated child store is retained on failure; the test process owns it.
            if (completed) store.close()
        }
    }

    private suspend fun cacheInitializationCloseProof(owner: Context) {
        val context = FixtureContext(owner, File(owner.cacheDir, "cache-init-close-race"), "qa_cache_init_close_race")
        var store = PixelLocalStore(context)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = CompletableDeferred<Throwable?>()
        val queried = CompletableDeferred<Throwable?>()
        val closed = CompletableDeferred<Unit>()
        var completed = false
        try {
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            val db = store.writableDatabase
            val data = store.nativeData()
            // The real first-use path must wait for SQLite without holding its
            // Java object monitor, which close needs while holding the helper.
            val firstRead = NativeDataRepository::class.java.getDeclaredMethod("readCurrent", kotlin.jvm.functions.Function1::class.java).apply { isAccessible = true }
            val count: (SQLiteDatabase) -> Int = { current ->
                current.rawQuery("SELECT count(*) FROM native_read_rows WHERE collection='customers'", null).use { rows -> rows.moveToFirst(); rows.getInt(0) }
            }
            val writer = Thread({
                db.acquireReference()
                try {
                    db.beginTransactionNonExclusive()
                    try {
                        held.countDown()
                        check(release.await(15, TimeUnit.SECONDS)) { "QA_NATIVE_DATA_INIT_WRITER_RELEASE_TIMEOUT" }
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                    written.complete(null)
                } catch (failure: Throwable) { written.complete(failure) }
                finally { db.releaseReference() }
            }, "qa-cache-init-blocking-writer").apply { isDaemon = true }
            val reader = Thread({
                try { firstRead.invoke(data, count); queried.complete(null) }
                catch (failure: Throwable) { queried.complete(if (failure is java.lang.reflect.InvocationTargetException) failure.targetException else failure) }
            }, "qa-cache-first-read").apply { isDaemon = true }
            val closer = Thread({
                try { store.close(); closed.complete(Unit) }
                catch (failure: Throwable) { closed.completeExceptionally(failure) }
            }, "qa-cache-init-close").apply { isDaemon = true }
            writer.start()
            yes(withContext(Dispatchers.IO) { held.await(5, TimeUnit.SECONDS) }, "INIT_BLOCKING_WRITER_ACQUIRED")
            reader.start()
            withTimeout(5_000) {
                while (reader.stackTrace.none { it.className == "android.database.sqlite.SQLiteConnectionPool" && it.methodName == "waitForConnection" }) {
                    if (queried.isCompleted) throw AssertionError("QA_NATIVE_DATA_FIRST_INIT_DID_NOT_WAIT", queried.await())
                    delay(10)
                }
            }
            yes(!queried.isCompleted, "FIRST_INIT_WAITING_FOR_WRITER")
            closer.start()
            try {
                withTimeout(5_000) {
                    while (!closed.isCompleted && closer.stackTrace.none { it.className == PixelLocalStore::class.java.name && it.methodName == "close" }) delay(10)
                }
            } finally { release.countDown() }
            val outcomes = withTimeout(10_000) { listOf(written.await(), queried.await()).also { closed.await() } }
            completed = true
            yes(outcomes.all { failure ->
                failure == null || ((failure is IllegalStateException || failure is android.database.sqlite.SQLiteException) && failure.message.orEmpty().contains("closed", ignoreCase = true))
            }, "INIT_CLOSE_ONLY_ALLOWED_CLOSED_QUERY_FAILURES")
            store = PixelLocalStore(context)
            yes(store.nativeData().list("customers").total == 1, "INIT_CLOSE_REOPEN_PRESERVES_SOURCE")
        } finally {
            release.countDown()
            // A reproduced deadlock belongs only to this isolated child store.
            if (completed) store.close()
        }
    }

    private suspend fun flowAndWalProof(store: PixelLocalStore, data: NativeDataRepository, key: String, equipment: JSONObject) = kotlinx.coroutines.coroutineScope {
        val events = CopyOnWriteArrayList<NativeListPage>()
        val cold = PixelLocalStore(store.appContext())
        val watcher = launch(Dispatchers.Default) { data.watchList("equipment").collect { events.add(it) } }
        try {
            withTimeout(10_000) { while (events.isEmpty()) delay(10) }
            val writerStarted = CompletableDeferred<Unit>()
            val releaseWriter = CountDownLatch(1)
            val writer = async(Dispatchers.IO) {
                val db = store.writableDatabase
                store.beginBusinessTransaction(db)
                try {
                    db.execSQL("UPDATE owner_state SET value=? WHERE key=?", arrayOf(JSONObject(equipment.toString()).put("name", "QA committed").toString(), key))
                    writerStarted.complete(Unit)
                    check(releaseWriter.await(15, TimeUnit.SECONDS)) { "QA_NATIVE_DATA_WRITER_RELEASE_TIMEOUT" }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            try {
                withTimeout(10_000) { writerStarted.await() }
                val old = withTimeout(5_000) { data.list("equipment") }
                yes(old.rows.single().title == "QA initial", "WAL_READER_DURING_UNCOMMITTED_WRITER")
                yes(events.size == 1, "UNCOMMITTED_FLOW_NOT_PUBLISHED")
                phase = "cold_helper_wal"
                val coldRead = withTimeout(5_000) { cold.nativeData().list("equipment") }
                yes(coldRead.rows.single().title == "QA initial", "COLD_HELPER_READS_VALID_WAL_CACHE_WITHOUT_WRITER")
                val snapshot = withTimeout(5_000) { withContext(Dispatchers.IO) { cold.getState() } }
                yes(snapshot.has("master"), "FULL_SNAPSHOT_READS_DURING_UNCOMMITTED_WAL_WRITER")
                yes(cold.ownerValue(key, "").contains("QA initial"), "FULL_SNAPSHOT_PRESERVES_UNCOMMITTED_WRITER_ISOLATION")
                phase = "flow_commit"
            } finally { releaseWriter.countDown() }
            writer.await()
            withTimeout(10_000) { while (events.size < 2) delay(10) }
            yes(events.last().rows.single().title == "QA committed", "FLOW_AFTER_PHYSICAL_COMMIT")
            val rebuilds = data.diagnostics().getLong("rebuild_count")
            store.putOwnerValue("native_form_draft:qa_native_data", "{}")
            delay(100)
            yes(data.diagnostics().getLong("rebuild_count") == rebuilds, "DRAFT_DOES_NOT_REBUILD_BUSINESS_CACHE")
            val db = store.writableDatabase
            store.beginBusinessTransaction(db)
            try { db.execSQL("UPDATE owner_state SET value=? WHERE key=?", arrayOf(JSONObject(equipment.toString()).put("name", "QA rollback").toString(), key)) }
            finally { db.endTransaction() }
            // An explicit duplicate notification must still never expose rolled-back values.
            store.notifyCommittedChange()
            delay(100)
            yes(data.list("equipment").rows.single().title == "QA committed", "ROLLBACK_PRESERVES_COMMITTED_LIST")
            yes(events.size == 2 && events.none { page -> page.rows.any { it.title == "QA rollback" } }, "DRAFT_AND_ROLLBACK_NO_FALSE_FLOW")
            yes(data.diagnostics().getLong("rebuild_count") == rebuilds, "ROLLBACK_PRESERVES_CACHE_REVISION")
        } finally { watcher.cancelAndJoin(); cold.close() }
    }

    private suspend fun cacheSchemaRepairProof(owner: Context) {
        val context = FixtureContext(owner, File(owner.cacheDir, "cache-schema-repair"), "qa_cache_schema_repair")
        PixelLocalStore(context).use { store ->
            val source = SyntheticBusinessData.profile("minimal")
            store.putOwnerValue("snapshot", source.toString())
            store.nativeData().list("customers")
            val revision = store.nativeData().diagnostics().getLong("source_revision")
            val db = store.writableDatabase
            db.execSQL("CREATE TABLE qa_preserved_table(id INTEGER PRIMARY KEY,value TEXT)")
            db.execSQL("INSERT INTO qa_preserved_table VALUES(1,'QA preserved')")
            db.execSQL("DROP INDEX ix_native_read_sort")
            db.execSQL("CREATE INDEX ix_native_read_sort ON native_read_rows(collection,id)")
            db.execSQL("DROP TRIGGER native_read_revision_owner_state_UPDATE")
            db.execSQL("CREATE TRIGGER native_read_revision_owner_state_UPDATE AFTER UPDATE ON owner_state BEGIN SELECT 1; END")
            source.getJSONArray("customers").getJSONObject(0).put("name", "QA repaired cache")
            store.putOwnerValue("snapshot", source.toString())
            PixelLocalStore(context).use { cold ->
                val data = cold.nativeData()
                yes(data.list("customers").rows.single().title == "QA repaired cache", "COLD_CACHE_REPAIRS_TAMPERED_SCHEMA_AND_STALE_DATA")
                val diagnostics = data.diagnostics()
                yes(diagnostics.getLong("source_revision") > revision && diagnostics.getJSONObject("plans").getJSONArray("customer_active_sort").toString().contains("ix_native_read_sort"), "CACHE_REPAIR_REPLACES_INDEX_AND_ADVANCES_GENERATION")
                source.getJSONArray("customers").getJSONObject(0).put("name", "QA repaired trigger")
                store.putOwnerValue("snapshot", source.toString())
                yes(data.list("customers").rows.single().title == "QA repaired trigger", "CACHE_REPAIR_REPLACES_REVISION_TRIGGER")
                db.rawQuery("SELECT value FROM qa_preserved_table WHERE id=1", null).use { row -> yes(row.moveToFirst() && row.getString(0) == "QA preserved", "CACHE_REPAIR_PRESERVES_UNKNOWN_TABLE") }
            }
        }
    }

    private fun businessTransactionLifetimeProof(owner: Context) {
        val context = FixtureContext(owner, File(owner.cacheDir, "business-close-lifetime"), "qa_business_close_lifetime")
        var store = PixelLocalStore(context)
        fun guardRejects(): Boolean = try { PixelLocalStore.assertNoOpenStores(context); false }
            catch (expected: IOException) { expected.message == "RESTORE_DATABASE_STILL_OPEN" }
        try {
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            store.putOwnerValue("qa_lifetime", "before")
            for (commit in listOf(true, false)) {
                val db = store.writableDatabase
                store.beginBusinessTransaction(db)
                try {
                    store.close()
                    yes(db.isOpen && store.writableDatabase === db, "BUSINESS_TRANSACTION_PINS_AND_REUSES_DATABASE_$commit")
                    store.putOwnerValue("qa_lifetime", if (commit) "committed" else "rolled_back")
                    yes(store.getState().has("master") && store.ownerValue("qa_lifetime", "") == if (commit) "committed" else "rolled_back", "CLOSED_HELPER_CALLBACK_AFFINITY_$commit")
                    yes(guardRejects(), "RESTORE_GUARD_REJECTS_PINNED_CLOSED_HELPER_$commit")
                    if (commit) db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                yes(!db.isOpen, "BUSINESS_TRANSACTION_RELEASES_PIN_$commit")
                PixelLocalStore.assertNoOpenStores(context)
                var closed = false
                try { store.writableDatabase } catch (expected: IllegalStateException) { closed = expected.message == "LOCAL_STORE_CLOSED" }
                yes(closed, "CLOSED_HELPER_CANNOT_REOPEN_$commit")
                store = PixelLocalStore(context)
                yes(store.ownerValue("qa_lifetime", "") == "committed", "BUSINESS_NEW_HELPER_COMMIT_ROLLBACK_$commit")
            }
            val db = store.writableDatabase
            PixelLocalStore.beginJournalTransaction(db) { throw IOException("QA_SYNTHETIC_COMMIT_FAILURE") }
            store.close()
            store.putOwnerValue("qa_lifetime", "failed_commit")
            db.setTransactionSuccessful()
            var rejected = false
            try { db.endTransaction() } catch (failure: IllegalStateException) { rejected = failure.message == "LOCAL_JOURNAL_TABLE_COMMIT_FAILED" }
            yes(rejected && !db.isOpen, "FAILED_COMMIT_RELEASES_PIN")
            PixelLocalStore.assertNoOpenStores(context)
            store = PixelLocalStore(context)
            yes(store.ownerValue("qa_lifetime", "") == "committed", "FAILED_COMMIT_ROLLS_BACK_AND_REOPENS")
        } finally { store.close() }
    }

    private fun snapshotCloseLifetimeProof(owner: Context) {
        val base = FixtureContext(owner, File(owner.cacheDir, "snapshot-close-lifetime"), "qa_snapshot_close_lifetime")
        var duringSnapshot: (() -> Unit)? = null
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = base.getSharedPreferences(name, mode)
                return object : SharedPreferences by preferences {
                    override fun getString(key: String?, defValue: String?): String? {
                        if (key == "drive_snapshot") duringSnapshot?.invoke()
                        return preferences.getString(key, defValue)
                    }
                }
            }
        }
        for (fail in listOf(false, true)) {
            val store = PixelLocalStore(context)
            try {
                store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
                val database = store.writableDatabase
                duringSnapshot = {
                    duringSnapshot = null
                    yes(database.inTransaction(), "SNAPSHOT_CLOSE_DURING_TRANSACTION_$fail")
                    store.close()
                    yes(database.isOpen, "SNAPSHOT_CLOSE_PINS_CONNECTION_$fail")
                    if (fail) throw IOException("QA_SYNTHETIC_SNAPSHOT_FAILURE")
                }
                val outcome = runCatching { store.getState() }
                yes(if (fail) outcome.exceptionOrNull() is IllegalStateException else outcome.getOrThrow().has("master"), "SNAPSHOT_CLOSE_OUTCOME_$fail")
                yes(!database.isOpen, "SNAPSHOT_CLOSE_RELEASES_CONNECTION_$fail")
                PixelLocalStore.assertNoOpenStores(context)
                PixelLocalStore(context).use { reopened ->
                    reopened.putOwnerValue("qa_snapshot_reopened", "ready")
                    yes(reopened.getState().has("master") && reopened.ownerValue("qa_snapshot_reopened", "") == "ready", "SNAPSHOT_CLOSE_NO_LOCK_AFTER_REOPEN_$fail")
                }
            } finally { duringSnapshot = null; store.close() }
        }
    }

    private suspend fun chunkedUploadStorageProof(owner: Context) {
        val context = FixtureContext(owner, File(owner.cacheDir, "chunk-upload-bound"), "qa_chunk_upload_bound")
        val store = PixelLocalStore(context)
        var completed = false
        try {
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            val repository = LocalOwnerRepository(context, store)
            val access = PixelOwnerAccess(context, store)
            val bytes = ByteArray(1024 * 1024) { ((it * 17 + it / 32768) and 255).toByte() }
            val upload = access.execute("begin_file_upload", JSONObject().put("request_id", "qa_chunk_bound_begin")
                .put("byte_size", bytes.size).put("sha256", McpFileTransfer.sha(bytes)).put("mime_type", "image/jpeg")).getJSONObject("data").getString("upload_id")
            val directory = File(context.filesDir, "mcp-photo-uploads")
            val raw = File(directory, "qa-legacy-unrecognized.raw").apply { writeText("[TEST] QA preserve raw") }
            val legacy = "[TEST] QA legacy BLOB".toByteArray()
            repository.putFile("qa_legacy_blob", JSONObject().put("kind", "backup"), legacy)
            val other = "[TEST] QA other active upload".toByteArray()
            repository.putFile("qa_other_upload", JSONObject().put("kind", "photo_upload"), other)
            var replay: JSONObject? = null
            for (offset in bytes.indices step 32768) {
                val part = bytes.copyOfRange(offset, minOf(bytes.size, offset + 32768))
                val args = JSONObject().put("request_id", "qa_chunk_bound_$offset").put("upload_id", upload).put("offset", offset)
                    .put("chunk_base64", java.util.Base64.getEncoder().encodeToString(part)).put("chunk_sha256", McpFileTransfer.sha(part))
                val result = access.execute("write_file_chunk", args).getJSONObject("data")
                yes(result.getInt("next_offset") == offset + part.size && !result.optBoolean("upload_cleanup_pending"), "CHUNK_OFFSET_AND_CLEANUP_$offset")
                val content = directory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9]{64}\\.bin")) }
                yes(content.size == 2 && content.sumOf { it.length() } == (offset + part.size + other.size).toLong(), "CHUNK_LIVE_DISK_BOUND_$offset")
                if (replay == null) replay = JSONObject(args.toString())
            }
            yes(repository.fileBytes(upload).contentEquals(bytes) && repository.fileBytes("qa_legacy_blob").contentEquals(legacy) && raw.readText() == "[TEST] QA preserve raw", "CHUNK_BYTES_AND_LEGACY_PRESERVED")
            yes(access.execute("write_file_chunk", replay!!).getJSONObject("data").optBoolean("replayed") && repository.fileBytes(upload).contentEquals(bytes), "OLD_CHUNK_REPLAY_DOES_NOT_REWIND")

            val concurrentBytes = bytes.copyOfRange(0, 65536)
            val second = access.execute("begin_file_upload", JSONObject().put("request_id", "qa_chunk_capture_begin")
                .put("byte_size", concurrentBytes.size).put("sha256", McpFileTransfer.sha(concurrentBytes)).put("mime_type", "image/jpeg")).getJSONObject("data").getString("upload_id")
            fun chunkArgs(offset: Int) = JSONObject().put("request_id", "qa_chunk_capture_$offset").put("upload_id", second).put("offset", offset)
                .put("chunk_base64", java.util.Base64.getEncoder().encodeToString(concurrentBytes.copyOfRange(offset, offset + 32768)))
                .put("chunk_sha256", McpFileTransfer.sha(concurrentBytes.copyOfRange(offset, offset + 32768)))
            access.execute("write_file_chunk", chunkArgs(0))
            val previous = File(directory, repository.fileMeta(second).getString("private_upload_storage_name"))
            val capture = File(context.cacheDir, "chunk-backup")
            val pinned = CountDownLatch(1)
            val captureAllowed = CountDownLatch(1)
            val captured = CompletableDeferred<File>()
            val saved = CompletableDeferred<JSONObject>()
            Thread({
                try {
                    synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
                        BackupFilePins.pinImmutable(directory, capture, "files/mcp-photo-uploads")
                        pinned.countDown()
                        check(captureAllowed.await(15, TimeUnit.SECONDS)) { "QA_CHUNK_BACKUP_RELEASE_TIMEOUT" }
                        val image = File(capture, "database.sqlite")
                        DatabaseBackupSnapshot.capture(store.writableDatabase, image)
                        BackupFilePins.capture(directory, capture, "files/mcp-photo-uploads")
                        captured.complete(image)
                    }
                } catch (failure: Throwable) { pinned.countDown(); captured.completeExceptionally(failure) }
            }, "qa-chunk-backup").apply { isDaemon = true }.start()
            yes(withContext(Dispatchers.IO) { pinned.await(5, TimeUnit.SECONDS) }, "CHUNK_BACKUP_PINNED")
            Thread({
                try { saved.complete(access.execute("write_file_chunk", chunkArgs(32768)).getJSONObject("data")) }
                catch (failure: Throwable) { saved.completeExceptionally(failure) }
            }, "qa-chunk-owner-write").apply { isDaemon = true }.start()
            try {
                withTimeout(10_000) { while (repository.fileMeta(second).getLong("private_upload_byte_size") != concurrentBytes.size.toLong()) delay(10) }
                yes(!saved.isCompleted && previous.isFile, "CHUNK_COMMITTED_BEFORE_CLEANUP_WAITS_FOR_BACKUP")
            } finally { captureAllowed.countDown() }
            val image = withTimeout(15_000) { saved.await(); captured.await() }
            val pinnedPrevious = File(capture, "files/mcp-photo-uploads/${previous.name}")
            yes(!previous.exists() && pinnedPrevious.readBytes().contentEquals(concurrentBytes.copyOfRange(0, 32768)), "SUPERSEDED_PREFIX_REMOVED_WITH_BACKUP_PIN_INTACT")
            SQLiteDatabase.openDatabase(image.path, null, SQLiteDatabase.OPEN_READONLY).use { snapshot ->
                snapshot.rawQuery("SELECT metadata,length(data) FROM mcp_uploads WHERE id=?", arrayOf(second)).use { row ->
                    yes(row.moveToFirst() && row.getLong(1) == 0L, "CHUNK_BACKUP_METADATA_ONLY")
                    val metadata = JSONObject(row.getString(0))
                    yes(File(capture, "files/mcp-photo-uploads/${metadata.getString("private_upload_storage_name")}").readBytes().contentEquals(concurrentBytes), "CHUNK_BACKUP_COMMITTED_REFERENCE_COMPLETE")
                }
            }

            val rollback = "[TEST] QA uncommitted prefix".toByteArray()
            val rollbackFile = File(directory, McpFileTransfer.sha(rollback) + ".bin")
            val published = CountDownLatch(1)
            val rollbackAllowed = CountDownLatch(1)
            val rolledBack = CompletableDeferred<Unit>()
            val cleaned = CompletableDeferred<Unit>()
            Thread({
                val db = store.writableDatabase
                try {
                    db.beginTransactionNonExclusive()
                    try {
                        repository.putFile(second, repository.fileMeta(second), rollback)
                        repository.pruneUnreferencedPhotoUploads() // Nested cleanup must not wait on capture.
                        published.countDown()
                        check(rollbackAllowed.await(15, TimeUnit.SECONDS)) { "QA_CHUNK_ROLLBACK_RELEASE_TIMEOUT" }
                    } finally { db.endTransaction() }
                    rolledBack.complete(Unit)
                } catch (failure: Throwable) { published.countDown(); rolledBack.completeExceptionally(failure) }
            }, "qa-chunk-rollback").apply { isDaemon = true }.start()
            yes(withContext(Dispatchers.IO) { published.await(5, TimeUnit.SECONDS) }, "ROLLBACK_PREFIX_PUBLISHED")
            val cleaner = Thread({
                try { repository.pruneUnreferencedPhotoUploads(); cleaned.complete(Unit) }
                catch (failure: Throwable) { cleaned.completeExceptionally(failure) }
            }, "qa-chunk-cleaner").apply { isDaemon = true }
            cleaner.start()
            try {
                withTimeout(5_000) { while (cleaner.stackTrace.none { it.className == "android.database.sqlite.SQLiteConnectionPool" && it.methodName == "waitForConnection" }) { if (cleaned.isCompleted) error("QA_CLEANER_DID_NOT_WAIT_FOR_PUBLISHER"); delay(10) } }
                yes(rollbackFile.isFile && repository.fileBytes(second).contentEquals(concurrentBytes), "CLEANUP_CANNOT_DELETE_UNCOMMITTED_PUBLISHER_OR_OLD_REFERENCE")
            } finally { rollbackAllowed.countDown() }
            withTimeout(10_000) { rolledBack.await(); cleaned.await() }
            yes(!rollbackFile.exists() && repository.fileBytes(second).contentEquals(concurrentBytes) && repository.fileBytes("qa_other_upload").contentEquals(other), "ROLLBACK_CLEANUP_PRESERVES_ALL_COMMITTED_UPLOADS")
            completed = true
        } finally { if (completed) store.close() }
    }


    private suspend fun uploadPublicationProof(owner: Context) {
        val context = FixtureContext(owner, File(owner.cacheDir, "upload-publication-race"), "qa_upload_publication_race")
        val store = PixelLocalStore(context)
        val repository = LocalOwnerRepository(context, store)
        val old = "[TEST] QA previous immutable upload".toByteArray()
        val next = ByteArray(4 * 1024 * 1024) { ((it * 31 + 17) and 255).toByte() }
        val uploadId = "qa_upload_publication"
        val oldHash = McpFileTransfer.sha(old)
        val nextHash = McpFileTransfer.sha(next)
        val metadata = JSONObject().put("kind", "photo_upload").put("byte_size", next.size).put("sha256", nextHash)
        val directory = File(context.filesDir, "mcp-photo-uploads")
        val stage = File(context.cacheDir, "backup-image")
        val probes = File(context.cacheDir, "pin-probes")
        val pinned = CountDownLatch(1)
        val published = CountDownLatch(1)
        val commitAllowed = CountDownLatch(1)
        val written = CompletableDeferred<Unit>()
        val captured = CompletableDeferred<File>()
        var completed = false
        try {
            repository.putFile(uploadId, metadata, old)
            val original = File(directory, "$oldHash.bin")
            val inode = android.system.Os.stat(original.path).st_ino
            repository.putFile(uploadId, metadata, old)
            yes(android.system.Os.stat(original.path).st_ino == inode, "EXISTING_UPLOAD_FILE_RETAINED")
            val backup = Thread({
                try {
                    synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK) {
                        BackupFilePins.pinImmutable(directory, stage, "files/mcp-photo-uploads")
                        pinned.countDown()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                        while (published.count > 0) {
                            check(System.nanoTime() < deadline) { "QA_NATIVE_DATA_UPLOAD_PUBLICATION_TIMEOUT" }
                            BackupFilePins.pinImmutable(directory, probes, "uploads")
                            check(directory.listFiles().orEmpty().all { it.name.matches(Regex("[a-f0-9]{64}\\.bin")) }) { "QA_NATIVE_DATA_PARTIAL_UPLOAD_VISIBLE_TO_BACKUP" }
                            Thread.yield()
                        }
                        val image = File(stage, "database.sqlite")
                        DatabaseBackupSnapshot.capture(store.writableDatabase, image)
                        // Match capture's second pass: newly committed file references must be available too.
                        BackupFilePins.capture(directory, stage, "files/mcp-photo-uploads")
                        captured.complete(image)
                    }
                } catch (failure: Throwable) { captured.completeExceptionally(failure); pinned.countDown() }
            }, "qa-upload-backup").apply { isDaemon = true }
            val writer = Thread({
                val db = store.writableDatabase
                try {
                    db.beginTransactionNonExclusive()
                    try {
                        repository.putFile(uploadId, metadata, next)
                        published.countDown()
                        check(commitAllowed.await(10, TimeUnit.SECONDS)) { "QA_NATIVE_DATA_UPLOAD_COMMIT_TIMEOUT" }
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                    written.complete(Unit)
                } catch (failure: Throwable) { published.countDown(); written.completeExceptionally(failure) }
            }, "qa-upload-publish").apply { isDaemon = true }
            backup.start()
            yes(withContext(Dispatchers.IO) { pinned.await(5, TimeUnit.SECONDS) }, "UPLOAD_BACKUP_PIN_STARTED")
            writer.start()
            try {
                yes(withContext(Dispatchers.IO) { published.await(10, TimeUnit.SECONDS) }, "UPLOAD_PUBLISHED_WITH_CAPTURE_LOCK_HELD")
                yes(repository.fileBytes(uploadId).contentEquals(old), "UNCOMMITTED_UPLOAD_REFERENCE_HIDDEN")
            } finally { commitAllowed.countDown() }
            val image = withTimeout(15_000) { written.await(); captured.await() }
            completed = true
            SQLiteDatabase.openDatabase(image.path, null, SQLiteDatabase.OPEN_READONLY).use { snapshot ->
                snapshot.rawQuery("SELECT metadata,length(data) FROM mcp_uploads WHERE id=?", arrayOf(uploadId)).use { row ->
                    yes(row.moveToFirst() && row.getLong(1) == 0L, "FILE_BACKED_UPLOAD_SNAPSHOT_NO_BLOB")
                    val reference = JSONObject(row.getString(0))
                    val sha = reference.getString("private_upload_sha256")
                    val file = File(File(stage, "files/mcp-photo-uploads"), reference.getString("private_upload_storage_name"))
                    yes(sha in listOf(oldHash, nextHash) && file.isFile && McpFileTransfer.sha(file.readBytes()) == sha, "UPLOAD_SNAPSHOT_REFERENCE_COMPLETE")
                }
            }
            yes(original.readBytes().contentEquals(old) && repository.fileBytes(uploadId).contentEquals(next), "UPLOAD_OLD_AND_NEW_BYTES_PRESERVED")
            val rollback = "[TEST] QA rolled-back upload bytes".toByteArray()
            val db = store.writableDatabase
            db.beginTransactionNonExclusive()
            try { repository.putFile(uploadId, metadata, rollback) } finally { db.endTransaction() }
            yes(repository.fileBytes(uploadId).contentEquals(next) && original.readBytes().contentEquals(old), "UPLOAD_ROLLBACK_RETAINS_COMMITTED_REFERENCE")
        } finally {
            commitAllowed.countDown()
            if (completed) store.close()
        }
    }

    private fun thumbnailTileProof(context: Context, store: PixelLocalStore) {
        val photoId = "qa_thumbnail_" + UUID.randomUUID().toString().replace("-", "")
        val attachments = store.photoAttachmentStore()
        val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = try {
            bitmap.eraseColor(0xff000000.toInt() or (photoId.hashCode() and 0x00ffffff))
            java.io.ByteArrayOutputStream().use { output ->
                yes(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output), "THUMB_JPEG_ENCODE")
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        yes(bytes.size in 1..65536, "THUMB_SMALL_BYTES")
        val hash = PhotoAttachmentStore.sha256(bytes)
        val thumbnailName = "$hash.jpg"
        val preview = attachments.thumbnailFile(thumbnailName)
        val invalidHash = PhotoAttachmentStore.sha256((photoId + "deliberately_invalid_original").toByteArray())
        val invalid = File(File(context.filesDir, "customer-photo-originals"), "$invalidHash.jpg")
        val original = File(File(context.filesDir, "customer-photo-originals"), "$hash.jpg")
        yes(!preview.exists() && !invalid.exists() && !original.exists(), "THUMB_OWN_NEW_FILES")
        var metadataReads = 0
        fun loadMetadata(): JSONObject? { metadataReads++; return store.photoAttachment(photoId) }
        try {
            attachments.storeThumbnail(bytes, thumbnailName)
            invalid.parentFile!!.mkdirs()
            invalid.writeBytes(byteArrayOf(1, 2, 3))
            store.restoreAttachmentMetadata(photoId, JSONObject().put("content_sha256", invalidHash)
                .put("mime_type", "image/jpeg").put("byte_size", 3).put("storage_name", invalid.name).put("role", "documentation"))
            repeat(40) {
                yes(resolveNativePhotoSource(attachments, thumbnailName, null, false, ::loadMetadata) == preview, "PROJECTED_THUMB_TILE")
            }
            yes(metadataReads == 0, "THUMB_ZERO_SQL_POINT_READS")
            // If any tile had verified this deliberately wrong original, the call above would have thrown.
            var originalRejected = false
            try { resolveNativePhotoSource(attachments, thumbnailName, null, true, ::loadMetadata) }
            catch (_: IOException) { originalRejected = true }
            yes(originalRejected && metadataReads == 1, "DETAIL_EXPLICIT_INVALID_ORIGINAL_CHECK")
            val metadata = JSONObject().put("content_sha256", hash).put("mime_type", "image/jpeg")
                .put("byte_size", bytes.size).put("storage_name", original.name).put("role", "documentation")
            attachments.store(bytes, metadata)
            store.restoreAttachmentMetadata(photoId, metadata)
            metadataReads = 0
            val missing = PhotoAttachmentStore.sha256((photoId + "missing_preview").toByteArray()) + ".jpg"
            yes(resolveNativePhotoSource(attachments, missing, null, false, ::loadMetadata) == original && metadataReads == 1, "MISSING_THUMB_ONE_SQL_ORIGINAL_FALLBACK")
            preview.writeBytes(byteArrayOf(4, 5, 6))
            metadataReads = 0
            yes(resolveNativePhotoSource(attachments, thumbnailName, null, false, ::loadMetadata) == original && metadataReads == 1, "TAMPERED_THUMB_ONE_SQL_ORIGINAL_FALLBACK")
            attachments.verifiedFile(metadata)
            yes(original.readBytes().contentEquals(bytes), "THUMB_FALLBACK_PRESERVES_ORIGINAL")
        } finally {
            store.writableDatabase.delete("photo_attachments", "photo_id=?", arrayOf(photoId))
            // All three paths were proven absent before this test and are owned by this invocation.
            preview.delete(); invalid.delete(); original.delete()
        }
    }


    private fun immutablePublishedFileProof(context: Context, store: PixelLocalStore) {
        val token = UUID.randomUUID().toString().replace("-", "")
        val originalRoot = File(context.filesDir, "customer-photo-originals").apply { mkdirs() }
        val thumbnailRoot = File(originalRoot, "thumbnails").apply { mkdirs() }
        val documentRoot = File(context.filesDir, "business-documents").apply { mkdirs() }
        val capture = File(context.cacheDir, "qa-published-pin-$token")
        val attachments = store.photoAttachmentStore()
        val documents = BusinessDocumentStore(context)
        val bitmap = android.graphics.Bitmap.createBitmap(3, 3, android.graphics.Bitmap.Config.ARGB_8888)
        val image = try {
            bitmap.eraseColor(0xff000000.toInt() or (token.hashCode() and 0x00ffffff))
            java.io.ByteArrayOutputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 83, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        val photoMeta = attachments.inspect(image, "image/jpeg").put("role", "documentation")
        val photo = attachments.file(photoMeta)
        val thumbnail = attachments.thumbnailFile(PhotoAttachmentStore.sha256(image) + ".jpg")
        val pdf = android.graphics.pdf.PdfDocument()
        val document = try {
            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(100, 100, 1).create())
            page.canvas.drawText("QA $token", 5f, 20f, android.graphics.Paint())
            pdf.finishPage(page)
            java.io.ByteArrayOutputStream().use { output -> pdf.writeTo(output); output.toByteArray() }
        } finally { pdf.close() }
        val documentHash = PhotoAttachmentStore.sha256(document)
        val documentMeta = JSONObject().put("pdf_storage_name", "$documentHash.pdf").put("pdf_sha256", documentHash).put("pdf_byte_size", document.size)
        val documentFile = File(documentRoot, "$documentHash.pdf")
        val legacy = File(originalRoot, "qa-existing-$token.new")
        yes(!photo.exists() && !thumbnail.exists() && !documentFile.exists() && !legacy.exists() && !capture.exists(), "PUBLISH_NEW_OWNED_FILES")
        legacy.writeBytes(byteArrayOf(81, 65))
        val events = CopyOnWriteArrayList<String>()
        val published = CountDownLatch(3)
        val observers = listOf(originalRoot to photo.name, thumbnailRoot to thumbnail.name, documentRoot to documentFile.name).map { (directory, expected) ->
            object : android.os.FileObserver(directory.path, android.os.FileObserver.CREATE or android.os.FileObserver.MODIFY or android.os.FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) {
                        events.add(path)
                        if (event and android.os.FileObserver.MOVED_TO != 0 && path == expected) published.countDown()
                    }
                }
            }.also { it.startWatching() }
        }
        try {
            attachments.store(image, photoMeta)
            attachments.storeThumbnail(image, thumbnail.name)
            documents.store(document, documentMeta)
            yes(published.await(10, TimeUnit.SECONDS), "PUBLISH_ATOMIC_FINAL_EVENTS")
            yes(events.all { it.matches(Regex("[0-9a-f]{64}\\.(jpg|pdf)")) }, "NO_PARTIAL_FILES_IN_PINNED_DIRECTORIES")
            BackupFilePins.pinImmutable(originalRoot, capture, "files/customer-photo-originals")
            BackupFilePins.pinImmutable(documentRoot, capture, "files/business-documents")
            val pinnedPhoto = File(capture, "files/customer-photo-originals/${photo.name}")
            val pinnedThumb = File(capture, "files/customer-photo-originals/thumbnails/${thumbnail.name}")
            val pinnedDocument = File(capture, "files/business-documents/${documentFile.name}")
            val before = listOf(pinnedPhoto, pinnedThumb, pinnedDocument).map { BackupArchive.sha256(it) }
            val count = events.size
            yes(!attachments.store(image, photoMeta), "PUBLISH_PHOTO_RETRY_REUSES_FINAL")
            attachments.storeThumbnail(image, thumbnail.name)
            documents.store(document, documentMeta)
            yes(before == listOf(photo, thumbnail, documentFile).map { BackupArchive.sha256(it) }, "PUBLISH_RETRIES_PRESERVE_FINAL_BYTES")
            yes(before == listOf(pinnedPhoto, pinnedThumb, pinnedDocument).map { BackupArchive.sha256(it) }, "PUBLISH_RETRIES_PRESERVE_PINNED_BYTES")
            yes(events.drop(count).none { it == photo.name || it == thumbnail.name || it == documentFile.name }, "PUBLISH_NO_FINAL_WRITE_ON_RETRY")
            yes(File(capture, "files/customer-photo-originals/${legacy.name}").readBytes().contentEquals(byteArrayOf(81, 65)) && legacy.readBytes().contentEquals(byteArrayOf(81, 65)), "LEGACY_NEW_FILE_PRESERVED_RAW")
        } finally {
            observers.forEach { it.stopWatching() }
            photo.delete(); thumbnail.delete(); documentFile.delete(); legacy.delete(); capture.deleteRecursively()
        }
        val offer = JSONObject().put("id", "qa_offer_pdf_$token").put("offer_number", "QA-ANG-1")
            .put("document_type", "fixed_price").put("issue_date", "2026-11-02").put("valid_until", "2026-11-30")
            .put("title", "QA Angebots-PDF").put("execution_window", "QA nach Vereinbarung").put("tax_mode", "standard").put("test_record", true)
            .put("issuer", JSONObject().put("business_name", "QA Musterbetrieb").put("business_vat_id", "ATU12345678"))
            .put("recipient", JSONObject().put("name", "QA Musterkunde").put("street", "QA Testweg 1"))
            .put("positions", JSONArray().put(JSONObject().put("title", "QA Gartenarbeit").put("quantity", 2).put("unit", "h").put("price", 10).put("net", 20).put("tax_rate", 20)))
            .put("net_total", 20).put("tax_total", 4).put("gross_total", 24)
        val offered = documents.create(offer)
        try {
            val raw = documents.read(offered)
            yes(offered.getString("document_id") == "offer:" + offer.getString("id"), "OFFER_PDF_HAS_OFFER_IDENTITY")
            yes(String(raw, 0, 5, Charsets.US_ASCII) == "%PDF-" && PhotoAttachmentStore.sha256(raw) == offered.getString("pdf_sha256"), "OFFER_PDF_ORIGINAL_HASH_READBACK")
            android.os.ParcelFileDescriptor.open(File(documentRoot, offered.getString("pdf_storage_name")), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                android.graphics.pdf.PdfRenderer(descriptor).use { renderer -> yes(renderer.pageCount > 0, "OFFER_PDF_RENDERS_ON_ANDROID") }
            }
        } finally { documents.deleteVerified(offered) }

    }

    private class FixtureContext(private val owner: Context, private val root: File, private val prefix: String) : ContextWrapper(owner) {
        override fun getApplicationContext(): Context = this
        override fun getDataDir(): File = directory("")
        override fun getFilesDir(): File = directory("files")
        override fun getNoBackupFilesDir(): File = directory("no_backup")
        override fun getCacheDir(): File = directory("cache")
        override fun getDatabasePath(name: String): File {
            if (name != "haus-garten-mobile.db") throw IOException("QA_SYNTHETIC_DATABASE_NAME_REQUIRED")
            return File(directory("databases"), name)
        }
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = openOrCreateDatabase(name, mode, factory, null)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (!name.matches(Regex("[A-Za-z0-9_]+"))) throw IOException("QA_SYNTHETIC_PREFERENCE_NAME_REQUIRED")
            return owner.getSharedPreferences("${prefix}_$name", mode)
        }
        private fun directory(name: String): File {
            val value = if (name.isEmpty()) root else File(root, name)
            if (!value.isDirectory && !value.mkdirs()) throw IOException("QA_SYNTHETIC_STORAGE_REQUIRED")
            return value
        }
    }
}
