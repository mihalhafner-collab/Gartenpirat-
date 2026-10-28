package at.hausgarten.service;
import org.json.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;
/** Exact selection, original bytes, failure atomicity and chunked private recovery. */
public final class DeletionBackupTests {
 static int checks;static void yes(boolean b){checks++;if(!b)throw new AssertionError("deletion safety "+checks);}
 interface Task{void run()throws Exception;}static void rejects(Task task)throws Exception{try{task.run();}catch(Exception expected){yes(true);return;}throw new AssertionError("Expected safety failure");}
 public static void main(String[] ignored)throws Exception{SyntheticBusinessData.verify();Path temp=Files.createTempDirectory("qa_deletion_backup_");
  try{
   byte[] photo="QA synthetic original".getBytes(StandardCharsets.UTF_8),pdf="%PDF-1.4\nQA synthetic document\n%%EOF".getBytes(StandardCharsets.UTF_8);
   String ph=HttpTransport.sha256(photo),dh=HttpTransport.sha256(pdf);JSONObject s=TrashPurgeBatchTests.state();
   for(String id:new String[]{"qa_photo_1","qa_photo_2"})TrashPurgeBatchTests.archive(s,"photo",TrashWorkflowTests.photo(id).put("content_sha256",ph).put("storage_name",ph+".jpg").put("byte_size",photo.length));
   JSONObject offer=OffersAndPhotosTests.draft().put("status","draft").put("pdf_storage_name",dh+".pdf").put("pdf_sha256",dh).put("pdf_byte_size",pdf.length);TrashPurgeBatchTests.archive(s,"offer",offer);
   JSONArray selection=TrashPurgeBatch.preview(s).getJSONArray("entries");String before=PixelMcpWriteTools.hash(s);int[] reads={0};
   JSONObject result=DeletionBackupStore.capture(temp.toFile(),s,selection,"qa_delete_batch",(type,row)->{reads[0]++;return type.equals("photo")?photo:pdf;});JSONObject meta=result.getJSONObject("metadata");
   yes(reads[0]==2);yes(meta.getInt("protected_records")==3&&meta.getInt("original_files")==2);yes(before.equals(PixelMcpWriteTools.hash(s)));
   yes(!result.getBoolean("complete_device_backup"));yes(!meta.getBoolean("includes_credentials"));yes(DeletionBackupStore.inspect(temp.toFile(),meta).getInt("protected_records")==3);
   File zip=temp.resolve(meta.getString("storage_name")).toFile();yes(DeletionBackupStore.sha(zip).equals(meta.getString("sha256")));
   try(ZipFile archive=new ZipFile(zip)){yes(archive.size()==3);yes(Arrays.equals(archive.getInputStream(archive.getEntry("originals/"+ph+".jpg")).readAllBytes(),photo));yes(Arrays.equals(archive.getInputStream(archive.getEntry("originals/"+dh+".pdf")).readAllBytes(),pdf));}
   JSONObject args=SyntheticBusinessData.object().put("resource_id",result.getString("backup_id"));yes(DeletionBackupStore.chunk(temp.toFile(),meta,args).getString("mime_type").equals("application/zip"));
   ByteArrayOutputStream assembled=new ByteArrayOutputStream();int offset=0;while(true){JSONObject chunk=DeletionBackupStore.chunk(temp.toFile(),meta,new JSONObject(args.toString()).put("expected_sha256",meta.getString("sha256")).put("offset",offset).put("length",73));byte[] bytes=Base64.getDecoder().decode(chunk.getString("chunk_base64"));yes(HttpTransport.sha256(bytes).equals(chunk.getString("chunk_sha256")));assembled.write(bytes);offset=chunk.getInt("next_offset");if(chunk.getBoolean("eof"))break;}
   yes(HttpTransport.sha256(assembled.toByteArray()).equals(meta.getString("sha256")));
   rejects(()->DeletionBackupStore.chunk(temp.toFile(),meta,new JSONObject(args.toString()).put("expected_sha256","0".repeat(64)).put("offset",0)));
   rejects(()->DeletionBackupStore.chunk(temp.toFile(),meta,new JSONObject(args.toString()).put("expected_sha256",meta.getString("sha256")).put("offset",-1)));
   rejects(()->DeletionBackupStore.inspect(temp.toFile(),new JSONObject(meta.toString()).put("storage_name","../qa_escape.zip")));
   File bad=temp.resolve("qa_failed").toFile();rejects(()->DeletionBackupStore.capture(bad,s,selection,"qa_missing_original",(type,row)->{throw new IOException("QA missing original");}));yes(bad.list().length==0);yes(before.equals(PixelMcpWriteTools.hash(s)));
   rejects(()->DeletionBackupStore.capture(bad,s,selection,"qa_wrong_original",(type,row)->new byte[photo.length]));yes(bad.list().length==0);
   JSONArray stale=new JSONArray(selection.toString());stale.getJSONObject(0).put("expected_record_hash","0".repeat(64));rejects(()->DeletionBackupStore.capture(bad,s,stale,"qa_stale",(type,row)->photo));yes(bad.list().length==0);
   try(RandomAccessFile file=new RandomAccessFile(zip,"rw")){file.seek(20);file.write(99);}rejects(()->DeletionBackupStore.inspect(temp.toFile(),meta));
   OwnerApplicationTests.MemoryRepository repository=TrashBoundaryTests.fixture();repository.failProtection=true;int pending=repository.pendingActions().length();rejects(()->TrashBoundaryTests.empty(repository,"qa_backup_failure"));yes(repository.pendingActions().length()==pending&&repository.protections==1);yes(TrashPurgeBatch.preview(repository.effective()).getInt("total")==1);
   repository.failProtection=false;JSONObject saved=TrashBoundaryTests.empty(repository,"qa_backup_retry");yes(saved.has("deletion_backup_id"));yes(repository.protections==2&&repository.pendingActions().length()==1);
   for(String type:DeletionRules.ENTITIES)yes(DeletionBackupStore.importance(type,SyntheticBusinessData.object().put("notes","QA retained note")).length()>0);
   System.out.println("PASS "+checks+" exact deletion backup/original/dedup/readback/chunk/corruption/failure-before-enqueue checks (synthetic OS-temp only)");
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
