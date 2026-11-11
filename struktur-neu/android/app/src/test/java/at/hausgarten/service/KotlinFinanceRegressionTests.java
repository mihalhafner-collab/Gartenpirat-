package at.hausgarten.service;

import java.math.BigDecimal;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Current production Kotlin rules; all parties, dates, prices and identifiers below are synthetic. */
public final class KotlinFinanceRegressionTests {
    private static final String STAMP = "2026-10-29T10:00:00Z";
    private static int checks;
    private interface Work { void run() throws Exception; }

    private static JSONObject json(String value) throws Exception { return new JSONObject(value); }
    private static JSONObject copy(JSONObject value) throws Exception { return new JSONObject(value.toString()); }
    private static void yes(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private static void money(Object value, String expected, String reason) {
        yes(new BigDecimal(value.toString()).compareTo(new BigDecimal(expected)) == 0, reason);
    }
    private static void rejected(String code, Work work) throws Exception {
        try { work.run(); throw new AssertionError("Expected rejection " + code); }
        catch (PixelMcpWriteTools.Rejected failure) { yes(code.equals(failure.code), "Business rejection " + code); }
    }
    private static void invalid(String message, Work work) throws Exception {
        try { work.run(); throw new AssertionError("Expected invalid input: " + message); }
        catch (IllegalArgumentException failure) { yes(message.equals(failure.getMessage()), "Business reason: " + message); }
    }
    private static JSONObject master() throws Exception {
        return json("{customers:[{id:'c1',name:'Synthetic Alpha',street:'Testweg 1',postal_code:'1000',city:'Teststadt',country:'AT',billing_mode:'direct'},"
                + "{id:'c2',name:'Synthetic Beta',street:'Testweg 2',postal_code:'2000',city:'Teststadt',country:'AT',billing_mode:'via_customer',default_billing_customer_id:'c1'}],"
                + "orders:[{id:'o1',customer_id:'c2',billing_customer_id:'c1',title:'Synthetic order',status:'Geplant'}],"
                + "worklogs:[{id:'w1',customer_id:'c2',billing_customer_id:'c1',order_id:'o1',work_date:'2026-10-28',hours:5.25,billed:0},"
                + "{id:'w2',customer_id:'c1',work_date:'2026-10-27',hours:1.5,billed:0}],invoices:[],photos:[],offers:[],service_catalog:[],"
                + "business_settings:{owner_name:'Synthetic Issuer',business_street:'Testweg 3',business_postal_code:'3000',business_city:'Teststadt',"
                + "invoice_issuer_confirmed:'true',tax_mode:'standard',business_vat_id:'ATU12345678'}}");
    }
    private static JSONObject offer() throws Exception {
        return OfferRules.normalize(json("{id:'off1',customer_id:'c1',title:'Synthetic offer',document_type:'fixed_price',issue_date:'2026-10-29',"
                + "valid_until:'2026-11-15',execution_window:'nach Vereinbarung',payment_terms:'14 Tage',tax_mode:'standard',customer_type:'b2b',scope_confirmed:true,"
                + "created_at:'2026-10-29T10:00:00Z',positions:[{title:'Synthetic work',description:'Test',quantity:2.1,price:31.55,tax_rate:20,unit:'h',price_source:'user_confirmed'}]}"), null, master());
    }
    private static void billingRelations() throws Exception {
        yes("c1".equals(BillingRules.defaultRecipient("c1", "", master())), "Direct recipient");
        yes("c1".equals(BillingRules.defaultRecipient("c2", "", master())), "Site customer retains separate payer");
        yes("c1".equals(BillingRules.defaultRecipient("c2", "o1", master())), "Explicit order payer");
        rejected("REFERENCE_MISMATCH", () -> BillingRules.defaultRecipient("c1", "o1", master()));
        JSONObject missing = BillingRules.assignment(json("{customer_id:'c1',billing_customer_id:'missing'}"), master(), true);
        yes(!missing.getBoolean("valid") && "missing".equals(missing.getString("billing_customer_id")), "Missing payer is reported, never reassigned");
        JSONArray groups = BillingRules.groups(master().getJSONArray("worklogs"), master());
        yes(groups.length() == 1 && groups.getJSONObject(0).getJSONArray("work_records").length() == 2,
                "Site and direct work join the same confirmed payer without losing work records");
        money(groups.getJSONObject(0).get("hours"), "6.75", "Exact grouped hours");
        JSONArray marked = new JSONArray(master().getJSONArray("worklogs").toString());
        marked.getJSONObject(0).put("billed", 1);
        JSONObject report = PixelMcpBusinessTools.report(marked, master());
        yes(report.getInt("marked_open") == 1 && report.getInt("marked_billed") == 1, "Manual billing marks remain independent of sevDesk");
        yes(!report.has("proven_invoiced") && !report.has("billing_unclear_or_draft") && !report.has("partially_invoiced"), "No retired invoice authority is projected into the hours overview");

    }

    private static void offersAndDecisions() throws Exception {
        JSONObject value = offer();
        money(value.get("gross_total"), "79.51", "Offer with no invoice discount preserves its own total");
        yes(OfferRules.validate(value, master()).getBoolean("valid"), "Explicitly sourced offer price validates");
        JSONObject open = copy(value); open.getJSONArray("positions").getJSONObject(0).put("price", JSONObject.NULL);
        OfferRules.normalize(open, null, master());
        yes(open.isNull("gross_total") && !OfferRules.validate(open, master()).getBoolean("valid"), "Unknown offer price remains unknown and blocks finalization");
        invalid("Unbekannte Angebotsart.", () -> OfferRules.normalize(copy(value).put("document_type", "invented"), null, master()));
        JSONObject decision = new JSONObject().put("formatVersion", 1).put("offer_id", "off1").put("content_version", OfferDecisionRules.contentVersion(value))
                .put("decision", "accepted").put("accepted_at", STAMP).put("evidence", "Synthetic approval");
        JSONObject decisions = new JSONObject().put("off1", decision);
        yes("accepted".equals(OfferDecisionRules.matching(value, decisions).getString("decision")), "Acceptance belongs to the exact reviewed version");
        yes("review_required".equals(OfferDecisionRules.matching(copy(value).put("title", "Synthetic changed offer"), decisions).getString("decision")),
                "Changing immutable version content does not transfer its acceptance");
    }

    /** Production decision, conversion and transaction rules; only storage/document I/O are ports. */
    private static final class DecisionStore implements InvocationHandler, OwnerRequestTransactions.Store {
        JSONObject data, beforeData;
        final Map<String, String> owner = new HashMap<>();
        Map<String, String> beforeOwner;
        boolean committed;
        int documentReads;
        final OwnerRepository repository;
        DecisionStore() throws Exception {
            data = master();
            JSONObject finalized = offer().put("status", "finalized").put("offer_number", "QA-OFFER-000001")
                    .put("finalized_at", STAMP).put("pdf_sha256", "a".repeat(64))
                    .put("pdf_byte_size", 1234).put("document_id", "synthetic-existing-offer-pdf");
            data.getJSONArray("offers").put(finalized);
            repository = (OwnerRepository) Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(),
                    new Class<?>[]{OwnerRepository.class}, this);
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
                case "effective": return copy(data);
                case "state": return new JSONObject().put("master", copy(data)).put("pending", new JSONArray());
                case "ownerValue": return owner.getOrDefault((String) args[0], (String) args[1]);
                case "putOwnerValue": owner.put((String) args[0], (String) args[1]); return null;
                case "readDocument":
                    yes(((JSONObject) args[0]).getString("pdf_sha256").equals("a".repeat(64)),
                            "Acceptance reads the exact finalized document through its I/O port");
                    documentReads++; return null;
                case "enqueue":
                    JSONObject event = new JSONObject().put("kind", args[0]).put("payload", args[1])
                            .put("client_id", args[2]).put("created_at", STAMP);
                    OfferJournalRules.apply(data, event);
                    return new JSONObject().put("local_state", "local_saved");
                default: throw new UnsupportedOperationException("Unimplemented synthetic port: " + method.getName());
            }
        }
        JSONObject decide(JSONObject args) throws Exception {
            return OwnerRequestTransactions.runRequest(this, args.getString("request_id"), PixelMcpWriteTools.hash(args),
                    () -> new OfferDecisionService(repository, Clock.fixed(Instant.parse(STAMP), ZoneOffset.UTC))
                            .perform("record_offer_acceptance", args, args.getString("request_id")), STAMP);
        }
        @Override public void begin() {
            try { beforeData = copy(data); } catch (Exception failure) { throw new AssertionError(failure); }
            beforeOwner = new HashMap<>(owner); committed = false;
        }
        @Override public String read(String key) { return owner.getOrDefault(key, ""); }
        @Override public void write(String key, String value) { owner.put(key, value); }
        @Override public void successful() { committed = true; }
        @Override public void end() { if (!committed) { data = beforeData; owner.clear(); owner.putAll(beforeOwner); } }
    }

    private static void acceptedOfferCreatesOrderAndReplays() throws Exception {
        DecisionStore store = new DecisionStore();
        JSONObject original = copy(store.data.getJSONArray("offers").getJSONObject(0));
        String originalHash = PixelMcpWriteTools.hash(LocalJournalRules.clean(original));
        JSONObject unconfirmed = new JSONObject().put("kind", "offer_convert").put("client_id", "qa_unconfirmed_conversion")
                .put("created_at", STAMP).put("payload", new JSONObject().put("id", "off1").put("expected_record_hash", originalHash));
        invalid("Ausdrückliche Bestätigung erforderlich.", () -> OfferJournalRules.apply(copy(store.data), unconfirmed));
        JSONObject args = new JSONObject().put("request_id", "qa_actual_offer_acceptance").put("offer_id", "off1")
                .put("expected_record_hash", originalHash).put("accepted_at", STAMP)
                .put("evidence", "Synthetic explicit acceptance of this exact finalized offer");
        int ordersBefore = store.data.getJSONArray("orders").length();
        String invoicesBefore = store.data.getJSONArray("invoices").toString();
        JSONObject accepted = store.decide(args);
        String orderId = accepted.getString("order_id");
        JSONObject order = PixelMcpWriteTools.find(store.data.getJSONArray("orders"), orderId);
        JSONObject acceptedOffer = store.data.getJSONArray("offers").getJSONObject(0);
        yes(acceptedOffer.getString("status").equals("accepted") && acceptedOffer.getString("converted_order_id").equals(orderId)
                        && store.data.getJSONArray("orders").length() == ordersBefore + 1 && order.getString("customer_id").equals("c1")
                        && order.getString("offer_id").equals("off1") && order.getJSONArray("offer_refs").getJSONObject(0).getString("offer_id").equals("off1"),
                "Explicit acceptance passes real conversion validation and atomically persists exactly one linked order");
        yes(OfferDecisionRules.contentVersion(acceptedOffer).equals(OfferDecisionRules.contentVersion(original))
                        && !accepted.getBoolean("invoice_created") && store.data.getJSONArray("invoices").toString().equals(invoicesBefore),
                "Actual acceptance preserves finalized offer content and PDF and never creates an invoice");
        JSONObject history = new OfferDecisionService(store.repository).query("get_offer_decision_history", new JSONObject().put("offer_id", "off1"));
        yes(history.getInt("total") == 1 && history.getJSONArray("history").getJSONObject(0).getString("evidence").equals(args.getString("evidence")),
                "Real accepted-version evidence survives readback");
        int readsBeforeReplay = store.documentReads;
        JSONObject replay = store.decide(args);
        yes(replay.getBoolean("replayed") && replay.getString("order_id").equals(orderId) && store.documentReads == readsBeforeReplay,
                "Exact acceptance request replay returns the same order without repeating the document or conversion effect");
        Map<String, String> ownerBeforeDuplicate = new HashMap<>(store.owner);
        rejected("OFFER_DECISION_ALREADY_RECORDED", () -> store.decide(copy(args).put("request_id", "qa_second_offer_acceptance")
                .put("expected_record_hash", PixelMcpWriteTools.hash(LocalJournalRules.clean(acceptedOffer)))));
        yes(store.owner.equals(ownerBeforeDuplicate) && store.data.getJSONArray("orders").length() == ordersBefore + 1
                        && store.data.getJSONArray("invoices").toString().equals(invoicesBefore),
                "A distinct duplicate acceptance rolls back completely and cannot create a second order or invoice");
    }

    private static void optionalOfferReview() throws Exception {
        JSONObject stateMaster = master(), value = offer();
        JSONObject entry = json("{id:'synthetic-catalog-1',title:'Synthetic price',default_price:31.55,price_confirmed:true,active:true}");
        stateMaster.getJSONArray("service_catalog").put(entry).put(json("{id:'synthetic-catalog-2',title:'Other synthetic price',default_price:99,price_confirmed:true}"));
        JSONObject position = value.getJSONArray("positions").getJSONObject(0);
        position.put("catalog_entry_id", entry.getString("id")).put("catalog_hash", PixelMcpWriteTools.hash(entry)).put("price_basis", "catalog"); position.remove("price_source");
        stateMaster.getJSONArray("offers").put(value);
        JSONObject state = new JSONObject().put("master", stateMaster).put("pending", new JSONArray());
        JSONObject regular = OfferTools.query("get_offer", json("{offer_id:'off1'}"), state);
        JSONObject detailed = OfferTools.query("get_offer", json("{offer_id:'off1',include_review:true}"), state);
        yes(!regular.has("review_snapshot"), "Default offer response remains unchanged");
        yes(regular.getString("record_hash").equals(detailed.getString("record_hash")), "Optional review uses the same record hash");
        yes(regular.getString("context_hash").equals(detailed.getString("context_hash")), "Optional review is bound to the same atomic context");
        JSONObject review = detailed.getJSONObject("review_snapshot");
        yes("Synthetic Alpha".equals(review.getJSONObject("customer").getString("name")), "Review includes the exact site customer");
        yes("Testweg 1".equals(review.getJSONObject("recipient").getString("street")), "Review includes the exact recipient");
        yes("Synthetic Issuer".equals(review.getJSONObject("business_settings").getString("owner_name")), "Review includes issuer settings");
        yes(review.getJSONArray("service_catalog").length() == 1, "Only referenced catalog prices are displayed");
        yes("synthetic-catalog-1".equals(review.getJSONArray("service_catalog").getJSONObject(0).getString("id")), "Displayed catalog price is the selected original");
        yes(detailed.getJSONObject("validation").getBoolean("valid"), "Review carries the same actual validation");
        stateMaster.getJSONArray("customers").getJSONObject(0).put("name", "Synthetic Changed Alpha");
        JSONObject changed = OfferTools.query("get_offer", json("{offer_id:'off1',include_review:true}"), state);
        yes(!changed.getString("context_hash").equals(detailed.getString("context_hash")), "Changed party invalidates the old finalization context");
        yes(changed.getString("record_hash").equals(detailed.getString("record_hash")), "Party change does not silently rewrite the offer record");
        yes("Synthetic Changed Alpha".equals(changed.getJSONObject("review_snapshot").getJSONObject("recipient").getString("name")), "Changed review and changed context describe the same party");
    }

    public static void main(String[] ignored) throws Exception {
        billingRelations();
        offersAndDecisions();
        acceptedOfferCreatesOrderAndReplays();
        optionalOfferReview();
        System.out.println("PASS " + checks + " Kotlin business assertions: customer/billing relationships, offer prices, offer decisions and contextual reviews; synthetic JVM only");
    }
}
