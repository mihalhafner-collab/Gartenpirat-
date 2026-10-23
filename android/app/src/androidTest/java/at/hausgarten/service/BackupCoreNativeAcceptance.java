package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
            File target=new File(f.context.getNoBackupFilesDir(),"qa_snapshot/database.sqlite");JSONObject manifest=DatabaseBackupSnapshot.capture(f.store.getWritableDatabase(),target);test.yes(manifest.getInt("schemaVersion")==4);
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
    private static String read(File file)throws Exception {return new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);}
    private BackupCoreNativeAcceptance(){}
}
