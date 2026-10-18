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
        final File directory;final LinkedHashMap<String,String> names=new LinkedHashMap<>(),mimes=new LinkedHashMap<>(),parents=new LinkedHashMap<>();int serial;boolean offlineWrite,badReadback,written,failAfterRename;String renamed="";
        MockDrive(File directory){this.directory=directory;directory.mkdirs();}
        String add(String name,String data)throws Exception{return add("root","application/json",name,data.getBytes(StandardCharsets.UTF_8));}
        String add(String parent,String mime,String name,byte[] data)throws Exception{String id="doc"+(++serial);names.put(id,name);mimes.put(id,mime);parents.put(id,parent);try(FileOutputStream out=new FileOutputStream(file(id))){out.write(data);}return id;}
        void replace(String id,byte[] data)throws Exception{try(FileOutputStream out=new FileOutputStream(file(id),false)){out.write(data);out.getFD().sync();}}
        File file(String id){return new File(directory,id+".bin");}
        String text(String id)throws Exception{return new String(java.nio.file.Files.readAllBytes(file(id).toPath()),StandardCharsets.UTF_8);}
        @Override public boolean onCreate(){return true;}
        @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID});}
        String[] columns(String[] projection){return projection==null?new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_LAST_MODIFIED}:projection;}
        void row(MatrixCursor cursor,String id){MatrixCursor.RowBuilder row=cursor.newRow();for(String key:cursor.getColumnNames()){switch(key){case DocumentsContract.Document.COLUMN_DOCUMENT_ID:row.add(id);break;case DocumentsContract.Document.COLUMN_DISPLAY_NAME:row.add(id.equals("root")?"Isolated Drive":names.get(id));break;case DocumentsContract.Document.COLUMN_MIME_TYPE:row.add(id.equals("root")?DocumentsContract.Document.MIME_TYPE_DIR:mimes.get(id));break;case DocumentsContract.Document.COLUMN_LAST_MODIFIED:row.add(id.equals("root")?0L:(long)Integer.parseInt(id.replace("doc","")));break;default:row.add(null);}}}
        @Override public Cursor queryDocument(String id,String[] projection){MatrixCursor cursor=new MatrixCursor(columns(projection));row(cursor,id);return cursor;}
        @Override public Cursor queryChildDocuments(String id,String[] projection,String sort){MatrixCursor cursor=new MatrixCursor(columns(projection));for(String child:names.keySet())if(id.equals(parents.get(child)))row(cursor,child);return cursor;}
        @Override public boolean isChildDocument(String parent,String child){String current=child;while(parents.containsKey(current)){current=parents.get(current);if(parent.equals(current))return true;}return false;}
        @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{try{return add(parent,mime,name,new byte[0]);}catch(Exception error){throw new FileNotFoundException("Isolated creation failed");}}
        @Override public String renameDocument(String id,String name)throws FileNotFoundException{if(!names.containsKey(id))throw new FileNotFoundException();names.put(id,name);renamed=id;return id;}
        @Override public void deleteDocument(String id)throws FileNotFoundException{names.remove(id);mimes.remove(id);parents.remove(id);if(file(id).exists()&&!file(id).delete())throw new FileNotFoundException("Isolated deletion failed");}
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal cancellation)throws FileNotFoundException{
            if(failAfterRename&&id.equals(renamed)&&!mode.contains("w"))throw new FileNotFoundException("Simulated response lost after publish");
            if(mode.contains("w")){if(offlineWrite)throw new FileNotFoundException("Simulated network failure");written=true;}
            if(badReadback&&written&&!mode.contains("w")){File fake=new File(directory,"bad.json");try(FileOutputStream out=new FileOutputStream(fake)){out.write("{}".getBytes(StandardCharsets.UTF_8));}catch(IOException error){throw new FileNotFoundException();}return ParcelFileDescriptor.open(fake,ParcelFileDescriptor.MODE_READ_ONLY);}
            return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));
        }
    }
    static final class Setup implements AutoCloseable{
        final PixelMcpInstrumentation.Isolated context;final Context io;final SharedPreferences prefs;final MockDrive provider;final MainActivity.LocalStore store;final DriveSyncCoordinator.Files files;
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
            files=new DriveSyncCoordinator.Files(io,tree,prefs);store=new MainActivity.LocalStore(io);
        }
        JSONObject sync()throws Exception{return DriveSyncCoordinator.run(context,store,files,"qadevice");}
        @Override public void close(){store.close();}
    }
    static JSONObject action(String client,String id)throws Exception{return new JSONObject().put("client_id",client).put("kind","worklog").put("payload",new JSONObject().put("id",id).put("customer_id","qa_c1").put("work_date","2026-10-12").put("activity","QA "+id).put("hours",1));}
    static String pkg(JSONObject action)throws Exception{return new JSONObject().put("format",1).put("actions",new JSONArray().put(action)).toString();}
    static void run(PixelMcpInstrumentation qa)throws Exception{
        qa.probe("Android provider full write readback and local acknowledgement without laptop",()->{try(Setup s=new Setup(qa)){
            JSONObject created=s.store.execute("create_customer_work_record",qa.args("qa-provider-success")).getJSONObject("data");String client=created.getJSONObject("sync").getString("client_id");
            JSONObject result=s.sync();qa.yes(result.getBoolean("ok")&&!result.getBoolean("cloud_confirmed"));qa.yes(s.store.pendingActions().length()==0);qa.yes(s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==1);
            qa.yes(s.files.readActions(MOBILE).length()==1);qa.yes(new JSONObject(s.provider.text("doc1")).getJSONArray("worklogs").length()==0);qa.yes(s.store.status(client,s.store.getState()).getString("status").equals("transferred"));
        }});
        qa.probe("Android provider reopens committed primary state with no local database or processor",()->{try(Setup s=new Setup(qa)){
            s.store.execute("create_customer_work_record",qa.args("qa-primary-restart"));qa.yes(s.sync().getBoolean("ok"));
            JSONObject original=new JSONObject(s.files.read("HausGarten-Laptop.json"));JSONObject reopened=s.files.readPrimary(original,"qadevice");
            qa.yes(reopened.getJSONArray("worklogs").length()==1&&reopened.getJSONObject("drive_action_receipts").length()==1);
            String first=reopened.getString("drive_parent_publication_id");qa.yes(s.sync().getBoolean("ok"));
            JSONObject replay=s.files.readPrimary(original,"qadevice");qa.yes(replay.getJSONArray("worklogs").length()==1);qa.yes(replay.getJSONObject("capabilities").getBoolean("offers_v1"));
            qa.yes(s.files.read("HausGarten-Laptop.json").equals(s.provider.text("doc1")));qa.yes(!s.prefs.getBoolean("drive_cloud_confirmed",false));
        }});
        qa.probe("Android provider duplicate names merge all actions without extra file",()->{try(Setup s=new Setup(qa)){
            String older=s.provider.add(MOBILE,pkg(action("qa-a","qa_w_a")));String before=s.provider.text(older);s.provider.add(MOBILE,pkg(action("qa-b","qa_w_b")));
            s.store.saveWorklog(new JSONObject().put("customer_id","qa_c1").put("work_date","2026-10-12").put("activity","QA c").put("hours",2));
            JSONObject result=s.sync();qa.yes(result.getBoolean("ok")&&result.getInt("duplicate_files")==1);qa.yes(s.provider.names.values().stream().filter(n->n.startsWith("HausGarten-Commit-")).count()==1);qa.yes(s.provider.text(older).equals(before));qa.yes(s.files.readActions(MOBILE).length()==3&&s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==3);
        }});
        qa.probe("Android provider write failure retains offline queue and previous file",()->{try(Setup s=new Setup(qa)){
            String own=s.provider.add(MOBILE,new JSONObject().put("actions",new JSONArray()).toString());String before=s.provider.text(own);
            s.store.execute("create_customer_work_record",qa.args("qa-provider-offline"));s.provider.offlineWrite=true;
            try{s.sync();throw new AssertionError("Expected failure");}catch(IOException expected){}
            qa.yes(s.store.pendingActions().length()==1&&s.provider.text(own).equals(before));qa.yes(s.prefs.getBoolean("drive_sync_failed",false));
        }});
        qa.probe("Android provider repeated sync retains delete but reports review not pending transfer",()->{try(Setup s=new Setup(qa)){
            JSONObject held=new JSONObject().put("client_id","qa-held-delete").put("kind","delete").put("payload",new JSONObject().put("entity","customer").put("id","qa_c1"));
            android.content.ContentValues row=new android.content.ContentValues();row.put("id","qa-held-delete");row.put("kind","delete");row.put("payload",held.getJSONObject("payload").toString());row.put("created_at","2026-10-13T01:00:00Z");s.store.getWritableDatabase().insertOrThrow("pending",null,row);
            for(int i=0;i<7;i++)s.sync();
            JSONObject q=s.store.getState().getJSONObject("sync_queue");qa.yes(q.getInt("pending_upload")==0&&q.getInt("review")==1&&s.store.pendingActions().length()==1);
            qa.yes(s.files.readActions(MOBILE).length()==1&&s.store.getState().getJSONObject("master").getJSONArray("customers").length()==1);
        }});
        qa.probe("Android provider six deletes and one customer conflict stay protected across seven retries",()->{try(Setup s=new Setup(qa)){
            for(int i=0;i<7;i++){android.content.ContentValues row=new android.content.ContentValues();row.put("id","qa-review-"+i);row.put("kind",i<6?"delete":"customer");row.put("payload",(i<6?new JSONObject().put("entity","worklog").put("id","qa-missing-"+i):new JSONObject().put("id","qa_c1").put("name","QA altered").put("_mcp_create",true)).toString());row.put("created_at","2026-10-13T01:00:0"+i+"Z");s.store.getWritableDatabase().insertOrThrow("pending",null,row);}
            for(int i=0;i<7;i++)qa.yes(!s.sync().getBoolean("ok"));
            JSONObject state=s.store.getState(),q=state.getJSONObject("sync_queue");qa.yes(q.getInt("total")==7&&q.getInt("review")==7&&q.getInt("pending_upload")==0);
            qa.yes(state.getJSONObject("master").getJSONArray("customers").getJSONObject(0).getString("name").equals("QA Kunde"));qa.yes(s.files.readActions(MOBILE).length()==7);
        }});
        qa.probe("Android provider wrong readback never acknowledges local action",()->{try(Setup s=new Setup(qa)){
            s.store.execute("create_customer_work_record",qa.args("qa-provider-bad-read"));s.provider.badReadback=true;
            try{s.sync();throw new AssertionError("Expected failure");}catch(IOException expected){}
            qa.yes(s.store.pendingActions().length()==1&&s.store.getState().getJSONObject("master").getJSONArray("worklogs").length()==0);
        }});
        qa.probe("Android provider uploads photo original before metadata journal and verifies readback",()->{try(Setup s=new Setup(qa)){
            String id=s.store.savePhoto(new JSONObject().put("customer_id","qa_c1").put("original_name","QA.gif").put("data",PixelMcpInstrumentation.VALID_GIF));JSONObject result=s.sync();qa.yes(result.getBoolean("ok")&&s.store.pendingActions().length()==0);
            JSONObject metadata=s.store.photoAttachment(id);qa.yes(metadata!=null&&s.files.readPhotoVerified(metadata).length==42);
            JSONArray actions=s.files.readActions(MOBILE);JSONObject payload=actions.getJSONObject(0).getJSONObject("payload");qa.yes(!payload.has("data")&&payload.getString("storage_name").equals(metadata.getString("storage_name")));
        }});
        qa.probe("Android provider keeps a verified photo after rename/readback failure and retry does not duplicate",()->{try(Setup s=new Setup(qa)){
            String id=s.store.savePhoto(new JSONObject().put("customer_id","qa_c1").put("data",PixelMcpInstrumentation.VALID_GIF));JSONObject metadata=s.store.photoAttachment(id);s.provider.failAfterRename=true;try{s.files.writePhotoVerified(metadata,s.store.photoAttachmentStore());throw new AssertionError("Expected lost readback");}catch(IOException expected){}String name=metadata.getString("storage_name");qa.yes(Collections.frequency(new ArrayList<>(s.provider.names.values()),name)==1);s.provider.failAfterRename=false;s.files.writePhotoVerified(metadata,s.store.photoAttachmentStore());qa.yes(Collections.frequency(new ArrayList<>(s.provider.names.values()),name)==1);qa.yes(s.store.pendingActions().length()==1);qa.yes(s.files.readPhotoVerified(metadata).length>0);
        }});
        qa.probe("Android provider lazily restores synced metadata-only photo from isolated Drive",()->{try(Setup s=new Setup(qa)){
            byte[] bytes=android.util.Base64.decode(PixelMcpInstrumentation.VALID_GIF.substring(PixelMcpInstrumentation.VALID_GIF.indexOf(',')+1),android.util.Base64.DEFAULT);String hash=PhotoAttachmentStore.sha256(bytes),name=hash+".gif";
            String folder=s.provider.add("root",DocumentsContract.Document.MIME_TYPE_DIR,"HausGarten-Fotos",new byte[0]);s.provider.add(folder,"image/gif",name,bytes);
            JSONObject snapshot=qa.fixture().put("photos",new JSONArray().put(new JSONObject().put("id","qa_remote_photo").put("customer_id","qa_c1").put("original_name","Remote.gif").put("filename",name).put("storage_name",name).put("content_sha256",hash).put("mime_type","image/gif").put("byte_size",bytes.length).put("role","documentation")));
            s.provider.replace("doc1",snapshot.toString().getBytes(StandardCharsets.UTF_8));qa.yes(s.sync().getBoolean("ok"));qa.yes(s.store.pendingActions().length()==0&&s.store.loadPhotoData("qa_remote_photo").equals(PixelMcpInstrumentation.VALID_GIF));
        }});
    }
}
