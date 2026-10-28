package at.hausgarten.service;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable Pixel snapshots in the EXISTING Drive folder. No mutable master overwrite. */
final class DrivePrimaryState {
    interface CommitVisitor {void accept(String id,String name,byte[] bytes)throws Exception;}
    interface Storage {
        Map<String,String> commits()throws Exception; // immutable document ID -> name
        byte[] read(String id)throws Exception;
        default byte[] readState(JSONObject manifest)throws Exception{return read(manifest.getString("state_file_id"));}
        default void readCommits(Map<String,String> files,CommitVisitor visitor)throws Exception{for(Map.Entry<String,String> file:files.entrySet())visitor.accept(file.getKey(),file.getValue(),read(file.getKey()));}
    }
    static JSONObject recover(JSONObject legacy,String publisher,Storage storage)throws Exception {
        return recover(legacy,publisher,storage,false);
    }
    /** Evidence only: verify the immutable chain and preserve BOTH variants, never authorize sync. */
    static JSONObject recover(JSONObject legacy,String publisher,Storage storage,boolean evidenceOnly)throws Exception {
        // Every caller (initial read, publish recheck, restart) uses the same additive
        // schema normalization. Never mutate the actual legacy object or its file.
        String observedLegacyHash=PixelMcpWriteTools.hash(legacy);
        legacy=new JSONObject(legacy.toString());DriveJournalRules.validateSnapshot(legacy);
        JSONArray summaries=new JSONArray();Map<String,JSONObject> manifests=new HashMap<>();
        Map<String,String> commitFiles=storage.commits();
        for(String name:commitFiles.values())if(!name.matches("HausGarten-Commit-[0-9a-f]{64}\\.json"))throw new IllegalArgumentException("PUBLICATION_INVALID: Ungültige Veröffentlichungsreferenz.");
        storage.readCommits(commitFiles,(id,name,bytes)->{
            if(!name.equals("HausGarten-Commit-"+HttpTransport.sha256(bytes)+".json"))throw new IllegalArgumentException("PUBLICATION_INVALID: Veröffentlichungsbeleg verändert.");
            JSONObject m=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!"hausgarten.drive.commit.v1".equals(m.optString("format")))throw new IllegalArgumentException("PUBLICATION_INVALID: Unbekanntes Belegformat.");
            summaries.put(new JSONObject().put("id",id).put("publisher",m.getString("publisher_id"))
                .put("sha",m.getString("state_sha256")).put("sequence",m.getLong("sequence"))
                .put("parent",m.optString("previous_publication_id")).put("parent_sha",m.optString("previous_state_sha256")));
            manifests.put(id,m);
        });
        JSONObject head=DrivePublication.head(summaries,publisher),state=new JSONObject(legacy.toString());
        String legacyHash=PixelMcpWriteTools.hash(legacy);
        if(head.length()>0) {
            JSONObject m=manifests.get(head.getString("id"));byte[] bytes=storage.readState(m);
            DrivePublication.verify(m,bytes,new JSONArray());state=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(state.has("drive_legacy_base_hash")&&!legacyHash.equals(state.getString("drive_legacy_base_hash"))
                    &&!observedLegacyHash.equals(state.getString("drive_legacy_base_hash"))
                    &&!compatibleLegacyHash(legacy,state.getString("drive_legacy_base_hash"))) {
                if(!evidenceOnly)throw new PixelMcpWriteTools.Rejected("LEGACY_SOURCE_CHANGED","Der ursprüngliche Drive-Gesamtstand wurde parallel verändert. Beide Stände bleiben erhalten; Migration ausdrücklich prüfen.");
                state.put("drive_legacy_source_changed",true);
            }
        }
        PixelDriveState.prepare(state);
        state.put("drive_legacy_base_hash",legacyHash).put("drive_parent_publication_id",head.optString("id"))
            .put("drive_parent_state_sha256",head.optString("sha")).put("drive_parent_sequence",head.optLong("sequence"));
        return state;
    }
    // Only absence versus EMPTY additive collections is equivalent. All unknown fields,
    // nonempty collections, records, queues and control metadata still participate.
    static boolean compatibleLegacyHash(JSONObject legacy,String expected)throws Exception {
        String[] added={"offers","service_catalog","appointments"};
        for(int mask=0;mask<8;mask++) {
            JSONObject candidate=new JSONObject(legacy.toString());boolean allowed=true;
            for(int i=0;i<added.length;i++)if((mask&(1<<i))!=0){JSONArray rows=candidate.optJSONArray(added[i]);if(rows==null||rows.length()!=0){allowed=false;break;}candidate.remove(added[i]);}
            if(allowed&&PixelMcpWriteTools.hash(candidate).equals(expected))return true;
        }
        return false;
    }
    static void checkParent(JSONObject state,JSONObject current)throws Exception {
        if(!state.optString("drive_parent_publication_id").equals(current.optString("drive_parent_publication_id")))
            throw new PixelMcpWriteTools.Rejected("PUBLICATION_CONFLICT","Ein neuer Pixel-Gesamtstand liegt vor. Abgleich wiederholen; keine Variante überschrieben.");
    }
}
