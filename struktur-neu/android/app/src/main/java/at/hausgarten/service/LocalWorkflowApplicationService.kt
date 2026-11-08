package at.hausgarten.service

import org.json.*

/** Thin shared command adapter; native UI and MCP use the same repository use cases. */
class LocalWorkflowApplicationService : OwnerCommand {
    private val repository: OwnerRepository

    constructor(repository: OwnerRepository) {
        this.repository = repository
    }

    @Throws(Exception::class)
    override fun perform(name: String, args: JSONObject, request: String): JSONObject {
        if (LocalWorkflowTools.backup(name)) {
            return repository.fullBackupAction(name, args)
        }
        if (LocalWorkflowTools.revier(name)) {
            return repository.revierAction(name, args)
        }
        if (LocalWorkflowTools.offerDecision(name)) {
            return OfferApplicationService(repository).perform(name, args, request)
        }
        throw IllegalArgumentException("Lokaler Anwendungsfall fehlt.")
    }
}
