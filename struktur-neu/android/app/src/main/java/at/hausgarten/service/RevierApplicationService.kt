package at.hausgarten.service

import java.math.BigDecimal
import java.time.*
import java.util.*
import org.json.*

/** Common offline application cases for objects, visits, tours and operating resources. */
class RevierApplicationService {
    /** A Visit proof cannot authorize a write to its separately versioned canonical appointment. */
    class CanonicalAppointmentRequired(
        @JvmField val appointmentId: String,
        @JvmField val currentRecordHash: String,
        @JvmField val visitId: String,
    ) :
        PixelMcpWriteTools.Rejected(
            "CANONICAL_APPOINTMENT_REQUIRED",
            "Dieser Einsatz gehört zu einem bestehenden Termin. Zum Ändern den aktuellen Termin öffnen.",
        )

    private val repository: RevierRepository
    private val clock: Clock

    constructor(repository: RevierRepository) : this(repository, Clock.systemUTC()) {}

    constructor(repository: RevierRepository, clock: Clock) {
        this.repository = repository
        this.clock = clock
    }

    /** Retired names respond explicitly before any transaction or receipt is written. */
    private fun rejectRemovedCare(name: String) {
        if (
            name in
                setOf(
                    "list_care_plans",
                    "create_care_plan",
                    "update_care_plan",
                    "generate_care_visits",
                )
        )
            throw RevierRules.error(
                "FUNCTION_REMOVED",
                "Objektpflege und Pflegepläne sind entfernt. Bestehende Objekte, Fotos und Historie bleiben erhalten.",
            )
    }

    private fun stamp(): String {
        return clock.instant().toString()
    }

    private fun today(): LocalDate {
        return clock.instant().atZone(BUSINESS_ZONE).toLocalDate()
    }

    @Throws(Exception::class)
    private fun records(type: String): JSONArray {
        var stored: JSONArray =
            repository.list("" + (("" + RevierRules.ROOT + "entity:") + type) + ":")
        val rows: JSONArray = JSONArray()
        run {
            var i: Int = 0
            while (i < stored.length()) {
                val row: JSONObject = stored.getJSONObject(i)
                RevierRules.version(row)
                if (!row.optBoolean("deleted")) {
                    rows.put(row)
                }
                i++
            }
        }
        return rows
    }

    @Throws(Exception::class)
    private fun load(type: String, id: String): JSONObject {
        var value: String = repository.get(key(type, id), "")
        if (value.isEmpty()) {
            throw RevierRules.error(
                "REFERENCE_NOT_FOUND",
                "Der verknüpfte Eintrag wurde nicht gefunden.",
            )
        }
        val row: JSONObject = JSONObject(value)
        RevierRules.version(row)
        if (row.optBoolean("deleted")) {
            throw RevierRules.error(
                "ENTITY_DELETED",
                "Dieser Eintrag wurde endgültig gelöscht. Eine alte Aktion legt ihn nicht erneut an.",
            )
        }
        if (!id.equals(row.getString("id"))) {
            throw RevierRules.error(
                "REVIER_RECORD_INVALID",
                "Eintrag und Kennung stimmen nicht überein.",
            )
        }
        return row
    }

    @Throws(Exception::class)
    private fun find(type: String, id: String): JSONObject? {
        var value: String = repository.get(key(type, id), "")
        if (value.isEmpty()) {
            return null
        }
        val row: JSONObject = JSONObject(value)
        RevierRules.version(row)
        if (row.optBoolean("deleted")) {
            return null
        }
        return row
    }

    @Throws(Exception::class)
    private fun canonical(state: JSONObject, type: String, id: String): JSONObject {
        val values: JSONArray? = state.optJSONArray(type)
        if (values != null) {
            run {
                var i: Int = 0
                while (i < values.length()) {
                    val row: JSONObject = values.getJSONObject(i)
                    if (id.equals(row.optString("id"))) {
                        return row
                    }
                    i++
                }
            }
        }
        throw RevierRules.error(
            "REFERENCE_NOT_FOUND",
            "Der bestehende Kunden-/Auftragsbezug wurde nicht gefunden.",
        )
    }

    @Throws(Exception::class)
    private fun customer(state: JSONObject, id: String, creating: Boolean): JSONObject {
        val customer: JSONObject = canonical(state, "customers", id)
        if (creating && (customer.optInt("active", 1) == 0)) {
            throw RevierRules.error(
                "CUSTOMER_INACTIVE",
                "Für neue Vorgänge einen aktiven Kunden wählen.",
            )
        }
        return customer
    }

    @Throws(Exception::class)
    private fun base(kind: String, id: String, old: JSONObject?): JSONObject {
        return (if (old == null)
            JSONObject()
                .put("format_version", 1)
                .put("kind", kind)
                .put("id", id)
                .put("created_at", stamp())
        else RevierRules.copy(old))
    }

    @Throws(Exception::class)
    private fun save(
        type: String,
        row: JSONObject,
        old: JSONObject?,
        request: String,
        reason: String,
    ): JSONObject {
        row.remove("record_hash")
        if (!row.has("created_at")) {
            row.put("created_at", stamp())
        }
        row.put("updated_at", stamp())
        RevierRules.bounded(row)
        repository.put(key(type, row.getString("id")), row.toString())
        var audit: JSONObject =
            JSONObject()
                .put("format_version", 1)
                .put("entity_type", type)
                .put("entity_id", row.getString("id"))
                .put("request_id", request)
                .put("at", stamp())
                .put("reason", reason)
                .put("before", (if (old == null) JSONObject.NULL else old))
                .put("after", row)
        repository.put(
            (("" +
                (("" + (("" + RevierRules.ROOT + "audit:") + type) + ":") + row.getString("id")) +
                ":") + PixelMcpWriteTools.hash(request)),
            audit.toString(),
        )
        return RevierRules.view(row)
    }

    @Throws(Exception::class)
    fun execute(name: String, args: JSONObject): JSONObject {
        rejectRemovedCare(name)
        var request: String = RevierRules.text(args, "request_id", 128, true)
        if ((request.length < 8) || !request.matches(Regex("[A-Za-z0-9_.:-]+"))) {
            throw RevierRules.error(
                "INVALID_INPUT",
                "Eine stabile Anfragekennung ist erforderlich.",
            )
        }
        var fingerprint: String =
            PixelMcpWriteTools.hash(JSONObject().put("operation", name).put("args", args))
        var receipt: String =
            (("" + RevierRules.ROOT + "request:") + PixelMcpWriteTools.hash(request))
        return repository.transaction(
            lambda0@{
                val prior: String = repository.get(receipt, "")
                if (!prior.isEmpty()) {
                    val saved: JSONObject = JSONObject(prior)
                    RevierRules.version(saved)
                    if (!fingerprint.equals(saved.getString("fingerprint"))) {
                        throw RevierRules.error(
                            "REPLAY_MISMATCH",
                            "Diese Anfragekennung gehört zu anderen Angaben.",
                        )
                    }
                    if (saved.getJSONObject("result").optBoolean("_result_content_removed")) {
                        throw RevierRules.error(
                            "REQUEST_EXPIRED",
                            "Dieser bereits verarbeitete Vorgang wurde endgültig gelöscht; keine erneute Anlage.",
                        )
                    }
                    return@lambda0 RevierRules.copy(saved.getJSONObject("result"))
                        .put("replayed", true)
                }
                var result: JSONObject =
                    mutate(name, RevierRules.copy(args), repository.effective(), request)
                        .put("local_saved", true)
                        .put("local_state", "local_saved")
                        .put("cloud_confirmed", false)
                        .put("replayed", false)
                var saved: JSONObject =
                    JSONObject()
                        .put("format_version", 1)
                        .put("fingerprint", fingerprint)
                        .put("at", stamp())
                        .put("result", result)
                RevierRules.bounded(saved)
                repository.put(receipt, saved.toString())
                return@lambda0 result
            }
        )
    }

    @Throws(Exception::class)
    fun query(name: String, args: JSONObject): JSONObject {
        rejectRemovedCare(name)
        if (name == "get_property_timeline" && args.optString("view") == "care")
            throw RevierRules.error(
                "FUNCTION_REMOVED",
                "Objektpflege ist entfernt. Verlauf, Fotos und Geld bleiben erhalten.",
            )
        for (field in arrayOf("from_date", "to_date")) {
            if (!args.optString(field).isEmpty()) {
                RevierRules.date(args.getString(field))
            }
        }
        if (
            ((!args.optString("from_date").isEmpty() && !args.optString("to_date").isEmpty()) &&
                (args.getString("to_date").compareTo(args.getString("from_date")) < 0))
        ) {
            throw RevierRules.error("INVALID_INPUT", "Zeitraum endet vor seinem Beginn.")
        }
        var state: JSONObject? =
            (if (
                Arrays.asList(
                        "get_property",
                        "get_property_timeline",
                        "get_linked_context",
                        "get_inquiry",
                        "search_local",
                        "get_revier_overview",
                    )
                    .contains(name)
            )
                repository.effective()
            else null)
        when (name) {
            "get_linked_context" -> {
                return PropertyTimelineRules.linkedContext(
                    args,
                    state!!,
                    JSONObject()
                        .put("properties", records("property"))
                        .put("inquiries", records("inquiry"))
                        .put("visits", records("visit")),
                )
            }
            "inspect_edit_action" -> {
                return inspectEditAction(args)
            }
            "list_properties" -> {
                return listing("property", "properties", args)
            }
            "get_property" -> {
                return propertyDetail(RevierRules.id(args, "property_id", true), (state)!!)
            }
            "get_property_timeline" -> {
                val propertyId = RevierRules.id(args, "property_id", true)
                val result = propertyDetail(propertyId, state!!)
                val projection =
                    PropertyTimelineRules.build(
                        propertyId,
                        state,
                        JSONObject()
                            .put("inquiries", records("inquiry"))
                            .put("visits", records("visit")),
                        JSONObject(repository.get(SettlementRules.KEY, "{}")),
                        args.optString("view", "history"),
                        args.optInt("offset", 0),
                        args.optInt("limit", 50),
                    )
                for (key in projection.keys()) result.put(key, projection.get(key))
                result.remove("history")
                return result
            }
            "list_inquiries" -> {
                return listing("inquiry", "inquiries", args)
            }
            "get_inquiry" -> {
                run {
                    var result: JSONObject =
                        detail("inquiry", RevierRules.id(args, "inquiry_id", true), "inquiry")
                    val inquiry: JSONObject = result.getJSONObject("record")
                    if (!inquiry.optString("offer_id").isEmpty()) {
                        result.put(
                            "offer",
                            canonical((state)!!, "offers", inquiry.getString("offer_id")),
                        )
                    }
                    if (!inquiry.optString("order_id").isEmpty()) {
                        result.put(
                            "order",
                            canonical((state)!!, "orders", inquiry.getString("order_id")),
                        )
                    }
                    return result
                }
            }
            "list_visits" -> {
                return listing("visit", "visits", args)
            }
            "get_visit" -> {
                return detail("visit", RevierRules.id(args, "visit_id", true), "visit")
            }
            "get_day_tour" -> {
                return tour(RevierRules.date(args, "date", true))
            }
            "get_tour_navigation" -> {
                val current = tour(RevierRules.date(args, "date", true))
                RevierRules.cas(
                    current.getJSONObject("tour"),
                    args.getString("expected_record_hash"),
                )
                return PlanningBoardRules.navigation(current) { load("property", it) }
            }
            "list_materials" -> {
                return listing("material", "materials", args)
            }
            "list_equipment" -> {
                return listing("equipment", "equipment", args)
            }
            "get_equipment_maintenance" -> {
                return equipmentMaintenanceHistory(args)
            }
            "search_local" -> {
                return search(args, (state)!!)
            }
            "get_revier_overview" -> {
                return overview(args, (state)!!)
            }
            else -> {
                throw RevierRules.error("UNKNOWN_TOOL", "Unbekannte Revier-Lesefunktion.")
            }
        }
    }

    @Throws(Exception::class)
    private fun mutate(
        name: String,
        args: JSONObject,
        state: JSONObject,
        request: String,
    ): JSONObject {
        when (name) {
            "create_property",
            "update_property" -> {
                return property(name, args, state, request)
            }
            "create_inquiry",
            "update_inquiry" -> {
                return inquiry(name, args, state, request)
            }
            "purge_revier_entity" -> {
                return deleteEntity(args, state, request)
            }
            "accept_inquiry_direct" -> {
                return directOrder(args, state, request)
            }
            "plan_inquiry_inspection" -> {
                return inspection(args, state, request)
            }
            "plan_visit",
            "update_visit" -> {
                return visit(name, args, state, request)
            }
            "set_day_tour",
            "move_tour_stop" -> {
                return changeTour(name, args, request)
            }
            "save_material" -> {
                return material(args, request)
            }
            "record_material_consumption",
            "record_material_movement" -> {
                return movement(args, state, request)
            }
            "save_equipment" -> {
                return equipment(args, request)
            }
            "save_equipment_maintenance_plan" -> {
                return equipmentMaintenancePlan(args, request)
            }
            "record_equipment_maintenance" -> {
                return equipmentMaintenanceDone(args, request)
            }
            else -> {
                throw RevierRules.error("UNKNOWN_TOOL", "Unbekannte Revier-Schreibfunktion.")
            }
        }
    }

    @Throws(Exception::class)
    private fun detail(type: String, id: String, alias: String): JSONObject {
        val row: JSONObject = RevierRules.view(load(type, id))
        var result: JSONObject =
            JSONObject()
                .put("record", row)
                .put(alias, row)
                .put("record_hash", row.getString("record_hash"))
        if (type.equals("visit")) {
            result
                .put(
                    "equipment_warnings",
                    equipmentWarnings(
                        (if (row.optJSONArray("equipment_ids") == null) JSONArray()
                        else row.getJSONArray("equipment_ids"))
                    ),
                )
                .put("external_calendar_status", "unknown")
        }
        if (type.equals("inquiry")) {
            var history: JSONArray =
                repository.list("" + (("" + RevierRules.ROOT + "audit:inquiry:") + id) + ":")
            result.put("history", last(history, 20)).put("history_total", history.length())
        }
        return result
    }

    @Throws(Exception::class)
    private fun matches(row: JSONObject, filters: JSONObject): Boolean {
        if (
            (!filters.optBoolean("include_archived", false) &&
                (!row.optBoolean("active", true) || row.optBoolean("archived")))
        ) {
            return false
        }
        for (field in arrayOf("customer_id", "property_id", "status", "district", "care_plan_id")) {
            if (
                (!filters.optString(field).isEmpty() &&
                    !filters.getString(field).equals(row.optString(field)))
            ) {
                return false
            }
        }
        var date: String =
            row.optString("date", row.optString("received_at", row.optString("updated_at")))
        if (
            (!filters.optString("from_date").isEmpty() || !filters.optString("to_date").isEmpty())
        ) {
            date = calendarDay(date)
        }
        if (
            (!filters.optString("from_date").isEmpty() &&
                (date.isEmpty() || (date.compareTo(filters.getString("from_date")) < 0)))
        ) {
            return false
        }
        if (
            (!filters.optString("to_date").isEmpty() &&
                (date.isEmpty() || (date.compareTo(filters.getString("to_date")) > 0)))
        ) {
            return false
        }
        return (filters.optString("query").isEmpty() ||
            normalize(row.toString()).contains(normalize(filters.getString("query"))))
    }

    @Throws(Exception::class)
    private fun listing(type: String, alias: String, filters: JSONObject): JSONObject {
        var offset: Int = RevierRules.integer(filters, "offset", 0, 1000000, 0)
        val limit: Int = RevierRules.integer(filters, "limit", 1, 100, 50)
        val selected: MutableList<JSONObject> = ArrayList()
        val rows: JSONArray = records(type)
        run {
            var i: Int = 0
            while (i < rows.length()) {
                val row: JSONObject = rows.getJSONObject(i)
                if (matches(row, filters)) {
                    selected.add(row)
                }
                i++
            }
        }
        selected.sortWith(
            Comparator.comparing(
                    lambda1@{ r: JSONObject ->
                        r.optString("date", r.optString("name", r.optString("title")))
                    }
                )
                .thenComparing(lambda2@{ r -> r.optString("id") })
        )
        val items: JSONArray = JSONArray()
        var related: JSONArray =
            (if (type.equals("material")) records("material_movement") else JSONArray())
        run {
            var i: Int = offset
            while ((i < selected.size) && (items.length() < limit)) {
                val row: JSONObject = RevierRules.view(selected.get(i))
                if (type.equals("equipment")) {
                    row.put("next_maintenance_due_on", EquipmentMaintenanceRules.nextDue(row))
                }
                if (type.equals("material")) {
                    row.remove("stock_decimal")
                    val history: JSONArray = JSONArray()
                    var field: String =
                        (if (type.equals("material")) "material_id" else "equipment_id")
                    run {
                        var j: Int = 0
                        while (j < related.length()) {
                            if (
                                (row.getString("id")
                                    .equals(related.getJSONObject(j).optString(field)) &&
                                    related.getJSONObject(j).optString("movement_kind") ==
                                        "consume")
                            ) {
                                history.put(RevierRules.view(related.getJSONObject(j)))
                            }
                            j++
                        }
                    }
                    row.put(
                            (if (type.equals("material")) "movements" else "usages"),
                            last(history, 20),
                        )
                        .put("history_total", history.length())
                }
                items.put(row)
                i++
            }
        }
        return JSONObject()
            .put("items", items)
            .put(alias, items)
            .put("total", selected.size)
            .put("offset", offset)
            .put("limit", limit)
    }

    @Throws(Exception::class)
    private fun propertyDetail(id: String, state: JSONObject): JSONObject {
        val result: JSONObject = detail("property", id, "property")
        val property: JSONObject = result.getJSONObject("property")
        result.put("customer", canonical(state, "customers", property.getString("customer_id")))
        for (group in
            arrayOf(
                arrayOf("visit", "visits"),
                arrayOf("inquiry", "inquiries"),
            )) {
            result.put(
                group[1],
                listing(group[0], group[1], JSONObject().put("property_id", id).put("limit", 100))
                    .getJSONArray("items"),
            )
        }
        var history: JSONArray =
            repository.list("" + (("" + RevierRules.ROOT + "audit:property:") + id) + ":")
        result.put("history", last(history, 20)).put("history_total", history.length())
        return result
    }

    @Throws(Exception::class)
    private fun property(
        operation: String,
        args: JSONObject,
        state: JSONObject,
        request: String,
    ): JSONObject {
        val creating: Boolean = operation.equals("create_property")
        var old: JSONObject? =
            (if (creating) null else load("property", RevierRules.id(args, "property_id", true)))
        val changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (old != null) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject =
            base(
                "property",
                (if (creating) RevierRules.newId("property", request) else old!!.getString("id")),
                old,
            )
        for (field in
            arrayOf(
                "customer_id",
                "name",
                "address",
                "district",
                "access_notes",
                "notes",
                "area_decimal",
                "area_unit",
                "latitude",
                "longitude",
                "active",
            )) {
            if (changes.has(field)) {
                row.put(field, changes.get(field))
            }
        }
        val customer: String = RevierRules.id(row, "customer_id", true)
        customer(state, customer, creating)
        if ((old != null) && !old.getString("customer_id").equals(customer)) {
            throw RevierRules.error(
                "PROPERTY_CUSTOMER_CHANGE_REVIEW_REQUIRED",
                "Objektkunden nicht still wechseln. Ursprüngliche Zuordnung bleibt erhalten.",
            )
        }
        row.put("name", RevierRules.text(row, "name", 240, true))
        for (field in arrayOf("address", "district", "access_notes", "notes", "area_unit")) {
            row.put(
                field,
                RevierRules.text(row, field, (if (field.equals("notes")) 10000 else 2000), false),
            )
        }
        row.put("active", RevierRules.bool(row, "active", true))
        if (row.has("area_decimal")) {
            val area: String = RevierRules.decimal(row, "area_decimal", true, true)
            if (area.isEmpty()) {
                row.remove("area_decimal")
            } else {
                row.put("area_decimal", area)
            }
        }
        val lat: Boolean = (row.has("latitude") && !row.isNull("latitude"))
        val lon: Boolean = (row.has("longitude") && !row.isNull("longitude"))
        if (lat != lon) {
            throw RevierRules.error("INVALID_INPUT", "Koordinaten gemeinsam angeben.")
        }
        if (
            (lat &&
                (((!java.lang.Double.isFinite(row.getDouble("latitude")) ||
                    (Math.abs(row.getDouble("latitude")) > 90)) ||
                    !java.lang.Double.isFinite(row.getDouble("longitude"))) ||
                    (Math.abs(row.getDouble("longitude")) > 180)))
        ) {
            throw RevierRules.error("INVALID_INPUT", "Ungültige Koordinaten.")
        }
        var saved: JSONObject =
            save("property", row, old, request, RevierRules.text(args, "reason", 2000, false))
        return JSONObject()
            .put("record", saved)
            .put("property", saved)
            .put("property_id", row.getString("id"))
    }

    @Throws(Exception::class)
    private fun inquiry(
        operation: String,
        args: JSONObject,
        state: JSONObject,
        request: String,
    ): JSONObject {
        val creating: Boolean = operation.equals("create_inquiry")
        var old: JSONObject? =
            (if (creating) null else load("inquiry", RevierRules.id(args, "inquiry_id", true)))
        val changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (old != null) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject =
            base(
                "inquiry",
                (if (creating) RevierRules.newId("inquiry", request) else old!!.getString("id")),
                old,
            )
        for (field in
            arrayOf(
                "customer_id",
                "property_id",
                "title",
                "concern",
                "received_at",
                "wish_from",
                "wish_until",
                "status",
                "inspection_notes",
                "measurements",
                "photo_ids",
                "offer_id",
                "order_id",
            )) {
            if (changes.has(field)) {
                row.put(field, changes.get(field))
            }
        }
        val customer: String = RevierRules.id(row, "customer_id", true)
        customer(state, customer, creating)
        if ((old != null) && !old.getString("customer_id").equals(customer)) {
            throw RevierRules.error(
                "INQUIRY_CUSTOMER_IMMUTABLE",
                "Ursprünglichen Kundenbezug erhalten.",
            )
        }
        val property: String = RevierRules.id(row, "property_id", false)
        if (
            (!property.isEmpty() &&
                !load("property", property).getString("customer_id").equals(customer))
        ) {
            throw RevierRules.error("RELATION_MISMATCH", "Objekt und Kunde passen nicht zusammen.")
        }
        row.put("title", RevierRules.text(row, "title", 240, true))
            .put("concern", RevierRules.text(row, "concern", 10000, false))
            .put("property_id", property)
        val received: String = RevierRules.text(row, "received_at", 80, false)
        row.put("received_at", (if (received.isEmpty()) stamp() else RevierRules.instant(received)))
        val from: String = RevierRules.date(row, "wish_from", false)
        val until: String = RevierRules.date(row, "wish_until", false)
        if ((!from.isEmpty() && !until.isEmpty()) && (until.compareTo(from) < 0)) {
            throw RevierRules.error("INVALID_INPUT", "Wunschzeitraum endet vor seinem Beginn.")
        }
        row.put("wish_from", from).put("wish_until", until)
        var status: String = RevierRules.text(row, "status", 30, false)
        if (status.isEmpty()) {
            status = "new"
        }
        if (
            (!Arrays.asList(
                    "new",
                    "inspection_planned",
                    "offered",
                    "accepted",
                    "declined",
                    "closed",
                )
                .contains(status))
        ) {
            throw RevierRules.error("INVALID_INPUT", "Ungültiger Anfragestatus.")
        }
        row.put("status", status)
        for (relation in arrayOf(arrayOf("offer_id", "offers"), arrayOf("order_id", "orders"))) {
            var linked: String = RevierRules.id(row, relation[0], false)
            if (
                (!linked.isEmpty() &&
                    !customer.equals(
                        canonical(state, relation[1], linked).optString("customer_id")
                    ))
            ) {
                throw RevierRules.error(
                    "RELATION_MISMATCH",
                    "Angebot/Auftrag und Anfrage gehören zu verschiedenen Kunden.",
                )
            }
            if (row.has(relation[0])) {
                row.put(relation[0], linked)
            }
        }
        if (status.equals("accepted")) {
            var order: String = row.optString("order_id")
            if (order.isEmpty()) {
                throw RevierRules.error(
                    "ACCEPTANCE_REQUIRED",
                    "Annahme zuerst mit einem bestätigten Auftrag verknüpfen.",
                )
            }
            val offerId: String = row.optString("offer_id")
            if (
                (((old != null) && old.optString("acceptance_source").equals("direct")) &&
                    !old.optString("order_id").equals(order))
            ) {
                throw RevierRules.error(
                    "ACCEPTANCE_VERSION_MISMATCH",
                    "Bestätigten Direktauftrag nicht still austauschen.",
                )
            }
            if (!offerId.isEmpty() && !row.optString("acceptance_source").equals("direct")) {
                val offer: JSONObject = canonical(state, "offers", offerId)
                var raw: String =
                    repository.get((("" + OfferDecisionRules.PREFIX + "current:") + offerId), "")
                val decision: JSONObject? = (if (raw.isEmpty()) null else JSONObject(raw))
                if (
                    (((!order.equals(offer.optString("converted_order_id")) ||
                        (decision == null)) ||
                        !decision.optString("decision").equals("accepted")) ||
                        !decision!!
                            .optString("content_version")
                            .equals(OfferDecisionRules.contentVersion(offer)))
                ) {
                    throw RevierRules.error(
                        "ACCEPTANCE_VERSION_MISMATCH",
                        "Angenommene Angebotsversion und daraus erzeugten Auftrag zuerst prüfen.",
                    )
                }
            } else {
                if (
                    (!row.optString("acceptance_source").equals("direct") ||
                        row.optString("acceptance_evidence").isEmpty())
                ) {
                    throw RevierRules.error(
                        "ACCEPTANCE_REQUIRED",
                        "Direkte Annahme mit Zeitpunkt und Nachweis über Auftrag direkt annehmen erfassen.",
                    )
                }
            }
        }
        if (
            ((changes.has("inspection_notes") || changes.has("measurements")) ||
                changes.has("photo_ids"))
        ) {
            for (field in arrayOf("measurements", "photo_ids")) {
                if (row.has(field) && !(row.get(field) is JSONArray)) {
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Messwerte und Fotoreferenzen als Listen angeben.",
                    )
                }
            }
            if (row.optString("inspection_appointment_id").isEmpty()) {
                throw RevierRules.error(
                    "INSPECTION_REQUIRED",
                    "Besichtigung zuerst als Termin planen; Beobachtungen bleiben dem Termin zugeordnet.",
                )
            }
            row.put("inspection_notes", RevierRules.text(row, "inspection_notes", 10000, false))
            val measurements: JSONArray? = row.optJSONArray("measurements")
            if (measurements != null) {
                if (measurements.length() > 200) {
                    throw RevierRules.error("INVALID_INPUT", "Zu viele Messwerte.")
                }
                val normalized: JSONArray = JSONArray()
                run {
                    var i: Int = 0
                    while (i < measurements.length()) {
                        val item: JSONObject = measurements.getJSONObject(i)
                        normalized.put(
                            JSONObject()
                                .put("name", RevierRules.text(item, "name", 200, true))
                                .put(
                                    "value_decimal",
                                    RevierRules.decimal(item, "value_decimal", false, false),
                                )
                                .put("unit", RevierRules.text(item, "unit", 60, false))
                        )
                        i++
                    }
                }
                row.put("measurements", normalized)
            }
            val photos: JSONArray? = row.optJSONArray("photo_ids")
            if (photos != null) {
                if (photos.length() > 200) {
                    throw RevierRules.error("INVALID_INPUT", "Zu viele Fotoreferenzen.")
                }
                val seen: MutableSet<String> = HashSet()
                run {
                    var i: Int = 0
                    while (i < photos.length()) {
                        val photo: String = photos.getString(i)
                        val source: JSONObject = canonical(state, "photos", photo)
                        if (
                            ((!seen.add(photo) ||
                                !customer.equals(source.optString("customer_id"))) ||
                                source.optString("role").equals("profile"))
                        ) {
                            throw RevierRules.error(
                                "PHOTO_CONTEXT_MISMATCH",
                                "Besichtigungsfoto benötigt den passenden Dokumentationskontext.",
                            )
                        }
                        i++
                    }
                }
            }
        }
        var saved: JSONObject =
            save("inquiry", row, old, request, RevierRules.text(args, "reason", 2000, false))
        return JSONObject()
            .put("record", saved)
            .put("inquiry", saved)
            .put("inquiry_id", row.getString("id"))
    }

    @Throws(Exception::class)
    private fun inspectEditAction(args: JSONObject): JSONObject {
        val tool: String = RevierRules.text(args, "original_tool", 100, true)
        var original: JSONObject = args.getJSONObject("original_args")
        RevierRules.bounded(original)
        var request: String = original.optString("request_id")
        // Native editor/timer receipts live in SQLite pending; inspecting them never repeats a
        // write.
        val ownerTool =
            PixelMcpOwnerTools.write(tool) || OfferTools.write(tool) || AppointmentTools.write(tool)
        if (
            !ownerTool &&
                (PixelMcpWriteTools.handles(tool) ||
                    PixelMcpEntityWriteTools.handles(tool) ||
                    PixelMcpSessionTools.handles(tool))
        ) {
            if (request.length < 8)
                throw RevierRules.error("INVALID_INPUT", "Ursprüngliche Aktionskennung fehlt.")
            val receipt =
                repository.nativeReceipt(request)
                    ?: return JSONObject()
                        .put("state", "unknown")
                        .put("business_write_performed", false)
            val fingerprint =
                PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", original))
            if (
                receipt.optString("tool") != tool || receipt.optString("fingerprint") != fingerprint
            ) {
                throw RevierRules.error(
                    "REPLAY_MISMATCH",
                    "Gesicherte Argumente und vorhandener Aktionsbeleg stimmen nicht überein.",
                )
            }
            val id = receipt.optString("entity_id", receipt.optString("work_record_id"))
            if (id.isEmpty())
                return JSONObject().put("state", "unknown").put("business_write_performed", false)
            val result =
                receipt.optJSONObject("result")?.let { RevierRules.copy(it) } ?: JSONObject()
            result.put("entity_id", id).put("entity_type", receipt.optString("entity_type"))
            return JSONObject()
                .put(
                    "state",
                    if (
                        receipt.optBoolean("test_purged") ||
                            receipt.optBoolean("_result_content_removed") ||
                            result.optBoolean("_result_content_removed")
                    )
                        "content_removed"
                    else "confirmed",
                )
                .put("entity_id", id)
                .put("entity_type", receipt.optString("entity_type"))
                .put("result", result)
                .put("business_write_performed", false)
        }
        var key: String
        var fingerprint: String
        var kind: String? =
            when (tool) {
                "saveCustomer" -> "customer"
                "saveOrder" -> "order"
                "saveWorklog" -> "worklog"
                else -> null
            }
        if (kind != null) {
            request = original.optString("_request_id")
            val payload: JSONObject = RevierRules.copy(original)
            payload.remove("_request_id")
            if (kind!!.equals("worklog") && payload.optString("_session_id").isEmpty()) {
                payload.remove("_session_id")
            }
            key = ("editor_commit:" + PixelMcpWriteTools.hash(request))
            fingerprint =
                PixelMcpWriteTools.hash(JSONObject().put("kind", kind).put("payload", payload))
        } else {
            if (tool.equals("savePhoto")) {
                request = original.optString("_photo_request_id")
                if (request.length < 8) {
                    throw RevierRules.error("INVALID_INPUT", "Ursprüngliche Fotoaktion fehlt.")
                }
                val raw: String = repository.get(("photo-ui:" + request), "")
                if (raw.isEmpty()) {
                    return JSONObject()
                        .put("state", "unknown")
                        .put("business_write_performed", false)
                }
                val receipt: JSONObject = JSONObject(raw)
                val photoPayload: JSONObject = RevierRules.copy(original)
                photoPayload.remove("thumbnail_data")
                photoPayload.remove("_capture_context")
                if (!photoPayload.has("data")) {
                    photoPayload.remove("data")
                    if (!receipt.has("metadata_hash")) {
                        return JSONObject()
                            .put("state", "unknown")
                            .put("business_write_performed", false)
                    }
                    if (
                        (!PixelMcpWriteTools.hash(photoPayload)
                            .equals(receipt.optString("metadata_hash")))
                    ) {
                        throw RevierRules.error(
                            "REPLAY_MISMATCH",
                            "Gesicherter Fotobezug und Aktionsbeleg stimmen nicht überein.",
                        )
                    }
                } else {
                    if (
                        (!PixelMcpWriteTools.hash(photoPayload).equals(receipt.optString("hash")))
                    ) {
                        throw RevierRules.error(
                            "REPLAY_MISMATCH",
                            "Gesicherte Fotoangaben und Aktionsbeleg stimmen nicht überein.",
                        )
                    }
                }
                return JSONObject()
                    .put("state", "confirmed")
                    .put("entity_id", receipt.getString("id"))
                    .put("entity_type", "photo")
                    .put("business_write_performed", false)
            } else {
                if (
                    ((((!PixelMcpOwnerTools.write(tool) && !OfferTools.write(tool)) &&
                        !AppointmentTools.write(tool)) && !tool.equals("update_photo")) &&
                        !tool.equals("update_business_settings"))
                ) {
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Diese ursprüngliche Editoraktion wird nicht unterstützt.",
                    )
                }
                key = ("receipt:mcp_owner_" + PixelMcpWriteTools.hash(request))
                fingerprint =
                    PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", original))
            }
        }
        if (request.length < 8) {
            throw RevierRules.error("INVALID_INPUT", "Ursprüngliche Aktionskennung fehlt.")
        }
        val raw: String = repository.get(key, "")
        if (raw.isEmpty()) {
            return JSONObject().put("state", "unknown").put("business_write_performed", false)
        }
        val receipt: JSONObject = JSONObject(raw)
        if (!fingerprint.equals(receipt.optString("fingerprint"))) {
            throw RevierRules.error(
                "REPLAY_MISMATCH",
                "Gesicherte Argumente und vorhandener Aktionsbeleg stimmen nicht überein.",
            )
        }
        var result: JSONObject =
            (if (kind == null) receipt.getJSONObject("result")
            else JSONObject().put("entity_id", receipt.getString("id")))
        var id: String = result.optString("entity_id")
        if (id.isEmpty() && (result.optJSONObject("record") != null)) {
            id = result.getJSONObject("record").optString("id")
        }
        if (id.isEmpty() && (result.optJSONObject("photo") != null)) {
            id = result.getJSONObject("photo").optString("id")
        }
        if (id.isEmpty()) {
            for (field in
                arrayOf(
                    "offer_id",
                    "invoice_id",
                    "entry_id",
                    "order_id",
                    "appointment_id",
                    "property_id",
                    "inquiry_id",
                    "visit_id",
                    "material_id",
                    "equipment_id",
                    "care_plan_id",
                    "photo_id",
                    "payment_id",
                )) {
                if (!result.optString(field).isEmpty()) {
                    id = result.getString(field)
                    break
                }
            }
        }
        var entityType: String = (if (kind == null) result.optString("entity_type") else kind)
        if (tool.equals("update_business_settings")) {
            if (
                (!entityType.equals("business_settings") &&
                    (result.optJSONObject("settings") == null))
            ) {
                return JSONObject().put("state", "unknown").put("business_write_performed", false)
            }
            id = "business_settings"
            entityType = "settings"
        } else {
            if (tool.equals("update_photo")) {
                entityType = "photo"
            }
        }
        return JSONObject()
            .put(
                "state",
                (if (result.optBoolean("_result_content_removed")) "content_removed"
                else "confirmed"),
            )
            .put("entity_id", id)
            .put("entity_type", entityType)
            .put("result", result)
            .put("business_write_performed", false)
    }

    @Throws(Exception::class)
    private fun deleteEntity(
        args: JSONObject,
        state: JSONObject,
        request: String,
        materialConsumption: Boolean = false,
    ): JSONObject {
        val type: String = RevierRules.text(args, "entity", 40, true)
        var id: String = RevierRules.id(args, "entity_id", true)
        if (type !in DELETABLE && !(materialConsumption && type == "material_movement")) {
            throw RevierRules.error("INVALID_INPUT", "Nicht unterstützte Eintragsart.")
        }
        val old: JSONObject = load(type, id)
        RevierRules.cas(old, args.optString("expected_record_hash"))
        if (!true.equals(args.opt("confirmed"))) {
            throw RevierRules.error(
                "CONFIRMATION_REQUIRED",
                "Diese Änderung ausdrücklich bestätigen.",
            )
        }
        RevierRules.text(args, "reason", 4000, false)
        val running = repository.currentSession()
        if (
            references(running, id) &&
                running.optString("state") !in listOf("IDLE", "FINALIZED", "CANCELLED")
        ) {
            throw RevierRules.error(
                "DEPENDENCY_EXISTS",
                "Die laufende Arbeit verwendet diesen Eintrag.",
            )
        }
        val related: JSONArray = repository.list("" + RevierRules.ROOT + "entity:")
        run {
            var i: Int = 0
            while (i < related.length()) {
                val row: JSONObject = related.getJSONObject(i)
                if (row.optBoolean("deleted") || row.optString("id").equals(id)) {
                    i++
                    continue
                }
                if (references(row, id)) {
                    throw RevierRules.error(
                        "DEPENDENCY_EXISTS",
                        (("" +
                            ("Ein anderer lokaler Eintrag verweist noch auf diesen Datensatz: " +
                                row.optString("kind")) +
                            "/") + row.optString("id")),
                    )
                }
                i++
            }
        }
        if (references(state, id) || references(repository.sessionContexts(), id)) {
            throw RevierRules.error(
                "DEPENDENCY_EXISTS",
                "Bestehende Geschäftsdaten oder Arbeitssitzungen benötigen diesen Eintrag; Verknüpfungen einzeln prüfen.",
            )
        }
        var tombstone: JSONObject =
            JSONObject()
                .put("format_version", 1)
                .put("kind", type)
                .put("id", id)
                .put("deleted", true)
                .put("deleted_at", stamp())
                .put("request_id", request)
        repository.put(key(type, id), tombstone.toString())
        for (prefix in
            arrayOf(
                ("" + (("" + (("" + RevierRules.ROOT + "audit:") + type) + ":") + id) + ":"),
                (if (type.equals("care_plan")) ("" + (("" + RevierRules.ROOT + "rule:") + id) + ":")
                else ("" + RevierRules.ROOT + "unused:")),
            )) {
            val rows: JSONArray = repository.rawEntries(prefix)
            run {
                var i: Int = 0
                while (i < rows.length()) {
                    repository.put(rows.getJSONObject(i).getString("key"), tombstone.toString())
                    i++
                }
            }
        }
        for (prefix in arrayOf(("" + RevierRules.ROOT + "request:"), "receipt:")) {
            val rows: JSONArray = repository.rawEntries(prefix)
            run {
                var i: Int = 0
                while (i < rows.length()) {
                    val entry: JSONObject = rows.getJSONObject(i)
                    val receipt: JSONObject = JSONObject(entry.getString("value"))
                    if (
                        ((receipt.optJSONObject("result") != null) &&
                            references(receipt.getJSONObject("result"), id))
                    ) {
                        receipt.put(
                            "result",
                            JSONObject()
                                .put("_result_content_removed", true)
                                .put("entity", type)
                                .put("entity_id", id),
                        )
                        repository.put(entry.getString("key"), receipt.toString())
                    }
                    i++
                }
            }
        }
        return JSONObject()
            .put("entity", type)
            .put("entity_id", id)
            .put("purged", true)
            .put("historical_backups_unchanged", true)
    }

    @Throws(Exception::class)
    private fun directOrder(args: JSONObject, state: JSONObject, request: String): JSONObject {
        val old: JSONObject = load("inquiry", RevierRules.id(args, "inquiry_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        if (!true.equals(args.opt("confirmed"))) {
            throw RevierRules.error(
                "CONFIRMATION_REQUIRED",
                "Direkte Auftragsannahme ausdrücklich bestätigen.",
            )
        }
        if (!old.optString("order_id").isEmpty()) {
            throw RevierRules.error(
                "ORDER_ALREADY_LINKED",
                "Die Anfrage hat bereits einen Auftrag. Vorhandenen Auftrag öffnen.",
            )
        }
        val evidence: String = RevierRules.text(args, "evidence", 4000, true)
        val at: String = RevierRules.instant(RevierRules.text(args, "accepted_at", 80, true))
        customer(state, old.getString("customer_id"), true)
        var order: JSONObject =
            JSONObject()
                .put("id", RevierRules.newId("order", request))
                .put("customer_id", old.getString("customer_id"))
                .put("title", old.getString("title"))
                .put("description", old.optString("concern"))
                .put("status", "Geplant")
                .put("customer_visible", 1)
                .put("_request_id", ("direct_order_" + PixelMcpWriteTools.hash(request)))
        if (!args.optString("billing_customer_id").isEmpty()) {
            order.put("billing_customer_id", args.getString("billing_customer_id"))
        }
        val property: String = old.optString("property_id")
        if (!property.isEmpty()) {
            order.put("location", load("property", property).optString("address"))
        }
        var id: String = repository.createDirectOrder(order)
        var row: JSONObject =
            RevierRules.copy(old)
                .put("order_id", id)
                .put("status", "accepted")
                .put("acceptance_source", "direct")
                .put("accepted_at", at)
                .put("acceptance_evidence", evidence)
        var saved: JSONObject =
            save("inquiry", row, old, request, "Direkte Auftragsannahme mit Nachweis")
        return JSONObject()
            .put("record", saved)
            .put("inquiry", saved)
            .put("order_id", id)
            .put("invoice_created", false)
    }

    @Throws(Exception::class)
    private fun inspection(args: JSONObject, state: JSONObject, request: String): JSONObject {
        val old: JSONObject = load("inquiry", RevierRules.id(args, "inquiry_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        val starts: String = RevierRules.instant(RevierRules.text(args, "starts_at", 80, true))
        val ends: String = RevierRules.instant(RevierRules.text(args, "ends_at", 80, true))
        if (!Instant.parse(ends).isAfter(Instant.parse(starts))) {
            throw RevierRules.error("INVALID_INPUT", "Besichtigung benötigt eine positive Dauer.")
        }
        var zone: String = RevierRules.text(args, "timezone", 80, false)
        if (zone.isEmpty()) {
            zone = "Europe/Vienna"
        }
        ZoneId.of(zone)
        val previous: String = old.optString("inspection_appointment_id")
        val mode: String = args.optString("mode", (if (previous.isEmpty()) "first" else ""))
        if (
            (!Arrays.asList("first", "reschedule", "additional", "reopen").contains(mode) ||
                (!previous.isEmpty() && mode.equals("first")))
        ) {
            throw RevierRules.error(
                "INSPECTION_ACTION_REQUIRED",
                "Besichtigung verschieben, zusätzliche Besichtigung oder abgesagten Vorgang neu öffnen bewusst wählen.",
            )
        }
        var prior: JSONObject? =
            (if (previous.isEmpty()) null else canonical(state, "appointments", previous))
        if (mode.equals("reschedule") || mode.equals("reopen")) {
            if (prior == null) {
                throw RevierRules.error("REFERENCE_NOT_FOUND", "Bisherige Besichtigung fehlt.")
            }
            if (
                (!AppointmentRules.hash(prior).equals(args.optString("expected_appointment_hash")))
            ) {
                throw RevierRules.error("WRITE_CONFLICT", "Bisherige Besichtigung wurde geändert.")
            }
            if (
                ((mode.equals("reschedule") && prior.optString("status").equals("cancelled")) ||
                    (mode.equals("reopen") && !prior.optString("status").equals("cancelled")))
            ) {
                throw RevierRules.error(
                    "INSPECTION_ACTION_REQUIRED",
                    "Status der bisherigen Besichtigung zuerst prüfen.",
                )
            }
        }
        var appointment: JSONObject =
            JSONObject()
                .put("request_id", request)
                .put("customer_id", old.getString("customer_id"))
                .put("title", ("Besichtigung · " + old.getString("title")))
                .put("purpose", "inspection")
                .put("weather_profile", "inspection")
                .put("starts_at", starts)
                .put("ends_at", ends)
                .put("timezone", zone)
                .put("notes", RevierRules.text(args, "notes", 10000, false))
        val property: String = old.optString("property_id")
        appointment.put(
            "location",
            (if (property.isEmpty()) "" else load("property", property).optString("address")),
        )
        var result: JSONObject
        if (mode.equals("reschedule")) {
            var updates: JSONObject =
                JSONObject().put("starts_at", starts).put("ends_at", ends).put("timezone", zone)
            if (args.has("notes")) {
                updates.put("notes", args.getString("notes"))
            }
            var change: JSONObject =
                JSONObject()
                    .put("request_id", request)
                    .put("appointment_id", previous)
                    .put("expected_record_hash", args.getString("expected_appointment_hash"))
                    .put("updates", updates)
            McpSchema.validate(change, AppointmentTools.schema("update_appointment"))
            result = repository.changeInspection("update_appointment", change)
        } else {
            McpSchema.validate(appointment, AppointmentTools.schema("create_appointment"))
            result = repository.planInspection(appointment)
        }
        var appointmentId: String = result.optString("appointment_id", result.optString("id"))
        if (appointmentId.isEmpty() && (result.optJSONObject("appointment") != null)) {
            appointmentId = result.getJSONObject("appointment").optString("id")
        }
        if (
            (appointmentId.isEmpty() ||
                (mode.equals("reschedule") && !appointmentId.equals(previous)))
        ) {
            throw RevierRules.error(
                "INSPECTION_COMMIT_UNCONFIRMED",
                "Termin konnte nicht eindeutig bestätigt werden.",
            )
        }
        val row: JSONObject = RevierRules.copy(old)
        var visits: JSONArray? = row.optJSONArray("inspection_visits")
        if (visits == null) {
            visits = JSONArray()
        }
        if (!previous.isEmpty() && !mode.equals("reschedule")) {
            visits.put(
                JSONObject()
                    .put("appointment_id", previous)
                    .put("notes", row.optString("inspection_notes"))
                    .put(
                        "measurements",
                        (if (row.optJSONArray("measurements") == null) JSONArray()
                        else row.getJSONArray("measurements")),
                    )
                    .put(
                        "photo_ids",
                        (if (row.optJSONArray("photo_ids") == null) JSONArray()
                        else row.getJSONArray("photo_ids")),
                    )
            )
            row.put("inspection_notes", "")
                .put("measurements", JSONArray())
                .put("photo_ids", JSONArray())
        }
        row.put("inspection_visits", visits)
            .put("inspection_appointment_id", appointmentId)
            .put("status", "inspection_planned")
        val saved: JSONObject = save("inquiry", row, old, request, ("Besichtigung: " + mode))
        return JSONObject()
            .put("record", saved)
            .put("inquiry", saved)
            .put("appointment_id", appointmentId)
            .put("inspection_action", mode)
            .put("external_calendar_status", "unknown")
    }

    @Throws(Exception::class)
    private fun needs(args: JSONObject): JSONArray {
        val values: JSONArray? = args.optJSONArray("material_needs")
        if (values == null) {
            if (args.has("material_needs")) {
                throw RevierRules.error("INVALID_INPUT", "Materialbedarf als Liste angeben.")
            }
            return JSONArray()
        }
        if (values.length() > 200) {
            throw RevierRules.error(
                "INVALID_INPUT",
                "Zu viele Materialpositionen in einem Einsatz.",
            )
        }
        val normalized: JSONArray = JSONArray()
        val seen: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while (i < values.length()) {
                val item: JSONObject = values.getJSONObject(i)
                val material: String = RevierRules.id(item, "material_id", true)
                if (!seen.add(material)) {
                    throw RevierRules.error(
                        "DUPLICATE_MATERIAL",
                        "Materialbedarf pro Katalogeintrag zusammenfassen.",
                    )
                }
                val source: JSONObject = load("material", material)
                if (!source.optBoolean("active", true)) {
                    throw RevierRules.error(
                        "MATERIAL_INACTIVE",
                        "Inaktives Material nicht neu einplanen.",
                    )
                }
                val quantity: String = RevierRules.decimal(item, "quantity_decimal", true, false)
                if (BigDecimal(quantity).signum() == 0) {
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Materialbedarf muss größer als null sein.",
                    )
                }
                normalized.put(
                    JSONObject()
                        .put("material_id", material)
                        .put("quantity_decimal", quantity)
                        .put("unit", source.getString("unit"))
                        .put("name", source.getString("name"))
                )
                i++
            }
        }
        return normalized
    }

    @Throws(Exception::class)
    private fun equipmentIds(args: JSONObject): JSONArray {
        val values: JSONArray? = args.optJSONArray("equipment_ids")
        if (values == null) {
            if (args.has("equipment_ids")) {
                throw RevierRules.error("INVALID_INPUT", "Gerätebedarf als Liste angeben.")
            }
            return JSONArray()
        }
        if (values.length() > 200) {
            throw RevierRules.error("INVALID_INPUT", "Zu viele Geräte in einem Einsatz.")
        }
        var out: JSONArray = JSONArray()
        val seen: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while (i < values.length()) {
                var value: String = values.getString(i)
                load("equipment", value)
                if (!seen.add(value)) {
                    throw RevierRules.error(
                        "DUPLICATE_EQUIPMENT",
                        "Gerät wurde mehrfach ausgewählt.",
                    )
                }
                out.put(value)
                i++
            }
        }
        return out
    }

    @Throws(Exception::class)
    private fun equipmentWarnings(ids: JSONArray): JSONArray {
        var out: JSONArray = JSONArray()
        run {
            var i: Int = 0
            while (i < ids.length()) {
                val item: JSONObject = load("equipment", ids.getString(i))
                if (
                    (!item.optBoolean("active", true) ||
                        !item.optString("condition", "ready").equals("ready"))
                ) {
                    out.put(
                        JSONObject()
                            .put("code", "EQUIPMENT_UNAVAILABLE")
                            .put("equipment_id", item.getString("id"))
                            .put("name", item.getString("name"))
                            .put("condition", item.optString("condition"))
                    )
                }
                i++
            }
        }
        return out
    }

    @Throws(Exception::class)
    private fun ruleVersions(planId: String): JSONArray {
        var rows: JSONArray =
            repository.list("" + (("" + RevierRules.ROOT + "rule:") + planId) + ":")
        run {
            var i: Int = 0
            while (i < rows.length()) {
                RevierRules.version(rows.getJSONObject(i))
                i++
            }
        }
        return rows
    }

    @Throws(Exception::class)
    private fun validateVisit(row: JSONObject, state: JSONObject) {
        val property: String = RevierRules.id(row, "property_id", true)
        var `object`: JSONObject = load("property", property)
        val ending: Boolean = row.optString("status").equals("cancelled")
        if (!ending && !`object`.optBoolean("active", true)) {
            throw RevierRules.error("PROPERTY_INACTIVE", "Objekt ist inaktiv.")
        }
        row.put("customer_id", `object`.getString("customer_id"))
        customer(state, row.getString("customer_id"), !ending)
        var order: String = RevierRules.id(row, "order_id", false)
        if (
            (!order.isEmpty() &&
                !canonical(state, "orders", order)
                    .optString("customer_id")
                    .equals(row.getString("customer_id")))
        ) {
            throw RevierRules.error(
                "RELATION_MISMATCH",
                "Auftrag und Einsatzobjekt gehören zu verschiedenen Kunden.",
            )
        }
        row.put("order_id", order)
            .put("title", RevierRules.text(row, "title", 240, true))
            .put("date", RevierRules.date(row, "date", true))
            .put("planned_minutes", RevierRules.integer(row, "planned_minutes", 1, 1440, -1))
            .put("notes", RevierRules.text(row, "notes", 10000, false))
            .put("timezone", "Europe/Vienna")
        if (row.getInt("planned_minutes") < 1) {
            throw RevierRules.error("INVALID_INPUT", "Geplante Dauer angeben.")
        }
        val start: String = RevierRules.time(row, "window_start")
        val end: String = RevierRules.time(row, "window_end")
        if (
            ((start.isEmpty() != end.isEmpty()) ||
                (!start.isEmpty() && !LocalTime.parse(end).isAfter(LocalTime.parse(start))))
        ) {
            throw RevierRules.error(
                "INVALID_INPUT",
                "Zeitfenster mit Beginn und späterem Ende angeben.",
            )
        }
        row.put("window_start", start).put("window_end", end)
        val status: String = row.optString("status", "planned")
        if (
            (!Arrays.asList("planned", "in_progress", "completed", "partial", "cancelled")
                .contains(status))
        ) {
            throw RevierRules.error("INVALID_INPUT", "Ungültiger Besuchsstatus.")
        }
        row.put("status", status)
        val remaining: Int = RevierRules.integer(row, "remaining_minutes", 0, 1440, 0)
        if (status.equals("partial") && (remaining < 1)) {
            throw RevierRules.error("INVALID_INPUT", "Offenen Arbeitsrest angeben.")
        }
        row.put("remaining_minutes", remaining)
            .put("material_needs", needs(row))
            .put("equipment_ids", equipmentIds(row))
    }

    @Throws(Exception::class)
    private fun overlaps(row: JSONObject): JSONArray {
        val warnings: JSONArray = JSONArray()
        val start: String = row.optString("window_start")
        val end: String = row.optString("window_end")
        if (start.isEmpty() || !RevierRules.activeVisit(row)) {
            return warnings
        }
        val visits: JSONArray = records("visit")
        run {
            var i: Int = 0
            while (i < visits.length()) {
                val other: JSONObject = visits.getJSONObject(i)
                if (
                    (((row.getString("id").equals(other.getString("id")) ||
                        !row.getString("date").equals(other.optString("date"))) ||
                        !RevierRules.activeVisit(other)) ||
                        other.optString("window_start").isEmpty())
                ) {
                    i++
                    continue
                }
                if (
                    ((start.compareTo(other.getString("window_end")) < 0) &&
                        (end.compareTo(other.getString("window_start")) > 0))
                ) {
                    warnings.put(
                        JSONObject()
                            .put("code", "LOCAL_VISIT_OVERLAP")
                            .put("visit_id", other.getString("id"))
                    )
                }
                i++
            }
        }
        return warnings
    }

    @Throws(Exception::class)
    private fun visit(
        operation: String,
        args: JSONObject,
        state: JSONObject,
        request: String,
    ): JSONObject {
        val creating: Boolean = operation.equals("plan_visit")
        var old: JSONObject? =
            (if (creating) null else load("visit", RevierRules.id(args, "visit_id", true)))
        val changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (old != null) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
            val canonicalAppointmentId =
                PlanningBoardRules.aliases(
                        state.optJSONArray("appointments") ?: JSONArray(),
                        JSONArray().put(old),
                    )[old.getString("id")]
            if (canonicalAppointmentId != null) {
                val appointment = canonical(state, "appointments", canonicalAppointmentId)
                throw CanonicalAppointmentRequired(
                    canonicalAppointmentId,
                    AppointmentRules.hash(appointment),
                    old.getString("id"),
                )
            }
        }
        var row: JSONObject =
            base(
                "visit",
                (if (creating) RevierRules.newId("visit", request) else old!!.getString("id")),
                old,
            )
        if (
            ((!creating && !old!!.optString("care_plan_id").isEmpty()) &&
                !args.optString("scope").equals("only_this"))
        ) {
            throw RevierRules.error(
                "SERIES_SCOPE_REQUIRED",
                "Für diesen Besuch ausdrücklich nur diesen Termin wählen.",
            )
        }
        for (field in
            arrayOf(
                "property_id",
                "order_id",
                "title",
                "date",
                "planned_minutes",
                "window_start",
                "window_end",
                "notes",
                "material_needs",
                "equipment_ids",
                "status",
                "remaining_minutes",
                "parent_visit_id",
                "planning_adopted",
            )) {
            if (changes.has(field)) {
                row.put(field, changes.get(field))
            }
        }
        if (
            ((old != null) &&
                (!old.getString("property_id").equals(row.optString("property_id")) ||
                    !old.optString("order_id").equals(row.optString("order_id"))))
        ) {
            throw RevierRules.error(
                "RELATION_IMMUTABLE",
                "Besuch behält seinen ursprünglichen Objekt-/Auftragsbezug.",
            )
        }
        var dateChanged: Boolean =
            ((old != null) && !old.getString("date").equals(row.optString("date")))
        var statusChanged: Boolean =
            ((old != null) && !old.getString("status").equals(row.optString("status")))
        val reason: String = RevierRules.text(args, "reason", 2000, false)
        if ((dateChanged || statusChanged) && reason.isEmpty()) {
            throw RevierRules.error(
                "CORRECTION_REASON_REQUIRED",
                "Änderung kurz begründen; ursprünglicher Besuch bleibt nachvollziehbar.",
            )
        }
        if (
            (((old != null) && old.optString("status").equals("completed")) &&
                ((((dateChanged || statusChanged) || changes.has("planned_minutes")) ||
                    changes.has("window_start")) || changes.has("window_end")))
        ) {
            throw RevierRules.error(
                "COMPLETED_VISIT_IMMUTABLE",
                "Abgeschlossenen Besuch nicht rückwirkend umplanen. Offenen Rest getrennt planen.",
            )
        }
        if (
            (((old != null) &&
                Arrays.asList("cancelled", "superseded").contains(old.optString("status"))) &&
                statusChanged)
        ) {
            throw RevierRules.error(
                "VISIT_NOT_REACTIVATABLE",
                "Abgesagten Besuch nicht still reaktivieren. Neuen Besuch planen.",
            )
        }
        validateVisit(row, state)
        if (old != null) {
            row.put("manual_override", true)
        } else {
            row.put("manual_override", true)
        }
        val parent: String = RevierRules.id(row, "parent_visit_id", false)
        if (!parent.isEmpty()) {
            val previous: JSONObject = load("visit", parent)
            if (
                (!previous.optString("status").equals("partial") ||
                    !previous.getString("property_id").equals(row.getString("property_id")))
            ) {
                throw RevierRules.error(
                    "RELATION_MISMATCH",
                    "Restbesuch benötigt den passenden teilerledigten Vorgänger.",
                )
            }
            var all: JSONArray = records("visit")
            run {
                var i: Int = 0
                while (i < all.length()) {
                    val child: JSONObject = all.getJSONObject(i)
                    if (
                        ((!child.getString("id").equals(row.getString("id")) &&
                            parent.equals(child.optString("parent_visit_id"))) &&
                            RevierRules.activeVisit(child))
                    ) {
                        throw RevierRules.error(
                            "REMAINING_ALREADY_PLANNED",
                            "Für diesen Arbeitsrest ist bereits ein aktiver Besuch geplant.",
                        )
                    }
                    i++
                }
            }
        }
        if (!reason.isEmpty()) {
            row.put("status_reason", reason)
        }
        val saved: JSONObject = save("visit", row, old, request, reason)
        var effects: JSONArray =
            (if (dateChanged || statusChanged)
                markToursForVisit(row.getString("id"), request, reason)
            else JSONArray())
        return JSONObject()
            .put("record", saved)
            .put("visit", saved)
            .put("visit_id", row.getString("id"))
            .put("tour_effects", effects)
            .put("overlap_warnings", overlaps(row))
            .put("equipment_warnings", equipmentWarnings(row.getJSONArray("equipment_ids")))
            .put("external_calendar_status", "unknown")
    }

    @Throws(Exception::class)
    private fun emptyTour(day: String): JSONObject {
        return JSONObject()
            .put("format_version", 1)
            .put("kind", "tour")
            .put("id", ("tour_" + day))
            .put("date", day)
            .put("visit_ids", JSONArray())
            .put("district", "")
            .put("needs_review", false)
    }

    @Throws(Exception::class)
    private fun tour(day: String): JSONObject {
        var row: JSONObject? = find("tour", ("tour_" + day))
        if (row == null) {
            row = emptyTour(day)
        }
        val available =
            PlanningBoardRules.tourEntries(
                repository.effective().optJSONArray("appointments") ?: JSONArray(),
                records("visit"),
                day,
            )
        val byId = available.associateBy { it.getString("id") }
        val selected =
            (0 until row.getJSONArray("visit_ids").length()).map {
                row.getJSONArray("visit_ids").getString(it)
            }
        val ordered =
            selected.mapNotNull { byId[it] } + available.filter { it.getString("id") !in selected }
        val stops = JSONArray()
        for ((index, source) in ordered.withIndex()) {
            stops.put(
                JSONObject(source.toString())
                    .put("position", index)
                    .put("tour_date_matches", true)
                    .put(
                        "equipment_warnings",
                        equipmentWarnings(source.optJSONArray("equipment_ids") ?: JSONArray()),
                    )
            )
        }
        val shown: JSONObject = RevierRules.view(row)
        return JSONObject()
            .put("record", shown)
            .put("tour", shown)
            .put("stops", stops)
            .put("record_hash", shown.getString("record_hash"))
            .put("route_optimization_claimed", false)
    }

    @Throws(Exception::class)
    private fun markToursForVisit(visitId: String, request: String, reason: String): JSONArray {
        var changed: JSONArray = JSONArray()
        val tours: JSONArray = records("tour")
        run {
            var i: Int = 0
            while (i < tours.length()) {
                val before: JSONObject = tours.getJSONObject(i)
                val ids: JSONArray = before.getJSONArray("visit_ids")
                run {
                    var j: Int = 0
                    while (j < ids.length()) {
                        if (ids.getString(j).equals(visitId)) {
                            var row: JSONObject =
                                RevierRules.copy(before)
                                    .put("needs_review", true)
                                    .put("review_reason", reason)
                            save("tour", row, before, request, reason)
                            changed.put(
                                JSONObject()
                                    .put("tour_id", row.getString("id"))
                                    .put("date", row.getString("date"))
                                    .put("needs_review", true)
                            )
                            break
                        }
                        j++
                    }
                }
                i++
            }
        }
        return changed
    }

    @Throws(Exception::class)
    private fun changeTour(operation: String, args: JSONObject, request: String): JSONObject {
        var day: String = RevierRules.date(args, "date", true)
        val tourId: String = ("tour_" + day)
        val old: JSONObject? = find("tour", tourId)
        val row: JSONObject = (if (old == null) emptyTour(day) else RevierRules.copy(old))
        RevierRules.cas(row, args.optString("expected_record_hash"))
        val current = tour(day).getJSONArray("stops")
        val available =
            (0 until current.length()).map { current.getJSONObject(it).getString("id") }.toSet()
        val ids =
            if (operation == "set_day_tour") JSONArray(args.getJSONArray("visit_ids").toString())
            else
                JSONArray(
                    (0 until current.length()).map { current.getJSONObject(it).getString("id") }
                )
        require(ids.length() <= 100) { "Eine Tagestour umfasst höchstens 100 Stopps." }
        val selected = mutableSetOf<String>()
        for (i in 0 until ids.length()) {
            val id = ids.getString(i)
            if (!selected.add(id))
                throw RevierRules.error(
                    "DUPLICATE_VISIT",
                    "Ein Termin darf nur einmal in der Tour stehen.",
                )
            if (id !in available)
                throw RevierRules.error(
                    "VISIT_NOT_PLANNABLE",
                    "Nur aktive Termine dieses Tages in die Tour übernehmen.",
                )
        }
        if (operation == "set_day_tour")
            row.put("district", RevierRules.text(args, "district", 200, false))
        else {
            val target = RevierRules.id(args, "visit_id", true)
            val direction = RevierRules.text(args, "direction", 8, true)
            require(direction in setOf("up", "down")) { "Nach oben oder nach unten wählen." }
            val at =
                (0 until ids.length()).firstOrNull { ids.getString(it) == target }
                    ?: throw RevierRules.error(
                        "REFERENCE_NOT_FOUND",
                        "Termin gehört nicht zu dieser Tour.",
                    )
            val next = at + if (direction == "up") -1 else 1
            if (next in 0 until ids.length()) {
                val other = ids.get(next)
                ids.put(next, target).put(at, other)
            }
        }
        row.put("visit_ids", ids).put("needs_review", false).put("review_reason", "")
        var saved: JSONObject =
            save(
                "tour",
                row,
                old,
                request,
                (if (operation.equals("move_tour_stop")) "Tourreihenfolge geändert"
                else "Tagestour bestätigt"),
            )
        return JSONObject()
            .put("record", saved)
            .put("tour", saved)
            .put("route_optimization_claimed", false)
    }

    @Throws(Exception::class)
    private fun material(args: JSONObject, request: String): JSONObject {
        McpSchema.validate(args, LocalWorkflowTools.schema("save_material"))
        var id: String = RevierRules.id(args, "material_id", false)
        val old: JSONObject? = (if (id.isEmpty()) null else load("material", id))
        if (old != null) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject =
            base("material", (if (old == null) RevierRules.newId("material", request) else id), old)
        for (field in
            arrayOf(
                "name",
                "unit",
                "purchase_price_minor",
                "billing_price_minor",
                "notes",
                "active",
            )) {
            if (args.has(field)) {
                row.put(field, args.get(field))
            }
        }
        row.put("name", RevierRules.text(row, "name", 240, true))
            .put("unit", RevierRules.text(row, "unit", 60, true))
            .put("notes", RevierRules.text(row, "notes", 10000, false))
            .put("active", RevierRules.bool(row, "active", true))
        for (price in arrayOf("purchase_price_minor", "billing_price_minor")) {
            if (row.has(price) && !row.isNull(price)) {
                row.put(price, RevierRules.minor(row, price))
            }
        }
        val saved: JSONObject = save("material", row, old, request, "Materialkatalog gespeichert")
        return JSONObject()
            .put("record", saved)
            .put("material", saved)
            .put("material_id", row.getString("id"))
    }

    @Throws(Exception::class)
    private fun linkContext(row: JSONObject, state: JSONObject, propertyRequired: Boolean) {
        val session: String = RevierRules.id(row, "session_id", false)
        if (!session.isEmpty()) {
            val origin: JSONObject? = repository.session(session)
            if (origin == null) {
                throw RevierRules.error(
                    "REFERENCE_NOT_FOUND",
                    "Arbeitssitzung wurde nicht gefunden.",
                )
            }
            for (field in arrayOf("customer_id", "property_id", "visit_id", "order_id")) {
                var original: String = origin.optString(field)
                if (original.isEmpty()) {
                    continue
                }
                val selected: String = row.optString(field)
                if (!selected.isEmpty() && !selected.equals(original)) {
                    throw RevierRules.error(
                        "RELATION_MISMATCH",
                        "Arbeitssitzung und ausgewählter Einsatz passen nicht zusammen.",
                    )
                }
                row.put(field, original)
            }
        }
        var property: String = RevierRules.id(row, "property_id", false)
        val visit: String = RevierRules.id(row, "visit_id", false)
        if (!visit.isEmpty()) {
            val source: JSONObject = load("visit", visit)
            if (!property.isEmpty() && !property.equals(source.getString("property_id"))) {
                throw RevierRules.error(
                    "RELATION_MISMATCH",
                    "Einsatz und Objekt passen nicht zusammen.",
                )
            }
            property = source.getString("property_id")
            if (
                (!row.optString("customer_id").isEmpty() &&
                    !row.getString("customer_id").equals(source.getString("customer_id")))
            ) {
                throw RevierRules.error(
                    "RELATION_MISMATCH",
                    "Arbeitssitzung und Einsatzkunde passen nicht zusammen.",
                )
            }
            if (
                ((!row.optString("order_id").isEmpty() &&
                    !source.optString("order_id").isEmpty()) &&
                    !row.getString("order_id").equals(source.getString("order_id")))
            ) {
                throw RevierRules.error(
                    "RELATION_MISMATCH",
                    "Arbeitssitzung und Auftrag passen nicht zusammen.",
                )
            }
            var order: String = source.optString("order_id")
            if (!order.isEmpty()) {
                val assigned: JSONObject = canonical(state, "orders", order)
                if (!source.getString("customer_id").equals(assigned.optString("customer_id"))) {
                    throw RevierRules.error(
                        "RELATION_MISMATCH",
                        "Einsatz und zugeordneter Auftrag passen nicht zusammen.",
                    )
                }
                row.put("order_id", order)
            }
            row.put("visit_id", visit)
        }
        if (propertyRequired && property.isEmpty()) {
            throw RevierRules.error(
                "PROPERTY_REQUIRED",
                "Für den geführten Einsatz ein Objekt oder einen zugeordneten Besuch wählen.",
            )
        }
        if (!property.isEmpty()) {
            var `object`: JSONObject = load("property", property)
            customer(state, `object`.getString("customer_id"), false)
            if (
                (!row.optString("customer_id").isEmpty() &&
                    !row.getString("customer_id").equals(`object`.getString("customer_id")))
            ) {
                throw RevierRules.error(
                    "RELATION_MISMATCH",
                    "Arbeitssitzung und Objektkunde passen nicht zusammen.",
                )
            }
            row.put("property_id", property).put("customer_id", `object`.getString("customer_id"))
        }
    }

    @Throws(Exception::class)
    private fun movement(args: JSONObject, state: JSONObject, request: String): JSONObject {
        McpSchema.validate(args, LocalWorkflowTools.schema("record_material_consumption"))
        val material = load("material", RevierRules.id(args, "material_id", true))
        RevierRules.cas(material, args.optString("expected_record_hash"))
        require(args.optString("kind", "consume") == "consume") {
            "Nur Materialverbrauch ist möglich."
        }
        val workId = RevierRules.id(args, "worklog_id", false)
        val sessionId = RevierRules.id(args, "session_id", false)
        require(workId.isNotBlank() != sessionId.isNotBlank()) {
            "Eine Arbeit oder laufende Sitzung auswählen."
        }
        val session =
            if (sessionId.isBlank()) null
            else
                (repository.session(sessionId)
                    ?: throw RevierRules.error("REFERENCE_NOT_FOUND", "Die Arbeitssitzung fehlt."))
        if (session != null && session.optString("state") in setOf("CANCELLED", "IDLE"))
            throw RevierRules.error(
                "SESSION_NOT_ACTIVE",
                "Eine verworfene Sitzung kann keinen Materialverbrauch aufnehmen.",
            )
        val savedWorkId =
            if (workId.isNotBlank()) workId
            else session?.optString("finalized_worklog_id").orEmpty()
        val work = if (savedWorkId.isBlank()) null else canonical(state, "worklogs", savedWorkId)
        val quantity = BigDecimal(RevierRules.decimal(args, "quantity_decimal", true, false))
        require(quantity.signum() > 0) { "Eine positive Verbrauchsmenge eingeben." }
        val row =
            base("material_movement", RevierRules.newId("consumption", request), null)
                .put("material_id", material.getString("id"))
                .put("movement_kind", "consume")
                .put("quantity_decimal", quantity.stripTrailingZeros().toPlainString())
                .put("unit", material.getString("unit"))
                .put("material_snapshot", RevierRules.copy(material))
                .put("reason", RevierRules.text(args, "reason", 2000, false))
        if (session != null) row.put("session_id", sessionId)
        if (work != null) {
            row.put("worklog_id", work.getString("id"))
            for (field in arrayOf("customer_id", "property_id", "visit_id", "order_id")) {
                if (work.has(field)) row.put(field, work.get(field))
            }
        }
        linkContext(row, state, false)
        val saved = save("material_movement", row, null, request, "Materialverbrauch erfasst")
        return JSONObject()
            .put("record", saved)
            .put("movement", saved)
            .put("material", RevierRules.view(material))
    }

    /** Technical completion step within the existing work-session transaction. */
    fun linkSessionMaterials(sessionId: String, worklogId: String) {
        val state = repository.effective()
        val work = canonical(state, "worklogs", worklogId)
        for (old in SettlementRules.rows(records("material_movement"))) {
            if (
                old.optString("session_id") != sessionId ||
                    old.optString("movement_kind") != "consume"
            )
                continue
            if (old.optString("worklog_id").isNotBlank()) {
                require(old.getString("worklog_id") == worklogId) {
                    "Materialverbrauch gehört bereits zu einer anderen Arbeit."
                }
                continue
            }
            val linked = RevierRules.copy(old).put("worklog_id", worklogId)
            for (field in arrayOf("customer_id", "property_id", "visit_id", "order_id")) {
                val previous = linked.optString(field)
                val selected = work.optString(field)
                require(previous.isBlank() || previous == selected) {
                    "Materialverbrauch und Arbeit passen nicht zusammen."
                }
                if (selected.isNotBlank()) linked.put(field, selected)
            }
            save(
                "material_movement",
                linked,
                old,
                "material-link-" +
                    PixelMcpWriteTools.hash(
                        sessionId + ":" + worklogId + ":" + old.getString("id")
                    ),
                "Materialverbrauch der Arbeit zugeordnet",
            )
        }
    }

    @Throws(Exception::class)
    private fun equipment(args: JSONObject, request: String): JSONObject {
        var id: String = RevierRules.id(args, "equipment_id", false)
        val old: JSONObject? = (if (id.isEmpty()) null else load("equipment", id))
        if (old != null) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject =
            base(
                "equipment",
                (if (old == null) RevierRules.newId("equipment", request) else id),
                old,
            )
        for (field in arrayOf("name", "condition", "maintenance_due", "notes", "active")) {
            if (args.has(field)) {
                row.put(field, args.get(field))
            }
        }
        row.put("name", RevierRules.text(row, "name", 240, true))
            .put("notes", RevierRules.text(row, "notes", 10000, false))
            .put("active", RevierRules.bool(row, "active", true))
            .put("maintenance_due", RevierRules.date(row, "maintenance_due", false))
        val condition: String = row.optString("condition", "ready")
        if (!Arrays.asList("ready", "maintenance", "defective").contains(condition)) {
            throw RevierRules.error("INVALID_INPUT", "Gerätezustand bewusst wählen.")
        }
        row.put("condition", condition)
        val upload: String = RevierRules.id(args, "profile_photo_upload_id", false)
        if (upload.isNotEmpty()) {
            if (
                ((old != null) && (old.optBoolean("archived") || !old.optBoolean("active", true)))
            ) {
                throw RevierRules.error(
                    "EQUIPMENT_INACTIVE",
                    "Archiviertes oder inaktives Gerät zuerst bewusst prüfen.",
                )
            }
            val photo =
                repository.prepareEquipmentProfilePhoto(upload, row.getString("id"), request)
            EquipmentProfilePhotoRules.validate(photo, row.getString("id"))
            repository.verifyEquipmentProfilePhoto(photo)
            row.put("profile_photo", photo).put("format_version", 3)
        }
        if (args.has("accessories") || args.has("current_chain_id")) {
            val previous = old?.optJSONArray("accessories") ?: JSONArray()
            val entries =
                if (args.has("accessories")) args.getJSONArray("accessories") else previous
            val accessories = JSONArray()
            val identities = HashSet<String>()
            for (index in 0 until entries.length()) {
                val input = entries.getJSONObject(index)
                val accessoryId = RevierRules.id(input, "id", true)
                if (!identities.add(accessoryId))
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Zubehörkennungen müssen innerhalb der Maschine eindeutig sein.",
                    )
                val before =
                    (0 until previous.length())
                        .map { previous.getJSONObject(it) }
                        .firstOrNull { it.optString("id") == accessoryId }
                val item =
                    JSONObject().put("id", accessoryId).put("equipment_id", row.getString("id"))
                val type = input.optString("type", "other")
                if (type !in listOf("chain", "other"))
                    throw RevierRules.error("INVALID_INPUT", "Zubehörart auswählen.")
                item.put("type", type)
                for (field in
                    listOf(
                        "name",
                        "manufacturer",
                        "model",
                        "pitch",
                        "gauge",
                        "drive_links",
                        "article_number",
                        "tooth_type",
                        "file_size",
                        "notes",
                    )) {
                    if (input.has(field))
                        item.put(
                            field,
                            RevierRules.text(
                                input,
                                field,
                                if (field == "notes") 10000 else 240,
                                false,
                            ),
                        )
                }
                if (listOf("name", "manufacturer", "model").all { item.optString(it).isBlank() })
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Zubehör mit Bezeichnung oder Hersteller/Modell erfassen.",
                    )
                before?.optJSONObject("photo")?.let { item.put("photo", RevierRules.copy(it)) }
                val uploadId = RevierRules.id(input, "photo_upload_id", false)
                if (uploadId.isNotEmpty()) {
                    if (row.optBoolean("archived") || !row.optBoolean("active", true))
                        throw RevierRules.error(
                            "EQUIPMENT_INACTIVE",
                            "Archiviertes oder inaktives Gerät zuerst bewusst prüfen.",
                        )
                    val photo =
                        repository.prepareEquipmentProfilePhoto(
                            uploadId,
                            row.getString("id"),
                            request + "_" + PixelMcpWriteTools.hash(accessoryId).take(16),
                        )
                    EquipmentProfilePhotoRules.validate(photo, row.getString("id"))
                    repository.verifyEquipmentProfilePhoto(photo)
                    item.put("photo", photo)
                }
                accessories.put(item)
            }
            val current = args.optString("current_chain_id", row.optString("current_chain_id"))
            if (
                current.isNotEmpty() &&
                    (0 until accessories.length()).none {
                        accessories.getJSONObject(it).optString("id") == current &&
                            accessories.getJSONObject(it).optString("type") == "chain"
                    }
            ) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Die aktuelle Kette muss dieser Maschine zugeordnet sein.",
                )
            }
            row.put("accessories", accessories).put("current_chain_id", current)
        }
        val saved: JSONObject = save("equipment", row, old, request, "Gerätezustand gespeichert")
        return JSONObject()
            .put("record", saved)
            .put("equipment", saved)
            .put("equipment_id", row.getString("id"))
    }

    @Throws(Exception::class)
    private fun maintenanceDevice(args: JSONObject): JSONObject {
        val old: JSONObject = load("equipment", RevierRules.id(args, "equipment_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        if (old.optBoolean("archived") || !old.optBoolean("active", true)) {
            throw RevierRules.error(
                "EQUIPMENT_INACTIVE",
                "Archiviertes oder inaktives Gerät zuerst bewusst prüfen.",
            )
        }
        EquipmentMaintenanceRules.plans(old)
        return old
    }

    @Throws(Exception::class)
    private fun equipmentMaintenancePlan(args: JSONObject, request: String): JSONObject {
        val old: JSONObject = maintenanceDevice(args)
        val row: JSONObject = RevierRules.copy(old)
        val plans: JSONArray = EquipmentMaintenanceRules.plans(row)
        val planId: String = RevierRules.id(args, "plan_id", false)
        val operation: String = RevierRules.text(args, "operation", 30, true)
        val reason: String = RevierRules.text(args, "reason", 2000, false)
        var plan: JSONObject
        if (planId.isEmpty()) {
            if (!operation.equals("configure")) {
                throw RevierRules.error("INVALID_INPUT", "Zuerst einen Wartungsplan anlegen.")
            }
            if (args.has("next_due_on")) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Erstes Intervall mit Beginn und Länge festlegen; Einzeltermine anschließend bewusst vorziehen.",
                )
            }
            if (plans.length() >= EquipmentMaintenanceRules.MAX_PLANS) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "An diesem Gerät sind bereits zu viele Wartungspläne vorhanden.",
                )
            }
            plan =
                JSONObject()
                    .put("id", RevierRules.newId("maintenance_plan", request))
                    .put("title", RevierRules.text(args, "title", 240, true))
                    .put("interval_value", RevierRules.integer(args, "interval_value", 1, 3650, -1))
                    .put("interval_unit", RevierRules.text(args, "interval_unit", 12, true))
                    .put("anchor_on", RevierRules.date(args, "starts_on", true))
                    .put("created_at", stamp())
            EquipmentMaintenanceRules.recalculate(plan)
            plans.put(plan)
        } else {
            plan = EquipmentMaintenanceRules.find(plans, planId)
            if (reason.isEmpty()) {
                throw RevierRules.error("INVALID_INPUT", "Grund für die Planänderung angeben.")
            }
            if (!operation.equals("configure") && args.has("title")) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Eine Terminvorziehung ändert nicht die Tätigkeit.",
                )
            }
            if (args.has("starts_on")) {
                throw RevierRules.error(
                    "INVALID_INPUT",
                    "Eine Planänderung setzt vergangene Wartungen nicht zurück.",
                )
            }
            if (operation.equals("bring_forward")) {
                if (args.has("interval_value") || args.has("interval_unit")) {
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Einmaliges Vorziehen verändert das Intervall nicht.",
                    )
                }
                val due: String = RevierRules.date(args, "next_due_on", true)
                if (
                    ((due.compareTo(today().toString()) < 0) ||
                        (due.compareTo(plan.getString("next_due_on")) >= 0))
                ) {
                    throw RevierRules.error(
                        "INVALID_INPUT",
                        "Einen früheren Termin ab heute wählen.",
                    )
                }
                plan.put("one_off_due_on", due)
                EquipmentMaintenanceRules.recalculate(plan)
            } else {
                if (operation.equals("shorten")) {
                    if (args.has("next_due_on")) {
                        throw RevierRules.error(
                            "INVALID_INPUT",
                            "Für ein kürzeres Intervall Länge und Einheit wählen.",
                        )
                    }
                    EquipmentMaintenanceRules.shorten(plan, RevierRules.copy(args), reason)
                } else {
                    if (operation.equals("configure")) {
                        if (args.has("next_due_on")) {
                            throw RevierRules.error(
                                "INVALID_INPUT",
                                "Einzeltermin über Früher warten ändern.",
                            )
                        }
                        if (args.has("title")) {
                            plan.put("title", RevierRules.text(args, "title", 240, true))
                        }
                        plan
                            .put(
                                "interval_value",
                                RevierRules.integer(args, "interval_value", 1, 3650, -1),
                            )
                            .put("interval_unit", RevierRules.text(args, "interval_unit", 12, true))
                        EquipmentMaintenanceRules.recalculate(plan)
                    } else {
                        throw RevierRules.error("INVALID_INPUT", "Unbekannte Wartungsplanänderung.")
                    }
                }
            }
            plan.put("last_change_reason", reason)
        }
        plan.put("updated_at", stamp())
        row.put("format_version", Math.max(2, row.optInt("format_version", 1)))
            .put("maintenance_plans", plans)
        EquipmentMaintenanceRules.plans(row)
        var saved: JSONObject =
            save(
                "equipment",
                row,
                old,
                request,
                (if (reason.isEmpty()) "Eigener Wartungsplan angelegt" else reason),
            )
        return JSONObject()
            .put("record", saved)
            .put("equipment", saved)
            .put("plan", RevierRules.copy(plan))
            .put("equipment_id", row.getString("id"))
    }

    @Throws(Exception::class)
    private fun equipmentMaintenanceDone(args: JSONObject, request: String): JSONObject {
        val old: JSONObject = maintenanceDevice(args)
        val row: JSONObject = RevierRules.copy(old)
        val plans: JSONArray = EquipmentMaintenanceRules.plans(row)
        var plan: JSONObject =
            EquipmentMaintenanceRules.find(plans, RevierRules.id(args, "plan_id", true))
        val before: JSONObject = RevierRules.copy(plan)
        val performed: String = RevierRules.date(args, "performed_on", true)
        val reason: String = RevierRules.text(args, "reason", 2000, false)
        val notes: String = RevierRules.text(args, "notes", 4000, false)
        if (performed.compareTo(today().toString()) > 0) {
            throw RevierRules.error(
                "INVALID_INPUT",
                "Eine erledigte Wartung darf nicht in der Zukunft liegen.",
            )
        }
        var shorter: Boolean =
            (args.has("shorter_interval_value") || args.has("shorter_interval_unit"))
        val newest: Boolean = (performed.compareTo(plan.getString("anchor_on")) >= 0)
        if (shorter && !newest) {
            throw RevierRules.error(
                "INVALID_INPUT",
                "Ein älterer Nachtrag verändert das aktuelle Intervall nicht. Plan separat prüfen.",
            )
        }
        val eventId: String = RevierRules.newId("equipment_maintenance", request)
        if (newest) {
            plan
                .put("anchor_on", performed)
                .put("last_completed_on", performed)
                .put("last_event_id", eventId)
                .remove("one_off_due_on")
            if (shorter) {
                var interval: JSONObject =
                    JSONObject()
                        .put(
                            "interval_value",
                            RevierRules.integer(args, "shorter_interval_value", 1, 3650, -1),
                        )
                        .put(
                            "interval_unit",
                            RevierRules.text(args, "shorter_interval_unit", 12, true),
                        )
                EquipmentMaintenanceRules.shorten(plan, interval, reason)
                plan.put("last_change_reason", reason)
            }
            EquipmentMaintenanceRules.recalculate(plan)
            plan.put("updated_at", stamp())
        }
        var event: JSONObject =
            base("equipment_maintenance", eventId, null)
                .put("format_version", 2)
                .put("equipment_id", row.getString("id"))
                .put("plan_id", plan.getString("id"))
                .put("title", plan.getString("title"))
                .put("performed_on", performed)
                .put("date", performed)
                .put("reason", reason)
                .put("notes", notes)
                .put("schedule_applied", newest)
                .put("plan_before", before)
                .put("plan_after", RevierRules.copy(plan))
        var savedEvent: JSONObject =
            save(
                "equipment_maintenance",
                event,
                null,
                request,
                (if (reason.isEmpty()) "Wartung ausdrücklich als erledigt erfasst" else reason),
            )
        row.put("maintenance_plans", plans)
        EquipmentMaintenanceRules.plans(row)
        var saved: JSONObject =
            save(
                "equipment",
                row,
                old,
                request,
                ("Wartung erfasst" + (if (reason.isEmpty()) "" else (": " + reason))),
            )
        return JSONObject()
            .put("record", saved)
            .put("equipment", saved)
            .put("maintenance", savedEvent)
            .put("equipment_id", row.getString("id"))
            .put("event_id", eventId)
    }

    @Throws(Exception::class)
    private fun equipmentMaintenanceHistory(args: JSONObject): JSONObject {
        val device: JSONObject = load("equipment", RevierRules.id(args, "equipment_id", true))
        EquipmentMaintenanceRules.plans(device)
        val events: MutableList<JSONObject> = ArrayList()
        var all: JSONArray = records("equipment_maintenance")
        run {
            var i: Int = 0
            while (i < all.length()) {
                if (
                    (device.getString("id").equals(all.getJSONObject(i).optString("equipment_id")))
                ) {
                    events.add(all.getJSONObject(i))
                }
                i++
            }
        }
        events.sortWith(
            Comparator.comparing(lambda4@{ e: JSONObject -> e.optString("performed_on") })
                .reversed()
                .thenComparing(
                    lambda5@{ e -> e.optString("created_at") },
                    Comparator.reverseOrder(),
                )
                .thenComparing(lambda6@{ e -> e.optString("id") })
        )
        var offset: Int = RevierRules.integer(args, "offset", 0, 1000000, 0)
        val limit: Int = RevierRules.integer(args, "limit", 1, 100, 20)
        val page: JSONArray = JSONArray()
        run {
            var i: Int = offset
            while ((i < events.size) && (page.length() < limit)) {
                page.put(RevierRules.view(events.get(i)))
                i++
            }
        }
        var record: JSONObject = RevierRules.view(device)
        return JSONObject()
            .put("record", record)
            .put("equipment", record)
            .put("plans", EquipmentMaintenanceRules.plans(device))
            .put("items", page)
            .put("maintenance", page)
            .put("total", events.size)
            .put("offset", offset)
            .put("limit", limit)
            .put("next_due_on", EquipmentMaintenanceRules.nextDue(device))
            .put("local_only", true)
    }

    @Throws(Exception::class)
    private fun search(args: JSONObject, state: JSONObject): JSONObject {
        val local = JSONObject()
        for (type in listOf("property", "inquiry", "visit", "material", "equipment")) local.put(
            type,
            records(type),
        )
        return LocalSearchRules.search(
            args,
            state,
            local,
            JSONObject(repository.get(SettlementRules.KEY, "{}")),
            AccountingRules.ids(JSONArray(repository.get(SettlementRules.LCR_KEY, "[]"))),
            today(),
            AccountingRules.ids(JSONArray(repository.get("personal_paid", "[]"))),
        )
    }

    @Throws(Exception::class)
    private fun overview(args: JSONObject, state: JSONObject): JSONObject {
        var day: String = args.optString("date", today().toString())
        RevierRules.date(day)
        val visits = records("visit")
        val appointments = state.optJSONArray("appointments") ?: JSONArray()
        val planned =
            JSONArray(
                PlanningBoardRules.tourEntries(appointments, visits, day).map {
                    JSONObject(it.toString()).put("id", it.getString("source_id"))
                }
            )
        val aliases = PlanningBoardRules.aliases(appointments, visits)
        val due = JSONArray()
        for (source in SettlementRules.rows(visits)) if (
            source.getString("id") !in aliases &&
                PlanningBoardRules.independentVisit(source) &&
                RevierRules.activeVisit(source) &&
                source.getString("date") <= day
        )
            due.put(RevierRules.view(source))
        val inquiries: JSONArray = records("inquiry")
        var open: Int = 0
        run {
            var i: Int = 0
            while (i < inquiries.length()) {
                if (
                    (!Arrays.asList("accepted", "declined", "closed")
                        .contains(inquiries.getJSONObject(i).optString("status")))
                ) {
                    open++
                }
                i++
            }
        }
        val maintenance = JSONArray()
        var maintenanceUnclear = 0
        val equipment = records("equipment")
        for (i in 0 until equipment.length()) {
            val device = equipment.getJSONObject(i)
            if (device.optBoolean("archived") || !device.optBoolean("active", true)) continue
            try {
                val next =
                    EquipmentMaintenanceRules.nextDue(device).ifEmpty {
                        device.optString("maintenance_due")
                    }
                if (next.isNotEmpty() && next <= day)
                    maintenance.put(
                        JSONObject()
                            .put("id", device.getString("id"))
                            .put("name", device.optString("name"))
                            .put("next_due_on", next)
                    )
            } catch (_: Exception) {
                maintenanceUnclear++
            }
        }
        var customerCount: Int =
            (if (state.optJSONArray("customers") == null) 0
            else state.getJSONArray("customers").length())
        return JSONObject()
            .put("date", day)
            .put("timezone", "Europe/Vienna")
            .put("visits", planned)
            .put("planned_visits", planned)
            .put("due_visits", due)
            .put("tour", tour(day))
            .put("properties_count", records("property").length())
            .put("customers_count", customerCount)
            .put("open_inquiries", open)
            .put("work_session", repository.currentSession())
            .put("external_calendar_status", "unknown")
            .put("maintenance_due", maintenance)
            .put("maintenance_unclear", maintenanceUnclear)
            .put("local_only", true)
    }

    @Throws(Exception::class)
    fun detachOptionalParent(entity: String, id: String, request: String) {
        if (entity == "worklog") {
            for (row in SettlementRules.rows(records("material_movement"))) {
                if (row.optString("worklog_id") != id) continue
                deleteEntity(
                    JSONObject()
                        .put("entity", "material_movement")
                        .put("entity_id", row.getString("id"))
                        .put("expected_record_hash", RevierRules.hash(row))
                        .put("confirmed", true),
                    repository.effective(),
                    request,
                    true,
                )
            }
            return
        }
        if (entity != "order" && entity != "appointment") return
        for (type in
            (if (entity == "order") arrayOf("visit", "inquiry", "material_movement")
            else arrayOf("visit", "inquiry"))) {
            val field = if (entity == "order") "order_id" else "inspection_appointment_id"
            val rows = records(type)
            for (i in 0 until rows.length()) {
                val old = rows.getJSONObject(i)
                val row = RevierRules.copy(old)
                var changed = false
                if (row.optString(field) == id) {
                    row.put(field, "")
                    changed = true
                }
                if (entity == "appointment") {
                    val history = row.optJSONArray("inspection_visits") ?: JSONArray()
                    for (j in 0 until history.length()) {
                        val inspection = history.getJSONObject(j)
                        if (inspection.optString("appointment_id") == id) {
                            inspection.put("appointment_id", "")
                            changed = true
                        }
                    }
                }
                if (!changed) continue
                save(
                    type,
                    row,
                    old,
                    request,
                    "Optionale Zuordnung nach Entfernen des Auftrags/Termins gelöst; Kundenbezug erhalten.",
                )
            }
        }
    }

    @Throws(Exception::class)
    fun protectParent(entity: String, id: String) {
        if (entity.equals("offer")) {
            val rows: JSONArray = repository.rawEntries(OfferDecisionRules.PREFIX)
            run {
                var i: Int = 0
                while (i < rows.length()) {
                    val entry: JSONObject = rows.getJSONObject(i)
                    val key: String = entry.getString("key")
                    if (
                        (!key.startsWith("" + OfferDecisionRules.PREFIX + "current:") &&
                            !key.startsWith("" + OfferDecisionRules.PREFIX + "revision:"))
                    ) {
                        i++
                        continue
                    }
                    var value: JSONObject = JSONObject(entry.getString("value"))
                    if (
                        (id.equals(value.optString("offer_id")) ||
                            id.equals(value.optString("parent_offer_id")))
                    ) {
                        throw RevierRules.error(
                            "REVIER_REFERENCE_PROTECTED",
                            "Angebot gehört zu einer unveränderlichen Entscheidung oder Versionshistorie. Nachweise erhalten; keine verwaiste Angebotsfassung erzeugen.",
                        )
                    }
                    i++
                }
            }
        }
        if (entity.equals("customer")) {
            for (type in arrayOf("property", "inquiry", "visit")) {
                val records: JSONArray = records(type)
                run {
                    var i: Int = 0
                    while (i < records.length()) {
                        if (id.equals(records.getJSONObject(i).optString("customer_id"))) {
                            throw RevierRules.error(
                                "REVIER_REFERENCE_PROTECTED",
                                "Kunde ist mit Objekt-/Anfrage-/Besuchshistorie verknüpft. Bezogene Einträge zuerst bewusst prüfen; keine verwaisten Bezüge erzeugen.",
                            )
                        }
                        i++
                    }
                }
            }
        }
        if (entity == "offer") {
            var field: String =
                (if (entity.equals("appointment")) "inspection_appointment_id"
                else ("" + entity + "_id"))
            val rows: JSONArray = records("inquiry")
            run {
                var i: Int = 0
                while (i < rows.length()) {
                    if (id.equals(rows.getJSONObject(i).optString(field))) {
                        throw RevierRules.error(
                            "REVIER_REFERENCE_PROTECTED",
                            "Eintrag gehört zum dokumentierten Anfrageverlauf. Diese Verknüpfung zuerst ausdrücklich prüfen.",
                        )
                    }
                    i++
                }
            }
        }
        if (entity.equals("photo")) {
            val rows: JSONArray = records("inquiry")
            run {
                var i: Int = 0
                while (i < rows.length()) {
                    val photos: JSONArray? = rows.getJSONObject(i).optJSONArray("photo_ids")
                    run {
                        var j: Int = 0
                        while ((photos != null) && (j < photos.length())) {
                            if (id.equals(photos.getString(j))) {
                                throw RevierRules.error(
                                    "REVIER_REFERENCE_PROTECTED",
                                    "Foto gehört zu einer Besichtigungsdokumentation. Diese Zuordnung zuerst ausdrücklich prüfen; Original bleibt erhalten.",
                                )
                            }
                            j++
                        }
                    }
                    i++
                }
            }
        }
    }

    @Throws(Exception::class)
    fun protectPhotoAssignment(photoId: String, customerId: String) {
        val rows: JSONArray = records("inquiry")
        run {
            var i: Int = 0
            while (i < rows.length()) {
                val inquiry: JSONObject = rows.getJSONObject(i)
                if (customerId.equals(inquiry.optString("customer_id"))) {
                    i++
                    continue
                }
                val photos: JSONArray? = inquiry.optJSONArray("photo_ids")
                run {
                    var j: Int = 0
                    while ((photos != null) && (j < photos.length())) {
                        if (photoId.equals(photos.getString(j))) {
                            throw RevierRules.error(
                                "REVIER_REFERENCE_PROTECTED",
                                "Foto ist mit einer Besichtigung eines anderen Kunden verknüpft. Zuordnung zuerst ausdrücklich klären.",
                            )
                        }
                        j++
                    }
                }
                i++
            }
        }
    }

    @Throws(Exception::class)
    fun equipmentPhoto(photoId: String): JSONObject? {
        val rows: JSONArray = records("equipment")
        run {
            var i: Int = 0
            while (i < rows.length()) {
                val row: JSONObject = rows.getJSONObject(i)
                val photo: JSONObject? = row.optJSONObject("profile_photo")
                if ((photo != null) && photoId.equals(photo.optString("id"))) {
                    EquipmentProfilePhotoRules.validate(photo, row.getString("id"))
                    return RevierRules.copy(photo)
                }
                val accessories = row.optJSONArray("accessories") ?: JSONArray()
                for (index in 0 until accessories.length()) {
                    val accessory = accessories.getJSONObject(index)
                    val photo = accessory.optJSONObject("photo") ?: continue
                    if (photoId == photo.optString("id")) {
                        if (accessory.optString("equipment_id") != row.getString("id"))
                            throw RevierRules.error(
                                "EQUIPMENT_PHOTO_INVALID",
                                "Zubehör gehört nicht zu dieser Maschine.",
                            )
                        EquipmentProfilePhotoRules.validate(photo, row.getString("id"))
                        return RevierRules.copy(photo)
                    }
                }
                i++
            }
        }
        return null
    }

    @Throws(Exception::class)
    fun validateRelationships(): JSONObject {
        val state: JSONObject = repository.effective()
        val counts: JSONObject = JSONObject()
        val hashes: JSONObject = JSONObject()
        val groups: MutableMap<String, JSONArray> = LinkedHashMap()
        for (type in
            arrayOf(
                "property",
                "inquiry",
                "care_plan",
                "visit",
                "tour",
                "material",
                "material_movement",
                "equipment",
                "equipment_usage",
            )) {
            val values: JSONArray? = records(type)
            groups.put(type, (values)!!)
            counts.put(type, values.length())
            val ids: TreeSet<String> = TreeSet()
            run {
                var i: Int = 0
                while (i < values.length()) {
                    val row: JSONObject = values.getJSONObject(i)
                    if (
                        (!type.equals(row.optString("kind")) ||
                            !ids.add(RevierRules.id(row, "id", true)))
                    ) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Datensatztyp oder eindeutige Kennung fehlt.",
                        )
                    }
                    i++
                }
            }
            hashes.put(type, PixelMcpWriteTools.hash(JSONArray(ids)))
        }
        run {
            var i: Int = 0
            while (i < groups.get("property")!!.length()) {
                val property: JSONObject = groups.get("property")!!.getJSONObject(i)
                customer(state, RevierRules.id(property, "customer_id", true), false)
                i++
            }
        }
        run {
            var i: Int = 0
            while (i < groups.get("inquiry")!!.length()) {
                val inquiry: JSONObject = groups.get("inquiry")!!.getJSONObject(i)
                val customer: String = RevierRules.id(inquiry, "customer_id", true)
                customer(state, customer, false)
                val property: String = RevierRules.id(inquiry, "property_id", false)
                if (
                    (!property.isEmpty() &&
                        !customer.equals(load("property", property).getString("customer_id")))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Anfrage und Objekt passen nicht zusammen.",
                    )
                }
                for (reference in
                    arrayOf(
                        arrayOf("offer_id", "offers"),
                        arrayOf("order_id", "orders"),
                        arrayOf("inspection_appointment_id", "appointments"),
                    )) {
                    if (
                        (!inquiry.optString(reference[0]).isEmpty() &&
                            !customer.equals(
                                canonical(state, reference[1], inquiry.getString(reference[0]))
                                    .optString("customer_id")
                            ))
                    ) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Anfrageverlauf hat einen widersprüchlichen Bezug.",
                        )
                    }
                }
                val photos: JSONArray? = inquiry.optJSONArray("photo_ids")
                run {
                    var j: Int = 0
                    while ((photos != null) && (j < photos.length())) {
                        if (
                            (!customer.equals(
                                canonical(state, "photos", photos.getString(j))
                                    .optString("customer_id")
                            ))
                        ) {
                            throw RevierRules.error(
                                "REVIER_RELATION_INVALID",
                                "Besichtigungsfoto gehört zu einem anderen Kunden.",
                            )
                        }
                        j++
                    }
                }
                i++
            }
        }
        run {
            var i: Int = 0
            while (i < groups.get("care_plan")!!.length()) {
                val plan: JSONObject = groups.get("care_plan")!!.getJSONObject(i)
                if (
                    (!plan
                        .getString("customer_id")
                        .equals(
                            load("property", plan.getString("property_id")).getString("customer_id")
                        ))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Pflegeplan und Objekt passen nicht zusammen.",
                    )
                }
                val rules: JSONArray = ruleVersions(plan.getString("id"))
                val versions: MutableSet<Int> = HashSet()
                var current: Boolean = false
                run {
                    var j: Int = 0
                    while (j < rules.length()) {
                        val rule: JSONObject = rules.getJSONObject(j)
                        if (
                            (!plan.getString("id").equals(rule.getString("plan_id")) ||
                                !versions.add(rule.getInt("version")))
                        ) {
                            throw RevierRules.error(
                                "REVIER_RELATION_INVALID",
                                "Pflegeversion fehlt oder ist doppelt.",
                            )
                        }
                        RevierRules.date(rule.getString("effective_from"))
                        if (rule.getInt("version") == plan.getInt("rule_version")) {
                            current = true
                        }
                        val snapshot: JSONObject = rule.getJSONObject("rule")
                        RevierRules.date(snapshot.getString("anchor_date"))
                        if (
                            ((snapshot.getInt("interval_days") < 1) ||
                                (snapshot.getInt("planned_minutes") < 1))
                        ) {
                            throw RevierRules.error(
                                "REVIER_RELATION_INVALID",
                                "Ungültige Pflegeversion.",
                            )
                        }
                        checkResourceReferences(snapshot)
                        j++
                    }
                }
                if (!current) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Aktuelle Pflegeversion fehlt.",
                    )
                }
                i++
            }
        }
        val occurrences: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while (i < groups.get("visit")!!.length()) {
                val visit: JSONObject = groups.get("visit")!!.getJSONObject(i)
                if (
                    (!visit
                        .getString("customer_id")
                        .equals(
                            load("property", visit.getString("property_id"))
                                .getString("customer_id")
                        ))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Besuch und Objekt passen nicht zusammen.",
                    )
                }
                RevierRules.date(visit.getString("date"))
                if (
                    (!visit.optString("order_id").isEmpty() &&
                        !visit
                            .getString("customer_id")
                            .equals(
                                canonical(state, "orders", visit.getString("order_id"))
                                    .optString("customer_id")
                            ))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Besuch und Auftrag passen nicht zusammen.",
                    )
                }
                if (!visit.optString("care_plan_id").isEmpty()) {
                    val plan: JSONObject = load("care_plan", visit.getString("care_plan_id"))
                    var occurrence: String =
                        (("" + plan.getString("id") + ":") + visit.getString("occurrence_key"))
                    RevierRules.date(visit.getString("occurrence_key"))
                    if (
                        ((!occurrences.add(occurrence) ||
                            !visit
                                .getString("id")
                                .equals(
                                    repository.get(
                                        (("" + RevierRules.ROOT + "occurrence:") + occurrence),
                                        "",
                                    )
                                )) ||
                            !plan.getString("property_id").equals(visit.getString("property_id")))
                    ) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Pflegevorkommen ist doppelt oder falsch verknüpft.",
                        )
                    }
                }
                if (
                    (!visit.optString("parent_visit_id").isEmpty() &&
                        !visit
                            .getString("property_id")
                            .equals(
                                load("visit", visit.getString("parent_visit_id"))
                                    .getString("property_id")
                            ))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Restbesuch gehört zu einem anderen Objekt.",
                    )
                }
                checkResourceReferences(visit)
                i++
            }
        }
        for (tour in SettlementRules.rows(groups.get("tour"))) {
            RevierRules.date(tour.getString("date"))
            val local = mutableSetOf<String>()
            for (j in 0 until tour.getJSONArray("visit_ids").length()) {
                val id = tour.getJSONArray("visit_ids").getString(j)
                if (!local.add(id))
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Ein Termin steht mehrfach in der Tour.",
                    )
                // Ordering references may outlive a move/deletion. The day view projects current
                // originals.
                if (id.startsWith("appointments:")) {
                    val source = state.optJSONArray("appointments") ?: JSONArray()
                    val exists =
                        (0 until source.length()).any {
                            source.getJSONObject(it).optString("id") ==
                                id.removePrefix("appointments:")
                        }
                    if (!exists) continue
                } else if (find("visit", id) == null) continue
            }
        }
        for (movement in SettlementRules.rows(groups.get("material_movement"))) {
            load("material", movement.getString("material_id"))
            if (movement.optString("worklog_id").isNotBlank()) {
                canonical(state, "worklogs", movement.getString("worklog_id"))
            }
            validateStoredContext(movement, state)
        }
        for (collection in arrayOf("worklogs", "photos")) {
            val values: JSONArray? = state.optJSONArray(collection)
            run {
                var i: Int = 0
                while ((values != null) && (i < values.length())) {
                    var value: JSONObject = values.getJSONObject(i)
                    validateStoredContext(value, state)
                    if (
                        (collection.equals("photos") &&
                            (!value.optString("property_id").isEmpty() ||
                                !value.optString("visit_id").isEmpty()))
                    ) {
                        if (value.optString("role").equals("profile")) {
                            throw RevierRules.error(
                                "REVIER_RELATION_INVALID",
                                "Profilbild und Einsatzdokumentation benötigen getrennte Zuordnungen.",
                            )
                        }
                        if (!value.optString("worklog_id").isEmpty()) {
                            var work: JSONObject =
                                canonical(state, "worklogs", value.getString("worklog_id"))
                            for (field in arrayOf("property_id", "visit_id")) {
                                if (
                                    ((!value.optString(field).isEmpty() &&
                                        !work.optString(field).isEmpty()) &&
                                        !value.getString(field).equals(work.getString(field)))
                                ) {
                                    throw RevierRules.error(
                                        "REVIER_RELATION_INVALID",
                                        "Foto und Arbeitsaufzeichnung gehören zu verschiedenen Einsätzen.",
                                    )
                                }
                            }
                        }
                    }
                    i++
                }
            }
        }
        val sessions: JSONArray = repository.sessionContexts()
        run {
            var i: Int = 0
            while (i < sessions.length()) {
                val session: JSONObject = sessions.getJSONObject(i)
                validateStoredContext(session, state)
                if (
                    (!session.optString("property_id").isEmpty() ||
                        !session.optString("visit_id").isEmpty())
                ) {
                    if (
                        ((session.has("_start_customer_id") &&
                            !session
                                .optString("customer_id")
                                .equals(session.optString("_start_customer_id"))) ||
                            (session.has("_start_order_id") &&
                                !session
                                    .optString("order_id")
                                    .equals(session.optString("_start_order_id"))))
                    ) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Sitzung und ursprünglicher Einsatzbezug widersprechen sich.",
                        )
                    }
                }
                i++
            }
        }
        val equipment: JSONArray = (groups.get("equipment"))!!
        val maintenance: JSONArray = records("equipment_maintenance")
        counts.put("equipment_maintenance", maintenance.length())
        val events: MutableMap<String, JSONObject> = HashMap()
        val latest: MutableMap<String, String> = HashMap()
        run {
            var i: Int = 0
            while (i < maintenance.length()) {
                val event: JSONObject? = maintenance.getJSONObject(i)
                val eventId: String = RevierRules.id((event)!!, "id", true)
                val equipmentId: String = RevierRules.id((event)!!, "equipment_id", true)
                val planId: String = RevierRules.id((event)!!, "plan_id", true)
                if (
                    (!event.optString("kind").equals("equipment_maintenance") ||
                        (events.put(eventId, event) != null))
                ) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Ungültiger Wartungsbeleg.")
                }
                val device: JSONObject = load("equipment", equipmentId)
                EquipmentMaintenanceRules.find(EquipmentMaintenanceRules.plans(device), planId)
                val performed: String = RevierRules.date(event, "performed_on", true)
                if (!performed.equals(event.optString("date"))) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Wartungsdatum und Historie widersprechen sich.",
                    )
                }
                RevierRules.text(event, "title", 240, true)
                val relation: String = (("" + equipmentId + ":") + planId)
                if (!(event.opt("schedule_applied") is Boolean)) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Planwirkung des Wartungsbelegs fehlt.",
                    )
                }
                val applied: Boolean = event.getBoolean("schedule_applied")
                if (
                    (applied &&
                        (!latest.containsKey(relation) ||
                            (performed.compareTo((latest.get(relation))!!) > 0)))
                ) {
                    latest.put(relation, performed)
                }
                for (snapshot in arrayOf("plan_before", "plan_after")) {
                    val historical: JSONObject = event.getJSONObject(snapshot)
                    if (!planId.equals(historical.optString("id"))) {
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Wartungsbeleg verweist auf einen anderen Plan.",
                        )
                    }
                    EquipmentMaintenanceRules.plans(
                        JSONObject().put("maintenance_plans", JSONArray().put(historical))
                    )
                }
                val before: JSONObject = event.getJSONObject("plan_before")
                val after: JSONObject = event.getJSONObject("plan_after")
                if (
                    (((applied != (performed.compareTo(before.getString("anchor_on")) >= 0)) ||
                        (applied &&
                            (!eventId.equals(after.optString("last_event_id")) ||
                                !performed.equals(after.optString("last_completed_on"))))) ||
                        (!applied && !RevierRules.hash(before).equals(RevierRules.hash(after))))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Wartungsbeleg und Planwirkung widersprechen sich.",
                    )
                }
                i++
            }
        }
        hashes.put(
            "equipment_maintenance",
            PixelMcpWriteTools.hash(JSONArray(TreeSet(events.keys))),
        )
        val equipmentPhotos: MutableMap<String, String> = TreeMap()
        run {
            var i: Int = 0
            while (i < equipment.length()) {
                val device: JSONObject = equipment.getJSONObject(i)
                if (device.has("profile_photo")) {
                    val photo: JSONObject = device.getJSONObject("profile_photo")
                    EquipmentProfilePhotoRules.validate(photo, device.getString("id"))
                    if (
                        (equipmentPhotos.put(
                            photo.getString("id"),
                            (("" + device.getString("id") + ":") +
                                photo.getString("content_sha256")),
                        ) != null)
                    ) {
                        throw RevierRules.error(
                            "EQUIPMENT_PHOTO_INVALID",
                            "Ein Gerätefoto darf nicht gleichzeitig zu mehreren Geräten gehören.",
                        )
                    }
                    repository.verifyEquipmentProfilePhoto(photo)
                }
                val accessories = device.optJSONArray("accessories") ?: JSONArray()
                val accessoryIds = HashSet<String>()
                val chain = device.optString("current_chain_id")
                var currentFound = chain.isEmpty()
                for (index in 0 until accessories.length()) {
                    val item = accessories.getJSONObject(index)
                    if (
                        !accessoryIds.add(item.getString("id")) ||
                            item.optString("equipment_id") != device.getString("id")
                    )
                        throw RevierRules.error(
                            "REVIER_RELATION_INVALID",
                            "Zubehörzuordnung ist nicht eindeutig.",
                        )
                    if (item.getString("id") == chain && item.optString("type") == "chain")
                        currentFound = true
                    item.optJSONObject("photo")?.let { photo ->
                        EquipmentProfilePhotoRules.validate(photo, device.getString("id"))
                        if (
                            equipmentPhotos.put(
                                photo.getString("id"),
                                device.getString("id") +
                                    ":" +
                                    item.getString("id") +
                                    ":" +
                                    photo.getString("content_sha256"),
                            ) != null
                        )
                            throw RevierRules.error(
                                "EQUIPMENT_PHOTO_INVALID",
                                "Fotozuordnung ist nicht eindeutig.",
                            )
                        repository.verifyEquipmentProfilePhoto(photo)
                    }
                }
                if (!currentFound)
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Aktuelle Kette gehört nicht zur Maschine.",
                    )
                val plans: JSONArray = EquipmentMaintenanceRules.plans(device)
                run {
                    var j: Int = 0
                    while (j < plans.length()) {
                        val plan: JSONObject = plans.getJSONObject(j)
                        val done: String = plan.optString("last_completed_on")
                        var relation: String =
                            (("" + device.getString("id") + ":") + plan.getString("id"))
                        if (!done.equals(latest.getOrDefault(relation, ""))) {
                            throw RevierRules.error(
                                "REVIER_RELATION_INVALID",
                                "Letzte Wartung und Verlauf sind nicht vollständig.",
                            )
                        }
                        if (!done.isEmpty()) {
                            val event: JSONObject? = events.get(plan.optString("last_event_id"))
                            if (
                                ((((event == null) ||
                                    !device
                                        .getString("id")
                                        .equals(event.optString("equipment_id"))) ||
                                    !plan.getString("id").equals(event.optString("plan_id"))) ||
                                    !done.equals(event.optString("performed_on")))
                            ) {
                                throw RevierRules.error(
                                    "REVIER_RELATION_INVALID",
                                    "Letzter Wartungsbeleg fehlt oder gehört zu einem anderen Gerät.",
                                )
                            }
                        }
                        j++
                    }
                }
                i++
            }
        }
        if (!equipmentPhotos.isEmpty()) {
            counts.put("equipment_profile_photos", equipmentPhotos.size)
            hashes.put(
                "equipment_profile_photos",
                PixelMcpWriteTools.hash(JSONObject(equipmentPhotos)),
            )
        }
        val decisions: JSONObject = OfferDecisionValidation.validate(repository, state)
        return JSONObject()
            .put("format_version", 1)
            .put("status", "PASS")
            .put("counts", counts)
            .put("id_set_hashes", hashes)
            .put("offer_decisions", decisions)
            .put("business_data_changed", false)
    }

    @Throws(Exception::class)
    private fun checkResourceReferences(row: JSONObject) {
        val materials: JSONArray? = row.optJSONArray("material_needs")
        run {
            var i: Int = 0
            while ((materials != null) && (i < materials.length())) {
                load("material", materials.getJSONObject(i).getString("material_id"))
                i++
            }
        }
        val equipment: JSONArray? = row.optJSONArray("equipment_ids")
        run {
            var i: Int = 0
            while ((equipment != null) && (i < equipment.length())) {
                load("equipment", equipment.getString(i))
                i++
            }
        }
    }

    @Throws(Exception::class)
    private fun validateStoredContext(row: JSONObject, state: JSONObject) {
        val property: String = RevierRules.id(row, "property_id", false)
        if (!property.isEmpty()) {
            var `object`: JSONObject = load("property", property)
            if (!row.optString("customer_id").equals(`object`.getString("customer_id"))) {
                throw RevierRules.error(
                    "REVIER_RELATION_INVALID",
                    "Einsatzbezug hat einen falschen Kunden.",
                )
            }
        }
        val visit: String = RevierRules.id(row, "visit_id", false)
        if (!visit.isEmpty()) {
            val source: JSONObject? = load("visit", visit)
            if (
                ((!property.equals(source!!.getString("property_id")) ||
                    !row.optString("customer_id").equals(source!!.getString("customer_id"))) ||
                    ((!row.optString("order_id").isEmpty() &&
                        !source!!.optString("order_id").isEmpty()) &&
                        !row.optString("order_id").equals(source!!.optString("order_id"))))
            ) {
                throw RevierRules.error(
                    "REVIER_RELATION_INVALID",
                    "Einsatzbezug hat ein falsches Objekt, einen anderen Kunden oder Auftrag.",
                )
            }
        }
        val session: String = RevierRules.id(row, "session_id", false)
        if (!session.isEmpty()) {
            val source: JSONObject? = repository.session(session)
            if (
                ((source == null) ||
                    (!row.optString("customer_id").isEmpty() &&
                        !row.getString("customer_id").equals(source.optString("customer_id"))))
            ) {
                throw RevierRules.error(
                    "REVIER_RELATION_INVALID",
                    "Sitzungsbezug fehlt oder widerspricht dem Kunden.",
                )
            }
            for (field in arrayOf("property_id", "visit_id")) {
                if (
                    ((!row.optString(field).isEmpty() && !source.optString(field).isEmpty()) &&
                        !row.getString(field).equals(source.getString(field)))
                ) {
                    throw RevierRules.error(
                        "REVIER_RELATION_INVALID",
                        "Sitzung und dokumentierter Einsatz widersprechen sich.",
                    )
                }
            }
        }
    }

    companion object {
        private val BUSINESS_ZONE: ZoneId = ZoneId.of("Europe/Vienna")

        private fun calendarDay(value: String): String {
            if (value.length <= 10) {
                return value
            }
            try {
                return Instant.parse(value).atZone(BUSINESS_ZONE).toLocalDate().toString()
            } catch (unverifiedLegacy: DateTimeException) {
                return value.substring(0, 10)
            }
        }

        private fun key(type: String, id: String): String {
            return (("" + (("" + RevierRules.ROOT + "entity:") + type) + ":") + id)
        }

        @Throws(Exception::class)
        private fun last(values: JSONArray, maximum: Int): JSONArray {
            val rows: MutableList<JSONObject> = ArrayList()
            run {
                var i: Int = 0
                while (i < values.length()) {
                    rows.add(values.getJSONObject(i))
                    i++
                }
            }
            rows.sortWith(
                Comparator.comparing(lambda10@{ r: JSONObject -> r.optString("at") }).reversed()
            )
            var out: JSONArray = JSONArray()
            run {
                var i: Int = 0
                while ((i < rows.size) && (i < maximum)) {
                    out.put(rows.get(i))
                    i++
                }
            }
            return out
        }

        private val DELETABLE: Array<String> =
            arrayOf("property", "inquiry", "care_plan", "visit", "material", "equipment")

        @Throws(Exception::class)
        private fun references(value: Any, id: String): Boolean {
            if (value is JSONObject) {
                var `object`: JSONObject = (value as JSONObject)
                run {
                    val keys: MutableIterator<String> = `object`.keys()
                    while (keys.hasNext()) {
                        if (references(`object`.get(keys.next()), id)) {
                            return true
                        }
                    }
                }
            } else {
                if (value is JSONArray) {
                    val array: JSONArray = (value as JSONArray)
                    run {
                        var i: Int = 0
                        while (i < array.length()) {
                            if (references(array.get(i), id)) {
                                return true
                            }
                            i++
                        }
                    }
                } else {
                    if (value is String) {
                        return id.equals(value)
                    }
                }
            }
            return false
        }

        private val SEARCH_MARKS: java.util.regex.Pattern =
            java.util.regex.Pattern.compile("\\p{M}+")

        private fun normalize(value: String): String {
            return SEARCH_MARKS.matcher(
                    java.text.Normalizer.normalize(
                        value.lowercase(Locale.GERMAN).replace("ß", "ss"),
                        java.text.Normalizer.Form.NFD,
                    )
                )
                .replaceAll("")
        }
    }
}
