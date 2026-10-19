package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Strict validation at the domain boundary, independent of connector validation. */
final class McpSchema {
    static void validate(Object value, JSONObject spec) throws Exception {
        Object declared=spec.opt("type");boolean accepted=false;
        JSONArray types=declared instanceof JSONArray?(JSONArray)declared:new JSONArray().put(declared);
        for(int i=0;i<types.length();i++){
            String t=types.optString(i);accepted|=t.equals("null")&&value==JSONObject.NULL||t.equals("object")&&value instanceof JSONObject||t.equals("array")&&value instanceof JSONArray||t.equals("string")&&value instanceof String||t.equals("boolean")&&value instanceof Boolean||t.equals("number")&&value instanceof Number||t.equals("integer")&&value instanceof Number&&Double.isFinite(((Number)value).doubleValue())&&((Number)value).doubleValue()==Math.rint(((Number)value).doubleValue());
        }
        if(!accepted)reject();
        if(value instanceof Number){double n=((Number)value).doubleValue();if(spec.has("exclusiveMinimum")&&n<=spec.getDouble("exclusiveMinimum"))reject();if(spec.has("exclusiveMaximum")&&n>=spec.getDouble("exclusiveMaximum"))reject();if(!Double.isFinite(n)||n<spec.optDouble("minimum",-Double.MAX_VALUE)||n>spec.optDouble("maximum",Double.MAX_VALUE))reject();}
        if(value instanceof String){String s=(String)value;if(s.length()<spec.optInt("minLength",0)||s.length()>spec.optInt("maxLength",10000)||spec.has("pattern")&&!s.matches(spec.getString("pattern")))reject();}
        JSONArray choices=spec.optJSONArray("enum");if(choices!=null){boolean found=false;for(int i=0;i<choices.length();i++)if(choices.get(i).equals(value))found=true;if(!found)reject();}
        if(value instanceof JSONObject){
            JSONObject obj=(JSONObject)value,props=spec.optJSONObject("properties");JSONArray required=spec.optJSONArray("required");
            if(obj.length()<spec.optInt("minProperties",0)||obj.length()>spec.optInt("maxProperties",1000))reject();
            for(int i=0;required!=null&&i<required.length();i++)if(!obj.has(required.getString(i)))reject();
            for(Iterator<String> keys=obj.keys();keys.hasNext();){String key=keys.next();JSONObject child=props==null?null:props.optJSONObject(key);if(child!=null)validate(obj.get(key),child);else if(spec.opt("additionalProperties") instanceof JSONObject)validate(obj.get(key),spec.getJSONObject("additionalProperties"));else if(Boolean.FALSE.equals(spec.opt("additionalProperties")))reject();}
        }
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;if(a.length()<spec.optInt("minItems",0)||a.length()>spec.optInt("maxItems",1000))reject();if(spec.has("items"))for(int i=0;i<a.length();i++)validate(a.get(i),spec.getJSONObject("items"));}
    }
    private static void reject() throws Exception {throw new PixelMcpWriteTools.Rejected("INVALID_INPUT","Eingaben entsprechen nicht dem erlaubten Schema. Pflichtangaben, Typen und Grenzen prüfen.");}
}
