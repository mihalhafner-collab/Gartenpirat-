package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Photo use cases. Effects pass through the existing owner repository transaction. */
final class PhotoApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    PhotoApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=repository.effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "begin_file_upload":{
                String id="upload_"+PixelMcpWriteTools.hash(request);JSONObject m=new JSONObject(a.toString()).put("kind","photo_upload");m.remove("request_id");repository.putFile(id,m,new byte[0]);return new JSONObject().put("upload_id",id).put("next_offset",0).put("max_chunk_bytes",PixelMcpOwnerTools.CHUNK);
            }
            case "write_file_chunk":{
                String id=a.getString("upload_id");JSONObject m=repository.fileMeta(id);if(!"photo_upload".equals(m.optString("kind"))||m.has("attached_photo"))throw new IllegalArgumentException("Upload ist nicht beschreibbar.");
                byte[] previous=repository.fileBytes(id),combined=McpFileTransfer.append(previous,a.getString("chunk_base64"),a.getInt("offset"),m.getInt("byte_size"),a.getString("chunk_sha256"));repository.putFile(id,m,combined);return new JSONObject().put("upload_id",id).put("next_offset",combined.length).put("complete",combined.length==m.getInt("byte_size"));
            }
            case "create_photo":{
                String upload=a.getString("upload_id");JSONObject m=repository.fileMeta(upload);byte[] bytes=repository.fileBytes(upload);
                if(m.has("attached_photo")||bytes.length!=m.getInt("byte_size")||!McpFileTransfer.sha(bytes).equals(m.getString("sha256")))throw new IllegalArgumentException("Upload unvollständig, verändert oder bereits verwendet.");
                JSONObject p=new JSONObject(a.toString());p.remove("request_id");p.remove("upload_id");p.put("id","pho_mcp_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("data","data:"+m.getString("mime_type")+";base64,"+Base64.getEncoder().encodeToString(bytes));String id=repository.savePhoto(p);m.put("attached_photo",id);repository.putFile(upload,m,new byte[0]);return new JSONObject().put("photo_id",id).put("local_state","local_saved").put("sync_state","sync_pending");
            }
            case "update_photo":{

                JSONObject old=PixelMcpWriteTools.find(all.getJSONArray("photos"),a.getString("photo_id"));if(!PixelMcpWriteTools.hash(PixelMcpOwnerTools.photoHash(old)).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Foto wurde inzwischen geändert.");JSONObject p=DriveJournalRules.clean(old),updates=a.getJSONObject("updates");for(Iterator<String> keys=updates.keys();keys.hasNext();){String key=keys.next();p.put(key,updates.get(key));}EntityProtectionRules.validatePhoto(p,all.getJSONArray("customers"),all.getJSONArray("orders"),all.getJSONArray("worklogs"));ManualChangeRules.prepare("photo",p,old,stamp);return repository.enqueue("photo",p,request);
            }
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
}
