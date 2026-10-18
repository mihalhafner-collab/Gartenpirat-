package at.hausgarten.service;

import org.json.*;
import java.util.*;
import java.math.*;
import java.time.LocalDate;

/** One offer rule path for the existing UI, MCP and Drive snapshot interpreter. */
final class OfferRules {
    static final String[] FIELDS={"customer_id","billing_customer_id","order_id","location","title","document_type","issue_date","valid_until","execution_window","positions","tax_mode","payment_terms","notes","assumptions","exclusions","header_text","footer_text","customer_type","contract_channel","consumer_information","consumer_information_confirmed","scope_confirmed"};
    static final String[] TYPES={"fixed_price","time_material","estimate_binding","estimate_nonbinding"};
    static String label(String type){switch(type){case "fixed_price":return "Angebot / Fixpreis";case "time_material":return "Angebot / Regie";case "estimate_binding":return "Verbindlicher Kostenvoranschlag";case "estimate_nonbinding":return "Unverbindlicher Kostenvoranschlag";default:return "Dokumentart noch wählen";}}
    static JSONObject normalize(JSONObject p,JSONObject old,JSONObject snapshot)throws Exception {
        if(old!=null&&!"draft".equals(old.optString("status","draft")))throw new IllegalArgumentException("Finalisierte Angebote bleiben unverändert. Für Änderungen duplizieren.");
        PixelMcpWriteTools.find(snapshot.getJSONArray("customers"),p.optString("customer_id"));
        if(!p.optString("billing_customer_id").isEmpty())PixelMcpWriteTools.find(snapshot.getJSONArray("customers"),p.getString("billing_customer_id"));
        if(!p.optString("order_id").isEmpty()&&!p.getString("customer_id").equals(PixelMcpWriteTools.find(snapshot.getJSONArray("orders"),p.getString("order_id")).optString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag widersprechen sich.");
        String type=p.optString("document_type");if(!type.isEmpty()&&!Arrays.asList(TYPES).contains(type))throw new IllegalArgumentException("Unbekannte Angebotsart.");
        p.put("pricing_mode",type.equals("time_material")?"time_material":type.startsWith("estimate_")?"estimate":"fixed");
        for(String k:new String[]{"title","location","execution_window","payment_terms","notes","assumptions","exclusions","header_text","footer_text","customer_type","contract_channel","consumer_information"}){
            if(!p.has(k))p.put(k,"");if(!(p.get(k) instanceof String)||p.getString(k).length()>10000)throw new IllegalArgumentException("Ungültiger Angebotstext: "+k);
        }
        for(String k:new String[]{"issue_date","valid_until"})if(!p.optString(k).isEmpty())LocalDate.parse(p.getString(k));
        JSONObject settings=snapshot.optJSONObject("business_settings");if(settings==null)settings=new JSONObject();
        if(!p.has("tax_mode")||p.optString("tax_mode").isEmpty())p.put("tax_mode",settings.optString("tax_mode"));
        JSONArray lines=p.optJSONArray("positions");if(lines==null)lines=new JSONArray();if(lines.length()>200)throw new IllegalArgumentException("Höchstens 200 Angebotspositionen.");
        BigDecimal net=BigDecimal.ZERO,tax=BigDecimal.ZERO;boolean complete=true;
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);if(line.optString("title").length()>500||line.optString("description").length()>10000)throw new IllegalArgumentException("Positionstext ist zu lang.");
            if(!line.has("quantity")||line.isNull("quantity")||!line.has("price")||line.isNull("price")){complete=false;line.put("net",JSONObject.NULL).put("tax",JSONObject.NULL);continue;}
            BigDecimal q=decimal(line.get("quantity")),price=decimal(line.get("price")),rate=decimal(!line.has("tax_rate")||line.isNull("tax_rate")?"0":line.get("tax_rate"));
            if(q.signum()<=0||q.compareTo(new BigDecimal("1000000000"))>0||price.signum()<0||price.compareTo(new BigDecimal("1000000000"))>0||rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Mengen, Preise oder Steuer sind ungültig.");
            if(p.optString("tax_mode").equals("small_business")&&rate.signum()!=0)throw new IllegalArgumentException("Kleinunternehmermodus erlaubt keinen Steuerausweis.");
            if(p.optString("tax_mode").equals("standard")&&(!line.has("tax_rate")||line.isNull("tax_rate"))){complete=false;line.put("net",JSONObject.NULL).put("tax",JSONObject.NULL);continue;}
            BigDecimal subtotal=money(q.multiply(price)),t=money(subtotal.multiply(rate).divide(new BigDecimal("100")));line.put("net",subtotal.doubleValue()).put("tax",t.doubleValue());net=net.add(subtotal);tax=tax.add(t);
        }
        if(net.add(tax).compareTo(new BigDecimal("1000000000"))>0)throw new IllegalArgumentException("Angebot überschreitet die sichere Betragsgrenze von einer Milliarde EUR.");
        p.put("positions",lines).put("net_total",complete?net.doubleValue():JSONObject.NULL).put("tax_total",complete?tax.doubleValue():JSONObject.NULL).put("gross_total",complete?net.add(tax).doubleValue():JSONObject.NULL).put("status","draft");
        if(!p.has("created_at"))p.put("created_at",java.time.Instant.now().toString());
        return p;
    }
    static String positionText(JSONArray positions)throws Exception{
        StringJoiner out=new StringJoiner("\n");for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);out.add(p.getString("title")+" — "+decimal(p.get("quantity")).stripTrailingZeros().toPlainString()+" "+p.optString("unit")+" × "+decimal(p.get("price")).stripTrailingZeros().toPlainString()+" EUR");}return out.toString();
    }
    static BigDecimal decimal(Object n){try{if(n==null||n==JSONObject.NULL)throw new Exception();BigDecimal d=new BigDecimal(n.toString());if(d.precision()>24||Math.abs(d.scale())>10)throw new Exception();return d;}catch(Exception e){throw new IllegalArgumentException("Endliche Mengen und Preise erforderlich.");}}
    static BigDecimal money(BigDecimal d){return d.setScale(2,RoundingMode.HALF_UP);}
    static JSONObject validate(JSONObject p,JSONObject snapshot)throws Exception {
        JSONArray errors=new JSONArray(),warnings=new JSONArray();JSONObject s=snapshot.optJSONObject("business_settings");if(s==null)s=new JSONObject();
        for(String k:new String[]{"owner_name","business_street","business_postal_code","business_city"})if(s.optString(k).trim().isEmpty())errors.put("Aussteller: "+k);
        if(!Arrays.asList("1","true").contains(s.optString("invoice_issuer_confirmed")))errors.put("Ausstellerdaten bestätigen");
        for(String k:new String[]{"title","issue_date","valid_until","execution_window","payment_terms"})if(p.optString(k).trim().isEmpty())errors.put(k+" fehlt");
        if(!Arrays.asList(TYPES).contains(p.optString("document_type")))errors.put("Dokumentart ausdrücklich auswählen");
        try{if(LocalDate.parse(p.getString("valid_until")).isBefore(LocalDate.parse(p.getString("issue_date"))))errors.put("Gültigkeit liegt vor Angebotsdatum");}catch(Exception e){errors.put("Gültige Angebotsdaten erforderlich");}
        String recipient=p.optString("billing_customer_id","");if(recipient.isEmpty())recipient=p.optString("customer_id");
        JSONObject c=OwnerJournalRules.findOrNull(snapshot.optJSONArray("customers"),recipient);
        if(c==null)errors.put("Eindeutiger Kunde fehlt");else{for(String k:new String[]{"name","street","postal_code","city"})if(c.optString(k).trim().isEmpty())errors.put("Empfänger: "+k);String country=c.optString("country").toLowerCase(Locale.GERMAN);if(!Arrays.asList("at","austria","österreich").contains(country))errors.put("Auslandsfall benötigt gesonderte Prüfung");}
        JSONObject customer=OwnerJournalRules.findOrNull(snapshot.optJSONArray("customers"),p.optString("customer_id"));if(customer!=null&&customer.optString("billing_mode").equals("via_customer")&&p.optString("billing_customer_id").isEmpty())errors.put("Abweichenden Rechnungsempfänger ausdrücklich auswählen");
        if(!Arrays.asList("small_business","standard").contains(s.optString("tax_mode"))||!s.optString("tax_mode").equals(p.optString("tax_mode")))errors.put("Steuermodus zentral bestätigen");
        if(p.optString("tax_mode").equals("standard")&&s.optString("business_vat_id").isEmpty())errors.put("UID des Ausstellers fehlt");
        if(p.optString("tax_mode").equals("small_business")&&s.optString("tax_exemption_text").isEmpty())errors.put("Steuerbefreiungshinweis fehlt");
        JSONArray lines=p.optJSONArray("positions");if(lines==null||lines.length()==0)errors.put("Mindestens eine Leistung erforderlich");
        for(int i=0;lines!=null&&i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);String at="Position "+(i+1)+": ";
            if(line.optString("title").trim().isEmpty()||line.optString("unit").trim().isEmpty())errors.put(at+"Leistung und Einheit fehlen");
            if(!line.has("price")||line.isNull("price"))errors.put(at+"Preis fehlt – nachfragen");
            if(!line.has("quantity")||line.isNull("quantity")||line.optDouble("quantity",0)<=0)errors.put(at+"Menge fehlt");
            if(!line.optString("catalog_entry_id").isEmpty()){
                JSONObject template=OwnerJournalRules.findOrNull(snapshot.optJSONArray("service_catalog"),line.getString("catalog_entry_id"));
                if(template==null||!template.optBoolean("price_confirmed")||template.isNull("default_price")||!PixelMcpWriteTools.hash(template).equals(line.optString("catalog_hash"))||!line.has("price")||line.isNull("price")||decimal(template.get("default_price")).compareTo(decimal(line.get("price")))!=0)errors.put(at+"freigegebenen Katalogpreis erneut prüfen");
            }else if(!"user_confirmed".equals(line.optString("price_source")))errors.put(at+"Preisquelle fehlt – keinen Preis erfinden");
            if(p.optString("tax_mode").equals("standard")&&(!line.has("tax_rate")||line.isNull("tax_rate")))errors.put(at+"Steuersatz fehlt");
        }
        if(!Arrays.asList("b2b","consumer").contains(p.optString("customer_type")))errors.put("B2B oder Verbraucher bewusst auswählen");
        if(!p.optBoolean("scope_confirmed"))errors.put("Leistungsumfang, Annahmen und Ausschlüsse prüfen");
        if(p.optString("customer_type").equals("consumer")){
            if(!Arrays.asList("on_premise","distance","off_premise").contains(p.optString("contract_channel")))errors.put("Vertragsweg für Verbraucher auswählen");
            if(Arrays.asList("distance","off_premise").contains(p.optString("contract_channel"))&&(!p.optBoolean("consumer_information_confirmed")||p.optString("consumer_information").trim().isEmpty()))errors.put("FAGG-Informationen und Rücktrittsbelehrung gesondert prüfen und beifügen");
        }
        if(p.optString("customer_type").equals("consumer")&&Arrays.asList("distance","off_premise").contains(p.optString("contract_channel")))for(String key:new String[]{"business_phone","business_email"})if(s.optString(key).trim().isEmpty())errors.put("FAGG-Kontaktangaben: "+key);
        if(p.optString("document_type").equals("estimate_nonbinding"))warnings.put("Keine Gewähr für die Richtigkeit des Voranschlags; unvermeidliche beträchtliche Überschreitungen unverzüglich anzeigen (§1170a ABGB). Kein Freibrief für Zusatzkosten.");
        warnings.put("Technische Vollständigkeitsprüfung; keine automatische Prüfung von Gewerbeberechtigung, allen Klauseln oder FAGG-Ausnahmen.");
        return new JSONObject().put("valid",errors.length()==0).put("errors",errors).put("warnings",warnings).put("legal_rules_version","AT-2026-10-18").put("offer_number",p.optString("offer_number")).put("number_assigned_on_finalization",p.optString("status","draft").equals("draft"));
    }
}
