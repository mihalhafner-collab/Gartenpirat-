package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Durable technical backup jobs in the existing DB; never changes business snapshots. */
final class BackupJobRepository {
    private final PixelLocalStore store;
    private final String installation;
    private final String epoch;
    private static final Set<String> PHASES=new HashSet<>(Arrays.asList("CAPTURE_PENDING","CAPTURING","CAPTURED","SEALING","SEALED_LOCAL","LOCAL_VERIFIED","UPLOAD_PENDING","UPLOADING","REMOTE_VERIFY_PENDING","REMOTE_VERIFIED","PROVIDER_WRITING","PROVIDER_VERIFY_PENDING","PROVIDER_VERIFIED","RETRY_PENDING","NEEDS_USER_ACTION","FAILED","CANCELLED"));
    private static final Set<String> MUTABLE=new HashSet<>(Arrays.asList("phase","capture","archive","upload","provider","attempts","nextAttemptAt","lastErrorCode","lastAttemptAt","verifiedAt"));
    BackupJobRepository(PixelLocalStore store,String installation,String epoch){if(installation==null||!installation.matches("[A-Za-z0-9_-]{8,200}")||epoch==null||!epoch.matches("[A-Za-z0-9_-]{8,200}"))throw new IllegalArgumentException("BACKUP_INSTALLATION_AND_EPOCH_REQUIRED");this.store=store;this.installation=installation;this.epoch=epoch;}
    JSONObject create(String requestId,String backupId,JSONObject destination)throws Exception {
        return create(requestId,backupId,destination,new JSONObject());
    }
    JSONObject create(String requestId,String backupId,JSONObject destination,JSONObject inputs)throws Exception {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}")||backupId==null||!backupId.matches("[A-Za-z0-9_-]{8,100}"))throw new IOException("BACKUP_JOB_ID_REQUIRED");
        String kind=destination.getString("kind");if(!kind.equals("drive")&&!kind.equals("saf"))throw new IOException("BACKUP_DESTINATION_UNSUPPORTED");
        String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("requestId",requestId).put("backupId",backupId).put("installationId",installation).put("restoreEpoch",epoch).put("destination",destination).put("inputs",inputs));
        String receiptKey="backup_job_request:"+PixelMcpWriteTools.hash(requestId);SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            String receipt=store.ownerValue(receiptKey,"");
            if(!receipt.isEmpty()){
                JSONObject prior=new JSONObject(receipt);if(!fingerprint.equals(prior.getString("fingerprint")))throw new IOException("BACKUP_JOB_REPLAY_MISMATCH");
                JSONObject result=load(prior.getString("backupId")).put("replayed",true);db.setTransactionSuccessful();return result;
            }
            if(!store.ownerValue(key(backupId),"").isEmpty())throw new IOException("BACKUP_JOB_ID_CONFLICT");
            JSONObject job=new JSONObject().put("formatVersion",1).put("backupId",backupId).put("installationId",installation).put("restoreEpoch",epoch).put("requestId",requestId).put("requestFingerprint",fingerprint).put("destination",new JSONObject(destination.toString())).put("inputs",new JSONObject(inputs.toString())).put("createdAt",java.time.Instant.now().toString()).put("phase","CAPTURE_PENDING").put("attempts",0);
            store.putOwnerValue(key(backupId),job.toString());store.putOwnerValue(receiptKey,new JSONObject().put("backupId",backupId).put("fingerprint",fingerprint).toString());db.setTransactionSuccessful();return view(job).put("replayed",false);
        }finally{db.endTransaction();}
    }
    JSONObject load(String backupId)throws Exception {
        String saved=store.ownerValue(key(backupId),"");if(saved.isEmpty())throw new IOException("BACKUP_JOB_NOT_FOUND");JSONObject job=new JSONObject(saved);
        if(job.getInt("formatVersion")!=1||!backupId.equals(job.getString("backupId")))throw new IOException("BACKUP_JOB_FORMAT_UNSUPPORTED");
        if(!installation.equals(job.getString("installationId"))||!epoch.equals(job.getString("restoreEpoch")))throw new IOException("BACKUP_JOB_RESTORE_CONTEXT_REVIEW_REQUIRED");return view(job);
    }
    JSONObject update(String backupId,String expectedRevision,JSONObject changes)throws Exception {
        if(expectedRevision==null||!expectedRevision.matches("[0-9a-f]{64}"))throw new IOException("BACKUP_JOB_REVISION_REQUIRED");
        for(Iterator<String> keys=changes.keys();keys.hasNext();)if(!MUTABLE.contains(keys.next()))throw new IOException("BACKUP_JOB_FIELD_IMMUTABLE");
        if(changes.has("phase")&&!PHASES.contains(changes.getString("phase")))throw new IOException("BACKUP_JOB_PHASE_UNSUPPORTED");
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject current=load(backupId);String revision=current.getString("revision");current.remove("revision");
            JSONObject updated=new JSONObject(current.toString());for(Iterator<String> keys=changes.keys();keys.hasNext();){String k=keys.next();updated.put(k,changes.get(k));}
            // A repeated identical step is a replay even when its reply was lost.
            if(PixelMcpWriteTools.hash(current).equals(PixelMcpWriteTools.hash(updated))){db.setTransactionSuccessful();return view(current);}
            if(!revision.equals(expectedRevision))throw new IOException("BACKUP_JOB_WRITE_CONFLICT");
            store.putOwnerValue(key(backupId),updated.toString());db.setTransactionSuccessful();return view(updated);
        }finally{db.endTransaction();}
    }
    DriveBackupUpload.Checkpoint uploadCheckpoint(String backupId)throws Exception {
        load(backupId);
        return new DriveBackupUpload.Checkpoint(){
            private String revision;
            public JSONObject read()throws Exception {JSONObject job=load(backupId);revision=job.getString("revision");JSONObject upload=job.optJSONObject("upload");return upload==null?null:new JSONObject(upload.toString());}
            public void save(JSONObject upload)throws Exception {
                if(revision==null)read();String phase=upload.getString("state");if(!Arrays.asList("UPLOAD_PENDING","UPLOADING","REMOTE_VERIFY_PENDING","REMOTE_VERIFIED").contains(phase))throw new IOException("BACKUP_UPLOAD_PHASE_UNSUPPORTED");
                JSONObject saved=update(backupId,revision,new JSONObject().put("phase",phase).put("upload",new JSONObject(upload.toString())));revision=saved.getString("revision");
            }
        };
    }
    JSONArray inventory()throws Exception {
        JSONArray jobs=new JSONArray();try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT key FROM owner_state WHERE key LIKE 'backup_job:%' ORDER BY key",null)){
            while(rows.moveToNext()){
                String id=rows.getString(0).substring("backup_job:".length());String raw=store.ownerValue(key(id),"");JSONObject row=new JSONObject(raw);boolean same=installation.equals(row.optString("installationId")),sameEpoch=epoch.equals(row.optString("restoreEpoch"));
                jobs.put(new JSONObject().put("backupId",id).put("phase",row.optString("phase")).put("createdAt",row.optString("createdAt")).put("sameInstallation",same).put("sameRestoreEpoch",sameEpoch).put("resumeContextMatches",same&&sameEpoch&&row.optInt("formatVersion")==1).put("destinationKind",row.getJSONObject("destination").optString("kind")));
            }
        }return jobs;
    }
    private static String key(String backupId)throws IOException {if(backupId==null||!backupId.matches("[A-Za-z0-9_-]{8,100}"))throw new IOException("BACKUP_JOB_ID_REQUIRED");return "backup_job:"+backupId;}
    private static JSONObject view(JSONObject job)throws Exception {return new JSONObject(job.toString()).put("revision",PixelMcpWriteTools.hash(job));}
}
