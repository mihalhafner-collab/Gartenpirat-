package at.hausgarten.service;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native capability/query adapter. No SQL or business mutations. */
final class NativeOwnerQueries implements PixelMcpOwnerTools.Access {
    private final Context context;
    private final PixelLocalStore store;
    private final LocalOwnerRepository repository;
    NativeOwnerQueries(Context context,PixelLocalStore store,LocalOwnerRepository repository){this.context=context;this.store=store;this.repository=repository;}
    @Override public JSONObject query(String name,JSONObject args)throws Exception{
        if(AppointmentTools.read(name))return AppointmentRuntime.query(context,store,name,args);
        switch(name){
            case "run_self_test":return selfTest();
            case "get_ui_action_catalog":{JSONObject catalog=new JSONObject(new String(repository.asset("ui-mcp-actions.json"),StandardCharsets.UTF_8));String hash=PixelMcpWriteTools.hash(catalog);if(args.has("expected_catalog_hash")&&!hash.equals(args.getString("expected_catalog_hash")))throw new PixelMcpWriteTools.Rejected("CATALOG_CHANGED","Katalog geändert; ab erster Seite neu lesen.");JSONArray all=catalog.getJSONArray("actions"),page=new JSONArray();for(int i=args.optInt("offset");i<all.length()&&page.length()<args.optInt("limit",50);i++)page.put(all.get(i));return new JSONObject().put("actions",page).put("total",all.length()).put("catalog_hash",hash).put("catalog_semantic",CatalogSemanticHash.describe(catalog)).put("offset",args.optInt("offset")).put("limit",args.optInt("limit",50)).put("next_cursor",args.optInt("offset")+page.length()<all.length()?args.optInt("offset")+page.length():JSONObject.NULL).put("scope",catalog.getString("scope"));}
            case "inspect_order_review":return OrderReviewRules.inspect(repository.state(),args.getString("order_id"));
            case "preview_empty_trash":return TrashPurgeBatch.preview(repository.effective());
            case "preview_archive_work_records":return WorklogArchiveBatch.preview(repository.effective(),args.getJSONArray("work_record_ids"));
            case "get_capabilities":{
                JSONObject data=new JSONObject().put("build_fingerprint",BuildProvenance.describe()).put("mail_readback_configured",!context.getSharedPreferences("haus_garten_mail",0).getString("account","").isEmpty()).put("app_version_name",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName).put("package_name",context.getPackageName()).put("database_version",4).put("app_version_code",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode).put("snapshot_available",!store.snapshotText("").isEmpty()).put("drive_configured",!context.getSharedPreferences("haus_garten",0).getString("drive_tree_uri","").isEmpty()).put("installed_permission_gate",new PixelTunnelConfig(context).writeEnabled());
                JSONObject current=repository.effective(),caps=current.optJSONObject("capabilities");data.put("processing_engine",PixelDriveState.ENGINE).put("primary_device_id",current.optString("drive_publisher_id")).put("capabilities",caps).put("offers_sync_ready",caps!=null&&caps.optBoolean("offers_v1")).put("service_catalog_sync_ready",caps!=null&&caps.optBoolean("service_catalog_v1"));
                data.put("planning",AppointmentRuntime.integration(context,store)).put("drive_configured",DriveAccount.available(context)).put("drive_transport",DriveAccount.configured(context)?"direct_drive":"android_provider").put("platform_limits",new JSONArray().put("tunnel_account_and_organization_grants").put("google_android_oauth_registration_and_device_consent").put("android_persisted_document_tree_grant").put("foreground_and_battery_limits").put("verified_pixel_drive_numbering_required").put("sharing_requires_device_interaction").put("camera_location_and_os_settings_require_device"));return data;
            }
            case "preview_purge":{JSONObject all=repository.effective(),entry=OwnerJournalRules.archives(all).optJSONObject(args.getString("entity")+":"+args.getString("entity_id"));if(entry==null||entry.optBoolean("purged"))throw new IllegalArgumentException("Eintrag nicht im Papierkorb.");JSONObject out=new JSONObject().put("expected_record_hash",OwnerJournalRules.version(entry)).put("allowed",true);try{TrashRules.purge(new JSONObject(all.toString()),new JSONObject().put("kind","purge_v1").put("client_id","read-only-preview").put("created_at",java.time.Instant.now().toString()).put("payload",new JSONObject().put("entity",args.getString("entity")).put("id",args.getString("entity_id")).put("confirmed",true).put("expected_record_hash",OwnerJournalRules.version(entry))));}catch(IllegalArgumentException e){out.put("allowed",false).put("message",e.getMessage());}return out;}
            case "preview_delete":return DeletionRules.preview(repository.effective(),args.getString("entity"),args.getString("entity_id"));
            case "get_business_settings":{JSONObject s=repository.effective().optJSONObject("business_settings");if(s==null)throw new IllegalArgumentException("Pixel-Einstellungsstand fehlt.");return new JSONObject().put("initialization_required",s.length()==0).put("settings_origin",repository.effective().optString("business_settings_origin","existing_snapshot")).put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("credentials_exposed",false);}
            case "get_settings":{JSONObject s=repository.settings();return new JSONObject().put("photo_storage",repository.photoStorageSummary()).put("settings",s).put("record_hash",PixelMcpWriteTools.hash(s)).put("personal_paid",new JSONArray(store.ownerValue("personal_paid","[]"))).put("credentials_exposed",false);}
            case "list_backups":return repository.listBackups();
            case "inspect_backup":{String id=args.getString("backup_id");JSONObject m=repository.fileMeta(id);if(!"backup".equals(m.optString("kind")))throw new IllegalArgumentException("Keine Sicherung.");if(DeletionBackupStore.SCOPE.equals(m.optString("scope")))return repository.inspectDeletionBackup(id,m);JSONObject saved=new JSONObject(new String(repository.fileBytes(id),StandardCharsets.UTF_8));return new JSONObject().put("backup_id",id).put("metadata",m).put("state_hash",PixelMcpWriteTools.hash(repository.state())).put("collections",repository.backupCounts(saved)).put("restore_mode","add_missing_without_overwrite");}
            case "search_catalog":{if("service".equals(args.optString("catalog_type"))){JSONObject filters=new JSONObject(args.toString());filters.remove("catalog_type");return OfferTools.query("search_offer_templates",filters,repository.state());}String q=args.optString("query").toLowerCase(Locale.GERMAN);int skip=args.optInt("offset"),limit=args.optInt("limit",20),total=0;JSONArray out=new JSONArray(),rows=repository.plants();for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(!p.toString().toLowerCase(Locale.GERMAN).contains(q))continue;if(total++>=skip&&out.length()<limit)out.put(p);}return new JSONObject().put("entries",out).put("total",total);}
            case "list_archive":{
                JSONObject archive=OwnerJournalRules.archives(repository.effective());List<String> keys=new ArrayList<>();for(Iterator<String> it=archive.keys();it.hasNext();){String key=it.next();if(archive.getJSONObject(key).optBoolean("purged"))continue;int split=key.indexOf(':');if(args.has("entity")&&!key.substring(0,split).equals(args.getString("entity")))continue;if(args.has("entity_id")&&!key.substring(split+1).equals(args.getString("entity_id")))continue;keys.add(key);}
                keys.sort((x,y)->{int date=archive.optJSONObject(y).optString("archived_at").compareTo(archive.optJSONObject(x).optString("archived_at"));return date==0?x.compareTo(y):date;});JSONArray out=new JSONArray();int skip=args.optInt("offset"),limit=args.optInt("limit",20);for(int i=skip;i<keys.size()&&out.length()<limit;i++){String key=keys.get(i);JSONObject saved=archive.getJSONObject(key);out.put(new JSONObject().put("archive_key",key).put("entry",saved).put("record_hash",OwnerJournalRules.version(saved)));}return new JSONObject().put("archive",out).put("total",keys.size());
            }
            case "get_catalog_entry":if(args.getString("entry_id").startsWith("svc_")){JSONObject e=PixelMcpWriteTools.find(repository.effective().getJSONArray("service_catalog"),args.getString("entry_id"));return new JSONObject().put("entry",e).put("record_hash",PixelMcpWriteTools.hash(e));}return repository.entry(args.getString("entry_id"));
            case "get_file_metadata":case "read_file_chunk":return repository.readChunk(args);
            default:throw new IllegalArgumentException("Lesefunktion fehlt.");
        }
    }
    private JSONObject selfTest()throws Exception {
        JSONArray checks=new JSONArray();JSONObject all=repository.effective(),settings=all.optJSONObject("business_settings"),caps=all.optJSONObject("capabilities");
        PixelMcpReadTools tools=new PixelMcpReadTools(()->store.getState(),new PixelMcpWriteTools.Sink(){public boolean writable(){return new PixelTunnelConfig(context).writeEnabled();}public JSONObject execute(String n,JSONObject a)throws Exception{return store.execute(n,a);}public JSONObject status(String id,JSONObject state)throws Exception{return store.status(id,state);}},this);JSONArray catalogue=tools.tools();Set<String> names=new HashSet<>();for(int i=0;i<catalogue.length();i++)names.add(catalogue.getJSONObject(i).getString("name"));
        checks.put(new JSONObject().put("name","build_and_protocol").put("status","PASS"));checks.put(new JSONObject().put("name","catalog_unique").put("status",names.size()==catalogue.length()?"PASS":"FAIL"));
        checks.put(new JSONObject().put("name","write_gate").put("status",new PixelTunnelConfig(context).writeEnabled()?"PASS":"NOT TESTABLE").put("write_enabled",new PixelTunnelConfig(context).writeEnabled()));
        boolean complete=settings!=null;for(String key:OfferContract.values("issuer_required_fields"))complete&=settings!=null&&!settings.optString(key).isEmpty();checks.put(new JSONObject().put("name","business_settings_complete").put("status",complete?"PASS":"FAIL"));
        checks.put(new JSONObject().put("name","drive_configuration").put("status",DriveAccount.available(context)?"PASS":"NOT TESTABLE"));checks.put(new JSONObject().put("name","offers_and_catalog").put("status",caps!=null&&caps.optBoolean("offers_v1")&&caps.optBoolean("service_catalog_v1")?"PASS":"FAIL"));
        JSONObject inventory=new JSONObject(new String(repository.asset("ui-mcp-actions.json"),StandardCharsets.UTF_8));JSONArray actions=inventory.getJSONArray("actions");boolean parity=true;for(int i=0;i<actions.length();i++){JSONObject action=actions.getJSONObject(i);JSONArray path=action.getJSONArray("mcp");if(action.getString("kind").equals("business")){parity&=path.length()>0;for(int j=0;j<path.length();j++)parity&=names.contains(path.getString(j));}else parity&=!action.optString("barrier").isEmpty();}
        checks.put(new JSONObject().put("name","ui_mcp_parity").put("status",!new PixelTunnelConfig(context).writeEnabled()?"NOT TESTABLE":parity?"PASS":"FAIL").put("actions",actions.length()));
        JSONObject sync=DriveSyncCoordinator.status(context,store.getState().getJSONObject("sync_queue"));checks.put(new JSONObject().put("name","sync_state").put("status",!sync.optBoolean("configured")?"NOT TESTABLE":sync.optBoolean("drive_sync_failed")||sync.optInt("review")>0?"FAIL":"PASS").put("state",sync.optString("state")));
        File docs=new File(context.getFilesDir(),"business-documents");checks.put(new JSONObject().put("name","private_document_storage").put("status",docs.isDirectory()&&docs.canRead()?"PASS":"NOT TESTABLE"));
        checks.put(new JSONObject().put("name","physical_google_and_delivery").put("status","NOT TESTABLE").put("reason","Separate Kontofreigabe und tatsächlichen Providerbeleg prüfen; Diagnose sendet nichts."));
        JSONObject migration=LocalMigrationInventory.inspect(store.getState(),(kind,reference)->{
            JSONObject metadata=reference;File file;
            if(kind.equals("photo")){
                JSONObject local=store.photoAttachment(reference.optString("id"));if(local!=null)metadata=local;
                if(!PhotoAttachmentStore.canonicalName(metadata.optString("storage_name")))return false;
                file=store.photoAttachmentStore().file(metadata);
            }else{
                if(!metadata.optString("pdf_storage_name").matches("[0-9a-f]{64}\\.pdf"))return false;
                file=new File(new File(context.getFilesDir(),"business-documents"),metadata.getString("pdf_storage_name"));
            }
            String expected=metadata.optString(kind.equals("photo")?"content_sha256":"pdf_sha256");
            long size=metadata.optLong(kind.equals("photo")?"byte_size":"pdf_byte_size",-1);
            if(!expected.matches("[0-9a-f]{64}")||!file.isFile()||file.length()!=size)return false;
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long read=0;
            try(InputStream input=new BufferedInputStream(new FileInputStream(file))){byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){digest.update(buffer,0,n);read+=n;}}
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));return read==size&&expected.equals(hash.toString());
        });
        checks.put(new JSONObject().put("name","local_authority_transition").put("status","NOT TESTABLE").put("reason","Inventar ist keine Umstellungsfreigabe; vollständige externe Sicherung und aktueller Beleg-/Schreibernachweis erforderlich."));
        return new JSONObject().put("checks",checks).put("local_migration_inventory",migration).put("build_fingerprint",BuildProvenance.describe()).put("tool_count",catalogue.length()).put("tool_catalog_hash",PixelMcpWriteTools.hash(catalogue)).put("tool_catalog_semantic",CatalogSemanticHash.describe(catalogue)).put("ui_catalog_hash",PixelMcpWriteTools.hash(inventory)).put("ui_catalog_semantic",CatalogSemanticHash.describe(inventory)).put("sync",new JSONObject().put("state",sync.optString("state")).put("pending",sync.optInt("pending")).put("review",sync.optInt("review")).put("confirmation_scope",sync.optString("confirmation_scope")).put("cloud_confirmed",sync.optBoolean("cloud_confirmed"))).put("business_data_changed",false).put("secrets_exposed",false);
    }
}
