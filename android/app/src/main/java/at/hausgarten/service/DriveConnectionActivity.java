package at.hausgarten.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Old internal activity name routes only to explicit encrypted-backup setup. */
public final class DriveConnectionActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        startActivity(new Intent(this,FullBackupActivity.class));
        finish();
    }
}
