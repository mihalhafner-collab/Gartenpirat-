package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** The editor's baseline belongs to the opened form, never to save time. */
final class ManualChangeRules {
    static JSONObject editor(JSONObject incoming, JSONObject current, String kind) throws Exception {
        if (!incoming.has("_edit_base")) return incoming;
        JSONObject base = incoming.getJSONObject("_edit_base");
        if (!incoming.optString("id").equals(base.optString("id")))
            throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT", "Die Formularzuordnung wurde verändert.");
        DriveJournalRules.compare(DriveJournalRules.guard(base, kind), current);
        JSONObject result = DriveJournalRules.clean(current);
        JSONObject changes = incoming.getJSONObject("_edit_changes");
        Set<String> allowed = new HashSet<>(Arrays.asList(kind.equals("customer")?PixelMcpEntityWriteTools.CUSTOMER_FIELDS:kind.equals("order")?PixelMcpEntityWriteTools.ORDER_FIELDS:kind.equals("invoice")?new String[]{"customer_id","order_id","invoice_date","service_start","service_end","payment_days","notes","positions","domestic_service_confirmed","header_text","footer_text","tax_mode","recipient_type","recipient_vat_id"}:PixelMcpWriteTools.BASE_FIELDS));
        allowed.remove("id"); allowed.remove("updated_at");
        for (Iterator<String> keys = changes.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!allowed.contains(key)) throw new IllegalArgumentException("Ungültiges Formularfeld.");
            result.put(key, changes.get(key));
        }
        return result;
    }

    static void prepare(String kind, JSONObject payload, JSONObject previous, String stamp) throws Exception {
        // A new action must not inherit the guard or resolution of an earlier action.
        for (String key : new String[]{"_mcp_base", "_sync_base", "_sync_resolves", "_sync_predecessors", "_sync_field_merge", "_edit_base", "_edit_changes", "_new_record"}) payload.remove(key);
        payload.put("_mcp_create", previous == null);
        if (previous != null) payload.put("_sync_base", DriveJournalRules.guard(previous, kind)).put("_sync_field_merge",true);
        if (previous == null && kind.equals("customer")) {
            if (!payload.has("active")) payload.put("active", 1);
            if (!payload.has("custom_fields")) payload.put("custom_fields", new JSONObject());
        }
        if (previous == null && (kind.equals("order") || kind.equals("photo")) && !payload.has("customer_visible")) payload.put("customer_visible", 1);
        if (previous == null && kind.equals("order") && payload.optString("status").isEmpty()) payload.put("status", "Geplant");
        payload.put("updated_at", stamp);
        if (!payload.has("created_at")) payload.put("created_at", previous != null && previous.has("created_at") ? previous.get("created_at") : stamp);
    }
}
