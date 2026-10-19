package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Limit each pass; one large original is allowed so it cannot starve forever. */
final class SyncMediaPlan {
    static Set<String> select(JSONArray attachments)throws Exception {
        Set<String> selected=new LinkedHashSet<>();long bytes=0;
        for(int i=0;i<attachments.length();i++){
            JSONObject m=attachments.getJSONObject(i);String hash=m.optString("content_sha256");
            if(!hash.matches("[0-9a-f]{64}")||selected.contains(hash))continue;
            long size=m.optLong("byte_size");if(size<=0)continue;
            if(selected.size()>=4||(!selected.isEmpty()&&bytes+size>8*1024*1024))continue;
            selected.add(hash);bytes+=size;
        }
        return selected;
    }
    private SyncMediaPlan(){}
}
