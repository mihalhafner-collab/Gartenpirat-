package at.hausgarten.service

import java.time.*
import java.util.*
import org.json.*

/** Acceptance/decline and conversion checks share the existing owner request transaction. */
internal class OfferDecisionService {
    private val repository: OwnerRepository
    private val clock: Clock

    constructor(repository: OwnerRepository) : this(repository, Clock.systemUTC()) {}

    constructor(repository: OwnerRepository, clock: Clock) {
        this.repository = repository
        this.clock = clock
    }

    private @Throws(Exception::class) fun current(id: String): JSONObject? {
        var raw =
            repository!!.ownerValue(
                (((OfferDecisionRules.PREFIX).toString() + "current:") + id),
                "",
            )
        if (raw!!.isEmpty()) {
            return null
        }
        var value: JSONObject = JSONObject(raw)
        OfferDecisionRules.version(value)
        return value
    }

    @Throws(Exception::class)
    fun query(name: String, args: JSONObject): JSONObject {
        if (!name!!.equals("get_offer_decision_history")) {
            throw IllegalArgumentException("Unbekannte Angebotsentscheidung.")
        }
        var id = args!!.getString("offer_id")
        var offer = PixelMcpWriteTools.find((repository!!.effective())!!.getJSONArray("offers"), id)
        var rows: JSONArray =
            JSONArray(
                repository!!.ownerValue(
                    (((OfferDecisionRules.PREFIX).toString() + "history:") + id),
                    "[]",
                )
            )
        var items: JSONArray = JSONArray()
        var offset: Int = RevierRules.integer(args, "offset", 0, 1000000, 0)
        var limit: Int = RevierRules.integer(args, "limit", 1, 100, 50)
        run {
            var i: Int = ((rows!!.length() - 1) - offset)
            while (((i >= 0) && (items!!.length() < limit))) {
                items!!.put(OfferDecisionRules.publicDecision(rows!!.getJSONObject(i)))

                i--
            }
        }
        var decision = current(id)
        var result: JSONObject =
            (((((((JSONObject())!!.put("formatVersion", 1))!!.put("offer_id", id))!!.put(
                                "history",
                                items,
                            ))!!
                            .put("items", items))!!
                        .put("total", rows!!.length()))!!
                    .put("record_hash", PixelMcpWriteTools.hash(DriveJournalRules.clean(offer))))!!
                .put("document_status", offer!!.optString("status"))
        if ((decision !== null)) {
            var map: JSONObject = (JSONObject())!!.put(id, decision)
            var shown = OfferDecisionRules.matching(offer, map)
            (result!!.put("decision", shown))!!.put(
                "decision_status",
                shown!!.getString("decision"),
            )
        } else {
            result!!.put("decision_status", "not_recorded")
        }
        var revision =
            repository!!.ownerValue(
                (((OfferDecisionRules.PREFIX).toString() + "revision:") + id),
                "",
            )
        if (!revision!!.isEmpty()) {
            result!!.put("revision", JSONObject(revision))
        }
        return result
    }

    @Throws(Exception::class)
    fun perform(name: String, args: JSONObject, request: String): JSONObject {
        if (!(Arrays.asList("record_offer_acceptance", "decline_offer"))!!.contains(name)) {
            throw IllegalArgumentException("Unbekannte Angebotsentscheidung.")
        }
        var requestId = RevierRules.text(args, "request_id", 128, true)
        if ((requestId!!.length < 8)) {
            throw PixelMcpWriteTools.Rejected(
                "INVALID_INPUT",
                "Stabile Anfragekennung erforderlich.",
            )
        }
        var fingerprint =
            PixelMcpWriteTools.hash(((JSONObject())!!.put("operation", name))!!.put("args", args))
        var receipt =
            (((OfferDecisionRules.PREFIX).toString() + "request:") +
                PixelMcpWriteTools.hash(requestId))
        var previous = repository!!.ownerValue(receipt, "")
        if (!previous!!.isEmpty()) {
            var saved: JSONObject = JSONObject(previous)
            if (!fingerprint!!.equals(saved!!.getString("fingerprint"))) {
                throw PixelMcpWriteTools.Rejected(
                    "REPLAY_MISMATCH",
                    "Diese Anfragekennung gehört zu anderen Entscheidungsangaben.",
                )
            }
            return (JSONObject((saved!!.getJSONObject("result"))!!.toString()))!!.put(
                "replayed",
                true,
            )
        }
        var id = RevierRules.id(args, "offer_id", true)
        var offer = PixelMcpWriteTools.find((repository!!.effective())!!.getJSONArray("offers"), id)
        if (
            !(PixelMcpWriteTools.hash(DriveJournalRules.clean(offer)))!!.equals(
                args!!.getString("expected_record_hash")
            )
        ) {
            throw PixelMcpWriteTools.Rejected(
                "WRITE_CONFLICT",
                "Angebot wurde geändert. Entscheiden Sie anhand der aktuellen finalisierten Version.",
            )
        }
        OfferDecisionRules.requireFinal(offer)
        repository!!.readDocument(offer)
        var existing = current(id)
        var version = OfferDecisionRules.contentVersion(offer)
        if ((existing !== null)) {
            throw PixelMcpWriteTools.Rejected(
                (if ((existing!!.getString("content_version"))!!.equals(version))
                    "OFFER_DECISION_ALREADY_RECORDED"
                else "OFFER_FINAL_VERSION_CHANGED"),
                "Diese Angebotsversion hat bereits eine Entscheidung oder einen widersprüchlichen Dokumentstand. Für Änderungen eine neue Version anlegen; alte Entscheidung bleibt erhalten.",
            )
        }
        var acceptance: Boolean = name!!.equals("record_offer_acceptance")
        if ((!acceptance && (offer!!.optString("status"))!!.equals("accepted"))) {
            throw PixelMcpWriteTools.Rejected(
                "OFFER_ALREADY_CONVERTED",
                "Angenommenen Auftrag nicht durch eine Ablehnungsmarkierung aufheben. Bestehenden Vorgang gesondert klären.",
            )
        }
        var stamp = (clock!!.instant())!!.toString()
        var decision: JSONObject =
            ((((((((JSONObject())!!.put("formatVersion", 1))!!.put(
                                        "decision_id",
                                        RevierRules.newId("offer_decision", requestId),
                                    ))!!
                                    .put("offer_id", id))!!
                                .put("content_version", version))!!
                            .put("decision", (if (acceptance) "accepted" else "declined")))!!
                        .put("recorded_at", stamp))!!
                    .put("pdf_sha256", offer!!.getString("pdf_sha256")))!!
                .put("request_id", requestId)
        if (acceptance) {
            var at = RevierRules.instant(RevierRules.text(args, "accepted_at", 80, true))
            var evidence = RevierRules.text(args, "evidence", 10000, true)
            if ((Instant.parse(at))!!.isAfter((clock!!.instant())!!.plusSeconds(120))) {
                throw PixelMcpWriteTools.Rejected(
                    "ACCEPTANCE_TIME_REVIEW_REQUIRED",
                    "Annahmezeit liegt in der Zukunft. Zeitpunkt und Geräteuhr prüfen.",
                )
            }
            (decision!!.put("accepted_at", at))!!.put("evidence", evidence)
        } else {
            decision!!.put("reason", RevierRules.text(args, "reason", 10000, false))
        }
        RevierRules.bounded(decision)
        var history: JSONArray =
            JSONArray(
                repository!!.ownerValue(
                    (((OfferDecisionRules.PREFIX).toString() + "history:") + id),
                    "[]",
                )
            )
        history!!.put(decision)
        repository!!.putOwnerValue(
            (((OfferDecisionRules.PREFIX).toString() + "history:") + id),
            history!!.toString(),
        )
        repository!!.putOwnerValue(
            (((OfferDecisionRules.PREFIX).toString() + "current:") + id),
            decision!!.toString(),
        )
        var result: JSONObject =
            ((((((JSONObject())!!.put("offer_id", id))!!.put(
                                "decision",
                                OfferDecisionRules.publicDecision(decision),
                            ))!!
                            .put("local_state", "local_saved"))!!
                        .put("cloud_confirmed", false))!!
                    .put("invoice_created", false))!!
                .put("replayed", false)
        repository!!.putOwnerValue(
            receipt,
            (((JSONObject())!!.put("fingerprint", fingerprint))!!.put("result", result))!!
                .toString(),
        )
        return result
    }

    @Throws(Exception::class)
    fun requireConversionDecision(offer: JSONObject): Unit {
        var decision = current(offer!!.getString("id"))
        if ((decision !== null)) {
            if (
                !(decision!!.getString("content_version"))!!.equals(
                    OfferDecisionRules.contentVersion(offer)
                )
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "OFFER_FINAL_VERSION_CHANGED",
                    "Annahmenachweis gehört zu einer anderen Dokumentversion. Auftrag nicht angelegt.",
                )
            }
            if (!(decision!!.getString("decision"))!!.equals("accepted")) {
                throw PixelMcpWriteTools.Rejected(
                    "OFFER_DECLINED",
                    "Abgelehntes Angebot erzeugt keinen Auftrag. Neue Angebotsversion bei Überarbeitung anlegen.",
                )
            }
        }
        if ((OfferDecisionRules.local(repository!!.state()) && (decision === null))) {
            throw PixelMcpWriteTools.Rejected(
                "OFFER_ACCEPTANCE_EVIDENCE_REQUIRED",
                "Annahmezeit und nachvollziehbaren Nachweis für genau diese Version erfassen, bevor ein Auftrag angelegt wird.",
            )
        }
    }

    @Throws(Exception::class)
    fun linkRevision(sourceId: String, targetId: String, request: String): Unit {
        var source =
            PixelMcpWriteTools.find((repository!!.effective())!!.getJSONArray("offers"), sourceId)
        var revision: JSONObject =
            ((((((JSONObject())!!.put("formatVersion", 1))!!.put("offer_id", targetId))!!.put(
                            "parent_offer_id",
                            sourceId,
                        ))!!
                        .put("parent_content_version", OfferDecisionRules.contentVersion(source)))!!
                    .put("created_at", (clock!!.instant())!!.toString()))!!
                .put("request_id", request)
        repository!!.putOwnerValue(
            (((OfferDecisionRules.PREFIX).toString() + "revision:") + targetId),
            revision!!.toString(),
        )
    }
}
