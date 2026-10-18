package at.hausgarten.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Small validation helpers for the existing manual offline write path. No storage/migration. */
final class EntityProtectionRules {
    static String text(JSONObject row,String key){return row==null||row.isNull(key)?"":row.optString(key).trim();}
    private static String normalized(String value){return value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    private static String digits(String value){return value.replaceAll("[^0-9]","");}
    private static JSONObject find(JSONArray rows,String id){
        for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(text(row,"id")))return row;}
        return null;
    }
    static void validateNewCustomer(JSONObject payload,JSONArray customers){
        if(payload.optBoolean("allow_duplicate",false))return;
        String name=normalized(text(payload,"name")),fullName=normalized(text(payload,"first_name")+" "+text(payload,"last_name")),company=normalized(text(payload,"company")),email=normalized(text(payload,"email")),phone=digits(text(payload,"phone")),source=text(payload,"sevdesk_contact_id");
        for(int i=0;customers!=null&&i<customers.length();i++){
            JSONObject row=customers.optJSONObject(i);if(row==null||text(payload,"id").equals(text(row,"id")))continue;
            String full=normalized(text(row,"first_name")+" "+text(row,"last_name"));
            boolean sameName=!name.isEmpty()&&(name.equals(normalized(text(row,"name")))||name.equals(full)||name.equals(normalized(text(row,"company"))))||!fullName.isEmpty()&&(fullName.equals(full)||fullName.equals(normalized(text(row,"name"))));
            boolean sameCompany=!company.isEmpty()&&company.equals(normalized(text(row,"company")));
            boolean sameContact=(!email.isEmpty()&&email.equals(normalized(text(row,"email"))))||(!phone.isEmpty()&&phone.equals(digits(text(row,"phone"))))||(!source.isEmpty()&&source.equals(text(row,"sevdesk_contact_id")));
            if(sameName||sameCompany||sameContact)throw new IllegalArgumentException("DUPLICATE_CUSTOMER: Ein ähnlicher Kunde existiert bereits: "+text(row,"name")+(text(row,"city").isEmpty()?"":" · "+text(row,"city"))+". Bitte vorhandenen Kunden prüfen, auch archivierte Kunden.");
        }
    }
    static void validateOrderChange(JSONObject payload,JSONObject previous,JSONArray worklogs,JSONArray invoices,JSONArray photos){
        if(previous==null||text(previous,"customer_id").equals(text(payload,"customer_id")))return;
        for(JSONArray rows:new JSONArray[]{worklogs,invoices,photos})for(int i=0;rows!=null&&i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row!=null&&text(previous,"id").equals(text(row,"order_id")))
                throw new IllegalArgumentException("Der Kunde kann nicht geändert werden: Dieser Auftrag besitzt bereits zugeordnete Arbeiten, Rechnungen oder Fotos.");
        }
    }
    static void validatePhoto(JSONObject payload,JSONArray customers,JSONArray orders,JSONArray worklogs){
        String customer=text(payload,"customer_id"),orderId=text(payload,"order_id"),workId=text(payload,"worklog_id");
        if(find(customers,customer)==null)throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
        if(!orderId.isEmpty()){
            JSONObject order=find(orders,orderId);
            if(order==null)throw new IllegalArgumentException("Der ausgewählte Auftrag existiert nicht.");
            if(!customer.equals(text(order,"customer_id")))throw new IllegalArgumentException("Kunde und Auftrag des Fotos passen nicht zusammen.");
        }
        if(!workId.isEmpty()){
            JSONObject work=find(worklogs,workId);
            if(work==null)throw new IllegalArgumentException("Die ausgewählte Arbeitsaufzeichnung existiert nicht.");
            if(!customer.equals(text(work,"customer_id")))throw new IllegalArgumentException("Kunde und Arbeitsaufzeichnung des Fotos passen nicht zusammen.");
            if(!orderId.isEmpty()&&!text(work,"order_id").isEmpty()&&!orderId.equals(text(work,"order_id")))throw new IllegalArgumentException("Auftrag und Arbeitsaufzeichnung des Fotos passen nicht zusammen.");
        }
    }
    static void validateInvoiceDelete(JSONObject row){
        if(row==null)throw new IllegalArgumentException("Die Rechnung wurde nicht gefunden. Bitte Ansicht aktualisieren.");
        if(!text(row,"invoice_number").isEmpty()&&!text(row,"invoice_number").startsWith("Entwurf"))throw new IllegalArgumentException("Eine vergebene Rechnungsnummer bleibt als Beleg erhalten. Nur einen lokalen Entwurf löschen.");
        if(!text(row,"sevdesk_id").isEmpty()||!text(row,"issued_at").isEmpty()||!java.util.Arrays.asList("Entwurf","Storniert").contains(text(row,"status")))
            throw new IllegalArgumentException("Diese Rechnung ist geschützt und kann hier nicht gelöscht werden. Nur lokale, nicht ausgestellte Entwürfe dürfen entfernt werden.");
    }
}
