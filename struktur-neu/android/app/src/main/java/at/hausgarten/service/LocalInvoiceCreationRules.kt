package at.hausgarten.service

import java.io.IOException
import java.time.Instant
import java.util.Arrays
import org.json.*

/**
 * Missing PDF bytes may be created only in their original context or after exact current review.
 */
internal object LocalInvoiceCreationRules {
    @JvmStatic
    fun hasDocumentFields(evidence: JSONObject): Boolean {
        for (field in
            (arrayOf(
                "pdf_sha256",
                "pdf_byte_size",
                "pdf_storage_name",
                "pdf_base64",
                "document_id",
                "document",
            ))!!) {
            if (evidence!!.has(field)) {
                return true
            }
        }
        return false
    }

    private @JvmStatic fun blocked(): IOException {
        return IOException("INVOICE_ORIGINAL_RECONCILIATION_REQUIRED")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun requireAllowed(
        job: JSONObject,
        identity: JSONObject,
        authority: JSONObject,
        restorePending: Boolean,
    ): Unit {
        try {
            var installation = identity!!.getString("installationId")
            var epoch = identity!!.getString("restoreEpoch")
            if (((restorePending || installation!!.isEmpty()) || epoch!!.isEmpty())) {
                throw blocked()
            }
            if (
                (((Arrays.asList("LOCAL_ACTIVE", "RESTORE_REVIEW"))!!.contains(
                    authority!!.optString("mode")
                ) && installation!!.equals(job!!.optString("origin_installation_id"))) &&
                    epoch!!.equals(job!!.optString("origin_restore_epoch")))
            ) {
                return
            }
            var permission = job!!.optJSONObject("creation_authorization")
            if (
                (((((((((((((permission === null) ||
                    !("1")!!.equals(java.lang.String.valueOf(permission!!.opt("formatVersion")))) ||
                    !(authority!!.optString("mode"))!!.equals("LOCAL_ACTIVE")) ||
                    !(authority!!.optString("invoice_authority"))!!.equals("RECONCILED")) ||
                    !installation!!.equals(authority!!.optString("installation_id"))) ||
                    !epoch!!.equals(authority!!.optString("restore_epoch"))) ||
                    !installation!!.equals(permission!!.optString("installation_id"))) ||
                    !epoch!!.equals(permission!!.optString("restore_epoch"))) ||
                    !(job!!.optString("snapshot_sha256"))!!.matches(Regex("[0-9a-f]{64}"))) ||
                    !(job!!.getString("snapshot_sha256"))!!.equals(
                        permission!!.optString("snapshot_sha256")
                    )) ||
                    !(permission!!.optString("ledger_proof_sha256"))!!.matches(
                        Regex("[0-9a-f]{64}")
                    )) ||
                    !(authority!!.optString("invoice_evidence_sha256"))!!.equals(
                        permission!!.getString("ledger_proof_sha256")
                    )) || (permission!!.optString("request_id"))!!.isEmpty())
            ) {
                throw blocked()
            }
            var approved = Instant.parse(permission!!.getString("authorized_at"))
            var minimum =
                authority!!.optString("evidence_not_before", authority!!.optString("blocked_at"))
            if ((!minimum!!.isEmpty() && approved!!.isBefore(Instant.parse(minimum)))) {
                throw blocked()
            }
        } catch (invalid: Exception) {
            throw blocked()
        }
    }
}
