package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Retryable cleanup after a verified publication and local snapshot commit, never before. */
final class TrashMediaCleanup {
    static JSONObject run(MainActivity.LocalStore store,DriveSyncCoordinator.Files files,JSONObject committed)throws Exception {
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
            }catch(Exception failure){error=DriveSyncCoordinator.failureCode(failure);}
        }
        return new JSONObject().put("pending",pending).put("completed",done).put("error_code",error);
    }
    private TrashMediaCleanup(){}
}
