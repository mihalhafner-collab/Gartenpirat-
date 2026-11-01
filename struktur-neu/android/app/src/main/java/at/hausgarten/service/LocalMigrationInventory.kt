package at.hausgarten.service

import java.math.BigDecimal
import java.util.*
import org.json.*

internal class LocalMigrationInventory {
    fun interface Originals {
        @Throws(Exception::class) fun verified(kind: String, reference: JSONObject): Boolean

        @Throws(Exception::class)
        fun pendingDocument(reference: JSONObject): Boolean {

            return false
        }
    }

    companion object {
        @JvmField val FORMAT: String = "hausgarten.local-migration-inventory.v1"
        private val COLLECTIONS: Array<String> =
            arrayOf(
                "customers",
                "orders",
                "worklogs",
                "photos",
                "offers",
                "invoices",
                "appointments",
                "service_catalog",
            )

        @JvmStatic
        @Throws(Exception::class)
        fun inspect(state: JSONObject, originals: Originals): JSONObject {

            var originalHash = PixelMcpWriteTools.hash(state)
            var master = state.getJSONObject("master")
            var effective: JSONObject
            var projectionApplied: Boolean = true
            try {

                effective = SnapshotProjection.effective(state)
            } catch (invalid: IllegalArgumentException) {
                effective = JSONObject(master.toString())
                projectionApplied = false
            }
            var counts = JSONObject()
            var ids = JSONObject()
            var hashes = JSONObject()
            var blockers = JSONArray()
            var duplicateIds: Int = 0
            var orphanRelations: Int = 0
            var missingOriginals: Int = 0
            var verifiedOriginals: Int = 0
            var pendingDocuments: Int = 0
            var known = HashMap<String, MutableSet<String>>()
            for (key in COLLECTIONS) {
                var rows = effective.optJSONArray(key)
                if ((rows == null)) {
                    rows = JSONArray()
                }
                var seen = TreeSet<String>()
                run {
                    var i: Int = 0
                    var firstIteration51 = true
                    while (true) {
                        if (firstIteration51) firstIteration51 = false
                        else {
                            i++
                        }
                        if (!((i < rows.length()))) break

                        var id = rows.getJSONObject(i).optString("id")
                        if ((id.isEmpty() || !(seen.add(id)))) {
                            duplicateIds++
                        }
                    }
                }
                known.put(key, seen)
                counts.put(key, rows.length())
                ids.put(key, PixelMcpWriteTools.hash(JSONArray(seen)))
                hashes.put(key, PixelMcpWriteTools.hash(rows))
            }
            var hours = BigDecimal.ZERO
            var invoiceAmounts = BigDecimal.ZERO
            for (key in COLLECTIONS) {
                var rows = effective.optJSONArray(key)
                if ((rows == null)) {
                    continue
                }
                run {
                    var i: Int = 0
                    var firstIteration53 = true
                    while (true) {
                        if (firstIteration53) firstIteration53 = false
                        else {
                            i++
                        }
                        if (!((i < rows.length()))) break

                        var row = rows.getJSONObject(i)
                        for (relation in
                            arrayOf(
                                arrayOf("customer_id", "customers"),
                                arrayOf("billing_customer_id", "customers"),
                                arrayOf("order_id", "orders"),
                                arrayOf("appointment_id", "appointments"),
                                arrayOf("worklog_id", "worklogs"),
                            )) {
                            var target = row.optString(relation[0])
                            if (
                                (!(target.isEmpty()) &&
                                    !(known.get(relation[1])!!.contains(target)))
                            ) {
                                orphanRelations++
                            }
                        }
                        if ((key == "worklogs")) {
                            hours = hours.add(decimal(row, "hours"))
                        }
                        if ((key == "invoices")) {
                            invoiceAmounts =
                                invoiceAmounts.add(
                                    decimal(
                                        row,
                                        (if (row.has("gross_total")) "gross_total" else "amount"),
                                    )
                                )
                        }
                        if ((key == "invoices")) {
                            var links = row.optJSONArray("worklog_ids")
                            run {
                                var j: Int = 0
                                var firstIteration52 = true
                                while (true) {
                                    if (firstIteration52) firstIteration52 = false
                                    else {
                                        j++
                                    }
                                    if (!(((links != null) && (j < links.length())))) break

                                    if (!(known.get("worklogs")!!.contains(links.getString(j)))) {
                                        orphanRelations++
                                    }
                                }
                            }
                        }
                        var document: Boolean =
                            ((((key == "invoices") && IssuedInvoiceRules.isIssued(row))) ||
                                ((key == "offers") && !(row.optString("finalized_at").isEmpty())))
                        if (((key == "photos") || document)) {
                            var present: Boolean = false
                            try {

                                present =
                                    originals.verified(
                                        (if ((key == "photos")) "photo" else "document"),
                                        JSONObject(row.toString()),
                                    )
                            } catch (unavailable: Exception) {}

                            var pendingDocument: Boolean = false
                            if ((!(present) && (key == "invoices"))) {
                                try {

                                    pendingDocument =
                                        originals.pendingDocument(JSONObject(row.toString()))
                                } catch (invalidJob: Exception) {}
                            }
                            if (present) {
                                verifiedOriginals++
                            } else {
                                if (pendingDocument) {
                                    pendingDocuments++
                                } else {
                                    missingOriginals++
                                }
                            }
                        }
                    }
                }
            }
            var archivedRecords: Int = 0
            var archive = effective.optJSONObject("owner_archive")
            if ((archive != null)) {
                run {
                    var keys = archive.keys()
                    var firstIteration54 = true
                    while (true) {
                        if (firstIteration54) firstIteration54 = false else {}
                        if (!(keys.hasNext())) break

                        var entry = archive.optJSONObject(keys.next())
                        if (((entry == null) || entry.optBoolean("purged"))) {
                            continue
                        }
                        var row = entry.optJSONObject("record")
                        if ((row == null)) {
                            continue
                        }
                        archivedRecords++
                        var kind =
                            (if ((row.has("content_sha256") || row.has("filename"))) "photo"
                            else
                                (if (
                                    ((row.has("pdf_sha256") || row.has("pdf_storage_name")) ||
                                        row.has("document_job_id"))
                                )
                                    "document"
                                else ""))
                        if (!(kind.isEmpty())) {
                            var present: Boolean = false
                            var pendingDocument: Boolean = false
                            try {

                                present = originals.verified(kind, JSONObject(row.toString()))
                                if ((!(present) && (kind == "document"))) {
                                    pendingDocument =
                                        originals.pendingDocument(JSONObject(row.toString()))
                                }
                            } catch (unavailable: Exception) {}

                            if (present) {
                                verifiedOriginals++
                            } else {
                                if (pendingDocument) {
                                    pendingDocuments++
                                } else {
                                    missingOriginals++
                                }
                            }
                        }
                    }
                }
            }
            var pending: Int =
                (if ((state.optJSONArray("pending") == null)) 0
                else state.getJSONArray("pending").length())
            var reviews: Int = remainingHistoricalReviews(master, Collections.emptyMap())
            if (!(projectionApplied)) {
                blockers.put("PENDING_PROJECTION_REVIEW_REQUIRED")
            }
            if (
                ((PixelDriveState.ENGINE == master.optString("processing_engine")) ||
                    (LocalAuthorityRules.ENGINE == master.optString("processing_engine")))
            ) {
                try {

                    var actions =
                        DriveJournalRules.union(
                            (if ((state.optJSONArray("pending") == null)) JSONArray()
                            else state.getJSONArray("pending"))
                        )
                    var replay = JSONObject(master.toString())
                    var errors = DriveJournalRules.apply(replay, actions)
                    reviews =
                        (remainingHistoricalReviews(
                            master,
                            validatedLocalResolutions(master, actions, replay, errors),
                        ) + errors.length())
                } catch (invalid: IllegalArgumentException) {
                    blockers.put("PENDING_PROJECTION_REVIEW_REQUIRED")
                    reviews++
                }
            }
            if ((master.optInt("schema_version", 1) > 2)) {
                blockers.put("SCHEMA_NOT_SUPPORTED")
            }
            if ((duplicateIds > 0)) {
                blockers.put("DUPLICATE_IDS")
            }
            if ((orphanRelations > 0)) {
                blockers.put("RELATION_REVIEW_REQUIRED")
            }
            if ((missingOriginals > 0)) {
                blockers.put("LOCAL_ORIGINALS_INCOMPLETE")
            }
            if ((pending > 0)) {
                blockers.put("PENDING_ACTION_RECONCILIATION_REQUIRED")
            }
            if ((reviews > 0)) {
                blockers.put("EXISTING_CONFLICT_REVIEW_REQUIRED")
            }
            blockers
                .put("DATABASE_SETTINGS_AND_STAGED_FILES_INVENTORY_REQUIRED")
                .put("FULL_EXTERNAL_BACKUP_REQUIRED")
                .put("OLD_WRITER_RETIREMENT_PROOF_REQUIRED")
                .put("CURRENT_INVOICE_NUMBER_RECONCILIATION_REQUIRED")
            var session = state.optJSONObject("work_session")
            if (((session != null) && session.optBoolean("review_required"))) {
                blockers.put("SESSION_REVIEW_REQUIRED")
            }
            var result =
                JSONObject()
                    .put("format", FORMAT)
                    .put("source_schema_version", master.optInt("schema_version", 1))
                    .put("counts", counts)
                    .put("id_set_sha256", ids)
                    .put("collection_sha256", hashes)
                    .put("source_master_sha256", PixelMcpWriteTools.hash(master))
                    .put("pending_sha256", PixelMcpWriteTools.hash(state.optJSONArray("pending")))
                    .put("projection_applied", projectionApplied)
                    .put("inventory_scope", "effective_business_records_and_archive_references")
                    .put("full_device_inventory", false)
                    .put("archived_records", archivedRecords)
                    .put("pending_actions", pending)
                    .put("historical_reviews", reviews)
                    .put("duplicate_ids", duplicateIds)
                    .put("orphan_relations", orphanRelations)
                    .put("verified_local_originals", verifiedOriginals)
                    .put("missing_local_originals", missingOriginals)
                    .put("pending_document_jobs", pendingDocuments)
                    .put("historical_hours_decimal", hours.toPlainString())
                    .put("historical_invoice_amounts_decimal", invoiceAmounts.toPlainString())
                    .put(
                        "session_state",
                        (if ((session == null)) "UNKNOWN"
                        else session.optString("state", "UNKNOWN")),
                    )
                    .put(
                        "session_revision_sha256",
                        (if ((session == null)) "" else session.optString("revision_hash")),
                    )
                    .put("blockers", blockers)
                    .put("local_authority_activated", false)
                    .put("invoice_issue_allowed", false)
                    .put("state_changed", false)
                    .put("external_backup_verified", false)
                    .put("remote_upload_verified", false)
            if (!((originalHash == PixelMcpWriteTools.hash(state)))) {
                throw IllegalStateException(
                    "Migrationsinventar darf den Ausgangsstand nicht verändern."
                )
            }
            return result
        }

        private @JvmStatic @Throws(Exception::class) fun remainingHistoricalReviews(
            master: JSONObject,
            verified: MutableMap<String, JSONObject>,
        ): Int {

            var remaining: Int = 0
            for (key in arrayOf("sync_errors", "drive_journal_errors", "drive_review_actions")) {
                var entries = master.optJSONArray(key)
                run {
                    var i: Int = 0
                    var firstIteration55 = true
                    while (true) {
                        if (firstIteration55) firstIteration55 = false
                        else {
                            i++
                        }
                        if (!(((entries != null) && (i < entries.length())))) break

                        var entry = entries.optJSONObject(i)
                        var original =
                            (if ((entry == null)) null
                            else verified.get(entry.optString("client_id")))
                        var fulfilled: Boolean = (original != null)
                        if ((fulfilled && (key == "drive_review_actions"))) {
                            fulfilled =
                                (OrderReviewRules.actionHash(original) ==
                                    OrderReviewRules.actionHash(entry))
                        } else {
                            if ((fulfilled && entry.has("kind"))) {
                                fulfilled =
                                    (original!!.getString("kind") == entry.optString("kind"))
                            }
                        }
                        if (!(fulfilled)) {
                            remaining++
                        }
                    }
                }
            }
            return remaining
        }

        private @JvmStatic @Throws(Exception::class) fun validatedLocalResolutions(
            master: JSONObject,
            actions: JSONArray,
            replay: JSONObject,
            errors: JSONArray,
        ): MutableMap<String, JSONObject> {

            var all = HashMap<String, JSONObject>()
            var verified = HashMap<String, JSONObject>()
            var failed = HashSet<String>()
            run {
                var i: Int = 0
                var firstIteration56 = true
                while (true) {
                    if (firstIteration56) firstIteration56 = false
                    else {
                        i++
                    }
                    if (!((i < actions.length()))) break

                    var action = actions.getJSONObject(i)
                    all.put(action.getString("client_id"), action)
                }
            }
            run {
                var i: Int = 0
                var firstIteration57 = true
                while (true) {
                    if (firstIteration57) firstIteration57 = false
                    else {
                        i++
                    }
                    if (!((i < errors.length()))) break

                    failed.add(errors.getJSONObject(i).optString("client_id"))
                }
            }
            var history = replay.optJSONObject("owner_conflict_history")
            var resolved = replay.optJSONObject("drive_resolved_actions")
            var receipts = replay.optJSONObject("drive_action_receipts")
            if ((((history == null) || (resolved == null)) || (receipts == null))) {
                return verified
            }
            var ordered = SyncActionOrder.order(actions)
            var prefix = JSONArray()
            run {
                var i: Int = 0
                var firstIteration59 = true
                while (true) {
                    if (firstIteration59) firstIteration59 = false
                    else {
                        i++
                    }
                    if (!((i < ordered.length()))) break

                    var action = ordered.getJSONObject(i)
                    prefix.put(action)
                    if (!(("conflict_resolution" == action.optString("kind")))) {
                        continue
                    }
                    var request = action.getString("client_id")
                    var target = action.getJSONObject("payload").optString("client_id")
                    if (
                        ((target.isEmpty() || failed.contains(target)) || failed.contains(request))
                    ) {
                        continue
                    }
                    try {

                        var before = JSONArray()
                        run {
                            var j: Int = 0
                            var firstIteration58 = true
                            while (true) {
                                if (firstIteration58) firstIteration58 = false
                                else {
                                    j++
                                }
                                if (!((j < (prefix.length() - 1)))) break

                                before.put(prefix.get(j))
                            }
                        }
                        var checked = JSONObject(master.toString())
                        DriveJournalRules.apply(checked, before)
                        for (key in arrayOf("sync_errors", "drive_journal_errors")) {
                            if (master.has(key)) {
                                checked.put(key, JSONArray(master.getJSONArray(key).toString()))
                            }
                        }
                        OwnerJournalRules.apply(
                            checked,
                            action,
                            all,
                            JSONObject(checked.getJSONObject("drive_resolved_actions").toString()),
                        )
                        var proof =
                            checked.getJSONObject("owner_conflict_history").getJSONObject(target)
                        var original = proof.getJSONObject("original_action")
                        var hash = OrderReviewRules.actionHash(original)
                        var applied = history.getJSONObject(target)
                        var receipt = receipts.getJSONObject(request)
                        if (
                            (((((((((((!((hash == resolved.optString(target))) ||
                                !((request == applied.optString("resolution_action")))) ||
                                !((hash == applied.optString("action_hash")))) ||
                                !((hash ==
                                    OrderReviewRules.actionHash(
                                        applied.getJSONObject("original_action")
                                    )))) ||
                                !((OwnerJournalRules.version(proof.optJSONObject("previous")) ==
                                    OwnerJournalRules.version(
                                        applied.optJSONObject("previous")
                                    )))) ||
                                !((proof.getString("resolution") ==
                                    applied.optString("resolution")))) ||
                                !((proof.getString("reason") == applied.optString("reason")))) ||
                                !((proof.getString("resolved_at") ==
                                    applied.optString("resolved_at")))) ||
                                !(("conflict_resolution" == receipt.optString("kind")))) ||
                                !(("applied" == receipt.optString("state")))) ||
                                !((PixelMcpWriteTools.hash(action) ==
                                    receipt.optString("action_sha256")))) ||
                                !((OrderReviewRules.actionHash(action) ==
                                    receipt.optString("review_sha256"))))
                        ) {
                            continue
                        }
                        verified.put(target, original)
                    } catch (unproven: Exception) {}
                }
            }
            return verified
        }

        private @JvmStatic fun decimal(row: JSONObject, key: String): BigDecimal {

            var value = row.opt(key)
            if ((((value == null) || (value == JSONObject.NULL)) || value.toString().isEmpty())) {
                return BigDecimal.ZERO
            }
            return BigDecimal(value.toString())
        }
    }
}
