package at.hausgarten.service;
import org.json.*;
import java.util.*;
/** Versioned catalogue-only serialization. Never used for business/CAS/publication hashes. */
final class CatalogSemanticHash {
    static final String VERSION="catalog-json-v1";
    static String canonical(Object value)throws Exception {
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;ArrayList<String> keys=new ArrayList<>();for(Iterator<String> i=o.keys();i.hasNext();)keys.add(i.next());Collections.sort(keys);ArrayList<String> parts=new ArrayList<>();for(String k:keys)parts.add(quote(k)+":"+canonical(o.get(k)));return "{"+String.join(",",parts)+"}";}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;ArrayList<String> parts=new ArrayList<>();for(int i=0;i<a.length();i++)parts.add(canonical(a.get(i)));return "["+String.join(",",parts)+"]";}
        if(value instanceof String)return quote((String)value);
        return PixelMcpWriteTools.canonical(value);
    }
    private static String quote(String s){
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){
            case '\\':out.append("\\\\");break;case '\"':out.append("\\\"");break;
            case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;
            default:if(c<32||c==0x2028||c==0x2029)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
        }}return out.append('\"').toString();
    }
    static String hash(Object value)throws Exception{return DriveRestClient.sha256(canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static JSONObject describe(Object value)throws Exception{return new JSONObject().put("version",VERSION).put("sha256",hash(value));}
    private CatalogSemanticHash(){}
}
