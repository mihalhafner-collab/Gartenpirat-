package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** A legacy Visit hash never grants write authority over a separate Appointment original. */
class CanonicalPlanningUpdateTest {
    private class Fixture(link: Int) {
        val memory = RevierDomainTests.Memory()
        val service = RevierApplicationService(memory, RevierDomainTests.CLOCK)
        val property = RevierDomainTests.createProperty(service, "canonical_update")
        val visitId: String
        val appointment: JSONObject
        val key: String

        init {
            visitId =
                service
                    .execute(
                        "plan_visit",
                        JSONObject()
                            .put("request_id", "qa_canonical_visit_create")
                            .put("property_id", property.getString("id"))
                            .put("title", "[TEST] Verknüpfter Originaleinsatz")
                            .put("date", "2027-05-19")
                            .put("planned_minutes", 60),
                    )
                    .getString("visit_id")
            appointment =
                AppointmentRules.prepare(
                    "create_appointment",
                    JSONObject()
                        .put("date", "2027-05-19")
                        .put("title", "[TEST] Kanonischer Termin"),
                    memory.snapshot,
                    "qa_canonical_appointment",
                    "2027-05-18T09:00:00Z",
                )
            key = RevierRules.ROOT + "entity:visit:" + visitId
            val visit = JSONObject(memory.values.getValue(key))
            when (link) {
                0 -> visit.put("appointment_id", appointment.getString("id"))
                1 ->
                    visit
                        .put("source_collection", "appointments")
                        .put("source_id", appointment.getString("id"))
                2 -> appointment.put("visit_id", visitId)
                3 -> appointment.put("source_collection", "visits").put("source_id", visitId)
            }
            memory.values[key] = visit.toString()
            memory.snapshot.put("appointments", JSONArray().put(appointment))
        }

        fun visit() =
            service
                .query("get_visit", JSONObject().put("visit_id", visitId))
                .getJSONObject("record")

        fun change(request: String = "qa_canonical_legacy_update") =
            JSONObject()
                .put("request_id", request)
                .put("visit_id", visitId)
                .put("expected_record_hash", visit().getString("record_hash"))
                .put("updates", JSONObject().put("date", "2027-05-20"))
                .put("reason", "[TEST] Bewusstes Verschieben")
    }

    private fun rejected(action: () -> Unit): PixelMcpWriteTools.Rejected {
        try {
            action()
        } catch (failure: PixelMcpWriteTools.Rejected) {
            return failure
        }
        throw AssertionError("Expected write rejection")
    }

    @Test
    fun everyExplicitLinkDirectionRejectsLegacyUpdatesWithoutChangingEitherOriginal() {
        for (link in 0..3) {
            val f = Fixture(link)
            val before = f.memory.values.toString()
            val snapshot = f.memory.snapshot.toString()
            val args = f.change()
            repeat(2) {
                val failure = rejected { f.service.execute("update_visit", args) }
                assertEquals("CANONICAL_APPOINTMENT_REQUIRED", failure.code)
                for (envelope in listOf(McpErrors.rpc(failure), McpErrors.bridge(failure))) {
                    val detail = envelope.getJSONObject("error")
                    assertEquals(f.visitId, detail.getString("visit_id"))
                    assertEquals(f.appointment.getString("id"), detail.getString("appointment_id"))
                    assertEquals(
                        AppointmentRules.hash(f.appointment),
                        detail.getString("record_hash"),
                    )
                    assertEquals("update_appointment", detail.getString("next_tool"))
                    assertEquals("not_committed", detail.getString("commit_state"))
                }
            }
            assertEquals(before, f.memory.values.toString())
            assertEquals(snapshot, f.memory.snapshot.toString())
        }
    }

    @Test
    fun staleVisitProofIsRejectedBeforeSuggestingAnotherWrite() {
        val f = Fixture(0)
        val args = f.change().put("expected_record_hash", "0".repeat(64))
        val before = f.memory.values.toString() + f.memory.snapshot.toString()
        assertEquals("WRITE_CONFLICT", rejected { f.service.execute("update_visit", args) }.code)
        assertEquals(before, f.memory.values.toString() + f.memory.snapshot.toString())
    }

    @Test
    fun explicitCanonicalUpdateRequiresItsOwnCurrentProofAndMovesTheSingleProjection() {
        val f = Fixture(2)
        val beforeVisit = f.memory.values.getValue(f.key)
        val workBefore = f.memory.snapshot.getJSONArray("worklogs").toString()
        val originalHash = AppointmentRules.hash(f.appointment)
        val args =
            JSONObject()
                .put("appointment_id", f.appointment.getString("id"))
                .put("expected_record_hash", originalHash)
                .put("updates", JSONObject().put("date", "2027-05-20"))
        val updated =
            AppointmentRules.prepare(
                "update_appointment",
                args,
                f.memory.snapshot,
                "qa_canonical_real_update",
                "2027-05-18T09:01:00Z",
            )
        f.memory.snapshot.put("appointments", JSONArray().put(updated))
        val visits = JSONArray().put(f.visit())
        assertEquals(
            0,
            PlanningBoardRules.tourEntries(JSONArray().put(updated), visits, "2027-05-19").size,
        )
        val shown = PlanningBoardRules.tourEntries(JSONArray().put(updated), visits, "2027-05-20")
        assertEquals(1, shown.size)
        assertEquals(updated.getString("id"), shown.single().getString("source_id"))
        assertEquals(beforeVisit, f.memory.values.getValue(f.key))
        assertEquals(workBefore, f.memory.snapshot.getJSONArray("worklogs").toString())
        assertEquals(
            "WRITE_CONFLICT",
            rejected {
                AppointmentRules.prepare(
                    "update_appointment",
                    args,
                    f.memory.snapshot,
                    "qa_canonical_stale",
                    "2027-05-18T09:02:00Z",
                )
            }
                .code,
        )
        val legacyFailure = rejected {
            f.service.execute("update_visit", f.change("qa_canonical_after_move"))
        }
        assertEquals(
            AppointmentRules.hash(updated),
            McpErrors.rpc(legacyFailure).getJSONObject("error").getString("record_hash"),
        )
    }

    @Test
    fun contradictoryLinksStayIndependentAndAreNeverSilentlyRedirected() {
        val f = Fixture(0)
        val other =
            AppointmentRules.prepare(
                    "create_appointment",
                    JSONObject().put("date", "2027-05-19"),
                    f.memory.snapshot,
                    "qa_canonical_second_original",
                    "2027-05-18T09:00:00Z",
                )
                .put("visit_id", f.visitId)
        f.memory.snapshot.getJSONArray("appointments").put(other)
        val args = f.change().put("updates", JSONObject().put("title", "[TEST] Eigener Einsatz"))
        val saved = f.service.execute("update_visit", args).getJSONObject("record")
        assertEquals(f.visitId, saved.getString("id"))
        assertEquals("[TEST] Eigener Einsatz", saved.getString("title"))
        assertEquals(
            3,
            PlanningBoardRules.tourEntries(
                    f.memory.snapshot.getJSONArray("appointments"),
                    JSONArray().put(saved),
                    "2027-05-19",
                )
                .size,
        )
    }
}
