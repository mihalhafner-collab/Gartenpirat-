package at.hausgarten.service;
import org.json.*;

/** Shared schema primitives, independent of tool registration and runtime. */
final class McpFields {
    static final int CHUNK=48*1024;
    static final String[] BUSINESS_KEYS={"offer_binding_text","offer_nonbinding_text","offer_time_material_text","business_name","owner_name","business_street","business_postal_code","business_city","business_legal_info","business_iban","business_bank","business_bic","business_phone","business_email","invoice_issuer_confirmed","invoice_numbering_confirmed","invoice_number_format","invoice_prefix","invoice_sevdesk_last_number","invoice_sevdesk_checked_at","invoice_numbering_authority","invoice_numbering_device_id","invoice_numbering_last_number","invoice_numbering_checked_at","tax_mode","tax_exemption_text","business_vat_id","default_payment_days","default_payment_terms"};
    static JSONObject text(int n)throws Exception{return new JSONObject().put("type","string").put("maxLength",n);}
    static JSONObject id()throws Exception{return text(500).put("minLength",1);}
    static JSONObject hash()throws Exception{return text(64).put("pattern","[0-9a-f]{64}");}
    static JSONObject number(int min,int max)throws Exception{return new JSONObject().put("type","integer").put("minimum",min).put("maximum",max);}
    static JSONObject bool()throws Exception{return new JSONObject().put("type","boolean");}
    static JSONObject object(JSONObject props,String... required)throws Exception{return new JSONObject().put("type","object").put("properties",props).put("required",new JSONArray(required)).put("additionalProperties",false);}
    static JSONObject enumeration(String...values)throws Exception{return id().put("enum",new JSONArray(values));}
}
