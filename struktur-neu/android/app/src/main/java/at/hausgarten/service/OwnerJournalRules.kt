package at.hausgarten.service

import org.json.*
import java.util.*

/** Additive owner journal protocol. Old journals and immutable receipts survive. */
class OwnerJournalRules {
    companion object {
        @JvmField val KINDS: Array<String> = arrayOf("purge_v1", "purge_trash_v1", "archive", "restore", "conflict_resolution", "business_settings")
        @JvmStatic fun handles(kind: String): Boolean {
            return (Arrays.asList(*KINDS).contains(kind) || OfferJournalRules.handles(kind))
        }
        @Throws(Exception::class)
        @JvmStatic fun archives(snapshot: JSONObject): JSONObject {
            var a: JSONObject? = snapshot.optJSONObject("owner_archive")
            if (((a == null))) {
                a = JSONObject()
                snapshot.put("owner_archive", a)
            }
            return a
        }
        @JvmStatic fun group(entity: String): String {
            return (if (entity.equals("invoice")) "invoices" else DriveJournalRules.collection(entity))
        }
        @Throws(Exception::class)
        @JvmStatic fun findOrNull(rows: JSONArray?, id: String): JSONObject? {
            run {
                var i: Int = 0
                while (((rows != null) && (i < rows!!.length()))) {
                    if ((id.equals(rows!!.getJSONObject(i).optString("id")))) {
                        return rows!!.getJSONObject(i)
                    }
                    i++
                }
            }
            return null
        }
        @Throws(Exception::class)
        @JvmStatic fun version(row: JSONObject?): String {
            if (((row == null))) {
                return PixelMcpWriteTools.hash(JSONObject.NULL)
            }
            if ((row!!.has("starts_at"))) {
                return AppointmentRules.hash(row)
            }
            if ((row!!.has("id"))) {
                if ((((row!!.has("offer_number") || row!!.has("pricing_mode")) || ((row!!.has("unit") && row!!.has("default_price")))))) {
                    return PixelMcpWriteTools.hash(DriveJournalRules.clean(row))
                }
                var kind: String = (if (row!!.has("name")) "customer" else (if (row!!.has("activity")) "worklog" else (if (row!!.has("title")) "order" else (if (((row!!.has("invoice_number") || row!!.has("positions")) || row!!.has("issued_at"))) "invoice" else "photo"))))
                return PixelMcpWriteTools.hash(DriveJournalRules.guard(row, kind))
            }
            return PixelMcpWriteTools.hash(DriveJournalRules.clean(row))
        }
        @Throws(Exception::class)
        @JvmStatic fun checkHash(row: JSONObject?, expected: String) {
            if ((!version(row).equals(expected))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Der geprüfte Stand hat sich verändert. Beide Varianten bleiben erhalten.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun apply(snapshot: JSONObject, action: JSONObject, all: MutableMap<String, JSONObject>, resolved: JSONObject) {
            var kind: String = action.getString("kind")
            var p: JSONObject = action.getJSONObject("payload")
            if ((kind.equals(TrashPurgeBatch.KIND))) {
                TrashPurgeBatch.apply(snapshot, action)
                return
            }
            if ((kind.equals("purge_v1"))) {
                TrashRules.purge(snapshot, action)
                return
            }
            if ((OfferJournalRules.handles(kind))) {
                OfferJournalRules.apply(snapshot, action)
                return
            }
            if ((kind.equals("business_settings"))) {
                var current: JSONObject? = snapshot.optJSONObject("business_settings")
                if (((current == null))) {
                    throw IllegalArgumentException("Fachliche Einstellungen fehlen; Pixel-Datenstand erneut prüfen.")
                }
                if ((!PixelMcpWriteTools.hash(current).equals(p.getString("expected_record_hash")))) {
                    throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Fachliche Einstellungen wurden geändert.")
                }
                PixelMcpOwnerTools.validate("update_business_settings", JSONObject().put("request_id", "journal-settings").put("expected_record_hash", p.getString("expected_record_hash")).put("updates", p.getJSONObject("updates")))
                var updated: JSONObject = JSONObject(current!!.toString())
                var changes: JSONObject = p.getJSONObject("updates")
                run {
                    var keys: MutableIterator<String> = changes.keys()
                    while (keys.hasNext()) {
                        var key: String = keys.next()
                        var value: Any = changes.get(key)
                        updated.put(key, (if (value is Boolean) ((if (value) "1" else "0")) else value.toString()))
                    }
                }
                snapshot.put("business_settings", updated)
                var issuer: JSONObject? = snapshot.optJSONObject("invoice_issuer")
                if (((issuer == null))) {
                    issuer = JSONObject()
                }
                for (key in PixelMcpOwnerTools.BUSINESS_KEYS) {
                    if ((!key.startsWith("invoice_"))) {
                        issuer!!.put(key, updated.optString(key))
                    }
                }
                snapshot.put("invoice_issuer", issuer)
                return
            }
            if ((kind.equals("conflict_resolution"))) {
                var target: String = p.getString("client_id")
                var prior: JSONObject? = all.get(target)
                var embedded: JSONObject? = p.optJSONObject("_original_action")
                if (((prior == null))) {
                    var knownFailure: Boolean = false
                    for (key in arrayOf("sync_errors", "drive_journal_errors")) {
                        var failures: JSONArray? = snapshot.optJSONArray(key)
                        run {
                            var i: Int = 0
                            while (((failures != null) && (i < failures!!.length()))) {
                                if ((target.equals(failures!!.getJSONObject(i).optString("client_id")))) {
                                    knownFailure = true
                                    break
                                }
                                i++
                            }
                        }
                    }
                    if ((knownFailure)) {
                        prior = embedded
                    }
                }
                if ((((((prior == null) || !target.equals(prior!!.optString("client_id"))) || !OrderReviewRules.actionHash(prior).equals(p.getString("expected_action_hash"))) || resolved.has(target)))) {
                    throw PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT", "Die ursprüngliche Konfliktaktion wurde verändert, ist nicht mehr nachweisbar oder bereits geprüft.")
                }
                if ((((embedded != null) && !OrderReviewRules.actionHash(embedded).equals(p.getString("expected_action_hash"))))) {
                    throw PixelMcpWriteTools.Rejected("ACTION_ID_CONFLICT", "Der eingebettete Konfliktbeleg stimmt nicht mit dem geprüften Hash überein.")
                }
                for (errorKey in arrayOf("sync_errors", "drive_journal_errors")) {
                    var failures: JSONArray? = snapshot.optJSONArray(errorKey)
                    run {
                        var i: Int = 0
                        while (((failures != null) && (i < failures!!.length()))) {
                            var failure: JSONObject = failures!!.getJSONObject(i)
                            if ((((target.equals(failure.optString("client_id")) && !Arrays.asList("WRITE_CONFLICT", "DELETE_REVIEW_REQUIRED").contains(failure.optString("code"))) && !(("keep_current".equals(p.optString("resolution")) && DeletionRules.cancelable(prior, failure.optString("code"))))))) {
                                throw PixelMcpWriteTools.Rejected("CONFLICT_RESOLUTION_NOT_AVAILABLE", "Infrastrukturfehler darf nicht als Geschäftsentscheidung quittiert werden.")
                            }
                            i++
                        }
                    }
                }
                var legacyDelete: Boolean = "delete".equals(prior!!.getString("kind"))
                var purgeCancel: Boolean = "purge_v1".equals(prior!!.getString("kind"))
                var bulkCancel: Boolean = TrashPurgeBatch.KIND.equals(prior!!.getString("kind"))
                var archiveCancel: Boolean = (("archive".equals(prior!!.getString("kind")) || purgeCancel) || bulkCancel)
                if (((archiveCancel && !"keep_current".equals(p.optString("resolution"))))) {
                    throw PixelMcpWriteTools.Rejected("CONFLICT_RESOLUTION_NOT_AVAILABLE", "Löschauftrag nur zurücknehmen oder anhand des aktuellen Stands neu prüfen.")
                }
                var entity: String = (if (bulkCancel) "" else (if (((legacyDelete || archiveCancel))) prior!!.getJSONObject("payload").getString("entity") else prior!!.getString("kind")))
                var collection: String = DriveJournalRules.collection(entity)
                if (((!bulkCancel && collection.isEmpty()))) {
                    throw IllegalArgumentException("Diesen Vorgang fachlich separat prüfen.")
                }
                var patch: JSONObject = prior!!.getJSONObject("payload")
                var current: JSONObject? = (if (bulkCancel) TrashPurgeBatch.currentGuard(snapshot) else (if (purgeCancel) archives(snapshot).optJSONObject((("" + entity + ":") + patch.optString("id"))) else findOrNull(snapshot.getJSONArray(collection), patch.optString("id"))))
                checkHash(current, p.getString("expected_current_hash"))
                var history: JSONObject? = snapshot.optJSONObject("owner_conflict_history")
                if (((history == null))) {
                    history = JSONObject()
                }
                if ((!p.optString("resolution").equals("keep_current"))) {
                    if ((!p.optString("resolution").equals("apply_proposed"))) {
                        throw IllegalArgumentException("Ungültige Konfliktentscheidung.")
                    }
                    if ((legacyDelete)) {
                        if (((current != null))) {
                            var archive: JSONObject = JSONObject().put("entity", entity).put("id", current!!.getString("id")).put("expected_record_hash", version(current)).put("_record_base", DriveJournalRules.guard(current, entity)).put("_archive_record", current).put("updated_at", action.optString("created_at", java.time.Instant.now().toString()))
                            apply(snapshot, JSONObject().put("kind", "archive").put("client_id", ("" + action.getString("client_id") + "-archive")).put("created_at", action.optString("created_at")).put("payload", archive), all, resolved)
                        }
                    } else {
                        var merged: JSONObject = (if ((current == null)) JSONObject() else DriveJournalRules.clean(current))
                        var clean: JSONObject = DriveJournalRules.clean(patch)
                        run {
                            var keys: MutableIterator<String> = clean.keys()
                            while (keys.hasNext()) {
                                var key: String = keys.next()
                                merged.put(key, clean.get(key))
                            }
                        }
                        DriveJournalRules.validateChange(entity, merged, current, snapshot)
                        replace(snapshot, collection, merged.getString("id"), merged)
                    }
                }
                history!!.put(target, JSONObject().put("original_action", prior).put("previous", (if ((current == null)) JSONObject.NULL else current)).put("resolution", p.getString("resolution")).put("action_hash", p.getString("expected_action_hash")).put("reason", p.optString("reason", "explicit_owner_decision")).put("resolved_at", action.optString("created_at")).put("resolution_action", action.getString("client_id")))
                snapshot.put("owner_conflict_history", history)
                resolved.put(target, OrderReviewRules.actionHash(prior))
                return
            }
            var entity: String = (if (kind.equals("invoice_payment")) "invoice" else p.getString("entity"))
            var id: String = p.getString("id")
            var collection: String = group(entity)
            if ((collection.isEmpty())) {
                throw IllegalArgumentException("Unbekannter Archivbereich.")
            }
            var rows: JSONArray = snapshot.getJSONArray(collection)
            var current: JSONObject? = findOrNull(rows, id)
            if ((kind.equals("invoice_payment"))) {
                checkHash(current, p.getString("expected_record_hash"))
                if (((((current == null) || current!!.optString("issued_at").isEmpty()) || "Storniert".equals(current!!.optString("status"))))) {
                    throw IllegalArgumentException("Nur ausgestellte, gültige Rechnungen können als bezahlt/offen markiert werden.")
                }
                var updated: JSONObject = JSONObject(current!!.toString()).put("status", (if (p.getBoolean("paid")) "Bezahlt" else "Gestellt")).put("updated_at", p.getString("updated_at"))
                replace(snapshot, collection, id, updated)
                return
            }
            var archive: JSONObject = archives(snapshot)
            var key: String = (("" + entity + ":") + id)
            if ((kind.equals("restore"))) {
                var saved: JSONObject? = archive.optJSONObject(key)
                if (((saved == null))) {
                    throw IllegalArgumentException("Archiveintrag fehlt.")
                }
                checkHash(saved, p.getString("expected_record_hash"))
                if ((saved!!.optBoolean("purged"))) {
                    throw IllegalArgumentException("Endgültig gelöschte Einträge sind nicht wiederherstellbar.")
                }
                var original: JSONObject = saved!!.getJSONObject("record")
                if (((entity.equals("customer") || entity.equals("service_template")))) {
                    if ((((current == null) || ((current!!.optBoolean("active", true) && (current!!.optInt("active", 1) != 0)))))) {
                        throw IllegalArgumentException("Der archivierte Kunde wurde inzwischen geändert.")
                    }
                    checkHash(current, saved!!.getString("archived_record_hash"))
                } else {
                    if (((current != null))) {
                        throw IllegalArgumentException("Die Original-ID ist bereits belegt.")
                    }
                }
                if ((!entity.equals("invoice"))) {
                    DriveJournalRules.validateChange(entity, original, null, snapshot)
                }
                replace(snapshot, collection, id, original)
                TrashRules.restoreLinks(snapshot, saved)
                archive.remove(key)
                return
            }
            checkHash(current, p.getString("expected_record_hash"))
            if (((current == null))) {
                throw IllegalArgumentException("Der Eintrag fehlt.")
            }
            validateArchive(entity, current, snapshot)
            if ((archive.has(key))) {
                throw IllegalArgumentException("Für diese ID besteht bereits ein Archivstand.")
            }
            var saved: JSONObject? = JSONObject().put("record", current).put("archived_at", action.optString("created_at")).put("archive_action", action.getString("client_id"))
            if ((p.has("deletion_policy"))) {
                saved!!.put("deletion_policy", p.getString("deletion_policy"))
            }
            var related: JSONArray = TrashRules.related(snapshot, entity, id)
            if ((("recoverable_v2".equals(p.optString("deletion_policy")) && !PixelMcpWriteTools.hash(related).equals(p.getString("expected_photo_links_hash"))))) {
                throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Fotozuordnungen inzwischen geändert.")
            }
            if ((((related.length() > 0) && (((entity.equals("order") || entity.equals("worklog")) || "recoverable_v2".equals(p.optString("deletion_policy"))))))) {
                if ((!"recoverable_v2".equals(p.optString("deletion_policy")))) {
                    throw IllegalArgumentException("Fotozuordnungen mit aktuellem Löschdialog erneut prüfen; alte Aktion bleibt erhalten.")
                }
                if ((!PixelMcpWriteTools.hash(related).equals(p.getString("expected_photo_links_hash")))) {
                    throw PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Fotozuordnungen inzwischen geändert. Nichts entfernt.")
                }
                saved!!.put("photo_links", TrashRules.detachRelated(snapshot, entity, id, p.getString("updated_at")))
            }
            if (((entity.equals("customer") || entity.equals("service_template")))) {
                var inactive: JSONObject = JSONObject(current!!.toString()).put("active", (if (entity.equals("customer")) 0 else false)).put("updated_at", p.getString("updated_at"))
                replace(snapshot, collection, id, inactive)
                saved!!.put("archived_record_hash", version(inactive))
            } else {
                replace(snapshot, collection, id, null)
            }
            TrashRules.detachOptionalParent(snapshot, entity, id, p.getString("updated_at"))
            archive.put(key, saved)
        }
        @Throws(Exception::class)
        @JvmStatic fun validateArchive(entity: String, row: JSONObject, snapshot: JSONObject) {
            if (entity == "order" || entity == "appointment") {
                TrashRules.requireDeletableParent(snapshot, entity, row.getString("id"))
                return
            }
            if ((entity.equals("customer"))) {
                for (group in arrayOf("customers", "orders", "worklogs")) {
                    var records: JSONArray? = snapshot.optJSONArray(group)
                    run {
                        var i: Int = 0
                        while (((records != null) && (i < records!!.length()))) {
                            var r: JSONObject = records!!.getJSONObject(i)
                            var key: String = (if (group.equals("customers")) "default_billing_customer_id" else "billing_customer_id")
                            if (((row.getString("id").equals(r.optString(key)) && !row.getString("id").equals(r.optString((if (group.equals("customers")) "id" else "customer_id")))))) {
                                throw IllegalArgumentException("Dieser Kunde wird als Rechnungsempfänger verwendet. Zuerst die Abrechnungszuordnungen prüfen.")
                            }
                            i++
                        }
                    }
                }
                return
            }
            if (((entity.equals("offer") && !OfferRules.editable(row)))) {
                throw IllegalArgumentException("Finalisierte oder angenommene Angebote bleiben als Beleg erhalten.")
            }
            if ((entity.equals("service_template"))) {
                return
            }
            var id: String = row.getString("id")
            if ((entity.equals("invoice"))) {
                EntityProtectionRules.validateInvoiceDelete(row)
            }
            for (collection in arrayOf("orders", "worklogs", "photos", "invoices", "offers")) {
                var rows: JSONArray = snapshot.getJSONArray(collection)
                run {
                    var i: Int = 0
                    while ((i < rows.length())) {
                        var other: JSONObject = rows.getJSONObject(i)
                        if (((!collection.equals("photos") && (((entity.equals("order") && id.equals(other.optString("order_id"))) || (entity.equals("worklog") && id.equals(other.optString("worklog_id")))))))) {
                            throw IllegalArgumentException("Verknüpfte Daten zuerst prüfen; nichts entfernt.")
                        }
                        if (((entity.equals("worklog") && collection.equals("invoices")))) {
                            var refs: JSONArray? = other.optJSONArray("worklog_ids")
                            var positions: JSONArray? = other.optJSONArray("positions")
                            run {
                                var j: Int = 0
                                while (((refs != null) && (j < refs!!.length()))) {
                                    if ((id.equals(refs!!.optString(j)))) {
                                        throw IllegalArgumentException("Die Arbeit ist mit einer Rechnung verknüpft.")
                                    }
                                    j++
                                }
                            }
                            run {
                                var j: Int = 0
                                while (((positions != null) && (j < positions!!.length()))) {
                                    if ((id.equals(positions!!.getJSONObject(j).optString("worklog_id")))) {
                                        throw IllegalArgumentException("Die Arbeit ist mit einer Rechnung verknüpft.")
                                    }
                                    j++
                                }
                            }
                        }
                        i++
                    }
                }
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun replace(snapshot: JSONObject, collection: String, id: String, replacement: JSONObject?) {
            var rows: JSONArray = snapshot.getJSONArray(collection)
            var next: JSONArray = JSONArray()
            var found: Boolean = false
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    var old: JSONObject = rows.getJSONObject(i)
                    if ((id.equals(old.optString("id")))) {
                        found = true
                        if (((replacement != null))) {
                            next.put(replacement)
                        }
                    } else {
                        next.put(old)
                    }
                    i++
                }
            }
            if (((!found && (replacement != null)))) {
                next.put(replacement)
            }
            snapshot.put(collection, next)
        }
    }
}
