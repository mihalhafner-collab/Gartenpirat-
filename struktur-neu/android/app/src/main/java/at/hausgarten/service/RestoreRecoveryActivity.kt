package at.hausgarten.service

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/** Recovery controls deliberately work without opening the business database. */
class RestoreRecoveryActivity : Activity() {
    public override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        title = "Wiederherstellung prüfen"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = NativeUi.dp(this, 16)
        root.setPadding(pad, pad, pad, pad)
        root.fitsSystemWindows = true
        val title = NativeUi.heading(NativeUi.text(TextView(this)))
        title.text = "Wiederherstellung prüfen"
        root.addView(title)
        val status = NativeUi.text(TextView(this))
        root.addView(status)
        val cold = RestoreStartupProtection.requiresColdRestart(this)
        status.text = if (cold) {
            "Der Bestand wurde geprüft aktiviert. Schließe die App und öffne sie erneut. Rechnungen bleiben bis zum aktuellen Belegabgleich gesperrt."
        } else {
            "Die Wiederherstellung benötigt eine Prüfung. Der bisherige Bestand oder seine Rückfallkopie bleibt erhalten. Es wurde keine Rechnungsausgabe freigegeben."
        }
        if (!cold) {
            val cancel = NativeUi.style(Button(this), 1)
            cancel.text = "Wiederherstellung abbrechen"
            root.addView(cancel)
            cancel.setOnClickListener {
                AlertDialog.Builder(this).setTitle("Wiederherstellung abbrechen?")
                    .setMessage("Nur ein noch nicht begonnener oder vollständig zurückgerollter Austausch kann abgebrochen werden.")
                    .setNegativeButton("Zurück", null)
                    .setPositiveButton("Abbrechen") { _, _ ->
                        try {
                            val pending = BackupRestoreService.pendingStatus(this)
                            BackupRestoreService(this).cancelActivation("cancel_" + UUID.randomUUID(),
                                pending.getString("restore_id"), pending.getString("preview_hash"))
                            status.text = "Abbruch gespeichert. Schließe die App und öffne sie erneut."
                            cancel.isEnabled = false
                        } catch (protectedState: Exception) {
                            status.text = "Abbruch ist in diesem Zustand gesperrt. Die Rückfallkopie bleibt geschützt; technischer Wiederanlauf erforderlich."
                        }
                    }.show()
            }
        }
        val close = NativeUi.style(Button(this), 0)
        close.text = "App schließen"
        root.addView(close)
        close.setOnClickListener {
            finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }
}
