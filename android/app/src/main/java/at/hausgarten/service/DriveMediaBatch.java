package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Independent immutable originals, then previews; never journal or SQLite mutations. */
final class DriveMediaBatch {
    interface Source {
        byte[] original(JSONObject metadata)throws Exception;
        void verify(byte[] raw,JSONObject metadata)throws Exception;
        byte[] thumbnail(String name)throws Exception;
    }
    static Map<String,Exception> transfer(DriveRestClient client,JSONArray batch,Source source)throws Exception {
        Map<String,Exception> failures=new HashMap<>();if(batch.length()==0)return failures;
        // Folder creation stays single-threaded. No worker can race to create a second directory.
        String directory;JSONArray rows;
        try{directory=client.ensureDirectory("HausGarten-Fotos");rows=client.list(directory);}
        catch(Exception error){for(int i=0;i<batch.length();i++)failures.put(batch.getJSONObject(i).getString("photo_id"),error);return failures;}
        Map<String,List<JSONObject>> groups=new LinkedHashMap<>();
        for(int i=0;i<batch.length();i++){JSONObject m=new JSONObject(batch.getJSONObject(i).toString());groups.computeIfAbsent(m.getString("content_sha256"),key->new ArrayList<>()).add(m);}
        List<DriveRestClient> children=Collections.synchronizedList(new ArrayList<>());List<java.util.concurrent.Callable<Void>> uploads=new ArrayList<>();List<String> hashes=new ArrayList<>(groups.keySet());final JSONArray originalRows=rows;
        for(String hash:hashes){List<JSONObject> group=groups.get(hash);
            uploads.add(()->{DriveRestClient child=client.fork(directory,originalRows);children.add(child);try{JSONObject m=group.get(0);byte[] raw=source.original(m);for(JSONObject reference:group)source.verify(raw,reference);child.put(directory,m.getString("storage_name"),m.getString("mime_type"),raw);return null;}finally{children.remove(child);}});}
        Runnable cancel=()->{synchronized(children){for(DriveRestClient child:children)child.cancelActiveTransfers();}};
        List<DriveTransferPool.Result<Void>> results=DriveTransferPool.run(uploads,cancel);
        for(int i=0;i<results.size();i++)if(results.get(i).error!=null)for(JSONObject m:groups.get(hashes.get(i)))failures.put(m.getString("photo_id"),results.get(i).error);
        // Thumbnails are deduplicated across ALL originals and follow verified originals.
        client.refresh();children.clear();uploads.clear();Map<String,List<JSONObject>> previews=new LinkedHashMap<>();
        for(int i=0;i<batch.length();i++){JSONObject m=new JSONObject(batch.getJSONObject(i).toString());String thumb=m.optString("thumbnail_storage_name");if(!failures.containsKey(m.getString("photo_id"))&&!thumb.isEmpty())previews.computeIfAbsent(thumb,key->new ArrayList<>()).add(m);}
        List<String> names=new ArrayList<>(previews.keySet());
        try{rows=names.isEmpty()?new JSONArray():client.list(directory);}catch(Exception unavailable){for(List<JSONObject> references:previews.values())for(JSONObject m:references)failures.put(m.getString("photo_id"),unavailable);return failures;}
        final JSONArray previewRows=rows;
        for(String name:names){uploads.add(()->{DriveRestClient child=client.fork(directory,previewRows);children.add(child);try{child.put(directory,name,"image/jpeg",source.thumbnail(name));return null;}finally{children.remove(child);}});}
        results=DriveTransferPool.run(uploads,cancel);
        for(int i=0;i<results.size();i++)if(results.get(i).error!=null)for(JSONObject m:previews.get(names.get(i)))failures.put(m.getString("photo_id"),results.get(i).error);
        client.refresh();return failures; // Coordinator alone mutates SQLite/action state after every worker joined.
    }
    private DriveMediaBatch(){}
}
