package at.hausgarten.service;
import org.json.*;

/** One existing catalog bootstrap rule, shared by live state and captured-state readers. */
final class ServiceCatalogBootstrap {
    interface Source {String read()throws Exception;}
    static void seed(JSONObject snapshot,Source source)throws Exception {
        if(!snapshot.optBoolean("service_catalog_initialized")&&snapshot.getJSONArray("service_catalog").length()==0)snapshot.put("service_catalog",new JSONArray(source.read()));
        snapshot.put("service_catalog_initialized",true);
    }
    private ServiceCatalogBootstrap(){}
}
