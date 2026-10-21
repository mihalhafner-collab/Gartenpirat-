package at.hausgarten.service;
import org.json.*;import java.util.*;
/** Read-only search/order integration over synthetic records and canonical relations. */
public final class SearchOrderingTests {
 static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("search "+checks);}
 public static void main(String[] ignored)throws Exception{SyntheticBusinessData.verify();JSONObject context=TrashWorkflowTests.state();JSONArray rows=new JSONArray();
  for(String[] c:new String[][]{{"qa_z","Zeta QA"},{"qa_10","Ähren 10 QA"},{"qa_2","Ähren 2 QA"},{"qa_m","Müller QA"},{"qa_inactive","Abel QA"}})rows.put(SyntheticBusinessData.object().put("id",c[0]).put("name",c[1]).put("active",!c[0].equals("qa_inactive")).put("street","QA Grauenrain").put("phone","+43 (664) 123-4567"));context.put("customers",rows);
  String original=PixelMcpWriteTools.hash(context);JSONArray sorted=SnapshotReadSupport.select(rows,SyntheticBusinessData.object().put("include_inactive",true).put("limit",100),"customer",context);
  yes(sorted.getJSONObject(0).getString("id").equals("qa_2"));yes(sorted.getJSONObject(1).getString("id").equals("qa_10"));yes(sorted.getJSONObject(4).getString("id").equals("qa_inactive"));
  for(String query:new String[]{"muller","mueller","MÜLLER grauenrain","Grauenrain Müller"})yes(SnapshotReadSupport.select(rows,SyntheticBusinessData.object().put("query",query),"customer",context).length()==1);
  yes(SnapshotReadSupport.select(rows,SyntheticBusinessData.object().put("query","436641234567"),"customer",context).length()==4);
  yes(SnapshotReadSupport.select(rows,SyntheticBusinessData.object(),"customer",context).length()==4);yes(original.equals(PixelMcpWriteTools.hash(context)));
  JSONArray orders=new JSONArray().put(SyntheticBusinessData.object().put("id","qa_job_10").put("customer_id","qa_m").put("title","QA Rasen 10")).put(SyntheticBusinessData.object().put("id","qa_job_2").put("customer_id","qa_m").put("title","QA Rasen 2"));context.put("orders",orders);
  JSONArray selected=SnapshotReadSupport.select(orders,SyntheticBusinessData.object().put("query","mueller rasen"),"order",context);yes(selected.length()==2);yes(selected.getJSONObject(0).getString("id").equals("qa_job_2"));
  rows.put(SyntheticBusinessData.object().put("id","qa_new").put("name","Aachen QA").put("active",1));yes(SnapshotReadSupport.select(rows,SyntheticBusinessData.object(),"customer",context).getJSONObject(0).getString("id").equals("qa_new"));
  Set<String> ids=new HashSet<>();for(int i=0;i<5;i++){JSONArray page=SnapshotReadSupport.select(rows,SyntheticBusinessData.object().put("offset",i).put("limit",1),"customer",context);yes(ids.add(page.getJSONObject(0).getString("id")));}yes(ids.size()==5);
  System.out.println("PASS "+checks+" German aliases/multiword/phone/natural-order/inactive/new-record/relation/pagination checks; read-only synthetic JVM");
 }
}
