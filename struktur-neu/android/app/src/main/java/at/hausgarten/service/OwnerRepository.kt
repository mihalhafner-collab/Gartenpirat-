package at.hausgarten.service

import org.json.*
import java.util.*

interface OwnerRepository {
@Throws(Exception::class) fun state(): JSONObject
@Throws(Exception::class) fun effective(): JSONObject {

return SnapshotProjection.effective(state())
}
@Throws(Exception::class) fun settings(): JSONObject {

return JSONObject(ownerValue("settings", "{}"))
}
fun ownerValue(key: String, fallback: String): String
fun putOwnerValue(key: String, value: String): Unit
@Throws(Exception::class) fun pendingActions(): JSONArray
@Throws(Exception::class) fun enqueue(kind: String, payload: JSONObject, request: String): JSONObject
@Throws(Exception::class) fun fileMeta(id: String): JSONObject
@Throws(Exception::class) fun fileBytes(id: String): ByteArray
@Throws(Exception::class) fun putFile(id: String, metadata: JSONObject, data: ByteArray): Unit
@Throws(Exception::class) fun savePhoto(payload: JSONObject): String
@Throws(Exception::class) fun replaceWorklog(payload: JSONObject): Unit
@Throws(Exception::class) fun createDocument(record: JSONObject): JSONObject
@Throws(Exception::class) fun readDocument(record: JSONObject): Unit
@Throws(Exception::class) fun verifyDelivery(messageId: String, offer: JSONObject): JSONObject
@Throws(Exception::class) fun planningSettings(): JSONObject
fun calendarConnected(): Boolean
fun weatherCredentialAvailable(): Boolean
@Throws(Exception::class) fun savePlanningSettings(settings: JSONObject, expected: String): Unit
@Throws(Exception::class) fun requestPlanning(): JSONObject
@Throws(Exception::class) fun acknowledgeWeather(args: JSONObject): JSONObject
@Throws(Exception::class) fun resolveCalendar(args: JSONObject): JSONObject
@Throws(Exception::class) fun checkBusy(appointment: JSONObject): Unit
@Throws(Exception::class) fun fullBackupStatus(args: JSONObject): JSONObject {

throw UnsupportedOperationException("Native full backup required")
}
@Throws(Exception::class) fun fullBackupAction(name: String, args: JSONObject): JSONObject {

throw UnsupportedOperationException("Native full backup required")
}
@Throws(Exception::class) fun revierQuery(name: String, args: JSONObject): JSONObject {

throw UnsupportedOperationException("Native Revier store required")
}
@Throws(Exception::class) fun revierAction(name: String, args: JSONObject): JSONObject {

throw UnsupportedOperationException("Native Revier store required")
}

}
