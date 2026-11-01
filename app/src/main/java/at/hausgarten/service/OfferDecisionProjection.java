package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Technical decision evidence joins the read view; canonical offer/PDF hashes stay unchanged. */
final class OfferDecisionProjection {
    static void apply(PixelLocalStore store,JSONObject master)throws Exception {apply(store.getReadableDatabase(),master);}
    static void apply(SQLiteDatabase database,JSONObject master)throws Exception {
        JSONObject decisions=new JSONObject(),revisions=new JSONObject();String prefix=OfferDecisionRules.PREFIX;
        try(Cursor rows=database.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){
            while(rows.moveToNext()){String key=rows.getString(0);if(!key.startsWith(prefix+"current:")&&!key.startsWith(prefix+"revision:"))continue;JSONObject value=new JSONObject(rows.getString(1));OfferDecisionRules.version(value);if(key.startsWith(prefix+"current:"))decisions.put(value.getString("offer_id"),value);else revisions.put(value.getString("offer_id"),value);}
        }
        if(decisions.length()>0)master.put("offer_decisions",decisions);else master.remove("offer_decisions");if(revisions.length()>0)master.put("offer_revisions",revisions);else master.remove("offer_revisions");
    }
    private OfferDecisionProjection(){}
}
