package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EisbaerBillingProjectionTest {
    @Test
    fun lcrExclusionAffectsInvoiceSelectionWithoutHidingOriginalWorkOrItsHours() {
        val customer = JSONObject().put("id", "qa_lcr_customer").put("name", "[TEST] Kunde")
        fun work(id: String) =
            JSONObject()
                .put("id", id)
                .put("customer_id", customer.getString("id"))
                .put("activity", "[TEST] Arbeit")
                .put("work_date", "2026-11-11")
                .put("hours", 1)
                .put("billed", 0)
                .put("billable", true)
        val works = JSONArray().put(work("qa_lcr_original")).put(work("qa_invoice_original"))
        val master =
            JSONObject()
                .put("customers", JSONArray().put(customer))
                .put("orders", JSONArray())
                .put("worklogs", works)
                .put("photos", JSONArray())
        val state =
            JSONObject()
                .put("master", master)
                .put("settlement_ledger", JSONObject())
                .put("lcr_work_ids", JSONArray().put("qa_lcr_original"))
        val original = master.toString()
        fun summary() =
            BusinessQueries.query(
                "get_work_record_summary",
                JSONObject().put("group_by_billing_customer", true),
                state,
                master,
                null,
            ) as JSONObject
        val groups = summary().getJSONArray("billing_groups")
        assertEquals(1, groups.length())
        assertEquals(
            "qa_invoice_original",
            groups.getJSONObject(0).getJSONArray("work_records").getJSONObject(0).getString("id"),
        )
        val list =
            BusinessQueries.query("list_work_records", JSONObject(), state, master, null)
                as JSONObject
        assertEquals(2, list.getJSONArray("work_records").length())
        val overview =
            BusinessQueries.query("get_business_summary", JSONObject(), state, master, null)
                as JSONObject
        assertEquals(2, overview.getInt("work_records"))
        assertEquals(
            1,
            overview
                .getJSONArray("billing_groups")
                .getJSONObject(0)
                .getJSONArray("work_records")
                .length(),
        )

        assertEquals(original, master.toString())
        state.put("lcr_work_ids", JSONArray())
        assertEquals(
            2,
            summary()
                .getJSONArray("billing_groups")
                .getJSONObject(0)
                .getJSONArray("work_records")
                .length(),
        )
        assertEquals(original, master.toString())
    }
}
