package at.hausgarten.service

import java.sql.DriverManager
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlanningBoardTest {
    @Test
    fun combinedPlanningQueryPaginatesBothKindsAndKeepsFilters() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use {
                it.execute(
                    "CREATE TABLE native_read_rows(collection TEXT,id TEXT,title TEXT,subtitle TEXT,work_date TEXT,sort_key TEXT,active INTEGER,status TEXT,customer_id TEXT,order_id TEXT,search_text TEXT)"
                )
            }
            fun add(
                collection: String,
                id: String,
                date: String,
                active: Int = 1,
                status: String = "planned",
                customer: String = "qa_board_customer",
            ) {
                db.prepareStatement("INSERT INTO native_read_rows VALUES(?,?,?,?,?,?,?,?,?,?,?)")
                    .use { statement ->
                        val values =
                            listOf(
                                collection,
                                id,
                                "[TEST] QA Hecke",
                                "",
                                date,
                                id,
                                active.toString(),
                                status,
                                customer,
                                "qa_board_order",
                                "[test] qa hecke",
                            )
                        values.forEachIndexed { index, value ->
                            statement.setString(index + 1, value)
                        }
                        statement.executeUpdate()
                    }
            }
            for (i in 0 until 60) add(
                if (i % 2 == 0) "appointments" else "visits",
                "qa_board_$i",
                if (i % 2 == 0) "2026-11-09" else "2026-11-15T18:00:00+01:00",
            )
            add("photos", "qa_board_photo", "2026-11-09")
            add("visits", "qa_board_archived", "2026-11-09", active = 0)
            add("appointments", "qa_board_done", "2026-11-09", status = "completed")
            add("visits", "qa_board_other", "2026-11-09", customer = "qa_board_other_customer")
            val args =
                JSONObject()
                    .put("date_from", "2026-11-09")
                    .put("date_to", "2026-11-15")
                    .put("customer_id", "qa_board_customer")
                    .put("order_id", "qa_board_order")
                    .put("status", "open")
                    .put("query", "HECKE")
            fun count(input: JSONObject): Int {
                val query = PlanningBoardRules.query(input)
                db.prepareStatement(query.countSql).use { statement ->
                    query.values.forEachIndexed { index, value ->
                        statement.setString(index + 1, value)
                    }
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        return rows.getInt(1)
                    }
                }
            }
            fun page(offset: Int): List<String> {
                val query =
                    PlanningBoardRules.query(JSONObject(args.toString()).put("offset", offset))
                db.prepareStatement(query.pageSql).use { statement ->
                    query.pageValues.forEachIndexed { index, value ->
                        statement.setString(index + 1, value)
                    }
                    statement.executeQuery().use { rows ->
                        return buildList { while (rows.next()) add(rows.getString(2)) }
                    }
                }
            }
            assertEquals(60, count(args))
            val first = page(0)
            val second = page(50)
            assertEquals(50, first.size)
            assertEquals(10, second.size)
            assertEquals(60, (first + second).toSet().size)
            assertTrue((first + second).all { it.matches(Regex("qa_board_[0-9]+")) })
            assertEquals(1, count(JSONObject(args.toString()).put("status", "completed")))
            assertEquals(0, count(JSONObject(args.toString()).put("query", "x') OR 1=1 --")))
            assertEquals(60, count(args))
        }
    }

    @Test
    fun visitWeatherUsesOnlyItsKnownWindowAndObjectLocation() {
        val row = visit()
        val before = row.toString()
        val property =
            JSONObject()
                .put("address", "[TEST] QA Garten")
                .put("latitude", 48.2)
                .put("longitude", 16.3)
        val input = requireNotNull(PlanningBoardRules.weatherAppointment("visit", row, property))
        assertEquals("2026-11-09T08:00:00Z", input.getString("starts_at"))
        assertEquals("2026-11-09T10:00:00Z", input.getString("ends_at"))
        assertEquals("[TEST] QA Garten", input.getString("location"))
        assertEquals(48.2 to 16.3, PlanningBoardRules.coordinates(input))
        assertEquals(before, row.toString())
        assertNull(
            PlanningBoardRules.weatherAppointment(
                "visit",
                visit().put("window_start", "").put("window_end", ""),
                property,
            )
        )
        assertNull(
            PlanningBoardRules.coordinates(
                requireNotNull(PlanningBoardRules.weatherAppointment("visit", row, JSONObject()))
            )
        )
    }

    private fun appointment(end: String = "2026-11-09T09:30:00Z") =
        JSONObject()
            .put("id", "qa_board_appointment")
            .put("status", "confirmed")
            .put("starts_at", "2026-11-09T08:00:00Z")
            .put("ends_at", end)
            .put("customer_id", "qa_customer")
            .put("timezone", "Europe/Vienna")

    private fun visit() =
        JSONObject()
            .put("id", "qa_board_visit")
            .put("status", "planned")
            .put("date", "2026-11-09")
            .put("window_start", "09:00")
            .put("window_end", "11:00")
            .put("planned_minutes", 60)
            .put("property_id", "qa_property")
            .put("care_plan_id", "qa_care")

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun visitMovesOnlyTheSelectedWindow() {
        val old = visit()
        val before = old.toString()
        val (tool, args) =
            PlanningBoardRules.move(
                "visit",
                old,
                LocalDate.parse("2026-11-10"),
                600,
                "a".repeat(64),
            )
        assertEquals("update_visit", tool)
        assertEquals("only_this", args.getString("scope"))
        assertEquals("a".repeat(64), args.getString("expected_record_hash"))
        val changes = args.getJSONObject("updates")
        assertEquals(3, changes.length())
        assertEquals("2026-11-10", changes.getString("date"))
        assertEquals("10:00", changes.getString("window_start"))
        assertEquals("12:00", changes.getString("window_end"))
        assertFalse(changes.has("planned_minutes"))
        assertFalse(changes.has("property_id"))
        assertEquals(before, old.toString())
    }

    @Test
    fun appointmentRetainsDurationAndSourceZone() {
        val old = appointment()
        val before = old.toString()
        val (tool, args) =
            PlanningBoardRules.move(
                "appointment",
                old,
                LocalDate.parse("2026-11-10"),
                630,
                "b".repeat(64),
            )
        assertEquals("update_appointment", tool)
        val changes = args.getJSONObject("updates")
        assertEquals(2, changes.length())
        assertEquals("2026-11-10T09:30:00Z", changes.getString("starts_at"))
        assertEquals("2026-11-10T11:00:00Z", changes.getString("ends_at"))
        assertFalse(changes.has("timezone"))
        assertEquals(before, old.toString())
    }

    @Test
    fun openEndIsNotInvented() {
        val changes =
            PlanningBoardRules.move(
                    "appointment",
                    appointment(""),
                    LocalDate.parse("2026-11-10"),
                    600,
                    "c".repeat(64),
                )
                .second
                .getJSONObject("updates")
        assertEquals(1, changes.length())
        assertFalse(changes.has("ends_at"))
    }

    @Test
    fun shiftsRespectSummerTimeAndRejectMissingTimes() {
        val next =
            PlanningBoardRules.move(
                    "appointment",
                    appointment(),
                    LocalDate.parse("2027-03-29"),
                    540,
                    "d".repeat(64),
                )
                .second
                .getJSONObject("updates")
        assertEquals("2027-03-29T07:00:00Z", next.getString("starts_at"))
        rejects {
            PlanningBoardRules.move(
                "appointment",
                appointment(),
                LocalDate.parse("2027-03-28"),
                150,
                "d".repeat(64),
            )
        }
        rejects {
            PlanningBoardRules.move(
                "visit",
                visit().put("window_start", "").put("window_end", ""),
                LocalDate.parse("2026-11-10"),
                600,
                "d".repeat(64),
            )
        }
    }

    @Test
    fun completedItemsAndOverflowStayUntouched() {
        rejects {
            PlanningBoardRules.move(
                "visit",
                visit().put("status", "completed"),
                LocalDate.parse("2026-11-10"),
                600,
                "e".repeat(64),
            )
        }
        rejects {
            PlanningBoardRules.move(
                "appointment",
                appointment().put("status", "cancelled"),
                LocalDate.parse("2026-11-10"),
                600,
                "e".repeat(64),
            )
        }
        rejects {
            PlanningBoardRules.move(
                "visit",
                visit(),
                LocalDate.parse("2026-11-10"),
                1410,
                "e".repeat(64),
            )
        }
        rejects {
            PlanningBoardRules.move(
                "visit",
                visit(),
                LocalDate.parse("2026-11-10"),
                -30,
                "e".repeat(64),
            )
        }
        assertEquals(600, PlanningBoardRules.snapMinute(606f))
        assertEquals(630, PlanningBoardRules.snapMinute(617f))
    }

    private fun tour(review: Boolean = false) =
        JSONObject()
            .put("tour", JSONObject().put("needs_review", review))
            .put("record_hash", "f".repeat(64))
            .put(
                "stops",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "qa_stop_b")
                            .put("property_id", "qa_property_b")
                            .put("title", "[TEST] Erstes Ziel")
                            .put("status", "planned")
                            .put("tour_date_matches", true)
                    )
                    .put(
                        JSONObject()
                            .put("id", "qa_stop_a")
                            .put("property_id", "qa_property_a")
                            .put("status", "planned")
                            .put("tour_date_matches", true)
                    ),
            )

    @Test
    fun navigationUsesSavedOrderAndEscapesTheAddress() {
        val order = tour()
        val before = order.toString()
        val called = mutableListOf<String>()
        val nav =
            PlanningBoardRules.navigation(order) {
                called += it
                JSONObject().put("address", "[TEST] A & B + Garten")
            }
        assertEquals(listOf("qa_property_b"), called)
        assertEquals("qa_stop_b", nav.getString("target_visit_id"))
        assertEquals("qa_stop_b", nav.getJSONArray("visit_ids").getString(0))
        assertEquals("qa_stop_a", nav.getJSONArray("visit_ids").getString(1))
        assertTrue(nav.getString("navigation_uri").contains("A%20%26%20B%20%2B%20Garten"))
        assertTrue(nav.getString("fallback_uri").startsWith("geo:0,0?q="))
        assertEquals(before, order.toString())
    }

    @Test
    fun navigationRejectsStaleOrEmptyTourAndMissingAddress() {
        rejects {
            PlanningBoardRules.navigation(tour(true)) { JSONObject().put("address", "[TEST] Ziel") }
        }
        rejects { PlanningBoardRules.navigation(tour().put("stops", JSONArray())) { JSONObject() } }
        val changed = tour()
        changed.getJSONArray("stops").getJSONObject(0).put("tour_date_matches", false)
        rejects {
            PlanningBoardRules.navigation(changed) { JSONObject().put("address", "[TEST] Ziel") }
        }
        rejects { PlanningBoardRules.navigation(tour()) { JSONObject() } }
    }

    @Test
    fun knownCoordinatesAreUsedWithoutInventingLocations() {
        val target = JSONObject().put("latitude", 48.2).put("longitude", 16.3)
        assertEquals(48.2 to 16.3, PlanningBoardRules.coordinates(target))
        assertTrue(
            PlanningBoardRules.navigation(tour()) { target }
                .getString("navigation_uri")
                .contains("48.2%2C16.3")
        )
        assertNull(PlanningBoardRules.coordinates(JSONObject()))
        assertNull(PlanningBoardRules.coordinates(JSONObject().put("latitude", 48.2)))
        assertNull(
            PlanningBoardRules.coordinates(JSONObject().put("latitude", 91).put("longitude", 16.3))
        )
    }
}
