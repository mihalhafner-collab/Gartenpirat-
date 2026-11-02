package at.hausgarten.service

import org.json.*
import java.util.*

/** Dispatch only; each domain service is independently testable without Android. */
class OwnerApplicationService {
    private val commands: MutableMap<String, OwnerCommand> = LinkedHashMap()
    constructor(repository: OwnerRepository) {
        register(OfferApplicationService(repository), OfferTools.WRITES)
        register(WorkApplicationService(repository), arrayOf("save_reviewed_order", "set_work_record_billed", "reassign_work_record", "set_personal_paid"))
        register(PhotoApplicationService(repository), arrayOf("begin_file_upload", "write_file_chunk", "create_photo", "update_photo"))
        register(SyncArchiveApplicationService(repository), arrayOf("empty_trash", "archive_work_records", "close_legacy_conflicts", "request_sync", "purge_entity", "delete_entity", "archive_entity", "restore_entity", "resolve_sync_conflict", "create_backup", "restore_backup"))
        register(SettingsApplicationService(repository), arrayOf("update_business_settings", "update_settings"))
        register(PlanningApplicationService(repository), AppointmentTools.WRITES)
        register(LocalWorkflowApplicationService(repository), LocalWorkflowTools.WRITES)
    }
    private fun register(command: OwnerCommand, names: Array<String>) {
        for (name in names) {
            if (((commands.put(name, command) != null))) {
                throw IllegalStateException("Duplicate owner operation")
            }
        }
    }
    fun handles(name: String): Boolean {
        return commands.containsKey(name)
    }
    @Throws(Exception::class)
    fun perform(name: String, args: JSONObject, request: String): JSONObject {
        var command: OwnerCommand? = commands.get(name)
        if (((command == null))) {
            throw IllegalArgumentException("Eigentümerfunktion fehlt.")
        }
        return command!!.perform(name, args, request)
    }
}
