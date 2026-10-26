package at.hausgarten.service;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Existing SQLite/medial storage adapter. Same files, tables, journals and receipt bytes. */
final class LocalOwnerRepository implements OwnerRepository {
    private final Context context;
    private final PixelLocalStore store;
    private final OwnerRequestTransactions.Store requests;
    LocalOwnerRepository(Context context,PixelLocalStore store){
        this.context=context;this.store=store;
        this.requests=new OwnerRequestTransactions.Store(){
            public void begin(){store.getWritableDatabase().beginTransaction();}
            public String read(String key){return store.ownerValue(key,"");}
            public void write(String key,String value){store.putOwnerValue(key,value);}
            public void successful(){store.getWritableDatabase().setTransactionSuccessful();}
            public void end(){store.getWritableDatabase().endTransaction();}
        };
    }
    // Pure storage/transaction boundary for JVM execution of this actual repository method.
    LocalOwnerRepository(OwnerRequestTransactions.Store requests){this.context=null;this.store=null;this.requests=requests;}
    private static String digest(byte[] bytes)throws Exception{return PhotoAttachmentStore.sha256(bytes);}
    public JSONObject state(){return store.getState();}
    public JSONObject effective()throws Exception{return PixelMcpReadTools.effective(state());}
    public JSONObject settings()throws Exception{return new JSONObject(store.ownerValue("settings","{}"));}
    public JSONObject fileMeta(String id)throws Exception{try(Cursor row=store.getReadableDatabase().rawQuery("SELECT metadata FROM mcp_uploads WHERE id=?",new String[]{id})){if(!row.moveToFirst())throw new IllegalArgumentException("Die Dateireferenz fehlt.");return new JSONObject(row.getString(0));}}
    public byte[] fileBytes(String id)throws Exception{
        int size;try(Cursor row=store.getReadableDatabase().rawQuery("SELECT length(data) FROM mcp_uploads WHERE id=?",new String[]{id})){if(!row.moveToFirst())throw new IllegalArgumentException("Dateireferenz fehlt.");size=row.getInt(0);}
        if(size>64*1024*1024)throw new IOException("Datei überschreitet Transfergrenze.");ByteArrayOutputStream out=new ByteArrayOutputStream(size);
        for(int offset=0;offset<size;offset+=PixelMcpOwnerTools.CHUNK)try(Cursor row=store.getReadableDatabase().rawQuery("SELECT substr(data,?,?) FROM mcp_uploads WHERE id=?",new String[]{Integer.toString(offset+1),Integer.toString(Math.min(PixelMcpOwnerTools.CHUNK,size-offset)),id})){if(!row.moveToFirst())throw new IOException("Dateiblock fehlt.");out.write(row.getBlob(0));}
        return out.toByteArray();
    }
    public void putFile(String id,JSONObject meta,byte[] data)throws Exception{ContentValues values=new ContentValues();values.put("id",id);values.put("metadata",meta.toString());values.put("data",data);store.getWritableDatabase().insertWithOnConflict("mcp_uploads",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
    byte[] asset(String path)throws Exception{try(InputStream in=context.getAssets().open(path)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>20*1024*1024)throw new IOException("Datei zu groß.");out.write(buffer,0,n);}return out.toByteArray();}}
    JSONArray plants()throws Exception{return new JSONObject(new String(asset("plant-catalog.json"),StandardCharsets.UTF_8)).getJSONArray("plants");}
    JSONObject entry(String id)throws Exception{return PixelMcpWriteTools.find(plants(),id);}
    private PixelMcpWriteTools.Rejected localOriginalMissing(){return new PixelMcpWriteTools.Rejected("LOCAL_ORIGINAL_MISSING","Das benötigte Original fehlt im lokalen Bestand. Das Original gezielt importieren oder eine geprüfte vollständige Sicherung am Gerät prüfen; nichts geändert.");}
    JSONObject readChunk(JSONObject a)throws Exception{
        if("backup".equals(a.optString("resource_type"))){JSONObject meta=fileMeta(a.getString("resource_id"));if(DeletionBackupStore.SCOPE.equals(meta.optString("scope")))return DeletionBackupStore.chunk(deletionBackupRoot(),meta,a);}
        String type=a.getString("resource_type"),id=a.getString("resource_id"),mime;byte[] bytes;
        switch(type){
            case "photo":{
                PixelMcpWriteTools.find(effective().getJSONArray("photos"),id);JSONObject m=store.photoAttachment(id);if(m==null){if(!DriveRolePolicy.businessSyncAllowed()||LocalAuthorityGuard.local(context))throw localOriginalMissing();throw new IOException("Original fehlt lokal. Zuerst abgleichen.");}
                // The final app never fetches business originals from Drive.
                try{bytes=store.photoAttachmentStore().read(m);}catch(IOException absent){if(!DriveRolePolicy.businessSyncAllowed()||LocalAuthorityGuard.local(context))throw localOriginalMissing();if(!DriveSyncCoordinator.fetchPhoto(context,m,store.photoAttachmentStore()))throw absent;bytes=store.photoAttachmentStore().read(m);}mime=m.getString("mime_type");break;
            }
            case "offer":{JSONObject offer=PixelMcpWriteTools.find(effective().getJSONArray("offers"),id);if(LocalAuthorityGuard.local(context)&&(offer.optString("pdf_storage_name").isEmpty()||offer.optString("pdf_sha256").isEmpty()))throw localOriginalMissing();try{bytes=new BusinessDocumentStore(context).read(offer);}catch(IOException missing){if(!DriveRolePolicy.businessSyncAllowed()||LocalAuthorityGuard.local(context))throw localOriginalMissing();bytes=DriveSyncCoordinator.fetchDocument(context,offer);new BusinessDocumentStore(context).store(bytes,offer);}mime="application/pdf";break;}
            case "invoice":{JSONObject invoice=PixelMcpWriteTools.find(effective().getJSONArray("invoices"),id);if(LocalAuthorityGuard.local(context)&&!invoice.optString("pdf_state","READY").equals("READY"))throw new PixelMcpWriteTools.Rejected("INVOICE_PDF_NOT_READY","Das lokale Rechnungs-PDF ist noch nicht verifiziert. Dokumentauftrag prüfen oder erneut ausführen.");String data=invoice.optString("pdf_base64");if(data.isEmpty()){if(LocalAuthorityGuard.local(context)&&(invoice.optString("pdf_storage_name").isEmpty()||invoice.optString("pdf_sha256").isEmpty()))throw localOriginalMissing();try{bytes=new BusinessDocumentStore(context).read(invoice);}catch(IOException absent){if(!DriveRolePolicy.businessSyncAllowed()||LocalAuthorityGuard.local(context))throw localOriginalMissing();bytes=DriveSyncCoordinator.fetchDocument(context,invoice);new BusinessDocumentStore(context).store(bytes,invoice);}mime="application/pdf";break;}if(data.length()>28*1024*1024)throw new IOException("PDF zu groß.");bytes=Base64.getDecoder().decode(data);mime="application/pdf";if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("Original-PDF beschädigt.");if(!invoice.optString("pdf_sha256").isEmpty()&&!digest(bytes).equals(invoice.getString("pdf_sha256")))throw new IOException("Original-PDF stimmt nicht mit dem Archiv überein.");break;}
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
    public JSONObject enqueue(String kind,JSONObject payload,String request)throws Exception{
        if(Arrays.asList("archive","delete","purge_v1").contains(kind))revier().protectParent(payload.optString("entity"),payload.optString("id"));
        if(kind.equals("photo")&&payload.optString("role").equals("profile"))revier().protectParent("photo",payload.optString("id"));
        if(kind.equals(TrashPurgeBatch.KIND)){JSONArray entries=payload.getJSONArray("entries");for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);revier().protectParent(entry.getString("entity"),entry.getString("entity_id"));}}
        JSONArray predecessors=payload.optJSONArray("_sync_predecessors");if(predecessors==null)predecessors=new JSONArray();JSONArray prior=store.pendingActions();for(int i=0;i<prior.length();i++){JSONObject event=prior.getJSONObject(i);if((!payload.optString("id").isEmpty()&&payload.optString("id").equals(event.getJSONObject("payload").optString("id")))||(kind.equals("business_settings")&&kind.equals(event.getString("kind"))))predecessors.put(event.getString("client_id"));JSONArray linked=payload.optJSONArray("photo_link_ids");for(int j=0;linked!=null&&j<linked.length();j++)if(linked.getJSONObject(j).getString("id").equals(event.getJSONObject("payload").optString("id")))predecessors.put(event.getString("client_id"));}if(predecessors.length()>0)payload.put("_sync_predecessors",predecessors);
        if(kind.equals("appointment")||kind.equals("offer_convert")){JSONObject parentProjection=new JSONObject(state().getJSONObject("master").toString());DriveJournalRules.apply(parentProjection,prior);Set<String> incorporated=DriveJournalRules.ids(parentProjection.optJSONArray("drive_applied_ids"));
        String targetOrder=payload.optString("order_id",payload.optString("existing_order_id"));
        for(int i=0;i<prior.length();i++){JSONObject event=prior.getJSONObject(i),p=event.getJSONObject("payload");String previous=event.getString("client_id"),type=event.getString("kind");if(!incorporated.contains(previous))continue;
            boolean parent=type.equals("customer")&&p.optBoolean("_mcp_create")&&payload.optString("customer_id").equals(p.optString("id"));
            if(!targetOrder.isEmpty()&&type.equals("order")&&targetOrder.equals(p.optString("id")))parent=true;
            if(!targetOrder.isEmpty()&&type.equals("offer_convert")){String converted=p.optString("existing_order_id");if(converted.isEmpty())converted="ord_offer_"+PixelMcpWriteTools.hash(previous).substring(0,32);if(targetOrder.equals(converted))parent=true;}
            if(parent&&!predecessors.toString().contains(JSONObject.quote(previous)))predecessors.put(previous);
        }
        }if(predecessors.length()>0)payload.put("_sync_predecessors",predecessors);
        if(store.testLocalOnly(kind,payload))payload.put("_local_only",true);
        ContentValues a=new ContentValues();a.put("id",request);a.put("kind",kind);a.put("payload",payload.toString());a.put("created_at",java.time.Instant.now().toString());store.getWritableDatabase().insertOrThrow("pending",null,a);
        JSONObject result=new JSONObject().put("client_id",request).put("entity_type",payload.optString("entity",kind)).put("entity_id",payload.optString("id")).put("local_state","local_saved").put("sync_state",!DriveRolePolicy.businessSyncAllowed()||payload.optBoolean("_local_only")?"not_applicable":"sync_pending").put("test_local_only",payload.optBoolean("_local_only")).put("cloud_confirmed",false);
        if(!DriveRolePolicy.businessSyncAllowed())result.put("drive_role",DriveRolePolicy.role()).put("storage_contract_version",DriveRolePolicy.contractVersion());return result;
    }
    JSONObject backupCounts(JSONObject saved)throws Exception{JSONObject out=new JSONObject(),master=saved.getJSONObject("state").getJSONObject("master");for(String key:new String[]{"customers","orders","worklogs","invoices","photos","service_catalog","offers","appointments"})out.put(key,master.getJSONArray(key).length());return out;}
    public JSONObject createBackup(String request)throws Exception{
        JSONObject saved=new JSONObject().put("format",1).put("state",state()).put("settings",settings()).put("personal_paid",new JSONArray(store.ownerValue("personal_paid","[]")));JSONArray originals=new JSONArray();long originalBytes=0;
        try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT photo_id FROM photo_attachments ORDER BY photo_id",null)){while(rows.moveToNext()){String id=rows.getString(0);JSONObject m=store.photoAttachment(id);byte[] bytes;try{bytes=store.photoAttachmentStore().read(m);}catch(Exception missing){throw new IOException("Sicherung nicht vollständig: ein Foto-Original fehlt lokal. Original gezielt importieren oder eine geprüfte Vollsicherung zur Wiederherstellung prüfen.");}originalBytes+=bytes.length;if(originalBytes>40*1024*1024)throw new IOException("Vollständige Datei-/Gerätesicherung für diesen Fotoumfang verwenden.");originals.put(new JSONObject().put("photo_id",id).put("metadata",m).put("base64",Base64.getEncoder().encodeToString(bytes)));}}
        saved.put("originals",originals);JSONArray documents=new JSONArray();JSONObject master=saved.getJSONObject("state").getJSONObject("master");long mediaBytes=originalBytes;
        for(String collection:new String[]{"invoices","offers"})for(int i=0;i<master.getJSONArray(collection).length();i++){JSONObject record=master.getJSONArray(collection).getJSONObject(i);if(record.optString("pdf_storage_name").isEmpty())continue;byte[] pdf;try{pdf=new BusinessDocumentStore(context).read(record);}catch(Exception missing){throw new IOException("Sicherung nicht vollständig: ein Dokument fehlt lokal. Original gezielt importieren oder eine geprüfte Vollsicherung zur Wiederherstellung prüfen.");}mediaBytes+=pdf.length;if(mediaBytes>40*1024*1024)throw new IOException("Vollständige Datei-/Gerätesicherung für diesen Medienumfang verwenden.");documents.put(new JSONObject().put("metadata",record).put("base64",Base64.getEncoder().encodeToString(pdf)));}
        saved.put("documents",documents);
        // A backup is an explicit private transfer artifact, never a business snapshot or log.
        byte[] bytes=saved.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>64*1024*1024)throw new IOException("Sicherung überschreitet die sichere Transfergrenze von 64 MiB. Vollständige Geräte-/Dateisicherung verwenden; kein Teilerfolg.");
        String id="backup_"+PixelMcpWriteTools.hash(request);JSONObject meta=new JSONObject().put("kind","backup").put("byte_size",bytes.length).put("sha256",digest(bytes)).put("created_at",java.time.Instant.now().toString()).put("includes_credentials",false).put("scope","business_snapshot_pending_local_photos_and_documents");putFile(id,meta,bytes);return new JSONObject().put("backup_id",id).put("metadata",meta).put("collections",backupCounts(saved)).put("complete_device_backup",false);
    }
    java.io.File deletionBackupRoot(){return new java.io.File(context.getNoBackupFilesDir(),"deletion-backups");}
    public JSONObject protectDeletion(JSONObject snapshot,JSONArray entries,String request)throws Exception{
        JSONObject result=DeletionBackupStore.capture(deletionBackupRoot(),snapshot,entries,request,(type,record)->{
            if(type.equals("photo")){
                try{return store.photoAttachmentStore().read(record);}catch(IOException missing){if(!DriveRolePolicy.businessSyncAllowed()||LocalAuthorityGuard.local(context))throw localOriginalMissing();if(!DriveSyncCoordinator.fetchPhoto(context,record,store.photoAttachmentStore()))throw new IOException("Fotooriginal für Löschsicherung nicht verfügbar. Nichts gelöscht; Verbindung prüfen.");return store.photoAttachmentStore().read(record);}
            }
            try{return new BusinessDocumentStore(context).read(record);}catch(IOException missing){if(!DriveRolePolicy.businessSyncAllowed()||LocalAuthorityGuard.local(context))throw localOriginalMissing();byte[] bytes=DriveSyncCoordinator.fetchDocument(context,record);new BusinessDocumentStore(context).store(bytes,record);return new BusinessDocumentStore(context).read(record);}
        });
        // Only the small index is transactional SQLite data. ZIP bytes stay private
        // and durable even if a later enqueue/commit fails; originals are never lost.
        putFile(result.getString("backup_id"),result.getJSONObject("metadata"),new byte[0]);return result;
    }
    JSONObject inspectDeletionBackup(String id,JSONObject meta)throws Exception{return DeletionBackupStore.inspect(deletionBackupRoot(),meta).put("backup_id",id).put("metadata",meta).put("state_hash",PixelMcpWriteTools.hash(state()));}
    public JSONObject restoreBackup(JSONObject a,String request)throws Exception{
        if(LocalAuthorityGuard.local(context))throw new PixelMcpWriteTools.Rejected("RESTORE_FULL_REPLACEMENT_REQUIRED","Im lokalen Betrieb vollständige verschlüsselte Wiederherstellung am Gerät prüfen und ausdrücklich bestätigen. Alte Teilbestände werden nicht vermischt.");
        if(DeletionBackupStore.SCOPE.equals(fileMeta(a.getString("backup_id")).optString("scope")))throw new IllegalArgumentException("Löschsicherung enthält gezielt gesicherte Originale. Vor einer Wiederherstellung einzeln prüfen; endgültig entfernte IDs werden nicht automatisch wieder angelegt.");
        if(!PixelMcpWriteTools.hash(state()).equals(a.getString("expected_state_hash")))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","App-Datenstand wurde seit Prüfung geändert.");
        if(state().getJSONArray("pending").length()!=0||!"IDLE".equals(state().getJSONObject("work_session").optString("state")))throw new IllegalArgumentException("Ausstehende Aktionen oder Zeiterfassung zuerst prüfen. Keine Daten ersetzt.");
        JSONObject meta=fileMeta(a.getString("backup_id"));byte[] bytes=fileBytes(a.getString("backup_id"));if(!"backup".equals(meta.optString("kind"))||!digest(bytes).equals(meta.getString("sha256")))throw new IllegalArgumentException("Sicherung beschädigt.");
        JSONObject backup=new JSONObject(new String(bytes,StandardCharsets.UTF_8)),old=backup.getJSONObject("state");if(old.getJSONArray("pending").length()!=0||!"IDLE".equals(old.getJSONObject("work_session").optString("state")))throw new IllegalArgumentException("Sicherung enthält offene Aktionen oder Zeiterfassung. Nur isoliert prüfen; keine automatische Übernahme.");
        JSONObject target=effective(),source=old.getJSONObject("master");DriveJournalRules.validateSnapshot(source);
        // First verify every existing ID. A single conflict aborts the whole transaction.
        for(String key:new String[]{"customers","orders","worklogs","invoices","photos","service_catalog","offers","appointments"})for(int i=0;i<source.getJSONArray(key).length();i++){JSONObject row=source.getJSONArray(key).getJSONObject(i);String entity=key.equals("customers")?"customer":key.equals("orders")?"order":key.equals("appointments")?"appointment":key.equals("worklogs")?"worklog":key.equals("invoices")?"invoice":key.equals("offers")?"offer":key.equals("service_catalog")?"service_template":"photo";if(TrashRules.purged(target,entity,row.getString("id")))throw new PixelMcpWriteTools.Rejected("RECORD_PURGED","Sicherung enthält endgültig gelöschte IDs. Keine automatische Wiederherstellung.");JSONObject existing=OwnerJournalRules.findOrNull(target.getJSONArray(key),row.getString("id"));if(existing!=null&&!OwnerJournalRules.version(existing).equals(OwnerJournalRules.version(row)))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Sicherung und aktuelle Daten enthalten verschiedene Versionen. Keine Überschreibung.");}
        int count=0;for(String key:new String[]{"customers","orders","worklogs","invoices","photos","service_catalog","offers","appointments"}){JSONArray rows=source.getJSONArray(key);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(OwnerJournalRules.findOrNull(target.getJSONArray(key),row.getString("id"))==null){String entity=key.equals("customers")?"customer":key.equals("orders")?"order":key.equals("appointments")?"appointment":key.equals("worklogs")?"worklog":key.equals("invoices")?"invoice":"photo";if(OwnerJournalRules.archives(target).has(entity+":"+row.getString("id")))throw new IllegalArgumentException("Archiveintrag zuerst ausdrücklich prüfen und wiederherstellen.");if(key.equals("invoices")&&!row.optString("issued_at").isEmpty())throw new IllegalArgumentException("Ausgestellte Rechnungen benötigen vollständige Archivwiederherstellung; nichts übernommen.");String kind=key.equals("customers")?"customer":key.equals("orders")?"order":key.equals("appointments")?"appointment":key.equals("worklogs")?"worklog":key.equals("invoices")?"invoice":key.equals("offers")?"offer":key.equals("service_catalog")?"service_template":"photo";if(key.equals("offers")&&!row.optString("status","draft").equals("draft"))throw new IllegalArgumentException("Finalisierte Angebote benötigen vollständige Archivwiederherstellung; nichts übernommen.");JSONObject p=DriveJournalRules.clean(row);p.put("_mcp_create",true);DriveJournalRules.validateChange(kind,p,null,target);target.getJSONArray(key).put(row);enqueue(kind,p,request+"_"+count++);}}}
        // Media must already be verified locally; never restore dangling references.
        JSONArray originals=backup.getJSONArray("originals");for(int i=0;i<originals.length();i++){JSONObject photo=originals.getJSONObject(i);JSONObject m=photo.getJSONObject("metadata");store.photoAttachmentStore().storeExpected(Base64.getDecoder().decode(photo.getString("base64")),m);store.restoreAttachmentMetadata(photo.getString("photo_id"),m);}
        JSONArray documents=backup.optJSONArray("documents");for(int i=0;documents!=null&&i<documents.length();i++){JSONObject document=documents.getJSONObject(i);new BusinessDocumentStore(context).store(Base64.getDecoder().decode(document.getString("base64")),document.getJSONObject("metadata"));}
        return new JSONObject().put("added_records",count).put("overwritten_records",0).put("sync_state","sync_pending");
    }

    @Override public JSONArray pendingActions()throws Exception{return store.pendingActions();}
    @Override public String ownerValue(String key,String fallback){return store.ownerValue(key,fallback);}
    @Override public void putOwnerValue(String key,String value){store.putOwnerValue(key,value);}
    @Override public String savePhoto(JSONObject payload)throws Exception{return store.savePhoto(payload);}
    @Override public void replaceWorklog(JSONObject p)throws Exception{
        Set<String> prior=new HashSet<>();JSONArray before=store.pendingActions();for(int i=0;i<before.length();i++)prior.add(before.getJSONObject(i).getString("client_id"));store.saveWorklog(p);
        JSONArray after=store.pendingActions();for(int i=0;i<after.length();i++){JSONObject added=after.getJSONObject(i);if(!prior.contains(added.getString("client_id"))&&"worklog".equals(added.getString("kind"))&&p.getString("id").equals(added.getJSONObject("payload").optString("id")))store.getWritableDatabase().delete("pending","id=?",new String[]{added.getString("client_id")});}
    }
    @Override public JSONObject createDocument(JSONObject record)throws Exception{return new BusinessDocumentStore(context).create(record);}
    @Override public void readDocument(JSONObject record)throws Exception{new BusinessDocumentStore(context).read(record);}
    @Override public JSONObject verifyDelivery(String id,JSONObject offer)throws Exception{return MailAccount.client(context).verify(id,offer);}
    @Override public void verifyLegacyBackup(String id,String preview)throws Exception{LegacyConflictBackup.verify(context,store,id,preview);}
    @Override public JSONObject planningSettings()throws Exception{return AppointmentRuntime.publicSettings(context,store);}
    @Override public boolean calendarConnected(){return PlanningAccount.connected(context);}
    @Override public boolean weatherCredentialAvailable(){return PlanningAccount.prefs(context).contains("weather_secret");}
    @Override public void savePlanningSettings(JSONObject settings,String expected)throws Exception{AppointmentRuntime.saveSettings(context,store,settings,expected);}
    @Override public JSONObject requestPlanning()throws Exception{return AppointmentRuntime.request(context,store);}
    @Override public JSONObject acknowledgeWeather(JSONObject args)throws Exception{return AppointmentRuntime.acknowledge(context,store,args);}
    @Override public JSONObject resolveCalendar(JSONObject args)throws Exception{return AppointmentRuntime.resolve(context,store,args);}
    @Override public void checkBusy(JSONObject appointment)throws Exception{AppointmentRuntime.checkBusy(context,store,appointment);}
    @Override public JSONObject fullBackupStatus(JSONObject args)throws Exception{return new FullBackupController(context,store).status(args);}
    @Override public JSONObject fullBackupAction(String name,JSONObject args)throws Exception {
        FullBackupController control=new FullBackupController(context,store);
        if(name.equals("request_full_backup"))return control.request(args);
        if(name.equals("retry_full_backup"))return control.retry(args);
        throw new IllegalArgumentException("Sicherungsaktion fehlt.");
    }
    private RevierApplicationService revier()throws Exception{return new RevierApplicationService(new RevierLocalRepository(store,a->{String operation=a.optString("_operation","create_appointment");a.remove("_operation");return new PlanningApplicationService(this).perform(operation,a,"mcp_revier_inspection_"+PixelMcpWriteTools.hash(a.getString("request_id")));}));}
    @Override public JSONObject revierQuery(String name,JSONObject args)throws Exception{return revier().query(name,args);}
    @Override public JSONObject revierAction(String name,JSONObject args)throws Exception{return revier().execute(name,args);}
    @Override public JSONObject localInvoiceAction(String name,JSONObject args,String request)throws Exception {
        if(name.equals("activate_local_authority"))return new LocalAuthorityMigration(context,store).activateFromArgs(args);
        if(name.equals("reconcile_local_invoice_authority"))return new LocalAuthorityMigration(context,store).reconcile(args);
        return new LocalInvoiceService(context,store).performIfLocal(name,args,request);
    }
    @Override public JSONObject localFinancialQuery(String name,JSONObject args)throws Exception {
        if(name.equals("preview_local_authority"))return new LocalAuthorityMigration(context,store).preview();
        return new LocalInvoiceService(context,store).query(name,args);
    }
    interface Request { JSONObject run() throws Exception; }
    JSONObject runRequest(String request,String fingerprint,Request command)throws Exception{
        return OwnerRequestTransactions.runRequest(requests,request,fingerprint,command::run,java.time.Instant.now().toString());
    }
    JSONObject prepareLegacyBackup(String request,String preview)throws Exception{if(!DriveRolePolicy.businessSyncAllowed())throw new PixelMcpWriteTools.Rejected("LEGACY_SYNC_RETIRED","Der frühere Geschäftsabgleich ist stillgelegt. Vollsicherungsstatus prüfen; keine alten Drive-Geschäftsdaten nachgeladen.");return LegacyConflictBackup.create(context,store,request,preview);}
    JSONObject photoStorageSummary()throws Exception{return store.photoAttachmentStore().storageSummary();}
    JSONObject listBackups()throws Exception{
JSONArray out=new JSONArray();try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT id,metadata FROM mcp_uploads ORDER BY rowid DESC",null)){while(rows.moveToNext()){JSONObject m=new JSONObject(rows.getString(1));if("backup".equals(m.optString("kind")))out.put(new JSONObject().put("backup_id",rows.getString(0)).put("metadata",m));if(out.length()>=100)break;}}return new JSONObject().put("backups",out);    }
String migratePersonalPaid(String value){
    SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{if(!"1".equals(store.ownerValue("personal_paid_migrated",""))){JSONArray old=new JSONArray(value),current=new JSONArray(store.ownerValue("personal_paid","[]"));Set<String> union=new java.util.LinkedHashSet<>();for(JSONArray list:new JSONArray[]{old,current})for(int i=0;i<list.length();i++){String id=list.getString(i);if(id.isEmpty()||id.length()>500)throw new IllegalArgumentException();union.add(id);}store.putOwnerValue("personal_paid",new JSONArray(union).toString());store.putOwnerValue("personal_paid_migrated","1");}db.setTransactionSuccessful();return "OK";}catch(Exception e){return "ERROR: Persönliche Übersicht bleibt erhalten; Übernahme nicht abgeschlossen.";}finally{db.endTransaction();}
}
String setPersonalPaid(String value){
    try{JSONArray ids=new JSONArray(value);JSONObject all=SnapshotProjection.effective(store.getState());for(int i=0;i<ids.length();i++)PixelMcpWriteTools.find(all.getJSONArray("worklogs"),ids.getString(i));store.putOwnerValue("personal_paid",ids.toString());return "OK";}catch(Exception e){return "ERROR: Persönliche Übersicht konnte nicht gespeichert werden.";}
}
}
