package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Work use cases. Effects pass through the existing owner repository transaction. */
final class WorkApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    WorkApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "save_reviewed_order":{JSONObject row=OrderReviewRules.prepare(repository.state(),a);return repository.enqueue("order",row,request).put("order_id",row.getString("id"));}
            case "set_work_record_billed":case "reassign_work_record":{
                JSONObject old=PixelMcpWriteTools.find(all.getJSONArray("worklogs"),a.getString("work_record_id"));if(!PixelMcpWriteTools.hash(PixelMcpWriteTools.base(old)).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Arbeitsaufzeichnung wurde verändert.");
                JSONObject p=DriveJournalRules.clean(old);if(name.equals("set_work_record_billed"))p.put("billed",a.getBoolean("billed")?1:0);else{p.put("customer_id",a.getString("customer_id")).put("billing_customer_id",a.getString("billing_customer_id")).put("order_id",a.optString("order_id"));}
                repository.replaceWorklog(p);
                p.put("_owner_reason",a.getString("reason"));return repository.enqueue("worklog",p,request);
            }
            case "set_personal_paid":{if(!"1".equals(repository.ownerValue("personal_paid_migrated","")))throw new PixelMcpWriteTools.Rejected("DEVICE_MIGRATION_REQUIRED","App einmal öffnen, damit vorhandene persönliche Markierungen verlustfrei übernommen werden.");String id=a.getString("work_record_id");PixelMcpWriteTools.find(all.getJSONArray("worklogs"),id);Set<String> ids=new LinkedHashSet<>();JSONArray old=new JSONArray(repository.ownerValue("personal_paid","[]"));for(int i=0;i<old.length();i++)ids.add(old.getString(i));if(a.getBoolean("paid"))ids.add(id);else ids.remove(id);repository.putOwnerValue("personal_paid",new JSONArray(ids).toString());return new JSONObject().put("work_record_id",id).put("paid",a.getBoolean("paid")).put("scope","device_only");}
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
}
