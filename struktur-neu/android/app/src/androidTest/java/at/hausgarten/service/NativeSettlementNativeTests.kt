package at.hausgarten.service

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

/** Real Android SQLite/PDF/ZIP boundaries. All paths belong to a new synthetic fixture. */
internal class NativeSettlementNativeTests private constructor(private val test: Instrumentation) {
    companion object {
        @JvmStatic
        fun run(test: Instrumentation, reopen: Boolean): JSONObject =
            NativeSettlementNativeTests(test).execute(reopen)

        internal fun fixture(): JSONObject {
            val state = SyntheticBusinessData.profile("minimal")
            state.put(
                "customers",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "qa_settlement_recipient")
                            .put("name", "[TEST] Erfundene Empfängerin Änne")
                            .put("street", "Erfundener Gartenweg 1")
                            .put("postal_code", "1000")
                            .put("city", "Synthetischer Ort")
                            .put("country", "AT")
                            .put("test_record", true)
                    ),
            )
            state.put("orders", JSONArray())
            state.put(
                "worklogs",
                JSONArray()
                    .put(work("qa_settlement_invoice", 1))
                    .put(work("qa_settlement_labor", 0))
                    .put(work("qa_settlement_old_unbound", 0))
                    .put(work("qa_settlement_race", 0)),
            )
            state.put("invoices", JSONArray()).put("offers", JSONArray()).put("photos", JSONArray())
            state.put(
                "business_settings",
                JSONObject()
                    .put("business_name", "[TEST] Erfundener Grünbetrieb")
                    .put(
                        "business_logo",
                        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
                    )
                    .put("business_email", "qa@example.invalid")
                    .put("business_iban", "AT611904300234573201")
                    .put("business_bic", "BKAUATWW")
                    .put("default_payment_terms", "Binnen 14 Tagen")
                    .put("hourly_rate", "40")
                    .put("owner_name", "Erfundene Person")
                    .put("business_street", "Synthetische Straße 9")
                    .put("business_postal_code", "2000")
                    .put("business_city", "Teststadt")
                    .put("business_country", "AT")
                    .put(
                        "business_legal_info",
                        "Synthetischer nicht eingetragener Einzelunternehmer",
                    )
                    .put("tax_mode", "small_business")
                    .put("invoice_issuer_confirmed", "true"),
            )
            return state
        }

        internal fun work(id: String, billed: Int) =
            JSONObject()
                .put("id", id)
                .put("customer_id", "qa_settlement_recipient")
                .put("billing_customer_id", "qa_settlement_recipient")
                .put("activity", "[TEST] Tatsächliche synthetische Gartenarbeit $id")
                .put("work_date", "2026-11-02")
                .put("hours", "1")
                .put("billed", billed)
                .put("test_record", true)

        internal fun draft(
            route: String,
            price: String,
            source: String = "",
            longText: Boolean = false,
        ) =
            JSONObject()
                .put("route", route)
                .put("title", "[TEST] ${if (route == "billed") "Rechnung" else "Labor"} $price")
                .put("billing_customer_id", "qa_settlement_recipient")
                .put("service_from", "2026-11-02")
                .put("service_to", "2026-11-02")
                .put("payment_terms", "Binnen 14 Tagen")
                .put("payment_details", "Nur synthetisch: vereinbarte Überweisung")
                .put(
                    "positions",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("title", "Grünpflege Äpfel und Sträucher")
                                .put(
                                    "description",
                                    if (longText)
                                        "Synthetische Leistung mit Umlauten ÄÖÜ äöü ß.\n"
                                            .repeat(180)
                                    else "Frei erfundene Gartenleistung",
                                )
                                .put("quantity", "1")
                                .put("unit", "Leistung")
                                .put("price", price)
                                .put("source_collection", if (source.isEmpty()) "" else "worklogs")
                                .put("source_id", source)
                        ),
                )
    }

    private var checks = 0
    private val executedMcpTools = linkedSetOf<String>()

    private fun yes(condition: Boolean, code: String) {
        if (!condition) throw AssertionError("QA_NATIVE_SETTLEMENT_$code")
        checks++
    }

    private fun rejects(code: String, action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            checks++
            return
        }
        throw AssertionError("QA_NATIVE_SETTLEMENT_EXPECTED_REJECTION_$code")
    }

    private fun hash(value: Any) = PixelMcpWriteTools.hash(value)

    private fun result(reopen: Boolean) =
        JSONObject()
            .put("checks", checks)
            .put("executed_mcp_tools", JSONArray(executedMcpTools.toList()))
            .put("real_pixel_mcp_adapter", executedMcpTools.isNotEmpty())
            .put("synthetic_only", true)
            .put("productive_data_read", false)
            .put("new_process_readback", reopen)
            .put("real_sqlite_transactions", true)
            .put("isolated_context_paths", true)
            .put("native_pdf_verified", true)
            .put("real_main_activity", false)
            .put("physical_pixel_tested", false)

    private fun mcp(
        context: Context,
        store: PixelLocalStore,
        tool: String,
        args: JSONObject = JSONObject(),
    ): JSONObject {
        val response =
            PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
                .call(tool, args)
        yes(response.optBoolean("ok"), "PUBLIC_MCP_OK_$tool")
        executedMcpTools.add(tool)
        return response.getJSONObject("data")
    }

    private fun call(
        repository: LocalOwnerRepository,
        tool: String,
        args: JSONObject,
        request: String,
    ): JSONObject =
        repository.runRequest(request, hash(JSONObject().put("tool", tool).put("args", args))) {
            SettlementApplicationService(repository).perform(tool, args, request)
        }

    private fun get(repository: LocalOwnerRepository, id: String) =
        SettlementTools.query("get_settlement", JSONObject().put("settlement_id", id), repository)

    private fun save(repository: LocalOwnerRepository, draft: JSONObject, request: String) =
        call(repository, "save_settlement_draft", JSONObject().put("draft", draft), request)
            .getString("settlement_id")

    /** Seed an already retained synthetic original, without invoking a retired business action. */
    private fun historicalInternal(
        repository: LocalOwnerRepository,
        draft: JSONObject,
        fixtureId: String,
    ): String {
        val row =
            SettlementRules.review(
                    SettlementRules.normalized(draft, fixtureId),
                    repository.effective(),
                )
                .put("status", "confirmed")
                .put("issue_date", "2026-11-05")
                .put("confirmed_at", "2026-11-05T10:00:00Z")
        row.put("total_minor", SettlementRules.total(row))
        val ledger = SettlementRules.ledger(repository)
        val records =
            ledger.optJSONArray("records") ?: JSONArray().also { ledger.put("records", it) }
        records.put(row)
        repository.putOwnerValue(SettlementRules.KEY, ledger.toString())
        return fixtureId
    }

    private fun prepare(repository: LocalOwnerRepository, id: String, request: String): JSONObject {
        val review = get(repository, id)
        yes(review.getJSONObject("validation").getBoolean("valid"), "VALID_REVIEW_$request")
        return call(
            repository,
            "prepare_settlement",
            JSONObject()
                .put("settlement_id", id)
                .put("expected_review_hash", review.getString("review_hash"))
                .put("confirmed", true)
                .put("domestic_eur_confirmed", true)
                .put("issuer_details_confirmed", true)
                .put("electronic_delivery_accepted", true),
            request,
        )
    }

    private fun complete(repository: LocalOwnerRepository, id: String, request: String) =
        call(repository, "complete_settlement", JSONObject().put("settlement_id", id), request)

    private fun execute(reopen: Boolean): JSONObject {
        // Package and isolated-fixture checks precede synthetic storage access.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_PACKAGE")
        yes(test.targetContext.packageName == "at.hausgarten.service", "TARGET_PACKAGE")
        val owner = test.targetContext.createDeviceProtectedStorageContext()
        val pointer = File(owner.noBackupFilesDir, "qa-native-settlement-pointer.json")
        val token: String
        val root: File
        if (reopen) {
            yes(pointer.isFile, "REOPEN_POINTER")
            val saved = JSONObject(pointer.readText())
            token = saved.getString("token")
            yes(token.matches(Regex("[0-9a-f]{32}")), "OWNED_TOKEN")
            root = File(owner.cacheDir, "qa-native-settlement-$token")
            yes(root.isDirectory, "PERSISTED_OWNED_ROOT")
        } else {
            yes(!pointer.exists(), "FRESH_SUITE_POINTER")
            token = UUID.randomUUID().toString().replace("-", "")
            root = File(owner.cacheDir, "qa-native-settlement-$token")
            yes(!root.exists() && root.mkdir(), "NEW_SYNTHETIC_ROOT")
        }
        val context = FixtureContext(owner, root, "qa_settlement_$token")
        for (path in
            listOf(
                context.dataDir,
                context.filesDir,
                context.cacheDir,
                context.noBackupFilesDir,
                context.getDatabasePath("haus-garten-mobile.db"),
            )) yes(
            path.canonicalPath == root.canonicalPath ||
                path.canonicalPath.startsWith(root.canonicalPath + File.separator),
            "ISOLATED_PATH",
        )
        if (reopen) {
            val marker = JSONObject(pointer.readText())
            yes(!RestoreStartupProtection.requiresColdRestart(context), "COLD_RESTART_SATISFIED")
            PixelLocalStore(context).use { store ->
                val repository = LocalOwnerRepository(context, store)
                val overview = store.nativeData().accounting(JSONObject())
                yes(
                    overview.getLong("billed_minor") == 90000L &&
                        overview.getLong("labor_minor") == 20000L &&
                        overview.getLong("total_minor") == 110000L,
                    "900_PLUS_200_AFTER_NEW_PROCESS",
                )
                val original =
                    get(repository, marker.getString("invoice_id")).getJSONObject("record")
                val bytes = BusinessDocumentStore(context).read(original.getJSONObject("pdf"))
                yes(
                    PhotoAttachmentStore.sha256(bytes) == marker.getString("pdf_sha256"),
                    "UNCHANGED_ORIGINAL_AFTER_RESTORE_AND_REOPEN",
                )
                yes(
                    repository.settlementSeries() != marker.getString("series"),
                    "NEW_SERIES_OUTSIDE_RESTORED_COUNTERS",
                )
                val restored = SettlementRules.ledger(repository)
                yes(
                    SettlementRules.rows(restored.optJSONArray("restored_sources")).any {
                        it.optString("id") == "qa_settlement_old_unbound"
                    },
                    "RESTORED_UNBOUND_SOURCE_REQUIRES_REVIEW",
                )
                val old =
                    save(
                        repository,
                        draft("billed", "1", "qa_settlement_old_unbound"),
                        "qa_native_settlement_old_restore_draft",
                    )
                val review = get(repository, old)
                yes(
                    !review.getJSONObject("validation").getBoolean("valid") &&
                        review
                            .getJSONObject("validation")
                            .getJSONArray("errors")
                            .toString()
                            .contains("Restaurierte Quelle"),
                    "RESTORED_UNBOUND_SOURCE_NOT_AUTOMATICALLY_BILLABLE",
                )
                rejects("RESTORED_SOURCE_REQUIRES_REVIEW") {
                    call(
                        repository,
                        "prepare_settlement",
                        JSONObject()
                            .put("settlement_id", old)
                            .put("expected_review_hash", review.getString("review_hash"))
                            .put("confirmed", true)
                            .put("domestic_eur_confirmed", true)
                            .put("issuer_details_confirmed", true)
                            .put("electronic_delivery_accepted", true),
                        "qa_native_settlement_old_restore_prepare",
                    )
                }
                val master = JSONObject(repository.effective().toString())
                master.getJSONArray("worklogs").put(work("qa_settlement_after_restore", 0))
                store.putOwnerValue("snapshot", master.toString())
                val fresh =
                    save(
                        repository,
                        draft("billed", "1", "qa_settlement_after_restore"),
                        "qa_native_settlement_fresh_restore_draft",
                    )
                yes(
                    get(repository, fresh).getJSONObject("validation").getBoolean("valid"),
                    "NEW_AFTER_RESTORE_SOURCE_NORMAL",
                )
                val pending =
                    SettlementRules.records(SettlementRules.ledger(repository)).single {
                        it.optString("status") == "prepared"
                    }
                val pendingId = pending.getString("id")
                rejects("RESTORED_RESERVATION_NOT_SILENTLY_COMPLETED") {
                    complete(repository, pendingId, "qa_native_settlement_restored_complete")
                }
                yes(
                    get(repository, pendingId).getJSONObject("record").getString("status") ==
                        "prepared",
                    "RESTORED_RESERVATION_REMAINS_PENDING",
                )
                val beforeReview =
                    mcp(
                        context,
                        store,
                        "get_settlement",
                        JSONObject().put("settlement_id", pendingId),
                    )
                yes(
                    beforeReview.getBoolean("requires_restore_review"),
                    "PUBLIC_MCP_ACTUAL_RESTORE_REQUIRES_REVIEW",
                )
                // This tool's public schema has no expected_record_hash. The unchanged record hash
                // and current restore guard are checked before and after the explicit review.
                val reviewArgs =
                    JSONObject()
                        .put("request_id", "qa_native_settlement_public_restored_review")
                        .put("settlement_id", pendingId)
                        .put("sources", pending.getJSONArray("sources"))
                        .put(
                            "evidence",
                            "Synthetischer aktueller Abgleich: reservierte Nummer nur für diesen unveränderten Vorgang, noch nicht abgeschlossen; keine verlorenen späteren Belege.",
                        )
                        .put("confirmed", true)
                yes(
                    mcp(context, store, "review_restored_sources", reviewArgs)
                        .getBoolean("reviewed"),
                    "PUBLIC_MCP_ACTUAL_RESTORE_REVIEW_WRITTEN",
                )
                val reviewedLedger = store.ownerValue(SettlementRules.KEY, "{}")
                yes(
                    mcp(context, store, "review_restored_sources", reviewArgs)
                        .getBoolean("replayed") &&
                        store.ownerValue(SettlementRules.KEY, "{}") == reviewedLedger,
                    "PUBLIC_MCP_RESTORE_REVIEW_REPLAY_ADDS_NO_DUPLICATE_EVIDENCE",
                )
                val reviewConflict = runCatching {
                    mcp(
                        context,
                        store,
                        "review_restored_sources",
                        JSONObject(reviewArgs.toString())
                            .put(
                                "evidence",
                                "Synthetisch geänderter Nachweis mit derselben Anfragekennung",
                            ),
                    )
                }
                    .exceptionOrNull()
                yes(
                    reviewConflict is PixelMcpWriteTools.Rejected &&
                        reviewConflict.code == "REPLAY_MISMATCH" &&
                        store.ownerValue(SettlementRules.KEY, "{}") == reviewedLedger,
                    "PUBLIC_MCP_RESTORE_REVIEW_REQUEST_CONFLICT_PRESERVES_EVIDENCE",
                )
                val afterReview =
                    mcp(
                        context,
                        store,
                        "get_settlement",
                        JSONObject().put("settlement_id", pendingId),
                    )
                yes(
                    !afterReview.getBoolean("requires_restore_review") &&
                        afterReview.getString("record_hash") ==
                            beforeReview.getString("record_hash") &&
                        afterReview.getJSONObject("record").getString("invoice_number") ==
                            pending.getString("invoice_number"),
                    "PUBLIC_MCP_RESTORE_REVIEW_READBACK_UNBLOCKS_SAME_UNALTERED_RESERVATION",
                )
                complete(repository, pendingId, "qa_native_settlement_restored_complete")
                yes(
                    get(repository, pendingId)
                        .getJSONObject("record")
                        .getString("invoice_number") == pending.getString("invoice_number"),
                    "VERIFIED_RESTORED_RESERVATION_RESUMES_SAME_NUMBER",
                )
                val correction =
                    call(
                        repository,
                        "correct_settlement",
                        JSONObject()
                            .put("settlement_id", pendingId)
                            .put(
                                "expected_record_hash",
                                get(repository, pendingId).getString("record_hash"),
                            )
                            .put(
                                "reason",
                                "Synthetischer Wiederanlauf nach dokumentiertem Restoreabgleich",
                            )
                            .put("confirmed", true),
                        "qa_native_settlement_restored_cancel",
                    )
                complete(
                    repository,
                    correction.getString("settlement_id"),
                    "qa_native_settlement_restored_cancel_complete",
                )
                yes(
                    store.nativeData().accounting(JSONObject()).getLong("total_minor") == 110000L,
                    "RESTORE_RESUMPTION_CORRECTS_ONLY_ITS_OWN_AMOUNT",
                )
            }
            return result(true).put("native_backup_roundtrip", true).put("restore_guard", true)
        }
        manualWorkAndSharedRecipient(context)
        publicDraftAndReservationMcp(context)
        val backup = File(context.noBackupFilesDir, "qa-settlement.zip")
        val marker = JSONObject().put("token", token)
        PixelLocalStore(context).use { store ->
            store.putOwnerValue("snapshot", fixture().toString())
            store.putOwnerValue("personal_paid_migrated", "1")
            store.putOwnerValue("personal_paid", JSONArray().put("qa_settlement_labor").toString())
            val repository = LocalOwnerRepository(context, store)
            val initial = hash(repository.effective())
            val marks = store.ownerValue("personal_paid", "[]")
            val invoice =
                save(
                    repository,
                    draft("billed", "900,00", "qa_settlement_invoice", true),
                    "qa_native_settlement_invoice_draft",
                )
            previewDraftWithoutIssuing(context, repository, invoice)
            prepare(repository, invoice, "qa_native_settlement_invoice_prepare")
            val beforeComplete = get(repository, invoice).getJSONObject("record")
            yes(
                beforeComplete.getString("status") == "prepared" &&
                    beforeComplete.getString("invoice_number").startsWith("GP-"),
                "DURABLE_NUMBER_RESERVATION",
            )
            yes(!beforeComplete.has("pdf"), "RESERVED_NOT_ISSUED")
            complete(repository, invoice, "qa_native_settlement_invoice_complete")
            val original = get(repository, invoice).getJSONObject("record")
            val pdf = original.getJSONObject("pdf")
            val originalBytes = BusinessDocumentStore(context).read(pdf)
            yes(
                originalBytes.size > 100 &&
                    String(originalBytes, 0, 5, Charsets.US_ASCII) == "%PDF-",
                "ORIGINAL_PDF_BYTES",
            )
            val pdfFile =
                File(context.filesDir, "business-documents/" + pdf.getString("pdf_storage_name"))
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor
                ->
                PdfRenderer(descriptor).use { renderer ->
                    yes(renderer.pageCount > 1, "MULTIPAGE_UMLAUT_DOCUMENT_OPENED")
                }
            }
            val labor =
                historicalInternal(
                    repository,
                    draft("labor", "200,00", "qa_settlement_labor"),
                    "qa_native_settlement_labor_draft",
                )
            val internal = get(repository, labor).getJSONObject("record")
            yes(
                !internal.has("invoice_number") && !internal.has("pdf"),
                "LABOR_NO_ARTIFICIAL_INVOICE_OR_NUMBER",
            )
            var overview = store.nativeData().accounting(JSONObject())
            yes(
                overview.getLong("billed_minor") == 90000L &&
                    overview.getLong("labor_minor") == 20000L &&
                    overview.getLong("total_minor") == 110000L,
                "900_PLUS_200_EQUALS_1100",
            )
            yes(overview.getInt("total") == 2, "SOURCE_UNITS_EXACTLY_ONCE")
            yes(
                store
                    .nativeData()
                    .accounting(JSONObject().put("route", "labor"))
                    .getLong("total_minor") == 20000L,
                "ROUTE_FILTER_SAME_TOTAL",
            )
            yes(
                store
                    .nativeData()
                    .accounting(JSONObject().put("date_from", "2026-11-03"))
                    .getLong("total_minor") == 0L,
                "WORK_DATE_NOT_INVOICE_DATE",
            )
            yes(
                hash(repository.effective()) == initial &&
                    store.ownerValue("personal_paid", "[]") == marks,
                "CLOSURES_PRESERVE_ALL_USER_ASSIGNMENTS",
            )
            // Export must retain the existing marked accounting units even before
            // a priced closure exists; a document-only list would lose this work.
            store.putOwnerValue(
                "personal_paid",
                JSONArray(marks).put("qa_settlement_old_unbound").toString(),
            )
            val ledgerBeforeExport = store.ownerValue(SettlementRules.KEY, "{}")
            val fullExport = mcp(context, store, "export_settlements")
            val repeatedExport = mcp(context, store, "export_settlements")
            val stableExport = JSONObject(fullExport.toString()).apply { remove("exported_at") }
            repeatedExport.remove("exported_at")
            yes(
                hash(stableExport) == hash(repeatedExport) &&
                    store.ownerValue(SettlementRules.KEY, "{}") == ledgerBeforeExport,
                "PUBLIC_MCP_EXPORT_READ_REPEAT_PRESERVES_ORIGINALS_AND_LEDGER",
            )
            yes(
                !fullExport.getBoolean("filtered") &&
                    fullExport.getJSONObject("overview").getLong("total_minor") == 110000L,
                "FULL_EXPORT_BOTH_ROUTES_AND_TOTAL",
            )
            yes(
                fullExport.getJSONArray("records").length() == 2 &&
                    fullExport.getJSONObject("series_counters").length() == 1,
                "FULL_EXPORT_RECORDS_AND_SERIES",
            )
            val exportedUnits = SettlementRules.rows(fullExport.getJSONArray("accounting_units"))
            yes(
                exportedUnits.size == 3 &&
                    exportedUnits
                        .single { it.getString("id") == "qa_settlement_old_unbound" }
                        .isNull("amount_minor"),
                "EXPORT_RETAINS_MARKED_UNPRICED_UNIT_WITHOUT_INVENTED_ZERO",
            )
            val missingDraft =
                save(
                    repository,
                    draft("billed", "1", "qa_missing_draft_source"),
                    "qa_native_settlement_missing_draft",
                )
            yes(
                !get(repository, missingDraft).getJSONObject("validation").getBoolean("valid"),
                "MISSING_DRAFT_SOURCE_REMAINS_REVIEWABLE",
            )
            val filteredExport =
                mcp(context, store, "export_settlements", JSONObject().put("route", "labor"))
            yes(
                filteredExport.getBoolean("filtered") &&
                    filteredExport.getJSONObject("overview").getLong("total_minor") == 20000L,
                "FILTERED_EXPORT_SAME_SELECTION_AND_LABEL",
            )
            yes(
                filteredExport.getJSONArray("accounting_units").length() == 2 &&
                    filteredExport.getJSONObject("overview").getInt("pending_amounts") == 1,
                "FILTERED_EXPORT_RETAINS_PENDING_LABOR_AND_CONFIRMED_AMOUNT",
            )
            yes(
                filteredExport.getJSONArray("records").length() == 1 &&
                    filteredExport.getJSONArray("records").getJSONObject(0).getString("id") ==
                        labor,
                "UNRELATED_MISSING_DRAFT_DOES_NOT_BREAK_FILTERED_EXPORT",
            )
            val replay = complete(repository, invoice, "qa_native_settlement_invoice_complete")
            yes(replay.getBoolean("replayed"), "SAME_REQUEST_REPLAY")
            rejects("SAME_REQUEST_ARGUMENT_MISMATCH") {
                call(
                    repository,
                    "complete_settlement",
                    JSONObject().put("settlement_id", labor),
                    "qa_native_settlement_invoice_complete",
                )
            }
            paymentAndCorrection(context, store, repository)
            failureAndResume(context, store, repository)
            concurrentReservations(context, store)
            yes(
                store.nativeData().accounting(JSONObject()).getLong("total_minor") == 110000L,
                "OTHER_CORRECTIONS_PRESERVE_1100",
            )
            LocalStorageMaintenance(context, store).clean(false)
            yes(
                PhotoAttachmentStore.sha256(BusinessDocumentStore(context).read(pdf)) ==
                    PhotoAttachmentStore.sha256(originalBytes),
                "CLEANUP_PRESERVES_ISSUED_ORIGINAL",
            )
            yes(
                get(repository, labor).getJSONObject("record").getString("status") == "confirmed",
                "CLEANUP_PRESERVES_INTERNAL_HISTORY",
            )
            yes(backup.createNewFile(), "EMPTY_BACKUP_TARGET_CREATED")
            val provider =
                object : BackupDocumentTransfer.LocalFile {
                    override fun write() = FileOutputStream(backup)

                    override fun read() = FileInputStream(backup)
                }
            val saved =
                PixelBackupService(context, store).saveTo(
                    "qa_native_settlement_save",
                    "qa_synthetic_settlement_destination",
                    provider,
                ) {}
            yes(saved.getBoolean("verified"), "COMPLETE_LOCAL_ZIP_WRITTEN_AND_READ_BACK")
            marker
                .put("invoice_id", invoice)
                .put("pdf_sha256", PhotoAttachmentStore.sha256(originalBytes))
                .put("series", repository.settlementSeries())
        }
        BackupArchive.verifyPlain(
                FileInputStream(backup),
                File(context.noBackupFilesDir, "qa-check-settlement"),
                context.noBackupFilesDir.usableSpace,
                PixelLocalStore.SCHEMA_VERSION,
            )
            .use { verified ->
                SQLiteDatabase.openDatabase(
                        verified.file("database.sqlite").path,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    )
                    .use { database ->
                        val ledger =
                            JSONObject(
                                DatabaseBackupSnapshot.ownerValue(
                                    database,
                                    SettlementRules.KEY,
                                    "{}",
                                )!!
                            )
                        yes(
                            SettlementRules.records(ledger).any {
                                it.optString("id") == marker.getString("invoice_id")
                            },
                            "ZIP_PRESERVES_LEDGER",
                        )
                        yes(
                            ledger.getJSONArray("payments").length() == 3,
                            "ZIP_PRESERVES_ALL_PAYMENT_ENTRIES",
                        )
                        yes(
                            ledger.getJSONObject("series_counters").length() == 1,
                            "ZIP_PRESERVES_COUNTERS",
                        )
                    }
                val pdf =
                    verified.file(
                        "files/business-documents/" + marker.getString("pdf_sha256") + ".pdf"
                    )
                yes(
                    pdf.isFile &&
                        PhotoAttachmentStore.sha256(pdf.readBytes()) ==
                            marker.getString("pdf_sha256"),
                    "ZIP_PRESERVES_ORIGINAL_BYTES",
                )
            }
        val restore = BackupRestoreService(context)
        val preview =
            restore.previewFrom(
                "qa_native_settlement_restore_preview",
                "qa_synthetic_settlement_archive",
            ) {
                FileInputStream(backup)
            }
        restore.requestActivation(
            "qa_native_settlement_restore_activate",
            preview.getString("restore_id"),
            preview.getString("preview_hash"),
            true,
        )
        yes(
            BackupRestoreService.activatePending(context, RestoreStartupProtection(context))
                .getString("phase") == "ACTIVE",
            "LOCAL_ZIP_RESTORE_ACTIVATED",
        )
        pointer.writeText(marker.toString())
        return result(false)
            .put("native_backup_roundtrip", true)
            .put("parallel_reservations", true)
            .put("pdf_failure_resume", true)
            .put("payment_correction", true)
    }

    private fun publicDraftAndReservationMcp(parent: Context) {
        val root = File(parent.noBackupFilesDir, "qa-finance-public-drafts")
        yes(!root.exists() && root.mkdir(), "PUBLIC_MCP_NEW_ISOLATED_DRAFT_ROOT")
        val context = FixtureContext(parent, root, "qa_finance_public_drafts")
        try {
            PixelLocalStore(context).use { store ->
                store.putOwnerValue("snapshot", fixture().toString())
                store.putOwnerValue("personal_paid_migrated", "1")
                store.putOwnerValue("personal_paid", "[]")
                val originalSources = hash(PixelMcpReadTools.effective(store.getState()))
                fun request(id: String) = JSONObject().put("request_id", "qa_public_finance_$id")
                fun call(tool: String, args: JSONObject = JSONObject()) =
                    mcp(context, store, tool, args)
                fun current(id: String) =
                    call("get_settlement", JSONObject().put("settlement_id", id))
                fun create(source: String, id: String) =
                    call(
                            "save_settlement_draft",
                            request(id).put("draft", draft("billed", "10", source)),
                        )
                        .getString("settlement_id")
                fun rejectedMutation(tool: String, args: JSONObject, code: String) {
                    val prior = store.ownerValue(SettlementRules.KEY, "{}")
                    val failure = runCatching { call(tool, args) }.exceptionOrNull()
                    yes(
                        failure is IllegalArgumentException &&
                            store.ownerValue(SettlementRules.KEY, "{}") == prior,
                        "PUBLIC_MCP_REJECTED_${tool}_$code",
                    )
                }
                val deleted = create("qa_settlement_invoice", "delete_draft")
                val firstVersion = current(deleted)
                val changedDraft =
                    draft("billed", "10", "qa_settlement_invoice")
                        .put("title", "[TEST] Bewusst überarbeiteter Entwurf")
                call(
                    "save_settlement_draft",
                    request("edit_before_delete")
                        .put("settlement_id", deleted)
                        .put("expected_record_hash", firstVersion.getString("record_hash"))
                        .put("draft", changedDraft),
                )
                val deletion =
                    request("delete")
                        .put("settlement_id", deleted)
                        .put("confirmed", true)
                        .put("expected_record_hash", firstVersion.getString("record_hash"))
                rejectedMutation("delete_settlement_draft", deletion, "STALE_HASH")
                deletion
                    .put("request_id", "qa_public_finance_delete_current")
                    .put("expected_record_hash", current(deleted).getString("record_hash"))
                yes(
                    call("delete_settlement_draft", deletion).getBoolean("deleted"),
                    "PUBLIC_MCP_DRAFT_DELETED",
                )
                val afterDeletion = store.ownerValue(SettlementRules.KEY, "{}")
                yes(
                    call("delete_settlement_draft", deletion).getBoolean("replayed") &&
                        store.ownerValue(SettlementRules.KEY, "{}") == afterDeletion,
                    "PUBLIC_MCP_DRAFT_DELETE_REPLAY_HAS_NO_SECOND_EFFECT",
                )
                yes(
                    SettlementRules.rows(call("list_settlements").getJSONArray("records")).none {
                        it.getString("id") == deleted
                    } &&
                        runCatching { current(deleted) }.exceptionOrNull() is
                            IllegalArgumentException,
                    "PUBLIC_MCP_DELETED_DRAFT_ABSENT_FROM_LIST_AND_DIRECT_READ",
                )
                val pending = create("qa_settlement_race", "abandon_draft")
                val review = current(pending)
                call(
                    "prepare_settlement",
                    request("prepare_abandon")
                        .put("settlement_id", pending)
                        .put("expected_review_hash", review.getString("review_hash"))
                        .put("confirmed", true)
                        .put("domestic_eur_confirmed", true)
                        .put("issuer_details_confirmed", true)
                        .put("electronic_delivery_accepted", true),
                )
                val prepared = current(pending)
                val reservedNumber = prepared.getJSONObject("record").getString("invoice_number")
                val reservedCounters =
                    JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))
                        .getJSONObject("series_counters")
                        .toString()
                rejectedMutation(
                    "delete_settlement_draft",
                    request("delete_prepared_denied")
                        .put("settlement_id", pending)
                        .put("expected_record_hash", prepared.getString("record_hash"))
                        .put("confirmed", true),
                    "PREPARED_PROTECTED",
                )
                val abandon =
                    request("abandon")
                        .put("settlement_id", pending)
                        .put("expected_record_hash", prepared.getString("record_hash"))
                        .put(
                            "reason",
                            "[TEST] Bewusst aufgegebene noch nicht ausgestellte Vorbereitung",
                        )
                        .put("confirmed", true)
                rejectedMutation(
                    "abandon_prepared_settlement",
                    JSONObject(abandon.toString())
                        .put("request_id", "qa_public_finance_abandon_stale")
                        .put("expected_record_hash", review.getString("record_hash")),
                    "STALE_HASH",
                )
                call("abandon_prepared_settlement", abandon)
                val abandoned = current(pending).getJSONObject("record")
                yes(
                    abandoned.getString("status") == "abandoned" &&
                        abandoned.getString("invoice_number") == reservedNumber &&
                        abandoned.getString("abandon_reason") == abandon.getString("reason") &&
                        !abandoned.has("pdf") &&
                        JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))
                            .getJSONObject("series_counters")
                            .toString() == reservedCounters &&
                        call(
                                "get_lcr_work_records",
                                JSONObject().put("work_record_id", "qa_settlement_race"),
                            )
                            .getJSONArray("records")
                            .getJSONObject(0)
                            .getBoolean("lcr_mutable"),
                    "PUBLIC_MCP_ABANDON_READBACK_PRESERVES_NUMBER_RELEASES_SOURCE_WITHOUT_ISSUED_PDF",
                )
                val afterAbandon = store.ownerValue(SettlementRules.KEY, "{}")
                yes(
                    call("abandon_prepared_settlement", abandon).getBoolean("replayed") &&
                        store.ownerValue(SettlementRules.KEY, "{}") == afterAbandon,
                    "PUBLIC_MCP_ABANDON_REPLAY_HAS_NO_SECOND_EFFECT",
                )
                yes(
                    hash(PixelMcpReadTools.effective(store.getState())) == originalSources &&
                        SettlementRules.rows(JSONObject(afterAbandon).optJSONArray("payments"))
                            .isEmpty(),
                    "PUBLIC_MCP_DELETE_AND_ABANDON_PRESERVE_ORIGINAL_WORK_AND_NEVER_BOOK_PAYMENT",
                )
            }
        } finally {
            yes(root.deleteRecursively(), "PUBLIC_MCP_ISOLATED_DRAFT_FIXTURE_REMOVED")
        }
    }

    private fun manualWorkAndSharedRecipient(parent: Context) {
        val root = File(parent.noBackupFilesDir, "qa-finance-source-workflows")
        yes(!root.exists() && root.mkdir(), "NEW_ISOLATED_SOURCE_WORKFLOW_ROOT")
        val context = FixtureContext(parent, root, "qa_finance_source_workflows")
        try {
            PixelLocalStore(context).use { store ->
                val source = fixture().put("worklogs", JSONArray())
                val recipient = "qa_settlement_recipient"
                val siteA = "qa_finance_site_a"
                val siteB = "qa_finance_site_b"
                val foreign = "qa_finance_foreign_recipient"
                val recipientRow = source.getJSONArray("customers").getJSONObject(0)
                fun customer(id: String, name: String, via: String = "") =
                    JSONObject(recipientRow.toString())
                        .put("id", id)
                        .put("name", "[TEST] $name")
                        .put("billing_mode", if (via.isEmpty()) "direct" else "via_customer")
                        .put("default_billing_customer_id", via)
                source
                    .getJSONArray("customers")
                    .put(customer(siteA, "Erster Einsatzkunde", recipient))
                    .put(customer(siteB, "Zweiter Einsatzkunde", recipient))
                    .put(customer(foreign, "Anderer Rechnungsempfänger"))
                store.putOwnerValue("snapshot", source.toString())
                store.putOwnerValue("personal_paid_migrated", "1")
                store.putOwnerValue("personal_paid", "[]")
                val repository = LocalOwnerRepository(context, store)
                fun property(customer: String, request: String) =
                    repository
                        .revierAction(
                            "create_property",
                            JSONObject()
                                .put("request_id", request)
                                .put("customer_id", customer)
                                .put("name", "[TEST] Eigenständiges Kundenobjekt"),
                        )
                        .getString("property_id")
                val propertyA = property(siteA, "qa_finance_property_a")
                val propertyB = property(siteB, "qa_finance_property_b")
                fun workArgs(request: String, customer: String, property: String, hours: Double) =
                    JSONObject()
                        .put("request_id", request)
                        .put("customer_id", customer)
                        .put("property_id", property)
                        .put("work_date", "2026-11-02")
                        .put("activity", "[TEST] Tatsächlich nachgetragene Arbeit $customer")
                        .put("hours", hours)
                fun original(id: String) =
                    kotlinx.coroutines.runBlocking {
                        requireNotNull(store.nativeData().detail("worklogs", id))
                    }
                val ledgerBeforeWork = repository.ownerValue(SettlementRules.KEY, "{}")
                val documents = File(context.filesDir, "business-documents")
                val documentNames = documents.list()?.sorted().orEmpty()
                val firstArgs = workArgs("qa_finance_manual_work", siteA, propertyA, 1.5)
                val first =
                    store.execute("create_customer_work_record", firstArgs).getJSONObject("data")
                val workA = first.getString("work_record_id")
                val replay =
                    store.execute("create_customer_work_record", firstArgs).getJSONObject("data")
                val savedA = original(workA)
                yes(
                    replay.getBoolean("replayed") &&
                        replay.getString("work_record_id") == workA &&
                        repository.effective().getJSONArray("worklogs").length() == 1,
                    "MANUAL_WORK_REPLAY_RETAINS_EXACTLY_ONE_ORIGINAL_ID",
                )
                yes(
                    savedA.getString("id") == workA &&
                        savedA.getString("customer_id") == siteA &&
                        savedA.getString("property_id") == propertyA &&
                        savedA.optString("order_id").isEmpty() &&
                        savedA.getString("billing_customer_id") == recipient &&
                        savedA.getDouble("hours") == 1.5,
                    "MANUAL_WORK_WITH_CUSTOMER_AND_OBJECT_NEEDS_NO_ORDER_AND_READS_BACK_FROM_SQLITE",
                )
                yes(
                    repository.ownerValue(SettlementRules.KEY, "{}") == ledgerBeforeWork &&
                        repository.effective().getJSONArray("invoices").length() == 0 &&
                        documents.list()?.sorted().orEmpty() == documentNames,
                    "MANUAL_WORK_CREATES_NO_SETTLEMENT_PAYMENT_INVOICE_OR_ORIGINAL_DOCUMENT",
                )
                val workB =
                    store
                        .execute(
                            "create_customer_work_record",
                            workArgs("qa_finance_second_site_work", siteB, propertyB, 2.0),
                        )
                        .getJSONObject("data")
                        .getString("work_record_id")
                val otherWork =
                    store
                        .execute(
                            "create_customer_work_record",
                            workArgs("qa_finance_foreign_work", foreign, "", 0.75),
                        )
                        .getJSONObject("data")
                        .getString("work_record_id")
                val master = repository.effective()
                val originalWorks = hash(master.getJSONArray("worklogs"))
                val input =
                    JSONObject()
                        .put("billing_customer_id", recipient)
                        .put("work_record_ids", JSONArray().put(workA).put(workB).put(otherWork))
                val candidate =
                    SettlementRules.prefill(
                            input,
                            master.getJSONArray("worklogs"),
                            SettlementRules.records(SettlementRules.ledger(repository)),
                            "40",
                        )
                        .put("title", "[TEST] Zwei Einsatzkunden mit gemeinsamem Empfänger")
                        .put("payment_terms", "Binnen 14 Tagen")
                        .put("payment_details", "Synthetisch vereinbarte Überweisung")
                val settlement = save(repository, candidate, "qa_finance_shared_recipient_draft")
                val saved = get(repository, settlement)
                val record = saved.getJSONObject("record")
                val positions = SettlementRules.rows(record.getJSONArray("positions"))
                yes(
                    positions.size == 2 &&
                        positions.map { it.getString("source_id") }.toSet() ==
                            setOf(workA, workB) &&
                        positions.all { it.getString("source_collection") == "worklogs" } &&
                        record.getJSONArray("sources").length() == 2 &&
                        record.getString("billing_customer_id") == recipient &&
                        record.optString("customer_id").isEmpty() &&
                        saved.getJSONObject("validation").getBoolean("valid") &&
                        record.getLong("total_minor") == 14000L,
                    "SHARED_RECIPIENT_DRAFT_SELECTS_TWO_ORIGINAL_SITE_WORKS_AND_EXCLUDES_FOREIGN_RECIPIENT",
                )
                val retainedA = original(workA)
                val retainedB = original(workB)
                val retainedForeign = original(otherWork)
                yes(
                    retainedA.getString("customer_id") == siteA &&
                        retainedB.getString("customer_id") == siteB &&
                        retainedA.getString("billing_customer_id") == recipient &&
                        retainedB.getString("billing_customer_id") == recipient &&
                        retainedForeign.getString("customer_id") == foreign &&
                        retainedForeign.getString("billing_customer_id") == foreign &&
                        hash(repository.effective().getJSONArray("worklogs")) == originalWorks,
                    "SHARED_RECIPIENT_DRAFT_PRESERVES_DISTINCT_SITE_CUSTOMERS_AND_ALL_ORIGINAL_WORKS",
                )
                yes(
                    SettlementRules.records(SettlementRules.ledger(repository)).size == 1 &&
                        record.getString("status") == "draft" &&
                        !record.has("invoice_number") &&
                        !record.has("pdf") &&
                        SettlementRules.rows(
                                SettlementRules.ledger(repository).optJSONArray("payments")
                            )
                            .isEmpty() &&
                        documents.list()?.sorted().orEmpty() == documentNames,
                    "SHARED_RECIPIENT_PREPARATION_REMAINS_ONE_DRAFT_WITHOUT_ISSUING_OR_PAYMENT",
                )
            }
        } finally {
            yes(root.deleteRecursively(), "ISOLATED_SOURCE_WORKFLOW_DATA_REMOVED")
        }
    }

    private fun previewDraftWithoutIssuing(
        context: Context,
        repository: LocalOwnerRepository,
        id: String,
    ) {
        val before = repository.ownerValue(SettlementRules.KEY, "{}")
        val originals = File(context.filesDir, "business-documents")
        val originalNames = originals.list()?.sorted().orEmpty()
        val metadata =
            SettlementTools.query(
                    "get_settlement_pdf",
                    JSONObject().put("settlement_id", id).put("preview", true),
                    repository,
                )
                .getJSONObject("metadata")
        yes(metadata.getBoolean("preview_only"), "DRAFT_PDF_IS_EXPLICITLY_PREVIEW_ONLY")
        val cache = File(context.cacheDir, "offer-share").canonicalFile
        val file = File(cache, metadata.getString("pdf_filename")).canonicalFile
        try {
            yes(file.parentFile == cache && file.isFile, "DRAFT_PDF_EXISTS_ONLY_IN_OWNED_CACHE")
            val bytes = file.readBytes()
            yes(
                bytes.size > 100 &&
                    String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-" &&
                    bytes.size.toLong() == metadata.getLong("pdf_byte_size") &&
                    PhotoAttachmentStore.sha256(bytes) == metadata.getString("pdf_sha256"),
                "DRAFT_PDF_BYTES_SIZE_AND_HASH_READ_BACK",
            )
            yes(
                metadata.getString("content_uri") ==
                    "content://${context.packageName}.offerfiles/${file.name}",
                "DRAFT_PDF_METADATA_REFERENCES_GENERATED_CACHE_FILE",
            )
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    yes(renderer.pageCount > 1, "MULTIPAGE_DRAFT_PDF_OPENED_BY_ANDROID_RENDERER")
                    renderer.openPage(0).use { page ->
                        yes(page.width > 0 && page.height > 0, "DRAFT_PDF_FIRST_PAGE_IS_READABLE")
                    }
                }
            }
            yes(
                repository.ownerValue(SettlementRules.KEY, "{}") == before,
                "DRAFT_PREVIEW_DOES_NOT_CHANGE_ANY_LEDGER_BYTES",
            )
            val ledger = SettlementRules.ledger(repository)
            val draft = SettlementRules.find(ledger, id)
            yes(
                draft.getString("status") == "draft" &&
                    !draft.has("invoice_number") &&
                    !draft.has("pdf") &&
                    SettlementRules.records(ledger).none(SettlementRules::active) &&
                    (ledger.optJSONObject("series_counters")?.length() ?: 0) == 0,
                "DRAFT_PREVIEW_RESERVES_NO_NUMBER_BINDS_NO_SOURCE_AND_ISSUES_NO_ORIGINAL",
            )
            yes(
                originals.list()?.sorted().orEmpty() == originalNames,
                "DRAFT_PREVIEW_CREATES_NO_FILE_IN_ORIGINAL_DOCUMENT_STORAGE",
            )
        } finally {
            if (file.isFile) yes(file.delete(), "OWNED_DRAFT_PREVIEW_CACHE_FILE_REMOVED")
        }
    }

    private fun paymentAndCorrection(
        context: Context,
        store: PixelLocalStore,
        repository: LocalOwnerRepository,
    ) {
        val id = save(repository, draft("billed", "10"), "qa_native_settlement_payment_draft")
        prepare(repository, id, "qa_native_settlement_payment_prepare")
        complete(repository, id, "qa_native_settlement_payment_complete")
        val original = get(repository, id).getJSONObject("record")
        val pdfHash = original.getJSONObject("pdf").getString("pdf_sha256")
        for ((index, amount) in listOf("4,00", "8,00").withIndex()) call(
            repository,
            "add_settlement_payment",
            JSONObject()
                .put("settlement_id", id)
                .put("expected_record_hash", get(repository, id).getString("record_hash"))
                .put("amount", amount)
                .put("date", "2026-11-03")
                .put("note", "Synthetischer Teilbetrag $index"),
            "qa_native_settlement_payment_$index",
        )
        yes(
            get(repository, id).getLong("paid_minor") == 1200L &&
                get(repository, id).getLong("balance_minor") == -200L,
            "PARTIAL_AND_OVERPAYMENT",
        )
        val payment = get(repository, id).getJSONArray("payments").getJSONObject(0).getString("id")
        call(
            repository,
            "add_settlement_payment",
            JSONObject()
                .put("settlement_id", id)
                .put("expected_record_hash", get(repository, id).getString("record_hash"))
                .put("amount", "-1,00")
                .put("date", "2026-11-04")
                .put("note", "Synthetisch bestätigte Gegenbuchung")
                .put("reversal_of", payment),
            "qa_native_settlement_reversal",
        )
        val correction =
            call(
                repository,
                "correct_settlement",
                JSONObject()
                    .put("settlement_id", id)
                    .put("expected_record_hash", get(repository, id).getString("record_hash"))
                    .put("reason", "Vollständige synthetische Korrektur")
                    .put("confirmed", true),
                "qa_native_settlement_cancellation",
            )
        val correctionId = correction.getString("settlement_id")
        yes(
            correction.getJSONObject("record").getString("invoice_number") !=
                original.getString("invoice_number"),
            "STORNO_OWN_RESERVED_NUMBER",
        )
        complete(repository, correctionId, "qa_native_settlement_cancellation_complete")
        yes(
            get(repository, id).getLong("paid_minor") == 1100L &&
                get(repository, id).getLong("balance_minor") == -1100L,
            "CANCELLATION_NEVER_DELETES_OR_REFUNDS_PAYMENT",
        )
        yes(
            get(repository, id)
                .getJSONObject("record")
                .getJSONObject("pdf")
                .getString("pdf_sha256") == pdfHash,
            "CANCELLATION_PRESERVES_ORIGINAL",
        )
        val internal =
            historicalInternal(
                repository,
                draft("labor", "7"),
                "qa_native_settlement_void_original",
            )
        val void =
            call(
                repository,
                "correct_settlement",
                JSONObject()
                    .put("settlement_id", internal)
                    .put("expected_record_hash", get(repository, internal).getString("record_hash"))
                    .put("reason", "Synthetische interne Aufhebung")
                    .put("confirmed", true),
                "qa_native_settlement_void",
            )
        yes(
            void.getJSONObject("record").getString("kind") == "void" &&
                !void.getJSONObject("record").has("invoice_number") &&
                !void.getJSONObject("record").has("pdf"),
            "INTERNAL_VOID_NO_FABRICATED_RECEIPT",
        )
    }

    private fun failureAndResume(
        context: Context,
        store: PixelLocalStore,
        repository: LocalOwnerRepository,
    ) {
        val id = save(repository, draft("billed", "5"), "qa_native_settlement_failure_draft")
        prepare(repository, id, "qa_native_settlement_failure_prepare")
        val before = get(repository, id).getJSONObject("record")
        val broken =
            object : OwnerRepository by repository {
                override fun createSettlementDocument(record: JSONObject): JSONObject =
                    throw IOException("QA_SYNTHETIC_PDF_FAILURE")
            }
        val args = JSONObject().put("settlement_id", id)
        rejects("PDF_ERROR") {
            repository.runRequest("qa_native_settlement_pdf_retry", hash(args)) {
                SettlementApplicationService(broken)
                    .perform("complete_settlement", args, "qa_native_settlement_pdf_retry")
            }
        }
        yes(
            get(repository, id).getJSONObject("record").getString("status") == "prepared" &&
                get(repository, id).getJSONObject("record").getString("invoice_number") ==
                    before.getString("invoice_number"),
            "PDF_FAILURE_RETAINS_NUMBER_AND_FROZEN_CONTENT",
        )
        val staging = File(context.cacheDir, "immutable-document-staging")
        yes(
            !staging.exists() || staging.isDirectory && staging.listFiles()?.isEmpty() == true,
            "OWNED_EMPTY_DOCUMENT_STAGING",
        )
        if (staging.exists()) yes(staging.delete(), "REMOVE_OWNED_EMPTY_STAGING")
        staging.writeText("[TEST] Synthetischer Dateifehler")
        try {
            val failure = runCatching {
                PixelOwnerAccess(context, store)
                    .execute(
                        "complete_settlement",
                        JSONObject()
                            .put("request_id", "qa_native_settlement_ui_pdf_failure")
                            .put("settlement_id", id),
                    )
            }
                .exceptionOrNull()
            yes(
                failure is PixelMcpWriteTools.Rejected &&
                    failure.code == "SETTLEMENT_DOCUMENT_FAILED",
                "PDF_FAILURE_HAS_VERIFIED_PREPARED_STATE",
            )
            yes(
                McpErrors.bridge(failure as Exception)
                    .getJSONObject("error")
                    .getString("commit_state") == "not_committed",
                "UI_CAN_RETRY_OR_EXPLICITLY_ABANDON_FAILED_PREPARATION",
            )
        } finally {
            yes(staging.delete() && staging.mkdirs(), "RESTORE_OWNED_STAGING")
        }
        store.writableDatabase.execSQL(
            "CREATE TRIGGER qa_settlement_receipt_abort BEFORE INSERT ON owner_state WHEN NEW.key='receipt:qa_native_settlement_pdf_retry' BEGIN SELECT RAISE(ABORT,'QA settlement commit abort after PDF publication'); END"
        )
        try {
            rejects("COMMIT_ABORT_AFTER_PDF") {
                repository.runRequest("qa_native_settlement_pdf_retry", hash(args)) {
                    SettlementApplicationService(repository)
                        .perform("complete_settlement", args, "qa_native_settlement_pdf_retry")
                }
            }
            yes(
                get(repository, id).getJSONObject("record").getString("status") == "prepared" &&
                    get(repository, id).getJSONObject("record").getString("invoice_number") ==
                        before.getString("invoice_number"),
                "SQLITE_ABORT_DOES_NOT_ISSUE_OR_REUSE_NUMBER",
            )
        } finally {
            store.writableDatabase.execSQL("DROP TRIGGER qa_settlement_receipt_abort")
        }
        repository.runRequest("qa_native_settlement_pdf_retry", hash(args)) {
            SettlementApplicationService(repository)
                .perform("complete_settlement", args, "qa_native_settlement_pdf_retry")
        }
        val corrected =
            call(
                repository,
                "correct_settlement",
                JSONObject()
                    .put("settlement_id", id)
                    .put("expected_record_hash", get(repository, id).getString("record_hash"))
                    .put("reason", "Synthetischer Fehlerpfad abgeschlossen")
                    .put("confirmed", true),
                "qa_native_settlement_failure_correct",
            )
        complete(
            repository,
            corrected.getString("settlement_id"),
            "qa_native_settlement_failure_correct_complete",
        )
    }

    private fun concurrentReservations(context: Context, store: PixelLocalStore) {
        val first = LocalOwnerRepository(context, store)
        val a =
            save(first, draft("billed", "2", "qa_settlement_race"), "qa_native_settlement_race_a")
        val b =
            save(first, draft("billed", "2", "qa_settlement_race"), "qa_native_settlement_race_b")
        val reviews = listOf(get(first, a), get(first, b))
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val success = AtomicInteger()
        val denied = AtomicInteger()
        for ((index, id) in listOf(a, b).withIndex()) Thread {
            try {
                start.await(10, TimeUnit.SECONDS)
                PixelLocalStore(context).use { secondStore ->
                    val repository = LocalOwnerRepository(context, secondStore)
                    call(
                        repository,
                        "prepare_settlement",
                        JSONObject()
                            .put("settlement_id", id)
                            .put(
                                "expected_review_hash",
                                reviews[index].getString("review_hash"),
                            )
                            .put("confirmed", true)
                            .put("domestic_eur_confirmed", true)
                            .put("issuer_details_confirmed", true)
                            .put("electronic_delivery_accepted", true),
                        "qa_native_settlement_race_prepare_$index",
                    )
                    success.incrementAndGet()
                }
            } catch (_: Exception) {
                denied.incrementAndGet()
            } finally {
                done.countDown()
            }
        }
            .start()
        start.countDown()
        yes(done.await(30, TimeUnit.SECONDS), "PARALLEL_WRITERS_FINISH")
        yes(success.get() == 1 && denied.get() == 1, "PARALLEL_SOURCE_RESERVED_EXACTLY_ONCE")
    }

    private class FixtureContext(
        private val owner: Context,
        private val root: File,
        private val prefix: String,
    ) : ContextWrapper(owner) {
        override fun getApplicationContext(): Context = this

        override fun getDataDir(): File = directory("")

        override fun getFilesDir(): File = directory("files")

        override fun getNoBackupFilesDir(): File = directory("no_backup")

        override fun getCacheDir(): File = directory("cache")

        override fun getDatabasePath(name: String): File {
            require(name == "haus-garten-mobile.db")
            return File(directory("databases"), name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
        ) = openOrCreateDatabase(name, mode, factory, null)

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
            require(name.matches(Regex("[A-Za-z0-9_]+")))
            return owner.getSharedPreferences("${prefix}_$name", mode)
        }

        private fun directory(name: String): File =
            (if (name.isEmpty()) root else File(root, name)).also {
                if (!it.isDirectory && !it.mkdirs())
                    throw IOException("QA_OWNED_DIRECTORY_REQUIRED")
            }
    }
}
