package at.hausgarten.service;
import org.json.*;
public final class WorklogArchiveBatchTests {
 static int n;static void yes(boolean b){if(!b)throw new AssertionError("check "+n);n++;}
 interface Task{void run()throws Exception;}static void reject(Task t)throws Exception{try{t.run();}catch(IllegalArgumentException e){yes(true);return;}throw new AssertionError("Expected rejection");}
 static JSONObject state()throws Exception{JSONObject s=TrashWorkflowTests.state();s.put("worklogs",new JSONArray());for(int i=0;i<130;i++)s.getJSONArray("worklogs").put(SyntheticBusinessData.object().put("id","qa_w"+i).put("customer_id","qa_c").put("activity","Synthetic work "+i).put("hours",1).put("work_date","2026-07-31"));return s;}
 public static void main(String[] ignored)throws Exception{ SyntheticBusinessData.verify();
  JSONObject s=state();JSONArray ids=new JSONArray();for(int i=0;i<128;i++)ids.put("qa_w"+i);
  String before=PixelMcpWriteTools.hash(s);JSONObject preview=WorklogArchiveBatch.preview(s,ids);yes(preview.getBoolean("allowed"));yes(preview.getInt("count")==128);yes(before.equals(PixelMcpWriteTools.hash(s)));
  long start=System.nanoTime();JSONArray events=WorklogArchiveBatch.actions(s,ids,preview.getString("preview_hash"),"isolated-batch","t2");long millis=(System.nanoTime()-start)/1000000;yes(events.length()==128);yes(before.equals(PixelMcpWriteTools.hash(s)));
  yes(DriveJournalRules.apply(s,events).length()==0);yes(s.getJSONArray("worklogs").length()==2);yes(s.getJSONObject("owner_archive").length()==128);String applied=PixelMcpWriteTools.hash(s);yes(DriveJournalRules.apply(s,events).length()==0);yes(applied.equals(PixelMcpWriteTools.hash(s)));
  reject(()->WorklogArchiveBatch.preview(s,new JSONArray().put("w128").put("w128")));reject(()->WorklogArchiveBatch.preview(s,new JSONArray()));JSONArray tooMany=new JSONArray();for(int i=0;i<201;i++)tooMany.put("qa_w"+i);reject(()->WorklogArchiveBatch.preview(s,tooMany));
  JSONObject stale=state();JSONObject p=WorklogArchiveBatch.preview(stale,ids);stale.getJSONArray("worklogs").getJSONObject(127).put("notes","parallel");String guard=PixelMcpWriteTools.hash(stale);reject(()->WorklogArchiveBatch.actions(stale,ids,p.getString("preview_hash"),"stale","t3"));yes(guard.equals(PixelMcpWriteTools.hash(stale)));
  JSONObject photoRace=state();JSONObject q=WorklogArchiveBatch.preview(photoRace,ids);photoRace.getJSONArray("photos").put(TrashWorkflowTests.photo("photo").put("worklog_id","qa_w0"));reject(()->WorklogArchiveBatch.actions(photoRace,ids,q.getString("preview_hash"),"photo-race","t4"));
  JSONObject protectedState=state();protectedState.getJSONArray("invoices").put(SyntheticBusinessData.object().put("id","qa_protected").put("status","Gestellt").put("positions",new JSONArray().put(SyntheticBusinessData.object().put("worklog_id","qa_w127"))));JSONObject blocked=WorklogArchiveBatch.preview(protectedState,ids);yes(!blocked.getBoolean("allowed"));String protectedHash=PixelMcpWriteTools.hash(protectedState);reject(()->WorklogArchiveBatch.actions(protectedState,ids,blocked.getString("preview_hash"),"qa_protected","t4"));yes(protectedHash.equals(PixelMcpWriteTools.hash(protectedState)));
  PixelMcpOwnerTools.validate("archive_work_records",SyntheticBusinessData.object().put("request_id","isolated-valid-batch").put("work_record_ids",ids).put("expected_preview_hash",preview.getString("preview_hash")).put("confirmed",true));yes(true);
  reject(()->PixelMcpOwnerTools.validate("archive_work_records",SyntheticBusinessData.object().put("request_id","isolated-no-confirm").put("work_record_ids",ids).put("expected_preview_hash",preview.getString("preview_hash"))));
  System.out.println("PASS "+n+" batch archive selection/version/photo/financial/replay checks; 128 action preparation="+millis+" ms (host JVM, synthetic)");
 }
}
