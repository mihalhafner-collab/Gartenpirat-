package at.hausgarten.service;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure review projection. Business caches, tokens and old action queues are never imported. */
final class RestoredBrowserReviewRules {
    static final String MIRROR="hausgarten.fullbackup.formdrafts.v1";
    private static IOException invalid(){return new IOException("BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED");}
    static JSONObject validate(JSONObject source)throws Exception {
        if(!(source.opt("formatVersion") instanceof Integer)||source.getInt("formatVersion")!=1||source.optJSONObject("storage")==null||source.toString().getBytes(StandardCharsets.UTF_8).length>16*1024*1024)throw invalid();
        JSONObject storage=source.getJSONObject("storage");for(Iterator<String> keys=storage.keys();keys.hasNext();)if(!(storage.get(keys.next()) instanceof String))throw invalid();
        if(source.has("drafts")){JSONObject draft=source.optJSONObject("drafts");if(draft==null||draft.optInt("formatVersion")!=1)throw invalid();forms(draft);}
        if(storage.has(MIRROR)){JSONObject mirror=new JSONObject(storage.getString(MIRROR));if(mirror.optInt("formatVersion")!=1||mirror.optJSONObject("pages")==null)throw invalid();JSONObject pages=mirror.getJSONObject("pages");for(Iterator<String> keys=pages.keys();keys.hasNext();){JSONObject page=pages.optJSONObject(keys.next());if(page==null)throw invalid();forms(page);}}
        return new JSONObject(source.toString());
    }
    private static void forms(JSONObject page)throws Exception {
        JSONArray forms=page.optJSONArray("forms");if(forms==null||forms.length()>10000)throw invalid();
        if(page.has("page")&&!(page.opt("page") instanceof String))throw invalid();
        for(int i=0;i<forms.length();i++){JSONObject form=forms.getJSONObject(i);JSONArray fields=form.getJSONArray("fields");if(fields.length()>10000)throw invalid();for(int j=0;j<fields.length();j++){JSONObject field=fields.getJSONObject(j);if(!(field.opt("value") instanceof String))throw invalid();for(String name:new String[]{"id","name","type","tag","label"})if(field.has(name)&&!(field.opt(name) instanceof String))throw invalid();if(field.has("checked")&&!(field.opt("checked") instanceof Boolean))throw invalid();if(field.has("selected")){JSONArray selected=field.getJSONArray("selected");for(int k=0;k<selected.length();k++)if(!(selected.get(k) instanceof String))throw invalid();}}}
    }
    static boolean selectable(String key){return Arrays.asList(MIRROR,"hausgarten.entry.draft.v1","hausgarten.offer.editor.v1","hausgarten.offer.decision.editor.v1","hausgarten.planning.draft.v1","hausgarten.planning.draft.v1.others","hausgarten.revier.editor.v1","hausgarten.payment.editor.v1","hausgarten.business.settings.draft").contains(key)||key.matches("hausgarten\\.(?:garden\\.draft|photo\\.edit)\\.[A-Za-z0-9_-]{1,200}");}
    static String label(String key){if(key.equals(MIRROR))return "Zusätzlich erfasste Formularwerte";if(key.startsWith("hausgarten.entry."))return "Arbeitsaufzeichnung";if(key.startsWith("hausgarten.offer.decision."))return "Angebotsentscheidung";if(key.startsWith("hausgarten.offer."))return "Angebot";if(key.startsWith("hausgarten.planning."))return "Terminplanung";if(key.startsWith("hausgarten.revier."))return "Objekt, Anfrage oder Pflege";if(key.startsWith("hausgarten.payment."))return "Zahlungserfassung";if(key.startsWith("hausgarten.business.settings."))return "Firmenprofil";if(key.startsWith("hausgarten.garden."))return "Gartenangaben";if(key.startsWith("hausgarten.photo."))return "Fotobeschreibung";return "Weitere Browserwerte";}
    static JSONObject drafts(JSONObject source)throws Exception {
        JSONObject checked=validate(source),storage=checked.getJSONObject("storage"),result=new JSONObject();for(Iterator<String> keys=storage.keys();keys.hasNext();){String key=keys.next();if(selectable(key))result.put(key,storage.getString(key));}
        JSONObject visible=checked.optJSONObject("drafts");if(visible!=null&&!visible.optString("page").isEmpty()&&visible.getJSONArray("forms").length()>0){JSONObject mirror=result.has(MIRROR)?new JSONObject(result.getString(MIRROR)):new JSONObject().put("formatVersion",1).put("pages",new JSONObject());mirror.getJSONObject("pages").put("restored_visible_forms",new JSONObject().put("page",visible.getString("page")).put("forms",visible.getJSONArray("forms")));result.put(MIRROR,mirror.toString());}
        return result;
    }
    static JSONArray candidates(JSONObject source,String origin)throws Exception {
        JSONObject draft=drafts(source);JSONArray rows=new JSONArray();TreeSet<String> keys=new TreeSet<>();draft.keys().forEachRemaining(keys::add);for(String key:keys)rows.put(new JSONObject().put("source",origin).put("key",key).put("label",label(key)).put("value",draft.getString(key)));return rows;
    }
    static JSONObject selection(JSONObject restored,JSONObject device,JSONArray selected)throws Exception {
        JSONObject backup=drafts(restored),current=drafts(device),result=new JSONObject();if(selected.length()>10000)throw invalid();Set<String> seen=new HashSet<>();
        for(int i=0;i<selected.length();i++){JSONObject choice=selected.getJSONObject(i);String origin=choice.getString("source"),key=choice.getString("key");if(!Arrays.asList("backup","device").contains(origin)||!selectable(key)||!seen.add(key))throw invalid();JSONObject source=origin.equals("backup")?backup:current;if(!source.has(key))throw invalid();result.put(key,source.getString(key));}return result;
    }
    static void validateSelection(JSONObject restored,JSONObject device,JSONObject saved)throws Exception {
        if(!saved.optString("request_id").matches("[A-Za-z0-9_-]{8,200}")||saved.optJSONArray("selected")==null||saved.optJSONObject("selected_storage")==null||saved.optJSONObject("clear_storage")==null)throw invalid();JSONObject expected=selection(restored,device,saved.getJSONArray("selected"));String hash=PixelMcpWriteTools.hash(expected);
        if(!hash.equals(saved.optString("selection_hash"))||!hash.equals(PixelMcpWriteTools.hash(saved.getJSONObject("selected_storage")))||!PixelMcpWriteTools.hash(validate(device).getJSONObject("storage")).equals(PixelMcpWriteTools.hash(saved.getJSONObject("clear_storage"))))throw invalid();
    }
    private RestoredBrowserReviewRules(){}
}
