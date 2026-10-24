#!/usr/bin/env python3
"""Execute camera regression logic on a host JDK, without Android or business data.

Compiles the real PhotoCaptureProvider and extracts the real camera/result methods
from MainActivity. Android boundary classes are deliberately small test doubles.
Filesystem/symlink operations are real. Intent delivery, permissions, WebView and
camera hardware are NOT Android/device acceptance tests. Requires Python 3 + JDK 17
and the same pinned HG_JSON_JAR dependency as tools/run-followup-java.py.
Run from any working directory: python3 tests/run_photo_capture_tests.py
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import sys
import os
import hashlib

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
JAVA = ROOT / "android/app/src/main/java/at/hausgarten/service"

STUBS = {
    "android/content/Context.java": '''package android.content;
public abstract class Context {
 public abstract java.io.File getCacheDir();
 public String getPackageName(){return "at.hausgarten.service.qa";}
}''',
    "android/content/ContentValues.java": 'package android.content; public class ContentValues {}',
    "android/database/Cursor.java": 'package android.database; public interface Cursor {}',
    "android/database/MatrixCursor.java": '''package android.database;
public class MatrixCursor implements Cursor {
 public MatrixCursor(String[] columns){} public void addRow(Object[] row){}
}''',
    "android/content/ContentProvider.java": '''package android.content;
import android.database.Cursor;import android.net.Uri;import android.os.ParcelFileDescriptor;
public abstract class ContentProvider {
 private Context context; public Context getContext(){return context;}
 public void attachForTest(Context value){context=value;}
 public abstract boolean onCreate(); public abstract String getType(Uri uri);
 public abstract ParcelFileDescriptor openFile(Uri uri,String mode)throws java.io.FileNotFoundException;
 public abstract Cursor query(Uri uri,String[] projection,String selection,String[] args,String order);
 public abstract Uri insert(Uri uri,ContentValues values);
 public abstract int update(Uri uri,ContentValues values,String selection,String[] args);
 public abstract int delete(Uri uri,String selection,String[] args);
}''',
    "android/net/Uri.java": '''package android.net;
import java.util.*;
public final class Uri {
 private final String scheme,authority;private final List<String> paths;
 private Uri(String s,String a,List<String> p){scheme=s;authority=a;paths=new ArrayList<>(p);}
 public String getScheme(){return scheme;} public String getAuthority(){return authority;}
 public List<String> getPathSegments(){return Collections.unmodifiableList(paths);}
 public String getLastPathSegment(){return paths.isEmpty()?null:paths.get(paths.size()-1);}
 public String toString(){return scheme+"://"+authority+"/"+String.join("/",paths);}
 public boolean equals(Object other){return other instanceof Uri&&toString().equals(other.toString());}
 public int hashCode(){return toString().hashCode();}
 public static class Builder {
  private String scheme,authority;private final List<String> paths=new ArrayList<>();
  public Builder scheme(String v){scheme=v;return this;} public Builder authority(String v){authority=v;return this;}
  public Builder appendPath(String v){paths.add(v);return this;} public Uri build(){return new Uri(scheme,authority,paths);}
 }
}''',
    "android/os/ParcelFileDescriptor.java": '''package android.os;
public class ParcelFileDescriptor {
 public static final int MODE_READ_ONLY=1,MODE_WRITE_ONLY=2,MODE_READ_WRITE=3,MODE_TRUNCATE=4;
 public static ParcelFileDescriptor open(java.io.File file,int mode)throws java.io.FileNotFoundException {
  if(!file.isFile())throw new java.io.FileNotFoundException();return new ParcelFileDescriptor();
 }
}''',
    "android/provider/OpenableColumns.java": '''package android.provider;
public interface OpenableColumns {String DISPLAY_NAME="_display_name",SIZE="_size";}''',
    "android/provider/MediaStore.java": '''package android.provider;
public class MediaStore {public static final String ACTION_IMAGE_CAPTURE="android.media.action.IMAGE_CAPTURE",EXTRA_OUTPUT="output";}''',
    "android/content/ActivityNotFoundException.java": '''package android.content;
public class ActivityNotFoundException extends RuntimeException {public ActivityNotFoundException(){super();}}''',
    "android/content/Intent.java": '''package android.content;
import android.net.Uri;import java.util.*;
public class Intent {
 public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_GRANT_WRITE_URI_PERMISSION=2;
 public String action; public final Map<String,Object> extras=new HashMap<>();public int flags;
 private ClipData clip;private Uri data;
 public Intent(){this("");} public Intent(String value){action=value;}
 public Intent putExtra(String key,Uri value){extras.put(key,value);return this;}
 public Intent setClipData(ClipData value){clip=value;return this;} public ClipData getClipData(){return clip;}
 public Intent addFlags(int value){flags|=value;return this;}
 public Intent setData(Uri value){data=value;return this;} public Uri getData(){return data;}
}''',
    "android/content/ClipData.java": '''package android.content;
import android.net.Uri;import java.util.*;
public class ClipData {
 private final List<Item> items=new ArrayList<>();
 public static ClipData newRawUri(String label,Uri uri){ClipData d=new ClipData();d.addItem(new Item(uri));return d;}
 public void addItem(Item i){items.add(i);} public int getItemCount(){return items.size();}
 public Item getItemAt(int i){return items.get(i);}
 public static class Item {private final Uri uri;public Item(Uri value){uri=value;}public Uri getUri(){return uri;}}
}''',
    "android/webkit/ValueCallback.java": 'package android.webkit; public interface ValueCallback<T>{void onReceiveValue(T value);}',
    "android/util/Log.java": '''package android.util;
import java.util.*;
public class Log {
 public static final List<String> messages=new ArrayList<>();
 public static int e(String tag,String message){messages.add(tag+":"+message);return 0;}
 public static int w(String tag,String message){messages.add(tag+":"+message);return 0;}
}''',
    "android/widget/Toast.java": '''package android.widget;
import android.content.Context;import java.util.*;
public class Toast {
 public static final int LENGTH_LONG=1; public static final List<String> messages=new ArrayList<>();
 private final String text;private Toast(String v){text=v;}
 public static Toast makeText(Context c,String text,int duration){return new Toast(text);}
 public void show(){messages.add(text);}
}''',
    "at/hausgarten/service/PhotoAttachmentStore.java": '''package at.hausgarten.service;
final class PhotoAttachmentStore {static final long MAX_BYTES=20L*1024L*1024L;}''',
    "at/hausgarten/service/LocalAuthorityGuard.java": '''package at.hausgarten.service;
final class LocalAuthorityGuard {
 static android.content.Context blocked;
 static void assertStoreMayOpen(android.content.Context context){if(context==blocked)throw new IllegalStateException("RESTORE_RESTART_REQUIRED");}
}''',
}


def extract_block(source: str, marker: str) -> str:
    """Extract one exact Java block; ignore braces in strings and comments."""
    if source.count(marker) != 1:
        raise AssertionError(f"Expected exactly one production block: {marker}")
    start = source.index(marker)
    opening = source.index("{", start)
    depth, state, escaped = 0, "code", False
    i = opening
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1:i + 2]
        if state in ("string", "char"):
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == ('"' if state == "string" else "'"):
                state = "code"
        elif state == "line":
            if ch == "\n":
                state = "code"
        elif state == "comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        elif ch == "/" and nxt in ("/", "*"):
            state = "line" if nxt == "/" else "comment"
            i += 1
        elif ch in ('"', "'"):
            state = "string" if ch == '"' else "char"
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[start:i + 1]
        i += 1
    raise AssertionError(f"Unterminated production block: {marker}")


def main() -> None:
    for tool in ("java", "javac"):
        if not shutil.which(tool):
            raise SystemExit(f"{tool} is required; tests were NOT run.")
    source = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
    capture = extract_block(source, "private boolean startPhotoCapture()")
    cleanup = extract_block(source, "private void clearPendingCapture(boolean delete)")
    # Static wiring guard only; behavioral tests below execute actual extracted methods.
    chooser = extract_block(source, "if (params.isCaptureEnabled()")
    assert "startPhotoCapture()" in chooser
    assert "Toast.makeText" not in chooser, "Do not mask a specific camera error with a second generic Toast."
    methods = [capture, cleanup]
    for marker in ("private boolean failPhotoCapture(", "private static void logPhotoCaptureFailure("):
        if marker in source:
            methods.append(extract_block(source, marker))
    result = extract_block(source, "if (requestCode == FILE_REQUEST)")
    wrapper = '''package at.hausgarten.service;
import android.content.*;import android.net.Uri;import android.provider.MediaStore;
import android.webkit.ValueCallback;import android.widget.Toast;import java.io.File;import java.util.*;
class MainActivity extends Context {
 static final int FILE_REQUEST=41,RESULT_OK=-1;
 final File cache;File alternateCache;int cacheCalls,alternateAfter=Integer.MAX_VALUE;
 RuntimeException cacheError,launchError,revokeError;int launchCount;
 Uri pendingCaptureUri,revoked;File pendingCaptureFile;int revokedFlags;
 Intent launched;ValueCallback<Uri[]> fileCallback;
 final FakeHandler syncHandler=new FakeHandler();
 MainActivity(File c){cache=c;}
 public File getCacheDir(){if(cacheError!=null)throw cacheError;return ++cacheCalls>alternateAfter?alternateCache:cache;}
 void startActivityForResult(Intent i,int request){launchCount++;if(launchError!=null)throw launchError;launched=i;}
 void revokeUriPermission(Uri u,int flags){revoked=u;revokedFlags=flags;if(revokeError!=null)throw revokeError;}
 boolean startForTest(){return startPhotoCapture();}
 void clearForTest(boolean delete){clearPendingCapture(delete);}
 static class FakeHandler {
  final List<Runnable> tasks=new ArrayList<>();final List<Long> delays=new ArrayList<>();
  void postDelayed(Runnable r,long delay){tasks.add(r);delays.add(delay);}
 }
'''
    wrapper += "\n".join(methods)
    wrapper += "\nvoid resultForTest(int resultCode,Intent data){int requestCode=FILE_REQUEST;\n" + result + "\n}\n}\n"
    with __import__("synthetic_fixtures").temporary_directory(prefix="gartenpirat-camera-test-") as directory:
        root = Path(directory)
        for path, text in STUBS.items():
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8")
        package = root / "at/hausgarten/service"
        shutil.copyfile(JAVA / "PhotoCaptureProvider.java", package / "PhotoCaptureProvider.java")
        shutil.copyfile(ROOT / "tests/java/PhotoCaptureHostTest.java", package / "PhotoCaptureHostTest.java")
        (package / "MainActivity.java").write_text(wrapper, encoding="utf-8")
        classes = root / "classes"
        classes.mkdir()
        sources = sorted(str(path) for path in root.rglob("*.java"))
        jar = Path(os.environ.get('HG_JSON_JAR', ROOT/'.dependencies/pixel-tunnel-tests/json-20240303.jar'))
        if hashlib.sha256(jar.read_bytes()).hexdigest() != '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':
            raise RuntimeError('JSON test dependency mismatch')
        sources.append(str(ROOT/'tests/java/at/hausgarten/service/SyntheticBusinessData.java'))
        subprocess.run(["javac", "-encoding", "UTF-8", "--release", "17", "-cp", str(jar), "-d", str(classes), *sources], cwd=ROOT, check=True, timeout=45)
        subprocess.run(["java", "-cp", os.pathsep.join((str(classes),str(jar))), "at.hausgarten.service.PhotoCaptureHostTest", str(root / "fixtures")], cwd=ROOT, check=True, timeout=45)
    print("PASS: camera chooser wiring guard. Android/device acceptance remains NOT RUN.")


if __name__ == "__main__":
    main()
