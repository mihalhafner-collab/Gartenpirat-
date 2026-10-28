package at.hausgarten.service;
import android.content.Context;
import org.json.*;
import java.util.Arrays;

/** Native MCP boundary: validation, routing and post-commit scheduling only. */
final class PixelOwnerAccess implements PixelMcpOwnerTools.Access {
    private final Context context;
    private final PixelLocalStore store;
    private final LocalOwnerRepository repository;
    private final OwnerApplicationService application;
    private final NativeOwnerQueries queries;
    PixelOwnerAccess(Context context,PixelLocalStore store){
        this.context=context;
        this.store=store;
        repository=new LocalOwnerRepository(context,store);
        application=new OwnerApplicationService(repository);
        queries=new NativeOwnerQueries(context,store,repository);
    }
    @Override public JSONObject query(String name,JSONObject args)throws Exception{return queries.query(name,args);}
    JSONObject execute(String name,JSONObject args)throws Exception{
        if(AppointmentTools.write(name))McpSchema.validate(args,AppointmentTools.schema(name));else if(OfferTools.write(name))McpSchema.validate(args,OfferTools.schema(name));else PixelMcpOwnerTools.validate(name,args);
        if((name.equals("request_sync")||name.equals("prepare_legacy_conflict_backup")||name.equals("close_legacy_conflicts"))&&!DriveRolePolicy.businessSyncAllowed())return new JSONObject().put("ok",true).put("data",DriveRolePolicy.retiredResponse()).put("cloud_confirmed",false);
        if(name.equals("request_invoice_issue")&&!DriveRolePolicy.businessSyncAllowed()&&!LocalAuthorityGuard.local(context))throw new PixelMcpWriteTools.Rejected("LOCAL_AUTHORITY_REQUIRED","Die Rechnungsausgabe ist bis zur kontrollierten lokalen Umstellung und zum aktuellen Beleg- und Nummernabgleich gesperrt. Es wurde kein Onlineabschluss angefordert.");
        if(name.equals("prepare_legacy_conflict_backup"))return new JSONObject().put("ok",true).put("data",repository.prepareLegacyBackup(args.getString("request_id"),args.optString("expected_preview_hash")));
        String request="mcp_owner_"+PixelMcpWriteTools.hash(args.getString("request_id")),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("tool",name).put("args",args));
        if(LocalWorkflowTools.authorityMutation(name)){JSONObject changed=repository.localInvoiceAction(name,args,request);MainActivity.refreshBusinessView();return new JSONObject().put("ok",true).put("data",changed).put("cloud_confirmed",false);}
        // Backup controls own context-bound receipts: even a retry must recheck the current restore epoch.
        JSONObject result=LocalWorkflowTools.backup(name)?application.perform(name,new JSONObject(args.toString()),request):repository.runRequest(request,fingerprint,()->{JSONObject changed=application.perform(name,new JSONObject(args.toString()),request);if(name.equals("create_appointment")||name.equals("create_appointment_series"))AppointmentRuntime.enableWeatherForAppointment(context,store);return changed;});
        if(name.equals("purge_entity")||name.equals("empty_trash"))try{store.compactPurgedTestActions();}catch(Exception pending){store.putOwnerValue("local_test_cleanup_error","JOURNAL_CLEANUP_PENDING");result.put("test_cleanup_state","review_required");}
        MainActivity.refreshBusinessView();
        if(AppointmentTools.write(name))PlanningJob.schedule(context,1000);
        if(name.equals("plan_inquiry_inspection"))try{PlanningJob.schedule(context,1000);}catch(Exception deferred){result.put("external_scheduling_pending",true);}
        if(name.equals("request_invoice_issue")||name.equals("retry_invoice_document_job"))try{LocalInvoiceJobs.schedule(context);}catch(Exception deferred){result.put("document_scheduling_pending",true);}
        if(name.equals("request_sync"))result=DriveSyncCoordinator.requestExplicit(context,request);
        return new JSONObject().put("ok",true).put("data",result).put("cloud_confirmed",false);
    }
}
