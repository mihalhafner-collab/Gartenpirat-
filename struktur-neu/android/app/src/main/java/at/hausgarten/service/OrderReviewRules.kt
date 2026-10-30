package at.hausgarten.service

import org.json.*
import java.util.*

/** Read-only inspection followed by an explicit, guarded order correction. */
class OrderReviewRules {
    companion object {
        @JvmStatic fun quote(value: String): String {
            var out: StringBuilder = StringBuilder("\"")
            run {
                var i: Int = 0
                while ((i < value.length)) {
                    var c: Char = value[i]
                    when ((c)) {
                        '"' -> {
                            out.append("\\\"")
                        }
                        '\\' -> {
                            out.append("\\\\")
                        }
                        '\b' -> {
                            out.append("\\b")
                        }
                        '\u000C' -> {
                            out.append("\\f")
                        }
                        '\n' -> {
                            out.append("\\n")
                        }
                        '\r' -> {
                            out.append("\\r")
                        }
                        '\t' -> {
                            out.append("\\t")
                        }
                        else -> {
                            if (((c.code < 32))) {
                                out.append(String.format(Locale.ROOT, "\\u%04x", (c).toInt()))
                            } else {
                                out.append(c)
                            }
                        }
                    }
                    i++
                }
            }
            return out.append('"').toString()
        }
        @Throws(Exception::class)
        @JvmStatic fun canonical(value: Any?): String {
            if ((value is JSONObject)) {
                var row: JSONObject = (value as JSONObject)
                var keys: MutableList<String> = ArrayList()
                row.keys().forEachRemaining(keys::add)
                Collections.sort(keys)
                var out: StringJoiner = StringJoiner(",", "{", "}")
                for (key in keys) {
                    out.add((("" + quote(key) + ":") + canonical(row.get(key))))
                }
                return out.toString()
            }
            if ((value is JSONArray)) {
                var rows: JSONArray = (value as JSONArray)
                var out: StringJoiner = StringJoiner(",", "[", "]")
                run {
                    var i: Int = 0
                    while ((i < rows.length())) {
                        out.add(canonical(rows.get(i)))
                        i++
                    }
                }
                return out.toString()
            }
            if ((((value == null) || (value == JSONObject.NULL)))) {
                return "null"
            }
            if ((value is Number)) {
                return java.math.BigDecimal(value!!.toString()).stripTrailingZeros().toPlainString()
            }
            return (if (value is String) quote((value as String)) else value!!.toString())
        }
        @Throws(Exception::class)
        @JvmStatic fun actionHash(action: JSONObject?): String {
            var digest: ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(canonical(action).toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            var out: StringBuilder = StringBuilder()
            for (b in digest) {
                out.append(String.format(Locale.ROOT, "%02x", (b.toInt() and 255)))
            }
            return out.toString()
        }
        @Throws(Exception::class)
        @JvmStatic fun find(snapshot: JSONObject, group: String, id: String): JSONObject? {
            var rows: JSONArray = snapshot.getJSONArray(group)
            run {
                var i: Int = 0
                while ((i < rows.length())) {
                    if ((id.equals(rows.getJSONObject(i).optString("id")))) {
                        return rows.getJSONObject(i)
                    }
                    i++
                }
            }
            return null
        }
        @Throws(Exception::class)
        @JvmStatic fun token(state: JSONObject): String {
            return PixelMcpWriteTools.hash(JSONObject().put("master", state.getJSONObject("master")).put("pending", state.getJSONArray("pending")))
        }
        @Throws(Exception::class)
        @JvmStatic fun preview(state: JSONObject): JSONObject {
            var snapshot: JSONObject = JSONObject(state.getJSONObject("master").toString())
            DriveJournalRules.apply(snapshot, DriveJournalRules.union(state.getJSONArray("pending")))
            return snapshot
        }
        @Throws(Exception::class)
        @JvmStatic fun candidate(state: JSONObject, valid: JSONObject, id: String): JSONObject {
            var current: JSONObject? = find(valid, "orders", id)
            var row: JSONObject? = (if ((current == null)) null else JSONObject(current!!.toString()))
            var pending: JSONArray = state.getJSONArray("pending")
            run {
                var i: Int = 0
                while ((i < pending.length())) {
                    var a: JSONObject = pending.getJSONObject(i)
                    var p: JSONObject = a.getJSONObject("payload")
                    if ((("order".equals(a.optString("kind")) && id.equals(p.optString("id"))))) {
                        if (((row == null))) {
                            row = JSONObject()
                        }
                        var clean: JSONObject = DriveJournalRules.clean(p)
                        run {
                            var keys: MutableIterator<String> = clean.keys()
                            while (keys.hasNext()) {
                                var key: String = keys.next()
                                row!!.put(key, clean.get(key))
                            }
                        }
                    }
                    i++
                }
            }
            if (((row == null))) {
                throw IllegalArgumentException("Dieser Auftrag ist nicht mehr vorhanden. Bitte die Liste aktualisieren.")
            }
            return row
        }
        @Throws(Exception::class)
        @JvmStatic fun protectionSnapshot(state: JSONObject, valid: JSONObject): JSONObject {
            var result: JSONObject = JSONObject(valid.toString())
            var effective: JSONObject = PixelMcpReadTools.effective(state)
            for (group in arrayOf("worklogs", "invoices", "photos")) {
                result.put(group, effective.getJSONArray(group))
            }
            return result
        }
        @Throws(Exception::class)
        @JvmStatic fun checks(row: JSONObject, old: JSONObject?, snapshot: JSONObject): JSONArray {
            var checks: JSONArray = JSONArray()
            add(checks, "Auftragsbezeichnung", !row.optString("title").trim { it <= ' ' }.isEmpty(), "Bitte eine Bezeichnung eintragen.")
            var customer: Boolean = (find(snapshot, "customers", row.optString("customer_id")) != null)
            add(checks, "Kundenzuordnung", customer, "Der ausgewählte Kunde fehlt. Bitte einen vorhandenen Kunden wählen.")
            try {
                BillingRules.order(JSONObject(row.toString()), snapshot)
                add(checks, "Rechnungsempfänger", true, "")
            } catch (e: Exception) {
                add(checks, "Rechnungsempfänger", false, e.message)
            }
            try {
                if (((!row.optString("scheduled_date").isEmpty() && !row.isNull("scheduled_date")))) {
                    java.time.LocalDate.parse(row.getString("scheduled_date"))
                }
                add(checks, "Auftragsdatum", true, "")
            } catch (e: Exception) {
                add(checks, "Auftragsdatum", false, "Bitte ein gültiges Datum wählen oder das optionale Datum leeren.")
            }
            try {
                EntityProtectionRules.validateOrderChange(row, old, snapshot.getJSONArray("worklogs"), snapshot.getJSONArray("invoices"), snapshot.getJSONArray("photos"))
                add(checks, "Verknüpfte Einträge", true, "")
            } catch (e: Exception) {
                add(checks, "Verknüpfte Einträge", false, e.message)
            }
            return checks
        }
        @Throws(Exception::class)
        @JvmStatic fun add(rows: JSONArray, label: String, ok: Boolean, message: String?) {
            rows.put(JSONObject().put("label", label).put("ok", ok).put("message", (if (ok) "In Ordnung" else message)))
        }
        @Throws(Exception::class)
        @JvmStatic fun inspect(state: JSONObject, id: String): JSONObject {
            var valid: JSONObject = preview(state)
            var row: JSONObject = candidate(state, valid, id)
            var old: JSONObject? = find(valid, "orders", id)
            var reasons: JSONArray = JSONArray()
            var pending: JSONArray = state.getJSONArray("pending")
            var errors: JSONArray? = valid.optJSONArray("drive_journal_errors")
            var own: MutableSet<String> = HashSet()
            run {
                var i: Int = 0
                while ((i < pending.length())) {
                    var a: JSONObject = pending.getJSONObject(i)
                    if ((("order".equals(a.optString("kind")) && id.equals(a.getJSONObject("payload").optString("id"))))) {
                        own.add(a.getString("client_id"))
                    }
                    i++
                }
            }
            run {
                var i: Int = 0
                while (((errors != null) && (i < errors!!.length()))) {
                    var e: JSONObject = errors!!.getJSONObject(i)
                    if ((own.contains(e.optString("client_id")))) {
                        reasons.put(e.optString("error", "Die Änderung benötigt Prüfung."))
                    }
                    i++
                }
            }
            return JSONObject().put("id", id).put("token", token(state)).put("order", row).put("current", (if ((old == null)) JSONObject.NULL else old)).put("customers", valid.getJSONArray("customers")).put("checks", checks(row, old, protectionSnapshot(state, valid))).put("reasons", reasons).put("pending", own.size)
        }
        @Throws(Exception::class)
        @JvmStatic fun prepare(state: JSONObject, request: JSONObject): JSONObject {
            if ((!token(state).equals(request.optString("token")))) {
                throw IllegalArgumentException("Der Datenstand hat sich geändert. Bitte erneut prüfen; deine Eingaben bleiben sichtbar.")
            }
            var id: String = request.getString("id")
            var decision: String = request.optString("decision")
            if (((!decision.equals("save") && !decision.equals("keep")))) {
                throw IllegalArgumentException("Bitte eine Entscheidung auswählen.")
            }
            var valid: JSONObject = preview(state)
            var old: JSONObject? = find(valid, "orders", id)
            var row: JSONObject
            if ((decision.equals("keep"))) {
                if (((old == null))) {
                    throw IllegalArgumentException("Für diesen Auftrag gibt es noch keinen übernommenen Stand.")
                }
                row = JSONObject(old!!.toString())
            } else {
                row = candidate(state, valid, id)
                var edits: JSONObject = request.getJSONObject("order")
                for (key in arrayOf("title", "customer_id", "billing_customer_id", "description", "location", "scheduled_date", "status", "customer_visible")) {
                    if ((edits.has(key))) {
                        row.put(key, edits.get(key))
                    }
                }
            }
            row = DriveJournalRules.clean(row)
            row.put("id", id)
            var checks: JSONArray = checks(row, old, protectionSnapshot(state, valid))
            run {
                var i: Int = 0
                while ((i < checks.length())) {
                    if ((!checks.getJSONObject(i).getBoolean("ok"))) {
                        throw IllegalArgumentException(checks.getJSONObject(i).getString("message"))
                    }
                    i++
                }
            }
            BillingRules.order(row, valid)
            var refs: JSONArray = JSONArray()
            var pending: JSONArray = state.getJSONArray("pending")
            var failures: MutableSet<String> = HashSet()
            var errors: JSONArray = valid.getJSONArray("drive_journal_errors")
            run {
                var i: Int = 0
                while ((i < errors.length())) {
                    failures.add(errors.getJSONObject(i).optString("client_id"))
                    i++
                }
            }
            run {
                var i: Int = 0
                while ((i < pending.length())) {
                    var a: JSONObject = pending.getJSONObject(i)
                    if (((("order".equals(a.optString("kind")) && id.equals(a.getJSONObject("payload").optString("id"))) && failures.contains(a.optString("client_id"))))) {
                        refs.put(JSONObject().put("client_id", a.getString("client_id")).put("sha256", actionHash(a)))
                    }
                    i++
                }
            }
            if (((refs.length() > 0))) {
                row.put("_sync_resolves", refs)
            }
            if (((old == null))) {
                row.put("_mcp_create", true)
            } else {
                row.put("_sync_base", DriveJournalRules.guard(old, "order"))
            }
            var stamp: String = java.time.Instant.now().toString()
            row.put("updated_at", stamp).put("source", "Pixel offline")
            if ((!row.has("created_at"))) {
                row.put("created_at", (if ((old == null)) stamp else old!!.optString("created_at", stamp)))
            }
            return row
        }
    }
}
