package at.hausgarten.service

import org.json.JSONObject

/** Historical import evidence counts without inventing an issue timestamp or a new document. */
internal object IssuedInvoiceRules {
    @JvmStatic
    fun isIssued(invoice: JSONObject): Boolean {
        return (!(invoice!!.optString("issued_at"))!!.isEmpty() ||
            (!(invoice!!.optString("sevdesk_id"))!!.isEmpty() &&
                !(invoice!!.optString("status"))!!.equals("Entwurf")))
    }
}
