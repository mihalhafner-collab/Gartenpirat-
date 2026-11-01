package at.hausgarten.service

import java.util.*
import org.json.*

internal class DriveJournalRules {
    fun interface Financial {
        @Throws(Exception::class) fun apply(snapshot: JSONObject, action: JSONObject): Unit
    }

    companion object {
        @JvmField
        val KINDS: Array<String> =
            arrayOf(
                "customer",
                "order",
                "worklog",
                "photo",
                "invoice",
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
                        "Der Drive-Gesamtstand ist unvollständig. Lokale Daten bleiben erhalten."
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
                                "Ungültige oder doppelte IDs im Drive-Gesamtstand. Abgleich angehalten."
                            )
                        }
                    }
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun union(vararg packages: JSONArray?): JSONArray {

            var actions = LinkedHashMap<String, JSONObject>()
            for (source in packages) {
                run {
                    var i: Int = 0
                    var firstIteration2 = true
                    while (true) {
                        if (firstIteration2) firstIteration2 = false
                        else {
                            i++
                        }
                        if (!(((source != null) && (i < source.length())))) break

                        var action = source.optJSONObject(i)
                        if (
                            ((((action == null) || action.optString("client_id").isEmpty()) ||
                                action.optString("kind").isEmpty()) ||
                                (action.optJSONObject("payload") == null))
                        ) {
                            throw IllegalArgumentException(
                                "Ungültige Drive-Änderungsdatei. Sie wurde nicht überschrieben."
                            )
                        }
                        var id = action.getString("client_id")
                        var old = actions.get(id)
                        if (
                            ((old != null) &&
                                !((PixelMcpWriteTools.hash(old) ==
                                    PixelMcpWriteTools.hash(action))))
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "ACTION_ID_CONFLICT",
                                "Eine Änderungskennung enthält verschiedene Angaben. Keine Datei überschrieben.",
                            )
                        }
                        if ((old == null)) {
                            actions.put(id, JSONObject(action.toString()))
                        }
                    }
                }
            }
            var ordered = ArrayList<JSONObject>(actions.values)
            ordered.sortWith(Comparator.comparingLong(DriveJournalRules::created))
            var out = JSONArray()
            for (action in ordered) {
                out.put(action)
            }
            return out
        }

        private @JvmStatic fun created(action: JSONObject): Long {

            try {

                return java.time.Instant.parse(action.optString("created_at")).toEpochMilli()
            } catch (ignored: Exception) {
                try {

                    return java.time.OffsetDateTime.parse(action.optString("created_at"))
                        .toInstant()
                        .toEpochMilli()
                } catch (missing: Exception) {
                    return 0
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
                            (if ((kind == "invoice"))
                                arrayOf(
                                    "id",
                                    "updated_at",
                                    "customer_id",
                                    "order_id",
                                    "invoice_number",
                                    "invoice_date",
                                    "service_start",
                                    "service_end",
                                    "status",
                                    "sevdesk_id",
                                    "issued_at",
                                    "positions",
                                    "payment_days",
                                    "notes",
                                    "domestic_service_confirmed",
                                    "document_type",
                                    "correction_of",
                                    "correction_reason",
                                    "header_text",
                                    "footer_text",
                                    "tax_mode",
                                    "recipient_type",
                                    "recipient_vat_id",
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
                                )))))
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

        @JvmStatic
        @Throws(Exception::class)
        fun queueStatus(snapshot: JSONObject, pending: JSONArray?): JSONObject {

            var delivered = ids(snapshot.optJSONArray("drive_delivered_ids"))
            var errors = HashMap<String, JSONObject>()
            for (key in arrayOf("sync_errors", "drive_journal_errors")) {
                var rows = snapshot.optJSONArray(key)
                run {
                    var i: Int = 0
                    var firstIteration6 = true
                    while (true) {
                        if (firstIteration6) firstIteration6 = false
                        else {
                            i++
                        }
                        if (!(((rows != null) && (i < rows.length())))) break

                        var row = rows.optJSONObject(i)
                        if ((row != null)) {
                            errors.put(row.optString("client_id"), row)
                        }
                    }
                }
            }
            var upload: Int = 0
            var review: Int = 0
            var items = JSONArray()
            run {
                var i: Int = 0
                var firstIteration7 = true
                while (true) {
                    if (firstIteration7) firstIteration7 = false
                    else {
                        i++
                    }
                    if (!(((pending != null) && (i < pending.length())))) break

                    var action = pending.getJSONObject(i)
                    if (action.getJSONObject("payload").optBoolean("_local_only")) {
                        continue
                    }
                    var id = action.optString("client_id")
                    var kind = action.optString("kind")
                    var error = errors.get(id)
                    var resolved = snapshot.optJSONObject("drive_resolved_actions")
                    if (
                        (((resolved != null) && resolved.has(id)) &&
                            !((OrderReviewRules.actionHash(action) == resolved.optString(id))))
                    ) {
                        error =
                            JSONObject()
                                .put("code", "ACTION_ID_CONFLICT")
                                .put(
                                    "error",
                                    "Die geprüfte Aktionskennung enthält einen anderen Hash. Manuell prüfen; keine alte Entscheidung anwenden.",
                                )
                    }
                    var transferred: Boolean = delivered.contains(id)
                    var held: Boolean = collection(kind).isEmpty()
                    var phase: String
                    var message: String
                    if (
                        ((error != null) && ("MEDIA_TRANSFER_PENDING" == error.optString("code")))
                    ) {
                        upload++
                        phase = "media_pending"
                        message = "Lokal gespeichert · Foto wird abschnittsweise übertragen."
                    } else {
                        if ((error != null)) {
                            review++
                            phase =
                                (if (error.optString("code").contains("CONFLICT")) "sync_conflict"
                                else "sync_failed")
                            message =
                                ("Prüfung erforderlich: " +
                                    ((if (error.optString("code").contains("CONFLICT"))
                                        "Widersprüchliche Änderungen; vorhandene Daten wurden nicht überschrieben."
                                    else "Die Änderung konnte nicht übernommen werden.")))
                        } else {
                            if ((held && transferred)) {
                                review++
                                phase = "review_required"
                                message =
                                    (if ((kind == "delete"))
                                        "An die Drive-App übergeben · Löschauftrag bleibt bis zur gesonderten Prüfung zurückgehalten."
                                    else
                                        "An die Drive-App übergeben · geschützter Vorgang benötigt Prüfung.")
                            } else {
                                upload++
                                phase = "sync_pending"
                                message = "Lokal gespeichert · Übertragung ausstehend."
                            }
                        }
                    }
                    var code = (if ((error == null)) "" else error.optString("code"))
                    var next =
                        (if ((code == "SYNC_COMPONENT_UPDATE_REQUIRED")) "update_sync_component"
                        else
                            (if (
                                ((code.contains("CONFLICT") || (code == "DELETE_REVIEW_REQUIRED")))
                            )
                                "compare_versions"
                            else
                                (if (((code == "AUTH_REQUIRED") || (code == "PERMISSION_REQUIRED")))
                                    "connect_drive"
                                else
                                    (if ((code == "PHOTO_ORIGINAL_REQUIRED")) "open_photo"
                                    else
                                        (if ((code == "MEDIA_TRANSFER_PENDING")) "retry_media"
                                        else
                                            (if (
                                                ((kind == "archive") &&
                                                    Arrays.asList(
                                                            "WRITE_CONFLICT",
                                                            "INVALID_ACTION",
                                                            "DEPENDENCY_BLOCKED",
                                                        )
                                                        .contains(code))
                                            )
                                                "compare_versions"
                                            else
                                                (if ((held && ((transferred || (error != null)))))
                                                    "review_action"
                                                else "retry_sync")))))))
                    var item =
                        JSONObject()
                            .put("client_id", id)
                            .put("state", phase)
                            .put("provider_readback", transferred)
                            .put("cloud_confirmed", false)
                            .put("kind", kind)
                            .put("entity_id", action.getJSONObject("payload").optString("id"))
                            .put("created_at", action.optString("created_at"))
                            .put("error", (if ((error == null)) "" else error.optString("error")))
                            .put("code", code)
                            .put("next_action", next)
                            .put("message", message)
                    DrivePublication.evidence(
                        item,
                        JSONObject().put("master", snapshot).put("pending", JSONArray().put(action)),
                    )
                    items.put(item)
                }
            }
            var missing = snapshot.optJSONArray("drive_media_errors")
            run {
                var i: Int = 0
                var firstIteration8 = true
                while (true) {
                    if (firstIteration8) firstIteration8 = false
                    else {
                        i++
                    }
                    if (!(((missing != null) && (i < missing.length())))) break

                    var e = missing.getJSONObject(i)
                    var id = e.optString("entity_id", e.optString("photo_id"))
                    var kind =
                        (if (Arrays.asList("invoice", "offer").contains(e.optString("entity_type")))
                            "document"
                        else "media")
                    items.put(
                        JSONObject()
                            .put("client_id", ((kind + ":") + id))
                            .put("kind", kind)
                            .put("entity_id", id)
                            .put("state", "review_required")
                            .put("code", e.optString("code"))
                            .put(
                                "next_action",
                                (if ((kind == "document")) "open_document" else "open_photo"),
                            )
                            .put("message", e.optString("message"))
                    )
                    review++
                }
            }
            return JSONObject()
                .put("total", items.length())
                .put("pending_upload", upload)
                .put("review", review)
                .put("items", items)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun apply(snapshot: JSONObject, actions: JSONArray): JSONArray {

            return apply(snapshot, actions, null)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun apply(snapshot: JSONObject, actions: JSONArray, financial: Financial?): JSONArray {
            var actions = actions

            actions = SyncActionOrder.order(actions)
            PixelDriveState.prepare(snapshot)
            var accepted = ids(snapshot.optJSONArray("accepted_ids"))
            var seen = ids(snapshot.optJSONArray("drive_applied_ids"))
            var errors = JSONArray()
            var all = HashMap<String, JSONObject>()
            var failed = HashSet<String>()
            run {
                var i: Int = 0
                var firstIteration9 = true
                while (true) {
                    if (firstIteration9) firstIteration9 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var a = actions.getJSONObject(i)
                    all.put(a.getString("client_id"), a)
                }
            }
            var resolved = snapshot.optJSONObject("drive_resolved_actions") ?: JSONObject()
            var remoteErrors = HashMap<String, JSONObject>()
            var remoteFailures = snapshot.optJSONArray("sync_errors")
            run {
                var i: Int = 0
                var firstIteration10 = true
                while (true) {
                    if (firstIteration10) firstIteration10 = false
                    else {
                        i++
                    }
                    if (!(((remoteFailures != null) && (i < remoteFailures.length())))) break

                    var failure = remoteFailures.optJSONObject(i)
                    if (((failure != null) && !(failure.optString("client_id").isEmpty()))) {
                        remoteErrors.put(failure.getString("client_id"), failure)
                    }
                }
            }
            var hashes = snapshot.optJSONObject("drive_action_hashes")
            if ((hashes == null)) {
                hashes = JSONObject()
            }
            run {
                var i: Int = 0
                var firstIteration17 = true
                while (true) {
                    if (firstIteration17) firstIteration17 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var action = actions.getJSONObject(i)
                    var client = action.getString("client_id")
                    var kind = action.getString("kind")
                    var group = collection(kind)
                    var digest = PixelMcpWriteTools.hash(action)
                    if ((hashes.has(client) && !((digest == hashes.getString(client))))) {
                        throw PixelMcpWriteTools.Rejected(
                            "ACTION_ID_CONFLICT",
                            "Eine bestätigte Änderungskennung wurde verändert. Abgleich angehalten.",
                        )
                    }
                    if (
                        (resolved.has(client) &&
                            !((OrderReviewRules.actionHash(action) == resolved.getString(client))))
                    ) {
                        throw PixelMcpWriteTools.Rejected(
                            "ACTION_ID_CONFLICT",
                            "Eine geprüfte Änderung wurde verändert. Abgleich angehalten.",
                        )
                    }
                    if ((accepted.contains(client) || resolved.has(client))) {
                        continue
                    }
                    if ((kind == "conflict_resolution")) {
                        var target = action.getJSONObject("payload").optString("client_id")
                        if (
                            (!(target.isEmpty()) &&
                                (((accepted.contains(target) || seen.contains(target)) ||
                                    resolved.has(target))))
                        ) {
                            var original = all.get(target)
                            if ((original == null)) {
                                original =
                                    action
                                        .getJSONObject("payload")
                                        .optJSONObject("_original_action")
                            }
                            if (
                                (((original == null) ||
                                    !((target == original.optString("client_id")))) ||
                                    !((OrderReviewRules.actionHash(original) ==
                                        action
                                            .getJSONObject("payload")
                                            .optString("expected_action_hash"))))
                            ) {
                                throw PixelMcpWriteTools.Rejected(
                                    "ACTION_ID_CONFLICT",
                                    "Überholte Entscheidung hat keinen passenden Originalbeleg.",
                                )
                            }
                            var history = snapshot.optJSONObject("owner_conflict_history")
                            if ((history == null)) {
                                history = JSONObject()
                            }
                            history.put(
                                client,
                                JSONObject()
                                    .put("resolution", "superseded")
                                    .put("effect", "no_business_data_changed")
                                    .put("target_client_id", target)
                                    .put("original_action", action)
                                    .put("action_hash", OrderReviewRules.actionHash(action)),
                            )
                            snapshot.put("owner_conflict_history", history)
                            seen.add(client)
                            hashes.put(client, digest)
                            continue
                        }
                    }
                    if (
                        (remoteErrors.containsKey(client) &&
                            !(PixelDriveState.retryableInfrastructure(
                                remoteErrors.get(client),
                                kind,
                            )))
                    ) {
                        seen.remove(client)
                        failed.add(client)
                        errors.put(
                            JSONObject(remoteErrors.get(client).toString()).put("kind", kind)
                        )
                        continue
                    }
                    if (seen.contains(client)) {
                        continue
                    }
                    var required =
                        action.getJSONObject("payload").optJSONArray("_sync_predecessors")
                    var dependencyFailed: Boolean = false
                    run {
                        var j: Int = 0
                        var firstIteration11 = true
                        while (true) {
                            if (firstIteration11) firstIteration11 = false
                            else {
                                j++
                            }
                            if (!(((required != null) && (j < required.length())))) break

                            if (
                                (failed.contains(required.getString(j)) &&
                                    !(resolved.has(required.getString(j))))
                            ) {
                                dependencyFailed = true
                            }
                        }
                    }
                    if (dependencyFailed) {
                        failed.add(client)
                        errors.put(
                            JSONObject()
                                .put("client_id", client)
                                .put("kind", kind)
                                .put("code", "DEPENDENCY_BLOCKED")
                                .put(
                                    "error",
                                    "Vorgängeraktion benötigt Prüfung; keine Folgeänderung angewendet.",
                                )
                        )
                        continue
                    }
                    if ((OwnerJournalRules.handles(kind) || (kind == "pixel_invoice_issue_v1"))) {
                        try {

                            var trial = JSONObject(snapshot.toString())
                            var trialResolved = JSONObject(resolved.toString())
                            if ((kind == "pixel_invoice_issue_v1")) {
                                if ((financial == null)) {
                                    throw PixelMcpWriteTools.Rejected(
                                        "NUMBERING_ONLINE_REQUIRED",
                                        "Online-Abschluss durch Pixel/Drive ausstehend.",
                                    )
                                }
                                financial.apply(trial, action)
                            } else {
                                OwnerJournalRules.apply(trial, action, all, trialResolved)
                            }
                            run {
                                var keys = trial.keys()
                                var firstIteration12 = true
                                while (true) {
                                    if (firstIteration12) firstIteration12 = false else {}
                                    if (!(keys.hasNext())) break

                                    var key = keys.next()
                                    snapshot.put(key, trial.get(key))
                                }
                            }
                            resolved = trialResolved
                            seen.add(client)
                            hashes.put(client, digest)
                        } catch (error: Exception) {
                            failed.add(client)
                            errors.put(
                                JSONObject()
                                    .put("client_id", client)
                                    .put("kind", kind)
                                    .put("code", errorCode(error))
                                    .put("error", error.message)
                            )
                        }
                        continue
                    }
                    if (group.isEmpty()) {
                        failed.add(client)
                        errors.put(
                            JSONObject()
                                .put("client_id", client)
                                .put("kind", kind)
                                .put(
                                    "code",
                                    (if ((kind == "delete")) "DELETE_REVIEW_REQUIRED"
                                    else "UNSUPPORTED_ACTION"),
                                )
                                .put(
                                    "error",
                                    (if ((kind == "delete"))
                                        "Älterer Löschauftrag benötigt eine ausdrückliche Entscheidung. Originale bleiben erhalten."
                                    else
                                        "Unbekannter Journaltyp benötigt eine sichere App-Migration; Aktion bleibt erhalten."),
                                )
                        )
                        continue
                    }
                    try {

                        var patch = action.getJSONObject("payload")
                        var predecessors = patch.optJSONArray("_sync_predecessors")
                        run {
                            var j: Int = 0
                            var firstIteration13 = true
                            while (true) {
                                if (firstIteration13) firstIteration13 = false
                                else {
                                    j++
                                }
                                if (!(((predecessors != null) && (j < predecessors.length()))))
                                    break

                                if (
                                    (failed.contains(predecessors.getString(j)) &&
                                        !(resolved.has(predecessors.getString(j))))
                                ) {
                                    throw PixelMcpWriteTools.Rejected(
                                        "DEPENDENCY_BLOCKED",
                                        "Eine Vorgängeränderung benötigt zuerst Prüfung. Diese Aktion bleibt erhalten.",
                                    )
                                }
                            }
                        }
                        var refs = patch.optJSONArray("_sync_resolves")
                        var resolving = HashSet<String>()
                        if (patch.has("_sync_resolves")) {
                            if (
                                (((!((kind == "order")) || (refs == null)) ||
                                    (refs.length() < 1)) || (refs.length() > 1000))
                            ) {
                                throw IllegalArgumentException("Ungültige Auftragsprüfung.")
                            }
                            run {
                                var j: Int = 0
                                var firstIteration14 = true
                                while (true) {
                                    if (firstIteration14) firstIteration14 = false
                                    else {
                                        j++
                                    }
                                    if (!((j < refs.length()))) break

                                    var ref = refs.getJSONObject(j)
                                    var target = ref.getString("client_id")
                                    var prior = all.get(target)
                                    if (
                                        ((((((((((prior == null) || !(resolving.add(target))) ||
                                            !(failed.contains(target))) ||
                                            accepted.contains(target)) || seen.contains(target)) ||
                                            resolved.has(target)) ||
                                            !(("order" == prior.optString("kind")))) ||
                                            patch.optString("id").isEmpty()) ||
                                            !((patch.optString("id") ==
                                                prior.getJSONObject("payload").optString("id")))) ||
                                            !((OrderReviewRules.actionHash(prior) ==
                                                ref.optString("sha256"))))
                                    ) {
                                        throw IllegalArgumentException(
                                            "Die geprüfte Änderung wurde inzwischen verändert oder übernommen. Bitte erneut prüfen."
                                        )
                                    }
                                }
                            }
                        }
                        var rows = JSONArray(snapshot.getJSONArray(group).toString())
                        var id = patch.optString("id")
                        if (id.isEmpty()) {
                            id = ((kind + "_sync_") + hashString(client).substring(0, 32))
                        }
                        var old: JSONObject? = null
                        var at: Int = -(1)
                        run {
                            var j: Int = 0
                            var firstIteration15 = true
                            while (true) {
                                if (firstIteration15) firstIteration15 = false
                                else {
                                    j++
                                }
                                if (!((j < rows.length()))) break

                                if ((id == rows.getJSONObject(j).optString("id"))) {
                                    old = rows.getJSONObject(j)
                                    at = j
                                    break
                                }
                            }
                        }
                        var expected = patch.optJSONObject("_sync_base")
                        if ((expected == null)) {
                            expected = patch.optJSONObject("_mcp_base")
                        }
                        if ((patch.optBoolean("_mcp_create") && (old != null))) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Die neue ID existiert bereits; keine Überschreibung.",
                            )
                        }
                        if (
                            (((kind == "appointment") && (expected != null)) &&
                                (((old == null) ||
                                    !((PixelMcpWriteTools.hash(expected) ==
                                        AppointmentRules.hash(old))))))
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Termin wurde seit dem Ausgangsstand verändert. Beide Varianten bleiben erhalten.",
                            )
                        }
                        var merge: Boolean = false
                        if ((expected != null)) {
                            try {

                                compare(expected, old)
                            } catch (conflict: PixelMcpWriteTools.Rejected) {
                                if (
                                    ((patch.optBoolean("_sync_field_merge") &&
                                        !(patch.has("_mcp_base"))) &&
                                        Arrays.asList("customer", "order", "worklog", "photo")
                                            .contains(kind))
                                ) {
                                    merge = true
                                } else {
                                    throw conflict
                                }
                            }
                        }
                        var merged =
                            (if ((old == null)) JSONObject() else JSONObject(old.toString()))
                        var sanitized = clean(patch)
                        if ((kind == "photo")) {
                            sanitized.remove("data")
                            sanitized.remove("full_data")
                        }
                        run {
                            var keys = sanitized.keys()
                            var firstIteration16 = true
                            while (true) {
                                if (firstIteration16) firstIteration16 = false else {}
                                if (!(keys.hasNext())) break

                                var key = keys.next()
                                merged.put(key, sanitized.get(key))
                            }
                        }
                        merged.put("id", id)
                        if (merge) {
                            merged = SyncActionOrder.merge(expected, patch, old, kind)
                        }
                        if (
                            (((old != null) && (expected == null)) &&
                                !((PixelMcpWriteTools.hash(merged) ==
                                    PixelMcpWriteTools.hash(old))))
                        ) {
                            throw PixelMcpWriteTools.Rejected(
                                "WRITE_CONFLICT",
                                "Ältere Änderung ohne Vergleichsstand. Bitte manuell prüfen; vorhandene Daten bleiben erhalten.",
                            )
                        }
                        if (
                            (((old == null) && (kind == "customer")) &&
                                patch.optBoolean("allow_duplicate"))
                        ) {
                            merged.put("allow_duplicate", true)
                        }
                        validateChange(kind, merged, old, snapshot)
                        merged.remove("allow_duplicate")
                        if ((at < 0)) {
                            rows.put(merged)
                        } else {
                            rows.put(at, merged)
                        }
                        snapshot.put(group, rows)
                        seen.add(client)
                        hashes.put(client, digest)
                        for (target in resolving) {
                            var priorHash = OrderReviewRules.actionHash(all.get(target))
                            resolved.put(target, priorHash)
                            hashes.put(target, PixelMcpWriteTools.hash(all.get(target)))
                        }
                    } catch (error: Exception) {
                        failed.add(client)
                        errors.put(
                            JSONObject()
                                .put("client_id", client)
                                .put("kind", kind)
                                .put("code", errorCode(error))
                                .put("error", error.message)
                        )
                    }
                }
            }
            var active = JSONArray()
            run {
                var i: Int = 0
                var firstIteration18 = true
                while (true) {
                    if (firstIteration18) firstIteration18 = false
                    else {
                        i++
                    }
                    if (!((i < errors.length()))) break

                    if (!(resolved.has(errors.getJSONObject(i).optString("client_id")))) {
                        active.put(errors.getJSONObject(i))
                    }
                }
            }
            errors = active
            var remote = snapshot.optJSONArray("sync_errors")
            var remaining = JSONArray()
            run {
                var i: Int = 0
                var firstIteration19 = true
                while (true) {
                    if (firstIteration19) firstIteration19 = false
                    else {
                        i++
                    }
                    if (!(((remote != null) && (i < remote.length())))) break

                    if (
                        (!(resolved.has(remote.getJSONObject(i).optString("client_id"))) &&
                            !(seen.contains(remote.getJSONObject(i).optString("client_id"))))
                    ) {
                        remaining.put(remote.getJSONObject(i))
                    }
                }
            }
            snapshot.put("drive_resolved_actions", resolved).put("sync_errors", remaining)
            refreshDisplayFields(snapshot)
            var receipts = snapshot.optJSONObject("drive_action_receipts")
            if ((receipts == null)) {
                receipts = JSONObject()
            }
            run {
                var i: Int = 0
                var firstIteration20 = true
                while (true) {
                    if (firstIteration20) firstIteration20 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var action = actions.getJSONObject(i)
                    var id = action.getString("client_id")
                    if ((seen.contains(id) || resolved.has(id))) {
                        receipts.put(
                            id,
                            JSONObject()
                                .put("action_sha256", PixelMcpWriteTools.hash(action))
                                .put("review_sha256", OrderReviewRules.actionHash(action))
                                .put("kind", action.getString("kind"))
                                .put("state", (if (resolved.has(id)) "resolved" else "applied")),
                        )
                    }
                }
            }
            snapshot
                .put("drive_action_receipts", receipts)
                .put("drive_applied_ids", JSONArray(seen))
                .put("drive_action_hashes", hashes)
                .put("drive_journal_errors", errors)
            return errors
        }

        @JvmStatic
        @Throws(Exception::class)
        fun reviewActions(snapshot: JSONObject, actions: JSONArray): JSONArray {

            var failed = HashSet<String>()
            var accepted = ids(snapshot.optJSONArray("accepted_ids"))
            var resolved = snapshot.optJSONObject("drive_resolved_actions")
            for (key in arrayOf("sync_errors", "drive_journal_errors")) {
                var rows = snapshot.optJSONArray(key)
                run {
                    var i: Int = 0
                    var firstIteration21 = true
                    while (true) {
                        if (firstIteration21) firstIteration21 = false
                        else {
                            i++
                        }
                        if (!(((rows != null) && (i < rows.length())))) break

                        failed.add(rows.getJSONObject(i).optString("client_id"))
                    }
                }
            }
            var result = JSONArray()
            var legacyPhotoReview = JSONArray()
            run {
                var i: Int = 0
                var firstIteration22 = true
                while (true) {
                    if (firstIteration22) firstIteration22 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var action = actions.getJSONObject(i)
                    var client = action.getString("client_id")
                    if (
                        ((("photo" == action.optString("kind")) &&
                            ((action.getJSONObject("payload").has("data") ||
                                action.getJSONObject("payload").has("full_data")))) &&
                            failed.contains(client))
                    ) {
                        legacyPhotoReview.put(client)
                        continue
                    }
                    if (
                        (((!(("delete" == action.optString("kind"))) && failed.contains(client)) &&
                            !(accepted.contains(client))) &&
                            (((resolved == null) || !(resolved.has(client)))))
                    ) {
                        result.put(JSONObject(action.toString()))
                    }
                }
            }
            run {
                var i: Int = 0
                var firstIteration23 = true
                while (true) {
                    if (firstIteration23) firstIteration23 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var a = actions.getJSONObject(i)
                    var id = a.getString("client_id")
                    if (
                        (((("delete" == a.optString("kind")) && failed.contains(id)) &&
                            !(accepted.contains(id))) &&
                            (((resolved == null) || !(resolved.has(id)))))
                    ) {
                        result.put(JSONObject(a.toString()))
                    }
                }
            }
            snapshot.put("drive_legacy_photo_review_ids", legacyPhotoReview)
            return result
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

        private @JvmStatic @Throws(Exception::class) fun hashString(value: String): String {

            var bytes =
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            var out = StringBuilder()
            for (b in bytes) {
                out.append(String.format(Locale.ROOT, "%02x", (b.toInt() and 255)))
            }
            return out.toString()
        }

        private @JvmStatic fun errorCode(error: Exception): String {

            return (if (error is PixelMcpWriteTools.Rejected)
                ((error as PixelMcpWriteTools.Rejected)).code
            else
                (if (error is HttpTransport.Failure) ((error as HttpTransport.Failure)).code
                else (if (error is java.io.IOException) "IO_RETRY_REQUIRED" else "INVALID_ACTION")))
        }

        @JvmStatic
        fun componentReady(snapshot: JSONObject, kind: String): Boolean {

            var capabilities = snapshot.optJSONObject("capabilities")
            if (Arrays.asList("offer_validate", "offer_share", "offer_delivery").contains(kind)) {
                return ((capabilities != null) && capabilities.optBoolean("offer_workflow_v2"))
            }
            if ((kind == "appointment")) {
                return ((capabilities != null) && capabilities.optBoolean("appointments_v1"))
            }
            if ((kind == "purge_trash_v1")) {
                return ((capabilities != null) && capabilities.optBoolean("trash_empty_v1"))
            }
            if ((kind == "purge_v1")) {
                return ((capabilities != null) && capabilities.optBoolean("trash_purge_v1"))
            }
            if (Arrays.asList("offer", "offer_finalize", "offer_convert").contains(kind)) {
                return ((capabilities != null) && capabilities.optBoolean("offers_v1"))
            }
            if ((kind == "service_template")) {
                return ((capabilities != null) && capabilities.optBoolean("service_catalog_v1"))
            }
            return true
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
            if ((kind == "invoice")) {
                InvoiceDraftRules.normalize(row, old, snapshot)
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
