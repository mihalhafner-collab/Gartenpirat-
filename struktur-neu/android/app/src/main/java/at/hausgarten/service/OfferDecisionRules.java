package at.hausgarten.service;

import org.json.*;
import java.time.*;
import java.util.*;

/** Decision evidence is separate from immutable document content and provider handoff state. */
final class OfferDecisionRules {
    static final String PREFIX="offer_decision:v1:";
    static void version(JSONObject value)throws Exception {Object version=value.opt("formatVersion");if(!(version instanceof Number)||!version.toString().equals("1"))throw new PixelMcpWriteTools.Rejected("OFFER_DECISION_VERSION_UNSUPPORTED","Neuere Angebotsentscheidungen benötigen eine passende Appversion.");}
    static String contentVersion(JSONObject offer)throws Exception {
        JSONObject fixed=new JSONObject();for(String field:OfferRules.FIELDS)if(offer.has(field))fixed.put(field,offer.get(field));for(String field:new String[]{"id","offer_number","finalized_at","issuer","recipient","pdf_sha256","pdf_byte_size","document_id","money_minor"})if(offer.has(field))fixed.put(field,offer.get(field));return PixelMcpWriteTools.hash(fixed);
    }
    static boolean local(JSONObject state){JSONObject authority=state.optJSONObject("local_authority");return authority!=null&&Arrays.asList("LOCAL_ACTIVE","RESTORE_REVIEW").contains(authority.optString("mode"));}
    static JSONObject publicDecision(JSONObject decision)throws Exception {
        JSONObject value=new JSONObject();for(String key:new String[]{"formatVersion","decision_id","offer_id","content_version","decision","accepted_at","evidence","reason","recorded_at","pdf_sha256","request_id"})if(decision.has(key))value.put(key,decision.get(key));value.put("decision_hash",PixelMcpWriteTools.hash(decision));return value;
    }
    static JSONObject matching(JSONObject offer,JSONObject decisions)throws Exception {
        if(decisions==null||decisions.optJSONObject(offer.optString("id"))==null)return null;JSONObject decision=decisions.getJSONObject(offer.getString("id"));version(decision);
        if(!decision.optString("content_version").equals(contentVersion(offer)))return new JSONObject().put("formatVersion",1).put("offer_id",offer.getString("id")).put("decision","review_required").put("reason","OFFER_DOCUMENT_VERSION_CHANGED");return publicDecision(decision);
    }
    static JSONObject decorateQuery(String name,JSONObject result,JSONObject effectiveState)throws Exception {
        JSONObject decisions=effectiveState.optJSONObject("offer_decisions");if(decisions==null||decisions.length()==0)return result;
        if(name.equals("get_offer")||name.equals("validate_offer")){JSONObject offer=result.optJSONObject("offer");if(offer!=null){JSONObject decision=matching(offer,decisions);if(decision!=null)result.put("decision",decision).put("decision_status",decision.getString("decision"));}}
        if(name.equals("list_offers")){JSONObject visible=new JSONObject();JSONArray rows=result.optJSONArray("offers");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject wrapper=rows.getJSONObject(i),offer=wrapper.optJSONObject("offer");if(offer==null)continue;JSONObject decision=matching(offer,decisions);if(decision!=null){wrapper.put("decision",decision);visible.put(offer.getString("id"),decision);}}if(visible.length()>0)result.put("decisions",visible);}
        return result;
    }
    static JSONObject requireFinal(JSONObject offer)throws Exception {
        if(!Arrays.asList("finalized","accepted").contains(offer.optString("status"))||offer.optString("finalized_at").isEmpty()||!offer.optString("pdf_sha256").matches("[0-9a-f]{64}"))throw new PixelMcpWriteTools.Rejected("OFFER_FINAL_VERSION_REQUIRED","Entschieden werden kann nur eine finalisierte Version mit geprüftem Original-PDF.");return offer;
    }
    private OfferDecisionRules(){}
}
