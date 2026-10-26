package at.hausgarten.service;

import org.json.*;
import java.time.Instant;
import java.util.*;

/** Checks captured decision evidence and replay records before backup certification or restore. */
final class OfferDecisionValidation {
    private static PixelMcpWriteTools.Rejected invalid(){return new PixelMcpWriteTools.Rejected("OFFER_DECISION_RELATION_INVALID","Angebotsentscheidung, Dokumentversion oder Annahmenachweis passen nicht zusammen. Bestand unverändert lassen und Sicherung prüfen.");}
    private static void require(boolean value)throws Exception {if(!value)throw invalid();}
    private static JSONObject offer(JSONObject state,String id)throws Exception {JSONArray offers=state.optJSONArray("offers");for(int i=0;offers!=null&&i<offers.length();i++)if(id.equals(offers.getJSONObject(i).optString("id")))return offers.getJSONObject(i);throw invalid();}
    private static String hash(Object value)throws Exception{return PixelMcpWriteTools.hash(value);}
    private static String text(JSONObject value,String key,int max,boolean required)throws Exception {String result=RevierRules.text(value,key,max,required);if(required)require(!result.isEmpty());return result;}
    static JSONObject validate(RevierRepository repository,JSONObject state)throws Exception {
        Map<String,JSONObject> current=new TreeMap<>(),revisions=new TreeMap<>(),receipts=new TreeMap<>();Map<String,JSONArray> histories=new TreeMap<>();JSONArray entries=repository.rawEntries(OfferDecisionRules.PREFIX);Set<String> keys=new HashSet<>();String prefix=OfferDecisionRules.PREFIX;
        try{
            for(int i=0;i<entries.length();i++){
                JSONObject entry=entries.getJSONObject(i);String key=entry.getString("key"),raw=entry.getString("value");require(keys.add(key));
                if(key.startsWith(prefix+"current:")){String id=key.substring((prefix+"current:").length());JSONObject value=new JSONObject(raw);require(id.equals(value.optString("offer_id")));OfferDecisionRules.version(value);current.put(id,value);}
                else if(key.startsWith(prefix+"history:")){String id=key.substring((prefix+"history:").length());histories.put(id,new JSONArray(raw));}
                else if(key.startsWith(prefix+"revision:")){String id=key.substring((prefix+"revision:").length());JSONObject value=new JSONObject(raw);require(id.equals(value.optString("offer_id")));OfferDecisionRules.version(value);revisions.put(id,value);}
                else if(key.startsWith(prefix+"request:"))receipts.put(key.substring((prefix+"request:").length()),new JSONObject(raw));
                else throw invalid();
            }
            require(current.keySet().equals(histories.keySet()));Set<String> decisions=new HashSet<>(),requestIds=new HashSet<>();
            for(Map.Entry<String,JSONObject> item:current.entrySet()){
                String id=item.getKey();JSONObject decision=item.getValue(),document=offer(state,id);OfferDecisionRules.requireFinal(document);String request=text(decision,"request_id",128,true),decisionId=RevierRules.id(decision,"decision_id",true);require(request.length()>=8&&request.matches("[A-Za-z0-9_.:-]+")&&requestIds.add(request)&&decisions.add(decisionId)&&decisionId.equals(RevierRules.newId("offer_decision",request)));
                require(text(decision,"content_version",64,true).matches("[0-9a-f]{64}")&&decision.getString("content_version").equals(OfferDecisionRules.contentVersion(document))&&decision.getString("pdf_sha256").equals(document.getString("pdf_sha256")));Instant.parse(text(decision,"recorded_at",80,true));String status=text(decision,"decision",30,true);require(Arrays.asList("accepted","declined").contains(status));
                if(status.equals("accepted")){Instant.parse(text(decision,"accepted_at",80,true));text(decision,"evidence",10000,true);}else{require(!document.optString("status").equals("accepted"));text(decision,"reason",10000,false);}
                JSONArray history=histories.get(id);require(history.length()==1);JSONObject recorded=history.getJSONObject(0);OfferDecisionRules.version(recorded);require(hash(recorded).equals(hash(decision)));
                JSONObject receipt=receipts.get(hash(request));require(receipt!=null);validateReceipt(receipt,decision,id);
            }
            require(receipts.size()==current.size());
            for(Map.Entry<String,JSONObject> item:revisions.entrySet()){
                String id=item.getKey();JSONObject revision=item.getValue(),child=offer(state,id);String parent=RevierRules.id(revision,"parent_offer_id",true);require(!parent.equals(id));JSONObject original=offer(state,parent);String parentVersion=revision.getString("parent_content_version");require(parentVersion.matches("[0-9a-f]{64}")&&child.optString("customer_id").equals(original.optString("customer_id")));if(Arrays.asList("finalized","accepted").contains(original.optString("status")))require(parentVersion.equals(OfferDecisionRules.contentVersion(original)));Instant.parse(text(revision,"created_at",80,true));text(revision,"request_id",200,true);
                Set<String> visited=new HashSet<>();visited.add(id);String ancestor=parent;while(!ancestor.isEmpty()){require(visited.add(ancestor));JSONObject link=revisions.get(ancestor);ancestor=link==null?"":link.optString("parent_offer_id");}
            }
            return new JSONObject().put("formatVersion",1).put("status","PASS").put("decisions",current.size()).put("histories",histories.size()).put("revisions",revisions.size()).put("receipts",receipts.size());
        }catch(PixelMcpWriteTools.Rejected rejected){throw rejected;}catch(Exception malformed){throw invalid();}
    }
    private static void validateReceipt(JSONObject receipt,JSONObject decision,String id)throws Exception {
        require(receipt.optString("fingerprint").matches("[0-9a-f]{64}"));JSONObject result=receipt.getJSONObject("result");require(id.equals(result.optString("offer_id"))&&!result.optBoolean("invoice_created",true)&&!result.optBoolean("cloud_confirmed",true)&&result.optString("local_state").equals("local_saved")&&hash(result.getJSONObject("decision")).equals(hash(OfferDecisionRules.publicDecision(decision))));
    }
    private OfferDecisionValidation(){}
}
