package at.hausgarten.service;
import org.json.*;
public final class LocalTestTombstoneTests {
 static int count;static void yes(boolean b){if(!b)throw new AssertionError("tombstone "+count);count++;}
 static JSONObject obj()throws Exception{return SyntheticBusinessData.object();}
 public static void main(String[] args)throws Exception{SyntheticBusinessData.verify();
  JSONObject original=obj().put("id","qa_test").put("name","[TEST] QA original").put("active",0),master=obj().put("customers",new JSONArray().put(original));
  JSONObject local=obj().put("purged",true).put("purge_action","qa_old_local").put("local_test_only",false).put("source_record_hash",OwnerJournalRules.version(original));
  JSONObject projected=new JSONObject(master.toString());yes(LocalTestTombstone.project(projected,"customer:qa_test",local));yes(projected.getJSONArray("customers").length()==0);JSONObject receipt=OwnerJournalRules.archives(projected).getJSONObject("customer:qa_test");yes(receipt.optBoolean("purged"));yes(!receipt.has("local_test_only")&&!receipt.has("source_record_hash"));yes(master.getJSONArray("customers").length()==1);
  JSONObject changed=new JSONObject(master.toString());changed.getJSONArray("customers").getJSONObject(0).put("notes","QA newer original");String before=PixelMcpWriteTools.hash(changed);yes(!LocalTestTombstone.project(changed,"customer:qa_test",local));yes(changed.getJSONArray("customers").length()==1);yes(before.equals(PixelMcpWriteTools.hash(changed)));yes(!OwnerJournalRules.archives(changed).has("customer:qa_test"));
  JSONObject remote=obj().put("customers",new JSONArray());JSONObject current=obj().put("purged",true).put("purge_action","qa_new_shared").put("archive_hash","qa_new_hash");OwnerJournalRules.archives(remote).put("customer:qa_test",current);String remoteHash=PixelMcpWriteTools.hash(remote);yes(!LocalTestTombstone.project(remote,"customer:qa_test",local));yes(PixelMcpWriteTools.hash(remote).equals(remoteHash));yes(OwnerJournalRules.archives(remote).getJSONObject("customer:qa_test").getString("purge_action").equals("qa_new_shared"));
  JSONObject archived=obj().put("customers",new JSONArray());JSONObject entry=obj().put("record",original).put("archived_at","qa_then");OwnerJournalRules.archives(archived).put("customer:qa_test",entry);yes(!LocalTestTombstone.project(archived,"customer:qa_test",local));local.put("source_archive_hash",OwnerJournalRules.version(entry));yes(LocalTestTombstone.project(archived,"customer:qa_test",local));
  JSONObject absent=obj().put("customers",new JSONArray());yes(LocalTestTombstone.project(absent,"customer:qa_test",obj().put("purged",true).put("purge_action","qa_never_shared").put("local_test_only",true)));yes(OwnerJournalRules.archives(absent).getJSONObject("customer:qa_test").getString("purge_action").equals("qa_never_shared"));
  System.out.println("PASS "+count+" server receipt precedence, changed-original visibility, private-metadata and local deletion protection checks");
 }
}
