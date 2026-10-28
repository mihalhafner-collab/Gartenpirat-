package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Retryable cleanup after a verified publication and local snapshot commit, never before. */
final class TrashMediaCleanup {
    interface Files { void deletePhotoFileVerified(String name,String hash)throws Exception; void deleteDocumentFileVerified(JSONObject document)throws Exception; }
    static JSONObject runLocal(android.content.Context context,PixelLocalStore store)throws Exception {
        if(!LocalAuthorityGuard.local(context))return new JSONObject().put("pending",0).put("completed",0).put("state","legacy_receipts_required");
        return run(store,new Files(){public void deletePhotoFileVerified(String name,String hash){}public void deleteDocumentFileVerified(JSONObject document){}},PixelMcpReadTools.effective(store.getState()));
    }
    static JSONObject run(PixelLocalStore store,Files files,JSONObject committed)throws Exception {
        JSONObject archive=OwnerJournalRules.archives(committed);int pending=0,done=0;String error="";
        for(Iterator<String> it=archive.keys();it.hasNext();){String key=it.next();JSONObject tombstone=archive.getJSONObject(key);
            if(!tombstone.optBoolean("purged")||!tombstone.has("media")&&!tombstone.has("document"))continue;
            String marker="purge-cleaned:"+tombstone.getString("purge_action");if(store.ownerValue(marker,"").equals("done"))continue;
            pending++;if(done>=4)continue;
            try {
                if(tombstone.has("document")){
                    JSONObject document=tombstone.getJSONObject("document"),current=PixelMcpReadTools.effective(store.getState());JSONArray queued=store.pendingActions();
                    if(!TrashRules.referenced(current,queued,document.optString("pdf_storage_name")))files.deleteDocumentFileVerified(document);
                    store.cleanupPurgedDocument(key,document,marker);pending--;done++;continue;
                }
                JSONObject media=tombstone.getJSONObject("media"),current=PixelMcpReadTools.effective(store.getState());JSONArray queued=store.pendingActions();
                String original=media.optString("storage_name"),thumb=media.optString("thumbnail_storage_name");
                if(!TrashRules.referenced(current,queued,original)&&!store.hasOtherPhotoAttachment(key.substring(6),media.optString("content_sha256")))files.deletePhotoFileVerified(original,media.getString("content_sha256"));
                if(!thumb.isEmpty()&&!TrashRules.referenced(current,queued,thumb))files.deletePhotoFileVerified(thumb,media.getString("thumbnail_sha256"));
                // Recheck under SQLite's writer lock before local bytes are reclaimed.
                store.cleanupPurgedPhoto(key.substring(6),media,marker);pending--;done++;
            }catch(Exception failure){error=SyncFailureRules.failureCode(failure);}
        }
        return new JSONObject().put("pending",pending).put("completed",done).put("error_code",error);
    }
    private TrashMediaCleanup(){}
}
