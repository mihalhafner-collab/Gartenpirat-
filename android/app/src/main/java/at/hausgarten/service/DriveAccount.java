package at.hausgarten.service;

import android.accounts.*;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;

/** Google consent used exclusively by the existing encrypted full-backup workflow. */
final class DriveAccount {
    // Existing user-selected Drive files require explicit consent, not implicit app-created-file access.
    static final String SCOPE="oauth2:https://www.googleapis.com/auth/drive";
    static String token(Context context,String account,Activity consent)throws Exception{
        AccountManager manager=AccountManager.get(context);Account selected=new Account(account,"com.google");
        Bundle result=consent==null?manager.getAuthToken(selected,SCOPE,null,false,null,null).getResult():manager.getAuthToken(selected,SCOPE,null,consent,null,null).getResult();
        String token=result.getString(AccountManager.KEY_AUTHTOKEN,"");if(consent!=null&&!token.isEmpty()){manager.invalidateAuthToken("com.google",token);token=manager.getAuthToken(selected,SCOPE,null,consent,null,null).getResult().getString(AccountManager.KEY_AUTHTOKEN,"");}if(token.isEmpty())throw new DriveRestClient.Failure("AUTH_REQUIRED","Google-Freigabe benötigt eine Bestätigung am Gerät. Drive erneut verbinden.",0,true);return token;
    }
    private DriveAccount(){}
}
