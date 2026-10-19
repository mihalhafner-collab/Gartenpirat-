package at.hausgarten.service;
import org.json.*;
public final class DriveLegacyNormalizationTests {
 static int count;static void yes(boolean b){if(!b)throw new AssertionError("legacy normalization "+count);count++;}
 public static void main(String[] args)throws Exception{ SyntheticBusinessData.verify();
  for(boolean oldRawHash:new boolean[]{false,true}){
   JSONObject legacy=PixelDriveStateTests.state();legacy.remove("offers");legacy.remove("service_catalog");String before=PixelMcpWriteTools.hash(legacy);
   JSONObject normalized=SyntheticBusinessData.object(legacy.toString());DriveJournalRules.validateSnapshot(normalized);
   PixelDriveStateTests.Memory storage=new PixelDriveStateTests.Memory();JSONObject initial=DrivePrimaryState.recover(normalized,"pixel-test",storage);
   if(oldRawHash)initial.put("drive_legacy_base_hash",before);
   storage.publish(initial,new JSONArray(),"commit-legacy",1,"","");
   JSONObject reopened=DrivePrimaryState.recover(legacy,"pixel-test",storage);yes(reopened.getJSONArray("offers").length()==0);yes(reopened.getString("drive_legacy_base_hash").equals(PixelMcpWriteTools.hash(normalized)));yes(before.equals(PixelMcpWriteTools.hash(legacy)));
   JSONObject withCollections=SyntheticBusinessData.object(legacy.toString()).put("offers",new JSONArray()).put("service_catalog",new JSONArray());
   // A historic raw hash is valid only for the exact observed raw input, never a different input.
   if(!oldRawHash)yes(DrivePrimaryState.recover(withCollections,"pixel-test",storage).getString("drive_parent_publication_id").equals("commit-legacy"));
   JSONObject changed=SyntheticBusinessData.object(legacy.toString());changed.getJSONArray("customers").getJSONObject(0).put("notes","real parallel modification");
   boolean blocked=false;try{DrivePrimaryState.recover(changed,"pixel-test",storage);}catch(PixelMcpWriteTools.Rejected e){blocked="LEGACY_SOURCE_CHANGED".equals(e.code);}yes(blocked);
  }

  // Build75 hashed offers/service_catalog but knew no appointments; caller76 validates FIRST.
  JSONObject v75=PixelDriveStateTests.state();v75.remove("appointments");String oldHash=PixelMcpWriteTools.hash(v75);
  PixelDriveStateTests.Memory oldStorage=new PixelDriveStateTests.Memory();JSONObject published=DrivePrimaryState.recover(v75,"pixel-test",oldStorage).put("drive_legacy_base_hash",oldHash);oldStorage.publish(published,new JSONArray(),"commit-75",1,"","");
  JSONObject caller76=SyntheticBusinessData.object(v75.toString());DriveJournalRules.validateSnapshot(caller76);
  yes(DrivePrimaryState.recover(caller76,"pixel-test",oldStorage).getString("drive_parent_publication_id").equals("commit-75"));
  JSONObject different=SyntheticBusinessData.object(caller76.toString());different.getJSONArray("customers").getJSONObject(0).put("notes","genuine external variant");
  boolean denied=false;try{DrivePrimaryState.recover(different,"pixel-test",oldStorage);}catch(PixelMcpWriteTools.Rejected e){denied=e.code.equals("LEGACY_SOURCE_CHANGED");}yes(denied);
  JSONObject evidence=DrivePrimaryState.recover(different,"pixel-test",oldStorage,true);yes(evidence.getBoolean("drive_legacy_source_changed"));yes(!evidence.getJSONArray("customers").getJSONObject(0).optString("notes").equals("genuine external variant"));
  yes(!DrivePrimaryState.compatibleLegacyHash(SyntheticBusinessData.object(v75.toString()).put("appointments",new JSONArray().put(SyntheticBusinessData.object().put("id","qa_a"))),oldHash));
  System.out.println("PASS "+count+" raw/normalized/restart/legacy-hash compatibility checks; actual business changes still blocked, original input unchanged");
 }
}
