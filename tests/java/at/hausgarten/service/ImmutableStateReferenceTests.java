package at.hausgarten.service;
import java.util.*;import org.json.*;
public final class ImmutableStateReferenceTests {
 static int count;static void yes(boolean b){if(!b)throw new AssertionError("state reference "+count);count++;}
 interface Task{void run()throws Exception;}
 static void rejects(Task task,String code)throws Exception{try{task.run();}catch(SyncIoFailure e){yes(e.code.equals(code));return;}throw new AssertionError("reference accepted");}
 public static void main(String[] args)throws Exception{SyntheticBusinessData.verify();
  byte[] original="qa_immutable_state".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=DriveRestClient.sha256(original),name="HausGarten-State-"+hash+".json";
  JSONObject manifest=SyntheticBusinessData.object().put("state_file_id","qa_old_provider_id").put("state_name",name).put("state_sha256",hash);
  Map<String,String> names=new LinkedHashMap<>();names.put("qa_new_provider_id",name);
  yes(Arrays.equals(original,ImmutableStateReference.read(names,manifest,id->original)));yes(manifest.getString("state_file_id").equals("qa_old_provider_id"));
  rejects(()->ImmutableStateReference.read(names,manifest,id->new byte[]{1}),"PUBLICATION_INVALID");
  rejects(()->ImmutableStateReference.read(new HashMap<>(),manifest,id->original),"REMOTE_STATE_REFERENCE_MISSING");
  JSONObject wrong=SyntheticBusinessData.object(manifest.toString()).put("state_name","qa_wrong.json");rejects(()->ImmutableStateReference.read(names,wrong,id->original),"PUBLICATION_INVALID");
  names.put("qa_old_provider_id",name);try{ImmutableStateReference.read(names,manifest,id->{throw new SecurityException("qa_denied");});throw new AssertionError("permission bypass");}catch(SecurityException expected){yes(true);}
  names.remove("qa_old_provider_id");names.put("qa_second_id",name);yes(Arrays.equals(original,ImmutableStateReference.read(names,manifest,id->original)));
  List<String> reads=new ArrayList<>();try{ImmutableStateReference.read(names,manifest,id->{reads.add(id);if(id.equals("qa_second_id"))throw new java.io.IOException("qa_interrupted");return original;});throw new AssertionError("duplicate read failure ignored");}catch(java.io.IOException expected){yes(reads.size()==2);}
  System.out.println("PASS "+count+" immutable state recovery, altered content, duplicate, missing, permission and interruption checks");
 }
}
