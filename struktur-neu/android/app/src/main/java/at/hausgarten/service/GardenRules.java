package at.hausgarten.service;
import org.json.*;
/** Optional garden facts reuse existing customer fields and the same revision guard. */
final class GardenRules {
    static final String LAWN="Rasenfläche (m²)",HEDGE="Heckenlänge (m)",WATER="Bewässerung",CARE="Pflegehinweise",ACCESS="Zugangshinweise";
    static void normalize(JSONObject customer)throws Exception{
        if(customer.has("garden_area"))customer.put("garden_area",number(customer.opt("garden_area")));
        Object raw=customer.opt("custom_fields");JSONObject fields=raw instanceof JSONObject?new JSONObject(raw.toString()):raw instanceof String&&!((String)raw).isEmpty()?new JSONObject((String)raw):new JSONObject();
        for(String key:new String[]{LAWN,HEDGE})if(fields.has(key))fields.put(key,number(fields.opt(key)));
        for(String key:new String[]{WATER,CARE,ACCESS})if(fields.has(key)&&fields.opt(key)!=JSONObject.NULL&&(!(fields.opt(key) instanceof String)||fields.getString(key).length()>2000))throw new IllegalArgumentException("Gartenhinweise dürfen höchstens 2000 Zeichen enthalten.");
        if(customer.opt("garden_area") instanceof Number&&fields.opt(LAWN) instanceof Number&&fields.getDouble(LAWN)>customer.getDouble("garden_area"))throw new IllegalArgumentException("Die Rasenfläche darf nicht größer als die gesamte Gartenfläche sein.");
        if(customer.has("custom_fields"))customer.put("custom_fields",fields);
    }
    private static Object number(Object value){if(value==null||value==JSONObject.NULL||value.toString().trim().isEmpty())return JSONObject.NULL;try{double n=Double.parseDouble(value.toString().replace(',','.'));if(!Double.isFinite(n)||n<0||n>1_000_000_000)throw new NumberFormatException();return n;}catch(Exception e){throw new IllegalArgumentException("Bitte eine gültige, nicht negative Gartenfläche oder Heckenlänge eingeben.");}}
}
