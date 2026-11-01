package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Typed settings; account references grant no permission on a replacement device. */
final class BackupPreferences {
    static final String[] GROUPS={"haus_garten","planning_private","haus_garten_mail"};
    static JSONObject capture(Context context)throws Exception {
        JSONObject groups=new JSONObject();
        for(String name:GROUPS){
            JSONObject values=new JSONObject(),types=new JSONObject();
            for(Map.Entry<String,?> item:context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll().entrySet()){
                String key=item.getKey(),lower=key.toLowerCase(Locale.ROOT);
                if(lower.contains("secret")||lower.contains("token")||lower.contains("password")||lower.contains("credential")||lower.contains("api_key")||lower.equals("weather_iv"))continue;
                Object value=item.getValue();String type;
                if(value instanceof String)type="string";
                else if(value instanceof Boolean)type="boolean";
                else if(value instanceof Integer)type="int";
                else if(value instanceof Long)type="long";
                else if(value instanceof Float){type="float";value=Float.toHexString((Float)value);}
                else if(value instanceof Set){type="string_set";TreeSet<String> sorted=new TreeSet<>();for(Object v:(Set<?>)value){if(!(v instanceof String))throw new IOException("BACKUP_PREFERENCE_TYPE_UNSUPPORTED");sorted.add((String)v);}value=new JSONArray(sorted);}
                else throw new IOException("BACKUP_PREFERENCE_TYPE_UNSUPPORTED");
                values.put(key,value);types.put(key,type);
            }
            groups.put(name,new JSONObject().put("values",values).put("types",types));
        }
        return new JSONObject().put("formatVersion",1).put("groups",groups);
    }
    static JSONObject values(JSONObject envelope,String name)throws Exception {
        if(envelope.getInt("formatVersion")!=1)throw new IOException("BACKUP_PREFERENCES_VERSION_UNSUPPORTED");
        JSONObject group=envelope.getJSONObject("groups").getJSONObject(name),values=group.getJSONObject("values"),types=group.getJSONObject("types");
        if(types.length()!=values.length())throw new IOException("BACKUP_PREFERENCE_TYPES_MISSING");
        for(Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();Object value=values.get(key);String type=types.getString(key);boolean valid;
            switch(type){
                case "string":valid=value instanceof String;break;
                case "boolean":valid=value instanceof Boolean;break;
                case "int":case "long":try{java.math.BigDecimal number=new java.math.BigDecimal(value.toString());if(type.equals("int"))number.intValueExact();else number.longValueExact();valid=value instanceof Number;}catch(ArithmeticException|NumberFormatException e){valid=false;}break;
                case "float":try{Float.valueOf((String)value);valid=true;}catch(ClassCastException|NumberFormatException e){valid=false;}break;
                case "string_set":valid=value instanceof JSONArray;if(valid){Set<String> seen=new HashSet<>();JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++)if(!(list.get(i) instanceof String)||!seen.add(list.getString(i)))valid=false;}break;
                default:valid=false;
            }
            if(!valid)throw new IOException("BACKUP_PREFERENCE_TYPE_INVALID");
        }
        return values;
    }
    private BackupPreferences(){}
}
