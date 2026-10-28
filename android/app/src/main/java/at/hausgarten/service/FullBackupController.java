package at.hausgarten.service;
import android.content.Context;
import org.json.*;
import java.io.IOException;

/** Local-only metadata and explicit preparation. Saving requires the user's Android file choice. */
final class FullBackupController {
    private final Context context;private final PixelLocalStore store;
    FullBackupController(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    JSONObject status(JSONObject args)throws Exception {
        JSONArray all=new LocalBackupCoordinator(context,store).records(),jobs=new JSONArray();String verified="",captured="";int offset=Math.max(0,args.optInt("offset")),limit=Math.min(100,Math.max(1,args.optInt("limit",20))),total=0;
        for(int i=all.length()-1;i>=0;i--){JSONObject record=all.getJSONObject(i),job=record.getJSONObject("job");if(!record.optBoolean("sameInstallation")||!record.optBoolean("sameRestoreEpoch")||job.optJSONObject("destination")==null||(!job.getJSONObject("destination").optString("purpose").equals("pixel_plain_backup")&&!args.optBoolean("legacy_evidence")))continue;if(args.has("backup_id")&&!args.getString("backup_id").equals(job.getString("backupId")))continue;
            JSONObject proof=job.optJSONObject("provider");if(proof!=null&&proof.optString("verified_at").compareTo(verified)>0){verified=proof.optString("verified_at");captured=job.optJSONObject("archive")==null?"":job.getJSONObject("archive").optString("capturedAt");}
            if(total++<offset||jobs.length()>=limit)continue;JSONObject archive=job.optJSONObject("archive");jobs.put(new JSONObject().put("backup_id",job.getString("backupId")).put("phase",job.getString("phase")).put("record_hash",job.getString("revision")).put("current_context",true).put("context_matches",true).put("captured_at",archive==null?"":archive.optString("capturedAt")).put("media_coverage",archive==null?"":archive.optString("mediaCoverage")).put("external_storage_verified",proof!=null&&proof.optBoolean("external_storage_verified")).put("provider_verified_at",proof==null?"":proof.optString("verified_at")).put("remote_verified_at",job.optJSONObject("upload")==null?"":job.getJSONObject("upload").optString("remoteVerifiedAt")).put("local_verified_at",archive==null?"":archive.optString("localVerifiedAt")).put("byte_size",archive==null?0:archive.optLong("size")).put("encrypted",archive!=null&&!archive.optString("archiveFormat").equals("PLAIN_ZIP")).put("cloud_confirmed",false));}
        BackupSettingsRepository checkpoint=new BackupSettingsRepository(context,store);
        return new JSONObject().put("format_version",2).put("mode","PIXEL_PLAINTEXT_ZIP").put("last_local_verified_at",verified).put("last_local_captured_at",captured).put("clock_review_required",future(verified)||future(captured)).put("encrypted",false).put("settings_hash",settingsHash()).put("jobs",jobs).put("total",total).put("offset",offset).put("limit",limit).put("browser_capture_pending",checkpoint.browserCapturePending()).put("browser_checkpoint_error",checkpoint.browserError()).put("requires_google_account",false).put("requires_recovery_key",false).put("cloud_confirmed",false);
    }
    private static boolean future(String value){try{return java.time.Instant.parse(value).isAfter(java.time.Instant.now().plusSeconds(120));}catch(Exception unknown){return false;}}
    private String settingsHash()throws Exception{return PixelMcpWriteTools.hash(new JSONObject().put("mode","PIXEL_PLAINTEXT_ZIP").put("identity",new BackupDeviceGuard(context).current()));}
    JSONObject request(JSONObject args)throws Exception {if(!settingsHash().equals(args.optString("expected_settings_hash")))throw new IOException("BACKUP_SETTINGS_WRITE_CONFLICT");return new JSONObject().put("accepted_locally",false).put("code","BACKUP_OPEN_PIXEL_FILE_PICKER").put("next_action","open_backup_screen_and_choose_pixel_file").put("cloud_confirmed",false);}
    JSONObject retry(JSONObject args)throws Exception {return new JSONObject().put("accepted_locally",false).put("code","BACKUP_OPEN_PIXEL_FILE_PICKER").put("next_action","resume_existing_save_in_backup_screen").put("cloud_confirmed",false);}
}
