package at.hausgarten.service;
import org.json.*;
import java.util.*;

import static at.hausgarten.service.EntityContract.*;

/** Validated customer/order/invoice-draft use cases, independent of transport and storage. */
final class EntityApplicationService {
    static JSONObject prepare(String name,JSONObject args,JSONObject state,String actionId)throws Exception{
        JSONObject master=SnapshotProjection.effective(state);boolean update=name.startsWith("update_");
        String entity=name.contains("customer")?"customer":name.contains("order")?"order":"invoice";
        String collection=entity.equals("customer")?"customers":entity.equals("order")?"orders":"invoices";
        String[] fields=entity.equals("customer")?CUSTOMER_FIELDS:entity.equals("order")?ORDER_FIELDS:INVOICE_FIELDS;
        JSONObject payload;
        if(update){
            JSONObject capabilities=master.optJSONObject("capabilities");
            if(capabilities==null||!Boolean.TRUE.equals(capabilities.opt("entity_compare_and_save")))throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Bitte Pixel-Datenstand und App-Version prüfen. Der Konfliktschutz ist noch nicht bestätigt.");
            String idKey=entity+"_id",id=args.getString(idKey);JSONObject old=PixelMcpWriteTools.find(master.optJSONArray(collection),id);
            if(old.optString("updated_at").isEmpty())throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Der Änderungsstand fehlt. Bitte zuerst vollständig abgleichen.");
            if(!PixelMcpWriteTools.hash(editable(old,fields)).equals(args.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der Datensatz wurde inzwischen geändert. Bitte erneut öffnen.");
            JSONArray pending=PixelDriveState.ENGINE.equals(master.optString("processing_engine"))?null:state.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.optJSONObject("payload");if(p!=null&&id.equals(p.optString("id")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Für diesen Datensatz ist bereits eine Änderung vorgemerkt. Bitte zuerst abgleichen.");}
            payload=new JSONObject().put("id",id);for(String key:fields)if(old.has(key))payload.put(key,old.get(key));for(String key:new String[]{"status","sevdesk_id","sevdesk_contact_id","issued_at","invoice_number","order_id","created_at","document_type","correction_of","correction_reason","header_text","footer_text"})if(old.has(key))payload.put(key,old.get(key));JSONObject patch=args.getJSONObject("updates");for(Iterator<String> it=patch.keys();it.hasNext();){String key=it.next();payload.put(key,patch.get(key));}
            if(entity.equals("customer")&&patch.has("custom_fields")){
                if(!capabilities.optBoolean("customer_custom_fields_compare_and_save"))throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Der Pixel-Vergleichsschutz für Kunden-Zusatzfelder fehlt; keine Änderung gespeichert.");
                JSONObject merged=customFields(old),changes=patch.getJSONObject("custom_fields");for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();merged.put(key,changes.get(key));}payload.put("custom_fields",merged);
            }
            JSONObject base=new JSONObject().put("id",id);for(String key:fields)if(!key.equals("positions")&&!key.equals("domestic_service_confirmed")&&!key.equals("payment_days")&&old.has(key))base.put(key,old.get(key));for(String key:new String[]{"updated_at","status","invoice_number"})if(old.has(key))base.put(key,old.get(key));
            if(entity.equals("customer")&&!capabilities.optBoolean("customer_custom_fields_compare_and_save")){base.remove("custom_fields");payload.remove("custom_fields");}
            else if(entity.equals("customer"))base.put("custom_fields",customFields(old));
            payload.put("_mcp_base",base);payload.remove("_mcp_create");
            if(entity.equals("invoice"))payload.put("_mcp_invoice_positions",old.optJSONArray("positions")).put("_mcp_invoice_payment_days",old.optInt("payment_days",14));
        }else{
            payload=new JSONObject();for(String key:fields)if(args.has(key))payload.put(key,args.get(key));
            payload.put("id",(entity.equals("customer")?"cus_mobile_mcp_":entity.equals("order")?"ord_mobile_mcp_":"inv_mobile_mcp_")+PixelMcpWriteTools.hash(actionId).substring(0,32));
            JSONArray rows=master.optJSONArray(collection);for(int i=0;rows!=null&&i<rows.length();i++)if(payload.getString("id").equals(rows.getJSONObject(i).optString("id")))throw new PixelMcpWriteTools.Rejected("ID_ALREADY_EXISTS","Dieser Datensatz existiert bereits.");
            payload.put("_mcp_create",true).put("_new_record",true);
        }
        if(entity.equals("customer")){
            BillingRules.customerConfig(payload,master.optJSONArray("customers"));
            if(payload.optString("name").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("MISSING_FIELD","Der Kundenname fehlt.");
            if(!update)try{EntityProtectionRules.validateNewCustomer(payload,master.optJSONArray("customers")==null?new JSONArray():master.getJSONArray("customers"));}catch(IllegalArgumentException duplicate){throw new PixelMcpWriteTools.Rejected("POSSIBLE_DUPLICATE",duplicate.getMessage());}
            JSONObject incoming=update?args.getJSONObject("updates"):args;
            if(incoming.has("email")&&!incoming.optString("email").isEmpty()&&!incoming.optString("email").matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte eine gültige E-Mail-Adresse angeben oder das Feld leer lassen.");
            if(!update&&!payload.has("custom_fields"))payload.put("custom_fields",new JSONObject());
            payload.put("active",payload.optBoolean("active",payload.optInt("active",1)!=0)?1:0);
        }else if(entity.equals("order")){
            BillingRules.order(payload,master);
            PixelMcpWriteTools.find(master.optJSONArray("customers"),payload.optString("customer_id"));
            if(payload.optString("title").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("MISSING_FIELD","Die Auftragsbezeichnung fehlt.");
            if(!update){JSONArray rows=master.optJSONArray("orders");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(payload.optString("customer_id").equals(row.optString("customer_id"))&&payload.optString("title").trim().equalsIgnoreCase(row.optString("title").trim())&&payload.optString("scheduled_date").equals(row.optString("scheduled_date")))throw new PixelMcpWriteTools.Rejected("POSSIBLE_DUPLICATE","Ein ähnlicher Auftrag existiert bereits. Bitte vorhandenen Auftrag suchen.");}}
            if(update){JSONObject previous=PixelMcpWriteTools.find(master.optJSONArray("orders"),payload.getString("id"));EntityProtectionRules.validateOrderChange(payload,previous,master.optJSONArray("worklogs"),master.optJSONArray("invoices"),master.optJSONArray("photos"));}
            if(!payload.optString("scheduled_date").isEmpty())try{java.time.LocalDate.parse(payload.optString("scheduled_date"));}catch(Exception e){throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte ein gültiges Auftragsdatum angeben.");}
            payload.put("customer_visible",payload.optBoolean("customer_visible",payload.optInt("customer_visible",1)!=0)?1:0).put("source","ChatGPT Sprache");
        }else{
            PixelMcpWriteTools.find(master.optJSONArray("customers"),payload.optString("customer_id"));
            if(!"Entwurf".equals(payload.optString("status","Entwurf"))||!payload.optString("sevdesk_id").isEmpty()||!payload.optString("issued_at").isEmpty())throw new PixelMcpWriteTools.Rejected("INVOICE_LOCKED","Nur lokale Rechnungsentwürfe dürfen bearbeitet werden.");
            for(String key:new String[]{"invoice_date","service_start","service_end"})if(!payload.optString(key).isEmpty())try{java.time.LocalDate.parse(payload.optString(key));}catch(Exception e){throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte gültige Rechnungs- und Leistungsdaten angeben.");}
            validatePositions(payload.getJSONArray("positions"));payload.put("status","Entwurf").put("sevdesk_id","").put("source","ChatGPT Sprache");
            JSONArray positions=payload.getJSONArray("positions");Set<String> used=new HashSet<>();for(int i=0;i<positions.length();i++){String worklogId=positions.getJSONObject(i).optString("worklog_id");if(!worklogId.isEmpty()){JSONObject log=PixelMcpWriteTools.find(master.optJSONArray("worklogs"),worklogId);if(!payload.optString("customer_id").equals(BillingRules.recipient(log))||log.optInt("billed",0)!=0||!used.add(worklogId))throw new PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH","Rechnungspositionen müssen offene Arbeiten dieses Kunden verwenden und dürfen keine Arbeit doppelt enthalten.");}}
            if(!update)payload.put("invoice_number","Entwurf-"+payload.getString("id"));
            if(payload.optInt("payment_days",14)>365)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Das Zahlungsziel muss zwischen 0 und 365 Tagen liegen.");
            java.math.BigDecimal total=java.math.BigDecimal.ZERO;for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);java.math.BigDecimal quantity=new java.math.BigDecimal(p.get("quantity").toString()),price=new java.math.BigDecimal(p.get("price").toString()),discount=new java.math.BigDecimal(p.opt("discount")==null?"0":p.get("discount").toString());java.math.BigDecimal amount=quantity.multiply(price).multiply(java.math.BigDecimal.ONE.subtract(discount.divide(new java.math.BigDecimal("100")))).setScale(2,java.math.RoundingMode.HALF_UP);p.put("net",amount.doubleValue()).put("tax",0);total=total.add(amount);}
            payload.put("amount",total.doubleValue()).put("net_total",total.doubleValue()).put("gross_total",total.doubleValue()).put("tax_total",0);
            InvoiceDraftRules.normalize(payload,update?PixelMcpWriteTools.find(master.getJSONArray("invoices"),payload.getString("id")):null,master);
        }
        return new JSONObject().put("entity_type",entity).put("payload",payload);
    }
}
