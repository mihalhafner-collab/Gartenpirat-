package at.hausgarten.service;

import org.json.*;

/** Attachment cache must never rewrite an immutable metadata change or its role/hash. */
final class PhotoJournalRules {
    static JSONObject forSnapshot(JSONObject original,JSONObject metadata)throws Exception {
        JSONObject p=new JSONObject(original.toString());
        boolean legacy=p.has("data")||p.has("full_data");
        if(legacy&&metadata!=null)for(String key:new String[]{"content_sha256","mime_type","byte_size","storage_name","filename","role"})
            if(!p.has(key)&&metadata.has(key))p.put(key,metadata.get(key));
        p.remove("data");p.remove("full_data");return p;
    }
    private PhotoJournalRules(){}
}
