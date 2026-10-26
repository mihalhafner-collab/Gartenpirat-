package at.hausgarten.service;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Conscious plaintext CSV/JSON/original export; distinct from encrypted full restore archives. */
final class BackupPortableExport {
    private static final ReentrantLock RUNNING=new ReentrantLock();
    private final Context context;private final PixelLocalStore store;
    BackupPortableExport(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    static void requireLocalDestination(Uri uri)throws IOException {
        if(uri==null||!"content".equals(uri.getScheme())||!"com.android.externalstorage.documents".equals(uri.getAuthority())||!android.provider.DocumentsContract.isTreeUri(uri))throw new IOException("PORTABLE_LOCAL_DESTINATION_REQUIRED");
    }
    JSONObject createAndExport(String requestId,Uri treeUri,boolean plaintextConfirmed)throws Exception {
        if(!plaintextConfirmed)throw new IOException("PORTABLE_PLAINTEXT_CONFIRMATION_REQUIRED");request(requestId);requireLocalDestination(treeUri);BackupSafService.requireTree(context,treeUri);
        String backupId="portable_"+PixelMcpWriteTools.hash(requestId).substring(0,32);LocalBackupCoordinator service=new LocalBackupCoordinator(context,store);boolean exists=false;
        try{JSONObject job=service.status(backupId);if(!job.getString("requestId").equals(requestId)||!job.getJSONObject("destination").optString("kind").equals("saf")||!job.getJSONObject("destination").optString("purpose").equals("portable_export")||!job.getJSONObject("destination").optString("treeUri").equals(treeUri.toString()))throw new IOException("PORTABLE_REPLAY_MISMATCH");exists=true;}catch(IOException absent){if(!"BACKUP_JOB_NOT_FOUND".equals(absent.getMessage()))throw absent;}
        if(!exists){JSONObject checkpoint=new BackupSettingsRepository(context,store).browser();service.createWithIntent(requestId,backupId,new JSONObject().put("kind","saf").put("treeUri",treeUri.toString()).put("purpose","portable_export"),checkpoint.getJSONObject("browser"),"manual");}
        return exportBackup(requestId,backupId,treeUri,true);
    }
    JSONObject exportBackup(String requestId,String backupId,Uri treeUri,boolean plaintextConfirmed)throws Exception {
        if(!plaintextConfirmed)throw new IOException("PORTABLE_PLAINTEXT_CONFIRMATION_REQUIRED");request(requestId);requireLocalDestination(treeUri);BackupSafService.requireTree(context,treeUri);if(store.getWritableDatabase().inTransaction())throw new IOException("PORTABLE_REQUIRES_OUTSIDE_TRANSACTION");if(!RUNNING.tryLock())throw new IOException("PORTABLE_RUNNER_BUSY");
        try{
            LocalBackupCoordinator service=new LocalBackupCoordinator(context,store);LocalBackupDataset.Captured captured=service.captureForPortable(backupId);JSONObject identity=new BackupDeviceGuard(context).current();String operation=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("identity",identity)).substring(0,40),key="portable_export:"+PixelMcpWriteTools.hash(requestId);
            String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("backup_id",backupId).put("tree_uri",treeUri.toString()).put("database_sha256",captured.metadata.getJSONObject("database").getString("sha256")).put("identity",identity).put("plaintext_confirmed",true));
            File work=new File(context.getNoBackupFilesDir(),"portable-export/"+operation);if(Files.isSymbolicLink(work.toPath())||!work.getCanonicalPath().startsWith(context.getNoBackupFilesDir().getCanonicalPath()+File.separator))throw new IOException("PORTABLE_STAGING_PATH_INVALID");if(!work.isDirectory()&&!work.mkdirs())throw new IOException("PORTABLE_STAGING_UNAVAILABLE");
            String saved=store.ownerValue(key,"");JSONObject state;
            if(saved.isEmpty()){
                File zip=new File(work,"export-"+UUID.randomUUID()+".zip");createZip(captured,zip);BackupPortableFormat.verify(zip,captured.metadata);state=new JSONObject().put("formatVersion",1).put("operation_id",operation).put("request_fingerprint",fingerprint).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch")).put("backup_id",backupId).put("archive_sha256",EncryptedBackupArchive.sha256(zip)).put("byte_size",zip.length()).put("local_zip",zip.getName()).put("captured_at",captured.metadata.getString("capturedAt")).put("archive_name","export-"+operation+".zip").put("commit_name","export-"+operation+".complete.json").put("commit_kind","hausgarten.portable-export.commit").put("phase","NEW").put("created_at",Instant.now().toString());
                SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{String concurrent=store.ownerValue(key,"");if(!concurrent.isEmpty())throw new IOException("PORTABLE_WRITE_CONFLICT");store.putOwnerValue(key,state.toString());db.setTransactionSuccessful();}finally{db.endTransaction();}
            }else{state=new JSONObject(saved);if(state.optInt("formatVersion")!=1)throw new IOException("PORTABLE_OPERATION_SCHEMA_UNSUPPORTED");if(!state.getString("request_fingerprint").equals(fingerprint))throw new IOException("PORTABLE_REPLAY_MISMATCH");}
            String filename=state.getString("local_zip");if(!filename.matches("export-[0-9a-f-]{36}\\.zip"))throw new IOException("PORTABLE_STAGING_PATH_INVALID");File zip=new File(work,filename);BackupDocumentTransfer.Checkpoint journal=checkpoint(key,identity);
            try{JSONObject result=BackupDocumentTransfer.exportPortable(zip,new File(work,"commit.json"),new BackupSafService.SafDocuments(context,treeUri),journal,input->{byte[] b=new byte[65536];while(input.read(b)!=-1)EncryptedBackupArchive.checkCancelled();});result.put("last_error_code","");journal.save(result);return view(result);}
            catch(Exception failure){try{JSONObject current=journal.read();current.put("last_error_code",safeCode(failure));journal.save(current);}catch(Exception ignored){}throw failure;}
        }finally{RUNNING.unlock();}
    }
    JSONObject status(String requestId)throws Exception {request(requestId);String saved=store.ownerValue("portable_export:"+PixelMcpWriteTools.hash(requestId),"");if(saved.isEmpty())throw new IOException("PORTABLE_OPERATION_NOT_FOUND");return view(new JSONObject(saved));}
    void createZip(LocalBackupDataset.Captured captured,File zip)throws Exception {
        File dataset=captured.directory;JSONObject prefs=new JSONObject(utf8(new File(dataset,"preferences.json")));List<EncryptedBackupArchive.Source> originals=new ArrayList<>();for(EncryptedBackupArchive.Source source:captured.sources())if(source.path.startsWith("files/customer-photo-originals/")||source.path.startsWith("files/business-documents/")||source.path.startsWith("cache/photo-capture/"))originals.add(source);
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(new File(dataset,"database.sqlite").getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);FileOutputStream output=new FileOutputStream(zip)){
            JSONObject state=BackupSnapshotState.read(db,BackupPreferences.values(prefs,"haus_garten"),utf8(new File(dataset,"bootstrap/initial-data.json")),utf8(new File(dataset,"bootstrap/service-catalog.json"))),effective=SnapshotProjection.effective(state),business=new JSONObject();
            for(String key:new String[]{"customers","orders","worklogs","invoices","photos","offers","appointments","appointment_series","service_catalog","business_settings","invoice_issuer","owner_archive","owner_conflict_history"})if(effective.has(key))business.put(key,effective.get(key));
            OutputStream nonClosing=new FilterOutputStream(BackupStorageBudget.guard(output,zip.getParentFile())){@Override public void close()throws IOException{flush();}};
            BackupPortableFormat.write(nonClosing,business,captured.metadata,originals,writer->writeLocalData(db,writer),additionalCsv(db));output.getFD().sync();
        }
    }
    private static Map<String,JSONArray> additionalCsv(SQLiteDatabase db)throws Exception {
        LinkedHashMap<String,JSONArray> groups=new LinkedHashMap<>();try(Cursor keys=db.rawQuery("SELECT key FROM owner_state WHERE key LIKE 'revier:v1:entity:%' OR key LIKE 'local_payment:%' OR key LIKE 'local_invoice_sources:%' OR key LIKE 'local_invoice_material_sources:%' OR key LIKE 'local_invoice_document_recovery:%' ORDER BY key",null)){
            while(keys.moveToNext()){String key=keys.getString(0);JSONObject value=new JSONObject(DatabaseBackupSnapshot.ownerValue(db,key,"{}"));String group;
                if(key.startsWith("revier:v1:entity:")){String tail=key.substring("revier:v1:entity:".length());int split=tail.indexOf(':');if(split<1)throw new IOException("PORTABLE_REVIER_ENTITY_KEY_INVALID");group=tail.substring(0,split);if(!groups.containsKey(group))groups.put(group,new JSONArray());groups.get(group).put(value);}
                else if(key.startsWith("local_invoice_document_recovery:")){group="invoice_document_recovery";if(!groups.containsKey(group))groups.put(group,new JSONArray());groups.get(group).put(value);}
                else if(key.startsWith("local_payment:")){group="payments";if(!groups.containsKey(group))groups.put(group,new JSONArray());groups.get(group).put(value);}
                else{boolean material=key.startsWith("local_invoice_material_sources:");String prefix=material?"local_invoice_material_sources:":"local_invoice_sources:";group=material?"invoice_material_allocations":"invoice_work_allocations";if(!groups.containsKey(group))groups.put(group,new JSONArray());for(Iterator<String> fields=value.keys();fields.hasNext();){String source=fields.next();groups.get(group).put(new JSONObject().put("invoice_id",key.substring(prefix.length())).put(material?"material_movement_id":"worklog_id",source).put("quantity",value.get(source)));}}
            }
        }return groups;
    }
    private static void writeLocalData(SQLiteDatabase db,Writer output)throws Exception {
        output.write("{\"formatVersion\":1,\"entries\":[");boolean first=true;
        try(Cursor keys=db.rawQuery("SELECT key FROM owner_state ORDER BY key",null)){while(keys.moveToNext()){String key=keys.getString(0);if(!allowedBusinessKey(key))continue;if(!first)output.write(',');first=false;output.write("{\"key\":");output.write(JSONObject.quote(key));output.write(",\"value\":");
            if(key.startsWith("local_invoice_job:")){JSONObject job=new JSONObject(DatabaseBackupSnapshot.ownerValue(db,key,"{}")),safe=new JSONObject();for(String field:new String[]{"formatVersion","invoice_id","phase","attempts","snapshot","snapshot_sha256","verified_at","last_error_code","origin_installation_id","origin_restore_epoch","creation_authorization","original_adoption","document"})if(job.has(field))safe.put(field,job.get(field));output.write(JSONObject.quote(safe.toString()));}
            else writeOwnerString(db,key,output);output.write('}');}}
        output.write("],\"personal_paid\":");String marks=DatabaseBackupSnapshot.ownerValue(db,"personal_paid","[]");boolean validMarks=true;try{output.write(new JSONArray(marks).toString());}catch(JSONException malformed){validMarks=false;output.write("null");}output.write(",\"personal_paid_source_value\":");output.write(JSONObject.quote(marks));output.write(",\"personal_paid_valid\":"+validMarks);output.write(",\"personal_paid_migrated\":");output.write(JSONObject.quote(DatabaseBackupSnapshot.ownerValue(db,"personal_paid_migrated","0")));output.write(",\"tables\":{");
        String[] tables={"work_sessions","work_session_events","photo_attachments"};for(int i=0;i<tables.length;i++){if(i>0)output.write(',');output.write(JSONObject.quote(tables[i]));output.write(':');writeTable(db,tables[i],output);}output.write("}}");output.flush();
    }
    static boolean allowedBusinessKey(String key){return BackupPortableFormat.allowedBusinessKey(key);}
    private static String utf8(File file)throws Exception {return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}

    private static void writeOwnerString(SQLiteDatabase db,String key,Writer output)throws Exception {long length;try(Cursor count=db.rawQuery("SELECT length(value) FROM owner_state WHERE key=?",new String[]{key})){if(!count.moveToFirst())throw new IOException("PORTABLE_SOURCE_CHANGED");length=count.getLong(0);}output.write('"');for(long offset=0;offset<length;offset+=32768)try(Cursor part=db.rawQuery("SELECT substr(value,?,32768) FROM owner_state WHERE key=?",new String[]{Long.toString(offset+1),key})){if(!part.moveToFirst())throw new IOException("PORTABLE_SOURCE_CHANGED");escaped(output,part.getString(0));}output.write('"');}
    private static void escaped(Writer output,String value)throws Exception {String quoted=JSONObject.quote(value);output.write(quoted,1,quoted.length()-2);}
    private static void writeTable(SQLiteDatabase db,String table,Writer output)throws Exception {output.write('[');boolean first=true;try(Cursor rows=db.rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)){String[] names=rows.getColumnNames();while(rows.moveToNext()){if(!first)output.write(',');first=false;output.write('{');for(int i=0;i<names.length;i++){if(i>0)output.write(',');output.write(JSONObject.quote(names[i]));output.write(':');switch(rows.getType(i)){case Cursor.FIELD_TYPE_NULL:output.write("null");break;case Cursor.FIELD_TYPE_INTEGER:output.write(Long.toString(rows.getLong(i)));break;case Cursor.FIELD_TYPE_FLOAT:output.write(rows.getString(i));break;case Cursor.FIELD_TYPE_STRING:output.write(JSONObject.quote(rows.getString(i)));break;default:throw new IOException("PORTABLE_BUSINESS_TABLE_TYPE_UNSUPPORTED");}}output.write('}');}}output.write(']');}
    private BackupDocumentTransfer.Checkpoint checkpoint(String key,JSONObject identity)throws Exception {return new BackupDocumentTransfer.Checkpoint(){String revision=PixelMcpWriteTools.hash(store.ownerValue(key,""));public JSONObject read()throws Exception{JSONObject current=new BackupDeviceGuard(context).current();if(!current.getString("installationId").equals(identity.getString("installationId"))||!current.getString("restoreEpoch").equals(identity.getString("restoreEpoch")))throw new IOException("PORTABLE_RESTORE_CONTEXT_CHANGED");JSONObject state=new JSONObject(store.ownerValue(key,"{}"));if(state.optInt("formatVersion")!=1)throw new IOException("PORTABLE_OPERATION_SCHEMA_UNSUPPORTED");return state;}public void save(JSONObject state)throws Exception{read();SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{String old=store.ownerValue(key,""),next=state.toString();if(!PixelMcpWriteTools.hash(old).equals(revision)&&!old.equals(next))throw new IOException("PORTABLE_WRITE_CONFLICT");store.putOwnerValue(key,next);revision=PixelMcpWriteTools.hash(next);db.setTransactionSuccessful();}finally{db.endTransaction();}}};}
    private static JSONObject view(JSONObject state)throws Exception {JSONObject out=new JSONObject();for(String key:new String[]{"backup_id","operation_id","phase","created_at","captured_at","byte_size","archive_sha256","verified_at","last_error_code"})if(state.has(key))out.put(key,state.get(key));return out.put("plaintext",true).put("restorable_database_backup",false).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("cloud_confirmed",false);}
    private static void request(String value)throws IOException{if(value==null||!value.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("PORTABLE_REQUEST_ID_REQUIRED");}
    private static String safeCode(Exception failure){String value=failure.getMessage();return value!=null&&value.matches("(?:PORTABLE|BACKUP)_[A-Z0-9_]{1,100}")?value:"PORTABLE_PROVIDER_IO";}
}
