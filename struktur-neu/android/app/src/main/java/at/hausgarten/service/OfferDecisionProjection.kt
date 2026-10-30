package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import org.json.*

/** Technical decision evidence joins the read view; canonical offer/PDF hashes stay unchanged. */
internal object OfferDecisionProjection {
    @JvmStatic
    @Throws(Exception::class)
    fun apply(store: PixelLocalStore, master: JSONObject): Unit {
        apply(store!!.getReadableDatabase(), master)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun apply(database: SQLiteDatabase, master: JSONObject): Unit {
        var decisions: JSONObject = JSONObject()
        var revisions: JSONObject = JSONObject()
        var prefix = OfferDecisionRules.PREFIX
        run {
            database!!
                .rawQuery(
                    "SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",
                    arrayOf(prefix, (prefix + '￿')),
                )
                .use { rows ->
                    while (rows!!.moveToNext()) {
                        var key = rows!!.getString(0)
                        if (
                            (!key!!.startsWith((prefix.toString() + "current:")) &&
                                !key!!.startsWith((prefix.toString() + "revision:")))
                        ) {
                            continue
                        }
                        var value: JSONObject = JSONObject(rows!!.getString(1))
                        OfferDecisionRules.version(value)
                        if (key!!.startsWith((prefix.toString() + "current:"))) {
                            decisions!!.put(value!!.getString("offer_id"), value)
                        } else {
                            revisions!!.put(value!!.getString("offer_id"), value)
                        }
                    }
                }
        }
        if ((decisions!!.length() > 0)) {
            master!!.put("offer_decisions", decisions)
        } else {
            master!!.remove("offer_decisions")
        }
        if ((revisions!!.length() > 0)) {
            master!!.put("offer_revisions", revisions)
        } else {
            master!!.remove("offer_revisions")
        }
    }
}
