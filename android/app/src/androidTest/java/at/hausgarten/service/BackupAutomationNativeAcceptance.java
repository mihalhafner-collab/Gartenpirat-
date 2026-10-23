package at.hausgarten.service;

import android.app.job.JobInfo;
import org.json.*;
import java.io.*;

/** Real SQLite/Keystore plus shared backup runner, never a Google account or device setting. */
final class BackupAutomationNativeAcceptance {
    private static void denied(PixelMcpInstrumentation.Task action)throws Exception {try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected backup setup refusal");}
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("backup native settings require explicit recovery setup and retain atomic CAS replay across reopen and restore context",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);try{
                BackupSettingsRepository settings=new BackupSettingsRepository(f.context,f.store);JSONObject before=settings.read();test.yes(!before.getBoolean("enabled")&&!before.getBoolean("contextMatches")&&!before.getBoolean("keyConfigured")&&before.getBoolean("unmeteredOnly"));
                denied(()->settings.configure("qa_auto_no_key",before.getString("revision"),true,"qa_backup@example.invalid","qa_service_folder_123",true));test.yes(!settings.read().getBoolean("enabled"));
                String code=BackupCrypto.newRecoveryCode();keys.confirmAndStore("qa_auto_key_setup",code,code);String revision=settings.read().getString("revision");
                f.store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER qa_backup_config_abort BEFORE INSERT ON owner_state WHEN NEW.key LIKE 'full_backup_settings_request:%' BEGIN SELECT RAISE(ABORT,'qa atomic config failure'); END");
                denied(()->settings.configure("qa_auto_atomic_config",revision,true,"qa_backup@example.invalid","qa_service_folder_123",true));test.yes(settings.read().getString("revision").equals(revision));f.store.getWritableDatabase().execSQL("DROP TRIGGER qa_backup_config_abort");
                JSONObject configured=settings.configure("qa_auto_atomic_config",revision,true,"qa_backup@example.invalid","qa_service_folder_123",true);test.yes(configured.getBoolean("enabled")&&configured.getBoolean("contextMatches")&&!configured.getBoolean("replayed"));
                test.yes(settings.configure("qa_auto_atomic_config",revision,true,"qa_backup@example.invalid","qa_service_folder_123",true).getBoolean("replayed"));
                denied(()->settings.configure("qa_auto_atomic_config",revision,true,"qa_changed@example.invalid","qa_service_folder_123",true));denied(()->settings.configure("qa_auto_stale_config",revision,false,"qa_backup@example.invalid","qa_service_folder_123",true));
                JSONObject browser=new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_draft","QA pending draft"));String br=settings.browserRevision();settings.saveBrowser("qa_auto_browser_save",br,browser);test.yes(settings.saveBrowser("qa_auto_browser_save",br,browser).getBoolean("replayed")&&settings.browser().getJSONObject("browser").getJSONObject("storage").getString("qa_draft").equals("QA pending draft"));
                denied(()->settings.saveBrowser("qa_auto_browser_changed",br,new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_draft","QA changed"))));
                denied(()->settings.saveBrowser("qa_auto_browser_invalid",settings.browserRevision(),new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_number",1))));
                f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){
                    BackupSettingsRepository saved=new BackupSettingsRepository(f.context,reopened);test.yes(saved.read().getBoolean("enabled")&&saved.browser().getJSONObject("browser").getJSONObject("storage").has("qa_draft"));BackupDeviceGuard guard=new BackupDeviceGuard(f.context);JSONObject identity=guard.current();
                    JSONObject newer=new JSONObject().put("formatVersion",2).put("backupId","qa_newer_job").put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch")).put("createdAt",java.time.Instant.now().toString()).put("phase","qa_newer_phase").put("destination",new JSONObject().put("kind","drive"));reopened.putOwnerValue("backup_job:qa_newer_job",newer.toString());test.yes(new BackupAutomation(f.context,reopened).decision(System.currentTimeMillis()).getString("action").equals("JOB_REVIEW"));
                    guard.advanceForRestore("qa_auto_restore_epoch",guard.current().getString("restoreEpoch"));test.yes(!saved.read().getBoolean("contextMatches"));denied(()->saved.browser());test.yes(new BackupAutomation(f.context,reopened).decision(System.currentTimeMillis()).getString("action").equals("DISABLED"));
                }
            }finally{BackupCoreNativeAcceptance.deleteQaKey(f,keys);}
        }});
        test.probe("backup native automatic runner resumes lost upload once with bounded retry and uses data age not new verification time",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);try{
                String code=BackupCrypto.newRecoveryCode();keys.confirmAndStore("qa_runner_key_setup",code,code);BackupSettingsRepository settings=new BackupSettingsRepository(f.context,f.store);settings.configure("qa_runner_settings",settings.read().getString("revision"),true,"qa_backup@example.invalid","qa_service_folder_123",true);settings.saveBrowser("qa_runner_browser",settings.browserRevision(),new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_draft","QA active editor")));
                BackupServiceNativeAcceptance.Server server=new BackupServiceNativeAcceptance.Server(new File(f.context.getCacheDir(),"qa_automatic_remote.hgb"));BackupApplicationService service=new BackupApplicationService(f.context,f.store,server,account->"qa_backup_token");BackupAutomation runner=new BackupAutomation(f.context,f.store,service,()->"qa_automatic_backup");long now=System.currentTimeMillis();String business=PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master"));
                JSONObject first=runner.run(now);test.yes(first.getString("action").equals("WAIT")&&first.getLong("nextAttemptAt")>=now+30000&&server.completed==1);String id=first.getString("backupId");test.yes(service.status(id).getInt("attempts")==1&&runner.run(now+1000).getString("action").equals("WAIT"));
                f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){BackupApplicationService resumed=new BackupApplicationService(f.context,reopened,server,account->"qa_backup_token");BackupAutomation next=new BackupAutomation(f.context,reopened,resumed);JSONObject completed=next.run(first.getLong("nextAttemptAt"));test.yes(completed.getString("action").equals("COMPLETED")&&completed.getString("backupId").equals(id)&&server.starts==1&&server.completed==1&&server.readbacks==1);JSONObject job=resumed.status(id);test.yes(job.getInt("attempts")==2&&job.getJSONObject("archive").has("capturedAt"));test.yes(next.decision(now+60000).getString("action").equals("WAIT"));test.yes(next.decision(now+2*BackupScheduleRules.INTERVAL_MS).getString("action").equals("CREATE"));test.yes(PixelMcpWriteTools.hash(reopened.getState().getJSONObject("master")).equals(business));}
            }finally{BackupCoreNativeAcceptance.deleteQaKey(f,keys);}
        }});
        test.probe("backup native scheduler specifications are daily persisted and network constrained without exact deadline",()->{
            JobInfo daily=FullBackupJob.specification(test.getTargetContext(),true,true,0),once=FullBackupJob.specification(test.getTargetContext(),false,false,60000);
            // Actual API30 reports zero for an unset override deadline (standalone native probe).
            test.yes(daily.isPeriodic()&&daily.isPersisted()&&daily.getIntervalMillis()==BackupScheduleRules.INTERVAL_MS&&daily.getNetworkType()==JobInfo.NETWORK_TYPE_UNMETERED&&daily.getMaxExecutionDelayMillis()==0);
            test.yes(!once.isPeriodic()&&once.isPersisted()&&once.getMinLatencyMillis()==60000&&once.getNetworkType()==JobInfo.NETWORK_TYPE_ANY&&once.getMaxExecutionDelayMillis()==0);
        });
    }
}
