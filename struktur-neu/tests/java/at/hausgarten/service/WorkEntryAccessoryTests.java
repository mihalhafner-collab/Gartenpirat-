package at.hausgarten.service;

import org.json.*;
import java.nio.file.*;
import java.util.*;

/** Free invented work/chain records and a generated one-pixel image; no device data. */
public final class WorkEntryAccessoryTests {
    static int checks;
    static void yes(boolean value) { if (!value) throw new AssertionError("work/accessory check " + checks); checks++; }
    interface Check { void run() throws Exception; }
    static void rejects(Check action) throws Exception { try { action.run(); throw new AssertionError("Expected invalid input"); } catch (IllegalArgumentException expected) { checks++; } }
    static JSONObject work() { return new JSONObject().put("work_date", "2026-10-30").put("activity", "QA synthetic garden").put("start_time", "08:00").put("end_time", "12:00"); }
    static JSONObject item(String id) { return new JSONObject().put("id", id).put("type", "chain").put("name", "QA chain " + id); }
    public static void main(String[] ignored) throws Exception {
        for (int pause : new int[]{15,30,45,60,23,0}) {
            JSONObject row = work().put("break_minutes", pause);
            WorklogRules.validate(row, true);
            double hours = Math.round((240-pause)/60.0*100)/100.0;
            yes(row.getDouble("hours") == hours);
            JSONObject reopened = new JSONObject(row.toString()); WorklogRules.validate(reopened, false);
            yes(reopened.getDouble("hours") == hours && reopened.getInt("break_minutes") == pause);
        }
        for (String value : new String[]{"50", "100", "150", "73.40", "27,35", "0"}) {
            JSONObject row = work().put("disposal_cost", value); WorklogRules.validate(row, true);
            JSONObject reopened = new JSONObject(row.toString()); WorklogRules.validate(reopened, false);
            yes(new java.math.BigDecimal(reopened.get("disposal_cost").toString()).compareTo(new java.math.BigDecimal(value.replace(',','.'))) == 0);
            yes(PixelMcpWriteTools.base(reopened).has("disposal_cost"));
        }
        JSONObject none = work(); WorklogRules.validate(none,true); yes(!none.has("disposal_cost"));
        JSONObject cleared = work().put("disposal_cost", ""); WorklogRules.validate(cleared,true); yes(!cleared.has("disposal_cost"));
        JSONObject net = work().put("start_time", "").put("end_time", "").put("hours", 3.5).put("break_minutes",30); WorklogRules.validate(net,true); yes(net.getDouble("hours")==3.5);
        rejects(() -> WorklogRules.validate(work().put("break_minutes","nonsense"),true));
        rejects(() -> WorklogRules.validate(work().put("break_minutes",240),true));
        rejects(() -> WorklogRules.validate(work().put("disposal_cost","x"),true));
        Path directory = Files.createTempDirectory("qa_accessories_");
        try {
            EquipmentProfilePhotoTests.Memory memory = new EquipmentProfilePhotoTests.Memory(directory.toFile());
            RevierApplicationService app = new RevierApplicationService(memory, RevierDomainTests.CLOCK);
            JSONObject machine = app.execute("save_equipment", EquipmentProfilePhotoTests.args("qa_machine_create").put("name","QA Saw")).getJSONObject("record");
            String id = machine.getString("id");
            memory.upload("qa_chain_upload");
            JSONObject one = item("qa_chain_one").put("manufacturer","QA maker").put("model","QA model").put("pitch","3/8 low profile").put("gauge","1.1 mm").put("drive_links","45").put("file_size","4 mm").put("photo_upload_id","qa_chain_upload");
            JSONObject two = item("qa_chain_two");
            JSONObject args = EquipmentProfilePhotoTests.update(app,"qa_accessories_save").put("accessories",new JSONArray().put(one).put(two)).put("current_chain_id","qa_chain_one");
            app.execute("save_equipment",args);
            yes(app.execute("save_equipment",args).getBoolean("replayed"));
            RevierApplicationService reopened = new RevierApplicationService(memory,RevierDomainTests.CLOCK);
            JSONObject stored = EquipmentProfilePhotoTests.current(reopened);
            yes(stored.getString("current_chain_id").equals("qa_chain_one"));
            JSONObject accessory = stored.getJSONArray("accessories").getJSONObject(0);
            for (String key : new String[]{"name","manufacturer","model","pitch","gauge","drive_links","file_size"}) yes(accessory.getString(key).equals(one.getString(key)));
            yes(accessory.getString("equipment_id").equals(id));
            yes(!stored.getJSONArray("accessories").getJSONObject(1).has("pitch"));
            String photo = accessory.getJSONObject("photo").getString("id");
            yes(reopened.equipmentPhoto(photo).getString("equipment_id").equals(id));
            yes(reopened.validateRelationships().getString("status").equals("PASS"));
            JSONObject stale = EquipmentProfilePhotoTests.update(reopened,"qa_stale_chain").put("current_chain_id","qa_chain_one");
            reopened.execute("save_equipment",EquipmentProfilePhotoTests.update(reopened,"qa_chain_switch").put("current_chain_id","qa_chain_two"));
            yes(EquipmentProfilePhotoTests.current(reopened).getString("current_chain_id").equals("qa_chain_two"));
            rejects(() -> reopened.execute("save_equipment",stale));
            rejects(() -> reopened.execute("save_equipment",EquipmentProfilePhotoTests.update(reopened,"qa_foreign_chain").put("current_chain_id","qa_other_machine_chain")));
            rejects(() -> reopened.execute("save_equipment",EquipmentProfilePhotoTests.update(reopened,"qa_duplicate_chain").put("accessories",new JSONArray().put(two).put(two))));
            String before = memory.values.toString();
            memory.abortReceipt = true;
            try { reopened.execute("save_equipment",EquipmentProfilePhotoTests.update(reopened,"qa_chain_abort").put("current_chain_id","")); throw new AssertionError("Expected rollback"); } catch(IllegalStateException expected) { checks++; } finally { memory.abortReceipt=false; }
            yes(memory.values.toString().equals(before));
            reopened.execute("save_equipment",EquipmentProfilePhotoTests.update(reopened,"qa_no_current_chain").put("current_chain_id",""));
            yes(EquipmentProfilePhotoTests.current(reopened).getString("current_chain_id").isEmpty());
            yes(reopened.equipmentPhoto(photo)!=null);
            McpSchema.validate(args, LocalWorkflowTools.schema("save_equipment")); checks++;
            yes(Arrays.asList(PixelMcpWriteTools.EDITABLE).contains("disposal_cost"));
            System.out.println("PASS " + checks + " work duration/disposal and machine accessory roundtrip/current-chain/photo/CAS/replay/rollback checks");
        } finally { try(var files=Files.walk(directory)) { files.sorted(Comparator.reverseOrder()).forEach(p -> {try {Files.delete(p);} catch(Exception e) {throw new RuntimeException(e);} });} }
    }
}
