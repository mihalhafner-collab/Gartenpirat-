package at.hausgarten.service;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Executes the production cleanup coordinator with explicit synthetic I/O doubles.
 * Does not test Android SQLite locks, document providers or actual cloud deletion.
 */
public final class TrashMediaCleanupTests {
    static int checks;
    static void yes(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static JSONObject photo(String id,int index,boolean thumbnail)throws Exception {
        String hash=String.format(Locale.ROOT,"%064x",index+1);
        JSONObject media=SyntheticBusinessData.object().put("storage_name",hash+".jpg").put("content_sha256",hash);
        if(thumbnail)media.put("thumbnail_storage_name",hash+".thumb.jpg").put("thumbnail_sha256",hash);
        return SyntheticBusinessData.object().put("purged",true).put("purge_action","qa_purge_"+id).put("media",media);
    }
    static PixelLocalStore store()throws Exception {return new PixelLocalStore();}
    static JSONObject archive(PixelLocalStore store)throws Exception {return OwnerJournalRules.archives(store.master);}
    static void retainAll(PixelLocalStore store,DriveSyncCoordinator.Files files)throws Exception {
        JSONObject before=SyntheticBusinessData.object(store.master.toString());
        JSONObject result=TrashMediaCleanup.run(store,files,store.master);
        yes(result.getInt("pending")==0,"shared cleanup completes without removing shared files");
        yes(files.deleted.isEmpty(),"referenced remote bytes are retained");
        yes(store.deletedLocal.isEmpty(),"referenced local bytes are retained");
        yes(PixelMcpWriteTools.hash(before).equals(PixelMcpWriteTools.hash(store.master)),"cleanup never mutates business records");
    }
    public static void main(String[] ignored)throws Exception {
        SyntheticBusinessData.verify();
        PixelLocalStore regular=store();DriveSyncCoordinator.Files files=new DriveSyncCoordinator.Files();
        JSONObject p=photo("qa_photo",1,true);archive(regular).put("photo:qa_photo",p);
        JSONObject result=TrashMediaCleanup.run(regular,files,regular.master);
        yes(result.getInt("completed")==1&&result.getInt("pending")==0,"one photo finishes");
        yes(files.deleted.size()==2&&regular.deletedLocal.size()==2,"original and thumbnail reclaimed");
        int calls=files.calls;yes(TrashMediaCleanup.run(regular,files,regular.master).getInt("completed")==0,"completed receipt survives another pass");
        yes(files.calls==calls,"completed receipt does no further I/O");
        PixelLocalStore notPurged=store();archive(notPurged).put("photo:qa_archived",SyntheticBusinessData.object().put("record",TrashWorkflowTests.photo("qa_archived")));
        DriveSyncCoordinator.Files untouched=new DriveSyncCoordinator.Files();TrashMediaCleanup.run(notPurged,untouched,notPurged.master);
        yes(untouched.calls==0&&notPurged.markers.isEmpty(),"recoverable trash retains every byte");
        // Live, archived and queued references each protect originals and thumbnails.
        for(int mode=0;mode<3;mode++){
            PixelLocalStore shared=store();JSONObject tomb=photo("qa_shared",2,true),media=tomb.getJSONObject("media");archive(shared).put("photo:qa_shared",tomb);
            JSONObject reference=SyntheticBusinessData.object(media.toString()).put("id","qa_reference");
            if(mode==0)shared.master.getJSONArray("photos").put(reference);
            if(mode==1)archive(shared).put("photo:qa_reference",SyntheticBusinessData.object().put("record",reference));
            if(mode==2)shared.pending.put(OffersAndPhotosTests.event("qa_queued_reference","qa_unsupported_proposal",SyntheticBusinessData.object().put("nested",reference)));
            retainAll(shared,new DriveSyncCoordinator.Files());
        }
        PixelLocalStore attachment=store();JSONObject shared=photo("qa_attachment",3,false);archive(attachment).put("photo:qa_attachment",shared);
        attachment.otherAttachments.add(shared.getJSONObject("media").getString("content_sha256"));retainAll(attachment,new DriveSyncCoordinator.Files());
        // Provider permission/CAS failures preserve local bytes and the retry marker.
        for(String failure:new String[]{"PERMISSION_REQUIRED","WRITE_CONFLICT"}){
            PixelLocalStore retry=store();archive(retry).put("photo:qa_retry",photo("qa_retry",4,true));DriveSyncCoordinator.Files remote=new DriveSyncCoordinator.Files();remote.failure=failure;
            JSONObject failed=TrashMediaCleanup.run(retry,remote,retry.master);
            yes(failed.getInt("pending")==1&&failed.getInt("completed")==0,"failed cleanup remains pending");
            yes(failed.getString("error_code").equals(failure),"failure stays distinguishable");
            yes(retry.markers.isEmpty()&&retry.deletedLocal.isEmpty(),"failed remote delete never acknowledges local cleanup");
            remote.failure="";yes(TrashMediaCleanup.run(retry,remote,retry.master).getInt("pending")==0,"retry can finish");
            yes(remote.deleted.size()==2&&retry.deletedLocal.size()==2,"retry reclaims both files once");
        }
        // A crash after remote deletion but before the local marker is safely repeatable.
        PixelLocalStore restart=store();archive(restart).put("photo:qa_restart",photo("qa_restart",5,true));restart.failLocal=true;
        DriveSyncCoordinator.Files remote=new DriveSyncCoordinator.Files();
        yes(TrashMediaCleanup.run(restart,remote,restart.master).getInt("pending")==1,"local commit failure remains pending");
        yes(restart.markers.isEmpty()&&restart.deletedLocal.isEmpty(),"failed local commit writes no marker");
        restart.failLocal=false;yes(TrashMediaCleanup.run(restart,remote,restart.master).getInt("pending")==0,"restart resumes cleanup");
        yes(remote.deleted.size()==2,"verified remote delete is idempotent");
        // Snapshot/read errors fail closed before any remote/local file deletion.
        PixelLocalStore unreadable=store();archive(unreadable).put("photo:qa_read_error",photo("qa_read_error",6,true));unreadable.failRead=true;
        DriveSyncCoordinator.Files noRead=new DriveSyncCoordinator.Files();
        yes(TrashMediaCleanup.run(unreadable,noRead,unreadable.master).getInt("pending")==1,"unreadable current state remains pending");
        yes(noRead.calls==0&&unreadable.markers.isEmpty(),"read failure cannot remove files");
        // Four completed records per pass; remaining tombstones are never lost.
        PixelLocalStore bounded=store();for(int i=0;i<9;i++)archive(bounded).put("photo:qa_bound_"+i,photo("qa_bound_"+i,10+i,false));
        DriveSyncCoordinator.Files batch=new DriveSyncCoordinator.Files();
        for(int remaining:new int[]{5,1,0}){
            JSONObject pass=TrashMediaCleanup.run(bounded,batch,bounded.master);
            yes(pass.getInt("completed")<=4&&pass.getInt("pending")==remaining,"bounded pass reports the remaining count");
        }
        yes(bounded.markers.size()==9&&batch.deleted.size()==9,"every bounded file eventually completes once");
        // PDF cleanup has the same shared-reference and retry rules.
        for(boolean referenced:new boolean[]{false,true}){
            PixelLocalStore documents=store();String name="a".repeat(64)+".pdf";
            JSONObject document=SyntheticBusinessData.object().put("pdf_storage_name",name).put("pdf_sha256","a".repeat(64));
            archive(documents).put("offer:qa_pdf",SyntheticBusinessData.object().put("purged",true).put("purge_action","qa_pdf_purge").put("document",document));
            if(referenced)documents.master.getJSONArray("invoices").put(SyntheticBusinessData.object().put("id","qa_kept_pdf").put("pdf_storage_name",name));
            DriveSyncCoordinator.Files pdfs=new DriveSyncCoordinator.Files();
            yes(TrashMediaCleanup.run(documents,pdfs,documents.master).getInt("completed")==1,"PDF cleanup receipt completes");
            yes(pdfs.deleted.contains(name)==!referenced&&documents.deletedLocal.contains(name)==!referenced,"shared PDF is preserved");
            yes(TrashMediaCleanup.run(documents,pdfs,documents.master).getInt("completed")==0,"PDF cleanup marker is idempotent");
        }
        System.out.println("PASS "+checks+" production cleanup scheduling/reference/retry/idempotence checks; synthetic I/O, no Android SQLite/provider execution");
    }
}

/** Explicit effects double; the production SQLite implementation is NOT exercised. */
final class PixelLocalStore {
    final JSONObject master;final JSONArray pending=new JSONArray();final Map<String,String> markers=new HashMap<>();
    final Set<String> otherAttachments=new HashSet<>(),deletedLocal=new HashSet<>();boolean failLocal,failRead;
    PixelLocalStore()throws Exception{master=TrashPurgeBatchTests.state();}
    String ownerValue(String key,String fallback){return markers.getOrDefault(key,fallback);}
    JSONObject getState()throws Exception{if(failRead)throw new IOException("QA current state unavailable");return SyntheticBusinessData.object().put("master",master).put("pending",pending);}
    JSONArray pendingActions(){return pending;}
    boolean hasOtherPhotoAttachment(String id,String hash){return otherAttachments.contains(hash);}
    void cleanupPurgedDocument(String key,JSONObject document,String marker)throws Exception{
        if(failLocal)throw new IOException("QA local commit failed");
        if(!TrashRules.referenced(master,pending,document.optString("pdf_storage_name")))deletedLocal.add(document.getString("pdf_storage_name"));
        markers.put(marker,"done");
    }
    void cleanupPurgedPhoto(String id,JSONObject media,String marker)throws Exception{
        if(failLocal)throw new IOException("QA local commit failed");
        String original=media.getString("storage_name"),thumb=media.optString("thumbnail_storage_name");
        if(!hasOtherPhotoAttachment(id,media.getString("content_sha256"))&&!TrashRules.referenced(master,pending,original))deletedLocal.add(original);
        if(!thumb.isEmpty()&&!TrashRules.referenced(master,pending,thumb))deletedLocal.add(thumb);
        markers.put(marker,"done");
    }
}

/** No network, credentials, Android provider or filesystem deletion. */
final class DriveSyncCoordinator {
    static String failureCode(Exception error){String message=error.getMessage();return Arrays.asList("PERMISSION_REQUIRED","WRITE_CONFLICT").contains(message)?message:"TRANSFER_FAILED";}
    static final class Files {
        final Set<String> deleted=new HashSet<>();String failure="";int calls;
        void deletePhotoFileVerified(String name,String hash)throws Exception{calls++;if(!failure.isEmpty())throw new IOException(failure);deleted.add(name);}
        void deleteDocumentFileVerified(JSONObject document)throws Exception{deletePhotoFileVerified(document.getString("pdf_storage_name"),document.getString("pdf_sha256"));}
    }
}
