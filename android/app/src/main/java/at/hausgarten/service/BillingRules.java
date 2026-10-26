package at.hausgarten.service;
import org.json.*;
import java.util.*;
import java.math.BigDecimal;

/** Explicit recipient IDs; customer_id remains the actual work site. No historical reassignment. */
final class BillingRules {
    static String text(JSONObject row,String key){return row==null||row.isNull(key)?"":row.optString(key).trim();}
    static String recipient(JSONObject work){String id=text(work,"billing_customer_id");return id.isEmpty()?text(work,"customer_id"):id;}
    static JSONObject customer(JSONArray rows,String id)throws Exception{return PixelMcpWriteTools.find(rows,id);}
    static void customerConfig(JSONObject row,JSONArray customers)throws Exception{
        String mode=text(row,"billing_mode");if(mode.isEmpty())mode="direct";
        if(!Arrays.asList("direct","via_customer").contains(mode))throw new PixelMcpWriteTools.Rejected("INVALID_BILLING_MODE","Abrechnung muss Direkt oder Über einen anderen Kunden sein.");
        String target=text(row,"default_billing_customer_id");
        if(mode.equals("direct")){if(!target.isEmpty()&&!target.equals(text(row,"id")))throw new PixelMcpWriteTools.Rejected("BILLING_MISMATCH","Bei Direktabrechnung bitte keinen anderen Standard-Rechnungsempfänger angeben.");row.put("default_billing_customer_id","");}
        else{
            if(target.isEmpty()||target.equals(text(row,"id")))throw new PixelMcpWriteTools.Rejected("MISSING_BILLING_CUSTOMER","Bitte einen anderen vorhandenen Rechnungsempfänger auswählen.");
            customer(customers,target);Set<String> visited=new HashSet<>();visited.add(text(row,"id"));String next=target;
            while(!next.isEmpty()){if(!visited.add(next))throw new PixelMcpWriteTools.Rejected("BILLING_CYCLE","Die Standard-Abrechnung würde einen Kreis bilden. Bitte Zuordnung prüfen.");JSONObject other=customer(customers,next);next="via_customer".equals(text(other,"billing_mode"))?text(other,"default_billing_customer_id"):"";}
        }
        row.put("billing_mode",mode);
    }
    static String defaultRecipient(String customerId,String orderId,JSONObject master)throws Exception{
        JSONObject workCustomer=customer(master.optJSONArray("customers"),customerId);
        String recipient=customerId;
        if("via_customer".equals(text(workCustomer,"billing_mode")))recipient=text(workCustomer,"default_billing_customer_id");
        if(!orderId.isEmpty()){JSONObject order=PixelMcpWriteTools.find(master.optJSONArray("orders"),orderId);if(!customerId.equals(text(order,"customer_id")))throw new PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH","Einsatzkunde und Auftrag passen nicht zusammen.");if(!text(order,"billing_customer_id").isEmpty())recipient=text(order,"billing_customer_id");}
        if(recipient.isEmpty())throw new PixelMcpWriteTools.Rejected("MISSING_BILLING_CUSTOMER","Der Standard-Rechnungsempfänger fehlt. Bitte Kundenabrechnung prüfen.");
        customer(master.optJSONArray("customers"),recipient);return recipient;
    }
    static void work(JSONObject row,JSONObject old,JSONObject master)throws Exception{
        String workCustomer=text(row,"customer_id");customer(master.optJSONArray("customers"),workCustomer);
        String target=text(row,"billing_customer_id");
        if(target.isEmpty())target=old!=null?recipient(old):defaultRecipient(workCustomer,text(row,"order_id"),master);
        customer(master.optJSONArray("customers"),target);row.put("billing_customer_id",target);
    }
    static void order(JSONObject row,JSONObject master)throws Exception{
        if(!text(row,"billing_customer_id").isEmpty())customer(master.optJSONArray("customers"),text(row,"billing_customer_id"));
    }
    static JSONObject assignment(JSONObject row,JSONObject master,boolean work)throws Exception{
        String customerId=text(row,work?"customer_id":"id"),recipient=work?recipient(row):"via_customer".equals(text(row,"billing_mode"))?text(row,"default_billing_customer_id"):customerId;
        JSONObject out=new JSONObject().put("work_customer_id",customerId).put("billing_customer_id",recipient).put("mode",recipient.equals(customerId)?"direct":"via_customer");
        try{out.put("billing_customer_name",customer(master.optJSONArray("customers"),recipient).optString("name")).put("valid",true);}catch(Exception missing){out.put("valid",false).put("notice","Rechnungsempfänger nicht vorhanden. Keine automatische Neuzuordnung.");}return out;
    }
    static JSONArray groups(JSONArray logs,JSONObject master)throws Exception{
        Map<String,JSONObject> groups=new TreeMap<>();
        ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;logs!=null&&i<logs.length();i++)sorted.add(logs.getJSONObject(i));
        sorted.sort(Comparator.comparing((JSONObject row)->row.optString("work_date")).reversed().thenComparing(row->row.optString("id")));
        for(JSONObject row:sorted){
            JSONObject quantity=LocalBillingRules.state(row,master.optJSONArray("invoices"),master);String evidence=PixelMcpBusinessTools.billing(row,master.optJSONArray("invoices"),master);if(row.optInt("billed",0)!=0||"proven_invoiced".equals(evidence))continue;
            String recipient=recipient(row);JSONObject group=groups.get(recipient);
            if(group==null){JSONObject detail=assignment(row,master,true);group=new JSONObject().put("billing_customer_id",recipient).put("billing_customer_name",detail.optString("billing_customer_name","Zuordnung prüfen")).put("valid",detail.getBoolean("valid")).put("hours",0).put("work_records",new JSONArray()).put("billing_status","marked_open_not_proven");groups.put(recipient,group);}
            JSONObject copy=new JSONObject(row.toString()).put("billing_customer_id",recipient).put("billing_evidence",evidence);if(quantity!=null)copy.put("billing_quantity",quantity).put("quantity_remaining_decimal",quantity.getString("remaining_decimal"));
            try{copy.put("work_customer_name",customer(master.optJSONArray("customers"),text(row,"customer_id")).optString("name"));}catch(Exception missing){copy.put("work_customer_name","Einsatzkunde fehlt");group.put("valid",false);}
            group.getJSONArray("work_records").put(copy);if(master.optJSONObject("local_billing_sources")==null)group.put("hours",group.getDouble("hours")+row.optDouble("hours",0));else{BigDecimal remaining=quantity==null?new BigDecimal(row.optString("hours","0")):new BigDecimal(quantity.getString("remaining_decimal")),total=new BigDecimal(group.optString("hours_decimal","0")).add(remaining);group.put("hours_decimal",total.stripTrailingZeros().toPlainString()).put("hours",total.doubleValue());}
        }
        JSONArray result=new JSONArray();for(JSONObject group:groups.values()){group.put("hours",Math.round(group.getDouble("hours")*100)/100.0);result.put(group);}return result;
    }
}
