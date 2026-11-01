package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Reads immutable START assignments without changing timers or inferring historical object links. */
final class RevierSessionContexts {
    static JSONObject session(SQLiteDatabase database,String id)throws Exception {
        JSONObject result;try(Cursor row=database.rawQuery("SELECT id,customer_id,order_id,state FROM work_sessions WHERE id=?",new String[]{id})){if(!row.moveToFirst())return null;result=new JSONObject().put("id",row.getString(0)).put("customer_id",row.getString(1)).put("order_id",row.getString(2)).put("state",row.getString(3));}
        try(Cursor start=database.rawQuery("SELECT payload FROM work_session_events WHERE session_id=? AND event_type='START' ORDER BY rowid",new String[]{id})){if(start.moveToFirst()){JSONObject origin=new JSONObject(start.getString(0));for(String key:new String[]{"property_id","visit_id"})if(origin.has(key))result.put(key,origin.get(key));result.put("_start_customer_id",origin.optString("customer_id")).put("_start_order_id",origin.optString("order_id"));if(start.moveToNext())throw new PixelMcpWriteTools.Rejected("REVIER_RELATION_INVALID","Eine Sitzung besitzt mehrere ursprüngliche Startzuordnungen.");}}
        return result;
    }
    static JSONArray all(SQLiteDatabase database)throws Exception {JSONArray out=new JSONArray();try(Cursor rows=database.rawQuery("SELECT id FROM work_sessions ORDER BY id",null)){while(rows.moveToNext())out.put(session(database,rows.getString(0)));}try(Cursor orphan=database.rawQuery("SELECT e.id FROM work_session_events e LEFT JOIN work_sessions s ON s.id=e.session_id WHERE e.event_type='START' AND s.id IS NULL LIMIT 1",null)){if(orphan.moveToFirst())throw new PixelMcpWriteTools.Rejected("REVIER_RELATION_INVALID","Ursprüngliche Startzuordnung besitzt keine Sitzung.");}return out;}
    private RevierSessionContexts(){}
}
