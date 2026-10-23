package at.hausgarten.service;

import android.app.job.*;
import android.content.*;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Daily deferrable backup only. Android supplies the network and execution window. */
public final class FullBackupJob extends JobService {
    static final int PERIODIC=0x484705,ONCE=0x484706;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer,Future<?>> tasks=new ConcurrentHashMap<>();
    static JobInfo specification(Context context,boolean periodic,boolean unmetered,long delay){
        JobInfo.Builder builder=new JobInfo.Builder(periodic?PERIODIC:ONCE,new ComponentName(context,FullBackupJob.class)).setRequiredNetworkType(unmetered?JobInfo.NETWORK_TYPE_UNMETERED:JobInfo.NETWORK_TYPE_ANY).setPersisted(true);
        if(periodic)builder.setPeriodic(BackupScheduleRules.INTERVAL_MS);else builder.setMinimumLatency(Math.max(0,delay)).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL);return builder.build();
    }
    static boolean install(Context context)throws Exception {
        if(BuildConfig.UI_PREVIEW)return false;try(PixelLocalStore store=new PixelLocalStore(context)){JSONObject settings=new BackupSettingsRepository(context,store).read();JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null)return false;
            if(!settings.optBoolean("enabled")||!settings.optBoolean("contextMatches")||!settings.optBoolean("keyConfigured")){scheduler.cancel(PERIODIC);scheduler.cancel(ONCE);return false;}
            int network=settings.optBoolean("unmeteredOnly",true)?JobInfo.NETWORK_TYPE_UNMETERED:JobInfo.NETWORK_TYPE_ANY;JobInfo existing=scheduler.getPendingJob(PERIODIC);
            if(existing!=null&&existing.getNetworkType()==network)return true;
            return scheduler.schedule(specification(context,true,settings.optBoolean("unmeteredOnly",true),0))==JobScheduler.RESULT_SUCCESS;
        }
    }
    static boolean schedule(Context context,long delay)throws Exception {
        if(!install(context))return false;try(PixelLocalStore store=new PixelLocalStore(context)){JSONObject settings=new BackupSettingsRepository(context,store).read();return context.getSystemService(JobScheduler.class).schedule(specification(context,false,settings.optBoolean("unmeteredOnly",true),delay))==JobScheduler.RESULT_SUCCESS;}
    }
    @Override public boolean onStartJob(JobParameters parameters){
        FutureTask<Void> task=new FutureTask<>(()->{
            JSONObject result=null;try(PixelLocalStore store=new PixelLocalStore(this)){result=new BackupAutomation(this,store).run(System.currentTimeMillis());}
            catch(Exception failure){
                // Setup/Keystore failures can precede creation of a generation. Keep a safe status.
                try(PixelLocalStore store=new PixelLocalStore(this)){store.putOwnerValue("full_backup_scheduler_status",new JSONObject().put("at",java.time.Instant.now().toString()).put("code",SyncFailureRules.failureCode(failure)).put("needsReview",true).toString());}catch(Exception ignored){}
            }
            finally{jobFinished(parameters,false);}
            if(!Thread.currentThread().isInterrupted()&&result!=null&&result.optString("action").equals("WAIT")){
                long due=result.optLong("nextAttemptAt",0);if(due>0&&due!=Long.MAX_VALUE)try{schedule(this,Math.max(0,due-System.currentTimeMillis()));}catch(Exception ignored){}
            }
            return null;
        }){@Override protected void done(){tasks.remove(parameters.getJobId(),this);}};
        tasks.put(parameters.getJobId(),task);EXECUTOR.execute(task);return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){
        Future<?> task=tasks.remove(parameters.getJobId());if(task!=null)task.cancel(true);
        try(PixelLocalStore store=new PixelLocalStore(this)){String action=new BackupAutomation(this,store).decision(System.currentTimeMillis()).getString("action");return action.equals("RESUME")||action.equals("WAIT");}catch(Exception unavailable){return false;}
    }
}
