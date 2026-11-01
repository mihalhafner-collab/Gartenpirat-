package at.hausgarten.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Local browser checkpoint in the existing database; no account or cloud settings. */
final class BackupSettingsRepository {
    private final Context context;private final PixelLocalStore store;private final BackupDeviceGuard guard;
    BackupSettingsRepository(Context context,PixelLocalStore store)throws Exception {this.context=context;this.store=store;guard=new BackupDeviceGuard(context);}
    JSONObject browser()throws Exception {
        String error=browserError();if(!error.isEmpty())throw new IOException(error);
        String raw=store.ownerValue("full_backup_browser","");if(raw.isEmpty())throw new IOException("BACKUP_BROWSER_STATE_REQUIRED_OPEN_APP");JSONObject saved=new JSONObject(raw),identity=guard.current();
        if(saved.getInt("formatVersion")!=1||!saved.getString("installationId").equals(identity.getString("installationId"))||!saved.getString("restoreEpoch").equals(identity.getString("restoreEpoch")))throw new IOException("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED");
        return saved;
    }
    String browserError(){
        String raw=store.ownerValue("full_backup_browser_status","");if(raw.isEmpty())return "";
        try{JSONObject status=new JSONObject(raw);if(status.optBoolean("capturePending"))return "BACKUP_BROWSER_CAPTURE_REQUIRED";String code=status.optString("code");if(code.isEmpty())return "";return java.util.Arrays.asList("BACKUP_BROWSER_CAPTURE_REQUIRED","BACKUP_BROWSER_STATE_TOO_LARGE","BACKUP_BROWSER_WRITE_CONFLICT").contains(code)?code:"BACKUP_BROWSER_CAPTURE_REQUIRED";}catch(Exception invalid){return "BACKUP_BROWSER_CAPTURE_REQUIRED";}
    }
    boolean browserCapturePending(){try{return new JSONObject(store.ownerValue("full_backup_browser_status","{}")).optBoolean("capturePending");}catch(Exception invalid){return false;}}
    String browserRevision()throws Exception {return PixelMcpWriteTools.hash(store.ownerValue("full_backup_browser",""));}
    JSONObject saveBrowser(String requestId,String expectedRevision,JSONObject browser)throws Exception {
        new RestoredBrowserCheckpoint(context,store).assertCheckpointAllowed();
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("BACKUP_REQUEST_ID_REQUIRED");
        if(expectedRevision==null||!expectedRevision.matches("[0-9a-f]{64}"))throw new IOException("BACKUP_BROWSER_REVISION_REQUIRED");
        if(browser==null||browser.optInt("formatVersion")!=1||browser.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_STATE_REQUIRED");
        JSONObject storage=browser.getJSONObject("storage");for(java.util.Iterator<String> it=storage.keys();it.hasNext();)if(!(storage.get(it.next()) instanceof String))throw new IOException("BACKUP_BROWSER_STORAGE_INVALID");
        if(browser.toString().getBytes(StandardCharsets.UTF_8).length>16*1024*1024)throw new IOException("BACKUP_BROWSER_STATE_TOO_LARGE");
        JSONObject identity=guard.current();String hash=PixelMcpWriteTools.hash(browser),receiptKey="full_backup_browser_request:"+PixelMcpWriteTools.hash(requestId),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("browserSha256",hash).put("expectedRevision",expectedRevision).put("identity",identity));SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            String receipt=store.ownerValue(receiptKey,"");if(!receipt.isEmpty()){
                JSONObject prior=new JSONObject(receipt);if(!fingerprint.equals(prior.getString("fingerprint")))throw new IOException("BACKUP_BROWSER_REPLAY_MISMATCH");db.setTransactionSuccessful();return prior.getJSONObject("result").put("replayed",true);
            }
            String raw=store.ownerValue("full_backup_browser","");if(!raw.isEmpty()){
                JSONObject saved=new JSONObject(raw);boolean same=identity.getString("installationId").equals(saved.optString("installationId"))&&identity.getString("restoreEpoch").equals(saved.optString("restoreEpoch"));
                if(same&&hash.equals(saved.optString("contentSha256"))){JSONObject result=new JSONObject().put("contentSha256",hash).put("capturedAt",saved.getString("capturedAt"));store.putOwnerValue(receiptKey,new JSONObject().put("fingerprint",fingerprint).put("result",result).toString());db.setTransactionSuccessful();return result;}
                if(requestId.equals(saved.optString("requestId")))throw new IOException("BACKUP_BROWSER_REPLAY_MISMATCH");
            }
            if(!browserRevision().equals(expectedRevision))throw new IOException("BACKUP_BROWSER_WRITE_CONFLICT");
            BrowserDraftRepository.validateCaptured(db,identity,browser);
            JSONObject saved=new JSONObject().put("formatVersion",1).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch")).put("requestId",requestId).put("capturedAt",java.time.Instant.now().toString()).put("contentSha256",hash).put("browser",new JSONObject(browser.toString()));store.putOwnerValue("full_backup_browser",saved.toString());JSONObject result=new JSONObject().put("contentSha256",hash).put("capturedAt",saved.getString("capturedAt"));store.putOwnerValue(receiptKey,new JSONObject().put("fingerprint",fingerprint).put("result",result).toString());db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
}
