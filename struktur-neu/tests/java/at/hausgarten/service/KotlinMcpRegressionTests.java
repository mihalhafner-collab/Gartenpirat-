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

    private static JSONObject trashFixture() throws Exception {
        JSONObject customer = new JSONObject().put("id", "qa_trash_customer").put("name", "[TEST] QA fictional cleanup").put("test_record", true).put("active", 0);
        JSONObject snapshot = master(customer);
        JSONObject archived = new JSONObject().put("record", new JSONObject(customer.toString()).put("active", 1))
                .put("archived_record_hash", OwnerJournalRules.version(customer));
        snapshot.put("owner_archive", new JSONObject().put("customer:qa_trash_customer", archived));
        snapshot.getJSONArray("worklogs").put(new JSONObject().put("id", "qa_trash_work").put("customer_id", customer.getString("id")).put("activity", "Synthetic work"));
        snapshot.getJSONArray("photos").put(new JSONObject().put("id", "qa_trash_photo").put("customer_id", customer.getString("id")).put("worklog_id", "qa_trash_work").put("storage_name", "qa-synthetic-only.png"));
        return snapshot;
    }

    private static JSONObject purgeAction(JSONObject snapshot) throws Exception {
        JSONArray targets = TrashRules.testTargets(snapshot, "customer", "qa_trash_customer");
        return new JSONObject().put("kind", "purge_v1").put("client_id", "qa_cascade_request").put("created_at", "2026-11-03T20:00:00Z")
                .put("payload", new JSONObject().put("entity", "customer").put("id", "qa_trash_customer").put("confirmed", true)
                    .put("expected_record_hash", OwnerJournalRules.version(snapshot.getJSONObject("owner_archive").getJSONObject("customer:qa_trash_customer")))
                    .put("expected_test_dependencies_hash", PixelMcpWriteTools.hash(targets)).put("test_dependencies", targets));
    }

    private static void testTrashCleanup() throws Exception {
        JSONObject snapshot = trashFixture();
        JSONObject action = purgeAction(snapshot);
        yes(action.getJSONObject("payload").getJSONArray("test_dependencies").length() == 2, "Active work and its photo belong to the test cleanup");
        TrashRules.purge(snapshot, action);
        for (String group : new String[]{"customers", "worklogs", "photos"}) yes(snapshot.getJSONArray(group).length() == 0, "No orphan test row in " + group);
        yes(snapshot.getJSONObject("owner_archive").length() == 0, "Test parent is physically removed from trash");

        JSONObject changed = trashFixture();
        JSONObject old = purgeAction(changed);
        changed.getJSONArray("worklogs").getJSONObject(0).put("activity", "Changed synthetic work");
        String before = PixelMcpWriteTools.hash(changed);
        rejected("WRITE_CONFLICT", () -> TrashRules.purge(changed, old));
        yes(PixelMcpWriteTools.hash(changed).equals(before), "A stale dependent version changes nothing");

        JSONObject blocked = trashFixture();
        blocked.getJSONArray("customers").put(new JSONObject().put("id", "qa_other_customer").put("name", "QA unrelated customer"));
        blocked.getJSONArray("offers").put(new JSONObject().put("id", "qa_other_offer").put("customer_id", "qa_other_customer").put("worklog_ids", new JSONArray().put("qa_trash_work")));
        String intact = PixelMcpWriteTools.hash(blocked);
        try { TrashRules.purge(blocked, purgeAction(blocked)); throw new AssertionError("Shared unrelated record must protect its reference"); }
        catch (IllegalArgumentException expected) { checks++; }
        yes(PixelMcpWriteTools.hash(blocked).equals(intact), "Unrelated dependencies reject the whole cleanup without partial removal");

        JSONObject archived = trashFixture();
        JSONObject photo = archived.getJSONArray("photos").getJSONObject(0);
        archived.put("photos", new JSONArray());
        archived.getJSONObject("owner_archive").put("photo:qa_trash_photo", new JSONObject().put("record", photo));
        TrashRules.purge(archived, purgeAction(archived));
        yes(archived.getJSONObject("owner_archive").length() == 0, "Archived descendants are removed with the test parent");

        JSONObject detached = trashFixture();
        JSONObject detachedPhoto = detached.getJSONArray("photos").getJSONObject(0);
        detachedPhoto.put("customer_id", "").put("worklog_id", "");
        detached.getJSONObject("owner_archive").getJSONObject("customer:qa_trash_customer").put("photo_links", new JSONArray().put(new JSONObject().put("id", "qa_trash_photo").put("detached_hash", OwnerJournalRules.version(detachedPhoto))));
        TrashRules.purge(detached, purgeAction(detached));
        yes(detached.getJSONArray("photos").length() == 0, "Unchanged detached photo ownership remains covered");

        JSONObject mixed = trashFixture();
        mixed.getJSONObject("owner_archive").put("photo:qa_unrelated_archived_photo", new JSONObject().put("record", new JSONObject().put("id", "qa_unrelated_archived_photo").put("caption", "QA ordinary trash photo")));
        JSONObject preview = TrashPurgeBatch.preview(mixed);
        yes(preview.getInt("count") == 2 && preview.getInt("linked_test_count") == 2, "Mixed trash includes test descendants without a Drive restriction");
        JSONObject payload = TrashPurgeBatch.payload(mixed, preview.getString("preview_hash"), true);
        yes(TestDataRules.localOnly(new JSONObject().put("kind", TrashPurgeBatch.KIND).put("payload", payload), mixed), "The complete mixed deletion stays local");
        JSONObject batch = new JSONObject().put("kind", TrashPurgeBatch.KIND).put("client_id", "qa_mixed_request").put("created_at", "2026-11-03T20:00:00Z").put("payload", payload);
        JSONObject stale = new JSONObject(mixed.toString());
        stale.getJSONArray("photos").getJSONObject(0).put("caption", "Changed");
        rejected("WRITE_CONFLICT", () -> TrashPurgeBatch.apply(stale, batch));
        TrashPurgeBatch.apply(mixed, batch);
        yes(mixed.getJSONObject("owner_archive").length() == 0 && mixed.getJSONArray("worklogs").length() == 0 && mixed.getJSONArray("photos").length() == 0, "One mixed batch leaves no test descendants");

        JSONObject replay = trashFixture();
        JSONArray events = new JSONArray().put(purgeAction(replay));
        yes(LocalJournalRules.apply(replay, events).length() == 0, "Cascade journal commits once");
        String committed = PixelMcpWriteTools.hash(replay);
        yes(LocalJournalRules.apply(replay, events).length() == 0 && PixelMcpWriteTools.hash(replay).equals(committed), "Exact cascade replay never recreates a descendant");

        JSONObject real = trashFixture();
        real.getJSONArray("customers").getJSONObject(0).put("name", "QA ordinary customer").remove("test_record");
        real.getJSONObject("owner_archive").getJSONObject("customer:qa_trash_customer").getJSONObject("record").put("name", "QA ordinary customer").remove("test_record");
        yes(TrashRules.testTargets(real, "customer", "qa_trash_customer").length() == 0, "Ordinary customer deletion never cascades");
    }

    private static void deletionReadback() throws Exception {
        for (String operation : new String[]{"purge_entity", "archive_work_records"}) for (boolean enqueueWorks : new boolean[]{false, true}) {
            JSONObject state = new JSONObject().put("master", trashFixture()).put("pending", new JSONArray()).put("work_session", new JSONObject().put("state", "IDLE"));
            OwnerRepository repository = (OwnerRepository) java.lang.reflect.Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(), new Class<?>[]{OwnerRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "state": return state;
                    case "effective": return SnapshotProjection.effective(state);
                    case "enqueue":
                        if (enqueueWorks) state.getJSONArray("pending").put(new JSONObject().put("kind", args[0]).put("payload", args[1]).put("client_id", args[2]).put("created_at", "2026-11-03T20:00:00Z"));
                        return new JSONObject().put("local_state", "local_saved");
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
            JSONObject payload = purgeAction(state.getJSONObject("master")).getJSONObject("payload");
            JSONObject args = new JSONObject().put("entity", "customer").put("entity_id", "qa_trash_customer").put("expected_record_hash", payload.getString("expected_record_hash")).put("confirmed", true);
            if (operation.equals("archive_work_records")) {
                JSONArray ids = new JSONArray().put("qa_trash_work");
                args = new JSONObject().put("work_record_ids", ids).put("expected_preview_hash", WorklogArchiveBatch.preview(state.getJSONObject("master"), ids).getString("preview_hash")).put("confirmed", true);
            }
            final JSONObject supplied = args;
            ArchiveApplicationService service = new ArchiveApplicationService(repository);
            if (!enqueueWorks) rejected("DELETE_NOT_APPLIED", () -> service.perform(operation, supplied, "qa_readback_failed"));
            else {
                service.perform(operation, supplied, "qa_readback_ok");
                JSONObject actual = repository.effective();
                if (operation.equals("purge_entity")) yes(actual.getJSONArray("customers").length() == 0 && actual.getJSONArray("worklogs").length() == 0 && actual.getJSONArray("photos").length() == 0, "Deletion success is based on the applied business state");
                else yes(actual.getJSONArray("worklogs").length() == 0 && actual.getJSONObject("owner_archive").has("worklog:qa_trash_work") && actual.getJSONArray("photos").getJSONObject(0).optString("worklog_id").isEmpty(), "Batch archive reads back removal and detached photo relations");
            }
        }
    }

    private static void removalAfterFailedEdit() throws Exception {
        for (String entity : new String[]{"customer", "worklog"}) {
            JSONObject baseline = trashFixture();
            String id = entity.equals("customer") ? "qa_trash_customer" : "qa_trash_work";
            JSONObject row = OwnerJournalRules.findOrNull(baseline.getJSONArray(OwnerJournalRules.group(entity)), id);
            JSONObject badBase = LocalJournalRules.guard(new JSONObject(row.toString()).put("updated_at", "qa_stale_version"), entity);
            JSONObject edit = new JSONObject().put("client_id", "qa_failed_edit").put("kind", entity).put("created_at", "2026-11-03T20:00:00Z")
                .put("payload", new JSONObject(row.toString()).put("_sync_base", badBase));
            JSONArray prior = new JSONArray().put(edit);
            JSONObject current = new JSONObject(baseline.toString());
            yes(LocalJournalRules.apply(current, prior).length() == 1, "Synthetic stale edit remains unapplied");
            JSONObject deletion = entity.equals("customer") ? purgeAction(current) : new JSONObject().put("kind", "archive").put("client_id", "qa_remove_work")
                .put("created_at", "2026-11-03T20:01:00Z").put("payload", DeletionRules.payload(current, "worklog", id, OwnerJournalRules.version(row), "2026-11-03T20:01:00Z"));
            JSONObject old = new JSONObject(deletion.toString());
            old.getJSONObject("payload").put("_sync_predecessors", new JSONArray().put("qa_failed_edit"));
            JSONObject blocked = new JSONObject(baseline.toString());
            JSONArray errors = LocalJournalRules.apply(blocked, new JSONArray().put(edit).put(old));
            yes(errors.length() == 2 && errors.getJSONObject(1).getString("code").equals("DEPENDENCY_BLOCKED"), "An automatic dependency on a failed edit used to block removal");
            JSONObject payload = deletion.getJSONObject("payload");
            payload.put("_sync_predecessors", LocalActionOrder.predecessors(deletion.getString("kind"), payload, prior, current));
            JSONObject actual = new JSONObject(baseline.toString());
            yes(LocalJournalRules.apply(actual, new JSONArray().put(edit).put(deletion)).length() == 1, "Only the old stale edit remains failed");
            yes(OwnerJournalRules.findOrNull(actual.getJSONArray(OwnerJournalRules.group(entity)), id) == null, "Current-version removal is applied despite the old failed edit");
            yes(prior.length() == 1 && PixelMcpWriteTools.hash(edit).equals(PixelMcpWriteTools.hash(prior.getJSONObject(0))), "The failed edit is not silently rewritten or deleted");
            JSONObject stale = new JSONObject(baseline.toString());
            OwnerJournalRules.findOrNull(stale.getJSONArray(OwnerJournalRules.group(entity)), id).put("updated_at", "qa_new_version");
            yes(LocalJournalRules.apply(stale, new JSONArray().put(edit).put(deletion)).length() == 2, "A changed current version still prevents deletion");
            JSONObject explicit = new JSONObject(payload.toString()).put("_sync_predecessors", new JSONArray().put("qa_failed_edit"));
            yes(LocalActionOrder.predecessors(deletion.getString("kind"), explicit, prior, current).getString(0).equals("qa_failed_edit"), "Explicit predecessor requirements remain intact");
            yes(LocalActionOrder.predecessors(entity, new JSONObject().put("id", id), prior, null).getString(0).equals("qa_failed_edit"), "Ordinary edits keep their prior-action dependency");
        }
    }

    private static OwnerRepository deletionPort(JSONObject state, String failId) {
        return (OwnerRepository) java.lang.reflect.Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(), new Class<?>[]{OwnerRepository.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "state": return state;
                case "effective": return SnapshotProjection.effective(state);
                case "enqueue":
                    JSONObject payload = (JSONObject) args[1];
                    if ("purge_v1".equals(args[0]) && failId.equals(payload.optString("id"))) throw new java.io.IOException("QA simulated second removal failure");
                    state.getJSONArray("pending").put(new JSONObject().put("kind", args[0]).put("payload", payload).put("client_id", args[2]).put("created_at", "2026-11-03T20:00:00Z"));
                    return new JSONObject().put("client_id", args[2]).put("local_state", "local_saved");
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static void permanentDeletion() throws Exception {
        for (String entity : new String[]{"worklog", "photo"}) {
            String id = entity.equals("worklog") ? "qa_trash_work" : "qa_trash_photo";
            JSONObject state = new JSONObject().put("master", trashFixture()).put("pending", new JSONArray()).put("work_session", new JSONObject().put("state", "IDLE"));
            OwnerRepository repository = deletionPort(state, "");
            JSONObject row = OwnerJournalRules.findOrNull(repository.effective().getJSONArray(OwnerJournalRules.group(entity)), id);
            JSONObject result = new ArchiveApplicationService(repository).perform("delete_entity", new JSONObject().put("entity", entity).put("entity_id", id).put("expected_record_hash", OwnerJournalRules.version(row)), "qa_permanent_" + entity);
            JSONObject actual = repository.effective();
            yes(!result.getBoolean("recoverable"), "Delete means permanent removal for " + entity);
            yes(OwnerJournalRules.findOrNull(actual.getJSONArray(OwnerJournalRules.group(entity)), id) == null && !OwnerJournalRules.archives(actual).has(entity + ":" + id), "No active or trash copy remains for " + entity);
            if (entity.equals("photo")) yes(actual.getJSONArray("worklogs").length() == 1, "Deleting a photo never deletes its parent work");
            String committed = PixelMcpWriteTools.hash(actual);
            yes(LocalJournalRules.apply(actual, state.getJSONArray("pending")).length() == 0 && committed.equals(PixelMcpWriteTools.hash(actual)), "Repeating the committed removal does not restore its content");
        }
        for (boolean failSecond : new boolean[]{false, true}) {
            JSONObject master = trashFixture();
            master.getJSONArray("worklogs").put(new JSONObject(master.getJSONArray("worklogs").getJSONObject(0).toString()).put("id", "qa_second_work"));
            JSONObject state = new JSONObject().put("master", master).put("pending", new JSONArray()).put("work_session", new JSONObject().put("state", "IDLE"));
            OwnerRepository repository = deletionPort(state, failSecond ? "qa_second_work" : "");
            JSONArray ids = new JSONArray().put("qa_trash_work").put("qa_second_work");
            JSONObject args = new JSONObject().put("work_record_ids", ids).put("expected_preview_hash", WorklogArchiveBatch.preview(repository.effective(), ids).getString("preview_hash")).put("confirmed", true);
            java.util.Map<String,String> receipts = new java.util.HashMap<>();
            OwnerRequestTransactions.Store transaction = new OwnerRequestTransactions.Store() {
                JSONObject before; boolean committed;
                public void begin() { before = new JSONObject(state.toString()); committed = false; }
                public String read(String key) { return receipts.getOrDefault(key, ""); }
                public void write(String key, String value) { receipts.put(key, value); }
                public void successful() { committed = true; }
                public void end() {
                    if (!committed) {
                        for (String key : new java.util.ArrayList<>(state.keySet())) state.remove(key);
                        for (String key : before.keySet()) state.put(key, before.get(key));
                    }
                }
            };
            String original = PixelMcpWriteTools.hash(state);
            try {
                OwnerRequestTransactions.runRequest(transaction, "qa_delete_selection", "qa_fingerprint", () -> new ArchiveApplicationService(repository).perform("delete_work_records", args, "qa_delete_selection"), "2026-11-03T20:00:00Z");
                if (failSecond) throw new AssertionError("Expected second purge failure");
                JSONObject actual = repository.effective();
                yes(actual.getJSONArray("worklogs").length() == 0 && !OwnerJournalRules.archives(actual).has("worklog:qa_trash_work") && !OwnerJournalRules.archives(actual).has("worklog:qa_second_work"), "Batch deletion leaves no work copies in trash");
                yes(receipts.containsKey("receipt:qa_delete_selection"), "Success receipt exists only for the completed selection");
            } catch (java.io.IOException expected) {
                if (!failSecond) throw expected;
                yes(original.equals(PixelMcpWriteTools.hash(state)) && receipts.isEmpty(), "A failed second purge rolls back both removals and their receipt");
            }
        }
    }

    private static void deletionPhaseReadback() throws Exception {
        for (String ignored : new String[]{"", "archive", "purge_v1", "dependent"}) {
            JSONObject state = new JSONObject().put("master", trashFixture()).put("pending", new JSONArray())
                .put("work_session", new JSONObject().put("state", "IDLE"));
            JSONObject retainedPhoto = new JSONObject(state.getJSONObject("master").getJSONArray("photos").getJSONObject(0).toString());
            OwnerRepository repository = (OwnerRepository) java.lang.reflect.Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(), new Class<?>[]{OwnerRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "state": return state;
                    case "effective":
                        JSONObject projected = SnapshotProjection.effective(state);
                        if (ignored.equals("dependent") && projected.getJSONArray("worklogs").length() == 0 && projected.getJSONArray("photos").length() == 0)
                            projected.getJSONArray("photos").put(new JSONObject(retainedPhoto.toString()));
                        return projected;
                    case "enqueue":
                        if (!ignored.equals(args[0])) state.getJSONArray("pending").put(new JSONObject().put("kind", args[0]).put("payload", args[1])
                            .put("client_id", args[2]).put("created_at", "2026-11-03T20:00:00Z"));
                        return new JSONObject().put("local_state", "local_saved");
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
            JSONObject work = repository.effective().getJSONArray("worklogs").getJSONObject(0);
            JSONObject args = new JSONObject().put("entity", "worklog").put("entity_id", work.getString("id"))
                .put("expected_record_hash", OwnerJournalRules.version(work));
            ArchiveApplicationService service = new ArchiveApplicationService(repository);
            if (!ignored.isEmpty()) rejected("DELETE_NOT_APPLIED", () -> service.perform("delete_entity", args, "qa_delete_phase_" + ignored));
            else {
                JSONObject result = service.perform("delete_entity", args, "qa_delete_phase_success");
                JSONObject actual = repository.effective();
                yes(actual.getJSONArray("worklogs").length() == 0 && actual.getJSONArray("photos").length() == 0,
                    "Permanent deletion verifies both its source and its test-dependent photo");
                yes(!result.has("master") && !result.has("owner_archive") && !result.has("worklogs"),
                    "Private verified source snapshots are absent from the public deletion receipt");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        optionalJsonBoundaries();
        guardedCustomerJournal();
        readAndWriteCatalog();
        testTrashCleanup();
        deletionReadback();
        removalAfterFailedEdit();
        permanentDeletion();
        deletionPhaseReadback();
        System.out.println("PASS " + checks + " Kotlin MCP nullable-JSON/journal/catalog regression checks; synthetic only");
    }
}
