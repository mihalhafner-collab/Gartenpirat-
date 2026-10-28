package at.hausgarten.service;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;

/** Actual native widgets, synthetic folder port, dedicated Cloud AVD only. */
final class BackupSetupNativeAcceptance {
    static Object field(Object owner,String name)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static void field(Object owner,String name,Object value)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task){tasks.add(task);}
        void next(PixelMcpInstrumentation test){if(tasks.isEmpty())throw new AssertionError("Missing queued selection");tasks.remove().run();test.waitForIdleSync();}
    }
    static final class Folders implements RetiredBackupDriveClient.Wire {
        int calls;
        JSONObject row(String id,String name)throws Exception{return new JSONObject().put("id",id).put("name",name).put("mimeType","application/vnd.google-apps.folder").put("trashed",false);}
        public RetiredBackupDriveClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception {
            calls++;if(!method.equals("GET")||body!=null)throw new AssertionError("Folder picker attempted a write");JSONObject result;
            if(url.contains("files?q="))result=new JSONObject().put("files",url.contains("root")?new JSONArray().put(row("qa_readonly_folder","QA Read only")).put(row("qa_backup_folder","QA Backup")):new JSONArray());
            else{String id=url.contains("qa_readonly_folder")?"qa_readonly_folder":"qa_backup_folder";result=row(id,"QA Backup").put("capabilities",new JSONObject().put("canAddChildren",!id.equals("qa_readonly_folder")));}
            return new RetiredBackupDriveClient.Reply(200,result.toString().getBytes(StandardCharsets.UTF_8),Collections.emptyMap());
        }
    }
    static void run(PixelMcpInstrumentation test)throws Exception {
        if(!Arrays.asList("ranchu","goldfish").contains(Build.HARDWARE))throw new IllegalStateException("QA_CLOUD_EMULATOR_REQUIRED");
        FullBackupActivity activity=(FullBackupActivity)test.startActivitySync(new Intent(test.getTargetContext(),FullBackupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            long deadline=SystemClock.elapsedRealtime()+30000;boolean[] ready={false};
            while(SystemClock.elapsedRealtime()<deadline&&!ready[0]){test.runOnMainSync(()->{try{ready[0]=(Boolean)field(activity,"setupLoaded");}catch(Exception e){throw new AssertionError(e);}});if(!ready[0])SystemClock.sleep(50);}
            test.yes(ready[0]);
            test.probe("simple backup screen keeps advanced functions closed and folder IDs out of the editor",()->{
                test.runOnMainSync(()->{try{
                    test.yes(((View)field(activity,"options")).getVisibility()==View.GONE);
                    test.yes(((View)field(activity,"folder")).getVisibility()==View.GONE);
                    test.yes(((EditText)field(activity,"account")).getKeyListener()==null);
                    test.yes(((Button)field(activity,"requestBackup")).getText().toString().equals("Backup einrichten"));
                    test.yes(((Button)field(activity,"saveSetup")).getText().toString().equals("Backup einschalten"));
                }catch(Exception e){throw new AssertionError(e);}});
            });
            test.probe("setup recreation never serializes a candidate key, verified copy or separation acknowledgement",()->{
                String secret=BackupCrypto.newRecoveryCode();test.runOnMainSync(()->{try{
                    field(activity,"candidateCode",secret);field(activity,"verifiedRecoveryCopy",secret);((CheckBox)field(activity,"codeSeparate")).setChecked(true);
                    Bundle state=new Bundle();activity.onSaveInstanceState(state);for(String key:state.keySet())test.yes(!String.valueOf(state.get(key)).contains(secret));
                    test.yes(!state.containsKey("verifiedRecoveryCopy")&&!state.containsKey("codeSeparate"));
                    field(activity,"candidateCode",null);field(activity,"verifiedRecoveryCopy","");((CheckBox)field(activity,"codeSeparate")).setChecked(false);
                }catch(Exception e){throw new AssertionError(e);}});
            });
            test.probe("native folder picker rejects read-only targets and applies exactly one explicit writable choice",()->{
                Queue work=new Queue();Folders wire=new Folders();String[] chosen={""};int[] commits={0};RetiredBackupFolderPicker picker=new RetiredBackupFolderPicker(activity,new RetiredBackupDriveClient(()->"qa_picker_token",wire),work,(id,name)->{chosen[0]=id;commits[0]++;});
                try{
                    test.runOnMainSync(picker::show);work.next(test);AlertDialog dialog=(AlertDialog)field(picker,"dialog");
                    test.runOnMainSync(()->{test.yes(dialog.getListView().getAdapter().getCount()==2);test.yes(!dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());dialog.getListView().performItemClick(null,0,0);});work.next(test);
                    test.runOnMainSync(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());work.next(test);
                    test.yes(commits[0]==0);test.runOnMainSync(()->{test.yes(!dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();});work.next(test);
                    test.runOnMainSync(()->dialog.getListView().performItemClick(null,1,1));work.next(test);
                    test.runOnMainSync(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());work.next(test);
                    test.yes(commits[0]==1&&chosen[0].equals("qa_backup_folder")&&!dialog.isShowing());
                }finally{test.runOnMainSync(picker::close);}
                Queue cancelled=new Queue();RetiredBackupFolderPicker closed=new RetiredBackupFolderPicker(activity,new RetiredBackupDriveClient(()->"qa_picker_token",wire),cancelled,(id,name)->{throw new AssertionError("Cancelled picker committed");});int before=wire.calls;
                test.runOnMainSync(()->{closed.show();closed.close();});cancelled.next(test);test.yes(wire.calls==before);
            });
        }finally{test.runOnMainSync(activity::finish);test.waitForIdleSync();}
    }
}
