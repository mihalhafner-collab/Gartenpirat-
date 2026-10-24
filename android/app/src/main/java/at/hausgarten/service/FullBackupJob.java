package at.hausgarten.service;

import android.app.job.*;
import android.content.*;
import android.os.PersistableBundle;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Android owns timing. Local preparation has no network constraint; uploads are optional jobs. */
public final class FullBackupJob extends JobService {
    static final int PERIODIC=0x484705,ONCE=0x484706,LOCAL=0x484707;
    private static final Object SCHEDULE_LOCK=new Object();
    private static final Set<Integer> ACTIVE=new HashSet<>();
    private static final Map<Integer,Long> FOLLOW_AT=new HashMap<>();
    private static final ExecutorService LOCAL_EXECUTOR=Executors.newSingleThreadExecutor(),UPLOAD_EXECUTOR=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer,Run> tasks=new ConcurrentHashMap<>();
    private static final class Run {
        final JobParameters parameters;final AtomicInteger state=new AtomicInteger();volatile Future<?> future;volatile boolean stopped;
        Run(JobParameters parameters){this.parameters=parameters;}
    }
    static JobInfo specification(Context context,boolean periodic,boolean unmetered,long delay){
        JobInfo.Builder builder=new JobInfo.Builder(periodic?PERIODIC:ONCE,new ComponentName(context,FullBackupJob.class)).setRequiredNetworkType(periodic?JobInfo.NETWORK_TYPE_NONE:unmetered?JobInfo.NETWORK_TYPE_UNMETERED:JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setExtras(scopeExtras(context));
        if(periodic)builder.setPeriodic(BackupScheduleRules.INTERVAL_MS);else builder.setMinimumLatency(Math.max(0,delay)).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL);return builder.build();
    }
    static JobInfo localSpecification(Context context,long delay){return new JobInfo.Builder(LOCAL,new ComponentName(context,FullBackupJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE).setPersisted(true).setMinimumLatency(Math.max(0,delay)).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setExtras(scopeExtras(context)).build();}
    private static PersistableBundle scopeExtras(Context context){PersistableBundle extras=new PersistableBundle();extras.putString("storage_scope",storageScope(context));return extras;}
    private static String storageScope(Context context){try{return PixelMcpWriteTools.hash(context.getFilesDir().getCanonicalPath());}catch(Exception unavailable){throw new IllegalStateException("BACKUP_STORAGE_SCOPE_UNAVAILABLE");}}
    static boolean matchesStorageScope(Context context,PersistableBundle extras){return extras!=null&&storageScope(context).equals(extras.getString("storage_scope",""));}

    /** Safe at app startup; restores only already opted-in/persisted work in the current epoch. */
    static boolean install(Context context)throws Exception {
        if(BuildConfig.UI_PREVIEW)return false;
        try(PixelLocalStore store=new PixelLocalStore(context)){
            JSONObject settings=new BackupSettingsRepository(context,store).read();JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null)return false;
            boolean ready=settings.optBoolean("contextMatches")&&settings.optBoolean("keyConfigured")&&FullBackupController.validTarget(settings);
            if(!ready){scheduler.cancel(PERIODIC);scheduler.cancel(ONCE);scheduler.cancel(LOCAL);return false;}
            JobInfo previous=scheduler.getPendingJob(PERIODIC);boolean installedNew=false;
            if(settings.optBoolean("enabled")){
                if(previous==null||previous.getNetworkType()!=JobInfo.NETWORK_TYPE_NONE||!matchesStorageScope(context,previous.getExtras())){if(scheduler.schedule(specification(context,true,settings.optBoolean("unmeteredOnly",true),0))!=JobScheduler.RESULT_SUCCESS)return false;installedNew=true;}
            }else scheduler.cancel(PERIODIC);
            JSONObject pending=new BackupAutomation(context,store).pending(System.currentTimeMillis());
            if(pending.optBoolean("has_local"))scheduleId(context,LOCAL,pending.optLong("next_local_at"),settings.optBoolean("unmeteredOnly",true));
            else if(installedNew)scheduleId(context,LOCAL,System.currentTimeMillis(),settings.optBoolean("unmeteredOnly",true));
            if(pending.optBoolean("has_upload"))scheduleId(context,ONCE,pending.optLong("next_upload_at"),settings.optBoolean("unmeteredOnly",true));
            return true;
        }
    }
    /** Must be called after the controller/owner transaction commits. */
    static boolean schedule(Context context,long delay)throws Exception {
        if(!install(context))return false;try(PixelLocalStore store=new PixelLocalStore(context)){
            JSONObject settings=new BackupSettingsRepository(context,store).read();long now=System.currentTimeMillis(),at=delay>Long.MAX_VALUE-now?Long.MAX_VALUE:now+Math.max(0,delay);
            boolean local=scheduleId(context,LOCAL,at,settings.optBoolean("unmeteredOnly",true));boolean upload=scheduleId(context,ONCE,at,settings.optBoolean("unmeteredOnly",true));return local&&upload;
        }
    }
    private static boolean scheduleId(Context context,int id,long due,boolean unmetered){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null||due==Long.MAX_VALUE)return false;
        synchronized(SCHEDULE_LOCK){
            if(ACTIVE.contains(id)){FOLLOW_AT.merge(id,due,Math::min);return true;}
            JobInfo previous=scheduler.getPendingJob(id);int network=id==LOCAL?JobInfo.NETWORK_TYPE_NONE:unmetered?JobInfo.NETWORK_TYPE_UNMETERED:JobInfo.NETWORK_TYPE_ANY;
            if(previous!=null&&previous.getNetworkType()==network&&matchesStorageScope(context,previous.getExtras())&&previous.getExtras().getLong("dueAt",Long.MAX_VALUE)<=due)return true;
            long delay=Math.max(0,due-System.currentTimeMillis());PersistableBundle extras=scopeExtras(context);extras.putLong("dueAt",due);
            JobInfo.Builder builder=new JobInfo.Builder(id,new ComponentName(context,FullBackupJob.class)).setRequiredNetworkType(network).setPersisted(true).setMinimumLatency(delay).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setExtras(extras);
            return scheduler.schedule(builder.build())==JobScheduler.RESULT_SUCCESS;
        }
    }
    @Override public boolean onStartJob(JobParameters parameters){
        // A fixture/foreign context may share the Android package, never its private data root.
        try{if(!matchesStorageScope(this,parameters.getExtras()))return false;}catch(RuntimeException unavailable){return false;}
        int id=parameters.getJobId();Run run=new Run(parameters);
        synchronized(SCHEDULE_LOCK){if(ACTIVE.contains(id)){FOLLOW_AT.merge(id,System.currentTimeMillis()+30000,Math::min);return false;}ACTIVE.add(id);tasks.put(id,run);}
        Runnable work=()->{
            if(!run.state.compareAndSet(0,1))return;
            JSONObject result=null;
            try(PixelLocalStore store=new PixelLocalStore(this)){
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);BackupAutomation automation=new BackupAutomation(this,store);result=id==ONCE?automation.runUpload(System.currentTimeMillis()):automation.runLocal(System.currentTimeMillis());
                if(!run.stopped){String action=result.optString("action"),code=result.optString("lastErrorCode");boolean review=action.equals("JOB_REVIEW")||action.equals("CLOCK_REVIEW");if(code.isEmpty()&&review)code=action.equals("CLOCK_REVIEW")?"BACKUP_CLOCK_REVIEW_REQUIRED":"BACKUP_JOB_REVIEW_REQUIRED";store.putOwnerValue("full_backup_scheduler_status",new JSONObject().put("at",java.time.Instant.now().toString()).put("code",code).put("needsReview",review).toString());}
            }catch(Exception failure){if(!run.stopped)recordFailure(failure);}
            finally{run.state.set(2);finish(run,result);}
        };
        try{run.future=(id==ONCE?UPLOAD_EXECUTOR:LOCAL_EXECUTOR).submit(work);if(run.stopped){if(run.state.compareAndSet(0,2)){run.future.cancel(true);finish(run,null);}else run.future.cancel(true);}return true;}
        catch(RuntimeException rejected){run.state.set(2);recordFailure(rejected);run.stopped=true;finish(run,null);return false;}
    }
    @Override public boolean onStopJob(JobParameters parameters){
        Run run=tasks.get(parameters.getJobId());if(run==null)return false;run.stopped=true;
        if(run.state.compareAndSet(0,2)){if(run.future!=null)run.future.cancel(true);finish(run,null);}else if(run.future!=null)run.future.cancel(true);
        // A stopped worker keeps its slot until its actual finally block; no concurrent writer is launched.
        try(PixelLocalStore store=new PixelLocalStore(this)){JSONObject pending=new BackupAutomation(this,store).pending(System.currentTimeMillis());return pending.optBoolean(parameters.getJobId()==ONCE?"has_upload":"has_local");}catch(Exception unavailable){return false;}
    }
    private void finish(Run run,JSONObject result){
        int id=run.parameters.getJobId();long follow;
        synchronized(SCHEDULE_LOCK){if(!tasks.remove(id,run))return;ACTIVE.remove(id);follow=FOLLOW_AT.containsKey(id)?FOLLOW_AT.remove(id):Long.MAX_VALUE;}
        if(!run.stopped)jobFinished(run.parameters,false);
        try{
            if(follow!=Long.MAX_VALUE)scheduleStage(id==ONCE?ONCE:LOCAL,follow);
            if(run.stopped||result==null)return;
            String action=result.optString("action");long now=System.currentTimeMillis(),due=result.optLong("nextAttemptAt",0);
            if(action.equals("PREPARED")||action.equals("UPLOAD_READY")){scheduleStage(ONCE,due>0?due:now);if(result.optBoolean("more_local"))scheduleStage(LOCAL,now);}
            else if(action.equals("COMPLETED")){if(result.optBoolean("more_uploads"))scheduleStage(ONCE,now);scheduleStage(LOCAL,now);}
            else if(action.equals("LOCAL_PENDING"))scheduleStage(LOCAL,due);
            else if(action.equals("BUSY"))scheduleStage(id==ONCE?ONCE:LOCAL,now+30000);
            else if(action.equals("WAIT")&&due>0&&due!=Long.MAX_VALUE)scheduleStage(result.optString("stage").equals("upload")?ONCE:LOCAL,due);
        }catch(Exception failure){if(!run.stopped)recordFailure(failure);}
    }
    private void scheduleStage(int id,long due)throws Exception {
        try(PixelLocalStore store=new PixelLocalStore(this)){
            JSONObject settings=new BackupSettingsRepository(this,store).read();if(!settings.optBoolean("contextMatches")||!settings.optBoolean("keyConfigured")||!FullBackupController.validTarget(settings))return;
            if(!scheduleId(this,id,due,settings.optBoolean("unmeteredOnly",true)))throw new java.io.IOException("BACKUP_SCHEDULER_UNAVAILABLE");
        }
    }
    private void recordFailure(Exception failure){
        try(PixelLocalStore store=new PixelLocalStore(this)){store.putOwnerValue("full_backup_scheduler_status",new JSONObject().put("at",java.time.Instant.now().toString()).put("code",BackupAutomation.safeCode(failure)).put("needsReview",true).toString());}catch(Exception ignored){/* Existing proof remains intact; never put raw exception text in logs. */}
    }
}
