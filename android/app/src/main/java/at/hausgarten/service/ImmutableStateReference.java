package at.hausgarten.service;
import org.json.*;
import java.util.*;
/** SAF may replace a temporary document ID after cloud upload. Recover only exact immutable bytes. */
final class ImmutableStateReference {
    interface Reader {byte[] read(String id)throws Exception;}
    static byte[] read(Map<String,String> names,JSONObject manifest,Reader reader)throws Exception {
        String id=manifest.getString("state_file_id"),hash=manifest.getString("state_sha256");
        if(!hash.matches("[0-9a-f]{64}"))throw new SyncIoFailure("PUBLICATION_INVALID");
        if(names.containsKey(id))return reader.read(id); // Existing ID never falls back on error or changed content.
        String expected="HausGarten-State-"+hash+".json";
        if(manifest.has("state_name")&&!expected.equals(manifest.getString("state_name")))throw new SyncIoFailure("PUBLICATION_INVALID");
        byte[] recovered=null;
        for(Map.Entry<String,String> row:names.entrySet())if(expected.equals(row.getValue())){
            byte[] bytes=reader.read(row.getKey());
            if(!hash.equals(DriveRestClient.sha256(bytes)))throw new SyncIoFailure("PUBLICATION_INVALID");
            if(recovered!=null&&!Arrays.equals(recovered,bytes))throw new SyncIoFailure("REMOTE_DUPLICATES_DIFFER");recovered=bytes;
        }
        if(recovered==null)throw new SyncIoFailure("REMOTE_STATE_REFERENCE_MISSING");
        return recovered; // Caller still validates the complete publication manifest and chain.
    }
    private ImmutableStateReference(){}
}
