package at.hausgarten.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Shared UI/MCP boundary. Mutations only persist intent; workers do file/network work after commit. */
final class FullBackupController {
    private final Context context;
    private final PixelLocalStore store;
    private final BackupSettingsRepository settings;
    private final LocalBackupCoordinator service;
    FullBackupController(Context context,PixelLocalStore store)throws Exception {this(context,store,new LocalBackupCoordinator(context,store));}
    FullBackupController(Context context,PixelLocalStore store,LocalBackupCoordinator service)throws Exception {this.context=context;this.store=store;this.service=service;settings=new BackupSettingsRepository(context,store);}

    JSONObject status(JSONObject args)throws Exception {
        int offset=args.optInt("offset",0),limit=args.optInt("limit",20);if(offset<0||offset>1000000||limit<1||limit>100)throw new IOException("BACKUP_PAGE_INVALID");
        String selected=args.optString("backup_id","");if(!selected.isEmpty())requireId(selected);
        JSONObject config=settings.read();JSONArray records=service.records();List<JSONObject> projected=new ArrayList<>();long newestCapture=Long.MIN_VALUE;String verifiedAt="",capturedAt="",scope="none";
        for(int i=0;i<records.length();i++){
            JSONObject entry=records.getJSONObject(i),job=entry.getJSONObject("job"),summary=summary(job,entry.getBoolean("sameInstallation")&&entry.getBoolean("sameRestoreEpoch"),config);
            boolean remote=summary.optString("phase").equals("REMOTE_VERIFIED")&&!summary.optString("remote_verified_at").isEmpty(),usb=summary.optBoolean("external_storage_verified")&&!summary.optString("provider_verified_at").isEmpty();
            if(summary.getBoolean("current_context")&&(remote||usb)){
                String at=summary.optString("captured_at"),check=summary.optString(remote?"remote_verified_at":"provider_verified_at");long time=millis(at);
                if(!check.isEmpty()&&(time>newestCapture||time==newestCapture&&millis(check)>millis(verifiedAt))){newestCapture=time;capturedAt=at;verifiedAt=check;scope=remote?"direct_drive_complete_hash_readback":"removable_storage_complete_readback";}
            }
            if(selected.isEmpty()||selected.equals(summary.getString("backup_id")))projected.add(summary);
        }
        projected.sort((a,b)->{int comparison=Long.compare(millis(b.optString("created_at")),millis(a.optString("created_at")));return comparison==0?a.optString("backup_id").compareTo(b.optString("backup_id")):comparison;});
        JSONArray page=new JSONArray();for(int i=offset;i<projected.size()&&page.length()<limit;i++)page.put(projected.get(i));
        boolean browserReady=false;String browserAt="";try{JSONObject browser=settings.browser();browserAt=browser.optString("capturedAt");browserReady=true;}catch(IOException|JSONException missing){/* Explicit setup/state status below; never synthesize an empty browser snapshot. */}
        boolean contextMatches=config.optBoolean("contextMatches"),target=validTarget(config),key=config.optBoolean("keyConfigured");long now=System.currentTimeMillis();String browserError=settings.browserError();if(!browserReady&&browserError.isEmpty())browserError="BACKUP_BROWSER_CAPTURE_REQUIRED";
        JSONObject scheduler=new JSONObject();try{scheduler=new JSONObject(store.ownerValue("full_backup_scheduler_status","{}"));}catch(JSONException invalid){scheduler.put("code","BACKUP_SCHEDULER_STATUS_REVIEW_REQUIRED");}
        return new JSONObject().put("format_version",1).put("settings_hash",config.getString("revision")).put("enabled",contextMatches&&config.optBoolean("enabled")).put("enabled_requested",config.optBoolean("enabled")).put("context_matches",contextMatches).put("key_configured",key).put("target_configured",target).put("unmetered_only",config.optBoolean("unmeteredOnly",true)).put("browser_checkpoint_available",browserReady).put("browser_captured_at",browserAt).put("browser_checkpoint_error",browserError).put("browser_capture_pending",settings.browserCapturePending()).put("requires_setup",!contextMatches||!target||!key||!browserReady).put("jobs",page).put("total",projected.size()).put("offset",offset).put("limit",limit).put("last_external_verified_at",verifiedAt).put("last_external_captured_at",capturedAt).put("last_external_data_age_ms",newestCapture==Long.MIN_VALUE||newestCapture>now?JSONObject.NULL:now-newestCapture).put("clock_review_required",newestCapture>now&&newestCapture-now>120000).put("last_scheduler_error_code",safeCode(scheduler.optString("code"))).put("last_scheduler_error_at",instant(scheduler.optString("at"))).put("exact_execution_guaranteed",false).put("cloud_confirmed",scope.equals("direct_drive_complete_hash_readback")).put("confirmation_scope",scope);
    }

    JSONObject request(JSONObject args)throws Exception {
        String request=args.getString("request_id"),expected=args.getString("expected_settings_hash");requireRequest(request);requireHash(expected);
        JSONObject identity=new BackupDeviceGuard(context).current();String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("operation","request").put("args",args).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch"))),receipt=receiptKey(request);SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject previous=receipt(receipt,fingerprint);if(previous!=null){db.setTransactionSuccessful();return previous;}
            JSONObject config=settings.read();requireReady(config);if(!expected.equals(config.getString("revision")))throw new IOException("BACKUP_SETTINGS_WRITE_CONFLICT");
            JSONObject checkpoint=settings.browser(),browser=checkpoint.getJSONObject("browser").put("checkpointCapturedAt",checkpoint.getString("capturedAt"));String id=request.substring(0,Math.min(32,request.length()))+"_"+PixelMcpWriteTools.hash(new JSONObject().put("request",request).put("identity",identity)).substring(0,24);
            JSONObject job=service.createWithIntent(request,id,destination(config),browser,"manual");JSONObject result=result(job,false);store.putOwnerValue(receipt,new JSONObject().put("fingerprint",fingerprint).put("backupId",id).put("result",result).toString());db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }

    JSONObject retry(JSONObject args)throws Exception {
        String request=args.getString("request_id"),id=args.getString("backup_id"),expected=args.getString("expected_record_hash");requireRequest(request);requireId(id);requireHash(expected);
        JSONObject identity=new BackupDeviceGuard(context).current();String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("operation","retry").put("args",args).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch"))),receipt=receiptKey(request);SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject previous=receipt(receipt,fingerprint);if(previous!=null){db.setTransactionSuccessful();return previous;}
            JSONObject config=settings.read();requireReady(config);JSONObject job=service.status(id);if(!targetMatches(job,config))throw new IOException("BACKUP_DESTINATION_CHANGED_REVIEW_REQUIRED");
            if(!BackupJobRepository.validPhase(job.optString("phase")))throw new IOException("BACKUP_JOB_PHASE_UNSUPPORTED");
            if(!job.getString("revision").equals(expected))throw new IOException("BACKUP_JOB_WRITE_CONFLICT");if(Arrays.asList("REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED").contains(job.getString("phase")))throw new IOException("BACKUP_ALREADY_COMPLETE");
            JSONObject archive=job.optJSONObject("archive");BackupJobRepository repository=new BackupJobRepository(store,identity.getString("installationId"),identity.getString("restoreEpoch"));
            job=repository.update(id,expected,new JSONObject().put("phase",archive!=null&&archive.has("localVerifiedAt")?"LOCAL_VERIFIED":"RETRY_PENDING").put("attempts",0).put("localAttempts",0).put("nextAttemptAt",0).put("lastErrorCode","").put("manualRequested",true).put("runnerAttemptId",""));
            JSONObject result=result(job,false);store.putOwnerValue(receipt,new JSONObject().put("fingerprint",fingerprint).put("backupId",id).put("result",result).toString());db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }

    private JSONObject receipt(String key,String fingerprint)throws Exception {
        String raw=store.ownerValue(key,"");if(raw.isEmpty())return null;JSONObject prior=new JSONObject(raw);if(!fingerprint.equals(prior.getString("fingerprint")))throw new IOException("BACKUP_CONTROL_REPLAY_MISMATCH");service.status(prior.getString("backupId"));return new JSONObject(prior.getJSONObject("result").toString()).put("replayed",true);
    }
    private static JSONObject result(JSONObject job,boolean replayed)throws Exception {return new JSONObject().put("backup_id",job.getString("backupId")).put("phase",job.getString("phase")).put("record_hash",job.getString("revision")).put("accepted_locally",true).put("replayed",replayed).put("cloud_confirmed",false).put("scheduled",false).put("next_action","schedule_after_commit");}
    private void requireReady(JSONObject config)throws Exception {
        if(!config.optBoolean("contextMatches"))throw new IOException("BACKUP_SETUP_REVIEW_REQUIRED");if(!validTarget(config))throw new IOException("BACKUP_DRIVE_DESTINATION_REQUIRED");if(!config.optBoolean("keyConfigured"))throw new IOException("BACKUP_RECOVERY_SETUP_REQUIRED");new BackupRecoveryKeyStore(context).read();
    }
    static boolean validTarget(JSONObject config){return DriveRestClient.validId(config.optString("folderId"))&&!config.optString("account").trim().isEmpty();}
    static boolean targetMatches(JSONObject job,JSONObject config){JSONObject destination=job.optJSONObject("destination");return destination!=null&&destination.optString("kind").equals("drive")&&destination.optString("folderId").equals(config.optString("folderId"))&&destination.optString("account").equals(config.optString("account"));}
    static JSONObject destination(JSONObject config)throws Exception {return new JSONObject().put("kind","drive").put("account",config.getString("account")).put("folderId",config.getString("folderId"));}
    private static String receiptKey(String request)throws Exception{return "full_backup_control_request:"+PixelMcpWriteTools.hash(request);}
    private static void requireRequest(String id)throws IOException {if(id==null||!id.matches("[A-Za-z0-9_-]{8,128}"))throw new IOException("BACKUP_REQUEST_ID_REQUIRED");}
    private static void requireId(String id)throws IOException {if(id==null||!id.matches("[A-Za-z0-9_-]{8,100}"))throw new IOException("BACKUP_JOB_ID_REQUIRED");}
    private static void requireHash(String hash)throws IOException {if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IOException("BACKUP_REVISION_REQUIRED");}
    private static String instant(String value){try{return Instant.parse(value).toString();}catch(Exception invalid){return "";}}
    private static long millis(String value){try{return Instant.parse(value).toEpochMilli();}catch(Exception invalid){return Long.MIN_VALUE;}}
    private static String safeCode(String value){return value.matches("[A-Z][A-Z0-9_]{0,100}")?value:"";}
    private static JSONObject summary(JSONObject job,boolean current,JSONObject config)throws Exception {
        boolean known=job.optInt("formatVersion")==1;JSONObject archive=known?job.optJSONObject("archive"):null,upload=known?job.optJSONObject("upload"):null,provider=known?job.optJSONObject("provider"):null;
        String phase=known&&BackupJobRepository.validPhase(job.optString("phase"))?job.optString("phase"):"FORMAT_REVIEW_REQUIRED",sha=archive==null?"":archive.optString("sha256"),captured=archive==null?"":archive.optString("capturedAt");if(!sha.matches("[0-9a-f]{64}"))sha="";
        String remote=phase.equals("REMOTE_VERIFIED")&&upload!=null?instant(upload.optString("remoteVerifiedAt")):"";boolean match=current&&known&&targetMatches(job,config),uploadStage=archive!=null&&archive.has("localVerifiedAt"),limited=known&&!Arrays.asList("REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED").contains(phase)&&job.optInt(uploadStage?"attempts":"localAttempts")>=BackupScheduleRules.MAX_ATTEMPTS&&!BackupAutomation.stageRunning(uploadStage);if(limited)phase="NEEDS_USER_ACTION";String lastError=safeCode(job.optString("lastErrorCode"));if(limited&&lastError.isEmpty())lastError="BACKUP_RETRY_LIMIT_REVIEW_REQUIRED";
        boolean externalProvider=current&&provider!=null&&provider.optBoolean("external_storage_verified")&&"REMOVABLE_STORAGE_VOLUME".equals(provider.optString("destination_location"))&&"DOCUMENT_PROVIDER_READBACK".equals(provider.optString("verification_scope"))&&sha.equals(provider.optString("archive_sha256"))&&archive!=null&&archive.optLong("size",-1)==provider.optLong("byte_size",-2)&&job.optString("backupId").equals(provider.optString("backup_id"));
        return new JSONObject().put("backup_id",job.getString("backupId")).put("phase",phase).put("record_hash",known?job.getString("revision"):"").put("created_at",instant(job.optString("createdAt"))).put("captured_at",instant(captured)).put("local_verified_at",archive==null?"":instant(archive.optString("localVerifiedAt"))).put("remote_verified_at",remote).put("provider_verified_at",provider==null?"":instant(provider.optString("verified_at"))).put("external_storage_verified",externalProvider).put("provider_location",provider==null?"":provider.optString("destination_location")).put("archive_sha256",sha).put("byte_size",archive==null?0:Math.max(0,archive.optLong("size"))).put("last_error_code",lastError).put("attempts",Math.max(0,job.optInt("attempts"))).put("local_attempts",Math.max(0,job.optInt("localAttempts"))).put("next_attempt_at",job.optLong("nextAttemptAt",0)==Long.MAX_VALUE?JSONObject.NULL:Math.max(0,job.optLong("nextAttemptAt",0))).put("current_context",current&&known).put("context_matches",current&&known).put("current_target",match).put("target_matches",match).put("retry_available",match&&!Arrays.asList("REMOTE_VERIFIED","PROVIDER_VERIFIED","CANCELLED").contains(phase)).put("cloud_confirmed",match&&!remote.isEmpty()).put("destination_kind",known&&job.optJSONObject("destination")!=null?job.getJSONObject("destination").optString("kind"):"");
    }
}
