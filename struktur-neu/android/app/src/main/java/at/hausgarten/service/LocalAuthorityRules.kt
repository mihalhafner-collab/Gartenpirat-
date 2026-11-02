package at.hausgarten.service

import java.math.*
import java.util.*
import org.json.*

internal class LocalAuthorityRules {
    constructor() {}

    companion object {
        @JvmField val ENGINE: String = "pixel_local_v2"
        @JvmField val CONTRACT: Int = 1

        @JvmStatic
        @Throws(Exception::class)
        fun minor(value: Any?): Long {

            if (((value == null) || (value == JSONObject.NULL))) {
                throw IllegalArgumentException("MONEY_REQUIRED")
            }
            return BigDecimal(value.toString())
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }

        @JvmStatic
        fun decimal(value: Long): String {

            return BigDecimal.valueOf(value, 2).toPlainString()
        }

        @JvmStatic
        @Throws(Exception::class)
        fun exactTotals(invoice: JSONObject): JSONObject {

            var net: Long = 0
            var tax: Long = 0
            var positions = JSONArray(invoice.getJSONArray("positions").toString())
            run {
                var i: Int = 0
                var firstIteration49 = true
                while (true) {
                    if (firstIteration49) firstIteration49 = false
                    else {
                        i++
                    }
                    if (!((i < positions.length()))) break

                    var line = positions.getJSONObject(i)
                    var quantity = BigDecimal(line.get("quantity").toString())
                    var price = BigDecimal(line.get("price").toString())
                    var discount = BigDecimal(line.optString("discount", "0"))
                    var rate = BigDecimal(line.optString("tax_rate", "0"))
                    if (
                        (((((quantity.signum() < 0) || (price.signum() < 0)) ||
                            (discount.signum() < 0)) ||
                            (discount.compareTo(BigDecimal("100")) > 0)) || (rate.signum() < 0))
                    ) {
                        throw IllegalArgumentException("INVALID_DECIMAL_POSITION")
                    }
                    var calculated =
                        quantity
                            .multiply(price)
                            .multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)))
                            .setScale(2, RoundingMode.HALF_UP)
                    var n: Long = minor(calculated)
                    var t: Long =
                        minor(
                            calculated
                                .multiply(rate)
                                .movePointLeft(2)
                                .setScale(2, RoundingMode.HALF_UP)
                        )
                    if (("cancellation" == invoice.optString("document_type"))) {
                        n = Math.negateExact(n)
                        t = Math.negateExact(t)
                    }
                    line
                        .put("quantity_decimal", quantity.toPlainString())
                        .put("unit_price_minor_decimal", price.movePointRight(2).toPlainString())
                        .put("net_minor", n)
                        .put("tax_minor", t)
                    net = Math.addExact(net, n)
                    tax = Math.addExact(tax, t)
                }
            }
            if (
                (((invoice.has("net_total") && (minor(invoice.get("net_total")) != net)) ||
                    (invoice.has("tax_total") && (minor(invoice.get("tax_total")) != tax))) ||
                    (invoice.has("gross_total") &&
                        (minor(invoice.get("gross_total")) != Math.addExact(net, tax))))
            ) {
                throw IllegalArgumentException("INVOICE_TOTAL_MISMATCH")
            }
            return JSONObject()
                .put("formatVersion", 1)
                .put("currency", "EUR")
                .put("positions", positions)
                .put("net_minor", net)
                .put("tax_minor", tax)
                .put("gross_minor", Math.addExact(net, tax))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun paymentBalance(invoiceMinor: Long, payments: JSONArray): JSONObject {

            var allocated: Long = 0
            var ids = HashSet<String>()
            run {
                var i: Int = 0
                var firstIteration50 = true
                while (true) {
                    if (firstIteration50) firstIteration50 = false
                    else {
                        i++
                    }
                    if (!((i < payments.length()))) break

                    var p = payments.getJSONObject(i)
                    if (!(ids.add(p.getString("payment_id")))) {
                        throw IllegalArgumentException("PAYMENT_DUPLICATE_ID")
                    }
                    allocated = Math.addExact(allocated, p.getLong("allocated_minor"))
                }
            }
            var difference: Long = Math.subtractExact(invoiceMinor, allocated)
            return JSONObject()
                .put("invoice_minor", invoiceMinor)
                .put("allocated_minor", allocated)
                .put("remaining_minor", Math.max(0, difference))
                .put("credit_minor", Math.max(0, Math.negateExact(difference)))
                .put("paid", (difference <= 0))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun requireEvidence(proof: JSONObject): Unit {

            if (
                (((!(("1" == java.lang.String.valueOf(proof.opt("formatVersion")))) ||
                    !(proof.optBoolean("external_writers_disabled"))) ||
                    !(proof.optString("evidence_sha256").matches(Regex("[0-9a-f]{64}")))) ||
                    proof.optString("evidence_reference").trim().isEmpty())
            ) {
                throw IllegalArgumentException("LOCAL_AUTHORITY_EVIDENCE_REQUIRED")
            }
            java.time.Instant.parse(proof.getString("checked_at"))
        }

    }
}
