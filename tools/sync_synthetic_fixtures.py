"""Generate test-only Java adapters from the single synthetic JSON source."""
import argparse,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=json.loads((ROOT/'tests/fixtures/synthetic-business.json').read_text())
wire=json.dumps(spec,ensure_ascii=False,separators=(',',':'))
java='''// Generated test fixture adapter; never packaged in the operative app.
package at.hausgarten.service;
import org.json.*;
public final class SyntheticBusinessData {
 private static final String SPEC = '''+json.dumps(wire,ensure_ascii=False)+''';
 public static JSONObject profile(String name) throws Exception {
  JSONObject spec=new JSONObject(SPEC),out=new JSONObject(spec.getJSONObject("minimal").toString());
  if(name.equals("edge_cases"))out.getJSONArray("customers").getJSONObject(0).put("name","QA Musterkunde ÄÖÜ < & >").put("custom_fields",new JSONObject().put("qa_multiline","QA\\nMehrzeilig"));
  else if(name.equals("scale")){JSONArray rows=new JSONArray();for(int i=0;i<spec.getInt("scale_rows");i++)rows.put(new JSONObject(out.getJSONArray("worklogs").getJSONObject(0).toString()).put("id",String.format(java.util.Locale.ROOT,"qa_work_%05d",i)));out.put("worklogs",rows);}
  else if(!name.equals("minimal"))throw new IllegalArgumentException("Unknown synthetic profile");
  return out;
 }
 public static JSONObject object() {return new JSONObject();}
 public static JSONObject object(java.util.Map<?,?> raw) {return new JSONObject(raw);}
 public static JSONObject object(String raw) throws JSONException {return new JSONObject(raw);}
 public static void verify() throws Exception {JSONObject root=profile("minimal");if(!root.getJSONArray("customers").getJSONObject(0).getBoolean("test_record")||!root.getJSONArray("customers").getJSONObject(0).getString("email").endsWith("@example.invalid"))throw new AssertionError("Synthetic fixture contract");}
}
'''
paths=[ROOT/'tests/java/at/hausgarten/service/SyntheticBusinessData.java',ROOT/'app/src/androidTest/java/at/hausgarten/service/SyntheticBusinessData.java']
parser=argparse.ArgumentParser();parser.add_argument('--write',action='store_true');args=parser.parse_args()
for p in paths:
 if args.write:p.write_text(java)
 if not p.exists() or p.read_text()!=java:raise SystemExit('Synthetic fixture adapter drift')
print('PASS canonical synthetic fixture Java adapters')
