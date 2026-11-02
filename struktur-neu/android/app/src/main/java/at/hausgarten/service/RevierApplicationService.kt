package at.hausgarten.service

import org.json.*
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

/** Common offline application cases for objects, care, visits, tours and operating resources. */
class RevierApplicationService {
    private val repository: RevierRepository
    private val clock: Clock
    constructor(repository: RevierRepository) : this(repository, Clock.systemUTC()) {
    }
    constructor(repository: RevierRepository, clock: Clock) {
        this.repository = repository
        this.clock = clock
    }
    private fun stamp(): String {
        return clock.instant().toString()
    }
    private fun today(): LocalDate {
        return clock.instant().atZone(BUSINESS_ZONE).toLocalDate()
    }
    @Throws(Exception::class)
    private fun records(type: String): JSONArray {
        var stored: JSONArray = repository.list(("" + (("" + RevierRules.ROOT + "entity:") + type) + ":"))
        var rows: JSONArray = JSONArray()
        run {
            var i: Int = 0
            while ((i < stored.length())) {
                var row: JSONObject = stored.getJSONObject(i)
                RevierRules.version(row)
                if ((!row.optBoolean("deleted"))) {
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
        if ((value.isEmpty())) {
            throw RevierRules.error("REFERENCE_NOT_FOUND", "Der verknüpfte Eintrag wurde nicht gefunden.")
        }
        var row: JSONObject = JSONObject(value)
        RevierRules.version(row)
        if ((row.optBoolean("deleted"))) {
            throw RevierRules.error("ENTITY_DELETED", "Dieser Eintrag wurde endgültig gelöscht. Eine alte Aktion legt ihn nicht erneut an.")
        }
        if ((!id.equals(row.getString("id")))) {
            throw RevierRules.error("REVIER_RECORD_INVALID", "Eintrag und Kennung stimmen nicht überein.")
        }
        return row
    }
    @Throws(Exception::class)
    private fun find(type: String, id: String): JSONObject? {
        var value: String = repository.get(key(type, id), "")
        if ((value.isEmpty())) {
            return null
        }
        var row: JSONObject = JSONObject(value)
        RevierRules.version(row)
        if ((row.optBoolean("deleted"))) {
            return null
        }
        return row
    }
    @Throws(Exception::class)
    private fun canonical(state: JSONObject, type: String, id: String): JSONObject {
        var values: JSONArray? = state.optJSONArray(type)
        if (((values != null))) {
            run {
                var i: Int = 0
                while ((i < values!!.length())) {
                    var row: JSONObject = values!!.getJSONObject(i)
                    if ((id.equals(row.optString("id")))) {
                        return row
                    }
                    i++
                }
            }
        }
        throw RevierRules.error("REFERENCE_NOT_FOUND", "Der bestehende Kunden-/Auftragsbezug wurde nicht gefunden.")
    }
    @Throws(Exception::class)
    private fun customer(state: JSONObject, id: String, creating: Boolean): JSONObject {
        var customer: JSONObject = canonical(state, "customers", id)
        if (((creating && (customer.optInt("active", 1) == 0)))) {
            throw RevierRules.error("CUSTOMER_INACTIVE", "Für neue Vorgänge einen aktiven Kunden wählen.")
        }
        return customer
    }
    @Throws(Exception::class)
    private fun base(kind: String, id: String, old: JSONObject?): JSONObject {
        return (if ((old == null)) JSONObject().put("format_version", 1).put("kind", kind).put("id", id).put("created_at", stamp()) else RevierRules.copy(old))
    }
    @Throws(Exception::class)
    private fun save(type: String, row: JSONObject, old: JSONObject?, request: String, reason: String): JSONObject {
        row.remove("record_hash")
        if ((!row.has("created_at"))) {
            row.put("created_at", stamp())
        }
        row.put("updated_at", stamp())
        RevierRules.bounded(row)
        repository.put(key(type, row.getString("id")), row.toString())
        var audit: JSONObject = JSONObject().put("format_version", 1).put("entity_type", type).put("entity_id", row.getString("id")).put("request_id", request).put("at", stamp()).put("reason", reason).put("before", (if ((old == null)) JSONObject.NULL else old)).put("after", row)
        repository.put((("" + (("" + (("" + RevierRules.ROOT + "audit:") + type) + ":") + row.getString("id")) + ":") + PixelMcpWriteTools.hash(request)), audit.toString())
        return RevierRules.view(row)
    }
    @Throws(Exception::class)
    fun execute(name: String, args: JSONObject): JSONObject {
        var request: String = RevierRules.text(args, "request_id", 128, true)
        if ((((request.length < 8) || !request.matches(Regex("[A-Za-z0-9_.:-]+"))))) {
            throw RevierRules.error("INVALID_INPUT", "Eine stabile Anfragekennung ist erforderlich.")
        }
        var fingerprint: String = PixelMcpWriteTools.hash(JSONObject().put("operation", name).put("args", args))
        var receipt: String = (("" + RevierRules.ROOT + "request:") + PixelMcpWriteTools.hash(request))
        return repository.transaction(lambda0@ {
            var prior: String = repository.get(receipt, "")
            if ((!prior.isEmpty())) {
                var saved: JSONObject = JSONObject(prior)
                RevierRules.version(saved)
                if ((!fingerprint.equals(saved.getString("fingerprint")))) {
                    throw RevierRules.error("REPLAY_MISMATCH", "Diese Anfragekennung gehört zu anderen Angaben.")
                }
                if ((saved.getJSONObject("result").optBoolean("_result_content_removed"))) {
                    throw RevierRules.error("REQUEST_EXPIRED", "Dieser bereits verarbeitete Vorgang wurde endgültig gelöscht; keine erneute Anlage.")
                }
                return@lambda0 RevierRules.copy(saved.getJSONObject("result")).put("replayed", true)
            }
            var result: JSONObject = mutate(name, RevierRules.copy(args), repository.effective(), request).put("local_saved", true).put("local_state", "local_saved").put("cloud_confirmed", false).put("replayed", false)
            var saved: JSONObject = JSONObject().put("format_version", 1).put("fingerprint", fingerprint).put("at", stamp()).put("result", result)
            RevierRules.bounded(saved)
            repository.put(receipt, saved.toString())
            return@lambda0 result
 })
    }
    @Throws(Exception::class)
    fun query(name: String, args: JSONObject): JSONObject {
        for (field in arrayOf("from_date", "to_date")) {
            if ((!args.optString(field).isEmpty())) {
                RevierRules.date(args.getString(field))
            }
        }
        if ((((!args.optString("from_date").isEmpty() && !args.optString("to_date").isEmpty()) && (args.getString("to_date").compareTo(args.getString("from_date")) < 0)))) {
            throw RevierRules.error("INVALID_INPUT", "Zeitraum endet vor seinem Beginn.")
        }
        var state: JSONObject? = (if (Arrays.asList("get_property", "get_inquiry", "search_local", "get_revier_overview").contains(name)) repository.effective() else null)
        when ((name)) {
            "inspect_edit_action" -> {
                return inspectEditAction(args)
            }
            "list_revier_archive" -> {
                return archiveList(args)
            }
            "list_properties" -> {
                return listing("property", "properties", args)
            }
            "get_property" -> {
                return propertyDetail(RevierRules.id(args, "property_id", true), (state)!!)
            }
            "list_inquiries" -> {
                return listing("inquiry", "inquiries", args)
            }
            "get_inquiry" -> {
                run {
                    var result: JSONObject = detail("inquiry", RevierRules.id(args, "inquiry_id", true), "inquiry")
                    var inquiry: JSONObject = result.getJSONObject("record")
                    if ((!inquiry.optString("offer_id").isEmpty())) {
                        result.put("offer", canonical((state)!!, "offers", inquiry.getString("offer_id")))
                    }
                    if ((!inquiry.optString("order_id").isEmpty())) {
                        result.put("order", canonical((state)!!, "orders", inquiry.getString("order_id")))
                    }
                    return result
                }
            }
            "list_care_plans" -> {
                return listing("care_plan", "care_plans", args)
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
    private fun mutate(name: String, args: JSONObject, state: JSONObject, request: String): JSONObject {
        when ((name)) {
            "create_property", "update_property" -> {
                return property(name, args, state, request)
            }
            "create_inquiry", "update_inquiry" -> {
                return inquiry(name, args, state, request)
            }
            "set_revier_archived", "purge_revier_entity" -> {
                return archiveChange(name, args, state, request)
            }
            "accept_inquiry_direct" -> {
                return directOrder(args, state, request)
            }
            "plan_inquiry_inspection" -> {
                return inspection(args, state, request)
            }
            "create_care_plan", "update_care_plan" -> {
                return care(name, args, state, request)
            }
            "generate_care_visits" -> {
                return generate(args, state, request)
            }
            "plan_visit", "update_visit" -> {
                return visit(name, args, state, request)
            }
            "set_day_tour", "move_tour_stop" -> {
                return changeTour(name, args, request)
            }
            "save_material" -> {
                return material(args, request)
            }
            "record_material_movement" -> {
                return movement(args, state, request)
            }
            "save_equipment" -> {
                return equipment(args, request)
            }
            "record_equipment_usage" -> {
                return usage(args, state, request)
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
        var row: JSONObject = RevierRules.view(load(type, id))
        var result: JSONObject = JSONObject().put("record", row).put(alias, row).put("record_hash", row.getString("record_hash"))
        if ((type.equals("visit"))) {
            result.put("equipment_warnings", equipmentWarnings((if ((row.optJSONArray("equipment_ids") == null)) JSONArray() else row.getJSONArray("equipment_ids")))).put("external_calendar_status", "unknown")
        }
        if ((type.equals("inquiry"))) {
            var history: JSONArray = repository.list(("" + (("" + RevierRules.ROOT + "audit:inquiry:") + id) + ":"))
            result.put("history", last(history, 20)).put("history_total", history.length())
        }
        return result
    }
    @Throws(Exception::class)
    private fun matches(row: JSONObject, filters: JSONObject): Boolean {
        if (((!filters.optBoolean("include_archived", false) && ((!row.optBoolean("active", true) || row.optBoolean("archived")))))) {
            return false
        }
        for (field in arrayOf("customer_id", "property_id", "status", "district", "care_plan_id")) {
            if (((!filters.optString(field).isEmpty() && !filters.getString(field).equals(row.optString(field))))) {
                return false
            }
        }
        var date: String = row.optString("date", row.optString("received_at", row.optString("updated_at")))
        if (((!filters.optString("from_date").isEmpty() || !filters.optString("to_date").isEmpty()))) {
            date = calendarDay(date)
        }
        if (((!filters.optString("from_date").isEmpty() && ((date.isEmpty() || (date.compareTo(filters.getString("from_date")) < 0)))))) {
            return false
        }
        if (((!filters.optString("to_date").isEmpty() && ((date.isEmpty() || (date.compareTo(filters.getString("to_date")) > 0)))))) {
            return false
        }
        return (filters.optString("query").isEmpty() || normalize(row.toString()).contains(normalize(filters.getString("query"))))
    }
    @Throws(Exception::class)
    private fun listing(type: String, alias: String, filters: JSONObject): JSONObject {
        var offset: Int = RevierRules.integer(filters, "offset", 0, 1000000, 0)
        var limit: Int = RevierRules.integer(filters, "limit", 1, 100, 50)
        var selected: MutableList<JSONObject> = ArrayList()
        var rows: JSONArray = records(type)
        run {
            var i: Int = 0
            while ((i < rows.length())) {
                var row: JSONObject = rows.getJSONObject(i)
                if ((matches(row, filters))) {
                    selected.add(row)
                }
                i++
            }
        }
        selected.sortWith(Comparator.comparing(lambda1@ { r: JSONObject -> r.optString("date", r.optString("name", r.optString("title"))) }).thenComparing(lambda2@ { r -> r.optString("id") }))
        var items: JSONArray = JSONArray()
        var related: JSONArray = (if (type.equals("material")) records("material_movement") else (if (type.equals("equipment")) records("equipment_usage") else JSONArray()))
        run {
            var i: Int = offset
            while (((i < selected.size) && (items.length() < limit))) {
                var row: JSONObject = RevierRules.view(selected.get(i))
                if ((type.equals("equipment"))) {
                    row.put("next_maintenance_due_on", EquipmentMaintenanceRules.nextDue(row))
                }
                if (((type.equals("material") || type.equals("equipment")))) {
                    var history: JSONArray = JSONArray()
                    var field: String = (if (type.equals("material")) "material_id" else "equipment_id")
                    run {
                        var j: Int = 0
                        while ((j < related.length())) {
                            if ((row.getString("id").equals(related.getJSONObject(j).optString(field)))) {
                                history.put(RevierRules.view(related.getJSONObject(j)))
                            }
                            j++
                        }
                    }
                    row.put((if (type.equals("material")) "movements" else "usages"), last(history, 20)).put("history_total", history.length())
                }
                items.put(row)
                i++
            }
        }
        return JSONObject().put("items", items).put(alias, items).put("total", selected.size).put("offset", offset).put("limit", limit)
    }
    @Throws(Exception::class)
    private fun propertyDetail(id: String, state: JSONObject): JSONObject {
        var result: JSONObject = detail("property", id, "property")
        var property: JSONObject = result.getJSONObject("property")
        result.put("customer", canonical(state, "customers", property.getString("customer_id")))
        for (group in arrayOf(arrayOf("visit", "visits"), arrayOf("care_plan", "care_plans"), arrayOf("inquiry", "inquiries"))) {
            result.put(group[1], listing(group[0], group[1], JSONObject().put("property_id", id).put("limit", 100)).getJSONArray("items"))
        }
        var history: JSONArray = repository.list(("" + (("" + RevierRules.ROOT + "audit:property:") + id) + ":"))
        result.put("history", last(history, 20)).put("history_total", history.length())
        return result
    }
    @Throws(Exception::class)
    private fun property(operation: String, args: JSONObject, state: JSONObject, request: String): JSONObject {
        var creating: Boolean = operation.equals("create_property")
        var old: JSONObject? = (if (creating) null else load("property", RevierRules.id(args, "property_id", true)))
        var changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (((old != null))) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject = base("property", (if (creating) RevierRules.newId("property", request) else old!!.getString("id")), old)
        for (field in arrayOf("customer_id", "name", "address", "district", "access_notes", "notes", "area_decimal", "area_unit", "latitude", "longitude", "active")) {
            if ((changes.has(field))) {
                row.put(field, changes.get(field))
            }
        }
        var customer: String = RevierRules.id(row, "customer_id", true)
        customer(state, customer, creating)
        if ((((old != null) && !old!!.getString("customer_id").equals(customer)))) {
            throw RevierRules.error("PROPERTY_CUSTOMER_CHANGE_REVIEW_REQUIRED", "Objektkunden nicht still wechseln. Ursprüngliche Zuordnung bleibt erhalten.")
        }
        row.put("name", RevierRules.text(row, "name", 240, true))
        for (field in arrayOf("address", "district", "access_notes", "notes", "area_unit")) {
            row.put(field, RevierRules.text(row, field, (if (field.equals("notes")) 10000 else 2000), false))
        }
        row.put("active", RevierRules.bool(row, "active", true))
        if ((row.has("area_decimal"))) {
            var area: String = RevierRules.decimal(row, "area_decimal", true, true)
            if ((area.isEmpty())) {
                row.remove("area_decimal")
            } else {
                row.put("area_decimal", area)
            }
        }
        var lat: Boolean = (row.has("latitude") && !row.isNull("latitude"))
        var lon: Boolean = (row.has("longitude") && !row.isNull("longitude"))
        if (((lat != lon))) {
            throw RevierRules.error("INVALID_INPUT", "Koordinaten gemeinsam angeben.")
        }
        if (((lat && ((((!java.lang.Double.isFinite(row.getDouble("latitude")) || (Math.abs(row.getDouble("latitude")) > 90)) || !java.lang.Double.isFinite(row.getDouble("longitude"))) || (Math.abs(row.getDouble("longitude")) > 180)))))) {
            throw RevierRules.error("INVALID_INPUT", "Ungültige Koordinaten.")
        }
        var saved: JSONObject = save("property", row, old, request, RevierRules.text(args, "reason", 2000, false))
        return JSONObject().put("record", saved).put("property", saved).put("property_id", row.getString("id"))
    }
    @Throws(Exception::class)
    private fun inquiry(operation: String, args: JSONObject, state: JSONObject, request: String): JSONObject {
        var creating: Boolean = operation.equals("create_inquiry")
        var old: JSONObject? = (if (creating) null else load("inquiry", RevierRules.id(args, "inquiry_id", true)))
        var changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (((old != null))) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject = base("inquiry", (if (creating) RevierRules.newId("inquiry", request) else old!!.getString("id")), old)
        for (field in arrayOf("customer_id", "property_id", "title", "concern", "received_at", "wish_from", "wish_until", "status", "inspection_notes", "measurements", "photo_ids", "offer_id", "order_id")) {
            if ((changes.has(field))) {
                row.put(field, changes.get(field))
            }
        }
        var customer: String = RevierRules.id(row, "customer_id", true)
        customer(state, customer, creating)
        if ((((old != null) && !old!!.getString("customer_id").equals(customer)))) {
            throw RevierRules.error("INQUIRY_CUSTOMER_IMMUTABLE", "Ursprünglichen Kundenbezug erhalten.")
        }
        var property: String = RevierRules.id(row, "property_id", false)
        if (((!property.isEmpty() && !load("property", property).getString("customer_id").equals(customer)))) {
            throw RevierRules.error("RELATION_MISMATCH", "Objekt und Kunde passen nicht zusammen.")
        }
        row.put("title", RevierRules.text(row, "title", 240, true)).put("concern", RevierRules.text(row, "concern", 10000, false)).put("property_id", property)
        var received: String = RevierRules.text(row, "received_at", 80, false)
        row.put("received_at", (if (received.isEmpty()) stamp() else RevierRules.instant(received)))
        var from: String = RevierRules.date(row, "wish_from", false)
        var until: String = RevierRules.date(row, "wish_until", false)
        if ((((!from.isEmpty() && !until.isEmpty()) && (until.compareTo(from) < 0)))) {
            throw RevierRules.error("INVALID_INPUT", "Wunschzeitraum endet vor seinem Beginn.")
        }
        row.put("wish_from", from).put("wish_until", until)
        var status: String = RevierRules.text(row, "status", 30, false)
        if ((status.isEmpty())) {
            status = "new"
        }
        if ((!Arrays.asList("new", "inspection_planned", "offered", "accepted", "declined", "closed").contains(status))) {
            throw RevierRules.error("INVALID_INPUT", "Ungültiger Anfragestatus.")
        }
        row.put("status", status)
        for (relation in arrayOf(arrayOf("offer_id", "offers"), arrayOf("order_id", "orders"))) {
            var linked: String = RevierRules.id(row, relation[0], false)
            if (((!linked.isEmpty() && !customer.equals(canonical(state, relation[1], linked).optString("customer_id"))))) {
                throw RevierRules.error("RELATION_MISMATCH", "Angebot/Auftrag und Anfrage gehören zu verschiedenen Kunden.")
            }
            if ((row.has(relation[0]))) {
                row.put(relation[0], linked)
            }
        }
        if ((status.equals("accepted"))) {
            var order: String = row.optString("order_id")
            if ((order.isEmpty())) {
                throw RevierRules.error("ACCEPTANCE_REQUIRED", "Annahme zuerst mit einem bestätigten Auftrag verknüpfen.")
            }
            var offerId: String = row.optString("offer_id")
            if (((((old != null) && old!!.optString("acceptance_source").equals("direct")) && !old!!.optString("order_id").equals(order)))) {
                throw RevierRules.error("ACCEPTANCE_VERSION_MISMATCH", "Bestätigten Direktauftrag nicht still austauschen.")
            }
            if (((!offerId.isEmpty() && !row.optString("acceptance_source").equals("direct")))) {
                var offer: JSONObject = canonical(state, "offers", offerId)
                var raw: String = repository.get((("" + OfferDecisionRules.PREFIX + "current:") + offerId), "")
                var decision: JSONObject? = (if (raw.isEmpty()) null else JSONObject(raw))
                if (((((!order.equals(offer.optString("converted_order_id")) || (decision == null)) || !decision!!.optString("decision").equals("accepted")) || !decision!!.optString("content_version").equals(OfferDecisionRules.contentVersion(offer))))) {
                    throw RevierRules.error("ACCEPTANCE_VERSION_MISMATCH", "Angenommene Angebotsversion und daraus erzeugten Auftrag zuerst prüfen.")
                }
            } else {
                if (((!row.optString("acceptance_source").equals("direct") || row.optString("acceptance_evidence").isEmpty()))) {
                    throw RevierRules.error("ACCEPTANCE_REQUIRED", "Direkte Annahme mit Zeitpunkt und Nachweis über Auftrag direkt annehmen erfassen.")
                }
            }
        }
        if ((((changes.has("inspection_notes") || changes.has("measurements")) || changes.has("photo_ids")))) {
            for (field in arrayOf("measurements", "photo_ids")) {
                if (((row.has(field) && !(row.get(field) is JSONArray)))) {
                    throw RevierRules.error("INVALID_INPUT", "Messwerte und Fotoreferenzen als Listen angeben.")
                }
            }
            if ((row.optString("inspection_appointment_id").isEmpty())) {
                throw RevierRules.error("INSPECTION_REQUIRED", "Besichtigung zuerst als Termin planen; Beobachtungen bleiben dem Termin zugeordnet.")
            }
            row.put("inspection_notes", RevierRules.text(row, "inspection_notes", 10000, false))
            var measurements: JSONArray? = row.optJSONArray("measurements")
            if (((measurements != null))) {
                if (((measurements!!.length() > 200))) {
                    throw RevierRules.error("INVALID_INPUT", "Zu viele Messwerte.")
                }
                var normalized: JSONArray = JSONArray()
                run {
                    var i: Int = 0
                    while ((i < measurements!!.length())) {
                        var item: JSONObject = measurements!!.getJSONObject(i)
                        normalized.put(JSONObject().put("name", RevierRules.text(item, "name", 200, true)).put("value_decimal", RevierRules.decimal(item, "value_decimal", false, false)).put("unit", RevierRules.text(item, "unit", 60, false)))
                        i++
                    }
                }
                row.put("measurements", normalized)
            }
            var photos: JSONArray? = row.optJSONArray("photo_ids")
            if (((photos != null))) {
                if (((photos!!.length() > 200))) {
                    throw RevierRules.error("INVALID_INPUT", "Zu viele Fotoreferenzen.")
                }
                var seen: MutableSet<String> = HashSet()
                run {
                    var i: Int = 0
                    while ((i < photos!!.length())) {
                        var photo: String = photos!!.getString(i)
                        var source: JSONObject = canonical(state, "photos", photo)
                        if ((((!seen.add(photo) || !customer.equals(source.optString("customer_id"))) || source.optString("role").equals("profile")))) {
                            throw RevierRules.error("PHOTO_CONTEXT_MISMATCH", "Besichtigungsfoto benötigt den passenden Dokumentationskontext.")
                        }
                        i++
                    }
                }
            }
        }
        var saved: JSONObject = save("inquiry", row, old, request, RevierRules.text(args, "reason", 2000, false))
        return JSONObject().put("record", saved).put("inquiry", saved).put("inquiry_id", row.getString("id"))
    }
    @Throws(Exception::class)
    private fun inspectEditAction(args: JSONObject): JSONObject {
        var tool: String = RevierRules.text(args, "original_tool", 100, true)
        var original: JSONObject = args.getJSONObject("original_args")
        RevierRules.bounded(original)
        var request: String = original.optString("request_id")
        // Native editor/timer receipts live in SQLite pending; inspecting them never repeats a write.
        val ownerTool = PixelMcpOwnerTools.write(tool) || OfferTools.write(tool) || AppointmentTools.write(tool)
        if (!ownerTool && (PixelMcpWriteTools.handles(tool) || PixelMcpEntityWriteTools.handles(tool) || PixelMcpSessionTools.handles(tool))) {
            if (request.length < 8) throw RevierRules.error("INVALID_INPUT", "Ursprüngliche Aktionskennung fehlt.")
            val receipt = repository.nativeReceipt(request)
                ?: return JSONObject().put("state", "unknown").put("business_write_performed", false)
            val fingerprint = PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", original))
            if (receipt.optString("tool") != tool || receipt.optString("fingerprint") != fingerprint) {
                throw RevierRules.error("REPLAY_MISMATCH", "Gesicherte Argumente und vorhandener Aktionsbeleg stimmen nicht überein.")
            }
            val id = receipt.optString("entity_id", receipt.optString("work_record_id"))
            if (id.isEmpty()) return JSONObject().put("state", "unknown").put("business_write_performed", false)
            val result = receipt.optJSONObject("result")?.let { RevierRules.copy(it) } ?: JSONObject()
            result.put("entity_id", id).put("entity_type", receipt.optString("entity_type"))
            return JSONObject().put("state", if (receipt.optBoolean("test_purged") || receipt.optBoolean("_result_content_removed") || result.optBoolean("_result_content_removed")) "content_removed" else "confirmed")
                .put("entity_id", id).put("entity_type", receipt.optString("entity_type")).put("result", result).put("business_write_performed", false)
        }
        var key: String
        var fingerprint: String
        var kind: String? = when (tool) {
                        "saveCustomer" -> "customer"
                        "saveOrder" -> "order"
                        "saveWorklog" -> "worklog"
                        else -> null
                    }
        if (((kind != null))) {
            request = original.optString("_request_id")
            var payload: JSONObject = RevierRules.copy(original)
            payload.remove("_request_id")
            if (((kind!!.equals("worklog") && payload.optString("_session_id").isEmpty()))) {
                payload.remove("_session_id")
            }
            key = ("editor_commit:" + PixelMcpWriteTools.hash(request))
            fingerprint = PixelMcpWriteTools.hash(JSONObject().put("kind", kind).put("payload", payload))
        } else {
            if ((tool.equals("savePhoto"))) {
                request = original.optString("_photo_request_id")
                if (((request.length < 8))) {
                    throw RevierRules.error("INVALID_INPUT", "Ursprüngliche Fotoaktion fehlt.")
                }
                var raw: String = repository.get(("photo-ui:" + request), "")
                if ((raw.isEmpty())) {
                    return JSONObject().put("state", "unknown").put("business_write_performed", false)
                }
                var receipt: JSONObject = JSONObject(raw)
                var photoPayload: JSONObject = RevierRules.copy(original)
                photoPayload.remove("thumbnail_data")
                photoPayload.remove("_capture_context")
                if ((!photoPayload.has("data"))) {
                    photoPayload.remove("data")
                    if ((!receipt.has("metadata_hash"))) {
                        return JSONObject().put("state", "unknown").put("business_write_performed", false)
                    }
                    if ((!PixelMcpWriteTools.hash(photoPayload).equals(receipt.optString("metadata_hash")))) {
                        throw RevierRules.error("REPLAY_MISMATCH", "Gesicherter Fotobezug und Aktionsbeleg stimmen nicht überein.")
                    }
                } else {
                    if ((!PixelMcpWriteTools.hash(photoPayload).equals(receipt.optString("hash")))) {
                        throw RevierRules.error("REPLAY_MISMATCH", "Gesicherte Fotoangaben und Aktionsbeleg stimmen nicht überein.")
                    }
                }
                return JSONObject().put("state", "confirmed").put("entity_id", receipt.getString("id")).put("entity_type", "photo").put("business_write_performed", false)
            } else {
                if ((((((!PixelMcpOwnerTools.write(tool) && !OfferTools.write(tool)) && !AppointmentTools.write(tool)) && !tool.equals("update_photo")) && !tool.equals("update_business_settings")))) {
                    throw RevierRules.error("INVALID_INPUT", "Diese ursprüngliche Editoraktion wird nicht unterstützt.")
                }
                key = ("receipt:mcp_owner_" + PixelMcpWriteTools.hash(request))
                fingerprint = PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", original))
            }
        }
        if (((request.length < 8))) {
            throw RevierRules.error("INVALID_INPUT", "Ursprüngliche Aktionskennung fehlt.")
        }
        var raw: String = repository.get(key, "")
        if ((raw.isEmpty())) {
            return JSONObject().put("state", "unknown").put("business_write_performed", false)
        }
        var receipt: JSONObject = JSONObject(raw)
        if ((!fingerprint.equals(receipt.optString("fingerprint")))) {
            throw RevierRules.error("REPLAY_MISMATCH", "Gesicherte Argumente und vorhandener Aktionsbeleg stimmen nicht überein.")
        }
        var result: JSONObject = (if ((kind == null)) receipt.getJSONObject("result") else JSONObject().put("entity_id", receipt.getString("id")))
        var id: String = result.optString("entity_id")
        if (((id.isEmpty() && (result.optJSONObject("record") != null)))) {
            id = result.getJSONObject("record").optString("id")
        }
        if (((id.isEmpty() && (result.optJSONObject("photo") != null)))) {
            id = result.getJSONObject("photo").optString("id")
        }
        if ((id.isEmpty())) {
            for (field in arrayOf("offer_id", "invoice_id", "entry_id", "order_id", "appointment_id", "property_id", "inquiry_id", "visit_id", "material_id", "equipment_id", "care_plan_id", "photo_id", "payment_id")) {
                if ((!result.optString(field).isEmpty())) {
                    id = result.getString(field)
                    break
                }
            }
        }
        var entityType: String = (if ((kind == null)) result.optString("entity_type") else kind)
        if ((tool.equals("update_business_settings"))) {
            if (((!entityType.equals("business_settings") && (result.optJSONObject("settings") == null)))) {
                return JSONObject().put("state", "unknown").put("business_write_performed", false)
            }
            id = "business_settings"
            entityType = "settings"
        } else {
            if ((tool.equals("update_photo"))) {
                entityType = "photo"
            }
        }
        return JSONObject().put("state", (if (result.optBoolean("_result_content_removed")) "content_removed" else "confirmed")).put("entity_id", id).put("entity_type", entityType).put("result", result).put("business_write_performed", false)
    }
    @Throws(Exception::class)
    private fun archiveList(args: JSONObject): JSONObject {
        var entries: MutableList<JSONObject> = ArrayList()
        for (type in ARCHIVABLE) {
            if (((!args.optString("entity").isEmpty() && !args.getString("entity").equals(type)))) {
                continue
            }
            var rows: JSONArray = records(type)
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    var row: JSONObject = rows.getJSONObject(i)
                    if ((row.optBoolean("archived"))) {
                        entries.add(JSONObject().put("entity", type).put("record", RevierRules.view(row)).put("record_hash", RevierRules.hash(row)))
                    }
                    i++
                }
            }
        }
        entries.sortWith(Comparator.comparing(lambda3@ { entry: JSONObject -> entry.optJSONObject("record").optString("archived_at") }).reversed())
        var offset: Int = RevierRules.integer(args, "offset", 0, 1000000, 0)
        var limit: Int = RevierRules.integer(args, "limit", 1, 100, 30)
        var page: JSONArray = JSONArray()
        run {
            var i: Int = offset
            while (((i < entries.size) && (page.length() < limit))) {
                page.put(entries.get(i))
                i++
            }
        }
        return JSONObject().put("items", page).put("total", entries.size).put("offset", offset).put("limit", limit)
    }
    @Throws(Exception::class)
    private fun archiveChange(operation: String, args: JSONObject, state: JSONObject, request: String): JSONObject {
        var type: String = RevierRules.text(args, "entity", 40, true)
        var id: String = RevierRules.id(args, "entity_id", true)
        if ((!Arrays.asList(*ARCHIVABLE).contains(type))) {
            throw RevierRules.error("INVALID_INPUT", "Nicht unterstützte Eintragsart.")
        }
        var old: JSONObject = load(type, id)
        RevierRules.cas(old, args.optString("expected_record_hash"))
        if ((!true.equals(args.opt("confirmed")))) {
            throw RevierRules.error("CONFIRMATION_REQUIRED", "Diese Änderung ausdrücklich bestätigen.")
        }
        var reason: String = RevierRules.text(args, "reason", 4000, false)
        if ((operation.equals("set_revier_archived"))) {
            var archived: Boolean = RevierRules.bool(args, "archived", true)
            if (((archived == old.optBoolean("archived")))) {
                return JSONObject().put("record", RevierRules.view(old)).put("entity", type).put("entity_id", id).put("archived", archived).put("already_in_state", true)
            }
            var running: JSONObject = repository.currentSession()
            if ((((archived && references(running, id)) && !Arrays.asList("IDLE", "FINALIZED", "CANCELLED").contains(running.optString("state"))))) {
                throw RevierRules.error("DEPENDENCY_EXISTS", "Eine laufende Arbeit benötigt diesen Eintrag. Arbeit zuerst prüfen und abschließen.")
            }
            if ((((archived && type.equals("visit")) && RevierRules.activeVisit(old)))) {
                throw RevierRules.error("VISIT_STILL_ACTIVE", "Einsatz zuerst ausdrücklich absagen oder abschließen; Archivieren beendet keine Arbeit.")
            }
            var row: JSONObject = RevierRules.copy(old).put("archived", archived)
            if ((archived)) {
                row.put("archived_at", stamp())
                if ((row.has("active"))) {
                    row.put("active_before_archive", row.get("active")).put("active", false)
                }
            } else {
                if ((row.has("active_before_archive"))) {
                    row.put("active", row.get("active_before_archive"))
                }
                row.remove("active_before_archive")
                row.remove("archived_at")
            }
            var saved: JSONObject = save(type, row, old, request, reason)
            return JSONObject().put("record", saved).put("entity", type).put("entity_id", id).put("archived", archived)
        }
        if ((!old.optBoolean("archived"))) {
            throw RevierRules.error("ARCHIVE_REQUIRED", "Eintrag zuerst archivieren und anschließend endgültiges Löschen bewusst bestätigen.")
        }
        var related: JSONArray = repository.list(("" + RevierRules.ROOT + "entity:"))
        run {
            var i: Int = 0
            while ((i < related.length())) {
                var row: JSONObject = related.getJSONObject(i)
                if (((row.optBoolean("deleted") || row.optString("id").equals(id)))) {
                    i++
                    continue
                }
                if ((references(row, id))) {
                    throw RevierRules.error("DEPENDENCY_EXISTS", (("" + ("Ein anderer lokaler Eintrag verweist noch auf diesen Datensatz: " + row.optString("kind")) + "/") + row.optString("id")))
                }
                i++
            }
        }
        if (((references(state, id) || references(repository.sessionContexts(), id)))) {
            throw RevierRules.error("DEPENDENCY_EXISTS", "Bestehende Geschäftsdaten oder Arbeitssitzungen benötigen diesen Eintrag. Archiviert erhalten; Verknüpfungen einzeln prüfen.")
        }
        var tombstone: JSONObject = JSONObject().put("format_version", 1).put("kind", type).put("id", id).put("deleted", true).put("deleted_at", stamp()).put("request_id", request)
        repository.put(key(type, id), tombstone.toString())
        for (prefix in arrayOf(("" + (("" + (("" + RevierRules.ROOT + "audit:") + type) + ":") + id) + ":"), (if (type.equals("care_plan")) ("" + (("" + RevierRules.ROOT + "rule:") + id) + ":") else ("" + RevierRules.ROOT + "unused:")))) {
            var rows: JSONArray = repository.rawEntries(prefix)
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    repository.put(rows.getJSONObject(i).getString("key"), tombstone.toString())
                    i++
                }
            }
        }
        for (prefix in arrayOf(("" + RevierRules.ROOT + "request:"), "receipt:")) {
            var rows: JSONArray = repository.rawEntries(prefix)
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    var entry: JSONObject = rows.getJSONObject(i)
                    var receipt: JSONObject = JSONObject(entry.getString("value"))
                    if ((((receipt.optJSONObject("result") != null) && references(receipt.getJSONObject("result"), id)))) {
                        receipt.put("result", JSONObject().put("_result_content_removed", true).put("entity", type).put("entity_id", id))
                        repository.put(entry.getString("key"), receipt.toString())
                    }
                    i++
                }
            }
        }
        return JSONObject().put("entity", type).put("entity_id", id).put("purged", true).put("historical_backups_unchanged", true)
    }
    @Throws(Exception::class)
    private fun directOrder(args: JSONObject, state: JSONObject, request: String): JSONObject {
        var old: JSONObject = load("inquiry", RevierRules.id(args, "inquiry_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        if ((!true.equals(args.opt("confirmed")))) {
            throw RevierRules.error("CONFIRMATION_REQUIRED", "Direkte Auftragsannahme ausdrücklich bestätigen.")
        }
        if ((!old.optString("order_id").isEmpty())) {
            throw RevierRules.error("ORDER_ALREADY_LINKED", "Die Anfrage hat bereits einen Auftrag. Vorhandenen Auftrag öffnen.")
        }
        var evidence: String = RevierRules.text(args, "evidence", 4000, true)
        var at: String = RevierRules.instant(RevierRules.text(args, "accepted_at", 80, true))
        customer(state, old.getString("customer_id"), true)
        var order: JSONObject = JSONObject().put("id", RevierRules.newId("order", request)).put("customer_id", old.getString("customer_id")).put("title", old.getString("title")).put("description", old.optString("concern")).put("status", "Geplant").put("customer_visible", 1).put("_request_id", ("direct_order_" + PixelMcpWriteTools.hash(request)))
        if ((!args.optString("billing_customer_id").isEmpty())) {
            order.put("billing_customer_id", args.getString("billing_customer_id"))
        }
        var property: String = old.optString("property_id")
        if ((!property.isEmpty())) {
            order.put("location", load("property", property).optString("address"))
        }
        var id: String = repository.createDirectOrder(order)
        var row: JSONObject = RevierRules.copy(old).put("order_id", id).put("status", "accepted").put("acceptance_source", "direct").put("accepted_at", at).put("acceptance_evidence", evidence)
        var saved: JSONObject = save("inquiry", row, old, request, "Direkte Auftragsannahme mit Nachweis")
        return JSONObject().put("record", saved).put("inquiry", saved).put("order_id", id).put("invoice_created", false)
    }
    @Throws(Exception::class)
    private fun inspection(args: JSONObject, state: JSONObject, request: String): JSONObject {
        var old: JSONObject = load("inquiry", RevierRules.id(args, "inquiry_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        var starts: String = RevierRules.instant(RevierRules.text(args, "starts_at", 80, true))
        var ends: String = RevierRules.instant(RevierRules.text(args, "ends_at", 80, true))
        if ((!Instant.parse(ends).isAfter(Instant.parse(starts)))) {
            throw RevierRules.error("INVALID_INPUT", "Besichtigung benötigt eine positive Dauer.")
        }
        var zone: String = RevierRules.text(args, "timezone", 80, false)
        if ((zone.isEmpty())) {
            zone = "Europe/Vienna"
        }
        ZoneId.of(zone)
        var previous: String = old.optString("inspection_appointment_id")
        var mode: String = args.optString("mode", (if (previous.isEmpty()) "first" else ""))
        if (((!Arrays.asList("first", "reschedule", "additional", "reopen").contains(mode) || (!previous.isEmpty() && mode.equals("first"))))) {
            throw RevierRules.error("INSPECTION_ACTION_REQUIRED", "Besichtigung verschieben, zusätzliche Besichtigung oder abgesagten Vorgang neu öffnen bewusst wählen.")
        }
        var prior: JSONObject? = (if (previous.isEmpty()) null else canonical(state, "appointments", previous))
        if (((mode.equals("reschedule") || mode.equals("reopen")))) {
            if (((prior == null))) {
                throw RevierRules.error("REFERENCE_NOT_FOUND", "Bisherige Besichtigung fehlt.")
            }
            if ((!AppointmentRules.hash(prior).equals(args.optString("expected_appointment_hash")))) {
                throw RevierRules.error("WRITE_CONFLICT", "Bisherige Besichtigung wurde geändert.")
            }
            if ((((mode.equals("reschedule") && prior!!.optString("status").equals("cancelled")) || (mode.equals("reopen") && !prior!!.optString("status").equals("cancelled"))))) {
                throw RevierRules.error("INSPECTION_ACTION_REQUIRED", "Status der bisherigen Besichtigung zuerst prüfen.")
            }
        }
        var appointment: JSONObject = JSONObject().put("request_id", request).put("customer_id", old.getString("customer_id")).put("title", ("Besichtigung · " + old.getString("title"))).put("purpose", "inspection").put("weather_profile", "inspection").put("starts_at", starts).put("ends_at", ends).put("timezone", zone).put("notes", RevierRules.text(args, "notes", 10000, false))
        var property: String = old.optString("property_id")
        appointment.put("location", (if (property.isEmpty()) "" else load("property", property).optString("address")))
        var result: JSONObject
        if ((mode.equals("reschedule"))) {
            var updates: JSONObject = JSONObject().put("starts_at", starts).put("ends_at", ends).put("timezone", zone)
            if ((args.has("notes"))) {
                updates.put("notes", args.getString("notes"))
            }
            var change: JSONObject = JSONObject().put("request_id", request).put("appointment_id", previous).put("expected_record_hash", args.getString("expected_appointment_hash")).put("updates", updates)
            McpSchema.validate(change, AppointmentTools.schema("update_appointment"))
            result = repository.changeInspection("update_appointment", change)
        } else {
            McpSchema.validate(appointment, AppointmentTools.schema("create_appointment"))
            result = repository.planInspection(appointment)
        }
        var appointmentId: String = result.optString("appointment_id", result.optString("id"))
        if (((appointmentId.isEmpty() && (result.optJSONObject("appointment") != null)))) {
            appointmentId = result.getJSONObject("appointment").optString("id")
        }
        if (((appointmentId.isEmpty() || (mode.equals("reschedule") && !appointmentId.equals(previous))))) {
            throw RevierRules.error("INSPECTION_COMMIT_UNCONFIRMED", "Termin konnte nicht eindeutig bestätigt werden.")
        }
        var row: JSONObject = RevierRules.copy(old)
        var visits: JSONArray? = row.optJSONArray("inspection_visits")
        if (((visits == null))) {
            visits = JSONArray()
        }
        if (((!previous.isEmpty() && !mode.equals("reschedule")))) {
            visits!!.put(JSONObject().put("appointment_id", previous).put("notes", row.optString("inspection_notes")).put("measurements", (if ((row.optJSONArray("measurements") == null)) JSONArray() else row.getJSONArray("measurements"))).put("photo_ids", (if ((row.optJSONArray("photo_ids") == null)) JSONArray() else row.getJSONArray("photo_ids"))))
            row.put("inspection_notes", "").put("measurements", JSONArray()).put("photo_ids", JSONArray())
        }
        row.put("inspection_visits", visits).put("inspection_appointment_id", appointmentId).put("status", "inspection_planned")
        var saved: JSONObject = save("inquiry", row, old, request, ("Besichtigung: " + mode))
        return JSONObject().put("record", saved).put("inquiry", saved).put("appointment_id", appointmentId).put("inspection_action", mode).put("external_calendar_status", "unknown")
    }
    @Throws(Exception::class)
    private fun needs(args: JSONObject): JSONArray {
        var values: JSONArray? = args.optJSONArray("material_needs")
        if (((values == null))) {
            if ((args.has("material_needs"))) {
                throw RevierRules.error("INVALID_INPUT", "Materialbedarf als Liste angeben.")
            }
            return JSONArray()
        }
        if (((values!!.length() > 200))) {
            throw RevierRules.error("INVALID_INPUT", "Zu viele Materialpositionen in einem Einsatz.")
        }
        var normalized: JSONArray = JSONArray()
        var seen: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while ((i < values!!.length())) {
                var item: JSONObject = values!!.getJSONObject(i)
                var material: String = RevierRules.id(item, "material_id", true)
                if ((!seen.add(material))) {
                    throw RevierRules.error("DUPLICATE_MATERIAL", "Materialbedarf pro Katalogeintrag zusammenfassen.")
                }
                var source: JSONObject = load("material", material)
                if ((!source.optBoolean("active", true))) {
                    throw RevierRules.error("MATERIAL_INACTIVE", "Inaktives Material nicht neu einplanen.")
                }
                var quantity: String = RevierRules.decimal(item, "quantity_decimal", true, false)
                if (((BigDecimal(quantity).signum() == 0))) {
                    throw RevierRules.error("INVALID_INPUT", "Materialbedarf muss größer als null sein.")
                }
                normalized.put(JSONObject().put("material_id", material).put("quantity_decimal", quantity).put("unit", source.getString("unit")).put("name", source.getString("name")))
                i++
            }
        }
        return normalized
    }
    @Throws(Exception::class)
    private fun equipmentIds(args: JSONObject): JSONArray {
        var values: JSONArray? = args.optJSONArray("equipment_ids")
        if (((values == null))) {
            if ((args.has("equipment_ids"))) {
                throw RevierRules.error("INVALID_INPUT", "Gerätebedarf als Liste angeben.")
            }
            return JSONArray()
        }
        if (((values!!.length() > 200))) {
            throw RevierRules.error("INVALID_INPUT", "Zu viele Geräte in einem Einsatz.")
        }
        var out: JSONArray = JSONArray()
        var seen: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while ((i < values!!.length())) {
                var value: String = values!!.getString(i)
                load("equipment", value)
                if ((!seen.add(value))) {
                    throw RevierRules.error("DUPLICATE_EQUIPMENT", "Gerät wurde mehrfach ausgewählt.")
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
            while ((i < ids.length())) {
                var item: JSONObject = load("equipment", ids.getString(i))
                if (((!item.optBoolean("active", true) || !item.optString("condition", "ready").equals("ready")))) {
                    out.put(JSONObject().put("code", "EQUIPMENT_UNAVAILABLE").put("equipment_id", item.getString("id")).put("name", item.getString("name")).put("condition", item.optString("condition")))
                }
                i++
            }
        }
        return out
    }
    @Throws(Exception::class)
    private fun normalizeRule(row: JSONObject): JSONObject {
        var rule: JSONObject = JSONObject()
        for (field in arrayOf("anchor_date", "interval_days", "planned_minutes", "season_start", "season_end", "material_needs", "equipment_ids", "title", "notes")) {
            if ((row.has(field))) {
                rule.put(field, row.get(field))
            }
        }
        rule.put("anchor_date", RevierRules.date(rule, "anchor_date", true)).put("interval_days", RevierRules.integer(rule, "interval_days", 1, 365, -1)).put("planned_minutes", RevierRules.integer(rule, "planned_minutes", 1, 1440, -1)).put("title", RevierRules.text(rule, "title", 240, true)).put("notes", RevierRules.text(rule, "notes", 10000, false))
        if ((((rule.getInt("interval_days") < 1) || (rule.getInt("planned_minutes") < 1)))) {
            throw RevierRules.error("INVALID_INPUT", "Turnus und geplante Dauer sind erforderlich.")
        }
        RevierRules.seasonField(rule, "season_start")
        RevierRules.seasonField(rule, "season_end")
        rule.put("material_needs", needs(rule)).put("equipment_ids", equipmentIds(rule))
        return rule
    }
    @Throws(Exception::class)
    private fun ruleVersions(planId: String): JSONArray {
        var rows: JSONArray = repository.list(("" + (("" + RevierRules.ROOT + "rule:") + planId) + ":"))
        run {
            var i: Int = 0
            while ((i < rows.length())) {
                RevierRules.version(rows.getJSONObject(i))
                i++
            }
        }
        return rows
    }
    @Throws(Exception::class)
    private fun ruleFor(planId: String, day: LocalDate): JSONObject? {
        var chosen: JSONObject? = null
        var rows: JSONArray = ruleVersions(planId)
        run {
            var i: Int = 0
            while ((i < rows.length())) {
                var item: JSONObject = rows.getJSONObject(i)
                var from: String = item.getString("effective_from")
                if ((((from.compareTo(day.toString()) <= 0) && (((chosen == null) || (item.getInt("version") > chosen!!.getInt("version"))))))) {
                    chosen = item
                }
                i++
            }
        }
        return chosen
    }
    @Throws(Exception::class)
    private fun care(operation: String, args: JSONObject, state: JSONObject, request: String): JSONObject {
        var creating: Boolean = operation.equals("create_care_plan")
        var old: JSONObject? = (if (creating) null else load("care_plan", RevierRules.id(args, "care_plan_id", true)))
        var changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (((old != null))) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject = base("care_plan", (if (creating) RevierRules.newId("care", request) else old!!.getString("id")), old)
        for (field in arrayOf("property_id", "title", "anchor_date", "interval_days", "planned_minutes", "season_start", "season_end", "material_needs", "equipment_ids", "active", "notes")) {
            if ((changes.has(field))) {
                row.put(field, changes.get(field))
            }
        }
        var property: String = RevierRules.id(row, "property_id", true)
        var `object`: JSONObject = load("property", property)
        customer(state, `object`.getString("customer_id"), creating)
        if ((!`object`.optBoolean("active", true))) {
            throw RevierRules.error("PROPERTY_INACTIVE", "Inaktives Objekt nicht neu einplanen.")
        }
        if ((((old != null) && !old!!.getString("property_id").equals(property)))) {
            throw RevierRules.error("RELATION_IMMUTABLE", "Pflegeplan behält seinen ursprünglichen Objektbezug.")
        }
        row.put("customer_id", `object`.getString("customer_id")).put("active", RevierRules.bool(row, "active", true))
        var rule: JSONObject = normalizeRule(row)
        run {
            var it: MutableIterator<String> = rule.keys()
            while (it.hasNext()) {
                var field: String = it.next()
                row.put(field, rule.get(field))
            }
        }
        var effective: LocalDate = (if (creating) RevierRules.date(row.getString("anchor_date")) else RevierRules.date(RevierRules.text(args, "effective_from", 10, true)))
        var next: Int = (if ((old == null)) 1 else (old!!.getInt("rule_version") + 1))
        if ((!creating)) {
            if ((!args.optString("scope").equals("this_and_future"))) {
                throw RevierRules.error("SERIES_SCOPE_REQUIRED", "Für die Regeländerung ausdrücklich diesen und zukünftige Termine wählen.")
            }
            if ((effective.isBefore(today()))) {
                throw RevierRules.error("PAST_RULE_IMMUTABLE", "Vergangene Pflegevorkommen werden nicht geändert.")
            }
        }
        var version: JSONObject = JSONObject().put("format_version", 1).put("plan_id", row.getString("id")).put("version", next).put("effective_from", effective.toString()).put("rule", rule).put("active", row.getBoolean("active")).put("created_at", stamp())
        RevierRules.bounded(version)
        repository.put((("" + (("" + RevierRules.ROOT + "rule:") + row.getString("id")) + ":") + String.format(Locale.ROOT, "%08d", next)), version.toString())
        row.put("rule_version", next).put("rule_effective_from", effective.toString())
        var superseded: Int = 0
        var preserved: Int = 0
        if (((old != null))) {
            var visits: JSONArray = records("visit")
            run {
                var i: Int = 0
                while ((i < visits.length())) {
                    var before: JSONObject = visits.getJSONObject(i)
                    if ((((!row.getString("id").equals(before.optString("care_plan_id")) || (before.getString("occurrence_key").compareTo(effective.toString()) < 0)) || !RevierRules.activeVisit(before)))) {
                        i++
                        continue
                    }
                    if ((((before.optBoolean("manual_override") || before.optString("status").equals("in_progress")) || before.optString("status").equals("partial")))) {
                        preserved++
                        i++
                        continue
                    }
                    var changed: JSONObject = RevierRules.copy(before).put("status", "superseded").put("status_reason", ("Zukünftige Regelversion " + next)).put("superseded_by_rule_version", next)
                    save("visit", changed, before, request, "Zukünftige Pflegeversion geändert")
                    markToursForVisit(before.getString("id"), request, "Pflegeregel geändert")
                    superseded++
                    i++
                }
            }
        }
        var saved: JSONObject = save("care_plan", row, old, request, ("Pflegeregel Version " + next))
        return JSONObject().put("record", saved).put("care_plan", saved).put("care_plan_id", row.getString("id")).put("superseded_visits", superseded).put("preserved_manual_visits", preserved).put("equipment_warnings", equipmentWarnings(rule.getJSONArray("equipment_ids")))
    }
    @Throws(Exception::class)
    private fun generatedVisit(plan: JSONObject, version: JSONObject, day: LocalDate, old: JSONObject?): JSONObject {
        var rule: JSONObject = version.getJSONObject("rule")
        var request: String = (("" + plan.getString("id") + ":") + day)
        var visit: JSONObject = base("visit", (if ((old == null)) RevierRules.newId("visit", request) else old!!.getString("id")), old)
        visit.put("property_id", plan.getString("property_id")).put("customer_id", plan.getString("customer_id")).put("title", rule.getString("title")).put("date", day.toString()).put("planned_minutes", rule.getInt("planned_minutes")).put("care_plan_id", plan.getString("id")).put("occurrence_key", day.toString()).put("rule_version", version.getInt("version")).put("rule_snapshot", rule).put("material_needs", rule.getJSONArray("material_needs")).put("equipment_ids", rule.getJSONArray("equipment_ids")).put("notes", rule.optString("notes")).put("status", "planned").put("manual_override", false).put("window_start", "").put("window_end", "").put("timezone", "Europe/Vienna")
        return visit
    }
    @Throws(Exception::class)
    private fun generate(args: JSONObject, state: JSONObject, request: String): JSONObject {
        var from: LocalDate = RevierRules.date(RevierRules.text(args, "from_date", 10, true))
        var until: LocalDate = RevierRules.date(RevierRules.text(args, "to_date", 10, true))
        var days: Long = ChronoUnit.DAYS.between(from, until)
        if (((((from.isBefore(today()) || until.isAfter(today().plusDays((179).toLong()))) || (days < 0)) || (days > 179)))) {
            throw RevierRules.error("GENERATION_HORIZON_INVALID", "Pflegevorkommen nur ab heute in einem rollierenden Horizont von höchstens180Tagen erzeugen.")
        }
        var plans: JSONArray = records("care_plan")
        var created: JSONArray = JSONArray()
        var resumed: JSONArray = JSONArray()
        var preserved: Int = 0
        if ((!args.optString("care_plan_id").isEmpty())) {
            load("care_plan", RevierRules.id(args, "care_plan_id", true))
        }
        run {
            var i: Int = 0
            while ((i < plans.length())) {
                var plan: JSONObject = plans.getJSONObject(i)
                if (((!args.optString("care_plan_id").isEmpty() && !args.getString("care_plan_id").equals(plan.getString("id"))))) {
                    i++
                    continue
                }
                var `object`: JSONObject = load("property", plan.getString("property_id"))
                if ((!`object`.optBoolean("active", true))) {
                    i++
                    continue
                }
                customer(state, plan.getString("customer_id"), false)
                run {
                    var day: LocalDate = from
                    while (!day.isAfter(until)) {
                        var version: JSONObject? = ruleFor(plan.getString("id"), day)
                        if (((((version == null) || !version!!.getBoolean("active")) || !RevierRules.occurs(version!!.getJSONObject("rule"), day)))) {
                            day = day.plusDays((1).toLong())
                            continue
                        }
                        var occurrence: String = (("" + (("" + RevierRules.ROOT + "occurrence:") + plan.getString("id")) + ":") + day)
                        var existing: String = repository.get(occurrence, "")
                        var old: JSONObject? = (if (existing.isEmpty()) null else load("visit", existing))
                        if ((((old != null) && !old!!.optString("status").equals("superseded")))) {
                            preserved++
                            day = day.plusDays((1).toLong())
                            continue
                        }
                        if ((((old != null) && old!!.optBoolean("manual_override")))) {
                            preserved++
                            day = day.plusDays((1).toLong())
                            continue
                        }
                        var visit: JSONObject = generatedVisit(plan, version, day, old)
                        save("visit", visit, old, request, "Fälliger Pflegebesuch erzeugt")
                        repository.put(occurrence, visit.getString("id"))
                        if (((old == null))) {
                            created.put(visit.getString("id"))
                        } else {
                            resumed.put(visit.getString("id"))
                        }
                        day = day.plusDays((1).toLong())
                    }
                }
                i++
            }
        }
        return JSONObject().put("created_visit_ids", created).put("reactivated_visit_ids", resumed).put("preserved_occurrences", preserved).put("from_date", from.toString()).put("to_date", until.toString())
    }
    @Throws(Exception::class)
    private fun validateVisit(row: JSONObject, state: JSONObject) {
        var property: String = RevierRules.id(row, "property_id", true)
        var `object`: JSONObject = load("property", property)
        var ending: Boolean = row.optString("status").equals("cancelled")
        if (((!ending && !`object`.optBoolean("active", true)))) {
            throw RevierRules.error("PROPERTY_INACTIVE", "Objekt ist inaktiv.")
        }
        row.put("customer_id", `object`.getString("customer_id"))
        customer(state, row.getString("customer_id"), !ending)
        var order: String = RevierRules.id(row, "order_id", false)
        if (((!order.isEmpty() && !canonical(state, "orders", order).optString("customer_id").equals(row.getString("customer_id"))))) {
            throw RevierRules.error("RELATION_MISMATCH", "Auftrag und Einsatzobjekt gehören zu verschiedenen Kunden.")
        }
        row.put("order_id", order).put("title", RevierRules.text(row, "title", 240, true)).put("date", RevierRules.date(row, "date", true)).put("planned_minutes", RevierRules.integer(row, "planned_minutes", 1, 1440, -1)).put("notes", RevierRules.text(row, "notes", 10000, false)).put("timezone", "Europe/Vienna")
        if (((row.getInt("planned_minutes") < 1))) {
            throw RevierRules.error("INVALID_INPUT", "Geplante Dauer angeben.")
        }
        var start: String = RevierRules.time(row, "window_start")
        var end: String = RevierRules.time(row, "window_end")
        if ((((start.isEmpty() != end.isEmpty()) || (!start.isEmpty() && !LocalTime.parse(end).isAfter(LocalTime.parse(start)))))) {
            throw RevierRules.error("INVALID_INPUT", "Zeitfenster mit Beginn und späterem Ende angeben.")
        }
        row.put("window_start", start).put("window_end", end)
        var status: String = row.optString("status", "planned")
        if ((!Arrays.asList("planned", "in_progress", "completed", "partial", "cancelled").contains(status))) {
            throw RevierRules.error("INVALID_INPUT", "Ungültiger Besuchsstatus.")
        }
        row.put("status", status)
        var remaining: Int = RevierRules.integer(row, "remaining_minutes", 0, 1440, 0)
        if (((status.equals("partial") && (remaining < 1)))) {
            throw RevierRules.error("INVALID_INPUT", "Offenen Arbeitsrest angeben.")
        }
        row.put("remaining_minutes", remaining).put("material_needs", needs(row)).put("equipment_ids", equipmentIds(row))
    }
    @Throws(Exception::class)
    private fun overlaps(row: JSONObject): JSONArray {
        var warnings: JSONArray = JSONArray()
        var start: String = row.optString("window_start")
        var end: String = row.optString("window_end")
        if (((start.isEmpty() || !RevierRules.activeVisit(row)))) {
            return warnings
        }
        var visits: JSONArray = records("visit")
        run {
            var i: Int = 0
            while ((i < visits.length())) {
                var other: JSONObject = visits.getJSONObject(i)
                if (((((row.getString("id").equals(other.getString("id")) || !row.getString("date").equals(other.optString("date"))) || !RevierRules.activeVisit(other)) || other.optString("window_start").isEmpty()))) {
                    i++
                    continue
                }
                if ((((start.compareTo(other.getString("window_end")) < 0) && (end.compareTo(other.getString("window_start")) > 0)))) {
                    warnings.put(JSONObject().put("code", "LOCAL_VISIT_OVERLAP").put("visit_id", other.getString("id")))
                }
                i++
            }
        }
        return warnings
    }
    @Throws(Exception::class)
    private fun visit(operation: String, args: JSONObject, state: JSONObject, request: String): JSONObject {
        var creating: Boolean = operation.equals("plan_visit")
        var old: JSONObject? = (if (creating) null else load("visit", RevierRules.id(args, "visit_id", true)))
        var changes: JSONObject = (if (creating) args else args.getJSONObject("updates"))
        if (((old != null))) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject = base("visit", (if (creating) RevierRules.newId("visit", request) else old!!.getString("id")), old)
        if ((((!creating && !old!!.optString("care_plan_id").isEmpty()) && !args.optString("scope").equals("only_this")))) {
            throw RevierRules.error("SERIES_SCOPE_REQUIRED", "Für diesen Besuch ausdrücklich nur diesen Termin wählen.")
        }
        for (field in arrayOf("property_id", "order_id", "title", "date", "planned_minutes", "window_start", "window_end", "notes", "material_needs", "equipment_ids", "status", "remaining_minutes", "parent_visit_id")) {
            if ((changes.has(field))) {
                row.put(field, changes.get(field))
            }
        }
        if ((((old != null) && ((!old!!.getString("property_id").equals(row.optString("property_id")) || !old!!.optString("order_id").equals(row.optString("order_id"))))))) {
            throw RevierRules.error("RELATION_IMMUTABLE", "Besuch behält seinen ursprünglichen Objekt-/Auftragsbezug.")
        }
        var dateChanged: Boolean = ((old != null) && !old!!.getString("date").equals(row.optString("date")))
        var statusChanged: Boolean = ((old != null) && !old!!.getString("status").equals(row.optString("status")))
        var reason: String = RevierRules.text(args, "reason", 2000, false)
        if (((((dateChanged || statusChanged)) && reason.isEmpty()))) {
            throw RevierRules.error("CORRECTION_REASON_REQUIRED", "Änderung kurz begründen; ursprünglicher Besuch bleibt nachvollziehbar.")
        }
        if (((((old != null) && old!!.optString("status").equals("completed")) && (((((dateChanged || statusChanged) || changes.has("planned_minutes")) || changes.has("window_start")) || changes.has("window_end")))))) {
            throw RevierRules.error("COMPLETED_VISIT_IMMUTABLE", "Abgeschlossenen Besuch nicht rückwirkend umplanen. Offenen Rest getrennt planen.")
        }
        if (((((old != null) && Arrays.asList("cancelled", "superseded").contains(old!!.optString("status"))) && statusChanged))) {
            throw RevierRules.error("VISIT_NOT_REACTIVATABLE", "Abgesagten Besuch nicht still reaktivieren. Neuen Besuch planen.")
        }
        validateVisit(row, state)
        if (((old != null))) {
            row.put("manual_override", true)
        } else {
            row.put("manual_override", true)
        }
        var parent: String = RevierRules.id(row, "parent_visit_id", false)
        if ((!parent.isEmpty())) {
            var previous: JSONObject = load("visit", parent)
            if (((!previous.optString("status").equals("partial") || !previous.getString("property_id").equals(row.getString("property_id"))))) {
                throw RevierRules.error("RELATION_MISMATCH", "Restbesuch benötigt den passenden teilerledigten Vorgänger.")
            }
            var all: JSONArray = records("visit")
            run {
                var i: Int = 0
                while ((i < all.length())) {
                    var child: JSONObject = all.getJSONObject(i)
                    if ((((!child.getString("id").equals(row.getString("id")) && parent.equals(child.optString("parent_visit_id"))) && RevierRules.activeVisit(child)))) {
                        throw RevierRules.error("REMAINING_ALREADY_PLANNED", "Für diesen Arbeitsrest ist bereits ein aktiver Besuch geplant.")
                    }
                    i++
                }
            }
        }
        if ((!reason.isEmpty())) {
            row.put("status_reason", reason)
        }
        var saved: JSONObject = save("visit", row, old, request, reason)
        var effects: JSONArray = (if ((dateChanged || statusChanged)) markToursForVisit(row.getString("id"), request, reason) else JSONArray())
        return JSONObject().put("record", saved).put("visit", saved).put("visit_id", row.getString("id")).put("tour_effects", effects).put("overlap_warnings", overlaps(row)).put("equipment_warnings", equipmentWarnings(row.getJSONArray("equipment_ids"))).put("external_calendar_status", "unknown")
    }
    @Throws(Exception::class)
    private fun emptyTour(day: String): JSONObject {
        return JSONObject().put("format_version", 1).put("kind", "tour").put("id", ("tour_" + day)).put("date", day).put("visit_ids", JSONArray()).put("district", "").put("needs_review", false)
    }
    @Throws(Exception::class)
    private fun tour(day: String): JSONObject {
        var row: JSONObject? = find("tour", ("tour_" + day))
        if (((row == null))) {
            row = emptyTour(day)
        }
        var stops: JSONArray = JSONArray()
        var ids: JSONArray = row!!.getJSONArray("visit_ids")
        run {
            var i: Int = 0
            while ((i < ids.length())) {
                var visit: JSONObject = load("visit", ids.getString(i))
                var stop: JSONObject = RevierRules.view(visit)
                stop.put("position", i).put("tour_date_matches", day.equals(visit.getString("date"))).put("equipment_warnings", equipmentWarnings((if ((visit.optJSONArray("equipment_ids") == null)) JSONArray() else visit.getJSONArray("equipment_ids"))))
                stops.put(stop)
                i++
            }
        }
        var shown: JSONObject = RevierRules.view(row)
        return JSONObject().put("record", shown).put("tour", shown).put("stops", stops).put("record_hash", shown.getString("record_hash")).put("route_optimization_claimed", false)
    }
    @Throws(Exception::class)
    private fun markToursForVisit(visitId: String, request: String, reason: String): JSONArray {
        var changed: JSONArray = JSONArray()
        var tours: JSONArray = records("tour")
        run {
            var i: Int = 0
            while ((i < tours.length())) {
                var before: JSONObject = tours.getJSONObject(i)
                var ids: JSONArray = before.getJSONArray("visit_ids")
                run {
                    var j: Int = 0
                    while ((j < ids.length())) {
                        if ((ids.getString(j).equals(visitId))) {
                            var row: JSONObject = RevierRules.copy(before).put("needs_review", true).put("review_reason", reason)
                            save("tour", row, before, request, reason)
                            changed.put(JSONObject().put("tour_id", row.getString("id")).put("date", row.getString("date")).put("needs_review", true))
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
        var tourId: String = ("tour_" + day)
        var old: JSONObject? = find("tour", tourId)
        var row: JSONObject = (if ((old == null)) emptyTour(day) else RevierRules.copy(old))
        RevierRules.cas(row, args.optString("expected_record_hash"))
        var ids: JSONArray
        if ((operation.equals("set_day_tour"))) {
            ids = args.getJSONArray("visit_ids")
            if (((ids.length() > 100))) {
                throw RevierRules.error("INVALID_INPUT", "Eine Tagestour umfasst höchstens100Stopps.")
            }
            var selected: MutableSet<String> = HashSet()
            run {
                var i: Int = 0
                while ((i < ids.length())) {
                    var id: String = ids.getString(i)
                    var visit: JSONObject = load("visit", id)
                    if ((!selected.add(id))) {
                        throw RevierRules.error("DUPLICATE_VISIT", "Ein Besuch darf nur einmal in der aktiven Tour stehen.")
                    }
                    if (((!visit.getString("date").equals(day) || !RevierRules.activeVisit(visit)))) {
                        throw RevierRules.error("VISIT_NOT_PLANNABLE", "Nur aktive Besuche dieses Tages in die Tour übernehmen.")
                    }
                    i++
                }
            }
            var tours: JSONArray = records("tour")
            run {
                var i: Int = 0
                while ((i < tours.length())) {
                    var other: JSONObject = tours.getJSONObject(i)
                    if ((other.getString("id").equals(tourId))) {
                        i++
                        continue
                    }
                    var stops: JSONArray = other.getJSONArray("visit_ids")
                    run {
                        var j: Int = 0
                        while ((j < stops.length())) {
                            if (((selected.contains(stops.getString(j)) && RevierRules.activeVisit(load("visit", stops.getString(j)))))) {
                                throw RevierRules.error("VISIT_ALREADY_IN_TOUR", "Ein Besuch ist bereits einer anderen aktiven Tour zugeordnet. Alte Tour ausdrücklich prüfen.")
                            }
                            j++
                        }
                    }
                    i++
                }
            }
            row.put("district", RevierRules.text(args, "district", 200, false))
        } else {
            ids = JSONArray(row.getJSONArray("visit_ids").toString())
            var visit: String = RevierRules.id(args, "visit_id", true)
            var direction: String = RevierRules.text(args, "direction", 8, true)
            if ((!Arrays.asList("up", "down").contains(direction))) {
                throw RevierRules.error("INVALID_INPUT", "Nach oben oder nach unten wählen.")
            }
            var at: Int = -1
            run {
                var i: Int = 0
                while ((i < ids.length())) {
                    if ((ids.getString(i).equals(visit))) {
                        at = i
                    }
                    i++
                }
            }
            if (((at < 0))) {
                throw RevierRules.error("REFERENCE_NOT_FOUND", "Besuch gehört nicht zu dieser Tour.")
            }
            var next: Int = (if (direction.equals("up")) (at - 1) else (at + 1))
            if ((((next >= 0) && (next < ids.length())))) {
                var value: Any = ids.get(next)
                ids.put(next, visit)
                ids.put(at, value)
            }
        }
        row.put("visit_ids", ids).put("needs_review", false).put("review_reason", "")
        var saved: JSONObject = save("tour", row, old, request, (if (operation.equals("move_tour_stop")) "Tourreihenfolge geändert" else "Tagestour bestätigt"))
        return JSONObject().put("record", saved).put("tour", saved).put("route_optimization_claimed", false)
    }
    @Throws(Exception::class)
    private fun material(args: JSONObject, request: String): JSONObject {
        var id: String = RevierRules.id(args, "material_id", false)
        var old: JSONObject? = (if (id.isEmpty()) null else load("material", id))
        if (((old != null))) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject = base("material", (if ((old == null)) RevierRules.newId("material", request) else id), old)
        for (field in arrayOf("name", "unit", "purchase_price_minor", "billing_price_minor", "notes", "active")) {
            if ((args.has(field))) {
                row.put(field, args.get(field))
            }
        }
        row.put("name", RevierRules.text(row, "name", 240, true)).put("unit", RevierRules.text(row, "unit", 60, true)).put("notes", RevierRules.text(row, "notes", 10000, false)).put("active", RevierRules.bool(row, "active", true))
        if ((((old != null) && !old!!.getString("unit").equals(row.getString("unit"))))) {
            var history: Boolean = (BigDecimal(old!!.optString("stock_decimal", "0")).signum() != 0)
            var movements: JSONArray = records("material_movement")
            run {
                var i: Int = 0
                while ((i < movements.length())) {
                    history = history or old!!.getString("id").equals(movements.getJSONObject(i).optString("material_id"))
                    i++
                }
            }
            if ((history)) {
                throw RevierRules.error("MATERIAL_UNIT_CHANGE_REVIEW_REQUIRED", "Einheit bei vorhandener Bewegungshistorie nicht wechseln; neuen Katalogeintrag anlegen.")
            }
        }
        for (price in arrayOf("purchase_price_minor", "billing_price_minor")) {
            if (((row.has(price) && !row.isNull(price)))) {
                row.put(price, RevierRules.minor(row, price))
            }
        }
        if (((old == null))) {
            row.put("stock_decimal", "0")
        }
        var saved: JSONObject = save("material", row, old, request, "Materialkatalog gespeichert")
        return JSONObject().put("record", saved).put("material", saved).put("material_id", row.getString("id"))
    }
    @Throws(Exception::class)
    private fun linkContext(row: JSONObject, state: JSONObject, propertyRequired: Boolean) {
        var session: String = RevierRules.id(row, "session_id", false)
        if ((!session.isEmpty())) {
            var origin: JSONObject? = repository.session(session)
            if (((origin == null))) {
                throw RevierRules.error("REFERENCE_NOT_FOUND", "Arbeitssitzung wurde nicht gefunden.")
            }
            for (field in arrayOf("customer_id", "property_id", "visit_id", "order_id")) {
                var original: String = origin!!.optString(field)
                if ((original.isEmpty())) {
                    continue
                }
                var selected: String = row.optString(field)
                if (((!selected.isEmpty() && !selected.equals(original)))) {
                    throw RevierRules.error("RELATION_MISMATCH", "Arbeitssitzung und ausgewählter Einsatz passen nicht zusammen.")
                }
                row.put(field, original)
            }
        }
        var property: String = RevierRules.id(row, "property_id", false)
        var visit: String = RevierRules.id(row, "visit_id", false)
        if ((!visit.isEmpty())) {
            var source: JSONObject = load("visit", visit)
            if (((!property.isEmpty() && !property.equals(source.getString("property_id"))))) {
                throw RevierRules.error("RELATION_MISMATCH", "Einsatz und Objekt passen nicht zusammen.")
            }
            property = source.getString("property_id")
            if (((!row.optString("customer_id").isEmpty() && !row.getString("customer_id").equals(source.getString("customer_id"))))) {
                throw RevierRules.error("RELATION_MISMATCH", "Arbeitssitzung und Einsatzkunde passen nicht zusammen.")
            }
            if ((((!row.optString("order_id").isEmpty() && !source.optString("order_id").isEmpty()) && !row.getString("order_id").equals(source.getString("order_id"))))) {
                throw RevierRules.error("RELATION_MISMATCH", "Arbeitssitzung und Auftrag passen nicht zusammen.")
            }
            var order: String = source.optString("order_id")
            if ((!order.isEmpty())) {
                var assigned: JSONObject = canonical(state, "orders", order)
                if ((!source.getString("customer_id").equals(assigned.optString("customer_id")))) {
                    throw RevierRules.error("RELATION_MISMATCH", "Einsatz und zugeordneter Auftrag passen nicht zusammen.")
                }
                row.put("order_id", order)
            }
            row.put("visit_id", visit)
        }
        if (((propertyRequired && property.isEmpty()))) {
            throw RevierRules.error("PROPERTY_REQUIRED", "Für den geführten Einsatz ein Objekt oder einen zugeordneten Besuch wählen.")
        }
        if ((!property.isEmpty())) {
            var `object`: JSONObject = load("property", property)
            customer(state, `object`.getString("customer_id"), false)
            if (((!row.optString("customer_id").isEmpty() && !row.getString("customer_id").equals(`object`.getString("customer_id"))))) {
                throw RevierRules.error("RELATION_MISMATCH", "Arbeitssitzung und Objektkunde passen nicht zusammen.")
            }
            row.put("property_id", property).put("customer_id", `object`.getString("customer_id"))
        }
    }
    @Throws(Exception::class)
    private fun movement(args: JSONObject, state: JSONObject, request: String): JSONObject {
        var old: JSONObject = load("material", RevierRules.id(args, "material_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        var kind: String = RevierRules.text(args, "kind", 20, true)
        var reason: String = RevierRules.text(args, "reason", 2000, true)
        if ((!Arrays.asList("receive", "consume", "adjust", "reversal").contains(kind))) {
            throw RevierRules.error("INVALID_INPUT", "Ungültige Materialbewegung.")
        }
        var movementId: String = RevierRules.newId("movement", request)
        var row: JSONObject = base("material_movement", movementId, null).put("material_id", old.getString("id")).put("movement_kind", kind).put("reason", reason).put("material_snapshot", RevierRules.copy(old))
        for (field in arrayOf("property_id", "visit_id", "session_id")) {
            if ((args.has(field))) {
                row.put(field, args.get(field))
            }
        }
        var delta: BigDecimal
        if ((kind.equals("reversal"))) {
            var original: String = RevierRules.id(args, "reverses_movement_id", true)
            var prior: JSONObject = load("material_movement", original)
            if ((!old.getString("id").equals(prior.getString("material_id")))) {
                throw RevierRules.error("RELATION_MISMATCH", "Gegenbuchung gehört zu einem anderen Material.")
            }
            if ((!repository.get((("" + RevierRules.ROOT + "reversed:") + original), "").isEmpty())) {
                throw RevierRules.error("MOVEMENT_ALREADY_REVERSED", "Diese Materialbewegung hat bereits eine Gegenbuchung.")
            }
            if ((prior.getString("movement_kind").equals("reversal"))) {
                throw RevierRules.error("INVALID_INPUT", "Gegenbuchungen nicht unsichtbar rückgängig machen; begründete Anpassung verwenden.")
            }
            delta = BigDecimal(prior.getString("quantity_decimal")).negate()
            if (((args.has("quantity_decimal") && (BigDecimal(RevierRules.decimal(args, "quantity_decimal", false, false)).compareTo(delta) != 0)))) {
                throw RevierRules.error("REVERSAL_QUANTITY_MISMATCH", "Gegenbuchung muss genau die ursprüngliche Menge ausgleichen.")
            }
            row.put("reverses_movement_id", original)
            for (field in arrayOf("property_id", "visit_id", "session_id", "customer_id")) {
                if ((prior.has(field))) {
                    row.put(field, prior.get(field))
                }
            }
        } else {
            delta = BigDecimal(RevierRules.decimal(args, "quantity_decimal", false, false))
            if (((delta.signum() == 0))) {
                throw RevierRules.error("INVALID_INPUT", "Bewegungsmenge muss von null abweichen.")
            }
            if (((kind.equals("receive") && (delta.signum() < 0)))) {
                throw RevierRules.error("INVALID_INPUT", "Zugang als positive Menge erfassen.")
            }
            if ((kind.equals("consume"))) {
                delta = delta.abs().negate()
            }
        }
        linkContext(row, state, kind.equals("consume"))
        var stock: BigDecimal = BigDecimal(old.optString("stock_decimal", "0")).add(delta)
        if (((stock.signum() < 0))) {
            throw RevierRules.error("INSUFFICIENT_STOCK", "Bestand reicht nicht aus. Tatsächlichen Zugang oder begründete Korrektur erfassen.")
        }
        row.put("quantity_decimal", delta.stripTrailingZeros().toPlainString()).put("unit", old.getString("unit"))
        var changed: JSONObject = RevierRules.copy(old).put("stock_decimal", stock.stripTrailingZeros().toPlainString())
        var saved: JSONObject = save("material_movement", row, null, request, reason)
        save("material", changed, old, request, reason)
        if ((kind.equals("reversal"))) {
            repository.put((("" + RevierRules.ROOT + "reversed:") + row.getString("reverses_movement_id")), movementId)
        }
        return JSONObject().put("record", saved).put("movement", saved).put("material", RevierRules.view(changed)).put("stock_decimal", changed.getString("stock_decimal"))
    }
    @Throws(Exception::class)
    private fun equipment(args: JSONObject, request: String): JSONObject {
        var id: String = RevierRules.id(args, "equipment_id", false)
        var old: JSONObject? = (if (id.isEmpty()) null else load("equipment", id))
        if (((old != null))) {
            RevierRules.cas(old, args.optString("expected_record_hash"))
        }
        var row: JSONObject = base("equipment", (if ((old == null)) RevierRules.newId("equipment", request) else id), old)
        for (field in arrayOf("name", "condition", "maintenance_due", "notes", "active")) {
            if ((args.has(field))) {
                row.put(field, args.get(field))
            }
        }
        row.put("name", RevierRules.text(row, "name", 240, true)).put("notes", RevierRules.text(row, "notes", 10000, false)).put("active", RevierRules.bool(row, "active", true)).put("maintenance_due", RevierRules.date(row, "maintenance_due", false))
        var condition: String = row.optString("condition", "ready")
        if ((!Arrays.asList("ready", "maintenance", "defective").contains(condition))) {
            throw RevierRules.error("INVALID_INPUT", "Gerätezustand bewusst wählen.")
        }
        row.put("condition", condition)
        var upload: String = RevierRules.id(args, "profile_photo_upload_id", false)
        var draft: JSONObject? = args.optJSONObject("profile_photo_draft")
        if ((((args.has("profile_photo_draft") && (draft == null)) || (!upload.isEmpty() && (draft != null))))) {
            throw RevierRules.error("INVALID_INPUT", "Genau eine lokale Fotoquelle verwenden.")
        }
        if (((!upload.isEmpty() || (draft != null)))) {
            if ((((old != null) && ((old!!.optBoolean("archived") || !old!!.optBoolean("active", true)))))) {
                throw RevierRules.error("EQUIPMENT_INACTIVE", "Archiviertes oder inaktives Gerät zuerst bewusst prüfen.")
            }
            var photo: JSONObject = (if ((draft == null)) repository.prepareEquipmentProfilePhoto(upload, row.getString("id"), request) else repository.prepareEquipmentProfilePhotoDraft(draft, row.getString("id"), request))
            EquipmentProfilePhotoRules.validate(photo, row.getString("id"))
            repository.verifyEquipmentProfilePhoto(photo)
            row.put("profile_photo", photo).put("format_version", 3)
        }
        if (args.has("accessories") || args.has("current_chain_id")) {
            val previous = old?.optJSONArray("accessories") ?: JSONArray()
            val entries = if (args.has("accessories")) args.getJSONArray("accessories") else previous
            val accessories = JSONArray()
            val identities = HashSet<String>()
            for (index in 0 until entries.length()) {
                val input = entries.getJSONObject(index)
                val accessoryId = RevierRules.id(input, "id", true)
                if (!identities.add(accessoryId)) throw RevierRules.error("INVALID_INPUT", "Zubehörkennungen müssen innerhalb der Maschine eindeutig sein.")
                val before = (0 until previous.length()).map { previous.getJSONObject(it) }.firstOrNull { it.optString("id") == accessoryId }
                val item = JSONObject().put("id", accessoryId).put("equipment_id", row.getString("id"))
                val type = input.optString("type", "other")
                if (type !in listOf("chain", "other")) throw RevierRules.error("INVALID_INPUT", "Zubehörart auswählen.")
                item.put("type", type)
                for (field in listOf("name", "manufacturer", "model", "pitch", "gauge", "drive_links", "article_number", "tooth_type", "file_size", "notes")) {
                    if (input.has(field)) item.put(field, RevierRules.text(input, field, if (field == "notes") 10000 else 240, false))
                }
                if (listOf("name", "manufacturer", "model").all { item.optString(it).isBlank() }) throw RevierRules.error("INVALID_INPUT", "Zubehör mit Bezeichnung oder Hersteller/Modell erfassen.")
                before?.optJSONObject("photo")?.let { item.put("photo", RevierRules.copy(it)) }
                val uploadId = RevierRules.id(input, "photo_upload_id", false)
                if (uploadId.isNotEmpty()) {
                    if (row.optBoolean("archived") || !row.optBoolean("active", true)) throw RevierRules.error("EQUIPMENT_INACTIVE", "Archiviertes oder inaktives Gerät zuerst bewusst prüfen.")
                    val photo = repository.prepareEquipmentProfilePhoto(uploadId, row.getString("id"), request + "_" + PixelMcpWriteTools.hash(accessoryId).take(16))
                    EquipmentProfilePhotoRules.validate(photo, row.getString("id")); repository.verifyEquipmentProfilePhoto(photo)
                    item.put("photo", photo)
                }
                accessories.put(item)
            }
            val current = args.optString("current_chain_id", row.optString("current_chain_id"))
            if (current.isNotEmpty() && (0 until accessories.length()).none { accessories.getJSONObject(it).optString("id") == current && accessories.getJSONObject(it).optString("type") == "chain" }) {
                throw RevierRules.error("INVALID_INPUT", "Die aktuelle Kette muss dieser Maschine zugeordnet sein.")
            }
            row.put("accessories", accessories).put("current_chain_id", current)
        }
        var saved: JSONObject = save("equipment", row, old, request, "Gerätezustand gespeichert")
        return JSONObject().put("record", saved).put("equipment", saved).put("equipment_id", row.getString("id"))
    }
    @Throws(Exception::class)
    private fun usage(args: JSONObject, state: JSONObject, request: String): JSONObject {
        var device: JSONObject = load("equipment", RevierRules.id(args, "equipment_id", true))
        RevierRules.cas(device, args.optString("expected_record_hash"))
        var row: JSONObject = base("equipment_usage", RevierRules.newId("usage", request), null).put("equipment_id", device.getString("id")).put("equipment_snapshot", device).put("notes", RevierRules.text(args, "notes", 10000, false))
        for (field in arrayOf("property_id", "visit_id", "session_id")) {
            if ((args.has(field))) {
                row.put(field, args.get(field))
            }
        }
        linkContext(row, state, true)
        var start: String = RevierRules.text(args, "starts_at", 80, false)
        var end: String = RevierRules.text(args, "ends_at", 80, false)
        if (((start.isEmpty() != end.isEmpty()))) {
            throw RevierRules.error("INVALID_INPUT", "Gerätenutzung mit beiden Zeitpunkten oder ohne Zeitangabe erfassen.")
        }
        if ((!start.isEmpty())) {
            start = RevierRules.instant(start)
            end = RevierRules.instant(end)
            if ((!Instant.parse(end).isAfter(Instant.parse(start)))) {
                throw RevierRules.error("INVALID_INPUT", "Gerätenutzung benötigt ein positives Zeitintervall.")
            }
            row.put("starts_at", start).put("ends_at", end)
        }
        var saved: JSONObject = save("equipment_usage", row, null, request, "Gerätenutzung erfasst")
        return JSONObject().put("record", saved).put("equipment_usage", saved).put("equipment_warnings", equipmentWarnings(JSONArray().put(device.getString("id"))))
    }
    @Throws(Exception::class)
    private fun maintenanceDevice(args: JSONObject): JSONObject {
        var old: JSONObject = load("equipment", RevierRules.id(args, "equipment_id", true))
        RevierRules.cas(old, args.optString("expected_record_hash"))
        if (((old.optBoolean("archived") || !old.optBoolean("active", true)))) {
            throw RevierRules.error("EQUIPMENT_INACTIVE", "Archiviertes oder inaktives Gerät zuerst bewusst prüfen.")
        }
        EquipmentMaintenanceRules.plans(old)
        return old
    }
    @Throws(Exception::class)
    private fun equipmentMaintenancePlan(args: JSONObject, request: String): JSONObject {
        var old: JSONObject = maintenanceDevice(args)
        var row: JSONObject = RevierRules.copy(old)
        var plans: JSONArray = EquipmentMaintenanceRules.plans(row)
        var planId: String = RevierRules.id(args, "plan_id", false)
        var operation: String = RevierRules.text(args, "operation", 30, true)
        var reason: String = RevierRules.text(args, "reason", 2000, false)
        var plan: JSONObject
        if ((planId.isEmpty())) {
            if ((!operation.equals("configure"))) {
                throw RevierRules.error("INVALID_INPUT", "Zuerst einen Wartungsplan anlegen.")
            }
            if ((args.has("next_due_on"))) {
                throw RevierRules.error("INVALID_INPUT", "Erstes Intervall mit Beginn und Länge festlegen; Einzeltermine anschließend bewusst vorziehen.")
            }
            if (((plans.length() >= EquipmentMaintenanceRules.MAX_PLANS))) {
                throw RevierRules.error("INVALID_INPUT", "An diesem Gerät sind bereits zu viele Wartungspläne vorhanden.")
            }
            plan = JSONObject().put("id", RevierRules.newId("maintenance_plan", request)).put("title", RevierRules.text(args, "title", 240, true)).put("interval_value", RevierRules.integer(args, "interval_value", 1, 3650, -1)).put("interval_unit", RevierRules.text(args, "interval_unit", 12, true)).put("anchor_on", RevierRules.date(args, "starts_on", true)).put("created_at", stamp())
            EquipmentMaintenanceRules.recalculate(plan)
            plans.put(plan)
        } else {
            plan = EquipmentMaintenanceRules.find(plans, planId)
            if ((reason.isEmpty())) {
                throw RevierRules.error("INVALID_INPUT", "Grund für die Planänderung angeben.")
            }
            if (((!operation.equals("configure") && args.has("title")))) {
                throw RevierRules.error("INVALID_INPUT", "Eine Terminvorziehung ändert nicht die Tätigkeit.")
            }
            if ((args.has("starts_on"))) {
                throw RevierRules.error("INVALID_INPUT", "Eine Planänderung setzt vergangene Wartungen nicht zurück.")
            }
            if ((operation.equals("bring_forward"))) {
                if (((args.has("interval_value") || args.has("interval_unit")))) {
                    throw RevierRules.error("INVALID_INPUT", "Einmaliges Vorziehen verändert das Intervall nicht.")
                }
                var due: String = RevierRules.date(args, "next_due_on", true)
                if ((((due.compareTo(today().toString()) < 0) || (due.compareTo(plan.getString("next_due_on")) >= 0)))) {
                    throw RevierRules.error("INVALID_INPUT", "Einen früheren Termin ab heute wählen.")
                }
                plan.put("one_off_due_on", due)
                EquipmentMaintenanceRules.recalculate(plan)
            } else {
                if ((operation.equals("shorten"))) {
                    if ((args.has("next_due_on"))) {
                        throw RevierRules.error("INVALID_INPUT", "Für ein kürzeres Intervall Länge und Einheit wählen.")
                    }
                    EquipmentMaintenanceRules.shorten(plan, RevierRules.copy(args), reason)
                } else {
                    if ((operation.equals("configure"))) {
                        if ((args.has("next_due_on"))) {
                            throw RevierRules.error("INVALID_INPUT", "Einzeltermin über Früher warten ändern.")
                        }
                        if ((args.has("title"))) {
                            plan.put("title", RevierRules.text(args, "title", 240, true))
                        }
                        plan.put("interval_value", RevierRules.integer(args, "interval_value", 1, 3650, -1)).put("interval_unit", RevierRules.text(args, "interval_unit", 12, true))
                        EquipmentMaintenanceRules.recalculate(plan)
                    } else {
                        throw RevierRules.error("INVALID_INPUT", "Unbekannte Wartungsplanänderung.")
                    }
                }
            }
            plan.put("last_change_reason", reason)
        }
        plan.put("updated_at", stamp())
        row.put("format_version", Math.max(2, row.optInt("format_version", 1))).put("maintenance_plans", plans)
        EquipmentMaintenanceRules.plans(row)
        var saved: JSONObject = save("equipment", row, old, request, (if (reason.isEmpty()) "Eigener Wartungsplan angelegt" else reason))
        return JSONObject().put("record", saved).put("equipment", saved).put("plan", RevierRules.copy(plan)).put("equipment_id", row.getString("id"))
    }
    @Throws(Exception::class)
    private fun equipmentMaintenanceDone(args: JSONObject, request: String): JSONObject {
        var old: JSONObject = maintenanceDevice(args)
        var row: JSONObject = RevierRules.copy(old)
        var plans: JSONArray = EquipmentMaintenanceRules.plans(row)
        var plan: JSONObject = EquipmentMaintenanceRules.find(plans, RevierRules.id(args, "plan_id", true))
        var before: JSONObject = RevierRules.copy(plan)
        var performed: String = RevierRules.date(args, "performed_on", true)
        var reason: String = RevierRules.text(args, "reason", 2000, false)
        var notes: String = RevierRules.text(args, "notes", 4000, false)
        if (((performed.compareTo(today().toString()) > 0))) {
            throw RevierRules.error("INVALID_INPUT", "Eine erledigte Wartung darf nicht in der Zukunft liegen.")
        }
        var shorter: Boolean = (args.has("shorter_interval_value") || args.has("shorter_interval_unit"))
        var newest: Boolean = (performed.compareTo(plan.getString("anchor_on")) >= 0)
        if (((shorter && !newest))) {
            throw RevierRules.error("INVALID_INPUT", "Ein älterer Nachtrag verändert das aktuelle Intervall nicht. Plan separat prüfen.")
        }
        var eventId: String = RevierRules.newId("equipment_maintenance", request)
        if ((newest)) {
            plan.put("anchor_on", performed).put("last_completed_on", performed).put("last_event_id", eventId).remove("one_off_due_on")
            if ((shorter)) {
                var interval: JSONObject = JSONObject().put("interval_value", RevierRules.integer(args, "shorter_interval_value", 1, 3650, -1)).put("interval_unit", RevierRules.text(args, "shorter_interval_unit", 12, true))
                EquipmentMaintenanceRules.shorten(plan, interval, reason)
                plan.put("last_change_reason", reason)
            }
            EquipmentMaintenanceRules.recalculate(plan)
            plan.put("updated_at", stamp())
        }
        var event: JSONObject = base("equipment_maintenance", eventId, null).put("format_version", 2).put("equipment_id", row.getString("id")).put("plan_id", plan.getString("id")).put("title", plan.getString("title")).put("performed_on", performed).put("date", performed).put("reason", reason).put("notes", notes).put("schedule_applied", newest).put("plan_before", before).put("plan_after", RevierRules.copy(plan))
        var savedEvent: JSONObject = save("equipment_maintenance", event, null, request, (if (reason.isEmpty()) "Wartung ausdrücklich als erledigt erfasst" else reason))
        row.put("maintenance_plans", plans)
        EquipmentMaintenanceRules.plans(row)
        var saved: JSONObject = save("equipment", row, old, request, ("Wartung erfasst" + ((if (reason.isEmpty()) "" else (": " + reason)))))
        return JSONObject().put("record", saved).put("equipment", saved).put("maintenance", savedEvent).put("equipment_id", row.getString("id")).put("event_id", eventId)
    }
    @Throws(Exception::class)
    private fun equipmentMaintenanceHistory(args: JSONObject): JSONObject {
        var device: JSONObject = load("equipment", RevierRules.id(args, "equipment_id", true))
        EquipmentMaintenanceRules.plans(device)
        var events: MutableList<JSONObject> = ArrayList()
        var all: JSONArray = records("equipment_maintenance")
        run {
            var i: Int = 0
            while ((i < all.length())) {
                if ((device.getString("id").equals(all.getJSONObject(i).optString("equipment_id")))) {
                    events.add(all.getJSONObject(i))
                }
                i++
            }
        }
        events.sortWith(Comparator.comparing(lambda4@ { e: JSONObject -> e.optString("performed_on") }).reversed().thenComparing(lambda5@ { e -> e.optString("created_at") }, Comparator.reverseOrder()).thenComparing(lambda6@ { e -> e.optString("id") }))
        var offset: Int = RevierRules.integer(args, "offset", 0, 1000000, 0)
        var limit: Int = RevierRules.integer(args, "limit", 1, 100, 20)
        var page: JSONArray = JSONArray()
        run {
            var i: Int = offset
            while (((i < events.size) && (page.length() < limit))) {
                page.put(RevierRules.view(events.get(i)))
                i++
            }
        }
        var record: JSONObject = RevierRules.view(device)
        return JSONObject().put("record", record).put("equipment", record).put("plans", EquipmentMaintenanceRules.plans(device)).put("items", page).put("maintenance", page).put("total", events.size).put("offset", offset).put("limit", limit).put("next_due_on", EquipmentMaintenanceRules.nextDue(device)).put("local_only", true)
    }
    private class SearchHit {
        val record: JSONObject?
        val type: String
        val targetType: String
        val id: String
        val title: String
        val date: String
        val notes: String?
        val status: String
        val customerId: String
        val propertyId: String
        val archived: Boolean
        constructor(row: JSONObject, targetType: String, id: String, title: String, date: String, archived: Boolean, notes: String?) {
            this.record = (if ((notes == null)) row else null)
            this.type = (if ((notes == null)) targetType else "note")
            this.targetType = targetType
            this.id = id
            this.title = title
            this.date = date
            this.archived = archived
            this.notes = notes
            this.status = (if ((notes == null)) row.optString("status") else "")
            this.customerId = (if ((notes == null)) row.optString("customer_id") else "")
            this.propertyId = (if ((notes == null)) row.optString("property_id") else "")
        }
        @Throws(Exception::class)
        fun selected(): JSONObject {
            var result: JSONObject = JSONObject().put("type", type).put("id", id).put("title", title).put("label", title)
            var target: JSONObject = JSONObject().put("type", targetType).put("id", id)
            if (((record != null))) {
                result.put("status", status)
                target.put("customer_id", customerId).put("property_id", propertyId)
            } else {
                result.put("snippet", notes!!.substring(0, Math.min(160, notes!!.length)))
            }
            result.put("date", calendarDay(date)).put("archived", archived).put("target", target)
            if (((record != null))) {
                result.put("record_hash", RevierRules.hash(record))
            }
            return result
        }
    }
    @Throws(Exception::class)
    private fun search(args: JSONObject, state: JSONObject): JSONObject {
        var query: String = RevierRules.text(args, "query", 200, true)
        var words: Array<String> = normalize(query).split(Regex("\\s+")).toTypedArray()
        var types: MutableSet<String> = HashSet()
        var selected: JSONArray? = args.optJSONArray("types")
        if (((selected != null))) {
            run {
                var i: Int = 0
                while ((i < selected!!.length())) {
                    types.add(selected!!.getString(i))
                    i++
                }
            }
        }
        var found: MutableList<SearchHit> = ArrayList()
        var unique: MutableSet<String> = HashSet()
        for (group in arrayOf(arrayOf("customers", "customer"), arrayOf("orders", "order"), arrayOf("appointments", "appointment"), arrayOf("worklogs", "activity"), arrayOf("offers", "offer"), arrayOf("invoices", "invoice"))) {
            var rows: JSONArray? = state.optJSONArray(group[0])
            if (((rows == null))) {
                continue
            }
            run {
                var i: Int = 0
                while ((i < rows!!.length())) {
                    searchRow(rows!!.getJSONObject(i), group[1], false, args, words, types, unique, found)
                    i++
                }
            }
        }
        for (group in arrayOf(arrayOf("property", "property"), arrayOf("inquiry", "inquiry"), arrayOf("care_plan", "care_plan"), arrayOf("visit", "visit"), arrayOf("material", "material"), arrayOf("equipment", "equipment"))) {
            var rows: JSONArray? = records(group[0])
            run {
                var i: Int = 0
                while ((i < rows!!.length())) {
                    searchRow(rows!!.getJSONObject(i), group[1], false, args, words, types, unique, found)
                    i++
                }
            }
        }
        if ((args.optBoolean("include_archived", false))) {
            var archive: JSONObject? = state.optJSONObject("owner_archive")
            if (((archive != null))) {
                run {
                    var it: MutableIterator<String> = archive!!.keys()
                    while (it.hasNext()) {
                        var key: String = it.next()
                        var entry: JSONObject = archive!!.getJSONObject(key)
                        if (((entry.optBoolean("purged") || (entry.optJSONObject("record") == null)))) {
                            continue
                        }
                        var type: String = (if (key.contains(":")) key.substring(0, key.indexOf(':')) else "")
                        if ((type.equals("worklog"))) {
                            type = "activity"
                        }
                        searchRow(entry.getJSONObject("record"), type, true, args, words, types, unique, found)
                    }
                }
            }
        }
        found.sortWith(Comparator.comparing(lambda7@ { hit: SearchHit -> hit.title }, String.CASE_INSENSITIVE_ORDER).thenComparing(lambda8@ { hit -> hit.type }).thenComparing(lambda9@ { hit -> hit.id }))
        var skip: Int = RevierRules.integer(args, "offset", 0, 1000000, 0)
        var limit: Int = RevierRules.integer(args, "limit", 1, 100, 50)
        var items: JSONArray = JSONArray()
        run {
            var i: Int = skip
            while (((i < found.size) && (items.length() < limit))) {
                items.put(found.get(i).selected())
                i++
            }
        }
        return JSONObject().put("items", items).put("results", items).put("total", found.size).put("offset", skip).put("limit", limit).put("index_scope", "all_local_records").put("index_rebuild", "read_from_canonical_state")
    }
    @Throws(Exception::class)
    private fun searchRow(row: JSONObject, type: String, archived: Boolean, filters: JSONObject, words: Array<String>, types: MutableSet<String>, unique: MutableSet<String>, found: MutableList<SearchHit>) {
        if (((!filters.optBoolean("include_archived", false) && (((archived || row.optBoolean("archived")) || (row.has("active") && (((row.optInt("active", 1) == 0) || false.equals(row.opt("active")))))))))) {
            return
        }
        if (((!filters.optString("status").isEmpty() && !filters.getString("status").equals(row.optString("status"))))) {
            return
        }
        var day: String = row.optString("date", row.optString("work_date", row.optString("starts_at", row.optString("issued_at", row.optString("received_at", row.optString("updated_at"))))))
        if (((!filters.optString("from_date").isEmpty() || !filters.optString("to_date").isEmpty()))) {
            day = calendarDay(day)
        }
        if ((((!filters.optString("from_date").isEmpty() && ((day.isEmpty() || (day.compareTo(filters.getString("from_date")) < 0)))) || (!filters.optString("to_date").isEmpty() && ((day.isEmpty() || (day.compareTo(filters.getString("to_date")) > 0))))))) {
            return
        }
        var targetId: String = row.optString("id")
        if ((targetId.isEmpty())) {
            return
        }
        var title: String = row.optString("name", row.optString("title", row.optString("activity", row.optString("invoice_number", row.optString("number", targetId)))))
        var body: String = (if ((types.isEmpty() || types.contains(type))) normalize(row.toString()) else "")
        var matches: Boolean = true
        for (word in words) {
            matches = matches and body.contains(word)
        }
        if ((((matches && ((types.isEmpty() || types.contains(type)))) && unique.add(((("" + type + ":") + targetId) + ((if (archived) ":archive" else ""))))))) {
            found.add(SearchHit(row, type, targetId, title, day, archived, null))
        }
        var notes: String = row.optString("notes", row.optString("note", row.optString("concern")))
        if (((!notes.isEmpty() && ((types.isEmpty() || types.contains("note")))))) {
            var noteMatches: Boolean = true
            var value: String = normalize(notes)
            for (word in words) {
                noteMatches = noteMatches and value.contains(word)
            }
            if (((noteMatches && unique.add(((("" + ("note:" + type) + ":") + targetId) + ((if (archived) ":archive" else ""))))))) {
                found.add(SearchHit(row, type, targetId, title, day, archived, notes))
            }
        }
    }
    @Throws(Exception::class)
    private fun overview(args: JSONObject, state: JSONObject): JSONObject {
        var day: String = args.optString("date", today().toString())
        RevierRules.date(day)
        var visits: JSONArray = records("visit")
        var planned: JSONArray = JSONArray()
        var due: JSONArray = JSONArray()
        run {
            var i: Int = 0
            while ((i < visits.length())) {
                var row: JSONObject = visits.getJSONObject(i)
                if ((!RevierRules.activeVisit(row))) {
                    i++
                    continue
                }
                if ((row.getString("date").equals(day))) {
                    planned.put(RevierRules.view(row))
                }
                if (((row.getString("date").compareTo(day) <= 0))) {
                    due.put(RevierRules.view(row))
                }
                i++
            }
        }
        var inquiries: JSONArray = records("inquiry")
        var open: Int = 0
        run {
            var i: Int = 0
            while ((i < inquiries.length())) {
                if ((!Arrays.asList("accepted", "declined", "closed").contains(inquiries.getJSONObject(i).optString("status")))) {
                    open++
                }
                i++
            }
        }
        var customerCount: Int = (if ((state.optJSONArray("customers") == null)) 0 else state.getJSONArray("customers").length())
        return JSONObject().put("date", day).put("timezone", "Europe/Vienna").put("visits", planned).put("planned_visits", planned).put("due_visits", due).put("tour", tour(day)).put("properties_count", records("property").length()).put("customers_count", customerCount).put("open_inquiries", open).put("work_session", repository.currentSession()).put("external_calendar_status", "unknown").put("local_only", true)
    }
    @Throws(Exception::class)
    fun detachOptionalParent(entity: String, id: String, request: String) {
        if (entity != "order" && entity != "appointment") return
        for (type in arrayOf("visit", "inquiry")) {
            val field = if (entity == "order") "order_id" else "inspection_appointment_id"
            val rows = records(type)
            for (i in 0 until rows.length()) {
                val old = rows.getJSONObject(i)
                val row = RevierRules.copy(old)
                var changed = false
                if (row.optString(field) == id) { row.put(field, ""); changed = true }
                if (entity == "appointment") {
                    val history = row.optJSONArray("inspection_visits") ?: JSONArray()
                    for (j in 0 until history.length()) {
                        val inspection = history.getJSONObject(j)
                        if (inspection.optString("appointment_id") == id) {
                            inspection.put("appointment_id", ""); changed = true
                        }
                    }
                }
                if (!changed) continue
                save(type, row, old, request,
                    "Optionale Zuordnung nach Entfernen des Auftrags/Termins gelöst; Kundenbezug erhalten.")
            }
        }
    }
    @Throws(Exception::class)
    fun protectParent(entity: String, id: String) {
        if ((entity.equals("offer"))) {
            var rows: JSONArray = repository.rawEntries(OfferDecisionRules.PREFIX)
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    var entry: JSONObject = rows.getJSONObject(i)
                    var key: String = entry.getString("key")
                    if (((!key.startsWith(("" + OfferDecisionRules.PREFIX + "current:")) && !key.startsWith(("" + OfferDecisionRules.PREFIX + "revision:"))))) {
                        i++
                        continue
                    }
                    var value: JSONObject = JSONObject(entry.getString("value"))
                    if (((id.equals(value.optString("offer_id")) || id.equals(value.optString("parent_offer_id"))))) {
                        throw RevierRules.error("REVIER_REFERENCE_PROTECTED", "Angebot gehört zu einer unveränderlichen Entscheidung oder Versionshistorie. Nachweise erhalten; keine verwaiste Angebotsfassung erzeugen.")
                    }
                    i++
                }
            }
        }
        if ((entity.equals("customer"))) {
            for (type in arrayOf("property", "inquiry", "visit")) {
                var records: JSONArray = records(type)
                run {
                    var i: Int = 0
                    while ((i < records.length())) {
                        if ((id.equals(records.getJSONObject(i).optString("customer_id")))) {
                            throw RevierRules.error("REVIER_REFERENCE_PROTECTED", "Kunde ist mit Objekt-/Anfrage-/Besuchshistorie verknüpft. Bezogene Einträge zuerst bewusst prüfen; keine verwaisten Bezüge erzeugen.")
                        }
                        i++
                    }
                }
            }
        }
        if (entity == "offer") {
            var field: String = (if (entity.equals("appointment")) "inspection_appointment_id" else ("" + entity + "_id"))
            var rows: JSONArray = records("inquiry")
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    if ((id.equals(rows.getJSONObject(i).optString(field)))) {
                        throw RevierRules.error("REVIER_REFERENCE_PROTECTED", "Eintrag gehört zum dokumentierten Anfrageverlauf. Diese Verknüpfung zuerst ausdrücklich prüfen.")
                    }
                    i++
                }
            }
        }
        if ((entity.equals("photo"))) {
            var rows: JSONArray = records("inquiry")
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    var photos: JSONArray? = rows.getJSONObject(i).optJSONArray("photo_ids")
                    run {
                        var j: Int = 0
                        while (((photos != null) && (j < photos!!.length()))) {
                            if ((id.equals(photos!!.getString(j)))) {
                                throw RevierRules.error("REVIER_REFERENCE_PROTECTED", "Foto gehört zu einer Besichtigungsdokumentation. Diese Zuordnung zuerst ausdrücklich prüfen; Original bleibt erhalten.")
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
        var rows: JSONArray = records("inquiry")
        run {
            var i: Int = 0
            while ((i < rows.length())) {
                var inquiry: JSONObject = rows.getJSONObject(i)
                if ((customerId.equals(inquiry.optString("customer_id")))) {
                    i++
                    continue
                }
                var photos: JSONArray? = inquiry.optJSONArray("photo_ids")
                run {
                    var j: Int = 0
                    while (((photos != null) && (j < photos!!.length()))) {
                        if ((photoId.equals(photos!!.getString(j)))) {
                            throw RevierRules.error("REVIER_REFERENCE_PROTECTED", "Foto ist mit einer Besichtigung eines anderen Kunden verknüpft. Zuordnung zuerst ausdrücklich klären.")
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
        var rows: JSONArray = records("equipment")
        run {
            var i: Int = 0
            while ((i < rows.length())) {
                var row: JSONObject = rows.getJSONObject(i)
                var photo: JSONObject? = row.optJSONObject("profile_photo")
                if ((((photo != null) && photoId.equals(photo!!.optString("id"))))) {
                    EquipmentProfilePhotoRules.validate(photo, row.getString("id"))
                    return RevierRules.copy(photo)
                }
                val accessories = row.optJSONArray("accessories") ?: JSONArray()
                for (index in 0 until accessories.length()) {
                    val accessory = accessories.getJSONObject(index)
                    val photo = accessory.optJSONObject("photo") ?: continue
                    if (photoId == photo.optString("id")) {
                        if (accessory.optString("equipment_id") != row.getString("id")) throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Zubehör gehört nicht zu dieser Maschine.")
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
        var state: JSONObject = repository.effective()
        var counts: JSONObject = JSONObject()
        var hashes: JSONObject = JSONObject()
        var groups: MutableMap<String, JSONArray> = LinkedHashMap()
        for (type in arrayOf("property", "inquiry", "care_plan", "visit", "tour", "material", "material_movement", "equipment", "equipment_usage")) {
            var values: JSONArray? = records(type)
            groups.put(type, (values)!!)
            counts.put(type, values!!.length())
            var ids: TreeSet<String> = TreeSet()
            run {
                var i: Int = 0
                while ((i < values!!.length())) {
                    var row: JSONObject = values!!.getJSONObject(i)
                    if (((!type.equals(row.optString("kind")) || !ids.add(RevierRules.id(row, "id", true))))) {
                        throw RevierRules.error("REVIER_RELATION_INVALID", "Datensatztyp oder eindeutige Kennung fehlt.")
                    }
                    i++
                }
            }
            hashes.put(type, PixelMcpWriteTools.hash(JSONArray(ids)))
        }
        run {
            var i: Int = 0
            while ((i < groups.get("property")!!.length())) {
                var property: JSONObject = groups.get("property")!!.getJSONObject(i)
                customer(state, RevierRules.id(property, "customer_id", true), false)
                i++
            }
        }
        run {
            var i: Int = 0
            while ((i < groups.get("inquiry")!!.length())) {
                var inquiry: JSONObject = groups.get("inquiry")!!.getJSONObject(i)
                var customer: String = RevierRules.id(inquiry, "customer_id", true)
                customer(state, customer, false)
                var property: String = RevierRules.id(inquiry, "property_id", false)
                if (((!property.isEmpty() && !customer.equals(load("property", property).getString("customer_id"))))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Anfrage und Objekt passen nicht zusammen.")
                }
                for (reference in arrayOf(arrayOf("offer_id", "offers"), arrayOf("order_id", "orders"), arrayOf("inspection_appointment_id", "appointments"))) {
                    if (((!inquiry.optString(reference[0]).isEmpty() && !customer.equals(canonical(state, reference[1], inquiry.getString(reference[0])).optString("customer_id"))))) {
                        throw RevierRules.error("REVIER_RELATION_INVALID", "Anfrageverlauf hat einen widersprüchlichen Bezug.")
                    }
                }
                var photos: JSONArray? = inquiry.optJSONArray("photo_ids")
                run {
                    var j: Int = 0
                    while (((photos != null) && (j < photos!!.length()))) {
                        if ((!customer.equals(canonical(state, "photos", photos!!.getString(j)).optString("customer_id")))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Besichtigungsfoto gehört zu einem anderen Kunden.")
                        }
                        j++
                    }
                }
                i++
            }
        }
        run {
            var i: Int = 0
            while ((i < groups.get("care_plan")!!.length())) {
                var plan: JSONObject = groups.get("care_plan")!!.getJSONObject(i)
                if ((!plan.getString("customer_id").equals(load("property", plan.getString("property_id")).getString("customer_id")))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Pflegeplan und Objekt passen nicht zusammen.")
                }
                var rules: JSONArray = ruleVersions(plan.getString("id"))
                var versions: MutableSet<Int> = HashSet()
                var current: Boolean = false
                run {
                    var j: Int = 0
                    while ((j < rules.length())) {
                        var rule: JSONObject = rules.getJSONObject(j)
                        if (((!plan.getString("id").equals(rule.getString("plan_id")) || !versions.add(rule.getInt("version"))))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Pflegeversion fehlt oder ist doppelt.")
                        }
                        RevierRules.date(rule.getString("effective_from"))
                        if (((rule.getInt("version") == plan.getInt("rule_version")))) {
                            current = true
                        }
                        var snapshot: JSONObject = rule.getJSONObject("rule")
                        RevierRules.date(snapshot.getString("anchor_date"))
                        if ((((snapshot.getInt("interval_days") < 1) || (snapshot.getInt("planned_minutes") < 1)))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Ungültige Pflegeversion.")
                        }
                        checkResourceReferences(snapshot)
                        j++
                    }
                }
                if ((!current)) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Aktuelle Pflegeversion fehlt.")
                }
                i++
            }
        }
        var occurrences: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while ((i < groups.get("visit")!!.length())) {
                var visit: JSONObject = groups.get("visit")!!.getJSONObject(i)
                if ((!visit.getString("customer_id").equals(load("property", visit.getString("property_id")).getString("customer_id")))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Besuch und Objekt passen nicht zusammen.")
                }
                RevierRules.date(visit.getString("date"))
                if (((!visit.optString("order_id").isEmpty() && !visit.getString("customer_id").equals(canonical(state, "orders", visit.getString("order_id")).optString("customer_id"))))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Besuch und Auftrag passen nicht zusammen.")
                }
                if ((!visit.optString("care_plan_id").isEmpty())) {
                    var plan: JSONObject = load("care_plan", visit.getString("care_plan_id"))
                    var occurrence: String = (("" + plan.getString("id") + ":") + visit.getString("occurrence_key"))
                    RevierRules.date(visit.getString("occurrence_key"))
                    if ((((!occurrences.add(occurrence) || !visit.getString("id").equals(repository.get((("" + RevierRules.ROOT + "occurrence:") + occurrence), ""))) || !plan.getString("property_id").equals(visit.getString("property_id"))))) {
                        throw RevierRules.error("REVIER_RELATION_INVALID", "Pflegevorkommen ist doppelt oder falsch verknüpft.")
                    }
                }
                if (((!visit.optString("parent_visit_id").isEmpty() && !visit.getString("property_id").equals(load("visit", visit.getString("parent_visit_id")).getString("property_id"))))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Restbesuch gehört zu einem anderen Objekt.")
                }
                checkResourceReferences(visit)
                i++
            }
        }
        var activeStops: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while ((i < groups.get("tour")!!.length())) {
                var tour: JSONObject = groups.get("tour")!!.getJSONObject(i)
                RevierRules.date(tour.getString("date"))
                var local: MutableSet<String> = HashSet()
                var ids: JSONArray = tour.getJSONArray("visit_ids")
                run {
                    var j: Int = 0
                    while ((j < ids.length())) {
                        var id: String = ids.getString(j)
                        var visit: JSONObject = load("visit", id)
                        if (((!local.add(id) || (RevierRules.activeVisit(visit) && !activeStops.add(id))))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Ein Besuch steht mehrfach in aktiven Touren.")
                        }
                        if ((((!tour.optBoolean("needs_review") && RevierRules.activeVisit(visit)) && !tour.getString("date").equals(visit.getString("date"))))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Tourverschiebung benötigt eine sichtbare Prüfung.")
                        }
                        j++
                    }
                }
                i++
            }
        }
        var balances: MutableMap<String, BigDecimal> = HashMap()
        run {
            var i: Int = 0
            while ((i < groups.get("material")!!.length())) {
                balances.put(groups.get("material")!!.getJSONObject(i).getString("id"), BigDecimal.ZERO)
                i++
            }
        }
        var reversed: MutableSet<String> = HashSet()
        run {
            var i: Int = 0
            while ((i < groups.get("material_movement")!!.length())) {
                var movement: JSONObject = groups.get("material_movement")!!.getJSONObject(i)
                var material: String = movement.getString("material_id")
                var catalog: JSONObject = load("material", material)
                if ((!catalog.getString("unit").equals(movement.getString("unit")))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Materialbewegung hat eine andere Einheit.")
                }
                var quantity: BigDecimal = BigDecimal(movement.getString("quantity_decimal"))
                if (((quantity.signum() == 0))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Leere Materialbewegung.")
                }
                balances.put(material, balances.get(material)!!.add(quantity))
                validateStoredContext(movement, state)
                if ((movement.optString("movement_kind").equals("reversal"))) {
                    var source: String = movement.getString("reverses_movement_id")
                    var original: JSONObject = load("material_movement", source)
                    if (((((!reversed.add(source) || !movement.getString("id").equals(repository.get((("" + RevierRules.ROOT + "reversed:") + source), ""))) || !material.equals(original.getString("material_id"))) || (quantity.compareTo(BigDecimal(original.getString("quantity_decimal")).negate()) != 0)))) {
                        throw RevierRules.error("REVIER_RELATION_INVALID", "Materialgegenbuchung ist widersprüchlich.")
                    }
                }
                i++
            }
        }
        run {
            var i: Int = 0
            while ((i < groups.get("material")!!.length())) {
                var material: JSONObject = groups.get("material")!!.getJSONObject(i)
                if (((balances.get(material.getString("id"))!!.compareTo(BigDecimal(material.getString("stock_decimal"))) != 0))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Bestand und Bewegungen stimmen nicht überein.")
                }
                i++
            }
        }
        run {
            var i: Int = 0
            while ((i < groups.get("equipment_usage")!!.length())) {
                var usage: JSONObject = groups.get("equipment_usage")!!.getJSONObject(i)
                load("equipment", usage.getString("equipment_id"))
                validateStoredContext(usage, state)
                i++
            }
        }
        for (collection in arrayOf("worklogs", "photos")) {
            var values: JSONArray? = state.optJSONArray(collection)
            run {
                var i: Int = 0
                while (((values != null) && (i < values!!.length()))) {
                    var value: JSONObject = values!!.getJSONObject(i)
                    validateStoredContext(value, state)
                    if (((collection.equals("photos") && ((!value.optString("property_id").isEmpty() || !value.optString("visit_id").isEmpty()))))) {
                        if ((value.optString("role").equals("profile"))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Profilbild und Einsatzdokumentation benötigen getrennte Zuordnungen.")
                        }
                        if ((!value.optString("worklog_id").isEmpty())) {
                            var work: JSONObject = canonical(state, "worklogs", value.getString("worklog_id"))
                            for (field in arrayOf("property_id", "visit_id")) {
                                if ((((!value.optString(field).isEmpty() && !work.optString(field).isEmpty()) && !value.getString(field).equals(work.getString(field))))) {
                                    throw RevierRules.error("REVIER_RELATION_INVALID", "Foto und Arbeitsaufzeichnung gehören zu verschiedenen Einsätzen.")
                                }
                            }
                        }
                    }
                    i++
                }
            }
        }
        var sessions: JSONArray = repository.sessionContexts()
        run {
            var i: Int = 0
            while ((i < sessions.length())) {
                var session: JSONObject = sessions.getJSONObject(i)
                validateStoredContext(session, state)
                if (((!session.optString("property_id").isEmpty() || !session.optString("visit_id").isEmpty()))) {
                    if ((((session.has("_start_customer_id") && !session.optString("customer_id").equals(session.optString("_start_customer_id"))) || (session.has("_start_order_id") && !session.optString("order_id").equals(session.optString("_start_order_id")))))) {
                        throw RevierRules.error("REVIER_RELATION_INVALID", "Sitzung und ursprünglicher Einsatzbezug widersprechen sich.")
                    }
                }
                i++
            }
        }
        var equipment: JSONArray = (groups.get("equipment"))!!
        var maintenance: JSONArray = records("equipment_maintenance")
        counts.put("equipment_maintenance", maintenance.length())
        var events: MutableMap<String, JSONObject> = HashMap()
        var latest: MutableMap<String, String> = HashMap()
        run {
            var i: Int = 0
            while ((i < maintenance.length())) {
                var event: JSONObject? = maintenance.getJSONObject(i)
                var eventId: String = RevierRules.id((event)!!, "id", true)
                var equipmentId: String = RevierRules.id((event)!!, "equipment_id", true)
                var planId: String = RevierRules.id((event)!!, "plan_id", true)
                if (((!event!!.optString("kind").equals("equipment_maintenance") || (events.put(eventId, event) != null)))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Ungültiger Wartungsbeleg.")
                }
                var device: JSONObject = load("equipment", equipmentId)
                EquipmentMaintenanceRules.find(EquipmentMaintenanceRules.plans(device), planId)
                var performed: String = RevierRules.date(event, "performed_on", true)
                if ((!performed.equals(event!!.optString("date")))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Wartungsdatum und Historie widersprechen sich.")
                }
                RevierRules.text(event, "title", 240, true)
                var relation: String = (("" + equipmentId + ":") + planId)
                if ((!(event!!.opt("schedule_applied") is Boolean))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Planwirkung des Wartungsbelegs fehlt.")
                }
                var applied: Boolean = event!!.getBoolean("schedule_applied")
                if (((applied && ((!latest.containsKey(relation) || (performed.compareTo((latest.get(relation))!!) > 0)))))) {
                    latest.put(relation, performed)
                }
                for (snapshot in arrayOf("plan_before", "plan_after")) {
                    var historical: JSONObject = event!!.getJSONObject(snapshot)
                    if ((!planId.equals(historical.optString("id")))) {
                        throw RevierRules.error("REVIER_RELATION_INVALID", "Wartungsbeleg verweist auf einen anderen Plan.")
                    }
                    EquipmentMaintenanceRules.plans(JSONObject().put("maintenance_plans", JSONArray().put(historical)))
                }
                var before: JSONObject = event!!.getJSONObject("plan_before")
                var after: JSONObject = event!!.getJSONObject("plan_after")
                if (((((applied != ((performed.compareTo(before.getString("anchor_on")) >= 0))) || (applied && ((!eventId.equals(after.optString("last_event_id")) || !performed.equals(after.optString("last_completed_on")))))) || (!applied && !RevierRules.hash(before).equals(RevierRules.hash(after)))))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Wartungsbeleg und Planwirkung widersprechen sich.")
                }
                i++
            }
        }
        hashes.put("equipment_maintenance", PixelMcpWriteTools.hash(JSONArray(TreeSet(events.keys))))
        var equipmentPhotos: MutableMap<String, String> = TreeMap()
        run {
            var i: Int = 0
            while ((i < equipment.length())) {
                var device: JSONObject = equipment.getJSONObject(i)
                if ((device.has("profile_photo"))) {
                    var photo: JSONObject = device.getJSONObject("profile_photo")
                    EquipmentProfilePhotoRules.validate(photo, device.getString("id"))
                    if (((equipmentPhotos.put(photo.getString("id"), (("" + device.getString("id") + ":") + photo.getString("content_sha256"))) != null))) {
                        throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Ein Gerätefoto darf nicht gleichzeitig zu mehreren Geräten gehören.")
                    }
                    repository.verifyEquipmentProfilePhoto(photo)
                }
                val accessories = device.optJSONArray("accessories") ?: JSONArray()
                val accessoryIds = HashSet<String>()
                val chain = device.optString("current_chain_id")
                var currentFound = chain.isEmpty()
                for (index in 0 until accessories.length()) {
                    val item = accessories.getJSONObject(index)
                    if (!accessoryIds.add(item.getString("id")) || item.optString("equipment_id") != device.getString("id")) throw RevierRules.error("REVIER_RELATION_INVALID", "Zubehörzuordnung ist nicht eindeutig.")
                    if (item.getString("id") == chain && item.optString("type") == "chain") currentFound = true
                    item.optJSONObject("photo")?.let { photo ->
                        EquipmentProfilePhotoRules.validate(photo, device.getString("id"))
                        if (equipmentPhotos.put(photo.getString("id"), device.getString("id") + ":" + item.getString("id") + ":" + photo.getString("content_sha256")) != null) throw RevierRules.error("EQUIPMENT_PHOTO_INVALID", "Fotozuordnung ist nicht eindeutig.")
                        repository.verifyEquipmentProfilePhoto(photo)
                    }
                }
                if (!currentFound) throw RevierRules.error("REVIER_RELATION_INVALID", "Aktuelle Kette gehört nicht zur Maschine.")
                var plans: JSONArray = EquipmentMaintenanceRules.plans(device)
                run {
                    var j: Int = 0
                    while ((j < plans.length())) {
                        var plan: JSONObject = plans.getJSONObject(j)
                        var done: String = plan.optString("last_completed_on")
                        var relation: String = (("" + device.getString("id") + ":") + plan.getString("id"))
                        if ((!done.equals(latest.getOrDefault(relation, "")))) {
                            throw RevierRules.error("REVIER_RELATION_INVALID", "Letzte Wartung und Verlauf sind nicht vollständig.")
                        }
                        if ((!done.isEmpty())) {
                            var event: JSONObject? = events.get(plan.optString("last_event_id"))
                            if ((((((event == null) || !device.getString("id").equals(event!!.optString("equipment_id"))) || !plan.getString("id").equals(event!!.optString("plan_id"))) || !done.equals(event!!.optString("performed_on"))))) {
                                throw RevierRules.error("REVIER_RELATION_INVALID", "Letzter Wartungsbeleg fehlt oder gehört zu einem anderen Gerät.")
                            }
                        }
                        j++
                    }
                }
                i++
            }
        }
        if ((!equipmentPhotos.isEmpty())) {
            counts.put("equipment_profile_photos", equipmentPhotos.size)
            hashes.put("equipment_profile_photos", PixelMcpWriteTools.hash(JSONObject(equipmentPhotos)))
        }
        var decisions: JSONObject = OfferDecisionValidation.validate(repository, state)
        return JSONObject().put("format_version", 1).put("status", "PASS").put("counts", counts).put("id_set_hashes", hashes).put("offer_decisions", decisions).put("business_data_changed", false)
    }
    @Throws(Exception::class)
    private fun checkResourceReferences(row: JSONObject) {
        var materials: JSONArray? = row.optJSONArray("material_needs")
        run {
            var i: Int = 0
            while (((materials != null) && (i < materials!!.length()))) {
                load("material", materials!!.getJSONObject(i).getString("material_id"))
                i++
            }
        }
        var equipment: JSONArray? = row.optJSONArray("equipment_ids")
        run {
            var i: Int = 0
            while (((equipment != null) && (i < equipment!!.length()))) {
                load("equipment", equipment!!.getString(i))
                i++
            }
        }
    }
    @Throws(Exception::class)
    private fun validateStoredContext(row: JSONObject, state: JSONObject) {
        var property: String = RevierRules.id(row, "property_id", false)
        if ((!property.isEmpty())) {
            var `object`: JSONObject = load("property", property)
            if ((!row.optString("customer_id").equals(`object`.getString("customer_id")))) {
                throw RevierRules.error("REVIER_RELATION_INVALID", "Einsatzbezug hat einen falschen Kunden.")
            }
        }
        var visit: String = RevierRules.id(row, "visit_id", false)
        if ((!visit.isEmpty())) {
            var source: JSONObject? = load("visit", visit)
            if ((((!property.equals(source!!.getString("property_id")) || !row.optString("customer_id").equals(source!!.getString("customer_id"))) || ((!row.optString("order_id").isEmpty() && !source!!.optString("order_id").isEmpty()) && !row.optString("order_id").equals(source!!.optString("order_id")))))) {
                throw RevierRules.error("REVIER_RELATION_INVALID", "Einsatzbezug hat ein falsches Objekt, einen anderen Kunden oder Auftrag.")
            }
        }
        var session: String = RevierRules.id(row, "session_id", false)
        if ((!session.isEmpty())) {
            var source: JSONObject? = repository.session(session)
            if ((((source == null) || (!row.optString("customer_id").isEmpty() && !row.getString("customer_id").equals(source!!.optString("customer_id")))))) {
                throw RevierRules.error("REVIER_RELATION_INVALID", "Sitzungsbezug fehlt oder widerspricht dem Kunden.")
            }
            for (field in arrayOf("property_id", "visit_id")) {
                if ((((!row.optString(field).isEmpty() && !source!!.optString(field).isEmpty()) && !row.getString(field).equals(source!!.getString(field))))) {
                    throw RevierRules.error("REVIER_RELATION_INVALID", "Sitzung und dokumentierter Einsatz widersprechen sich.")
                }
            }
        }
    }
    companion object {
        private val BUSINESS_ZONE: ZoneId = ZoneId.of("Europe/Vienna")
        private fun calendarDay(value: String): String {
            if (((value.length <= 10))) {
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
            var rows: MutableList<JSONObject> = ArrayList()
            run {
                var i: Int = 0
                while ((i < values.length())) {
                    rows.add(values.getJSONObject(i))
                    i++
                }
            }
            rows.sortWith(Comparator.comparing(lambda10@ { r: JSONObject -> r.optString("at") }).reversed())
            var out: JSONArray = JSONArray()
            run {
                var i: Int = 0
                while (((i < rows.size) && (i < maximum))) {
                    out.put(rows.get(i))
                    i++
                }
            }
            return out
        }
        private val ARCHIVABLE: Array<String> = arrayOf("property", "inquiry", "care_plan", "visit", "material", "equipment")
        @Throws(Exception::class)
        private fun references(value: Any, id: String): Boolean {
            if ((value is JSONObject)) {
                var `object`: JSONObject = (value as JSONObject)
                run {
                    var keys: MutableIterator<String> = `object`.keys()
                    while (keys.hasNext()) {
                        if ((references(`object`.get(keys.next()), id))) {
                            return true
                        }
                    }
                }
            } else {
                if ((value is JSONArray)) {
                    var array: JSONArray = (value as JSONArray)
                    run {
                        var i: Int = 0
                        while ((i < array.length())) {
                            if ((references(array.get(i), id))) {
                                return true
                            }
                            i++
                        }
                    }
                } else {
                    if ((value is String)) {
                        return id.equals(value)
                    }
                }
            }
            return false
        }
        private val SEARCH_MARKS: java.util.regex.Pattern = java.util.regex.Pattern.compile("\\p{M}+")
        private fun normalize(value: String): String {
            return SEARCH_MARKS.matcher(java.text.Normalizer.normalize(value.lowercase(Locale.GERMAN).replace("ß", "ss"), java.text.Normalizer.Form.NFD)).replaceAll("")
        }
    }
}
