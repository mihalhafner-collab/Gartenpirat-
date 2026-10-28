package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Settings use cases. Effects pass through the existing owner repository transaction. */
final class SettingsApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    SettingsApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "update_business_settings":{JSONObject p=new JSONObject(a.toString());p.remove("request_id");OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind","business_settings").put("payload",p),new HashMap<>(),new JSONObject());return repository.enqueue("business_settings",p,request);}
            case "update_settings":{JSONObject s=repository.settings();if(!PixelMcpWriteTools.hash(s).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Einstellungen wurden geändert.");JSONObject u=a.getJSONObject("updates");for(Iterator<String> keys=u.keys();keys.hasNext();){String key=keys.next();s.put(key,u.get(key));}repository.putOwnerValue("settings",s.toString());return new JSONObject().put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("scope","device_only");}
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
}
