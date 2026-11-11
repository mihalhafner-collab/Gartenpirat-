package at.hausgarten.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Recovery controls deliberately work without opening the business database. */
class RestoreRecoveryActivity : ComponentActivity() {
    private var status by
        mutableStateOf(
            "Die Wiederherstellung benötigt eine Prüfung. Der unterbrochene Austausch muss abgeschlossen werden."
        )
    private var cold by mutableStateOf(true)
    private var busy by mutableStateOf(true)
    private var cancelled by mutableStateOf(false)
    private var confirmCancellation by mutableStateOf(false)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Wiederherstellung prüfen"
        nativeContent("Wiederherstellung prüfen", showBack = false) {
            Text(status)
            if (!cold)
                NativeButton(
                    enabled = !busy && !cancelled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { confirmCancellation = true },
                ) {
                    Text("Wiederherstellung abbrechen")
                }
            NativeButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
            ) {
                Text("App schließen")
            }
            if (confirmCancellation)
                AlertDialog(
                    onDismissRequest = { confirmCancellation = false },
                    title = { Text("Wiederherstellung abbrechen?") },
                    text = {
                        Text(
                            "Nur ein noch nicht begonnener oder vollständig zurückgerollter Austausch kann abgebrochen werden."
                        )
                    },
                    dismissButton = {
                        NativeTextButton(onClick = { confirmCancellation = false }) {
                            Text("Zurück")
                        }
                    },
                    confirmButton = {
                        NativeTextButton(
                            onClick = {
                                confirmCancellation = false
                                cancelRestore()
                            }
                        ) {
                            Text("Abbrechen")
                        }
                    },
                )
        }
        lifecycleScope.launch {
            try {
                cold =
                    withContext(Dispatchers.IO) {
                        try {
                            RevierApplication.awaitStartup(this@RestoreRecoveryActivity)
                        } catch (protectedState: IllegalStateException) {
                            if (protectedState.message != "RESTORE_RESTART_REQUIRED")
                                throw protectedState
                        }
                        RestoreStartupProtection.requiresColdRestart(this@RestoreRecoveryActivity)
                    }
                if (cold)
                    status =
                        "Der Bestand wurde geprüft aktiviert. Schließe die App und öffne sie erneut. Rechnungen bleiben bis zum aktuellen Belegabgleich gesperrt."
                busy = false
            } catch (_: Exception) {
                // Keep cancellation unavailable if the startup gate itself could not be observed.
                status =
                    "Abbruch ist in diesem Zustand gesperrt. Der Austausch muss zuerst abgeschlossen werden."
            }
        }
    }

    private fun cancelRestore() {
        if (busy || cold || cancelled) return
        busy = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pending = BackupRestoreService.pendingStatus(this@RestoreRecoveryActivity)
                    BackupRestoreService(this@RestoreRecoveryActivity)
                        .cancelActivation(
                            "cancel_" + UUID.randomUUID(),
                            pending.getString("restore_id"),
                            pending.getString("preview_hash"),
                        )
                }
                status = "Abbruch gespeichert. Schließe die App und öffne sie erneut."
                cancelled = true
            } catch (protectedState: Exception) {
                status =
                    "Abbruch ist in diesem Zustand gesperrt. Der Austausch muss zuerst abgeschlossen werden."
            } finally {
                busy = false
            }
        }
    }
}
