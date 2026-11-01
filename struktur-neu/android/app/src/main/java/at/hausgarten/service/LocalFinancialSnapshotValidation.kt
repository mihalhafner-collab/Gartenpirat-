package at.hausgarten.service

import android.database.sqlite.SQLiteDatabase
import org.json.*

/** Captured SQLite adapter; no live authority changes, file jobs or writes. */
internal object LocalFinancialSnapshotValidation {
    @JvmStatic
    @Throws(Exception::class)
    fun inspect(captured: SQLiteDatabase, effective: JSONObject): JSONObject {
        var namespaces: JSONObject = JSONObject()
        run {
            captured!!
                .rawQuery(
                    "SELECT key FROM owner_state WHERE key LIKE 'local_payment:%' OR key LIKE 'invoice_opening_balance:%' OR key LIKE 'local_invoice_job:%' OR key LIKE 'local_invoice_document_recovery:%' OR key LIKE 'local_number_reservation:%' OR key LIKE 'local_invoice_sources:%' OR key LIKE 'local_invoice_material_sources:%' OR key LIKE 'revier:v1:entity:material_movement:%' OR key LIKE 'revier:v1:reversed:%' OR key='local_invoice_counter' OR key='local_invoice_external_evidence'",
                    null,
                )
                .use { rows ->
                    while (rows!!.moveToNext()) {
                        var key = rows!!.getString(0)
                        namespaces!!.put(key, DatabaseBackupSnapshot.ownerValue(captured, key, ""))
                    }
                }
        }
        return LocalFinancialValidation.inspect(effective, namespaces)
    }
}
