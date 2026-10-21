package at.hausgarten.service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Independent file I/O only. Caller joins every worker before any journal/receipt mutation. */
final class DriveTransferPool {
    static final int WORKERS=3;
    static final class Result<T>{
        final T value;final Exception error;
        Result(T value,Exception error){this.value=value;this.error=error;}
    }
    static <T> List<Result<T>> run(List<Callable<T>> tasks,Runnable cancelActive)throws Exception{
        if(tasks.isEmpty())return Collections.emptyList();
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(WORKERS,tasks.size()),r->{Thread t=new Thread(r,"drive-file-transfer");t.setDaemon(true);return t;});
        List<Future<Result<T>>> futures=new ArrayList<>();boolean interrupted=false;
        try{
            for(Callable<T> task:tasks)futures.add(pool.submit(()->{try{return new Result<>(task.call(),null);}catch(Exception error){return new Result<>(null,error);}}));
            List<Result<T>> results=new ArrayList<>();
            for(Future<Result<T>> future:futures)results.add(future.get());
            return results; // Stable input order, even when completion order differs.
        }catch(InterruptedException stopped){interrupted=true;throw new InterruptedIOException("Dateiübertragung unterbrochen; keine Veröffentlichung bestätigt.");}
        catch(ExecutionException failed){if(failed.getCause() instanceof Error)throw (Error)failed.getCause();throw new IOException("Dateiübertragung abgebrochen.",failed.getCause());}
        finally{
            for(Future<?> future:futures)if(!future.isDone())future.cancel(true);
            pool.shutdownNow();cancel(cancelActive);
            // No worker may keep using a closing SQLite store or writing after the caller returns.
            boolean restore=interrupted||Thread.interrupted();
            while(!pool.isTerminated())try{pool.awaitTermination(250,TimeUnit.MILLISECONDS);}catch(InterruptedException stopped){restore=true;cancel(cancelActive);}
            if(restore)Thread.currentThread().interrupt();
        }
    }
    private static void cancel(Runnable action){try{action.run();}catch(RuntimeException ignored){/* Still join every worker; socket timeouts remain in force. */}}
    private DriveTransferPool(){}
}
