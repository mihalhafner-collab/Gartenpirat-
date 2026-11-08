package at.hausgarten.service

import java.util.*
import org.json.*

/** Additive owner tools; the existing private tunnel write gate remains authoritative. */
internal class OfferTools {
    companion object {
        @JvmField
        val READS: Array<String> =
            arrayOf(
                "get_offer_contract",
                "list_offers",
                "get_offer",
                "validate_offer",
                "search_offer_templates",
                "get_offer_template",
                "get_offer_context",
            )
        @JvmField
        val WRITES: Array<String> =
            arrayOf(
                "create_and_validate_offer",
                "validate_offer_draft",
                "prepare_offer_delivery",
                "confirm_offer_delivery",
                "create_offer_draft",
                "update_offer_draft",
                "finalize_offer",
                "duplicate_offer",
                "prepare_offer_share",
                "convert_offer_to_order",
                "create_catalog_entry",
                "update_catalog_entry",
            )

        @JvmStatic
        fun read(n: String): Boolean {
            return (Arrays.asList(*(READS)))!!.contains(n)
        }

        @JvmStatic
        fun write(n: String): Boolean {
            return (Arrays.asList(*(WRITES)))!!.contains(n)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun fields(): JSONObject {
            var p: JSONObject = JSONObject()
            for (k in (OfferRules.FIELDS)!!) {
                p!!.put(k, McpFields.text(10000))
            }
            for (k in
                (arrayOf(
                    "scope_confirmed",
                    "consumer_information_confirmed",
                    "binding_confirmed",
                    "test_record",
                ))!!) {
                p!!.put(k, McpFields.bool())
            }
            (((p!!.put("document_type", McpFields.enumeration(*(OfferRules.TYPES))))!!.put(
                        "customer_type",
                        McpFields.enumeration(*(OfferContract.values("customer_types"))),
                    ))!!
                    .put(
                        "contract_channel",
                        McpFields.enumeration(*(OfferContract.values("contract_channels"))),
                    ))!!
                .put("tax_mode", McpFields.enumeration(*(OfferContract.values("tax_modes"))))
            var line: JSONObject = JSONObject()
            for (k in
                (arrayOf("title", "description", "unit", "catalog_entry_id", "catalog_hash"))!!) {
                line!!.put(k, McpFields.text(10000))
            }
            line!!.put("title", McpFields.text(500))
            line!!.put(
                "price_source",
                (McpFields.enumeration(*(OfferContract.values("price_sources"))))!!.put(
                    "description",
                    "Bei einem ausdrücklich vom Nutzer bestätigten manuellen Preis exakt user_confirmed verwenden; bei freigegebenem Katalogpreis weglassen und catalog_entry_id/catalog_hash verwenden.",
                ),
            )
            (line!!.put(
                    "price_basis",
                    McpFields.enumeration(*(OfferContract.values("price_bases"))),
                ))!!
                .put("price_note", McpFields.text(2000))
            var calculation: JSONObject = JSONObject()
            for (k in (arrayOf("base_price", "factor", "surcharge"))!!) {
                calculation!!.put(
                    k,
                    (((JSONObject())!!.put("type", ((JSONArray())!!.put("number"))!!.put("null")))!!
                            .put("minimum", 0))!!
                        .put("maximum", 1000000000),
                )
            }
            line!!.put("calculation", McpFields.`object`(calculation))
            for (k in (arrayOf("quantity", "price", "tax_rate"))!!) {
                line!!.put(
                    k,
                    (((JSONObject())!!.put("type", ((JSONArray())!!.put("number"))!!.put("null")))!!
                            .put("minimum", 0))!!
                        .put("maximum", 1000000000),
                )
            }
            (line!!.getJSONObject("tax_rate"))!!.put("maximum", 100)
            ((line!!.getJSONObject("quantity"))!!.put("exclusiveMinimum", 0))!!.put(
                "description",
                "Bei bekannten Mengen größer als0, andernfalls null für einen unvollständigen Entwurf.",
            )
            p!!.put(
                "positions",
                (((JSONObject())!!.put("type", "array"))!!.put("maxItems", 200))!!.put(
                    "items",
                    McpFields.`object`(line, "title", "unit"),
                ),
            )
            return p
        }

        @JvmStatic
        @Throws(Exception::class)
        fun catalogFields(): JSONObject {
            var p: JSONObject = JSONObject()
            for (k in
                (arrayOf(
                    "title",
                    "description",
                    "unit",
                    "pricing_type",
                    "category",
                    "legal_notes",
                    "assumptions",
                    "exclusions",
                ))!!) {
                p!!.put(k, McpFields.text(10000))
            }
            for (k in (arrayOf("default_price", "default_quantity"))!!) {
                p!!.put(
                    k,
                    (((JSONObject())!!.put("type", ((JSONArray())!!.put("number"))!!.put("null")))!!
                            .put("minimum", 0))!!
                        .put("maximum", 1000000000),
                )
            }
            p!!.put("pricing_type", McpFields.enumeration(*(OfferContract.values("pricing_types"))))
            (p!!.put("active", McpFields.bool()))!!.put("price_confirmed", McpFields.bool())
            return p
        }

        @JvmStatic
        @Throws(Exception::class)
        fun schema(n: String): JSONObject {
            var p: JSONObject = JSONObject()
            var required: MutableList<String> = ArrayList()
            if (write(n)) {
                p!!.put("request_id", (McpFields.text(128))!!.put("minLength", 8))
                required!!.add("request_id")
            }
            if (
                (Arrays.asList(
                        "get_offer",
                        "validate_offer",
                        "update_offer_draft",
                        "finalize_offer",
                        "duplicate_offer",
                        "prepare_offer_share",
                        "convert_offer_to_order",
                        "validate_offer_draft",
                        "prepare_offer_delivery",
                        "confirm_offer_delivery",
                    ))!!
                    .contains(n)
            ) {
                p!!.put("offer_id", McpFields.id())
                required!!.add("offer_id")
            }
            if (
                (Arrays.asList(
                        "update_offer_draft",
                        "finalize_offer",
                        "convert_offer_to_order",
                        "prepare_offer_share",
                        "validate_offer_draft",
                        "prepare_offer_delivery",
                        "confirm_offer_delivery",
                    ))!!
                    .contains(n)
            ) {
                p!!.put("expected_record_hash", McpFields.hash())
                required!!.add("expected_record_hash")
            }
            when (n) {
                "get_offer" -> p.put("include_review", McpFields.bool())
                "create_offer_draft",
                "create_and_validate_offer" -> {
                    run {
                        var f = fields()
                        run {
                            var it = f!!.keys()
                            while (it!!.hasNext()) {
                                var k = it!!.next()
                                p!!.put(k, f!!.get(k))
                            }
                        }
                        required!!.addAll(Arrays.asList("customer_id", "title", "positions"))
                    }
                }
                "update_offer_draft" -> {
                    p!!.put("updates", (McpFields.`object`(fields()))!!.put("minProperties", 1))
                    required!!.add("updates")
                }
                "list_offers" -> {
                    ((((p!!.put("customer_id", McpFields.id()))!!.put(
                                    "document_type",
                                    McpFields.enumeration(*(OfferRules.TYPES)),
                                ))!!
                                .put(
                                    "status",
                                    McpFields.enumeration(
                                        *(OfferContract.values("workflow_statuses"))
                                    ),
                                ))!!
                            .put("offset", McpFields.number(0, 1000000)))!!
                        .put("limit", McpFields.number(1, 100))
                }
                "search_offer_templates" -> {
                    ((p!!.put("query", McpFields.text(200)))!!.put(
                            "offset",
                            McpFields.number(0, 1000000),
                        ))!!
                        .put("limit", McpFields.number(1, 100))
                }
                "get_offer_template" -> {
                    p!!.put("entry_id", McpFields.id())
                    required!!.add("entry_id")
                }
                "get_offer_context" -> {
                    p!!.put("customer_id", McpFields.id())
                    required!!.add("customer_id")
                }
                "prepare_offer_delivery" -> {
                    (p!!.put("recipient_email", (McpFields.text(320))!!.put("minLength", 3)))!!.put(
                        "confirmed",
                        (McpFields.bool())!!.put("enum", (JSONArray())!!.put(true)),
                    )
                    required!!.addAll(Arrays.asList("recipient_email", "confirmed"))
                }
                "confirm_offer_delivery" -> {
                    (p!!.put("provider", McpFields.enumeration("gmail")))!!.put(
                        "message_id",
                        McpFields.id(),
                    )
                    required!!.addAll(Arrays.asList("provider", "message_id"))
                }
                "finalize_offer" -> {
                    (p!!.put("expected_context_hash", McpFields.hash()))!!.put(
                        "confirmed",
                        (McpFields.bool())!!.put("enum", (JSONArray())!!.put(true)),
                    )
                    required!!.addAll(Arrays.asList("expected_context_hash", "confirmed"))
                }
                "convert_offer_to_order" -> {
                    ((p!!.put(
                                "confirmed",
                                (McpFields.bool())!!.put("enum", (JSONArray())!!.put(true)),
                            ))!!
                            .put("existing_order_id", McpFields.id()))!!
                        .put("expected_order_hash", McpFields.hash())
                    required!!.add("confirmed")
                }
                "create_catalog_entry" -> {
                    run {
                        var f = catalogFields()
                        run {
                            var it = f!!.keys()
                            while (it!!.hasNext()) {
                                var k = it!!.next()
                                p!!.put(k, f!!.get(k))
                            }
                        }
                        required!!.addAll(Arrays.asList("title", "unit", "category"))
                    }
                }
                "update_catalog_entry" -> {
                    ((p!!.put("entry_id", McpFields.id()))!!.put(
                            "expected_record_hash",
                            McpFields.hash(),
                        ))!!
                        .put(
                            "updates",
                            (McpFields.`object`(catalogFields()))!!.put("minProperties", 1),
                        )
                    required!!.addAll(Arrays.asList("entry_id", "expected_record_hash", "updates"))
                }
            }
            return McpFields.`object`(p, *(required!!.toTypedArray()))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun tools(writable: Boolean): JSONArray {
            var out: JSONArray = JSONArray()
            var names: MutableList<String> = ArrayList(Arrays.asList(*(READS)))
            if (writable) {
                names!!.addAll(Arrays.asList(*(WRITES)))
            }
            for (n in names!!) {
                var d =
                    (if (n!!.equals("get_offer_contract"))
                        "Liest den gemeinsamen Dokumentvertrag: Dokumentarten, Bindungsarten, Preisquellen/-grundlagen und Statuswerte. Keine Datenänderung."
                    else
                        (if (n!!.equals("create_and_validate_offer"))
                            "Erstellt und validiert einen Angebots-/Kostenvoranschlagsentwurf atomar. Bei ungültigen oder mehrdeutigen Angaben wird nichts gespeichert. Nur bestätigte Leistungen und Preise; Testflag erfordert Testkunden."
                        else
                            (if (n!!.equals("prepare_offer_delivery"))
                                "Bereitet das unveränderte Original-PDF für den ausdrücklich bestätigten Empfänger vor. Nicht gesendet. Anschließend separaten freigegebenen Mailkanal verwenden und dessen Nachrichten-ID prüfen."
                            else
                                (if (n!!.equals("confirm_offer_delivery"))
                                    "Prüft ausschließlich lesend die echte Gmail-SENT-Nachricht, Aussteller, bestätigten Empfänger und identischen Original-PDF-Hash. Erst nach Provider-Readback sent; separate Gmail-Lesefreigabe am Pixel notwendig. Keine Mail wird versendet."
                                else
                                    (if (n!!.equals("validate_offer_draft"))
                                        "Validiert und speichert den Status validated für den aktuellen Entwurf; atomarer Hashschutz, kein PDF-Versand und keine Rechnung."
                                    else
                                        (if (n!!.equals("prepare_offer_share"))
                                            "Bereitet das finalisierte Original-PDF mit SHA256 zum Teilen vor und protokolliert share_prepared. Niemals sent. Markierte Testdokumente liefern ausschließlich eine lokale Vorschau, keinen externen Versand."
                                        else
                                            (if (
                                                (n!!.contains("catalog") ||
                                                    n!!.contains("template"))
                                            )
                                                "Gemeinsame editierbare Leistungsbausteine. Fehlende Preise sind null und müssen erfragt werden; keine erfundenen Preise."
                                            else
                                                (if (n!!.equals("finalize_offer"))
                                                    "Finalisiert ausschließlich das geprüfte Angebot nach ausdrücklicher Bestätigung. Kunde, Aussteller und Preise sind über context_hash gebunden; kein Rechnungsabschluss."
                                                else
                                                    (if (n!!.equals("convert_offer_to_order"))
                                                        "Übernimmt ein ausdrücklich angenommenes Angebot mit Kunde, Einsatzort und offer_id in einen Auftrag. Keine Rechnung."
                                                    else
                                                        "Verwaltet Angebote in der bestehenden App und im lokalen Geschäftsjournal. Entwürfe dürfen unvollständig sein; Dokumentart bewusst wählen. record_hash vor Updates lesen; request_id bei Wiederholung unverändert lassen. Niemals Kunden, Stunden oder Preise erfinden.")))))))))
                out!!.put(
                    ((((JSONObject())!!.put("name", n))!!.put("description", d))!!.put(
                            "inputSchema",
                            schema(n),
                        ))!!
                        .put(
                            "annotations",
                            ((((JSONObject())!!.put("readOnlyHint", read(n)))!!.put(
                                        "destructiveHint",
                                        false,
                                    ))!!
                                    .put("idempotentHint", true))!!
                                .put("openWorldHint", false),
                        )
                )
            }
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun context(p: JSONObject, s: JSONObject): JSONObject {
            var recipient = p!!.optString("billing_customer_id")
            if (recipient!!.isEmpty()) {
                recipient = p!!.optString("customer_id")
            }
            var c =
                PixelMcpWriteTools.find(s!!.getJSONArray("customers"), p!!.getString("customer_id"))
            var r = PixelMcpWriteTools.find(s!!.getJSONArray("customers"), recipient)
            var settings = s!!.optJSONObject("business_settings")
            return (((((JSONObject())!!.put("customer", LocalJournalRules.guard(c, "customer")))!!
                            .put("recipient", LocalJournalRules.guard(r, "customer")))!!
                        .put(
                            "order",
                            (if ((p!!.optString("order_id"))!!.isEmpty()) JSONObject.NULL
                            else
                                LocalJournalRules.guard(
                                    PixelMcpWriteTools.find(
                                        s!!.getJSONArray("orders"),
                                        p!!.getString("order_id"),
                                    ),
                                    "order",
                                )),
                        ))!!
                    .put(
                        "business_settings",
                        (if ((settings === null)) JSONObject() else settings),
                    ))!!
                .put(
                    "service_catalog",
                    (if ((s!!.optJSONArray("service_catalog") === null)) JSONArray()
                    else s!!.getJSONArray("service_catalog")),
                )
        }

        @JvmStatic
        @Throws(Exception::class)
        fun query(n: String, a: JSONObject, state: JSONObject): JSONObject {
            McpSchema.validate(a, schema(n))
            if (n!!.equals("get_offer_contract")) {
                return OfferContract.data()
            }
            var s = PixelMcpReadTools.effective(state)
            var offers = s!!.getJSONArray("offers")
            if ((n!!.equals("get_offer") || n!!.equals("validate_offer"))) {
                var p = PixelMcpWriteTools.find(offers, a!!.getString("offer_id"))
                var result: JSONObject =
                    (((((JSONObject())!!.put("offer", p))!!.put(
                                    "document_contract",
                                    OfferRules.contractView(p),
                                ))!!
                                .put(
                                    "workflow_status",
                                    (if ((p!!.optString("delivery_status"))!!.equals("sent")) "sent"
                                    else
                                        (if (
                                            (p!!.optString("delivery_status"))!!.equals(
                                                "share_prepared"
                                            )
                                        )
                                            "share_prepared"
                                        else p!!.optString("status", "draft"))),
                                ))!!
                            .put(
                                "record_hash",
                                PixelMcpWriteTools.hash(LocalJournalRules.clean(p)),
                            ))!!
                        .put("context_hash", PixelMcpWriteTools.hash(context(p, s)))
                if (a.optBoolean("include_review")) {
                    val review = context(p, s)
                    val selected = JSONArray()
                    val positions = p.optJSONArray("positions") ?: JSONArray()
                    val catalog = s.optJSONArray("service_catalog") ?: JSONArray()
                    for (index in 0 until catalog.length()) {
                        val entry = catalog.getJSONObject(index)
                        if (
                            (0 until positions.length()).any {
                                positions.getJSONObject(it).optString("catalog_entry_id") ==
                                    entry.optString("id")
                            }
                        )
                            selected.put(entry)
                    }
                    review.put("service_catalog", selected)
                    result
                        .put("review_snapshot", review)
                        .put("validation", OfferRules.validate(p, s))
                }
                if (!(p!!.optString("order_id"))!!.isEmpty()) {
                    result!!.put(
                        "order_hash",
                        OwnerJournalRules.version(
                            PixelMcpWriteTools.find(
                                s!!.getJSONArray("orders"),
                                p!!.getString("order_id"),
                            )
                        ),
                    )
                }
                if (n!!.equals("validate_offer")) {
                    result!!.put("validation", OfferRules.validate(p, s))
                }
                return result
            }
            if (n!!.equals("get_offer_template")) {
                var p =
                    PixelMcpWriteTools.find(
                        s!!.getJSONArray("service_catalog"),
                        a!!.getString("entry_id"),
                    )
                return ((JSONObject())!!.put("entry", p))!!.put(
                    "record_hash",
                    PixelMcpWriteTools.hash(p),
                )
            }
            if (n!!.equals("get_offer_context")) {
                var id = a!!.getString("customer_id")
                var c = PixelMcpWriteTools.find(s!!.getJSONArray("customers"), id)
                var logs: JSONArray = JSONArray()
                var orders: JSONArray = JSONArray()
                var prices: JSONArray = JSONArray()
                var all = s!!.getJSONArray("worklogs")
                run {
                    var i: Int = (all!!.length() - 1)
                    while (((i >= 0) && (logs!!.length() < 10))) {
                        if (id!!.equals((all!!.getJSONObject(i))!!.optString("customer_id"))) {
                            logs!!.put(all!!.getJSONObject(i))
                        }

                        i--
                    }
                }
                all = s!!.getJSONArray("orders")
                run {
                    var i: Int = 0
                    while (((i < all!!.length()) && (orders!!.length() < 10))) {
                        if (
                            (id!!.equals((all!!.getJSONObject(i))!!.optString("customer_id")) &&
                                !(Arrays.asList("Erledigt", "Abgeschlossen", "Storniert"))!!
                                    .contains((all!!.getJSONObject(i))!!.optString("status")))
                        ) {
                            orders!!.put(all!!.getJSONObject(i))
                        }

                        i++
                    }
                }
                all = s!!.getJSONArray("service_catalog")
                run {
                    var i: Int = 0
                    while ((i < all!!.length())) {
                        var e = all!!.getJSONObject(i)
                        if (
                            ((e!!.optBoolean("active", true) &&
                                e!!.optBoolean("price_confirmed")) && !e!!.isNull("default_price"))
                        ) {
                            prices!!.put(
                                ((JSONObject())!!.put("entry", e))!!.put(
                                    "record_hash",
                                    PixelMcpWriteTools.hash(e),
                                )
                            )
                        }

                        i++
                    }
                }
                return ((((((JSONObject())!!.put("customer", c))!!.put(
                                    "billing_configuration",
                                    BillingRules.assignment(c, s, false),
                                ))!!
                                .put("recent_work", logs))!!
                            .put("open_orders", orders))!!
                        .put("approved_prices", prices))!!
                    .put("business_settings", s!!.optJSONObject("business_settings"))
            }
            var rows =
                (if (n!!.equals("list_offers")) offers else s!!.getJSONArray("service_catalog"))
            var out: JSONArray = JSONArray()
            var total: Int = 0
            if (n!!.equals("search_offer_templates")) {
                var collator = java.text.Collator.getInstance(Locale.GERMAN)
                collator!!.setStrength(java.text.Collator.PRIMARY)
                var ordered: MutableList<JSONObject> = ArrayList()
                run {
                    var i: Int = 0
                    while ((i < rows!!.length())) {
                        ordered!!.add(rows!!.getJSONObject(i))

                        i++
                    }
                }
                ordered!!.sortWith(
                    lambda@{ x, y ->
                        var order: Int =
                            SnapshotReadSupport.natural(
                                x!!.optString("category"),
                                y!!.optString("category"),
                                collator,
                            )
                        if ((order == 0)) {
                            order =
                                SnapshotReadSupport.natural(
                                    x!!.optString("title"),
                                    y!!.optString("title"),
                                    collator,
                                )
                        }
                        return@lambda (if ((order == 0))
                            (x!!.optString("id"))!!.compareTo(y!!.optString("id"))
                        else order)
                    }
                )
                rows = JSONArray(ordered)
            }
            run {
                var i: Int = 0
                while ((i < rows!!.length())) {
                    var p = rows!!.getJSONObject(i)
                    if (
                        (!(a!!.optString("customer_id"))!!.isEmpty() &&
                            !(a!!.getString("customer_id"))!!.equals(p!!.optString("customer_id")))
                    ) {
                        run {
                            i++
                            continue
                        }
                    }
                    if (
                        (!(a!!.optString("document_type"))!!.isEmpty() &&
                            !(a!!.getString("document_type"))!!.equals(
                                p!!.optString("document_type")
                            ))
                    ) {
                        run {
                            i++
                            continue
                        }
                    }
                    if (
                        ((!(a!!.optString("status"))!!.isEmpty() &&
                            !(a!!.getString("status"))!!.equals(p!!.optString("status"))) &&
                            !(a!!.getString("status"))!!.equals(p!!.optString("delivery_status")))
                    ) {
                        run {
                            i++
                            continue
                        }
                    }
                    if (
                        !SnapshotReadSupport.matches(
                            a!!.optString("query"),
                            SnapshotReadSupport.text(
                                p,
                                "title",
                                "category",
                                "description",
                                "assumptions",
                                "exclusions",
                                "legal_notes",
                                "offer_number",
                            ),
                        )
                    ) {
                        run {
                            i++
                            continue
                        }
                    }
                    if (
                        ((total++ >= a!!.optInt("offset", 0)) &&
                            (out!!.length() < a!!.optInt("limit", 30)))
                    ) {
                        out!!.put(
                            ((JSONObject())!!.put(
                                    (if (n!!.equals("list_offers")) "offer" else "entry"),
                                    p,
                                ))!!
                                .put(
                                    "record_hash",
                                    PixelMcpWriteTools.hash(LocalJournalRules.clean(p)),
                                )
                        )
                    }

                    i++
                }
            }
            return (((((JSONObject())!!.put(
                                (if (n!!.equals("list_offers")) "offers" else "entries"),
                                out,
                            ))!!
                            .put("total", total))!!
                        .put("offset", a!!.optInt("offset")))!!
                    .put("limit", a!!.optInt("limit", 30)))!!
                .put(
                    "next_cursor",
                    (if (((a!!.optInt("offset") + out!!.length()) < total))
                        (a!!.optInt("offset") + out!!.length())
                    else JSONObject.NULL),
                )
        }
    }
}
