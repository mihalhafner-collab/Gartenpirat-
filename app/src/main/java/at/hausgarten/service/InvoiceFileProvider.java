package at.hausgarten.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Exposes only explicitly shared generated invoice PDFs, never the database. */
public class InvoiceFileProvider extends ContentProvider {
    public boolean onCreate() {return true;}
    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if(name == null || !name.matches("Rechnung-[a-f0-9-]{36}\\.pdf"))throw new FileNotFoundException();
        File value = new File(new File(getContext().getCacheDir(),"invoice-share"),name);
        if(!value.isFile())throw new FileNotFoundException();
        return value;
    }
    public String getType(Uri uri) {return "application/pdf";}
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Nur Lesezugriff erlaubt.");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        try {
            File value=file(uri);String[] columns=projection == null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor result=new MatrixCursor(columns);Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.SIZE.equals(columns[i])?value.length():OpenableColumns.DISPLAY_NAME.equals(columns[i])?value.getName():null;
            result.addRow(row);return result;
        }catch(FileNotFoundException error){return null;}
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
