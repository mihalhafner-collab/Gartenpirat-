package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Count actual transport calls using synthetic responses, including fresh inventory changes. */
public final class DrivePerformanceTests {
    static void yes(boolean value){if(!value)throw new AssertionError();}
    interface Case{void run()throws Exception;}
    static int passed;
    static void check(String name,Case test)throws Exception{test.run();passed++;System.out.println("PASS "+name);}
    static void reject(Case test)throws Exception{try{test.run();}catch(IOException expected){return;}throw new AssertionError("Expected rejection");}
    static final class Inventory implements DriveRestClient.Wire {
        final Map<String,byte[]> bodies=new LinkedHashMap<>();final Map<String,JSONObject> rows=new LinkedHashMap<>();
        int reads,lists;boolean absent;
        JSONObject add(String id,String kind,byte[] bytes)throws Exception{
            StringBuilder md5=new StringBuilder();for(byte b:MessageDigest.getInstance("MD5").digest(bytes))md5.append(String.format(Locale.ROOT,"%02x",b&255));
            JSONObject row=new JSONObject().put("id",id).put("name",kind+DriveRestClient.sha256(bytes)+".json")
                .put("version","1").put("md5Checksum",md5.toString()).put("size",bytes.length);
            rows.put(id,row);bodies.put(id,bytes);return row;
        }
        public DriveRestClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int max)throws Exception{
            yes(method.equals("GET"));
            if(url.contains("files?q=")){lists++;return DriveTransportTests.reply(200,new JSONObject().put("files",absent?new JSONArray():new JSONArray(rows.values())).toString());}
            reads++;String id=url.substring(url.lastIndexOf('/')+1).split("\\?",2)[0];byte[] raw=absent?null:bodies.get(id);
            if(raw==null)return DriveTransportTests.reply(404,"");if(raw.length>max)throw new IOException("Synthetic payload limit");
            return new DriveRestClient.Reply(200,raw.clone(),Collections.emptyMap());
        }
    }
    static DriveRestClient client(DriveRestClient.Wire wire){return DriveTransportTests.client(wire,new DriveTransportTests.Memory());}
    static DrivePrimaryState.Storage storage(DriveRestClient client){return new DrivePrimaryState.Storage(){
        public Map<String,String> commits()throws Exception{Map<String,String> names=new LinkedHashMap<>();JSONArray rows=client.list(client.folder);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getString("name").startsWith("HausGarten-Commit-"))names.put(row.getString("id"),row.getString("name"));}return names;}
        public byte[] read(String id)throws Exception{return client.readRoot(id,DriveRestClient.MAX_JSON);}
    };}
    public static void main(String[] args)throws Exception{
        SyntheticBusinessData.verify();
        check("8 MiB: previous 8 -> 2 data PUTs, one full server readback",()->{
            byte[] raw=new byte[8*1024*1024];Arrays.fill(raw,(byte)7);DriveTransportTests.Remote remote=new DriveTransportTests.Remote(raw);
            DriveRestClient c=client(remote);c.put(c.folder,"sample.json","image/jpeg",raw);
            yes(remote.puts==2&&remote.starts==1&&remote.reads==1&&Arrays.equals(remote.stored,raw));
        });
        check("large upload survives process loss without a second session",()->{
            byte[] raw=new byte[3*1024*1024+123];DriveTransportTests.Remote remote=new DriveTransportTests.Remote(raw);remote.failSecond=true;
            DriveTransportTests.Memory checkpoints=new DriveTransportTests.Memory();DriveRestClient c=DriveTransportTests.client(remote,checkpoints);
            reject(()->c.put(c.folder,"sample.json","image/jpeg",raw));yes(remote.offset==1024*1024);
            remote.failSecond=false;DriveTransportTests.client(remote,checkpoints).put(c.folder,"sample.json","image/jpeg",raw);
            yes(remote.starts==1&&remote.reads==1&&remote.offset==raw.length);
        });
        check("large upload still rejects corrupt readback",()->{
            DriveTransportTests.Remote remote=new DriveTransportTests.Remote(new byte[2*1024*1024]);remote.corrupt=true;
            DriveTransportTests.Memory memory=new DriveTransportTests.Memory();DriveRestClient c=DriveTransportTests.client(remote,memory);
            reject(()->c.put(c.folder,"sample.json","image/jpeg",remote.original));yes(memory.values.entrySet().stream().anyMatch(e->e.getKey().startsWith("drive-upload:")&&!e.getValue().isEmpty()));
        });
        check("old 256 KiB checkpoint resumes with larger chunks and no new session",()->{
            byte[] raw=new byte[3*1024*1024];DriveTransportTests.Remote remote=new DriveTransportTests.Remote(raw);remote.offset=DriveRestClient.CHUNK;
            DriveTransportTests.Memory memory=new DriveTransportTests.Memory();DriveRestClient c=DriveTransportTests.client(remote,memory);
            String key="drive-upload:"+DriveRestClient.sha256((c.folder+"/sample.json/"+DriveRestClient.sha256(raw)).getBytes(StandardCharsets.UTF_8));
            memory.put(key,"https://www.googleapis.com/upload/drive/v3/files?upload_id=qa_old_chunk");
            c.put(c.folder,"sample.json","image/jpeg",raw);yes(remote.starts==0&&remote.puts==3&&remote.reads==1&&remote.offset==raw.length);
        });
        check("contradictory range cannot skip unsent large bytes",()->{
            DriveRestClient c=client((method,url,h,b,max)->{
                if(method.equals("GET"))return DriveTransportTests.reply(200,"{\"files\":[]}");
                if(method.equals("POST"))return new DriveRestClient.Reply(200,new byte[0],Collections.singletonMap("Location","https://www.googleapis.com/upload/drive/v3/files?upload_id=qa_synthetic"));
                return new DriveRestClient.Reply(308,new byte[0],Collections.singletonMap("Range","bytes=0-2097151"));
            });reject(()->c.put(c.folder,"sample.json","image/jpeg",new byte[3*1024*1024]));
        });
        check("fresh unchanged inventory reuses hash-verified JSON, defensive copies",()->{
            Inventory remote=new Inventory();String id="qa_commit_cache_001";remote.add(id,"HausGarten-Commit-","{}".getBytes(StandardCharsets.UTF_8));DriveRestClient c=client(remote);
            byte[] first=c.readRoot(id,100);first[0]=0;c.refresh();yes(c.readRoot(id,100)[0]=='{');yes(remote.reads==1&&remote.lists==2);
            reject(()->c.readRoot(id,1));yes(remote.reads==1);
        });
        check("changed version, checksum or size always forces server read",()->{
            Inventory remote=new Inventory();String id="qa_commit_cache_002";JSONObject row=remote.add(id,"HausGarten-State-",new byte[]{1});DriveRestClient c=client(remote);c.readRoot(id,100);
            for(String field:new String[]{"version","md5Checksum","size"}){row.put(field,field.equals("md5Checksum")?"00000000000000000000000000000000":"2");c.refresh();c.readRoot(id,100);}
            yes(remote.reads==4);
        });
        check("missing proof never caches; modified hash-addressed bytes rejected",()->{
            Inventory remote=new Inventory();String id="qa_commit_cache_003";JSONObject row=remote.add(id,"HausGarten-Commit-",new byte[]{1});row.remove("version");DriveRestClient c=client(remote);
            c.readRoot(id,100);c.refresh();c.readRoot(id,100);yes(remote.reads==2);
            remote.bodies.put(id,new byte[]{2});c.refresh();reject(()->c.readRoot(id,100));
        });
        check("mutable legacy state and conditional counter reads always fresh",()->{
            Inventory remote=new Inventory();String id="qa_mutable_state_001";remote.add(id,"HausGarten-State-",new byte[]{1}).put("name","HausGarten-Laptop.json");DriveRestClient c=client(remote);
            c.readRoot(id,100);remote.bodies.put(id,new byte[]{2});yes(c.readRoot(id,100)[0]==2);c.readConditional(id);yes(remote.reads==3);
        });
        check("deleted files cannot be served from previous inventory",()->{
            Inventory remote=new Inventory();String id="qa_commit_cache_004";remote.add(id,"HausGarten-State-",new byte[]{1});DriveRestClient c=client(remote);c.readRoot(id,100);
            remote.absent=true;c.refresh();reject(()->c.readRoot(id,100));yes(remote.reads==2);
        });
        check("fresh inventory includes new competing commits",()->{
            Inventory remote=new Inventory();remote.add("qa_commit_head_001","HausGarten-Commit-",new byte[]{1});DriveRestClient c=client(remote);yes(c.list(c.folder).length()==1);
            remote.add("qa_commit_head_002","HausGarten-Commit-",new byte[]{2});c.refresh();yes(c.list(c.folder).length()==2);
            yes(c.readRoot("qa_commit_head_002",100)[0]==2);
        });
        check("cached recovery still rejects a newly observed chain fork",()->{
            JSONObject legacy=PixelDriveStateTests.state();PixelDriveStateTests.Memory memory=new PixelDriveStateTests.Memory();
            JSONObject initial=DrivePrimaryState.recover(legacy,"pixel-test",memory);memory.publish(initial,new JSONArray(),"qa_commit_root_001",1,"","");
            Inventory remote=new Inventory();for(Map.Entry<String,byte[]> entry:memory.data.entrySet())remote.add(entry.getKey(),entry.getKey().startsWith("state-")?"HausGarten-State-":"HausGarten-Commit-",entry.getValue());
            DriveRestClient c=client(remote);yes(DrivePrimaryState.recover(legacy,"pixel-test",storage(c)).getInt("drive_parent_sequence")==1);
            c.refresh();DrivePrimaryState.recover(legacy,"pixel-test",storage(c));yes(remote.reads==2);
            memory.publish(initial,new JSONArray(),"qa_commit_fork_001",1,"","");remote.add("qa_commit_fork_001","HausGarten-Commit-",memory.data.get("qa_commit_fork_001"));c.refresh();
            boolean blocked=false;try{DrivePrimaryState.recover(legacy,"pixel-test",storage(c));}catch(Exception conflict){blocked=true;}yes(blocked&&remote.reads==3);
        });
        check("LRU byte budget evicts old bodies; oversized entries not retained",()->{
            Inventory remote=new Inventory();DriveRestClient c=client(remote);
            for(int i=0;i<10;i++){byte[] b=new byte[512*1024];b[0]=(byte)i;String id="qa_cache_budget_00"+i;remote.add(id,"HausGarten-State-",b);c.refresh();c.readRoot(id,b.length);}
            c.readRoot("qa_cache_budget_000",1024*1024);yes(remote.reads==11);
            remote.add("qa_cache_oversized_001","HausGarten-State-",new byte[512*1024+1]);c.refresh();c.readRoot("qa_cache_oversized_001",1024*1024);c.readRoot("qa_cache_oversized_001",1024*1024);yes(remote.reads==13);
        });
        System.out.println("PASS "+passed+" Drive performance checks; synthetic only; upload data PUTs previous 8 -> 2, unchanged immutable body GETs 2 -> 1");
    }
}
