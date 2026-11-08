package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Local effects used by the common Revier UI/MCP application service. */
interface RevierRepository {
    fun interface Work {
        @Throws(Exception::class) fun run(): JSONObject
    }

    @Throws(Exception::class) fun effective(): JSONObject

    @Throws(Exception::class) fun get(key: String, fallback: String): String

    @Throws(Exception::class) fun put(key: String, value: String)

    @Throws(Exception::class) fun list(prefix: String): JSONArray

    @Throws(Exception::class) fun rawEntries(prefix: String): JSONArray

    @Throws(Exception::class) fun transaction(work: Work): JSONObject

    @Throws(Exception::class) fun planInspection(appointmentArgs: JSONObject): JSONObject

    @Throws(Exception::class)
    fun changeInspection(operation: String, args: JSONObject): JSONObject {
        throw PixelMcpWriteTools.Rejected(
            "INSPECTION_ADAPTER_UNAVAILABLE",
            "Terminänderung derzeit nicht verfügbar.",
        )
    }

    @Throws(Exception::class)
    fun createDirectOrder(args: JSONObject): String {
        throw PixelMcpWriteTools.Rejected(
            "ORDER_ADAPTER_UNAVAILABLE",
            "Auftragsanlage derzeit nicht verfügbar.",
        )
    }

    @Throws(Exception::class) fun session(id: String): JSONObject? = null

    @Throws(Exception::class) fun sessionContexts(): JSONArray = JSONArray()

    @Throws(Exception::class) fun currentSession(): JSONObject = JSONObject()

    @Throws(Exception::class) fun nativeReceipt(request: String): JSONObject? = null

    @Throws(Exception::class)
    fun prepareEquipmentProfilePhoto(
        upload: String,
        equipment: String,
        request: String,
    ): JSONObject {
        throw RevierRules.error(
            "EQUIPMENT_PHOTO_UNAVAILABLE",
            "Gerätefoto kann gerade nicht gespeichert werden.",
        )
    }

    @Throws(Exception::class)
    fun verifyEquipmentProfilePhoto(photo: JSONObject) {
        throw RevierRules.error(
            "EQUIPMENT_PHOTO_ORIGINAL_MISSING",
            "Gerätefoto kann hier nicht vollständig geprüft werden.",
        )
    }
}
