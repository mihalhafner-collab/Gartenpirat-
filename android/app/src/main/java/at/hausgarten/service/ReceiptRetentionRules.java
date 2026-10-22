package at.hausgarten.service;
import org.json.*;
import java.time.Instant;
import java.util.*;

/** Pure retention gate. Full receipts expire; minimal replay guards remain permanent. */
final class ReceiptRetentionRules {
    static final long AGE_MS=90L*24*60*60*1000;
    static boolean eligible(JSONObject receipt,String request,JSONArray pending,Object references,long now)throws Exception{
        String stamp=receipt.optString("created_at",receipt.optString("observed_at"));long created;
        try{created=Instant.parse(stamp).toEpochMilli();}catch(Exception unknownAge){return false;}
        if(created>now||now-created<=AGE_MS||receipt.optString("fingerprint").isEmpty())return false;
        if(references(pending,request)||references(references,request))return false;
        JSONObject result=receipt.optJSONObject("result");
        // Deletion backups and LocalTestCleanup consume these complete receipts later.
        if(result==null||!result.optString("deletion_backup_id").isEmpty()||!result.optString("backup_id").isEmpty())return false;
        return true;
    }
    static boolean references(Object value,String request)throws Exception{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;for(Iterator<String> it=object.keys();it.hasNext();){String key=it.next();if(key.equals(request)||references(object.get(key),request))return true;}}
        else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)if(references(array.get(i),request))return true;}
        else if(value instanceof String)return request.equals(value);
        return false;
    }
    private ReceiptRetentionRules(){}
}
