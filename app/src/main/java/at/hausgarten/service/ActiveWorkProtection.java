package at.hausgarten.service;
import org.json.*;
import java.util.*;
/** Local timer references cannot be orphaned by either UI or MCP removal. */
final class ActiveWorkProtection {
 static void require(JSONObject state,String entity,String id)throws Exception {
  JSONObject session=state.optJSONObject("work_session");if(session==null||Arrays.asList("IDLE","FINALIZED","CANCELLED").contains(session.optString("state")))return;
  if(id.equals(session.optString(entity+"_id"))||entity.equals("worklog")&&id.equals(session.optString("finalized_worklog_id")))throw new PixelMcpWriteTools.Rejected("ACTIVE_WORK_SESSION","Die zugehörige Zeiterfassung zuerst abschließen oder bewusst verwerfen. Eintrag bleibt erhalten.");
 }
 private ActiveWorkProtection(){}
}
