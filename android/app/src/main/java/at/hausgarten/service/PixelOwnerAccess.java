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
        if(name.equals("prepare_legacy_conflict_backup"))return new JSONObject().put("ok",true).put("data",repository.prepareLegacyBackup(args.getString("request_id"),args.optString("expected_preview_hash")));
        String request="mcp_owner_"+PixelMcpWriteTools.hash(args.getString("request_id")),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("tool",name).put("args",args));
        JSONObject result=repository.runRequest(request,fingerprint,()->application.perform(name,new JSONObject(args.toString()),request));
        if(name.equals("create_appointment")||name.equals("create_appointment_series"))AppointmentRuntime.enableWeatherForAppointment(context,store);
        if(AppointmentTools.write(name))PlanningJob.schedule(context,1000);
        if(name.equals("request_sync"))result=DriveSyncCoordinator.requestExplicit(context,request);
        else if(!AppointmentTools.write(name)&&!Arrays.asList("begin_file_upload","write_file_chunk","create_backup","set_personal_paid","update_settings","prepare_invoice_share").contains(name))DriveSyncCoordinator.request(context,1000);
        return new JSONObject().put("ok",true).put("data",result).put("cloud_confirmed",false);
    }
}
