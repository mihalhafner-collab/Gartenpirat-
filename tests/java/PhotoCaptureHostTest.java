package at.hausgarten.service;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Host logic/filesystem regression, not an Android permission or hardware test. */
public final class PhotoCaptureHostTest {
    private static Path root;
    private static int cases;
    interface Checked { void run() throws Exception; }
    private static void require(boolean value,String label) {
        if(!value)throw new AssertionError(label);
    }
    private static void test(String label,Checked body)throws Exception {
        Toast.messages.clear();Log.messages.clear();
        body.run();cases++;System.out.println("PASS "+label);
    }
    private static MainActivity fresh()throws Exception {
        return new MainActivity(Files.createTempDirectory(root,"case-").toFile());
    }
    private static void reject(Checked action)throws Exception {
        try{action.run();}catch(FileNotFoundException expected){return;}
        throw new AssertionError("Expected a rejected capture file/URI");
    }
    private static void failure(MainActivity app,String code)throws Exception {
        require(!app.startForTest(),"Failure must not claim camera launch success");
        require(app.pendingCaptureFile==null&&app.pendingCaptureUri==null,"Clear failed capture state");
        require(Toast.messages.size()==1,"Exactly one useful error message");
        require(Log.messages.stream().anyMatch(v->v.contains(code)),"Diagnostic code "+code);
        require(Log.messages.stream().noneMatch(v->v.contains("PRIVATE_EXCEPTION_DETAIL")),"Do not log private exception text");
        File[] files=new File(app.cache,"photo-capture").listFiles();
        require(files==null||files.length==0,"No failed-capture temporary-file leak");
    }
    private static Uri galleryUri(String name) {
        return new Uri.Builder().scheme("content").authority("test.gallery").appendPath(name).build();
    }
    private static final class Result implements android.webkit.ValueCallback<Uri[]> {
        int calls;Uri[] value;
        public void onReceiveValue(Uri[] result){calls++;value=result;}
    }
    public static void main(String[] args)throws Exception { SyntheticBusinessData.verify();
        root=Files.createDirectories(Path.of(args[0]));
        test("normal cache target and content URI",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);
            Uri uri=PhotoCaptureProvider.uri(app,file);
            require(file.isFile()&&file.getName().matches("capture-[0-9a-f]{32}\\.jpg"),"Prepared unique JPEG target");
            require("content".equals(uri.getScheme()),"Content URI, not a file URI");
            require((app.getPackageName()+".photocapture").equals(uri.getAuthority()),"Correct QA authority");
        });
        test("aliased Android-style cache path",()->{
            MainActivity real=fresh();Path alias=real.cache.toPath().resolveSibling(real.cache.getName()+"-alias");
            Files.createSymbolicLink(alias,real.cache.toPath());
            MainActivity app=new MainActivity(alias.toFile());File file=PhotoCaptureProvider.createTarget(app);
            require(PhotoCaptureProvider.uri(app,file).equals(PhotoCaptureProvider.uri(app,file.getCanonicalFile())),"Alias and canonical path identify the same capture");
        });
        test("canonical context accepts alias to same target",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);
            Path alias=app.cache.toPath().resolveSibling(app.cache.getName()+"-alias");Files.createSymbolicLink(alias,app.cache.toPath());
            File aliased=alias.resolve("photo-capture").resolve(file.getName()).toFile();
            require(PhotoCaptureProvider.uri(app,file).equals(PhotoCaptureProvider.uri(app,aliased)),"Canonical equality in both directions");
        });
        test("normalised dot path remains inside capture directory",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);
            File dotted=new File(file.getParentFile(),"../photo-capture/"+file.getName());
            require(PhotoCaptureProvider.uri(app,file).equals(PhotoCaptureProvider.uri(app,dotted)),"Normalised same file");
        });
        test("same basename outside capture directory rejected",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);
            File outside=new File(app.cache,file.getName());Files.write(outside.toPath(),new byte[]{7});
            reject(()->PhotoCaptureProvider.uri(app,outside));require(outside.isFile(),"Outside data unchanged");
        });
        test("symlink escape rejected without touching destination",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);
            Path outside=app.cache.toPath().resolve("synthetic-private-record");Files.write(outside,new byte[]{7});
            Files.delete(file.toPath());Files.createSymbolicLink(file.toPath(),outside);
            reject(()->PhotoCaptureProvider.uri(app,file));require(Files.readAllBytes(outside)[0]==7,"Escaped target untouched");
        });
        test("invalid capture filename rejected",()->{
            MainActivity app=fresh();File invalid=new File(app.cache,"arbitrary.jpg");Files.write(invalid.toPath(),new byte[]{7});
            reject(()->PhotoCaptureProvider.uri(app,invalid));
        });
        test("missing capture file rejected",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);Files.delete(file.toPath());
            reject(()->PhotoCaptureProvider.uri(app,file));
        });
        test("directory is not an image target",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);Files.delete(file.toPath());Files.createDirectory(file.toPath());
            reject(()->PhotoCaptureProvider.uri(app,file));
        });
        test("null target becomes a checked preparation failure",()->reject(()->PhotoCaptureProvider.uri(fresh(),null)));
        test("provider still rejects foreign scheme authority and path",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);
            PhotoCaptureProvider provider=new PhotoCaptureProvider();provider.attachForTest(app);
            reject(()->provider.openFile(new Uri.Builder().scheme("file").authority(app.getPackageName()+".photocapture").appendPath(file.getName()).build(),"r"));
            reject(()->provider.openFile(new Uri.Builder().scheme("content").authority("foreign").appendPath(file.getName()).build(),"r"));
            reject(()->provider.openFile(new Uri.Builder().scheme("content").authority(app.getPackageName()+".photocapture").appendPath("..").appendPath(file.getName()).build(),"r"));
        });
        test("launch supplies output ClipData and temporary read/write flags",()->{
            MainActivity app=fresh();require(app.startForTest(),"Camera launch accepted by fake boundary");
            require(app.launchCount==1&&MediaStore.ACTION_IMAGE_CAPTURE.equals(app.launched.action),"Exactly one image-capture intent");
            require(app.pendingCaptureUri.equals(app.launched.extras.get(MediaStore.EXTRA_OUTPUT)),"Output target");
            require(app.pendingCaptureUri.equals(app.launched.getClipData().getItemAt(0).getUri()),"ClipData URI grant target");
            require(app.launched.flags==3&&Toast.messages.isEmpty(),"Temporary read/write grants, no false error");
        });
        test("launch through directory alias succeeds",()->{
            MainActivity real=fresh();Path alias=real.cache.toPath().resolveSibling(real.cache.getName()+"-launch-alias");Files.createSymbolicLink(alias,real.cache.toPath());
            require(new MainActivity(alias.toFile()).startForTest(),"Regression: directory aliases must not masquerade as missing camera");
        });
        test("storage failure is not reported as missing camera",()->{
            MainActivity real=fresh();Path notDirectory=real.cache.toPath().resolve("file");Files.write(notDirectory,new byte[]{1});
            MainActivity app=new MainActivity(notDirectory.toFile());failure(app,"PREPARE_IO");
            require(app.launchCount==0&&Toast.messages.get(0).contains("Fotodatei"),"Correct storage diagnosis");
        });
        test("preparation permission denial has separate diagnosis",()->{
            MainActivity app=fresh();app.cacheError=new SecurityException("PRIVATE_EXCEPTION_DETAIL");failure(app,"PREPARE_DENIED");
            require(app.launchCount==0,"No launch after preparation denial");
        });
        test("unexpected preparation failure has separate diagnosis",()->{
            MainActivity app=fresh();app.cacheError=new IllegalStateException("PRIVATE_EXCEPTION_DETAIL");failure(app,"PREPARE_FAILED");
        });
        test("URI preparation failure also removes already-created target",()->{
            MainActivity app=fresh();app.alternateCache=Files.createTempDirectory(root,"alternate-").toFile();app.alternateAfter=1; // Switch after target creation, before its URI is checked.
            failure(app,"PREPARE_IO");require(app.launchCount==0,"Do not launch with an invalid URI");
        });
        test("missing camera is specific and retry remains possible",()->{
            MainActivity app=fresh();app.launchError=new ActivityNotFoundException();failure(app,"CAMERA_MISSING");
            require(Toast.messages.get(0).contains("Keine Kamera-App"),"Missing handler diagnosis");
            app.launchError=null;require(app.startForTest(),"Retry after failed launch");
        });
        test("blocked camera launch is not reported as absent app",()->{
            MainActivity app=fresh();app.launchError=new SecurityException("PRIVATE_EXCEPTION_DETAIL");failure(app,"LAUNCH_DENIED");
            require(!Toast.messages.get(0).contains("Keine Kamera-App"),"Security failure is not handler absence");
        });
        test("unexpected camera launch failure preserves cause category",()->{
            MainActivity app=fresh();app.launchError=new IllegalStateException("PRIVATE_EXCEPTION_DETAIL");failure(app,"LAUNCH_FAILED");
        });
        test("grant revocation failure cannot prevent target cleanup",()->{
            MainActivity app=fresh();app.launchError=new ActivityNotFoundException();app.revokeError=new SecurityException("PRIVATE_EXCEPTION_DETAIL");
            failure(app,"CAMERA_MISSING");require(Log.messages.stream().anyMatch(v->v.contains("REVOKE_FAILED")),"Cleanup failure diagnostic");
        });
        test("successful capture with null result Intent retains file for WebView",()->{
            MainActivity app=fresh();Result result=new Result();app.fileCallback=result;require(app.startForTest(),"Launch");
            File captured=app.pendingCaptureFile;Uri uri=app.pendingCaptureUri;Files.write(captured.toPath(),new byte[]{1,2,3});
            app.resultForTest(MainActivity.RESULT_OK,null);
            require(result.calls==1&&result.value.length==1&&uri.equals(result.value[0]),"Capture URI delivered once");
            require(captured.isFile()&&app.pendingCaptureFile==null&&app.pendingCaptureUri==null,"No early deletion before WebView reads");
            require(uri.equals(app.revoked)&&app.revokedFlags==3,"Camera grants revoked");
            require(app.syncHandler.delays.isEmpty(),"Unsaved camera draft has no age-based deletion");
        });
        test("cancellation clears only the temporary capture",()->{
            MainActivity app=fresh();Result result=new Result();app.fileCallback=result;
            Path sentinel=app.cache.toPath().resolve("synthetic-database");Files.write(sentinel,new byte[]{8});require(app.startForTest(),"Launch");File file=app.pendingCaptureFile;
            app.resultForTest(0,null);
            require(result.calls==1&&result.value==null&&!file.exists(),"Cancellation callback and cleanup");
            require(Files.readAllBytes(sentinel)[0]==8&&Toast.messages.isEmpty(),"Other data preserved, cancellation is not an error");
        });
        test("empty successful camera response is rejected",()->{
            MainActivity app=fresh();Result result=new Result();app.fileCallback=result;require(app.startForTest(),"Launch");File file=app.pendingCaptureFile;
            app.resultForTest(MainActivity.RESULT_OK,null);
            require(result.calls==1&&result.value==null&&!file.exists()&&Toast.messages.size()==1,"Reject empty capture");
        });
        test("oversized capture is rejected before WebView hand-off",()->{
            MainActivity app=fresh();Result result=new Result();app.fileCallback=result;require(app.startForTest(),"Launch");File file=app.pendingCaptureFile;
            try(RandomAccessFile large=new RandomAccessFile(file,"rw")){large.setLength(PhotoAttachmentStore.MAX_BYTES+1);}
            app.resultForTest(MainActivity.RESULT_OK,null);
            require(result.value==null&&!file.exists()&&Toast.messages.size()==1,"Reject oversized capture");
        });
        test("existing single gallery selection preserved",()->{
            MainActivity app=fresh();Result result=new Result();app.fileCallback=result;Uri uri=galleryUri("single");
            app.resultForTest(MainActivity.RESULT_OK,new Intent().setData(uri));
            require(result.calls==1&&uri.equals(result.value[0]),"Single selection delivered");
        });
        test("existing multiple gallery selection preserved",()->{
            MainActivity app=fresh();Result result=new Result();app.fileCallback=result;Uri first=galleryUri("first"),second=galleryUri("second");
            ClipData clip=ClipData.newRawUri("test",first);clip.addItem(new ClipData.Item(null));clip.addItem(new ClipData.Item(second));
            app.resultForTest(MainActivity.RESULT_OK,new Intent().setClipData(clip));
            require(result.calls==1&&Arrays.equals(result.value,new Uri[]{first,second}),"Multiple selection and null filtering preserved");
        });
        test("completed callback cannot erase a newly registered callback",()->{
            MainActivity app=fresh();Result next=new Result();int[] firstCalls={0};
            app.fileCallback=value->{firstCalls[0]++;app.fileCallback=next;};
            app.resultForTest(MainActivity.RESULT_OK,new Intent().setData(galleryUri("first")));
            require(firstCalls[0]==1&&app.fileCallback==next,"Preserve reentrant callback registration");
            app.resultForTest(MainActivity.RESULT_OK,new Intent().setData(galleryUri("second")));
            require(next.calls==1&&app.fileCallback==null,"Next callback delivered once");
        });
        test("old camera drafts survive cleanup startup and later preparation",()->{
            MainActivity app=fresh();File old=PhotoCaptureProvider.createTarget(app),recent=PhotoCaptureProvider.createTarget(app);
            Path unrelated=new File(old.getParentFile(),"synthetic-record.db").toPath();Files.write(unrelated,new byte[]{4});
            Files.write(old.toPath(),new byte[]{7,8,9});
            require(old.setLastModified(System.currentTimeMillis()-48L*60L*60L*1000L),"Set old synthetic timestamp");
            PhotoCaptureProvider.cleanupStale(app);
            PhotoCaptureProvider provider=new PhotoCaptureProvider();provider.attachForTest(app);require(provider.onCreate(),"Provider startup remains harmless");PhotoCaptureProvider.createTarget(app);
            require(old.exists()&&recent.exists()&&Files.exists(unrelated)&&Arrays.equals(Files.readAllBytes(old.toPath()),new byte[]{7,8,9}),"Age alone must not delete a restored or interrupted capture");
        });
        test("restore gate blocks camera URI read write metadata and target creation before file access",()->{
            MainActivity app=fresh();File file=PhotoCaptureProvider.createTarget(app);Files.write(file.toPath(),new byte[]{7,8,9});Uri uri=PhotoCaptureProvider.uri(app,file);PhotoCaptureProvider provider=new PhotoCaptureProvider();provider.attachForTest(app);LocalAuthorityGuard.blocked=app;
            try{require(provider.onCreate(),"Recovery provider startup stays available");for(String mode:new String[]{"r","w","wt","rw","rwt"}){try{provider.openFile(uri,mode);throw new AssertionError("Restore file mode allowed: "+mode);}catch(IllegalStateException expected){require(expected.getMessage().equals("RESTORE_RESTART_REQUIRED"),"Fixed restore code");}}
                for(Checked action:new Checked[]{()->PhotoCaptureProvider.createTarget(app),()->PhotoCaptureProvider.uri(app,file),()->provider.query(uri,null,null,null,null),()->provider.getType(uri),()->PhotoCaptureProvider.cleanupStale(app)}){try{action.run();throw new AssertionError("Restore photo operation allowed");}catch(IllegalStateException expected){require(expected.getMessage().equals("RESTORE_RESTART_REQUIRED"),"Fixed restore code");}}
                require(Arrays.equals(Files.readAllBytes(file.toPath()),new byte[]{7,8,9}),"Blocked modes do not truncate the draft");
            }finally{LocalAuthorityGuard.blocked=null;}
        });
        System.out.println("PASS "+cases+" host camera/provider cases; Android device tests NOT RUN.");
    }
}
