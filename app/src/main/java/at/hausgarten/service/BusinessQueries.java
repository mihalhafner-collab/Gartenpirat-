package at.hausgarten.service;
import org.json.*;
import java.util.*;
import static at.hausgarten.service.SnapshotReadSupport.*;

/** Queries are grouped by business domain; transport/permissions stay in the MCP adapter. */
final class BusinessQueries {
    interface Query { Object run(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception; }
    private static final Map<String, Query> QUERIES = new LinkedHashMap<>();
    static {
        QUERIES.put("search_customers", BusinessQueries::customersOrders);
        QUERIES.put("get_customer", BusinessQueries::customersOrders);
        QUERIES.put("search_orders", BusinessQueries::customersOrders);
        QUERIES.put("get_order", BusinessQueries::customersOrders);
        QUERIES.put("get_open_orders", BusinessQueries::customersOrders);
        QUERIES.put("list_work_records", BusinessQueries::work);
        QUERIES.put("get_customer_work_records", BusinessQueries::work);
        QUERIES.put("get_work_record_summary", BusinessQueries::work);
        QUERIES.put("get_active_work_session", BusinessQueries::work);
        QUERIES.put("get_work_record", BusinessQueries::work);
        QUERIES.put("list_invoices", BusinessQueries::documents);
        QUERIES.put("get_invoice", BusinessQueries::documents);
        QUERIES.put("list_photos", BusinessQueries::documents);
        QUERIES.put("get_sync_overview", BusinessQueries::sync);
        QUERIES.put("get_sync_status", BusinessQueries::sync);
        QUERIES.put("get_business_summary", BusinessQueries::overview);
        QUERIES.put("search_business_data", BusinessQueries::overview);
    }
    static Object query(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception {
        Query query = QUERIES.get(name);
        if (query == null) throw new IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.");
        if(state.optBoolean("storage_list_selected")){
            if(name.equals("search_business_data"))return new JSONObject().put("customers",master.getJSONArray("customers")).put("orders",master.getJSONArray("orders")).put("work_records",master.getJSONArray("worklogs"));
            String collection=name.equals("search_customers")?"customers":name.equals("search_orders")||name.equals("get_open_orders")?"orders":name.equals("list_work_records")||name.equals("get_customer_work_records")?"worklogs":name.equals("list_invoices")?"invoices":name.equals("list_photos")?"photos":"";
            if(collection.isEmpty())throw new IllegalArgumentException("TABLE_LIST_QUERY_UNSUPPORTED");
            JSONArray rows=master.getJSONArray(collection);JSONObject result=new JSONObject().put(collection.equals("worklogs")?"work_records":collection,rows);
            if(name.equals("search_customers"))result.put("count",rows.length()).put("requires_clarification",rows.length()>1);return result;
        }
        // Name is passed as immutable dispatch data, never inferred from record contents.
        return query.run(name, a, state, master, writer);
    }
    private static Object customersOrders(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception {
        JSONArray customers=master.optJSONArray("customers"),orders=master.optJSONArray("orders"),logs=master.optJSONArray("worklogs"),invoices=master.optJSONArray("invoices"),photos=master.optJSONArray("photos");
        Object data;
        switch(name){
            case "search_customers":{JSONArray matches=select(customers,a,"customer",master);data=new JSONObject().put("customers",matches).put("count",matches.length()).put("requires_clarification",matches.length()>1);break;}
            case "get_customer":data=find(customers,a.getString("customer_id"));break;
            case "search_orders":data=new JSONObject().put("orders",select(orders,a,"order",master));break;
            case "get_order":data=find(orders,a.getString("order_id"));break;
            case "get_open_orders":data=new JSONObject().put("orders",select(orders,new JSONObject(a.toString()).put("open_only",true).put("limit",a.optInt("limit",50)),"order",master));break;
            default:throw new IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.");
        }
        return data;
    }
    private static Object work(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception {
        JSONArray customers=master.optJSONArray("customers"),orders=master.optJSONArray("orders"),logs=master.optJSONArray("worklogs"),invoices=master.optJSONArray("invoices"),photos=master.optJSONArray("photos");
        Object data;
        switch(name){
            case "list_work_records":data=new JSONObject().put("work_records",select(logs,a,"worklog",master));break;
            case "get_customer_work_records":data=new JSONObject().put("work_records",select(logs,a,"worklog",master));break;
            case "get_work_record_summary":{JSONArray matching=new JSONArray();for(int i=0;logs!=null&&i<logs.length();i++){JSONObject row=logs.getJSONObject(i);if(!a.optString("customer_id").isEmpty()&&!a.getString("customer_id").equals(row.optString("customer_id")))continue;if(!a.optString("billing_customer_id").isEmpty()&&!a.getString("billing_customer_id").equals(BillingRules.recipient(row)))continue;if(!a.optString("date_from").isEmpty()&&row.optString("work_date").compareTo(a.getString("date_from"))<0)continue;if(!a.optString("date_to").isEmpty()&&row.optString("work_date").compareTo(a.getString("date_to"))>0)continue;matching.put(row);}data=PixelMcpBusinessTools.report(matching,master);if(a.optBoolean("group_by_billing_customer"))((JSONObject)data).put("billing_groups",BillingRules.groups(matching,master));break;}
            case "get_active_work_session":data=state.has("work_session")?state.getJSONObject("work_session"):new JSONObject().put("state","UNAVAILABLE");break;
            case "get_work_record":data=find(logs,a.getString("work_record_id"));break;
            default:throw new IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.");
        }
        return data;
    }
    private static Object documents(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception {
        JSONArray customers=master.optJSONArray("customers"),orders=master.optJSONArray("orders"),logs=master.optJSONArray("worklogs"),invoices=master.optJSONArray("invoices"),photos=master.optJSONArray("photos");
        Object data;
        switch(name){
            case "list_invoices":data=new JSONObject().put("invoices",select(invoices,a,"invoice",master));break;
            case "get_invoice":data=find(invoices,a.getString("invoice_id"));break;
            case "list_photos":data=new JSONObject().put("photos",select(photos,a,"photo",master));break;
            default:throw new IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.");
        }
        return data;
    }
    private static Object sync(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception {
        JSONArray customers=master.optJSONArray("customers"),orders=master.optJSONArray("orders"),logs=master.optJSONArray("worklogs"),invoices=master.optJSONArray("invoices"),photos=master.optJSONArray("photos");
        Object data;
        switch(name){
            case "get_sync_overview":{JSONArray pending=state.optJSONArray("pending");data=new JSONObject().put("integrity",PixelDriveState.integrity(state.getJSONObject("master"))).put("pending_actions",pending==null?0:pending.length()).put("last_drive_sync",state.optString("last_drive_sync")).put("sync_errors",master.optJSONArray("sync_errors")==null?new JSONArray():master.getJSONArray("sync_errors")).put("timezone","Europe/Vienna").put("archived_conflict_reviews",master.optJSONObject("owner_conflict_history")==null?0:master.getJSONObject("owner_conflict_history").length()).put("journal_errors",master.optJSONArray("drive_journal_errors")==null?new JSONArray():master.getJSONArray("drive_journal_errors")).put("confirmation_scope",state.optString("confirmation_scope","drive_provider_readback")).put("cloud_confirmed",state.optBoolean("cloud_confirmed",false)&&"drive_server_manifest".equals(state.optString("confirmation_scope"))).put("last_full_sync",state.optString("last_full_sync")).put("notice","Lokale Speicherung, Provider-Rücklesen und vollständiger Abgleich sind getrennte Zustände. Nur geprüfte Drive-Serverbelege bestätigen die Übernahme.");break;}
            case "get_sync_status":{if(writer!=null){data=PixelMcpBusinessTools.sync(writer.status(a.getString("client_id"),state),state);break;}String id=a.getString("client_id"),status="unknown";JSONArray accepted=master.optJSONArray("accepted_ids");if(accepted!=null)for(int i=0;i<accepted.length();i++)if(id.equals(accepted.optString(i)))status="accepted";
                JSONArray pending=state.optJSONArray("pending");if(!"accepted".equals(status)&&pending!=null)for(int i=0;i<pending.length();i++)if(id.equals(pending.getJSONObject(i).optString("client_id")))status="queued";
                boolean applied=DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(id);if(applied&&!status.equals("accepted"))status="transferred";
                data=new JSONObject().put("client_id",id).put("status",status).put("confirmed_by_app","accepted".equals(status)).put("applied_on_pixel",applied).put("cloud_confirmed",false);break;}
            default:throw new IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.");
        }
        return data;
    }
    private static Object overview(String name, JSONObject a, JSONObject state, JSONObject master, PixelMcpWriteTools.Sink writer) throws Exception {
        JSONArray customers=master.optJSONArray("customers"),orders=master.optJSONArray("orders"),logs=master.optJSONArray("worklogs"),invoices=master.optJSONArray("invoices"),photos=master.optJSONArray("photos");
        Object data;
        switch(name){
            case "get_business_summary":{double hours=0;int unbilled=0;for(int i=0;logs!=null&&i<logs.length();i++){JSONObject row=logs.getJSONObject(i);hours+=row.optDouble("hours",0);if(row.optInt("billed",0)==0)unbilled++;}data=new JSONObject().put("customers",customers==null?0:customers.length()).put("orders",orders==null?0:orders.length()).put("work_records",logs==null?0:logs.length()).put("hours",Math.round(hours*100)/100.0).put("marked_unbilled",unbilled).put("billing_groups",BillingRules.groups(logs,master));break;}
            case "search_business_data":{JSONObject filters=new JSONObject().put("query",a.getString("query")).put("include_inactive",true).put("limit",a.optInt("limit_per_type",10));data=new JSONObject().put("customers",select(customers,filters,"customer",master)).put("orders",select(orders,filters,"order",master)).put("work_records",select(logs,filters,"worklog",master));break;}
            default:throw new IllegalArgumentException("Dieses Werkzeug ist in der Pixel-Testversion nicht freigegeben.");
        }
        return data;
    }
}
