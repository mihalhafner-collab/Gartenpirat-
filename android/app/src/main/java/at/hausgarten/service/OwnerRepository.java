package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Effects required by owner use cases. No Android, SQL, UI or provider types. */
interface OwnerRepository {
    JSONObject state() throws Exception;
    default JSONObject effective() throws Exception { return SnapshotProjection.effective(state()); }
    default JSONObject settings() throws Exception { return new JSONObject(ownerValue("settings","{}")); }
    String ownerValue(String key,String fallback);
    void putOwnerValue(String key,String value);
    JSONArray pendingActions() throws Exception;
    JSONObject enqueue(String kind,JSONObject payload,String request) throws Exception;
    JSONObject fileMeta(String id) throws Exception;
    byte[] fileBytes(String id) throws Exception;
    void putFile(String id,JSONObject metadata,byte[] data) throws Exception;
    String savePhoto(JSONObject payload) throws Exception;
    void replaceWorklog(JSONObject payload) throws Exception;
    JSONObject createDocument(JSONObject record) throws Exception;
    void readDocument(JSONObject record) throws Exception;
    JSONObject verifyDelivery(String messageId,JSONObject offer) throws Exception;
    void verifyLegacyBackup(String id,String preview) throws Exception;
    JSONObject createBackup(String request) throws Exception;
    JSONObject protectDeletion(JSONObject snapshot,JSONArray entries,String request) throws Exception;
    JSONObject restoreBackup(JSONObject args,String request) throws Exception;
    JSONObject planningSettings() throws Exception;
    boolean calendarConnected();
    boolean weatherCredentialAvailable();
    void savePlanningSettings(JSONObject settings,String expected) throws Exception;
    JSONObject requestPlanning() throws Exception;
    JSONObject acknowledgeWeather(JSONObject args) throws Exception;
    JSONObject resolveCalendar(JSONObject args) throws Exception;
    void checkBusy(JSONObject appointment) throws Exception;
}
