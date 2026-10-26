package at.hausgarten.service;
import org.json.*;
import java.util.*;

public final class SyncCommitRulesTests {
    static int checks;static void yes(boolean b){checks++;if(!b)throw new AssertionError("commit rules "+checks);}
    public static void main(String[] args)throws Exception{
        SyntheticBusinessData.verify();
        for(boolean throwsStatus:new boolean[]{false,true}){
            JSONObject snapshot=new JSONObject().put("accepted_ids",new JSONArray().put("qa_accepted")).put("drive_applied_ids",new JSONArray().put("qa_applied"))
                .put("rejected_invoice_ids",new JSONArray().put("qa_rejected")).put("drive_resolved_actions",new JSONObject().put("qa_resolved",true));
            Set<String> queue=new HashSet<>(Arrays.asList("qa_accepted","qa_applied","qa_rejected","qa_resolved","qa_unconfirmed"));List<String> calls=new ArrayList<>();String[] durable={""};
            SyncCommitRules.Result result=SyncCommitRules.commit(snapshot,new SyncCommitRules.Storage(){
                public void commitSnapshot(JSONObject saved,Set<String> ids){calls.add("snapshot_and_ack");durable[0]=saved.toString();queue.removeAll(ids);}
                public boolean saveStatus()throws Exception{calls.add("preferences");yes(queue.equals(Collections.singleton("qa_unconfirmed")));if(throwsStatus)throw new java.io.IOException("qa preference failure");return false;}
            });
            yes(calls.equals(Arrays.asList("snapshot_and_ack","preferences")));yes(!result.statusSaved);yes(result.confirmed.size()==4);
            yes(durable[0].equals(snapshot.toString()));yes(queue.equals(Collections.singleton("qa_unconfirmed")));
            JSONObject warning=SyncFailureRules.recordFailure(new SyncIoFailure("STATUS_NOT_SAVED"),5,0);yes(warning.getInt("retry_count")==0);yes(!warning.getBoolean("schedule_retry"));
        }
        boolean[] statusCalled={false};try{SyncCommitRules.commit(new JSONObject(),new SyncCommitRules.Storage(){
            public void commitSnapshot(JSONObject saved,Set<String> ids)throws Exception{throw new java.io.IOException("qa rollback");}
            public boolean saveStatus(){statusCalled[0]=true;return true;}
        });throw new AssertionError("failed transaction accepted");}catch(java.io.IOException expected){yes(!statusCalled[0]);}
        List<Runnable> posted=new ArrayList<>();List<String> scheduling=new ArrayList<>();boolean[] available={true},reject={false};BackgroundWorkDispatch.Host host=new BackgroundWorkDispatch.Host(){public void state(boolean pending,String code){scheduling.add(pending+":"+code);}public boolean post(Runnable task){if(reject[0])return false;posted.add(task);return true;}public void prepare(){}public boolean localReady(){return true;}public boolean local(long delay){if(!available[0])throw new IllegalStateException("qa late failure");return true;}};BackgroundWorkDispatch.request(0,host);yes(posted.size()==1&&scheduling.equals(Arrays.asList("true:")));available[0]=false;posted.remove(0).run();yes(scheduling.get(scheduling.size()-1).equals("true:BACKGROUND_SCHEDULING_FAILED"));available[0]=true;BackgroundWorkDispatch.request(0,host);posted.remove(0).run();yes(scheduling.get(scheduling.size()-1).equals("false:"));reject[0]=true;BackgroundWorkDispatch.request(0,host);yes(posted.isEmpty()&&scheduling.get(scheduling.size()-1).equals("true:BACKGROUND_HANDLER_UNAVAILABLE"));
        System.out.println("PASS "+checks+" production atomic-confirmation/post-commit preference-failure checks");
    }
}
