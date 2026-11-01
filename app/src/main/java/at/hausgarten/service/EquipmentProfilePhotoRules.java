package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.file.*;

/** Device identity pictures use the existing private photo files, never a customer record. */
final class EquipmentProfilePhotoRules {
    static final int MAX_BYTES=20*1024*1024;
    static void validate(JSONObject photo,String equipmentId)throws Exception {
        String hash=photo.optString("content_sha256"),mime=photo.optString("mime_type");
        String extension=mime.equals("image/jpeg")?".jpg":mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":mime.equals("image/gif")?".gif":mime.equals("image/heic")?".heic":"";
        if(photo.optInt("format_version")!=1||!photo.optString("id").matches("eqp_photo_[0-9a-f]{32}")||!equipmentId.equals(photo.optString("equipment_id"))||!photo.optString("role").equals("profile")||!hash.matches("[0-9a-f]{64}")||extension.isEmpty()||!photo.optString("storage_name").equals(hash+extension)||photo.optLong("byte_size")<1||photo.optLong("byte_size")>MAX_BYTES||photo.optInt("width")<1||photo.optInt("height")<1||photo.optInt("width")>16384||photo.optInt("height")>16384||(long)photo.optInt("width")*photo.optInt("height")>120000000L)
            throw RevierRules.error("EQUIPMENT_PHOTO_INVALID","Das Gerätefoto besitzt keine eindeutige geprüfte Zuordnung.");
        String thumb=photo.optString("thumbnail_storage_name"),thumbHash=photo.optString("thumbnail_sha256");
        if(!thumb.isEmpty()&&(!thumbHash.matches("[0-9a-f]{64}")||!thumb.equals(thumbHash+".jpg")))throw RevierRules.error("EQUIPMENT_PHOTO_INVALID","Die Fotovorschau besitzt keine gültige Referenz.");
    }
    static void upload(JSONObject metadata,byte[] bytes)throws Exception {
        if(!metadata.optString("kind").equals("photo_upload")||metadata.has("attached_photo")||bytes.length<1||bytes.length>MAX_BYTES||bytes.length!=metadata.optLong("byte_size")||!McpFileTransfer.sha(bytes).equals(metadata.optString("sha256")))throw RevierRules.error("EQUIPMENT_PHOTO_UPLOAD_INVALID","Die Fotoauswahl ist unvollständig oder bereits verwendet. Auswahl erneut prüfen.");
    }
    static JSONObject draft(JSONObject current,JSONObject source)throws Exception {
        String session=source.optString("session_id"),selection=source.optString("selection");
        JSONObject graph=current.optJSONObject("record"),origin=graph==null?null:graph.optJSONObject("action_context");
        if(!session.matches("[A-Za-z0-9_.:-]{8,200}")||!selection.matches("[a-f0-9]{32}")||!source.optString("media_key").equals("equipment_"+session)||!current.optString("key").equals("customer-draft:"+source.optString("media_key"))||source.optString("installation_id").isEmpty()||source.optString("restore_epoch").isEmpty()||!current.optString("installation_id").equals(source.optString("installation_id"))||!current.optString("restore_epoch").equals(source.optString("restore_epoch"))||graph==null||origin==null||!source.optString("installation_id").equals(origin.optString("installation_id"))||!source.optString("restore_epoch").equals(origin.optString("restore_epoch"))||graph.optBoolean("_restore_action_review_required")||!session.equals(graph.optString("session_id"))||!selection.equals(graph.optString("selection")))
            throw RevierRules.error("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE","Diese Fotoauswahl ist nicht mehr aktuell. Gerätefoto erneut bewusst auswählen; es wurde nichts angelegt.");
        JSONObject ref=graph.optJSONObject("file");
        if(ref==null)throw RevierRules.error("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE","Der lokale Fotoentwurf ist unvollständig.");
        BrowserMediaFiles.validateReference(ref);
        if(!ref.optString("representation").equals("blob")||ref.optLong("byte_size")<1||ref.optLong("byte_size")>MAX_BYTES)throw RevierRules.error("EQUIPMENT_PHOTO_INVALID","Bitte ein unterstütztes Foto bis 20 MB auswählen.");
        return ref;
    }
    static void draftSession(JSONObject state,JSONObject source,String request)throws Exception {
        JSONObject envelope=state.optBoolean("present")&&!state.optBoolean("review_required")?new JSONObject(state.getString("value")):new JSONObject();
        String session=source.optString("session_id");JSONObject entries=envelope.optJSONObject("entries"),removed=envelope.optJSONObject("removed"),edit=entries==null?null:entries.optJSONObject(session);
        JSONObject context=edit==null?null:edit.optJSONObject("context"),pending=edit==null?null:edit.optJSONObject("pending"),args=pending==null?null:pending.optJSONObject("args");
        if(envelope.optInt("formatVersion")!=1||removed==null||removed.has(session)||edit==null||!edit.optString("kind").equals("equipment")||context==null||!source.optString("media_key").equals(context.optString("media_key"))||!source.optString("selection").equals(context.optString("photo_selection"))||pending==null||!pending.optString("tool").equals("save_equipment")||args==null||args.optJSONObject("profile_photo_draft")==null||!request.equals(args.optString("request_id"))||!PixelMcpWriteTools.hash(source).equals(PixelMcpWriteTools.hash(args.optJSONObject("profile_photo_draft"))))
            throw RevierRules.error("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE","Diese Gerätebearbeitung wurde verworfen oder geändert. Aktuellen Entwurf prüfen; es wurde nichts angelegt.");
    }
    static void attachment(JSONObject photo,JSONObject attachment)throws Exception {
        if(attachment==null)throw RevierRules.error("EQUIPMENT_PHOTO_ORIGINAL_MISSING","Das lokale Gerätefoto fehlt.");
        for(String field:new String[]{"content_sha256","mime_type","byte_size","storage_name","role"})if(!photo.has(field)||!attachment.has(field)||!photo.get(field).toString().equals(attachment.get(field).toString()))throw RevierRules.error("EQUIPMENT_PHOTO_INVALID","Gerätefoto und lokales Original stimmen nicht überein.");
    }
    static void original(File photoDirectory,JSONObject photo)throws Exception {
        if(photoDirectory==null)throw RevierRules.error("EQUIPMENT_PHOTO_ORIGINAL_MISSING","Der geprüfte Fotospeicher fehlt.");
        validate(photo,photo.getString("equipment_id"));File file=new File(photoDirectory,photo.getString("storage_name"));
        if(!file.getCanonicalFile().getParentFile().equals(photoDirectory.getCanonicalFile())||Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()!=photo.getLong("byte_size")||file.length()>MAX_BYTES||!McpFileTransfer.sha(Files.readAllBytes(file.toPath())).equals(photo.getString("content_sha256")))throw RevierRules.error("EQUIPMENT_PHOTO_ORIGINAL_MISSING","Das Gerätefoto fehlt oder wurde verändert. Diese Sicherung ist noch nicht vollständig geprüft.");
    }
    private EquipmentProfilePhotoRules(){}
}
