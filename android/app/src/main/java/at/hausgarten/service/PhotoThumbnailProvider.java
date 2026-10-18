package at.hausgarten.service;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** App-private thumbnails only. No originals, free paths or exported provider. */
public final class PhotoThumbnailProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "image/jpeg";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        String name=uri.getLastPathSegment();if(!"r".equals(mode)||name==null||!name.matches("[0-9a-f]{64}\\.jpg")||uri.getPathSegments().size()!=1)throw new FileNotFoundException("Vorschaureferenz ungültig.");
        PhotoAttachmentStore store=new PhotoAttachmentStore(getContext());
        try{File f=store.thumbnailFile(name);if(!f.isFile()){byte[] raw=DriveSyncCoordinator.fetchThumbnail(getContext(),name);store.storeThumbnail(raw,name);}return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception error){throw new FileNotFoundException("Vorschau noch nicht verfügbar.");}
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
