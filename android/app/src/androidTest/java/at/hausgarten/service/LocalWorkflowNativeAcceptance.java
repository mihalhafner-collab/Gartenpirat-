package at.hausgarten.service;

import org.json.*;

/** Positive new tools cross the actual JSON-RPC/owner/SQLite boundary in disposable stores. */
final class LocalWorkflowNativeAcceptance {
    private static JSONObject rpc(OwnerNativeAcceptance.Fixture f,String name,JSONObject args)throws Exception {
        JSONObject response=f.tools.rpc(new JSONObject().put("jsonrpc","2.0").put("id","qa_local_rpc").put("method","tools/call").put("params",new JSONObject().put("name",name).put("arguments",args))).getJSONObject("result");
        JSONObject body=response.getJSONObject("structuredContent");if(response.optBoolean("isError"))throw new AssertionError("QA local tool failed: "+name+" code="+body.optJSONObject("error").optString("code"));return body.getJSONObject("data");
    }
    private static JSONObject request(String id)throws Exception{return new JSONObject().put("request_id",id);}
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("local workflow native RPC object inquiry care visit and manual tour share one persistent domain",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            java.time.LocalDate anchor=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Vienna")).plusDays(1);String day=anchor.toString(),horizon=anchor.plusDays(14).toString();
            String original=PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master").getJSONArray("customers"));
            test.yes(rpc(f,"list_properties",new JSONObject()).getJSONArray("items").length()==0);
            JSONObject create=request("qa_rpc_property_create").put("customer_id","qa_c").put("name","QA Objekt").put("address","QA Testweg").put("district","QA Nord");JSONObject property=rpc(f,"create_property",create).getJSONObject("record");String propertyId=property.getString("id");
            test.yes(rpc(f,"create_property",create).getBoolean("replayed"));test.yes(rpc(f,"list_properties",new JSONObject()).getInt("total")==1);
            JSONObject edit=request("qa_rpc_property_edit").put("property_id",propertyId).put("expected_record_hash",property.getString("record_hash")).put("updates",new JSONObject().put("notes","QA Zugang bestätigt"));rpc(f,"update_property",edit);
            JSONObject stale=request("qa_rpc_property_stale").put("property_id",propertyId).put("expected_record_hash",property.getString("record_hash")).put("updates",new JSONObject().put("notes","QA stale must not overwrite"));try{f.call("update_property",stale);throw new AssertionError("Stale object editor wrote data");}catch(PixelMcpWriteTools.Rejected expected){test.yes(expected.code.equals("WRITE_CONFLICT"));}
            JSONObject inquiry=rpc(f,"create_inquiry",request("qa_rpc_inquiry_create").put("customer_id","qa_c").put("property_id",propertyId).put("title","QA Pflegeanfrage")).getJSONObject("record");test.yes(rpc(f,"get_inquiry",new JSONObject().put("inquiry_id",inquiry.getString("id"))).getJSONObject("record").getString("property_id").equals(propertyId));
            JSONObject care=rpc(f,"create_care_plan",request("qa_rpc_care_create").put("property_id",propertyId).put("title","QA Wochenpflege").put("anchor_date",day).put("interval_days",7).put("planned_minutes",150)).getJSONObject("record");
            JSONObject generate=request("qa_rpc_care_generate").put("care_plan_id",care.getString("id")).put("from_date",day).put("to_date",horizon);rpc(f,"generate_care_visits",generate);test.yes(rpc(f,"generate_care_visits",generate).getBoolean("replayed"));
            JSONArray visits=rpc(f,"list_visits",new JSONObject().put("property_id",propertyId)).getJSONArray("items");test.yes(visits.length()==3);String first=visits.getJSONObject(0).getString("id");
            JSONObject extra=rpc(f,"plan_visit",request("qa_rpc_visit_extra").put("property_id",propertyId).put("date",day).put("title","QA Zweiter Besuch").put("planned_minutes",30)).getJSONObject("record");
            JSONObject tour=rpc(f,"get_day_tour",new JSONObject().put("date",day));rpc(f,"set_day_tour",request("qa_rpc_tour_set").put("date",day).put("visit_ids",new JSONArray().put(first).put(extra.getString("id"))).put("expected_record_hash",tour.getString("record_hash")));
            tour=rpc(f,"get_day_tour",new JSONObject().put("date",day));rpc(f,"move_tour_stop",request("qa_rpc_tour_move").put("date",day).put("visit_id",extra.getString("id")).put("direction","up").put("expected_record_hash",tour.getString("record_hash")));
            test.yes(rpc(f,"get_day_tour",new JSONObject().put("date",day)).getJSONObject("record").getJSONArray("visit_ids").getString(0).equals(extra.getString("id")));
            test.yes(rpc(f,"search_local",new JSONObject().put("query","Wochenpflege").put("types",new JSONArray().put("care_plan"))).getInt("total")>0);
            test.yes(PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master").getJSONArray("customers")).equals(original));
        }});
        test.probe("local workflow native RPC stock movement and defective equipment retain exact decimal audit",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            JSONObject property=rpc(f,"create_property",request("qa_rpc_resource_property").put("customer_id","qa_c").put("name","QA Materialobjekt")).getJSONObject("record");
            JSONObject material=rpc(f,"save_material",request("qa_rpc_material_create").put("name","QA Sack").put("unit","Sack").put("billing_price_minor",900)).getJSONObject("record");String materialId=material.getString("id");
            rpc(f,"record_material_movement",request("qa_rpc_material_receive").put("material_id",materialId).put("kind","receive").put("quantity_decimal","5").put("reason","QA Zugang").put("expected_record_hash",material.getString("record_hash")));
            material=rpc(f,"list_materials",new JSONObject()).getJSONArray("items").getJSONObject(0);JSONObject consume=request("qa_rpc_material_consume").put("material_id",materialId).put("kind","consume").put("quantity_decimal","2").put("reason","QA Verbrauch").put("property_id",property.getString("id")).put("expected_record_hash",material.getString("record_hash"));JSONObject movement=rpc(f,"record_material_movement",consume);test.yes(movement.getString("stock_decimal").equals("3")&&rpc(f,"record_material_movement",consume).getBoolean("replayed"));
            JSONObject device=rpc(f,"save_equipment",request("qa_rpc_equipment_create").put("name","QA Gerät").put("condition","defective")).getJSONObject("record");JSONObject usage=rpc(f,"record_equipment_usage",request("qa_rpc_equipment_usage").put("equipment_id",device.getString("id")).put("property_id",property.getString("id")).put("expected_record_hash",device.getString("record_hash")));test.yes(usage.getJSONArray("equipment_warnings").length()>0);
            test.yes(rpc(f,"list_equipment",new JSONObject()).getInt("total")==1);
        }});
        test.probe("local workflow native RPC full-backup status redacts secrets and request retry is one local intent",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);try{
                JSONObject empty=rpc(f,"get_full_backup_status",new JSONObject());test.yes(!empty.getBoolean("key_configured")&&empty.getJSONArray("jobs").length()==0);
                test.yes(rpc(f,"get_restore_status",new JSONObject()).getString("phase").equals("NONE"));
                String code=BackupCrypto.newRecoveryCode();keys.confirmAndStore("qa_rpc_backup_key_setup",code,code);BackupSettingsRepository setup=new BackupSettingsRepository(f.context,f.store);setup.configure("qa_rpc_backup_setup",setup.read().getString("revision"),false,"qa_backup@example.invalid","qa_backup_rpc_folder",true);setup.saveBrowser("qa_rpc_backup_drafts",setup.browserRevision(),new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_draft","QA pending editor")));
                JSONObject status=rpc(f,"get_full_backup_status",new JSONObject());JSONObject args=request("qa_rpc_full_backup_request").put("expected_settings_hash",status.getString("settings_hash"));JSONObject accepted=rpc(f,"request_full_backup",args);test.yes(accepted.getBoolean("accepted_locally")&&!accepted.getBoolean("cloud_confirmed"));test.yes(rpc(f,"request_full_backup",args).getBoolean("replayed"));
                status=rpc(f,"get_full_backup_status",new JSONObject());test.yes(status.getInt("total")==1&&!status.toString().contains(code)&&!status.toString().contains("QA pending editor")&&!status.toString().contains("upload_id="));
                JSONObject job=status.getJSONArray("jobs").getJSONObject(0);JSONObject retry=request("qa_rpc_full_backup_retry").put("backup_id",job.getString("backup_id")).put("expected_record_hash",job.getString("record_hash"));test.yes(rpc(f,"retry_full_backup",retry).getString("backup_id").equals(accepted.getString("backup_id")));test.yes(rpc(f,"retry_full_backup",retry).getBoolean("replayed"));
                test.yes(rpc(f,"get_full_backup_status",new JSONObject()).getInt("total")==1);
                BackupDeviceGuard identity=new BackupDeviceGuard(f.context);identity.advanceForRestore("qa_rpc_backup_restore_epoch",identity.current().getString("restoreEpoch"));
                JSONObject denied=f.tools.rpc(new JSONObject().put("jsonrpc","2.0").put("id","qa_backup_old_epoch").put("method","tools/call").put("params",new JSONObject().put("name","request_full_backup").put("arguments",args))).getJSONObject("result");
                test.yes(denied.getBoolean("isError")&&denied.getJSONObject("structuredContent").getJSONObject("error").getString("code").equals("BACKUP_CONTROL_REPLAY_MISMATCH"));
            }finally{BackupCoreNativeAcceptance.deleteQaKey(f,keys);}
        }});
    }
}
