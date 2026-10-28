package at.hausgarten.service;

/** The posted callback owns scheduling failures; a committed business write stays committed. */
final class BackgroundWorkDispatch {
    interface Host {
        void state(boolean pending,String code);
        boolean post(Runnable task);
        void prepare()throws Exception;
        boolean localReady()throws Exception;
        boolean local(long delay)throws Exception;
    }
    static void request(long delay,Host host){
        host.state(true,"");
        Runnable task=()->{try{
            boolean accepted;
            host.prepare();
                if(!host.localReady()){host.state(true,"BACKUP_SETUP_REQUIRED");return;}
                accepted=host.local(Math.max(0,delay));
            host.state(!accepted,accepted?"":"BACKGROUND_SCHEDULER_UNAVAILABLE");
        }catch(Exception failure){host.state(true,"BACKGROUND_SCHEDULING_FAILED");}};
        try{if(!host.post(task))host.state(true,"BACKGROUND_HANDLER_UNAVAILABLE");}
        catch(RuntimeException failure){host.state(true,"BACKGROUND_HANDLER_UNAVAILABLE");}
    }
    private BackgroundWorkDispatch(){}
}
