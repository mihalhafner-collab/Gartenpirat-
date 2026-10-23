package at.hausgarten.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Private captured inputs for the encrypted writer; no provider, network or activation. */
final class LocalBackupDataset {
    static final class Captured {
        final File directory;
        final JSONObject metadata;
        Captured(File directory,JSONObject metadata){this.directory=directory;this.metadata=metadata;}
        List<EncryptedBackupArchive.Source> sources()throws Exception{return BackupFilePins.sources(directory);}
    }
    static Captured capture(Context context,PixelLocalStore store,File freshDirectory,JSONObject identity,JSONObject browserStorage)throws Exception {
        if(browserStorage==null||browserStorage.optInt("formatVersion")!=1||browserStorage.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_REQUIRED");
        String stage=freshDirectory.getCanonicalPath();
        for(File source:new File[]{context.getFilesDir(),context.getCacheDir(),new File(context.getNoBackupFilesDir(),"deletion-backups"),new File(context.getNoBackupFilesDir(),"legacy-conflict-backups")})if(stage.equals(source.getCanonicalPath())||stage.startsWith(source.getCanonicalPath()+File.separator))throw new IOException("BACKUP_CAPTURE_RECURSIVE_TARGET_REJECTED");
        if(freshDirectory.exists()||!freshDirectory.mkdirs())throw new IOException("BACKUP_CAPTURE_TARGET_EXISTS_OR_UNAVAILABLE");
        String initial=asset(context,"initial-data.json"),catalog=asset(context,"service-catalog.json"),started=java.time.Instant.now().toString();
        // Reuse local-only legacy materialization. IDs/amounts/source snapshots are unchanged;
        // the existing content hashes make original-file caching idempotent. Never fetch Drive.
        JSONObject raw=new JSONObject(store.snapshotText(initial));store.recordSnapshotPhotoAttachments(raw);store.documentReferences(raw);
        JSONObject current=PixelMcpReadTools.effective(store.getState());store.recordSnapshotPhotoAttachments(current);store.documentReferences(current);
        BackupFilePins.pinImmutable(new File(context.getFilesDir(),"customer-photo-originals"),freshDirectory,"files/customer-photo-originals");
        BackupFilePins.pinImmutable(new File(context.getFilesDir(),"business-documents"),freshDirectory,"files/business-documents");
        JSONObject preferences=BackupPreferences.capture(context);
        write(new File(freshDirectory,"preferences.json"),preferences.toString());write(new File(freshDirectory,"browser-storage.json"),browserStorage.toString());
        write(new File(freshDirectory,"bootstrap/initial-data.json"),initial);write(new File(freshDirectory,"bootstrap/service-catalog.json"),catalog);
        File database=new File(freshDirectory,"database.sqlite");JSONObject db=DatabaseBackupSnapshot.capture(store.getWritableDatabase(),database);
        BackupFilePins.capture(context.getFilesDir(),freshDirectory,"files");
        for(String name:new String[]{"photo-capture","invoice-share"})BackupFilePins.capture(new File(context.getCacheDir(),name),freshDirectory,"cache/"+name);
        // Keystore/recovery capsules and our own staging trees are deliberately not exported.
        // Existing deletion/conflict backups remain immutable evidence, inside this encryption.
        for(String name:new String[]{"deletion-backups","legacy-conflict-backups"})BackupFilePins.capture(new File(context.getNoBackupFilesDir(),name),freshDirectory,"no_backup/"+name);
        JSONObject inventory,state;
        try(SQLiteDatabase snapshot=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            state=BackupSnapshotState.read(snapshot,BackupPreferences.values(preferences,"haus_garten"),initial,catalog);
            inventory=LocalMigrationInventory.inspect(state,new LocalOriginalVerifier(new File(freshDirectory,"files"),id->BackupSnapshotState.photo(snapshot,id)));
        }
        if(inventory.getInt("missing_local_originals")!=0)throw new IOException("BACKUP_LOCAL_ORIGINALS_INCOMPLETE");
        JSONObject metadata=new JSONObject(identity.toString()).put("schemaVersion",db.getInt("schemaVersion")).put("applicationId",context.getPackageName()).put("appVersion",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
            .put("captureStartedAt",started).put("capturedAt",java.time.Instant.now().toString()).put("counts",inventory.getJSONObject("counts")).put("database",db).put("inventory",inventory)
            .put("numberState",new JSONObject().put("business_settings",SnapshotProjection.effective(state).optJSONObject("business_settings")).put("external_current_number_proof",false))
            .put("lastIssuedDocumentIds",issued(SnapshotProjection.effective(state))).put("captureScope","database_all_tables_private_files_preferences_browser_storage_and_legacy_backups")
            .put("structured_preference_credentials_excluded",true).put("legacy_backup_evidence_activation","FORBIDDEN").put("external_account_activation","REQUIRES_DEVICE_REAUTHORIZATION").put("local_authority_activated",false).put("invoice_release_after_restore","BLOCKED_UNTIL_CURRENT_EXTERNAL_RECONCILIATION")
            .put("device_specific_exclusions",new JSONArray().put("Android_Keystore").put("backup_key_protection").put("account_tokens").put("running_backup_staging"));
        return new Captured(freshDirectory,metadata);
    }
    static JSONObject inspectStaged(File directory,JSONObject manifest)throws Exception {
        File database=new File(directory,"database.sqlite");JSONObject db=DatabaseBackupSnapshot.inspect(database);
        if(db.getInt("schemaVersion")!=manifest.getInt("schemaVersion")||!db.getString("sha256").equals(manifest.getJSONObject("database").getString("sha256")))throw new IOException("BACKUP_DATABASE_MANIFEST_MISMATCH");
        JSONObject preferences=new JSONObject(text(new File(directory,"preferences.json"))),browser=new JSONObject(text(new File(directory,"browser-storage.json")));
        for(String name:BackupPreferences.GROUPS)BackupPreferences.values(preferences,name);
        if(browser.optInt("formatVersion")!=1||browser.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_INVALID");
        try(SQLiteDatabase snapshot=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            JSONObject state=BackupSnapshotState.read(snapshot,BackupPreferences.values(preferences,"haus_garten"),text(new File(directory,"bootstrap/initial-data.json")),text(new File(directory,"bootstrap/service-catalog.json")));
            JSONObject inventory=LocalMigrationInventory.inspect(state,new LocalOriginalVerifier(new File(directory,"files"),id->BackupSnapshotState.photo(snapshot,id)));
            JSONObject original=manifest.getJSONObject("inventory");
            for(String field:new String[]{"counts","id_set_sha256","collection_sha256","pending_sha256","historical_hours_decimal","historical_invoice_amounts_decimal"})if(!PixelMcpWriteTools.hash(inventory.get(field)).equals(PixelMcpWriteTools.hash(original.get(field))))throw new IOException("BACKUP_BUSINESS_INVENTORY_MISMATCH");
            if(inventory.getInt("missing_local_originals")!=0)throw new IOException("BACKUP_LOCAL_ORIGINALS_INCOMPLETE");return inventory;
        }
    }
    private static JSONArray issued(JSONObject effective)throws Exception {
        TreeSet<String> ids=new TreeSet<>();JSONArray invoices=effective.getJSONArray("invoices");for(int i=0;i<invoices.length();i++){JSONObject row=invoices.getJSONObject(i);if(!row.optString("issued_at").isEmpty())ids.add(row.getString("id"));}return new JSONArray(ids);
    }
    private static String asset(Context context,String name)throws Exception {try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String text(File file)throws Exception {return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private static void write(File file,String value)throws Exception {File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_STAGING_UNAVAILABLE");try(FileOutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    private LocalBackupDataset(){}
}
