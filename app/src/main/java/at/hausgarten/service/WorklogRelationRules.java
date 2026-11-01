package at.hausgarten.service;
import org.json.JSONArray;
import org.json.JSONObject;

/** Mirrors the existing laptop relation/issued-invoice guards without changing any records. */
final class WorklogRelationRules {
    private static String text(JSONObject row,String key){return row==null||row.isNull(key)?"":row.optString(key);}
    static void validate(JSONObject payload,JSONObject old,JSONArray photos,JSONArray invoices)throws Exception{
        if(old==null)return;
        String id=old.getString("id"),customer=text(payload,"customer_id"),order=text(payload,"order_id");
        for(int i=0;photos!=null&&i<photos.length();i++){
            JSONObject photo=photos.optJSONObject(i);if(photo==null||!id.equals(text(photo,"worklog_id")))continue;
            if(!customer.equals(text(photo,"customer_id"))||(!text(photo,"order_id").isEmpty()&&!order.equals(text(photo,"order_id"))))
                throw new IllegalArgumentException("Bitte zuerst die Fotozuordnung prüfen. Kunde oder Auftrag würden dem Foto widersprechen.");
        }
        for(int i=0;invoices!=null&&i<invoices.length();i++){
            JSONObject invoice=invoices.optJSONObject(i);if(invoice==null)continue;
            JSONArray ids=invoice.optJSONArray("worklog_ids");boolean linked=false;
            for(int j=0;ids!=null&&j<ids.length();j++)if(id.equals(ids.optString(j)))linked=true;
            JSONArray positions=invoice.optJSONArray("positions");
            for(int j=0;positions!=null&&j<positions.length();j++){JSONObject position=positions.optJSONObject(j);if(position!=null&&id.equals(position.optString("worklog_id")))linked=true;}
            if(!linked)continue;
            if(!customer.equals(text(old,"customer_id"))||!order.equals(text(old,"order_id"))||!BillingRules.recipient(payload).equals(BillingRules.recipient(old)))
                throw new IllegalArgumentException("Diese Arbeit ist mit einer Rechnung verknüpft. Kunde und Auftrag bleiben erhalten.");
            if(text(invoice,"issued_at").isEmpty())continue;
            for(String field:new String[]{"work_date","start_time","end_time","break_minutes","hours","activity","material","kilometers"}){
                if(!payload.has(field))continue;
                boolean numeric=field.equals("break_minutes")||field.equals("hours")||field.equals("kilometers");
                boolean changed=numeric?Double.compare(payload.optDouble(field,0),old.optDouble(field,0))!=0:!text(payload,field).equals(text(old,field));
                if(changed)throw new IllegalArgumentException("Abgerechnete Leistungsdaten bleiben unverändert. Interne Notizen können ergänzt werden.");
            }
            if(payload.optInt("billed",old.optInt("billed",0))==0)throw new IllegalArgumentException("Diese Arbeit ist bereits einer abgeschlossenen Rechnung zugeordnet.");
        }
    }
}
