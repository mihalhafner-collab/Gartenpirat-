package at.hausgarten.service

import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Read-only evidence shared by the backup screen, MCP and the self-test. */
object BackupStatusRules {
    private val UNFINISHED =
        setOf(
            "CAPTURE_PENDING",
            "CAPTURING",
            "CAPTURED",
            "SEALING",
            "SEALED_LOCAL",
            "LOCAL_VERIFIED",
            "PROVIDER_WRITING",
            "PROVIDER_VERIFY_PENDING",
            "RETRY_PENDING",
        )

    private fun instant(value: String?): Instant =
        try {
            Instant.parse(value)
        } catch (invalid: Exception) {
            Instant.MIN
        }

    private fun clockInvalid(value: String?, now: Instant): Boolean {
        val time = instant(value)
        return time == Instant.MIN || time.isAfter(now.plusSeconds(120))
    }

    private fun activity(job: JSONObject): Instant {
        var newest = Instant.MIN
        for (field in
            arrayOf("created_at", "last_attempt_at", "last_failure_at", "provider_verified_at")) {
            val time = instant(job.optString(field))
            if (time.isAfter(newest)) newest = time
        }
        return newest
    }

    @JvmStatic
    @Throws(Exception::class)
    fun project(
        records: JSONArray,
        args: JSONObject,
        running: Set<String>,
        now: Instant,
    ): JSONObject {
        val ordered = ArrayList<JSONObject>()
        var verified = ""
        var captured = ""
        var clockReview = false
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val job = record.getJSONObject("job")
            val destination = job.optJSONObject("destination")
            if (
                !record.optBoolean("sameInstallation") ||
                    !record.optBoolean("sameRestoreEpoch") ||
                    destination == null ||
                    (destination.optString("purpose") != "pixel_plain_backup" &&
                        !args.optBoolean("legacy_evidence"))
            )
                continue
            val id = job.getString("backupId")
            if (args.has("backup_id") && args.getString("backup_id") != id) continue
            val archive = job.optJSONObject("archive")
            val proof = job.optJSONObject("provider")
            val recorded = job.optString("phase")
            var phase = recorded
            var error = job.optString("lastErrorCode")
            val active = running.contains(id)
            val plain = destination.optString("purpose") == "pixel_plain_backup"
            val proven =
                plain &&
                    archive != null &&
                    proof != null &&
                    archive.optString("archiveFormat") == "PLAIN_ZIP" &&
                    archive.optString("sha256").matches(Regex("[0-9a-f]{64}")) &&
                    archive.optString("sha256") == proof.optString("archive_sha256") &&
                    archive.optLong("size") > 0 &&
                    archive.optLong("size") == proof.optLong("byte_size") &&
                    id == proof.optString("backup_id") &&
                    proof.optString("verification_scope") == "DOCUMENT_PROVIDER_READBACK"
            val checked = proof?.optString("verified_at") ?: ""
            val capture = archive?.optString("capturedAt") ?: ""
            val badClock =
                proven &&
                    (clockInvalid(checked, now) ||
                        clockInvalid(capture, now) ||
                        clockInvalid(archive.optString("localVerifiedAt"), now) ||
                        instant(capture).isAfter(instant(checked)) ||
                        instant(archive.optString("localVerifiedAt")).isAfter(instant(checked)))
            clockReview = clockReview || badClock
            if (proven && !badClock && instant(checked).isAfter(instant(verified))) {
                verified = checked
                captured = capture
            }
            if (error.isNotEmpty()) {
                phase = "FAILED"
            } else if (plain && !active && recorded in UNFINISHED) {
                phase = "NEEDS_USER_ACTION"
                error = "BACKUP_INTERRUPTED"
            } else if (plain && recorded == "PROVIDER_VERIFIED" && (!proven || badClock)) {
                phase = "NEEDS_USER_ACTION"
                error =
                    if (badClock) "BACKUP_CLOCK_REVIEW_REQUIRED"
                    else "BACKUP_PROVIDER_PROOF_INVALID"
            }
            ordered.add(
                JSONObject()
                    .put("backup_id", id)
                    .put("phase", phase)
                    .put("recorded_phase", recorded)
                    .put("running", active)
                    .put("created_at", job.optString("createdAt"))
                    .put("last_attempt_at", job.optString("lastLocalAttemptAt"))
                    .put(
                        "last_failure_at",
                        if (job.optString("lastErrorCode").isEmpty()) ""
                        else job.optString("lastAttemptAt"),
                    )
                    .put(
                        "last_error_code",
                        if (error.isEmpty()) ""
                        else BackupScheduleRules.errorCode(IOException(error)),
                    )
                    .put("record_hash", job.getString("revision"))
                    .put("current_context", true)
                    .put("context_matches", true)
                    .put("captured_at", capture)
                    .put("media_coverage", archive?.optString("mediaCoverage") ?: "")
                    .put(
                        "external_storage_verified",
                        proof != null && proof.optBoolean("external_storage_verified"),
                    )
                    .put("provider_verified_at", checked)
                    .put(
                        "remote_verified_at",
                        if (job.optJSONObject("upload") == null) ""
                        else job.getJSONObject("upload").optString("remoteVerifiedAt"),
                    )
                    .put("local_verified_at", archive?.optString("localVerifiedAt") ?: "")
                    .put("byte_size", archive?.optLong("size") ?: 0L)
                    .put(
                        "warning_count",
                        maxOf(
                            job.optInt("warning_count"),
                            job.optJSONArray("warnings")?.length() ?: 0,
                        ),
                    )
                    .put("warnings", job.optJSONArray("warnings") ?: JSONArray())
                    .put("counts", job.optJSONObject("counts") ?: JSONObject())
                    .put(
                        "encrypted",
                        archive != null && archive.optString("archiveFormat") != "PLAIN_ZIP",
                    )
                    .put("saved_proof_valid", proven && !badClock)
                    .put("cloud_confirmed", false)
            )
        }
        ordered.sortWith(
            Comparator { a, b ->
                val time = activity(b).compareTo(activity(a))
                if (time != 0) time
                else a.optString("backup_id").compareTo(b.optString("backup_id"))
            }
        )
        val offset = maxOf(0, args.optInt("offset"))
        val limit = minOf(100, maxOf(1, args.optInt("limit", 20)))
        val page = JSONArray()
        var i = offset
        while (i < ordered.size && page.length() < limit) page.put(ordered[i++])
        val latest = if (ordered.isEmpty()) JSONObject() else ordered[0]
        val state =
            when {
                latest.optBoolean("running") -> "RUNNING"
                latest.optString("phase") in
                    listOf("FAILED", "NEEDS_USER_ACTION", "FORMAT_REVIEW_REQUIRED") -> "FAILED"
                latest.optString("phase") == "PROVIDER_VERIFIED" &&
                    latest.optBoolean("saved_proof_valid") -> "VERIFIED"
                else -> "NOT_SAVED"
            }
        return JSONObject()
            .put("state", state)
            .put("latest_job", latest)
            .put("jobs", page)
            .put("total", ordered.size)
            .put("offset", offset)
            .put("limit", limit)
            .put("last_local_verified_at", verified)
            .put("last_local_captured_at", captured)
            .put("clock_review_required", clockReview)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun selfTest(status: JSONObject): JSONObject {
        val state = status.optString("state")
        return JSONObject()
            .put("name", "local_backup")
            .put(
                "status",
                if (state == "FAILED" || status.optBoolean("clock_review_required")) "FAIL"
                else if (state == "VERIFIED") "PASS" else "NOT TESTABLE",
            )
            .put("scope", "recorded_pixel_file_readback")
            .put("state", state)
            .put(
                "last_error_code",
                if (status.optString("current_access_error").isNotEmpty())
                    status.getString("current_access_error")
                else if (status.optJSONObject("latest_job") == null) ""
                else status.getJSONObject("latest_job").optString("last_error_code"),
            )
            .put("required_for_business", false)
            .put("file_rechecked_now", false)
            .put("restore_tested", false)
            .put("cloud_confirmed", false)
    }

    @JvmStatic
    fun failureMessage(code: String): String {
        if (code == "BACKUP_FORMAT_UNSUPPORTED")
            return "Dieses Sicherungsformat wird nicht unterstützt. Bitte eine unverschlüsselte lokale ZIP-Sicherung auswählen. Alte verschlüsselte Sicherungen können nicht mehr geöffnet werden."
        if (code == "BACKUP_DOCUMENT_REQUIRED" || code == "BACKUP_PIXEL_FOLDER_REQUIRED")
            return "Bitte über die Android-Dateiauswahl eine lokale Sicherungsdatei auf dem Pixel wählen."
        if (code == "BACKUP_INTERRUPTED")
            return "Die letzte Sicherung wurde unterbrochen. Bitte die Sicherung erneut öffnen und speichern."
        if (code == "BACKUP_RUNNER_BUSY")
            return "Eine Sicherung läuft bereits. Bitte auf deren Ergebnis warten."
        if (code == "BACKUP_TARGET_ALREADY_VERIFIED")
            return "Die Sicherungsdatei ist bereits geprüft."
        if (code == "BACKUP_SAFE_REPLACE_UNAVAILABLE")
            return "Dieser Speicherort erlaubt keinen nachweisbar sicheren Austausch. Die vorhandene Datei bleibt unberührt; bitte einen geeigneten lokalen Speicherort wählen."
        if (code == "BACKUP_ALL_FILES_ACCESS_REQUIRED")
            return "Für den sicheren Austausch derselben lokalen ZIP verlangt Android Zugriff auf alle Dateien. Bitte den Dateizugriff bewusst in den Android-Einstellungen freigeben. Die vorhandene ZIP wurde nicht überschrieben."
        if (code == "BACKUP_DOCUMENT_PERMISSION_REQUIRED")
            return "Für diese Sicherungsdatei fehlt die dauerhafte Android-Lese- und Schreibfreigabe. Bitte die Zieldatei über die Android-Dateiauswahl wählen. Die Datei wurde nicht überschrieben."
        if (code == "BACKUP_TARGET_CHANGED")
            return "Die Sicherungsdatei wurde außerhalb der App verändert. Sie wurde nicht überschrieben; bitte die Datei prüfen oder einen anderen Speicherort wählen."
        if (code == "BACKUP_REQUEST_ID_CONFLICT")
            return "Dieser Sicherungsvorgang gehört zu einer anderen Datei. Bitte die Sicherungsansicht neu öffnen."
        if (code == "BACKUP_LOCAL_WRITE_FAILED")
            return "Die ZIP-Datei konnte nicht vollständig gespeichert werden. Bitte freien Speicher und Schreibzugriff prüfen und erneut speichern."
        if (code == "BACKUP_LOCAL_READBACK_FAILED")
            return "Die gespeicherte Datei konnte nicht vollständig zurückgelesen werden. Bitte freien Speicher und Dateizugriff prüfen und erneut speichern."
        if (code.contains("SPACE_REQUIRED") || code == "BACKUP_WORK_STORAGE_UNAVAILABLE")
            return "Zu wenig freier Speicher für Sicherung und Prüfung. Bitte Speicher freigeben und erneut speichern."
        if (code == "BACKUP_PREFERENCES_CHANGED_RETRY" || code == "BACKUP_SOURCE_CHANGED")
            return "Der Bestand hat sich während der Aufnahme verändert. Bitte den laufenden Vorgang abschließen und erneut sichern."
        if (code == "BACKUP_DATABASE_INTEGRITY_FAILED")
            return "Die Datenbank ist technisch nicht lesbar. Der Bestand bleibt erhalten; die Datenbank muss geprüft werden."
        if (
            code.startsWith("BACKUP_FILE_") ||
                code.startsWith("BACKUP_MANIFEST_") ||
                code == "BACKUP_COMMIT_MISSING"
        )
            return "Die Sicherungsdatei ist unvollständig oder beschädigt. Bitte eine andere Sicherungsdatei auswählen."
        return "Vorgang nicht abgeschlossen. Der vorhandene Bestand bleibt erhalten. Fehlercode: $code"
    }

    private fun displayTime(value: String): String =
        try {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.GERMAN)
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(value))
        } catch (unknown: Exception) {
            "Zeitpunkt unbekannt"
        }

    @JvmStatic
    fun message(status: JSONObject): String {
        val latest = status.optJSONObject("latest_job")
        val state = status.optString("state")
        if (state == "RUNNING") return "Eine Sicherung läuft. Bitte auf das Ergebnis warten."
        val earlier = status.optString("last_local_verified_at")
        if (status.optString("current_access_error").isNotEmpty())
            return failureMessage(status.getString("current_access_error")) +
                if (earlier.isEmpty()) ""
                else " Früherer bestätigter Sicherungszeitpunkt: ${displayTime(earlier)}."
        if (state == "FAILED")
            return failureMessage(
                latest?.optString("last_error_code", "BACKUP_OPERATION_FAILED")
                    ?: "BACKUP_OPERATION_FAILED"
            ) +
                if (earlier.isEmpty()) ""
                else " Früherer bestätigter Sicherungszeitpunkt: ${displayTime(earlier)}."
        if (status.optBoolean("clock_review_required"))
            return "Sicherungszeit nicht bestätigt. Bitte Gerätezeit und Sicherungsdatei prüfen."
        if (state == "VERIFIED")
            return (if (latest != null && latest.optInt("warning_count") > 0)
                "Gespeichert mit Einschränkungen · ${BackupInventory.warningSummary(latest.optJSONArray("warnings"), latest.optInt("warning_count"))}. "
            else "") +
                "Sicherung gespeichert und vollständig zurückgelesen: ${displayTime(earlier)}. Enthaltener Datenstand: ${displayTime(status.optString("last_local_captured_at"))}. Neuere Arbeit ist damit nicht automatisch gesichert." +
                if (latest != null && latest.optInt("warning_count") > 0)
                    " Hinweise zum Ausgangsbestand: ${latest.optInt("warning_count")}. Details: data/backup-notes.json in der ZIP-Datei."
                else ""
        return "Noch keine vollständig gespeicherte und zurückgelesene Sicherung bestätigt. Eine Kopie auf demselben Pixel schützt nicht bei Verlust oder Defekt des Geräts."
    }
}
