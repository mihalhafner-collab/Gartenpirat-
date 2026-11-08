// Generated test fixture adapter; never packaged in the operative app.
package at.hausgarten.service;
import org.json.*;
public final class SyntheticBusinessData {
 private static final String SPEC = "{\"format\":\"hausgarten.synthetic.v1\",\"profiles\":[\"minimal\",\"edge_cases\",\"scale\"],\"scale_rows\":3000,\"minimal\":{\"format\":1,\"schema_version\":2,\"customers\":[{\"id\":\"qa_customer_1\",\"name\":\"QA Musterkunde\",\"street\":\"QA Musterweg 1\",\"postal_code\":\"0000\",\"city\":\"QA Beispielort\",\"country\":\"AT\",\"email\":\"qa_customer@example.invalid\",\"phone\":\"\",\"active\":1,\"test_record\":true}],\"orders\":[{\"id\":\"qa_order_1\",\"customer_id\":\"qa_customer_1\",\"title\":\"QA Gartenpflege\",\"status\":\"Geplant\",\"test_record\":true}],\"worklogs\":[{\"id\":\"qa_work_1\",\"customer_id\":\"qa_customer_1\",\"order_id\":\"qa_order_1\",\"work_date\":\"2026-10-01\",\"activity\":\"QA Gartenpflege\",\"hours\":2,\"break_minutes\":0,\"kilometers\":0,\"billed\":0,\"customer_visible\":1,\"test_record\":true}],\"invoices\":[],\"offers\":[],\"photos\":[],\"appointments\":[],\"service_catalog\":[],\"accepted_ids\":[],\"sync_errors\":[],\"business_settings\":{\"owner_name\":\"QA Musteraussteller\",\"business_name\":\"QA Musterbetrieb\",\"business_street\":\"QA Musterweg 2\",\"business_postal_code\":\"0000\",\"business_city\":\"QA Beispielort\",\"business_email\":\"qa_issuer@example.invalid\",\"business_phone\":\"\",\"business_iban\":\"\",\"tax_mode\":\"unconfigured\"}}}";
 public static JSONObject profile(String name) throws Exception {
  JSONObject spec=new JSONObject(SPEC),out=new JSONObject(spec.getJSONObject("minimal").toString());
  if(name.equals("edge_cases"))out.getJSONArray("customers").getJSONObject(0).put("name","QA Musterkunde ÄÖÜ < & >").put("custom_fields",new JSONObject().put("qa_multiline","QA\nMehrzeilig"));
  else if(name.equals("scale")){JSONArray rows=new JSONArray();for(int i=0;i<spec.getInt("scale_rows");i++)rows.put(new JSONObject(out.getJSONArray("worklogs").getJSONObject(0).toString()).put("id",String.format(java.util.Locale.ROOT,"qa_work_%05d",i)));out.put("worklogs",rows);}
  else if(!name.equals("minimal"))throw new IllegalArgumentException("Unknown synthetic profile");
  return out;
 }
 public static JSONObject object() {return new JSONObject();}
 public static JSONObject object(java.util.Map<?,?> raw) {return new JSONObject(raw);}
 public static JSONObject object(String raw) throws JSONException {return new JSONObject(raw);}
 public static void verify() throws Exception {JSONObject root=profile("minimal");if(!root.getJSONArray("customers").getJSONObject(0).getBoolean("test_record")||!root.getJSONArray("customers").getJSONObject(0).getString("email").endsWith("@example.invalid"))throw new AssertionError("Synthetic fixture contract");}
}
