package at.hausgarten.service

import android.content.Context
import org.json.*

internal class PixelOwnerAccess : PixelMcpOwnerTools.Access {
    private val context: Context
    private val store: PixelLocalStore
    private val repository: LocalOwnerRepository
    private val application: OwnerApplicationService
    private val queries: NativeOwnerQueries

    constructor(context: Context, store: PixelLocalStore) {
        this.context = context
        this.store = store
        repository = LocalOwnerRepository(context, store)
        application = OwnerApplicationService(repository)
        queries = NativeOwnerQueries(context, store, repository)
    }

    override @Throws(Exception::class) fun query(name: String, args: JSONObject): JSONObject? {

        return queries.query(name, args)
    }

    @Throws(Exception::class)
    fun execute(name: String, args: JSONObject): JSONObject {

        if (AppointmentTools.write(name)) {
            McpSchema.validate(args, AppointmentTools.schema(name))
        } else {
            if (OfferTools.write(name)) {
                McpSchema.validate(args, OfferTools.schema(name))
            } else {
                PixelMcpOwnerTools.validate(name, args)
            }
        }
        val request = ("mcp_owner_" + PixelMcpWriteTools.hash(args.getString("request_id")))
        val fingerprint = PixelMcpWriteTools.hash(JSONObject().put("tool", name).put("args", args))
        var result =
            try {
                (if (LocalWorkflowTools.backup(name))
                    application.perform(name, JSONObject(args.toString()), request)
                else
                    repository.runRequest(
                        request,
                        fingerprint,
                        LAM@{
                            var changed =
                                application.perform(name, JSONObject(args.toString()), request)
                            return@LAM changed
                        },
                    ))
            } catch (failure: IllegalArgumentException) {
                if (name in SettlementTools.WRITES && failure !is PixelMcpWriteTools.Rejected) {
                    throw PixelMcpWriteTools.Rejected(
                        "SETTLEMENT_VALIDATION",
                        failure.message ?: "Abrechnungsangaben prüfen.",
                    )
                }
                throw failure
            } catch (failure: java.io.IOException) {
                // Only a verified still-prepared row without a committed receipt permits
                // clearing the UI's uncertain request. A failed state read stays uncertain.
                val notCommitted =
                    name == "complete_settlement" &&
                        runCatching {
                                store.ownerValue("receipt:$request", "").isEmpty() &&
                                    SettlementRules.find(
                                            SettlementRules.ledger(repository),
                                            args.getString("settlement_id"),
                                        )
                                        .optString("status") == "prepared"
                            }
                            .getOrDefault(false)
                if (notCommitted)
                    throw PixelMcpWriteTools.Rejected(
                        "SETTLEMENT_DOCUMENT_FAILED",
                        "Original-PDF konnte nicht fertiggestellt werden. Die geprüfte Vorbereitung und ihre Nummer bleiben erhalten. Erneut versuchen oder die Vorbereitung bewusst aufheben.",
                    )
                throw failure
            }
        if (name == "begin_file_upload" || name == "write_file_chunk" || name == "create_photo") {
            try {
                repository.pruneUnreferencedPhotoUploads()
            } catch (pending: Exception) {
                // The chunk and its replay receipt have already committed.
                result.put("upload_cleanup_pending", true)
            }
        }
        if (name in setOf("purge_entity", "empty_trash", "delete_entity", "delete_work_records")) {
            try {

                LocalStorageMaintenance(context, store).clean(false)
            } catch (pending: Exception) {
                store.putOwnerValue(
                    "local_media_cleanup",
                    JSONObject()
                        .put("pending", true)
                        .put("code", "STORAGE_CLEANUP_PENDING")
                        .toString(),
                )
                result.put("storage_cleanup_pending", true)
            }
        }
        MainActivity.refreshBusinessView()
        if (AppointmentTools.write(name)) {
            PlanningJob.schedule(context, 1000)
        }
        if (name == "plan_inquiry_inspection") {
            try {

                PlanningJob.schedule(context, 1000)
            } catch (deferred: Exception) {
                result.put("external_scheduling_pending", true)
            }
        }
        return JSONObject().put("ok", true).put("data", result).put("cloud_confirmed", false)
    }
}
