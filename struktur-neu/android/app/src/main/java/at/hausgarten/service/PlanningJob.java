package at.hausgarten.service;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** Independent of the Drive executor; adaptive one-shot checks drive weather cadence, periodic work is only a persisted fallback. */
public final class PlanningJob extends JobService {
    static final int PERIODIC=0x484711,ONCE=0x484712;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();private final ConcurrentHashMap<Integer,Future<?>> tasks=new ConcurrentHashMap<>();
    private static int activeOnce;private static long followAt=Long.MAX_VALUE;
    static void install(Context c){JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null&&scheduler.getPendingJob(PERIODIC)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,PlanningJob.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24*60*60*1000L).build());}
    static synchronized void schedule(Context c,long delay){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;
        try(PixelLocalStore store=new PixelLocalStore(c)){if(!AppointmentRuntime.calendarEnabled(c,store)&&!AppointmentRuntime.flag(c,store,"weather_enabled")){scheduler.cancel(ONCE);scheduler.cancel(PERIODIC);PlanningAccount.prefs(c).edit().remove("job_due_at").apply();return;}}catch(Exception unavailable){return;}
        install(c);long due=System.currentTimeMillis()+Math.max(1000,delay),existing=PlanningAccount.prefs(c).getLong("job_due_at",Long.MAX_VALUE);
        // Replacing a running job ID asks Android to interrupt that same calendar check.
        if(activeOnce>0){followAt=Math.min(followAt,due);return;}
        if(scheduler.getPendingJob(ONCE)!=null&&existing<=due&&existing>System.currentTimeMillis())return;
        int result=scheduler.schedule(new JobInfo.Builder(ONCE,new ComponentName(c,PlanningJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(1000,delay)).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        if(result==JobScheduler.RESULT_SUCCESS)PlanningAccount.prefs(c).edit().putLong("job_due_at",due).apply();
    }
    @Override public boolean onStartJob(JobParameters params){
        if(params.getJobId()==ONCE){PlanningAccount.prefs(this).edit().remove("job_due_at").apply();synchronized(PlanningJob.class){activeOnce++;}}
        java.util.concurrent.atomic.AtomicBoolean retry=new java.util.concurrent.atomic.AtomicBoolean();
        FutureTask<Void> task=new FutureTask<>(()->{try(PixelLocalStore store=new PixelLocalStore(this)){if(DriveSyncCoordinator.hasNetwork(this))AppointmentRuntime.refresh(this,store);else retry.set(true);}catch(RuntimeException unavailable){retry.set(true);}return null;}){
            @Override protected void done(){
                tasks.remove(params.getJobId(),this);long next=Long.MAX_VALUE;
                if(params.getJobId()==ONCE)synchronized(PlanningJob.class){if(--activeOnce==0){next=followAt;followAt=Long.MAX_VALUE;}}
                if(!isCancelled())jobFinished(params,retry.get());
                if(next!=Long.MAX_VALUE)schedule(getApplicationContext(),Math.max(1000,next-System.currentTimeMillis()));
            }
        };
        tasks.put(params.getJobId(),task);EXECUTOR.execute(task);return true;
    }
    @Override public boolean onStopJob(JobParameters params){Future<?> task=tasks.remove(params.getJobId());if(task!=null)task.cancel(true);return true;}
}
