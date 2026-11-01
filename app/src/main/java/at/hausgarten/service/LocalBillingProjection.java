package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Joins the exact local source ledger without changing an invoice or historical work record. */
final class LocalBillingProjection {
    static void apply(PixelLocalStore store,JSONObject master)throws Exception {apply(store.getReadableDatabase(),master);}
    static void apply(SQLiteDatabase database,JSONObject master)throws Exception {
        if(!LocalAuthorityRules.ENGINE.equals(master.optString("processing_engine"))){master.remove("local_billing_sources");return;}
        JSONObject allocations=new JSONObject();String prefix="local_invoice_sources:";
        try(Cursor rows=database.rawQuery("SELECT key,value FROM owner_state WHERE key>=? AND key<? ORDER BY key",new String[]{prefix,prefix+'\uffff'})){while(rows.moveToNext())allocations.put(rows.getString(0).substring(prefix.length()),new JSONObject(rows.getString(1)));}
        JSONObject sources=new JSONObject().put("formatVersion",1).put("by_invoice",allocations);LocalBillingRules.validate(master,sources);master.put("local_billing_sources",sources);
    }
    private LocalBillingProjection(){}
}
