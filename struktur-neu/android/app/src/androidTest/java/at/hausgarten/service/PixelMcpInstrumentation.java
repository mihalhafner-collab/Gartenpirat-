package at.hausgarten.service;

import android.app.Activity;
import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

/** Current opt-in ADB routines use freshly created, synthetic storage only.
 * Native backup/provider/review suites reject real devices and existing fixture data.
 * A passing emulator suite is not physical Pixel or real Drive-account acceptance.
 */
public final class PixelMcpInstrumentation extends Instrumentation {
    private Bundle arguments;
    private int checks;
    private interface Check {void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    private void yes(boolean value)throws Exception {if(!value)throw new Exception("SYNTHETIC_ASSERTION_"+checks);checks++;}
    private void denied(String code,Check check)throws Exception {
        try{check.run();}catch(Exception error){if(String.valueOf(error.getMessage()).contains(code)){checks++;return;}throw error;}
        throw new Exception("EXPECTED_REJECTION_"+code);
    }
    private static String hash(Object value)throws Exception{return PixelMcpWriteTools.hash(value);}
    private static JSONObject fixture()throws Exception {
        JSONObject source=new JSONObject().put("schema_version",2).put("unknown_optional",new JSONObject().put("decimal","12.3400").put("unicode","Grün 🌿"));
        String[] kinds=BusinessTableStore.COLLECTIONS;int[] counts={300,40,5000,500,3000};
        for(int k=0;k<kinds.length;k++){JSONArray rows=new JSONArray();for(int i=0;i<counts[k];i++){
            JSONObject row=new JSONObject().put("id",kinds[k]+"_qa_"+i).put("name","Synthetisch "+i).put("unknown_field",JSONObject.NULL);
            if(k!=0)row.put("customer_id","customers_qa_"+(i%300));
            if(k==2)row.put("hours","1.25").put("order_id","orders_qa_"+(i%40));
            if(k==3)row.put("amount","123.45");rows.put(row);
        }source.put(kinds[k],rows);}
        // Larger than a typical CursorWindow and including supplementary Unicode characters.
        StringBuilder large=new StringBuilder();for(int i=0;i<600000;i++)large.append("🌿");source.getJSONArray("photos").getJSONObject(0).put("large_optional",large.toString());return source;
    }
    @Override public void onStart(){
        Bundle report=new Bundle();File directory=null;long started=System.nanoTime();
        try{
            String suite=arguments==null?"":arguments.getString("suite","");
            if(suite.equals("native_data")||suite.equals("native_compose")){
                JSONObject result=suite.equals("native_data")?NativeDataAccessNativeTests.run(this):NativeComposeAppTests.run(this);
                checks=result.getInt("checks");report.putString("result",result.put("suite",suite).toString());
                finish(Activity.RESULT_OK,report);return;
            }
            if(suite.equals("document_provider")||suite.equals("document_provider_reopen")){
                JSONObject result=BackupDocumentProviderNativeTests.run(this,suite.equals("document_provider_reopen"));
                checks=result.getInt("checks");report.putString("result",result.put("suite",suite).toString());
                finish(Activity.RESULT_OK,report);return;
            }
            if(suite.equals("browser_checkpoint")||suite.equals("browser_checkpoint_reopen")){
                JSONObject result=RestoredBrowserCheckpointNativeTests.run(this,suite.equals("browser_checkpoint_reopen"));
                checks=result.getInt("checks");report.putString("result",result.put("suite",suite).toString());
                finish(Activity.RESULT_OK,report);return;
            }
            if(suite.equals("local_backup")||suite.equals("local_backup_reopen")){
                checks=LocalBackupNativeTests.run(this,suite.equals("local_backup_reopen"));
                report.putString("result",new JSONObject().put("suite",suite).put("checks",checks).put("synthetic_only",true).put("productive_data_read",false).put("native_backup_roundtrip",true).put("android_file_picker_tested",false).toString());
                finish(Activity.RESULT_OK,report);return;
            }
            if(!suite.equals("business_tables"))throw new Exception("EXPLICIT_SYNTHETIC_SUITE_REQUIRED");
            directory=Files.createTempDirectory(getTargetContext().getCacheDir().toPath(),"gp-table-test-").toFile();
            runStorage(directory);runJournal(directory);
            report.putString("result",new JSONObject().put("suite",suite).put("checks",checks).put("elapsed_ms",(System.nanoTime()-started)/1000000).put("synthetic_only",true).put("productive_data_read",false).put("archive_restore_tested",false).toString());
            removeOwned(directory);directory=null;finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("failure",error.getClass().getSimpleName()+": "+error.getMessage());report.putString("trace",android.util.Log.getStackTraceString(error));report.putInt("checks",checks);try{if(directory!=null)removeOwned(directory);}catch(Exception cleanup){report.putString("cleanup","SYNTHETIC_CLEANUP_FAILED");}finish(Activity.RESULT_CANCELED,report);}
    }
    private void runStorage(File directory)throws Exception {
        File file=new File(directory,"synthetic.sqlite");JSONObject source=fixture();String original=source.toString(),sourceHash=hash(source),request="qa_table_transfer";
        // Test-only evidence; the production maintenance entry requires a verified encrypted archive.
        BusinessTableStore.Evidence proof=value->{if(!hash(value).equals(sourceHash))throw new Exception("QA_BACKUP_SOURCE_MISMATCH");return hash("synthetic archive");};
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null)){
            db.setVersion(PixelLocalStore.SCHEMA_VERSION);db.execSQL("CREATE TABLE owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");db.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,payload TEXT NOT NULL)");
            db.execSQL("INSERT INTO owner_state VALUES('snapshot',?)",new Object[]{original});BusinessTableStore tables=PixelLocalStore.businessTables(db);
            yes(tables.status().getString("phase").equals("SNAPSHOT"));
            denied("TABLE_SOURCE_CHANGED",()->tables.prepare(request,hash("wrong"),proof));
            tables.prepare(request,sourceHash,proof);yes(!tables.active());
            denied("TABLE_TRANSFER_IN_PROGRESS",()->db.execSQL("INSERT INTO pending VALUES('qa','{}')"));
            denied("TABLE_TRANSFER_IN_PROGRESS",()->db.execSQL("UPDATE owner_state SET value='{}' WHERE key='snapshot'"));
            String checkpoint=hash(tables.status());db.beginTransaction();try{tables.copyBatch(request,33,proof);}finally{db.endTransaction();}
            yes(checkpoint.equals(hash(tables.status()))); // An interrupted outer transaction rolls rows and progress back together.
            tables.copyBatch(request,73,proof);yes(tables.status().getInt("offset")==73);
            tables.cancel(request);yes(tables.status().getString("phase").equals("ABORTED"));
            db.execSQL("INSERT INTO pending VALUES('qa','{}')");db.execSQL("DELETE FROM pending WHERE id='qa'");
            tables.prepare(request,sourceHash,proof);
        }
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null)){
            BusinessTableStore tables=PixelLocalStore.businessTables(db);yes(tables.status().getInt("offset")==73);yes(!tables.active());
            while(tables.status().getString("phase").equals("COPYING"))tables.copyBatch(request,1000,proof);
            yes(tables.status().getString("phase").equals("READY"));
            tables.activate(request,sourceHash,proof);tables.activate(request,sourceHash,proof);yes(tables.active());
            yes(sourceHash.equals(hash(tables.exportSnapshot())));yes(original.equals(DatabaseBackupSnapshot.ownerValue(db,"snapshot","")));
            JSONObject row=tables.get("customers","customers_qa_0"),edited=new JSONObject(row.toString()).put("name","QA Änderung nach Übernahme");
            JSONObject receipt=tables.put("customers",edited,hash(row),"qa_table_write");yes(hash(receipt).equals(hash(tables.put("customers",edited,hash(row),"qa_table_write"))));
            denied("TABLE_REQUEST_REUSED",()->tables.put("customers",row,hash(edited),"qa_table_write"));
            denied("TABLE_WRITE_CONFLICT",()->tables.put("customers",row,hash(row),"qa_table_conflict"));
            yes(tables.select("worklogs","order_id","orders_qa_0",0,100).length()==100);
            JSONObject filters=new JSONObject().put("order_id","orders_qa_0").put("offset",17).put("limit",23);
            yes(hash(SnapshotReadSupport.withoutBinary(tables.selected("worklogs","worklog",filters,source))).equals(hash(SnapshotReadSupport.select(source.getJSONArray("worklogs"),filters,"worklog",source))));
            filters=new JSONObject().put("query","Synthetisch 2").put("offset",3).put("limit",7);
            JSONObject expectedSource=new JSONObject(source.toString());expectedSource.getJSONArray("customers").put(0,edited);
            yes(hash(SnapshotReadSupport.withoutBinary(tables.selected("customers","customer",filters,source))).equals(hash(SnapshotReadSupport.select(expectedSource.getJSONArray("customers"),filters,"customer",source))));
            JSONObject current=tables.exportSnapshot();JSONArray reversed=new JSONArray();JSONArray customers=current.getJSONArray("customers");for(int i=customers.length()-1;i>=0;i--)reversed.put(customers.get(i));current.put("customers",reversed);
            tables.replaceSnapshot(current);yes(hash(current).equals(hash(tables.exportSnapshot())));yes(original.equals(DatabaseBackupSnapshot.ownerValue(db,"snapshot","")));
            File captured=new File(directory,"captured.sqlite");DatabaseBackupSnapshot.capture(db,captured);
            try(SQLiteDatabase restore=SQLiteDatabase.openDatabase(captured.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){yes(hash(current).equals(hash(PixelLocalStore.businessTables(restore).snapshot("{}"))));}
            String currentHash=hash(current);BusinessTableStore.Evidence currentProof=value->{yes(currentHash.equals(hash(value)));return hash("synthetic current archive");};
            tables.rollback("qa_table_rollback",currentHash,currentProof);tables.rollback("qa_table_rollback",currentHash,currentProof);
            yes(!tables.active());yes(currentHash.equals(hash(tables.snapshot("{}"))));
            yes(DatabaseBackupSnapshot.ownerValue(db,"business_tables:original:"+hash(request),"").equals(original));
        }
    }
    private void runJournal(File directory)throws Exception {
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(new File(directory,"journal.sqlite"),null)){
            db.execSQL("CREATE TABLE owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");db.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)");
            JSONObject source=new JSONObject();for(String collection:BusinessTableStore.COLLECTIONS)source.put(collection,new JSONArray());
            JSONObject customer=new JSONObject().put("id","qa_customer").put("name","QA Alt").put("active",1);source.getJSONArray("customers").put(customer);
            db.execSQL("INSERT INTO owner_state VALUES('snapshot',?)",new Object[]{source.toString()});String sourceHash=hash(source);BusinessTableStore tables=PixelLocalStore.businessTables(db);
            BusinessTableStore.Evidence proof=value->hash("synthetic journal archive");tables.prepare("qa_journal_transfer",sourceHash,proof);tables.copyBatch("qa_journal_transfer",100,proof);tables.activate("qa_journal_transfer",sourceHash,proof);
            BusinessTableStore.Action<Void> commit=()->{PixelLocalStore.businessTables(db,true).commitJournal(LocalPendingActions.read(db,false,id->null,0),master->{PixelDriveState.prepare(master);master.put("processing_engine",LocalAuthorityRules.ENGINE);});return null;};
            JSONObject payload=new JSONObject().put("id","qa_customer").put("name","QA Neu").put("_sync_base",DriveJournalRules.guard(customer,"customer"));
            PixelLocalStore.beginJournalTransaction(db,commit);try{
                append(db,"qa_temporary",payload);db.execSQL("DELETE FROM pending WHERE id='qa_temporary'");append(db,"qa_final",payload);
                db.execSQL("INSERT INTO owner_state VALUES('receipt:qa_final','{}')");yes(!tables.projectionCurrent());db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            yes(tables.projectionCurrent());yes(tables.get("customers","qa_customer").getString("name").equals("QA Neu"));
            JSONObject current=tables.exportSnapshot();yes(DriveJournalRules.ids(current.getJSONArray("drive_applied_ids")).contains("qa_final"));yes(!DriveJournalRules.ids(current.getJSONArray("drive_applied_ids")).contains("qa_temporary"));
            yes(LocalAuthorityRules.ENGINE.equals(current.getString("processing_engine")));
            yes(hash(source).equals(hash(tables.originalSnapshot())));
            db.execSQL("CREATE TRIGGER qa_fail BEFORE INSERT ON business_customers WHEN NEW.id='qa_customer' BEGIN SELECT RAISE(ABORT,'QA_STORAGE_FAILURE'); END");
            denied("LOCAL_JOURNAL_TABLE_COMMIT_FAILED",()->{
                PixelLocalStore.beginJournalTransaction(db,commit);try{
                    append(db,"qa_failure",new JSONObject().put("id","qa_customer").put("name","QA Verlust verboten").put("_sync_base",DriveJournalRules.guard(tables.get("customers","qa_customer"),"customer")));
                    db.execSQL("INSERT INTO owner_state VALUES('receipt:qa_failure','{}')");db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            });
            yes(DatabaseBackupSnapshot.ownerValue(db,"receipt:qa_failure",null)==null);yes(hash(current).equals(hash(tables.exportSnapshot())));
            try(android.database.Cursor rows=db.rawQuery("SELECT count(*) FROM pending WHERE id='qa_failure'",null)){yes(rows.moveToFirst()&&rows.getInt(0)==0);}
            db.execSQL("DROP TRIGGER qa_fail");yes(tables.projectionCurrent());
            // Fresh adapter after commit: a restart must use the same sealed row projection.
            yes(PixelLocalStore.businessTables(db).projectionCurrent());
        }
    }
    private static void append(SQLiteDatabase db,String id,JSONObject payload){db.execSQL("INSERT INTO pending(id,kind,payload,created_at) VALUES(?,'customer',?,'2026-10-28T12:00:00Z')",new Object[]{id,payload.toString()});}
    private static void removeOwned(File directory)throws Exception {
        // Only the fresh random directory created above is ever passed here.
        File[] entries=directory.listFiles();if(entries!=null)for(File entry:entries){if(entry.isDirectory())removeOwned(entry);else if(!entry.delete())throw new Exception("SYNTHETIC_CLEANUP_FAILED");}if(!directory.delete())throw new Exception("SYNTHETIC_CLEANUP_FAILED");
    }
}
