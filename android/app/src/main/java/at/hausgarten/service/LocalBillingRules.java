package at.hausgarten.service;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

/** Exact remaining local source quantities. Legacy links never imply a guessed partial amount. */
final class LocalBillingRules {
    private static PixelMcpWriteTools.Rejected invalid(){return new PixelMcpWriteTools.Rejected("LOCAL_BILLING_SOURCE_REVIEW_REQUIRED","Lokale Leistungszuordnung und Belegsnapshot passen nicht zusammen. Keine erneute Abrechnung freigeben; Quellen prüfen.");}
    private static void require(boolean condition)throws Exception {if(!condition)throw invalid();}
    private static String decimal(BigDecimal amount){return amount.stripTrailingZeros().toPlainString();}
    private static BigDecimal amount(Object raw)throws Exception {if(raw==null||raw==JSONObject.NULL)throw invalid();try{BigDecimal value=new BigDecimal(raw.toString());require(value.signum()>=0&&value.precision()<=24&&Math.abs(value.scale())<=10);return value;}catch(NumberFormatException malformed){throw invalid();}}
    private static Map<String,JSONObject> indexed(JSONArray values)throws Exception {Map<String,JSONObject> result=new HashMap<>();for(int i=0;values!=null&&i<values.length();i++){JSONObject value=values.getJSONObject(i);String id=value.optString("id");require(!id.isEmpty()&&result.put(id,value)==null);}return result;}
    static boolean linked(JSONObject invoice,String id)throws Exception {JSONArray links=invoice.optJSONArray("worklog_ids");for(int i=0;links!=null&&i<links.length();i++)if(id.equals(links.optString(i)))return true;JSONArray lines=invoice.optJSONArray("positions");for(int i=0;lines!=null&&i<lines.length();i++)if(id.equals(lines.getJSONObject(i).optString("worklog_id")))return true;return false;}
    private static boolean issued(JSONObject invoice){return !invoice.optString("issued_at").isEmpty()||!invoice.optString("sevdesk_id").isEmpty()&&!invoice.optString("status").equals("Entwurf");}
    static void validate(JSONObject master,JSONObject sources)throws Exception {
        Object format=sources.opt("formatVersion");require(format instanceof Number&&format.toString().equals("1"));JSONObject byInvoice=sources.getJSONObject("by_invoice");Map<String,BigDecimal> totals=new HashMap<>();JSONArray invoices=master.optJSONArray("invoices"),logs=master.optJSONArray("worklogs");Map<String,JSONObject> invoiceIndex=indexed(invoices),logIndex=indexed(logs);
        for(Iterator<String> ids=byInvoice.keys();ids.hasNext();){String id=ids.next();JSONObject invoice=invoiceIndex.get(id),allocation=byInvoice.getJSONObject(id);require(invoice!=null);JSONObject snapshot=invoice.optJSONObject("local_source_allocations");require(issued(invoice)&&snapshot!=null&&PixelMcpWriteTools.hash(allocation).equals(PixelMcpWriteTools.hash(snapshot))&&LocalAuthorityRules.ENGINE.equals(invoice.optJSONObject("numbering_receipt")==null?"":invoice.getJSONObject("numbering_receipt").optString("authority"))&&!invoice.optString("local_issue_request").isEmpty());
            Map<String,BigDecimal> lineQuantities=new HashMap<>();JSONArray lines=invoice.optJSONArray("positions");for(int i=0;lines!=null&&i<lines.length();i++){JSONObject line=lines.getJSONObject(i);String work=line.optString("worklog_id");if(work.isEmpty())continue;require(!lineQuantities.containsKey(work)&&allocation.has(work));lineQuantities.put(work,amount(line.get("quantity")));}require(lineQuantities.size()==allocation.length());
            for(Iterator<String> keys=allocation.keys();keys.hasNext();){String work=keys.next();JSONObject log=logIndex.get(work);require(log!=null);BigDecimal quantity=amount(allocation.get(work));require(allocation.get(work) instanceof String&&quantity.signum()>0&&lineQuantities.containsKey(work)&&quantity.compareTo(lineQuantities.get(work))==0&&linked(invoice,work));totals.put(work,totals.getOrDefault(work,BigDecimal.ZERO).add(quantity));require(totals.get(work).compareTo(amount(log.get("hours")))<=0);}
        }
        for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject invoice=invoices.getJSONObject(i);if(invoice.has("local_source_allocations")&&issued(invoice))require(byInvoice.has(invoice.getString("id")));}
    }
    static JSONObject state(JSONObject row,JSONArray invoices,JSONObject master)throws Exception {
        JSONObject sources=master.optJSONObject("local_billing_sources");if(sources==null)return null;Object version=sources.opt("formatVersion");require(version instanceof Number&&version.toString().equals("1"));JSONObject byInvoice=sources.getJSONObject("by_invoice");BigDecimal quantity=amount(row.opt("hours")),allocated=BigDecimal.ZERO;boolean draft=false,found=false;
        for(int i=0;invoices!=null&&i<invoices.length();i++){
            JSONObject invoice=invoices.getJSONObject(i);if(!linked(invoice,row.optString("id")))continue;if(!issued(invoice)){draft=true;continue;}
            JSONObject values=byInvoice.optJSONObject(invoice.optString("id"));if(values==null||!values.has(row.optString("id")))return null;allocated=allocated.add(amount(values.get(row.getString("id"))));found=true;
        }
        require(allocated.compareTo(quantity)<=0);BigDecimal remaining=quantity.subtract(allocated);if(row.optInt("billed",0)!=0&&remaining.signum()>0)return null;
        String evidence=remaining.signum()==0&&found?"proven_invoiced":allocated.signum()>0?"partially_invoiced":draft?"draft_linked_not_proven":"marked_open_not_proven";
        return new JSONObject().put("formatVersion",1).put("quantity_decimal",decimal(quantity)).put("allocated_decimal",decimal(allocated)).put("remaining_decimal",decimal(remaining)).put("evidence",evidence);
    }
    private LocalBillingRules(){}
}
