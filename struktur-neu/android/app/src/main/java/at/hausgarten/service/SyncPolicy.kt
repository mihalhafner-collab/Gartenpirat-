package at.hausgarten.service

import org.json.*

/** One public state machine for UI, MCP and background scheduling. No business data. */
class SyncPolicy {
    private constructor() {
    }
    companion object {
        @JvmField val MAX_ATTEMPTS: Int = 8
        @JvmStatic fun automaticAllowed(s: JSONObject, now: Long): Boolean {
            return ((((s.optBoolean("configured") && ((s.optBoolean("automatic_enabled", true) || s.optBoolean("manual_requested")))) && ((!s.optBoolean("user_action_required") || ((s.optString("last_error_code").equals("REMOTE_STATE_REFERENCE_MISSING") && (s.optInt("retry_count") < MAX_ATTEMPTS)) && (s.optLong("next_retry") > 0))))) && (((s.optInt("retry_count") < MAX_ATTEMPTS) || s.optString("last_error_code").equals("NETWORK_UNAVAILABLE")))) && (s.optLong("next_retry") <= now))
        }
        @JvmStatic fun retryDelay(attempt: Int, retryAfter: Long): Long {
            return Math.max(Math.min((((24 * 60) * 60) * 1000L), Math.max(0, retryAfter)), Math.min(((15 * 60) * 1000L), (15000L shl Math.min(Math.max(attempt, 0), 6))))
        }
        @JvmStatic fun message(code: String): String {
            when ((code)) {
                "FOLDER_NOT_SELECTED" -> {
                    return "Bitte zuerst den bestehenden Drive-Ordner auswählen. Lokale Daten bleiben erhalten."
                }
                "DEVICE_ID_MISSING" -> {
                    return "Die Gerätekennung fehlt. App erneut öffnen und den Abgleich erneut versuchen. Keine neue Änderungsdatei angelegt."
                }
                "NETWORK_UNAVAILABLE" -> {
                    return "Keine nutzbare Internetverbindung. Der Abgleich wird mit Abstand erneut versucht; lokale Daten bleiben erhalten."
                }
                "STATUS_NOT_SAVED" -> {
                    return "Daten gesichert, Status konnte nicht gespeichert werden. Der bestätigte Datenstand bleibt erhalten."
                }
                "NOT_CONNECTED" -> {
                    return "Drive verbinden oder den bestehenden Ordner auswählen. Lokale Daten bleiben erhalten."
                }
                "AUTH_REQUIRED", "PERMISSION_REQUIRED", "HTTP_401", "HTTP_403" -> {
                    return "Drive-Freigabe am Gerät prüfen und die Verbindung bestätigen. Lokale Daten bleiben erhalten."
                }
                "REMOTE_STATE_REFERENCE_MISSING" -> {
                    return "Der bestätigte Gesamtstand fehlt im verbundenen Ordner. Originale und lokale Änderungen bleiben erhalten; Drive-Dateien prüfen."
                }
                "REMOTE_MASTER_MISSING" -> {
                    return "Der Gesamtstand fehlt im verbundenen Ordner. Bestehenden Datenordner prüfen; kein leerer Ersatz wird angelegt."
                }
                "REMOTE_DUPLICATES_DIFFER" -> {
                    return "Gleichnamige Drive-Dateien enthalten verschiedene Stände. Beide Originale bleiben erhalten; Versionen prüfen."
                }
                "REMOTE_FILE_TOO_LARGE" -> {
                    return "Eine Drive-Datei überschreitet die sichere Lesegrenze. Original und lokale Daten bleiben erhalten; Datenarchiv prüfen."
                }
                "PROVIDER_READ_FAILED", "PROVIDER_QUERY_FAILED" -> {
                    return "Der Android-Speicheranbieter konnte die Drive-Datei nicht bereitstellen. Verbindung und Ordnerzugriff am Gerät prüfen, dann erneut abgleichen."
                }
                "INVALID_REMOTE_DATA" -> {
                    return "Eine Drive-Datei ist unvollständig oder ungültig. Originale bleiben erhalten; Datei und Sicherung prüfen."
                }
                "LEGACY_SOURCE_CHANGED", "PUBLICATION_CONFLICT", "PUBLICATION_INVALID", "ACTION_ID_CONFLICT" -> {
                    return "Drive enthält unterschiedliche Versionen. Beide Stände bleiben erhalten; Versionen prüfen."
                }
                "RETRY_LIMIT" -> {
                    return "Automatische Versuche pausiert. Verbindung prüfen und einmal erneut versuchen."
                }
                else -> {
                    return "Drive-Abgleich nicht abgeschlossen. Internet und Ordnerfreigabe prüfen; erneut versuchen. Lokale Daten bleiben erhalten."
                }
            }
        }
        @Throws(Exception::class)
        @JvmStatic fun describe(s: JSONObject, queue: JSONObject, running: Boolean, now: Long): JSONObject {
            var out: JSONObject = JSONObject(s.toString())
            var pending: Int = queue.optInt("pending_upload")
            var review: Int = queue.optInt("review")
            out.put("pending", pending).put("review", review).put("pending_total", queue.optInt("total", (pending + review))).put("syncing", running)
            var code: String = s.optString("last_error_code")
            var state: String
            var title: String
            var action: String = "none"
            var detail: String
            if ((!s.optBoolean("configured"))) {
                state = "not_connected"
                title = "Drive verbinden"
                detail = message((if (code.equals("FOLDER_NOT_SELECTED")) code else "NOT_CONNECTED"))
                action = "connect_drive"
            } else {
                if ((running)) {
                    state = "syncing"
                    title = "Abgleich läuft"
                    detail = "Änderungen werden geprüft und übertragen."
                } else {
                    if ((s.optBoolean("drive_sync_failed"))) {
                        var paused: Boolean = (s.optBoolean("user_action_required") || ((s.optInt("retry_count") >= MAX_ATTEMPTS) && !code.equals("NETWORK_UNAVAILABLE")))
                        var scheduled: Boolean = (s.optBoolean("automatic_enabled", true) || s.optBoolean("manual_requested"))
                        state = (if (paused) "action_required" else (if ((scheduled && (s.optLong("next_retry") > now))) "retry_wait" else "error"))
                        title = (if (paused) "Abgleich braucht deine Hilfe" else (if (state.equals("retry_wait")) "Erneuter Versuch folgt" else "Abgleich erneut versuchen"))
                        detail = message((if (code.isEmpty()) "TRANSFER_FAILED" else code))
                        action = (if (java.util.Arrays.asList("REMOTE_DUPLICATES_DIFFER", "LEGACY_SOURCE_CHANGED", "PUBLICATION_CONFLICT", "PUBLICATION_INVALID", "ACTION_ID_CONFLICT").contains(code)) "compare_versions" else (if (java.util.Arrays.asList("AUTH_REQUIRED", "PERMISSION_REQUIRED", "HTTP_401", "HTTP_403", "FOLDER_NOT_SELECTED").contains(code)) "connect_drive" else "retry_sync"))
                    } else {
                        if ((code.equals("STATUS_NOT_SAVED"))) {
                            state = "status_warning"
                            title = "Status konnte nicht gespeichert werden"
                            detail = message(code)
                            action = "retry_sync"
                        } else {
                            if (((pending > 0))) {
                                state = "pending"
                                title = ("" + pending + " Änderung(en) warten auf Drive")
                                var automatic: Boolean = s.optBoolean("automatic_enabled", true)
                                var manual: Boolean = s.optBoolean("manual_requested")
                                detail = (if (automatic) "Auf diesem Gerät gespeichert. Der Abgleich erfolgt automatisch bei verfügbarer Verbindung." else (if (manual) "Auf diesem Gerät gespeichert. Der angeforderte Abgleich wartet auf eine Verbindung." else "Auf diesem Gerät gespeichert. Automatischer Abgleich ist ausgeschaltet."))
                                action = (if ((automatic || manual)) "none" else "retry_sync")
                            } else {
                                if (((review > 0))) {
                                    state = "review_required"
                                    title = ("" + review + " Änderung(en) prüfen")
                                    detail = "Die Übertragung ist getrennt von diesen Prüfungen. Unabhängige Änderungen werden weiter abgeglichen."
                                    action = "review_changes"
                                } else {
                                    if (((s.optInt("trash_cleanup_pending") > 0))) {
                                        state = "cleanup_pending"
                                        title = "Dateibereinigung ausstehend"
                                        detail = "Papierkorbeinträge entfernt. Nicht mehr verwendete Dateien werden nach bestätigtem Abgleich bereinigt; andere Arbeiten bleiben möglich."
                                        action = "retry_sync"
                                    } else {
                                        if (((s.optBoolean("cloud_confirmed") && "drive_server_manifest".equals(s.optString("confirmation_scope"))))) {
                                            state = "synced"
                                            title = "Vollständig mit Drive abgeglichen"
                                            detail = "Änderungen und Originale mit Drive-Serverbelegen bestätigt."
                                        } else {
                                            if ((!s.optString("last_sync").isEmpty())) {
                                                state = "provider_readback"
                                                title = "An Drive übergeben"
                                                detail = "Vom Speicheranbieter zurückgelesen. Ein vollständiger Cloud-Abschluss ist damit nicht bestätigt."
                                            } else {
                                                state = "ready"
                                                title = "Erster Abgleich ausstehend"
                                                detail = "Die lokale Erfassung funktioniert auch ohne Verbindung."
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return out.put("state", state).put("title", title).put("detail", detail).put("next_action", action).put("cloud_confirmed", state.equals("synced"))
        }
    }
}
