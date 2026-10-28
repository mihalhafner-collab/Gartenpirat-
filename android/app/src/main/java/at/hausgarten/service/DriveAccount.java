package at.hausgarten.service;

import android.accounts.*;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import java.util.concurrent.TimeUnit;

/** Google consent used exclusively by the existing encrypted full-backup workflow. */
final class DriveAccount {
    // Existing user-selected Drive files require explicit consent, not implicit app-created-file access.
    static final String SCOPE="oauth2:https://www.googleapis.com/auth/drive";
    static final long AUTH_WAIT_SECONDS=90;
    static Bundle await(AccountManagerFuture<Bundle> request,long deadline)throws Exception {
        try{long remaining=deadline-System.nanoTime();if(remaining<=0)throw new DriveRestClient.Failure("AUTH_CANCELLED_OR_TIMEOUT","Zeitlimit der Google-Freigabe erreicht.",0,true);return request.getResult(remaining,TimeUnit.NANOSECONDS);}
        catch(OperationCanceledException cancelled){throw new DriveRestClient.Failure("AUTH_CANCELLED_OR_TIMEOUT","Google-Freigabe wurde abgebrochen oder nicht rechtzeitig beantwortet.",0,true);}
        finally{if(!request.isDone())request.cancel(true);}
    }
    static String token(Context context,String account,Activity consent)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(AUTH_WAIT_SECONDS);
        AccountManager manager=AccountManager.get(context);Account selected=new Account(account,"com.google");
        Bundle result=await(consent==null?manager.getAuthToken(selected,SCOPE,null,false,null,null):manager.getAuthToken(selected,SCOPE,null,consent,null,null),deadline);
        String token=result.getString(AccountManager.KEY_AUTHTOKEN,"");if(consent!=null&&!token.isEmpty()){manager.invalidateAuthToken("com.google",token);token=await(manager.getAuthToken(selected,SCOPE,null,consent,null,null),deadline).getString(AccountManager.KEY_AUTHTOKEN,"");}if(token.isEmpty())throw new DriveRestClient.Failure("AUTH_REQUIRED","Google-Freigabe benötigt eine Bestätigung am Gerät. Drive erneut verbinden.",0,true);return token;
    }
    private DriveAccount(){}
}
