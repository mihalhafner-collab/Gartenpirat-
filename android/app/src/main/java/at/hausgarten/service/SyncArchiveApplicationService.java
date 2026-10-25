package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** SyncArchive use cases. Effects pass through the existing owner repository transaction. */
final class SyncArchiveApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    SyncArchiveApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "empty_trash":{
                JSONObject payload=TrashPurgeBatch.payload(all,a.getString("expected_preview_hash"),a.getBoolean("confirmed"));if(TestDataRules.localOnly(new JSONObject().put("kind",TrashPurgeBatch.KIND).put("payload",payload),all))payload.put("_local_only",true);
                JSONArray targets=payload.getJSONArray("entries");for(int i=0;i<targets.length();i++){JSONObject target=targets.getJSONObject(i);requireInactiveSession(target.getString("entity"),target.getString("entity_id"));}
                JSONArray prior=repository.pendingActions(),shared=new JSONArray(),predecessors=new JSONArray();
                for(int i=0;i<prior.length();i++){JSONObject event=prior.getJSONObject(i),p=event.getJSONObject("payload");
                    if(payload.optBoolean("_local_only")||!p.optBoolean("_local_only")&&!p.optBoolean("test_record"))shared.put(event);}
                JSONObject projected=new JSONObject(repository.state().getJSONObject("master").toString());DriveJournalRules.apply(projected,shared);
                if(!payload.optBoolean("_local_only"))TrashPurgeBatch.bindDrivePreview(payload,projected);
                Set<String> incorporated=DriveJournalRules.ids(projected.optJSONArray("drive_applied_ids"));for(int i=0;i<shared.length();i++){String id=shared.getJSONObject(i).getString("client_id");if(incorporated.contains(id))predecessors.put(id);}
                if(predecessors.length()>0)payload.put("_sync_predecessors",predecessors);
                TrashPurgeBatch.apply(new JSONObject(all.toString()),new JSONObject().put("kind",TrashPurgeBatch.KIND).put("client_id",request).put("created_at",stamp).put("payload",payload));
                JSONObject backup=repository.protectDeletion(all,payload.getJSONArray("entries"),request);
                return repository.enqueue(TrashPurgeBatch.KIND,payload,request).put("deletion_backup_id",backup.getString("backup_id")).put("deleted",payload.getJSONArray("entries").length()).put("recoverable",false).put("media_cleanup","after_verified_publication");
            }
            case "archive_work_records":{
                JSONArray events=WorklogArchiveBatch.actions(all,a.getJSONArray("work_record_ids"),a.getString("expected_preview_hash"),request,stamp),saved=new JSONArray();
                for(int i=0;i<events.length();i++){JSONObject event=events.getJSONObject(i);saved.put(repository.enqueue("archive",event.getJSONObject("payload"),event.getString("client_id")));}
                return new JSONObject().put("count",saved.length()).put("actions",saved).put("recoverable",true).put("physical_delete",false).put("local_state","local_saved").put("sync_state","sync_pending");
            }
            case "close_legacy_conflicts":{
                // Local atomic decision needs its verified backup, not a successful network job.
                // No Drive I/O or business mutation occurs here; delivery remains a separate sync.
                String expected=a.getString("expected_preview_hash");repository.verifyLegacyBackup(a.getString("backup_id"),expected);LegacyConflictRules.require(repository.state(),expected);
                JSONObject preview=LegacyConflictRules.preview(repository.state());JSONArray entries=preview.getJSONArray("entries");
                // The outer SQLite transaction rolls back every queued decision if a single
                // action/current hash fails. Original actions stay present until sync ack.
                for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.getJSONObject(i);JSONObject result=perform("resolve_sync_conflict",entry,request+"_"+i);
                }
                return new JSONObject().put("decisions_queued",entries.length()).put("resolution","keep_current").put("business_data_changed",false).put("backup_id",a.getString("backup_id")).put("history_state","sync_pending");
            }
            case "request_sync":return DriveRolePolicy.retiredResponse();
            case "purge_entity":{
                requireInactiveSession(a.getString("entity"),a.getString("entity_id"));
                String kind="purge_v1";JSONObject p=new JSONObject().put("id",a.getString("entity_id")).put("expected_record_hash",a.getString("expected_record_hash")).put("updated_at",stamp);
                if(name.equals("purge_entity"))p.put("entity",a.getString("entity")).put("confirmed",a.getBoolean("confirmed"));
                OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind",kind).put("payload",p).put("client_id",request).put("created_at",stamp),new HashMap<>(),new JSONObject());
                JSONObject backup=repository.protectDeletion(all,new JSONArray().put(new JSONObject().put("entity",a.getString("entity")).put("entity_id",a.getString("entity_id")).put("expected_record_hash",a.getString("expected_record_hash"))),request);
                return repository.enqueue(kind,p,request).put("deletion_backup_id",backup.getString("backup_id"));
            }
            case "delete_entity":case "archive_entity":return archiveOne(all,a,request,stamp);
            case "restore_entity":{
                String entity=a.getString("entity"),id=a.getString("entity_id");JSONObject p=new JSONObject(a.toString()).put("id",id).put("updated_at",stamp);p.remove("request_id");p.remove("entity_id");p.put("_archive_entry",OwnerJournalRules.archives(all).getJSONObject(entity+":"+id));
                OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("client_id",request).put("kind","restore").put("payload",p).put("created_at",stamp),new HashMap<>(),new JSONObject());return repository.enqueue("restore",p,request);
            }
            case "resolve_sync_conflict":{
                JSONObject resolutionState=repository.state();JSONObject conflicts=PixelMcpOwnerTools.conflicts(resolutionState,new JSONObject().put("limit",1).put("client_id",a.getString("client_id")));JSONObject found=null;JSONArray list=conflicts.getJSONArray("conflicts");for(int i=0;i<list.length();i++)if(a.getString("client_id").equals(list.getJSONObject(i).optString("client_id")))found=list.getJSONObject(i);
                if(found!=null&&!found.optBoolean("resolution_available",false)){
                    String code=found.optString("code");
                    if("SYNC_COMPONENT_UPDATE_REQUIRED".equals(code))throw new PixelMcpWriteTools.Rejected(code,"Dieser Vorgang ist kein Datenkonflikt. Zuerst Pixel-Verarbeitung aktualisieren und erneut synchronisieren; keine Konfliktentscheidung wurde erzeugt.");
                    if("ACTION_ID_CONFLICT".equals(code))throw new PixelMcpWriteTools.Rejected(code,"Die Kontrollaktion selbst ist widersprüchlich und darf nicht durch eine weitere Konfliktentscheidung verändert werden.");
                    if("legacy_inline_photo_requires_original_review".equals(found.optString("review_reason")))throw new PixelMcpWriteTools.Rejected("LEGACY_PHOTO_REVIEW_REQUIRED","Älteres Fotojournal benötigt Prüfung des Originals. Keine Aktion verworfen oder verändert.");
                    throw new PixelMcpWriteTools.Rejected("CONFLICT_RESOLUTION_NOT_AVAILABLE","Dieser technische oder geschützte Vorgang kann nicht als normaler Datenkonflikt aufgelöst werden.");
                }if(found==null||!found.has("action"))throw new IllegalArgumentException("Konfliktaktion nicht vollständig verfügbar; nichts geändert.");JSONObject p=new JSONObject(a.toString());p.remove("request_id");JSONObject original=found.getJSONObject("action");p.put("_original_action",original);JSONObject current=found.optJSONObject("current");p.put("_current_base",current==null?JSONObject.NULL:("purge_v1".equals(original.getString("kind"))||TrashPurgeBatch.KIND.equals(original.getString("kind")))?DriveJournalRules.clean(current):DriveJournalRules.guard(current,"delete".equals(original.getString("kind"))?original.getJSONObject("payload").getString("entity"):original.getString("kind")));Map<String,JSONObject> actions=new HashMap<>();actions.put(a.getString("client_id"),found.getJSONObject("action"));OwnerJournalRules.apply(SnapshotProjection.effective(resolutionState),new JSONObject().put("kind","conflict_resolution").put("client_id",request).put("payload",p),actions,new JSONObject());return repository.enqueue("conflict_resolution",p,request);
            }
            case "create_backup":return repository.createBackup(request);
            case "restore_backup":return repository.restoreBackup(a,request);
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
    private void requireInactiveSession(String entity,String id)throws Exception {
        ActiveWorkProtection.require(repository.state(),entity,id);
    }
    private JSONObject archiveOne(JSONObject all,JSONObject args,String request,String stamp)throws Exception {
        requireInactiveSession(args.getString("entity"),args.getString("entity_id"));
        if(args.getString("entity").equals("appointment")){JSONObject session=repository.state().getJSONObject("work_session");if(args.getString("entity_id").equals(session.optString("appointment_id"))&&!Arrays.asList("IDLE","FINALIZED","CANCELLED").contains(session.optString("state")))throw new IllegalArgumentException("Zeiterfassung dieses Termins zuerst abschließen oder bewusst verwerfen. Nichts archiviert.");}
        JSONObject p=DeletionRules.payload(all,args.getString("entity"),args.getString("entity_id"),args.getString("expected_record_hash"),stamp);
        if(p.getJSONArray("photo_link_ids").length()>0&&!args.has("expected_photo_links_hash"))throw new PixelMcpWriteTools.Rejected("PREVIEW_REQUIRED","Fotozuordnungen zuerst mit preview_delete prüfen.");
        if(args.has("expected_photo_links_hash")&&!args.getString("expected_photo_links_hash").equals(p.getString("expected_photo_links_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Fotozuordnungen wurden geändert.");
        OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind","archive").put("client_id",request).put("payload",p).put("created_at",stamp),new HashMap<>(),new JSONObject());return repository.enqueue("archive",p,request).put("recoverable",true).put("physical_delete",false);
    }
}
