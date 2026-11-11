package at.hausgarten.service

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EisbaerPlanningTest {
    private fun state() =
        JSONObject()
            .put("customers", JSONArray())
            .put("orders", JSONArray())
            .put("appointments", JSONArray())

    private fun prepare(
        args: JSONObject,
        snapshot: JSONObject = state(),
        request: String = "qa_optional_plan",
    ) =
        AppointmentRules.prepare(
            "create_appointment",
            args,
            snapshot,
            request,
            "2026-11-11T09:00:00Z",
        )

    @Test
    fun explicitEmptySaveCreatesAnUntimedOriginalWithoutInventingCustomerTitleOrEnd() {
        val args = JSONObject().put("request_id", "qa_optional_plan")
        McpSchema.validate(args, AppointmentTools.schema("create_appointment"))
        val before = state()
        val original = before.toString()
        val row = prepare(args, before)
        assertEquals(original, before.toString())
        assertEquals("2026-11-11", row.getString("date"))
        assertEquals("", row.getString("starts_at"))
        assertEquals("", row.getString("ends_at"))
        assertEquals("", row.getString("title"))
        assertEquals("", row.getString("customer_id"))
        assertEquals("Termin", AppointmentRules.title(row))
        assertNull(PlanningBoardRules.start("appointment", row))
        assertEquals(LocalDate.parse("2026-11-11"), PlanningBoardRules.day("appointment", row))
        assertEquals(row.getString("id"), prepare(args).getString("id"))
        assertFalse(
            AppointmentAvailability.assess(row, JSONArray(), JSONObject()).getBoolean("app_checked")
        )
    }

    @Test
    fun selectedDayAndLaterEditsRetainTheSameIdentity() {
        val row = prepare(JSONObject().put("date", "2026-12-05"))
        val snapshot = state().put("appointments", JSONArray().put(row))
        val update =
            JSONObject()
                .put("appointment_id", row.getString("id"))
                .put("expected_record_hash", AppointmentRules.hash(row))
                .put(
                    "updates",
                    JSONObject().put("title", "[TEST] Neuer Titel").put("date", "2026-12-06"),
                )
        val saved =
            AppointmentRules.prepare(
                "update_appointment",
                update,
                snapshot,
                "qa_optional_update",
                "2026-11-11T09:01:00Z",
            )
        assertEquals(row.getString("id"), saved.getString("id"))
        assertEquals("2026-12-06", saved.getString("date"))
        assertEquals("", saved.getString("ends_at"))
        assertEquals(1, snapshot.getJSONArray("appointments").length())
        assertEquals("", row.getString("title"))
    }

    @Test
    fun omittedCustomerCanBeFilledLaterAndAnExistingAssignmentStaysProtected() {
        val row = prepare(JSONObject().put("date", "2026-12-05"))
        val snapshot =
            state()
                .put("appointments", JSONArray().put(row))
                .put(
                    "customers",
                    JSONArray()
                        .put(JSONObject().put("id", "qa_plan_customer").put("active", 1))
                        .put(JSONObject().put("id", "qa_plan_other").put("active", 1)),
                )
        val args =
            JSONObject()
                .put("appointment_id", row.getString("id"))
                .put("expected_record_hash", AppointmentRules.hash(row))
                .put("updates", JSONObject().put("customer_id", "qa_plan_customer"))
        val saved =
            AppointmentRules.prepare(
                "update_appointment",
                args,
                snapshot,
                "qa_plan_assign_customer",
                "2026-11-11T09:01:00Z",
            )
        assertEquals(row.getString("id"), saved.getString("id"))
        assertEquals("qa_plan_customer", saved.getString("customer_id"))
        snapshot.put("appointments", JSONArray().put(saved))
        rejects {
            AppointmentRules.prepare(
                "update_appointment",
                JSONObject()
                    .put("appointment_id", saved.getString("id"))
                    .put("expected_record_hash", AppointmentRules.hash(saved))
                    .put("updates", JSONObject().put("customer_id", "qa_plan_other")),
                snapshot,
                "qa_plan_reassign",
                "2026-11-11T09:02:00Z",
            )
        }
        val work =
            JSONObject()
                .put("id", "qa_plan_actual_work")
                .put("appointment_id", row.getString("id"))
                .put("customer_id", "qa_plan_customer")
        AppointmentRules.worklog(work, null, state().put("appointments", JSONArray().put(row)))
        assertEquals("", row.getString("customer_id"))
    }

    @Test
    fun invalidPresentDatesAndAnEndWithoutStartAreRejected() {
        rejects { prepare(JSONObject().put("date", "2026-02-30")) }
        rejects {
            prepare(JSONObject().put("date", "2026-11-11").put("ends_at", "2026-11-11T10:00:00Z"))
        }
        rejects {
            prepare(
                JSONObject()
                    .put("starts_at", "2026-11-11T10:00:00Z")
                    .put("ends_at", "2026-11-11T09:00:00Z")
            )
        }
        val row = prepare(JSONObject().put("starts_at", "2026-11-11T10:00:00Z"))
        assertTrue(AppointmentRules.openEnd(row))
        assertEquals("", row.getString("ends_at"))
    }

    @Test
    fun explicitLinksDeduplicateButSimilarIndependentSourcesRemainSeparate() {
        val appointment =
            prepare(JSONObject().put("date", "2026-12-05").put("title", "[TEST] Termin"))
        val visit =
            JSONObject()
                .put("id", "qa_linked_visit")
                .put("appointment_id", appointment.getString("id"))
                .put("date", "2026-12-05")
                .put("status", "planned")
                .put("title", "[TEST] Termin")
        val independent =
            JSONObject(visit.toString()).put("id", "qa_independent_visit").put("appointment_id", "")
        val appointments = JSONArray().put(appointment)
        val visits = JSONArray().put(visit).put(independent)
        val before = visits.toString()
        assertEquals(
            mapOf("qa_linked_visit" to appointment.getString("id")),
            PlanningBoardRules.aliases(appointments, visits),
        )
        val entries = PlanningBoardRules.tourEntries(appointments, visits, "2026-12-05")
        assertEquals(2, entries.size)
        assertEquals(
            setOf("appointments", "visits"),
            entries.map { it.getString("source_collection") }.toSet(),
        )
        assertEquals(before, visits.toString())
        appointment.put("date", "2026-12-06")
        assertEquals(1, PlanningBoardRules.tourEntries(appointments, visits, "2026-12-05").size)
    }

    @Test
    fun generatedCareVisitsRequireAnExplicitAdoptionWithoutChangingTheirIdentity() {
        val legacy =
            JSONObject()
                .put("id", "qa_legacy_care_visit")
                .put("care_plan_id", "qa_original_care")
                .put("status", "planned")
                .put("date", "2026-12-05")
                .put("manual_override", true)
        val before = legacy.toString()
        assertEquals(
            0,
            PlanningBoardRules.tourEntries(JSONArray(), JSONArray().put(legacy), "2026-12-05").size,
        )
        assertEquals(before, legacy.toString())
        val selected = JSONObject(legacy.toString()).put("planning_adopted", true)
        val entries =
            PlanningBoardRules.tourEntries(JSONArray(), JSONArray().put(selected), "2026-12-05")
        assertEquals(1, entries.size)
        assertEquals(legacy.getString("id"), entries.single().getString("id"))
        assertEquals(legacy.getString("id"), entries.single().getString("source_id"))
        assertEquals("qa_original_care", entries.single().getString("care_plan_id"))
    }

    @Test
    fun untimedCalendarProjectionUsesDateWithoutInventingAnInstant() {
        val row = prepare(JSONObject().put("date", "2026-12-05"))
        val remote = CalendarRules.event(row)
        assertEquals("2026-12-05", remote.getJSONObject("start").getString("date"))
        assertFalse(remote.getJSONObject("start").has("dateTime"))
        assertFalse(remote.getBoolean("endTimeUnspecified"))
        assertEquals("Termin", remote.getString("summary"))
        val unchanged = CalendarRules.changes(row, row, remote)
        assertEquals("", unchanged.getString("title"))
        assertEquals("", unchanged.getString("starts_at"))
        assertEquals("", unchanged.getString("ends_at"))
        assertTrue(AppointmentRules.pending(row, Instant.parse("2026-12-05T08:00:00Z")))
        assertFalse(AppointmentRules.pending(row, Instant.parse("2026-12-06T08:00:00Z")))
    }

    @Test
    fun sharedTourReadsOriginalAppointmentsAndFollowsMovesWithoutCreatingVisitsOrWork() {
        val memory = RevierDomainTests.Memory()
        val first =
            prepare(JSONObject().put("date", "2026-12-05"), request = "qa_shared_tour_first")
        val second =
            prepare(JSONObject().put("date", "2026-12-05"), request = "qa_shared_tour_second")
        memory.snapshot.put("appointments", JSONArray().put(first).put(second))
        val workBefore = memory.snapshot.getJSONArray("worklogs").toString()
        val service = RevierApplicationService(memory, RevierDomainTests.CLOCK)
        val args = JSONObject().put("date", "2026-12-05")
        val initial = service.query("get_day_tour", args)
        assertEquals(2, initial.getJSONArray("stops").length())
        assertEquals(0, service.query("list_visits", JSONObject()).getInt("total"))
        val reversed =
            JSONArray(
                (0 until initial.getJSONArray("stops").length())
                    .map { initial.getJSONArray("stops").getJSONObject(it).getString("id") }
                    .reversed()
            )
        val change =
            JSONObject()
                .put("request_id", "qa_shared_tour_order")
                .put("date", "2026-12-05")
                .put("expected_record_hash", initial.getString("record_hash"))
                .put("visit_ids", reversed)
        service.execute("set_day_tour", change)
        assertTrue(service.execute("set_day_tour", change).getBoolean("replayed"))
        assertEquals(
            reversed.getString(0),
            service
                .query("get_day_tour", args)
                .getJSONArray("stops")
                .getJSONObject(0)
                .getString("id"),
        )
        first.put("date", "2026-12-06")
        assertEquals(1, service.query("get_day_tour", args).getJSONArray("stops").length())
        assertEquals(
            1,
            service
                .query("get_day_tour", JSONObject().put("date", "2026-12-06"))
                .getJSONArray("stops")
                .length(),
        )
        assertEquals(workBefore, memory.snapshot.getJSONArray("worklogs").toString())
        assertEquals(0, service.query("list_visits", JSONObject()).getInt("total"))
        assertEquals("PASS", service.validateRelationships().getString("status"))
    }

    @Test
    fun confirmedAutosaveSurvivesFailedReadbackAndEditsTheSameOriginalAfterRetry() = runBlocking {
        val edit =
            PlanningEdit.create(
                "planning:appointment:new",
                null,
                JSONObject().put("date", "2026-12-05"),
            )
        assertTrue(edit.change("title", "[TEST] Ein Termin"))
        val action = requireNotNull(edit.freeze())
        val args = action.getJSONObject("args")
        val request = args.getString("request_id")
        val snapshot = state()
        val original = prepare(args, snapshot, request)
        OwnerJournalRules.replace(snapshot, "appointments", original.getString("id"), original)
        val written = JSONObject().put("appointment_id", original.getString("id"))
        var saved: JSONObject? = null
        try {
            edit.readCommitted(
                written,
                persist = { saved = JSONObject(it.toString()) },
                read = { throw IllegalStateException("[TEST] Rücklesen fehlgeschlagen") },
            )
            fail("Readback failure must retain the pending operation")
        } catch (_: IllegalStateException) {}
        assertEquals(original.getString("id"), edit.original.getString("id"))
        assertEquals(
            request,
            requireNotNull(edit.pending).getJSONObject("args").getString("request_id"),
        )
        val retained = requireNotNull(saved)
        assertEquals(original.getString("id"), retained.getJSONObject("original").getString("id"))
        assertTrue(retained.has("pending") && !retained.isNull("pending"))
        val restored = PlanningEdit.restore(edit.key, retained)
        assertFalse(restored.change("title", "[TEST] Vorzeitig ändern"))
        val replay = requireNotNull(restored.freeze())
        assertEquals(action.getString("tool"), replay.getString("tool"))
        assertEquals(
            PixelMcpWriteTools.hash(action.getJSONObject("args")),
            PixelMcpWriteTools.hash(replay.getJSONObject("args")),
        )
        restored.readCommitted(
            written,
            persist = { saved = it },
            read = { id ->
                assertEquals(original.getString("id"), id)
                JSONObject()
                    .put("appointment", original)
                    .put("record_hash", AppointmentRules.hash(original))
            },
        )
        assertNull(restored.pending)
        assertTrue(restored.change("title", "[TEST] Derselbe Termin geändert"))
        val update = requireNotNull(restored.freeze())
        assertEquals("update_appointment", update.getString("tool"))
        assertEquals(
            original.getString("id"),
            update.getJSONObject("args").getString("appointment_id"),
        )
        val next =
            AppointmentRules.prepare(
                "update_appointment",
                update.getJSONObject("args"),
                snapshot,
                update.getJSONObject("args").getString("request_id"),
                "2026-11-11T09:01:00Z",
            )
        OwnerJournalRules.replace(snapshot, "appointments", next.getString("id"), next)
        assertEquals(1, snapshot.getJSONArray("appointments").length())
        assertEquals(
            original.getString("id"),
            snapshot.getJSONArray("appointments").getJSONObject(0).getString("id"),
        )
        assertEquals(
            "[TEST] Derselbe Termin geändert",
            snapshot.getJSONArray("appointments").getJSONObject(0).getString("title"),
        )
    }

    @Test
    fun removingTheTimeClearsItsOldDurationAndChangingTheDayNeedsNoTime() {
        val original =
            prepare(
                JSONObject()
                    .put("starts_at", "2026-12-05T08:00:00Z")
                    .put("ends_at", "2026-12-05T10:00:00Z")
            )
        val edit =
            PlanningEdit.create(
                "planning:appointment:" + original.getString("id"),
                JSONObject()
                    .put("appointment", original)
                    .put("record_hash", AppointmentRules.hash(original)),
                JSONObject(),
            )
        assertEquals("2.0", edit.values.getString("duration_hours"))
        assertTrue(edit.change("local_start", ""))
        assertEquals("", edit.values.getString("duration_hours"))
        assertTrue(edit.values.getBoolean("end_open"))
        assertEquals("2026-12-05", edit.businessValues().getString("date"))
        assertEquals("", edit.businessValues().getString("starts_at"))
        assertTrue(edit.change("local_start", "2026-12-06T"))
        val untimed = edit.businessValues()
        assertEquals("2026-12-06", untimed.getString("date"))
        assertEquals("", untimed.getString("starts_at"))
        assertEquals("", untimed.getString("ends_at"))
        assertEquals(
            original.getString("id"),
            requireNotNull(edit.freeze()).getJSONObject("args").getString("appointment_id"),
        )
    }

    private fun rejects(task: () -> Unit) {
        try {
            task()
            fail("Expected invalid present value to be rejected")
        } catch (_: RuntimeException) {}
    }
}
