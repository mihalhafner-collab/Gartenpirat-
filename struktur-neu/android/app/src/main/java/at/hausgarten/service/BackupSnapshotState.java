package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.IOException;

/** Read the captured DB/prefs using existing projection/catalog/tombstone rules, never live state. */
final class BackupSnapshotState {
    static JSONObject read(SQLiteDatabase db,JSONObject preferences,String initialData,String serviceCatalog)throws Exception {
        if(db.getVersion()<4||db.getVersion()>PixelLocalStore.SCHEMA_VERSION)throw new IOException("BACKUP_DATABASE_VERSION_UNSUPPORTED");
        String fallback=preferences.optString("drive_snapshot",initialData);
        JSONObject master=PixelLocalStore.businessTables(db).snapshot(fallback);
        String engine=master.optString("processing_engine");if(!engine.isEmpty()&&!engine.equals(PixelDriveState.ENGINE)&&!engine.equals(LocalAuthorityRules.ENGINE))throw new IOException("BACKUP_SNAPSHOT_ENGINE_UNSUPPORTED");
        PixelLocalStore.removeSnapshotPhotoBinaries(master);JSONArray invoices=master.optJSONArray("invoices");for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject invoice=invoices.getJSONObject(i);if(LegacyPdfAttachment.describe(invoice)!=null)invoice.remove("pdf_base64");}
        PixelDriveState.prepare(master);ServiceCatalogBootstrap.seed(master,()->serviceCatalog);LocalTestCleanup.overlay((key,otherwise)->DatabaseBackupSnapshot.ownerValue(db,key,otherwise),master);
        if(engine.equals(LocalAuthorityRules.ENGINE)||!DatabaseBackupSnapshot.ownerValue(db,"local_authority_contract","").isEmpty())master.put("processing_engine",LocalAuthorityRules.ENGINE).put("local_contract_version",LocalAuthorityRules.CONTRACT);
        OfferDecisionProjection.apply(db,master);LocalBillingProjection.apply(db,master);
        JSONArray actions=LocalPendingActions.read(db,false,id->photo(db,id),0);
        JSONObject state=new JSONObject().put("master",master).put("pending",DriveJournalRules.union(actions,master.optJSONArray("drive_review_actions"))).put("extended_business_fingerprint",BusinessStateFingerprint.read(db));
        try(Cursor session=db.rawQuery("SELECT id,state,updated_at FROM work_sessions WHERE state NOT IN ('FINALIZED','CANCELLED') ORDER BY rowid DESC LIMIT 1",null)){
            state.put("work_session",session.moveToFirst()?new JSONObject().put("id",session.getString(0)).put("state",session.getString(1)).put("updated_at",session.getString(2)):new JSONObject().put("state","IDLE"));
        }
        return state;
    }
    static JSONObject photo(SQLiteDatabase db,String id)throws Exception {
        try(Cursor row=db.rawQuery("SELECT content_sha256,mime_type,byte_size,storage_name,role FROM photo_attachments WHERE photo_id=?",new String[]{id})){
            if(!row.moveToFirst())return null;return new JSONObject().put("id",id).put("content_sha256",row.getString(0)).put("mime_type",row.getString(1)).put("byte_size",row.getLong(2)).put("storage_name",row.getString(3)).put("role",row.getString(4));
        }
    }
    private BackupSnapshotState(){}
}
