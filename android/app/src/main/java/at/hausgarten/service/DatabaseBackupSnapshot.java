package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Consistent private SQLite image, including pending rows, receipts, sessions and BLOB jobs. */
final class DatabaseBackupSnapshot {
    static JSONObject capture(SQLiteDatabase source,File freshTarget)throws Exception {
        if(source.inTransaction())throw new IOException("BACKUP_SNAPSHOT_REQUIRES_OUTSIDE_TRANSACTION");
        if(freshTarget.exists()||freshTarget.isDirectory())throw new IOException("BACKUP_SNAPSHOT_TARGET_EXISTS");
        File parent=freshTarget.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_SNAPSHOT_STORAGE_UNAVAILABLE");
        // SQLite performs a transactional snapshot, including any WAL pages. Do not copy
        // the live main DB file. Unsupported older engines fail without changing source.
        source.execSQL("VACUUM INTO '"+freshTarget.getAbsolutePath().replace("'","''")+"'");
        try(FileOutputStream sync=new FileOutputStream(freshTarget,true)){sync.getFD().sync();}
        return inspect(freshTarget);
    }
    static JSONObject inspect(File database)throws Exception {
        if(!database.isFile())throw new IOException("BACKUP_DATABASE_MISSING");
        try(SQLiteDatabase copy=SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            try(Cursor check=copy.rawQuery("PRAGMA integrity_check",null)){if(!check.moveToFirst()||!"ok".equals(check.getString(0))||check.moveToNext())throw new IOException("BACKUP_DATABASE_INTEGRITY_FAILED");}
            try(Cursor foreign=copy.rawQuery("PRAGMA foreign_key_check",null)){if(foreign.moveToFirst())throw new IOException("BACKUP_DATABASE_RELATION_FAILED");}
            JSONObject counts=new JSONObject();TreeMap<String,String> definitions=new TreeMap<>();
            try(Cursor schema=copy.rawQuery("SELECT type,name,sql FROM sqlite_master ORDER BY type,name",null)){
                while(schema.moveToNext()){
                    String kind=schema.getString(0),name=schema.getString(1),sql=schema.getString(2);definitions.put(kind+":"+name,sql==null?"":sql);
                    if(kind.equals("table"))try(Cursor count=copy.rawQuery("SELECT count(*) FROM \""+name.replace("\"","\"\"")+"\"",null)){if(!count.moveToFirst())throw new IOException("BACKUP_DATABASE_COUNT_FAILED");counts.put(name,count.getLong(0));}
                }
            }
            return new JSONObject().put("schemaVersion",copy.getVersion()).put("tableCounts",counts).put("schemaSha256",PixelMcpWriteTools.hash(new JSONObject(definitions))).put("size",database.length()).put("sha256",EncryptedBackupArchive.sha256(database));
        }
    }
    /** Large snapshot/payload rows must not require a CursorWindow-sized JSON value. */
    static String ownerValue(SQLiteDatabase snapshot,String key,String fallback)throws Exception {
        int length;try(Cursor row=snapshot.rawQuery("SELECT length(value) FROM owner_state WHERE key=?",new String[]{key})){if(!row.moveToFirst())return fallback;length=row.getInt(0);}
        StringBuilder value=new StringBuilder();for(int offset=0;offset<length;offset+=32768)try(Cursor row=snapshot.rawQuery("SELECT substr(value,?,32768) FROM owner_state WHERE key=?",new String[]{Integer.toString(offset+1),key})){if(!row.moveToFirst())throw new IOException("BACKUP_DATABASE_ROW_MISSING");value.append(row.getString(0));}return value.toString();
    }
    private DatabaseBackupSnapshot(){}
}
