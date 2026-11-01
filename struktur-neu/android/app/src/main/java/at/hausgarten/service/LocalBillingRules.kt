package at.hausgarten.service

import java.math.BigDecimal
import java.util.*
import org.json.*

/** Exact remaining local source quantities. Legacy links never imply a guessed partial amount. */
internal object LocalBillingRules {
    /** Labels belong to copied display rows; immutable payment entries remain the hash authority. */
    @JvmStatic
    fun paymentDisplay(payments: JSONArray, invoices: JSONArray): JSONArray {
        val numbers = HashMap<String, String>()
        for (index in 0 until invoices.length()) {
            val invoice = invoices.getJSONObject(index)
            numbers[invoice.getString("id")] = invoice.optString("invoice_number")
        }
        val result = JSONArray()
        for (index in 0 until payments.length()) {
            val payment = JSONObject(payments.getJSONObject(index).toString())
            val allocations = payment.getJSONArray("allocations")
            for (allocationIndex in 0 until allocations.length()) {
                val allocation = allocations.getJSONObject(allocationIndex)
                allocation.put("invoice_number", numbers[allocation.getString("invoice_id")] ?: "")
            }
            result.put(payment)
        }
        return result
    }

    private @JvmStatic fun invalid(): PixelMcpWriteTools.Rejected {
        return PixelMcpWriteTools.Rejected(
            "LOCAL_BILLING_SOURCE_REVIEW_REQUIRED",
            "Lokale Leistungszuordnung und Belegsnapshot passen nicht zusammen. Keine erneute Abrechnung freigeben; Quellen prüfen.",
        )
    }

    private @JvmStatic @Throws(Exception::class) fun require(condition: Boolean): Unit {
        if (!condition) {
            throw invalid()
        }
    }

    private @JvmStatic fun decimal(amount: BigDecimal): String {
        return (amount!!.stripTrailingZeros())!!.toPlainString()
    }

    private @JvmStatic @Throws(Exception::class) fun amount(raw: Any?): BigDecimal {
        if (((raw === null) || (raw === JSONObject.NULL))) {
            throw invalid()
        }
        try {
            var value: BigDecimal = BigDecimal(raw!!.toString())
            require(
                (((value!!.signum() >= 0) && (value!!.precision() <= 24)) &&
                    (Math.abs(value!!.scale()) <= 10))
            )
            return value
        } catch (malformed: NumberFormatException) {
            throw invalid()
        }
    }

    private @JvmStatic @Throws(Exception::class) fun indexed(
        values: JSONArray?
    ): MutableMap<String, JSONObject> {
        var result: MutableMap<String, JSONObject> = HashMap()
        run {
            var i: Int = 0
            while (((values !== null) && (i < values!!.length()))) {
                var value = values!!.getJSONObject(i)
                var id = value!!.optString("id")
                require((!id!!.isEmpty() && (result!!.put(id, value) === null)))

                i++
            }
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun linked(invoice: JSONObject, id: String): Boolean {
        var links = invoice!!.optJSONArray("worklog_ids")
        run {
            var i: Int = 0
            while (((links !== null) && (i < links!!.length()))) {
                if (id!!.equals(links!!.optString(i))) {
                    return true
                }

                i++
            }
        }
        var lines = invoice!!.optJSONArray("positions")
        run {
            var i: Int = 0
            while (((lines !== null) && (i < lines!!.length()))) {
                if (id!!.equals((lines!!.getJSONObject(i))!!.optString("worklog_id"))) {
                    return true
                }

                i++
            }
        }
        return false
    }

    private @JvmStatic fun issued(invoice: JSONObject): Boolean {
        return IssuedInvoiceRules.isIssued(invoice)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validate(master: JSONObject, sources: JSONObject): Unit {
        var format = sources!!.opt("formatVersion")
        require(((format is Number) && (format!!.toString())!!.equals("1")))
        var byInvoice = sources!!.getJSONObject("by_invoice")
        var totals: MutableMap<String, BigDecimal> = HashMap()
        var invoices = master!!.optJSONArray("invoices")
        var logs = master!!.optJSONArray("worklogs")
        var invoiceIndex = indexed(invoices)
        var logIndex = indexed(logs)
        run {
            var ids = byInvoice!!.keys()
            while (ids!!.hasNext()) {
                var id = ids!!.next()
                var invoice = invoiceIndex!!.get(id)
                var allocation = byInvoice!!.getJSONObject(id)
                require((invoice !== null))
                var snapshot = invoice!!.optJSONObject("local_source_allocations")
                require(
                    ((((issued(invoice) && (snapshot !== null)) &&
                        (PixelMcpWriteTools.hash(allocation))!!.equals(
                            PixelMcpWriteTools.hash(snapshot)
                        )) &&
                        LocalAuthorityRules.ENGINE.equals(
                            (if ((invoice!!.optJSONObject("numbering_receipt") === null)) ""
                            else
                                (invoice!!.getJSONObject("numbering_receipt"))!!.optString(
                                    "authority"
                                ))
                        )) && !(invoice!!.optString("local_issue_request"))!!.isEmpty())
                )
                var lineQuantities: MutableMap<String, BigDecimal> = HashMap()
                var lines = invoice!!.optJSONArray("positions")
                run {
                    var i: Int = 0
                    while (((lines !== null) && (i < lines!!.length()))) {
                        var line = lines!!.getJSONObject(i)
                        var work = line!!.optString("worklog_id")
                        if (work!!.isEmpty()) {
                            run {
                                i++
                                continue
                            }
                        }
                        require((!lineQuantities!!.containsKey(work) && allocation!!.has(work)))
                        lineQuantities!!.put(work, amount(line!!.get("quantity")))

                        i++
                    }
                }
                require((lineQuantities!!.size == allocation!!.length()))
                run {
                    var keys = allocation!!.keys()
                    while (keys!!.hasNext()) {
                        var work = keys!!.next()
                        var log = logIndex!!.get(work)
                        require((log !== null))
                        var quantity = amount(allocation!!.get(work))
                        require(
                            (((((allocation!!.get(work) is String) && (quantity!!.signum() > 0)) &&
                                lineQuantities!!.containsKey(work)) &&
                                (quantity!!.compareTo(lineQuantities!!.get(work)) == 0)) &&
                                linked(invoice, work))
                        )
                        totals!!.put(
                            work,
                            (totals!!.getOrDefault(work, BigDecimal.ZERO))!!.add(quantity),
                        )
                        require(((totals!!.get(work))!!.compareTo(amount(log!!.get("hours"))) <= 0))
                    }
                }
            }
        }
        run {
            var i: Int = 0
            while (((invoices !== null) && (i < invoices!!.length()))) {
                var invoice = invoices!!.getJSONObject(i)
                if ((invoice!!.has("local_source_allocations") && issued(invoice))) {
                    require(byInvoice!!.has(invoice!!.getString("id")))
                }

                i++
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun state(row: JSONObject, invoices: JSONArray?, master: JSONObject): JSONObject? {
        var sources = master!!.optJSONObject("local_billing_sources")
        if ((sources === null)) {
            return null
        }
        var version = sources!!.opt("formatVersion")
        require(((version is Number) && (version!!.toString())!!.equals("1")))
        var byInvoice = sources!!.getJSONObject("by_invoice")
        var quantity = amount(row!!.opt("hours"))
        var allocated = BigDecimal.ZERO
        var draft: Boolean = false
        var found: Boolean = false
        run {
            var i: Int = 0
            while (((invoices !== null) && (i < invoices!!.length()))) {
                var invoice = invoices!!.getJSONObject(i)
                if (!linked(invoice, row!!.optString("id"))) {
                    run {
                        i++
                        continue
                    }
                }
                if (!issued(invoice)) {
                    draft = true
                    run {
                        i++
                        continue
                    }
                }
                var values = byInvoice!!.optJSONObject(invoice!!.optString("id"))
                if (((values === null) || !values!!.has(row!!.optString("id")))) {
                    return null
                }
                allocated = allocated!!.add(amount(values!!.get(row!!.getString("id"))))
                found = true

                i++
            }
        }
        require((allocated!!.compareTo(quantity) <= 0))
        var remaining = quantity!!.subtract(allocated)
        if (((row!!.optInt("billed", 0) != 0) && (remaining!!.signum() > 0))) {
            return null
        }
        var evidence =
            (if (((remaining!!.signum() == 0) && found)) "proven_invoiced"
            else
                (if ((allocated!!.signum() > 0)) "partially_invoiced"
                else (if (draft) "draft_linked_not_proven" else "marked_open_not_proven")))
        return (((((JSONObject())!!.put("formatVersion", 1))!!.put(
                        "quantity_decimal",
                        decimal(quantity),
                    ))!!
                    .put("allocated_decimal", decimal(allocated)))!!
                .put("remaining_decimal", decimal(remaining)))!!
            .put("evidence", evidence)
    }
}
