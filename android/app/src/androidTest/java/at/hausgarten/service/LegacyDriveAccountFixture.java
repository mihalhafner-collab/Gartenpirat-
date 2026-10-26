package at.hausgarten.service;

import android.accounts.*;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;

/** Google AccountManager owns tokens; private preferences contain only account/folder selectors. */
final class LegacyDriveAccountFixture {
    // Existing user-selected Drive files require explicit consent, not implicit app-created-file access.
    static final String SCOPE="oauth2:https://www.googleapis.com/auth/drive";
    static boolean configured(Context c){SharedPreferences p=c.getSharedPreferences("haus_garten",0);return "rest".equals(p.getString("drive_transport",""))&&!p.getString("drive_account","").isEmpty()&&LegacyDriveRestFixture.validId(p.getString("drive_folder_id",""));}
    static boolean available(Context c){return configured(c)||!c.getSharedPreferences("haus_garten",0).getString("drive_tree_uri","").isEmpty();}
    static boolean providerPermission(Context c){
        String saved=c.getSharedPreferences("haus_garten",0).getString("drive_tree_uri","");if(saved.isEmpty())return false;
        for(android.content.UriPermission permission:c.getContentResolver().getPersistedUriPermissions())
            if(permission.getUri().equals(android.net.Uri.parse(saved))&&permission.isReadPermission()&&permission.isWritePermission())return true;
        return false;
    }
    static String token(Context context,String account,Activity consent)throws Exception{
        throw new java.io.IOException("QA_LIVE_DRIVE_ACCOUNT_FORBIDDEN");
    }
    static LegacyDriveRestFixture client(Context c,PixelLocalStore store){SharedPreferences p=c.getSharedPreferences("haus_garten",0);return new LegacyDriveRestFixture(p.getString("drive_folder_id",""),()->token(c,p.getString("drive_account",""),null),new LegacyDriveRestFixture.HttpWire(),new LegacyDriveRestFixture.Checkpoints(){public String get(String key){return store.ownerValue(key,"");}public void put(String key,String value){store.putOwnerValue(key,value);}});}
    private LegacyDriveAccountFixture(){}
}
