package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.IOException;

/** One journal view for live UI/MCP reads and captured backup reads. */
final class LocalPendingActions {
    interface Photos {JSONObject get(String id)throws Exception;}
    static JSONArray read(SQLiteDatabase db,boolean forDrive,Photos photos,long now)throws Exception {
        JSONArray result=new JSONArray();
        try(Cursor rows=db.rawQuery("SELECT id,kind,length(payload),created_at FROM pending WHERE kind!='mcp_receipt' ORDER BY created_at ASC,rowid ASC",null)){
            while(rows.moveToNext()){
                String id=rows.getString(0),kind=rows.getString(1);int length=rows.getInt(2);StringBuilder text=new StringBuilder();
                for(int offset=0;offset<length;offset+=32768)try(Cursor part=db.rawQuery("SELECT substr(payload,?,32768) FROM pending WHERE id=?",new String[]{Integer.toString(offset+1),id})){
                    if(!part.moveToFirst())throw new IOException("LOCAL_PENDING_ROW_MISSING");text.append(part.getString(0));
                }
                JSONObject payload=new JSONObject(text.toString());
                if(forDrive&&(payload.optBoolean("_local_only")||payload.optBoolean("test_record")))continue;
                if(forDrive&&("delete".equals(kind)||"archive".equals(kind))&&payload.optLong("_undo_until",0)>now)continue;
                payload.remove("_undo_until");
                if(!forDrive&&"photo".equals(kind))payload=PhotoJournalRules.forSnapshot(payload,photos.get(payload.optString("id")));
                result.put(new JSONObject().put("client_id",id).put("kind",kind).put("payload",payload).put("created_at",rows.getString(3)));
            }
        }
        return result;
    }
    private LocalPendingActions(){}
}
