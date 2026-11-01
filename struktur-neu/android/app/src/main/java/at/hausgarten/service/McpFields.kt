package at.hausgarten.service

import org.json.JSONArray
import org.json.JSONObject

/** Schema primitives independent from registration and runtime. */
internal class McpFields private constructor() {
    companion object {
        const val CHUNK = 48 * 1024
        @JvmField
        val BUSINESS_KEYS =
            arrayOf(
                "offer_binding_text",
                "offer_nonbinding_text",
                "offer_time_material_text",
                "business_name",
                "owner_name",
                "business_street",
                "business_postal_code",
                "business_city",
                "business_legal_info",
                "business_iban",
                "business_bank",
                "business_bic",
                "business_phone",
                "business_email",
                "invoice_issuer_confirmed",
                "invoice_numbering_confirmed",
                "invoice_number_format",
                "invoice_prefix",
                "invoice_sevdesk_last_number",
                "invoice_sevdesk_checked_at",
                "invoice_numbering_authority",
                "invoice_numbering_device_id",
                "invoice_numbering_last_number",
                "invoice_numbering_checked_at",
                "tax_mode",
                "tax_exemption_text",
                "business_vat_id",
                "default_payment_days",
                "default_payment_terms",
            )

        @JvmStatic
        @Throws(Exception::class)
        fun text(n: Int): JSONObject = JSONObject().put("type", "string").put("maxLength", n)

        @JvmStatic @Throws(Exception::class) fun id(): JSONObject = text(500).put("minLength", 1)

        @JvmStatic
        @Throws(Exception::class)
        fun hash(): JSONObject = text(64).put("pattern", "[0-9a-f]{64}")

        @JvmStatic
        @Throws(Exception::class)
        fun number(min: Int, max: Int): JSONObject =
            JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)

        @JvmStatic
        @Throws(Exception::class)
        fun bool(): JSONObject = JSONObject().put("type", "boolean")

        @JvmStatic
        @Throws(Exception::class)
        fun `object`(props: JSONObject, vararg required: String): JSONObject =
            JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", JSONArray(required))
                .put("additionalProperties", false)

        @JvmStatic
        @Throws(Exception::class)
        fun enumeration(vararg values: String): JSONObject = id().put("enum", JSONArray(values))
    }
}
