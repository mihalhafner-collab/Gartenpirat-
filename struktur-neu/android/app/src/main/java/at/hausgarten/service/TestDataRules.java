package at.hausgarten.service;
import org.json.*;import java.util.*;
/** Test provenance is a domain boundary, never a licence to use real customer records. */
final class TestDataRules {
 static boolean customer(JSONObject c){return c!=null&&(c.optString("name").startsWith("[TEST]")||c.optString("name").equals("MCP FUNKTIONSTEST – KEIN ECHTER KUNDE"));}
 static boolean marked(JSONObject p){return p.optBoolean("test_record")||p.optString("title").startsWith("[TEST]");}
 static void customerChange(JSONObject row,JSONObject old){if(old!=null&&customer(old)!=customer(row))throw new IllegalArgumentException("Test-/Produktivzuordnung eines Kunden darf nicht gewechselt werden.");if(row.optBoolean("test_record")&&!customer(row))throw new IllegalArgumentException("Testkunde eindeutig mit [TEST] markieren.");}
 static void guard(JSONObject p,JSONObject s)throws Exception {
  JSONObject customer=OwnerJournalRules.findOrNull(s.optJSONArray("customers"),p.optString("customer_id"));
  boolean test=marked(p)||customer(customer);if(!test)return;
  String recipient=p.optString("billing_customer_id",p.optString("customer_id"));if(recipient.isEmpty())recipient=p.optString("customer_id");
  if(!customer(customer)||!customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"),recipient)))throw new PixelMcpWriteTools.Rejected("TEST_DATA_ISOLATION","Testdokumente benötigen ausschließlich eindeutig markierte Testkunden und Testempfänger.");
  p.put("test_record",true);
 }
 static void noDelivery(JSONObject p,JSONObject s)throws Exception {guard(new JSONObject(p.toString()),s);if(marked(p)||customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"),p.optString("customer_id"))))throw new PixelMcpWriteTools.Rejected("TEST_DELIVERY_BLOCKED","Testdokumente dürfen nicht an externe Empfänger übergeben oder versendet werden.");}
 static boolean localOnly(JSONObject a,JSONObject s)throws Exception {
  JSONObject p=a.getJSONObject("payload");if(p.optBoolean("_local_only"))return true;
  if(a.optString("kind").equals(TrashPurgeBatch.KIND)){boolean local=false,remote=false;JSONArray entries=p.getJSONArray("entries");for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);boolean test=localOnly(new JSONObject().put("kind","purge_v1").put("payload",new JSONObject().put("entity",e.getString("entity")).put("id",e.getString("entity_id"))),s);local|=test;remote|=!test;}if(local&&remote)throw new PixelMcpWriteTools.Rejected("TEST_DATA_ISOLATION","Lokale Testeinträge im Papierkorb zuerst einzeln endgültig löschen. Keine gemischte Test-/Drive-Löschaktion gespeichert.");return local;}
  if(p.optBoolean("_local_only")||marked(p)||a.optString("kind").equals("customer")&&customer(p))return true;
  if(customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"),p.optString("customer_id"))))return true;
  String kind=a.optString("kind");if(java.util.Arrays.asList("archive","restore","purge_v1").contains(kind))kind=p.optString("entity");String group=kind.startsWith("offer_")?"offers":kind.startsWith("invoice_")?"invoices":DriveJournalRules.collection(kind);
  JSONObject existing=OwnerJournalRules.findOrNull(s.optJSONArray(group),p.optString("id"));if(existing==null){JSONObject archive=s.optJSONObject("owner_archive"),entry=archive==null?null:archive.optJSONObject(kind+":"+p.optString("id"));if(entry!=null)existing=entry.optJSONObject("record");}return existing!=null&&(marked(existing)||kind.equals("customer")&&customer(existing)||customer(OwnerJournalRules.findOrNull(s.optJSONArray("customers"),existing.optString("customer_id"))));
 }
 private TestDataRules(){}
}
