package at.hausgarten.service

import org.json.*
import java.util.*

/** Test provenance is a domain boundary, never a licence to use real customer records. */
class TestDataRules {
    private constructor() {
    }
    companion object {
        @JvmStatic fun customer(c: JSONObject?): Boolean {
            return ((c != null) && ((c!!.optString("name").startsWith("[TEST]") || c!!.optString("name").equals("MCP FUNKTIONSTEST – KEIN ECHTER KUNDE"))))
        }
        @JvmStatic fun marked(p: JSONObject): Boolean {
            return (p.optBoolean("test_record") || p.optString("title").startsWith("[TEST]"))
        }
        @JvmStatic fun customerChange(row: JSONObject, old: JSONObject?) {
            if ((((old != null) && (customer(old) != customer(row))))) {
                throw IllegalArgumentException("Test-/Produktivzuordnung eines Kunden darf nicht gewechselt werden.")
            }
            if (((row.optBoolean("test_record") && !customer(row)))) {
                throw IllegalArgumentException("Testkunde eindeutig mit [TEST] markieren.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun guard(p: JSONObject, s: JSONObject) {
            var customer: JSONObject? = OwnerJournalRules.findOrNull(s.optJSONArray("customers"), p.optString("customer_id"))
            var test: Boolean = (marked(p) || customer(customer))
            if ((!test)) {
                return
            }
            var recipient: String = p.optString("billing_customer_id", p.optString("customer_id"))
            if ((recipient.isEmpty())) {
                recipient = p.optString("customer_id")
            }
            if (((!customer(customer) || !customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"), recipient))))) {
                throw PixelMcpWriteTools.Rejected("TEST_DATA_ISOLATION", "Testdokumente benötigen ausschließlich eindeutig markierte Testkunden und Testempfänger.")
            }
            p.put("test_record", true)
        }
        @Throws(Exception::class)
        @JvmStatic fun noDelivery(p: JSONObject, s: JSONObject) {
            guard(JSONObject(p.toString()), s)
            if (((marked(p) || customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"), p.optString("customer_id")))))) {
                throw PixelMcpWriteTools.Rejected("TEST_DELIVERY_BLOCKED", "Testdokumente dürfen nicht an externe Empfänger übergeben oder versendet werden.")
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun localOnly(a: JSONObject, s: JSONObject): Boolean {
            var p: JSONObject = a.getJSONObject("payload")
            if ((p.optBoolean("_local_only"))) {
                return true
            }
            if ((a.optString("kind").equals(TrashPurgeBatch.KIND))) {
                var local: Boolean = false
                var remote: Boolean = false
                var entries: JSONArray = p.getJSONArray("entries")
                run {
                    var i: Int = 0
                    while ((i < entries.length())) {
                        var e: JSONObject = entries.getJSONObject(i)
                        var test: Boolean = localOnly(JSONObject().put("kind", "purge_v1").put("payload", JSONObject().put("entity", e.getString("entity")).put("id", e.getString("entity_id"))), s)
                        local = local or test
                        remote = remote or !test
                        i++
                    }
                }
                if (((local && remote))) {
                    throw PixelMcpWriteTools.Rejected("TEST_DATA_ISOLATION", "Lokale Testeinträge im Papierkorb zuerst einzeln endgültig löschen. Keine gemischte Test-/Drive-Löschaktion gespeichert.")
                }
                return local
            }
            if ((((p.optBoolean("_local_only") || marked(p)) || (a.optString("kind").equals("customer") && customer(p))))) {
                return true
            }
            if ((customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"), p.optString("customer_id"))))) {
                return true
            }
            var kind: String = a.optString("kind")
            if ((java.util.Arrays.asList("archive", "restore", "purge_v1").contains(kind))) {
                kind = p.optString("entity")
            }
            var group: String = (if (kind.startsWith("offer_")) "offers" else (if (kind.startsWith("invoice_")) "invoices" else LocalJournalRules.collection(kind)))
            var existing: JSONObject? = OwnerJournalRules.findOrNull(s.optJSONArray(group), p.optString("id"))
            if (((existing == null))) {
                var archive: JSONObject? = s.optJSONObject("owner_archive")
                var entry: JSONObject? = (if ((archive == null)) null else archive!!.optJSONObject((("" + kind + ":") + p.optString("id"))))
                if (((entry != null))) {
                    existing = entry!!.optJSONObject("record")
                }
            }
            return ((existing != null) && (((marked(existing) || (kind.equals("customer") && customer(existing))) || customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"), existing!!.optString("customer_id"))))))
        }
    }
}
