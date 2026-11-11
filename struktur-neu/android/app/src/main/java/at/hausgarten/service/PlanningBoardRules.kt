package at.hausgarten.service

import java.net.URLEncoder
import java.time.*
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Display geometry becomes an explicit time change; the existing writers remain authoritative. */
internal object PlanningBoardRules {
    val zone: ZoneId = ZoneId.of("Europe/Vienna")

    fun boardSchema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "properties",
                JSONObject()
                    .put("date_from", McpFields.text(10).put("format", "date"))
                    .put("date_to", McpFields.text(10).put("format", "date"))
                    .put("query", McpFields.text(200))
                    .put("customer_id", McpFields.id())
                    .put("order_id", McpFields.id())
                    .put(
                        "status",
                        McpFields.enumeration("", "open", "completed", "cancelled")
                            .put("minLength", 0),
                    )
                    .put("offset", McpFields.number(0, 1000000))
                    .put("limit", McpFields.number(1, 50)),
            )
            .put("required", JSONArray(listOf("date_from", "date_to")))

    data class Query(val where: String, val values: List<String>, val offset: Int, val limit: Int) {
        val countSql
            get() = "SELECT count(*) FROM native_read_rows WHERE $where"

        val pageSql
            get() =
                "SELECT collection,id,title,subtitle,work_date FROM native_read_rows WHERE $where ORDER BY work_date,sort_key,collection,id LIMIT ? OFFSET ?"

        val pageValues
            get() = values + listOf(limit.toString(), offset.toString())
    }

    /**
     * Only explicit persisted relationships identify aliases. Similar names/times are independent.
     */
    fun aliases(appointments: JSONArray, visits: JSONArray): Map<String, String> {
        val appointmentRows = (0 until appointments.length()).map { appointments.getJSONObject(it) }
        val visitRows = (0 until visits.length()).map { visits.getJSONObject(it) }
        val appointmentIds = appointmentRows.map { it.getString("id") }.toSet()
        val visitIds = visitRows.map { it.getString("id") }.toSet()
        val candidates = linkedMapOf<String, MutableSet<String>>()
        fun add(visit: String, appointment: String) {
            if (visit in visitIds && appointment in appointmentIds)
                candidates.getOrPut(visit) { linkedSetOf() }.add(appointment)
        }
        for (row in visitRows) {
            add(row.getString("id"), row.optString("appointment_id"))
            if (row.optString("source_collection") == "appointments")
                add(row.getString("id"), row.optString("source_id"))
        }
        for (row in appointmentRows) {
            add(row.optString("visit_id"), row.getString("id"))
            if (row.optString("source_collection") == "visits")
                add(row.optString("source_id"), row.getString("id"))
        }
        // Contradictory links stay separate and visible for review.
        return candidates.filterValues { it.size == 1 }.mapValues { it.value.single() }
    }

    fun independentVisit(row: JSONObject): Boolean =
        row.optString("care_plan_id").isBlank() || row.optBoolean("planning_adopted")

    /** A view of originals, never a copied visit or an implicit work record. */
    fun tourEntries(appointments: JSONArray, visits: JSONArray, day: String): List<JSONObject> {
        LocalDate.parse(day)
        val hidden = aliases(appointments, visits).keys
        val result = mutableListOf<JSONObject>()
        for (i in 0 until appointments.length()) {
            val source = appointments.getJSONObject(i)
            if (
                !AppointmentRules.active(source) ||
                    source.optString("status") == "draft" ||
                    AppointmentRules.day(source).toString() != day
            )
                continue
            val local = start("appointment", source)
            val end =
                source
                    .optString("ends_at")
                    .takeIf(String::isNotBlank)
                    ?.let { Instant.parse(it).atZone(zone).toLocalTime().toString() }
                    .orEmpty()
            result.add(
                JSONObject(source.toString())
                    .put("id", "appointments:" + source.getString("id"))
                    .put("source_collection", "appointments")
                    .put("source_id", source.getString("id"))
                    .put("date", day)
                    .put("title", AppointmentRules.title(source))
                    .put("window_start", local?.toLocalTime()?.toString() ?: "")
                    .put("window_end", end)
            )
        }
        for (i in 0 until visits.length()) {
            val source = visits.getJSONObject(i)
            if (
                source.getString("id") in hidden ||
                    !independentVisit(source) ||
                    !RevierRules.activeVisit(source) ||
                    source.optString("date") != day
            )
                continue
            result.add(
                JSONObject(source.toString())
                    .put("source_collection", "visits")
                    .put("source_id", source.getString("id"))
            )
        }
        return result.sortedWith(
            compareBy<JSONObject> { it.optString("window_start").ifBlank { "99:99" } }
                .thenBy { it.getString("id") }
        )
    }

    fun query(args: JSONObject, hiddenVisits: Set<String> = emptySet()): Query {
        McpSchema.validate(args, boardSchema())
        val from = LocalDate.parse(args.getString("date_from"))
        val to = LocalDate.parse(args.getString("date_to"))
        require(!to.isBefore(from)) { "Das Ende muss nach dem Beginn liegen." }
        val where =
            StringBuilder(
                "collection IN ('appointments','visits') AND active=1 AND work_date>=? AND work_date<=?"
            )
        val values = mutableListOf(from.toString(), to.toString() + '\uffff')
        if (hiddenVisits.isNotEmpty()) {
            where
                .append(" AND NOT (collection='visits' AND id IN (")
                .append(hiddenVisits.joinToString(",") { "?" })
                .append("))")
            values.addAll(hiddenVisits)
        }
        for (key in listOf("customer_id", "order_id")) if (args.optString(key).isNotBlank()) {
            where.append(" AND $key=?")
            values.add(args.getString(key))
        }
        when (args.optString("status")) {
            "open" -> where.append(" AND status NOT IN ('completed','cancelled','superseded')")
            "completed",
            "cancelled" -> {
                where.append(" AND status=?")
                values.add(args.getString("status"))
            }
        }
        for (term in
            SnapshotReadSupport.fold(args.optString("query"))
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotEmpty)) {
            where.append(" AND instr(search_text,?)>0")
            values.add(term)
        }
        return Query(where.toString(), values, args.optInt("offset", 0), args.optInt("limit", 50))
    }

    fun weatherAppointment(kind: String, record: JSONObject, property: JSONObject): JSONObject? {
        if (kind == "appointment")
            return if (AppointmentRules.timed(record)) JSONObject(record.toString()) else null
        val local = start(kind, record) ?: return null
        val duration = duration(kind, record) ?: return null
        val point = coordinates(property)
        return JSONObject()
            .put("id", record.getString("id"))
            .put("timezone", zone.id)
            .put("starts_at", local.atZone(zone).toInstant().toString())
            .put("ends_at", local.plusMinutes(duration).atZone(zone).toInstant().toString())
            .put("location", property.optString("address"))
            .put("weather_profile", "general")
            .apply {
                if (point != null) put("latitude", point.first).put("longitude", point.second)
            }
    }

    fun start(kind: String, record: JSONObject): LocalDateTime? =
        when (kind) {
            "appointment" ->
                record.optString("starts_at").takeIf(String::isNotBlank)?.let {
                    Instant.parse(it).atZone(zone).toLocalDateTime()
                }
            "visit" ->
                record.optString("window_start").takeIf(String::isNotBlank)?.let {
                    LocalDate.parse(record.getString("date")).atTime(LocalTime.parse(it))
                }
            else -> error("Unbekannter Planungseintrag.")
        }

    fun day(kind: String, record: JSONObject): LocalDate =
        if (kind == "visit") LocalDate.parse(record.getString("date"))
        else AppointmentRules.day(record)

    fun duration(kind: String, record: JSONObject): Long? {
        if (kind == "visit") {
            val start = record.optString("window_start")
            val end = record.optString("window_end")
            if (start.isBlank() || end.isBlank()) return null
            return Duration.between(LocalTime.parse(start), LocalTime.parse(end)).toMinutes()
        }
        val end = record.optString("ends_at")
        return if (end.isBlank()) null
        else
            Duration.between(Instant.parse(record.getString("starts_at")), Instant.parse(end))
                .toMinutes()
    }

    fun snapMinute(minute: Float): Int = (minute / 30).roundToInt() * 30

    fun move(
        kind: String,
        record: JSONObject,
        date: LocalDate,
        minute: Int,
        hash: String,
    ): Pair<String, JSONObject> {
        require(kind in setOf("appointment", "visit"))
        require(record.optString("status") !in setOf("completed", "cancelled", "superseded")) {
            "Abgeschlossene Einsätze bleiben unverändert."
        }
        require(start(kind, record) != null) { "Zuerst eine Uhrzeit für diesen Einsatz festlegen." }
        require(minute in 0..1439 && minute % 30 == 0) {
            "Eine Uhrzeit innerhalb dieses Tages wählen."
        }
        val local = date.atStartOfDay().plusMinutes(minute.toLong())
        require(zone.rules.getValidOffsets(local).isNotEmpty()) {
            "Diese Uhrzeit entfällt durch die Zeitumstellung."
        }
        val updates = JSONObject()
        val args =
            JSONObject()
                .put("${kind}_id", record.getString("id"))
                .put("expected_record_hash", hash)
                .put("updates", updates)
        val length = duration(kind, record)
        if (kind == "visit") {
            require(length != null && length > 0 && minute + length < 1440) {
                "Das vollständige Zeitfenster muss in diesen Tag passen."
            }
            updates
                .put("date", date.toString())
                .put("window_start", local.toLocalTime().toString())
                .put("window_end", local.plusMinutes(length).toLocalTime().toString())
            args.put("reason", "In der Planung verschoben")
            if (record.optString("care_plan_id").isNotBlank()) args.put("scope", "only_this")
        } else {
            val oldOffset = Instant.parse(record.getString("starts_at")).atZone(zone).offset
            val selectedOffset =
                zone.rules.getValidOffsets(local).let {
                    if (oldOffset in it) oldOffset else it.first()
                }
            val instant = local.toInstant(selectedOffset)
            updates.put("starts_at", instant.toString())
            if (length != null) updates.put("ends_at", instant.plusSeconds(length * 60).toString())
        }
        return "update_$kind" to args
    }

    fun coordinates(record: JSONObject): Pair<Double, Double>? {
        if (record.isNull("latitude") || record.isNull("longitude")) return null
        val latitude = record.optDouble("latitude", Double.NaN)
        val longitude = record.optDouble("longitude", Double.NaN)
        return if (
            latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
        )
            latitude to longitude
        else null
    }

    private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun navigation(tour: JSONObject, property: (String) -> JSONObject): JSONObject {
        val record = tour.getJSONObject("tour")
        require(!record.optBoolean("needs_review")) {
            "Die geänderte Tour zuerst prüfen und speichern."
        }
        val stops = SettlementRules.rows(tour.getJSONArray("stops"))
        require(stops.isNotEmpty()) { "Zuerst Stopps in die Tour aufnehmen." }
        require(
            stops.all {
                it.optBoolean("tour_date_matches") &&
                    (if (it.optString("source_collection") == "appointments")
                        AppointmentRules.active(it)
                    else RevierRules.activeVisit(it))
            }
        ) {
            "Die geänderten Stopps zuerst in der Tour prüfen."
        }
        val routed = stops.mapNotNull { stop ->
            val direct = stop.optString("location").trim()
            val address =
                if (direct.isNotBlank()) direct
                else
                    stop
                        .optString("property_id")
                        .takeIf(String::isNotBlank)
                        ?.let { property(it).optString("address").trim() }
                        .orEmpty()
            if (address.isBlank()) null else stop to address
        }
        require(routed.isNotEmpty()) { "Für die Navigation fehlt eine Adresse." }
        val first = routed.first().first
        val addresses = routed.map { it.second }
        val navigation =
            "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" +
                encoded(addresses.last()) +
                if (addresses.size > 1)
                    "&waypoints=" + encoded(addresses.dropLast(1).joinToString("|"))
                else ""
        return JSONObject()
            .put("tour_hash", tour.getString("record_hash"))
            .put("target_visit_id", first.getString("id"))
            .put("target_title", first.optString("title", "Erster Stopp"))
            .put("address", addresses.first())
            .put("addresses", JSONArray(addresses))
            .put("navigation_uri", navigation)
            .put("fallback_uri", navigation)
            .put("visit_ids", JSONArray(stops.map { it.getString("id") }))
            .put("navigation_stop_ids", JSONArray(routed.map { it.first.getString("id") }))
            .put("without_address", stops.size - routed.size)
    }
}
