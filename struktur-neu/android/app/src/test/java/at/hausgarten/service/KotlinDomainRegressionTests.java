package at.hausgarten.service;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Current Kotlin domain code, synthetic transaction ports and isolated temporary media only. */
public final class KotlinDomainRegressionTests {
    private static int checks;

    private static void yes(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private interface Work { void run() throws Exception; }

    private static void rejected(String code, Work work) throws Exception {
        try {
            work.run();
            throw new AssertionError("Expected " + code);
        } catch (PixelMcpWriteTools.Rejected failure) {
            yes(code.equals(failure.code), "Business rejection remains " + code);
        }
    }

    private static void conflict(Work work) throws Exception {
        rejected("WRITE_CONFLICT", work);
    }

    private static void nullablePortInputs() throws Exception {
        for (String kind : new String[]{"customer", "order", "worklog"}) {
            JSONObject fresh = new JSONObject().put("id", "qa_new_" + kind);
            yes(ManualChangeRules.editor(fresh, null, kind) == fresh,
                    "A new " + kind + " form has no previous record");
        }
        JSONObject base = new JSONObject().put("id", "qa_changed_customer").put("name", "QA Kunde");
        JSONObject edit = new JSONObject(base.toString()).put("_edit_base", base)
                .put("_edit_changes", new JSONObject().put("name", "QA eigener Name"));
        conflict(() -> ManualChangeRules.editor(edit, null, "customer"));
        yes(!TrashRules.referenced(new JSONObject(), null, "qa-missing-original.jpg"),
                "Missing optional collections contain no media reference");
        JSONObject order = new JSONObject().put("customer_id", "qa_customer_2");
        JSONObject previous = new JSONObject().put("id", "qa_order_1").put("customer_id", "qa_customer_1");
        EntityProtectionRules.validateOrderChange(order, previous, null, null, null);
        yes(order.getString("customer_id").equals("qa_customer_2")
                        && previous.getString("customer_id").equals("qa_customer_1"),
                "Absent optional relations preserve the accepted order change and original");
        JSONObject unassignedPhoto = new JSONObject();
        EntityProtectionRules.validatePhoto(unassignedPhoto, null, null, null);
        yes(unassignedPhoto.length() == 0, "Validating an unassigned photo invents no customer relation");
        try {
            EntityProtectionRules.validatePhoto(
                    new JSONObject().put("customer_id", "qa_customer_1"), null, null, null);
            throw new AssertionError("Expected missing photo customer");
        } catch (IllegalArgumentException failure) {
            yes("Der ausgewählte Kunde existiert nicht.".equals(failure.getMessage()),
                    "An unavailable photo customer is rejected with the business reason");
        }
        rejected("INVALID_INPUT", () -> RevierRules.decimal(new JSONObject(), "quantity_decimal", true, false));
        rejected("REVIER_SCHEMA_UPGRADE_REQUIRED", () -> RevierRules.version(new JSONObject().put("kind", "equipment")));
    }

    private static void guardedCustomerWithoutCustomFields() throws Exception {
        JSONObject master = SyntheticBusinessData.profile("minimal");
        JSONObject original = master.getJSONArray("customers").getJSONObject(0);
        original.remove("custom_fields");
        original.put("name", "[TEST] QA Kunde vor bestätigter Änderung").put("unknown_field", JSONObject.NULL);
        JSONObject payload = new JSONObject(original.toString()).put("name", "[TEST] QA Kunde nach bestätigter Änderung")
                .put("_sync_base", LocalJournalRules.guard(original, "customer"));
        String actionId = "qa_guarded_customer_without_custom_fields";
        JSONObject action = new JSONObject().put("client_id", actionId).put("kind", "customer")
                .put("created_at", "2027-05-18T10:00:00Z").put("payload", payload);
        JSONArray errors = LocalJournalRules.apply(master, new JSONArray().put(action));
        JSONObject saved = master.getJSONArray("customers").getJSONObject(0);
        yes(saved.getString("name").equals("[TEST] QA Kunde nach bestätigter Änderung"),
                "A guarded customer update without optional custom fields changes the name");
        JSONArray applied = master.getJSONArray("drive_applied_ids");
        boolean recorded = false;
        for (int i = 0; i < applied.length(); i++) recorded |= actionId.equals(applied.getString(i));
        yes(recorded, "The successful customer action is recorded as applied");
        yes(errors.length() == 0 && master.getJSONArray("drive_journal_errors").length() == 0,
                "Missing optional garden fields produce no journal error");
        yes(!saved.has("custom_fields") && saved.has("unknown_field") && saved.isNull("unknown_field"),
                "Applying the update preserves absent optional fields and explicit null values");
    }

    private static void legacyTimerCharacterization() throws Exception {
        Random random = new Random(20261029);
        String[] boots = {null, "", "unverified_boot", "boot:7", "boot:8"};
        JSONArray outcomes = new JSONArray();
        for (int i = 0; i < 2000; i++) {
            long wallStart = random.nextLong(), elapsedStart = random.nextLong();
            String bootStart = boots[i % boots.length], bootNow = boots[(i / 5) % boots.length];
            long wallNow = random.nextLong(), elapsedNow = random.nextLong();
            outcomes.put(new JSONArray()
                    .put(WorkSessionTimingRules.reviewReason(wallStart, elapsedStart, bootStart,
                            wallNow, elapsedNow, bootNow))
                    .put(WorkSessionTimingRules.verifiedDelta(wallStart, elapsedStart, bootStart,
                            wallNow, elapsedNow, bootNow)));
        }
        // Observed from the retained Java baseline before its Kotlin replacement.
        yes(PixelMcpWriteTools.hash(outcomes).equals(
                        "b5131abb9381629e631600cb33c9984b19cbbe4fd5c77d18f6153be164fb67e5"),
                "2,000 clock/boot edge cases retain the Java timer decisions");
    }

    public static void main(String[] ignored) throws Exception {
        int original = RevierDomainTests.assertions;
        RevierDomainTests.workflow();
        RevierDomainTests.inspectionContractLimit();
        RevierDomainTests.seriesAndTours();
        RevierDomainTests.resources();
        RevierDomainTests.timedMovementContext();
        RevierDomainTests.searchScale();
        RevierDomainTests.searchResponseContract();
        RevierDomainTests.searchSettlementsAndContact();
        RevierDomainTests.acceptanceAndInspectionCorrections();
        RevierDomainTests.archiveDeletion();
        RevierDomainTests.restoredActionInspection();
        RevierDomainTests.restoredOwnerActionInspection();
        RevierDomainTests.calendarDayFilters();
        int workflows = RevierDomainTests.assertions - original;
        int beforeReceipts = RevierDomainTests.assertions;
        RevierDomainTests.nativeAndOwnerReceiptInspection();
        int receipts = RevierDomainTests.assertions - beforeReceipts;
        nullablePortInputs();
        int nullable = checks;
        guardedCustomerWithoutCustomFields();
        int journal = checks - nullable;
        legacyTimerCharacterization();
        System.out.println("PASS " + (workflows + receipts + checks)
                + " Kotlin domain assertions: " + workflows + " existing Revier, " + receipts
                + " receipt inspection, " + nullable
                + " nullable inputs, " + journal + " guarded customer journal, " + (checks - nullable - journal)
                + " timer characterization (2,000 scenarios); synthetic JVM only");
    }
}
