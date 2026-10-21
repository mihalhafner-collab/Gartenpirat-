package at.hausgarten.service;

import org.json.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Derives weather context without changing an appointment or guessing another customer's site. */
final class WeatherLocationRules {
    static final int RESOLVER_VERSION=2;
    interface Lookup {JSONArray find(String query)throws Exception;}
    static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace("ß","ss").replaceAll("strasse\\b","str").replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");}
    static String address(JSONObject customer){String street=customer.optString("street").trim(),town=(customer.optString("postal_code")+" "+customer.optString("city")).trim();return street.isEmpty()?town:town.isEmpty()?street:street+", "+town;}
    static boolean coordinates(JSONObject a){double lat=a.optDouble("latitude",Double.NaN),lon=a.optDouble("longitude",Double.NaN);return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    static JSONObject context(JSONObject appointment,JSONArray customers,JSONArray orders)throws Exception {
        JSONObject a=new JSONObject(appointment.toString());String place=a.optString("location").trim();
        JSONObject customer=OwnerJournalRules.findOrNull(customers,a.optString("customer_id"));
        if(place.isEmpty()&&!a.optString("order_id").isEmpty()){
            JSONObject order=OwnerJournalRules.findOrNull(orders,a.optString("order_id"));
            if(order!=null&&a.optString("customer_id").equals(order.optString("customer_id")))place=order.optString("location").trim();
        }
        if(place.isEmpty()&&customer!=null)place=address(customer);
        a.put("weather_location_label",place).put("weather_location_state",place.isEmpty()?"missing":"pending");
        if(coordinates(a))return a.put("weather_location_state","confirmed_coordinates");
        if(customer!=null&&!place.isEmpty()&&normalized(place).equals(normalized(address(customer)))&&coordinates(customer)){
            a.put("latitude",customer.getDouble("latitude")).put("longitude",customer.getDouble("longitude")).put("weather_location_state","customer_coordinates");
        }
        return a;
    }
    static String cacheKey(JSONObject a)throws Exception{return "weather:location:"+PixelMcpWriteTools.hash(a.optString("customer_id")+"\n"+normalized(a.optString("weather_location_label")));}
    static JSONObject cached(JSONObject context,JSONObject cache)throws Exception {
        JSONObject a=new JSONObject(context.toString());if(coordinates(a))return a;
        if(coordinates(cache))a.put("latitude",cache.getDouble("latitude")).put("longitude",cache.getDouble("longitude")).put("weather_location_state",cache.optString("state").equals("locality_resolved")?"locality_resolved":"address_resolved").put("weather_location_area",cache.optString("area_label")).put("weather_location_precision",cache.optString("precision","address"));
        else if(cache.has("state"))a.put("weather_location_state",cache.getString("state"));return a;
    }
    private static String matchText(String value){return normalized(value).replaceAll("(\\p{L})\\s+str\\b","$1str");}
    static boolean matches(String query,String returnedAddress){
        String wanted=matchText(query),actual=" "+matchText(returnedAddress)+" ";if(wanted.length()<3)return false;
        for(String word:wanted.split(" "))if(!actual.contains(" "+word+" "))return false;return true;
    }
    static JSONObject choose(String query,JSONArray candidates)throws Exception {
        JSONObject chosen=null;for(int i=0;i<candidates.length();i++){
            JSONObject c=candidates.getJSONObject(i);if(!coordinates(c)||!matches(query,c.optString("address")))continue;
            if(chosen!=null&&(Math.abs(chosen.getDouble("latitude")-c.getDouble("latitude"))>0.009||Math.abs(chosen.getDouble("longitude")-c.getDouble("longitude"))>0.015))return new JSONObject().put("state","ambiguous");
            chosen=c;
        }
        return chosen==null?new JSONObject().put("state","not_found"):new JSONObject().put("latitude",chosen.getDouble("latitude")).put("longitude",chosen.getDouble("longitude")).put("state","address_resolved");
    }
    /** Only the explicitly written postal town may be used as a coarser weather area. */
    static String localityQuery(String query){
        Matcher matcher=Pattern.compile("(?<![\\p{L}\\d])(\\d{4,5})\\s+([\\p{L}][^,;\\n]*)").matcher(query);String locality="";
        while(matcher.find())locality=matcher.group(1)+" "+matcher.group(2).trim();
        return locality;
    }
    static JSONObject chooseLocality(String query,JSONArray candidates)throws Exception {
        JSONArray towns=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject c=candidates.getJSONObject(i);
            // A broad lookup must not silently pick a numbered house.
            if(c.optString("house_number").trim().isEmpty()&&matches(query,c.optString("address")))towns.put(c);
        }
        JSONObject chosen=choose(query,towns);if(coordinates(chosen))chosen.put("state","locality_resolved").put("precision","locality").put("area_label",query);return chosen;
    }
    private static JSONObject find(String query,Lookup lookup,boolean locality)throws Exception {
        try{JSONArray rows=lookup.find(query);return rows==null?new JSONObject().put("state","pending"):locality?chooseLocality(query,rows):choose(query,rows);}
        catch(java.io.InterruptedIOException stopped){throw stopped;}
        catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException();}
        catch(Exception unavailable){return new JSONObject().put("state","unavailable");}
    }
    static JSONObject search(String query,Lookup lookup)throws Exception {
        String locality=localityQuery(query);boolean onlyLocality=!locality.isEmpty()&&normalized(query.split("[,;]",2)[0]).equals(normalized(locality));
        JSONObject exact=find(query,lookup,onlyLocality);if(coordinates(exact)||exact.optString("state").equals("pending"))return exact;
        if(!locality.isEmpty()&&!onlyLocality){JSONObject area=find(locality,lookup,true);if(coordinates(area)||Arrays.asList("pending","ambiguous","unavailable").contains(area.optString("state")))return area;}
        return exact;
    }
    static boolean retryable(String state){return Arrays.asList("unavailable","not_found","ambiguous","pending").contains(state);}
    private WeatherLocationRules(){}
}
