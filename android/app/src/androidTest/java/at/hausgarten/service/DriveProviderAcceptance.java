package at.hausgarten.service;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real Android DocumentsContract/ContentResolver I/O against an UNREGISTERED private provider. */
final class DriveProviderAcceptance {
    static final String AUTHORITY="at.hausgarten.isolated.documents";
    static final String MOBILE="HausGarten-Mobile-qadevice.json";
    static final class MockDrive extends DocumentsProvider{
        final File directory;final LinkedHashMap<String,String> names=new LinkedHashMap<>(),mimes=new LinkedHashMap<>(),parents=new LinkedHashMap<>();int serial,queries,ioCalls;boolean nullQuery;boolean offlineWrite,badReadback,written,failAfterRename;String renamed="";
        MockDrive(File directory){this.directory=directory;directory.mkdirs();}
        String add(String name,String data)throws Exception{return add("root","application/json",name,data.getBytes(StandardCharsets.UTF_8));}
        String add(String parent,String mime,String name,byte[] data)throws Exception{String id="qa_doc"+(++serial);names.put(id,name);mimes.put(id,mime);parents.put(id,parent);try(FileOutputStream out=new FileOutputStream(file(id))){out.write(data);}return id;}
        void replace(String id,byte[] data)throws Exception{try(FileOutputStream out=new FileOutputStream(file(id),false)){out.write(data);out.getFD().sync();}}
        File file(String id){return new File(directory,id+".bin");}
        String text(String id)throws Exception{return new String(java.nio.file.Files.readAllBytes(file(id).toPath()),StandardCharsets.UTF_8);}
        @Override public boolean onCreate(){return true;}
        @Override public Cursor queryRoots(String[] projection){ioCalls++;return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID});}
        String[] columns(String[] projection){return projection==null?new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_LAST_MODIFIED}:projection;}
        void row(MatrixCursor cursor,String id){MatrixCursor.RowBuilder row=cursor.newRow();for(String key:cursor.getColumnNames()){switch(key){case DocumentsContract.Document.COLUMN_DOCUMENT_ID:row.add(id);break;case DocumentsContract.Document.COLUMN_DISPLAY_NAME:row.add(id.equals("root")?"Isolated Drive":names.get(id));break;case DocumentsContract.Document.COLUMN_MIME_TYPE:row.add(id.equals("root")?DocumentsContract.Document.MIME_TYPE_DIR:mimes.get(id));break;case DocumentsContract.Document.COLUMN_LAST_MODIFIED:row.add(id.equals("root")?0L:(long)Integer.parseInt(id.replace("qa_doc","")));break;default:row.add(null);}}}
        @Override public Cursor queryDocument(String id,String[] projection){ioCalls++;MatrixCursor cursor=new MatrixCursor(columns(projection));row(cursor,id);return cursor;}
        @Override public Cursor queryChildDocuments(String id,String[] projection,String sort){ioCalls++;queries++;if(nullQuery)return null;MatrixCursor cursor=new MatrixCursor(columns(projection));for(String child:names.keySet())if(id.equals(parents.get(child)))row(cursor,child);return cursor;}
        @Override public boolean isChildDocument(String parent,String child){ioCalls++;String current=child;while(parents.containsKey(current)){current=parents.get(current);if(parent.equals(current))return true;}return false;}
        @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{ioCalls++;try{return add(parent,mime,name,new byte[0]);}catch(Exception error){throw new FileNotFoundException("Isolated creation failed");}}
        @Override public String renameDocument(String id,String name)throws FileNotFoundException{ioCalls++;if(!names.containsKey(id))throw new FileNotFoundException();names.put(id,name);renamed=id;return id;}
        @Override public void deleteDocument(String id)throws FileNotFoundException{ioCalls++;names.remove(id);mimes.remove(id);parents.remove(id);if(file(id).exists()&&!file(id).delete())throw new FileNotFoundException("Isolated deletion failed");}
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal cancellation)throws FileNotFoundException{ioCalls++;
            if(failAfterRename&&id.equals(renamed)&&!mode.contains("w"))throw new FileNotFoundException("Simulated response lost after publish");
            if(mode.contains("w")){if(offlineWrite)throw new FileNotFoundException("Simulated network failure");written=true;}
            if(badReadback&&written&&!mode.contains("w")){File fake=new File(directory,"bad.json");try(FileOutputStream out=new FileOutputStream(fake)){out.write("{}".getBytes(StandardCharsets.UTF_8));}catch(IOException error){throw new FileNotFoundException();}return ParcelFileDescriptor.open(fake,ParcelFileDescriptor.MODE_READ_ONLY);}
            return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));
        }
    }
    static final class Setup implements AutoCloseable{
        final PixelMcpInstrumentation.Isolated context;final Context io;final SharedPreferences prefs;final MockDrive provider;final PixelLocalStore store;final LegacyDriveSyncFixture.Files files;
        Setup(PixelMcpInstrumentation qa)throws Exception{
            context=new PixelMcpInstrumentation.Isolated(qa.getTargetContext());prefs=context.getSharedPreferences("haus_garten",0);
            provider=new MockDrive(new File(context.getFilesDir(),context.prefix+"provider"));
            ProviderInfo info=new ProviderInfo();info.authority=AUTHORITY;info.exported=true;info.grantUriPermissions=true;info.readPermission=info.writePermission="android.permission.MANAGE_DOCUMENTS";
            // Metadata required by DocumentsProvider; NOT installed/registered/exported in any manifest.
            provider.attachInfo(context,info);
            ContentResolver resolver=ContentResolver.wrap(provider);
            io=new ContextWrapper(context){@Override public ContentResolver getContentResolver(){return resolver;}@Override public File getFilesDir(){File folder=new File(super.getFilesDir(),context.prefix+"backups");folder.mkdirs();return folder;}};
            Uri tree=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"root");JSONObject base=qa.fixture().put("photos",new JSONArray());
            provider.add("HausGarten-Laptop.json",base.toString());prefs.edit().putString("drive_tree_uri",tree.toString()).putString("drive_snapshot",base.toString()).commit();
            files=new LegacyDriveSyncFixture.Files(io,tree,prefs);store=new PixelLocalStore(io);
        }
        JSONObject sync()throws Exception{return LegacyDriveSyncFixture.run(context,store,files,"qadevice");}
        @Override public void close(){store.close();}
    }
    static JSONObject action(String client,String id)throws Exception{return SyntheticBusinessData.object().put("client_id",client).put("kind","worklog").put("payload",SyntheticBusinessData.object().put("id",id).put("customer_id","qa_c1").put("work_date","2026-10-12").put("activity","QA "+id).put("hours",1));}
    static byte[] zipBytes(java.util.zip.ZipFile zip,String name)throws Exception{try(InputStream in=zip.getInputStream(zip.getEntry(name));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>21*1024*1024)throw new IOException("Synthetic ZIP bound exceeded");out.write(b,0,n);}return out.toByteArray();}}
    static String pkg(JSONObject action)throws Exception{return SyntheticBusinessData.object().put("format",1).put("actions",new JSONArray().put(action)).toString();}
    static void run(PixelMcpInstrumentation qa)throws Exception{
        qa.probe("SAF read phase caches inventory and verified bytes but refreshes every write boundary",()->{try(Setup s=new Setup(qa)){
            String value="qa_verified_bytes",name="HausGarten-Commit-"+LegacyDriveRestFixture.sha256(value.getBytes(StandardCharsets.UTF_8))+".json",id=s.provider.add(name,value);
            s.files.beginRead();int before=s.provider.queries;s.files.documents();s.files.documents();qa.yes(s.provider.queries==before+1);
            LegacyDriveSyncFixture.Doc doc=null;for(LegacyDriveSyncFixture.Doc d:s.files.documents())if(d.id.equals(id))doc=d;
            qa.yes(doc!=null);byte[] first=s.files.readImmutable(doc);s.provider.replace(id,"qa_changed_same_name_and_time".getBytes(StandardCharsets.UTF_8));qa.yes(Arrays.equals(first,s.files.readImmutable(doc)));
            s.files.endRead();qa.yes(!Arrays.equals(first,s.files.readImmutable(doc)));qa.yes(s.files.documents().size()>0&&s.provider.queries>before+1);
            s.provider.nullQuery=true;try{s.files.documents();throw new AssertionError("null provider query accepted");}catch(SyncIoFailure e){qa.yes(e.code.equals("PROVIDER_QUERY_FAILED"));}finally{s.provider.nullQuery=false;}
        }});
        qa.probe("SAF conflicting duplicate masters produce stable diagnosis without overwrite",()->{try(Setup s=new Setup(qa)){
            s.provider.add("HausGarten-Laptop.json", "{\"qa_conflict\":true}");String before=s.store.snapshotText("");
            try{s.sync();throw new AssertionError("different masters accepted");}catch(Exception error){qa.yes(LegacyDriveSyncFixture.failureCode(error).equals("REMOTE_DUPLICATES_DIFFER"));}
            qa.yes(before.equals(s.store.snapshotText("")));qa.yes(s.context.getSharedPreferences("haus_garten",0).getBoolean("drive_user_action_required",false));
        }});

        qa.probe("slow Drive read never holds SQLite and concurrent run joins without second network request",()->{try(Setup s=new Setup(qa)){
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
            LegacyDriveSyncFixture.Files slow=new LegacyDriveSyncFixture.Files(s.io,Uri.parse(s.prefs.getString("drive_tree_uri","")),s.prefs){
                @Override String read(String name)throws Exception{if(reads.incrementAndGet()==1){entered.countDown();if(!release.await(30,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("QA slow provider timeout");}return super.read(name);}
            };
            java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.Future<JSONObject> first=workers.submit(()->LegacyDriveSyncFixture.run(s.context,s.store,slow,"qadevice"));
            try{
                qa.yes(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
                java.util.concurrent.Future<JSONObject> local=workers.submit(()->{JSONObject saved=s.store.execute("create_customer_work_record",qa.args("qa-during-slow-provider"));qa.yes(s.store.getState().getJSONArray("pending").length()==1);return saved;});
                qa.yes(local.get(10,java.util.concurrent.TimeUnit.SECONDS).getBoolean("ok"));
                JSONObject joined=LegacyDriveSyncFixture.run(s.context,s.store,s.files,"qadevice");qa.yes(joined.getBoolean("coalesced")&&!joined.getBoolean("cloud_confirmed")&&reads.get()==1);
            }finally{release.countDown();workers.shutdown();}
            qa.yes(first.get(120,java.util.concurrent.TimeUnit.SECONDS).getBoolean("ok"));
            qa.yes(s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==1&&s.store.pendingActions().length()==0);
        }});
        qa.probe("provider trash document cleanup removes only unreferenced verified PDFs and is repeatable",()->{try(Setup s=new Setup(qa)){
            s.sync();JSONObject snapshot=s.files.readPrimary(SyntheticBusinessData.object(s.files.read("HausGarten-Laptop.json")),"qadevice");PixelDriveState.prepare(snapshot);JSONObject archive=OwnerJournalRules.archives(snapshot);BusinessDocumentStore documents=new BusinessDocumentStore(s.io);
            JSONObject[] metadata=new JSONObject[2];for(int i=0;i<2;i++){byte[] raw=("%PDF-1.4\n% Synthetic cleanup "+i+"\n%%EOF").getBytes(StandardCharsets.UTF_8);String sha=PhotoAttachmentStore.sha256(raw);metadata[i]=SyntheticBusinessData.object().put("document_id","offer:qa_qa-doc-"+i).put("pdf_storage_name",sha+".pdf").put("pdf_sha256",sha).put("pdf_byte_size",raw.length);documents.store(raw,metadata[i]);s.files.writeDocumentVerified(metadata[i],raw);JSONObject record=SyntheticBusinessData.object(metadata[i].toString()).put("id","qa_qa-doc-"+i).put("offer_number","").put("status","draft").put("customer_id","qa_c1");archive.put("offer:qa_qa-doc-"+i,SyntheticBusinessData.object().put("record",record).put("archived_at","2026-10-18"));}
            snapshot.getJSONArray("invoices").put(SyntheticBusinessData.object(metadata[0].toString()).put("id","qa_qa-protected-document").put("customer_id","qa_c1").put("invoice_number","R-1").put("issued_at","2026-10-18").put("status","Gestellt").put("positions",new JSONArray()));
            JSONObject preview=TrashPurgeBatch.preview(snapshot);qa.yes(preview.getInt("count")==2);JSONObject purge=SyntheticBusinessData.object().put("client_id","qa-provider-empty-documents").put("kind",TrashPurgeBatch.KIND).put("created_at","2026-10-19T00:00:00Z").put("payload",TrashPurgeBatch.payload(snapshot,preview.getString("preview_hash"),true));TrashPurgeBatch.apply(snapshot,purge);
            s.files.publish(snapshot,new JSONArray());s.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject cleaned=TrashMediaCleanup.run(s.store,s.files,snapshot);qa.yes(cleaned.getInt("pending")==0&&cleaned.getInt("completed")==2);qa.yes(documents.read(metadata[0]).length>0&&s.files.readDocumentVerified(metadata[0]).length>0);
            boolean localMissing=false,remoteMissing=false;try{documents.read(metadata[1]);}catch(IOException expected){localMissing=true;}try{s.files.readDocumentVerified(metadata[1]);}catch(IOException expected){remoteMissing=true;}qa.yes(localMissing&&remoteMissing);qa.yes(TrashMediaCleanup.run(s.store,s.files,snapshot).getInt("completed")==0);
        }});
        qa.probe("Android provider full write readback and local acknowledgement without laptop",()->{try(Setup s=new Setup(qa)){
            JSONObject created=s.store.execute("create_customer_work_record",qa.args("qa-provider-success")).getJSONObject("data");String client=created.getJSONObject("sync").getString("client_id");
            JSONObject result=s.sync();qa.yes(result.getBoolean("ok")&&!result.getBoolean("cloud_confirmed"));qa.yes(s.store.pendingActions().length()==0);qa.yes(s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==1);
            qa.yes(s.files.readActions(MOBILE).length()==1);qa.yes(SyntheticBusinessData.object(s.provider.text("qa_doc1")).getJSONArray("worklogs").length()==0);JSONObject historical=s.store.status(client,s.store.getState());qa.yes(historical.getString("status").equals("local_saved")&&historical.getInt("storage_contract_version")==3&&historical.getJSONObject("historical_sync_inventory").getString("status").equals("transferred")&&historical.getJSONObject("historical_sync_inventory").getBoolean("provider_readback")&&!historical.getBoolean("uploaded_to_drive"));
        }});
        qa.probe("Android provider reopens committed primary state with no local database or processor",()->{try(Setup s=new Setup(qa)){
            s.store.execute("create_customer_work_record",qa.args("qa-primary-restart"));qa.yes(s.sync().getBoolean("ok"));
            JSONObject original=SyntheticBusinessData.object(s.files.read("HausGarten-Laptop.json"));JSONObject reopened=s.files.readPrimary(original,"qadevice");
            qa.yes(reopened.getJSONArray("worklogs").length()==1&&reopened.getJSONObject("drive_action_receipts").length()==1);
            String first=reopened.getString("drive_parent_publication_id");qa.yes(s.sync().getBoolean("ok"));
            JSONObject replay=s.files.readPrimary(original,"qadevice");qa.yes(replay.getJSONArray("worklogs").length()==1);qa.yes(replay.getJSONObject("capabilities").getBoolean("offers_v1"));
            qa.yes(s.files.read("HausGarten-Laptop.json").equals(s.provider.text("qa_doc1")));qa.yes(!s.prefs.getBoolean("drive_cloud_confirmed",false));
        }});
        qa.probe("Android provider duplicate names merge all actions without extra file",()->{try(Setup s=new Setup(qa)){
            String older=s.provider.add(MOBILE,pkg(action("qa-a","qa_w_a")));String before=s.provider.text(older);s.provider.add(MOBILE,pkg(action("qa-b","qa_w_b")));
            s.store.saveWorklog(SyntheticBusinessData.object().put("customer_id","qa_c1").put("work_date","2026-10-12").put("activity","QA c").put("hours",2));
            JSONObject result=s.sync();qa.yes(result.getBoolean("ok")&&result.getInt("duplicate_files")==1);qa.yes(s.provider.names.values().stream().filter(n->n.startsWith("HausGarten-Commit-")).count()==1);qa.yes(s.provider.text(older).equals(before));qa.yes(s.files.readActions(MOBILE).length()==3&&s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==3);
        }});
        qa.probe("Android provider write failure retains offline queue and previous file",()->{try(Setup s=new Setup(qa)){
            String own=s.provider.add(MOBILE,SyntheticBusinessData.object().put("actions",new JSONArray()).toString());String before=s.provider.text(own);
            s.store.execute("create_customer_work_record",qa.args("qa-provider-offline"));s.provider.offlineWrite=true;
            try{s.sync();throw new AssertionError("Expected failure");}catch(IOException expected){}
            qa.yes(s.store.pendingActions().length()==1&&s.provider.text(own).equals(before));qa.yes(s.prefs.getBoolean("drive_sync_failed",false));
        }});
        qa.probe("standalone legacy provider retries retain delete review while public backup-only queue stays retired",()->{try(Setup s=new Setup(qa)){
            JSONObject held=SyntheticBusinessData.object().put("client_id","qa_qa-held-delete").put("kind","delete").put("payload",SyntheticBusinessData.object().put("entity","customer").put("id","qa_c1"));
            android.content.ContentValues row=new android.content.ContentValues();row.put("id","qa_qa-held-delete");row.put("kind","delete");row.put("payload",held.getJSONObject("payload").toString());row.put("created_at","2026-10-13T01:00:00Z");s.store.getWritableDatabase().insertOrThrow("pending",null,row);
            for(int i=0;i<7;i++)s.sync();
            JSONObject state=s.store.getState(),q=state.getJSONObject("legacy_queue_inventory"),publicQueue=state.getJSONObject("sync_queue");qa.yes(q.getInt("pending_upload")==0&&q.getInt("review")==1&&q.getInt("total")==1&&s.store.pendingActions().length()==1);qa.yes(publicQueue.getString("mode").equals("backup_only")&&publicQueue.getInt("contract_version")==3&&publicQueue.getInt("total")==0&&publicQueue.getInt("pending")==0&&publicQueue.getInt("review")==1&&publicQueue.getInt("locally_committed")==1);
            qa.yes(s.files.readActions(MOBILE).length()==1&&s.store.getState().getJSONObject("master").getJSONArray("customers").length()==1);
        }});
        qa.probe("owner action status uses actual provider delivery instead of local application",()->{try(Setup s=new Setup(qa)){
            s.sync();PixelMcpReadTools tools=new PixelMcpReadTools(s.store::getState,s.store,new PixelOwnerAccess(s.io,s.store));JSONObject settings=tools.call("get_business_settings",SyntheticBusinessData.object()).getJSONObject("data");JSONObject saved=tools.call("update_business_settings",SyntheticBusinessData.object().put("request_id","qa-provider-owner-status").put("expected_record_hash",settings.getString("record_hash")).put("updates",SyntheticBusinessData.object().put("owner_name","QA Aussteller"))).getJSONObject("data");String id=saved.getString("client_id");qa.yes(!s.store.status(id,s.store.getState()).getBoolean("uploaded_to_drive"));s.sync();JSONObject result=s.store.status(id,s.store.getState());JSONObject archived=result.getJSONObject("historical_sync_inventory");qa.yes(result.getString("status").equals("local_saved")&&!result.getBoolean("provider_readback")&&!result.getBoolean("uploaded_to_drive")&&result.getString("confirmation_scope").equals("local_only")&&!result.getBoolean("cloud_confirmed")&&archived.getString("status").equals("transferred")&&archived.getBoolean("provider_readback")&&archived.getBoolean("uploaded_to_drive")&&archived.getString("confirmation_scope").equals("drive_provider_readback"));JSONObject noEvidence=SyntheticBusinessData.object(s.store.getState().toString());noEvidence.getJSONObject("master").remove("drive_delivered_ids");JSONObject onlyLocal=s.store.status(id,noEvidence);qa.yes(onlyLocal.getString("status").equals("local_saved")&&onlyLocal.getJSONObject("historical_sync_inventory").getString("status").equals("applied_on_pixel")&&!onlyLocal.getJSONObject("historical_sync_inventory").getBoolean("uploaded_to_drive")&&onlyLocal.getJSONObject("historical_sync_inventory").getString("confirmation_scope").equals("local_only")&&!onlyLocal.getBoolean("uploaded_to_drive")&&onlyLocal.getString("confirmation_scope").equals("local_only"));
        }});
        qa.probe("standalone legacy provider preserves seven conflicts and verified originals while public bulk close retires without mutation",()->{try(Setup s=new Setup(qa)){
            JSONObject oldLegacy=SyntheticBusinessData.object(s.files.read("HausGarten-Laptop.json"));DriveJournalRules.validateSnapshot(oldLegacy);
            JSONObject legacyPhoto=SyntheticBusinessData.object().put("id","qa_qa-original-legacy").put("customer_id","qa_c1").put("filename","original-legacy.gif");
            byte[] legacyBytes=Base64.getDecoder().decode(PixelMcpInstrumentation.VALID_GIF.substring(PixelMcpInstrumentation.VALID_GIF.indexOf(',')+1));String legacyFolder=s.provider.add("root",DocumentsContract.Document.MIME_TYPE_DIR,"HausGarten-Fotos",new byte[0]),legacyFile=s.provider.add(legacyFolder,"image/gif",legacyPhoto.getString("filename"),legacyBytes);
            oldLegacy.remove("appointments");String oldLegacyHash=PixelMcpWriteTools.hash(oldLegacy);oldLegacy.getJSONArray("photos").put(legacyPhoto);PixelDriveState.prepare(oldLegacy);oldLegacy.put("drive_publisher_id","qadevice").put("drive_legacy_base_hash",oldLegacyHash);s.files.publish(oldLegacy,new JSONArray());
            String photo=s.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("original_name","QA-backup.gif").put("data",PixelMcpInstrumentation.VALID_GIF));s.sync();JSONObject media=s.store.photoAttachment(photo);
            for(int i=0;i<7;i++){android.content.ContentValues row=new android.content.ContentValues();row.put("id","qa_qa-review-"+i);row.put("kind",i<6?"delete":"customer");row.put("payload",(i<6?SyntheticBusinessData.object().put("entity","worklog").put("id","qa_qa-missing-"+i):SyntheticBusinessData.object().put("id","qa_c1").put("name","QA altered").put("_mcp_create",true)).toString());row.put("created_at","2026-10-13T01:00:0"+i+"Z");s.store.getWritableDatabase().insertOrThrow("pending",null,row);}
            for(int i=0;i<7;i++){JSONObject reviewed=s.sync();qa.yes(reviewed.getBoolean("ok")&&reviewed.getBoolean("needs_review"));}
            JSONObject state=s.store.getState(),q=state.getJSONObject("legacy_queue_inventory"),publicQueue=state.getJSONObject("sync_queue");qa.yes(q.getInt("total")==7&&q.getInt("review")==7&&q.getInt("pending_upload")==0);qa.yes(state.getString("drive_role").equals("backup_only")&&state.getInt("storage_contract_version")==3&&publicQueue.getInt("total")==0&&publicQueue.getInt("pending")==0&&publicQueue.getInt("review")==7&&publicQueue.getInt("locally_committed")==7);
            qa.yes(state.getJSONObject("master").getJSONArray("customers").getJSONObject(0).getString("name").equals("QA Kunde"));qa.yes(s.files.readActions(MOBILE).length()==8);
            s.store.photoAttachmentStore().discardCreated(media);boolean missing=false;try{s.store.photoAttachmentStore().read(media);}catch(IOException expected){missing=true;}qa.yes(missing);
            String before=PixelDriveState.integrity(state.getJSONObject("master")).getString("business_state_hash");
            String previewHash=LegacyConflictRules.preview(s.store.getState()).getString("preview_hash");
            // The current publication was created with a pre-appointments legacy hash.
            // Reproduce an action_required sync without changing a single business record.
            s.prefs.edit().putBoolean("drive_sync_failed",true).putString("drive_last_error_code","LEGACY_SOURCE_CHANGED").commit();
            // This explicit standalone compatibility validator receives the same
            // original legacy queue, not the final app's zero-active-sync projection.
            JSONObject historicalState=SyntheticBusinessData.object(s.store.getState().toString());historicalState.put("sync_queue",new JSONObject(historicalState.getJSONObject("legacy_queue_inventory").toString()));LegacyBackupPolicy.require(historicalState,true,true,true,false,"provider_readback","LEGACY_SOURCE_CHANGED",previewHash);
            qa.rejected(()->LegacyConflictBackupFixture.createVerified(s.io,s.store,"qa-provider-stale-backup",s.files,"0".repeat(64)));
            LegacyDriveSyncFixture.Files drifting=new LegacyDriveSyncFixture.Files(s.io,Uri.parse(s.prefs.getString("drive_tree_uri","")),s.prefs){int reads;@Override String read(String name)throws Exception{String raw=super.read(name);if(name.equals("HausGarten-Laptop.json")&&++reads==2)return SyntheticBusinessData.object(raw).put("concurrent_evidence","changed").toString();return raw;}};
            boolean driftRejected=false;try{LegacyConflictBackupFixture.createVerified(s.io,s.store,"qa-provider-drifting-backup",drifting,previewHash);}catch(IOException changed){driftRejected=changed.getMessage().contains("Drive-Quelle während der Sicherung geändert");}qa.yes(driftRejected);qa.yes(s.store.ownerValue("legacy-backup-request:"+PixelMcpWriteTools.hash("qa-provider-drifting-backup"),"").isEmpty());
            qa.rejected(()->s.files.readLegacyPhoto(SyntheticBusinessData.object(legacyPhoto.toString()).put("filename","../outside.gif")));
            qa.rejected(()->s.files.readLegacyPhoto(SyntheticBusinessData.object(legacyPhoto.toString()).put("filename","https://outside.invalid/x.gif")));
            s.provider.names.put(legacyFile,"temporarily-missing.gif");boolean missingLegacy=false;try{LegacyConflictBackupFixture.createVerified(s.io,s.store,"qa-provider-missing-legacy",s.files,previewHash);}catch(IOException absent){missingLegacy=true;}qa.yes(missingLegacy);s.provider.names.put(legacyFile,legacyPhoto.getString("filename"));
            LegacyDriveSyncFixture.Files photoDrift=new LegacyDriveSyncFixture.Files(s.io,Uri.parse(s.prefs.getString("drive_tree_uri","")),s.prefs){int reads;@Override byte[] readLegacyPhoto(JSONObject photo)throws Exception{byte[] raw=super.readLegacyPhoto(photo);if(++reads==2)raw[raw.length-1]^=1;return raw;}};
            boolean mediaDrift=false;try{LegacyConflictBackupFixture.createVerified(s.io,s.store,"qa-provider-legacy-drift",photoDrift,previewHash);}catch(IOException changed){mediaDrift=changed.getMessage().contains("Legacy-Foto während");}qa.yes(mediaDrift&&s.store.ownerValue("legacy-backup-request:"+PixelMcpWriteTools.hash("qa-provider-legacy-drift"),"").isEmpty());
            JSONObject backup=LegacyConflictBackupFixture.createVerified(s.io,s.store,"qa-provider-seven-backup",s.files,previewHash),meta=backup.getJSONObject("backup"),preview=backup.getJSONObject("preview");
            qa.yes(meta.getBoolean("complete")&&meta.getString("confirmation_scope").equals("provider_readback")&&!meta.getBoolean("cloud_confirmed")&&meta.getInt("legacy_photo_originals")==1);
            File zipFile=new File(new File(new File(s.io.getNoBackupFilesDir(),"legacy-conflict-backups"),meta.getString("backup_id")),"backup.zip");try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(zipFile)){JSONArray index=new JSONArray(new String(zipBytes(zip,"legacy-photo-index.json"),StandardCharsets.UTF_8));JSONObject item=index.getJSONObject(0);qa.yes(index.length()==1&&item.getString("photo_id").equals(legacyPhoto.getString("id"))&&item.getString("observed_sha256").equals(PhotoAttachmentStore.sha256(legacyBytes)));qa.yes(Arrays.equals(legacyBytes,zipBytes(zip,item.getString("archive_path"))));}
            qa.yes(s.store.photoAttachmentStore().read(media).length==media.getInt("byte_size"));
            LegacyConflictBackupFixture.verify(s.io,s.store,meta.getString("backup_id"),preview.getString("preview_hash"));
            PixelOwnerAccess owner=new PixelOwnerAccess(s.io,s.store);JSONArray entries=preview.getJSONArray("entries");
            JSONObject close=SyntheticBusinessData.object().put("request_id","qa-provider-bulk-close").put("backup_id",meta.getString("backup_id")).put("expected_preview_hash",previewHash).put("confirmed",true);
            String queueBefore=PixelMcpWriteTools.hash(s.store.pendingActions()),wireBefore=PixelMcpWriteTools.hash(s.files.readActions(MOBILE)),historyBefore=PixelMcpWriteTools.hash(state.getJSONObject("master").optJSONObject("owner_conflict_history")),receipt="receipt:mcp_owner_"+PixelMcpWriteTools.hash(close.getString("request_id"));int providerBefore=s.provider.ioCalls;qa.yes(entries.length()==7&&s.store.ownerValue(receipt,"").isEmpty());
            qa.rejected(()->owner.execute("close_legacy_conflicts",SyntheticBusinessData.object(close.toString()).put("confirmed",false)));
            McpAuditTools router=new McpAuditTools(s.store::getState,s.store,owner);
            JSONObject retired=McpWorkflowAcceptance.rpc(router,"close_legacy_conflicts",close).getJSONObject("data"),retry=McpWorkflowAcceptance.rpc(router,"close_legacy_conflicts",close).getJSONObject("data");qa.yes(retired.getString("code").equals("LEGACY_SYNC_RETIRED")&&retired.getString("drive_role").equals("backup_only")&&retired.getInt("storage_contract_version")==3&&!retired.getBoolean("backup_started")&&!retired.has("decisions_queued")&&PixelMcpWriteTools.hash(retired).equals(PixelMcpWriteTools.hash(retry)));
            qa.yes(s.provider.ioCalls==providerBefore&&s.store.ownerValue(receipt,"").isEmpty()&&queueBefore.equals(PixelMcpWriteTools.hash(s.store.pendingActions()))&&historyBefore.equals(PixelMcpWriteTools.hash(s.store.getState().getJSONObject("master").optJSONObject("owner_conflict_history")))&&before.equals(PixelDriveState.integrity(s.store.getState().getJSONObject("master")).getString("business_state_hash")));
            // Further explicitly supplied legacy-engine retries still cannot invent
            // the seven retired decisions or consume their original action IDs.
            s.sync();qa.yes(s.store.pendingActions().length()==7&&queueBefore.equals(PixelMcpWriteTools.hash(s.store.pendingActions()))&&before.equals(PixelDriveState.integrity(s.store.getState().getJSONObject("master")).getString("business_state_hash")));
            qa.yes(historyBefore.equals(PixelMcpWriteTools.hash(s.store.getState().getJSONObject("master").optJSONObject("owner_conflict_history")))&&s.files.readActions(MOBILE).length()==8&&wireBefore.equals(PixelMcpWriteTools.hash(s.files.readActions(MOBILE))));
            s.sync();qa.yes(s.store.getState().getJSONObject("legacy_queue_inventory").getInt("total")==7&&s.store.getState().getJSONObject("sync_queue").getInt("total")==0&&!s.prefs.getBoolean("drive_cloud_confirmed",false));LegacyConflictBackupFixture.verify(s.io,s.store,meta.getString("backup_id"),preview.getString("preview_hash"));
        }});
        qa.probe("Android provider wrong readback never acknowledges local action",()->{try(Setup s=new Setup(qa)){
            s.store.execute("create_customer_work_record",qa.args("qa-provider-bad-read"));s.provider.badReadback=true;
            try{s.sync();throw new AssertionError("Expected failure");}catch(IOException expected){}
            qa.yes(s.store.pendingActions().length()==1&&s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==0);
        }});
        qa.probe("Android provider uploads photo original before metadata journal and verifies readback",()->{try(Setup s=new Setup(qa)){
            String id=s.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("original_name","QA.gif").put("data",PixelMcpInstrumentation.VALID_GIF));JSONObject result=s.sync();qa.yes(result.getBoolean("ok")&&s.store.pendingActions().length()==0);
            JSONObject metadata=s.store.photoAttachment(id);qa.yes(metadata!=null&&s.files.readPhotoVerified(metadata).length==42);
            JSONArray actions=s.files.readActions(MOBILE);JSONObject payload=actions.getJSONObject(0).getJSONObject("payload");qa.yes(!payload.has("data")&&payload.getString("storage_name").equals(metadata.getString("storage_name")));
        }});
        qa.probe("Android provider keeps a verified photo after rename/readback failure and retry does not duplicate",()->{try(Setup s=new Setup(qa)){
            String id=s.store.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("data",PixelMcpInstrumentation.VALID_GIF));JSONObject metadata=s.store.photoAttachment(id);s.provider.failAfterRename=true;try{s.files.writePhotoVerified(metadata,s.store.photoAttachmentStore());throw new AssertionError("Expected lost readback");}catch(IOException expected){}String name=metadata.getString("storage_name");qa.yes(Collections.frequency(new ArrayList<>(s.provider.names.values()),name)==1);s.provider.failAfterRename=false;s.files.writePhotoVerified(metadata,s.store.photoAttachmentStore());qa.yes(Collections.frequency(new ArrayList<>(s.provider.names.values()),name)==1);qa.yes(s.store.pendingActions().length()==1);qa.yes(s.files.readPhotoVerified(metadata).length>0);
        }});
        qa.probe("public backup-only photo reads retain metadata and reject missing local original without provider I/O while explicit legacy port verifies bytes",()->{try(Setup s=new Setup(qa)){
            byte[] bytes=android.util.Base64.decode(PixelMcpInstrumentation.VALID_GIF.substring(PixelMcpInstrumentation.VALID_GIF.indexOf(',')+1),android.util.Base64.DEFAULT);String hash=PhotoAttachmentStore.sha256(bytes),name=hash+".gif";
            String folder=s.provider.add("root",DocumentsContract.Document.MIME_TYPE_DIR,"HausGarten-Fotos",new byte[0]),original=s.provider.add(folder,"image/gif",name,bytes);
            JSONObject snapshot=qa.fixture().put("photos",new JSONArray().put(SyntheticBusinessData.object().put("id","qa_remote_photo").put("customer_id","qa_c1").put("original_name","Remote.gif").put("filename",name).put("storage_name",name).put("content_sha256",hash).put("mime_type","image/gif").put("byte_size",bytes.length).put("role","documentation")));
            // Explicit synthetic metadata setup, not a runtime Drive import. The
            // injected standalone Files port separately proves the old file layout.
            PixelDriveState.prepare(snapshot);s.provider.replace("qa_doc1",snapshot.toString().getBytes(StandardCharsets.UTF_8));s.store.recordSnapshotPhotoAttachments(snapshot);s.store.commitSnapshot(snapshot,Collections.emptySet());JSONObject metadata=s.store.photoAttachment("qa_remote_photo");qa.yes(metadata!=null&&metadata.getString("content_sha256").equals(hash)&&metadata.getInt("byte_size")==bytes.length);byte[] verified=s.files.readPhotoVerified(metadata);qa.yes(Arrays.equals(verified,bytes)&&PhotoAttachmentStore.sha256(verified).equals(hash));
            String before=s.store.snapshotText(""),pending=PixelMcpWriteTools.hash(s.store.pendingActions()),attachment=PixelMcpWriteTools.hash(metadata),record=PixelMcpWriteTools.hash(PixelMcpWriteTools.find(PixelMcpReadTools.effective(s.store.getState()).getJSONArray("photos"),"qa_remote_photo"));int providerBefore=s.provider.ioCalls;boolean writtenBefore=s.provider.written;
            try{s.store.loadPhotoData("qa_remote_photo");throw new AssertionError("QA_PUBLIC_PHOTO_FETCH_REACTIVATED");}catch(IOException missing){qa.yes(missing.getMessage().equals("LOCAL_ORIGINAL_MISSING"));}
            PixelMcpReadTools publicTools=new PixelMcpReadTools(s.store::getState,s.store,new PixelOwnerAccess(s.io,s.store));try{publicTools.call("get_file_metadata",new JSONObject().put("resource_type","photo").put("resource_id","qa_remote_photo"));throw new AssertionError("QA_PUBLIC_ORIGINAL_READ_REACTIVATED");}catch(PixelMcpWriteTools.Rejected missing){qa.yes(missing.code.equals("LOCAL_ORIGINAL_MISSING"));}
            try{LegacyDriveSyncFixture.fetchPhoto(s.io,metadata,s.store.photoAttachmentStore());throw new AssertionError("QA_PUBLIC_NETWORK_FALLBACK_REACTIVATED");}catch(IOException missing){qa.yes(missing.getMessage().equals("LOCAL_ORIGINAL_MISSING"));}
            qa.yes(s.provider.ioCalls==providerBefore&&s.provider.written==writtenBefore&&before.equals(s.store.snapshotText(""))&&pending.equals(PixelMcpWriteTools.hash(s.store.pendingActions()))&&attachment.equals(PixelMcpWriteTools.hash(s.store.photoAttachment("qa_remote_photo")))&&record.equals(PixelMcpWriteTools.hash(PixelMcpWriteTools.find(PixelMcpReadTools.effective(s.store.getState()).getJSONArray("photos"),"qa_remote_photo"))));
            qa.yes(s.store.pendingActions().length()==0&&s.store.getState().getInt("storage_contract_version")==3&&s.store.getState().getString("drive_role").equals("backup_only")&&PhotoAttachmentStore.sha256(java.nio.file.Files.readAllBytes(s.provider.file(original).toPath())).equals(hash));try{s.store.photoAttachmentStore().read(metadata);throw new AssertionError("QA_MISSING_ORIGINAL_WAS_SILENTLY_IMPORTED");}catch(IOException expected){}
        }});
    }
}
