package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Real platform Keystore/JCE and SQLite images, always in disposable QA namespaces. */
final class BackupCoreNativeAcceptance {
    private static void securityRejected(PixelMcpInstrumentation.Task command)throws Exception {
        try{command.run();}catch(GeneralSecurityException expected){return;}throw new AssertionError("Expected recovery protection refusal");
    }
    private static String alias(BackupRecoveryKeyStore keys)throws Exception {
        java.lang.reflect.Field field=BackupRecoveryKeyStore.class.getDeclaredField("alias");field.setAccessible(true);return (String)field.get(keys);
    }
    static void deleteQaKey(OwnerNativeAcceptance.Fixture fixture,BackupRecoveryKeyStore keys)throws Exception {
        if(!fixture.context.getFilesDir().getName().contains(fixture.context.prefix))throw new AssertionError("QA key scope required");
        KeyStore platform=KeyStore.getInstance("AndroidKeyStore");platform.load(null);platform.deleteEntry(alias(keys));
    }
    static void run(PixelMcpInstrumentation test)throws Exception {
        schemaFence(test);
        nativeEditorArchive(test);
        test.probe("backup native recovery confirmation replay and independent capsule instances preserve one key",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);String code=BackupCrypto.newRecoveryCode(),other=BackupCrypto.newRecoveryCode();
            try{
                test.yes(!keys.configured());securityRejected(()->keys.confirmAndStore("qa_backup_setup_1",code,other));test.yes(!keys.configured());
                keys.confirmAndStore("qa_backup_setup_1",code,code);test.yes(keys.configured()&&keys.read().equals(code));
                BackupRecoveryKeyStore reopened=new BackupRecoveryKeyStore(f.context);reopened.confirmAndStore("qa_backup_setup_1",code,code);test.yes(reopened.read().equals(code));
                securityRejected(()->reopened.confirmAndStore("qa_backup_setup_1",other,other));securityRejected(()->reopened.confirmAndStore("qa_backup_setup_2",other,other));test.yes(reopened.read().equals(code));
            }finally{deleteQaKey(f,keys);}
        }});
        test.probe("backup native stream decrypts with exported code after deleting only its QA Keystore alias",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);String code=BackupCrypto.newRecoveryCode();
            try{
                keys.confirmAndStore("qa_backup_setup_3",code,code);File capsule=new File(f.context.getNoBackupFilesDir(),"backup-key-protection/recovery.v1");String original=EncryptedBackupArchive.sha256(capsule);
                byte[] synthetic=new byte[128*1024];new Random(23).nextBytes(synthetic);File encrypted=new File(f.context.getCacheDir(),"qa_encrypted_stream.hgb");
                try(OutputStream out=BackupCrypto.encrypt(new FileOutputStream(encrypted),keys.read())){out.write(synthetic);}
                deleteQaKey(f,keys);securityRejected(keys::read);securityRejected(()->keys.confirmAndStore("qa_backup_setup_4",code,code));test.yes(EncryptedBackupArchive.sha256(capsule).equals(original));
                ByteArrayOutputStream plain=new ByteArrayOutputStream();try(InputStream input=BackupCrypto.decrypt(new FileInputStream(encrypted),code)){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)plain.write(b,0,n);}
                test.yes(Arrays.equals(synthetic,plain.toByteArray()));
            }finally{deleteQaKey(f,keys);}
        }});
        test.probe("backup native consistent SQLite image retains IDs pending session large Unicode settings and upload BLOB",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            JSONObject synthetic=f.store.getState().getJSONObject("master");synthetic.getJSONArray("customers").getJSONObject(0).put("name","[TEST] QA Snapshotkunde").put("test_record",true);f.store.commitSnapshot(synthetic,Collections.emptySet());
            f.store.saveWorklog(SyntheticBusinessData.object().put("id","qa_snapshot_work").put("customer_id","qa_c").put("activity","QA snapshot work").put("work_date","2027-05-18").put("hours",2.5).put("test_record",true));
            JSONObject session=f.store.startWorkSession(SyntheticBusinessData.object().put("request_id","qa_snapshot_timer").put("customer_id","qa_c").put("activity","QA active snapshot"));
            String large=PixelMcpInstrumentation.repeat("QA Ä☘ ",500000);f.store.putOwnerValue("qa_large_setting",large);f.store.putOwnerValue("qa_number_state","{\"prefix\":\"QA-\",\"next\":12}");
            byte[] blob=new byte[3*1024*1024];new Random(31).nextBytes(blob);new LocalOwnerRepository(f.context,f.store).putFile("qa_snapshot_upload",new JSONObject().put("kind","upload").put("test_record",true),blob);
            String before=PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master")),pending=PixelMcpWriteTools.hash(f.store.pendingActions());
            File target=new File(f.context.getNoBackupFilesDir(),"qa_snapshot/database.sqlite");JSONObject manifest=DatabaseBackupSnapshot.capture(f.store.getWritableDatabase(),target);test.yes(manifest.getInt("schemaVersion")==6);
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(target.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
                test.yes(DatabaseBackupSnapshot.ownerValue(db,"qa_large_setting","").equals(large));test.yes(DatabaseBackupSnapshot.ownerValue(db,"qa_number_state","").contains("QA-"));
                try(Cursor row=db.rawQuery("SELECT id,state FROM work_sessions WHERE id=?",new String[]{session.getString("id")})){test.yes(row.moveToFirst()&&row.getString(1).equals(WorkSessionStore.RUNNING));}
                MessageDigest digest=MessageDigest.getInstance("SHA-256");for(int offset=0;offset<blob.length;offset+=32768)try(Cursor row=db.rawQuery("SELECT substr(data,?,32768) FROM mcp_uploads WHERE id='qa_snapshot_upload'",new String[]{Integer.toString(offset+1)})){test.yes(row.moveToFirst());digest.update(row.getBlob(0));}test.yes(Arrays.equals(digest.digest(),MessageDigest.getInstance("SHA-256").digest(blob)));
            }
            test.yes(before.equals(PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master")))&&pending.equals(PixelMcpWriteTools.hash(f.store.pendingActions())));
            test.yes(f.store.workSessions().current().getString("id").equals(session.getString("id")));
            try{DatabaseBackupSnapshot.capture(f.store.getWritableDatabase(),target);throw new AssertionError("Must not overwrite snapshot");}catch(IOException expected){test.yes(manifest.getString("sha256").equals(EncryptedBackupArchive.sha256(target)));}
        }});
        test.probe("backup native captured dataset encrypted roundtrip retains journal receipts photos PDF typed settings drafts and personal paid",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            JSONObject synthetic=f.store.getState().getJSONObject("master");synthetic.getJSONArray("customers").getJSONObject(0).put("name","[TEST] QA Sicherungskunde").put("test_record",true);
            String pdf="%PDF-1.4\nQA synthetic backup document\n%%EOF\n";
            synthetic.getJSONArray("invoices").put(SyntheticBusinessData.object().put("id","qa_backup_invoice").put("customer_id","qa_c").put("issued_at","2027-05-18T11:00:00Z").put("amount","148.00").put("test_record",true).put("pdf_base64",android.util.Base64.encodeToString(pdf.getBytes(java.nio.charset.StandardCharsets.US_ASCII),android.util.Base64.NO_WRAP)));
            f.store.commitSnapshot(synthetic,Collections.emptySet());
            f.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c").put("role","before").put("_photo_request_id","qa_backup_photo").put("data",PixelMcpInstrumentation.VALID_GIF));
            f.store.execute("start_work",SyntheticBusinessData.object().put("request_id","qa_backup_active_session").put("customer_id","qa_c").put("activity","QA active work"));
            test.yes(new LocalOwnerRepository(f.context,f.store).migratePersonalPaid("[\"qa_w\"]").equals("OK"));f.store.putOwnerValue("qa_document_job","{\"state\":\"PENDING\",\"id\":\"qa_job\"}");
            f.context.getSharedPreferences("haus_garten",0).edit().putInt("qa_int",7).putLong("qa_long",7L).putFloat("qa_float",Float.NaN).putStringSet("qa_set",new TreeSet<>(Arrays.asList("QA Ä","QA ☘"))).commit();
            f.context.getSharedPreferences("planning_private",0).edit().putString("weather_secret","qa_excluded_ciphertext").putString("weather_iv","qa_excluded_iv").commit();
            File pending=new File(f.context.getFilesDir(),"pending-work-photo.json");try(FileOutputStream out=new FileOutputStream(pending)){out.write("{\"id\":\"qa_draft_photo\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            JSONObject browser=SyntheticBusinessData.object().put("formatVersion",1).put("storage",SyntheticBusinessData.object().put("qa_draft","QA unsaved draft"));
            String before=PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master")),pendingHash=PixelMcpWriteTools.hash(f.store.pendingActions());
            File stage=new File(f.context.getNoBackupFilesDir(),"qa_complete_capture");
            LocalBackupDataset.Captured capture=LocalBackupDataset.capture(f.context,f.store,stage,new JSONObject().put("backupId","qa_backup_roundtrip").put("installationId","qa_installation").put("businessId","qa_business"),browser);
            JSONObject live=LocalMigrationInventory.inspect(f.store.getState(),new LocalOriginalVerifier(f.context.getFilesDir(),f.store::photoAttachment));
            test.yes(capture.metadata.getJSONObject("inventory").getString("pending_sha256").equals(live.getString("pending_sha256")));
            test.yes(PixelMcpWriteTools.hash(capture.metadata.getJSONObject("inventory").getJSONObject("collection_sha256")).equals(PixelMcpWriteTools.hash(live.getJSONObject("collection_sha256"))));
            test.yes(capture.metadata.getJSONObject("database").getJSONObject("tableCounts").getInt("pending")>capture.metadata.getJSONObject("inventory").getInt("pending_actions"));
            String code=BackupCrypto.newRecoveryCode();File encrypted=new File(f.context.getCacheDir(),"qa_dataset.hgb");
            EncryptedBackupArchive.write(new FileOutputStream(encrypted),code,capture.metadata,capture.sources());
            try(EncryptedBackupArchive.Verified restored=EncryptedBackupArchive.verifyAndStage(new FileInputStream(encrypted),code,new File(f.context.getNoBackupFilesDir(),"qa_complete_restored"),128L*1024*1024,PixelLocalStore.SCHEMA_VERSION)){
                JSONObject inventory=LocalBackupDataset.inspectStaged(new File(restored.directory,"data"),restored.manifest);test.yes(inventory.getInt("missing_local_originals")==0&&inventory.getInt("verified_local_originals")==2);
                test.yes(inventory.getString("historical_invoice_amounts_decimal").equals("148.00")&&inventory.getString("session_state").equals(WorkSessionStore.RUNNING));
                JSONObject preferences=new JSONObject(read(restored.file("preferences.json")));JSONObject values=BackupPreferences.values(preferences,"haus_garten"),types=preferences.getJSONObject("groups").getJSONObject("haus_garten").getJSONObject("types");
                test.yes(types.getString("qa_long").equals("long")&&types.getString("qa_int").equals("int")&&types.getString("qa_float").equals("float")&&Float.isNaN(Float.valueOf(values.getString("qa_float"))));
                test.yes(values.getJSONArray("qa_set").length()==2&&!BackupPreferences.values(preferences,"planning_private").has("weather_secret")&&!BackupPreferences.values(preferences,"planning_private").has("weather_iv"));
                test.yes(new JSONObject(read(restored.file("browser-storage.json"))).getJSONObject("storage").getString("qa_draft").equals("QA unsaved draft"));test.yes(read(restored.file("files/pending-work-photo.json")).contains("qa_draft_photo"));
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(restored.file("database.sqlite").getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){test.yes(DatabaseBackupSnapshot.ownerValue(db,"personal_paid","").equals("[\"qa_w\"]")&&DatabaseBackupSnapshot.ownerValue(db,"personal_paid_migrated","").equals("1")&&DatabaseBackupSnapshot.ownerValue(db,"qa_document_job","").contains("PENDING"));}
                test.yes(!restored.manifest.getBoolean("local_authority_activated")&&restored.manifest.getString("invoice_release_after_restore").equals("BLOCKED_UNTIL_CURRENT_EXTERNAL_RECONCILIATION"));
            }
            test.yes(before.equals(PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master")))&&pendingHash.equals(PixelMcpWriteTools.hash(f.store.pendingActions())));
        }});
    }
    /** Historical 4/5 schema, created without opening the current helper first. */
    private static final class HistoricalStore extends SQLiteOpenHelper {
        HistoricalStore(android.content.Context context,int version){super(context,"haus-garten-mobile.db",null,version);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)");
            WorkSessionStore.createSchema(db);
            db.execSQL("CREATE TABLE photo_attachments(photo_id TEXT PRIMARY KEY,content_sha256 TEXT NOT NULL,mime_type TEXT NOT NULL,byte_size INTEGER NOT NULL,storage_name TEXT NOT NULL,role TEXT NOT NULL DEFAULT 'documentation',transfer_state TEXT NOT NULL DEFAULT 'local',created_at TEXT NOT NULL)");
            db.execSQL("CREATE INDEX ix_photo_attachments_hash ON photo_attachments(content_sha256)");
            db.execSQL("CREATE TABLE owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            db.execSQL("CREATE TABLE mcp_uploads(id TEXT PRIMARY KEY,metadata TEXT NOT NULL,data BLOB NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new AssertionError("Historical fixture must not upgrade itself");}
    }
    private static void owner(SQLiteDatabase database,String key,String value){ContentValues row=new ContentValues();row.put("key",key);row.put("value",value);database.insertOrThrow("owner_state",null,row);}
    /** Fingerprint exact rows, value types and schema; user_version is asserted separately. */
    private static String rows(SQLiteDatabase database)throws Exception {
        JSONObject state=new JSONObject(),schema=new JSONObject();
        try(Cursor definitions=database.rawQuery("SELECT name,sql FROM sqlite_master WHERE type IN ('table','index') AND name NOT LIKE 'sqlite_%' ORDER BY name",null)){
            while(definitions.moveToNext())schema.put(definitions.getString(0),definitions.isNull(1)?JSONObject.NULL:definitions.getString(1));
        }
        for(String table:new String[]{"pending","owner_state","work_sessions","work_session_events","photo_attachments","mcp_uploads"}){
            JSONArray values=new JSONArray();try(Cursor cursor=database.rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)){
                while(cursor.moveToNext()){JSONArray value=new JSONArray();for(int i=0;i<cursor.getColumnCount();i++)value.put(new JSONObject().put("type",cursor.getType(i)).put("value",cursor.isNull(i)?JSONObject.NULL:cursor.getType(i)==Cursor.FIELD_TYPE_BLOB?PhotoAttachmentStore.sha256(cursor.getBlob(i)):cursor.getString(i)));values.put(value);}
            }state.put(table,values);
        }return PixelMcpWriteTools.hash(state.put("schema",schema));
    }
    private static final WorkSessionStore.Clock FIXED_CLOCK=new WorkSessionStore.Clock(){public long wallMillis(){return java.time.Instant.parse("2027-05-18T06:00:00Z").toEpochMilli();}public long elapsedMillis(){return 100000L;}public String bootMarker(){return "qa_same_boot";}};
    private static void schemaFence(PixelMcpInstrumentation test)throws Exception {
        for(int historical:new int[]{4,5})test.probe("native schema "+historical+" to 6 preserves exact business rows and rejects older writer",()->{
            PixelMcpInstrumentation.Isolated context=new PixelMcpInstrumentation.Isolated(test.getTargetContext());PixelLocalStore current=null;
            try{
                JSONObject snapshot=SyntheticBusinessData.object("{customers:[{id:'qa_schema_customer',name:'[TEST] QA Schema',active:1}],orders:[{id:'qa_schema_order',customer_id:'qa_schema_customer',title:'QA original order'}],worklogs:[{id:'qa_schema_work',customer_id:'qa_schema_customer',order_id:'qa_schema_order',work_date:'2027-05-18',activity:'QA original work',hours:'2.50',billed:0}],invoices:[{id:'qa_schema_invoice',customer_id:'qa_schema_customer',status:'Entwurf',amount:'148.00'}],photos:[],offers:[],appointments:[],business_settings:{}}");
                test.yes(context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",snapshot.toString()).putString("qa_original_setting","QA Ä☘ preserved").putLong("qa_original_counter",98L).commit());
                String originalRows,session;
                try(HistoricalStore old=new HistoricalStore(context,historical)){
                    SQLiteDatabase db=old.getWritableDatabase();test.yes(db.getVersion()==historical);owner(db,"snapshot",snapshot.toString());owner(db,"personal_paid","[\"qa_schema_work\"]");owner(db,"qa_setting","{\"unit\":\"Sack\",\"amount\":\"9.00\"}");owner(db,"qa_open_action","{\"request_id\":\"qa_schema_pending_action\",\"state\":\"PENDING\"}");
                    ContentValues action=new ContentValues();action.put("id","qa_schema_pending");action.put("kind","worklog");action.put("payload",SyntheticBusinessData.object("{id:'qa_schema_pending_work',customer_id:'qa_schema_customer',work_date:'2027-05-18',activity:'QA queued work',hours:'1.25',billed:0}").toString());action.put("created_at","2027-05-18T08:00:00Z");db.insertOrThrow("pending",null,action);
                    session=new WorkSessionStore(db,FIXED_CLOCK).start(new JSONObject().put("request_id","qa_schema_timer_start").put("customer_id","qa_schema_customer").put("order_id","qa_schema_order").put("work_date","2027-05-18").put("activity","QA active preserved session")).getString("id");
                    ContentValues upload=new ContentValues();upload.put("id","qa_schema_upload");upload.put("metadata","{\"kind\":\"upload\",\"test_record\":true}");upload.put("data",new byte[]{0,7,(byte)255,23});db.insertOrThrow("mcp_uploads",null,upload);originalRows=rows(db);
                }
                current=new PixelLocalStore(context,FIXED_CLOCK);test.yes(current.getReadableDatabase().getVersion()==6&&rows(current.getReadableDatabase()).equals(originalRows));test.yes(current.snapshotText("").equals(snapshot.toString())&&current.pendingActions().length()==1);
                JSONObject effective=SnapshotProjection.effective(current.getState());test.yes(PixelMcpWriteTools.find(effective.getJSONArray("invoices"),"qa_schema_invoice").getString("amount").equals("148.00")&&PixelMcpWriteTools.find(effective.getJSONArray("worklogs"),"qa_schema_work").getString("hours").equals("2.50"));
                test.yes(current.workSessions().current().getString("id").equals(session)&&current.workSessions().current().getString("state").equals(WorkSessionStore.RUNNING)&&current.ownerValue("personal_paid","").equals("[\"qa_schema_work\"]"));
                test.yes(context.getSharedPreferences("haus_garten",0).getString("drive_snapshot","").equals(snapshot.toString())&&context.getSharedPreferences("haus_garten",0).getString("qa_original_setting","").equals("QA Ä☘ preserved")&&context.getSharedPreferences("haus_garten",0).getLong("qa_original_counter",0)==98L);current.close();
                try(HistoricalStore stale=new HistoricalStore(context,5)){
                    try{stale.getWritableDatabase().execSQL("INSERT INTO owner_state(key,value) VALUES('qa_stale_writer','must never commit')");throw new AssertionError("Older schema helper accepted a write");}catch(android.database.sqlite.SQLiteException expected){test.yes(expected.getMessage()!=null&&expected.getMessage().contains("Can't downgrade database from version 6 to 5"));}
                }
                try(SQLiteDatabase inspect=SQLiteDatabase.openDatabase(context.getDatabasePath("haus-garten-mobile.db").getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){test.yes(inspect.getVersion()==6&&rows(inspect).equals(originalRows)&&DatabaseBackupSnapshot.ownerValue(inspect,"qa_stale_writer",null)==null);}
            }finally{if(current==null)current=new PixelLocalStore(context,FIXED_CLOCK);McpDispatchAcceptance.cleanup(context,current);}
        });
    }
    private static JSONObject editorBrowser(BrowserDraftRepository repository)throws Exception {
        JSONObject list=repository.read(new JSONObject().put("formatVersion",1).put("action","list").toString()),storage=new JSONObject();JSONArray states=list.getJSONArray("states");
        for(int i=0;i<states.length();i++){JSONObject state=states.getJSONObject(i);if(state.getBoolean("present"))storage.put(state.getString("key"),state.getString("value"));}
        return new JSONObject().put("formatVersion",1).put("storage",storage).put("editor_state",new JSONObject().put("formatVersion",1).put("context",list.getJSONObject("context")).put("review_required",list.getBoolean("review_required")).put("record_hash",list.getString("record_hash")));
    }
    private static void editorMismatch(PixelMcpInstrumentation test,PixelMcpInstrumentation.Task operation)throws Exception {
        try{operation.run();}catch(IOException expected){test.yes(expected.getMessage().equals("BACKUP_BROWSER_EDITOR_STATE_MISMATCH"));return;}throw new AssertionError("Native editor/backup mismatch must refuse");
    }
    private static void nativeEditorArchive(PixelMcpInstrumentation test)throws Exception {
        test.probe("native editor drafts and real media mirror survive encrypted isolated stage while mismatched checkpoint cannot change live data",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext(),FIXED_CLOCK,false)){
            byte[] original=android.util.Base64.decode(PixelMcpInstrumentation.VALID_GIF.substring(PixelMcpInstrumentation.VALID_GIF.indexOf(',')+1),android.util.Base64.DEFAULT);BrowserMediaFiles mediaFiles=new BrowserMediaFiles(new File(f.context.getFilesDir(),"browser-media"),new File(f.context.getNoBackupFilesDir(),"qa_editor_uploads"));String upload=PixelMcpWriteTools.hash("qa_editor_original_upload");mediaFiles.begin(upload,original.length,"image/gif");mediaFiles.append(upload,0,original);JSONObject reference=mediaFiles.complete(upload);
            JSONObject graph=new JSONObject().put("photos",new JSONArray().put(new JSONObject().put("id","qa_editor_photo").put("customer_id","qa_c").put("file",reference).put("request_id","qa_editor_frozen_photo")));BrowserMediaRepository media=new BrowserMediaRepository(f.context,f.store);media.beginMirror("qa_editor_mirror_begin","active");media.writeMirrorRecord("qa_editor_mirror_record","active","customer-draft:qa_c",graph);media.completeMirror("qa_editor_mirror_complete","active",new JSONArray().put("customer-draft:qa_c"));test.yes(media.status().getBoolean("mirror_complete"));
            BrowserDraftRepository drafts=new BrowserDraftRepository(f.context,f.store);String key="hausgarten.form.sessions.v1",tombstone="hausgarten.entry.draft.v1";JSONObject draft=new JSONObject().put("formatVersion",1).put("entries",new JSONObject().put("qa_editor_session",new JSONObject().put("id","qa_editor_session").put("kind","order").put("values",new JSONObject().put("customer_id","qa_c").put("title","QA Ä☘ preserved draft")).put("pending",new JSONObject().put("request_id","qa_editor_frozen_action").put("method","saveOrder").put("args",new JSONObject().put("id","qa_editor_order").put("customer_id","qa_c").put("title","QA immutable pending order")))));
            String mirror=RestoredBrowserReviewRules.MIRROR,mirrorBytes="{\n  \"formatVersion\" : 1,\n  \"pages\" : {\"qa_page\":{\"forms\":[{\"fields\":[{\"id\":\"qa_note\",\"type\":\"textarea\",\"value\":\"QA original mirrored form\"}]}]}}\n}\n";
            JSONObject first=drafts.read(new JSONObject().put("formatVersion",1).put("action","get").put("key",key).toString()),deleted=drafts.read(new JSONObject().put("formatVersion",1).put("action","get").put("key",tombstone).toString()),mirrored=drafts.read(new JSONObject().put("formatVersion",1).put("action","get").put("key",mirror).toString());JSONObject write=new JSONObject().put("formatVersion",1).put("request_id","qa_editor_native_draft_write").put("context",first.getJSONObject("context")).put("mutations",new JSONArray().put(new JSONObject().put("key",key).put("expected_record_hash",first.getString("record_hash")).put("value",draft.toString())).put(new JSONObject().put("key",tombstone).put("expected_record_hash",deleted.getString("record_hash")).put("value",JSONObject.NULL)).put(new JSONObject().put("key",mirror).put("expected_record_hash",mirrored.getString("record_hash")).put("value",mirrorBytes)));test.yes(!drafts.write(write.toString()).getBoolean("replayed")&&drafts.write(write.toString()).getBoolean("replayed"));
            JSONObject unsanitized=editorBrowser(drafts);String exactSource=unsanitized.toString();JSONObject browser=MainActivity.sanitizeBackupCheckpoint(unsanitized);test.yes(unsanitized.toString().equals(exactSource)&&browser.getJSONObject("storage").getString(mirror).equals(mirrorBytes)&&PixelMcpWriteTools.hash(browser.getJSONObject("editor_state")).equals(PixelMcpWriteTools.hash(unsanitized.getJSONObject("editor_state"))));
            for(String variant:new String[]{"version","hash","context","review","extra"}){JSONObject invalid=new JSONObject(browser.toString()),descriptor=invalid.getJSONObject("editor_state");switch(variant){case "version":descriptor.put("formatVersion","1");break;case "hash":descriptor.put("record_hash","qa_invalid_digest");break;case "context":descriptor.getJSONObject("context").put("restoreEpoch","qa_invalid_epoch");break;case "review":descriptor.put("review_required","false");break;case "extra":descriptor.put("qa_unexpected","must refuse");break;default:throw new AssertionError("Unknown QA descriptor variant");}String before=invalid.toString();try{MainActivity.sanitizeBackupCheckpoint(invalid);throw new AssertionError("Malformed native editor descriptor accepted");}catch(IOException expected){test.yes(expected.getMessage().equals("BACKUP_BROWSER_CAPTURE_REQUIRED"));}test.yes(invalid.toString().equals(before));}
            JSONObject identity=new BackupDeviceGuard(f.context).current();identity.put("backupId","qa_native_editor_roundtrip").put("businessId","qa_native_editor_business").put("requireDatabaseBrowserCheckpoint",true);BackupSettingsRepository settings=new BackupSettingsRepository(f.context,f.store);settings.saveBrowser("qa_editor_checkpoint",settings.browserRevision(),browser);String liveRows=rows(f.store.getReadableDatabase()),browserRow=f.store.ownerValue("full_backup_browser","");
            File capturedDir=new File(f.context.getNoBackupFilesDir(),"qa_native_editor_capture");LocalBackupDataset.Captured captured=LocalBackupDataset.capture(f.context,f.store,capturedDir,identity,browser);test.yes(captured.metadata.getInt("schemaVersion")==6&&captured.metadata.getJSONObject("browser_media_inventory").getInt("blob_count")==1);
            String code=BackupCrypto.newRecoveryCode();File encrypted=new File(f.context.getCacheDir(),"qa_native_editor_archive.hgb");EncryptedBackupArchive.write(new FileOutputStream(encrypted),code,captured.metadata,captured.sources());
            try(EncryptedBackupArchive.Verified restored=EncryptedBackupArchive.verifyAndStage(new FileInputStream(encrypted),code,new File(f.context.getNoBackupFilesDir(),"qa_native_editor_restored"),128L*1024*1024,6)){
                LocalBackupDataset.inspectStaged(new File(restored.directory,"data"),restored.manifest);JSONObject stagedBrowser=new JSONObject(read(restored.file("browser-storage.json")));test.yes(stagedBrowser.getJSONObject("storage").getString(key).equals(draft.toString())&&stagedBrowser.getJSONObject("storage").getString(mirror).equals(mirrorBytes)&&!stagedBrowser.getJSONObject("storage").has(tombstone)&&PixelMcpWriteTools.hash(stagedBrowser.getJSONObject("editor_state")).equals(PixelMcpWriteTools.hash(browser.getJSONObject("editor_state"))));
                try(SQLiteDatabase snapshot=SQLiteDatabase.openDatabase(restored.file("database.sqlite").getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
                    test.yes(rows(snapshot).equals(liveRows));String raw=DatabaseBackupSnapshot.ownerValue(snapshot,BrowserDraftRepository.ROW+PixelMcpWriteTools.hash(key),"");test.yes(new JSONObject(raw).getString("value").equals(draft.toString())&&new JSONObject(raw).getString("last_request_id").equals("qa_editor_native_draft_write"));JSONObject gone=new JSONObject(DatabaseBackupSnapshot.ownerValue(snapshot,BrowserDraftRepository.ROW+PixelMcpWriteTools.hash(tombstone),""));test.yes(!gone.getBoolean("present")&&gone.isNull("value"));
                    BrowserDraftRepository.validateCaptured(snapshot,restored.manifest,stagedBrowser);
                    for(String variant:new String[]{"missing","digest","context","storage","tombstone"}){JSONObject broken=new JSONObject(stagedBrowser.toString());switch(variant){case "missing":broken.remove("editor_state");break;case "digest":broken.getJSONObject("editor_state").put("record_hash",PixelMcpWriteTools.hash("qa_wrong_digest"));break;case "context":broken.getJSONObject("editor_state").getJSONObject("context").put("restoreEpoch",java.util.UUID.randomUUID().toString());break;case "storage":broken.getJSONObject("storage").put(key,draft.toString()+" ");break;case "tombstone":broken.getJSONObject("storage").put(tombstone,"QA must remain discarded");break;default:throw new AssertionError("Unknown QA editor mismatch");}editorMismatch(test,()->BrowserDraftRepository.validateCaptured(snapshot,restored.manifest,broken));}
                }
                File stagedOriginal=restored.file("files/browser-media/"+reference.getString("sha256")+".blob");test.yes(stagedOriginal.length()==original.length&&EncryptedBackupArchive.sha256(stagedOriginal).equals(reference.getString("sha256"))&&Arrays.equals(java.nio.file.Files.readAllBytes(stagedOriginal.toPath()),original));
            }
            for(String variant:new String[]{"missing","digest"}){JSONObject invalid=new JSONObject(browser.toString());if(variant.equals("missing"))invalid.remove("editor_state");else invalid.getJSONObject("editor_state").put("record_hash",PixelMcpWriteTools.hash("qa_wrong_capture_digest"));JSONObject directIdentity=new JSONObject(identity.toString());directIdentity.remove("requireDatabaseBrowserCheckpoint");editorMismatch(test,()->settings.saveBrowser("qa_bad_editor_checkpoint_"+variant,settings.browserRevision(),invalid));editorMismatch(test,()->LocalBackupDataset.capture(f.context,f.store,new File(f.context.getNoBackupFilesDir(),"qa_bad_editor_capture_"+variant),directIdentity,invalid));test.yes(rows(f.store.getReadableDatabase()).equals(liveRows)&&f.store.ownerValue("full_backup_browser","").equals(browserRow));}
            test.yes(rows(f.store.getReadableDatabase()).equals(liveRows)&&media.readRecord("customer-draft:qa_c").getString("record_hash").equals(PixelMcpWriteTools.hash(graph))&&new JSONObject(drafts.read(new JSONObject().put("formatVersion",1).put("action","get").put("key",key).toString()).getString("value")).getJSONObject("entries").getJSONObject("qa_editor_session").getJSONObject("pending").getString("request_id").equals("qa_editor_frozen_action"));
        }});
    }
    private static String read(File file)throws Exception {return new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);}
    private BackupCoreNativeAcceptance(){}
}
