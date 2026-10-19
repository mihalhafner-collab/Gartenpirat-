package at.hausgarten.service;

import org.json.*;
import java.util.*;
import java.time.*;

/** Typed adapters for the existing persistent timer, not a second time store. */
final class PixelMcpSessionTools {
    static final String[] NAMES={"finish_work","start_work","start_break","end_break","stop_work","complete_work_session","continue_work","discard_work_session","restore_work_session"};
    static boolean handles(String name){return Arrays.asList(NAMES).contains(name);}
    static JSONObject schema(String name)throws Exception{
        JSONObject props=new JSONObject().put("request_id",PixelMcpWriteTools.field("request_id").put("minLength",8).put("maxLength",128));JSONArray required=new JSONArray().put("request_id");
        if(name.equals("start_work")){
            for(String key:new String[]{"customer_id","billing_customer_id","order_id","appointment_id","work_date","activity","location"})props.put(key,PixelMcpWriteTools.field(key));required.put("customer_id");
        }else{props.put("session_id",PixelMcpWriteTools.field("session_id"));required.put("session_id");}
        if(name.equals("complete_work_session")||name.equals("finish_work")){
            for(String key:new String[]{"customer_id","billing_customer_id","activity","material","notes","location","kilometers","material_cost"})props.put(key,PixelMcpWriteTools.field(key));required.put("activity");
        }
        return new JSONObject().put("type","object").put("properties",props).put("required",required).put("additionalProperties",false);
    }
    static JSONArray tools()throws Exception{
        JSONArray out=new JSONArray();for(String name:NAMES)out.put(new JSONObject().put("name",name).put("inputSchema",schema(name)).put("description",
            "Bedient die vorhandene dauerhafte Pixel-Zeiterfassung. Vor Änderungen get_active_work_session lesen. request_id bei Wiederholung gleich lassen. start_work verlangt einen echten Kunden; Datum sonst heute in Europe/Vienna. stop_work stoppt nur die Sitzung, erzeugt noch keine Arbeitsaufzeichnung. complete_work_session übernimmt die geprüfte gestoppte Sitzung in den vorhandenen mobilen Abgleich; Nettozeit kommt ausschließlich aus dem Timer. finish_work stoppt und speichert dieselbe echte Sitzung atomar anhand session_id und Tätigkeit; Validierungsfehler erhalten den vorherigen Timerzustand. Keine Rechnungsänderung.")
            .put("annotations",new JSONObject().put("readOnlyHint",false).put("destructiveHint",false).put("idempotentHint",true).put("openWorldHint",false)));return out;
    }
    static JSONObject prepare(String name,JSONObject args,JSONObject state)throws Exception{
        PixelMcpWriteTools.validate(args,schema(name));JSONObject current=state.optJSONObject("work_session"),out=new JSONObject(args.toString());
        if(current==null)throw new PixelMcpWriteTools.Rejected("SESSION_UNAVAILABLE","Die aktuelle Zeiterfassung ist nicht lesbar. Bitte die App prüfen.");
        if(name.equals("start_work")){
            if(!"IDLE".equals(current.optString("state")))throw new PixelMcpWriteTools.Rejected("SESSION_CONFLICT","Eine Sitzung ist bereits aktiv oder wartet auf Abschluss.");
            JSONObject master=PixelMcpReadTools.effective(state);PixelMcpWriteTools.find(master.optJSONArray("customers"),out.getString("customer_id"));
            String order=out.optString("order_id");if(!order.isEmpty()){JSONObject row=PixelMcpWriteTools.find(master.optJSONArray("orders"),order);if(!row.optString("customer_id").equals(out.getString("customer_id")))throw new PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH","Kunde und Auftrag passen nicht zusammen.");if(out.optString("location").isEmpty())out.put("location",row.optString("location"));}
            BillingRules.work(out,null,master);
            if(!out.has("work_date"))out.put("work_date",LocalDate.now(ZoneId.of("Europe/Vienna")).toString());LocalDate.parse(out.getString("work_date"));return out;
        }
        if(name.equals("restore_work_session")){if(!"IDLE".equals(current.optString("state")))throw new PixelMcpWriteTools.Rejected("SESSION_CONFLICT","Eine andere Sitzung ist aktiv.");return out;}
        if(!args.getString("session_id").equals(current.optString("id")))throw new PixelMcpWriteTools.Rejected("SESSION_CONFLICT","Die Sitzung ist nicht mehr die aktuelle Sitzung. Bitte erneut abfragen.");
        if(name.equals("discard_work_session")){if(!Arrays.asList("RUNNING","PAUSED","STOPPED_PENDING_REVIEW").contains(current.optString("state")))throw new PixelMcpWriteTools.Rejected("SESSION_CONFLICT","Diese Sitzung kann nicht verworfen werden.");return out;}
        String expected=name.equals("continue_work")?"STOPPED_PENDING_REVIEW":name.equals("start_break")?"RUNNING":name.equals("end_break")?"PAUSED":(name.equals("complete_work_session")||name.equals("finish_work"))?"STOPPED_PENDING_REVIEW":"";
        if(!expected.isEmpty()&&!expected.equals(current.optString("state"))||name.equals("stop_work")&&!Arrays.asList("RUNNING","PAUSED").contains(current.optString("state")))throw new PixelMcpWriteTools.Rejected("SESSION_CONFLICT","Diese Aktion passt nicht zum aktuellen Sitzungszustand.");
        if(name.equals("complete_work_session")||name.equals("finish_work")){
            if(current.optBoolean("pause_unverified"))throw new PixelMcpWriteTools.Rejected("TIME_REVIEW_REQUIRED","Die Geräteuhr hat sich während einer Pause widersprüchlich geändert. Bitte die Sitzung in der App prüfen.");
            String customer=current.optString("customer_id");if(!out.optString("customer_id",customer).equals(customer)&&!customer.isEmpty())throw new PixelMcpWriteTools.Rejected("REFERENCE_MISMATCH","Die Kundenzuordnung der gestarteten Sitzung darf nicht still geändert werden.");
            out.put("customer_id",out.optString("customer_id",customer)).put("order_id",current.optString("order_id")).put("work_date",current.getString("work_date")).put("hours",current.getDouble("hours")).put("break_minutes",current.optInt("break_minutes",0)).put("billed",0).put("customer_visible",1).put("source","ChatGPT Sprache");
            if(current.has("started_at_ms")){String origin=Instant.ofEpochMilli(current.getLong("started_at_ms")).atZone(ZoneId.of("Europe/Vienna")).toString();String audit="Zeiterfassung: Beginn "+origin+", Ende "+current.optString("updated_at")+". Nettozeit aus Timer; Pausen in Minuten aus Ereigniszeiten gerundet.";out.put("notes",(out.optString("notes").isEmpty()?"":out.getString("notes")+"\n")+audit);}
            PixelMcpWriteTools.find(PixelMcpReadTools.effective(state).optJSONArray("customers"),out.getString("customer_id"));
            if(!out.has("billing_customer_id")&&!current.optString("billing_customer_id").isEmpty())out.put("billing_customer_id",current.getString("billing_customer_id"));
            BillingRules.work(out,null,PixelMcpReadTools.effective(state));
            PixelMcpWriteTools.applyMaterialCost(out);WorklogRules.validate(out,true);
        }
        return out;
    }
}
