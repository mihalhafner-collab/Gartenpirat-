package at.hausgarten.service

import java.util.*
import org.json.*

internal class OfferJournalRules {
    companion object {
        @JvmStatic
        fun handles(kind: String): Boolean {
            return (Arrays.asList(
                    "offer_finalize",
                    "offer_convert",
                    "offer_validate",
                    "offer_share",
                    "offer_delivery",
                ))!!
                .contains(kind)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun apply(s: JSONObject, a: JSONObject): Unit {
            var p = a!!.getJSONObject("payload")
            var old = PixelMcpWriteTools.find(s!!.getJSONArray("offers"), p!!.getString("id"))
            if (
                !(PixelMcpWriteTools.hash(LocalJournalRules.clean(old)))!!.equals(
                    p!!.getString("expected_record_hash")
                )
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Angebot wurde inzwischen verändert.",
                )
            }
            if (
                (!(Arrays.asList("offer_validate", "offer_share"))!!.contains(
                    a!!.getString("kind")
                ) && !p!!.optBoolean("confirmed"))
            ) {
                throw IllegalArgumentException("Ausdrückliche Bestätigung erforderlich.")
            }
            var next: JSONObject = JSONObject(old!!.toString())
            var kind = a!!.getString("kind")
            if (kind!!.equals("offer_validate")) {
                OfferRules.normalize(next, old, s)
                var validation = OfferRules.validate(next, s)
                if (!validation!!.getBoolean("valid")) {
                    throw PixelMcpWriteTools.Rejected(
                        "DOCUMENT_VALIDATION_FAILED",
                        (validation!!.getJSONArray("errors"))!!.join("; "),
                    )
                }
                ((next!!.put("status", "validated"))!!.put(
                        "validated_at",
                        a!!.getString("created_at"),
                    ))!!
                    .put(
                        "validation_context_hash",
                        PixelMcpWriteTools.hash(OfferTools.context(old, s)),
                    )
            } else {
                if ((kind!!.equals("offer_share") || kind!!.equals("offer_delivery"))) {
                    if (
                        !(Arrays.asList("finalized", "accepted"))!!.contains(
                            old!!.optString("status")
                        )
                    ) {
                        throw IllegalArgumentException("Dokument zuerst finalisieren.")
                    }
                    if ((kind!!.equals("offer_delivery") || p!!.has("recipient_email"))) {
                        TestDataRules.noDelivery(old, s)
                    }
                    if (kind!!.equals("offer_share")) {
                        if (!(old!!.optString("delivery_status"))!!.equals("sent")) {
                            next!!.put("delivery_status", "share_prepared")
                        }
                        next!!.put("share_prepared_at", a!!.getString("created_at"))
                        if (p!!.has("recipient_email")) {
                            next!!.put("delivery_recipient", p!!.getString("recipient_email"))
                        }
                    } else {
                        var receipt = p!!.getJSONObject("receipt")
                        if (
                            (((!("pixel_gmail_readback")!!.equals(
                                receipt!!.optString("verified_by")
                            ) ||
                                !(old!!.optString("pdf_sha256"))!!.equals(
                                    receipt!!.optString("pdf_sha256")
                                )) ||
                                !(old!!.optString("delivery_recipient"))!!.equals(
                                    receipt!!.optString("recipient_email")
                                )) || (receipt!!.optString("message_id"))!!.isEmpty())
                        ) {
                            throw IllegalArgumentException("Geprüfter Versandbeleg fehlt.")
                        }
                        ((next!!.put("delivery_status", "sent"))!!.put(
                                "delivery_receipt",
                                receipt,
                            ))!!
                            .put("sent_at", receipt!!.getString("sent_at"))
                    }
                } else {
                    if (kind!!.equals("offer_finalize")) {
                        if (
                            !(PixelMcpWriteTools.hash(OfferTools.context(old, s)))!!.equals(
                                p!!.getString("expected_context_hash")
                            )
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Kunde, Aussteller oder Preise wurden geändert. Erneut validieren.",
                            )
                        }
                        OfferRules.normalize(next, old, s)
                        var v = OfferRules.validate(next, s)
                        if (!v!!.getBoolean("valid")) {
                            throw IllegalArgumentException(
                                (v!!.getJSONArray("errors"))!!.join("; ")
                            )
                        }
                        next!!.put(
                            "offer_number",
                            ((((((if (next!!.optBoolean("test_record")) "TEST-" else "") +
                                    (if (
                                        (next!!.optString("document_type"))!!.startsWith(
                                            "estimate_"
                                        )
                                    )
                                        "KV-"
                                    else "ANG-")) +
                                    (next!!.getString("issue_date"))!!.substring(0, 4)))
                                .toString() + "-") +
                                ((PixelMcpWriteTools.hash(next!!.getString("id")))!!.substring(
                                        0,
                                        16,
                                    ))!!
                                    .uppercase(Locale.ROOT)),
                        )
                        run {
                            var i: Int = 0
                            while ((i < (s!!.getJSONArray("offers"))!!.length())) {
                                var other = (s!!.getJSONArray("offers"))!!.getJSONObject(i)
                                if (
                                    (!(other!!.getString("id"))!!.equals(next!!.getString("id")) &&
                                        (next!!.getString("offer_number"))!!.equals(
                                            other!!.optString("offer_number")
                                        ))
                                ) {
                                    throw IllegalArgumentException(
                                        "Angebotsnummer ist bereits vergeben."
                                    )
                                }

                                i++
                            }
                        }
                        (((next!!.put("status", "finalized"))!!.put(
                                    "finalized_at",
                                    a!!.getString("created_at"),
                                ))!!
                                .put(
                                    "issuer",
                                    (OfferTools.context(old, s))!!.getJSONObject(
                                        "business_settings"
                                    ),
                                ))!!
                            .put(
                                "recipient",
                                (OfferTools.context(old, s))!!.getJSONObject("recipient"),
                            )
                        for (key in
                            (arrayOf(
                                "document_id",
                                "pdf_storage_name",
                                "pdf_sha256",
                                "pdf_byte_size",
                                "pdf_filename",
                            ))!!) {
                            if (p!!.has(key)) {
                                next!!.put(key, p!!.get(key))
                            }
                        }
                    } else {
                        if (!(old!!.optString("status"))!!.equals("finalized")) {
                            throw IllegalArgumentException(
                                "Nur ein finalisiertes, ausdrücklich angenommenes Angebot übernehmen."
                            )
                        }
                        if (old!!.optBoolean("test_record")) {
                            throw PixelMcpWriteTools.Rejected(
                                "TEST_CONVERSION_BLOCKED",
                                "Testdokumente nicht in produktive Aufträge übernehmen.",
                            )
                        }
                        var id = p!!.optString("existing_order_id")
                        var order: JSONObject? = null
                        if (!id!!.isEmpty()) {
                            order =
                                JSONObject(
                                    (PixelMcpWriteTools.find(s!!.getJSONArray("orders"), id))!!
                                        .toString()
                                )
                            if (
                                !(old!!.getString("customer_id"))!!.equals(
                                    order!!.getString("customer_id")
                                )
                            ) {
                                throw IllegalArgumentException(
                                    "Angebot und Auftrag gehören zu verschiedenen Kunden."
                                )
                            }
                            if (
                                !(OwnerJournalRules.version(order))!!.equals(
                                    p!!.optString("expected_order_hash")
                                )
                            ) {
                                throw PixelMcpWriteTools.Rejected(
                                    "WRITE_CONFLICT",
                                    "Auftrag wurde geändert.",
                                )
                            }
                        } else {
                            id =
                                ("ord_offer_" +
                                    (PixelMcpWriteTools.hash(a!!.getString("client_id")))!!
                                        .substring(0, 32))
                            if (
                                (OwnerJournalRules.findOrNull(s!!.getJSONArray("orders"), id) !==
                                    null)
                            ) {
                                throw IllegalArgumentException("Auftrags-ID ist bereits belegt.")
                            }
                            var c =
                                PixelMcpWriteTools.find(
                                    s!!.getJSONArray("customers"),
                                    old!!.getString("customer_id"),
                                )
                            order =
                                (((((((JSONObject())!!.put("id", id))!!.put(
                                                        "customer_id",
                                                        old!!.getString("customer_id"),
                                                    ))!!
                                                    .put(
                                                        "billing_customer_id",
                                                        (if (
                                                            (old!!.optString(
                                                                    "billing_customer_id"
                                                                ))!!
                                                                .isEmpty()
                                                        )
                                                            old!!.getString("customer_id")
                                                        else old!!.getString("billing_customer_id")),
                                                    ))!!
                                                .put("title", old!!.getString("title")))!!
                                            .put(
                                                "location",
                                                (if ((old!!.optString("location"))!!.isEmpty())
                                                    (((((((c!!.optString("street")).toString() +
                                                                ", ") +
                                                                c!!.optString("postal_code")))
                                                            .toString() + " ") +
                                                            c!!.optString("city")))!!
                                                        .trim()
                                                else old!!.getString("location")),
                                            ))!!
                                        .put("status", "Geplant"))!!
                                    .put("created_at", a!!.getString("created_at"))
                        }
                        var refs = order!!.optJSONArray("offer_refs")
                        if ((refs === null)) {
                            refs = JSONArray()
                        }
                        run {
                            var i: Int = 0
                            while ((i < refs!!.length())) {
                                if (
                                    (old!!.getString("id"))!!.equals(
                                        (refs!!.getJSONObject(i))!!.optString("offer_id")
                                    )
                                ) {
                                    throw IllegalArgumentException(
                                        "Angebot ist bereits mit dem Auftrag verbunden."
                                    )
                                }

                                i++
                            }
                        }
                        refs!!.put(
                            ((((JSONObject())!!.put("offer_id", old!!.getString("id")))!!.put(
                                        "accepted_record_hash",
                                        PixelMcpWriteTools.hash(LocalJournalRules.clean(old)),
                                    ))!!
                                    .put("accepted_at", a!!.getString("created_at")))!!
                                .put(
                                    "role",
                                    (if ((order!!.optString("offer_id"))!!.isEmpty()) "main"
                                    else "addendum"),
                                )
                        )
                        order!!.put("offer_refs", refs)
                        if ((order!!.optString("offer_id"))!!.isEmpty()) {
                            order!!.put("offer_id", old!!.getString("id"))
                        }
                        (order!!.put(
                                "description",
                                (((((((order!!.optString("description")).toString() + "\n") +
                                            old!!.getString("offer_number")))
                                        .toString() + "\n") +
                                        OfferRules.positionText(old!!.getJSONArray("positions"))))!!
                                    .trim(),
                            ))!!
                            .put("updated_at", a!!.getString("created_at"))
                        LocalJournalRules.validateChange("order", order, null, s)
                        OwnerJournalRules.replace(s, "orders", id, order)
                        (next!!.put("status", "accepted"))!!.put("converted_order_id", id)
                    }
                }
            }
            next!!.put("updated_at", a!!.getString("created_at"))
            OwnerJournalRules.replace(s, "offers", old!!.getString("id"), next)
        }
    }
}
