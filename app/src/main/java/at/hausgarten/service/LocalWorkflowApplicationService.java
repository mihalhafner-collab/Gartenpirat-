package at.hausgarten.service;
import org.json.*;

/** Thin shared command adapter; native UI and MCP use the same repository use cases. */
final class LocalWorkflowApplicationService implements OwnerCommand {
    private final OwnerRepository repository;
    LocalWorkflowApplicationService(OwnerRepository repository){this.repository=repository;}
    @Override public JSONObject perform(String name,JSONObject args,String request)throws Exception {
        if(LocalWorkflowTools.backup(name))return repository.fullBackupAction(name,args);
        if(LocalWorkflowTools.revier(name))return repository.revierAction(name,args);
        if(LocalWorkflowTools.financial(name)){JSONObject result=repository.localInvoiceAction(name,args,request);if(result==null)throw new PixelMcpWriteTools.Rejected("LOCAL_AUTHORITY_REQUIRED","Diesen Vorgang erst nach kontrollierter lokaler Autoritätsumstellung verwenden.");return result;}
        if(LocalWorkflowTools.offerDecision(name))return new OfferApplicationService(repository).perform(name,args,request);
        throw new IllegalArgumentException("Lokaler Anwendungsfall fehlt.");
    }
}
