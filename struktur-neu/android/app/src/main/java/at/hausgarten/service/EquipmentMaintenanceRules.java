package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;
import static at.hausgarten.service.RevierRules.*;

/** Calendar plans for the existing local equipment owner; no scheduler or network effects. */
final class EquipmentMaintenanceRules {
    static final int MAX_PLANS=64;

    static void interval(JSONObject row)throws Exception {
        String unit=text(row,"interval_unit",12,true);
        int maximum;
        switch(unit){case "days":maximum=3650;break;case "weeks":maximum=520;break;
            case "months":maximum=120;break;case "years":maximum=10;break;
            default:throw error("INVALID_INPUT","Intervall in Tagen, Wochen, Monaten oder Jahren wählen.");}
        int value=integer(row,"interval_value",1,maximum,-1);
        if(value<1)throw error("INVALID_INPUT","Eine positive Intervalllänge angeben.");
        row.put("interval_value",value).put("interval_unit",unit);
    }

    static LocalDate advance(LocalDate anchor,JSONObject interval)throws Exception {
        interval(interval);int value=interval.getInt("interval_value");
        try{switch(interval.getString("interval_unit")){
            case "days":return anchor.plusDays(value);
            case "weeks":return anchor.plusWeeks(value);
            case "months":return anchor.plusMonths(value);
            default:return anchor.plusYears(value);
        }}catch(DateTimeException invalid){throw error("INVALID_INPUT","Dieses Intervall ergibt kein gültiges Wartungsdatum.");}
    }

    static void recalculate(JSONObject plan)throws Exception {
        String regular=advance(date(plan.getString("anchor_on")),plan).toString();
        if(regular.length()!=10)throw error("INVALID_INPUT","Das Wartungsdatum liegt außerhalb des gültigen Bereichs.");
        plan.put("regular_due_on",regular);
        String once=date(plan,"one_off_due_on",false);
        if(!once.isEmpty()&&once.compareTo(regular)>=0){plan.remove("one_off_due_on");once="";}
        plan.put("next_due_on",once.isEmpty()?regular:once);
    }

    static JSONArray plans(JSONObject equipment)throws Exception {
        if(!equipment.has("maintenance_plans"))return new JSONArray();
        if(!(equipment.get("maintenance_plans") instanceof JSONArray))throw error("REVIER_RELATION_INVALID","Wartungspläne sind nicht als Liste gespeichert.");
        JSONArray plans=equipment.getJSONArray("maintenance_plans");
        if(plans.length()>MAX_PLANS)throw error("REVIER_RELATION_INVALID","Zu viele Wartungspläne an diesem Gerät.");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<plans.length();i++){
            JSONObject p=plans.getJSONObject(i);String id=id(p,"id",true);
            if(!ids.add(id))throw error("REVIER_RELATION_INVALID","Wartungsplan ist mehrfach vorhanden.");
            text(p,"title",240,true);interval(p);date(p,"anchor_on",true);
            JSONObject expected=copy(p);recalculate(expected);
            for(String field:new String[]{"regular_due_on","next_due_on"})
                if(!expected.getString(field).equals(p.optString(field)))throw error("REVIER_RELATION_INVALID","Wartungsintervall und Fälligkeit widersprechen sich.");
            if(expected.has("one_off_due_on")!=p.has("one_off_due_on"))throw error("REVIER_RELATION_INVALID","Einzeltermin und regelmäßige Fälligkeit widersprechen sich.");
            String completed=date(p,"last_completed_on",false);
            if(!completed.isEmpty()&&(!completed.equals(p.getString("anchor_on"))||p.optString("last_event_id").isEmpty()))
                throw error("REVIER_RELATION_INVALID","Letzte Wartung und Planbezug widersprechen sich.");
        }
        return plans;
    }

    static JSONObject find(JSONArray plans,String id)throws Exception {
        for(int i=0;i<plans.length();i++)if(id.equals(plans.getJSONObject(i).getString("id")))return plans.getJSONObject(i);
        throw error("REFERENCE_NOT_FOUND","Dieser Wartungsplan wurde nicht gefunden.");
    }

    static void shorten(JSONObject plan,JSONObject requested,String reason)throws Exception {
        if(reason.trim().isEmpty())throw error("INVALID_INPUT","Grund für das kürzere Intervall angeben.");
        interval(requested);LocalDate anchor=date(plan.getString("anchor_on"));
        if(!advance(anchor,requested).isBefore(advance(anchor,plan)))throw error("INVALID_INPUT","Ein tatsächlich kürzeres Intervall wählen.");
        plan.put("interval_value",requested.getInt("interval_value")).put("interval_unit",requested.getString("interval_unit"));
        recalculate(plan);
    }

    static String nextDue(JSONObject equipment)throws Exception {
        String next="";JSONArray plans=plans(equipment);
        for(int i=0;i<plans.length();i++){String due=plans.getJSONObject(i).getString("next_due_on");if(next.isEmpty()||due.compareTo(next)<0)next=due;}
        return next;
    }

    private EquipmentMaintenanceRules(){}
}
