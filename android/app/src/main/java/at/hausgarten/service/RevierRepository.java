package at.hausgarten.service;

import org.json.*;

/** Local effects used by the common Revier UI/MCP application service. */
interface RevierRepository {
    interface Work { JSONObject run() throws Exception; }
    JSONObject effective() throws Exception;
    String get(String key,String fallback) throws Exception;
    void put(String key,String value) throws Exception;
    JSONArray list(String prefix) throws Exception;
    JSONArray rawEntries(String prefix) throws Exception;
    JSONObject transaction(Work work) throws Exception;
    JSONObject planInspection(JSONObject appointmentArgs) throws Exception;
    default JSONObject session(String id)throws Exception {return null;}
    default JSONArray sessionContexts()throws Exception {return new JSONArray();}
    default JSONObject currentSession()throws Exception {return new JSONObject();}
    default void assertSourceCorrectionAllowed(String type,String id)throws Exception {}
}
