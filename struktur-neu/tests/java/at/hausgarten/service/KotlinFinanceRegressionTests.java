package at.hausgarten.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

/** Current production Kotlin rules; all parties, dates, prices and identifiers below are synthetic. */
public final class KotlinFinanceRegressionTests {
    private static final String STAMP = "2026-10-29T10:00:00Z";
    private static int checks;
    private interface Work { void run() throws Exception; }

    private static JSONObject json(String value) { return new JSONObject(value); }
    private static JSONObject copy(JSONObject value) { return new JSONObject(value.toString()); }
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
    private static boolean blocker(JSONObject review, String code) {
        JSONArray blockers = review.getJSONArray("blockers");
        for (int i = 0; i < blockers.length(); i++) if (code.equals(blockers.getJSONObject(i).getString("code"))) return true;
        return false;
    }

    private static JSONObject master() {
        return json("{customers:[{id:'c1',name:'Synthetic Alpha',street:'Testweg 1',postal_code:'1000',city:'Teststadt',country:'AT',billing_mode:'direct'},"
                + "{id:'c2',name:'Synthetic Beta',street:'Testweg 2',postal_code:'2000',city:'Teststadt',country:'AT',billing_mode:'via_customer',default_billing_customer_id:'c1'}],"
                + "orders:[{id:'o1',customer_id:'c2',billing_customer_id:'c1',title:'Synthetic order',status:'Geplant'}],"
                + "worklogs:[{id:'w1',customer_id:'c2',billing_customer_id:'c1',order_id:'o1',work_date:'2026-10-28',hours:5.25,billed:0},"
                + "{id:'w2',customer_id:'c1',work_date:'2026-10-27',hours:1.5,billed:0}],invoices:[],offers:[],service_catalog:[],"
                + "business_settings:{owner_name:'Synthetic Issuer',business_street:'Testweg 3',business_postal_code:'3000',business_city:'Teststadt',"
                + "invoice_issuer_confirmed:'true',invoice_numbering_confirmed:'true',tax_mode:'standard',business_vat_id:'ATU12345678',invoice_number_format:'own',invoice_prefix:'TEST'}}");
    }
    private static JSONObject draft() {
        return json("{id:'i1',customer_id:'c1',order_id:'o1',invoice_number:'Entwurf-i1',invoice_date:'2026-10-29',service_start:'2026-10-27',service_end:'2026-10-28',"
                + "payment_days:14,tax_mode:'standard',domestic_service_confirmed:true,status:'Entwurf',positions:[{title:'Synthetic work',description:'Synthetic work',"
                + "quantity:2.1,price:31.55,discount:5,tax_rate:20,unit:'h',worklog_id:'w1'}]}");
    }
    private static JSONObject invoice() throws Exception {
        JSONObject value = draft(); InvoiceDraftRules.normalize(value, null, master()); return value;
    }
    private static JSONObject offer() throws Exception {
        return OfferRules.normalize(json("{id:'off1',customer_id:'c1',title:'Synthetic offer',document_type:'fixed_price',issue_date:'2026-10-29',"
                + "valid_until:'2026-11-15',execution_window:'nach Vereinbarung',payment_terms:'14 Tage',tax_mode:'standard',customer_type:'b2b',scope_confirmed:true,"
                + "created_at:'2026-10-29T10:00:00Z',positions:[{title:'Synthetic work',description:'Test',quantity:2.1,price:31.55,tax_rate:20,unit:'h',price_source:'user_confirmed'}]}"), null, master());
    }
    private static JSONObject dependencies() {
        return json("{formatVersion:1,authority:{mode:'LOCAL_ACTIVE',invoice_authority:'RECONCILED',migration_epoch:'epoch',installation_id:'installation',restore_epoch:'restore'},"
                + "worklog_allocations_by_invoice:{},material_allocations_by_invoice:{},material_movements:{},material_reversals:{}}");
    }
    private static JSONObject issuePayload(JSONObject state, JSONObject invoice) throws Exception {
        return new JSONObject().put("id", "i1").put("confirmed", true)
                .put("_expected_invoice", DriveJournalRules.guard(invoice, "invoice"))
                .put("_expected_settings_hash", PixelMcpWriteTools.hash(state.getJSONObject("business_settings")))
                .put("_expected_recipient_hash", OwnerJournalRules.version(state.getJSONArray("customers").getJSONObject(0)))
                .put("_expected_worklogs", new JSONObject().put("w1", DriveJournalRules.guard(state.getJSONArray("worklogs").getJSONObject(0), "worklog")));
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
    }

    private static void draftMoneyAndGuards() throws Exception {
        JSONObject value = invoice();
        // These amounts were observed in the approved pre-port Java fixture. They
        // independently pin rounding of 2.1 x EUR31.55, less 5%, plus 20% tax.
        money(value.get("net_total"), "62.94", "Net rounds once per position");
        money(value.get("tax_total"), "12.59", "Tax rounds from rounded line net");
        money(value.get("gross_total"), "75.53", "Gross preserves the approved Java amount");
        money(value.getJSONArray("positions").getJSONObject(0).get("quantity"), "2.1", "No integral quantity truncation");
        yes("w1".equals(value.getJSONArray("worklog_ids").getString(0)), "Original work source remains linked");
        String once = PixelMcpWriteTools.hash(value);
        InvoiceDraftRules.normalize(value, null, master());
        yes(once.equals(PixelMcpWriteTools.hash(value)), "Repeated draft calculation is stable");
        invalid("1 bis 200 Rechnungspositionen erforderlich.", () -> InvoiceDraftRules.normalize(draft().put("positions", new JSONArray()), null, master()));
        invalid("Ausgestellte und importierte Belege bleiben unverändert.", () -> InvoiceDraftRules.normalize(draft(), draft().put("issued_at", STAMP), master()));
        invalid("Steuermodus muss zentral bestätigt sein; Kleinunternehmer weisen keine Steuer aus.", () -> InvoiceDraftRules.normalize(draft().put("tax_mode", "small_business"), null, master()));
        invalid("Rechnungsempfänger und ausdrücklich zugeordneter Abrechnungskunde des Auftrags passen nicht zusammen.", () -> InvoiceDraftRules.normalize(draft().put("customer_id", "c2"), null, master()));
        invalid("Leistungszeitraum ist widersprüchlich.", () -> InvoiceDraftRules.normalize(draft().put("service_start", "2026-10-30"), null, master()));
        JSONObject exact = LocalAuthorityRules.exactTotals(invoice());
        yes(exact.getLong("gross_minor") == 7553 && exact.getLong("net_minor") == 6294 && exact.getLong("tax_minor") == 1259,
                "Frozen invoice uses exact cents matching its decimal totals");
        yes(LocalAuthorityRules.minor("75.53") == 7553, "Decimal amount converts to exact cents");
        try { LocalAuthorityRules.minor("0.001"); throw new AssertionError("Fractional cent accepted"); }
        catch (ArithmeticException expected) { yes(true, "Fractional cent is rejected without rounding"); }
    }

    private static void issueAndCorrection() throws Exception {
        JSONObject state = master(), original = invoice(); state.getJSONArray("invoices").put(original);
        JSONObject payload = issuePayload(state, original);
        JSONObject prepared = InvoiceIssueRules.prepare(state, new JSONObject().put("payload", payload), LocalDate.parse("2026-10-29"));
        yes("2026-11-12".equals(prepared.getString("due_date")), "Payment deadline follows the reviewed invoice date");
        yes("Synthetic Issuer".equals(prepared.getJSONObject("issuer").getString("owner_name"))
                        && "Synthetic Alpha".equals(prepared.getJSONObject("recipient").getString("name")),
                "Preparation freezes separate issuer and recipient");
        yes(!IssuedInvoiceRules.isIssued(prepared), "Preparation assigns no final issue status");
        JSONObject changed = copy(state); changed.getJSONArray("customers").getJSONObject(0).put("street", "Synthetic changed address");
        rejected("WRITE_CONFLICT", () -> InvoiceIssueRules.prepare(changed, new JSONObject().put("payload", payload), LocalDate.parse("2026-10-29")));
        JSONObject committed = copy(prepared).put("pdf_sha256", "a".repeat(64));
        InvoiceIssueRules.commit(state, committed, json("{invoice_id:'i1',invoice_number:'TEST-2026-0001',action_sha256:'" + "b".repeat(64) + "'}"), STAMP);
        JSONObject issued = state.getJSONArray("invoices").getJSONObject(0);
        yes(IssuedInvoiceRules.isIssued(issued) && "TEST-2026-0001".equals(issued.getString("invoice_number"))
                        && "a".repeat(64).equals(issued.getString("pdf_sha256")), "Committed number and original hash remain attached to the issued invoice");
        JSONObject correction = new JSONObject().put("id", "i1").put("correction_type", "cancellation").put("reason", "Synthetic correction")
                .put("_expected_invoice", DriveJournalRules.guard(issued, "invoice"));
        String frozenOriginal = PixelMcpWriteTools.hash(issued);
        InvoiceIssueRules.correction(state, new JSONObject().put("payload", correction).put("client_id", "synthetic-correction-1").put("created_at", STAMP));
        JSONObject cancellation = state.getJSONArray("invoices").getJSONObject(1);
        yes(frozenOriginal.equals(PixelMcpWriteTools.hash(state.getJSONArray("invoices").getJSONObject(0)))
                        && !IssuedInvoiceRules.isIssued(cancellation) && "i1".equals(cancellation.getString("correction_of")),
                "Cancellation is a separate unissued draft; original invoice is unchanged");
        money(cancellation.get("gross_total"), "-75.53", "Cancellation negates the original exact amount");
    }

    private static void invoiceReviewAndPartialSources() throws Exception {
        JSONObject state = master(); state.getJSONArray("invoices").put(invoice());
        JSONObject review = InvoiceIssueReviewRules.capture(state, "i1", dependencies()).getJSONObject("issue_review");
        yes(review.getBoolean("ready") && review.getJSONArray("blockers").isEmpty(), "Complete synthetic issue review is ready");
        // Frozen canonical context hash was read from the approved Java baseline;
        // it binds this complete fixture, including the original source quantity.
        yes("48bf9e2fb6d1cf0f29d9466d2a92ec8c2658026615e6f24b3f2f0ba453a8a030".equals(review.getString("context_hash")),
                "Kotlin issue snapshot retains the approved complete review hash");
        JSONObject changed = copy(state); changed.getJSONObject("business_settings").put("business_city", "Synthetic other city");
        JSONObject next = InvoiceIssueReviewRules.capture(changed, "i1", dependencies()).getJSONObject("issue_review");
        yes(!review.getString("context_hash").equals(next.getString("context_hash")) && review.getString("record_hash").equals(next.getString("record_hash")),
                "Issuer change invalidates the context while preserving the draft record hash");
        rejected("WRITE_CONFLICT", () -> InvoiceIssueReviewRules.requireCurrent(next, review.getString("context_hash")));
        JSONObject missing = copy(state); missing.put("worklogs", new JSONArray());
        JSONObject blocked = InvoiceIssueReviewRules.capture(missing, "i1", dependencies()).getJSONObject("issue_review");
        yes(!blocked.getBoolean("ready") && blocker(blocked, "INVOICE_WORKLOG_MISSING"), "Missing linked work blocks issue");
        yes(blocker(InvoiceIssueReviewRules.capture(master(), "missing", dependencies()).getJSONObject("issue_review"), "INVOICE_NOT_FOUND"), "Absent invoice is explicit in the review");
        JSONObject partial = invoice().put("issued_at", STAMP).put("local_issue_request", "synthetic-issue")
                .put("local_source_allocations", json("{w1:'2.1'}")).put("numbering_receipt", new JSONObject().put("authority", LocalAuthorityRules.ENGINE));
        state.getJSONArray("invoices").put(0, partial);
        JSONObject sources = json("{formatVersion:1,by_invoice:{i1:{w1:'2.1'}}}");
        LocalBillingRules.validate(state, sources); state.put("local_billing_sources", sources);
        JSONObject remaining = LocalBillingRules.state(state.getJSONArray("worklogs").getJSONObject(0), state.getJSONArray("invoices"), state);
        money(remaining.get("remaining_decimal"), "3.15", "Partial issue leaves exactly 3.15 hours from 5.25 minus 2.1");
        yes("partially_invoiced".equals(remaining.getString("evidence")), "Partial source keeps explicit ledger evidence");
        rejected("LOCAL_BILLING_SOURCE_REVIEW_REQUIRED", () -> LocalBillingRules.validate(master(), json("{formatVersion:2,by_invoice:{}}")));
    }

    private static void paymentsAndDisplayProjection() throws Exception {
        JSONArray partial = new JSONArray().put(json("{payment_id:'synthetic-p1',allocated_minor:5000}"))
                .put(json("{payment_id:'synthetic-p2',allocated_minor:1000}"));
        JSONObject balance = LocalAuthorityRules.paymentBalance(7553, partial);
        yes(balance.getLong("remaining_minor") == 1553 && balance.getLong("credit_minor") == 0 && !balance.getBoolean("paid"), "Part payments preserve a 1553-cent rest");
        JSONObject overpaid = LocalAuthorityRules.paymentBalance(7553, new JSONArray().put(json("{payment_id:'synthetic-p3',allocated_minor:10000}")));
        yes(overpaid.getLong("remaining_minor") == 0 && overpaid.getLong("credit_minor") == 2447, "Overpayment stays a separate 2447-cent credit");
        invalid("PAYMENT_DUPLICATE_ID", () -> LocalAuthorityRules.paymentBalance(7553, new JSONArray().put(partial.getJSONObject(0)).put(partial.getJSONObject(0))));
        JSONObject original = invoice();
        yes(LocalFinancialValidation.sameFrozenInvoice(original, copy(original).put("pdf_state", "READY").put("updated_at", STAMP)), "Document completion does not rewrite frozen business content");
        JSONObject modified = copy(original); modified.getJSONArray("positions").getJSONObject(0).put("price", 1);
        yes(!LocalFinancialValidation.sameFrozenInvoice(original, modified), "Changed position price is not the same frozen invoice");
        JSONArray invoices = new JSONArray().put(json("{id:'i1',invoice_number:'TEST-2026-0001'}"));
        JSONArray entries = new JSONArray().put(json("{payment_id:'synthetic-p1',amount_minor:5000,allocations:[{invoice_id:'i1',amount_minor:4000},{invoice_id:'missing',amount_minor:1000}]}"))
                .put(json("{payment_id:'synthetic-p2',amount_minor:1000,allocations:[{invoice_id:'i1',amount_minor:1000}]}"));
        String rawHash = PixelMcpWriteTools.hash(entries), invoiceHash = PixelMcpWriteTools.hash(invoices);
        JSONArray display = LocalBillingRules.paymentDisplay(entries, invoices);
        yes("TEST-2026-0001".equals(display.getJSONObject(0).getJSONArray("allocations").getJSONObject(0).getString("invoice_number")), "Payment allocation gets its human invoice number");
        yes("".equals(display.getJSONObject(0).getJSONArray("allocations").getJSONObject(1).getString("invoice_number")), "Missing invoice label is not invented");
        yes(rawHash.equals(PixelMcpWriteTools.hash(entries)) && invoiceHash.equals(PixelMcpWriteTools.hash(invoices))
                        && !entries.getJSONObject(0).getJSONArray("allocations").getJSONObject(0).has("invoice_number"), "Display projection leaves stored ledger and invoice hashes unchanged");
        JSONArray page = LocalBillingRules.paymentDisplay(new JSONArray().put(entries.getJSONObject(1)), invoices);
        yes(PixelMcpWriteTools.hash(page.getJSONObject(0)).equals(PixelMcpWriteTools.hash(display.getJSONObject(1)))
                        && rawHash.equals(PixelMcpWriteTools.hash(entries)), "Changing the display page preserves labels and the authoritative whole-ledger hash");
        display.getJSONObject(0).getJSONArray("allocations").getJSONObject(0).put("amount_minor", 1);
        yes(entries.getJSONObject(0).getJSONArray("allocations").getJSONObject(0).getLong("amount_minor") == 4000,
                "Display allocation amounts are deep copies, never mutable ledger aliases");
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
        billingRelations(); draftMoneyAndGuards(); issueAndCorrection();
        invoiceReviewAndPartialSources(); paymentsAndDisplayProjection();
        offersAndDecisions(); optionalOfferReview();
        System.out.println("PASS " + checks + " Kotlin finance assertions: invoice rounding/issue/correction, complete reviews, exact partial sources, payments/credits, immutable ledger labels, offer decisions; synthetic JVM only");
    }
}
