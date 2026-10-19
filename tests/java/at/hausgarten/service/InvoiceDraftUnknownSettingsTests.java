package at.hausgarten.service;
import org.json.*;
public final class InvoiceDraftUnknownSettingsTests {
 public static void main(String[] ignored)throws Exception{ SyntheticBusinessData.verify();
  int count=0;for(boolean present:new boolean[]{false,true}){
   JSONObject s=PixelDriveStateTests.financialState();if(present)s.getJSONObject("business_settings").put("tax_mode","");else s.getJSONObject("business_settings").remove("tax_mode");
   JSONObject draft=PixelDriveStateTests.invoice(s);if(!draft.getString("tax_mode").equals("unconfigured"))throw new AssertionError("Do not invent tax status");count++;
   JSONObject edit=SyntheticBusinessData.object(draft.toString()).put("notes","synthetic correction");InvoiceDraftRules.normalize(edit,draft,s);if(!edit.getString("tax_mode").equals("unconfigured"))throw new AssertionError("Unknown status must remain editable");count++;
   boolean rejected=false;try{InvoiceIssueRules.prepare(s,PixelDriveStateTests.issue(s,draft,"unconfigured-tax-issue"),java.time.LocalDate.parse("2026-10-18"));}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("Unknown tax must not issue a final invoice");count++;
  }
  System.out.println("PASS "+count+" missing/blank tax settings: drafts editable, no invented tax mode, final issue blocked");
 }
}
