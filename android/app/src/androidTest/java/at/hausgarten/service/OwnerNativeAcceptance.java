package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Prepared device acceptance only. Never invokes a production store/provider or tunnel. */
final class OwnerNativeAcceptance {
    static final class Fixture implements AutoCloseable {
        final PixelMcpInstrumentation.Isolated context;
        final PixelLocalStore store;
        final McpAuditTools tools;
        Fixture(Context base)throws Exception{
            context=new PixelMcpInstrumentation.Isolated(base);
            JSONObject snapshot=SyntheticBusinessData.object("{customers:[{id:'qa_c',name:'QA Eigentümer',active:1,billing_mode:'direct',updated_at:'2026-10-18T08:00:00Z'}],orders:[],worklogs:[{id:'qa_w',customer_id:'qa_c',billing_customer_id:'qa_c',work_date:'2026-10-18',activity:'QA Pflege',hours:2,break_minutes:0,kilometers:0,billed:0,customer_visible:1,notes:'Original',updated_at:'2026-10-18T08:00:00Z'}],invoices:[],photos:[],capabilities:{entity_compare_and_save:true,worklog_compare_and_save:true,customer_custom_fields_compare_and_save:true,invoice_compare_and_save:true,photo_compare_and_save:true,owner_journal_v2:true}}");
            JSONObject config=SyntheticBusinessData.object();for(String key:PixelMcpOwnerTools.BUSINESS_KEYS)config.put(key,"");snapshot.put("business_settings",config);
            context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",snapshot.toString()).commit();store=new PixelLocalStore(context);tools=new McpAuditTools(store::getState,store,new PixelOwnerAccess(context,store));
        }
        JSONObject call(String n,JSONObject a)throws Exception{return tools.call(n,a).getJSONObject("data");}
        public void close(){store.close();}
    }
    static JSONObject request(String id)throws Exception{return SyntheticBusinessData.object().put("request_id",id);}
    static void rejectedDocument(PixelMcpInstrumentation test,PixelMcpInstrumentation.Task task)throws Exception{
        try{task.run();}catch(java.io.IOException|IllegalArgumentException expected){test.yes(true);return;}throw new AssertionError("Changed document must be rejected");
    }
    static void run(PixelMcpInstrumentation test)throws Exception{
        test.probe("active work prevents customer archive and leaves the running session intact",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            f.store.startWorkSession(SyntheticBusinessData.object().put("request_id","qa-active-cleanup-guard").put("customer_id","qa_c").put("activity","QA active work").put("work_date","2026-10-22"));
            JSONObject preview=f.call("preview_delete",SyntheticBusinessData.object().put("entity","customer").put("entity_id","qa_c"));
            try{f.call("delete_entity",request("qa-active-archive").put("entity","customer").put("entity_id","qa_c").put("expected_record_hash",preview.getString("expected_record_hash")));throw new AssertionError("active work customer archived");}catch(PixelMcpWriteTools.Rejected e){test.yes(e.code.equals("ACTIVE_WORK_SESSION"));}
            test.yes(f.store.getState().getJSONObject("work_session").getString("state").equals("RUNNING"));test.yes(f.store.pendingActions().length()==0);
        }});
        test.probe("historically shared synthetic customer removal keeps fresh versioned Drive actions",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject base=new JSONObject(f.store.snapshotText("{}"));base.getJSONArray("customers").put(SyntheticBusinessData.object().put("id","qa_old_shared_test").put("name","[TEST] QA old shared").put("active",0).put("updated_at","qa_t1"));f.store.commitSnapshot(base,new HashSet<>());
            String id="qa_old_shared_test";JSONObject preview=f.call("preview_delete",SyntheticBusinessData.object().put("entity","customer").put("entity_id",id));f.call("delete_entity",request("qa-old-shared-archive").put("entity","customer").put("entity_id",id).put("expected_record_hash",preview.getString("expected_record_hash")));
            JSONObject purge=f.call("preview_purge",SyntheticBusinessData.object().put("entity","customer").put("entity_id",id));f.call("purge_entity",request("qa-old-shared-purge").put("entity","customer").put("entity_id",id).put("expected_record_hash",purge.getString("expected_record_hash")).put("confirmed",true));
            JSONArray outgoing=f.store.pendingActionsForDrive();test.yes(outgoing.length()==2);test.yes(f.store.pendingActions().length()==2);JSONObject replay=new JSONObject(base.toString());DriveJournalRules.apply(replay,outgoing);test.yes(TrashRules.purged(replay,"customer",id));test.yes(OwnerJournalRules.findOrNull(replay.getJSONArray("customers"),id)==null);test.yes(f.store.compactPurgedTestActions()==0);Set<String> confirmed=new HashSet<>();for(int i=0;i<outgoing.length();i++)confirmed.add(outgoing.getJSONObject(i).getString("client_id"));f.store.commitSnapshot(replay,confirmed);JSONObject cloudReceipt=OwnerJournalRules.archives(replay).getJSONObject("customer:"+id),viewReceipt=OwnerJournalRules.archives(f.store.getState().getJSONObject("master")).getJSONObject("customer:"+id);test.yes(OwnerJournalRules.version(cloudReceipt).equals(OwnerJournalRules.version(viewReceipt)));test.yes(!viewReceipt.has("local_test_only")&&!viewReceipt.has("source_record_hash"));
        }});

        test.probe("purged local test journals compact atomically and never recreate records after restart",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            String real=PixelMcpWriteTools.hash(f.call("get_customer",SyntheticBusinessData.object().put("customer_id","qa_c")));
            String id=f.store.saveCustomer(SyntheticBusinessData.object().put("name","[TEST] QA temporary").put("test_record",true));
            JSONObject preview=f.call("preview_delete",SyntheticBusinessData.object().put("entity","customer").put("entity_id",id));
            f.call("delete_entity",request("qa-test-cleanup-archive").put("entity","customer").put("entity_id",id).put("expected_record_hash",preview.getString("expected_record_hash")));
            JSONObject purge=f.call("preview_purge",SyntheticBusinessData.object().put("entity","customer").put("entity_id",id));
            JSONObject args=request("qa-test-cleanup-purge").put("entity","customer").put("entity_id",id).put("expected_record_hash",purge.getString("expected_record_hash")).put("confirmed",true);
            JSONObject removed=f.call("purge_entity",args);test.yes(f.store.pendingActions().length()==0);test.yes(f.store.pendingActionsForDrive().length()==0);test.yes(f.call("purge_entity",args).getBoolean("replayed"));
            try(PixelLocalStore reopened=new PixelLocalStore(f.context)){JSONObject all=PixelMcpReadTools.effective(reopened.getState());test.yes(OwnerJournalRules.findOrNull(all.getJSONArray("customers"),id)==null);test.yes(TrashRules.purged(all,"customer",id));test.yes(!OwnerJournalRules.archives(all).getJSONObject("customer:"+id).has("record"));}
            test.yes(real.equals(PixelMcpWriteTools.hash(f.call("get_customer",SyntheticBusinessData.object().put("customer_id","qa_c")))));test.yes(f.call("inspect_backup",SyntheticBusinessData.object().put("backup_id",removed.getString("deletion_backup_id"))).getInt("protected_records")==1);
        }});

        test.probe("refactored public bridge preserves inherited actions and isolated business writes",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            int inherited=0,business=0;for(java.lang.reflect.Method method:MainActivity.DataBridge.class.getMethods())if(method.isAnnotationPresent(android.webkit.JavascriptInterface.class))inherited++;
            for(java.lang.reflect.Method method:BusinessDataBridge.class.getMethods())if(method.isAnnotationPresent(android.webkit.JavascriptInterface.class))business++;
            test.yes(inherited==52&&business==35);
            java.util.concurrent.atomic.AtomicInteger sync=new java.util.concurrent.atomic.AtomicInteger();
            BusinessDataBridge bridge=new BusinessDataBridge(f.context,f.store,new PendingWorkPhotoStore(f.context),sync::incrementAndGet);
            test.yes(new JSONObject(bridge.getState()).getBoolean("ui_effective"));
            test.yes(new JSONObject(bridge.ownerCall("get_settings","{}")).getBoolean("ok"));
            test.yes(!new JSONObject(bridge.ownerCall("qa_unknown_operation","{}")).getBoolean("ok"));
            test.yes(bridge.migratePersonalPaid("[]").equals("OK"));test.yes(bridge.getPersonalPaid().equals("[]"));
            test.yes(bridge.setPersonalPaid("[\"qa_missing\"]").startsWith("ERROR:"));test.yes(bridge.getPersonalPaid().equals("[]"));
            String saved=bridge.saveCustomer(SyntheticBusinessData.object().put("name","[TEST] QA Bridgekunde").put("test_record",true).toString());test.yes(!saved.startsWith("ERROR:"));test.yes(sync.get()==1);
            test.yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("customers"),saved).getString("name").equals("[TEST] QA Bridgekunde"));
        }});
        test.probe("empty trash is one atomic durable request for 136 entries and survives restart",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master");PixelDriveState.prepare(snapshot);JSONObject archive=OwnerJournalRules.archives(snapshot);
            for(int i=0;i<136;i++)archive.put("worklog:qa_trash-"+i,SyntheticBusinessData.object().put("record",SyntheticBusinessData.object().put("id","qa_trash-"+i).put("customer_id","qa_c").put("work_date","2026-07-31").put("activity","Synthetic trash").put("hours",1).put("billed",0)).put("archive_action","synthetic-archive-"+i).put("archived_at","2026-08-01"));
            f.store.commitSnapshot(snapshot,new HashSet<>());JSONObject preview=f.call("preview_empty_trash",SyntheticBusinessData.object());test.yes(preview.getInt("count")==136);
            JSONObject args=request("native-empty-136").put("expected_preview_hash",preview.getString("preview_hash")).put("confirmed",true);
            f.store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER abort_empty BEFORE INSERT ON pending WHEN NEW.kind='purge_trash_v1' BEGIN SELECT RAISE(ABORT,'synthetic crash'); END");
            try{f.call("empty_trash",args);throw new AssertionError("Expected synthetic transaction abort");}catch(android.database.sqlite.SQLiteException expected){test.yes(true);}
            test.yes(f.store.pendingActions().length()==0&&f.call("list_archive",SyntheticBusinessData.object()).getInt("total")==136);f.store.getWritableDatabase().execSQL("DROP TRIGGER abort_empty");
            test.yes(f.call("empty_trash",args).getInt("deleted")==136);test.yes(f.call("empty_trash",args).getBoolean("replayed"));test.yes(f.store.pendingActions().length()==1&&f.call("list_archive",SyntheticBusinessData.object()).getInt("total")==0);
            JSONObject committed=f.store.getState().getJSONObject("master");test.yes(DriveJournalRules.apply(committed,f.store.pendingActions()).length()==0);f.store.commitSnapshot(committed,DriveJournalRules.ids(committed.getJSONArray("drive_applied_ids")));f.store.close();
            try(PixelLocalStore reopened=new PixelLocalStore(f.context)){test.yes(reopened.pendingActions().length()==0&&PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs").length()==1);test.yes(TrashPurgeBatch.preview(PixelMcpReadTools.effective(reopened.getState())).getInt("total")==0);}
        }});
        test.probe("empty trash shares exact selection while private customer journals stay local",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master");PixelDriveState.prepare(snapshot);
            OwnerJournalRules.archives(snapshot).put("worklog:qa_shared_trash",SyntheticBusinessData.object().put("record",SyntheticBusinessData.object()
                .put("id","qa_shared_trash").put("customer_id","qa_c").put("activity","QA isolated archive").put("work_date","2026-07-31").put("hours",1).put("billed",0)).put("archived_at","2026-08-01"));
            f.store.commitSnapshot(snapshot,new HashSet<>());String active=PixelMcpWriteTools.hash(snapshot.getJSONArray("customers"));
            String local=f.store.saveCustomer(SyntheticBusinessData.object().put("name","[TEST] QA private bulk customer").put("test_record",true));
            JSONObject preview=f.call("preview_empty_trash",SyntheticBusinessData.object());test.yes(preview.getInt("count")==1);
            JSONObject args=request("qa_native_private_bulk").put("expected_preview_hash",preview.getString("preview_hash")).put("confirmed",true);
            test.yes(f.call("empty_trash",args).getInt("deleted")==1);test.yes(f.call("empty_trash",args).getBoolean("replayed"));
            test.yes(f.store.pendingActionsForDrive().length()==1&&f.call("list_archive",SyntheticBusinessData.object()).getInt("total")==0);
            JSONArray shared=f.store.pendingActionsForDrive();test.yes(shared.getJSONObject(0).getJSONObject("payload").has("_drive_preview_hash"));
            JSONObject remote=new JSONObject(snapshot.toString());test.yes(DriveJournalRules.apply(remote,shared).length()==0);
            test.yes(active.equals(PixelMcpWriteTools.hash(remote.getJSONArray("customers"))));test.yes(OwnerJournalRules.findOrNull(remote.getJSONArray("customers"),local)==null);
            f.store.commitSnapshot(remote,DriveJournalRules.ids(remote.getJSONArray("drive_applied_ids")));
            test.yes(f.store.pendingActionsForDrive().length()==0&&f.store.pendingActions().length()==1);
            test.yes(OwnerJournalRules.findOrNull(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("customers"),local)!=null);
        }});
        test.probe("empty trash rejects a changed active reference before adding any journal",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master");PixelDriveState.prepare(snapshot);OwnerJournalRules.archives(snapshot).put("worklog:qa_old",SyntheticBusinessData.object().put("record",SyntheticBusinessData.object().put("id","qa_old").put("customer_id","qa_c").put("activity","Synthetic old").put("hours",1)).put("archived_at","t1"));f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject p=f.call("preview_empty_trash",SyntheticBusinessData.object());snapshot.getJSONArray("invoices").put(SyntheticBusinessData.object().put("id","qa_protected").put("customer_id","qa_c").put("issued_at","2026-10-18").put("invoice_number","R-1").put("status","Gestellt").put("positions",new JSONArray().put(SyntheticBusinessData.object().put("worklog_id","qa_old"))));f.store.commitSnapshot(snapshot,new HashSet<>());
            test.rejected(()->f.call("empty_trash",request("native-stale-empty").put("expected_preview_hash",p.getString("preview_hash")).put("confirmed",true)));test.yes(f.store.pendingActions().length()==0&&f.call("list_archive",SyntheticBusinessData.object()).getInt("total")==1);
        }});
        test.probe("batch archive atomically rolls back an interrupted write and replays after reopen",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master");JSONArray ids=new JSONArray(),rows=new JSONArray();
            for(int i=0;i<128;i++){String id="qa_synthetic-batch-"+i;ids.put(id);rows.put(SyntheticBusinessData.object().put("id",id).put("customer_id","qa_c").put("work_date","2026-07-31").put("activity","Isolated batch work").put("hours",1));}
            snapshot.put("worklogs",rows);f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject p=f.call("preview_archive_work_records",SyntheticBusinessData.object().put("work_record_ids",ids)),args=request("native-batch-atomic").put("work_record_ids",ids).put("expected_preview_hash",p.getString("preview_hash")).put("confirmed",true);
            f.store.getWritableDatabase().execSQL("CREATE TRIGGER fail_isolated_batch BEFORE INSERT ON pending WHEN NEW.id LIKE '%_2' BEGIN SELECT RAISE(ABORT, 'synthetic interrupted transaction'); END");
            boolean failed=false;try{f.call("archive_work_records",args);}catch(android.database.sqlite.SQLiteException expected){failed=true;}test.yes(failed&&f.store.pendingActions().length()==0);
            test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs").length()==128);
            f.store.getWritableDatabase().execSQL("DROP TRIGGER fail_isolated_batch");long start=android.os.SystemClock.elapsedRealtime();
            test.yes(f.call("archive_work_records",args).getInt("count")==128);long duration=android.os.SystemClock.elapsedRealtime()-start;
            test.yes(f.call("archive_work_records",args).getBoolean("replayed")&&f.store.pendingActions().length()==128);f.store.close();
            try(PixelLocalStore reopened=new PixelLocalStore(f.context)){test.yes(reopened.pendingActions().length()==128);test.yes(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs").length()==0);test.yes(new PixelOwnerAccess(f.context,reopened).execute("archive_work_records",args).getJSONObject("data").getBoolean("replayed"));}
            android.os.Bundle timing=new android.os.Bundle();timing.putString("stream","BATCH_128_LOCAL_COMMIT_MS "+duration+" (isolated Android emulator)\n");test.sendStatus(0,timing);
        }});
        test.probe("permanent trash replay and restart keep bytes until a committed publication",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            String id=f.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c").put("worklog_id","qa_w").put("caption","Synthetic linked original").put("data",PixelMcpInstrumentation.VALID_GIF));
            f.store.removeEntity("worklog","qa_w");test.yes(f.call("get_photo",SyntheticBusinessData.object().put("photo_id",id)).getJSONObject("photo").optString("worklog_id").isEmpty());f.store.undoRemoveEntity("worklog","qa_w");
            JSONObject preview=f.call("preview_delete",SyntheticBusinessData.object().put("entity","photo").put("entity_id",id));
            f.call("delete_entity",request("native-trash-photo").put("entity","photo").put("entity_id",id).put("expected_record_hash",preview.getString("expected_record_hash")).put("expected_photo_links_hash",preview.getString("expected_photo_links_hash")));
            JSONObject entry=f.call("list_archive",SyntheticBusinessData.object().put("entity","photo")).getJSONArray("archive").getJSONObject(0);
            JSONObject purge=request("native-purge-photo").put("entity","photo").put("entity_id",id).put("expected_record_hash",entry.getString("record_hash")).put("confirmed",true);
            JSONObject missing=SyntheticBusinessData.object(purge.toString());missing.remove("confirmed");test.rejected(()->f.call("purge_entity",missing));
            f.call("purge_entity",purge);test.yes(f.call("purge_entity",purge).getBoolean("replayed"));test.yes(f.call("list_archive",SyntheticBusinessData.object().put("entity","photo")).getInt("total")==0);
            test.yes(f.store.photoAttachmentStore().read(f.store.photoAttachment(id)).length>0);
            // Synthetic verified publication/commit, followed by restart before independent cleanup.
            JSONObject committed=f.store.getState().getJSONObject("master");PixelDriveState.prepare(committed);test.yes(DriveJournalRules.apply(committed,f.store.pendingActions()).length()==0);
            f.store.commitSnapshot(committed,DriveJournalRules.ids(committed.getJSONArray("drive_applied_ids")));f.store.close();
            try(PixelLocalStore reopened=new PixelLocalStore(f.context)){
                final int[] deletes={0};DriveSyncCoordinator.Files files=new DriveSyncCoordinator.Files(f.context,android.net.Uri.EMPTY,f.context.getSharedPreferences("haus_garten",0)){
                    @Override public void deletePhotoFileVerified(String name,String hash)throws Exception{test.yes(name.startsWith(hash+"."));deletes[0]++;}
                };
                JSONObject cleanup=TrashMediaCleanup.run(reopened,files,committed);test.yes(cleanup.getInt("pending")==0);test.yes(reopened.photoAttachment(id)==null);test.yes(deletes[0]>0);
                int count=deletes[0];TrashMediaCleanup.run(reopened,files,committed);test.yes(deletes[0]==count);
            }
        }});
        test.probe("customer original UI retry persists exactly one action and original",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject payload=SyntheticBusinessData.object().put("customer_id","qa_c").put("original_name","qa.gif").put("caption","QA Original").put("_photo_request_id","photo_ui_isolated_request").put("data",PixelMcpInstrumentation.VALID_GIF);String id=f.store.savePhoto(SyntheticBusinessData.object(payload.toString()));test.yes(f.store.savePhoto(SyntheticBusinessData.object(payload.toString())).equals(id));test.yes(f.store.pendingActions().length()==1);test.yes(f.store.photoAttachmentStore().read(f.store.photoAttachment(id)).length>0);payload.put("caption","Different request");test.rejected(()->f.store.savePhoto(payload));
        }});
        test.probe("UI archive is reversible locally and does not enqueue a permanent legacy delete",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            f.store.removeEntity("worklog","qa_w");test.yes(f.store.pendingActions().getJSONObject(0).getString("kind").equals("archive"));test.yes(f.store.pendingActionsForDrive().length()==1);String originalActionHash=PixelMcpWriteTools.hash(f.store.pendingActions().getJSONObject(0));test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs").length()==0);f.store.undoRemoveEntity("worklog","qa_w");test.yes(f.store.pendingActions().length()==2&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs").length()==1);test.yes(PixelMcpWriteTools.hash(f.store.pendingActions().getJSONObject(0)).equals(originalActionHash));test.yes(f.store.pendingActions().getJSONObject(1).getString("kind").equals("restore"));
        }});
        test.probe("photo metadata role keeps the same immutable action in snapshot and Drive",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            String id=f.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c").put("role","documentation").put("caption","QA").put("data",PixelMcpInstrumentation.VALID_GIF));
            JSONObject before=f.call("get_photo",SyntheticBusinessData.object().put("photo_id",id));f.call("update_photo",request("native-role-update").put("photo_id",id).put("expected_record_hash",before.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("role","before").put("caption","QA changed")));
            JSONObject after=f.call("get_photo",SyntheticBusinessData.object().put("photo_id",id));test.yes(after.getJSONObject("photo").getString("role").equals("before"));test.yes(after.getJSONObject("photo").getString("caption").equals("QA changed"));test.yes(PixelMcpWriteTools.hash(f.store.pendingActions()).equals(PixelMcpWriteTools.hash(f.store.pendingActionsForDrive())));
            f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("photos"),id);test.yes(row.getString("role").equals("before"));}
        }});
        test.probe("owner native settings/catalog/capability reads and settings replay",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject cap=f.call("get_capabilities",SyntheticBusinessData.object());test.yes(cap.getInt("protocol_version")==PixelMcpReadTools.PROTOCOL_VERSION);
            JSONObject settings=f.call("get_settings",SyntheticBusinessData.object());JSONObject args=request("owner-settings").put("expected_record_hash",settings.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("default_activity","QA Garten"));f.call("update_settings",args);test.yes(f.call("update_settings",args).getBoolean("replayed"));
            test.yes(f.call("search_catalog",SyntheticBusinessData.object().put("query","Thuja")).getInt("total")>0);test.yes(f.call("get_catalog_entry",SyntheticBusinessData.object().put("entry_id","thuja-occidentalis")).getString("id").equals("thuja-occidentalis"));
            test.yes(f.call("get_sync_conflicts",SyntheticBusinessData.object()).getInt("total")==0);test.yes(f.store.pendingActions().length()==0);
        }});
        test.probe("owner native file blocks/photo metadata and exact original read",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            byte[] bytes=Base64.getDecoder().decode(PixelMcpInstrumentation.VALID_GIF.split(",",2)[1]);String hash=McpFileTransfer.sha(bytes);
            JSONObject upload=f.call("begin_file_upload",request("owner-upload").put("byte_size",bytes.length).put("sha256",hash).put("mime_type","image/gif"));String id=upload.getString("upload_id");
            JSONObject block=request("owner-file-block").put("upload_id",id).put("offset",0).put("chunk_base64",Base64.getEncoder().encodeToString(bytes)).put("chunk_sha256",hash);f.call("write_file_chunk",block);test.yes(f.call("write_file_chunk",block).getBoolean("replayed"));
            JSONObject create=request("owner-photo-create").put("upload_id",id).put("customer_id","qa_c").put("caption","QA Original");String photo=f.call("create_photo",create).getString("photo_id");test.yes(f.call("create_photo",create).getString("photo_id").equals(photo));
            JSONObject meta=f.call("get_file_metadata",SyntheticBusinessData.object().put("resource_type","photo").put("resource_id",photo));test.yes(meta.getString("sha256").equals(hash));
            JSONObject data=f.call("read_file_chunk",SyntheticBusinessData.object().put("resource_type","photo").put("resource_id",photo).put("expected_sha256",hash).put("offset",0));test.yes(Arrays.equals(bytes,Base64.getDecoder().decode(data.getString("chunk_base64"))));
            JSONObject record=f.call("get_photo",SyntheticBusinessData.object().put("photo_id",photo));f.call("update_photo",request("owner-photo-edit").put("photo_id",photo).put("expected_record_hash",record.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("caption","QA Nachtrag")));
            test.yes(f.store.pendingActions().length()==2);test.yes(f.store.photoAttachmentStore().read(f.store.photoAttachment(photo)).length==bytes.length);
        }});
        test.probe("owner native archive/restore retains queue and original ID",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            String hash=f.tools.call("get_customer",SyntheticBusinessData.object().put("customer_id","qa_c")).getString("entity_hash");f.call("archive_entity",request("owner-archive").put("entity","customer").put("entity_id","qa_c").put("expected_record_hash",hash));
            JSONObject saved=f.call("list_archive",SyntheticBusinessData.object()).getJSONArray("archive").getJSONObject(0);f.call("restore_entity",request("owner-restore").put("entity","customer").put("entity_id","qa_c").put("expected_record_hash",saved.getString("record_hash")));
            test.yes(f.call("get_customer",SyntheticBusinessData.object().put("customer_id","qa_c")).getInt("active")==1);test.yes(f.store.pendingActions().length()==2);
        }});
        test.probe("owner native personal marks are local and cannot bypass migration",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            test.rejected(()->f.call("set_personal_paid",request("owner-paid-before").put("work_record_id","qa_w").put("paid",true)));f.store.putOwnerValue("personal_paid_migrated","1");f.call("set_personal_paid",request("owner-paid").put("work_record_id","qa_w").put("paid",true));
            test.yes(f.call("get_settings",SyntheticBusinessData.object()).getJSONArray("personal_paid").getString(0).equals("qa_w"));test.yes(f.store.pendingActions().length()==0);test.yes(f.call("get_work_record",SyntheticBusinessData.object().put("work_record_id","qa_w")).getInt("billed")==0);
        }});
        test.probe("owner native backup inspection/restore refuses changed versions",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject backup=f.call("create_backup",request("owner-backup"));String id=backup.getString("backup_id");test.yes(f.call("list_backups",SyntheticBusinessData.object()).getJSONArray("backups").length()==1);JSONObject info=f.call("inspect_backup",SyntheticBusinessData.object().put("backup_id",id));
            test.yes(f.call("restore_backup",request("owner-restore-identical").put("backup_id",id).put("expected_state_hash",info.getString("state_hash"))).getInt("overwritten_records")==0);
            JSONObject snapshot=f.store.getState().getJSONObject("master");snapshot.getJSONArray("worklogs").getJSONObject(0).put("notes","Parallel");f.store.commitSnapshot(snapshot,new HashSet<>());JSONObject changed=f.call("inspect_backup",SyntheticBusinessData.object().put("backup_id",id));
            test.rejected(()->f.call("restore_backup",request("owner-restore-stale").put("backup_id",id).put("expected_state_hash",changed.getString("state_hash"))));test.yes(f.call("get_work_record",SyntheticBusinessData.object().put("work_record_id","qa_w")).getString("notes").equals("Parallel"));
        }});
        test.probe("owner native conflict decision retains original action until Drive review",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject original=SyntheticBusinessData.object("{id:'qa_c',name:'QA andere Variante',_mcp_create:true}");f.store.saveAction("customer","cus_",original);JSONObject snapshot=f.store.getState().getJSONObject("master");DriveJournalRules.apply(snapshot,f.store.pendingActions());f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject conflict=f.call("get_sync_conflicts",SyntheticBusinessData.object()).getJSONArray("conflicts").getJSONObject(0);f.call("resolve_sync_conflict",request("owner-resolution").put("client_id",conflict.getString("client_id")).put("expected_action_hash",conflict.getString("action_hash")).put("expected_current_hash",conflict.getString("current_hash")).put("resolution","keep_current"));
            test.yes(f.store.pendingActions().length()==2);test.yes(f.call("get_customer",SyntheticBusinessData.object().put("customer_id","qa_c")).getString("name").equals("QA Eigentümer"));
        }});
        test.probe("owner native large metadata avoids CursorWindow-sized rows",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            StringBuilder builder=new StringBuilder();for(int i=0;i<1_100_000;i++)builder.append("Ä🌱");String text=builder.toString();f.store.putOwnerValue("large-fixture",text);test.yes(f.store.ownerValue("large-fixture","").equals(text));
        }});
        test.probe("owner native business configuration uses journal and excludes credentials",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject old=f.call("get_business_settings",SyntheticBusinessData.object());f.call("update_business_settings",request("owner-business-settings").put("expected_record_hash",old.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("owner_name","QA Aussteller").put("invoice_issuer_confirmed",true)));
            test.yes(f.call("get_business_settings",SyntheticBusinessData.object()).getJSONObject("settings").getString("owner_name").equals("QA Aussteller"));test.yes(f.store.pendingActions().length()==1);
        }});
        test.probe("owner native invoice draft updates and finalization remain distinct",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject create=request("owner-draft").put("customer_id","qa_c").put("invoice_date","2026-10-18").put("positions",new JSONArray().put(SyntheticBusinessData.object().put("title","QA Pflege").put("quantity",2).put("price",45)));
            String id=f.call("create_invoice_draft",create).getString("entity_id");JSONObject snapshot=f.store.getState().getJSONObject("master");DriveJournalRules.apply(snapshot,f.store.pendingActions());f.store.commitSnapshot(snapshot,DriveJournalRules.ids(snapshot.getJSONArray("drive_applied_ids")));
            String hash=f.tools.call("get_invoice",SyntheticBusinessData.object().put("invoice_id",id)).getString("record_hash");f.call("update_invoice_draft",request("owner-draft-update").put("invoice_id",id).put("expected_record_hash",hash).put("updates",SyntheticBusinessData.object().put("notes","QA Nachtrag")));
            String revised=f.tools.call("get_invoice",SyntheticBusinessData.object().put("invoice_id",id)).getString("record_hash");
            test.rejected(()->f.call("request_invoice_issue",request("owner-issue").put("invoice_id",id).put("expected_record_hash",revised).put("confirmed",true)));
            test.yes(f.call("get_invoice",SyntheticBusinessData.object().put("invoice_id",id)).getString("status").equals("Entwurf"));test.yes(f.store.pendingActions().length()==1);
            test.yes(f.call("request_sync",request("owner-sync")).getString("state").equals("not_connected"));
        }});
        test.probe("owner native correction/payment/share preserve issued original",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master"),invoice=SyntheticBusinessData.object("{id:'qa_i',customer_id:'qa_c',invoice_number:'QA-1',invoice_date:'2026-10-18',status:'Gestellt',issued_at:'2026-10-18T08:00:00Z',updated_at:'2026-10-18T08:00:00Z',amount:90,positions:[{title:'QA Pflege',quantity:2,price:45}],worklog_ids:[]}");invoice.put("pdf_base64",Base64.getEncoder().encodeToString("%PDF-1.4\nSynthetic header only".getBytes(java.nio.charset.StandardCharsets.UTF_8)));snapshot.getJSONArray("invoices").put(invoice);f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject read=f.tools.call("get_invoice",SyntheticBusinessData.object().put("invoice_id","qa_i"));JSONObject correction=f.call("create_invoice_correction",request("owner-correction").put("invoice_id","qa_i").put("expected_record_hash",read.getString("record_hash")).put("correction_type","cancellation").put("reason","QA Korrektur"));test.yes(correction.getBoolean("requires_issue_confirmation"));
            JSONObject paid=f.call("update_invoice_payment",request("owner-invoice-paid").put("invoice_id","qa_i").put("expected_record_hash",read.getString("entity_hash")).put("paid",true));test.yes(paid.getString("sync_state").equals("sync_pending"));
            JSONObject share=f.call("prepare_invoice_share",request("owner-share").put("invoice_id","qa_i").put("channel","share"));test.yes(share.getString("state").equals("device_interaction_required")&&!share.getBoolean("sent"));test.yes(f.call("get_invoice",SyntheticBusinessData.object().put("invoice_id","qa_i")).getString("issued_at").equals(invoice.getString("issued_at")));
        }});
        test.probe("native photo optimization rotates and creates separately verified small previews",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            android.graphics.Bitmap large=android.graphics.Bitmap.createBitmap(4096,2048,android.graphics.Bitmap.Config.RGB_565);large.eraseColor(android.graphics.Color.GREEN);java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();large.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,encoded);large.recycle();byte[] jpeg=encoded.toByteArray();
            // Synthetic EXIF orientation 6; no camera, location or production file.
            byte[] exif={(byte)255,(byte)225,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};java.io.ByteArrayOutputStream rotated=new java.io.ByteArrayOutputStream();rotated.write(jpeg,0,2);rotated.write(exif);rotated.write(jpeg,2,jpeg.length-2);
            JSONObject payload=SyntheticBusinessData.object().put("data","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(rotated.toByteArray())).put("role","documentation");PhotoAttachmentStore.Prepared p=f.store.photoAttachmentStore().prepare(payload);test.yes(p.metadata.getInt("height")==3072&&p.metadata.getInt("width")==1536);test.yes(p.bytes.length>0&&p.bytes.length<jpeg.length);test.yes(PhotoPolicy.orientation(p.bytes)==1);test.yes(f.store.photoAttachmentStore().readThumbnail(p.metadata.getString("thumbnail_storage_name")).length<65536);
            PhotoAttachmentStore.Prepared original=f.store.photoAttachmentStore().prepare(SyntheticBusinessData.object(payload.toString()).put("_photo_quality","original"));byte[] thumb=f.store.photoAttachmentStore().readThumbnail(original.metadata.getString("thumbnail_storage_name"));android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(thumb,0,thumb.length,bounds);test.yes(bounds.outHeight>bounds.outWidth);test.yes(Arrays.equals(original.bytes,rotated.toByteArray()));
            byte[] small=Base64.getDecoder().decode(PixelMcpInstrumentation.VALID_GIF.split(",",2)[1]);PhotoAttachmentStore.Prepared tiny=f.store.photoAttachmentStore().prepare(SyntheticBusinessData.object().put("data",PixelMcpInstrumentation.VALID_GIF));test.yes(Arrays.equals(small,tiny.bytes));
        }});
        test.probe("native 21 photo writes keep customer relationships and survive store reopen",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            for(int i=0;i<21;i++)f.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c").put("caption","QA "+i).put("_photo_request_id","isolated-21-photos-"+i).put("data",PixelMcpInstrumentation.VALID_GIF));test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("photos").length()==21);test.yes(f.store.pendingActions().length()==21);f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){test.yes(reopened.pendingActions().length()==21);test.yes(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("photos").length()==21);for(int i=0;i<21;i++)test.yes("qa_c".equals(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("photos").getJSONObject(i).getString("customer_id")));}
        }});
        test.probe("native timer photos link their final worklog atomically and completion retries do not duplicate",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            PixelMcpInstrumentation.FakeClock clock=new PixelMcpInstrumentation.FakeClock();WorkSessionStore timer=new WorkSessionStore(f.store.getWritableDatabase(),clock);JSONObject session=timer.start(SyntheticBusinessData.object().put("request_id","isolated-photo-session").put("customer_id","qa_c").put("billing_customer_id","qa_c").put("work_date","2026-10-18"));String sid=session.getString("id");String photo=f.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c").put("session_id",sid).put("data",PixelMcpInstrumentation.VALID_GIF));test.rejected(()->f.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_unknown").put("session_id",sid).put("data",PixelMcpInstrumentation.VALID_GIF)));clock.advance(3600000);timer.stop(sid,"isolated-photo-stop");String work=f.store.completeWorkSession(sid,SyntheticBusinessData.object().put("activity","QA Gartenpflege"),"isolated-photo-complete");int count=f.store.pendingActions().length();test.yes(f.store.completeWorkSession(sid,SyntheticBusinessData.object(),"isolated-photo-complete").equals(work));test.yes(f.store.pendingActions().length()==count);JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("photos"),photo);test.yes(row.getString("session_id").equals(sid)&&row.getString("worklog_id").equals(work)&&row.getString("customer_id").equals("qa_c"));
        }});
        test.probe("native offer MCP produces immutable separate PDF and no invoice",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master"),c=snapshot.getJSONArray("customers").getJSONObject(0);c.put("street","Test 1").put("postal_code","1000").put("city","Wien").put("country","AT");snapshot.put("business_settings",SyntheticBusinessData.object().put("owner_name","QA Aussteller").put("business_street","Test 2").put("business_postal_code","1000").put("business_city","Wien").put("invoice_issuer_confirmed","1").put("tax_mode","small_business").put("tax_exemption_text","QA geprüfter Steuertext"));f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject a=request("native-offer-create").put("customer_id","qa_c").put("title","QA Pflege").put("document_type","fixed_price").put("customer_type","b2b").put("scope_confirmed",true).put("issue_date","2026-10-18").put("valid_until","2026-11-18").put("execution_window","November").put("payment_terms","14 Tage").put("positions",new JSONArray().put(SyntheticBusinessData.object().put("title","QA Arbeit").put("quantity",14).put("price",50).put("tax_rate",0).put("unit","h").put("price_source","user_confirmed")));String id=f.call("create_offer_draft",a).getString("offer_id");test.yes(f.call("create_offer_draft",a).getString("offer_id").equals(id));JSONObject read=f.call("validate_offer",SyntheticBusinessData.object().put("offer_id",id));test.yes(read.getJSONObject("validation").getBoolean("valid"));
            JSONObject finalize=request("native-offer-final").put("offer_id",id).put("expected_record_hash",read.getString("record_hash")).put("expected_context_hash",read.getString("context_hash")).put("confirmed",true);f.call("finalize_offer",finalize);test.yes(f.call("finalize_offer",finalize).getBoolean("replayed"));JSONObject offer=f.call("get_offer",SyntheticBusinessData.object().put("offer_id",id)).getJSONObject("offer");byte[] pdf=new BusinessDocumentStore(f.context).read(offer);test.yes(pdf.length>5&&PhotoAttachmentStore.sha256(pdf).equals(offer.getString("pdf_sha256")));test.yes(!offer.has("pdf_base64")&&PixelMcpReadTools.effective(f.store.getState()).getJSONArray("invoices").length()==0);rejectedDocument(test,()->new BusinessDocumentStore(f.context).store(pdf,SyntheticBusinessData.object(offer.toString()).put("pdf_storage_name",PixelMcpInstrumentation.repeat("a",64)+".pdf")));
        }});
        test.probe("native invoice PDF is separately hashed and rejects changed document bytes",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject issuer=SyntheticBusinessData.object().put("owner_name","QA Aussteller").put("business_street","Test 2").put("business_postal_code","1000").put("business_city","Wien").put("tax_exemption_text","QA Steuertext");
            JSONObject invoice=SyntheticBusinessData.object().put("id","qa_native-invoice-pdf").put("invoice_number","QA-2026-0001").put("invoice_date","2026-10-18").put("service_start","2026-10-17").put("service_end","2026-10-18").put("due_date","2026-11-01").put("issuer",issuer).put("recipient",SyntheticBusinessData.object().put("name","QA Kunde").put("street","Test 1").put("postal_code","1000").put("city","Wien")).put("tax_mode","small_business").put("net_total",100).put("tax_total",0).put("gross_total",100).put("positions",new JSONArray().put(SyntheticBusinessData.object().put("title","QA Pflege").put("quantity",2).put("unit","h").put("price",50).put("net",100).put("tax",0)));
            BusinessDocumentStore documents=new BusinessDocumentStore(f.context);JSONObject meta=documents.createInvoice(invoice);byte[] bytes=documents.read(meta);test.yes(bytes.length>5&&PhotoAttachmentStore.sha256(bytes).equals(meta.getString("pdf_sha256")));test.yes(!meta.has("pdf_base64")&&!meta.has("data"));bytes[bytes.length-1]^=1;rejectedDocument(test,()->documents.store(bytes,meta));
        }});
        test.probe("owner native manual billing flag and explicit assignment use shared work saver",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject work=f.tools.call("get_work_record",SyntheticBusinessData.object().put("work_record_id","qa_w"));f.call("set_work_record_billed",request("owner-manual-billing").put("work_record_id","qa_w").put("expected_record_hash",work.getString("record_hash")).put("billed",true).put("reason","QA manuelle Markierung"));test.yes(f.call("get_work_record",SyntheticBusinessData.object().put("work_record_id","qa_w")).getInt("billed")==1);
            JSONObject current=f.tools.call("get_work_record",SyntheticBusinessData.object().put("work_record_id","qa_w"));f.call("reassign_work_record",request("owner-explicit-assignment").put("work_record_id","qa_w").put("expected_record_hash",current.getString("record_hash")).put("customer_id","qa_c").put("billing_customer_id","qa_c").put("reason","QA ausdrückliche Zuordnung"));test.yes(f.store.pendingActions().length()==2);
        }});
    }
}
