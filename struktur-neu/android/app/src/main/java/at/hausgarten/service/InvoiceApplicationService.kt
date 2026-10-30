package at.hausgarten.service

import java.util.*
import org.json.*

/** Invoice use cases. Effects pass through the existing owner repository transaction. */
internal class InvoiceApplicationService : OwnerCommand {
    private val repository: OwnerRepository

    constructor(repository: OwnerRepository) {
        this.repository = repository
    }

    override @Throws(Exception::class) fun perform(
        name: String,
        a: JSONObject,
        request: String,
    ): JSONObject {
        var local = repository!!.localInvoiceAction(name, a, request)
        if ((local !== null)) {
            return local
        }
        var all = repository!!.effective()
        var stamp = (java.time.Instant.now())!!.toString()
        when (name) {
            "request_invoice_issue",
            "create_invoice_correction",
            "update_invoice_payment" -> {
                run {
                    var invoice =
                        PixelMcpWriteTools.find(
                            all!!.getJSONArray("invoices"),
                            a!!.getString("invoice_id"),
                        )
                    var expected =
                        (if (name!!.equals("update_invoice_payment"))
                            OwnerJournalRules.version(invoice)
                        else
                            PixelMcpWriteTools.hash(
                                PixelMcpEntityWriteTools.recordForHash("invoice", invoice)
                            ))
                    if (!expected!!.equals(a!!.getString("expected_record_hash"))) {
                        throw PixelMcpWriteTools.Rejected(
                            "WRITE_CONFLICT",
                            "Rechnung wurde geändert.",
                        )
                    }
                    var p: JSONObject =
                        ((JSONObject(a!!.toString()))!!.put("id", invoice!!.getString("id")))!!.put(
                            "updated_at",
                            stamp,
                        )
                    p!!.remove("request_id")
                    p!!.remove("invoice_id")
                    p!!.put("_record_base", DriveJournalRules.guard(invoice, "invoice"))
                    if (name!!.equals("request_invoice_issue")) {
                        TestDataRules.noDelivery(invoice, all)
                        InvoiceDraftRules.normalize(JSONObject(invoice!!.toString()), invoice, all)
                        run {
                            var i: Int = 0
                            while (
                                (i < ((repository!!.state())!!.getJSONArray("pending"))!!.length())
                            ) {
                                var old =
                                    ((repository!!.state())!!.getJSONArray("pending"))!!
                                        .getJSONObject(i)
                                if (
                                    ((Arrays.asList(
                                            "invoice_issue",
                                            "owner_invoice_issue",
                                            "pixel_invoice_issue_v1",
                                        ))!!
                                        .contains(old!!.optString("kind")) &&
                                        (invoice!!.getString("id"))!!.equals(
                                            (old!!.getJSONObject("payload"))!!.optString("id")
                                        ))
                                ) {
                                    throw IllegalArgumentException("Abschluss bereits vorgemerkt.")
                                }

                                i++
                            }
                        }
                        p!!.put("_expected_invoice", DriveJournalRules.guard(invoice, "invoice"))
                        var works: JSONObject = JSONObject()
                        var links = invoice!!.optJSONArray("worklog_ids")
                        run {
                            var j: Int = 0
                            while (((links !== null) && (j < links!!.length()))) {
                                var linked = links!!.getString(j)
                                works!!.put(
                                    linked,
                                    DriveJournalRules.guard(
                                        PixelMcpWriteTools.find(
                                            all!!.getJSONArray("worklogs"),
                                            linked,
                                        ),
                                        "worklog",
                                    ),
                                )

                                j++
                            }
                        }
                        p!!.put("_expected_worklogs", works)
                        (p!!.put(
                                "_expected_settings_hash",
                                PixelMcpWriteTools.hash(all!!.getJSONObject("business_settings")),
                            ))!!
                            .put(
                                "_expected_recipient_hash",
                                OwnerJournalRules.version(
                                    PixelMcpWriteTools.find(
                                        all!!.getJSONArray("customers"),
                                        invoice!!.getString("customer_id"),
                                    )
                                ),
                            )
                        InvoiceIssueRules.prepare(
                            all,
                            (JSONObject())!!.put("payload", p),
                            java.time.LocalDate.now(java.time.ZoneId.of("Europe/Vienna")),
                        )
                        return (repository!!.enqueue("pixel_invoice_issue_v1", p, request))!!.put(
                            "finalization",
                            "pixel_drive_numbering_pending",
                        )
                    }
                    if (name!!.equals("create_invoice_correction")) {
                        if ((invoice!!.optString("issued_at"))!!.isEmpty()) {
                            throw IllegalArgumentException(
                                "Korrekturen benötigen einen ausgestellten Originalbeleg."
                            )
                        }
                        p!!.put("_expected_invoice", DriveJournalRules.guard(invoice, "invoice"))
                        return ((repository!!.enqueue("invoice_correction", p, request))!!.put(
                                "correction_state",
                                "draft_pending",
                            ))!!
                            .put("requires_issue_confirmation", true)
                    }
                    OwnerJournalRules.apply(
                        JSONObject(all!!.toString()),
                        ((JSONObject())!!.put("kind", "invoice_payment"))!!.put("payload", p),
                        HashMap(),
                        JSONObject(),
                    )
                    return repository!!.enqueue("invoice_payment", p, request)
                }
            }
            "prepare_invoice_share" -> {
                run {
                    var inv =
                        PixelMcpWriteTools.find(
                            all!!.getJSONArray("invoices"),
                            a!!.getString("invoice_id"),
                        )
                    if (
                        ((inv!!.optString("issued_at"))!!.isEmpty() ||
                            ((inv!!.optString("pdf_base64"))!!.isEmpty() &&
                                (inv!!.optString("pdf_storage_name"))!!.isEmpty()))
                    ) {
                        throw IllegalArgumentException(
                            "Original-PDF einer ausgestellten Rechnung fehlt."
                        )
                    }
                    var handoff: JSONObject =
                        ((JSONObject())!!.put("invoice_id", inv!!.getString("id")))!!.put(
                            "channel",
                            a!!.getString("channel"),
                        )
                    repository!!.putOwnerValue("invoice_share_handoff", handoff!!.toString())
                    return ((handoff!!.put("state", "device_interaction_required"))!!.put(
                            "sent",
                            false,
                        ))!!
                        .put("delivered", false)
                }
            }
            else -> {
                throw IllegalArgumentException("Eigentümerfunktion fehlt.")
            }
        }
    }
}
