package at.hausgarten.service

import java.util.*
import org.json.JSONObject

internal class McpErrors {
    constructor() {}

    companion object {
        private val LOCAL_CODES: MutableSet<String> =
            HashSet(
                Arrays.asList(
                    "REVIER_SCHEMA_UPGRADE_REQUIRED",
                    "REVIER_RECORD_INVALID",
                    "LOCAL_STATE_BUILD_FAILED",
                    "LOCAL_BOOTSTRAP_INVALID",
                    "LOCAL_DATABASE_CONTRACT_REVIEW_REQUIRED",
                    "LOCAL_ORIGINAL_MISSING",
                    "LOCAL_STATE_CONTRACT_INVALID",
                    "LOCAL_STATE_METADATA_UNREADABLE",
                    "LOCAL_STORE_PATH_INVALID",
                    "RESTORE_DATABASE_STILL_OPEN",
                    "BACKUP_SAF_OWNED_TEMP_CLEANUP_REQUIRED",
                    "BACKUP_CAPTURE_METADATA_MISSING",
                    "BACKUP_RUNNER_BUSY",
                    "BACKUP_ALREADY_COMPLETE",
                    "BACKUP_AUTOMATIC_PAUSED",
                    "BACKUP_BUSINESS_ID_REQUIRED",
                    "BACKUP_BUSINESS_INVENTORY_MISMATCH",
                    "BACKUP_CAPTURE_ARCHIVE_MISMATCH",
                    "BACKUP_CAPTURE_IDENTITY_MISMATCH",
                    "BACKUP_CAPTURE_METADATA_CHANGED",
                    "BACKUP_CAPTURE_RECURSIVE_TARGET_REJECTED",
                    "BACKUP_CAPTURE_TARGET_EXISTS_OR_UNAVAILABLE",
                    "BACKUP_COMMIT_MISSING",
                    "BACKUP_CONTROL_REPLAY_MISMATCH",
                    "BACKUP_DATABASE_MANIFEST_MISMATCH",
                    "BACKUP_DATABASE_VERSION_UNSUPPORTED",
                    "BACKUP_DESTINATION_CHANGED_REVIEW_REQUIRED",
                    "BACKUP_DESTINATION_UNSUPPORTED",
                    "BACKUP_DEVICE_EPOCH_CONFLICT",
                    "BACKUP_DEVICE_GUARD_INVALID",
                    "BACKUP_DEVICE_GUARD_UNAVAILABLE",
                    "BACKUP_DUPLICATE_FILE",
                    "BACKUP_ENVELOPE_INVALID",
                    "BACKUP_ENVELOPE_TOO_LARGE",
                    "BACKUP_FILE_HASH_MISMATCH",
                    "BACKUP_FILE_SET_INVALID",
                    "BACKUP_FORMAT_UNSUPPORTED",
                    "BACKUP_INSTALLATION_AND_EPOCH_REQUIRED",
                    "BACKUP_INTENT_INVALID",
                    "BACKUP_INTERRUPTED",
                    "BACKUP_JOB_ATTEMPTS_INVALID",
                    "BACKUP_JOB_ATTEMPT_ID_INVALID",
                    "BACKUP_JOB_FIELD_IMMUTABLE",
                    "BACKUP_JOB_FORMAT_UNSUPPORTED",
                    "BACKUP_JOB_ID_CONFLICT",
                    "BACKUP_JOB_ID_REQUIRED",
                    "BACKUP_JOB_MANUAL_INTENT_INVALID",
                    "BACKUP_JOB_NOT_FOUND",
                    "BACKUP_JOB_PHASE_UNSUPPORTED",
                    "BACKUP_JOB_REPLAY_MISMATCH",
                    "BACKUP_JOB_RESTORE_CONTEXT_REVIEW_REQUIRED",
                    "BACKUP_JOB_RETRY_TIME_INVALID",
                    "BACKUP_JOB_REVISION_REQUIRED",
                    "BACKUP_JOB_SUPERSEDED",
                    "BACKUP_JOB_WRITE_CONFLICT",
                    "BACKUP_LOCAL_ORIGINALS_INCOMPLETE",
                    "BACKUP_LOCAL_VERIFICATION_REQUIRED",
                    "BACKUP_MANIFEST_INVALID",
                    "BACKUP_MANIFEST_TOO_LARGE",
                    "BACKUP_METADATA_TOO_LARGE",
                    "BACKUP_PAGE_INVALID",
                    "BACKUP_PATH_INVALID",
                    "BACKUP_PREFERENCES_CHANGED_RETRY",
                    "BACKUP_PREFERENCES_VERSION_UNSUPPORTED",
                    "BACKUP_PREFERENCE_TYPES_MISSING",
                    "BACKUP_PREFERENCE_TYPE_INVALID",
                    "BACKUP_PREFERENCE_TYPE_UNSUPPORTED",
                    "BACKUP_PREPARE_REQUIRES_OUTSIDE_TRANSACTION",
                    "BACKUP_PROVIDER_DESTINATION_REQUIRED",
                    "BACKUP_REQUEST_ID_REQUIRED",
                    "BACKUP_RESTORE_CONTEXT_CHANGED",
                    "BACKUP_RESTORE_REPLAY_MISMATCH",
                    "BACKUP_RESTORE_REQUEST_ID_REQUIRED",
                    "BACKUP_REVISION_REQUIRED",
                    "BACKUP_SAF_COMMIT_CONTENT_MISMATCH",
                    "BACKUP_SAF_COMMIT_FORMAT_UNSUPPORTED",
                    "BACKUP_SAF_COMMIT_READBACK_MISMATCH",
                    "BACKUP_SAF_COMMIT_SOURCE_CHANGED",
                    "BACKUP_SAF_COMMIT_TOO_LARGE",
                    "BACKUP_SAF_CREATE_RESPONSE_UNCERTAIN",
                    "BACKUP_SAF_DUPLICATE_CANDIDATE",
                    "BACKUP_SAF_GENERATION_MISMATCH",
                    "BACKUP_SAF_INVENTORY_UNAVAILABLE",
                    "BACKUP_SAF_NAME_COLLISION",
                    "BACKUP_SAF_OPERATION_NOT_FOUND",
                    "BACKUP_SAF_PERMISSION_REQUIRED",
                    "BACKUP_SAF_READBACK_HASH_MISMATCH",
                    "BACKUP_SAF_READBACK_SIZE_MISMATCH",
                    "BACKUP_SAF_READ_UNAVAILABLE",
                    "BACKUP_SAF_REPLAY_MISMATCH",
                    "BACKUP_SAF_REQUEST_ID_REQUIRED",
                    "BACKUP_SAF_REQUIRES_OUTSIDE_TRANSACTION",
                    "BACKUP_SAF_RESTORE_CONTEXT_CHANGED",
                    "BACKUP_SAF_RUNNER_BUSY",
                    "BACKUP_SAF_SOURCE_CHANGED",
                    "BACKUP_SAF_STAGING_INVALID",
                    "BACKUP_SAF_STAGING_UNAVAILABLE",
                    "BACKUP_SAF_TREE_REQUIRED",
                    "BACKUP_SAF_WRITE_CONFLICT",
                    "BACKUP_SAF_WRITE_UNAVAILABLE",
                    "BACKUP_SCHEDULER_UNAVAILABLE",
                    "BACKUP_SEALED_ARCHIVE_CHANGED",
                    "BACKUP_SEALED_ARCHIVE_MISSING",
                    "BACKUP_SETTINGS_CHANGED",
                    "BACKUP_SETTINGS_REPLAY_MISMATCH",
                    "BACKUP_SETTINGS_REVISION_REQUIRED",
                    "BACKUP_SETTINGS_VERSION_UNSUPPORTED",
                    "BACKUP_SETTINGS_WRITE_CONFLICT",
                    "BACKUP_SETUP_REVIEW_REQUIRED",
                    "BACKUP_SNAPSHOT_ENGINE_UNSUPPORTED",
                    "BACKUP_SOURCE_CHANGED",
                    "BACKUP_SOURCE_LINK_OR_DIRECTORY_INVALID",
                    "BACKUP_SOURCE_LINK_REJECTED",
                    "BACKUP_SOURCE_UNAVAILABLE",
                    "BACKUP_SOURCE_UNREADABLE",
                    "BACKUP_STAGING_LINK_REJECTED",
                    "BACKUP_STAGING_UNAVAILABLE",
                    "BACKUP_STAGING_UNREADABLE",
                    "BACKUP_STORAGE_RESERVE_REQUIRED",
                    "BACKUP_VERSION_UNSUPPORTED",
                    "BACKUP_WORK_LINK_REJECTED",
                    "BACKUP_WORK_OWNERSHIP_MISMATCH",
                    "BACKUP_WORK_OWNERSHIP_UNCONFIRMED",
                    "BACKUP_WORK_PATH_REJECTED",
                    "BACKUP_WORK_STORAGE_UNAVAILABLE",
                    "INVALID_DECIMAL_POSITION",
                    "LOCAL_DATABASE_CONTRACT_UNSUPPORTED",
                    "MONEY_REQUIRED",
                    "REPLAY_MISMATCH",
                    "RESTORE_ACTIVATION_REPLAY_MISMATCH",
                    "RESTORE_ACTIVE_PATH_LINK_REJECTED",
                    "RESTORE_ACTIVE_PATH_OVERLAP",
                    "RESTORE_CANCELLATION_REPLAY_MISMATCH",
                    "RESTORE_CANCELLATION_REQUIRES_RECOVERY",
                    "RESTORE_CONFIRMATION_REQUIRED",
                    "RESTORE_CURRENT_BACKUP_PROOF_REQUIRED",
                    "RESTORE_DOCUMENT_REQUIRED",
                    "RESTORE_EXCHANGE_NEW_STATE_AMBIGUOUS",
                    "RESTORE_EXCHANGE_NEW_STATE_CHANGED",
                    "RESTORE_EXCHANGE_OLD_STATE_AMBIGUOUS",
                    "RESTORE_EXCHANGE_OLD_STATE_CHANGED",
                    "RESTORE_EXCHANGE_PATH_CONFLICT",
                    "RESTORE_EXCHANGE_STORAGE_UNAVAILABLE",
                    "RESTORE_EXCHANGE_TARGET_INVALID",
                    "RESTORE_EXCHANGE_TARGET_OVERLAP",
                    "RESTORE_HASH_REQUIRED",
                    "RESTORE_ID_INVALID",
                    "RESTORE_METADATA_TOO_LARGE",
                    "RESTORE_OTHER_ACTIVATION_PENDING",
                    "RESTORE_PATH_INVALID",
                    "RESTORE_PREFERENCE_PATH_REQUIRED",
                    "RESTORE_PREFERENCE_TYPE_UNSUPPORTED",
                    "RESTORE_PREPARED_STORAGE_UNAVAILABLE",
                    "RESTORE_PREVIEW_CHANGED",
                    "RESTORE_PREVIEW_NOT_REUSABLE",
                    "RESTORE_PROTECTION_REQUIRED",
                    "RESTORE_REPLAY_MISMATCH",
                    "RESTORE_REQUEST_ID_REQUIRED",
                    "RESTORE_RESTART_REQUIRED",
                    "RESTORE_ROLLBACK_NEW_STATE_AMBIGUOUS",
                    "RESTORE_ROLLBACK_NEW_STATE_REMAINS",
                    "RESTORE_ROLLBACK_OLD_STATE_AMBIGUOUS",
                    "RESTORE_SOURCE_UNAVAILABLE",
                    "RESTORE_SPACE_REQUIRED",
                    "RESTORE_SPACE_REQUIRED_OR_SOURCE_CHANGED",
                    "RESTORE_STAGED_FILE_CHANGED",
                    "RESTORE_STAGING_CLEANUP_REQUIRED",
                    "RESTORE_STAGING_LINK_REJECTED",
                    "RESTORE_STAGING_UNAVAILABLE",
                    "RESTORE_STARTUP_REENTRY",
                    "RESTORE_TARGET_CONTEXT_CHANGED",
                    "RESTORE_UNEXPECTED_STAGED_FILE",
                    "WRITE_CONFLICT",
                )
            )

        private @JvmStatic fun localCode(error: Exception): String? {
            var value = error.message
            return (if ((value != null) && LOCAL_CODES.contains(value)) value else null)
        }

        private @JvmStatic fun localMessage(code: String): String {
            if (code == "LOCAL_STATE_BUILD_FAILED") {
                return "Der lokale Datenstand konnte nicht vollständig aufgebaut werden. Lokalen Stand und geprüfte Sicherung am Gerät prüfen; keine automatische Rückspielung starten."
            }
            if (code == "LOCAL_BOOTSTRAP_INVALID") {
                return "Der leere lokale App-Ausgangsstand konnte nicht geprüft werden. App-Version und Sicherungsstand prüfen; keine automatische Übernahme externer Daten und keine Änderung ausgeführt."
            }
            if (code == "LOCAL_ORIGINAL_MISSING") {
                return "Ein benötigtes Original fehlt oder konnte nicht vollständig geprüft werden. Geprüfte Sicherung am Gerät prüfen; Originale werden nicht automatisch ersetzt."
            }
            if (
                ((code.contains("WRITE_CONFLICT") || code.contains("EPOCH_CONFLICT")) ||
                    code.endsWith("CHANGED"))
            ) {
                return "Der gespeicherte Stand hat sich geändert. Neu laden; Eingaben bleiben erhalten."
            }
            if (code.contains("REPLAY_MISMATCH")) {
                return "Die Anfragekennung gehört zu anderen Angaben. Die ursprüngliche Aktion prüfen."
            }
            if (code.contains("DECIMAL") || code.startsWith("MONEY_")) {
                return "Beträge und Mengen mit exakten Dezimalwerten beziehungsweise kleinsten Währungseinheiten prüfen."
            }
            if (code == "BACKUP_FORMAT_UNSUPPORTED") return BackupStatusRules.failureMessage(code)
            if (code.contains("STORAGE") || code.contains("SPACE")) {
                return "Freien Speicher und gewähltes Sicherungsziel prüfen. Die letzte geprüfte Sicherung bleibt erhalten."
            }
            if (
                ((code.contains("RESTORE") || code.contains("VERSION")) || code.contains("SCHEMA"))
            ) {
                return "Version und Wiederherstellungsstand müssen geprüft werden. Alte Schreibvorgänge bleiben gesperrt."
            }
            return "Der lokale Vorgang benötigt Prüfung. Gespeicherten Stand und Geräteeinrichtung prüfen."
        }

        private @JvmStatic fun code(error: Exception, fallback: String): String {
            val local = localCode(error)
            return (if (error is PixelMcpWriteTools.Rejected)
                (error as PixelMcpWriteTools.Rejected).code
            else (if (local != null) local else fallback))
        }

        @JvmStatic
        @Throws(Exception::class)
        fun rpc(error: Exception): JSONObject {
            val local = localCode(error)
            var problem =
                JSONObject()
                    .put(
                        "code",
                        code(
                            error,
                            (if (error is HttpTransport.Failure)
                                (error as HttpTransport.Failure).code
                            else
                                (if (error is IllegalArgumentException) "INVALID_INPUT"
                                else "READ_ERROR")),
                        ),
                    )
                    .put(
                        "message",
                        (if (local != null) localMessage(local)
                        else
                            (if (
                                (error is IllegalArgumentException ||
                                    error is HttpTransport.Failure)
                            )
                                error.message
                            else
                                "Anfrage nicht möglich. Pflichtangaben, Datensatz-ID oder gespeicherten lokalen Stand prüfen.")),
                    )
            if (error is HttpTransport.Failure) {
                val failure = (error as HttpTransport.Failure)
                problem
                    .put("retry_after_ms", Math.max(0, Math.min(3600000, failure.retryAfter)))
                    .put("user_action_required", failure.userAction)
            }
            if (local != null) {
                problem.put("user_action_required", true)
            }
            rejection(problem, error)
            return envelope(problem)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun bridge(error: Exception): JSONObject {
            val local = localCode(error)
            var problem =
                JSONObject()
                    .put("code", code(error, "VALIDATION_ERROR"))
                    .put(
                        "message",
                        (if (local != null) localMessage(local)
                        else
                            (if (error is IllegalArgumentException) error.message
                            else
                                "Vorgang nicht abgeschlossen. Sicherung, Versionen und Berechtigungen prüfen.")),
                    )
            rejection(problem, error)
            return envelope(problem)
        }

        private @JvmStatic @Throws(Exception::class) fun rejection(
            problem: JSONObject,
            error: Exception,
        ): Unit {
            val code = problem.optString("code")
            if (error is RevierApplicationService.CanonicalAppointmentRequired) {
                problem
                    .put("visit_id", error.visitId)
                    .put("appointment_id", error.appointmentId)
                    .put("record_hash", error.currentRecordHash)
                    .put("next_tool", "update_appointment")
            }
            if (
                (error is PixelMcpWriteTools.Rejected &&
                    !(Arrays.asList("REPLAY_MISMATCH", "REQUEST_EXPIRED").contains(code)))
            ) {
                problem.put("commit_state", "not_committed")
            }
        }

        private @JvmStatic @Throws(Exception::class) fun envelope(problem: JSONObject): JSONObject {
            if (problem.optString("code") == "RESTORE_FULL_REPLACEMENT_REQUIRED") {
                problem
                    .put("device_step", "open_full_backup_restore")
                    .put("required_restore_contract", "pixel_full_replacement_v2")
            }
            return JSONObject().put("ok", false).put("error", problem)
        }
    }
}
