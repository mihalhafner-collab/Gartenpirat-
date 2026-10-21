package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Bound resident original bytes, not mobile data usage; keep publication/follow-up latency bounded. */
final class SyncMediaPlan {
    static final int FILES_PER_PASS=12;
    static final long BYTES_PER_PASS=32L*1024*1024;
    static Set<String> select(JSONArray attachments)throws Exception {
        return select(attachments,"",false);
    }
    static Set<String> select(JSONArray attachments,String afterHash,boolean urgentChanges)throws Exception {
        LinkedHashMap<String,Long> unique=new LinkedHashMap<>();
        for(int i=0;i<attachments.length();i++){
            JSONObject m=attachments.getJSONObject(i);String hash=m.optString("content_sha256");
            if(!hash.matches("[0-9a-f]{64}")||unique.containsKey(hash))continue;
            long size=m.optLong("byte_size");if(size<=0)continue;
            unique.put(hash,size);
        }
        List<String> hashes=new ArrayList<>(unique.keySet());int start=hashes.indexOf(afterHash)+1;
        Set<String> selected=new LinkedHashSet<>();long bytes=0;int limit=urgentChanges?DriveTransferPool.WORKERS:FILES_PER_PASS;
        for(int i=0;i<hashes.size();i++){
            String hash=hashes.get((start+i)%hashes.size());long size=unique.get(hash);
            if(selected.size()>=limit||(!selected.isEmpty()&&bytes+size>BYTES_PER_PASS))continue;
            selected.add(hash);bytes+=size;
        }
        return selected;
    }
    static String nextCursor(JSONArray attachments,String afterHash,Set<String> selected)throws Exception{
        LinkedHashSet<String> unique=new LinkedHashSet<>();for(int i=0;i<attachments.length();i++){JSONObject m=attachments.getJSONObject(i);String hash=m.optString("content_sha256");if(hash.matches("[0-9a-f]{64}")&&m.optLong("byte_size")>0)unique.add(hash);}
        List<String> hashes=new ArrayList<>(unique);if(hashes.isEmpty()||selected.isEmpty())return afterHash;int start=hashes.indexOf(afterHash)+1;
        // The first deferred original gets the first slot next time, including files skipped for RAM.
        for(int i=0;i<hashes.size();i++)if(!selected.contains(hashes.get((start+i)%hashes.size())))return hashes.get((start+i+hashes.size()-1)%hashes.size());
        return hashes.get((start+hashes.size()-1)%hashes.size());
    }
    private SyncMediaPlan(){}
}
