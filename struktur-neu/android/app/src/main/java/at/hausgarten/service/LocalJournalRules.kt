package at.hausgarten.service

import java.util.*
import org.json.*

internal class LocalJournalRules {
    companion object {
        @JvmField
        val KINDS: Array<String> =
            arrayOf(
                "customer",
                "order",
                "worklog",
                "photo",
                "offer",
                "service_template",
                "appointment",
            )

        @JvmStatic
        fun collection(kind: String): String {

            when (kind) {
                "appointment" -> {
                    return "appointments"
                }
                "customer" -> {
                    return "customers"
                }
                "order" -> {
                    return "orders"
                }
                "worklog" -> {
                    return "worklogs"
                }
                "photo" -> {
                    return "photos"
                }
                "invoice" -> {
                    return "invoices"
                }
                "offer" -> {
                    return "offers"
                }
                "service_template" -> {
                    return "service_catalog"
                }
                else -> {
                    return ""
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validateSnapshot(snapshot: JSONObject): Unit {

            for (key in arrayOf("offers", "service_catalog", "appointments")) {
                if (!(snapshot.has(key))) {
                    snapshot.put(key, JSONArray())
                }
            }
            for (key in
                arrayOf(
                    "customers",
                    "orders",
                    "worklogs",
                    "invoices",
                    "photos",
                    "offers",
                    "service_catalog",
                    "appointments",
                )) {
                var rows = snapshot.optJSONArray(key)
                if ((rows == null)) {
                    throw IllegalArgumentException(
                        "Der lokale Gesamtstand ist unvollständig."
                    )
                }
                var ids = HashSet<String>()
                run {
                    var i: Int = 0
                    var firstIteration1 = true
                    while (true) {
                        if (firstIteration1) firstIteration1 = false
                        else {
                            i++
                        }
                        if (!((i < rows.length()))) break

                        var row = rows.optJSONObject(i)
                        if (
                            (((row == null) || row.optString("id").isEmpty()) ||
                                !(ids.add(row.getString("id"))))
                        ) {
                            throw IllegalArgumentException(
                                "Ungültige oder doppelte IDs im lokalen Gesamtstand."
                            )
                        }
                    }
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun guard(row: JSONObject?, kind: String): JSONObject {

            var base = JSONObject()
            if ((row == null)) {
                return base
            }
            if ((((kind == "offer") || (kind == "service_template")) || (kind == "appointment"))) {
                return clean(row)
            }
            if (((kind == "worklog") && !(row.optString("appointment_id").isEmpty()))) {
                base.put("appointment_id", row.getString("appointment_id"))
            }
            var keys =
                (if ((kind == "worklog")) PixelMcpWriteTools.BASE_FIELDS
                else
                    (if ((kind == "customer"))
                        arrayOf(
                            "id",
                            "updated_at",
                            "name",
                            "first_name",
                            "last_name",
                            "company",
                            "street",
                            "postal_code",
                            "city",
                            "country",
                            "email",
                            "phone",
                            "notes",
                            "active",
                            "garden_area",
                            "sevdesk_customer_number",
                            "custom_fields",
                            "billing_mode",
                            "default_billing_customer_id",
                        )
                    else
                        (if ((kind == "order"))
                            arrayOf(
                                "id",
                                "updated_at",
                                "customer_id",
                                "title",
                                "description",
                                "location",
                                "scheduled_date",
                                "status",
                                "notes",
                                "customer_visible",
                                "billing_customer_id",
                                "offer_id",
                                "offer_refs",
                            )
                        else
                                arrayOf(
                                    "id",
                                    "customer_id",
                                    "order_id",
                                    "worklog_id",
                                    "filename",
                                    "caption",
                                    "customer_visible",
                                    "content_sha256",
                                    "byte_size",
                                    "mime_type",
                                    "storage_name",
                                    "role",
                                    "session_id",
                                    "capture_time",
                                    "updated_at",
                                ))))
            for (key in keys) {
                base.put(
                    key,
                    (if ((key == "custom_fields")) comparable(key, row.opt(key))
                    else (if (row.has(key)) row.get(key) else JSONObject.NULL)),
                )
            }
            return base
        }

        @JvmStatic
        @Throws(Exception::class)
        fun clean(value: JSONObject): JSONObject {

            var out = JSONObject(value.toString())
            run {
                var keys = value.keys()
                var firstIteration3 = true
                while (true) {
                    if (firstIteration3) firstIteration3 = false else {}
                    if (!(keys.hasNext())) break

                    var key = keys.next()
                    if (
                        (((key.startsWith("_") || (key == "pending")) ||
                            (key == "allow_duplicate")) || (key == "require_order"))
                    ) {
                        out.remove(key)
                    }
                }
            }
            return out
        }

        @JvmStatic
        @Throws(Exception::class)
        fun comparable(key: String, value: Any?): Any {

            if (
                ((key == "garden_area") &&
                    ((((value == null) || (value == JSONObject.NULL)) ||
                        value.toString().isEmpty())))
            ) {
                return JSONObject.NULL
            }
            if ((key == "custom_fields")) {
                return (if ((((value == null) || (value == JSONObject.NULL)) || ("" == value)))
                    JSONObject()
                else (if (value is String) JSONObject((value as String)) else value))
            }
            if (
                Arrays.asList(
                        "hours",
                        "break_minutes",
                        "kilometers",
                        "billed",
                        "customer_visible",
                        "active",
                        "garden_area",
                    )
                    .contains(key)
            ) {
                if (((value == null) || (value == JSONObject.NULL))) {
                    return 0
                }
                if (value is Boolean) {
                    return (if (((value as Boolean))) 1 else 0)
                }
                if (value.toString().isEmpty()) {
                    return 0
                }
                return java.math.BigDecimal(value.toString()).stripTrailingZeros()
            }
            if (((value == null) || (value == JSONObject.NULL))) {
                return ""
            }
            return value
        }

        @JvmStatic
        @Throws(Exception::class)
        fun compare(expected: JSONObject, current: JSONObject?): Unit {

            if (((current == null) || !((expected.optString("id") == current.optString("id"))))) {
                throw PixelMcpWriteTools.Rejected(
                    "WRITE_CONFLICT",
                    "Der ursprüngliche Datensatz fehlt. Keine Wiederanlage vorgenommen.",
                )
            }
            run {
                var keys = expected.keys()
                var firstIteration4 = true
                while (true) {
                    if (firstIteration4) firstIteration4 = false else {}
                    if (!(keys.hasNext())) break

                    var key = keys.next()
                    if (
                        !((PixelMcpWriteTools.canonical(comparable(key, expected.get(key))) ==
                            PixelMcpWriteTools.canonical(comparable(key, current.opt(key)))))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "WRITE_CONFLICT",
                            "Der Datensatz wurde parallel geändert. Bitte die Änderung prüfen.",
                        )
                    }
                }
            }
        }

        @JvmStatic
        fun ids(values: JSONArray?): MutableSet<String> {

            var ids = HashSet<String>()
            run {
                var i: Int = 0
                var firstIteration5 = true
                while (true) {
                    if (firstIteration5) firstIteration5 = false
                    else {
                        i++
                    }
                    if (!(((values != null) && (i < values.length())))) break

                    ids.add(values.optString(i))
                }
            }
            return ids
        }

        /** Persisted drive_* receipt keys are part of the existing local backup format.
         * They identify committed local actions; no delivery, cloud merge or sync state is used. */
        @JvmStatic @Throws(Exception::class)
        fun apply(snapshot: JSONObject, input: JSONArray): JSONArray {
            LocalBusinessState.prepare(snapshot)
            val actions = LocalActionOrder.order(input)
            val seen = ids(snapshot.optJSONArray("drive_applied_ids"))
            val accepted = ids(snapshot.optJSONArray("accepted_ids"))
            val hashes = snapshot.optJSONObject("drive_action_hashes") ?: JSONObject()
            val resolved = snapshot.optJSONObject("drive_resolved_actions") ?: JSONObject()
            val receipts = snapshot.optJSONObject("drive_action_receipts") ?: JSONObject()
            val all = HashMap<String, JSONObject>()
            val errors = JSONArray()
            val failed = HashSet<String>()
            for (i in 0 until actions.length()) {
                val action = actions.getJSONObject(i)
                all[action.getString("client_id")] = action
            }
            for (i in 0 until actions.length()) {
                val action = actions.getJSONObject(i)
                val client = action.getString("client_id")
                val kind = action.getString("kind")
                val digest = PixelMcpWriteTools.hash(action)
                if (hashes.has(client) && hashes.getString(client) != digest) {
                    throw PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT", "Eine bestätigte lokale Aktionskennung wurde verändert.")
                }
                if (seen.contains(client) || accepted.contains(client) || resolved.has(client)) continue
                try {
                    val trial = JSONObject(snapshot.toString())
                    val trialResolved = JSONObject(resolved.toString())
                    val patch = action.getJSONObject("payload")
                    val dependencies = patch.optJSONArray("_sync_predecessors")
                    for (j in 0 until (dependencies?.length() ?: 0)) {
                        if (failed.contains(dependencies!!.getString(j))) throw PixelMcpWriteTools.Rejected("DEPENDENCY_BLOCKED", "Lokale Vorgängeraktion benötigt Prüfung.")
                    }
                    if (OwnerJournalRules.handles(kind)) {
                        OwnerJournalRules.apply(trial, action, all, trialResolved)
                    } else {
                        require(kind in KINDS) { "UNSUPPORTED_ACTION" }
                        val group = collection(kind)
                        val id = patch.getString("id")
                        val old = OwnerJournalRules.findOrNull(trial.getJSONArray(group), id)
                        val expected = patch.optJSONObject("_mcp_base") ?: patch.optJSONObject("_sync_base")
                        if (patch.optBoolean("_mcp_create") && old != null) throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Die neue ID existiert bereits.")
                        if (expected != null) compare(expected, old)
                        val merged = if (old == null) JSONObject() else JSONObject(old.toString())
                        val sanitized = clean(patch)
                        if (kind == "photo") { sanitized.remove("data"); sanitized.remove("full_data") }
                        for (key in sanitized.keys()) merged.put(key, sanitized.get(key))
                        if (old != null && expected == null && PixelMcpWriteTools.hash(merged) != PixelMcpWriteTools.hash(old)) {
                            throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Lokale Änderung ohne Vergleichsstand; nichts überschrieben.")
                        }
                        if (old == null && patch.optBoolean("allow_duplicate")) merged.put("allow_duplicate", true)
                        validateChange(kind, merged, old, trial)
                        merged.remove("allow_duplicate")
                        OwnerJournalRules.replace(trial, group, id, merged)
                    }
                    for (key in trial.keys()) snapshot.put(key, trial.get(key))
                    for (key in trialResolved.keys()) resolved.put(key, trialResolved.get(key))
                    seen.add(client)
                    hashes.put(client, digest)
                    receipts.put(client, JSONObject().put("action_sha256", digest).put("kind", kind).put("state", "applied"))
                } catch (error: Exception) {
                    failed.add(client)
                    errors.put(JSONObject().put("client_id", client).put("kind", kind).put("code", errorCode(error)).put("error", error.message))
                }
            }
            refreshDisplayFields(snapshot)
            snapshot.put("drive_applied_ids", JSONArray(seen)).put("drive_action_hashes", hashes)
                .put("drive_resolved_actions", resolved).put("drive_action_receipts", receipts).put("drive_journal_errors", errors)
            return errors
        }

        private @JvmStatic @Throws(Exception::class) fun refreshDisplayFields(
            snapshot: JSONObject
        ): Unit {

            var names = HashMap<String, String>()
            var titles = HashMap<String, String>()
            var totals = HashMap<String, Double>()
            var customers = snapshot.getJSONArray("customers")
            var orders = snapshot.getJSONArray("orders")
            var logs = snapshot.getJSONArray("worklogs")
            run {
                var i: Int = 0
                var firstIteration24 = true
                while (true) {
                    if (firstIteration24) firstIteration24 = false
                    else {
                        i++
                    }
                    if (!((i < customers.length()))) break

                    var row = customers.getJSONObject(i)
                    names.put(row.getString("id"), row.optString("name"))
                }
            }
            run {
                var i: Int = 0
                var firstIteration25 = true
                while (true) {
                    if (firstIteration25) firstIteration25 = false
                    else {
                        i++
                    }
                    if (!((i < orders.length()))) break

                    var row = orders.getJSONObject(i)
                    titles.put(row.getString("id"), row.optString("title"))
                }
            }
            run {
                var i: Int = 0
                var firstIteration26 = true
                while (true) {
                    if (firstIteration26) firstIteration26 = false
                    else {
                        i++
                    }
                    if (!((i < logs.length()))) break

                    var row = logs.getJSONObject(i)
                    var order = row.optString("order_id")
                    totals.put(
                        order,
                        (totals.getOrDefault(order, 0.0) + row.optDouble("hours", 0.0)),
                    )
                }
            }
            for (group in arrayOf("orders", "worklogs", "photos", "invoices")) {
                var rows = snapshot.getJSONArray(group)
                run {
                    var i: Int = 0
                    var firstIteration27 = true
                    while (true) {
                        if (firstIteration27) firstIteration27 = false
                        else {
                            i++
                        }
                        if (!((i < rows.length()))) break

                        var row = rows.getJSONObject(i)
                        if (row.has("customer_name")) {
                            row.put(
                                "customer_name",
                                names.getOrDefault(
                                    row.optString("customer_id"),
                                    row.optString("customer_name"),
                                ),
                            )
                        }
                        if (row.has("order_title")) {
                            row.put(
                                "order_title",
                                titles.getOrDefault(row.optString("order_id"), ""),
                            )
                        }
                        if (((group == "orders") && row.has("total_hours"))) {
                            row.put(
                                "total_hours",
                                (Math.round((totals.getOrDefault(row.optString("id"), 0.0) * 100)) /
                                    100.0),
                            )
                        }
                    }
                }
            }
        }

        private @JvmStatic fun errorCode(error: Exception): String {

            return (if (error is PixelMcpWriteTools.Rejected)
                ((error as PixelMcpWriteTools.Rejected)).code
            else
                (if (error is HttpTransport.Failure) ((error as HttpTransport.Failure)).code
                else (if (error is java.io.IOException) "IO_RETRY_REQUIRED" else "INVALID_ACTION")))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validateTemplate(row: JSONObject): Unit {

            if (!(row.has("pricing_type"))) {
                row.put("pricing_type", "unit")
            }
            if (
                !(OfferContract.values("pricing_types")
                    .asList()
                    .contains(row.optString("pricing_type")))
            ) {
                throw IllegalArgumentException("Unbekannte Bausteinart.")
            }
            for (key in arrayOf("title", "category", "unit")) {
                if (row.optString(key).trim().isEmpty()) {
                    throw IllegalArgumentException(
                        "Leistungsbaustein benötigt Titel, Kategorie und Einheit."
                    )
                }
            }
            for (key in arrayOf("default_price", "default_quantity")) {
                if (
                    ((row.has(key) && !(row.isNull(key))) &&
                        (OfferRules.decimal(row.get(key)).signum() < 0))
                ) {
                    throw IllegalArgumentException("Negative Bausteinwerte sind ungültig.")
                }
            }
            if (!(row.has("default_price"))) {
                row.put("default_price", JSONObject.NULL)
            }
            if (!(row.has("active"))) {
                row.put("active", true)
            }
            if (!(row.has("price_confirmed"))) {
                row.put("price_confirmed", false)
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun validateChange(
            kind: String,
            row: JSONObject,
            old: JSONObject?,
            snapshot: JSONObject,
        ): Unit {

            if (TrashRules.purged(snapshot, kind, row.getString("id"))) {
                throw PixelMcpWriteTools.Rejected(
                    "RECORD_PURGED",
                    "Diese ID wurde endgültig gelöscht und darf nicht durch ein altes Formular, Journal oder Backup wieder angelegt werden.",
                )
            }
            var customers = snapshot.getJSONArray("customers")
            var orders = snapshot.getJSONArray("orders")
            var logs = snapshot.getJSONArray("worklogs")
            var photos = snapshot.getJSONArray("photos")
            var invoices = snapshot.getJSONArray("invoices")
            if ((kind == "customer")) {
                TestDataRules.customerChange(row, old)
                GardenRules.normalize(row)
                BillingRules.customerConfig(row, customers)
                if (row.optString("name").trim().isEmpty()) {
                    throw IllegalArgumentException("Der Kundenname fehlt.")
                }
                if ((old == null)) {
                    EntityProtectionRules.validateNewCustomer(row, customers)
                    if (!(row.has("active"))) {
                        row.put("active", 1)
                    }
                    if (!(row.has("custom_fields"))) {
                        row.put("custom_fields", JSONObject())
                    }
                }
                return
            }
            if ((kind == "appointment")) {
                AppointmentRules.validate(row, old, snapshot)
                return
            }
            if ((kind == "offer")) {
                OfferRules.normalize(row, old, snapshot)
                return
            }
            if ((kind == "service_template")) {
                validateTemplate(row)
                return
            }
            if ((kind == "photo")) {
                EntityProtectionRules.validatePhoto(row, customers, orders, logs)
                return
            }
            PixelMcpWriteTools.find(customers, row.optString("customer_id"))
            var orderId = (if (row.isNull("order_id")) "" else row.optString("order_id"))
            if (
                (!(orderId.isEmpty()) &&
                    !((row.optString("customer_id") ==
                        PixelMcpWriteTools.find(orders, orderId).optString("customer_id"))))
            ) {
                throw IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.")
            }
            if ((kind == "order")) {
                TestDataRules.guard(row, snapshot)
                BillingRules.order(row, snapshot)
                if (row.optString("title").trim().isEmpty()) {
                    throw IllegalArgumentException("Auftragsbezeichnung fehlt.")
                }
                if ((old == null)) {
                    if (row.optString("status").isEmpty()) {
                        row.put("status", "Geplant")
                    }
                    if (!(row.has("customer_visible"))) {
                        row.put("customer_visible", 1)
                    }
                }
                if (
                    (!(row.optString("scheduled_date").isEmpty()) &&
                        !(row.isNull("scheduled_date")))
                ) {
                    java.time.LocalDate.parse(row.getString("scheduled_date"))
                }
                EntityProtectionRules.validateOrderChange(row, old, logs, invoices, photos)
            }
            if ((kind == "worklog")) {
                AppointmentRules.worklog(row, old, snapshot)
                if (
                    (row.optString("activity").trim().isEmpty() ||
                        row.optString("work_date").isEmpty())
                ) {
                    throw IllegalArgumentException("Datum und Tätigkeit sind erforderlich.")
                }
                BillingRules.work(
                    row,
                    (if (((old == null) && !(row.has("billing_customer_id"))))
                        JSONObject().put("customer_id", row.getString("customer_id"))
                    else old),
                    snapshot,
                )
                var hours: Double = row.optDouble("hours", 0.0)
                WorklogRules.validate(row, (old == null))
                if ((Math.abs((hours - row.optDouble("hours"))) > 0.005)) {
                    throw IllegalArgumentException("Stunden und Uhrzeiten widersprechen sich.")
                }
                WorklogRelationRules.validate(row, old, photos, invoices)
            }
            if ((kind == "photo")) {
                EntityProtectionRules.validatePhoto(row, customers, orders, logs)
            }
        }
    }
}
