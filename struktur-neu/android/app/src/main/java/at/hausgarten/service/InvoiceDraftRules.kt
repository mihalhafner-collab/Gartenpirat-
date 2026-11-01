package at.hausgarten.service

import java.math.*
import java.util.*
import org.json.*

/** Shared validation/calculation for UI, MCP and Drive replay. */
internal class InvoiceDraftRules {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun normalize(p: JSONObject, previous: JSONObject?, master: JSONObject): Unit {
            if (
                ((previous !== null) &&
                    ((!(previous!!.optString("issued_at"))!!.isEmpty() ||
                        !(previous!!.optString("sevdesk_id"))!!.isEmpty()) ||
                        !("Entwurf")!!.equals(previous!!.optString("status", "Entwurf"))))
            ) {
                throw IllegalArgumentException(
                    "Ausgestellte und importierte Belege bleiben unverändert."
                )
            }
            if (
                ((!("Entwurf")!!.equals(p!!.optString("status", "Entwurf")) ||
                    !(p!!.optString("issued_at"))!!.isEmpty()) ||
                    !(p!!.optString("sevdesk_id"))!!.isEmpty())
            ) {
                throw IllegalArgumentException("Nur lokale Entwürfe können gespeichert werden.")
            }
            PixelMcpWriteTools.find(
                master!!.getJSONArray("customers"),
                p!!.optString("customer_id"),
            )
            TestDataRules.guard(p, master)
            var order = p!!.optString("order_id")
            if (
                (!order!!.isEmpty() &&
                    !(p!!.optString("customer_id"))!!.equals(
                        BillingRules.recipient(
                            PixelMcpWriteTools.find(master!!.getJSONArray("orders"), order)
                        )
                    ))
            ) {
                throw IllegalArgumentException(
                    "Rechnungsempfänger und ausdrücklich zugeordneter Abrechnungskunde des Auftrags passen nicht zusammen."
                )
            }
            for (key in (arrayOf("invoice_date", "service_start", "service_end"))!!) {
                if (!(p!!.optString(key))!!.isEmpty()) {
                    java.time.LocalDate.parse(p!!.getString(key))
                }
            }
            if (
                ((!(p!!.optString("service_start"))!!.isEmpty() &&
                    !(p!!.optString("service_end"))!!.isEmpty()) &&
                    ((p!!.getString("service_start"))!!.compareTo(p!!.getString("service_end")) >
                        0))
            ) {
                throw IllegalArgumentException("Leistungszeitraum ist widersprüchlich.")
            }
            var days: Int = p!!.optInt("payment_days", 14)
            if (((days < 0) || (days > 365))) {
                throw IllegalArgumentException("Zahlungsziel 0 bis 365 Tage.")
            }
            var positions = p!!.getJSONArray("positions")
            if (((positions!!.length() < 1) || (positions!!.length() > 200))) {
                throw IllegalArgumentException("1 bis 200 Rechnungspositionen erforderlich.")
            }
            PixelMcpEntityWriteTools.validatePositions(positions)
            var settings = master!!.optJSONObject("business_settings")
            if ((settings === null)) {
                settings = JSONObject()
            }
            var taxMode = p!!.optString("tax_mode")
            if ((taxMode!!.isEmpty() || taxMode!!.equals("unconfigured"))) {
                taxMode = settings!!.optString("tax_mode")
                if (taxMode!!.isEmpty()) {
                    taxMode = "unconfigured"
                }
            }
            if (
                !(Arrays.asList("small_business", "standard", "unconfigured"))!!.contains(taxMode)
            ) {
                throw IllegalArgumentException("Steuermodus ist ungültig.")
            }
            var recipientType = p!!.optString("recipient_type")
            var recipientVat = p!!.optString("recipient_vat_id")
            if (
                ((!(Arrays.asList("", "b2b", "consumer"))!!.contains(recipientType) ||
                    (recipientVat!!.length > 40)) ||
                    (!recipientVat!!.isEmpty() &&
                        !recipientVat!!.matches(Regex("[A-Za-z]{2}[A-Za-z0-9]{2,38}"))))
            ) {
                throw IllegalArgumentException("Empfängertyp oder UID ist ungültig.")
            }
            if (p!!.has("recipient_type")) {
                p!!.put("recipient_type", recipientType)
            }
            if (p!!.has("recipient_vat_id")) {
                p!!.put("recipient_vat_id", recipientVat)
            }
            p!!.put("tax_mode", taxMode)
            var used: MutableSet<String> = LinkedHashSet()
            var total = BigDecimal.ZERO
            var totalTax = BigDecimal.ZERO
            run {
                var i: Int = 0
                while ((i < positions!!.length())) {
                    var line = positions!!.getJSONObject(i)
                    for (key in (arrayOf("description", "worklog_id"))!!) {
                        if (!line!!.has(key)) {
                            line!!.put(key, "")
                        }
                    }
                    if (!line!!.has("unit")) {
                        line!!.put("unit", "Stk.")
                    }
                    for (key in (arrayOf("tax_rate", "discount"))!!) {
                        if (!line!!.has(key)) {
                            line!!.put(key, 0)
                        }
                    }
                    var work = line!!.optString("worklog_id")
                    if (!work!!.isEmpty()) {
                        var log = PixelMcpWriteTools.find(master!!.getJSONArray("worklogs"), work)
                        if (
                            ((!used!!.add(work) || (log!!.optInt("billed") != 0)) ||
                                !(p!!.getString("customer_id"))!!.equals(
                                    BillingRules.recipient(log)
                                ))
                        ) {
                            throw IllegalArgumentException(
                                "Positionen benötigen offene Arbeiten des Rechnungsempfängers ohne Doppelzuordnung."
                            )
                        }
                        var quantity =
                            LocalBillingRules.state(log, master!!.optJSONArray("invoices"), master)
                        if (
                            ((quantity !== null) &&
                                ((BigDecimal((line!!.get("quantity"))!!.toString()))!!.compareTo(
                                    BigDecimal(quantity!!.getString("remaining_decimal"))
                                ) > 0))
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "INVOICE_SOURCE_QUANTITY_EXCEEDED",
                                "Positionsmenge überschreitet die noch nicht abgerechnete Leistungsmenge. Restmenge neu lesen; Eingaben bleiben erhalten.",
                            )
                        }
                    }
                    var quantity: BigDecimal = BigDecimal((line!!.get("quantity"))!!.toString())
                    var price: BigDecimal = BigDecimal((line!!.get("price"))!!.toString())
                    var discount: BigDecimal =
                        BigDecimal(
                            (if ((line!!.opt("discount") === null)) "0"
                            else (line!!.get("discount"))!!.toString())
                        )
                    var net =
                        ((quantity!!.multiply(price))!!.multiply(
                                BigDecimal.ONE.subtract(discount!!.divide(BigDecimal("100")))
                            ))!!
                            .setScale(2, RoundingMode.HALF_UP)
                    var rate: BigDecimal = BigDecimal((line!!.get("tax_rate"))!!.toString())
                    if ((!taxMode!!.equals("standard") && (rate!!.signum() != 0))) {
                        throw IllegalArgumentException(
                            "Steuermodus muss zentral bestätigt sein; Kleinunternehmer weisen keine Steuer aus."
                        )
                    }
                    var tax =
                        ((net!!.multiply(rate))!!.divide(BigDecimal("100")))!!.setScale(
                            2,
                            RoundingMode.HALF_UP,
                        )
                    (line!!.put("net", net!!.toDouble()))!!.put("tax", tax!!.toDouble())
                    total = total!!.add(net)
                    totalTax = totalTax!!.add(tax)

                    i++
                }
            }
            if (("cancellation")!!.equals(p!!.optString("document_type"))) {
                if ((p!!.optString("correction_of"))!!.isEmpty()) {
                    throw IllegalArgumentException("Originalbezug des Stornos fehlt.")
                }
                total = total!!.negate()
                totalTax = totalTax!!.negate()
                run {
                    var i: Int = 0
                    while ((i < positions!!.length())) {
                        var line = positions!!.getJSONObject(i)
                        (line!!.put("net", -line!!.getDouble("net")))!!.put(
                            "tax",
                            -line!!.getDouble("tax"),
                        )

                        i++
                    }
                }
            }
            if (!p!!.has("document_type")) {
                p!!.put("document_type", "invoice")
            }
            for (key in
                (arrayOf("correction_of", "correction_reason", "header_text", "footer_text"))!!) {
                if (!p!!.has(key)) {
                    p!!.put(key, "")
                }
            }
            (((((((p!!.put(
                                            "domestic_service_confirmed",
                                            p!!.optBoolean("domestic_service_confirmed", false),
                                        ))!!
                                        .put("payment_days", days))!!
                                    .put("worklog_ids", JSONArray(used)))!!
                                .put("status", "Entwurf"))!!
                            .put("amount", (total!!.add(totalTax))!!.toDouble()))!!
                        .put("net_total", total!!.toDouble()))!!
                    .put("tax_total", totalTax!!.toDouble()))!!
                .put("gross_total", (total!!.add(totalTax))!!.toDouble())
            if (LocalAuthorityRules.ENGINE.equals(master!!.optString("processing_engine"))) {
                p!!.put("money_minor", LocalAuthorityRules.exactTotals(p))
            }
        }
    }
}
