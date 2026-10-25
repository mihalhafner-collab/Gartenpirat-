package at.hausgarten.service;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Actual Android SQLite/preferences + serialized journal tests. No cloud or productive writes. */
final class NativeDriveAcceptance {
    static void run(PixelMcpInstrumentation qa)throws Exception{
        qa.probe("native offline new customer and later edit share consistent defaults",()->{
            PixelMcpInstrumentation.Isolated ctx=new PixelMcpInstrumentation.Isolated(qa.getTargetContext());JSONObject base=qa.fixture().put("photos",new JSONArray());ctx.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",base.toString()).commit();
            try(PixelLocalStore local=new PixelLocalStore(ctx)){String id=local.saveCustomer(SyntheticBusinessData.object().put("name","QA Offline Defaults"));local.saveCustomer(SyntheticBusinessData.object().put("id",id).put("name","QA Offline Defaults").put("notes","QA Nachtrag"));qa.yes(DriveJournalRules.apply(base,local.pendingActionsForDrive()).length()==0);JSONObject row=PixelMcpWriteTools.find(base.getJSONArray("customers"),id);qa.yes(row.getString("notes").equals("QA Nachtrag")&&row.getInt("active")==1&&row.getJSONObject("custom_fields").length()==0);}
        });
        qa.probe("native manual edits retain a guarded offline action chain",()->{
            PixelMcpInstrumentation.Isolated ctx=new PixelMcpInstrumentation.Isolated(qa.getTargetContext());
            JSONObject base=qa.fixture().put("photos",new JSONArray());ctx.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",base.toString()).commit();
            try(PixelLocalStore local=new PixelLocalStore(ctx)){
                String id=local.saveWorklog(SyntheticBusinessData.object().put("customer_id","qa_c1").put("activity","QA Offline").put("hours",2).put("work_date","2026-10-12"));
                local.saveWorklog(SyntheticBusinessData.object().put("id",id).put("customer_id","qa_c1").put("activity","QA Offline").put("hours",3).put("work_date","2026-10-12").put("notes","QA Ergänzung"));
                JSONArray actions=local.pendingActionsForDrive();qa.yes(actions.length()==2);
                qa.yes(actions.getJSONObject(1).getJSONObject("payload").has("_sync_base"));
                qa.yes(DriveJournalRules.apply(base,actions).length()==0);
                qa.yes(base.getJSONArray("worklogs").length()==1&&base.getJSONArray("worklogs").getJSONObject(0).getDouble("hours")==3);
            }
        });
        qa.probe("native serialized legacy journal restart exposes local state and preserves historical transfer proof without cloud claim",()->{
            PixelMcpInstrumentation.Isolated ctx=new PixelMcpInstrumentation.Isolated(qa.getTargetContext());SharedPreferences prefs=ctx.getSharedPreferences("haus_garten",0);
            JSONObject base=qa.fixture().put("photos",new JSONArray());prefs.edit().putString("drive_snapshot",base.toString()).commit();
            String client,id;JSONArray actions;
            try(PixelLocalStore local=new PixelLocalStore(ctx)){
                JSONObject result=local.execute("create_customer_work_record",qa.args("qa-drive-restart")).getJSONObject("data");
                id=result.getString("work_record_id");client=result.getJSONObject("sync").getString("client_id");actions=local.pendingActionsForDrive();
                File folder=new File(ctx.getFilesDir(),ctx.prefix+"journal-test");qa.yes(folder.mkdir());File file=new File(folder,"journal.json");
                byte[] bytes=SyntheticBusinessData.object().put("format",1).put("actions",actions).toString().getBytes(StandardCharsets.UTF_8);
                try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}
                byte[] received=java.nio.file.Files.readAllBytes(file.toPath());qa.yes(java.util.Arrays.equals(bytes,received));
                actions=SyntheticBusinessData.object(new String(received,StandardCharsets.UTF_8)).getJSONArray("actions");
                qa.yes(DriveJournalRules.apply(base,actions).length()==0);
                qa.yes(prefs.edit().putString("drive_snapshot",base.toString()).commit());local.markUploaded(actions);
                local.acknowledge(DriveJournalRules.ids(base.getJSONArray("drive_applied_ids")));
            }
            try(PixelLocalStore reopened=new PixelLocalStore(ctx)){
                qa.yes(reopened.pendingActions().length()==0);
                qa.yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs"),id).getString("customer_id").equals("qa_c1"));
                JSONObject status=reopened.status(client,reopened.getState()),historical=status.getJSONObject("historical_sync_inventory");qa.yes(historical.getBoolean("applied_on_pixel")&&historical.getString("status").equals("transferred")&&historical.getBoolean("provider_readback")&&!historical.getBoolean("cloud_confirmed"));
                qa.yes(status.getString("status").equals("local_saved")&&status.getString("sync_state").equals("not_applicable")&&status.getString("drive_role").equals("backup_only")&&status.getInt("storage_contract_version")==3&&!status.getBoolean("applied_on_pixel")&&!status.getBoolean("cloud_confirmed")&&!status.getBoolean("uploaded_to_drive")&&!status.getBoolean("confirmed_by_app"));
            }
        });
        qa.probe("native restart replays journals on older snapshot without losing IDs",()->{
            JSONObject base=qa.fixture().put("photos",new JSONArray());JSONObject action=SyntheticBusinessData.object().put("client_id","qa-journal-replay").put("kind","worklog").put("payload",SyntheticBusinessData.object().put("id","qa_journal_w").put("customer_id","qa_c1").put("work_date","2026-10-12").put("hours",2).put("activity","QA Replay"));
            JSONArray journal=new JSONArray().put(action);JSONObject first=SyntheticBusinessData.object(base.toString());qa.yes(DriveJournalRules.apply(first,journal).length()==0);
            JSONObject restarted=SyntheticBusinessData.object(base.toString());qa.yes(DriveJournalRules.apply(restarted,journal).length()==0);
            qa.yes(first.getJSONArray("worklogs").getJSONObject(0).getString("id").equals(restarted.getJSONArray("worklogs").getJSONObject(0).getString("id")));
            DriveJournalRules.apply(restarted,journal);qa.yes(restarted.getJSONArray("worklogs").length()==1);
        });
        qa.probe("native conflict retains local queue and remote value",()->{
            PixelMcpInstrumentation.Isolated ctx=new PixelMcpInstrumentation.Isolated(qa.getTargetContext());JSONObject base=qa.fixture().put("photos",new JSONArray());
            base.getJSONArray("customers").getJSONObject(0).put("notes","Original").put("updated_at","2026-10-12T08:00:00Z");ctx.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",base.toString()).commit();
            try(PixelLocalStore local=new PixelLocalStore(ctx)){
                local.saveCustomer(SyntheticBusinessData.object().put("id","qa_c1").put("name","QA Kunde").put("notes","Pixel Änderung"));
                base.getJSONArray("customers").getJSONObject(0).put("notes","Web Änderung");JSONArray errors=DriveJournalRules.apply(base,local.pendingActionsForDrive());
                qa.yes(errors.length()==1&&errors.getJSONObject(0).getString("code").equals("WRITE_CONFLICT"));qa.yes(local.pendingActions().length()==1&&base.getJSONArray("customers").getJSONObject(0).getString("notes").equals("Web Änderung"));
            }
        });
        finalRuntimeBoundaries(qa);
    }
    private static void finalRuntimeBoundaries(PixelMcpInstrumentation qa)throws Exception {
        for(boolean localMode:new boolean[]{false,true})qa.probe("final backup-only app boundaries preserve "+(localMode?"local locked":"unmigrated legacy")+" data with zero business provider and account I/O",()->{
            try(DriveProviderAcceptance.Setup setup=new DriveProviderAcceptance.Setup(qa)){
                if(localMode){LocalAuthorityGuard guard=new LocalAuthorityGuard(setup.io);JSONObject evidence=new JSONObject().put("formatVersion",1).put("external_writers_disabled",true).put("evidence_sha256",PixelMcpWriteTools.hash("qa_final_boundary_no_external_writer")).put("evidence_reference","qa_isolated_final_boundary").put("checked_at",java.time.Instant.now().toString());try(AutoCloseable ignored=LocalAuthorityGuard.exclusiveStartup()){JSONObject begun=guard.beginMigration("qa_final_boundary_activate",guard.current().getString("revision"),evidence),contract=new JSONObject().put("formatVersion",1).put("migration_epoch",begun.getString("migration_epoch"));setup.store.putOwnerValue("local_authority_contract",contract.toString());setup.store.commitLocalSnapshot(setup.store.getState().getJSONObject("master"));guard.finishMigration("qa_final_boundary_activate",contract);}}
                setup.store.saveCustomer(new JSONObject().put("id","qa_final_boundary_existing").put("name","QA preserved queued customer"));
                qa.yes(setup.prefs.edit().putString("drive_transport","rest").putString("drive_account","qa_owner@example.invalid").putString("drive_folder_id","qa_final_backup_folder_12345").putBoolean("drive_automatic_enabled",true).putBoolean("drive_manual_sync_requested",true).commit());
                final int[] accountOrNetwork={0};Context boundary=new ContextWrapper(setup.io){@Override public Context getApplicationContext(){return this;}@Override public Object getSystemService(String name){if(Context.ACCOUNT_SERVICE.equals(name)||Context.CONNECTIVITY_SERVICE.equals(name)){accountOrNetwork[0]++;throw new AssertionError("QA_FINAL_BUSINESS_NETWORK_SERVICE_TOUCHED");}if(Context.JOB_SCHEDULER_SERVICE.equals(name))return null;return super.getSystemService(name);}};
                String preferences=PixelMcpWriteTools.hash(new JSONObject(setup.prefs.getAll())),queue=PixelMcpWriteTools.hash(setup.store.pendingActions()),snapshot=setup.store.snapshotText("{}"),authority=PixelMcpWriteTools.hash(new LocalAuthorityGuard(boundary).current());int providerQueries=setup.provider.queries;
                qa.yes(!DriveRolePolicy.businessSyncAllowed()&&!DriveSyncCoordinator.automaticEnabled(boundary)&&!DriveSyncCoordinator.automaticAllowed(boundary)&&!DriveSyncCoordinator.prepareExplicit(boundary,"qa_final_no_explicit_sync"));
                JSONObject direct=DriveSyncCoordinator.run(boundary,setup.store),explicit=DriveSyncCoordinator.requestExplicit(boundary,"qa_final_retired_explicit");for(JSONObject result:new JSONObject[]{direct,explicit})qa.yes(result.getString("code").equals("LEGACY_SYNC_RETIRED")&&result.getString("drive_role").equals("backup_only")&&result.getInt("storage_contract_version")==3&&!result.getBoolean("backup_started")&&!result.getBoolean("cloud_confirmed"));
                DriveSyncCoordinator.request(boundary,0);DriveSyncCoordinator.poll(boundary);DriveSyncJob.install(boundary);qa.yes(!DriveSyncJob.schedule(boundary,0));
                PixelOwnerAccess access=new PixelOwnerAccess(boundary,setup.store);for(String tool:new String[]{"request_sync","prepare_legacy_conflict_backup","close_legacy_conflicts"}){JSONObject args=new JSONObject().put("request_id","qa_final_retired_"+tool);if(!tool.equals("request_sync"))args.put("expected_preview_hash",PixelMcpInstrumentation.repeat("a",64));if(tool.equals("close_legacy_conflicts"))args.put("backup_id","qa_unissued_legacy_backup").put("confirmed",true);JSONObject result=access.execute(tool,args).getJSONObject("data");qa.yes(result.getString("code").equals("LEGACY_SYNC_RETIRED")&&!result.getBoolean("backup_started"));}
                try{access.execute("request_invoice_issue",new JSONObject().put("request_id","qa_final_locked_invoice").put("invoice_id","qa_final_unissued_invoice").put("expected_record_hash",PixelMcpInstrumentation.repeat("a",64)).put("expected_context_hash",PixelMcpInstrumentation.repeat("b",64)).put("confirmed",true));throw new AssertionError("QA_FINAL_INVOICE_AUTHORITY_NOT_BYPASSED");}catch(PixelMcpWriteTools.Rejected rejected){qa.yes(!localMode&&rejected.code.equals("LOCAL_AUTHORITY_REQUIRED"));}catch(IOException rejected){qa.yes(localMode&&rejected.getMessage().equals("INVOICE_AUTHORITY_RECONCILIATION_REQUIRED"));}
                JSONObject media=new JSONObject().put("id","qa_missing_original");for(PixelMcpInstrumentation.Task fetch:new PixelMcpInstrumentation.Task[]{()->DriveSyncCoordinator.fetchPhoto(boundary,media,setup.store.photoAttachmentStore()),()->DriveSyncCoordinator.fetchDocument(boundary,media),()->DriveSyncCoordinator.fetchThumbnail(boundary,PixelMcpInstrumentation.repeat("a",64)+".jpg")}){try{fetch.run();throw new AssertionError("QA_FINAL_ORIGINAL_NETWORK_FALLBACK");}catch(IOException missing){qa.yes(missing.getMessage().equals("LOCAL_ORIGINAL_MISSING"));}}
                qa.yes(PixelMcpWriteTools.hash(new JSONObject(setup.prefs.getAll())).equals(preferences)&&PixelMcpWriteTools.hash(setup.store.pendingActions()).equals(queue)&&setup.store.snapshotText("{}").equals(snapshot)&&PixelMcpWriteTools.hash(new LocalAuthorityGuard(boundary).current()).equals(authority));
                int before=setup.store.pendingActions().length();BusinessDataBridge bridge=new BusinessDataBridge(boundary,setup.store,new PendingWorkPhotoStore(boundary),()->DriveSyncCoordinator.request(boundary,0));String payload=new JSONObject().put("_request_id","qa_final_local_customer_action").put("id","qa_final_new_offline_customer").put("name","QA new offline customer").toString();qa.yes(bridge.saveCustomer(payload).equals("qa_final_new_offline_customer")&&bridge.saveCustomer(payload).equals("qa_final_new_offline_customer")&&setup.store.pendingActions().length()==before+1);qa.yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(setup.store.getState()).getJSONArray("customers"),"qa_final_new_offline_customer").getString("name").equals("QA new offline customer"));
                qa.yes(PixelMcpWriteTools.hash(new JSONObject(setup.prefs.getAll())).equals(preferences)&&PixelMcpWriteTools.hash(new LocalAuthorityGuard(boundary).current()).equals(authority)&&setup.provider.queries==providerQueries&&!setup.provider.written&&accountOrNetwork[0]==0);
            }
        });
    }
}
