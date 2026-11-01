package at.hausgarten.service

import android.content.ContentValues
import java.util.*
import org.json.*

internal class LocalTestCleanup {
    fun interface Values {
        @Throws(Exception::class) fun get(key: String, fallback: String): String
    }

    constructor() {}

    companion object {
        private val KEY: String = "purged_local_test_records_v1"

        @JvmStatic
        @Throws(Exception::class)
        fun overlay(store: PixelLocalStore, master: JSONObject): Unit {

            overlay(store::ownerValue, master)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun overlay(values: Values, master: JSONObject): Unit {

            var tombstones = JSONObject(values.get(KEY, "{}"))
            run {
                var it = tombstones.keys()
                var firstIteration60 = true
                while (true) {
                    if (firstIteration60) firstIteration60 = false else {}
                    if (!(it.hasNext())) break

                    var key = it.next()
                    LocalTestTombstone.project(master, key, tombstones.getJSONObject(key))
                }
            }
        }

        @JvmStatic
        fun sameTarget(event: JSONObject, entity: String, id: String): Boolean {

            var kind = event.optString("kind")
            var p = event.optJSONObject("payload")
            if (((p == null) || !((id == p.optString("id"))))) {
                return false
            }
            return ((((entity == kind) ||
                (Arrays.asList("archive", "restore", "purge_v1").contains(kind) &&
                    (entity == p.optString("entity")))) ||
                ((entity == "offer") && kind.startsWith("offer_"))) ||
                ((entity == "invoice") && kind.startsWith("invoice_")))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun syntheticOrigin(
            raw: JSONObject,
            pending: JSONArray,
            entity: String,
            id: String,
        ): Boolean {

            var record =
                OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)), id)
            if ((record == null)) {
                var a = raw.optJSONObject("owner_archive")
                var saved = (if ((a == null)) null else a.optJSONObject(((entity + ":") + id)))
                record = (if ((saved == null)) null else saved.optJSONObject("record"))
            }
            run {
                var i: Int = 0
                var firstIteration61 = true
                while (true) {
                    if (firstIteration61) firstIteration61 = false
                    else {
                        i++
                    }
                    if (!(((record == null) && (i < pending.length())))) break

                    var event = pending.getJSONObject(i)
                    var p = event.getJSONObject("payload")
                    if ((!(sameTarget(event, entity, id)) || !(p.optBoolean("_local_only")))) {
                        continue
                    }
                    record = p.optJSONObject("_archive_record")
                    if (((record == null) && (entity == event.optString("kind")))) {
                        record = p
                    }
                }
            }
            if ((record == null)) {
                return false
            }
            if ((entity == "customer")) {
                return TestDataRules.customer(record)
            }
            if (TestDataRules.marked(record)) {
                return true
            }
            var customer = record.optString("customer_id")
            var c = OwnerJournalRules.findOrNull(raw.optJSONArray("customers"), customer)
            if (TestDataRules.customer(c)) {
                return true
            }
            run {
                var i: Int = 0
                var firstIteration62 = true
                while (true) {
                    if (firstIteration62) firstIteration62 = false
                    else {
                        i++
                    }
                    if (!((i < pending.length()))) break

                    var a = pending.getJSONObject(i)
                    if (
                        (((a.optString("kind") == "customer") &&
                            (a.getJSONObject("payload").optString("id") == customer)) &&
                            TestDataRules.customer(a.getJSONObject("payload")))
                    ) {
                        return true
                    }
                }
            }
            return false
        }

    }
}
