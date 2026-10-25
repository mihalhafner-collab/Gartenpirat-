package at.hausgarten.service;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** System-persisted scheduling; no always-on service or availability claim. */
public final class DriveSyncJob extends JobService {
    private static final int PERIODIC=0x484701,ONCE=0x484702;private final ConcurrentHashMap<Integer,Future<?>> tasks=new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static int activeOnce;
    private static long followAt=Long.MAX_VALUE;
    static void install(Context c){if(!DriveAccount.available(c))return;JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs==null)return;if(!DriveSyncCoordinator.automaticEnabled(c)){jobs.cancel(PERIODIC);return;}JobInfo old=jobs.getPendingJob(PERIODIC);if(old==null)jobs.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,DriveSyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build());}
    static synchronized boolean schedule(Context c,long delay){
        if(!DriveAccount.available(c))return false;install(c);
        long due=System.currentTimeMillis()+Math.max(0,delay);
        // Rescheduling a running job ID invokes onStopJob and would cancel its upload.
        if(activeOnce>0){followAt=Math.min(followAt,due);return true;}
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        return jobs!=null&&jobs.schedule(new JobInfo.Builder(ONCE,new ComponentName(c,DriveSyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(Math.max(0,delay)).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())==JobScheduler.RESULT_SUCCESS;
    }
    @Override public boolean onStartJob(JobParameters params){
        if(params.getJobId()==ONCE)synchronized(DriveSyncJob.class){activeOnce++;}
        FutureTask<Void> task=new FutureTask<>(()->{boolean retry=false;
            try(PixelLocalStore store=new PixelLocalStore(this)){
                if(DriveSyncCoordinator.automaticAllowed(this)){if(DriveSyncCoordinator.hasNetwork(this))DriveSyncCoordinator.run(this,store);else retry=true;}
            }catch(Exception e){retry=!(e instanceof SecurityException)&&!(e instanceof IllegalArgumentException)&&!(e instanceof org.json.JSONException)&&(!(e instanceof DriveRestClient.Failure)||!((DriveRestClient.Failure)e).userAction)&&getSharedPreferences("haus_garten",0).getInt("drive_retry_count",0)<8;}
            finally{jobFinished(params,retry);}
            return null;
        }){
            @Override protected void done(){
                tasks.remove(params.getJobId(),this);long deferred=Long.MAX_VALUE;
                if(params.getJobId()==ONCE)synchronized(DriveSyncJob.class){if(--activeOnce==0){deferred=followAt;followAt=Long.MAX_VALUE;}}
                // done() also runs for a queued job cancelled before it ever opens a store.
                if(deferred!=Long.MAX_VALUE)DriveSyncCoordinator.request(getApplicationContext(),Math.max(0,deferred-System.currentTimeMillis()));
            }
        };
        tasks.put(params.getJobId(),task);EXECUTOR.execute(task);return true;
    }
    @Override public boolean onStopJob(JobParameters params){Future<?> task=tasks.remove(params.getJobId());if(task!=null)task.cancel(true);return getSharedPreferences("haus_garten",0).getInt("drive_retry_count",0)<8;}
}
