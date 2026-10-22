package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Invokes the actual LocalOwnerRepository.runRequest, with an explicitly named transaction port. */
public final class LocalOwnerRepositoryTests {
    static int checks;static void yes(boolean b){checks++;if(!b)throw new AssertionError("owner repository "+checks);}
    static final class MemoryTransactions implements OwnerRequestTransactions.Store {
        Map<String,String> values=new HashMap<>(),before;Set<String> pending=new HashSet<>(),prior;boolean success;
        public void begin(){before=new HashMap<>(values);prior=new HashSet<>(pending);success=false;}
        public String read(String key){return values.getOrDefault(key,"");}
        public void write(String key,String value){values.put(key,value);}
        public void successful(){success=true;}
        public void end(){if(!success){values=before;pending=prior;}}
    }
    public static void main(String[] args)throws Exception{
        SyntheticBusinessData.verify();MemoryTransactions db=new MemoryTransactions();LocalOwnerRepository repo=new LocalOwnerRepository(db);String fingerprint=PixelMcpWriteTools.hash("qa_same");
        try{repo.runRequest("qa_rollback",fingerprint,()->{db.pending.add("qa_rollback");throw new java.io.IOException("qa deliberate failure");});throw new AssertionError("accepted failed command");}catch(java.io.IOException expected){yes(db.pending.isEmpty());yes(db.values.isEmpty());}
        int[] calls={0};LocalOwnerRepository.Request command=()->{calls[0]++;if(!db.pending.add("qa_once"))throw new IllegalStateException("qa duplicate primary key");return new JSONObject().put("client_id","qa_once");};
        JSONObject first=repo.runRequest("qa_once",fingerprint,command);yes(first.getString("client_id").equals("qa_once"));yes(calls[0]==1&&db.pending.size()==1);yes(db.values.containsKey("receipt:qa_once"));
        JSONObject replay=repo.runRequest("qa_once",fingerprint,command);yes(replay.getBoolean("replayed"));yes(calls[0]==1&&db.pending.size()==1);
        try{repo.runRequest("qa_once",PixelMcpWriteTools.hash("qa_changed"),command);throw new AssertionError("accepted changed replay");}catch(PixelMcpWriteTools.Rejected e){yes(e.code.equals("REPLAY_MISMATCH"));yes(calls[0]==1);}
        // Reproduce the AP4 unsafe assumption: once acknowledged, the primary key no longer exists.
        MemoryTransactions unsafe=new MemoryTransactions();LocalOwnerRepository old=new LocalOwnerRepository(unsafe);int[] effects={0};LocalOwnerRepository.Request action=()->{effects[0]++;unsafe.pending.add("qa_acknowledged");return new JSONObject().put("client_id","qa_acknowledged");};
        old.runRequest("qa_acknowledged",fingerprint,action);unsafe.pending.clear();unsafe.values.remove("receipt:qa_acknowledged");old.runRequest("qa_acknowledged",fingerprint,action);yes(effects[0]==2);
        // Production retention writes the minimal guard in the same transaction as deleting the full result.
        db.pending.clear();db.values.remove("receipt:qa_once");db.values.put("receipt_guard:qa_once",new JSONObject().put("fingerprint",fingerprint).toString());
        try{repo.runRequest("qa_once",fingerprint,command);throw new AssertionError("re-executed expired request");}catch(PixelMcpWriteTools.Rejected e){yes(e.code.equals("REQUEST_EXPIRED"));yes(calls[0]==1&&db.pending.isEmpty());}
        try{repo.runRequest("qa_once",PixelMcpWriteTools.hash("qa_changed"),command);throw new AssertionError("expired changed replay accepted");}catch(PixelMcpWriteTools.Rejected e){yes(e.code.equals("REPLAY_MISMATCH"));yes(calls[0]==1);}
        System.out.println("PASS "+checks+" actual LocalOwnerRepository rollback/replay/mismatch/expired replay checks");
    }
}
