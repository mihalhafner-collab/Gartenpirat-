package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Extends the existing SQLite owner state. Existing business entities stay canonical. */
class RevierLocalRepository(private val store: PixelLocalStore, private val inspections: InspectionPlanner?) : RevierRepository {
    fun interface InspectionPlanner { @Throws(Exception::class) fun plan(appointmentArgs: JSONObject): JSONObject }
    override fun effective(): JSONObject = PixelMcpReadTools.effective(store.getState())
    override fun get(key: String, fallback: String): String = store.ownerValue(key, fallback)
    override fun put(key: String, value: String) = store.putOwnerValue(key, value)
    override fun list(prefix: String): JSONArray {
        val out = JSONArray()
        store.readableDatabase.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key", arrayOf(prefix, prefix + '\uffff')).use { rows ->
            while (rows.moveToNext()) out.put(JSONObject(rows.getString(1)))
        }
        return out
    }
    override fun transaction(work: RevierRepository.Work): JSONObject {
        val db = store.writableDatabase
        store.beginBusinessTransaction(db)
        try { val result = work.run(); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    override fun planInspection(appointmentArgs: JSONObject): JSONObject {
        val planner = inspections ?: throw PixelMcpWriteTools.Rejected("INSPECTION_ADAPTER_UNAVAILABLE", "Besichtigung kann gerade nicht geplant werden. Eingaben bleiben erhalten.")
        return planner.plan(appointmentArgs)
    }
    override fun changeInspection(operation: String, args: JSONObject): JSONObject {
        if (operation != "update_appointment") throw IllegalArgumentException("Ungültige Terminänderung.")
        return inspections!!.plan(JSONObject(args.toString()).put("_operation", operation))
    }
    override fun createDirectOrder(args: JSONObject): String = store.saveEditorRecord("order", args)
    override fun session(id: String): JSONObject? = RevierSessionContexts.session(store.readableDatabase, id)
    override fun sessionContexts(): JSONArray = RevierSessionContexts.all(store.readableDatabase)
    override fun currentSession(): JSONObject = store.workSessions().current()
    override fun nativeReceipt(request: String): JSONObject? = store.lookupNativeReceipt(request)
    override fun prepareEquipmentProfilePhoto(upload: String, equipment: String, request: String): JSONObject = store.prepareEquipmentProfilePhoto(upload, equipment, request)
    override fun prepareEquipmentProfilePhotoDraft(source: JSONObject, equipment: String, request: String): JSONObject = store.prepareEquipmentProfilePhotoDraft(source, equipment, request)
    override fun verifyEquipmentProfilePhoto(photo: JSONObject) {
        EquipmentProfilePhotoRules.attachment(photo, store.photoAttachment(photo.getString("id")))
        store.photoAttachmentStore().read(photo)
    }
    override fun assertSourceCorrectionAllowed(type: String, id: String) {
        if (type != "material_movement") return
        store.readableDatabase.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<?", arrayOf("local_invoice_material_sources:", "local_invoice_material_sources:\uffff")).use { rows ->
            while (rows.moveToNext()) {
                val allocations = JSONObject(store.ownerValue(rows.getString(0), "{}"))
                if (allocations.has(id)) throw PixelMcpWriteTools.Rejected("INVOICED_SOURCE_CORRECTION_REQUIRED", "Materialquelle wurde bereits abgerechnet. Zuerst den erforderlichen Korrekturbeleg führen; keine unbemerkte Gegenbuchung.")
            }
        }
    }
    override fun rawEntries(prefix: String): JSONArray {
        val out = JSONArray()
        store.readableDatabase.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key", arrayOf(prefix, prefix + '\uffff')).use { rows ->
            while (rows.moveToNext()) out.put(JSONObject().put("key", rows.getString(0)).put("value", rows.getString(1)))
        }
        return out
    }
}
