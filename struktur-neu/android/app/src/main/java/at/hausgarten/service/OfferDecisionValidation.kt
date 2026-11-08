package at.hausgarten.service

import java.time.Instant
import java.util.*
import org.json.*

/** Checks captured decision evidence and replay records before backup certification or restore. */
internal object OfferDecisionValidation {
    private @JvmStatic fun invalid(): PixelMcpWriteTools.Rejected {
        return PixelMcpWriteTools.Rejected(
            "OFFER_DECISION_RELATION_INVALID",
            "Angebotsentscheidung, Dokumentversion oder Annahmenachweis passen nicht zusammen. Bestand unverändert lassen und Sicherung prüfen.",
        )
    }

    private @JvmStatic @Throws(Exception::class) fun require(value: Boolean): Unit {
        if (!value) {
            throw invalid()
        }
    }

    private @JvmStatic @Throws(Exception::class) fun offer(
        state: JSONObject,
        id: String,
    ): JSONObject {
        val offers = state!!.optJSONArray("offers")
        run {
            var i: Int = 0
            while (((offers !== null) && (i < offers!!.length()))) {
                if (id!!.equals((offers!!.getJSONObject(i))!!.optString("id"))) {
                    return offers!!.getJSONObject(i)
                }

                i++
            }
        }
        throw invalid()
    }

    private @JvmStatic @Throws(Exception::class) fun hash(value: Any?): String {
        return PixelMcpWriteTools.hash(value)
    }

    private @JvmStatic @Throws(Exception::class) fun text(
        value: JSONObject,
        key: String,
        max: Int,
        required: Boolean,
    ): String {
        val result = RevierRules.text(value, key, max, required)
        if (required) {
            require(!result!!.isEmpty())
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validate(repository: RevierRepository, state: JSONObject): JSONObject {
        var current: MutableMap<String, JSONObject> = TreeMap()
        val revisions: MutableMap<String, JSONObject> = TreeMap()
        val receipts: MutableMap<String, JSONObject> = TreeMap()
        val histories: MutableMap<String, JSONArray> = TreeMap()
        val entries = repository!!.rawEntries(OfferDecisionRules.PREFIX)
        val keys: MutableSet<String> = HashSet()
        val prefix = OfferDecisionRules.PREFIX
        try {
            run {
                var i: Int = 0
                while ((i < entries!!.length())) {
                    val entry = entries!!.getJSONObject(i)
                    val key = entry!!.getString("key")
                    val raw = entry!!.getString("value")
                    require(keys!!.add(key))
                    if (key!!.startsWith((prefix.toString() + "current:"))) {
                        var id = key!!.substring(((prefix.toString() + "current:"))!!.length)
                        var value: JSONObject = JSONObject(raw)
                        require(id!!.equals(value!!.optString("offer_id")))
                        OfferDecisionRules.version(value)
                        current!!.put(id, value)
                    } else {
                        if (key!!.startsWith((prefix.toString() + "history:"))) {
                            var id = key!!.substring(((prefix.toString() + "history:"))!!.length)
                            histories!!.put(id, JSONArray(raw))
                        } else {
                            if (key!!.startsWith((prefix.toString() + "revision:"))) {
                                var id =
                                    key!!.substring(((prefix.toString() + "revision:"))!!.length)
                                var value: JSONObject = JSONObject(raw)
                                require(id!!.equals(value!!.optString("offer_id")))
                                OfferDecisionRules.version(value)
                                revisions!!.put(id, value)
                            } else {
                                if (key!!.startsWith((prefix.toString() + "request:"))) {
                                    receipts!!.put(
                                        key!!.substring(
                                            ((prefix.toString() + "request:"))!!.length
                                        ),
                                        JSONObject(raw),
                                    )
                                } else {
                                    throw invalid()
                                }
                            }
                        }
                    }

                    i++
                }
            }
            require((current!!.keys)!!.equals(histories!!.keys))
            val decisions: MutableSet<String> = HashSet()
            val requestIds: MutableSet<String> = HashSet()
            for (item in (current!!.entries)!!) {
                var id = item!!.key
                val decision = item!!.value
                val document = offer(state, id)
                OfferDecisionRules.requireFinal(document)
                val request = text(decision, "request_id", 128, true)
                val decisionId = RevierRules.id(decision, "decision_id", true)
                require(
                    (((((request!!.length >= 8) && request!!.matches(Regex("[A-Za-z0-9_.:-]+"))) &&
                        requestIds!!.add(request)) && decisions!!.add(decisionId)) &&
                        decisionId!!.equals(RevierRules.newId("offer_decision", request)))
                )
                require(
                    (((text(decision, "content_version", 64, true))!!.matches(
                        Regex("[0-9a-f]{64}")
                    ) &&
                        (decision!!.getString("content_version"))!!.equals(
                            OfferDecisionRules.contentVersion(document)
                        )) &&
                        (decision!!.getString("pdf_sha256"))!!.equals(
                            document!!.getString("pdf_sha256")
                        ))
                )
                Instant.parse(text(decision, "recorded_at", 80, true))
                val status = text(decision, "decision", 30, true)
                require((Arrays.asList("accepted", "declined"))!!.contains(status))
                if (status!!.equals("accepted")) {
                    Instant.parse(text(decision, "accepted_at", 80, true))
                    text(decision, "evidence", 10000, true)
                } else {
                    require(!(document!!.optString("status"))!!.equals("accepted"))
                    text(decision, "reason", 10000, false)
                }
                val history = histories!!.get(id)
                require((history!!.length() == 1))
                val recorded = history!!.getJSONObject(0)
                OfferDecisionRules.version(recorded)
                require((hash(recorded))!!.equals(hash(decision)))
                val receipt = receipts!!.get(hash(request))
                require((receipt !== null))
                validateReceipt(receipt!!, decision, id)
            }
            require((receipts!!.size == current!!.size))
            for (item in (revisions!!.entries)!!) {
                var id = item!!.key
                val revision = item!!.value
                val child = offer(state, id)
                val parent = RevierRules.id(revision, "parent_offer_id", true)
                require(!parent!!.equals(id))
                var original = offer(state, parent)
                val parentVersion = revision!!.getString("parent_content_version")
                require(
                    (parentVersion!!.matches(Regex("[0-9a-f]{64}")) &&
                        (child!!.optString("customer_id"))!!.equals(
                            original!!.optString("customer_id")
                        ))
                )
                if (
                    (Arrays.asList("finalized", "accepted"))!!.contains(
                        original!!.optString("status")
                    )
                ) {
                    require(parentVersion!!.equals(OfferDecisionRules.contentVersion(original)))
                }
                Instant.parse(text(revision, "created_at", 80, true))
                text(revision, "request_id", 200, true)
                val visited: MutableSet<String> = HashSet()
                visited!!.add(id)
                var ancestor = parent
                while (!ancestor!!.isEmpty()) {
                    require(visited!!.add(ancestor))
                    val link = revisions!!.get(ancestor)
                    ancestor = (if ((link === null)) "" else link!!.optString("parent_offer_id"))
                }
            }
            return ((((((JSONObject())!!.put("formatVersion", 1))!!.put("status", "PASS"))!!.put(
                            "decisions",
                            current!!.size,
                        ))!!
                        .put("histories", histories!!.size))!!
                    .put("revisions", revisions!!.size))!!
                .put("receipts", receipts!!.size)
        } catch (rejected: PixelMcpWriteTools.Rejected) {
            throw rejected
        } catch (malformed: Exception) {
            throw invalid()
        }
    }

    private @JvmStatic @Throws(Exception::class) fun validateReceipt(
        receipt: JSONObject,
        decision: JSONObject,
        id: String,
    ): Unit {
        require((receipt!!.optString("fingerprint"))!!.matches(Regex("[0-9a-f]{64}")))
        val result = receipt!!.getJSONObject("result")
        require(
            ((((id!!.equals(result!!.optString("offer_id")) &&
                !result!!.optBoolean("invoice_created", true)) &&
                !result!!.optBoolean("cloud_confirmed", true)) &&
                (result!!.optString("local_state"))!!.equals("local_saved")) &&
                (hash(result!!.getJSONObject("decision")))!!.equals(
                    hash(OfferDecisionRules.publicDecision(decision))
                ))
        )
    }
}
