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
    static Captured capture(Context context,PixelLocalStore store,File freshDirectory,JSONObject identity,JSONObject browserStorage)throws Exception {synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK){return captureLocked(context,store,freshDirectory,identity,browserStorage);}}
    private static Captured captureLocked(Context context,PixelLocalStore store,File freshDirectory,JSONObject identity,JSONObject browserStorage)throws Exception {
        String stageName="READINESS";try{
        if(browserStorage==null||browserStorage.optInt("formatVersion")!=1||browserStorage.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_REQUIRED");
        JSONObject mediaReadiness=new BrowserMediaRepository(context,store).status();if(!mediaReadiness.optBoolean("mirror_complete")||mediaReadiness.optString("mode").equals("MIRROR_REQUIRED"))throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");
        String stage=freshDirectory.getCanonicalPath();
        for(File source:new File[]{context.getFilesDir(),context.getCacheDir(),new File(context.getNoBackupFilesDir(),"deletion-backups"),new File(context.getNoBackupFilesDir(),"legacy-conflict-backups")})if(stage.equals(source.getCanonicalPath())||stage.startsWith(source.getCanonicalPath()+File.separator))throw new IOException("BACKUP_CAPTURE_RECURSIVE_TARGET_REJECTED");
        if(freshDirectory.exists()||!freshDirectory.mkdirs())throw new IOException("BACKUP_CAPTURE_TARGET_EXISTS_OR_UNAVAILABLE");
        String initial=asset(context,"initial-data.json"),catalog=asset(context,"service-catalog.json"),started=java.time.Instant.now().toString();
        // Reuse local-only legacy materialization. IDs/amounts/source snapshots are unchanged;
        // the existing content hashes make original-file caching idempotent. Never fetch Drive.
        stageName="MATERIALIZE";JSONObject raw=store.rawSnapshot(initial);store.recordSnapshotPhotoAttachments(raw);store.documentReferences(raw);
        JSONObject current=PixelMcpReadTools.effective(store.getState());store.recordSnapshotPhotoAttachments(current);store.documentReferences(current);
        stageName="PIN_FILES";BackupFilePins.pinImmutable(new File(context.getFilesDir(),"customer-photo-originals"),freshDirectory,"files/customer-photo-originals");
        BackupFilePins.pinImmutable(new File(context.getFilesDir(),"business-documents"),freshDirectory,"files/business-documents");
        BackupFilePins.pinImmutable(new File(context.getFilesDir(),"browser-media"),freshDirectory,"files/browser-media");
        stageName="PREFERENCES";JSONObject preferences=BackupPreferences.capture(context);
        write(new File(freshDirectory,"preferences.json"),preferences.toString());
        write(new File(freshDirectory,"bootstrap/initial-data.json"),initial);write(new File(freshDirectory,"bootstrap/service-catalog.json"),catalog);
        stageName="DATABASE";File database=new File(freshDirectory,"database.sqlite");JSONObject db=DatabaseBackupSnapshot.capture(store.getWritableDatabase(),database);
        stageName="BROWSER";JSONObject capturedBrowser=browserStorage;
        try(SQLiteDatabase snapshot=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            if(identity.optBoolean("requireDatabaseBrowserCheckpoint"))capturedBrowser=databaseBrowser(snapshot,identity);
            BrowserDraftRepository.validateCaptured(snapshot,identity,capturedBrowser);
        }
        write(new File(freshDirectory,"browser-storage.json"),capturedBrowser.toString());
        stageName="FILES";BackupFilePins.capture(context.getFilesDir(),freshDirectory,"files");
        for(String name:new String[]{"photo-capture","invoice-share"})BackupFilePins.capture(new File(context.getCacheDir(),name),freshDirectory,"cache/"+name);
        // Keystore/recovery capsules and our own staging trees are deliberately not exported.
        // Existing deletion/conflict backups remain immutable evidence, inside this encryption.
        for(String name:new String[]{"deletion-backups","legacy-conflict-backups"})BackupFilePins.capture(new File(context.getNoBackupFilesDir(),name),freshDirectory,"no_backup/"+name);
        // A legacy preference-backed snapshot or an editor setting may change independently
        // of SQLite. Fail/retry instead of certifying a mixed preference capture.
        if(!PixelMcpWriteTools.hash(preferences).equals(PixelMcpWriteTools.hash(BackupPreferences.capture(context))))throw new IOException("BACKUP_PREFERENCES_CHANGED_RETRY");
        stageName="INVENTORY";JSONObject inventory,state,revier,numberState,financial,financialOriginals,browserMedia;
        try(SQLiteDatabase snapshot=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            stageName="INVENTORY_STATE";
            state=BackupSnapshotState.read(snapshot,BackupPreferences.values(preferences,"haus_garten"),initial,catalog);
            state.put("extended_business_fingerprint",BusinessStateFingerprint.read(snapshot));
            stageName="INVENTORY_BUSINESS";
            inventory=LocalMigrationInventory.inspect(state,new LocalOriginalVerifier(new File(freshDirectory,"files"),id->BackupSnapshotState.photo(snapshot,id),id->new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot,"local_invoice_job:"+id,"{}"))));
            stageName="INVENTORY_RELATIONS";
            revier=new RevierApplicationService(new RevierSnapshotRepository(snapshot,SnapshotProjection.effective(state),new File(freshDirectory,"files"))).validateRelationships();
            stageName="INVENTORY_FINANCIAL";
            financial=LocalFinancialSnapshotValidation.inspect(snapshot,SnapshotProjection.effective(state));
            stageName="INVENTORY_ORIGINALS";
            financialOriginals=LocalFinancialOriginals.inspect(new File(freshDirectory,"files"),new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot,"local_invoice_external_evidence","{}")));
            stageName="INVENTORY_BROWSER_MEDIA";
            browserMedia=BrowserMediaRepository.inspectCaptured(snapshot,new File(freshDirectory,"files"));
            stageName="INVENTORY_NUMBERS";
            numberState=numberState(snapshot,state);
        }
        stageName="INVENTORY_METADATA";
        if(inventory.getInt("missing_local_originals")!=0)throw new IOException("BACKUP_LOCAL_ORIGINALS_INCOMPLETE");
        JSONObject metadata=new JSONObject(identity.toString()).put("schemaVersion",db.getInt("schemaVersion")).put("applicationId",context.getPackageName()).put("appVersion",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
            .put("captureStartedAt",started).put("capturedAt",java.time.Instant.now().toString()).put("counts",inventory.getJSONObject("counts")).put("database",db).put("inventory",inventory).put("revier_inventory",revier).put("financial_inventory",financial).put("financial_originals",financialOriginals).put("business_snapshot_sha256",LocalAuthorityMigration.businessHash(state)).put("business_snapshot_hash_version",1)
            .put("browser_checkpoint_source",identity.optBoolean("requireDatabaseBrowserCheckpoint")?"captured_database":"legacy_explicit_argument").put("browser_checkpoint_captured_at",capturedBrowser.optString("checkpointCapturedAt")).put("browser_checkpoint_sha256",PixelMcpWriteTools.hash(capturedBrowser))
            .put("numberState",numberState)
            .put("browser_media_format_version",1).put("browser_media_inventory",browserMedia).put("media_coverage","VERIFIED_NATIVE_MIRROR")
            .put("lastIssuedDocumentIds",issued(SnapshotProjection.effective(state))).put("captureScope","database_all_tables_private_files_preferences_browser_storage_and_legacy_backups")
            .put("structured_preference_credentials_excluded",true).put("legacy_backup_evidence_activation","FORBIDDEN").put("external_account_activation","REQUIRES_DEVICE_REAUTHORIZATION").put("local_authority_activated",false).put("invoice_release_after_restore","BLOCKED_UNTIL_CURRENT_EXTERNAL_RECONCILIATION")
            .put("device_specific_exclusions",new JSONArray().put("Android_Keystore").put("backup_key_protection").put("backup_device_guard").put("local_authority_guard").put("full_restore_staging").put("full_backup_saf_staging").put("scoped_restore_capsules").put("account_tokens").put("running_backup_staging"));
        return new Captured(freshDirectory,metadata);
        }catch(Exception failure){throw BackupScheduleRules.captureFailure(stageName,failure);}
    }
    static JSONObject inspectStaged(File directory,JSONObject manifest)throws Exception {
        if(manifest.has("business_snapshot_sha256")&&manifest.optInt("business_snapshot_hash_version",1)!=1)throw new IOException("BACKUP_BUSINESS_HASH_VERSION_UNSUPPORTED");
        File database=new File(directory,"database.sqlite");JSONObject db=DatabaseBackupSnapshot.inspect(database);
        if(db.getInt("schemaVersion")!=manifest.getInt("schemaVersion")||!db.getString("sha256").equals(manifest.getJSONObject("database").getString("sha256")))throw new IOException("BACKUP_DATABASE_MANIFEST_MISMATCH");
        JSONObject preferences=new JSONObject(text(new File(directory,"preferences.json"))),browser=new JSONObject(text(new File(directory,"browser-storage.json")));
        for(String name:BackupPreferences.GROUPS)BackupPreferences.values(preferences,name);
        if(browser.optInt("formatVersion")!=1||browser.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_INVALID");
        try(SQLiteDatabase snapshot=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            BrowserDraftRepository.validateCaptured(snapshot,manifest,browser);
            if(manifest.has("browser_media_inventory")){if(manifest.optInt("browser_media_format_version")!=1)throw new IOException("BACKUP_BROWSER_MEDIA_VERSION_UNSUPPORTED");JSONObject media=BrowserMediaRepository.inspectCaptured(snapshot,new File(directory,"files"));if(!PixelMcpWriteTools.hash(media).equals(PixelMcpWriteTools.hash(manifest.getJSONObject("browser_media_inventory"))))throw new IOException("BACKUP_BROWSER_MEDIA_INVENTORY_MISMATCH");}
            else if(BrowserMediaRepository.hasCapturedNamespace(snapshot))throw new IOException("BACKUP_BROWSER_MEDIA_MANIFEST_REQUIRED");
            if(manifest.optBoolean("requireDatabaseBrowserCheckpoint")&&!PixelMcpWriteTools.hash(databaseBrowser(snapshot,manifest)).equals(PixelMcpWriteTools.hash(browser)))throw new IOException("BACKUP_BROWSER_DATABASE_MISMATCH");
            if(manifest.has("browser_checkpoint_sha256")&&!manifest.getString("browser_checkpoint_sha256").equals(PixelMcpWriteTools.hash(browser)))throw new IOException("BACKUP_BROWSER_MANIFEST_MISMATCH");
            JSONObject state=BackupSnapshotState.read(snapshot,BackupPreferences.values(preferences,"haus_garten"),text(new File(directory,"bootstrap/initial-data.json")),text(new File(directory,"bootstrap/service-catalog.json")));
            state.put("extended_business_fingerprint",BusinessStateFingerprint.read(snapshot));
            if(manifest.has("business_snapshot_sha256")&&!manifest.getString("business_snapshot_sha256").equals(LocalAuthorityMigration.businessHash(state)))throw new IOException("BACKUP_BUSINESS_SNAPSHOT_MISMATCH");
            JSONObject expectedNumbers=manifest.getJSONObject("numberState"),numbers=numberState(snapshot,state);if(expectedNumbers.optBoolean("external_current_number_proof"))throw new IOException("BACKUP_NUMBER_PROOF_SCOPE_INVALID");for(String field:new String[]{"business_settings","local_invoice_counter","reservation_count","counter_source"})if(expectedNumbers.has(field)&&!PixelMcpWriteTools.hash(expectedNumbers.get(field)).equals(PixelMcpWriteTools.hash(numbers.opt(field))))throw new IOException("BACKUP_NUMBER_STATE_MISMATCH");
            JSONObject inventory=LocalMigrationInventory.inspect(state,new LocalOriginalVerifier(new File(directory,"files"),id->BackupSnapshotState.photo(snapshot,id),id->new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot,"local_invoice_job:"+id,"{}"))));
            JSONObject revier=new RevierApplicationService(new RevierSnapshotRepository(snapshot,SnapshotProjection.effective(state),new File(directory,"files"))).validateRelationships();if(manifest.has("revier_inventory"))for(String field:new String[]{"counts","id_set_hashes"})if(!PixelMcpWriteTools.hash(revier.get(field)).equals(PixelMcpWriteTools.hash(manifest.getJSONObject("revier_inventory").get(field))))throw new IOException("BACKUP_REVIER_INVENTORY_MISMATCH");
            JSONObject financial=LocalFinancialSnapshotValidation.inspect(snapshot,SnapshotProjection.effective(state));if(manifest.has("financial_inventory")&&!PixelMcpWriteTools.hash(financial.getJSONObject("counts")).equals(PixelMcpWriteTools.hash(manifest.getJSONObject("financial_inventory").getJSONObject("counts"))))throw new IOException("BACKUP_FINANCIAL_INVENTORY_MISMATCH");
            JSONObject financialOriginals=LocalFinancialOriginals.inspect(new File(directory,"files"),new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot,"local_invoice_external_evidence","{}")));if(manifest.has("financial_originals")){JSONObject expected=manifest.getJSONObject("financial_originals");for(java.util.Iterator<String> fields=expected.keys();fields.hasNext();){String field=fields.next();if(!financialOriginals.has(field)||!PixelMcpWriteTools.hash(financialOriginals.get(field)).equals(PixelMcpWriteTools.hash(expected.get(field))))throw new IOException("BACKUP_FINANCIAL_ORIGINALS_MISMATCH");}}
            JSONObject original=manifest.getJSONObject("inventory");
            for(String field:new String[]{"counts","id_set_sha256","collection_sha256","pending_sha256","historical_hours_decimal","historical_invoice_amounts_decimal"})if(!PixelMcpWriteTools.hash(inventory.get(field)).equals(PixelMcpWriteTools.hash(original.get(field))))throw new IOException("BACKUP_BUSINESS_INVENTORY_MISMATCH");
            if(original.has("pending_document_jobs")&&original.getInt("pending_document_jobs")!=inventory.getInt("pending_document_jobs"))throw new IOException("BACKUP_DOCUMENT_JOBS_MISMATCH");
            if(inventory.getInt("missing_local_originals")!=0)throw new IOException("BACKUP_LOCAL_ORIGINALS_INCOMPLETE");return inventory;
        }
    }
    /** Evidence for an explicitly invoked table transfer; never called by startup or restore.
     * Verifies the actual encrypted archive on every step and compares captured/live business
     * state, pending actions, relationships and originals. The staging directory must be fresh.
     */
    static BusinessTableStore.Evidence tableEvidence(PixelLocalStore store,File archive,String recovery,File staging){
        return source->{
            String digest=EncryptedBackupArchive.sha256(archive);
            try(InputStream input=new BufferedInputStream(new FileInputStream(archive));
                EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(input,recovery,staging,staging.getParentFile().getUsableSpace(),PixelLocalStore.SCHEMA_VERSION)){
                JSONObject manifest=verified.manifest,identity=new BackupDeviceGuard(store.appContext()).current();
                for(String key:new String[]{"installationId","restoreEpoch"})if(!identity.getString(key).equals(manifest.getString(key)))throw new IOException("TABLE_BACKUP_DEVICE_CONTEXT_MISMATCH");
                if(!store.appContext().getPackageName().equals(manifest.getString("applicationId")))throw new IOException("TABLE_BACKUP_APPLICATION_MISMATCH");
                File directory=new File(verified.directory,"data");JSONObject capturedInventory=inspectStaged(directory,manifest);
                try(SQLiteDatabase captured=SQLiteDatabase.openDatabase(new File(directory,"database.sqlite").getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
                    JSONObject prefs=BackupPreferences.values(new JSONObject(text(new File(directory,"preferences.json"))),"haus_garten");
                    JSONObject capturedRaw=PixelLocalStore.businessTables(captured).snapshot(prefs.optString("drive_snapshot",text(new File(directory,"bootstrap/initial-data.json"))));
                    if(!PixelMcpWriteTools.hash(source).equals(PixelMcpWriteTools.hash(capturedRaw)))throw new IOException("TABLE_BACKUP_SOURCE_MISMATCH");
                }
                JSONObject live=store.getState().put("extended_business_fingerprint",BusinessStateFingerprint.read(store.getReadableDatabase()));
                if(!LocalAuthorityMigration.businessHash(live).equals(manifest.getString("business_snapshot_sha256")))throw new IOException("TABLE_BACKUP_BUSINESS_STATE_CHANGED");
                JSONObject inventory=LocalMigrationInventory.inspect(live,new LocalOriginalVerifier(store.appContext().getFilesDir(),store::photoAttachment,id->new JSONObject(store.ownerValue("local_invoice_job:"+id,"{}"))));
                if(!inventory.optBoolean("projection_applied")||inventory.getInt("duplicate_ids")!=0||inventory.getInt("orphan_relations")!=0||inventory.getInt("missing_local_originals")!=0)throw new IOException("TABLE_SOURCE_INTEGRITY_REVIEW_REQUIRED");
                for(String key:new String[]{"counts","id_set_sha256","collection_sha256","pending_sha256","historical_hours_decimal","historical_invoice_amounts_decimal"})if(!PixelMcpWriteTools.hash(inventory.get(key)).equals(PixelMcpWriteTools.hash(capturedInventory.get(key))))throw new IOException("TABLE_BACKUP_INVENTORY_MISMATCH");
                new RevierApplicationService(new RevierSnapshotRepository(store.getReadableDatabase(),SnapshotProjection.effective(live),store.appContext().getFilesDir())).validateRelationships();
                LocalFinancialSnapshotValidation.inspect(store.getReadableDatabase(),SnapshotProjection.effective(live));
                if(!digest.equals(EncryptedBackupArchive.sha256(archive)))throw new IOException("TABLE_BACKUP_CHANGED");
                return digest;
            }
        };
    }
    private static JSONObject numberState(SQLiteDatabase snapshot,JSONObject state)throws Exception {
        String counter=DatabaseBackupSnapshot.ownerValue(snapshot,"local_invoice_counter","");long reservations;try(android.database.Cursor rows=snapshot.rawQuery("SELECT count(*) FROM owner_state WHERE key LIKE 'local_number_reservation:%'",null)){if(!rows.moveToFirst())throw new IOException("BACKUP_NUMBER_STATE_INVALID");reservations=rows.getLong(0);}
        return new JSONObject().put("business_settings",SnapshotProjection.effective(state).optJSONObject("business_settings")).put("local_invoice_counter",counter.isEmpty()?JSONObject.NULL:new JSONObject(counter)).put("reservation_count",reservations).put("counter_source","captured_database").put("external_current_number_proof",false);
    }
    private static JSONObject databaseBrowser(SQLiteDatabase snapshot,JSONObject identity)throws Exception {
        String raw=DatabaseBackupSnapshot.ownerValue(snapshot,"full_backup_browser","");if(raw.isEmpty())throw new IOException("BACKUP_BROWSER_STATE_REQUIRED_OPEN_APP");
        JSONObject status=new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot,"full_backup_browser_status","{}"));if(status.optBoolean("capturePending")||!status.optString("code").isEmpty())throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");
        JSONObject saved=new JSONObject(raw);if(saved.optInt("formatVersion")!=1||!saved.optString("installationId").equals(identity.optString("installationId"))||!saved.optString("restoreEpoch").equals(identity.optString("restoreEpoch")))throw new IOException("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED");
        JSONObject browser=saved.optJSONObject("browser");if(browser==null||browser.optInt("formatVersion")!=1||browser.optJSONObject("storage")==null||!PixelMcpWriteTools.hash(browser).equals(saved.optString("contentSha256")))throw new IOException("BACKUP_BROWSER_STATE_INVALID");
        java.time.Instant captured=java.time.Instant.parse(saved.getString("capturedAt"));if(captured.isAfter(java.time.Instant.now().plusSeconds(120)))throw new IOException("BACKUP_BROWSER_CLOCK_REVIEW_REQUIRED");
        return new JSONObject(browser.toString()).put("checkpointCapturedAt",captured.toString());
    }
    private static JSONArray issued(JSONObject effective)throws Exception {
        TreeSet<String> ids=new TreeSet<>();JSONArray invoices=effective.getJSONArray("invoices");for(int i=0;i<invoices.length();i++){JSONObject row=invoices.getJSONObject(i);if(IssuedInvoiceRules.isIssued(row))ids.add(row.getString("id"));}return new JSONArray(ids);
    }
    private static String asset(Context context,String name)throws Exception {try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String text(File file)throws Exception {return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private static void write(File file,String value)throws Exception {File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_STAGING_UNAVAILABLE");try(FileOutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    private LocalBackupDataset(){}
}
