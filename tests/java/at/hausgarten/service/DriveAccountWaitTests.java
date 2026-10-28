package at.hausgarten.service;
import android.accounts.AccountManagerFuture;
import android.os.Bundle;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
public final class DriveAccountWaitTests {
    static int checks;static void yes(boolean v){checks++;if(!v)throw new AssertionError("auth wait "+checks);}
    static final class Pending implements AccountManagerFuture<Bundle> {
        boolean done,cancelled,fail;int timed,unbounded;long timeout;
        public boolean cancel(boolean interrupt){cancelled=interrupt;return true;}
        public boolean isCancelled(){return cancelled;}public boolean isDone(){return done;}
        public Bundle getResult(){unbounded++;throw new AssertionError("unbounded auth wait");}
        public Bundle getResult(long duration,TimeUnit unit)throws IOException {timed++;timeout=unit.toNanos(duration);if(fail)throw new IOException("QA provider unavailable");done=true;return null;}
    }
    public static void main(String[] args)throws Exception {
        long budget=TimeUnit.SECONDS.toNanos(90);Pending success=new Pending();RetiredDriveAccount.await(success,System.nanoTime()+budget);yes(success.timed==1&&success.unbounded==0&&success.timeout>0&&success.timeout<=budget&&!success.cancelled);
        Pending unavailable=new Pending();unavailable.fail=true;try{RetiredDriveAccount.await(unavailable,System.nanoTime()+budget);throw new AssertionError();}catch(IOException expected){yes(unavailable.cancelled&&unavailable.timed==1&&unavailable.unbounded==0);}
        Pending expired=new Pending();try{RetiredDriveAccount.await(expired,System.nanoTime()-1);throw new AssertionError();}catch(RetiredBackupDriveClient.Failure expected){yes(expected.code.equals("AUTH_CANCELLED_OR_TIMEOUT")&&expected.userAction&&expired.timed==0&&expired.cancelled);}
        Pending second=new Pending();RetiredDriveAccount.await(second,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(100));yes(second.timeout<=TimeUnit.MILLISECONDS.toNanos(100)&&second.timeout>0);yes(RetiredDriveAccount.AUTH_WAIT_SECONDS==90);
        System.out.println("PASS "+checks+" auth wait checks; success, provider failure, expired shared deadline, remaining budget and cancellation; no account access");
    }
}
