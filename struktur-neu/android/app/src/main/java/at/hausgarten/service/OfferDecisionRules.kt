package at.hausgarten.service

import java.time.*
import java.util.*
import org.json.*

/** Decision evidence is separate from immutable document content and provider handoff state. */
internal object OfferDecisionRules {
    @JvmField val PREFIX: String = "offer_decision:v1:"

    @JvmStatic
    @Throws(Exception::class)
    fun version(value: JSONObject): Unit {
        var version = value!!.opt("formatVersion")
        if ((!(version is Number) || !(version!!.toString())!!.equals("1"))) {
            throw PixelMcpWriteTools.Rejected(
                "OFFER_DECISION_VERSION_UNSUPPORTED",
                "Neuere Angebotsentscheidungen benötigen eine passende Appversion.",
            )
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun contentVersion(offer: JSONObject): String {
        var fixed: JSONObject = JSONObject()
        for (field in (OfferRules.FIELDS)!!) {
            if (offer!!.has(field)) {
                fixed!!.put(field, offer!!.get(field))
            }
        }
        for (field in
            (arrayOf(
                "id",
                "offer_number",
                "finalized_at",
                "issuer",
                "recipient",
                "pdf_sha256",
                "pdf_byte_size",
                "document_id",
                "money_minor",
            ))!!) {
            if (offer!!.has(field)) {
                fixed!!.put(field, offer!!.get(field))
            }
        }
        return PixelMcpWriteTools.hash(fixed)
    }

    @JvmStatic
    fun local(state: JSONObject): Boolean {
        var authority = state!!.optJSONObject("local_authority")
        return ((authority !== null) &&
            (Arrays.asList("LOCAL_ACTIVE", "RESTORE_REVIEW"))!!.contains(
                authority!!.optString("mode")
            ))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun publicDecision(decision: JSONObject): JSONObject {
        var value: JSONObject = JSONObject()
        for (key in
            (arrayOf(
                "formatVersion",
                "decision_id",
                "offer_id",
                "content_version",
                "decision",
                "accepted_at",
                "evidence",
                "reason",
                "recorded_at",
                "pdf_sha256",
                "request_id",
            ))!!) {
            if (decision!!.has(key)) {
                value!!.put(key, decision!!.get(key))
            }
        }
        value!!.put("decision_hash", PixelMcpWriteTools.hash(decision))
        return value
    }

    @JvmStatic
    @Throws(Exception::class)
    fun matching(offer: JSONObject, decisions: JSONObject?): JSONObject? {
        if (
            ((decisions === null) || (decisions!!.optJSONObject(offer!!.optString("id")) === null))
        ) {
            return null
        }
        var decision = decisions!!.getJSONObject(offer!!.getString("id"))
        version(decision)
        if (!(decision!!.optString("content_version"))!!.equals(contentVersion(offer))) {
            return ((((JSONObject())!!.put("formatVersion", 1))!!.put(
                        "offer_id",
                        offer!!.getString("id"),
                    ))!!
                    .put("decision", "review_required"))!!
                .put("reason", "OFFER_DOCUMENT_VERSION_CHANGED")
        }
        return publicDecision(decision)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decorateQuery(name: String, result: JSONObject, effectiveState: JSONObject): JSONObject {
        var decisions = effectiveState!!.optJSONObject("offer_decisions")
        if (((decisions === null) || (decisions!!.length() == 0))) {
            return result
        }
        if ((name!!.equals("get_offer") || name!!.equals("validate_offer"))) {
            var offer = result!!.optJSONObject("offer")
            if ((offer !== null)) {
                var decision = matching(offer, decisions)
                if ((decision !== null)) {
                    (result!!.put("decision", decision))!!.put(
                        "decision_status",
                        decision!!.getString("decision"),
                    )
                }
            }
        }
        if (name!!.equals("list_offers")) {
            var visible: JSONObject = JSONObject()
            var rows = result!!.optJSONArray("offers")
            run {
                var i: Int = 0
                while (((rows !== null) && (i < rows!!.length()))) {
                    var wrapper = rows!!.getJSONObject(i)
                    var offer = wrapper!!.optJSONObject("offer")
                    if ((offer === null)) {
                        run {
                            i++
                            continue
                        }
                    }
                    var decision = matching(offer, decisions)
                    if ((decision !== null)) {
                        wrapper!!.put("decision", decision)
                        visible!!.put(offer!!.getString("id"), decision)
                    }

                    i++
                }
            }
            if ((visible!!.length() > 0)) {
                result!!.put("decisions", visible)
            }
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun requireFinal(offer: JSONObject): JSONObject {
        if (
            ((!(Arrays.asList("finalized", "accepted"))!!.contains(offer!!.optString("status")) ||
                (offer!!.optString("finalized_at"))!!.isEmpty()) ||
                !(offer!!.optString("pdf_sha256"))!!.matches(Regex("[0-9a-f]{64}")))
        ) {
            throw PixelMcpWriteTools.Rejected(
                "OFFER_FINAL_VERSION_REQUIRED",
                "Entschieden werden kann nur eine finalisierte Version mit geprüftem Original-PDF.",
            )
        }
        return offer
    }
}
