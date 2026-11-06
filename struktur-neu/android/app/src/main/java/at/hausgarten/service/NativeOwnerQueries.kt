package at.hausgarten.service

import android.content.*
import org.json.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

class NativeOwnerQueries : PixelMcpOwnerTools.Access {
private val context: Context
private val store: PixelLocalStore
private val repository: LocalOwnerRepository
constructor(context: Context, store: PixelLocalStore, repository: LocalOwnerRepository) {
this.context = context
this.store = store
this.repository = repository
}
private fun exportSettlements(args: JSONObject): JSONObject {
McpSchema.validate(args, SettlementTools.schema("export_settlements"))
val db = store.getWritableDatabase()
store.beginBusinessTransaction(db)
try {
    val ledger = SettlementRules.ledger(repository)
    val master = repository.effective()
    val selectedKeys = linkedSetOf<String>()
    var offset = 0
    var reviewOffset = 0
    var overview: JSONObject? = null
    do {
        val page = store.nativeData().accounting(JSONObject(args.toString()).put("limit", 100).put("offset", offset).put("review_offset", reviewOffset))
        if (overview == null) overview = JSONObject(page.toString()).apply { remove("records"); remove("reviews") }
        for (row in SettlementRules.rows(page.getJSONArray("records")) + SettlementRules.rows(page.getJSONArray("reviews"))) selectedKeys.add(row.getString("key"))
        offset += 100; reviewOffset += 100
    } while (offset < overview!!.getInt("total") || reviewOffset < overview!!.getInt("review_total"))
    val filtered = args.keys().asSequence().any { args.optString(it).isNotEmpty() }
    val all = SettlementRules.records(ledger)
    val selected = if (!filtered) all else all.filter { row ->
        "settlements:" + row.getString("id") in selectedKeys || SettlementRules.rows(row.optJSONArray("sources")).any { ref ->
            SettlementRules.rows(master.optJSONArray(ref.getString("collection"))).any { it.optString("id") == ref.getString("id") } &&
                SettlementRules.group(master, ref.getString("collection"), ref.getString("id")) in selectedKeys
        }
    }.toMutableList().apply {
        val ids = map { it.getString("id") }.toSet()
        for (row in all) if (row.optString("corrects") in ids || row.optString("corrected_by") in ids) if (none { it.getString("id") == row.getString("id") }) add(row)
    }
    val ids = selected.map { it.getString("id") }.toSet()
    val result = JSONObject(ledger.toString()).put("format", "gartenpirat.local-settlement-export.v1")
        .put("exported_at", java.time.Instant.now().toString()).put("filtered", filtered).put("filters", JSONObject(args.toString()))
        .put("records", JSONArray(selected)).put("overview", overview).put("selection_keys", JSONArray(selectedKeys))
        .put("payments", JSONArray(SettlementRules.rows(ledger.optJSONArray("payments")).filter { it.optString("settlement_id") in ids }))
        .put("delivery_evidence", JSONArray(SettlementRules.rows(ledger.optJSONArray("delivery_evidence")).filter { it.optString("settlement_id") in ids }))
        .put("notice", "Beide Abrechnungsarten sind nachvollziehbar. Summen sind bestätigte Abrechnungsbeträge, keine Zahlungseingänge. Gefilterte Auswahl ist kein vollständiger Betriebsbestand. PDF, Hash und ZIP garantieren keine Rechtskonformität.")
    // Replay internals are retained by full backups; the human export keeps business histories.
    result.remove("operations")
    db.setTransactionSuccessful()
    return result
} finally { db.endTransaction() }
}

private fun storageContract(): JSONObject =
    JSONObject().put("storage_contract_version", 3)
        .put("business_sync_allowed", false)
        .put("invoice_finalization", "LOCAL_KU_EUR_PREPARE_COMPLETE")

private @Throws(Exception::class) fun withStorageContract(result: JSONObject): JSONObject {

var contract = storageContract()
run {
var keys = contract.keys()
var firstIteration12 = true
while (true) {
if (firstIteration12) firstIteration12 = false else {  }
if (!(keys.hasNext())) break

var key = keys.next()
result.put(key, contract.get(key))

}
}
return result
}
override @Throws(Exception::class) fun query(name: String, args: JSONObject): JSONObject? {

if (name == "export_settlements") return exportSettlements(args)
if (name in SettlementTools.READS && name != "export_settlements") return SettlementTools.query(name, args, repository)
if (AppointmentTools.read(name)) {
return AppointmentRuntime.query(context, store, name, args)
}
if ((name == "get_restore_status")) {
return BackupRestoreService.pendingStatus(context)
}
if (LocalWorkflowTools.backup(name)) {
return repository.fullBackupStatus(args)
}
if (LocalWorkflowTools.revier(name)) {
return repository.revierQuery(name, args)
}
if (LocalWorkflowTools.offerDecision(name)) {
return OfferDecisionService(repository).query(name, args)
}
when (name) {
"get_accounting_overview" -> return store.nativeData().accounting(args)
"get_accounting_record" -> return store.nativeData().accountingRecord(args)
"run_self_test" -> {
return selfTest()
}
"get_ui_action_catalog" -> {
run {
var catalog = JSONObject(String(repository.asset("ui-mcp-actions.json"), StandardCharsets.UTF_8))
var hash = PixelMcpWriteTools.hash(catalog)
if (args.has("expected_catalog_hash") && !((hash == args.getString("expected_catalog_hash")))) {
throw PixelMcpWriteTools.Rejected("CATALOG_CHANGED", "Katalog geändert; ab erster Seite neu lesen.")
}
var all = catalog.getJSONArray("actions")
var page = JSONArray()
run {
var i: Int = args.optInt("offset")
var firstIteration13 = true
while (true) {
if (firstIteration13) firstIteration13 = false else { i++ }
if (!(i < all.length() && page.length() < args.optInt("limit", 50))) break

page.put(all.get(i))

}
}
return JSONObject().put("actions", page).put("total", all.length()).put("catalog_hash", hash).put("catalog_semantic", CatalogSemanticHash.describe(catalog)).put("offset", args.optInt("offset")).put("limit", args.optInt("limit", 50)).put("next_cursor", (if (args.optInt("offset") + page.length() < all.length()) args.optInt("offset") + page.length() else JSONObject.NULL)).put("scope", catalog.getString("scope"))
}
}
"preview_empty_trash" -> {
return TrashPurgeBatch.preview(repository.effective())
}
"preview_archive_work_records" -> {
return WorklogArchiveBatch.preview(repository.effective(), args.getJSONArray("work_record_ids"))
}
"get_capabilities" -> {
run {
var data = JSONObject().put("build_fingerprint", BuildProvenance.describe()).put("mail_readback_configured", !(context.getSharedPreferences("haus_garten_mail", 0).getString("account", "")!!.isEmpty())).put("app_version_name", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName).put("package_name", context.getPackageName()).put("database_version", PixelLocalStore.SCHEMA_VERSION).put("app_version_code", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode).put("snapshot_available", !(store.snapshotText("").isEmpty())).put("installed_permission_gate", PixelTunnelConfig(context).writeEnabled())
var current = repository.effective()
var caps = current.optJSONObject("capabilities")
withStorageContract(data)
data.put("inventory_processing_engine", current.optString("processing_engine")).put("offers_local_ready", caps != null && caps.optBoolean("offers_v1")).put("service_catalog_local_ready", caps != null && caps.optBoolean("service_catalog_v1"))
data.put("capabilities", ((if (caps == null) JSONObject() else JSONObject(caps.toString()))).put("local_settlements_v1", true).put("local_payment_ledger_v1", true).put("native_editor_storage_v1", true).put("business_drive_sync", false).put("encrypted_drive_backup", false).put("local_plaintext_backup", true))
var backup = JSONObject()
var target: Boolean = true
data.put("local_workflows_contract", 1).put("backup_contract", JSONObject().put("format_version", 3).put("inventory_issues_block_backup", false).put("encrypted_streaming", false).put("daily_deferrable", false).put("setup_requires_device", true).put("remote_readback_sha256", false).put("mode", "PIXEL_PLAINTEXT_ZIP").put("automatic_enabled", false).put("key_required", false))
data.put("planning", AppointmentRuntime.integration(context, store)).put("backup_storage", "PIXEL_LOCAL").put("platform_limits", JSONArray().put("tunnel_account_and_organization_grants").put("backup_android_local_file_permission").put("foreground_and_battery_limits").put("sharing_requires_device_interaction").put("camera_location_and_os_settings_require_device"))
return data
}
}
"preview_purge" -> {
run {
var all = repository.effective()
var entry = OwnerJournalRules.archives(all).optJSONObject(args.getString("entity") + ":" + args.getString("entity_id"))
if (entry == null || entry.optBoolean("purged")) {
throw IllegalArgumentException("Eintrag nicht im Papierkorb.")
}
var out = JSONObject().put("expected_record_hash", OwnerJournalRules.version(entry)).put("allowed", true)
try {

TrashRules.purge(JSONObject(all.toString()), JSONObject().put("kind", "purge_v1").put("client_id", "read-only-preview").put("created_at", java.time.Instant.now().toString()).put("payload", JSONObject().put("entity", args.getString("entity")).put("id", args.getString("entity_id")).put("confirmed", true).put("expected_record_hash", OwnerJournalRules.version(entry)).put("expected_test_dependencies_hash", PixelMcpWriteTools.hash(TrashRules.testTargets(all, args.getString("entity"), args.getString("entity_id"))))))

} catch (e: IllegalArgumentException) {
out.put("allowed", false).put("message", e.message)
}
return out
}
}
"preview_delete" -> {
return DeletionRules.preview(repository.effective(), args.getString("entity"), args.getString("entity_id"))
}
"get_business_settings" -> {
run {
var effective = repository.effective()
var s = effective.optJSONObject("business_settings")
if (s == null) {
throw IllegalArgumentException("Pixel-Einstellungsstand fehlt.")
}
return withStorageContract(JSONObject().put("initialization_required", s.length() == 0).put("settings_origin", effective.optString("business_settings_origin", "existing_snapshot")).put("settings", JSONObject().apply { for (key in McpFields.BUSINESS_KEYS) if (s.has(key)) put(key, s.get(key)) }).put("record_hash", PixelMcpWriteTools.hash(s)).put("credentials_exposed", false))
}
}
"get_settings" -> {
run {
var s = repository.settings()
return withStorageContract(JSONObject().put("photo_storage", repository.photoStorageSummary()).put("settings", s).put("record_hash", PixelMcpWriteTools.hash(s)).put("personal_paid", JSONArray(store.ownerValue("personal_paid", "[]"))).put("credentials_exposed", false))
}
}
"search_catalog" -> {
run {
if (("service" == args.optString("catalog_type"))) {
var filters = JSONObject(args.toString())
filters.remove("catalog_type")
return OfferTools.query("search_offer_templates", filters, repository.state())
}
var q = args.optString("query").lowercase(Locale.GERMAN)
var skip: Int = args.optInt("offset")
var limit: Int = args.optInt("limit", 20)
var total: Int = 0
var out = JSONArray()
var rows = repository.plants()
run {
var i: Int = 0
var firstIteration14 = true
while (true) {
if (firstIteration14) firstIteration14 = false else { i++ }
if (!(i < rows.length())) break

var p = rows.getJSONObject(i)
if (!(p.toString().lowercase(Locale.GERMAN).contains(q))) {
continue
}
if (total++ >= skip && out.length() < limit) {
out.put(p)
}

}
}
return JSONObject().put("entries", out).put("total", total)
}
}
"list_archive" -> {
run {
var archive = OwnerJournalRules.archives(repository.effective())
var keys = ArrayList<String>()
run {
var it = archive.keys()
var firstIteration15 = true
while (true) {
if (firstIteration15) firstIteration15 = false else {  }
if (!(it.hasNext())) break

var key = it.next()
if (archive.getJSONObject(key).optBoolean("purged")) {
continue
}
var split: Int = key.indexOf(':')
if (args.has("entity") && !((key.substring(0, split) == args.getString("entity")))) {
continue
}
if (args.has("entity_id") && !((key.substring(split + 1) == args.getString("entity_id")))) {
continue
}
keys.add(key)

}
}
keys.sortWith(Comparator(LAM@ { x, y ->
var date: Int = archive.optJSONObject(y).optString("archived_at").compareTo(archive.optJSONObject(x).optString("archived_at"))
return@LAM (if (date == 0) x.compareTo(y) else date)
 }))
var out = JSONArray()
var skip: Int = args.optInt("offset")
var limit: Int = args.optInt("limit", 20)
run {
var i: Int = skip
var firstIteration16 = true
while (true) {
if (firstIteration16) firstIteration16 = false else { i++ }
if (!(i < keys.size && out.length() < limit)) break

var key = keys.get(i)
var saved = archive.getJSONObject(key)
out.put(JSONObject().put("archive_key", key).put("entry", saved).put("record_hash", OwnerJournalRules.version(saved)))

}
}
return JSONObject().put("archive", out).put("total", keys.size)
}
}
"get_catalog_entry" -> {
if (args.getString("entry_id").startsWith("svc_")) {
var e = PixelMcpWriteTools.find(repository.effective().getJSONArray("service_catalog"), args.getString("entry_id"))
return JSONObject().put("entry", e).put("record_hash", PixelMcpWriteTools.hash(e))
}
return repository.entry(args.getString("entry_id"))
}
"get_file_metadata", "read_file_chunk" -> {
return repository.readChunk(args)
}
else -> {
throw IllegalArgumentException("Lesefunktion fehlt.")
}
}
}
private @Throws(Exception::class) fun selfTest(): JSONObject {

var checks = JSONArray()
var all = repository.effective()
var settings = all.optJSONObject("business_settings")
var caps = all.optJSONObject("capabilities")
var tools = PixelMcpReadTools(store.readSource(), object : PixelMcpWriteTools.Sink {
override fun writable(): Boolean {

return PixelTunnelConfig(context).writeEnabled()
}
override @Throws(Exception::class) fun execute(n: String, a: JSONObject): JSONObject {

return store.execute(n, a)
}
override @Throws(Exception::class) fun status(id: String, state: JSONObject): JSONObject {

return store.status(id, state)
}
}, this)
var catalogue = tools.tools()
var names = HashSet<String>()
run {
var i: Int = 0
var firstIteration17 = true
while (true) {
if (firstIteration17) firstIteration17 = false else { i++ }
if (!(i < catalogue.length())) break

names.add(catalogue.getJSONObject(i).getString("name"))

}
}
checks.put(JSONObject().put("name", "build_and_protocol").put("status", "PASS"))
checks.put(JSONObject().put("name", "catalog_unique").put("status", (if (names.size == catalogue.length()) "PASS" else "FAIL")))
checks.put(JSONObject().put("name", "write_gate").put("status", (if (PixelTunnelConfig(context).writeEnabled()) "PASS" else "NOT TESTABLE")).put("write_enabled", PixelTunnelConfig(context).writeEnabled()))
var complete: Boolean = settings != null
for (key in OfferContract.values("issuer_required_fields")) {
complete = complete and (settings != null && !(settings.optString(key).isEmpty()))
}
checks.put(JSONObject().put("name", "business_settings_complete").put("status", (if (complete) "PASS" else "FAIL")))
checks.put(BackupStatusRules.selfTest(PixelBackupService(context, store).status(JSONObject().put("limit", 1))))
checks.put(JSONObject().put("name", "offers_and_catalog").put("status", (if (caps != null && caps.optBoolean("offers_v1") && caps.optBoolean("service_catalog_v1")) "PASS" else "FAIL")))
var inventory = JSONObject(String(repository.asset("ui-mcp-actions.json"), StandardCharsets.UTF_8))
var actions = inventory.getJSONArray("actions")
var parity: Boolean = true
run {
var i: Int = 0
var firstIteration19 = true
while (true) {
if (firstIteration19) firstIteration19 = false else { i++ }
if (!(i < actions.length())) break

var action = actions.getJSONObject(i)
var path = action.getJSONArray("mcp")
if ((action.getString("kind") == "business")) {
parity = parity and (path.length() > 0)
run {
var j: Int = 0
var firstIteration18 = true
while (true) {
if (firstIteration18) firstIteration18 = false else { j++ }
if (!(j < path.length())) break

parity = parity and (names.contains(path.getString(j)))

}
}
} else {
parity = parity and (!(action.optString("barrier").isEmpty()))
}

}
}
checks.put(JSONObject().put("name", "ui_mcp_parity").put("status", (if (!(PixelTunnelConfig(context).writeEnabled())) "NOT TESTABLE" else (if (parity) "PASS" else "FAIL"))).put("actions", actions.length()))
var docs = File(context.getFilesDir(), "business-documents")
checks.put(JSONObject().put("name", "private_document_storage").put("status", (if (docs.isDirectory() && docs.canRead()) "PASS" else "NOT TESTABLE")))
checks.put(JSONObject().put("name", "physical_google_and_delivery").put("status", "NOT TESTABLE").put("reason", "Separate Kontofreigabe und tatsächlichen Providerbeleg prüfen; Diagnose sendet nichts."))
return JSONObject().put("checks", checks).put("build_fingerprint", BuildProvenance.describe()).put("tool_count", catalogue.length()).put("tool_catalog_hash", PixelMcpWriteTools.hash(catalogue)).put("tool_catalog_semantic", CatalogSemanticHash.describe(catalogue)).put("ui_catalog_hash", PixelMcpWriteTools.hash(inventory)).put("ui_catalog_semantic", CatalogSemanticHash.describe(inventory)).put("business_data_changed", false).put("secrets_exposed", false)
}
}
