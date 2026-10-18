package at.hausgarten.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

/**
 * Narrow, app-private bridge for ACTION_IMAGE_CAPTURE.
 *
 * Only randomly named JPEG files in the dedicated cache directory are exposed,
 * and only to a camera app while MainActivity grants that exact URI.  No database,
 * customer-photo original, or other cache file is reachable through this provider.
 */
public final class PhotoCaptureProvider extends ContentProvider {
    private static final String PREFIX = "capture-";
    private static final String SUFFIX = ".jpg";
    private static final long STALE_MS = 24L * 60L * 60L * 1000L;

    @Override public boolean onCreate() {
        Context context=getContext();if(context!=null)cleanupStale(context);
        return true;
    }

    static File createTarget(Context context) throws IOException {
        File directory=directory(context);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Kamera-Zwischenspeicher ist nicht verfügbar.");
        cleanupStale(context);
        File target=new File(directory,PREFIX+UUID.randomUUID().toString().replace("-","")+SUFFIX);
        if(!target.createNewFile())throw new IOException("Kameradatei konnte nicht vorbereitet werden.");
        return target;
    }

    static Uri uri(Context context,File file) throws FileNotFoundException {
        if(file==null)throw new FileNotFoundException("Die vorbereitete Kameradatei fehlt.");
        File checked=checked(context,file.getName());
        try{
            // Android may expose the same private cache through a directory alias.
            // Compare both canonical paths, while retaining checked()'s root/name guard.
            if(!checked.equals(file.getCanonicalFile())||!checked.isFile())
                throw new FileNotFoundException("Die Kameradatei liegt nicht im erlaubten Zwischenspeicher oder fehlt.");
        }catch(IOException error){
            FileNotFoundException failure=new FileNotFoundException("Die Kameradatei konnte nicht freigegeben werden.");
            failure.initCause(error);throw failure;
        }
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".photocapture").appendPath(file.getName()).build();
    }

    static void cleanupStale(Context context) {
        File[] files=directory(context).listFiles();if(files==null)return;
        long cutoff=System.currentTimeMillis()-STALE_MS;
        for(File file:files)if(validName(file.getName())&&file.isFile()&&file.lastModified()<cutoff)file.delete();
    }

    private static File directory(Context context){return new File(context.getCacheDir(),"photo-capture");}
    private static boolean validName(String name){return name!=null&&name.matches("capture-[0-9a-f]{32}\\.jpg");}
    private static File checked(Context context,String name)throws FileNotFoundException{
        if(!validName(name))throw new FileNotFoundException();
        try{
            File directory=directory(context).getCanonicalFile(),file=new File(directory,name).getCanonicalFile();
            if(!directory.equals(file.getParentFile()))throw new FileNotFoundException();
            return file;
        }catch(IOException error){
            FileNotFoundException failure=new FileNotFoundException("Der Kamera-Dateipfad konnte nicht geprüft werden.");
            failure.initCause(error);throw failure;
        }
    }
    private File file(Uri uri)throws FileNotFoundException{
        Context context=getContext();if(context==null||uri==null||!"content".equals(uri.getScheme())||!(context.getPackageName()+".photocapture").equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new FileNotFoundException();
        File file=checked(context,uri.getLastPathSegment());if(!file.isFile())throw new FileNotFoundException();return file;
    }

    @Override public String getType(Uri uri){return "image/jpeg";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        File target=file(uri);
        if("r".equals(mode))return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_ONLY);
        if("w".equals(mode)||"wt".equals(mode))return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_TRUNCATE);
        if("rw".equals(mode)||"rwt".equals(mode))return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_WRITE|("rwt".equals(mode)?ParcelFileDescriptor.MODE_TRUNCATE:0));
        throw new FileNotFoundException("Nur der vorbereitete Kamera-Lese-/Schreibzugriff ist erlaubt.");
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try{
            File value=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor result=new MatrixCursor(columns);Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.SIZE.equals(columns[i])?value.length():OpenableColumns.DISPLAY_NAME.equals(columns[i])?value.getName():null;
            result.addRow(row);return result;
        }catch(FileNotFoundException error){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
