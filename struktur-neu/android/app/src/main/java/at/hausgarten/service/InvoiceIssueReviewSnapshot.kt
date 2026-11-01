package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import java.util.*
import org.json.*

/** Atomic, read-only projection for the UI review and the commit-time comparison. */
internal object InvoiceIssueReviewSnapshot {
    @JvmStatic
    @Throws(Exception::class)
    fun capture(store: PixelLocalStore, invoiceId: String): JSONObject {
        var database = store!!.getReadableDatabase()
        database!!.beginTransaction()
        try {
            var master = store!!.rawSnapshot("{}")
            PixelLocalStore.removeSnapshotPhotoBinaries(master)
            PixelDriveState.prepare(master)
            store!!.seedServiceCatalog(master)
            LocalTestCleanup.overlay(store, master)
            var authority: JSONObject = (LocalAuthorityGuard(store!!.appContext()))!!.current()
            if (!(authority!!.optString("mode"))!!.equals("LEGACY_DRIVE")) {
                (master!!.put("processing_engine", LocalAuthorityRules.ENGINE))!!.put(
                    "local_contract_version",
                    LocalAuthorityRules.CONTRACT,
                )
            }
            var pending =
                DriveJournalRules.union(
                    store!!.pendingActions(),
                    master!!.optJSONArray("drive_review_actions"),
                )
            var effective =
                SnapshotProjection.effective(
                    ((JSONObject())!!.put("master", master))!!.put("pending", pending)
                )
            var blockers: JSONArray = JSONArray()
            try {
                LocalBillingProjection.apply(database, effective)
            } catch (invalid: Exception) {
                effective!!.remove("local_billing_sources")
                blockers!!.put(
                    ((JSONObject())!!.put("code", "LOCAL_BILLING_SOURCE_REVIEW_REQUIRED"))!!.put(
                        "message",
                        "Der aktuelle lokale Leistungs- und Restmengenstand benötigt Prüfung. Keine neue Rechnung freigeben.",
                    )
                )
            }
            var dependencies: JSONObject =
                (((((JSONObject())!!.put("formatVersion", 1))!!.put("authority", authority))!!.put(
                            "blockers",
                            blockers,
                        ))!!
                        .put(
                            "worklog_allocations_by_invoice",
                            ledger(database, "local_invoice_sources:", blockers),
                        ))!!
                    .put(
                        "material_allocations_by_invoice",
                        ledger(database, "local_invoice_material_sources:", blockers),
                    )
            var movements: JSONObject = JSONObject()
            var reversals: JSONObject = JSONObject()
            var invoice =
                OwnerJournalRules.findOrNull(effective!!.optJSONArray("invoices"), invoiceId)
            describeOriginal(invoice, blockers)
            if (((invoice !== null) && !(invoice!!.optString("correction_of"))!!.isEmpty())) {
                describeOriginal(
                    OwnerJournalRules.findOrNull(
                        effective!!.optJSONArray("invoices"),
                        invoice!!.getString("correction_of"),
                    ),
                    blockers,
                )
            }
            var positions =
                (if (
                    ((invoice === null) ||
                        (invoice!!.optString("document_type"))!!.equals("cancellation"))
                )
                    null
                else invoice!!.optJSONArray("positions"))
            var ids: MutableSet<String> = TreeSet()
            run {
                var i: Int = 0
                while (((positions !== null) && (i < positions!!.length()))) {
                    var position = positions!!.optJSONObject(i)
                    if (
                        (((position !== null) &&
                            (position!!.optString("source_type"))!!.equals("material_movement")) &&
                            !(position!!.optString("source_id"))!!.isEmpty())
                    ) {
                        ids!!.add(position!!.getString("source_id"))
                    }

                    i++
                }
            }
            for (id in ids!!) {
                var raw =
                    DatabaseBackupSnapshot.ownerValue(
                        database,
                        ("revier:v1:entity:material_movement:" + id),
                        "",
                    )
                if (!raw!!.isEmpty()) {
                    try {
                        var movement: JSONObject = JSONObject(raw)
                        RevierRules.version(movement)
                        if (!id!!.equals(movement!!.getString("id"))) {
                            throw IllegalArgumentException()
                        }
                        movements!!.put(id, movement)
                    } catch (invalid: Exception) {
                        blockers!!.put(
                            ((((JSONObject())!!.put("code", "INVOICE_MATERIAL_SOURCE_INVALID"))!!
                                        .put(
                                            "message",
                                            "Eine Materialquelle ist beschädigt oder benötigt eine neuere Appversion. Die Einsatzdokumentation prüfen.",
                                        ))!!
                                    .put("entity_type", "material_movement"))!!
                                .put("id", id)
                        )
                    }
                }
                reversals!!.put(
                    id,
                    DatabaseBackupSnapshot.ownerValue(database, ("revier:v1:reversed:" + id), ""),
                )
            }
            (dependencies!!.put("material_movements", movements))!!.put(
                "material_reversals",
                reversals,
            )
            var result = InvoiceIssueReviewRules.capture(effective, invoiceId, dependencies)
            database!!.setTransactionSuccessful()
            return result
        } finally {
            database!!.endTransaction()
        }
    }

    private @JvmStatic @Throws(Exception::class) fun describeOriginal(
        invoice: JSONObject?,
        blockers: JSONArray,
    ): Unit {
        if (((invoice === null) || (invoice!!.optString("pdf_base64"))!!.isEmpty())) {
            return
        }
        try {
            LegacyPdfAttachment.describe(invoice)
            invoice!!.remove("pdf_base64")
        } catch (invalid: Exception) {
            blockers!!.put(
                ((((JSONObject())!!.put("code", "INVOICE_ORIGINAL_REVIEW_REQUIRED"))!!.put(
                            "message",
                            "Das vorhandene Original-PDF ist nicht zuverlässig lesbar. Originalnachweis prüfen; keine Ersatzdatei erzeugen.",
                        ))!!
                        .put("entity_type", "invoice"))!!
                    .put("id", invoice!!.optString("id"))
            )
        }
    }

    private @JvmStatic @Throws(Exception::class) fun ledger(
        database: SQLiteDatabase,
        prefix: String,
        blockers: JSONArray,
    ): JSONObject {
        var result: JSONObject = JSONObject()
        run {
            database!!
                .rawQuery(
                    "SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",
                    arrayOf(prefix, (prefix + '￿')),
                )
                .use { rows ->
                    while (rows!!.moveToNext()) {
                        var id = (rows!!.getString(0))!!.substring(prefix!!.length)
                        try {
                            result!!.put(id, JSONObject(rows!!.getString(1)))
                        } catch (invalid: Exception) {
                            result!!.put(id, JSONObject.NULL)
                            blockers!!.put(
                                ((((JSONObject())!!.put("code", "INVOICE_SOURCE_LEDGER_INVALID"))!!
                                            .put(
                                                "message",
                                                "Eine lokale Leistungszuordnung ist beschädigt. Vor einer neuen Ausgabe den Belegbezug prüfen.",
                                            ))!!
                                        .put("entity_type", "invoice"))!!
                                    .put("id", id)
                            )
                        }
                    }
                }
        }
        return result
    }
}
