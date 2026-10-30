package at.hausgarten.service

import java.time.*
import java.util.*
import org.json.*

/** Financial rules shared by MCP request validation and the Pixel Drive processor. */
internal class InvoiceIssueRules {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun prepare(s: JSONObject, a: JSONObject, today: LocalDate): JSONObject {
            var p = a!!.getJSONObject("payload")
            var old = PixelMcpWriteTools.find(s!!.getJSONArray("invoices"), p!!.getString("id"))
            TestDataRules.noDelivery(old, s)
            if (!p!!.optBoolean("confirmed")) {
                throw IllegalArgumentException("Rechnungsabschluss ausdrücklich bestätigen.")
            }
            DriveJournalRules.compare(p!!.getJSONObject("_expected_invoice"), old)
            var settings = s!!.getJSONObject("business_settings")
            var customer =
                PixelMcpWriteTools.find(
                    s!!.getJSONArray("customers"),
                    old!!.getString("customer_id"),
                )
            if (
                (!(PixelMcpWriteTools.hash(settings))!!.equals(
                    p!!.optString("_expected_settings_hash")
                ) ||
                    !(OwnerJournalRules.version(customer))!!.equals(
                        p!!.optString("_expected_recipient_hash")
                    ))
            ) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Aussteller, Nummernkonfiguration oder Empfänger geändert. Abschluss erneut prüfen.",
                )
            }
            var next = DriveJournalRules.clean(old)
            InvoiceDraftRules.normalize(next, old, s)
            for (key in
                (arrayOf(
                    "owner_name",
                    "business_street",
                    "business_postal_code",
                    "business_city",
                ))!!) {
                required(settings, key)
            }
            for (key in (arrayOf("name", "street", "postal_code", "city"))!!) {
                required(customer, key)
            }
            for (key in (arrayOf("invoice_issuer_confirmed", "invoice_numbering_confirmed"))!!) {
                if (!(Arrays.asList("1", "true"))!!.contains(settings!!.optString(key))) {
                    throw IllegalArgumentException(
                        "Aussteller und Nummernkreis müssen bestätigt sein."
                    )
                }
            }
            if (
                (!(Arrays.asList("at", "austria", "österreich"))!!.contains(
                    (customer!!.optString("country"))!!.lowercase(Locale.ROOT)
                ) || !next!!.optBoolean("domestic_service_confirmed"))
            ) {
                throw IllegalArgumentException(
                    "Nur bestätigte gewöhnliche Inlandsleistungen sind unterstützt."
                )
            }
            var date = LocalDate.parse(next!!.getString("invoice_date"))
            var start = LocalDate.parse(next!!.getString("service_start"))
            var end = LocalDate.parse(next!!.getString("service_end"))
            if (((!date!!.equals(today) || end!!.isAfter(today)) || end!!.isBefore(start))) {
                throw IllegalArgumentException(
                    "Rechnungsdatum und Leistungszeitraum erneut prüfen."
                )
            }
            var tax = settings!!.optString("tax_mode")
            if (
                (!(Arrays.asList("small_business", "standard"))!!.contains(tax) ||
                    !tax!!.equals(next!!.optString("tax_mode")))
            ) {
                throw IllegalArgumentException("Bestätigter Steuermodus fehlt.")
            }
            if (tax!!.equals("small_business")) {
                required(settings, "tax_exemption_text")
            } else {
                required(settings, "business_vat_id")
            }
            if ((Math.abs(next!!.getDouble("gross_total")) <= 0)) {
                throw IllegalArgumentException("Rechnungsbetrag muss ungleich null sein.")
            }
            if ((tax!!.equals("standard") && (Math.abs(next!!.getDouble("gross_total")) > 10000))) {
                if (
                    !(Arrays.asList("b2b", "consumer"))!!.contains(
                        next!!.optString("recipient_type")
                    )
                ) {
                    throw IllegalArgumentException("Empfängertyp bestätigen.")
                }
                if ((next!!.optString("recipient_type"))!!.equals("b2b")) {
                    required(next, "recipient_vat_id")
                }
            }
            var expected = p!!.getJSONObject("_expected_worklogs")
            var links = next!!.getJSONArray("worklog_ids")
            if ((expected!!.length() != links!!.length())) {
                throw IllegalArgumentException("Leistungs-Vergleichsstände unvollständig.")
            }
            run {
                var i: Int = 0
                while ((i < links!!.length())) {
                    var id = links!!.getString(i)
                    DriveJournalRules.compare(
                        expected!!.getJSONObject(id),
                        PixelMcpWriteTools.find(s!!.getJSONArray("worklogs"), id),
                    )

                    i++
                }
            }
            run {
                var i: Int = 0
                while ((i < (s!!.getJSONArray("invoices"))!!.length())) {
                    var other = (s!!.getJSONArray("invoices"))!!.getJSONObject(i)
                    if ((other!!.optString("issued_at"))!!.isEmpty()) {
                        run {
                            i++
                            continue
                        }
                    }
                    var used = other!!.optJSONArray("worklog_ids")
                    run {
                        var j: Int = 0
                        while (((used !== null) && (j < used!!.length()))) {
                            if (expected!!.has(used!!.getString(j))) {
                                throw IllegalArgumentException(
                                    "Arbeit bereits in ausgestellter Rechnung enthalten."
                                )
                            }

                            j++
                        }
                    }

                    i++
                }
            }
            if (
                (Arrays.asList("replacement", "cancellation"))!!.contains(
                    next!!.optString("document_type")
                )
            ) {
                var original =
                    PixelMcpWriteTools.find(
                        s!!.getJSONArray("invoices"),
                        next!!.getString("correction_of"),
                    )
                if ((original!!.optString("issued_at"))!!.isEmpty()) {
                    throw IllegalArgumentException("Ausgestellter Originalbeleg fehlt.")
                }
                if ((next!!.optString("document_type"))!!.equals("cancellation")) {
                    if ((original!!.optString("status"))!!.equals("Storniert")) {
                        throw IllegalArgumentException("Original bereits storniert.")
                    }
                    var frozen: JSONArray =
                        JSONArray((original!!.getJSONArray("positions"))!!.toString())
                    var actual: JSONArray =
                        JSONArray((next!!.getJSONArray("positions"))!!.toString())
                    run {
                        var i: Int = 0
                        while ((i < frozen!!.length())) {
                            (frozen!!.getJSONObject(i))!!.put("worklog_id", "")

                            i++
                        }
                    }
                    run {
                        var i: Int = 0
                        while ((i < actual!!.length())) {
                            for (k in (arrayOf("net", "tax"))!!) {
                                (actual!!.getJSONObject(i))!!.put(
                                    k,
                                    Math.abs((actual!!.getJSONObject(i))!!.getDouble(k)),
                                )
                            }

                            i++
                        }
                    }
                    if (
                        !(PixelMcpWriteTools.hash(frozen))!!.equals(PixelMcpWriteTools.hash(actual))
                    ) {
                        throw IllegalArgumentException(
                            "Storno muss den unveränderten Originalpositionen entsprechen."
                        )
                    }
                }
            }
            return ((next!!.put("issuer", JSONObject(settings!!.toString())))!!.put(
                    "recipient",
                    JSONObject(customer!!.toString()),
                ))!!
                .put(
                    "due_date",
                    (date!!.plusDays((next!!.getInt("payment_days")).toLong()))!!.toString(),
                )
        }

        @JvmStatic
        fun required(o: JSONObject, key: String): Unit {
            if (((o!!.optString(key))!!.trim())!!.isEmpty()) {
                throw IllegalArgumentException(("Pflichtangabe fehlt: " + key))
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun commit(
            s: JSONObject,
            prepared: JSONObject,
            reservation: JSONObject,
            stamp: String,
        ): Unit {
            var number = reservation!!.getString("invoice_number")
            if (
                (!(prepared!!.getString("id"))!!.equals(reservation!!.optString("invoice_id")) ||
                    !(reservation!!.optString("action_sha256"))!!.matches(Regex("[0-9a-f]{64}")))
            ) {
                throw IllegalArgumentException("Nummernbeleg gehört nicht zur Rechnung.")
            }
            run {
                var i: Int = 0
                while ((i < (s!!.getJSONArray("invoices"))!!.length())) {
                    var other = (s!!.getJSONArray("invoices"))!!.getJSONObject(i)
                    if (
                        (!(other!!.getString("id"))!!.equals(prepared!!.getString("id")) &&
                            number!!.equals(other!!.optString("invoice_number")))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "NUMBER_COLLISION",
                            "Rechnungsnummer bereits belegt; keine Überschreibung.",
                        )
                    }

                    i++
                }
            }
            if ((prepared!!.optString("pdf_sha256"))!!.isEmpty()) {
                throw IllegalArgumentException("Original-PDF fehlt.")
            }
            var issued: JSONObject =
                (((((JSONObject(prepared!!.toString()))!!.put("invoice_number", number))!!.put(
                                "issued_at",
                                stamp,
                            ))!!
                            .put("updated_at", stamp))!!
                        .put("status", "Gestellt"))!!
                    .put("numbering_receipt", reservation)
            OwnerJournalRules.replace(s, "invoices", issued!!.getString("id"), issued)
            var links = issued!!.getJSONArray("worklog_ids")
            run {
                var i: Int = 0
                while ((i < links!!.length())) {
                    var work: JSONObject =
                        JSONObject(
                            (PixelMcpWriteTools.find(
                                    s!!.getJSONArray("worklogs"),
                                    links!!.getString(i),
                                ))!!
                                .toString()
                        )
                    (work!!.put("billed", 1))!!.put("updated_at", stamp)
                    OwnerJournalRules.replace(s, "worklogs", work!!.getString("id"), work)

                    i++
                }
            }
            if ((issued!!.optString("document_type"))!!.equals("cancellation")) {
                var original: JSONObject =
                    JSONObject(
                        (PixelMcpWriteTools.find(
                                s!!.getJSONArray("invoices"),
                                issued!!.getString("correction_of"),
                            ))!!
                            .toString()
                    )
                (original!!.put("status", "Storniert"))!!.put("updated_at", stamp)
                OwnerJournalRules.replace(s, "invoices", original!!.getString("id"), original)
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun correction(s: JSONObject, a: JSONObject): Unit {
            var p = a!!.getJSONObject("payload")
            var original =
                PixelMcpWriteTools.find(s!!.getJSONArray("invoices"), p!!.getString("id"))
            DriveJournalRules.compare(p!!.getJSONObject("_expected_invoice"), original)
            var type = p!!.getString("correction_type")
            if (
                ((((original!!.optString("issued_at"))!!.isEmpty() ||
                    !(Arrays.asList("replacement", "cancellation"))!!.contains(type)) ||
                    ((p!!.optString("reason"))!!.trim())!!.isEmpty()) ||
                    (type!!.equals("cancellation") &&
                        (original!!.optString("status"))!!.equals("Storniert")))
            ) {
                throw IllegalArgumentException("Originalbezug, Korrekturart und Grund prüfen.")
            }
            var id =
                ("inv_correction_" +
                    (PixelMcpWriteTools.hash(a!!.getString("client_id")))!!.substring(0, 32))
            if ((OwnerJournalRules.findOrNull(s!!.getJSONArray("invoices"), id) !== null)) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Korrektur-ID bereits belegt.")
            }
            var next: JSONObject = JSONObject(original!!.toString())
            for (key in
                (arrayOf(
                    "issuer",
                    "recipient",
                    "pdf_sha256",
                    "pdf_storage_name",
                    "pdf_byte_size",
                    "pdf_base64",
                    "numbering_receipt",
                    "document_id",
                ))!!) {
                next!!.remove(key)
            }
            var correctionDate =
                (((Instant.parse(a!!.getString("created_at")))!!.atZone(
                            ZoneId.of("Europe/Vienna")
                        ))!!
                        .toLocalDate())!!
                    .toString()
            ((((((((((next!!.put("id", id))!!.put("invoice_number", ("Entwurf-" + id)))!!.put(
                                                "invoice_date",
                                                correctionDate,
                                            ))!!
                                            .put("status", "Entwurf"))!!
                                        .put("issued_at", ""))!!
                                    .put("sevdesk_id", ""))!!
                                .put("document_type", type))!!
                            .put("correction_of", original!!.getString("id")))!!
                        .put("correction_reason", p!!.getString("reason")))!!
                    .put("created_at", a!!.getString("created_at")))!!
                .put("updated_at", a!!.getString("created_at"))
            var positions = next!!.getJSONArray("positions")
            run {
                var i: Int = 0
                while ((i < positions!!.length())) {
                    (positions!!.getJSONObject(i))!!.put("worklog_id", "")

                    i++
                }
            }
            InvoiceDraftRules.normalize(next, null, s)
            OwnerJournalRules.replace(s, "invoices", id, next)
        }
    }
}
