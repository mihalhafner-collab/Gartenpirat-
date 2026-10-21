package at.hausgarten.service;
import org.json.*;
import java.time.*;

/** Isolated synthetic location and notification policy regression tests. */
public final class WeatherWorkflowTests {
 static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("Weather workflow check "+checks);}
 static JSONObject candidate(String address,double latitude)throws Exception{return new JSONObject().put("address",address).put("latitude",latitude).put("longitude",16.3);}
 public static void main(String[] args)throws Exception{
  SyntheticBusinessData.verify();JSONObject c=new JSONObject().put("id","qa_c").put("street","QA Musterstraße 7").put("postal_code","9999").put("city","QA Musterort").put("latitude",48.2).put("longitude",16.3),a=new JSONObject().put("id","qa_a").put("customer_id","qa_c").put("location","");JSONArray customers=new JSONArray().put(c),orders=new JSONArray();
  JSONObject derived=WeatherLocationRules.context(a,customers,orders);yes(WeatherLocationRules.coordinates(derived));yes(derived.getString("weather_location_label").equals("QA Musterstraße 7, 9999 QA Musterort"));yes(!a.has("latitude")&&a.getString("location").isEmpty());
  a.put("location","QA Musterstr. 7 9999 QA Musterort");yes(WeatherLocationRules.context(a,customers,orders).getString("weather_location_state").equals("customer_coordinates"));
  a.put("location","QA Anderer Weg 8, 9999 QA Musterort");derived=WeatherLocationRules.context(a,customers,orders);yes(!WeatherLocationRules.coordinates(derived));String changed=WeatherLocationRules.cacheKey(derived);a.put("location","QA Anderer Weg 9, 9999 QA Musterort");yes(!changed.equals(WeatherLocationRules.cacheKey(WeatherLocationRules.context(a,customers,orders))));
  a.put("location","").put("order_id","qa_o");orders.put(new JSONObject().put("id","qa_o").put("customer_id","qa_c").put("location","QA Zweites Grundstück"));derived=WeatherLocationRules.context(a,customers,orders);yes(derived.getString("weather_location_label").equals("QA Zweites Grundstück")&&!WeatherLocationRules.coordinates(derived));
  orders.getJSONObject(0).put("customer_id","qa_other");yes(WeatherLocationRules.coordinates(WeatherLocationRules.context(a,customers,orders))); // An unrelated order is never used.
  a.put("latitude",47.1).put("longitude",15.1);yes(WeatherLocationRules.context(a,customers,orders).getDouble("latitude")==47.1);
  String query="QA Musterstr. 7, 9999 QA Musterort";JSONObject found=WeatherLocationRules.choose(query,new JSONArray().put(candidate("QA Musterstraße 7, 9999 QA Musterort, Österreich",48.2)));yes(found.getString("state").equals("address_resolved"));
  yes(WeatherLocationRules.choose(query,new JSONArray().put(candidate("QA Musterstraße 17, 9999 QA Musterort",48.2))).getString("state").equals("not_found"));yes(WeatherLocationRules.choose(query,new JSONArray().put(candidate("QA Musterstraße 7, 9998 QA Musterort",48.2))).getString("state").equals("not_found"));
  yes(WeatherLocationRules.choose("QA Doppelort",new JSONArray().put(candidate("QA Doppelort",48.2)).put(candidate("QA Doppelort",47.2))).getString("state").equals("ambiguous"));
  yes(WeatherLocationRules.choose("QA Doppelort",new JSONArray().put(candidate("QA Doppelort",48.2)).put(candidate("QA Doppelort",48.2001))).getString("state").equals("address_resolved"));
  JSONObject context=WeatherLocationRules.context(new JSONObject().put("customer_id","qa_c").put("location",query),new JSONArray(),new JSONArray()),cached=WeatherLocationRules.cached(context,found);yes(WeatherLocationRules.coordinates(cached)&&!WeatherLocationRules.coordinates(context));yes(WeatherLocationRules.cached(context,new JSONObject().put("state","unavailable")).getString("weather_location_state").equals("unavailable"));
  // Reproduce overly strict joins with synthetic addresses, never a screenshot/customer address.
  yes(WeatherLocationRules.matches("QA Musterstraße 7, 9999 QA Musterort","QA Muster Straße 7, 9999 QA Musterort"));
  yes(WeatherLocationRules.matches("QA Muster Str. 7, 9999 QA Musterort","QA Musterstraße 7, 9999 QA Musterort"));
  yes(!WeatherLocationRules.matches("QA Musterstraße 7, 9999 QA Musterort","QA Muster Straße 17, 9999 QA Musterort"));
  yes(WeatherLocationRules.localityQuery(query).equals("9999 QA Musterort"));yes(WeatherLocationRules.localityQuery("QA Grundstück ohne Ort").isEmpty());
  yes(WeatherLocationRules.localityQuery(query+", Österreich").equals("9999 QA Musterort"));
  java.util.List<String> searches=new java.util.ArrayList<>();JSONObject area=WeatherLocationRules.search(query,q->{searches.add(q);return q.equals(query)?new JSONArray():new JSONArray().put(candidate("9999 QA Musterort, Österreich",48.2));});
  yes(searches.size()==2&&searches.get(1).equals("9999 QA Musterort"));yes(area.getString("state").equals("locality_resolved")&&area.getString("precision").equals("locality"));
  JSONObject regional=WeatherLocationRules.cached(context,area);yes(regional.getString("weather_location_state").equals("locality_resolved")&&regional.getString("weather_location_area").equals("9999 QA Musterort"));yes(!context.has("latitude"));
  searches.clear();JSONObject exact=WeatherLocationRules.search(query,q->{searches.add(q);return new JSONArray().put(candidate("QA Muster Straße 7, 9999 QA Musterort",48.2));});yes(searches.size()==1&&exact.getString("state").equals("address_resolved"));
  JSONObject wrong=WeatherLocationRules.search(query,q->q.equals(query)?new JSONArray():new JSONArray().put(candidate("9998 QA Anderer Ort",48.2)));yes(!WeatherLocationRules.coordinates(wrong)&&wrong.getString("state").equals("not_found"));
  JSONObject ambiguous=WeatherLocationRules.search(query,q->q.equals(query)?new JSONArray():new JSONArray().put(candidate("9999 QA Musterort",48.2)).put(candidate("9999 QA Musterort",47.2)));yes(ambiguous.getString("state").equals("ambiguous"));
  JSONObject house=candidate("9999 QA Musterort",48.2).put("house_number","17");yes(!WeatherLocationRules.coordinates(WeatherLocationRules.chooseLocality("9999 QA Musterort",new JSONArray().put(house))));
  JSONObject pending=WeatherLocationRules.search(query,q->q.equals(query)?new JSONArray():null);yes(pending.getString("state").equals("pending"));
  yes(WeatherLocationRules.search(query,q->{throw new java.io.IOException("Synthetic unavailable");}).getString("state").equals("unavailable"));
  for(String state:new String[]{"not_found","ambiguous","unavailable","pending"})yes(WeatherLocationRules.retryable(state));yes(!WeatherLocationRules.retryable("address_resolved")&&!WeatherLocationRules.retryable("locality_resolved"));
  Instant now=Instant.parse("2026-10-20T10:00:00Z");JSONObject appointment=new JSONObject().put("id","qa_a").put("title","QA Hecke schneiden").put("starts_at","2026-10-21T06:00:00Z").put("ends_at","2026-10-21T08:00:00Z").put("status","confirmed"),risk=new JSONObject().put("severity","critical").put("alert_hash","a".repeat(64)).put("reasons",new JSONArray().put("Starke Böen: Tätigkeit prüfen."));
  yes(WeatherAlertRules.shouldNotify(appointment,risk,now,"",""));yes(!WeatherAlertRules.shouldNotify(appointment,risk,now,"a".repeat(64),""));yes(!WeatherAlertRules.shouldNotify(appointment,risk,now,"","a".repeat(64)));risk.put("alert_hash","b".repeat(64));yes(WeatherAlertRules.shouldNotify(appointment,risk,now,"","a".repeat(64)));
  Instant night=Instant.parse("2026-10-20T21:00:00Z");yes(!WeatherAlertRules.shouldNotify(appointment,risk,night,"",""));risk.put("critical_official_warning",true);yes(WeatherAlertRules.shouldNotify(appointment,risk,night,"",""));
  for(String status:new String[]{"draft","cancelled","completed"}){appointment.put("status",status);yes(!WeatherAlertRules.actionable(appointment,risk,now));}appointment.put("status","confirmed");yes(!WeatherAlertRules.actionable(appointment,risk,Instant.parse("2026-10-21T08:00:00Z")));risk.put("severity","unknown");yes(!WeatherAlertRules.actionable(appointment,risk,now));risk.put("severity","critical");
  JSONObject text=WeatherAlertRules.message(appointment,risk,"QA Kundin");yes(text.getString("title").contains("Wetterwarnung")&&text.getString("title").contains("Böen"));yes(text.getString("body").contains("QA Kundin")&&text.getString("body").contains("08:00")&&text.getString("body").contains("Böen"));yes(text.getString("detail").contains("QA Hecke schneiden"));yes(appointment.getString("starts_at").equals("2026-10-21T06:00:00Z"));
  risk.put("reasons",new JSONArray().put("Noch keine belastbare Vorhersage verfügbar.").put("GeoSphere-Warnung: Gewitter (Stufe 2)."));text=WeatherAlertRules.message(appointment,risk,"QA Kundin");yes(text.getString("title").contains("Gewitter")&&text.getString("body").contains("GeoSphere-Warnung"));
  risk.put("location_precision","locality").put("location_area","9999 QA Musterort");yes(WeatherAlertRules.message(appointment,risk,"QA Kundin").getString("body").contains("Ortsvorhersage · 9999 QA Musterort"));
  System.out.println("PASS "+checks+" weather address precedence, ambiguity, cache isolation, quiet hours, deduplication and actionable message checks; synthetic only");
 }
}
