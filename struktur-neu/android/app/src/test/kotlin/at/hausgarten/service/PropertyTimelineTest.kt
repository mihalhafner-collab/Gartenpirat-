package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PropertyTimelineTest {
    private class Fixture {
        val master =
            JSONObject().apply {
                for (key in
                    listOf(
                        "orders",
                        "offers",
                        "worklogs",
                        "photos",
                        "invoices",
                        "appointments",
                    )) put(key, JSONArray())
            }
        val revier =
            JSONObject().apply {
                for (key in listOf("inquiries", "visits", "care_plans")) put(key, JSONArray())
            }
        val ledger = JSONObject().put("records", JSONArray()).put("payments", JSONArray())

        fun row(
            source: JSONObject,
            collection: String,
            id: String,
            property: String = "qa_property",
        ) =
            JSONObject()
                .put("id", id)
                .put("customer_id", "qa_same_customer")
                .apply { if (property.isNotBlank()) put("property_id", property) }
                .also { source.getJSONArray(collection).put(it) }

        fun view(view: String = "history", offset: Int = 0, limit: Int = 50) =
            PropertyTimelineRules.build("qa_property", master, revier, ledger, view, offset, limit)

        fun items(view: String = "history") = SettlementRules.rows(view(view).getJSONArray("items"))
    }

    @Test
    fun completeExplicitWorkflowIsVisibleThroughPayment() {
        val f = Fixture()
        f.row(f.revier, "inquiries", "qa_inquiry").put("received_at", "2026-11-01")
        f.row(f.master, "offers", "qa_offer")
            .put("status", "issued")
            .put("issue_date", "2026-11-02")
        f.row(f.master, "orders", "qa_order")
            .put("status", "Erledigt")
            .put("created_at", "2026-11-03")
        f.row(f.revier, "visits", "qa_visit").put("status", "completed").put("date", "2026-11-04")
        f.row(f.master, "worklogs", "qa_work").put("work_date", "2026-11-04")
        f.row(f.master, "photos", "qa_photo").put("capture_time", "2026-11-04T09:00:00Z")
        f.row(f.ledger, "records", "qa_settlement", "")
            .put("status", "confirmed")
            .put("kind", "invoice")
            .put("issue_date", "2026-11-05")
            .put("total_minor", 1234)
            .put(
                "sources",
                JSONArray().put(JSONObject().put("collection", "worklogs").put("id", "qa_work")),
            )
        f.row(f.ledger, "payments", "qa_payment", "")
            .put("settlement_id", "qa_settlement")
            .put("date", "2026-11-06")
            .put("amount_minor", 1234)
        val items = f.items()
        assertEquals(
            listOf(
                "inquiry",
                "offer",
                "order",
                "visit",
                "worklog",
                "photo",
                "settlement",
                "payment",
            ),
            items.map { it.getString("kind") },
        )
        assertTrue(items.all { it.getBoolean("filled") })
        assertEquals("qa_settlement", items.last().getString("target_id"))
        assertEquals(1234, items.last().getLong("amount_minor"))
    }

    @Test
    fun sameCustomerDoesNotEstablishObjectMembership() {
        val f = Fixture()
        f.row(f.master, "worklogs", "qa_owned")
        f.row(f.master, "worklogs", "qa_customer_only", "")
        f.row(f.master, "worklogs", "qa_other_object", "qa_other")
        f.row(f.revier, "visits", "qa_other_visit", "qa_other")
        f.row(f.master, "photos", "qa_other_photo", "qa_other").put("worklog_id", "qa_owned")
        assertEquals(listOf("qa_owned"), f.items().map { it.getString("id") })
    }

    @Test
    fun knownParentLinksWorkButExplicitDifferentObjectWins() {
        val f = Fixture()
        f.row(f.master, "orders", "qa_order")
        f.row(f.revier, "visits", "qa_visit").put("order_id", "qa_order")
        f.row(f.master, "worklogs", "qa_order_work", "").put("order_id", "qa_order")
        f.row(f.master, "worklogs", "qa_visit_work", "").put("visit_id", "qa_visit")
        f.row(f.master, "worklogs", "qa_wrong_work", "qa_other").put("visit_id", "qa_visit")
        f.row(f.master, "photos", "qa_work_photo", "").put("worklog_id", "qa_visit_work")
        assertEquals(
            setOf("qa_order", "qa_visit", "qa_order_work", "qa_visit_work", "qa_work_photo"),
            f.items().map { it.getString("id") }.toSet(),
        )
    }

    @Test
    fun relatedParentDoesNotAssignItsUnlinkedSiblings() {
        val f = Fixture()
        f.row(f.master, "orders", "qa_related_order", "")
        f.row(f.master, "worklogs", "qa_own_work").put("order_id", "qa_related_order")
        f.row(f.master, "worklogs", "qa_unknown_sibling", "").put("order_id", "qa_related_order")
        assertEquals(
            setOf("qa_related_order", "qa_own_work"),
            f.items().map { it.getString("id") }.toSet(),
        )
    }

    @Test
    fun sharedInvoiceAndPaymentsStayDocumentAmountsWithoutObjectTotals() {
        val f = Fixture()
        f.row(f.master, "worklogs", "qa_work")
        f.row(f.ledger, "records", "qa_shared", "")
            .put("status", "confirmed")
            .put("total_minor", 9000)
            .put(
                "sources",
                JSONArray()
                    .put(JSONObject().put("collection", "worklogs").put("id", "qa_work"))
                    .put(JSONObject().put("collection", "worklogs").put("id", "qa_unrelated")),
            )
        f.row(f.ledger, "payments", "qa_part", "")
            .put("settlement_id", "qa_shared")
            .put("amount_minor", 4000)
        val view = f.view("money")
        val rows = SettlementRules.rows(view.getJSONArray("items"))
        assertEquals(2, rows.size)
        assertEquals(
            9000,
            rows.single { it.getString("kind") == "settlement" }.getLong("amount_minor"),
        )
        assertEquals(
            "document",
            rows.single { it.getString("kind") == "settlement" }.getString("amount_scope"),
        )
        assertEquals(
            "payment",
            rows.single { it.getString("kind") == "payment" }.getString("amount_scope"),
        )
        assertFalse(view.has("object_total_minor"))
        assertFalse(view.has("balance_minor"))
    }

    @Test
    fun unknownDatesAndStatusesAreNotInvented() {
        val f = Fixture()
        f.row(f.master, "orders", "qa_unknown_order").put("status", "Geplant")
        f.row(f.master, "offers", "qa_unknown_offer")
            .put("issue_date", "morgen")
            .put("status", "unknown")
        f.row(f.revier, "visits", "qa_future").put("date", "2026-11-20").put("status", "planned")
        val rows = f.items()
        assertTrue(rows.all { !it.getBoolean("filled") })
        assertEquals("2026-11-20", rows.first().getString("at"))
        assertTrue(rows.drop(1).all { it.getString("at").isEmpty() })
    }

    @Test
    fun paginationHasStableKeysAndAllEvents() {
        val f = Fixture()
        for (i in 0 until 62) f.row(f.master, "worklogs", "qa_work_$i")
            .put("work_date", "2026-11-01")
        val first = f.view(limit = 50)
        val last = f.view(offset = 50)
        assertEquals(62, first.getInt("total"))
        assertTrue(first.getBoolean("has_more"))
        assertFalse(last.getBoolean("has_more"))
        val keys =
            SettlementRules.rows(first.getJSONArray("items")) +
                SettlementRules.rows(last.getJSONArray("items"))
        assertEquals(62, keys.map { it.getString("key") }.toSet().size)
    }

    @Test
    fun readsPreserveInputsAndCareResultsAreIndependent() {
        val f = Fixture()
        f.row(f.revier, "care_plans", "qa_care").put("notes", "[TEST] Eigene Notiz")
        val before = f.master.toString() + f.revier.toString() + f.ledger.toString()
        val row = f.items("care").single().getJSONObject("record")
        row.put("notes", "[TEST] Nur Ergebnis ändern")
        assertEquals(before, f.master.toString() + f.revier.toString() + f.ledger.toString())
    }

    @Test
    fun legacyPaidFlagDoesNotInventPaymentOrCurrency() {
        val f = Fixture()
        f.row(f.master, "invoices", "qa_legacy")
            .put("gross_total", "12,34")
            .put("status", "paid")
            .put("paid", true)
        val rows = f.items("money")
        assertEquals(1, rows.size)
        assertEquals("invoice", rows.single().getString("kind"))
        assertEquals(1234, rows.single().getLong("amount_minor"))
        assertEquals("", rows.single().getString("currency"))
        assertEquals(0, f.view().getJSONObject("counts").getInt("payments"))
    }

    @Test
    fun fractionalMinorUnitsAreNotTruncated() {
        val f = Fixture()
        f.row(f.ledger, "records", "qa_fractional").put("total_minor", "12.34")
        assertFalse(f.items("money").single().has("amount_minor"))
    }

    @Test
    fun scopeReferencesAndInspectionLinksAreExplicit() {
        val f = Fixture()
        f.row(f.revier, "inquiries", "qa_inquiry").put("inspection_appointment_id", "qa_inspection")
        f.row(f.master, "appointments", "qa_inspection", "")
            .put("starts_at", "2026-11-09T08:00:00Z")
            .put("status", "confirmed")
        f.row(f.master, "appointments", "qa_scoped", "")
            .put("scope_refs", JSONArray().put("property:qa_property"))
            .put("starts_at", "2026-11-10T08:00:00Z")
        f.row(f.master, "appointments", "qa_not_scoped", "")
            .put("scope_refs", JSONArray().put("property:qa_property_extra"))
            .put("starts_at", "2026-11-10T08:00:00Z")
        assertEquals(
            setOf("qa_inspection", "qa_scoped"),
            f.items()
                .filter { it.getString("kind") == "appointment" }
                .map { it.getString("id") }
                .toSet(),
        )
    }

    @Test
    fun photoViewContainsMetadataAndDoesNotAlterTheSource() {
        val f = Fixture()
        val photo =
            f.row(f.master, "photos", "qa_photo")
                .put("caption", "[TEST] Hecke")
                .put("thumbnail_data", "[TEST] Inline-Vorschau")
        val before = photo.toString()
        assertFalse(f.items("photos").single().getJSONObject("record").has("thumbnail_data"))
        assertEquals(before, photo.toString())
    }
}
