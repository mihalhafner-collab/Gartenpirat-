package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Extends the existing SQLite owner state. Existing business entities stay canonical. */
final class RevierLocalRepository implements RevierRepository {
    interface InspectionPlanner { JSONObject plan(JSONObject appointmentArgs)throws Exception; }
    private final PixelLocalStore store;private final InspectionPlanner inspections;
    RevierLocalRepository(PixelLocalStore store,InspectionPlanner inspections){this.store=store;this.inspections=inspections;}
    public JSONObject effective()throws Exception{return PixelMcpReadTools.effective(store.getState());}
    public String get(String key,String fallback){return store.ownerValue(key,fallback);}
    public void put(String key,String value){store.putOwnerValue(key,value);}
    public JSONArray list(String prefix)throws Exception {
        JSONArray out=new JSONArray();SQLiteDatabase db=store.getReadableDatabase();
        // Each new entity/version/audit row is bounded independently; no growing whole-state JSON.
        try(Cursor rows=db.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){
            while(rows.moveToNext())out.put(new JSONObject(rows.getString(1)));
        }return out;
    }
    public JSONObject transaction(Work work)throws Exception {
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{JSONObject result=work.run();db.setTransactionSuccessful();return result;}finally{db.endTransaction();}
    }
    public JSONObject planInspection(JSONObject appointmentArgs)throws Exception {
        if(inspections==null)throw new PixelMcpWriteTools.Rejected("INSPECTION_ADAPTER_UNAVAILABLE","Besichtigung kann gerade nicht geplant werden. Eingaben bleiben erhalten.");
        return inspections.plan(appointmentArgs);
    }
    public JSONObject changeInspection(String operation,JSONObject args)throws Exception {
        if(!operation.equals("update_appointment"))throw new IllegalArgumentException("Ungültige Terminänderung.");
        JSONObject routed=new JSONObject(args.toString()).put("_operation",operation);return inspections.plan(routed);
    }
    public String createDirectOrder(JSONObject args)throws Exception {return store.saveEditorRecord("order",args);}
    public JSONObject session(String id)throws Exception {return RevierSessionContexts.session(store.getReadableDatabase(),id);}
    public JSONArray sessionContexts()throws Exception {return RevierSessionContexts.all(store.getReadableDatabase());}
    public JSONObject currentSession()throws Exception {return store.workSessions().current();}
    public JSONObject prepareEquipmentProfilePhoto(String upload,String equipment,String request)throws Exception {return store.prepareEquipmentProfilePhoto(upload,equipment,request);}
    public JSONObject prepareEquipmentProfilePhotoDraft(JSONObject source,String equipment,String request)throws Exception {return store.prepareEquipmentProfilePhotoDraft(source,equipment,request);}
    public void verifyEquipmentProfilePhoto(JSONObject photo)throws Exception {EquipmentProfilePhotoRules.attachment(photo,store.photoAttachment(photo.getString("id")));store.photoAttachmentStore().read(photo);}
    public void assertSourceCorrectionAllowed(String type,String id)throws Exception {
        if(!type.equals("material_movement"))return;
        try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT key FROM owner_state WHERE key>=? AND key<?",new String[]{"local_invoice_material_sources:","local_invoice_material_sources:\uffff"})){
            while(rows.moveToNext()){JSONObject allocations=new JSONObject(store.ownerValue(rows.getString(0),"{}"));if(allocations.has(id))throw new PixelMcpWriteTools.Rejected("INVOICED_SOURCE_CORRECTION_REQUIRED","Materialquelle wurde bereits abgerechnet. Zuerst den erforderlichen Korrekturbeleg führen; keine unbemerkte Gegenbuchung.");}
        }
    }
    public JSONArray rawEntries(String prefix)throws Exception {JSONArray out=new JSONArray();try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){while(rows.moveToNext())out.put(new JSONObject().put("key",rows.getString(0)).put("value",rows.getString(1)));}return out;}
}
