package at.hausgarten.service;

import org.json.*;

/** User-requested deletion means recoverable removal in the existing owner archive. */
final class DeletionRules {
    static final String[] ENTITIES={"customer","order","worklog","photo","invoice","offer","service_template"};
    static boolean cancelable(JSONObject action,String code) {
        return action!=null&&"archive".equals(action.optString("kind"))
            &&java.util.Arrays.asList("WRITE_CONFLICT","INVALID_ACTION","DEPENDENCY_BLOCKED").contains(code)
            &&action.optJSONObject("payload")!=null&&java.util.Arrays.asList(ENTITIES).contains(action.optJSONObject("payload").optString("entity"));
    }
    static JSONObject preview(JSONObject snapshot,String entity,String id)throws Exception {
        if(!java.util.Arrays.asList(ENTITIES).contains(entity))throw new IllegalArgumentException("Dieser Bereich kann nicht gelöscht werden.");
        JSONObject current=OwnerJournalRules.findOrNull(snapshot.optJSONArray(OwnerJournalRules.group(entity)),id);
        if(current==null)throw new IllegalArgumentException("Der Eintrag fehlt oder liegt bereits im Papierkorb.");
        JSONObject out=new JSONObject().put("entity",entity).put("entity_id",id).put("expected_record_hash",OwnerJournalRules.version(current))
            .put("recoverable",true).put("physical_delete",false).put("cascade",false).put("updated_at",current.optString("updated_at"))
            .put("mode",entity.equals("customer")||entity.equals("service_template")?"deactivate":"trash");
        JSONObject archived=snapshot.optJSONObject("owner_archive");if(archived!=null&&archived.has(entity+":"+id))return out.put("allowed",false).put("message","Der Eintrag liegt bereits im Papierkorb.");
        try{OwnerJournalRules.validateArchive(entity,current,snapshot);out.put("allowed",true).put("message","Aus aktiven Ansichten entfernen. Original und Verknüpfungen bleiben im Papierkorb wiederherstellbar.");}
        catch(IllegalArgumentException blocked){out.put("allowed",false).put("message",blocked.getMessage());}
        return out;
    }
    static JSONObject payload(JSONObject snapshot,String entity,String id,String expected,String stamp)throws Exception {
        JSONObject current=OwnerJournalRules.findOrNull(snapshot.getJSONArray(OwnerJournalRules.group(entity)),id);
        OwnerJournalRules.checkHash(current,expected);if(current==null)throw new IllegalArgumentException("Der Eintrag fehlt.");
        OwnerJournalRules.validateArchive(entity,current,snapshot);
        return new JSONObject().put("entity",entity).put("id",id).put("expected_record_hash",expected)
            .put("_record_base",DriveJournalRules.guard(current,entity)).put("_archive_record",current)
            .put("deletion_policy","recoverable_v1").put("updated_at",stamp);
    }
    private DeletionRules(){}
}
