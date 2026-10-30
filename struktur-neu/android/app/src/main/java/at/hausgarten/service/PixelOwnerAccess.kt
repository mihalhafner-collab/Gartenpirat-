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
        if (
            (((((name == "request_sync") || (name == "prepare_legacy_conflict_backup")) ||
                (name == "close_legacy_conflicts"))) && !(DriveRolePolicy.businessSyncAllowed()))
        ) {
            return JSONObject()
                .put("ok", true)
                .put("data", DriveRolePolicy.retiredResponse())
                .put("cloud_confirmed", false)
        }
        if (
            (((name == "request_invoice_issue") && !(DriveRolePolicy.businessSyncAllowed())) &&
                !(LocalAuthorityGuard.local(context)))
        ) {
            throw PixelMcpWriteTools.Rejected(
                "LOCAL_AUTHORITY_REQUIRED",
                "Die Rechnungsausgabe ist bis zur kontrollierten lokalen Umstellung und zum aktuellen Beleg- und Nummernabgleich gesperrt. Es wurde kein Onlineabschluss angefordert.",
            )
        }
        if ((name == "prepare_legacy_conflict_backup")) {
            return JSONObject()
                .put("ok", true)
                .put(
                    "data",
                    repository.prepareLegacyBackup(
                        args.getString("request_id"),
                        args.optString("expected_preview_hash"),
                    ),
                )
        }
        var request = ("mcp_owner_" + PixelMcpWriteTools.hash(args.getString("request_id")))
        var fingerprint = PixelMcpWriteTools.hash(JSONObject().put("tool", name).put("args", args))
        if (LocalWorkflowTools.authorityMutation(name)) {
            var changed = repository.localInvoiceAction(name, args, request)
            MainActivity.refreshBusinessView()
            return JSONObject().put("ok", true).put("data", changed).put("cloud_confirmed", false)
        }
        var result =
            (if (LocalWorkflowTools.backup(name))
                application.perform(name, JSONObject(args.toString()), request)
            else
                repository.runRequest(
                    request,
                    fingerprint,
                    LAM@{
                        var changed =
                            application.perform(name, JSONObject(args.toString()), request)
                        if (
                            ((name == "create_appointment") ||
                                (name == "create_appointment_series"))
                        ) {
                            AppointmentRuntime.enableWeatherForAppointment(context, store)
                        }
                        return@LAM changed
                    },
                ))
        if (((name == "purge_entity") || (name == "empty_trash"))) {
            try {

                store.compactPurgedTestActions()
            } catch (pending: Exception) {
                store.putOwnerValue("local_test_cleanup_error", "JOURNAL_CLEANUP_PENDING")
                result.put("test_cleanup_state", "review_required")
            }
        }
        MainActivity.refreshBusinessView()
        if (AppointmentTools.write(name)) {
            PlanningJob.schedule(context, 1000)
        }
        if ((name == "plan_inquiry_inspection")) {
            try {

                PlanningJob.schedule(context, 1000)
            } catch (deferred: Exception) {
                result.put("external_scheduling_pending", true)
            }
        }
        if (((name == "request_invoice_issue") || (name == "retry_invoice_document_job"))) {
            try {

                LocalInvoiceJobs.schedule(context)
            } catch (deferred: Exception) {
                result.put("document_scheduling_pending", true)
            }
        }
        if ((name == "request_sync")) {
            result = DriveSyncCoordinator.requestExplicit(context, request)
        }
        return JSONObject().put("ok", true).put("data", result).put("cloud_confirmed", false)
    }
}
