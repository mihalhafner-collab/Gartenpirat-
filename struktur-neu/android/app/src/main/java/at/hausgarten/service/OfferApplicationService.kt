package at.hausgarten.service

import java.util.*
import org.json.*

/** Offer use cases. Effects pass through the existing owner repository transaction. */
internal class OfferApplicationService : OwnerCommand {
    private val repository: OwnerRepository

    constructor(repository: OwnerRepository) {
        this.repository = repository
    }

    override @Throws(Exception::class) fun perform(
        name: String,
        args: JSONObject,
        request: String,
    ): JSONObject {
        var all = repository!!.effective()
        var stamp = (java.time.Instant.now())!!.toString()
        when (name) {
            "record_offer_acceptance",
            "decline_offer" -> {
                return (OfferDecisionService(repository))!!.perform(name, args, request)
            }
            "create_and_validate_offer" -> {
                run {
                    var created =
                        perform("create_offer_draft", args, (request.toString() + "_draft"))
                    var id = created!!.getString("offer_id")
                    var row =
                        PixelMcpWriteTools.find(
                            (repository!!.effective())!!.getJSONArray("offers"),
                            id,
                        )
                    var result =
                        perform(
                            "validate_offer_draft",
                            ((JSONObject())!!.put("offer_id", id))!!.put(
                                "expected_record_hash",
                                PixelMcpWriteTools.hash(LocalJournalRules.clean(row)),
                            ),
                            (request.toString() + "_validate"),
                        )
                    return result!!.put("created", true)
                }
            }
            "validate_offer_draft" -> {
                run {
                    var row =
                        PixelMcpWriteTools.find(
                            all!!.getJSONArray("offers"),
                            args!!.getString("offer_id"),
                        )
                    var payload: JSONObject =
                        ((JSONObject())!!.put("id", row!!.getString("id")))!!.put(
                            "expected_record_hash",
                            args!!.getString("expected_record_hash"),
                        )
                    var event: JSONObject =
                        ((((JSONObject())!!.put("client_id", request))!!.put(
                                    "kind",
                                    "offer_validate",
                                ))!!
                                .put("created_at", stamp))!!
                            .put("payload", payload)
                    OfferJournalRules.apply(JSONObject(all!!.toString()), event)
                    return ((repository!!.enqueue("offer_validate", payload, request))!!.put(
                            "offer_id",
                            row!!.getString("id"),
                        ))!!
                        .put("validation_state", "validated")
                }
            }
            "create_offer_draft",
            "update_offer_draft",
            "duplicate_offer" -> {
                run {
                    var old: JSONObject? = null
                    var p: JSONObject? = null
                    if (name!!.equals("update_offer_draft")) {
                        old =
                            PixelMcpWriteTools.find(
                                all!!.getJSONArray("offers"),
                                args!!.getString("offer_id"),
                            )
                        if (
                            !(PixelMcpWriteTools.hash(LocalJournalRules.clean(old)))!!.equals(
                                args!!.getString("expected_record_hash")
                            )
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Angebot wurde geändert.",
                            )
                        }
                        p = LocalJournalRules.clean(old)
                        var changes = args!!.getJSONObject("updates")
                        run {
                            var it = changes!!.keys()
                            while (it!!.hasNext()) {
                                var key = it!!.next()
                                p!!.put(key, changes!!.get(key))
                            }
                        }
                    } else {
                        p = JSONObject()
                        var source =
                            (if (name!!.equals("duplicate_offer"))
                                PixelMcpWriteTools.find(
                                    all!!.getJSONArray("offers"),
                                    args!!.getString("offer_id"),
                                )
                            else args)
                        for (key in (OfferRules.FIELDS)!!) {
                            if (source!!.has(key)) {
                                p!!.put(key, source!!.get(key))
                            }
                        }
                        (((p!!.put(
                                        "id",
                                        ("off_" +
                                            (PixelMcpWriteTools.hash(request))!!.substring(0, 32)),
                                    ))!!
                                    .put("offer_number", ""))!!
                                .put("source", "owner_app_mcp"))!!
                            .put("created_at", stamp)
                    }
                    OfferRules.normalize(p, old, all)
                    if (
                        (name!!.equals("update_offer_draft") &&
                            (args!!.getJSONObject("updates"))!!.optBoolean("binding_confirmed"))
                    ) {
                        (p!!.put("binding_confirmed", true))!!.put(
                            "binding_confirmation_hash",
                            OfferRules.bindingHash(p),
                        )
                        OfferRules.normalize(p, old, all)
                    }
                    ManualChangeRules.prepare("offer", p, old, stamp)
                    var saved =
                        ((repository!!.enqueue("offer", p, request))!!.put(
                                "offer_id",
                                p!!.getString("id"),
                            ))!!
                            .put(
                                "record_hash",
                                PixelMcpWriteTools.hash(
                                    LocalJournalRules.clean(
                                        PixelMcpWriteTools.find(
                                            (repository!!.effective())!!.getJSONArray("offers"),
                                            p!!.getString("id"),
                                        )
                                    )
                                ),
                            )
                    if (
                        (name!!.equals("duplicate_offer") &&
                            OfferDecisionRules.local(repository!!.state()))
                    ) {
                        (OfferDecisionService(repository))!!.linkRevision(
                            args!!.getString("offer_id"),
                            p!!.getString("id"),
                            request,
                        )
                    }
                    return saved
                }
            }
            "create_catalog_entry",
            "update_catalog_entry" -> {
                run {
                    var p: JSONObject = JSONObject()
                    var old: JSONObject? = null
                    if (name!!.equals("update_catalog_entry")) {
                        old =
                            PixelMcpWriteTools.find(
                                all!!.getJSONArray("service_catalog"),
                                args!!.getString("entry_id"),
                            )
                        if (
                            !(PixelMcpWriteTools.hash(old))!!.equals(
                                args!!.getString("expected_record_hash")
                            )
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Baustein wurde geändert.",
                            )
                        }
                        p = JSONObject(old!!.toString())
                    }
                    var changes =
                        (if (name!!.equals("update_catalog_entry")) args!!.getJSONObject("updates")
                        else args)
                    var fields = OfferTools.catalogFields()
                    run {
                        var it = fields!!.keys()
                        while (it!!.hasNext()) {
                            var key = it!!.next()
                            if (changes!!.has(key)) {
                                p!!.put(key, changes!!.get(key))
                            }
                        }
                    }
                    if ((old === null)) {
                        p!!.put(
                            "id",
                            ("svc_" + (PixelMcpWriteTools.hash(request))!!.substring(0, 32)),
                        )
                    }
                    LocalJournalRules.validateTemplate(p)
                    ManualChangeRules.prepare("service_template", p, old, stamp)
                    return (repository!!.enqueue("service_template", p, request))!!.put(
                        "entry_id",
                        p!!.getString("id"),
                    )
                }
            }
            "finalize_offer",
            "convert_offer_to_order" -> {
                run {
                    var existingOffer =
                        PixelMcpWriteTools.find(
                            all!!.getJSONArray("offers"),
                            args!!.getString("offer_id"),
                        )
                    if (
                        ((name!!.equals("convert_offer_to_order") &&
                            (existingOffer!!.optString("status"))!!.equals("accepted")) &&
                            !(existingOffer!!.optString("converted_order_id"))!!.isEmpty())
                    ) {
                        if (
                            !(AppointmentRules.hash(existingOffer))!!.equals(
                                args!!.getString("expected_record_hash")
                            )
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Angebot wurde geändert.",
                            )
                        }
                        return ((((JSONObject())!!.put(
                                        "offer_id",
                                        existingOffer!!.getString("id"),
                                    ))!!
                                    .put(
                                        "order_id",
                                        existingOffer!!.getString("converted_order_id"),
                                    ))!!
                                .put("already_converted", true))!!
                            .put("invoice_created", false)
                    }
                    if (name!!.equals("convert_offer_to_order")) {
                        (OfferDecisionService(repository))!!.requireConversionDecision(
                            existingOffer
                        )
                    }
                    var p: JSONObject =
                        (JSONObject(args!!.toString()))!!.put("id", args!!.getString("offer_id"))
                    p!!.remove("request_id")
                    p!!.remove("offer_id")
                    var kind =
                        (if (name!!.equals("finalize_offer")) "offer_finalize" else "offer_convert")
                    if (!(args!!.optString("existing_order_id"))!!.isEmpty()) {
                        p!!.put(
                            "_expected_order",
                            LocalJournalRules.guard(
                                PixelMcpWriteTools.find(
                                    all!!.getJSONArray("orders"),
                                    args!!.getString("existing_order_id"),
                                ),
                                "order",
                            ),
                        )
                    }
                    var event: JSONObject =
                        ((((JSONObject())!!.put("client_id", request))!!.put("kind", kind))!!.put(
                                "payload",
                                p,
                            ))!!
                            .put("created_at", stamp)
                    var copy: JSONObject = JSONObject(all!!.toString())
                    OfferJournalRules.apply(copy, event)
                    if (kind!!.equals("offer_finalize")) {
                        var finalized =
                            PixelMcpWriteTools.find(
                                copy!!.getJSONArray("offers"),
                                p!!.getString("id"),
                            )
                        var media = repository!!.createDocument(finalized)
                        run {
                            var it = media!!.keys()
                            while (it!!.hasNext()) {
                                var key = it!!.next()
                                p!!.put(key, media!!.get(key))
                            }
                        }
                    }
                    return (((repository!!.enqueue(kind, p, request))!!.put(
                                "offer_id",
                                p!!.getString("id"),
                            ))!!
                            .put(
                                "order_id",
                                (PixelMcpWriteTools.find(
                                        copy!!.getJSONArray("offers"),
                                        p!!.getString("id"),
                                    ))!!
                                    .optString("converted_order_id"),
                            ))!!
                        .put("invoice_created", false)
                }
            }
            "prepare_offer_share",
            "prepare_offer_delivery",
            "confirm_offer_delivery" -> {
                run {
                    var offer =
                        PixelMcpWriteTools.find(
                            all!!.getJSONArray("offers"),
                            args!!.getString("offer_id"),
                        )
                    if (
                        !(PixelMcpWriteTools.hash(LocalJournalRules.clean(offer)))!!.equals(
                            args!!.getString("expected_record_hash")
                        )
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "WRITE_CONFLICT",
                            "Angebot wurde geändert.",
                        )
                    }
                    if (
                        !(Arrays.asList("finalized", "accepted"))!!.contains(
                            offer!!.optString("status")
                        )
                    ) {
                        throw IllegalArgumentException("Dokument zuerst finalisieren.")
                    }
                    repository!!.readDocument(offer)
                    var payload: JSONObject =
                        ((JSONObject())!!.put("id", offer!!.getString("id")))!!.put(
                            "expected_record_hash",
                            args!!.getString("expected_record_hash"),
                        )
                    var kind: String = "offer_share"
                    var receipt: JSONObject? = null
                    if (name!!.equals("prepare_offer_delivery")) {
                        TestDataRules.noDelivery(offer, all)
                        var address =
                            OfferDeliveryClient.oneAddress(args!!.getString("recipient_email"))
                        if (
                            (address!!.isEmpty() ||
                                !address!!.equals(args!!.getString("recipient_email")))
                        ) {
                            throw IllegalArgumentException(
                                "Eine eindeutige ausdrücklich bestätigte Empfängeradresse erforderlich."
                            )
                        }
                        payload!!.put("recipient_email", address)
                    }
                    if (name!!.equals("confirm_offer_delivery")) {
                        TestDataRules.noDelivery(offer, all)
                        receipt = repository!!.verifyDelivery(args!!.getString("message_id"), offer)
                        (payload!!.put("receipt", receipt))!!.put("confirmed", true)
                        kind = "offer_delivery"
                    }
                    var event: JSONObject =
                        ((((JSONObject())!!.put("client_id", request))!!.put("kind", kind))!!.put(
                                "payload",
                                payload,
                            ))!!
                            .put("created_at", stamp)
                    OfferJournalRules.apply(JSONObject(all!!.toString()), event)
                    var out =
                        (((repository!!.enqueue(kind, payload, request))!!.put(
                                    "offer_id",
                                    offer!!.getString("id"),
                                ))!!
                                .put("sent", (receipt !== null)))!!
                            .put(
                                "state",
                                (if ((receipt !== null)) "sent"
                                else
                                    (if (offer!!.optBoolean("test_record")) "test_preview_only"
                                    else
                                        (if (name!!.equals("prepare_offer_delivery"))
                                            "mail_provider_handoff"
                                        else "device_interaction_required"))),
                            )
                    out!!.put(
                        "document",
                        ((((JSONObject())!!.put("resource_type", "offer"))!!.put(
                                    "resource_id",
                                    offer!!.getString("id"),
                                ))!!
                                .put("sha256", offer!!.getString("pdf_sha256")))!!
                            .put(
                                "file_name",
                                offer!!.optString(
                                    "pdf_filename",
                                    ((offer!!.getString("offer_number")).toString() + ".pdf"),
                                ),
                            ),
                    )
                    if (name!!.equals("prepare_offer_delivery")) {
                        ((out!!.put("recipient_email", payload!!.getString("recipient_email")))!!
                                .put(
                                    "subject",
                                    (((OfferRules.label(offer!!.getString("document_type")))
                                        .toString() + " ") + offer!!.getString("offer_number")),
                                ))!!
                            .put(
                                "body",
                                ((((((offer!!.getString("title")).toString() + "\n") +
                                        offer!!.optString("binding_notice")))
                                    .toString() + "\n") + offer!!.optString("payment_terms")),
                            )
                    }
                    if (
                        (name!!.equals("prepare_offer_share") && !offer!!.optBoolean("test_record"))
                    ) {
                        repository!!.putOwnerValue("offer_share_handoff", offer!!.getString("id"))
                    }
                    if ((receipt !== null)) {
                        out!!.put("delivery_receipt", receipt)
                    }
                    return out
                }
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }
}
