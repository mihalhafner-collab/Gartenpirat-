package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Atomic component renames behind an external reader/writer gate, with a durable roll-back journal. */
final class BackupDatasetExchange {
    interface Journal {JSONObject read()throws Exception;void save(JSONObject state)throws Exception;}
    interface Moves {void move(File from,File to)throws Exception;}
    static final Moves ATOMIC=(from,to)->{
        if(Files.isSymbolicLink(from.toPath())||Files.isSymbolicLink(to.toPath())||to.exists())throw new IOException("RESTORE_EXCHANGE_PATH_CONFLICT");
        File parent=to.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("RESTORE_EXCHANGE_STORAGE_UNAVAILABLE");
        Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);
    };
    static void install(Journal journal,Moves moves)throws Exception {
        JSONObject state=journal.read();JSONArray targets=state.getJSONArray("targets");
        for(int i=0;i<targets.length();i++){
            EncryptedBackupArchive.checkCancelled();JSONObject target=targets.getJSONObject(i);File live=file(target,"live"),old=file(target,"old");
            if(!target.optBoolean("old_done")){
                if(!target.has("old_present")){target.put("old_present",live.exists());journal.save(state);}
                if(target.getBoolean("old_present")){
                    if(live.exists()&&!old.exists())moves.move(live,old);
                    else if(live.exists()||!old.exists())throw new IOException("RESTORE_EXCHANGE_OLD_STATE_AMBIGUOUS");
                }else if(live.exists()||old.exists())throw new IOException("RESTORE_EXCHANGE_OLD_STATE_CHANGED");
                target.put("old_done",true);journal.save(state);
            }
            if(!target.optBoolean("new_done")){
                String source=target.optString("prepared","");
                if(!source.isEmpty()){
                    File prepared=new File(source);
                    if(prepared.exists()&&!live.exists())moves.move(prepared,live);
                    else if(prepared.exists()||!live.exists())throw new IOException("RESTORE_EXCHANGE_NEW_STATE_AMBIGUOUS");
                }else if(live.exists())throw new IOException("RESTORE_EXCHANGE_NEW_STATE_CHANGED");
                target.put("new_done",true);journal.save(state);
            }
        }
        state.put("phase","SWAPPED");journal.save(state);
    }
    static void rollback(Journal journal,Moves moves)throws Exception {
        JSONObject state=journal.read();state.put("phase","ROLLING_BACK");journal.save(state);JSONArray targets=state.getJSONArray("targets");
        for(int i=targets.length()-1;i>=0;i--){
            JSONObject target=targets.getJSONObject(i);File live=file(target,"live"),old=file(target,"old");String source=target.optString("prepared","");
            // A process may have died immediately after a move but before its completion flag.
            boolean installed=!source.isEmpty()&&!new File(source).exists()&&live.exists()&&target.optBoolean("old_done");
            if(!target.optBoolean("new_rolled_back")){
                if(installed){File prepared=new File(source);moves.move(live,prepared);}
                else if(source.isEmpty()&&target.optBoolean("runtime_sidecar")&&target.optBoolean("new_done")&&target.optBoolean("old_done")&&live.exists())moves.move(live,new File(old.getParentFile(),target.getString("kind")+"_restored_sidecar"));
                else if(target.optBoolean("new_done")&&!source.isEmpty()&&live.exists()&&new File(source).exists())throw new IOException("RESTORE_ROLLBACK_NEW_STATE_AMBIGUOUS");
                target.put("new_rolled_back",true);journal.save(state);
            }
            if(!target.optBoolean("old_rolled_back")){
                if(target.optBoolean("old_present")){
                    if(old.exists()&&!live.exists())moves.move(old,live);
                    else if(old.exists()||!live.exists())throw new IOException("RESTORE_ROLLBACK_OLD_STATE_AMBIGUOUS");
                }else if(target.has("old_present")&&live.exists())throw new IOException("RESTORE_ROLLBACK_NEW_STATE_REMAINS");
                target.put("old_rolled_back",true);journal.save(state);
            }
        }
        state.put("phase","ROLLED_BACK");journal.save(state);
    }
    static JSONArray targets(Map<String,File> live,Map<String,File> prepared,File rollback)throws Exception {
        JSONArray out=new JSONArray();Set<String> paths=new HashSet<>();
        for(Map.Entry<String,File> item:live.entrySet()){
            String key=item.getKey();if(!key.matches("[a-z0-9_]{1,80}"))throw new IOException("RESTORE_EXCHANGE_TARGET_INVALID");File target=item.getValue().getCanonicalFile(),source=prepared.get(key),old=new File(rollback,key).getCanonicalFile();
            if(!paths.add(target.getPath())||target.equals(old)||source!=null&&target.equals(source.getCanonicalFile()))throw new IOException("RESTORE_EXCHANGE_TARGET_OVERLAP");
            out.put(new JSONObject().put("kind",key).put("live",target.getPath()).put("old",old.getPath()).put("prepared",source==null?"":source.getCanonicalPath()).put("runtime_sidecar",key.startsWith("database_")||key.startsWith("preferences_")&&key.endsWith("_bak")));
        }return out;
    }
    static boolean anyOldMoved(JSONObject journal)throws Exception {JSONArray targets=journal.optJSONArray("targets");if(targets==null)return false;for(int i=0;i<targets.length();i++){JSONObject target=targets.getJSONObject(i);if(target.optBoolean("old_done")||new File(target.getString("old")).exists())return true;}return false;}
    private static File file(JSONObject value,String key)throws Exception{return new File(value.getString(key));}
    private BackupDatasetExchange(){}
}
