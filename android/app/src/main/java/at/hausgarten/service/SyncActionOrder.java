package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Stable dependency order, independent of device clocks. Original action bytes stay unchanged. */
final class SyncActionOrder {
    static JSONArray order(JSONArray input)throws Exception{
        LinkedHashMap<String,JSONObject> actions=new LinkedHashMap<>();Map<String,String> creators=new HashMap<>(),versions=new HashMap<>();
        for(int i=0;i<input.length();i++){JSONObject a=input.getJSONObject(i),p=a.getJSONObject("payload");String id=a.getString("client_id"),entity=a.getString("kind")+":"+p.optString("id");actions.put(id,a);if(p.optBoolean("_mcp_create")&&!p.optString("id").isEmpty())creators.putIfAbsent(entity,id);if(!p.optString("updated_at").isEmpty())versions.put(entity+":"+p.getString("updated_at"),id);}
        Map<String,Set<String>> dependencies=new LinkedHashMap<>();
        for(Map.Entry<String,JSONObject> entry:actions.entrySet()){String id=entry.getKey();JSONObject a=entry.getValue(),p=a.getJSONObject("payload");Set<String> deps=new LinkedHashSet<>();
            if("conflict_resolution".equals(a.getString("kind"))&&actions.containsKey(p.optString("client_id")))deps.add(p.getString("client_id"));
            String ownKind=a.getString("kind").startsWith("offer_")?"offer":a.getString("kind").equals("pixel_invoice_issue_v1")?"invoice":a.getString("kind");
            String creator=creators.get(ownKind+":"+p.optString("id"));if(creator!=null&&!creator.equals(id))deps.add(creator);
            JSONArray explicit=p.optJSONArray("_sync_predecessors");for(int j=0;explicit!=null&&j<explicit.length();j++)if(actions.containsKey(explicit.getString(j)))deps.add(explicit.getString(j));
            for(String[] ref:new String[][]{{"customer_id","customer"},{"billing_customer_id","customer"},{"order_id","order"},{"worklog_id","worklog"}}){String parent=creators.get(ref[1]+":"+p.optString(ref[0]));if(parent!=null&&!parent.equals(id))deps.add(parent);}
            JSONObject base=p.optJSONObject("_sync_base");if(base==null)base=p.optJSONObject("_mcp_base");if(base!=null){String prior=versions.get(a.getString("kind")+":"+p.optString("id")+":"+base.optString("updated_at"));if(prior!=null&&!prior.equals(id))deps.add(prior);}
            if(deps.contains(id))throw new PixelMcpWriteTools.Rejected("DEPENDENCY_CYCLE","Änderungen enthalten eine zyklische Vorgängerkette. Originale bleiben erhalten.");dependencies.put(id,deps);
        }
        JSONArray result=new JSONArray();Set<String> done=new HashSet<>();
        while(done.size()<actions.size()){boolean moved=false;for(String id:actions.keySet())if(!done.contains(id)&&done.containsAll(dependencies.get(id))){done.add(id);result.put(actions.get(id));moved=true;}if(!moved)throw new PixelMcpWriteTools.Rejected("DEPENDENCY_CYCLE","Änderungen enthalten eine zyklische Vorgängerkette. Originale bleiben erhalten.");}
        return result;
    }
    /** Merge only explicitly changed fields whose original values still match. */
    static JSONObject merge(JSONObject base,JSONObject patch,JSONObject current,String kind)throws Exception{
        if(current==null||!base.optString("id").equals(current.optString("id")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Der ursprüngliche Datensatz fehlt.");
        JSONObject result=new JSONObject(current.toString());Set<String> changed=new HashSet<>();
        for(Iterator<String> keys=patch.keys();keys.hasNext();){String key=keys.next();if(key.startsWith("_")||Arrays.asList("id","updated_at","created_at","pending").contains(key))continue;
            if(base.has(key)&&!equal(key,base.opt(key),patch.opt(key))){if(!equal(key,base.opt(key),current.opt(key))&&!equal(key,patch.opt(key),current.opt(key)))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Dasselbe Feld wurde unterschiedlich geändert. Beide Varianten bleiben erhalten.");changed.add(key);}}
        for(String key:changed)result.put(key,patch.get(key));
        // Financial and relationship validation runs on the merged result in the caller.
        if(patch.has("updated_at"))result.put("updated_at",patch.get("updated_at"));return result;
    }
    private static boolean equal(String key,Object a,Object b)throws Exception{return PixelMcpWriteTools.canonical(DriveJournalRules.comparable(key,a)).equals(PixelMcpWriteTools.canonical(DriveJournalRules.comparable(key,b)));}
}
