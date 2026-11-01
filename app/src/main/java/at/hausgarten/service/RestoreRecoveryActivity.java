package at.hausgarten.service;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.util.UUID;

/** Recovery controls deliberately work without opening the business database. */
public final class RestoreRecoveryActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);setTitle("Wiederherstellung prüfen");LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=NativeUi.dp(this,16);root.setPadding(pad,pad,pad,pad);root.setFitsSystemWindows(true);TextView title=NativeUi.heading(NativeUi.text(new TextView(this)));title.setText("Wiederherstellung prüfen");root.addView(title);TextView status=NativeUi.text(new TextView(this));root.addView(status);boolean cold=RestoreStartupProtection.requiresColdRestart(this);status.setText(cold?"Der Bestand wurde geprüft aktiviert. Schließe die App und öffne sie erneut. Rechnungen bleiben bis zum aktuellen Belegabgleich gesperrt.":"Die Wiederherstellung benötigt eine Prüfung. Der bisherige Bestand oder seine Rückfallkopie bleibt erhalten. Es wurde keine Rechnungsausgabe freigegeben.");
        if(!cold){Button cancel=NativeUi.style(new Button(this),1);cancel.setText("Wiederherstellung abbrechen");root.addView(cancel);cancel.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Wiederherstellung abbrechen?").setMessage("Nur ein noch nicht begonnener oder vollständig zurückgerollter Austausch kann abgebrochen werden.").setNegativeButton("Zurück",null).setPositiveButton("Abbrechen",(dialog,which)->{try{JSONObject pending=BackupRestoreService.pendingStatus(this);new BackupRestoreService(this).cancelActivation("cancel_"+UUID.randomUUID(),pending.getString("restore_id"),pending.getString("preview_hash"));status.setText("Abbruch gespeichert. Schließe die App und öffne sie erneut.");cancel.setEnabled(false);}catch(Exception protectedState){status.setText("Abbruch ist in diesem Zustand gesperrt. Die Rückfallkopie bleibt geschützt; technischer Wiederanlauf erforderlich.");}}).show());}
        Button close=NativeUi.style(new Button(this),0);close.setText("App schließen");root.addView(close);close.setOnClickListener(v->{finishAffinity();android.os.Process.killProcess(android.os.Process.myPid());});ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
}
