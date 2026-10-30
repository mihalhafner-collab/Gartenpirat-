package at.hausgarten.service

import android.content.Context
import java.io.*
import java.math.*
import java.time.*
import java.util.*
import org.json.*

/**
 * Local invoice snapshots, document outbox and append-only payment allocations in the existing DB.
 */
internal class LocalInvoiceService {
    private val context: Context
    private val store: PixelLocalStore
    private val clock: java.time.Clock

    constructor(
        context: Context,
        store: PixelLocalStore,
    ) : this(context, store, java.time.Clock.system(ZoneId.of("Europe/Vienna"))) {}

    constructor(context: Context, store: PixelLocalStore, clock: java.time.Clock) {
        this.context = context
        this.store = store
        this.clock = clock
    }

    @Throws(Exception::class)
    fun performIfLocal(name: String, args: JSONObject, request: String): JSONObject? {
        if (!LocalAuthorityGuard.local(context)) {
            return null
        }
        when (name) {
            "request_invoice_issue" -> {
                return issue(args, request)
            }
            "create_invoice_correction" -> {
                return correction(args, request)
            }
            "prepare_invoice_share" -> {
                return prepareShare(args)
            }
            "update_invoice_payment" -> {
                throw IOException("PAYMENT_AMOUNT_AND_ALLOCATION_REQUIRED")
            }
            "record_payment",
            "correct_payment" -> {
                return payment(name, args, request)
            }
            "retry_invoice_document_job" -> {
                return retryDocument(args, request)
            }
            else -> {
                return null
            }
        }
    }

    @Throws(Exception::class)
    fun query(name: String, args: JSONObject): JSONObject {
        if (name!!.equals("get_payment_ledger")) {
            return ledger(args)
        }
        if (name!!.equals("get_invoice_document_job")) {
            var invoice = invoice(args!!.getString("invoice_id"))
            var raw = store!!.ownerValue(("local_invoice_job:" + invoice!!.getString("id")), "")
            var recorded =
                invoice!!.optString(
                    "pdf_state",
                    (if ((invoice!!.optString("pdf_sha256"))!!.isEmpty()) "MISSING" else "READY"),
                )
            var result: JSONObject =
                ((((((JSONObject())!!.put("invoice_id", invoice!!.getString("id")))!!.put(
                                    "record_hash",
                                    OwnerJournalRules.version(invoice),
                                ))!!
                                .put("recorded_pdf_state", recorded))!!
                            .put("pdf_state", recorded))!!
                        .put("original_verified", false))!!
                    .put(
                        "job",
                        (if (raw!!.isEmpty()) JSONObject.NULL else safeJob(JSONObject(raw))),
                    )
            if (recorded!!.equals("READY")) {
                try {
                    verifiedInvoice(invoice)
                    result!!.put("original_verified", true)
                } catch (missing: Exception) {
                    (result!!.put("pdf_state", "ORIGINAL_REVIEW_REQUIRED"))!!.put(
                        "error_code",
                        "INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED",
                    )
                }
            }
            return result
        }
        throw IllegalArgumentException("LOCAL_FINANCIAL_QUERY_UNKNOWN")
    }

    private @Throws(Exception::class) fun issue(args: JSONObject, request: String): JSONObject {
        var guard: LocalAuthorityGuard = LocalAuthorityGuard(context)
        guard!!.requireInvoiceAuthority()
        var db = store!!.getWritableDatabase()
        store!!.beginBusinessTransaction(db)
        try {
            var all = materialize()
            var current =
                PixelMcpWriteTools.find(
                    all!!.getJSONArray("invoices"),
                    args!!.getString("invoice_id"),
                )
            TestDataRules.noDelivery(current, all)
            if (IssuedInvoiceRules.isIssued(current)) {
                if (request!!.equals(current!!.optString("local_issue_request"))) {
                    db!!.setTransactionSuccessful()
                    return (issuedResult(current))!!.put("replayed", true)
                }
                throw IOException("INVOICE_ALREADY_ISSUED")
            }
            if (
                !(PixelMcpWriteTools.hash(
                        PixelMcpEntityWriteTools.recordForHash("invoice", current)
                    ))!!
                    .equals(args!!.getString("expected_record_hash"))
            ) {
                throw IOException("WRITE_CONFLICT")
            }
            var issueReview = InvoiceIssueReviewSnapshot.capture(store, current!!.getString("id"))
            if (
                !(issueReview!!.getString("record_hash"))!!.equals(
                    args!!.getString("expected_record_hash")
                )
            ) {
                throw IOException("WRITE_CONFLICT")
            }
            InvoiceIssueReviewRules.requireCurrent(
                issueReview!!.getJSONObject("issue_review"),
                args!!.optString("expected_context_hash"),
            )
            var payload: JSONObject =
                (((((JSONObject())!!.put("id", current!!.getString("id")))!!.put(
                                "confirmed",
                                args!!.optBoolean("confirmed"),
                            ))!!
                            .put(
                                "_expected_invoice",
                                DriveJournalRules.guard(current, "invoice"),
                            ))!!
                        .put(
                            "_expected_settings_hash",
                            PixelMcpWriteTools.hash(all!!.getJSONObject("business_settings")),
                        ))!!
                    .put(
                        "_expected_recipient_hash",
                        OwnerJournalRules.version(
                            PixelMcpWriteTools.find(
                                all!!.getJSONArray("customers"),
                                current!!.getString("customer_id"),
                            )
                        ),
                    )
            var expected: JSONObject = JSONObject()
            var links = current!!.optJSONArray("worklog_ids")
            run {
                var i: Int = 0
                while (((links !== null) && (i < links!!.length()))) {
                    expected!!.put(
                        links!!.getString(i),
                        DriveJournalRules.guard(
                            PixelMcpWriteTools.find(
                                all!!.getJSONArray("worklogs"),
                                links!!.getString(i),
                            ),
                            "worklog",
                        ),
                    )

                    i++
                }
            }
            payload!!.put("_expected_worklogs", expected)
            var validation: JSONObject = JSONObject(all!!.toString())
            if (("cancellation")!!.equals(current!!.optString("document_type"))) {
                clearActiveSources(
                    PixelMcpWriteTools.find(
                        validation!!.getJSONArray("invoices"),
                        current!!.getString("correction_of"),
                    )
                )
            }
            run {
                var i: Int = 0
                while ((i < (validation!!.getJSONArray("invoices"))!!.length())) {
                    var other = (validation!!.getJSONArray("invoices"))!!.getJSONObject(i)
                    var saved =
                        store!!.ownerValue(("local_invoice_sources:" + other!!.getString("id")), "")
                    if ((!saved!!.isEmpty() && IssuedInvoiceRules.isIssued(other))) {
                        other!!.put("worklog_ids", JSONArray())
                    }

                    i++
                }
            }
            var prepared =
                InvoiceIssueRules.prepare(
                    validation,
                    (JSONObject())!!.put("payload", payload),
                    LocalDate.now(clock),
                )
            for (field in
                (arrayOf(
                    "pdf_sha256",
                    "pdf_storage_name",
                    "pdf_byte_size",
                    "pdf_base64",
                    "document_id",
                    "pdf_filename",
                ))!!) {
                prepared!!.remove(field)
            }
            var money = LocalAuthorityRules.exactTotals(prepared)
            var allocations = allocateSources(prepared, all)
            var materials = allocateMaterialSources(prepared)
            var counterRaw = store!!.ownerValue("local_invoice_counter", "")
            if (counterRaw!!.isEmpty()) {
                throw IOException("INVOICE_AUTHORITY_RECONCILIATION_REQUIRED")
            }
            var counter: JSONObject = JSONObject(counterRaw)
            var authority = guard!!.current()
            var old: Int = counter!!.getInt("last_number")
            if ((old < authority!!.optInt("invoice_high_water"))) {
                throw IOException("INVOICE_NUMBER_ROLLBACK_RECONCILIATION_REQUIRED")
            }
            if ((old >= 1000000000)) {
                throw IOException("INVOICE_NUMBER_RANGE_EXHAUSTED")
            }
            var number: Int = (old + 1)
            var settings = all!!.getJSONObject("business_settings")
            var format = settings!!.getString("invoice_number_format")
            var prefix = settings!!.optString("invoice_prefix")
            if (
                (!(Arrays.asList("own", "sevdesk"))!!.contains(format) ||
                    (format!!.equals("own") &&
                        !prefix!!.matches(Regex("[A-Za-z][A-Za-z0-9-]{0,19}"))))
            ) {
                throw IOException("INVOICE_NUMBER_CONFIGURATION_REQUIRED")
            }
            var year = (prepared!!.getString("invoice_date"))!!.substring(0, 4)
            var label =
                (if (format!!.equals("sevdesk")) (((("RE-" + number)).toString() + "/") + year)
                else
                    (((((prefix.toString() + "-") + year)).toString() + "-") +
                        java.lang.String.format(Locale.ROOT, "%04d", number)))
            run {
                var i: Int = 0
                while ((i < (all!!.getJSONArray("invoices"))!!.length())) {
                    if (
                        label!!.equals(
                            ((all!!.getJSONArray("invoices"))!!.getJSONObject(i))!!.optString(
                                "invoice_number"
                            )
                        )
                    ) {
                        throw IOException("INVOICE_NUMBER_COLLISION")
                    }

                    i++
                }
            }
            var external = store!!.ownerValue("local_invoice_external_evidence", "")
            if (!external!!.isEmpty()) {
                var known: JSONArray = (JSONObject(external))!!.getJSONArray("issued")
                run {
                    var i: Int = 0
                    while ((i < known!!.length())) {
                        if (
                            label!!.equals((known!!.getJSONObject(i))!!.getString("invoice_number"))
                        ) {
                            throw IOException("INVOICE_NUMBER_COLLISION")
                        }

                        i++
                    }
                }
            }
            var stamp = (Instant.now(clock))!!.toString()
            var issued: JSONObject =
                (((((((((((JSONObject(prepared!!.toString()))!!.put("invoice_number", label))!!.put(
                                                        "issued_at",
                                                        stamp,
                                                    ))!!
                                                    .put("updated_at", stamp))!!
                                                .put("status", "Gestellt"))!!
                                            .put("local_issue_request", request))!!
                                        .put("money_minor", money))!!
                                    .put("pdf_state", "PDF_PENDING"))!!
                                .put(
                                    "document_job_id",
                                    ("invoice:" + prepared!!.getString("id")),
                                ))!!
                            .put("local_source_allocations", allocations))!!
                        .put("local_material_source_allocations", materials))!!
                    .put(
                        "numbering_receipt",
                        (((((((JSONObject())!!.put("authority", LocalAuthorityRules.ENGINE))!!.put(
                                                "migration_epoch",
                                                authority!!.optString("migration_epoch"),
                                            ))!!
                                            .put("invoice_id", prepared!!.getString("id")))!!
                                        .put("invoice_number", label))!!
                                    .put("counter", number))!!
                                .put("request_id", request))!!
                            .put("action_sha256", PixelMcpWriteTools.hash(args)),
                    )
            var jobOrigin: JSONObject = (BackupDeviceGuard(context))!!.current()
            var frozenHash = PixelMcpWriteTools.hash(issued)
            var reservation: JSONObject =
                (((((((JSONObject())!!.put("formatVersion", 1))!!.put("request_id", request))!!.put(
                                    "invoice_id",
                                    issued!!.getString("id"),
                                ))!!
                                .put("invoice_number", label))!!
                            .put("counter", number))!!
                        .put("snapshot_sha256", frozenHash))!!
                    .put("reserved_at", stamp)
            store!!.putOwnerValue(("local_number_reservation:" + request), reservation!!.toString())
            counter!!.put("last_number", number)
            store!!.putOwnerValue("local_invoice_counter", counter!!.toString())
            store!!.putOwnerValue(
                ("local_invoice_sources:" + issued!!.getString("id")),
                allocations!!.toString(),
            )
            store!!.putOwnerValue(
                ("local_invoice_material_sources:" + issued!!.getString("id")),
                materials!!.toString(),
            )
            store!!.putOwnerValue(
                ("local_invoice_job:" + issued!!.getString("id")),
                (((((((((JSONObject())!!.put("formatVersion", 1))!!.put(
                                                "invoice_id",
                                                issued!!.getString("id"),
                                            ))!!
                                            .put("phase", "PDF_PENDING"))!!
                                        .put("attempts", 0))!!
                                    .put("snapshot", issued))!!
                                .put("snapshot_sha256", frozenHash))!!
                            .put(
                                "origin_installation_id",
                                jobOrigin!!.getString("installationId"),
                            ))!!
                        .put("origin_restore_epoch", jobOrigin!!.getString("restoreEpoch")))!!
                    .toString(),
            )
            OwnerJournalRules.replace(all, "invoices", issued!!.getString("id"), issued)
            run {
                var it = allocations!!.keys()
                while (it!!.hasNext()) {
                    var id = it!!.next()
                    var log: JSONObject =
                        JSONObject(
                            (PixelMcpWriteTools.find(all!!.getJSONArray("worklogs"), id))!!
                                .toString()
                        )
                    var available: BigDecimal = BigDecimal((log!!.get("hours"))!!.toString())
                    var used = sourceUsed(id)
                    if ((used!!.compareTo(available) >= 0)) {
                        log!!.put("billed", 1)
                    }
                    OwnerJournalRules.replace(all, "worklogs", id, log)
                }
            }
            if (("cancellation")!!.equals(issued!!.optString("document_type"))) {
                var original: JSONObject =
                    JSONObject(
                        (PixelMcpWriteTools.find(
                                all!!.getJSONArray("invoices"),
                                issued!!.getString("correction_of"),
                            ))!!
                            .toString()
                    )
                original!!.put("status", "Storniert")
                OwnerJournalRules.replace(all, "invoices", original!!.getString("id"), original)
            }
            store!!.commitLocalSnapshot(all)
            db!!.setTransactionSuccessful()
            return issuedResult(issued)
        } finally {
            db!!.endTransaction()
        }
    }

    private @Throws(Exception::class) fun allocateSources(
        invoice: JSONObject,
        all: JSONObject,
    ): JSONObject {
        var allocations: JSONObject = JSONObject()
        var lines = invoice!!.getJSONArray("positions")
        run {
            var i: Int = 0
            while ((i < lines!!.length())) {
                var line = lines!!.getJSONObject(i)
                var id = line!!.optString("worklog_id")
                if (id!!.isEmpty()) {
                    run {
                        i++
                        continue
                    }
                }
                var log = PixelMcpWriteTools.find(all!!.getJSONArray("worklogs"), id)
                var quantity: BigDecimal = BigDecimal((line!!.get("quantity"))!!.toString())
                var available: BigDecimal = BigDecimal((log!!.get("hours"))!!.toString())
                var unit = (line!!.optString("unit"))!!.lowercase(Locale.ROOT)
                if (!(Arrays.asList("h", "std", "std.", "stunde", "stunden"))!!.contains(unit)) {
                    throw IOException("INVOICE_SOURCE_UNIT_REVIEW_REQUIRED")
                }
                if (
                    ((allocations!!.has(id) || (quantity!!.signum() <= 0)) ||
                        (((sourceUsed(id))!!.add(quantity))!!.compareTo(available) > 0))
                ) {
                    throw IOException("INVOICE_SOURCE_QUANTITY_EXCEEDED")
                }
                run {
                    var j: Int = 0
                    while ((j < (all!!.getJSONArray("invoices"))!!.length())) {
                        var old = (all!!.getJSONArray("invoices"))!!.getJSONObject(j)
                        if (
                            (!IssuedInvoiceRules.isIssued(old) ||
                                !(store!!.ownerValue(
                                        ("local_invoice_sources:" + old!!.getString("id")),
                                        "",
                                    ))!!
                                    .isEmpty())
                        ) {
                            run {
                                j++
                                continue
                            }
                        }
                        var used = old!!.optJSONArray("worklog_ids")
                        run {
                            var k: Int = 0
                            while (((used !== null) && (k < used!!.length()))) {
                                if (id!!.equals(used!!.getString(k))) {
                                    throw IOException("INVOICE_SOURCE_ALREADY_BILLED")
                                }

                                k++
                            }
                        }

                        j++
                    }
                }
                allocations!!.put(id, quantity!!.toPlainString())

                i++
            }
        }
        return allocations
    }

    private @Throws(Exception::class) fun sourceUsed(worklog: String): BigDecimal {
        var total = BigDecimal.ZERO
        run {
            (store!!.getReadableDatabase())!!
                .rawQuery(
                    "SELECT key FROM owner_state WHERE key LIKE 'local_invoice_sources:%'",
                    null,
                )
                .use { keys ->
                    while (keys!!.moveToNext()) {
                        var allocation: JSONObject =
                            JSONObject(store!!.ownerValue(keys!!.getString(0), "{}"))
                        if (allocation!!.has(worklog)) {
                            total = total!!.add(BigDecimal(allocation!!.getString(worklog)))
                        }
                    }
                }
        }
        return total
    }

    private @Throws(Exception::class) fun allocateMaterialSources(invoice: JSONObject): JSONObject {
        if (("cancellation")!!.equals(invoice!!.optString("document_type"))) {
            return JSONObject()
        }
        var allocated: JSONObject = JSONObject()
        var lines = invoice!!.getJSONArray("positions")
        run {
            var i: Int = 0
            while ((i < lines!!.length())) {
                var line = lines!!.getJSONObject(i)
                if (!(line!!.optString("source_type"))!!.equals("material_movement")) {
                    run {
                        i++
                        continue
                    }
                }
                var id = line!!.getString("source_id")
                var raw = store!!.ownerValue(("revier:v1:entity:material_movement:" + id), "")
                if (raw!!.isEmpty()) {
                    throw IOException("INVOICE_MATERIAL_SOURCE_MISSING")
                }
                var movement: JSONObject = JSONObject(raw)
                if (
                    ((!(movement!!.optString("movement_kind"))!!.equals("consume") ||
                        !(store!!.ownerValue(("revier:v1:reversed:" + id), ""))!!.isEmpty()) ||
                        allocated!!.has(id))
                ) {
                    throw IOException("INVOICE_MATERIAL_SOURCE_NOT_AVAILABLE")
                }
                if (!(line!!.getString("unit"))!!.equals(movement!!.getString("unit"))) {
                    throw IOException("INVOICE_SOURCE_UNIT_REVIEW_REQUIRED")
                }
                var canonical = SnapshotProjection.effective(store!!.getState())
                var recipient = movement!!.optString("billing_customer_id")
                if (recipient!!.isEmpty()) {
                    recipient =
                        BillingRules.defaultRecipient(
                            movement!!.getString("customer_id"),
                            movement!!.optString("order_id"),
                            canonical,
                        )
                }
                if (!(invoice!!.getString("customer_id"))!!.equals(recipient)) {
                    throw IOException("INVOICE_MATERIAL_RECIPIENT_MISMATCH")
                }
                var quantity: BigDecimal =
                    BigDecimal(
                        line!!.optString(
                            "source_quantity_decimal",
                            (line!!.get("quantity"))!!.toString(),
                        )
                    )
                var available: BigDecimal =
                    (BigDecimal(movement!!.getString("quantity_decimal")))!!.abs()
                var used = BigDecimal.ZERO
                if (
                    (quantity!!.compareTo(BigDecimal((line!!.get("quantity"))!!.toString())) != 0)
                ) {
                    throw IOException("INVOICE_SOURCE_QUANTITY_REVIEW_REQUIRED")
                }
                run {
                    (store!!.getReadableDatabase())!!
                        .rawQuery(
                            "SELECT key FROM owner_state WHERE key LIKE 'local_invoice_material_sources:%'",
                            null,
                        )
                        .use { keys ->
                            while (keys!!.moveToNext()) {
                                var values: JSONObject =
                                    JSONObject(store!!.ownerValue(keys!!.getString(0), "{}"))
                                if (values!!.has(id)) {
                                    used = used!!.add(BigDecimal(values!!.getString(id)))
                                }
                            }
                        }
                }
                if (
                    ((quantity!!.signum() <= 0) ||
                        ((used!!.add(quantity))!!.compareTo(available) > 0))
                ) {
                    throw IOException("INVOICE_SOURCE_QUANTITY_EXCEEDED")
                }
                allocated!!.put(id, quantity!!.toPlainString())

                i++
            }
        }
        return allocated
    }

    private @Throws(Exception::class) fun correction(
        args: JSONObject,
        request: String,
    ): JSONObject {
        var db = store!!.getWritableDatabase()
        store!!.beginBusinessTransaction(db)
        try {
            var all = materialize()
            var original =
                PixelMcpWriteTools.find(
                    all!!.getJSONArray("invoices"),
                    args!!.getString("invoice_id"),
                )
            if (
                !(PixelMcpWriteTools.hash(
                        PixelMcpEntityWriteTools.recordForHash("invoice", original)
                    ))!!
                    .equals(args!!.getString("expected_record_hash"))
            ) {
                throw IOException("WRITE_CONFLICT")
            }
            var validation: JSONObject = JSONObject(all!!.toString())
            var shadow =
                PixelMcpWriteTools.find(
                    validation!!.getJSONArray("invoices"),
                    original!!.getString("id"),
                )
            clearActiveSources(shadow)
            var payload: JSONObject =
                ((JSONObject(args!!.toString()))!!.put("id", original!!.getString("id")))!!.put(
                    "_expected_invoice",
                    DriveJournalRules.guard(shadow, "invoice"),
                )
            InvoiceIssueRules.correction(
                validation,
                (((JSONObject())!!.put("client_id", request))!!.put(
                        "created_at",
                        (Instant.now(clock))!!.toString(),
                    ))!!
                    .put("payload", payload),
            )
            var id = ("inv_correction_" + (PixelMcpWriteTools.hash(request))!!.substring(0, 32))
            var draft = PixelMcpWriteTools.find(validation!!.getJSONArray("invoices"), id)
            for (field in
                (arrayOf(
                    "local_issue_request",
                    "local_source_allocations",
                    "local_material_source_allocations",
                    "document_job_id",
                    "pdf_state",
                    "payment_balance",
                ))!!) {
                draft!!.remove(field)
            }
            OwnerJournalRules.replace(all, "invoices", id, draft)
            store!!.commitLocalSnapshot(all)
            db!!.setTransactionSuccessful()
            return ((((JSONObject())!!.put("correction_state", "draft_created"))!!.put(
                        "invoice_id",
                        id,
                    ))!!
                    .put("entity_id", id))!!
                .put("requires_issue_confirmation", true)
        } finally {
            db!!.endTransaction()
        }
    }

    private @Throws(Exception::class) fun prepareShare(args: JSONObject): JSONObject {
        var all = materialize()
        var invoice =
            PixelMcpWriteTools.find(all!!.getJSONArray("invoices"), args!!.getString("invoice_id"))
        TestDataRules.noDelivery(invoice, all)
        if (
            (!(invoice!!.optString("pdf_state", "READY"))!!.equals("READY") ||
                (invoice!!.optString("pdf_sha256"))!!.isEmpty())
        ) {
            throw IOException("INVOICE_PDF_NOT_READY")
        }
        verifiedInvoice(invoice)
        var result: JSONObject =
            (((((JSONObject())!!.put("invoice_id", invoice!!.getString("id")))!!.put(
                            "channel",
                            args!!.getString("channel"),
                        ))!!
                        .put("state", "device_interaction_required"))!!
                    .put("sent", false))!!
                .put("delivered", false)
        store!!.putOwnerValue("invoice_share_handoff", result!!.toString())
        return result
    }

    private @Throws(Exception::class) fun verifiedInvoice(invoice: JSONObject): Unit {
        var raw = store!!.ownerValue(("local_invoice_job:" + invoice!!.getString("id")), "")
        if (raw!!.isEmpty()) {
            LocalInvoiceJobs.verifiedOriginal(context, invoice)
        } else {
            LocalInvoiceJobs.verifyReady(context, JSONObject(raw), invoice)
        }
    }

    private @Throws(Exception::class) fun retryDocument(
        args: JSONObject,
        request: String,
    ): JSONObject {
        var current = invoice(args!!.getString("invoice_id"))
        if (
            !(OwnerJournalRules.version(current))!!.equals(args!!.getString("expected_record_hash"))
        ) {
            throw IOException("WRITE_CONFLICT")
        }
        var key = ("local_invoice_job:" + current!!.getString("id"))
        var raw = store!!.ownerValue(key, "")
        if (raw!!.isEmpty()) {
            throw IOException("INVOICE_DOCUMENT_JOB_MISSING")
        }
        var job: JSONObject = JSONObject(raw)
        if ((job!!.optString("phase"))!!.equals("ORIGINAL_REVIEW_REQUIRED")) {
            throw IOException("INVOICE_DOCUMENT_ORIGINAL_MISSING_OR_CHANGED")
        }
        if ((job!!.optString("phase"))!!.equals("READY")) {
            verifiedInvoice(current)
            return safeJob(job)
        }
        (job!!.put("phase", "PDF_PENDING"))!!.put("attempts", 0)
        store!!.putOwnerValue(key, job!!.toString())
        return safeJob(job)
    }

    private @Throws(Exception::class) fun payment(
        name: String,
        args: JSONObject,
        request: String,
    ): JSONObject {
        var db = store!!.getWritableDatabase()
        store!!.beginBusinessTransaction(db)
        try {
            var current = ledger(JSONObject())
            if (
                !(current!!.getString("ledger_hash"))!!.equals(
                    args!!.getString("expected_ledger_hash")
                )
            ) {
                throw IOException("PAYMENT_LEDGER_WRITE_CONFLICT")
            }
            var all = materialize()
            var entries = paymentEntries()
            var stamp = (Instant.now(clock))!!.toString()
            var id = ("pay_" + (PixelMcpWriteTools.hash(request))!!.substring(0, 32))
            if (name!!.equals("correct_payment")) {
                if (((args!!.optString("reason"))!!.trim())!!.isEmpty()) {
                    throw IOException("PAYMENT_CORRECTION_REASON_REQUIRED")
                }
                var old: JSONObject? = null
                run {
                    var i: Int = 0
                    while ((i < entries!!.length())) {
                        if (
                            ((entries!!.getJSONObject(i))!!.getString("payment_id"))!!.equals(
                                args!!.getString("payment_id")
                            )
                        ) {
                            old = entries!!.getJSONObject(i)
                        }

                        i++
                    }
                }
                if (
                    (((old === null) || old!!.optBoolean("reversal")) ||
                        (exactMinor(old!!.get("amount_minor")) <= 0))
                ) {
                    throw IOException("PAYMENT_NOT_CORRECTABLE")
                }
                run {
                    var i: Int = 0
                    while ((i < entries!!.length())) {
                        if (
                            (old!!.getString("payment_id"))!!.equals(
                                (entries!!.getJSONObject(i))!!.optString("reverses")
                            )
                        ) {
                            throw IOException("PAYMENT_ALREADY_CORRECTED")
                        }

                        i++
                    }
                }
                var reversed: JSONArray = JSONArray()
                var oldAlloc = old!!.getJSONArray("allocations")
                run {
                    var i: Int = 0
                    while ((i < oldAlloc!!.length())) {
                        reversed!!.put(
                            (JSONObject((oldAlloc!!.getJSONObject(i))!!.toString()))!!.put(
                                "amount_minor",
                                Math.negateExact(
                                    (oldAlloc!!.getJSONObject(i))!!.getLong("amount_minor")
                                ),
                            )
                        )

                        i++
                    }
                }
                var reversal: JSONObject =
                    ((((((((JSONObject())!!.put("formatVersion", 1))!!.put(
                                                "payment_id",
                                                (id.toString() + "_reversal"),
                                            ))!!
                                            .put(
                                                "amount_minor",
                                                Math.negateExact(old!!.getLong("amount_minor")),
                                            ))!!
                                        .put("allocations", reversed))!!
                                    .put("reversal", true))!!
                                .put("reverses", old!!.getString("payment_id")))!!
                            .put("reason", args!!.getString("reason")))!!
                        .put("recorded_at", stamp)
                store!!.putOwnerValue(
                    ("local_payment:" + reversal!!.getString("payment_id")),
                    reversal!!.toString(),
                )
                entries!!.put(reversal)
            }
            var amount: Long = exactMinor(args!!.get("amount_minor"))
            if (((amount < 0) || ((amount == 0L) && !name!!.equals("correct_payment")))) {
                throw IOException("PAYMENT_POSITIVE_AMOUNT_REQUIRED")
            }
            if (((amount == 0L) && ((args!!.getJSONArray("allocations"))!!.length() != 0))) {
                throw IOException("PAYMENT_ZERO_CORRECTION_REQUIRES_EMPTY_ALLOCATIONS")
            }
            var date = LocalDate.parse(args!!.getString("paid_on"))
            if (date!!.isAfter(LocalDate.now(clock))) {
                throw IOException("PAYMENT_DATE_IN_FUTURE")
            }
            var method = args!!.getString("method")
            if (!(Arrays.asList("cash", "bank", "card", "other"))!!.contains(method)) {
                throw IOException("PAYMENT_METHOD_INVALID")
            }
            var allocations: JSONArray =
                JSONArray((args!!.getJSONArray("allocations"))!!.toString())
            var total: Long = 0
            var used: MutableSet<String> = HashSet()
            run {
                var i: Int = 0
                while ((i < allocations!!.length())) {
                    var allocation = allocations!!.getJSONObject(i)
                    var invoice =
                        PixelMcpWriteTools.find(
                            all!!.getJSONArray("invoices"),
                            allocation!!.getString("invoice_id"),
                        )
                    if (
                        ((!used!!.add(invoice!!.getString("id")) ||
                            !IssuedInvoiceRules.isIssued(invoice)) ||
                            (invoice!!.optString("status"))!!.equals("Storniert"))
                    ) {
                        throw IOException("PAYMENT_INVOICE_INVALID")
                    }
                    if (
                        !(OwnerJournalRules.version(invoice))!!.equals(
                            allocation!!.getString("expected_record_hash")
                        )
                    ) {
                        throw IOException("WRITE_CONFLICT")
                    }
                    var known = balance(invoice, entries)
                    if (!known!!.getBoolean("balance_known")) {
                        throw IOException("PAYMENT_BALANCE_RECONCILIATION_REQUIRED")
                    }
                    if ((known!!.getLong("invoice_minor") <= 0)) {
                        throw IOException("PAYMENT_CREDIT_DOCUMENT_ALLOCATION_REQUIRES_REVIEW")
                    }
                    var assigned: Long = exactMinor(allocation!!.get("amount_minor"))
                    if ((assigned <= 0)) {
                        throw IOException("PAYMENT_ALLOCATION_INVALID")
                    }
                    try {
                        total = Math.addExact(total.toLong(), assigned.toLong())
                    } catch (invalid: ArithmeticException) {
                        throw IOException("PAYMENT_AMOUNT_MINOR_INVALID")
                    }
                    allocation!!.remove("expected_record_hash")

                    i++
                }
            }
            if ((total > amount)) {
                throw IOException("PAYMENT_ALLOCATION_EXCEEDS_AMOUNT")
            }
            var payment: JSONObject =
                (((((((((((JSONObject())!!.put("formatVersion", 1))!!.put("payment_id", id))!!.put(
                                                    "request_id",
                                                    request,
                                                ))!!
                                                .put(
                                                    "client_request_id",
                                                    args!!.getString("request_id"),
                                                ))!!
                                            .put("amount_minor", amount))!!
                                        .put("unallocated_minor", (amount - total)))!!
                                    .put("paid_on", date!!.toString()))!!
                                .put("method", method))!!
                            .put("reference", args!!.optString("reference")))!!
                        .put("allocations", allocations))!!
                    .put("recorded_at", stamp)
            if (name!!.equals("correct_payment")) {
                (payment!!.put("corrects", args!!.getString("payment_id")))!!.put(
                    "reason",
                    args!!.getString("reason"),
                )
            }
            store!!.putOwnerValue(("local_payment:" + id), payment!!.toString())
            entries!!.put(payment)
            run {
                var i: Int = 0
                while ((i < (all!!.getJSONArray("invoices"))!!.length())) {
                    var invoice = (all!!.getJSONArray("invoices"))!!.getJSONObject(i)
                    var balance = balance(invoice, entries)
                    if (
                        (balance!!.getBoolean("has_ledger_entries") &&
                            !(invoice!!.optString("status"))!!.equals("Storniert"))
                    ) {
                        (invoice!!.put("payment_balance", balance))!!.put(
                            "status",
                            (if (balance!!.getBoolean("paid")) "Bezahlt" else "Gestellt"),
                        )
                    }

                    i++
                }
            }
            store!!.commitLocalSnapshot(all)
            var result: JSONObject =
                (((JSONObject())!!.put("payment_id", id))!!.put("local_state", "local_saved"))!!
                    .put("ledger_hash", (ledger(JSONObject()))!!.getString("ledger_hash"))
            db!!.setTransactionSuccessful()
            return result
        } finally {
            db!!.endTransaction()
        }
    }

    private @Throws(Exception::class) fun ledger(args: JSONObject): JSONObject {
        var entries = paymentEntries()
        var filtered: JSONArray = JSONArray()
        var balances: JSONArray = JSONArray()
        var paymentIndex: MutableMap<String, JSONObject> = HashMap()
        run {
            var i: Int = 0
            while ((i < entries!!.length())) {
                paymentIndex!!.put(
                    (entries!!.getJSONObject(i))!!.getString("payment_id"),
                    entries!!.getJSONObject(i),
                )

                i++
            }
        }
        var invoiceId = args!!.optString("invoice_id")
        var offset: Int = args!!.optInt("offset", 0)
        var limit: Int = args!!.optInt("limit", 50)
        var total: Int = 0
        run {
            var i: Int = 0
            while ((i < entries!!.length())) {
                var p = entries!!.getJSONObject(i)
                var match: Boolean =
                    (invoiceId!!.isEmpty() || paymentReferencesInvoice(p, invoiceId, paymentIndex))
                if (((match && (total++ >= offset)) && (filtered!!.length() < limit))) {
                    filtered!!.put(p)
                }

                i++
            }
        }
        var state = SnapshotProjection.effective(store!!.getState())
        run {
            var i: Int = 0
            while ((i < (state!!.getJSONArray("invoices"))!!.length())) {
                var invoice = (state!!.getJSONArray("invoices"))!!.getJSONObject(i)
                if (
                    (IssuedInvoiceRules.isIssued(invoice) &&
                        (invoiceId!!.isEmpty() || invoiceId!!.equals(invoice!!.getString("id"))))
                ) {
                    balances!!.put(
                        ((balance(invoice, entries))!!.put(
                                "invoice_id",
                                invoice!!.getString("id"),
                            ))!!
                            .put("record_hash", OwnerJournalRules.version(invoice))
                            .put("invoice_number", invoice.optString("invoice_number"))
                    )
                }

                i++
            }
        }
        // Display labels are projections only; keep the ledger hash bound to
        // the immutable stored entries and independent from the selected page.
        val visiblePayments = LocalBillingRules.paymentDisplay(filtered, state.getJSONArray("invoices"))
        return (((((JSONObject())!!.put("formatVersion", 1))!!.put(
                        "ledger_hash",
                        PixelMcpWriteTools.hash(entries),
                    ))!!
                    .put("payments", visiblePayments))!!
                .put("total", total))!!
            .put("balances", balances)
    }

    private @Throws(Exception::class) fun balance(
        invoice: JSONObject,
        entries: JSONArray,
    ): JSONObject {
        var amounts: JSONArray = JSONArray()
        var has: Boolean = false
        run {
            var i: Int = 0
            while ((i < entries!!.length())) {
                var p = entries!!.getJSONObject(i)
                var alloc: JSONObject? = null
                var list = p!!.getJSONArray("allocations")
                run {
                    var j: Int = 0
                    while ((j < list!!.length())) {
                        alloc = list!!.getJSONObject(j)
                        if ((invoice!!.getString("id"))!!.equals(alloc!!.getString("invoice_id"))) {
                            has = true
                            amounts!!.put(
                                ((JSONObject())!!.put("payment_id", p!!.getString("payment_id")))!!
                                    .put("allocated_minor", alloc!!.getLong("amount_minor"))
                            )
                        }

                        j++
                    }
                }

                i++
            }
        }
        var original: Long =
            (if ((invoice!!.optJSONObject("money_minor") !== null))
                (invoice!!.getJSONObject("money_minor"))!!.getLong("gross_minor")
            else
                LocalAuthorityRules.minor(
                    (if (invoice!!.has("gross_total")) invoice!!.get("gross_total")
                    else invoice!!.get("amount"))
                ))
        var opening =
            store!!.ownerValue(("invoice_opening_balance:" + invoice!!.getString("id")), "")
        if (!opening!!.isEmpty()) {
            var baseline: Long = (JSONObject(opening))!!.getLong("remaining_minor")
            amounts!!.put(
                ((JSONObject())!!.put("payment_id", "opening_baseline"))!!.put(
                    "allocated_minor",
                    (original - baseline),
                )
            )
        }
        var result =
            (((LocalAuthorityRules.paymentBalance(original, amounts))!!.put(
                        "has_ledger_entries",
                        has,
                    ))!!
                    .put("legacy_status", invoice!!.optString("status")))!!
                .put(
                    "balance_known",
                    (((invoice!!.has("money_minor") || has) ||
                        !(invoice!!.optString("status"))!!.equals("Bezahlt")) ||
                        !opening!!.isEmpty()),
                )
        if (!result!!.getBoolean("balance_known")) {
            (result!!.put("paid", true))!!.put("remaining_minor", JSONObject.NULL)
        }
        return result
    }

    private @Throws(Exception::class) fun paymentEntries(): JSONArray {
        val out = JSONArray()
        // Ordinary entries fit in one bounded cursor row. Keep the existing
        // chunked reader for large historical entries instead of risking a
        // CursorWindow overflow or issuing two extra queries for every payment.
        store.readableDatabase.rawQuery(
            "SELECT key, CASE WHEN length(value)<=32768 THEN value ELSE NULL END " +
                "FROM owner_state WHERE key LIKE 'local_payment:%' ORDER BY rowid", null
        ).use { rows ->
            while (rows.moveToNext()) {
                val raw = if (rows.isNull(1)) store.ownerValue(rows.getString(0), "{}") else rows.getString(1)
                out.put(JSONObject(raw))
            }
        }
        return out
    }

    private @Throws(Exception::class) fun invoice(id: String): JSONObject {
        return PixelMcpWriteTools.find(
            (SnapshotProjection.effective(store!!.getState()))!!.getJSONArray("invoices"),
            id,
        )
    }

    private @Throws(Exception::class) fun materialize(): JSONObject {
        var state = store!!.getState()
        var all: JSONObject = JSONObject((state!!.getJSONObject("master"))!!.toString())
        var errors = DriveJournalRules.apply(all, state!!.getJSONArray("pending"))
        if ((errors!!.length() > 0)) {
            all!!.put("drive_journal_errors", errors)
        }
        return all
    }

    private @Throws(Exception::class) fun safeJob(job: JSONObject): JSONObject {
        var review: Boolean =
            ((job!!.optString("phase"))!!.equals("ORIGINAL_REVIEW_REQUIRED") ||
                (job!!.optString("last_error_code"))!!.equals(
                    "INVOICE_ORIGINAL_RECONCILIATION_REQUIRED"
                ))
        if ((!review && !(job!!.optString("phase"))!!.equals("READY"))) {
            try {
                LocalInvoiceCreationRules.requireAllowed(
                    job,
                    (BackupDeviceGuard(context))!!.current(),
                    (LocalAuthorityGuard(context))!!.current(),
                    LocalAuthorityGuard.restorePending(context),
                )
            } catch (held: IOException) {
                review = true
            }
        }
        return ((((((JSONObject())!!.put("invoice_id", job!!.getString("invoice_id")))!!.put(
                            "phase",
                            job!!.getString("phase"),
                        ))!!
                        .put("attempts", job!!.optInt("attempts")))!!
                    .put("last_error_code", job!!.optString("last_error_code")))!!
                .put("snapshot_sha256", job!!.getString("snapshot_sha256")))!!
            .put("original_review_required", review)
    }

    companion object {
        private @JvmStatic @Throws(Exception::class) fun clearActiveSources(
            invoice: JSONObject
        ): Unit {
            var positions = invoice!!.getJSONArray("positions")
            run {
                var i: Int = 0
                while ((i < positions!!.length())) {
                    for (field in
                        (arrayOf("source_type", "source_id", "source_quantity_decimal"))!!) {
                        (positions!!.getJSONObject(i))!!.remove(field)
                    }

                    i++
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun paymentReferencesInvoice(
            payment: JSONObject,
            invoiceId: String,
            entries: MutableMap<String, JSONObject>,
        ): Boolean {
            var visited: MutableSet<String> = HashSet()
            run {
                var current: JSONObject? = payment
                while (((current !== null) && visited!!.add(current!!.getString("payment_id")))) {
                    var allocations = current!!.getJSONArray("allocations")
                    run {
                        var i: Int = 0
                        while ((i < allocations!!.length())) {
                            if (
                                invoiceId!!.equals(
                                    (allocations!!.getJSONObject(i))!!.getString("invoice_id")
                                )
                            ) {
                                return true
                            }

                            i++
                        }
                    }
                    current =
                        entries!!.get(
                            current!!.optString("corrects", current!!.optString("reverses"))
                        )
                }
            }
            return false
        }

        private @JvmStatic @Throws(IOException::class) fun exactMinor(value: Any?): Long {
            try {
                return (BigDecimal(value!!.toString()))!!.longValueExact()
            } catch (invalid: Exception) {
                throw IOException("PAYMENT_AMOUNT_MINOR_INVALID")
            }
        }

        private @JvmStatic @Throws(Exception::class) fun issuedResult(
            invoice: JSONObject
        ): JSONObject {
            return (((((((JSONObject())!!.put("invoice_id", invoice!!.getString("id")))!!.put(
                                    "invoice_number",
                                    invoice!!.getString("invoice_number"),
                                ))!!
                                .put("finalization", "locally_issued"))!!
                            .put("pdf_state", invoice!!.optString("pdf_state", "PDF_PENDING")))!!
                        .put(
                            "pdf_release_allowed",
                            (invoice!!.optString("pdf_state"))!!.equals("READY"),
                        ))!!
                    .put("sent", false))!!
                .put("record_hash", OwnerJournalRules.version(invoice))
        }
    }
}
