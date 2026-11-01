package at.hausgarten.service;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Native backing for the existing browser draft keys. No business records or network effects. */
final class BrowserDraftRepository {
    static final String PREFIX="browser_drafts:v1:", ROW=PREFIX+"key:", HEAD=PREFIX+"head";
    private static final String REVIEW="restored_browser_checkpoint_requires_explicit_review";
    private static final int MAX_VALUE_BYTES=16*1024*1024, MAX_REQUEST_BYTES=32*1024*1024, MAX_MUTATIONS=10000;
    private final PixelLocalStore store;
    private final BackupDeviceGuard guard;

    static final class Failure extends IOException {
        final String code;
        Failure(String code){super(code);this.code=code;}
    }
    BrowserDraftRepository(Context context,PixelLocalStore store){this.store=store;guard=new BackupDeviceGuard(context);}

    static boolean selectable(String key){return RestoredBrowserReviewRules.selectable(key)||key.equals("hausgarten.invoice.issue.action.v1")||key.equals("hausgarten.offer.order.action.v1");}
    private static Failure fail(String code){return new Failure(code);}
    private static void fields(JSONObject value,String... allowed)throws Exception {
        Set<String> names=new HashSet<>(Arrays.asList(allowed));for(Iterator<String> keys=value.keys();keys.hasNext();)if(!names.contains(keys.next()))throw fail("EDITOR_STORAGE_INVALID");
    }
    private static String text(JSONObject value,String key)throws Exception {Object raw=value.opt(key);if(!(raw instanceof String))throw fail("EDITOR_STORAGE_INVALID");return (String)raw;}
    private static void version(JSONObject value)throws Exception {Object raw=value.opt("formatVersion");if(!(raw instanceof Integer)||((Integer)raw)!=1)throw fail("EDITOR_STORAGE_INVALID");}
    private static JSONObject identity(JSONObject value)throws Exception {
        fields(value,"installationId","restoreEpoch");String installation=text(value,"installationId"),epoch=text(value,"restoreEpoch");if(!installation.matches("[0-9a-f-]{36}")||!epoch.matches("[0-9a-f-]{36}"))throw fail("EDITOR_STORAGE_INVALID");return new JSONObject().put("installationId",installation).put("restoreEpoch",epoch);
    }
    private JSONObject current()throws Exception {JSONObject value=guard.current();return identity(new JSONObject().put("installationId",value.get("installationId")).put("restoreEpoch",value.get("restoreEpoch")));}
    private static boolean same(JSONObject a,JSONObject b)throws Exception {return a.getString("installationId").equals(b.getString("installationId"))&&a.getString("restoreEpoch").equals(b.getString("restoreEpoch"));}
    private static String key(JSONObject value)throws Exception {String key=text(value,"key");if(!selectable(key))throw fail("EDITOR_STORAGE_INVALID");return key;}
    private static String request(JSONObject value)throws Exception {String request=text(value,"request_id");if(!request.matches("[A-Za-z0-9_.:-]{8,200}"))throw fail("EDITOR_STORAGE_INVALID");return request;}
    private static JSONObject parse(String raw)throws Exception {
        if(raw==null||raw.length()>MAX_REQUEST_BYTES||raw.getBytes(StandardCharsets.UTF_8).length>MAX_REQUEST_BYTES)throw fail("EDITOR_STORAGE_INVALID");JSONObject value;try{value=new JSONObject(raw);}catch(Exception invalid){throw fail("EDITOR_STORAGE_INVALID");}version(value);return value;
    }
    private static boolean review(SQLiteDatabase database)throws Exception {String value=DatabaseBackupSnapshot.ownerValue(database,REVIEW,null);if(value==null||value.equals("false"))return false;if(value.equals("true"))return true;throw fail("EDITOR_STORAGE_INVALID");}
    private static String storageKey(String key)throws Exception {return ROW+PixelMcpWriteTools.hash(key);}
    private static long revision(JSONObject row)throws Exception {Object value=row.opt("revision");if(!(value instanceof Integer)&&!(value instanceof Long))throw fail("EDITOR_STORAGE_INVALID");long revision=((Number)value).longValue();if(revision<1)throw fail("EDITOR_STORAGE_INVALID");return revision;}
    private static JSONObject row(SQLiteDatabase database,String storageKey)throws Exception {
        String raw=DatabaseBackupSnapshot.ownerValue(database,storageKey,null);if(raw==null)return null;JSONObject row;
        try{row=new JSONObject(raw);version(row);fields(row,"formatVersion","key","context","revision","present","value","last_request_id","last_fingerprint");String key=key(row);if(!storageKey.equals(storageKey(key)))throw fail("EDITOR_STORAGE_INVALID");identity(row.getJSONObject("context"));revision(row);if(!(row.opt("present") instanceof Boolean)||!row.has("value")||!(row.get("value") instanceof String)&&row.get("value")!=JSONObject.NULL)throw fail("EDITOR_STORAGE_INVALID");if(row.getBoolean("present")!=(row.get("value") instanceof String))throw fail("EDITOR_STORAGE_INVALID");if(row.get("value") instanceof String&&row.getString("value").getBytes(StandardCharsets.UTF_8).length>MAX_VALUE_BYTES)throw fail("EDITOR_STORAGE_INVALID");request(new JSONObject().put("request_id",row.get("last_request_id")));if(!text(row,"last_fingerprint").matches("[0-9a-f]{64}"))throw fail("EDITOR_STORAGE_INVALID");return row;}catch(Failure invalid){throw invalid;}catch(Exception invalid){throw fail("EDITOR_STORAGE_INVALID");}
    }
    private static JSONObject head(SQLiteDatabase database)throws Exception {
        String raw=DatabaseBackupSnapshot.ownerValue(database,HEAD,null);if(raw==null)return null;try{JSONObject value=new JSONObject(raw);version(value);fields(value,"formatVersion","context","last_request_id","last_fingerprint");identity(value.getJSONObject("context"));request(new JSONObject().put("request_id",value.get("last_request_id")));if(!text(value,"last_fingerprint").matches("[0-9a-f]{64}"))throw fail("EDITOR_STORAGE_INVALID");return value;}catch(Failure invalid){throw invalid;}catch(Exception invalid){throw fail("EDITOR_STORAGE_INVALID");}
    }
    private static List<String> storedKeys(SQLiteDatabase database)throws Exception {
        List<String> result=new ArrayList<>();
        // Only keys cross CursorWindow; large string values use the existing chunked reader.
        try(Cursor rows=database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{"browser_drafts:","browser_drafts:\uffff"})){while(rows.moveToNext()){String key=rows.getString(0);if(key.equals(HEAD))continue;if(!key.matches("browser_drafts:v1:key:[0-9a-f]{64}"))throw fail("EDITOR_STORAGE_INVALID");result.add(key);}}
        return result;
    }
    private static JSONObject state(String key,JSONObject row,JSONObject context,boolean held)throws Exception {
        boolean initialized=!held&&row!=null&&same(row.getJSONObject("context"),context);long revision=initialized?revision(row):0;boolean present=initialized&&row.getBoolean("present");Object value=present?row.get("value"):JSONObject.NULL;
        JSONObject state=new JSONObject().put("key",key).put("initialized",initialized).put("present",present).put("value",value).put("revision",revision);
        state.put("record_hash",PixelMcpWriteTools.hash(new JSONObject(state.toString()).put("formatVersion",1).put("context",context)));return state;
    }
    private static JSONArray states(SQLiteDatabase database,JSONObject context,boolean held)throws Exception {
        TreeMap<String,JSONObject> values=new TreeMap<>();head(database);for(String storageKey:storedKeys(database)){JSONObject row=row(database,storageKey);if(row==null)throw fail("EDITOR_STORAGE_INVALID");if(!held&&same(row.getJSONObject("context"),context))values.put(row.getString("key"),state(row.getString("key"),row,context,false));}JSONArray result=new JSONArray();for(JSONObject state:values.values())result.put(state);return result;
    }
    private static String digest(JSONObject context,boolean held,JSONArray states)throws Exception {JSONArray hashes=new JSONArray();for(int i=0;i<states.length();i++){JSONObject state=states.getJSONObject(i);hashes.put(new JSONObject().put("key",state.getString("key")).put("record_hash",state.getString("record_hash")));}return PixelMcpWriteTools.hash(new JSONObject().put("formatVersion",1).put("context",context).put("review_required",held).put("states",hashes));}
    /** Same hash as action=list, using only the supplied captured database and guard identity. */
    static String snapshotDigest(SQLiteDatabase database,JSONObject identity)throws Exception {JSONObject context=identity(new JSONObject().put("installationId",identity.get("installationId")).put("restoreEpoch",identity.get("restoreEpoch")));boolean held=review(database);return digest(context,held,states(database,context,held));}
    /** Bind the browser checkpoint to the very same captured SQLite draft values and epoch. */
    static void validateCaptured(SQLiteDatabase database,JSONObject sourceIdentity,JSONObject browser)throws IOException {
        try{
            boolean declared=browser!=null&&browser.has("editor_state"),hasNamespace;
            try(Cursor keys=database.rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<? LIMIT 1",new String[]{"browser_drafts:","browser_drafts:\uffff"})){hasNamespace=keys.moveToFirst();}
            // Old, explicitly synthetic/native-only captures predate both contracts.
            if(!hasNamespace&&!declared)return;
            JSONObject context=identity(new JSONObject().put("installationId",sourceIdentity.get("installationId")).put("restoreEpoch",sourceIdentity.get("restoreEpoch")));boolean held=review(database);JSONArray current=states(database,context,false);
            JSONObject descriptor=browser.optJSONObject("editor_state"),storage=browser.optJSONObject("storage");if(descriptor==null||storage==null)throw fail("EDITOR_STORAGE_INVALID");version(descriptor);fields(descriptor,"formatVersion","context","review_required","record_hash");if(!(descriptor.opt("review_required") instanceof Boolean)||descriptor.getBoolean("review_required")!=held||!same(identity(descriptor.getJSONObject("context")),context)||!text(descriptor,"record_hash").equals(digest(context,held,held?new JSONArray():current)))throw fail("EDITOR_STORAGE_INVALID");
            Set<String> backed=new HashSet<>();for(int i=0;i<current.length();i++){JSONObject state=current.getJSONObject(i);String key=state.getString("key");backed.add(key);if(state.getBoolean("present")){if(!(storage.opt(key) instanceof String)||!state.getString("value").equals(storage.getString(key)))throw fail("EDITOR_STORAGE_INVALID");}else if(storage.has(key))throw fail("EDITOR_STORAGE_INVALID");}
            // During restore review raw browser values are deliberately separate sources.
            if(!held)for(Iterator<String> keys=storage.keys();keys.hasNext();){String key=keys.next();if(selectable(key)&&!backed.contains(key))throw fail("EDITOR_STORAGE_INVALID");}
        }catch(Exception invalid){throw new IOException("BACKUP_BROWSER_EDITOR_STATE_MISMATCH");}
    }
    private static JSONObject envelope(JSONObject context,boolean held)throws Exception {return new JSONObject().put("formatVersion",1).put("context",context).put("review_required",held);}
    private void unchangedContext(JSONObject expected)throws Exception {if(!same(expected,current()))throw fail("EDITOR_STORAGE_CONTEXT_CHANGED");}

    JSONObject read(String raw)throws Exception {
        JSONObject input=parse(raw);String action=text(input,"action");if(action.equals("get"))fields(input,"formatVersion","action","key");else if(action.equals("list"))fields(input,"formatVersion","action");else throw fail("EDITOR_STORAGE_INVALID");String wanted=action.equals("get")?key(input):null;JSONObject context=current();SQLiteDatabase database=store.getReadableDatabase();database.beginTransaction();JSONObject result;
        try{unchangedContext(context);boolean held=review(database);result=envelope(context,held);if(wanted!=null){head(database);storedKeys(database);JSONObject state=state(wanted,row(database,storageKey(wanted)),context,held);for(Iterator<String> keys=state.keys();keys.hasNext();){String key=keys.next();result.put(key,state.get(key));}}else{JSONArray states=states(database,context,held);result.put("states",states).put("record_hash",digest(context,held,states));}unchangedContext(context);database.setTransactionSuccessful();}finally{database.endTransaction();}return result;
    }
    private static void put(SQLiteDatabase database,String key,JSONObject value)throws Exception {ContentValues content=new ContentValues();content.put("key",key);content.put("value",value.toString());if(database.insertWithOnConflict("owner_state",null,content,SQLiteDatabase.CONFLICT_REPLACE)<0)throw fail("EDITOR_STORAGE_SAVE_FAILED");}
    JSONObject write(String raw)throws Exception {
        JSONObject input=parse(raw);fields(input,"formatVersion","request_id","context","mutations");String request=request(input);JSONObject suppliedContext=input.optJSONObject("context");if(suppliedContext==null)throw fail("EDITOR_STORAGE_INVALID");JSONObject expected=identity(suppliedContext);JSONArray mutations=input.optJSONArray("mutations");if(mutations==null||mutations.length()<1||mutations.length()>MAX_MUTATIONS)throw fail("EDITOR_STORAGE_INVALID");TreeMap<String,JSONObject> changes=new TreeMap<>();
        for(int i=0;i<mutations.length();i++){JSONObject change=mutations.optJSONObject(i);if(change==null)throw fail("EDITOR_STORAGE_INVALID");fields(change,"key","expected_record_hash","value");String key=key(change);if(changes.put(key,change)!=null||!text(change,"expected_record_hash").matches("[0-9a-f]{64}")||!change.has("value"))throw fail("EDITOR_STORAGE_INVALID");Object value=change.get("value");if(value!=JSONObject.NULL&&!(value instanceof String)||value instanceof String&&((String)value).getBytes(StandardCharsets.UTF_8).length>MAX_VALUE_BYTES)throw fail("EDITOR_STORAGE_INVALID");}
        JSONObject context=current();if(!same(expected,context))throw fail("EDITOR_STORAGE_CONTEXT_CHANGED");String fingerprint=PixelMcpWriteTools.hash(input);SQLiteDatabase database=store.getWritableDatabase();database.beginTransaction();JSONObject result;
        try{
            unchangedContext(context);if(review(database))throw fail("EDITOR_STORAGE_RESTORE_REVIEW_REQUIRED");storedKeys(database);JSONObject head=head(database);if(head!=null&&same(head.getJSONObject("context"),context)&&request.equals(head.getString("last_request_id"))&&!fingerprint.equals(head.getString("last_fingerprint")))throw fail("EDITOR_STORAGE_REPLAY_MISMATCH");
            TreeMap<String,JSONObject> before=new TreeMap<>();boolean replay=true;
            for(String key:changes.keySet()){JSONObject row=row(database,storageKey(key));before.put(key,row);boolean sameContext=row!=null&&same(row.getJSONObject("context"),context),sameRequest=sameContext&&request.equals(row.getString("last_request_id"));if(sameRequest&&!fingerprint.equals(row.getString("last_fingerprint")))throw fail("EDITOR_STORAGE_REPLAY_MISMATCH");if(sameRequest){Object saved=row.get("value"),wanted=changes.get(key).get("value");if(saved==JSONObject.NULL?wanted!=JSONObject.NULL:!(wanted instanceof String)||!saved.equals(wanted))throw fail("EDITOR_STORAGE_INVALID");}if(!sameRequest)replay=false;}
            // Replayed batches retain their committed revisions. Every CAS is checked before any write.
            if(!replay){for(String key:changes.keySet())if(!changes.get(key).getString("expected_record_hash").equals(state(key,before.get(key),context,false).getString("record_hash")))throw fail("EDITOR_STORAGE_CONFLICT");unchangedContext(context);
                for(String key:changes.keySet()){JSONObject old=before.get(key);long next=old==null?1:Math.addExact(revision(old),1);Object value=changes.get(key).get("value");JSONObject changed=new JSONObject().put("formatVersion",1).put("key",key).put("context",context).put("revision",next).put("present",value instanceof String).put("value",value).put("last_request_id",request).put("last_fingerprint",fingerprint);put(database,storageKey(key),changed);before.put(key,changed);}
                put(database,HEAD,new JSONObject().put("formatVersion",1).put("context",context).put("last_request_id",request).put("last_fingerprint",fingerprint));
            }
            JSONArray changedStates=new JSONArray();for(String key:changes.keySet())changedStates.put(state(key,before.get(key),context,false));JSONArray all=states(database,context,false);result=envelope(context,false).put("states",changedStates).put("record_hash",digest(context,false,all)).put("replayed",replay);unchangedContext(context);database.setTransactionSuccessful();
        }finally{database.endTransaction();}return result;
    }
    static String responseFailure(Exception failure){
        String code=failure instanceof Failure?((Failure)failure).code:"EDITOR_STORAGE_SAVE_FAILED",message;
        switch(code){case "EDITOR_STORAGE_INVALID":message="Der Entwurfsauftrag ist nicht lesbar. Eingaben erhalten und die Ansicht erneut öffnen.";break;case "EDITOR_STORAGE_CONFLICT":message="Der Entwurf wurde inzwischen geändert. Den aktuellen Stand erneut lesen; keine Änderungen überschreiben.";break;case "EDITOR_STORAGE_CONTEXT_CHANGED":message="Der Gerätestand wurde gewechselt. Die aktuelle Ansicht erneut öffnen; ältere Entwürfe bleiben getrennt.";break;case "EDITOR_STORAGE_REPLAY_MISMATCH":message="Diese Aktionskennung gehört zu anderen Entwurfsangaben. Die ursprüngliche Aktion unverändert prüfen.";break;case "EDITOR_STORAGE_RESTORE_REVIEW_REQUIRED":message="Erhaltene Entwürfe nach der Wiederherstellung zuerst ausdrücklich prüfen.";break;default:code="EDITOR_STORAGE_SAVE_FAILED";message="Der Entwurf konnte noch nicht als dauerhaft gespeichert bestätigt werden. Dieselbe Aktion erneut prüfen.";}
        try{return new JSONObject().put("ok",false).put("error",new JSONObject().put("code",code).put("message",message)).toString();}catch(Exception impossible){return "{\"ok\":false,\"error\":{\"code\":\"EDITOR_STORAGE_SAVE_FAILED\",\"message\":\"Entwurf erneut prüfen.\"}}";}
    }
}
