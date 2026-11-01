package at.hausgarten.service;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Objects;

/** Outside all backup datasets. A restore must advance the epoch before activation. */
final class BackupDeviceGuard {
    private static final Object LOCK=new Object();
    private final AtomicFile file;
    BackupDeviceGuard(Context context){file=new AtomicFile(new File(context.getNoBackupFilesDir(),"backup-device-guard/identity.v1"));}
    JSONObject current()throws Exception {
        synchronized(LOCK){
            if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())write(new JSONObject().put("formatVersion",1).put("installationId",UUID.randomUUID().toString()).put("restoreEpoch",UUID.randomUUID().toString()));
            return read();
        }
    }
    JSONObject advanceForRestore(String requestId,String expectedEpoch)throws Exception {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("BACKUP_RESTORE_REQUEST_ID_REQUIRED");
        synchronized(LOCK){
            JSONObject saved=current();if(requestId.equals(saved.optString("lastRestoreRequest"))){if(!Objects.equals(expectedEpoch,saved.optString("lastRestoreExpectedEpoch")))throw new IOException("BACKUP_RESTORE_REPLAY_MISMATCH");return saved;}
            if(!saved.getString("restoreEpoch").equals(expectedEpoch))throw new IOException("BACKUP_DEVICE_EPOCH_CONFLICT");
            saved.put("restoreEpoch",UUID.randomUUID().toString()).put("lastRestoreRequest",requestId).put("lastRestoreExpectedEpoch",expectedEpoch);write(saved);return read();
        }
    }
    private JSONObject read()throws Exception {
        byte[] bytes;try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()+n>4096)throw new IOException("BACKUP_DEVICE_GUARD_INVALID");out.write(b,0,n);}bytes=out.toByteArray();}
        JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(value.getInt("formatVersion")!=1||!value.getString("installationId").matches("[0-9a-f-]{36}")||!value.getString("restoreEpoch").matches("[0-9a-f-]{36}"))throw new IOException("BACKUP_DEVICE_GUARD_INVALID");return value;
    }
    private void write(JSONObject value)throws Exception {
        File parent=file.getBaseFile().getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_DEVICE_GUARD_UNAVAILABLE");FileOutputStream out=null;
        try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);out=null;}catch(Exception failure){if(out!=null)file.failWrite(out);throw failure;}
    }
}
