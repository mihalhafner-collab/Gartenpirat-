package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Operative media-batch and HTTPS boundary with synthetic bytes, never a network/account. */
public final class DriveThroughputTests {
    interface Case{void run()throws Exception;}
    static int passed;static void yes(boolean value){if(!value)throw new AssertionError();}
    static void check(String name,Case test)throws Exception{test.run();passed++;System.out.println("PASS "+name);}
    static final String ROOT="qa_drive_root_001",PHOTOS="qa_photo_folder_001";
    static final class Memory implements LegacyDriveRestFixture.Checkpoints{
        final ConcurrentHashMap<String,String> values=new ConcurrentHashMap<>();
        public String get(String key){return values.getOrDefault(key,"");}public void put(String key,String value){values.put(key,value);}
    }
    static final class Upload {
        final String id,name;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Upload(String id,String name){this.id=id;this.name=name;}
    }
    static final class Remote implements LegacyDriveRestFixture.Wire {
        final Map<String,Upload> sessions=new ConcurrentHashMap<>(),stored=new ConcurrentHashMap<>();
        final Set<String> originalNames=ConcurrentHashMap.newKeySet(),corrupt=ConcurrentHashMap.newKeySet();
        final AtomicInteger directoryPosts=new AtomicInteger(),starts=new AtomicInteger(),reads=new AtomicInteger(),
            originalReads=new AtomicInteger(),active=new AtomicInteger(),maximum=new AtomicInteger();
        volatile boolean directory=true;CountDownLatch overlap;int expectedOriginals;
        LegacyDriveRestFixture client(Memory state){return new LegacyDriveRestFixture(ROOT,()->"qa_synthetic_token",this,state);}
        public LegacyDriveRestFixture.Reply send(String method,String url,Map<String,String> headers,byte[] body,int max)throws Exception{
            if(method.equals("GET")&&url.contains("files?q=")){
                String query=URLDecoder.decode(url.substring(url.indexOf("q=")+2).split("&",2)[0],"UTF-8");JSONArray files=new JSONArray();
                if(query.contains("'"+ROOT+"'")&&directory)files.put(new JSONObject().put("id",PHOTOS).put("name","HausGarten-Fotos").put("mimeType","application/vnd.google-apps.folder").put("version","1"));
                if(query.contains("'"+PHOTOS+"'"))for(Upload upload:stored.values())files.put(new JSONObject().put("id",upload.id).put("name",upload.name).put("version","1").put("size",upload.bytes.size()));
                return DriveTransportTests.reply(200,new JSONObject().put("files",files).toString());
            }
            if(method.equals("POST")&&!url.contains("uploadType=")){directoryPosts.incrementAndGet();directory=true;return DriveTransportTests.reply(200,"{\"id\":\""+PHOTOS+"\"}");}
            if(method.equals("POST")){
                String name=new JSONObject(new String(body,StandardCharsets.UTF_8)).getString("name");
                if(!originalNames.contains(name))yes(originalReads.get()>=expectedOriginals);
                String id="qa_upload_file_"+starts.incrementAndGet();Upload upload=new Upload(id,name);sessions.put(id,upload);
                return new LegacyDriveRestFixture.Reply(200,new byte[0],Collections.singletonMap("Location","https://www.googleapis.com/upload/drive/v3/files?upload_id="+id));
            }
            if(method.equals("PUT")){
                Upload upload=sessions.get(url.substring(url.indexOf("upload_id=")+10));yes(upload!=null);
                if(body.length==0)return new LegacyDriveRestFixture.Reply(308,new byte[0],upload.bytes.size()==0?Collections.emptyMap():Collections.singletonMap("Range","bytes=0-"+(upload.bytes.size()-1)));
                int count=active.incrementAndGet();maximum.accumulateAndGet(count,Math::max);
                try{
                    if(overlap!=null&&originalNames.contains(upload.name)){overlap.countDown();yes(overlap.await(3,TimeUnit.SECONDS));}
                    Thread.sleep(15);upload.bytes.write(body);String range=headers.get("Content-Range");int total=Integer.parseInt(range.substring(range.lastIndexOf('/')+1));
                    if(upload.bytes.size()==total){stored.put(upload.id,upload);return DriveTransportTests.reply(200,new JSONObject().put("id",upload.id).put("name",upload.name).toString());}
                    return new LegacyDriveRestFixture.Reply(308,new byte[0],Collections.singletonMap("Range","bytes=0-"+(upload.bytes.size()-1)));
                }finally{active.decrementAndGet();}
            }
            if(method.equals("GET")){
                String id=url.substring(url.lastIndexOf('/')+1).split("\\?",2)[0];Upload upload=stored.get(id);yes(upload!=null);reads.incrementAndGet();
                byte[] raw=upload.bytes.toByteArray();yes(raw.length<=max);if(originalNames.contains(upload.name))originalReads.incrementAndGet();
                if(corrupt.contains(upload.name))raw[0]^=1;
                return new LegacyDriveRestFixture.Reply(200,raw,Collections.emptyMap());
            }
            throw new AssertionError("Unexpected synthetic transport call");
        }
    }
    static final class Source implements DriveMediaBatch.Source {
        final Map<String,byte[]> bytes=new HashMap<>();final AtomicInteger originalReads=new AtomicInteger();
        JSONObject photo(Remote remote,String id,int value,boolean thumb)throws Exception{
            byte[] raw=new byte[256*1024];Arrays.fill(raw,(byte)value);String hash=LegacyDriveRestFixture.sha256(raw),name=hash+".jpg";bytes.put(name,raw);remote.originalNames.add(name);
            JSONObject metadata=new JSONObject().put("photo_id",id).put("content_sha256",hash).put("storage_name",name).put("byte_size",raw.length).put("mime_type","image/jpeg");
            if(thumb){byte[] preview="qa_shared_thumbnail".getBytes(StandardCharsets.UTF_8);String thumbnail=LegacyDriveRestFixture.sha256(preview)+".jpg";bytes.put(thumbnail,preview);metadata.put("thumbnail_storage_name",thumbnail);}
            return metadata;
        }
        public byte[] original(JSONObject metadata){originalReads.incrementAndGet();return bytes.get(metadata.optString("storage_name")).clone();}
        public void verify(byte[] raw,JSONObject metadata)throws Exception{if(raw.length!=metadata.getLong("byte_size")||!LegacyDriveRestFixture.sha256(raw).equals(metadata.getString("content_sha256")))throw new IOException("Synthetic metadata mismatch");}
        public byte[] thumbnail(String name){return bytes.get(name).clone();}
    }
    public static void main(String[] args)throws Exception{
        SyntheticBusinessData.verify();
        check("three actual immutable uploads overlap, all readbacks finish before previews",()->{
            Remote remote=new Remote();remote.directory=false;remote.overlap=new CountDownLatch(3);remote.expectedOriginals=3;Source source=new Source();JSONArray batch=new JSONArray();
            for(int i=0;i<3;i++)batch.put(source.photo(remote,"qa_photo_"+i,i,true));Memory checkpoints=new Memory();
            Map<String,Exception> failures=DriveMediaBatch.transfer(remote.client(checkpoints),batch,source);
            yes(failures.isEmpty()&&remote.maximum.get()==3&&remote.active.get()==0&&remote.directoryPosts.get()==1);
            yes(remote.starts.get()==4&&remote.reads.get()==4&&source.originalReads.get()==3);
            yes(checkpoints.values.entrySet().stream().filter(e->e.getKey().startsWith("drive-upload:")).allMatch(e->e.getValue().isEmpty()));
        });
        check("one corrupt readback blocks only that photo and retains its resumable checkpoint",()->{
            Remote remote=new Remote();remote.expectedOriginals=3;Source source=new Source();JSONArray batch=new JSONArray();
            for(int i=0;i<3;i++)batch.put(source.photo(remote,"qa_photo_"+i,i,true));remote.corrupt.add(batch.getJSONObject(1).getString("storage_name"));Memory memory=new Memory();
            Map<String,Exception> failures=DriveMediaBatch.transfer(remote.client(memory),batch,source);
            yes(failures.size()==1&&failures.containsKey("qa_photo_1")&&remote.starts.get()==4&&remote.active.get()==0);
            yes(memory.values.entrySet().stream().filter(e->e.getKey().startsWith("drive-upload:")&&!e.getValue().isEmpty()).count()==1);
        });
        check("duplicate originals and previews uploaded once, all references validated",()->{
            Remote remote=new Remote();remote.expectedOriginals=1;Source source=new Source();JSONObject first=source.photo(remote,"qa_first",1,true),second=new JSONObject(first.toString()).put("photo_id","qa_second");
            yes(DriveMediaBatch.transfer(remote.client(new Memory()),new JSONArray().put(first).put(second),source).isEmpty());yes(remote.starts.get()==2&&source.originalReads.get()==1);
            Remote bad=new Remote();Source badSource=new Source();JSONObject good=badSource.photo(bad,"qa_good",2,false),mismatch=new JSONObject(good.toString()).put("photo_id","qa_bad").put("byte_size",1);
            yes(DriveMediaBatch.transfer(bad.client(new Memory()),new JSONArray().put(good).put(mismatch),badSource).size()==2&&bad.starts.get()==0);
        });
        check("worker results preserve input order and file errors are isolated",()->{
            CountDownLatch second=new CountDownLatch(1);List<Callable<Integer>> tasks=Arrays.asList(()->{yes(second.await(2,TimeUnit.SECONDS));return 1;},()->{second.countDown();return 2;},()->{throw new IOException("qa_failure");});
            List<DriveTransferPool.Result<Integer>> result=DriveTransferPool.run(tasks,()->{});yes(result.get(0).value==1&&result.get(1).value==2&&result.get(2).error instanceof IOException);
        });
        check("interruption cancels and joins workers before the caller returns",()->{
            CountDownLatch started=new CountDownLatch(3);AtomicInteger live=new AtomicInteger();AtomicBoolean restored=new AtomicBoolean(),failed=new AtomicBoolean();
            List<Callable<Void>> tasks=new ArrayList<>();for(int i=0;i<3;i++)tasks.add(()->{live.incrementAndGet();started.countDown();try{Thread.sleep(10000);return null;}finally{live.decrementAndGet();}});
            Thread caller=new Thread(()->{try{DriveTransferPool.run(tasks,()->{});}catch(InterruptedIOException stopped){failed.set(true);restored.set(Thread.currentThread().isInterrupted());}catch(Exception error){throw new RuntimeException(error);}});
            caller.start();yes(started.await(2,TimeUnit.SECONDS));caller.interrupt();caller.join(3000);yes(!caller.isAlive()&&failed.get()&&restored.get()&&live.get()==0);
        });
        check("cancel actual resumable batch closes only its active requests and retains all checkpoints",()->{
            Remote remote=new Remote();Source source=new Source();JSONArray batch=new JSONArray();for(int i=0;i<6;i++)batch.put(source.photo(remote,"qa_cancel_photo_"+i,i,false));
            CountDownLatch started=new CountDownLatch(3);ConcurrentHashMap<Thread,CountDownLatch> gates=new ConcurrentHashMap<>();AtomicInteger closed=new AtomicInteger();
            LegacyDriveRestFixture.Wire wire=new LegacyDriveRestFixture.Wire(){
                public LegacyDriveRestFixture.Reply send(String method,String url,Map<String,String> h,byte[] b,int max)throws Exception{
                    if(method.equals("PUT")&&b.length>0){CountDownLatch gate=new CountDownLatch(1);gates.put(Thread.currentThread(),gate);started.countDown();
                        try{boolean released=false;while(!released)try{released=gate.await(3,TimeUnit.SECONDS);if(!released)throw new IOException("Synthetic cancellation timeout");}catch(InterruptedException stopped){/* Model a socket requiring disconnect, not Thread.interrupt alone. */}
                            throw new InterruptedIOException("Synthetic socket closed");
                        }finally{gates.remove(Thread.currentThread());}}
                    return remote.send(method,url,h,b,max);
                }
                public void cancel(Thread thread){CountDownLatch gate=gates.get(thread);if(gate!=null){closed.incrementAndGet();gate.countDown();}}
            };
            Memory memory=new Memory();LegacyDriveRestFixture client=new LegacyDriveRestFixture(ROOT,()->"qa_token",wire,memory);AtomicBoolean aborted=new AtomicBoolean();
            Thread caller=new Thread(()->{try{DriveMediaBatch.transfer(client,batch,source);}catch(InterruptedIOException stopped){aborted.set(Thread.currentThread().isInterrupted());}catch(Exception failed){throw new RuntimeException(failed);}});
            caller.start();yes(started.await(3,TimeUnit.SECONDS));caller.interrupt();caller.join(5000);
            yes(!caller.isAlive()&&aborted.get()&&closed.get()==3&&gates.isEmpty()&&remote.reads.get()==0&&remote.starts.get()==3);
            yes(memory.values.entrySet().stream().filter(e->e.getKey().startsWith("drive-upload:")&&!e.getValue().isEmpty()).count()==3);
        });
        check("global HTTP bound admits six independent requests, never more",()->{
            AtomicInteger active=new AtomicInteger(),max=new AtomicInteger();CountDownLatch firstSix=new CountDownLatch(6);ExecutorService callers=Executors.newFixedThreadPool(10);List<Future<?>> futures=new ArrayList<>();
            LegacyDriveRestFixture.Wire wire=(m,u,h,b,limit)->{int live=active.incrementAndGet();max.accumulateAndGet(live,Math::max);try{firstSix.countDown();yes(firstSix.await(3,TimeUnit.SECONDS));Thread.sleep(10);return new LegacyDriveRestFixture.Reply(200,new byte[]{1},Collections.emptyMap());}finally{active.decrementAndGet();}};
            try{for(int i=0;i<10;i++)futures.add(callers.submit(()->{try{new LegacyDriveRestFixture(ROOT,()->"qa_token",wire,new Memory()).read("qa_read_file_001",10);}catch(Exception error){throw new RuntimeException(error);}}));for(Future<?> future:futures)future.get(5,TimeUnit.SECONDS);yes(max.get()==6&&active.get()==0);}finally{callers.shutdownNow();}
        });
        check("Retry-After stops queued siblings without another wire request",()->{
            AtomicInteger calls=new AtomicInteger();LegacyDriveRestFixture parent=new LegacyDriveRestFixture(ROOT,()->"qa_token",(m,u,h,b,limit)->{calls.incrementAndGet();return new LegacyDriveRestFixture.Reply(429,new byte[0],Collections.singletonMap("Retry-After","60"));},new Memory());
            LegacyDriveRestFixture child=parent.fork(PHOTOS,new JSONArray());
            for(LegacyDriveRestFixture client:Arrays.asList(child,parent))try{client.read("qa_read_file_001",10);throw new AssertionError();}catch(LegacyDriveRestFixture.Failure failure){yes(failure.code.equals("TEMPORARY_UNAVAILABLE")&&failure.retryAfter>59000);}
            yes(calls.get()==1);
        });
        check("worker cache refresh/mutation cannot corrupt the parent inventory",()->{
            JSONArray rows=new JSONArray().put(new JSONObject().put("id","qa_file_id_001").put("name","qa-name").put("version","1"));
            LegacyDriveRestFixture parent=new LegacyDriveRestFixture(ROOT,()->"qa_token",(m,u,h,b,max)->DriveTransportTests.reply(200,new JSONObject().put("files",rows).toString()),new Memory());parent.list(PHOTOS);
            LegacyDriveRestFixture child=parent.fork(PHOTOS,rows);child.unique(PHOTOS,"qa-name").put("version","2");child.refresh();yes(parent.unique(PHOTOS,"qa-name").getString("version").equals("1"));
        });
        check("inventory fingerprints are order-independent and observe modifications/removals",()->{
            AtomicReference<JSONArray> listing=new AtomicReference<>(new JSONArray().put(new JSONObject().put("id","qa_file_a").put("name","qa, = :\n").put("version","1")).put(new JSONObject().put("id","qa_file_b").put("name","qa-b").put("version","2")));
            LegacyDriveRestFixture c=new LegacyDriveRestFixture(ROOT,()->"qa_token",(m,u,h,b,max)->DriveTransportTests.reply(200,new JSONObject().put("files",listing.get()).toString()),new Memory());String first=c.inventoryFingerprint();
            JSONArray previous=listing.get();listing.set(new JSONArray().put(previous.getJSONObject(1)).put(previous.getJSONObject(0)));c.refresh();yes(first.equals(c.inventoryFingerprint()));
            listing.get().getJSONObject(0).put("version","3");c.refresh();yes(!first.equals(c.inventoryFingerprint()));listing.get().getJSONObject(0).remove("version");c.refresh();yes(c.inventoryFingerprint().isEmpty());
        });
        check("new changes cannot postpone an already due sync; Undo and Retry-After remain floors",()->{
            SyncWakeup.Window window=new SyncWakeup.Window();yes(window.request(1000,0,0)==1000);for(int i=1;i<100;i++)yes(window.request(1000+i,0,0)==1000);
            yes(window.request(1100,0,9000)==9000);yes(window.request(1200,0,0)==1200);yes(window.request(1300,0,30000)==30000);window.clear();yes(window.due()==Long.MAX_VALUE);
        });
        check("unchanged metadata never authorizes a fabricated confirmation or suppresses pending work",()->{
            yes(!SyncWakeup.fullCheck("qa_same","qa_same",false,false,false,0));
            yes(SyncWakeup.fullCheck("qa_same","qa_same",true,false,false,0));yes(SyncWakeup.fullCheck("qa_same","qa_same",false,true,false,0));yes(SyncWakeup.fullCheck("qa_same","qa_same",false,false,true,0));
            yes(SyncWakeup.fullCheck("qa_new","qa_same",false,false,false,0));yes(SyncWakeup.fullCheck("","",false,false,false,0));yes(SyncWakeup.fullCheck("qa_same","qa_same",false,false,false,SyncWakeup.FULL_CHECK_MS));
        });
        check("media budget permits twelve files and preserves a large original without starvation",()->{
            JSONArray rows=new JSONArray();for(int i=0;i<20;i++)rows.put(new JSONObject().put("content_sha256",String.format("%064x",i)).put("byte_size",2*1024*1024));yes(SyncMediaPlan.select(rows).size()==12);
            rows.getJSONObject(0).put("byte_size",20*1024*1024);Set<String> selected=SyncMediaPlan.select(rows);long total=0;for(int i=0;i<rows.length();i++)if(selected.contains(rows.getJSONObject(i).getString("content_sha256")))total+=rows.getJSONObject(i).getLong("byte_size");yes(selected.contains("0".repeat(64))&&total<=32L*1024*1024);
        });
        check("cold commit recovery overlaps three reads, validates the complete chain before use",()->{
            JSONObject legacy=PixelDriveStateTests.state();PixelDriveStateTests.Memory memory=new PixelDriveStateTests.Memory();JSONObject initial=DrivePrimaryState.recover(legacy,"pixel-test",memory);
            String parent="",previous="";for(int i=1;i<=3;i++){String id="qa_commit_chain_00"+i;memory.publish(initial,new JSONArray(),id,i,parent,previous);parent=id;previous=LegacyDriveRestFixture.sha256(memory.data.get("state-"+id));}
            JSONArray listing=new JSONArray();for(Map.Entry<String,byte[]> file:memory.data.entrySet()){String name=memory.names.getOrDefault(file.getKey(),"HausGarten-State-"+LegacyDriveRestFixture.sha256(file.getValue())+".json");listing.put(new JSONObject().put("id",file.getKey()).put("name",name).put("size",file.getValue().length).put("version","1").put("md5Checksum","a".repeat(32)));}
            CountDownLatch three=new CountDownLatch(3);AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger(),reads=new AtomicInteger();
            LegacyDriveRestFixture c=new LegacyDriveRestFixture(ROOT,()->"qa_token",(m,u,h,b,max)->{
                if(u.contains("files?q="))return DriveTransportTests.reply(200,new JSONObject().put("files",listing).toString());
                String id=u.substring(u.lastIndexOf('/')+1).split("\\?",2)[0];reads.incrementAndGet();
                if(memory.names.containsKey(id)){int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);try{three.countDown();yes(three.await(3,TimeUnit.SECONDS));Thread.sleep(10);}finally{active.decrementAndGet();}}
                return new LegacyDriveRestFixture.Reply(200,memory.data.get(id),Collections.emptyMap());
            },new Memory());
            DrivePrimaryState.Storage storage=new DrivePrimaryState.Storage(){public Map<String,String> commits(){return memory.names;}public byte[] read(String id)throws Exception{return c.readRoot(id,LegacyDriveRestFixture.MAX_JSON);}public void readCommits(Map<String,String> names,DrivePrimaryState.CommitVisitor visitor)throws Exception{c.readCommitFiles(names,visitor);}};
            yes(DrivePrimaryState.recover(legacy,"pixel-test",storage).getInt("drive_parent_sequence")==3);yes(maximum.get()==3&&reads.get()==4&&active.get()==0);
            c.refresh();yes(DrivePrimaryState.recover(legacy,"pixel-test",storage).getInt("drive_parent_sequence")==3);yes(reads.get()==4);
            memory.data.get("qa_commit_chain_002")[0]^=1;for(int i=0;i<listing.length();i++)if(listing.getJSONObject(i).getString("id").equals("qa_commit_chain_002"))listing.getJSONObject(i).put("version","2");c.refresh();
            boolean rejected=false;try{DrivePrimaryState.recover(legacy,"pixel-test",storage);}catch(Exception invalid){rejected=true;}yes(rejected&&active.get()==0);
        });
        check("fetched photo cache guard recognizes purge and retains shared live references",()->{
            JSONObject state=PixelDriveStateTests.state();String name="a".repeat(64)+".jpg";JSONObject photo=new JSONObject().put("id","qa_photo_cache").put("storage_name",name);
            state.put("photos",new JSONArray().put(photo));yes(TrashRules.referenced(state,new JSONArray(),name));
            state.put("photos",new JSONArray());state.put("owner_archive",new JSONObject().put("photo:qa_photo_cache",new JSONObject().put("record",photo).put("purged",true)));
            yes(!TrashRules.referenced(state,new JSONArray(),name));state.getJSONObject("owner_archive").getJSONObject("photo:qa_photo_cache").put("purged",false);yes(TrashRules.referenced(state,new JSONArray(),name));
            state.getJSONObject("owner_archive").getJSONObject("photo:qa_photo_cache").put("purged",true);state.getJSONArray("photos").put(new JSONObject(photo.toString()).put("id","qa_shared_photo"));yes(TrashRules.referenced(state,new JSONArray(),name));
        });
        check("failed originals cannot starve later files; customer/work changes get one short wave",()->{
            JSONArray rows=new JSONArray();for(int i=0;i<30;i++)rows.put(new JSONObject().put("content_sha256",String.format("%064x",i)).put("byte_size",1024));
            Set<String> first=SyncMediaPlan.select(rows,"",false);String last="";for(String hash:first)last=hash;
            Set<String> second=SyncMediaPlan.select(rows,last,false);yes(first.size()==12&&second.size()==12&&Collections.disjoint(first,second));
            Set<String> urgent=SyncMediaPlan.select(rows,"",true);yes(urgent.size()==3&&urgent.iterator().next().equals("0".repeat(64)));
            yes(SyncMediaPlan.select(new JSONArray(),last,true).isEmpty());yes(SyncMediaPlan.select(rows,"qa_missing_cursor",false).equals(first));
            JSONArray mixed=new JSONArray();for(int i=0;i<14;i++)mixed.put(new JSONObject().put("content_sha256",String.format("%064x",i)).put("byte_size",(i<2?20:1)*1024*1024));
            String cursor="";Set<String> seen=new HashSet<>();for(int i=0;i<3;i++){Set<String> wave=SyncMediaPlan.select(mixed,cursor,false);seen.addAll(wave);cursor=SyncMediaPlan.nextCursor(mixed,cursor,wave);}yes(seen.size()==14);
        });
        check("fully drained HTTP streams permit keep-alive; bounded-read failure still disconnects",()->{
            AtomicBoolean disconnected=new AtomicBoolean(),streamClosed=new AtomicBoolean();
            AtomicInteger opens=new AtomicInteger();LegacyDriveRestFixture.HttpWire wire=new LegacyDriveRestFixture.HttpWire(url->{opens.incrementAndGet();return new java.net.HttpURLConnection(url){
                    public void connect(){}public void disconnect(){disconnected.set(true);}public boolean usingProxy(){return false;}
                    public int getResponseCode(){return 200;}public Map<String,List<String>> getHeaderFields(){return Collections.emptyMap();}
                    public InputStream getInputStream(){return new ByteArrayInputStream(new byte[]{1,2,3}){public void close()throws IOException{streamClosed.set(true);super.close();}};}
                };
            });
            yes(wire.send("GET","https://www.googleapis.com/drive/v3/files/qa_keepalive",Collections.emptyMap(),null,10).body.length==3);
            yes(streamClosed.get()&&!disconnected.get());streamClosed.set(false);
            boolean rejected=false;try{wire.send("GET","https://www.googleapis.com/drive/v3/files/qa_keepalive",Collections.emptyMap(),null,1);}catch(LegacyDriveRestFixture.Failure limited){rejected=limited.code.equals("PAYLOAD_LIMIT");}
            yes(rejected&&streamClosed.get()&&disconnected.get());
            boolean untrusted=false;try{wire.send("GET","https://qa.example.invalid/",Collections.emptyMap(),null,10);}catch(SecurityException expected){untrusted=true;}yes(untrusted&&opens.get()==2);
        });
        System.out.println("PASS "+passed+" throughput/cancellation/integrity/scheduling checks; actual synthetic media concurrency=3, HTTP ceiling=6; no real mobile timing claim");
    }
}
