package at.hausgarten.service;
import org.json.*;
import java.util.*;

/** Pure bounded selection/redaction. No Android, provider or writes. */
final class SnapshotReadSupport {
    private static final java.util.regex.Pattern NATURAL=java.util.regex.Pattern.compile("\\d+|\\D+");
    static JSONObject find(JSONArray rows,String id) throws Exception {
        if(rows!=null)for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);
        throw new IllegalArgumentException("Die Datensatz-ID wurde im Pixel-Datenstand nicht gefunden.");
    }
    static String text(JSONObject row,String...keys){StringBuilder value=new StringBuilder();for(String key:keys)value.append(' ').append(row.optString(key));return value.toString().toLowerCase(Locale.GERMAN);}
    static String fold(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(Locale.GERMAN).replace("ß","ss").replace("ae","a").replace("oe","o").replace("ue","u");}
    static boolean matches(String query,String haystack){String value=fold(haystack);for(String term:fold(query).trim().split("\\s+"))if(!value.contains(term))return false;return true;}
    static int natural(String a,String b,java.text.Collator collator){
        java.util.regex.Matcher left=NATURAL.matcher(a),right=NATURAL.matcher(b);
        while(left.find()){if(!right.find())return 1;String x=left.group(),y=right.group();int order;
            if(Character.isDigit(x.charAt(0))&&Character.isDigit(y.charAt(0)))order=new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));else order=collator.compare(x,y);
            if(order!=0)return order;
        }return right.find()?-1:0;
    }
    static Object withoutBinary(Object value)throws Exception{
        if(value instanceof JSONArray){JSONArray source=(JSONArray)value,result=new JSONArray();for(int i=0;i<source.length();i++)result.put(withoutBinary(source.get(i)));return result;}
        if(!(value instanceof JSONObject))return value;
        JSONObject source=(JSONObject)value,result=new JSONObject();boolean photo=source.has("original_name")&&(source.has("data")||source.has("storage_name")||source.has("mime_type"));Iterator<String> keys=source.keys();
        while(keys.hasNext()){
            String key=keys.next(),lower=key.toLowerCase(Locale.ROOT);Object item=source.get(key);
            boolean inlineBinary=item instanceof String&&((String)item).trim().toLowerCase(Locale.ROOT).matches("^data:[^,]+;base64,.*");
            boolean plainBase64=lower.equals("data")&&item instanceof String&&((String)item).length()>256&&((String)item).matches("[A-Za-z0-9+/]*={0,2}");
            if(lower.endsWith("_base64")||lower.equals("thumbnail_data")||(photo&&lower.equals("data"))||inlineBinary||plainBase64)continue;
            result.put(key,withoutBinary(item));
        }return result;
    }
    static JSONArray select(JSONArray rows,JSONObject a,String kind) throws Exception {
        return select(rows,a,kind,null);
    }
    static JSONArray select(JSONArray rows,JSONObject a,String kind,JSONObject context) throws Exception {
        JSONArray result=new JSONArray();if(rows==null)return result;
        String query=a.optString("query","").trim();
        int limit=a.optInt("limit",kind.equals("worklog")?50:20);if(limit<1||limit>100)throw new IllegalArgumentException("Bitte 1 bis 100 Ergebnisse anfordern.");
        ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;i<rows.length();i++)sorted.add(rows.getJSONObject(i));
        if(kind.equals("customer")||kind.equals("order")){java.text.Collator collator=java.text.Collator.getInstance(Locale.GERMAN);collator.setStrength(java.text.Collator.PRIMARY);String field=kind.equals("customer")?"name":"title";
            sorted.sort((x,y)->{int order=kind.equals("customer")?Boolean.compare(x.optInt("active",1)==0||!x.optBoolean("active",true),y.optInt("active",1)==0||!y.optBoolean("active",true)):0;
                if(order==0)order=natural(x.optString(field),y.optString(field),collator);return order==0?x.optString("id").compareTo(y.optString("id")):order;});}
        if(kind.equals("worklog"))sorted.sort(Comparator.comparing((JSONObject x)->x.optString("work_date")).reversed().thenComparing(x->x.optString("id")));
        if(kind.equals("photo"))sorted.sort(Comparator.comparing((JSONObject x)->x.optString("capture_time",x.optString("created_at"))).reversed().thenComparing(x->x.optString("id")));
        Map<String,String> customerNames=new HashMap<>(),orderTitles=new HashMap<>();
        if(context!=null){JSONArray cs=context.optJSONArray("customers"),os=context.optJSONArray("orders");
            for(int i=0;cs!=null&&i<cs.length();i++){JSONObject c=cs.getJSONObject(i);customerNames.put(c.optString("id"),text(c,"name","company"));}
            for(int i=0;os!=null&&i<os.length();i++){JSONObject o=os.getJSONObject(i);orderTitles.put(o.optString("id"),o.optString("title"));}}
        int offset=a.optInt("offset"),matched=0;if(offset<0||offset>1000000)throw new IllegalArgumentException("Ungültiger Listenversatz.");
        for(JSONObject row:sorted){
            if(kind.equals("photo")){boolean skip=false;for(String f:new String[]{"role","worklog_id","session_id"})if(!a.optString(f).isEmpty()&&!a.getString(f).equals(row.optString(f)))skip=true;if(skip)continue;}
            if(kind.equals("customer")&&!a.optBoolean("include_inactive",false)&&(row.optInt("active",1)==0||!row.optBoolean("active",true)))continue;
            if(!a.optString("customer_id").isEmpty()&&!a.optString("customer_id").equals(row.optString("customer_id")))continue;
            if(!a.optString("billing_customer_id").isEmpty()&&!a.optString("billing_customer_id").equals(BillingRules.recipient(row)))continue;
            if(!a.optString("order_id").isEmpty()&&!a.optString("order_id").equals(row.optString("order_id")))continue;
            if(!a.optString("status").isEmpty()&&!a.optString("status").equals(row.optString("status")))continue;
            if(kind.equals("order")&&a.optBoolean("open_only",false)&&!java.util.Arrays.asList("","Geplant","Aktiv","In Arbeit","Wartet auf Material","Offen").contains(row.optString("status")))continue;
            if(a.has("billed")&&a.getBoolean("billed")!=(row.optInt("billed",0)!=0))continue;
            String day=kind.equals("photo")?row.optString("capture_time",row.optString("created_at")):row.optString("work_date");if(day.length()>10)day=day.substring(0,10);if(!a.optString("date_from").isEmpty()&&day.compareTo(a.optString("date_from"))<0)continue;
            if(!a.optString("date_to").isEmpty()&&day.compareTo(a.optString("date_to"))>0)continue;
            String haystack=kind.equals("customer")?text(row,"name","company","first_name","last_name","street","postal_code","city","email","phone")
                :kind.equals("order")?text(row,"title","description","location","customer_name"):text(row,"activity","notes","material","location","customer_name","order_title");
            if(context!=null)haystack+=" "+customerNames.getOrDefault(row.optString("customer_id"),"")+" "+orderTitles.getOrDefault(row.optString("order_id"),"");
            haystack+=" "+text(row,"invoice_number","offer_number","caption","original_name","filename");
            if(!query.isEmpty()&&!matches(query,haystack)){
                String digits=query.replaceAll("\\D","");if(!kind.equals("customer")||digits.length()<4||!query.matches("[+0-9\\s()./\\-]+")||!row.optString("phone").replaceAll("\\D","").contains(digits))continue;
            }
            if(matched++<offset)continue;
            result.put(new JSONObject(row.toString()));if(result.length()>=limit)break;
        }
        return (JSONArray)withoutBinary(result);
    }
}
