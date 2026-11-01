package at.hausgarten.service

import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.util.*
import org.json.*

/** Read-only semantic validation of a captured financial namespace, independent of Android. */
internal object LocalFinancialValidation {
    private @JvmStatic fun invalid(code: String): IOException {
        return IOException(("FINANCIAL_" + code))
    }

    private @JvmStatic @Throws(IOException::class) fun require(value: Boolean, code: String): Unit {
        if (!value) {
            throw invalid(code)
        }
    }

    private @JvmStatic @Throws(Exception::class) fun integer(raw: Any?): Long {
        try {
            return (BigDecimal(raw!!.toString()))!!.longValueExact()
        } catch (bad: Exception) {
            throw invalid("AMOUNT_INVALID")
        }
    }

    private @JvmStatic @Throws(Exception::class) fun quantity(raw: Any?): BigDecimal {
        try {
            var value: BigDecimal = BigDecimal(raw!!.toString())
            require(
                (((value!!.signum() >= 0) && (value!!.precision() <= 24)) &&
                    (Math.abs(value!!.scale()) <= 12)),
                "QUANTITY_INVALID",
            )
            return value
        } catch (bad: NumberFormatException) {
            throw invalid("QUANTITY_INVALID")
        }
    }

    private @JvmStatic @Throws(Exception::class) fun `object`(raw: Any?): JSONObject {
        return (if ((raw is JSONObject)) JSONObject(raw!!.toString())
        else JSONObject((raw as String)))
    }

    private @JvmStatic @Throws(Exception::class) fun id(row: JSONObject, field: String): String {
        var value = row!!.getString(field)
        require(!(value!!.trim())!!.isEmpty(), "REFERENCE_INVALID")
        return value
    }

    private @JvmStatic fun issued(invoice: JSONObject): Boolean {
        return IssuedInvoiceRules.isIssued(invoice)
    }

    private @JvmStatic @Throws(Exception::class) fun index(
        rows: JSONArray?
    ): MutableMap<String, JSONObject> {
        var result: MutableMap<String, JSONObject> = HashMap()
        run {
            var i: Int = 0
            while (((rows !== null) && (i < rows!!.length()))) {
                var row = rows!!.getJSONObject(i)
                require((result!!.put(id(row, "id"), row) === null), "DUPLICATE_ID")

                i++
            }
        }
        return result
    }

    private @JvmStatic @Throws(Exception::class) fun find(
        values: MutableMap<String, JSONObject>,
        id: String,
    ): JSONObject {
        var row = values!!.get(id)
        require((row !== null), "REFERENCE_MISSING")
        return row!!
    }

    private @JvmStatic @Throws(Exception::class) fun records(
        namespaces: JSONObject,
        prefix: String,
    ): MutableMap<String, JSONObject> {
        var result: MutableMap<String, JSONObject> = HashMap()
        run {
            var keys = namespaces!!.keys()
            while (keys!!.hasNext()) {
                var key = keys!!.next()
                if (key!!.startsWith(prefix)) {
                    var suffix = key!!.substring(prefix!!.length)
                    require(!suffix!!.isEmpty(), "NAMESPACE_INVALID")
                    result!!.put(suffix, `object`(namespaces!!.get(key)))
                }
            }
        }
        return result
    }

    private @JvmStatic @Throws(Exception::class) fun same(a: JSONObject, b: JSONObject): Boolean {
        return (PixelMcpWriteTools.hash(a))!!.equals(PixelMcpWriteTools.hash(b))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun sameFrozenInvoice(actual: JSONObject, frozen: JSONObject): Boolean {
        var first: JSONObject = JSONObject(actual!!.toString())
        var second: JSONObject = JSONObject(frozen!!.toString())
        for (field in
            (arrayOf(
                "status",
                "updated_at",
                "payment_balance",
                "pdf_state",
                "pdf_sha256",
                "pdf_storage_name",
                "pdf_byte_size",
                "pdf_base64",
                "pdf_filename",
                "document_id",
            ))!!) {
            first!!.remove(field)
            second!!.remove(field)
        }
        return same(first, second)
    }

    private @JvmStatic @Throws(Exception::class) fun invoiceMinor(invoice: JSONObject): Long {
        return (if ((invoice!!.optJSONObject("money_minor") !== null))
            integer((invoice!!.getJSONObject("money_minor"))!!.get("gross_minor"))
        else
            LocalAuthorityRules.minor(
                (if (invoice!!.has("gross_total")) invoice!!.get("gross_total")
                else invoice!!.get("amount"))
            ))
    }

    private @JvmStatic @Throws(Exception::class) fun allocations(
        payment: JSONObject,
        invoices: MutableMap<String, JSONObject>,
        reversal: Boolean,
    ): MutableMap<String, Long> {
        var result: MutableMap<String, Long> = TreeMap()
        var rows = payment!!.getJSONArray("allocations")
        require((rows!!.length() <= 100), "PAYMENT_INVALID")
        run {
            var i: Int = 0
            while ((i < rows!!.length())) {
                var allocation = rows!!.getJSONObject(i)
                var invoiceId = id(allocation, "invoice_id")
                var invoice = find(invoices, invoiceId)
                require(
                    (issued(invoice) && (invoiceMinor(invoice) > 0)),
                    "PAYMENT_REFERENCE_INVALID",
                )
                var amount: Long = integer(allocation!!.get("amount_minor"))
                require((if (reversal) (amount < 0) else (amount > 0)), "PAYMENT_INVALID")
                require((result!!.put(invoiceId, amount) === null), "DUPLICATE_ALLOCATION")

                i++
            }
        }
        return result
    }

    private @JvmStatic @Throws(Exception::class) fun payments(
        invoices: MutableMap<String, JSONObject>,
        payments: MutableMap<String, JSONObject>,
        opening: MutableMap<String, JSONObject>,
    ): Unit {
        var reversed: MutableMap<String, String> = HashMap()
        var corrected: MutableMap<String, String> = HashMap()
        var allocated: MutableMap<String, MutableMap<String, Long>> = HashMap()
        var requests: MutableSet<String> = HashSet()
        var clientRequests: MutableSet<String> = HashSet()
        for (entry in (payments!!.entries)!!) {
            var row = entry!!.value
            require(
                ((integer(row!!.get("formatVersion")) == 1L) &&
                    (entry!!.key)!!.equals(id(row, "payment_id"))),
                "PAYMENT_INVALID",
            )
            Instant.parse(row!!.getString("recorded_at"))
            var reversal: Boolean = row!!.optBoolean("reversal")
            var amount: Long = integer(row!!.get("amount_minor"))
            require(
                (if (reversal) (amount < 0)
                else
                    ((amount > 0) ||
                        (((amount == 0L) && !(row!!.optString("corrects"))!!.isEmpty()) &&
                            ((row!!.getJSONArray("allocations"))!!.length() == 0)))),
                "PAYMENT_INVALID",
            )
            var allocation = allocations(row, invoices, reversal)
            allocated!!.put(entry!!.key, allocation)
            var sum: Long = 0
            for (value in (allocation!!.values)!!) {
                sum = Math.addExact(sum.toLong(), value.toLong())
            }
            if (reversal) {
                var original = id(row, "reverses")
                require(
                    (!((row!!.optString("reason"))!!.trim())!!.isEmpty() &&
                        (reversed!!.put(original, entry!!.key) === null)),
                    "PAYMENT_CORRECTION_INVALID",
                )
            } else {
                LocalDate.parse(row!!.getString("paid_on"))
                require(requests!!.add(id(row, "request_id")), "PAYMENT_DUPLICATE_REQUEST")
                if (row!!.has("client_request_id")) {
                    require(
                        clientRequests!!.add(id(row, "client_request_id")),
                        "PAYMENT_DUPLICATE_REQUEST",
                    )
                }
                require(
                    (Arrays.asList("cash", "bank", "card", "other"))!!.contains(
                        row!!.optString("method")
                    ),
                    "PAYMENT_INVALID",
                )
                require(
                    ((sum <= amount) &&
                        (integer(row!!.get("unallocated_minor")) == (amount - sum))),
                    "PAYMENT_SUM_MISMATCH",
                )
                if (row!!.has("corrects")) {
                    var original = id(row, "corrects")
                    require(
                        (!((row!!.optString("reason"))!!.trim())!!.isEmpty() &&
                            (corrected!!.put(original, entry!!.key) === null)),
                        "PAYMENT_CORRECTION_INVALID",
                    )
                }
            }
        }
        require((reversed!!.keys)!!.equals(corrected!!.keys), "PAYMENT_CORRECTION_INVALID")
        for (originalId in (reversed!!.keys)!!) {
            var original = find(payments, originalId)
            var reversal = find(payments, reversed!!.get(originalId)!!)
            var correction = find(payments, corrected!!.get(originalId)!!)
            require(
                (((!original!!.optBoolean("reversal") &&
                    (integer(original!!.get("amount_minor")) > 0)) &&
                    !originalId!!.equals(correction!!.getString("payment_id"))) &&
                    (reversal!!.getString("payment_id"))!!.equals(
                        ((correction!!.getString("payment_id")).toString() + "_reversal")
                    )),
                "PAYMENT_CORRECTION_INVALID",
            )
            require(
                ((integer(reversal!!.get("amount_minor")) ==
                    Math.negateExact(integer(original!!.get("amount_minor")))) &&
                    (reversal!!.getString("reason"))!!.equals(correction!!.getString("reason"))),
                "PAYMENT_CORRECTION_INVALID",
            )
            var expected: MutableMap<String, Long> = TreeMap()
            for (allocation in ((allocated!!.get(originalId))!!.entries)!!) {
                expected!!.put(allocation!!.key, Math.negateExact(allocation!!.value))
            }
            require(
                expected!!.equals(allocated!!.get(reversal!!.getString("payment_id"))),
                "PAYMENT_CORRECTION_INVALID",
            )
        }
        for (start in (corrected!!.keys)!!) {
            var visited: MutableSet<String> = HashSet()
            var next = start
            while (corrected!!.containsKey(next)) {
                require(visited!!.add(next), "PAYMENT_CORRECTION_INVALID")
                next = corrected!!.get(next)!!
            }
        }
        for (entry in (opening!!.entries)!!) {
            var invoice = find(invoices, entry!!.key)
            var value = entry!!.value
            var amount: Long = integer(value!!.get("remaining_minor"))
            require(
                ((issued(invoice) && (amount >= 0)) &&
                    (amount <= Math.max(0, invoiceMinor(invoice)))),
                "OPENING_BALANCE_INVALID",
            )
            if (value!!.has("invoice_id")) {
                require(
                    (entry!!.key)!!.equals(value!!.getString("invoice_id")),
                    "OPENING_BALANCE_INVALID",
                )
            }
        }
    }

    private @JvmStatic @Throws(Exception::class) fun sourceLedger(
        field: String,
        ledger: MutableMap<String, JSONObject>,
        invoices: MutableMap<String, JSONObject>,
        sources: MutableMap<String, JSONObject>,
        material: Boolean,
    ): Unit {
        var totals: MutableMap<String, BigDecimal> = HashMap()
        for (entry in (ledger!!.entries)!!) {
            var invoice = find(invoices, entry!!.key)
            require(
                (((issued(invoice) && !(invoice!!.optString("local_issue_request"))!!.isEmpty()) &&
                    (invoice!!.optJSONObject(field) !== null)) &&
                    same(entry!!.value, invoice!!.getJSONObject(field))),
                "SOURCE_SNAPSHOT_MISMATCH",
            )
            var allocation = entry!!.value
            run {
                var ids = allocation!!.keys()
                while (ids!!.hasNext()) {
                    var source = ids!!.next()
                    var raw = allocation!!.get(source)
                    require((raw is String), "QUANTITY_INVALID")
                    var amount = quantity(raw)
                    require((amount!!.signum() > 0), "QUANTITY_INVALID")
                    var original = find(sources, source)
                    var available: BigDecimal? = null
                    if (material) {
                        require(
                            (original!!.optString("movement_kind"))!!.equals("consume"),
                            "MATERIAL_REFERENCE_INVALID",
                        )
                        available = (BigDecimal(original!!.getString("quantity_decimal")))!!.abs()
                    } else {
                        available = quantity(original!!.get("hours"))
                    }
                    var total = (totals!!.getOrDefault(source, BigDecimal.ZERO))!!.add(amount)
                    require((total!!.compareTo(available) <= 0), "SOURCE_QUANTITY_EXCEEDED")
                    totals!!.put(source, total)
                    var linked: Boolean = false
                    var positions = invoice!!.getJSONArray("positions")
                    run {
                        var i: Int = 0
                        while ((i < positions!!.length())) {
                            var line = positions!!.getJSONObject(i)
                            if (
                                (if (material)
                                    ((line!!.optString("source_type"))!!.equals(
                                        "material_movement"
                                    ) && (line!!.optString("source_id"))!!.equals(source))
                                else (line!!.optString("worklog_id"))!!.equals(source))
                            ) {
                                require(
                                    ((BigDecimal((line!!.get("quantity"))!!.toString()))!!
                                        .compareTo(amount) == 0),
                                    "SOURCE_SNAPSHOT_MISMATCH",
                                )
                                if (material) {
                                    require(
                                        (line!!.optString("unit"))!!.equals(
                                            original!!.optString("unit")
                                        ),
                                        "MATERIAL_REFERENCE_INVALID",
                                    )
                                }
                                linked = true
                            }

                            i++
                        }
                    }
                    require(linked, "SOURCE_REFERENCE_INVALID")
                }
            }
        }
        for (invoice in (invoices!!.values)!!) {
            if ((issued(invoice) && invoice!!.has(field))) {
                require(ledger!!.containsKey(invoice!!.getString("id")), "SOURCE_SNAPSHOT_MISMATCH")
            }
        }
    }

    private @JvmStatic @Throws(Exception::class) fun jobs(
        invoices: MutableMap<String, JSONObject>,
        jobs: MutableMap<String, JSONObject>,
    ): Unit {
        for (entry in (jobs!!.entries)!!) {
            var job = entry!!.value
            var invoice = find(invoices, entry!!.key)
            var frozen = job!!.getJSONObject("snapshot")
            var phase = job!!.getString("phase")
            var hash = job!!.getString("snapshot_sha256")
            require(
                (((integer(job!!.get("formatVersion")) == 1L) &&
                    (entry!!.key)!!.equals(id(job, "invoice_id"))) &&
                    (entry!!.key)!!.equals(id(frozen, "id"))),
                "JOB_INVALID",
            )
            require(
                ((Arrays.asList("PDF_PENDING", "READY", "ORIGINAL_REVIEW_REQUIRED"))!!.contains(
                    phase
                ) &&
                    (integer(
                        (if ((job!!.opt("attempts") === null)) 0 else job!!.get("attempts"))
                    ) >= 0)),
                "JOB_INVALID",
            )
            require(
                (((hash!!.matches(Regex("[0-9a-f]{64}")) &&
                    (PixelMcpWriteTools.hash(frozen))!!.equals(hash)) && issued(invoice)) &&
                    sameFrozenInvoice(invoice, frozen)),
                "JOB_SNAPSHOT_MISMATCH",
            )
            require(
                ((frozen!!.optString("pdf_state"))!!.equals("PDF_PENDING") &&
                    same(
                        frozen!!.getJSONObject("money_minor"),
                        LocalAuthorityRules.exactTotals(frozen),
                    )),
                "JOB_SNAPSHOT_MISMATCH",
            )
            require(
                ((frozen!!.optString("document_job_id"))!!.equals(("invoice:" + entry!!.key)) &&
                    !(frozen!!.optString("local_issue_request"))!!.isEmpty()),
                "JOB_INVALID",
            )
            var issuer = frozen!!.getJSONObject("issuer")
            var recipient = frozen!!.getJSONObject("recipient")
            for (field in
                (arrayOf(
                    "owner_name",
                    "business_street",
                    "business_postal_code",
                    "business_city",
                ))!!) {
                require(
                    !((issuer!!.optString(field))!!.trim())!!.isEmpty(),
                    "JOB_SNAPSHOT_MISMATCH",
                )
            }
            for (field in (arrayOf("name", "street", "postal_code", "city"))!!) {
                require(
                    !((recipient!!.optString(field))!!.trim())!!.isEmpty(),
                    "JOB_SNAPSHOT_MISMATCH",
                )
            }
            require(
                (Arrays.asList("small_business", "standard"))!!.contains(
                    frozen!!.optString("tax_mode")
                ),
                "JOB_SNAPSHOT_MISMATCH",
            )
            Instant.parse(frozen!!.getString("issued_at"))
            var invoiceDate = LocalDate.parse(frozen!!.getString("invoice_date"))
            var start = LocalDate.parse(frozen!!.getString("service_start"))
            var end = LocalDate.parse(frozen!!.getString("service_end"))
            require(
                (!end!!.isBefore(start) && !end!!.isAfter(invoiceDate)),
                "JOB_SNAPSHOT_MISMATCH",
            )
            if ((job!!.has("origin_installation_id") || job!!.has("origin_restore_epoch"))) {
                require(
                    ((job!!.optString("origin_installation_id"))!!.matches(
                        Regex("[0-9a-f-]{36}")
                    ) &&
                        (job!!.optString("origin_restore_epoch"))!!.matches(
                            Regex("[0-9a-f-]{36}")
                        )),
                    "JOB_ORIGIN_INVALID",
                )
            }
            if (job!!.has("creation_authorization")) {
                var authorization = job!!.getJSONObject("creation_authorization")
                require(
                    ((((((integer(authorization!!.get("formatVersion")) == 1L) &&
                        (authorization!!.optString("installation_id"))!!.matches(
                            Regex("[0-9a-f-]{36}")
                        )) &&
                        (authorization!!.optString("restore_epoch"))!!.matches(
                            Regex("[0-9a-f-]{36}")
                        )) && hash!!.equals(authorization!!.optString("snapshot_sha256"))) &&
                        (authorization!!.optString("ledger_proof_sha256"))!!.matches(
                            Regex("[0-9a-f]{64}")
                        )) && !(authorization!!.optString("request_id"))!!.isEmpty()),
                    "JOB_AUTHORIZATION_INVALID",
                )
                Instant.parse(authorization!!.getString("authorized_at"))
            }
            if (job!!.has("original_adoption")) {
                var adoption = job!!.getJSONObject("original_adoption")
                require(
                    (((((((integer(adoption!!.get("formatVersion")) == 1L) &&
                        hash!!.equals(adoption!!.optString("snapshot_sha256"))) &&
                        (adoption!!.optString("ledger_proof_sha256"))!!.matches(
                            Regex("[0-9a-f]{64}")
                        )) && !(adoption!!.optString("request_id"))!!.isEmpty()) &&
                        (job!!.optJSONObject("document") !== null)) &&
                        (Arrays.asList("READY", "ORIGINAL_REVIEW_REQUIRED"))!!.contains(phase)) &&
                        (!adoption!!.has("receipt_proof_sha256") ||
                            (adoption!!.optString("receipt_proof_sha256"))!!.matches(
                                Regex("[0-9a-f]{64}")
                            ))),
                    "JOB_ADOPTION_INVALID",
                )
                Instant.parse(adoption!!.getString("adopted_at"))
            }
            if (phase!!.equals("READY")) {
                var metadata = job!!.getJSONObject("document")
                require(
                    (((((invoice!!.optString("pdf_state"))!!.equals("READY") &&
                        (metadata!!.optString("document_id"))!!.equals(
                            ("invoice:" + entry!!.key)
                        )) &&
                        (metadata!!.optString("pdf_sha256"))!!.matches(Regex("[0-9a-f]{64}"))) &&
                        (metadata!!.optString("pdf_storage_name"))!!.equals(
                            ((metadata!!.getString("pdf_sha256")).toString() + ".pdf")
                        )) && (integer(metadata!!.get("pdf_byte_size")) > 0)),
                    "JOB_DOCUMENT_INVALID",
                )
                for (field in
                    (arrayOf("document_id", "pdf_sha256", "pdf_storage_name", "pdf_byte_size"))!!) {
                    require(
                        ((metadata!!.get(field))!!.toString())!!.equals(
                            (invoice!!.get(field))!!.toString()
                        ),
                        "JOB_DOCUMENT_INVALID",
                    )
                }
            }
            if (phase!!.equals("PDF_PENDING")) {
                require((invoice!!.optString("pdf_state"))!!.equals("PDF_PENDING"), "JOB_INVALID")
            }
        }
        for (invoice in (invoices!!.values)!!) {
            if ((issued(invoice) && !(invoice!!.optString("local_issue_request"))!!.isEmpty())) {
                require(jobs!!.containsKey(invoice!!.getString("id")), "JOB_MISSING")
            }
        }
    }

    private @JvmStatic @Throws(Exception::class) fun numbers(
        namespaces: JSONObject,
        invoices: MutableMap<String, JSONObject>,
        jobs: MutableMap<String, JSONObject>,
        reservations: MutableMap<String, JSONObject>,
    ): Unit {
        var high: Long = 0
        var counters: MutableSet<Long> = HashSet()
        var reservedInvoices: MutableSet<String> = HashSet()
        var labels: MutableSet<String> = HashSet()
        for (invoice in (invoices!!.values)!!) {
            if ((issued(invoice) && !(invoice!!.optString("invoice_number"))!!.isEmpty())) {
                require(
                    labels!!.add(invoice!!.getString("invoice_number")),
                    "ISSUED_NUMBER_COLLISION",
                )
            }
        }
        for (entry in (reservations!!.entries)!!) {
            var reservation = entry!!.value
            var invoice = find(invoices, id(reservation, "invoice_id"))
            var job = find(jobs, invoice!!.getString("id"))
            var counter: Long = integer(reservation!!.get("counter"))
            require(
                ((((((integer(reservation!!.get("formatVersion")) == 1L) &&
                    (entry!!.key)!!.equals(reservation!!.getString("request_id"))) &&
                    (entry!!.key)!!.equals(invoice!!.getString("local_issue_request"))) &&
                    issued(invoice)) && (counter > 0)) && (counter <= 1000000000L)),
                "RESERVATION_INVALID",
            )
            require(counters!!.add(counter), "RESERVATION_COUNTER_COLLISION")
            require(
                reservedInvoices!!.add(invoice!!.getString("id")),
                "RESERVATION_INVOICE_COLLISION",
            )
            require(
                ((reservation!!.getString("invoice_number"))!!.equals(
                    invoice!!.getString("invoice_number")
                ) &&
                    (reservation!!.getString("snapshot_sha256"))!!.equals(
                        job!!.getString("snapshot_sha256")
                    )),
                "RESERVATION_SNAPSHOT_MISMATCH",
            )
            var receipt = invoice!!.getJSONObject("numbering_receipt")
            require(
                (((((receipt!!.optString("authority"))!!.equals(LocalAuthorityRules.ENGINE) &&
                    (receipt!!.getString("invoice_id"))!!.equals(invoice!!.getString("id"))) &&
                    (receipt!!.getString("invoice_number"))!!.equals(
                        invoice!!.getString("invoice_number")
                    )) && (integer(receipt!!.get("counter")) == counter)) &&
                    (receipt!!.getString("request_id"))!!.equals(entry!!.key)),
                "RESERVATION_SNAPSHOT_MISMATCH",
            )
            high = Math.max(high, counter)
        }
        for (invoice in (invoices!!.values)!!) {
            if ((issued(invoice) && !(invoice!!.optString("local_issue_request"))!!.isEmpty())) {
                require(
                    reservations!!.containsKey(invoice!!.getString("local_issue_request")),
                    "RESERVATION_MISSING",
                )
            }
        }
        if (namespaces!!.has("local_invoice_external_evidence")) {
            var proof = `object`(namespaces!!.get("local_invoice_external_evidence"))
            LocalAuthorityRules.requireEvidence(proof)
            var last: Long = integer(proof!!.get("last_number"))
            require(((last >= 0) && (last <= 1000000000L)), "COUNTER_INVALID")
            high = Math.max(high, last)
            var ids: MutableSet<String> = HashSet()
            var externalNumbers: MutableSet<String> = HashSet()
            var reservationIds: MutableSet<String> = HashSet()
            var originals = proof!!.getJSONArray("issued")
            var unproduced: MutableMap<String, JSONObject> = HashMap()
            run {
                var i: Int = 0
                while ((i < originals!!.length())) {
                    var original = originals!!.getJSONObject(i)
                    var invoiceId = id(original, "invoice_id")
                    var number = id(original, "invoice_number")
                    require(
                        (ids!!.add(invoiceId) && externalNumbers!!.add(number)),
                        "NUMBER_EVIDENCE_INVALID",
                    )
                    var known = invoices!!.get(invoiceId)
                    if ((original!!.optString("pdf_resolution"))!!.equals("not_created")) {
                        var job = find(jobs, invoiceId)
                        var authorization = job!!.getJSONObject("creation_authorization")
                        var review = proof!!.getJSONObject("current_review")
                        require(
                            ((((((((((((known !== null) && issued(known)) &&
                                number!!.equals(known!!.optString("invoice_number"))) &&
                                java.lang.Boolean.TRUE.equals(
                                    original!!.opt("not_created_confirmed")
                                )) &&
                                !((original!!.optString("not_created_reason"))!!.trim())!!
                                    .isEmpty()) &&
                                !LocalInvoiceCreationRules.hasDocumentFields(original)) &&
                                (original!!.optString("snapshot_sha256"))!!.equals(
                                    job!!.getString("snapshot_sha256")
                                )) &&
                                (authorization!!.getString("ledger_proof_sha256"))!!.equals(
                                    PixelMcpWriteTools.hash(proof)
                                )) && java.lang.Boolean.TRUE.equals(review!!.opt("confirmed"))) &&
                                (authorization!!.getString("installation_id"))!!.equals(
                                    review!!.optString("installation_id")
                                )) &&
                                (authorization!!.getString("restore_epoch"))!!.equals(
                                    review!!.optString("restore_epoch")
                                )) &&
                                (authorization!!.getString("authorized_at"))!!.equals(
                                    review!!.optString("reviewed_at")
                                )),
                            "NUMBER_EVIDENCE_INVALID",
                        )
                        unproduced!!.put(invoiceId, job)
                    } else {
                        require(
                            (original!!.optString("pdf_sha256"))!!.matches(Regex("[0-9a-f]{64}")),
                            "NUMBER_EVIDENCE_INVALID",
                        )
                        if ((known !== null)) {
                            require(
                                (issued(known) &&
                                    number!!.equals(known!!.optString("invoice_number"))),
                                "NUMBER_EVIDENCE_INVALID",
                            )
                            if (!(known!!.optString("pdf_sha256"))!!.isEmpty()) {
                                require(
                                    (known!!.getString("pdf_sha256"))!!.equals(
                                        original!!.getString("pdf_sha256")
                                    ),
                                    "NUMBER_EVIDENCE_INVALID",
                                )
                            }
                        } else {
                            require(!labels!!.contains(number), "EXTERNAL_NUMBER_COLLISION")
                        }
                    }

                    i++
                }
            }
            var externalReservations = proof!!.getJSONArray("reservations")
            run {
                var i: Int = 0
                while ((i < externalReservations!!.length())) {
                    var reservation = externalReservations!!.getJSONObject(i)
                    var counter: Long = integer(reservation!!.get("counter"))
                    require(
                        (((reservationIds!!.add(id(reservation, "reservation_id")) &&
                            (counter >= 0)) && (counter <= last)) &&
                            (Arrays.asList("reserved", "issued", "cancelled"))!!.contains(
                                reservation!!.optString("state")
                            )),
                        "NUMBER_EVIDENCE_INVALID",
                    )
                    id(reservation, "invoice_id")
                    id(reservation, "invoice_number")

                    i++
                }
            }
            for (entry in (unproduced!!.entries)!!) {
                var job = entry!!.value
                var frozen = job!!.getJSONObject("snapshot")
                var reservation = find(reservations, frozen!!.getString("local_issue_request"))
                var matched: Boolean = false
                run {
                    var i: Int = 0
                    while ((i < externalReservations!!.length())) {
                        var proofReservation = externalReservations!!.getJSONObject(i)
                        if (
                            (proofReservation!!.getString("reservation_id"))!!.equals(
                                reservation!!.getString("request_id")
                            )
                        ) {
                            matched =
                                ((((Arrays.asList("reserved", "issued"))!!.contains(
                                    proofReservation!!.optString("state")
                                ) &&
                                    (entry!!.key)!!.equals(
                                        proofReservation!!.getString("invoice_id")
                                    )) &&
                                    (frozen!!.getString("invoice_number"))!!.equals(
                                        proofReservation!!.getString("invoice_number")
                                    )) &&
                                    (integer(proofReservation!!.get("counter")) ==
                                        integer(reservation!!.get("counter"))))
                        }

                        i++
                    }
                }
                require(matched, "NUMBER_EVIDENCE_INVALID")
            }
        }
        if (namespaces!!.has("local_invoice_counter")) {
            var counter: Long =
                integer((`object`(namespaces!!.get("local_invoice_counter")))!!.get("last_number"))
            require(
                (((counter >= high) && (counter >= 0)) && (counter <= 1000000000L)),
                "COUNTER_INVALID",
            )
        } else {
            require((high == 0L), "COUNTER_MISSING")
        }
    }

    private @JvmStatic @Throws(Exception::class) fun recoveryAudit(
        invoices: MutableMap<String, JSONObject>,
        jobs: MutableMap<String, JSONObject>,
        audit: MutableMap<String, JSONObject>,
    ): Unit {
        for (entry in (audit!!.entries)!!) {
            var row = entry!!.value
            var invoice = find(invoices, id(row, "invoice_id"))
            var job = find(jobs, invoice!!.getString("id"))
            var resolution = row!!.getString("resolution")
            var request = id(row, "request_id")
            require(
                (((((integer(row!!.get("formatVersion")) == 1L) &&
                    (entry!!.key)!!.equals(
                        PixelMcpWriteTools.hash(
                            ((JSONObject())!!.put("request_id", request))!!.put(
                                "invoice_id",
                                invoice!!.getString("id"),
                            )
                        )
                    )) &&
                    (invoice!!.getString("invoice_number"))!!.equals(
                        row!!.getString("invoice_number")
                    )) &&
                    (job!!.getString("snapshot_sha256"))!!.equals(
                        row!!.getString("snapshot_sha256")
                    )) &&
                    (Arrays.asList(
                            "first_creation_explicitly_authorized",
                            "verified_original_adopted",
                        ))!!
                        .contains(resolution)),
                "RECOVERY_AUDIT_INVALID",
            )
            for (field in
                (arrayOf("before_job_sha256", "after_job_sha256", "ledger_proof_sha256"))!!) {
                require(
                    (row!!.optString(field))!!.matches(Regex("[0-9a-f]{64}")),
                    "RECOVERY_AUDIT_INVALID",
                )
            }
            require(
                ((row!!.optString("installation_id"))!!.matches(Regex("[0-9a-f-]{36}")) &&
                    (row!!.optString("restore_epoch"))!!.matches(Regex("[0-9a-f-]{36}"))),
                "RECOVERY_AUDIT_INVALID",
            )
            if (resolution!!.equals("first_creation_explicitly_authorized")) {
                require(
                    !((row!!.optString("reason"))!!.trim())!!.isEmpty(),
                    "RECOVERY_AUDIT_INVALID",
                )
            }
            Instant.parse(row!!.getString("reviewed_at"))
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun inspect(effective: JSONObject, namespaces: JSONObject): JSONObject {
        try {
            var invoices = index(effective!!.optJSONArray("invoices"))
            var work = index(effective!!.optJSONArray("worklogs"))
            var payments = records(namespaces, "local_payment:")
            var opening = records(namespaces, "invoice_opening_balance:")
            var jobs = records(namespaces, "local_invoice_job:")
            var reservations = records(namespaces, "local_number_reservation:")
            var workSources = records(namespaces, "local_invoice_sources:")
            var materials = records(namespaces, "local_invoice_material_sources:")
            var movements = records(namespaces, "revier:v1:entity:material_movement:")
            payments(invoices, payments, opening)
            sourceLedger("local_source_allocations", workSources, invoices, work, false)
            sourceLedger("local_material_source_allocations", materials, invoices, movements, true)
            jobs(invoices, jobs)
            numbers(namespaces, invoices, jobs, reservations)
            recoveryAudit(invoices, jobs, records(namespaces, "local_invoice_document_recovery:"))
            for (invoice in (invoices!!.values)!!) {
                if (
                    ((issued(invoice) &&
                        !(invoice!!.optString("local_issue_request"))!!.isEmpty()) &&
                        !(invoice!!.optString("document_type"))!!.equals("cancellation"))
                ) {
                    var works: MutableSet<String> = HashSet()
                    var usedMaterial: MutableSet<String> = HashSet()
                    var positions = invoice!!.getJSONArray("positions")
                    run {
                        var i: Int = 0
                        while ((i < positions!!.length())) {
                            var line = positions!!.getJSONObject(i)
                            var workId = line!!.optString("worklog_id")
                            if (!workId!!.isEmpty()) {
                                require(
                                    ((works!!.add(workId) &&
                                        workSources!!.containsKey(invoice!!.getString("id"))) &&
                                        (workSources!!.get(invoice!!.getString("id")))!!.has(
                                            workId
                                        )),
                                    "SOURCE_SNAPSHOT_MISMATCH",
                                )
                            }
                            if ((line!!.optString("source_type"))!!.equals("material_movement")) {
                                var movement = line!!.getString("source_id")
                                require(
                                    ((usedMaterial!!.add(movement) &&
                                        materials!!.containsKey(invoice!!.getString("id"))) &&
                                        (materials!!.get(invoice!!.getString("id")))!!.has(
                                            movement
                                        )),
                                    "SOURCE_SNAPSHOT_MISMATCH",
                                )
                            }

                            i++
                        }
                    }
                }
            }
            for (ledger in (materials!!.values)!!) {
                run {
                    var ids = ledger!!.keys()
                    while (ids!!.hasNext()) {
                        require(
                            !namespaces!!.has(("revier:v1:reversed:" + ids!!.next())),
                            "MATERIAL_REFERENCE_INVALID",
                        )
                    }
                }
            }
            return (((((JSONObject())!!.put("formatVersion", 1))!!.put("status", "PASS"))!!.put(
                        "counts",
                        (((((JSONObject())!!.put("payments", payments!!.size))!!.put(
                                        "document_jobs",
                                        jobs!!.size,
                                    ))!!
                                    .put("number_reservations", reservations!!.size))!!
                                .put("work_source_ledgers", workSources!!.size))!!
                            .put("material_source_ledgers", materials!!.size),
                    ))!!
                    .put("original_files_verified", false))!!
                .put("data_changed", false)
        } catch (fixed: IOException) {
            throw fixed
        } catch (malformed: Exception) {
            throw invalid("RECORD_INVALID")
        }
    }
}
