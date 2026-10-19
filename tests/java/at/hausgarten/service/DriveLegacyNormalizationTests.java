package at.hausgarten.service;
import org.json.*;
public final class DriveLegacyNormalizationTests {
 static int count;static void yes(boolean b){if(!b)throw new AssertionError("legacy normalization "+count);count++;}
 public static void main(String[] args)throws Exception{
  for(boolean oldRawHash:new boolean[]{false,true}){
   JSONObject legacy=PixelDriveStateTests.state();legacy.remove("offers");legacy.remove("service_catalog");String before=PixelMcpWriteTools.hash(legacy);
   JSONObject normalized=new JSONObject(legacy.toString());DriveJournalRules.validateSnapshot(normalized);
   PixelDriveStateTests.Memory storage=new PixelDriveStateTests.Memory();JSONObject initial=DrivePrimaryState.recover(normalized,"pixel-test",storage);
   if(oldRawHash)initial.put("drive_legacy_base_hash",before);
   storage.publish(initial,new JSONArray(),"commit-legacy",1,"","");
   JSONObject reopened=DrivePrimaryState.recover(legacy,"pixel-test",storage);yes(reopened.getJSONArray("offers").length()==0);yes(reopened.getString("drive_legacy_base_hash").equals(PixelMcpWriteTools.hash(normalized)));yes(before.equals(PixelMcpWriteTools.hash(legacy)));
   JSONObject withCollections=new JSONObject(legacy.toString()).put("offers",new JSONArray()).put("service_catalog",new JSONArray());
   // A historic raw hash is valid only for the exact observed raw input, never a different input.
   if(!oldRawHash)yes(DrivePrimaryState.recover(withCollections,"pixel-test",storage).getString("drive_parent_publication_id").equals("commit-legacy"));
   JSONObject changed=new JSONObject(legacy.toString());changed.getJSONArray("customers").getJSONObject(0).put("notes","real parallel modification");
   boolean blocked=false;try{DrivePrimaryState.recover(changed,"pixel-test",storage);}catch(PixelMcpWriteTools.Rejected e){blocked="LEGACY_SOURCE_CHANGED".equals(e.code);}yes(blocked);
  }
  System.out.println("PASS "+count+" raw/normalized/restart/legacy-hash compatibility checks; actual business changes still blocked, original input unchanged");
 }
}
