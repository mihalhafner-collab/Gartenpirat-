package at.hausgarten.service

import java.time.*
import java.util.*
import org.json.*

/** Calendar plans for the existing local equipment owner; no scheduler or network effects. */
class EquipmentMaintenanceRules {
    private constructor() {}

    companion object {
        @JvmField val MAX_PLANS: Int = 64

        @Throws(Exception::class)
        @JvmStatic
        fun interval(row: JSONObject) {
            var unit: String = RevierRules.text(row, "interval_unit", 12, true)
            var maximum: Int
            when ((unit)) {
                "days" -> {
                    maximum = 3650
                }
                "weeks" -> {
                    maximum = 520
                }
                "months" -> {
                    maximum = 120
                }
                "years" -> {
                    maximum = 10
                }
                else -> {
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Intervall in Tagen, Wochen, Monaten oder Jahren wählen.",
                    )
                }
            }
            var value: Int = RevierRules.integer(row, "interval_value", 1, maximum, -1)
            if (((value < 1))) {
                throw RevierRules.error("INVALID_INPUT", "Eine positive Intervalllänge angeben.")
            }
            row.put("interval_value", value).put("interval_unit", unit)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun advance(anchor: LocalDate, interval: JSONObject): LocalDate {
            interval(interval)
            var value: Int = interval.getInt("interval_value")
            try {
                when ((interval.getString("interval_unit"))) {
                    "days" -> {
                        return anchor.plusDays((value).toLong())
                    }
                    "weeks" -> {
                        return anchor.plusWeeks((value).toLong())
                    }
                    "months" -> {
                        return anchor.plusMonths((value).toLong())
                    }
                    else -> {
                        return anchor.plusYears((value).toLong())
                    }
                }
            } catch (invalid: DateTimeException) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Dieses Intervall ergibt kein gültiges Wartungsdatum.",
                )
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun recalculate(plan: JSONObject) {
            var regular: String =
                advance(RevierRules.date(plan.getString("anchor_on")), plan).toString()
            if (((regular.length != 10))) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Das Wartungsdatum liegt außerhalb des gültigen Bereichs.",
                )
            }
            plan.put("regular_due_on", regular)
            var once: String = RevierRules.date(plan, "one_off_due_on", false)
            if (((!once.isEmpty() && (once.compareTo(regular) >= 0)))) {
                plan.remove("one_off_due_on")
                once = ""
            }
            plan.put("next_due_on", (if (once.isEmpty()) regular else once))
        }

        @Throws(Exception::class)
        @JvmStatic
        fun plans(equipment: JSONObject): JSONArray {
            if ((!equipment.has("maintenance_plans"))) {
                return JSONArray()
            }
            if ((!(equipment.get("maintenance_plans") is JSONArray))) {
                throw RevierRules.error(
                    "REVIER_RELATION_INVALID",
                    "Wartungspläne sind nicht als Liste gespeichert.",
                )
            }
            var plans: JSONArray = equipment.getJSONArray("maintenance_plans")
            if (((plans.length() > MAX_PLANS))) {
                throw RevierRules.error(
                    "REVIER_RELATION_INVALID",
                    "Zu viele Wartungspläne an diesem Gerät.",
                )
            }
            var ids: MutableSet<String> = HashSet()
            run {
                var i: Int = 0
                while ((i < plans.length())) {
                    var p: JSONObject = plans.getJSONObject(i)
                    var id: String = RevierRules.id(p, "id", true)
                    if ((!ids.add(id))) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Wartungsplan ist mehrfach vorhanden.",
                        )
                    }
                    RevierRules.text(p, "title", 240, true)
                    interval(p)
                    RevierRules.date(p, "anchor_on", true)
                    var expected: JSONObject = RevierRules.copy(p)
                    recalculate(expected)
                    for (field in arrayOf("regular_due_on", "next_due_on")) {
                        if ((!expected.getString(field).equals(p.optString(field)))) {
                            throw RevierRules.error(
                                "REVIER_RELATION_INVALID",
                                "Wartungsintervall und Fälligkeit widersprechen sich.",
                            )
                        }
                    }
                    if (((expected.has("one_off_due_on") != p.has("one_off_due_on")))) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Einzeltermin und regelmäßige Fälligkeit widersprechen sich.",
                        )
                    }
                    var completed: String = RevierRules.date(p, "last_completed_on", false)
                    if (
                        ((!completed.isEmpty() &&
                            ((!completed.equals(p.getString("anchor_on")) ||
                                p.optString("last_event_id").isEmpty()))))
                    ) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Letzte Wartung und Planbezug widersprechen sich.",
                        )
                    }
                    i++
                }
            }
            return plans
        }

        @Throws(Exception::class)
        @JvmStatic
        fun find(plans: JSONArray, id: String): JSONObject {
            run {
                var i: Int = 0
                while ((i < plans.length())) {
                    if ((id.equals(plans.getJSONObject(i).getString("id")))) {
                        return plans.getJSONObject(i)
                    }
                    i++
                }
            }
            throw RevierRules.error(
                "REFERENCE_NOT_FOUND",
                "Dieser Wartungsplan wurde nicht gefunden.",
            )
        }

        @Throws(Exception::class)
        @JvmStatic
        fun shorten(plan: JSONObject, requested: JSONObject, reason: String) {
            if ((reason.trim().isEmpty())) {
                throw RevierRules.error("INVALID_INPUT", "Grund für das kürzere Intervall angeben.")
            }
            interval(requested)
            var anchor: LocalDate = RevierRules.date(plan.getString("anchor_on"))
            if ((!advance(anchor, requested).isBefore(advance(anchor, plan)))) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Ein tatsächlich kürzeres Intervall wählen.",
                )
            }
            plan
                .put("interval_value", requested.getInt("interval_value"))
                .put("interval_unit", requested.getString("interval_unit"))
            recalculate(plan)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun nextDue(equipment: JSONObject): String {
            var next: String = ""
            var plans: JSONArray = plans(equipment)
            run {
                var i: Int = 0
                while ((i < plans.length())) {
                    var due: String = plans.getJSONObject(i).getString("next_due_on")
                    if (((next.isEmpty() || (due.compareTo(next) < 0)))) {
                        next = due
                    }
                    i++
                }
            }
            return next
        }
    }
}
