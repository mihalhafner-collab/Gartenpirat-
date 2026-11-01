package at.hausgarten.service;

import android.content.Context;
import org.json.JSONObject;

/** Compatibility responses only. The retired business transport is absent from the app. */
final class DriveSyncCoordinator {
    static final Object LOCK=new Object();
    static boolean running(){return false;}
    static JSONObject status(Context context,JSONObject queue)throws Exception {
        JSONObject result=DriveRolePolicy.retiredResponse().put("ok",true).put("mode",DriveRolePolicy.role()).put("sync_contract_version",DriveRolePolicy.contractVersion()).put("configured",false).put("legacy_sync_enabled",false).put("automatic_enabled",false).put("syncing",false).put("legacy_queue_preserved",true).put("confirmation_scope","local_only").put("migration_required",!LocalAuthorityGuard.local(context)).put("device_action","openFullBackup");
        if(queue!=null)for(String key:new String[]{"pending","pending_upload","review","total","locally_committed"})if(queue.has(key))result.put(key,queue.get(key));
        return result;
    }
    static JSONObject requestExplicit(Context context,String request)throws Exception{return status(context,null);}
    static boolean hasNetwork(Context context){
        android.net.ConnectivityManager manager=context.getSystemService(android.net.ConnectivityManager.class);if(manager==null)return false;
        android.net.Network network=manager.getActiveNetwork();android.net.NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
        return caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    private DriveSyncCoordinator(){}
}
