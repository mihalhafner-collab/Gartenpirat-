package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Real production uploader/HTTP streaming reader; disk-backed synthetic Drive port. */
public final class DriveBackupUploadTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("backup upload check "+checks);}
    interface Action {void run()throws Exception;}
    static void rejected(Action action)throws Exception{try{action.run();}catch(IOException|SecurityException expected){checks++;return;}throw new AssertionError("Expected safe refusal");}
    static final class MemoryCheckpoint implements RetiredDriveBackupUpload.Checkpoint {
        JSONObject saved;String failState="";
        public JSONObject read()throws Exception{return saved==null?null:new JSONObject(saved.toString());}
        public void save(JSONObject value)throws Exception{if(value.optString("state").equals(failState))throw new IOException("qa_simulated_checkpoint_interruption");saved=new JSONObject(value.toString());}
    }
    static final String FOLDER="qa_backup_folder_123",REMOTE="qa_backup_remote_123",ID="qa_backup_generation_123";
    static final class Server implements RetiredBackupDriveClient.Wire {
        final File remote;long received,total;int generated,starts,completed,readbacks,maxBody,requests;boolean exists,dropChunk,dropFinal,corruptReadback;String rangeOverride,location="https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=qa_session";String name=ID+".hgb",parent=FOLDER;
        Server(File root)throws Exception{remote=new File(root,"qa_remote_"+UUID.randomUUID()+".bin");}
        RetiredBackupDriveClient.Reply reply(int code,String body,Map<String,String> headers){return new RetiredBackupDriveClient.Reply(code,body.getBytes(java.nio.charset.StandardCharsets.UTF_8),headers);}
        JSONObject metadata()throws Exception{return new JSONObject().put("id",REMOTE).put("name",name).put("parents",new JSONArray().put(parent)).put("mimeType","application/octet-stream").put("size",Long.toString(total)).put("version","qa_v1").put("trashed",false);}
        public RetiredBackupDriveClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception{
            requests++;yes(headers.get("Authorization").equals("Bearer qa_test_token"));yes(maximum<=8192);if(body!=null)maxBody=Math.max(maxBody,body.length);
            if(url.contains("generateIds")){generated++;return reply(200,new JSONObject().put("ids",new JSONArray().put(REMOTE)).toString(),Collections.emptyMap());}
            if(method.equals("GET")){yes(url.startsWith(RetiredBackupDriveClient.API+"files/"+REMOTE+"?fields="));return reply(exists?200:404,exists?metadata().toString():"{}",Collections.emptyMap());}
            if(method.equals("POST")){
                starts++;JSONObject meta=new JSONObject(new String(body,java.nio.charset.StandardCharsets.UTF_8));yes(meta.getString("id").equals(REMOTE)&&meta.getString("name").equals(ID+".hgb"));total=Long.parseLong(headers.get("X-Upload-Content-Length"));received=0;try(RandomAccessFile out=new RandomAccessFile(remote,"rw")){out.setLength(0);}return reply(200,"{}",Collections.singletonMap("Location",location));
            }
            yes(method.equals("PUT")&&url.equals(location));String range=headers.get("Content-Range");
            if(range.equals("bytes */"+total)){if(exists)return reply(200,new JSONObject().put("id",REMOTE).toString(),Collections.emptyMap());return reply(308,"",received==0?Collections.emptyMap():Collections.singletonMap("Range",rangeOverride!=null?rangeOverride:"bytes=0-"+(received-1)));}
            String[] coordinates=range.substring(6).split("[-/]");long start=Long.parseLong(coordinates[0]),end=Long.parseLong(coordinates[1]);yes(start==received&&end-start+1==body.length&&Long.parseLong(coordinates[2])==total);
            try(RandomAccessFile out=new RandomAccessFile(remote,"rw")){out.seek(start);out.write(body);}received+=body.length;
            if(dropChunk&&received<total){dropChunk=false;throw new IOException("qa_lost_chunk_reply");}
            if(received==total){exists=true;completed++;if(dropFinal){dropFinal=false;throw new IOException("qa_lost_final_reply");}return reply(200,new JSONObject().put("id",REMOTE).toString(),Collections.emptyMap());}
            return reply(308,"",Collections.singletonMap("Range",rangeOverride!=null?rangeOverride:"bytes=0-"+(received-1)));
        }
        public RetiredBackupDriveClient.HashReply readHash(String url,Map<String,String> headers,long expected)throws Exception{
            readbacks++;yes(url.equals(RetiredBackupDriveClient.API+"files/"+REMOTE+"?alt=media&supportsAllDrives=true"));
            return new RetiredBackupDriveClient.HttpWire(target->new HttpURLConnection(target){
                public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}
                public Map<String,List<String>> getHeaderFields(){return Collections.emptyMap();}
                public InputStream getInputStream()throws IOException{InputStream source=new FileInputStream(remote);if(!corruptReadback)return source;return new FilterInputStream(source){boolean altered;@Override public int read(byte[] b,int off,int length)throws IOException{int n=super.read(b,off,length);if(n>0&&!altered){b[off]^=1;altered=true;}return n;}};}
            }).readHash(url,headers,expected);
        }
    }
    static RetiredDriveBackupUpload uploader(Server server){return new RetiredDriveBackupUpload(new RetiredBackupDriveClient(FOLDER,()->"qa_test_token",server,new RetiredBackupDriveClient.Checkpoints(){public String get(String key){return "";}public void put(String key,String value){}}));}
    static File payload(File root,long size)throws Exception{File file=new File(root,"qa_sealed.bin");byte[] buffer=new byte[64*1024];Random random=new Random(71);try(OutputStream out=new FileOutputStream(file)){for(long n=0;n<size;){random.nextBytes(buffer);int length=(int)Math.min(buffer.length,size-n);out.write(buffer,0,length);n+=length;}}return file;}
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("qa_backup_upload_").toFile();
        try{
            File large=payload(root,48L*1024*1024+79);String sha=EncryptedBackupArchive.sha256(large);Server server=new Server(root);MemoryCheckpoint checkpoint=new MemoryCheckpoint();
            JSONObject verified=uploader(server).upload(large,ID,sha,checkpoint);yes(verified.getString("state").equals("REMOTE_VERIFIED")&&verified.getString("remoteSha256").equals(sha)&&server.completed==1&&server.maxBody<=1024*1024);
            uploader(server).upload(large,ID,sha,checkpoint);yes(server.starts==1&&server.generated==1&&server.completed==1&&server.readbacks==2);
            server.corruptReadback=true;rejected(()->uploader(server).upload(large,ID,sha,checkpoint));yes(checkpoint.saved.getString("state").equals("REMOTE_VERIFY_PENDING"));server.corruptReadback=false;
            uploader(server).upload(large,ID,sha,checkpoint);yes(server.completed==1&&checkpoint.saved.getString("state").equals("REMOTE_VERIFIED"));
            String originalName=server.name;server.name="qa_other.hgb";rejected(()->uploader(server).upload(large,ID,sha,checkpoint));yes(checkpoint.saved.getString("state").equals("UPLOAD_PENDING")&&server.starts==1);server.name=originalName;
            int before=server.requests;rejected(()->uploader(server).upload(large,"qa_other_generation",sha,checkpoint));yes(server.requests==before);
            File small=payload(root,2L*1024*1024+43);String smallSha=EncryptedBackupArchive.sha256(small);
            Server dropped=new Server(root);MemoryCheckpoint c2=new MemoryCheckpoint();dropped.dropChunk=true;rejected(()->uploader(dropped).upload(small,ID,smallSha,c2));yes(!dropped.exists&&c2.saved.getString("state").equals("UPLOADING"));uploader(dropped).upload(small,ID,smallSha,c2);yes(dropped.starts==1&&dropped.generated==1&&dropped.completed==1);
            Server finalDrop=new Server(root);MemoryCheckpoint c3=new MemoryCheckpoint();finalDrop.dropFinal=true;rejected(()->uploader(finalDrop).upload(small,ID,smallSha,c3));yes(finalDrop.exists);uploader(finalDrop).upload(small,ID,smallSha,c3);yes(finalDrop.completed==1&&finalDrop.starts==1);
            Server lostCheckpoint=new Server(root);MemoryCheckpoint c4=new MemoryCheckpoint();c4.failState="UPLOADING";rejected(()->uploader(lostCheckpoint).upload(small,ID,smallSha,c4));yes(c4.saved.getString("remoteId").equals(REMOTE));c4.failState="";uploader(lostCheckpoint).upload(small,ID,smallSha,c4);yes(lostCheckpoint.generated==1&&lostCheckpoint.starts==2&&lostCheckpoint.completed==1);
            Server badRange=new Server(root);badRange.rangeOverride="bytes=0-99999999999999999999999999999";MemoryCheckpoint c5=new MemoryCheckpoint();rejected(()->uploader(badRange).upload(small,ID,smallSha,c5));yes(!badRange.exists&&!c5.saved.getString("state").equals("REMOTE_VERIFIED"));
            Server badTarget=new Server(root);badTarget.location="https://qa.example.invalid/upload_id=qa";MemoryCheckpoint c6=new MemoryCheckpoint();rejected(()->uploader(badTarget).upload(small,ID,smallSha,c6));yes(badTarget.received==0&&c6.saved.getString("session").isEmpty());
            RetiredBackupDriveClient.HttpWire http=new RetiredBackupDriveClient.HttpWire(target->{throw new AssertionError("Untrusted URL opened");});rejected(()->http.readHash("https://www.googleapis.com@qa.example.invalid/file",Collections.emptyMap(),1));rejected(()->http.readHash("http://www.googleapis.com/file",Collections.emptyMap(),1));
            Server mismatch=new Server(root);MemoryCheckpoint c7=new MemoryCheckpoint();uploader(mismatch).upload(small,ID,smallSha,c7);mismatch.total++;rejected(()->uploader(mismatch).upload(small,ID,smallSha,c7));yes(mismatch.completed==1&&!c7.saved.getString("state").equals("REMOTE_VERIFIED"));
            System.out.println("PASS "+checks+" backup upload assertions;48MiB disk payload,1MiB chunks,real streaming HTTP hash reader,restarts/lost replies/conflicts;synthetic port only,no Google account");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}
