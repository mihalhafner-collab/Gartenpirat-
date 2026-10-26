package at.hausgarten.service;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real streams and immutable files; Android/SQLite/UI acceptance is separate. */
public final class BrowserMediaFilesTests {
    static int checks;interface Action {void run()throws Exception;}
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Browser media "+checks);}
    static void denied(String code,Action action)throws Exception{try{action.run();throw new AssertionError("Missing refusal "+code);}catch(IOException expected){yes(expected.getMessage().equals("BROWSER_MEDIA_"+code));}}
    public static void main(String[] ignored)throws Exception {
        Path root=Files.createTempDirectory("qa_browser_media_");try{
            File media=root.resolve("files/browser-media").toFile(),uploads=root.resolve("noBackup/qa_current_epoch").toFile();boolean[] full={false};BrowserMediaFiles[] store={new BrowserMediaFiles(media,uploads,bytes->{if(full[0])throw new IOException("BROWSER_MEDIA_SPACE_REQUIRED");})};String id=PixelMcpWriteTools.hash("qa_media_upload");byte[] block=new byte[BrowserMediaFiles.CHUNK_BYTES];new Random(71).nextBytes(block);long total=48L*1024*1024;yes(store[0].begin(id,total,"image/jpeg").getLong("next_offset")==0);
            denied("UPLOAD_INCOMPLETE",()->store[0].complete(id));denied("REPLAY_MISMATCH",()->store[0].begin(id,total+1,"image/jpeg"));denied("CHUNK_ORDER_INVALID",()->store[0].append(id,1,block));store[0].append(id,0,block);yes(store[0].append(id,0,block).getLong("next_offset")==block.length);byte[] wrong=block.clone();wrong[5]^=1;denied("REPLAY_MISMATCH",()->store[0].append(id,0,wrong));full[0]=true;denied("SPACE_REQUIRED",()->store[0].append(id,block.length,block));yes(root.resolve("noBackup/qa_current_epoch/"+id+".part").toFile().length()==block.length);full[0]=false;
            // A killed process has no Java state; a new instance resumes exact existing bytes.
            store[0]=new BrowserMediaFiles(media,uploads);yes(store[0].begin(id,total,"image/jpeg").getLong("next_offset")==block.length);for(long offset=block.length;offset<total;offset+=block.length)store[0].append(id,offset,block);JSONObject ref=store[0].complete(id);yes(ref.getLong("byte_size")==total&&ref.getString("sha256").matches("[0-9a-f]{64}"));BrowserMediaFiles.verifyReference(media,ref);yes(store[0].complete(id).toString().equals(ref.toString()));yes(store[0].begin(id,total,"image/jpeg").getLong("next_offset")==0);denied("REPLAY_MISMATCH",()->new BrowserMediaFiles(media,uploads).append(id,0,wrong));
            File copy=root.resolve("qa_restored_48MiB.bin").toFile();try(OutputStream output=new FileOutputStream(copy)){for(long offset=0;offset<total;offset+=block.length)output.write(store[0].read(ref,offset,block.length));}yes(BrowserMediaFiles.hash(copy).equals(ref.getString("sha256"))&&copy.length()>40L*1024*1024);
            JSONObject graph=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("file",ref).put("payload",new JSONObject().put("_photo_request_id","photo_"+"a".repeat(32)).put("customer_id","qa_c").put("order_id","qa_o"))));String before=graph.toString();yes(BrowserMediaFiles.references(graph).length()==1&&graph.toString().equals(before));denied("INLINE_MEDIA_FORBIDDEN",()->BrowserMediaFiles.references(new JSONObject().put("data","data:image/jpeg;base64,AAAA")));denied("GRAPH_SECRET_FIELD",()->BrowserMediaFiles.references(new JSONObject().put("access_token","qa_secret_never_export")));denied("REFERENCE_INVALID",()->BrowserMediaFiles.references(new JSONObject().put("image",new JSONObject(ref.toString()).put("sha256","../qa_escape"))));
            String zero=PixelMcpWriteTools.hash("qa_empty_draft_blob");store[0].begin(zero,0,"application/octet-stream");yes(store[0].complete(zero).getLong("byte_size")==0);String second=PixelMcpWriteTools.hash("qa_same_blob_second_action");store[0].begin(second,total,"image/png");for(long offset=0;offset<total;offset+=block.length)store[0].append(second,offset,block);JSONObject same=store[0].complete(second);yes(same.getString("sha256").equals(ref.getString("sha256"))&&media.listFiles().length==2);
            File original=BrowserMediaFiles.referenceFile(media,ref);try(RandomAccessFile change=new RandomAccessFile(original,"rw")){change.seek(10);change.write(1);}denied("ORIGINAL_MISSING_OR_CHANGED",()->BrowserMediaFiles.verifyReference(media,ref));yes(copy.length()==total);
            System.out.println("PASS "+checks+" browser media 48MiB stream/reopen/chunk-replay/quota/exact-hash/graph-ref/no-inline/no-secret/corruption checks; no Android or SQLite claim");
        }finally{try(java.util.stream.Stream<Path> walk=Files.walk(root)){for(Path path:(Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}
