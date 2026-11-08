package at.hausgarten.service

import java.math.*
import java.time.LocalDate
import java.util.*
import org.json.*

/** One offer rule path for the existing UI, MCP and Drive snapshot interpreter. */
internal class OfferRules {
    companion object {
        @JvmField
        val FIELDS: Array<String> =
            arrayOf(
                "customer_id",
                "billing_customer_id",
                "order_id",
                "location",
                "title",
                "document_type",
                "issue_date",
                "valid_until",
                "execution_window",
                "positions",
                "tax_mode",
                "payment_terms",
                "notes",
                "assumptions",
                "exclusions",
                "header_text",
                "footer_text",
                "customer_type",
                "contract_channel",
                "consumer_information",
                "consumer_information_confirmed",
                "scope_confirmed",
                "binding_confirmed",
                "binding_notice",
                "test_record",
            )
        @JvmField val TYPES: Array<String> = OfferContract.types()

        @JvmStatic
        fun label(type: String): String {
            return (OfferContract.type(type)).optString("label", "Dokumentart noch wählen")
        }

        @JvmStatic
        @Throws(Exception::class)
        fun bindingHash(p: JSONObject): String {
            return PixelMcpWriteTools.hash(
                ((JSONObject()).put("document_type", p.optString("document_type"))).put(
                    "binding_notice",
                    p.optString("binding_notice"),
                )
            )
        }

        @JvmStatic
        fun editable(p: JSONObject): Boolean {
            return (Arrays.asList("draft", "validated")).contains(p.optString("status", "draft"))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun contractView(p: JSONObject): JSONObject {
            val type = OfferContract.type(p.optString("document_type"))
            return (((((JSONObject()).put("supported", (type.length() > 0))).put(
                            "binding_mode",
                            p.optString("binding_mode", type.optString("binding_mode", "unknown")),
                        ))!!
                        .put("binding_status", p.optString("binding_status", "pending_review")))!!
                    .put(
                        "binding_notice",
                        p.optString("binding_notice", type.optString("notice")),
                    ))!!
                .put(
                    "legacy_missing_binding_confirmation",
                    ((p.optString("document_type")).startsWith("estimate_") &&
                        !p.has("binding_confirmed")),
                )
        }

        @JvmStatic
        @Throws(Exception::class)
        fun normalize(p: JSONObject, old: JSONObject?, snapshot: JSONObject): JSONObject {
            if ((old !== null) && !editable(old)) {
                throw IllegalArgumentException(
                    "Finalisierte Angebote bleiben unverändert. Für Änderungen duplizieren."
                )
            }
            PixelMcpWriteTools.find(snapshot.getJSONArray("customers"), p.optString("customer_id"))
            if (!(p.optString("billing_customer_id")).isEmpty()) {
                PixelMcpWriteTools.find(
                    snapshot.getJSONArray("customers"),
                    p.getString("billing_customer_id"),
                )
            }
            if (
                (!(p.optString("order_id")).isEmpty() &&
                    !(p.getString("customer_id"))!!.equals(
                        (PixelMcpWriteTools.find(
                                snapshot.getJSONArray("orders"),
                                p.getString("order_id"),
                            ))!!
                            .optString("customer_id")
                    ))
            ) {
                throw IllegalArgumentException("Kunde und Auftrag widersprechen sich.")
            }
            val type = p.optString("document_type")
            if (!type.isEmpty() && !(Arrays.asList(*(TYPES))).contains(type)) {
                throw IllegalArgumentException("Unbekannte Angebotsart.")
            }
            val definition = OfferContract.type(type)
            (p.put("pricing_mode", definition.optString("pricing_mode", "unselected"))).put(
                "binding_mode",
                definition.optString("binding_mode", "unselected"),
            )
            if (
                (((old !== null) && !type!!.equals(old.optString("document_type"))) &&
                    (p.optString("binding_notice"))!!.equals(old.optString("binding_notice")))
            ) {
                p.remove("binding_notice")
            }
            val config = snapshot.optJSONObject("business_settings")
            var noticeKey =
                (if (type!!.equals("estimate_binding")) "offer_binding_text"
                else
                    (if (type!!.equals("estimate_nonbinding")) "offer_nonbinding_text"
                    else "offer_time_material_text"))
            var defaultNotice = (if (config === null) "" else config.optString(noticeKey))
            if ((defaultNotice.trim()).isEmpty()) {
                defaultNotice = definition.optString("notice")
            }
            if (!p.has("binding_notice")) {
                p.put("binding_notice", (if (type!!.equals("fixed_price")) "" else defaultNotice))
            }
            if (
                (((old !== null) &&
                    (!type!!.equals(old.optString("document_type")) ||
                        !(p.optString("binding_notice"))!!.equals(
                            old.optString("binding_notice")
                        ))) && !(bindingHash(p))!!.equals(p.optString("binding_confirmation_hash")))
            ) {
                p.put("binding_confirmed", false)
            }
            if (p.optBoolean("binding_confirmed")) {
                p.put("binding_confirmation_hash", bindingHash(p))
            } else {
                p.remove("binding_confirmation_hash")
            }
            p.put(
                "binding_status",
                (if (type.startsWith("estimate_"))
                    (if (p.optBoolean("binding_confirmed")) "confirmed" else "pending_review")
                else "not_applicable"),
            )
            if (!p.has("delivery_status")) {
                p.put("delivery_status", "not_prepared")
            }
            for (k in (arrayOf("validated_at", "validation_context_hash"))!!) {
                p.remove(k)
            }
            TestDataRules.guard(p, snapshot)
            for (k in
                (arrayOf(
                    "title",
                    "location",
                    "execution_window",
                    "payment_terms",
                    "notes",
                    "assumptions",
                    "exclusions",
                    "header_text",
                    "footer_text",
                    "customer_type",
                    "contract_channel",
                    "consumer_information",
                    "binding_notice",
                ))!!) {
                if (!p.has(k)) {
                    p.put(k, "")
                }
                if (!(p.get(k) is String) || ((p.getString(k)).length > 10000)) {
                    throw IllegalArgumentException("Ungültiger Angebotstext: " + k)
                }
            }
            for (k in (arrayOf("issue_date", "valid_until"))!!) {
                if (!(p.optString(k)).isEmpty()) {
                    LocalDate.parse(p.getString(k))
                }
            }
            var settings = snapshot.optJSONObject("business_settings")
            if (settings === null) {
                settings = JSONObject()
            }
            if (!p.has("tax_mode") || (p.optString("tax_mode")).isEmpty()) {
                p.put("tax_mode", settings.optString("tax_mode"))
            }
            for (e in
                (arrayOf(
                    arrayOf("customer_type", "customer_types"),
                    arrayOf("contract_channel", "contract_channels"),
                    arrayOf("tax_mode", "tax_modes"),
                ))!!) {
                if (
                    (!(p.optString(e!![0])).isEmpty() &&
                        !(Arrays.asList(*(OfferContract.values(e!![1])))).contains(
                            p.getString(e!![0])
                        ))
                ) {
                    throw PixelMcpWriteTools.Rejected(
                        "INVALID_INPUT",
                        ("Unbekannter Domänenwert: " + e!![0]),
                    )
                }
            }
            for (key in
                (arrayOf(
                    "binding_confirmed",
                    "scope_confirmed",
                    "consumer_information_confirmed",
                    "test_record",
                ))!!) {
                if (p.has(key) && !(p.get(key) is Boolean)) {
                    throw PixelMcpWriteTools.Rejected(
                        "INVALID_INPUT",
                        ("Bestätigung muss Ja/Nein sein: " + key),
                    )
                }
            }
            var lines = p.optJSONArray("positions")
            if (lines === null) {
                lines = JSONArray()
            }
            if (lines.length() > 200) {
                throw IllegalArgumentException("Höchstens 200 Angebotspositionen.")
            }
            var net = BigDecimal.ZERO
            var tax = BigDecimal.ZERO
            var complete: Boolean = true
            run {
                var i: Int = 0
                while (i < lines.length()) {
                    val line = lines.getJSONObject(i)
                    if (!line.has("price_basis")) {
                        line.put(
                            "price_basis",
                            (if ((line.optString("catalog_entry_id")).isEmpty()) "confirmed"
                            else "catalog"),
                        )
                    }
                    if (
                        (line.has("price_source") &&
                            !(Arrays.asList(*(OfferContract.values("price_sources")))).contains(
                                line.optString("price_source")
                            ))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            "Preisquelle exakt user_confirmed verwenden; Katalogpreise über ID und Hash.",
                        )
                    }
                    if (
                        (line.has("price_basis") &&
                            !(Arrays.asList(*(OfferContract.values("price_bases")))).contains(
                                line.optString("price_basis")
                            ))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "INVALID_INPUT",
                            "Unbekannte Preisgrundlage.",
                        )
                    }
                    if (
                        ((((line.optString("title")).length > 500) ||
                            ((line.optString("description")).length > 10000)) ||
                            ((line.optString("price_note")).length > 2000))
                    ) {
                        throw IllegalArgumentException("Positionstext ist zu lang.")
                    }
                    if (
                        (((!line.has("quantity") || line.isNull("quantity")) ||
                            !line.has("price")) || line.isNull("price"))
                    ) {
                        complete = false
                        (line.put("net", JSONObject.NULL)).put("tax", JSONObject.NULL)
                        run {
                            i++
                            continue
                        }
                    }
                    val q = decimal(line.get("quantity"))
                    val price = decimal(line.get("price"))
                    var rate =
                        decimal(
                            (if (!line.has("tax_rate") || line.isNull("tax_rate")) "0"
                            else line.get("tax_rate"))
                        )
                    if (
                        ((((((q.signum() <= 0) || (q.compareTo(BigDecimal("1000000000")) > 0)) ||
                            (price.signum() < 0)) ||
                            (price.compareTo(BigDecimal("1000000000")) > 0)) ||
                            (rate.signum() < 0)) || (rate.compareTo(BigDecimal("100")) > 0))
                    ) {
                        throw IllegalArgumentException("Mengen, Preise oder Steuer sind ungültig.")
                    }
                    if (
                        ((p.optString("tax_mode"))!!.equals("small_business") &&
                            (rate.signum() != 0))
                    ) {
                        throw IllegalArgumentException(
                            "Kleinunternehmermodus erlaubt keinen Steuerausweis."
                        )
                    }
                    if (
                        ((p.optString("tax_mode"))!!.equals("standard") &&
                            (!line.has("tax_rate") || line.isNull("tax_rate")))
                    ) {
                        complete = false
                        (line.put("net", JSONObject.NULL)).put("tax", JSONObject.NULL)
                        run {
                            i++
                            continue
                        }
                    }
                    val subtotal = money(q.multiply(price))
                    val t = money((subtotal.multiply(rate)).divide(BigDecimal("100")))
                    (line.put("net", subtotal.toDouble())).put("tax", t.toDouble())
                    net = net.add(subtotal)
                    tax = tax.add(t)

                    i++
                }
            }
            if ((net.add(tax)).compareTo(BigDecimal("1000000000")) > 0) {
                throw IllegalArgumentException(
                    "Angebot überschreitet die sichere Betragsgrenze von einer Milliarde EUR."
                )
            }
            ((((p.put("positions", lines)).put(
                            "net_total",
                            (if (complete) net.toDouble() else JSONObject.NULL),
                        ))!!
                        .put("tax_total", (if (complete) tax.toDouble() else JSONObject.NULL)))!!
                    .put(
                        "gross_total",
                        (if (complete) (net.add(tax)).toDouble() else JSONObject.NULL),
                    ))!!
                .put("status", "draft")
            run {
                if (complete) {
                    p.put("money_minor", OfferMoney.exactTotals(p))
                } else {
                    p.remove("money_minor")
                }
            }
            if (!p.has("created_at")) {
                p.put("created_at", (java.time.Instant.now())!!.toString())
            }
            return p
        }

        @JvmStatic
        @Throws(Exception::class)
        fun positionText(positions: JSONArray): String {
            var out: StringJoiner = StringJoiner("\n")
            run {
                var i: Int = 0
                while (i < positions.length()) {
                    var p = positions.getJSONObject(i)
                    out.add(
                        ((((((((p.getString("title")).toString() + " — ") +
                                        ((decimal(p.get("quantity"))).stripTrailingZeros())!!
                                            .toPlainString())
                                    .toString() + " ") + p.optString("unit"))
                                .toString() + " × ") +
                                ((decimal(p.get("price"))).stripTrailingZeros())!!.toPlainString())
                            .toString() + " EUR")
                    )

                    i++
                }
            }
            return out!!.toString()
        }

        @JvmStatic
        fun decimal(n: Any?): BigDecimal {
            try {
                if ((n === null) || (n === JSONObject.NULL)) {
                    throw Exception()
                }
                val d: BigDecimal = BigDecimal(n!!.toString())
                if ((d.precision() > 24) || (Math.abs(d.scale()) > 10)) {
                    throw Exception()
                }
                return d
            } catch (e: Exception) {
                throw IllegalArgumentException("Endliche Mengen und Preise erforderlich.")
            }
        }

        @JvmStatic
        fun money(d: BigDecimal): BigDecimal {
            return d.setScale(2, RoundingMode.HALF_UP)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validate(p: JSONObject, snapshot: JSONObject): JSONObject {
            var snapshot = snapshot

            if ((!editable(p) && p.has("issuer")) && p.has("recipient")) {
                snapshot =
                    (JSONObject(snapshot!!.toString())).put(
                        "business_settings",
                        p.getJSONObject("issuer"),
                    )
                var recipient = p.optString("billing_customer_id")
                if (recipient.isEmpty()) {
                    recipient = p.optString("customer_id")
                }
                var frozen: JSONObject =
                    (JSONObject((p.getJSONObject("recipient"))!!.toString())).put("id", recipient)
                OwnerJournalRules.replace(snapshot, "customers", recipient, frozen)
            }
            val checked: JSONObject = JSONObject(p!!.toString())
            normalize(checked, null, snapshot)
            val errors: JSONArray = JSONArray()
            val warnings: JSONArray = JSONArray()
            val fieldErrors: JSONObject = JSONObject()
            var s = snapshot.optJSONObject("business_settings")
            if (s === null) {
                s = JSONObject()
            }
            for (k in (OfferContract.values("issuer_required_fields"))!!) {
                if (((s.optString(k)).trim()).isEmpty()) {
                    errors.put("Aussteller: " + k)
                }
            }
            if (!(Arrays.asList("1", "true")).contains(s.optString("invoice_issuer_confirmed"))) {
                errors.put("Ausstellerdaten bestätigen")
            }
            for (k in (OfferContract.values("required_fields"))!!) {
                if (((p.optString(k)).trim()).isEmpty()) {
                    errors.put(k.toString() + " fehlt")
                }
            }
            if (!(Arrays.asList(*(TYPES))).contains(p.optString("document_type"))) {
                errors.put("Dokumentart ausdrücklich auswählen")
            }
            try {
                if (
                    (LocalDate.parse(p.getString("valid_until"))).isBefore(
                        LocalDate.parse(p.getString("issue_date"))
                    )
                ) {
                    errors.put("Gültigkeit liegt vor Angebotsdatum")
                }
            } catch (e: Exception) {
                errors.put("Gültige Angebotsdaten erforderlich")
            }
            var recipient = p.optString("billing_customer_id", "")
            if (recipient.isEmpty()) {
                recipient = p.optString("customer_id")
            }
            val c = OwnerJournalRules.findOrNull(snapshot.optJSONArray("customers"), recipient)
            if (c === null) {
                errors.put("Eindeutiger Kunde fehlt")
            } else {
                for (k in (arrayOf("name", "street", "postal_code", "city"))!!) {
                    if (((c.optString(k)).trim()).isEmpty()) {
                        errors.put("Empfänger: " + k)
                    }
                }
                val country = (c.optString("country")).lowercase(Locale.GERMAN)
                if (!(Arrays.asList("at", "austria", "österreich")).contains(country)) {
                    errors.put("Auslandsfall benötigt gesonderte Prüfung")
                }
            }
            var customer =
                OwnerJournalRules.findOrNull(
                    snapshot.optJSONArray("customers"),
                    p.optString("customer_id"),
                )
            if (
                (((customer !== null) &&
                    (customer.optString("billing_mode"))!!.equals("via_customer")) &&
                    (p.optString("billing_customer_id")).isEmpty())
            ) {
                errors.put("Abweichenden Rechnungsempfänger ausdrücklich auswählen")
            }
            if (
                (!(Arrays.asList(*(OfferContract.values("tax_modes")))).contains(
                    s.optString("tax_mode")
                ) || !(s.optString("tax_mode"))!!.equals(p.optString("tax_mode")))
            ) {
                errors.put("Steuermodus zentral bestätigen")
            }
            if (
                ((p.optString("tax_mode"))!!.equals("standard") &&
                    (s.optString("business_vat_id")).isEmpty())
            ) {
                errors.put("UID des Ausstellers fehlt")
            }
            if (
                ((p.optString("tax_mode"))!!.equals("small_business") &&
                    (s.optString("tax_exemption_text")).isEmpty())
            ) {
                errors.put("Steuerbefreiungshinweis fehlt")
            }
            val lines = p.optJSONArray("positions")
            if ((lines === null) || (lines.length() == 0)) {
                errors.put("Mindestens eine Leistung erforderlich")
            }
            run {
                var i: Int = 0
                while ((lines !== null) && (i < lines.length())) {
                    val line = lines.getJSONObject(i)
                    val at = (("Position " + (i + 1)).toString() + ": ")
                    for (key in (arrayOf("title", "unit"))!!) {
                        if (((line.optString(key)).trim()).isEmpty()) {
                            fieldErrors.put(
                                ((("positions." + i).toString() + ".") + key),
                                "Pflichtangabe fehlt",
                            )
                        }
                    }
                    for (key in (arrayOf("price", "quantity"))!!) {
                        if (!line.has(key) || line.isNull(key)) {
                            fieldErrors.put(
                                ((("positions." + i).toString() + ".") + key),
                                (if (key!!.equals("price")) "Preis fehlt – nachfragen"
                                else "Menge fehlt"),
                            )
                        }
                    }
                    if (
                        (((line.optString("title")).trim()).isEmpty() ||
                            ((line.optString("unit")).trim()).isEmpty())
                    ) {
                        errors.put(at.toString() + "Leistung und Einheit fehlen")
                    }
                    if (!line.has("price") || line.isNull("price")) {
                        errors.put(at.toString() + "Preis fehlt – nachfragen")
                    }
                    if (
                        ((!line.has("quantity") || line.isNull("quantity")) ||
                            (line.optDouble("quantity", (0).toDouble()) <= 0))
                    ) {
                        errors.put(at.toString() + "Menge fehlt")
                    }
                    if (!(line.optString("catalog_entry_id")).isEmpty()) {
                        var template =
                            OwnerJournalRules.findOrNull(
                                snapshot.optJSONArray("service_catalog"),
                                line.getString("catalog_entry_id"),
                            )
                        if (
                            (((((((template === null) || !template.optBoolean("price_confirmed")) ||
                                template.isNull("default_price")) ||
                                !(PixelMcpWriteTools.hash(template))!!.equals(
                                    line.optString("catalog_hash")
                                )) || !line.has("price")) || line.isNull("price")) ||
                                ((decimal(template.get("default_price"))).compareTo(
                                    decimal(line.get("price"))
                                ) != 0))
                        ) {
                            errors.put((at.toString() + "freigegebenen Katalogpreis erneut prüfen"))
                        }
                    } else {
                        if (
                            !(Arrays.asList(*(OfferContract.values("price_sources")))).contains(
                                line.optString("price_source")
                            )
                        ) {
                            errors.put(
                                (at.toString() + "Preisquelle fehlt – keinen Preis erfinden")
                            )
                        }
                    }
                    var basis =
                        line.optString(
                            "price_basis",
                            (if ((line.optString("catalog_entry_id")).isEmpty()) "confirmed"
                            else "catalog"),
                        )
                    if (
                        (!(Arrays.asList(*(OfferContract.values("price_bases")))).contains(basis) ||
                            (basis!!.equals("catalog") !=
                                !(line.optString("catalog_entry_id")).isEmpty()))
                    ) {
                        errors.put(at.toString() + "Preisgrundlage widerspricht Preisquelle")
                    }
                    if (
                        ((Arrays.asList("calculated", "estimated")).contains(basis) &&
                            ((line.optString("price_note")).trim()).isEmpty())
                    ) {
                        errors.put(
                            (at.toString() +
                                "Berechnung oder Schätzung erläutern und Preis bestätigen")
                        )
                    }
                    if (basis!!.equals("calculated")) {
                        try {
                            val calculation = line.getJSONObject("calculation")
                            var derived =
                                money(
                                    ((decimal(calculation.get("base_price"))).multiply(
                                            decimal(calculation.get("factor"))
                                        ))!!
                                        .add(
                                            decimal(
                                                (if (calculation.opt("surcharge") === null) 0
                                                else calculation.get("surcharge"))
                                            )
                                        )
                                )
                            if (derived.compareTo(decimal(line.get("price"))) != 0) {
                                errors.put(
                                    (at.toString() +
                                        "Berechnung stimmt nicht mit dem Einzelpreis überein")
                                )
                            }
                        } catch (invalid: Exception) {
                            errors.put(
                                (at.toString() + "Vollständige Preisberechnung erforderlich")
                            )
                        }
                    }
                    if (
                        ((p.optString("tax_mode"))!!.equals("standard") &&
                            (!line.has("tax_rate") || line.isNull("tax_rate")))
                    ) {
                        errors.put(at.toString() + "Steuersatz fehlt")
                    }

                    i++
                }
            }
            if (
                !(Arrays.asList(*(OfferContract.values("customer_types")))).contains(
                    p.optString("customer_type")
                )
            ) {
                errors.put("B2B oder Verbraucher bewusst auswählen")
            }
            if (!p.optBoolean("scope_confirmed")) {
                errors.put("Leistungsumfang, Annahmen und Ausschlüsse prüfen")
            }
            if ((p.optString("customer_type"))!!.equals("consumer")) {
                if (
                    !(Arrays.asList(*(OfferContract.values("contract_channels")))).contains(
                        p.optString("contract_channel")
                    )
                ) {
                    errors.put("Vertragsweg für Verbraucher auswählen")
                }
                if (
                    ((Arrays.asList("distance", "off_premise")).contains(
                        p.optString("contract_channel")
                    ) &&
                        (!p.optBoolean("consumer_information_confirmed") ||
                            ((p.optString("consumer_information")).trim()).isEmpty()))
                ) {
                    errors.put(
                        "FAGG-Informationen und Rücktrittsbelehrung gesondert prüfen und beifügen"
                    )
                }
            }
            if (
                ((p.optString("customer_type"))!!.equals("consumer") &&
                    (Arrays.asList("distance", "off_premise")).contains(
                        p.optString("contract_channel")
                    ))
            ) {
                for (key in (arrayOf("business_phone", "business_email"))!!) {
                    if (((s.optString(key)).trim()).isEmpty()) {
                        errors.put("FAGG-Kontaktangaben: " + key)
                    }
                }
            }
            if (
                ((p.optString("document_type")).startsWith("estimate_") &&
                    (!p.optBoolean("binding_confirmed") ||
                        ((p.optString("binding_notice")).trim()).isEmpty()))
            ) {
                errors.put("Bindungsart und Hinweistext ausdrücklich bestätigen")
                fieldErrors.put(
                    "binding_confirmed",
                    "Verbindlich oder unverbindlich sowie zugehörigen Hinweistext prüfen.",
                )
            }
            try {
                TestDataRules.guard(JSONObject(p!!.toString()), snapshot)
            } catch (isolated: IllegalArgumentException) {
                errors.put("Testdaten-Isolation: Testkunde und Testempfänger erforderlich")
                fieldErrors.put(
                    "customer_id",
                    "Testdaten dürfen nicht mit produktiven Kunden verknüpft sein.",
                )
            }
            for (k in (OfferContract.values("required_fields"))!!) {
                if (((p.optString(k)).trim()).isEmpty()) {
                    fieldErrors.put(k, "Pflichtangabe fehlt")
                }
            }
            if (
                !(Arrays.asList(*(OfferContract.values("customer_types")))).contains(
                    p.optString("customer_type")
                )
            ) {
                fieldErrors.put("customer_type", "Kundentyp bewusst wählen")
            }
            if (!p.optBoolean("scope_confirmed")) {
                fieldErrors.put("scope_confirmed", "Leistungsumfang bestätigen")
            }
            if (!(Arrays.asList(*(TYPES))).contains(p.optString("document_type"))) {
                fieldErrors.put("document_type", "Dokumentart ausdrücklich wählen")
            }
            if ((p.optString("document_type"))!!.equals("estimate_nonbinding")) {
                warnings.put(
                    "Keine Gewähr für die Richtigkeit des Voranschlags; unvermeidliche beträchtliche Überschreitungen unverzüglich anzeigen (§1170a ABGB). Kein Freibrief für Zusatzkosten."
                )
            }
            warnings.put(
                "Technische Vollständigkeitsprüfung; keine automatische Prüfung von Gewerbeberechtigung, allen Klauseln oder FAGG-Ausnahmen."
            )
            return (((((((JSONObject()).put("valid", (errors.length() == 0))).put(
                                    "errors",
                                    errors,
                                ))!!
                                .put("field_errors", fieldErrors))!!
                            .put("warnings", warnings))!!
                        .put(
                            "legal_rules_version",
                            (OfferContract.data()).getString("legal_rules_version"),
                        ))!!
                    .put("offer_number", p.optString("offer_number")))!!
                .put(
                    "number_assigned_on_finalization",
                    (p.optString("status", "draft")).matches(Regex("draft|validated")),
                )
        }
    }
}
