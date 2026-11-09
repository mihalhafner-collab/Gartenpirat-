package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PropertyTimelineTest {
    @Test
    fun visitEditorUsesThePublishedCreateAndUpdateContracts() {
        val context =
            JSONObject().put("customer_id", "qa_customer").put("property_id", "qa_property")
        val edit = RevierEdit.create("qa_visit_new", "visit", null, context)
        edit.change("title", "QA vom Objekt geplanter Einsatz")
        val original = edit.row().toString()
        val create = revierArguments(edit, edit.row())
        assertEquals("plan_visit", create.getString("tool"))
        val input = create.getJSONObject("args")
        assertEquals("qa_property", input.getString("property_id"))
        assertEquals("QA vom Objekt geplanter Einsatz", input.getString("title"))
        assertEquals(60, input.getInt("planned_minutes"))
        assertFalse(input.has("status"))
        McpSchema.validate(input, LocalWorkflowTools.schema("plan_visit"))
        assertEquals(original, edit.row().toString())
        val existing = JSONObject(original).put("id", "qa_visit").put("record_hash", "a".repeat(64))
        val updateEdit = RevierEdit.create("qa_visit_edit", "visit", existing, JSONObject())
        updateEdit.change("status", "completed")
        updateEdit.change("reason", "QA erledigt")
        val update = revierArguments(updateEdit, updateEdit.row())
        assertEquals("update_visit", update.getString("tool"))
        val updateInput = update.getJSONObject("args")
        assertEquals("completed", updateInput.getJSONObject("updates").getString("status"))
        assertFalse(updateInput.getJSONObject("updates").has("property_id"))
        McpSchema.validate(updateInput, LocalWorkflowTools.schema("update_visit"))
    }

    @Test
    fun contextIdentityIgnoresTimerTicksAndTracksOnlyLinksAndPeriod() {
        val args =
            JSONObject()
                .put("customer_id", "qa_customer")
                .put("order_id", "qa_order")
                .put("worked_ms", 1000)
                .put("activity", "QA Text")
        val before = args.toString()
        val key = PropertyTimelineRules.contextIdentity(args)
        assertEquals(
            key,
            PropertyTimelineRules.contextIdentity(
                JSONObject(before)
                    .put("worked_ms", 2000)
                    .put("activity", "QA Text ergänzt")
                    .put("revision_hash", "qa_tick")
            ),
        )
        assertNotEquals(
            key,
            PropertyTimelineRules.contextIdentity(
                JSONObject(before).put("order_id", "qa_other_order")
            ),
        )
        assertNotEquals(
            key,
            PropertyTimelineRules.contextIdentity(JSONObject(before).put("date_from", "2026-11-09")),
        )
        assertEquals(before, args.toString())
    }

    @Test
    fun inquiryOrderLinkUsesThePublishedUpdateContract() {
        val create =
            JSONObject()
                .put("request_id", "qa_inquiry_create")
                .put("customer_id", "qa_customer")
                .put("property_id", "qa_property")
                .put("title", "QA Anfrage")
        McpSchema.validate(create, LocalWorkflowTools.schema("create_inquiry"))
        var rejected = false
        try {
            McpSchema.validate(
                JSONObject(create.toString()).put("order_id", "qa_order"),
                LocalWorkflowTools.schema("create_inquiry"),
            )
        } catch (_: PixelMcpWriteTools.Rejected) {
            rejected = true
        }
        assertTrue(rejected)
        McpSchema.validate(
            JSONObject()
                .put("request_id", "qa_inquiry_link")
                .put("inquiry_id", "qa_inquiry")
                .put("expected_record_hash", "a".repeat(64))
                .put("updates", JSONObject().put("order_id", "qa_order")),
            LocalWorkflowTools.schema("update_inquiry"),
        )
    }

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
                for (key in listOf("properties", "inquiries", "visits", "care_plans")) put(
                    key,
                    JSONArray(),
                )
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
    fun legacyObjectScopesAndInspectionLinksRemainReadable() {
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
    fun actualInquiryAndOfferBackReferencesConnectOrdersAppointmentsAndWork() {
        val f = Fixture()
        f.row(f.revier, "inquiries", "qa_inquiry").put("offer_id", "qa_offer")
        f.row(f.master, "offers", "qa_offer", "")
            .put("status", "accepted")
            .put("converted_order_id", "qa_order")
            .put("positions", JSONArray().put(JSONObject().put("title", "QA Leistung")))
        f.row(f.master, "orders", "qa_order", "")
        f.row(f.master, "appointments", "qa_appointment", "")
            .put("order_id", "qa_order")
            .put("scope_refs", JSONArray().put("qa_offer#0"))
            .put("starts_at", "2026-11-09T08:00:00Z")
        f.row(f.master, "worklogs", "qa_work", "").put("order_id", "qa_order")
        f.row(f.master, "worklogs", "qa_same_customer_only", "")
        assertEquals(
            setOf("qa_inquiry", "qa_offer", "qa_order", "qa_appointment", "qa_work"),
            f.items().map { it.getString("id") }.toSet(),
        )
    }

    @Test
    fun linkedContextReadsKnownOrderAndOfferParentsWithoutChangingInputs() {
        val f = Fixture()
        f.row(f.revier, "properties", "qa_property", "")
        f.row(f.revier, "inquiries", "qa_inquiry").put("offer_id", "qa_offer")
        f.row(f.master, "offers", "qa_offer", "").put("converted_order_id", "qa_order")
        f.row(f.master, "orders", "qa_order", "")
        for (args in
            listOf(
                JSONObject().put("order_id", "qa_order"),
                JSONObject().put("offer_id", "qa_offer"),
            )) {
            val original = args.toString()
            val before = f.master.toString() + f.revier.toString()
            val result = PropertyTimelineRules.linkedContext(args, f.master, f.revier)
            assertEquals("qa_property", result.getString("property_id"))
            assertEquals("qa_order", result.getString("order_id"))
            assertEquals("qa_same_customer", result.getString("customer_id"))
            assertFalse(result.optBoolean("context_conflict"))
            assertEquals(original, args.toString())
            assertEquals(before, f.master.toString() + f.revier.toString())
        }
    }

    @Test
    fun linkedContextNeverAssignsAnObjectFromCustomerAndKeepsExplicitLinks() {
        val f = Fixture()
        f.row(f.revier, "properties", "qa_property", "")
        f.row(f.revier, "properties", "qa_other", "")
        f.row(f.master, "orders", "qa_order")
        val customerOnly =
            PropertyTimelineRules.linkedContext(
                JSONObject()
                    .put("customer_id", "qa_same_customer")
                    .put("property_id", JSONObject.NULL),
                f.master,
                f.revier,
            )
        assertFalse(customerOnly.has("property_id"))
        val explicit =
            PropertyTimelineRules.linkedContext(
                JSONObject().put("order_id", "qa_order").put("property_id", "qa_other"),
                f.master,
                f.revier,
            )
        assertEquals("qa_other", explicit.getString("property_id"))
        assertTrue(explicit.optBoolean("context_conflict"))
    }

    @Test
    fun linkedContextShowsAmbiguityAndRejectsParentsOfAnotherCustomer() {
        val f = Fixture()
        f.row(f.revier, "properties", "qa_property", "")
        f.row(f.revier, "properties", "qa_other", "")
        f.row(f.master, "orders", "qa_order", "")
        f.row(f.revier, "inquiries", "qa_one").put("order_id", "qa_order")
        f.row(f.revier, "inquiries", "qa_two", "qa_other").put("order_id", "qa_order")
        val ambiguous =
            PropertyTimelineRules.linkedContext(
                JSONObject().put("order_id", "qa_order"),
                f.master,
                f.revier,
            )
        assertTrue(ambiguous.optBoolean("context_conflict"))
        assertFalse(ambiguous.has("property_id"))
        val foreign =
            PropertyTimelineRules.linkedContext(
                JSONObject()
                    .put("order_id", "qa_order")
                    .put("customer_id", "qa_different_customer"),
                f.master,
                f.revier,
            )
        assertTrue(foreign.optBoolean("context_conflict"))
        assertFalse(foreign.has("property_id"))
        f.row(f.master, "worklogs", "qa_unknown_work", "").put("order_id", "qa_order")
        f.row(f.master, "appointments", "qa_unknown_appointment", "").put("order_id", "qa_order")
        assertFalse(
            f.items().any {
                it.optString("id") in setOf("qa_unknown_work", "qa_unknown_appointment")
            }
        )
        f.revier.getJSONArray("inquiries").getJSONObject(0).put("offer_id", "qa_scoped_offer")
        f.row(f.master, "offers", "qa_scoped_offer", "")
            .put("status", "accepted")
            .put("converted_order_id", "qa_order")
            .put("positions", JSONArray().put(JSONObject().put("title", "QA Leistung")))
        f.row(f.master, "appointments", "qa_scoped_appointment", "")
            .put("order_id", "qa_order")
            .put("scope_refs", JSONArray().put("qa_scoped_offer#0"))
        assertEquals(
            setOf("qa_scoped_appointment"),
            f.items()
                .filter { it.optString("kind") == "appointment" }
                .map { it.getString("id") }
                .toSet(),
        )
        val scoped =
            PropertyTimelineRules.linkedContext(
                JSONObject().put("appointment_id", "qa_scoped_appointment"),
                f.master,
                f.revier,
            )
        assertEquals("qa_property", scoped.getString("property_id"))
        assertFalse(scoped.optBoolean("context_conflict"))
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
