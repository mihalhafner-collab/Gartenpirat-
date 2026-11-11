package at.hausgarten.service

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalSearchTest {
    private class Fixture {
        val master = JSONObject()
        val local = JSONObject()
        val ledger = JSONObject().put("records", JSONArray()).put("payments", JSONArray())
        val lcr = mutableSetOf<String>()
        val legacyLabor = mutableSetOf<String>()

        init {
            for (name in
                listOf(
                    "customers",
                    "orders",
                    "appointments",
                    "worklogs",
                    "offers",
                    "invoices",
                    "photos",
                )) master.put(name, JSONArray())
            for (name in listOf("property", "inquiry", "visit", "material", "equipment")) local.put(
                name,
                JSONArray(),
            )
        }

        fun add(target: JSONObject, collection: String, id: String) =
            JSONObject().put("id", id).also { target.getJSONArray(collection).put(it) }

        fun search(query: String = "", filters: JSONObject = JSONObject()) =
            LocalSearchRules.search(
                JSONObject(filters.toString()).put("query", query),
                master,
                local,
                ledger,
                lcr,
                LocalDate.parse("2026-11-11"),
                legacyLabor,
            )

        fun hits(query: String = "", filters: JSONObject = JSONObject()) =
            SettlementRules.rows(search(query, filters).getJSONArray("items"))
    }

    private fun filter(vararg entries: Pair<String, Any>) =
        JSONObject().apply { entries.forEach { (key, value) -> put(key, value) } }

    private fun types(vararg values: String) = filter("types" to JSONArray(values.toList()))

    @Test
    fun termsMatchOnlyUserFieldsAndExplicitNamesWithAndAndIgnoreCase() {
        val f = Fixture()
        f.add(f.master, "customers", "qa_customer")
            .put("name", "QA Äußerer Betrieb")
            .put("company", "QA Grünraum")
        f.add(f.local, "property", "qa_property")
            .put("name", "QA Rosengarten")
            .put("address", "QA Musterstraße 7")
            .put("customer_id", "qa_customer")
        f.add(f.master, "orders", "qa_order")
            .put("title", "QA Sommerauftrag")
            .put("customer_id", "qa_customer")
        val work =
            f.add(f.master, "worklogs", "qa_work")
                .put("activity", "QA Hecke kürzen")
                .put("customer_id", "qa_customer")
                .put("property_id", "qa_property")
                .put("order_id", "qa_order")
                .put("record_hash", "qa_hidden_hash")
                .put("source", "qa_hidden_source")
        val before = f.master.toString()
        assertEquals(
            "qa_work",
            f.hits("AUSSERER kürz Sommer", types("activity")).single().getString("id"),
        )
        assertEquals(
            "qa_work",
            f.hits("Musterstrasse hecke", types("activity")).single().getString("id"),
        )
        assertTrue(f.hits("hecke nonexistent", types("activity")).isEmpty())
        assertTrue(f.hits("qa_hidden_hash").isEmpty())
        assertTrue(f.hits("qa_hidden_source").isEmpty())
        assertTrue(f.hits("qa_work").isEmpty())
        assertEquals(before, f.master.toString())
        work.put("activity", "QA Rasen mähen")
        assertTrue(f.hits("hecke", types("activity")).isEmpty())
        assertEquals(
            "qa_work",
            f.hits("rasen", types("activity")).single().getJSONObject("target").getString("id"),
        )
    }

    @Test
    fun lcrAndNotesNeverDuplicateTheOriginalWorkInGlobalSearch() {
        val f = Fixture()
        f.add(f.master, "worklogs", "qa_marked")
            .put("activity", "QA Hecke")
            .put("notes", "QA Hecke sorgfältig")
            .put("work_date", "2026-11-10")
        f.add(f.master, "worklogs", "qa_regular")
            .put("activity", "QA Hecke")
            .put("work_date", "2026-11-09")
        f.lcr.add("qa_marked")
        val hits = f.hits("Hecke")
        assertEquals(2, hits.size)
        assertEquals(1, hits.count { it.getBoolean("lcr") })
        assertEquals("qa_marked", f.hits("Hecke", filter("lcr" to true)).single().getString("id"))
        assertEquals("qa_regular", f.hits("Hecke", filter("lcr" to false)).single().getString("id"))
        val note = f.hits("sorgfältig", types("note")).single()
        assertEquals("activity", note.getJSONObject("target").getString("type"))
        assertEquals("qa_marked", note.getJSONObject("target").getString("id"))
        assertFalse(note.has("record_hash"))
        f.lcr.clear()
        assertTrue(f.hits("Hecke", filter("lcr" to true)).isEmpty())
        assertEquals(2, f.hits("Hecke", filter("status" to "open")).size)
        f.legacyLabor.add("qa_regular")
        assertEquals(
            "qa_marked",
            f.hits("Hecke", filter("status" to "open")).single().getString("id"),
        )
    }

    @Test
    fun customersRecipientsPropertiesAndOptionalPlanningRemainSeparate() {
        val f = Fixture()
        f.add(f.master, "customers", "qa_site")
            .put("name", "QA Einsatzkunde")
            .put("city", "QA Beispielort")
        f.add(f.master, "customers", "qa_recipient").put("name", "QA Rechnungsempfänger")
        f.add(f.master, "worklogs", "qa_work")
            .put("activity", "QA Schnitt")
            .put("customer_id", "qa_site")
            .put("billing_customer_id", "qa_recipient")
            .put("property_id", "qa_property")
            .put("work_date", "2026-10-12")
        assertEquals(
            1,
            f.hits(
                    "Schnitt",
                    filter(
                        "customer_id" to "qa_site",
                        "billing_customer_id" to "qa_recipient",
                        "property_id" to "qa_property",
                    ),
                )
                .size,
        )
        assertTrue(f.hits("Schnitt", filter("customer_id" to "qa_recipient")).isEmpty())
        assertTrue(f.hits("Schnitt", filter("billing_customer_id" to "qa_site")).isEmpty())
        assertEquals(
            "qa_site",
            f.hits("", filter("types" to JSONArray().put("customer"), "open_work" to true))
                .single()
                .getString("id"),
        )
        f.add(f.master, "appointments", "qa_untimed")
            .put("date", "2026-11-11")
            .put("starts_at", "")
            .put("title", "")
        f.add(f.master, "appointments", "qa_timed")
            .put("title", "QA Termin")
            .put("customer_id", "qa_site")
            .put("starts_at", "2026-11-11T09:00:00+01:00")
        val untimed =
            f.hits(
                    "",
                    filter(
                        "types" to JSONArray().put("appointment"),
                        "without_customer" to true,
                        "without_time" to true,
                    ),
                )
                .single()
        assertEquals("qa_untimed", untimed.getString("id"))
        assertEquals("Termin", untimed.getString("title"))
        assertEquals("planned", untimed.getString("date_basis"))
        assertEquals("2026-11-11", untimed.getString("date"))
    }

    @Test
    fun photosAndPositionNamesSearchMetadataAndOpenTheirOwnOriginal() {
        val f = Fixture()
        f.add(f.master, "customers", "qa_site").put("name", "QA Gartenkunde")
        f.add(f.master, "worklogs", "qa_work")
            .put("activity", "QA Heckenschnitt")
            .put("customer_id", "qa_site")
        f.add(f.master, "photos", "qa_photo")
            .put("caption", "QA vorher")
            .put("filename", "qa_hecke.jpg")
            .put("worklog_id", "qa_work")
            .put("capture_time", "2026-11-10T23:30:00Z")
            .put("storage_name", "qa_secret_internal_path")
        val photo =
            f.hits(
                    "vorher Gartenkunde",
                    filter(
                        "types" to JSONArray().put("photo"),
                        "work_record_id" to "qa_work",
                        "from_date" to "2026-11-11",
                    ),
                )
                .single()
        assertEquals("photo", photo.getJSONObject("target").getString("type"))
        assertEquals("qa_photo", photo.getJSONObject("target").getString("id"))
        assertTrue(f.hits("qa_secret_internal_path").isEmpty())
        f.add(f.master, "offers", "qa_offer")
            .put("title", "QA Angebot")
            .put("positions", JSONArray().put(filter("description" to "QA Obstbaum schneiden")))
        assertEquals(
            "qa_offer",
            f.hits("obstbaum schneiden", types("offer")).single().getString("id"),
        )
    }

    @Test
    fun financialDatesUseTheRequestedBasisAndUnknownAmountsStayUnknown() {
        val f = Fixture()
        f.add(f.master, "worklogs", "qa_work")
            .put("activity", "QA Finanztest")
            .put("work_date", "2026-10-12")
        f.add(f.ledger, "records", "qa_invoice")
            .put("title", "QA Finanztest")
            .put("kind", "invoice")
            .put("status", "confirmed")
            .put("issue_date", "2026-11-10")
            .put("total_minor", 24000)
            .put("recipient", filter("name" to "QA Empfänger"))
        f.add(f.ledger, "payments", "qa_payment")
            .put("settlement_id", "qa_invoice")
            .put("date", "2026-12-05")
            .put("amount_minor", 10000)
        assertEquals(
            "qa_work",
            f.hits(
                    "Finanztest",
                    filter(
                        "date_basis" to "work",
                        "from_date" to "2026-10-01",
                        "to_date" to "2026-10-31",
                    ),
                )
                .single()
                .getString("id"),
        )
        val document =
            f.hits(
                    "Finanztest",
                    filter(
                        "date_basis" to "document",
                        "from_date" to "2026-11-01",
                        "to_date" to "2026-11-30",
                        "amount_from" to "239,99",
                        "amount_to" to "240.00",
                    ),
                )
                .single()
        assertEquals("qa_invoice", document.getString("id"))
        assertEquals("240.00", document.getString("amount"))
        val paid =
            f.hits(
                    "Finanztest",
                    filter(
                        "date_basis" to "payment",
                        "from_date" to "2026-12-01",
                        "to_date" to "2026-12-31",
                        "status" to "partial",
                    ),
                )
                .single()
        assertEquals("2026-12-05", paid.getString("date"))
        assertEquals("payment", paid.getString("date_basis"))
        assertTrue(
            f.hits("Finanztest", filter("date_basis" to "payment", "to_date" to "2026-11-30"))
                .isEmpty()
        )
        assertTrue(
            f.hits(
                    "Finanztest",
                    filter("types" to JSONArray().put("activity"), "amount_from" to "0"),
                )
                .isEmpty()
        )
    }

    @Test
    fun explicitAppointmentLinksDeduplicateButSimilarIndependentVisitsDoNot() {
        val f = Fixture()
        f.add(f.master, "appointments", "qa_appointment")
            .put("title", "QA Gartenbesuch")
            .put("date", "2026-11-11")
        f.add(f.local, "visit", "qa_linked")
            .put("title", "QA Gartenbesuch")
            .put("appointment_id", "qa_appointment")
        f.add(f.local, "visit", "qa_independent")
            .put("title", "QA Gartenbesuch")
            .put("date", "2026-11-11")
        assertEquals(
            setOf("qa_appointment", "qa_independent"),
            f.hits("Gartenbesuch").map { it.getString("id") }.toSet(),
        )
        assertEquals(
            setOf("qa_linked", "qa_independent"),
            f.hits("Gartenbesuch", types("visit")).map { it.getString("id") }.toSet(),
        )
    }

    @Test
    fun collectiveInvoicesFindEachExplicitSiteCustomerWithoutChangingTheRecipient() {
        val f = Fixture()
        f.add(f.master, "customers", "qa_a").put("name", "QA Rosenkunde")
        f.add(f.master, "customers", "qa_b").put("name", "QA Baumkunde")
        f.add(f.master, "customers", "qa_recipient").put("name", "QA Verwaltung")
        f.add(f.master, "worklogs", "qa_a_work")
            .put("customer_id", "qa_a")
            .put("billing_customer_id", "qa_recipient")
        f.add(f.master, "worklogs", "qa_b_work")
            .put("customer_id", "qa_b")
            .put("billing_customer_id", "qa_recipient")
        f.add(f.ledger, "records", "qa_collective")
            .put("kind", "invoice")
            .put("status", "confirmed")
            .put("billing_customer_id", "qa_recipient")
            .put(
                "sources",
                JSONArray()
                    .put(filter("collection" to "worklogs", "id" to "qa_a_work"))
                    .put(filter("collection" to "worklogs", "id" to "qa_b_work")),
            )
        assertEquals(
            "qa_collective",
            f.hits(
                    "Baumkunde",
                    filter(
                        "types" to JSONArray().put("settlement"),
                        "customer_id" to "qa_b",
                        "billing_customer_id" to "qa_recipient",
                    ),
                )
                .single()
                .getString("id"),
        )
        assertTrue(
            f.hits(
                    "Baumkunde",
                    filter(
                        "types" to JSONArray().put("settlement"),
                        "customer_id" to "qa_recipient",
                    ),
                )
                .isEmpty()
        )
    }

    @Test
    fun legacyUnzonedTimestampsKeepTheirExplicitCalendarDayWithoutInventingAnOffset() {
        val f = Fixture()
        for ((id, stamp) in
            listOf(
                "qa_local_space" to "2026-10-25 23:30:00",
                "qa_local_iso" to "2026-10-25T23:30:00",
                "qa_utc" to "2026-10-25T23:30:00Z",
                "qa_invalid" to "2026-02-30 23:30:00",
            )) f.add(f.local, "inquiry", id).put("title", "QA Kalender").put("received_at", stamp)
        val before = f.local.toString()
        val hits =
            f.hits("Kalender", filter("from_date" to "2026-10-25", "to_date" to "2026-10-25"))
        assertEquals(
            setOf("qa_local_space", "qa_local_iso"),
            hits.map { it.getString("id") }.toSet(),
        )
        assertTrue(hits.all { it.getString("date") == "2026-10-25" })
        assertEquals(
            "qa_utc",
            f.hits("Kalender", filter("from_date" to "2026-10-26", "to_date" to "2026-10-26"))
                .single()
                .getString("id"),
        )
        assertTrue(
            f.hits("Kalender", filter("from_date" to "2026-02-01", "to_date" to "2026-02-28"))
                .isEmpty()
        )
        assertEquals(before, f.local.toString())
    }

    @Test
    fun operationalFiltersUseStoredPricesDueDatesAndArchiveState() {
        val f = Fixture()
        f.add(f.local, "equipment", "qa_due")
            .put("name", "QA Mäher")
            .put("maintenance_due", "2026-11-10")
        f.add(f.local, "equipment", "qa_later")
            .put("name", "QA Mäher")
            .put("maintenance_due", "2026-12-10")
        assertEquals(
            "qa_due",
            f.hits("Mäher", filter("maintenance_due" to true)).single().getString("id"),
        )
        f.add(f.local, "material", "qa_material")
            .put("name", "QA Kompost")
            .put("category", "QA Erde")
            .put("billing_price_minor", 1250)
        assertEquals(
            "qa_material",
            f.hits(
                    "Kompost",
                    filter(
                        "category" to "QA Erde",
                        "amount_from" to "12,50",
                        "amount_to" to "12.50",
                    ),
                )
                .single()
                .getString("id"),
        )
        f.add(f.master, "customers", "qa_inactive")
            .put("name", "QA Altbestand")
            .put("city", "QA Beispielort")
            .put("active", 0)
        assertTrue(f.hits("Altbestand").isEmpty())
        val archived =
            f.hits("Altbestand", filter("archived" to true, "city" to "beispiel")).single()
        assertTrue(archived.getBoolean("archived"))
        assertEquals("qa_inactive", archived.getString("id"))
    }

    @Test
    fun resultGroupsAndPagesKeepStableOriginalTargetsAndRejectInvalidFilters() {
        val f = Fixture()
        f.add(f.master, "customers", "qa_customer")
            .put("name", "QA Gleich")
            .put("phone", "+43 000 0000001")
        f.add(f.local, "property", "qa_property")
            .put("name", "QA Gleich")
            .put("customer_id", "qa_customer")
        f.add(f.master, "photos", "qa_deleted").put("caption", "QA Gleich").put("deleted", true)
        val before = f.master.toString()
        val result = f.search("Gleich", filter("offset" to 1, "limit" to 1))
        assertEquals(2, result.getInt("total"))
        assertEquals("property", result.getJSONArray("items").getJSONObject(0).getString("type"))
        assertEquals(2, result.getJSONArray("groups").length())
        assertEquals(
            "+43 000 0000001",
            f.hits("Gleich", types("customer")).single().getString("phone"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            f.search("", filter("from_date" to "2026-12-01", "to_date" to "2026-11-01"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            f.search("", filter("amount_from" to "12", "amount_to" to "11"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            f.search("", filter("amount_from" to "unknown"))
        }
        assertEquals(before, f.master.toString())
    }
}
