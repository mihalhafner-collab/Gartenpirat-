package at.hausgarten.service

import org.json.*
import java.util.*
import at.hausgarten.service.SnapshotReadSupport.find
import at.hausgarten.service.SnapshotReadSupport.select

class BusinessQueries {
fun interface Query {
@Throws(Exception::class) fun run(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any
}
companion object {
private val QUERIES: MutableMap<String, Query> = LinkedHashMap()
init {
QUERIES.put("search_customers", BusinessQueries::customersOrders)
QUERIES.put("get_customer", BusinessQueries::customersOrders)
QUERIES.put("search_orders", BusinessQueries::customersOrders)
QUERIES.put("get_order", BusinessQueries::customersOrders)
QUERIES.put("get_open_orders", BusinessQueries::customersOrders)
QUERIES.put("list_work_records", BusinessQueries::work)
QUERIES.put("get_customer_work_records", BusinessQueries::work)
QUERIES.put("get_work_record_summary", BusinessQueries::work)
QUERIES.put("get_active_work_session", BusinessQueries::work)
QUERIES.put("get_work_record", BusinessQueries::work)
QUERIES.put("list_invoices", BusinessQueries::documents)
QUERIES.put("get_invoice", BusinessQueries::documents)
QUERIES.put("list_photos", BusinessQueries::documents)
QUERIES.put("get_sync_overview", BusinessQueries::sync)
QUERIES.put("get_sync_status", BusinessQueries::sync)
QUERIES.put("get_business_summary", BusinessQueries::overview)
QUERIES.put("search_business_data", BusinessQueries::overview)
}
@JvmStatic @Throws(Exception::class) fun query(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any {

var query = QUERIES.get(name)
if (query == null) {
throw IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.")
}
if (state.optBoolean("storage_list_selected")) {
if ((name == "search_business_data")) {
return JSONObject().put("customers", master.getJSONArray("customers")).put("orders", master.getJSONArray("orders")).put("work_records", master.getJSONArray("worklogs"))
}
var collection = (if ((name == "search_customers")) "customers" else (if ((name == "search_orders") || (name == "get_open_orders")) "orders" else (if ((name == "list_work_records") || (name == "get_customer_work_records")) "worklogs" else (if ((name == "list_invoices")) "invoices" else (if ((name == "list_photos")) "photos" else "")))))
if (collection.isEmpty()) {
throw IllegalArgumentException("TABLE_LIST_QUERY_UNSUPPORTED")
}
var rows = master.getJSONArray(collection)
var result = JSONObject().put((if ((collection == "worklogs")) "work_records" else collection), rows)
if ((name == "search_customers")) {
result.put("count", rows.length()).put("requires_clarification", rows.length() > 1)
}
return result
}
return query.run(name, a, state, master, writer)
}
private @JvmStatic @Throws(Exception::class) fun customersOrders(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any {

var customers = master.optJSONArray("customers")
var orders = master.optJSONArray("orders")
var logs = master.optJSONArray("worklogs")
var invoices = master.optJSONArray("invoices")
var photos = master.optJSONArray("photos")
var data: Any
when (name) {
"search_customers" -> {
run {
var matches = select(customers, a, "customer", master)
data = JSONObject().put("customers", matches).put("count", matches.length()).put("requires_clarification", matches.length() > 1)
}
}
"get_customer" -> {
data = find(customers, a.getString("customer_id"))
}
"search_orders" -> {
data = JSONObject().put("orders", select(orders, a, "order", master))
}
"get_order" -> {
data = find(orders, a.getString("order_id"))
}
"get_open_orders" -> {
data = JSONObject().put("orders", select(orders, JSONObject(a.toString()).put("open_only", true).put("limit", a.optInt("limit", 50)), "order", master))
}
else -> {
throw IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.")
}
}
return data
}
private @JvmStatic @Throws(Exception::class) fun work(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any {

var customers = master.optJSONArray("customers")
var orders = master.optJSONArray("orders")
var logs = master.optJSONArray("worklogs")
var invoices = master.optJSONArray("invoices")
var photos = master.optJSONArray("photos")
var data: Any
when (name) {
"list_work_records" -> {
data = JSONObject().put("work_records", select(logs, a, "worklog", master))
}
"get_customer_work_records" -> {
data = JSONObject().put("work_records", select(logs, a, "worklog", master))
}
"get_work_record_summary" -> {
run {
var matching = JSONArray()
run {
var i: Int = 0
var firstIteration8 = true
while (true) {
if (firstIteration8) firstIteration8 = false else { i++ }
if (!(logs != null && i < logs.length())) break

var row = logs.getJSONObject(i)
if (!(a.optString("customer_id").isEmpty()) && !((a.getString("customer_id") == row.optString("customer_id")))) {
continue
}
if (!(a.optString("billing_customer_id").isEmpty()) && !((a.getString("billing_customer_id") == BillingRules.recipient(row)))) {
continue
}
if (!(a.optString("date_from").isEmpty()) && row.optString("work_date").compareTo(a.getString("date_from")) < 0) {
continue
}
if (!(a.optString("date_to").isEmpty()) && row.optString("work_date").compareTo(a.getString("date_to")) > 0) {
continue
}
matching.put(row)

}
}
data = PixelMcpBusinessTools.report(matching, master)
if (a.optBoolean("group_by_billing_customer")) {
((data as JSONObject)).put("billing_groups", BillingRules.groups(matching, master))
}
}
}
"get_active_work_session" -> {
data = (if (state.has("work_session")) state.getJSONObject("work_session") else JSONObject().put("state", "UNAVAILABLE"))
}
"get_work_record" -> {
data = find(logs, a.getString("work_record_id"))
}
else -> {
throw IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.")
}
}
return data
}
private @JvmStatic @Throws(Exception::class) fun documents(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any {

var customers = master.optJSONArray("customers")
var orders = master.optJSONArray("orders")
var logs = master.optJSONArray("worklogs")
var invoices = master.optJSONArray("invoices")
var photos = master.optJSONArray("photos")
var data: Any
when (name) {
"list_invoices" -> {
data = JSONObject().put("invoices", select(invoices, a, "invoice", master))
}
"get_invoice" -> {
data = find(invoices, a.getString("invoice_id"))
}
"list_photos" -> {
data = JSONObject().put("photos", select(photos, a, "photo", master))
}
else -> {
throw IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.")
}
}
return data
}
private @JvmStatic @Throws(Exception::class) fun sync(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any {

var customers = master.optJSONArray("customers")
var orders = master.optJSONArray("orders")
var logs = master.optJSONArray("worklogs")
var invoices = master.optJSONArray("invoices")
var photos = master.optJSONArray("photos")
var data: Any
when (name) {
"get_sync_overview" -> {
run {
var pending = state.optJSONArray("pending")
data = JSONObject().put("integrity", PixelDriveState.integrity(state.getJSONObject("master"))).put("pending_actions", (if (pending == null) 0 else pending.length())).put("last_drive_sync", state.optString("last_drive_sync")).put("sync_errors", (if (master.optJSONArray("sync_errors") == null) JSONArray() else master.getJSONArray("sync_errors"))).put("timezone", "Europe/Vienna").put("archived_conflict_reviews", (if (master.optJSONObject("owner_conflict_history") == null) 0 else master.getJSONObject("owner_conflict_history").length())).put("journal_errors", (if (master.optJSONArray("drive_journal_errors") == null) JSONArray() else master.getJSONArray("drive_journal_errors"))).put("confirmation_scope", state.optString("confirmation_scope", "drive_provider_readback")).put("cloud_confirmed", state.optBoolean("cloud_confirmed", false) && ("drive_server_manifest" == state.optString("confirmation_scope"))).put("last_full_sync", state.optString("last_full_sync")).put("notice", "Lokale Speicherung, Provider-Rücklesen und vollständiger Abgleich sind getrennte Zustände. Nur geprüfte Drive-Serverbelege bestätigen die Übernahme.")
}
}
"get_sync_status" -> {
run {
if (writer != null) {
return PixelMcpBusinessTools.sync(writer.status(a.getString("client_id"), state), state)
}
var id = a.getString("client_id")
var status = "unknown"
var accepted = master.optJSONArray("accepted_ids")
if (accepted != null) {
run {
var i: Int = 0
var firstIteration9 = true
while (true) {
if (firstIteration9) firstIteration9 = false else { i++ }
if (!(i < accepted.length())) break

if ((id == accepted.optString(i))) {
status = "accepted"
}

}
}
}
var pending = state.optJSONArray("pending")
if (!(("accepted" == status)) && pending != null) {
run {
var i: Int = 0
var firstIteration10 = true
while (true) {
if (firstIteration10) firstIteration10 = false else { i++ }
if (!(i < pending.length())) break

if ((id == pending.getJSONObject(i).optString("client_id"))) {
status = "queued"
}

}
}
}
var applied: Boolean = DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(id)
if (applied && !((status == "accepted"))) {
status = "transferred"
}
data = JSONObject().put("client_id", id).put("status", status).put("confirmed_by_app", ("accepted" == status)).put("applied_on_pixel", applied).put("cloud_confirmed", false)
}
}
else -> {
throw IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.")
}
}
return data
}
private @JvmStatic @Throws(Exception::class) fun overview(name: String, a: JSONObject, state: JSONObject, master: JSONObject, writer: PixelMcpWriteTools.Sink?): Any {

var customers = master.optJSONArray("customers")
var orders = master.optJSONArray("orders")
var logs = master.optJSONArray("worklogs")
var invoices = master.optJSONArray("invoices")
var photos = master.optJSONArray("photos")
var data: Any
when (name) {
"get_business_summary" -> {
run {
var hours: Double = 0.0
var unbilled: Int = 0
run {
var i: Int = 0
var firstIteration11 = true
while (true) {
if (firstIteration11) firstIteration11 = false else { i++ }
if (!(logs != null && i < logs.length())) break

var row = logs.getJSONObject(i)
hours += row.optDouble("hours", 0.0)
if (row.optInt("billed", 0) == 0) {
unbilled++
}

}
}
data = JSONObject().put("customers", (if (customers == null) 0 else customers.length())).put("orders", (if (orders == null) 0 else orders.length())).put("work_records", (if (logs == null) 0 else logs.length())).put("hours", Math.round(hours * 100) / 100.0).put("marked_unbilled", unbilled).put("billing_groups", BillingRules.groups(logs, master))
}
}
"search_business_data" -> {
run {
var filters = JSONObject().put("query", a.getString("query")).put("include_inactive", true).put("limit", a.optInt("limit_per_type", 10))
data = JSONObject().put("customers", select(customers, filters, "customer", master)).put("orders", select(orders, filters, "order", master)).put("work_records", select(logs, filters, "worklog", master))
}
}
else -> {
throw IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.")
}
}
return data
}
}
}
