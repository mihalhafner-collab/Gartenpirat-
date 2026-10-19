package at.hausgarten.service;
import org.json.*;
import java.util.*;

public final class OwnerToolsTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("owner domain assertion "+checks);}
    interface Check{void run()throws Exception;}
    static void bad(Check check)throws Exception{boolean rejected=false;try{check.run();}catch(Exception e){rejected=true;}yes(rejected);}
    static JSONObject snapshot()throws Exception{return SyntheticBusinessData.object("{customers:[{id:'qa_c',name:'Synthetischer Kunde',active:1,billing_mode:'direct',updated_at:'t1'}],orders:[],worklogs:[],invoices:[],photos:[]}");}
    static JSONObject action(String id,String kind,JSONObject p)throws Exception{return SyntheticBusinessData.object().put("client_id",id).put("kind",kind).put("payload",p).put("created_at","2026-10-18T12:00:00Z");}
    public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
        PixelMcpReadTools readOnly=new PixelMcpReadTools(()->SyntheticBusinessData.object().put("master",snapshot()).put("pending",new JSONArray()));
        Set<String> names=new HashSet<>();JSONArray advertised=readOnly.tools();for(int i=0;i<advertised.length();i++){JSONObject t=advertised.getJSONObject(i);yes(names.add(t.getString("name")));yes(t.getJSONObject("annotations").getBoolean("readOnlyHint"));}
        for(String name:PixelMcpOwnerTools.WRITES){bad(()->readOnly.call(name,SyntheticBusinessData.object()));bad(()->PixelMcpOwnerTools.validate(name,SyntheticBusinessData.object().put("request_id","valid-request").put("arbitrary_sql","delete")));}
        for(String name:PixelMcpOwnerTools.READS)yes(names.contains(name));
        JSONObject input=SyntheticBusinessData.object().put("request_id","file-request").put("byte_size",4).put("sha256","a".repeat(64)).put("mime_type","image/jpeg");PixelMcpOwnerTools.validate("begin_file_upload",input);yes(true);bad(()->PixelMcpOwnerTools.validate("begin_file_upload",SyntheticBusinessData.object(input.toString()).put("mime_type","application/x-executable")));
        bad(()->PixelMcpOwnerTools.validate("read_file_chunk",SyntheticBusinessData.object().put("resource_type","photo").put("resource_id","qa_p").put("offset",0).put("length",500000).put("expected_sha256","a".repeat(64))));
        byte[] abc=new byte[]{1,2,3};String encoded=Base64.getEncoder().encodeToString(abc),hash=McpFileTransfer.sha(abc);
        yes(Arrays.equals(abc,McpFileTransfer.append(new byte[0],encoded,0,3,hash)));bad(()->McpFileTransfer.append(new byte[0],encoded,1,3,hash));bad(()->McpFileTransfer.append(new byte[0],encoded,0,2,hash));bad(()->McpFileTransfer.append(new byte[0],encoded,0,3,"a".repeat(64)));yes(abc.length==3);
        JSONObject s=snapshot(),customer=s.getJSONArray("customers").getJSONObject(0);JSONObject p=SyntheticBusinessData.object().put("entity","customer").put("id","qa_c").put("expected_record_hash",OwnerJournalRules.version(customer)).put("updated_at","t2");JSONObject archived=action("archive-c","archive",p);
        yes(DriveJournalRules.apply(s,new JSONArray().put(archived)).length()==0);yes(s.getJSONArray("customers").getJSONObject(0).getInt("active")==0);yes(DriveJournalRules.apply(s,new JSONArray().put(archived)).length()==0);
        JSONObject entry=s.getJSONObject("owner_archive").getJSONObject("customer:qa_c"),restore=action("restore-c","restore",SyntheticBusinessData.object().put("entity","customer").put("id","qa_c").put("expected_record_hash",OwnerJournalRules.version(entry)));
        yes(DriveJournalRules.apply(s,new JSONArray().put(archived).put(restore)).length()==0);yes(s.getJSONArray("customers").getJSONObject(0).getInt("active")==1);yes(s.getJSONObject("owner_archive").length()==0);
        JSONObject c=snapshot(),original=c.getJSONArray("customers").getJSONObject(0);JSONObject collision=action("collision","customer",SyntheticBusinessData.object(original.toString()).put("name","Andere Angabe").put("_mcp_create",true));yes(DriveJournalRules.apply(c,new JSONArray().put(collision)).length()==1);
        JSONObject state=SyntheticBusinessData.object().put("master",c).put("pending",new JSONArray().put(collision));JSONObject conflict=PixelMcpOwnerTools.conflicts(state,SyntheticBusinessData.object()).getJSONArray("conflicts").getJSONObject(0);yes(conflict.has("action_hash")&&conflict.has("current_hash"));
        JSONObject decision=SyntheticBusinessData.object().put("client_id","collision").put("expected_action_hash",conflict.getString("action_hash")).put("expected_current_hash",conflict.getString("current_hash")).put("resolution","keep_current");
        JSONObject resolve=action("resolve","conflict_resolution",decision);yes(DriveJournalRules.apply(c,new JSONArray().put(collision).put(resolve)).length()==0);yes(c.getJSONObject("owner_conflict_history").getJSONObject("collision").getJSONObject("original_action").getJSONObject("payload").getString("name").equals("Andere Angabe"));yes(c.getJSONArray("customers").getJSONObject(0).getString("name").equals("Synthetischer Kunde"));yes(!DriveJournalRules.ids(c.optJSONArray("accepted_ids")).contains("collision"));yes(DriveJournalRules.apply(c,new JSONArray().put(collision).put(resolve)).length()==0);
        JSONObject draft=SyntheticBusinessData.object("{id:'qa_i',customer_id:'qa_c',invoice_date:'2026-10-18',positions:[{title:'Pflege',quantity:3,price:0.335}],status:'Entwurf'}");InvoiceDraftRules.normalize(draft,null,snapshot());yes(draft.getDouble("amount")==1.01);bad(()->InvoiceDraftRules.normalize(draft,SyntheticBusinessData.object(draft.toString()).put("issued_at","issued"),snapshot()));
        JSONObject settingsSnapshot=snapshot().put("business_settings",SyntheticBusinessData.object().put("owner_name","QA"));String beforeSettings=PixelMcpWriteTools.hash(settingsSnapshot.getJSONObject("business_settings"));
        JSONObject settingsAction=action("settings","business_settings",SyntheticBusinessData.object().put("expected_record_hash",beforeSettings).put("updates",SyntheticBusinessData.object().put("owner_name","QA Neu").put("invoice_issuer_confirmed",true)));
        yes(DriveJournalRules.apply(settingsSnapshot,new JSONArray().put(settingsAction)).length()==0);yes(settingsSnapshot.getJSONObject("business_settings").getString("invoice_issuer_confirmed").equals("1"));
        bad(()->PixelMcpOwnerTools.validate("update_business_settings",SyntheticBusinessData.object().put("request_id","secret-setting").put("expected_record_hash",beforeSettings).put("updates",SyntheticBusinessData.object().put("api_key","synthetic-not-allowed"))));
        JSONObject photoSnapshot=snapshot().put("drive_journal_errors",new JSONArray().put(SyntheticBusinessData.object().put("client_id","old-photo").put("kind","photo").put("code","WRITE_CONFLICT")));
        JSONObject oldPhoto=action("old-photo","photo",SyntheticBusinessData.object().put("id","qa_p").put("customer_id","qa_c").put("data","data:image/jpeg;base64,SYNTHETIC_ORIGINAL"));
        JSONArray review=DriveJournalRules.reviewActions(photoSnapshot,new JSONArray().put(oldPhoto));yes(review.length()==0);yes(!photoSnapshot.toString().contains("SYNTHETIC_ORIGINAL"));
        JSONObject oldPhotoConflict=PixelMcpOwnerTools.conflicts(SyntheticBusinessData.object().put("master",photoSnapshot).put("pending",new JSONArray().put(oldPhoto)),SyntheticBusinessData.object()).getJSONArray("conflicts").getJSONObject(0);yes(!oldPhotoConflict.getBoolean("resolution_available")&&!oldPhotoConflict.has("action_hash"));
        System.out.println("CROSS_OWNER_HASH "+OwnerJournalRules.version(SyntheticBusinessData.object("{id:'qa_c',name:'QA',active:0,custom_fields:'{\"A\":true}',updated_at:'t2'}")));
        System.out.println("PASS "+checks+" owner schemas, access gates, file blocks, archives and conflict receipts (synthetic host data)");
    }
}
