package at.hausgarten.service;
import java.util.*;
/** Bounded RAM cache for one read phase. No metadata-only trust across a write boundary. */
final class VerifiedSyncCache {
    private final int limit;private int size;
    private final LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>(16,0.75f,true);
    VerifiedSyncCache(int limit){this.limit=limit;}
    byte[] get(String id){byte[] b=entries.get(id);return b==null?null:b.clone();}
    void remember(String id,String name,byte[] bytes)throws Exception {
        if(!name.matches("HausGarten-(Commit|State)-[0-9a-f]{64}\\.json")||!name.endsWith(DriveRestClient.sha256(bytes)+".json"))return;
        byte[] old=entries.remove(id);if(old!=null)size-=old.length;
        if(bytes.length>limit)return;
        while(size+bytes.length>limit&&!entries.isEmpty()){String first=entries.keySet().iterator().next();size-=entries.remove(first).length;}
        entries.put(id,bytes.clone());size+=bytes.length;
    }
    void clear(){entries.clear();size=0;}
    int bytes(){return size;}
}
