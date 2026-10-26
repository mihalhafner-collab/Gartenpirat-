package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.io.IOException;

/** Exercises the actual retired production boundary; all records live in isolated QA stores. */
final class DriveBackupOnlyNativeAcceptance {
    static void run(PixelMcpInstrumentation test)throws Exception {
        for(boolean restoreReview:new boolean[]{false,true})test.probe("removed Drive engine cannot execute in "+(restoreReview?"restore review":"legacy authority"),()->{
            PixelMcpInstrumentation.Isolated context=new PixelMcpInstrumentation.Isolated(test.getTargetContext());
            context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",test.fixture().toString()).putString("drive_account","qa_retired@example.invalid").putString("drive_folder_id","qa_retired_folder_123456").putString("drive_tree_uri","content://qa_retired/tree/legacy").putBoolean("drive_automatic_enabled",true).putBoolean("drive_manual_sync_requested",true).commit();
            if(restoreReview)new LocalAuthorityGuard(context).blockUnverifiedDatabase("qa_restore_retirement");
            try(PixelLocalStore store=new PixelLocalStore(context)){
                String before=PixelMcpWriteTools.hash(store.getState());
                for(JSONObject result:new JSONObject[]{DriveSyncCoordinator.run(context,store),DriveSyncCoordinator.requestExplicit(context,"qa_retired_retry")}){
                    test.yes(result.getString("code").equals("LEGACY_SYNC_RETIRED")&&!result.getBoolean("backup_started")&&!result.getBoolean("cloud_confirmed"));
                }
                test.yes(!DriveSyncCoordinator.prepareExplicit(context,"qa_retired_request")&&!DriveSyncCoordinator.automaticEnabled(context)&&!DriveSyncCoordinator.automaticAllowed(context));
                DriveSyncCoordinator.request(context,0);DriveSyncCoordinator.poll(context);
                test.yes(!DriveSyncJob.schedule(context,0)&&before.equals(PixelMcpWriteTools.hash(store.getState())));
                LocalOwnerRepository owner=new LocalOwnerRepository(context,store);
                try{owner.restoreBackup(new JSONObject(),"qa_restore_retired");throw new AssertionError("Partial restore became active");}
                catch(PixelMcpWriteTools.Rejected expected){test.yes(expected.code.equals("RESTORE_FULL_REPLACEMENT_REQUIRED"));}
                JSONObject missing=new JSONObject().put("id","qa_missing_original");
                for(PixelMcpInstrumentation.Task action:new PixelMcpInstrumentation.Task[]{()->DriveSyncCoordinator.fetchPhoto(context,missing,store.photoAttachmentStore()),()->DriveSyncCoordinator.fetchDocument(context,missing),()->DriveSyncCoordinator.fetchThumbnail(context,"qa_missing.jpg")}){
                    try{action.run();throw new AssertionError("Retired original fetch became active");}
                    catch(IOException expected){test.yes(expected.getMessage().equals("LOCAL_ORIGINAL_MISSING"));}
                }
                test.yes(before.equals(PixelMcpWriteTools.hash(store.getState())));
            }
        });
        test.probe("real UI and MCP local writes remain replayable without a Drive transport",()->{
            PixelMcpInstrumentation.Isolated context=new PixelMcpInstrumentation.Isolated(test.getTargetContext());
            context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",test.fixture().toString()).commit();
            String uiId,mcpId;
            try(PixelLocalStore store=new PixelLocalStore(context)){
                uiId=store.saveCustomer(SyntheticBusinessData.object().put("name","QA removed drive UI"));
                JSONObject args=SyntheticBusinessData.object().put("request_id","qa_removed_drive_mcp").put("name","QA removed drive MCP");
                JSONObject saved=store.execute("create_customer",args).getJSONObject("data");mcpId=saved.getString("entity_id");
                int pending=store.pendingActions().length();
                test.yes(store.execute("create_customer",args).getJSONObject("data").getBoolean("replayed")&&store.pendingActions().length()==pending);
                test.yes(!new BackupSettingsRepository(context,store).read().optBoolean("enabled"));
                test.yes(DriveSyncCoordinator.run(context,store).getString("code").equals("LEGACY_SYNC_RETIRED"));
            }
            try(PixelLocalStore reopened=new PixelLocalStore(context)){
                JSONObject all=PixelMcpReadTools.effective(reopened.getState());
                test.yes(PixelMcpWriteTools.find(all.getJSONArray("customers"),uiId).getString("name").equals("QA removed drive UI"));
                test.yes(PixelMcpWriteTools.find(all.getJSONArray("customers"),mcpId).getString("name").equals("QA removed drive MCP"));
                test.yes(!new LocalAuthorityGuard(context).current().optString("mode").equals("LOCAL_ACTIVE"));
            }
        });
    }
}
