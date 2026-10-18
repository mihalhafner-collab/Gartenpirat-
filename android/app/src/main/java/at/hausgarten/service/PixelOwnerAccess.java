package at.hausgarten.service;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native boundary. No free paths, URLs, credential reads or operational test hooks. */
final class PixelOwnerAccess implements PixelMcpOwnerTools.Access {
    private final Context context;
    private final MainActivity.LocalStore store;
    PixelOwnerAccess(Context context,MainActivity.LocalStore store){this.context=context;this.store=store;}
    private static String digest(byte[] bytes)throws Exception{return PhotoAttachmentStore.sha256(bytes);}
    private JSONObject state(){return store.getState();}
    private JSONObject effective()throws Exception{return PixelMcpReadTools.effective(state());}
    private JSONObject settings()throws Exception{return new JSONObject(store.ownerValue("settings","{}"));}
    private JSONObject fileMeta(String id)throws Exception{try(Cursor row=store.getReadableDatabase().rawQuery("SELECT metadata FROM mcp_uploads WHERE id=?",new String[]{id})){if(!row.moveToFirst())throw new IllegalArgumentException("Die Dateireferenz fehlt.");return new JSONObject(row.getString(0));}}
    private byte[] fileBytes(String id)throws Exception{
        int size;try(Cursor row=store.getReadableDatabase().rawQuery("SELECT length(data) FROM mcp_uploads WHERE id=?",new String[]{id})){if(!row.moveToFirst())throw new IllegalArgumentException("Dateireferenz fehlt.");size=row.getInt(0);}
        if(size>64*1024*1024)throw new IOException("Datei überschreitet Transfergrenze.");ByteArrayOutputStream out=new ByteArrayOutputStream(size);
        for(int offset=0;offset<size;offset+=PixelMcpOwnerTools.CHUNK)try(Cursor row=store.getReadableDatabase().rawQuery("SELECT substr(data,?,?) FROM mcp_uploads WHERE id=?",new String[]{Integer.toString(offset+1),Integer.toString(Math.min(PixelMcpOwnerTools.CHUNK,size-offset)),id})){if(!row.moveToFirst())throw new IOException("Dateiblock fehlt.");out.write(row.getBlob(0));}
        return out.toByteArray();
    }
    private void putFile(String id,JSONObject meta,byte[] data)throws Exception{ContentValues values=new ContentValues();values.put("id",id);values.put("metadata",meta.toString());values.put("data",data);store.getWritableDatabase().insertWithOnConflict("mcp_uploads",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
    private byte[] asset(String path)throws Exception{try(InputStream in=context.getAssets().open(path)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>20*1024*1024)throw new IOException("Datei zu groß.");out.write(buffer,0,n);}return out.toByteArray();}}
    private JSONArray plants()throws Exception{return new JSONObject(new String(asset("plant-catalog.json"),StandardCharsets.UTF_8)).getJSONArray("plants");}
    private JSONObject entry(String id)throws Exception{return PixelMcpWriteTools.find(plants(),id);}
    @Override public JSONObject query(String name,JSONObject args)throws Exception{
        switch(name){
            case "get_capabilities":{
                JSONObject data=new JSONObject().put("app_version_name",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName).put("app_version_code",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode).put("snapshot_available",!store.snapshotText("").isEmpty()).put("drive_configured",!context.getSharedPreferences("haus_garten",0).getString("drive_tree_uri","").isEmpty()).put("installed_permission_gate",new PixelTunnelConfig(context).writeEnabled());
                data.put("platform_limits",new JSONArray().put("tunnel_account_and_organization_grants").put("android_persisted_document_tree_grant").put("foreground_and_battery_limits").put("central_invoice_numbering_service").put("sharing_requires_device_interaction").put("camera_location_and_os_settings_require_device"));return data;
            }
            case "get_business_settings":{JSONObject s=effective().optJSONObject("business_settings");if(s==null)throw new PixelMcpWriteTools.Rejected("SETTINGS_UNAVAILABLE","Aktualisierten Web-Nummernservice abgleichen, damit vorhandene Einstellungen erhalten bleiben.");return new JSONObject().put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("credentials_exposed",false);}
            case "get_settings":{JSONObject s=settings();return new JSONObject().put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("personal_paid",new JSONArray(store.ownerValue("personal_paid","[]"))).put("credentials_exposed",false);}
            case "list_backups":{JSONArray out=new JSONArray();try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT id,metadata FROM mcp_uploads ORDER BY rowid DESC",null)){while(rows.moveToNext()){JSONObject m=new JSONObject(rows.getString(1));if("backup".equals(m.optString("kind")))out.put(new JSONObject().put("backup_id",rows.getString(0)).put("metadata",m));if(out.length()>=100)break;}}return new JSONObject().put("backups",out);}
            case "inspect_backup":{String id=args.getString("backup_id");JSONObject m=fileMeta(id);if(!"backup".equals(m.optString("kind")))throw new IllegalArgumentException("Keine Sicherung.");JSONObject saved=new JSONObject(new String(fileBytes(id),StandardCharsets.UTF_8));return new JSONObject().put("backup_id",id).put("metadata",m).put("state_hash",PixelMcpWriteTools.hash(state())).put("collections",backupCounts(saved)).put("restore_mode","add_missing_without_overwrite");}
            case "search_catalog":{String q=args.optString("query").toLowerCase(Locale.GERMAN);int skip=args.optInt("offset"),limit=args.optInt("limit",20),total=0;JSONArray out=new JSONArray(),rows=plants();for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(!p.toString().toLowerCase(Locale.GERMAN).contains(q))continue;if(total++>=skip&&out.length()<limit)out.put(p);}return new JSONObject().put("entries",out).put("total",total);}
            case "list_archive":{JSONObject archive=OwnerJournalRules.archives(effective());JSONArray out=new JSONArray();int skip=args.optInt("offset"),limit=args.optInt("limit",20),at=0;for(Iterator<String> keys=archive.keys();keys.hasNext();){String key=keys.next();if(at++<skip||out.length()>=limit)continue;JSONObject saved=archive.getJSONObject(key);out.put(new JSONObject().put("archive_key",key).put("entry",saved).put("record_hash",OwnerJournalRules.version(saved)));}return new JSONObject().put("archive",out).put("total",archive.length());}
            case "get_catalog_entry":return entry(args.getString("entry_id"));
            case "get_file_metadata":case "read_file_chunk":return readChunk(args);
            default:throw new IllegalArgumentException("Lesefunktion fehlt.");
        }
    }
    private JSONObject readChunk(JSONObject a)throws Exception{
        String type=a.getString("resource_type"),id=a.getString("resource_id"),mime;byte[] bytes;
        switch(type){
            case "photo":{
                PixelMcpWriteTools.find(effective().getJSONArray("photos"),id);JSONObject m=store.photoAttachment(id);if(m==null)throw new IOException("Original fehlt lokal. Zuerst abgleichen.");
                // Explicit read may retrieve the referenced, hash-verified original from Drive.
                try{bytes=store.photoAttachmentStore().read(m);}catch(IOException absent){if(!DriveSyncCoordinator.fetchPhoto(context,m,store.photoAttachmentStore()))throw absent;bytes=store.photoAttachmentStore().read(m);}mime=m.getString("mime_type");break;
            }
            case "invoice":{JSONObject invoice=PixelMcpWriteTools.find(effective().getJSONArray("invoices"),id);String data=invoice.optString("pdf_base64");if(data.isEmpty())throw new IOException("Original-PDF noch nicht verfügbar. Nummernservice und Drive-Abgleich prüfen.");if(data.length()>28*1024*1024)throw new IOException("PDF zu groß.");bytes=Base64.getDecoder().decode(data);mime="application/pdf";if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("Original-PDF beschädigt.");if(!invoice.optString("pdf_sha256").isEmpty()&&!digest(bytes).equals(invoice.getString("pdf_sha256")))throw new IOException("Original-PDF stimmt nicht mit dem Archiv überein.");break;}
            case "catalog":{
                // Only the exact asset paths named by an existing catalog record are addressable.
                String[] parts=id.split("#",2);JSONObject p=entry(parts[0]);String path=parts.length==2?parts[1]:"";boolean listed=false;JSONArray images=p.optJSONArray("images");for(int i=0;images!=null&&i<images.length();i++)if(path.equals(images.getJSONObject(i).optString("path")))listed=true;
                if(!listed||path.isEmpty()||path.contains("..")||path.startsWith("/")||!p.toString().contains(JSONObject.quote(path))||!path.matches("[A-Za-z0-9_./-]+\\.(jpg|jpeg|png|webp)"))throw new IllegalArgumentException("Katalogbild nicht freigegeben.");bytes=asset(path);mime=path.endsWith("png")?"image/png":path.endsWith("webp")?"image/webp":"image/jpeg";break;
            }
            default:{JSONObject m=fileMeta(id);if(!"backup".equals(m.optString("kind")))throw new IllegalArgumentException("Keine Sicherung.");bytes=fileBytes(id);mime="application/json";}
        }
        String hash=digest(bytes);if(!a.has("expected_sha256"))return new JSONObject().put("resource_type",type).put("resource_id",id).put("mime_type",mime).put("byte_size",bytes.length).put("sha256",hash);if(!hash.equals(a.getString("expected_sha256")))throw new PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT","Prüfsumme des Originals stimmt nicht mit dem angeforderten Stand überein.");
        int offset=a.getInt("offset"),length=a.optInt("length",PixelMcpOwnerTools.CHUNK);if(offset>bytes.length)throw new IllegalArgumentException("Dateibereich fehlt.");byte[] part=Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+length));
        return new JSONObject().put("resource_type",type).put("resource_id",id).put("mime_type",mime).put("byte_size",bytes.length).put("sha256",hash).put("offset",offset).put("next_offset",offset+part.length).put("eof",offset+part.length==bytes.length).put("chunk_sha256",digest(part)).put("chunk_base64",Base64.getEncoder().encodeToString(part));
    }
    JSONObject execute(String name,JSONObject args)throws Exception{
        PixelMcpOwnerTools.validate(name,args);
        String request="mcp_owner_"+PixelMcpWriteTools.hash(args.getString("request_id")),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("tool",name).put("args",args));
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();JSONObject result;
        try{
            String old=store.ownerValue("receipt:"+request,"");if(!old.isEmpty()){JSONObject receipt=new JSONObject(old);if(!fingerprint.equals(receipt.getString("fingerprint")))throw new PixelMcpWriteTools.Rejected("REPLAY_MISMATCH","Die Anfragekennung gehört zu anderen Angaben.");result=receipt.getJSONObject("result").put("replayed",true);}
            else{result=perform(name,args,request);store.putOwnerValue("receipt:"+request,new JSONObject().put("fingerprint",fingerprint).put("result",result).toString());}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(!Arrays.asList("begin_file_upload","write_file_chunk","create_backup","set_personal_paid","update_settings","prepare_invoice_share").contains(name))DriveSyncCoordinator.request(context,1000);
        return new JSONObject().put("ok",true).put("data",result).put("cloud_confirmed",false);
    }
    private JSONObject queued(String kind,JSONObject payload,String request)throws Exception{
        ContentValues a=new ContentValues();a.put("id",request);a.put("kind",kind);a.put("payload",payload.toString());a.put("created_at",java.time.Instant.now().toString());store.getWritableDatabase().insertOrThrow("pending",null,a);
        return new JSONObject().put("client_id",request).put("entity_type",payload.optString("entity",kind)).put("entity_id",payload.optString("id")).put("local_state","local_saved").put("sync_state","sync_pending").put("cloud_confirmed",false);
    }
    private JSONObject perform(String name,JSONObject a,String request)throws Exception{
        JSONObject all=effective();String stamp=java.time.Instant.now().toString();
        switch(name){
            case "request_sync":return new JSONObject().put("state","requested").put("cloud_confirmed",false);
            case "begin_file_upload":{
                String id="upload_"+PixelMcpWriteTools.hash(request);JSONObject m=new JSONObject(a.toString()).put("kind","photo_upload");m.remove("request_id");putFile(id,m,new byte[0]);return new JSONObject().put("upload_id",id).put("next_offset",0).put("max_chunk_bytes",PixelMcpOwnerTools.CHUNK);
            }
            case "write_file_chunk":{
                String id=a.getString("upload_id");JSONObject m=fileMeta(id);if(!"photo_upload".equals(m.optString("kind"))||m.has("attached_photo"))throw new IllegalArgumentException("Upload ist nicht beschreibbar.");
                byte[] previous=fileBytes(id),combined=McpFileTransfer.append(previous,a.getString("chunk_base64"),a.getInt("offset"),m.getInt("byte_size"),a.getString("chunk_sha256"));putFile(id,m,combined);return new JSONObject().put("upload_id",id).put("next_offset",combined.length).put("complete",combined.length==m.getInt("byte_size"));
            }
            case "create_photo":{
                String upload=a.getString("upload_id");JSONObject m=fileMeta(upload);byte[] bytes=fileBytes(upload);
                if(m.has("attached_photo")||bytes.length!=m.getInt("byte_size")||!digest(bytes).equals(m.getString("sha256")))throw new IllegalArgumentException("Upload unvollständig, verändert oder bereits verwendet.");
                JSONObject p=new JSONObject(a.toString());p.remove("request_id");p.remove("upload_id");p.put("id","pho_mcp_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("data","data:"+m.getString("mime_type")+";base64,"+Base64.getEncoder().encodeToString(bytes));String id=store.savePhoto(p);m.put("attached_photo",id);putFile(upload,m,bytes);return new JSONObject().put("photo_id",id).put("local_state","local_saved").put("sync_state","sync_pending");
            }
            case "set_work_record_billed":case "reassign_work_record":{
                JSONObject old=PixelMcpWriteTools.find(all.getJSONArray("worklogs"),a.getString("work_record_id"));if(!PixelMcpWriteTools.hash(PixelMcpWriteTools.base(old)).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Arbeitsaufzeichnung wurde verändert.");
                JSONObject p=DriveJournalRules.clean(old);if(name.equals("set_work_record_billed"))p.put("billed",a.getBoolean("billed")?1:0);else{p.put("customer_id",a.getString("customer_id")).put("billing_customer_id",a.getString("billing_customer_id")).put("order_id",a.optString("order_id"));}
                Set<String> prior=new HashSet<>();JSONArray before=store.pendingActions();for(int i=0;i<before.length();i++)prior.add(before.getJSONObject(i).getString("client_id"));store.saveWorklog(p);
                JSONArray after=store.pendingActions();for(int i=0;i<after.length();i++){JSONObject added=after.getJSONObject(i);if(!prior.contains(added.getString("client_id"))&&"worklog".equals(added.getString("kind"))&&p.getString("id").equals(added.getJSONObject("payload").optString("id")))store.getWritableDatabase().delete("pending","id=?",new String[]{added.getString("client_id")});}
                p.put("_owner_reason",a.getString("reason"));return queued("worklog",p,request);
            }
            case "update_photo":{
                if(all.optJSONObject("capabilities")==null||!all.getJSONObject("capabilities").optBoolean("photo_compare_and_save"))throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Zuerst die gemeinsame Web-/Drive-Verarbeitung aktualisieren und abgleichen. Fotovergleichsschutz fehlt.");
                JSONObject old=PixelMcpWriteTools.find(all.getJSONArray("photos"),a.getString("photo_id"));if(!PixelMcpWriteTools.hash(PixelMcpOwnerTools.photoHash(old)).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Foto wurde inzwischen geändert.");JSONObject p=DriveJournalRules.clean(old),updates=a.getJSONObject("updates");for(Iterator<String> keys=updates.keys();keys.hasNext();){String key=keys.next();p.put(key,updates.get(key));}EntityProtectionRules.validatePhoto(p,all.getJSONArray("customers"),all.getJSONArray("orders"),all.getJSONArray("worklogs"));ManualChangeRules.prepare("photo",p,old,stamp);return queued("photo",p,request);
            }
            case "archive_entity":case "restore_entity":{
                String entity=a.getString("entity"),id=a.getString("entity_id");JSONObject p=new JSONObject(a.toString()).put("id",id).put("updated_at",stamp);p.remove("request_id");p.remove("entity_id");JSONObject current=OwnerJournalRules.findOrNull(all.getJSONArray(OwnerJournalRules.group(entity)),id);if(name.equals("archive_entity"))p.put("_record_base",DriveJournalRules.guard(current,entity)).put("_archive_record",current);else p.put("_archive_entry",OwnerJournalRules.archives(all).getJSONObject(entity+":"+id));JSONObject event=new JSONObject().put("client_id",request).put("kind",name.equals("archive_entity")?"archive":"restore").put("payload",p).put("created_at",stamp);OwnerJournalRules.apply(new JSONObject(all.toString()),event,new HashMap<>(),new JSONObject());return queued(event.getString("kind"),p,request);
            }
            case "resolve_sync_conflict":{
                JSONObject conflicts=PixelMcpOwnerTools.conflicts(state(),new JSONObject().put("limit",1).put("client_id",a.getString("client_id")));JSONObject found=null;JSONArray list=conflicts.getJSONArray("conflicts");for(int i=0;i<list.length();i++)if(a.getString("client_id").equals(list.getJSONObject(i).optString("client_id")))found=list.getJSONObject(i);
                if(found!=null&&!found.optBoolean("resolution_available",true))throw new PixelMcpWriteTools.Rejected("LEGACY_PHOTO_REVIEW_REQUIRED","Älteres Fotojournal benötigt Prüfung des Originals. Keine Aktion verworfen oder verändert.");if(found==null||!found.has("action"))throw new IllegalArgumentException("Konfliktaktion nicht vollständig verfügbar; nichts geändert.");JSONObject p=new JSONObject(a.toString());p.remove("request_id");JSONObject original=found.getJSONObject("action");p.put("_original_action",original);JSONObject current=found.optJSONObject("current");p.put("_current_base",current==null?JSONObject.NULL:DriveJournalRules.guard(current,original.getString("kind")));Map<String,JSONObject> actions=new HashMap<>();actions.put(a.getString("client_id"),found.getJSONObject("action"));OwnerJournalRules.apply(new JSONObject(state().getJSONObject("master").toString()),new JSONObject().put("kind","conflict_resolution").put("client_id",request).put("payload",p),actions,new JSONObject());return queued("conflict_resolution",p,request);
            }
            case "update_business_settings":{JSONObject p=new JSONObject(a.toString());p.remove("request_id");OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind","business_settings").put("payload",p),new HashMap<>(),new JSONObject());return queued("business_settings",p,request);}
            case "update_settings":{JSONObject s=settings();if(!PixelMcpWriteTools.hash(s).equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Einstellungen wurden geändert.");JSONObject u=a.getJSONObject("updates");for(Iterator<String> keys=u.keys();keys.hasNext();){String key=keys.next();s.put(key,u.get(key));}store.putOwnerValue("settings",s.toString());return new JSONObject().put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("scope","device_only");}
            case "set_personal_paid":{if(!"1".equals(store.ownerValue("personal_paid_migrated","")))throw new PixelMcpWriteTools.Rejected("DEVICE_MIGRATION_REQUIRED","App einmal öffnen, damit vorhandene persönliche Markierungen verlustfrei übernommen werden.");String id=a.getString("work_record_id");PixelMcpWriteTools.find(all.getJSONArray("worklogs"),id);Set<String> ids=new LinkedHashSet<>();JSONArray old=new JSONArray(store.ownerValue("personal_paid","[]"));for(int i=0;i<old.length();i++)ids.add(old.getString(i));if(a.getBoolean("paid"))ids.add(id);else ids.remove(id);store.putOwnerValue("personal_paid",new JSONArray(ids).toString());return new JSONObject().put("work_record_id",id).put("paid",a.getBoolean("paid")).put("scope","device_only");}
            case "create_backup":return createBackup(request);
            case "restore_backup":return restoreBackup(a,request);
            case "request_invoice_issue":case "create_invoice_correction":case "update_invoice_payment":{
                JSONObject invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),a.getString("invoice_id"));String expected=name.equals("update_invoice_payment")?OwnerJournalRules.version(invoice):PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice",invoice));if(!expected.equals(a.getString("expected_record_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Rechnung wurde geändert.");
                JSONObject p=new JSONObject(a.toString()).put("id",invoice.getString("id")).put("updated_at",stamp);p.remove("request_id");p.remove("invoice_id");p.put("_record_base",DriveJournalRules.guard(invoice,"invoice"));
                if(name.equals("request_invoice_issue")){
                    InvoiceDraftRules.normalize(new JSONObject(invoice.toString()),invoice,all);for(int i=0;i<state().getJSONArray("pending").length();i++){JSONObject old=state().getJSONArray("pending").getJSONObject(i);if(Arrays.asList("invoice_issue","owner_invoice_issue").contains(old.optString("kind"))&&invoice.getString("id").equals(old.getJSONObject("payload").optString("id")))throw new IllegalArgumentException("Abschluss bereits vorgemerkt.");}p.put("_expected_invoice",DriveJournalRules.guard(invoice,"invoice"));JSONObject works=new JSONObject();JSONArray links=invoice.optJSONArray("worklog_ids");for(int j=0;links!=null&&j<links.length();j++){String linked=links.getString(j);works.put(linked,DriveJournalRules.guard(PixelMcpWriteTools.find(all.getJSONArray("worklogs"),linked),"worklog"));}p.put("_expected_worklogs",works);return queued("owner_invoice_issue",p,request).put("finalization","central_numbering_service_pending");
                }
                if(name.equals("create_invoice_correction")){if(invoice.optString("issued_at").isEmpty())throw new IllegalArgumentException("Korrekturen benötigen einen ausgestellten Originalbeleg.");p.put("_expected_invoice",DriveJournalRules.guard(invoice,"invoice"));return queued("invoice_correction",p,request).put("correction_state","draft_pending").put("requires_issue_confirmation",true);}
                OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind","invoice_payment").put("payload",p),new HashMap<>(),new JSONObject());return queued("invoice_payment",p,request);
            }
            case "prepare_invoice_share":{
                JSONObject inv=PixelMcpWriteTools.find(all.getJSONArray("invoices"),a.getString("invoice_id"));if(inv.optString("issued_at").isEmpty()||inv.optString("pdf_base64").isEmpty())throw new IllegalArgumentException("Original-PDF einer ausgestellten Rechnung fehlt.");JSONObject handoff=new JSONObject().put("invoice_id",inv.getString("id")).put("channel",a.getString("channel"));store.putOwnerValue("invoice_share_handoff",handoff.toString());return handoff.put("state","device_interaction_required").put("sent",false).put("delivered",false);
            }
            default:throw new IllegalArgumentException("Eigentümerfunktion fehlt.");
        }
    }
    private JSONObject backupCounts(JSONObject saved)throws Exception{JSONObject out=new JSONObject(),master=saved.getJSONObject("state").getJSONObject("master");for(String key:new String[]{"customers","orders","worklogs","invoices","photos"})out.put(key,master.getJSONArray(key).length());return out;}
    private JSONObject createBackup(String request)throws Exception{
        JSONObject saved=new JSONObject().put("format",1).put("state",state()).put("settings",settings()).put("personal_paid",new JSONArray(store.ownerValue("personal_paid","[]")));JSONArray originals=new JSONArray();
        try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT photo_id FROM photo_attachments ORDER BY photo_id",null)){while(rows.moveToNext()){String id=rows.getString(0);JSONObject m=store.photoAttachment(id);byte[] bytes;try{bytes=store.photoAttachmentStore().read(m);}catch(Exception missing){throw new IOException("Sicherung nicht vollständig: ein Foto-Original fehlt lokal. Zuerst vollständig laden.");}originals.put(new JSONObject().put("photo_id",id).put("metadata",m).put("base64",Base64.getEncoder().encodeToString(bytes)));}}
        saved.put("originals",originals);
        // A backup is an explicit private transfer artifact, never a business snapshot or log.
        byte[] bytes=saved.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>64*1024*1024)throw new IOException("Sicherung überschreitet die sichere Transfergrenze von 64 MiB. Vollständige Geräte-/Dateisicherung verwenden; kein Teilerfolg.");
        String id="backup_"+PixelMcpWriteTools.hash(request);JSONObject meta=new JSONObject().put("kind","backup").put("byte_size",bytes.length).put("sha256",digest(bytes)).put("created_at",java.time.Instant.now().toString()).put("includes_credentials",false).put("scope","business_snapshot_pending_and_local_photos");putFile(id,meta,bytes);return new JSONObject().put("backup_id",id).put("metadata",meta).put("collections",backupCounts(saved)).put("complete_device_backup",false);
    }
    private JSONObject restoreBackup(JSONObject a,String request)throws Exception{
        if(!PixelMcpWriteTools.hash(state()).equals(a.getString("expected_state_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","App-Datenstand wurde seit Prüfung geändert.");
        if(state().getJSONArray("pending").length()!=0||!"IDLE".equals(state().getJSONObject("work_session").optString("state")))throw new IllegalArgumentException("Ausstehende Aktionen oder Zeiterfassung zuerst prüfen. Keine Daten ersetzt.");
        JSONObject meta=fileMeta(a.getString("backup_id"));byte[] bytes=fileBytes(a.getString("backup_id"));if(!"backup".equals(meta.optString("kind"))||!digest(bytes).equals(meta.getString("sha256")))throw new IllegalArgumentException("Sicherung beschädigt.");
        JSONObject backup=new JSONObject(new String(bytes,StandardCharsets.UTF_8)),old=backup.getJSONObject("state");if(old.getJSONArray("pending").length()!=0||!"IDLE".equals(old.getJSONObject("work_session").optString("state")))throw new IllegalArgumentException("Sicherung enthält offene Aktionen oder Zeiterfassung. Nur isoliert prüfen; keine automatische Übernahme.");
        JSONObject target=effective(),source=old.getJSONObject("master");DriveJournalRules.validateSnapshot(source);
        // First verify every existing ID. A single conflict aborts the whole transaction.
        for(String key:new String[]{"customers","orders","worklogs","invoices","photos"})for(int i=0;i<source.getJSONArray(key).length();i++){JSONObject row=source.getJSONArray(key).getJSONObject(i),existing=OwnerJournalRules.findOrNull(target.getJSONArray(key),row.getString("id"));if(existing!=null&&!OwnerJournalRules.version(existing).equals(OwnerJournalRules.version(row)))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Sicherung und aktuelle Daten enthalten verschiedene Versionen. Keine Überschreibung.");}
        int count=0;for(String key:new String[]{"customers","orders","worklogs","invoices","photos"}){JSONArray rows=source.getJSONArray(key);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(OwnerJournalRules.findOrNull(target.getJSONArray(key),row.getString("id"))==null){String entity=key.equals("customers")?"customer":key.equals("orders")?"order":key.equals("worklogs")?"worklog":key.equals("invoices")?"invoice":"photo";if(OwnerJournalRules.archives(target).has(entity+":"+row.getString("id")))throw new IllegalArgumentException("Archiveintrag zuerst ausdrücklich prüfen und wiederherstellen.");if(key.equals("invoices")&&!row.optString("issued_at").isEmpty())throw new IllegalArgumentException("Ausgestellte Rechnungen benötigen vollständige Archivwiederherstellung; nichts übernommen.");String kind=key.equals("customers")?"customer":key.equals("orders")?"order":key.equals("worklogs")?"worklog":key.equals("invoices")?"invoice":"photo";JSONObject p=DriveJournalRules.clean(row);p.put("_mcp_create",true);DriveJournalRules.validateChange(kind,p,null,target);target.getJSONArray(key).put(row);queued(kind,p,request+"_"+count++);}}}
        // Media must already be verified locally; never restore dangling references.
        JSONArray originals=backup.getJSONArray("originals");for(int i=0;i<originals.length();i++){JSONObject photo=originals.getJSONObject(i);JSONObject m=photo.getJSONObject("metadata");store.photoAttachmentStore().storeExpected(Base64.getDecoder().decode(photo.getString("base64")),m);store.restoreAttachmentMetadata(photo.getString("photo_id"),m);}
        return new JSONObject().put("added_records",count).put("overwritten_records",0).put("sync_state","sync_pending");
    }
}
