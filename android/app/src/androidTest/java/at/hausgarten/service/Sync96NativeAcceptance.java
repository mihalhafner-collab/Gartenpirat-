package at.hausgarten.service;
import android.content.*;
import org.json.*;
import java.util.*;

/** Real SQLite and preference adapters, private synthetic provider only. No device/account sync. */
final class Sync96NativeAcceptance {
    static void run(PixelMcpInstrumentation t)throws Exception{
        t.probe("96 configuration failures persist actionable first attempt without retry",()->{
            for(String code:new String[]{"FOLDER_NOT_SELECTED","DEVICE_ID_MISSING"})try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
                android.content.SharedPreferences p=f.context.getSharedPreferences("haus_garten",0);p.edit().putInt("drive_retry_count",7).commit();
                DriveSyncCoordinator.recordFailure(f.context,new SyncIoFailure(code));
                t.yes(p.getString("drive_last_error_code","").equals(code)&&p.getBoolean("drive_user_action_required",false));
                t.yes(p.getInt("drive_retry_count",0)==1&&p.getLong("drive_next_retry",1)==0);
                t.yes(p.getString("drive_last_error","").equals(SyncPolicy.message(code)));
            }
        });
        t.probe("96 actual owner request rolls back action and receipt then replays once",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            LocalOwnerRepository repo=new LocalOwnerRepository(f.context,f.store);String fp=PixelMcpWriteTools.hash("qa_request");
            try{repo.runRequest("qa_rollback",fp,()->{repo.enqueue("worklog",new JSONObject().put("id","qa_rollback"),"qa_rollback");throw new java.io.IOException("qa forced rollback");});throw new AssertionError("accepted failed request");}catch(java.io.IOException expected){}
            t.yes(f.store.pendingActions().length()==0&&f.store.ownerValue("receipt:qa_rollback","").isEmpty());
            int[] effects={0};LocalOwnerRepository.Request command=()->{effects[0]++;return repo.enqueue("worklog",new JSONObject().put("id","qa_once"),"qa_once");};
            repo.runRequest("qa_once",fp,command);t.yes(repo.runRequest("qa_once",fp,command).getBoolean("replayed"));t.yes(effects[0]==1&&f.store.pendingActions().length()==1);
            try{repo.runRequest("qa_once",PixelMcpWriteTools.hash("qa_changed"),command);throw new AssertionError("accepted changed request");}catch(PixelMcpWriteTools.Rejected e){t.yes(e.code.equals("REPLAY_MISMATCH"));}
        }});
        t.probe("96 rejected invoice ack is atomic before failed postcommit preferences",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            JSONObject snapshot=new JSONObject(f.store.getState().getJSONObject("master").toString());LocalOwnerRepository repo=new LocalOwnerRepository(f.context,f.store);
            repo.enqueue("owner_invoice_issue",new JSONObject().put("id","qa_rejected_invoice"),"qa_rejected_invoice_action");
            snapshot.put("rejected_invoice_ids",new JSONArray().put("qa_rejected_invoice_action"));
            SyncCommitRules.Result result=SyncCommitRules.commit(snapshot,new SyncCommitRules.Storage(){
                public void commitSnapshot(JSONObject saved,Set<String> ids)throws Exception{f.store.commitSnapshot(saved,ids);}
                public boolean saveStatus()throws Exception{t.yes(f.store.pendingActions().length()==0);return false;}
            });
            t.yes(!result.statusSaved&&f.store.pendingActions().length()==0&&f.store.snapshotText("").equals(snapshot.toString()));
            DriveSyncCoordinator.recordFailure(f.context,new SyncIoFailure("STATUS_NOT_SAVED"));SharedPreferences p=f.context.getSharedPreferences("haus_garten",0);
            t.yes(p.getInt("drive_retry_count",-1)==0&&!p.getBoolean("drive_sync_failed",true)&&p.getLong("drive_next_retry",-1)==0);
            t.yes(p.getString("drive_last_error","").startsWith("Daten gesichert"));
        }});
        t.probe("96 full provider run keeps committed data when final preference commit returns false",()->{try(DriveProviderAcceptance.Setup s=new DriveProviderAcceptance.Setup(t)){
            s.store.execute("create_customer_work_record",t.args("qa_postcommit_status"));boolean[] failed={false};
            Context flaky=new ContextWrapper(s.context){@Override public SharedPreferences getSharedPreferences(String name,int mode){
                SharedPreferences delegate=super.getSharedPreferences(name,mode);
                return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
                    if(method.getName().equals("edit")){SharedPreferences.Editor editor=delegate.edit();boolean[] status={false};
                        return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(self,operation,values)->{
                            if(operation.getName().equals("putString")&&"last_drive_sync".equals(values[0]))status[0]=true;
                            if(operation.getName().equals("commit")&&status[0]&&!failed[0]){failed[0]=true;editor.apply();return false;}
                            Object value=operation.invoke(editor,values);return value==editor?self:value;
                        });
                    }
                    return method.invoke(delegate,args);
                });
            }};
            JSONObject result=DriveSyncCoordinator.run(flaky,s.store,s.files,"qadevice");t.yes(failed[0]&&result.getBoolean("ok")&&result.getString("status_warning").equals("STATUS_NOT_SAVED"));
            t.yes(s.store.pendingActions().length()==0&&s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==1);
            t.yes(s.prefs.getInt("drive_retry_count",-1)==0&&!s.prefs.getBoolean("drive_sync_failed",true)&&s.prefs.getLong("drive_next_retry",-1)==0);
        }});
        t.probe("96 failed receipt deletion transaction retains full receipt and rolls back its guard",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            String fp=PixelMcpWriteTools.hash("qa_retention_rollback");f.store.putOwnerValue("receipt:qa_gc_rollback",new JSONObject().put("created_at","2026-01-01T00:00:00Z").put("fingerprint",fp).put("result",new JSONObject()).toString());
            f.store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER qa_gc_abort BEFORE DELETE ON owner_state BEGIN SELECT RAISE(ABORT,'qa deliberate rollback'); END");
            try{OwnerReceiptRetention.afterConfirmedSync(f.store,f.store.getState().getJSONObject("master"),java.time.Instant.parse("2026-10-22T12:00:00Z").toEpochMilli());throw new AssertionError("accepted failed deletion");}catch(android.database.sqlite.SQLiteException expected){}
            t.yes(!f.store.ownerValue("receipt:qa_gc_rollback","").isEmpty()&&f.store.ownerValue("receipt_guard:qa_gc_rollback","").isEmpty());
        }});
        t.probe("H1 malformed foreign journal fails closed before publishing or acknowledging local work",()->{try(DriveProviderAcceptance.Setup s=new DriveProviderAcceptance.Setup(t)){
            s.store.execute("create_customer_work_record",t.args("qa_bad_foreign_journal"));String snapshot=s.store.snapshotText(""),pending=s.store.pendingActions().toString();
            String foreign=s.provider.add("HausGarten-Mobile-qaforeign.json","{\"qa_invalid\":true}");String original=s.provider.text(foreign);
            try{s.sync();throw new AssertionError("accepted malformed foreign journal");}catch(Exception error){t.yes(DriveSyncCoordinator.failureCode(error).equals("INVALID_REMOTE_DATA"));}
            t.yes(s.store.snapshotText("").equals(snapshot)&&s.store.pendingActions().toString().equals(pending)&&s.provider.text(foreign).equals(original));
        }});
        t.probe("96 receipt retention preserves pending backup and both tombstone references",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())){
            LocalOwnerRepository repo=new LocalOwnerRepository(f.context,f.store);String fp=PixelMcpWriteTools.hash("qa_retention");
            for(String request:new String[]{"qa_orphan","qa_pending","qa_backup","qa_tomb","qa_local_tomb","qa_series","qa_batch_tomb"}){
                repo.runRequest(request,fp,()->new JSONObject().put("client_id",request));JSONObject receipt=new JSONObject(f.store.ownerValue("receipt:"+request,"{}"));receipt.put("created_at","2026-01-01T00:00:00Z");
                if(request.equals("qa_backup"))receipt.getJSONObject("result").put("deletion_backup_id","qa_backup_file");f.store.putOwnerValue("receipt:"+request,receipt.toString());
            }
            repo.enqueue("worklog",new JSONObject().put("id","qa_series_work"),"qa_series_0");
            repo.enqueue("worklog",new JSONObject().put("id","qa_pending"),"qa_pending");JSONObject snapshot=new JSONObject(f.store.getState().getJSONObject("master").toString());
            OwnerJournalRules.archives(snapshot).put("customer:qa_tomb",new JSONObject().put("purge_action","qa_tomb"));
            OwnerJournalRules.archives(snapshot).put("customer:qa_batch_tomb",new JSONObject().put("purge_action","qa_batch_tomb_0"));
            f.store.putOwnerValue("purged_local_test_records_v1",new JSONObject().put("customer:qa_local",new JSONObject().put("purge_action","qa_local_tomb")).toString());
            t.yes(OwnerReceiptRetention.afterConfirmedSync(f.store,snapshot,java.time.Instant.parse("2026-10-22T12:00:00Z").toEpochMilli())==1);
            t.yes(f.store.ownerValue("receipt:qa_orphan","").isEmpty()&&!f.store.ownerValue("receipt_guard:qa_orphan","").isEmpty());
            for(String request:new String[]{"qa_pending","qa_backup","qa_tomb","qa_local_tomb","qa_series","qa_batch_tomb"})t.yes(!f.store.ownerValue("receipt:"+request,"").isEmpty());
            int[] effects={0};try{repo.runRequest("qa_orphan",fp,()->{effects[0]++;return new JSONObject();});throw new AssertionError("expired request ran");}catch(PixelMcpWriteTools.Rejected e){t.yes(e.code.equals("REQUEST_EXPIRED")&&effects[0]==0);}
            JSONObject summary=new JSONObject(f.store.ownerValue("receipt_retention_summary","{}"));t.yes(summary.getInt("removed")==1&&!summary.toString().contains("qa_orphan"));
        }});
        t.probe("96 provider-only readback never expires owner receipts",()->{try(DriveProviderAcceptance.Setup s=new DriveProviderAcceptance.Setup(t)){
            s.store.putOwnerValue("receipt:qa_provider_orphan",new JSONObject().put("created_at","2026-01-01T00:00:00Z").put("fingerprint",PixelMcpWriteTools.hash("qa_provider")).put("result",new JSONObject()).toString());
            t.yes(s.sync().getBoolean("ok"));t.yes(!s.store.ownerValue("receipt:qa_provider_orphan","").isEmpty());t.yes(s.store.ownerValue("receipt_retention_summary","").isEmpty());
        }});
    }
    private Sync96NativeAcceptance(){}
}
