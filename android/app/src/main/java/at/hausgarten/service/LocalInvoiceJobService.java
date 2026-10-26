package at.hausgarten.service;

import android.app.job.*;
import java.util.concurrent.*;

/** Deferrable local-only document work, no foreground service or network constraint. */
public final class LocalInvoiceJobService extends JobService {
    private Future<?> task;private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    @Override public boolean onStartJob(JobParameters params){if(!LocalInvoiceJobs.storageScope(this).equals(params.getExtras().getString("storage_scope","")))return false;task=EXECUTOR.submit(()->{try(PixelLocalStore store=new PixelLocalStore(this)){android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);LocalInvoiceJobs.run(this,store);}catch(Exception failure){/* Safe error codes live in durable jobs. */}finally{jobFinished(params,false);}});return true;}
    @Override public boolean onStopJob(JobParameters params){if(task!=null)task.cancel(true);return false;}
}
