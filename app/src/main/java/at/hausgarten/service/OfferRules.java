package at.hausgarten.service;

import org.json.*;
import java.util.*;
import java.math.*;
import java.time.LocalDate;

/** One offer rule path for the existing UI, MCP and Drive snapshot interpreter. */
final class OfferRules {
    static final String[] FIELDS={"customer_id","billing_customer_id","order_id","location","title","document_type","issue_date","valid_until","execution_window","positions","tax_mode","payment_terms","notes","assumptions","exclusions","header_text","footer_text","customer_type","contract_channel","consumer_information","consumer_information_confirmed","scope_confirmed","binding_confirmed","binding_notice","test_record"};
    static final String[] TYPES=OfferContract.types();
    static String label(String type){return OfferContract.type(type).optString("label","Dokumentart noch wählen");}
    static String bindingHash(JSONObject p)throws Exception{return PixelMcpWriteTools.hash(new JSONObject().put("document_type",p.optString("document_type")).put("binding_notice",p.optString("binding_notice")));}
    static boolean editable(JSONObject p){return Arrays.asList("draft","validated").contains(p.optString("status","draft"));}
    static JSONObject contractView(JSONObject p)throws Exception {
        JSONObject type=OfferContract.type(p.optString("document_type"));return new JSONObject().put("supported",type.length()>0).put("binding_mode",p.optString("binding_mode",type.optString("binding_mode","unknown"))).put("binding_status",p.optString("binding_status","pending_review")).put("binding_notice",p.optString("binding_notice",type.optString("notice"))).put("legacy_missing_binding_confirmation",p.optString("document_type").startsWith("estimate_")&&!p.has("binding_confirmed"));
    }

    static JSONObject normalize(JSONObject p,JSONObject old,JSONObject snapshot)throws Exception {
        if(old!=null&&!editable(old))throw new IllegalArgumentException("Finalisierte Angebote bleiben unverändert. Für Änderungen duplizieren.");
        PixelMcpWriteTools.find(snapshot.getJSONArray("customers"),p.optString("customer_id"));
        if(!p.optString("billing_customer_id").isEmpty())PixelMcpWriteTools.find(snapshot.getJSONArray("customers"),p.getString("billing_customer_id"));
        if(!p.optString("order_id").isEmpty()&&!p.getString("customer_id").equals(PixelMcpWriteTools.find(snapshot.getJSONArray("orders"),p.getString("order_id")).optString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag widersprechen sich.");
        String type=p.optString("document_type");if(!type.isEmpty()&&!Arrays.asList(TYPES).contains(type))throw new IllegalArgumentException("Unbekannte Angebotsart.");
        JSONObject definition=OfferContract.type(type);
        p.put("pricing_mode",definition.optString("pricing_mode","unselected")).put("binding_mode",definition.optString("binding_mode","unselected"));
        if(old!=null&&!type.equals(old.optString("document_type"))&&p.optString("binding_notice").equals(old.optString("binding_notice")))p.remove("binding_notice");
        JSONObject config=snapshot.optJSONObject("business_settings");String noticeKey=type.equals("estimate_binding")?"offer_binding_text":type.equals("estimate_nonbinding")?"offer_nonbinding_text":"offer_time_material_text";
        String defaultNotice=config==null?"":config.optString(noticeKey);if(defaultNotice.trim().isEmpty())defaultNotice=definition.optString("notice");if(!p.has("binding_notice"))p.put("binding_notice",type.equals("fixed_price")?"":defaultNotice);
        if(old!=null&&(!type.equals(old.optString("document_type"))||!p.optString("binding_notice").equals(old.optString("binding_notice")))&&!bindingHash(p).equals(p.optString("binding_confirmation_hash")))p.put("binding_confirmed",false);
        if(p.optBoolean("binding_confirmed"))p.put("binding_confirmation_hash",bindingHash(p));else p.remove("binding_confirmation_hash");
        p.put("binding_status",type.startsWith("estimate_")?(p.optBoolean("binding_confirmed")?"confirmed":"pending_review"):"not_applicable");
        if(!p.has("delivery_status"))p.put("delivery_status","not_prepared");
        for(String k:new String[]{"validated_at","validation_context_hash"})p.remove(k);
        TestDataRules.guard(p,snapshot);
        for(String k:new String[]{"title","location","execution_window","payment_terms","notes","assumptions","exclusions","header_text","footer_text","customer_type","contract_channel","consumer_information","binding_notice"}){
            if(!p.has(k))p.put(k,"");if(!(p.get(k) instanceof String)||p.getString(k).length()>10000)throw new IllegalArgumentException("Ungültiger Angebotstext: "+k);
        }
        for(String k:new String[]{"issue_date","valid_until"})if(!p.optString(k).isEmpty())LocalDate.parse(p.getString(k));
        JSONObject settings=snapshot.optJSONObject("business_settings");if(settings==null)settings=new JSONObject();
        if(!p.has("tax_mode")||p.optString("tax_mode").isEmpty())p.put("tax_mode",settings.optString("tax_mode"));
        for(String[] e:new String[][]{{"customer_type","customer_types"},{"contract_channel","contract_channels"},{"tax_mode","tax_modes"}})if(!p.optString(e[0]).isEmpty()&&!Arrays.asList(OfferContract.values(e[1])).contains(p.getString(e[0])))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Unbekannter Domänenwert: "+e[0]);
        for(String key:new String[]{"binding_confirmed","scope_confirmed","consumer_information_confirmed","test_record"})if(p.has(key)&&!(p.get(key) instanceof Boolean))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Bestätigung muss Ja/Nein sein: "+key);
        JSONArray lines=p.optJSONArray("positions");if(lines==null)lines=new JSONArray();if(lines.length()>200)throw new IllegalArgumentException("Höchstens 200 Angebotspositionen.");
        BigDecimal net=BigDecimal.ZERO,tax=BigDecimal.ZERO;boolean complete=true;
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);if(!line.has("price_basis"))line.put("price_basis",line.optString("catalog_entry_id").isEmpty()?"confirmed":"catalog");if(line.has("price_source")&&!Arrays.asList(OfferContract.values("price_sources")).contains(line.optString("price_source")))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Preisquelle exakt user_confirmed verwenden; Katalogpreise über ID und Hash.");if(line.has("price_basis")&&!Arrays.asList(OfferContract.values("price_bases")).contains(line.optString("price_basis")))throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Unbekannte Preisgrundlage.");if(line.optString("title").length()>500||line.optString("description").length()>10000||line.optString("price_note").length()>2000)throw new IllegalArgumentException("Positionstext ist zu lang.");
            if(!line.has("quantity")||line.isNull("quantity")||!line.has("price")||line.isNull("price")){complete=false;line.put("net",JSONObject.NULL).put("tax",JSONObject.NULL);continue;}
            BigDecimal q=decimal(line.get("quantity")),price=decimal(line.get("price")),rate=decimal(!line.has("tax_rate")||line.isNull("tax_rate")?"0":line.get("tax_rate"));
            if(q.signum()<=0||q.compareTo(new BigDecimal("1000000000"))>0||price.signum()<0||price.compareTo(new BigDecimal("1000000000"))>0||rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Mengen, Preise oder Steuer sind ungültig.");
            if(p.optString("tax_mode").equals("small_business")&&rate.signum()!=0)throw new IllegalArgumentException("Kleinunternehmermodus erlaubt keinen Steuerausweis.");
            if(p.optString("tax_mode").equals("standard")&&(!line.has("tax_rate")||line.isNull("tax_rate"))){complete=false;line.put("net",JSONObject.NULL).put("tax",JSONObject.NULL);continue;}
            BigDecimal subtotal=money(q.multiply(price)),t=money(subtotal.multiply(rate).divide(new BigDecimal("100")));line.put("net",subtotal.doubleValue()).put("tax",t.doubleValue());net=net.add(subtotal);tax=tax.add(t);
        }
        if(net.add(tax).compareTo(new BigDecimal("1000000000"))>0)throw new IllegalArgumentException("Angebot überschreitet die sichere Betragsgrenze von einer Milliarde EUR.");
        p.put("positions",lines).put("net_total",complete?net.doubleValue():JSONObject.NULL).put("tax_total",complete?tax.doubleValue():JSONObject.NULL).put("gross_total",complete?net.add(tax).doubleValue():JSONObject.NULL).put("status","draft");
        // New local records carry authoritative integer money; compatibility fields stay intact.
        if(LocalAuthorityRules.ENGINE.equals(snapshot.optString("processing_engine"))){if(complete)p.put("money_minor",LocalAuthorityRules.exactTotals(p));else p.remove("money_minor");}
        if(!p.has("created_at"))p.put("created_at",java.time.Instant.now().toString());
        return p;
    }
    static String positionText(JSONArray positions)throws Exception{
        StringJoiner out=new StringJoiner("\n");for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);out.add(p.getString("title")+" — "+decimal(p.get("quantity")).stripTrailingZeros().toPlainString()+" "+p.optString("unit")+" × "+decimal(p.get("price")).stripTrailingZeros().toPlainString()+" EUR");}return out.toString();
    }
    static BigDecimal decimal(Object n){try{if(n==null||n==JSONObject.NULL)throw new Exception();BigDecimal d=new BigDecimal(n.toString());if(d.precision()>24||Math.abs(d.scale())>10)throw new Exception();return d;}catch(Exception e){throw new IllegalArgumentException("Endliche Mengen und Preise erforderlich.");}}
    static BigDecimal money(BigDecimal d){return d.setScale(2,RoundingMode.HALF_UP);}
    static JSONObject validate(JSONObject p,JSONObject snapshot)throws Exception {
        if(!editable(p)&&p.has("issuer")&&p.has("recipient")){snapshot=new JSONObject(snapshot.toString()).put("business_settings",p.getJSONObject("issuer"));String recipient=p.optString("billing_customer_id");if(recipient.isEmpty())recipient=p.optString("customer_id");JSONObject frozen=new JSONObject(p.getJSONObject("recipient").toString()).put("id",recipient);OwnerJournalRules.replace(snapshot,"customers",recipient,frozen);}
        // Re-run numeric/domain normalization on a copy; reads never change saved business data.
        JSONObject checked=new JSONObject(p.toString());normalize(checked,null,snapshot);

        JSONArray errors=new JSONArray(),warnings=new JSONArray();JSONObject fieldErrors=new JSONObject();JSONObject s=snapshot.optJSONObject("business_settings");if(s==null)s=new JSONObject();
        for(String k:OfferContract.values("issuer_required_fields"))if(s.optString(k).trim().isEmpty())errors.put("Aussteller: "+k);
        if(!Arrays.asList("1","true").contains(s.optString("invoice_issuer_confirmed")))errors.put("Ausstellerdaten bestätigen");
        for(String k:OfferContract.values("required_fields"))if(p.optString(k).trim().isEmpty())errors.put(k+" fehlt");
        if(!Arrays.asList(TYPES).contains(p.optString("document_type")))errors.put("Dokumentart ausdrücklich auswählen");
        try{if(LocalDate.parse(p.getString("valid_until")).isBefore(LocalDate.parse(p.getString("issue_date"))))errors.put("Gültigkeit liegt vor Angebotsdatum");}catch(Exception e){errors.put("Gültige Angebotsdaten erforderlich");}
        String recipient=p.optString("billing_customer_id","");if(recipient.isEmpty())recipient=p.optString("customer_id");
        JSONObject c=OwnerJournalRules.findOrNull(snapshot.optJSONArray("customers"),recipient);
        if(c==null)errors.put("Eindeutiger Kunde fehlt");else{for(String k:new String[]{"name","street","postal_code","city"})if(c.optString(k).trim().isEmpty())errors.put("Empfänger: "+k);String country=c.optString("country").toLowerCase(Locale.GERMAN);if(!Arrays.asList("at","austria","österreich").contains(country))errors.put("Auslandsfall benötigt gesonderte Prüfung");}
        JSONObject customer=OwnerJournalRules.findOrNull(snapshot.optJSONArray("customers"),p.optString("customer_id"));if(customer!=null&&customer.optString("billing_mode").equals("via_customer")&&p.optString("billing_customer_id").isEmpty())errors.put("Abweichenden Rechnungsempfänger ausdrücklich auswählen");
        if(!Arrays.asList(OfferContract.values("tax_modes")).contains(s.optString("tax_mode"))||!s.optString("tax_mode").equals(p.optString("tax_mode")))errors.put("Steuermodus zentral bestätigen");
        if(p.optString("tax_mode").equals("standard")&&s.optString("business_vat_id").isEmpty())errors.put("UID des Ausstellers fehlt");
        if(p.optString("tax_mode").equals("small_business")&&s.optString("tax_exemption_text").isEmpty())errors.put("Steuerbefreiungshinweis fehlt");
        JSONArray lines=p.optJSONArray("positions");if(lines==null||lines.length()==0)errors.put("Mindestens eine Leistung erforderlich");
        for(int i=0;lines!=null&&i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);String at="Position "+(i+1)+": ";for(String key:new String[]{"title","unit"})if(line.optString(key).trim().isEmpty())fieldErrors.put("positions."+i+"."+key,"Pflichtangabe fehlt");for(String key:new String[]{"price","quantity"})if(!line.has(key)||line.isNull(key))fieldErrors.put("positions."+i+"."+key,key.equals("price")?"Preis fehlt – nachfragen":"Menge fehlt");
            if(line.optString("title").trim().isEmpty()||line.optString("unit").trim().isEmpty())errors.put(at+"Leistung und Einheit fehlen");
            if(!line.has("price")||line.isNull("price"))errors.put(at+"Preis fehlt – nachfragen");
            if(!line.has("quantity")||line.isNull("quantity")||line.optDouble("quantity",0)<=0)errors.put(at+"Menge fehlt");
            if(!line.optString("catalog_entry_id").isEmpty()){
                JSONObject template=OwnerJournalRules.findOrNull(snapshot.optJSONArray("service_catalog"),line.getString("catalog_entry_id"));
                if(template==null||!template.optBoolean("price_confirmed")||template.isNull("default_price")||!PixelMcpWriteTools.hash(template).equals(line.optString("catalog_hash"))||!line.has("price")||line.isNull("price")||decimal(template.get("default_price")).compareTo(decimal(line.get("price")))!=0)errors.put(at+"freigegebenen Katalogpreis erneut prüfen");
            }else if(!Arrays.asList(OfferContract.values("price_sources")).contains(line.optString("price_source")))errors.put(at+"Preisquelle fehlt – keinen Preis erfinden");
            String basis=line.optString("price_basis",line.optString("catalog_entry_id").isEmpty()?"confirmed":"catalog");
            if(!Arrays.asList(OfferContract.values("price_bases")).contains(basis)||basis.equals("catalog")!=!line.optString("catalog_entry_id").isEmpty())errors.put(at+"Preisgrundlage widerspricht Preisquelle");
            if(Arrays.asList("calculated","estimated").contains(basis)&&line.optString("price_note").trim().isEmpty())errors.put(at+"Berechnung oder Schätzung erläutern und Preis bestätigen");
            if(basis.equals("calculated")){try{JSONObject calculation=line.getJSONObject("calculation");BigDecimal derived=money(decimal(calculation.get("base_price")).multiply(decimal(calculation.get("factor"))).add(decimal(calculation.opt("surcharge")==null?0:calculation.get("surcharge"))));if(derived.compareTo(decimal(line.get("price")))!=0)errors.put(at+"Berechnung stimmt nicht mit dem Einzelpreis überein");}catch(Exception invalid){errors.put(at+"Vollständige Preisberechnung erforderlich");}}
            if(p.optString("tax_mode").equals("standard")&&(!line.has("tax_rate")||line.isNull("tax_rate")))errors.put(at+"Steuersatz fehlt");
        }
        if(!Arrays.asList(OfferContract.values("customer_types")).contains(p.optString("customer_type")))errors.put("B2B oder Verbraucher bewusst auswählen");
        if(!p.optBoolean("scope_confirmed"))errors.put("Leistungsumfang, Annahmen und Ausschlüsse prüfen");
        if(p.optString("customer_type").equals("consumer")){
            if(!Arrays.asList(OfferContract.values("contract_channels")).contains(p.optString("contract_channel")))errors.put("Vertragsweg für Verbraucher auswählen");
            if(Arrays.asList("distance","off_premise").contains(p.optString("contract_channel"))&&(!p.optBoolean("consumer_information_confirmed")||p.optString("consumer_information").trim().isEmpty()))errors.put("FAGG-Informationen und Rücktrittsbelehrung gesondert prüfen und beifügen");
        }
        if(p.optString("customer_type").equals("consumer")&&Arrays.asList("distance","off_premise").contains(p.optString("contract_channel")))for(String key:new String[]{"business_phone","business_email"})if(s.optString(key).trim().isEmpty())errors.put("FAGG-Kontaktangaben: "+key);
        if(p.optString("document_type").startsWith("estimate_")&&(!p.optBoolean("binding_confirmed")||p.optString("binding_notice").trim().isEmpty())){errors.put("Bindungsart und Hinweistext ausdrücklich bestätigen");fieldErrors.put("binding_confirmed","Verbindlich oder unverbindlich sowie zugehörigen Hinweistext prüfen.");}
        try{TestDataRules.guard(new JSONObject(p.toString()),snapshot);}catch(IllegalArgumentException isolated){errors.put("Testdaten-Isolation: Testkunde und Testempfänger erforderlich");fieldErrors.put("customer_id","Testdaten dürfen nicht mit produktiven Kunden verknüpft sein.");}
        for(String k:OfferContract.values("required_fields"))if(p.optString(k).trim().isEmpty())fieldErrors.put(k,"Pflichtangabe fehlt");
        if(!Arrays.asList(OfferContract.values("customer_types")).contains(p.optString("customer_type")))fieldErrors.put("customer_type","Kundentyp bewusst wählen");if(!p.optBoolean("scope_confirmed"))fieldErrors.put("scope_confirmed","Leistungsumfang bestätigen");
        if(!Arrays.asList(TYPES).contains(p.optString("document_type")))fieldErrors.put("document_type","Dokumentart ausdrücklich wählen");
        if(p.optString("document_type").equals("estimate_nonbinding"))warnings.put("Keine Gewähr für die Richtigkeit des Voranschlags; unvermeidliche beträchtliche Überschreitungen unverzüglich anzeigen (§1170a ABGB). Kein Freibrief für Zusatzkosten.");
        warnings.put("Technische Vollständigkeitsprüfung; keine automatische Prüfung von Gewerbeberechtigung, allen Klauseln oder FAGG-Ausnahmen.");
        return new JSONObject().put("valid",errors.length()==0).put("errors",errors).put("field_errors",fieldErrors).put("warnings",warnings).put("legal_rules_version",OfferContract.data().getString("legal_rules_version")).put("offer_number",p.optString("offer_number")).put("number_assigned_on_finalization",p.optString("status","draft").matches("draft|validated"));
    }
}
