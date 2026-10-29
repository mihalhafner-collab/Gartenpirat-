package at.hausgarten.service;
import android.content.Context;
import org.json.*;
import java.io.IOException;

/** Local-only metadata and explicit preparation. Saving requires the user's Android file choice. */
final class FullBackupController {
    private final Context context;private final PixelLocalStore store;
    FullBackupController(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    JSONObject status(JSONObject args)throws Exception {
        JSONObject status=PixelBackupService.statusProjection(new LocalBackupCoordinator(context,store),args);
        BackupSettingsRepository checkpoint=new BackupSettingsRepository(context,store);
        return status.put("format_version",2).put("mode","PIXEL_PLAINTEXT_ZIP").put("encrypted",false).put("settings_hash",settingsHash())
            .put("browser_capture_pending",checkpoint.browserCapturePending()).put("browser_checkpoint_error",checkpoint.browserError())
            .put("requires_google_account",false).put("requires_recovery_key",false).put("cloud_confirmed",false);
    }
    private String settingsHash()throws Exception{return PixelMcpWriteTools.hash(new JSONObject().put("mode","PIXEL_PLAINTEXT_ZIP").put("identity",new BackupDeviceGuard(context).current()));}
    JSONObject request(JSONObject args)throws Exception {if(!settingsHash().equals(args.optString("expected_settings_hash")))throw new IOException("BACKUP_SETTINGS_WRITE_CONFLICT");return new JSONObject().put("accepted_locally",false).put("code","BACKUP_OPEN_PIXEL_FILE_PICKER").put("next_action","open_backup_screen_and_choose_pixel_file").put("cloud_confirmed",false);}
    JSONObject retry(JSONObject args)throws Exception {return new JSONObject().put("accepted_locally",false).put("code","BACKUP_OPEN_PIXEL_FILE_PICKER").put("next_action","resume_existing_save_in_backup_screen").put("cloud_confirmed",false);}
}
