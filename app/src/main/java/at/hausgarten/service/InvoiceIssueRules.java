package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

/** Financial rules shared by MCP request validation and the Pixel Drive processor. */
final class InvoiceIssueRules {
    static JSONObject prepare(JSONObject s,JSONObject a,LocalDate today)throws Exception {
        JSONObject p=a.getJSONObject("payload"),old=PixelMcpWriteTools.find(s.getJSONArray("invoices"),p.getString("id"));
        TestDataRules.noDelivery(old,s);
        if(!p.optBoolean("confirmed"))throw new IllegalArgumentException("Rechnungsabschluss ausdrücklich bestätigen.");
        DriveJournalRules.compare(p.getJSONObject("_expected_invoice"),old);
        JSONObject settings=s.getJSONObject("business_settings"),customer=PixelMcpWriteTools.find(s.getJSONArray("customers"),old.getString("customer_id"));
        if(!PixelMcpWriteTools.hash(settings).equals(p.optString("_expected_settings_hash"))||!OwnerJournalRules.version(customer).equals(p.optString("_expected_recipient_hash")))
            throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Aussteller, Nummernkonfiguration oder Empfänger geändert. Abschluss erneut prüfen.");
        JSONObject next=DriveJournalRules.clean(old);InvoiceDraftRules.normalize(next,old,s);
        for(String key:new String[]{"owner_name","business_street","business_postal_code","business_city"})required(settings,key);
        for(String key:new String[]{"name","street","postal_code","city"})required(customer,key);
        for(String key:new String[]{"invoice_issuer_confirmed","invoice_numbering_confirmed"})if(!Arrays.asList("1","true").contains(settings.optString(key)))throw new IllegalArgumentException("Aussteller und Nummernkreis müssen bestätigt sein.");
        if(!Arrays.asList("at","austria","österreich").contains(customer.optString("country").toLowerCase(Locale.ROOT))||!next.optBoolean("domestic_service_confirmed"))throw new IllegalArgumentException("Nur bestätigte gewöhnliche Inlandsleistungen sind unterstützt.");
        LocalDate date=LocalDate.parse(next.getString("invoice_date")),start=LocalDate.parse(next.getString("service_start")),end=LocalDate.parse(next.getString("service_end"));
        if(!date.equals(today)||end.isAfter(today)||end.isBefore(start))throw new IllegalArgumentException("Rechnungsdatum und Leistungszeitraum erneut prüfen.");
        String tax=settings.optString("tax_mode");if(!Arrays.asList("small_business","standard").contains(tax)||!tax.equals(next.optString("tax_mode")))throw new IllegalArgumentException("Bestätigter Steuermodus fehlt.");
        if(tax.equals("small_business"))required(settings,"tax_exemption_text");else required(settings,"business_vat_id");
        if(Math.abs(next.getDouble("gross_total"))<=0)throw new IllegalArgumentException("Rechnungsbetrag muss ungleich null sein.");
        if(tax.equals("standard")&&Math.abs(next.getDouble("gross_total"))>10000){if(!Arrays.asList("b2b","consumer").contains(next.optString("recipient_type")))throw new IllegalArgumentException("Empfängertyp bestätigen.");if(next.optString("recipient_type").equals("b2b"))required(next,"recipient_vat_id");}
        JSONObject expected=p.getJSONObject("_expected_worklogs");JSONArray links=next.getJSONArray("worklog_ids");if(expected.length()!=links.length())throw new IllegalArgumentException("Leistungs-Vergleichsstände unvollständig.");
        for(int i=0;i<links.length();i++){String id=links.getString(i);DriveJournalRules.compare(expected.getJSONObject(id),PixelMcpWriteTools.find(s.getJSONArray("worklogs"),id));}
        for(int i=0;i<s.getJSONArray("invoices").length();i++){JSONObject other=s.getJSONArray("invoices").getJSONObject(i);if(other.optString("issued_at").isEmpty())continue;JSONArray used=other.optJSONArray("worklog_ids");for(int j=0;used!=null&&j<used.length();j++)if(expected.has(used.getString(j)))throw new IllegalArgumentException("Arbeit bereits in ausgestellter Rechnung enthalten.");}
        if(Arrays.asList("replacement","cancellation").contains(next.optString("document_type"))){
            JSONObject original=PixelMcpWriteTools.find(s.getJSONArray("invoices"),next.getString("correction_of"));if(original.optString("issued_at").isEmpty())throw new IllegalArgumentException("Ausgestellter Originalbeleg fehlt.");
            if(next.optString("document_type").equals("cancellation")){
                if(original.optString("status").equals("Storniert"))throw new IllegalArgumentException("Original bereits storniert.");
                JSONArray frozen=new JSONArray(original.getJSONArray("positions").toString()),actual=new JSONArray(next.getJSONArray("positions").toString());
                for(int i=0;i<frozen.length();i++)frozen.getJSONObject(i).put("worklog_id","");
                for(int i=0;i<actual.length();i++)for(String k:new String[]{"net","tax"})actual.getJSONObject(i).put(k,Math.abs(actual.getJSONObject(i).getDouble(k)));
                if(!PixelMcpWriteTools.hash(frozen).equals(PixelMcpWriteTools.hash(actual)))throw new IllegalArgumentException("Storno muss den unveränderten Originalpositionen entsprechen.");
            }
        }
        return next.put("issuer",new JSONObject(settings.toString())).put("recipient",new JSONObject(customer.toString()))
            .put("due_date",date.plusDays(next.getInt("payment_days")).toString());
    }
    static void required(JSONObject o,String key){if(o.optString(key).trim().isEmpty())throw new IllegalArgumentException("Pflichtangabe fehlt: "+key);}
    static void commit(JSONObject s,JSONObject prepared,JSONObject reservation,String stamp)throws Exception {
        String number=reservation.getString("invoice_number");if(!prepared.getString("id").equals(reservation.optString("invoice_id"))||!reservation.optString("action_sha256").matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Nummernbeleg gehört nicht zur Rechnung.");
        for(int i=0;i<s.getJSONArray("invoices").length();i++){JSONObject other=s.getJSONArray("invoices").getJSONObject(i);if(!other.getString("id").equals(prepared.getString("id"))&&number.equals(other.optString("invoice_number")))throw new PixelMcpWriteTools.Rejected("NUMBER_COLLISION","Rechnungsnummer bereits belegt; keine Überschreibung.");}
        if(prepared.optString("pdf_sha256").isEmpty())throw new IllegalArgumentException("Original-PDF fehlt.");
        JSONObject issued=new JSONObject(prepared.toString()).put("invoice_number",number).put("issued_at",stamp).put("updated_at",stamp).put("status","Gestellt").put("numbering_receipt",reservation);
        OwnerJournalRules.replace(s,"invoices",issued.getString("id"),issued);
        JSONArray links=issued.getJSONArray("worklog_ids");for(int i=0;i<links.length();i++){JSONObject work=new JSONObject(PixelMcpWriteTools.find(s.getJSONArray("worklogs"),links.getString(i)).toString());work.put("billed",1).put("updated_at",stamp);OwnerJournalRules.replace(s,"worklogs",work.getString("id"),work);}
        if(issued.optString("document_type").equals("cancellation")){JSONObject original=new JSONObject(PixelMcpWriteTools.find(s.getJSONArray("invoices"),issued.getString("correction_of")).toString());original.put("status","Storniert").put("updated_at",stamp);OwnerJournalRules.replace(s,"invoices",original.getString("id"),original);}
    }
    static void correction(JSONObject s,JSONObject a)throws Exception {
        JSONObject p=a.getJSONObject("payload"),original=PixelMcpWriteTools.find(s.getJSONArray("invoices"),p.getString("id"));DriveJournalRules.compare(p.getJSONObject("_expected_invoice"),original);
        String type=p.getString("correction_type");if(original.optString("issued_at").isEmpty()||!Arrays.asList("replacement","cancellation").contains(type)||p.optString("reason").trim().isEmpty()||type.equals("cancellation")&&original.optString("status").equals("Storniert"))throw new IllegalArgumentException("Originalbezug, Korrekturart und Grund prüfen.");
        String id="inv_correction_"+PixelMcpWriteTools.hash(a.getString("client_id")).substring(0,32);if(OwnerJournalRules.findOrNull(s.getJSONArray("invoices"),id)!=null)throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Korrektur-ID bereits belegt.");
        JSONObject next=new JSONObject(original.toString());for(String key:new String[]{"issuer","recipient","pdf_sha256","pdf_storage_name","pdf_byte_size","pdf_base64","numbering_receipt","document_id"})next.remove(key);
        // Business dates use Vienna; derive once from the original action, never from replay time.
        String correctionDate=Instant.parse(a.getString("created_at")).atZone(ZoneId.of("Europe/Vienna")).toLocalDate().toString();
        next.put("id",id).put("invoice_number","Entwurf-"+id).put("invoice_date",correctionDate).put("status","Entwurf").put("issued_at","").put("sevdesk_id","")
            .put("document_type",type).put("correction_of",original.getString("id")).put("correction_reason",p.getString("reason")).put("created_at",a.getString("created_at")).put("updated_at",a.getString("created_at"));
        JSONArray positions=next.getJSONArray("positions");for(int i=0;i<positions.length();i++)positions.getJSONObject(i).put("worklog_id","");
        InvoiceDraftRules.normalize(next,null,s);OwnerJournalRules.replace(s,"invoices",id,next);
    }
}
