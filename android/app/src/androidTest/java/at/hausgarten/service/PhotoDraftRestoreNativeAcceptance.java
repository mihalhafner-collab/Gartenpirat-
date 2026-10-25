package at.hausgarten.service;

import android.content.pm.ProviderInfo;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Only disposable synthetic capture/provider paths; never a registered production URI. */
final class PhotoDraftRestoreNativeAcceptance {
    private static void blocked(PixelMcpInstrumentation test,PixelMcpInstrumentation.Task task)throws Exception {
        try{task.run();throw new AssertionError("Expected restore photo fence");}catch(IllegalStateException expected){test.yes("RESTORE_RESTART_REQUIRED".equals(expected.getMessage()));}
    }
    private static PhotoCaptureProvider provider(PixelMcpInstrumentation.Isolated context){PhotoCaptureProvider provider=new PhotoCaptureProvider();ProviderInfo info=new ProviderInfo();info.authority=context.getPackageName()+".photocapture";info.exported=false;info.grantUriPermissions=true;provider.attachInfo(context,info);return provider;}
    private static String payload()throws Exception {return new JSONObject().put("id","qa_photo_draft_restore").put("customer_id","qa_c").put("worklog_id","qa_w").put("data","data:image/jpeg;base64,/9j/").toString();}
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("legacy pending work photo rejects overwrite and clears only a resolved action without deleting saved work",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();test.yes(image.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out));image.recycle();JSONObject original=new JSONObject(payload()).put("_photo_request_id","qa_legacy_photo_retry").put("data","data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));PendingWorkPhotoStore pending=new PendingWorkPhotoStore(f.context);String staged=pending.stage(original.toString());BusinessDataBridge bridge=new BusinessDataBridge(f.context,f.store,pending,()->{});
            test.yes(bridge.clearPendingWorkPhoto().startsWith("ERROR:")&&pending.read().equals(staged));try{pending.stage(new JSONObject(staged).put("id","qa_other_pending_photo").toString());throw new AssertionError("QA_PENDING_PHOTO_OVERWRITTEN");}catch(IllegalStateException expected){test.yes(pending.read().equals(staged));}
            String photo=bridge.savePhoto(staged);test.yes(photo.equals(original.getString("id")));test.yes(bridge.clearPendingWorkPhoto().equals("OK")&&pending.read().isEmpty());test.yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("worklogs"),"qa_w").getString("id").equals("qa_w"));test.yes(f.store.photoAttachment(photo)!=null);
            String rejected=pending.stage(new JSONObject(original.toString()).put("id","qa_legacy_rejected_photo").put("_photo_request_id","qa_legacy_photo_rejected").put("customer_id","qa_missing").toString());test.yes(bridge.savePhoto(rejected).startsWith("ERROR: NOT_COMMITTED:")&&bridge.clearPendingWorkPhoto().equals("OK"));
        }});
        test.probe("old restored camera captures survive provider startup and later camera preparation",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            File old=PhotoCaptureProvider.createTarget(f.context);byte[] bytes="QA preserved camera draft".getBytes(StandardCharsets.US_ASCII);Files.write(old.toPath(),bytes);test.yes(old.setLastModified(System.currentTimeMillis()-48L*60*60*1000));Uri uri=PhotoCaptureProvider.uri(f.context,old);PhotoCaptureProvider provider=provider(f.context);test.yes(provider.onCreate());PhotoCaptureProvider.cleanupStale(f.context);File next=PhotoCaptureProvider.createTarget(f.context);test.yes(next.isFile()&&old.isFile()&&Arrays.equals(bytes,Files.readAllBytes(old.toPath())));try(android.os.ParcelFileDescriptor read=provider.openFile(uri,"r")){test.yes(read.getStatSize()==bytes.length);}
        }});
        test.probe("pending and failed-hold restore fence all camera URI and pending-photo access without changing drafts",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            File capture=PhotoCaptureProvider.createTarget(f.context);byte[] before="QA capture must not truncate".getBytes(StandardCharsets.US_ASCII);Files.write(capture.toPath(),before);Uri uri=PhotoCaptureProvider.uri(f.context,capture);PhotoCaptureProvider provider=provider(f.context);PendingWorkPhotoStore pending=new PendingWorkPhotoStore(f.context);String staged=pending.stage(payload());File pendingFile=new File(f.context.getFilesDir(),"pending-work-photo.json");String pendingSha=EncryptedBackupArchive.sha256(pendingFile);LocalAuthorityGuard.markRestorePending(f.context,"qa_photo_restore_fence");
            for(int phase=0;phase<2;phase++){
                test.yes(provider.onCreate());for(String mode:new String[]{"r","w","wt","rw","rwt"})blocked(test,()->{try(android.os.ParcelFileDescriptor ignored=provider.openFile(uri,mode)){};});blocked(test,()->provider.query(uri,null,null,null,null));blocked(test,()->provider.getType(uri));blocked(test,()->PhotoCaptureProvider.createTarget(f.context));blocked(test,()->PhotoCaptureProvider.uri(f.context,capture));blocked(test,()->PhotoCaptureProvider.cleanupStale(f.context));blocked(test,()->new PendingWorkPhotoStore(f.context));blocked(test,()->pending.stage(payload()));blocked(test,()->pending.read());blocked(test,()->pending.clear());test.yes(Arrays.equals(before,Files.readAllBytes(capture.toPath()))&&pendingSha.equals(EncryptedBackupArchive.sha256(pendingFile)));
                if(phase==0){LocalAuthorityGuard.clearRestorePending(f.context);File activation=new File(f.context.getNoBackupFilesDir(),"full-restore/activation.json");test.yes(activation.getParentFile().isDirectory()||activation.getParentFile().mkdirs());Files.write(activation.toPath(),new JSONObject().put("formatVersion",1).put("phase","FAILED_HOLD").toString().getBytes(StandardCharsets.UTF_8));test.yes(BackupRestoreService.requiresExclusiveStartup(f.context));}
            }
            test.yes(staged.contains("qa_photo_draft_restore"));
        }});
        test.probe("pending-photo AtomicFile backup is recovered after interrupted write instead of hidden",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            PendingWorkPhotoStore pending=new PendingWorkPhotoStore(f.context);String staged=pending.stage(payload());File base=new File(f.context.getFilesDir(),"pending-work-photo.json"),prior=new File(base+".bak");test.yes(base.renameTo(prior));test.yes(prior.setLastModified(System.currentTimeMillis()-48L*60*60*1000));test.yes(new PendingWorkPhotoStore(f.context).read().equals(staged));test.yes(base.isFile()&&Arrays.equals(staged.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(base.toPath())));
        }});
    }
}
