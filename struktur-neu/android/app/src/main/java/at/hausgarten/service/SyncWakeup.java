package at.hausgarten.service;

/** Transport scheduling only: earliest request wins, safety deadlines may move it later. */
final class SyncWakeup {
    static final long ACTIVE_POLL_MS=15000,FULL_CHECK_MS=15*60*1000;
    static final class Window {
        private long due=Long.MAX_VALUE;
        synchronized long request(long now,long delay,long notBefore){
            long wanted=Math.max(now+Math.max(0,delay),notBefore);
            if(due==Long.MAX_VALUE||wanted<due||due<notBefore)due=wanted;
            return due;
        }
        synchronized long due(){return due;}
        synchronized void clear(){due=Long.MAX_VALUE;}
    }
    static boolean fullCheck(String current,String previous,boolean pending,boolean forced,boolean failed,long elapsed){
        return pending||forced||failed||current.isEmpty()||!current.equals(previous)||elapsed>=FULL_CHECK_MS;
    }
    private SyncWakeup(){}
}
