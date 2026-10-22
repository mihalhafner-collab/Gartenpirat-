package at.hausgarten.service;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Compact only explicitly purged synthetic records. Business journals and backups stay intact. */
final class LocalTestCleanup {
    private static final String KEY="purged_local_test_records_v1";
    static void overlay(PixelLocalStore store,JSONObject master)throws Exception {
        JSONObject tombstones=new JSONObject(store.ownerValue(KEY,"{}"));
        for(Iterator<String> it=tombstones.keys();it.hasNext();){String key=it.next();LocalTestTombstone.project(master,key,tombstones.getJSONObject(key));}
    }
    static boolean sameTarget(JSONObject event,String entity,String id){
        String kind=event.optString("kind");JSONObject p=event.optJSONObject("payload");if(p==null||!id.equals(p.optString("id")))return false;
        return entity.equals(kind)||Arrays.asList("archive","restore","purge_v1").contains(kind)&&entity.equals(p.optString("entity"))||entity.equals("offer")&&kind.startsWith("offer_")||entity.equals("invoice")&&kind.startsWith("invoice_");
    }
    static boolean syntheticOrigin(JSONObject raw,JSONArray pending,String entity,String id)throws Exception {
        JSONObject record=OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)),id);
        if(record==null){JSONObject a=raw.optJSONObject("owner_archive"),saved=a==null?null:a.optJSONObject(entity+":"+id);record=saved==null?null:saved.optJSONObject("record");}
        for(int i=0;record==null&&i<pending.length();i++){JSONObject event=pending.getJSONObject(i),p=event.getJSONObject("payload");if(!sameTarget(event,entity,id)||!p.optBoolean("_local_only"))continue;record=p.optJSONObject("_archive_record");if(record==null&&entity.equals(event.optString("kind")))record=p;}
        if(record==null)return false;
        if(entity.equals("customer"))return TestDataRules.customer(record);
        if(TestDataRules.marked(record))return true;
        String customer=record.optString("customer_id");JSONObject c=OwnerJournalRules.findOrNull(raw.optJSONArray("customers"),customer);
        if(TestDataRules.customer(c))return true;
        for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i);if(a.optString("kind").equals("customer")&&a.getJSONObject("payload").optString("id").equals(customer)&&TestDataRules.customer(a.getJSONObject("payload")))return true;}
        return false;
    }
    static int compact(PixelLocalStore store)throws Exception {
        JSONArray initial=store.pendingActions();boolean purge=false;for(int i=0;i<initial.length();i++){JSONObject a=initial.getJSONObject(i);if(a.getJSONObject("payload").optBoolean("_local_only")&&Arrays.asList("purge_v1",TrashPurgeBatch.KIND).contains(a.optString("kind")))purge=true;}
        if(!purge){cleanupMedia(store);return 0;}
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();int removed=0;
        try{
            JSONObject state=store.getState(),raw=new JSONObject(store.snapshotText("{}"));JSONArray pending=store.pendingActions();
            JSONObject effective=PixelMcpReadTools.effective(state),archive=OwnerJournalRules.archives(effective),receipts=new JSONObject(store.ownerValue(KEY,"{}"));
            JSONObject session=state.getJSONObject("work_session");
            JSONArray candidates=new JSONArray();
            for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.getJSONObject("payload");if(!p.optBoolean("_local_only"))continue;
                if(a.optString("kind").equals("purge_v1"))candidates.put(a);
                else if(a.optString("kind").equals(TrashPurgeBatch.KIND)){JSONArray entries=p.getJSONArray("entries");for(int j=0;j<entries.length();j++){JSONObject e=entries.getJSONObject(j);String entity=e.getString("entity"),id=e.getString("entity_id");candidates.put(new JSONObject().put("client_id",a.getString("client_id")+":"+entity+":"+id).put("_backup_request",a.getString("client_id")).put("created_at",a.getString("created_at")).put("payload",new JSONObject().put("entity",entity).put("id",id)));}}
            }
            for(int i=0;i<candidates.length();i++){
                JSONObject action=candidates.getJSONObject(i),p=action.getJSONObject("payload");
                String entity=p.optString("entity"),id=p.optString("id"),key=entity+":"+id;JSONObject tombstone=archive.optJSONObject(key);
                if(tombstone==null||!tombstone.optBoolean("purged")||!action.optString("client_id").equals(tombstone.optString("purge_action"))||!syntheticOrigin(raw,pending,entity,id))continue;
                if(!Arrays.asList("IDLE","FINALIZED","CANCELLED").contains(session.optString("state"))&&(id.equals(session.optString(entity+"_id"))||id.equals(session.optString("finalized_worklog_id"))))continue;
                TrashRules.requireUnreferenced(effective,entity,id);
                // A real verified deletion backup is required even for old local test actions.
                JSONObject receipt=new JSONObject(store.ownerValue("receipt:"+action.optString("_backup_request",action.getString("client_id")),"{}")),result=receipt.optJSONObject("result");
                String backup=result==null?"":result.optString("deletion_backup_id");if(backup.isEmpty())continue;
                LocalOwnerRepository repo=new LocalOwnerRepository(store.appContext(),store);JSONObject meta=repo.fileMeta(backup);repo.inspectDeletionBackup(backup,meta);
                boolean local=OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)),id)==null&&!OwnerJournalRules.archives(raw).has(key);
                promoteSharedRemoval(store,raw,pending,entity,id,action);
                JSONObject compacted=new JSONObject(tombstone.toString()).put("local_test_only",local);
                JSONObject original=OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)),id),oldArchive=OwnerJournalRules.archives(raw).optJSONObject(key);
                if(original!=null)compacted.put("source_record_hash",OwnerJournalRules.version(original));if(oldArchive!=null)compacted.put("source_archive_hash",OwnerJournalRules.version(oldArchive));receipts.put(key,compacted);
                for(int j=0;j<pending.length();j++){JSONObject old=pending.getJSONObject(j);if(old.getJSONObject("payload").optBoolean("_local_only")&&sameTarget(old,entity,id)){markReceipt(store,old.getString("client_id"),entity,id);removed+=db.delete("pending","id=? AND kind!='mcp_receipt'",new String[]{old.getString("client_id")});}}
            }
            for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.getJSONObject("payload");if(!a.optString("kind").equals(TrashPurgeBatch.KIND)||!p.optBoolean("_local_only"))continue;boolean complete=true;JSONArray entries=p.getJSONArray("entries");for(int j=0;j<entries.length();j++){JSONObject e=entries.getJSONObject(j);if(!receipts.has(e.getString("entity")+":"+e.getString("entity_id")))complete=false;}if(complete){markReceipt(store,a.getString("client_id"),TrashPurgeBatch.KIND,"");removed+=db.delete("pending","id=?",new String[]{a.getString("client_id")});}}
            store.putOwnerValue(KEY,receipts.toString());db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        cleanupMedia(store);return removed;
    }
    private static void cleanupMedia(PixelLocalStore store)throws Exception {
        // Verified local-only originals can be collected without waiting for a Drive upload
        // which is intentionally forbidden. Shared records still require normal sync receipts.
        JSONObject tombstones=new JSONObject(store.ownerValue(KEY,"{}"));
        for(Iterator<String> it=tombstones.keys();it.hasNext();){String key=it.next();JSONObject receipt=tombstones.getJSONObject(key);if(!receipt.optBoolean("local_test_only"))continue;String marker="local_test_media_cleanup:"+PixelMcpWriteTools.hash(key);if(store.ownerValue(marker,"").equals("done"))continue;
            try{if(key.startsWith("photo:")&&receipt.has("media"))store.cleanupPurgedPhoto(key.substring(6),receipt.getJSONObject("media"),marker);else if(receipt.has("document"))store.cleanupPurgedDocument(key,receipt.getJSONObject("document"),marker);}
            catch(Exception pending){store.putOwnerValue("local_test_cleanup_error","MEDIA_CLEANUP_PENDING");}
        }
    }
    private static void markReceipt(PixelLocalStore store,String client,String entity,String id)throws Exception {
        // Migrate old receipts BEFORE removing their only journal provenance. Fingerprints stay immutable.
        SQLiteDatabase db=store.getWritableDatabase();String key="receipt_"+client;
        try(android.database.Cursor row=db.rawQuery("SELECT payload FROM pending WHERE id=? AND kind='mcp_receipt'",new String[]{key})){
            if(row.moveToFirst()){JSONObject saved=new JSONObject(row.getString(0));
                if(entity.equals(saved.optString("entity_type"))&&id.equals(saved.optString("entity_id",saved.optString("work_record_id")))){
                    saved.put("test_local_only",true).put("test_purged",true);ContentValues values=new ContentValues();values.put("payload",saved.toString());db.update("pending",values,"id=? AND kind='mcp_receipt'",new String[]{key});
                }
            }
        }
        String old=store.ownerValue("receipt:"+client,"");if(!old.isEmpty()){JSONObject saved=new JSONObject(old),result=saved.optJSONObject("result");if(result!=null){result.put("test_local_only",true).put("test_purged",true);store.putOwnerValue("receipt:"+client,saved.toString());}}
    }
    private static void promoteSharedRemoval(PixelLocalStore store,JSONObject raw,JSONArray pending,String entity,String id,JSONObject purge)throws Exception {
        JSONObject record=OwnerJournalRules.findOrNull(raw.optJSONArray(OwnerJournalRules.group(entity)),id),saved=OwnerJournalRules.archives(raw).optJSONObject(entity+":"+id);
        if(record==null&&(saved==null||saved.optBoolean("purged")))return; // Never upload a locally created test.
        JSONObject shared=new JSONObject(raw.toString());JSONArray existing=new JSONArray();for(int i=0;i<pending.length();i++)if(!pending.getJSONObject(i).getJSONObject("payload").optBoolean("_local_only"))existing.put(pending.get(i));DriveJournalRules.apply(shared,existing);
        if(TrashRules.purged(shared,entity,id))return;
        String stamp=purge.optString("created_at"),base="test_cleanup_"+PixelMcpWriteTools.hash(purge.getString("client_id")+":"+entity+":"+id).substring(0,40);JSONArray events=new JSONArray();
        if(!OwnerJournalRules.archives(shared).has(entity+":"+id)){
            JSONObject current=OwnerJournalRules.findOrNull(shared.getJSONArray(OwnerJournalRules.group(entity)),id);if(current==null)throw new IllegalArgumentException("Testoriginal fehlt für versionsgeschützte Bereinigung.");
            JSONObject event=new JSONObject().put("client_id",base+"_archive").put("kind","archive").put("created_at",stamp).put("payload",DeletionRules.payload(shared,entity,id,OwnerJournalRules.version(current),stamp));
            OwnerJournalRules.apply(shared,event,new HashMap<>(),new JSONObject());events.put(event);
        }
        JSONObject entry=OwnerJournalRules.archives(shared).getJSONObject(entity+":"+id),payload=new JSONObject().put("entity",entity).put("id",id).put("confirmed",true).put("expected_record_hash",OwnerJournalRules.version(entry)).put("updated_at",stamp);
        if(events.length()>0)payload.put("_sync_predecessors",new JSONArray().put(base+"_archive"));
        JSONObject event=new JSONObject().put("client_id",base+"_purge").put("kind","purge_v1").put("created_at",stamp).put("payload",payload);TrashRules.purge(shared,event);events.put(event);
        for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);ContentValues row=new ContentValues();row.put("id",e.getString("client_id"));row.put("kind",e.getString("kind"));row.put("created_at",stamp);row.put("payload",e.getJSONObject("payload").toString());store.getWritableDatabase().insertOrThrow("pending",null,row);}
    }
    private LocalTestCleanup(){}
}
