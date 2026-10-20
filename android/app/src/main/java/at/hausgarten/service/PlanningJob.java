package at.hausgarten.service;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** Independent of the Drive executor; adaptive one-shot checks drive weather cadence, daily periodic is only a persisted fallback. */
public final class PlanningJob extends JobService {
    static final int PERIODIC=0x484711,ONCE=0x484712;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();private Future<?> task;
    static void install(Context c){JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null&&scheduler.getPendingJob(PERIODIC)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,PlanningJob.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24*60*60*1000L).build());}
    static void schedule(Context c,long delay){try(PixelLocalStore store=new PixelLocalStore(c)){if(!AppointmentRuntime.calendarEnabled(c,store)&&!AppointmentRuntime.flag(c,store,"weather_enabled"))return;}catch(Exception unavailable){return;}install(c);JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null)scheduler.schedule(new JobInfo.Builder(ONCE,new ComponentName(c,PlanningJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(1000,delay)).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    @Override public boolean onStartJob(JobParameters params){task=EXECUTOR.submit(()->{try(PixelLocalStore store=new PixelLocalStore(this)){AppointmentRuntime.refresh(this,store);}finally{jobFinished(params,false);}});return true;}
    @Override public boolean onStopJob(JobParameters params){if(task!=null)task.cancel(true);return true;}
}
