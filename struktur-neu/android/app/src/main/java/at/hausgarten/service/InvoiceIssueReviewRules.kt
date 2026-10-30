package at.hausgarten.service

import java.math.BigDecimal
import java.util.*
import org.json.*

/** One deterministic review of a draft and its dependencies; never reserves or mutates anything. */
internal object InvoiceIssueReviewRules {
    private @JvmStatic @Throws(Exception::class) fun withoutBinary(value: JSONObject): JSONObject {
        var result = DriveJournalRules.clean(value)
        for (key in (arrayOf("pdf_base64", "data", "full_data", "thumbnail_data"))!!) {
            result!!.remove(key)
        }
        return result
    }

    private @JvmStatic @Throws(Exception::class) fun find(
        state: JSONObject,
        collection: String,
        id: String,
    ): JSONObject? {
        var rows = state!!.optJSONArray(collection)
        run {
            var i: Int = 0
            while (((rows !== null) && (i < rows!!.length()))) {
                var row = rows!!.getJSONObject(i)
                if (id!!.equals(row!!.optString("id"))) {
                    return row
                }

                i++
            }
        }
        return null
    }

    private @JvmStatic @Throws(Exception::class) fun block(
        blockers: JSONArray,
        code: String,
        message: String,
        type: String,
        id: String,
    ): Unit {
        var value: JSONObject = ((JSONObject())!!.put("code", code))!!.put("message", message)
        if (!type!!.isEmpty()) {
            value!!.put("entity_type", type)
        }
        if (!id!!.isEmpty()) {
            value!!.put("id", id)
        }
        blockers!!.put(value)
    }

    private @JvmStatic @Throws(Exception::class) fun amount(raw: Any?): BigDecimal {
        if (((raw === null) || (raw === JSONObject.NULL))) {
            throw IllegalArgumentException()
        }
        var value: BigDecimal = BigDecimal(raw!!.toString())
        if (
            (((value!!.signum() < 0) || (value!!.precision() > 24)) ||
                (Math.abs(value!!.scale()) > 12))
        ) {
            throw IllegalArgumentException()
        }
        return value
    }

    private @JvmStatic fun decimal(value: BigDecimal): String {
        return (value!!.stripTrailingZeros())!!.toPlainString()
    }

    private @JvmStatic fun keys(value: JSONObject): SortedSet<String> {
        var result: TreeSet<String> = TreeSet()
        run {
            var names = value!!.keys()
            while (names!!.hasNext()) {
                result!!.add(names!!.next())
            }
        }
        return result
    }

    private @JvmStatic fun `object`(input: JSONObject, key: String): JSONObject {
        var value = input!!.optJSONObject(key)
        return (if ((value === null)) JSONObject() else value)
    }

    private @JvmStatic @Throws(Exception::class) fun authority(
        dependencies: JSONObject
    ): JSONObject {
        var source = `object`(dependencies, "authority")
        var result: JSONObject = JSONObject()
        for (key in
            (arrayOf(
                "mode",
                "migration_epoch",
                "invoice_authority",
                "installation_id",
                "restore_epoch",
            ))!!) {
            if (source!!.has(key)) {
                result!!.put(key, source!!.get(key))
            }
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun capture(state: JSONObject, invoiceId: String, dependencies: JSONObject): JSONObject {
        var blockers: JSONArray = JSONArray()
        var worklogs: JSONArray = JSONArray()
        var materials: JSONArray = JSONArray()
        var orders: JSONArray = JSONArray()
        var invoice = find(state, "invoices", invoiceId)
        var issuer = state!!.optJSONObject("business_settings")
        var recipient =
            (if ((invoice === null)) null
            else find(state, "customers", invoice!!.optString("customer_id")))
        var recordHash =
            (if ((invoice === null)) ""
            else
                PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice", invoice)))
        var authority = authority(dependencies)
        var version = dependencies!!.opt("formatVersion")
        if ((!(version is Number) || !(version!!.toString())!!.equals("1"))) {
            block(
                blockers,
                "INVOICE_REVIEW_DEPENDENCY_VERSION_UNSUPPORTED",
                "Der Ausgabeprüfstand benötigt eine passende Appversion. Keine Ausgabe freigeben.",
                "review",
                "",
            )
        }
        if ((invoice === null)) {
            block(
                blockers,
                "INVOICE_NOT_FOUND",
                "Der Rechnungsentwurf wurde nicht gefunden. Den aktuellen Rechnungsbestand öffnen.",
                "invoice",
                invoiceId,
            )
        } else {
            if (
                (IssuedInvoiceRules.isIssued(invoice) ||
                    !(invoice!!.optString("status", "Entwurf"))!!.equals("Entwurf"))
            ) {
                block(
                    blockers,
                    "INVOICE_ALREADY_ISSUED",
                    "Dieser Beleg ist kein ausstellbarer Entwurf. Das unveränderliche Original öffnen.",
                    "invoice",
                    invoiceId,
                )
            }
        }
        if ((issuer === null)) {
            block(
                blockers,
                "INVOICE_ISSUER_MISSING",
                "Die bestätigten Aussteller- und Nummerneinstellungen fehlen. Firmendaten prüfen.",
                "business_settings",
                "",
            )
        }
        if (((invoice !== null) && (recipient === null))) {
            block(
                blockers,
                "INVOICE_RECIPIENT_MISSING",
                "Der gespeicherte Rechnungsempfänger fehlt. Die ursprüngliche Zuordnung ausdrücklich klären.",
                "customer",
                invoice!!.optString("customer_id"),
            )
        }
        if (
            (!(authority!!.optString("mode"))!!.equals("LOCAL_ACTIVE") ||
                !(authority!!.optString("invoice_authority"))!!.equals("RECONCILED"))
        ) {
            block(
                blockers,
                "INVOICE_AUTHORITY_RECONCILIATION_REQUIRED",
                "Die lokale Rechnungsausgabe ist bis zum aktuellen Beleg- und Nummernabgleich gesperrt.",
                "authority",
                "",
            )
        }
        var technical = dependencies!!.optJSONArray("blockers")
        run {
            var i: Int = 0
            while (((technical !== null) && (i < technical!!.length()))) {
                blockers!!.put(technical!!.get(i))

                i++
            }
        }
        try {
            LocalBillingRules.validate(
                state,
                ((JSONObject())!!.put("formatVersion", 1))!!.put(
                    "by_invoice",
                    `object`(dependencies, "worklog_allocations_by_invoice"),
                ),
            )
        } catch (invalid: Exception) {
            block(
                blockers,
                "LOCAL_BILLING_SOURCE_REVIEW_REQUIRED",
                "Die lokalen Leistungszuordnungen stimmen nicht mit den ausgestellten Belegen überein. Vor einer Ausgabe prüfen.",
                "source_ledger",
                "",
            )
        }
        var shown = (if ((invoice === null)) null else withoutBinary(invoice))
        if (
            (((invoice !== null) && !IssuedInvoiceRules.isIssued(invoice)) &&
                (invoice!!.optString("status", "Entwurf"))!!.equals("Entwurf"))
        ) {
            try {
                InvoiceDraftRules.normalize(shown!!, invoice, state)
            } catch (invalid: Exception) {
                shown = withoutBinary(invoice)
                block(
                    blockers,
                    "INVOICE_DRAFT_REVIEW_REQUIRED",
                    (if ((invalid!!.message === null))
                        "Der gespeicherte Entwurf benötigt eine erneute fachliche Prüfung."
                    else invalid!!.message!!),
                    "invoice",
                    invoiceId,
                )
            }
        }
        var snapshot: JSONObject =
            (((((((JSONObject())!!.put(
                                        "invoice",
                                        (if ((shown === null)) JSONObject.NULL else shown),
                                    ))!!
                                    .put(
                                        "issuer",
                                        (if ((issuer === null)) JSONObject.NULL
                                        else withoutBinary(issuer)),
                                    ))!!
                                .put(
                                    "recipient",
                                    (if ((recipient === null)) JSONObject.NULL
                                    else withoutBinary(recipient)),
                                ))!!
                            .put("worklogs", worklogs))!!
                        .put("materials", materials))!!
                    .put("orders", orders))!!
                .put("authority", authority)
        if ((invoice !== null)) {
            var requestedWork: MutableMap<String, BigDecimal> = TreeMap()
            var requestedMaterial: MutableMap<String, BigDecimal> = TreeMap()
            var workIds: MutableSet<String> = TreeSet()
            var materialIds: MutableSet<String> = TreeSet()
            var orderIds: MutableSet<String> = TreeSet()
            var cancellation: Boolean =
                (invoice!!.optString("document_type"))!!.equals("cancellation")
            var positions = invoice!!.optJSONArray("positions")
            if (!(invoice!!.optString("order_id"))!!.isEmpty()) {
                orderIds!!.add(invoice!!.getString("order_id"))
            }
            if (((positions === null) || (positions!!.length() == 0))) {
                block(
                    blockers,
                    "INVOICE_POSITIONS_MISSING",
                    "Der Entwurf benötigt vollständige Leistungspositionen.",
                    "invoice",
                    invoiceId,
                )
            }
            run {
                var i: Int = 0
                while (((positions !== null) && (i < positions!!.length()))) {
                    var line = positions!!.optJSONObject(i)
                    if ((line === null)) {
                        block(
                            blockers,
                            "INVOICE_POSITION_INVALID",
                            "Eine Rechnungsposition ist nicht lesbar. Den Entwurf prüfen.",
                            "invoice",
                            invoiceId,
                        )
                        run {
                            i++
                            continue
                        }
                    }
                    var work = line!!.optString("worklog_id")
                    var type = line!!.optString("source_type")
                    var source = line!!.optString("source_id")
                    if (type!!.equals("worklog")) {
                        if ((!work!!.isEmpty() && !source!!.equals(work))) {
                            block(
                                blockers,
                                "INVOICE_SOURCE_REFERENCE_MISMATCH",
                                "Die Leistungsquellen einer Position widersprechen sich.",
                                "worklog",
                                source,
                            )
                        }
                        work = source
                    }
                    if ((!cancellation && !work!!.isEmpty())) {
                        workIds!!.add(work)
                        addRequested(requestedWork, work, line, "worklog", blockers)
                    }
                    if ((!cancellation && type!!.equals("material_movement"))) {
                        if (source!!.isEmpty()) {
                            block(
                                blockers,
                                "INVOICE_MATERIAL_SOURCE_MISSING",
                                "Die Materialposition benötigt eine eindeutige Einsatzquelle.",
                                "material_movement",
                                "",
                            )
                        } else {
                            materialIds!!.add(source)
                            addRequested(
                                requestedMaterial,
                                source,
                                line,
                                "material_movement",
                                blockers,
                            )
                        }
                    }
                    if ((line!!.isNull("price") || !line!!.has("price"))) {
                        block(
                            blockers,
                            "INVOICE_PRICE_MISSING",
                            "Ein bestätigter Positionspreis fehlt. Den Entwurf ergänzen.",
                            "invoice",
                            invoiceId,
                        )
                    }

                    i++
                }
            }
            var linked = invoice!!.optJSONArray("worklog_ids")
            run {
                var i: Int = 0
                while (((!cancellation && (linked !== null)) && (i < linked!!.length()))) {
                    workIds!!.add(linked!!.getString(i))

                    i++
                }
            }
            for (id in workIds!!) {
                var row = find(state, "worklogs", id)
                if ((row === null)) {
                    block(
                        blockers,
                        "INVOICE_WORKLOG_MISSING",
                        "Eine zugeordnete Arbeitsaufzeichnung fehlt. Den ursprünglichen Leistungsbezug klären.",
                        "worklog",
                        id,
                    )
                    worklogs!!.put(
                        ((JSONObject())!!.put("id", id))!!.put("record", JSONObject.NULL)
                    )
                    continue
                }
                if (!(row!!.optString("order_id"))!!.isEmpty()) {
                    orderIds!!.add(row!!.getString("order_id"))
                }
                var quantity = workQuantity(state, row, dependencies, blockers)
                var entry: JSONObject =
                    ((((((JSONObject())!!.put("id", id))!!.put("record", withoutBinary(row)))!!.put(
                                    "record_hash",
                                    OwnerJournalRules.version(row),
                                ))!!
                                .put(
                                    "requested_quantity_decimal",
                                    (if (requestedWork!!.containsKey(id))
                                        decimal(requestedWork!!.get(id)!!)
                                    else JSONObject.NULL),
                                ))!!
                            .put("billing_quantity", quantity))!!
                        .put(
                            "allocations_by_invoice",
                            allocations(
                                dependencies,
                                "worklog_allocations_by_invoice",
                                id,
                                blockers,
                            ),
                        )
                worklogs!!.put(entry)
                checkRequested(
                    requestedWork!!.get(id),
                    quantity!!.opt("remaining_decimal"),
                    id,
                    "worklog",
                    blockers,
                )
                checkSourceCustomer(state, row, "worklog", id, blockers)
                checkUnits(invoice, id, "worklog", "", blockers)
                if (!(invoice!!.optString("customer_id"))!!.equals(BillingRules.recipient(row))) {
                    block(
                        blockers,
                        "INVOICE_SOURCE_RECIPIENT_MISMATCH",
                        "Arbeitsaufzeichnung und gespeicherter Rechnungsempfänger passen nicht zusammen.",
                        "worklog",
                        id,
                    )
                }
            }
            var movements = `object`(dependencies, "material_movements")
            var reversals = `object`(dependencies, "material_reversals")
            for (id in materialIds!!) {
                var row = movements!!.optJSONObject(id)
                if ((row === null)) {
                    block(
                        blockers,
                        "INVOICE_MATERIAL_SOURCE_MISSING",
                        "Eine zugeordnete Materialbewegung fehlt. Die Einsatzdokumentation prüfen.",
                        "material_movement",
                        id,
                    )
                    materials!!.put(
                        ((JSONObject())!!.put("id", id))!!.put("record", JSONObject.NULL)
                    )
                    continue
                }
                if (!(row!!.optString("order_id"))!!.isEmpty()) {
                    orderIds!!.add(row!!.getString("order_id"))
                }
                var reversed = reversals!!.optString(id)
                var used =
                    allocations(dependencies, "material_allocations_by_invoice", id, blockers)
                var total: BigDecimal? = null
                var allocated = sum(used, blockers, "material_movement", id)
                var remaining: BigDecimal? = null
                try {
                    RevierRules.version(row)
                    if (
                        (!id!!.equals(row!!.optString("id")) ||
                            !(row!!.optString("kind"))!!.equals("material_movement"))
                    ) {
                        throw IllegalArgumentException()
                    }
                    var signed: BigDecimal = BigDecimal(row!!.getString("quantity_decimal"))
                    if (
                        (!(row!!.optString("movement_kind"))!!.equals("consume") ||
                            (signed!!.signum() >= 0))
                    ) {
                        throw IllegalArgumentException()
                    }
                    total = signed!!.abs()
                    remaining = total!!.subtract(allocated)
                    if ((remaining!!.signum() < 0)) {
                        throw IllegalArgumentException()
                    }
                } catch (invalid: Exception) {
                    remaining = null
                    block(
                        blockers,
                        "INVOICE_MATERIAL_QUANTITY_REVIEW_REQUIRED",
                        "Materialquelle und bereits zugeordnete Mengen müssen geprüft werden.",
                        "material_movement",
                        id,
                    )
                }
                if (!reversed!!.isEmpty()) {
                    block(
                        blockers,
                        "INVOICE_MATERIAL_SOURCE_REVERSED",
                        "Diese Materialbewegung wurde bereits nachvollziehbar gegen gebucht. Eine gültige Quelle wählen.",
                        "material_movement",
                        id,
                    )
                }
                var entry: JSONObject =
                    ((((((((JSONObject())!!.put("id", id))!!.put("record", withoutBinary(row)))!!
                                            .put(
                                                "record_hash",
                                                PixelMcpWriteTools.hash(
                                                    DriveJournalRules.clean(row)
                                                ),
                                            ))!!
                                        .put(
                                            "requested_quantity_decimal",
                                            (if (requestedMaterial!!.containsKey(id))
                                                decimal(requestedMaterial!!.get(id)!!)
                                            else JSONObject.NULL),
                                        ))!!
                                    .put(
                                        "allocated_decimal",
                                        (if ((allocated === null)) JSONObject.NULL
                                        else decimal(allocated)),
                                    ))!!
                                .put(
                                    "remaining_decimal",
                                    (if ((remaining === null)) JSONObject.NULL
                                    else decimal(remaining)),
                                ))!!
                            .put("reversal_id", reversed))!!
                        .put("allocations_by_invoice", used)
                materials!!.put(entry)
                checkRequested(
                    requestedMaterial!!.get(id),
                    entry!!.opt("remaining_decimal"),
                    id,
                    "material_movement",
                    blockers,
                )
                checkSourceCustomer(state, row, "material_movement", id, blockers)
                checkUnits(invoice, id, "material_movement", row!!.optString("unit"), blockers)
                for (document in (keys(used))!!) {
                    if (
                        !matchesMaterialAllocation(
                            find(state, "invoices", document),
                            id,
                            row!!.optString("unit"),
                            used!!.opt(document),
                        )
                    ) {
                        block(
                            blockers,
                            "INVOICE_SOURCE_LEDGER_INVALID",
                            "Eine Materialzuordnung stimmt nicht mit dem unveränderlichen ausgestellten Beleg und seiner Positionsmenge überein.",
                            "invoice",
                            document,
                        )
                    }
                }
                try {
                    var payer = row!!.optString("billing_customer_id")
                    if (payer!!.isEmpty()) {
                        var site = find(state, "customers", row!!.optString("customer_id"))
                        if ((site !== null)) {
                            entry!!.put("billing_customer_context", withoutBinary(site))
                        }
                        payer =
                            BillingRules.defaultRecipient(
                                row!!.getString("customer_id"),
                                row!!.optString("order_id"),
                                state,
                            )
                    }
                    if (!payer!!.equals(invoice!!.optString("customer_id"))) {
                        throw IllegalArgumentException()
                    }
                } catch (mismatch: Exception) {
                    block(
                        blockers,
                        "INVOICE_MATERIAL_RECIPIENT_MISMATCH",
                        "Materialeinsatz und gespeicherter Rechnungsempfänger müssen ausdrücklich abgeglichen werden.",
                        "material_movement",
                        id,
                    )
                }
            }
            for (id in orderIds!!) {
                var row = find(state, "orders", id)
                if ((row === null)) {
                    block(
                        blockers,
                        "INVOICE_ORDER_MISSING",
                        "Ein zugeordneter Auftrag fehlt. Den ursprünglichen Bezug klären.",
                        "order",
                        id,
                    )
                    orders!!.put(((JSONObject())!!.put("id", id))!!.put("record", JSONObject.NULL))
                } else {
                    orders!!.put(
                        (((JSONObject())!!.put("id", id))!!.put("record", withoutBinary(row)))!!
                            .put("record_hash", OwnerJournalRules.version(row))
                    )
                }
            }
            if (!(invoice!!.optString("correction_of"))!!.isEmpty()) {
                var original = find(state, "invoices", invoice!!.getString("correction_of"))
                snapshot!!.put(
                    "correction_original",
                    (if ((original === null)) JSONObject.NULL else withoutBinary(original)),
                )
                if (((original === null) || !IssuedInvoiceRules.isIssued(original))) {
                    block(
                        blockers,
                        "INVOICE_CORRECTION_ORIGINAL_MISSING",
                        "Der Korrekturbezug benötigt den bestehenden ausgestellten Originalbeleg.",
                        "invoice",
                        invoice!!.getString("correction_of"),
                    )
                }
            }
        }
        var review: JSONObject =
            ((((((JSONObject())!!.put("formatVersion", 1))!!.put("invoice_id", invoiceId))!!.put(
                            "record_hash",
                            recordHash,
                        ))!!
                        .put("snapshot", snapshot))!!
                    .put("ready", (blockers!!.length() == 0)))!!
                .put("blockers", blockers)
        review!!.put("context_hash", PixelMcpWriteTools.hash(review))
        return (((JSONObject())!!.put(
                    "invoice",
                    (if ((invoice === null)) JSONObject.NULL else withoutBinary(invoice)),
                ))!!
                .put("record_hash", recordHash))!!
            .put("issue_review", review)
    }

    private @JvmStatic @Throws(Exception::class) fun addRequested(
        requested: MutableMap<String, BigDecimal>,
        id: String,
        line: JSONObject,
        type: String,
        blockers: JSONArray,
    ): Unit {
        try {
            var value = amount(line!!.opt("quantity"))
            if (((value!!.signum() == 0) || requested!!.containsKey(id))) {
                throw IllegalArgumentException()
            }
            if (
                (line!!.has("source_quantity_decimal") &&
                    (value!!.compareTo(amount(line!!.get("source_quantity_decimal"))) != 0))
            ) {
                throw IllegalArgumentException()
            }
            requested!!.put(id, value)
        } catch (invalid: Exception) {
            block(
                blockers,
                "INVOICE_SOURCE_QUANTITY_REVIEW_REQUIRED",
                "Die Quellenmenge einer Position fehlt, ist doppelt oder widersprüchlich.",
                type,
                id,
            )
        }
    }

    private @JvmStatic @Throws(Exception::class) fun allocations(
        dependencies: JSONObject,
        name: String,
        source: String,
        blockers: JSONArray,
    ): JSONObject {
        var all = `object`(dependencies, name)
        var result: JSONObject = JSONObject()
        for (invoice in (keys(all))!!) {
            var row = all!!.optJSONObject(invoice)
            if ((row === null)) {
                block(
                    blockers,
                    "INVOICE_SOURCE_LEDGER_INVALID",
                    "Eine lokale Leistungszuordnung ist beschädigt. Keine neue Ausgabe freigeben.",
                    "invoice",
                    invoice,
                )
                result!!.put(invoice, JSONObject.NULL)
                continue
            }
            if (row!!.has(source)) {
                result!!.put(invoice, row!!.get(source))
            }
        }
        return result
    }

    private @JvmStatic @Throws(Exception::class) fun sum(
        allocations: JSONObject,
        blockers: JSONArray,
        type: String,
        id: String,
    ): BigDecimal? {
        var total = BigDecimal.ZERO
        var valid: Boolean = true
        for (invoice in (keys(allocations))!!) {
            try {
                var raw = allocations!!.get(invoice)
                if (!(raw is String)) {
                    throw IllegalArgumentException()
                }
                var value = amount(raw)
                if ((value!!.signum() == 0)) {
                    throw IllegalArgumentException()
                }
                total = total!!.add(value)
            } catch (invalid: Exception) {
                valid = false
                block(
                    blockers,
                    "INVOICE_SOURCE_LEDGER_INVALID",
                    "Bereits zugeordnete Leistungsmenge ist nicht zuverlässig lesbar.",
                    type,
                    id,
                )
            }
        }
        return (if (valid) total else null)
    }

    private @JvmStatic @Throws(Exception::class) fun workQuantity(
        state: JSONObject,
        row: JSONObject,
        dependencies: JSONObject,
        blockers: JSONArray,
    ): JSONObject {
        var id = row!!.getString("id")
        var used = allocations(dependencies, "worklog_allocations_by_invoice", id, blockers)
        var allocated = sum(used, blockers, "worklog", id)
        var quantity: BigDecimal? = null
        var remaining: BigDecimal? = null
        var unknown: Boolean = false
        try {
            quantity = amount(row!!.opt("hours"))
            remaining = quantity!!.subtract(allocated)
            if ((remaining!!.signum() < 0)) {
                throw IllegalArgumentException()
            }
        } catch (invalid: Exception) {
            remaining = null
            block(
                blockers,
                "INVOICE_SOURCE_QUANTITY_REVIEW_REQUIRED",
                "Gespeicherte Stunden und bereits zugeordnete Mengen passen nicht zusammen.",
                "worklog",
                id,
            )
        }
        var invoices = state!!.optJSONArray("invoices")
        run {
            var i: Int = 0
            while (((invoices !== null) && (i < invoices!!.length()))) {
                var invoice = invoices!!.getJSONObject(i)
                if (
                    ((IssuedInvoiceRules.isIssued(invoice) &&
                        LocalBillingRules.linked(invoice, id)) &&
                        !used!!.has(invoice!!.getString("id")))
                ) {
                    unknown = true
                }

                i++
            }
        }
        if (
            ((row!!.optInt("billed", 0) != 0) &&
                ((remaining === null) || (remaining!!.signum() > 0)))
        ) {
            unknown = true
        }
        if (unknown) {
            block(
                blockers,
                "INVOICE_SOURCE_BILLING_UNCLEAR",
                "Für diese Arbeitsaufzeichnung ist die offene Menge nicht vollständig belegt. Historische Abrechnung prüfen.",
                "worklog",
                id,
            )
            remaining = null
        }
        for (invoice in (keys(used))!!) {
            var original = find(state, "invoices", invoice)
            if (
                (((original === null) || !IssuedInvoiceRules.isIssued(original)) ||
                    !LocalBillingRules.linked(original, id))
            ) {
                block(
                    blockers,
                    "INVOICE_SOURCE_LEDGER_INVALID",
                    "Eine Leistungszuordnung besitzt keinen passenden ausgestellten Beleg.",
                    "invoice",
                    invoice,
                )
            }
        }
        return (((((JSONObject())!!.put("formatVersion", 1))!!.put(
                        "quantity_decimal",
                        (if ((quantity === null)) JSONObject.NULL else decimal(quantity)),
                    ))!!
                    .put(
                        "allocated_decimal",
                        (if ((allocated === null)) JSONObject.NULL else decimal(allocated)),
                    ))!!
                .put(
                    "remaining_decimal",
                    (if ((remaining === null)) JSONObject.NULL else decimal(remaining)),
                ))!!
            .put(
                "evidence",
                (if ((remaining === null)) "billing_unclear"
                else
                    (if (((remaining!!.signum() == 0) && (allocated!!.signum() > 0)))
                        "proven_invoiced"
                    else
                        (if ((allocated!!.signum() > 0)) "partially_invoiced"
                        else "marked_open_not_proven"))),
            )
    }

    private @JvmStatic @Throws(Exception::class) fun checkSourceCustomer(
        state: JSONObject,
        source: JSONObject,
        type: String,
        id: String,
        blockers: JSONArray,
    ): Unit {
        if ((find(state, "customers", source!!.optString("customer_id")) === null)) {
            block(
                blockers,
                "INVOICE_SOURCE_CUSTOMER_MISSING",
                "Der ursprüngliche Einsatzkunde einer Leistungsquelle fehlt. Die Zuordnung klären; keinen Ersatzkunden automatisch übernehmen.",
                type,
                id,
            )
        }
    }

    private @JvmStatic @Throws(Exception::class) fun checkUnits(
        invoice: JSONObject,
        id: String,
        type: String,
        expected: String,
        blockers: JSONArray,
    ): Unit {
        var lines = invoice!!.optJSONArray("positions")
        run {
            var i: Int = 0
            while (((lines !== null) && (i < lines!!.length()))) {
                var line = lines!!.optJSONObject(i)
                if ((line === null)) {
                    run {
                        i++
                        continue
                    }
                }
                var linked: Boolean =
                    (if (type!!.equals("worklog"))
                        (id!!.equals(line!!.optString("worklog_id")) ||
                            ((line!!.optString("source_type"))!!.equals("worklog") &&
                                id!!.equals(line!!.optString("source_id"))))
                    else
                        ((line!!.optString("source_type"))!!.equals("material_movement") &&
                            id!!.equals(line!!.optString("source_id"))))
                if (!linked) {
                    run {
                        i++
                        continue
                    }
                }
                var unit = line!!.optString("unit")
                if (
                    (if (type!!.equals("worklog"))
                        !(Arrays.asList("h", "std", "std.", "stunde", "stunden"))!!.contains(
                            unit!!.lowercase(Locale.ROOT)
                        )
                    else !unit!!.equals(expected))
                ) {
                    block(
                        blockers,
                        "INVOICE_SOURCE_UNIT_REVIEW_REQUIRED",
                        "Positions- und Quellenmenge benötigen dieselbe fachliche Einheit. Die Position prüfen.",
                        type,
                        id,
                    )
                }

                i++
            }
        }
    }

    private @JvmStatic @Throws(Exception::class) fun matchesMaterialAllocation(
        invoice: JSONObject?,
        id: String,
        unit: String,
        raw: Any?,
    ): Boolean {
        try {
            if (
                (((((invoice === null) || !IssuedInvoiceRules.isIssued(invoice)) ||
                    !LocalAuthorityRules.ENGINE.equals(
                        (`object`(invoice, "numbering_receipt"))!!.optString("authority")
                    )) || (invoice!!.optString("local_issue_request"))!!.isEmpty()) ||
                    !(raw is String))
            ) {
                return false
            }
            var expected = amount(raw)
            var snapshot =
                amount((`object`(invoice, "local_material_source_allocations"))!!.opt(id))
            if (((expected!!.signum() <= 0) || (expected!!.compareTo(snapshot) != 0))) {
                return false
            }
            var lines = invoice!!.optJSONArray("positions")
            var count: Int = 0
            run {
                var i: Int = 0
                while (((lines !== null) && (i < lines!!.length()))) {
                    var line = lines!!.optJSONObject(i)
                    if (
                        (((line === null) ||
                            !(line!!.optString("source_type"))!!.equals("material_movement")) ||
                            !id!!.equals(line!!.optString("source_id")))
                    ) {
                        run {
                            i++
                            continue
                        }
                    }
                    count++
                    if (
                        ((!unit!!.equals(line!!.optString("unit")) ||
                            (expected!!.compareTo(amount(line!!.opt("quantity"))) != 0)) ||
                            (line!!.has("source_quantity_decimal") &&
                                (expected!!.compareTo(
                                    amount(line!!.get("source_quantity_decimal"))
                                ) != 0)))
                    ) {
                        return false
                    }

                    i++
                }
            }
            return (count == 1)
        } catch (invalid: Exception) {
            return false
        }
    }

    private @JvmStatic @Throws(Exception::class) fun checkRequested(
        requested: BigDecimal?,
        remaining: Any?,
        id: String,
        type: String,
        blockers: JSONArray,
    ): Unit {
        try {
            if (
                (((requested === null) || (requested!!.signum() <= 0)) ||
                    (requested!!.compareTo(amount(remaining)) > 0))
            ) {
                throw IllegalArgumentException()
            }
        } catch (invalid: Exception) {
            block(
                blockers,
                "INVOICE_SOURCE_QUANTITY_EXCEEDED",
                "Die gewünschte Positionsmenge überschreitet den belegten Rest oder ist noch ungeklärt.",
                type,
                id,
            )
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun requireCurrent(review: JSONObject, expectedContextHash: String?): Unit {
        var version = review!!.opt("formatVersion")
        if (
            (((!(version is Number) || !(version!!.toString())!!.equals("1")) ||
                (expectedContextHash === null)) ||
                !expectedContextHash!!.matches(Regex("[0-9a-f]{64}")))
        ) {
            throw PixelMcpWriteTools.Rejected(
                "INVOICE_REVIEW_CONTEXT_REQUIRED",
                "Die vollständige lokale Ausgabeprüfung neu öffnen und ausdrücklich bestätigen.",
            )
        }
        if (!expectedContextHash!!.equals(review!!.optString("context_hash"))) {
            throw PixelMcpWriteTools.Rejected(
                "WRITE_CONFLICT",
                "Entwurf, Aussteller, Empfänger oder Leistungsquellen haben sich seit der Prüfung geändert. Den aktuellen Ausgabeentwurf erneut prüfen.",
            )
        }
        if (
            ((!review!!.optBoolean("ready") || (review!!.optJSONArray("blockers") === null)) ||
                ((review!!.getJSONArray("blockers"))!!.length() != 0))
        ) {
            throw PixelMcpWriteTools.Rejected(
                "INVOICE_REVIEW_BLOCKED",
                (if (
                    ((review!!.optJSONArray("blockers") !== null) &&
                        ((review!!.getJSONArray("blockers"))!!.length() > 0))
                )
                    ((review!!.getJSONArray("blockers"))!!.getJSONObject(0))!!.optString(
                        "message",
                        "Die Ausgabeprüfung ist noch nicht vollständig.",
                    )
                else "Die Ausgabeprüfung ist noch nicht vollständig."),
            )
        }
    }
}
