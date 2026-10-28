package at.hausgarten.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** A real, byte-verified external copy replaces manual retyping during setup. */
final class BackupRecoveryExport {
    interface Output {OutputStream open()throws Exception;}
    interface Input {InputStream open()throws Exception;}
    static String writeAndVerify(String code,Output output,Input input)throws Exception {
        byte[] key=BackupCrypto.recoveryKey(code);Arrays.fill(key,(byte)0);
        byte[] expected=(code+"\n").getBytes(StandardCharsets.US_ASCII);
        try(OutputStream target=output.open()){
            if(target==null)throw new IOException("RECOVERY_EXPORT_UNAVAILABLE");
            target.write(expected);target.flush();
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream source=input.open()){
            if(source==null)throw new IOException("RECOVERY_EXPORT_UNAVAILABLE");
            byte[] block=new byte[256];int count;
            while((count=source.read(block))!=-1){if(count>4096-bytes.size())throw new IOException("RECOVERY_EXPORT_READBACK_FAILED");bytes.write(block,0,count);}
        }
        byte[] actual=bytes.toByteArray();
        try{
            if(!MessageDigest.isEqual(expected,actual))throw new IOException("RECOVERY_EXPORT_READBACK_FAILED");
            return new String(actual,StandardCharsets.US_ASCII).trim();
        }finally{Arrays.fill(expected,(byte)0);Arrays.fill(actual,(byte)0);}
    }
    private BackupRecoveryExport(){}
}
