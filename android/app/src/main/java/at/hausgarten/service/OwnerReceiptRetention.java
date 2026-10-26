package at.hausgarten.service;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Called only after a server-verified publication and successful local commit/status write. */
final class OwnerReceiptRetention {
    static int afterConfirmedSync(PixelLocalStore store,JSONObject committed,long now)throws Exception{
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();int removed=0;
        try{
            JSONArray pending=store.pendingActions(),references=new JSONArray().put(OwnerJournalRules.archives(committed))
                .put(new JSONObject(store.ownerValue("purged_local_test_records_v1","{}")));
            Set<String> backupIds=new HashSet<>();
            try(Cursor rows=db.rawQuery("SELECT id,metadata FROM mcp_uploads",null)){while(rows.moveToNext()){backupIds.add(rows.getString(0));references.put(new JSONObject(rows.getString(1)));}}
            ArrayList<String> keys=new ArrayList<>();try(Cursor rows=db.rawQuery("SELECT key FROM owner_state WHERE key LIKE 'receipt:%' ORDER BY key",null)){while(rows.moveToNext())keys.add(rows.getString(0));}
            String stamp=java.time.Instant.ofEpochMilli(now).toString();
            for(String key:keys){String request=key.substring("receipt:".length());JSONObject receipt=new JSONObject(store.ownerValue(key,"{}"));
                // Legacy receipts have no reliable creation date. Start a conservative observation window.
                if(!receipt.has("created_at")&&!receipt.has("observed_at")){receipt.put("observed_at",stamp);store.putOwnerValue(key,receipt.toString());continue;}
                if(backupIds.contains("backup_deletion_"+PixelMcpWriteTools.hash(request))||backupIds.contains("backup_"+PixelMcpWriteTools.hash(request)))continue;
                if(!ReceiptRetentionRules.eligible(receipt,request,pending,references,now))continue;
                store.putOwnerValue("receipt_guard:"+request,new JSONObject().put("fingerprint",receipt.getString("fingerprint")).put("compacted_at",stamp).toString());
                removed+=db.delete("owner_state","key=?",new String[]{key});
            }
            store.putOwnerValue("receipt_retention_summary",new JSONObject().put("removed",removed).put("checked_at",stamp).toString());
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return removed;
    }
    private OwnerReceiptRetention(){}
}
