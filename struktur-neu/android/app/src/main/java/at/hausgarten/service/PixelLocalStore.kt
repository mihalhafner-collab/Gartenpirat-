package at.hausgarten.service

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.IOException
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.HashSet
import java.util.Iterator
import java.util.Locale
import java.util.Set
import java.util.UUID

class PixelLocalStore : SQLiteOpenHelper, PixelMcpWriteTools.Sink {
internal val context: Context
private val preferences: SharedPreferences
private val photoAttachments: PhotoAttachmentStore
private val clock: WorkSessionStore.Clock
private var registeredOpen: Boolean = false
private var closed: Boolean = false
private var lastOpenedDatabase: SQLiteDatabase? = null
private val photoOperations = java.lang.Object()
private var activePhotoOperations = 0
constructor(context: Context) : this(context, WorkSessionStore.DeviceClock(context)) {

}
constructor(context: Context, clock: WorkSessionStore.Clock) : super(context, "haus-garten-mobile.db", null, SCHEMA_VERSION) {
LocalAuthorityGuard.assertStoreMayOpen(context)
setWriteAheadLoggingEnabled(true)
this.clock = clock
this.context = context
preferences = context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE)
photoAttachments = PhotoAttachmentStore(context)
migrateLegacySnapshotPhotoAttachments()
}
override @Synchronized fun getWritableDatabase(): SQLiteDatabase {

if (closed) return closedTransactionDatabase()
LocalAuthorityGuard.assertStoreMayOpen(context)
return super.getWritableDatabase()
}
override @Synchronized fun getReadableDatabase(): SQLiteDatabase {

if (closed) return closedTransactionDatabase()
LocalAuthorityGuard.assertStoreMayOpen(context)
return super.getReadableDatabase()
}
private fun closedTransactionDatabase(): SQLiteDatabase {
val database = lastOpenedDatabase
if (database != null && database.isOpen && database.inTransaction()) return database
throw IllegalStateException("LOCAL_STORE_CLOSED")
}
override fun onOpen(database: SQLiteDatabase): Unit {

super.onOpen(database)
try {

LocalAuthorityGuard.verifyDatabaseContract(context, database)
if (!database.isReadOnly && !database.isWriteAheadLoggingEnabled) throw IllegalStateException("LOCAL_WAL_REQUIRED")
lastOpenedDatabase = database
synchronized(OPEN_DATABASES) { OPEN_DATABASES[database] = context.getDatabasePath("haus-garten-mobile.db").canonicalPath }
if (!(registeredOpen)) {
OPEN_STORES.computeIfAbsent(context.getDatabasePath("haus-garten-mobile.db").getCanonicalPath(), { key -> java.util.concurrent.atomic.AtomicInteger() }).incrementAndGet()
registeredOpen = true
}

} catch (invalid: Exception) {
throw IllegalStateException("LOCAL_DATABASE_CONTRACT_REVIEW_REQUIRED", invalid)
}
}
override fun close(): Unit {
synchronized(photoOperations) {
while (activePhotoOperations > 0) photoOperations.wait()
synchronized(this) {

if (closed) return
closed = true
super.close()
if (registeredOpen) {
try {

var count = OPEN_STORES.get(context.getDatabasePath("haus-garten-mobile.db").getCanonicalPath())
if (count != null) {
count.decrementAndGet()
}

} catch (invalid: IOException) {
throw IllegalStateException("LOCAL_STORE_PATH_INVALID", invalid)
} finally {
registeredOpen = false
}
}
}
}
}
fun nativeData(): NativeDataRepository = NativeDataRepository(this)

fun notifyCommittedChange() = NativeDataRepository.notifyCommitted(context.getDatabasePath("haus-garten-mobile.db").canonicalPath)

fun appContext(): Context {

return context
}
fun businessTables(): BusinessTableStore {

return businessTables(getWritableDatabase())
}
fun beginBusinessTransaction(db: SQLiteDatabase): Unit {

beginJournalTransaction(db, LAM@ {
var tables = businessTables(db, true)
if (tables.active() && !(tables.projectionCurrent())) {
tables.commitJournal(LocalPendingActions.read(db, false, { id -> BackupSnapshotState.photo(db, id) }, 0), LAM@ { master ->
removeSnapshotPhotoBinaries(master)
documentReferences(master)
PixelDriveState.prepare(master)
seedServiceCatalog(master)
LocalTestCleanup.overlay({ key, fallback -> DatabaseBackupSnapshot.ownerValue(db, key, fallback) ?: fallback }, master)
if (LocalAuthorityGuard.local(context)) {
master.put("processing_engine", LocalAuthorityRules.ENGINE).put("local_contract_version", LocalAuthorityRules.CONTRACT)
}
OfferDecisionProjection.apply(db, master)
LocalBillingProjection.apply(db, master)
 })
}
return@LAM null
 })
}
fun workSessions(): WorkSessionStore {

return WorkSessionStore(getWritableDatabase(), clock)
}
private @Throws(Exception::class) fun readAsset(name: String): String {

context.getAssets().open(name).use { input ->
BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->

var out = StringBuilder()
while (true) {
val line = reader.readLine() ?: break
out.append(line)
}
return out.toString()

}
}
}
private @Throws(Exception::class) fun emptyBootstrap(): JSONObject {

try {

var value = JSONObject(readAsset("initial-data.json"))
var keys = HashSet<String>(java.util.Arrays.asList("format", "schema_version", "accepted_ids", "sync_errors", "capabilities", "customers", "orders", "worklogs", "invoices", "photos", "offers", "appointments", "service_catalog"))
if (value.length() != keys.size || value.getInt("format") != 1 || value.getInt("schema_version") != 2) {
throw IllegalArgumentException()
}
run {
var names = value.keys()
var firstIteration34 = true
while (true) {
if (firstIteration34) firstIteration34 = false else {  }
if (!(names.hasNext())) break

if (!(keys.contains(names.next()))) {
throw IllegalArgumentException()
}

}
}
for (key in arrayOf("accepted_ids", "sync_errors", "customers", "orders", "worklogs", "invoices", "photos", "offers", "appointments", "service_catalog")) {
if (value.getJSONArray(key).length() != 0) {
throw IllegalArgumentException()
}
}
var capabilities = value.getJSONObject("capabilities")
var supported = arrayOf("worklog_compare_and_save", "entity_compare_and_save", "customer_custom_fields_compare_and_save", "billing_relations")
if (capabilities.length() != supported.size) {
throw IllegalArgumentException()
}
for (key in supported) {
if (!((java.lang.Boolean.TRUE == capabilities.get(key)))) {
throw IllegalArgumentException()
}
}
return value

} catch (invalid: Exception) {
throw IOException("LOCAL_BOOTSTRAP_INVALID")
}
}
private fun isoNow(): String {

return java.time.Instant.now().toString()
}
private fun migrateLegacySnapshotPhotoAttachments(): Unit {

try {

var saved = preferences.getString("drive_snapshot", "")!!
if (saved.isEmpty()) {
return
}
var snapshot = JSONObject(saved)
var photos = snapshot.optJSONArray("photos")
var inline: Boolean = false
run {
var i: Int = 0
var firstIteration35 = true
while (true) {
if (firstIteration35) firstIteration35 = false else { i++ }
if (!(photos != null && i < photos.length())) break

var photo = photos.optJSONObject(i)
if (photo != null && (photo.has("data") || photo.has("full_data"))) {
inline = true
break
}

}
}
if (!(inline)) {
return
}
recordSnapshotPhotoAttachments(snapshot)
preferences.edit().putString("drive_snapshot", snapshot.toString()).commit()

} catch (ignored: Exception) {

}
}
@Throws(Exception::class) fun documentReferences(snapshot: JSONObject): Unit {

var invoices = snapshot.optJSONArray("invoices")
run {
var i: Int = 0
var firstIteration36 = true
while (true) {
if (firstIteration36) firstIteration36 = false else { i++ }
if (!(invoices != null && i < invoices.length())) break

var invoice = invoices.getJSONObject(i)
var prepared = LegacyPdfAttachment.describe(invoice)
if (prepared != null) {
BusinessDocumentStore(context).store(prepared.bytes, invoice)
invoice.remove("pdf_base64")
}

}
}
}
override fun onCreate(database: SQLiteDatabase): Unit {

database.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)")
WorkSessionStore.createSchema(database)
createPhotoAttachmentSchema(database)
createOwnerSchema(database)
}
override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit {

if (oldVersion < 2) {
WorkSessionStore.createSchema(database)
}
if (oldVersion < 3) {
createPhotoAttachmentSchema(database)
migrateLegacyPendingPhotoAttachments(database)
}
if (oldVersion < 4) {
createOwnerSchema(database)
}
}
private fun createOwnerSchema(database: SQLiteDatabase): Unit {

database.execSQL("CREATE TABLE IF NOT EXISTS owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
database.execSQL("CREATE TABLE IF NOT EXISTS mcp_uploads(id TEXT PRIMARY KEY,metadata TEXT NOT NULL,data BLOB NOT NULL)")
}
@Throws(Exception::class) fun rawSnapshot(fallback: String): JSONObject {

return businessTables().snapshot(preferences.getString("drive_snapshot", fallback)!!)
}
fun snapshotText(fallback: String): String {

var value = ownerValueOrNull("snapshot")
return (if (value == null) preferences.getString("drive_snapshot", fallback)!! else value)
}
fun putOwnerValue(key: String, value: String): Unit {

var db = getWritableDatabase()
beginBusinessTransaction(db)
try {

var tables = businessTables(db)
if ((key == "snapshot") && tables.active()) {
tables.replaceSnapshot(JSONObject(value))
} else {
db.execSQL("INSERT OR REPLACE INTO owner_state(key,value) VALUES(?,?)", arrayOf(key, value))
if ((key == "purged_local_test_records_v1")) {
tables.invalidateJournal()
}
}
db.setTransactionSuccessful()

} catch (invalid: Exception) {
throw IllegalStateException("LOCAL_STATE_WRITE_FAILED", invalid)
} finally {
db.endTransaction()
}
}
fun ownerValue(key: String, fallback: String): String = ownerValueNullable(key, fallback)!!

fun ownerValueOrNull(key: String): String? = ownerValueNullable(key, null)

private fun ownerValueNullable(key: String, fallback: String?): String? {

if ((key == "snapshot")) {
try {

var tables = businessTables()
if (tables.active()) {
return tables.exportSnapshot().toString()
}

} catch (invalid: Exception) {
throw IllegalStateException("LOCAL_TABLE_READ_FAILED", invalid)
}
}
var count: Int
getReadableDatabase().rawQuery("SELECT length(value) FROM owner_state WHERE key=?", arrayOf(key)).use { row ->

if (!(row.moveToFirst())) {
return fallback
}
count = row.getInt(0)

}
var out = StringBuilder()
run {
var offset: Int = 0
var firstIteration37 = true
while (true) {
if (firstIteration37) firstIteration37 = false else { offset += 32768 }
if (!(offset < count)) break

getReadableDatabase().rawQuery("SELECT substr(value,?,32768) FROM owner_state WHERE key=?", arrayOf((offset + 1).toString(), key)).use { row ->

if (!(row.moveToFirst())) {
return fallback
}
out.append(row.getString(0))

}

}
}
return out.toString()
}
@Throws(Exception::class) fun seedServiceCatalog(snapshot: JSONObject): Unit {

ServiceCatalogBootstrap.seed(snapshot, { readAsset("service-catalog.json") })
}
private fun createPhotoAttachmentSchema(database: SQLiteDatabase): Unit {

database.execSQL("CREATE TABLE IF NOT EXISTS photo_attachments(photo_id TEXT PRIMARY KEY,content_sha256 TEXT NOT NULL,mime_type TEXT NOT NULL,byte_size INTEGER NOT NULL,storage_name TEXT NOT NULL,role TEXT NOT NULL DEFAULT 'documentation',transfer_state TEXT NOT NULL DEFAULT 'local',created_at TEXT NOT NULL)")
database.execSQL("CREATE INDEX IF NOT EXISTS ix_photo_attachments_hash ON photo_attachments(content_sha256)")
}
private fun migrateLegacyPendingPhotoAttachments(database: SQLiteDatabase): Unit {

database.rawQuery("SELECT payload FROM pending WHERE kind='photo' ORDER BY rowid", null).use { rows ->

while (rows.moveToNext()) {
try {

var payload = JSONObject(rows.getString(0))
if (payload.optString("data").isEmpty() || payload.optString("id").isEmpty()) {
continue
}
var prepared = photoAttachments.prepareLegacy(payload)
saveAttachmentMetadata(database, payload.getString("id"), prepared.metadata, "legacy_inline")

} catch (ignored: Exception) {

}
}

}
}
fun getState(): JSONObject {

var db = getWritableDatabase()
// Activity destruction may close the helper while an IO query finishes.
// Keep the connection pool alive through endTransaction, including failures.
db.acquireReference()
try {
db.beginTransaction()
try {

var result = getStateLocked()
db.setTransactionSuccessful()
return result

} finally {
db.endTransaction()
}
} finally { db.releaseReference() }
}
fun readSource(): PixelMcpReadTools.Source {

return object : PixelMcpReadTools.Source {
override fun state(): JSONObject {

return getState()
}
override @Throws(Exception::class) fun stateFor(tool: String, args: JSONObject): JSONObject {

return queryState(tool, args)
}
override @Throws(Exception::class) fun metadata(): JSONObject {

return JSONObject().put("last_drive_sync", preferences.getString("last_drive_sync", ""))
}
}
}
private @Throws(Exception::class) fun queryState(tool: String, args: JSONObject): JSONObject {

if (tool == "get_active_work_session") {
return readSource().metadata().put("master", JSONObject()).put("pending", JSONArray()).put("storage_point_read", true).put("work_session", workSessions().current())
}

return nativeData().queryState(tool, args, readSource().metadata()) ?: getState()
}
private fun getStateLocked(): JSONObject {

var output = JSONObject()
try {

var master = rawSnapshot(readAsset("initial-data.json"))
removeSnapshotPhotoBinaries(master)
documentReferences(master)
PixelDriveState.prepare(master)
seedServiceCatalog(master)
LocalTestCleanup.overlay(this, master)
if (LocalAuthorityGuard.local(context)) {
master.put("processing_engine", LocalAuthorityRules.ENGINE).put("local_contract_version", LocalAuthorityRules.CONTRACT)
}
output.put("master", master)
output.put("pending", pendingActions())
output.put("work_session", workSessions().current())
output.put("sync_queue", JSONObject().put("mode", "local_only").put("contract_version", 3)
    .put("total", 0).put("pending", 0).put("review", 0)
    .put("locally_committed", output.getJSONArray("pending").length()))
output.put("storage_contract_version", 3)
output.put("processing_engine", LocalAuthorityRules.ENGINE)
output.put("business_sync_allowed", false)
output.put("local_authority", LocalAuthorityGuard(context).current())

} catch (error: Exception) {
throw IllegalStateException("LOCAL_STATE_BUILD_FAILED", error)
}
try {
OfferDecisionProjection.apply(this, output.getJSONObject("master"))
LocalBillingProjection.apply(this, output.getJSONObject("master"))
output.put("sync_status", JSONObject().put("state", "local_only")
    .put("syncing", false).put("business_sync_allowed", false)
    .put("cloud_confirmed", false))
} catch (invalid: Exception) {
throw IllegalStateException("LOCAL_STATE_METADATA_UNREADABLE", invalid)
}
return output
}
@Throws(Exception::class) fun saveWorklog(payload: JSONObject): String {

var transaction = getWritableDatabase()
beginBusinessTransaction(transaction)
try {

var result = saveWorklogLocked(payload)
transaction.setTransactionSuccessful()
return result

} finally {
transaction.endTransaction()
}
}
private @Throws(Exception::class) fun saveWorklogLocked(payload: JSONObject): String {
var payload = payload

var current = PixelMcpReadTools.effective(getState())
payload = ManualChangeRules.editor(payload, findById(current.getJSONArray("worklogs"), payload.optString("id")), "worklog")
var customerId = payload.optString("customer_id").trim()
if (customerId.isEmpty() || payload.optString("activity").trim().isEmpty()) {
throw IllegalArgumentException("Kunde und Tätigkeit sind erforderlich.")
}
var isNew: Boolean = payload.optString("id").isEmpty()
var requireOrder: Boolean = payload.optBoolean("_require_order", false) || payload.optBoolean("require_order", false)
var orderId = (if (payload.isNull("order_id")) "" else payload.optString("order_id").trim())
var master = current
var customers = master.getJSONArray("customers")
var orders = master.getJSONArray("orders")
var previous = findById(master.getJSONArray("worklogs"), payload.optString("id"))
isNew = previous == null
if (!(containsId(customers, customerId))) {
throw IllegalArgumentException("Der ausgewählte Kunde existiert nicht.")
}
if (isNew && requireOrder && orderId.isEmpty()) {
throw IllegalArgumentException("Bitte zuerst einen Auftrag auswählen oder anlegen.")
}
if (!(orderId.isEmpty())) {
var order = findById(orders, orderId)
if (order == null) {
throw IllegalArgumentException("Der ausgewählte Auftrag existiert nicht.")
}
if (!((customerId == order.optString("customer_id")))) {
throw IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.")
}
if (payload.optString("location").trim().isEmpty()) {
payload.put("location", order.optString("location"))
}
}
BillingRules.work(payload, previous, JSONObject().put("customers", customers).put("orders", orders))
AppointmentRules.worklog(payload, previous, current)
validateRevierContext(payload, false)
if (payload.has("material_cost") && payload.optString("material_cost").isBlank()) payload.remove("material_cost")
PixelMcpWriteTools.applyMaterialCost(payload)
WorklogRules.validate(payload, isNew)
WorklogRelationRules.validate(payload, previous, master.getJSONArray("photos"), master.getJSONArray("invoices"))
var entityId = payload.optString("id")
if (entityId.isEmpty()) {
entityId = "wrk_mobile_" + UUID.randomUUID().toString().replace("-", "")
}
payload.put("id", entityId)
if (payload.optString("source").trim().isEmpty()) {
payload.put("source", (if (previous == null) "Pixel offline" else previous.optString("source", "Pixel offline")))
}
prepareManualChange("worklog", payload, previous)
var actionId = "worklog_change_" + UUID.randomUUID().toString().replace("-", "")
var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

var values = ContentValues()
values.put("id", actionId)
values.put("kind", "worklog")
values.put("payload", payload.toString())
values.put("created_at", isoNow())
database.insertOrThrow("pending", null, values)
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
return entityId
}
@Throws(Exception::class) fun startWorkSession(payload: JSONObject): JSONObject {

var request = JSONObject(payload.toString())
request.remove("_request_input_hash")
var inputHash = PixelMcpWriteTools.hash(request)
request.put("_request_input_hash", inputHash)
var db = getWritableDatabase()
beginBusinessTransaction(db)
try {

var result = startWorkSessionLocked(request)
db.setTransactionSuccessful()
return result

} finally {
db.endTransaction()
}
}
private @Throws(Exception::class) fun startWorkSessionLocked(payload: JSONObject): JSONObject {

var active = workSessions().current()
if (!(("IDLE" == active.optString("state")))) {
return workSessions().start(payload)
}
var master = PixelMcpReadTools.effective(getState())
var customer = payload.optString("customer_id").trim()
var order = payload.optString("order_id").trim()
if (!(customer.isEmpty()) && !(containsId(master.getJSONArray("customers"), customer))) {
throw IllegalArgumentException("Der ausgewählte Kunde existiert nicht.")
}
if (!(customer.isEmpty())) {
BillingRules.work(payload, null, JSONObject().put("customers", master.getJSONArray("customers")).put("orders", master.getJSONArray("orders")))
}
if (!(order.isEmpty())) {
var row = findById(master.getJSONArray("orders"), order)
if (row == null || !((customer == row.optString("customer_id")))) {
throw IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.")
}
}
if (!(payload.optString("appointment_id").isEmpty())) {
var appointment = PixelMcpWriteTools.find(PixelMcpReadTools.effective(getState()).getJSONArray("appointments"), payload.getString("appointment_id"))
if (!(AppointmentRules.active(appointment)) || (appointment.optString("status") == "draft") || !((customer == appointment.optString("customer_id"))) || !((order == appointment.optString("order_id")))) {
throw IllegalArgumentException("Termin, Kunde und Auftrag passen nicht zu einer aktiven Arbeit.")
}
}
validateRevierContext(payload, true)
return workSessions().start(payload)
}
@Throws(Exception::class) fun completeWorkSession(sessionId: String, payload: JSONObject, requestId: String): String {

var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

var sessions = workSessions()
var session = sessions.current(sessionId)
if (!((WorkSessionStore.STOPPED == session.optString("state"))) && !((WorkSessionStore.FINALIZED == session.optString("state")))) {
throw IllegalStateException("Bitte die laufende Arbeit zuerst stoppen.")
}
var stableId = "wrk_session_" + sessionId.replace("ses_pixel_", "")
if ((WorkSessionStore.FINALIZED == session.optString("state"))) {
if ((stableId == session.optString("finalized_worklog_id"))) {
return stableId
}
throw IllegalStateException("Diese Arbeitssitzung ist bereits abgeschlossen.")
}
payload.remove("_require_order")
payload.remove("require_order")
payload.put("id", stableId).put("work_date", session.getString("work_date")).put("hours", session.getDouble("hours"))
if (session.optBoolean("review_required") || session.optBoolean("pause_unverified")) {
throw IllegalArgumentException("Pausenzeiten wegen widersprüchlicher Geräteuhr bitte prüfen.")
}
payload.put("break_minutes", session.optInt("break_minutes", 0))
payload.remove("start_time")
payload.remove("end_time")
var timerStamp = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(java.time.ZoneId.of("Europe/Vienna"))
var timing = "Timer-Nachweis (Europe/Vienna): " + timerStamp.format(java.time.Instant.ofEpochMilli(session.getLong("started_at_ms"))) + " bis " + timerStamp.format(java.time.Instant.parse(session.getString("updated_at"))) + "; " + session.getDouble("hours") + " h netto, " + session.optInt("break_minutes", 0) + " min Pause."
if (("manual_review" == session.optString("time_origin"))) {
timing = "Geprüfte Timerzeit: " + session.getDouble("hours") + " h netto, " + session.optInt("break_minutes", 0) + " min Pause. Keine Endzeit rekonstruiert. Grund: " + session.optString("correction_reason")
}
if (!(payload.optString("notes").contains(timing))) {
payload.put("notes", java.lang.String.valueOf(((if (payload.optString("notes").isEmpty()) "" else payload.getString("notes") + "\n"))) + timing)
}
if (payload.optString("customer_id").trim().isEmpty()) {
payload.put("customer_id", session.optString("customer_id"))
}
if (!(payload.has("order_id"))) {
payload.put("order_id", session.optString("order_id"))
}
if (!(session.optString("order_id").isEmpty()) && !((session.optString("order_id") == payload.optString("order_id")))) {
throw IllegalArgumentException("Der Auftrag der gestarteten Sitzung bleibt erhalten.")
}
if (!(session.optString("customer_id").isEmpty()) && !((session.optString("customer_id") == payload.optString("customer_id")))) {
throw IllegalArgumentException("Die Sitzung gehört zu einem anderen Einsatzkunden.")
}
if (!(payload.has("billing_customer_id")) && !(session.optString("billing_customer_id").isEmpty())) {
payload.put("billing_customer_id", session.getString("billing_customer_id"))
}
if (payload.optString("location").trim().isEmpty() && (payload.optString("customer_id") == session.optString("customer_id"))) {
payload.put("location", session.optString("location"))
}
if (!(session.optString("appointment_id").isEmpty())) {
payload.put("appointment_id", session.getString("appointment_id"))
}
retainSessionContext(payload, session)
var saved = saveWorklog(payload)
var photos = PixelMcpReadTools.effective(getState())
var sessionPhotos = photos.getJSONArray("photos")
run {
var i: Int = 0
var firstIteration39 = true
while (true) {
if (firstIteration39) firstIteration39 = false else { i++ }
if (!(i < sessionPhotos.length())) break

var old = sessionPhotos.getJSONObject(i)
if (!((sessionId == old.optString("session_id"))) || !(old.optString("worklog_id").isEmpty())) {
continue
}
var linked = DriveJournalRules.clean(old)
linked.put("worklog_id", saved)
prepareManualChange("photo", linked, old)
saveAction("photo", "pho_mobile_", linked)

}
}
sessions.finalizeSession(sessionId, saved, requestId)
database.setTransactionSuccessful()
return saved

} finally {
database.endTransaction()
}
}
private @Throws(Exception::class) fun validateRevierContext(payload: JSONObject, starting: Boolean): Unit {

var property = payload.optString("property_id")
var visit = payload.optString("visit_id")
if (!(visit.isEmpty())) {
var raw = ownerValue("revier:v1:entity:visit:" + visit, "")!!
if (raw.isEmpty()) {
throw IllegalArgumentException("Der ausgewählte Besuch existiert nicht.")
}
var row = JSONObject(raw)
RevierRules.version(row)
if (!((property == row.getString("property_id"))) || !((payload.optString("customer_id") == row.getString("customer_id"))) || !((payload.optString("order_id") == row.optString("order_id")))) {
throw IllegalArgumentException("Besuch, Objekt, Kunde und Auftrag passen nicht zusammen.")
}
if (starting && !(RevierRules.activeVisit(row))) {
throw IllegalArgumentException("Dieser Besuch ist abgesagt oder abgeschlossen. Zuerst den Besuch prüfen.")
}
}
if (!(property.isEmpty())) {
var raw = ownerValue("revier:v1:entity:property:" + property, "")!!
if (raw.isEmpty()) {
throw IllegalArgumentException("Das ausgewählte Objekt existiert nicht.")
}
var row = JSONObject(raw)
RevierRules.version(row)
if (!((payload.optString("customer_id") == row.getString("customer_id"))) || starting && !(row.optBoolean("active", true))) {
throw IllegalArgumentException("Objekt und Einsatzkunde passen nicht zu einer neuen Arbeit.")
}
}
}
override @Throws(Exception::class) fun execute(tool: String, args: JSONObject): JSONObject {

if (AppointmentTools.write(tool) || OfferTools.write(tool) || PixelMcpOwnerTools.write(tool)) {
return PixelOwnerAccess(context, this).execute(tool, args)
}
if (PixelMcpSessionTools.handles(tool)) {
return executeSession(tool, args)
}
if (PixelMcpWriteTools.handles(tool)) {
PixelMcpWriteTools.validate(args, PixelMcpWriteTools.schema(tool))
} else {
if (PixelMcpEntityWriteTools.handles(tool)) {
PixelMcpEntityWriteTools.validate(tool, args)
} else {
throw PixelMcpWriteTools.Rejected("UNKNOWN_TOOL", "Diese Schreibfunktion ist nicht freigegeben.")
}
}
var clientId = "mcp_pixel_" + PixelMcpWriteTools.hash(args.getString("request_id")).substring(0, 40)
var fingerprint = PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", args))
var database = getWritableDatabase()
beginBusinessTransaction(database)
var result: JSONObject
try {

var receipt = receipt(clientId)
if (receipt != null) {
if (!((fingerprint == receipt.optString("fingerprint")))) {
throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Diese Anfragekennung wurde bereits für andere Angaben verwendet. Bitte die ursprüngliche Anfrage prüfen.")
}
result = JSONObject().put("entity_type", receipt.optString("entity_type", "worklog")).put("entity_id", receipt.optString("entity_id", receipt.optString("work_record_id"))).put("replayed", true).put("sync", status(clientId, getState()))
} else {
if (snapshotText("").isEmpty()) {
putOwnerValue("snapshot", emptyBootstrap().toString())
}
var prepared = (if (PixelMcpWriteTools.handles(tool)) JSONObject().put("entity_type", "worklog").put("payload", PixelMcpWriteTools.prepare(tool, args, getState(), clientId)) else PixelMcpEntityWriteTools.prepare(tool, args, getState(), clientId))
var entityType = prepared.getString("entity_type")
var payload = prepared.getJSONObject("payload")
var source = payload.optString("source", "ChatGPT Sprache")
var changedAt = isoNow()
payload.put("updated_at", changedAt)
if (!(payload.has("created_at"))) {
payload.put("created_at", changedAt)
}
var existingActions = HashSet<String>()
var priorActions = pendingActions()
run {
var i: Int = 0
var firstIteration40 = true
while (true) {
if (firstIteration40) firstIteration40 = false else { i++ }
if (!(i < priorActions.length())) break

existingActions.add(priorActions.getJSONObject(i).getString("client_id"))

}
}
var entityId: String
when (entityType) {
"customer" -> {
entityId = saveCustomer(payload)
}
"order" -> {
entityId = saveOrder(payload)
}
else -> {
entityId = saveWorklog(payload)
}
}
payload.put("source", source)
if (testLocalOnly(entityType, payload)) {
payload.put("_local_only", true)
}
database.rawQuery("SELECT id,payload FROM pending WHERE kind=?", arrayOf(entityType)).use { rows ->

while (rows.moveToNext()) {
if (!(existingActions.contains(rows.getString(0))) && (entityId == JSONObject(rows.getString(1)).optString("id"))) {
database.delete("pending", "id=?", arrayOf(rows.getString(0)))
}
}

}
var action = ContentValues()
action.put("id", clientId)
action.put("kind", entityType)
action.put("payload", payload.toString())
action.put("created_at", isoNow())
database.insertOrThrow("pending", null, action)
var metadata = JSONObject().put("fingerprint", fingerprint).put("tool", tool).put("entity_type", entityType).put("entity_id", entityId).put("work_record_id", (if ((entityType == "worklog")) entityId else "")).put("uploaded", false).put("test_local_only", payload.optBoolean("_local_only"))
var saved = ContentValues()
saved.put("id", "receipt_" + clientId)
saved.put("kind", "mcp_receipt")
saved.put("payload", metadata.toString())
saved.put("created_at", isoNow())
database.insertOrThrow("pending", null, saved)
result = JSONObject().put("entity_type", entityType).put("entity_id", entityId).put("replayed", false).put("sync", status(clientId, getState()))
}
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
if (context is McpSyncHost) {
((context as McpSyncHost)).requestMcpSync()
}
if (("worklog" == result.optString("entity_type"))) {
result.put("work_record_id", result.getString("entity_id"))
}
result.put("sync", PixelMcpBusinessTools.sync(result.getJSONObject("sync"), getState())).put("local_state", "local_saved").put("sync_state", result.getJSONObject("sync").optString("sync_state"))
return JSONObject().put("ok", true).put("data", result).put("message", "Auf dem Pixel gespeichert. Drive dient ausschließlich der getrennt geprüften Vollsicherung.")
}
/** Read the original idempotency proof without replaying any command. */
fun lookupNativeReceipt(request: String): JSONObject? = receipt("mcp_pixel_" + PixelMcpWriteTools.hash(request).substring(0, 40))

private @Throws(Exception::class) fun receipt(clientId: String): JSONObject? {

getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE id=? AND kind='mcp_receipt'", arrayOf("receipt_" + clientId)).use { row ->

return (if (row.moveToFirst()) JSONObject(row.getString(0)) else null)

}
}
private @Throws(Exception::class) fun executeSession(tool: String, args: JSONObject): JSONObject {

PixelMcpWriteTools.validate(args, PixelMcpSessionTools.schema(tool))
var clientId = "mcp_pixel_" + PixelMcpWriteTools.hash(args.getString("request_id")).substring(0, 40)
var fingerprint = PixelMcpWriteTools.hash(JSONObject().put("tool", tool).put("args", args))
var database = getWritableDatabase()
beginBusinessTransaction(database)
var result: JSONObject
try {

var existing = receipt(clientId)
if (existing != null) {
if (!((fingerprint == existing.optString("fingerprint")))) {
throw PixelMcpWriteTools.Rejected("REPLAY_MISMATCH", "Anfragekennung wurde für andere Angaben verwendet.")
}
result = JSONObject(existing.getJSONObject("result").toString()).put("replayed", true)
} else {
var timer = workSessions()
var id = args.optString("session_id")
var request = "mcp-session-" + clientId
if ((tool == "finish_work")) {
var current = timer.current(id)
if (java.util.Arrays.asList("RUNNING", "PAUSED").contains(current.optString("state"))) {
timer.stop(id, request + "-stop")
}
}
var payload = PixelMcpSessionTools.prepare(tool, args, getState())
var session: JSONObject
when (tool) {
"review_work_session" -> {
payload.put("request_id", request)
session = timer.reviewTime(id, payload)
}
"start_work" -> {
payload.put("request_id", request)
session = startWorkSession(payload)
}
"start_break" -> {
session = timer.pause(id, request)
}
"end_break" -> {
session = timer.resume(id, request)
}
"stop_work" -> {
session = timer.stop(id, request)
}
"continue_work" -> {
session = timer.continueStopped(id, request)
}
"discard_work_session" -> {
session = timer.discard(id, request)
}
"restore_work_session" -> {
session = timer.restore(id, request)
}
else -> {
payload.remove("request_id")
payload.remove("session_id")
var saved = completeWorkSession(id, payload, request)
payload.put("id", saved).put("_mcp_create", true)
if (testLocalOnly("worklog", payload)) {
payload.put("_local_only", true)
}
database.rawQuery("SELECT id,payload FROM pending WHERE kind='worklog'", null).use { rows ->

while (rows.moveToNext()) {
if ((saved == JSONObject(rows.getString(1)).optString("id"))) {
database.delete("pending", "id=?", arrayOf(rows.getString(0)))
}
}

}
var action = ContentValues()
action.put("id", clientId)
action.put("kind", "worklog")
action.put("payload", payload.toString())
action.put("created_at", isoNow())
database.insertOrThrow("pending", null, action)
session = timer.current(id)
}
}
result = JSONObject().put("client_id", clientId).put("session", session).put("replayed", false).put("local_state", "local_saved").put("sync_state", (if (((tool == "complete_work_session") || (tool == "finish_work"))) "sync_pending" else "not_applicable")).put("requires_work_record_completion", (tool == "stop_work"))
var meta = JSONObject().put("fingerprint", fingerprint).put("tool", tool).put("entity_type", (if (((tool == "complete_work_session") || (tool == "finish_work"))) "worklog" else "work_session")).put("entity_id", (if (((tool == "complete_work_session") || (tool == "finish_work"))) session.optString("finalized_worklog_id") else session.getString("id"))).put("work_record_id", session.optString("finalized_worklog_id")).put("uploaded", false).put("test_local_only", payload.optBoolean("_local_only")).put("result", result)
var row = ContentValues()
row.put("id", "receipt_" + clientId)
row.put("kind", "mcp_receipt")
row.put("payload", meta.toString())
row.put("created_at", isoNow())
database.insertOrThrow("pending", null, row)
}
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
if (((tool == "complete_work_session") || (tool == "finish_work"))) {
if (context is McpSyncHost) {
((context as McpSyncHost)).requestMcpSync()
}
var sync = status(clientId, getState())
result.put("sync", sync).put("sync_state", sync.optString("sync_state"))
}
return JSONObject().put("ok", true).put("data", result).put("message", (if ((tool == "stop_work")) "Zeiterfassung lokal gestoppt. Tätigkeit prüfen und mit complete_work_session als Arbeitsaufzeichnung abschließen." else "Aktion dauerhaft auf dem Pixel gespeichert. Timer und Arbeitsaufzeichnungen bleiben lokal; Sicherungen werden als unverschlüsselte ZIP auf dem Pixel gespeichert."))
}
override @Throws(Exception::class) fun status(clientId: String, state: JSONObject): JSONObject {

var master = state.getJSONObject("master")
var metadata = receipt(clientId)
var ownerReceipt = ownerValue("receipt:" + clientId, "")!!
var ownerSaved: Boolean = !(ownerReceipt.isEmpty())
var providerReadback: Boolean = DriveJournalRules.ids(master.optJSONArray("drive_delivered_ids")).contains(clientId) || (metadata != null && metadata.optBoolean("uploaded"))
var ownerResult = (if (ownerSaved) JSONObject(ownerReceipt).optJSONObject("result") else null)
if (metadata != null && ("work_session" == metadata.optString("entity_type"))) {
return JSONObject().put("client_id", clientId).put("status", "local_saved").put("sync_state", "not_applicable").put("local_state", "local_saved").put("saved_locally", true).put("confirmed_by_app", false).put("uploaded_to_drive", false).put("entity_id", metadata.optString("entity_id")).put("message", "Timeraktion dauerhaft lokal gespeichert; keine Drive-Arbeitsaufzeichnung. Aktuellen Zustand mit get_active_work_session lesen.")
}
var phase = "unknown"
var accepted = master.optJSONArray("accepted_ids")
run {
var i: Int = 0
var firstIteration41 = true
while (true) {
if (firstIteration41) firstIteration41 = false else { i++ }
if (!(accepted != null && i < accepted.length())) break

if ((clientId == accepted.optString(i))) {
phase = "accepted"
}

}
}
if (!((phase == "accepted"))) {
var pending = state.optJSONArray("pending")
var queued: Boolean = false
run {
var i: Int = 0
var firstIteration42 = true
while (true) {
if (firstIteration42) firstIteration42 = false else { i++ }
if (!(pending != null && i < pending.length())) break

if ((clientId == pending.getJSONObject(i).optString("client_id"))) {
queued = true
}

}
}
if (queued) {
phase = (if (metadata != null && metadata.optBoolean("uploaded")) "transferred" else "queued")
} else {
if (metadata != null) {
phase = "superseded"
}
}
if (DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(clientId)) {
phase = (if (providerReadback) "transferred" else "applied_on_pixel")
}
var errors = master.optJSONArray("sync_errors")
run {
var i: Int = 0
var firstIteration43 = true
while (true) {
if (firstIteration43) firstIteration43 = false else { i++ }
if (!(errors != null && i < errors.length())) break

var error = errors.optJSONObject(i)
if (error != null && (clientId == error.optString("client_id"))) {
phase = "failed"
}

}
}
var journalErrors = master.optJSONArray("drive_journal_errors")
run {
var i: Int = 0
var firstIteration44 = true
while (true) {
if (firstIteration44) firstIteration44 = false else { i++ }
if (!(journalErrors != null && i < journalErrors.length())) break

var error = journalErrors.optJSONObject(i)
if (error != null && (clientId == error.optString("client_id"))) {
phase = "failed"
}

}
}
}
if (master.optJSONObject("drive_resolved_actions") != null && master.getJSONObject("drive_resolved_actions").has(clientId)) {
phase = "resolved"
}
return PixelMcpBusinessTools.sync(JSONObject().put("client_id", clientId).put("status", phase).put("confirmed_by_app", (phase == "accepted")).put("test_local_only", metadata != null && metadata.optBoolean("test_local_only") || ownerResult != null && ownerResult.optBoolean("test_local_only")).put("saved_locally", metadata != null || ownerSaved).put("uploaded_to_drive", (phase == "accepted") || providerReadback || (metadata != null && metadata.optBoolean("uploaded"))).put("provider_readback", providerReadback || (metadata != null && metadata.optBoolean("uploaded"))).put("entity_type", (if (metadata == null) ((if (ownerResult == null) "" else ownerResult.optString("entity_type"))) else metadata.optString("entity_type", "worklog"))).put("entity_id", (if (metadata == null) ((if (ownerResult == null) "" else ownerResult.optString("entity_id"))) else metadata.optString("entity_id", metadata.optString("work_record_id")))).put("work_record_id", (if (metadata == null) "" else metadata.optString("work_record_id"))).put("applied_on_pixel", DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(clientId)).put("confirmation_scope", (if ((phase == "accepted")) "web_import_receipt" else (if ((providerReadback || metadata != null && metadata.optBoolean("uploaded"))) "drive_provider_readback" else "local_only"))).put("cloud_confirmed", false).put("message", (if ((phase == "resolved")) "Die Konfliktentscheidung wurde übernommen. Die ursprüngliche Aktion bleibt im Journal erhalten." else (if ((phase == "accepted")) "Im Gesamtstand übernommen und bestätigt. Cloud-Upload wird durch Drive durchgeführt." else (if ((phase == "failed")) "Die Änderung benötigt eine Prüfung. Lokale Daten bleiben erhalten." else (if ((phase == "superseded")) "Die vorgemerkte Änderung wurde in der App ersetzt. Bitte den Eintrag öffnen." else (if ((phase == "transferred")) "Änderung an die Drive-App übergeben und zurückgelesen; auf dem Pixel nutzbar. Der Google-Cloud-Upload ist nicht unabhängig bestätigt." else (if ((phase == "applied_on_pixel")) "Auf dem Pixel übernommen; Provider-Rücklesen noch nicht nachgewiesen." else (if ((phase == "queued")) "Auf dem Pixel gesichert. Drive-Abgleich steht noch aus." else "Für diese Anfrage liegt noch keine Bestätigung vor.")))))))), state)
}
@Throws(Exception::class) fun markUploaded(actions: JSONArray): Unit {

var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

run {
var i: Int = 0
var firstIteration45 = true
while (true) {
if (firstIteration45) firstIteration45 = false else { i++ }
if (!(i < actions.length())) break

var clientId = actions.getJSONObject(i).optString("client_id")
var metadata = receipt(clientId)
if (metadata != null) {
metadata.put("uploaded", true)
var value = ContentValues()
value.put("payload", metadata.toString())
database.update("pending", value, "id=? AND kind='mcp_receipt'", arrayOf("receipt_" + clientId))
}

}
}
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
}
private @Throws(Exception::class) fun findById(rows: JSONArray, id: String): JSONObject? {

if (rows == null) {
return null
}
run {
var i: Int = 0
var firstIteration46 = true
while (true) {
if (firstIteration46) firstIteration46 = false else { i++ }
if (!(i < rows.length())) break

var row = rows.optJSONObject(i)
if (row != null && (id == row.optString("id"))) {
return row
}

}
}
return null
}
private @Throws(Exception::class) fun containsId(rows: JSONArray, id: String): Boolean {

return findById(rows, id) != null
}
@Throws(Exception::class) fun saveEditorRecord(kind: String, input: JSONObject): String {

var payload = JSONObject(input.toString())
var request = payload.optString("_request_id")
payload.remove("_request_id")
if ((kind == "worklog") && payload.optString("_session_id").isEmpty()) {
payload.remove("_session_id")
}
if (!(request.isEmpty()) && !(request.matches(Regex("[A-Za-z0-9_-]{8,200}")))) {
throw IllegalArgumentException("Ungültige Aktionskennung.")
}
var db = getWritableDatabase()
beginBusinessTransaction(db)
try {

var key = "editor_commit:" + PixelMcpWriteTools.hash(request)
var fingerprint = PixelMcpWriteTools.hash(JSONObject().put("kind", kind).put("payload", payload))
if (!(request.isEmpty())) {
var raw = ownerValue(key, "")!!
if (!(raw.isEmpty())) {
var receipt = JSONObject(raw)
if (!((fingerprint == receipt.getString("fingerprint")))) {
throw IllegalArgumentException("REQUEST_ID_REUSED: Ursprüngliche Speicherung unverändert prüfen.")
}
db.setTransactionSuccessful()
return receipt.getString("id")
}
}
var id: String
when (kind) {
"customer" -> {
id = saveCustomer(payload)
}
"order" -> {
id = saveOrder(payload)
}
"worklog" -> {
run {
var work = JSONObject(payload.toString())
var session = work.optString("_session_id")
work.remove("_session_id")
if (!(session.isEmpty()) && !(request.isEmpty()) && (WorkSessionStore.FINALIZED == workSessions().current(session).optString("state"))) {
throw IllegalArgumentException("Diese Arbeitssitzung ist bereits abgeschlossen. Gespeicherte Arbeit öffnen; eine andere Bearbeitung wurde nicht angewendet.")
}
id = (if (session.isEmpty()) saveWorklog(work) else completeWorkSession(session, work, "ui-finalize-" + session))
}
}
else -> {
throw IllegalArgumentException("Unbekannter Editor.")
}
}
if (!(request.isEmpty())) {
putOwnerValue(key, JSONObject().put("formatVersion", 1).put("fingerprint", fingerprint).put("id", id).toString())
}
db.setTransactionSuccessful()
return id

} finally {
db.endTransaction()
}
}
@Throws(Exception::class) fun saveCustomer(payload: JSONObject): String {
var payload = payload

if (payload.optString("name").trim().isEmpty()) {
throw IllegalArgumentException("Der Kundenname fehlt.")
}
var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

var master = PixelMcpReadTools.effective(getState())
var customers = master.getJSONArray("customers")
payload = ManualChangeRules.editor(payload, findById(customers, payload.optString("id")), "customer")
TestDataRules.customerChange(payload, findById(customers, payload.optString("id")))
GardenRules.normalize(payload)
BillingRules.customerConfig(payload, customers)
if (findById(customers, payload.optString("id")) == null) {
EntityProtectionRules.validateNewCustomer(payload, customers)
}
var entityId = payload.optString("id")
if (entityId.isEmpty()) {
entityId = "cus_mobile_" + UUID.randomUUID().toString().replace("-", "")
}
payload.put("id", entityId)
prepareManualChange("customer", payload, findById(customers, entityId))
if (TestDataRules.customer(payload)) {
payload.put("_local_only", true)
}
var actionId = "customer_change_" + UUID.randomUUID().toString().replace("-", "")
var values = ContentValues()
values.put("id", actionId)
values.put("kind", "customer")
values.put("payload", payload.toString())
values.put("created_at", isoNow())
database.insertOrThrow("pending", null, values)
database.setTransactionSuccessful()
return entityId

} finally {
database.endTransaction()
}
}
@Throws(Exception::class) fun saveReviewedOrder(request: JSONObject): String {

var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

var payload = OrderReviewRules.prepare(getState(), request)
var id = saveAction("order", "ord_mobile_", payload)
database.setTransactionSuccessful()
return id

} finally {
database.endTransaction()
}
}
@Throws(Exception::class) fun saveOrder(payload: JSONObject): String {

var tx = getWritableDatabase()
beginBusinessTransaction(tx)
try {

var result = saveOrderLocked(payload)
tx.setTransactionSuccessful()
return result

} finally {
tx.endTransaction()
}
}
private @Throws(Exception::class) fun saveOrderLocked(payload: JSONObject): String {
var payload = payload

var current = PixelMcpReadTools.effective(getState())
payload = ManualChangeRules.editor(payload, findById(current.getJSONArray("orders"), payload.optString("id")), "order")
BillingRules.order(payload, current)
if (payload.optString("customer_id").isEmpty() || payload.optString("title").trim().isEmpty()) {
throw IllegalArgumentException("Kunde und Auftragsbezeichnung sind erforderlich.")
}
var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

var master = PixelMcpReadTools.effective(getState())
if (!(containsId(master.getJSONArray("customers"), payload.optString("customer_id")))) {
throw IllegalArgumentException("Der ausgewählte Kunde existiert nicht.")
}
if (!(payload.optString("scheduled_date").isEmpty()) && !(payload.isNull("scheduled_date"))) {
try {

java.time.LocalDate.parse(payload.optString("scheduled_date"))

} catch (error: Exception) {
throw IllegalArgumentException("Bitte ein gültiges Auftragsdatum auswählen.")
}
}
var entityId = payload.optString("id")
EntityProtectionRules.validateOrderChange(payload, findById(master.getJSONArray("orders"), entityId), master.getJSONArray("worklogs"), master.getJSONArray("invoices"), master.getJSONArray("photos"))
if (entityId.isEmpty()) {
entityId = "ord_mobile_" + UUID.randomUUID().toString().replace("-", "")
}
payload.put("id", entityId)
payload.put("source", "Pixel offline")
prepareManualChange("order", payload, findById(master.getJSONArray("orders"), entityId))
var saved = saveAction("order", "ord_mobile_", payload)
database.setTransactionSuccessful()
return saved

} finally {
database.endTransaction()
}
}
@Throws(Exception::class) fun savePhoto(payload: JSONObject): String {
// SQLite serializes writes, including owner commands that already hold a transaction.
// Do not retain the helper monitor while waiting for that writer connection.
synchronized(photoOperations) { activePhotoOperations++ }
try { return savePhotoTransaction(payload) }
finally { synchronized(photoOperations) { activePhotoOperations--; photoOperations.notifyAll() } }
}
private @Throws(Exception::class) fun savePhotoTransaction(payload: JSONObject): String {
var payload = payload

payload = JSONObject(payload.toString())
payload.remove("_capture_context")
if (payload.has("_photo_quality") && !(java.util.Arrays.asList("original", "high", "optimized").contains(payload.getString("_photo_quality")))) {
throw IllegalArgumentException("Ungültiges Foto-Qualitätsprofil. Die Auswahl bleibt als Entwurf erhalten.")
}
if (payload.optString("customer_id").isEmpty() || payload.optString("data").isEmpty()) {
throw IllegalArgumentException("Kunde und Foto sind erforderlich.")
}
if (!(payload.optString("capture_time").isEmpty())) {
java.time.Instant.parse(payload.getString("capture_time"))
}
var immutableRequest = JSONObject(payload.toString())
immutableRequest.remove("thumbnail_data")
var request = payload.optString("_photo_request_id")
var fingerprint = PixelMcpWriteTools.hash(immutableRequest)
if (!(request.isEmpty()) && !(request.matches(Regex("[A-Za-z0-9_-]{8,128}")))) {
throw IllegalArgumentException("Ungültige Foto-Anfragekennung.")
}
if (!(request.isEmpty())) {
var saved = ownerValue("photo-ui:" + request, "")!!
if (!(saved.isEmpty())) {
var receipt = JSONObject(saved)
if (!((fingerprint == receipt.getString("hash")))) {
throw IllegalArgumentException("Die Foto-Anfragekennung enthält andere Angaben.")
}
return receipt.getString("id")
}
}
var entityId = payload.optString("id")
if (entityId.isEmpty()) {
entityId = "pho_mobile_" + UUID.randomUUID().toString().replace("-", "")
}
payload.put("id", entityId)
var prepared: PhotoAttachmentStore.Prepared? = null
var committed: Boolean = false
var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

if (!(request.isEmpty())) {
var saved = ownerValue("photo-ui:" + request, "")!!
if (!(saved.isEmpty())) {
var receipt = JSONObject(saved)
if (!((fingerprint == receipt.getString("hash")))) {
throw IllegalArgumentException("Die Foto-Anfragekennung enthält andere Angaben.")
}
database.setTransactionSuccessful()
committed = true
return receipt.getString("id")
}
}
var master = PixelMcpReadTools.effective(getState())
if (!(payload.optString("session_id").isEmpty())) {
var session = workSessions().current(payload.getString("session_id"))
if (!((payload.getString("customer_id") == session.optString("customer_id"))) || !((payload.optString("order_id") == session.optString("order_id")))) {
throw IllegalArgumentException("Foto und gestartete Sitzung gehören nicht zum selben Kunden/Auftrag.")
}
retainSessionContext(payload, session)
if (!(session.optString("finalized_worklog_id").isEmpty())) {
payload.put("worklog_id", session.getString("finalized_worklog_id"))
}
}
validateRevierContext(payload, false)
EntityProtectionRules.validatePhoto(payload, master.getJSONArray("customers"), master.getJSONArray("orders"), master.getJSONArray("worklogs"))
if (!(payload.has("_photo_quality"))) {
payload.put("_photo_quality", JSONObject(ownerValue("settings", "{}")).optString("photo_quality", "optimized"))
}
prepared = photoAttachments.prepare(payload)
prepareManualChange("photo", payload, findById(master.getJSONArray("photos"), payload.optString("id")))
run {
var keys = prepared.metadata.keys()
var firstIteration49 = true
while (true) {
if (firstIteration49) firstIteration49 = false else {  }
if (!(keys.hasNext())) break

var key = keys.next()
payload.put(key, prepared.metadata.get(key))

}
}
var original = payload.optString("original_name", "foto.jpg").replace('\\', '/')
var slash: Int = original.lastIndexOf('/')
if (slash >= 0) {
original = original.substring(slash + 1)
}
if (original.length > 240) {
original = original.substring(0, 240)
}
payload.put("original_name", original)
payload.remove("data")
payload.remove("_photo_quality")
payload.put("sync_state", "local_saved")
payload.put("filename", prepared.metadata.getString("storage_name"))
payload.remove("thumbnail_data")
saveAttachmentMetadata(database, entityId, prepared.metadata, "local")
var saved = saveAction("photo", "pho_mobile_", payload)
if (!(request.isEmpty())) {
putOwnerValue("photo-ui:" + request, JSONObject().put("hash", fingerprint).put("metadata_hash", photoMetadataHash(immutableRequest)).put("id", saved).toString())
}
database.setTransactionSuccessful()
committed = true
return saved

} finally {
database.endTransaction()
if (!(committed) && prepared != null && prepared.created) {
try {
getReadableDatabase().rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? LIMIT 1", arrayOf(prepared.metadata.getString("content_sha256"))).use { referenced ->

if (!(referenced.moveToFirst())) {
photoAttachments.discardCreated(prepared.metadata)
}

}
} catch (ignored: Exception) {

}
}
}
}
@Throws(Exception::class) fun prepareEquipmentProfilePhoto(upload: String, equipment: String, request: String): JSONObject {

var files = LocalOwnerRepository(context, this)
var meta = files.fileMeta(upload)
var bytes = files.fileBytes(upload)
EquipmentProfilePhotoRules.upload(meta, bytes)
var photo = storeEquipmentProfilePhoto(bytes, meta.getString("mime_type"), equipment, request)
meta.put("attached_photo", photo.getString("id")).put("equipment_id", equipment)
files.putFile(upload, meta, ByteArray(0))
return photo
}
@Throws(Exception::class) fun prepareEquipmentProfilePhotoDraft(source: JSONObject, equipment: String, request: String): JSONObject {

try {

var consumed = "equipment_photo_draft_consumed:" + PixelMcpWriteTools.hash(source)
if (!(ownerValue(consumed, "")!!.isEmpty())) {
throw PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE", "Diese Fotoauswahl wurde bereits gespeichert. Aktuellen Gerätestand prüfen.")
}
var editState = BrowserDraftRepository(context, this).read(JSONObject().put("formatVersion", 1).put("action", "get").put("key", "hausgarten.form.sessions.v1").toString())
EquipmentProfilePhotoRules.draftSession(editState, source, request)
var media = BrowserMediaRepository(context, this)
var current = media.readRecord("customer-draft:" + source.optString("media_key"))
var ref = EquipmentProfilePhotoRules.draft(current, source)
media.assertReadableReference(current.getString("record_id"), ref)
var directory = File(context.getFilesDir(), "browser-media")
BrowserMediaFiles.verifyReference(directory, ref)
var bytes = java.nio.file.Files.readAllBytes(BrowserMediaFiles.referenceFile(directory, ref).toPath())
if (bytes.size.toLong() != ref.getLong("byte_size") || !((McpFileTransfer.sha(bytes) == ref.getString("sha256")))) {
throw IOException("BROWSER_MEDIA_ORIGINAL_MISSING_OR_CHANGED")
}
var photo = storeEquipmentProfilePhoto(bytes, ref.getString("mime_type"), equipment, request)
putOwnerValue(consumed, request)
return photo

} catch (known: PixelMcpWriteTools.Rejected) {
throw known
} catch (unavailable: Exception) {
throw PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE", "Fotoentwurf nicht eindeutig lesbar. Auswahl erneut prüfen; Geräteangaben bleiben erhalten.")
}
}
private @Throws(Exception::class) fun storeEquipmentProfilePhoto(bytes: ByteArray, mime: String, equipment: String, request: String): JSONObject {

var prepared: PhotoAttachmentStore.Prepared
try {

prepared = photoAttachments.prepare(JSONObject().put("data", "data:" + PhotoAttachmentStore.canonicalMime(mime) + ";base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).put("role", "profile").put("_photo_quality", "optimized"))

} catch (invalid: IllegalArgumentException) {
throw PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_INVALID", "Das ausgewählte Foto ist beschädigt oder nicht unterstützt. Bitte eine andere Datei auswählen.")
} catch (unavailable: IOException) {
throw PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_UNAVAILABLE", "Das Foto konnte noch nicht im privaten Speicher gesichert werden. Geräteangaben und Fotoentwurf bleiben erhalten.")
}
var photo = JSONObject(prepared.metadata.toString()).put("format_version", 1).put("id", "eqp_photo_" + PixelMcpWriteTools.hash(request).substring(0, 32)).put("equipment_id", equipment)
EquipmentProfilePhotoRules.validate(photo, equipment)
saveAttachmentMetadata(getWritableDatabase(), photo.getString("id"), photo, "local")
return photo
}
private @Throws(Exception::class) fun saveAttachmentMetadata(database: SQLiteDatabase, photoId: String, metadata: JSONObject, state: String): Unit {

var hash = metadata.optString("content_sha256")
var mime = PhotoAttachmentStore.canonicalMime(metadata.optString("mime_type"))
var name = metadata.optString("storage_name")
var role = metadata.optString("role", "documentation").trim().lowercase(Locale.ROOT)
var extension = (if ((mime == "image/jpeg")) ".jpg" else (if ((mime == "image/png")) ".png" else (if ((mime == "image/webp")) ".webp" else (if ((mime == "image/gif")) ".gif" else (if ((mime == "image/heic")) ".heic" else "")))))
if (photoId == null || photoId.trim().isEmpty() || photoId.length > 180 || !(PhotoAttachmentStore.canonicalName(name)) || !(hash.matches(Regex("[0-9a-f]{64}"))) || !((name == hash + extension)) || metadata.optLong("byte_size") < 1 || metadata.optLong("byte_size") > PhotoAttachmentStore.MAX_BYTES || !(HashSet(java.util.Arrays.asList("profile", "documentation", "before", "during", "after", "damage", "other")).contains(role))) {
throw IllegalArgumentException("Ungültige lokale Foto-Metadaten.")
}
var values = ContentValues()
values.put("photo_id", photoId)
values.put("content_sha256", hash)
values.put("mime_type", mime)
values.put("byte_size", metadata.getLong("byte_size"))
values.put("storage_name", name)
values.put("role", role)
values.put("transfer_state", state)
values.put("created_at", isoNow())
database.insertWithOnConflict("photo_attachments", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}
@Throws(Exception::class) fun restoreAttachmentMetadata(photoId: String, metadata: JSONObject): Unit {

saveAttachmentMetadata(getWritableDatabase(), photoId, metadata, "local")
}
@Throws(Exception::class) fun photoAttachment(photoId: String): JSONObject? {

getReadableDatabase().rawQuery("SELECT photo_id,content_sha256,mime_type,byte_size,storage_name,role,transfer_state,created_at FROM photo_attachments WHERE photo_id=?", arrayOf(photoId)).use { row ->

if (!(row.moveToFirst())) {
return null
}
return JSONObject().put("photo_id", row.getString(0)).put("content_sha256", row.getString(1)).put("mime_type", row.getString(2)).put("byte_size", row.getLong(3)).put("storage_name", row.getString(4)).put("role", row.getString(5)).put("transfer_state", row.getString(6)).put("created_at", row.getString(7))

}
}
fun photoAttachmentStore(): PhotoAttachmentStore {

return photoAttachments
}
private fun persistedConfirmedActionIds(): MutableSet<String> {

var confirmed = HashSet<String>()
try {

var snapshot = JSONObject(snapshotText("{}"))
confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids")))
confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")))
confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")))
var resolved = snapshot.optJSONObject("drive_resolved_actions")
if (resolved != null) {
var keys = resolved.keys()
while (keys.hasNext()) {
confirmed.add(keys.next())
}
}

} catch (ignored: Exception) {

}
return confirmed
}
@Throws(Exception::class) fun pendingPhotoAttachments(actions: JSONArray): JSONArray {

var result = JSONArray()
var confirmed = persistedConfirmedActionIds()
run {
var i: Int = 0
var firstIteration50 = true
while (true) {
if (firstIteration50) firstIteration50 = false else { i++ }
if (!(i < actions.length())) break

var action = actions.getJSONObject(i)
var payload = action.optJSONObject("payload")
if (!(("photo" == action.optString("kind"))) || payload == null || !(payload.optString("data").isEmpty())) {
continue
}
var metadata = photoAttachment(payload.optString("id"))
if (metadata == null) {
if (confirmed.contains(action.optString("client_id"))) {
continue
}
result.put(JSONObject().put("photo_id", payload.optString("id")).put("missing", true))
continue
}
for (key in arrayOf("thumbnail_storage_name", "thumbnail_sha256")) {
if (payload.has(key)) {
metadata.put(key, payload.get(key))
}
}
result.put(metadata)

}
}
return result
}
fun markPhotoAttachmentTransferred(photoId: String): Unit {

var values = ContentValues()
values.put("transfer_state", "provider_readback")
getWritableDatabase().update("photo_attachments", values, "photo_id=?", arrayOf(photoId))
}
@Throws(Exception::class) fun recordSnapshotPhotoAttachments(snapshot: JSONObject): Unit {

var photos = snapshot.optJSONArray("photos")
if (photos == null) {
return
}
var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

run {
var i: Int = 0
var firstIteration51 = true
while (true) {
if (firstIteration51) firstIteration51 = false else { i++ }
if (!(i < photos.length())) break

var photo = photos.optJSONObject(i)
if (photo == null || photo.optString("id").isEmpty()) {
continue
}
var local = photoAttachment(photo.optString("id"))
var inline = photo.optString("data")
if (inline.isEmpty()) {
inline = photo.optString("full_data")
}
if (!(inline.isEmpty())) {
var prepared = photoAttachments.prepareLegacy(JSONObject(photo.toString()).put("data", inline))
if (local != null && !((local.getString("content_sha256") == prepared.metadata.getString("content_sha256")))) {
if (prepared.created) {
photoAttachments.discardCreated(prepared.metadata)
}
throw IllegalArgumentException("Die Foto-ID verweist auf verschiedene Originale. Abgleich angehalten.")
}
if (local == null) {
saveAttachmentMetadata(database, photo.getString("id"), prepared.metadata, "legacy_inline_snapshot")
local = JSONObject(prepared.metadata.toString()).put("photo_id", photo.getString("id"))
}
}
photo.remove("data")
photo.remove("full_data")
if (local != null && (!(photo.optString("content_sha256").matches(Regex("[0-9a-f]{64}"))) || !(PhotoAttachmentStore.canonicalName(photo.optString("storage_name"))))) {
for (key in arrayOf("content_sha256", "mime_type", "byte_size", "storage_name", "role")) {
photo.put(key, local.get(key))
}
}
if (!(photo.optString("content_sha256").matches(Regex("[0-9a-f]{64}"))) || !(PhotoAttachmentStore.canonicalName(photo.optString("storage_name")))) {
continue
}
var keepExisting: Boolean = false
database.rawQuery("SELECT content_sha256,transfer_state FROM photo_attachments WHERE photo_id=?", arrayOf(photo.getString("id"))).use { existing ->

if (existing.moveToFirst()) {
var sameHash: Boolean = (existing.getString(0) == photo.getString("content_sha256"))
var authoritativeLocal: Boolean = !(("remote_available" == existing.getString(1)))
keepExisting = sameHash || authoritativeLocal
}

}
if (keepExisting) {
continue
}
var metadata = JSONObject().put("content_sha256", photo.getString("content_sha256")).put("mime_type", photo.getString("mime_type")).put("byte_size", photo.getLong("byte_size")).put("storage_name", photo.getString("storage_name")).put("role", photo.optString("role", "documentation"))
saveAttachmentMetadata(database, photo.getString("id"), metadata, "remote_available")

}
}
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
}
@Throws(Exception::class) fun cleanupConfirmedPhotoDeletes(submitted: JSONArray, confirmed: MutableSet<String>, snapshot: JSONObject): Unit {

var active = HashSet<String>()
var protectedPending = HashSet<String>()
var candidates = HashSet<String>()
var photos = snapshot.optJSONArray("photos")
run {
var i: Int = 0
var firstIteration52 = true
while (true) {
if (firstIteration52) firstIteration52 = false else { i++ }
if (!(photos != null && i < photos.length())) break

var photo = photos.optJSONObject(i)
if (photo != null && !(photo.optString("id").isEmpty())) {
active.add(photo.optString("id"))
}

}
}
run {
var i: Int = 0
var firstIteration53 = true
while (true) {
if (firstIteration53) firstIteration53 = false else { i++ }
if (!(i < submitted.length())) break

var action = submitted.optJSONObject(i)
var payload = (if (action == null) null else action.optJSONObject("payload"))
if (payload == null) {
continue
}
var client = action.optString("client_id")
var kind = action.optString("kind")
var id = payload.optString("id")
if (("photo" == kind) && !(confirmed.contains(client)) && !(id.isEmpty())) {
protectedPending.add(id)
}
if (("delete" == kind) && ("photo" == payload.optString("entity")) && confirmed.contains(client) && !(id.isEmpty()) && (snapshot.optJSONObject("drive_resolved_actions") == null || !(snapshot.getJSONObject("drive_resolved_actions").has(client)))) {
candidates.add(id)
}

}
}
candidates.removeAll(active)
candidates.removeAll(protectedPending)
if (candidates.isEmpty()) {
return
}
var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

for (photoId in candidates) {
var metadata = photoAttachment(photoId)
if (metadata == null) {
continue
}
var hash = metadata.getString("content_sha256")
var shared: Boolean
database.rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? AND photo_id<>? LIMIT 1", arrayOf(hash, photoId)).use { references ->

shared = references.moveToFirst()

}
if (!(shared)) {
photoAttachments.discardCreated(metadata)
}
database.delete("photo_attachments", "photo_id=?", arrayOf(photoId))
}
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
}
@Throws(Exception::class) fun loadPhotoData(photoId: String): String {

var metadata = photoAttachment(photoId)
if (metadata == null) {
throw IllegalArgumentException("Für dieses Foto ist noch kein dauerhaftes Original hinterlegt.")
}
try {

return photoAttachments.dataUri(metadata)

} catch (missing: Exception) {
throw IOException("LOCAL_ORIGINAL_MISSING", missing)
}
}
private @Throws(Exception::class) fun prepareManualChange(kind: String, payload: JSONObject, previous: JSONObject?): Unit {

ManualChangeRules.prepare(kind, payload, previous, isoNow())
var queued = pendingActions()
var dependencies = JSONArray()
var last = ""
run {
var i: Int = 0
var firstIteration54 = true
while (true) {
if (firstIteration54) firstIteration54 = false else { i++ }
if (!(i < queued.length())) break

var a = queued.getJSONObject(i)
var p = a.getJSONObject("payload")
var type = a.optString("kind")
if ((kind == type) && (payload.optString("id") == p.optString("id"))) {
last = a.getString("client_id")
}
if (p.optBoolean("_mcp_create")) {
for (ref in arrayOf(arrayOf("customer_id", "customer"), arrayOf("billing_customer_id", "customer"), arrayOf("order_id", "order"), arrayOf("worklog_id", "worklog"))) {
if ((ref[1] == type) && !(payload.optString(ref[0]).isEmpty()) && (payload.optString(ref[0]) == p.optString("id"))) {
dependencies.put(a.getString("client_id"))
}
}
}

}
}
if (!(last.isEmpty())) {
dependencies.put(last)
}
if (dependencies.length() > 0) {
payload.put("_sync_predecessors", dependencies)
}
}
@Throws(Exception::class) fun removeEntity(entity: String, entityId: String): String {

var db = getWritableDatabase()
beginBusinessTransaction(db)
try {

ActiveWorkProtection.require(getState(), entity, entityId)
var all = PixelMcpReadTools.effective(getState())
var current = OwnerJournalRules.findOrNull(all.getJSONArray(OwnerJournalRules.group(entity)), entityId)
if (current == null) {
throw IllegalArgumentException("Der Eintrag fehlt.")
}
var payload = DeletionRules.payload(all, entity, entityId, OwnerJournalRules.version(current), isoNow())
OwnerJournalRules.apply(JSONObject(all.toString()), JSONObject().put("kind", "archive").put("payload", payload).put("client_id", "preview-delete").put("created_at", isoNow()), java.util.HashMap(), JSONObject())
val revier = RevierApplicationService(RevierLocalRepository(this, null))
revier.protectParent(entity, entityId)
revier.detachOptionalParent(entity, entityId, "archive_local_" + java.util.UUID.randomUUID())
var saved = saveAction("archive", "archive_mobile_", payload)
db.setTransactionSuccessful()
return saved

} finally {
db.endTransaction()
}
}
@Throws(Exception::class) fun undoRemoveEntity(entity: String, entityId: String): String {

var all = PixelMcpReadTools.effective(getState())
var entry = OwnerJournalRules.archives(all).optJSONObject(entity + ":" + entityId)
if (entry == null) {
throw IllegalArgumentException("Der Eintrag ist nicht im Papierkorb. Aktuellen Stand prüfen.")
}
return PixelOwnerAccess(context, this).execute("restore_entity", JSONObject().put("request_id", "ui-restore-" + UUID.randomUUID()).put("entity", entity).put("entity_id", entityId).put("expected_record_hash", OwnerJournalRules.version(entry))).toString()
}
fun remainingDeleteUndoMs(): Long {

var remaining: Long = 0
try {
getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE kind IN ('delete','archive')", null).use { cursor ->

while (cursor.moveToNext()) {
var payload = JSONObject(cursor.getString(0))
remaining = Math.max(remaining, payload.optLong("_undo_until", 0) - System.currentTimeMillis())
}

}
} catch (ignored: Exception) {

}
return Math.max(0, remaining)
}
@Throws(Exception::class) fun testLocalOnly(kind: String, payload: JSONObject): Boolean {

var event = JSONObject().put("kind", kind).put("payload", payload)
var master = JSONObject(snapshotText("{}"))
if (TestDataRules.localOnly(event, master)) {
return true
}
var entity = (if (java.util.Arrays.asList("archive", "restore", "purge_v1").contains(kind)) payload.optString("entity") else (if (kind.startsWith("offer_")) "offer" else (if (kind.startsWith("invoice_")) "invoice" else kind)))
var pending = pendingActions()
run {
var i: Int = 0
var firstIteration57 = true
while (true) {
if (firstIteration57) firstIteration57 = false else { i++ }
if (!(i < pending.length())) break

var a = pending.getJSONObject(i)
var p = a.getJSONObject("payload")
if (!(p.optBoolean("_local_only")) && !(p.optBoolean("test_record"))) {
continue
}
if ((a.optString("kind") == "customer") && (p.optString("id") == payload.optString("customer_id")) || (a.optString("kind") == entity) && !(p.optString("id").isEmpty()) && (p.optString("id") == payload.optString("id"))) {
return true
}

}
}
return false
}
@Throws(Exception::class) fun saveAction(kind: String, prefix: String, payload: JSONObject): String {

var db = getWritableDatabase()
beginBusinessTransaction(db)
try {

if (testLocalOnly(kind, payload)) {
payload.put("_local_only", true)
}
var id = payload.optString("id")
if (id.isEmpty()) {
id = prefix + UUID.randomUUID().toString().replace("-", "")
}
payload.put("id", id)
var values = ContentValues()
values.put("id", kind + "_change_" + UUID.randomUUID())
values.put("kind", kind)
values.put("payload", payload.toString())
values.put("created_at", isoNow())
db.insertOrThrow("pending", null, values)
db.setTransactionSuccessful()
return id

} finally {
db.endTransaction()
}
}
@Throws(Exception::class) fun pendingActions(): JSONArray =
    LocalPendingActions.read(getReadableDatabase(), false, this::photoAttachment, System.currentTimeMillis())
fun acknowledge(ids: MutableSet<String>): Unit {

var database = getWritableDatabase()
beginBusinessTransaction(database)
try {

for (id in ids) {
database.delete("pending", "id=?", arrayOf(id))
}
database.setTransactionSuccessful()

} finally {
database.endTransaction()
}
}
companion object {
@JvmField val SCHEMA_VERSION: Int = 7
private val OPEN_STORES: java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> = java.util.concurrent.ConcurrentHashMap()
private val OPEN_DATABASES = java.util.WeakHashMap<SQLiteDatabase, String>()
@JvmStatic @Throws(Exception::class) fun assertNoOpenStores(context: Context): Unit {

val path = context.getDatabasePath("haus-garten-mobile.db").canonicalPath
var count = OPEN_STORES.get(path)
val databases = synchronized(OPEN_DATABASES) { OPEN_DATABASES.entries.map { it.key to it.value } }
if ((count != null && count.get() != 0) || databases.any { it.second == path && it.first.isOpen }) {
throw IOException("RESTORE_DATABASE_STILL_OPEN")
}
}
@JvmStatic fun businessTables(database: SQLiteDatabase): BusinessTableStore {

return businessTables(database, false)
}
@JvmStatic fun businessTables(database: SQLiteDatabase, committing: Boolean): BusinessTableStore {

return BusinessTableStore(object : BusinessTableStore.Sql {
override fun query(statement: String, vararg args: String): BusinessTableStore.Rows {

var rows = database.rawQuery(statement, args)
return object : BusinessTableStore.Rows {
override fun next(): Boolean {

return rows.moveToNext()
}
override fun text(column: Int): String {

return rows.getString(column)
}
override fun number(column: Int): Long {

return rows.getLong(column)
}
override fun close(): Unit {

rows.close()
}
}
}
override fun execute(statement: String, vararg args: Any?): Unit {

database.execSQL(statement, args)
}
override @Throws(Exception::class) fun <T> transaction(action: BusinessTableStore.Action<T>): T {

if (database.isReadOnly() || committing) {
return action.run()
}
database.beginTransaction()
try {

var result = action.run()
database.setTransactionSuccessful()
return result

} finally {
database.endTransaction()
}
}
})
}
@JvmStatic fun beginJournalTransaction(db: SQLiteDatabase, commit: BusinessTableStore.Action<Void?>): Unit {

if (db.inTransaction()) {
db.beginTransaction()
return
}
db.beginTransactionWithListener(object : android.database.sqlite.SQLiteTransactionListener {
override fun onBegin(): Unit {
db.acquireReference()
}
override fun onRollback(): Unit {
db.releaseReference()
}
override fun onCommit(): Unit {

try {

commit.run()
NativeDataRepository.notifyCommitted(db.path)

} catch (failure: Exception) {
throw IllegalStateException("LOCAL_JOURNAL_TABLE_COMMIT_FAILED", failure)
} finally {
db.releaseReference()
}
}
})
}
@JvmStatic fun removeSnapshotPhotoBinaries(snapshot: JSONObject): Unit {

var photos = snapshot.optJSONArray("photos")
run {
var i: Int = 0
var firstIteration58 = true
while (true) {
if (firstIteration58) firstIteration58 = false else { i++ }
if (!(photos != null && i < photos.length())) break

var photo = photos.optJSONObject(i)
if (photo != null) {
photo.remove("data")
photo.remove("full_data")
}

}
}
}
private @JvmStatic @Throws(Exception::class) fun retainSessionContext(payload: JSONObject, session: JSONObject): Unit {

for (field in arrayOf("property_id", "visit_id")) {
var value = session.optString(field)
if (payload.has(field) && !((payload.optString(field) == value))) {
throw IllegalArgumentException("Der Objekt-/Besuchsbezug der gestarteten Sitzung bleibt erhalten.")
}
if (!(value.isEmpty())) {
payload.put(field, value)
}
}
}
@JvmStatic @Throws(Exception::class) fun photoMetadataHash(original: JSONObject): String {

var metadata = JSONObject(original.toString())
metadata.remove("data")
metadata.remove("thumbnail_data")
metadata.remove("_capture_context")
return PixelMcpWriteTools.hash(metadata)
}
}
}
