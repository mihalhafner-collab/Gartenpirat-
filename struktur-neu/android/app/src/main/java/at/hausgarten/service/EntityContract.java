package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Existing entity fields, stable record hashes and value validation. */
final class EntityContract {
    static final String[] CUSTOMER_FIELDS={"test_record","name","company","first_name","last_name","street","postal_code","city","country","phone","email","notes","active","garden_area","sevdesk_customer_number","custom_fields","billing_mode","default_billing_customer_id"};
    static final String[] ORDER_FIELDS={"test_record","customer_id","billing_customer_id","title","description","location","scheduled_date","status","customer_visible"};
    static final String[] INVOICE_FIELDS={"test_record","customer_id","invoice_date","service_start","service_end","payment_days","notes","positions","domestic_service_confirmed","order_id","header_text","footer_text","tax_mode","recipient_type","recipient_vat_id"};
    static void validateCustomFields(JSONObject fields)throws Exception{
        if(fields.length()>50)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte höchstens 50 Kunden-Zusatzfelder angeben.");
        for(Iterator<String> it=fields.keys();it.hasNext();){
            String key=it.next();Object value=fields.get(key);
            if(key.trim().isEmpty()||key.length()>80||key.matches(".*[\\p{Cntrl}].*")||Arrays.asList("__proto__","constructor","prototype").contains(key))
                throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte eine eindeutige Zusatzfeld-Bezeichnung mit höchstens 80 Zeichen verwenden.");
            if(value==JSONObject.NULL)continue;
            if(value instanceof String&&((String)value).length()<=2000)continue;
            if(value instanceof Boolean)continue;
            if(value instanceof Number&&Double.isFinite(((Number)value).doubleValue()))continue;
            throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Zusatzfelder unterstützen Text, endliche Zahlen, Ja/Nein oder null.");
        }
    }
    static JSONObject customFields(JSONObject row)throws Exception{
        Object value=row.opt("custom_fields");
        if(value==null||value==JSONObject.NULL||"".equals(value))return new JSONObject();
        if(value instanceof JSONObject)return new JSONObject(value.toString());
        if(value instanceof String)try{return new JSONObject((String)value);}catch(Exception ignored){}
        throw new PixelMcpWriteTools.Rejected("INVALID_STORED_DATA","Bestehende Kunden-Zusatzfelder sind nicht lesbar. Bitte prüfen; keine Änderung gespeichert.");
    }
    static JSONObject editable(JSONObject row,String[] fields)throws Exception{JSONObject out=new JSONObject().put("id",row.optString("id"));if(row.has("updated_at"))out.put("updated_at",row.get("updated_at"));for(String key:fields)if(row.has(key))out.put(key,row.get(key));return out;}
    static void validatePositions(JSONArray positions)throws Exception{
        for(int i=0;i<positions.length();i++){
            JSONObject p=positions.optJSONObject(i);
            if(p==null||p.optString("title").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Jede Rechnungsposition benötigt eine Bezeichnung.");
            for(Iterator<String> keys=p.keys();keys.hasNext();)if(!Arrays.asList("title","description","quantity","price","unit","tax_rate","discount","worklog_id","source_type","source_id","source_quantity_decimal","net","tax","net_total","tax_total","gross_total").contains(keys.next()))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Unbekannte Angabe in Rechnungsposition.");
            if(p.has("source_type")){
                String type=p.getString("source_type");if(!Arrays.asList("manual","worklog","material_movement").contains(type))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Leistungsquelle ist ungültig.");
                if(!type.equals("manual")&&p.optString("source_id").trim().isEmpty())throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Eindeutige Leistungsquelle fehlt.");
                if(p.has("source_quantity_decimal")&&(!p.getString("source_quantity_decimal").matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,12})?")||new java.math.BigDecimal(p.getString("source_quantity_decimal")).signum()<=0))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Quellmenge muss eine positive Dezimalzahl sein.");
                if(type.equals("worklog")){if(!p.optString("worklog_id").isEmpty()&&!p.getString("worklog_id").equals(p.getString("source_id")))throw new PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH","Leistungsquellen passen nicht zusammen.");p.put("worklog_id",p.getString("source_id"));}
            }
            for(String key:new String[]{"quantity","price","discount","tax_rate"})if(p.has(key)&&!(p.opt(key) instanceof Number))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Menge, Preis, Rabatt und Steuer müssen Zahlen sein.");
            double quantity=p.optDouble("quantity",Double.NaN),price=p.optDouble("price",Double.NaN),discount=p.optDouble("discount",0);
            if(!Double.isFinite(quantity)||quantity<=0||!Double.isFinite(price)||price<0||!Double.isFinite(discount)||discount<0||discount>100||!Double.isFinite(p.optDouble("tax_rate",0))||p.optDouble("tax_rate",0)<0||p.optDouble("tax_rate",0)>100)throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bitte Menge, Preis, Rabatt und Steuersatz prüfen.");
        }
    }
}
