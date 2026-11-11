package at.hausgarten.service

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.os.StrictMode
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** Production repository/SQLite/coroutines, with a new synthetic target-UID root per invocation. */
class NativeDataAccessNativeTests private constructor(private val test: Instrumentation) {
    companion object {
        @JvmStatic
        fun runUpgrade(
            test: Instrumentation,
            verify: Boolean,
            expectedSource: String,
            expectedGit: String,
        ): JSONObject =
            NativeDataAccessNativeTests(test).upgrade158(verify, expectedSource, expectedGit)

        @JvmStatic
        fun run(test: Instrumentation): JSONObject {
            val fixture = NativeDataAccessNativeTests(test)
            try {
                return fixture.execute()
            } catch (failure: Throwable) {
                throw IllegalStateException("QA_NATIVE_DATA_PHASE_${fixture.phase}", failure)
            }
        }
    }

    private var phase = "guards"
    private var checks = 0

    private fun yes(condition: Boolean, code: String) {
        if (!condition) throw AssertionError("QA_NATIVE_DATA_$code")
        checks++
    }

    /** Current test APK runs both sides, using only production APIs already present in 158. */
    private fun upgrade158(
        verify: Boolean,
        expectedSource: String,
        expectedGit: String,
    ): JSONObject {
        yes(test.context.packageName == "at.hausgarten.service.test", "UPGRADE_TEST_PACKAGE")
        yes(test.targetContext.packageName == "at.hausgarten.service", "UPGRADE_TARGET_PACKAGE")
        val emulator =
            test.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input ->
                    input.readBytes().toString(Charsets.UTF_8).trim()
                }
            }
        yes(
            emulator == "1" && android.os.Build.VERSION.SDK_INT == 35,
            "UPGRADE_DISPOSABLE_EMULATOR_ONLY",
        )
        yes(
            expectedSource.matches(Regex("[a-f0-9]{64}")) &&
                expectedGit.matches(Regex("[a-f0-9]{40}")),
            "UPGRADE_EXPECTED_IDENTITY_REQUIRED",
        )
        val context = test.targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        yes(info.longVersionCode == if (verify) 159L else 158L, "UPGRADE_INSTALLED_VERSION")
        val marker = File(context.noBackupFilesDir, "qa-inplace-upgrade-158.json")
        if (!verify) {
            yes(!marker.exists(), "UPGRADE_NEW_TEST_MARKER")
            yes(
                !context.getDatabasePath("haus-garten-mobile.db").exists(),
                "UPGRADE_FRESH_BASELINE_DATABASE",
            )
        } else yes(marker.isFile, "UPGRADE_BASELINE_MARKER_REQUIRED")
        return PixelLocalStore(context).use { store ->
            val tools =
                PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
            fun call(name: String, args: JSONObject = JSONObject()): JSONObject {
                val result = tools.call(name, args)
                yes(result.optBoolean("ok"), "UPGRADE_PUBLIC_MCP_$name")
                return result.getJSONObject("data")
            }
            fun request(id: String) = JSONObject().put("request_id", "qa_inplace_158_$id")
            fun sourceHash(): String {
                val current = PixelMcpReadTools.effective(store.getState())
                val originals = JSONObject()
                for (collection in
                    listOf(
                        "customers",
                        "orders",
                        "worklogs",
                        "photos",
                        "offers",
                        "invoices",
                    )) originals.put(collection, current.optJSONArray(collection) ?: JSONArray())
                return PixelMcpWriteTools.hash(originals)
            }
            fun sessionRows(): String {
                val tables = JSONObject()
                for (table in listOf("work_sessions", "work_session_events")) {
                    val rows = JSONArray()
                    store.readableDatabase.rawQuery("SELECT * FROM $table ORDER BY id", null).use {
                        cursor ->
                        while (cursor.moveToNext()) {
                            val row = JSONObject()
                            for (column in 0 until cursor.columnCount) row.put(
                                cursor.getColumnName(column),
                                when (cursor.getType(column)) {
                                    android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                    android.database.Cursor.FIELD_TYPE_INTEGER ->
                                        cursor.getLong(column)
                                    android.database.Cursor.FIELD_TYPE_FLOAT ->
                                        cursor.getDouble(column)
                                    android.database.Cursor.FIELD_TYPE_BLOB ->
                                        java.util.Base64.getEncoder()
                                            .encodeToString(cursor.getBlob(column))
                                    else -> cursor.getString(column)
                                },
                            )
                            rows.put(row)
                        }
                    }
                    tables.put(table, rows)
                }
                return PixelMcpWriteTools.hash(tables)
            }
            fun photoHash(id: String): String {
                val metadata =
                    call(
                        "get_file_metadata",
                        JSONObject().put("resource_type", "photo").put("resource_id", id),
                    )
                val bytes =
                    call(
                        "read_file_chunk",
                        JSONObject()
                            .put("resource_type", "photo")
                            .put("resource_id", id)
                            .put("offset", 0)
                            .put("expected_sha256", metadata.getString("sha256")),
                    )
                val original = java.util.Base64.getDecoder().decode(bytes.getString("chunk_base64"))
                yes(
                    bytes.getBoolean("eof") &&
                        original.size.toLong() == metadata.getLong("byte_size"),
                    "UPGRADE_COMPLETE_ORIGINAL_BYTES",
                )
                val hash = PhotoAttachmentStore.sha256(original)
                yes(hash == metadata.getString("sha256"), "UPGRADE_PUBLIC_ORIGINAL_SHA256")
                return hash
            }
            val fingerprint =
                call("get_capabilities").getJSONObject("runtime").getJSONObject("build_fingerprint")
            yes(
                fingerprint.getString("source_sha256") == expectedSource &&
                    fingerprint.getString("git_sha") == expectedGit &&
                    !fingerprint.getBoolean("source_dirty"),
                "UPGRADE_RUNTIME_SOURCE_BINDING",
            )
            yes(
                fingerprint.getInt("version_code") == info.longVersionCode.toInt(),
                "UPGRADE_RUNTIME_PACKAGE_MATCH",
            )
            val draftKey = "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new")
            if (!verify) {
                val initial = PixelMcpReadTools.effective(store.getState())
                for (collection in
                    listOf("customers", "orders", "worklogs", "photos", "offers", "invoices")) yes(
                    (initial.optJSONArray(collection)?.length() ?: 0) == 0,
                    "UPGRADE_EMPTY_$collection",
                )
                val customer =
                    call(
                            "create_customer",
                            request("customer")
                                .put("name", "[TEST] QA In-place 158 Kunde")
                                .put("test_record", true),
                        )
                        .getString("entity_id")
                val workArgs =
                    request("work")
                        .put("customer_id", customer)
                        .put("work_date", "2026-11-01")
                        .put("activity", "[TEST] QA erhaltene Arbeit")
                        .put("hours", 1.25)
                val work = call("create_customer_work_record", workArgs).getString("work_record_id")
                val bitmap =
                    android.graphics.Bitmap.createBitmap(
                        3,
                        2,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                val image =
                    java.io.ByteArrayOutputStream().use { output ->
                        yes(
                            bitmap.compress(
                                android.graphics.Bitmap.CompressFormat.PNG,
                                100,
                                output,
                            ),
                            "UPGRADE_SYNTHETIC_IMAGE",
                        )
                        bitmap.recycle()
                        output.toByteArray()
                    }
                val upload =
                    call(
                            "begin_file_upload",
                            request("photo_begin")
                                .put("byte_size", image.size)
                                .put("sha256", McpFileTransfer.sha(image))
                                .put("mime_type", "image/png"),
                        )
                        .getString("upload_id")
                call(
                    "write_file_chunk",
                    request("photo_chunk")
                        .put("upload_id", upload)
                        .put("offset", 0)
                        .put("chunk_base64", java.util.Base64.getEncoder().encodeToString(image))
                        .put("chunk_sha256", McpFileTransfer.sha(image)),
                )
                val photo =
                    call(
                            "create_photo",
                            request("photo_attach")
                                .put("upload_id", upload)
                                .put("customer_id", customer)
                                .put("worklog_id", work)
                                .put("role", "documentation"),
                        )
                        .getString("photo_id")
                val draft =
                    call(
                            "save_settlement_draft",
                            request("draft")
                                .put(
                                    "draft",
                                    JSONObject()
                                        .put("route", "billed")
                                        .put("title", "[TEST] QA offener Rechnungsentwurf")
                                        .put("customer_id", customer)
                                        .put("billing_customer_id", customer)
                                        .put("service_from", "2026-11-01")
                                        .put("service_to", "2026-11-01")
                                        .put(
                                            "positions",
                                            JSONArray()
                                                .put(
                                                    JSONObject()
                                                        .put("title", "[TEST] QA Arbeit")
                                                        .put("unit", "Stunden")
                                                        .put("quantity", "1.25")
                                                        .put("price", "")
                                                        .put("source_collection", "worklogs")
                                                        .put("source_id", work)
                                                ),
                                        ),
                                ),
                        )
                        .getString("settlement_id")
                val form =
                    JSONObject()
                        .put(
                            "values",
                            JSONObject()
                                .put("customer_id", customer)
                                .put("activity", "[TEST] QA ungespeicherter Nachtrag")
                                .put("hours", "0.75"),
                        )
                store.putOwnerValue(draftKey, form.toString())
                val startArgs =
                    request("timer")
                        .put("customer_id", customer)
                        .put("activity", "[TEST] QA laufende Arbeit")
                val session = call("start_work", startArgs).getJSONObject("session")
                yes(session.getString("state") == "RUNNING", "UPGRADE_BASELINE_SESSION_RUNNING")
                val draftRecord =
                    call("get_settlement", JSONObject().put("settlement_id", draft))
                        .getJSONObject("record")
                yes(
                    draftRecord.getString("status") == "draft" &&
                        draftRecord
                            .getJSONArray("positions")
                            .getJSONObject(0)
                            .getString("price")
                            .isEmpty(),
                    "UPGRADE_OPEN_INCOMPLETE_DRAFT",
                )
                marker.writeText(
                    JSONObject()
                        .put("fixture", "synthetic-real-apk-upgrade-158-v1")
                        .put("baseline_source_sha256", expectedSource)
                        .put("baseline_git_sha", expectedGit)
                        .put("first_install_time", info.firstInstallTime)
                        .put("uid", android.os.Process.myUid())
                        .put("baseline_pid", android.os.Process.myPid())
                        .put("customer_id", customer)
                        .put("work_id", work)
                        .put("photo_id", photo)
                        .put("draft_id", draft)
                        .put("session_id", session.getString("id"))
                        .put("source_hash", sourceHash())
                        .put("session_rows_hash", sessionRows())
                        .put("form", form)
                        .put(
                            "ledger_hash",
                            PixelMcpWriteTools.hash(
                                JSONObject(store.ownerValue("settlement:v1", "{}"))
                            ),
                        )
                        .put("photo_sha256", photoHash(photo))
                        .put("work_args", workArgs)
                        .put("start_args", startArgs)
                        .toString()
                )
            } else {
                val saved = JSONObject(marker.readText())
                yes(
                    saved.getString("fixture") == "synthetic-real-apk-upgrade-158-v1" &&
                        saved.getString("baseline_source_sha256") ==
                            "7442733c07dee2e28fe62ad8b50b34819b90f22e68cedeab59ae1dd4eb9fd640" &&
                        saved.getString("baseline_git_sha") ==
                            "8101f3c395c9770005727bc37fc2f2057fca9130",
                    "UPGRADE_PINNED_BASELINE_PROOF",
                )
                yes(
                    info.firstInstallTime == saved.getLong("first_install_time") &&
                        android.os.Process.myUid() == saved.getInt("uid"),
                    "UPGRADE_SAME_INSTALLATION_NO_UNINSTALL",
                )
                yes(
                    android.os.Process.myPid() != saved.getInt("baseline_pid"),
                    "UPGRADE_NEW_TARGET_PROCESS",
                )
                yes(
                    sourceHash() == saved.getString("source_hash"),
                    "UPGRADE_ALL_ORIGINAL_COLLECTIONS_IDENTICAL",
                )
                yes(store.readableDatabase.version == 7, "UPGRADE_SQLITE_SCHEMA_7_PRESERVED")
                yes(
                    call(
                            "get_customer",
                            JSONObject().put("customer_id", saved.getString("customer_id")),
                        )
                        .getString("id") == saved.getString("customer_id"),
                    "UPGRADE_PUBLIC_CUSTOMER_ID",
                )
                val work =
                    call(
                        "get_work_record",
                        JSONObject().put("work_record_id", saved.getString("work_id")),
                    )
                yes(
                    work.getString("id") == saved.getString("work_id") &&
                        work.getString("customer_id") == saved.getString("customer_id"),
                    "UPGRADE_PUBLIC_WORK_RELATION",
                )
                val photo =
                    call("get_photo", JSONObject().put("photo_id", saved.getString("photo_id")))
                        .getJSONObject("photo")
                yes(
                    photo.getString("worklog_id") == saved.getString("work_id") &&
                        photoHash(saved.getString("photo_id")) == saved.getString("photo_sha256"),
                    "UPGRADE_PHOTO_ORIGINAL_AND_LINK",
                )
                val draft =
                    call(
                            "get_settlement",
                            JSONObject().put("settlement_id", saved.getString("draft_id")),
                        )
                        .getJSONObject("record")
                yes(
                    draft.getString("status") == "draft" &&
                        draft.getJSONArray("positions").getJSONObject(0).getString("source_id") ==
                            saved.getString("work_id"),
                    "UPGRADE_PUBLIC_DRAFT_AND_SOURCE",
                )
                yes(
                    PixelMcpWriteTools.hash(JSONObject(store.ownerValue("settlement:v1", "{}"))) ==
                        saved.getString("ledger_hash"),
                    "UPGRADE_LEDGER_UNCHANGED_NO_ISSUANCE",
                )
                yes(
                    store.ownerValue(draftKey, "") == saved.getJSONObject("form").toString(),
                    "UPGRADE_NATIVE_FORM_DRAFT_PRESERVED",
                )
                val session = call("get_active_work_session")
                yes(
                    session.getString("id") == saved.getString("session_id") &&
                        session.getString("state") == "RUNNING",
                    "UPGRADE_RUNNING_SESSION_PRESERVED",
                )
                yes(
                    sessionRows() == saved.getString("session_rows_hash"),
                    "UPGRADE_SESSION_AND_EVENT_ROWS_IDENTICAL",
                )
                yes(
                    call("create_customer_work_record", saved.getJSONObject("work_args"))
                        .getBoolean("replayed") &&
                        call("start_work", saved.getJSONObject("start_args"))
                            .getBoolean("replayed"),
                    "UPGRADE_ORIGINAL_REQUEST_RECEIPTS_REPLAY",
                )
                yes(
                    sourceHash() == saved.getString("source_hash") &&
                        sessionRows() == saved.getString("session_rows_hash"),
                    "UPGRADE_REPLAY_HAS_NO_DUPLICATE_OR_FINALIZATION",
                )
                yes(marker.delete(), "UPGRADE_PRIVATE_TEST_MARKER_REMOVED")
            }
            JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("baseline_version_code", 158)
                .put("installed_version_code", info.longVersionCode)
                .put("git_sha", fingerprint.getString("git_sha"))
                .put("source_sha256", fingerprint.getString("source_sha256"))
                .put("in_place_update", verify)
                .put("data_preserved", verify)
                .put("running_session_preserved", verify)
                .put("drafts_preserved", verify)
                .put("original_photo_bytes_preserved", verify)
        }
    }

    private fun execute(): JSONObject {
        // All guards precede any target storage lookup, preference access or database open.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_APK_REQUIRED")
        yes(test.targetContext.packageName == "at.hausgarten.service", "TARGET_PACKAGE_REQUIRED")
        yes(Looper.myLooper() != Looper.getMainLooper(), "INSTRUMENTATION_WORKER_REQUIRED")
        val owner = test.targetContext.createDeviceProtectedStorageContext()
        yes(owner.isDeviceProtectedStorage, "DEVICE_PROTECTED_OWNER")
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-native-data-$token")
        yes(!root.exists() && root.mkdir(), "NEW_SYNTHETIC_ROOT")
        val context = FixtureContext(owner, root, "qa_native_data_$token")
        for (path in
            listOf(
                context.dataDir,
                context.filesDir,
                context.noBackupFilesDir,
                context.cacheDir,
                context.getDatabasePath("haus-garten-mobile.db"),
            )) {
            yes(
                path.canonicalPath == root.canonicalPath ||
                    path.canonicalPath.startsWith(root.canonicalPath + File.separator),
                "ISOLATED_PATH",
            )
        }
        return PixelLocalStore(context).use { store ->
            runBlocking {
                val data = store.nativeData()
                phase = "scale"
                val fixture = scaleFixture()
                store.putOwnerValue("snapshot", fixture.toString())
                val count = data.list("worklogs", offset = 115, limit = 50)
                yes(
                    count.rows.size == 50 && count.total == 38_800 && count.hasMore,
                    "BOUNDED_SCALE_PAGE",
                )
                yes(
                    count.rows.zipWithNext().all { (a, b) ->
                        a.workDate > b.workDate || a.workDate == b.workDate && a.id < b.id
                    },
                    "HISTORY_SORT",
                )
                val projected =
                    data.list(
                        "worklogs",
                        propertyId = "synthetic_property",
                        dateFrom = "2026-10-01",
                        dateTo = "2026-10-28",
                        limit = 13,
                    )
                yes(projected.rows.size == 13 && projected.total > 1000, "OBJECT_DATE_FILTER")
                val firstDiagnostics = data.diagnostics()
                yes(
                    firstDiagnostics.getString("journal_mode") == "wal" &&
                        store.writableDatabase.isWriteAheadLoggingEnabled,
                    "RUNTIME_WAL",
                )
                val plans = firstDiagnostics.getJSONObject("plans")
                for (name in plans.keys()) {
                    val plan = plans.getJSONArray(name).toString()
                    yes(
                        plan.contains("USING INDEX ix_native_read_") &&
                            !plan.contains("TEMP B-TREE"),
                        "INDEX_$name",
                    )
                }
                store.readableDatabase
                    .rawQuery(
                        "SELECT count(*) FROM native_read_rows WHERE collection IN ('customers','orders','worklogs')",
                        null,
                    )
                    .use { rows ->
                        yes(
                            rows.moveToFirst() && rows.getInt(0) == 40_000,
                            "ACTUAL_40000_PROJECTION_ROWS",
                        )
                    }
                store.readableDatabase.rawQuery("PRAGMA table_info(native_read_rows)", null).use {
                    rows ->
                    val names = ArrayList<String>()
                    while (rows.moveToNext()) names.add(rows.getString(1))
                    yes(
                        "record_json" !in names && "selection_json" !in names && "notes" !in names,
                        "DISPLAY_COLUMNS_ONLY",
                    )
                }
                val detail = data.detail("worklogs", "qa_work_00000")!!
                yes(detail.getString("notes") == longNote(), "CHUNKED_UNICODE_DETAIL")
                for (i in 0 until 5) {
                    data.list("customers", search = "Muster", limit = 7, offset = i)
                    data.list("worklogs", propertyId = "synthetic_property", limit = 20, offset = i)
                    data.detail("worklogs", "qa_work_00000")
                }
                yes(
                    data.diagnostics().getLong("rebuild_count") ==
                        firstDiagnostics.getLong("rebuild_count"),
                    "REPEATED_READS_REUSE_CACHE",
                )
                phase = "mcp_parity"
                mcpParity(store, fixture)

                // A small second source keeps mutation/Flow checks independent of scale timings.
                val smallSource =
                    SyntheticBusinessData.profile("minimal")
                        .put(
                            "offers",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("id", "qa_offer_sent")
                                        .put("title", "QA offer")
                                        .put("status", "draft")
                                        .put("delivery_status", "sent")
                                        .put("gross_total", "123.45")
                                        .put("assumptions", "QA Suchannahme")
                                ),
                        )
                        .put(
                            "service_catalog",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("id", "svc_qa_projection")
                                        .put("title", "QA service")
                                        .put("category", "QA category")
                                        .put("default_price", "7.50")
                                        .put("unit", "h")
                                        .put("active", true)
                                ),
                        )
                        .put(
                            "appointments",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("id", "qa_appointment")
                                        .put("title", "QA appointment")
                                        .put("starts_at", "2026-10-28T23:30:00Z")
                                ),
                        )
                store.putOwnerValue("snapshot", smallSource.toString())
                yes(
                    data.list("offers", status = "sent").rows.single().id == "qa_offer_sent",
                    "OFFER_DELIVERY_STATUS_FILTER",
                )
                yes(
                    data.list("offers", status = "sent").rows.single().subtitle.contains("123,45"),
                    "PROJECTED_OFFER_AMOUNT",
                )
                val service = data.list("service_catalog", search = "QA service").rows.single()
                yes(
                    service.subtitle.contains("QA category") &&
                        service.subtitle.contains("7,50") &&
                        service.subtitle.contains(" / h"),
                    "PROJECTED_CATALOG_PRICE_CATEGORY_UNIT",
                )
                yes(
                    data.list("offers", status = "draft").rows.single().id == "qa_offer_sent",
                    "OFFER_DOMAIN_STATUS_FILTER",
                )
                yes(data.list("offers", search = "Suchannahme").total == 1, "OFFER_SEARCH_FIELDS")
                yes(
                    data
                        .list("appointments", dateFrom = "2026-10-29", dateTo = "2026-10-29")
                        .total == 1,
                    "VIENNA_DAY_BOUNDARY",
                )
                smallSource
                    .getJSONArray("appointments")
                    .put(
                        JSONObject()
                            .put("id", "qa_later_appointment")
                            .put("title", "QA later")
                            .put("starts_at", "2027-01-01T09:00:00Z")
                    )
                store.putOwnerValue("snapshot", smallSource.toString())
                yes(
                    data.list("appointments", limit = 1, chronological = true).rows.single().id ==
                        "qa_appointment",
                    "NEXT_APPOINTMENT_BEFORE_PAGE_LIMIT",
                )
                yes(
                    data.list("appointments", limit = 1).rows.single().id == "qa_later_appointment",
                    "DEFAULT_HISTORY_ORDER_UNCHANGED",
                )
                val timer = store.readSource().stateFor("get_active_work_session", JSONObject())
                yes(
                    timer.getJSONObject("master").length() == 0 &&
                        timer.getJSONArray("pending").length() == 0 &&
                        timer.optBoolean("storage_point_read") &&
                        timer.getJSONObject("work_session").has("state"),
                    "MCP_TIMER_MINIMAL_STATE",
                )
                phase = "transfer_cache"
                transferCacheProof(store, data)
                val equipment =
                    JSONObject()
                        .put("id", "qa_equipment")
                        .put("kind", "equipment")
                        .put("format_version", 1)
                        .put("name", "QA initial")
                        .put("condition", "ready")
                        .put("test_record", true)
                val key = "revier:v1:entity:equipment:qa_equipment"
                store.putOwnerValue(key, equipment.toString())
                store.putOwnerValue(
                    "revier:v1:entity:property:qa_property",
                    JSONObject()
                        .put("id", "qa_property")
                        .put("kind", "property")
                        .put("format_version", 1)
                        .put("name", "QA property")
                        .put("address", "QA Synthetic Street 1")
                        .toString(),
                )
                yes(data.list("equipment", status = "ready").total == 1, "EQUIPMENT_CONDITION")
                yes(
                    data.list("properties").rows.single().address == "QA Synthetic Street 1",
                    "PROPERTY_ADDRESS",
                )
                store.putOwnerValue(
                    "revier:v1:entity:equipment_usage:qa_usage",
                    JSONObject()
                        .put("id", "qa_usage")
                        .put("kind", "equipment_usage")
                        .put("format_version", 1)
                        .put("equipment_id", "qa_equipment")
                        .put("created_at", "2026-10-29T09:00:00Z")
                        .toString(),
                )
                yes(
                    data
                        .history("equipment_usage", "equipment_id", "qa_equipment")
                        .single()
                        .getString("id") == "qa_usage",
                    "BOUNDED_RELATION_HISTORY",
                )
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
                phase = "closed_commit_observer"
                closedCommitObserverProof(context)
                phase = "contended_commit_observer"
                contendedCommitObserverProof(context)
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
                val proofFailures = JSONArray()
                val completedProofs = JSONObject()
                fun proof(name: String, execute: () -> JSONObject): JSONObject {
                    phase = name
                    return try {
                        execute().also { completedProofs.put(name, it) }
                    } catch (failure: Throwable) {
                        if (
                            failure is java.util.concurrent.CancellationException ||
                                failure !is Exception && failure !is AssertionError
                        )
                            throw failure
                        proofFailures.put(
                            JSONObject()
                                .put("proof", name)
                                .put("phase", phase)
                                .put("trace", failure.stackTraceToString())
                        )
                        JSONObject()
                    }
                }
                // Each proof owns a separate disposable store and restores its runtime ports.
                // Collect independent failures; any failure still rejects this entire suite.
                val eisbaerMcp = proof("eisbaer_mcp") { eisbaerMcpProof(owner) }
                val catalogMcp = proof("eisbaer_catalog_mcp") { eisbaerCatalogProof(owner) }
                val planningTimeMcp =
                    proof("eisbaer_planning_time_mcp") { eisbaerPlanningTimeMcpProof(owner) }
                val offersMcp = proof("eisbaer_offers_mcp") { eisbaerOffersMcpProof(owner) }
                val mediaBackupMcp =
                    proof("eisbaer_media_backup_mcp") { eisbaerMediaBackupMcpProof(owner) }
                val revierMcp = proof("eisbaer_revier_mcp") { eisbaerRevierMcpProof(owner) }
                if (proofFailures.length() > 0)
                    throw IllegalStateException(
                        "QA_NATIVE_DATA_INDEPENDENT_PROOFS_FAILED " +
                            JSONObject()
                                .put("failures", proofFailures)
                                .put("completed_proofs", completedProofs)
                    )
                phase = "office_baseline"
                val office = officeReadMeasurements(context, store, data)
                phase = "local_storage_cleanup"
                localStorageCleanup(owner)
                phase = "final_revision"
                val final = data.diagnostics()
                yes(
                    final.getLong("source_revision") == final.getLong("cached_revision"),
                    "COMMITTED_CACHE_REVISION",
                )
                JSONObject()
                    .put("checks", checks)
                    .put("synthetic_only", true)
                    .put("productive_data_read", false)
                    .put("isolated_context_paths", true)
                    .put("projection_rows", 40_000)
                    .put("plans", plans)
                    .put("real_sqlite_transactions", true)
                    .put("wal_concurrent_reader", true)
                    .put("actual_coroutine_flow", true)
                    .put("local_storage_cleanup", true)
                    .put("local_delete_without_cloud_guards", true)
                    .put("cache_photo_concurrency", true)
                    .put("cache_initialization_close_concurrency", true)
                    .put("upload_publication_backup_concurrency", true)
                    .put("chunk_upload_prefix_storage_bounded", true)
                    .put("main_thread_disk_violations", 0)
                    .put("performance_claim", false)
                    .put("office_reads", office)
                    .put("eisbaer_mcp", eisbaerMcp)
                    .put("eisbaer_catalog_mcp", catalogMcp)
                    .put("eisbaer_planning_time_mcp", planningTimeMcp)
                    .put("eisbaer_offers_mcp", offersMcp)
                    .put("eisbaer_media_backup_mcp", mediaBackupMcp)
                    .put("eisbaer_revier_mcp", revierMcp)
            }
        }
    }

    private fun eisbaerOffersMcpProof(owner: Context): JSONObject {
        val initialChecks = checks
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-offers-mcp-$token")
        yes(!root.exists() && root.mkdir(), "OFFERS_MCP_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_offers_mcp_$token")
        val executed = linkedSetOf<String>()
        val successful = linkedSetOf<String>()
        val rejections = JSONArray()
        try {
            val result =
                PixelLocalStore(context).use { store ->
                    val fixture = NativeSettlementNativeTests.fixture()
                    fixture
                        .put("orders", JSONArray())
                        .put("worklogs", JSONArray())
                        .put("service_catalog", JSONArray())
                    // Settlements derive their statutory exemption text, whereas offers require
                    // the issuer's configured text explicitly before document validation.
                    fixture
                        .getJSONObject("business_settings")
                        .put("tax_exemption_text", SettlementRules.EXEMPTION)
                    // Entirely invented and isolated. An unmarked fixture exercises the actual
                    // acceptance path; marked documents deliberately prohibit conversion/delivery.
                    fixture
                        .getJSONArray("customers")
                        .put(
                            JSONObject(
                                    fixture.getJSONArray("customers").getJSONObject(0).toString()
                                )
                                .put("id", "qa_offers_mcp_customer")
                                .put("name", "QA erfundene Eisbär Firmenkundin")
                                .put("test_record", false)
                        )
                    store.putOwnerValue("snapshot", fixture.toString())
                    val proof =
                        OffersMcpProof(
                            PixelMcpReadTools(
                                store.readSource(),
                                store,
                                PixelOwnerAccess(context, store),
                            ),
                            executed,
                            successful,
                            rejections,
                        )
                    phase = "eisbaer_offers_catalog"
                    val entryId = proof.catalog()
                    phase = "eisbaer_offers_draft_share"
                    val marked = proof.draftAndShare(entryId)
                    phase = "eisbaer_offers_decisions"
                    proof
                        .decisions(context, store)
                        .put("catalog_id", entryId)
                        .put("marked_offer_id", marked.getJSONObject("offer").getString("id"))
                        .put(
                            "marked_pdf_sha256",
                            marked.getJSONObject("offer").getString("pdf_sha256"),
                        )
                }
            phase = "eisbaer_offers_reopened"
            PixelLocalStore(context).use { reopened ->
                val proof =
                    OffersMcpProof(
                        PixelMcpReadTools(
                            reopened.readSource(),
                            reopened,
                            PixelOwnerAccess(context, reopened),
                        ),
                        executed,
                        successful,
                        rejections,
                    )
                proof.durableReadback(result)
            }
            val required =
                setOf(
                    "confirm_offer_delivery",
                    "convert_offer_to_order",
                    "create_and_validate_offer",
                    "decline_offer",
                    "duplicate_offer",
                    "finalize_offer",
                    "get_catalog_entry",
                    "get_offer_context",
                    "get_offer_contract",
                    "get_offer_decision_history",
                    "get_offer_template",
                    "list_offers",
                    "prepare_offer_delivery",
                    "prepare_offer_share",
                    "record_offer_acceptance",
                    "search_catalog",
                    "search_offer_templates",
                    "update_catalog_entry",
                    "update_offer_draft",
                    "validate_offer",
                    "validate_offer_draft",
                )
            yes(
                executed.containsAll(required),
                "OFFERS_MCP_ALL_REQUESTED_PUBLIC_BOUNDARIES_EXECUTED",
            )
            yes(
                successful.containsAll(required),
                "OFFERS_MCP_ALL_LOCAL_SUCCESS_PATHS_EXECUTED",
            )
            result.remove("acceptance_args")
            return result
                .put("executed_tools", JSONArray(executed.toList()))
                .put("successful_tools", JSONArray(successful.toList()))
                .put("rejections", rejections)
                .put("checks", checks - initialChecks)
                .put("synthetic_only", true)
                .put("fresh_store_readback", true)
                .put("catalog_aliases_share_original", true)
                .put("invalid_create_atomic", true)
                .put("draft_validation_and_finalization", true)
                .put("acceptance_creates_exactly_one_order", true)
                .put("replay_and_stale_conflicts", true)
                .put("marked_delivery_blocked_before_provider", true)
                .put(
                    "provider_adapter_with_synthetic_transport",
                    result.getJSONObject("delivery_adapter").getBoolean("complete"),
                )
                .put("external_provider_readback_tested", false)
                .put("external_messages_sent", false)
        } finally {
            context.clearPreferences()
            root.deleteRecursively()
        }
    }

    private inner class OffersMcpProof(
        private val tools: PixelMcpReadTools,
        private val executed: MutableSet<String>,
        private val successful: MutableSet<String>,
        private val rejections: JSONArray,
    ) {
        fun call(name: String, args: JSONObject = JSONObject()): JSONObject {
            executed.add(name)
            val response = tools.call(name, args)
            yes(response.optBoolean("ok"), "OFFERS_MCP_OK_$name")
            successful.add(name)
            return response.getJSONObject("data")
        }

        private fun rejected(name: String, args: JSONObject, code: String) {
            executed.add(name)
            try {
                tools.call(name, args)
            } catch (failure: Exception) {
                val actual = McpErrors.rpc(failure).getJSONObject("error").getString("code")
                yes(actual == code, "OFFERS_MCP_REJECT_${name}_${code}_ACTUAL_$actual")
                rejections.put(JSONObject().put("tool", name).put("code", actual))
                return
            }
            throw AssertionError("QA_NATIVE_DATA_OFFERS_MCP_EXPECTED_${name}_$code")
        }

        private fun request(id: String) = JSONObject().put("request_id", "qa_offers_mcp_$id")

        private fun read(id: String) =
            call("get_offer", JSONObject().put("offer_id", id).put("include_review", true))

        private fun count(customer: String) =
            call("list_offers", JSONObject().put("customer_id", customer)).getInt("total")

        private fun history(id: String) =
            call("get_offer_decision_history", JSONObject().put("offer_id", id))

        private fun action(id: String, key: String, read: JSONObject) =
            request(key)
                .put("offer_id", id)
                .put("expected_record_hash", read.getString("record_hash"))

        private fun complete(key: String, marked: Boolean): JSONObject {
            val day = java.time.LocalDate.now()
            return request(key)
                .put(
                    "customer_id",
                    if (marked) "qa_settlement_recipient" else "qa_offers_mcp_customer",
                )
                .put(
                    "title",
                    if (marked) "[TEST] Eisbär MCP Angebot" else "QA erfundenes Eisbär MCP Angebot",
                )
                .put("document_type", "fixed_price")
                .put("issue_date", day.toString())
                .put("valid_until", day.plusDays(14).toString())
                .put("execution_window", "Synthetischer Zeitraum nach Vereinbarung")
                .put("payment_terms", "Binnen 14 Tagen")
                .put("tax_mode", "small_business")
                .put("customer_type", "b2b")
                .put("scope_confirmed", true)
                .put("test_record", marked)
                .put(
                    "positions",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("title", "Erfundene Gartenarbeit")
                                .put("description", "Ausschließlich lokale Testdaten")
                                .put("quantity", 2)
                                .put("price", 35)
                                .put("tax_rate", 0)
                                .put("unit", "h")
                                .put("price_source", "user_confirmed")
                        ),
                )
        }

        fun catalog(): String {
            val contract = call("get_offer_contract")
            yes(
                contract.getJSONArray("document_types").let { rows ->
                    (0 until rows.length()).any {
                        rows.getJSONObject(it).getString("id") == "fixed_price"
                    }
                },
                "OFFERS_MCP_CONTRACT_EXPOSES_FIXED_PRICE",
            )
            val args =
                request("catalog_create")
                    .put("title", "QA Eisbär MCP Baustein")
                    .put("description", "Erfundener bestätigter Baustein")
                    .put("unit", "h")
                    .put("category", "QA Eisbär")
                    .put("pricing_type", "unit")
                    .put("default_price", 30)
                    .put("default_quantity", 2)
                    .put("price_confirmed", true)
                    .put("active", true)
            val entryId = call("create_catalog_entry", args).getString("entry_id")
            val before = call("get_catalog_entry", JSONObject().put("entry_id", entryId))
            val update =
                request("catalog_update")
                    .put("entry_id", entryId)
                    .put("expected_record_hash", before.getString("record_hash"))
                    .put(
                        "updates",
                        JSONObject()
                            .put("default_price", 35)
                            .put("description", "Explizit bestätigte neue Preisfassung"),
                    )
            call("update_catalog_entry", update)
            yes(
                call("update_catalog_entry", update).getBoolean("replayed"),
                "OFFERS_MCP_CATALOG_REPLAY",
            )
            rejected(
                "update_catalog_entry",
                JSONObject(update.toString()).put("request_id", "qa_offers_mcp_catalog_stale"),
                "WRITE_CONFLICT",
            )
            rejected(
                "update_catalog_entry",
                JSONObject(update.toString()).put("updates", JSONObject().put("default_price", 99)),
                "REPLAY_MISMATCH",
            )
            val current = call("get_catalog_entry", JSONObject().put("entry_id", entryId))
            val template = call("get_offer_template", JSONObject().put("entry_id", entryId))
            yes(
                current.getString("record_hash") == template.getString("record_hash") &&
                    current.getJSONObject("entry").getDouble("default_price") == 35.0 &&
                    current.getJSONObject("entry").getString("id") == entryId,
                "OFFERS_MCP_CATALOG_ALIAS_READS_UPDATED_ORIGINAL",
            )
            for (name in listOf("search_catalog", "search_offer_templates")) {
                val rows =
                    call(name, JSONObject().put("query", "Eisbär MCP Baustein").put("limit", 1))
                yes(
                    rows.getInt("total") == 1 &&
                        rows.getJSONArray("entries").length() == 1 &&
                        rows
                            .getJSONArray("entries")
                            .getJSONObject(0)
                            .getJSONObject("entry")
                            .getString("id") == entryId,
                    "OFFERS_MCP_SEARCH_ORIGINAL_$name",
                )
                yes(
                    call(
                            name,
                            JSONObject()
                                .put("query", "Eisbär MCP Baustein")
                                .put("offset", 1)
                                .put("limit", 1),
                        )
                        .getJSONArray("entries")
                        .length() == 0,
                    "OFFERS_MCP_SEARCH_PAGE_END_$name",
                )
            }
            val context =
                call("get_offer_context", JSONObject().put("customer_id", "qa_offers_mcp_customer"))
            val approved = context.getJSONArray("approved_prices")
            yes(
                context.getJSONObject("customer").getString("id") == "qa_offers_mcp_customer" &&
                    approved.length() == 1 &&
                    approved.getJSONObject(0).getJSONObject("entry").getString("id") == entryId,
                "OFFERS_MCP_CONTEXT_USES_CONFIRMED_ORIGINAL_PRICE",
            )
            return entryId
        }

        private fun finalize(id: String, key: String): JSONObject {
            val before = read(id)
            val args =
                action(id, key, before)
                    .put("expected_context_hash", before.getString("context_hash"))
                    .put("confirmed", true)
            call("finalize_offer", args)
            yes(
                call("finalize_offer", args).getBoolean("replayed"),
                "OFFERS_MCP_FINALIZE_REPLAY_$key",
            )
            val result = read(id)
            val offer = result.getJSONObject("offer")
            val metadata =
                call(
                    "get_file_metadata",
                    JSONObject().put("resource_type", "offer").put("resource_id", id),
                )
            yes(
                offer.getString("status") == "finalized" &&
                    offer.getString("pdf_sha256") == metadata.getString("sha256") &&
                    metadata.getLong("byte_size") > 0 &&
                    offer.getString("offer_number").isNotBlank(),
                "OFFERS_MCP_FINALIZED_PDF_ORIGINAL_$key",
            )
            return result
        }

        fun draftAndShare(entryId: String): JSONObject {
            val initialCount = count("qa_settlement_recipient")
            val invalid =
                request("invalid_atomic")
                    .put("customer_id", "qa_settlement_recipient")
                    .put("title", "[TEST] Unvollständiger atomarer Entwurf")
                    .put("positions", JSONArray())
            rejected("create_and_validate_offer", invalid, "DOCUMENT_VALIDATION_FAILED")
            yes(
                count("qa_settlement_recipient") == initialCount,
                "OFFERS_MCP_INVALID_ATOMIC_CREATE_NO_ORIGINAL",
            )
            val created =
                call(
                    "create_offer_draft",
                    request("incomplete_create")
                        .put("customer_id", "qa_settlement_recipient")
                        .put("title", "[TEST] Bewusst unvollständig")
                        .put("positions", JSONArray()),
                )
            val id = created.getString("offer_id")
            val incomplete = call("validate_offer", JSONObject().put("offer_id", id))
            yes(
                !incomplete.getJSONObject("validation").getBoolean("valid") &&
                    incomplete.getJSONObject("offer").getString("status") == "draft",
                "OFFERS_MCP_INCOMPLETE_DRAFT_REPORTS_ERRORS",
            )
            rejected(
                "validate_offer_draft",
                action(id, "invalid_validation", incomplete),
                "DOCUMENT_VALIDATION_FAILED",
            )
            yes(
                read(id).getString("record_hash") == incomplete.getString("record_hash"),
                "OFFERS_MCP_INVALID_VALIDATION_NO_MUTATION",
            )
            val updates = complete("unused", true).apply { remove("request_id") }
            val entry = call("get_catalog_entry", JSONObject().put("entry_id", entryId))
            updates.getJSONArray("positions").getJSONObject(0).apply {
                remove("price_source")
                put("catalog_entry_id", entryId).put("catalog_hash", entry.getString("record_hash"))
            }
            val update = action(id, "complete_update", incomplete).put("updates", updates)
            call("update_offer_draft", update)
            yes(
                call("update_offer_draft", update).getBoolean("replayed"),
                "OFFERS_MCP_DRAFT_UPDATE_REPLAY",
            )
            rejected(
                "update_offer_draft",
                JSONObject(update.toString()).put("request_id", "qa_offers_mcp_draft_stale"),
                "WRITE_CONFLICT",
            )
            val valid = call("validate_offer", JSONObject().put("offer_id", id))
            val validation = valid.getJSONObject("validation")
            yes(
                validation.getBoolean("valid"),
                "OFFERS_MCP_CORRECTED_DRAFT_VALIDATION_${validation.getJSONArray("errors")}",
            )
            val corrected = valid.getJSONObject("offer")
            val correctedLine = corrected.getJSONArray("positions").getJSONObject(0)
            yes(
                corrected.getDouble("gross_total") == 70.0 &&
                    correctedLine.getDouble("price") == 35.0 &&
                    correctedLine.getDouble("quantity") == 2.0 &&
                    correctedLine.getString("catalog_entry_id") == entryId &&
                    correctedLine.getString("catalog_hash") == entry.getString("record_hash") &&
                    correctedLine.getString("price_basis") == "catalog" &&
                    !correctedLine.has("price_source"),
                "OFFERS_MCP_CORRECTED_DRAFT_CONFIRMED_CATALOG_PRICE",
            )
            val validate = action(id, "validate_complete", valid)
            call("validate_offer_draft", validate)
            yes(
                call("validate_offer_draft", validate).getBoolean("replayed"),
                "OFFERS_MCP_VALIDATION_REPLAY",
            )
            val validated = read(id)
            yes(
                validated.getJSONObject("offer").getString("status") == "validated",
                "OFFERS_MCP_VALIDATED_STATUS_DURABLE",
            )
            rejected(
                "finalize_offer",
                action(id, "bad_context", validated)
                    .put("expected_context_hash", "0".repeat(64))
                    .put("confirmed", true),
                "WRITE_CONFLICT",
            )
            yes(
                read(id).getString("record_hash") == validated.getString("record_hash") &&
                    read(id).getJSONObject("offer").optString("pdf_sha256").isBlank(),
                "OFFERS_MCP_CONTEXT_CONFLICT_NO_FINALIZATION",
            )
            val finalized = finalize(id, "marked_finalize")
            val share = action(id, "share_preview", finalized)
            val prepared = call("prepare_offer_share", share)
            yes(
                prepared.getString("state") == "test_preview_only" &&
                    !prepared.getBoolean("sent") &&
                    prepared.getJSONObject("document").getString("sha256") ==
                        finalized.getJSONObject("offer").getString("pdf_sha256"),
                "OFFERS_MCP_SHARE_LOCAL_ORIGINAL_PREVIEW",
            )
            yes(
                call("prepare_offer_share", share).getBoolean("replayed"),
                "OFFERS_MCP_SHARE_REPLAY",
            )
            val shared = read(id)
            yes(
                shared.getJSONObject("offer").getString("delivery_status") == "share_prepared" &&
                    !shared.getJSONObject("offer").has("delivery_receipt"),
                "OFFERS_MCP_PREPARED_IS_NEVER_SENT",
            )
            rejected(
                "prepare_offer_delivery",
                action(id, "blocked_delivery", shared)
                    .put("recipient_email", "qa@example.invalid")
                    .put("confirmed", true),
                "TEST_DELIVERY_BLOCKED",
            )
            rejected(
                "confirm_offer_delivery",
                action(id, "blocked_confirmation", shared)
                    .put("provider", "gmail")
                    .put("message_id", "qa_synthetic_message_never_sent"),
                "TEST_DELIVERY_BLOCKED",
            )
            rejected(
                "record_offer_acceptance",
                action(id, "blocked_test_conversion", shared)
                    .put("accepted_at", java.time.Instant.now().minusSeconds(60).toString())
                    .put("evidence", "Erfundene Annahme eines gesperrten Testdokuments"),
                "TEST_CONVERSION_BLOCKED",
            )
            yes(
                read(id).getString("record_hash") == shared.getString("record_hash") &&
                    history(id).getInt("total") == 0 &&
                    call(
                            "get_offer_context",
                            JSONObject().put("customer_id", "qa_settlement_recipient"),
                        )
                        .getJSONArray("open_orders")
                        .length() == 0,
                "OFFERS_MCP_BLOCKED_DELIVERY_AND_CONVERSION_ROLL_BACK_DOCUMENT_DECISION_ORDER",
            )
            return shared
        }

        fun decisions(context: Context, store: PixelLocalStore): JSONObject {
            val args = complete("validated_create", false)
            val id = call("create_and_validate_offer", args).getString("offer_id")
            yes(
                call("create_and_validate_offer", args).getBoolean("replayed") &&
                    count("qa_offers_mcp_customer") == 1,
                "OFFERS_MCP_VALID_CREATE_REPLAY_ONE_ORIGINAL",
            )
            yes(
                read(id).getJSONObject("offer").getString("status") == "validated",
                "OFFERS_MCP_ATOMIC_CREATE_IS_VALIDATED",
            )
            val finalized = finalize(id, "accept_finalize")
            // Preparing a handoff is local only: no intent is launched and no mail client is
            // invoked.
            val deliveryArgs =
                action(id, "local_mail_preparation", finalized)
                    .put("recipient_email", "qa@example.invalid")
                    .put("confirmed", true)
            val handoff = call("prepare_offer_delivery", deliveryArgs)
            yes(
                handoff.getString("state") == "mail_provider_handoff" &&
                    !handoff.getBoolean("sent") &&
                    handoff.getString("recipient_email") == "qa@example.invalid" &&
                    handoff.getJSONObject("document").getString("sha256") ==
                        finalized.getJSONObject("offer").getString("pdf_sha256"),
                "OFFERS_MCP_DELIVERY_PREPARATION_LOCAL_UNSENT_ORIGINAL",
            )
            yes(
                call("prepare_offer_delivery", deliveryArgs).getBoolean("replayed"),
                "OFFERS_MCP_DELIVERY_PREPARATION_REPLAY",
            )
            val preparedForDelivery = read(id)
            yes(
                preparedForDelivery.getJSONObject("offer").getString("delivery_status") ==
                    "share_prepared" &&
                    !preparedForDelivery.getJSONObject("offer").has("delivery_receipt"),
                "OFFERS_MCP_LOCAL_MAIL_PREPARATION_DOES_NOT_PROVE_SENT",
            )
            val delivery = deliveryAdapterReadback(context, store, id, preparedForDelivery)
            val readyForAcceptance = read(id)
            val acceptedAt = java.time.Instant.now().minusSeconds(60).toString()
            val acceptance =
                action(id, "accept", readyForAcceptance)
                    .put("accepted_at", acceptedAt)
                    .put(
                        "evidence",
                        "Erfundene Zustimmung für ausschließlich isolierten MCP-Funktionstest",
                    )
            rejected(
                "record_offer_acceptance",
                JSONObject(acceptance.toString())
                    .put("request_id", "qa_offers_mcp_accept_stale")
                    .put("expected_record_hash", "0".repeat(64)),
                "WRITE_CONFLICT",
            )
            yes(history(id).getInt("total") == 0, "OFFERS_MCP_STALE_ACCEPTANCE_NO_HISTORY")
            val accepted = call("record_offer_acceptance", acceptance)
            val orderId = accepted.getString("order_id")
            yes(
                call("record_offer_acceptance", acceptance).getBoolean("replayed"),
                "OFFERS_MCP_ACCEPTANCE_REPLAY",
            )
            rejected(
                "record_offer_acceptance",
                JSONObject(acceptance.toString())
                    .put("evidence", "Widersprüchliche zweite Anfrage"),
                "REPLAY_MISMATCH",
            )
            val current = read(id)
            val decision = history(id)
            yes(
                current.getJSONObject("offer").getString("status") == "accepted" &&
                    current.getJSONObject("offer").getString("converted_order_id") == orderId &&
                    current.getJSONObject("offer").getString("pdf_sha256") ==
                        finalized.getJSONObject("offer").getString("pdf_sha256") &&
                    decision.getInt("total") == 1 &&
                    decision.getString("decision_status") == "accepted" &&
                    decision.getJSONArray("history").getJSONObject(0).getString("accepted_at") ==
                        acceptedAt &&
                    decision.getJSONArray("history").getJSONObject(0).getString("request_id") ==
                        acceptance.getString("request_id"),
                "OFFERS_MCP_ACCEPTANCE_ORIGINAL_AND_ONE_DECISION",
            )
            val conversion = action(id, "compat_convert", current).put("confirmed", true)
            val compatibility = call("convert_offer_to_order", conversion)
            yes(
                compatibility.getBoolean("already_converted") &&
                    !compatibility.getBoolean("invoice_created") &&
                    compatibility.getString("order_id") == orderId &&
                    call("convert_offer_to_order", conversion).getBoolean("replayed"),
                "OFFERS_MCP_COMPATIBILITY_RETURNS_SAME_ORDER",
            )
            rejected(
                "convert_offer_to_order",
                action(id, "compat_stale", finalized).put("confirmed", true),
                "WRITE_CONFLICT",
            )
            val duplicateArgs = request("duplicate").put("offer_id", id)
            val duplicateId = call("duplicate_offer", duplicateArgs).getString("offer_id")
            yes(
                call("duplicate_offer", duplicateArgs).getBoolean("replayed") &&
                    duplicateId != id &&
                    count("qa_offers_mcp_customer") == 2,
                "OFFERS_MCP_DUPLICATE_REPLAY_ONE_NEW_ORIGINAL",
            )
            val duplicate = read(duplicateId).getJSONObject("offer")
            yes(
                duplicate.getString("status") == "draft" &&
                    duplicate.optString("pdf_sha256").isBlank() &&
                    duplicate.optString("converted_order_id").isBlank() &&
                    history(duplicateId).getInt("total") == 0,
                "OFFERS_MCP_DUPLICATE_HAS_NO_OLD_PDF_ORDER_OR_DECISION",
            )
            val declineFinal = finalize(duplicateId, "decline_finalize")
            val declineArgs =
                action(duplicateId, "decline", declineFinal)
                    .put("reason", "Erfundene Ablehnung im lokalen Test")
            call("decline_offer", declineArgs)
            yes(
                call("decline_offer", declineArgs).getBoolean("replayed"),
                "OFFERS_MCP_DECLINE_REPLAY",
            )
            val declined = history(duplicateId)
            yes(
                declined.getInt("total") == 1 &&
                    declined.getString("decision_status") == "declined" &&
                    declined.getJSONArray("history").getJSONObject(0).getString("reason") ==
                        declineArgs.getString("reason") &&
                    read(duplicateId).getJSONObject("offer").getString("pdf_sha256") ==
                        declineFinal.getJSONObject("offer").getString("pdf_sha256"),
                "OFFERS_MCP_DECLINE_PRESERVES_ORIGINAL_AND_EVIDENCE",
            )
            rejected(
                "convert_offer_to_order",
                action(duplicateId, "declined_conversion", read(duplicateId))
                    .put("confirmed", true),
                "OFFER_DECLINED",
            )
            rejected(
                "record_offer_acceptance",
                action(duplicateId, "after_decline", read(duplicateId))
                    .put("accepted_at", acceptedAt)
                    .put("evidence", "Unzulässige zweite Entscheidung"),
                "OFFER_DECISION_ALREADY_RECORDED",
            )
            yes(
                history(id).getInt("total") == 1 && history(duplicateId).getInt("total") == 1,
                "OFFERS_MCP_REJECTED_WRITES_NO_EXTRA_DECISIONS",
            )
            return JSONObject()
                .put("accepted_offer_id", id)
                .put("delivery_adapter", delivery)
                .put("declined_offer_id", duplicateId)
                .put("order_id", orderId)
                .put("acceptance_args", acceptance)
                .put("accepted_pdf_sha256", current.getJSONObject("offer").getString("pdf_sha256"))
        }

        /** Only the provider wire is synthetic; parsing, domain application and SQLite are real. */
        private fun deliveryAdapterReadback(
            context: Context,
            store: PixelLocalStore,
            id: String,
            prepared: JSONObject,
        ): JSONObject {
            val initial = checks
            val original = prepared.getJSONObject("offer")
            val pdf = BusinessDocumentStore(context).read(original)
            val pdfHash = PhotoAttachmentStore.sha256(pdf)
            val messageId = "qa_eisbaer_sent_original"
            val sender = original.getJSONObject("issuer").getString("business_email")
            val recipient = original.getString("delivery_recipient")
            val stamp = java.time.Instant.now().minusSeconds(1).toEpochMilli().toString()
            var mode = "valid"
            var providerReads = 0
            val wire = HttpTransport.Wire { method, url, headers, body, _ ->
                yes(
                    method == "GET" &&
                        body == null &&
                        url ==
                            "https://www.googleapis.com/gmail/v1/users/me/messages/$messageId?format=full" &&
                        headers["Authorization"] == "Bearer qa_synthetic_mail_readonly",
                    "OFFERS_MCP_DELIVERY_REAL_CLIENT_READONLY_SYNTHETIC_WIRE",
                )
                providerReads++
                if (mode == "temporary")
                    HttpTransport.Reply(503, byteArrayOf(), mapOf("Retry-After" to "1"))
                else {
                    val attachment = pdf.copyOf()
                    if (mode == "wrong_pdf")
                        attachment[attachment.lastIndex] =
                            (attachment.last().toInt() xor 1).toByte()
                    val message =
                        JSONObject()
                            .put("id", messageId)
                            .put(
                                "labelIds",
                                JSONArray(listOf(if (mode == "not_sent") "DRAFT" else "SENT")),
                            )
                            .put("internalDate", stamp)
                            .put(
                                "payload",
                                JSONObject()
                                    .put("mimeType", "multipart/mixed")
                                    .put(
                                        "headers",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("name", "From")
                                                    .put(
                                                        "value",
                                                        if (mode == "wrong_sender")
                                                            "other@example.invalid"
                                                        else sender,
                                                    )
                                            )
                                            .put(
                                                JSONObject()
                                                    .put("name", "To")
                                                    .put(
                                                        "value",
                                                        if (mode == "wrong_recipient")
                                                            "other@example.invalid"
                                                        else recipient,
                                                    )
                                            ),
                                    )
                                    .put(
                                        "parts",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("mimeType", "application/pdf")
                                                    .put(
                                                        "body",
                                                        JSONObject()
                                                            .put("size", attachment.size)
                                                            .put(
                                                                "data",
                                                                java.util.Base64.getUrlEncoder()
                                                                    .withoutPadding()
                                                                    .encodeToString(attachment),
                                                            ),
                                                    )
                                            ),
                                    ),
                            )
                    HttpTransport.Reply(
                        200,
                        message.toString().toByteArray(Charsets.UTF_8),
                        emptyMap(),
                    )
                }
            }
            val client = OfferDeliveryClient({ "qa_synthetic_mail_readonly" }, wire)
            val local = LocalOwnerRepository(context, store)
            val repository =
                object : OwnerRepository by local {
                    override fun verifyDelivery(messageId: String, offer: JSONObject): JSONObject =
                        client.verify(messageId, offer)
                }
            val application = OwnerApplicationService(repository)
            // This scoped sink replaces the existing verification port for this one command.
            // It never changes global runtime ports or the ordinary store's production client.
            val sink =
                object : PixelMcpWriteTools.Sink {
                    override fun writable() = store.writable()

                    override fun status(id: String, state: JSONObject) = store.status(id, state)

                    override fun execute(tool: String, args: JSONObject): JSONObject {
                        check(tool == "confirm_offer_delivery")
                        McpSchema.validate(args, OfferTools.schema(tool))
                        val request =
                            "mcp_owner_" + PixelMcpWriteTools.hash(args.getString("request_id"))
                        val fingerprint =
                            PixelMcpWriteTools.hash(
                                JSONObject().put("tool", tool).put("args", args)
                            )
                        val result =
                            local.runRequest(request, fingerprint) {
                                application.perform(tool, JSONObject(args.toString()), request)
                            }
                        return JSONObject()
                            .put("ok", true)
                            .put("data", result)
                            .put("cloud_confirmed", false)
                    }
                }
            val adapter =
                PixelMcpReadTools(store.readSource(), sink, PixelOwnerAccess(context, store))
            fun invoke(args: JSONObject): JSONObject {
                executed.add("confirm_offer_delivery")
                val response = adapter.call("confirm_offer_delivery", args)
                yes(response.getBoolean("ok"), "OFFERS_MCP_DELIVERY_PUBLIC_ADAPTER_OK")
                successful.add("confirm_offer_delivery")
                return response.getJSONObject("data")
            }
            fun args(key: String) =
                action(id, "delivery_adapter_$key", prepared)
                    .put("provider", "gmail")
                    .put("message_id", messageId)
            fun rejected(args: JSONObject, code: String) {
                executed.add("confirm_offer_delivery")
                val receiptKey =
                    "receipt:mcp_owner_" + PixelMcpWriteTools.hash(args.getString("request_id"))
                val originalReceipt = store.ownerValue(receiptKey, "")
                try {
                    adapter.call("confirm_offer_delivery", args)
                } catch (failure: Exception) {
                    val actual = McpErrors.rpc(failure).getJSONObject("error").getString("code")
                    yes(actual == code, "OFFERS_MCP_DELIVERY_REJECT_${mode}_$actual")
                    yes(
                        store.ownerValue(receiptKey, "") == originalReceipt,
                        "OFFERS_MCP_DELIVERY_REJECTION_PRESERVES_REQUEST_RECEIPT",
                    )
                    rejections.put(
                        JSONObject().put("tool", "confirm_offer_delivery").put("code", actual)
                    )
                    return
                }
                throw AssertionError("QA_EXPECTED_DELIVERY_REJECTION_$mode")
            }
            val confirmation = args("confirmed_original")
            val confirmedRequest =
                "mcp_owner_" + PixelMcpWriteTools.hash(confirmation.getString("request_id"))
            fun untouchedActions(): String =
                PixelMcpWriteTools.hash(
                    JSONArray(
                        SettlementRules.rows(store.pendingActions()).filter {
                            it.getString("client_id") != confirmedRequest
                        }
                    )
                )
            fun businessProof(allowDelivery: Boolean = false): String {
                val state = PixelMcpReadTools.effective(store.getState())
                if (allowDelivery) {
                    val target = PixelMcpWriteTools.find(state.getJSONArray("offers"), id)
                    for (key in
                        listOf(
                            "delivery_status",
                            "delivery_receipt",
                            "sent_at",
                            "updated_at",
                        )) target.remove(key)
                    // Remove exactly this expected action's derived indices, not other journal
                    // data.
                    val applied = state.getJSONArray("drive_applied_ids")
                    state.put(
                        "drive_applied_ids",
                        JSONArray(
                            (0 until applied.length())
                                .map { applied.getString(it) }
                                .filter { it != confirmedRequest }
                                .sorted()
                        ),
                    )
                    state.getJSONObject("drive_action_hashes").remove(confirmedRequest)
                    state.getJSONObject("drive_action_receipts").remove(confirmedRequest)
                }
                val proof =
                    JSONObject()
                        .put("business", state)
                        .put("ledger", store.ownerValue("settlement:v1", "{}"))
                for (table in listOf("work_sessions", "work_session_events")) store.readableDatabase
                    .rawQuery("SELECT COUNT(*) FROM $table", null)
                    .use {
                        yes(it.moveToFirst(), "OFFERS_MCP_DELIVERY_COUNT_$table")
                        proof.put(table, it.getLong(0))
                    }
                return PixelMcpWriteTools.hash(proof)
            }
            fun deliveries(): Int =
                SettlementRules.rows(store.pendingActions()).count {
                    it.getString("kind") == "offer_delivery" &&
                        it.getJSONObject("payload").optString("id") == id
                }
            val before = businessProof()
            val unrelated = businessProof(true)
            val beforeDeliveries = deliveries()
            val otherActions = untouchedActions()
            yes(
                pdfHash == original.getString("pdf_sha256"),
                "OFFERS_MCP_DELIVERY_REAL_ORIGINAL_PDF",
            )
            for (bad in listOf("not_sent", "wrong_sender", "wrong_recipient", "wrong_pdf")) {
                mode = bad
                rejected(args(bad), "INVALID_INPUT")
                yes(
                    businessProof() == before &&
                        deliveries() == beforeDeliveries &&
                        read(id).getString("record_hash") == prepared.getString("record_hash") &&
                        PhotoAttachmentStore.sha256(
                            BusinessDocumentStore(context).read(read(id).getJSONObject("offer"))
                        ) == pdfHash,
                    "OFFERS_MCP_DELIVERY_BAD_${bad}_ROLLS_BACK_ORIGINAL_AND_RECEIPT",
                )
            }
            mode = "temporary"
            rejected(confirmation, "MAIL_TEMPORARY_UNAVAILABLE")
            yes(businessProof() == before, "OFFERS_MCP_DELIVERY_TEMPORARY_FAILURE_NO_COMMIT")
            mode = "valid"
            val confirmed = invoke(confirmation)
            val saved = read(id).getJSONObject("offer")
            val receipt = saved.getJSONObject("delivery_receipt")
            yes(
                confirmed.getString("offer_id") == id &&
                    confirmed.getBoolean("sent") &&
                    confirmed.getString("state") == "sent" &&
                    saved.getString("delivery_status") == "sent" &&
                    receipt.getString("verified_by") == "pixel_gmail_readback" &&
                    receipt.getString("message_id") == messageId &&
                    receipt.getString("recipient_email") == recipient &&
                    receipt.getString("pdf_sha256") == pdfHash &&
                    saved.getString("sent_at") ==
                        java.time.Instant.ofEpochMilli(stamp.toLong()).toString() &&
                    deliveries() == beforeDeliveries + 1 &&
                    untouchedActions() == otherActions &&
                    businessProof(true) == unrelated,
                "OFFERS_MCP_DELIVERY_REAL_CLIENT_SQLITE_COMMIT_ONLY_VERIFIED_ORIGINAL",
            )
            val receiptHash = PixelMcpWriteTools.hash(receipt)
            val after = businessProof()
            val readsBeforeReplay = providerReads
            val replay = invoke(confirmation)
            yes(
                replay.getBoolean("replayed") &&
                    replay.getString("offer_id") == id &&
                    providerReads == readsBeforeReplay &&
                    deliveries() == beforeDeliveries + 1 &&
                    businessProof() == after,
                "OFFERS_MCP_DELIVERY_SAME_REQUEST_REPLAY_NO_PROVIDER_OR_BUSINESS_DUPLICATE",
            )
            rejected(
                JSONObject(confirmation.toString()).put("message_id", "different_message"),
                "REPLAY_MISMATCH",
            )
            rejected(
                JSONObject(confirmation.toString())
                    .put("request_id", "qa_offers_mcp_delivery_adapter_stale"),
                "WRITE_CONFLICT",
            )
            yes(
                providerReads == readsBeforeReplay &&
                    businessProof() == after &&
                    PhotoAttachmentStore.sha256(
                        BusinessDocumentStore(context).read(read(id).getJSONObject("offer"))
                    ) == pdfHash,
                "OFFERS_MCP_DELIVERY_CONFLICTS_PRESERVE_VERIFIED_ORIGINAL",
            )
            PixelLocalStore(context).use { fresh ->
                val reopened =
                    PixelMcpReadTools(fresh.readSource(), fresh, PixelOwnerAccess(context, fresh))
                        .call("get_offer", JSONObject().put("offer_id", id))
                        .getJSONObject("data")
                        .getJSONObject("offer")
                yes(
                    reopened.getString("delivery_status") == "sent" &&
                        PixelMcpWriteTools.hash(reopened.getJSONObject("delivery_receipt")) ==
                            receiptHash &&
                        reopened.getString("pdf_sha256") == pdfHash,
                    "OFFERS_MCP_DELIVERY_FRESH_SQLITE_READBACK_SAME_RECEIPT_AND_PDF",
                )
            }
            return JSONObject()
                .put("complete", true)
                .put("offer_id", id)
                .put("message_id", messageId)
                .put("receipt_hash", receiptHash)
                .put("pdf_sha256", pdfHash)
                .put("provider_transport", "SYNTHETIC_IN_PROCESS_WIRE")
                .put("external_provider_success_claimed", false)
                .put("external_messages_sent", false)
                .put("checks", checks - initial)
        }

        fun durableReadback(result: JSONObject) {
            val acceptedId = result.getString("accepted_offer_id")
            val declinedId = result.getString("declined_offer_id")
            val orderId = result.getString("order_id")
            val accepted = read(acceptedId)
            val delivery = result.getJSONObject("delivery_adapter")
            yes(
                accepted.getJSONObject("offer").getString("delivery_status") == "sent" &&
                    PixelMcpWriteTools.hash(
                        accepted.getJSONObject("offer").getJSONObject("delivery_receipt")
                    ) == delivery.getString("receipt_hash") &&
                    accepted.getJSONObject("offer").getString("pdf_sha256") ==
                        delivery.getString("pdf_sha256"),
                "OFFERS_MCP_DELIVERY_REOPEN_AFTER_ACCEPTANCE_PRESERVES_VERIFIED_ORIGINAL",
            )
            val context =
                call("get_offer_context", JSONObject().put("customer_id", "qa_offers_mcp_customer"))
            val orders = context.getJSONArray("open_orders")
            yes(
                orders.length() == 1 &&
                    orders.getJSONObject(0).getString("id") == orderId &&
                    orders.getJSONObject(0).getString("customer_id") == "qa_offers_mcp_customer" &&
                    accepted.getJSONObject("offer").getString("converted_order_id") == orderId,
                "OFFERS_MCP_REOPEN_EXACTLY_ONE_ORIGINAL_ORDER",
            )
            val replay = call("record_offer_acceptance", result.getJSONObject("acceptance_args"))
            yes(
                replay.getBoolean("replayed") &&
                    replay.getString("order_id") == orderId &&
                    history(acceptedId).getInt("total") == 1 &&
                    history(declinedId).getInt("total") == 1 &&
                    history(acceptedId).getString("decision_status") == "accepted" &&
                    history(declinedId).getString("decision_status") == "declined",
                "OFFERS_MCP_REOPEN_REPLAY_AND_DECISIONS",
            )
            yes(
                call("get_offer_context", JSONObject().put("customer_id", "qa_offers_mcp_customer"))
                    .getJSONArray("open_orders")
                    .length() == 1,
                "OFFERS_MCP_REOPEN_REPLAY_NO_DUPLICATE_ORDER",
            )
            for ((id, hash) in
                listOf(
                    acceptedId to result.getString("accepted_pdf_sha256"),
                    result.getString("marked_offer_id") to result.getString("marked_pdf_sha256"),
                )) {
                val metadata =
                    call(
                        "get_file_metadata",
                        JSONObject().put("resource_type", "offer").put("resource_id", id),
                    )
                yes(
                    metadata.getString("sha256") == hash && metadata.getLong("byte_size") > 0,
                    "OFFERS_MCP_REOPEN_ORIGINAL_PDF_$id",
                )
            }
            yes(
                call(
                        "get_catalog_entry",
                        JSONObject().put("entry_id", result.getString("catalog_id")),
                    )
                    .getJSONObject("entry")
                    .getDouble("default_price") == 35.0,
                "OFFERS_MCP_REOPEN_CATALOG_PRICE",
            )
            yes(
                count("qa_offers_mcp_customer") == 2 && count("qa_settlement_recipient") == 1,
                "OFFERS_MCP_REOPEN_EXACT_ORIGINAL_COUNTS",
            )
        }
    }

    private fun eisbaerMcpProof(owner: Context): JSONObject {
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-mcp-$token")
        yes(!root.exists() && root.mkdir(), "EISBAER_MCP_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_mcp_$token")
        try {
            return PixelLocalStore(context).use { store ->
                val fixture = SyntheticBusinessData.profile("minimal")
                fixture
                    .getJSONArray("customers")
                    .getJSONObject(0)
                    .put("name", "QA Eisbär Einsatzkunde")
                    .put("default_billing_customer_id", "qa_mcp_recipient")
                fixture
                    .getJSONArray("customers")
                    .put(
                        JSONObject()
                            .put("id", "qa_mcp_recipient")
                            .put("name", "QA Eisbär Rechnungsempfänger")
                            .put("city", "QA Rechnungsort")
                            .put("active", 1)
                            .put("test_record", true)
                    )
                for ((id, date, amount) in
                    listOf(
                        Triple("qa_mcp_search_lcr", "2026-10-04", "12.34"),
                        Triple("qa_mcp_search_open", "2026-10-05", "56.78"),
                    )) fixture
                    .getJSONArray("worklogs")
                    .put(
                        JSONObject()
                            .put("id", id)
                            .put("customer_id", "qa_customer_1")
                            .put("billing_customer_id", "qa_mcp_recipient")
                            .put("order_id", "")
                            .put("activity", "QA Eisbär Sucharbeit")
                            .put("work_date", date)
                            .put("created_at", "2026-11-01T09:00:00Z")
                            .put("accounting_amount", amount)
                            .put("hours", 1)
                            .put("billed", 0)
                            .put("billable", true)
                            .put("test_record", true)
                    )
                store.putOwnerValue("snapshot", fixture.toString())
                val tools =
                    PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
                val executed = linkedSetOf<String>()
                fun call(name: String, args: JSONObject = JSONObject()): JSONObject {
                    val result = tools.call(name, args)
                    yes(result.optBoolean("ok"), "EISBAER_MCP_OK_$name")
                    executed.add(name)
                    return result.getJSONObject("data")
                }
                fun rejected(name: String, args: JSONObject, code: String): JSONObject {
                    try {
                        tools.call(name, args)
                    } catch (failure: Exception) {
                        val error = McpErrors.rpc(failure).getJSONObject("error")
                        yes(error.getString("code") == code, "EISBAER_MCP_ERROR_${name}_$code")
                        executed.add(name)
                        return error
                    }
                    throw AssertionError("QA_NATIVE_DATA_EISBAER_MCP_EXPECTED_${name}_$code")
                }
                fun request(id: String) = JSONObject().put("request_id", "qa_eisbaer_mcp_$id")
                fun appointment(id: String) =
                    call("get_appointment", JSONObject().put("appointment_id", id))
                fun board(day: String) =
                    call(
                        "get_planning_board",
                        JSONObject().put("date_from", day).put("date_to", day),
                    )
                fun originalRows(): JSONArray =
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments")
                fun sourceCount(rows: JSONArray, id: String): Int =
                    SettlementRules.rows(rows).count {
                        it.optString("source_id", it.optString("id")) == id
                    }
                fun ownerFingerprint(): String {
                    val rows = JSONArray()
                    store.readableDatabase
                        .rawQuery("SELECT key,value FROM owner_state ORDER BY key", null)
                        .use { cursor ->
                            while (cursor.moveToNext()) rows.put(
                                JSONObject()
                                    .put("key", cursor.getString(0))
                                    .put("value", cursor.getString(1))
                            )
                        }
                    return PixelMcpWriteTools.hash(rows)
                }

                phase = "eisbaer_mcp_empty_appointment"
                val emptyArgs = request("empty_create")
                val created = call("create_appointment", emptyArgs)
                val emptyId = created.getString("appointment_id")
                val empty = appointment(emptyId)
                val emptyRow = empty.getJSONObject("appointment")
                yes(
                    emptyRow.getString("customer_id").isBlank() &&
                        emptyRow.getString("title").isBlank(),
                    "EISBAER_EMPTY_NO_INVENTED_CONTEXT",
                )
                yes(
                    emptyRow.getString("starts_at").isBlank() &&
                        emptyRow.getString("ends_at").isBlank(),
                    "EISBAER_EMPTY_NO_INVENTED_TIME",
                )
                yes(
                    call("create_appointment", emptyArgs).getBoolean("replayed"),
                    "EISBAER_EMPTY_CREATE_REPLAY",
                )
                yes(originalRows().length() == 1, "EISBAER_EMPTY_ONE_ORIGINAL")
                val oldEmptyHash = empty.getString("record_hash")
                val editEmpty =
                    request("empty_update")
                        .put("appointment_id", emptyId)
                        .put("expected_record_hash", oldEmptyHash)
                        .put(
                            "updates",
                            JSONObject()
                                .put("title", "QA Eisbär leerer Termin ergänzt")
                                .put("date", "2027-05-18"),
                        )
                call("update_appointment", editEmpty)
                yes(
                    call("update_appointment", editEmpty).getBoolean("replayed"),
                    "EISBAER_EMPTY_UPDATE_REPLAY",
                )
                val emptyUpdated = appointment(emptyId)
                yes(
                    emptyUpdated.getJSONObject("appointment").getString("id") == emptyId &&
                        originalRows().length() == 1,
                    "EISBAER_EMPTY_UPDATE_SAME_ID",
                )
                yes(
                    emptyUpdated.getJSONObject("appointment").getString("title") ==
                        "QA Eisbär leerer Termin ergänzt",
                    "EISBAER_EMPTY_UPDATE_REREAD",
                )
                rejected(
                    "update_appointment",
                    JSONObject(editEmpty.toString())
                        .put("request_id", "qa_eisbaer_mcp_empty_stale"),
                    "WRITE_CONFLICT",
                )
                rejected(
                    "create_appointment",
                    request("invalid_time")
                        .put("starts_at", "2027-05-19T10:00:00Z")
                        .put("ends_at", "2027-05-19T09:00:00Z"),
                    "INVALID_INPUT",
                )
                yes(
                    originalRows().length() == 1 &&
                        appointment(emptyId).getString("record_hash") ==
                            emptyUpdated.getString("record_hash"),
                    "EISBAER_EMPTY_REJECTIONS_KEEP_ORIGINAL",
                )

                phase = "eisbaer_mcp_canonical_appointment"
                val property =
                    call(
                            "create_property",
                            request("property")
                                .put("customer_id", "qa_customer_1")
                                .put("name", "QA Eisbär MCP Objekt"),
                        )
                        .getJSONObject("record")
                val propertyId = property.getString("id")
                val canonicalId =
                    call(
                            "create_appointment",
                            request("canonical_create")
                                .put("customer_id", "qa_customer_1")
                                .put("date", "2027-05-19")
                                .put("title", "QA Eisbär kanonischer Termin"),
                        )
                        .getString("appointment_id")
                val visitId =
                    call(
                            "plan_visit",
                            request("canonical_visit")
                                .put("property_id", propertyId)
                                .put("title", "QA Eisbär verknüpfter Besuch")
                                .put("date", "2027-05-19")
                                .put("planned_minutes", 30),
                        )
                        .getString("visit_id")
                val visitKey = RevierRules.ROOT + "entity:visit:" + visitId
                // Arrange a persisted legacy link; the mutation under test still uses the public
                // adapter.
                store.putOwnerValue(
                    visitKey,
                    JSONObject(store.ownerValue(visitKey, "{}"))
                        .put("appointment_id", canonicalId)
                        .toString(),
                )
                val linked =
                    call("get_visit", JSONObject().put("visit_id", visitId)).getJSONObject("record")
                val moveLegacy =
                    request("canonical_legacy_move")
                        .put("visit_id", visitId)
                        .put("expected_record_hash", linked.getString("record_hash"))
                        .put("updates", JSONObject().put("date", "2027-05-20"))
                        .put("reason", "QA Eisbär bewusst verschieben")
                val beforeLegacy = ownerFingerprint()
                val redirected =
                    rejected("update_visit", moveLegacy, "CANONICAL_APPOINTMENT_REQUIRED")
                rejected("update_visit", moveLegacy, "CANONICAL_APPOINTMENT_REQUIRED")
                yes(ownerFingerprint() == beforeLegacy, "EISBAER_CANONICAL_REJECT_NO_MUTATION")
                yes(
                    redirected.getString("appointment_id") == canonicalId &&
                        redirected.getString("next_tool") == "update_appointment",
                    "EISBAER_CANONICAL_TARGET_PUBLIC",
                )
                val canonicalHash = appointment(canonicalId).getString("record_hash")
                yes(
                    redirected.getString("record_hash") == canonicalHash,
                    "EISBAER_CANONICAL_CURRENT_PROOF_PUBLIC",
                )
                val moveCanonical =
                    request("canonical_real_move")
                        .put("appointment_id", canonicalId)
                        .put("expected_record_hash", canonicalHash)
                        .put("updates", JSONObject().put("date", "2027-05-20"))
                call("update_appointment", moveCanonical)
                yes(
                    call("update_appointment", moveCanonical).getBoolean("replayed"),
                    "EISBAER_CANONICAL_MOVE_REPLAY",
                )
                rejected(
                    "update_appointment",
                    JSONObject(moveCanonical.toString())
                        .put("request_id", "qa_eisbaer_mcp_canonical_stale"),
                    "WRITE_CONFLICT",
                )
                val moved = appointment(canonicalId)
                yes(
                    moved.getJSONObject("appointment").getString("date") == "2027-05-20",
                    "EISBAER_CANONICAL_MOVE_REREAD",
                )
                yes(
                    call("get_visit", JSONObject().put("visit_id", visitId))
                        .getJSONObject("record")
                        .getString("record_hash") == linked.getString("record_hash"),
                    "EISBAER_CANONICAL_ALIAS_ORIGINAL_UNCHANGED",
                )
                yes(
                    sourceCount(board("2027-05-19").getJSONArray("items"), canonicalId) == 0,
                    "EISBAER_CANONICAL_OLD_DAY_EMPTY",
                )
                yes(
                    sourceCount(board("2027-05-20").getJSONArray("items"), canonicalId) == 1,
                    "EISBAER_CANONICAL_BOARD_ONCE",
                )
                val today = call("get_revier_overview", JSONObject().put("date", "2027-05-20"))
                val tour = call("get_day_tour", JSONObject().put("date", "2027-05-20"))
                yes(
                    sourceCount(today.getJSONArray("planned_visits"), canonicalId) == 1 &&
                        sourceCount(tour.getJSONArray("stops"), canonicalId) == 1,
                    "EISBAER_CANONICAL_TODAY_TOUR_SAME_SOURCE",
                )
                yes(
                    tour.getJSONArray("stops").getJSONObject(0).getString("source_collection") ==
                        "appointments",
                    "EISBAER_CANONICAL_SOURCE_COLLECTION",
                )

                phase = "eisbaer_mcp_removed_care"
                val removed =
                    listOf(
                        "list_care_plans" to JSONObject(),
                        "create_care_plan" to
                            request("care_create")
                                .put("property_id", propertyId)
                                .put("title", "QA entfernte Pflege")
                                .put("anchor_date", "2027-05-21")
                                .put("interval_days", 7)
                                .put("planned_minutes", 30),
                        "update_care_plan" to
                            request("care_update")
                                .put("care_plan_id", "qa_eisbaer_old_care")
                                .put("expected_record_hash", "a".repeat(64))
                                .put("scope", "this_and_future")
                                .put("effective_from", "2027-05-21")
                                .put("updates", JSONObject().put("title", "QA keine Änderung")),
                        "generate_care_visits" to
                            request("care_generate")
                                .put("from_date", "2027-05-21")
                                .put("to_date", "2027-05-28"),
                    )
                val beforeCare = ownerFingerprint()
                for ((tool, args) in removed) repeat(2) { rejected(tool, args, "FUNCTION_REMOVED") }
                yes(ownerFingerprint() == beforeCare, "EISBAER_ALL_REMOVED_CARE_NO_MUTATION")

                phase = "eisbaer_mcp_adopt_care_visit"
                val legacyId =
                    call(
                            "plan_visit",
                            request("legacy_visit")
                                .put("property_id", propertyId)
                                .put("date", "2027-05-21")
                                .put("title", "QA Eisbär historischer Pflegebesuch")
                                .put("planned_minutes", 45),
                        )
                        .getString("visit_id")
                val legacyKey = RevierRules.ROOT + "entity:visit:" + legacyId
                val oldRule =
                    JSONObject()
                        .put("id", "qa_eisbaer_old_care")
                        .put("kind", "care_plan")
                        .put("format_version", 1)
                        .put("customer_id", "qa_customer_1")
                        .put("property_id", propertyId)
                        .put("title", "QA historische Pflege")
                        .put("anchor_date", "2027-05-21")
                        .put("interval_days", 7)
                        .put("planned_minutes", 45)
                        .put("active", false)
                        .put("created_at", "2026-10-01T09:00:00Z")
                        .put("updated_at", "2026-10-01T09:00:00Z")
                val historicalRule =
                    JSONObject()
                        .put("anchor_date", "2027-05-21")
                        .put("interval_days", 7)
                        .put("planned_minutes", 45)
                        .put("season_start", "")
                        .put("season_end", "")
                        .put("material_needs", JSONArray())
                        .put("equipment_ids", JSONArray())
                oldRule
                    .put("rule_version", 1)
                    .put(
                        "rule_versions",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("version", 1)
                                    .put("effective_from", "2027-05-21")
                                    .put("rule", historicalRule)
                            ),
                    )
                val oldRuleKey = RevierRules.ROOT + "entity:care_plan:" + oldRule.getString("id")
                store.putOwnerValue(oldRuleKey, oldRule.toString())
                store.putOwnerValue(
                    legacyKey,
                    JSONObject(store.ownerValue(legacyKey, "{}"))
                        .put("care_plan_id", oldRule.getString("id"))
                        .put("occurrence_key", "2027-05-21")
                        .put("rule_version", 1)
                        .put("manual_override", false)
                        .put("planning_adopted", false)
                        .toString(),
                )
                store.putOwnerValue(
                    RevierRules.ROOT + "occurrence:" + oldRule.getString("id") + ":2027-05-21",
                    legacyId,
                )
                val beforeAdoption =
                    call("get_visit", JSONObject().put("visit_id", legacyId))
                        .getJSONObject("record")
                yes(
                    board("2027-05-21").getInt("total") == 0 &&
                        call("get_day_tour", JSONObject().put("date", "2027-05-21"))
                            .getJSONArray("stops")
                            .length() == 0,
                    "EISBAER_LEGACY_NOT_AUTOMATICALLY_ADOPTED",
                )
                val adopt =
                    request("legacy_adopt")
                        .put("visit_id", legacyId)
                        .put("expected_record_hash", beforeAdoption.getString("record_hash"))
                        .put("updates", JSONObject().put("planning_adopted", true))
                        .put("scope", "only_this")
                        .put("reason", "QA bewusst benötigten Termin übernehmen")
                call("update_visit", adopt)
                yes(call("update_visit", adopt).getBoolean("replayed"), "EISBAER_ADOPTION_REPLAY")
                rejected(
                    "update_visit",
                    JSONObject(adopt.toString())
                        .put("request_id", "qa_eisbaer_mcp_legacy_adopt_stale"),
                    "WRITE_CONFLICT",
                )
                val adopted =
                    call("get_visit", JSONObject().put("visit_id", legacyId))
                        .getJSONObject("record")
                yes(
                    adopted.getBoolean("planning_adopted") &&
                        adopted.getString("id") == legacyId &&
                        adopted.getString("care_plan_id") == oldRule.getString("id"),
                    "EISBAER_ADOPTION_SAME_ORIGINAL",
                )
                yes(
                    store.ownerValue(oldRuleKey, "") == oldRule.toString(),
                    "EISBAER_ADOPTION_OLD_RULE_UNCHANGED",
                )
                yes(
                    sourceCount(board("2027-05-21").getJSONArray("items"), legacyId) == 1 &&
                        sourceCount(
                            call("get_revier_overview", JSONObject().put("date", "2027-05-21"))
                                .getJSONArray("planned_visits"),
                            legacyId,
                        ) == 1 &&
                        sourceCount(
                            call("get_day_tour", JSONObject().put("date", "2027-05-21"))
                                .getJSONArray("stops"),
                            legacyId,
                        ) == 1,
                    "EISBAER_ADOPTION_PUBLIC_SHARED_PROJECTION",
                )

                phase = "eisbaer_mcp_search_filters"
                val lcrRecord =
                    call(
                            "get_lcr_work_records",
                            JSONObject().put("work_record_id", "qa_mcp_search_lcr"),
                        )
                        .getJSONArray("records")
                        .getJSONObject(0)
                call(
                    "set_work_record_lcr",
                    request("search_lcr")
                        .put("work_record_id", "qa_mcp_search_lcr")
                        .put("expected_record_hash", lcrRecord.getString("lcr_record_hash"))
                        .put("lcr", true),
                )
                val search =
                    JSONObject()
                        .put("query", "Eisbär Sucharbeit")
                        .put("types", JSONArray().put("activity"))
                        .put("customer_id", "qa_customer_1")
                        .put("billing_customer_id", "qa_mcp_recipient")
                        .put("lcr", true)
                        .put("date_basis", "work")
                        .put("from_date", "2026-10-04")
                        .put("to_date", "2026-10-04")
                        .put("amount_from", "12,34")
                        .put("amount_to", "12,34")
                val searchBefore = ownerFingerprint()
                val found = call("search_local", search)
                yes(found.getInt("total") == 1, "EISBAER_SEARCH_COMBINED_FILTERS")
                val hit = found.getJSONArray("items").getJSONObject(0)
                yes(
                    hit.getString("id") == "qa_mcp_search_lcr" &&
                        hit.getBoolean("lcr") &&
                        hit.getString("billing_customer_id") == "qa_mcp_recipient",
                    "EISBAER_SEARCH_ORIGINAL_ID_AND_RELATIONS",
                )
                yes(
                    hit.getString("date_basis") == "work" &&
                        hit.getString("date") == "2026-10-04" &&
                        hit.getString("amount") == "12.34",
                    "EISBAER_SEARCH_DATE_AND_DECIMAL_AMOUNT",
                )
                val reread =
                    call(
                        "get_work_record",
                        JSONObject()
                            .put("work_record_id", hit.getJSONObject("target").getString("id")),
                    )
                val originalWork = reread
                yes(
                    originalWork.getString("id") == "qa_mcp_search_lcr" &&
                        originalWork.getString("accounting_amount") == "12.34",
                    "EISBAER_SEARCH_TARGET_REREADS_ORIGINAL",
                )
                for (negative in
                    listOf(
                        JSONObject(search.toString()).put("customer_id", "qa_mcp_recipient"),
                        JSONObject(search.toString()).put("billing_customer_id", "qa_customer_1"),
                        JSONObject(search.toString()).put("lcr", false),
                        JSONObject(search.toString()).put("date_basis", "created"),
                        JSONObject(search.toString())
                            .put("amount_from", "12.35")
                            .put("amount_to", "99"),
                    )) yes(
                    call("search_local", negative).getInt("total") == 0,
                    "EISBAER_SEARCH_FILTER_EXCLUSION",
                )
                yes(
                    PixelMcpWriteTools.hash(call("search_local", search).getJSONArray("items")) ==
                        PixelMcpWriteTools.hash(found.getJSONArray("items")),
                    "EISBAER_SEARCH_READ_REPEAT",
                )
                yes(ownerFingerprint() == searchBefore, "EISBAER_SEARCH_NO_MUTATION")
                yes(
                    originalRows().length() == 2 && call("list_visits").getInt("total") == 2,
                    "EISBAER_PUBLIC_FLOWS_NO_DUPLICATES",
                )
                phase = "eisbaer_mcp_lcr_delete"
                val keptId = "qa_mcp_search_open"
                val deletedId = "qa_mcp_search_lcr"
                val keptOriginal =
                    call("get_work_record", JSONObject().put("work_record_id", keptId))
                val keptMarker =
                    call("get_lcr_work_records", JSONObject().put("work_record_id", keptId))
                        .getJSONArray("records")
                        .getJSONObject(0)
                call(
                    "set_work_record_lcr",
                    request("delete_keep_marker")
                        .put("work_record_id", keptId)
                        .put("expected_record_hash", keptMarker.getString("lcr_record_hash"))
                        .put("lcr", true),
                )
                val beforeDelete = call("get_lcr_work_records").getJSONArray("records")
                yes(
                    SettlementRules.rows(beforeDelete).map { it.getString("id") }.toSet() ==
                        setOf(deletedId, keptId),
                    "EISBAER_LCR_DELETE_TWO_INDEPENDENT_ORIGINALS",
                )
                val preview =
                    call(
                        "preview_delete",
                        JSONObject().put("entity", "worklog").put("entity_id", deletedId),
                    )
                val deleteArgs =
                    request("delete_lcr_original")
                        .put("entity", "worklog")
                        .put("entity_id", deletedId)
                        .put("expected_record_hash", preview.getString("expected_record_hash"))
                        .put(
                            "expected_photo_links_hash",
                            preview.getString("expected_photo_links_hash"),
                        )
                call("delete_entity", deleteArgs)
                val afterDelete = ownerFingerprint()
                // Permanent deletion intentionally redacts receipts that refer to the removed
                // original. The retained fingerprint rejects repeats instead of recreating it.
                rejected("delete_entity", deleteArgs, "REQUEST_EXPIRED")
                yes(
                    ownerFingerprint() == afterDelete,
                    "EISBAER_LCR_DELETE_REPEAT_PRESERVES_REMOVAL_AND_OTHER_ORIGINALS",
                )
                val remaining = call("get_lcr_work_records").getJSONArray("records")
                yes(
                    remaining.length() == 1 &&
                        remaining.getJSONObject(0).getString("id") == keptId &&
                        remaining.getJSONObject(0).getBoolean("lcr"),
                    "EISBAER_LCR_DELETE_RETAINS_OTHER_MARKER",
                )
                val contextIds = call("get_settlement_context").getJSONArray("lcr_work_ids")
                yes(
                    (0 until contextIds.length()).map(contextIds::getString).toSet() ==
                        setOf(keptId),
                    "EISBAER_LCR_DELETE_REMOVES_STALE_REFERENCE",
                )
                val listedWork = call("list_work_records").getJSONArray("work_records")
                yes(
                    SettlementRules.rows(listedWork).none { it.getString("id") == deletedId } &&
                        listedWork.length() == 2,
                    "EISBAER_LCR_DELETE_ACTUAL_ORIGINAL_REMOVED",
                )
                yes(
                    PixelMcpWriteTools.hash(
                        call("get_work_record", JSONObject().put("work_record_id", keptId))
                    ) == PixelMcpWriteTools.hash(keptOriginal),
                    "EISBAER_LCR_DELETE_KEEPS_OTHER_ORIGINAL_UNCHANGED",
                )
                phase = "eisbaer_mcp_independent_similar_sources"
                val independentDay = "2027-05-24"
                val sameTitle = "QA Eisbär zwei unabhängige Termine"
                val independentAppointmentArgs =
                    request("independent_appointment")
                        .put("customer_id", "qa_customer_1")
                        .put("title", sameTitle)
                        .put("date", independentDay)
                val independentAppointmentId =
                    call("create_appointment", independentAppointmentArgs)
                        .getString("appointment_id")
                val independentVisitArgs =
                    request("independent_visit")
                        .put("property_id", propertyId)
                        .put("title", sameTitle)
                        .put("date", independentDay)
                        .put("planned_minutes", 45)
                val independentVisitId =
                    call("plan_visit", independentVisitArgs).getString("visit_id")
                yes(
                    call("create_appointment", independentAppointmentArgs).getBoolean("replayed") &&
                        call("plan_visit", independentVisitArgs).getBoolean("replayed"),
                    "EISBAER_INDEPENDENT_SIMILAR_SOURCE_CREATES_REPLAY",
                )
                val expectedIndependentSources =
                    setOf(
                        "appointments" to independentAppointmentId,
                        "visits" to independentVisitId,
                    )
                fun exactIndependentSources(rows: JSONArray): Boolean =
                    rows.length() == 2 &&
                        SettlementRules.rows(rows)
                            .map {
                                it.getString("source_collection") to it.getString("source_id")
                            }
                            .toSet() == expectedIndependentSources
                yes(
                    exactIndependentSources(board(independentDay).getJSONArray("items")) &&
                        exactIndependentSources(
                            call("get_revier_overview", JSONObject().put("date", independentDay))
                                .getJSONArray("planned_visits")
                        ) &&
                        exactIndependentSources(
                            call("get_day_tour", JSONObject().put("date", independentDay))
                                .getJSONArray("stops")
                        ),
                    "EISBAER_INDEPENDENT_SAME_TITLE_DATE_CUSTOMER_PRESERVE_TWO_SOURCE_PAIRS",
                )
                val independentAppointment =
                    appointment(independentAppointmentId).getJSONObject("appointment")
                val independentVisit =
                    call("get_visit", JSONObject().put("visit_id", independentVisitId))
                        .getJSONObject("record")
                yes(
                    independentAppointmentId != independentVisitId &&
                        independentAppointment.getString("id") == independentAppointmentId &&
                        independentVisit.getString("id") == independentVisitId &&
                        listOf(independentAppointment, independentVisit).all {
                            it.getString("title") == sameTitle &&
                                it.getString("date") == independentDay &&
                                it.getString("customer_id") == "qa_customer_1"
                        } &&
                        independentAppointment.optString("visit_id").isBlank() &&
                        independentVisit.optString("appointment_id").isBlank() &&
                        originalRows().length() == 3 &&
                        call("list_visits").getInt("total") == 3,
                    "EISBAER_INDEPENDENT_SIMILAR_ORIGINALS_REREAD_WITHOUT_LINK_OR_DUPLICATE",
                )
                return@use JSONObject()
                    .put("executed_tools", JSONArray(executed.toList()))
                    .put("empty_appointment_roundtrip", true)
                    .put("canonical_update_roundtrip", true)
                    .put("removed_care_tools", JSONArray(removed.map { it.first }))
                    .put("legacy_adoption_roundtrip", true)
                    .put("search_filters_original_readback", true)
                    .put("lcr_delete_references", true)
                    .put("independent_similar_sources", true)
                    .put("synthetic_only", true)
                    .put("real_pixel_mcp_adapter", true)
            }
        } finally {
            context.clearPreferences()
            yes(root.deleteRecursively(), "EISBAER_MCP_ISOLATED_ROOT_REMOVED")
        }
    }

    private fun eisbaerMediaBackupMcpProof(owner: Context): JSONObject {
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-media-mcp-$token")
        yes(!root.exists() && root.mkdir(), "MEDIA_MCP_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_media_mcp_$token")
        val executed = linkedSetOf<String>()
        val initialChecks = checks
        try {
            return PixelLocalStore(context).use { store ->
                store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
                val tools =
                    PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
                fun call(name: String, args: JSONObject = JSONObject()): JSONObject {
                    val result = tools.call(name, args)
                    yes(result.optBoolean("ok"), "MEDIA_MCP_OK_$name")
                    executed.add(name)
                    return result.getJSONObject("data")
                }
                fun rejected(name: String, args: JSONObject, code: String) {
                    try {
                        tools.call(name, args)
                    } catch (failure: Exception) {
                        yes(
                            McpErrors.rpc(failure).getJSONObject("error").getString("code") == code,
                            "MEDIA_MCP_REJECT_${name}_$code",
                        )
                        executed.add(name)
                        return
                    }
                    throw AssertionError("QA_NATIVE_DATA_MEDIA_EXPECTED_${name}_$code")
                }
                fun request(id: String) = JSONObject().put("request_id", "qa_media_mcp_$id")
                val bitmap =
                    android.graphics.Bitmap.createBitmap(
                        3,
                        2,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                val bytes =
                    java.io.ByteArrayOutputStream().use { output ->
                        try {
                            bitmap.eraseColor(android.graphics.Color.rgb(25, 115, 160))
                            yes(
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.PNG,
                                    100,
                                    output,
                                ),
                                "MEDIA_MCP_SYNTHETIC_PNG",
                            )
                            output.toByteArray()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                val uploadArgs =
                    request("begin")
                        .put("byte_size", bytes.size)
                        .put("sha256", McpFileTransfer.sha(bytes))
                        .put("mime_type", "image/png")
                val upload = call("begin_file_upload", uploadArgs).getString("upload_id")
                yes(
                    call("begin_file_upload", uploadArgs).getString("upload_id") == upload,
                    "MEDIA_MCP_UPLOAD_REPLAY_ID",
                )
                val chunkArgs =
                    request("chunk")
                        .put("upload_id", upload)
                        .put("offset", 0)
                        .put("chunk_base64", java.util.Base64.getEncoder().encodeToString(bytes))
                        .put("chunk_sha256", McpFileTransfer.sha(bytes))
                yes(
                    call("write_file_chunk", chunkArgs).getBoolean("complete"),
                    "MEDIA_MCP_UPLOAD_COMPLETE",
                )
                yes(
                    call("write_file_chunk", chunkArgs).getBoolean("replayed"),
                    "MEDIA_MCP_CHUNK_REPLAY",
                )
                val photoArgs =
                    request("photo")
                        .put("upload_id", upload)
                        .put("customer_id", "qa_customer_1")
                        .put("caption", "QA MCP Originalfoto")
                val photoId = call("create_photo", photoArgs).getString("photo_id")
                yes(
                    call("create_photo", photoArgs).getString("photo_id") == photoId,
                    "MEDIA_MCP_PHOTO_REPLAY_ID",
                )
                val photos =
                    call("list_photos", JSONObject().put("customer_id", "qa_customer_1"))
                        .getJSONArray("photos")
                yes(
                    photos.length() == 1 && photos.getJSONObject(0).getString("id") == photoId,
                    "MEDIA_MCP_EXACTLY_ONE_ORIGINAL_PHOTO",
                )
                val original = call("get_photo", JSONObject().put("photo_id", photoId))
                val metaArgs =
                    JSONObject().put("resource_type", "photo").put("resource_id", photoId)
                val metadata = call("get_file_metadata", metaArgs)
                yes(
                    metadata.getInt("byte_size") == bytes.size &&
                        metadata.getString("sha256") == McpFileTransfer.sha(bytes),
                    "MEDIA_MCP_ORIGINAL_METADATA",
                )
                val download = java.io.ByteArrayOutputStream()
                var offset = 0
                while (offset < bytes.size) {
                    val part =
                        call(
                            "read_file_chunk",
                            JSONObject(metaArgs.toString())
                                .put("offset", offset)
                                .put("length", 17)
                                .put("expected_sha256", metadata.getString("sha256")),
                        )
                    val decoded =
                        java.util.Base64.getDecoder().decode(part.getString("chunk_base64"))
                    yes(
                        McpFileTransfer.sha(decoded) == part.getString("chunk_sha256") &&
                            part.getInt("next_offset") == offset + decoded.size &&
                            decoded.isNotEmpty(),
                        "MEDIA_MCP_CHUNK_INTEGRITY_$offset",
                    )
                    download.write(decoded)
                    offset = part.getInt("next_offset")
                    yes(part.getBoolean("eof") == (offset == bytes.size), "MEDIA_MCP_EOF_$offset")
                }
                yes(download.toByteArray().contentEquals(bytes), "MEDIA_MCP_BYTE_EXACT_ORIGINAL")
                rejected(
                    "read_file_chunk",
                    JSONObject(metaArgs.toString())
                        .put("offset", 0)
                        .put("expected_sha256", "0".repeat(64)),
                    "FILE_VERSION_CONFLICT",
                )
                val updateArgs =
                    request("caption")
                        .put("photo_id", photoId)
                        .put("expected_record_hash", original.getString("record_hash"))
                        .put("updates", JSONObject().put("caption", "QA MCP Originalfoto ergänzt"))
                call("update_photo", updateArgs)
                yes(
                    call("update_photo", updateArgs).getBoolean("replayed"),
                    "MEDIA_MCP_EDIT_REPLAY",
                )
                rejected(
                    "update_photo",
                    JSONObject(updateArgs.toString())
                        .put("request_id", "qa_media_mcp_caption_stale"),
                    "WRITE_CONFLICT",
                )
                yes(
                    call("get_photo", JSONObject().put("photo_id", photoId))
                        .getJSONObject("photo")
                        .getString("caption") == "QA MCP Originalfoto ergänzt" &&
                        call("get_file_metadata", metaArgs).getString("sha256") ==
                            metadata.getString("sha256"),
                    "MEDIA_MCP_METADATA_EDIT_KEEPS_ORIGINAL_BYTES",
                )

                val backupStatus = call("get_full_backup_status")
                val beforeBackup = PixelMcpWriteTools.hash(store.getState())
                val backupArgs =
                    request("backup_handoff")
                        .put("expected_settings_hash", backupStatus.getString("settings_hash"))
                for (result in
                    listOf(
                        call("request_full_backup", backupArgs),
                        call("request_full_backup", backupArgs),
                        call(
                            "retry_full_backup",
                            request("backup_retry")
                                .put("backup_id", "qa_missing_backup")
                                .put("expected_record_hash", "0".repeat(64)),
                        ),
                    )) {
                    yes(
                        result.getString("code") == "BACKUP_OPEN_DOCUMENT_PICKER" &&
                            !result.getBoolean("accepted_locally") &&
                            result.getBoolean("safe_replacement_required"),
                        "MEDIA_MCP_BACKUP_REQUIRES_EXPLICIT_DEVICE_PICKER",
                    )
                }
                repeat(2) {
                    yes(
                        call("get_restore_status").getString("phase") == "NONE",
                        "MEDIA_MCP_RESTORE_READ_DOES_NOT_ACTIVATE",
                    )
                }
                yes(
                    PixelMcpWriteTools.hash(store.getState()) == beforeBackup,
                    "MEDIA_MCP_HANDOFFS_DO_NOT_CREATE_BACKUP_OR_RESTORE_BUSINESS_MUTATIONS",
                )
                JSONObject()
                    .put("executed_tools", JSONArray(executed.toList()))
                    .put("checks", checks - initialChecks)
                    .put("real_pixel_mcp_adapter", true)
                    .put("original_photo_bytes", true)
                    .put("write_replay_and_stale_conflict", true)
                    .put("backup_device_handoff_only", true)
                    .put("physical_backup_acceptance", false)
                    .put("synthetic_only", true)
            }
        } finally {
            context.clearPreferences()
            yes(root.deleteRecursively(), "MEDIA_MCP_ISOLATED_ROOT_REMOVED")
        }
    }

    private fun eisbaerPlanningTimeMcpProof(owner: Context): JSONObject {
        val beforeChecks = checks
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-planning-time-$token")
        yes(!root.exists() && root.mkdir(), "EISBAER_PLANNING_TIME_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_planning_time_$token")
        val executed = linkedSetOf<String>()
        var proofWall = java.time.Instant.parse("2027-05-19T07:00:00Z").toEpochMilli()
        var proofElapsed = 1_000_000L
        var proofBoot = "qa_eisbaer_mcp_boot"
        val clock =
            object : WorkSessionStore.Clock {
                override fun wallMillis(): Long = proofWall

                override fun elapsedMillis(): Long = proofElapsed

                override fun bootMarker(): String = proofBoot
            }
        val report: JSONObject
        try {
            report =
                PixelLocalStore(context, clock).use { store ->
                    store.putOwnerValue(
                        "snapshot",
                        SyntheticBusinessData.profile("minimal").toString(),
                    )
                    val tools =
                        PixelMcpReadTools(
                            store.readSource(),
                            store,
                            PixelOwnerAccess(context, store),
                        )
                    fun call(
                        name: String,
                        args: JSONObject = JSONObject(),
                        adapter: PixelMcpReadTools = tools,
                    ): JSONObject {
                        executed.add(name)
                        val result = adapter.call(name, args)
                        yes(result.optBoolean("ok"), "EISBAER_PLANNING_TIME_PUBLIC_OK_$name")
                        return result.getJSONObject("data")
                    }
                    fun rejected(name: String, args: JSONObject, code: String): JSONObject {
                        executed.add(name)
                        try {
                            tools.call(name, args)
                        } catch (failure: Exception) {
                            val error = McpErrors.rpc(failure).getJSONObject("error")
                            yes(
                                error.getString("code") == code,
                                "EISBAER_PLANNING_TIME_REJECT_${name}_$code",
                            )
                            return error
                        }
                        throw AssertionError(
                            "QA_NATIVE_DATA_EISBAER_PLANNING_TIME_EXPECTED_${name}_$code"
                        )
                    }
                    fun request(id: String) =
                        JSONObject().put("request_id", "qa_eisbaer_planning_time_$id")
                    fun originalState() =
                        PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
                    fun appointment(id: String) =
                        call("get_appointment", JSONObject().put("appointment_id", id))
                    val zone = java.time.ZoneId.of("Europe/Vienna")
                    val day = java.time.LocalDate.now(zone).plusDays(1)

                    phase = "eisbaer_mcp_planning_settings"
                    val initialSettings = call("get_planning_settings")
                    yes(
                        !initialSettings
                            .getJSONObject("integration")
                            .optBoolean("calendar_connected"),
                        "EISBAER_PLANNING_NO_EXTERNAL_ACCOUNT",
                    )
                    val settingsArgs =
                        request("settings")
                            .put("expected_record_hash", initialSettings.getString("record_hash"))
                            .put(
                                "updates",
                                JSONObject()
                                    .put("day_start", "07:30")
                                    .put("day_end", "16:30")
                                    .put("workdays", JSONArray(listOf(1, 2, 3, 4, 5, 6, 7)))
                                    .put("calendar_enabled", false)
                                    .put("weather_enabled", false)
                                    .put("notifications", false),
                            )
                    call("update_planning_settings", settingsArgs)
                    yes(
                        call("update_planning_settings", settingsArgs).getBoolean("replayed"),
                        "EISBAER_PLANNING_SETTINGS_REPLAY",
                    )
                    val settings = call("get_planning_settings")
                    yes(
                        settings.getJSONObject("settings").getString("day_start") == "07:30" &&
                            settings.getJSONObject("settings").getString("day_end") == "16:30",
                        "EISBAER_PLANNING_SETTINGS_REREAD",
                    )
                    rejected(
                        "update_planning_settings",
                        JSONObject(settingsArgs.toString())
                            .put("request_id", "qa_eisbaer_planning_time_settings_stale"),
                        "WRITE_CONFLICT",
                    )
                    rejected(
                        "update_planning_settings",
                        request("calendar_permission")
                            .put("expected_record_hash", settings.getString("record_hash"))
                            .put("updates", JSONObject().put("calendar_enabled", true)),
                        "INVALID_INPUT",
                    )
                    yes(
                        call("get_planning_settings").getString("record_hash") ==
                            settings.getString("record_hash"),
                        "EISBAER_PLANNING_PERMISSION_REJECTION_PRESERVES_SETTINGS",
                    )
                    val beforeConfigurationRequest = originalState()
                    for (tool in listOf("request_calendar_sync", "request_weather_refresh")) {
                        val args = request(tool)
                        val gated = call(tool, args)
                        yes(
                            gated.getString("state") == "configuration_required" &&
                                !gated.getBoolean("confirmed"),
                            "EISBAER_PLANNING_CONFIGURATION_GATE_$tool",
                        )
                        yes(
                            call(tool, args).getBoolean("replayed"),
                            "EISBAER_PLANNING_CONFIGURATION_REPLAY_$tool",
                        )
                    }
                    yes(
                        originalState() == beforeConfigurationRequest,
                        "EISBAER_CONFIGURATION_GATE_CREATES_NO_BUSINESS_DATA",
                    )

                    phase = "eisbaer_mcp_planning_series"
                    val weatherId =
                        call(
                                "create_appointment",
                                request("weather_appointment")
                                    .put("customer_id", "qa_customer_1")
                                    .put("title", "[TEST] QA synthetischer Wettertermin")
                                    .put(
                                        "starts_at",
                                        day.atTime(10, 0).atZone(zone).toInstant().toString(),
                                    )
                                    .put(
                                        "ends_at",
                                        day.atTime(11, 0).atZone(zone).toInstant().toString(),
                                    )
                                    .put("timezone", zone.id)
                                    .put("location", "[TEST] QA eindeutiger Testort")
                                    .put("latitude", 48.201)
                                    .put("longitude", 16.301)
                                    .put("weather_profile", "general"),
                            )
                            .getString("appointment_id")
                    val seriesDay = day.plusDays(2)
                    val seriesArgs =
                        request("series")
                            .put("customer_id", "qa_customer_1")
                            .put("title", "[TEST] QA ausdrückliche Wiederholung")
                            .put(
                                "starts_at",
                                seriesDay.atTime(14, 0).atZone(zone).toInstant().toString(),
                            )
                            .put(
                                "ends_at",
                                seriesDay.atTime(15, 0).atZone(zone).toInstant().toString(),
                            )
                            .put("timezone", zone.id)
                            .put("occurrences", 2)
                            .put("interval_weeks", 1)
                    val series =
                        call("create_appointment_series", seriesArgs)
                            .getJSONArray("appointment_ids")
                    yes(
                        series.length() == 2 && series.getString(0) != series.getString(1),
                        "EISBAER_SERIES_TWO_ORIGINAL_IDS",
                    )
                    yes(
                        call("create_appointment_series", seriesArgs).getBoolean("replayed"),
                        "EISBAER_SERIES_REPLAY",
                    )
                    val seriesFirst = appointment(series.getString(0)).getJSONObject("appointment")
                    val seriesNext = appointment(series.getString(1)).getJSONObject("appointment")
                    yes(
                        seriesFirst.getString("date") == seriesDay.toString() &&
                            seriesNext.getString("date") == seriesDay.plusWeeks(1).toString() &&
                            seriesFirst.getString("series_id") == seriesNext.getString("series_id"),
                        "EISBAER_SERIES_DATES_AND_SHARED_RELATION_REREAD",
                    )
                    val filter =
                        JSONObject()
                            .put("customer_id", "qa_customer_1")
                            .put("date_from", day.toString())
                            .put("date_to", seriesDay.plusWeeks(1).toString())
                    val listed = call("list_appointments", filter)
                    yes(
                        listed.getInt("total") == 3 &&
                            SettlementRules.rows(listed.getJSONArray("appointments"))
                                .map { it.getJSONObject("appointment").getString("id") }
                                .toSet() ==
                                setOf(weatherId, series.getString(0), series.getString(1)),
                        "EISBAER_SERIES_LIST_HAS_ORIGINALS_ONCE",
                    )
                    val contextRead = call("get_planning_context", filter)
                    yes(
                        contextRead.getJSONObject("customer").getString("id") == "qa_customer_1" &&
                            contextRead.getJSONArray("appointments").length() == 3,
                        "EISBAER_PLANNING_CONTEXT_PUBLIC_RELATIONS",
                    )
                    val beforeSuggestion = originalState()
                    val suggested =
                        call(
                            "suggest_appointment_slots",
                            JSONObject()
                                .put("customer_id", "qa_customer_1")
                                .put("duration_minutes", 60)
                                .put("date_from", day.toString())
                                .put("date_to", day.plusDays(1).toString()),
                        )
                    yes(
                        !suggested.getBoolean("booked") &&
                            !suggested.getBoolean("calendar_availability_verified") &&
                            suggested.getJSONArray("suggestions").length() > 0,
                        "EISBAER_SUGGESTIONS_NOT_BOOKINGS_OR_EXTERNAL_CONFIRMATION",
                    )
                    rejected(
                        "suggest_appointment_slots",
                        JSONObject()
                            .put("customer_id", "qa_customer_1")
                            .put("duration_minutes", 0)
                            .put("date_from", day.toString())
                            .put("date_to", day.toString()),
                        "INVALID_INPUT",
                    )
                    yes(originalState() == beforeSuggestion, "EISBAER_SUGGESTIONS_READ_ONLY")

                    phase = "eisbaer_mcp_synthetic_weather"
                    val hourly =
                        JSONObject()
                            .put(
                                "time",
                                JSONArray(
                                    (0..25).map {
                                        day.atStartOfDay(zone).toEpochSecond() + it * 3600L
                                    }
                                ),
                            )
                            .put("temperature_2m", JSONArray((0..25).map { 15 }))
                            .put("precipitation", JSONArray((0..25).map { 8 }))
                            .put("wind_gusts_10m", JSONArray((0..25).map { 90 }))
                            .put("precipitation_probability", JSONArray((0..25).map { 90 }))
                            .put("weather_code", JSONArray((0..25).map { 95 }))
                    val weatherCache =
                        JSONObject()
                            .put("fetched_at", java.time.Instant.now().toString())
                            .put("forecast", JSONObject().put("hourly", hourly))
                    val weatherOriginal = appointment(weatherId)
                    val weatherHash = weatherOriginal.getString("record_hash")
                    store.putOwnerValue(
                        "weather:" +
                            WeatherRiskRules.locationKey(
                                weatherOriginal.getJSONObject("appointment")
                            ),
                        weatherCache.toString(),
                    )
                    val weatherFilter =
                        JSONObject().put("date_from", day.toString()).put("date_to", day.toString())
                    val risks = call("get_weather_risks", weatherFilter)
                    val weather =
                        risks.getJSONArray("appointments").getJSONObject(0).getJSONObject("weather")
                    yes(
                        weather.getString("severity") == "critical" &&
                            weather.getString("alert_hash").isNotBlank(),
                        "EISBAER_PUBLIC_WEATHER_READS_SYNTHETIC_CACHE",
                    )
                    val weekWeather = call("get_workweek_weather", weatherFilter)
                    yes(
                        weekWeather.getJSONArray("appointments").length() == 1 &&
                            weekWeather.getJSONArray("next_workdays").length() == 5 &&
                            weekWeather.getInt("requested_forecast_days") ==
                                OpenMeteoWeatherSource.FORECAST_DAYS,
                        "EISBAER_WORKWEEK_WEATHER_PUBLIC_REQUEST_COVERAGE",
                    )
                    val acknowledge =
                        request("ack_weather")
                            .put("appointment_id", weatherId)
                            .put("expected_alert_hash", weather.getString("alert_hash"))
                    val acknowledged = call("acknowledge_weather_alert", acknowledge)
                    yes(
                        acknowledged.getBoolean("acknowledged") &&
                            !acknowledged.getBoolean("appointment_changed"),
                        "EISBAER_WEATHER_ACK_NO_RESCHEDULING",
                    )
                    yes(
                        call("acknowledge_weather_alert", acknowledge).getBoolean("replayed"),
                        "EISBAER_WEATHER_ACK_REPLAY",
                    )
                    val rereadRisks =
                        call("get_weather_risks", weatherFilter).getJSONArray("weather_risks")
                    yes(
                        SettlementRules.rows(rereadRisks)
                            .single { it.getString("appointment_id") == weatherId }
                            .getBoolean("acknowledged"),
                        "EISBAER_WEATHER_ACK_PUBLIC_REREAD",
                    )
                    rejected(
                        "acknowledge_weather_alert",
                        request("ack_stale")
                            .put("appointment_id", weatherId)
                            .put("expected_alert_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    yes(
                        appointment(weatherId).getString("record_hash") == weatherHash,
                        "EISBAER_WEATHER_ACK_PRESERVES_ORIGINAL",
                    )

                    phase = "eisbaer_mcp_calendar_local_conflict"
                    val bindingKey = AppointmentRuntime.bindingKey(context, weatherId)
                    val conflict =
                        JSONObject()
                            .put("base", weatherOriginal.getJSONObject("appointment"))
                            .put("state", "review_required")
                            .put(
                                "conflict",
                                JSONObject()
                                    .put("code", "[TEST] QA local calendar conflict")
                                    .put("remote", JSONObject.NULL),
                            )
                    store.putOwnerValue(bindingKey, conflict.toString())
                    val calendarRead = appointment(weatherId)
                    val conflictHash =
                        calendarRead.getJSONObject("calendar").getString("conflict_hash")
                    yes(
                        conflictHash == PixelMcpWriteTools.hash(conflict),
                        "EISBAER_CALENDAR_CONFLICT_CURRENT_PROOF_PUBLIC",
                    )
                    val resolve =
                        request("resolve_local_calendar")
                            .put("appointment_id", weatherId)
                            .put("expected_record_hash", calendarRead.getString("record_hash"))
                            .put("expected_conflict_hash", conflictHash)
                            .put("confirmed", true)
                            .put("resolution", "keep_without_calendar")
                    rejected(
                        "resolve_calendar_conflict",
                        JSONObject(resolve.toString())
                            .put("request_id", "qa_eisbaer_planning_time_resolve_stale_original")
                            .put("expected_record_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    rejected(
                        "resolve_calendar_conflict",
                        JSONObject(resolve.toString())
                            .put("request_id", "qa_eisbaer_planning_time_resolve_stale_conflict")
                            .put("expected_conflict_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    val resolved = call("resolve_calendar_conflict", resolve)
                    yes(
                        resolved.getString("state") == "detached" &&
                            !resolved.getBoolean("business_data_changed") &&
                            !resolved.getBoolean("google_confirmed"),
                        "EISBAER_CALENDAR_LOCAL_DETACH_NO_REMOTE_SUCCESS_CLAIM",
                    )
                    yes(
                        call("resolve_calendar_conflict", resolve).getBoolean("replayed"),
                        "EISBAER_CALENDAR_CONFLICT_REPLAY",
                    )
                    val detached = JSONObject(store.ownerValue(bindingKey, "{}"))
                    yes(
                        detached.getBoolean("detached") &&
                            !detached.has("conflict") &&
                            appointment(weatherId).getString("record_hash") == weatherHash,
                        "EISBAER_CALENDAR_CONFLICT_LOCAL_REREAD_PRESERVES_ORIGINAL",
                    )
                    rejected(
                        "resolve_calendar_conflict",
                        JSONObject(resolve.toString())
                            .put("request_id", "qa_eisbaer_planning_time_resolve_after_detach"),
                        "WRITE_CONFLICT",
                    )
                    val cancelProof = appointment(series.getString(0))
                    val cancel =
                        request("cancel_series_original")
                            .put("appointment_id", series.getString(0))
                            .put("expected_record_hash", cancelProof.getString("record_hash"))
                    call("cancel_appointment", cancel)
                    yes(
                        call("cancel_appointment", cancel).getBoolean("replayed"),
                        "EISBAER_CANCEL_APPOINTMENT_REPLAY",
                    )
                    yes(
                        appointment(series.getString(0))
                            .getJSONObject("appointment")
                            .getString("status") == "cancelled" &&
                            call("list_appointments", filter).getInt("total") == 3,
                        "EISBAER_CANCEL_PRESERVES_ORIGINAL_AND_SERIES_HISTORY",
                    )
                    rejected(
                        "cancel_appointment",
                        JSONObject(cancel.toString())
                            .put("request_id", "qa_eisbaer_planning_time_cancel_stale"),
                        "WRITE_CONFLICT",
                    )

                    // The real timer receives a controllable monotonic clock; no sleeping and no
                    // direct session-table edits are needed for actual public write/read paths.
                    phase = "eisbaer_mcp_time_roundtrip"
                    fun advanceTime(milliseconds: Long) {
                        proofWall += milliseconds
                        proofElapsed += milliseconds
                    }
                    fun currentTime() = call("get_active_work_session")
                    fun timeWorks() =
                        SettlementRules.rows(
                            call("list_work_records", JSONObject().put("limit", 100))
                                .getJSONArray("work_records")
                        )
                    val timeBeforeIds = timeWorks().map { it.getString("id") }.toSet()
                    yes(currentTime().getString("state") == "IDLE", "EISBAER_TIME_FRESH_IDLE")
                    val firstStartArgs =
                        request("time_start_a")
                            .put("customer_id", "qa_customer_1")
                            .put("work_date", "2027-05-19")
                            .put("activity", "[TEST] QA MCP Timer A")
                    val firstStart = call("start_work", firstStartArgs).getJSONObject("session")
                    val firstSessionId = firstStart.getString("id")
                    yes(
                        firstStart.getString("state") == "RUNNING" &&
                            call("start_work", firstStartArgs).getBoolean("replayed"),
                        "EISBAER_TIME_START_A_REPLAY",
                    )
                    advanceTime(120_000L)
                    val firstStopArgs = request("time_stop_a").put("session_id", firstSessionId)
                    val firstStopped = call("stop_work", firstStopArgs).getJSONObject("session")
                    yes(
                        firstStopped.getString("state") == "STOPPED_PENDING_REVIEW" &&
                            firstStopped.getLong("worked_ms") == 120_000L,
                        "EISBAER_TIME_STOP_A_ACTUAL_DURATION",
                    )
                    yes(
                        call("stop_work", firstStopArgs).getBoolean("replayed") &&
                            timeWorks().map { it.getString("id") }.toSet() == timeBeforeIds,
                        "EISBAER_TIME_STOP_REPLAY_WITHOUT_WORK",
                    )
                    val continueArgs = request("time_continue_a").put("session_id", firstSessionId)
                    val continued = call("continue_work", continueArgs).getJSONObject("session")
                    yes(
                        continued.getString("id") == firstSessionId &&
                            continued.getString("state") == "RUNNING",
                        "EISBAER_TIME_CONTINUE_SAME_SESSION",
                    )
                    yes(
                        call("continue_work", continueArgs).getBoolean("replayed"),
                        "EISBAER_TIME_CONTINUE_REPLAY",
                    )
                    advanceTime(240_000L)
                    val stoppedAgain =
                        call(
                                "stop_work",
                                request("time_stop_a_second").put("session_id", firstSessionId),
                            )
                            .getJSONObject("session")
                    yes(
                        stoppedAgain.getLong("worked_ms") == 360_000L &&
                            currentTime().getString("state") == "STOPPED_PENDING_REVIEW",
                        "EISBAER_TIME_CONTINUE_ACCUMULATES_ONCE",
                    )
                    val completeArgs =
                        request("time_complete_a")
                            .put("session_id", firstSessionId)
                            .put("activity", "[TEST] QA MCP Timer A abgeschlossen")
                    val completed =
                        call("complete_work_session", completeArgs).getJSONObject("session")
                    val firstWorkId = completed.getString("finalized_worklog_id")
                    yes(
                        completed.getString("state") == "FINALIZED" && firstWorkId.isNotBlank(),
                        "EISBAER_TIME_COMPLETE_A_FINALIZED",
                    )
                    yes(
                        call("complete_work_session", completeArgs).getBoolean("replayed"),
                        "EISBAER_TIME_COMPLETE_REPLAY",
                    )
                    val firstWork =
                        call("get_work_record", JSONObject().put("work_record_id", firstWorkId))
                    yes(
                        firstWork.getString("id") == firstWorkId &&
                            firstWork.getString("customer_id") == "qa_customer_1" &&
                            kotlin.math.abs(firstWork.getDouble("hours") - 0.1) < 0.0000001 &&
                            firstWork.getInt("break_minutes") == 0,
                        "EISBAER_TIME_COMPLETE_ORIGINAL_REREAD",
                    )
                    yes(
                        currentTime().getString("state") == "IDLE" &&
                            timeWorks().map { it.getString("id") }.toSet() ==
                                timeBeforeIds + firstWorkId,
                        "EISBAER_TIME_COMPLETE_ONE_WORK_AND_IDLE",
                    )
                    rejected(
                        "continue_work",
                        request("time_continue_finalized").put("session_id", firstSessionId),
                        "SESSION_CONFLICT",
                    )

                    val secondStart =
                        call(
                                "start_work",
                                request("time_start_b")
                                    .put("customer_id", "qa_customer_1")
                                    .put("work_date", "2027-05-19")
                                    .put("activity", "[TEST] QA MCP Timer B"),
                            )
                            .getJSONObject("session")
                    val secondSessionId = secondStart.getString("id")
                    advanceTime(60_000L)
                    val discardArgs = request("time_discard_b").put("session_id", secondSessionId)
                    val discarded =
                        call("discard_work_session", discardArgs).getJSONObject("session")
                    yes(
                        discarded.getString("state") == "CANCELLED" &&
                            discarded.getLong("worked_ms") == 60_000L &&
                            currentTime().getString("state") == "IDLE",
                        "EISBAER_TIME_DISCARD_WITHOUT_WORK",
                    )
                    val restoreArgs = request("time_restore_b").put("session_id", secondSessionId)
                    val restored =
                        call("restore_work_session", restoreArgs).getJSONObject("session")
                    yes(
                        restored.getString("id") == secondSessionId &&
                            restored.getString("state") == "PAUSED" &&
                            restored.getLong("worked_ms") == 60_000L,
                        "EISBAER_TIME_RESTORE_SAME_SESSION_PAUSED",
                    )
                    yes(
                        call("restore_work_session", restoreArgs).getBoolean("replayed"),
                        "EISBAER_TIME_RESTORE_REPLAY",
                    )
                    val restoredRevision = currentTime().getString("revision_hash")
                    rejected(
                        "restore_work_session",
                        request("time_restore_conflict").put("session_id", secondSessionId),
                        "SESSION_CONFLICT",
                    )
                    yes(
                        currentTime().getString("revision_hash") == restoredRevision,
                        "EISBAER_TIME_RESTORE_CONFLICT_PRESERVES_REVISION",
                    )
                    call("end_break", request("time_resume_b").put("session_id", secondSessionId))
                    advanceTime(120_000L)
                    proofBoot = "qa_eisbaer_mcp_boot_after_restart"
                    val uncertain = currentTime()
                    yes(
                        uncertain.getBoolean("review_required") &&
                            uncertain.getString("review_reason") == "DEVICE_RESTART",
                        "EISBAER_TIME_BOOT_CHANGE_REQUIRES_REVIEW",
                    )
                    val reviewArgs =
                        request("time_review_b")
                            .put("session_id", secondSessionId)
                            .put("expected_revision_hash", uncertain.getString("revision_hash"))
                            .put("worked_minutes", 15)
                            .put("break_minutes", 2)
                            .put(
                                "reason",
                                "[TEST] QA bewusst geprüfte Nettozeit nach simuliertem Neustart",
                            )
                            .put("confirmed", true)
                    rejected(
                        "review_work_session",
                        JSONObject(reviewArgs.toString())
                            .put("request_id", "qa_eisbaer_mcp_time_review_stale_before")
                            .put("expected_revision_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    val reviewed = call("review_work_session", reviewArgs).getJSONObject("session")
                    yes(
                        reviewed.getString("state") == "STOPPED_PENDING_REVIEW" &&
                            reviewed.getLong("worked_ms") == 900_000L &&
                            reviewed.getInt("break_minutes") == 2 &&
                            !reviewed.getBoolean("review_required") &&
                            reviewed.getString("time_origin") == "manual_review",
                        "EISBAER_TIME_REVIEW_CONFIRMED_VALUES_REREAD",
                    )
                    yes(
                        call("review_work_session", reviewArgs).getBoolean("replayed"),
                        "EISBAER_TIME_REVIEW_REPLAY",
                    )
                    rejected(
                        "review_work_session",
                        JSONObject(reviewArgs.toString()).put("worked_minutes", 16),
                        "REPLAY_MISMATCH",
                    )
                    rejected(
                        "review_work_session",
                        JSONObject(reviewArgs.toString())
                            .put("request_id", "qa_eisbaer_mcp_time_review_stale_after"),
                        "WRITE_CONFLICT",
                    )
                    yes(
                        currentTime().getString("revision_hash") ==
                            reviewed.getString("revision_hash") &&
                            timeWorks().map { it.getString("id") }.toSet() ==
                                timeBeforeIds + firstWorkId,
                        "EISBAER_TIME_REVIEW_ERRORS_NO_WORK_OR_REVISION_CHANGE",
                    )
                    call(
                        "continue_work",
                        request("time_continue_reviewed_b").put("session_id", secondSessionId),
                    )
                    advanceTime(180_000L)
                    val runningBeforeFinish = currentTime()
                    yes(
                        runningBeforeFinish.getString("state") == "RUNNING" &&
                            runningBeforeFinish.getLong("worked_ms") == 1_080_000L,
                        "EISBAER_TIME_REVIEWED_SESSION_CONTINUES_FROM_CONFIRMED_NET",
                    )
                    rejected(
                        "finish_work",
                        request("time_finish_wrong_customer")
                            .put("session_id", secondSessionId)
                            .put("customer_id", "qa_wrong_customer")
                            .put("activity", "[TEST] QA kein falscher Abschluss"),
                        "REFERENCE_MISMATCH",
                    )
                    yes(
                        currentTime().getString("state") == "RUNNING" &&
                            currentTime().getString("revision_hash") ==
                                runningBeforeFinish.getString("revision_hash") &&
                            timeWorks().map { it.getString("id") }.toSet() ==
                                timeBeforeIds + firstWorkId,
                        "EISBAER_TIME_FINISH_VALIDATION_ROLLS_BACK_ATOMIC_STOP",
                    )
                    val finishArgs =
                        request("time_finish_b")
                            .put("session_id", secondSessionId)
                            .put("activity", "[TEST] QA MCP Timer B atomar abgeschlossen")
                    val finished = call("finish_work", finishArgs).getJSONObject("session")
                    val secondWorkId = finished.getString("finalized_worklog_id")
                    yes(
                        finished.getString("state") == "FINALIZED" &&
                            secondWorkId.isNotBlank() &&
                            secondWorkId != firstWorkId,
                        "EISBAER_TIME_FINISH_FROM_RUNNING_FINALIZES_ONCE",
                    )
                    yes(
                        call("finish_work", finishArgs).getBoolean("replayed"),
                        "EISBAER_TIME_FINISH_REPLAY",
                    )
                    val secondWork =
                        call("get_work_record", JSONObject().put("work_record_id", secondWorkId))
                    yes(
                        secondWork.getString("id") == secondWorkId &&
                            secondWork.getString("customer_id") == "qa_customer_1" &&
                            kotlin.math.abs(secondWork.getDouble("hours") - 0.3) < 0.0000001 &&
                            secondWork.getInt("break_minutes") == 2,
                        "EISBAER_TIME_FINISH_ORIGINAL_REREAD",
                    )
                    rejected(
                        "stop_work",
                        request("time_stop_finalized").put("session_id", secondSessionId),
                        "SESSION_CONFLICT",
                    )
                    yes(
                        currentTime().getString("state") == "IDLE" &&
                            timeWorks().map { it.getString("id") }.toSet() ==
                                timeBeforeIds + firstWorkId + secondWorkId,
                        "EISBAER_TIME_ALL_PATHS_TWO_ORIGINALS_AND_NO_LIVE_TIMER",
                    )

                    phase = "eisbaer_mcp_configured_planning_requests"
                    // Public configured requests, with the external job boundary unavailable.
                    // Settings, receipts, preferences and business data still use real SQLite/API.
                    var suppressedJobs = 0
                    val requestContext =
                        object : ContextWrapper(context) {
                            override fun getApplicationContext(): Context = this

                            override fun getSystemService(name: String): Any? =
                                if (name == Context.JOB_SCHEDULER_SERVICE) {
                                    suppressedJobs++
                                    null
                                } else super.getSystemService(name)
                        }
                    fun configuredRequestTools(value: PixelLocalStore): PixelMcpReadTools {
                        val access = PixelOwnerAccess(requestContext, value)
                        val writer =
                            object : PixelMcpWriteTools.Sink {
                                override fun writable(): Boolean = value.writable()

                                override fun execute(tool: String, args: JSONObject): JSONObject =
                                    access.execute(tool, args)

                                override fun status(id: String, state: JSONObject): JSONObject =
                                    value.status(id, state)
                            }
                        return PixelMcpReadTools(value.readSource(), writer, access)
                    }
                    val requestTools = configuredRequestTools(store)
                    fun requestTimeProof(database: SQLiteDatabase): String {
                        val rows = JSONObject()
                        for (table in listOf("work_sessions", "work_session_events")) {
                            val values = JSONArray()
                            database.rawQuery("SELECT * FROM $table ORDER BY id", null).use { cursor
                                ->
                                while (cursor.moveToNext()) {
                                    val row = JSONObject()
                                    for (column in 0 until cursor.columnCount) row.put(
                                        cursor.getColumnName(column),
                                        if (cursor.isNull(column)) JSONObject.NULL
                                        else cursor.getString(column),
                                    )
                                    values.put(row)
                                }
                            }
                            rows.put(table, values)
                        }
                        return PixelMcpWriteTools.hash(rows)
                    }
                    val beforeConfiguredSettings = call("get_planning_settings")
                    val beforeConfiguredBusiness = originalState()
                    val beforeConfiguredTime = requestTimeProof(store.readableDatabase)
                    val beforeConfiguredLedger = store.ownerValue("settlement:v1", "")
                    val requestCustomer =
                        call("get_customer", JSONObject().put("customer_id", "qa_customer_1"))
                    val requestLocation =
                        WeatherLocationRules.context(
                            appointment(series.getString(1)).getJSONObject("appointment"),
                            JSONArray().put(requestCustomer),
                            JSONArray(),
                        )
                    yes(
                        requestLocation.getString("weather_location_state") == "pending" &&
                            requestLocation.getString("status") != "cancelled" &&
                            !WeatherLocationRules.coordinates(requestLocation),
                        "EISBAER_CONFIGURED_REQUEST_RETRYABLE_LOCAL_WEATHER_LOCATION",
                    )
                    val requestLocationKey = WeatherLocationRules.cacheKey(requestLocation)
                    val queuedRequests = JSONArray()
                    try {
                        call(
                            "update_planning_settings",
                            request("configured_requests_enable")
                                .put(
                                    "expected_record_hash",
                                    beforeConfiguredSettings.getString("record_hash"),
                                )
                                .put("updates", JSONObject().put("weather_enabled", true)),
                            requestTools,
                        )
                        PixelLocalStore(context, clock).use { fresh ->
                            yes(
                                JSONObject(fresh.ownerValue("planning:settings", "{}"))
                                    .getBoolean("weather_enabled"),
                                "EISBAER_CONFIGURED_REQUEST_SETTING_DURABLE",
                            )
                        }
                        for (tool in listOf("request_calendar_sync", "request_weather_refresh")) {
                            val prefs = PlanningAccount.prefs(context)
                            yes(
                                prefs
                                    .edit()
                                    .putInt("calendar_failures", 3)
                                    .putInt("weather_failures", 4)
                                    .putBoolean("weather_blocked", true)
                                    .putString("calendar_retry_at", "2027-05-19T10:00:00Z")
                                    .putString("weather_retry_at", "2027-05-19T10:00:01Z")
                                    .commit(),
                                "EISBAER_CONFIGURED_REQUEST_SEEDED_RETRY_STATE_$tool",
                            )
                            store.putOwnerValue(
                                requestLocationKey,
                                JSONObject()
                                    .put("state", "unavailable")
                                    .put("attempted_at", "2027-05-19T10:00:00Z")
                                    .put("qa_marker", tool)
                                    .toString(),
                            )
                            val args = request("configured_$tool")
                            val queued = call(tool, args, requestTools)
                            yes(
                                queued.getString("state") == "queued" &&
                                    !queued.getBoolean("confirmed") &&
                                    !queued
                                        .getJSONObject("integration")
                                        .getBoolean("calendar_connected"),
                                "EISBAER_CONFIGURED_PUBLIC_REQUEST_QUEUED_$tool",
                            )
                            // A synchronous commit waits for the request's asynchronous apply.
                            yes(
                                prefs.edit().commit() &&
                                    prefs.getInt("calendar_failures", -1) == 0 &&
                                    prefs.getInt("weather_failures", -1) == 0 &&
                                    !prefs.getBoolean("weather_blocked", true) &&
                                    !prefs.contains("calendar_retry_at") &&
                                    !prefs.contains("weather_retry_at"),
                                "EISBAER_CONFIGURED_REQUEST_RETRY_RESET_DURABLE_$tool",
                            )
                            val afterRequestPreferences = JSONObject(prefs.all)
                            PixelLocalStore(context, clock).use { fresh ->
                                val requestedCache =
                                    JSONObject(fresh.ownerValue(requestLocationKey, "{}"))
                                yes(
                                    requestedCache.getString("state") == "pending" &&
                                        !requestedCache.has("attempted_at") &&
                                        requestedCache.getString("qa_marker") == tool,
                                    "EISBAER_CONFIGURED_REQUEST_WEATHER_RETRY_CACHE_DURABLE_$tool",
                                )
                                val freshTools = configuredRequestTools(fresh)
                                val replay = call(tool, args, freshTools)
                                yes(
                                    replay.getBoolean("replayed") &&
                                        replay.getString("state") == "queued" &&
                                        !replay.getBoolean("confirmed") &&
                                        PixelMcpWriteTools.hash(JSONObject(prefs.all)) ==
                                            PixelMcpWriteTools.hash(afterRequestPreferences) &&
                                        PixelMcpWriteTools.hash(
                                            JSONObject(fresh.ownerValue(requestLocationKey, "{}"))
                                        ) == PixelMcpWriteTools.hash(requestedCache),
                                    "EISBAER_CONFIGURED_REQUEST_FRESH_STORE_REPLAY_$tool",
                                )
                                yes(
                                    PixelMcpWriteTools.hash(
                                        PixelMcpReadTools.effective(fresh.getState())
                                    ) == beforeConfiguredBusiness &&
                                        fresh.ownerValue("settlement:v1", "") ==
                                            beforeConfiguredLedger &&
                                        requestTimeProof(fresh.readableDatabase) ==
                                            beforeConfiguredTime,
                                    "EISBAER_CONFIGURED_REQUEST_PRESERVES_ORIGINALS_TIME_AND_LEDGER_$tool",
                                )
                            }
                            queuedRequests.put(tool)
                        }
                    } finally {
                        val configuredSettings = call("get_planning_settings")
                        call(
                            "update_planning_settings",
                            request("configured_requests_restore")
                                .put(
                                    "expected_record_hash",
                                    configuredSettings.getString("record_hash"),
                                )
                                .put(
                                    "updates",
                                    JSONObject()
                                        .put(
                                            "weather_enabled",
                                            beforeConfiguredSettings
                                                .getJSONObject("settings")
                                                .optBoolean("weather_enabled"),
                                        ),
                                ),
                            requestTools,
                        )
                    }
                    yes(
                        suppressedJobs > 0 &&
                            queuedRequests.length() == 2 &&
                            call("get_planning_settings").getString("record_hash") ==
                                beforeConfiguredSettings.getString("record_hash"),
                        "EISBAER_CONFIGURED_REQUESTS_RESTORE_SETTINGS_WITHOUT_EXTERNAL_JOB",
                    )

                    phase = "eisbaer_mcp_calendar_adapter_conflict"
                    val calendarAdapter = calendarAdapterConflictProof(owner)
                    JSONObject()
                        .put("executed_tools", JSONArray(executed.sorted()))
                        .put("calendar_adapter_two_sided_conflict", calendarAdapter)
                        .put(
                            "configured_planning_requests",
                            JSONObject()
                                .put("complete", true)
                                .put("public_queued_tools", queuedRequests)
                                .put("fresh_store_replay", true)
                                .put("durable_retry_state_reset", true)
                                .put("originals_time_and_ledger_preserved", true)
                                .put("external_job_scheduling_suppressed", true)
                                .put("external_provider_success_claimed", false),
                        )
                        .put("isolated_context_paths", true)
                        .put("public_adapter_and_real_sqlite", true)
                        .put("synthetic_clock", true)
                        .put("synthetic_weather_cache_only", true)
                        .put("external_account_connected", false)
                        .put("external_provider_success_claimed", false)
                        .put("physical_calendar_weather_verification", "NOT_RUN")
                        .put("configured_requests_queued_and_replayed", true)
                        .put("planning_settings_series_and_conflicts", true)
                        .put("timer_originals_and_replay_readback", true)
                }
        } finally {
            context.clearPreferences()
            yes(root.deleteRecursively(), "EISBAER_PLANNING_TIME_ROOT_REMOVED")
        }
        return report.put("checks", checks - beforeChecks)
    }

    /** Real CalendarClient/SQLite sync with a synthetic transport, without OAuth or network. */
    private fun calendarAdapterConflictProof(owner: Context): JSONObject {
        val beforeChecks = checks
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-calendar-adapter-$token")
        yes(!root.exists() && root.mkdir(), "EISBAER_CALENDAR_ADAPTER_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_calendar_adapter_$token")
        val executed = linkedSetOf<String>()
        val report: JSONObject
        try {
            report =
                PixelLocalStore(context).use { store ->
                    store.putOwnerValue(
                        "snapshot",
                        SyntheticBusinessData.profile("minimal").toString(),
                    )
                    val tools =
                        PixelMcpReadTools(
                            store.readSource(),
                            store,
                            PixelOwnerAccess(context, store),
                        )
                    fun call(name: String, args: JSONObject): JSONObject {
                        executed.add(name)
                        val result = tools.call(name, args)
                        yes(result.optBoolean("ok"), "EISBAER_CALENDAR_ADAPTER_PUBLIC_OK_$name")
                        return result.getJSONObject("data")
                    }
                    val calendar = "qa_calendar_adapter"
                    // The scoped calendar ID selects the binding namespace; no account is
                    // connected.
                    yes(
                        PlanningAccount.prefs(context)
                            .edit()
                            .putString("calendar_id", calendar)
                            .commit(),
                        "EISBAER_CALENDAR_ADAPTER_SCOPED_CONFIGURATION",
                    )
                    yes(
                        !PlanningAccount.connected(context),
                        "EISBAER_CALENDAR_ADAPTER_NO_LIVE_ACCOUNT",
                    )
                    val now = java.time.Instant.now()
                    val created =
                        call(
                            "create_appointment",
                            JSONObject()
                                .put("request_id", "qa_calendar_adapter_create")
                                .put("customer_id", "qa_customer_1")
                                .put("title", "[TEST] QA bestätigte Kalenderbasis")
                                .put("starts_at", now.plusSeconds(86400).toString())
                                .put("ends_at", now.plusSeconds(90000).toString())
                                .put("timezone", "Europe/Vienna"),
                        )
                    val id = created.getString("appointment_id")
                    val eventId = CalendarRules.eventId(id)
                    val eventsPath = "/calendar/v3/calendars/$calendar/events"
                    var remote: JSONObject? = null
                    var writes = 0
                    var syncReads = 0
                    var eventReads = 0
                    fun reply(status: Int, body: JSONObject) =
                        HttpTransport.Reply(
                            status,
                            body.toString().toByteArray(Charsets.UTF_8),
                            emptyMap(),
                        )
                    val wire = HttpTransport.Wire { method, url, headers, body, _ ->
                        yes(
                            headers["Authorization"] == "Bearer qa_synthetic_calendar_token",
                            "EISBAER_CALENDAR_ADAPTER_SYNTHETIC_CREDENTIAL_ONLY",
                        )
                        val path = java.net.URI(url).path
                        if (method in listOf("POST", "PATCH", "DELETE")) writes++
                        when {
                            method == "GET" && path == eventsPath -> {
                                syncReads++
                                val items = JSONArray()
                                remote?.let { items.put(JSONObject(it.toString())) }
                                reply(
                                    200,
                                    JSONObject()
                                        .put("items", items)
                                        .put("nextSyncToken", "qa_sync_$syncReads"),
                                )
                            }
                            method == "GET" && path == "$eventsPath/$eventId" -> {
                                eventReads++
                                remote?.let { reply(200, JSONObject(it.toString())) }
                                    ?: reply(404, JSONObject())
                            }
                            method == "POST" && path == eventsPath -> {
                                yes(remote == null, "EISBAER_CALENDAR_ADAPTER_NO_DUPLICATE_POST")
                                yes(
                                    java.net.URI(url).query == "sendUpdates=none",
                                    "EISBAER_CALENDAR_ADAPTER_NO_ATTENDEE_MESSAGES",
                                )
                                remote =
                                    JSONObject(requireNotNull(body).toString(Charsets.UTF_8))
                                        .put("etag", "\"qa_etag_1\"")
                                CalendarRules.requireIdentity(requireNotNull(remote), id)
                                reply(201, JSONObject(requireNotNull(remote).toString()))
                            }
                            else ->
                                throw IOException("QA_UNEXPECTED_CALENDAR_TRANSPORT_$method:$path")
                        }
                    }
                    val client =
                        CalendarClient(CalendarClient.Token { "qa_synthetic_calendar_token" }, wire)
                    val bindingKey = AppointmentRuntime.bindingKey(context, id)
                    fun appointment() =
                        call("get_appointment", JSONObject().put("appointment_id", id))
                    fun binding() =
                        PixelLocalStore(context).use { fresh ->
                            JSONObject(fresh.ownerValue(bindingKey, "{}"))
                        }
                    fun businessProof() =
                        PixelLocalStore(context).use { fresh ->
                            val proof =
                                JSONObject()
                                    .put("business", PixelMcpReadTools.effective(fresh.getState()))
                                    .put("ledger", fresh.ownerValue("settlement:v1", "{}"))
                            for (table in listOf("work_sessions", "work_session_events")) {
                                fresh.readableDatabase
                                    .rawQuery("SELECT COUNT(*) FROM $table", null)
                                    .use {
                                        yes(
                                            it.moveToFirst(),
                                            "EISBAER_CALENDAR_ADAPTER_COUNT_$table",
                                        )
                                        proof.put(table, it.getLong(0))
                                    }
                            }
                            PixelMcpWriteTools.hash(proof)
                        }
                    val original = appointment().getJSONObject("appointment")
                    val beforeFirstSync = businessProof()
                    AppointmentRuntime.syncCalendar(context, store, now, client, calendar)
                    val confirmed = binding()
                    yes(
                        confirmed.getString("state") == "confirmed" &&
                            AppointmentRules.hash(confirmed.getJSONObject("base")) ==
                                AppointmentRules.hash(original) &&
                            confirmed.getString("etag") ==
                                requireNotNull(remote).getString("etag") &&
                            writes == 1 &&
                            syncReads == 1 &&
                            eventReads == 2 &&
                            businessProof() == beforeFirstSync,
                        "EISBAER_CALENDAR_ADAPTER_INITIAL_REAL_SYNC_AND_READBACK",
                    )
                    CalendarRules.requireIdentity(requireNotNull(remote), id)
                    val first = appointment()
                    call(
                        "update_appointment",
                        JSONObject()
                            .put("request_id", "qa_calendar_adapter_local_edit")
                            .put("appointment_id", id)
                            .put("expected_record_hash", first.getString("record_hash"))
                            .put("updates", JSONObject().put("title", "[TEST] QA lokale Änderung")),
                    )
                    val local = appointment().getJSONObject("appointment")
                    val externalTitle = "[TEST] QA externe Änderung"
                    val prefix =
                        requireNotNull(remote)
                            .getJSONObject("extendedProperties")
                            .getJSONObject("private")
                            .optString("hg_customer_prefix")
                    requireNotNull(remote)
                        .put("summary", prefix + externalTitle)
                        .put("etag", "\"qa_etag_2\"")
                    yes(
                        local.getString("id") == id &&
                            local.getString("title") != original.getString("title") &&
                            CalendarRules.title(requireNotNull(remote)) == externalTitle &&
                            externalTitle != original.getString("title") &&
                            externalTitle != local.getString("title"),
                        "EISBAER_CALENDAR_ADAPTER_TWO_SIDES_CHANGED_FROM_CONFIRMED_BASE",
                    )
                    val beforeConflict = businessProof()
                    val remoteHash = PixelMcpWriteTools.hash(requireNotNull(remote))
                    val beforeWrites = writes
                    AppointmentRuntime.syncCalendar(
                        context,
                        store,
                        now.plusSeconds(1),
                        client,
                        calendar,
                    )
                    val conflict = binding()
                    yes(
                        conflict.getString("state") == "review_required" &&
                            conflict.getString("error") == "CALENDAR_WRITE_CONFLICT" &&
                            AppointmentRules.hash(conflict.getJSONObject("base")) ==
                                AppointmentRules.hash(original) &&
                            conflict.getString("etag") == confirmed.getString("etag") &&
                            AppointmentRules.hash(
                                conflict.getJSONObject("conflict").getJSONObject("local")
                            ) == AppointmentRules.hash(local) &&
                            PixelMcpWriteTools.hash(
                                conflict.getJSONObject("conflict").getJSONObject("remote")
                            ) == remoteHash,
                        "EISBAER_CALENDAR_ADAPTER_REAL_TWO_SIDED_CONFLICT_PERSISTED",
                    )
                    CalendarRules.requireIdentity(
                        conflict.getJSONObject("conflict").getJSONObject("remote"),
                        id,
                    )
                    yes(
                        businessProof() == beforeConflict &&
                            PixelMcpWriteTools.hash(requireNotNull(remote)) == remoteHash &&
                            writes == beforeWrites &&
                            appointment().getString("record_hash") ==
                                AppointmentRules.hash(local) &&
                            PixelMcpReadTools.effective(store.getState())
                                .getJSONArray("appointments")
                                .length() == 1,
                        "EISBAER_CALENDAR_ADAPTER_CONFLICT_PRESERVES_ORIGINALS_AND_REMOTE_WITHOUT_DUPLICATE",
                    )
                    // The same changed remote is delivered again; this repeats the real conflict
                    // path.
                    AppointmentRuntime.syncCalendar(
                        context,
                        store,
                        now.plusSeconds(2),
                        client,
                        calendar,
                    )
                    yes(
                        PixelMcpWriteTools.hash(binding()) == PixelMcpWriteTools.hash(conflict) &&
                            businessProof() == beforeConflict &&
                            PixelMcpWriteTools.hash(requireNotNull(remote)) == remoteHash &&
                            writes == 1 &&
                            syncReads == 3 &&
                            eventReads == 4,
                        "EISBAER_CALENDAR_ADAPTER_REPEATED_SYNC_PRESERVES_BOTH_VARIANTS",
                    )
                    JSONObject()
                        .put("complete", true)
                        .put("executed_tools", JSONArray(executed.sorted()))
                        .put("real_calendar_client_and_sqlite", true)
                        .put("initial_confirmed_base_readback", true)
                        .put("two_sided_conflict_persisted", true)
                        .put("business_originals_and_remote_preserved", true)
                        .put("repeat_sync_not_request_replay", true)
                        .put("appointment_id", id)
                        .put("event_id", eventId)
                        .put("confirmed_base_hash", AppointmentRules.hash(original))
                        .put("local_hash", AppointmentRules.hash(local))
                        .put("remote_hash", remoteHash)
                        .put("remote_writes", writes)
                        .put("remote_sync_reads", syncReads)
                        .put("remote_event_reads", eventReads)
                        .put("transport", "SYNTHETIC_IN_PROCESS_WIRE")
                        .put("external_account_connected", false)
                        .put("external_provider_success_claimed", false)
                }
        } finally {
            context.clearPreferences()
            yes(root.deleteRecursively(), "EISBAER_CALENDAR_ADAPTER_ROOT_REMOVED")
        }
        return report.put("checks", checks - beforeChecks)
    }

    /**
     * Real offline MCP calls; installed-device/provider acceptance deliberately remains separate.
     */
    private fun eisbaerCatalogProof(owner: Context): JSONObject {
        val initialChecks = checks
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-catalog-$token")
        yes(!root.exists() && root.mkdir(), "CATALOG_MCP_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_catalog_$token")
        val executed = linkedSetOf<String>()
        val succeeded = linkedSetOf<String>()
        val transitions = linkedSetOf<String>()
        lateinit var tools: PixelMcpReadTools
        fun response(name: String, args: JSONObject): JSONObject {
            phase = "eisbaer_catalog_$name"
            executed.add(name)
            return tools
                .rpc(
                    JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", executed.size)
                        .put("method", "tools/call")
                        .put(
                            "params",
                            JSONObject().put("name", name).put("arguments", args),
                        )
                )!!
                .getJSONObject("result")
        }
        fun envelope(name: String, args: JSONObject = JSONObject()): JSONObject {
            val result = response(name, args)
            val value = result.getJSONObject("structuredContent")
            yes(
                !result.getBoolean("isError") && value.optBoolean("ok"),
                "CATALOG_MCP_OK_${name}: ${value.optJSONObject("error")}",
            )
            succeeded.add(name)
            return value
        }
        fun call(name: String, args: JSONObject = JSONObject()): JSONObject =
            envelope(name, args).getJSONObject("data")
        fun rejected(name: String, args: JSONObject, code: String) {
            val result = response(name, args)
            val value = result.getJSONObject("structuredContent")
            yes(
                result.getBoolean("isError") &&
                    !value.optBoolean("ok") &&
                    value.getJSONObject("error").getString("code") == code,
                "CATALOG_MCP_ERROR_${name}_$code",
            )
            transitions.add(name)
        }
        fun request(id: String) = JSONObject().put("request_id", "qa_catalog_mcp_$id")
        fun ids(rows: JSONArray): Set<String> =
            (0 until rows.length()).map { rows.getJSONObject(it).getString("id") }.toSet()
        fun proof(store: PixelLocalStore): String =
            PixelMcpWriteTools.hash(
                JSONObject()
                    .put("business", PixelMcpReadTools.effective(store.getState()))
                    .put("settings", store.ownerValue("settings", "{}"))
                    .put("ledger", store.ownerValue("settlement:v1", "{}"))
                    .put("personal", store.ownerValue("personal_paid", "[]"))
            )
        var customerId = ""
        var orderId = ""
        var durableProof = ""
        try {
            PixelLocalStore(context).use { store ->
                store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
                store.putOwnerValue("personal_paid_migrated", "1")
                store.putOwnerValue("personal_paid", "[]")
                PixelTunnelConfig(context).writeEnabled(true)
                tools =
                    PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
                val catalog = tools.tools()
                // Pinned names and base-read schemas from Eisbär's immutable 155-tool metadata.
                val baselineNames =
                    """
                    integration_health search_customers get_customer search_orders get_order list_work_records
                    get_work_record get_open_orders list_photos get_business_summary search_business_data
                    get_customer_work_records get_work_record_summary get_active_work_session create_work_record
                    create_customer_work_record update_work_record create_customer update_customer create_order
                    update_order finish_work start_work start_break end_break stop_work complete_work_session
                    continue_work discard_work_session restore_work_session review_work_session list_settlements
                    get_settlement get_settlement_context get_settlement_pdf export_settlements run_self_test
                    get_ui_action_catalog get_accounting_overview get_accounting_record get_capabilities
                    preview_delete_work_records preview_archive_work_records preview_delete get_photo get_settings
                    get_business_settings search_catalog get_catalog_entry read_file_chunk get_file_metadata
                    get_full_backup_status get_restore_status get_revier_overview inspect_edit_action list_properties
                    get_property get_property_timeline get_linked_context list_inquiries get_inquiry list_care_plans
                    list_visits get_visit get_day_tour get_planning_board get_tour_navigation list_materials
                    list_equipment search_local get_equipment_maintenance get_offer_decision_history get_list_actions
                    save_settlement_draft delete_settlement_draft prepare_settlement complete_settlement
                    correct_settlement abandon_prepared_settlement add_settlement_payment review_restored_sources
                    record_settlement_delivery delete_work_records set_work_record_billed reassign_work_record
                    update_photo begin_file_upload write_file_chunk create_photo delete_entity update_settings
                    update_business_settings set_personal_paid set_accounting_record request_full_backup
                    retry_full_backup create_property update_property create_inquiry update_inquiry
                    accept_inquiry_direct purge_revier_entity plan_inquiry_inspection create_care_plan update_care_plan
                    generate_care_visits plan_visit update_visit set_day_tour move_tour_stop save_material
                    record_material_consumption record_material_movement save_equipment save_equipment_maintenance_plan
                    record_equipment_maintenance record_offer_acceptance decline_offer queue_list_action
                    undo_list_action get_offer_contract list_offers get_offer validate_offer search_offer_templates
                    get_offer_template get_offer_context create_and_validate_offer validate_offer_draft
                    prepare_offer_delivery confirm_offer_delivery create_offer_draft update_offer_draft finalize_offer
                    duplicate_offer prepare_offer_share convert_offer_to_order create_catalog_entry update_catalog_entry
                    get_planning_settings get_planning_context list_appointments get_appointment
                    suggest_appointment_slots get_workweek_weather get_weather_risks update_planning_settings
                    request_weather_refresh create_appointment create_appointment_series resolve_calendar_conflict
                    update_appointment cancel_appointment acknowledge_weather_alert request_calendar_sync
                    """
                        .trimIndent()
                        .split(Regex("\\s+"))
                        .toSet()
                val additions =
                    setOf("get_lcr_work_records", "get_finance_overview", "set_work_record_lcr")
                val expectedNames = baselineNames + additions
                val actualNames =
                    (0 until catalog.length()).map {
                        catalog.getJSONObject(it).getString("name")
                    }
                yes(
                    baselineNames.size == 155 &&
                        catalog.length() == 158 &&
                        actualNames.size == actualNames.toSet().size &&
                        actualNames.toSet() == expectedNames,
                    "CATALOG_MCP_EXACT_155_PLUS_THREE_UNIQUE_NAMES",
                )
                val baseSchemaHashes =
                    mapOf(
                        "integration_health" to
                            "d746974fa9afd5e951f76f9af38954b0ad7f436f2120dc974da65e5ee39f856f",
                        "search_customers" to
                            "090ded1f4fb5cda692c8c8806e8f924d6ef07ee5cf7b4080a1ac1ece4c78ef62",
                        "get_customer" to
                            "3743a28b90167e8079a869997c1b69f420dc2da5ac67612827df535aa1192fdd",
                        "search_orders" to
                            "286547bd9ecef4df3c8eeca6f8bd67e5e86bd7a7d0c4128541332655b683813f",
                        "get_order" to
                            "d8a472d1aa52d3bd63730df340277e644d2a775a153da604f5c656755eebad50",
                        "list_work_records" to
                            "2c2e0f7b37ae44bab4fba36b18646ec48da2be85d128766b6e0f6ce218c3a98e",
                        "get_work_record" to
                            "8eef36a02adf4a8997c4d236f9fc84237938a0429047bc8a4d4ae22d137781ff",
                        "get_open_orders" to
                            "ab7e2acbffc37ad5f9a7a07ce17b4da170fb5f1483bba8fc76d390fe510cf829",
                        "list_photos" to
                            "5585d60d665e07dbfd222a2541656d309604f00f09df8786db022c637430ac80",
                        "get_business_summary" to
                            "d746974fa9afd5e951f76f9af38954b0ad7f436f2120dc974da65e5ee39f856f",
                        "search_business_data" to
                            "83c1d07624c087b4bb28bc6297b561fed542e3c89da51f5b5607cb5da32408cb",
                        "get_customer_work_records" to
                            "057232539b6fc3cfed04e7770c208b98cb6134c0c045b93765491d356c4013d0",
                        "get_work_record_summary" to
                            "0e775139983640f70b5c2e8311362f9087640eac4242038c9a954140fd386ee4",
                        "get_active_work_session" to
                            "d746974fa9afd5e951f76f9af38954b0ad7f436f2120dc974da65e5ee39f856f",
                    )
                for (index in 0 until catalog.length()) {
                    val entry = catalog.getJSONObject(index)
                    val name = entry.getString("name")
                    val schema = entry.getJSONObject("inputSchema")
                    val properties = schema.getJSONObject("properties")
                    val required = schema.getJSONArray("required")
                    yes(
                        schema.getString("type") == "object" &&
                            !schema.getBoolean("additionalProperties") &&
                            (0 until required.length()).all {
                                properties.has(required.getString(it))
                            } &&
                            entry.getString("description").isNotBlank(),
                        "CATALOG_MCP_SCHEMA_STRUCTURE_$name",
                    )
                    val annotations = entry.getJSONObject("annotations")
                    yes(
                        annotations.has("readOnlyHint") &&
                            annotations.has("destructiveHint") &&
                            annotations.has("idempotentHint") &&
                            !annotations.getBoolean("openWorldHint"),
                        "CATALOG_MCP_ANNOTATIONS_$name",
                    )
                    val expectedSchema =
                        when {
                            PixelMcpWriteTools.handles(name) -> PixelMcpWriteTools.schema(name)
                            PixelMcpEntityWriteTools.handles(name) ->
                                PixelMcpEntityWriteTools.schema(name)
                            PixelMcpSessionTools.handles(name) -> PixelMcpSessionTools.schema(name)
                            PixelMcpOwnerTools.read(name) || PixelMcpOwnerTools.write(name) ->
                                PixelMcpOwnerTools.schema(name)
                            OfferTools.read(name) || OfferTools.write(name) ->
                                OfferTools.schema(name)
                            AppointmentTools.read(name) || AppointmentTools.write(name) ->
                                AppointmentTools.schema(name)
                            else -> null
                        }
                    yes(
                        if (expectedSchema == null)
                            PixelMcpWriteTools.hash(schema) == baseSchemaHashes[name]
                        else
                            PixelMcpWriteTools.hash(schema) ==
                                PixelMcpWriteTools.hash(expectedSchema),
                        "CATALOG_MCP_EXACT_RUNTIME_SCHEMA_$name",
                    )
                }
                val listed =
                    tools
                        .rpc(
                            JSONObject()
                                .put("jsonrpc", "2.0")
                                .put("id", "catalog")
                                .put("method", "tools/list")
                        )!!
                        .getJSONObject("result")
                        .getJSONArray("tools")
                val capabilities = call("get_capabilities")
                val catalogHash = PixelMcpWriteTools.hash(catalog)
                yes(
                    PixelMcpWriteTools.hash(listed) == catalogHash &&
                        PixelMcpWriteTools.hash(capabilities.getJSONArray("tools")) ==
                            catalogHash &&
                        capabilities.getString("tool_catalog_hash") == catalogHash &&
                        capabilities.getInt("protocol_version") == 9 &&
                        capabilities.getBoolean("write_enabled") &&
                        !capabilities.getBoolean("business_sync_allowed"),
                    "CATALOG_MCP_PROTOCOL_SCHEMA_IDENTITY",
                )
                val runtime = capabilities.getJSONObject("runtime")
                yes(
                    runtime.getString("package_name") == owner.packageName &&
                        runtime.getInt("database_version") == 7 &&
                        runtime.getInt("storage_contract_version") == 3 &&
                        runtime.getJSONObject("capabilities").getBoolean("object_care_removed") &&
                        runtime
                            .getJSONObject("capabilities")
                            .getBoolean("lcr_original_work_marker_v1") &&
                        runtime.getJSONObject("build_fingerprint").length() > 0,
                    "CATALOG_MCP_RUNTIME_IDENTITY_AND_CAPABILITIES",
                )
                val beforeRead = proof(store)
                val health = call("integration_health")
                yes(
                    health.getString("status") == "ready" &&
                        health.getBoolean("write_enabled") &&
                        health.getInt("tool_count") == 158 &&
                        health.getString("tool_catalog_hash") == catalogHash,
                    "CATALOG_MCP_HEALTH_MATCHES_REAL_RUNTIME",
                )
                val inventory =
                    context.assets.open("ui-mcp-actions.json").use {
                        JSONObject(it.readBytes().toString(Charsets.UTF_8))
                    }
                val actionHash = PixelMcpWriteTools.hash(inventory)
                val allActions = JSONArray()
                var offset = 0
                do {
                    val page =
                        call(
                            "get_ui_action_catalog",
                            JSONObject()
                                .put("offset", offset)
                                .put("limit", 37)
                                .put("expected_catalog_hash", actionHash),
                        )
                    yes(
                        page.getString("catalog_hash") == actionHash &&
                            page.getInt("total") == inventory.getJSONArray("actions").length() &&
                            page.getInt("offset") == offset,
                        "CATALOG_MCP_ACTION_PAGE_$offset",
                    )
                    val rows = page.getJSONArray("actions")
                    for (index in 0 until rows.length()) allActions.put(rows.getJSONObject(index))
                    offset += rows.length()
                    if (page.isNull("next_cursor")) break
                    yes(
                        page.getInt("next_cursor") == offset && rows.length() > 0,
                        "CATALOG_MCP_ACTION_CURSOR_$offset",
                    )
                } while (offset < inventory.getJSONArray("actions").length())
                yes(
                    PixelMcpWriteTools.hash(allActions) ==
                        PixelMcpWriteTools.hash(inventory.getJSONArray("actions")),
                    "CATALOG_MCP_COMPLETE_ACTION_MATRIX",
                )
                rejected(
                    "get_ui_action_catalog",
                    JSONObject().put("expected_catalog_hash", "0".repeat(64)),
                    "CATALOG_CHANGED",
                )
                val settings = call("get_settings")
                val business = call("get_business_settings")
                yes(
                    !settings.getBoolean("credentials_exposed") &&
                        !business.getBoolean("credentials_exposed") &&
                        business.getJSONObject("settings").getString("tax_mode") ==
                            "unconfigured" &&
                        settings.getInt("storage_contract_version") == 3,
                    "CATALOG_MCP_PRIVATE_SETTINGS_READ",
                )
                val diagnostics = call("run_self_test")
                val diagnosticRows = diagnostics.getJSONArray("checks")
                fun diagnostic(name: String): JSONObject =
                    (0 until diagnosticRows.length())
                        .map {
                            diagnosticRows.getJSONObject(it)
                        }
                        .single { it.getString("name") == name }
                for (name in
                    listOf("build_and_protocol", "catalog_unique", "write_gate", "ui_mcp_parity")) {
                    yes(
                        diagnostic(name).getString("status") == "PASS",
                        "CATALOG_MCP_REAL_DIAGNOSTIC_$name",
                    )
                }
                yes(
                    diagnostics.getInt("tool_count") == 158 &&
                        diagnostics.getString("tool_catalog_hash") == catalogHash &&
                        !diagnostics.getBoolean("business_data_changed") &&
                        !diagnostics.getBoolean("secrets_exposed") &&
                        diagnostic("physical_google_and_delivery").getString("status") ==
                            "NOT TESTABLE",
                    "CATALOG_MCP_DIAGNOSTICS_KEEP_DEVICE_ACCEPTANCE_OPEN",
                )
                yes(
                    proof(store) == beforeRead,
                    "CATALOG_MCP_READS_AND_DIAGNOSTICS_DO_NOT_CHANGE_BUSINESS",
                )

                val createCustomer =
                    request("customer_create")
                        .put("name", "[TEST] QA Catalog Einsatzkunde")
                        .put("city", "QA Katalogort")
                        .put("email", "qa_catalog@example.invalid")
                        .put("test_record", true)
                customerId = call("create_customer", createCustomer).getString("entity_id")
                yes(
                    call("create_customer", createCustomer).getBoolean("replayed"),
                    "CATALOG_MCP_CUSTOMER_CREATE_REPLAY",
                )
                val customerRead =
                    envelope("get_customer", JSONObject().put("customer_id", customerId))
                yes(
                    customerRead.getJSONObject("data").getString("name") ==
                        "[TEST] QA Catalog Einsatzkunde" &&
                        customerRead.getJSONObject("data").getString("city") == "QA Katalogort",
                    "CATALOG_MCP_CUSTOMER_CREATE_READBACK",
                )
                val updateCustomer =
                    request("customer_update")
                        .put("customer_id", customerId)
                        .put("expected_record_hash", customerRead.getString("record_hash"))
                        .put(
                            "updates",
                            JSONObject()
                                .put("notes", "QA Katalognotiz gespeichert")
                                .put("custom_fields", JSONObject().put("QA Tor", "QA Nord")),
                        )
                call("update_customer", updateCustomer)
                yes(
                    call("update_customer", updateCustomer).getBoolean("replayed"),
                    "CATALOG_MCP_CUSTOMER_UPDATE_REPLAY",
                )
                val updatedCustomer =
                    call("get_customer", JSONObject().put("customer_id", customerId))
                yes(
                    updatedCustomer.getString("notes") == "QA Katalognotiz gespeichert" &&
                        updatedCustomer.getString("city") == "QA Katalogort" &&
                        updatedCustomer.getJSONObject("custom_fields").getString("QA Tor") ==
                            "QA Nord",
                    "CATALOG_MCP_CUSTOMER_UPDATE_PRESERVES_UNMENTIONED_FIELDS",
                )
                var beforeRejected = proof(store)
                rejected(
                    "update_customer",
                    JSONObject(updateCustomer.toString())
                        .put("request_id", "qa_catalog_mcp_customer_stale"),
                    "WRITE_CONFLICT",
                )
                rejected(
                    "create_customer",
                    JSONObject(createCustomer.toString()).put("city", "QA Changed"),
                    "REPLAY_MISMATCH",
                )
                rejected(
                    "create_customer",
                    JSONObject(createCustomer.toString())
                        .put("request_id", "qa_catalog_mcp_customer_duplicate"),
                    "POSSIBLE_DUPLICATE",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_CUSTOMER_CONFLICTS_DO_NOT_MUTATE")
                yes(
                    ids(
                        call(
                                "search_customers",
                                JSONObject().put("query", "[TEST] QA Catalog Einsatzkunde"),
                            )
                            .getJSONArray("customers")
                    ) == setOf(customerId),
                    "CATALOG_MCP_CUSTOMER_SEARCH_READBACK",
                )

                val createOrder =
                    request("order_create")
                        .put("customer_id", customerId)
                        .put("title", "QA Catalog Auftrag")
                        .put("status", "Geplant")
                        .put("test_record", true)
                orderId = call("create_order", createOrder).getString("entity_id")
                yes(
                    call("create_order", createOrder).getBoolean("replayed"),
                    "CATALOG_MCP_ORDER_CREATE_REPLAY",
                )
                val orderRead = envelope("get_order", JSONObject().put("order_id", orderId))
                yes(
                    orderRead.getJSONObject("data").getString("customer_id") == customerId,
                    "CATALOG_MCP_ORDER_CREATE_READBACK",
                )
                val updateOrder =
                    request("order_update")
                        .put("order_id", orderId)
                        .put("expected_record_hash", orderRead.getString("record_hash"))
                        .put("updates", JSONObject().put("title", "QA Catalog Auftrag korrigiert"))
                call("update_order", updateOrder)
                yes(
                    call("update_order", updateOrder).getBoolean("replayed"),
                    "CATALOG_MCP_ORDER_UPDATE_REPLAY",
                )
                yes(
                    call("get_order", JSONObject().put("order_id", orderId)).getString("title") ==
                        "QA Catalog Auftrag korrigiert",
                    "CATALOG_MCP_ORDER_UPDATE_READBACK",
                )
                beforeRejected = proof(store)
                rejected(
                    "update_order",
                    JSONObject(updateOrder.toString())
                        .put("request_id", "qa_catalog_mcp_order_stale"),
                    "WRITE_CONFLICT",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_ORDER_CONFLICT_DOES_NOT_MUTATE")
                yes(
                    ids(
                        call(
                                "search_orders",
                                JSONObject()
                                    .put("query", "QA Catalog Auftrag korrigiert")
                                    .put("customer_id", customerId),
                            )
                            .getJSONArray("orders")
                    ) == setOf(orderId),
                    "CATALOG_MCP_ORDER_SEARCH_READBACK",
                )
                yes(
                    ids(
                        call("get_open_orders", JSONObject().put("customer_id", customerId))
                            .getJSONArray("orders")
                    ) == setOf(orderId),
                    "CATALOG_MCP_OPEN_ORDER_READBACK",
                )
                val contextRead = call("get_linked_context", JSONObject().put("order_id", orderId))
                yes(
                    contextRead.getString("customer_id") == customerId &&
                        contextRead.getString("order_id") == orderId,
                    "CATALOG_MCP_LINKED_CONTEXT_RESOLVES_ORIGINAL_PARENT",
                )
                val crossSearch =
                    call(
                        "search_business_data",
                        JSONObject().put("query", "QA Catalog").put("limit_per_type", 20),
                    )
                yes(
                    ids(crossSearch.getJSONArray("customers")) == setOf(customerId) &&
                        ids(crossSearch.getJSONArray("orders")) == setOf(orderId) &&
                        crossSearch.getJSONArray("work_records").length() == 0,
                    "CATALOG_MCP_CROSS_BUSINESS_SEARCH_RESULTS",
                )

                val workArgs = JSONObject().put("work_record_id", "qa_work_1")
                val workRead = envelope("get_work_record", workArgs)
                val original = workRead.getJSONObject("data")
                val reassignment =
                    request("reassign")
                        .put("work_record_id", "qa_work_1")
                        .put("expected_record_hash", workRead.getString("record_hash"))
                        .put("customer_id", customerId)
                        .put("billing_customer_id", "qa_customer_1")
                        .put("order_id", "")
                        .put("reason", "QA bewusst andere Kundenzuordnung")
                call("reassign_work_record", reassignment)
                yes(
                    call("reassign_work_record", reassignment).getBoolean("replayed"),
                    "CATALOG_MCP_REASSIGN_REPLAY",
                )
                val reassigned = call("get_work_record", workArgs)
                yes(
                    reassigned.getString("id") == original.getString("id") &&
                        reassigned.getString("customer_id") == customerId &&
                        reassigned.getString("billing_customer_id") == "qa_customer_1" &&
                        reassigned.optString("order_id").isEmpty() &&
                        reassigned.getDouble("hours") == original.getDouble("hours") &&
                        reassigned.getString("activity") == original.getString("activity"),
                    "CATALOG_MCP_REASSIGN_ONE_ORIGINAL_PRESERVES_WORK",
                )
                beforeRejected = proof(store)
                rejected(
                    "reassign_work_record",
                    JSONObject(reassignment.toString())
                        .put("request_id", "qa_catalog_mcp_reassign_stale"),
                    "WRITE_CONFLICT",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_REASSIGN_CONFLICT_NO_MUTATION")
                val customerWorks =
                    call(
                        "get_customer_work_records",
                        JSONObject()
                            .put("customer_id", customerId)
                            .put("date_from", "2026-10-01")
                            .put("date_to", "2026-10-01"),
                    )
                yes(
                    ids(customerWorks.getJSONArray("work_records")) == setOf("qa_work_1") &&
                        call(
                                "get_customer_work_records",
                                JSONObject().put("customer_id", "qa_customer_1"),
                            )
                            .getJSONArray("work_records")
                            .length() == 0,
                    "CATALOG_MCP_CUSTOMER_WORKS_FOLLOW_REAL_REASSIGNMENT",
                )
                yes(
                    ids(
                        call(
                                "list_work_records",
                                JSONObject()
                                    .put("customer_id", customerId)
                                    .put("billing_customer_id", "qa_customer_1"),
                            )
                            .getJSONArray("work_records")
                    ) == setOf("qa_work_1"),
                    "CATALOG_MCP_WORK_FILTERS_READBACK",
                )
                val summary =
                    call(
                        "get_work_record_summary",
                        JSONObject()
                            .put("customer_id", customerId)
                            .put("group_by_billing_customer", true),
                    )
                yes(
                    summary.getInt("work_records") == 1 && summary.getDouble("hours") == 2.0,
                    "CATALOG_MCP_WORK_SUMMARY_REAL_ORIGINAL",
                )
                val businessSummary = call("get_business_summary")
                yes(
                    businessSummary.getInt("customers") == 2 &&
                        businessSummary.getInt("orders") == 2 &&
                        businessSummary.getInt("work_records") == 1 &&
                        businessSummary.getDouble("hours") == 2.0,
                    "CATALOG_MCP_BUSINESS_SUMMARY_REAL_COUNTS",
                )
                val accounting =
                    call(
                        "get_accounting_record",
                        JSONObject().put("collection", "worklogs").put("entity_id", "qa_work_1"),
                    )
                yes(
                    accounting.getString("id") == "qa_work_1" &&
                        accounting.getString("collection") == "worklogs" &&
                        accounting.getInt("work_count") == 1 &&
                        accounting.getString("expected_record_hash").isNotBlank(),
                    "CATALOG_MCP_ACCOUNTING_READS_SAME_ORIGINAL",
                )
                val originalLedger = store.ownerValue("settlement:v1", "{}")
                val markBilled =
                    request("manual_billed")
                        .put("work_record_id", "qa_work_1")
                        .put(
                            "expected_record_hash",
                            envelope("get_work_record", workArgs).getString("record_hash"),
                        )
                        .put("billed", true)
                        .put("reason", "QA ausdrückliche manuelle Markierung")
                call("set_work_record_billed", markBilled)
                yes(
                    call("set_work_record_billed", markBilled).getBoolean("replayed") &&
                        call("get_work_record", workArgs).getInt("billed") == 1,
                    "CATALOG_MCP_MANUAL_BILLED_REPLAY_READBACK",
                )
                beforeRejected = proof(store)
                rejected(
                    "set_work_record_billed",
                    JSONObject(markBilled.toString())
                        .put("request_id", "qa_catalog_mcp_manual_billed_stale"),
                    "WRITE_CONFLICT",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_MANUAL_BILLED_STALE_NO_MUTATION")
                val markedAccounting =
                    call(
                        "get_accounting_record",
                        JSONObject().put("collection", "worklogs").put("entity_id", "qa_work_1"),
                    )
                val clearMark =
                    request("manual_route_open")
                        .put("collection", "worklogs")
                        .put("entity_id", "qa_work_1")
                        .put("route", "open")
                        .put(
                            "expected_record_hash",
                            markedAccounting.getString("expected_record_hash"),
                        )
                call("set_accounting_record", clearMark)
                yes(
                    call("set_accounting_record", clearMark).getBoolean("replayed") &&
                        call("get_work_record", workArgs).getInt("billed") == 0 &&
                        store.ownerValue("settlement:v1", "{}") == originalLedger &&
                        PixelMcpReadTools.effective(store.getState())
                            .getJSONArray("invoices")
                            .length() == 0,
                    "CATALOG_MCP_MANUAL_ROUTE_REPLAY_WITHOUT_DOCUMENT_OR_PAYMENT",
                )
                beforeRejected = proof(store)
                rejected(
                    "set_accounting_record",
                    JSONObject(clearMark.toString())
                        .put("request_id", "qa_catalog_mcp_manual_route_stale"),
                    "WRITE_CONFLICT",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_MANUAL_ROUTE_STALE_NO_MUTATION")
                val beforePreview = proof(store)
                val selection = JSONObject().put("work_record_ids", JSONArray().put("qa_work_1"))
                val preview = call("preview_delete_work_records", selection)
                val archiveAlias = call("preview_archive_work_records", selection)
                yes(
                    preview.getInt("count") == 1 &&
                        preview.getBoolean("allowed") &&
                        preview.getBoolean("physical_delete") &&
                        !preview.getBoolean("recoverable") &&
                        preview.getJSONArray("entries").getJSONObject(0).getString("entity_id") ==
                            "qa_work_1" &&
                        PixelMcpWriteTools.hash(preview) == PixelMcpWriteTools.hash(archiveAlias) &&
                        proof(store) == beforePreview,
                    "CATALOG_MCP_BOTH_PREVIEWS_READ_ONLY_SAME_ORIGINAL",
                )
                rejected(
                    "set_personal_paid",
                    request("retired_legacy").put("work_record_id", "qa_work_1").put("paid", true),
                    "LEGACY_LABOR_RETIRED",
                )
                yes(
                    proof(store) == beforePreview &&
                        store.ownerValue("personal_paid", "[]") == "[]",
                    "CATALOG_MCP_LEGACY_PATH_DOES_NOT_REINTERPRET_WORK",
                )

                val updateSettings =
                    request("settings_update")
                        .put("expected_record_hash", settings.getString("record_hash"))
                        .put(
                            "updates",
                            JSONObject()
                                .put("default_activity", "QA Catalog Standardarbeit")
                                .put("photo_quality", "high"),
                        )
                call("update_settings", updateSettings)
                yes(
                    call("update_settings", updateSettings).getBoolean("replayed"),
                    "CATALOG_MCP_SETTINGS_REPLAY",
                )
                yes(
                    call("get_settings").getJSONObject("settings").getString("default_activity") ==
                        "QA Catalog Standardarbeit",
                    "CATALOG_MCP_SETTINGS_READBACK",
                )
                val updateBusiness =
                    request("business_update")
                        .put("expected_record_hash", business.getString("record_hash"))
                        .put("updates", JSONObject().put("business_phone", "+43 000 000000"))
                call("update_business_settings", updateBusiness)
                yes(
                    call("update_business_settings", updateBusiness).getBoolean("replayed"),
                    "CATALOG_MCP_BUSINESS_SETTINGS_REPLAY",
                )
                val updatedBusiness = call("get_business_settings").getJSONObject("settings")
                yes(
                    updatedBusiness.getString("business_phone") == "+43 000 000000" &&
                        updatedBusiness.getString("tax_mode") == "unconfigured" &&
                        updatedBusiness.getString("business_name") ==
                            business.getJSONObject("settings").getString("business_name"),
                    "CATALOG_MCP_BUSINESS_SETTINGS_PRESERVE_TAX_STATUS",
                )
                beforeRejected = proof(store)
                rejected(
                    "update_settings",
                    JSONObject(updateSettings.toString())
                        .put("request_id", "qa_catalog_mcp_settings_stale"),
                    "WRITE_CONFLICT",
                )
                rejected(
                    "update_business_settings",
                    JSONObject(updateBusiness.toString())
                        .put("request_id", "qa_catalog_mcp_business_stale"),
                    "WRITE_CONFLICT",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_SETTINGS_CONFLICTS_NO_MUTATION")
                val readOnly =
                    PixelMcpReadTools(store.readSource(), null, PixelOwnerAccess(context, store))
                tools = readOnly
                yes(
                    !call("get_capabilities").getBoolean("write_enabled") &&
                        (0 until readOnly.tools().length()).all {
                            readOnly
                                .tools()
                                .getJSONObject(it)
                                .getJSONObject("annotations")
                                .getBoolean("readOnlyHint")
                        },
                    "CATALOG_MCP_READ_ONLY_REGISTRATION_GATE",
                )
                rejected(
                    "create_customer",
                    request("denied").put("name", "QA Write disabled"),
                    "WRITE_DISABLED",
                )
                yes(proof(store) == beforeRejected, "CATALOG_MCP_WRITE_GATE_NO_MUTATION")
                durableProof = proof(store)
            }
            PixelLocalStore(context).use { reopened ->
                tools =
                    PixelMcpReadTools(
                        reopened.readSource(),
                        reopened,
                        PixelOwnerAccess(context, reopened),
                    )
                yes(
                    proof(reopened) == durableProof,
                    "CATALOG_MCP_DATABASE_REOPEN_EXACT_BUSINESS_STATE",
                )
                yes(
                    call("get_customer", JSONObject().put("customer_id", customerId))
                        .getString("notes") == "QA Katalognotiz gespeichert" &&
                        call("get_order", JSONObject().put("order_id", orderId))
                            .getString("title") == "QA Catalog Auftrag korrigiert" &&
                        call("get_work_record", JSONObject().put("work_record_id", "qa_work_1"))
                            .getString("customer_id") == customerId &&
                        call("get_settings").getJSONObject("settings").getString("photo_quality") ==
                            "high" &&
                        call("get_business_settings")
                            .getJSONObject("settings")
                            .getString("business_phone") == "+43 000 000000",
                    "CATALOG_MCP_CUSTOMER_ORDER_WORK_SETTINGS_DURABLE_READBACK",
                )
            }
            return JSONObject()
                .put("executed_tools", JSONArray(executed.toList()))
                .put("successful_tools", JSONArray(succeeded.toList()))
                .put("transition_tools", JSONArray(transitions.toList()))
                .put("checks", checks - initialChecks)
                .put("catalog_baseline_tools", 155)
                .put("catalog_runtime_tools", 158)
                .put("exact_runtime_schemas", true)
                .put("real_pixel_mcp_adapter", true)
                .put("real_rpc_tools_call", true)
                .put("durable_business_readback", true)
                .put("write_replay_and_stale_conflict", true)
                .put("device_connections_acceptance", false)
                .put("synthetic_only", true)
        } finally {
            context.clearPreferences()
            yes(root.deleteRecursively(), "CATALOG_MCP_ISOLATED_ROOT_REMOVED")
        }
    }

    private fun eisbaerRevierMcpProof(owner: Context): JSONObject {
        val beforeChecks = checks
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-eisbaer-revier-$token")
        yes(!root.exists() && root.mkdir(), "EISBAER_REVIER_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_eisbaer_revier_$token")
        val executed = linkedSetOf<String>()
        val positive = linkedSetOf<String>()
        val transitions = linkedSetOf<String>()
        val report: JSONObject
        try {
            report =
                PixelLocalStore(context).use { store ->
                    val fixture = SyntheticBusinessData.profile("minimal")
                    // Test documents require a visibly marked customer as well as test IDs.
                    fixture
                        .getJSONArray("customers")
                        .getJSONObject(0)
                        .put("name", "[TEST] QA MCP Revier Kunde")
                    store.putOwnerValue("snapshot", fixture.toString())
                    val tools =
                        PixelMcpReadTools(
                            store.readSource(),
                            store,
                            PixelOwnerAccess(context, store),
                        )
                    fun call(name: String, args: JSONObject = JSONObject()): JSONObject {
                        executed.add(name)
                        val result = tools.call(name, args)
                        yes(result.optBoolean("ok"), "EISBAER_REVIER_PUBLIC_OK_$name")
                        positive.add(name)
                        return result.getJSONObject("data")
                    }
                    fun rejected(name: String, args: JSONObject, code: String): JSONObject {
                        executed.add(name)
                        try {
                            tools.call(name, args)
                        } catch (failure: Exception) {
                            val error = McpErrors.rpc(failure).getJSONObject("error")
                            yes(
                                error.getString("code") == code,
                                "EISBAER_REVIER_REJECT_${name}_$code",
                            )
                            transitions.add(name)
                            return error
                        }
                        throw AssertionError("QA_NATIVE_DATA_EISBAER_REVIER_EXPECTED_${name}_$code")
                    }
                    fun request(id: String) =
                        JSONObject().put("request_id", "qa_eisbaer_revier_$id")
                    fun master() = PixelMcpReadTools.effective(store.getState())
                    fun ids(rows: JSONArray) = SettlementRules.rows(rows).map { it.getString("id") }
                    fun property(id: String) =
                        call("get_property", JSONObject().put("property_id", id))
                            .getJSONObject("record")

                    phase = "eisbaer_revier_protected_fixture"
                    val protectedProperty =
                        call(
                                "create_property",
                                request("protected_property")
                                    .put("customer_id", "qa_customer_1")
                                    .put("name", "[TEST] QA bestehender Originalbezug"),
                            )
                            .getJSONObject("record")
                    // Arrange an older synthetic original before the write/read workflows under
                    // test.
                    // Its file bytes are not part of this proof; the relationship protection is
                    // real.
                    fixture
                        .getJSONArray("worklogs")
                        .getJSONObject(0)
                        .put("property_id", protectedProperty.getString("id"))
                    fixture
                        .getJSONArray("invoices")
                        .put(
                            JSONObject()
                                .put("id", "qa_revier_historic_invoice")
                                .put("invoice_number", "QA-HIST-001")
                                .put("status", "issued")
                                .put("customer_id", "qa_customer_1")
                                .put("property_id", protectedProperty.getString("id"))
                                .put("gross_total", "42.00")
                                .put("issued_at", "2026-10-01T10:00:00Z")
                                .put("test_record", true)
                        )
                    store.putOwnerValue("snapshot", fixture.toString())
                    fun legacyOriginals(): String {
                        val state = master()
                        return PixelMcpWriteTools.hash(
                            JSONObject()
                                .put(
                                    "work",
                                    SnapshotReadSupport.find(
                                        state.getJSONArray("worklogs"),
                                        "qa_work_1",
                                    ),
                                )
                                .put(
                                    "order",
                                    SnapshotReadSupport.find(
                                        state.getJSONArray("orders"),
                                        "qa_order_1",
                                    ),
                                )
                                .put(
                                    "customer",
                                    SnapshotReadSupport.find(
                                        state.getJSONArray("customers"),
                                        "qa_customer_1",
                                    ),
                                )
                                .put("invoices", state.getJSONArray("invoices"))
                        )
                    }
                    val beforeLegacy = legacyOriginals()
                    val baselineWorks = ids(master().getJSONArray("worklogs")).toSet()
                    val publicProperty =
                        call(
                                "create_property",
                                request("editable_property")
                                    .put("customer_id", "qa_customer_1")
                                    .put("name", "[TEST] QA MCP Objekt vor Änderung"),
                            )
                            .getJSONObject("record")
                    val propertyId = publicProperty.getString("id")
                    val propertyChange =
                        request("update_property")
                            .put("property_id", propertyId)
                            .put("expected_record_hash", publicProperty.getString("record_hash"))
                            .put(
                                "updates",
                                JSONObject()
                                    .put("name", "[TEST] QA MCP Objekt geändert")
                                    .put("access_notes", "[TEST] QA bewusste Zutrittsangabe"),
                            )
                    call("update_property", propertyChange)
                    yes(
                        call("update_property", propertyChange).getBoolean("replayed"),
                        "EISBAER_REVIER_PROPERTY_UPDATE_REPLAY",
                    )
                    val updatedProperty = property(propertyId)
                    yes(
                        updatedProperty.getString("id") == propertyId &&
                            updatedProperty.getString("name") == "[TEST] QA MCP Objekt geändert" &&
                            updatedProperty.getString("access_notes") ==
                                "[TEST] QA bewusste Zutrittsangabe",
                        "EISBAER_REVIER_PROPERTY_SAME_ORIGINAL_REREAD",
                    )
                    rejected(
                        "update_property",
                        JSONObject(propertyChange.toString())
                            .put("request_id", "qa_eisbaer_revier_update_property_stale"),
                        "WRITE_CONFLICT",
                    )
                    val listedProperties =
                        call(
                            "list_properties",
                            JSONObject()
                                .put("customer_id", "qa_customer_1")
                                .put("query", "MCP Objekt geändert"),
                        )
                    yes(
                        listedProperties.getInt("total") == 1 &&
                            listedProperties
                                .getJSONArray("items")
                                .getJSONObject(0)
                                .getString("id") == propertyId,
                        "EISBAER_REVIER_LIST_PROPERTIES_PUBLIC_FILTER",
                    )

                    phase = "eisbaer_mcp_inquiry_direct_acceptance"
                    fun inquiryMaster() = PixelMcpReadTools.effective(store.getState())
                    val inquiryBaseline = inquiryMaster()
                    val inquiryBaselineIds =
                        listOf("customers", "orders", "worklogs", "invoices", "offers", "photos")
                            .associateWith { group ->
                                SettlementRules.rows(inquiryBaseline.optJSONArray(group))
                                    .map { it.getString("id") }
                                    .toSet()
                            }
                    fun inquiryExistingOriginals(): JSONObject {
                        val current = inquiryMaster()
                        return JSONObject().apply {
                            for ((group, ids) in inquiryBaselineIds) {
                                val byId =
                                    SettlementRules.rows(current.optJSONArray(group)).associateBy {
                                        it.getString("id")
                                    }
                                put(
                                    group,
                                    JSONArray(
                                        ids.sorted().map { id ->
                                            requireNotNull(byId[id]) {
                                                "An original $group record disappeared"
                                            }
                                        }
                                    ),
                                )
                            }
                        }
                    }
                    val inquiryOriginalHash = PixelMcpWriteTools.hash(inquiryExistingOriginals())
                    val inquiryInitialOrders =
                        SettlementRules.rows(inquiryBaseline.optJSONArray("orders"))
                            .map { it.getString("id") }
                            .toSet()
                    val inquiryInitialAppointments =
                        SettlementRules.rows(inquiryBaseline.optJSONArray("appointments"))
                            .map { it.getString("id") }
                            .toSet()
                    val inquiryInitialWorks =
                        PixelMcpWriteTools.hash(inquiryBaseline.getJSONArray("worklogs"))
                    val inquiryInitialInvoices =
                        PixelMcpWriteTools.hash(inquiryBaseline.getJSONArray("invoices"))
                    val inquiryInitialLedger = store.ownerValue(SettlementRules.KEY, "{}")
                    val inquiryRecipientCreation =
                        call(
                            "create_customer",
                            request("inquiry_recipient")
                                .put("name", "[TEST] QA MCP Anfrage Rechnungsempfänger"),
                        )
                    val inquiryRecipientId = inquiryRecipientCreation.getString("entity_id")
                    val inquiryRecipient =
                        call("get_customer", JSONObject().put("customer_id", inquiryRecipientId))
                    yes(
                        inquiryRecipient.getString("id") == inquiryRecipientId &&
                            inquiryRecipientId != "qa_customer_1",
                        "EISBAER_INQUIRY_REAL_DISTINCT_RECIPIENT_REREAD",
                    )
                    val inquiryDirectCreateArgs =
                        request("inquiry_direct_create")
                            .put("customer_id", "qa_customer_1")
                            .put("property_id", propertyId)
                            .put("title", "[TEST] QA MCP ausdrücklich angenommene Anfrage")
                            .put("concern", "[TEST] QA tatsächlicher Kundenwunsch")
                    val inquiryDirectCreated =
                        call("create_inquiry", inquiryDirectCreateArgs).getJSONObject("record")
                    val inquiryDirectInquiryId = inquiryDirectCreated.getString("id")
                    yes(
                        call("create_inquiry", inquiryDirectCreateArgs).getBoolean("replayed"),
                        "EISBAER_INQUIRY_CREATE_REPLAY",
                    )
                    val inquiryDirectFresh =
                        call("get_inquiry", JSONObject().put("inquiry_id", inquiryDirectInquiryId))
                            .getJSONObject("record")
                    yes(
                        inquiryDirectFresh.getString("status") == "new" &&
                            inquiryDirectFresh.optString("order_id").isBlank() &&
                            SettlementRules.rows(inquiryMaster().getJSONArray("orders"))
                                .map { it.getString("id") }
                                .toSet() == inquiryInitialOrders,
                        "EISBAER_INQUIRY_CREATE_AND_READ_DO_NOT_ACCEPT",
                    )
                    val inquiryDirectAcceptArgs =
                        request("inquiry_direct_accept")
                            .put("inquiry_id", inquiryDirectInquiryId)
                            .put(
                                "expected_record_hash",
                                inquiryDirectFresh.getString("record_hash"),
                            )
                            .put("accepted_at", "2027-05-18T07:00:00Z")
                            .put("evidence", "[TEST] QA bewusst dokumentierte mündliche Zusage")
                            .put("billing_customer_id", inquiryRecipientId)
                            .put("confirmed", true)
                    rejected(
                        "accept_inquiry_direct",
                        JSONObject(inquiryDirectAcceptArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_accept_unconfirmed")
                            .put("confirmed", false),
                        "CONFIRMATION_REQUIRED",
                    )
                    rejected(
                        "accept_inquiry_direct",
                        JSONObject(inquiryDirectAcceptArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_accept_stale_before")
                            .put("expected_record_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    yes(
                        call("get_inquiry", JSONObject().put("inquiry_id", inquiryDirectInquiryId))
                            .getJSONObject("record")
                            .getString("record_hash") ==
                            inquiryDirectFresh.getString("record_hash") &&
                            SettlementRules.rows(inquiryMaster().getJSONArray("orders"))
                                .map { it.getString("id") }
                                .toSet() == inquiryInitialOrders,
                        "EISBAER_INQUIRY_FAILED_ACCEPTANCE_DOES_NOT_CREATE_ORDER",
                    )
                    val ignoredWriterArgs = JSONObject(inquiryDirectAcceptArgs.toString())
                    val ignoredOrderId =
                        RevierRules.newId("order", ignoredWriterArgs.getString("request_id"))
                    val beforeIgnoredSnapshot = PixelMcpWriteTools.hash(inquiryMaster())
                    fun pendingCount(): Int =
                        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM pending", null).use {
                            it.moveToFirst()
                            it.getInt(0)
                        }
                    val beforeIgnoredWriter = pendingCount()
                    val ignoreTrigger = "qa_eisbaer_direct_order_ignore"
                    store.writableDatabase.execSQL(
                        "CREATE TRIGGER $ignoreTrigger BEFORE INSERT ON pending " +
                            "WHEN NEW.kind='order' AND json_extract(NEW.payload,'$.id')='$ignoredOrderId' " +
                            "BEGIN SELECT RAISE(IGNORE); END"
                    )
                    try {
                        rejected(
                            "accept_inquiry_direct",
                            ignoredWriterArgs,
                            "DIRECT_ORDER_NOT_APPLIED",
                        )
                    } finally {
                        store.writableDatabase.execSQL("DROP TRIGGER $ignoreTrigger")
                    }
                    yes(
                        pendingCount() == beforeIgnoredWriter &&
                            PixelMcpWriteTools.hash(inquiryMaster()) == beforeIgnoredSnapshot &&
                            store
                                .ownerValue(
                                    RevierRules.ROOT +
                                        "request:" +
                                        PixelMcpWriteTools.hash(
                                            ignoredWriterArgs.getString("request_id")
                                        ),
                                    "",
                                )
                                .isEmpty() &&
                            call(
                                    "get_inquiry",
                                    JSONObject().put("inquiry_id", inquiryDirectInquiryId),
                                )
                                .getJSONObject("record")
                                .getString("record_hash") ==
                                inquiryDirectFresh.getString("record_hash") &&
                            SettlementRules.rows(inquiryMaster().getJSONArray("orders"))
                                .map { it.getString("id") }
                                .toSet() == inquiryInitialOrders &&
                            store.ownerValue(SettlementRules.KEY, "{}") == inquiryInitialLedger,
                        "EISBAER_INQUIRY_IGNORED_WRITER_ROLLS_BACK_WITHOUT_DANGLING_LINK_OR_COMMIT",
                    )
                    val inquiryDirectAccepted =
                        call("accept_inquiry_direct", inquiryDirectAcceptArgs)
                    val inquiryDirectOrderId = inquiryDirectAccepted.getString("order_id")
                    yes(
                        !inquiryDirectAccepted.getBoolean("invoice_created") &&
                            call("accept_inquiry_direct", inquiryDirectAcceptArgs)
                                .getBoolean("replayed"),
                        "EISBAER_INQUIRY_ACCEPTS_ONCE_WITHOUT_INVOICE",
                    )
                    yes(
                        inquiryDirectOrderId == ignoredOrderId &&
                            SettlementRules.rows(inquiryMaster().getJSONArray("orders")).count {
                                it.getString("id") == inquiryDirectOrderId
                            } == 1,
                        "EISBAER_INQUIRY_ACCEPTANCE_MATERIALIZES_ORIGINAL_BEFORE_LINK_READ",
                    )
                    val inquiryAcceptedInquiry =
                        call("get_inquiry", JSONObject().put("inquiry_id", inquiryDirectInquiryId))
                            .getJSONObject("record")
                    val inquiryDirectOrder =
                        call("get_order", JSONObject().put("order_id", inquiryDirectOrderId))
                    yes(
                        inquiryAcceptedInquiry.getString("status") == "accepted" &&
                            inquiryAcceptedInquiry.getString("order_id") == inquiryDirectOrderId &&
                            inquiryAcceptedInquiry.getString("acceptance_source") == "direct" &&
                            inquiryAcceptedInquiry.getString("acceptance_evidence") ==
                                inquiryDirectAcceptArgs.getString("evidence"),
                        "EISBAER_INQUIRY_ACCEPTANCE_EVIDENCE_AND_ORDER_REREAD",
                    )
                    yes(
                        inquiryDirectOrder.getString("id") == inquiryDirectOrderId &&
                            inquiryDirectOrder.getString("customer_id") == "qa_customer_1" &&
                            inquiryDirectOrder.getString("inquiry_id") == inquiryDirectInquiryId &&
                            inquiryDirectOrder.getString("property_id") == propertyId &&
                            inquiryDirectOrder.getString("billing_customer_id") ==
                                inquiryRecipientId &&
                            inquiryDirectOrder.getString("title") ==
                                inquiryDirectFresh.getString("title"),
                        "EISBAER_INQUIRY_ORDER_KEEPS_EXACT_CUSTOMER_AND_RECIPIENT",
                    )
                    rejected(
                        "accept_inquiry_direct",
                        JSONObject(inquiryDirectAcceptArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_accept_stale_after"),
                        "WRITE_CONFLICT",
                    )
                    rejected(
                        "accept_inquiry_direct",
                        JSONObject(inquiryDirectAcceptArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_accept_again")
                            .put(
                                "expected_record_hash",
                                inquiryAcceptedInquiry.getString("record_hash"),
                            ),
                        "ORDER_ALREADY_LINKED",
                    )
                    yes(
                        SettlementRules.rows(inquiryMaster().getJSONArray("orders"))
                            .map { it.getString("id") }
                            .toSet() == inquiryInitialOrders + inquiryDirectOrderId,
                        "EISBAER_INQUIRY_ONE_EXPLICIT_ORDER_NO_DUPLICATE",
                    )

                    phase = "eisbaer_mcp_inquiry_inspection"
                    val inquiryInspectionCreateArgs =
                        request("inquiry_inspection_create")
                            .put("customer_id", "qa_customer_1")
                            .put("title", "[TEST] QA MCP echte Besichtigungsplanung")
                    val inquiryInspectionInquiryId =
                        call("create_inquiry", inquiryInspectionCreateArgs)
                            .getJSONObject("record")
                            .getString("id")
                    val inquiryInspectionFresh =
                        call(
                                "get_inquiry",
                                JSONObject().put("inquiry_id", inquiryInspectionInquiryId),
                            )
                            .getJSONObject("record")
                    val inquiryInspectionArgs =
                        request("inquiry_inspection_first")
                            .put("inquiry_id", inquiryInspectionInquiryId)
                            .put(
                                "expected_record_hash",
                                inquiryInspectionFresh.getString("record_hash"),
                            )
                            .put("starts_at", "2027-06-02T08:00:00Z")
                            .put("ends_at", "2027-06-02T09:00:00Z")
                            .put("timezone", "Europe/Vienna")
                            .put("notes", "[TEST] QA Besichtigung bewusst geplant")
                    rejected(
                        "plan_inquiry_inspection",
                        JSONObject(inquiryInspectionArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_inspection_stale_before")
                            .put("expected_record_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    val inquiryInspectionPlanned =
                        call("plan_inquiry_inspection", inquiryInspectionArgs)
                    val inquiryInspectionAppointmentId =
                        inquiryInspectionPlanned.getString("appointment_id")
                    yes(
                        call("plan_inquiry_inspection", inquiryInspectionArgs)
                            .getBoolean("replayed") &&
                            inquiryInspectionPlanned.getString("external_calendar_status") ==
                                "unknown",
                        "EISBAER_INQUIRY_INSPECTION_REPLAY_NO_EXTERNAL_CONFIRMATION",
                    )
                    val inquiryInspectionAppointment =
                        call(
                            "get_appointment",
                            JSONObject().put("appointment_id", inquiryInspectionAppointmentId),
                        )
                    val inquiryInspectionOriginal =
                        inquiryInspectionAppointment.getJSONObject("appointment")
                    val inquiryInspectionLinked =
                        call(
                                "get_inquiry",
                                JSONObject().put("inquiry_id", inquiryInspectionInquiryId),
                            )
                            .getJSONObject("record")
                    yes(
                        inquiryInspectionOriginal.getString("id") ==
                            inquiryInspectionAppointmentId &&
                            inquiryInspectionOriginal.getString("customer_id") == "qa_customer_1" &&
                            inquiryInspectionOriginal.getString("purpose") == "inspection" &&
                            inquiryInspectionOriginal.getString("weather_profile") ==
                                "inspection" &&
                            inquiryInspectionOriginal.getString("starts_at") ==
                                "2027-06-02T08:00:00Z" &&
                            inquiryInspectionOriginal.getString("ends_at") ==
                                "2027-06-02T09:00:00Z",
                        "EISBAER_INQUIRY_INSPECTION_ACTUAL_APPOINTMENT_REREAD",
                    )
                    yes(
                        inquiryInspectionLinked.getString("status") == "inspection_planned" &&
                            inquiryInspectionLinked.getString("inspection_appointment_id") ==
                                inquiryInspectionAppointmentId,
                        "EISBAER_INQUIRY_INSPECTION_STABLE_LINK_REREAD",
                    )
                    rejected(
                        "plan_inquiry_inspection",
                        JSONObject(inquiryInspectionArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_inspection_stale_after"),
                        "WRITE_CONFLICT",
                    )
                    rejected(
                        "plan_inquiry_inspection",
                        JSONObject(inquiryInspectionArgs.toString())
                            .put("starts_at", "2027-06-02T08:30:00Z"),
                        "REPLAY_MISMATCH",
                    )
                    rejected(
                        "plan_inquiry_inspection",
                        JSONObject(inquiryInspectionArgs.toString())
                            .put("request_id", "qa_eisbaer_inquiry_inspection_requires_mode")
                            .put(
                                "expected_record_hash",
                                inquiryInspectionLinked.getString("record_hash"),
                            ),
                        "INSPECTION_ACTION_REQUIRED",
                    )
                    val inquiryRescheduleArgs =
                        request("inquiry_inspection_reschedule")
                            .put("inquiry_id", inquiryInspectionInquiryId)
                            .put(
                                "expected_record_hash",
                                inquiryInspectionLinked.getString("record_hash"),
                            )
                            .put("mode", "reschedule")
                            .put(
                                "expected_appointment_hash",
                                inquiryInspectionAppointment.getString("record_hash"),
                            )
                            .put("starts_at", "2027-06-03T08:00:00Z")
                            .put("ends_at", "2027-06-03T09:00:00Z")
                            .put("timezone", "Europe/Vienna")
                    rejected(
                        "plan_inquiry_inspection",
                        JSONObject(inquiryRescheduleArgs.toString())
                            .put(
                                "request_id",
                                "qa_eisbaer_inquiry_inspection_bad_appointment_proof",
                            )
                            .put("expected_appointment_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    yes(
                        call(
                                "get_appointment",
                                JSONObject().put("appointment_id", inquiryInspectionAppointmentId),
                            )
                            .getString("record_hash") ==
                            inquiryInspectionAppointment.getString("record_hash") &&
                            call(
                                    "get_inquiry",
                                    JSONObject().put("inquiry_id", inquiryInspectionInquiryId),
                                )
                                .getJSONObject("record")
                                .getString("record_hash") ==
                                inquiryInspectionLinked.getString("record_hash"),
                        "EISBAER_INQUIRY_INSPECTION_BAD_PROOF_PRESERVES_BOTH_ORIGINALS",
                    )
                    yes(
                        call("plan_inquiry_inspection", inquiryRescheduleArgs)
                            .getString("appointment_id") == inquiryInspectionAppointmentId &&
                            call("plan_inquiry_inspection", inquiryRescheduleArgs)
                                .getBoolean("replayed"),
                        "EISBAER_INQUIRY_INSPECTION_RESCHEDULE_SAME_ID_REPLAY",
                    )
                    val inquiryRescheduled =
                        call(
                                "get_appointment",
                                JSONObject().put("appointment_id", inquiryInspectionAppointmentId),
                            )
                            .getJSONObject("appointment")
                    yes(
                        inquiryRescheduled.getString("starts_at") == "2027-06-03T08:00:00Z" &&
                            inquiryRescheduled.getString("ends_at") == "2027-06-03T09:00:00Z" &&
                            call(
                                    "get_inquiry",
                                    JSONObject().put("inquiry_id", inquiryInspectionInquiryId),
                                )
                                .getJSONObject("record")
                                .getString("inspection_appointment_id") ==
                                inquiryInspectionAppointmentId,
                        "EISBAER_INQUIRY_INSPECTION_RESCHEDULE_ACTUAL_REREAD",
                    )
                    yes(
                        SettlementRules.rows(inquiryMaster().getJSONArray("appointments"))
                            .map { it.getString("id") }
                            .toSet() == inquiryInitialAppointments + inquiryInspectionAppointmentId,
                        "EISBAER_INQUIRY_INSPECTION_EXACTLY_ONE_APPOINTMENT",
                    )
                    yes(
                        PixelMcpWriteTools.hash(inquiryMaster().getJSONArray("worklogs")) ==
                            inquiryInitialWorks &&
                            PixelMcpWriteTools.hash(inquiryMaster().getJSONArray("invoices")) ==
                                inquiryInitialInvoices &&
                            PixelMcpWriteTools.hash(inquiryExistingOriginals()) ==
                                inquiryOriginalHash &&
                            store.ownerValue(SettlementRules.KEY, "{}") == inquiryInitialLedger,
                        "EISBAER_INQUIRY_ALL_PREEXISTING_ORIGINALS_UNCHANGED_NO_WORK_OR_INVOICE",
                    )

                    val inquiryListing =
                        call(
                            "list_inquiries",
                            JSONObject().put("customer_id", "qa_customer_1").put("query", "QA MCP"),
                        )
                    yes(
                        inquiryListing.getInt("total") == 2 &&
                            ids(inquiryListing.getJSONArray("items")).toSet() ==
                                setOf(inquiryDirectInquiryId, inquiryInspectionInquiryId),
                        "EISBAER_REVIER_LIST_INQUIRIES_PUBLIC_CUSTOMER_QUERY",
                    )
                    val acceptedListing =
                        call(
                            "list_inquiries",
                            JSONObject()
                                .put("customer_id", "qa_customer_1")
                                .put("status", "accepted"),
                        )
                    yes(
                        ids(acceptedListing.getJSONArray("items")) ==
                            listOf(inquiryDirectInquiryId),
                        "EISBAER_REVIER_LIST_INQUIRIES_PUBLIC_STATUS",
                    )

                    phase = "eisbaer_revier_legacy_work_alias"
                    val workArgs =
                        request("legacy_work_create")
                            .put("order_id", "qa_order_1")
                            .put("work_date", "2027-06-22")
                            .put("activity", "[TEST] QA öffentlicher alter Arbeitsname")
                            .put("hours", 1.25)
                    val workCreated = call("create_work_record", workArgs)
                    val workId = workCreated.getString("work_record_id")
                    yes(
                        call("create_work_record", workArgs).getBoolean("replayed"),
                        "EISBAER_REVIER_CREATE_WORK_ALIAS_REPLAY",
                    )
                    val aliasWork =
                        call("get_work_record", JSONObject().put("work_record_id", workId))
                    yes(
                        aliasWork.getString("id") == workId &&
                            aliasWork.getString("customer_id") == "qa_customer_1" &&
                            aliasWork.getString("order_id") == "qa_order_1" &&
                            kotlin.math.abs(aliasWork.getDouble("hours") - 1.25) < 0.0000001 &&
                            ids(master().getJSONArray("worklogs")).toSet() ==
                                baselineWorks + workId,
                        "EISBAER_REVIER_CREATE_WORK_ALIAS_ORIGINAL_REREAD_ONCE",
                    )
                    rejected(
                        "create_work_record",
                        JSONObject(workArgs.toString())
                            .put("activity", "[TEST] QA anderer Inhalt unter gleicher Kennung"),
                        "REPLAY_MISMATCH",
                    )

                    phase = "eisbaer_revier_tour_move_alias"
                    val tourDay = "2027-06-22"
                    fun visit(suffix: String): String =
                        call(
                                "plan_visit",
                                request("tour_visit_$suffix")
                                    .put("property_id", propertyId)
                                    .put("title", "[TEST] QA MCP Tourstopp $suffix")
                                    .put("date", tourDay)
                                    .put("planned_minutes", 30),
                            )
                            .getString("visit_id")
                    val firstVisit = visit("a")
                    val secondVisit = visit("b")
                    val firstProof =
                        call("get_visit", JSONObject().put("visit_id", firstVisit))
                            .getString("record_hash")
                    val secondProof =
                        call("get_visit", JSONObject().put("visit_id", secondVisit))
                            .getString("record_hash")
                    val tourBefore = call("get_day_tour", JSONObject().put("date", tourDay))
                    call(
                        "set_day_tour",
                        request("initial_tour")
                            .put("date", tourDay)
                            .put("visit_ids", JSONArray().put(firstVisit).put(secondVisit))
                            .put("expected_record_hash", tourBefore.getString("record_hash")),
                    )
                    val ordered = call("get_day_tour", JSONObject().put("date", tourDay))
                    val moveArgs =
                        request("move_tour")
                            .put("date", tourDay)
                            .put("visit_id", secondVisit)
                            .put("direction", "up")
                            .put("expected_record_hash", ordered.getString("record_hash"))
                    call("move_tour_stop", moveArgs)
                    yes(
                        call("move_tour_stop", moveArgs).getBoolean("replayed"),
                        "EISBAER_REVIER_MOVE_TOUR_REPLAY",
                    )
                    rejected(
                        "move_tour_stop",
                        JSONObject(moveArgs.toString())
                            .put("request_id", "qa_eisbaer_revier_move_tour_stale"),
                        "WRITE_CONFLICT",
                    )
                    val moved = call("get_day_tour", JSONObject().put("date", tourDay))
                    val movedIds = moved.getJSONObject("tour").getJSONArray("visit_ids")
                    yes(
                        (0 until movedIds.length()).map(movedIds::getString) ==
                            listOf(secondVisit, firstVisit) &&
                            ids(moved.getJSONArray("stops")) == listOf(secondVisit, firstVisit),
                        "EISBAER_REVIER_MOVE_TOUR_PUBLIC_ORDER_REREAD",
                    )
                    yes(
                        call("get_visit", JSONObject().put("visit_id", firstVisit))
                            .getString("record_hash") == firstProof &&
                            call("get_visit", JSONObject().put("visit_id", secondVisit))
                                .getString("record_hash") == secondProof &&
                            ids(master().getJSONArray("worklogs")).toSet() ==
                                baselineWorks + workId,
                        "EISBAER_REVIER_TOUR_MOVE_KEEPS_ORIGINALS_AND_CREATES_NO_WORK",
                    )

                    phase = "eisbaer_revier_material_legacy_alias"
                    val material =
                        call(
                                "save_material",
                                request("alias_material")
                                    .put("name", "[TEST] QA Verbrauchsmaterial")
                                    .put("unit", "Sack")
                                    .put("billing_price_minor", 250),
                            )
                            .getJSONObject("record")
                    val materialId = material.getString("id")
                    val movementArgs =
                        request("legacy_material_consume")
                            .put("material_id", materialId)
                            .put("worklog_id", workId)
                            .put("quantity_decimal", "2")
                            .put("kind", "consume")
                            .put("expected_record_hash", material.getString("record_hash"))
                    val consumed =
                        call("record_material_movement", movementArgs).getJSONObject("movement")
                    yes(
                        call("record_material_movement", movementArgs).getBoolean("replayed"),
                        "EISBAER_REVIER_MATERIAL_ALIAS_REPLAY",
                    )
                    val rereadMaterial =
                        SettlementRules.rows(call("list_materials").getJSONArray("items")).single {
                            it.getString("id") == materialId
                        }
                    yes(
                        rereadMaterial.getInt("history_total") == 1 &&
                            rereadMaterial
                                .getJSONArray("movements")
                                .getJSONObject(0)
                                .getString("id") == consumed.getString("id") &&
                            consumed.getString("worklog_id") == workId &&
                            consumed.getString("quantity_decimal") == "2" &&
                            !rereadMaterial.has("stock_decimal"),
                        "EISBAER_REVIER_MATERIAL_ALIAS_READS_ONE_REAL_CONSUMPTION",
                    )
                    rejected(
                        "record_material_movement",
                        JSONObject(movementArgs.toString()).put("quantity_decimal", "3"),
                        "REPLAY_MISMATCH",
                    )
                    rejected(
                        "record_material_movement",
                        JSONObject(movementArgs.toString())
                            .put("request_id", "qa_eisbaer_revier_material_stale")
                            .put("expected_record_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    rejected(
                        "record_material_movement",
                        JSONObject(movementArgs.toString())
                            .put("request_id", "qa_eisbaer_revier_retired_inventory")
                            .put("kind", "receive"),
                        "INVALID_INPUT",
                    )
                    yes(
                        SettlementRules.rows(call("list_materials").getJSONArray("items"))
                            .single { it.getString("id") == materialId }
                            .getInt("history_total") == 1,
                        "EISBAER_REVIER_MATERIAL_ALIAS_REJECTIONS_DO_NOT_ADD_MOVEMENTS",
                    )

                    phase = "eisbaer_revier_permanent_purge"
                    // Revier's contract previews the exact original with get_property +
                    // record_hash;
                    // preview_delete is intentionally a separate contract for core entities only.
                    val disposable =
                        call(
                                "create_property",
                                request("purge_disposable")
                                    .put("customer_id", "qa_customer_1")
                                    .put("name", "[TEST] QA ausschließlich löschbares Testobjekt"),
                            )
                            .getJSONObject("record")
                    val disposableId = disposable.getString("id")
                    val purgePreview = property(disposableId)
                    val purgeArgs =
                        request("permanent_purge")
                            .put("entity", "property")
                            .put("entity_id", disposableId)
                            .put("expected_record_hash", purgePreview.getString("record_hash"))
                            .put("confirmed", true)
                            .put("reason", "[TEST] QA eigenes unverbundenes Testobjekt entfernen")
                    rejected(
                        "purge_revier_entity",
                        JSONObject(purgeArgs.toString())
                            .put("request_id", "qa_eisbaer_revier_purge_not_confirmed")
                            .put("confirmed", false),
                        "CONFIRMATION_REQUIRED",
                    )
                    rejected(
                        "purge_revier_entity",
                        JSONObject(purgeArgs.toString())
                            .put("request_id", "qa_eisbaer_revier_purge_stale")
                            .put("expected_record_hash", "0".repeat(64)),
                        "WRITE_CONFLICT",
                    )
                    yes(
                        property(disposableId).getString("record_hash") ==
                            purgePreview.getString("record_hash"),
                        "EISBAER_REVIER_PURGE_REJECTIONS_KEEP_ORIGINAL",
                    )
                    yes(
                        call("purge_revier_entity", purgeArgs).getBoolean("purged"),
                        "EISBAER_REVIER_PERMANENT_PURGE_ACTUALLY_APPLIED",
                    )
                    yes(
                        call("purge_revier_entity", purgeArgs).getBoolean("replayed"),
                        "EISBAER_REVIER_PURGE_REPLAY",
                    )
                    rejected(
                        "get_property",
                        JSONObject().put("property_id", disposableId),
                        "ENTITY_DELETED",
                    )
                    val survivingProperties = call("list_properties").getJSONArray("items")
                    yes(
                        disposableId !in ids(survivingProperties) &&
                            propertyId in ids(survivingProperties) &&
                            protectedProperty.getString("id") in ids(survivingProperties),
                        "EISBAER_REVIER_PURGE_NO_RESURRECTION_OR_COLLATERAL_DELETE",
                    )
                    val protectedPreview = property(protectedProperty.getString("id"))
                    rejected(
                        "purge_revier_entity",
                        request("purge_protected_original")
                            .put("entity", "property")
                            .put("entity_id", protectedProperty.getString("id"))
                            .put("expected_record_hash", protectedPreview.getString("record_hash"))
                            .put("confirmed", true),
                        "DEPENDENCY_EXISTS",
                    )
                    yes(
                        legacyOriginals() == beforeLegacy &&
                            property(protectedProperty.getString("id")).getString("record_hash") ==
                                protectedPreview.getString("record_hash"),
                        "EISBAER_REVIER_PURGE_PROTECTS_HISTORICAL_ORIGINALS",
                    )

                    JSONObject()
                        .put("executed_tools", JSONArray(executed.sorted()))
                        .put("successful_tools", JSONArray(positive.sorted()))
                        .put("transition_tools", JSONArray(transitions.sorted()))
                        .put("public_adapter_and_real_sqlite", true)
                        .put("synthetic_only", true)
                        .put("inquiry_acceptance_inspection_roundtrip", true)
                        .put("inquiry_writer_rollback", true)
                        .put("work_alias_and_tour_move_roundtrip", true)
                        .put("property_update_list_and_permanent_purge", true)
                        .put("legacy_material_consumption_alias", true)
                        .put("legacy_originals_preserved", true)
                        .put("calendar_provider_success_claimed", false)
                }
        } finally {
            context.clearPreferences()
            yes(root.deleteRecursively(), "EISBAER_REVIER_ROOT_REMOVED")
        }
        return report.put("checks", checks - beforeChecks)
    }

    private fun localStorageCleanup(owner: Context) {
        val token = UUID.randomUUID().toString().replace("-", "")
        val root = File(owner.cacheDir, "qa-local-cleanup-$token")
        yes(!root.exists() && root.mkdir(), "CLEANUP_NEW_ROOT")
        val context = FixtureContext(owner, root, "qa_cleanup_$token")
        fun file(
            directory: String,
            name: String,
            content: ByteArray = ByteArray(8192) { 7 },
        ): File =
            File(File(context.filesDir, directory).apply { mkdirs() }, name).apply {
                writeBytes(content)
            }
        val issued = file("business-documents", "qa-issued.pdf")
        val draft = file("business-documents", "qa-draft.pdf")
        val lost = file("customer-photo-originals", "qa-unused.jpg")
        val browser = file("browser-media", "a".repeat(64) + ".blob")
        val pending = file("pending-work-photo-files", "b".repeat(64) + ".jpg")
        val staged = file("native-form-media", "qa-form.jpg")
        staged.setLastModified(0)
        File(context.filesDir, "pending-work-photo.json")
            .writeText(JSONObject().put("sha256", "b".repeat(64)).toString())
        val fixture = SyntheticBusinessData.profile("minimal")
        fixture
            .getJSONArray("invoices")
            .put(
                JSONObject()
                    .put("id", "qa_issued")
                    .put("invoice_number", "QA-001")
                    .put("issued_at", "2026-10-30T10:00:00Z")
                    .put("customer_id", "qa_customer_1")
                    .put("pdf_storage_name", issued.name)
            )
        fixture
            .getJSONArray("invoices")
            .put(
                JSONObject()
                    .put("id", "qa_draft")
                    .put("invoice_number", "Entwurf")
                    .put("status", "Entwurf")
                    .put("customer_id", "qa_customer_1")
                    .put("pdf_storage_name", draft.name)
            )
        fixture
            .getJSONArray("appointments")
            .put(
                JSONObject()
                    .put("id", "qa_appointment")
                    .put("customer_id", "qa_customer_1")
                    .put("order_id", "qa_order_1")
                    .put("title", "QA Erledigter Termin")
                    .put("status", "completed")
            )
        fixture.getJSONArray("worklogs").getJSONObject(0).put("appointment_id", "qa_appointment")
        val linkedInvoice = JSONObject(fixture.toString())
        linkedInvoice
            .getJSONArray("invoices")
            .getJSONObject(0)
            .put("worklog_ids", JSONArray().put("qa_work_1"))
        var protected = false
        try {
            OwnerJournalRules.validateArchive(
                "order",
                linkedInvoice.getJSONArray("orders").getJSONObject(0),
                linkedInvoice,
            )
        } catch (_: IllegalArgumentException) {
            protected = true
        }
        yes(protected, "ISSUED_WORK_PROTECTS_PARENT_ORDER")
        context
            .getSharedPreferences("haus_garten", 0)
            .edit()
            .putString("drive_snapshot", fixture.toString())
            .commit()
        lateinit var keptPhoto: File
        PixelLocalStore(context).use { store ->
            val db = store.writableDatabase
            store.putOwnerValue("business_tables:original:qa_obsolete", fixture.toString())
            store.putOwnerValue(
                "browser_media:v1:record:qa",
                JSONObject().put("graph", JSONObject().put("sha256", "a".repeat(64))).toString(),
            )
            store.putOwnerValue(
                "native-form:qa",
                JSONObject().put("uri", staged.toURI().toString()).toString(),
            )
            store.putOwnerValue(
                "revier:v1:entity:inquiry:qa_inspection",
                JSONObject()
                    .put("format_version", 1)
                    .put("kind", "inquiry")
                    .put("id", "qa_inspection")
                    .put("customer_id", "qa_customer_1")
                    .put("inspection_appointment_id", "qa_appointment")
                    .put(
                        "inspection_visits",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("appointment_id", "qa_appointment")
                                    .put("notes", "QA Befund erhalten")
                            ),
                    )
                    .toString(),
            )
            db.execSQL(
                "INSERT INTO mcp_uploads VALUES('qa_old_upload', '{}', ?)",
                arrayOf(ByteArray(1024 * 1024)),
            )
            db.execSQL(
                "INSERT INTO mcp_uploads VALUES('qa_retained_backup', '{\"kind\":\"backup\"}', ?)",
                arrayOf(ByteArray(4096) { 42 }),
            )
            val bitmap =
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val encoded =
                java.io.ByteArrayOutputStream().use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
            bitmap.recycle()
            fun photo(request: String, order: String = "qa_order_1"): String =
                store.savePhoto(
                    JSONObject()
                        .put("customer_id", "qa_customer_1")
                        .apply { if (order.isNotEmpty()) put("order_id", order) }
                        .put("_photo_request_id", request)
                        .put(
                            "_capture_context",
                            JSONObject()
                                .put("installation_id", "old-device")
                                .put("restore_epoch", "old-epoch"),
                        )
                        .put(
                            "data",
                            "data:image/png;base64," +
                                android.util.Base64.encodeToString(
                                    encoded,
                                    android.util.Base64.NO_WRAP,
                                ),
                        )
                )
            var first = photo("qa_photo_cleanup_first")
            var second = photo("qa_photo_cleanup_second")
            val access = PixelOwnerAccess(context, store)
            var sequence = 0
            fun command(name: String, entity: String, id: String): JSONObject {
                require(name == "delete_entity")
                val proof =
                    requireNotNull(
                        access.query(
                            "preview_delete",
                            JSONObject().put("entity", entity).put("entity_id", id),
                        )
                    )
                return access.execute(
                    name,
                    JSONObject()
                        .put("request_id", "qa_cleanup_${sequence++}")
                        .put("entity", entity)
                        .put("entity_id", id)
                        .put("expected_record_hash", proof.getString("expected_record_hash"))
                        .put(
                            "expected_photo_links_hash",
                            proof.getString("expected_photo_links_hash"),
                        ),
                )
            }
            fun staleDeletion(name: String, args: JSONObject) {
                val proof =
                    requireNotNull(
                        access.query(
                            "preview_delete",
                            JSONObject()
                                .put("entity", args.getString("entity"))
                                .put("entity_id", args.getString("entity_id")),
                        )
                    )
                if (!args.has("expected_record_hash"))
                    args.put("expected_record_hash", proof.getString("expected_record_hash"))
                if (!args.has("expected_photo_links_hash"))
                    args.put(
                        "expected_photo_links_hash",
                        proof.getString("expected_photo_links_hash"),
                    )
                val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
                var rejected = false
                try {
                    access.execute(name, args.put("request_id", "qa_stale_delete_${sequence++}"))
                } catch (failure: PixelMcpWriteTools.Rejected) {
                    rejected = failure.code == "WRITE_CONFLICT"
                }
                yes(rejected, "STALE_DELETE_PROOF_REJECTED_$name")
                yes(
                    before ==
                        PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())),
                    "STALE_DELETE_PRESERVES_DATA_$name",
                )
            }
            staleDeletion(
                "delete_entity",
                JSONObject()
                    .put("entity", "order")
                    .put("entity_id", "qa_order_1")
                    .put("expected_record_hash", "0".repeat(64)),
            )
            staleDeletion(
                "delete_entity",
                JSONObject()
                    .put("entity", "order")
                    .put("entity_id", "qa_order_1")
                    .put("expected_photo_links_hash", "0".repeat(64)),
            )
            command("delete_entity", "order", "qa_order_1")
            var current = PixelMcpReadTools.effective(store.getState())
            yes(current.getJSONArray("orders").length() == 0, "ORDER_REMOVED_WITH_WORK_PRESERVED")
            yes(
                current.getJSONArray("worklogs").getJSONObject(0).optString("order_id").isEmpty(),
                "WORK_ORDER_DETACHED",
            )
            yes(
                current.getJSONArray("worklogs").getJSONObject(0).getString("customer_id") ==
                    "qa_customer_1",
                "WORK_CUSTOMER_PRESERVED",
            )
            yes(
                !OwnerJournalRules.archives(PixelMcpReadTools.effective(store.getState()))
                    .has("order:qa_order_1"),
                "NO_ORDER_TOMBSTONE",
            )
            // Owner decision 03.11.2026: purging marked test data also removes the photos it
            // detached.
            val photosAfterOrder =
                PixelMcpReadTools.effective(store.getState()).getJSONArray("photos")
            yes(
                listOf(first, second).all {
                    OwnerJournalRules.findOrNull(photosAfterOrder, it) == null &&
                        store.photoAttachment(it) == null
                },
                "TEST_ORDER_PURGE_REMOVES_ITS_PHOTOS",
            )
            first = photo("qa_photo_cleanup_kept_first", "")
            second = photo("qa_photo_cleanup_kept_second", "")
            command("delete_entity", "appointment", "qa_appointment")
            yes(
                PixelMcpReadTools.effective(store.getState())
                    .getJSONArray("worklogs")
                    .getJSONObject(0)
                    .optString("appointment_id")
                    .isEmpty(),
                "COMPLETED_WORK_APPOINTMENT_DETACHED",
            )
            val inspection =
                JSONObject(store.ownerValue("revier:v1:entity:inquiry:qa_inspection", "{}"))
            yes(
                inspection.optString("inspection_appointment_id").isEmpty() &&
                    inspection
                        .getJSONArray("inspection_visits")
                        .getJSONObject(0)
                        .optString("appointment_id")
                        .isEmpty(),
                "INSPECTION_REFERENCES_DETACHED",
            )
            yes(
                inspection.optString("customer_id") == "qa_customer_1" &&
                    inspection
                        .getJSONArray("inspection_visits")
                        .getJSONObject(0)
                        .optString("notes") == "QA Befund erhalten",
                "INSPECTION_HISTORY_PRESERVED",
            )
            val metadata = store.photoAttachment(first)!!
            keptPhoto =
                File(
                    File(context.filesDir, "customer-photo-originals"),
                    metadata.getString("storage_name"),
                )
            store.putOwnerValue(
                "receipt:qa_deleted_photo",
                JSONObject().put("result", JSONObject().put("photo_id", first)).toString(),
            )
            db.execSQL(
                "INSERT INTO pending VALUES('receipt_qa_deleted_photo','mcp_receipt',?,'2026-10-30T00:00:00Z')",
                arrayOf(
                    JSONObject().put("entity_type", "photo").put("entity_id", first).toString()
                ),
            )
            command("delete_entity", "photo", first)
            yes(keptPhoto.isFile, "ACTIVE_SHARED_PHOTO_RETAINED")
            val removedReceipt = JSONObject(store.ownerValue("receipt:qa_deleted_photo", "{}"))
            yes(
                removedReceipt.getJSONObject("result").optBoolean("_result_content_removed") &&
                    !removedReceipt.getJSONObject("result").has("photo_id") &&
                    store.ownerValue("photo-ui:qa_photo_cleanup_kept_first", "").isEmpty(),
                "DELETED_PHOTO_CONTENT_REMOVED",
            )
            db.rawQuery("SELECT count(*) FROM pending WHERE id='receipt_qa_deleted_photo'", null)
                .use { rows ->
                    yes(rows.moveToFirst() && rows.getInt(0) == 0, "DELETED_NATIVE_RECEIPT_REMOVED")
                }
            command("delete_entity", "invoice", "qa_draft")
            yes(!draft.exists() && issued.isFile, "DRAFT_PDF_REMOVED_ISSUED_RETAINED")
            var rejected = false
            try {
                command("delete_entity", "invoice", "qa_issued")
            } catch (_: Exception) {
                rejected = true
            }
            yes(rejected && issued.isFile, "ISSUED_INVOICE_PROTECTED")
            val orphan =
                File(context.noBackupFilesDir, "pixel-backup/attempt-abandoned/capture/large.bin")
            orphan.parentFile!!.mkdirs()
            orphan.writeBytes(ByteArray(16384))
            val oldDeletion = File(context.noBackupFilesDir, "deletion-backups/qa-orphan.zip")
            oldDeletion.parentFile!!.mkdirs()
            oldDeletion.writeBytes(ByteArray(8192))
            val maintenance = LocalStorageMaintenance(context, store)
            val cleaned = maintenance.clean(true)
            yes(
                cleaned.getLong("database_bytes") > 0 && cleaned.getLong("freed_bytes") > 0,
                "ACTUAL_USAGE_AND_RECLAIM",
            )
            yes(!orphan.exists() && !lost.exists(), "ORPHANS_REMOVED")
            yes(
                !oldDeletion.exists() && !oldDeletion.parentFile!!.exists(),
                "RETIRED_BACKUP_TREE_REMOVED",
            )
            db.rawQuery("SELECT count(*) FROM mcp_uploads WHERE id='qa_retained_backup'", null)
                .use { rows ->
                    yes(rows.moveToFirst() && rows.getInt(0) == 0, "RETIRED_BACKUP_BLOB_REMOVED")
                }
            yes(
                pending.isFile && staged.isFile && keptPhoto.isFile && issued.isFile,
                "ALL_LIVE_REFERENCES_RETAINED",
            )
            yes(!browser.exists(), "OLD_WEBVIEW_DRAFT_PHOTO_RELEASED")
            db.rawQuery("SELECT count(*) FROM owner_state WHERE key GLOB 'browser_media:*'", null)
                .use { rows ->
                    yes(rows.moveToFirst() && rows.getInt(0) == 0, "OLD_WEBVIEW_ENTRIES_REMOVED")
                }
            yes(
                !context.getSharedPreferences("haus_garten", 0).contains("drive_snapshot"),
                "LEGACY_PREFERENCES_REMOVED",
            )
            db.rawQuery(
                    "SELECT count(*) FROM owner_state WHERE key GLOB 'business_tables:original:*' OR key GLOB 'receipt_guard:*'",
                    null,
                )
                .use { rows ->
                    yes(rows.moveToFirst() && rows.getInt(0) == 0, "OLD_DUPLICATES_REMOVED")
                }
            db.rawQuery("SELECT count(*) FROM mcp_uploads WHERE id='qa_old_upload'", null).use {
                rows ->
                yes(rows.moveToFirst() && rows.getInt(0) == 0, "ORPHAN_UPLOAD_BLOB_REMOVED")
            }
            command("delete_entity", "photo", second)
            yes(!keptPhoto.exists(), "LAST_PHOTO_REFERENCE_RECLAIMED_IMMEDIATELY")
            current = PixelMcpReadTools.effective(store.getState())
            yes(
                current.getJSONArray("worklogs").length() == 1 &&
                    current.getJSONArray("invoices").length() == 1,
                "BUSINESS_ROWS_PRESERVED",
            )
            yes(OwnerJournalRules.archives(current).length() == 0, "DELETIONS_KEEP_NO_TOMBSTONES")
            val directWork =
                JSONObject(current.getJSONArray("worklogs").getJSONObject(0).toString())
                    .put("id", "qa_direct_work")
                    .put("billed", 0)
            current.getJSONArray("worklogs").put(directWork)
            store.putOwnerValue("snapshot", current.toString())
            val directRemoved =
                access.execute(
                    "delete_entity",
                    JSONObject()
                        .put("request_id", "qa_direct_work_delete")
                        .put("entity", "worklog")
                        .put("entity_id", "qa_direct_work")
                        .put("expected_record_hash", OwnerJournalRules.version(directWork)),
                )
            val afterDirect = PixelMcpReadTools.effective(store.getState())
            yes(
                !(directRemoved.optJSONObject("data") ?: directRemoved).optBoolean(
                    "recoverable",
                    true,
                ),
                "DIRECT_WORK_NOT_RECOVERABLE " + directRemoved,
            )
            yes(
                OwnerJournalRules.findOrNull(
                    afterDirect.getJSONArray("worklogs"),
                    "qa_direct_work",
                ) == null,
                "DIRECT_WORK_REMOVED",
            )
            yes(
                !OwnerJournalRules.archives(afterDirect).has("worklog:qa_direct_work"),
                "DIRECT_WORK_WITHOUT_TRASH",
            )
            val red =
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            red.eraseColor(android.graphics.Color.RED)
            val directBytes =
                java.io.ByteArrayOutputStream().use { output ->
                    red.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
            red.recycle()
            val directPhotoId =
                store.savePhoto(
                    JSONObject()
                        .put("customer_id", "qa_customer_1")
                        .put("_photo_request_id", "qa_direct_photo")
                        .put(
                            "data",
                            "data:image/png;base64," +
                                android.util.Base64.encodeToString(
                                    directBytes,
                                    android.util.Base64.NO_WRAP,
                                ),
                        )
                )
            val directPhoto =
                OwnerJournalRules.findOrNull(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("photos"),
                    directPhotoId,
                )!!
            val directFile =
                File(
                    File(context.filesDir, "customer-photo-originals"),
                    store.photoAttachment(directPhotoId)!!.getString("storage_name"),
                )
            yes(directFile.isFile, "DIRECT_PHOTO_EXISTS_BEFORE_DELETE")
            access.execute(
                "delete_entity",
                JSONObject()
                    .put("request_id", "qa_direct_photo_delete")
                    .put("entity", "photo")
                    .put("entity_id", directPhotoId)
                    .put("expected_record_hash", OwnerJournalRules.version(directPhoto)),
            )
            val afterPhoto = PixelMcpReadTools.effective(store.getState())
            yes(
                !directFile.exists() &&
                    OwnerJournalRules.findOrNull(
                        afterPhoto.getJSONArray("photos"),
                        directPhotoId,
                    ) == null &&
                    !OwnerJournalRules.archives(afterPhoto).has("photo:$directPhotoId"),
                "DIRECT_PHOTO_AND_FILE_PERMANENTLY_REMOVED",
            )
        }
        PixelLocalStore(context).use { reopened ->
            val current = PixelMcpReadTools.effective(reopened.getState())
            yes(
                current.getJSONArray("customers").length() == 1 &&
                    current.getJSONArray("worklogs").length() == 1 &&
                    current.getJSONArray("invoices").length() == 1,
                "COMPACTED_REOPEN_EXACT_ROWS",
            )
            yes(
                current.getJSONArray("photos").length() == 0 &&
                    current.getJSONArray("orders").length() == 0 &&
                    OwnerJournalRules.archives(current).length() == 0,
                "DELETED_ROWS_DO_NOT_REAPPEAR",
            )
        }
    }

    /**
     * Office performance covers retained work-record reads; no invoice ledger exists in this build.
     */
    private suspend fun officeReadMeasurements(
        context: Context,
        store: PixelLocalStore,
        data: NativeDataRepository,
    ): JSONObject {
        val scope = "ISOLATED_NATIVE_WORK_RECORD_READS;NO_UI_LATENCY"
        repeat(2) { data.list("worklogs", limit = 50) }
        val values = ArrayList<Double>()
        repeat(40) {
            val start = System.nanoTime()
            data.list("worklogs", limit = 50)
            values.add((System.nanoTime() - start) / 1_000_000.0)
        }
        val sorted = values.sorted()
        return JSONObject()
            .put("scope", scope)
            .put("samples_per_path", values.size)
            .put(
                "billing_work_records",
                JSONObject()
                    .put("mean_ms", values.average())
                    .put("p95_ms", sorted[37])
                    .put("samples_ms", JSONArray(values)),
            )
    }

    private fun longNote(): String = "QA synthetic 🌿 äß ".repeat(7000)

    private fun scaleFixture(): JSONObject {
        val fixture = SyntheticBusinessData.profile("minimal")
        val customers = JSONArray()
        for (i in 0 until 400) customers.put(
            JSONObject()
                .put("id", "qa_customer_$i")
                .put("name", "QA Musterkunde $i")
                .put("street", "QA Synthetic Street $i")
                .put("active", if (i % 7 == 0) 0 else 1)
                .put("test_record", true)
        )
        val orders = JSONArray()
        for (i in 0 until 800) orders.put(
            JSONObject()
                .put("id", "qa_order_$i")
                .put("title", "QA order $i")
                .put("customer_id", "qa_customer_${i % 400}")
                .put("status", if (i % 3 == 0) "Geplant" else "Aktiv")
                .put("test_record", true)
        )
        val work = JSONArray()
        for (i in 0 until 38_800) work.put(
            JSONObject()
                .put("id", "qa_work_" + i.toString().padStart(5, '0'))
                .put("customer_id", "qa_customer_${i % 400}")
                .put("order_id", "qa_order_${i % 800}")
                .put("property_id", if (i % 5 == 0) "other" else "synthetic_property")
                .put("work_date", "2026-10-" + (i % 28 + 1).toString().padStart(2, '0'))
                .put("activity", "QA synthetic $i")
                .put("hours", 1)
                .put("billed", i % 2)
                .put("test_record", true)
                .apply { if (i == 0) put("notes", longNote()) }
        )
        return fixture.put("customers", customers).put("orders", orders).put("worklogs", work)
    }

    private fun mcpParity(store: PixelLocalStore, fixture: JSONObject) {
        for ((collection, kind, tool) in
            listOf(
                Triple("customers", "customer", "search_customers"),
                Triple("orders", "order", "search_orders"),
                Triple("worklogs", "worklog", "list_work_records"),
            )) {
            for (filter in
                listOf(
                    JSONObject().put("offset", 3).put("limit", 7),
                    JSONObject().put("query", "QA 2").put("limit", 13),
                )) {
                val actual =
                    store
                        .readSource()
                        .stateFor(tool, filter)
                        .getJSONObject("master")
                        .getJSONArray(collection)
                val expected =
                    SnapshotReadSupport.select(
                        fixture.getJSONArray(collection),
                        filter,
                        kind,
                        fixture,
                    )
                yes(
                    PixelMcpWriteTools.hash(actual) == PixelMcpWriteTools.hash(expected),
                    "MCP_FILTER_$collection",
                )
            }
        }
    }

    private suspend fun receiptProof(store: PixelLocalStore, data: NativeDataRepository) {
        val request = "qa_native_receipt_request"
        val args = JSONObject().put("request_id", request).put("activity", "QA synthetic")
        val receipt =
            JSONObject()
                .put("tool", "create_work_record")
                .put(
                    "fingerprint",
                    PixelMcpWriteTools.hash(
                        JSONObject().put("tool", "create_work_record").put("args", args)
                    ),
                )
                .put("entity_type", "worklog")
                .put("entity_id", "qa_work_1")
                .put("work_record_id", "qa_work_1")
        val revision = data.diagnostics().getLong("rebuild_count")
        store.writableDatabase.execSQL(
            "INSERT INTO pending(id,kind,payload,created_at) VALUES(?,?,?,?)",
            arrayOf(
                "receipt_mcp_pixel_" + PixelMcpWriteTools.hash(request).substring(0, 40),
                "mcp_receipt",
                receipt.toString(),
                "2026-10-29T00:00:00Z",
            ),
        )
        yes(
            PixelMcpWriteTools.hash(RevierLocalRepository(store, null).nativeReceipt(request)!!) ==
                PixelMcpWriteTools.hash(receipt),
            "NATIVE_RECEIPT_UNCHANGED",
        )
        yes(store.lookupNativeReceipt("qa_missing_receipt") == null, "MISSING_RECEIPT_NO_REPLAY")
        yes(
            data.diagnostics().getLong("rebuild_count") == revision,
            "RECEIPT_EXCLUDED_FROM_CACHE_INVALIDATION",
        )
    }

    private suspend fun transferCacheProof(store: PixelLocalStore, data: NativeDataRepository) {
        val before = data.list("customers", limit = 5)
        val source = JSONObject(store.snapshotText(""))
        val tables = store.businessTables()
        val request = "qa_native_cache_transfer"
        tables.prepare(request, PixelMcpWriteTools.hash(source)) {
            PixelMcpWriteTools.hash("QA synthetic transfer evidence")
        }
        try {
            yes(tables.status().getString("phase") == "COPYING", "SYNTHETIC_TRANSFER_PREPARED_ONLY")
            yes(
                data.list("customers", limit = 5) == before,
                "DERIVED_CACHE_REFRESH_DURING_TRANSFER",
            )
            var fenced = false
            try {
                store.writableDatabase.execSQL(
                    "UPDATE owner_state SET value=value WHERE key='snapshot'"
                )
            } catch (failure: android.database.sqlite.SQLiteException) {
                fenced = failure.message.orEmpty().contains("TABLE_TRANSFER_IN_PROGRESS")
            }
            yes(fenced, "TRANSFER_AUTHORITY_WRITE_FENCE_RETAINED")
        } finally {
            tables.cancel(request)
        }
        yes(
            tables.status().getString("phase") == "ABORTED" &&
                data.list("customers", limit = 5) == before,
            "TRANSFER_CANCEL_WITH_NATIVE_REVISION_TRIGGERS",
        )
    }

    private suspend fun mainDispatcherProof(store: PixelLocalStore) {
        val violations = CopyOnWriteArrayList<String>()
        withContext(Dispatchers.Main) {
            val previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener(Executor { command -> command.run() }) {
                        violations.add(it.javaClass.simpleName)
                    }
                    .build()
            )
            try {
                val data = store.nativeData()
                data.list("customers")
                data.detail("customers", "qa_customer_1")
                data.history("equipment_usage", "equipment_id", "qa_equipment")
                data.diagnostics()
                withTimeout(10_000) { data.watchList("equipment").first() }
                withTimeout(10_000) { data.watchDetail("equipment", "qa_equipment").first() }
                withTimeout(10_000) {
                    data.watchHistory("equipment_usage", "equipment_id", "qa_equipment").first()
                }
            } finally {
                StrictMode.setThreadPolicy(previous)
            }
        }
        yes(violations.isEmpty(), "NO_MAIN_THREAD_DATABASE_OR_FILE_WORK")
    }

    private suspend fun cachePhotoConcurrencyProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "photo-refresh-race"),
                "qa_photo_refresh_race",
            )
        var store = PixelLocalStore(context)
        val writerHeld = CountDownLatch(1)
        val refreshAllowed = CountDownLatch(1)
        val refreshed = CompletableDeferred<Unit>()
        val saved = CompletableDeferred<String>()
        val closed = CompletableDeferred<Unit>()
        var completed = false
        try {
            val source = SyntheticBusinessData.profile("minimal")
            source
                .getJSONArray("customers")
                .getJSONObject(0)
                .put("name", "[TEST] QA concurrent customer")
            store.putOwnerValue("snapshot", source.toString())
            var data = store.nativeData()
            data.list("customers", limit = 5)
            store.putOwnerValue(
                "revier:v1:entity:equipment:qa_refresh_photo",
                JSONObject()
                    .put("id", "qa_refresh_photo")
                    .put("kind", "equipment")
                    .put("name", "[TEST] QA cache photo race")
                    .put("condition", "ready")
                    .toString(),
            )
            val bitmap =
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val bytes =
                try {
                    java.io.ByteArrayOutputStream().use { output ->
                        check(
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                        )
                        output.toByteArray()
                    }
                } finally {
                    bitmap.recycle()
                }
            val request = "qa_native_refresh_photo_race"
            val photo =
                JSONObject()
                    .put("customer_id", "qa_customer_1")
                    .put("caption", "[TEST] QA concurrent cache photo")
                    .put("original_name", "qa-cache-photo.png")
                    .put("role", "documentation")
                    .put("test_record", true)
                    .put("_photo_request_id", request)
                    .put(
                        "data",
                        "data:image/png;base64," +
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                    )
            val db = store.writableDatabase
            // Invoke the actual refresh at its transaction boundary, without a production test
            // hook.
            val refresh =
                NativeDataRepository::class
                    .java
                    .getDeclaredMethod("refresh", SQLiteDatabase::class.java)
                    .apply { isAccessible = true }
            val reader =
                Thread(
                        {
                            try {
                                db.beginTransactionNonExclusive()
                                try {
                                    writerHeld.countDown()
                                    check(refreshAllowed.await(10, TimeUnit.SECONDS)) {
                                        "QA_NATIVE_DATA_REFRESH_RELEASE_TIMEOUT"
                                    }
                                    refresh.invoke(data, db)
                                    db.setTransactionSuccessful()
                                } finally {
                                    db.endTransaction()
                                }
                                refreshed.complete(Unit)
                            } catch (failure: Throwable) {
                                refreshed.completeExceptionally(failure)
                            }
                        },
                        "qa-cache-refresh",
                    )
                    .apply { isDaemon = true }
            val writer =
                Thread(
                        {
                            try {
                                saved.complete(store.savePhoto(photo))
                            } catch (failure: Throwable) {
                                saved.completeExceptionally(failure)
                            }
                        },
                        "qa-photo-save",
                    )
                    .apply { isDaemon = true }
            val closer =
                Thread(
                        {
                            try {
                                store.close()
                                closed.complete(Unit)
                            } catch (failure: Throwable) {
                                closed.completeExceptionally(failure)
                            }
                        },
                        "qa-photo-store-close",
                    )
                    .apply { isDaemon = true }
            reader.start()
            yes(
                withContext(Dispatchers.IO) { writerHeld.await(5, TimeUnit.SECONDS) },
                "REFRESH_WRITER_ACQUIRED",
            )
            writer.start()
            try {
                withTimeout(5_000) {
                    while (
                        writer.stackTrace.none {
                            it.className == "android.database.sqlite.SQLiteConnectionPool" &&
                                it.methodName == "waitForConnection"
                        }
                    ) {
                        if (saved.isCompleted) saved.await()
                        delay(10)
                    }
                }
                yes(!saved.isCompleted, "PHOTO_WAITING_FOR_REFRESH_WRITER")
                closer.start()
                withTimeout(5_000) {
                    while (
                        closer.state != Thread.State.WAITING ||
                            closer.stackTrace.none {
                                it.className == PixelLocalStore::class.java.name &&
                                    it.methodName == "close"
                            }
                    ) {
                        if (closed.isCompleted) {
                            closed.await()
                            error("QA_NATIVE_DATA_CLOSE_DID_NOT_DRAIN_PHOTO")
                        }
                        delay(10)
                    }
                }
                yes(!closed.isCompleted, "CLOSE_DRAINS_PHOTO_WITHOUT_HELPER_MONITOR")
            } finally {
                refreshAllowed.countDown()
            }
            val id =
                withTimeout(10_000) {
                    refreshed.await()
                    saved.await().also { closed.await() }
                }
            completed = true
            store = PixelLocalStore(context)
            data = store.nativeData()
            yes(
                store.photoAttachment(id) != null &&
                    data.detail("photos", id)?.optString("id") == id,
                "REFRESH_AND_PHOTO_BOTH_COMMITTED",
            )
            yes(
                JSONObject(store.ownerValue("photo-ui:$request", "{}")).getString("id") == id,
                "CONCURRENT_PHOTO_RECEIPT_COMMITTED",
            )
            yes(
                store.savePhoto(JSONObject(photo.toString())) == id,
                "CONCURRENT_PHOTO_REPLAY_SAME_ID",
            )
            val nested =
                withContext(Dispatchers.IO) {
                    val transaction = store.writableDatabase
                    store.beginBusinessTransaction(transaction)
                    try {
                        val result =
                            store.savePhoto(
                                JSONObject(photo.toString())
                                    .put("_photo_request_id", request + "_nested")
                            )
                        transaction.setTransactionSuccessful()
                        result
                    } finally {
                        transaction.endTransaction()
                    }
                }
            yes(
                nested != id && data.detail("photos", nested)?.optString("id") == nested,
                "PHOTO_IN_EXISTING_OWNER_TRANSACTION",
            )
        } finally {
            refreshAllowed.countDown()
            // A timeout must report failure rather than block closing a deliberately deadlocked
            // fixture.
            // Only this fresh, isolated child store is retained on failure; the test process owns
            // it.
            if (completed) store.close()
        }
    }

    private suspend fun cacheInitializationCloseProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "cache-init-close-race"),
                "qa_cache_init_close_race",
            )
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
            val firstRead =
                NativeDataRepository::class
                    .java
                    .getDeclaredMethod("readCurrent", kotlin.jvm.functions.Function1::class.java)
                    .apply { isAccessible = true }
            val count: (SQLiteDatabase) -> Int = { current ->
                current
                    .rawQuery(
                        "SELECT count(*) FROM native_read_rows WHERE collection='customers'",
                        null,
                    )
                    .use { rows ->
                        rows.moveToFirst()
                        rows.getInt(0)
                    }
            }
            val writer =
                Thread(
                        {
                            db.acquireReference()
                            try {
                                db.beginTransactionNonExclusive()
                                try {
                                    held.countDown()
                                    check(release.await(15, TimeUnit.SECONDS)) {
                                        "QA_NATIVE_DATA_INIT_WRITER_RELEASE_TIMEOUT"
                                    }
                                    db.setTransactionSuccessful()
                                } finally {
                                    db.endTransaction()
                                }
                                written.complete(null)
                            } catch (failure: Throwable) {
                                written.complete(failure)
                            } finally {
                                db.releaseReference()
                            }
                        },
                        "qa-cache-init-blocking-writer",
                    )
                    .apply { isDaemon = true }
            val reader =
                Thread(
                        {
                            try {
                                firstRead.invoke(data, count)
                                queried.complete(null)
                            } catch (failure: Throwable) {
                                queried.complete(
                                    if (failure is java.lang.reflect.InvocationTargetException)
                                        failure.targetException
                                    else failure
                                )
                            }
                        },
                        "qa-cache-first-read",
                    )
                    .apply { isDaemon = true }
            val closer =
                Thread(
                        {
                            try {
                                store.close()
                                closed.complete(Unit)
                            } catch (failure: Throwable) {
                                closed.completeExceptionally(failure)
                            }
                        },
                        "qa-cache-init-close",
                    )
                    .apply { isDaemon = true }
            writer.start()
            yes(
                withContext(Dispatchers.IO) { held.await(5, TimeUnit.SECONDS) },
                "INIT_BLOCKING_WRITER_ACQUIRED",
            )
            reader.start()
            withTimeout(5_000) {
                while (
                    reader.stackTrace.none {
                        it.className == "android.database.sqlite.SQLiteConnectionPool" &&
                            it.methodName == "waitForConnection"
                    }
                ) {
                    if (queried.isCompleted)
                        throw AssertionError(
                            "QA_NATIVE_DATA_FIRST_INIT_DID_NOT_WAIT",
                            queried.await(),
                        )
                    delay(10)
                }
            }
            yes(!queried.isCompleted, "FIRST_INIT_WAITING_FOR_WRITER")
            closer.start()
            try {
                withTimeout(5_000) {
                    while (
                        !closed.isCompleted &&
                            closer.stackTrace.none {
                                it.className == PixelLocalStore::class.java.name &&
                                    it.methodName == "close"
                            }
                    ) delay(10)
                }
            } finally {
                release.countDown()
            }
            val outcomes =
                withTimeout(10_000) {
                    listOf(written.await(), queried.await()).also { closed.await() }
                }
            completed = true
            yes(
                outcomes.all { failure ->
                    failure == null ||
                        ((failure is IllegalStateException ||
                            failure is android.database.sqlite.SQLiteException) &&
                            failure.message.orEmpty().contains("closed", ignoreCase = true))
                },
                "INIT_CLOSE_ONLY_ALLOWED_CLOSED_QUERY_FAILURES",
            )
            store = PixelLocalStore(context)
            yes(
                store.nativeData().list("customers").total == 1,
                "INIT_CLOSE_REOPEN_PRESERVES_SOURCE",
            )
        } finally {
            release.countDown()
            // A reproduced deadlock belongs only to this isolated child store.
            if (completed) store.close()
        }
    }

    private suspend fun flowAndWalProof(
        store: PixelLocalStore,
        data: NativeDataRepository,
        key: String,
        equipment: JSONObject,
    ) =
        kotlinx.coroutines.coroutineScope {
            val events = CopyOnWriteArrayList<NativeListPage>()
            val cold = PixelLocalStore(store.appContext())
            val watcher =
                launch(Dispatchers.Default) {
                    data.watchList("equipment").collect { events.add(it) }
                }
            try {
                withTimeout(10_000) { while (events.isEmpty()) delay(10) }
                val writerStarted = CompletableDeferred<Unit>()
                val releaseWriter = CountDownLatch(1)
                val writer =
                    async(Dispatchers.IO) {
                        val db = store.writableDatabase
                        store.beginBusinessTransaction(db)
                        try {
                            db.execSQL(
                                "UPDATE owner_state SET value=? WHERE key=?",
                                arrayOf(
                                    JSONObject(equipment.toString())
                                        .put("name", "QA committed")
                                        .toString(),
                                    key,
                                ),
                            )
                            writerStarted.complete(Unit)
                            check(releaseWriter.await(15, TimeUnit.SECONDS)) {
                                "QA_NATIVE_DATA_WRITER_RELEASE_TIMEOUT"
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                try {
                    withTimeout(10_000) { writerStarted.await() }
                    val old = withTimeout(5_000) { data.list("equipment") }
                    yes(
                        old.rows.single().title == "QA initial",
                        "WAL_READER_DURING_UNCOMMITTED_WRITER",
                    )
                    yes(events.size == 1, "UNCOMMITTED_FLOW_NOT_PUBLISHED")
                    phase = "cold_helper_wal"
                    val coldRead = withTimeout(5_000) { cold.nativeData().list("equipment") }
                    yes(
                        coldRead.rows.single().title == "QA initial",
                        "COLD_HELPER_READS_VALID_WAL_CACHE_WITHOUT_WRITER",
                    )
                    val snapshot =
                        withTimeout(5_000) { withContext(Dispatchers.IO) { cold.getState() } }
                    yes(snapshot.has("master"), "FULL_SNAPSHOT_READS_DURING_UNCOMMITTED_WAL_WRITER")
                    yes(
                        cold.ownerValue(key, "").contains("QA initial"),
                        "FULL_SNAPSHOT_PRESERVES_UNCOMMITTED_WRITER_ISOLATION",
                    )
                    phase = "flow_commit"
                } finally {
                    releaseWriter.countDown()
                }
                writer.await()
                withTimeout(10_000) { while (events.size < 2) delay(10) }
                yes(
                    events.last().rows.single().title == "QA committed",
                    "FLOW_AFTER_PHYSICAL_COMMIT",
                )
                val rebuilds = data.diagnostics().getLong("rebuild_count")
                store.putOwnerValue("native_form_draft:qa_native_data", "{}")
                delay(100)
                yes(
                    data.diagnostics().getLong("rebuild_count") == rebuilds,
                    "DRAFT_DOES_NOT_REBUILD_BUSINESS_CACHE",
                )
                val db = store.writableDatabase
                store.beginBusinessTransaction(db)
                try {
                    db.execSQL(
                        "UPDATE owner_state SET value=? WHERE key=?",
                        arrayOf(
                            JSONObject(equipment.toString()).put("name", "QA rollback").toString(),
                            key,
                        ),
                    )
                } finally {
                    db.endTransaction()
                }
                // An explicit duplicate notification must still never expose rolled-back values.
                store.notifyCommittedChange()
                delay(100)
                yes(
                    data.list("equipment").rows.single().title == "QA committed",
                    "ROLLBACK_PRESERVES_COMMITTED_LIST",
                )
                yes(
                    events.size == 2 &&
                        events.none { page -> page.rows.any { it.title == "QA rollback" } },
                    "DRAFT_AND_ROLLBACK_NO_FALSE_FLOW",
                )
                yes(
                    data.diagnostics().getLong("rebuild_count") == rebuilds,
                    "ROLLBACK_PRESERVES_CACHE_REVISION",
                )
            } finally {
                watcher.cancelAndJoin()
                cold.close()
            }
        }

    private suspend fun cacheSchemaRepairProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "cache-schema-repair"),
                "qa_cache_schema_repair",
            )
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
            db.execSQL(
                "CREATE TRIGGER native_read_revision_owner_state_UPDATE AFTER UPDATE ON owner_state BEGIN SELECT 1; END"
            )
            source.getJSONArray("customers").getJSONObject(0).put("name", "QA repaired cache")
            store.putOwnerValue("snapshot", source.toString())
            PixelLocalStore(context).use { cold ->
                val data = cold.nativeData()
                yes(
                    data.list("customers").rows.single().title == "QA repaired cache",
                    "COLD_CACHE_REPAIRS_TAMPERED_SCHEMA_AND_STALE_DATA",
                )
                val diagnostics = data.diagnostics()
                yes(
                    diagnostics.getLong("source_revision") > revision &&
                        diagnostics
                            .getJSONObject("plans")
                            .getJSONArray("customer_active_sort")
                            .toString()
                            .contains("ix_native_read_sort"),
                    "CACHE_REPAIR_REPLACES_INDEX_AND_ADVANCES_GENERATION",
                )
                source.getJSONArray("customers").getJSONObject(0).put("name", "QA repaired trigger")
                store.putOwnerValue("snapshot", source.toString())
                yes(
                    data.list("customers").rows.single().title == "QA repaired trigger",
                    "CACHE_REPAIR_REPLACES_REVISION_TRIGGER",
                )
                db.rawQuery("SELECT value FROM qa_preserved_table WHERE id=1", null).use { row ->
                    yes(
                        row.moveToFirst() && row.getString(0) == "QA preserved",
                        "CACHE_REPAIR_PRESERVES_UNKNOWN_TABLE",
                    )
                }
            }
        }
    }

    private fun closedCommitObserverProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "closed-commit-observer"),
                "qa_closed_commit_observer",
            )
        val store = PixelLocalStore(context)
        try {
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            store.putOwnerValue("qa_closed_observer_marker", "kept")
            val data = store.nativeData()
            store.close()
            val observed = runBlocking {
                withTimeout(5000) { data.watchCommittedChanges().toList() }
            }
            yes(observed.isEmpty(), "CLOSED_COMMIT_OBSERVER_COMPLETES_WITHOUT_REOPENING")
            PixelLocalStore.assertNoOpenStores(context)
            PixelLocalStore(context).use { reopened ->
                yes(
                    reopened.ownerValue("qa_closed_observer_marker", "") == "kept",
                    "CLOSED_COMMIT_OBSERVER_PRESERVES_STORED_DATA",
                )
            }
        } finally {
            store.close()
        }
    }

    private fun contendedCommitObserverProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "contended-commit-observer"),
                "qa_contended_commit_observer",
            )
        PixelLocalStore(context).use { store ->
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            store.putOwnerValue("qa_contended_observer_marker", "kept")
            val data = store.nativeData()
            val before = PixelMcpWriteTools.hash(store.getState())
            val db = store.writableDatabase
            // Pin the primary connection: the commit barrier must encounter SQLITE_BUSY
            // while a separate helper holds the write lock on this isolated database.
            db.beginTransactionNonExclusive()
            try {
                db.rawQuery("PRAGMA busy_timeout=50", null).use { it.moveToFirst() }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            PixelLocalStore(context).use { writer ->
                val writerDb = writer.writableDatabase
                writerDb.beginTransactionNonExclusive()
                runBlocking {
                    val started = CompletableDeferred<Unit>()
                    val observed =
                        async(Dispatchers.IO) {
                            started.complete(Unit)
                            data.watchCommittedChanges().first()
                        }
                    try {
                        started.await()
                        delay(350)
                        yes(observed.isActive, "LOCKED_COMMIT_OBSERVER_WAITS_FOR_WRITER")
                        writerDb.setTransactionSuccessful()
                    } finally {
                        writerDb.endTransaction()
                    }
                    withTimeout(5000) { observed.await() }
                    yes(observed.isCompleted, "LOCKED_COMMIT_OBSERVER_RESUMES_AFTER_UNLOCK")
                }
            }
            yes(
                PixelMcpWriteTools.hash(store.getState()) == before &&
                    store.ownerValue("qa_contended_observer_marker", "") == "kept",
                "LOCKED_COMMIT_OBSERVER_PRESERVES_STORED_DATA",
            )
        }
        PixelLocalStore.assertNoOpenStores(context)
    }

    private fun businessTransactionLifetimeProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "business-close-lifetime"),
                "qa_business_close_lifetime",
            )
        var store = PixelLocalStore(context)
        fun guardRejects(): Boolean =
            try {
                PixelLocalStore.assertNoOpenStores(context)
                false
            } catch (expected: IOException) {
                expected.message == "RESTORE_DATABASE_STILL_OPEN"
            }
        try {
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            store.putOwnerValue("qa_lifetime", "before")
            for (commit in listOf(true, false)) {
                val db = store.writableDatabase
                store.beginBusinessTransaction(db)
                try {
                    store.close()
                    yes(
                        db.isOpen && store.writableDatabase === db,
                        "BUSINESS_TRANSACTION_PINS_AND_REUSES_DATABASE_$commit",
                    )
                    store.putOwnerValue("qa_lifetime", if (commit) "committed" else "rolled_back")
                    yes(
                        store.getState().has("master") &&
                            store.ownerValue("qa_lifetime", "") ==
                                if (commit) "committed" else "rolled_back",
                        "CLOSED_HELPER_CALLBACK_AFFINITY_$commit",
                    )
                    yes(guardRejects(), "RESTORE_GUARD_REJECTS_PINNED_CLOSED_HELPER_$commit")
                    if (commit) db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                yes(!db.isOpen, "BUSINESS_TRANSACTION_RELEASES_PIN_$commit")
                PixelLocalStore.assertNoOpenStores(context)
                var closed = false
                try {
                    store.writableDatabase
                } catch (expected: IllegalStateException) {
                    closed = expected.message == "LOCAL_STORE_CLOSED"
                }
                yes(closed, "CLOSED_HELPER_CANNOT_REOPEN_$commit")
                store = PixelLocalStore(context)
                yes(
                    store.ownerValue("qa_lifetime", "") == "committed",
                    "BUSINESS_NEW_HELPER_COMMIT_ROLLBACK_$commit",
                )
            }
            val db = store.writableDatabase
            PixelLocalStore.beginJournalTransaction(db) {
                throw IOException("QA_SYNTHETIC_COMMIT_FAILURE")
            }
            store.close()
            store.putOwnerValue("qa_lifetime", "failed_commit")
            db.setTransactionSuccessful()
            var rejected = false
            try {
                db.endTransaction()
            } catch (failure: IllegalStateException) {
                rejected = failure.message == "LOCAL_JOURNAL_TABLE_COMMIT_FAILED"
            }
            yes(rejected && !db.isOpen, "FAILED_COMMIT_RELEASES_PIN")
            PixelLocalStore.assertNoOpenStores(context)
            store = PixelLocalStore(context)
            yes(
                store.ownerValue("qa_lifetime", "") == "committed",
                "FAILED_COMMIT_ROLLS_BACK_AND_REOPENS",
            )
        } finally {
            store.close()
        }
    }

    private fun snapshotCloseLifetimeProof(owner: Context) {
        val base =
            FixtureContext(
                owner,
                File(owner.cacheDir, "snapshot-close-lifetime"),
                "qa_snapshot_close_lifetime",
            )
        var duringSnapshot: (() -> Unit)? = null
        val context =
            object : ContextWrapper(base) {
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
                yes(
                    if (fail) outcome.exceptionOrNull() is IllegalStateException
                    else outcome.getOrThrow().has("master"),
                    "SNAPSHOT_CLOSE_OUTCOME_$fail",
                )
                yes(!database.isOpen, "SNAPSHOT_CLOSE_RELEASES_CONNECTION_$fail")
                PixelLocalStore.assertNoOpenStores(context)
                PixelLocalStore(context).use { reopened ->
                    reopened.putOwnerValue("qa_snapshot_reopened", "ready")
                    yes(
                        reopened.getState().has("master") &&
                            reopened.ownerValue("qa_snapshot_reopened", "") == "ready",
                        "SNAPSHOT_CLOSE_NO_LOCK_AFTER_REOPEN_$fail",
                    )
                }
            } finally {
                duringSnapshot = null
                store.close()
            }
        }
    }

    private suspend fun chunkedUploadStorageProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "chunk-upload-bound"),
                "qa_chunk_upload_bound",
            )
        val store = PixelLocalStore(context)
        var completed = false
        try {
            store.putOwnerValue("snapshot", SyntheticBusinessData.profile("minimal").toString())
            val repository = LocalOwnerRepository(context, store)
            val access = PixelOwnerAccess(context, store)
            val bytes = ByteArray(1024 * 1024) { ((it * 17 + it / 32768) and 255).toByte() }
            val upload =
                access
                    .execute(
                        "begin_file_upload",
                        JSONObject()
                            .put("request_id", "qa_chunk_bound_begin")
                            .put("byte_size", bytes.size)
                            .put("sha256", McpFileTransfer.sha(bytes))
                            .put("mime_type", "image/jpeg"),
                    )
                    .getJSONObject("data")
                    .getString("upload_id")
            val directory = File(context.filesDir, "mcp-photo-uploads")
            val raw =
                File(directory, "qa-legacy-unrecognized.raw").apply {
                    writeText("[TEST] QA preserve raw")
                }
            val legacy = "[TEST] QA legacy BLOB".toByteArray()
            repository.putFile("qa_legacy_blob", JSONObject().put("kind", "backup"), legacy)
            val other = "[TEST] QA other active upload".toByteArray()
            repository.putFile("qa_other_upload", JSONObject().put("kind", "photo_upload"), other)
            var replay: JSONObject? = null
            for (offset in bytes.indices step 32768) {
                val part = bytes.copyOfRange(offset, minOf(bytes.size, offset + 32768))
                val args =
                    JSONObject()
                        .put("request_id", "qa_chunk_bound_$offset")
                        .put("upload_id", upload)
                        .put("offset", offset)
                        .put("chunk_base64", java.util.Base64.getEncoder().encodeToString(part))
                        .put("chunk_sha256", McpFileTransfer.sha(part))
                val result = access.execute("write_file_chunk", args).getJSONObject("data")
                yes(
                    result.getInt("next_offset") == offset + part.size &&
                        !result.optBoolean("upload_cleanup_pending"),
                    "CHUNK_OFFSET_AND_CLEANUP_$offset",
                )
                val content =
                    directory.listFiles().orEmpty().filter {
                        it.name.matches(Regex("[a-f0-9]{64}\\.bin"))
                    }
                yes(
                    content.size == 2 &&
                        content.sumOf { it.length() } == (offset + part.size + other.size).toLong(),
                    "CHUNK_LIVE_DISK_BOUND_$offset",
                )
                if (replay == null) replay = JSONObject(args.toString())
            }
            yes(
                repository.fileBytes(upload).contentEquals(bytes) &&
                    repository.fileBytes("qa_legacy_blob").contentEquals(legacy) &&
                    raw.readText() == "[TEST] QA preserve raw",
                "CHUNK_BYTES_AND_LEGACY_PRESERVED",
            )
            yes(
                access
                    .execute("write_file_chunk", replay!!)
                    .getJSONObject("data")
                    .optBoolean("replayed") && repository.fileBytes(upload).contentEquals(bytes),
                "OLD_CHUNK_REPLAY_DOES_NOT_REWIND",
            )

            val concurrentBytes = bytes.copyOfRange(0, 65536)
            val second =
                access
                    .execute(
                        "begin_file_upload",
                        JSONObject()
                            .put("request_id", "qa_chunk_capture_begin")
                            .put("byte_size", concurrentBytes.size)
                            .put("sha256", McpFileTransfer.sha(concurrentBytes))
                            .put("mime_type", "image/jpeg"),
                    )
                    .getJSONObject("data")
                    .getString("upload_id")
            fun chunkArgs(offset: Int) =
                JSONObject()
                    .put("request_id", "qa_chunk_capture_$offset")
                    .put("upload_id", second)
                    .put("offset", offset)
                    .put(
                        "chunk_base64",
                        java.util.Base64.getEncoder()
                            .encodeToString(concurrentBytes.copyOfRange(offset, offset + 32768)),
                    )
                    .put(
                        "chunk_sha256",
                        McpFileTransfer.sha(concurrentBytes.copyOfRange(offset, offset + 32768)),
                    )
            access.execute("write_file_chunk", chunkArgs(0))
            val previous =
                File(
                    directory,
                    repository.fileMeta(second).getString("private_upload_storage_name"),
                )
            val capture = File(context.cacheDir, "chunk-backup")
            val pinned = CountDownLatch(1)
            val captureAllowed = CountDownLatch(1)
            val captured = CompletableDeferred<File>()
            val saved = CompletableDeferred<JSONObject>()
            Thread(
                    {
                        try {
                            synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
                                BackupFilePins.pinImmutable(
                                    directory,
                                    capture,
                                    "files/mcp-photo-uploads",
                                )
                                pinned.countDown()
                                check(captureAllowed.await(15, TimeUnit.SECONDS)) {
                                    "QA_CHUNK_BACKUP_RELEASE_TIMEOUT"
                                }
                                val image = File(capture, "database.sqlite")
                                DatabaseBackupSnapshot.capture(store.writableDatabase, image)
                                BackupFilePins.capture(
                                    directory,
                                    capture,
                                    "files/mcp-photo-uploads",
                                )
                                captured.complete(image)
                            }
                        } catch (failure: Throwable) {
                            pinned.countDown()
                            captured.completeExceptionally(failure)
                        }
                    },
                    "qa-chunk-backup",
                )
                .apply { isDaemon = true }
                .start()
            yes(
                withContext(Dispatchers.IO) { pinned.await(5, TimeUnit.SECONDS) },
                "CHUNK_BACKUP_PINNED",
            )
            Thread(
                    {
                        try {
                            saved.complete(
                                access
                                    .execute("write_file_chunk", chunkArgs(32768))
                                    .getJSONObject("data")
                            )
                        } catch (failure: Throwable) {
                            saved.completeExceptionally(failure)
                        }
                    },
                    "qa-chunk-owner-write",
                )
                .apply { isDaemon = true }
                .start()
            try {
                withTimeout(10_000) {
                    while (
                        repository.fileMeta(second).getLong("private_upload_byte_size") !=
                            concurrentBytes.size.toLong()
                    ) delay(10)
                }
                yes(
                    !saved.isCompleted && previous.isFile,
                    "CHUNK_COMMITTED_BEFORE_CLEANUP_WAITS_FOR_BACKUP",
                )
            } finally {
                captureAllowed.countDown()
            }
            val image =
                withTimeout(15_000) {
                    saved.await()
                    captured.await()
                }
            val pinnedPrevious = File(capture, "files/mcp-photo-uploads/${previous.name}")
            yes(
                !previous.exists() &&
                    pinnedPrevious.readBytes().contentEquals(concurrentBytes.copyOfRange(0, 32768)),
                "SUPERSEDED_PREFIX_REMOVED_WITH_BACKUP_PIN_INTACT",
            )
            SQLiteDatabase.openDatabase(image.path, null, SQLiteDatabase.OPEN_READONLY).use {
                snapshot ->
                snapshot
                    .rawQuery(
                        "SELECT metadata,length(data) FROM mcp_uploads WHERE id=?",
                        arrayOf(second),
                    )
                    .use { row ->
                        yes(row.moveToFirst() && row.getLong(1) == 0L, "CHUNK_BACKUP_METADATA_ONLY")
                        val metadata = JSONObject(row.getString(0))
                        yes(
                            File(
                                    capture,
                                    "files/mcp-photo-uploads/${metadata.getString("private_upload_storage_name")}",
                                )
                                .readBytes()
                                .contentEquals(concurrentBytes),
                            "CHUNK_BACKUP_COMMITTED_REFERENCE_COMPLETE",
                        )
                    }
            }

            val rollback = "[TEST] QA uncommitted prefix".toByteArray()
            val rollbackFile = File(directory, McpFileTransfer.sha(rollback) + ".bin")
            val published = CountDownLatch(1)
            val rollbackAllowed = CountDownLatch(1)
            val rolledBack = CompletableDeferred<Unit>()
            val cleaned = CompletableDeferred<Unit>()
            Thread(
                    {
                        val db = store.writableDatabase
                        try {
                            db.beginTransactionNonExclusive()
                            try {
                                repository.putFile(second, repository.fileMeta(second), rollback)
                                repository
                                    .pruneUnreferencedPhotoUploads() // Nested cleanup must not wait
                                // on capture.
                                published.countDown()
                                check(rollbackAllowed.await(15, TimeUnit.SECONDS)) {
                                    "QA_CHUNK_ROLLBACK_RELEASE_TIMEOUT"
                                }
                            } finally {
                                db.endTransaction()
                            }
                            rolledBack.complete(Unit)
                        } catch (failure: Throwable) {
                            published.countDown()
                            rolledBack.completeExceptionally(failure)
                        }
                    },
                    "qa-chunk-rollback",
                )
                .apply { isDaemon = true }
                .start()
            yes(
                withContext(Dispatchers.IO) { published.await(5, TimeUnit.SECONDS) },
                "ROLLBACK_PREFIX_PUBLISHED",
            )
            val cleaner =
                Thread(
                        {
                            try {
                                repository.pruneUnreferencedPhotoUploads()
                                cleaned.complete(Unit)
                            } catch (failure: Throwable) {
                                cleaned.completeExceptionally(failure)
                            }
                        },
                        "qa-chunk-cleaner",
                    )
                    .apply { isDaemon = true }
            cleaner.start()
            try {
                withTimeout(5_000) {
                    while (
                        cleaner.stackTrace.none {
                            it.className == "android.database.sqlite.SQLiteConnectionPool" &&
                                it.methodName == "waitForConnection"
                        }
                    ) {
                        if (cleaned.isCompleted) error("QA_CLEANER_DID_NOT_WAIT_FOR_PUBLISHER")
                        delay(10)
                    }
                }
                yes(
                    rollbackFile.isFile &&
                        repository.fileBytes(second).contentEquals(concurrentBytes),
                    "CLEANUP_CANNOT_DELETE_UNCOMMITTED_PUBLISHER_OR_OLD_REFERENCE",
                )
            } finally {
                rollbackAllowed.countDown()
            }
            withTimeout(10_000) {
                rolledBack.await()
                cleaned.await()
            }
            yes(
                !rollbackFile.exists() &&
                    repository.fileBytes(second).contentEquals(concurrentBytes) &&
                    repository.fileBytes("qa_other_upload").contentEquals(other),
                "ROLLBACK_CLEANUP_PRESERVES_ALL_COMMITTED_UPLOADS",
            )
            completed = true
        } finally {
            if (completed) store.close()
        }
    }

    private suspend fun uploadPublicationProof(owner: Context) {
        val context =
            FixtureContext(
                owner,
                File(owner.cacheDir, "upload-publication-race"),
                "qa_upload_publication_race",
            )
        val store = PixelLocalStore(context)
        val repository = LocalOwnerRepository(context, store)
        val old = "[TEST] QA previous immutable upload".toByteArray()
        val next = ByteArray(4 * 1024 * 1024) { ((it * 31 + 17) and 255).toByte() }
        val uploadId = "qa_upload_publication"
        val oldHash = McpFileTransfer.sha(old)
        val nextHash = McpFileTransfer.sha(next)
        val metadata =
            JSONObject()
                .put("kind", "photo_upload")
                .put("byte_size", next.size)
                .put("sha256", nextHash)
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
            yes(
                android.system.Os.stat(original.path).st_ino == inode,
                "EXISTING_UPLOAD_FILE_RETAINED",
            )
            val backup =
                Thread(
                        {
                            try {
                                synchronized(PixelLocalStore.FILE_CAPTURE_LOCK) {
                                    BackupFilePins.pinImmutable(
                                        directory,
                                        stage,
                                        "files/mcp-photo-uploads",
                                    )
                                    pinned.countDown()
                                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                                    while (published.count > 0) {
                                        check(System.nanoTime() < deadline) {
                                            "QA_NATIVE_DATA_UPLOAD_PUBLICATION_TIMEOUT"
                                        }
                                        BackupFilePins.pinImmutable(directory, probes, "uploads")
                                        check(
                                            directory.listFiles().orEmpty().all {
                                                it.name.matches(Regex("[a-f0-9]{64}\\.bin"))
                                            }
                                        ) {
                                            "QA_NATIVE_DATA_PARTIAL_UPLOAD_VISIBLE_TO_BACKUP"
                                        }
                                        Thread.yield()
                                    }
                                    val image = File(stage, "database.sqlite")
                                    DatabaseBackupSnapshot.capture(store.writableDatabase, image)
                                    // Match capture's second pass: newly committed file references
                                    // must be available too.
                                    BackupFilePins.capture(
                                        directory,
                                        stage,
                                        "files/mcp-photo-uploads",
                                    )
                                    captured.complete(image)
                                }
                            } catch (failure: Throwable) {
                                captured.completeExceptionally(failure)
                                pinned.countDown()
                            }
                        },
                        "qa-upload-backup",
                    )
                    .apply { isDaemon = true }
            val writer =
                Thread(
                        {
                            val db = store.writableDatabase
                            try {
                                db.beginTransactionNonExclusive()
                                try {
                                    repository.putFile(uploadId, metadata, next)
                                    published.countDown()
                                    check(commitAllowed.await(10, TimeUnit.SECONDS)) {
                                        "QA_NATIVE_DATA_UPLOAD_COMMIT_TIMEOUT"
                                    }
                                    db.setTransactionSuccessful()
                                } finally {
                                    db.endTransaction()
                                }
                                written.complete(Unit)
                            } catch (failure: Throwable) {
                                published.countDown()
                                written.completeExceptionally(failure)
                            }
                        },
                        "qa-upload-publish",
                    )
                    .apply { isDaemon = true }
            backup.start()
            yes(
                withContext(Dispatchers.IO) { pinned.await(5, TimeUnit.SECONDS) },
                "UPLOAD_BACKUP_PIN_STARTED",
            )
            writer.start()
            try {
                yes(
                    withContext(Dispatchers.IO) { published.await(10, TimeUnit.SECONDS) },
                    "UPLOAD_PUBLISHED_WITH_CAPTURE_LOCK_HELD",
                )
                yes(
                    repository.fileBytes(uploadId).contentEquals(old),
                    "UNCOMMITTED_UPLOAD_REFERENCE_HIDDEN",
                )
            } finally {
                commitAllowed.countDown()
            }
            val image =
                withTimeout(15_000) {
                    written.await()
                    captured.await()
                }
            completed = true
            SQLiteDatabase.openDatabase(image.path, null, SQLiteDatabase.OPEN_READONLY).use {
                snapshot ->
                snapshot
                    .rawQuery(
                        "SELECT metadata,length(data) FROM mcp_uploads WHERE id=?",
                        arrayOf(uploadId),
                    )
                    .use { row ->
                        yes(
                            row.moveToFirst() && row.getLong(1) == 0L,
                            "FILE_BACKED_UPLOAD_SNAPSHOT_NO_BLOB",
                        )
                        val reference = JSONObject(row.getString(0))
                        val sha = reference.getString("private_upload_sha256")
                        val file =
                            File(
                                File(stage, "files/mcp-photo-uploads"),
                                reference.getString("private_upload_storage_name"),
                            )
                        yes(
                            sha in listOf(oldHash, nextHash) &&
                                file.isFile &&
                                McpFileTransfer.sha(file.readBytes()) == sha,
                            "UPLOAD_SNAPSHOT_REFERENCE_COMPLETE",
                        )
                    }
            }
            yes(
                original.readBytes().contentEquals(old) &&
                    repository.fileBytes(uploadId).contentEquals(next),
                "UPLOAD_OLD_AND_NEW_BYTES_PRESERVED",
            )
            val rollback = "[TEST] QA rolled-back upload bytes".toByteArray()
            val db = store.writableDatabase
            db.beginTransactionNonExclusive()
            try {
                repository.putFile(uploadId, metadata, rollback)
            } finally {
                db.endTransaction()
            }
            yes(
                repository.fileBytes(uploadId).contentEquals(next) &&
                    original.readBytes().contentEquals(old),
                "UPLOAD_ROLLBACK_RETAINS_COMMITTED_REFERENCE",
            )
        } finally {
            commitAllowed.countDown()
            if (completed) store.close()
        }
    }

    private fun thumbnailTileProof(context: Context, store: PixelLocalStore) {
        val photoId = "qa_thumbnail_" + UUID.randomUUID().toString().replace("-", "")
        val attachments = store.photoAttachmentStore()
        val bitmap =
            android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes =
            try {
                bitmap.eraseColor(0xff000000.toInt() or (photoId.hashCode() and 0x00ffffff))
                java.io.ByteArrayOutputStream().use { output ->
                    yes(
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output),
                        "THUMB_JPEG_ENCODE",
                    )
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        yes(bytes.size in 1..65536, "THUMB_SMALL_BYTES")
        val hash = PhotoAttachmentStore.sha256(bytes)
        val thumbnailName = "$hash.jpg"
        val preview = attachments.thumbnailFile(thumbnailName)
        val invalidHash =
            PhotoAttachmentStore.sha256((photoId + "deliberately_invalid_original").toByteArray())
        val invalid = File(File(context.filesDir, "customer-photo-originals"), "$invalidHash.jpg")
        val original = File(File(context.filesDir, "customer-photo-originals"), "$hash.jpg")
        yes(!preview.exists() && !invalid.exists() && !original.exists(), "THUMB_OWN_NEW_FILES")
        var metadataReads = 0
        fun loadMetadata(): JSONObject? {
            metadataReads++
            return store.photoAttachment(photoId)
        }
        try {
            attachments.storeThumbnail(bytes, thumbnailName)
            invalid.parentFile!!.mkdirs()
            invalid.writeBytes(byteArrayOf(1, 2, 3))
            store.restoreAttachmentMetadata(
                photoId,
                JSONObject()
                    .put("content_sha256", invalidHash)
                    .put("mime_type", "image/jpeg")
                    .put("byte_size", 3)
                    .put("storage_name", invalid.name)
                    .put("role", "documentation"),
            )
            repeat(40) {
                yes(
                    resolveNativePhotoSource(
                        attachments,
                        thumbnailName,
                        null,
                        false,
                        ::loadMetadata,
                    ) == preview,
                    "PROJECTED_THUMB_TILE",
                )
            }
            yes(metadataReads == 0, "THUMB_ZERO_SQL_POINT_READS")
            // If any tile had verified this deliberately wrong original, the call above would have
            // thrown.
            var originalRejected = false
            try {
                resolveNativePhotoSource(attachments, thumbnailName, null, true, ::loadMetadata)
            } catch (_: IOException) {
                originalRejected = true
            }
            yes(originalRejected && metadataReads == 1, "DETAIL_EXPLICIT_INVALID_ORIGINAL_CHECK")
            val metadata =
                JSONObject()
                    .put("content_sha256", hash)
                    .put("mime_type", "image/jpeg")
                    .put("byte_size", bytes.size)
                    .put("storage_name", original.name)
                    .put("role", "documentation")
            attachments.store(bytes, metadata)
            store.restoreAttachmentMetadata(photoId, metadata)
            metadataReads = 0
            val missing =
                PhotoAttachmentStore.sha256((photoId + "missing_preview").toByteArray()) + ".jpg"
            yes(
                resolveNativePhotoSource(attachments, missing, null, false, ::loadMetadata) ==
                    original && metadataReads == 1,
                "MISSING_THUMB_ONE_SQL_ORIGINAL_FALLBACK",
            )
            preview.writeBytes(byteArrayOf(4, 5, 6))
            metadataReads = 0
            yes(
                resolveNativePhotoSource(attachments, thumbnailName, null, false, ::loadMetadata) ==
                    original && metadataReads == 1,
                "TAMPERED_THUMB_ONE_SQL_ORIGINAL_FALLBACK",
            )
            attachments.verifiedFile(metadata)
            yes(original.readBytes().contentEquals(bytes), "THUMB_FALLBACK_PRESERVES_ORIGINAL")
        } finally {
            store.writableDatabase.delete("photo_attachments", "photo_id=?", arrayOf(photoId))
            // All three paths were proven absent before this test and are owned by this invocation.
            preview.delete()
            invalid.delete()
            original.delete()
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
        val bitmap =
            android.graphics.Bitmap.createBitmap(3, 3, android.graphics.Bitmap.Config.ARGB_8888)
        val image =
            try {
                bitmap.eraseColor(0xff000000.toInt() or (token.hashCode() and 0x00ffffff))
                java.io.ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 83, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        val photoMeta = attachments.inspect(image, "image/jpeg").put("role", "documentation")
        val photo = attachments.file(photoMeta)
        val thumbnail = attachments.thumbnailFile(PhotoAttachmentStore.sha256(image) + ".jpg")
        val pdf = android.graphics.pdf.PdfDocument()
        val document =
            try {
                val page =
                    pdf.startPage(
                        android.graphics.pdf.PdfDocument.PageInfo.Builder(100, 100, 1).create()
                    )
                page.canvas.drawText("QA $token", 5f, 20f, android.graphics.Paint())
                pdf.finishPage(page)
                java.io.ByteArrayOutputStream().use { output ->
                    pdf.writeTo(output)
                    output.toByteArray()
                }
            } finally {
                pdf.close()
            }
        val documentHash = PhotoAttachmentStore.sha256(document)
        val documentMeta =
            JSONObject()
                .put("pdf_storage_name", "$documentHash.pdf")
                .put("pdf_sha256", documentHash)
                .put("pdf_byte_size", document.size)
        val documentFile = File(documentRoot, "$documentHash.pdf")
        val legacy = File(originalRoot, "qa-existing-$token.new")
        yes(
            !photo.exists() &&
                !thumbnail.exists() &&
                !documentFile.exists() &&
                !legacy.exists() &&
                !capture.exists(),
            "PUBLISH_NEW_OWNED_FILES",
        )
        legacy.writeBytes(byteArrayOf(81, 65))
        val events = CopyOnWriteArrayList<String>()
        val published = CountDownLatch(3)
        val observers =
            listOf(
                    originalRoot to photo.name,
                    thumbnailRoot to thumbnail.name,
                    documentRoot to documentFile.name,
                )
                .map { (directory, expected) ->
                    object :
                            android.os.FileObserver(
                                directory,
                                android.os.FileObserver.CREATE or
                                    android.os.FileObserver.MODIFY or
                                    android.os.FileObserver.MOVED_TO,
                            ) {
                            override fun onEvent(event: Int, path: String?) {
                                if (path != null) {
                                    events.add(path)
                                    if (
                                        event and android.os.FileObserver.MOVED_TO != 0 &&
                                            path == expected
                                    )
                                        published.countDown()
                                }
                            }
                        }
                        .also { it.startWatching() }
                }
        try {
            attachments.store(image, photoMeta)
            attachments.storeThumbnail(image, thumbnail.name)
            documents.store(document, documentMeta)
            yes(published.await(10, TimeUnit.SECONDS), "PUBLISH_ATOMIC_FINAL_EVENTS")
            yes(
                events.all { it.matches(Regex("[0-9a-f]{64}\\.(jpg|pdf)")) },
                "NO_PARTIAL_FILES_IN_PINNED_DIRECTORIES",
            )
            BackupFilePins.pinImmutable(originalRoot, capture, "files/customer-photo-originals")
            BackupFilePins.pinImmutable(documentRoot, capture, "files/business-documents")
            val pinnedPhoto = File(capture, "files/customer-photo-originals/${photo.name}")
            val pinnedThumb =
                File(capture, "files/customer-photo-originals/thumbnails/${thumbnail.name}")
            val pinnedDocument = File(capture, "files/business-documents/${documentFile.name}")
            val before =
                listOf(pinnedPhoto, pinnedThumb, pinnedDocument).map { BackupArchive.sha256(it) }
            val count = events.size
            yes(!attachments.store(image, photoMeta), "PUBLISH_PHOTO_RETRY_REUSES_FINAL")
            attachments.storeThumbnail(image, thumbnail.name)
            documents.store(document, documentMeta)
            yes(
                before == listOf(photo, thumbnail, documentFile).map { BackupArchive.sha256(it) },
                "PUBLISH_RETRIES_PRESERVE_FINAL_BYTES",
            )
            yes(
                before ==
                    listOf(pinnedPhoto, pinnedThumb, pinnedDocument).map {
                        BackupArchive.sha256(it)
                    },
                "PUBLISH_RETRIES_PRESERVE_PINNED_BYTES",
            )
            yes(
                events.drop(count).none {
                    it == photo.name || it == thumbnail.name || it == documentFile.name
                },
                "PUBLISH_NO_FINAL_WRITE_ON_RETRY",
            )
            yes(
                File(capture, "files/customer-photo-originals/${legacy.name}")
                    .readBytes()
                    .contentEquals(byteArrayOf(81, 65)) &&
                    legacy.readBytes().contentEquals(byteArrayOf(81, 65)),
                "LEGACY_NEW_FILE_PRESERVED_RAW",
            )
        } finally {
            observers.forEach { it.stopWatching() }
            photo.delete()
            thumbnail.delete()
            documentFile.delete()
            legacy.delete()
            capture.deleteRecursively()
        }
        val offer =
            JSONObject()
                .put("id", "qa_offer_pdf_$token")
                .put("offer_number", "QA-ANG-1")
                .put("document_type", "fixed_price")
                .put("issue_date", "2026-11-02")
                .put("valid_until", "2026-11-30")
                .put("title", "QA Angebots-PDF")
                .put("execution_window", "QA nach Vereinbarung")
                .put("tax_mode", "standard")
                .put("test_record", true)
                .put(
                    "issuer",
                    JSONObject()
                        .put("business_name", "QA Musterbetrieb")
                        .put("business_vat_id", "ATU12345678"),
                )
                .put(
                    "recipient",
                    JSONObject().put("name", "QA Musterkunde").put("street", "QA Testweg 1"),
                )
                .put(
                    "positions",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("title", "QA Gartenarbeit")
                                .put("quantity", 2)
                                .put("unit", "h")
                                .put("price", 10)
                                .put("net", 20)
                                .put("tax_rate", 20)
                        ),
                )
                .put("net_total", 20)
                .put("tax_total", 4)
                .put("gross_total", 24)
        val offered = documents.create(offer)
        try {
            val raw = documents.read(offered)
            yes(
                offered.getString("document_id") == "offer:" + offer.getString("id"),
                "OFFER_PDF_HAS_OFFER_IDENTITY",
            )
            yes(
                String(raw, 0, 5, Charsets.US_ASCII) == "%PDF-" &&
                    PhotoAttachmentStore.sha256(raw) == offered.getString("pdf_sha256"),
                "OFFER_PDF_ORIGINAL_HASH_READBACK",
            )
            android.os.ParcelFileDescriptor.open(
                    File(documentRoot, offered.getString("pdf_storage_name")),
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY,
                )
                .use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                        yes(renderer.pageCount > 0, "OFFER_PDF_RENDERS_ON_ANDROID")
                    }
                }
        } finally {
            documents.deleteVerified(offered)
        }
    }

    private class FixtureContext(
        private val owner: Context,
        private val root: File,
        private val prefix: String,
    ) : ContextWrapper(owner) {
        private val preferenceNames = mutableSetOf<String>()

        fun clearPreferences() {
            for (name in synchronized(preferenceNames) { preferenceNames.toList() }) owner
                .deleteSharedPreferences(name)
        }

        override fun getApplicationContext(): Context = this

        override fun getDataDir(): File = directory("")

        override fun getFilesDir(): File = directory("files")

        override fun getNoBackupFilesDir(): File = directory("no_backup")

        override fun getCacheDir(): File = directory("cache")

        override fun getDatabasePath(name: String): File {
            if (name != "haus-garten-mobile.db")
                throw IOException("QA_SYNTHETIC_DATABASE_NAME_REQUIRED")
            return File(directory("databases"), name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
        ): SQLiteDatabase = openOrCreateDatabase(name, mode, factory, null)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            handler: DatabaseErrorHandler?,
        ): SQLiteDatabase =
            SQLiteDatabase.openDatabase(
                getDatabasePath(name).path,
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                handler,
            )

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (!name.matches(Regex("[A-Za-z0-9_]+")))
                throw IOException("QA_SYNTHETIC_PREFERENCE_NAME_REQUIRED")
            val scoped = "${prefix}_$name"
            synchronized(preferenceNames) { preferenceNames.add(scoped) }
            return owner.getSharedPreferences(scoped, mode)
        }

        private fun directory(name: String): File {
            val value = if (name.isEmpty()) root else File(root, name)
            if (!value.isDirectory && !value.mkdirs())
                throw IOException("QA_SYNTHETIC_STORAGE_REQUIRED")
            return value
        }
    }
}
