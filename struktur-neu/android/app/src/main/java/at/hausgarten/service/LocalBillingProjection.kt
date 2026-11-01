package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import org.json.*

/** Joins the exact local source ledger without changing an invoice or historical work record. */
internal object LocalBillingProjection {
    @JvmStatic
    @Throws(Exception::class)
    fun apply(store: PixelLocalStore, master: JSONObject): Unit {
        apply(store!!.getReadableDatabase(), master)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun apply(database: SQLiteDatabase, master: JSONObject): Unit {
        if (!LocalAuthorityRules.ENGINE.equals(master!!.optString("processing_engine"))) {
            master!!.remove("local_billing_sources")
            return
        }
        var allocations: JSONObject = JSONObject()
        var prefix: String = "local_invoice_sources:"
        run {
            database!!
                .rawQuery(
                    "SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",
                    arrayOf(prefix, (prefix + '￿')),
                )
                .use { rows ->
                    while (rows!!.moveToNext()) {
                        allocations!!.put(
                            (rows!!.getString(0))!!.substring(prefix!!.length),
                            JSONObject(rows!!.getString(1)),
                        )
                    }
                }
        }
        var sources: JSONObject =
            ((JSONObject())!!.put("formatVersion", 1))!!.put("by_invoice", allocations)
        LocalBillingRules.validate(master, sources)
        master!!.put("local_billing_sources", sources)
    }
}
