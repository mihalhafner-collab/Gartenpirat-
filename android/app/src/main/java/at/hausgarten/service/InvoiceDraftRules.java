package at.hausgarten.service;
import org.json.*;
import java.util.*;
import java.math.*;

/** Shared validation/calculation for UI, MCP and Drive replay. */
final class InvoiceDraftRules {
    static void normalize(JSONObject p,JSONObject previous,JSONObject master)throws Exception{
        if(previous!=null&&(!previous.optString("issued_at").isEmpty()||!previous.optString("sevdesk_id").isEmpty()||!"Entwurf".equals(previous.optString("status","Entwurf"))))throw new IllegalArgumentException("Ausgestellte und importierte Belege bleiben unverändert.");
        if(!"Entwurf".equals(p.optString("status","Entwurf"))||!p.optString("issued_at").isEmpty()||!p.optString("sevdesk_id").isEmpty())throw new IllegalArgumentException("Nur lokale Entwürfe können gespeichert werden.");
        PixelMcpWriteTools.find(master.getJSONArray("customers"),p.optString("customer_id"));TestDataRules.guard(p,master);
        String order=p.optString("order_id");if(!order.isEmpty()&&!p.optString("customer_id").equals(PixelMcpWriteTools.find(master.getJSONArray("orders"),order).optString("customer_id")))throw new IllegalArgumentException("Rechnungskunde und Auftrag passen nicht zusammen.");
        for(String key:new String[]{"invoice_date","service_start","service_end"})if(!p.optString(key).isEmpty())java.time.LocalDate.parse(p.getString(key));
        if(!p.optString("service_start").isEmpty()&&!p.optString("service_end").isEmpty()&&p.getString("service_start").compareTo(p.getString("service_end"))>0)throw new IllegalArgumentException("Leistungszeitraum ist widersprüchlich.");
        int days=p.optInt("payment_days",14);if(days<0||days>365)throw new IllegalArgumentException("Zahlungsziel 0 bis 365 Tage.");
        JSONArray positions=p.getJSONArray("positions");if(positions.length()<1||positions.length()>200)throw new IllegalArgumentException("1 bis 200 Rechnungspositionen erforderlich.");PixelMcpEntityWriteTools.validatePositions(positions);
        JSONObject settings=master.optJSONObject("business_settings");if(settings==null)settings=new JSONObject();String taxMode=p.optString("tax_mode");
        if(taxMode.isEmpty()||taxMode.equals("unconfigured")){taxMode=settings.optString("tax_mode");if(taxMode.isEmpty())taxMode="unconfigured";}
        if(!Arrays.asList("small_business","standard","unconfigured").contains(taxMode))throw new IllegalArgumentException("Steuermodus ist ungültig.");
        String recipientType=p.optString("recipient_type"),recipientVat=p.optString("recipient_vat_id");
        if(!Arrays.asList("","b2b","consumer").contains(recipientType)||recipientVat.length()>40||(!recipientVat.isEmpty()&&!recipientVat.matches("[A-Za-z]{2}[A-Za-z0-9]{2,38}")))throw new IllegalArgumentException("Empfängertyp oder UID ist ungültig.");
        if(p.has("recipient_type"))p.put("recipient_type",recipientType);if(p.has("recipient_vat_id"))p.put("recipient_vat_id",recipientVat);
        p.put("tax_mode",taxMode);Set<String> used=new LinkedHashSet<>();BigDecimal total=BigDecimal.ZERO,totalTax=BigDecimal.ZERO;
        for(int i=0;i<positions.length();i++){
            JSONObject line=positions.getJSONObject(i);for(String key:new String[]{"description","worklog_id"})if(!line.has(key))line.put(key,"");if(!line.has("unit"))line.put("unit","Stk.");for(String key:new String[]{"tax_rate","discount"})if(!line.has(key))line.put(key,0);String work=line.optString("worklog_id");
            if(!work.isEmpty()){JSONObject log=PixelMcpWriteTools.find(master.getJSONArray("worklogs"),work);if(!used.add(work)||log.optInt("billed")!=0||!p.getString("customer_id").equals(BillingRules.recipient(log)))throw new IllegalArgumentException("Positionen benötigen offene Arbeiten des Rechnungsempfängers ohne Doppelzuordnung.");}
            BigDecimal quantity=new BigDecimal(line.get("quantity").toString()),price=new BigDecimal(line.get("price").toString()),discount=new BigDecimal(line.opt("discount")==null?"0":line.get("discount").toString());
            BigDecimal net=quantity.multiply(price).multiply(BigDecimal.ONE.subtract(discount.divide(new BigDecimal("100")))).setScale(2,RoundingMode.HALF_UP);BigDecimal rate=new BigDecimal(line.get("tax_rate").toString());if(!taxMode.equals("standard")&&rate.signum()!=0)throw new IllegalArgumentException("Steuermodus muss zentral bestätigt sein; Kleinunternehmer weisen keine Steuer aus.");BigDecimal tax=net.multiply(rate).divide(new BigDecimal("100")).setScale(2,RoundingMode.HALF_UP);line.put("net",net.doubleValue()).put("tax",tax.doubleValue());total=total.add(net);totalTax=totalTax.add(tax);
        }
        if("cancellation".equals(p.optString("document_type"))){if(p.optString("correction_of").isEmpty())throw new IllegalArgumentException("Originalbezug des Stornos fehlt.");total=total.negate();totalTax=totalTax.negate();for(int i=0;i<positions.length();i++){JSONObject line=positions.getJSONObject(i);line.put("net",-line.getDouble("net")).put("tax",-line.getDouble("tax"));}}
        if(!p.has("document_type"))p.put("document_type","invoice");for(String key:new String[]{"correction_of","correction_reason","header_text","footer_text"})if(!p.has(key))p.put(key,"");
        p.put("domestic_service_confirmed",p.optBoolean("domestic_service_confirmed",false)).put("payment_days",days).put("worklog_ids",new JSONArray(used)).put("status","Entwurf").put("amount",total.add(totalTax).doubleValue()).put("net_total",total.doubleValue()).put("tax_total",totalTax.doubleValue()).put("gross_total",total.add(totalTax).doubleValue());
    }
}
