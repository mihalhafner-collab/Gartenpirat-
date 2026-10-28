package at.hausgarten.service;

import android.app.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Explicit backup-folder selection; folder metadata only, never business files. */
final class RetiredBackupFolderPicker {
    interface Selection {void selected(String id,String name);}
    private final Activity activity;
    private final RetiredBackupDriveClient client;
    private final Executor work;
    private final Selection selection;
    private final ArrayList<JSONObject> path=new ArrayList<>();
    private AlertDialog dialog;
    private ArrayAdapter<String> labels;
    private JSONArray folders=new JSONArray();
    private volatile boolean closed;
    private boolean loading,retry;
    private long revision;
    private String next="",retryToken="";
    RetiredBackupFolderPicker(Activity activity,RetiredBackupDriveClient client,Executor work,Selection selection){this.activity=activity;this.client=client;this.work=work;this.selection=selection;}
    void show(){
        labels=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1);
        dialog=new AlertDialog.Builder(activity).setTitle("Backup-Ordner auswählen").setAdapter(labels,null)
            .setPositiveButton("Hier sichern",null).setNeutralButton("Eine Ebene zurück",null).setNegativeButton("Abbrechen",(d,w)->close()).create();
        dialog.setOnDismissListener(d->close());dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->select());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(!loading&&!path.isEmpty()){path.remove(path.size()-1);load("");}});
        dialog.getListView().setOnItemClickListener((parent,view,index,id)->{
            if(loading)return;if(retry){load(retryToken);return;}
            if(index<folders.length()){path.add(folders.optJSONObject(index));load("");}
            else if(!next.isEmpty())load(next);
        });
        load("");
    }
    private String parent(){return path.isEmpty()?"root":path.get(path.size()-1).optString("id");}
    private void enabled(boolean value){loading=!value;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(value&&!path.isEmpty());dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(value&&!path.isEmpty());dialog.getListView().setEnabled(value);}
    private void ui(long request,Runnable action){activity.runOnUiThread(()->{if(!closed&&!activity.isFinishing()&&!activity.isDestroyed()&&request==revision)action.run();});}
    private void load(String pageToken){
        long request=++revision;String parent=parent();folders=new JSONArray();next="";retry=false;labels.clear();labels.add("Ordner werden geladen …");enabled(false);
        dialog.setTitle(path.isEmpty()?"Backup-Ordner · Meine Ablage":path.get(path.size()-1).optString("name"));
        work.execute(()->{if(closed)return;try{JSONObject page=client.backupFolderPage(parent,pageToken);ui(request,()->{
            labels.clear();folders=page.optJSONArray("files");next=page.optString("nextPageToken");
            for(int i=0;i<folders.length();i++)labels.add(folders.optJSONObject(i).optString("name"));
            if(!next.isEmpty())labels.add("Weitere Ordner anzeigen …");
            if(folders.length()==0){retry=true;retryToken=pageToken;labels.add(path.isEmpty()?"In Drive einen Backup-Ordner anlegen, dann hier erneut laden":"Keine Unterordner · antippen zum Neuladen");}
            enabled(true);
        });}catch(Exception error){ui(request,()->{labels.clear();folders=new JSONArray();retry=true;retryToken=pageToken;labels.add("Ordner konnten nicht geladen werden. Antippen zum erneuten Laden.");enabled(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);});}});
    }
    private void select(){
        if(loading||path.isEmpty())return;String id=parent();long request=++revision;enabled(false);dialog.setTitle("Ordnerzugriff wird geprüft …");
        work.execute(()->{if(closed)return;try{JSONObject folder=client.backupFolder(id);ui(request,()->{selection.selected(folder.optString("id"),folder.optString("name"));close();});}
        catch(Exception error){ui(request,()->{dialog.setTitle("Kein Schreibzugriff · anderen Ordner wählen");enabled(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);});}});
    }
    void close(){if(closed)return;closed=true;++revision;client.cancelActiveTransfers();if(dialog!=null)dialog.dismiss();}
}
