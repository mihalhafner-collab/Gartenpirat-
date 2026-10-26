package at.hausgarten.service;
import org.json.*;

public final class ServiceCatalogBootstrapTests {
    public static void main(String[] args)throws Exception {
        JSONObject empty=new JSONObject().put("service_catalog_initialized",true).put("service_catalog",new JSONArray());
        ServiceCatalogBootstrap.seed(empty,()->{throw new AssertionError("An intentionally empty catalog must stay empty");});
        if(empty.getJSONArray("service_catalog").length()!=0)throw new AssertionError();
        JSONObject historical=new JSONObject().put("service_catalog",new JSONArray().put(new JSONObject().put("id","qa_existing_template").put("price","12.3400")));
        String original=historical.getJSONArray("service_catalog").toString();ServiceCatalogBootstrap.seed(historical,()->{throw new AssertionError("Existing prices must stay unchanged");});
        if(!historical.getJSONArray("service_catalog").toString().equals(original))throw new AssertionError();
        JSONObject fresh=new JSONObject().put("service_catalog",new JSONArray());ServiceCatalogBootstrap.seed(fresh,()->"[{\"id\":\"qa_template_without_price\",\"unit\":\"h\"}]");
        if(fresh.getJSONArray("service_catalog").getJSONObject(0).has("price")||!fresh.getBoolean("service_catalog_initialized"))throw new AssertionError();
        JSONObject failed=new JSONObject().put("service_catalog",new JSONArray());
        try{ServiceCatalogBootstrap.seed(failed,()->{throw new java.io.IOException("test source unavailable");});throw new AssertionError();}catch(java.io.IOException expected){}
        if(failed.optBoolean("service_catalog_initialized")||failed.getJSONArray("service_catalog").length()!=0)throw new AssertionError();
        System.out.println("PASS4 catalog bootstrap preservation/failure checks; no historical price changes");
    }
}
