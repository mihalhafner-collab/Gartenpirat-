package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Dispatch only; each domain service is independently testable without Android. */
final class OwnerApplicationService {
    private final Map<String,OwnerCommand> commands=new LinkedHashMap<>();
    OwnerApplicationService(OwnerRepository repository){
        register(new OfferApplicationService(repository),OfferTools.WRITES);
        register(new WorkApplicationService(repository),new String[]{"save_reviewed_order","set_work_record_billed","reassign_work_record","set_personal_paid"});
        register(new PhotoApplicationService(repository),new String[]{"begin_file_upload","write_file_chunk","create_photo","update_photo"});
        register(new SyncArchiveApplicationService(repository),new String[]{"empty_trash","archive_work_records","close_legacy_conflicts","request_sync","purge_entity","delete_entity","archive_entity","restore_entity","resolve_sync_conflict","create_backup","restore_backup"});
        register(new SettingsApplicationService(repository),new String[]{"update_business_settings","update_settings"});
        register(new InvoiceApplicationService(repository),new String[]{"request_invoice_issue","create_invoice_correction","update_invoice_payment","prepare_invoice_share"});
        register(new PlanningApplicationService(repository),AppointmentTools.WRITES);
        register(new LocalWorkflowApplicationService(repository),LocalWorkflowTools.WRITES);
    }
    private void register(OwnerCommand command,String[] names){
        for(String name:names)if(commands.put(name,command)!=null)throw new IllegalStateException("Duplicate owner operation");
    }
    boolean handles(String name){return commands.containsKey(name);}
    JSONObject perform(String name,JSONObject args,String request)throws Exception{
        OwnerCommand command=commands.get(name);
        if(command==null)throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        return command.perform(name,args,request);
    }
}
