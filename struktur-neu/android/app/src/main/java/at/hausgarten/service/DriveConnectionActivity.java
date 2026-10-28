package at.hausgarten.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Historical internal name redirects to local backup. No Google connection. */
public final class DriveConnectionActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        startActivity(new Intent(this,FullBackupActivity.class));
        finish();
    }
}
