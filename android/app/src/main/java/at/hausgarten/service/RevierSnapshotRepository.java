package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Read-only semantic validation port for captured/restored SQLite images, never the active store. */
final class RevierSnapshotRepository implements RevierRepository {
    private final SQLiteDatabase database;private final JSONObject canonical;private final java.io.File photoDirectory;
    RevierSnapshotRepository(SQLiteDatabase database,JSONObject canonical){this(database,canonical,null);}
    RevierSnapshotRepository(SQLiteDatabase database,JSONObject canonical,java.io.File privateFiles){this.database=database;this.canonical=canonical;this.photoDirectory=privateFiles==null?null:new java.io.File(privateFiles,"customer-photo-originals");}
    public JSONObject effective()throws Exception{return new JSONObject(canonical.toString());}
    public String get(String key,String fallback)throws Exception{return DatabaseBackupSnapshot.ownerValue(database,key,fallback);}
    public void put(String key,String value){throw new IllegalStateException("REVIER_SNAPSHOT_READ_ONLY");}
    public JSONObject transaction(Work work){throw new IllegalStateException("REVIER_SNAPSHOT_READ_ONLY");}
    public JSONObject planInspection(JSONObject args){throw new IllegalStateException("REVIER_SNAPSHOT_READ_ONLY");}
    public JSONArray list(String prefix)throws Exception {JSONArray out=new JSONArray();try(Cursor rows=database.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){while(rows.moveToNext())out.put(new JSONObject(rows.getString(1)));}return out;}
    public JSONArray rawEntries(String prefix)throws Exception {JSONArray out=new JSONArray();try(Cursor rows=database.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){while(rows.moveToNext())out.put(new JSONObject().put("key",rows.getString(0)).put("value",rows.getString(1)));}return out;}
    public JSONObject session(String id)throws Exception {return RevierSessionContexts.session(database,id);}
    public JSONArray sessionContexts()throws Exception {return RevierSessionContexts.all(database);}
    public void verifyEquipmentProfilePhoto(JSONObject photo)throws Exception {EquipmentProfilePhotoRules.attachment(photo,BackupSnapshotState.photo(database,photo.getString("id")));EquipmentProfilePhotoRules.original(photoDirectory,photo);}
}
