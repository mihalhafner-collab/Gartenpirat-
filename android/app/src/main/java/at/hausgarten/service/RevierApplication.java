package at.hausgarten.service;

import android.app.Application;
import android.content.Context;

/** Cold-start recovery precedes ContentProvider creation and any database/adapter opening. */
public final class RevierApplication extends Application {
    private static volatile boolean recoveryRequired;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(base);try{
        new LocalAuthorityGuard(this).recoverInterruptedMigration();
        if(BackupRestoreService.requiresExclusiveStartup(this))BackupRestoreService.activatePending(this,new RestoreStartupProtection(this));
        recoveryRequired=BackupRestoreService.requiresExclusiveStartup(this)||RestoreStartupProtection.requiresColdRestart(this);
    }catch(Exception failure){recoveryRequired=true;}}
    @Override public void onCreate(){super.onCreate();DriveSyncJob.install(this);}
    static boolean recoveryRequired(){return recoveryRequired;}
}
