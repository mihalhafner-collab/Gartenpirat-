package at.hausgarten.service;

import android.app.job.JobScheduler;
import android.content.Context;

/** Cancels the stable pre-transition job IDs. No job service or retry is registered. */
final class DriveSyncJob {
    static void install(Context context){
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        if(jobs!=null){jobs.cancel(0x484701);jobs.cancel(0x484702);}
    }
    static boolean schedule(Context context,long delay){install(context);return false;}
    private DriveSyncJob(){}
}
