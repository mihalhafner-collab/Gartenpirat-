package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** One explicit trash selection, one guarded journal action. Never deletes active dependencies. */
final class TrashPurgeBatch {
    static final String KIND="purge_trash_v1";
    private static final String[] GROUPS={"customers","orders","worklogs","photos","invoices","offers","service_catalog","appointments"};

    static JSONObject preview(JSONObject snapshot)throws Exception {
        JSONObject archive=OwnerJournalRules.archives(snapshot),basis=new JSONObject();
        TreeSet<String> candidates=new TreeSet<>();TreeMap<String,String> blocked=new TreeMap<>();
        JSONArray archiveGuards=new JSONArray();
        TreeSet<String> archiveKeys=new TreeSet<>();archive.keys().forEachRemaining(archiveKeys::add);
        for(String key:archiveKeys) {
            JSONObject entry=archive.getJSONObject(key);if(entry.optBoolean("purged"))continue;
            if(key.indexOf(':')<1||!entry.has("record"))throw new IllegalArgumentException("Ungültiger Papierkorbeintrag. Nichts entfernt.");
            candidates.add(key);archiveGuards.put(new JSONObject().put("key",key).put("hash",OwnerJournalRules.version(entry)));
        }
        basis.put("archive",archiveGuards);
        for(String group:GROUPS){TreeMap<String,String> guards=new TreeMap<>();JSONArray rows=snapshot.optJSONArray(group);if(rows==null&&group.equals("appointments"))rows=new JSONArray();if(rows==null)throw new IllegalArgumentException("Unvollständiger Geschäftsstand.");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);guards.put(row.getString("id"),OwnerJournalRules.version(row));}
            basis.put(group,new JSONObject(guards));}
        // Fixed point: protected trash continues to protect everything it references.
        // References entirely inside the removable set do not create circular barriers.
        boolean changed;
        do {
            changed=false;JSONObject view=referenceView(snapshot,candidates);
            for(String key:new ArrayList<>(candidates))try{validate(snapshot,view,key);}
            catch(IllegalArgumentException refused){candidates.remove(key);blocked.put(key,refused.getMessage());changed=true;}
        }while(changed);
        JSONArray entries=new JSONArray(),remaining=new JSONArray();
        for(String key:candidates){int split=key.indexOf(':');entries.put(new JSONObject().put("entity",key.substring(0,split)).put("entity_id",key.substring(split+1)).put("expected_record_hash",OwnerJournalRules.version(archive.getJSONObject(key))));}
        for(Map.Entry<String,String> e:blocked.entrySet())remaining.put(new JSONObject().put("archive_key",e.getKey()).put("message",e.getValue()));
        return new JSONObject().put("total",archiveGuards.length()).put("count",entries.length()).put("allowed",entries.length()>0)
            .put("entries",entries).put("blocked",remaining).put("preview_hash",PixelMcpWriteTools.hash(basis))
            .put("recoverable",false).put("active_data_deleted",false);
    }
    private static JSONObject referenceView(JSONObject source,Set<String> selected)throws Exception {
        JSONObject view=new JSONObject(source.toString()),archive=OwnerJournalRules.archives(view);
        for(String key:selected){archive.remove(key);int split=key.indexOf(':');String type=key.substring(0,split);
            if(type.equals("customer")||type.equals("service_template"))OwnerJournalRules.replace(view,OwnerJournalRules.group(type),key.substring(split+1),null);}
        return view;
    }
    private static void validate(JSONObject original,JSONObject view,String key)throws Exception {
        int split=key.indexOf(':');String type=key.substring(0,split),id=key.substring(split+1);
        if(!Arrays.asList(DeletionRules.ENTITIES).contains(type))throw new IllegalArgumentException("Geschützter oder unbekannter Papierkorbbereich.");
        JSONObject saved=OwnerJournalRules.archives(original).getJSONObject(key),record=saved.getJSONObject("record");
        if(!id.equals(record.getString("id")))throw new IllegalArgumentException("Papierkorb-ID stimmt nicht mit dem Original überein.");
        JSONObject active=OwnerJournalRules.findOrNull(original.getJSONArray(OwnerJournalRules.group(type)),id);
        if(type.equals("customer")||type.equals("service_template")){
            OwnerJournalRules.checkHash(active,saved.getString("archived_record_hash"));
            if(active==null||active.optBoolean("active",true)&&active.optInt("active",1)!=0)throw new IllegalArgumentException("Eintrag ist wieder aktiv. Nichts gelöscht.");
        }else if(active!=null)throw new IllegalArgumentException("Eintrag ist wieder aktiv. Nichts gelöscht.");
        OwnerJournalRules.validateArchive(type,record,view);TrashRules.requireUnreferenced(view,type,id);
    }
    static JSONObject payload(JSONObject snapshot,String expected,boolean confirmed)throws Exception {
        if(!confirmed)throw new IllegalArgumentException("Papierkorb leeren ausdrücklich bestätigen.");
        JSONObject preview=preview(snapshot);
        if(!preview.getString("preview_hash").equals(expected))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Papierkorb oder Verknüpfungen wurden geändert. Nichts gelöscht; Vorschau neu öffnen.");
        if(!preview.getBoolean("allowed"))throw new IllegalArgumentException("Keine freigegebenen Papierkorbeinträge vorhanden.");
        return new JSONObject().put("confirmed",true).put("expected_preview_hash",expected).put("entries",preview.getJSONArray("entries"));
    }
    static JSONObject currentGuard(JSONObject snapshot)throws Exception {return new JSONObject().put("preview_hash",preview(snapshot).getString("preview_hash"));}
    /** Bind the SAME confirmed entries to the view that can actually leave the device.
     * Local-only test journals remain private and may change an unrelated guard row.
     * Both full-state hashes are captured in the owner transaction before enqueueing.
     */
    static void bindDrivePreview(JSONObject payload,JSONObject shared)throws Exception {
        JSONObject drive=preview(shared);
        if(!PixelMcpWriteTools.hash(payload.getJSONArray("entries")).equals(PixelMcpWriteTools.hash(drive.getJSONArray("entries"))))
            throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Lokale und für Drive vorgesehene Papierkorbmenge unterscheiden sich. Einträge einzeln prüfen; nichts gelöscht.");
        if(!drive.getString("preview_hash").equals(payload.getString("expected_preview_hash")))
            payload.put("_drive_preview_hash",drive.getString("preview_hash"));
    }
    static void apply(JSONObject snapshot,JSONObject action)throws Exception {
        JSONObject supplied=action.getJSONObject("payload");String expected=supplied.getString("expected_preview_hash");
        // An additive transport guard never widens the selection: all current reference,
        // financial, version and exact-entry checks below still run against THIS snapshot.
        if(supplied.has("_drive_preview_hash")&&!preview(snapshot).getString("preview_hash").equals(expected))expected=supplied.getString("_drive_preview_hash");
        JSONObject checked=payload(snapshot,expected,supplied.optBoolean("confirmed"));
        if(!PixelMcpWriteTools.hash(checked.getJSONArray("entries")).equals(PixelMcpWriteTools.hash(supplied.getJSONArray("entries"))))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Die bestätigte Löschmenge stimmt nicht mehr überein.");
        JSONArray entries=checked.getJSONArray("entries");JSONObject archive=OwnerJournalRules.archives(snapshot);
        for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);String type=e.getString("entity"),id=e.getString("entity_id");TrashRules.commitPurge(snapshot,action,type,id,archive.getJSONObject(type+":"+id));}
    }
    private TrashPurgeBatch(){}
}
