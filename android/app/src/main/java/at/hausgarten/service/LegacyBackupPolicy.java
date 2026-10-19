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
        int count=LegacyConflictRules.preview(state).getInt("count");if(count<1||count>100||state.getJSONObject("sync_queue").getInt("total")!=count)throw new IllegalArgumentException("Zuerst technische und neue Aktionen prüfen; die gesamte aktive Auswahl muss historisch und eindeutig sein.");
        if(count!=7&&expectedPreview.isEmpty())throw new PixelMcpWriteTools.Rejected("PREVIEW_REQUIRED","Geänderte Auswahl zuerst neu lesen und mit aktuellem Vorschauhash binden. Keine automatische Übernahme einer alten Siebener-Auswahl.");
        if(!expectedPreview.isEmpty())LegacyConflictRules.require(state,expectedPreview);
    }
    private LegacyBackupPolicy(){}
}
