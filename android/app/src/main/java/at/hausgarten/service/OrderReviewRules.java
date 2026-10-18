package at.hausgarten.service;
import org.json.*;
import java.util.*;
/** Read-only inspection followed by an explicit, guarded order correction. */
final class OrderReviewRules {
 // This wire digest must not depend on Android versus desktop org.json escaping.
 static String quote(String value){
  StringBuilder out=new StringBuilder("\"");
  for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){
   case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;
   case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;
   case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;
   default:if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
  }}return out.append('"').toString();
 }
 static String canonical(Object value)throws Exception{
  if(value instanceof JSONObject){JSONObject row=(JSONObject)value;List<String> keys=new ArrayList<>();row.keys().forEachRemaining(keys::add);Collections.sort(keys);StringJoiner out=new StringJoiner(",","{","}");for(String key:keys)out.add(quote(key)+":"+canonical(row.get(key)));return out.toString();}
  if(value instanceof JSONArray){JSONArray rows=(JSONArray)value;StringJoiner out=new StringJoiner(",","[","]");for(int i=0;i<rows.length();i++)out.add(canonical(rows.get(i)));return out.toString();}
  if(value==null||value==JSONObject.NULL)return "null";
  if(value instanceof Number)return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
  return value instanceof String?quote((String)value):value.toString();
 }
 static String actionHash(JSONObject action)throws Exception{
  byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(canonical(action).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
 }
 static JSONObject find(JSONObject snapshot,String group,String id)throws Exception{
  JSONArray rows=snapshot.getJSONArray(group);for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;
 }
 static String token(JSONObject state)throws Exception{
  return PixelMcpWriteTools.hash(new JSONObject().put("master",state.getJSONObject("master")).put("pending",state.getJSONArray("pending")));
 }
 static JSONObject preview(JSONObject state)throws Exception{
  JSONObject snapshot=new JSONObject(state.getJSONObject("master").toString());
  DriveJournalRules.apply(snapshot,DriveJournalRules.union(state.getJSONArray("pending")));return snapshot;
 }
 static JSONObject candidate(JSONObject state,JSONObject valid,String id)throws Exception{
  JSONObject current=find(valid,"orders",id),row=current==null?null:new JSONObject(current.toString());
  JSONArray pending=state.getJSONArray("pending");
  for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.getJSONObject("payload");if("order".equals(a.optString("kind"))&&id.equals(p.optString("id"))){if(row==null)row=new JSONObject();JSONObject clean=DriveJournalRules.clean(p);for(Iterator<String> keys=clean.keys();keys.hasNext();){String key=keys.next();row.put(key,clean.get(key));}}}
  if(row==null)throw new IllegalArgumentException("Dieser Auftrag ist nicht mehr vorhanden. Bitte die Liste aktualisieren.");
  return row;
 }
 static JSONObject protectionSnapshot(JSONObject state,JSONObject valid)throws Exception{
  JSONObject result=new JSONObject(valid.toString()),effective=PixelMcpReadTools.effective(state);
  for(String group:new String[]{"worklogs","invoices","photos"})result.put(group,effective.getJSONArray(group));return result;
 }
 static JSONArray checks(JSONObject row,JSONObject old,JSONObject snapshot)throws Exception{
  JSONArray checks=new JSONArray();
  add(checks,"Auftragsbezeichnung",!row.optString("title").trim().isEmpty(),"Bitte eine Bezeichnung eintragen.");
  boolean customer=find(snapshot,"customers",row.optString("customer_id"))!=null;
  add(checks,"Kundenzuordnung",customer,"Der ausgewählte Kunde fehlt. Bitte einen vorhandenen Kunden wählen.");
  try{BillingRules.order(new JSONObject(row.toString()),snapshot);add(checks,"Rechnungsempfänger",true,"");}catch(Exception e){add(checks,"Rechnungsempfänger",false,e.getMessage());}
  try{if(!row.optString("scheduled_date").isEmpty()&&!row.isNull("scheduled_date"))java.time.LocalDate.parse(row.getString("scheduled_date"));add(checks,"Auftragsdatum",true,"");}catch(Exception e){add(checks,"Auftragsdatum",false,"Bitte ein gültiges Datum wählen oder das optionale Datum leeren.");}
  try{EntityProtectionRules.validateOrderChange(row,old,snapshot.getJSONArray("worklogs"),snapshot.getJSONArray("invoices"),snapshot.getJSONArray("photos"));add(checks,"Verknüpfte Einträge",true,"");}catch(Exception e){add(checks,"Verknüpfte Einträge",false,e.getMessage());}
  return checks;
 }
 static void add(JSONArray rows,String label,boolean ok,String message)throws Exception{rows.put(new JSONObject().put("label",label).put("ok",ok).put("message",ok?"In Ordnung":message));}
 static JSONObject inspect(JSONObject state,String id)throws Exception{
  JSONObject valid=preview(state),row=candidate(state,valid,id),old=find(valid,"orders",id);JSONArray reasons=new JSONArray(),pending=state.getJSONArray("pending"),errors=valid.optJSONArray("drive_journal_errors");
  Set<String> own=new HashSet<>();for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i);if("order".equals(a.optString("kind"))&&id.equals(a.getJSONObject("payload").optString("id")))own.add(a.getString("client_id"));}
  for(int i=0;errors!=null&&i<errors.length();i++){JSONObject e=errors.getJSONObject(i);if(own.contains(e.optString("client_id")))reasons.put(e.optString("error","Die Änderung benötigt Prüfung."));}
  return new JSONObject().put("id",id).put("token",token(state)).put("order",row).put("current",old==null?JSONObject.NULL:old)
    .put("customers",valid.getJSONArray("customers")).put("checks",checks(row,old,protectionSnapshot(state,valid))).put("reasons",reasons).put("pending",own.size());
 }
 static JSONObject prepare(JSONObject state,JSONObject request)throws Exception{
  if(!token(state).equals(request.optString("token")))throw new IllegalArgumentException("Der Datenstand hat sich geändert. Bitte erneut prüfen; deine Eingaben bleiben sichtbar.");
  String id=request.getString("id"),decision=request.optString("decision");
  if(!decision.equals("save")&&!decision.equals("keep"))throw new IllegalArgumentException("Bitte eine Entscheidung auswählen.");
  JSONObject valid=preview(state),old=find(valid,"orders",id),row;
  if(decision.equals("keep")){if(old==null)throw new IllegalArgumentException("Für diesen Auftrag gibt es noch keinen übernommenen Stand.");row=new JSONObject(old.toString());}
  else{
   row=candidate(state,valid,id);JSONObject edits=request.getJSONObject("order");
   for(String key:new String[]{"title","customer_id","billing_customer_id","description","location","scheduled_date","status","customer_visible"})if(edits.has(key))row.put(key,edits.get(key));
  }
  row=DriveJournalRules.clean(row);row.put("id",id);
  JSONArray checks=checks(row,old,protectionSnapshot(state,valid));
  for(int i=0;i<checks.length();i++)if(!checks.getJSONObject(i).getBoolean("ok"))throw new IllegalArgumentException(checks.getJSONObject(i).getString("message"));
  BillingRules.order(row,valid);
  JSONArray refs=new JSONArray(),pending=state.getJSONArray("pending");Set<String> failures=new HashSet<>();
  JSONArray errors=valid.getJSONArray("drive_journal_errors");
  for(int i=0;i<errors.length();i++)failures.add(errors.getJSONObject(i).optString("client_id"));
  for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i);if("order".equals(a.optString("kind"))&&id.equals(a.getJSONObject("payload").optString("id"))&&failures.contains(a.optString("client_id")))refs.put(new JSONObject().put("client_id",a.getString("client_id")).put("sha256",actionHash(a)));}
  if(refs.length()>0)row.put("_sync_resolves",refs);
  if(old==null)row.put("_mcp_create",true);else row.put("_sync_base",DriveJournalRules.guard(old,"order"));
  String stamp=java.time.Instant.now().toString();row.put("updated_at",stamp).put("source","Pixel offline");
  if(!row.has("created_at"))row.put("created_at",old==null?stamp:old.optString("created_at",stamp));
  return row;
 }
}
