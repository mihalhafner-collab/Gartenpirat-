package at.hausgarten.service;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** Independent of the Drive executor; adaptive one-shot checks drive weather cadence, periodic work is only a persisted fallback. */
public final class PlanningJob extends JobService {
    static final int PERIODIC=0x484711,ONCE=0x484712;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();private Future<?> task;
    static void install(Context c){JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null&&scheduler.getPendingJob(PERIODIC)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,PlanningJob.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24*60*60*1000L).build());}
    static synchronized void schedule(Context c,long delay){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;
        try(PixelLocalStore store=new PixelLocalStore(c)){if(!AppointmentRuntime.calendarEnabled(c,store)&&!AppointmentRuntime.flag(c,store,"weather_enabled")){scheduler.cancel(ONCE);scheduler.cancel(PERIODIC);PlanningAccount.prefs(c).edit().remove("job_due_at").apply();return;}}catch(Exception unavailable){return;}
        install(c);long due=System.currentTimeMillis()+Math.max(1000,delay),existing=PlanningAccount.prefs(c).getLong("job_due_at",Long.MAX_VALUE);
        if(scheduler.getPendingJob(ONCE)!=null&&existing<=due&&existing>System.currentTimeMillis())return;
        int result=scheduler.schedule(new JobInfo.Builder(ONCE,new ComponentName(c,PlanningJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(1000,delay)).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        if(result==JobScheduler.RESULT_SUCCESS)PlanningAccount.prefs(c).edit().putLong("job_due_at",due).apply();
    }
    @Override public boolean onStartJob(JobParameters params){if(params.getJobId()==ONCE)PlanningAccount.prefs(this).edit().remove("job_due_at").apply();task=EXECUTOR.submit(()->{try(PixelLocalStore store=new PixelLocalStore(this)){AppointmentRuntime.refresh(this,store);}finally{jobFinished(params,false);}});return true;}
    @Override public boolean onStopJob(JobParameters params){if(task!=null)task.cancel(true);return true;}
}
