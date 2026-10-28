package at.hausgarten.service;

import org.json.JSONObject;

/** Historical import evidence counts without inventing an issue timestamp or a new document. */
final class IssuedInvoiceRules {
    static boolean isIssued(JSONObject invoice){
        return !invoice.optString("issued_at").isEmpty()
            || !invoice.optString("sevdesk_id").isEmpty()&&!invoice.optString("status").equals("Entwurf");
    }
    private IssuedInvoiceRules(){}
}
