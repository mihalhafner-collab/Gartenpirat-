package at.hausgarten.service

import java.math.*
import java.util.*
import org.json.*

internal class OfferMoney {
    constructor() {}

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun minor(value: Any?): Long {
            if ((value == null) || (value == JSONObject.NULL)) {
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
        fun exactTotals(offer: JSONObject): JSONObject {
            var net: Long = 0
            var tax: Long = 0
            val positions = JSONArray(offer.getJSONArray("positions").toString())
            for (i in 0 until positions.length()) {
                val line = positions.getJSONObject(i)
                val quantity = BigDecimal(line.get("quantity").toString())
                val price = BigDecimal(line.get("price").toString())
                val discount = BigDecimal(line.optString("discount", "0"))
                val rate = BigDecimal(line.optString("tax_rate", "0"))
                if (
                    (((((quantity.signum() < 0) || (price.signum() < 0)) ||
                        (discount.signum() < 0)) || (discount.compareTo(BigDecimal("100")) > 0)) ||
                        (rate.signum() < 0))
                ) {
                    throw IllegalArgumentException("INVALID_DECIMAL_POSITION")
                }
                var calculated =
                    quantity
                        .multiply(price)
                        .multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP)
                val n: Long = minor(calculated)
                var t: Long =
                    minor(
                        calculated.multiply(rate).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
                    )
                line
                    .put("quantity_decimal", quantity.toPlainString())
                    .put("unit_price_minor_decimal", price.movePointRight(2).toPlainString())
                    .put("net_minor", n)
                    .put("tax_minor", t)
                net = Math.addExact(net, n)
                tax = Math.addExact(tax, t)
            }
            if (
                (((offer.has("net_total") && (minor(offer.get("net_total")) != net)) ||
                    (offer.has("tax_total") && (minor(offer.get("tax_total")) != tax))) ||
                    (offer.has("gross_total") &&
                        (minor(offer.get("gross_total")) != Math.addExact(net, tax))))
            ) {
                throw IllegalArgumentException("OFFER_TOTAL_MISMATCH")
            }
            return JSONObject()
                .put("formatVersion", 1)
                .put("currency", "EUR")
                .put("positions", positions)
                .put("net_minor", net)
                .put("tax_minor", tax)
                .put("gross_minor", Math.addExact(net, tax))
        }
    }
}
