package at.hausgarten.service;
import org.json.*;
/** Local deletion protection must neither replace fresh cloud evidence nor hide changed originals. */
final class LocalTestTombstone {
 static boolean project(JSONObject master,String key,JSONObject receipt)throws Exception {
  int split=key.indexOf(':');if(split<=0)throw new IllegalArgumentException("Ungültiger lokaler Löschbeleg.");
  String entity=key.substring(0,split),id=key.substring(split+1);
  if(!receipt.optBoolean("purged")||receipt.has("record"))throw new IllegalArgumentException("Ungültiger lokaler Löschbeleg.");
  JSONObject archive=master.optJSONObject("owner_archive"),remote=archive==null?null:archive.optJSONObject(key);
  if(remote!=null&&remote.optBoolean("purged"))return false; // Preserve exact newer server receipt and hashes.
  JSONObject record=OwnerJournalRules.findOrNull(master.optJSONArray(OwnerJournalRules.group(entity)),id);
  if(record!=null&&!OwnerJournalRules.version(record).equals(receipt.optString("source_record_hash")))return false;
  if(remote!=null&&!OwnerJournalRules.version(remote).equals(receipt.optString("source_archive_hash")))return false;
  JSONObject tombstone=new JSONObject(receipt.toString());
  for(String privateKey:new String[]{"local_test_only","source_record_hash","source_archive_hash"})tombstone.remove(privateKey);
  OwnerJournalRules.replace(master,OwnerJournalRules.group(entity),id,null);OwnerJournalRules.archives(master).put(key,tombstone);return true;
 }
 private LocalTestTombstone(){}
}
