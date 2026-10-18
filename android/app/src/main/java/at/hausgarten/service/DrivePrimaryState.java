package at.hausgarten.service;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable Pixel snapshots in the EXISTING Drive folder. No mutable master overwrite. */
final class DrivePrimaryState {
    interface Storage {
        Map<String,String> commits()throws Exception; // immutable document ID -> name
        byte[] read(String id)throws Exception;
    }
    static JSONObject recover(JSONObject legacy,String publisher,Storage storage)throws Exception {
        JSONArray summaries=new JSONArray();Map<String,JSONObject> manifests=new HashMap<>();
        for(Map.Entry<String,String> file:storage.commits().entrySet()) {
            String name=file.getValue();if(!name.matches("HausGarten-Commit-[0-9a-f]{64}\\.json"))throw new IllegalArgumentException("PUBLICATION_INVALID: Ungültige Veröffentlichungsreferenz.");
            byte[] bytes=storage.read(file.getKey());
            if(!name.equals("HausGarten-Commit-"+DriveRestClient.sha256(bytes)+".json"))throw new IllegalArgumentException("PUBLICATION_INVALID: Veröffentlichungsbeleg verändert.");
            JSONObject m=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!"hausgarten.drive.commit.v1".equals(m.optString("format")))throw new IllegalArgumentException("PUBLICATION_INVALID: Unbekanntes Belegformat.");
            summaries.put(new JSONObject().put("id",file.getKey()).put("publisher",m.getString("publisher_id"))
                .put("sha",m.getString("state_sha256")).put("sequence",m.getLong("sequence"))
                .put("parent",m.optString("previous_publication_id")).put("parent_sha",m.optString("previous_state_sha256")));
            manifests.put(file.getKey(),m);
        }
        JSONObject head=DrivePublication.head(summaries,publisher),state=new JSONObject(legacy.toString());
        String legacyHash=PixelMcpWriteTools.hash(legacy);
        if(head.length()>0) {
            JSONObject m=manifests.get(head.getString("id"));byte[] bytes=storage.read(m.getString("state_file_id"));
            DrivePublication.verify(m,bytes,new JSONArray());state=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(state.has("drive_legacy_base_hash")&&!legacyHash.equals(state.getString("drive_legacy_base_hash")))
                throw new PixelMcpWriteTools.Rejected("LEGACY_SOURCE_CHANGED","Der ursprüngliche Drive-Gesamtstand wurde parallel verändert. Beide Stände bleiben erhalten; Migration ausdrücklich prüfen.");
        }
        PixelDriveState.prepare(state);
        state.put("drive_legacy_base_hash",legacyHash).put("drive_parent_publication_id",head.optString("id"))
            .put("drive_parent_state_sha256",head.optString("sha")).put("drive_parent_sequence",head.optLong("sequence"));
        return state;
    }
    static void checkParent(JSONObject state,JSONObject current)throws Exception {
        if(!state.optString("drive_parent_publication_id").equals(current.optString("drive_parent_publication_id")))
            throw new PixelMcpWriteTools.Rejected("PUBLICATION_CONFLICT","Ein neuer Pixel-Gesamtstand liegt vor. Abgleich wiederholen; keine Variante überschrieben.");
    }
}
