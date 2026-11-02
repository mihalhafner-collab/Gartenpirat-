package at.hausgarten.service;

import org.json.JSONArray;
import org.json.JSONObject;

/** Current Kotlin MCP/journal boundaries, using wholly synthetic legacy-shaped JSON. */
public final class KotlinMcpRegressionTests {
    private static int checks;
    private interface Work { void run() throws Exception; }

    private static void yes(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void rejected(String code, Work work) throws Exception {
        try {
            work.run();
            throw new AssertionError("Expected " + code);
        } catch (PixelMcpWriteTools.Rejected failure) {
            yes(code.equals(failure.code), "Stable rejection: " + code);
        }
    }

    private static JSONObject master(JSONObject customer) throws Exception {
        JSONObject value = new JSONObject().put("schema_version", 2)
                .put("business_settings", new JSONObject())
                .put("customers", new JSONArray().put(customer));
        for (String key : new String[]{"orders", "worklogs", "invoices", "photos",
                "offers", "service_catalog", "appointments"}) value.put(key, new JSONArray());
        return value;
    }

    private static void optionalJsonBoundaries() throws Exception {
        for (Object value : new Object[]{null, JSONObject.NULL}) {
            yes("null".equals(PixelMcpWriteTools.canonical(value)), "Canonical JSON null");
            yes("null".equals(CatalogSemanticHash.canonical(value)), "Catalog JSON null");
            yes(PixelMcpWriteTools.hash(value).equals(
                    "74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b"),
                    "Legacy null hash remains stable");
        }
        yes(LocalJournalRules.guard(null, "customer").length() == 0, "Absent row has empty guard");
        yes(LocalJournalRules.ids(null).isEmpty(), "Absent optional ID list is empty");
        for (Object value : new Object[]{null, JSONObject.NULL, ""}) {
            yes(PixelMcpWriteTools.canonical(LocalJournalRules.comparable("custom_fields", value))
                    .equals("{}"), "Missing/null/empty custom fields compare as empty object");
            yes(LocalJournalRules.comparable("garden_area", value) == JSONObject.NULL,
                    "Missing garden area remains unknown");
        }
        JSONObject customer = new JSONObject().put("id", "qa_missing_fields")
                .put("name", "[TEST] QA Kunde").put("active", 1);
        LocalJournalRules.compare(LocalJournalRules.guard(customer, "customer"), customer);
        checks++;
        rejected("WRITE_CONFLICT", () -> LocalJournalRules.compare(
                LocalJournalRules.guard(customer, "customer"), null));
        rejected("WRITE_CONFLICT", () -> LocalJournalRules.compare(
                LocalJournalRules.guard(customer, "customer"),
                new JSONObject(customer.toString()).put("name", "[TEST] QA Fremdänderung")));

        JSONObject optional = McpFields.object(new JSONObject().put("note", McpFields.text(30)));
        McpSchema.validate(new JSONObject(), optional);
        checks++;
        JSONObject nullable = new JSONObject().put("type", new JSONArray().put("string").put("null"));
        McpSchema.validate(JSONObject.NULL, nullable);
        checks++;
        rejected("INVALID_INPUT", () -> McpSchema.validate(null, nullable));
        rejected("INVALID_INPUT", () -> McpSchema.validate(
                new JSONObject().put("unexpected", true), optional));
    }

    private static void guardedCustomerJournal() throws Exception {
        Object[] variants = {null, JSONObject.NULL, "", "{}", new JSONObject().put("QA", "Altbestand")};
        for (int i = 0; i < variants.length; i++) {
            JSONObject customer = new JSONObject().put("id", "qa_legacy_customer_" + i)
                    .put("name", "[TEST] QA Ursprünglich").put("active", 1)
                    .put("test_record", true).put("unknown_optional", new JSONObject().put("qa", true));
            if (variants[i] != null) customer.put("custom_fields", variants[i]);
            JSONObject state = master(customer);
            JSONObject payload = new JSONObject(customer.toString()).put("name", "[TEST] QA Journal")
                    .put("_sync_base", LocalJournalRules.guard(customer, "customer"));
            String id = "qa_customer_journal_" + i;
            JSONArray actions = new JSONArray().put(new JSONObject().put("client_id", id)
                    .put("kind", "customer").put("created_at", "2026-10-28T12:00:00Z")
                    .put("payload", payload));
            String originalAction = PixelMcpWriteTools.hash(actions);
            JSONArray errors = LocalJournalRules.apply(state, actions);
            yes(errors.length() == 0, "Optional custom_fields variant " + i + " keeps guarded update valid");
            JSONObject saved = state.getJSONArray("customers").getJSONObject(0);
            yes(saved.getString("name").equals("[TEST] QA Journal"), "Journal commits the customer edit");
            yes(saved.getJSONObject("unknown_optional").getBoolean("qa"), "Unknown legacy data retained");
            yes(LocalJournalRules.ids(state.getJSONArray("drive_applied_ids")).contains(id),
                    "Committed journal ID recorded");
            yes(originalAction.equals(PixelMcpWriteTools.hash(actions)), "Journal payload stays immutable");
            if (i == 0) yes(!saved.has("custom_fields"), "Absent optional field is not invented");
            saved.put("name", "[TEST] QA Später");
            yes(LocalJournalRules.apply(state, actions).length() == 0, "Exact replay remains accepted");
            yes(state.getJSONArray("customers").getJSONObject(0).getString("name")
                    .equals("[TEST] QA Später"), "Replay preserves a newer customer state");
        }
    }

    private static void readAndWriteCatalog() throws Exception {
        JSONObject customer = new JSONObject().put("id", "qa_catalog_customer")
                .put("name", "[TEST] QA Katalog").put("active", 1);
        JSONObject state = new JSONObject().put("master", master(customer)).put("pending", new JSONArray());
        PixelMcpReadTools read = new PixelMcpReadTools(() -> state);
        PixelMcpWriteTools.Sink sink = new PixelMcpWriteTools.Sink() {
            public JSONObject execute(String tool, JSONObject args) throws Exception { return new JSONObject().put("ok", true); }
            public JSONObject status(String id, JSONObject value) throws Exception { return new JSONObject().put("phase", "local_saved"); }
        };
        PixelMcpReadTools write = new PixelMcpReadTools(() -> state, sink);
        JSONArray readonly = read.tools(), writable = write.tools();
        yes(readonly.length() >= 50, "Retained reader capabilities are registered");
        yes(writable.length() > readonly.length(), "Writable owner capabilities are registered");
        for (JSONArray catalog : new JSONArray[]{readonly, writable}) {
            for (int index = 0; index < catalog.length(); index++) {
                String tool = catalog.getJSONObject(index).getString("name");
                yes(!java.util.Arrays.asList("list_invoices", "get_invoice", "create_invoice_draft",
                    "update_invoice_draft", "request_invoice_issue", "update_invoice_payment",
                    "request_sync", "resolve_sync_conflict", "get_sync_overview", "get_sync_conflicts",
                    "get_sync_status", "inspect_order_review", "save_reviewed_order",
                    "record_payment", "correct_payment", "allocate_payment", "reserve_invoice_number",
                    "issue_invoice", "cancel_invoice", "prepare_invoice_delivery", "prepare_invoice_share").contains(tool),
                    "Retired invoice and Drive tools are not advertised");
            }
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int i = 0; i < writable.length(); i++) {
            JSONObject tool = writable.getJSONObject(i);
            yes(names.add(tool.getString("name")), "MCP tool is registered exactly once");
            yes(tool.getJSONObject("inputSchema").getString("type").equals("object"), "MCP argument contract exists");
        }
        for (String tool : new String[]{"create_customer", "create_order", "create_work_record", "set_work_record_billed", "create_offer_draft", "get_offer", "save_material", "get_full_backup_status"})
            yes(names.contains(tool), "Retained business tool: " + tool);
        JSONObject material = LocalWorkflowTools.schema("save_material").getJSONObject("properties");
        yes(material.getJSONObject("purchase_price_minor").getLong("minimum") == 0, "Material purchase price uses nonnegative cents");
        yes(material.getJSONObject("billing_price_minor").getLong("maximum") == 9000000000000000L, "Material billing price retains exact integer bound");
        for (int i = 0; i < writable.length(); i++) {
            JSONObject tool = writable.getJSONObject(i);
            if (tool.getString("name").equals("update_business_settings")) {
                JSONObject fields = tool.getJSONObject("inputSchema").getJSONObject("properties").getJSONObject("updates").getJSONObject("properties");
                for (String removed : new String[]{"invoice_prefix", "invoice_number_format", "invoice_numbering_authority", "invoice_numbering_last_number", "invoice_sevdesk_last_number"})
                    yes(!fields.has(removed), "Retired invoice numbering is not writable: " + removed);
                yes(fields.has("invoice_issuer_confirmed") && fields.has("tax_mode"), "Offer issuer and tax settings remain writable");
            }
        }
        yes(read.call("search_customers", new JSONObject().put("query", "Katalog"))
                .getJSONObject("data").getInt("count") == 1, "Search works with absent optional customer fields");
        rejected("WRITE_DISABLED", () -> read.call("create_customer", new JSONObject()));
    }

    public static void main(String[] args) throws Exception {
        optionalJsonBoundaries();
        guardedCustomerJournal();
        readAndWriteCatalog();
        System.out.println("PASS " + checks + " Kotlin MCP nullable-JSON/journal/catalog regression checks; synthetic only");
    }
}
