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
    private static JSONObject master() {
        return json("{customers:[{id:'c1',name:'Synthetic Alpha',street:'Testweg 1',postal_code:'1000',city:'Teststadt',country:'AT',billing_mode:'direct'},"
                + "{id:'c2',name:'Synthetic Beta',street:'Testweg 2',postal_code:'2000',city:'Teststadt',country:'AT',billing_mode:'via_customer',default_billing_customer_id:'c1'}],"
                + "orders:[{id:'o1',customer_id:'c2',billing_customer_id:'c1',title:'Synthetic order',status:'Geplant'}],"
                + "worklogs:[{id:'w1',customer_id:'c2',billing_customer_id:'c1',order_id:'o1',work_date:'2026-10-28',hours:5.25,billed:0},"
                + "{id:'w2',customer_id:'c1',work_date:'2026-10-27',hours:1.5,billed:0}],invoices:[],offers:[],service_catalog:[],"
                + "business_settings:{owner_name:'Synthetic Issuer',business_street:'Testweg 3',business_postal_code:'3000',business_city:'Teststadt',"
                + "invoice_issuer_confirmed:'true',invoice_numbering_confirmed:'true',tax_mode:'standard',business_vat_id:'ATU12345678',invoice_number_format:'own',invoice_prefix:'TEST'}}");
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
        billingRelations();
        offersAndDecisions();
        optionalOfferReview();
        System.out.println("PASS " + checks + " Kotlin business assertions: customer/billing relationships, offer prices, offer decisions and contextual reviews; synthetic JVM only");
    }
}
