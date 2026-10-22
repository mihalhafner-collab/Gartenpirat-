package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Snapshot/ack is one atomic storage call. Later status persistence cannot undo that commit. */
final class SyncCommitRules {
    interface Storage { void commitSnapshot(JSONObject snapshot,Set<String> confirmed)throws Exception; boolean saveStatus()throws Exception; }
    static final class Result {
        final Set<String> confirmed;final boolean statusSaved;
        Result(Set<String> ids,boolean saved){confirmed=Collections.unmodifiableSet(ids);statusSaved=saved;}
    }
    static Result commit(JSONObject snapshot,Storage storage)throws Exception{
        Set<String> ids=DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids"));
        ids.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
        ids.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")));
        JSONObject resolutions=snapshot.optJSONObject("drive_resolved_actions");if(resolutions!=null)resolutions.keys().forEachRemaining(ids::add);
        storage.commitSnapshot(snapshot,ids);
        boolean saved;try{saved=storage.saveStatus();}catch(Exception statusFailure){saved=false;}
        return new Result(ids,saved);
    }
    private SyncCommitRules(){}
}
