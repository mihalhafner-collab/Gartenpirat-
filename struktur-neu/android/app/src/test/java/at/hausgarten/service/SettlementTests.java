package at.hausgarten.service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local-settlement invariants against production Kotlin; every datum below is synthetic. */
public final class SettlementTests {
    private static int checks;
    private static final SettlementRules RULES = SettlementRules.INSTANCE;
    private interface Action { void run() throws Exception; }
    private static JSONObject copy(JSONObject value) throws Exception { return new JSONObject(value.toString()); }
    private static void yes(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void invalid(Action action, String reason) throws Exception {
        try { action.run(); throw new AssertionError("Accepted invalid input: " + reason); }
        catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
    }
    private static JSONObject source(String collection, String id) throws Exception { return new JSONObject().put("collection", collection).put("id", id); }
    private static JSONObject line(String collection, String source, String quantity, String price) throws Exception {
        return new JSONObject().put("title", "Synthetische Gartenleistung").put("description", "Frei erfunden: Sträucher und Grünfläche")
            .put("unit", "h").put("quantity", quantity).put("price", price).put("source_collection", collection).put("source_id", source);
    }
    private static JSONObject customer(String id) throws Exception {
        return new JSONObject().put("id", id).put("name", "Synthetischer Empfänger " + id).put("street", "Erfundener Weg 10")
            .put("postal_code", "1000").put("city", "Teststadt").put("country", "AT");
    }
    private static JSONObject work(String id, String order, String recipient) throws Exception {
        return new JSONObject().put("id", id).put("customer_id", "site").put("billing_customer_id", recipient)
            .put("order_id", order).put("activity", "Synthetische Arbeit " + id).put("work_date", "2026-11-01")
            .put("hours", "2").put("billed", 0);
    }
    private static JSONObject master() throws Exception {
        return new JSONObject().put("customers", new JSONArray().put(customer("recipient")).put(customer("other")).put(customer("site")))
            .put("orders", new JSONArray().put(new JSONObject().put("id", "order").put("title", "Erfundener Gartenauftrag")
                .put("customer_id", "site").put("billing_customer_id", "recipient").put("status", "Erledigt").put("scheduled_date", "2026-10-31")))
            .put("worklogs", new JSONArray().put(work("work-a", "order", "recipient")).put(work("work-b", "order", "recipient"))
                .put(work("standalone", "", "recipient")).put(work("foreign-recipient", "", "other")))
            .put("invoices", new JSONArray()).put("photos", new JSONArray()).put("offers", new JSONArray())
            .put("business_settings", new JSONObject().put("business_name", "Synthetischer Gartenbetrieb").put("owner_name", "Erfundene Person")
                .put("business_street", "Erfundene Straße 20").put("business_postal_code", "2000").put("business_city", "Testort")
                .put("business_country", "AT").put("tax_mode", "small_business"));
    }
    private static JSONObject draft(String route, JSONArray positions) throws Exception {
        return new JSONObject().put("route", route).put("title", "Synthetische Abrechnung").put("customer_id", "site")
            .put("billing_customer_id", "recipient").put("service_from", "2026-11-01").put("service_to", "2026-11-01")
            .put("payment_terms", "Binnen 14 Tagen").put("payment_details", "Synthetisch vereinbarte Überweisung")
            .put("positions", positions);
    }
    private static JSONObject ledger() throws Exception { return new JSONObject().put("records", new JSONArray()).put("payments", new JSONArray()); }
    private static JSONObject review(JSONObject draft, JSONObject data, String id) throws Exception { return RULES.review(RULES.normalized(draft, id), data); }
    private static JSONObject validation(JSONObject view, JSONObject data, JSONObject ledger) throws Exception { return RULES.validation(view, data, ledger, Collections.emptySet()); }
    private static boolean error(JSONObject result, String text) throws Exception {
        JSONArray errors = result.getJSONArray("errors");
        for (int i = 0; i < errors.length(); i++) if (errors.getString(i).contains(text)) return true;
        return false;
    }
    private static JSONObject bound(String id, String status, String route, JSONObject... sources) throws Exception {
        return new JSONObject().put("id", id).put("status", status).put("route", route)
            .put("kind", route.equals("billed") ? "invoice" : "internal").put("sources", new JSONArray(Arrays.asList(sources)));
    }
    private static void decimalsAndDrafts() throws Exception {
        yes(RULES.decimal(" 12,50 ").compareTo(new BigDecimal("12.50")) == 0, "German decimal quantity accepted exactly");
        yes(RULES.minor("900,00") == 90000L && RULES.minor("0.01") == 1L, "Euro fields become cents exactly once");
        for (String value : new String[]{"1.234,56", "1,234.56", "NaN", "Infinity", "1e2", "1,123456789"})
            invalid(() -> RULES.decimal(value), "ambiguous or nondecimal input " + value);
        invalid(() -> RULES.minor("1.001"), "a payment cannot contain fractional cents");
        JSONObject normalized = RULES.normalized(draft("billed", new JSONArray().put(line("", "", "3", "0,335"))), "rounding");
        yes(normalized.getJSONArray("positions").getJSONObject(0).getLong("amount_minor") == 101L, "Round the final line product HALF_UP, not the unit price");
        yes(RULES.review(normalized, master()).getLong("total_minor") == 101L, "Complete draft preview shows exact confirmed-position total before conscious closure");
        JSONObject incomplete = RULES.normalized(draft("labor", new JSONArray().put(line("", "", "1", "200,00")).put(line("", "", "2", ""))), "incomplete");
        JSONArray positions = incomplete.getJSONArray("positions");
        yes(positions.getJSONObject(0).getLong("amount_minor") == 20000L && positions.getJSONObject(1).isNull("amount_minor"), "Known part survives and unknown price is not zero");
        yes(!RULES.review(incomplete, master()).has("total_minor"), "Incomplete preview does not present a partial amount as the full total");
        JSONObject overflowing = copy(normalized);
        overflowing.getJSONArray("positions").getJSONObject(0).put("amount_minor", Long.MAX_VALUE);
        overflowing.getJSONArray("positions").put(copy(normalized.getJSONArray("positions").getJSONObject(0)).put("id", "rounding:2"));
        JSONObject overflowingView = RULES.review(overflowing, master());
        yes(overflowingView.optBoolean("total_overflow") && error(validation(overflowingView, master(), ledger()), "Gesamtbetrag"), "Aggregate overflow blocks closure with a clear amount error");
        yes(!validation(RULES.review(incomplete, master()), master(), ledger()).getBoolean("valid"), "Incomplete draft remains legal to save but cannot close");
        invalid(() -> RULES.normalized(draft("billed", new JSONArray().put(line("", "", "-1", "10"))), "negative"), "negative original quantity");
        JSONObject unfinishedSource = RULES.normalized(draft("billed", new JSONArray().put(line("worklogs", "", "1", "10"))), "unfinished-source");
        yes(unfinishedSource.getJSONArray("positions").getJSONObject(0).getString("source_collection").equals("worklogs")
            && unfinishedSource.getJSONArray("sources").length() == 0, "Draft preserves selected source type while no source record has been picked yet");
        JSONObject unfinishedValidation = validation(RULES.review(unfinishedSource, master()), master(), ledger());
        yes(!unfinishedValidation.getBoolean("valid") && error(unfinishedValidation, "Quellzuordnung ist unvollständig"),
            "An incomplete source draft cannot close or silently become a manual operation");
        yes(normalized.getJSONArray("positions").getJSONObject(0).getString("settlement_id").equals("rounding"), "Every position belongs to its operation");
        JSONObject large = RULES.normalized(draft("billed", new JSONArray().put(line("", "", "1000", "5000"))), "large");
        yes(RULES.total(large) == 500000000L, "Historic invoice volumes and 2500 EUR examples impose no technical amount cap");
    }
    private static void sourceBindingsAndReview() throws Exception {
        JSONObject data = master();
        JSONObject view = review(draft("billed", new JSONArray().put(line("worklogs", "work-a", "2", "10"))
            .put(line("worklogs", "work-a", "1", "4"))), data, "multiple-lines");
        yes(view.getJSONArray("positions").length() == 2 && view.getJSONArray("sources").length() == 1 && RULES.total(view) == 2400L,
            "Time and material lines remain distinct while their source binds once");
        yes(validation(view, data, ledger()).getBoolean("valid"), "Ordinary KU invoice validates without tax number or UID");
        JSONObject standardTax = copy(data); standardTax.getJSONObject("business_settings").put("tax_mode", "standard");
        yes(error(validation(review(draft("billed", new JSONArray().put(line("", "", "1", "1"))), standardTax, "standard-tax"), standardTax, ledger()), "Steuerstatus"),
            "Unsupported tax status cannot silently become a KU invoice");
        JSONObject historical = copy(data); historical.getJSONArray("worklogs").getJSONObject(0).put("source", "sevdesk_trial");
        yes(error(validation(view, historical, ledger()), "Versuchsdaten"), "Historical sevDesk attempts never become newly issued services automatically");
        yes(view.getJSONObject("recipient").getString("id").equals("recipient") && !view.getJSONObject("recipient").getString("id").equals(view.getString("customer_id")),
            "Actual site customer and legal recipient stay separate");
        JSONObject mixed = review(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "10"))
            .put(line("worklogs", "foreign-recipient", "1", "20"))), data, "mixed-recipient");
        yes(error(validation(mixed, data, ledger()), "Rechnungsempfänger"), "Collection cannot mix legal recipients");
        JSONObject overlap = review(draft("billed", new JSONArray().put(line("orders", "order", "1", "900"))
            .put(line("worklogs", "work-a", "2", "50"))), data, "order-and-work");
        yes(error(validation(overlap, data, ledger()), "gemeinsam"), "Order amount and its work amount never both count");
        JSONObject activeLedger = ledger().put("records", new JSONArray().put(bound("reserved", "prepared", "billed", source("worklogs", "work-a"))));
        yes(error(validation(view, data, activeLedger), "gebunden"), "Prepared operation reserves the work before its PDF exists");
        JSONObject nextWork = review(draft("billed", new JSONArray().put(line("worklogs", "work-b", "1", "20"))), data, "other-work");
        yes(validation(nextWork, data, activeLedger).getBoolean("valid"), "Further actual work of the order remains independently open");
        JSONObject opposite = review(draft("labor", new JSONArray().put(line("worklogs", "work-b", "1", "20"))), data, "opposite");
        yes(error(validation(opposite, data, activeLedger), "anderen Abrechnungsart"), "Route switch requires correction of the existing active operation");
        activeLedger.put("records", new JSONArray().put(bound("reserved", "confirmed", "billed", source("orders", "order"))));
        yes(error(validation(nextWork, data, activeLedger), "gebunden"), "An order reservation also protects its associated actual work");
        activeLedger.put("records", new JSONArray().put(bound("old", "cancelled", "billed", source("worklogs", "work-a"))));
        yes(validation(view, data, activeLedger).getBoolean("valid"), "A neutralized operation releases only its own active binding");
        JSONObject marked = copy(data); marked.getJSONArray("orders").getJSONObject(0).put("status", "labor clandestinus");
        yes(error(validation(view, marked, ledger()), "Benutzerzuordnung"), "Issuing invoice never silently changes a personal route");
        JSONObject restoration = ledger().put("restore_epoch", "synthetic-epoch-2").put("restored_sources", new JSONArray().put(source("worklogs", "work-a")));
        yes(error(validation(view, data, restoration), "Restaurierte Quelle"), "Missing restored binding is not proof a service was never settled");
        restoration.put("source_reviews", new JSONArray().put(source("worklogs", "work-a").put("restore_epoch", "synthetic-epoch-1")));
        yes(!validation(view, data, restoration).getBoolean("valid"), "An old restore review does not clear the current restored source");
        restoration.put("source_reviews", new JSONArray().put(source("worklogs", "work-a").put("restore_epoch", "synthetic-epoch-2")));
        yes(validation(view, data, restoration).getBoolean("valid"), "Conscious current source reconciliation permits restored work");
        yes(validation(nextWork, data, restoration).getBoolean("valid"), "New or unflagged work remains usable after restore");
        JSONObject abroad = copy(data); abroad.getJSONArray("customers").getJSONObject(0).put("country", "DE");
        yes(error(validation(review(draft("billed", new JSONArray().put(line("", "", "1", "1"))), abroad, "abroad"), abroad, ledger()), "Inlandsabrechnung"),
            "Unsupported foreign billing is clearly rejected");
    }
    /** Exercises the actual service and request transaction; only storage/PDF IO are synthetic ports. */
    private static final class Memory implements InvocationHandler, OwnerRequestTransactions.Store {
        final JSONObject master;
        final Map<String, String> owner = new HashMap<>();
        final OwnerRepository repository;
        Map<String, String> before;
        boolean committed, failPdf;
        String epoch = "synthetic-epoch-0", series = "GP-SYNTHETIC-000000000001";
        int documentWrites, documentReads;
        Memory() throws Exception { this(SettlementTests.master(), Collections.emptyMap()); }
        Memory(JSONObject master, Map<String,String> previous) throws Exception {
            this.master = copy(master); owner.putAll(previous);
            owner.putIfAbsent("personal_paid", "[]"); owner.putIfAbsent("personal_paid_migrated", "1");
            repository = (OwnerRepository) Proxy.newProxyInstance(OwnerRepository.class.getClassLoader(), new Class<?>[]{OwnerRepository.class}, this);
        }
        JSONObject ledger() throws Exception { return new JSONObject(owner.getOrDefault(SettlementRules.KEY, "{}")); }
        JSONObject record(String id) throws Exception { return RULES.find(ledger(), id); }
        synchronized JSONObject run(String name, JSONObject args, String request) throws Exception {
            String fingerprint = RULES.hash(new JSONObject().put("tool", name).put("args", args));
            return OwnerRequestTransactions.runRequest(this, request, fingerprint,
                () -> new SettlementApplicationService(repository).perform(name, args, request), "2026-11-06T10:00:00Z");
        }
        String save(JSONObject draft, String request) throws Exception {
            return run("save_settlement_draft", new JSONObject().put("draft", draft), request).getString("settlement_id");
        }
        JSONObject prepareArgs(String id) throws Exception {
            return new JSONObject().put("settlement_id", id).put("expected_review_hash", RULES.hash(RULES.review(record(id), master)))
                .put("confirmed", true).put("domestic_eur_confirmed", true).put("issuer_details_confirmed", true)
                .put("electronic_delivery_accepted", true);
        }
        JSONObject prepare(String id, String request) throws Exception { return run("prepare_settlement", prepareArgs(id), request); }
        JSONObject complete(String id, String request) throws Exception { return run("complete_settlement", new JSONObject().put("settlement_id", id), request); }
        JSONObject proof(String id) throws Exception { return new JSONObject().put("settlement_id", id).put("expected_record_hash", RULES.hash(record(id))); }
        JSONObject pay(String id, String amount, String request) throws Exception {
            return run("add_settlement_payment", proof(id).put("amount", amount).put("date", "2026-11-06"), request);
        }
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "effective": return copy(master);
                case "ownerValue": return owner.getOrDefault((String) args[0], (String) args[1]);
                case "putOwnerValue": owner.put((String) args[0], (String) args[1]); return null;
                case "settlementSeries": return series;
                case "settlementRestoreEpoch": return epoch;
                case "createSettlementDocument": {
                    JSONObject record = (JSONObject) args[0];
                    if (failPdf) throw new java.io.IOException("SYNTHETIC_PDF_FAILURE");
                    yes(record.getString("route").equals("billed") && record.getString("status").equals("prepared") && !record.getString("invoice_number").isBlank(),
                        "PDF IO receives frozen, numbered, separately committed prepared invoice");
                    yes(record.getString("tax_exemption_text").equals("Umsatzsteuerbefreit gemäß § 6 Abs. 1 Z 27 UStG"), "PDF carries exact KU exemption");
                    for (JSONObject position : RULES.rows(record.getJSONArray("positions"))) {
                        BigDecimal calculated = new BigDecimal(position.getString("quantity")).multiply(new BigDecimal(position.getString("price")))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                        yes(calculated.movePointRight(2).longValueExact() == position.getLong("amount_minor"), "Frozen PDF line arithmetic is coherent, including Storno signs");
                    }
                    documentWrites++;
                    String hash = RULES.hash(record);
                    return new JSONObject().put("pdf_sha256", hash).put("pdf_storage_name", hash + ".pdf")
                        .put("pdf_filename", record.getString("invoice_number") + ".pdf").put("pdf_byte_size", 100);
                }
                case "readDocument":
                    yes(((JSONObject) args[0]).has("pdf_sha256"), "Business completion requests checked original readback");
                    documentReads++; return null;
                default: throw new AssertionError("Unexpected settlement storage operation: " + method.getName());
            }
        }
        public void begin() { before = new HashMap<>(owner); committed = false; }
        public String read(String key) { return owner.getOrDefault(key, ""); }
        public void write(String key, String value) { owner.put(key, value); }
        public void successful() { committed = true; }
        public void end() { if (!committed) { owner.clear(); owner.putAll(before); } }
    }
    private static void closureReplayPaymentsAndCorrections() throws Exception {
        Memory memory = new Memory();
        String originalMaster = RULES.hash(memory.master), originalMarks = memory.owner.get("personal_paid");
        String invoice = memory.save(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "900"))), "synthetic-save-invoice");
        JSONObject prepareArgs = memory.prepareArgs(invoice);
        JSONObject prepared = memory.run("prepare_settlement", prepareArgs, "synthetic-prepare-invoice");
        String number = prepared.getJSONObject("record").getString("invoice_number");
        yes(prepared.getString("status").equals("prepared") && memory.documentWrites == 0, "Number and frozen content commit before filesystem rendering");
        yes(memory.run("prepare_settlement", prepareArgs, "synthetic-prepare-invoice").getBoolean("replayed") && memory.record(invoice).getString("invoice_number").equals(number),
            "Prepare retry reserves no second number");
        invalid(() -> memory.run("prepare_settlement", copy(prepareArgs).put("electronic_delivery_accepted", false), "synthetic-prepare-invoice"), "same request with different arguments");
        memory.failPdf = true;
        try { memory.complete(invoice, "synthetic-complete-invoice"); throw new AssertionError("Synthetic PDF failure accepted"); }
        catch (java.io.IOException expected) { yes(memory.record(invoice).getString("status").equals("prepared"), "PDF failure preserves durable prepared source reservation"); }
        yes(memory.record(invoice).getString("invoice_number").equals(number) && !memory.record(invoice).has("pdf"), "Failed PDF neither renumbers nor acknowledges business closure");
        Memory reopened = new Memory(memory.master, memory.owner);
        JSONObject finished = reopened.complete(invoice, "synthetic-complete-invoice");
        yes(finished.getString("status").equals("confirmed") && reopened.documentWrites == 1 && reopened.documentReads == 1, "New process continues same reserved invoice to one checked original");
        JSONObject beforeInvoice = copy(reopened.record(invoice));
        yes(reopened.complete(invoice, "synthetic-complete-invoice").getBoolean("replayed") && reopened.documentWrites == 1, "Completion replay performs no second PDF write");
        reopened.master.getJSONArray("customers").getJSONObject(0).put("name", "Später geänderter synthetischer Empfänger");
        yes(reopened.record(invoice).getJSONObject("recipient").getString("name").equals(beforeInvoice.getJSONObject("recipient").getString("name")), "Later master-data changes never rewrite issued recipient snapshot");
        reopened.master.getJSONArray("customers").getJSONObject(0).put("name", memory.master.getJSONArray("customers").getJSONObject(0).getString("name"));
        String internal = reopened.save(draft("labor", new JSONArray().put(line("worklogs", "standalone", "1", "200"))), "synthetic-save-internal");
        reopened.prepare(internal, "synthetic-prepare-internal"); reopened.complete(internal, "synthetic-complete-internal");
        yes(!reopened.record(internal).has("invoice_number") && !reopened.record(internal).has("pdf") && reopened.documentWrites == 1, "Labor creates a confirmed internal operation without invoice number, PDF or artificial invoice");
        yes(reopened.record(invoice).getLong("total_minor") == 90000L && reopened.record(internal).getLong("total_minor") == 20000L
            && Math.addExact(reopened.record(invoice).getLong("total_minor"), reopened.record(internal).getLong("total_minor")) == 110000L,
            "900 EUR invoice plus 200 EUR internal remain exactly 1100 EUR after process reopening");
        yes(RULES.hash(reopened.master).equals(originalMaster) && reopened.owner.get("personal_paid").equals(originalMarks), "Both closures preserve source records and user markings byte for byte");
        reopened.pay(invoice, "300,00", "synthetic-payment-part");
        yes(RULES.paid(reopened.ledger(), invoice) == 30000L, "Partial payment is its own dated entry");
        reopened.pay(invoice, "700,00", "synthetic-payment-over");
        yes(RULES.paid(reopened.ledger(), invoice) == 100000L && reopened.record(invoice).getLong("total_minor") == 90000L, "Overpayment is retained without changing confirmed billed amount");
        String paymentId = reopened.ledger().getJSONArray("payments").getJSONObject(0).getString("id");
        reopened.run("add_settlement_payment", reopened.proof(invoice).put("amount", "-50,00").put("date", "2026-11-06")
            .put("reversal_of", paymentId).put("note", "Synthetische Teilgegenbuchung"), "synthetic-payment-reversal");
        yes(RULES.paid(reopened.ledger(), invoice) == 95000L, "Counter-entry preserves the original positive payment and net paid sum");
        invalid(() -> reopened.run("add_settlement_payment", reopened.proof(invoice).put("amount", "-251,00").put("date", "2026-11-06")
            .put("reversal_of", paymentId).put("note", "Zu große synthetische Gegenbuchung"), "synthetic-payment-invalid"), "counter-entries exceeding original payment");
        int paymentCount = reopened.ledger().getJSONArray("payments").length();
        JSONObject correction = reopened.run("correct_settlement", reopened.proof(invoice).put("confirmed", true).put("reason", "Synthetischer vollständiger Storno")
            .put("access_evidence", "Synthetischer Nachweis separat dokumentiert"), "synthetic-invoice-correction");
        String correctionId = correction.getString("settlement_id");
        yes(reopened.record(invoice).getString("status").equals("confirmed") && correction.getString("status").equals("prepared"), "Original remains active until separate cancellation PDF has closed");
        reopened.complete(correctionId, "synthetic-complete-cancellation");
        yes(reopened.record(invoice).getString("status").equals("cancelled") && !reopened.record(correctionId).getString("invoice_number").equals(number)
            && reopened.record(correctionId).getString("original_number").equals(number), "Full cancellation has own number and immutable original reference");
        yes(reopened.record(invoice).getLong("total_minor") + reopened.record(correctionId).getLong("total_minor") == 0
            && reopened.record(invoice).getJSONObject("pdf").toString().equals(beforeInvoice.getJSONObject("pdf").toString()), "Cancellation neutralizes only its original amount and preserves issued original PDF metadata");
        yes(reopened.ledger().getJSONArray("payments").length() == paymentCount && RULES.paid(reopened.ledger(), invoice) == 95000L, "Storno neither deletes payments nor claims a refund");
        JSONObject voided = reopened.run("correct_settlement", reopened.proof(internal).put("confirmed", true).put("reason", "Synthetische interne Aufhebung"), "synthetic-internal-correction");
        yes(voided.getString("status").equals("confirmed") && voided.getJSONObject("record").getString("kind").equals("void")
            && !voided.getJSONObject("record").has("invoice_number") && !voided.getJSONObject("record").has("pdf"), "Internal void remains internal and creates no invented credit invoice");
        yes(reopened.record(internal).getString("status").equals("cancelled") && reopened.documentWrites == 2
            && reopened.ledger().getJSONArray("payments").length() == paymentCount, "Internal correction preserves other original PDFs and all payments");
        String manual = reopened.save(draft("labor", new JSONArray().put(line("", "", "1", "15,55"))), "synthetic-manual");
        reopened.prepare(manual, "synthetic-manual-prepare"); reopened.complete(manual, "synthetic-manual-close");
        yes(reopened.record(manual).getJSONArray("sources").length() == 0 && RULES.hash(reopened.master).equals(originalMaster), "Manual settlement is its own unit without manufactured order or worklog");
    }
    private static void restoredReservations() throws Exception {
        for (String route : new String[]{"billed", "labor"}) for (boolean manual : new boolean[]{false, true}) {
            Memory original = new Memory();
            String suffix = route + (manual ? "-manual" : "-source");
            String id = original.save(draft(route, new JSONArray().put(line(manual ? "" : "worklogs", manual ? "" : "work-a", "1", "75"))), "restore-save-" + suffix);
            original.prepare(id, "restore-prepare-" + suffix);
            String number = original.record(id).optString("invoice_number");
            Memory restored = new Memory(original.master, original.owner);
            restored.epoch = "synthetic-epoch-2"; restored.series = "GP-SYNTHETIC-000000000002";
            invalid(() -> restored.complete(id, "restore-complete-" + suffix), "unreviewed old reservation " + suffix);
            yes(restored.documentWrites == 0 && restored.record(id).getString("status").equals("prepared"), "Restored prepared operation blocks before any PDF or business closure, including manual work");
            JSONObject currentLedger = restored.ledger().put("reservation_reviews", new JSONArray().put(new JSONObject().put("settlement_id", id).put("restore_epoch", "synthetic-epoch-1")));
            restored.owner.put(SettlementRules.KEY, currentLedger.toString());
            invalid(() -> restored.complete(id, "restore-complete-" + suffix), "stale reservation reconciliation " + suffix);
            JSONObject review = new JSONObject().put("settlement_id", id).put("sources", restored.record(id).getJSONArray("sources"))
                .put("confirmed", true).put("evidence", "Synthetischer aktueller Abgleich: Belege, Originale, Leistungsbindungen und Zahlungen geprüft.");
            restored.run("review_restored_sources", review, "restore-review-" + suffix);
            JSONObject completed = restored.complete(id, "restore-complete-" + suffix);
            yes(completed.getString("status").equals("confirmed") && restored.record(id).optString("invoice_number").equals(number), "Consciously reconciled restored reservation continues the same frozen record and reserved number");
            if (route.equals("billed")) {
                yes(restored.ledger().getJSONObject("series_counters").optLong(restored.series) == 0L, "Continuing a reviewed reservation consumes no replacement number or unrelated new-series counter");
                String fresh = restored.save(draft("billed", new JSONArray().put(line("", "", "1", "2"))), "restore-fresh-" + suffix);
                restored.prepare(fresh, "restore-fresh-prepare-" + suffix);
                yes(restored.record(fresh).getString("invoice_number").startsWith(restored.series + "-") && !restored.record(fresh).getString("invoice_number").equals(number),
                    "New invoices after restore use the external current series, never the restored old counter");
            } else yes(!restored.record(id).has("invoice_number") && restored.documentWrites == 0, "Restored internal reservation remains internal after its current reconciliation");
        }
    }
    private static long accountingTotal(List<JSONObject> rows, String route) throws Exception {
        long total = 0L;
        for (JSONObject row : rows) if (!row.isNull("amount_minor") && row.getString("route").equals(route)
            && Arrays.asList("", "partial_amount").contains(row.getString("issue_code"))) total = Math.addExact(total, row.getLong("amount_minor"));
        return total;
    }
    private static void groupedOverviewAndLaterWork() throws Exception {
        Memory memory = new Memory();
        memory.master.getJSONArray("orders").getJSONObject(0).put("status", "Abgerechnet");
        memory.master.getJSONArray("orders").put(new JSONObject().put("id", "order-two").put("title", "Zweiter synthetischer Auftrag")
            .put("customer_id", "site").put("billing_customer_id", "recipient").put("status", "Erledigt"));
        memory.master.getJSONArray("worklogs").put(work("work-two", "order-two", "recipient"));
        String collection = memory.save(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "300"))
            .put(line("worklogs", "work-two", "1", "600"))), "overview-collection");
        memory.prepare(collection, "overview-collection-prepare"); memory.complete(collection, "overview-collection-close");
        String internal = memory.save(draft("labor", new JSONArray().put(line("worklogs", "standalone", "1", "200"))), "overview-labor");
        memory.prepare(internal, "overview-labor-prepare"); memory.complete(internal, "overview-labor-close");
        Memory reopened = new Memory(memory.master, memory.owner);
        List<JSONObject> rows = AccountingRules.INSTANCE.project(reopened.master, Collections.emptySet(), new JSONArray(), reopened.ledger());
        JSONObject first = rows.stream().filter(row -> row.optString("key").equals("orders:order")).findFirst().orElseThrow();
        JSONObject second = rows.stream().filter(row -> row.optString("key").equals("orders:order-two")).findFirst().orElseThrow();
        yes(rows.stream().filter(row -> row.optString("collection").equals("orders")).count() == 2L && first.optLong("amount_minor") == 30000L && second.optLong("amount_minor") == 60000L,
            "Collection invoice allocates each actual line once to its order, not its whole total to every order");
        yes(first.getString("issue_code").equals("partial_amount") && first.getString("work_date").equals("2026-11-01"), "Marked order retains known partial money, explicit pending scope and original work-date basis");
        yes(accountingTotal(rows, "billed") == 90000L && accountingTotal(rows, "labor") == 20000L
            && Math.addExact(accountingTotal(rows, "billed"), accountingTotal(rows, "labor")) == 110000L,
            "Shared accounting projection still yields 900 invoice + 200 Labor = 1100 after reopening, including known partial money");
        Memory orderScope = new Memory();
        String orderInvoice = orderScope.save(draft("billed", new JSONArray().put(line("orders", "order", "1", "100"))), "scope-order");
        orderScope.prepare(orderInvoice, "scope-order-prepare");
        orderScope.master.getJSONArray("worklogs").put(work("new-later-work", "order", "recipient"));
        JSONObject later = review(draft("billed", new JSONArray().put(line("worklogs", "new-later-work", "1", "25"))), orderScope.master, "later");
        JSONObject old = review(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "25"))), orderScope.master, "old");
        yes(validation(later, orderScope.master, orderScope.ledger()).getBoolean("valid") && !validation(old, orderScope.master, orderScope.ledger()).getBoolean("valid"),
            "Order binding freezes its original actual work scope; later work remains open while included work remains protected");
    }
    private static void legacyReferencesAndMissingDraftSources() throws Exception {
        JSONObject data = master();
        yes(data.getJSONArray("worklogs").getJSONObject(0).optString("source").isEmpty(), "Synthetic ordinary work has no sevDesk source marker");
        data.getJSONArray("invoices").put(new JSONObject().put("id", "synthetic-old-reference").put("sevdesk_id", "synthetic-sevdesk-attempt")
            .put("issued_at", "2026-10-01T10:00:00Z").put("worklog_ids", new JSONArray().put("work-a")));
        JSONObject candidate = review(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "900"))), data, "legacy-ordinary-work");
        JSONObject reconciliation = ledger().put("restore_epoch", "synthetic-current-epoch");
        JSONObject rejected = validation(candidate, data, reconciliation);
        yes(!rejected.getBoolean("valid") && error(rejected, "frühere Belegverknüpfung"),
            "An ordinary unmarked worklog linked by an issued/sevDesk legacy invoice cannot be reissued by default");
        yes(RULES.externalSources(data).stream().anyMatch(ref -> ref.optString("collection").equals("worklogs") && ref.optString("id").equals("work-a")),
            "Legacy worklog_ids are detected even without a worklog source flag");
        reconciliation.put("source_reviews", new JSONArray().put(source("worklogs", "work-a").put("restore_epoch", "synthetic-old-epoch")
            .put("evidence", "Veraltete synthetische Sichtung")));
        yes(!validation(candidate, data, reconciliation).getBoolean("valid"), "Old epoch reconciliation does not authorize reissuing legacy-linked work");
        Memory memory = new Memory(data, Collections.emptyMap()); memory.epoch = "synthetic-current-epoch";
        memory.owner.put(SettlementRules.KEY, reconciliation.toString());
        String evidence = "Synthetischer aktueller Abgleich: Altverknüpfung war nur Versuch; weder tatsächlicher sevDesk-Umsatz noch ausgestelltes Original oder Zahlung vorhanden.";
        memory.run("review_restored_sources", new JSONObject().put("sources", new JSONArray().put(source("worklogs", "work-a")))
            .put("confirmed", true).put("evidence", evidence), "synthetic-legacy-reconciliation");
        yes(validation(candidate, data, memory.ledger()).getBoolean("valid"), "Only a consciously recorded current reconciliation of the synthetic trial reference clears the warning");
        JSONArray reviews = memory.ledger().getJSONArray("source_reviews");
        JSONObject current = reviews.getJSONObject(reviews.length() - 1);
        yes(current.getString("evidence").equals(evidence) && current.getString("restore_epoch").equals("synthetic-current-epoch")
            && current.getString("id").equals("work-a"), "Explicit actual-state evidence remains attached to the exact source and current epoch");
        JSONObject restoredData = master();
        JSONObject restoredLedger = ledger().put("restore_epoch", "synthetic-restore-2")
            .put("restored_sources", new JSONArray().put(source("worklogs", "work-a")));
        JSONObject newOrder = review(draft("billed", new JSONArray().put(line("orders", "order", "1", "100"))), restoredData, "new-order-old-work");
        yes(!validation(newOrder, restoredData, restoredLedger).getBoolean("valid") && error(validation(newOrder, restoredData, restoredLedger), "Restaurierte Quelle"),
            "New unflagged order cannot bypass review of an included restored worklog");
        restoredLedger.put("source_reviews", new JSONArray().put(source("worklogs", "work-a").put("restore_epoch", "synthetic-restore-2")
            .put("evidence", "Synthetischer aktueller Belegstand der enthaltenen alten Arbeit geprüft")));
        yes(validation(newOrder, restoredData, restoredLedger).getBoolean("valid"), "Review of the included restored work applies to the intended order scope without blocking unflagged work");
        JSONObject missingDraft = RULES.normalized(draft("billed", new JSONArray().put(line("worklogs", "synthetic-missing-work", "1", "40"))), "missing-source-draft");
        JSONObject readable = RULES.review(missingDraft, master());
        yes(readable.getJSONArray("positions").length() == 1
            && readable.getJSONArray("source_snapshots").getJSONObject(0).getJSONObject("record").getBoolean("missing"),
            "Draft with deleted or missing source stays readable with an explicit missing snapshot");
        JSONObject missingValidation = validation(readable, master(), ledger());
        yes(!missingValidation.getBoolean("valid") && error(missingValidation, "Leistungsquelle fehlt"), "Readable missing-source draft is blocked from final closure");
    }
    private static void abandonPreparedRecovery() throws Exception {
        for (String route : new String[]{"billed", "labor"}) {
            Memory memory = new Memory();
            String id = memory.save(draft(route, new JSONArray().put(line("worklogs", "work-a", "1", "90"))), "abandon-draft-" + route);
            memory.prepare(id, "abandon-prepare-" + route);
            String reservedNumber = memory.record(id).optString("invoice_number");
            String frozenLines = memory.record(id).getJSONArray("positions").toString();
            String frozenSources = memory.record(id).getJSONArray("source_snapshots").toString();
            if (route.equals("billed")) {
                memory.failPdf = true;
                try { memory.complete(id, "abandon-failed-pdf"); throw new AssertionError("Permanent synthetic PDF error accepted"); }
                catch (java.io.IOException expected) { checks++; }
            }
            JSONObject abandoned = memory.run("abandon_prepared_settlement", memory.proof(id).put("reason", "Synthetisch dauerhaft nicht erzeugbares Original; reservierten Vorgang bewusst beenden.")
                .put("confirmed", true), "abandon-reservation-" + route);
            yes(abandoned.getString("status").equals("abandoned") && memory.record(id).getString("status").equals("abandoned"),
                "Unfinishable prepared operation has an explicit retained abandonment outcome");
            yes(memory.record(id).optString("invoice_number").equals(reservedNumber)
                && memory.record(id).getJSONArray("positions").toString().equals(frozenLines)
                && memory.record(id).getJSONArray("source_snapshots").toString().equals(frozenSources),
                "Abandonment preserves frozen content, source evidence and any already reserved invoice number");
            yes(memory.documentWrites == 0 && !memory.record(id).has("pdf") && !RULES.active(memory.record(id)),
                "Abandonment releases the active reservation without inventing a Storno PDF or issued invoice");
            invalid(() -> memory.complete(id, "abandon-old-complete-" + route), "abandoned reservation cannot later complete");
            String replacement = memory.save(draft(route, new JSONArray().put(line("worklogs", "work-a", "1", "90"))), "abandon-new-draft-" + route);
            memory.prepare(replacement, "abandon-new-prepare-" + route);
            if (route.equals("billed")) {
                yes(!memory.record(replacement).getString("invoice_number").equals(reservedNumber)
                    && memory.record(replacement).getLong("sequence") == memory.record(id).getLong("sequence") + 1L,
                    "A new draft may use the freed service but never reuses the abandoned reserved invoice number");
            } else yes(!memory.record(id).has("invoice_number") && !memory.record(replacement).has("invoice_number"),
                "Abandoned and replacement Labor operations remain without invoice numbers");
            memory.failPdf = false;
            memory.complete(replacement, "abandon-new-complete-" + route);
            invalid(() -> memory.run("abandon_prepared_settlement", memory.proof(replacement).put("reason", "Unzulässiges Aufgeben bestätigten Vorgangs")
                .put("confirmed", true), "abandon-confirmed-" + route), "confirmed originals require normal cancellation or internal void");
            yes(memory.record(replacement).getString("status").equals("confirmed") && memory.documentWrites == (route.equals("billed") ? 1 : 0),
                "Recovery produces only the chosen new final document and never alters an already confirmed original");
            Memory oldBackup = new Memory();
            String restoredId = oldBackup.save(draft(route, new JSONArray().put(line("", "", "1", "10"))), "abandon-restored-draft-" + route);
            oldBackup.prepare(restoredId, "abandon-restored-prepare-" + route);
            Memory restored = new Memory(oldBackup.master, oldBackup.owner); restored.epoch = "synthetic-abandon-restore-2";
            JSONObject abandonArgs = restored.proof(restoredId).put("reason", "Synthetischer vorbereiteter Altvorgang soll aufgegeben werden").put("confirmed", true);
            invalid(() -> restored.run("abandon_prepared_settlement", abandonArgs, "abandon-restored-action-" + route),
                "restored preparation cannot be abandoned without current actual-state reconciliation");
            restored.run("review_restored_sources", new JSONObject().put("settlement_id", restoredId).put("sources", new JSONArray())
                .put("confirmed", true).put("evidence", "Synthetischer aktueller Abgleich: kein ausgestelltes Original und kein Geschäftsabschluss zum vorbereiteten Vorgang."), "abandon-restored-review-" + route);
            restored.run("abandon_prepared_settlement", abandonArgs, "abandon-restored-action-" + route);
            yes(restored.record(restoredId).getString("status").equals("abandoned") && restored.documentWrites == 0,
                "Conscious current reconciliation permits abandoning even a manual restored preparation without fabricated documents");
        }
    }
    private static void simultaneousReservations() throws Exception {
        Memory memory = new Memory();
        String a = memory.save(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "30"))), "synthetic-concurrent-draft-a");
        String b = memory.save(draft("billed", new JSONArray().put(line("worklogs", "work-a", "1", "30"))), "synthetic-concurrent-draft-b");
        JSONObject argsA = memory.prepareArgs(a), argsB = memory.prepareArgs(b);
        java.util.concurrent.ExecutorService threads = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();
            results.add(threads.submit(() -> { start.await(); try { memory.run("prepare_settlement", argsA, "synthetic-concurrent-prepare-a"); return true; } catch (IllegalArgumentException expected) { return false; } }));
            results.add(threads.submit(() -> { start.await(); try { memory.run("prepare_settlement", argsB, "synthetic-concurrent-prepare-b"); return true; } catch (IllegalArgumentException expected) { return false; } }));
            start.countDown(); int successes = 0;
            for (java.util.concurrent.Future<Boolean> future : results) if (future.get(20, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            yes(successes == 1 && RULES.records(memory.ledger()).stream().filter(RULES::active).count() == 1, "Serialized competing closures bind the same service once");
            JSONObject counters = memory.ledger().getJSONObject("series_counters");
            yes(counters.getLong("GP-SYNTHETIC-000000000001") == 1L, "Rejected competing closure consumes no second number");
        } finally { threads.shutdownNow(); }
    }
    private static void openWorkPrefill() throws Exception {
        JSONArray works = master().getJSONArray("worklogs");
        JSONObject bound = new JSONObject().put("status", "confirmed").put("kind", "invoice").put("billing_customer_id", "recipient")
            .put("positions", new JSONArray().put(new JSONObject().put("unit", "Stunden").put("price", "45")))
            .put("sources", new JSONArray().put(source("worklogs", "work-a")));
        JSONObject later = new JSONObject().put("status", "draft").put("kind", "invoice").put("billing_customer_id", "other")
            .put("positions", new JSONArray().put(new JSONObject().put("unit", "Stunden").put("price", "50")));
        JSONArray ids = new JSONArray().put("work-a").put("work-b").put("standalone");
        JSONObject draft = RULES.prefill(new JSONObject().put("billing_customer_id", "recipient").put("work_record_ids", ids), works, List.of(bound, later));
        JSONArray lines = draft.getJSONArray("positions");
        yes(lines.length() == 2, "Work bound by an active closure is not offered again");
        yes(lines.getJSONObject(0).getString("price").equals("45") && lines.getJSONObject(1).getString("price").equals("45"), "Recipient's own last hourly rate wins");
        yes(lines.getJSONObject(0).getString("unit").equals("Stunden") && lines.getJSONObject(0).getString("quantity").equals("2"), "Hours become the quantity");
        yes(lines.getJSONObject(0).getString("source_collection").equals("worklogs") && !lines.getJSONObject(0).getString("source_id").equals("work-a"), "Positions keep their work source");
        yes(lines.getJSONObject(0).getString("description").equals("01.11.2026"), "Work date is shown as Austrian date");
        yes(draft.getString("route").equals("billed") && draft.getString("billing_customer_id").equals("recipient") && draft.getString("customer_id").equals("site"), "Recipient and single work customer are set");
        yes(draft.getString("service_from").equals("2026-11-01") && draft.getString("service_to").equals("2026-11-01"), "Service period follows the work dates");
        JSONObject fallback = RULES.prefill(new JSONObject().put("billing_customer_id", "third").put("work_record_ids", new JSONArray().put("foreign-recipient")), works, List.of(bound, later));
        yes(fallback.getJSONArray("positions").getJSONObject(0).getString("price").equals("50"), "Without an own rate the last used rate is offered");
        JSONObject unknown = RULES.prefill(new JSONObject().put("billing_customer_id", "third").put("work_record_ids", new JSONArray().put("work-b")), works, List.of());
        yes(unknown.getJSONArray("positions").getJSONObject(0).getString("price").isEmpty(), "Without any rate the price stays open");
        yes(RULES.prefill(new JSONObject(), works, List.of(bound)).getJSONArray("positions").length() == 0, "Without a selection the draft stays empty");
        JSONObject configured = RULES.prefill(new JSONObject().put("billing_customer_id", "third").put("work_record_ids", new JSONArray().put("foreign-recipient")), works, List.of(bound, later), "48,50");
        yes(configured.getJSONArray("positions").getJSONObject(0).getString("price").equals("48.50"), "The configured hourly rate comes before the last rate of another recipient");
        JSONObject own = RULES.prefill(new JSONObject().put("billing_customer_id", "recipient").put("work_record_ids", ids), works, List.of(bound, later), "48,50");
        yes(own.getJSONArray("positions").getJSONObject(0).getString("price").equals("45"), "A rate agreed with the recipient still wins over the configured rate");
        JSONObject invalid = RULES.prefill(new JSONObject().put("billing_customer_id", "third").put("work_record_ids", new JSONArray().put("work-b")), works, List.of(), "viel");
        yes(invalid.getJSONArray("positions").getJSONObject(0).getString("price").isEmpty(), "An invalid configured rate is not used");
    }
    public static void main(String[] ignored) throws Exception {
        openWorkPrefill();
        decimalsAndDrafts();
        sourceBindingsAndReview();
        closureReplayPaymentsAndCorrections();
        simultaneousReservations();
        restoredReservations();
        groupedOverviewAndLaterWork();
        legacyReferencesAndMissingDraftSources();
        abandonPreparedRecovery();
        System.out.println("PASS " + checks + " synthetic settlement assertions: decimal EUR, drafts, snapshots, legal recipients, unique bindings, route protection and restored-source review");
    }
}
