package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Read-only evidence and quality checks using the existing effective data. */
final class PixelMcpBusinessTools {
    static JSONObject sync(JSONObject legacy,JSONObject state)throws Exception{
        String phase=legacy.optString("status"),value=phase.equals("local_saved")?"not_applicable":phase.equals("accepted")?"sync_confirmed":phase.equals("failed")?"sync_failed":phase.equals("superseded")?"sync_conflict":Arrays.asList("queued","transferred").contains(phase)?"sync_pending":"unknown";
        JSONArray errors=state.getJSONObject("master").optJSONArray("sync_errors");for(int i=0;errors!=null&&i<errors.length();i++){JSONObject e=errors.optJSONObject(i);if(e!=null&&e.optString("client_id").equals(legacy.optString("client_id"))&&"WRITE_CONFLICT".equals(e.optString("code")))value="sync_conflict";}
        errors=state.getJSONObject("master").optJSONArray("drive_journal_errors");for(int i=0;errors!=null&&i<errors.length();i++){JSONObject e=errors.optJSONObject(i);if(e!=null&&e.optString("client_id").equals(legacy.optString("client_id")))value="WRITE_CONFLICT".equals(e.optString("code"))?"sync_conflict":"sync_failed";}
        if(value.equals("sync_pending")&&state.optBoolean("drive_sync_failed"))value="sync_failed";
        legacy.put("retryable",value.equals("sync_failed"));
        if(value.equals("sync_failed")&&state.optBoolean("drive_sync_failed"))legacy.put("sync_message","Der letzte Drive-Abgleich ist fehlgeschlagen. Lokale Daten bleiben erhalten; App öffnen und Abgleich erneut versuchen.");
        legacy.put("sync_state",value).put("local_state",legacy.optBoolean("saved_locally")?"local_saved":"unknown");return legacy;
    }
    static String billing(JSONObject row,JSONArray invoices)throws Exception{
        String id=row.optString("id");boolean draft=false,issued=false;
        for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject inv=invoices.getJSONObject(i);if(Arrays.asList("Storniert","Cancelled","Entfernt").contains(inv.optString("status")))continue;JSONArray links=inv.optJSONArray("worklog_ids");boolean linked=false;for(int j=0;links!=null&&j<links.length();j++)if(id.equals(links.optString(j)))linked=true;
            JSONArray positions=inv.optJSONArray("positions");for(int j=0;positions!=null&&j<positions.length();j++)if(id.equals(positions.getJSONObject(j).optString("worklog_id")))linked=true;
            if(linked){if(!inv.optString("issued_at").isEmpty()||!inv.optString("sevdesk_id").isEmpty()&&!"Entwurf".equals(inv.optString("status")))issued=true;else draft=true;}}
        return issued?"proven_invoiced":row.optInt("billed",0)!=0?"billing_unclear":draft?"draft_linked_not_proven":"marked_open_not_proven";
    }
    static boolean overlap(JSONObject a,JSONObject b){
        if(!a.optString("work_date").equals(b.optString("work_date"))||a.optString("id").equals(b.optString("id")))return false;
        try{int as=java.time.LocalTime.parse(a.getString("start_time")).toSecondOfDay(),ae=java.time.LocalTime.parse(a.getString("end_time")).toSecondOfDay(),bs=java.time.LocalTime.parse(b.getString("start_time")).toSecondOfDay(),be=java.time.LocalTime.parse(b.getString("end_time")).toSecondOfDay();return ae>as&&be>bs&&as<be&&bs<ae;}catch(Exception e){return false;}
    }
    static void preventOverlap(JSONObject payload,JSONArray logs)throws Exception{
        for(int i=0;logs!=null&&i<logs.length();i++)if(overlap(payload,logs.getJSONObject(i)))throw new PixelMcpWriteTools.Rejected("TIME_OVERLAP","Diese Uhrzeiten überschneiden sich mit einer vorhandenen Arbeitsaufzeichnung. Bitte den Tag prüfen; keine Änderung wurde gespeichert.");
    }
    static JSONObject report(JSONArray logs,JSONObject master)throws Exception{
        double hours=0;int marked=0,proven=0,unclear=0;JSONArray issues=new JSONArray();Set<String> customerIds=new HashSet<>();JSONArray customers=master.optJSONArray("customers");for(int i=0;customers!=null&&i<customers.length();i++)customerIds.add(customers.getJSONObject(i).optString("id"));
        if(logs.length()>5000)throw new IllegalArgumentException("Bitte einen engeren Zeitraum für die Qualitätsprüfung wählen.");
        for(int i=0;i<logs.length();i++){JSONObject row=logs.getJSONObject(i);hours+=row.optDouble("hours",0);String evidence=billing(row,master.optJSONArray("invoices"));if(evidence.equals("proven_invoiced"))proven++;else if(evidence.equals("marked_open_not_proven"))marked++;else unclear++;
            if(!customerIds.contains(row.optString("customer_id")))issues.put(issue("MISSING_CUSTOMER",row,"Kundenverknüpfung fehlt oder ist ungültig."));
            try{JSONObject normalized=new JSONObject(row.toString());WorklogRules.validate(normalized,false);if(Math.abs(normalized.optDouble("hours")-row.optDouble("hours"))>0.005)issues.put(issue("TIME_MISMATCH",row,"Gespeicherte Stunden passen nicht zu den Uhrzeiten."));}catch(Exception e){issues.put(issue("INVALID_WORK_RECORD",row,"Datum, Zeiten, Tätigkeit und Zahlen bitte prüfen."));}
            if(row.optInt("billed",0)==0&&evidence.equals("proven_invoiced"))issues.put(issue("BILLING_MISMATCH",row,"Verknüpfte ausgestellte Rechnung vorhanden, App-Markierung noch offen. Nicht automatisch geändert."));
            for(int j=0;j<i;j++){JSONObject other=logs.getJSONObject(j);if(overlap(row,other))issues.put(issue("TIME_OVERLAP",row,"Überschneidung mit "+other.optString("id")));if(row.optString("customer_id").equals(other.optString("customer_id"))&&row.optString("work_date").equals(other.optString("work_date"))&&Math.abs(row.optDouble("hours")-other.optDouble("hours"))<0.005&&row.optString("activity").trim().equalsIgnoreCase(other.optString("activity").trim()))issues.put(issue("POSSIBLE_DUPLICATE",row,"Ähnlicher Eintrag: "+other.optString("id")));}
        }
        return new JSONObject().put("work_records",logs.length()).put("hours",Math.round(hours*100)/100.0).put("marked_open_not_proven",marked).put("proven_invoiced",proven).put("billing_unclear_or_draft",unclear).put("issues",issues).put("timezone","Europe/Vienna").put("billing_notice","Offen markiert ist kein Nachweis fehlender Rechnungen. Tatsächlich nicht abgerechnet ist ohne vollständige verlässliche Rechnungsquelle nicht bestimmbar.");
    }
    private static JSONObject issue(String code,JSONObject row,String message)throws Exception{return new JSONObject().put("code",code).put("work_record_id",row.optString("id")).put("message",message);}
}
