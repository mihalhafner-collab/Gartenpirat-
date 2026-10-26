package at.hausgarten.service;
import org.json.*;

/** Actual request transaction algorithm; Android SQLite supplies only the transaction/storage port. */
final class OwnerRequestTransactions {
    interface Store { void begin(); String read(String key); void write(String key,String value); void successful(); void end(); }
    interface Command { JSONObject run()throws Exception; }
    static JSONObject runRequest(Store store,String request,String fingerprint,Command command,String now)throws Exception{
        store.begin();JSONObject result;
        try{
            String old=store.read("receipt:"+request),guard=store.read("receipt_guard:"+request);
            if(!old.isEmpty()){
                JSONObject receipt=new JSONObject(old);requireFingerprint(receipt,fingerprint);if(receipt.getJSONObject("result").optBoolean("_result_content_removed"))throw new PixelMcpWriteTools.Rejected("REQUEST_EXPIRED","Dieser verarbeitete Vorgang wurde endgültig entfernt. Aktuellen Stand prüfen; keine erneute Ausführung.");result=receipt.getJSONObject("result").put("replayed",true);
            }else if(!guard.isEmpty()){
                requireFingerprint(new JSONObject(guard),fingerprint);
                throw new PixelMcpWriteTools.Rejected("REQUEST_EXPIRED","Diese Anfrage wurde bereits verarbeitet. Der Detailbeleg ist abgelaufen. Aktuellen Eintrag prüfen; die Aktion wird nicht erneut ausgeführt.");
            }else{
                result=command.run();store.write("receipt:"+request,new JSONObject().put("fingerprint",fingerprint).put("created_at",now).put("result",result).toString());
            }
            store.successful();
        }finally{store.end();}
        return result;
    }
    private static void requireFingerprint(JSONObject receipt,String fingerprint)throws Exception{
        if(!fingerprint.equals(receipt.getString("fingerprint")))throw new PixelMcpWriteTools.Rejected("REPLAY_MISMATCH","Die Anfragekennung gehört zu anderen Angaben.");
    }
    private OwnerRequestTransactions(){}
}
