package at.hausgarten.service;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;

/** Existing owner_state storage and transaction adapter; no second operative database. */
final class BrowserMediaRepository {
    interface Identity {JSONObject current()throws Exception;}
    private final BrowserMediaRepositoryRules rules;
    BrowserMediaRepository(Context context,PixelLocalStore store)throws Exception {try{checkNamespaceVersion(store.getReadableDatabase());rules=new BrowserMediaRepositoryRules(new Values(store.getWritableDatabase(),()->new BackupDeviceGuard(context).current()),new File(context.getFilesDir(),"browser-media"));}catch(Exception failure){throw safe(failure);}}
    private static final Set<String> SAFE_CODES=new HashSet<>(Arrays.asList("CONTEXT_INVALID","VERSION_UNSUPPORTED","CONTEXT_CHANGED","STATE_INVALID","KEY_INVALID","REQUEST_ID_REQUIRED","REPLAY_MISMATCH","RECORD_INVALID","RECORD_MISSING","GRAPH_TOO_LARGE","WRITE_CONFLICT","RESTORE_REVIEW_REQUIRED","MIRROR_REQUIRED","SOURCE_INVALID","MIRROR_NOT_ALLOWED","MIRROR_CLOSED","DUPLICATE_KEY","MIRROR_INCOMPLETE","ACTIVE_CONFLICT","RESTORE_MIRROR_REQUIRED","SELECTION_INVALID","SELECTION_REQUIRED","REFERENCE_NOT_ACTIVE","REFERENCE_INVALID","PATH_INVALID","STORAGE_UNAVAILABLE","UPLOAD_INVALID","UPLOAD_REVIEW_REQUIRED","SIZE_INVALID","MIME_INVALID","CHUNK_INVALID","CHUNK_ORDER_INVALID","UPLOAD_CLOSED","UPLOAD_INCOMPLETE","ORIGINAL_MISSING_OR_CHANGED","ORIGINAL_MISSING","GRAPH_INVALID","GRAPH_SECRET_FIELD","INLINE_MEDIA_FORBIDDEN","SPACE_REQUIRED","INTERRUPTED","INPUT_INVALID","WRITE_PREPARATION_REQUIRED","WRITE_PENDING","WRITE_CANCELLED","WRITE_ALREADY_COMMITTED"));
    private static IOException safe(Exception failure){String code=failure.getMessage(),prefix="BROWSER_MEDIA_";return new IOException(code!=null&&code.startsWith(prefix)&&SAFE_CODES.contains(code.substring(prefix.length()))?code:prefix+"STATE_INVALID");}
    private JSONObject run(BrowserMediaRepositoryRules.Work work)throws Exception {try{return work.run();}catch(Exception failure){throw safe(failure);}}
    JSONObject status()throws Exception {return run(rules::status);}
    JSONObject deferRestoreReview(String request)throws Exception {return run(()->rules.deferRestoreReview(request));}
    JSONObject readRecord(String key)throws Exception {return run(()->rules.readRecord(key));}
    JSONObject prepareRecordWrite(String request,String key,String expected)throws Exception {return run(()->rules.prepareRecordWrite(request,key,expected));}
    JSONObject cancelRecordWrite(String request,String key,String expected,String reason)throws Exception {return run(()->rules.cancelRecordWrite(request,key,expected,reason));}
    JSONObject writeRecord(String request,String key,String expected,JSONObject graph)throws Exception {return run(()->rules.writeRecord(request,key,expected,graph));}
    JSONObject cleanupFiles()throws Exception{return run(rules::cleanupFiles);}
    JSONObject cleanupDeletedKey(String key,String request)throws Exception {return run(()->rules.cleanupDeletedKey(key,request));}
    JSONObject beginMirror(String request,String source)throws Exception {return run(()->rules.beginMirror(request,source));}
    JSONObject writeMirrorRecord(String request,String source,String key,JSONObject graph)throws Exception {return run(()->rules.writeMirrorRecord(request,source,key,graph));}
    JSONObject completeMirror(String request,String source,JSONArray keys)throws Exception {JSONObject result=run(()->rules.completeMirror(request,source,keys));try{rules.cleanupFiles();}catch(Exception deferred){result.put("cleanup_pending",true);}return result;}
    JSONObject reviewCandidates()throws Exception {return run(rules::reviewCandidates);}
    JSONObject prepareRestoreSelection(String request,String expected,JSONArray selected)throws Exception {return run(()->rules.prepareRestoreSelection(request,expected,selected));}
    JSONObject verifyPreparedRestoreSelection(String request,String expected,JSONArray selected,String selectionHash)throws Exception {return run(()->rules.verifyPreparedRestoreSelection(request,expected,selected,selectionHash));}
    JSONObject activateSelection(String request,String selectionHash)throws Exception {
        JSONObject before=rules.reviewCandidates(),result=run(()->rules.activateSelection("media_activate_"+PixelMcpWriteTools.hash(request),selectionHash));Set<String> keys=new TreeSet<>();JSONArray candidates=before.getJSONArray("candidates");for(int j=0;j<candidates.length();j++)keys.add(candidates.getJSONObject(j).getString("key"));for(String key:keys)try{JSONObject current=rules.readRecord(key);String cleanup="media_selection_cleanup_"+PixelMcpWriteTools.hash(request+":"+key);rules.prepareRecordWrite(cleanup,key,current.getString("record_hash"));rules.writeRecord(cleanup,key,current.getString("record_hash"),current.isNull("record")?null:current.getJSONObject("record"));rules.cleanupDeletedKey(key,cleanup);}catch(Exception deferred){result.put("cleanup_pending",true);}return result;
    }
    void assertReadableReference(String id,JSONObject ref)throws Exception {try{rules.assertReadableReference(id,ref);}catch(Exception failure){throw safe(failure);}}
    static void installRestored(SQLiteDatabase database,String request,JSONObject identity)throws Exception {try{new BrowserMediaRepositoryRules(new Values(database,()->identity),null).installRestored(request);}catch(Exception failure){throw safe(failure);}}
    static boolean hasCapturedNamespace(SQLiteDatabase database)throws Exception {try(Cursor rows=database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? LIMIT 1",new String[]{"browser_media:","browser_media:\uffff"})){return rows.moveToFirst();}catch(Exception failure){throw safe(failure);}}
    private static void checkNamespaceVersion(SQLiteDatabase database)throws Exception {try(Cursor rows=database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<?",new String[]{"browser_media:","browser_media:\uffff"})){while(rows.moveToNext())if(!rows.getString(0).startsWith(BrowserMediaRepositoryRules.PREFIX))throw new IOException("BROWSER_MEDIA_VERSION_UNSUPPORTED");}}
    static JSONObject inspectCaptured(SQLiteDatabase database,File files)throws Exception {try{checkNamespaceVersion(database);String raw=DatabaseBackupSnapshot.ownerValue(database,BrowserMediaRepositoryRules.PREFIX+"state","");if(raw.isEmpty())throw new IOException("BROWSER_MEDIA_MIRROR_REQUIRED");JSONObject state=new JSONObject(raw),identity=new JSONObject().put("installationId",state.getString("installation_id")).put("restoreEpoch",state.getString("restore_epoch"));return new BrowserMediaRepositoryRules(new Values(database,()->identity),new File(files,"browser-media")).inspectCaptured();}catch(Exception failure){throw safe(failure);}}
    private static final class Values implements BrowserMediaRepositoryRules.Store {
        private final SQLiteDatabase database;private final Identity identity;
        Values(SQLiteDatabase database,Identity identity){this.database=database;this.identity=identity;}
        public JSONObject identity()throws Exception{return identity.current();}
        public String get(String key,String fallback)throws Exception{return DatabaseBackupSnapshot.ownerValue(database,key,fallback);}
        public void put(String key,String value){ContentValues row=new ContentValues();row.put("key",key);row.put("value",value);database.insertWithOnConflict("owner_state",null,row,SQLiteDatabase.CONFLICT_REPLACE);}
        public void remove(String key){database.delete("owner_state","key=?",new String[]{key});}
        public JSONArray entries(String prefix)throws Exception {JSONArray entries=new JSONArray();try(Cursor rows=database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){while(rows.moveToNext()){String key=rows.getString(0);entries.put(new JSONObject().put("key",key).put("value",new JSONObject(get(key,"{}"))));}}return entries;}
        public JSONObject transaction(BrowserMediaRepositoryRules.Work work)throws Exception {database.beginTransaction();try{JSONObject result=work.run();database.setTransactionSuccessful();return result;}finally{database.endTransaction();}}
        public boolean fileDeletionAllowed(){return !database.inTransaction();}
    }
}
