package at.hausgarten.service;

import android.accounts.Account;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.GoogleApiAvailabilityLight;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Explicit consent continuation; background use never launches a Google UI. */
final class CalendarAuthorization {
    static final class ConsentRequired extends Exception {
        final PendingIntent resolution;
        ConsentRequired(PendingIntent resolution){super(CalendarAccessRules.message("CALENDAR_AUTH_REQUIRED"));this.resolution=resolution;}
    }
    static AuthorizationRequest request(String account,boolean busy)throws DriveRestClient.Failure {
        if(account==null||account.trim().isEmpty())throw failure("CALENDAR_ACCOUNT_ACCESS_REQUIRED");
        List<Scope> scopes=new ArrayList<>();for(String value:CalendarAccessRules.requestedScopes(busy))scopes.add(new Scope(value));
        return AuthorizationRequest.builder().setAccount(new Account(account,"com.google")).setRequestedScopes(scopes).build();
    }
    static String token(Context context,String account,boolean busy)throws Exception {
        try{
            if(GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context)!=0)throw failure("CALENDAR_GOOGLE_SERVICES_REQUIRED");
            AuthorizationResult result=Tasks.await(Identity.getAuthorizationClient(context).authorize(request(account,busy)),45,TimeUnit.SECONDS);
            if(result.hasResolution()){
                if(result.getPendingIntent()==null)throw failure("CALENDAR_GOOGLE_AUTH_ERROR");
                throw new ConsentRequired(result.getPendingIntent());
            }
            return accepted(busy,result);
        }catch(ConsentRequired required){throw required;}catch(Exception error){throw failure(error);}
    }
    static String fromIntent(Context context,Intent data,boolean busy)throws DriveRestClient.Failure {
        try{if(data==null)throw failure("CALENDAR_CONSENT_CANCELLED");return accepted(busy,Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data));}
        catch(Exception error){throw failure(error);}
    }
    private static String accepted(boolean busy,AuthorizationResult result)throws DriveRestClient.Failure {
        String code=CalendarAccessRules.grantCode(busy,result.getAccessToken(),result.getGrantedScopes());
        if(!code.isEmpty())throw failure(code);return result.getAccessToken();
    }
    static void invalidate(Context context,String rejected)throws DriveRestClient.Failure {
        try{Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(rejected).build()),45,TimeUnit.SECONDS);}
        catch(Exception error){throw failure(error);}
    }
    static DriveRestClient.Failure failure(String code){return new DriveRestClient.Failure(code,CalendarAccessRules.message(code),code.equals("CALENDAR_TOKEN_NETWORK")?30000:0,!code.equals("CALENDAR_TOKEN_NETWORK"));}
    static DriveRestClient.Failure failure(Exception error){
        Throwable cause=error;for(int i=0;i<4&&cause instanceof ExecutionException&&cause.getCause()!=null;i++)cause=cause.getCause();
        if(cause instanceof DriveRestClient.Failure)return (DriveRestClient.Failure)cause;
        if(cause instanceof ApiException){String known=CalendarAccessRules.authenticationCode((ApiException)cause);return failure(known.equals("CALENDAR_APP_REGISTRATION_REQUIRED")||known.equals("CALENDAR_SCOPE_UNSUPPORTED")?known:CalendarAccessRules.googleStatus(((ApiException)cause).getStatusCode()));}
        if(cause instanceof java.util.concurrent.TimeoutException)return failure("CALENDAR_TOKEN_NETWORK");
        if(cause instanceof InterruptedException){Thread.currentThread().interrupt();return failure("CALENDAR_CONSENT_CANCELLED");}
        return failure(CalendarAccessRules.authenticationCode(cause instanceof Exception?(Exception)cause:error));
    }
    private CalendarAuthorization(){}
}
