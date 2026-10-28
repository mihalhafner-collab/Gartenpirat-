package at.hausgarten.service;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Explicit SAF generation export. Provider readback is never represented as remote cloud upload. */
final class RetiredBackupSafService {
    private static final ReentrantLock RUNNING=new ReentrantLock();
    private final Context context;private final PixelLocalStore store;
    RetiredBackupSafService(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    JSONObject createAndExport(String requestId,Uri treeUri)throws Exception {
        requireTree(context,treeUri);request(requestId);
        RetiredLocalBackupCoordinator service=new RetiredLocalBackupCoordinator(context,store);String backupId="backup_"+PixelMcpWriteTools.hash(requestId).substring(0,32);boolean exists=false;
        try{JSONObject job=service.status(backupId);if(!job.getString("requestId").equals(requestId)||!job.getJSONObject("destination").optString("kind").equals("saf")||!job.getJSONObject("destination").optString("treeUri").equals(treeUri.toString()))throw new IOException("BACKUP_SAF_REPLAY_MISMATCH");exists=true;}catch(IOException missing){if(!"BACKUP_JOB_NOT_FOUND".equals(missing.getMessage()))throw missing;}
        if(!exists){JSONObject checkpoint=new RetiredBackupSettingsRepository(context,store).browser();service.createWithIntent(requestId,backupId,new JSONObject().put("kind","saf").put("treeUri",treeUri.toString()),checkpoint.getJSONObject("browser"),"manual");}return exportBackup(requestId,backupId,treeUri);
    }
    JSONObject exportBackup(String requestId,String backupId,Uri treeUri)throws Exception {
        request(requestId);requireTree(context,treeUri);if(store.getWritableDatabase().inTransaction())throw new IOException("BACKUP_SAF_REQUIRES_OUTSIDE_TRANSACTION");
        if(!RUNNING.tryLock())throw new IOException("BACKUP_SAF_RUNNER_BUSY");
        try{
            RetiredLocalBackupCoordinator service=new RetiredLocalBackupCoordinator(context,store);JSONObject prepared=service.prepareLocal(backupId);if(prepared.optBoolean("runnerBusy"))throw new IOException("BACKUP_SAF_RUNNER_BUSY");
            File archive=service.verifiedArchive(backupId);JSONObject job=service.status(backupId),metadata=service.metadata(backupId),identity=new BackupDeviceGuard(context).current(),destination=destinationEvidence(context,treeUri);
            String operation=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch"))).substring(0,40);
            String key="full_backup_saf:"+PixelMcpWriteTools.hash(requestId),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("backup_id",backupId).put("tree_uri",treeUri.toString()).put("archive_sha256",job.getJSONObject("archive").getString("sha256")).put("identity",identity));
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                String raw=store.ownerValue(key,"");if(raw.isEmpty()){
                    JSONObject state=new JSONObject().put("formatVersion",1).put("operation_id",operation).put("request_fingerprint",fingerprint).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch")).put("backup_id",backupId).put("archive_sha256",job.getJSONObject("archive").getString("sha256")).put("byte_size",archive.length()).put("captured_at",metadata.getString("capturedAt")).put("archive_name","generation-"+operation+".hgb").put("commit_name","generation-"+operation+".hgc").put("phase","NEW").put("created_at",Instant.now().toString()).put("destination_location",destination.getString("destination_location")).put("external_storage_verified",destination.getBoolean("external_storage_verified"));store.putOwnerValue(key,state.toString());
                }else{JSONObject prior=new JSONObject(raw);if(prior.optInt("formatVersion")!=1)throw new IOException("BACKUP_SAF_OPERATION_SCHEMA_UNSUPPORTED");if(!prior.getString("request_fingerprint").equals(fingerprint))throw new IOException("BACKUP_SAF_REPLAY_MISMATCH");}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            BackupDocumentTransfer.Checkpoint saved=checkpoint(key,identity);
            File work=new File(context.getNoBackupFilesDir(),"full-backup-saf/"+operation);if(Files.isSymbolicLink(work.toPath())||!work.getCanonicalPath().startsWith(context.getNoBackupFilesDir().getCanonicalPath()+File.separator))throw new IOException("BACKUP_SAF_STAGING_INVALID");if(!work.isDirectory()&&!work.mkdirs())throw new IOException("BACKUP_SAF_STAGING_UNAVAILABLE");
            try{
                JSONObject result=BackupDocumentTransfer.export(archive,new File(work,"commit.hgc"),new BackupRecoveryKeyStore(context).read(),new SafDocuments(context,treeUri),saved,ciphertext->{
                    File stage=new File(work,"verify-"+UUID.randomUUID());try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(ciphertext,new BackupRecoveryKeyStore(context).read(),stage,Math.max(0,work.getUsableSpace()-BackupStorageBudget.BUSINESS_RESERVE_BYTES),PixelLocalStore.SCHEMA_VERSION)){
                        if(!verified.manifest.getString("backupId").equals(backupId))throw new IOException("BACKUP_SAF_GENERATION_MISMATCH");LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);
                    }
                });
                result.put("last_error_code","");saved.save(result);service.recordProviderProof(backupId,new JSONObject().put("backup_id",backupId).put("archive_sha256",result.getString("archive_sha256")).put("byte_size",result.getLong("byte_size")).put("verified_at",result.getString("verified_at")).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("destination_location",result.getString("destination_location")).put("external_storage_verified",result.optBoolean("external_storage_verified")).put("cloud_confirmed",false));return view(result);
            }catch(Exception failure){try{JSONObject state=saved.read();state.put("last_error_code",safeCode(failure));saved.save(state);}catch(Exception ignored){}throw failure;}
        }finally{RUNNING.unlock();}
    }
    JSONObject status(String requestId)throws Exception {
        request(requestId);String raw=store.ownerValue("full_backup_saf:"+PixelMcpWriteTools.hash(requestId),"");if(raw.isEmpty())throw new IOException("BACKUP_SAF_OPERATION_NOT_FOUND");return view(new JSONObject(raw));
    }
    private BackupDocumentTransfer.Checkpoint checkpoint(String key,JSONObject identity)throws Exception {
        return new BackupDocumentTransfer.Checkpoint(){String revision=PixelMcpWriteTools.hash(store.ownerValue(key,""));
            public JSONObject read()throws Exception{JSONObject now=new BackupDeviceGuard(context).current();if(!now.getString("installationId").equals(identity.getString("installationId"))||!now.getString("restoreEpoch").equals(identity.getString("restoreEpoch")))throw new IOException("BACKUP_SAF_RESTORE_CONTEXT_CHANGED");JSONObject state=new JSONObject(store.ownerValue(key,"{}"));if(state.optInt("formatVersion")!=1)throw new IOException("BACKUP_SAF_OPERATION_SCHEMA_UNSUPPORTED");return state;}
            public void save(JSONObject state)throws Exception{read();SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{String current=store.ownerValue(key,"");String next=state.toString();if(!PixelMcpWriteTools.hash(current).equals(revision)&&!current.equals(next))throw new IOException("BACKUP_SAF_WRITE_CONFLICT");store.putOwnerValue(key,next);revision=PixelMcpWriteTools.hash(next);db.setTransactionSuccessful();}finally{db.endTransaction();}}
        };
    }
    private static JSONObject view(JSONObject state)throws Exception {JSONObject out=new JSONObject();for(String key:new String[]{"backup_id","operation_id","phase","created_at","captured_at","byte_size","archive_sha256","provider_readback_at","verified_at","last_error_code","destination_location","external_storage_verified"})if(state.has(key))out.put(key,state.get(key));return out.put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("cloud_confirmed",false).put("remote_upload_proven",false).put("needs_user_action",!state.optString("last_error_code").isEmpty());}
    private static void request(String request)throws IOException{if(request==null||!request.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("BACKUP_SAF_REQUEST_ID_REQUIRED");}
    static void requireTree(Context context,Uri uri)throws IOException {
        if(uri==null||!"content".equals(uri.getScheme())||!DocumentsContract.isTreeUri(uri))throw new IOException("BACKUP_SAF_TREE_REQUIRED");
        for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())if(permission.getUri().equals(uri)&&permission.isReadPermission()&&permission.isWritePermission())return;
        int flags=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if(context.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),flags)==android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        throw new IOException("BACKUP_SAF_PERMISSION_REQUIRED");
    }
    private static JSONObject destinationEvidence(Context context,Uri uri)throws Exception {
        String location="DOCUMENT_PROVIDER_EXTERNALITY_UNCONFIRMED";boolean external=false;
        try{if("com.android.externalstorage.documents".equals(uri.getAuthority())){String id=DocumentsContract.getTreeDocumentId(uri);String volume=id.contains(":")?id.substring(0,id.indexOf(':')):id;if(volume.equals("primary"))location="SAME_DEVICE";else{android.os.storage.StorageManager manager=context.getSystemService(android.os.storage.StorageManager.class);if(manager!=null)for(android.os.storage.StorageVolume item:manager.getStorageVolumes())if(item.getUuid()!=null&&item.getUuid().equalsIgnoreCase(volume)&&item.isRemovable()&&!item.isPrimary()&&android.os.Environment.MEDIA_MOUNTED.equals(item.getState())){external=true;location="REMOVABLE_STORAGE_VOLUME";break;}}}}
        catch(Exception unavailable){external=false;location="DOCUMENT_PROVIDER_EXTERNALITY_UNCONFIRMED";}
        return new JSONObject().put("destination_location",location).put("external_storage_verified",external);
    }

    private static String safeCode(Exception error){String message=error.getMessage();return message!=null&&message.matches("(?:BACKUP|RECOVERY)_[A-Z0-9_]{1,100}")?message:"BACKUP_SAF_PROVIDER_IO";}
    static final class SafDocuments implements BackupDocumentTransfer.Documents {
        private final ContentResolver resolver;private final Uri tree,parent;
        SafDocuments(Context context,Uri tree){resolver=context.getContentResolver();this.tree=tree;parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
        public List<String> find(String displayName)throws Exception {ArrayList<String> result=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));try(Cursor rows=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(rows==null)throw new IOException("BACKUP_SAF_INVENTORY_UNAVAILABLE");while(rows.moveToNext())if(displayName.equals(rows.getString(1)))result.add(DocumentsContract.buildDocumentUriUsingTree(tree,rows.getString(0)).toString());}return result;}
        public String create(String name,String mime)throws Exception {Uri value=DocumentsContract.createDocument(resolver,parent,mime,name);return value==null?null:value.toString();}
        public InputStream read(String id)throws Exception{InputStream input=resolver.openInputStream(Uri.parse(id));if(input==null)throw new IOException("BACKUP_SAF_READ_UNAVAILABLE");return input;}
        public OutputStream replaceOwnCandidate(String id)throws Exception{OutputStream output=resolver.openOutputStream(Uri.parse(id),"wt");if(output==null)throw new IOException("BACKUP_SAF_WRITE_UNAVAILABLE");return output;}
    }
}
