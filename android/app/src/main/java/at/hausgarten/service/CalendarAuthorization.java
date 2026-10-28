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
    static AuthorizationRequest request(String account,boolean busy)throws HttpTransport.Failure {
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
    static String fromIntent(Context context,Intent data,boolean busy)throws HttpTransport.Failure {
        try{if(data==null)throw failure("CALENDAR_AUTH_RESULT_MISSING");return accepted(busy,Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data));}
        catch(Exception error){throw failure(error);}
    }
    static String fromActivityResult(Context context,int result,Intent data,boolean busy)throws HttpTransport.Failure {
        // A non-OK Google result can contain a precise ApiException. Decode it first.
        if(data==null)throw failure(CalendarAccessRules.missingResultCode(result));
        return fromIntent(context,data,busy);
    }
    static String signingSha1(Context context){
        try{
            int flag=android.os.Build.VERSION.SDK_INT>=28?android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES:android.content.pm.PackageManager.GET_SIGNATURES;
            android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),flag);
            android.content.pm.Signature[] signers=android.os.Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;
            if(signers==null||signers.length!=1)return "nicht eindeutig";
            byte[] digest=java.security.MessageDigest.getInstance("SHA-1").digest(signers[0].toByteArray());StringBuilder value=new StringBuilder();
            for(byte b:digest){if(value.length()>0)value.append(':');value.append(String.format(java.util.Locale.ROOT,"%02X",b&255));}return value.toString();
        }catch(Exception unavailable){return "nicht lesbar";}
    }
    private static String accepted(boolean busy,AuthorizationResult result)throws HttpTransport.Failure {
        String code=CalendarAccessRules.grantCode(busy,result.getAccessToken(),result.getGrantedScopes());
        if(!code.isEmpty())throw failure(code);return result.getAccessToken();
    }
    static void invalidate(Context context,String rejected)throws HttpTransport.Failure {
        try{Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(rejected).build()),45,TimeUnit.SECONDS);}
        catch(Exception error){throw failure(error);}
    }
    static HttpTransport.Failure failure(String code){boolean retry=code.equals("CALENDAR_TOKEN_NETWORK")||code.equals("CALENDAR_INTERRUPTED");return new HttpTransport.Failure(code,CalendarAccessRules.message(code),retry?30000:0,!retry);}
    static HttpTransport.Failure failure(Exception error){
        Throwable cause=error;for(int i=0;i<4&&cause instanceof ExecutionException&&cause.getCause()!=null;i++)cause=cause.getCause();
        if(cause instanceof HttpTransport.Failure)return (HttpTransport.Failure)cause;
        if(cause instanceof ApiException){String known=CalendarAccessRules.authenticationCode((ApiException)cause);return failure(known.equals("CALENDAR_APP_REGISTRATION_REQUIRED")||known.equals("CALENDAR_SCOPE_UNSUPPORTED")?known:CalendarAccessRules.googleStatus(((ApiException)cause).getStatusCode()));}
        if(cause instanceof java.util.concurrent.TimeoutException)return failure("CALENDAR_TOKEN_NETWORK");
        if(CalendarRecoveryRules.interrupted(cause)){Thread.currentThread().interrupt();return failure("CALENDAR_INTERRUPTED");}
        return failure(CalendarAccessRules.authenticationCode(cause instanceof Exception?(Exception)cause:error));
    }
    private CalendarAuthorization(){}
}
