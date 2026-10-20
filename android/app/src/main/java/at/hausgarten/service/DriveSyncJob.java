package at.hausgarten.service;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** System-persisted scheduling; no always-on service or availability claim. */
public final class DriveSyncJob extends JobService {
    private static final int PERIODIC=0x484701,ONCE=0x484702;private Future<?> task;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    static void install(Context c){if(!DriveAccount.available(c))return;JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs==null)return;if(!DriveSyncCoordinator.automaticEnabled(c)){jobs.cancel(PERIODIC);return;}JobInfo old=jobs.getPendingJob(PERIODIC);if(old==null)jobs.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,DriveSyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build());}
    static void schedule(Context c,long delay){if(!DriveAccount.available(c))return;install(c);JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs!=null)jobs.schedule(new JobInfo.Builder(ONCE,new ComponentName(c,DriveSyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(Math.max(500,delay)).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    @Override public boolean onStartJob(JobParameters params){task=EXECUTOR.submit(()->{boolean retry=false;try(PixelLocalStore store=new PixelLocalStore(this)){android.content.SharedPreferences p=getSharedPreferences("haus_garten",0);if(DriveSyncCoordinator.automaticAllowed(this))DriveSyncCoordinator.run(this,store);}catch(Exception e){retry=!(e instanceof SecurityException)&&!(e instanceof IllegalArgumentException)&&!(e instanceof org.json.JSONException)&&(!(e instanceof DriveRestClient.Failure)||!((DriveRestClient.Failure)e).userAction)&&getSharedPreferences("haus_garten",0).getInt("drive_retry_count",0)<8;}finally{jobFinished(params,retry);}});return true;}
    @Override public boolean onStopJob(JobParameters params){if(task!=null)task.cancel(true);return getSharedPreferences("haus_garten",0).getInt("drive_retry_count",0)<8;}
}
