package at.hausgarten.service;

import org.json.*;
import java.nio.file.*;

/** Frozen synthetic observable results, independent of handler layout. */
public final class RefactoringCharacterizationTests {
    public static void main(String[] args) throws Exception {
        SyntheticBusinessData.verify();
        JSONObject state = PixelMcpWriteTests.state();
        String before = PixelMcpWriteTools.hash(state);
        PixelMcpReadTools tools = new PixelMcpReadTools(() -> state);
        JSONObject results = SyntheticBusinessData.object();
        String[][] cases = {
            {"search_customers", "{query:'QA'}"}, {"get_customer", "{customer_id:'qa_c1'}"},
            {"search_orders", "{}"}, {"get_order", "{order_id:'qa_o1'}"},
            {"list_work_records", "{}"}, {"get_customer_work_records", "{customer_id:'qa_c1'}"},
            {"get_work_record_summary", "{}"}, {"get_work_record", "{work_record_id:'qa_w1'}"},
            {"get_open_orders", "{}"}, {"list_invoices", "{}"}, {"list_photos", "{}"},
            {"get_business_summary", "{}"}, {"search_business_data", "{query:'QA'}"},
            {"get_sync_status", "{client_id:'qa_missing'}"}, {"get_sync_overview", "{}"},
            {"get_active_work_session", "{}"}, {"get_customer", "{customer_id:'qa_missing'}"},
            {"list_work_records", "{date_from:'2026-10-19',date_to:'2026-10-01'}"},
            {"create_customer", "{}"}, {"request_invoice_issue", "{}"},
            {"update_photo", "{}"}, {"get_settings", "{}"}, {"unknown_tool", "{}"}
        };
        int i = 0;
        for (String[] test : cases) {
            JSONObject request = SyntheticBusinessData.object().put("jsonrpc", "2.0").put("id", i)
                .put("method", "tools/call").put("params", SyntheticBusinessData.object()
                .put("name", test[0]).put("arguments", SyntheticBusinessData.object(test[1])));
            results.put("qa_case_" + i++, PixelMcpWriteTools.hash(
                tools.rpc(request).getJSONObject("result").getJSONObject("structuredContent")));
        }
        if (!before.equals(PixelMcpWriteTools.hash(state))) throw new AssertionError("Query mutated fixture");
        results.put("state_hash", before);
        Files.writeString(Path.of("outputs/integration-java/refactoring-observations.json"), results.toString(2));
        System.out.println("PASS " + cases.length + " frozen read/error/write-gate observations; input unchanged");
    }
}
