package at.hausgarten.service;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** One user-selected plaintext ZIP on this Pixel. No cloud, account or scheduler. */
final class PixelBackupService {
    private static final Set<String> RUNNING=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static JSONObject statusProjection(LocalBackupCoordinator service,JSONObject args)throws Exception {
        // Never hold a process lock across SQLite reads; include workers seen at either boundary.
        Set<String> active=new HashSet<>(RUNNING);JSONArray records=service.records();active.addAll(RUNNING);
        return BackupStatusRules.project(records,args,active,Instant.now());
    }
    private final Context context;private final PixelLocalStore store;
    PixelBackupService(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    static void cancelRetiredJobs(Context context){try{android.app.job.JobScheduler jobs=context.getSystemService(android.app.job.JobScheduler.class);if(jobs!=null)for(android.app.job.JobInfo job:jobs.getAllPendingJobs())if(job.getService().getClassName().endsWith(".FullBackupJob"))jobs.cancel(job.getId());}catch(RuntimeException unavailable){/* The retired service is absent from the manifest as well. */}}
    static void requirePixel(Uri uri)throws Exception {
        if(uri==null||!"content".equals(uri.getScheme())||!("com.android.providers.downloads.documents".equals(uri.getAuthority())||"com.android.externalstorage.documents".equals(uri.getAuthority())&&DocumentsContract.getDocumentId(uri).startsWith("primary:")))throw new IOException("BACKUP_PIXEL_FOLDER_REQUIRED");
    }
    JSONObject save(String request,Uri target,java.util.function.Consumer<String> progress)throws Exception {
        requirePixel(target);if(request==null||!request.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("BACKUP_REQUEST_ID_REQUIRED");
        LocalBackupCoordinator service=new LocalBackupCoordinator(context,store);String id="pixel_"+PixelMcpWriteTools.hash(request).substring(0,32);JSONObject job;
        if(!RUNNING.add(id))throw new IOException("BACKUP_RUNNER_BUSY");
        try{
        try{job=service.status(id);if(!job.getString("requestId").equals(request)||!target.toString().equals(job.getJSONObject("destination").optString("treeUri")))throw new IOException("BACKUP_LOCAL_TARGET_CHANGED");}
        catch(IOException absent){if(!"BACKUP_JOB_NOT_FOUND".equals(absent.getMessage()))throw absent;JSONObject checkpoint=new BackupSettingsRepository(context,store).browser();job=service.createWithIntent(request,id,new JSONObject().put("kind","saf").put("treeUri",target.toString()).put("purpose","pixel_plain_backup"),checkpoint.getJSONObject("browser"),"manual");}
        progress.accept("Bestand für die Sicherung aufnehmen …");JSONObject prepared=service.prepareLocal(id);if(prepared.optBoolean("runnerBusy"))throw new IOException("BACKUP_RUNNER_BUSY");
        try{File archive=service.verifiedArchive(id);job=service.status(id);
        progress.accept("ZIP-Datei speichern und vollständig zurücklesen …");String hash=job.getJSONObject("archive").getString("sha256");
        BackupDocumentTransfer.exportLocal(archive,hash,job.optJSONObject("provider")!=null,new BackupDocumentTransfer.LocalFile(){
            public OutputStream write()throws Exception{return context.getContentResolver().openOutputStream(target,"wt");}
            public InputStream read()throws Exception{return context.getContentResolver().openInputStream(target);}
        });
        service.recordProviderProof(id,new JSONObject().put("backup_id",id).put("archive_sha256",hash).put("byte_size",archive.length()).put("verified_at",Instant.now().toString()).put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("destination_location","SAME_DEVICE").put("external_storage_verified",false));
        return new JSONObject().put("backup_id",id).put("saved",true).put("verified",true).put("encrypted",false).put("byte_size",archive.length()).put("cloud_confirmed",false);
        }catch(Exception failure){service.recordFailure(id,failure);throw failure;}
        }finally{RUNNING.remove(id);}
    }
}
