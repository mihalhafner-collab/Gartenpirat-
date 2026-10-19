package at.hausgarten.service;
import org.json.*;

/** Backup safety is about a verified readable publication, not the choice of Drive transport. */
final class LegacyBackupPolicy {
    static void require(JSONObject state,boolean connected,boolean permission,boolean failed,boolean running,String scope)throws Exception {
        require(state,connected,permission,failed,running,scope,"","");
    }
    static void require(JSONObject state,boolean connected,boolean permission,boolean failed,boolean running,String scope,String error,String expectedPreview)throws Exception {
        if(!connected||!permission)throw new IllegalArgumentException("Bestehenden Drive-Ordner freigeben. Keine Altentscheidung ausgeführt.");
        boolean recovery=failed&&"LEGACY_SOURCE_CHANGED".equals(error)&&!expectedPreview.isEmpty();
        if(recovery)LegacyConflictRules.require(state,expectedPreview);
        if((failed&&!recovery)||running||!java.util.Arrays.asList("provider_readback","drive_server_manifest").contains(scope))throw new IllegalArgumentException("Zuerst den laufenden Drive-Abgleich abschließen. Keine Altentscheidung ausgeführt.");
        if(LegacyConflictRules.preview(state).getInt("count")!=7||state.getJSONObject("sync_queue").getInt("total")!=7)throw new IllegalArgumentException("Zuerst technische und neue Aktionen prüfen; erwartet sind genau sieben historische Prüfungen.");
    }
    private LegacyBackupPolicy(){}
}
